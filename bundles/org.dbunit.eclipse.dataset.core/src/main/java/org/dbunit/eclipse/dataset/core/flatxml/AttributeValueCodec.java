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
import java.util.Map;

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
    private static final Map<Integer, String> ESCAPES = Map.of((int) '&', "&amp;", (int) '<', "&lt;",
            (int) '>', "&gt;", (int) '"', "&quot;", (int) '\'', "&apos;", (int) '\t', "&#09;", (int) '\n',
            "&#xA;", (int) '\r', "&#xD;");

    private static final Map<String, Character> PREDEFINED_ENTITIES =
            Map.of("lt", '<', "gt", '>', "amp", '&', "quot", '"', "apos", '\'');

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
     * Returns the message that says that a code point is not allowed in an XML 1.0 document, which every
     * place that refuses such a character gives, so that they word it alike.
     *
     * @param codePoint The code point that is not an XML 1.0 {@code Char}.
     * @return The message, which names the code point as {@code U+} and at least four hexadecimal digits.
     */
    public static String notXmlCharacterMessage(final int codePoint)
    {
        final String hexadecimal = String.format(Locale.ROOT, "%04X", codePoint);
        return NLS.bind(Messages.Codec_notXmlCharacter, hexadecimal);
    }

    /**
     * Tells whether a name is one of the five entities that XML predefines, which need no declaration.
     *
     * @param name The entity name, without the ampersand and the semicolon.
     * @return True for {@code lt}, {@code gt}, {@code amp}, {@code quot}, and {@code apos}.
     */
    static boolean isPredefinedEntity(final String name)
    {
        return PREDEFINED_ENTITIES.containsKey(name);
    }

    /**
     * Returns the index after the tab, line feed, or carriage return at an index, which is one further for a
     * carriage return followed by a line feed, because that pair is one line end.
     */
    private static int indexAfterWhitespace(final CharSequence raw, final int index)
    {
        final boolean isCrLf =
                raw.charAt(index) == '\r' && index + 1 < raw.length() && raw.charAt(index + 1) == '\n';
        return isCrLf ? index + 2 : index + 1;
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
            validateLiteralRun(raw, 0, raw.length());
            return raw.toString();
        }
        final StringBuilder result = new StringBuilder(raw.length());
        validateLiteralRun(raw, 0, plainLength);
        result.append(raw, 0, plainLength);
        int index = plainLength;
        while (index < raw.length())
        {
            final char current = raw.charAt(index);
            if (current == '&')
            {
                index = decodeReference(raw, index, result);
            }
            else if (current == '\t' || current == '\n' || current == '\r')
            {
                result.append(' ');
                index = indexAfterWhitespace(raw, index);
            }
            else
            {
                final int codePoint = Character.codePointAt(raw, index);
                validateLiteralCodePoint(codePoint, index);
                result.appendCodePoint(codePoint);
                index += Character.charCount(codePoint);
            }
        }
        return result.toString();
    }

    /**
     * Throws when a code point anywhere in raw's [start, end) range is not an XML 1.0 {@code Char}.
     */
    private static void validateLiteralRun(final CharSequence raw, final int start, final int end)
            throws AttributeValueException
    {
        int index = start;
        while (index < end)
        {
            final int codePoint = Character.codePointAt(raw, index);
            validateLiteralCodePoint(codePoint, index);
            index += Character.charCount(codePoint);
        }
    }

    private static void validateLiteralCodePoint(final int codePoint, final int offset)
            throws AttributeValueException
    {
        if (!isXmlChar(codePoint))
        {
            throw new AttributeValueException(notXmlCharacterMessage(codePoint), offset, false);
        }
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
            throw new DatasetEditException(notXmlCharacterMessage(codePoint));
        }
        final String escape = ESCAPES.get(codePoint);
        if (escape == null)
        {
            appendLiteralOrCharacterReference(result, codePoint, encoder);
        }
        else
        {
            result.append(escape);
        }
    }

    private static void appendLiteralOrCharacterReference(final StringBuilder result, final int codePoint,
            final CharsetEncoder encoder)
    {
        if (isEncodable(codePoint, encoder))
        {
            result.appendCodePoint(codePoint);
        }
        else
        {
            result.append("&#x").append(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT))
                    .append(';');
        }
    }

    /**
     * Tells whether the encoder can encode a code point. A character of the basic plane is asked about as a
     * char, which builds nothing, because this is asked for every character that is written.
     */
    private static boolean isEncodable(final int codePoint, final CharsetEncoder encoder)
    {
        if (encoder == null)
        {
            return true;
        }
        if (Character.isBmpCodePoint(codePoint))
        {
            return encoder.canEncode((char) codePoint);
        }
        return encoder.canEncode(new String(Character.toChars(codePoint)));
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
        final Character predefined = PREDEFINED_ENTITIES.get(body);
        if (predefined == null)
        {
            decodeCharacterOrThrow(body, ampersandOffset, result);
        }
        else
        {
            result.append(predefined.charValue());
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

    /**
     * Parses the digits of a character reference, which must name an XML 1.0 {@code Char}.
     *
     * @param digits The digits between {@code &#} or {@code &#x} and the semicolon.
     * @param radix The radix of the digits, 10 or 16.
     * @param referenceOffset The offset to report for a problem, which is that of the reference's
     *                        ampersand.
     * @return The code point that the reference names.
     * @throws AttributeValueException When the digits are missing or invalid, or name no XML character.
     */
    static int parseCharacterReference(final String digits, final int radix, final int referenceOffset)
            throws AttributeValueException
    {
        if (digits.isEmpty())
        {
            throw new AttributeValueException(Messages.Codec_referenceWithoutDigits, referenceOffset, false);
        }
        final long value = accumulateDigits(digits, radix, referenceOffset);
        if (!isXmlChar((int) value))
        {
            final String hexadecimal = Long.toHexString(value).toUpperCase(Locale.ROOT);
            throw new AttributeValueException(NLS.bind(Messages.Codec_referenceNotXmlCharacter, hexadecimal),
                    referenceOffset, false);
        }
        return (int) value;
    }

    private static long accumulateDigits(final String digits, final int radix, final int referenceOffset)
            throws AttributeValueException
    {
        long value = 0;
        for (int index = 0; index < digits.length(); index++)
        {
            final int digit = asciiDigit(digits.charAt(index), radix);
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
        return value;
    }

    /**
     * Returns the value of an ASCII digit, or -1 for any other character. Character.digit alone also
     * accepts the digits of other scripts and fullwidth letters, which XML does not allow in a character
     * reference.
     */
    private static int asciiDigit(final char character, final int radix)
    {
        return character < 0x80 ? Character.digit(character, radix) : -1;
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
