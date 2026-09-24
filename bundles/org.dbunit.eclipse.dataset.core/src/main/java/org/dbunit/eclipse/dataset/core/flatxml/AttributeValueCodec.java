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
import java.util.Locale;

import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;

/**
 * Decodes and escapes dbUnit flat XML attribute values.
 *
 * @since 1.0.0
 */
public final class AttributeValueCodec
{
    private AttributeValueCodec()
    {
    }

    /**
     * Returns whether a code point is an XML 1.0 {@code Char}.
     *
     * @param codePoint The Unicode code point to test.
     * @return True when the code point may appear in an XML 1.0 document.
     */
    public static boolean isXmlChar(final int codePoint)
    {
        return codePoint == 0x9
                || codePoint == 0xA
                || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }

    /**
     * Decodes a raw attribute value: the five predefined entities and character references become their
     * characters, and a literal tab, line feed, or carriage return normalizes to a space.
     *
     * @param raw The raw text between the attribute's quotes, exactly as written in the document.
     * @return The decoded value.
     * @throws AttributeValueException When an entity or character reference is invalid.
     */
    public static String decode(final CharSequence raw) throws AttributeValueException
    {
        final StringBuilder result = new StringBuilder(raw.length());
        int index = 0;
        while (index < raw.length())
        {
            final char current = raw.charAt(index);
            if (current == '&')
            {
                index = decodeReference(raw, index, result);
            }
            else if (current == '\t' || current == '\n')
            {
                result.append(' ');
                index++;
            }
            else if (current == '\r')
            {
                final boolean isCrLf = index + 1 < raw.length() && raw.charAt(index + 1) == '\n';
                result.append(' ');
                index = isCrLf ? index + 2 : index + 1;
            }
            else
            {
                result.append(current);
                index++;
            }
        }
        return result.toString();
    }

    /**
     * Escapes a value for writing as a double- or single-quoted XML attribute value.
     *
     * @param value The value to escape.
     * @param encoder The encoder of the document's charset, or null when every character is encodable.
     * @return The escaped text to write between the attribute's quotes.
     * @throws DatasetEditException When the value contains a code point that is not an XML 1.0
     *                               {@code Char}.
     */
    public static String escape(final String value, final CharsetEncoder encoder)
    {
        final StringBuilder result = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length())
        {
            final int codePoint = value.codePointAt(index);
            appendEscaped(result, codePoint, encoder);
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static void appendEscaped(final StringBuilder result, final int codePoint,
            final CharsetEncoder encoder)
    {
        if (!isXmlChar(codePoint))
        {
            throw new DatasetEditException(
                    "The character U+" + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT)
                            + " is not allowed in an XML 1.0 document.");
        }
        switch (codePoint)
        {
            case '&' -> result.append("&amp;");
            case '<' -> result.append("&lt;");
            case '>' -> result.append("&gt;");
            case '"' -> result.append("&quot;");
            case '\'' -> result.append("&apos;");
            case '\t' -> result.append("&#09;");
            case '\n' -> result.append("&#xA;");
            case '\r' -> result.append("&#xD;");
            default -> appendLiteralOrCharacterReference(result, codePoint, encoder);
        }
    }

    private static void appendLiteralOrCharacterReference(final StringBuilder result, final int codePoint,
            final CharsetEncoder encoder)
    {
        final boolean encodable =
                encoder == null || encoder.canEncode(new String(Character.toChars(codePoint)));
        if (encodable)
        {
            result.appendCodePoint(codePoint);
        }
        else
        {
            result.append("&#x").append(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT))
                    .append(';');
        }
    }

    private static int decodeReference(final CharSequence raw, final int ampersandOffset,
            final StringBuilder result) throws AttributeValueException
    {
        final int semicolon = indexOf(raw, ';', ampersandOffset + 1);
        if (semicolon < 0)
        {
            throw new AttributeValueException("'&' must start a valid entity or character reference.",
                    ampersandOffset, false);
        }
        final String body = raw.subSequence(ampersandOffset + 1, semicolon).toString();
        switch (body)
        {
            case "lt" -> result.append('<');
            case "gt" -> result.append('>');
            case "amp" -> result.append('&');
            case "quot" -> result.append('"');
            case "apos" -> result.append('\'');
            default -> decodeCharacterOrThrow(body, ampersandOffset, result);
        }
        return semicolon + 1;
    }

    private static void decodeCharacterOrThrow(final String body, final int ampersandOffset,
            final StringBuilder result) throws AttributeValueException
    {
        if (body.startsWith("#x"))
        {
            result.appendCodePoint(parseCharacterReference(body.substring(2), 16, ampersandOffset));
        }
        else if (body.startsWith("#"))
        {
            result.appendCodePoint(parseCharacterReference(body.substring(1), 10, ampersandOffset));
        }
        else
        {
            throw new AttributeValueException(
                    "Entity reference '&" + body + ";' is not supported; only the predefined entities "
                            + "and character references are available without a DTD.",
                    ampersandOffset, true);
        }
    }

    private static int parseCharacterReference(final String digits, final int radix,
            final int referenceOffset) throws AttributeValueException
    {
        if (digits.isEmpty())
        {
            throw new AttributeValueException("A character reference must have at least one digit.",
                    referenceOffset, false);
        }
        long value = 0;
        for (int index = 0; index < digits.length(); index++)
        {
            final int digit = Character.digit(digits.charAt(index), radix);
            if (digit < 0)
            {
                throw new AttributeValueException(
                        "'" + digits + "' is not a valid character reference.", referenceOffset, false);
            }
            value = value * radix + digit;
            if (value > Character.MAX_CODE_POINT)
            {
                throw new AttributeValueException("A character reference must not exceed U+10FFFF.",
                        referenceOffset, false);
            }
        }
        if (!isXmlChar((int) value))
        {
            throw new AttributeValueException(
                    "Character reference U+" + Long.toHexString(value).toUpperCase(Locale.ROOT)
                            + " is not allowed in XML 1.0.",
                    referenceOffset, false);
        }
        return (int) value;
    }

    private static int indexOf(final CharSequence text, final char target, final int fromIndex)
    {
        for (int index = fromIndex; index < text.length(); index++)
        {
            if (text.charAt(index) == target)
            {
                return index;
            }
        }
        return -1;
    }
}
