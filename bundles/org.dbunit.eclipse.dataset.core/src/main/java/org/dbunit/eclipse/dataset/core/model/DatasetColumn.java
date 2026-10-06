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

import java.util.Locale;

/**
 * One column of a dataset table.
 *
 * @param name The name of the column.
 * @param declared True when the column is declared for this table in the DTD.
 * @param hasValues True when at least one row has a non-null value for this column.
 * @param pending True when the column was added in this editor session and is not yet saved in the
 *                document.
 * @param defaultValue The value dbUnit loads for a row that has no attribute for this column: the default
 *                     or {@code #FIXED} value that the DTD declares for it, or null when the DTD declares
 *                     none and a row without the attribute loads NULL.
 * @since 1.0.0
 */
public record DatasetColumn(String name, boolean declared, boolean hasValues, boolean pending,
        String defaultValue)
{
    /**
     * Creates a column that has no default value.
     *
     * @param name The name of the column.
     * @param declared True when the column is declared for this table in the DTD.
     * @param hasValues True when at least one row has a non-null value for this column.
     * @param pending True when the column was added in this editor session and is not yet saved in the
     *                document.
     */
    public DatasetColumn(final String name, final boolean declared, final boolean hasValues,
            final boolean pending)
    {
        this(name, declared, hasValues, pending, null);
    }

    /**
     * Returns the key that identifies a column among the columns of a table: its name in upper case, with
     * Locale.ENGLISH so that the key does not depend on the language of the user. Two names with one key
     * name one column, as dbUnit matches the names of columns without regard to letter case.
     *
     * @param columnName The name of a column, in any letter case.
     * @return The key of the column.
     */
    public static String keyOf(final String columnName)
    {
        return columnName.toUpperCase(Locale.ENGLISH);
    }

    /**
     * Returns the key of this column.
     *
     * @return The key of the name of this column, as {@link #keyOf} gives it.
     */
    public String key()
    {
        return keyOf(name);
    }

    /**
     * Returns whether a row without an attribute for this column loads a value other than NULL.
     *
     * @return True when the DTD declares a default value for the column.
     */
    public boolean hasDefaultValue()
    {
        return defaultValue != null;
    }

    /**
     * Returns the value dbUnit loads for a cell of this column.
     *
     * @param value The cell's value, which is null when the row has no attribute for the column.
     * @return The value when it is not null, otherwise the column's default value, which is null when the
     *         column has none.
     */
    public String effectiveValue(final String value)
    {
        return value == null ? defaultValue : value;
    }
}
