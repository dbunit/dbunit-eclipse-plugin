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
package org.dbunit.eclipse.dataset.ui.actions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.edit.ChangeOrigin;
import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.dialogs.AddTableDialog;
import org.dbunit.eclipse.dataset.ui.dialogs.DatasetNameValidator;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@code GridAction} subclasses against the Commands and Clipboard specifications' selection,
 * enablement, undo, and active-cell-editor rules.
 */
class GridActionsTest
{
    private Shell shell;

    @BeforeEach
    void createShell()
    {
        shell = new Shell(Display.getDefault());
    }

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testInsertRowBelow_withAnchorInTheSecondRow_insertsAtIndex2AndSelectsTheNewRow()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 1;
        final InsertRowBelowAction action = new InsertRowBelowAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                .as("Insert Row Below must add one row.").hasSize(4);
        assertThat(context.selectedRegion)
                .as("Insert Row Below must select the new row at index 2, in the anchor column.")
                .isEqualTo(new Rectangle(0, 2, 1, 1));
    }

    @Test
    void testDeleteRows_withTheSecondAndThirdRowsSelected_deletesBothInOneUndoStep()
            throws ExecutionException
    {
        final IDocument document = new Document(
                "<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/><USERS ID=\"4\"/></dataset>");
        DocumentUndoManagerRegistry.connect(document);
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(this);
        try
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final TestContext context = new TestContext(datasetDocument, "USERS");
            context.rowIndexes = List.of(1, 2);
            final DeleteRowsAction action = new DeleteRowsAction(context);

            action.run();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .as("Deleting the selected rows must remove both.").hasSize(2);
            assertThat(context.multiCellEditTitles)
                    .as("Delete Rows must report a rejected edit through a modal dialog, not just the "
                            + "status line.")
                    .containsExactly("Delete Rows");

            undoManager.undo();
            datasetDocument.refresh();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .as("Deleting two rows must be one undo step.").hasSize(4);
        }
        finally
        {
            undoManager.disconnect(this);
            DocumentUndoManagerRegistry.disconnect(document);
        }
    }

    @Test
    void testUpdate_reflectsWhetherThePageIsEditable()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final InsertRowAboveAction action = new InsertRowAboveAction(context);

        action.update(context.getSelection());
        assertThat(action.isEnabled())
                .as("An editable page with a column must enable Insert Row Above.").isTrue();

        context.editable = false;
        action.update(context.getSelection());
        assertThat(action.isEnabled()).as("A read-only page must disable the action.").isFalse();
    }

    @Test
    void testRun_whileACellEditorIsActive_changesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.hasActiveCellEditor = true;
        final InsertRowBelowAction action = new InsertRowBelowAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                .as("Insert Row Below must do nothing while a cell editor is active.").hasSize(1);
    }

    @Test
    void testDuplicateRows_withTwoRowsSelected_insertsCopiesDirectlyAfterThemAndSelectsTheNewBlock()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0);
        final DuplicateRowsAction action = new DuplicateRowsAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).as("Duplicating two rows must add two rows.").hasSize(5);
        assertThat(table.getRows().get(2).getValue(0)).as("The duplicate block must copy the first row.")
                .isEqualTo("1");
        assertThat(table.getRows().get(3).getValue(0))
                .as("The duplicate block must copy the second row, in order.").isEqualTo("2");
        assertThat(context.selectedRegion).as("Duplicate Rows must select the new block.")
                .isEqualTo(new Rectangle(0, 2, 1, 2));
    }

    @Test
    void testMoveRowsUp_withAContiguousBlockSelected_movesItUpAndKeepsItSelected()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1, 2);
        context.columnIndexes = List.of(0);
        final MoveRowsUpAction action = new MoveRowsUpAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(0).getValue(0)).as("Moving the block up must place it first.")
                .isEqualTo("2");
        assertThat(table.getRows().get(1).getValue(0)).isEqualTo("3");
        assertThat(table.getRows().get(2).getValue(0))
                .as("The row the block moved past must follow it.").isEqualTo("1");
        assertThat(context.selectedRegion).as("Move Rows Up must keep the moved block selected.")
                .isEqualTo(new Rectangle(0, 0, 1, 2));
    }

    @Test
    void testMoveRowsDown_withAContiguousBlockSelected_movesItDownAndKeepsItSelected()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0);
        final MoveRowsDownAction action = new MoveRowsDownAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(0).getValue(0))
                .as("The row the block moved past must precede it.").isEqualTo("3");
        assertThat(table.getRows().get(1).getValue(0)).as("Moving the block down must place it after.")
                .isEqualTo("1");
        assertThat(table.getRows().get(2).getValue(0)).isEqualTo("2");
        assertThat(context.selectedRegion).as("Move Rows Down must keep the moved block selected.")
                .isEqualTo(new Rectangle(0, 1, 1, 2));
    }

    @Test
    void testUpdate_forMoveRowsUpAndDown_disabledWhenNotContiguousOrAtTheEdge()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final MoveRowsUpAction up = new MoveRowsUpAction(context);
        final MoveRowsDownAction down = new MoveRowsDownAction(context);

        context.rowIndexes = List.of(0, 2);
        up.update(context.getSelection());
        down.update(context.getSelection());
        assertThat(up.isEnabled()).as("A non-contiguous selection must disable Move Rows Up.").isFalse();
        assertThat(down.isEnabled()).as("A non-contiguous selection must disable Move Rows Down.")
                .isFalse();

        context.rowIndexes = List.of(0, 1);
        up.update(context.getSelection());
        down.update(context.getSelection());
        assertThat(up.isEnabled()).as("A block already at the top must disable Move Rows Up.").isFalse();
        assertThat(down.isEnabled()).as("A block not at the bottom must enable Move Rows Down.").isTrue();

        context.rowIndexes = List.of(1, 2);
        up.update(context.getSelection());
        down.update(context.getSelection());
        assertThat(up.isEnabled()).as("A block not at the top must enable Move Rows Up.").isTrue();
        assertThat(down.isEnabled()).as("A block already at the bottom must disable Move Rows Down.")
                .isFalse();
    }

    @Test
    void testAddColumn_whenTheDialogReturnsAName_addsThePendingColumn()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final AddColumnAction action = new AddColumnAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final IInputValidator validator,
                    final DatasetTable table)
            {
                return "EMAIL";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                .extracting(DatasetColumn::name).as("Add Column must add the entered name.")
                .contains("EMAIL");
    }

    @Test
    void testAddColumnNameDialogMessage_whenTheTableHasNoDtdDeclaredColumns_isJustThePrompt()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(AddColumnAction.nameDialogMessage(table))
                .as("A table with no DTD-declared columns must get a plain prompt.")
                .isEqualTo("Column name:");
    }

    @Test
    void testAddColumnNameDialogMessage_whenTheTableHasDtdDeclaredColumns_warnsThatDbUnitReadsTheDtd()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n    <USERS ID=\"1\"/>\n"
                        + "</dataset>\n");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(AddColumnAction.nameDialogMessage(table))
                .as("A table with DTD-declared columns must warn that dbUnit reads columns from the DTD.")
                .contains("dbUnit reads a flat XML dataset's columns from its DTD");
    }

    @Test
    void testRenameColumn_whenTheDialogReturnsANewName_renamesTheAnchorColumn()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        final RenameColumnAction action = new RenameColumnAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator)
            {
                return "USER_ID";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns().get(0).name())
                .as("Rename Column must apply the entered name.").isEqualTo("USER_ID");
    }

    @Test
    void testDeleteColumn_whenConfirmed_deletesTheAnchorColumnNamedWithItsValueCount()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 1;
        final DeleteColumnAction action = new DeleteColumnAction(context)
        {
            @Override
            boolean confirmDelete(final Shell shell, final DatasetColumn column, final int valueCount)
            {
                assertThat(column.name()).as("The confirmation must name the anchor column.")
                        .isEqualTo("NAME");
                assertThat(valueCount).as("The confirmation must count the column's current values.")
                        .isEqualTo(1);
                return true;
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                .extracting(DatasetColumn::name).as("Confirmed deletion must remove the column.")
                .doesNotContain("NAME");
    }

    @Test
    void testDeleteColumn_whenNotConfirmed_changesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 1;
        final DeleteColumnAction action = new DeleteColumnAction(context)
        {
            @Override
            boolean confirmDelete(final Shell shell, final DatasetColumn column, final int valueCount)
            {
                return false;
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                .extracting(DatasetColumn::name).as("Declining the confirmation must change nothing.")
                .contains("NAME");
    }

    @Test
    void testAddTable_whenTheDialogReturnsANameAndColumns_addsTheTableAndSelectsItsTab()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final AddTableAction action = new AddTableAction(context)
        {
            @Override
            AddTableDialog openDialog(final Shell shell, final DatasetNameValidator nameValidator)
            {
                return new AddTableDialog(shell, nameValidator)
                {
                    @Override
                    public String getTableName()
                    {
                        return "Accounts";
                    }

                    @Override
                    public List<String> getColumnNames()
                    {
                        return List.of("ID", "BALANCE");
                    }
                };
            }
        };

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("ACCOUNTS").orElseThrow();
        assertThat(table.getColumns()).extracting(DatasetColumn::name)
                .as("Add Table must create the table with the entered columns.")
                .containsExactly("ID", "BALANCE");
        assertThat(context.expectedNewTableKey)
                .as("Add Table must select the new table's tab by its case-folded key.")
                .isEqualTo("ACCOUNTS");
    }

    @Test
    void testRenameTable_whenTheDialogReturnsANewName_renamesTheTableAndKeepsItsTab()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final RenameTableAction action = new RenameTableAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator)
            {
                return "Customers";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("CUSTOMERS")).as("Rename Table must apply the entered name.")
                .isPresent();
        assertThat(context.expectedRenameOldKey).as("Rename Table must record the old tab key.")
                .isEqualTo("USERS");
        assertThat(context.expectedRenameNewKey)
                .as("Rename Table must record the new tab's case-folded key.").isEqualTo("CUSTOMERS");
    }

    @Test
    void testDeleteTable_whenConfirmed_removesTheTable()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\"/><ACCOUNTS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final DeleteTableAction action = new DeleteTableAction(context)
        {
            @Override
            boolean confirmDelete(final Shell shell, final DatasetTable table)
            {
                assertThat(table.getName()).as("The confirmation must name the active table.")
                        .isEqualTo("USERS");
                return true;
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS"))
                .as("Confirmed deletion must remove the table.").isEmpty();
        assertThat(datasetDocument.getModel().findTable("ACCOUNTS"))
                .as("Deleting one table must not affect the others.").isPresent();
    }

    @Test
    void testDeleteTable_whenNotConfirmed_changesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final DeleteTableAction action = new DeleteTableAction(context)
        {
            @Override
            boolean confirmDelete(final Shell shell, final DatasetTable table)
            {
                return false;
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS"))
                .as("Declining the confirmation must change nothing.").isPresent();
    }

    @Test
    void testCopy_ofA2x2Range_putsTabSeparatedTextOnTheClipboard()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(1, 0), new Point(0, 1), new Point(1, 1));
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText).as("Copy must put the range as tab-separated text.")
                .isEqualTo("1\tAlice" + System.lineSeparator() + "2\tBob" + System.lineSeparator());
    }

    @Test
    void testCopy_withANonRectangularSelection_copiesUnselectedCellsInTheRectangleAsEmpty()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(1, 1));
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText)
                .as("A cell inside the bounding rectangle that is not selected must copy as empty.")
                .isEqualTo("1\t" + System.lineSeparator() + "\tBob" + System.lineSeparator());
    }

    @Test
    void testCopyThenPaste_ofAnEmptyStringCell_preservesItAsEmptyStringNotNull()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0));
        new SetEmptyStringAction(context).run();

        new CopyAction(context).run();
        context.rowIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 1));
        new PasteAction(context).run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(1).getValue(1))
                .as("Pasting a copied empty-string cell must set the empty string, not NULL.")
                .isEqualTo("");
    }

    @Test
    void testCopy_whileACellEditorIsActive_actsOnTheEditorsTextInsteadOfTheGrid()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.hasActiveCellEditor = true;
        final Text text = new Text(shell, SWT.NONE);
        text.setText("hello world");
        text.setSelection(0, 5);
        context.activeCellEditorText = text;
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(text.getSelectionText()).as("The editor's own selection is what Copy must act on.")
                .isEqualTo("hello");
        assertThat(context.clipboardText)
                .as("With an active cell editor, Copy must not use the grid-level clipboard write.")
                .isNull();
    }

    @Test
    void testUpdate_forCopy_staysEnabledWhileACellEditorIsActive()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0);
        context.hasActiveCellEditor = true;
        final CopyAction action = new CopyAction(context);

        action.update(context.getSelection());

        assertThat(action.isEnabled()).as("Copy must stay enabled while a cell editor is active.").isTrue();
    }

    @Test
    void testUpdate_onAReadOnlyPage_enablesCopyAndSelectAllButNotCut()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0);
        context.editable = false;
        final List<GridAction> actions =
                List.of(new CopyAction(context), new SelectAllAction(context), new CutAction(context));
        final List<Boolean> enabled = new ArrayList<>();
        for (final GridAction action : actions)
        {
            action.update(context.getSelection());
            enabled.add(action.isEnabled());
        }

        assertThat(enabled).as("A read-only page must still copy and select cells, but not cut them.")
                .containsExactly(true, true, false);
    }

    @Test
    void testCut_ofACellRange_copiesThenSetsTheSelectedCellsToNull()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0), new Point(1, 1));
        context.wholeRowsSelected = false;
        final CutAction action = new CutAction(context);

        action.run();

        assertThat(context.clipboardText).as("Cut must copy the selection before clearing it.")
                .isEqualTo("Alice" + System.lineSeparator() + "Bob" + System.lineSeparator());
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(0).getValue(1)).as("Cut must set the selected cells to NULL.")
                .isNull();
        assertThat(table.getRows().get(1).getValue(1)).isNull();
        assertThat(table.getRows().get(2).getValue(1)).as("Cut must not touch an unselected row.")
                .isEqualTo("Carol");
        assertThat(table.getRows().get(0).getValue(0)).as("Cut must not touch an unselected column.")
                .isEqualTo("1");
    }

    @Test
    void testCut_ofWholeRows_deletesThemInsteadOfSettingThemToNull()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(1, 0), new Point(0, 1), new Point(1, 1));
        context.wholeRowsSelected = true;
        final CutAction action = new CutAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).as("Cutting whole rows must delete them, not just clear them.")
                .hasSize(1);
        assertThat(table.getRows().get(0).getValue(0)).isEqualTo("3");
    }

    @Test
    void testDelete_setsTheSelectedCellsToNull()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0));
        final DeleteAction action = new DeleteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(0).getValue(1)).as("Delete must set the selected cells to NULL.")
                .isNull();
        assertThat(table.getRows().get(0).getValue(0)).isEqualTo("1");
        assertThat(table.getRows().get(1).getValue(1)).as("Delete must not touch an unselected row.")
                .isEqualTo("Bob");
    }

    @Test
    void testSelectAll_run_selectsEveryCellOfTheActiveGrid()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final SelectAllAction action = new SelectAllAction(context);

        action.run();

        assertThat(context.selectAllCalled).as("Select All must select every cell of the active grid.")
                .isTrue();
    }

    @Test
    void testSetEmptyString_setsTheSelectedCellsToTheEmptyString()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0));
        final SetEmptyStringAction action = new SetEmptyStringAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0).getValue(1))
                .as("Set to Empty String must store the empty string, not NULL.").isEqualTo("");
    }

    @Test
    void testEditCellInDialog_run_opensTheAnchorCellInADialogEditor()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;
        final EditCellInDialogAction action = new EditCellInDialogAction(context);

        action.run();

        assertThat(context.editCellInDialogCalled)
                .as("Edit Cell in Dialog must open the anchor cell's editor.").isTrue();
    }

    @Test
    void testShowInSource_run_selectsAndRevealsTheAnchorCellOnTheSourcePage()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;
        final ShowInSourceAction action = new ShowInSourceAction(context);

        action.run();

        assertThat(context.showInSourceCalled)
                .as("Show in Source must select and reveal the anchor cell's range.").isTrue();
    }

    @Test
    void testUpdate_forShowInSource_staysEnabledOnAReadOnlyPage()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;
        context.editable = false;
        final ShowInSourceAction action = new ShowInSourceAction(context);

        action.update(context.getSelection());

        assertThat(action.isEnabled()).as("A read-only page must still show a cell in the source.").isTrue();
    }

    @Test
    void testFillDown_withAMultiRowSelection_copiesTheTopRowIntoTheOtherSelectedRows()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1, 2);
        context.columnIndexes = List.of(1);
        final FillDownAction action = new FillDownAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(0).getValue(1)).isEqualTo("Alice");
        assertThat(table.getRows().get(1).getValue(1)).as("Fill Down must copy the top row's value.")
                .isEqualTo("Alice");
        assertThat(table.getRows().get(2).getValue(1)).isEqualTo("Alice");
        assertThat(table.getRows().get(0).getValue(0)).as("Fill Down must not touch an unselected column.")
                .isEqualTo("1");
        assertThat(context.multiCellEditTitles)
                .as("Fill Down must report a rejected edit through a modal dialog, not just the status "
                        + "line.")
                .containsExactly("Fill Down");
    }

    @Test
    void testFillDown_withAOneRowSelection_copiesFromTheRowAbove()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(1);
        final FillDownAction action = new FillDownAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(1).getValue(1))
                .as("With a one-row selection, Fill Down must copy from the row above.")
                .isEqualTo("Alice");
        assertThat(context.multiCellEditTitles).containsExactly("Fill Down");
    }

    @Test
    void testFillDown_whenTheEditWouldEmptyARow_reportsItThroughExecuteMultiCellEditAndChangesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS NAME=\"Bob\"/></dataset>");
        datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", null)));
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(1);
        final FillDownAction action = new FillDownAction(context);

        action.run();

        assertThat(context.multiCellEditTitles)
                .as("A rejected multi-cell edit must still go through executeMultiCellEdit, naming the "
                        + "command, so the page can show a modal dialog instead of just the status line.")
                .containsExactly("Fill Down");
        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(1).getValue(1))
                .as("A rejected edit must change nothing.").isEqualTo("Bob");
    }

    @Test
    void testImageDescriptor_ofEachActionWithAGeneratedIcon_isThatIcon()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");

        final List<ImageDescriptor> actionImages = List.of(
                new InsertRowAboveAction(context).getImageDescriptor(),
                new InsertRowBelowAction(context).getImageDescriptor(),
                new DuplicateRowsAction(context).getImageDescriptor(),
                new DeleteRowsAction(context).getImageDescriptor(),
                new AddColumnAction(context).getImageDescriptor(),
                new DeleteColumnAction(context).getImageDescriptor(),
                new AddTableAction(context).getImageDescriptor(),
                new SetNullAction(context).getImageDescriptor(),
                new FillDownAction(context).getImageDescriptor());

        assertThat(actionImages).as("Every command with a generated icon must show it in toolbars and menus.")
                .containsExactly(DatasetImages.getImageDescriptor(DatasetImages.IMG_INSERT_ROW_ABOVE),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_INSERT_ROW_BELOW),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_DUPLICATE_ROWS),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_DELETE_ROWS),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_ADD_COLUMN),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_DELETE_COLUMN),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_ADD_TABLE),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_SET_NULL),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_FILL_DOWN));
    }

    @Test
    void testUpdate_forFillDown_disabledForASingleTopRowSelection()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final FillDownAction action = new FillDownAction(context);

        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        action.update(context.getSelection());
        assertThat(action.isEnabled())
                .as("A single-row selection at the top must disable Fill Down: no row above it.")
                .isFalse();

        context.rowIndexes = List.of(1);
        action.update(context.getSelection());
        assertThat(action.isEnabled())
                .as("A single-row selection with a row above it must enable Fill Down.").isTrue();

        context.rowIndexes = List.of(0, 1);
        action.update(context.getSelection());
        assertThat(action.isEnabled()).as("A multi-row selection must enable Fill Down.").isTrue();
    }

    @Test
    void testPaste_ofABlockAtTheLastRow_appendsOneRowInOneUndoStep() throws ExecutionException
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        DocumentUndoManagerRegistry.connect(document);
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(this);
        try
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final TestContext context = new TestContext(datasetDocument, "USERS");
            context.rowIndexes = List.of(0);
            context.columnIndexes = List.of(0, 1);
            context.clipboardText = "10\tX\n20\tY\n30\tZ\n";
            final PasteAction action = new PasteAction(context);
            final List<ChangeOrigin> modelRebuilds = new ArrayList<>();
            datasetDocument.addModelListener(event -> modelRebuilds.add(event.origin()));

            action.run();

            assertThat(modelRebuilds)
                    .as("A paste that changes a row and appends rows must rebuild the model once.")
                    .containsExactly(ChangeOrigin.EDIT);
            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getRows()).as("A 3-row paste at the last row must append two rows.")
                    .hasSize(3);
            assertThat(table.getRows().get(0).getValue(0)).isEqualTo("10");
            assertThat(table.getRows().get(0).getValue(1)).isEqualTo("X");
            assertThat(table.getRows().get(1).getValue(0)).isEqualTo("20");
            assertThat(table.getRows().get(1).getValue(1)).isEqualTo("Y");
            assertThat(table.getRows().get(2).getValue(0)).isEqualTo("30");
            assertThat(table.getRows().get(2).getValue(1)).isEqualTo("Z");
            assertThat(context.selectedRegion).as("Paste must select the pasted block.")
                    .isEqualTo(new Rectangle(0, 0, 2, 3));

            undoManager.undo();
            datasetDocument.refresh();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .as("The existing-row change and the appended rows must be one undo step.").hasSize(1);
            assertThat(context.multiCellEditTitles)
                    .as("Paste must report a rejected edit through a modal dialog, not just the status "
                            + "line.")
                    .containsExactly("Paste");
        }
        finally
        {
            undoManager.disconnect(this);
            DocumentUndoManagerRegistry.disconnect(document);
        }
    }

    @Test
    void testPaste_ofASingleValueOntoAMultiCellSelection_fillsEverySelectedCell()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0), new Point(1, 1));
        context.clipboardText = "X";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(0).getValue(1))
                .as("A single pasted value onto a multi-cell selection must fill every selected cell.")
                .isEqualTo("X");
        assertThat(table.getRows().get(1).getValue(1)).isEqualTo("X");
        assertThat(context.multiCellEditTitles).containsExactly("Paste");
    }

    @Test
    void testPaste_withMorePastedColumnsThanFit_ignoresThemWithAStatusMessage()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0);
        context.clipboardText = "10\tX\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0).getValue(0))
                .as("A column that fits must still be pasted.").isEqualTo("10");
        assertThat(context.statusMessage)
                .as("A pasted column beyond the table's last column must be ignored with a status message.")
                .isEqualTo("Ignored 1 pasted column beyond the table's last column.");
    }

    @Test
    void testPaste_forAnEmptyTable_isEnabledOnlyWithColumnsAndClipboardText()
    {
        final FlatXmlDatasetDocument withColumns = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
        final TestContext withColumnsContext = new TestContext(withColumns, "USERS");
        final PasteAction withColumnsAction = new PasteAction(withColumnsContext);

        withColumnsAction.update(withColumnsContext.getSelection());
        assertThat(withColumnsAction.isEnabled())
                .as("An empty table with columns must stay disabled for Paste until clipboard text is "
                        + "available.")
                .isFalse();

        withColumnsContext.clipboardText = "1\n";
        withColumnsAction.update(withColumnsContext.getSelection());
        assertThat(withColumnsAction.isEnabled())
                .as("An empty table with columns must enable Paste once clipboard text is available.")
                .isTrue();

        final FlatXmlDatasetDocument withoutColumns = create("<dataset><NONE/></dataset>");
        final TestContext withoutColumnsContext = new TestContext(withoutColumns, "NONE");
        withoutColumnsContext.clipboardText = "1\n";
        final PasteAction withoutColumnsAction = new PasteAction(withoutColumnsContext);

        withoutColumnsAction.update(withoutColumnsContext.getSelection());
        assertThat(withoutColumnsAction.isEnabled())
                .as("A table without columns must stay disabled for Paste even with clipboard text.")
                .isFalse();
    }

    @Test
    void testPaste_of2x2BlockIntoAnEmpty3ColumnTable_appendsTwoRowsFromColumnZeroInOneUndoStep()
            throws ExecutionException
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED CITY CDATA #IMPLIED>\n]>\n"
                        + "<dataset>\n</dataset>\n");
        DocumentUndoManagerRegistry.connect(document);
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(this);
        try
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final TestContext context = new TestContext(datasetDocument, "USERS");
            context.clipboardText = "1\tAlice\n2\tBob\n";
            final PasteAction action = new PasteAction(context);
            final List<ChangeOrigin> modelRebuilds = new ArrayList<>();
            datasetDocument.addModelListener(event -> modelRebuilds.add(event.origin()));

            action.run();

            assertThat(modelRebuilds).as("Pasting into an empty table must rebuild the model once.")
                    .containsExactly(ChangeOrigin.EDIT);
            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getRows()).as("The paste must append two rows.").hasSize(2);
            assertThat(table.getRows().get(0).getValue(0)).isEqualTo("1");
            assertThat(table.getRows().get(0).getValue(1)).isEqualTo("Alice");
            assertThat(table.getRows().get(1).getValue(0)).isEqualTo("2");
            assertThat(table.getRows().get(1).getValue(1)).isEqualTo("Bob");
            assertThat(context.selectedRegion).as("Paste must select the pasted block.")
                    .isEqualTo(new Rectangle(0, 0, 2, 2));

            undoManager.undo();
            datasetDocument.refresh();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .as("Appending the pasted rows must be one undo step.").isEmpty();
        }
        finally
        {
            undoManager.disconnect(this);
            DocumentUndoManagerRegistry.disconnect(document);
        }
    }

    private static FlatXmlDatasetDocument create(final String content)
    {
        return create(new Document(content));
    }

    private static FlatXmlDatasetDocument create(final IDocument document)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }

    private final class TestContext implements DatasetGridContext
    {
        private final DatasetDocument datasetDocument;

        private final String tableKey;

        private boolean editable = true;

        private boolean hasActiveCellEditor;

        private final List<String> multiCellEditTitles = new ArrayList<>();

        private int anchorColumnIndex = -1;

        private int anchorRowIndex = -1;

        private List<Integer> rowIndexes = List.of();

        private List<Integer> columnIndexes = List.of();

        private boolean wholeRowsSelected;

        private List<Point> selectedCellPositions = List.of();

        private Text activeCellEditorText;

        private boolean selectAllCalled;

        private boolean editCellInDialogCalled;

        private boolean showInSourceCalled;

        private String statusMessage;

        private String clipboardText;

        private Rectangle selectedRegion;

        private String expectedRenameOldKey;

        private String expectedRenameNewKey;

        private String expectedNewTableKey;

        TestContext(final DatasetDocument datasetDocument, final String tableKey)
        {
            this.datasetDocument = datasetDocument;
            this.tableKey = tableKey;
        }

        @Override
        public DatasetDocument getDatasetDocument()
        {
            return datasetDocument;
        }

        @Override
        public boolean isEditable()
        {
            return editable;
        }

        @Override
        public String getNullDisplayText()
        {
            return "(null)";
        }

        @Override
        public boolean isDarkTheme()
        {
            return false;
        }

        @Override
        public boolean executeEdit(final Runnable edit)
        {
            if (!editable)
            {
                return false;
            }
            try
            {
                edit.run();
                return true;
            }
            catch (final DatasetEditException e)
            {
                return false;
            }
        }

        @Override
        public boolean executeMultiCellEdit(final String title, final Runnable edit)
        {
            multiCellEditTitles.add(title);
            return executeEdit(edit);
        }

        @Override
        public void fillContextMenu(final IMenuManager menu, final String region)
        {
        }

        @Override
        public boolean hasActiveCellEditor()
        {
            return hasActiveCellEditor;
        }

        @Override
        public GridSelection getSelection()
        {
            final DatasetTable table = datasetDocument.getModel().findTable(tableKey).orElseThrow();
            final int rowCount = table.getRows().size();
            final int columnCount = table.getColumns().size();
            final int firstRow = rowIndexes.isEmpty() ? -1 : rowIndexes.get(0);
            final int lastRow = rowIndexes.isEmpty() ? -1 : rowIndexes.get(rowIndexes.size() - 1);
            final int firstColumn = columnIndexes.isEmpty() ? -1 : columnIndexes.get(0);
            final int lastColumn =
                    columnIndexes.isEmpty() ? -1 : columnIndexes.get(columnIndexes.size() - 1);
            return new GridSelection(tableKey, rowCount, columnCount, anchorColumnIndex, anchorRowIndex,
                    rowIndexes, columnIndexes, firstRow, lastRow, firstColumn, lastColumn,
                    wholeRowsSelected);
        }

        @Override
        public void selectRegion(final int firstColumnIndex, final int firstRowIndex, final int columnCount,
                final int rowCount)
        {
            selectedRegion = new Rectangle(firstColumnIndex, firstRowIndex, columnCount, rowCount);
        }

        @Override
        public Shell getShell()
        {
            return shell;
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
        public Text getActiveCellEditorText()
        {
            return activeCellEditorText;
        }

        @Override
        public List<Point> getSelectedCellPositions()
        {
            return selectedCellPositions;
        }

        @Override
        public void selectAll()
        {
            selectAllCalled = true;
        }

        @Override
        public void editCellInDialog()
        {
            editCellInDialogCalled = true;
        }

        @Override
        public void showInSource()
        {
            showInSourceCalled = true;
        }

        @Override
        public void setStatusMessage(final String message)
        {
            statusMessage = message;
        }

        @Override
        public void writeClipboardText(final String text)
        {
            clipboardText = text;
        }

        @Override
        public String readClipboardText()
        {
            return clipboardText;
        }
    }
}
