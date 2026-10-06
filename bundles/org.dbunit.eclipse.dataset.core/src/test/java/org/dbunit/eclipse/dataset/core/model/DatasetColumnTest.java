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

import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Tests the key of a {@link DatasetColumn}, and which value dbUnit loads for a cell of it.
 */
class DatasetColumnTest
{
    @Test
    void testKeyOf_forAName_returnsItInUpperCase()
    {
        assertThat(DatasetColumn.keyOf("Name")).as("A column is keyed by its upper-cased name.")
                .isEqualTo("NAME");
    }

    @Test
    void testKeyOf_forNamesThatDifferOnlyInCase_returnsOneKey()
    {
        assertThat(DatasetColumn.keyOf("name")).as("Two spellings of a name name one column.")
                .isEqualTo(DatasetColumn.keyOf("NAME"));
    }

    @Test
    void testKeyOf_whenTheDefaultLocaleUpperCasesIDifferently_doesNotFollowIt()
    {
        final Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try
        {
            assertThat(DatasetColumn.keyOf("title"))
                    .as("The key must not depend on the language of the user, which Turkish upper-cases i "
                            + "to a dotted capital I.")
                    .isEqualTo("TITLE");
        }
        finally
        {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void testKey_whenTheNameHasLowerCaseLetters_returnsTheKeyOfTheName()
    {
        final DatasetColumn column = new DatasetColumn("Name", false, true, false);

        assertThat(column.key()).as("The key of a column is the key of its name.").isEqualTo("NAME");
    }

    @Test
    void testEffectiveValue_whenTheCellHasAValue_returnsTheValue()
    {
        final DatasetColumn column = new DatasetColumn("STATUS", true, true, false, "ACTIVE");

        assertThat(column.effectiveValue("x")).as("A value that is present must win over the default.")
                .isEqualTo("x");
    }

    @Test
    void testEffectiveValue_whenTheCellHasTheEmptyString_returnsTheEmptyString()
    {
        final DatasetColumn column = new DatasetColumn("STATUS", true, true, false, "ACTIVE");

        assertThat(column.effectiveValue("")).as("An empty string is a value, not an absent attribute.")
                .isEmpty();
    }

    @Test
    void testEffectiveValue_whenTheCellHasNoValue_returnsTheDefault()
    {
        final DatasetColumn column = new DatasetColumn("STATUS", true, false, false, "ACTIVE");

        assertThat(column.effectiveValue(null)).as("An absent attribute loads the default.")
                .isEqualTo("ACTIVE");
    }

    @Test
    void testEffectiveValue_whenTheColumnHasNoDefault_returnsNullForAnAbsentValue()
    {
        final DatasetColumn column = new DatasetColumn("STATUS", true, false, false);

        assertThat(column.effectiveValue(null)).as("Without a default, an absent attribute is NULL.")
                .isNull();
    }

    @Test
    void testHasDefaultValue_whenTheDefaultIsTheEmptyString_returnsTrue()
    {
        final DatasetColumn column = new DatasetColumn("STATUS", true, false, false, "");

        assertThat(column.hasDefaultValue()).as("An empty default still loads a value, not NULL.").isTrue();
    }

    @Test
    void testHasDefaultValue_whenTheColumnIsCreatedWithoutADefault_returnsFalse()
    {
        final DatasetColumn column = new DatasetColumn("STATUS", true, false, false);

        assertThat(column.hasDefaultValue()).as("The four-argument constructor must leave the column "
                + "without a default.").isFalse();
    }
}
