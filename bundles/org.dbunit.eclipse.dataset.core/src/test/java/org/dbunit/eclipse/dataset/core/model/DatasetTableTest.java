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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests case-insensitive column lookup and the plain getters of a dataset table.
 */
class DatasetTableTest
{
    private static final DatasetColumn ID_COLUMN = new DatasetColumn("ID", true, true, false);

    private static final DatasetColumn NAME_COLUMN = new DatasetColumn("NAME", true, true, false);

    @Test
    void testGetColumnIndex_whenNameMatchesIgnoringCase_returnsTheIndex()
    {
        final DatasetTable table = new DatasetTable("USERS", "USERS", List.of(ID_COLUMN, NAME_COLUMN),
                List.of(), false);

        assertThat(table.getColumnIndex("name")).as("Column lookup must be case-insensitive.")
                .isEqualTo(1);
    }

    @Test
    void testGetColumnIndex_whenNameIsAbsent_returnsMinusOne()
    {
        final DatasetTable table = new DatasetTable("USERS", "USERS", List.of(ID_COLUMN, NAME_COLUMN),
                List.of(), false);

        assertThat(table.getColumnIndex("EMAIL")).as("A column that does not exist must return -1.")
                .isEqualTo(-1);
    }

    @Test
    void testGetters_whenConstructed_returnTheGivenValues()
    {
        final DatasetRow row = new DatasetRow(List.of("1", "Alice"));
        final DatasetTable table = new DatasetTable("USERS", "Users", List.of(ID_COLUMN, NAME_COLUMN),
                List.of(row), true);

        assertThat(table.getKey()).as("getKey must return the given key.").isEqualTo("USERS");
        assertThat(table.getName()).as("getName must return the given display name.").isEqualTo("Users");
        assertThat(table.getColumns()).as("getColumns must return the given columns.")
                .containsExactly(ID_COLUMN, NAME_COLUMN);
        assertThat(table.getRows()).as("getRows must return the given rows.").containsExactly(row);
        assertThat(table.isDeclaredOnly()).as("isDeclaredOnly must return the given flag.").isTrue();
    }
}
