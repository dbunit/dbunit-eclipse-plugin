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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.junit.jupiter.api.Test;

/**
 * Tests decoding and escaping of dbUnit flat XML attribute values: predefined entities, character
 * references, whitespace normalization, and escaping on write.
 */
class AttributeValueCodecTest
{
    @Test
    void testDecode_whenGivenPlainText_returnsItUnchanged() throws AttributeValueException
    {
        assertThat(AttributeValueCodec.decode("Alice Smith, 42 > 7"))
                .as("Text without references or literal whitespace other than spaces must decode to itself.")
                .isEqualTo("Alice Smith, 42 > 7");
    }

    @Test
    void testDecode_whenPlainTextPrecedesTextToDecode_keepsThePlainTextAndDecodesTheRest()
            throws AttributeValueException
    {
        assertThat(AttributeValueCodec.decode("Tom &amp; Jerry\tsay &lt;hi&gt;"))
                .as("The plain text before the first reference must be kept, and the rest decoded.")
                .isEqualTo("Tom & Jerry say <hi>");
    }

    @Test
    void testDecode_whenGivenEachPredefinedEntity_returnsItsCharacter() throws AttributeValueException
    {
        assertThat(AttributeValueCodec.decode("&lt;&gt;&amp;&quot;&apos;"))
                .as("The five predefined entities must decode to their characters.").isEqualTo("<>&\"'");
    }

    @Test
    void testDecode_whenGivenDecimalAndHexadecimalReferences_returnsTheirCharacter()
            throws AttributeValueException
    {
        assertThat(AttributeValueCodec.decode("&#65;"))
                .as("A decimal reference must decode by code point.").isEqualTo("A");
        assertThat(AttributeValueCodec.decode("&#x41;"))
                .as("A hexadecimal reference must decode by code point.").isEqualTo("A");
    }

    @Test
    void testDecode_whenGivenLiteralTabOrLineFeed_returnsASpace() throws AttributeValueException
    {
        assertThat(AttributeValueCodec.decode("\t")).as("A literal tab must normalize to a space.")
                .isEqualTo(" ");
        assertThat(AttributeValueCodec.decode("\n")).as("A literal line feed must normalize to a space.")
                .isEqualTo(" ");
    }

    @Test
    void testDecode_whenGivenACarriageReturnLineFeedPair_returnsOneSpace() throws AttributeValueException
    {
        assertThat(AttributeValueCodec.decode("\r\n"))
                .as("A CR LF pair must normalize to exactly one space.").isEqualTo(" ");
    }

    @Test
    void testDecode_whenGivenALoneCarriageReturn_returnsASpace() throws AttributeValueException
    {
        assertThat(AttributeValueCodec.decode("\r"))
                .as("A lone carriage return must normalize to a space.").isEqualTo(" ");
    }

    @Test
    void testDecode_whenGivenALineFeedCharacterReference_returnsTheLineFeedItself()
            throws AttributeValueException
    {
        assertThat(AttributeValueCodec.decode("&#10;"))
                .as("A character reference is not literal whitespace, so it must not be normalized.")
                .isEqualTo("\n");
    }

    @Test
    void testDecode_whenGivenAnUnknownEntity_throwsFlaggedAsUnsupportedAtItsOffset()
    {
        final AttributeValueException exception = catchThrowableOfType(
                () -> AttributeValueCodec.decode("ab&foo;cd"), AttributeValueException.class);

        assertThat(exception).as("An unknown entity must throw AttributeValueException.").isNotNull();
        assertThat(exception.getOffset()).as("The offset must point at the '&'.").isEqualTo(2);
        assertThat(exception.isUnsupportedEntity())
                .as("An unknown named entity is unsupported, not malformed.").isTrue();
        assertThat(exception.getMessage()).as("The message must quote the entity reference.")
                .isEqualTo("Entity reference '&foo;' is not supported; only the predefined entities and "
                        + "character references are available without a DTD.");
    }

    @Test
    void testDecode_whenGivenABareAmpersand_throwsNotFlaggedAsUnsupportedAtItsOffset()
    {
        final AttributeValueException exception = catchThrowableOfType(
                () -> AttributeValueCodec.decode("ab&cd"), AttributeValueException.class);

        assertThat(exception).as("A '&' without a ';' must throw AttributeValueException.").isNotNull();
        assertThat(exception.getOffset()).as("The offset must point at the '&'.").isEqualTo(2);
        assertThat(exception.isUnsupportedEntity())
                .as("A malformed reference is not an unsupported entity.").isFalse();
    }

    @Test
    void testDecode_whenGivenAReferenceToANonXmlCharacter_throwsAtItsOffset()
    {
        final AttributeValueException exception = catchThrowableOfType(
                () -> AttributeValueCodec.decode("a&#1;b"), AttributeValueException.class);

        assertThat(exception).as("U+0001 is not an XML 1.0 Char, so the reference must throw.")
                .isNotNull();
        assertThat(exception.getOffset()).as("The offset must point at the '&'.").isEqualTo(1);
    }

    @Test
    void testEscape_whenGivenEachSpecialCharacter_matchesS11Exactly()
    {
        assertThat(AttributeValueCodec.escape("&", null)).as("'&' must escape to &amp;.")
                .isEqualTo("&amp;");
        assertThat(AttributeValueCodec.escape("<", null)).as("'<' must escape to &lt;.")
                .isEqualTo("&lt;");
        assertThat(AttributeValueCodec.escape(">", null)).as("'>' must escape to &gt;.")
                .isEqualTo("&gt;");
        assertThat(AttributeValueCodec.escape("\"", null)).as("'\"' must escape to &quot;.")
                .isEqualTo("&quot;");
        assertThat(AttributeValueCodec.escape("'", null)).as("''' must escape to &apos;.")
                .isEqualTo("&apos;");
        assertThat(AttributeValueCodec.escape("\t", null)).as("A tab must escape to &#09;.")
                .isEqualTo("&#09;");
        assertThat(AttributeValueCodec.escape("\n", null)).as("A line feed must escape to &#xA;.")
                .isEqualTo("&#xA;");
        assertThat(AttributeValueCodec.escape("\r", null)).as("A carriage return must escape to &#xD;.")
                .isEqualTo("&#xD;");
    }

    @Test
    void testEscape_whenEncoderCannotEncodeTheCharacter_usesAHexadecimalReference()
    {
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();

        assertThat(AttributeValueCodec.escape("€", encoder))
                .as("'€' is not encodable in ISO-8859-1, so it must become &#x20AC;.")
                .isEqualTo("&#x20AC;");
    }

    @Test
    void testEscape_whenEncoderCanEncodeTheCharacter_staysLiteral()
    {
        final CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();

        assertThat(AttributeValueCodec.escape("€", encoder))
                .as("'€' is encodable in UTF-8, so it must stay literal.").isEqualTo("€");
    }

    @Test
    void testEscape_whenGivenASupplementaryCharacterTheEncoderCannotEncode_usesOneHexadecimalReference()
    {
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        final String emoji = new String(Character.toChars(0x1F600));

        assertThat(AttributeValueCodec.escape(emoji, encoder))
                .as("An emoji must escape to exactly one character reference.").isEqualTo("&#x1F600;");
    }

    @Test
    void testEscape_whenValueContainsANonXmlCharacter_throwsDatasetEditException()
    {
        final DatasetEditException exception = catchThrowableOfType(
                () -> AttributeValueCodec.escape("\u0001", null), DatasetEditException.class);

        assertThat(exception).as("U+0001 is not an XML 1.0 Char, so escaping it must be rejected.")
                .isNotNull();
    }

    @Test
    void testDecodeOfEscape_whenGivenManyRandomStrings_roundTripsExactly() throws AttributeValueException
    {
        final int[] pool = buildRoundTripCodePointPool();
        final CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        final Random random = new Random(42);
        for (int i = 0; i < 1000; i++)
        {
            final String value = randomString(pool, random);
            final String escaped = AttributeValueCodec.escape(value, encoder);
            final String decoded = AttributeValueCodec.decode(escaped);
            assertThat(decoded).as("Round-tripping '" + value + "' must return the original value.")
                    .isEqualTo(value);
        }
    }

    private static int[] buildRoundTripCodePointPool()
    {
        final List<Integer> pool = new ArrayList<>();
        for (char letter = 'a'; letter <= 'z'; letter++)
        {
            pool.add((int) letter);
        }
        for (char letter = 'A'; letter <= 'Z'; letter++)
        {
            pool.add((int) letter);
        }
        for (char digit = '0'; digit <= '9'; digit++)
        {
            pool.add((int) digit);
        }
        pool.addAll(List.of((int) '<', (int) '>', (int) '&', (int) '"', (int) '\'', (int) ' ',
                (int) '\t', (int) '\n', (int) '\r'));
        pool.addAll(List.of(0x1F600, 0x10000, 0x20AC));
        final int[] result = new int[pool.size()];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = pool.get(i);
        }
        return result;
    }

    private static String randomString(final int[] pool, final Random random)
    {
        final int length = random.nextInt(20) + 1;
        final StringBuilder value = new StringBuilder();
        for (int i = 0; i < length; i++)
        {
            value.appendCodePoint(pool[random.nextInt(pool.length)]);
        }
        return value.toString();
    }
}
