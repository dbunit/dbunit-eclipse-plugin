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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * @param columnKeys The table's columns by key, used for ordering new attributes and for their display
     *                   names; an edit builds it once for all of its rows.
     * @param changes Column key (upper-cased) to new value; null means NULL (the attribute is removed).
     *                A column key absent from this map is unchanged. When the element has two or more
     *                attributes for a key that differ only in letter case, every one of them matches the
     *                key and gets the same change, so a caller that sets a value must rule that out first;
     *                removing a column removes all of them.
     * @param renames Column key (upper-cased) to new column name. A column key absent from this map
     *                keeps its spelling. A caller must rule out a key that the element has two or more
     *                attributes for, which would all get the new name.
     * @param encoder The encoder used to decide which characters need a numeric character reference.
     * @return The replacement text for {@code text[element.nameEndOffset(), element.attributesEndOffset())},
     *         or null when the result equals the original text.
     */
    static String rewrite(final String text, final FlatXmlElement element, final ColumnKeys columnKeys,
            final Map<String, String> changes, final Map<String, String> renames,
            final CharsetEncoder encoder)
    {
        final Set<String> existingKeys = existingKeys(element);
        final List<String> additions = pendingAdditions(changes, existingKeys, columnKeys);

        final StringBuilder result = new StringBuilder();
        int nextAddition = 0;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            final String key = attribute.key();
            final int attributeColumnIndex = columnKeys.indexOf(key);
            while (nextAddition < additions.size()
                    && columnKeys.indexOf(additions.get(nextAddition)) < attributeColumnIndex)
            {
                appendAddition(result, additions.get(nextAddition), changes, columnKeys, encoder);
                nextAddition++;
            }
            appendAttribute(result, text, attribute, key, changes, renames, encoder);
        }
        while (nextAddition < additions.size())
        {
            appendAddition(result, additions.get(nextAddition), changes, columnKeys, encoder);
            nextAddition++;
        }

        final String rewritten = result.toString();
        final String original = text.substring(element.nameEndOffset(), element.attributesEndOffset());
        return rewritten.equals(original) ? null : rewritten;
    }

    private static Set<String> existingKeys(final FlatXmlElement element)
    {
        final Set<String> keys = new HashSet<>();
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            keys.add(attribute.key());
        }
        return keys;
    }

    private static List<String> pendingAdditions(final Map<String, String> changes,
            final Set<String> existingKeys, final ColumnKeys columnKeys)
    {
        final List<String> additions = new ArrayList<>();
        for (final Map.Entry<String, String> entry : changes.entrySet())
        {
            if (!existingKeys.contains(entry.getKey()) && entry.getValue() != null)
            {
                additions.add(entry.getKey());
            }
        }
        additions.sort(Comparator.comparingInt(columnKeys::indexOf));
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
        appendValue(result, text, attribute, changes.get(key), encoder);
        result.append(attribute.quote());
    }

    /**
     * Appends the text of an attribute's value: the new value, escaped, or the text as it was when there is
     * no new value, or when the new value is the one that the attribute holds, which the text may spell with
     * character references, so that the edit shows only what changed.
     */
    private static void appendValue(final StringBuilder result, final String text,
            final FlatXmlAttribute attribute, final String newValue, final CharsetEncoder encoder)
    {
        if (newValue == null || newValue.equals(attribute.value()))
        {
            result.append(text, attribute.valueOffset(), attribute.valueEndOffset());
        }
        else
        {
            result.append(AttributeValueCodec.escape(newValue, encoder));
        }
    }

    private static void appendAddition(final StringBuilder result, final String key,
            final Map<String, String> changes, final ColumnKeys columnKeys, final CharsetEncoder encoder)
    {
        result.append(' ').append(columnKeys.nameOf(key)).append("=\"")
                .append(AttributeValueCodec.escape(changes.get(key), encoder)).append('"');
    }
}
