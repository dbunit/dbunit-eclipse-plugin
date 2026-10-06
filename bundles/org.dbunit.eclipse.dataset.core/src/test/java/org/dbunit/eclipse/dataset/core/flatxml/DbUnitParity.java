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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.eclipse.dataset.core.TestDatasets;
import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdReader;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;

/**
 * Compares the model that the editor builds from a dataset text with the dataset that real dbUnit 3.5.2
 * loads from a file, so that the tests of the editor can show that it reads exactly what dbUnit would read.
 */
final class DbUnitParity
{
    private static final Path DATASETS_DIRECTORY = Path.of("src", "test", "resources", "datasets");

    private DbUnitParity()
    {
    }

    static void assertParity(final String text, final File file, final boolean sensing,
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

    static List<String> columnNames(final ITable table) throws Exception
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

    static File datasetsFile(final String name)
    {
        return DATASETS_DIRECTORY.resolve(name).toFile();
    }
}
