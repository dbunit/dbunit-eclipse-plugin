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
package org.dbunit.eclipse.dataset.core.model;

import java.util.List;

/**
 * An immutable snapshot of one table of a dataset.
 *
 * @since 1.0.0
 */
public final class DatasetTable
{
    private final String key;

    private final String name;

    private final List<DatasetColumn> columns;

    private final List<DatasetRow> rows;

    private final boolean declaredOnly;

    /**
     * Creates a dataset table.
     *
     * @param key The identity of the table across refreshes: the upper-cased name (Locale.ENGLISH), or
     *            the exact name when table names are case-sensitive.
     * @param name The spelling of the first element of the table, or of its DTD declaration.
     * @param columns The columns of the table, copied defensively.
     * @param rows The rows of the table, copied defensively.
     * @param declaredOnly True when the table is declared in the DTD but has no element in the document.
     */
    public DatasetTable(final String key, final String name, final List<DatasetColumn> columns,
            final List<DatasetRow> rows, final boolean declaredOnly)
    {
        this.key = key;
        this.name = name;
        this.columns = List.copyOf(columns);
        this.rows = List.copyOf(rows);
        this.declaredOnly = declaredOnly;
    }

    /**
     * Returns the identity of the table across refreshes.
     *
     * @return The upper-cased name (Locale.ENGLISH), or the exact name when table names are
     *         case-sensitive.
     */
    public String getKey()
    {
        return key;
    }

    /**
     * Returns the display spelling of the table.
     *
     * @return The spelling of the first element of the table, or of its DTD declaration.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the columns of the table.
     *
     * @return An unmodifiable list of columns.
     */
    public List<DatasetColumn> getColumns()
    {
        return columns;
    }

    /**
     * Returns the rows of the table.
     *
     * @return An unmodifiable list of rows.
     */
    public List<DatasetRow> getRows()
    {
        return rows;
    }

    /**
     * Finds the index of a column by name.
     *
     * @param columnName The column name to look up, matched case-insensitively.
     * @return The index of the matching column, or -1 when no column matches.
     */
    public int getColumnIndex(final String columnName)
    {
        for (int index = 0; index < columns.size(); index++)
        {
            final DatasetColumn column = columns.get(index);
            if (column.name().equalsIgnoreCase(columnName))
            {
                return index;
            }
        }
        return -1;
    }

    /**
     * Returns whether the table exists only because the DTD declares it.
     *
     * @return True when the table is declared in the DTD but has no element in the document.
     */
    public boolean isDeclaredOnly()
    {
        return declaredOnly;
    }
}
