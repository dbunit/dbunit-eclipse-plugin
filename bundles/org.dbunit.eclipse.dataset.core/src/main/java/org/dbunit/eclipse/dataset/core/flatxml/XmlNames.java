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
 * The XML 1.0 {@code Name} production, used for table and column names and by the flat XML parser.
 *
 * @since 1.0.0
 */
public final class XmlNames
{
    /**
     * The code point ranges of the XML 1.0 {@code NameStartChar} production, each as its first and last code
     * point. The ranges are in ascending order, so that a scan can stop at the first range that starts
     * above the code point.
     */
    private static final int[][] NAME_START_RANGES = { { ':', ':' }, { 'A', 'Z' }, { '_', '_' }, { 'a', 'z' },
            { 0xC0, 0xD6 }, { 0xD8, 0xF6 }, { 0xF8, 0x2FF }, { 0x370, 0x37D }, { 0x37F, 0x1FFF },
            { 0x200C, 0x200D }, { 0x2070, 0x218F }, { 0x2C00, 0x2FEF }, { 0x3001, 0xD7FF }, { 0xF900, 0xFDCF },
            { 0xFDF0, 0xFFFD }, { 0x10000, 0xEFFFF } };

    private XmlNames()
    {
    }

    /**
     * Returns whether a code point may start an XML 1.0 name.
     *
     * @param codePoint The Unicode code point to test.
     * @return True when the code point is a {@code NameStartChar}.
     */
    public static boolean isNameStartChar(final int codePoint)
    {
        boolean inRange = false;
        int index = 0;
        while (index < NAME_START_RANGES.length && codePoint >= NAME_START_RANGES[index][0] && !inRange)
        {
            inRange = codePoint <= NAME_START_RANGES[index][1];
            index++;
        }
        return inRange;
    }

    /**
     * Returns whether a code point may continue an XML 1.0 name after its first character.
     *
     * @param codePoint The Unicode code point to test.
     * @return True when the code point is a {@code NameChar}.
     */
    public static boolean isNameChar(final int codePoint)
    {
        return isNameStartChar(codePoint)
                || codePoint == '-'
                || codePoint == '.'
                || (codePoint >= '0' && codePoint <= '9')
                || codePoint == 0xB7
                || (codePoint >= 0x300 && codePoint <= 0x36F)
                || (codePoint >= 0x203F && codePoint <= 0x2040);
    }

    /**
     * Returns whether a string is a valid XML 1.0 name.
     *
     * @param name The string to test.
     * @return True when the string is non-empty, its first code point is a {@code NameStartChar}, and
     *         every later code point is a {@code NameChar}.
     */
    public static boolean isValidName(final String name)
    {
        if (name.isEmpty())
        {
            return false;
        }
        final int firstCodePoint = name.codePointAt(0);
        if (!isNameStartChar(firstCodePoint))
        {
            return false;
        }
        int index = Character.charCount(firstCodePoint);
        while (index < name.length())
        {
            final int codePoint = name.codePointAt(index);
            if (!isNameChar(codePoint))
            {
                return false;
            }
            index += Character.charCount(codePoint);
        }
        return true;
    }
}
