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
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Compares {@link XmlNames} with the XML parser of the Java runtime, which dbUnit loads a dataset with: for
 * every code point of the basic plane, and for a sample beyond it, the parser accepts the code point in a
 * name exactly when {@code XmlNames} does, at the start of a name and inside it.
 */
class XmlNamesParityTest
{
    private static final int LAST_BASIC_PLANE_CODE_POINT = 0xFFFF;

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
