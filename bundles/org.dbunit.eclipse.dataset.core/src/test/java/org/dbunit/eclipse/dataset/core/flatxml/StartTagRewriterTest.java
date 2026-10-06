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
import java.util.LinkedHashMap;
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
    void testRewrite_whenAChangeSetsTheValueThatTheAttributeHas_returnsNullAndLeavesItsRawText()
    {
        final String text = "<dataset><USERS NAME=\"Caf&#xE9; &amp; A&#65;\" ID='1'/></dataset>";
        final FlatXmlElement element = FlatXmlParser.parse(text).elements().get(0);
        final List<DatasetColumn> columns = List.of(new DatasetColumn("NAME", false, true, false),
                new DatasetColumn("ID", false, true, false));
        final Map<String, String> changes = Map.of("NAME", "Caf\u00e9 & AA", "ID", "1");
        final ColumnKeys columnKeys = ColumnKeys.of(columns);

        final String rewritten = StartTagRewriter.rewrite(text, element, columnKeys, changes, Map.of(),
                StandardCharsets.UTF_8.newEncoder());

        assertThat(rewritten).as("A change to the value that the attribute holds already must write nothing.")
                .isNull();
    }

    @Test
    void testRewrite_whenOneChangeKeepsTheValueAndAnotherChangesIt_rewritesOnlyTheChangedAttribute()
    {
        final String text = "<dataset><USERS NAME=\"Caf&#xE9;\" CODE='a&#x62;'/></dataset>";
        final FlatXmlElement element = FlatXmlParser.parse(text).elements().get(0);
        final List<DatasetColumn> columns = List.of(new DatasetColumn("NAME", false, true, false),
                new DatasetColumn("CODE", false, true, false));
        final Map<String, String> changes = Map.of("NAME", "Caf\u00e9", "CODE", "z");
        final ColumnKeys columnKeys = ColumnKeys.of(columns);

        final String rewritten = StartTagRewriter.rewrite(text, element, columnKeys, changes, Map.of(),
                StandardCharsets.UTF_8.newEncoder());

        assertThat(rewritten).as("The attribute that keeps its value keeps its raw text, and the other one "
                + "gets the new value in its own quotes.")
                .isEqualTo(" NAME=\"Caf&#xE9;\" CODE='z'");
    }

    @Test
    void testRewrite_whenAChangeKeepsTheValueOfARenamedAttribute_keepsItsRawValueUnderTheNewName()
    {
        final String text = "<dataset><USERS NAME=\"Caf&#xE9;\"/></dataset>";
        final FlatXmlElement element = FlatXmlParser.parse(text).elements().get(0);
        final List<DatasetColumn> columns = List.of(new DatasetColumn("NAME", false, true, false));

        final String rewritten = StartTagRewriter.rewrite(text, element, ColumnKeys.of(columns),
                Map.of("NAME", "Caf\u00e9"), Map.of("NAME", "LABEL"), StandardCharsets.UTF_8.newEncoder());

        assertThat(rewritten).as("A rename with an unchanged value must not touch the value text.")
                .isEqualTo(" LABEL=\"Caf&#xE9;\"");
    }

    @Test
    void testRewrite_whenSeveralAttributesAreAdded_writesThemInTheOrderOfTheColumns()
    {
        final String text = "<dataset><USERS ID=\"1\"/></dataset>";
        final FlatXmlElement element = FlatXmlParser.parse(text).elements().get(0);
        final List<DatasetColumn> columns = List.of(new DatasetColumn("ID", false, true, false),
                new DatasetColumn("NAME", false, true, false), new DatasetColumn("CITY", false, true, false));
        final ColumnKeys columnKeys = ColumnKeys.of(columns);
        final Map<String, String> changes = new LinkedHashMap<>();
        changes.put("CITY", "Springfield");
        changes.put("NAME", "Alice");

        final String rewritten = StartTagRewriter.rewrite(text, element, columnKeys, changes, Map.of(),
                StandardCharsets.UTF_8.newEncoder());

        assertThat(rewritten).as("The added attributes must follow the order of the columns, not the order "
                + "of the changes.").isEqualTo(" ID=\"1\" NAME=\"Alice\" CITY=\"Springfield\"");
    }

    @Test
    void testRewrite_whenAnAddedAttributesColumnIsNotInColumns_sortsItLastInsteadOfThrowing()
    {
        final String text = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final FlatXmlElement element = FlatXmlParser.parse(text).elements().get(0);
        final List<DatasetColumn> columns = List.of(new DatasetColumn("ID", false, true, false),
                new DatasetColumn("NAME", false, true, false));
        final Map<String, String> changes = Map.of("CITY", "Springfield");
        final ColumnKeys columnKeys = ColumnKeys.of(columns);

        final String rewritten = StartTagRewriter.rewrite(text, element, columnKeys, changes, Map.of(),
                StandardCharsets.UTF_8.newEncoder());

        assertThat(rewritten)
                .as("An added attribute whose column is absent from columnIndexByKey must sort after "
                        + "every known column instead of throwing a NullPointerException.")
                .isEqualTo(" ID=\"1\" NAME=\"Alice\" CITY=\"Springfield\"");
    }
}
