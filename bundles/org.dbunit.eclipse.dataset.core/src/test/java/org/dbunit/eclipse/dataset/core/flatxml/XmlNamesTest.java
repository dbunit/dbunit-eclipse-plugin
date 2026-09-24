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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the XML 1.0 Name production that dbUnit flat XML table and column names must follow.
 */
class XmlNamesTest
{
    @ParameterizedTest
    @ValueSource(strings = { "USERS", "order_items", "a.b-c", "ns:TABLE", "Ä1" })
    void testIsValidName_whenNameFollowsTheXmlNameProduction_returnsTrue(final String name)
    {
        assertThat(XmlNames.isValidName(name)).as("'" + name + "' must be a valid XML 1.0 name.").isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "1ABC", "-a", "a b", "a<b" })
    void testIsValidName_whenNameViolatesTheXmlNameProduction_returnsFalse(final String name)
    {
        assertThat(XmlNames.isValidName(name)).as("'" + name + "' must not be a valid XML 1.0 name.")
                .isFalse();
    }

    @Test
    void testIsValidNameAndIsNameStartChar_whenNameStartsWithASupplementaryCharacter_returnsTrue()
    {
        final int supplementaryNameStartChar = 0x10000;
        final String name = new String(Character.toChars(supplementaryNameStartChar)) + "TABLE";

        assertThat(XmlNames.isNameStartChar(supplementaryNameStartChar))
                .as("U+10000 must be a valid NameStartChar.").isTrue();
        assertThat(XmlNames.isValidName(name))
                .as("A name starting with a supplementary character must be valid.").isTrue();
    }
}
