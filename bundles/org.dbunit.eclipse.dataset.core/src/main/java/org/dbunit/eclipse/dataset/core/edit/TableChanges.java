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
package org.dbunit.eclipse.dataset.core.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;

/**
 * Which tables of a dataset model came from the tables of the model before it. A change of the source does
 * not say so, whether the editor renamed a table, an undo or a redo did, the user did on the Source page,
 * or the options of the document gave the tables other keys. A table that only the old model has, and a
 * table that only the new model has, with the same columns and the same rows, are one table that was
 * renamed; every other table that only the new model has was added.
 *
 * @param renamedKeys The new key of each renamed table, by its old key, in the order of the old model.
 * @param addedKeys The keys of the added tables, in the order of the new model.
 * @since 1.0.0
 */
public record TableChanges(Map<String, String> renamedKeys, List<String> addedKeys)
{
    /**
     * No table was renamed or added, which is what to assume when nothing says otherwise.
     */
    public static final TableChanges NONE = new TableChanges(Map.of(), List.of());

    /**
     * Creates the changes with unmodifiable copies of the given renames and added keys.
     */
    public TableChanges
    {
        renamedKeys = Collections.unmodifiableMap(new LinkedHashMap<>(renamedKeys));
        addedKeys = List.copyOf(addedKeys);
    }

    /**
     * Works out which tables of a model came from the tables of the model before it. A key that both models
     * have is the same table, whatever changed in it. When two models have a table with the same key, the
     * first of them counts.
     *
     * @param before The model before the change.
     * @param after The model after the change.
     * @return The tables that were renamed and the tables that were added.
     */
    public static TableChanges between(final DatasetModel before, final DatasetModel after)
    {
        final Map<String, DatasetTable> tablesBefore = firstTableOfEachKey(before);
        final Map<String, DatasetTable> tablesAfter = firstTableOfEachKey(after);
        final List<DatasetTable> goneTables = tablesOnlyIn(tablesBefore, tablesAfter);
        final List<DatasetTable> newTables = tablesOnlyIn(tablesAfter, tablesBefore);

        final Map<String, String> renamedKeys = new LinkedHashMap<>();
        for (final DatasetTable goneTable : goneTables)
        {
            final DatasetTable renamedTable = firstWithTheContentOf(goneTable, newTables);
            if (renamedTable != null)
            {
                renamedKeys.put(goneTable.getKey(), renamedTable.getKey());
                newTables.remove(renamedTable);
            }
        }

        final List<String> addedKeys = new ArrayList<>();
        for (final DatasetTable newTable : newTables)
        {
            addedKeys.add(newTable.getKey());
        }
        return new TableChanges(renamedKeys, addedKeys);
    }

    private static Map<String, DatasetTable> firstTableOfEachKey(final DatasetModel model)
    {
        final Map<String, DatasetTable> tables = new LinkedHashMap<>();
        for (final DatasetTable table : model.getTables())
        {
            tables.putIfAbsent(table.getKey(), table);
        }
        return tables;
    }

    private static List<DatasetTable> tablesOnlyIn(final Map<String, DatasetTable> tables,
            final Map<String, DatasetTable> otherTables)
    {
        final List<DatasetTable> only = new ArrayList<>();
        for (final DatasetTable table : tables.values())
        {
            if (!otherTables.containsKey(table.getKey()))
            {
                only.add(table);
            }
        }
        return only;
    }

    private static DatasetTable firstWithTheContentOf(final DatasetTable table,
            final List<DatasetTable> candidates)
    {
        for (final DatasetTable candidate : candidates)
        {
            if (haveTheSameContent(table, candidate))
            {
                return candidate;
            }
        }
        return null;
    }

    private static boolean haveTheSameContent(final DatasetTable first, final DatasetTable second)
    {
        return columnNames(first).equals(columnNames(second)) && haveTheSameRows(first, second);
    }

    private static List<String> columnNames(final DatasetTable table)
    {
        final List<String> names = new ArrayList<>();
        for (final DatasetColumn column : table.getColumns())
        {
            names.add(column.name());
        }
        return names;
    }

    private static boolean haveTheSameRows(final DatasetTable first, final DatasetTable second)
    {
        final List<DatasetRow> firstRows = first.getRows();
        final List<DatasetRow> secondRows = second.getRows();
        if (firstRows.size() != secondRows.size())
        {
            return false;
        }
        for (int rowIndex = 0; rowIndex < firstRows.size(); rowIndex++)
        {
            final List<String> firstValues = firstRows.get(rowIndex).getValues();
            final List<String> secondValues = secondRows.get(rowIndex).getValues();
            if (!firstValues.equals(secondValues))
            {
                return false;
            }
        }
        return true;
    }
}
