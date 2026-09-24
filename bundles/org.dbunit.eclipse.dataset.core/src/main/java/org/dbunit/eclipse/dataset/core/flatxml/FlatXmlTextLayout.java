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

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

/**
 * The layout facts the edit engine needs to keep new and moved text looking like the rest of the
 * document: line delimiters, indentation, and whether an element is alone on its line.
 */
final class FlatXmlTextLayout
{
    private final String text;

    private final String lineDelimiter;

    FlatXmlTextLayout(final String text, final String lineDelimiter)
    {
        this.text = text;
        this.lineDelimiter = lineDelimiter;
    }

    String getLineDelimiter()
    {
        return lineDelimiter;
    }

    /**
     * Returns the whitespace between the start of the element's line and the element.
     *
     * @return That whitespace, or the empty string when non-whitespace precedes the element on its line.
     */
    String indentOf(final FlatXmlElement element)
    {
        return leadingWhitespaceOf(element.offset());
    }

    /**
     * Returns the indentation new children of the root should use.
     *
     * @param root The root element.
     * @param children The root's current children, in document order.
     * @return {@code indentOf} the first child, or, without children, the root line's indentation plus
     *         two spaces.
     */
    String childIndentation(final FlatXmlRoot root, final List<FlatXmlElement> children)
    {
        if (!children.isEmpty())
        {
            return indentOf(children.get(0));
        }
        return leadingWhitespaceOf(root.offset()) + "  ";
    }

    /**
     * Returns the range to remove so that deleting an element leaves no blank line behind.
     *
     * @return The whole line, including its delimiter, when only whitespace precedes the element on its
     *         line and only whitespace follows it there; otherwise the element alone.
     */
    IRegion lineExtent(final FlatXmlElement element)
    {
        final int lineStart = lineStartOffset(element.offset());
        if (!isBlank(text, lineStart, element.offset()))
        {
            return elementRegion(element);
        }
        final int lineEnd = lineEndOffset(element.endOffset());
        if (!isBlank(text, element.endOffset(), lineEnd))
        {
            return elementRegion(element);
        }
        final int delimiterEnd = lineEnd + delimiterLengthAt(lineEnd);
        return new Region(lineStart, delimiterEnd - lineStart);
    }

    private static IRegion elementRegion(final FlatXmlElement element)
    {
        return new Region(element.offset(), element.endOffset() - element.offset());
    }

    private String leadingWhitespaceOf(final int offset)
    {
        final int lineStart = lineStartOffset(offset);
        return isBlank(text, lineStart, offset) ? text.substring(lineStart, offset) : "";
    }

    private int lineStartOffset(final int offset)
    {
        int index = offset;
        while (index > 0 && !isLineBreakChar(text.charAt(index - 1)))
        {
            index--;
        }
        return index;
    }

    private int lineEndOffset(final int offset)
    {
        int index = offset;
        while (index < text.length() && !isLineBreakChar(text.charAt(index)))
        {
            index++;
        }
        return index;
    }

    private int delimiterLengthAt(final int offset)
    {
        if (offset >= text.length())
        {
            return 0;
        }
        if (text.charAt(offset) == '\r')
        {
            return offset + 1 < text.length() && text.charAt(offset + 1) == '\n' ? 2 : 1;
        }
        return isLineBreakChar(text.charAt(offset)) ? 1 : 0;
    }

    private static boolean isLineBreakChar(final char ch)
    {
        return ch == '\n' || ch == '\r';
    }

    private static boolean isBlank(final String value, final int start, final int end)
    {
        for (int index = start; index < end; index++)
        {
            if (!Character.isWhitespace(value.charAt(index)))
            {
                return false;
            }
        }
        return true;
    }
}
