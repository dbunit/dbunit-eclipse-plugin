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
import java.util.List;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link CellEdits}: the edits that it plans for each kind of change, applied to the text, and the
 * changes that it refuses.
 */
class CellEditsTest
{
    private static final String TEXT = "<dataset>\n" + "  <USERS ID=\"1\" NAME=\"Bob\" EMAIL=\"b@x.org\"/>\n"
            + "  <USERS ID=\"2\" NAME=\"Alice\"/>\n" + "</dataset>\n";

    private static List<TextEdit> plan(final ParsedDataset parsed, final CellChange... changes)
    {
        final DatasetTable table = parsed.model().findTable("USERS").orElseThrow();
        return new CellEdits(parsed.editContext()).plan("USERS", table, List.of(changes));
    }

    private static String result(final ParsedDataset parsed, final CellChange... changes) throws Exception
    {
        return parsed.apply(plan(parsed, changes));
    }

    @Test
    void testPlan_whenACellChanges_rewritesTheStartTagOfItsRow() throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), new CellChange(0, "NAME", "Bobby"));

        assertThat(result).as("Only the value of the changed cell must differ.")
                .isEqualTo(TEXT.replace("NAME=\"Bob\"", "NAME=\"Bobby\""));
    }

    @Test
    void testPlan_whenCellsOfTwoRowsChange_rewritesBothStartTags() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final List<TextEdit> edits =
                plan(parsed, new CellChange(0, "NAME", "Bobby"), new CellChange(1, "NAME", "Alicia"));

        assertThat(parsed.apply(edits)).as("Both rows must change.")
                .isEqualTo(TEXT.replace("\"Bob\"", "\"Bobby\"").replace("\"Alice\"", "\"Alicia\""));
        assertThat(edits).as("There must be one edit for each changed row.").hasSize(2);
    }

    @Test
    void testPlan_whenTwoCellsOfOneRowChange_makesOneEditForTheRow() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final List<TextEdit> edits =
                plan(parsed, new CellChange(0, "NAME", "x"), new CellChange(0, "EMAIL", "y"));

        assertThat(parsed.apply(edits)).as("Both cells must change.")
                .isEqualTo(TEXT.replace("\"Bob\"", "\"x\"").replace("\"b@x.org\"", "\"y\""));
        assertThat(edits).as("A row is rewritten by one edit, however many of its cells change.")
                .hasSize(1);
    }

    @Test
    void testPlan_whenACellGetsTheValueItAlreadyHas_makesNoEdit()
    {
        final List<TextEdit> edits = plan(ParsedDataset.of(TEXT), new CellChange(0, "NAME", "Bob"));

        assertThat(edits).as("A change that leaves the text as it is needs no edit.").isEmpty();
    }

    @Test
    void testPlan_whenNoChangeIsGiven_makesNoEdit()
    {
        final List<TextEdit> edits = plan(ParsedDataset.of(TEXT));

        assertThat(edits).as("Nothing to change means nothing to edit.").isEmpty();
    }

    @Test
    void testPlan_whenACellBecomesNull_removesItsAttribute() throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), new CellChange(0, "EMAIL", null));

        assertThat(result).as("A NULL cell has no attribute.")
                .isEqualTo(TEXT.replace(" EMAIL=\"b@x.org\"", ""));
    }

    @Test
    void testPlan_whenANullCellGetsAValue_addsTheAttribute() throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), new CellChange(1, "EMAIL", "a@x.org"));

        assertThat(result).as("The cell must get an attribute after the others.")
                .isEqualTo(TEXT.replace("NAME=\"Alice\"", "NAME=\"Alice\" EMAIL=\"a@x.org\""));
    }

    @Test
    void testPlan_whenTheColumnNameDiffersInCase_findsTheColumn() throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), new CellChange(0, "name", "Z"));

        assertThat(result).as("Column names are matched ignoring case.")
                .isEqualTo(TEXT.replace("NAME=\"Bob\"", "NAME=\"Z\""));
    }

    @Test
    void testPlan_whenAValueNeedsEscaping_escapesIt() throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), new CellChange(0, "NAME", "a&b<c"));

        assertThat(result).as("Markup characters must be written as entities.")
                .isEqualTo(TEXT.replace("NAME=\"Bob\"", "NAME=\"a&amp;b&lt;c\""));
    }

    @Test
    void testPlan_whenTheCharsetCannotEncodeACharacter_writesACharacterReference() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);
        final DatasetTable table = parsed.model().findTable("USERS").orElseThrow();
        final CellEdits cellEdits = new CellEdits(parsed.editContext(StandardCharsets.US_ASCII));

        final List<TextEdit> edits = cellEdits.plan("USERS", table, List.of(new CellChange(0, "NAME", "é")));

        assertThat(parsed.apply(edits)).as("The encoder of the context decides what needs a reference.")
                .isEqualTo(TEXT.replace("NAME=\"Bob\"", "NAME=\"&#xE9;\""));
    }

    @Test
    void testPlan_whenTheRowDoesNotExist_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> plan(parsed, new CellChange(2, "NAME", "x")))
                .as("A row past the last one cannot be changed.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_noSuchRow, 2, "USERS"));
        assertThatThrownBy(() -> plan(parsed, new CellChange(-1, "NAME", "x")))
                .as("A negative row cannot be changed.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_noSuchRow, -1, "USERS"));
    }

    @Test
    void testPlan_whenTheColumnDoesNotExist_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> plan(parsed, new CellChange(0, "PHONE", "x")))
                .as("A column that the table does not have cannot be changed.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_noSuchColumn, "PHONE", "USERS"));
    }

    @Test
    void testPlan_whenTheChangesWouldLeaveARowWithoutValues_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> plan(parsed, new CellChange(1, "ID", null), new CellChange(1, "NAME", null)))
                .as("A row must keep at least one value.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_rowWouldBeEmpty, 1, "USERS"));
    }

    @Test
    void testPlan_whenOneCellBecomesNullAndAnotherKeepsItsValue_isAllowed() throws Exception
    {
        final String result = result(ParsedDataset.of(TEXT), new CellChange(1, "NAME", null));

        assertThat(result).as("A row that keeps a value may lose another.")
                .isEqualTo(TEXT.replace("<USERS ID=\"2\" NAME=\"Alice\"/>", "<USERS ID=\"2\"/>"));
    }

    @Test
    void testPlan_whenARowHasTwoAttributesForTheColumnThatDifferOnlyInCase_throwsDatasetEditException()
    {
        final ParsedDataset parsed =
                ParsedDataset.of("<dataset>\n  <USERS ID=\"1\" id=\"x\" NAME=\"Bob\"/>\n</dataset>\n");

        assertThatThrownBy(() -> plan(parsed, new CellChange(0, "ID", "5")))
                .as("Changing one of two spellings would leave the other one behind.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_cellHasCaseVariantAttributes,
                        new Object[] { "ID", 0, "USERS" }));
    }
}
