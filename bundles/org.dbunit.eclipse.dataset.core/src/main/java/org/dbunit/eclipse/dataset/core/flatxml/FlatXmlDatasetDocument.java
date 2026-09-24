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
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.DocumentRewriteSession;
import org.eclipse.jface.text.DocumentRewriteSessionType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
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
    private static final int REWRITE_SESSION_EDIT_THRESHOLD = 50;

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
        final DocumentRewriteSession session = outermost ? startRewriteSession() : null;
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
            if (batchDepth == 0)
            {
                if (undoManager != null)
                {
                    undoManager.endCompoundChange();
                }
                stopRewriteSession(session);
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
                                + "' with no values.");
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
        throw new UnsupportedOperationException("insertRows is not implemented yet.");
    }

    @Override
    public void duplicateRows(final String tableKey, final int[] rowIndexes)
    {
        throw new UnsupportedOperationException("duplicateRows is not implemented yet.");
    }

    @Override
    public void deleteRows(final String tableKey, final int[] rowIndexes)
    {
        throw new UnsupportedOperationException("deleteRows is not implemented yet.");
    }

    @Override
    public void moveRows(final String tableKey, final int firstRowIndex, final int rowCount,
            final int delta)
    {
        throw new UnsupportedOperationException("moveRows is not implemented yet.");
    }

    @Override
    public void addColumn(final String tableKey, final String columnName)
    {
        throw new UnsupportedOperationException("addColumn is not implemented yet.");
    }

    @Override
    public void renameColumn(final String tableKey, final String columnName, final String newColumnName)
    {
        throw new UnsupportedOperationException("renameColumn is not implemented yet.");
    }

    @Override
    public void deleteColumn(final String tableKey, final String columnName)
    {
        throw new UnsupportedOperationException("deleteColumn is not implemented yet.");
    }

    @Override
    public void addTable(final String tableName, final List<String> columnNames)
    {
        throw new UnsupportedOperationException("addTable is not implemented yet.");
    }

    @Override
    public void renameTable(final String tableKey, final String newTableName)
    {
        throw new UnsupportedOperationException("renameTable is not implemented yet.");
    }

    @Override
    public void deleteTable(final String tableKey)
    {
        throw new UnsupportedOperationException("deleteTable is not implemented yet.");
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
        final String newText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + delimiter + "<dataset>"
                + delimiter + "</dataset>" + delimiter;
        apply(List.of(new ReplaceEdit(0, document.getLength(), newText)));
        refreshInternal(ChangeOrigin.EDIT);
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
        final List<DatasetProblem> problems = FlatXmlValidator.validate(parse, built.index(),
                built.model().getTables(), dtdResolution.state(), dtdResolution.declarations(), options);
        final DatasetModel oldModel = model;
        model = new DatasetModel(built.model().getTables(), problems, built.model().isEditable());
        index = built.index();
        stale = false;
        notifyListeners(new DatasetModelChangeEvent(oldModel, model, origin));
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
        final MultiTextEdit root = new MultiTextEdit();
        for (final TextEdit edit : edits)
        {
            root.addChild(edit);
        }
        final boolean outermost = batchDepth == 0;
        final boolean largeChange = edits.size() > REWRITE_SESSION_EDIT_THRESHOLD;
        final DocumentRewriteSession session = outermost && largeChange ? startRewriteSession() : null;
        final IDocumentUndoManager undoManager =
                DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        if (outermost && undoManager != null)
        {
            undoManager.beginCompoundChange();
        }
        try
        {
            root.apply(document, TextEdit.NONE);
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
            stopRewriteSession(session);
        }
    }

    private DocumentRewriteSession startRewriteSession()
    {
        if (document instanceof final IDocumentExtension4 extension)
        {
            return extension.startRewriteSession(DocumentRewriteSessionType.UNRESTRICTED);
        }
        return null;
    }

    private void stopRewriteSession(final DocumentRewriteSession session)
    {
        if (session != null)
        {
            ((IDocumentExtension4) document).stopRewriteSession(session);
        }
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

    private record DtdResolution(DtdDeclarations declarations, DtdState state)
    {
    }

    private record CachedDtd(String publicId, String text)
    {
    }
}
