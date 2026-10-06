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

import java.util.List;

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
    void testIsValidName_whenNameHasACharacterBeyondTheBasicPlane_returnsFalse()
    {
        final String supplementary = new String(Character.toChars(0x20000));

        assertThat(List.of(XmlNames.isValidName(supplementary + "TABLE"),
                XmlNames.isValidName("TABLE" + supplementary), XmlNames.isNameStartChar(0x10000),
                XmlNames.isNameChar(0x10000)))
                .as("The XML parser of the Java runtime refuses a character beyond U+FFFF in a name, though "
                        + "the fifth edition of XML 1.0 allows it, so dbUnit cannot load such a name.")
                .containsExactly(false, false, false, false);
    }

    @ParameterizedTest
    @ValueSource(ints = { ':', 'A', 'Z', '_', 'a', 'z', 0xC0, 0xD6, 0xD8, 0xF6, 0xF8, 0x131, 0x134, 0x13E,
            0xE01, 0x1E9B, 0x3007, 0x3021, 0x3029, 0x4E00, 0x9FA5, 0xAC00, 0xD7A3 })
    void testIsNameStartChar_whenCodePointIsALetterOfTheFourthEdition_returnsTrue(final int codePoint)
    {
        assertThat(XmlNames.isNameStartChar(codePoint)).as("U+%04X must be a NameStartChar.", codePoint)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = { '-', '.', '0', '9', ';', '<', '@', '[', '^', '`', '{', 0xB7, 0xBF, 0xD7, 0xF7,
            0x132, 0x2FF, 0x300, 0x36F, 0x370, 0x37E, 0x387, 0x2000, 0x200C, 0x203F, 0x2070, 0x2190, 0x2460,
            0x2C00, 0x3000, 0x3001, 0x9FA6, 0xD7A4, 0xD7FF, 0xD800, 0xF900, 0xFB00, 0xFDF0, 0xFFFD, 0xFFFE,
            0x10000, 0x20000, 0xE0000 })
    void testIsNameStartChar_whenCodePointIsNoLetterOfTheFourthEdition_returnsFalse(final int codePoint)
    {
        assertThat(XmlNames.isNameStartChar(codePoint)).as("U+%04X must not be a NameStartChar.", codePoint)
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = { '-', '.', '0', '9', ':', 'a', 0xB7, 0x300, 0x345, 0x360, 0x361, 0x387, 0x660, 0x669,
            0xE01, 0x3007 })
    void testIsNameChar_whenCodePointIsPartOfANameOfTheFourthEdition_returnsTrue(final int codePoint)
    {
        assertThat(XmlNames.isNameChar(codePoint)).as("U+%04X must be a NameChar.", codePoint).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = { ' ', '/', '<', '=', '>', 0xD7, 0xF7, 0x2000, 0x2190, 0x3001, 0x9FA6, 0xD800, 0xF900,
            0xFFFE, 0x10000, 0x20000 })
    void testIsNameChar_whenCodePointIsNoPartOfANameOfTheFourthEdition_returnsFalse(final int codePoint)
    {
        assertThat(XmlNames.isNameChar(codePoint)).as("U+%04X must not be a NameChar.", codePoint).isFalse();
    }
}
