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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ColumnEdits}: the names that it accepts, the edits that it plans to rename and delete
 * columns, and the columns that it refuses to change.
 */
class ColumnEditsTest
{
    private static final String TEXT = "<dataset>\n" + "  <USERS ID=\"1\" NAME=\"Bob\" EMAIL=\"b@x.org\"/>\n"
            + "  <USERS ID=\"2\" NAME=\"Alice\"/>\n" + "  <PETS NAME=\"Rex\"/>\n" + "</dataset>\n";

    private static DatasetTable usersOf(final ParsedDataset parsed)
    {
        return parsed.model().findTable("USERS").orElseThrow();
    }

    private static String renamed(final ParsedDataset parsed, final String columnName,
            final String newColumnName) throws Exception
    {
        final DatasetTable table = usersOf(parsed);
        final ColumnEdits columnEdits = new ColumnEdits(parsed.editContext());
        final DatasetColumn column = columnEdits.requireRename("USERS", table, columnName, newColumnName);
        return parsed.apply(columnEdits.renameEdits("USERS", table, column, columnName, newColumnName));
    }

    private static List<TextEdit> deletionPlan(final ParsedDataset parsed, final String columnName)
    {
        final DatasetTable table = usersOf(parsed);
        final DatasetColumn column = ColumnEdits.requireExistingColumn(table, columnName);
        return new ColumnEdits(parsed.editContext()).deleteEdits("USERS", table, column, columnName);
    }

    @Test
    void testRequireNewColumn_whenTheNameIsFreeAndValid_accepts()
    {
        final DatasetTable table = usersOf(ParsedDataset.of(TEXT));

        assertThatCode(() -> ColumnEdits.requireNewColumn(table, "PHONE")).as("A new valid name is fine.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireNewColumn_whenTheNameIsNotAValidXmlName_throwsDatasetEditException()
    {
        final DatasetTable table = usersOf(ParsedDataset.of(TEXT));

        for (final String invalid : List.of("1bad", "has space", ""))
        {
            assertThatThrownBy(() -> ColumnEdits.requireNewColumn(table, invalid))
                    .as("'" + invalid + "' cannot be an attribute name.")
                    .isInstanceOf(DatasetEditException.class)
                    .hasMessage(NLS.bind(Messages.Edit_invalidColumnName, invalid));
        }
    }

    @Test
    void testRequireNewColumn_whenAColumnHasTheNameInAnotherCase_throwsDatasetEditException()
    {
        final DatasetTable table = usersOf(ParsedDataset.of(TEXT));

        assertThatThrownBy(() -> ColumnEdits.requireNewColumn(table, "email"))
                .as("Column names are compared ignoring case.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_columnExists, "USERS", "email"));
    }

    @Test
    void testRequireExistingColumn_whenTheNameDiffersInCase_returnsTheColumn()
    {
        final DatasetTable table = usersOf(ParsedDataset.of(TEXT));

        final DatasetColumn column = ColumnEdits.requireExistingColumn(table, "name");

        assertThat(column).as("The column must be found ignoring case.")
                .isEqualTo(new DatasetColumn("NAME", false, true, false));
    }

    @Test
    void testRequireExistingColumn_whenThereIsNoSuchColumn_throwsDatasetEditException()
    {
        final DatasetTable table = usersOf(ParsedDataset.of(TEXT));

        assertThatThrownBy(() -> ColumnEdits.requireExistingColumn(table, "PHONE"))
                .as("A column that the table lacks cannot be found.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_noSuchColumn, "PHONE", "USERS"));
    }

    @Test
    void testRequireRename_whenTheRenameIsValid_returnsTheColumn()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final DatasetColumn column = new ColumnEdits(parsed.editContext()).requireRename("USERS",
                usersOf(parsed), "NAME", "FULLNAME");

        assertThat(column).as("The column to rename must be returned.")
                .isEqualTo(new DatasetColumn("NAME", false, true, false));
    }

    @Test
    void testRequireRename_whenOnlyTheLetterCaseChanges_accepts()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);
        final ColumnEdits columnEdits = new ColumnEdits(parsed.editContext());
        final DatasetTable table = usersOf(parsed);

        assertThatCode(() -> columnEdits.requireRename("USERS", table, "NAME", "name"))
                .as("A column may take another spelling of its own name.").doesNotThrowAnyException();
    }

    @Test
    void testRequireRename_whenTheNewNameBelongsToAnotherColumn_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);
        final ColumnEdits columnEdits = new ColumnEdits(parsed.editContext());
        final DatasetTable table = usersOf(parsed);

        assertThatThrownBy(() -> columnEdits.requireRename("USERS", table, "NAME", "email"))
                .as("Two columns cannot share a name.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_columnExists, "USERS", "email"));
    }

    @Test
    void testRequireRename_whenTheNewNameIsInvalid_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);
        final ColumnEdits columnEdits = new ColumnEdits(parsed.editContext());
        final DatasetTable table = usersOf(parsed);

        assertThatThrownBy(() -> columnEdits.requireRename("USERS", table, "NAME", "1bad"))
                .as("The new name must be a valid XML name.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_invalidColumnName, "1bad"));
    }

    @Test
    void testRequireRename_whenTheColumnDoesNotExist_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);
        final ColumnEdits columnEdits = new ColumnEdits(parsed.editContext());
        final DatasetTable table = usersOf(parsed);

        assertThatThrownBy(() -> columnEdits.requireRename("USERS", table, "PHONE", "MOBILE"))
                .as("Only a column of the table can be renamed.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_noSuchColumn, "PHONE", "USERS"));
    }

    @Test
    void testRequireRename_whenARowSpellsTheColumnInTwoWays_throwsDatasetEditException()
    {
        final ParsedDataset parsed =
                ParsedDataset.of("<dataset>\n  <USERS ID=\"1\" NAME=\"Bob\" name=\"x\"/>\n</dataset>\n");
        final ColumnEdits columnEdits = new ColumnEdits(parsed.editContext());
        final DatasetTable table = usersOf(parsed);

        assertThatThrownBy(() -> columnEdits.requireRename("USERS", table, "NAME", "FULLNAME"))
                .as("Renaming one spelling would leave the other one behind.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_renameColumnWithCaseVariants, "NAME", "USERS"));
    }

    @Test
    void testRenameEdits_whenRowsHaveTheColumn_rewritesTheirStartTagsOnly() throws Exception
    {
        final String result = renamed(ParsedDataset.of(TEXT), "NAME", "FULLNAME");

        assertThat(result).as("The USERS rows must change, and the PETS row, which has a NAME too, must not.")
                .isEqualTo(TEXT.replace("NAME=\"Bob\"", "FULLNAME=\"Bob\"").replace("NAME=\"Alice\"",
                        "FULLNAME=\"Alice\""));
    }

    @Test
    void testRenameEdits_whenTheColumnIsOnlyDeclared_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.withDtd(TEXT, "<!ELEMENT dataset (USERS*, PETS*)>"
                + "<!ELEMENT USERS EMPTY><!ATTLIST USERS ID CDATA #IMPLIED NAME CDATA #IMPLIED "
                + "EMAIL CDATA #IMPLIED PHONE CDATA #IMPLIED><!ELEMENT PETS EMPTY>");

        assertThatThrownBy(() -> renamed(parsed, "PHONE", "MOBILE"))
                .as("A column that no row has cannot be renamed in the text.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_renameColumnIsDeclaredOnly, "PHONE", "USERS"));
    }

    @Test
    void testDeleteEdits_whenOneRowHasTheColumn_rewritesOnlyThatStartTag() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final List<TextEdit> edits = deletionPlan(parsed, "EMAIL");

        assertThat(parsed.apply(edits)).as("The attribute must be removed from the row that has it.")
                .isEqualTo(TEXT.replace(" EMAIL=\"b@x.org\"", ""));
        assertThat(edits).as("A row without the column needs no edit.").hasSize(1);
    }

    @Test
    void testDeleteEdits_whenEveryRowHasTheColumn_rewritesEachStartTag() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final List<TextEdit> edits = deletionPlan(parsed, "name");

        assertThat(parsed.apply(edits)).as("The column must go from every USERS row, ignoring case.")
                .isEqualTo(TEXT.replace(" NAME=\"Bob\"", "").replace(" NAME=\"Alice\"", ""));
    }

    @Test
    void testDeleteEdits_whenARowHasNoOtherColumn_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset
                .of("<dataset>\n  <USERS ID=\"1\" NAME=\"Bob\"/>\n  <USERS NAME=\"Alice\"/>\n</dataset>\n");

        assertThatThrownBy(() -> deletionPlan(parsed, "NAME"))
                .as("Deleting the only column of a row would leave an element without attributes.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_deleteColumnWouldEmptyRow, "NAME", "USERS"));
    }

    @Test
    void testRequireNewColumnNames_whenTheNamesAreValidAndDistinct_accepts()
    {
        assertThatCode(() -> ColumnEdits.requireNewColumnNames("USERS", List.of("ID", "NAME")))
                .as("Distinct valid names are fine.").doesNotThrowAnyException();
    }

    @Test
    void testRequireNewColumnNames_whenThereAreNoNames_accepts()
    {
        assertThatCode(() -> ColumnEdits.requireNewColumnNames("USERS", List.of()))
                .as("A table may start without columns.").doesNotThrowAnyException();
    }

    @Test
    void testRequireNewColumnNames_whenANameIsInvalid_throwsDatasetEditException()
    {
        assertThatThrownBy(() -> ColumnEdits.requireNewColumnNames("USERS", List.of("ID", "1bad")))
                .as("Every name must be a valid XML name.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_invalidColumnName, "1bad"));
    }

    @Test
    void testRequireNewColumnNames_whenANameRepeatsInAnotherCase_throwsDatasetEditException()
    {
        assertThatThrownBy(() -> ColumnEdits.requireNewColumnNames("USERS", List.of("ID", "id")))
                .as("Names are compared ignoring case, and the second spelling is reported.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_columnExists, "USERS", "id"));
    }
}
