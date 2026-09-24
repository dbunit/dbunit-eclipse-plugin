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
import java.util.Locale;
import java.util.Map;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.NoSuchColumnException;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.eclipse.dataset.core.TestDatasets;
import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdReader;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
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

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = { "flatXmlDataSetTest.xml", "flatXmlDataSetDuplicateTest.xml",
            "flatXmlDataSetDuplicateMultipleCaseTest.xml", "editor-sample.xml", "column-sensing.xml",
            "special-characters.xml" })
    void testBuild_whenFixtureHasNoDoctype_matchesDbUnitOnEveryColumn(final String fixtureName)
            throws Exception
    {
        assertParity(TestDatasets.read(fixtureName), datasetsFile(fixtureName), true, true);
    }

    @ParameterizedTest
    @ValueSource(strings = { "flatXmlTableTest.xml", "flatXmlDataSetDtdDifferentCaseTest.xml",
            "internal-subset.xml" })
    void testBuild_whenFixtureHasADoctype_matchesDbUnitOnDeclaredColumns(final String fixtureName)
            throws Exception
    {
        assertParity(TestDatasets.read(fixtureName), datasetsFile(fixtureName), false, false);
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
    void testBuild_whenInternalSubsetIsLoadedWithColumnSensing_dbUnitThrowsNoSuchColumnException()
    {
        assertThatThrownBy(() -> new FlatXmlDataSetBuilder().setColumnSensing(true)
                .build(datasetsFile("internal-subset.xml"))).as(
                        "An attribute the DTD does not declare, loaded with column sensing, must fail, "
                                + "the case the validator reports as an ERROR.")
                .isInstanceOf(NoSuchColumnException.class);
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
                    final String actual = modelTable.getRows().get(row).getValue(columnIndex);
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
        DtdDeclarations declarations = DtdReader.read(internalSubset == null ? "" : internalSubset);
        if (parse.doctype().systemId() != null)
        {
            declarations =
                    declarations.merge(DtdReader.read(TestDatasets.read(parse.doctype().systemId())));
        }
        return declarations;
    }

    private static File datasetsFile(final String name)
    {
        return DATASETS_DIRECTORY.resolve(name).toFile();
    }
}
