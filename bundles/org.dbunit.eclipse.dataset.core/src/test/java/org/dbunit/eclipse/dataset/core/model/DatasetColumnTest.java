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

import org.junit.jupiter.api.Test;

/**
 * Tests which value dbUnit loads for a cell of a {@link DatasetColumn}.
 */
class DatasetColumnTest
{
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
