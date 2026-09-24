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

import java.util.List;

/**
 * One element parsed from the flat XML body: a table row when it has attributes, or a marker when it
 * does not.
 *
 * @param name The element name, exactly as spelled in the document.
 * @param offset The offset of the start tag's opening angle bracket.
 * @param nameEndOffset The offset just after the element name.
 * @param attributes The element's attributes, in document order.
 * @param attributesEndOffset The offset just after the last attribute, or equal to nameEndOffset when
 *                             there are no attributes.
 * @param startTagEndOffset The offset just after the start tag's closing {@code >} or {@code />}.
 * @param selfClosing True when the element was written as a self-closing tag.
 * @param endTagOffset The offset of the end tag's opening angle bracket, or -1 when selfClosing is true.
 * @param endOffset The offset just after the whole element.
 */
record FlatXmlElement(String name, int offset, int nameEndOffset, List<FlatXmlAttribute> attributes,
        int attributesEndOffset, int startTagEndOffset, boolean selfClosing, int endTagOffset,
        int endOffset)
{
}
