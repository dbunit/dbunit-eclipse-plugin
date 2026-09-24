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

import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;

/**
 * Rewrites one element's start tag: changed attributes and renames are applied segment by segment, so
 * every untouched attribute is copied byte for byte, preserving its quote style, spacing, and position.
 */
final class StartTagRewriter
{
    private StartTagRewriter()
    {
    }

    /**
     * Computes the new text of an element's attributes.
     *
     * @param text The document text the element was parsed from.
     * @param element The element to rewrite.
     * @param columns The table's columns, used for ordering new attributes and for their display names.
     * @param changes Column key (upper-cased) to new value; null means NULL (the attribute is removed).
     *                A column key absent from this map is unchanged.
     * @param renames Column key (upper-cased) to new column name. A column key absent from this map
     *                keeps its spelling.
     * @param encoder The encoder used to decide which characters need a numeric character reference.
     * @return The replacement text for {@code text[element.nameEndOffset(), element.attributesEndOffset())},
     *         or null when the result equals the original text.
     */
    static String rewrite(final String text, final FlatXmlElement element, final List<DatasetColumn> columns,
            final Map<String, String> changes, final Map<String, String> renames,
            final CharsetEncoder encoder)
    {
        final Map<String, Integer> columnIndexByKey = new HashMap<>();
        final Map<String, String> displayNameByKey = new HashMap<>();
        for (int index = 0; index < columns.size(); index++)
        {
            final String key = columns.get(index).name().toUpperCase(Locale.ENGLISH);
            columnIndexByKey.put(key, index);
            displayNameByKey.put(key, columns.get(index).name());
        }

        final Set<String> existingKeys = new HashSet<>();
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            existingKeys.add(attribute.name().toUpperCase(Locale.ENGLISH));
        }

        final List<String> additions = pendingAdditions(changes, existingKeys, columnIndexByKey);

        final StringBuilder result = new StringBuilder();
        int nextAddition = 0;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            final String key = attribute.name().toUpperCase(Locale.ENGLISH);
            final int attributeColumnIndex = columnIndexByKey.getOrDefault(key, Integer.MAX_VALUE);
            while (nextAddition < additions.size()
                    && columnIndexByKey.get(additions.get(nextAddition)) < attributeColumnIndex)
            {
                appendAddition(result, additions.get(nextAddition), changes, displayNameByKey, encoder);
                nextAddition++;
            }
            appendAttribute(result, text, attribute, key, changes, renames, encoder);
        }
        while (nextAddition < additions.size())
        {
            appendAddition(result, additions.get(nextAddition), changes, displayNameByKey, encoder);
            nextAddition++;
        }

        final String rewritten = result.toString();
        final String original = text.substring(element.nameEndOffset(), element.attributesEndOffset());
        return rewritten.equals(original) ? null : rewritten;
    }

    private static List<String> pendingAdditions(final Map<String, String> changes,
            final Set<String> existingKeys, final Map<String, Integer> columnIndexByKey)
    {
        final List<String> additions = new ArrayList<>();
        for (final Map.Entry<String, String> entry : changes.entrySet())
        {
            if (!existingKeys.contains(entry.getKey()) && entry.getValue() != null)
            {
                additions.add(entry.getKey());
            }
        }
        additions.sort(Comparator.comparingInt(key -> columnIndexByKey.getOrDefault(key, Integer.MAX_VALUE)));
        return additions;
    }

    private static void appendAttribute(final StringBuilder result, final String text,
            final FlatXmlAttribute attribute, final String key, final Map<String, String> changes,
            final Map<String, String> renames, final CharsetEncoder encoder)
    {
        final boolean hasChange = changes.containsKey(key);
        final String rename = renames.get(key);
        if (!hasChange && rename == null)
        {
            result.append(text, attribute.segmentOffset(), attribute.endOffset());
            return;
        }
        if (hasChange && changes.get(key) == null)
        {
            return;
        }
        result.append(text, attribute.segmentOffset(), attribute.nameOffset());
        result.append(rename != null ? rename : attribute.name());
        result.append(text, attribute.nameOffset() + attribute.name().length(), attribute.valueOffset());
        if (hasChange)
        {
            result.append(AttributeValueCodec.escape(changes.get(key), encoder));
        }
        else
        {
            result.append(text, attribute.valueOffset(), attribute.valueEndOffset());
        }
        result.append(attribute.quote());
    }

    private static void appendAddition(final StringBuilder result, final String key,
            final Map<String, String> changes, final Map<String, String> displayNameByKey,
            final CharsetEncoder encoder)
    {
        result.append(' ').append(displayNameByKey.getOrDefault(key, key)).append("=\"")
                .append(AttributeValueCodec.escape(changes.get(key), encoder)).append('"');
    }
}
