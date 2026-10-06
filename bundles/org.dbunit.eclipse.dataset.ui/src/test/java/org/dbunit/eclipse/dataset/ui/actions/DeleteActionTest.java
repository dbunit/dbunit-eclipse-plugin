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
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DeleteAction} against the Commands specification: the selected cells are set to NULL, whole
 * rows are deleted, the note about a default value, and the text of a cell editor is edited instead.
 */
class DeleteActionTest extends GridActionFixture
{
    @Test
    void testDelete_ofSelectedCells_setsThemToNull()
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
}
