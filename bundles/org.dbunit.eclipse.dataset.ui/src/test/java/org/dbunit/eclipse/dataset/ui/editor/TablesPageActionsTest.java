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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.actions.DatasetCommandIds;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.core.commands.IHandler;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.services.IServiceLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link TablesPageActions}: the global action handlers, the menus and the toolbar, the enablement for
 * a selection, undo and redo of the shared history of a document, and the activation of the context and the
 * command handlers of the page, which is checked against a recording locator of the two services.
 */
class TablesPageActionsTest
{
    private static final String TABLES_PAGE_CONTEXT_ID = "org.dbunit.eclipse.dataset.ui.tablesPageContext";

    private static final List<String> GRID_COMMAND_IDS = List.of(DatasetCommandIds.INSERT_ROW_ABOVE,
            DatasetCommandIds.INSERT_ROW_BELOW, DatasetCommandIds.DELETE_ROWS,
            DatasetCommandIds.DUPLICATE_ROWS, DatasetCommandIds.MOVE_ROWS_UP,
            DatasetCommandIds.MOVE_ROWS_DOWN, DatasetCommandIds.ADD_COLUMN, DatasetCommandIds.RENAME_COLUMN,
            DatasetCommandIds.DELETE_COLUMN,
            DatasetCommandIds.ADD_TABLE, DatasetCommandIds.RENAME_TABLE, DatasetCommandIds.DELETE_TABLE,
            DatasetCommandIds.SET_NULL, DatasetCommandIds.SET_EMPTY_STRING, DatasetCommandIds.FILL_DOWN,
            DatasetCommandIds.EDIT_CELL_IN_DIALOG, DatasetCommandIds.SHOW_IN_SOURCE);

    private static final GridSelection USERS_CELL_SELECTED =
            new GridSelection("USERS", 2, 2, 0, 0, List.of(0), List.of(0), false);

    private Shell shell;

    private CTabFolder tabFolder;

    private Document document;

    private AtomicReference<IDocument> sourceDocument;

    private AtomicReference<GridSelection> selection;

    private AtomicInteger selectionReads;

    private AtomicBoolean cellEditorOpen;

    private RecordingServices services;

    private TablesPageActions actions;

    @BeforeEach
    void createActions()
    {
        shell = new Shell(Display.getDefault());
        shell.setLayout(new FillLayout());
        shell.setSize(400, 300);
        tabFolder = new CTabFolder(shell, SWT.TOP | SWT.BORDER | SWT.FLAT);
        document = new Document("<dataset/>");
        trackChanges(document);
        sourceDocument = new AtomicReference<>(document);
        selection = new AtomicReference<>(GridSelection.NONE);
        selectionReads = new AtomicInteger();
        cellEditorOpen = new AtomicBoolean();
        services = new RecordingServices();
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(new Document("<dataset/>"),
                DtdSource.NONE, FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        final StubGridContext context = new StubGridContext(datasetDocument);
        actions = new TablesPageActions(context, tabFolder, shell, () -> services, sourceDocument::get,
                cellEditorOpen::get, () ->
                {
                    selectionReads.incrementAndGet();
                    return selection.get();
                });
        shell.open();
        UiTestWorkspace.processEvents();
    }

    @AfterEach
    void disposeActions()
    {
        actions.dispose();
        stopTracking(document);
        shell.dispose();
    }

    @Test
    void testGetGlobalActionHandler_forEachGlobalActionId_returnsTheMatchingAction()
    {
        final List<String> ids = List.of(ActionFactory.UNDO.getId(), ActionFactory.REDO.getId(),
                ActionFactory.CUT.getId(), ActionFactory.COPY.getId(), ActionFactory.PASTE.getId(),
                ActionFactory.DELETE.getId(), ActionFactory.SELECT_ALL.getId());
        final List<String> handlers = new ArrayList<>();

        for (final String id : ids)
        {
            final IAction handler = actions.getGlobalActionHandler(id);
            handlers.add(handler.getClass().getSimpleName() + ": " + handler.getText());
        }

        assertThat(handlers).as("Each global action id must have its action.").containsExactly(
                "DocumentUndoAction: " + Messages.Action_undo, "DocumentUndoAction: " + Messages.Action_redo,
                "CutAction: " + Messages.Action_cut, "CopyAction: " + Messages.Action_copy,
                "PasteAction: " + Messages.Action_paste, "DeleteAction: " + Messages.Action_delete,
                "SelectAllAction: " + Messages.Action_selectAll);
    }

    @Test
    void testGetGlobalActionHandler_forAnIdThatThePageDoesNotHandle_isNull()
    {
        assertThat(actions.getGlobalActionHandler(ActionFactory.SAVE.getId()))
                .as("An action that the page does not offer must not be returned.").isNull();
    }

    @Test
    void testFillContextMenu_forTheBodyOfTheGrid_addsTheCellActions()
    {
        final MenuManager menu = new MenuManager();

        actions.fillContextMenu(menu, GridRegion.BODY);

        assertThat(actionNames(menu.getItems())).as("The body's menu must offer the cell actions in order.")
                .containsExactly("CutAction", "CopyAction", "PasteAction", "InsertRowAboveAction",
                        "InsertRowBelowAction", "DuplicateRowsAction", "DeleteRowsAction", "MoveRowsUpAction",
                        "MoveRowsDownAction", "SetNullAction", "SetEmptyStringAction", "FillDownAction",
                        "EditCellInDialogAction", "ShowInSourceAction");
    }

    @Test
    void testFillContextMenu_forTheRowHeader_addsTheRowActions()
    {
        final MenuManager menu = new MenuManager();

        actions.fillContextMenu(menu, GridRegion.ROW_HEADER);

        assertThat(actionNames(menu.getItems()))
                .as("The row header's menu must offer the row actions in order.")
                .containsExactly("InsertRowAboveAction", "InsertRowBelowAction", "DuplicateRowsAction",
                        "DeleteRowsAction", "MoveRowsUpAction", "MoveRowsDownAction");
    }

    @Test
    void testFillContextMenu_forTheColumnHeader_addsTheColumnActions()
    {
        final MenuManager menu = new MenuManager();

        actions.fillContextMenu(menu, GridRegion.COLUMN_HEADER);

        assertThat(actionNames(menu.getItems()))
                .as("The column header's menu must offer the column actions in order.")
                .containsExactly("AddColumnAction", "RenameColumnAction", "DeleteColumnAction");
    }

    @Test
    void testFillContextMenu_forAnotherRegion_addsNothing()
    {
        final MenuManager menu = new MenuManager();

        actions.fillContextMenu(menu, GridRegion.CORNER);

        assertThat(menu.getItems()).as("The corner has no menu items.").isEmpty();
    }

    @Test
    void testTabFolder_whenTheActionsAreCreated_hasAToolbarWithTheRowColumnAndTableActions()
    {
        final ToolBar toolBar = (ToolBar) tabFolder.getTopRight();
        final List<String> names = new ArrayList<>();
        for (final ToolItem item : toolBar.getItems())
        {
            names.add(actionOf(item.getData()).getClass().getSimpleName());
        }

        assertThat(names).as("The toolbar must offer these actions in order.").containsExactly(
                "InsertRowBelowAction", "DeleteRowsAction", "AddColumnAction", "DeleteColumnAction",
                "AddTableAction");
    }

    @Test
    void testTabFolder_whenTheTabMenuIsShown_offersTheTableActions()
    {
        assertThat(tabMenuActions().keySet()).as("The tab menu must offer the table actions in order.")
                .containsExactly("AddTableAction", "RenameTableAction", "DeleteTableAction");
    }

    @Test
    void testUpdateGridActionsEnablement_whenTheSelectionChanges_enablesTheActionsThatFitTheSelection()
    {
        final Map<String, IAction> tabActions = tabMenuActions();
        final IAction addTable = tabActions.get("AddTableAction");
        final IAction renameTable = tabActions.get("RenameTableAction");
        final IAction selectAll = actions.getGlobalActionHandler(ActionFactory.SELECT_ALL.getId());
        final IAction cut = actions.getGlobalActionHandler(ActionFactory.CUT.getId());

        actions.updateGridActionsEnablement();
        final Map<String, Boolean> withoutSelection = enabledStates(addTable, renameTable, selectAll, cut);
        selection.set(USERS_CELL_SELECTED);
        actions.updateGridActionsEnablement();
        final Map<String, Boolean> withSelection = enabledStates(addTable, renameTable, selectAll, cut);

        assertThat(withoutSelection).as("Only adding a table fits when nothing is selected.")
                .isEqualTo(Map.of("AddTableAction", true, "RenameTableAction", false, "SelectAllAction",
                        false, "CutAction", false));
        assertThat(withSelection).as("Every action fits a selected cell of a table.")
                .isEqualTo(Map.of("AddTableAction", true, "RenameTableAction", true, "SelectAllAction", true,
                        "CutAction", true));
    }

    @Test
    void testTabFolder_whenATabIsSelected_updatesTheEnablement()
    {
        selectionReads.set(0);

        tabFolder.notifyListeners(SWT.Selection, new Event());

        assertThat(selectionReads.get()).as("The enablement must be computed for the new selection.")
                .isPositive();
    }

    @Test
    void testTabFolder_whenTheMenuOpensOverATab_selectsThatTabAndUpdatesTheEnablement()
    {
        final CTabItem first = addTab("USERS");
        final CTabItem second = addTab("ORDERS");
        tabFolder.setSelection(first);
        layOut();
        selectionReads.set(0);

        tabFolder.notifyListeners(SWT.MenuDetect, menuDetectEventAt(second, 0));

        assertThat(tabFolder.getSelection()).as("The tab under the pointer must be selected.")
                .isSameAs(second);
        assertThat(selectionReads.get()).as("The enablement must be computed for the new selection.")
                .isPositive();
    }

    @Test
    void testTabFolder_whenTheMenuOpensOverNoTab_keepsTheSelection()
    {
        final CTabItem first = addTab("USERS");
        final CTabItem second = addTab("ORDERS");
        tabFolder.setSelection(first);
        layOut();
        selectionReads.set(0);

        tabFolder.notifyListeners(SWT.MenuDetect, menuDetectEventAt(second, second.getBounds().width + 100));

        assertThat(tabFolder.getSelection()).as("Without a tab under the pointer the selection stays.")
                .isSameAs(first);
        assertThat(selectionReads.get()).as("Nothing changed, so nothing is computed.").isZero();
    }

    @Test
    void testActivate_whenCalled_activatesTheContextAndTheCommandHandlersInOrder()
    {
        actions.activate();

        final List<String> expected = new ArrayList<>();
        expected.add("activateContext " + TABLES_PAGE_CONTEXT_ID);
        for (final String commandId : GRID_COMMAND_IDS)
        {
            expected.add("activateHandler " + commandId + " ActionHandler");
        }
        assertThat(services.calls).as("The context and the handlers of the grid actions must be activated.")
                .isEqualTo(expected);
    }

    @Test
    void testActivate_whenCalled_updatesTheEnablementForTheCurrentSelection()
    {
        final IAction selectAll = actions.getGlobalActionHandler(ActionFactory.SELECT_ALL.getId());
        selection.set(USERS_CELL_SELECTED);

        actions.activate();

        assertThat(selectAll.isEnabled()).as("The enablement must fit the current selection.").isTrue();
    }

    @Test
    void testActivate_whenCalled_updatesUndoAndRedoFromTheHistory() throws BadLocationException
    {
        final IAction undo = actions.getGlobalActionHandler(ActionFactory.UNDO.getId());
        actions.dispose();
        document.replace(0, 0, "<!-- -->");

        actions.activate();

        assertThat(undo.isEnabled()).as("Undo and redo must show the history when the page activates.")
                .isTrue();
    }

    @Test
    void testDeactivate_afterActivate_deactivatesTheContextAndAllTheHandlers()
    {
        actions.activate();
        services.calls.clear();

        actions.deactivate();

        assertThat(services.calls).as("The context and every handler activation must be deactivated.")
                .containsExactly("deactivateContext 0",
                        "deactivateHandlers " + indexes(0, GRID_COMMAND_IDS.size()));
    }

    @Test
    void testDeactivate_afterActivate_stopsEachHandlerFromListeningToItsAction()
    {
        actions.activate();
        final List<AtomicInteger> reports = listenToTheActivatedHandlers();
        toggleTheEnablementOfTheHandledActions();
        final List<Integer> reportsWhileActive = counts(reports);

        actions.deactivate();
        toggleTheEnablementOfTheHandledActions();

        assertThat(reportsWhileActive).as("A handler that is active reports what its action does.")
                .doesNotContain(0);
        assertThat(counts(reports))
                .as("A handler that was deactivated must not stay attached to its action, or each "
                        + "activation of the page adds a listener that the action tells about every change.")
                .isEqualTo(reportsWhileActive);
    }

    @Test
    void testDispose_whileThePageIsActive_stopsEachHandlerFromListeningToItsAction()
    {
        actions.activate();
        final List<AtomicInteger> reports = listenToTheActivatedHandlers();
        toggleTheEnablementOfTheHandledActions();
        final List<Integer> reportsWhileActive = counts(reports);

        actions.dispose();
        toggleTheEnablementOfTheHandledActions();

        assertThat(counts(reports))
                .as("An editor that closes while its Tables page is active must release its handlers too.")
                .isEqualTo(reportsWhileActive);
    }

    @Test
    void testDeactivate_afterASecondActivate_deactivatesOnlyTheNewActivations()
    {
        actions.activate();
        actions.deactivate();
        actions.activate();
        services.calls.clear();

        actions.deactivate();

        assertThat(services.calls).as("The activations of the first cycle must be forgotten.")
                .containsExactly("deactivateContext 1", "deactivateHandlers "
                        + indexes(GRID_COMMAND_IDS.size(), 2 * GRID_COMMAND_IDS.size()));
    }

    @Test
    void testUpdateGridActionsEnablement_whenACellEditorOpensAndCloses_takesAndGivesBackTheKeyBindings()
    {
        actions.activate();
        services.calls.clear();

        cellEditorOpen.set(true);
        actions.updateGridActionsEnablement();
        actions.updateGridActionsEnablement();
        cellEditorOpen.set(false);
        actions.updateGridActionsEnablement();
        actions.updateGridActionsEnablement();

        assertThat(services.calls)
                .as("The context must be deactivated once while the editor is open, and activated once "
                        + "when it closes.")
                .containsExactly("deactivateContext 0", "activateContext " + TABLES_PAGE_CONTEXT_ID);
    }

    @Test
    void testUpdateGridActionsEnablement_whenThePageIsNotActive_activatesNoContext()
    {
        actions.updateGridActionsEnablement();

        assertThat(services.calls).as("An inactive page must not take the key bindings.").isEmpty();
    }

    @Test
    void testDeactivate_whileACellEditorIsOpen_doesNotDeactivateTheContextAgain()
    {
        actions.activate();
        cellEditorOpen.set(true);
        actions.updateGridActionsEnablement();
        services.calls.clear();

        actions.deactivate();

        assertThat(services.calls).as("Only the handlers are left to deactivate.")
                .containsExactly("deactivateHandlers " + indexes(0, GRID_COMMAND_IDS.size()));
    }

    @Test
    void testActivate_whenTheCellEditorIsStillOpen_leavesTheContextInactiveUntilItCloses()
    {
        cellEditorOpen.set(true);

        actions.activate();

        assertThat(services.calls).as("The handlers are active, and the key bindings wait for the editor.")
                .doesNotContain("activateContext " + TABLES_PAGE_CONTEXT_ID);

        cellEditorOpen.set(false);
        actions.updateGridActionsEnablement();

        assertThat(services.calls).as("Closing the editor must activate the context.")
                .contains("activateContext " + TABLES_PAGE_CONTEXT_ID);
    }

    @Test
    void testUndoAndRedo_whenTheDocumentChanges_followItsHistoryWithoutAnExplicitUpdate()
            throws BadLocationException
    {
        final IAction undo = actions.getGlobalActionHandler(ActionFactory.UNDO.getId());
        final IAction redo = actions.getGlobalActionHandler(ActionFactory.REDO.getId());
        final List<Boolean> initial = List.of(undo.isEnabled(), redo.isEnabled());

        document.replace(0, 0, "<!-- -->");
        final List<Boolean> afterChange = List.of(undo.isEnabled(), redo.isEnabled());
        undo.run();
        final List<Boolean> afterUndo = List.of(undo.isEnabled(), redo.isEnabled());

        assertThat(List.of(initial, afterChange, afterUndo)).as("Undo and redo must follow the history.")
                .containsExactly(List.of(false, false), List.of(true, false), List.of(false, true));
        assertThat(document.get()).as("Undo must restore the text.").isEqualTo("<dataset/>");
    }

    @Test
    void testUndo_whenTheDocumentChangesOnAnotherThread_isEnabledOnceTheDisplayRunsItsUpdate()
            throws InterruptedException
    {
        final IAction undo = actions.getGlobalActionHandler(ActionFactory.UNDO.getId());

        changeOnAnotherThread();
        UiTestWorkspace.processEvents();

        assertThat(undo.isEnabled()).as("The update that another thread asks for must run on the display.")
                .isTrue();
    }

    @Test
    void testUndo_whenTheDocumentChangesOnAnotherThreadAndThePageIsDisposedBeforeTheUpdate_updatesNothing()
            throws InterruptedException
    {
        final IAction undo = actions.getGlobalActionHandler(ActionFactory.UNDO.getId());
        changeOnAnotherThread();

        shell.dispose();
        UiTestWorkspace.processEvents();

        assertThat(undo.isEnabled()).as("A disposed page must not update its actions.").isFalse();
    }

    @Test
    void testUndo_whenAnotherDocumentChangesAndTheCurrentOneHasNoUndoManager_isNotUpdated()
            throws BadLocationException
    {
        final IAction undo = actions.getGlobalActionHandler(ActionFactory.UNDO.getId());
        document.replace(0, 0, "<!-- -->");
        sourceDocument.set(new Document("<dataset/>"));
        final Document other = new Document("<dataset/>");
        trackChanges(other);
        try
        {
            other.replace(0, 0, "<!-- -->");
        }
        finally
        {
            stopTracking(other);
        }

        assertThat(undo.isEnabled()).as("A change of another document must not update undo.").isTrue();
    }

    @Test
    void testUndo_whenAnotherDocumentChangesAndTheCurrentOneHasItsOwnHistory_isNotUpdated()
            throws BadLocationException
    {
        final IAction undo = actions.getGlobalActionHandler(ActionFactory.UNDO.getId());
        document.replace(0, 0, "<!-- -->");
        final Document current = new Document("<dataset/>");
        final Document other = new Document("<dataset/>");
        trackChanges(current);
        trackChanges(other);
        try
        {
            sourceDocument.set(current);
            other.replace(0, 0, "<!-- -->");
        }
        finally
        {
            stopTracking(other);
            stopTracking(current);
        }

        assertThat(undo.isEnabled()).as("An operation of another history must not update undo.").isTrue();
    }

    @Test
    void testUpdateUndoRedoActions_afterTheDocumentChangedUnnoticed_showsTheHistory()
            throws BadLocationException
    {
        final IAction undo = actions.getGlobalActionHandler(ActionFactory.UNDO.getId());
        actions.dispose();
        document.replace(0, 0, "<!-- -->");
        final boolean beforeTheUpdate = undo.isEnabled();

        actions.updateUndoRedoActions();

        assertThat(List.of(beforeTheUpdate, undo.isEnabled()))
                .as("The explicit update must show the history that the actions missed.")
                .containsExactly(false, true);
    }

    @Test
    void testDispose_whenCalled_stopsFollowingTheHistory() throws BadLocationException
    {
        final IAction undo = actions.getGlobalActionHandler(ActionFactory.UNDO.getId());

        actions.dispose();
        document.replace(0, 0, "<!-- -->");

        assertThat(undo.isEnabled()).as("After dispose no history event may reach the actions.").isFalse();
    }

    private void trackChanges(final IDocument tracked)
    {
        DocumentUndoManagerRegistry.connect(tracked);
        final IDocumentUndoManager manager = DocumentUndoManagerRegistry.getDocumentUndoManager(tracked);
        manager.connect(this);
    }

    private void stopTracking(final IDocument tracked)
    {
        final IDocumentUndoManager manager = DocumentUndoManagerRegistry.getDocumentUndoManager(tracked);
        manager.disconnect(this);
        DocumentUndoManagerRegistry.disconnect(tracked);
    }

    private CTabItem addTab(final String text)
    {
        final CTabItem item = new CTabItem(tabFolder, SWT.NONE);
        item.setText(text);
        return item;
    }

    private void layOut()
    {
        shell.layout(true, true);
        UiTestWorkspace.processEvents();
    }

    private Event menuDetectEventAt(final CTabItem item, final int pixelsToTheRightOfItsLeftEdge)
    {
        final Rectangle bounds = item.getBounds();
        final Point onDisplay = tabFolder.toDisplay(bounds.x + pixelsToTheRightOfItsLeftEdge + 1,
                bounds.y + bounds.height / 2);
        final Event event = new Event();
        event.x = onDisplay.x;
        event.y = onDisplay.y;
        return event;
    }

    private void changeOnAnotherThread() throws InterruptedException
    {
        final Thread thread = new Thread(() ->
        {
            try
            {
                document.replace(0, 0, "<!-- -->");
            }
            catch (final BadLocationException e)
            {
                throw new IllegalStateException(e);
            }
        });
        thread.start();
        thread.join();
    }

    private Map<String, IAction> tabMenuActions()
    {
        final Menu menu = tabFolder.getMenu();
        menu.notifyListeners(SWT.Show, new Event());
        final Map<String, IAction> byName = new LinkedHashMap<>();
        for (final MenuItem item : menu.getItems())
        {
            final IAction action = actionOf(item.getData());
            byName.put(action.getClass().getSimpleName(), action);
        }
        return byName;
    }

    private static IAction actionOf(final Object contributionItem)
    {
        return ((ActionContributionItem) contributionItem).getAction();
    }

    private static List<String> actionNames(final IContributionItem[] items)
    {
        final List<String> names = new ArrayList<>();
        for (final IContributionItem item : items)
        {
            names.add(actionOf(item).getClass().getSimpleName());
        }
        return names;
    }

    private static Map<String, Boolean> enabledStates(final IAction... actionsToAsk)
    {
        final Map<String, Boolean> states = new LinkedHashMap<>();
        for (final IAction action : actionsToAsk)
        {
            states.put(action.getClass().getSimpleName(), action.isEnabled());
        }
        return states;
    }

    /**
     * Listens to each handler that was activated, the way that the handler service does, which makes the
     * handler listen to its action.
     */
    private List<AtomicInteger> listenToTheActivatedHandlers()
    {
        final List<AtomicInteger> reports = new ArrayList<>();
        for (final IHandler handler : services.activatedHandlers)
        {
            final AtomicInteger report = new AtomicInteger();
            handler.addHandlerListener(event -> report.incrementAndGet());
            reports.add(report);
        }
        return reports;
    }

    private void toggleTheEnablementOfTheHandledActions()
    {
        for (final IHandler handler : services.activatedHandlers)
        {
            final IAction action = ((ActionHandler) handler).getAction();
            action.setEnabled(!action.isEnabled());
        }
    }

    private static List<Integer> counts(final List<AtomicInteger> reports)
    {
        return reports.stream().map(AtomicInteger::get).toList();
    }

    private static List<Integer> indexes(final int fromInclusive, final int toExclusive)
    {
        final List<Integer> indexes = new ArrayList<>();
        for (int index = fromInclusive; index < toExclusive; index++)
        {
            indexes.add(index);
        }
        return indexes;
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }

    /**
     * A locator of the context service and the handler service that writes down what is activated and
     * deactivated, and hands out activations that are known by their position in the order of creation.
     */
    private static final class RecordingServices implements IServiceLocator
    {
        private final List<String> calls = new ArrayList<>();

        private final List<Object> contextActivations = new ArrayList<>();

        private final List<Object> handlerActivations = new ArrayList<>();

        private final List<IHandler> activatedHandlers = new ArrayList<>();

        private final IContextService contextService = proxy(IContextService.class, this::contextCall);

        private final IHandlerService handlerService = proxy(IHandlerService.class, this::handlerCall);

        @Override
        public <T> T getService(final Class<T> api)
        {
            if (api == IContextService.class)
            {
                return api.cast(contextService);
            }
            if (api == IHandlerService.class)
            {
                return api.cast(handlerService);
            }
            return null;
        }

        @Override
        public boolean hasService(final Class<?> api)
        {
            return api == IContextService.class || api == IHandlerService.class;
        }

        private Object contextCall(final Object proxy, final Method method, final Object[] arguments)
        {
            final String name = method.getName();
            if ("activateContext".equals(name))
            {
                calls.add("activateContext " + arguments[0]);
                final Object activation = proxy(IContextActivation.class, RecordingServices::identityOnly);
                contextActivations.add(activation);
                return activation;
            }
            if ("deactivateContext".equals(name))
            {
                calls.add("deactivateContext " + contextActivations.indexOf(arguments[0]));
                return null;
            }
            return identityOnly(proxy, method, arguments);
        }

        private Object handlerCall(final Object proxy, final Method method, final Object[] arguments)
        {
            final String name = method.getName();
            if ("activateHandler".equals(name))
            {
                calls.add("activateHandler " + arguments[0] + " " + arguments[1].getClass().getSimpleName());
                final Object activation = proxy(IHandlerActivation.class, RecordingServices::identityOnly);
                handlerActivations.add(activation);
                activatedHandlers.add((IHandler) arguments[1]);
                return activation;
            }
            if ("deactivateHandlers".equals(name))
            {
                final List<Integer> positions = new ArrayList<>();
                for (final Object activation : (Collection<?>) arguments[0])
                {
                    positions.add(handlerActivations.indexOf(activation));
                }
                calls.add("deactivateHandlers " + positions);
                return null;
            }
            return identityOnly(proxy, method, arguments);
        }

        private static Object identityOnly(final Object proxy, final Method method, final Object[] arguments)
        {
            final String name = method.getName();
            if ("equals".equals(name))
            {
                return proxy == arguments[0];
            }
            if ("hashCode".equals(name))
            {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(name))
            {
                return "recorded " + method.getDeclaringClass().getSimpleName();
            }
            return null;
        }
    }
}
