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
 * Tests {@link SetNullAction} against the Commands specification: the rule for the first row, and the note
 * on the status line about the columns to which the DTD gives a default value.
 */
class SetNullActionTest extends GridActionFixture
{
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
}
