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
package org.dbunit.eclipse.dataset.ui.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.data.validate.ValidationFailedException;
import org.eclipse.nebula.widgets.nattable.edit.EditConfigAttributes;
import org.eclipse.nebula.widgets.nattable.edit.command.EditSelectionCommand;
import org.eclipse.nebula.widgets.nattable.edit.editor.ICellEditor;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer.MoveDirectionEnum;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectCellCommand;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests in-place editing against the Grid specification: {@link TableBodyDataProvider#setDataValue},
 * {@link XmlCharacterValidator}, and {@link PrintableCharacterKeyEventMatcher}.
 */
class GridEditingTest
{
    private static final String DEFAULTS_DATASET = "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
            + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #IMPLIED STATUS CDATA \"ACTIVE\">\n]>\n"
            + "<dataset><USERS ID=\"1\"/><USERS STATUS=\"x\"/></dataset>";

    private static final String FOUR_USERS = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
            + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/><USERS ID=\"4\" NAME=\"Dave\"/>"
            + "</dataset>";

    private static final String USERS_WITHOUT_ALICE = "<dataset><USERS ID=\"2\" NAME=\"Bob\"/>"
            + "<USERS ID=\"3\" NAME=\"Carol\"/><USERS ID=\"4\" NAME=\"Dave\"/></dataset>";

    private static final String CELL_GONE_MESSAGE =
            "The cell is no longer in the table, so its new value was not saved.";

    private static final String EDIT_CANCELLED_MESSAGE =
            "The table changed while a cell was being edited, so the edit was cancelled.";

    private Shell shell;

    private ModalDialogDriver dialogDriver;

    @BeforeEach
    void createShell()
    {
        shell = new Shell(Display.getDefault());
        dialogDriver = new ModalDialogDriver(shell);
    }

    @AfterEach
    void disposeShell()
    {
        dialogDriver.disarm();
        shell.dispose();
    }

    @Test
    void testSetDataValue_whenAValueChanges_rewritesOnlyThatAttribute()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "Carol");

        assertThat(document.get()).as("Only the changed attribute's text must change.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Carol\"/></dataset>");
    }

    @Test
    void testSetDataValue_whenEditingANullCellWithTheEmptyString_staysNull()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>";
        final IDocument document = new Document(originalText);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 1, "");

        assertThat(provider.getDataValue(1, 1)).as("An empty edit of a NULL cell must keep it NULL.")
                .isNull();
        assertThat(document.get()).as("An empty edit of a NULL cell must not change the document.")
                .isEqualTo(originalText);
    }

    @Test
    void testSetDataValue_whenCommittingTheDisplayedDefaultOfAnOmittedAttribute_leavesTheDocumentUnchanged()
    {
        final IDocument document = new Document(DEFAULTS_DATASET);
        final TableBodyDataProvider provider =
                new TableBodyDataProvider(new TestContext(create(document)), "USERS");

        provider.setDataValue(1, 0, "ACTIVE");

        assertThat(document.get())
                .as("Committing the default that the cell displays must not write an attribute for it.")
                .isEqualTo(DEFAULTS_DATASET);
    }

    @Test
    void testSetDataValue_whenReplacingTheDisplayedDefault_writesTheAttribute()
    {
        final IDocument document = new Document(DEFAULTS_DATASET);
        final TableBodyDataProvider provider =
                new TableBodyDataProvider(new TestContext(create(document)), "USERS");

        provider.setDataValue(1, 0, "INACTIVE");

        assertThat(document.get()).as("A value other than the default must be written as an attribute.")
                .isEqualTo(DEFAULTS_DATASET.replace("<USERS ID=\"1\"/>",
                        "<USERS ID=\"1\" STATUS=\"INACTIVE\"/>"));
    }

    @Test
    void testSetDataValue_whenClearingTheDisplayedDefault_storesTheEmptyString()
    {
        final IDocument document = new Document(DEFAULTS_DATASET);
        final TableBodyDataProvider provider =
                new TableBodyDataProvider(new TestContext(create(document)), "USERS");

        provider.setDataValue(1, 0, "");

        assertThat(document.get())
                .as("Clearing a cell that shows a default must store the empty string, which overrides it.")
                .isEqualTo(DEFAULTS_DATASET.replace("<USERS ID=\"1\"/>", "<USERS ID=\"1\" STATUS=\"\"/>"));
    }

    @Test
    void testSetDataValue_whenCommittingTheEmptyDefaultOfAnOmittedAttribute_leavesTheDocumentUnchanged()
    {
        final String originalText = DEFAULTS_DATASET.replace("\"ACTIVE\"", "\"\"");
        final IDocument document = new Document(originalText);
        final TableBodyDataProvider provider =
                new TableBodyDataProvider(new TestContext(create(document)), "USERS");

        provider.setDataValue(1, 0, "");

        assertThat(document.get())
                .as("Committing an empty default that the cell displays must not write an attribute.")
                .isEqualTo(originalText);
    }

    @Test
    void testCommit_whenTheInPlaceEditorOfADefaultedCellIsUntouched_keepsTheAttributeOut()
    {
        final IDocument document = new Document(DEFAULTS_DATASET);
        final NatTable natTable = openGrid(create(document), "USERS").getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 1, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        final ICellEditor cellEditor = natTable.getActiveCellEditor();

        final Object editorValue = cellEditor.getEditorValue();
        cellEditor.commit(MoveDirectionEnum.NONE);

        assertThat(editorValue).as("The editor of a cell that shows a default must start with the default.")
                .isEqualTo("ACTIVE");
        assertThat(document.get())
                .as("Committing the editor of a defaulted cell without typing must not write the default.")
                .isEqualTo(DEFAULTS_DATASET);
    }

    @Test
    void testEditCellInDialog_whenTheCellShowsADefaultAndTheDialogIsConfirmedUnchanged_keepsTheAttributeOut()
    {
        final IDocument document = new Document(DEFAULTS_DATASET);
        final DatasetGrid grid = openGrid(create(document), "USERS");
        grid.selectRegion(1, 0, 1, 1);
        dialogDriver.confirmNextDialog();

        grid.editCellInDialog();

        assertThat(dialogDriver.hasConfirmed()).as("The dialog must open and be confirmed.").isTrue();
        assertThat(document.get())
                .as("Confirming the dialog of a defaulted cell without a change must not write the default.")
                .isEqualTo(DEFAULTS_DATASET);
    }

    @Test
    void testSetDataValue_whenEditingANonNullCellWithTheEmptyString_storesTheEmptyString()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "");

        assertThat(provider.getDataValue(1, 0))
                .as("An empty edit of a non-NULL cell must store the empty string, not NULL.").isEqualTo("");
        assertThat(document.get()).as("The stored empty string must appear as an empty attribute.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"\"/></dataset>");
    }

    @Test
    void testSetDataValue_whenTheEditWouldEmptyARow_leavesTheDocumentUnchangedAndSetsTheStatusLineError()
    {
        final String originalText = "<dataset><USERS ID=\"1\"/></dataset>";
        final IDocument document = new Document(originalText);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(0, 0, null);

        assertThat(document.get()).as("A rejected edit must leave the document unchanged.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage)
                .as("A rejected edit must set the status line error message.")
                .isEqualTo("This change would leave row 0 of table 'USERS' with no values. Use Delete "
                        + "Rows to remove it instead.");
    }

    @Test
    void testSetDataValue_whenTheInputIsReadOnly_rejectsTheEdit()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument, false);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "Carol");

        assertThat(document.get()).as("A read-only input must reject the edit.").isEqualTo(originalText);
    }

    @Test
    void testSetDataValue_whenTheRowIsPastTheEnd_leavesTheDocumentUnchangedAndSetsTheStatusLineError()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final TestContext context = new TestContext(create(document));
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 1, "Carol");

        assertThat(document.get()).as("An edit of a row that is gone must leave the document unchanged.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage).as("The user must be told that the edit was not saved.")
                .isEqualTo(CELL_GONE_MESSAGE);
    }

    @Test
    void testSetDataValue_whenNatTableFoundNoRow_leavesTheDocumentUnchangedAndSetsTheStatusLineError()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final TestContext context = new TestContext(create(document));
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, -1, "Carol");

        assertThat(document.get()).as("NatTable passes -1 for a position that is out of range.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage).as("The user must be told that the edit was not saved.")
                .isEqualTo(CELL_GONE_MESSAGE);
    }

    @Test
    void testSetDataValue_whenTheColumnIsPastTheEnd_leavesTheDocumentUnchangedAndSetsTheStatusLineError()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final TestContext context = new TestContext(create(document));
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(2, 0, "Carol");

        assertThat(document.get()).as("An edit of a column that is gone must leave the document unchanged.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage).as("The user must be told that the edit was not saved.")
                .isEqualTo(CELL_GONE_MESSAGE);
    }

    @Test
    void testSetDataValue_whenNatTableFoundNoColumn_leavesTheDocumentUnchangedAndSetsTheStatusLineError()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final TestContext context = new TestContext(create(document));
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(-1, 0, "Carol");

        assertThat(document.get()).as("NatTable passes -1 for a position that is out of range.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage).as("The user must be told that the edit was not saved.")
                .isEqualTo(CELL_GONE_MESSAGE);
    }

    @Test
    void testSetDataValue_whenTheEditorTurnedLineFeedsIntoCrLf_keepsTheLineFeeds()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NOTE=\"first&#xA;second\"/></dataset>";
        final IDocument document = new Document(originalText);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "first\r\nsecond");

        assertThat(document.get())
                .as("A text widget that writes CR LF must not change a value with line feeds.")
                .isEqualTo(originalText);
    }

    @Test
    void testSetDataValue_whenTheValueUsedCrLf_keepsCrLf()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NOTE=\"a&#xD;&#xA;b\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "a\r\nb\r\nc");

        assertThat(document.get()).as("A value that used CR LF line breaks must keep them.")
                .isEqualTo("<dataset><USERS ID=\"1\" NOTE=\"a&#xD;&#xA;b&#xD;&#xA;c\"/></dataset>");
    }

    @Test
    void testCommit_ofAnEditThatMovesTheSelection_closesTheEditorWithoutEditingTheNextCell()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final NatTable natTable = openGrid(datasetDocument, "USERS").getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 1, 1, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        final ICellEditor cellEditor = natTable.getActiveCellEditor();
        cellEditor.setEditorValue("10");

        cellEditor.commit(MoveDirectionEnum.DOWN);

        assertThat(natTable.getActiveCellEditor())
                .as("Committing with Enter or Tab must move the selection without editing the next cell.")
                .isNull();
        assertThat(document.get()).as("The committed value must reach the document.")
                .isEqualTo("<dataset><USERS ID=\"10\"/><USERS ID=\"2\"/></dataset>");
    }

    @Test
    void testCommit_whenTheTextOfTheInPlaceEditorIsCleared_storesTheEmptyString()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final NatTable natTable = openGrid(create(document), "USERS").getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 1, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        final ICellEditor cellEditor = natTable.getActiveCellEditor();
        cellEditor.setEditorValue("");

        cellEditor.commit(MoveDirectionEnum.NONE);

        assertThat(document.get())
                .as("Clearing the text of a cell with a value must store the empty string, not NULL.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"\"/></dataset>");
    }

    @Test
    void testCommit_whenTheInPlaceEditorOfANullCellIsUntouched_keepsItNull()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>";
        final IDocument document = new Document(originalText);
        final NatTable natTable = openGrid(create(document), "USERS").getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        final ICellEditor cellEditor = natTable.getActiveCellEditor();

        cellEditor.commit(MoveDirectionEnum.NONE);

        assertThat(document.get())
                .as("Committing the editor of a NULL cell without typing anything must keep it NULL.")
                .isEqualTo(originalText);
    }

    @Test
    void testCommitActiveCellEditor_whileACellIsBeingEdited_writesItsValueAndClosesTheEditor()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 1, 2, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        natTable.getActiveCellEditor().setEditorValue("20");

        final boolean closed = grid.commitActiveCellEditor();

        assertThat(closed).as("Committing a valid value must close the editor.").isTrue();
        assertThat(natTable.getActiveCellEditor()).as("No cell editor may stay open.").isNull();
        assertThat(document.get()).as("The open editor's value must reach the document, as saving needs.")
                .isEqualTo("<dataset><USERS ID=\"1\"/><USERS ID=\"20\"/></dataset>");
    }

    @Test
    void testCancelActiveCellEditor_whileACellIsBeingEdited_closesTheEditorWithoutWritingItsValue()
    {
        final String originalText = "<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>";
        final IDocument document = new Document(originalText);
        final DatasetGrid grid = openGrid(create(document), "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 1, 2, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        natTable.getActiveCellEditor().setEditorValue("20");

        grid.cancelActiveCellEditor();

        assertThat(natTable.getActiveCellEditor()).as("No cell editor may stay open.").isNull();
        assertThat(document.get()).as("The value of a cancelled editor must not reach the document.")
                .isEqualTo(originalText);
    }

    @Test
    void testCancelActiveCellEditor_whenNoCellIsBeingEdited_doesNothing()
    {
        final String originalText = "<dataset><USERS ID=\"1\"/></dataset>";
        final IDocument document = new Document(originalText);
        final DatasetGrid grid = openGrid(create(document), "USERS");

        grid.cancelActiveCellEditor();

        assertThat(document.get()).as("Cancelling without an open editor must leave the document alone.")
                .isEqualTo(originalText);
    }

    @Test
    void testEditCellInDialog_whenAnEmptyStringIsConfirmedUnchanged_keepsTheEmptyString()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"\"/></dataset>";
        final IDocument document = new Document(originalText);
        final DatasetGrid grid = openGrid(create(document), "USERS");
        grid.selectRegion(1, 0, 1, 1);
        dialogDriver.confirmNextDialog();

        grid.editCellInDialog();

        assertThat(dialogDriver.hasConfirmed()).as("The dialog must open and be confirmed.").isTrue();
        assertThat(document.get())
                .as("Confirming the dialog without a change must keep the empty string, not make it NULL.")
                .isEqualTo(originalText);
    }

    @Test
    void testEditCellInDialog_whenTheDialogTextIsCleared_storesTheEmptyString()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final DatasetGrid grid = openGrid(create(document), "USERS");
        grid.selectRegion(1, 0, 1, 1);
        dialogDriver.confirmNextDialogWithText("");

        grid.editCellInDialog();

        assertThat(dialogDriver.hasConfirmed()).as("The dialog must open and be confirmed.").isTrue();
        assertThat(document.get())
                .as("Clearing the text of a cell with a value must store the empty string, not NULL.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"\"/></dataset>");
    }

    @Test
    void testEditCellInDialog_whenTheCellIsNullAndTheDialogIsConfirmedUnchanged_keepsItNull()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>";
        final IDocument document = new Document(originalText);
        final DatasetGrid grid = openGrid(create(document), "USERS");
        grid.selectRegion(1, 1, 1, 1);
        dialogDriver.confirmNextDialog();

        grid.editCellInDialog();

        assertThat(dialogDriver.hasConfirmed()).as("The dialog must open and be confirmed.").isTrue();
        assertThat(document.get())
                .as("Confirming the dialog of a NULL cell without typing anything must keep it NULL.")
                .isEqualTo(originalText);
    }

    @Test
    void testEditCellInDialog_whenTheDialogTextIsChanged_storesTheNewValue()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final DatasetGrid grid = openGrid(create(document), "USERS");
        grid.selectRegion(1, 0, 1, 1);
        dialogDriver.confirmNextDialogWithText("Bob");

        grid.editCellInDialog();

        assertThat(dialogDriver.hasConfirmed()).as("The dialog must open and be confirmed.").isTrue();
        assertThat(document.get()).as("The text entered in the dialog must reach the document.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Bob\"/></dataset>");
    }

    @Test
    void testTableChanged_whenARowAboveTheEditedCellIsRemoved_closesTheEditorWithoutWritingItsValue()
    {
        final IDocument document = new Document(FOUR_USERS);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        natTable.getActiveCellEditor().setEditorValue("Zed");

        changeDocument(datasetDocument, document, grid, USERS_WITHOUT_ALICE);
        grid.commitActiveCellEditor();

        assertThat(natTable.getActiveCellEditor())
                .as("A table whose rows changed must close the open editor.").isNull();
        assertThat(document.get())
                .as("The value of Bob's cell must not land in the row that took Bob's place.")
                .isEqualTo(USERS_WITHOUT_ALICE);
    }

    @Test
    void testTableChanged_whenTheEditedColumnIsRemoved_closesTheEditor()
    {
        final IDocument document = new Document(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        natTable.getActiveCellEditor().setEditorValue("Zed");
        final String changedText = "<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>";

        changeDocument(datasetDocument, document, grid, changedText);
        grid.commitActiveCellEditor();

        assertThat(natTable.getActiveCellEditor())
                .as("A table that lost the edited column must close the editor.").isNull();
        assertThat(document.get()).as("The value of a column that is gone must not be written.")
                .isEqualTo(changedText);
    }

    @Test
    void testTableChanged_whenTheEditorIsCancelled_tellsTheUserOnTheStatusLine()
    {
        final IDocument document = new Document(FOUR_USERS);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS", context);
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));

        changeDocument(datasetDocument, document, grid, USERS_WITHOUT_ALICE);

        assertThat(context.lastStatusMessage).as("The user must be told why the edit went away.")
                .isEqualTo(EDIT_CANCELLED_MESSAGE);
    }

    @Test
    void testTableChanged_whenNoCellIsBeingEdited_leavesTheStatusLineAlone()
    {
        final IDocument document = new Document(FOUR_USERS);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS", context);

        changeDocument(datasetDocument, document, grid, USERS_WITHOUT_ALICE);

        assertThat(context.lastStatusMessage).as("Without an edit there is nothing to report.").isNull();
    }

    @Test
    void testTableChanged_afterTheDialogWasConfirmed_leavesTheStatusLineAlone()
    {
        final IDocument document = new Document(FOUR_USERS);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS", context);
        grid.selectRegion(1, 1, 1, 1);
        dialogDriver.confirmNextDialogWithText("Zed");
        grid.editCellInDialog();

        changeDocument(datasetDocument, document, grid, USERS_WITHOUT_ALICE);

        assertThat(context.lastStatusMessage).as("A dialog that is closed has no edit left to cancel.")
                .isNull();
    }

    @Test
    void testTableChanged_whenOnlyAnotherValueChanges_keepsTheEditorOpenAndWritesItsValue()
    {
        final IDocument document = new Document(FOUR_USERS);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        natTable.getActiveCellEditor().setEditorValue("Zed");

        changeDocument(datasetDocument, document, grid, FOUR_USERS.replace("Dave", "David"));

        assertThat(natTable.getActiveCellEditor())
                .as("A change that keeps the table's rows and columns must not close the editor.")
                .isNotNull();
        grid.commitActiveCellEditor();
        assertThat(document.get()).as("The edited cell must still get its value.")
                .isEqualTo(FOUR_USERS.replace("Dave", "David").replace("Bob", "Zed"));
    }

    @Test
    void testEditCellInDialog_whenARowIsRemovedWhileTheDialogIsOpen_closesTheDialogWithoutWritingItsValue()
    {
        final IDocument document = new Document(FOUR_USERS);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS", context);
        grid.selectRegion(1, 1, 1, 1);
        dialogDriver.changeThenConfirmNextDialogWithText(
                () -> changeDocument(datasetDocument, document, grid, USERS_WITHOUT_ALICE), "Zed");

        grid.editCellInDialog();

        assertThat(dialogDriver.hasOpened()).as("The dialog must open.").isTrue();
        assertThat(dialogDriver.hasConfirmed())
                .as("The dialog of a cell whose table changed shape must be closed.").isFalse();
        assertThat(document.get()).as("The value must not land in the row that took Bob's place.")
                .isEqualTo(USERS_WITHOUT_ALICE);
        assertThat(context.lastStatusMessage).as("The user must be told why the dialog went away.")
                .isEqualTo(EDIT_CANCELLED_MESSAGE);
        assertThat(dialogCellEditor(grid).isClosed())
                .as("The editor must be closed as the dialog's Cancel button closes it, to release its "
                        + "resources.")
                .isTrue();
    }

    @Test
    void testEditCellInDialog_whenOnlyAnotherValueChangesWhileTheDialogIsOpen_stillStoresTheNewValue()
    {
        final IDocument document = new Document(FOUR_USERS);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS");
        grid.selectRegion(1, 1, 1, 1);
        dialogDriver.changeThenConfirmNextDialogWithText(
                () -> changeDocument(datasetDocument, document, grid, FOUR_USERS.replace("Dave", "David")),
                "Zed");

        grid.editCellInDialog();

        assertThat(dialogDriver.hasConfirmed())
                .as("A change that keeps the table's rows and columns must not close the dialog.").isTrue();
        assertThat(document.get()).as("The edited cell must still get its value.")
                .isEqualTo(FOUR_USERS.replace("Dave", "David").replace("Bob", "Zed"));
    }

    @Test
    void testEdit_whenAMultiLineCellOpensItsDialogAndARowIsRemoved_closesTheDialogWithoutWritingItsValue()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NOTE=\"first&#xA;second\"/>"
                + "<USERS ID=\"2\" NOTE=\"third&#xA;fourth\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
        final String changedText = "<dataset><USERS ID=\"2\" NOTE=\"third&#xA;fourth\"/></dataset>";
        dialogDriver.changeThenConfirmNextDialogWithText(
                () -> changeDocument(datasetDocument, document, grid, changedText), "other");

        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));

        assertThat(dialogDriver.hasOpened()).as("A multi-line value must open in the dialog.").isTrue();
        assertThat(dialogDriver.hasConfirmed()).as("The dialog of a table that changed shape must be closed.")
                .isFalse();
        assertThat(document.get()).as("The dialog's value must not be written.").isEqualTo(changedText);
    }

    @Test
    void testTableChanged_ofAnotherGridWhileTheDialogIsOpen_leavesTheDialogOpen()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><ORDERS ID=\"1\"/></dataset>";
        final IDocument document = new Document(originalText);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final DatasetGrid users = openGrid(datasetDocument, "USERS");
        final DatasetGrid orders = openGrid(datasetDocument, "ORDERS");
        users.selectRegion(1, 0, 1, 1);
        dialogDriver.changeThenConfirmNextDialogWithText(() ->
        {
            datasetDocument.insertRows("ORDERS", 1, List.of(List.of("2")));
            users.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());
            orders.tableChanged(datasetDocument.getModel().findTable("ORDERS").orElseThrow());
        }, "Bob");

        users.editCellInDialog();

        assertThat(dialogDriver.hasConfirmed())
                .as("A change to another table's rows must not close this table's dialog.").isTrue();
        assertThat(document.get()).as("Both the other table's new row and the dialog's value must be there.")
                .contains("<USERS ID=\"1\" NAME=\"Bob\"/>", "<ORDERS ID=\"2\"/>");
    }

    @Test
    void testTableChanged_whenAMultiLineCellIsEditedInPlace_closesTheEditorAndLeavesTheWindowOpen()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NOTE=\"first&#xA;second\"/>"
                + "<USERS ID=\"2\" NOTE=\"third&#xA;fourth\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final DatasetGrid grid = openGrid(datasetDocument, "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.getConfigRegistry().registerConfigAttribute(EditConfigAttributes.OPEN_IN_DIALOG,
                Boolean.FALSE, DisplayMode.EDIT, DatasetCellLabels.MULTI_LINE_VALUE);
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        final String changedText = "<dataset><USERS ID=\"2\" NOTE=\"third&#xA;fourth\"/></dataset>";

        changeDocument(datasetDocument, document, grid, changedText);

        assertThat(natTable.getActiveCellEditor()).as("The editor of a table that changed shape must close.")
                .isNull();
        assertThat(shell.isDisposed())
                .as("Closing an editor that is not in a dialog must not close a window.").isFalse();
    }

    @Test
    void testDispose_whileTheDialogIsOpen_closesTheDialogWithoutWritingItsValue()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final DatasetGrid grid = openGrid(create(document), "USERS");
        grid.selectRegion(1, 0, 1, 1);
        dialogDriver.changeThenConfirmNextDialogWithText(() -> grid.getControl().dispose(), "Bob");

        grid.editCellInDialog();

        assertThat(dialogDriver.hasOpened()).as("The dialog must open.").isTrue();
        assertThat(dialogDriver.hasConfirmed()).as("The dialog of a grid that is gone must be closed.")
                .isFalse();
        assertThat(document.get()).as("The dialog's value must not be written.").isEqualTo(originalText);
    }

    @Test
    void testValidate_whenTheValueContainsANonXmlCharacter_throwsNamingTheCharacter()
    {
        final XmlCharacterValidator validator = new XmlCharacterValidator();

        assertThatThrownBy(() -> validator.validate(0, 0, "a\u0001b"))
                .as("A code point outside the XML 1.0 Char production must be rejected.")
                .isInstanceOf(ValidationFailedException.class)
                .hasMessage("Character U+0001 is not allowed in XML.");
    }

    @Test
    void testMatches_onANonMacPlatform_forPrintableCharactersWithNoOrExpectedModifiers_returnsTrue()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(false);

        assertThat(matcher.matches(keyEvent('a', 'a', SWT.NONE))).as("A plain letter must match.").isTrue();
        assertThat(matcher.matches(keyEvent('5', '5', SWT.NONE))).as("A plain digit must match.").isTrue();
        assertThat(matcher.matches(keyEvent('.', '.', SWT.NONE))).as("Plain punctuation must match.")
                .isTrue();
        assertThat(matcher.matches(keyEvent(' ', ' ', SWT.NONE))).as("A plain space must match.").isTrue();
        assertThat(matcher.matches(keyEvent('A', 'a', SWT.SHIFT))).as("Shift plus a letter must match.")
                .isTrue();
        assertThat(matcher.matches(keyEvent('a', 'a', SWT.MOD1 | SWT.MOD3)))
                .as("AltGr (Ctrl+Alt) plus a character must match.").isTrue();
        assertThat(matcher.matches(keyEvent('A', 'a', SWT.MOD1 | SWT.MOD3 | SWT.SHIFT)))
                .as("AltGr with Shift plus a character must match.").isTrue();
    }

    @Test
    void testMatches_onANonMacPlatform_forAltAlone_returnsFalse()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(false);

        assertThat(matcher.matches(keyEvent('a', 'a', SWT.ALT)))
                .as("Alt alone must not match outside macOS, since it drives menu mnemonics.").isFalse();
        assertThat(matcher.matches(keyEvent('A', 'a', SWT.ALT | SWT.SHIFT)))
                .as("Alt with Shift must not match outside macOS either.").isFalse();
    }

    @Test
    void testMatches_onMacOs_forPrintableCharactersTypedWithOption_returnsTrue()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(true);

        assertThat(matcher.matches(keyEvent('a', 'a', SWT.NONE))).as("A plain letter must match.").isTrue();
        assertThat(matcher.matches(keyEvent('A', 'a', SWT.SHIFT))).as("Shift plus a letter must match.")
                .isTrue();
        assertThat(matcher.matches(keyEvent('@', 'l', SWT.ALT)))
                .as("A character typed with Option alone must match on macOS.").isTrue();
        assertThat(matcher.matches(keyEvent('€', 'e', SWT.ALT | SWT.SHIFT)))
                .as("A character typed with Option and Shift must match on macOS.").isTrue();
    }

    @Test
    void testMatches_onMacOs_forCommandModifiedCharacters_returnsFalse()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(true);

        assertThat(matcher.matches(keyEvent('a', 'a', SWT.COMMAND))).as("Command alone must not match.")
                .isFalse();
        assertThat(matcher.matches(keyEvent('a', 'a', SWT.COMMAND | SWT.ALT)))
                .as("Command plus Option must not match, even though SWT.MOD1 | SWT.MOD3 is Command "
                        + "plus Option on macOS.")
                .isFalse();
    }

    @Test
    void testMatches_forControlCharactersOrNonTypingKeys_returnsFalse()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(false);

        assertThat(matcher.matches(keyEvent((char) 3, 'c', SWT.MOD1))).as("Ctrl+C must not match.")
                .isFalse();
        assertThat(matcher.matches(keyEvent((char) 0, SWT.ARROW_RIGHT, SWT.NONE)))
                .as("An arrow key must not match.").isFalse();
        assertThat(matcher.matches(keyEvent((char) 0, SWT.F2, SWT.NONE)))
                .as("A function key must not match.").isFalse();
    }

    @Test
    void testMatches_forEitherHalfOfASupplementaryCharacter_returnsFalse()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(false);
        final int emoji = 0x1F600;

        assertThat(matcher.matches(keyEvent(Character.highSurrogate(emoji), 0, SWT.NONE)))
                .as("The first half of an emoji, which SWT sends as a key event of its own, must not match.")
                .isFalse();
        assertThat(matcher.matches(keyEvent(Character.lowSurrogate(emoji), 0, SWT.NONE)))
                .as("The second half of an emoji must not match either.").isFalse();
    }

    @Test
    void testRun_whenTheKeyEventCarriesAPrintableCharacter_opensTheEditorSeededWithIt()
    {
        final DatasetGrid grid = openGrid(create(new Document(FOUR_USERS)), "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));

        new AnchorCellKeyEditAction(grid.getSelectionLayer()).run(natTable, keyEvent('x', 'x', SWT.NONE));

        assertThat(natTable.getActiveCellEditor().getEditorValue())
                .as("Typing a character on a selected cell must start an edit with that character.")
                .isEqualTo("x");
    }

    @Test
    void testRun_whenTheKeyEventCarriesHalfOfASupplementaryCharacter_doesNotSeedTheEditorWithIt()
    {
        final DatasetGrid grid = openGrid(create(new Document(FOUR_USERS)), "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
        final char highSurrogate = Character.highSurrogate(0x1F600);

        final KeyEvent typedHalf = keyEvent(highSurrogate, 0, SWT.NONE);
        new AnchorCellKeyEditAction(grid.getSelectionLayer()).run(natTable, typedHalf);

        final Object editorValue = natTable.getActiveCellEditor().getEditorValue();
        assertThat(String.valueOf(editorValue))
                .as("An editor seeded with half of a pair would hold a character that is not well formed.")
                .doesNotContain(String.valueOf(highSurrogate));
    }

    @Test
    void testAddCellEditorListener_whenAnEditorOpensAndIsCommitted_reportsTheEditorStateAtEachStep()
    {
        final FlatXmlDatasetDocument datasetDocument = create(new Document(FOUR_USERS));
        final DatasetGrid grid = openGrid(datasetDocument, "USERS");
        final NatTable natTable = grid.getNatTable();
        final List<Boolean> editorOpenAtEachCall = new ArrayList<>();
        grid.addCellEditorListener(() -> editorOpenAtEachCall.add(natTable.getActiveCellEditor() != null));
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));

        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        natTable.commitAndCloseActiveCellEditor();

        assertThat(editorOpenAtEachCall)
                .as("A listener must find the editor active when it opens and gone when it closes.")
                .containsExactly(true, false);
    }

    @Test
    void testAddCellEditorListener_whenAnEditorOpensAndIsCancelled_reportsTheEditorStateAtEachStep()
    {
        final FlatXmlDatasetDocument datasetDocument = create(new Document(FOUR_USERS));
        final DatasetGrid grid = openGrid(datasetDocument, "USERS");
        final NatTable natTable = grid.getNatTable();
        final List<Boolean> editorOpenAtEachCall = new ArrayList<>();
        grid.addCellEditorListener(() -> editorOpenAtEachCall.add(natTable.getActiveCellEditor() != null));
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));

        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        grid.cancelActiveCellEditor();

        assertThat(editorOpenAtEachCall)
                .as("A listener must find the editor active when it opens and gone when it is cancelled.")
                .containsExactly(true, false);
    }

    @Test
    void testAddCellEditorListener_whenTheGridIsDisposedWithAnOpenEditor_reportsNoClosing()
    {
        final FlatXmlDatasetDocument datasetDocument = create(new Document(FOUR_USERS));
        final DatasetGrid grid = openGrid(datasetDocument, "USERS");
        final NatTable natTable = grid.getNatTable();
        final List<Boolean> editorOpenAtEachCall = new ArrayList<>();
        grid.addCellEditorListener(() -> editorOpenAtEachCall.add(natTable.getActiveCellEditor() != null));
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));

        natTable.dispose();

        assertThat(editorOpenAtEachCall)
                .as("An editor that goes away with its grid must not be reported, as the grid is gone.")
                .containsExactly(true);
    }

    private KeyEvent keyEvent(final char character, final int keyCode, final int stateMask)
    {
        final Event event = new Event();
        event.widget = shell;
        event.character = character;
        event.keyCode = keyCode;
        event.stateMask = stateMask;
        return new KeyEvent(event);
    }

    /**
     * Replaces the document's text as a reload from disk does, then refreshes the model and the grid as the
     * Tables page does after a text change.
     */
    private static void changeDocument(final FlatXmlDatasetDocument datasetDocument, final IDocument document,
            final DatasetGrid grid, final String newText)
    {
        document.set(newText);
        datasetDocument.refresh();
        final String tableKey = grid.getBodyDataProvider().getTableKey();
        grid.tableChanged(datasetDocument.getModel().findTable(tableKey).orElseThrow());
    }

    private static ICellEditor dialogCellEditor(final DatasetGrid grid)
    {
        return grid.getNatTable().getConfigRegistry().getConfigAttribute(EditConfigAttributes.CELL_EDITOR,
                DisplayMode.EDIT, DatasetCellLabels.EDIT_IN_DIALOG);
    }

    private DatasetGrid openGrid(final FlatXmlDatasetDocument datasetDocument, final String tableKey)
    {
        return openGrid(datasetDocument, tableKey, new TestContext(datasetDocument));
    }

    private DatasetGrid openGrid(final FlatXmlDatasetDocument datasetDocument, final String tableKey,
            final TestContext context)
    {
        shell.setSize(400, 300);
        final DatasetGrid grid = new DatasetGrid(shell, context, tableKey);
        grid.tableChanged(datasetDocument.getModel().findTable(tableKey).orElseThrow());
        grid.getControl().setBounds(shell.getClientArea());
        shell.open();
        final Display display = Display.getCurrent();
        while (display.readAndDispatch())
        {
            // Let NatTable lay out before the test edits its cells.
        }
        return grid;
    }

    private static FlatXmlDatasetDocument create(final IDocument document)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }

    private static final class TestContext implements DatasetGridContext
    {
        private final DatasetDocument datasetDocument;

        private final boolean editable;

        private String lastErrorMessage;

        private String lastStatusMessage;

        TestContext(final DatasetDocument datasetDocument)
        {
            this(datasetDocument, true);
        }

        TestContext(final DatasetDocument datasetDocument, final boolean editable)
        {
            this.datasetDocument = datasetDocument;
            this.editable = editable;
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
                lastErrorMessage = null;
                return true;
            }
            catch (final DatasetEditException e)
            {
                lastErrorMessage = e.getMessage();
                return false;
            }
        }

        @Override
        public boolean executeMultiCellEdit(final String title, final Runnable edit)
        {
            return executeEdit(edit);
        }

        @Override
        public void fillContextMenu(final IMenuManager menu, final String region)
        {
        }

        @Override
        public boolean hasActiveCellEditor()
        {
            return false;
        }

        @Override
        public GridSelection getSelection()
        {
            return GridSelection.NONE;
        }

        @Override
        public void selectRegion(final int firstColumnIndex, final int firstRowIndex, final int columnCount,
                final int rowCount)
        {
        }

        @Override
        public Shell getShell()
        {
            return null;
        }

        @Override
        public void expectRename(final String oldKey, final String newKey)
        {
        }

        @Override
        public void cancelExpectedRename()
        {
        }

        @Override
        public void cancelExpectedNewTableSelected()
        {
        }

        @Override
        public void expectNewTableSelected(final String tableName)
        {
        }

        @Override
        public Text getActiveCellEditorText()
        {
            return null;
        }

        @Override
        public List<Point> getSelectedCellPositions()
        {
            return List.of();
        }

        @Override
        public void selectAll()
        {
        }

        @Override
        public void editCellInDialog()
        {
        }

        @Override
        public void showInSource()
        {
        }

        @Override
        public void setStatusMessage(final String message)
        {
            lastStatusMessage = message;
        }

        @Override
        public void setStatusErrorMessage(final String message)
        {
            lastErrorMessage = message;
        }

        @Override
        public void writeClipboardText(final String text)
        {
        }

        @Override
        public String readClipboardText()
        {
            return null;
        }
    }
}
