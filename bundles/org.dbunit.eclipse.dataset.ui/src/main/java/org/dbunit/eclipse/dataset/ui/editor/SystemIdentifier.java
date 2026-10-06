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
package org.dbunit.eclipse.dataset.ui.editor;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * The system identifier of a DOCTYPE read as the XML parser of dbUnit reads it as a URI reference: where the
 * file system separates names with a backslash, a backslash is a slash, and a character that a URI cannot
 * hold, such as a space, is escaped with the percent sign, while an escape sequence that is there already
 * stays as it is. A path such as {@code test data/dataset.dtd} or {@code dtd\dataset.dtd} is no URI as it is
 * written, but the parser loads the file that it names.
 *
 * @since 1.0.0
 */
final class SystemIdentifier
{
    private static final String HEXADECIMAL_DIGITS = "0123456789ABCDEF";

    private static final String PUNCTUATION = "-_.!~*'()" + ";/?:@&=+$,#";

    private final String text;

    private final boolean backslashIsSeparator;

    /**
     * Creates the identifier.
     *
     * @param text The system identifier as the DOCTYPE writes it.
     * @param backslashIsSeparator True when the file system separates names with a backslash, so that a
     *                             backslash in the identifier is a slash.
     */
    SystemIdentifier(final String text, final boolean backslashIsSeparator)
    {
        this.text = text;
        this.backslashIsSeparator = backslashIsSeparator;
    }

    /**
     * Returns the URI reference that the identifier stands for.
     *
     * @return The URI reference, with each character that a URI cannot hold escaped.
     * @throws URISyntaxException When the escaped text is still no URI reference.
     */
    URI toUri() throws URISyntaxException
    {
        return new URI(escaped());
    }

    /**
     * Returns the identifier with each character that a URI cannot hold escaped, as UTF-8 bytes.
     *
     * @return The escaped text.
     */
    String escaped()
    {
        final String slashed = backslashIsSeparator ? text.replace('\\', '/') : text;
        final StringBuilder result = new StringBuilder(slashed.length());
        int index = 0;
        while (index < slashed.length())
        {
            final int codePoint = slashed.codePointAt(index);
            if (isKept(slashed, index, codePoint))
            {
                result.appendCodePoint(codePoint);
            }
            else
            {
                appendEscaped(result, codePoint);
            }
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static boolean isKept(final String value, final int index, final int codePoint)
    {
        if (codePoint == '%')
        {
            return startsEscapeSequence(value, index);
        }
        if (codePoint > 127)
        {
            return !Character.isISOControl(codePoint) && !Character.isSpaceChar(codePoint);
        }
        return Character.isLetterOrDigit(codePoint) || PUNCTUATION.indexOf(codePoint) >= 0;
    }

    private static boolean startsEscapeSequence(final String value, final int index)
    {
        return index + 2 < value.length() && isHexadecimalDigit(value.charAt(index + 1))
                && isHexadecimalDigit(value.charAt(index + 2));
    }

    private static boolean isHexadecimalDigit(final char ch)
    {
        return Character.digit(ch, 16) >= 0 && ch < 128;
    }

    private static void appendEscaped(final StringBuilder result, final int codePoint)
    {
        final byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
        for (final byte value : bytes)
        {
            result.append('%').append(HEXADECIMAL_DIGITS.charAt((value >> 4) & 0xF))
                    .append(HEXADECIMAL_DIGITS.charAt(value & 0xF));
        }
    }
}
