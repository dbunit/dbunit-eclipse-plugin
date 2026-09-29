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

import java.util.List;

import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ScanProblems}: what it records for informational and blocking problems, and the line and
 * column that it adds to a blocking problem's message.
 */
class ScanProblemsTest
{
    private static DatasetProblem errorAt(final String message, final int offset)
    {
        return new DatasetProblem(ProblemCode.NOT_WELL_FORMED, ProblemSeverity.ERROR, message, null, null, -1,
                offset, 0);
    }

    @Test
    void testIsWellFormed_whenNothingWasRecorded_isTrue()
    {
        final ScanProblems problems = new ScanProblems("<dataset/>");

        assertThat(problems.isWellFormed()).as("A scan without problems is well-formed.").isTrue();
        assertThat(problems.toList()).as("A scan without problems has none listed.").isEmpty();
    }

    @Test
    void testAddInfoProblem_whenAdded_recordsAnInfoAndKeepsTheTextWellFormed()
    {
        final ScanProblems problems = new ScanProblems("<dataset>stray</dataset>");

        problems.addInfoProblem(ProblemCode.TEXT_CONTENT_IGNORED, "The text is ignored.", 9, 5);

        assertThat(problems.toList()).as("The info problem must be recorded as given.")
                .containsExactly(new DatasetProblem(ProblemCode.TEXT_CONTENT_IGNORED, ProblemSeverity.INFO,
                        "The text is ignored.", null, null, -1, 9, 5));
        assertThat(problems.isWellFormed()).as("An info problem does not block editing.").isTrue();
    }

    @Test
    void testBlockingError_whenRecorded_addsTheLineAndColumnAndMarksTheTextNotWellFormed()
    {
        final ScanProblems problems = new ScanProblems("<dataset>\n<USERS></ORDERS></dataset>");

        problems.blockingError(ProblemCode.NOT_WELL_FORMED, "Expected </USERS>, but found </ORDERS>.", 17);

        assertThat(problems.toList()).as("The message must end with the line and column of the offset.")
                .containsExactly(
                        errorAt("Expected </USERS>, but found </ORDERS>. (line 2, column 8)", 17));
        assertThat(problems.isWellFormed()).as("A blocking problem makes the text not well-formed.")
                .isFalse();
    }

    @Test
    void testBlockingError_whenTheOffsetIsZero_reportsLineOneColumnOne()
    {
        final ScanProblems problems = new ScanProblems("<dataset/>");

        problems.blockingError(ProblemCode.NOT_WELL_FORMED, "Problem.", 0);

        assertThat(problems.toList()).as("The first character is at line 1, column 1.")
                .containsExactly(errorAt("Problem. (line 1, column 1)", 0));
    }

    @Test
    void testBlockingError_whenLinesEndWithCarriageReturnAndLineFeed_countsEachPairOnce()
    {
        final ScanProblems problems = new ScanProblems("a\r\nb\r\nc");

        problems.blockingError(ProblemCode.NOT_WELL_FORMED, "Problem.", 6);

        assertThat(problems.toList()).as("Two CR LF pairs must count as two line ends.")
                .containsExactly(errorAt("Problem. (line 3, column 1)", 6));
    }

    @Test
    void testBlockingError_whenLinesEndWithLineFeedOnly_countsEachOne()
    {
        final ScanProblems problems = new ScanProblems("a\nb\nc");

        problems.blockingError(ProblemCode.NOT_WELL_FORMED, "Problem.", 4);

        assertThat(problems.toList()).as("Two line feeds must count as two line ends.")
                .containsExactly(errorAt("Problem. (line 3, column 1)", 4));
    }

    @Test
    void testBlockingError_whenLinesEndWithCarriageReturnOnly_countsEachOne()
    {
        final ScanProblems problems = new ScanProblems("a\rb\rc");

        problems.blockingError(ProblemCode.NOT_WELL_FORMED, "Problem.", 4);

        assertThat(problems.toList()).as("Two carriage returns must count as two line ends.")
                .containsExactly(errorAt("Problem. (line 3, column 1)", 4));
    }

    @Test
    void testBlockingError_whenTheOffsetIsInTheMiddleOfALine_countsTheColumnFromOne()
    {
        final ScanProblems problems = new ScanProblems("<dataset>\n  <USERS ID=\"1\"/>");

        problems.blockingError(ProblemCode.NOT_WELL_FORMED, "Problem.", 12);

        assertThat(problems.toList()).as("Offset 12 is the third character of line 2.")
                .containsExactly(errorAt("Problem. (line 2, column 3)", 12));
    }

    @Test
    void testBlockingError_whenTheOffsetIsBeyondTheText_countsToTheEndOfTheTextOnly()
    {
        final ScanProblems problems = new ScanProblems("ab");

        problems.blockingError(ProblemCode.NOT_WELL_FORMED, "Problem.", 10);

        assertThat(problems.toList())
                .as("The location must stop at the end of the text, and the offset stays as given.")
                .containsExactly(errorAt("Problem. (line 1, column 3)", 10));
    }

    @Test
    void testBlockingError_whenACauseIsGiven_returnsAnExceptionWithThatCause()
    {
        final ScanProblems problems = new ScanProblems("<dataset/>");
        final IllegalStateException cause = new IllegalStateException("cause");

        final StopScanException exception =
                problems.blockingError(ProblemCode.NOT_WELL_FORMED, "Problem.", 0, cause);

        assertThat(exception.getCause()).as("The exception must carry the exception behind the problem.")
                .isSameAs(cause);
    }

    @Test
    void testBlockingError_whenNoCauseIsGiven_returnsAnExceptionWithoutCause()
    {
        final ScanProblems problems = new ScanProblems("<dataset/>");

        final StopScanException exception =
                problems.blockingError(ProblemCode.NOT_WELL_FORMED, "Problem.", 0);

        assertThat(exception.getCause()).as("There is no exception behind the problem.").isNull();
    }

    @Test
    void testToList_whenInfoAndBlockingProblemsWereRecorded_listsThemInTheOrderRecorded()
    {
        final ScanProblems problems = new ScanProblems("<dataset>stray<!-- open");

        problems.addInfoProblem(ProblemCode.TEXT_CONTENT_IGNORED, "The text is ignored.", 9, 5);
        problems.blockingError(ProblemCode.NOT_WELL_FORMED, "Unclosed comment.", 14);

        assertThat(problems.toList()).as("The info problem must come before the blocking problem.")
                .containsExactly(
                        new DatasetProblem(ProblemCode.TEXT_CONTENT_IGNORED, ProblemSeverity.INFO,
                                "The text is ignored.", null, null, -1, 9, 5),
                        errorAt("Unclosed comment. (line 1, column 15)", 14));
    }

    @Test
    void testToList_whenProblemsAreAddedAfterwards_returnsASnapshot()
    {
        final ScanProblems problems = new ScanProblems("<dataset>stray</dataset>");
        problems.addInfoProblem(ProblemCode.TEXT_CONTENT_IGNORED, "First.", 9, 5);
        final List<DatasetProblem> snapshot = problems.toList();

        problems.addInfoProblem(ProblemCode.TEXT_CONTENT_IGNORED, "Second.", 9, 5);

        assertThat(snapshot).as("A list taken earlier must not change when more problems are recorded.")
                .hasSize(1);
    }
}
