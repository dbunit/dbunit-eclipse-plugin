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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.ui.actions.DatasetCommandIds;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link TablesPage} against the Tables Page layout, reconciliation, and refresh-scheduling rules.
 */
class TablesPageTest
{
    @Test
    void testTablesPage_whenOpened_showsOneTabPerTableInModelOrder() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\"/><ORDERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final CTabFolder tabFolder = editor.getTablesPage().getTabFolder();

            assertThat(tabFolder.getItemCount()).as("There must be one tab per table.").isEqualTo(2);
            assertThat(tabFolder.getItem(0).getText()).as("Tabs must be in model order.")
                    .isEqualTo("USERS");
            assertThat(tabFolder.getItem(1).getText()).as("Tabs must be in model order.")
                    .isEqualTo("ORDERS");
        }
    }

    @Test
    void testTablesPage_whenTablesAreAddedAndRemoved_updatesTheTabs() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();

            datasetDocument.addTable("ORDERS", List.of());

            assertThat(tablesPage.getTabFolder().getItemCount()).as("Adding a table must add a tab.")
                    .isEqualTo(2);

            datasetDocument.deleteTable("USERS");

            assertThat(tablesPage.getTabFolder().getItemCount())
                    .as("Deleting a table must remove its tab.").isEqualTo(1);
            assertThat(tablesPage.getTabFolder().getItem(0).getText())
                    .as("The remaining table's tab must survive.").isEqualTo("ORDERS");
        }
    }

    @Test
    void testTablesPage_whenRenamingATableThroughExpectRename_keepsTheSameTabItem() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final CTabItem originalItem = tablesPage.getTabFolder().getItem(0);

            tablesPage.expectRename("USERS", "CUSTOMERS");
            editor.getDatasetDocument().renameTable("USERS", "CUSTOMERS");

            assertThat(tablesPage.getTabFolder().getItemCount()).as("The rename must not add a tab.")
                    .isEqualTo(1);
            assertThat(tablesPage.getTabFolder().getItem(0))
                    .as("expectRename must keep the same tab item, not recreate it.")
                    .isSameAs(originalItem);
            assertThat(tablesPage.getTabFolder().getItem(0).getText())
                    .as("The surviving tab must show the new name.").isEqualTo("CUSTOMERS");
        }
    }

    @Test
    void testTablesPage_whenXmlHasABlockingError_showsTheBannerAndIsNotEditable() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("broken.xml", "<dataset><USERS ID=\"1\"</dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();

            assertThat(tablesPage.isEditable())
                    .as("A blocking XML error must make the page non-editable.").isFalse();
            assertThat(tablesPage.getErrorBanner().getControl().getVisible())
                    .as("A blocking XML error must show the banner.").isTrue();
        }
    }

    @Test
    void testTablesPage_whenFileIsBlank_showsCreateEmptyDatasetButtonThatCreatesIt() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("blank.xml", "");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();

            assertThat(tablesPage.isShowingBlankState())
                    .as("A blank file must show the blank-document state.").isTrue();

            tablesPage.getCreateEmptyDatasetButton().notifyListeners(SWT.Selection, new Event());
            UiTestWorkspace.processEvents();

            assertThat(editor.getDatasetDocument().isBlank())
                    .as("Clicking Create Empty Dataset must create the dataset.").isFalse();
            assertThat(tablesPage.isShowingBlankState())
                    .as("The page must switch away from the blank-document state.").isFalse();
        }
    }

    @Test
    void testTablesPage_whenFileIsBlank_centersTheMessageAndButtonWithoutTheBanner() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("blank.xml", "");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final Composite page = (Composite) tablesPage.getControl();
            page.setSize(800, 600);
            page.layout(true, true);
            final Button button = tablesPage.getCreateEmptyDatasetButton();
            final Rectangle buttonBounds = page.getDisplay().map(button.getParent(), page, button.getBounds());

            assertThat(tablesPage.getErrorBanner().getControl().getVisible())
                    .as("A blank file must not show the error banner, as the blank state explains it.")
                    .isFalse();
            assertThat(Math.abs(buttonBounds.y + buttonBounds.height / 2 - 300))
                    .as("The button must sit with the message at the center of the page, not at its bottom.")
                    .isLessThan(100);
        }
    }

    @Test
    void testTablesPage_whenBlankFileIsReadOnly_disablesCreateEmptyDatasetAndLeavesTheFileBlank()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openReadOnlyExternalFile("");
            final Button button = editor.getTablesPage().getCreateEmptyDatasetButton();

            assertThat(button.getEnabled()).as("A read-only file must disable Create Empty Dataset.").isFalse();

            button.notifyListeners(SWT.Selection, new Event());
            UiTestWorkspace.processEvents();

            assertThat(editor.getDatasetDocument().isBlank())
                    .as("Create Empty Dataset must not change a file whose input state does not validate.")
                    .isTrue();
        }
    }

    @Test
    void testTablesPage_whenDatasetHasNoTables_showsTheNoTablesStateWithTheButtonEnabled() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset/>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();

            assertThat(tablesPage.isShowingNoTablesState())
                    .as("A dataset without tables must show the no-tables state.").isTrue();
            assertThat(tablesPage.getAddTableButton().getEnabled())
                    .as("The Add Table button must be enabled for an editable dataset.").isTrue();
        }
    }

    @Test
    void testTablesPage_whenATableIsAddedToAnEmptyDataset_showsTheTabFolderAgain() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset/>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();

            editor.getDatasetDocument().addTable("USERS", List.of());

            assertThat(tablesPage.isShowingNoTablesState())
                    .as("Adding a table must leave the no-tables state.").isFalse();
            assertThat(tablesPage.getTabFolder().getItemCount()).as("Adding a table must show its tab.")
                    .isEqualTo(1);
        }
    }

    @Test
    void testTablesPage_whenNoTablesStateIsReadOnly_disablesTheAddTableButton() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final FlatXmlDatasetEditor editor =
                    (FlatXmlDatasetEditor) workspace.openReadOnlyExternalFile("<dataset/>");
            final TablesPage tablesPage = editor.getTablesPage();

            assertThat(tablesPage.isShowingNoTablesState())
                    .as("A read-only dataset without tables must still show the no-tables state.").isTrue();
            assertThat(tablesPage.getAddTableButton().getEnabled())
                    .as("A read-only dataset must disable the Add Table button.").isFalse();
        }
    }

    @Test
    void testRefreshScheduling_whenTablesPageIsNotActive_refreshesOnlyOnActivation() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            tablesPage.deactivate();

            final ITextEditor sourceEditor = editor.getSourceEditor();
            sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput()).replace(0, 0,
                    "<!--x-->");
            UiTestWorkspace.processEvents();

            assertThat(editor.getDatasetDocument().isStale())
                    .as("A source change while the Tables page is inactive must not refresh immediately.")
                    .isTrue();

            tablesPage.activate();

            assertThat(editor.getDatasetDocument().isStale())
                    .as("Activating the Tables page must refresh the stale model.").isFalse();
        }
    }

    @Test
    void testActivate_whenTheTablesPageIsActive_activatesTheContextAndHandlers() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IContextService contextService =
                    editor.getEditorSite().getService(IContextService.class);
            final IHandlerService handlerService =
                    editor.getEditorSite().getService(IHandlerService.class);

            assertThat(contextService.getActiveContextIds())
                    .as("Opening on the Tables page must activate its context.")
                    .contains("org.dbunit.eclipse.dataset.ui.tablesPageContext");
            assertThatCode(() -> handlerService.executeCommand(DatasetCommandIds.INSERT_ROW_ABOVE, null))
                    .as("The Tables page's command handlers must be active.")
                    .doesNotThrowAnyException();

            editor.showOnSourcePage(0, 0);
            UiTestWorkspace.processEvents();

            assertThat(contextService.getActiveContextIds())
                    .as("Switching to the Source page must deactivate the Tables page's context.")
                    .doesNotContain("org.dbunit.eclipse.dataset.ui.tablesPageContext");
        }
    }

    @Test
    void testInsertRowBelowAndDeleteRows_throughTheirCommands_selectTheRowAtTheirPosition()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\" NAME=\"A\"/><USERS ID=\"2\" NAME=\"B\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final IHandlerService handlerService =
                    editor.getEditorSite().getService(IHandlerService.class);
            tablesPage.selectRegion(1, 0, 1, 1);

            handlerService.executeCommand(DatasetCommandIds.INSERT_ROW_BELOW, null);

            assertThat(tablesPage.getSelection())
                    .as("Insert Row Below must select the new row's cell in the anchor column.")
                    .isEqualTo(new GridSelection("USERS", 3, 2, 1, 1, List.of(1), List.of(1), 1, 1, 1, 1,
                            false));

            tablesPage.selectRegion(0, 0, 2, 2);
            handlerService.executeCommand(DatasetCommandIds.DELETE_ROWS, null);

            assertThat(tablesPage.getSelection())
                    .as("Delete Rows must select the row that takes the place of the deleted ones.")
                    .isEqualTo(new GridSelection("USERS", 1, 2, 0, 0, List.of(0), List.of(0), 0, 0, 0, 0,
                            false));
        }
    }

    @Test
    void testMoveRowsUp_throughItsCommand_keepsTheMovedRowsSelected() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><T A=\"1\"/><T A=\"2\"/><T A=\"3\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final IHandlerService handlerService =
                    editor.getEditorSite().getService(IHandlerService.class);
            tablesPage.selectRegion(0, 1, 1, 2);

            handlerService.executeCommand(DatasetCommandIds.MOVE_ROWS_UP, null);

            assertThat(tablesPage.getSelection())
                    .as("Move Rows Up must keep the moved rows selected, so that they can move again.")
                    .isEqualTo(new GridSelection("T", 3, 1, 0, 0, List.of(0, 1), List.of(0), 0, 1, 0, 0,
                            true));
        }
    }

    @Test
    void testGlobalActionHandler_undoAfterAGridEdit_restoresTheTextAndRedoReappliesIt() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
            final IFile file = workspace.createFile("dataset.xml", originalText);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();
            final IDocument document = sourceDocument(editor);

            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Carol")));
            UiTestWorkspace.processEvents();

            tablesPage.getGlobalActionHandler(ActionFactory.UNDO.getId()).run();
            UiTestWorkspace.processEvents();

            assertThat(document.get()).as("Undo must restore the original text.")
                    .isEqualTo(originalText);
            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0)
                    .getValue(1)).as("Undo must restore the grid's model value.").isEqualTo("Alice");

            tablesPage.getGlobalActionHandler(ActionFactory.REDO.getId()).run();
            UiTestWorkspace.processEvents();

            assertThat(document.get()).as("Redo must reapply the edit.")
                    .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Carol\"/></dataset>");
            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0)
                    .getValue(1)).as("Redo must restore the grid's model value.").isEqualTo("Carol");
        }
    }

    @Test
    void testGlobalActionHandler_undoFromTheSourcePage_undoesATablesPageEditTooBecauseHistoryIsShared()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
            final IFile file = workspace.createFile("dataset.xml", originalText);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();
            final ITextEditor sourceEditor = editor.getSourceEditor();
            final IDocument document = sourceDocument(editor);

            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Carol")));
            UiTestWorkspace.processEvents();

            sourceEditor.getAction(ITextEditorActionConstants.UNDO).run();
            UiTestWorkspace.processEvents();

            assertThat(document.get())
                    .as("The Source page's undo action must undo a Tables-page edit: they share one history.")
                    .isEqualTo(originalText);
        }
    }

    @Test
    void testTablesPage_afterTheEditorInputChanges_undoesAndRefreshesWithTheNewDocument() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
            final IEditorPart otherEditor =
                    workspace.open(workspace.createFile("saved-as.xml", originalText));
            final IEditorInput savedAsInput = otherEditor.getEditorInput();
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                    .closeEditor(otherEditor, false);
            final FlatXmlDatasetEditor editor =
                    (FlatXmlDatasetEditor) workspace.open(workspace.createFile("dataset.xml", originalText));
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();

            editor.getSourceEditor().setInput(savedAsInput);
            UiTestWorkspace.processEvents();
            final IDocument newDocument = sourceDocument(editor);
            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Carol")));
            tablesPage.getGlobalActionHandler(ActionFactory.UNDO.getId()).run();

            assertThat(newDocument.get()).as("Undo on the Tables page must undo the new document's edit.")
                    .isEqualTo(originalText);

            newDocument.replace(newDocument.get().indexOf("Alice"), "Alice".length(), "Dave");
            UiTestWorkspace.processEvents();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0)
                    .getValue(1)).as("A change to the new document must refresh the Tables page.")
                    .isEqualTo("Dave");
        }
    }

    private static IDocument sourceDocument(final FlatXmlDatasetEditor editor)
    {
        final ITextEditor sourceEditor = editor.getSourceEditor();
        return sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput());
    }
}
