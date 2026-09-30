/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.eclipse.dataset.core.flatxml;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.edit.ChangeOrigin;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.edit.DatasetModelChangeEvent;
import org.dbunit.eclipse.dataset.core.edit.DatasetModelListener;
import org.dbunit.eclipse.dataset.core.edit.TextDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * A dbUnit flat XML dataset bound to an Eclipse text document: the document is the single source of
 * truth, and every edit becomes a minimal text edit applied to it.
 *
 * @since 1.0.0
 */
public final class FlatXmlDatasetDocument implements TextDatasetDocument
{
    private static final FlatXmlIndex EMPTY_INDEX =
            new FlatXmlIndex(null, null, "", List.of(), Map.of(), Map.of(), Map.of());

    private final IDocumentListener documentListener = new IDocumentListener()
    {
        @Override
        public void documentAboutToBeChanged(final DocumentEvent event)
        {
            // Nothing to do before a change: documentChanged marks the model stale afterwards.
        }

        @Override
        public void documentChanged(final DocumentEvent event)
        {
            stale = true;
        }
    };

    private final List<DatasetModelListener> listeners = new ArrayList<>();

    private final PendingColumns pendingColumns;

    private final TextEditApplier applier = new TextEditApplier();

    private final Supplier<Charset> charset;

    private final Consumer<IStatus> log;

    private IDocument document;

    private DtdResolver dtdResolver;

    private FlatXmlOptions options;

    private DatasetModel model;

    private FlatXmlIndex index;

    private FlatXmlTextLayout layout;

    private boolean stale;

    /**
     * Creates a dataset document bound to a text document, which logs nothing.
     *
     * @param document The text document; its content is the single source of truth.
     * @param dtdSource Loads external DTD files the document's DOCTYPE refers to.
     * @param options The case-sensitivity and column-sensing options.
     * @param charset Returns the document's current charset, consulted only when escaping a value that
     *                needs one; a null result or a thrown exception falls back to UTF-8.
     */
    public FlatXmlDatasetDocument(final IDocument document, final DtdSource dtdSource,
            final FlatXmlOptions options, final Supplier<Charset> charset)
    {
        this(document, dtdSource, options, charset, status ->
        {
        });
    }

    /**
     * Creates a dataset document bound to a text document, which reports the failures it works around.
     *
     * @param document The text document; its content is the single source of truth.
     * @param dtdSource Loads external DTD files the document's DOCTYPE refers to; one that throws is
     *                  treated as a DTD that could not be found, and the failure is logged.
     * @param options The case-sensitivity and column-sensing options.
     * @param charset Returns the document's current charset, consulted only when escaping a value that
     *                needs one; a null result falls back to UTF-8, and so does a thrown exception, which is
     *                logged.
     * @param log Receives a warning status for each failure that the document works around.
     */
    public FlatXmlDatasetDocument(final IDocument document, final DtdSource dtdSource,
            final FlatXmlOptions options, final Supplier<Charset> charset, final Consumer<IStatus> log)
    {
        this.document = document;
        this.dtdResolver = new DtdResolver(dtdSource, log);
        this.options = options;
        this.charset = charset;
        this.log = log;
        this.pendingColumns = new PendingColumns(() ->
        {
            stale = true;
            refreshInternal(ChangeOrigin.EDIT);
        });
        this.model = DatasetModel.EMPTY;
        this.index = EMPTY_INDEX;
        this.stale = true;
        document.addDocumentListener(documentListener);
    }

    @Override
    public DatasetModel getModel()
    {
        return model;
    }

    @Override
    public boolean isStale()
    {
        return stale;
    }

    @Override
    public void refresh()
    {
        refreshInternal(ChangeOrigin.DOCUMENT);
    }

    @Override
    public void addModelListener(final DatasetModelListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void removeModelListener(final DatasetModelListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    public void batch(final Runnable operations)
    {
        applier.batch(document, operations);
    }

    @Override
    public void setCells(final String tableKey, final List<CellChange> changes)
    {
        final DatasetTable table = editableTable(tableKey);
        final List<TextEdit> edits = new CellEdits(editContext()).plan(tableKey, table, changes);
        if (edits.isEmpty())
        {
            return;
        }
        applier.apply(document, edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void insertRows(final String tableKey, final int rowIndex, final List<List<String>> rows)
    {
        final DatasetTable table = editableTable(tableKey);
        final List<TextEdit> edits = rowInsertEdits(tableKey, table, rowIndex, rows);
        if (edits.isEmpty())
        {
            return;
        }
        applier.apply(document, edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void setCellsAndAppendRows(final String tableKey, final List<CellChange> changes,
            final List<List<String>> rows)
    {
        final DatasetTable table = editableTable(tableKey);
        // Both sets of edits come from the same index: the cell edits rewrite start tags of existing
        // rows, and the appended rows go after the last row element, so they never overlap.
        final EditContext context = editContext();
        final List<TextEdit> edits = new ArrayList<>(new CellEdits(context).plan(tableKey, table, changes));
        if (!rows.isEmpty())
        {
            edits.addAll(rowInsertEdits(tableKey, table, table.getRows().size(), rows));
        }
        if (edits.isEmpty())
        {
            return;
        }
        applier.apply(document, edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    private EditContext editContext()
    {
        return new EditContext(document::get, index, layout, charset, log);
    }

    /**
     * Refreshes the model, then returns the table with a key.
     *
     * @throws DatasetEditException When the source has errors or the table does not exist.
     */
    private DatasetTable editableTable(final String tableKey)
    {
        refreshForEdit();
        return getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException(NLS.bind(Messages.Edit_noSuchTable, tableKey)));
    }

    /**
     * Refreshes the model, and requires that it can be edited.
     *
     * @throws DatasetEditException When the source has errors.
     */
    private void refreshForEdit()
    {
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException(Messages.Edit_sourceHasErrors);
        }
    }

    /**
     * Validates new rows and returns the edit that inserts them before the row at rowIndex, or after the
     * last row when rowIndex is the row count, computed against the current index; no edit when there are
     * no rows.
     */
    private List<TextEdit> rowInsertEdits(final String tableKey, final DatasetTable table,
            final int rowIndex, final List<List<String>> rows)
    {
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        requireInsertPosition(table, rowElements.size(), rowIndex);
        for (final List<String> values : rows)
        {
            requireInsertableRow(table, values);
        }
        if (rows.isEmpty())
        {
            return List.of();
        }

        final CharsetEncoder encoder = editContext().encoder();
        final List<String> rowTexts = new ArrayList<>();
        for (final List<String> values : rows)
        {
            rowTexts.add(buildRowText(table, values, encoder));
        }

        final TextEdit edit = insertEdit(tableKey, rowElements, rowIndex, rowTexts);
        return List.of(edit);
    }

    private static void requireInsertPosition(final DatasetTable table, final int rowCount,
            final int rowIndex)
    {
        if (table.getColumns().isEmpty())
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_tableHasNoColumns, table.getName()));
        }
        if (rowIndex < 0 || rowIndex > rowCount)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_rowOutOfRange, rowIndex, table.getName()));
        }
    }

    private static void requireInsertableRow(final DatasetTable table, final List<String> values)
    {
        if (values.size() != table.getColumns().size())
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_wrongValueCount,
                    table.getColumns().size(), table.getName()));
        }
        if (isAllNull(values))
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_newRowWouldBeEmpty, table.getName()));
        }
    }

    /**
     * Returns the edit that inserts the texts of new rows before the row at rowIndex, after the last row
     * when rowIndex is the row count, or into the table's empty element or the root when it has no rows.
     */
    private TextEdit insertEdit(final String tableKey, final List<FlatXmlElement> rowElements,
            final int rowIndex, final List<String> rowTexts)
    {
        final String delimiter = layout.getLineDelimiter();
        final TextEdit edit;
        if (rowIndex < rowElements.size())
        {
            final FlatXmlElement anchor = rowElements.get(rowIndex);
            final String indent = layout.indentOf(anchor);
            final StringBuilder insertText = new StringBuilder();
            for (final String rowText : rowTexts)
            {
                insertText.append(rowText).append(delimiter).append(indent);
            }
            edit = new InsertEdit(anchor.offset(), insertText.toString());
        }
        else if (!rowElements.isEmpty())
        {
            final FlatXmlElement last = rowElements.get(rowElements.size() - 1);
            final String indent = layout.indentOf(last);
            final StringBuilder insertText = new StringBuilder();
            for (final String rowText : rowTexts)
            {
                insertText.append(delimiter).append(indent).append(rowText);
            }
            edit = new InsertEdit(last.endOffset(), insertText.toString());
        }
        else if (!index.getMarkerElements(tableKey).isEmpty())
        {
            final FlatXmlElement marker = index.getMarkerElements(tableKey).get(0);
            final String indent = layout.indentOf(marker);
            final String joined = String.join(delimiter + indent, rowTexts);
            edit = new ReplaceEdit(marker.offset(), marker.endOffset() - marker.offset(), joined);
        }
        else
        {
            final String indent = layout.childIndentation(index.getRoot(), index.getElements());
            final String joined = String.join(delimiter + indent, rowTexts);
            edit = layout.insertAsLastChildOfRoot(index.getRoot(), index.getElements(), joined);
        }
        return edit;
    }

    @Override
    public void duplicateRows(final String tableKey, final int[] rowIndexes)
    {
        final DatasetTable table = editableTable(tableKey);
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        final int[] sorted = sortedRowIndexes(table, rowElements, rowIndexes);
        if (sorted.length == 0)
        {
            return;
        }

        final FlatXmlElement lastSelected = rowElements.get(sorted[sorted.length - 1]);
        final String delimiter = layout.getLineDelimiter();
        final String indent = layout.indentOf(lastSelected);
        final String text = document.get();
        final StringBuilder insertText = new StringBuilder();
        for (final int rowIndex : sorted)
        {
            final FlatXmlElement element = rowElements.get(rowIndex);
            insertText.append(delimiter).append(indent).append(text, element.offset(),
                    element.endOffset());
        }

        applier.apply(document, List.of(new InsertEdit(lastSelected.endOffset(), insertText.toString())));
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void deleteRows(final String tableKey, final int[] rowIndexes)
    {
        final DatasetTable table = editableTable(tableKey);
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        final int[] sorted = sortedRowIndexes(table, rowElements, rowIndexes);
        if (sorted.length == 0)
        {
            return;
        }

        final boolean deletingAllRows = sorted.length == rowElements.size();
        final boolean hasMarker = !index.getMarkerElements(tableKey).isEmpty();
        final List<TextEdit> edits = new ArrayList<>();
        for (int position = 0; position < sorted.length; position++)
        {
            final FlatXmlElement element = rowElements.get(sorted[position]);
            if (position == 0 && deletingAllRows && !hasMarker)
            {
                edits.add(new ReplaceEdit(element.offset(), element.endOffset() - element.offset(),
                        "<" + table.getName() + "/>"));
            }
            else
            {
                final IRegion region = layout.lineExtent(element);
                edits.add(new DeleteEdit(region.getOffset(), region.getLength()));
            }
        }

        applier.apply(document, edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    /**
     * Returns a sorted copy of row indexes, which must all be rows of the table.
     */
    private static int[] sortedRowIndexes(final DatasetTable table, final List<FlatXmlElement> rowElements,
            final int[] rowIndexes)
    {
        final int[] sorted = rowIndexes.clone();
        Arrays.sort(sorted);
        for (final int rowIndex : sorted)
        {
            if (rowIndex < 0 || rowIndex >= rowElements.size())
            {
                throw new DatasetEditException(
                        NLS.bind(Messages.Edit_rowOutOfRange, rowIndex, table.getName()));
            }
        }
        return sorted;
    }

    @Override
    public void moveRows(final String tableKey, final int firstRowIndex, final int rowCount,
            final int delta)
    {
        final DatasetTable table = editableTable(tableKey);
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        requireRowBlock(table, rowElements.size(), firstRowIndex, rowCount);
        requireOneRowStep(delta);
        requireRoomToMove(table, rowElements.size(), firstRowIndex, rowCount, delta);

        applier.apply(document, rowMoveEdits(rowElements, firstRowIndex, rowCount, delta));
        refreshInternal(ChangeOrigin.EDIT);
    }

    private static void requireRowBlock(final DatasetTable table, final int totalRows,
            final int firstRowIndex, final int blockSize)
    {
        if (firstRowIndex < 0 || blockSize < 1 || firstRowIndex + blockSize > totalRows)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_rowBlockOutOfRange, table.getName()));
        }
    }

    private static void requireOneRowStep(final int delta)
    {
        if (delta != -1 && delta != 1)
        {
            throw new DatasetEditException(Messages.Edit_moveByOnePosition);
        }
    }

    private static void requireRoomToMove(final DatasetTable table, final int totalRows,
            final int firstRowIndex, final int blockSize, final int delta)
    {
        if (delta < 0 && firstRowIndex == 0)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_moveBeforeFirstRow, table.getName()));
        }
        if (delta > 0 && firstRowIndex + blockSize >= totalRows)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_moveAfterLastRow, table.getName()));
        }
    }

    /**
     * Returns the edits that move a block of rows one position up or down: one replace edit for each row of
     * the block and for the neighbouring row it passes, which rotates their texts by one position. The
     * arguments are already validated.
     */
    private List<TextEdit> rowMoveEdits(final List<FlatXmlElement> rowElements, final int firstRowIndex,
            final int rowCount, final int delta)
    {
        final int startPosition = delta < 0 ? firstRowIndex - 1 : firstRowIndex;
        final int positionCount = rowCount + 1;
        final String text = document.get();
        final List<FlatXmlElement> positions = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        for (int i = 0; i < positionCount; i++)
        {
            final FlatXmlElement element = rowElements.get(startPosition + i);
            positions.add(element);
            texts.add(text.substring(element.offset(), element.endOffset()));
        }

        final List<TextEdit> edits = new ArrayList<>();
        for (int i = 0; i < positionCount; i++)
        {
            final int sourceIndex =
                    delta > 0 ? Math.floorMod(i - 1, positionCount) : Math.floorMod(i + 1, positionCount);
            final FlatXmlElement position = positions.get(i);
            edits.add(new ReplaceEdit(position.offset(), position.endOffset() - position.offset(),
                    texts.get(sourceIndex)));
        }
        return edits;
    }

    @Override
    public void addColumn(final String tableKey, final String columnName)
    {
        final DatasetTable table = editableTable(tableKey);
        requireValidColumnName(columnName);
        requireAvailableColumnName(table, columnName, -1);

        pendingColumns.addColumn(document, tableKey, columnName);
    }

    @Override
    public void renameColumn(final String tableKey, final String columnName, final String newColumnName)
    {
        final DatasetTable table = editableTable(tableKey);
        final int columnIndex = requireColumnIndex(table, columnName);
        requireValidColumnName(newColumnName);
        requireAvailableColumnName(table, newColumnName, columnIndex);

        final DatasetColumn column = table.getColumns().get(columnIndex);
        final String key = columnName.toUpperCase(Locale.ENGLISH);
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        for (final FlatXmlElement element : rowElements)
        {
            if (element.hasCaseVariantAttributes(key))
            {
                throw new DatasetEditException(NLS.bind(Messages.Edit_renameColumnWithCaseVariants,
                        column.name(), table.getName()));
            }
        }

        if (column.pending())
        {
            pendingColumns.renameColumn(document, tableKey, columnName, newColumnName);
            return;
        }
        requireRenamableColumn(table, column);

        final Map<String, String> renames = new LinkedHashMap<>();
        renames.put(key, newColumnName);
        final List<TextEdit> edits = startTagEdits(table, rowElements, Map.of(), renames);
        if (edits.isEmpty())
        {
            return;
        }
        applier.apply(document, edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void deleteColumn(final String tableKey, final String columnName)
    {
        final DatasetTable table = editableTable(tableKey);
        final int columnIndex = requireColumnIndex(table, columnName);
        final DatasetColumn column = table.getColumns().get(columnIndex);

        if (column.pending())
        {
            pendingColumns.deleteColumn(document, tableKey, columnName);
            return;
        }

        final String key = columnName.toUpperCase(Locale.ENGLISH);
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        requireNoRowLeftEmpty(table, column, rowElements, key);

        final Map<String, String> changes = new LinkedHashMap<>();
        changes.put(key, null);
        final List<TextEdit> edits = startTagEdits(table, rowElements, changes, Map.of());
        if (edits.isEmpty())
        {
            return;
        }
        applier.apply(document, edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    private static int requireColumnIndex(final DatasetTable table, final String columnName)
    {
        final int columnIndex = table.getColumnIndex(columnName);
        if (columnIndex < 0)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_noSuchColumn, columnName, table.getName()));
        }
        return columnIndex;
    }

    private static void requireValidColumnName(final String columnName)
    {
        if (!XmlNames.isValidName(columnName))
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_invalidColumnName, columnName));
        }
    }

    /**
     * Requires that no other column of the table has the name.
     *
     * @param ownColumnIndex The index of the column that gets the name, or -1 for a new column.
     */
    private static void requireAvailableColumnName(final DatasetTable table, final String columnName,
            final int ownColumnIndex)
    {
        final int existingIndex = table.getColumnIndex(columnName);
        if (existingIndex >= 0 && existingIndex != ownColumnIndex)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_columnExists, table.getName(), columnName));
        }
    }

    private static void requireRenamableColumn(final DatasetTable table, final DatasetColumn column)
    {
        if (column.declared() && !column.hasValues())
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_renameColumnIsDeclaredOnly,
                    column.name(), table.getName()));
        }
    }

    private static void requireNoRowLeftEmpty(final DatasetTable table, final DatasetColumn column,
            final List<FlatXmlElement> rowElements, final String key)
    {
        for (final FlatXmlElement element : rowElements)
        {
            if (hasOnlyColumn(element, key))
            {
                throw new DatasetEditException(NLS.bind(Messages.Edit_deleteColumnWouldEmptyRow,
                        column.name(), table.getName()));
            }
        }
    }

    private static boolean hasOnlyColumn(final FlatXmlElement element, final String key)
    {
        boolean hasColumn = false;
        int remaining = 0;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            if (attribute.name().toUpperCase(Locale.ENGLISH).equals(key))
            {
                hasColumn = true;
            }
            else
            {
                remaining++;
            }
        }
        return hasColumn && remaining == 0;
    }

    /**
     * Returns the start-tag rewrites that apply changes and renames to the attributes of row elements.
     */
    private List<TextEdit> startTagEdits(final DatasetTable table, final List<FlatXmlElement> rowElements,
            final Map<String, String> changes, final Map<String, String> renames)
    {
        final CharsetEncoder encoder = editContext().encoder();
        final String text = document.get();
        final List<TextEdit> edits = new ArrayList<>();
        for (final FlatXmlElement element : rowElements)
        {
            final String rewritten =
                    StartTagRewriter.rewrite(text, element, table.getColumns(), changes, renames, encoder);
            if (rewritten != null)
            {
                edits.add(new ReplaceEdit(element.nameEndOffset(),
                        element.attributesEndOffset() - element.nameEndOffset(), rewritten));
            }
        }
        return edits;
    }

    @Override
    public void addTable(final String tableName, final List<String> columnNames)
    {
        refreshForEdit();
        requireValidTableName(tableName);
        requireUnusedTableName(tableName, Set.of());
        requireNewColumnNames(tableName, columnNames);

        final TextEdit insertion =
                layout.insertAsLastChildOfRoot(index.getRoot(), index.getElements(), "<" + tableName + "/>");
        applier.apply(document, List.of(insertion));
        pendingColumns.addTable(tableKeyOf(tableName), columnNames);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void renameTable(final String tableKey, final String newTableName)
    {
        final DatasetTable tableToRename = editableTable(tableKey);
        if (tableToRename.isDeclaredOnly())
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_renameTableIsDeclaredOnly, tableToRename.getName()));
        }
        requireValidTableName(newTableName);
        requireUnusedTableName(newTableName, Set.of(tableKey));

        final List<FlatXmlElement> elements = index.getAllElementsInOrder(tableKey);
        final List<TextEdit> edits = tableNameEdits(elements, newTableName);
        if (!edits.isEmpty())
        {
            applier.apply(document, edits);
        }

        pendingColumns.renameTable(tableKey, tableKeyOf(newTableName));
        stale = true;
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void deleteTable(final String tableKey)
    {
        final DatasetTable tableToDelete = editableTable(tableKey);
        if (tableToDelete.isDeclaredOnly())
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_deleteTableIsDeclaredOnly, tableToDelete.getName()));
        }

        final List<FlatXmlElement> elements = index.getAllElementsInOrder(tableKey);
        final List<TextEdit> edits = new ArrayList<>();
        for (final FlatXmlElement element : elements)
        {
            final IRegion region = layout.lineExtent(element);
            edits.add(new DeleteEdit(region.getOffset(), region.getLength()));
        }
        if (!edits.isEmpty())
        {
            applier.apply(document, edits);
        }

        pendingColumns.deleteTable(tableKey);
        stale = true;
        refreshInternal(ChangeOrigin.EDIT);
    }

    private void requireValidTableName(final String tableName)
    {
        if (!XmlNames.isValidName(tableName))
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_invalidTableName, tableName));
        }
        if (isReservedRootName(tableName))
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_reservedTableName, tableName));
        }
    }

    /**
     * Requires that no table has the name, other than the tables with the keys to ignore.
     *
     * @param ignoredTableKeys The keys of the tables that may have the name: the one that is being renamed.
     */
    private void requireUnusedTableName(final String tableName, final Set<String> ignoredTableKeys)
    {
        for (final DatasetTable existing : getModel().getTables())
        {
            if (!ignoredTableKeys.contains(existing.getKey()) && sameTableName(existing.getName(), tableName))
            {
                throw new DatasetEditException(NLS.bind(Messages.Edit_tableExists, tableName));
            }
        }
    }

    private static void requireNewColumnNames(final String tableName, final List<String> columnNames)
    {
        final Set<String> seenColumnKeys = new HashSet<>();
        for (final String columnName : columnNames)
        {
            requireValidColumnName(columnName);
            final String columnKey = columnName.toUpperCase(Locale.ENGLISH);
            if (!seenColumnKeys.add(columnKey))
            {
                throw new DatasetEditException(NLS.bind(Messages.Edit_columnExists, tableName, columnName));
            }
        }
    }

    /**
     * Returns the edits that rename the start tag and the end tag of each element to a table name.
     */
    private static List<TextEdit> tableNameEdits(final List<FlatXmlElement> elements, final String tableName)
    {
        final List<TextEdit> edits = new ArrayList<>();
        for (final FlatXmlElement element : elements)
        {
            edits.add(new ReplaceEdit(element.offset() + 1, element.name().length(), tableName));
            if (!element.selfClosing())
            {
                edits.add(new ReplaceEdit(element.endTagOffset() + 2, element.name().length(), tableName));
            }
        }
        return edits;
    }

    private boolean isReservedRootName(final String tableName)
    {
        return sameTableName("dataset", tableName);
    }

    private boolean sameTableName(final String oneName, final String otherName)
    {
        return tableKeyOf(oneName).equals(tableKeyOf(otherName));
    }

    @Override
    public String tableKeyOf(final String tableName)
    {
        return options.caseSensitiveTableNames() ? tableName : tableName.toUpperCase(Locale.ENGLISH);
    }

    @Override
    public void dispose()
    {
        document.removeDocumentListener(documentListener);
        listeners.clear();
    }

    @Override
    public boolean isBlank()
    {
        return document.get().isBlank();
    }

    @Override
    public void createEmptyDataset()
    {
        if (!isBlank())
        {
            throw new DatasetEditException(Messages.Edit_documentNotBlank);
        }
        final String delimiter = TextUtilities.getDefaultLineDelimiter(document);
        final String newText = emptyDatasetText(delimiter);
        applier.apply(document, List.of(new ReplaceEdit(0, document.getLength(), newText)));
        refreshInternal(ChangeOrigin.EDIT);
    }

    /**
     * Returns the text of an empty flat XML dataset: an XML declaration for UTF-8, then the start and end
     * tags of the {@code dataset} root element, each on its own line.
     *
     * @param lineDelimiter The delimiter that ends each of the three lines.
     * @return The text of an empty dataset.
     */
    public static String emptyDatasetText(final String lineDelimiter)
    {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + lineDelimiter + "<dataset>" + lineDelimiter
                + "</dataset>" + lineDelimiter;
    }

    @Override
    public Optional<IRegion> locate(final CellAddress address)
    {
        return new CellLocator(model, index, options).locate(address);
    }

    @Override
    public Optional<CellAddress> cellAt(final int offset)
    {
        return new CellLocator(model, index, options).cellAt(offset);
    }

    /**
     * Changes the case-sensitivity and column-sensing options, then refreshes.
     *
     * @param newOptions The new options.
     */
    public void setOptions(final FlatXmlOptions newOptions)
    {
        this.options = newOptions;
        stale = true;
        refresh();
    }

    public void reloadDtd()
    {
        final boolean changed = dtdResolver.reload();
        if (changed)
        {
            stale = true;
            refresh();
        }
    }

    /**
     * Rebinds this document to a new text document and DTD source, typically after Save As, then marks
     * the model stale and refreshes.
     *
     * @param newDocument The new text document.
     * @param newDtdSource The new DTD source.
     */
    public void rebind(final IDocument newDocument, final DtdSource newDtdSource)
    {
        document.removeDocumentListener(documentListener);
        this.document = newDocument;
        this.dtdResolver = new DtdResolver(newDtdSource, log);
        this.pendingColumns.forgetHistory();
        document.addDocumentListener(documentListener);
        stale = true;
        refresh();
    }

    private void refreshInternal(final ChangeOrigin origin)
    {
        if (!stale)
        {
            return;
        }
        final long modificationStamp = currentModificationStamp();
        pendingColumns.restore(modificationStamp);
        final String text = document.get();
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final DtdResolution dtdResolution = dtdResolver.resolve(parse.doctype());
        final FlatXmlModelBuilder.Result built = FlatXmlModelBuilder.build(text, parse,
                dtdResolution.declarations(), options, pendingColumns.asMap());
        pendingColumns.prune(built.model().getTables());
        pendingColumns.record(modificationStamp);
        final List<DatasetProblem> problems = FlatXmlValidator.validate(parse, built.index(),
                built.model().getTables(), dtdResolution.state(), dtdResolution.declarations(), options);
        final DatasetModel oldModel = model;
        model = new DatasetModel(built.model().getTables(), problems, built.model().isEditable());
        index = built.index();
        layout = new FlatXmlTextLayout(text, TextUtilities.getDefaultLineDelimiter(document));
        stale = false;
        notifyListeners(new DatasetModelChangeEvent(oldModel, model, origin));
    }

    /**
     * Returns the document's current modification stamp, or {@link IDocumentExtension4#UNKNOWN_MODIFICATION_STAMP}
     * when it does not support one.
     */
    private long currentModificationStamp()
    {
        if (document instanceof final IDocumentExtension4 extension)
        {
            return extension.getModificationStamp();
        }
        return IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
    }

    private void notifyListeners(final DatasetModelChangeEvent event)
    {
        for (final DatasetModelListener listener : new ArrayList<>(listeners))
        {
            listener.modelChanged(event);
        }
    }

    /**
     * Returns the text of a new row element: the table's display name, one attribute per non-null value
     * in table column order, and a self-closing end.
     */
    private static String buildRowText(final DatasetTable table, final List<String> values,
            final CharsetEncoder encoder)
    {
        final StringBuilder text = new StringBuilder();
        text.append('<').append(table.getName());
        for (int columnIndex = 0; columnIndex < table.getColumns().size(); columnIndex++)
        {
            final String value = values.get(columnIndex);
            if (value != null)
            {
                text.append(' ').append(table.getColumns().get(columnIndex).name()).append("=\"")
                        .append(AttributeValueCodec.escape(value, encoder)).append('"');
            }
        }
        text.append("/>");
        return text.toString();
    }

    /**
     * Returns whether every value is null; a new row with no values would have an element without
     * attributes, which is not a valid row.
     */
    private static boolean isAllNull(final List<String> values)
    {
        for (final String value : values)
        {
            if (value != null)
            {
                return false;
            }
        }
        return true;
    }

}
