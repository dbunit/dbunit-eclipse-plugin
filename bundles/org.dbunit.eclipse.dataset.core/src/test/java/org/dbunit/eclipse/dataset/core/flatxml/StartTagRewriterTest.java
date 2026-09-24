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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link StartTagRewriter}.
 */
class StartTagRewriterTest
{
    @Test
    void testRewrite_whenAnAddedAttributesColumnIsNotInColumns_sortsItLastInsteadOfThrowing()
    {
        final String text = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final FlatXmlElement element = FlatXmlParser.parse(text).elements().get(0);
        final List<DatasetColumn> columns = List.of(new DatasetColumn("ID", false, true, false),
                new DatasetColumn("NAME", false, true, false));
        final Map<String, String> changes = Map.of("CITY", "Springfield");

        final String rewritten = StartTagRewriter.rewrite(text, element, columns, changes, Map.of(),
                StandardCharsets.UTF_8.newEncoder());

        assertThat(rewritten)
                .as("An added attribute whose column is absent from columnIndexByKey must sort after "
                        + "every known column instead of throwing a NullPointerException.")
                .isEqualTo(" ID=\"1\" NAME=\"Alice\" CITY=\"Springfield\"");
    }
}
