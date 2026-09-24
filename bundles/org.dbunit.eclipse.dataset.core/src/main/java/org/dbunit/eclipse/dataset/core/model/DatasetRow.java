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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable snapshot of one row of a dataset table.
 *
 * @since 1.0.0
 */
public final class DatasetRow
{
    private final List<String> values;

    /**
     * Creates a dataset row.
     *
     * @param values The values of the row, aligned with the table columns and copied defensively; a
     *               null value means NULL.
     */
    public DatasetRow(final List<String> values)
    {
        this.values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * Returns the value of one column.
     *
     * @param columnIndex The index of the column.
     * @return The value, or null when the value is NULL.
     */
    public String getValue(final int columnIndex)
    {
        return values.get(columnIndex);
    }

    /**
     * Returns the number of values in the row.
     *
     * @return The number of values.
     */
    public int size()
    {
        return values.size();
    }

    /**
     * Returns the values of the row.
     *
     * @return An unmodifiable list that may contain null.
     */
    public List<String> getValues()
    {
        return values;
    }
}
