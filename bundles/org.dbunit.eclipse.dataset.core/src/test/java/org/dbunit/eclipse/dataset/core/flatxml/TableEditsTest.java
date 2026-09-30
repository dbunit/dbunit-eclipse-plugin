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
import java.util.Locale;
import java.util.function.UnaryOperator;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link TableEdits}: the edits that it plans to add, rename, and delete tables, the table names that
 * it accepts under a case-insensitive and a case-sensitive key rule, and the tables that it refuses to
 * change.
 */
class TableEditsTest
{
    private static final UnaryOperator<String> INSENSITIVE = name -> name.toUpperCase(Locale.ENGLISH);

    private static final UnaryOperator<String> SENSITIVE = UnaryOperator.identity();

    private static final String USERS_ROWS =
            "  <USERS ID=\"1\" NAME=\"Bob\"/>\n  <USERS ID=\"2\" NAME=\"Alice\"/>\n";

    private static final String ORDER = "  <ORDERS ID=\"10\"/>\n";

    private static final String TEXT = "<dataset>\n" + USERS_ROWS + ORDER + "</dataset>\n";

    private static TableEdits tableEditsFor(final ParsedDataset parsed,
            final UnaryOperator<String> tableKeyOf)
    {
        return new TableEdits(parsed.editContext(), parsed.model(), tableKeyOf);
    }

    private static List<TextEdit> add(final ParsedDataset parsed, final String tableName,
            final List<String> columnNames)
    {
        return tableEditsFor(parsed, INSENSITIVE).addEdits(tableName, columnNames);
    }

    private static List<TextEdit> rename(final ParsedDataset parsed, final UnaryOperator<String> tableKeyOf,
            final String tableKey, final String newTableName)
    {
        final DatasetTable table = parsed.model().findTable(tableKey).orElseThrow();
        return tableEditsFor(parsed, tableKeyOf).renameEdits(tableKey, table, newTableName);
    }

    private static List<TextEdit> delete(final ParsedDataset parsed, final String tableKey)
    {
        final DatasetTable table = parsed.model().findTable(tableKey).orElseThrow();
        return tableEditsFor(parsed, INSENSITIVE).deleteEdits(tableKey, table);
    }

    @Test
    void testAddEdits_whenTheNameIsFree_insertsAnEmptyElementAtTheEndOfTheDataset() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final List<TextEdit> edits = add(parsed, "PETS", List.of("ID"));

        assertThat(parsed.apply(edits)).as("The new table is an empty element after the last one.")
                .isEqualTo("<dataset>\n" + USERS_ROWS + ORDER + "  <PETS/>\n</dataset>\n");
    }

    @Test
    void testAddEdits_whenTheDatasetIsSelfClosing_opensTheDataset() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of("<dataset/>\n");

        final List<TextEdit> edits = add(parsed, "PETS", List.of());

        assertThat(parsed.apply(edits)).as("The dataset must get its own end tag around the new table.")
                .isEqualTo("<dataset>\n  <PETS/>\n</dataset>\n");
    }

    @Test
    void testAddEdits_whenTheNameIsNotAValidXmlName_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        for (final String invalid : List.of("1bad", "has space", ""))
        {
            assertThatThrownBy(() -> add(parsed, invalid, List.of()))
                    .as("'" + invalid + "' cannot be an element name.")
                    .isInstanceOf(DatasetEditException.class)
                    .hasMessage(NLS.bind(Messages.Edit_invalidTableName, invalid));
        }
    }

    @Test
    void testAddEdits_whenTheNameIsTheRootsName_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> add(parsed, "dataset", List.of())).as("The root's own name is reserved.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_reservedTableName, "dataset"));
        assertThatThrownBy(() -> add(parsed, "DATASET", List.of()))
                .as("The root's name is reserved in any letter case when names are case-insensitive.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_reservedTableName, "DATASET"));
    }

    @Test
    void testAddEdits_whenATableHasTheNameInAnotherCase_throwsDatasetEditExceptionIfNamesAreCaseInsensitive()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> add(parsed, "users", List.of())).as("USERS and users are one table.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_tableExists, "users"));
    }

    @Test
    void testAddEdits_whenATableHasTheNameInAnotherCase_acceptsItIfNamesAreCaseSensitive() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT, new FlatXmlOptions(true, false), "\n");

        final List<TextEdit> edits = tableEditsFor(parsed, SENSITIVE).addEdits("users", List.of());

        assertThat(parsed.apply(edits)).as("USERS and users are two tables when names are case-sensitive.")
                .isEqualTo("<dataset>\n" + USERS_ROWS + ORDER + "  <users/>\n</dataset>\n");
    }

    @Test
    void testAddEdits_whenAColumnNameIsInvalid_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> add(parsed, "PETS", List.of("ID", "1bad")))
                .as("The columns of a new table must have valid names.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_invalidColumnName, "1bad"));
    }

    @Test
    void testAddEdits_whenTheTableNameAndAColumnNameAreInvalid_reportsTheTableFirst()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> add(parsed, "1bad", List.of("2bad")))
                .as("The table's name is checked before its columns.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_invalidTableName, "1bad"));
    }

    @Test
    void testRenameEdits_whenElementsAreSelfClosing_renamesEachStartTag() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final List<TextEdit> edits = rename(parsed, INSENSITIVE, "USERS", "PEOPLE");

        assertThat(parsed.apply(edits)).as("Both USERS rows must become PEOPLE rows.")
                .isEqualTo(TEXT.replace("USERS", "PEOPLE"));
    }

    @Test
    void testRenameEdits_whenAnElementHasAnEndTag_renamesBothTags() throws Exception
    {
        final String text = "<dataset>\n  <USERS ID=\"1\"></USERS>\n  <USERS ID=\"2\"/>\n</dataset>\n";
        final ParsedDataset parsed = ParsedDataset.of(text);

        final List<TextEdit> edits = rename(parsed, INSENSITIVE, "USERS", "PEOPLE");

        assertThat(parsed.apply(edits)).as("The end tag must follow the start tag.")
                .isEqualTo(text.replace("USERS", "PEOPLE"));
    }

    @Test
    void testRenameEdits_whenTheTableHasAMarkerElement_renamesItToo() throws Exception
    {
        final String text = "<dataset>\n  <USERS ID=\"1\"/>\n  <USERS/>\n</dataset>\n";
        final ParsedDataset parsed = ParsedDataset.of(text);

        final List<TextEdit> edits = rename(parsed, INSENSITIVE, "USERS", "PEOPLE");

        assertThat(parsed.apply(edits)).as("Every element of the table must take the new name.")
                .isEqualTo(text.replace("USERS", "PEOPLE"));
    }

    @Test
    void testRenameEdits_whenOnlyTheLetterCaseChanges_renamesTheElements() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final List<TextEdit> edits = rename(parsed, INSENSITIVE, "USERS", "Users");

        assertThat(parsed.apply(edits)).as("A table may take another spelling of its own name.")
                .isEqualTo(TEXT.replace("USERS", "Users"));
    }

    @Test
    void testRenameEdits_whenTheNewNameBelongsToAnotherTable_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> rename(parsed, INSENSITIVE, "USERS", "orders"))
                .as("Two tables cannot share a name.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_tableExists, "orders"));
    }

    @Test
    void testRenameEdits_whenTheNewNameIsInvalidOrReserved_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        assertThatThrownBy(() -> rename(parsed, INSENSITIVE, "USERS", "1bad"))
                .as("The new name must be a valid XML name.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_invalidTableName, "1bad"));
        assertThatThrownBy(() -> rename(parsed, INSENSITIVE, "USERS", "dataset"))
                .as("The new name must not be the root's name.").isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_reservedTableName, "dataset"));
    }

    @Test
    void testRenameEdits_whenTheTableIsOnlyDeclared_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.withDtd("<dataset/>\n",
                "<!ELEMENT dataset (PETS*)><!ELEMENT PETS EMPTY><!ATTLIST PETS ID CDATA #IMPLIED>");

        assertThatThrownBy(() -> rename(parsed, INSENSITIVE, "PETS", "ANIMALS"))
                .as("A table without an element has no name in the text to change.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_renameTableIsDeclaredOnly, "PETS"));
    }

    @Test
    void testDeleteEdits_whenTheTableHasRows_removesTheirLines() throws Exception
    {
        final ParsedDataset parsed = ParsedDataset.of(TEXT);

        final List<TextEdit> edits = delete(parsed, "USERS");

        assertThat(parsed.apply(edits)).as("Both USERS rows and their lines must go.")
                .isEqualTo("<dataset>\n" + ORDER + "</dataset>\n");
    }

    @Test
    void testDeleteEdits_whenTheTableHasAMarkerElement_removesItToo() throws Exception
    {
        final String text = "<dataset>\n  <USERS ID=\"1\"/>\n  <USERS/>\n  <ORDERS ID=\"10\"/>\n</dataset>\n";
        final ParsedDataset parsed = ParsedDataset.of(text);

        final List<TextEdit> edits = delete(parsed, "USERS");

        assertThat(parsed.apply(edits)).as("Every element of the table must go.")
                .isEqualTo("<dataset>\n" + ORDER + "</dataset>\n");
    }

    @Test
    void testDeleteEdits_whenElementsShareTheirLine_removesTheElementsAlone() throws Exception
    {
        final String text = "<dataset><USERS ID=\"1\"/><ORDERS ID=\"10\"/></dataset>\n";
        final ParsedDataset parsed = ParsedDataset.of(text);

        final List<TextEdit> edits = delete(parsed, "USERS");

        assertThat(parsed.apply(edits)).as("Neighbors on the same line must stay.")
                .isEqualTo("<dataset><ORDERS ID=\"10\"/></dataset>\n");
    }

    @Test
    void testDeleteEdits_whenTheTableIsOnlyDeclared_throwsDatasetEditException()
    {
        final ParsedDataset parsed = ParsedDataset.withDtd("<dataset/>\n",
                "<!ELEMENT dataset (PETS*)><!ELEMENT PETS EMPTY><!ATTLIST PETS ID CDATA #IMPLIED>");

        assertThatThrownBy(() -> delete(parsed, "PETS"))
                .as("A table without an element has nothing to delete.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage(NLS.bind(Messages.Edit_deleteTableIsDeclaredOnly, "PETS"));
    }
}
