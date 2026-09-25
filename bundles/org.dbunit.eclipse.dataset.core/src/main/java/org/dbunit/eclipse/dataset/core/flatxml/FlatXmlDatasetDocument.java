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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

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
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
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
    private static final int JOIN_EDITS_THRESHOLD = 50;

    private static final FlatXmlIndex EMPTY_INDEX =
            new FlatXmlIndex(null, null, "", List.of(), Map.of(), Map.of(), Map.of());

    private final IDocumentListener documentListener = new IDocumentListener()
    {
        @Override
        public void documentAboutToBeChanged(final DocumentEvent event)
        {
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

    private final Supplier<Charset> charset;

    private IDocument document;

    private DtdSource dtdSource;

    private FlatXmlOptions options;

    private DatasetModel model;

    private FlatXmlIndex index;

    private FlatXmlTextLayout layout;

    private boolean stale;

    private int batchDepth;

    /**
     * Creates a dataset document bound to a text document.
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
        this.document = document;
        this.dtdSource = dtdSource;
        this.options = options;
        this.charset = charset;
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
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        final DatasetTable table = getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException("There is no table '" + tableKey + "'."));
        for (final CellChange change : changes)
        {
            if (change.rowIndex() < 0 || change.rowIndex() >= table.getRows().size())
            {
                throw new DatasetEditException(
                        "Row " + change.rowIndex() + " does not exist in table '" + table.getName()
                                + "'.");
            }
            if (table.getColumnIndex(change.columnName()) < 0)
            {
                throw new DatasetEditException("Column '" + change.columnName() + "' does not exist in "
                        + "table '" + table.getName() + "'.");
            }
        }

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
            if (wouldEmptyRow(table, rowIndex, rowChanges))
            {
                throw new DatasetEditException(
                        "This change would leave row " + rowIndex + " of table '" + table.getName()
                                + "' with no values. Use Delete Rows to remove it instead.");
            }
            final FlatXmlElement element = rowElements.get(rowIndex);
            final String rewritten = StartTagRewriter.rewrite(text, element, table.getColumns(),
                    rowChanges, Map.of(), encoder);
            if (rewritten != null)
            {
                edits.add(new ReplaceEdit(element.nameEndOffset(),
                        element.attributesEndOffset() - element.nameEndOffset(), rewritten));
            }
        }
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
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        final DatasetTable table = getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException("There is no table '" + tableKey + "'."));
        if (table.getColumns().isEmpty())
        {
            throw new DatasetEditException(
                    "Table '" + table.getName() + "' has no columns to insert a row into.");
        }
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        if (rowIndex < 0 || rowIndex > rowElements.size())
        {
            throw new DatasetEditException(
                    "Row " + rowIndex + " is out of range for table '" + table.getName() + "'.");
        }
        for (final List<String> values : rows)
        {
            if (values.size() != table.getColumns().size())
            {
                throw new DatasetEditException("Each new row must have exactly "
                        + table.getColumns().size() + " values for table '" + table.getName() + "'.");
            }
        }
        if (rows.isEmpty())
        {
            return;
        }

        final CharsetEncoder encoder = currentEncoder();
        final String delimiter = layout.getLineDelimiter();
        final List<String> rowTexts = new ArrayList<>();
        for (final List<String> values : rows)
        {
            rowTexts.add(buildRowText(table, normalizeAllNullRow(values), encoder));
        }

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

        apply(List.of(edit));
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void duplicateRows(final String tableKey, final int[] rowIndexes)
    {
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        final DatasetTable table = getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException("There is no table '" + tableKey + "'."));
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        final int[] sorted = rowIndexes.clone();
        Arrays.sort(sorted);
        for (final int rowIndex : sorted)
        {
            if (rowIndex < 0 || rowIndex >= rowElements.size())
            {
                throw new DatasetEditException(
                        "Row " + rowIndex + " is out of range for table '" + table.getName() + "'.");
            }
        }
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
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        final DatasetTable table = getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException("There is no table '" + tableKey + "'."));
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        final int[] sorted = rowIndexes.clone();
        Arrays.sort(sorted);
        for (final int rowIndex : sorted)
        {
            if (rowIndex < 0 || rowIndex >= rowElements.size())
            {
                throw new DatasetEditException(
                        "Row " + rowIndex + " is out of range for table '" + table.getName() + "'.");
            }
        }
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

    @Override
    public void moveRows(final String tableKey, final int firstRowIndex, final int rowCount,
            final int delta)
    {
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        final DatasetTable table = getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException("There is no table '" + tableKey + "'."));
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        if (firstRowIndex < 0 || rowCount < 1 || firstRowIndex + rowCount > rowElements.size())
        {
            throw new DatasetEditException(
                    "The row block is out of range for table '" + table.getName() + "'.");
        }
        if (delta != -1 && delta != 1)
        {
            throw new DatasetEditException("A row block can only move up or down by one position at a "
                    + "time.");
        }
        if (delta < 0 && firstRowIndex == 0)
        {
            throw new DatasetEditException(
                    "Cannot move past the first row of table '" + table.getName() + "'.");
        }
        if (delta > 0 && firstRowIndex + rowCount >= rowElements.size())
        {
            throw new DatasetEditException(
                    "Cannot move past the last row of table '" + table.getName() + "'.");
        }

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

        apply(edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void addColumn(final String tableKey, final String columnName)
    {
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        final DatasetTable table = getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException("There is no table '" + tableKey + "'."));
        if (!XmlNames.isValidName(columnName))
        {
            throw new DatasetEditException("'" + columnName + "' is not a valid column name.");
        }
        if (table.getColumnIndex(columnName) >= 0)
        {
            throw new DatasetEditException(
                    "Table '" + table.getName() + "' already has a column named '" + columnName + "'.");
        }

        pendingColumns.computeIfAbsent(tableKey, unused -> new ArrayList<>()).add(columnName);
        stale = true;
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void renameColumn(final String tableKey, final String columnName, final String newColumnName)
    {
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        final DatasetTable table = getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException("There is no table '" + tableKey + "'."));
        final int columnIndex = table.getColumnIndex(columnName);
        if (columnIndex < 0)
        {
            throw new DatasetEditException(
                    "Column '" + columnName + "' does not exist in table '" + table.getName() + "'.");
        }
        if (!XmlNames.isValidName(newColumnName))
        {
            throw new DatasetEditException("'" + newColumnName + "' is not a valid column name.");
        }
        final int conflictingIndex = table.getColumnIndex(newColumnName);
        if (conflictingIndex >= 0 && conflictingIndex != columnIndex)
        {
            throw new DatasetEditException("Table '" + table.getName() + "' already has a column named '"
                    + newColumnName + "'.");
        }

        final DatasetColumn column = table.getColumns().get(columnIndex);
        final String key = columnName.toUpperCase(Locale.ENGLISH);
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        for (final FlatXmlElement element : rowElements)
        {
            int matches = 0;
            for (final FlatXmlAttribute attribute : element.attributes())
            {
                if (attribute.name().toUpperCase(Locale.ENGLISH).equals(key))
                {
                    matches++;
                }
            }
            if (matches > 1)
            {
                throw new DatasetEditException("Cannot rename column '" + column.name() + "' in table '"
                        + table.getName() + "' because a row has two attributes for it that differ only "
                        + "in letter case; remove one of them on the Source page first.");
            }
        }

        if (column.pending())
        {
            final List<String> pending = pendingColumns.get(tableKey);
            if (pending != null)
            {
                for (int i = 0; i < pending.size(); i++)
                {
                    if (pending.get(i).equalsIgnoreCase(columnName))
                    {
                        pending.set(i, newColumnName);
                        break;
                    }
                }
            }
            stale = true;
            refreshInternal(ChangeOrigin.EDIT);
            return;
        }

        final Map<String, String> renames = new LinkedHashMap<>();
        renames.put(key, newColumnName);
        final CharsetEncoder encoder = currentEncoder();
        final String text = document.get();
        final List<TextEdit> edits = new ArrayList<>();
        for (final FlatXmlElement element : rowElements)
        {
            final String rewritten =
                    StartTagRewriter.rewrite(text, element, table.getColumns(), Map.of(), renames, encoder);
            if (rewritten != null)
            {
                edits.add(new ReplaceEdit(element.nameEndOffset(),
                        element.attributesEndOffset() - element.nameEndOffset(), rewritten));
            }
        }
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
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        final DatasetTable table = getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException("There is no table '" + tableKey + "'."));
        final int columnIndex = table.getColumnIndex(columnName);
        if (columnIndex < 0)
        {
            throw new DatasetEditException(
                    "Column '" + columnName + "' does not exist in table '" + table.getName() + "'.");
        }
        final DatasetColumn column = table.getColumns().get(columnIndex);

        if (column.pending())
        {
            final List<String> pending = pendingColumns.get(tableKey);
            if (pending != null)
            {
                pending.removeIf(name -> name.equalsIgnoreCase(columnName));
                if (pending.isEmpty())
                {
                    pendingColumns.remove(tableKey);
                }
            }
            stale = true;
            refreshInternal(ChangeOrigin.EDIT);
            return;
        }

        final String key = columnName.toUpperCase(Locale.ENGLISH);
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        for (final FlatXmlElement element : rowElements)
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
            if (hasColumn && remaining == 0)
            {
                throw new DatasetEditException("Cannot delete column '" + column.name() + "' from table '"
                        + table.getName() + "' because it would leave a row with no values.");
            }
        }

        final Map<String, String> changes = new LinkedHashMap<>();
        changes.put(key, null);
        final CharsetEncoder encoder = currentEncoder();
        final String text = document.get();
        final List<TextEdit> edits = new ArrayList<>();
        for (final FlatXmlElement element : rowElements)
        {
            final String rewritten =
                    StartTagRewriter.rewrite(text, element, table.getColumns(), changes, Map.of(), encoder);
            if (rewritten != null)
            {
                edits.add(new ReplaceEdit(element.nameEndOffset(),
                        element.attributesEndOffset() - element.nameEndOffset(), rewritten));
            }
        }
        if (edits.isEmpty())
        {
            return;
        }
        apply(edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void addTable(final String tableName, final List<String> columnNames)
    {
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        if (!XmlNames.isValidName(tableName))
        {
            throw new DatasetEditException("'" + tableName + "' is not a valid table name.");
        }
        if (isReservedRootName(tableName))
        {
            throw new DatasetEditException("'" + tableName + "' is reserved for the root element.");
        }
        for (final DatasetTable existing : getModel().getTables())
        {
            if (sameTableName(existing.getName(), tableName))
            {
                throw new DatasetEditException("A table named '" + tableName + "' already exists.");
            }
        }
        final List<String> seenColumnNames = new ArrayList<>();
        for (final String columnName : columnNames)
        {
            if (!XmlNames.isValidName(columnName))
            {
                throw new DatasetEditException("'" + columnName + "' is not a valid column name.");
            }
            for (final String seenColumnName : seenColumnNames)
            {
                if (seenColumnName.equalsIgnoreCase(columnName))
                {
                    throw new DatasetEditException("Table '" + tableName + "' already has a column named '"
                            + columnName + "'.");
                }
            }
            seenColumnNames.add(columnName);
        }

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
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException("There is no table '" + tableKey + "'."));
        if (!XmlNames.isValidName(newTableName))
        {
            throw new DatasetEditException("'" + newTableName + "' is not a valid table name.");
        }
        if (isReservedRootName(newTableName))
        {
            throw new DatasetEditException("'" + newTableName + "' is reserved for the root element.");
        }
        for (final DatasetTable existing : getModel().getTables())
        {
            if (!existing.getKey().equals(tableKey) && sameTableName(existing.getName(), newTableName))
            {
                throw new DatasetEditException("A table named '" + newTableName + "' already exists.");
            }
        }

        final List<FlatXmlElement> elements = index.getAllElementsInOrder(tableKey);
        final List<TextEdit> edits = new ArrayList<>();
        for (final FlatXmlElement element : elements)
        {
            edits.add(new ReplaceEdit(element.offset() + 1, element.name().length(), newTableName));
            if (!element.selfClosing())
            {
                edits.add(new ReplaceEdit(element.endTagOffset() + 2, element.name().length(),
                        newTableName));
            }
        }
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
        refresh();
        if (!getModel().isEditable())
        {
            throw new DatasetEditException("Cannot edit because the source has errors that block "
                    + "editing.");
        }
        getModel().findTable(tableKey)
                .orElseThrow(() -> new DatasetEditException("There is no table '" + tableKey + "'."));

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

    private boolean isReservedRootName(final String tableName)
    {
        return sameTableName("dataset", tableName);
    }

    private boolean sameTableName(final String oneName, final String otherName)
    {
        return options.caseSensitiveTableNames() ? oneName.equals(otherName)
                : oneName.equalsIgnoreCase(otherName);
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
            throw new DatasetEditException("Cannot create an empty dataset because the document is not "
                    + "blank.");
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
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            if (offset >= attribute.nameOffset() && offset < attribute.endOffset())
            {
                final int columnIndex = table.get().getColumnIndex(attribute.name());
                if (columnIndex >= 0)
                {
                    return Optional.of(new CellAddress(key, rowIndex, columnIndex));
                }
            }
        }
        return Optional.of(new CellAddress(key, rowIndex, 0));
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
            final String freshText = dtdSource.load(cached.publicId(), systemId).orElse(null);
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
        final String text = document.get();
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final DtdResolution dtdResolution = resolveDtd(parse.doctype());
        final FlatXmlModelBuilder.Result built = FlatXmlModelBuilder.build(text, parse,
                dtdResolution.declarations(), options, pendingColumns);
        prunePendingColumns(built.model().getTables());
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
     * Drops each table's pending columns that a fresh build now finds backed by data or the DTD, so a
     * pending column never lingers once it is no longer needed.
     */
    private void prunePendingColumns(final List<DatasetTable> tables)
    {
        for (final DatasetTable table : tables)
        {
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
    }

    private static boolean isBackedByRealColumn(final DatasetTable table, final String name)
    {
        final int columnIndex = table.getColumnIndex(name);
        return columnIndex >= 0 && !table.getColumns().get(columnIndex).pending();
    }

    private DtdResolution resolveDtd(final FlatXmlDoctype doctype)
    {
        if (doctype == null)
        {
            return new DtdResolution(null, DtdState.NONE);
        }
        final String internalSubsetText = doctype.internalSubset();
        final DtdDeclarations internalSubset =
                DtdReader.read(internalSubsetText == null ? "" : internalSubsetText);
        if (doctype.systemId() == null)
        {
            return new DtdResolution(internalSubset, DtdState.LOADED);
        }
        final String externalText = loadExternalDtd(doctype.publicId(), doctype.systemId());
        if (externalText == null)
        {
            return new DtdResolution(internalSubset, DtdState.NOT_LOADED);
        }
        return new DtdResolution(internalSubset.merge(DtdReader.read(externalText)), DtdState.LOADED);
    }

    private String loadExternalDtd(final String publicId, final String systemId)
    {
        final CachedDtd cached = dtdCache.get(systemId);
        if (cached != null)
        {
            return cached.text();
        }
        final String text = dtdSource.load(publicId, systemId).orElse(null);
        dtdCache.put(systemId, new CachedDtd(publicId, text));
        return text;
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
            change.apply(document, TextEdit.NONE);
        }
        catch (final MalformedTreeException | BadLocationException e)
        {
            throw new IllegalStateException("Computed text edits do not fit the document.", e);
        }
        finally
        {
            if (outermost && undoManager != null)
            {
                undoManager.endCompoundChange();
            }
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
        FlatXmlAttribute found = null;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            if (attribute.name().equalsIgnoreCase(columnName))
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
     * Returns an encoder for the document's current charset, falling back to UTF-8 when the supplier
     * returns null or throws.
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
     * Returns values unchanged, unless every value is null, in which case the first column becomes the
     * empty string instead: a row cannot have every value NULL, because an element without attributes is
     * not a row.
     */
    private static List<String> normalizeAllNullRow(final List<String> values)
    {
        for (final String value : values)
        {
            if (value != null)
            {
                return values;
            }
        }
        if (values.isEmpty())
        {
            return values;
        }
        final List<String> normalized = new ArrayList<>(values);
        normalized.set(0, "");
        return normalized;
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
            final String replacement = "<dataset>" + delimiter + childIndentation + childrenText
                    + delimiter + "</dataset>";
            return new ReplaceEdit(root.offset(), root.endOffset() - root.offset(), replacement);
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
