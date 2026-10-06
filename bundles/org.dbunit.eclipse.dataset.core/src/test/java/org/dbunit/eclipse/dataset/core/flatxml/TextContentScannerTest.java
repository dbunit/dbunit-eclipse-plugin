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
import java.util.stream.Stream;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.osgi.util.NLS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests {@link TextContentScanner}: each text is skipped up to the next tag, and whether it held more than
 * whitespace and what was left unread are asserted together. Text that XML 1.0 does not allow must record
 * one blocking problem at the offending character or reference and stop the scan.
 */
class TextContentScannerTest
{
    private static final boolean ENTITIES_CANNOT_BE_DECLARED = false;

    private static final boolean ENTITIES_MAY_BE_DECLARED = true;

    private static final String CONTROL_CHARACTER = String.valueOf((char) 0x1);

    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private record Skip(boolean significant, String remainder)
    {
    }

    private static Skip skip(final String text, final boolean entitiesMayBeDeclared)
    {
        final ScanProblems problems = new ScanProblems(text);
        final XmlLexer lexer = new XmlLexer(text, problems);
        final TextContentScanner scanner = new TextContentScanner(lexer, problems);
        final boolean significant = scanner.skip(entitiesMayBeDeclared);
        final String remainder = text.substring(lexer.position());
        return new Skip(significant, remainder);
    }

    private static List<DatasetProblem> problemsAfterFailure(final String text,
            final boolean entitiesMayBeDeclared)
    {
        final ScanProblems problems = new ScanProblems(text);
        final XmlLexer lexer = new XmlLexer(text, problems);
        final TextContentScanner scanner = new TextContentScanner(lexer, problems);
        assertThatThrownBy(() -> scanner.skip(entitiesMayBeDeclared)).as("Malformed text must stop the scan.")
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

    private static Stream<Arguments> ampersandsThatStartNoReference()
    {
        return Stream.of(Arguments.of("R&D fixtures", 1), Arguments.of("R & D", 2), Arguments.of("&;", 0),
                Arguments.of("&1;", 0), Arguments.of("&", 0), Arguments.of("&#", 0),
                Arguments.of("&amp more", 0), Arguments.of("x &amp", 2), Arguments.of("&#65 more", 0),
                Arguments.of("&a b;", 0));
    }

    private static Stream<Arguments> characterReferencesWithAnInvalidDigit()
    {
        return Stream.of(Arguments.of("&#12a;", "12a"), Arguments.of("&#X41;", "X41"),
                Arguments.of("&#xZZ;", "ZZ"), Arguments.of("&#x4G;", "4G"));
    }

    private static Stream<Arguments> characterReferencesToCharactersThatXmlDoesNotAllow()
    {
        return Stream.of(Arguments.of("&#1;", "1"), Arguments.of("&#0;", "0"), Arguments.of("&#xB;", "B"),
                Arguments.of("&#12;", "C"), Arguments.of("&#xD800;", "D800"),
                Arguments.of("&#xFFFE;", "FFFE"), Arguments.of("&#xFFFF;", "FFFF"));
    }

    @Test
    void testSkip_whenTheTextIsOnlyWhitespace_isNotSignificantAndStopsAtTheNextTag()
    {
        final Skip result = skip("\r\n\t  <USERS/>", ENTITIES_CANNOT_BE_DECLARED);

        assertThat(result).as("Whitespace is skipped, and the tag that ends the text is left.")
                .isEqualTo(new Skip(false, "<USERS/>"));
    }

    @Test
    void testSkip_whenATagIsNext_skipsNothing()
    {
        final Skip result = skip("<USERS/>", ENTITIES_CANNOT_BE_DECLARED);

        assertThat(result).as("There is no text before the tag.").isEqualTo(new Skip(false, "<USERS/>"));
    }

    @Test
    void testSkip_whenTheTextIsEmpty_skipsNothing()
    {
        final Skip result = skip("", ENTITIES_CANNOT_BE_DECLARED);

        assertThat(result).as("An empty text has no content.").isEqualTo(new Skip(false, ""));
    }

    @Test
    void testSkip_whenTheTextHoldsWords_isSignificantAndStopsAtTheNextTag()
    {
        final Skip result = skip("  stray text\n<USERS/>", ENTITIES_CANNOT_BE_DECLARED);

        assertThat(result).as("Words are content, and the tag that ends the text is left.")
                .isEqualTo(new Skip(true, "<USERS/>"));
    }

    @Test
    void testSkip_whenTheTextRunsToTheEnd_stopsAtTheEnd()
    {
        final Skip result = skip("stray text", ENTITIES_CANNOT_BE_DECLARED);

        assertThat(result).as("The text ends where the document does.").isEqualTo(new Skip(true, ""));
    }

    @Test
    void testSkip_whenTheTextHoldsGreaterThanSignsAndCloseBrackets_acceptsThem()
    {
        final Skip result = skip("a > b ]] > c ] d ]>", ENTITIES_CANNOT_BE_DECLARED);

        assertThat(result).as("Only the three characters ]]> in a row are forbidden in text.")
                .isEqualTo(new Skip(true, ""));
    }

    @ParameterizedTest
    @ValueSource(strings = { "&amp;", "&lt;", "&gt;", "&quot;", "&apos;" })
    void testSkip_whenTheTextHoldsAPredefinedEntityReference_acceptsItWithoutADtd(final String reference)
    {
        final Skip result = skip(reference + "<", ENTITIES_CANNOT_BE_DECLARED);

        assertThat(result).as("The five predefined entities need no declaration.")
                .isEqualTo(new Skip(true, "<"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "&#65;", "&#x41;", "&#0065;", "&#9;", "&#xA;", "&#13;", "&#x1F600;", "&#xFDD0;",
            "&#x85;" })
    void testSkip_whenTheTextHoldsACharacterReference_acceptsIt(final String reference)
    {
        final Skip result = skip(reference + "<", ENTITIES_CANNOT_BE_DECLARED);

        assertThat(result).as("A reference to an XML character is well-formed.")
                .isEqualTo(new Skip(true, "<"));
    }

    @Test
    void testSkip_whenTheTextHoldsSeveralReferences_acceptsThemAll()
    {
        final Skip result = skip("R&amp;D &lt;b&gt; &#65;&#x42;<", ENTITIES_CANNOT_BE_DECLARED);

        assertThat(result).as("Every reference of a text is checked, and the text goes on after each one.")
                .isEqualTo(new Skip(true, "<"));
    }

    @Test
    void testSkip_whenTheTextHoldsCharactersThatXmlAllowsButDiscourages_acceptsThem()
    {
        final String text = "a" + EMOJI + (char) 0x7F + (char) 0x85 + (char) 0xFDD0 + "z";

        final Skip result = skip(text, ENTITIES_CANNOT_BE_DECLARED);

        assertThat(result).as("A supplementary character, DEL, NEL, and a noncharacter are XML characters.")
                .isEqualTo(new Skip(true, ""));
    }

    @Test
    void testSkip_whenEntitiesMayBeDeclared_acceptsAReferenceToAnyEntity()
    {
        final Skip result = skip("a &nbsp; b<", ENTITIES_MAY_BE_DECLARED);

        assertThat(result).as("A DTD may declare the entity, so the reference is accepted.")
                .isEqualTo(new Skip(true, "<"));
    }

    @ParameterizedTest
    @MethodSource("ampersandsThatStartNoReference")
    void testSkip_whenAnAmpersandStartsNoReference_recordsABlockingProblemAtIt(final String text,
            final int offset)
    {
        final List<DatasetProblem> problems = problemsAfterFailure(text, ENTITIES_MAY_BE_DECLARED);

        assertThat(problems).as("A bare ampersand must be reported at the ampersand.")
                .containsExactly(problemInFirstLine(Messages.Codec_invalidAmpersand, offset));
    }

    @ParameterizedTest
    @ValueSource(strings = { "&#;", "&#x;" })
    void testSkip_whenACharacterReferenceHasNoDigits_recordsABlockingProblemAtTheAmpersand(final String text)
    {
        final List<DatasetProblem> problems = problemsAfterFailure(text, ENTITIES_MAY_BE_DECLARED);

        assertThat(problems).as("A character reference needs at least one digit.")
                .containsExactly(problemInFirstLine(Messages.Codec_referenceWithoutDigits, 0));
    }

    @ParameterizedTest
    @MethodSource("characterReferencesWithAnInvalidDigit")
    void testSkip_whenACharacterReferenceHasAnInvalidDigit_recordsABlockingProblemAtTheAmpersand(
            final String text, final String digits)
    {
        final List<DatasetProblem> problems = problemsAfterFailure(text, ENTITIES_MAY_BE_DECLARED);

        assertThat(problems).as("A digit that does not fit the radix must be reported.")
                .containsExactly(problemInFirstLine(NLS.bind(Messages.Codec_invalidReference, digits), 0));
    }

    @ParameterizedTest
    @MethodSource("characterReferencesToCharactersThatXmlDoesNotAllow")
    void testSkip_whenACharacterReferenceNamesNoXmlCharacter_recordsABlockingProblemAtTheAmpersand(
            final String text, final String hexadecimal)
    {
        final List<DatasetProblem> problems = problemsAfterFailure(text, ENTITIES_MAY_BE_DECLARED);

        assertThat(problems).as("A reference to a character that XML forbids must be reported.")
                .containsExactly(problemInFirstLine(
                        NLS.bind(Messages.Codec_referenceNotXmlCharacter, hexadecimal), 0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "&#x110000;", "&#99999999999;" })
    void testSkip_whenACharacterReferenceIsTooLarge_recordsABlockingProblemAtTheAmpersand(final String text)
    {
        final List<DatasetProblem> problems = problemsAfterFailure(text, ENTITIES_MAY_BE_DECLARED);

        assertThat(problems).as("A reference beyond U+10FFFF must be reported.")
                .containsExactly(problemInFirstLine(Messages.Codec_referenceTooLarge, 0));
    }

    @Test
    void testSkip_whenTheTextHoldsACdataEnd_recordsABlockingProblemAtIt()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("a ]]> b", ENTITIES_MAY_BE_DECLARED);

        assertThat(problems).as("The three characters ]]> must be reported where they start.")
                .containsExactly(problemInFirstLine(Messages.Parser_cdataEndInText, 2));
    }

    @Test
    void testSkip_whenTheTextHoldsAControlCharacter_recordsABlockingProblemAtIt()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("  a" + CONTROL_CHARACTER + "b", ENTITIES_MAY_BE_DECLARED);

        assertThat(problems).as("A character that XML forbids must be reported where it is.")
                .containsExactly(problemInFirstLine(NLS.bind(Messages.Codec_notXmlCharacter, "0001"), 3));
    }

    @Test
    void testSkip_whenTheTextHoldsALoneSurrogate_recordsABlockingProblemAtIt()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("a" + (char) 0xD800 + "b", ENTITIES_MAY_BE_DECLARED);

        assertThat(problems).as("A surrogate without its partner is no XML character.")
                .containsExactly(problemInFirstLine(NLS.bind(Messages.Codec_notXmlCharacter, "D800"), 1));
    }

    @ParameterizedTest
    @ValueSource(strings = { "&nbsp;", "a &copy; b", "&AMP;", "&Lt;" })
    void testSkip_whenEntitiesCannotBeDeclared_recordsABlockingProblemForAnyOtherEntity(final String text)
    {
        final List<DatasetProblem> problems = problemsAfterFailure(text, ENTITIES_CANNOT_BE_DECLARED);

        final int offset = text.indexOf('&');
        final String name = text.substring(offset + 1, text.indexOf(';'));
        assertThat(problems).as("An entity that nothing declares must be reported at its ampersand.")
                .containsExactly(
                        problemInFirstLine(NLS.bind(Messages.Parser_entityNotDeclared, name), offset));
    }
}
