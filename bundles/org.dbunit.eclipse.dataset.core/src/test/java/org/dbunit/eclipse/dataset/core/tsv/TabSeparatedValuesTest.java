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
package org.dbunit.eclipse.dataset.core.tsv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link TabSeparatedValues} against the Clipboard specification's format and parse rules.
 */
class TabSeparatedValuesTest
{
    @Test
    void testFormat_withASimpleGrid_joinsFieldsWithTabsAndRowsWithTheLineSeparator()
    {
        final List<List<String>> rows =
                List.of(Arrays.asList("1", "Alice"), Arrays.asList("2", "Bob"));

        final String text = TabSeparatedValues.format(rows, "\n");

        assertThat(text).as("Fields must be tab-separated and every row must end with the separator.")
                .isEqualTo("1\tAlice\n2\tBob\n");
    }

    @Test
    void testFormat_whenAValueIsNull_writesAnUnquotedEmptyField()
    {
        final List<List<String>> rows = List.of(Arrays.asList("1", null));

        final String text = TabSeparatedValues.format(rows, "\n");

        assertThat(text).as("A null value must format as an unquoted empty field.").isEqualTo("1\t\n");
    }

    @Test
    void testFormat_whenAValueIsTheEmptyString_writesAQuotedEmptyField()
    {
        final List<List<String>> rows = List.of(Arrays.asList("1", ""));

        final String text = TabSeparatedValues.format(rows, "\n");

        assertThat(text).as("An empty string must format as a quoted empty field, distinct from NULL.")
                .isEqualTo("1\t\"\"\n");
    }

    @Test
    void testFormat_whenAValueContainsATabOrLineBreakOrQuote_quotesItAndDoublesEmbeddedQuotes()
    {
        final List<List<String>> rows =
                List.of(Arrays.asList("a\tb", "c\nd", "e\rf", "say \"hi\"", "\"leading"));

        final String text = TabSeparatedValues.format(rows, "\n");

        assertThat(text).as("Special characters must be quoted, with embedded quotes doubled.")
                .isEqualTo("\"a\tb\"\t\"c\nd\"\t\"e\rf\"\t\"say \"\"hi\"\"\"\t\"\"\"leading\"\n");
    }

    @Test
    void testFormat_whenAValueHasNoSpecialCharacters_leavesItUnquoted()
    {
        final List<List<String>> rows = List.of(List.of("plain"));

        final String text = TabSeparatedValues.format(rows, "\n");

        assertThat(text).as("A value with no special characters must not be quoted.")
                .isEqualTo("plain\n");
    }

    @Test
    void testParse_ofEmptyText_returnsAnEmptyList()
    {
        assertThat(TabSeparatedValues.parse("")).as("Empty text must give an empty list.").isEmpty();
    }

    @Test
    void testParse_withATrailingLineEnd_doesNotCreateAnExtraEmptyRow()
    {
        final List<List<String>> rows = TabSeparatedValues.parse("1\tAlice\n2\tBob\n");

        assertThat(rows).as("A trailing line end must not create an extra empty row.")
                .containsExactly(List.of("1", "Alice"), List.of("2", "Bob"));
    }

    @Test
    void testParse_withNoTrailingLineEnd_stillReadsTheLastRow()
    {
        final List<List<String>> rows = TabSeparatedValues.parse("1\tAlice\n2\tBob");

        assertThat(rows).as("The last row must be read even with no trailing line end.")
                .containsExactly(List.of("1", "Alice"), List.of("2", "Bob"));
    }

    @Test
    void testParse_acceptsCrLfLfAndCrLineEnds()
    {
        final List<List<String>> rows = TabSeparatedValues.parse("a\r\nb\nc\rd");

        assertThat(rows).as("CR LF, LF, and CR must each end a row.").containsExactly(List.of("a"),
                List.of("b"), List.of("c"), List.of("d"));
    }

    @Test
    void testParse_withRaggedRows_padsShorterRowsWithNullToTheWidestRow()
    {
        final List<List<String>> rows = TabSeparatedValues.parse("1\t2\t3\na\tb\n");

        assertThat(rows).as("Shorter rows must be padded with NULL to the widest row's width.")
                .containsExactly(List.of("1", "2", "3"), Arrays.asList("a", "b", null));
    }

    @Test
    void testParse_withAnEmptyLine_readsItAsOneNullField()
    {
        final List<List<String>> rows = TabSeparatedValues.parse("a\n\nb\n");

        assertThat(rows).as("A blank line between rows must parse as one row with one NULL field.")
                .containsExactly(List.of("a"), Arrays.asList((String) null), List.of("b"));
    }

    @Test
    void testParse_ofAQuotedEmptyField_readsItAsTheEmptyString()
    {
        final List<List<String>> rows = TabSeparatedValues.parse("\"\"\n");

        assertThat(rows).as("A quoted empty field must parse as the empty string, not NULL.")
                .containsExactly(List.of(""));
    }

    @Test
    void testParse_withAQuotedFieldContainingTabsAndLineBreaksAndDoubledQuotes_unescapesIt()
    {
        final List<List<String>> rows =
                TabSeparatedValues.parse("\"a\tb\nc\"\t\"say \"\"hi\"\"\"\n");

        assertThat(rows).as("A quoted field must keep its embedded tabs and line breaks, and unescape "
                + "doubled quotes.").containsExactly(List.of("a\tb\nc", "say \"hi\""));
    }

    @Test
    void testFormatThenParse_ofAnExcelStyleSample_roundTrips()
    {
        final List<List<String>> original = List.of(Arrays.asList("1", "Alice \"the boss\"", "multi\nline"),
                Arrays.asList("2", null, "plain"));

        final List<List<String>> roundTripped =
                TabSeparatedValues.parse(TabSeparatedValues.format(original, "\r\n"));

        assertThat(roundTripped).as("Formatting then parsing must reproduce the original values, with "
                + "null read back as null.").containsExactly(
                        List.of("1", "Alice \"the boss\"", "multi\nline"), Arrays.asList("2", null, "plain"));
    }

    @Test
    void testFormatThenParse_ofNullAndTheEmptyString_keepsThemDistinct()
    {
        final List<List<String>> original = List.of(Arrays.asList((String) null, ""));

        final List<List<String>> roundTripped =
                TabSeparatedValues.parse(TabSeparatedValues.format(original, "\n"));

        assertThat(roundTripped).as("NULL and the empty string must round-trip as distinct values.")
                .containsExactly(Arrays.asList((String) null, ""));
    }
}
