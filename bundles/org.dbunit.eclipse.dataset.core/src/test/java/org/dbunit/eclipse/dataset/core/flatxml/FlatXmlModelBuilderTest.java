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

import java.util.List;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdReader;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlModelBuilder} against the table and column grouping rules of the Model Builder and
 * Mapping sections of the flat XML specification.
 */
class FlatXmlModelBuilderTest
{
    @Test
    void testBuild_whenTableNamesDifferOnlyInCase_groupsThemUnderTheFirstSpelling()
    {
        final DatasetModel model =
                buildWithoutDtd("<dataset><USERS ID=\"1\"/><users ID=\"2\"/></dataset>",
                        FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        assertThat(model.getTables()).as("Both spellings must group into one table.").hasSize(1);
        final DatasetTable table = model.getTables().get(0);
        assertThat(table.getName()).as("The display name must be the first spelling.").isEqualTo("USERS");
        assertThat(table.getRows()).as("Both elements' rows must be kept.").hasSize(2);
    }

    @Test
    void testBuild_whenCaseSensitiveTableNames_keepsSpellingsSeparate()
    {
        final DatasetModel model = buildWithoutDtd(
                "<dataset><USERS ID=\"1\"/><users ID=\"2\"/></dataset>", new FlatXmlOptions(true, false),
                Map.of());

        assertThat(model.getTables()).as("Case-sensitive table names must keep them separate.")
                .extracting(DatasetTable::getName).containsExactly("USERS", "users");
    }

    @Test
    void testBuild_whenATableIsSplitAcrossInterleavedSegments_mergesRowsInDocumentOrder()
    {
        final DatasetModel model = buildWithoutDtd(
                "<dataset><USERS ID=\"1\"/><ORDERS ID=\"10\"/><USERS ID=\"2\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getRows()).as("Interleaved segments must merge in document order.")
                .extracting(row -> row.getValue(users.getColumnIndex("ID"))).containsExactly("1", "2");
    }

    @Test
    void testBuild_whenAttributesAppearInDifferentOrderAcrossRows_ordersColumnsByFirstAppearance()
    {
        final DatasetModel model = buildWithoutDtd(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                        + "<USERS EMAIL=\"bob@x.org\" ID=\"2\" NAME=\"Bob\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns()).as("Columns must be ordered by first appearance.")
                .extracting(DatasetColumn::name).containsExactly("ID", "NAME", "EMAIL");
    }

    @Test
    void testBuild_whenATableHasOnlyMarkerElements_isAnEmptyTable()
    {
        final DatasetModel model = buildWithoutDtd("<dataset><AUDIT_LOG/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable auditLog = model.findTable("AUDIT_LOG").orElseThrow();
        assertThat(auditLog.getRows()).as("A marker element declares the table but is not a row.")
                .isEmpty();
    }

    @Test
    void testBuild_whenTableIsDeclaredInDtd_ordersDeclaredColumnsFirstThenUndeclared()
    {
        final DatasetModel model = buildWithDoctype(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset><USERS ID=\"1\" NAME=\"Alice\" EXTRA=\"x\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns()).as(
                "Declared columns must come first in DTD order, then undeclared data columns.")
                .extracting(DatasetColumn::name).containsExactly("ID", "NAME", "EXTRA");
        assertThat(users.getColumns().get(0).declared()).as("ID must be marked declared.").isTrue();
        assertThat(users.getColumns().get(2).declared()).as("EXTRA must not be marked declared.")
                .isFalse();
    }

    @Test
    void testBuild_whenADtdTableHasNoElements_isAppendedLastAsDeclaredOnly()
    {
        final DatasetModel model = buildWithDoctype(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*, ORDERS*)>\n"
                        + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #REQUIRED>\n"
                        + "<!ELEMENT ORDERS EMPTY>\n<!ATTLIST ORDERS ID CDATA #REQUIRED>\n]>\n"
                        + "<dataset><USERS ID=\"1\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        assertThat(model.getTables()).as("ORDERS must be appended after USERS.")
                .extracting(DatasetTable::getName).containsExactly("USERS", "ORDERS");
        final DatasetTable orders = model.findTable("ORDERS").orElseThrow();
        assertThat(orders.isDeclaredOnly()).as("A DTD table without elements is declared-only.").isTrue();
        assertThat(orders.getRows()).as("A declared-only table has no rows.").isEmpty();
    }

    @Test
    void testBuild_whenAnElementIsDeclaredOutsideTheContentModel_isNeitherADtdTableNorDeclaredOnly()
    {
        final DatasetModel model = buildWithDoctype(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
                        + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #REQUIRED>\n"
                        + "<!ELEMENT AUDIT_LOG EMPTY>\n]>\n"
                        + "<dataset><USERS ID=\"1\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        assertThat(model.getTables()).as(
                "AUDIT_LOG is declared but outside the content model, so it must not appear at all.")
                .extracting(DatasetTable::getName).containsExactly("USERS");
    }

    @Test
    void testBuild_whenTwoAttributesDifferOnlyInCase_theLastOnesValueWins()
    {
        final DatasetModel model = buildWithoutDtd("<dataset><USERS id=\"1\" ID=\"2\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns()).as("id and ID must be one column.").hasSize(1);
        assertThat(users.getRows().get(0).getValue(0)).as("The last attribute's value must win.")
                .isEqualTo("2");
    }

    @Test
    void testBuild_whenAPendingColumnHasNoData_isMarkedPending()
    {
        final DatasetModel model = buildWithoutDtd("<dataset><USERS ID=\"1\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of("USERS", List.of("EMAIL")));

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns()).as("The pending column must be appended.")
                .extracting(DatasetColumn::name).containsExactly("ID", "EMAIL");
        assertThat(users.getColumns().get(1).pending()).as("EMAIL must be pending.").isTrue();
    }

    @Test
    void testBuild_whenDataProvidesAValueForAFormerlyPendingColumn_isNoLongerPending()
    {
        final DatasetModel model =
                buildWithoutDtd("<dataset><USERS ID=\"1\" EMAIL=\"a@x.org\"/></dataset>",
                        FlatXmlOptions.DBUNIT_DEFAULTS, Map.of("USERS", List.of("EMAIL")));

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns().get(1).pending())
                .as("A pending column must stop being pending once data has it.").isFalse();
    }

    @Test
    void testBuild_whenAColumnHasAtLeastOneValue_hasValuesIsTrue()
    {
        final DatasetModel model = buildWithoutDtd(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns().get(1).hasValues())
                .as("NAME has a value in the first row, so hasValues must be true.").isTrue();
    }

    @Test
    void testBuild_whenNoRowHasAValueForAColumn_hasValuesIsFalse()
    {
        final DatasetModel model = buildWithDoctype(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset><USERS ID=\"1\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns().get(1).hasValues())
                .as("NAME has no value in any row, so hasValues must be false.").isFalse();
    }

    @Test
    void testBuild_whenParseIsNotWellFormed_givesANonEditableModel()
    {
        final FlatXmlParseResult parse = FlatXmlParser.parse("<dataset><USERS ID=\"1\"></ORDERS>");

        final DatasetModel model = FlatXmlModelBuilder
                .build("", parse, null, FlatXmlOptions.DBUNIT_DEFAULTS, Map.of()).model();

        assertThat(model.isEditable()).as("A not well-formed parse must give a non-editable model.")
                .isFalse();
    }

    private static DatasetModel buildWithoutDtd(final String text, final FlatXmlOptions options,
            final Map<String, List<String>> pendingColumns)
    {
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        return FlatXmlModelBuilder.build(text, parse, null, options, pendingColumns).model();
    }

    private static DatasetModel buildWithDoctype(final String text, final FlatXmlOptions options,
            final Map<String, List<String>> pendingColumns)
    {
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final DtdDeclarations dtd = DtdReader.read(parse.doctype().internalSubset());
        return FlatXmlModelBuilder.build(text, parse, dtd, options, pendingColumns).model();
    }
}
