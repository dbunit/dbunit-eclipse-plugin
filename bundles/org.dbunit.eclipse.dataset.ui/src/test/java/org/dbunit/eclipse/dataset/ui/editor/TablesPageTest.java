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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.actions.DatasetCommandIds;
import org.dbunit.eclipse.dataset.ui.actions.InsertRowAboveAction;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.text.IDocument;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.edit.command.EditSelectionCommand;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.style.CellStyleAttributes;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.nebula.widgets.nattable.style.IStyle;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
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
    private static final String TABLES_PAGE_CONTEXT_ID = "org.dbunit.eclipse.dataset.ui.tablesPageContext";

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
    void testTablesPage_whenOpened_countsEachTablesRowsAndColumnsInItsTabTooltip() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><ONE A=\"1\"/><TWO A=\"1\" B=\"2\"/><TWO A=\"3\" B=\"4\"/><NONE/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final List<String> tooltips = new ArrayList<>();
            for (final CTabItem item : editor.getTablesPage().getTabFolder().getItems())
            {
                tooltips.add(item.getToolTipText());
            }

            assertThat(tooltips)
                    .as("A tab's tooltip must count its table's rows and columns, one in the singular.")
                    .containsExactly("1 row, 1 column", "2 rows, 2 columns", "0 rows, 0 columns");
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
    void testTablesPage_whenTheLastTableIsDeleted_disposesTheStaleGridAndDisablesItsCommands()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final IHandlerService handlerService =
                    editor.getEditorSite().getService(IHandlerService.class);

            editor.getDatasetDocument().deleteTable("USERS");

            assertThat(tablesPage.getSelection())
                    .as("Deleting the last table must clear the selection, not keep the disposed "
                            + "grid's.")
                    .isEqualTo(GridSelection.NONE);
            assertThat(tablesPage.getAddTableButton().getEnabled())
                    .as("Add Table must stay available with no tables.").isTrue();
            assertThatThrownBy(
                    () -> handlerService.executeCommand(DatasetCommandIds.EDIT_CELL_IN_DIALOG, null))
                    .as("Edit Cell in Dialog must not run on the disposed grid.")
                    .isInstanceOf(NotEnabledException.class);
        }
    }

    @Test
    void testTablesPage_whenDtdContentModelRepeatsATableName_opensWithOneTabPerTable() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<!DOCTYPE dataset [\n<!ELEMENT dataset (A*, B*, A*)>\n<!ELEMENT A EMPTY>\n"
                            + "<!ATTLIST A ID CDATA #REQUIRED>\n<!ELEMENT B EMPTY>\n"
                            + "<!ATTLIST B ID CDATA #REQUIRED>\n]>\n<dataset>\n  <B ID=\"1\"/>\n</dataset>\n");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final CTabFolder tabFolder = editor.getTablesPage().getTabFolder();

            assertThat(tabFolder.getItemCount())
                    .as("A DTD content model that repeats a table name must still open with one tab "
                            + "per table, not crash.")
                    .isEqualTo(2);
            assertThat(tabFolder.getItem(0).getText()).as("B has a row, so it comes first.")
                    .isEqualTo("B");
            assertThat(tabFolder.getItem(1).getText())
                    .as("A has no rows, so its single declared-only tab is appended last.")
                    .isEqualTo("A");
        }
    }

    @Test
    void testTablesPage_whenATableIsRenamedByTheDocument_keepsItsTabAndGrid() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><ORDERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final CTabItem usersTab = tablesPage.getTabFolder().getItem(0);
            final Control usersGrid = usersTab.getControl();

            editor.getDatasetDocument().renameTable("USERS", "CUSTOMERS");

            assertThat(tablesPage.getTabFolder().getItem(0))
                    .as("A rename that nobody announced must still leave the table its tab.")
                    .isSameAs(usersTab);
            assertThat(usersTab.getControl()).as("The renamed table must keep its grid.").isSameAs(usersGrid);
            assertThat(usersTab.getText()).as("The tab must show the new name.").isEqualTo("CUSTOMERS");
        }
    }

    @Test
    void testTablesPage_whenAnEditAddsATable_selectsItsTab() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\"/><ORDERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final CTabFolder tabFolder = editor.getTablesPage().getTabFolder();

            editor.getDatasetDocument().addTable("ACCOUNTS", List.of("ID"));

            assertThat(tabFolder.getSelection().getText()).as("The tab of the table that an edit added "
                    + "must be selected, wherever the table is in the model.").isEqualTo("ACCOUNTS");
        }
    }

    @Test
    void testTablesPage_whenATableAppearsOnTheSourcePage_keepsTheSelection() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><A ID=\"1\"/><B ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final CTabFolder tabFolder = editor.getTablesPage().getTabFolder();
            tabFolder.setSelection(1);

            sourceDocument(editor).set("<dataset><A ID=\"1\"/><B ID=\"1\"/><C ID=\"1\"/></dataset>");
            UiTestWorkspace.processEvents();

            assertThat(tabFolder.getSelection().getText())
                    .as("A table that the Source page added must not take the selection from the table that "
                            + "the user is working on.")
                    .isEqualTo("B");
        }
    }

    @Test
    void testTablesPage_whenAnUndoBringsATableBack_keepsTheSelection() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><A ID=\"1\"/><B ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            editor.getDatasetDocument().deleteTable("B");
            UiTestWorkspace.processEvents();

            tablesPage.getGlobalActionHandler(ActionFactory.UNDO.getId()).run();
            UiTestWorkspace.processEvents();

            assertThat(tablesPage.getTabFolder().getSelection().getText())
                    .as("A table that an undo brought back must not take the selection from the table that "
                            + "the user is working on.")
                    .isEqualTo("A");
        }
    }

    @Test
    void testTablesPage_whenTheRenameOfATableIsUndone_keepsItsGridAndItsSelection() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                    + "<USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            editor.getDatasetDocument().renameTable("USERS", "CUSTOMERS");
            tablesPage.selectRegion(1, 1, 1, 1);
            UiTestWorkspace.processEvents();
            final Control gridBeforeTheUndo = tablesPage.getTabFolder().getItem(0).getControl();

            tablesPage.getGlobalActionHandler(ActionFactory.UNDO.getId()).run();
            UiTestWorkspace.processEvents();

            assertThat(tablesPage.getTabFolder().getItem(0).getText())
                    .as("The undo must bring the old name back.").isEqualTo("USERS");
            assertThat(tablesPage.getTabFolder().getItem(0).getControl())
                    .as("The undo of a rename must leave the table its grid.").isSameAs(gridBeforeTheUndo);
            assertThat(tablesPage.getSelection().anchorRowIndex())
                    .as("The grid must keep its selection, the cell in the second row.").isEqualTo(1);
        }
    }

    @Test
    void testTablesPage_whenTheSourcePageRenamesATable_keepsItsTabAndGrid() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                    + "<ORDERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final CTabItem usersTab = tablesPage.getTabFolder().getItem(0);
            final Control usersGrid = usersTab.getControl();

            sourceDocument(editor)
                    .set("<dataset><CUSTOMERS ID=\"1\" NAME=\"Alice\"/><ORDERS ID=\"1\"/></dataset>");
            UiTestWorkspace.processEvents();

            assertThat(tablesPage.getTabFolder().getItem(0))
                    .as("A table that the Source page renamed must keep its tab.").isSameAs(usersTab);
            assertThat(usersTab.getControl()).as("The renamed table must keep its grid.").isSameAs(usersGrid);
            assertThat(usersTab.getText()).as("The tab must show the new name.").isEqualTo("CUSTOMERS");
        }
    }

    @Test
    void testTablesPage_whenTableNamesBecomeCaseSensitive_keepsTheTabsAndGrids() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><users ID=\"1\"/><orders ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final CTabFolder tabFolder = tablesPage.getTabFolder();
            final Control usersGrid = tabFolder.getItem(0).getControl();
            final Control ordersGrid = tabFolder.getItem(1).getControl();

            editor.getDatasetDocument().setOptions(new FlatXmlOptions(true, false));
            UiTestWorkspace.processEvents();

            assertThat(List.of(tabFolder.getItem(0).getControl(), tabFolder.getItem(1).getControl()))
                    .as("The tables are the same, only their keys changed, so they must keep their grids.")
                    .containsExactly(usersGrid, ordersGrid);
        }
    }

    @Test
    void testTablesPage_whenTheRenameOfTheSelectedTableIsUndone_selectsTheTableAgain() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><A ID=\"1\"/><B ID=\"1\"/><C ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final CTabFolder tabFolder = tablesPage.getTabFolder();
            tabFolder.setSelection(1);

            editor.getDatasetDocument().renameTable("B", "X");
            tablesPage.getGlobalActionHandler(ActionFactory.UNDO.getId()).run();
            UiTestWorkspace.processEvents();

            assertThat(tabFolder.getSelection().getText())
                    .as("Undoing the rename of the selected table must select that table again.")
                    .isEqualTo("B");
        }
    }

    @Test
    void testTablesPage_whenTheSourceMovesTheSelectedTableAheadOfTheOthers_keepsItSelected() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><A ID=\"1\"/><B ID=\"1\"/><C ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final CTabFolder tabFolder = editor.getTablesPage().getTabFolder();
            tabFolder.setSelection(2);

            sourceDocument(editor).set("<dataset><C ID=\"1\"/><A ID=\"1\"/><B ID=\"1\"/></dataset>");
            UiTestWorkspace.processEvents();

            assertThat(tabFolder.getSelection().getText())
                    .as("Moving the selected table's rows ahead of the other tables on the Source page must "
                            + "keep its tab selected.")
                    .isEqualTo("C");
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
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openInDatasetEditor(file);
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
    void testTablesPage_whenTextArrivesBeforeTheBlankStateIsLeftAndTheButtonIsPressed_changesNothingAndThrowsNothing()
            throws Exception
    {
        final String arrivedText = "<dataset><USERS ID=\"1\"/></dataset>";
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                LogRecorder log = new LogRecorder(PlatformUI.class))
        {
            final IFile file = workspace.createFile("blank.xml", "");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openInDatasetEditor(file);
            final TablesPage tablesPage = editor.getTablesPage();
            sourceDocument(editor).set(arrivedText);

            tablesPage.getCreateEmptyDatasetButton().notifyListeners(SWT.Selection, new Event());
            UiTestWorkspace.processEvents();

            assertThat(log.statuses()).filteredOn(status -> status.matches(IStatus.ERROR))
                    .extracting(IStatus::getMessage)
                    .as("The button of the blank state that is still shown when text has arrived must be "
                            + "refused with a message, not throw into the event loop.")
                    .isEmpty();
            assertThat(sourceDocument(editor).get()).as("The text that arrived must stay as it is.")
                    .isEqualTo(arrivedText);
        }
    }

    @Test
    void testTablesPage_whenFileIsBlank_centersTheMessageAndButtonWithoutTheBanner() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("blank.xml", "");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openInDatasetEditor(file);
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
    void testTablesPage_whenTheFileIsNoLongerReadOnlyWhenTheWindowIsActivated_becomesEditable()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final Path path = workspace.createExternalFile("<dataset><USERS ID=\"1\"/></dataset>");
            path.toFile().setReadOnly();
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openExternalFile(path);
            final TablesPage tablesPage = editor.getTablesPage();
            final boolean editableWhileReadOnly = tablesPage.isEditable();
            final boolean bannerWhileReadOnly = tablesPage.getErrorBanner().getControl().getVisible();

            path.toFile().setWritable(true);
            editor.handleWindowActivated(editor.getEditorSite().getWorkbenchWindow());

            assertThat(List.of(editableWhileReadOnly, bannerWhileReadOnly, tablesPage.isEditable(),
                    tablesPage.getErrorBanner().getControl().getVisible()))
                    .as("A read-only file shows the banner and refuses edits, until activating the window "
                            + "finds that the file is writable, with no change of the text.")
                    .containsExactly(false, true, true, false);
        }
    }

    @Test
    void testTablesPage_whenTheFileBecomesReadOnlyWhenTheWindowIsActivated_showsTheBannerAndRefusesEdits()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final Path path = workspace.createExternalFile("<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openExternalFile(path);
            final TablesPage tablesPage = editor.getTablesPage();
            final boolean editableWhileWritable = tablesPage.isEditable();

            path.toFile().setReadOnly();
            editor.handleWindowActivated(editor.getEditorSite().getWorkbenchWindow());

            assertThat(List.of(editableWhileWritable, tablesPage.isEditable(),
                    tablesPage.getErrorBanner().getControl().getVisible()))
                    .as("A file that became read-only must show the banner and refuse edits at once.")
                    .containsExactly(true, false, true);
        }
    }

    @Test
    void testTablesPage_whenTheWorkbenchTurnsDarkWhileTheEditorIsOpen_darkensItsGridsOnActivation()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final NatTable natTable = (NatTable) tablesPage.getTabFolder().getSelection().getControl();
            final Color black = new Color(natTable.getDisplay(), 0, 0, 0);
            try
            {
                final Color backgroundBefore = bodyBackground(natTable);
                tablesPage.getControl().setBackground(black);

                editor.handleWindowActivated(editor.getEditorSite().getWorkbenchWindow());

                final Color backgroundAfter = bodyBackground(natTable);
                assertThat(List.of(brightness(backgroundBefore) > 128, brightness(backgroundAfter) < 128))
                        .as("The grid of an open editor must take the dark theme of the workbench when the "
                                + "editor is activated again.")
                        .containsExactly(true, true);
            }
            finally
            {
                black.dispose();
            }
        }
    }

    private static Color bodyBackground(final NatTable natTable)
    {
        final IStyle style = natTable.getConfigRegistry().getConfigAttribute(CellConfigAttributes.CELL_STYLE,
                DisplayMode.NORMAL);
        return style.getAttributeValue(CellStyleAttributes.BACKGROUND_COLOR);
    }

    private static int brightness(final Color color)
    {
        return (color.getRed() + color.getGreen() + color.getBlue()) / 3;
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
    void testActivate_whenTheTextChangedWhileInactiveButTheSourceSelectionDidNotMove_refreshesTheModel()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            tablesPage.selectRegion(0, 0, 1, 1);
            tablesPage.deactivate();
            final IDocument document = sourceDocument(editor);
            document.replace(document.getLength(), 0, "<!--x-->");
            UiTestWorkspace.processEvents();

            tablesPage.activate();

            assertThat(editor.getDatasetDocument().isStale())
                    .as("Activating the Tables page must refresh the model of a text that changed while it "
                            + "was inactive, also when the selection of the Source page stayed where the "
                            + "page left it.")
                    .isFalse();
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
                    .contains(TABLES_PAGE_CONTEXT_ID);
            assertThatCode(() -> handlerService.executeCommand(DatasetCommandIds.INSERT_ROW_ABOVE, null))
                    .as("The Tables page's command handlers must be active.")
                    .doesNotThrowAnyException();

            editor.showOnSourcePage(0, 0);
            UiTestWorkspace.processEvents();

            assertThat(contextService.getActiveContextIds())
                    .as("Switching to the Source page must deactivate the Tables page's context.")
                    .doesNotContain(TABLES_PAGE_CONTEXT_ID);
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
                    .isEqualTo(new GridSelection("USERS", 3, 2, 1, 1, List.of(1), List.of(1), false));

            tablesPage.selectRegion(0, 0, 2, 2);
            handlerService.executeCommand(DatasetCommandIds.DELETE_ROWS, null);

            assertThat(tablesPage.getSelection())
                    .as("Delete Rows must select the row that takes the place of the deleted ones.")
                    .isEqualTo(new GridSelection("USERS", 1, 2, 0, 0, List.of(0), List.of(0), false));
        }
    }

    @Test
    void testDeleteHandler_withWholeRowsSelected_deletesTheRows() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\" NAME=\"A\"/>"
                    + "<USERS ID=\"2\" NAME=\"B\"/><USERS ID=\"3\" NAME=\"C\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            tablesPage.selectRegion(0, 0, 2, 2);

            assertThat(tablesPage.getSelection().wholeRowsSelected())
                    .as("Selecting every column of two rows must select them in full.").isTrue();

            tablesPage.getGlobalActionHandler(ActionFactory.DELETE.getId()).run();

            assertThat(editor.getDatasetDocument().getModel().findTable("USERS").orElseThrow().getRows())
                    .extracting(row -> row.getValues())
                    .as("Delete with whole rows selected must delete the rows, not fail to make them NULL.")
                    .containsExactly(List.of("3", "C"));
        }
    }

    @Test
    void testDeleteHandler_withACellSelected_makesTheCellNull() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\" NAME=\"A\"/><USERS ID=\"2\" NAME=\"B\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            tablesPage.selectRegion(1, 1, 1, 1);

            tablesPage.getGlobalActionHandler(ActionFactory.DELETE.getId()).run();

            assertThat(editor.getDatasetDocument().getModel().findTable("USERS").orElseThrow().getRows())
                    .extracting(row -> row.getValues())
                    .as("Delete with part of a row selected must only make the cell NULL.")
                    .containsExactly(List.of("1", "A"), Arrays.asList("2", null));
        }
    }

    @Test
    void testDeleteHandler_withEveryRowSelected_keepsTheColumnsSoThatARowCanBeInserted() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\" NAME=\"A\"/><USERS ID=\"2\" NAME=\"B\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();
            final IHandlerService handlerService =
                    editor.getEditorSite().getService(IHandlerService.class);
            tablesPage.selectRegion(0, 0, 2, 2);

            tablesPage.getGlobalActionHandler(ActionFactory.DELETE.getId()).run();

            final DatasetTable emptied = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(emptied.getColumns())
                    .as("Deleting every row must leave the table its columns, as pending ones.")
                    .containsExactly(new DatasetColumn("ID", false, false, true),
                            new DatasetColumn("NAME", false, false, true));
            assertThat(tablesPage.getSelection().columnCount())
                    .as("The grid must still show both columns.").isEqualTo(2);

            handlerService.executeCommand(DatasetCommandIds.INSERT_ROW_BELOW, null);

            final DatasetTable refilled = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(refilled.getRows()).extracting(row -> row.getValues())
                    .as("A row must be insertable into the table that has no rows.")
                    .containsExactly(Arrays.asList("", null));
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
                    .isEqualTo(new GridSelection("T", 3, 1, 0, 0, List.of(0, 1), List.of(0), true));
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
    void testGlobalActionHandler_afterUndoThenRedoOfAGridEdit_updatesEnablementAtEachStep() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
            final IFile file = workspace.createFile("dataset.xml", originalText);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();
            final IAction undo = tablesPage.getGlobalActionHandler(ActionFactory.UNDO.getId());
            final IAction redo = tablesPage.getGlobalActionHandler(ActionFactory.REDO.getId());

            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Carol")));
            UiTestWorkspace.processEvents();
            undo.run();
            UiTestWorkspace.processEvents();

            assertThat(redo.isEnabled()).as("Redo must become enabled right after Undo runs.").isTrue();

            redo.run();
            UiTestWorkspace.processEvents();

            assertThat(undo.isEnabled()).as("Undo must become enabled right after Redo runs.").isTrue();
        }
    }

    @Test
    void testGlobalActionHandler_afterAddColumnThenItsUndo_updatesEnablementAtEachStep() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();
            final IAction undo = tablesPage.getGlobalActionHandler(ActionFactory.UNDO.getId());
            final IAction redo = tablesPage.getGlobalActionHandler(ActionFactory.REDO.getId());

            datasetDocument.addColumn("USERS", "EXTRA");
            UiTestWorkspace.processEvents();

            assertThat(undo.isEnabled())
                    .as("Adding a pending column must enable Undo even though it changes no text.").isTrue();

            undo.run();
            UiTestWorkspace.processEvents();

            assertThat(redo.isEnabled())
                    .as("Undoing a pending-column operation must enable Redo.").isTrue();
        }
    }

    @Test
    void testGlobalActionHandler_undoAndRedoOfARowInsert_refreshTheModelBeforeTheyReturn() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();
            datasetDocument.insertRows("USERS", 1, List.of(List.of("9", "Zed")));
            UiTestWorkspace.processEvents();

            tablesPage.getGlobalActionHandler(ActionFactory.UNDO.getId()).run();

            assertThat(datasetDocument.isStale())
                    .as("Undo must bring the model up to date before it returns, not leave that to a "
                            + "queued runnable.")
                    .isFalse();
            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .extracting(row -> row.getValues())
                    .as("The model must show the rows that the undo left.")
                    .containsExactly(List.of("1", "Alice"), List.of("2", "Bob"));

            tablesPage.getGlobalActionHandler(ActionFactory.REDO.getId()).run();

            assertThat(datasetDocument.isStale())
                    .as("Redo must bring the model up to date before it returns, not leave that to a "
                            + "queued runnable.")
                    .isFalse();
            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .extracting(row -> row.getValues())
                    .as("The model must show the rows that the redo brought back.")
                    .containsExactly(List.of("1", "Alice"), List.of("9", "Zed"), List.of("2", "Bob"));
        }
    }

    @Test
    void testTablesPage_whenFillDownFollowsAnUndoOfARowInsertBeforeEventsRun_actsOnTheUndoneRows()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                    + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();
            final IHandlerService handlerService =
                    editor.getEditorSite().getService(IHandlerService.class);
            datasetDocument.insertRows("USERS", 1, List.of(List.of("9", "Zed")));
            UiTestWorkspace.processEvents();
            tablesPage.selectRegion(1, 1, 1, 2);

            tablesPage.getGlobalActionHandler(ActionFactory.UNDO.getId()).run();
            handlerService.executeCommand(DatasetCommandIds.FILL_DOWN, null);

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .extracting(row -> row.getValues())
                    .as("A Fill Down that is already queued behind an undo must act on the rows that the "
                            + "undo left, not copy the value of the row that it removed.")
                    .containsExactly(List.of("1", "Alice"), List.of("2", "Alice"), List.of("3", "Carol"));
        }
    }

    @Test
    void testTablesPage_whenFillDownFollowsAChangeOfTheTextBeforeEventsRun_actsOnTheChangedText()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                    + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();
            final IHandlerService handlerService =
                    editor.getEditorSite().getService(IHandlerService.class);
            UiTestWorkspace.processEvents();
            tablesPage.selectRegion(1, 0, 1, 2);
            final IDocument document = sourceDocument(editor);

            document.replace(document.get().indexOf("Alice"), "Alice".length(), "Zed");
            handlerService.executeCommand(DatasetCommandIds.FILL_DOWN, null);

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .extracting(row -> row.getValues())
                    .as("A Fill Down that runs before the page has refreshed from a change of the text must "
                            + "copy the value that the text has now, not the one that it had.")
                    .containsExactly(List.of("1", "Zed"), List.of("2", "Zed"), List.of("3", "Carol"));
        }
    }

    @Test
    void testTablesPage_whileACellEditorIsOpen_leavesTheKeysOfTheGridCommandsToTheEditor() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\" NAME=\"A\"/><USERS ID=\"2\" NAME=\"B\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final Composite page = (Composite) tablesPage.getControl();
            page.setSize(800, 600);
            page.layout(true, true);
            UiTestWorkspace.processEvents();
            final NatTable natTable = (NatTable) tablesPage.getTabFolder().getSelection().getControl();
            final IContextService contextService = editor.getEditorSite().getService(IContextService.class);
            tablesPage.selectRegion(1, 1, 1, 1);
            final boolean activeBeforeEditing =
                    contextService.getActiveContextIds().contains(TABLES_PAGE_CONTEXT_ID);

            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            final boolean activeWhileEditing =
                    contextService.getActiveContextIds().contains(TABLES_PAGE_CONTEXT_ID);
            natTable.commitAndCloseActiveCellEditor();
            final boolean activeAfterEditing =
                    contextService.getActiveContextIds().contains(TABLES_PAGE_CONTEXT_ID);

            assertThat(tablesPage.hasActiveCellEditor()).as("The editor must be closed again.").isFalse();
            assertThat(List.of(activeBeforeEditing, activeWhileEditing, activeAfterEditing))
                    .as("The key bindings of the grid commands must be active, give way to an open cell "
                            + "editor, which would not get Ctrl+Delete and the other keys that they use "
                            + "otherwise, and come back when it closes.")
                    .containsExactly(true, false, true);
        }
    }

    @Test
    void testTablesPage_afterManySwitchesBetweenThePages_addsNoListenerToItsActionsEachTime() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IAction action = insertRowAboveAction(editor);
            showTheSourcePageAndThenTheTablesPage(editor);
            final int listenersAfterOneRoundTrip = propertyChangeListenerCount(action);

            for (int round = 0; round < 4; round++)
            {
                showTheSourcePageAndThenTheTablesPage(editor);
            }

            assertThat(propertyChangeListenerCount(action))
                    .as("A handler that stays attached to its action once the page is left adds a listener "
                            + "each time the Tables page comes back, and the action tells all of them about "
                            + "each change.")
                    .isEqualTo(listenersAfterOneRoundTrip);
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

    @Test
    void testTablesPage_whenOpened_selectsTheFirstCellOfTheFirstTable() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\" NAME=\"A\"/><USERS ID=\"2\" NAME=\"B\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();

            assertThat(tablesPage.getSelection())
                    .as("Opening a dataset must select the first cell of the first table.")
                    .isEqualTo(new GridSelection("USERS", 2, 2, 0, 0, List.of(0), List.of(0), false));
        }
    }

    @Test
    void testTablesPage_whenATableHasNoRows_hasNoInitialSelection() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<!DOCTYPE dataset [\n<!ELEMENT dataset (ORDERS*)>\n<!ELEMENT ORDERS EMPTY>\n"
                            + "<!ATTLIST ORDERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();

            assertThat(tablesPage.getSelection())
                    .as("A table without rows must start with no cell selected.")
                    .isEqualTo(new GridSelection("ORDERS", 0, 1, -1, -1, List.of(), List.of(), false));
        }
    }

    @Test
    void testTablesPage_whenTheTextLosesARowDuringACellEdit_closesTheCellEditorWithoutWritingItsValue()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\" NAME=\"A\"/><USERS ID=\"2\" NAME=\"B\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final Composite page = (Composite) tablesPage.getControl();
            page.setSize(800, 600);
            page.layout(true, true);
            UiTestWorkspace.processEvents();
            final NatTable natTable = (NatTable) tablesPage.getTabFolder().getSelection().getControl();
            tablesPage.selectRegion(1, 1, 1, 1);
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            natTable.getActiveCellEditor().setEditorValue("Zed");
            final String reloadedText = "<dataset><USERS ID=\"2\" NAME=\"B\"/></dataset>";

            sourceDocument(editor).set(reloadedText);
            UiTestWorkspace.processEvents();

            assertThat(tablesPage.hasActiveCellEditor())
                    .as("A reload that removes a row must close the editor, whose cell is another row now.")
                    .isFalse();
            natTable.commitAndCloseActiveCellEditor();
            UiTestWorkspace.processEvents();
            assertThat(sourceDocument(editor).get())
                    .as("The cancelled value must not land in the reloaded text.").isEqualTo(reloadedText);
        }
    }

    private static IAction insertRowAboveAction(final FlatXmlDatasetEditor editor)
    {
        final MenuManager menu = new MenuManager();
        editor.getTablesPage().fillContextMenu(menu, GridRegion.BODY);
        for (final IContributionItem item : menu.getItems())
        {
            if (item instanceof final ActionContributionItem actionItem
                    && actionItem.getAction() instanceof InsertRowAboveAction)
            {
                return actionItem.getAction();
            }
        }
        throw new AssertionError("The menu of the body has no Insert Row Above action.");
    }

    private static void showTheSourcePageAndThenTheTablesPage(final FlatXmlDatasetEditor editor)
    {
        editor.showOnSourcePage(0, 0);
        UiTestWorkspace.processEvents();
        final CTabFolder pages = (CTabFolder) editor.getTablesPage().getControl().getParent();
        final CTabItem tablesTab = pages.getItem(0);
        pages.setSelection(tablesTab);
        final Event click = new Event();
        click.item = tablesTab;
        pages.notifyListeners(SWT.Selection, click);
        UiTestWorkspace.processEvents();
    }

    /**
     * Counts the listeners that an action keeps in its list of them, which JFace offers no way to read, so
     * the list is found by its type.
     */
    private static int propertyChangeListenerCount(final IAction action) throws IllegalAccessException
    {
        for (Class<?> type = action.getClass(); type != null; type = type.getSuperclass())
        {
            for (final Field field : type.getDeclaredFields())
            {
                if (ListenerList.class.isAssignableFrom(field.getType()))
                {
                    field.setAccessible(true);
                    return ((ListenerList<?>) field.get(action)).size();
                }
            }
        }
        throw new AssertionError("The action has no list of listeners that the test could count.");
    }

    private static IDocument sourceDocument(final FlatXmlDatasetEditor editor)
    {
        final ITextEditor sourceEditor = editor.getSourceEditor();
        return sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput());
    }
}
