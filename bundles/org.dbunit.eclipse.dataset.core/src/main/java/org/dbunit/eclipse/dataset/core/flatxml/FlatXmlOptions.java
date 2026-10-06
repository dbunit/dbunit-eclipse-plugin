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

import org.dbunit.eclipse.dataset.core.model.DatasetTable;

/**
 * The options that change how a flat XML document is read, matching dbUnit's
 * {@code FlatXmlDataSetBuilder}.
 *
 * @param caseSensitiveTableNames True when table names must match exactly instead of case-insensitively.
 * @param columnSensing True when an attribute missing from a table's first element still becomes a
 *                       column, instead of being ignored.
 * @since 1.0.0
 */
public record FlatXmlOptions(boolean caseSensitiveTableNames, boolean columnSensing)
{
    /**
     * The options that dbUnit itself uses by default, which match table names case-insensitively and do not
     * sense columns.
     */
    public static final FlatXmlOptions DBUNIT_DEFAULTS = new FlatXmlOptions(false, false);

    /**
     * Returns the key of a table, which identifies it across refreshes: its name, as these options match
     * the names of tables.
     *
     * @param tableName The name of the table, as its first element spells it.
     * @return The key, as {@link DatasetTable#keyOf} gives it for these options.
     */
    public String tableKey(final String tableName)
    {
        return DatasetTable.keyOf(tableName, caseSensitiveTableNames);
    }
}
