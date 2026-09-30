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
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
import org.dbunit.eclipse.dataset.ui.actions.ShowInSourceAction;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.text.IDocument;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.services.IServiceLocator;

/**
 * The actions of the Tables page: the grid actions and the edit actions that the workbench retargets, with
 * their toolbar, tab menu, and context menu items, their enablement for the current selection, undo and redo
 * of the document's shared history, and the activation of the page's key binding context and command
 * handlers while the page is active. All of it belongs to the page's controls, so it may be used on the UI
 * thread only.
 */
final class TablesPageActions
{
    private static final String TABLES_PAGE_CONTEXT_ID = "org.dbunit.eclipse.dataset.ui.tablesPageContext";

    private final Control control;

    private final Supplier<IServiceLocator> serviceLocator;

    private final Supplier<IDocument> sourceDocument;

    private final Supplier<GridSelection> currentSelection;

    private final DocumentUndoAction undoAction;

    private final DocumentUndoAction redoAction;

    private final IOperationHistoryListener operationHistoryListener;

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

    private final ShowInSourceAction showInSourceAction;

    private final CutAction cutAction;

    private final CopyAction copyAction;

    private final PasteAction pasteAction;

    private final DeleteAction deleteAction;

    private final SelectAllAction selectAllAction;

    private final Map<String, IAction> globalActionHandlers;

    private final List<GridAction> gridActions = new ArrayList<>();

    private final List<GridAction> retargetableActions = new ArrayList<>();

    private final List<IHandlerActivation> handlerActivations = new ArrayList<>();

    private IContextActivation contextActivation;

    /**
     * Creates the actions of a page and connects them to its tab folder.
     *
     * @param context The context that the actions work with.
     * @param tabFolder The folder that gets the toolbar, the tab menu, and the selection listener.
     * @param control The page's control, whose display runs the update of undo and redo that a change on
     *                another thread asks for.
     * @param serviceLocator Returns the locator of the context service and the handler service of the
     *                       editor.
     * @param sourceDocument Returns the document whose shared undo history undo and redo act on, which
     *                       changes when the editor's input does.
     * @param hasActiveCellEditor Returns true while a grid cell editor is open, so undo and redo decline to
     *                            run.
     * @param currentSelection Returns the selection of the grid of the selected tab.
     */
    TablesPageActions(final DatasetGridContext context, final CTabFolder tabFolder, final Control control,
            final Supplier<IServiceLocator> serviceLocator, final Supplier<IDocument> sourceDocument,
            final BooleanSupplier hasActiveCellEditor, final Supplier<GridSelection> currentSelection)
    {
        this.control = control;
        this.serviceLocator = serviceLocator;
        this.sourceDocument = sourceDocument;
        this.currentSelection = currentSelection;

        tabFolder.addSelectionListener(
                SelectionListener.widgetSelectedAdapter(event -> updateGridActionsEnablement()));

        insertRowAboveAction = new InsertRowAboveAction(context);
        insertRowBelowAction = new InsertRowBelowAction(context);
        deleteRowsAction = new DeleteRowsAction(context);
        duplicateRowsAction = new DuplicateRowsAction(context);
        moveRowsUpAction = new MoveRowsUpAction(context);
        moveRowsDownAction = new MoveRowsDownAction(context);
        addColumnAction = new AddColumnAction(context);
        renameColumnAction = new RenameColumnAction(context);
        deleteColumnAction = new DeleteColumnAction(context);
        addTableAction = new AddTableAction(context);
        renameTableAction = new RenameTableAction(context);
        deleteTableAction = new DeleteTableAction(context);
        setNullAction = new SetNullAction(context);
        setEmptyStringAction = new SetEmptyStringAction(context);
        fillDownAction = new FillDownAction(context);
        editCellInDialogAction = new EditCellInDialogAction(context);
        showInSourceAction = new ShowInSourceAction(context);
        cutAction = new CutAction(context);
        copyAction = new CopyAction(context);
        pasteAction = new PasteAction(context);
        deleteAction = new DeleteAction(context);
        selectAllAction = new SelectAllAction(context);
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
        gridActions.add(showInSourceAction);
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

        undoAction = new DocumentUndoAction(sourceDocument, false, hasActiveCellEditor);
        redoAction = new DocumentUndoAction(sourceDocument, true, hasActiveCellEditor);
        globalActionHandlers = Map.of(ActionFactory.UNDO.getId(), undoAction, ActionFactory.REDO.getId(),
                redoAction, ActionFactory.CUT.getId(), cutAction, ActionFactory.COPY.getId(), copyAction,
                ActionFactory.PASTE.getId(), pasteAction, ActionFactory.DELETE.getId(), deleteAction,
                ActionFactory.SELECT_ALL.getId(), selectAllAction);
        operationHistoryListener = this::handleOperationHistoryEvent;
        OperationHistoryFactory.getOperationHistory().addOperationHistoryListener(operationHistoryListener);
    }

    void activate()
    {
        final IContextService contextService = serviceLocator.get().getService(IContextService.class);
        contextActivation = contextService.activateContext(TABLES_PAGE_CONTEXT_ID);
        final IHandlerService handlerService = serviceLocator.get().getService(IHandlerService.class);
        for (final GridAction action : gridActions)
        {
            handlerActivations.add(handlerService.activateHandler(action.getActionDefinitionId(),
                    new ActionHandler(action)));
        }
        updateUndoRedoActions();
        updateGridActionsEnablement();
    }

    void deactivate()
    {
        final IContextService contextService = serviceLocator.get().getService(IContextService.class);
        contextService.deactivateContext(contextActivation);
        contextActivation = null;
        final IHandlerService handlerService = serviceLocator.get().getService(IHandlerService.class);
        handlerService.deactivateHandlers(handlerActivations);
        handlerActivations.clear();
    }

    void dispose()
    {
        OperationHistoryFactory.getOperationHistory().removeOperationHistoryListener(operationHistoryListener);
    }

    void fillContextMenu(final IMenuManager menu, final String region)
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
            menu.add(showInSourceAction);
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

    void updateUndoRedoActions()
    {
        undoAction.update();
        redoAction.update();
    }

    private void handleOperationHistoryEvent(final OperationHistoryEvent event)
    {
        if (!isUndoRedoEnablementEvent(event.getEventType()))
        {
            return;
        }
        final IDocumentUndoManager manager =
                DocumentUndoManagerRegistry.getDocumentUndoManager(sourceDocument.get());
        if (manager == null || !event.getOperation().hasContext(manager.getUndoContext()))
        {
            return;
        }
        if (Display.getCurrent() == null)
        {
            control.getDisplay().asyncExec(() ->
            {
                if (!control.isDisposed())
                {
                    updateUndoRedoActions();
                }
            });
        }
        else
        {
            updateUndoRedoActions();
        }
    }

    private static boolean isUndoRedoEnablementEvent(final int eventType)
    {
        return eventType == OperationHistoryEvent.DONE || eventType == OperationHistoryEvent.UNDONE
                || eventType == OperationHistoryEvent.REDONE
                || eventType == OperationHistoryEvent.OPERATION_ADDED
                || eventType == OperationHistoryEvent.OPERATION_REMOVED
                || eventType == OperationHistoryEvent.OPERATION_CHANGED;
    }

    void updateGridActionsEnablement()
    {
        final GridSelection selection = currentSelection.get();
        for (final GridAction action : gridActions)
        {
            action.update(selection);
        }
        for (final GridAction action : retargetableActions)
        {
            action.update(selection);
        }
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
        return globalActionHandlers.get(actionDefinitionId);
    }

    void runAddTableAction()
    {
        addTableAction.run();
    }
}
