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

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlIndex}: the first element of a table, which is a row or a marker, whichever comes
 * first in the document.
 */
class FlatXmlIndexTest
{
    private static FlatXmlIndex indexOf(final String text)
    {
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        return FlatXmlModelBuilder.build(text, parse, null, FlatXmlOptions.DBUNIT_DEFAULTS, Map.of()).index();
    }

    @Test
    void testGetFirstElement_whenAMarkerComesBeforeTheRows_returnsTheMarker()
    {
        final String text = "<dataset><USERS/><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>";
        final FlatXmlIndex index = indexOf(text);

        final FlatXmlElement first = index.getFirstElement("USERS");

        assertThat(first.offset()).as("The marker is the first element of the table.")
                .isEqualTo(text.indexOf("<USERS/>"));
        assertThat(first).as("It must be the one that the ordered list starts with.")
                .isEqualTo(index.getAllElementsInOrder("USERS").get(0));
    }

    @Test
    void testGetFirstElement_whenARowComesBeforeTheMarkers_returnsTheRow()
    {
        final String text = "<dataset><USERS ID=\"1\"/><USERS/><USERS ID=\"2\"/></dataset>";
        final FlatXmlIndex index = indexOf(text);

        final FlatXmlElement first = index.getFirstElement("USERS");

        assertThat(first.offset()).as("The first row is the first element of the table.")
                .isEqualTo(text.indexOf("<USERS ID=\"1\""));
        assertThat(first).as("It must be the one that the ordered list starts with.")
                .isEqualTo(index.getAllElementsInOrder("USERS").get(0));
    }

    @Test
    void testGetFirstElement_whenTheTableHasOnlyRows_returnsTheFirstRow()
    {
        final String text = "<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>";

        final FlatXmlElement first = indexOf(text).getFirstElement("USERS");

        assertThat(first.offset()).as("The first row is the first element.")
                .isEqualTo(text.indexOf("<USERS ID=\"1\""));
    }

    @Test
    void testGetFirstElement_whenTheTableHasOnlyMarkers_returnsTheFirstMarker()
    {
        final String text = "<dataset><USERS/><USERS/></dataset>";

        final FlatXmlElement first = indexOf(text).getFirstElement("USERS");

        assertThat(first.offset()).as("The first marker is the first element.").isEqualTo(9);
    }

    @Test
    void testGetFirstElement_forATableWithNoElements_isNull()
    {
        assertThat(indexOf("<dataset><USERS ID=\"1\"/></dataset>").getFirstElement("ORDERS"))
                .as("A table that the document lacks has no first element.").isNull();
    }
}
