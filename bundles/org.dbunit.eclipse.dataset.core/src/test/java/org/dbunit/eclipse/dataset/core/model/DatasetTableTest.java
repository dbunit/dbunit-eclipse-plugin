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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Tests case-insensitive column lookup, the plain getters, and the default values of a dataset table.
 */
class DatasetTableTest
{
    private static final DatasetColumn ID_COLUMN = new DatasetColumn("ID", true, true, false);

    private static final DatasetColumn NAME_COLUMN = new DatasetColumn("NAME", true, true, false);

    @Test
    void testKeyOf_whenTableNamesAreNotCaseSensitive_returnsTheNameInUpperCase()
    {
        assertThat(DatasetTable.keyOf("Users", false)).as("A table is keyed by its upper-cased name.")
                .isEqualTo("USERS");
    }

    @Test
    void testKeyOf_whenTableNamesAreCaseSensitive_returnsTheNameAsItIs()
    {
        assertThat(DatasetTable.keyOf("Users", true)).as("The exact name is the key of a table whose name "
                + "is case-sensitive.").isEqualTo("Users");
    }

    @Test
    void testKeyOf_whenTheDefaultLocaleUpperCasesIDifferently_doesNotFollowIt()
    {
        final Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try
        {
            assertThat(DatasetTable.keyOf("titles", false))
                    .as("The key must not depend on the language of the user.").isEqualTo("TITLES");
        }
        finally
        {
            Locale.setDefault(defaultLocale);
        }
    }

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
    void testGetColumnIndex_whenAnotherColumnIsEqualIgnoringCaseYetDistinct_returnsTheColumnWithTheSameKey()
    {
        final DatasetColumn dottedColumn = new DatasetColumn("\u0130D", true, true, false);
        final DatasetTable table = new DatasetTable("USERS", "USERS", List.of(dottedColumn, ID_COLUMN),
                List.of(), false);

        assertThat(table.getColumnIndex("ID"))
                .as("The ID column must be found, not the earlier column whose name equals it only "
                        + "ignoring case.")
                .isEqualTo(1);
        assertThat(table.getColumnIndex("\u0130D")).as("The column with the dotted capital I must be found.")
                .isEqualTo(0);
    }

    @Test
    void testGetColumnIndex_whenTheNameSharesAKeyYetIsNotEqualIgnoringCase_returnsTheIndex()
    {
        final DatasetColumn column = new DatasetColumn("straße", true, true, false);
        final DatasetTable table = new DatasetTable("ADDRESSES", "ADDRESSES", List.of(ID_COLUMN, column),
                List.of(), false);

        assertThat(table.getColumnIndex("STRASSE"))
                .as("A name that upper-cases to the column's key must find it, although the two names are "
                        + "not equal ignoring case.")
                .isEqualTo(1);
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

    @Test
    void testIsDeclaredInExternalDtd_whenGivenNoFlag_isFalse()
    {
        final DatasetTable table = new DatasetTable("USERS", "USERS", List.of(ID_COLUMN), List.of(), false);

        assertThat(table.isDeclaredInExternalDtd())
                .as("A table created without the flag must not claim an external DTD declares it.")
                .isFalse();
    }

    @Test
    void testIsDeclaredInExternalDtd_whenGivenTheFlag_returnsIt()
    {
        final DatasetTable table =
                new DatasetTable("USERS", "USERS", List.of(ID_COLUMN), List.of(), false, true);

        assertThat(table.isDeclaredInExternalDtd()).as("isDeclaredInExternalDtd must return the given flag.")
                .isTrue();
    }

    @Test
    void testGetEffectiveValue_whenTheCellHasAValue_returnsTheValueNotTheDefault()
    {
        final DatasetTable table = tableWithDefaultStatus(List.of("1", "x"));

        assertThat(table.getEffectiveValue(0, 1))
                .as("An attribute that is present must win over the default.").isEqualTo("x");
    }

    @Test
    void testGetEffectiveValue_whenTheCellHasNoValueAndTheColumnHasADefault_returnsTheDefault()
    {
        final DatasetTable table = tableWithDefaultStatus(Arrays.asList("1", null));

        assertThat(table.getEffectiveValue(0, 1)).as("An absent attribute loads the column's default.")
                .isEqualTo("ACTIVE");
    }

    @Test
    void testGetEffectiveValue_whenTheCellHasNoValueAndTheColumnHasNoDefault_returnsNull()
    {
        final DatasetTable table = tableWithDefaultStatus(Arrays.asList(null, "x"));

        assertThat(table.getEffectiveValue(0, 0))
                .as("An absent attribute of a column without a default is NULL.").isNull();
    }

    @Test
    void testHasDefaultValues_whenSomeColumnHasADefault_returnsTrue()
    {
        assertThat(tableWithDefaultStatus(List.of("1", "x")).hasDefaultValues())
                .as("One column with a default is enough.").isTrue();
    }

    @Test
    void testHasDefaultValues_whenNoColumnHasADefault_returnsFalse()
    {
        final DatasetTable table = new DatasetTable("USERS", "USERS", List.of(ID_COLUMN, NAME_COLUMN),
                List.of(), false);

        assertThat(table.hasDefaultValues()).as("Without any default, an empty element is not a row.")
                .isFalse();
    }

    private static DatasetTable tableWithDefaultStatus(final List<String> rowValues)
    {
        final DatasetColumn statusColumn = new DatasetColumn("STATUS", true, true, false, "ACTIVE");
        return new DatasetTable("USERS", "USERS", List.of(ID_COLUMN, statusColumn),
                List.of(new DatasetRow(rowValues)), false);
    }
}
