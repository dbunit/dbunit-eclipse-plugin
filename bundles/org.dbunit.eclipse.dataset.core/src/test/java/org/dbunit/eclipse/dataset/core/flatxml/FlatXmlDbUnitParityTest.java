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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.NoSuchColumnException;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.eclipse.dataset.core.TestDatasets;
import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdReader;
import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Compares the model built from each "parity" fixture with the dataset real dbUnit 3.5.2 loads from the
 * same file, so the editor shows exactly what dbUnit would read.
 */
class FlatXmlDbUnitParityTest
{
    private static final Path DATASETS_DIRECTORY = Path.of("src", "test", "resources", "datasets");

    private static final String CASE_VARIANT_TABLES_DATASET = "<!DOCTYPE dataset [\n"
            + "<!ELEMENT dataset (users*, USERS*)>\n"
            + "<!ELEMENT users EMPTY>\n<!ATTLIST users A CDATA #IMPLIED>\n"
            + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS B CDATA #IMPLIED>\n]>\n<dataset/>\n";

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = { "flatXmlDataSetTest.xml", "flatXmlDataSetDuplicateTest.xml",
            "flatXmlDataSetDuplicateMultipleCaseTest.xml", "editor-sample.xml", "column-sensing.xml",
            "special-characters.xml", "column-name-case.xml" })
    void testBuild_whenFixtureHasNoDoctype_matchesDbUnitOnEveryColumn(final String fixtureName)
            throws Exception
    {
        assertParity(TestDatasets.read(fixtureName), datasetsFile(fixtureName), true, true);
    }

    @ParameterizedTest
    @ValueSource(strings = { "flatXmlTableTest.xml", "flatXmlDataSetDtdDifferentCaseTest.xml",
            "internal-subset.xml", "column-name-case-dtd.xml", "dtd-defaults-internal.xml",
            "dtd-defaults-external.xml", "dtd-parameter-entity.xml", "doctype-root-name.xml" })
    void testBuild_whenFixtureHasADoctype_matchesDbUnitOnDeclaredColumns(final String fixtureName)
            throws Exception
    {
        assertParity(TestDatasets.read(fixtureName), datasetsFile(fixtureName), false, false);
    }

    @Test
    void testBuild_whenTheContentModelIsEmpty_dbUnitFailsToLoadTheDatasetAndTheModelReportsIt()
            throws Exception
    {
        final String text = "<!DOCTYPE dataset [\n<!ELEMENT dataset EMPTY>\n]>\n<dataset/>\n";
        final Path file = tempDir.resolve("empty-content-model.xml");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(new Document(text),
                DtdSource.NONE, FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();

        assertThatThrownBy(() -> new FlatXmlDataSetBuilder().build(file.toFile()))
                .as("dbUnit must fail to load a dataset whose DTD declares the dataset element EMPTY.")
                .isInstanceOf(DataSetException.class);
        assertThat(datasetDocument.getModel().getProblems()).extracting(DatasetProblem::code)
                .as("The model must report the failure, and not as a table named EMPTY.")
                .containsExactly(ProblemCode.DTD_EMPTY_CONTENT_MODEL);
        assertThat(datasetDocument.getModel().getTables()).as("The keyword EMPTY is no table.").isEmpty();
    }

    @Test
    void testBuild_whenTheDtdListsTwoSpellingsOfATable_dbUnitFailsToLoadTheDatasetAndTheModelReportsIt()
            throws Exception
    {
        final Path file = tempDir.resolve("table-name-case-variants.xml");
        Files.writeString(file, CASE_VARIANT_TABLES_DATASET, StandardCharsets.UTF_8);
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(
                new Document(CASE_VARIANT_TABLES_DATASET), DtdSource.NONE, FlatXmlOptions.DBUNIT_DEFAULTS,
                () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();

        assertThatThrownBy(() -> new FlatXmlDataSetBuilder().build(file.toFile()))
                .as("dbUnit must fail to load a DTD that lists two spellings of one table.")
                .isInstanceOf(DataSetException.class);
        assertThat(datasetDocument.getModel().getProblems()).extracting(DatasetProblem::code)
                .as("The model must report the failure.")
                .containsExactly(ProblemCode.DTD_TABLE_NAME_CASE_VARIANTS);
        assertThat(datasetDocument.getModel().getTables()).extracting(DatasetTable::getName)
                .as("The two spellings are one table, as in dbUnit.").containsExactly("users");
    }

    @Test
    void testBuild_whenTheDtdListsTwoSpellingsOfATableAndNamesAreCaseSensitive_dbUnitStillFailsToLoadIt()
            throws Exception
    {
        final Path file = tempDir.resolve("table-name-case-variants.xml");
        Files.writeString(file, CASE_VARIANT_TABLES_DATASET, StandardCharsets.UTF_8);
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(
                new Document(CASE_VARIANT_TABLES_DATASET), DtdSource.NONE, new FlatXmlOptions(true, false),
                () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();

        assertThatThrownBy(
                () -> new FlatXmlDataSetBuilder().setCaseSensitiveTableNames(true).build(file.toFile()))
                .as("dbUnit keeps the tables of a DTD in a map that ignores letter case.")
                .isInstanceOf(DataSetException.class);
        assertThat(datasetDocument.getModel().getProblems()).extracting(DatasetProblem::code)
                .as("The model must report the failure whatever the case sensitivity of table names.")
                .containsExactly(ProblemCode.DTD_TABLE_NAME_CASE_VARIANTS);
    }

    @Test
    void testBuild_whenTheDtdDeclaresOnlyAnotherNameThanTheDoctypesAsRoot_dbUnitFailsToLoadTheRows()
            throws Exception
    {
        final String text = "<!DOCTYPE dataset [\n<!ELEMENT DATASET (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                + "<!ATTLIST USERS ID CDATA #IMPLIED>\n]>\n<dataset><USERS ID=\"1\"/></dataset>\n";
        final Path file = tempDir.resolve("other-root-name.xml");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(new Document(text),
                DtdSource.NONE, FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();

        assertThatThrownBy(() -> new FlatXmlDataSetBuilder().build(file.toFile()))
                .as("dbUnit takes the name of the DOCTYPE for the root, and finds no tables in the DTD.")
                .isInstanceOf(DataSetException.class);
        assertThat(datasetDocument.getModel().getProblems()).extracting(DatasetProblem::code)
                .as("The model must report that the DTD does not list the table, as dbUnit finds it.")
                .containsExactly(ProblemCode.TABLE_NOT_DECLARED_IN_DTD);
    }

    @Test
    void testBuild_whenTheIso88591FixtureIsRead_matchesDbUnitOnEveryColumn() throws Exception
    {
        final String text = TestDatasets.read("iso-8859-1.xml", StandardCharsets.ISO_8859_1);

        assertParity(text, datasetsFile("iso-8859-1.xml"), true, true);
    }

    @Test
    void testBuild_whenEditorSampleIsRewrittenWithCrLf_matchesDbUnitOnEveryColumn() throws Exception
    {
        final String crlfText = TestDatasets.read("editor-sample.xml").replace("\n", "\r\n");
        final Path crlfFile = tempDir.resolve("crlf.xml");
        Files.writeString(crlfFile, crlfText, StandardCharsets.UTF_8);

        assertParity(crlfText, crlfFile.toFile(), true, true);
    }

    @Test
    void testDbUnit_whenTheFirstRowLacksAColumnTheOtherRowsHave_ignoresTheColumnWithoutColumnSensing()
            throws Exception
    {
        final Path file = tempDir.resolve("sparse-first-row.xml");
        Files.writeString(file, "<dataset>\n    <T ID=\"\"/>\n    <T ID=\"1\" NAME=\"a\"/>\n</dataset>\n",
                StandardCharsets.UTF_8);

        final ITable withoutSensing =
                new FlatXmlDataSetBuilder().setColumnSensing(false).build(file.toFile()).getTable("T");
        final ITable withSensing =
                new FlatXmlDataSetBuilder().setColumnSensing(true).build(file.toFile()).getTable("T");

        assertThat(List.of(columnNames(withoutSensing), columnNames(withSensing)))
                .as("Without column sensing dbUnit takes the columns from the first row, which is why the "
                        + "editor keeps every column of the old first row on a new first row.")
                .containsExactly(List.of("ID"), List.of("ID", "NAME"));
    }

    @Test
    void testInsertBlankRow_aboveTheFirstRow_leavesDbUnitLoadingEveryColumn() throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n</dataset>\n");
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();

        datasetDocument.insertBlankRow("USERS", 0);

        final Path file = tempDir.resolve("blank-row.xml");
        Files.writeString(file, document.get(), StandardCharsets.UTF_8);
        assertParity(document.get(), file.toFile(), false, true);
        final ITable dbUnitTable =
                new FlatXmlDataSetBuilder().setColumnSensing(false).build(file.toFile()).getTable("USERS");
        assertThat(dbUnitTable.getValue(1, "NAME"))
                .as("dbUnit must still load the name of the old first row.").isEqualTo("Alice");
    }

    @Test
    void testRenameTable_whenTheInternalSubsetDeclaresTheTable_leavesDbUnitLoadingTheRenamedTable()
            throws Exception
    {
        final File file = renameInFixture("dtd-defaults-internal.xml", "USERS", "ACCOUNTS");

        assertParity(Files.readString(file.toPath(), StandardCharsets.UTF_8), file, false, false);
        final IDataSet dbUnitDataSet = new FlatXmlDataSetBuilder().setColumnSensing(false).build(file);
        assertThat(dbUnitDataSet.getTableNames())
                .as("dbUnit must find the renamed table, which its DTD still declares.")
                .containsExactly("ACCOUNTS", "ORDERS", "AUDIT");
        assertThat(dbUnitDataSet.getTable("ACCOUNTS").getValue(0, "STATUS"))
                .as("dbUnit must still apply the default that the renamed table's ATTLIST declares.")
                .isEqualTo("ACTIVE");
    }

    @Test
    void testRenameTable_whenOnlyTheLetterCaseChanges_leavesDbUnitApplyingTheDtdDefaults() throws Exception
    {
        final File file = renameInFixture("dtd-defaults-internal.xml", "USERS", "Users");

        assertParity(Files.readString(file.toPath(), StandardCharsets.UTF_8), file, false, false);
        final ITable dbUnitTable =
                new FlatXmlDataSetBuilder().setColumnSensing(false).build(file).getTable("Users");
        assertThat(dbUnitTable.getValue(0, "STATUS")).as(
                "dbUnit gives a DTD's defaults only to elements spelled like the DTD's element, so the "
                        + "DTD must have taken the new spelling too.")
                .isEqualTo("ACTIVE");
    }

    @Test
    void testBuild_whenInternalSubsetIsLoadedWithColumnSensing_dbUnitThrowsNoSuchColumnException()
    {
        assertThatThrownBy(() -> new FlatXmlDataSetBuilder().setColumnSensing(true)
                .build(datasetsFile("internal-subset.xml"))).as(
                        "An attribute the DTD does not declare, loaded with column sensing, must fail, "
                                + "the case the validator reports as an ERROR.")
                .isInstanceOf(NoSuchColumnException.class);
    }

    /**
     * Renames a table of a fixture through a dataset document and saves the resulting text to a file, so
     * that dbUnit can load what the editor wrote.
     */
    private File renameInFixture(final String fixtureName, final String tableKey, final String newTableName)
            throws Exception
    {
        final IDocument document = new Document(TestDatasets.read(fixtureName));
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();

        datasetDocument.renameTable(tableKey, newTableName);

        final Path file = tempDir.resolve("renamed-table.xml");
        Files.writeString(file, document.get(), StandardCharsets.UTF_8);
        return file.toFile();
    }

    private void assertParity(final String text, final File file, final boolean sensing,
            final boolean everyColumnMustMatch) throws Exception
    {
        final DatasetModel model = buildModel(text);
        final IDataSet dbUnitDataSet = new FlatXmlDataSetBuilder().setColumnSensing(sensing).build(file);

        final String[] dbUnitTableNames = dbUnitDataSet.getTableNames();
        assertThat(model.getTables()).as("Table names and order must match dbUnit.")
                .extracting(table -> table.getName().toUpperCase(Locale.ENGLISH))
                .containsExactly(upperCase(dbUnitTableNames));

        for (int tableIndex = 0; tableIndex < dbUnitTableNames.length; tableIndex++)
        {
            final ITable dbUnitTable = dbUnitDataSet.getTable(dbUnitTableNames[tableIndex]);
            final DatasetTable modelTable = model.getTables().get(tableIndex);
            final int rowCount = dbUnitTable.getRowCount();
            assertThat(modelTable.getRows())
                    .as("Table '" + modelTable.getName() + "': row count must match dbUnit.")
                    .hasSize(rowCount);

            final Column[] dbUnitColumns = dbUnitTable.getTableMetaData().getColumns();
            for (final Column column : dbUnitColumns)
            {
                final String columnName = column.getColumnName();
                final int columnIndex = modelTable.getColumnIndex(columnName);
                assertThat(columnIndex)
                        .as("Table '" + modelTable.getName() + "': column '" + columnName
                                + "' must exist in the model.")
                        .isGreaterThanOrEqualTo(0);
                for (int row = 0; row < rowCount; row++)
                {
                    final Object expected = dbUnitTable.getValue(row, columnName);
                    final String actual = modelTable.getEffectiveValue(row, columnIndex);
                    assertThat(actual)
                            .as("Table '" + modelTable.getName() + "', row " + row + ", column '"
                                    + columnName + "'.")
                            .isEqualTo(expected);
                }
            }
            if (everyColumnMustMatch)
            {
                assertThat(modelTable.getColumns())
                        .as("Table '" + modelTable.getName() + "': without a DTD, the model must show "
                                + "exactly dbUnit's columns, not more.")
                        .hasSize(dbUnitColumns.length);
            }
        }
    }

    private static List<String> columnNames(final ITable table) throws Exception
    {
        final List<String> names = new ArrayList<>();
        for (final Column column : table.getTableMetaData().getColumns())
        {
            names.add(column.getColumnName());
        }
        return names;
    }

    private static String[] upperCase(final String[] names)
    {
        final String[] result = new String[names.length];
        for (int i = 0; i < names.length; i++)
        {
            result[i] = names[i].toUpperCase(Locale.ENGLISH);
        }
        return result;
    }

    private static DatasetModel buildModel(final String text)
    {
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final DtdDeclarations dtd = resolveDtd(parse);
        return FlatXmlModelBuilder.build(text, parse, dtd, FlatXmlOptions.DBUNIT_DEFAULTS, Map.of())
                .model();
    }

    private static DtdDeclarations resolveDtd(final FlatXmlParseResult parse)
    {
        if (parse.doctype() == null)
        {
            return null;
        }
        final String internalSubset = parse.doctype().internalSubset();
        final String rootName = parse.doctype().rootName();
        DtdDeclarations declarations = DtdReader.read(internalSubset == null ? "" : internalSubset, rootName);
        if (parse.doctype().systemId() != null)
        {
            declarations = declarations
                    .merge(DtdReader.read(TestDatasets.read(parse.doctype().systemId()), rootName));
        }
        return declarations;
    }

    private static File datasetsFile(final String name)
    {
        return DATASETS_DIRECTORY.resolve(name).toFile();
    }
}
