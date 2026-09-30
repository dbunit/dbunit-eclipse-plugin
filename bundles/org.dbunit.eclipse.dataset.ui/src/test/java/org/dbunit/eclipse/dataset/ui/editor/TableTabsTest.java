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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.text.Document;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.edit.command.EditSelectionCommand;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectCellCommand;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link TableTabs}: the tabs that follow the tables of the model, keep the grid of a renamed table,
 * select the tab of a new table, describe each table in its tab, and select a cell of any table.
 */
class TableTabsTest
{
    private static final String USERS_AND_ORDERS = "<dataset><USERS ID=\"1\"/><ORDERS ID=\"1\"/></dataset>";

    private Shell shell;

    private Document document;

    private FlatXmlDatasetDocument datasetDocument;

    private CTabFolder tabFolder;

    private AtomicInteger selectionChanges;

    private TableTabs tabs;

    @BeforeEach
    void createTabs()
    {
        shell = new Shell(Display.getDefault());
        shell.setLayout(new FillLayout());
        shell.setSize(400, 300);
        document = new Document("<dataset/>");
        datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE, FlatXmlOptions.DBUNIT_DEFAULTS,
                () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        tabFolder = new CTabFolder(shell, SWT.TOP | SWT.BORDER | SWT.FLAT);
        selectionChanges = new AtomicInteger();
        final LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources(), shell);
        tabs = new TableTabs(tabFolder, new StubGridContext(datasetDocument), resources,
                selectionChanges::incrementAndGet);
        shell.open();
        UiTestWorkspace.processEvents();
    }

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testReconcile_whenTheModelHasTables_showsOneTabPerTableInModelOrder()
    {
        final DatasetModel model = show(USERS_AND_ORDERS);

        tabs.reconcile(model);

        assertThat(tabTexts()).as("There must be one tab per table, in the order of the model.")
                .containsExactly("USERS", "ORDERS");
    }

    @Test
    void testReconcile_whenNoTabWasSelected_selectsTheFirstTab()
    {
        final DatasetModel model = show(USERS_AND_ORDERS);

        tabs.reconcile(model);

        assertThat(tabFolder.getSelectionIndex()).as("The first tab must be selected.").isZero();
    }

    @Test
    void testReconcile_whenATableIsInsertedBeforeAnother_keepsTheGridsAndMovesTheTab()
    {
        tabs.reconcile(show(USERS_AND_ORDERS));
        final Control usersGrid = tabFolder.getItem(0).getControl();
        final Control ordersGrid = tabFolder.getItem(1).getControl();

        tabs.reconcile(show("<dataset><USERS ID=\"1\"/><ITEMS ID=\"1\"/><ORDERS ID=\"1\"/></dataset>"));

        assertThat(tabTexts()).as("The new table's tab must be between the others.")
                .containsExactly("USERS", "ITEMS", "ORDERS");
        assertThat(tabFolder.getItem(0).getControl()).as("The first table must keep its grid.")
                .isSameAs(usersGrid);
        assertThat(tabFolder.getItem(2).getControl()).as("The moved table must keep its grid.")
                .isSameAs(ordersGrid);
        assertThat(ordersGrid.isDisposed()).as("The moved table's grid must stay alive.").isFalse();
    }

    @Test
    void testReconcile_whenATableIsRemoved_disposesItsTabAndGrid()
    {
        tabs.reconcile(show(USERS_AND_ORDERS));
        final Control usersGrid = tabFolder.getItem(0).getControl();

        tabs.reconcile(show("<dataset><ORDERS ID=\"1\"/></dataset>"));

        assertThat(tabTexts()).as("Only the remaining table may have a tab.").containsExactly("ORDERS");
        assertThat(usersGrid.isDisposed()).as("The removed table's grid must be disposed.").isTrue();
    }

    @Test
    void testReconcile_whenTheModelIsEmpty_removesEveryTab()
    {
        tabs.reconcile(show(USERS_AND_ORDERS));

        tabs.reconcile(DatasetModel.EMPTY);

        assertThat(tabTexts()).as("No table may have a tab.").isEmpty();
    }

    @Test
    void testReconcile_whenTwoTablesShareAKey_showsOneTab()
    {
        final DatasetModel parsed = show("<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetTable users = parsed.getTables().get(0);
        final DatasetModel model = new DatasetModel(List.of(users, users), List.of(), true);

        tabs.reconcile(model);

        assertThat(tabTexts()).as("A key must have one tab.").containsExactly("USERS");
    }

    @Test
    void testReconcile_whenARenameIsExpected_keepsTheTabAndGridUnderTheNewKey()
    {
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/></dataset>"));
        final CTabItem usersTab = tabFolder.getItem(0);
        final Control usersGrid = usersTab.getControl();

        tabs.expectRename("USERS", "CUSTOMERS");
        tabs.reconcile(show("<dataset><CUSTOMERS ID=\"1\"/></dataset>"));

        assertThat(tabFolder.getItem(0)).as("The renamed table must keep its tab.").isSameAs(usersTab);
        assertThat(usersTab.getText()).as("The tab must show the new name.").isEqualTo("CUSTOMERS");
        assertThat(usersTab.getControl()).as("The renamed table must keep its grid.").isSameAs(usersGrid);
        assertThat(anchorOf(tabs.activeGrid().getSelection()))
                .as("The grid must follow the table to its new key.")
                .isEqualTo(new CellAddress("CUSTOMERS", 0, 0));
    }

    @Test
    void testReconcile_whenARenameIsNotExpected_replacesTheTabAndGrid()
    {
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/></dataset>"));
        final Control usersGrid = tabFolder.getItem(0).getControl();

        tabs.reconcile(show("<dataset><CUSTOMERS ID=\"1\"/></dataset>"));

        assertThat(tabTexts()).as("The table under its new name must have a tab.")
                .containsExactly("CUSTOMERS");
        assertThat(usersGrid.isDisposed()).as("The grid of the name that is gone must be disposed.").isTrue();
    }

    @Test
    void testCancelExpectedRename_beforeTheReconcile_replacesTheTabAndGrid()
    {
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/></dataset>"));
        final Control usersGrid = tabFolder.getItem(0).getControl();

        tabs.expectRename("USERS", "CUSTOMERS");
        tabs.cancelExpectedRename();
        tabs.reconcile(show("<dataset><CUSTOMERS ID=\"1\"/></dataset>"));

        assertThat(usersGrid.isDisposed()).as("A cancelled rename must not keep the grid.").isTrue();
    }

    @Test
    void testReconcile_whenAnExpectedRenameDidNotHappen_forgetsItForTheNextReconcile()
    {
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/></dataset>"));
        tabs.expectRename("USERS", "CUSTOMERS");
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/><NOTES ID=\"1\"/></dataset>"));
        final Control usersGrid = tabFolder.getItem(0).getControl();

        tabs.reconcile(show("<dataset><CUSTOMERS ID=\"1\"/><NOTES ID=\"1\"/></dataset>"));

        assertThat(usersGrid.isDisposed()).as("An expectation must last for one reconcile.").isTrue();
    }

    @Test
    void testReconcile_whenANewTableIsExpected_selectsItsTab()
    {
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/></dataset>"));

        tabs.expectNewTableSelected("ORDERS");
        tabs.reconcile(show(USERS_AND_ORDERS));

        assertThat(tabFolder.getSelection().getText()).as("The new table's tab must be selected.")
                .isEqualTo("ORDERS");
    }

    @Test
    void testCancelExpectedNewTableSelected_beforeTheReconcile_keepsTheSelection()
    {
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/></dataset>"));

        tabs.expectNewTableSelected("ORDERS");
        tabs.cancelExpectedNewTableSelected();
        tabs.reconcile(show(USERS_AND_ORDERS));

        assertThat(tabFolder.getSelection().getText()).as("A cancelled expectation must not move the selection.")
                .isEqualTo("USERS");
    }

    @Test
    void testReconcile_whenTheExpectedNewTableDidNotAppear_forgetsItForTheNextReconcile()
    {
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/></dataset>"));
        tabs.expectNewTableSelected("ORDERS");
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/></dataset>"));

        tabs.reconcile(show(USERS_AND_ORDERS));

        assertThat(tabFolder.getSelection().getText()).as("An expectation must last for one reconcile.")
                .isEqualTo("USERS");
    }

    @Test
    void testReconcile_whenATableHasSeveralRowsAndColumns_countsThemInTheTooltip()
    {
        final DatasetModel model = show("<dataset><USERS ID=\"1\" NAME=\"a\" AGE=\"3\"/>"
                + "<USERS ID=\"2\" NAME=\"b\" AGE=\"4\"/></dataset>");

        tabs.reconcile(model);

        assertThat(tabFolder.getItem(0).getToolTipText()).as("The tooltip must count rows and columns.")
                .isEqualTo("2 rows, 3 columns");
    }

    @Test
    void testReconcile_whenATableHasOneRowAndOneColumn_countsThemInTheSingularInTheTooltip()
    {
        final DatasetModel model = show("<dataset><USERS ID=\"1\"/></dataset>");

        tabs.reconcile(model);

        assertThat(tabFolder.getItem(0).getToolTipText()).as("The tooltip must use the singular.")
                .isEqualTo("1 row, 1 column");
    }

    @Test
    void testReconcile_whenATableHasNoRows_showsItsTabInItalics()
    {
        final DatasetModel model = show("<dataset><USERS ID=\"1\"/><EMPTY/></dataset>");

        tabs.reconcile(model);

        assertThat(isItalic(tabFolder.getItem(0).getFont())).as("A table with rows must not be italic.")
                .isFalse();
        assertThat(isItalic(tabFolder.getItem(1).getFont())).as("A table without rows must be italic.")
                .isTrue();
    }

    @Test
    void testReconcile_whenATableHasAnErrorAndAWarning_showsTheErrorImageOnlyOnThatTab()
    {
        final DatasetModel parsed = show(USERS_AND_ORDERS);
        final DatasetModel model = withProblems(parsed, problem(ProblemSeverity.WARNING, "USERS"),
                problem(ProblemSeverity.ERROR, "USERS"));

        tabs.reconcile(model);

        assertThat(tabFolder.getItem(0).getImage()).as("An error must win over a warning.")
                .isSameAs(sharedImage(ISharedImages.IMG_OBJS_ERROR_TSK));
        assertThat(tabFolder.getItem(1).getImage()).as("A table without problems must show no image.")
                .isNull();
    }

    @Test
    void testReconcile_whenATableHasOnlyAWarning_showsTheWarningImage()
    {
        final DatasetModel parsed = show(USERS_AND_ORDERS);
        final DatasetModel model = withProblems(parsed, problem(ProblemSeverity.WARNING, "ORDERS"));

        tabs.reconcile(model);

        assertThat(tabFolder.getItem(1).getImage()).as("A warning must show the warning image.")
                .isSameAs(sharedImage(ISharedImages.IMG_OBJS_WARN_TSK));
    }

    @Test
    void testReconcile_whenATableHasOnlyAnInformation_showsNoImage()
    {
        final DatasetModel parsed = show(USERS_AND_ORDERS);
        final DatasetModel model = withProblems(parsed, problem(ProblemSeverity.INFO, "USERS"));

        tabs.reconcile(model);

        assertThat(tabFolder.getItem(0).getImage()).as("An information must not show an image.").isNull();
    }

    @Test
    void testReconcile_whenAProblemIsGone_removesTheImage()
    {
        final DatasetModel parsed = show(USERS_AND_ORDERS);
        tabs.reconcile(withProblems(parsed, problem(ProblemSeverity.ERROR, "USERS")));

        tabs.reconcile(parsed);

        assertThat(tabFolder.getItem(0).getImage()).as("A table without problems must show no image.")
                .isNull();
    }

    @Test
    void testSelectCell_whenTheTableHasATab_selectsTheTabAndTheCellAndTellsTheListener()
    {
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/><ORDERS ID=\"1\" NAME=\"a\"/>"
                + "<ORDERS ID=\"2\" NAME=\"b\"/></dataset>"));
        selectionChanges.set(0);

        tabs.selectCell(new CellAddress("ORDERS", 1, 0));

        assertThat(tabFolder.getSelection().getText()).as("The table's tab must be selected.")
                .isEqualTo("ORDERS");
        assertThat(anchorOf(tabs.activeGrid().getSelection())).as("The cell must be selected.")
                .isEqualTo(new CellAddress("ORDERS", 1, 0));
        assertThat(selectionChanges.get()).as("The listener must be told about the selection.").isPositive();
    }

    @Test
    void testSelectCell_whenTheColumnIsUnknown_selectsOnlyTheTab()
    {
        tabs.reconcile(show(USERS_AND_ORDERS));

        tabs.selectCell(new CellAddress("ORDERS", 0, -1));

        assertThat(tabFolder.getSelection().getText()).as("The table's tab must be selected.")
                .isEqualTo("ORDERS");
        assertThat(anchorOf(tabs.activeGrid().getSelection())).as("The grid keeps the cell it selected first.")
                .isEqualTo(new CellAddress("ORDERS", 0, 0));
    }

    @Test
    void testSelectCell_whenTheTableHasNoTab_changesNothing()
    {
        tabs.reconcile(show(USERS_AND_ORDERS));
        selectionChanges.set(0);

        tabs.selectCell(new CellAddress("MISSING", 0, 0));

        assertThat(tabFolder.getSelection().getText()).as("The selected tab must stay.").isEqualTo("USERS");
        assertThat(selectionChanges.get()).as("The listener must not be told.").isZero();
    }

    @Test
    void testReconcile_whenACellIsSelectedInAGrid_tellsTheListener()
    {
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>"));
        selectionChanges.set(0);
        final NatTable natTable = (NatTable) tabFolder.getSelection().getControl();

        natTable.doCommand(new SelectCellCommand(natTable, 1, 2, false, false));

        assertThat(selectionChanges.get()).as("The listener must be told about the selection.").isPositive();
    }

    @Test
    void testActiveGrid_whenNoTabExists_isNull()
    {
        assertThat(tabs.activeGrid()).as("Without tabs there is no active grid.").isNull();
    }

    @Test
    void testActiveGrid_whenTabsExist_isTheGridOfTheSelectedTab()
    {
        tabs.reconcile(show(USERS_AND_ORDERS));
        final Control usersGrid = tabFolder.getItem(0).getControl();
        final Control ordersGrid = tabFolder.getItem(1).getControl();

        final Control firstActive = tabs.activeGrid().getControl();
        tabFolder.setSelection(1);
        final Control secondActive = tabs.activeGrid().getControl();

        assertThat(List.of(firstActive, secondActive)).as("The active grid must follow the selected tab.")
                .containsExactly(usersGrid, ordersGrid);
    }

    @Test
    void testCommitActiveCellEditor_whileACellIsBeingEdited_writesItsValueToTheDocument()
    {
        tabs.reconcile(show("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>"));
        shell.layout(true, true);
        UiTestWorkspace.processEvents();
        final NatTable natTable = (NatTable) tabFolder.getSelection().getControl();
        natTable.doCommand(new SelectCellCommand(natTable, 1, 2, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        natTable.getActiveCellEditor().setEditorValue("20");

        tabs.commitActiveCellEditor();

        assertThat(document.get()).as("The open editor's value must reach the document.")
                .isEqualTo("<dataset><USERS ID=\"1\"/><USERS ID=\"20\"/></dataset>");
    }

    private DatasetModel show(final String content)
    {
        document.set(content);
        datasetDocument.refresh();
        return datasetDocument.getModel();
    }

    private List<String> tabTexts()
    {
        final List<String> texts = new ArrayList<>();
        for (final CTabItem item : tabFolder.getItems())
        {
            texts.add(item.getText());
        }
        return texts;
    }

    private static CellAddress anchorOf(final GridSelection selection)
    {
        return new CellAddress(selection.tableKey(), selection.anchorRowIndex(), selection.anchorColumnIndex());
    }

    private static boolean isItalic(final Font font)
    {
        return (font.getFontData()[0].getStyle() & SWT.ITALIC) != 0;
    }

    private static Image sharedImage(final String key)
    {
        return PlatformUI.getWorkbench().getSharedImages().getImage(key);
    }

    private static DatasetProblem problem(final ProblemSeverity severity, final String tableKey)
    {
        return new DatasetProblem(ProblemCode.REDUNDANT_EMPTY_ELEMENT, severity, "A problem.", tableKey, null, 0,
                0, 0);
    }

    private static DatasetModel withProblems(final DatasetModel model, final DatasetProblem... problems)
    {
        return new DatasetModel(model.getTables(), List.of(problems), model.isEditable());
    }
}
