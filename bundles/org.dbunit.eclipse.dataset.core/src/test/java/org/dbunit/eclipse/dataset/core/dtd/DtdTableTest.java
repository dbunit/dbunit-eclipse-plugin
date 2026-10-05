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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests that {@link DtdTable} keeps its column list and its defaults to itself.
 */
class DtdTableTest
{
    @Test
    void testConstructor_whenTheGivenListChangesAfterwards_keepsTheColumnsItWasGiven()
    {
        final List<String> columns = new ArrayList<>(List.of("ID", "NAME"));
        final DtdTable table = new DtdTable("USERS", columns);

        columns.add("EXTRA");

        assertThat(table).as("A later change to the caller's list must not change the table.")
                .isEqualTo(new DtdTable("USERS", List.of("ID", "NAME")));
    }

    @Test
    void testColumns_whenTheReturnedListIsModified_throws()
    {
        final DtdTable table = new DtdTable("USERS", new ArrayList<>(List.of("ID", "NAME")));

        assertThatThrownBy(() -> table.columns().add("EXTRA"))
                .as("The returned column list must not let a caller change the table.")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testConstructor_whenTheGivenDefaultsChangeAfterwards_keepsTheDefaultsItWasGiven()
    {
        final Map<String, String> defaults = new HashMap<>(Map.of("NAME", "x"));
        final DtdTable table = new DtdTable("USERS", List.of("ID", "NAME"), defaults);

        defaults.put("ID", "0");

        assertThat(table).as("A later change to the caller's map must not change the table.")
                .isEqualTo(new DtdTable("USERS", List.of("ID", "NAME"), Map.of("NAME", "x")));
    }

    @Test
    void testDefaults_whenTheReturnedMapIsModified_throws()
    {
        final DtdTable table = new DtdTable("USERS", List.of("NAME"), new HashMap<>(Map.of("NAME", "x")));

        assertThatThrownBy(() -> table.defaults().put("ID", "0"))
                .as("The returned map must not let a caller change the table.")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testConstructor_whenGivenNoDefaults_isEqualToATableWithAnEmptyDefaultsMap()
    {
        final DtdTable table = new DtdTable("USERS", List.of("ID"));

        assertThat(table).as("A table created without defaults must have none.")
                .isEqualTo(new DtdTable("USERS", List.of("ID"), Map.of()));
    }
}
