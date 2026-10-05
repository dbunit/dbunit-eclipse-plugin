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
import java.util.Locale;

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

    private final boolean declaredInExternalDtd;

    /**
     * Creates a dataset table that no external DTD declares.
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
        this(key, name, columns, rows, declaredOnly, false);
    }

    /**
     * Creates a dataset table.
     *
     * @param key The identity of the table across refreshes: the upper-cased name (Locale.ENGLISH), or
     *            the exact name when table names are case-sensitive.
     * @param name The spelling of the first element of the table, or of its DTD declaration.
     * @param columns The columns of the table, copied defensively.
     * @param rows The rows of the table, copied defensively.
     * @param declaredOnly True when the table is declared in the DTD but has no element in the document.
     * @param declaredInExternalDtd True when an external DTD declares the table.
     */
    public DatasetTable(final String key, final String name, final List<DatasetColumn> columns,
            final List<DatasetRow> rows, final boolean declaredOnly, final boolean declaredInExternalDtd)
    {
        this.key = key;
        this.name = name;
        this.columns = List.copyOf(columns);
        this.rows = List.copyOf(rows);
        this.declaredOnly = declaredOnly;
        this.declaredInExternalDtd = declaredInExternalDtd;
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
     * @param columnName The column name to look up. A column matches when its name and this name are equal
     *            after upper-casing them with Locale.ENGLISH, which is how the rest of the plugin keys
     *            columns.
     * @return The index of the first matching column, or -1 when no column matches.
     */
    public int getColumnIndex(final String columnName)
    {
        final String columnKey = columnName.toUpperCase(Locale.ENGLISH);
        for (int index = 0; index < columns.size(); index++)
        {
            final DatasetColumn column = columns.get(index);
            if (column.name().toUpperCase(Locale.ENGLISH).equals(columnKey))
            {
                return index;
            }
        }
        return -1;
    }

    /**
     * Returns the value dbUnit loads for a cell.
     *
     * @param rowIndex The index of the cell's row.
     * @param columnIndex The index of the cell's column.
     * @return The cell's value, or, when the row has no attribute for the column, the column's default
     *         value, which is null when the column has none.
     * @throws IndexOutOfBoundsException When the table has no such row or column.
     */
    public String getEffectiveValue(final int rowIndex, final int columnIndex)
    {
        final String value = rows.get(rowIndex).getValue(columnIndex);
        return columns.get(columnIndex).effectiveValue(value);
    }

    /**
     * Returns whether a row without any attribute would load a value for some column.
     *
     * @return True when at least one column has a default value, so an element without attributes is a
     *         row of the table, not just a marker that the table exists.
     */
    public boolean hasDefaultValues()
    {
        for (final DatasetColumn column : columns)
        {
            if (column.hasDefaultValue())
            {
                return true;
            }
        }
        return false;
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

    /**
     * Returns whether a DTD file that the dataset's DOCTYPE names declares the table. The editor does not
     * change such a file, so a rename of the table leaves the declaration in it under the old name.
     *
     * @return True when the DTD file declares the table with an {@code ELEMENT} or {@code ATTLIST}
     *         declaration or lists it in the content model of the {@code dataset} element.
     */
    public boolean isDeclaredInExternalDtd()
    {
        return declaredInExternalDtd;
    }
}
