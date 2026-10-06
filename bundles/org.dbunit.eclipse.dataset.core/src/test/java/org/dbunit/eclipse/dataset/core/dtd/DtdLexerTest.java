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
package org.dbunit.eclipse.dataset.core.dtd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link DtdLexer}: each operation is run on a small text, and the token it returns and the text left
 * unread are asserted together.
 */
class DtdLexerTest
{
    private record Scan(String token, String remainder)
    {
    }

    private static String remainderAfter(final String text, final Consumer<DtdLexer> operation)
    {
        final DtdLexer lexer = new DtdLexer(text);
        operation.accept(lexer);
        final int position = lexer.position();
        return text.substring(position);
    }

    private static Scan scan(final String text, final Function<DtdLexer, String> operation)
    {
        final DtdLexer lexer = new DtdLexer(text);
        final String token = operation.apply(lexer);
        final int position = lexer.position();
        final String remainder = text.substring(position);
        return new Scan(token, remainder);
    }

    @Test
    void testAtEnd_whenTheTextIsEmpty_isTrue()
    {
        final DtdLexer lexer = new DtdLexer("");

        assertThat(lexer.atEnd()).as("An empty text has nothing left to read.").isTrue();
    }

    @Test
    void testAtEnd_whenCharactersRemain_isFalse()
    {
        final DtdLexer lexer = new DtdLexer("a");

        assertThat(lexer.atEnd()).as("A character remains to be read.").isFalse();
    }

    @Test
    void testAdvance_whenCalled_movesThePositionForward()
    {
        final DtdLexer lexer = new DtdLexer("abcdef");

        lexer.advance(2);

        assertThat(lexer.position()).as("The position must move over the given number of characters.")
                .isEqualTo(2);
        assertThat(lexer.atCharacter('c')).as("The next character must be the one after those skipped.")
                .isTrue();
    }

    @Test
    void testAtCharacter_whenTheCharacterIsNext_isTrue()
    {
        final DtdLexer lexer = new DtdLexer("(a)");

        assertThat(lexer.atCharacter('(')).as("The opening parenthesis is next.").isTrue();
    }

    @Test
    void testAtCharacter_whenAnotherCharacterIsNext_isFalse()
    {
        final DtdLexer lexer = new DtdLexer("(a)");

        assertThat(lexer.atCharacter('a')).as("A different character is next.").isFalse();
    }

    @Test
    void testAtCharacter_whenAtTheEnd_isFalse()
    {
        final DtdLexer lexer = new DtdLexer("");

        assertThat(lexer.atCharacter('a')).as("No character is next at the end of the text.").isFalse();
    }

    @Test
    void testAtDeclarationEnd_whenAGreaterThanIsNext_isTrue()
    {
        final DtdLexer lexer = new DtdLexer("> tail");

        assertThat(lexer.atDeclarationEnd()).as("The closing angle bracket ends a declaration.").isTrue();
    }

    @Test
    void testAtDeclarationEnd_whenAtTheEnd_isTrue()
    {
        final DtdLexer lexer = new DtdLexer("");

        assertThat(lexer.atDeclarationEnd()).as("The end of the text ends a declaration.").isTrue();
    }

    @Test
    void testAtDeclarationEnd_whenAnotherCharacterIsNext_isFalse()
    {
        final DtdLexer lexer = new DtdLexer("a>");

        assertThat(lexer.atDeclarationEnd()).as("An ordinary character does not end a declaration.")
                .isFalse();
    }

    @Test
    void testAtText_whenTheLiteralStartsAtThePosition_isTrue()
    {
        final DtdLexer lexer = new DtdLexer("xx<!-- c -->");
        lexer.advance(2);

        assertThat(lexer.atText("<!--")).as("The literal starts at the position, not at the text's start.")
                .isTrue();
    }

    @Test
    void testAtText_whenTheLiteralRunsPastTheEnd_isFalse()
    {
        final DtdLexer lexer = new DtdLexer("<!-");

        assertThat(lexer.atText("<!--")).as("A literal longer than the remaining text cannot match.")
                .isFalse();
    }

    @Test
    void testAtText_whenTheLiteralDiffers_isFalse()
    {
        final DtdLexer lexer = new DtdLexer("<!ELEMENT");

        assertThat(lexer.atText("<!ENTITY")).as("A literal that differs in a later character is no match.")
                .isFalse();
    }

    @Test
    void testAtText_whenTheLiteralIsTheWholeRemainingText_isTrue()
    {
        final DtdLexer lexer = new DtdLexer("]]>");

        assertThat(lexer.atText("]]>")).as("A literal that ends exactly at the end of the text matches.")
                .isTrue();
    }

    @Test
    void testSkipWhitespace_whenWhitespaceLeads_stopsAtTheFirstOtherCharacter()
    {
        final String remainder = remainderAfter(" \t\r\n x y", DtdLexer::skipWhitespace);

        assertThat(remainder).as("Every leading whitespace character must be skipped, and no other.")
                .isEqualTo("x y");
    }

    @Test
    void testSkipWhitespace_whenAnIdeographicSpaceLeads_stopsAtIt()
    {
        final String remainder = remainderAfter(" \u3000x", DtdLexer::skipWhitespace);

        assertThat(remainder).as("The ideographic space is not XML whitespace, so skipping stops at it.")
                .isEqualTo("\u3000x");
    }

    @Test
    void testSkipWhitespace_whenOnlyWhitespaceRemains_stopsAtTheEnd()
    {
        final String remainder = remainderAfter("  \n", DtdLexer::skipWhitespace);

        assertThat(remainder).as("Skipping must stop at the end of the text.").isEmpty();
    }

    @Test
    void testSkipCharacter_whenTheExpectedCharacterIsNext_movesOverIt()
    {
        final String remainder = remainderAfter(">x", lexer -> lexer.skipCharacter('>'));

        assertThat(remainder).as("The expected character must be skipped.").isEqualTo("x");
    }

    @Test
    void testSkipCharacter_whenAnotherCharacterIsNext_staysPut()
    {
        final String remainder = remainderAfter("x>", lexer -> lexer.skipCharacter('>'));

        assertThat(remainder).as("A different character must not be skipped.").isEqualTo("x>");
    }

    @Test
    void testSkipComment_whenClosed_stopsAfterTheClosingMarker()
    {
        final String remainder = remainderAfter("<!-- a > b --> tail", DtdLexer::skipComment);

        assertThat(remainder).as("The whole comment, including a greater-than sign, must be skipped.")
                .isEqualTo(" tail");
    }

    @Test
    void testSkipComment_whenUnclosed_stopsAtTheEnd()
    {
        final String remainder = remainderAfter("<!-- open", DtdLexer::skipComment);

        assertThat(remainder).as("An unclosed comment must run to the end of the text.").isEmpty();
    }

    @Test
    void testSkipProcessingInstruction_whenClosed_stopsAfterTheClosingMarker()
    {
        final String remainder =
                remainderAfter("<?xml version=\"1.0\"?> tail", DtdLexer::skipProcessingInstruction);

        assertThat(remainder).as("The whole processing instruction must be skipped.").isEqualTo(" tail");
    }

    @Test
    void testSkipProcessingInstruction_whenUnclosed_stopsAtTheEnd()
    {
        final String remainder = remainderAfter("<?target data", DtdLexer::skipProcessingInstruction);

        assertThat(remainder).as("An unclosed processing instruction must run to the end of the text.")
                .isEmpty();
    }

    @Test
    void testSkipConditionalSection_whenClosed_stopsAfterTheClosingMarker()
    {
        final String remainder = remainderAfter("<![IGNORE[ <!ELEMENT a EMPTY> ]]> tail",
                DtdLexer::skipConditionalSection);

        assertThat(remainder).as("The whole conditional section must be skipped.").isEqualTo(" tail");
    }

    @Test
    void testSkipConditionalSection_whenUnclosed_stopsAtTheEnd()
    {
        final String remainder = remainderAfter("<![INCLUDE[ <!ELEMENT a EMPTY>",
                DtdLexer::skipConditionalSection);

        assertThat(remainder).as("An unclosed conditional section must run to the end of the text.")
                .isEmpty();
    }

    @Test
    void testSkipParameterEntityReference_whenASemicolonEndsIt_consumesTheSemicolon()
    {
        final String remainder = remainderAfter("%common; tail", DtdLexer::skipParameterEntityReference);

        assertThat(remainder).as("The percent sign, the name, and the semicolon must be skipped.")
                .isEqualTo(" tail");
    }

    @Test
    void testSkipParameterEntityReference_whenNoSemicolonFollows_stopsAfterTheName()
    {
        final String remainder = remainderAfter("%common tail", DtdLexer::skipParameterEntityReference);

        assertThat(remainder).as("Without a semicolon, only the percent sign and the name are skipped.")
                .isEqualTo(" tail");
    }

    @Test
    void testSkipToMatchingCloseAngleBracket_whenAQuotedLiteralHoldsAGreaterThan_skipsPastIt()
    {
        final String remainder = remainderAfter("<!ENTITY % e \"a > b\"> tail",
                DtdLexer::skipToMatchingCloseAngleBracket);

        assertThat(remainder).as("A greater-than sign inside a quoted literal must not end the declaration.")
                .isEqualTo(" tail");
    }

    @Test
    void testSkipToMatchingCloseAngleBracket_whenASingleQuotedLiteralHoldsAGreaterThan_skipsPastIt()
    {
        final String remainder = remainderAfter("<!ENTITY e 'a > b'> tail",
                DtdLexer::skipToMatchingCloseAngleBracket);

        assertThat(remainder).as("A single-quoted literal must hide a greater-than sign too.")
                .isEqualTo(" tail");
    }

    @Test
    void testSkipToMatchingCloseAngleBracket_whenUnclosed_stopsAtTheEnd()
    {
        final String remainder =
                remainderAfter("<!NOTATION n SYSTEM", DtdLexer::skipToMatchingCloseAngleBracket);

        assertThat(remainder).as("An unclosed declaration must run to the end of the text.").isEmpty();
    }

    @Test
    void testSkipToMatchingCloseAngleBracket_whenALiteralIsUnclosed_readsToTheEnd()
    {
        final DtdLexer lexer = new DtdLexer("<!ENTITY e 'abc>");

        lexer.skipToMatchingCloseAngleBracket();

        assertThat(lexer.atEnd()).as("An unclosed literal must swallow the rest of the text.").isTrue();
    }

    @Test
    void testSkipParenthesizedList_whenClosed_stopsAfterTheClosingParenthesis()
    {
        final String remainder = remainderAfter("(a|b) tail", DtdLexer::skipParenthesizedList);

        assertThat(remainder).as("The list up to and including its closing parenthesis must be skipped.")
                .isEqualTo(" tail");
    }

    @Test
    void testSkipParenthesizedList_whenUnclosed_stopsAtTheEnd()
    {
        final String remainder = remainderAfter("(a|b", DtdLexer::skipParenthesizedList);

        assertThat(remainder).as("An unclosed list must run to the end of the text.").isEmpty();
    }

    @Test
    void testScanQuotedLiteral_whenDoubleQuoted_stopsAfterTheClosingQuote()
    {
        final String remainder = remainderAfter("\"x y\" tail", DtdLexer::scanQuotedLiteral);

        assertThat(remainder).as("The literal, both quotes included, must be consumed.")
                .isEqualTo(" tail");
    }

    @Test
    void testScanQuotedLiteral_whenSingleQuoted_stopsAfterTheClosingQuote()
    {
        final String remainder = remainderAfter("'x \" y' tail", DtdLexer::scanQuotedLiteral);

        assertThat(remainder).as("A double quote inside a single-quoted literal must not end it.")
                .isEqualTo(" tail");
    }

    @Test
    void testScanQuotedLiteral_whenUnclosed_stopsAtTheEnd()
    {
        final String remainder = remainderAfter("\"open", DtdLexer::scanQuotedLiteral);

        assertThat(remainder).as("An unclosed literal must run to the end of the text.").isEmpty();
    }

    @Test
    void testScanQuotedLiteralIfPresent_whenAQuoteIsNext_consumesTheLiteral()
    {
        final String remainder = remainderAfter("\"x\" tail", DtdLexer::scanQuotedLiteralIfPresent);

        assertThat(remainder).as("A literal that starts at the position must be consumed.")
                .isEqualTo(" tail");
    }

    @Test
    void testScanQuotedLiteralIfPresent_whenNoQuoteIsNext_staysPut()
    {
        final String remainder = remainderAfter("#IMPLIED", DtdLexer::scanQuotedLiteralIfPresent);

        assertThat(remainder).as("Without a quote, nothing must be consumed.").isEqualTo("#IMPLIED");
    }

    @Test
    void testScanQuotedLiteralIfPresent_whenAtTheEnd_staysPut()
    {
        final String remainder = remainderAfter("", DtdLexer::scanQuotedLiteralIfPresent);

        assertThat(remainder).as("At the end of the text, nothing is left to consume.").isEmpty();
    }

    @Test
    void testScanParenthesizedContentSpec_whenGroupsAreNested_returnsTheBalancedGroupWithItsQuantifier()
    {
        final Scan scan = scan("(A, (B|C)*)* tail", DtdLexer::scanParenthesizedContentSpec);

        assertThat(scan).as("The outer group and its quantifier must be returned, the rest left unread.")
                .isEqualTo(new Scan("(A, (B|C)*)*", " tail"));
    }

    @Test
    void testScanParenthesizedContentSpec_whenNoQuantifierFollows_returnsTheGroupOnly()
    {
        final Scan scan = scan("(A|B) tail", DtdLexer::scanParenthesizedContentSpec);

        assertThat(scan).as("Only the group must be returned when no quantifier follows.")
                .isEqualTo(new Scan("(A|B)", " tail"));
    }

    @Test
    void testScanParenthesizedContentSpec_whenUnclosed_returnsTheRestOfTheText()
    {
        final Scan scan = scan("(A, (B", DtdLexer::scanParenthesizedContentSpec);

        assertThat(scan).as("An unbalanced group must run to the end of the text.")
                .isEqualTo(new Scan("(A, (B", ""));
    }

    @Test
    void testScanName_whenTheNameIsFollowedByWhitespace_stopsAtTheWhitespace()
    {
        final Scan scan = scan("USERS ID", DtdLexer::scanName);

        assertThat(scan).as("The name must be returned and the whitespace left unread.")
                .isEqualTo(new Scan("USERS", " ID"));
    }

    @Test
    void testScanName_whenTheNameHasPunctuation_keepsTheNameCharacters()
    {
        final Scan scan = scan("a-b.c:d>", DtdLexer::scanName);

        assertThat(scan).as("Hyphens, dots, and colons are name characters.")
                .isEqualTo(new Scan("a-b.c:d", ">"));
    }

    @Test
    void testScanName_whenTheFirstCharacterCannotStartAName_returnsAnEmptyNameAndStaysPut()
    {
        final Scan scan = scan("1abc", DtdLexer::scanName);

        assertThat(scan).as("A digit cannot start a name, so nothing is consumed.")
                .isEqualTo(new Scan("", "1abc"));
    }

    @Test
    void testScanName_whenASupplementaryCharacterFollowsTheName_stopsBeforeIt()
    {
        final String supplementary = new String(Character.toChars(0x10000));
        final Scan scan = scan("a" + supplementary + "b c", DtdLexer::scanName);

        assertThat(scan).as("The parser takes no character beyond U+FFFF in a name, so the name ends before "
                + "it, whole, not in the middle of its surrogate pair.")
                .isEqualTo(new Scan("a", supplementary + "b c"));
    }

    @Test
    void testScanBareWord_whenFollowedByWhitespace_stopsAtTheWhitespace()
    {
        final Scan scan = scan("#REQUIRED next", DtdLexer::scanBareWord);

        assertThat(scan).as("The word must end at the first whitespace character.")
                .isEqualTo(new Scan("#REQUIRED", " next"));
    }

    @Test
    void testScanBareWord_whenFollowedByAnIdeographicSpace_doesNotStopAtIt()
    {
        final Scan scan = scan("CDATA\u3000next>", DtdLexer::scanBareWord);

        assertThat(scan).as("The ideographic space is not XML whitespace, so it does not end the word.")
                .isEqualTo(new Scan("CDATA\u3000next", ">"));
    }

    @Test
    void testScanBareWord_whenFollowedByAGreaterThan_stopsAtTheGreaterThan()
    {
        final Scan scan = scan("CDATA>", DtdLexer::scanBareWord);

        assertThat(scan).as("The word must end at the declaration's closing bracket.")
                .isEqualTo(new Scan("CDATA", ">"));
    }
}
