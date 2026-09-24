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

/**
 * One attribute of a flat XML element, with the offsets needed to rewrite it in place.
 *
 * @param name The attribute name, exactly as spelled in the document.
 * @param value The decoded, normalized value.
 * @param segmentOffset The start of the whitespace that precedes the name; for the first attribute this
 *                       equals the element's nameEndOffset, so the segments of all of an element's
 *                       attributes together cover exactly text[nameEndOffset, attributesEndOffset).
 * @param nameOffset The offset of the first character of the name.
 * @param valueOffset The offset of the first character after the opening quote.
 * @param valueEndOffset The offset of the closing quote.
 * @param quote The quote character used, either a double or a single quote.
 */
record FlatXmlAttribute(String name, String value, int segmentOffset, int nameOffset, int valueOffset,
        int valueEndOffset, char quote)
{
    /**
     * Returns the offset just after this attribute, including its closing quote.
     *
     * @return {@code valueEndOffset() + 1}.
     */
    int endOffset()
    {
        return valueEndOffset + 1;
    }
}
