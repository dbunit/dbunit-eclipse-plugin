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

import java.util.List;

import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.ui.DatasetUiPlugin;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.dbunit.eclipse.dataset.ui.preferences.PreferenceKeys;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * The Tables page of {@link FlatXmlDatasetEditor}: one sheet tab per table, an {@link ErrorBanner}, and a
 * blank-document state.
 *
 * @since 1.0.0
 */
final class TablesPage implements DatasetGridContext
{
    private final FlatXmlDatasetEditor editor;

    private final FlatXmlDatasetDocument datasetDocument;

    private final LocalResourceManager resources;

    private final Composite control;

    private final PageServices services;

    private final ErrorBanner errorBanner;

    private final Composite contentStack;

    private final StackLayout contentStackLayout;

    private final ProblemsSection problemsSection;

    private final PageSelectionSync pageSelectionSync;

    private final Composite blankComposite;

    private final CTabFolder tabFolder;

    private final TableTabs tabs;

    private final ActiveGrid activeGrid;

    private final TablesPageActions actions;

    private final Button createEmptyDatasetButton;

    private final Composite noTablesComposite;

    private final Button addTableButton;

    private final IDocumentListener sourceDocumentListener = new IDocumentListener()
    {
        @Override
        public void documentAboutToBeChanged(final DocumentEvent event)
        {
            // Nothing to do before a change: documentChanged marks the page for a refresh afterwards.
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

    private IDocument listenedDocument;

    private boolean editable;

    TablesPage(final Composite parent, final FlatXmlDatasetEditor editor,
            final FlatXmlDatasetDocument datasetDocument)
    {
        this.editor = editor;
        this.datasetDocument = datasetDocument;
        resources = new LocalResourceManager(JFaceResources.getResources(), parent);
        pageSelectionSync = new PageSelectionSync(this::sourceSelectionRegion,
                editor.getSourceEditor()::selectAndReveal, datasetDocument);

        control = new Composite(parent, SWT.NONE);
        final GridLayout controlLayout = new GridLayout(1, false);
        controlLayout.marginWidth = 0;
        controlLayout.marginHeight = 0;
        control.setLayout(controlLayout);

        services = new PageServices(control, () -> editable,
                () -> editor.getSourceEditor().validateEditorInputState(),
                () -> editor.getEditorSite().getActionBars().getStatusLineManager());
        errorBanner = new ErrorBanner(control, editor);

        contentStack = new Composite(control, SWT.NONE);
        contentStack.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        contentStackLayout = new StackLayout();
        contentStack.setLayout(contentStackLayout);

        problemsSection = new ProblemsSection(control, this::selectProblem);

        blankComposite = new Composite(contentStack, SWT.NONE);
        blankComposite.setLayout(new GridLayout(1, false));
        final Label blankLabel = new Label(blankComposite, SWT.CENTER);
        blankLabel.setText(Messages.TablesPage_emptyFile);
        blankLabel.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, true, true));
        createEmptyDatasetButton = new Button(blankComposite, SWT.PUSH);
        createEmptyDatasetButton.setText(Messages.TablesPage_createEmptyDataset);
        createEmptyDatasetButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, true));
        createEmptyDatasetButton.addSelectionListener(
                SelectionListener.widgetSelectedAdapter(event -> createEmptyDataset()));

        noTablesComposite = new Composite(contentStack, SWT.NONE);
        noTablesComposite.setLayout(new GridLayout(1, false));
        final Label noTablesLabel = new Label(noTablesComposite, SWT.CENTER);
        noTablesLabel.setText(Messages.TablesPage_noTables);
        noTablesLabel.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, true, true));
        addTableButton = new Button(noTablesComposite, SWT.PUSH);
        addTableButton.setText(Messages.Action_addTable);
        addTableButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, true));
        addTableButton.addSelectionListener(
                SelectionListener.widgetSelectedAdapter(event -> runAddTableAction()));

        tabFolder = new CTabFolder(contentStack, SWT.TOP | SWT.BORDER | SWT.FLAT);
        actions = new TablesPageActions(this, tabFolder, control, editor::getEditorSite, this::sourceDocument,
                this::hasActiveCellEditor, this::getSelection);
        tabs = new TableTabs(tabFolder, this, resources, actions::updateGridActionsEnablement);
        activeGrid = new ActiveGrid(tabFolder, tabs::activeGrid);

        final IDocument document = sourceDocument();
        listenedDocument = document;
        document.addDocumentListener(sourceDocumentListener);
        datasetDocument.addModelListener(event -> reconcile());

        reconcile();
    }

    Control getControl()
    {
        return control;
    }

    /**
     * Commits the value of an open cell editor, so that it is in the document before the document is saved
     * or the Source page shows it.
     */
    void commitActiveCellEditor()
    {
        tabs.commitActiveCellEditor();
    }

    void activate()
    {
        active = true;
        actions.activate();
        if (refreshPending)
        {
            refreshPending = false;
            datasetDocument.refresh();
        }
        pageSelectionSync.onActivate().ifPresent(tabs::selectCell);
    }

    void deactivate()
    {
        pageSelectionSync.onDeactivate(activeGrid.currentCellAddress());
        active = false;
        actions.deactivate();
    }

    /**
     * Follows the editor to the document of its new input, after Save As or a move of its file.
     */
    void inputChanged()
    {
        listenedDocument.removeDocumentListener(sourceDocumentListener);
        listenedDocument = sourceDocument();
        listenedDocument.addDocumentListener(sourceDocumentListener);
        actions.updateUndoRedoActions();
    }

    /**
     * Stops listening to the document, which can outlive the editor when another editor shares it.
     */
    void dispose()
    {
        listenedDocument.removeDocumentListener(sourceDocumentListener);
        actions.dispose();
    }

    /**
     * Returns this page's action for a global action id, for {@link DatasetEditorContributor} to install
     * while the Tables page is active.
     *
     * @param actionDefinitionId One of {@link ActionFactory}'s global action ids.
     * @return The action, or null when this page has none for that id.
     */
    IAction getGlobalActionHandler(final String actionDefinitionId)
    {
        return actions.getGlobalActionHandler(actionDefinitionId);
    }

    /**
     * Repaints every grid, for example after the NULL display text changed.
     */
    void repaintGrids()
    {
        tabs.repaintGrids();
    }

    @Override
    public void expectRename(final String oldKey, final String newKey)
    {
        tabs.expectRename(oldKey, newKey);
    }

    @Override
    public void expectNewTableSelected(final String tableKey)
    {
        tabs.expectNewTableSelected(tableKey);
    }

    @Override
    public void cancelExpectedRename()
    {
        tabs.cancelExpectedRename();
    }

    @Override
    public void cancelExpectedNewTableSelected()
    {
        tabs.cancelExpectedNewTableSelected();
    }

    @Override
    public boolean isEditable()
    {
        return editable;
    }

    @Override
    public DatasetDocument getDatasetDocument()
    {
        return datasetDocument;
    }

    @Override
    public String getNullDisplayText()
    {
        return DatasetUiPlugin.getDefault().getPreferenceStore().getString(PreferenceKeys.NULL_DISPLAY_TEXT);
    }

    @Override
    public boolean isDarkTheme()
    {
        return services.isDarkTheme();
    }

    @Override
    public boolean executeEdit(final Runnable edit)
    {
        return services.executeEdit(edit);
    }

    @Override
    public boolean executeMultiCellEdit(final String title, final Runnable edit)
    {
        return services.executeMultiCellEdit(title, edit);
    }

    @Override
    public void fillContextMenu(final IMenuManager menu, final String region)
    {
        actions.fillContextMenu(menu, region);
    }

    @Override
    public boolean hasActiveCellEditor()
    {
        return activeGrid.hasActiveCellEditor();
    }

    @Override
    public GridSelection getSelection()
    {
        return activeGrid.getSelection();
    }

    @Override
    public void selectRegion(final int firstColumnIndex, final int firstRowIndex, final int columnCount,
            final int rowCount)
    {
        activeGrid.selectRegion(firstColumnIndex, firstRowIndex, columnCount, rowCount);
    }

    @Override
    public Shell getShell()
    {
        return services.getShell();
    }

    @Override
    public Text getActiveCellEditorText()
    {
        return activeGrid.getActiveCellEditorText();
    }

    @Override
    public List<Point> getSelectedCellPositions()
    {
        return activeGrid.getSelectedCellPositions();
    }

    @Override
    public void selectAll()
    {
        activeGrid.selectAll();
    }

    @Override
    public void editCellInDialog()
    {
        activeGrid.editCellInDialog();
    }

    @Override
    public void showInSource()
    {
        final CellAddress address = activeGrid.currentCellAddress();
        if (address == null)
        {
            return;
        }
        datasetDocument.locate(address)
                .ifPresent(region -> editor.showOnSourcePage(region.getOffset(), region.getLength()));
    }

    @Override
    public void setStatusMessage(final String message)
    {
        services.setStatusMessage(message);
    }

    @Override
    public void setStatusErrorMessage(final String message)
    {
        services.setStatusErrorMessage(message);
    }

    @Override
    public void writeClipboardText(final String text)
    {
        services.writeClipboardText(text);
    }

    @Override
    public String readClipboardText()
    {
        return services.readClipboardText();
    }

    CTabFolder getTabFolder()
    {
        return tabFolder;
    }

    ErrorBanner getErrorBanner()
    {
        return errorBanner;
    }

    ProblemsSection getProblemsSection()
    {
        return problemsSection;
    }

    boolean isShowingBlankState()
    {
        return blankComposite.equals(contentStackLayout.topControl);
    }

    Button getCreateEmptyDatasetButton()
    {
        return createEmptyDatasetButton;
    }

    boolean isShowingNoTablesState()
    {
        return noTablesComposite.equals(contentStackLayout.topControl);
    }

    Button getAddTableButton()
    {
        return addTableButton;
    }

    private IDocument sourceDocument()
    {
        final ITextEditor sourceEditor = editor.getSourceEditor();
        return sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput());
    }

    void selectProblem(final DatasetProblem problem)
    {
        if (problem.tableKey() == null)
        {
            editor.showOnSourcePage(problem.offset(), problem.length());
            return;
        }
        final int columnIndex = problem.columnName() == null ? -1
                : datasetDocument.getModel().findTable(problem.tableKey())
                        .map(table -> table.getColumnIndex(problem.columnName())).orElse(-1);
        tabs.selectCell(new CellAddress(problem.tableKey(), Math.max(problem.rowIndex(), 0), columnIndex));
    }

    private IRegion sourceSelectionRegion()
    {
        final ITextSelection selection =
                (ITextSelection) editor.getSourceEditor().getSelectionProvider().getSelection();
        return new Region(selection.getOffset(), selection.getLength());
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
            tabs.reconcile(DatasetModel.EMPTY);
            createEmptyDatasetButton.setEnabled(inputModifiable);
            contentStackLayout.topControl = blankComposite;
        }
        else if (model.getTables().isEmpty())
        {
            tabs.reconcile(DatasetModel.EMPTY);
            addTableButton.setEnabled(editable);
            contentStackLayout.topControl = noTablesComposite;
        }
        else
        {
            tabs.reconcile(model);
            contentStackLayout.topControl = tabFolder;
        }
        contentStack.layout();

        updateBanner(model);
        // A blank document's only problem is its missing root element, which the blank state explains.
        final DatasetModel listedModel = datasetDocument.isBlank() ? DatasetModel.EMPTY : model;
        problemsSection.update(listedModel);
        actions.updateGridActionsEnablement();
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

    private void runAddTableAction()
    {
        actions.runAddTableAction();
    }

}
