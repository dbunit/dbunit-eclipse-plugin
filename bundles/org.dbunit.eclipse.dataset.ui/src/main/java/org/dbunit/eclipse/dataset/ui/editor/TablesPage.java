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

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * The Tables page of {@link FlatXmlDatasetEditor}: one sheet tab per table, an {@link ErrorBanner}, and a
 * blank-document state.
 *
 * @since 1.0.0
 */
final class TablesPage
{
    private final FlatXmlDatasetEditor editor;

    private final FlatXmlDatasetDocument datasetDocument;

    private final LocalResourceManager resources;

    private final Composite control;

    private final ErrorBanner errorBanner;

    private final Composite contentStack;

    private final StackLayout contentStackLayout;

    private final Composite blankComposite;

    private final CTabFolder tabFolder;

    private final Button createEmptyDatasetButton;

    private final Map<String, CTabItem> tabsByKey = new LinkedHashMap<>();

    private final IDocumentListener sourceDocumentListener = new IDocumentListener()
    {
        @Override
        public void documentAboutToBeChanged(final DocumentEvent event)
        {
        }

        @Override
        public void documentChanged(final DocumentEvent event)
        {
            refreshPending = true;
            if (active)
            {
                scheduleRefresh();
            }
        }
    };

    private boolean active;

    private boolean refreshPending;

    private boolean refreshScheduled;

    private boolean editable;

    private String expectedRenameOldKey;

    private String expectedRenameNewKey;

    TablesPage(final Composite parent, final FlatXmlDatasetEditor editor,
            final FlatXmlDatasetDocument datasetDocument)
    {
        this.editor = editor;
        this.datasetDocument = datasetDocument;
        resources = new LocalResourceManager(JFaceResources.getResources(), parent);

        control = new Composite(parent, SWT.NONE);
        final GridLayout controlLayout = new GridLayout(1, false);
        controlLayout.marginWidth = 0;
        controlLayout.marginHeight = 0;
        control.setLayout(controlLayout);

        errorBanner = new ErrorBanner(control, editor);

        contentStack = new Composite(control, SWT.NONE);
        contentStack.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        contentStackLayout = new StackLayout();
        contentStack.setLayout(contentStackLayout);

        blankComposite = new Composite(contentStack, SWT.NONE);
        blankComposite.setLayout(new GridLayout(1, false));
        final Label blankLabel = new Label(blankComposite, SWT.CENTER);
        blankLabel.setText("The file is empty.");
        blankLabel.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, true, true));
        createEmptyDatasetButton = new Button(blankComposite, SWT.PUSH);
        createEmptyDatasetButton.setText("Create Empty Dataset");
        createEmptyDatasetButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, true));
        createEmptyDatasetButton.addSelectionListener(
                SelectionListener.widgetSelectedAdapter(event -> createEmptyDataset()));

        tabFolder = new CTabFolder(contentStack, SWT.TOP | SWT.BORDER | SWT.FLAT);

        sourceDocument().addDocumentListener(sourceDocumentListener);
        datasetDocument.addModelListener(event -> reconcile());

        reconcile();
    }

    Control getControl()
    {
        return control;
    }

    void activate()
    {
        active = true;
        if (refreshPending)
        {
            refreshPending = false;
            datasetDocument.refresh();
        }
    }

    void deactivate()
    {
        active = false;
    }

    /**
     * Tells the page that the table currently keyed {@code oldKey} is about to become {@code newKey}, so
     * the next reconciliation keeps its tab instead of disposing and recreating it.
     *
     * @param oldKey The table's key before the rename.
     * @param newKey The table's key after the rename.
     */
    void expectRename(final String oldKey, final String newKey)
    {
        expectedRenameOldKey = oldKey;
        expectedRenameNewKey = newKey;
    }

    boolean isEditable()
    {
        return editable;
    }

    CTabFolder getTabFolder()
    {
        return tabFolder;
    }

    ErrorBanner getErrorBanner()
    {
        return errorBanner;
    }

    boolean isShowingBlankState()
    {
        return contentStackLayout.topControl == blankComposite;
    }

    Button getCreateEmptyDatasetButton()
    {
        return createEmptyDatasetButton;
    }

    private IDocument sourceDocument()
    {
        final ITextEditor sourceEditor = editor.getSourceEditor();
        return sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput());
    }

    private void scheduleRefresh()
    {
        if (refreshScheduled)
        {
            return;
        }
        refreshScheduled = true;
        control.getDisplay().asyncExec(() ->
        {
            refreshScheduled = false;
            if (control.isDisposed())
            {
                return;
            }
            if (refreshPending)
            {
                refreshPending = false;
                datasetDocument.refresh();
            }
        });
    }

    private void reconcile()
    {
        if (control.isDisposed())
        {
            return;
        }
        final DatasetModel model = datasetDocument.getModel();
        final boolean inputModifiable = editor.getSourceEditor().isEditorInputModifiable();
        editable = model.isEditable() && inputModifiable;

        if (datasetDocument.isBlank())
        {
            createEmptyDatasetButton.setEnabled(inputModifiable);
            contentStackLayout.topControl = blankComposite;
        }
        else
        {
            reconcileTabs(model);
            contentStackLayout.topControl = tabFolder;
        }
        contentStack.layout();

        updateBanner(model);
    }

    private void reconcileTabs(final DatasetModel model)
    {
        final Set<String> seenKeys = new HashSet<>();
        int index = 0;
        for (final DatasetTable table : model.getTables())
        {
            final String key = resolveRenamedKey(table.getKey());
            seenKeys.add(key);
            CTabItem item = tabsByKey.get(key);
            if (item == null)
            {
                item = new CTabItem(tabFolder, SWT.NONE, index);
                item.setControl(new Composite(tabFolder, SWT.NONE));
                tabsByKey.put(key, item);
            }
            else if (tabFolder.indexOf(item) != index)
            {
                item = moveTab(item, index);
                tabsByKey.put(key, item);
            }
            updateTab(item, table, model);
            index++;
        }
        expectedRenameOldKey = null;
        expectedRenameNewKey = null;

        final Iterator<Map.Entry<String, CTabItem>> iterator = tabsByKey.entrySet().iterator();
        while (iterator.hasNext())
        {
            final Map.Entry<String, CTabItem> entry = iterator.next();
            if (!seenKeys.contains(entry.getKey()))
            {
                entry.getValue().getControl().dispose();
                entry.getValue().dispose();
                iterator.remove();
            }
        }
    }

    private String resolveRenamedKey(final String currentKey)
    {
        if (currentKey.equals(expectedRenameNewKey) && tabsByKey.containsKey(expectedRenameOldKey))
        {
            tabsByKey.put(currentKey, tabsByKey.remove(expectedRenameOldKey));
        }
        return currentKey;
    }

    private CTabItem moveTab(final CTabItem oldItem, final int index)
    {
        final Control tabControl = oldItem.getControl();
        oldItem.setControl(null);
        oldItem.dispose();
        final CTabItem newItem = new CTabItem(tabFolder, SWT.NONE, index);
        newItem.setControl(tabControl);
        return newItem;
    }

    private void updateTab(final CTabItem item, final DatasetTable table, final DatasetModel model)
    {
        item.setText(table.getName());
        item.setToolTipText(
                table.getRows().size() + " rows, " + table.getColumns().size() + " columns");
        item.setFont(table.getRows().isEmpty() ? italicFont() : null);
        item.setImage(problemImage(model.getProblems(table.getKey())));
    }

    private Font italicFont()
    {
        final FontDescriptor descriptor = FontDescriptor.createFrom(tabFolder.getFont()).setStyle(SWT.ITALIC);
        return resources.createFont(descriptor);
    }

    private static Image problemImage(final List<DatasetProblem> problems)
    {
        boolean hasWarning = false;
        for (final DatasetProblem problem : problems)
        {
            if (problem.severity() == ProblemSeverity.ERROR)
            {
                return sharedImage(ISharedImages.IMG_OBJS_ERROR_TSK);
            }
            if (problem.severity() == ProblemSeverity.WARNING)
            {
                hasWarning = true;
            }
        }
        return hasWarning ? sharedImage(ISharedImages.IMG_OBJS_WARN_TSK) : null;
    }

    private void updateBanner(final DatasetModel model)
    {
        if (!editor.getSourceEditor().isEditorInputModifiable())
        {
            errorBanner.showReadOnly();
            return;
        }
        if (datasetDocument.isBlank())
        {
            errorBanner.hide();
            return;
        }
        for (final DatasetProblem problem : model.getProblems())
        {
            if (problem.code().blocksEditing())
            {
                errorBanner.showBlocking(problem);
                return;
            }
        }
        errorBanner.hide();
    }

    private void createEmptyDataset()
    {
        if (editor.getSourceEditor().validateEditorInputState())
        {
            datasetDocument.createEmptyDataset();
        }
    }

    private static Image sharedImage(final String key)
    {
        return PlatformUI.getWorkbench().getSharedImages().getImage(key);
    }
}
