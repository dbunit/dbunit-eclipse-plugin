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

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.osgi.util.NLS;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DoctypeScanner}: the declaration that it returns, with the offsets of its start, its
 * internal subset, and its end, and the blocking problem that each kind of malformed declaration records.
 */
class DoctypeScannerTest
{
    private static FlatXmlDoctype scanAt(final String text, final int start)
    {
        final ScanProblems problems = new ScanProblems(text);
        final XmlLexer lexer = new XmlLexer(text, problems);
        lexer.advance(start);
        return new DoctypeScanner(lexer, problems).scan();
    }

    private static List<DatasetProblem> problemsAfterFailure(final String text, final int start)
    {
        final ScanProblems problems = new ScanProblems(text);
        final XmlLexer lexer = new XmlLexer(text, problems);
        lexer.advance(start);
        final DoctypeScanner scanner = new DoctypeScanner(lexer, problems);
        assertThatThrownBy(scanner::scan).as("A malformed declaration must stop the scan.")
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
    void testScan_whenTheDeclarationHasNoIdentifier_returnsOnlyTheRootName()
    {
        final String text = "<!DOCTYPE dataset>";

        final FlatXmlDoctype doctype = scanAt(text, 0);

        assertThat(doctype).as("Only the root name and the offsets must be set.")
                .isEqualTo(new FlatXmlDoctype("dataset", null, null, null, -1, 0, text.length()));
    }

    @Test
    void testScan_whenTheDeclarationHasASystemIdentifier_returnsIt()
    {
        final String text = "<!DOCTYPE dataset SYSTEM \"my.dtd\">";

        final FlatXmlDoctype doctype = scanAt(text, 0);

        assertThat(doctype).as("The system identifier must be returned without its quotes.")
                .isEqualTo(new FlatXmlDoctype("dataset", null, "my.dtd", null, -1, 0, text.length()));
    }

    @Test
    void testScan_whenTheDeclarationHasAPublicIdentifier_returnsBothIdentifiers()
    {
        final String text = "<!DOCTYPE dataset PUBLIC '-//pub//id' \"my.dtd\">";

        final FlatXmlDoctype doctype = scanAt(text, 0);

        assertThat(doctype).as("Both identifiers must be returned, whichever quotes they use.")
                .isEqualTo(new FlatXmlDoctype("dataset", "-//pub//id", "my.dtd", null, -1, 0, text.length()));
    }

    @Test
    void testScan_whenTheDeclarationHasAnInternalSubset_returnsItsTextAndOffset()
    {
        final String subset = "<!ELEMENT dataset ANY>";
        final String text = "<!DOCTYPE dataset [" + subset + "]>";

        final FlatXmlDoctype doctype = scanAt(text, 0);

        assertThat(doctype).as("The subset text and the offset of its first character must be returned.")
                .isEqualTo(new FlatXmlDoctype("dataset", null, null, subset, text.indexOf('[') + 1, 0,
                        text.length()));
    }

    @Test
    void testScan_whenTheDeclarationHasASystemIdentifierAndAnInternalSubset_returnsBoth()
    {
        final String subset = "<!ELEMENT dataset ANY>";
        final String text = "<!DOCTYPE dataset SYSTEM \"my.dtd\" [" + subset + "]>";

        final FlatXmlDoctype doctype = scanAt(text, 0);

        assertThat(doctype).as("The identifier and the subset must both be returned.")
                .isEqualTo(new FlatXmlDoctype("dataset", null, "my.dtd", subset, text.indexOf('[') + 1, 0,
                        text.length()));
    }

    @Test
    void testScan_whenTheSubsetHoldsBracketsAndAnglesInLiteralsCommentsAndInstructions_returnsItWhole()
    {
        final String subset = "<!-- a comment with > and ] inside -->\n<!ATTLIST USERS NOTE CDATA \"a]b>c\">"
                + "<!ATTLIST USERS TEXT CDATA 'x]y'><?pi ]?>";
        final String text = "<!DOCTYPE dataset [" + subset + "]>";

        final FlatXmlDoctype doctype = scanAt(text, 0);

        assertThat(doctype.internalSubset())
                .as("A bracket inside a literal, a comment, or an instruction must not end the subset.")
                .isEqualTo(subset);
    }

    @Test
    void testScan_whenTheDeclarationDoesNotStartTheText_recordsOffsetsFromTheStartOfTheText()
    {
        final String prefix = "<?xml version=\"1.0\"?>\n";
        final String subset = "<!ELEMENT dataset ANY>";
        final String text = prefix + "<!DOCTYPE dataset [" + subset + "]>";

        final FlatXmlDoctype doctype = scanAt(text, prefix.length());

        assertThat(doctype).as("All offsets must count from the start of the whole text.")
                .isEqualTo(new FlatXmlDoctype("dataset", null, null, subset,
                        prefix.length() + "<!DOCTYPE dataset [".length(), prefix.length(), text.length()));
    }

    @Test
    void testScan_whenWhitespaceSurroundsTheParts_isAccepted()
    {
        final String text = "<!DOCTYPE \n dataset \t SYSTEM \r\n \"d.dtd\"  >";

        final FlatXmlDoctype doctype = scanAt(text, 0);

        assertThat(doctype).as("Any run of whitespace may separate the parts.")
                .isEqualTo(new FlatXmlDoctype("dataset", null, "d.dtd", null, -1, 0, text.length()));
    }

    @Test
    void testScan_whenTheRootNameIsMissing_recordsABlockingProblemAfterTheKeyword()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("<!DOCTYPE>", 0);

        assertThat(problems).as("The name is expected right after the keyword and its whitespace.")
                .containsExactly(problemInFirstLine(Messages.Parser_expectedDoctypeName, 9));
    }

    @Test
    void testScan_whenNoWhitespaceSeparatesTheRootName_recordsABlockingProblemAfterTheKeyword()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("<!DOCTYPEdataset>", 0);

        assertThat(problems).as("Whitespace is required between the keyword and the name.")
                .containsExactly(problemInFirstLine(Messages.Parser_expectedDoctypeName, 9));
    }

    @Test
    void testScan_whenTheRootNameCannotStartAName_recordsABlockingProblemAtIt()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("<!DOCTYPE 1abc>", 0);

        assertThat(problems).as("A digit cannot start the root name.")
                .containsExactly(problemInFirstLine(Messages.Parser_expectedDoctypeName, 10));
    }

    @Test
    void testScan_whenNoWhitespaceFollowsSystem_recordsABlockingProblemAtTheLiteral()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("<!DOCTYPE dataset SYSTEM\"d.dtd\">", 0);

        assertThat(problems).as("Whitespace is required between SYSTEM and its literal.")
                .containsExactly(problemInFirstLine(Messages.Parser_expectedWhitespaceAfterIdKeyword, 24));
    }

    @Test
    void testScan_whenNoWhitespaceFollowsPublic_recordsABlockingProblemAtTheLiteral()
    {
        final List<DatasetProblem> problems =
                problemsAfterFailure("<!DOCTYPE dataset PUBLIC'-//pub//id' \"d.dtd\">", 0);

        assertThat(problems).as("Whitespace is required between PUBLIC and its literal.")
                .containsExactly(problemInFirstLine(Messages.Parser_expectedWhitespaceAfterIdKeyword, 24));
    }

    @Test
    void testScan_whenNoWhitespaceSeparatesThePublicAndTheSystemLiteral_recordsABlockingProblemAtTheSecond()
    {
        final String text = "<!DOCTYPE dataset PUBLIC '-//pub//id'\"d.dtd\">";

        final List<DatasetProblem> problems = problemsAfterFailure(text, 0);

        assertThat(problems).as("Whitespace is required between the public and the system literal.")
                .containsExactly(problemInFirstLine(Messages.Parser_expectedWhitespaceBetweenIds,
                        text.indexOf("\"d.dtd")));
    }

    @Test
    void testScan_whenThePublicIdentifierHasNoSystemLiteral_recordsABlockingProblemAtTheEnd()
    {
        final String text = "<!DOCTYPE dataset PUBLIC '-//pub//id'>";

        final List<DatasetProblem> problems = problemsAfterFailure(text, 0);

        assertThat(problems).as("What is missing is the literal, not whitespace before it.")
                .containsExactly(
                        problemInFirstLine(Messages.Parser_expectedQuotedLiteral, text.length() - 1));
    }

    @Test
    void testScan_whenTheInternalSubsetFollowsTheLiteralWithoutWhitespace_isAccepted()
    {
        final String subset = "<!ELEMENT dataset ANY>";
        final String text = "<!DOCTYPE dataset SYSTEM \"d.dtd\"[" + subset + "]>";

        final FlatXmlDoctype doctype = scanAt(text, 0);

        assertThat(doctype).as("Whitespace before the bracket of the subset is optional.")
                .isEqualTo(new FlatXmlDoctype("dataset", null, "d.dtd", subset, text.indexOf('[') + 1, 0,
                        text.length()));
    }

    @Test
    void testScan_whenTheSystemIdentifierIsNotQuoted_recordsABlockingProblemAtIt()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("<!DOCTYPE dataset SYSTEM d.dtd>", 0);

        assertThat(problems).as("The identifier must be a quoted literal.")
                .containsExactly(problemInFirstLine(Messages.Parser_expectedQuotedLiteral, 25));
    }

    @Test
    void testScan_whenTheSystemIdentifierIsNotClosed_recordsABlockingProblemAtItsOpeningQuote()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("<!DOCTYPE dataset SYSTEM \"d.dtd>", 0);

        assertThat(problems).as("An unclosed literal must be reported at its opening quote.")
                .containsExactly(problemInFirstLine(Messages.Parser_unclosedLiteral, 25));
    }

    @Test
    void testScan_whenTheInternalSubsetIsNotClosed_recordsABlockingProblemAtTheEndOfTheText()
    {
        final String text = "<!DOCTYPE dataset [<!ELEMENT a EMPTY>";

        final List<DatasetProblem> problems = problemsAfterFailure(text, 0);

        assertThat(problems).as("A subset without its closing bracket must be reported at the end.")
                .containsExactly(problemInFirstLine(Messages.Parser_unclosedSubset, text.length()));
    }

    @Test
    void testScan_whenALiteralInTheSubsetIsNotClosed_recordsABlockingProblemAtItsOpeningQuote()
    {
        final String text = "<!DOCTYPE dataset [<!ATTLIST a b CDATA \"x]>";

        final List<DatasetProblem> problems = problemsAfterFailure(text, 0);

        assertThat(problems).as("An unclosed literal in the subset must be reported at its opening quote.")
                .containsExactly(
                        problemInFirstLine(Messages.Parser_unclosedSubsetLiteral, text.indexOf('"')));
    }

    @Test
    void testScan_whenACommentInTheSubsetIsNotClosed_recordsABlockingProblemAtItsStart()
    {
        final List<DatasetProblem> problems = problemsAfterFailure("<!DOCTYPE dataset [<!-- open", 0);

        assertThat(problems).as("An unclosed comment must be reported where it starts.")
                .containsExactly(problemInFirstLine(Messages.Parser_unclosedComment, 19));
    }

    @Test
    void testScan_whenTheClosingAngleBracketIsMissing_recordsABlockingProblemAtTheEndOfTheText()
    {
        final String text = "<!DOCTYPE dataset SYSTEM \"d.dtd\"";

        final List<DatasetProblem> problems = problemsAfterFailure(text, 0);

        assertThat(problems).as("A declaration without its closing bracket must be reported at the end.")
                .containsExactly(problemInFirstLine(Messages.Parser_unclosedDoctype, text.length()));
    }
}
