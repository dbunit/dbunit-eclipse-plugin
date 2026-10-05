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
package org.dbunit.eclipse.dataset.core.flatxml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link RowEdits}: the edits that it plans to duplicate, delete, and move rows, applied to the text,
 * and the selections and moves that it refuses.
 */
class RowEditsTest
{
    private static final String BOB = "  <USERS ID=\"1\" NAME=\"Bob\"/>\n";

    private static final String ALICE = "  <USERS ID=\"2\" NAME=\"Alice\"/>\n";

    private static final String CARL = "  <USERS ID=\"3\" NAME=\"Carl\"/>\n";

    private static final String ORDER = "  <ORDERS ID=\"10\"/>\n";

    private static final String TEXT = dataset(BOB, ALICE, CARL, ORDER);

    private static String dataset(final String... lines)
    {
        return "<dataset>\n" + String.join("", lines) + "</dataset>\n";
    }

    private static RowEdits rowEditsFor(final ParsedDataset parsed)
    {
        return new RowEdits(parsed.editContext());
    }

    private static DatasetTable usersOf(final ParsedDataset parsed)
    {
        return parsed.model().findTable("USERS").orElseThrow();
    }

    private static String duplicated(final String text, final int... rowIndexes) throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(text);
        return parsed.apply(rowEditsFor(parsed).duplicateEdits("USERS", usersOf(parsed), rowIndexes));
    }

    private static String deleted(final String text, final int... rowIndexes) throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(text);
        return parsed.apply(rowEditsFor(parsed).deleteEdits("USERS", usersOf(parsed), rowIndexes));
    }

    private static String moved(final int firstRowIndex, final int rowCount, final int delta) throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);
        final List<TextEdit> edits =
                rowEditsFor(parsed).moveEdits("USERS", usersOf(parsed), firstRowIndex, rowCount, delta);
        return parsed.apply(edits);
    }

    @Test
    void testDuplicateEdits_whenOneRowIsGiven_insertsACopyRightAfterIt() throws Exception
    {
        assertThat(duplicated(TEXT, 0)).as("The copy must follow its row on a line of its own.")
                .isEqualTo(dataset(BOB, BOB, ALICE, CARL, ORDER));
    }

    @Test
    void testDuplicateEdits_whenSeveralRowsAreGiven_insertsTheCopiesAfterTheLastOfThem() throws Exception
    {
        assertThat(duplicated(TEXT, 0, 2)).as("The copies go after the last selected row, in row order.")
                .isEqualTo(dataset(BOB, ALICE, CARL, BOB, CARL, ORDER));
    }

    @Test
    void testDuplicateEdits_whenTheRowsAreNotInOrder_copiesThemInRowOrder() throws Exception
    {
        assertThat(duplicated(TEXT, 2, 0)).as("The selection is sorted first.")
                .isEqualTo(dataset(BOB, ALICE, CARL, BOB, CARL, ORDER));
    }

    @Test
    void testDuplicateEdits_whenARowIsGivenTwice_copiesItTwice() throws Exception
    {
        assertThat(duplicated(TEXT, 1, 1)).as("A repeated row index is not removed.")
                .isEqualTo(dataset(BOB, ALICE, ALICE, ALICE, CARL, ORDER));
    }

    @Test
    void testDuplicateEdits_whenNoRowIsGiven_makesNoEdit()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final List<TextEdit> edits = rowEditsFor(parsed).duplicateEdits("USERS", usersOf(parsed), new int[0]);

        assertThat(edits).as("Nothing selected means nothing to copy.").isEmpty();
    }

    @Test
    void testDuplicateEdits_whenARowDoesNotExist_throwsDatasetEditException()
    {
        assertThatThrownBy(() -> duplicated(TEXT, 3)).as("Only rows of the table can be copied.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_rowOutOfRange, 3, "USERS"));
    }

    @Test
    void testDeleteEdits_whenOneRowIsGiven_removesItsWholeLine() throws Exception
    {
        assertThat(deleted(TEXT, 1)).as("No blank line may be left behind.")
                .isEqualTo(dataset(BOB, CARL, ORDER));
    }

    @Test
    void testDeleteEdits_whenSeveralRowsAreGiven_removesEachLine() throws Exception
    {
        assertThat(deleted(TEXT, 2, 0)).as("Every selected row must go, whatever the order of the selection.")
                .isEqualTo(dataset(ALICE, ORDER));
    }

    @Test
    void testDeleteEdits_whenEveryRowOfATableWithoutMarkerIsGiven_leavesAnEmptyElement() throws Exception
    {
        assertThat(deleted(TEXT, 0, 1, 2)).as("The table must stay in the document as an empty element.")
                .isEqualTo(dataset("  <USERS/>\n", ORDER));
    }

    @Test
    void testDeleteEdits_whenEveryRowOfATableWithAMarkerIsGiven_removesAllTheRows() throws Exception
    {
        final String text = dataset("  <USERS ID=\"1\"/>\n", "  <USERS/>\n");

        assertThat(deleted(text, 0)).as("The marker already keeps the table in the document.")
                .isEqualTo(dataset("  <USERS/>\n"));
    }

    @Test
    void testDeleteEdits_whenEveryRowOfATableWithDefaultValuesIsGiven_leavesNoEmptyElement() throws Exception
    {
        final String text = dataset("  <USERS ID=\"1\"/>\n", "  <USERS ID=\"2\"/>\n", ORDER);
        final ParsedDataset parsed = ParsedDataset.withDtd(text,
                "<!ELEMENT dataset (USERS*, ORDERS*)>\n<!ELEMENT USERS EMPTY>\n<!ELEMENT ORDERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED STATUS CDATA \"ACTIVE\">\n"
                        + "<!ATTLIST ORDERS ID CDATA #REQUIRED>");

        final List<TextEdit> edits =
                rowEditsFor(parsed).deleteEdits("USERS", usersOf(parsed), new int[] { 0, 1 });

        assertThat(parsed.apply(edits)).as("An empty element would be a row of default values in dbUnit, "
                + "so every row must go; the DTD keeps the table.").isEqualTo(dataset(ORDER));
    }

    @Test
    void testDeleteEdits_whenAnEmptyElementIsARowOfDefaultValues_removesItsLineLikeAnyRow() throws Exception
    {
        final String text = dataset("  <USERS ID=\"1\"/>\n", "  <USERS/>\n");
        final ParsedDataset parsed = ParsedDataset.withDtd(text,
                "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED STATUS CDATA \"ACTIVE\">");

        final List<TextEdit> edits =
                rowEditsFor(parsed).deleteEdits("USERS", usersOf(parsed), new int[] { 1 });

        assertThat(parsed.apply(edits)).as("The empty element is the table's second row.")
                .isEqualTo(dataset("  <USERS ID=\"1\"/>\n"));
    }

    @Test
    void testDeleteEdits_whenTheRowSharesItsLine_removesTheElementAlone() throws Exception
    {
        final String text = "<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>\n";

        assertThat(deleted(text, 0)).as("A row on a shared line must not take the line with it.")
                .isEqualTo("<dataset><USERS ID=\"2\"/></dataset>\n");
    }

    @Test
    void testDeleteEdits_whenNoRowIsGiven_makesNoEdit()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final List<TextEdit> edits = rowEditsFor(parsed).deleteEdits("USERS", usersOf(parsed), new int[0]);

        assertThat(edits).as("Nothing selected means nothing to delete.").isEmpty();
    }

    @Test
    void testDeleteEdits_whenARowDoesNotExist_throwsDatasetEditException()
    {
        assertThatThrownBy(() -> deleted(TEXT, -1)).as("Only rows of the table can be deleted.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_rowOutOfRange, -1, "USERS"));
    }

    @Test
    void testMoveEdits_whenABlockMovesDown_swapsItWithTheRowBelow() throws Exception
    {
        assertThat(moved(0, 2, 1)).as("The block of two rows must pass the third row.")
                .isEqualTo(dataset(CARL, BOB, ALICE, ORDER));
    }

    @Test
    void testMoveEdits_whenARowMovesUp_swapsItWithTheRowAbove() throws Exception
    {
        assertThat(moved(1, 1, -1)).as("The second row must pass the first.")
                .isEqualTo(dataset(ALICE, BOB, CARL, ORDER));
    }

    @Test
    void testMoveEdits_whenABlockMovesUp_swapsItWithTheRowAbove() throws Exception
    {
        assertThat(moved(1, 2, -1)).as("The block of two rows must pass the first row.")
                .isEqualTo(dataset(ALICE, CARL, BOB, ORDER));
    }

    @Test
    void testMoveEdits_whenTheBlockIsNotInTheTable_throwsDatasetEditException()
    {
        final String message = NLS.bind(Messages.Edit_rowBlockOutOfRange, "USERS");

        assertThatThrownBy(() -> moved(-1, 1, 1)).as("The block cannot start before the first row.")
                .isInstanceOf(DatasetEditException.class).hasMessage(message);
        assertThatThrownBy(() -> moved(0, 0, 1)).as("The block cannot be empty.")
                .isInstanceOf(DatasetEditException.class).hasMessage(message);
        assertThatThrownBy(() -> moved(2, 2, -1)).as("The block cannot reach past the last row.")
                .isInstanceOf(DatasetEditException.class).hasMessage(message);
    }

    @Test
    void testMoveEdits_whenTheDistanceIsNotOnePosition_throwsDatasetEditException()
    {
        assertThatThrownBy(() -> moved(1, 1, 2)).as("Rows move by one position at a time.")
                .isInstanceOf(DatasetEditException.class).hasMessage(Messages.Edit_moveByOnePosition);
        assertThatThrownBy(() -> moved(1, 1, 0)).as("Standing still is not a move.")
                .isInstanceOf(DatasetEditException.class).hasMessage(Messages.Edit_moveByOnePosition);
    }

    @Test
    void testMoveEdits_whenTheFirstRowMovesUp_throwsDatasetEditException()
    {
        assertThatThrownBy(() -> moved(0, 1, -1)).as("There is no row above the first one.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_moveBeforeFirstRow, "USERS"));
    }

    @Test
    void testMoveEdits_whenTheLastRowMovesDown_throwsDatasetEditException()
    {
        assertThatThrownBy(() -> moved(2, 1, 1)).as("There is no row below the last one.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_moveAfterLastRow, "USERS"));
    }
}
