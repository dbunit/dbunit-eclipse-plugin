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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdTable;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;

/**
 * Builds a dataset model and its index from a parsed flat XML document, applying dbUnit's table and
 * column grouping, DTD columns, and pending columns.
 */
final class FlatXmlModelBuilder
{
    private FlatXmlModelBuilder()
    {
    }

    /**
     * Builds the model and index of a parsed flat XML document. The model carries no problems; the
     * validator adds those separately.
     *
     * @param text The exact text that was parsed.
     * @param parse The parse result to build from.
     * @param dtd The merged DTD declarations, or null when the document has no DOCTYPE.
     * @param options The case-sensitivity and column-sensing options.
     * @param pendingColumns Each table's pending columns, keyed by table key, in the order they were
     *                       added.
     * @return The model and the index the edit engine takes offsets from.
     */
    static Result build(final String text, final FlatXmlParseResult parse, final DtdDeclarations dtd,
            final FlatXmlOptions options, final Map<String, List<String>> pendingColumns)
    {
        final Map<String, DtdTable> dtdTablesByKey = indexDtdTables(dtd, options);
        final Map<String, TableGroup> groups = groupElementsByTable(parse.elements(), options);

        final List<DatasetTable> tables = new ArrayList<>();
        for (final Map.Entry<String, TableGroup> entry : groups.entrySet())
        {
            final String key = entry.getKey();
            final TableGroup group = entry.getValue();
            final DtdTable dtdTable = dtdTablesByKey.get(key);
            final List<ColumnInfo> columns = buildColumns(group.rowElements, dtdTable,
                    pendingColumns.getOrDefault(key, List.of()));
            final List<DatasetRow> rows = buildRows(group.rowElements, columns);
            tables.add(new DatasetTable(key, group.displayName, toDatasetColumns(columns, rows), rows,
                    false));
        }
        addDtdOnlyTables(tables, dtd, options, groups, pendingColumns);

        final DatasetModel model = new DatasetModel(tables, List.of(), parse.wellFormed());
        final FlatXmlIndex index = buildIndex(parse, text, groups);
        return new Result(model, index);
    }

    private static Map<String, DtdTable> indexDtdTables(final DtdDeclarations dtd,
            final FlatXmlOptions options)
    {
        final Map<String, DtdTable> byKey = new LinkedHashMap<>();
        if (dtd != null)
        {
            for (final DtdTable table : dtd.tables())
            {
                byKey.put(tableKey(table.name(), options), table);
            }
        }
        return byKey;
    }

    private static Map<String, TableGroup> groupElementsByTable(final List<FlatXmlElement> elements,
            final FlatXmlOptions options)
    {
        final Map<String, TableGroup> groups = new LinkedHashMap<>();
        for (final FlatXmlElement element : elements)
        {
            final String key = tableKey(element.name(), options);
            final TableGroup group = groups.computeIfAbsent(key, unused -> new TableGroup(element.name()));
            if (element.attributes().isEmpty())
            {
                group.markerElements.add(element);
            }
            else
            {
                group.rowElements.add(element);
            }
        }
        return groups;
    }

    private static List<ColumnInfo> buildColumns(final List<FlatXmlElement> rowElements,
            final DtdTable dtdTable, final List<String> pendingNames)
    {
        final Map<String, ColumnInfo> columns = new LinkedHashMap<>();
        if (dtdTable != null)
        {
            for (final String declaredName : dtdTable.columns())
            {
                columns.put(columnKey(declaredName), new ColumnInfo(declaredName, true, false));
            }
        }
        for (final FlatXmlElement row : rowElements)
        {
            for (final FlatXmlAttribute attribute : row.attributes())
            {
                columns.putIfAbsent(columnKey(attribute.name()),
                        new ColumnInfo(attribute.name(), false, false));
            }
        }
        for (final String pendingName : pendingNames)
        {
            columns.putIfAbsent(columnKey(pendingName), new ColumnInfo(pendingName, false, true));
        }
        return List.copyOf(columns.values());
    }

    private static List<DatasetRow> buildRows(final List<FlatXmlElement> rowElements,
            final List<ColumnInfo> columns)
    {
        final Map<String, Integer> columnIndexByKey = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++)
        {
            columnIndexByKey.put(columnKey(columns.get(index).displayName()), index);
        }
        final List<DatasetRow> rows = new ArrayList<>();
        for (final FlatXmlElement row : rowElements)
        {
            final String[] values = new String[columns.size()];
            for (final FlatXmlAttribute attribute : row.attributes())
            {
                final Integer columnIndex = columnIndexByKey.get(columnKey(attribute.name()));
                if (columnIndex != null)
                {
                    // Attributes are visited in document order, so when two of an element's attributes
                    // differ only in case, the last one keeps overwriting and so wins.
                    values[columnIndex] = attribute.value();
                }
            }
            rows.add(new DatasetRow(Arrays.asList(values)));
        }
        return rows;
    }

    private static List<DatasetColumn> toDatasetColumns(final List<ColumnInfo> columns,
            final List<DatasetRow> rows)
    {
        final List<DatasetColumn> result = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++)
        {
            final ColumnInfo column = columns.get(index);
            boolean hasValues = false;
            for (final DatasetRow row : rows)
            {
                if (row.getValue(index) != null)
                {
                    hasValues = true;
                    break;
                }
            }
            result.add(new DatasetColumn(column.displayName(), column.declared(), hasValues,
                    column.pending()));
        }
        return List.copyOf(result);
    }

    private static void addDtdOnlyTables(final List<DatasetTable> tables, final DtdDeclarations dtd,
            final FlatXmlOptions options, final Map<String, TableGroup> groups,
            final Map<String, List<String>> pendingColumns)
    {
        if (dtd == null)
        {
            return;
        }
        for (final DtdTable dtdTable : dtd.tables())
        {
            final String key = tableKey(dtdTable.name(), options);
            if (groups.containsKey(key))
            {
                continue;
            }
            final List<ColumnInfo> columns =
                    buildColumns(List.of(), dtdTable, pendingColumns.getOrDefault(key, List.of()));
            tables.add(new DatasetTable(key, dtdTable.name(), toDatasetColumns(columns, List.of()),
                    List.of(), true));
        }
    }

    private static FlatXmlIndex buildIndex(final FlatXmlParseResult parse, final String text,
            final Map<String, TableGroup> groups)
    {
        final Map<String, String> displayNames = new LinkedHashMap<>();
        final Map<String, List<FlatXmlElement>> rowElementsByKey = new LinkedHashMap<>();
        final Map<String, List<FlatXmlElement>> markerElementsByKey = new LinkedHashMap<>();
        for (final Map.Entry<String, TableGroup> entry : groups.entrySet())
        {
            final TableGroup group = entry.getValue();
            displayNames.put(entry.getKey(), group.displayName);
            rowElementsByKey.put(entry.getKey(), group.rowElements);
            markerElementsByKey.put(entry.getKey(), group.markerElements);
        }
        return new FlatXmlIndex(parse.root(), parse.doctype(), text, parse.elements(), displayNames,
                rowElementsByKey, markerElementsByKey);
    }

    private static String tableKey(final String name, final FlatXmlOptions options)
    {
        return options.caseSensitiveTableNames() ? name : name.toUpperCase(Locale.ENGLISH);
    }

    private static String columnKey(final String name)
    {
        return name.toUpperCase(Locale.ENGLISH);
    }

    /**
     * One table's elements, gathered while walking the document once.
     */
    private static final class TableGroup
    {
        private final String displayName;

        private final List<FlatXmlElement> rowElements = new ArrayList<>();

        private final List<FlatXmlElement> markerElements = new ArrayList<>();

        private TableGroup(final String displayName)
        {
            this.displayName = displayName;
        }
    }

    /**
     * One column, before its rows are known.
     */
    private record ColumnInfo(String displayName, boolean declared, boolean pending)
    {
    }

    /**
     * The model and index built from one parse.
     */
    record Result(DatasetModel model, FlatXmlIndex index)
    {
    }
}
