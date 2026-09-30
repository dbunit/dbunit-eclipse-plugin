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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link RowInsertEdits}: where new rows go in each situation, how they are written, and the
 * positions and rows that it refuses.
 */
class RowInsertEditsTest
{
    private static final String TEXT = "<dataset>\n" + "  <USERS ID=\"1\" NAME=\"Bob\"/>\n"
            + "  <USERS ID=\"2\" NAME=\"Alice\"/>\n" + "  <ORDERS ID=\"10\"/>\n" + "</dataset>\n";

    private static List<String> row(final String... values)
    {
        return Arrays.asList(values);
    }

    private static String beforeBob(final String lines)
    {
        return TEXT.replace("  <USERS ID=\"1\"", lines + "  <USERS ID=\"1\"");
    }

    private static String beforeAlice(final String lines)
    {
        return TEXT.replace("  <USERS ID=\"2\"", lines + "  <USERS ID=\"2\"");
    }

    private static String afterAlice(final String lines)
    {
        return TEXT.replace("NAME=\"Alice\"/>\n", "NAME=\"Alice\"/>\n" + lines);
    }

    private static List<TextEdit> plan(final ParsedDataset parsed, final String tableKey, final int rowIndex,
            final List<List<String>> rows)
    {
        final DatasetTable table = parsed.model().findTable(tableKey).orElseThrow();
        return new RowInsertEdits(parsed.editContext()).plan(tableKey, table, rowIndex, rows);
    }

    private static String result(final ParsedDataset parsed, final String tableKey, final int rowIndex,
            final List<List<String>> rows) throws Exception
    {
        return parsed.apply(plan(parsed, tableKey, rowIndex, rows));
    }

    @Test
    void testPlan_whenInsertingBeforeTheFirstRow_putsTheRowOnItsOwnLineWithTheSameIndentation()
            throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), "USERS", 0, List.of(row("3", "Carl")));

        assertThat(result).as("The new row must come before the first row, indented like it.")
                .isEqualTo(beforeBob("  <USERS ID=\"3\" NAME=\"Carl\"/>\n"));
    }

    @Test
    void testPlan_whenInsertingBetweenRows_putsTheRowBeforeTheRowAtThatIndex() throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), "USERS", 1, List.of(row("3", "Carl")));

        assertThat(result).as("The new row must take the place of the second row, which moves down.")
                .isEqualTo(beforeAlice("  <USERS ID=\"3\" NAME=\"Carl\"/>\n"));
    }

    @Test
    void testPlan_whenInsertingAfterTheLastRowOfTheTable_putsTheRowRightAfterIt() throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), "USERS", 2, List.of(row("3", "Carl")));

        assertThat(result).as("The new row must follow the table's last row, not the end of the document.")
                .isEqualTo(afterAlice("  <USERS ID=\"3\" NAME=\"Carl\"/>\n"));
    }

    @Test
    void testPlan_whenSeveralRowsAreInserted_writesThemInOrderBeforeTheAnchor() throws Exception
    {
        final String result =
                result(ParsedDataset.of(TEXT), "USERS", 1, List.of(row("3", "Carl"), row("4", "Dora")));

        assertThat(result).as("The rows must keep the order that they were given in.")
                .isEqualTo(beforeAlice(
                        "  <USERS ID=\"3\" NAME=\"Carl\"/>\n  <USERS ID=\"4\" NAME=\"Dora\"/>\n"));
    }

    @Test
    void testPlan_whenAValueIsNull_leavesItsAttributeOut() throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), "USERS", 2, List.of(row("3", null)));

        assertThat(result).as("A NULL cell has no attribute.")
                .isEqualTo(TEXT.replace("NAME=\"Alice\"/>\n", "NAME=\"Alice\"/>\n  <USERS ID=\"3\"/>\n"));
    }

    @Test
    void testPlan_whenAValueNeedsEscaping_escapesIt() throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), "USERS", 2, List.of(row("3", "a&b<c")));

        assertThat(result).as("Markup characters must be written as entities.")
                .isEqualTo(TEXT.replace("NAME=\"Alice\"/>\n",
                        "NAME=\"Alice\"/>\n  <USERS ID=\"3\" NAME=\"a&amp;b&lt;c\"/>\n"));
    }

    @Test
    void testPlan_whenTheCharsetCannotEncodeACharacter_writesACharacterReference() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);
        final DatasetTable table = parsed.model().findTable("USERS").orElseThrow();
        final EditContext asciiContext = parsed.editContext(StandardCharsets.US_ASCII);

        final RowInsertEdits rowInsertEdits = new RowInsertEdits(asciiContext);

        final List<TextEdit> edits = rowInsertEdits.plan("USERS", table, 2, List.of(row("3", "é")));

        assertThat(parsed.apply(edits)).as("The encoder of the context decides what needs a reference.")
                .isEqualTo(afterAlice("  <USERS ID=\"3\" NAME=\"&#xE9;\"/>\n"));
    }

    @Test
    void testPlan_whenTheLineDelimiterIsCrLf_usesItBetweenTheLines() throws Exception
    {
        final String text = TEXT.replace("\n", "\r\n");
        final ParsedDataset parsed = ParsedDataset.of(text, FlatXmlOptions.DBUNIT_DEFAULTS, "\r\n");

        final String result = result(parsed, "USERS", 2, List.of(row("3", "Carl")));

        assertThat(result).as("The new line must end like the others.")
                .isEqualTo(text.replace("NAME=\"Alice\"/>\r\n",
                        "NAME=\"Alice\"/>\r\n  <USERS ID=\"3\" NAME=\"Carl\"/>\r\n"));
    }

    @Test
    void testPlan_whenTheTableIsOnlyAMarker_replacesTheMarkerWithTheRows() throws Exception
    {
        final String text = "<dataset>\n  <USERS ID=\"1\"/>\n  <ORDERS/>\n</dataset>\n";
        final ParsedDataset parsed = ParsedDataset.withDtd(text,
                "<!ELEMENT dataset (USERS*, ORDERS*)><!ELEMENT USERS EMPTY><!ELEMENT ORDERS EMPTY>"
                        + "<!ATTLIST ORDERS ID CDATA #IMPLIED TOTAL CDATA #IMPLIED>");

        final String result = result(parsed, "ORDERS", 0, List.of(row("7", "9"), row("8", "5")));

        assertThat(result).as("The marker element must give way to the rows.")
                .isEqualTo("<dataset>\n  <USERS ID=\"1\"/>\n  <ORDERS ID=\"7\" TOTAL=\"9\"/>\n"
                        + "  <ORDERS ID=\"8\" TOTAL=\"5\"/>\n</dataset>\n");
    }

    @Test
    void testPlan_whenTheTableIsOnlyDeclared_putsTheRowsAtTheEndOfTheDataset() throws Exception
    {
        final String text = "<dataset>\n  <USERS ID=\"1\"/>\n</dataset>\n";
        final ParsedDataset parsed = ParsedDataset.withDtd(text,
                "<!ELEMENT dataset (USERS*, ORDERS*)><!ELEMENT USERS EMPTY><!ELEMENT ORDERS EMPTY>"
                        + "<!ATTLIST ORDERS ID CDATA #IMPLIED>");

        final String result = result(parsed, "ORDERS", 0, List.of(row("7")));

        assertThat(result).as("A table without elements gets its rows as the last children of the root.")
                .isEqualTo("<dataset>\n  <USERS ID=\"1\"/>\n  <ORDERS ID=\"7\"/>\n</dataset>\n");
    }

    @Test
    void testPlan_whenNoRowIsGiven_makesNoEdit()
    {
        final List<TextEdit> edits = plan(ParsedDataset.of(TEXT), "USERS", 1, List.of());

        assertThat(edits).as("Nothing to insert means nothing to edit.").isEmpty();
    }

    @Test
    void testPlan_whenTheRowIndexIsOutOfRange_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> plan(parsed, "USERS", -1, List.of(row("3", "Carl"))))
                .as("A negative position cannot be used.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_rowOutOfRange, -1, "USERS"));
        assertThatThrownBy(() -> plan(parsed, "USERS", 3, List.of(row("3", "Carl"))))
                .as("A position past the end of the table cannot be used.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_rowOutOfRange, 3, "USERS"));
    }

    @Test
    void testPlan_whenTheTableHasNoColumns_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.withDtd("<dataset/>",
                "<!ELEMENT dataset (BARE*)><!ELEMENT BARE EMPTY>");

        assertThatThrownBy(() -> plan(parsed, "BARE", 0, List.of(row())))
                .as("A row cannot be written without columns.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_tableHasNoColumns, "BARE"));
    }

    @Test
    void testPlan_whenARowHasTheWrongNumberOfValues_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> plan(parsed, "USERS", 0, List.of(row("3"))))
                .as("A row needs one value for each column.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_wrongValueCount, 2, "USERS"));
    }

    @Test
    void testPlan_whenARowHasOnlyNullValues_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> plan(parsed, "USERS", 0, List.of(row(null, null))))
                .as("A row without a value would be an element without attributes.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_newRowWouldBeEmpty, "USERS"));
    }
}
