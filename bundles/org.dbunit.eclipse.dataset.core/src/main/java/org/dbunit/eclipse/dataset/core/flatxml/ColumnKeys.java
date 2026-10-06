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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;

/**
 * The columns of a table by the upper-cased name that identifies a column: where each column stands among
 * the columns of the table, and how it is spelled. The rewrite of each row of an edit needs the same two
 * lookups, so an edit builds them once and hands them to every rewrite.
 */
final class ColumnKeys
{
    private final Map<String, Integer> indexByKey;

    private final Map<String, String> nameByKey;

    private ColumnKeys(final Map<String, Integer> indexByKey, final Map<String, String> nameByKey)
    {
        this.indexByKey = indexByKey;
        this.nameByKey = nameByKey;
    }

    /**
     * Builds the lookups for the columns of a table. When two columns have the same key, the later one is
     * the one that the key finds.
     *
     * @param columns The columns of the table, in order.
     * @return The lookups.
     */
    static ColumnKeys of(final List<DatasetColumn> columns)
    {
        final Map<String, Integer> indexByKey = new HashMap<>();
        final Map<String, String> nameByKey = new HashMap<>();
        for (int index = 0; index < columns.size(); index++)
        {
            final String name = columns.get(index).name();
            final String key = DatasetColumn.keyOf(name);
            indexByKey.put(key, index);
            nameByKey.put(key, name);
        }
        return new ColumnKeys(indexByKey, nameByKey);
    }

    /**
     * Returns where a column stands among the columns of the table.
     *
     * @param key The upper-cased name of the column.
     * @return The index of the column, or {@link Integer#MAX_VALUE} when the table has no such column, so
     *         that an attribute of an unknown column sorts after the known ones.
     */
    int indexOf(final String key)
    {
        return indexByKey.getOrDefault(key, Integer.MAX_VALUE);
    }

    /**
     * Returns how a column is spelled.
     *
     * @param key The upper-cased name of the column.
     * @return The name of the column as the table spells it, or the key when the table has no such column.
     */
    String nameOf(final String key)
    {
        return nameByKey.getOrDefault(key, key);
    }
}
