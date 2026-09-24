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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the edit engine needs from a parse that the dataset model itself does not keep: the root,
 * the DOCTYPE, the text that was parsed, and, per table key, the display name, the row elements, and the
 * marker elements. The edit engine takes all offsets from this index.
 */
final class FlatXmlIndex
{
    private final FlatXmlRoot root;

    private final FlatXmlDoctype doctype;

    private final String text;

    private final List<FlatXmlElement> elements;

    private final Map<String, String> displayNames;

    private final Map<String, List<FlatXmlElement>> rowElements;

    private final Map<String, List<FlatXmlElement>> markerElements;

    FlatXmlIndex(final FlatXmlRoot root, final FlatXmlDoctype doctype, final String text,
            final List<FlatXmlElement> elements, final Map<String, String> displayNames,
            final Map<String, List<FlatXmlElement>> rowElements,
            final Map<String, List<FlatXmlElement>> markerElements)
    {
        this.root = root;
        this.doctype = doctype;
        this.text = text;
        this.elements = List.copyOf(elements);
        this.displayNames = new LinkedHashMap<>(displayNames);
        this.rowElements = copyOfElementLists(rowElements);
        this.markerElements = copyOfElementLists(markerElements);
    }

    FlatXmlRoot getRoot()
    {
        return root;
    }

    FlatXmlDoctype getDoctype()
    {
        return doctype;
    }

    String getText()
    {
        return text;
    }

    List<FlatXmlElement> getElements()
    {
        return elements;
    }

    Set<String> getTableKeys()
    {
        return displayNames.keySet();
    }

    String getDisplayName(final String tableKey)
    {
        return displayNames.get(tableKey);
    }

    List<FlatXmlElement> getRowElements(final String tableKey)
    {
        return rowElements.getOrDefault(tableKey, List.of());
    }

    List<FlatXmlElement> getMarkerElements(final String tableKey)
    {
        return markerElements.getOrDefault(tableKey, List.of());
    }

    private static Map<String, List<FlatXmlElement>> copyOfElementLists(
            final Map<String, List<FlatXmlElement>> source)
    {
        final Map<String, List<FlatXmlElement>> copy = new LinkedHashMap<>();
        for (final Map.Entry<String, List<FlatXmlElement>> entry : source.entrySet())
        {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return copy;
    }
}
