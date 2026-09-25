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
package org.dbunit.eclipse.dataset.ui.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link XmlTagScanner}.
 */
class XmlTagScannerTest
{
    private static final String TAG = "tag";

    private static final String ATTRIBUTE_NAME = "attributeName";

    private static final String ATTRIBUTE_VALUE = "attributeValue";

    @Test
    void testNextToken_forAStartTag_separatesAttributeNamesAndQuotedValuesFromTheRestOfTheTag()
    {
        final List<Run> runs = scan("<USERS ID=\"1\" NAME = 'a b' NOTE=\"x=y>z\"/>");

        assertThat(runs)
                .as("Attribute names and quoted values must get their own tokens; the rest is the tag token.")
                .isEqualTo(List.of(
                        new Run(TAG, "<USERS "),
                        new Run(ATTRIBUTE_NAME, "ID"),
                        new Run(TAG, "="),
                        new Run(ATTRIBUTE_VALUE, "\"1\""),
                        new Run(TAG, " "),
                        new Run(ATTRIBUTE_NAME, "NAME"),
                        new Run(TAG, " = "),
                        new Run(ATTRIBUTE_VALUE, "'a b'"),
                        new Run(TAG, " "),
                        new Run(ATTRIBUTE_NAME, "NOTE"),
                        new Run(TAG, "="),
                        new Run(ATTRIBUTE_VALUE, "\"x=y>z\""),
                        new Run(TAG, "/>")));
    }

    @Test
    void testNextToken_forAnEndTag_returnsOnlyTheTagToken()
    {
        final List<Run> runs = scan("</dataset>");

        assertThat(runs).as("An end tag's element name must not become an attribute name.")
                .isEqualTo(List.of(new Run(TAG, "</dataset>")));
    }

    /**
     * Scans a tag and joins adjacent tokens with the same data into runs, as the presentation reconciler
     * colors them.
     */
    private static List<Run> scan(final String tag)
    {
        final IDocument document = new Document(tag);
        final XmlTagScanner scanner =
                new XmlTagScanner(new Token(TAG), new Token(ATTRIBUTE_NAME), new Token(ATTRIBUTE_VALUE));
        scanner.setRange(document, 0, document.getLength());
        final List<Run> runs = new ArrayList<>();
        IToken token = scanner.nextToken();
        while (!token.isEOF())
        {
            final String data = (String) token.getData();
            final int offset = scanner.getTokenOffset();
            final String text = tag.substring(offset, offset + scanner.getTokenLength());
            final int lastIndex = runs.size() - 1;
            if (lastIndex >= 0 && runs.get(lastIndex).data().equals(data))
            {
                runs.set(lastIndex, new Run(data, runs.get(lastIndex).text() + text));
            }
            else
            {
                runs.add(new Run(data, text));
            }
            token = scanner.nextToken();
        }
        return runs;
    }

    private record Run(String data, String text)
    {
    }
}
