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
 * Tests {@link CutAction} against the Clipboard specification: the selection is copied and then cleared or
 * deleted, and a selection that cannot be copied is refused.
 */
class CutActionTest extends GridActionFixture
{
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
}
