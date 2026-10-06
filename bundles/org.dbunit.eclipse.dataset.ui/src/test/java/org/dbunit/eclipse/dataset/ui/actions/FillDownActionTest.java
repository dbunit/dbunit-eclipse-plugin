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

import java.util.List;

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.swt.graphics.Point;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FillDownAction} against the Commands specification: the rows below the top row of the
 * selection get its value, the enablement does not enumerate the selected cells, and a rejected edit is
 * reported by a dialog.
 */
class FillDownActionTest extends GridActionFixture
{
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
}
