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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.dbunit.eclipse.dataset.ui.DatasetUiPlugin;
import org.dbunit.eclipse.dataset.ui.actions.AddColumnAction;
import org.dbunit.eclipse.dataset.ui.actions.AddTableAction;
import org.dbunit.eclipse.dataset.ui.actions.CopyAction;
import org.dbunit.eclipse.dataset.ui.actions.CutAction;
import org.dbunit.eclipse.dataset.ui.actions.DeleteAction;
import org.dbunit.eclipse.dataset.ui.actions.DeleteColumnAction;
import org.dbunit.eclipse.dataset.ui.actions.DeleteRowsAction;
import org.dbunit.eclipse.dataset.ui.actions.DeleteTableAction;
import org.dbunit.eclipse.dataset.ui.actions.DuplicateRowsAction;
import org.dbunit.eclipse.dataset.ui.actions.EditCellInDialogAction;
import org.dbunit.eclipse.dataset.ui.actions.FillDownAction;
import org.dbunit.eclipse.dataset.ui.actions.GridAction;
import org.dbunit.eclipse.dataset.ui.actions.InsertRowAboveAction;
import org.dbunit.eclipse.dataset.ui.actions.InsertRowBelowAction;
import org.dbunit.eclipse.dataset.ui.actions.MoveRowsDownAction;
import org.dbunit.eclipse.dataset.ui.actions.MoveRowsUpAction;
import org.dbunit.eclipse.dataset.ui.actions.PasteAction;
import org.dbunit.eclipse.dataset.ui.actions.RenameColumnAction;
import org.dbunit.eclipse.dataset.ui.actions.RenameTableAction;
import org.dbunit.eclipse.dataset.ui.actions.SelectAllAction;
import org.dbunit.eclipse.dataset.ui.actions.SetEmptyStringAction;
import org.dbunit.eclipse.dataset.ui.actions.SetNullAction;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGrid;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.dbunit.eclipse.dataset.ui.preferences.PreferenceKeys;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.edit.editor.ICellEditor;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * The Tables page of {@link FlatXmlDatasetEditor}: one sheet tab per table, an {@link ErrorBanner}, and a
 * blank-document state.
 *
 * @since 1.0.0
 */
final class TablesPage implements DatasetGridContext
{
    private static final String TABLES_PAGE_CONTEXT_ID = "org.dbunit.eclipse.dataset.ui.tablesPageContext";

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

    private final Composite noTablesComposite;

    private final Button addTableButton;

    private final Map<String, CTabItem> tabsByKey = new LinkedHashMap<>();

    private final Map<String, DatasetGrid> gridsByKey = new LinkedHashMap<>();

    private final IDocumentListener sourceDocumentListener = new IDocumentListener()
    {
        @Override
        public void documentAboutToBeChanged(final DocumentEvent event)
        {
        }

        @Override
        public void documentChanged(final DocumentEvent event)
        {
            updateUndoRedoActions();
            refreshPending = true;
            if (active)
            {
                scheduleRefresh();
            }
        }
    };

    private final DocumentUndoAction undoAction;

    private final DocumentUndoAction redoAction;

    private final InsertRowAboveAction insertRowAboveAction;

    private final InsertRowBelowAction insertRowBelowAction;

    private final DeleteRowsAction deleteRowsAction;

    private final DuplicateRowsAction duplicateRowsAction;

    private final MoveRowsUpAction moveRowsUpAction;

    private final MoveRowsDownAction moveRowsDownAction;

    private final AddColumnAction addColumnAction;

    private final RenameColumnAction renameColumnAction;

    private final DeleteColumnAction deleteColumnAction;

    private final AddTableAction addTableAction;

    private final RenameTableAction renameTableAction;

    private final DeleteTableAction deleteTableAction;

    private final SetNullAction setNullAction;

    private final SetEmptyStringAction setEmptyStringAction;

    private final FillDownAction fillDownAction;

    private final EditCellInDialogAction editCellInDialogAction;

    private final CutAction cutAction;

    private final CopyAction copyAction;

    private final PasteAction pasteAction;

    private final DeleteAction deleteAction;

    private final SelectAllAction selectAllAction;

    private final List<GridAction> gridActions = new ArrayList<>();

    private final List<GridAction> retargetableActions = new ArrayList<>();

    private final List<IHandlerActivation> handlerActivations = new ArrayList<>();

    private IContextActivation contextActivation;

    private boolean active;

    private boolean refreshPending;

    private boolean refreshScheduled;

    private IDocument listenedDocument;

    private boolean editable;

    private String expectedRenameOldKey;

    private String expectedRenameNewKey;

    private String expectedNewTableKey;

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

        noTablesComposite = new Composite(contentStack, SWT.NONE);
        noTablesComposite.setLayout(new GridLayout(1, false));
        final Label noTablesLabel = new Label(noTablesComposite, SWT.CENTER);
        noTablesLabel.setText("The dataset has no tables.");
        noTablesLabel.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, true, true));
        addTableButton = new Button(noTablesComposite, SWT.PUSH);
        addTableButton.setText("Add Table...");
        addTableButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, true));
        addTableButton.addSelectionListener(
                SelectionListener.widgetSelectedAdapter(event -> runAddTableAction()));

        tabFolder = new CTabFolder(contentStack, SWT.TOP | SWT.BORDER | SWT.FLAT);
        tabFolder.addSelectionListener(
                SelectionListener.widgetSelectedAdapter(event -> updateGridActionsEnablement()));

        insertRowAboveAction = new InsertRowAboveAction(this);
        insertRowBelowAction = new InsertRowBelowAction(this);
        deleteRowsAction = new DeleteRowsAction(this);
        duplicateRowsAction = new DuplicateRowsAction(this);
        moveRowsUpAction = new MoveRowsUpAction(this);
        moveRowsDownAction = new MoveRowsDownAction(this);
        addColumnAction = new AddColumnAction(this);
        renameColumnAction = new RenameColumnAction(this);
        deleteColumnAction = new DeleteColumnAction(this);
        addTableAction = new AddTableAction(this);
        renameTableAction = new RenameTableAction(this);
        deleteTableAction = new DeleteTableAction(this);
        setNullAction = new SetNullAction(this);
        setEmptyStringAction = new SetEmptyStringAction(this);
        fillDownAction = new FillDownAction(this);
        editCellInDialogAction = new EditCellInDialogAction(this);
        cutAction = new CutAction(this);
        copyAction = new CopyAction(this);
        pasteAction = new PasteAction(this);
        deleteAction = new DeleteAction(this);
        selectAllAction = new SelectAllAction(this);
        gridActions.add(insertRowAboveAction);
        gridActions.add(insertRowBelowAction);
        gridActions.add(deleteRowsAction);
        gridActions.add(duplicateRowsAction);
        gridActions.add(moveRowsUpAction);
        gridActions.add(moveRowsDownAction);
        gridActions.add(addColumnAction);
        gridActions.add(renameColumnAction);
        gridActions.add(deleteColumnAction);
        gridActions.add(addTableAction);
        gridActions.add(renameTableAction);
        gridActions.add(deleteTableAction);
        gridActions.add(setNullAction);
        gridActions.add(setEmptyStringAction);
        gridActions.add(fillDownAction);
        gridActions.add(editCellInDialogAction);
        retargetableActions.add(cutAction);
        retargetableActions.add(copyAction);
        retargetableActions.add(pasteAction);
        retargetableActions.add(deleteAction);
        retargetableActions.add(selectAllAction);

        final ToolBarManager toolBarManager = new ToolBarManager(SWT.FLAT);
        toolBarManager.add(insertRowBelowAction);
        toolBarManager.add(deleteRowsAction);
        toolBarManager.add(addColumnAction);
        toolBarManager.add(deleteColumnAction);
        toolBarManager.add(addTableAction);
        final ToolBar toolBar = toolBarManager.createControl(tabFolder);
        tabFolder.setTopRight(toolBar);

        tabFolder.addMenuDetectListener(event ->
        {
            final Point point = tabFolder.toControl(event.x, event.y);
            final CTabItem item = tabFolder.getItem(point);
            if (item != null)
            {
                tabFolder.setSelection(item);
                updateGridActionsEnablement();
            }
        });
        final MenuManager tabMenuManager = new MenuManager();
        tabMenuManager.setRemoveAllWhenShown(true);
        tabMenuManager.addMenuListener(manager ->
        {
            manager.add(addTableAction);
            manager.add(renameTableAction);
            manager.add(deleteTableAction);
        });
        tabFolder.setMenu(tabMenuManager.createContextMenu(tabFolder));

        final IDocument document = sourceDocument();
        listenedDocument = document;
        document.addDocumentListener(sourceDocumentListener);
        datasetDocument.addModelListener(event -> reconcile());
        undoAction = new DocumentUndoAction(this::sourceDocument, false, this::hasActiveCellEditor);
        redoAction = new DocumentUndoAction(this::sourceDocument, true, this::hasActiveCellEditor);

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
        for (final DatasetGrid grid : gridsByKey.values())
        {
            grid.commitActiveCellEditor();
        }
    }

    void activate()
    {
        active = true;
        final IContextService contextService = editor.getEditorSite().getService(IContextService.class);
        contextActivation = contextService.activateContext(TABLES_PAGE_CONTEXT_ID);
        final IHandlerService handlerService = editor.getEditorSite().getService(IHandlerService.class);
        for (final GridAction action : gridActions)
        {
            handlerActivations.add(handlerService.activateHandler(action.getActionDefinitionId(),
                    new ActionHandler(action)));
        }
        updateUndoRedoActions();
        updateGridActionsEnablement();
        if (refreshPending)
        {
            refreshPending = false;
            datasetDocument.refresh();
        }
    }

    void deactivate()
    {
        active = false;
        final IContextService contextService = editor.getEditorSite().getService(IContextService.class);
        contextService.deactivateContext(contextActivation);
        contextActivation = null;
        final IHandlerService handlerService = editor.getEditorSite().getService(IHandlerService.class);
        handlerService.deactivateHandlers(handlerActivations);
        handlerActivations.clear();
    }

    /**
     * Follows the editor to the document of its new input, after Save As or a move of its file.
     */
    void inputChanged()
    {
        listenedDocument.removeDocumentListener(sourceDocumentListener);
        listenedDocument = sourceDocument();
        listenedDocument.addDocumentListener(sourceDocumentListener);
        updateUndoRedoActions();
    }

    /**
     * Stops listening to the document, which can outlive the editor when another editor shares it.
     */
    void dispose()
    {
        listenedDocument.removeDocumentListener(sourceDocumentListener);
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
        if (ActionFactory.UNDO.getId().equals(actionDefinitionId))
        {
            return undoAction;
        }
        if (ActionFactory.REDO.getId().equals(actionDefinitionId))
        {
            return redoAction;
        }
        if (ActionFactory.CUT.getId().equals(actionDefinitionId))
        {
            return cutAction;
        }
        if (ActionFactory.COPY.getId().equals(actionDefinitionId))
        {
            return copyAction;
        }
        if (ActionFactory.PASTE.getId().equals(actionDefinitionId))
        {
            return pasteAction;
        }
        if (ActionFactory.DELETE.getId().equals(actionDefinitionId))
        {
            return deleteAction;
        }
        if (ActionFactory.SELECT_ALL.getId().equals(actionDefinitionId))
        {
            return selectAllAction;
        }
        return null;
    }

    @Override
    public void expectRename(final String oldKey, final String newKey)
    {
        expectedRenameOldKey = oldKey;
        expectedRenameNewKey = newKey;
    }

    @Override
    public void expectNewTableSelected(final String tableKey)
    {
        expectedNewTableKey = tableKey;
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
        return relativeLuminance(control.getBackground()) < 0.5;
    }

    @Override
    public boolean executeEdit(final Runnable edit)
    {
        if (!editable || !editor.getSourceEditor().validateEditorInputState())
        {
            return false;
        }
        try
        {
            edit.run();
        }
        catch (final DatasetEditException e)
        {
            editor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(e.getMessage());
            Display.getCurrent().beep();
            return false;
        }
        editor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(null);
        return true;
    }

    @Override
    public boolean executeMultiCellEdit(final String title, final Runnable edit)
    {
        if (!editable || !editor.getSourceEditor().validateEditorInputState())
        {
            return false;
        }
        try
        {
            edit.run();
        }
        catch (final DatasetEditException e)
        {
            MessageDialog.openError(control.getShell(), title, e.getMessage());
            return false;
        }
        editor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(null);
        return true;
    }

    @Override
    public void fillContextMenu(final IMenuManager menu, final String region)
    {
        if (GridRegion.BODY.equals(region))
        {
            menu.add(cutAction);
            menu.add(copyAction);
            menu.add(pasteAction);
            menu.add(insertRowAboveAction);
            menu.add(insertRowBelowAction);
            menu.add(duplicateRowsAction);
            menu.add(deleteRowsAction);
            menu.add(moveRowsUpAction);
            menu.add(moveRowsDownAction);
            menu.add(setNullAction);
            menu.add(setEmptyStringAction);
            menu.add(fillDownAction);
            menu.add(editCellInDialogAction);
        }
        else if (GridRegion.ROW_HEADER.equals(region))
        {
            menu.add(insertRowAboveAction);
            menu.add(insertRowBelowAction);
            menu.add(duplicateRowsAction);
            menu.add(deleteRowsAction);
            menu.add(moveRowsUpAction);
            menu.add(moveRowsDownAction);
        }
        else if (GridRegion.COLUMN_HEADER.equals(region))
        {
            menu.add(addColumnAction);
            menu.add(renameColumnAction);
            menu.add(deleteColumnAction);
        }
    }

    @Override
    public boolean hasActiveCellEditor()
    {
        final CTabItem selected = tabFolder.getSelection();
        return selected != null && selected.getControl() instanceof NatTable
                && ((NatTable) selected.getControl()).getActiveCellEditor() != null;
    }

    @Override
    public GridSelection getSelection()
    {
        final DatasetGrid grid = activeGrid();
        return grid != null ? grid.getSelection() : GridSelection.NONE;
    }

    @Override
    public void selectRegion(final int firstColumnIndex, final int firstRowIndex, final int columnCount,
            final int rowCount)
    {
        final DatasetGrid grid = activeGrid();
        if (grid != null)
        {
            grid.selectRegion(firstColumnIndex, firstRowIndex, columnCount, rowCount);
        }
    }

    @Override
    public Shell getShell()
    {
        return control.getShell();
    }

    @Override
    public Text getActiveCellEditorText()
    {
        final CTabItem selected = tabFolder.getSelection();
        if (selected == null || !(selected.getControl() instanceof NatTable))
        {
            return null;
        }
        final ICellEditor cellEditor = ((NatTable) selected.getControl()).getActiveCellEditor();
        if (cellEditor == null)
        {
            return null;
        }
        final Control editorControl = cellEditor.getEditorControl();
        return editorControl instanceof Text ? (Text) editorControl : null;
    }

    @Override
    public List<Point> getSelectedCellPositions()
    {
        final DatasetGrid grid = activeGrid();
        return grid != null ? grid.getSelectedCellPositions() : List.of();
    }

    @Override
    public void selectAll()
    {
        final DatasetGrid grid = activeGrid();
        if (grid != null)
        {
            grid.selectAll();
        }
    }

    @Override
    public void editCellInDialog()
    {
        final DatasetGrid grid = activeGrid();
        if (grid != null)
        {
            grid.editCellInDialog();
        }
    }

    @Override
    public void setStatusMessage(final String message)
    {
        editor.getEditorSite().getActionBars().getStatusLineManager().setMessage(message);
    }

    @Override
    public void writeClipboardText(final String text)
    {
        final Clipboard clipboard = new Clipboard(control.getDisplay());
        try
        {
            clipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
        }
        finally
        {
            clipboard.dispose();
        }
    }

    @Override
    public String readClipboardText()
    {
        final Clipboard clipboard = new Clipboard(control.getDisplay());
        try
        {
            return (String) clipboard.getContents(TextTransfer.getInstance());
        }
        finally
        {
            clipboard.dispose();
        }
    }

    private static double relativeLuminance(final Color color)
    {
        return 0.2126 * linearize(color.getRed()) + 0.7152 * linearize(color.getGreen())
                + 0.0722 * linearize(color.getBlue());
    }

    private static double linearize(final int channelValue)
    {
        final double normalized = channelValue / 255.0;
        return normalized <= 0.03928 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
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

    boolean isShowingNoTablesState()
    {
        return contentStackLayout.topControl == noTablesComposite;
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

    private void updateUndoRedoActions()
    {
        undoAction.update();
        redoAction.update();
    }

    private void updateGridActionsEnablement()
    {
        final GridSelection selection = getSelection();
        for (final GridAction action : gridActions)
        {
            action.update(selection);
        }
        for (final GridAction action : retargetableActions)
        {
            action.update(selection);
        }
    }

    private DatasetGrid activeGrid()
    {
        final CTabItem selected = tabFolder.getSelection();
        if (selected == null)
        {
            return null;
        }
        for (final DatasetGrid grid : gridsByKey.values())
        {
            if (grid.getControl() == selected.getControl())
            {
                return grid;
            }
        }
        return null;
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
        else if (model.getTables().isEmpty())
        {
            addTableButton.setEnabled(editable);
            contentStackLayout.topControl = noTablesComposite;
        }
        else
        {
            reconcileTabs(model);
            contentStackLayout.topControl = tabFolder;
        }
        contentStack.layout();

        updateBanner(model);
        updateGridActionsEnablement();
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
            DatasetGrid grid = gridsByKey.get(key);
            if (item == null)
            {
                grid = new DatasetGrid(tabFolder, this, key);
                grid.addSelectionListener(this::updateGridActionsEnablement);
                gridsByKey.put(key, grid);
                item = new CTabItem(tabFolder, SWT.NONE, index);
                item.setControl(grid.getControl());
                tabsByKey.put(key, item);
                if (key.equals(expectedNewTableKey))
                {
                    tabFolder.setSelection(item);
                }
            }
            else if (tabFolder.indexOf(item) != index)
            {
                item = moveTab(item, index);
                tabsByKey.put(key, item);
            }
            grid.tableChanged(table);
            updateTab(item, table, model);
            index++;
        }
        expectedRenameOldKey = null;
        expectedRenameNewKey = null;
        expectedNewTableKey = null;

        final Iterator<Map.Entry<String, CTabItem>> iterator = tabsByKey.entrySet().iterator();
        while (iterator.hasNext())
        {
            final Map.Entry<String, CTabItem> entry = iterator.next();
            if (!seenKeys.contains(entry.getKey()))
            {
                entry.getValue().getControl().dispose();
                entry.getValue().dispose();
                iterator.remove();
                gridsByKey.remove(entry.getKey());
            }
        }

        if (tabFolder.getSelection() == null && tabFolder.getItemCount() > 0)
        {
            tabFolder.setSelection(0);
        }
    }

    private String resolveRenamedKey(final String currentKey)
    {
        if (currentKey.equals(expectedRenameNewKey) && tabsByKey.containsKey(expectedRenameOldKey))
        {
            tabsByKey.put(currentKey, tabsByKey.remove(expectedRenameOldKey));
            final DatasetGrid grid = gridsByKey.remove(expectedRenameOldKey);
            grid.tableRenamed(currentKey);
            gridsByKey.put(currentKey, grid);
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
        item.setToolTipText(table.isDeclaredOnly() ? "Declared in the DTD; no rows"
                : table.getRows().size() + " rows, " + table.getColumns().size() + " columns");
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

    private void runAddTableAction()
    {
        addTableAction.run();
    }

    private static Image sharedImage(final String key)
    {
        return PlatformUI.getWorkbench().getSharedImages().getImage(key);
    }
}
