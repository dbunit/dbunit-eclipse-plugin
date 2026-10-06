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

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.eclipse.swt.graphics.Rectangle;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link InsertRowAboveAction} and {@link InsertRowBelowAction}, which share {@link InsertRowAction},
 * against the Commands specification: where the new row goes, which cells it fills, and the rule for the
 * first row.
 */
class InsertRowActionTest extends GridActionFixture
{
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
    void testUpdate_whenThePageGoesFromEditableToReadOnly_enablesThenDisablesTheAction()
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
}
