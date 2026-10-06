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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Tests the XML 1.0 Name production that dbUnit flat XML table and column names must follow, and compares it
 * with the XML parser of the Java runtime, which dbUnit loads a dataset with: for every code point of the
 * basic plane, and for a sample beyond it, the parser accepts the code point in a name exactly when
 * {@code XmlNames} does, at the start of a name and inside it.
 */
class XmlNamesTest
{
    private static final int LAST_BASIC_PLANE_CODE_POINT = 0xFFFF;

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
    void testIsWhitespace_whenTheCharacterIsSpaceTabLineFeedOrCarriageReturn_isTrue()
    {
        assertThat(List.of(XmlNames.isWhitespace(' '), XmlNames.isWhitespace('\t'),
                XmlNames.isWhitespace('\n'), XmlNames.isWhitespace('\r')))
                .as("The four XML whitespace characters must all be whitespace.")
                .containsExactly(true, true, true, true);
    }

    @Test
    void testIsWhitespace_whenTheCharacterIsAnyOther_isFalse()
    {
        assertThat(List.of(XmlNames.isWhitespace('a'), XmlNames.isWhitespace('\f'),
                XmlNames.isWhitespace((char) 0xB), XmlNames.isWhitespace((char) 0xA0),
                XmlNames.isWhitespace((char) 0x2003), XmlNames.isWhitespace((char) 0x2028),
                XmlNames.isWhitespace((char) 0x3000)))
                .as("Letters, the form feed, the vertical tab, the no-break space, the em space, the line "
                        + "separator, and the ideographic space are not XML whitespace, though some of them "
                        + "are whitespace to Java.")
                .containsOnly(false);
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

    @Test
    void testIsNameStartChar_forEveryBasicPlaneCodePoint_agreesWithTheXmlParserOfTheJavaRuntime()
            throws Exception
    {
        final SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
        final List<String> disagreements = new ArrayList<>();

        for (int codePoint = 0; codePoint <= LAST_BASIC_PLANE_CODE_POINT; codePoint++)
        {
            final boolean parserAccepts = parses(parser, "<" + asText(codePoint) + "b/>");
            if (parserAccepts != XmlNames.isNameStartChar(codePoint))
            {
                disagreements.add(String.format("U+%04X: parser %s", codePoint, parserAccepts));
            }
        }

        assertThat(disagreements).as("Every code point that starts a name for the parser must start one for "
                + "XmlNames, and no other.").isEmpty();
    }

    @Test
    void testIsNameChar_forEveryBasicPlaneCodePoint_agreesWithTheXmlParserOfTheJavaRuntime() throws Exception
    {
        final SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
        final List<String> disagreements = new ArrayList<>();

        for (int codePoint = 0; codePoint <= LAST_BASIC_PLANE_CODE_POINT; codePoint++)
        {
            final boolean parserAccepts = parses(parser, "<a" + asText(codePoint) + "b/>");
            if (parserAccepts != XmlNames.isNameChar(codePoint))
            {
                disagreements.add(String.format("U+%04X: parser %s", codePoint, parserAccepts));
            }
        }

        assertThat(disagreements).as("Every code point that may be part of a name for the parser must be one "
                + "for XmlNames, and no other.").isEmpty();
    }

    @Test
    void testIsNameStartCharAndIsNameChar_forCodePointsBeyondTheBasicPlane_agreeWithTheParser()
            throws Exception
    {
        final SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
        final List<String> disagreements = new ArrayList<>();

        for (int codePoint = 0x10000; codePoint <= Character.MAX_CODE_POINT; codePoint += 0x101)
        {
            final boolean startAccepted = parses(parser, "<" + asText(codePoint) + "b/>");
            final boolean insideAccepted = parses(parser, "<a" + asText(codePoint) + "b/>");
            if (startAccepted != XmlNames.isNameStartChar(codePoint)
                    || insideAccepted != XmlNames.isNameChar(codePoint))
            {
                disagreements.add(String.format("U+%05X", codePoint));
            }
        }

        assertThat(disagreements).as("The parser takes no character beyond U+FFFF in a name.").isEmpty();
    }

    private static String asText(final int codePoint)
    {
        return new String(Character.toChars(codePoint));
    }

    private static boolean parses(final SAXParser parser, final String xml)
    {
        parser.reset();
        try
        {
            parser.parse(new InputSource(new StringReader(xml)), new DefaultHandler());
            return true;
        }
        catch (final Exception e)
        {
            return false;
        }
    }
}
