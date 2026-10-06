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
package org.dbunit.eclipse.dataset.ui.editor;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.ui.DatasetUiPlugin;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.preferences.PreferenceKeys;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IFindReplaceTarget;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.IEncodingSupport;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * The dbUnit Dataset Editor: a Tables page and a Source page over the same underlying document.
 *
 * @since 1.0.0
 */
public final class FlatXmlDatasetEditor extends MultiPageEditorPart
{
    private static final ILog LOG = ILog.of(FlatXmlDatasetEditor.class);

    /**
     * The identifier this editor is registered under in {@code plugin.xml}.
     */
    public static final String ID = "org.dbunit.eclipse.dataset.ui.flatXmlDatasetEditor";

    private static final int TABLES_PAGE_INDEX = 0;

    private static final int SOURCE_PAGE_INDEX = 1;

    private final EncodingResolver encodingResolver = new EncodingResolver();

    private final IPropertyListener sourceInputListener = (source, propertyId) ->
    {
        if (propertyId == IEditorPart.PROP_INPUT)
        {
            handleInputChanged();
        }
    };

    private final IPartListener2 partListener = new IPartListener2()
    {
        @Override
        public void partActivated(final IWorkbenchPartReference partRef)
        {
            if (FlatXmlDatasetEditor.this.equals(partRef.getPart(false)))
            {
                refreshExternalState();
            }
        }
    };

    private final IWindowListener windowListener = new IWindowListener()
    {
        @Override
        public void windowActivated(final IWorkbenchWindow window)
        {
            handleWindowActivated(window);
        }

        @Override
        public void windowDeactivated(final IWorkbenchWindow window)
        {
            // Nothing to do: the editor only reacts to a window being activated.
        }

        @Override
        public void windowClosed(final IWorkbenchWindow window)
        {
            // Nothing to do: the editor only reacts to a window being activated.
        }

        @Override
        public void windowOpened(final IWorkbenchWindow window)
        {
            // Nothing to do: the editor only reacts to a window being activated.
        }
    };

    private final IPreferenceStore preferenceStore = DatasetUiPlugin.getDefault().getPreferenceStore();

    private final IPropertyChangeListener preferenceListener = event ->
    {
        final String property = event.getProperty();
        // Preferences can change on any thread, for example when a setup tool applies them in a job.
        if (Display.getCurrent() == null)
        {
            Display.getDefault().asyncExec(() -> preferenceChanged(property));
        }
        else
        {
            preferenceChanged(property);
        }
    };

    private FlatXmlSourceEditor sourceEditor;

    private TablesPage tablesPage;

    private FlatXmlDatasetDocument datasetDocument;

    private int previousPageIndex = -1;

    @Override
    public void init(final IEditorSite site, final IEditorInput input) throws PartInitException
    {
        super.init(site, input);
        setPartName(input.getName());
    }

    @Override
    protected void createPages()
    {
        sourceEditor = new FlatXmlSourceEditor(this::cancelActiveCellEditor, this::closeEditor);
        final int sourcePageIndex;
        try
        {
            sourcePageIndex = addPage(sourceEditor, getEditorInput());
        }
        catch (final PartInitException e)
        {
            throw new IllegalStateException(e.getStatus().getMessage(), e);
        }
        setPageText(sourcePageIndex, Messages.Editor_sourcePageText);

        final IDocument document =
                sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput());
        datasetDocument = new FlatXmlDatasetDocument(document,
                new EditorInputDtdSource(getEditorInput()), PreferenceKeys.readOptions(),
                this::currentCharset, LOG::log);
        datasetDocument.refresh();

        tablesPage = new TablesPage(getContainer(), this, datasetDocument);
        addPage(TABLES_PAGE_INDEX, tablesPage.getControl());
        setPageText(TABLES_PAGE_INDEX, Messages.Editor_tablesPageText);

        final boolean editable = datasetDocument.getModel().isEditable() || datasetDocument.isBlank();
        setActivePage(editable ? TABLES_PAGE_INDEX : SOURCE_PAGE_INDEX);
        previousPageIndex = getActivePage();

        sourceEditor.addPropertyListener(sourceInputListener);
        getSite().getPage().addPartListener(partListener);
        getSite().getWorkbenchWindow().getWorkbench().addWindowListener(windowListener);
        preferenceStore.addPropertyChangeListener(preferenceListener);
    }

    @Override
    protected void pageChange(final int newPageIndex)
    {
        if (previousPageIndex == TABLES_PAGE_INDEX)
        {
            tablesPage.commitActiveCellEditor();
            tablesPage.deactivate();
        }
        super.pageChange(newPageIndex);
        if (newPageIndex == TABLES_PAGE_INDEX)
        {
            datasetDocument.refresh();
            tablesPage.activate();
        }
        previousPageIndex = newPageIndex;
    }

    @Override
    public void doSave(final IProgressMonitor monitor)
    {
        commitActiveCellEditor();
        sourceEditor.doSave(monitor);
    }

    @Override
    public void doSaveAs()
    {
        commitActiveCellEditor();
        sourceEditor.doSaveAs();
    }

    @Override
    public boolean isSaveAsAllowed()
    {
        return true;
    }

    FlatXmlSourceEditor getSourceEditor()
    {
        return sourceEditor;
    }

    FlatXmlDatasetDocument getDatasetDocument()
    {
        return datasetDocument;
    }

    TablesPage getTablesPage()
    {
        return tablesPage;
    }

    String getPageTitle(final int pageIndex)
    {
        return getPageText(pageIndex);
    }

    boolean isSourcePageActive()
    {
        return getActivePage() == SOURCE_PAGE_INDEX;
    }

    void showOnSourcePage(final int offset, final int length)
    {
        setActivePage(SOURCE_PAGE_INDEX);
        sourceEditor.selectAndReveal(offset, length);
    }

    @Override
    public <T> T getAdapter(final Class<T> adapterClass)
    {
        if (adapterClass == IGotoMarker.class)
        {
            final IGotoMarker gotoMarker = marker ->
            {
                setActivePage(SOURCE_PAGE_INDEX);
                sourceEditor.getAdapter(IGotoMarker.class).gotoMarker(marker);
            };
            return adapterClass.cast(gotoMarker);
        }
        if (adapterClass == ITextEditor.class)
        {
            return adapterClass.cast(sourceEditor);
        }
        if (adapterClass == AbstractTextEditor.class)
        {
            // A text editor asks whether its file changed on disk only after the part that holds it was
            // activated, which it counts only when that part answers with the editor. The inherited answer
            // is the editor of the page that is shown, so the Source editor would never ask on the Tables
            // page. The zoom commands of the workbench ask the same question, so they are enabled there too.
            return adapterClass.cast(sourceEditor);
        }
        if (adapterClass == IFindReplaceTarget.class)
        {
            return getActivePage() == SOURCE_PAGE_INDEX ? sourceEditor.getAdapter(adapterClass) : null;
        }
        return super.getAdapter(adapterClass);
    }

    @Override
    public void dispose()
    {
        removeSourceEditorListeners();
        removeWorkbenchListeners();
        preferenceStore.removePropertyChangeListener(preferenceListener);
        if (datasetDocument != null)
        {
            datasetDocument.dispose();
        }
        if (tablesPage != null)
        {
            tablesPage.dispose();
        }
        super.dispose();
    }

    private void removeSourceEditorListeners()
    {
        if (sourceEditor != null)
        {
            sourceEditor.removePropertyListener(sourceInputListener);
        }
    }

    private void removeWorkbenchListeners()
    {
        if (getSite() != null && getSite().getPage() != null)
        {
            getSite().getPage().removePartListener(partListener);
        }
        if (getSite() != null && getSite().getWorkbenchWindow() != null)
        {
            getSite().getWorkbenchWindow().getWorkbench().removeWindowListener(windowListener);
        }
    }

    private void handleInputChanged()
    {
        final IEditorInput newInput = sourceEditor.getEditorInput();
        setInputWithNotify(newInput);
        setPartName(newInput.getName());
        final IDocument document = sourceEditor.getDocumentProvider().getDocument(newInput);
        datasetDocument.rebind(document, new EditorInputDtdSource(newInput));
        tablesPage.inputChanged();
    }

    private void commitActiveCellEditor()
    {
        if (tablesPage != null)
        {
            tablesPage.commitActiveCellEditor();
        }
    }

    private void cancelActiveCellEditor()
    {
        if (tablesPage != null)
        {
            tablesPage.cancelActiveCellEditor();
        }
    }

    private void closeEditor(final boolean save)
    {
        Display.getDefault().asyncExec(() ->
        {
            // An earlier request to close the editor may have closed it already, which takes its page away.
            final IWorkbenchPage page = getSite().getPage();
            if (page != null)
            {
                page.closeEditor(FlatXmlDatasetEditor.this, save);
            }
        });
    }

    /**
     * Checks the files that this editor shows when its own window is activated and it is the active part of
     * its page. The workbench tells every editor of every window about each window that is activated, and
     * the editor of another window has nothing to check then: a DTD that it read again or a prompt that it
     * showed would belong to the window that the user left.
     *
     * @param window The window that was activated.
     */
    void handleWindowActivated(final IWorkbenchWindow window)
    {
        final boolean ownWindow = Objects.equals(getSite().getWorkbenchWindow(), window);
        if (ownWindow && FlatXmlDatasetEditor.this.equals(getSite().getPage().getActivePart()))
        {
            refreshExternalState();
        }
    }

    private void refreshExternalState()
    {
        sourceEditor.checkExternalModification();
        datasetDocument.reloadDtd();
        tablesPage.refreshInputState();
    }

    private void preferenceChanged(final String property)
    {
        if (tablesPage.getControl().isDisposed())
        {
            return;
        }
        if (PreferenceKeys.NULL_DISPLAY_TEXT.equals(property))
        {
            tablesPage.repaintGrids();
        }
        else if (PreferenceKeys.ASSUME_COLUMN_SENSING.equals(property)
                || PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES.equals(property))
        {
            datasetDocument.setOptions(PreferenceKeys.readOptions());
        }
    }

    Charset currentCharset()
    {
        final IEncodingSupport encodingSupport = sourceEditor.getAdapter(IEncodingSupport.class);
        if (encodingSupport == null)
        {
            return StandardCharsets.UTF_8;
        }
        final String encoding = encodingSupport.getEncoding() != null ? encodingSupport.getEncoding()
                : encodingSupport.getDefaultEncoding();
        if (encoding == null)
        {
            return StandardCharsets.UTF_8;
        }
        return encodingResolver.charsetOf(encoding);
    }
}
