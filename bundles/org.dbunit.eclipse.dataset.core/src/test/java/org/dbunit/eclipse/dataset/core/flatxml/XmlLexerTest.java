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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.osgi.util.NLS;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link XmlLexer}: each operation is run on a small text, and the token it returns and the text left
 * unread are asserted together. A malformed text must record one blocking problem and stop the scan.
 */
class XmlLexerTest
{
    private static final String CONTROL_CHARACTER = String.valueOf((char) 0x1);

    private static final String LONE_SURROGATE = String.valueOf((char) 0xD800);

    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private record Scan(String token, String remainder)
    {
    }

    private static XmlLexer lexerFor(final String text)
    {
        return new XmlLexer(text, new ScanProblems(text));
    }

    private static String remainderAfter(final String text, final Consumer<XmlLexer> operation)
    {
        final XmlLexer lexer = lexerFor(text);
        operation.accept(lexer);
        final int position = lexer.position();
        return text.substring(position);
    }

    private static Scan scan(final String text, final Function<XmlLexer, String> operation)
    {
        final XmlLexer lexer = lexerFor(text);
        final String token = operation.apply(lexer);
        final int position = lexer.position();
        final String remainder = text.substring(position);
        return new Scan(token, remainder);
    }

    private static List<DatasetProblem> problemsAfterFailure(final String text, final int start,
            final Consumer<XmlLexer> operation)
    {
        final ScanProblems problems = new ScanProblems(text);
        final XmlLexer lexer = new XmlLexer(text, problems);
        lexer.advance(start);
        assertThatThrownBy(() -> operation.accept(lexer)).as("A malformed text must stop the scan.")
                .isInstanceOf(StopScanException.class);
        return problems.toList();
    }

    private static DatasetProblem problemInFirstLine(final String message, final int offset)
    {
        final int column = offset + 1;
        final String located = NLS.bind(Messages.Parser_position, new Object[] { message, 1, column });
        return new DatasetProblem(ProblemCode.NOT_WELL_FORMED, ProblemSeverity.ERROR, located, null, null, -1,
                offset, 0);
    }

    @Test
    void testPosition_whenNewlyCreated_isZero()
    {
        final XmlLexer lexer = lexerFor("<dataset/>");

        assertThat(lexer.position()).as("A new lexer starts at the first character.").isZero();
    }

    @Test
    void testAdvance_whenCalled_movesThePositionForward()
    {
        final XmlLexer lexer = lexerFor("abcdef");

        lexer.advance(2);

        assertThat(lexer.position()).as("The position must move over the given number of characters.")
                .isEqualTo(2);
        assertThat(lexer.currentCharacter()).as("The next character must be the one after those skipped.")
                .isEqualTo('c');
    }

    @Test
    void testAtEnd_whenTheTextIsEmpty_isTrue()
    {
        final XmlLexer lexer = lexerFor("");

        assertThat(lexer.atEnd()).as("An empty text has nothing left to read.").isTrue();
    }

    @Test
    void testAtEnd_whenCharactersRemain_isFalse()
    {
        final XmlLexer lexer = lexerFor("a");

        assertThat(lexer.atEnd()).as("A character remains to be read.").isFalse();
    }

    @Test
    void testAtCharacter_whenTheCharacterIsNext_isTrue()
    {
        final XmlLexer lexer = lexerFor("<a>");

        assertThat(lexer.atCharacter('<')).as("The opening angle bracket is next.").isTrue();
    }

    @Test
    void testAtCharacter_whenAnotherCharacterIsNext_isFalse()
    {
        final XmlLexer lexer = lexerFor("<a>");

        assertThat(lexer.atCharacter('a')).as("A different character is next.").isFalse();
    }

    @Test
    void testAtCharacter_whenAtTheEnd_isFalse()
    {
        final XmlLexer lexer = lexerFor("");

        assertThat(lexer.atCharacter('a')).as("No character is next at the end of the text.").isFalse();
    }

    @Test
    void testAtText_whenTheLiteralStartsAtThePosition_isTrue()
    {
        final XmlLexer lexer = lexerFor("xx<!-- c -->");
        lexer.advance(2);

        assertThat(lexer.atText("<!--")).as("The literal starts at the position, not at the text's start.")
                .isTrue();
    }

    @Test
    void testAtText_whenTheLiteralRunsPastTheEnd_isFalse()
    {
        final XmlLexer lexer = lexerFor("<!-");

        assertThat(lexer.atText("<!--")).as("A literal longer than the remaining text cannot match.")
                .isFalse();
    }

    @Test
    void testAtText_whenTheLiteralDiffers_isFalse()
    {
        final XmlLexer lexer = lexerFor("<![CDATA[");

        assertThat(lexer.atText("<![CDATX[")).as("A literal that differs in a later character is no match.")
                .isFalse();
    }

    @Test
    void testAtNameStart_whenALetterIsNext_isTrue()
    {
        final XmlLexer lexer = lexerFor("USERS");

        assertThat(lexer.atNameStart()).as("A letter can start a name.").isTrue();
    }

    @Test
    void testAtNameStart_whenADigitIsNext_isFalse()
    {
        final XmlLexer lexer = lexerFor("1USERS");

        assertThat(lexer.atNameStart()).as("A digit cannot start a name.").isFalse();
    }

    @Test
    void testAtNameStart_whenAtTheEnd_isFalse()
    {
        final XmlLexer lexer = lexerFor("");

        assertThat(lexer.atNameStart()).as("No name can start at the end of the text.").isFalse();
    }

    @Test
    void testAtNameStart_whenASupplementaryNameStartCharacterIsNext_isTrue()
    {
        final XmlLexer lexer = lexerFor(new String(Character.toChars(0x10000)) + "a");

        assertThat(lexer.atNameStart()).as("A supplementary name start character must be read whole.")
                .isTrue();
    }

    @Test
    void testIsElementStart_whenALessThanAndANameStartCharacterFollow_isTrue()
    {
        final XmlLexer lexer = lexerFor("<USERS/>");

        assertThat(lexer.isElementStart()).as("A less-than sign and a letter start an element.").isTrue();
    }

    @Test
    void testIsElementStart_whenALessThanAndASlashFollow_isFalse()
    {
        final XmlLexer lexer = lexerFor("</USERS>");

        assertThat(lexer.isElementStart()).as("An end tag does not start an element.").isFalse();
    }

    @Test
    void testIsElementStart_whenTheLessThanIsTheLastCharacter_isFalse()
    {
        final XmlLexer lexer = lexerFor("<");

        assertThat(lexer.isElementStart()).as("A lone less-than sign does not start an element.").isFalse();
    }

    @Test
    void testIsElementStart_whenTheCharacterIsNotALessThan_isFalse()
    {
        final XmlLexer lexer = lexerFor("USERS");

        assertThat(lexer.isElementStart()).as("Text does not start an element.").isFalse();
    }

    @Test
    void testIsWhitespace_whenTheCharacterIsSpaceTabLineFeedOrCarriageReturn_isTrue()
    {
        assertThat(List.of(XmlLexer.isWhitespace(' '), XmlLexer.isWhitespace('\t'),
                XmlLexer.isWhitespace('\n'), XmlLexer.isWhitespace('\r')))
                .as("The four XML whitespace characters must all be whitespace.")
                .containsExactly(true, true, true, true);
    }

    @Test
    void testIsWhitespace_whenTheCharacterIsAnyOther_isFalse()
    {
        assertThat(List.of(XmlLexer.isWhitespace('a'), XmlLexer.isWhitespace('\f'),
                XmlLexer.isWhitespace((char) 0xA0)))
                .as("Letters, form feeds, and no-break spaces are not XML whitespace.")
                .containsExactly(false, false, false);
    }

    @Test
    void testTextBetween_whenCalled_returnsTheRange()
    {
        final XmlLexer lexer = lexerFor("<dataset/>");

        assertThat(lexer.textBetween(1, 8).toString()).as("The characters from start up to end.")
                .isEqualTo("dataset");
    }

    @Test
    void testSkipBom_whenTheTextStartsWithAByteOrderMark_movesPastIt()
    {
        final String text = String.valueOf((char) 0xFEFF) + "<dataset/>";

        final String remainder = remainderAfter(text, XmlLexer::skipBom);

        assertThat(remainder).as("A leading byte order mark must be skipped.").isEqualTo("<dataset/>");
    }

    @Test
    void testSkipBom_whenTheTextHasNoByteOrderMark_staysPut()
    {
        final String remainder = remainderAfter("<dataset/>", XmlLexer::skipBom);

        assertThat(remainder).as("Without a byte order mark, nothing must be skipped.")
                .isEqualTo("<dataset/>");
    }

    @Test
    void testSkipBom_whenTheTextIsEmpty_staysPut()
    {
        final String remainder = remainderAfter("", XmlLexer::skipBom);

        assertThat(remainder).as("An empty text has no byte order mark.").isEmpty();
    }

    @Test
    void testSkipWhitespace_whenWhitespaceLeads_stopsAtTheFirstOtherCharacter()
    {
        final String remainder = remainderAfter(" \t\r\n x y", XmlLexer::skipWhitespace);

        assertThat(remainder).as("Every leading whitespace character must be skipped, and no other.")
                .isEqualTo("x y");
    }

    @Test
    void testSkipWhitespace_whenAFormFeedLeads_staysPut()
    {
        final String remainder = remainderAfter("\f x", XmlLexer::skipWhitespace);

        assertThat(remainder).as("A form feed is not XML whitespace.").isEqualTo("\f x");
    }

    @Test
    void testSkipWhitespace_whenOnlyWhitespaceRemains_stopsAtTheEnd()
    {
        final String remainder = remainderAfter("  \n", XmlLexer::skipWhitespace);

        assertThat(remainder).as("Skipping must stop at the end of the text.").isEmpty();
    }

    @Test
    void testSkipProcessingInstruction_whenClosed_stopsAfterTheClosingMarker()
    {
        final String remainder =
                remainderAfter("<?xml version=\"1.0\"?> tail", XmlLexer::skipProcessingInstruction);

        assertThat(remainder).as("The whole processing instruction must be skipped.").isEqualTo(" tail");
    }

    @Test
    void testSkipProcessingInstruction_whenUnclosed_recordsABlockingProblemAtItsStart()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("ab<?target data", 2, XmlLexer::skipProcessingInstruction);

        assertThat(problems).as("An unclosed processing instruction must be reported where it starts.")
                .containsExactly(problemInFirstLine(Messages.Parser_unclosedProcessingInstruction, 2));
    }

    @Test
    void testSkipComment_whenClosed_stopsAfterTheClosingMarker()
    {
        final String remainder = remainderAfter("<!-- a > b --> tail", XmlLexer::skipComment);

        assertThat(remainder).as("The whole comment, including a greater-than sign, must be skipped.")
                .isEqualTo(" tail");
    }

    @Test
    void testSkipComment_whenUnclosed_recordsABlockingProblemAtItsStart()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("ab<!-- open", 2, XmlLexer::skipComment);

        assertThat(problems).as("An unclosed comment must be reported where it starts.")
                .containsExactly(problemInFirstLine(Messages.Parser_unclosedComment, 2));
    }

    @Test
    void testSkipCData_whenClosed_stopsAfterTheClosingMarker()
    {
        final String remainder = remainderAfter("<![CDATA[ a < b ]]> tail", XmlLexer::skipCData);

        assertThat(remainder).as("The whole CDATA section, including a less-than sign, must be skipped.")
                .isEqualTo(" tail");
    }

    @Test
    void testSkipCData_whenUnclosed_recordsABlockingProblemAtItsStart()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("ab<![CDATA[ open", 2, XmlLexer::skipCData);

        assertThat(problems).as("An unclosed CDATA section must be reported where it starts.")
                .containsExactly(problemInFirstLine(Messages.Parser_unclosedCdata, 2));
    }

    @Test
    void testSkipProcessingInstruction_whenItHoldsACharacterThatXmlDoesNotAllow_recordsABlockingProblemAtIt()
    {
        final String text = "<?target a" + CONTROL_CHARACTER + "b?>";

        final List<DatasetProblem> problems =
                problemsAfterFailure(text, 0, XmlLexer::skipProcessingInstruction);

        assertThat(problems).as("A control character must be reported where it is.")
                .containsExactly(problemInFirstLine(NLS.bind(Messages.Codec_notXmlCharacter, "1"), 10));
    }

    @Test
    void testSkipComment_whenItHoldsACharacterThatXmlDoesNotAllow_recordsABlockingProblemAtIt()
    {
        final String text = "<!-- a" + CONTROL_CHARACTER + "b -->";

        final List<DatasetProblem> problems = problemsAfterFailure(text, 0, XmlLexer::skipComment);

        assertThat(problems).as("A control character must be reported where it is.")
                .containsExactly(problemInFirstLine(NLS.bind(Messages.Codec_notXmlCharacter, "1"), 6));
    }

    @Test
    void testSkipComment_whenItHoldsALoneSurrogate_recordsABlockingProblemAtIt()
    {
        final String text = "<!--" + LONE_SURROGATE + "-->";

        final List<DatasetProblem> problems = problemsAfterFailure(text, 0, XmlLexer::skipComment);

        assertThat(problems).as("A surrogate without its partner is no XML character.")
                .containsExactly(problemInFirstLine(NLS.bind(Messages.Codec_notXmlCharacter, "D800"), 4));
    }

    @Test
    void testSkipComment_whenItHoldsASupplementaryCharacterAndAmpersands_stopsAfterTheClosingMarker()
    {
        final String remainder = remainderAfter("<!-- R&D " + EMOJI + " ]]> --> tail", XmlLexer::skipComment);

        assertThat(remainder).as("Text that is no markup inside a comment must be skipped as it is.")
                .isEqualTo(" tail");
    }

    @Test
    void testSkipCData_whenItHoldsACharacterThatXmlDoesNotAllow_recordsABlockingProblemAtIt()
    {
        final String text = "<![CDATA[a" + CONTROL_CHARACTER + "b]]>";

        final List<DatasetProblem> problems = problemsAfterFailure(text, 0, XmlLexer::skipCData);

        assertThat(problems).as("A control character must be reported where it is.")
                .containsExactly(problemInFirstLine(NLS.bind(Messages.Codec_notXmlCharacter, "1"), 10));
    }

    @Test
    void testSkipCData_whenItHoldsAmpersandsAndASupplementaryCharacter_stopsAfterTheClosingMarker()
    {
        final String remainder =
                remainderAfter("<![CDATA[R&D &nbsp; " + EMOJI + "]]> tail", XmlLexer::skipCData);

        assertThat(remainder).as("References inside a CDATA section are plain text.").isEqualTo(" tail");
    }

    @Test
    void testSkipXmlCharacter_whenAnOrdinaryCharacterIsNext_movesOverIt()
    {
        final String remainder = remainderAfter("ab", XmlLexer::skipXmlCharacter);

        assertThat(remainder).as("One character must be skipped.").isEqualTo("b");
    }

    @Test
    void testSkipXmlCharacter_whenASurrogatePairIsNext_movesOverBothHalves()
    {
        final String remainder = remainderAfter(EMOJI + "b", XmlLexer::skipXmlCharacter);

        assertThat(remainder).as("A surrogate pair is one character.").isEqualTo("b");
    }

    @Test
    void testSkipXmlCharacter_whenTheCharacterIsALineFeedOrTab_movesOverIt()
    {
        final List<String> remainders = List.of(remainderAfter("\nb", XmlLexer::skipXmlCharacter),
                remainderAfter("\tb", XmlLexer::skipXmlCharacter));

        assertThat(remainders).as("Tab and line feed are XML characters.").containsExactly("b", "b");
    }

    @Test
    void testSkipXmlCharacter_whenTheCharacterIsANoncharacterWithinTheXmlRange_movesOverIt()
    {
        final String remainder = remainderAfter((char) 0xFDD0 + "b", XmlLexer::skipXmlCharacter);

        assertThat(remainder).as("XML 1.0 allows U+FDD0, as it allows every character up to U+FFFD.")
                .isEqualTo("b");
    }

    @Test
    void testSkipXmlCharacter_whenTheCharacterIsAControlCharacter_recordsABlockingProblemAtIt()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("a" + CONTROL_CHARACTER, 1, XmlLexer::skipXmlCharacter);

        assertThat(problems).as("A control character is no XML character and must be reported.")
                .containsExactly(problemInFirstLine(NLS.bind(Messages.Codec_notXmlCharacter, "1"), 1));
    }

    @Test
    void testSkipXmlCharacter_whenTheCharacterIsOutsideTheXmlRange_recordsABlockingProblemAtIt()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure(String.valueOf((char) 0xFFFE), 0, XmlLexer::skipXmlCharacter);

        assertThat(problems).as("U+FFFE is outside the XML range and must be reported.")
                .containsExactly(problemInFirstLine(NLS.bind(Messages.Codec_notXmlCharacter, "FFFE"), 0));
    }

    @Test
    void testScanName_whenTheNameIsFollowedByWhitespace_stopsAtTheWhitespace()
    {
        final Scan scan = scan("USERS ID", XmlLexer::scanName);

        assertThat(scan).as("The name must be returned and the whitespace left unread.")
                .isEqualTo(new Scan("USERS", " ID"));
    }

    @Test
    void testScanName_whenTheNameHasPunctuation_keepsTheNameCharacters()
    {
        final Scan scan = scan("a-b.c:d>", XmlLexer::scanName);

        assertThat(scan).as("Hyphens, dots, and colons are name characters.")
                .isEqualTo(new Scan("a-b.c:d", ">"));
    }

    @Test
    void testScanName_whenTheNameHoldsASupplementaryCharacter_consumesItWhole()
    {
        final String supplementary = new String(Character.toChars(0x10000));
        final Scan scan = scan("a" + supplementary + "b c", XmlLexer::scanName);

        assertThat(scan).as("A supplementary character must be consumed as one code point.")
                .isEqualTo(new Scan("a" + supplementary + "b", " c"));
    }

    @Test
    void testScanName_whenTheSameNameIsScannedTwice_returnsOneSharedString()
    {
        final String text = "USERS USERS";
        final XmlLexer lexer = lexerFor(text);
        final String first = lexer.scanName();
        lexer.skipWhitespace();

        final String second = lexer.scanName();

        assertThat(second).as("Repeated names must share one string instead of one per occurrence.")
                .isSameAs(first);
    }

    @Test
    void testScanName_whenTheFirstCharacterCannotStartAName_recordsABlockingProblem()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("<1abc", 1, XmlLexer::scanName);

        assertThat(problems).as("A name that starts with a digit must be reported at that digit.")
                .containsExactly(problemInFirstLine(Messages.Parser_expectedName, 1));
    }

    @Test
    void testScanName_whenAtTheEnd_recordsABlockingProblem()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("<", 1, XmlLexer::scanName);

        assertThat(problems).as("A name is required at the end of the text too.")
                .containsExactly(problemInFirstLine(Messages.Parser_expectedName, 1));
    }

    @Test
    void testScanQuotedLiteral_whenDoubleQuoted_returnsTheValue()
    {
        final Scan scan = scan("\"my.dtd\" tail", XmlLexer::scanQuotedLiteral);

        assertThat(scan).as("The value without its quotes must be returned.")
                .isEqualTo(new Scan("my.dtd", " tail"));
    }

    @Test
    void testScanQuotedLiteral_whenSingleQuoted_returnsTheValueWithTheOtherQuoteInside()
    {
        final Scan scan = scan("'a\"b' tail", XmlLexer::scanQuotedLiteral);

        assertThat(scan).as("A double quote inside a single-quoted literal must not end it.")
                .isEqualTo(new Scan("a\"b", " tail"));
    }

    @Test
    void testScanQuotedLiteral_whenNoQuoteIsNext_recordsABlockingProblem()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("ab", 0, XmlLexer::scanQuotedLiteral);

        assertThat(problems).as("A literal must start with a quote.")
                .containsExactly(problemInFirstLine(Messages.Parser_expectedQuotedLiteral, 0));
    }

    @Test
    void testScanQuotedLiteral_whenUnclosed_recordsABlockingProblemAtTheOpeningQuote()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("ab\"open", 2, XmlLexer::scanQuotedLiteral);

        assertThat(problems).as("An unclosed literal must be reported at its opening quote.")
                .containsExactly(problemInFirstLine(Messages.Parser_unclosedLiteral, 2));
    }

    @Test
    void testExpectCharacter_whenTheExpectedCharacterIsNext_consumesIt()
    {
        final String remainder = remainderAfter("=x", lexer -> lexer.expectCharacter('=', "Unused."));

        assertThat(remainder).as("The expected character must be consumed.").isEqualTo("x");
    }

    @Test
    void testExpectCharacter_whenAnotherCharacterIsNext_recordsTheGivenMessage()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("a:b", 1, lexer -> lexer.expectCharacter('=', "Expected equals."));

        assertThat(problems).as("The problem must carry the message that the caller gave.")
                .containsExactly(problemInFirstLine("Expected equals.", 1));
    }

    @Test
    void testExpectCharacter_whenAtTheEnd_recordsTheGivenMessage()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("a", 1, lexer -> lexer.expectCharacter('=', "Expected equals."));

        assertThat(problems).as("The end of the text does not hold the expected character.")
                .containsExactly(problemInFirstLine("Expected equals.", 1));
    }

    @Test
    void testScanOpeningQuote_whenADoubleQuoteIsNext_returnsItAndMovesPastIt()
    {
        final XmlLexer lexer = lexerFor("\"x\"");

        final char quote = lexer.scanOpeningQuote("Unused.");

        assertThat(quote).as("The double quote must be returned.").isEqualTo('"');
        assertThat(lexer.position()).as("The opening quote must be consumed.").isEqualTo(1);
    }

    @Test
    void testScanOpeningQuote_whenASingleQuoteIsNext_returnsIt()
    {
        final XmlLexer lexer = lexerFor("'x'");

        final char quote = lexer.scanOpeningQuote("Unused.");

        assertThat(quote).as("The single quote must be returned.").isEqualTo('\'');
    }

    @Test
    void testScanOpeningQuote_whenNoQuoteIsNext_recordsTheGivenMessage()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("a=x", 2, lexer -> lexer.scanOpeningQuote("Expected quote."));

        assertThat(problems).as("The problem must carry the message that the caller gave.")
                .containsExactly(problemInFirstLine("Expected quote.", 2));
    }

    @Test
    void testScanOpeningQuote_whenAtTheEnd_recordsTheGivenMessage()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("a=", 2, lexer -> lexer.scanOpeningQuote("Expected quote."));

        assertThat(problems).as("The end of the text does not hold a quote.")
                .containsExactly(problemInFirstLine("Expected quote.", 2));
    }

    @Test
    void testScanToClosingQuote_whenClosed_returnsTheOffsetOfTheClosingQuote()
    {
        final XmlLexer lexer = lexerFor("\"abc\" tail");
        lexer.advance(1);

        final int closing = lexer.scanToClosingQuote('"', 1);

        assertThat(closing).as("The offset of the closing quote must be returned.").isEqualTo(4);
        assertThat(lexer.position()).as("The lexer must stand on the closing quote.").isEqualTo(4);
    }

    @Test
    void testScanToClosingQuote_whenTheValueHoldsTheOtherQuote_doesNotStopAtIt()
    {
        final XmlLexer lexer = lexerFor("'a\"b'");
        lexer.advance(1);

        final int closing = lexer.scanToClosingQuote('\'', 1);

        assertThat(closing).as("Only the matching quote closes the value.").isEqualTo(4);
    }

    @Test
    void testScanToClosingQuote_whenTheValueHoldsALessThan_recordsABlockingProblemAtIt()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("\"a<b\"", 1, lexer -> lexer.scanToClosingQuote('"', 1));

        assertThat(problems).as("A less-than sign in a value must be reported where it is.")
                .containsExactly(problemInFirstLine(Messages.Parser_lessThanInValue, 2));
    }

    @Test
    void testScanToClosingQuote_whenUnclosed_recordsABlockingProblemAtTheOpeningQuote()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("\"abc", 1, lexer -> lexer.scanToClosingQuote('"', 1));

        assertThat(problems).as("An unclosed value must be reported at its opening quote.")
                .containsExactly(problemInFirstLine(Messages.Parser_unclosedValue, 0));
    }
}
