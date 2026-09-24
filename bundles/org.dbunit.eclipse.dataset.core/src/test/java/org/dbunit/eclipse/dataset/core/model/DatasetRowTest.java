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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests that a dataset row preserves null values and never exposes or shares its internal list.
 */
class DatasetRowTest
{
    @Test
    void testGetValues_whenAValueIsNull_preservesTheNullValue()
    {
        final DatasetRow row = new DatasetRow(Arrays.asList("a", null, "b"));

        assertThat(row.getValues()).as("A null value means NULL and must be preserved.")
                .containsExactly("a", null, "b");
    }

    @Test
    void testConstructor_whenTheSourceListIsModifiedAfterward_leavesTheRowUnaffected()
    {
        final List<String> source = new ArrayList<>(Arrays.asList("a", "b"));
        final DatasetRow row = new DatasetRow(source);

        source.set(0, "changed");

        assertThat(row.getValues()).as("The constructor must copy the values defensively.")
                .containsExactly("a", "b");
    }

    @Test
    void testGetValues_whenCalled_returnsAnUnmodifiableList()
    {
        final DatasetRow row = new DatasetRow(Arrays.asList("a", "b"));

        final List<String> values = row.getValues();

        assertThatThrownBy(() -> values.add("c")).as("The returned list must be unmodifiable.")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testGetValueAndSize_whenConstructed_matchTheGivenValues()
    {
        final DatasetRow row = new DatasetRow(Arrays.asList("a", null, "c"));

        assertThat(row.size()).as("The size must equal the number of values given.").isEqualTo(3);
        assertThat(row.getValue(1)).as("A null value must be returned as null.").isNull();
        assertThat(row.getValue(2)).as("A non-null value must be returned unchanged.").isEqualTo("c");
    }
}
