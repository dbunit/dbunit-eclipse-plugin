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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.osgi.util.NLS;
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
            // An undo that nothing refreshed left the pending columns in the state it undid, and the
            // stamp that this change leads to has no snapshot to put them right.
            final long modificationStamp = PendingColumns.modificationStampOf(document);
            pendingColumns.restore(modificationStamp);
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
        final List<TextEdit> edits = new RowInsertEdits(editContext()).plan(tableKey, table, rowIndex, rows);
        if (edits.isEmpty())
        {
            return;
        }
        applier.apply(document, edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void insertBlankRow(final String tableKey, final int rowIndex)
    {
        final DatasetTable table = editableTable(tableKey);
        final RowInsertEdits rowInsertEdits = new RowInsertEdits(editContext());
        final List<String> blankRow = rowInsertEdits.blankRow(tableKey, table, rowIndex);
        final List<TextEdit> edits = rowInsertEdits.plan(tableKey, table, rowIndex, List.of(blankRow));
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
            edits.addAll(new RowInsertEdits(context).plan(tableKey, table, table.getRows().size(), rows));
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
        return new EditContext(index, layout, charset, options, log);
    }

    private TableEdits tableEdits()
    {
        return new TableEdits(editContext(), getModel(), this::tableKeyOf);
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

    @Override
    public void duplicateRows(final String tableKey, final int[] rowIndexes)
    {
        final DatasetTable table = editableTable(tableKey);
        final List<TextEdit> edits = new RowEdits(editContext()).duplicateEdits(tableKey, table, rowIndexes);
        if (!edits.isEmpty())
        {
            applier.apply(document, edits);
            refreshInternal(ChangeOrigin.EDIT);
        }
    }

    @Override
    public void deleteRows(final String tableKey, final int[] rowIndexes)
    {
        final DatasetTable table = editableTable(tableKey);
        final List<TextEdit> edits = new RowEdits(editContext()).deleteEdits(tableKey, table, rowIndexes);
        if (!edits.isEmpty())
        {
            applier.apply(document, edits);
            final boolean deletedEveryRow = rowIndexes.length == table.getRows().size();
            if (deletedEveryRow)
            {
                pendingColumns.keepDataColumns(table);
            }
            refreshInternal(ChangeOrigin.EDIT);
        }
    }

    @Override
    public void moveRows(final String tableKey, final int firstRowIndex, final int rowCount,
            final int delta)
    {
        final DatasetTable table = editableTable(tableKey);
        final RowEdits rowEdits = new RowEdits(editContext());
        final List<TextEdit> edits = rowEdits.moveEdits(tableKey, table, firstRowIndex, rowCount, delta);
        applier.apply(document, edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void addColumn(final String tableKey, final String columnName)
    {
        final DatasetTable table = editableTable(tableKey);
        ColumnEdits.requireNewColumn(table, columnName);
        pendingColumns.addColumn(document, tableKey, columnName);
    }

    @Override
    public void renameColumn(final String tableKey, final String columnName, final String newColumnName)
    {
        final DatasetTable table = editableTable(tableKey);
        final ColumnEdits columnEdits = new ColumnEdits(editContext());
        final DatasetColumn column = columnEdits.requireRename(tableKey, table, columnName, newColumnName);
        if (column.pending())
        {
            pendingColumns.renameColumn(document, tableKey, columnName, newColumnName);
            return;
        }

        final List<TextEdit> edits =
                columnEdits.renameEdits(tableKey, table, column, columnName, newColumnName);
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
        final DatasetColumn column = ColumnEdits.requireExistingColumn(table, columnName);
        if (column.pending())
        {
            pendingColumns.deleteColumn(document, tableKey, columnName);
            return;
        }

        final ColumnEdits columnEdits = new ColumnEdits(editContext());
        final List<TextEdit> edits = columnEdits.deleteEdits(tableKey, table, column, columnName);
        if (edits.isEmpty())
        {
            return;
        }
        applier.apply(document, edits);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void addTable(final String tableName, final List<String> columnNames)
    {
        refreshForEdit();
        final List<TextEdit> edits = tableEdits().addEdits(tableName, columnNames);
        applier.apply(document, edits);
        pendingColumns.addTable(tableKeyOf(tableName), columnNames);
        refreshInternal(ChangeOrigin.EDIT);
    }

    @Override
    public void renameTable(final String tableKey, final String newTableName)
    {
        final DatasetTable tableToRename = editableTable(tableKey);
        final List<TextEdit> edits = tableEdits().renameEdits(tableKey, tableToRename, newTableName);
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
        final List<TextEdit> edits = tableEdits().deleteEdits(tableKey, tableToDelete);
        if (!edits.isEmpty())
        {
            applier.apply(document, edits);
        }

        pendingColumns.deleteTable(tableKey);
        stale = true;
        refreshInternal(ChangeOrigin.EDIT);
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

    /**
     * Tells whether the text is empty or only whitespace, as {@link String#isBlank()} does, from the text
     * as it is now. It reads characters up to the first one that is not whitespace instead of copying the
     * whole text, because the Tables page asks on each change.
     */
    @Override
    public boolean isBlank()
    {
        final int length = document.getLength();
        for (int offset = 0; offset < length; offset++)
        {
            if (!Character.isWhitespace(characterAt(offset)))
            {
                return false;
            }
        }
        return true;
    }

    private char characterAt(final int offset)
    {
        try
        {
            return document.getChar(offset);
        }
        catch (final BadLocationException e)
        {
            throw new IllegalStateException("The text has no character at " + offset + ".", e);
        }
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
        refresh();
        return new CellLocator(model, index, options).locate(address);
    }

    @Override
    public Optional<CellAddress> cellAt(final int offset)
    {
        refresh();
        return new CellLocator(model, index, options).cellAt(offset);
    }

    /**
     * Changes the case-sensitivity and column-sensing options, then refreshes. The pending columns of each
     * table follow it to the key that its name has under the new options.
     *
     * @param newOptions The new options.
     */
    public void setOptions(final FlatXmlOptions newOptions)
    {
        final List<DatasetTable> tables = model.getTables();
        this.options = newOptions;
        final Map<String, String> newKeysByOldKey = new HashMap<>();
        for (final DatasetTable table : tables)
        {
            newKeysByOldKey.put(table.getKey(), tableKeyOf(table.getName()));
        }
        pendingColumns.rekey(newKeysByOldKey);
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
        final long modificationStamp = PendingColumns.modificationStampOf(document);
        pendingColumns.restore(modificationStamp);
        final String text = document.get();
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final DtdResolution dtdResolution = dtdResolver.resolve(parse.doctype());
        final FlatXmlModelBuilder.Result built = FlatXmlModelBuilder.build(text, parse,
                dtdResolution.declarations(), options, pendingColumns.asMap());
        final List<DatasetTable> tables = built.model().getTables();
        pendingColumns.pruneBackedColumns(tables);
        if (listsEveryTable(parse, dtdResolution))
        {
            pendingColumns.pruneMissingTables(tables);
        }
        pendingColumns.record(modificationStamp);
        final List<DatasetProblem> problems = FlatXmlValidator.validate(parse, built.index(), tables,
                dtdResolution.state(), dtdResolution.declarations(), options);
        final DatasetModel oldModel = model;
        model = new DatasetModel(tables, problems, built.model().isEditable());
        index = built.index();
        layout = new FlatXmlTextLayout(text, TextUtilities.getDefaultLineDelimiter(document));
        stale = false;
        notifyListeners(new DatasetModelChangeEvent(oldModel, model, origin));
    }

    /**
     * Returns whether the model of a refresh lists every table of the document: the text must have been
     * parsed to its end, and the external DTD, which can declare tables that have no element, must have
     * been loaded when the DOCTYPE names one.
     */
    private static boolean listsEveryTable(final FlatXmlParseResult parse, final DtdResolution dtdResolution)
    {
        return parse.wellFormed() && dtdResolution.state() != DtdState.NOT_LOADED;
    }

    private void notifyListeners(final DatasetModelChangeEvent event)
    {
        for (final DatasetModelListener listener : new ArrayList<>(listeners))
        {
            listener.modelChanged(event);
        }
    }

}
