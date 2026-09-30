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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlElement}: the check for attributes that differ only in letter case.
 */
class FlatXmlElementTest
{
    private static FlatXmlAttribute attribute(final String name)
    {
        return new FlatXmlAttribute(name, "v", 0, 0, 0, 0, '"');
    }

    private static FlatXmlElement elementWith(final String... attributeNames)
    {
        final List<FlatXmlAttribute> attributes = Arrays.stream(attributeNames)
                .map(FlatXmlElementTest::attribute).toList();
        return new FlatXmlElement("USERS", 0, 6, attributes, 0, 0, true, -1, 0);
    }

    @Test
    void testHasCaseVariantAttributes_whenTheElementHasNoAttributes_isFalse()
    {
        assertThat(elementWith().hasCaseVariantAttributes("ID")).as("There is nothing to compare.").isFalse();
    }

    @Test
    void testHasCaseVariantAttributes_whenOneAttributeMatches_isFalse()
    {
        assertThat(elementWith("id", "NAME").hasCaseVariantAttributes("ID"))
                .as("A single attribute is not a variant of anything.").isFalse();
    }

    @Test
    void testHasCaseVariantAttributes_whenTwoAttributesDifferOnlyInCase_isTrue()
    {
        assertThat(elementWith("ID", "NAME", "id").hasCaseVariantAttributes("ID"))
                .as("ID and id are two spellings of one column.").isTrue();
    }

    @Test
    void testHasCaseVariantAttributes_whenThreeAttributesMatch_isTrue()
    {
        assertThat(elementWith("Id", "iD", "ID").hasCaseVariantAttributes("ID"))
                .as("Three spellings are more than one.").isTrue();
    }

    @Test
    void testHasCaseVariantAttributes_whenOnlyAnotherColumnHasVariants_isFalse()
    {
        assertThat(elementWith("ID", "name", "NAME").hasCaseVariantAttributes("ID"))
                .as("Variants of other columns must not count.").isFalse();
    }

    @Test
    void testHasCaseVariantAttributes_whenTheKeyIsNotUpperCase_isFalse()
    {
        assertThat(elementWith("ID", "id").hasCaseVariantAttributes("id"))
                .as("The key is compared with the upper-cased names, so it must be upper-cased.").isFalse();
    }
}
