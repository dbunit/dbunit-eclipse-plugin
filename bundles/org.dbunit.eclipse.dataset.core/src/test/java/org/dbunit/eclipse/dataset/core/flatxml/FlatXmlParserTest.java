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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.dbunit.eclipse.dataset.core.TestDatasets;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlParser} against the scanning rules of the flat XML specification.
 */
class FlatXmlParserTest
{
    @Test
    void testParse_whenTextIsEmpty_reportsRootNotDataset()
    {
        final FlatXmlParseResult result = FlatXmlParser.parse("");

        assertThat(result.wellFormed()).as("An empty document is not well-formed.").isFalse();
        assertThat(result.problems()).as("An empty document must report ROOT_NOT_DATASET.")
                .extracting(DatasetProblem::code).containsExactly(ProblemCode.ROOT_NOT_DATASET);
    }

    @Test
    void testParse_whenSelfClosingDataset_parsesToNoElements()
    {
        final FlatXmlParseResult result = FlatXmlParser.parse("<dataset/>");

        assertThat(result.wellFormed()).as("A self-closing <dataset/> must be well-formed.").isTrue();
        assertThat(result.elements()).as("A self-closing <dataset/> has no row elements.").isEmpty();
    }

    @Test
    void testParse_whenEmptyDataset_parsesToNoElements()
    {
        final FlatXmlParseResult result = FlatXmlParser.parse("<dataset></dataset>");

        assertThat(result.wellFormed()).as("<dataset></dataset> must be well-formed.").isTrue();
        assertThat(result.elements()).as("<dataset></dataset> has no row elements.").isEmpty();
    }

    @Test
    void testParse_whenAttributesUseBothQuoteStyles_recordsExactOffsets()
    {
        final String text = "<dataset><USERS ID=\"1\" NAME='Bob'/></dataset>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("The document must be well-formed.").isTrue();
        assertThat(result.elements()).as("There must be exactly one row element.").hasSize(1);
        final FlatXmlElement element = result.elements().get(0);
        assertThat(text.substring(element.offset(), element.nameEndOffset()))
                .as("The element offsets must start at '<USERS'.").isEqualTo("<USERS");
        assertThat(element.attributes()).as("There must be exactly two attributes.").hasSize(2);
        final FlatXmlAttribute id = element.attributes().get(0);
        final FlatXmlAttribute name = element.attributes().get(1);
        assertThat(id.quote()).as("ID must be double-quoted.").isEqualTo('"');
        assertThat(text.substring(id.nameOffset(), id.nameOffset() + id.name().length()))
                .as("The name offset must point at 'ID'.").isEqualTo("ID");
        assertThat(text.substring(id.valueOffset(), id.valueEndOffset()))
                .as("The value offset range must be the raw value '1'.").isEqualTo("1");
        assertThat(name.quote()).as("NAME must be single-quoted.").isEqualTo('\'');
        assertThat(text.substring(name.valueOffset(), name.valueEndOffset()))
                .as("The value offset range must be the raw value 'Bob'.").isEqualTo("Bob");
    }

    @Test
    void testParse_whenStartTagSpansSeveralLines_attributeSegmentsCoverExactlyTheAttributesRange()
    {
        final String text =
                "<dataset><USERS ID=\"1\"\n       NAME=\"Bob\"\n       EMAIL=\"b@x.org\"/></dataset>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("The document must be well-formed.").isTrue();
        final FlatXmlElement element = result.elements().get(0);
        final StringBuilder concatenated = new StringBuilder();
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            concatenated.append(text, attribute.segmentOffset(), attribute.endOffset());
        }
        final String expected = text.substring(element.nameEndOffset(), element.attributesEndOffset());
        assertThat(concatenated.toString())
                .as("The concatenated attribute segments must equal the attributes range verbatim.")
                .isEqualTo(expected);
    }

    @Test
    void testParse_whenElementHasAnEndTag_recordsEndTagOffsetAndEndOffset()
    {
        final String text = "<dataset><ORDERS ID=\"1\"></ORDERS></dataset>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        final FlatXmlElement element = result.elements().get(0);
        assertThat(element.selfClosing()).as("An element with an end tag is not self-closing.").isFalse();
        assertThat(text.substring(element.endTagOffset(), element.endOffset()))
                .as("endTagOffset and endOffset must bound the end tag exactly.").isEqualTo("</ORDERS>");
    }

    @Test
    void testParse_whenElementHasNoAttributes_hasEmptyAttributesList()
    {
        final FlatXmlParseResult result = FlatXmlParser.parse("<dataset><AUDIT_LOG/></dataset>");

        assertThat(result.elements().get(0).attributes()).as("An element without attributes has none.")
                .isEmpty();
    }

    @Test
    void testParse_whenCommentsAndProcessingInstructionsAppearEverywhere_areSkipped()
    {
        final String text = "<?xml version=\"1.0\"?><!-- prolog comment -->\n"
                + "<dataset><!-- body comment --><?body-pi?><USERS ID=\"1\"/></dataset>"
                + "<!-- epilog comment --><?epilog-pi?>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed())
                .as("Comments and processing instructions must not affect well-formedness.").isTrue();
        assertThat(result.elements()).as("Only the one row element must be recorded.").hasSize(1);
    }

    @Test
    void testParse_whenDoctypeHasSystemIdentifier_isRecorded()
    {
        final String text = "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset/>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("A DOCTYPE with SYSTEM must be well-formed.").isTrue();
        assertThat(result.doctype().rootName()).as("The root name must be recorded.").isEqualTo("dataset");
        assertThat(result.doctype().systemId()).as("The system identifier must be recorded.")
                .isEqualTo("my.dtd");
        assertThat(result.doctype().publicId()).as("There is no public identifier.").isNull();
        assertThat(result.doctype().internalSubset()).as("There is no internal subset.").isNull();
    }

    @Test
    void testParse_whenDoctypeHasPublicIdentifier_isRecorded()
    {
        final String text = "<!DOCTYPE dataset PUBLIC \"-//pub//id\" \"my.dtd\"><dataset/>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("A DOCTYPE with PUBLIC must be well-formed.").isTrue();
        assertThat(result.doctype().publicId()).as("The public identifier must be recorded.")
                .isEqualTo("-//pub//id");
        assertThat(result.doctype().systemId()).as("The system identifier must be recorded.")
                .isEqualTo("my.dtd");
    }

    @Test
    void testParse_whenInternalSubsetContainsGreaterThanAndBracketInsideQuotesAndComments_isRecordedWhole()
    {
        final String subset =
                "<!-- a comment with > and ] inside -->\n<!ATTLIST USERS NOTE CDATA \"a]b>c\">";
        final String text = "<!DOCTYPE dataset [" + subset + "]><dataset/>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("The document must be well-formed.").isTrue();
        assertThat(result.doctype().internalSubset()).as(
                "The internal subset text must be recorded exactly, including '>' and ']' inside quotes "
                        + "and comments.").isEqualTo(subset);
    }

    @Test
    void testParse_whenTextStartsWithByteOrderMark_isSkipped()
    {
        final String text = "﻿<dataset><USERS ID=\"1\"/></dataset>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("A leading BOM must not affect well-formedness.").isTrue();
        assertThat(result.root().offset()).as("The root offset must skip the BOM.").isEqualTo(1);
    }

    @Test
    void testParse_whenLineEndsAreCrLf_offsetsStayExact()
    {
        final String text = "<dataset>\r\n    <USERS ID=\"1\"/>\r\n</dataset>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("CR LF line ends must not affect well-formedness.").isTrue();
        final FlatXmlElement element = result.elements().get(0);
        assertThat(text.substring(element.offset(), element.endOffset()))
                .as("The element offsets must point exactly at the element text.")
                .isEqualTo("<USERS ID=\"1\"/>");
    }

    @Test
    void testParse_whenEditorSampleIsRewrittenWithCrLf_stillHasNoBlockingProblems()
    {
        final String lf = TestDatasets.read("editor-sample.xml");
        final String crlf = lf.replace("\n", "\r\n");

        final FlatXmlParseResult result = FlatXmlParser.parse(crlf);

        assertThat(result.wellFormed())
                .as("A CR LF variant of editor-sample.xml must still be well-formed.").isTrue();
    }

    @Test
    void testParse_whenBodyHasText_reportsTextContentIgnoredAndContinues()
    {
        final String text = "<dataset>stray text<USERS ID=\"1\"/></dataset>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("Stray text in the body must not block editing.").isTrue();
        assertThat(result.elements()).as("Parsing must continue and still find the row element.")
                .hasSize(1);
        assertThat(result.problems()).as("Exactly one TEXT_CONTENT_IGNORED problem must be reported.")
                .extracting(DatasetProblem::code).containsExactly(ProblemCode.TEXT_CONTENT_IGNORED);
        final DatasetProblem problem = result.problems().get(0);
        assertThat(text.substring(problem.offset(), problem.offset() + problem.length()))
                .as("The problem range must cover exactly the stray text.").isEqualTo("stray text");
    }

    @Test
    void testParse_whenRootIsNotDataset_reportsRootNotDatasetAtRootOffset()
    {
        final String text = TestDatasets.read("malformed/root-not-dataset.xml");

        assertBlockingProblem(text, ProblemCode.ROOT_NOT_DATASET, text.indexOf("<notdataset"));
    }

    @Test
    void testParse_whenElementIsNestedInARow_reportsNestedElementAtNestedOffset()
    {
        final String text = TestDatasets.read("malformed/nested-element.xml");

        assertBlockingProblem(text, ProblemCode.NESTED_ELEMENT, text.indexOf("<NESTED"));
    }

    @Test
    void testParse_whenEndTagNameDoesNotMatch_reportsNotWellFormedAtEndTagOffset()
    {
        final String text = TestDatasets.read("malformed/mismatched-end-tag.xml");

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf("</ORDERS"));
    }

    @Test
    void testParse_whenAttributeIsDuplicated_reportsNotWellFormedAtSecondAttributeNameOffset()
    {
        final String text = TestDatasets.read("malformed/duplicate-attribute.xml");

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.lastIndexOf("ID"));
    }

    @Test
    void testParse_whenValueContainsLessThan_reportsNotWellFormedAtLessThanOffset()
    {
        final String text = TestDatasets.read("malformed/less-than-in-value.xml");

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED,
                text.indexOf('<', text.indexOf("NAME=")));
    }

    @Test
    void testParse_whenWhitespaceIsMissingBetweenAttributes_reportsNotWellFormedAtSecondAttributeOffset()
    {
        final String text = TestDatasets.read("malformed/missing-whitespace-between-attributes.xml");

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf("NAME="));
    }

    @Test
    void testParse_whenEntityIsUnknown_reportsUnsupportedEntityAtAmpersandOffset()
    {
        final String text = TestDatasets.read("malformed/unknown-entity.xml");

        assertBlockingProblem(text, ProblemCode.UNSUPPORTED_ENTITY, text.indexOf('&'));
    }

    @Test
    void testParse_whenContentFollowsTheRoot_reportsNotWellFormedAtThatOffset()
    {
        final String text = TestDatasets.read("malformed/content-after-root.xml");

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf("<EXTRA"));
    }

    @Test
    void testParse_whenACommentIsUnterminated_reportsNotWellFormedAtCommentStart()
    {
        final String text = TestDatasets.read("malformed/unterminated-comment.xml");

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf("<!--"));
    }

    @Test
    void testParse_whenGivenEveryWellFormedFixture_hasNoBlockingProblems()
    {
        final List<String> fixtures = List.of("flatXmlDataSetTest.xml", "flatXmlDataSetDuplicateTest.xml",
                "flatXmlDataSetDuplicateMultipleCaseTest.xml", "flatXmlTableTest.xml",
                "flatXmlDataSetDtdDifferentCaseTest.xml", "editor-sample.xml", "column-sensing.xml",
                "first-element-empty.xml", "internal-subset.xml", "special-characters.xml");
        for (final String fixture : fixtures)
        {
            final FlatXmlParseResult result = FlatXmlParser.parse(TestDatasets.read(fixture));

            assertThat(result.wellFormed()).as(
                    "Fixture '" + fixture + "' must parse without a problem that blocks editing.")
                    .isTrue();
        }
    }

    @Test
    void testParse_whenGivenTheIso88591Fixture_hasNoBlockingProblems()
    {
        final String text = TestDatasets.read("iso-8859-1.xml", StandardCharsets.ISO_8859_1);

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed())
                .as("The ISO-8859-1 fixture must parse without a problem that blocks editing.").isTrue();
    }

    @Test
    void testParse_whenParsingLargeDatasets_charAtCallCountStaysLinear()
    {
        final String smallText = generateDataset(2000);
        final String largeText = generateDataset(4000);

        final CountingCharSequence smallCounted = new CountingCharSequence(smallText);
        FlatXmlParser.parse(smallCounted);
        final long smallCount = smallCounted.getCharAtCallCount();

        final CountingCharSequence largeCounted = new CountingCharSequence(largeText);
        FlatXmlParser.parse(largeCounted);
        final long largeCount = largeCounted.getCharAtCallCount();

        assertThat(smallCount).as("The charAt call count must not exceed 4 times the text length.")
                .isLessThanOrEqualTo(smallText.length() * 4L);
        assertThat(largeCount)
                .as("Doubling the rows must at most double the charAt call count (plus 1%).")
                .isLessThanOrEqualTo((long) (smallCount * 2.01));
    }

    private void assertBlockingProblem(final String text, final ProblemCode expectedCode,
            final int expectedOffset)
    {
        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("A document with this problem must not be well-formed.")
                .isFalse();
        assertThat(result.problems()).as("The blocking problem must be reported last.").last()
                .satisfies(problem ->
                {
                    assertThat(problem.code()).as("The problem code must match.").isEqualTo(expectedCode);
                    assertThat(problem.offset()).as("The problem offset must match.")
                            .isEqualTo(expectedOffset);
                });
    }

    private static String generateDataset(final int rowCount)
    {
        // A fixed-width id keeps every row the same length, so the text length (and so the expected
        // charAt count) scales exactly with rowCount instead of drifting with the id's digit count.
        final StringBuilder text = new StringBuilder();
        text.append("<dataset>\n");
        for (int i = 0; i < rowCount; i++)
        {
            final String id = String.format("%07d", i);
            text.append("    <USERS ID=\"").append(id).append("\" NAME=\"User ").append(id)
                    .append("\" EMAIL=\"user").append(id).append("@example.org\"/>\n");
        }
        text.append("</dataset>\n");
        return text.toString();
    }

    private static final class CountingCharSequence implements CharSequence
    {
        private final String delegate;

        private long charAtCallCount;

        private CountingCharSequence(final String delegate)
        {
            this.delegate = delegate;
        }

        private long getCharAtCallCount()
        {
            return charAtCallCount;
        }

        @Override
        public int length()
        {
            return delegate.length();
        }

        @Override
        public char charAt(final int index)
        {
            charAtCallCount++;
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end)
        {
            return delegate.subSequence(start, end);
        }

        @Override
        public String toString()
        {
            return delegate;
        }
    }
}
