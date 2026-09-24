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
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.preferences.PreferenceKeys;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IFindReplaceTarget;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.IEncodingSupport;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.IElementStateListener;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * The dbUnit Dataset Editor: a Tables page and a Source page over the same underlying document.
 *
 * @since 1.0.0
 */
public final class FlatXmlDatasetEditor extends MultiPageEditorPart
{
    /**
     * The identifier this editor is registered under in {@code plugin.xml}.
     */
    public static final String ID = "org.dbunit.eclipse.dataset.ui.flatXmlDatasetEditor";

    private static final int TABLES_PAGE_INDEX = 0;

    private static final int SOURCE_PAGE_INDEX = 1;

    private final IPropertyListener sourceInputListener = (source, propertyId) ->
    {
        if (propertyId == IEditorPart.PROP_INPUT)
        {
            handleInputChanged();
        }
    };

    private final IElementStateListener elementStateListener = new IElementStateListener()
    {
        @Override
        public void elementDirtyStateChanged(final Object element, final boolean isDirty)
        {
        }

        @Override
        public void elementContentAboutToBeReplaced(final Object element)
        {
        }

        @Override
        public void elementContentReplaced(final Object element)
        {
        }

        @Override
        public void elementDeleted(final Object element)
        {
            if (element != null && element.equals(sourceEditor.getEditorInput()))
            {
                closeIfClean();
            }
        }

        @Override
        public void elementMoved(final Object originalElement, final Object movedElement)
        {
            if (movedElement == null && originalElement != null
                    && originalElement.equals(sourceEditor.getEditorInput()))
            {
                closeIfClean();
            }
        }
    };

    private final IPartListener2 partListener = new IPartListener2()
    {
        @Override
        public void partActivated(final IWorkbenchPartReference partRef)
        {
            if (partRef.getPart(false) == FlatXmlDatasetEditor.this)
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
            if (getSite().getPage().getActivePart() == FlatXmlDatasetEditor.this)
            {
                refreshExternalState();
            }
        }

        @Override
        public void windowDeactivated(final IWorkbenchWindow window)
        {
        }

        @Override
        public void windowClosed(final IWorkbenchWindow window)
        {
        }

        @Override
        public void windowOpened(final IWorkbenchWindow window)
        {
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
        sourceEditor = new FlatXmlSourceEditor();
        final int sourcePageIndex;
        try
        {
            sourcePageIndex = addPage(sourceEditor, getEditorInput());
        }
        catch (final PartInitException e)
        {
            ErrorDialog.openError(getSite().getShell(), Messages.Editor_openErrorTitle, null, e.getStatus());
            return;
        }
        setPageText(sourcePageIndex, Messages.Editor_sourcePageText);

        final IDocument document =
                sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput());
        datasetDocument = new FlatXmlDatasetDocument(document,
                new EditorInputDtdSource(getEditorInput()), PreferenceKeys.readOptions(),
                this::currentCharset);
        datasetDocument.refresh();

        tablesPage = new TablesPage(getContainer(), this, datasetDocument);
        addPage(TABLES_PAGE_INDEX, tablesPage.getControl());
        setPageText(TABLES_PAGE_INDEX, Messages.Editor_tablesPageText);

        final boolean editable = datasetDocument.getModel().isEditable() || datasetDocument.isBlank();
        setActivePage(editable ? TABLES_PAGE_INDEX : SOURCE_PAGE_INDEX);
        previousPageIndex = getActivePage();

        sourceEditor.addPropertyListener(sourceInputListener);
        sourceEditor.getDocumentProvider().addElementStateListener(elementStateListener);
        getSite().getPage().addPartListener(partListener);
        getSite().getWorkbenchWindow().getWorkbench().addWindowListener(windowListener);
    }

    @Override
    protected void pageChange(final int newPageIndex)
    {
        if (previousPageIndex == TABLES_PAGE_INDEX)
        {
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
        sourceEditor.doSave(monitor);
    }

    @Override
    public void doSaveAs()
    {
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
            setActivePage(SOURCE_PAGE_INDEX);
            return adapterClass.cast(sourceEditor.getAdapter(IGotoMarker.class));
        }
        if (adapterClass == ITextEditor.class)
        {
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
        if (sourceEditor != null)
        {
            sourceEditor.removePropertyListener(sourceInputListener);
            sourceEditor.getDocumentProvider().removeElementStateListener(elementStateListener);
        }
        if (getSite() != null && getSite().getPage() != null)
        {
            getSite().getPage().removePartListener(partListener);
        }
        if (getSite() != null && getSite().getWorkbenchWindow() != null)
        {
            getSite().getWorkbenchWindow().getWorkbench().removeWindowListener(windowListener);
        }
        if (datasetDocument != null)
        {
            datasetDocument.dispose();
        }
        super.dispose();
    }

    private void handleInputChanged()
    {
        final IEditorInput newInput = sourceEditor.getEditorInput();
        setInputWithNotify(newInput);
        setPartName(newInput.getName());
        final IDocument document = sourceEditor.getDocumentProvider().getDocument(newInput);
        datasetDocument.rebind(document, new EditorInputDtdSource(newInput));
    }

    private void closeIfClean()
    {
        if (!isDirty())
        {
            Display.getDefault()
                    .asyncExec(() -> getSite().getPage().closeEditor(FlatXmlDatasetEditor.this, false));
        }
    }

    private void refreshExternalState()
    {
        sourceEditor.checkExternalModification();
        datasetDocument.reloadDtd();
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
        try
        {
            return Charset.forName(encoding);
        }
        catch (final IllegalCharsetNameException | UnsupportedCharsetException e)
        {
            return StandardCharsets.UTF_8;
        }
    }
}
