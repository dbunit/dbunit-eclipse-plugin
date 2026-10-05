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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.osgi.util.NLS;

/**
 * The columns that dbUnit takes from the first element of a table, and the rule that an edit must keep
 * them. Without a DTD and without column sensing, dbUnit creates a table's columns from the attributes of the
 * table's first element, and ignores an attribute that a later element has and the first element lacks. An
 * edit that takes a column off the first element while another row has a value for it therefore makes dbUnit
 * stop loading those values, although the grid keeps showing them. This class is meant for one operation.
 */
final class FirstElementColumns
{
    private final DatasetTable table;

    private final Set<Integer> columnIndexes;

    /**
     * Reads the columns that dbUnit takes from the first element of a table.
     *
     * @param context The context of the operation, which tells whether dbUnit takes a table's columns from
     *                its first element.
     * @param tableKey The key of the table.
     * @param table The table.
     */
    FirstElementColumns(final EditContext context, final String tableKey, final DatasetTable table)
    {
        this.table = table;
        this.columnIndexes = columnIndexesOfFirstElement(context, tableKey, table);
    }

    private static Set<Integer> columnIndexesOfFirstElement(final EditContext context, final String tableKey,
            final DatasetTable table)
    {
        if (!context.firstElementDefinesColumns())
        {
            return Set.of();
        }
        final FlatXmlElement first = context.index().getFirstElement(tableKey);
        return first == null ? Set.of() : columnIndexesOf(table, first);
    }

    /**
     * Returns the columns that dbUnit takes from the first element.
     *
     * @return The indexes of the table's columns, in ascending order; empty when dbUnit takes the columns
     *         from elsewhere, or when the first element has no attributes.
     */
    Set<Integer> columnIndexes()
    {
        return columnIndexes;
    }

    /**
     * Returns whether there is no column to keep.
     *
     * @return True when {@link #columnIndexes()} is empty.
     */
    boolean isEmpty()
    {
        return columnIndexes.isEmpty();
    }

    /**
     * Returns the columns that an element has an attribute for.
     *
     * @param table The table whose columns are meant.
     * @param element The element.
     * @return The indexes of the columns, in ascending order.
     */
    static Set<Integer> columnIndexesOf(final DatasetTable table, final FlatXmlElement element)
    {
        final Set<Integer> indexes = new TreeSet<>();
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            final int columnIndex = table.getColumnIndex(attribute.name());
            if (columnIndex >= 0)
            {
                indexes.add(columnIndex);
            }
        }
        return Collections.unmodifiableSet(indexes);
    }

    /**
     * Returns the columns that a row has a value for.
     *
     * @param row The row.
     * @return The indexes of the columns, in ascending order.
     */
    static Set<Integer> columnIndexesWithValues(final DatasetRow row)
    {
        return columnIndexesWithValues(row.getValues());
    }

    /**
     * Returns the columns that a list of values, aligned with the table's columns, has a value for.
     *
     * @param values The values, in which null means NULL.
     * @return The indexes of the columns, in ascending order.
     */
    static Set<Integer> columnIndexesWithValues(final List<String> values)
    {
        final Set<Integer> indexes = new TreeSet<>();
        for (int columnIndex = 0; columnIndex < values.size(); columnIndex++)
        {
            if (values.get(columnIndex) != null)
            {
                indexes.add(columnIndex);
            }
        }
        return Collections.unmodifiableSet(indexes);
    }

    /**
     * Requires that the first element after an edit still has a value for every column that dbUnit takes from
     * the first element now, unless no other row has a value for the column, because nothing is lost then.
     *
     * @param prospectiveColumnIndexes The columns that the first element will have a value for.
     * @param otherRowHasValue Tells whether a row other than the first element will have a value for a
     *                         column, given by its index.
     * @throws DatasetEditException When dbUnit would ignore values that it loads now, naming the first
     *                              column that it would lose.
     */
    void requireKept(final Set<Integer> prospectiveColumnIndexes, final IntPredicate otherRowHasValue)
    {
        for (final int columnIndex : columnIndexes)
        {
            if (!prospectiveColumnIndexes.contains(columnIndex) && otherRowHasValue.test(columnIndex))
            {
                final String columnName = table.getColumns().get(columnIndex).name();
                throw new DatasetEditException(NLS.bind(Messages.Edit_firstRowWouldLoseColumn,
                        new Object[] { columnName, table.getName() }));
            }
        }
    }
}
