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
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
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
    void testBuild_whenDtdContentModelRepeatsATableNameWithDifferentCase_isOneDeclaredOnlyTable()
    {
        final DatasetModel model = buildWithDoctype(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (users*, USERS*)>\n]>\n<dataset></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        assertThat(model.getTables())
                .as("A case-insensitive duplicate in the content model must yield one table, not two.")
                .extracting(DatasetTable::getName).containsExactly("users");
        assertThat(model.getTables().get(0).isDeclaredOnly())
                .as("A DTD-only table with no elements is declared-only.").isTrue();
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
    void testBuild_whenAnExternalDtdDeclaresATable_marksItDeclaredInTheExternalDtd()
    {
        final DatasetModel model = buildWithExternalDtd("<!DOCTYPE dataset SYSTEM \"x.dtd\" [\n"
                + "<!ELEMENT dataset (USERS*, ORDERS*, PETS*)>\n<!ELEMENT USERS EMPTY>\n"
                + "<!ATTLIST USERS ID CDATA #REQUIRED>\n<!ELEMENT ORDERS EMPTY>\n"
                + "<!ATTLIST ORDERS ID CDATA #REQUIRED>\n]>\n"
                + "<dataset><USERS ID=\"1\"/><ORDERS ID=\"2\"/></dataset>",
                "<!ATTLIST USERS NAME CDATA #IMPLIED>\n<!ELEMENT PETS EMPTY>",
                FlatXmlOptions.DBUNIT_DEFAULTS);

        assertThat(model.getTables())
                .as("The tables that the external DTD declares, with or without elements in the document, "
                        + "must be marked, and the one that only the internal subset declares must not.")
                .extracting(DatasetTable::getName, DatasetTable::isDeclaredInExternalDtd)
                .containsExactly(tuple("USERS", true), tuple("ORDERS", false), tuple("PETS", true));
    }

    @Test
    void testBuild_whenAnExternalDtdSpellsATableInAnotherCase_marksItIfNamesAreCaseInsensitive()
    {
        final String text = "<!DOCTYPE dataset SYSTEM \"x.dtd\" [\n<!ELEMENT dataset (USERS*)>\n"
                + "<!ELEMENT USERS EMPTY>\n]>\n<dataset><USERS ID=\"1\"/></dataset>";

        final DatasetModel model = buildWithExternalDtd(text, "<!ATTLIST Users ID CDATA #IMPLIED>",
                FlatXmlOptions.DBUNIT_DEFAULTS);

        assertThat(model.findTable("USERS").orElseThrow().isDeclaredInExternalDtd())
                .as("Users and USERS are one table, so the external declaration counts.").isTrue();
    }

    @Test
    void testBuild_whenAnExternalDtdSpellsATableInAnotherCase_doesNotMarkItIfNamesAreCaseSensitive()
    {
        final String text = "<!DOCTYPE dataset SYSTEM \"x.dtd\" [\n<!ELEMENT dataset (USERS*)>\n"
                + "<!ELEMENT USERS EMPTY>\n]>\n<dataset><USERS ID=\"1\"/></dataset>";

        final DatasetModel model = buildWithExternalDtd(text, "<!ATTLIST Users ID CDATA #IMPLIED>",
                new FlatXmlOptions(true, false));

        assertThat(model.findTable("USERS").orElseThrow().isDeclaredInExternalDtd())
                .as("Users and USERS are two tables, so the external declaration is not USERS'.").isFalse();
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
    void testBuild_whenALaterRowSpellsAColumnInAnotherCase_fillsTheColumnFromIt()
    {
        final DatasetModel model = buildWithoutDtd("<dataset><USERS ID=\"1\"/><USERS id=\"2\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns()).as("Both spellings must be one column, named after the first.")
                .extracting(DatasetColumn::name).containsExactly("ID");
        assertThat(users.getRows()).as("The second row's id attribute must fill the ID column.")
                .extracting(row -> row.getValue(0)).containsExactly("1", "2");
    }

    @Test
    void testBuild_whenARowSpellsADtdColumnInAnotherCase_fillsTheDeclaredColumnFromIt()
    {
        final DatasetModel model = buildWithDoctype(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS EMPNO CDATA #REQUIRED>\n]>\n"
                        + "<dataset><USERS empno=\"1\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns()).as("The lower-case attribute must not add a column.")
                .extracting(DatasetColumn::name).containsExactly("EMPNO");
        assertThat(users.getRows().get(0).getValue(0))
                .as("The declared column must take the lower-case attribute's value.").isEqualTo("1");
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
    void testBuild_whenDtdColumnsHaveDefaultAndFixedValues_theModelColumnsCarryThem()
    {
        final DatasetModel model = buildWithDoctype(
                dtdDefaultsDoctype() + "<dataset><USERS ID=\"1\"/></dataset>", FlatXmlOptions.DBUNIT_DEFAULTS,
                Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns())
                .as("Only the columns the DTD gives a default or fixed value carry one.")
                .containsExactly(new DatasetColumn("ID", true, true, false),
                        new DatasetColumn("STATUS", true, false, false, "ACTIVE"),
                        new DatasetColumn("KIND", true, false, false, "A"));
    }

    @Test
    void testBuild_whenARowOmitsDefaultedAttributes_keepsNullAndLoadsTheDefaults()
    {
        final DatasetModel model = buildWithDoctype(
                dtdDefaultsDoctype() + "<dataset><USERS ID=\"1\"/></dataset>", FlatXmlOptions.DBUNIT_DEFAULTS,
                Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getRows().get(0).getValues())
                .as("The row must keep no value where it has no attribute.")
                .containsExactly("1", null, null);
        assertThat(effectiveValues(users, 0))
                .as("dbUnit loads the defaults for the attributes the row omits.")
                .containsExactly("1", "ACTIVE", "A");
    }

    @Test
    void testBuild_whenARowSpecifiesDefaultedAttributes_theirValuesWinEvenWhenEmpty()
    {
        final DatasetModel model = buildWithDoctype(
                dtdDefaultsDoctype() + "<dataset><USERS ID=\"1\" STATUS=\"x\" KIND=\"B\"/>"
                        + "<USERS ID=\"2\" STATUS=\"\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(effectiveValues(users, 0))
                .as("A specified value must win over a default or a fixed value.")
                .containsExactly("1", "x", "B");
        assertThat(effectiveValues(users, 1)).as("An empty specified value must win over the default.")
                .containsExactly("2", "", "A");
    }

    @Test
    void testBuild_whenADeclaredOnlyTableHasDefaults_itsColumnsCarryThem()
    {
        final DatasetModel model = buildWithDoctype(dtdDefaultsDoctype() + "<dataset/>",
                FlatXmlOptions.DBUNIT_DEFAULTS, Map.of());

        final DatasetTable users = model.findTable("USERS").orElseThrow();
        assertThat(users.getColumns())
                .as("A table that only the DTD declares must still show its columns' defaults.")
                .extracting(DatasetColumn::defaultValue).containsExactly(null, "ACTIVE", "A");
    }

    @Test
    void testBuild_whenAnElementWithoutAttributesHasDefaultValues_isARowOfDefaults()
    {
        final FlatXmlModelBuilder.Result result = buildResultWithDoctype(
                dtdDefaultsDoctype() + "<dataset><USERS/></dataset>", FlatXmlOptions.DBUNIT_DEFAULTS);

        final DatasetTable users = result.model().findTable("USERS").orElseThrow();
        assertThat(users.getRows()).as("dbUnit loads the empty element as a row of default values.")
                .hasSize(1);
        assertThat(effectiveValues(users, 0)).as("The row must show what dbUnit loads for it.")
                .containsExactly(null, "ACTIVE", "A");
        assertThat(result.index().getMarkerElements("USERS"))
                .as("The element must be indexed as a row, so that edits treat it as one.").isEmpty();
        assertThat(result.index().getRowElements("USERS")).as("The element must be the row's element.")
                .hasSize(1);
    }

    @Test
    void testBuild_whenAnElementWithoutAttributesHasNoDefaultValues_isStillAMarker()
    {
        final FlatXmlModelBuilder.Result result = buildResultWithDoctype(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED STATUS CDATA #IMPLIED>\n]>\n"
                        + "<dataset><USERS/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS);

        assertThat(result.model().findTable("USERS").orElseThrow().getRows())
                .as("Without a default, dbUnit loads no row for the empty element.").isEmpty();
        assertThat(result.index().getMarkerElements("USERS")).as("The element must stay a marker.")
                .hasSize(1);
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

    /**
     * Builds the model of a text whose DOCTYPE has an internal subset and names an external DTD, merging
     * the declarations as the dataset document does.
     */
    private static DatasetModel buildWithExternalDtd(final String text, final String externalDtdText,
            final FlatXmlOptions options)
    {
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final DtdDeclarations internalSubset = DtdReader.read(parse.doctype().internalSubset());
        final DtdDeclarations dtd = internalSubset.merge(DtdReader.read(externalDtdText));
        return FlatXmlModelBuilder.build(text, parse, dtd, options, Map.of()).model();
    }

    private static FlatXmlModelBuilder.Result buildResultWithDoctype(final String text,
            final FlatXmlOptions options)
    {
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final DtdDeclarations dtd = DtdReader.read(parse.doctype().internalSubset());
        return FlatXmlModelBuilder.build(text, parse, dtd, options, Map.of());
    }

    /**
     * Returns a DOCTYPE whose DTD declares USERS with a column without a default, a column with a default,
     * and a column with a fixed value.
     */
    private static String dtdDefaultsDoctype()
    {
        return "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                + "<!ATTLIST USERS ID CDATA #IMPLIED STATUS CDATA \"ACTIVE\" KIND CDATA #FIXED \"A\">\n]>\n";
    }

    private static List<String> effectiveValues(final DatasetTable table, final int rowIndex)
    {
        final List<String> values = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < table.getColumns().size(); columnIndex++)
        {
            values.add(table.getEffectiveValue(rowIndex, columnIndex));
        }
        return values;
    }
}
