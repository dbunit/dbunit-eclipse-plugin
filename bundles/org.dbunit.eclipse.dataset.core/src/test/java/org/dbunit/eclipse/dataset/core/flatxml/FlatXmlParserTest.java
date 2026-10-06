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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.eclipse.dataset.core.TestDatasets;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.SAXParseException;

/**
 * Tests {@link FlatXmlParser} against the scanning rules of the flat XML specification, and against real
 * dbUnit 3.5.2: for text content, comments, processing instructions, and CDATA sections, it compares
 * whether the parser reports a dataset as not well-formed with whether dbUnit fails to load it, so that the
 * editor neither lets the user edit a file that dbUnit cannot load nor refuses one that dbUnit loads.
 */
class FlatXmlParserTest
{
    private static final String CONTROL_CHARACTER = String.valueOf((char) 0x1);

    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private static final String NONCHARACTER_FFFE = String.valueOf((char) 0xFFFE);

    private static final String ARROW = String.valueOf((char) 0x2190);

    private static final String COMMA = String.valueOf((char) 0x3001);

    private static final String COMPATIBILITY_IDEOGRAPH = String.valueOf((char) 0xF900);

    private static final String COMBINING_GRAVE_ACCENT = String.valueOf((char) 0x300);

    private static final String COMBINING_LETTER_X = String.valueOf((char) 0x36F);

    private static final String THAI_LETTER = String.valueOf((char) 0xE01);

    private static final String IDEOGRAPH = String.valueOf((char) 0x4E00);

    private static final String MIDDLE_DOT = String.valueOf((char) 0xB7);

    private static final String XML_DECLARATION = "<?xml version=\"1.0\"?>\n";

    private static final String EXTERNAL_DOCTYPE =
            XML_DECLARATION + "<!DOCTYPE dataset SYSTEM \"plain.dtd\">\n";

    private static final String INTERNAL_SUBSET_START = XML_DECLARATION + "<!DOCTYPE dataset [\n"
            + "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS ANY>\n<!ATTLIST USERS ID CDATA #IMPLIED>\n";

    private static Stream<Arguments> textsWithAControlCharacter()
    {
        return Stream.of(
                Arguments.of("body text", "<dataset>\n    a" + CONTROL_CHARACTER + "b\n</dataset>"),
                Arguments.of("row text",
                        "<dataset>\n    <USERS ID=\"1\">a" + CONTROL_CHARACTER + "b</USERS>\n</dataset>"),
                Arguments.of("body comment", "<dataset>\n    <!--a" + CONTROL_CHARACTER + "b-->\n</dataset>"),
                Arguments.of("row comment", "<dataset>\n    <USERS ID=\"1\"><!--a" + CONTROL_CHARACTER
                        + "b--></USERS>\n</dataset>"),
                Arguments.of("body processing instruction",
                        "<dataset>\n    <?target a" + CONTROL_CHARACTER + "b?>\n</dataset>"),
                Arguments.of("body CDATA section",
                        "<dataset>\n    <![CDATA[a" + CONTROL_CHARACTER + "b]]>\n</dataset>"),
                Arguments.of("prolog comment", "<!--a" + CONTROL_CHARACTER + "b-->\n<dataset/>"),
                Arguments.of("prolog processing instruction",
                        "<?target a" + CONTROL_CHARACTER + "b?>\n<dataset/>"),
                Arguments.of("epilog comment", "<dataset/>\n<!--a" + CONTROL_CHARACTER + "b-->"),
                Arguments.of("internal subset comment",
                        "<!DOCTYPE dataset [\n<!--a" + CONTROL_CHARACTER + "b-->\n]>\n<dataset/>"));
    }

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
    void testParse_forAttributesOfTheSameName_givesThemTheUpperCasedNameAsOneSharedKey()
    {
        final String text = "<dataset><USERS Name=\"1\"/><ORDERS Name=\"2\" other=\"3\"/></dataset>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        final FlatXmlAttribute first = result.elements().get(0).attributes().get(0);
        final FlatXmlAttribute second = result.elements().get(1).attributes().get(0);
        final FlatXmlAttribute other = result.elements().get(1).attributes().get(1);
        assertThat(List.of(first.key(), other.key())).as("The key must be the name in upper case.")
                .containsExactly("NAME", "OTHER");
        assertThat(second.key())
                .as("Attributes of one name must share one key, which the parser works out only once.")
                .isSameAs(first.key());
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
    void testParse_whenElementsRepeatNames_sharesOneStringPerName()
    {
        final FlatXmlParseResult result =
                FlatXmlParser.parse("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>");

        final FlatXmlElement first = result.elements().get(0);
        final FlatXmlElement second = result.elements().get(1);
        assertThat(second.name()).as("Elements with the same name must share one name string.")
                .isSameAs(first.name());
        assertThat(second.attributes().get(0).name())
                .as("Attributes with the same name must share one name string.")
                .isSameAs(first.attributes().get(0).name());
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
    void testParse_whenAnIdeographicSpaceFollowsTheRoot_reportsNotWellFormedAtIt()
    {
        final String text = "<dataset><USERS ID=\"1\"/></dataset>\n\u3000";

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf('\u3000'));
    }

    @Test
    void testParse_whenEndTagNameDoesNotMatch_reportsNotWellFormedAtEndTagOffset()
    {
        final String text = TestDatasets.read("malformed/mismatched-end-tag.xml");

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf("</ORDERS"));
    }

    @Test
    void testParse_whenEndTagNameDoesNotMatch_namesBothElementsAndTheLineAndColumn()
    {
        final FlatXmlParseResult result = FlatXmlParser.parse("<dataset>\n<USERS></ORDERS></dataset>");

        assertThat(result.problems()).extracting(DatasetProblem::message)
                .as("The message must name the expected and the found element, and where the end tag is.")
                .containsExactly("Expected </USERS>, but found </ORDERS>. (line 2, column 8)");
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
    void testParse_whenBodyTextHasABareAmpersand_reportsNotWellFormedAtTheAmpersand()
    {
        final String text = TestDatasets.read("malformed/bare-ampersand-in-text.xml");

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf('&'));
    }

    @Test
    void testParse_whenARowHoldsAnUndeclaredEntityInItsText_reportsNotWellFormedAtTheAmpersand()
    {
        final String text = TestDatasets.read("malformed/undeclared-entity-in-text.xml");

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf('&'));
    }

    @Test
    void testParse_whenBodyTextHoldsACdataEnd_reportsNotWellFormedAtIt()
    {
        final String text = "<dataset>\n    a ]]> b\n    <USERS ID=\"1\"/>\n</dataset>";

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf("]]>"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("textsWithAControlCharacter")
    void testParse_whenAControlCharacterIsInTextOrMarkup_reportsNotWellFormedAtIt(final String place,
            final String text)
    {
        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf(CONTROL_CHARACTER));
    }

    @Test
    void testParse_whenTheDoctypeHasAnExternalSubset_acceptsAnEntityReferenceInText()
    {
        final String text = "<!DOCTYPE dataset SYSTEM \"dataset.dtd\">\n<dataset>\n    &nbsp;\n"
                + "    <USERS ID=\"1\"/>\n</dataset>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("The external DTD may declare the entity.").isTrue();
        assertThat(result.problems()).as("The text is still reported as ignored.")
                .extracting(DatasetProblem::code).containsExactly(ProblemCode.TEXT_CONTENT_IGNORED);
    }

    @Test
    void testParse_whenTheInternalSubsetDeclaresAnEntity_acceptsAnEntityReferenceInText()
    {
        final String text = "<!DOCTYPE dataset [\n<!ENTITY nbsp \"&#160;\">\n]>\n<dataset>\n    &nbsp;\n"
                + "    <USERS ID=\"1\"/>\n</dataset>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("The internal subset declares the entity.").isTrue();
    }

    @Test
    void testParse_whenTheInternalSubsetDeclaresNoEntity_reportsAnEntityReferenceInText()
    {
        final String text = "<!DOCTYPE dataset [\n<!ELEMENT dataset ANY>\n]>\n<dataset>\n    &nbsp;\n"
                + "    <USERS ID=\"1\"/>\n</dataset>";

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf('&'));
    }

    @Test
    void testParse_whenTheDoctypeDeclaresNothing_reportsAnEntityReferenceInText()
    {
        final String text = "<!DOCTYPE dataset>\n<dataset>\n    &nbsp;\n    <USERS ID=\"1\"/>\n</dataset>";

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf('&'));
    }

    @Test
    void testParse_whenTheDoctypeHasAnExternalSubset_reportsABareAmpersandInText()
    {
        final String text = "<!DOCTYPE dataset SYSTEM \"dataset.dtd\">\n<dataset>\n    R&D\n"
                + "    <USERS ID=\"1\"/>\n</dataset>";

        assertBlockingProblem(text, ProblemCode.NOT_WELL_FORMED, text.indexOf('&'));
    }

    @Test
    void testParse_whenTextAndMarkupHoldContentThatLooksOddButIsAllowed_staysWellFormed()
    {
        final String text = "<dataset>\n"
                + "    &amp; &lt; &gt; &quot; &apos; &#65; &#x41; ]] > a > b\n"
                + "    <!-- R&D ]]> " + EMOJI + " -->\n"
                + "    <?target R&D ]]> " + EMOJI + "?>\n"
                + "    <![CDATA[R&D &nbsp; ]]>\n"
                + "    <USERS ID=\"a]]>b\">" + EMOJI + (char) 0x7F + (char) 0xFDD0 + "</USERS>\n"
                + "</dataset>";

        final FlatXmlParseResult result = FlatXmlParser.parse(text);

        assertThat(result.wellFormed()).as("Everything in this text is allowed by XML 1.0.").isTrue();
        assertThat(result.elements()).as("The row element must still be found.").hasSize(1);
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

    private static String body(final String content)
    {
        return "<dataset>\n    " + content + "\n    <USERS ID=\"1\"/>\n</dataset>\n";
    }

    private static String rowOf(final String tableName, final String columnName)
    {
        return "<dataset>\n    <" + tableName + " " + columnName + "=\"1\"/>\n</dataset>\n";
    }

    private static String rowText(final String content)
    {
        return "<dataset>\n    <USERS ID=\"1\">" + content + "</USERS>\n</dataset>\n";
    }

    private static Stream<Arguments> malformedTexts()
    {
        return Stream.of(
                Arguments.of("a bare ampersand in body text", body("R&D fixtures")),
                Arguments.of("an ampersand and a space", body("R & D")),
                Arguments.of("an ampersand and a semicolon", body("&;")),
                Arguments.of("a reference without its semicolon", body("&amp more")),
                Arguments.of("a character reference without digits", body("&#;")),
                Arguments.of("a hexadecimal character reference without digits", body("&#x;")),
                Arguments.of("a character reference with a letter", body("&#12a;")),
                Arguments.of("a character reference with an uppercase X", body("&#X41;")),
                Arguments.of("a reference to a control character", body("&#1;")),
                Arguments.of("a reference to a surrogate", body("&#xD800;")),
                Arguments.of("a reference to U+FFFE", body("&#xFFFE;")),
                Arguments.of("a reference beyond U+10FFFF", body("&#x110000;")),
                Arguments.of("an undeclared entity in body text", body("&nbsp;")),
                Arguments.of("an undeclared entity in row text", rowText("&nbsp;")),
                Arguments.of("an undeclared entity next to an internal subset without entities",
                        INTERNAL_SUBSET_START + "]>\n" + body("&nbsp;")),
                Arguments.of("an undeclared entity in a DOCTYPE without a DTD",
                        XML_DECLARATION + "<!DOCTYPE dataset>\n" + body("&nbsp;")),
                Arguments.of("a CDATA end in body text", body("a ]]> b")),
                Arguments.of("a CDATA end in row text", rowText("a ]]> b")),
                Arguments.of("a CDATA end after a CDATA section", body("<![CDATA[x]]>]]>")),
                Arguments.of("a control character in body text", body("a" + CONTROL_CHARACTER + "b")),
                Arguments.of("a control character in row text", rowText("a" + CONTROL_CHARACTER + "b")),
                Arguments.of("U+FFFE in body text", body("a" + NONCHARACTER_FFFE + "b")),
                Arguments.of("a control character in a comment",
                        body("<!-- a" + CONTROL_CHARACTER + "b -->")),
                Arguments.of("U+FFFE in a comment", body("<!-- a" + NONCHARACTER_FFFE + "b -->")),
                Arguments.of("a control character in a processing instruction",
                        body("<?target a" + CONTROL_CHARACTER + "b?>")),
                Arguments.of("a control character in a CDATA section",
                        body("<![CDATA[a" + CONTROL_CHARACTER + "b]]>")),
                Arguments.of("a control character in a comment before the root",
                        "<!-- a" + CONTROL_CHARACTER + "b -->\n" + body("")),
                Arguments.of("a control character in a comment after the root",
                        body("") + "<!-- a" + CONTROL_CHARACTER + "b -->\n"),
                Arguments.of("a control character in a processing instruction before the root",
                        "<?target a" + CONTROL_CHARACTER + "b?>\n" + body("")),
                Arguments.of("a bare ampersand next to an external DTD", EXTERNAL_DOCTYPE + body("R&D")),
                Arguments.of("a CDATA end next to an external DTD", EXTERNAL_DOCTYPE + body("a ]]> b")),
                Arguments.of("a control character next to an external DTD",
                        EXTERNAL_DOCTYPE + body("a" + CONTROL_CHARACTER + "b")),
                Arguments.of("a reference to a control character next to an external DTD",
                        EXTERNAL_DOCTYPE + body("&#1;")),
                Arguments.of("a double hyphen in a comment", body("<!-- a -- b -->")),
                Arguments.of("a comment that ends with three hyphens", body("<!-- a --->")),
                Arguments.of("a double hyphen in a comment before the root",
                        "<!-- ---- USERS ---- -->\n" + body("")),
                Arguments.of("a double hyphen in a comment after the root",
                        body("") + "<!-- a -- b -->\n"),
                Arguments.of("a double hyphen in a comment of the internal subset",
                        INTERNAL_SUBSET_START + "<!-- a -- b -->\n]>\n" + body("")),
                Arguments.of("an XML declaration after white space", "\n" + XML_DECLARATION + body("")),
                Arguments.of("an XML declaration after a comment",
                        "<!-- c -->\n" + XML_DECLARATION + body("")),
                Arguments.of("a second XML declaration", XML_DECLARATION + XML_DECLARATION + body("")),
                Arguments.of("an XML declaration inside the root", body(XML_DECLARATION)),
                Arguments.of("an XML declaration after the root", body("") + XML_DECLARATION),
                Arguments.of("an XML declaration in the internal subset",
                        INTERNAL_SUBSET_START + XML_DECLARATION + "]>\n" + body("")),
                Arguments.of("a processing instruction target in capitals", body("<?XML data?>")),
                Arguments.of("a processing instruction target in mixed case", body("<?Xml data?>")),
                Arguments.of("an XML declaration in capitals", "<?XML version=\"1.0\"?>\n" + body("")),
                Arguments.of("a processing instruction without a target", body("<? data?>")),
                Arguments.of("a processing instruction target that does not start with a name character",
                        body("<?1abc data?>")),
                Arguments.of("a processing instruction target followed by a quote", body("<?target\"x\"?>")),
                Arguments.of("a table name that starts with an arrow", rowOf(ARROW + "T", "ID")),
                Arguments.of("a column name with an arrow in it", rowOf("T", "A" + ARROW + "B")),
                Arguments.of("a table name that starts with an ideographic comma", rowOf(COMMA + "T", "ID")),
                Arguments.of("a column name that starts with a compatibility ideograph",
                        rowOf("T", COMPATIBILITY_IDEOGRAPH + "ID")),
                Arguments.of("a column name with a supplementary character in it",
                        rowOf("T", "A" + EMOJI + "B")),
                Arguments.of("a table name that is a supplementary character", rowOf(EMOJI, "ID")),
                Arguments.of("a column name with a combining character that the fourth edition lacks",
                        rowOf("T", "A" + COMBINING_LETTER_X)),
                Arguments.of("a SYSTEM keyword with no white space before its literal",
                        XML_DECLARATION + "<!DOCTYPE dataset SYSTEM\"plain.dtd\">\n" + body("")),
                Arguments.of("a PUBLIC keyword with no white space before its literal",
                        XML_DECLARATION + "<!DOCTYPE dataset PUBLIC\"-//DbUnit//DTD Test//EN\" "
                                + "\"plain.dtd\">\n" + body("")),
                Arguments.of("a public and a system literal with no white space between them",
                        XML_DECLARATION + "<!DOCTYPE dataset PUBLIC \"-//DbUnit//DTD Test//EN\""
                                + "\"plain.dtd\">\n" + body("")));
    }

    private static Stream<Arguments> wellFormedTexts()
    {
        return Stream.of(
                Arguments.of("the five predefined entities in body text",
                        body("&amp; &lt; &gt; &quot; &apos;")),
                Arguments.of("the five predefined entities in row text",
                        rowText("&amp; &lt; &gt; &quot; &apos;")),
                Arguments.of("character references in body text",
                        body("&#65; &#x41; &#9; &#10; &#13; &#x1F600; &#xFDD0; &#x85;")),
                Arguments.of("greater-than signs and close brackets in body text", body("a > b ]] > c ] d")),
                Arguments.of("a CDATA end in a comment", body("<!-- a ]]> b -->")),
                Arguments.of("a CDATA end in a processing instruction", body("<?target a ]]> b?>")),
                Arguments.of("a CDATA end in an attribute value",
                        "<dataset>\n    <USERS ID=\"a]]>b\"/>\n</dataset>\n"),
                Arguments.of("a bare ampersand in a comment", body("<!-- R&D -->")),
                Arguments.of("a bare ampersand in a processing instruction", body("<?target R&D?>")),
                Arguments.of("a bare ampersand in a CDATA section", body("<![CDATA[R&D]]>")),
                Arguments.of("an undeclared entity in a CDATA section", body("<![CDATA[&nbsp;]]>")),
                Arguments.of("a bracket before the end of a CDATA section", body("<![CDATA[x]]]>")),
                Arguments.of("supplementary characters in text, a comment, and a CDATA section",
                        body("a" + EMOJI + "b <!-- " + EMOJI + " --><![CDATA[" + EMOJI + "]]>")),
                Arguments.of("DEL, NEL, and a noncharacter in body text",
                        body("a" + (char) 0x7F + (char) 0x85 + (char) 0xFDD0 + "b")),
                Arguments.of("an undeclared entity next to a SYSTEM identifier",
                        EXTERNAL_DOCTYPE + body("&nbsp;")),
                Arguments.of("an undeclared entity in row text next to a SYSTEM identifier",
                        EXTERNAL_DOCTYPE + rowText("&nbsp;")),
                Arguments.of("an undeclared entity next to PUBLIC and SYSTEM identifiers",
                        XML_DECLARATION + "<!DOCTYPE dataset PUBLIC \"-//DbUnit//DTD Test//EN\" "
                                + "\"plain.dtd\">\n" + body("&nbsp;")),
                Arguments.of("an entity that the external DTD declares",
                        XML_DECLARATION + "<!DOCTYPE dataset SYSTEM \"entity.dtd\">\n" + body("&nbsp;")),
                Arguments.of("an entity that the internal subset declares",
                        INTERNAL_SUBSET_START + "<!ENTITY nbsp \"&#160;\">\n]>\n" + body("&nbsp;")),
                Arguments.of("an entity that the internal subset declares, in row text",
                        INTERNAL_SUBSET_START + "<!ENTITY nbsp \"&#160;\">\n]>\n" + rowText("&nbsp;")),
                Arguments.of("an XML declaration at the start", XML_DECLARATION + body("")),
                Arguments.of("an XML declaration after a byte order mark",
                        "\uFEFF" + XML_DECLARATION + body("")),
                Arguments.of("an XML declaration with an encoding and a standalone flag",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + body("")),
                Arguments.of("a processing instruction whose target starts with xml",
                        body("<?xml-stylesheet href=\"a.xsl\"?>")),
                Arguments.of("a processing instruction before the root", "<?target data?>\n" + body("")),
                Arguments.of("a processing instruction without data", body("<?target?>")),
                Arguments.of("a processing instruction target with hyphens, dots, and digits",
                        body("<?a-b.c1 data?>")),
                Arguments.of("single hyphens in a comment", body("<!-- a - b - c -->")),
                Arguments.of("an empty comment", body("<!---->")),
                Arguments.of("a comment with a hyphen before its end", body("<!-- a- -->")),
                Arguments.of("a comment of hyphens and spaces", body("<!-- - - - -->")),
                Arguments.of("a comment in the internal subset",
                        INTERNAL_SUBSET_START + "<!-- a - b -->\n]>\n" + body("")),
                Arguments.of("a column name that starts with a Thai letter",
                        rowOf("USERS", THAI_LETTER + "ID")),
                Arguments.of("a column name that starts with an ideograph", rowOf("USERS", IDEOGRAPH + "ID")),
                Arguments.of("a column name with a middle dot and a combining accent in it",
                        rowOf("USERS", "A" + MIDDLE_DOT + "B" + COMBINING_GRAVE_ACCENT)),
                Arguments.of("a column name with a hyphen, a dot, and digits in it",
                        rowOf("USERS", "A-1.2")),
                Arguments.of("a DOCTYPE with line breaks and tabs between its parts",
                        XML_DECLARATION + "<!DOCTYPE\ndataset\tPUBLIC\r\n\"-//DbUnit//DTD Test//EN\"\n\t"
                                + "\"plain.dtd\"\n>\n" + body("")),
                Arguments.of("an internal subset that follows the literal with no white space",
                        XML_DECLARATION + "<!DOCTYPE dataset SYSTEM \"plain.dtd\"[]>\n" + body("")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedTexts")
    void testParse_whenTheTextIsNotWellFormed_dbUnitFailsToLoadItAndTheParserBlocksEditing(
            final String description, final String text, @TempDir final Path tempDir) throws Exception
    {
        final File file = write(tempDir, text);

        assertThatThrownBy(() -> new FlatXmlDataSetBuilder().build(file))
                .as("dbUnit must fail to load " + description + ", because its XML parser rejects it.")
                .isInstanceOf(DataSetException.class).hasRootCauseInstanceOf(SAXParseException.class);
        final FlatXmlParseResult result = FlatXmlParser.parse(text);
        assertThat(result.wellFormed())
                .as("The parser must report " + description + " as not well-formed, as dbUnit does.")
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wellFormedTexts")
    void testParse_whenTheTextIsWellFormed_dbUnitLoadsItAndTheParserAllowsEditing(final String description,
            final String text, @TempDir final Path tempDir) throws Exception
    {
        final File file = write(tempDir, text);

        final IDataSet dataSet = new FlatXmlDataSetBuilder().build(file);
        assertThat(dataSet.getTableNames()).as("dbUnit must load " + description + " as a dataset.")
                .containsExactly("USERS");
        final FlatXmlParseResult result = FlatXmlParser.parse(text);
        assertThat(result.wellFormed())
                .as("The parser must accept " + description + ", as dbUnit does.").isTrue();
    }

    /**
     * Writes the DTD files that some of the texts name, and the text as the dataset file that dbUnit loads.
     */
    private static File write(final Path directory, final String text) throws IOException
    {
        final String declarations =
                "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS ANY>\n<!ATTLIST USERS ID CDATA #IMPLIED>\n";
        Files.writeString(directory.resolve("plain.dtd"), declarations, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("entity.dtd"), declarations + "<!ENTITY nbsp \"&#160;\">\n",
                StandardCharsets.UTF_8);
        final Path file = directory.resolve("dataset.xml");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file.toFile();
    }
}
