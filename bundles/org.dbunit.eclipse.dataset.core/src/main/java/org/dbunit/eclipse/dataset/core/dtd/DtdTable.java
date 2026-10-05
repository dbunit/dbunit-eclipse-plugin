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
package org.dbunit.eclipse.dataset.core.dtd;

import java.util.List;
import java.util.Map;

/**
 * One table the DTD declares for the dataset: the element name, its columns in DTD order, and the default
 * value of each column that has one.
 *
 * @param name The element name.
 * @param columns The column names, in declared order; the table keeps its own unmodifiable copy.
 * @param defaults The default value of each column declared with one, by column name. A column declared
 *                 {@code #FIXED} has its fixed value here. A column declared {@code #REQUIRED} or
 *                 {@code #IMPLIED} has none. The table keeps its own unmodifiable copy.
 * @since 1.0.0
 */
public record DtdTable(String name, List<String> columns, Map<String, String> defaults)
{
    /**
     * Creates a table with its own unmodifiable copy of the column names and the defaults, so that neither
     * the caller who created it nor a caller of {@link #columns()} or {@link #defaults()} can change it.
     */
    public DtdTable
    {
        columns = List.copyOf(columns);
        defaults = Map.copyOf(defaults);
    }

    /**
     * Creates a table whose columns have no default values.
     *
     * @param name The element name.
     * @param columns The column names, in declared order.
     */
    public DtdTable(final String name, final List<String> columns)
    {
        this(name, columns, Map.of());
    }
}
