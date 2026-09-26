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

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.eclipse.osgi.util.NLS;

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
        final int plainLength = plainPrefixLength(raw);
        if (plainLength == raw.length())
        {
            return raw.toString();
        }
        final StringBuilder result = new StringBuilder(raw.length());
        result.append(raw, 0, plainLength);
        int index = plainLength;
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
     * Returns the length of a raw value's leading characters that decode to themselves, which is the whole
     * raw value for most values.
     */
    private static int plainPrefixLength(final CharSequence raw)
    {
        int index = 0;
        while (index < raw.length() && !needsDecoding(raw.charAt(index)))
        {
            index++;
        }
        return index;
    }

    private static boolean needsDecoding(final char character)
    {
        return character == '&' || character == '\t' || character == '\n' || character == '\r';
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
            final String hexadecimal = Integer.toHexString(codePoint).toUpperCase(Locale.ROOT);
            throw new DatasetEditException(NLS.bind(Messages.Codec_notXmlCharacter, hexadecimal));
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
            throw new AttributeValueException(Messages.Codec_invalidAmpersand, ampersandOffset, false);
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
            throw new AttributeValueException(NLS.bind(Messages.Codec_unsupportedEntity, body),
                    ampersandOffset, true);
        }
    }

    private static int parseCharacterReference(final String digits, final int radix,
            final int referenceOffset) throws AttributeValueException
    {
        if (digits.isEmpty())
        {
            throw new AttributeValueException(Messages.Codec_referenceWithoutDigits, referenceOffset, false);
        }
        long value = 0;
        for (int index = 0; index < digits.length(); index++)
        {
            final int digit = Character.digit(digits.charAt(index), radix);
            if (digit < 0)
            {
                throw new AttributeValueException(NLS.bind(Messages.Codec_invalidReference, digits),
                        referenceOffset, false);
            }
            value = value * radix + digit;
            if (value > Character.MAX_CODE_POINT)
            {
                throw new AttributeValueException(Messages.Codec_referenceTooLarge, referenceOffset, false);
            }
        }
        if (!isXmlChar((int) value))
        {
            final String hexadecimal = Long.toHexString(value).toUpperCase(Locale.ROOT);
            throw new AttributeValueException(NLS.bind(Messages.Codec_referenceNotXmlCharacter, hexadecimal),
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
