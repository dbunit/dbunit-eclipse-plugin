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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdReader;
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
import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;

/**
 * A dbUnit flat XML dataset bound to an Eclipse text document: the document is the single source of
 * truth, and every edit becomes a minimal text edit applied to it.
 *
 * @since 1.0.0
 */
public final class FlatXmlDatasetDocument implements TextDatasetDocument
{
    private static final String PLUGIN_ID = "org.dbunit.eclipse.dataset.core";

    private static final int JOIN_EDITS_THRESHOLD = 50;

    private static final int PENDING_COLUMNS_HISTORY_LIMIT = 200;

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

    private final Map<String, CachedDtd> dtdCache = new LinkedHashMap<>();

    private final Map<String, List<String>> pendingColumns = new LinkedHashMap<>();

    private final Map<Long, Map<String, List<String>>> pendingColumnsHistory = new LinkedHashMap<>();

    private final Supplier<Charset> charset;

    private final Consumer<IStatus> log;

    private IDocument document;

    private DtdSource dtdSource;

    private FlatXmlOptions options;

    private DatasetModel model;

    private FlatXmlIndex index;

    private FlatXmlTextLayout layout;

    private boolean stale;

    private int batchDepth;

    private long lastRefreshModificationStamp = IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;

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
        this.dtdSource = dtdSource;
        this.options = options;
        this.charset = charset;
        this.log = log;
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
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        final boolean outermost = batchDepth == 0;
        batchDepth++;
        if (outermost && undoManager != null)
        {
            undoManager.beginCompoundChange();
        }
        try
        {
            operations.run();
        }
        finally
        {
            batchDepth--;
            if (batchDepth == 0 && undoManager != null)
            {
                undoManager.endCompoundChange();
            }
        }
    }

    @Override
    public void setCells(final String tableKey, final List<CellChange> changes)
    {
        final DatasetTable table = editableTable(tableKey);
        final List<TextEdit> edits = cellEdits(tableKey, table, changes);
        if (edits.isEmpty())
        {
            return;
        }
        apply(edits);
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
        apply(edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void setCellsAndAppendRows(final String tableKey, final List<CellChange> changes,
            final List<List<String>> rows)
    {
        final DatasetTable table = editableTable(tableKey);
        // Both sets of edits come from the same index: the cell edits rewrite start tags of existing
        // rows, and the appended rows go after the last row element, so they never overlap.
        final List<TextEdit> edits = new ArrayList<>(cellEdits(tableKey, table, changes));
        if (!rows.isEmpty())
        {
            edits.addAll(rowInsertEdits(tableKey, table, table.getRows().size(), rows));
        }
        if (edits.isEmpty())
        {
            return;
        }
        apply(edits);
        refreshInternal(ChangeOrigin.EDIT);
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
     * Validates cell changes and returns the start-tag rewrites that make them, one per changed row
     * element, computed against the current index.
     */
    private List<TextEdit> cellEdits(final String tableKey, final DatasetTable table,
            final List<CellChange> changes)
    {
        requireExistingCells(table, changes);

        final Map<Integer, Map<String, String>> changesByRow = new LinkedHashMap<>();
        for (final CellChange change : changes)
        {
            final String columnKey = change.columnName().toUpperCase(Locale.ENGLISH);
            changesByRow.computeIfAbsent(change.rowIndex(), unused -> new LinkedHashMap<>())
                    .put(columnKey, change.value());
        }

        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        final CharsetEncoder encoder = currentEncoder();
        final List<TextEdit> edits = new ArrayList<>();
        final String text = document.get();
        for (final Map.Entry<Integer, Map<String, String>> entry : changesByRow.entrySet())
        {
            final int rowIndex = entry.getKey();
            final Map<String, String> rowChanges = entry.getValue();
            final FlatXmlElement element = rowElements.get(rowIndex);
            final String ambiguousKey = findCaseVariantColumnKey(element, rowChanges.keySet());
            if (ambiguousKey != null)
            {
                final String columnName = table.getColumns().get(table.getColumnIndex(ambiguousKey)).name();
                throw new DatasetEditException(NLS.bind(Messages.Edit_cellHasCaseVariantAttributes,
                        new Object[] { columnName, rowIndex, table.getName() }));
            }
            requireRowNotEmptied(table, rowIndex, rowChanges);
            final String rewritten = StartTagRewriter.rewrite(text, element, table.getColumns(),
                    rowChanges, Map.of(), encoder);
            if (rewritten != null)
            {
                edits.add(new ReplaceEdit(element.nameEndOffset(),
                        element.attributesEndOffset() - element.nameEndOffset(), rewritten));
            }
        }
        return edits;
    }

    private static void requireExistingCells(final DatasetTable table, final List<CellChange> changes)
    {
        for (final CellChange change : changes)
        {
            if (change.rowIndex() < 0 || change.rowIndex() >= table.getRows().size())
            {
                throw new DatasetEditException(
                        NLS.bind(Messages.Edit_noSuchRow, change.rowIndex(), table.getName()));
            }
            if (table.getColumnIndex(change.columnName()) < 0)
            {
                throw new DatasetEditException(
                        NLS.bind(Messages.Edit_noSuchColumn, change.columnName(), table.getName()));
            }
        }
    }

    private void requireRowNotEmptied(final DatasetTable table, final int rowIndex,
            final Map<String, String> rowChanges)
    {
        if (wouldEmptyRow(table, rowIndex, rowChanges))
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_rowWouldBeEmpty, rowIndex, table.getName()));
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

        final CharsetEncoder encoder = currentEncoder();
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
            edit = insertAsLastChildOfRoot(joined);
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

        apply(List.of(new InsertEdit(lastSelected.endOffset(), insertText.toString())));
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

        apply(edits);
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

        apply(rowMoveEdits(rowElements, firstRowIndex, rowCount, delta));
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

        applyPendingColumnsChange("Add pending column",
                () -> pendingColumns.computeIfAbsent(tableKey, unused -> new ArrayList<>()).add(columnName),
                () -> removePendingColumn(tableKey, columnName));
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
            if (hasCaseVariantAttributes(element, key))
            {
                throw new DatasetEditException(NLS.bind(Messages.Edit_renameColumnWithCaseVariants,
                        column.name(), table.getName()));
            }
        }

        if (column.pending())
        {
            applyPendingColumnsChange("Rename pending column",
                    () -> renamePendingColumn(tableKey, columnName, newColumnName),
                    () -> renamePendingColumn(tableKey, newColumnName, columnName));
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
        apply(edits);
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
            final List<String> pending = pendingColumns.get(tableKey);
            final int pendingIndex = pending == null ? -1 : indexOfColumn(pending, columnName);
            applyPendingColumnsChange("Delete pending column",
                    () -> removePendingColumn(tableKey, columnName),
                    () -> restorePendingColumn(tableKey, pendingIndex, columnName));
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
        apply(edits);
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
        final CharsetEncoder encoder = currentEncoder();
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

    /**
     * Runs a pendingColumns change that makes no text edit of its own: when a document undo manager is
     * connected, through a custom operation on its undo context, so the change still becomes its own step
     * in the document's undo history instead of being invisible to undo; otherwise directly.
     */
    private void applyPendingColumnsChange(final String label, final Runnable doIt, final Runnable undoIt)
    {
        final PendingColumnsOperation operation = new PendingColumnsOperation(label, doIt, undoIt);
        final IDocumentUndoManager undoManager =
                DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        try
        {
            if (undoManager == null)
            {
                operation.execute(null, null);
            }
            else
            {
                operation.addContext(undoManager.getUndoContext());
                final IOperationHistory history = OperationHistoryFactory.getOperationHistory();
                history.execute(operation, null, null);
            }
        }
        catch (final ExecutionException e)
        {
            throw new IllegalStateException("A pending column change failed.", e);
        }
    }

    private void removePendingColumn(final String tableKey, final String columnName)
    {
        final List<String> pending = pendingColumns.get(tableKey);
        if (pending == null)
        {
            return;
        }
        final String columnKey = columnName.toUpperCase(Locale.ENGLISH);
        pending.removeIf(name -> name.toUpperCase(Locale.ENGLISH).equals(columnKey));
        if (pending.isEmpty())
        {
            pendingColumns.remove(tableKey);
        }
    }

    private void restorePendingColumn(final String tableKey, final int index, final String columnName)
    {
        if (index < 0)
        {
            return;
        }
        pendingColumns.computeIfAbsent(tableKey, unused -> new ArrayList<>()).add(index, columnName);
    }

    private void renamePendingColumn(final String tableKey, final String oldName, final String newName)
    {
        final List<String> pending = pendingColumns.get(tableKey);
        if (pending == null)
        {
            return;
        }
        final int index = indexOfColumn(pending, oldName);
        if (index >= 0)
        {
            pending.set(index, newName);
        }
    }

    private static int indexOfColumn(final List<String> names, final String name)
    {
        final String columnKey = name.toUpperCase(Locale.ENGLISH);
        for (int i = 0; i < names.size(); i++)
        {
            if (names.get(i).toUpperCase(Locale.ENGLISH).equals(columnKey))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * An undoable operation for a pendingColumns change that has no text edit of its own to carry it: it
     * shares the connected document undo manager's own undo context, so undo and redo interleave with text
     * edits in the same chronological order the user made them.
     */
    private final class PendingColumnsOperation extends AbstractOperation
    {
        private final Runnable doIt;

        private final Runnable undoIt;

        private PendingColumnsOperation(final String label, final Runnable doIt, final Runnable undoIt)
        {
            super(label);
            this.doIt = doIt;
            this.undoIt = undoIt;
        }

        @Override
        public IStatus execute(final IProgressMonitor monitor, final IAdaptable info)
        {
            return runAndRefresh(doIt);
        }

        @Override
        public IStatus redo(final IProgressMonitor monitor, final IAdaptable info)
        {
            return runAndRefresh(doIt);
        }

        @Override
        public IStatus undo(final IProgressMonitor monitor, final IAdaptable info)
        {
            return runAndRefresh(undoIt);
        }

        private IStatus runAndRefresh(final Runnable action)
        {
            action.run();
            stale = true;
            refreshInternal(ChangeOrigin.EDIT);
            return Status.OK_STATUS;
        }
    }

    @Override
    public void addTable(final String tableName, final List<String> columnNames)
    {
        refreshForEdit();
        requireValidTableName(tableName);
        requireUnusedTableName(tableName, Set.of());
        requireNewColumnNames(tableName, columnNames);

        apply(List.of(insertAsLastChildOfRoot("<" + tableName + "/>")));
        if (!columnNames.isEmpty())
        {
            pendingColumns.put(tableKeyOf(tableName), new ArrayList<>(columnNames));
        }
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
            apply(edits);
        }

        final List<String> pending = pendingColumns.remove(tableKey);
        if (pending != null)
        {
            pendingColumns.put(tableKeyOf(newTableName), pending);
        }
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
            apply(edits);
        }

        pendingColumns.remove(tableKey);
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
        apply(List.of(new ReplaceEdit(0, document.getLength(), newText)));
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
        final Optional<DatasetTable> maybeTable = model.findTable(address.tableKey());
        if (maybeTable.isEmpty())
        {
            return Optional.empty();
        }
        final DatasetTable table = maybeTable.get();
        final List<FlatXmlElement> rowElements = index.getRowElements(address.tableKey());
        if (address.rowIndex() < 0 || address.rowIndex() >= rowElements.size())
        {
            return Optional.empty();
        }
        final FlatXmlElement element = rowElements.get(address.rowIndex());
        if (address.columnIndex() < 0)
        {
            return Optional.of(new Region(element.offset(), element.endOffset() - element.offset()));
        }
        if (address.columnIndex() >= table.getColumns().size())
        {
            return Optional.empty();
        }
        final String columnName = table.getColumns().get(address.columnIndex()).name();
        final FlatXmlAttribute attribute = findAttribute(element, columnName);
        if (attribute == null)
        {
            return Optional.of(new Region(element.offset() + 1,
                    element.nameEndOffset() - element.offset() - 1));
        }
        return Optional.of(
                new Region(attribute.valueOffset(), attribute.valueEndOffset() - attribute.valueOffset()));
    }

    @Override
    public Optional<CellAddress> cellAt(final int offset)
    {
        final FlatXmlElement element = findElementContaining(offset);
        if (element == null)
        {
            return Optional.empty();
        }
        final String key = tableKey(element.name());
        final List<FlatXmlElement> rowElements = index.getRowElements(key);
        final int rowIndex = rowElements.indexOf(element);
        if (rowIndex < 0)
        {
            return Optional.empty();
        }
        final Optional<DatasetTable> table = model.findTable(key);
        if (table.isEmpty())
        {
            return Optional.empty();
        }
        final int columnIndex = columnIndexAt(table.get(), element, offset);
        return Optional.of(new CellAddress(key, rowIndex, columnIndex));
    }

    private static int columnIndexAt(final DatasetTable table, final FlatXmlElement element, final int offset)
    {
        int columnIndex = 0;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            if (offset >= attribute.nameOffset() && offset < attribute.endOffset())
            {
                columnIndex = Math.max(0, table.getColumnIndex(attribute.name()));
            }
        }
        return columnIndex;
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

    /**
     * Reads every cached DTD again through the {@link DtdSource}. When any text differs from the cached
     * one, including a DTD that became readable or unreadable, replaces the cache, marks the model stale,
     * and refreshes with {@link ChangeOrigin#DOCUMENT}. Does nothing when nothing changed.
     */
    public void reloadDtd()
    {
        boolean changed = false;
        for (final Map.Entry<String, CachedDtd> entry : new LinkedHashMap<>(dtdCache).entrySet())
        {
            final String systemId = entry.getKey();
            final CachedDtd cached = entry.getValue();
            final String freshText = loadFromDtdSource(cached.publicId(), systemId);
            if (!Objects.equals(freshText, cached.text()))
            {
                dtdCache.put(systemId, new CachedDtd(cached.publicId(), freshText));
                changed = true;
            }
        }
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
        this.dtdSource = newDtdSource;
        this.dtdCache.clear();
        this.pendingColumnsHistory.clear();
        this.lastRefreshModificationStamp = IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
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
        restorePendingColumns(modificationStamp);
        final String text = document.get();
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final DtdResolution dtdResolution = resolveDtd(parse.doctype());
        final FlatXmlModelBuilder.Result built = FlatXmlModelBuilder.build(text, parse,
                dtdResolution.declarations(), options, pendingColumns);
        prunePendingColumns(built.model().getTables());
        recordPendingColumns(modificationStamp);
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
     * Drops each table's pending columns that a fresh build now finds backed by data or the DTD, and
     * drops a table's whole entry once the table itself no longer exists in the model, so a pending
     * column never lingers once it is no longer needed.
     */
    private void prunePendingColumns(final List<DatasetTable> tables)
    {
        final Set<String> tableKeys = new HashSet<>();
        for (final DatasetTable table : tables)
        {
            tableKeys.add(table.getKey());
            final List<String> pending = pendingColumns.get(table.getKey());
            if (pending == null)
            {
                continue;
            }
            pending.removeIf(name -> isBackedByRealColumn(table, name));
            if (pending.isEmpty())
            {
                pendingColumns.remove(table.getKey());
            }
        }
        pendingColumns.keySet().removeIf(key -> !tableKeys.contains(key));
    }

    private static boolean isBackedByRealColumn(final DatasetTable table, final String name)
    {
        final int columnIndex = table.getColumnIndex(name);
        return columnIndex >= 0 && !table.getColumns().get(columnIndex).pending();
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

    /**
     * Restores pendingColumns from the snapshot recorded for modificationStamp, when that stamp differs
     * from the previous refresh's stamp and a snapshot was recorded for it. This is how the pending
     * columns that belonged to an earlier document state come back once undo or redo, which restores the
     * document's modification stamp along with its text, returns the document to that earlier state.
     */
    private void restorePendingColumns(final long modificationStamp)
    {
        if (modificationStamp == lastRefreshModificationStamp)
        {
            return;
        }
        final Map<String, List<String>> snapshot = pendingColumnsHistory.get(modificationStamp);
        if (snapshot == null)
        {
            return;
        }
        pendingColumns.clear();
        for (final Map.Entry<String, List<String>> entry : snapshot.entrySet())
        {
            pendingColumns.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    /**
     * Records a deep copy of pendingColumns under modificationStamp, so that a later undo or redo back to
     * this exact modification stamp can restore it, then discards the oldest recorded snapshot once there
     * are more than {@link #PENDING_COLUMNS_HISTORY_LIMIT} of them.
     */
    private void recordPendingColumns(final long modificationStamp)
    {
        final Map<String, List<String>> snapshot = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : pendingColumns.entrySet())
        {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        pendingColumnsHistory.put(modificationStamp, snapshot);
        lastRefreshModificationStamp = modificationStamp;
        if (pendingColumnsHistory.size() > PENDING_COLUMNS_HISTORY_LIMIT)
        {
            final Iterator<Long> oldest = pendingColumnsHistory.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    private DtdResolution resolveDtd(final FlatXmlDoctype doctype)
    {
        if (doctype == null)
        {
            return new DtdResolution(null, DtdState.NONE);
        }
        final String internalSubsetText = doctype.internalSubset();
        DtdDeclarations internalSubset =
                DtdReader.read(internalSubsetText == null ? "" : internalSubsetText);
        if (internalSubsetText != null)
        {
            internalSubset = internalSubset.withProblemsShiftedBy(doctype.internalSubsetOffset());
        }
        if (doctype.systemId() == null)
        {
            return new DtdResolution(internalSubset, DtdState.LOADED);
        }
        final String externalText = loadExternalDtd(doctype.publicId(), doctype.systemId());
        if (externalText == null)
        {
            return new DtdResolution(internalSubset, DtdState.NOT_LOADED);
        }
        final DtdDeclarations external = DtdReader.read(externalText)
                .withProblemsAt(doctype.offset(), doctype.endOffset() - doctype.offset());
        return new DtdResolution(internalSubset.merge(external), DtdState.LOADED);
    }

    private String loadExternalDtd(final String publicId, final String systemId)
    {
        final CachedDtd cached = dtdCache.get(systemId);
        if (cached != null)
        {
            return cached.text();
        }
        final String text = loadFromDtdSource(publicId, systemId);
        dtdCache.put(systemId, new CachedDtd(publicId, text));
        return text;
    }

    /**
     * Loads from {@link #dtdSource}, treating a failure the same as a DTD it could not find, so that no
     * {@link DtdSource} implementation can break the model by letting an unchecked exception escape. The
     * failure is logged.
     */
    private String loadFromDtdSource(final String publicId, final String systemId)
    {
        try
        {
            return dtdSource.load(publicId, systemId).orElse(null);
        }
        catch (final RuntimeException e)
        {
            final String message = "The DTD source failed to load the DTD \"" + systemId
                    + "\", so it is treated as not found.";
            warn(message, e);
            return null;
        }
    }

    private void warn(final String message, final RuntimeException cause)
    {
        final IStatus status = new Status(IStatus.WARNING, PLUGIN_ID, message, cause);
        log.accept(status);
    }

    private void apply(final List<TextEdit> edits)
    {
        final TextEdit change = edits.size() > JOIN_EDITS_THRESHOLD ? joinEdits(edits) : combineEdits(edits);
        final boolean outermost = batchDepth == 0;
        final IDocumentUndoManager undoManager =
                DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        if (outermost && undoManager != null)
        {
            undoManager.beginCompoundChange();
        }
        try
        {
            applyToDocument(change);
        }
        finally
        {
            if (outermost && undoManager != null)
            {
                undoManager.endCompoundChange();
            }
        }
    }

    private void applyToDocument(final TextEdit change)
    {
        try
        {
            change.apply(document, TextEdit.NONE);
        }
        catch (final MalformedTreeException | BadLocationException e)
        {
            throw new IllegalStateException("Computed text edits do not fit the document.", e);
        }
    }

    private static MultiTextEdit combineEdits(final List<TextEdit> edits)
    {
        final MultiTextEdit root = new MultiTextEdit();
        for (final TextEdit edit : edits)
        {
            root.addChild(edit);
        }
        return root;
    }

    /**
     * Joins edits into one replacement of the text from the first edit to the last, which keeps the text
     * between the edits as it is. The document changes once, however many edits there are: each edit
     * that widens the text store's gap past its limit makes the store copy the whole text, so applying
     * many scattered edits one by one takes time in proportion to their number times the document's
     * length.
     *
     * @param edits The edits to join; they must not overlap.
     * @return The replacement.
     */
    private ReplaceEdit joinEdits(final List<TextEdit> edits)
    {
        final List<TextEdit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparingInt(TextEdit::getOffset));
        final int start = ordered.get(0).getOffset();
        final int end = ordered.get(ordered.size() - 1).getExclusiveEnd();
        final String original;
        try
        {
            original = document.get(start, end - start);
        }
        catch (final BadLocationException e)
        {
            throw new IllegalStateException("Computed text edits do not fit the document.", e);
        }
        final StringBuilder replacement = new StringBuilder(original.length());
        int position = start;
        for (final TextEdit edit : ordered)
        {
            if (edit.getOffset() < position)
            {
                throw new IllegalStateException("Computed text edits overlap.");
            }
            replacement.append(original, position - start, edit.getOffset() - start);
            replacement.append(newTextOf(edit));
            position = edit.getExclusiveEnd();
        }
        return new ReplaceEdit(start, end - start, replacement.toString());
    }

    private static String newTextOf(final TextEdit edit)
    {
        if (edit instanceof final ReplaceEdit replaceEdit)
        {
            return replaceEdit.getText();
        }
        if (edit instanceof final InsertEdit insertEdit)
        {
            return insertEdit.getText();
        }
        if (edit instanceof DeleteEdit)
        {
            return "";
        }
        throw new IllegalStateException("Unsupported text edit " + edit.getClass().getName() + ".");
    }

    private void notifyListeners(final DatasetModelChangeEvent event)
    {
        for (final DatasetModelListener listener : new ArrayList<>(listeners))
        {
            listener.modelChanged(event);
        }
    }

    private FlatXmlElement findElementContaining(final int offset)
    {
        final List<FlatXmlElement> elements = index.getElements();
        int low = 0;
        int high = elements.size() - 1;
        while (low <= high)
        {
            final int middle = (low + high) >>> 1;
            final FlatXmlElement element = elements.get(middle);
            if (offset < element.offset())
            {
                high = middle - 1;
            }
            else if (offset >= element.endOffset())
            {
                low = middle + 1;
            }
            else
            {
                return element;
            }
        }
        return null;
    }

    private static FlatXmlAttribute findAttribute(final FlatXmlElement element, final String columnName)
    {
        final String columnKey = columnName.toUpperCase(Locale.ENGLISH);
        FlatXmlAttribute found = null;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            if (attribute.name().toUpperCase(Locale.ENGLISH).equals(columnKey))
            {
                found = attribute;
            }
        }
        return found;
    }

    private String tableKey(final String name)
    {
        return options.caseSensitiveTableNames() ? name : name.toUpperCase(Locale.ENGLISH);
    }

    /**
     * Returns whether applying rowChanges (column key to new value) to a row would leave every column
     * NULL; a row with no values would leave its element without attributes, which is not a valid row.
     */
    private static boolean wouldEmptyRow(final DatasetTable table, final int rowIndex,
            final Map<String, String> rowChanges)
    {
        final DatasetRow row = table.getRows().get(rowIndex);
        for (int columnIndex = 0; columnIndex < table.getColumns().size(); columnIndex++)
        {
            final String columnKey = table.getColumns().get(columnIndex).name().toUpperCase(Locale.ENGLISH);
            final String value = rowChanges.containsKey(columnKey) ? rowChanges.get(columnKey)
                    : row.getValue(columnIndex);
            if (value != null)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the first key in columnKeys whose element has two or more attributes that differ only in
     * letter case, or null when none do.
     */
    private static String findCaseVariantColumnKey(final FlatXmlElement element, final Set<String> columnKeys)
    {
        for (final String columnKey : columnKeys)
        {
            if (hasCaseVariantAttributes(element, columnKey))
            {
                return columnKey;
            }
        }
        return null;
    }

    /**
     * Returns whether element has two or more attributes whose upper-cased name equals columnKey.
     */
    private static boolean hasCaseVariantAttributes(final FlatXmlElement element, final String columnKey)
    {
        int matches = 0;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            if (attribute.name().toUpperCase(Locale.ENGLISH).equals(columnKey))
            {
                matches++;
            }
        }
        return matches > 1;
    }

    /**
     * Returns an encoder for the document's current charset, falling back to UTF-8 when the supplier
     * returns null or throws; a thrown exception is logged.
     */
    private CharsetEncoder currentEncoder()
    {
        try
        {
            final Charset result = charset.get();
            return (result != null ? result : StandardCharsets.UTF_8).newEncoder();
        }
        catch (final RuntimeException e)
        {
            final String message = "The charset supplier failed, so the document escapes values for UTF-8.";
            warn(message, e);
            return StandardCharsets.UTF_8.newEncoder();
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

    /**
     * Inserts text as the last child of the root: before the root's end tag (at the start of its line
     * when only whitespace precedes it there), or, when the root is self-closing, by replacing it with an
     * open and close tag around the new text.
     */
    private TextEdit insertAsLastChildOfRoot(final String childrenText)
    {
        final FlatXmlRoot root = index.getRoot();
        final String delimiter = layout.getLineDelimiter();
        final String childIndentation = layout.childIndentation(root, index.getElements());
        if (root.selfClosing())
        {
            final String replacement =
                    ">" + delimiter + childIndentation + childrenText + delimiter + "</dataset>";
            return new ReplaceEdit(root.startTagEndOffset() - 2, 2, replacement);
        }
        if (layout.isAtStartOfItsLine(root.endTagOffset()))
        {
            final int lineStart = layout.startOfLineContaining(root.endTagOffset());
            return new InsertEdit(lineStart, childIndentation + childrenText + delimiter);
        }
        return new InsertEdit(root.endTagOffset(), delimiter + childIndentation + childrenText + delimiter);
    }

    private record DtdResolution(DtdDeclarations declarations, DtdState state)
    {
    }

    private record CachedDtd(String publicId, String text)
    {
    }
}
