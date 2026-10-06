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
import java.util.Optional;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.ChangeOrigin;
import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.dialogs.AddTableDialog;
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
    private static final String DEFAULTS_DATASET = "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
            + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #REQUIRED STATUS CDATA \"ACTIVE\">\n]>\n"
            + "<dataset><USERS ID=\"1\"/><USERS ID=\"2\" STATUS=\"x\"/></dataset>";

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
    void testInsertRowBelow_withAnchorInTheFirstOfThreeRows_putsTheRowSecond()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;

        new InsertRowBelowAction(context).run();

        assertThat(firstColumnValues(datasetDocument, "USERS"))
                .as("The anchor in the first row is an anchor like any other: the new row goes right below "
                        + "it.")
                .containsExactly("1", "", "2", "3");
    }

    @Test
    void testInsertRowBelow_withNoAnchor_appendsTheRowAfterTheLastRow()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final InsertRowBelowAction action = new InsertRowBelowAction(context);

        action.run();

        assertThat(firstColumnValues(datasetDocument, "USERS"))
                .as("With nothing to insert below, the new row must go after the last row, not before the "
                        + "first.")
                .containsExactly("1", "2", "3", "");
        assertThat(context.selectedRegion).as("The new, last row must be selected.")
                .isEqualTo(new Rectangle(0, 3, 1, 1));
    }

    @Test
    void testInsertRowAbove_withNoAnchor_putsTheRowBeforeTheFirstRow()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final InsertRowAboveAction action = new InsertRowAboveAction(context);

        action.run();

        assertThat(firstColumnValues(datasetDocument, "USERS"))
                .as("With nothing to insert above, the new row must go before the first row.")
                .containsExactly("", "1", "2", "3");
    }

    @Test
    void testInsertRowBelow_withAnchorInTheFirstRow_getsTheEmptyStringInItsFirstColumn()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;
        final InsertRowBelowAction action = new InsertRowBelowAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(1)
                .getValues())
                .as("A blank row must get the empty string in its first column, not null, so the "
                        + "insert does not hit the all-null-row rejection.")
                .containsExactly("", null);
    }

    @Test
    void testInsertRowBelow_withAnchorInTheFirstRow_showsNoStatusMessage()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;

        new InsertRowBelowAction(context).run();

        assertThat(context.statusMessage).as("A row after the first row needs no explanation.").isNull();
    }

    @Test
    void testInsertRowAbove_withAnchorInTheFirstRowWithoutDtd_getsEveryColumnOfTheOldFirstRow()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;
        final InsertRowAboveAction action = new InsertRowAboveAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0)
                .getValues())
                .as("The new first row needs every column of the old first row, or dbUnit would ignore "
                        + "the names of the other rows.")
                .containsExactly("", "");
        assertThat(context.statusMessage)
                .as("The user must be told why the new row has an empty string in every column.")
                .isEqualTo("The new row is the table's first row now, so it has an empty string in each "
                        + "column of the old first row: dbUnit takes a table's columns from its first row.");
        assertThat(context.selectedRegion).as("The new row must be selected, in the anchor column.")
                .isEqualTo(new Rectangle(0, 0, 1, 1));
    }

    @Test
    void testInsertRowAbove_withAnchorInTheSecondRow_getsTheEmptyStringInItsFirstColumnOnly()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 1;

        new InsertRowAboveAction(context).run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(1)
                .getValues())
                .as("A blank row must get the empty string in its first column, not null, so the "
                        + "insert does not hit the all-null-row rejection.")
                .containsExactly("", null);
        assertThat(context.statusMessage).as("A row after the first row needs no explanation.").isNull();
    }

    @Test
    void testInsertRowAbove_withAnchorInTheFirstRowOfATableWithADtd_getsTheEmptyStringInItsFirstColumnOnly()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
                        + "<!ATTLIST USERS ID CDATA #IMPLIED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;

        new InsertRowAboveAction(context).run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0)
                .getValues())
                .as("With a DTD, dbUnit takes the columns from the DTD, so the first column is enough.")
                .containsExactly("", null);
        assertThat(context.statusMessage).as("There is nothing to explain.").isNull();
    }

    @Test
    void testSetNull_whenTheFirstRowWouldLoseAColumnThatAnotherRowHas_changesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0));

        new SetNullAction(context).run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0).getValue(1))
                .as("dbUnit would ignore Bob's name, so the first row's name must stay.")
                .isEqualTo("Alice");
    }

    @Test
    void testDeleteRows_whenTheNextRowLacksAColumnThatAnotherRowHas_changesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x\"/>"
                        + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carl\" EMAIL=\"c@x\"/>"
                        + "</dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);

        new DeleteRowsAction(context).run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                .as("Bob would become the first row without an email, which dbUnit would ignore in Carl.")
                .hasSize(3);
    }

    @Test
    void testMoveRowsUp_whenTheRowMovedToTheTopLacksAColumnThatAnotherRowHas_changesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x\"/>"
                        + "<USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);

        new MoveRowsUpAction(context).run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0).getValue(1))
                .as("Alice must stay the first row, because Bob lacks the email that she has.")
                .isEqualTo("Alice");
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
    void testRun_whenTheTextChangedAndNothingRefreshedTheDocument_refreshesItBeforeTheCommandRuns()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final List<Boolean> staleWhenTheCommandRan = new ArrayList<>();
        final GridAction action = new GridAction(context)
        {
            @Override
            protected void runOnGrid(final DatasetGridContext gridContext)
            {
                staleWhenTheCommandRan.add(datasetDocument.isStale());
            }

            @Override
            protected boolean isEnabledFor(final GridSelection selection)
            {
                return true;
            }
        };
        document.replace(document.get().indexOf("<USERS"), 0, "<AUDIT_LOG/>");

        action.run();

        assertThat(staleWhenTheCommandRan)
                .as("A command that is queued behind a change of the text must run on the model of the "
                        + "text as it is now.")
                .containsExactly(false);
    }

    @Test
    void testRun_whileACellEditorIsActiveAndTheTextChanged_doesNotRefreshTheDocument() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.hasActiveCellEditor = true;
        final Text text = new Text(shell, SWT.NONE);
        text.setText("hello world");
        text.setSelection(0, 5);
        context.activeCellEditorText = text;
        document.replace(document.get().indexOf("<USERS"), 0, "<AUDIT_LOG/>");

        new CopyAction(context).run();

        assertThat(datasetDocument.isStale())
                .as("A refresh rebuilds the grids and can close the editor, so a command that works on "
                        + "the editor's text must leave it to the page.")
                .isTrue();
    }

    @Test
    void testRun_whenRunOnGridThrowsADatasetEditException_reportsItAsAnErrorMessageInsteadOfPropagating()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final GridAction action = new GridAction(context)
        {
            @Override
            protected void runOnGrid(final DatasetGridContext context)
            {
                throw new DatasetEditException("The selection is out of date.");
            }

            @Override
            protected boolean isEnabledFor(final GridSelection selection)
            {
                return true;
            }
        };

        action.run();

        assertThat(context.statusErrorMessage)
                .as("A DatasetEditException thrown directly by runOnGrid must be reported as the status "
                        + "line's error message, like a rejected edit, instead of propagating.")
                .isEqualTo("The selection is out of date.");
        assertThat(context.statusMessage)
                .as("The rejection must not be reported as a normal message, which an earlier error "
                        + "message would hide.")
                .isNull();
    }

    @Test
    void testRun_whenTheSelectionNamesATableThatNoLongerExists_reportsItAsAnErrorMessage()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.staleSelection =
                new GridSelection("GONE", 1, 1, 0, 0, List.of(0), List.of(0), false);
        final AddColumnAction action = new AddColumnAction(context);

        action.run();

        assertThat(context.statusErrorMessage)
                .as("A selection that names a table missing from the model must be reported as the "
                        + "status line's error message.")
                .isEqualTo("There is no table 'GONE'.");
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
    void testRenameColumnNameDialogMessage_whenTheColumnIsNotDeclared_isJustThePrompt()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetColumn column =
                datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns().get(0);

        assertThat(RenameColumnAction.nameDialogMessage(column))
                .as("A column that is not DTD-declared must get a plain prompt.")
                .isEqualTo("Column name:");
    }

    @Test
    void testRenameColumnNameDialogMessage_whenTheColumnIsDeclared_warnsThatDbUnitReadsTheDtd()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n    <USERS ID=\"1\"/>\n"
                        + "</dataset>\n");
        final DatasetColumn column =
                datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns().get(0);

        assertThat(RenameColumnAction.nameDialogMessage(column))
                .as("A DTD-declared column must warn that dbUnit reads columns from the DTD.")
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
                    final IInputValidator validator, final DatasetColumn column)
            {
                return "USER_ID";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns().get(0).name())
                .as("Rename Column must apply the entered name.").isEqualTo("USER_ID");
    }

    @Test
    void testRenameColumn_whenAnotherColumnIsEqualIgnoringCaseYetDistinct_countsItsNameAsUsed()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" \u0130D=\"2\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 1;
        final List<IInputValidator> validators = new ArrayList<>();
        final RenameColumnAction action = new RenameColumnAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetColumn column)
            {
                validators.add(validator);
                return null;
            }
        };

        action.run();

        assertThat(validators).as("Rename Column must open its dialog.").hasSize(1);
        assertThat(validators.get(0).isValid("ID"))
                .as("The name of the other column must count as used, although it equals the renamed "
                        + "column's name ignoring case.")
                .isNotNull();
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
    void testAddTable_whenTheDialogReturnsANameAndColumns_addsTheTable()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final AddTableAction action = new AddTableAction(context)
        {
            @Override
            AddTableDialog openDialog(final Shell shell, final IInputValidator nameValidator)
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
    }

    @Test
    void testAddTable_whenTheEditIsRejected_createsNoTable()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.editable = false;
        final AddTableAction action = new AddTableAction(context)
        {
            @Override
            AddTableDialog openDialog(final Shell shell, final IInputValidator nameValidator)
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
                        return List.of();
                    }
                };
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("ACCOUNTS"))
                .as("A rejected edit must not create the table.").isEmpty();
    }

    @Test
    void testAddTable_whenTheEnteredNameIsTheReservedRootName_theDialogValidatorRejectsIt()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final List<IInputValidator> capturedValidator = new ArrayList<>();
        final AddTableAction action = new AddTableAction(context)
        {
            @Override
            AddTableDialog openDialog(final Shell shell, final IInputValidator nameValidator)
            {
                capturedValidator.add(nameValidator);
                return null;
            }
        };

        action.run();

        assertThat(capturedValidator.get(0).isValid("dataset"))
                .as("Add Table must reject the name reserved for the root element.").isNotNull();
    }

    @Test
    void testRenameTableNameDialogMessage_whenTheTableIsNotDeclaredOnly_isJustThePrompt()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(RenameTableAction.nameDialogMessage(table))
                .as("A table that is not declared-only must get a plain prompt.")
                .isEqualTo("Table name:");
    }

    @Test
    void testRenameTableNameDialogMessage_whenTheTableIsDeclaredOnly_warnsThatDbUnitReadsTheDtd()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(RenameTableAction.nameDialogMessage(table))
                .as("A declared-only table must warn that dbUnit reads tables from the DTD.")
                .contains("dbUnit reads a flat XML dataset's tables from its DTD");
    }

    @Test
    void testRenameTableNameDialogMessage_whenADtdFileDeclaresTheTable_warnsToRenameItThereToo()
    {
        final FlatXmlDatasetDocument datasetDocument = createWithDtdFile(
                "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><USERS ID=\"1\"/></dataset>",
                "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY><!ATTLIST USERS ID CDATA #REQUIRED>");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(RenameTableAction.nameDialogMessage(table))
                .as("The editor does not change a DTD file, so the dialog must say that the file needs the "
                        + "new name too.")
                .contains("a DTD file declares this table", "rename the table there too");
    }

    @Test
    void testRenameTableNameDialogMessage_whenOnlyTheInternalSubsetDeclaresTheTable_isJustThePrompt()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<!DOCTYPE dataset [\n"
                + "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset><USERS ID=\"1\"/></dataset>\n");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(RenameTableAction.nameDialogMessage(table))
                .as("The editor renames a table in the internal subset itself, so there is nothing to warn "
                        + "about.")
                .isEqualTo("Table name:");
    }

    @Test
    void testRenameTable_whenTheInternalSubsetDeclaresTheTable_theDtdStillDeclaresTheRenamedTable()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<!DOCTYPE dataset [\n"
                + "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final RenameTableAction action = new RenameTableAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetTable table)
            {
                return "Customers";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().getTables())
                .as("The renamed table must replace the old one, with no table left that only the DTD "
                        + "declares.")
                .extracting(DatasetTable::getName).containsExactly("Customers");
        assertThat(datasetDocument.getModel().getProblems())
                .as("The DTD must declare the renamed table, so dbUnit can load the dataset.").isEmpty();
    }

    @Test
    void testRenameTable_whenTheDialogReturnsANewName_renamesTheTable()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final RenameTableAction action = new RenameTableAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetTable table)
            {
                return "Customers";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("CUSTOMERS")).as("Rename Table must apply the entered name.")
                .isPresent();
    }

    @Test
    void testRenameTable_whenTheEditIsRejected_leavesTheTableAsItWas()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.editable = false;
        final RenameTableAction action = new RenameTableAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetTable table)
            {
                return "Customers";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS"))
                .as("A rejected edit must not rename the table.").isPresent();
    }

    @Test
    void testRenameTable_whenCaseSensitiveAndAnotherTableHasTheTargetNameExactly_theDialogValidatorRejectsIt()
    {
        final FlatXmlDatasetDocument datasetDocument =
                createCaseSensitive("<dataset><users ID=\"1\"/><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "users");
        final List<IInputValidator> capturedValidator = new ArrayList<>();
        final RenameTableAction action = new RenameTableAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetTable table)
            {
                capturedValidator.add(validator);
                return null;
            }
        };

        action.run();

        assertThat(capturedValidator.get(0).isValid("USERS"))
                .as("Renaming 'users' to 'USERS' must be rejected because a separate table already has "
                        + "that exact name, even though the validator excludes the table being renamed.")
                .isNotNull();
    }

    @Test
    void testDeleteTableConfirmMessage_whenTheTableIsNotDeclaredOnly_hasNoDtdNote()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(DeleteTableAction.confirmMessage(table))
                .as("A table that is not declared-only must not mention the DTD.")
                .doesNotContain("DTD");
    }

    @Test
    void testDeleteTableConfirmMessage_whenTheTableIsDeclaredOnly_warnsThatDbUnitReadsTheDtd()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(DeleteTableAction.confirmMessage(table))
                .as("A declared-only table's confirmation must warn that dbUnit reads tables from the "
                        + "DTD.")
                .contains("dbUnit reads tables from the DTD");
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
    void testCopy_ofNonAdjacentWholeRows_copiesOnlyTheSelectedRows()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 2);
        context.columnIndexes = List.of(0, 1);
        context.wholeRowsSelected = true;
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(1, 0), new Point(0, 2), new Point(1, 2));
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText)
                .as("The row between two rows selected with Ctrl is not selected, so it must not be "
                        + "copied as an empty row.")
                .isEqualTo("1\tAlice" + System.lineSeparator() + "3\tCarol" + System.lineSeparator());
        assertThat(context.statusErrorMessage).as("Rows that line up are copyable.").isNull();
    }

    @Test
    void testCopy_ofNonAdjacentColumns_copiesOnlyTheSelectedColumns()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset>"
                + "<USERS ID=\"1\" NAME=\"Alice\" CITY=\"Rome\"/><USERS ID=\"2\" NAME=\"Bob\" CITY=\"Paris\"/>"
                + "</dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 2);
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(2, 0), new Point(0, 1), new Point(2, 1));
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText)
                .as("The column between two columns selected with Ctrl is not selected, so it must not be "
                        + "copied as an empty column.")
                .isEqualTo("1\tRome" + System.lineSeparator() + "2\tParis" + System.lineSeparator());
    }

    @Test
    void testCopy_ofNonAdjacentCellsInOneColumn_copiesThemNextToEachOther()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 2);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0), new Point(1, 2));
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText)
                .as("Two cells of one column selected with Ctrl must copy as two adjacent cells.")
                .isEqualTo("Alice" + System.lineSeparator() + "Carol" + System.lineSeparator());
    }

    @Test
    void testCopy_whenTheSelectedRowsHaveDifferentSelectedColumns_refusesAndLeavesTheClipboardAlone()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(1, 1));
        context.clipboardText = "kept";
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText)
                .as("A selection with no sensible rectangle must copy nothing, not a rectangle with empty "
                        + "cells in the gaps, which pastes as NULL.")
                .isEqualTo("kept");
        assertThat(context.statusErrorMessage).as("Copy must say why it copied nothing.")
                .isEqualTo("Cannot copy or cut this selection, because its rows have different selected "
                        + "columns. Select the same columns in every selected row.");
    }

    @Test
    void testCut_whenTheSelectedRowsHaveDifferentSelectedColumns_refusesAndChangesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(1, 1));
        context.clipboardText = "kept";
        final CutAction action = new CutAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("Cut copies before it clears, so a selection that cannot be copied must clear "
                        + "nothing.")
                .containsExactly(List.of("1", "Alice"), List.of("2", "Bob"));
        assertThat(context.clipboardText).as("Cut must leave the clipboard alone.").isEqualTo("kept");
        assertThat(context.statusErrorMessage).as("Cut must say why it did nothing.")
                .startsWith("Cannot copy or cut this selection");
    }

    @Test
    void testCut_ofNonAdjacentWholeRows_copiesJustThoseRowsThenDeletesThem()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 2);
        context.columnIndexes = List.of(0, 1);
        context.wholeRowsSelected = true;
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(1, 0), new Point(0, 2), new Point(1, 2));
        final CutAction action = new CutAction(context);

        action.run();

        assertThat(context.clipboardText).as("Cut must copy the two rows without the row between them.")
                .isEqualTo("1\tAlice" + System.lineSeparator() + "3\tCarol" + System.lineSeparator());
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("Cut must delete the two selected rows and keep the row between them.")
                .containsExactly(List.of("2", "Bob"));
    }

    @Test
    void testCopyThenPaste_ofNonAdjacentRowsOntoTheLastRow_pastesThemOneAfterTheOther()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 2);
        context.columnIndexes = List.of(0, 1);
        context.wholeRowsSelected = true;
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(1, 0), new Point(0, 2), new Point(1, 2));
        new CopyAction(context).run();
        context.rowIndexes = List.of(2);
        context.columnIndexes = List.of(0);
        context.wholeRowsSelected = false;
        context.selectedCellPositions = List.of(new Point(0, 2));

        new PasteAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("The first copied row must overwrite the last row and the second must be appended, "
                        + "with no empty row between them.")
                .containsExactly(List.of("1", "Alice"), List.of("2", "Bob"), List.of("1", "Alice"),
                        List.of("3", "Carol"));
        assertThat(context.statusErrorMessage).as("Nothing may be rejected.").isNull();
    }

    @Test
    void testCopyThenPaste_ofNonAdjacentCellsOfOneColumn_writesNoNullBetweenTheTargets()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 2);
        context.columnIndexes = List.of(0);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(0, 2));
        new CopyAction(context).run();
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0));

        new PasteAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValue(1))
                .as("The two copied cells must go into the next two cells of the target column, and the "
                        + "third cell must stay as it was.")
                .containsExactly("1", "3", "Carol");
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
        context.rowIndexes = List.of(1, 2);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 1), new Point(1, 2));
        context.wholeRowsSelected = false;
        final CutAction action = new CutAction(context);

        action.run();

        assertThat(context.clipboardText).as("Cut must copy the selection before clearing it.")
                .isEqualTo("Bob" + System.lineSeparator() + "Carol" + System.lineSeparator());
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(1).getValue(1)).as("Cut must set the selected cells to NULL.")
                .isNull();
        assertThat(table.getRows().get(2).getValue(1)).isNull();
        assertThat(table.getRows().get(0).getValue(1)).as("Cut must not touch an unselected row.")
                .isEqualTo("Alice");
        assertThat(table.getRows().get(1).getValue(0)).as("Cut must not touch an unselected column.")
                .isEqualTo("2");
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
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 1));
        final DeleteAction action = new DeleteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(1).getValue(1)).as("Delete must set the selected cells to NULL.")
                .isNull();
        assertThat(table.getRows().get(1).getValue(0)).isEqualTo("2");
        assertThat(table.getRows().get(0).getValue(1)).as("Delete must not touch an unselected row.")
                .isEqualTo("Alice");
    }

    @Test
    void testDelete_ofWholeRows_deletesThemInsteadOfSettingThemToNull()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(1, 0), new Point(0, 1), new Point(1, 1));
        context.wholeRowsSelected = true;
        final DeleteAction action = new DeleteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("Delete must remove whole rows, because setting every cell of a row to NULL is "
                        + "rejected.")
                .containsExactly(List.of("3", "Carol"));
        assertThat(context.statusErrorMessage).as("Nothing may be rejected.").isNull();
    }

    @Test
    void testDelete_ofWholeRows_selectsTheRowThatTakesTheirPlaceInTheAnchorColumn()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/>"
                + "<USERS ID=\"4\" NAME=\"Dave\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 1;
        context.anchorRowIndex = 1;
        context.rowIndexes = List.of(1, 2);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions =
                List.of(new Point(0, 1), new Point(1, 1), new Point(0, 2), new Point(1, 2));
        context.wholeRowsSelected = true;

        new DeleteAction(context).run();

        assertThat(context.selectedRegion)
                .as("Deleting rows with Delete must leave the grid as Delete Rows does: with the row "
                        + "that took the first deleted row's place selected, so Delete can go on.")
                .isEqualTo(new Rectangle(1, 1, 1, 1));
    }

    @Test
    void testDelete_ofWholeRowsThatTheFirstRowRuleRefuses_reportsItThroughTheDeleteRowsDialog()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x\"/>"
                        + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carl\" EMAIL=\"c@x\"/>"
                        + "</dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0, 1, 2);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(1, 0), new Point(2, 0));
        context.wholeRowsSelected = true;

        new DeleteAction(context).run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                .as("Bob would become the first row without an email, which dbUnit would ignore in Carl.")
                .hasSize(3);
        assertThat(context.multiCellEditTitles)
                .as("Deleting whole rows is the Delete Rows command, so a refusal must be reported through "
                        + "its modal dialog, not just the status line.")
                .containsExactly("Delete Rows");
    }

    @Test
    void testCopy_ofACellThatShowsADefaultValue_copiesTheDefault()
    {
        final FlatXmlDatasetDocument datasetDocument = create(DEFAULTS_DATASET);
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(1, 0));

        new CopyAction(context).run();

        assertThat(context.clipboardText)
                .as("Copy must put the value dbUnit loads, so a cell without a value copies its default.")
                .isEqualTo("1\tACTIVE" + System.lineSeparator());
    }

    @Test
    void testSetNull_whenTheColumnHasADefaultValue_explainsOnTheStatusLineThatDbUnitLoadsTheDefault()
    {
        final FlatXmlDatasetDocument datasetDocument = create(DEFAULTS_DATASET);
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 1));

        new SetNullAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(1).getValue(1)).as("The attribute must be removed.").isNull();
        assertThat(table.getEffectiveValue(1, 1)).as("dbUnit loads the default for the removed attribute.")
                .isEqualTo("ACTIVE");
        assertThat(context.statusMessage).as("The user must be told why the cell is not NULL.")
                .isEqualTo("Column \"STATUS\" has the DTD default value \"ACTIVE\", so dbUnit loads it, "
                        + "not NULL, for a cell without a value.");
    }

    @Test
    void testSetNull_whenSeveralSelectedColumnsHaveDefaultValues_namesThemAllOnTheStatusLine()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED STATUS CDATA \"ACTIVE\" NOTE CDATA \"n\">\n]>\n"
                        + "<dataset><USERS ID=\"1\" STATUS=\"x\" NOTE=\"y\"/><USERS ID=\"2\" STATUS=\"x\"/>"
                        + "</dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(1, 2);
        context.selectedCellPositions =
                List.of(new Point(1, 0), new Point(2, 0), new Point(1, 1), new Point(2, 1));

        new SetNullAction(context).run();

        assertThat(context.statusMessage).as("Each column with a default must be named once.")
                .isEqualTo("Columns STATUS, NOTE have DTD default values, so dbUnit loads them, not NULL, "
                        + "for cells without a value.");
    }

    @Test
    void testSetNull_whenNoSelectedColumnHasADefaultValue_showsNoStatusMessage()
    {
        final FlatXmlDatasetDocument datasetDocument = create(DEFAULTS_DATASET);
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(0);
        context.selectedCellPositions = List.of(new Point(0, 1));
        context.statusMessage = "earlier message";

        new SetNullAction(context).run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(1).getValue(0))
                .as("The ID value must be removed.").isNull();
        assertThat(context.statusMessage).as("A column without a default needs no explanation.")
                .isEqualTo("earlier message");
    }

    @Test
    void testSetNull_whenTheEditIsRejected_showsNoStatusMessage()
    {
        final FlatXmlDatasetDocument datasetDocument = create(DEFAULTS_DATASET);
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(1, 0));

        new SetNullAction(context).run();

        assertThat(context.statusMessage)
                .as("An edit that did not run must not claim that a default is loaded instead of NULL.")
                .isNull();
    }

    @Test
    void testDelete_whenTheColumnHasADefaultValue_explainsOnTheStatusLineThatDbUnitLoadsTheDefault()
    {
        final FlatXmlDatasetDocument datasetDocument = create(DEFAULTS_DATASET);
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 1));

        new DeleteAction(context).run();

        assertThat(context.statusMessage).as("Delete sets cells to NULL, so it must give the same notice.")
                .isEqualTo("Column \"STATUS\" has the DTD default value \"ACTIVE\", so dbUnit loads it, "
                        + "not NULL, for a cell without a value.");
    }

    @Test
    void testDelete_whileEditingWithCaretBeforeASurrogatePair_deletesTheWholeCharacter()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.hasActiveCellEditor = true;
        final Text text = new Text(shell, SWT.NONE);
        text.setText("a😀b");
        text.setSelection(1);
        context.activeCellEditorText = text;
        final DeleteAction action = new DeleteAction(context);

        action.run();

        assertThat(text.getText())
                .as("Forward-delete must remove the whole surrogate pair, not just its high surrogate.")
                .isEqualTo("ab");
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
        context.selectedCellPositions =
                List.of(new Point(1, 0), new Point(1, 1), new Point(1, 2));
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
        context.selectedCellPositions = List.of(new Point(1, 1));
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
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0), new Point(1, 1));
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
        context.selectedCellPositions = List.of(new Point(1, 0));
        action.update(context.getSelection());
        assertThat(action.isEnabled())
                .as("A single-row selection at the top must disable Fill Down: no row above it.")
                .isFalse();

        context.rowIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 1));
        action.update(context.getSelection());
        assertThat(action.isEnabled())
                .as("A single-row selection with a row above it must enable Fill Down.").isTrue();

        context.rowIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(1, 0), new Point(1, 1));
        action.update(context.getSelection());
        assertThat(action.isEnabled()).as("A multi-row selection must enable Fill Down.").isTrue();
    }

    @Test
    void testUpdate_forFillDown_doesNotEnumerateTheSelectedCells()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final FillDownAction action = new FillDownAction(context);
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(0, 1), new Point(1, 0), new Point(1, 1));

        action.update(context.getSelection());

        assertThat(action.isEnabled()).as("Two selected rows in a column enable Fill Down.").isTrue();
        assertThat(context.selectedCellPositionReads)
                .as("The enablement is worked out on every selection event, so it must read the snapshot "
                        + "of the selection and not list every selected cell, which is a million for a big "
                        + "table.")
                .isZero();
    }

    @Test
    void testUpdate_forFillDown_withOneCellInEachOfTwoColumnsOnTheFirstRow_isDisabled()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final FillDownAction action = new FillDownAction(context);
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(1, 0));

        action.update(context.getSelection());

        assertThat(action.isEnabled())
                .as("Each column has one selected cell in the first row, so none has a row above it.")
                .isFalse();
    }

    @Test
    void testUpdate_forFillDown_withCellsOnTheFirstAndSecondRowInDifferentColumns_isEnabled()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final FillDownAction action = new FillDownAction(context);
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(1, 1));

        action.update(context.getSelection());

        assertThat(action.isEnabled())
                .as("The cell on the second row has the row above it, so its column can be filled.").isTrue();
    }

    @Test
    void testFillDown_withANonRectangularSelection_fillsEachColumnFromItsOwnTopSelectedCell()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\" CITY=\"Rome\"/>"
                        + "<USERS ID=\"2\" NAME=\"Bob\" CITY=\"Paris\"/>"
                        + "<USERS ID=\"3\" NAME=\"Carol\" CITY=\"Cairo\"/>"
                        + "<USERS ID=\"4\" NAME=\"Dave\" CITY=\"Lima\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1, 2, 3);
        context.columnIndexes = List.of(0, 2);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(0, 1), new Point(0, 2),
                new Point(0, 3), new Point(2, 2), new Point(2, 3));
        final FillDownAction action = new FillDownAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValue(0))
                .as("Column 0 (ID) is selected in every row and must fill from its own top row.")
                .containsExactly("1", "1", "1", "1");
        assertThat(table.getRows()).extracting(row -> row.getValue(2))
                .as("Column 2 (CITY) is selected only from row 2 down, so it must fill from row 2, not "
                        + "row 0, and its unselected rows 0 and 1 must stay untouched.")
                .containsExactly("Rome", "Paris", "Cairo", "Cairo");
        assertThat(table.getRows()).extracting(row -> row.getValue(1))
                .as("Column 1 (NAME) has no selected cells at all and must not be touched.")
                .containsExactly("Alice", "Bob", "Carol", "Dave");
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
    void testPaste_ofASingleValueOntoAMultiCellSelection_listsTheSelectedCellsOnce()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0), new Point(1, 1));
        context.clipboardText = "X";

        new PasteAction(context).run();

        assertThat(context.selectedCellPositionReads)
                .as("The cells to fill are listed once, to fill them, and not again to find out that there "
                        + "are several.")
                .isEqualTo(1);
    }

    @Test
    void testPaste_ofASingleValueOntoTwoCellsOfOneRow_fillsBothCells()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 1), new Point(1, 1));
        context.clipboardText = "X";

        new PasteAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("Two selected cells in one row are several cells, so the value must fill both of them "
                        + "and leave the other row alone.")
                .containsExactly(List.of("1", "Alice"), List.of("X", "X"));
    }

    @Test
    void testPaste_ofASingleValueOntoOneCell_pastesItWithoutListingTheSelectedCells()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 1));
        context.clipboardText = "X";

        new PasteAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("The value must go into the one selected cell.")
                .containsExactly(List.of("1", "Alice"), List.of("2", "X"));
        assertThat(context.selectedCellPositionReads)
                .as("One cell is the anchor of a block, and the snapshot of the selection says so, so "
                        + "the paste must not list the selected cells.")
                .isZero();
    }

    @Test
    void testPaste_ofABlockOntoAMultiCellSelection_pastesItAtTheAnchorWithoutListingTheSelectedCells()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1, 2);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(0, 1), new Point(0, 2),
                new Point(1, 0), new Point(1, 1), new Point(1, 2));
        context.clipboardText = "10\tX\n20\tY\n";

        new PasteAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("The block must go in at the top-left cell of the selection, and the third row stays.")
                .containsExactly(List.of("10", "X"), List.of("20", "Y"), List.of("3", "Carol"));
        assertThat(context.selectedCellPositionReads)
                .as("A block goes in at the anchor, so the paste must read the snapshot of the selection "
                        + "and not list every selected cell, which is a million for a big table.")
                .isZero();
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
    void testPaste_withNoValidAnchorOnANonEmptyTable_doesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.clipboardText = "X\tY\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows())
                .as("A paste with no valid anchor on a non-empty table must do nothing, not overwrite "
                        + "row 0.")
                .hasSize(1);
        assertThat(table.getRows().get(0).getValue(0)).isEqualTo("1");
        assertThat(table.getRows().get(0).getValue(1)).isEqualTo("Alice");
    }

    @Test
    void testPaste_forAnEmptyTable_isEnabledOnlyWithColumns()
    {
        final FlatXmlDatasetDocument withColumns = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
        final TestContext withColumnsContext = new TestContext(withColumns, "USERS");
        final PasteAction withColumnsAction = new PasteAction(withColumnsContext);

        withColumnsAction.update(withColumnsContext.getSelection());
        assertThat(withColumnsAction.isEnabled())
                .as("An empty table with columns must enable Paste whatever the clipboard holds.")
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
    void testPaste_forAnEmptyTableWithAnEmptyClipboard_doesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).as("Paste with an empty clipboard must not add a row.").isEmpty();
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

    @Test
    void testPaste_ofABlankLineBetweenRowsToAppend_skipsItAndAppendsTheOtherRows()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0, 1);
        context.clipboardText = "10\tX\n\n30\tZ\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("A pasted blank line cannot become a row, so the rows around it must be pasted without "
                        + "it.")
                .containsExactly(List.of("10", "X"), List.of("30", "Z"));
        assertThat(context.selectedRegion).as("Paste must select the rows it wrote.")
                .isEqualTo(new Rectangle(0, 0, 2, 2));
        assertThat(context.statusMessage).as("Paste must say that it skipped a row.")
                .isEqualTo("Skipped 1 pasted row without values, because a new row needs at least one "
                        + "value.");
    }

    @Test
    void testPaste_ofTextEndingInABlankLineIntoAnEmptyTable_appendsTheRowsAndSkipsTheBlankLine()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset>\n</dataset>\n");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.clipboardText = "1\tAlice\n2\tBob\n\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("A trailing blank line must not fail the paste of the rows before it.")
                .containsExactly(List.of("1", "Alice"), List.of("2", "Bob"));
        assertThat(context.selectedRegion).as("Paste must select the rows it wrote, not the skipped one.")
                .isEqualTo(new Rectangle(0, 0, 2, 2));
        assertThat(context.statusMessage).as("Paste must say that it skipped a row.")
                .isEqualTo("Skipped 1 pasted row without values, because a new row needs at least one "
                        + "value.");
    }

    @Test
    void testPaste_whenEveryRowToAppendHasNoValue_changesNothingAndSaysWhy()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset>\n</dataset>\n");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.clipboardText = "\n\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).as("Blank lines must not add rows.").isEmpty();
        assertThat(context.multiCellEditTitles).as("A paste with nothing to write must not run an edit.")
                .isEmpty();
        assertThat(context.selectedRegion).as("A paste that wrote nothing must not select anything.")
                .isNull();
        assertThat(context.statusMessage).as("Paste must say why nothing was added.")
                .isEqualTo("Skipped 2 pasted rows without values, because a new row needs at least one "
                        + "value.");
    }

    @Test
    void testPaste_whenOnlyValuesBeyondTheLastColumnWouldMakeUpARowToAppend_reportsBothOmissions()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        context.clipboardText = "A\tB\n\tD\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("The value that fits must be pasted, and the row left without values must be skipped.")
                .containsExactly(List.of("1", "A"));
        assertThat(context.statusMessage).as("Paste must report the ignored column and the skipped row.")
                .isEqualTo("Ignored 1 pasted column beyond the table's last column. Skipped 1 pasted row "
                        + "without values, because a new row needs at least one value.");
    }

    @Test
    void testPaste_ofACopiedNullCellOntoAnExistingCell_setsItToNull()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(1);
        context.clipboardText = "\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(1).getValues())
                .as("A blank line is a NULL cell, which Copy writes for one, so Paste must set the target "
                        + "cell to NULL.")
                .containsExactly("2", null);
        assertThat(context.statusMessage).as("Pasting onto an existing row skips nothing.").isNull();
    }

    private static List<String> firstColumnValues(final FlatXmlDatasetDocument datasetDocument,
            final String tableKey)
    {
        final DatasetTable table = datasetDocument.getModel().findTable(tableKey).orElseThrow();
        final List<String> values = new ArrayList<>();
        for (final DatasetRow row : table.getRows())
        {
            values.add(row.getValues().get(0));
        }
        return values;
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

    private static FlatXmlDatasetDocument createWithDtdFile(final String content, final String dtdText)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(new Document(content),
                (publicId, systemId) -> Optional.of(dtdText), FlatXmlOptions.DBUNIT_DEFAULTS,
                () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }

    private static FlatXmlDatasetDocument createCaseSensitive(final String content)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(new Document(content),
                DtdSource.NONE, new FlatXmlOptions(true, FlatXmlOptions.DBUNIT_DEFAULTS.columnSensing()),
                () -> StandardCharsets.UTF_8);
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

        private int selectedCellPositionReads;

        private Text activeCellEditorText;

        private boolean selectAllCalled;

        private boolean editCellInDialogCalled;

        private boolean showInSourceCalled;

        private String statusMessage;

        private String statusErrorMessage;

        private GridSelection staleSelection;

        private String clipboardText;

        private Rectangle selectedRegion;

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
            if (staleSelection != null)
            {
                return staleSelection;
            }
            final DatasetTable table = datasetDocument.getModel().findTable(tableKey).orElseThrow();
            final int rowCount = table.getRows().size();
            final int columnCount = table.getColumns().size();
            return new GridSelection(tableKey, rowCount, columnCount, anchorColumnIndex, anchorRowIndex,
                    rowIndexes, columnIndexes, wholeRowsSelected);
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
        public Text getActiveCellEditorText()
        {
            return activeCellEditorText;
        }

        @Override
        public List<Point> getSelectedCellPositions()
        {
            selectedCellPositionReads++;
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
        public void setStatusErrorMessage(final String message)
        {
            statusErrorMessage = message;
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
