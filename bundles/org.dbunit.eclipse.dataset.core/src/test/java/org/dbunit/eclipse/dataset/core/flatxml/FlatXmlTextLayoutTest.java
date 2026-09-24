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

import org.eclipse.jface.text.IRegion;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlTextLayout} against the indentation and line-extent rules of the Edit Engine
 * specification.
 */
class FlatXmlTextLayoutTest
{
    @Test
    void testIndentOf_whenElementIsIndentedWithSpaces_returnsTheSpaces()
    {
        final String text = "<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n";
        final FlatXmlElement element = onlyElement(text);
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, "\n");

        assertThat(layout.indentOf(element))
                .as("The indentation must be the four spaces before the element.").isEqualTo("    ");
    }

    @Test
    void testIndentOf_whenElementIsIndentedWithATab_returnsTheTab()
    {
        final String text = "<dataset>\n\t<USERS ID=\"1\"/>\n</dataset>\n";
        final FlatXmlElement element = onlyElement(text);
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, "\n");

        assertThat(layout.indentOf(element)).as("The indentation must be the tab before the element.")
                .isEqualTo("\t");
    }

    @Test
    void testIndentOf_whenElementIsInline_returnsEmpty()
    {
        final String text = "<dataset><USERS ID=\"1\"/></dataset>\n";
        final FlatXmlElement element = onlyElement(text);
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, "\n");

        assertThat(layout.indentOf(element))
                .as("Non-whitespace precedes the element on its line, so there is no indentation.")
                .isEqualTo("");
    }

    @Test
    void testChildIndentation_whenRootHasChildren_returnsTheFirstChildsIndentation()
    {
        final String text = "<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n";
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, "\n");

        final String indentation = layout.childIndentation(parse.root(), parse.elements());

        assertThat(indentation).as("Without an explicit new child, indentation must match the first one.")
                .isEqualTo("    ");
    }

    @Test
    void testChildIndentation_whenRootHasNoChildren_returnsTheRootsIndentationPlusTwoSpaces()
    {
        final String text = "  <dataset>\n</dataset>\n";
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, "\n");

        final String indentation = layout.childIndentation(parse.root(), parse.elements());

        assertThat(indentation).as("Without children, the indentation must be the root's indentation "
                + "plus two spaces.")
                .isEqualTo("    ");
    }

    @Test
    void testLineExtent_whenElementIsAloneOnItsLine_returnsTheWholeLineWithItsDelimiter()
    {
        final String text = "<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n";
        final FlatXmlElement element = onlyElement(text);
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, "\n");

        final IRegion region = layout.lineExtent(element);

        assertThat(substringOf(text, region)).as("The whole line, including its delimiter, must be removed.")
                .isEqualTo("    <USERS ID=\"1\"/>\n");
    }

    @Test
    void testLineExtent_whenElementSharesItsLineWithOtherContent_returnsTheElementAlone()
    {
        final String text = "<dataset><USERS ID=\"1\"/><ORDERS ID=\"2\"/></dataset>\n";
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final FlatXmlElement element = parse.elements().get(0);
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, "\n");

        final IRegion region = layout.lineExtent(element);

        assertThat(substringOf(text, region))
                .as("An inline element must be removed alone, keeping its neighbors.")
                .isEqualTo("<USERS ID=\"1\"/>");
    }

    @Test
    void testIndentOf_whenLineEndsAreCrLf_stillFindsTheLineStart()
    {
        final String text = "<dataset>\r\n    <USERS ID=\"1\"/>\r\n</dataset>\r\n";
        final FlatXmlElement element = onlyElement(text);
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, "\r\n");

        assertThat(layout.indentOf(element)).as("CR LF line ends must not affect indentation detection.")
                .isEqualTo("    ");
    }

    @Test
    void testLineExtent_whenLineEndsAreCrLf_includesTheWholeDelimiter()
    {
        final String text = "<dataset>\r\n    <USERS ID=\"1\"/>\r\n</dataset>\r\n";
        final FlatXmlElement element = onlyElement(text);
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, "\r\n");

        final IRegion region = layout.lineExtent(element);

        assertThat(substringOf(text, region))
                .as("The CR LF delimiter must be included whole, not split.")
                .isEqualTo("    <USERS ID=\"1\"/>\r\n");
    }

    private static String substringOf(final String text, final IRegion region)
    {
        return text.substring(region.getOffset(), region.getOffset() + region.getLength());
    }

    private static FlatXmlElement onlyElement(final String text)
    {
        return FlatXmlParser.parse(text).elements().get(0);
    }
}
