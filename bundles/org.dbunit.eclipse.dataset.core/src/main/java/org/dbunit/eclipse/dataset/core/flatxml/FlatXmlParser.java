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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.eclipse.osgi.util.NLS;

/**
 * Scans dbUnit flat XML text once, left to right, with a hand-written scanner instead of a standard XML
 * parser, because edits need exact offsets of every element, attribute name, and raw attribute value.
 */
final class FlatXmlParser
{
    private FlatXmlParser()
    {
    }

    /**
     * Parses flat XML text.
     *
     * @param text The document text; its offsets equal {@code IDocument} offsets.
     * @return The parse result.
     */
    static FlatXmlParseResult parse(final CharSequence text)
    {
        final ScanProblems problems = new ScanProblems(text);
        final XmlLexer lexer = new XmlLexer(text, problems);
        return new Scanner(lexer, problems).scan();
    }

    /**
     * Holds the mutable state of one scan. A fresh instance is used for each call to {@link #parse}.
     */
    private static final class Scanner
    {
        private final XmlLexer lexer;

        private final ScanProblems problems;

        private final List<FlatXmlElement> elements = new ArrayList<>();

        /**
         * The attribute names of the start tag being scanned; start tags never nest, so one set serves
         * them all.
         */
        private final Set<String> startTagAttributeNames = new HashSet<>();

        private FlatXmlDoctype doctype;

        private FlatXmlRoot root;

        private Scanner(final XmlLexer lexer, final ScanProblems problems)
        {
            this.lexer = lexer;
            this.problems = problems;
        }

        private FlatXmlParseResult scan()
        {
            try
            {
                lexer.skipBom();
                scanProlog();
                scanRoot();
                scanEpilog();
            }
            catch (final StopScanException ignored)
            {
                // The scan stopped at the first blocking problem; the fields already hold the partial
                // result.
            }
            return new FlatXmlParseResult(doctype, root, List.copyOf(elements), problems.toList(),
                    problems.isWellFormed());
        }

        private void scanProlog()
        {
            while (true)
            {
                lexer.skipWhitespace();
                if (lexer.atEnd())
                {
                    throw problems.blockingError(ProblemCode.ROOT_NOT_DATASET,
                            Messages.Parser_noRootElement, lexer.position());
                }
                if (lexer.isElementStart())
                {
                    return;
                }
                if (!scanPrologMarkup())
                {
                    throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                            Messages.Parser_unexpectedBeforeRoot, lexer.position());
                }
            }
        }

        /**
         * Scans the processing instruction, comment, or DOCTYPE declaration at the current position; a second
         * DOCTYPE declaration is not one.
         *
         * @return True when one was scanned, false when the text at the current position is none of them.
         */
        private boolean scanPrologMarkup()
        {
            boolean scanned = true;
            if (lexer.atText("<?"))
            {
                lexer.skipProcessingInstruction();
            }
            else if (lexer.atText("<!--"))
            {
                lexer.skipComment();
            }
            else if (doctype == null && lexer.atText("<!DOCTYPE"))
            {
                scanDoctype();
            }
            else
            {
                scanned = false;
            }
            return scanned;
        }

        private void scanRoot()
        {
            final int rootOffset = lexer.position();
            final StartTag startTag = scanStartTag();
            if (!"dataset".equals(startTag.name()))
            {
                throw problems.blockingError(ProblemCode.ROOT_NOT_DATASET,
                        NLS.bind(Messages.Parser_rootNotDataset, startTag.name()),
                        rootOffset);
            }
            if (startTag.selfClosing())
            {
                root = new FlatXmlRoot(rootOffset, startTag.startTagEndOffset(), true, -1,
                        startTag.startTagEndOffset());
                return;
            }
            final int endTagOffset = scanChildren("dataset", true);
            final int endOffset = lexer.position();
            root = new FlatXmlRoot(rootOffset, startTag.startTagEndOffset(), false, endTagOffset, endOffset);
        }

        private void scanEpilog()
        {
            while (!lexer.atEnd())
            {
                final char ch = lexer.currentCharacter();
                if (XmlLexer.isWhitespace(ch))
                {
                    lexer.advance(1);
                }
                else if (lexer.atText("<?"))
                {
                    lexer.skipProcessingInstruction();
                }
                else if (lexer.atText("<!--"))
                {
                    lexer.skipComment();
                }
                else
                {
                    throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                            Messages.Parser_unexpectedAfterRoot, lexer.position());
                }
            }
        }

        private void scanRowElement()
        {
            final int elementOffset = lexer.position();
            final StartTag startTag = scanStartTag();
            if (startTag.selfClosing())
            {
                elements.add(new FlatXmlElement(startTag.name(), elementOffset, startTag.nameEndOffset(),
                        startTag.attributes(), startTag.attributesEndOffset(),
                        startTag.startTagEndOffset(), true, -1, startTag.startTagEndOffset()));
                return;
            }
            final int endTagOffset = scanChildren(startTag.name(), false);
            final int endOffset = lexer.position();
            elements.add(new FlatXmlElement(startTag.name(), elementOffset, startTag.nameEndOffset(),
                    startTag.attributes(), startTag.attributesEndOffset(), startTag.startTagEndOffset(),
                    false, endTagOffset, endOffset));
        }

        /**
         * Scans an element's start tag: {@code <name} followed by zero or more attributes, then
         * {@code >} or {@code />}. Used for both the root and row elements; the caller decides what an
         * element's name and attributes mean.
         */
        private StartTag scanStartTag()
        {
            lexer.advance(1); // consume '<'
            final String name = lexer.scanName();
            final int nameEndOffset = lexer.position();
            final List<FlatXmlAttribute> attributes = new ArrayList<>();
            startTagAttributeNames.clear();
            int attributesEndOffset = nameEndOffset;
            while (true)
            {
                final int segmentOffset = lexer.position();
                lexer.skipWhitespace();
                final boolean hadWhitespace = lexer.position() > segmentOffset;
                if (lexer.atText("/>"))
                {
                    lexer.advance(2);
                    final int startTagEndOffset = lexer.position();
                    return new StartTag(name, nameEndOffset, List.copyOf(attributes), attributesEndOffset,
                            startTagEndOffset, true);
                }
                if (lexer.atText(">"))
                {
                    lexer.advance(1);
                    final int startTagEndOffset = lexer.position();
                    return new StartTag(name, nameEndOffset, List.copyOf(attributes), attributesEndOffset,
                            startTagEndOffset, false);
                }
                requireAttributeStart(hadWhitespace, attributes.isEmpty());
                final FlatXmlAttribute attribute = scanAttribute(segmentOffset);
                rememberAttributeName(attribute);
                attributes.add(attribute);
                attributesEndOffset = attribute.endOffset();
            }
        }

        /**
         * Requires that an attribute starts at the current position: the text must not end here, and
         * whitespace must come before the attribute.
         */
        private void requireAttributeStart(final boolean hadWhitespace, final boolean firstAttribute)
        {
            if (lexer.atEnd())
            {
                throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_endedInStartTag,
                        lexer.position());
            }
            if (!hadWhitespace)
            {
                throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                        firstAttribute
                                ? Messages.Parser_expectedAfterElementName
                                : Messages.Parser_expectedWhitespaceBetweenAttributes,
                        lexer.position());
            }
        }

        private void rememberAttributeName(final FlatXmlAttribute attribute)
        {
            if (!startTagAttributeNames.add(attribute.name()))
            {
                final String message = NLS.bind(Messages.Parser_duplicateAttribute, attribute.name());
                throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, message, attribute.nameOffset());
            }
        }

        private FlatXmlAttribute scanAttribute(final int segmentOffset)
        {
            final int nameOffset = lexer.position();
            final String name = lexer.scanName();
            lexer.skipWhitespace();
            lexer.expectCharacter('=', Messages.Parser_expectedEquals);
            lexer.skipWhitespace();
            final char quote = lexer.scanOpeningQuote(Messages.Parser_expectedQuotedValue);
            final int valueOffset = lexer.position();
            final int valueEndOffset = lexer.scanToClosingQuote(quote, valueOffset);
            final CharSequence rawValue = lexer.textBetween(valueOffset, valueEndOffset);
            final String value = decodeValue(rawValue, valueOffset);
            lexer.advance(1); // consume the closing quote
            return new FlatXmlAttribute(name, value, segmentOffset, nameOffset, valueOffset, valueEndOffset,
                    quote);
        }

        private String decodeValue(final CharSequence raw, final int valueOffset)
        {
            try
            {
                return AttributeValueCodec.decode(raw);
            }
            catch (final AttributeValueException e)
            {
                final ProblemCode code =
                        e.isUnsupportedEntity() ? ProblemCode.UNSUPPORTED_ENTITY : ProblemCode.NOT_WELL_FORMED;
                throw problems.blockingError(code, e.getMessage(), valueOffset + e.getOffset(), e);
            }
        }

        /**
         * Scans the children of an element, from just after its start tag's {@code >} to just after its
         * end tag's {@code >}. When allowElements is true, a bare {@code <} starts a row element
         * (scanned recursively); otherwise it is a nested element, which flat XML does not support.
         *
         * @return The offset of the end tag's opening angle bracket.
         */
        private int scanChildren(final String endTagName, final boolean allowElements)
        {
            final TextRun run = new TextRun();
            int endTagOffset = -1;
            while (endTagOffset < 0)
            {
                if (lexer.atEnd())
                {
                    throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                            NLS.bind(Messages.Parser_endedBeforeEndTag, endTagName), lexer.position());
                }
                final char ch = lexer.currentCharacter();
                final int offset = lexer.position();
                if (ch != '<')
                {
                    run.include(offset, !XmlLexer.isWhitespace(ch));
                    lexer.advance(1);
                }
                else if (lexer.atText("</"))
                {
                    run.end(offset);
                    endTagOffset = scanEndTag(endTagName);
                }
                else
                {
                    scanMarkup(run, allowElements);
                }
            }
            return endTagOffset;
        }

        /**
         * Scans the processing instruction, comment, CDATA section, or row element at the current
         * position, which is at a {@code <} that does not start an end tag.
         */
        private void scanMarkup(final TextRun run, final boolean allowElements)
        {
            final int offset = lexer.position();
            if (lexer.atText("<?"))
            {
                run.end(offset);
                lexer.skipProcessingInstruction();
            }
            else if (lexer.atText("<!--"))
            {
                run.end(offset);
                lexer.skipComment();
            }
            else if (lexer.atText("<![CDATA["))
            {
                run.include(offset, true);
                lexer.skipCData();
            }
            else if (allowElements)
            {
                run.end(offset);
                scanRowElement();
            }
            else
            {
                throw problems.blockingError(ProblemCode.NESTED_ELEMENT, Messages.Parser_nestedElement,
                        offset);
            }
        }

        private int scanEndTag(final String expectedName)
        {
            final int endTagStart = lexer.position();
            lexer.advance(2); // consume "</"
            final String name = lexer.scanName();
            lexer.skipWhitespace();
            if (!lexer.atCharacter('>'))
            {
                final String message = NLS.bind(Messages.Parser_unclosedEndTag, name);
                throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, message, lexer.position());
            }
            lexer.advance(1); // consume '>'
            if (!name.equals(expectedName))
            {
                throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                        NLS.bind(Messages.Parser_mismatchedEndTag, expectedName, name), endTagStart);
            }
            return endTagStart;
        }

        /**
         * The text between the markup in an element, which the scan reports once, as ignored, when it holds
         * more than whitespace. Whitespace does not end a run of text (rule 5): only markup does, so that
         * "stray text" reports as one problem rather than one per word.
         */
        private final class TextRun
        {
            private int start = -1;

            private boolean significant;

            private void include(final int offset, final boolean isSignificant)
            {
                if (start < 0)
                {
                    start = offset;
                }
                if (isSignificant)
                {
                    significant = true;
                }
            }

            private void end(final int offset)
            {
                if (start >= 0 && significant)
                {
                    problems.addInfoProblem(ProblemCode.TEXT_CONTENT_IGNORED, Messages.Parser_textIgnored,
                            start, offset - start);
                }
                start = -1;
                significant = false;
            }
        }

        private void scanDoctype()
        {
            final int doctypeOffset = lexer.position();
            lexer.advance("<!DOCTYPE".length());
            final String rootName = scanDoctypeName();
            lexer.skipWhitespace();
            String publicId = null;
            String systemId = null;
            if (lexer.atText("SYSTEM"))
            {
                lexer.advance("SYSTEM".length());
                lexer.skipWhitespace();
                systemId = lexer.scanQuotedLiteral();
                lexer.skipWhitespace();
            }
            else if (lexer.atText("PUBLIC"))
            {
                lexer.advance("PUBLIC".length());
                lexer.skipWhitespace();
                publicId = lexer.scanQuotedLiteral();
                lexer.skipWhitespace();
                systemId = lexer.scanQuotedLiteral();
                lexer.skipWhitespace();
            }
            String internalSubset = null;
            int internalSubsetOffset = -1;
            if (lexer.atCharacter('['))
            {
                lexer.advance(1);
                internalSubsetOffset = lexer.position();
                skipToMatchingCloseBracket();
                final int internalSubsetEndOffset = lexer.position();
                internalSubset = lexer.textBetween(internalSubsetOffset, internalSubsetEndOffset).toString();
                lexer.advance(1); // consume ']'
                lexer.skipWhitespace();
            }
            lexer.expectCharacter('>', Messages.Parser_unclosedDoctype);
            final int doctypeEndOffset = lexer.position();
            doctype = new FlatXmlDoctype(rootName, publicId, systemId, internalSubset, internalSubsetOffset,
                    doctypeOffset, doctypeEndOffset);
        }

        private String scanDoctypeName()
        {
            final int beforeNameWhitespace = lexer.position();
            lexer.skipWhitespace();
            if (lexer.position() == beforeNameWhitespace || !lexer.atNameStart())
            {
                throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                        Messages.Parser_expectedDoctypeName, lexer.position());
            }
            return lexer.scanName();
        }

        /**
         * Advances to the ']' that closes an internal subset, skipping quoted literals, comments, and
         * processing instructions so that '>' and ']' inside them do not end it early.
         */
        private void skipToMatchingCloseBracket()
        {
            while (!lexer.atEnd() && lexer.currentCharacter() != ']')
            {
                skipSubsetItem();
            }
            if (lexer.atEnd())
            {
                throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_unclosedSubset,
                        lexer.position());
            }
        }

        /**
         * Skips the quoted literal, comment, processing instruction, or other character at the current
         * position in an internal subset.
         */
        private void skipSubsetItem()
        {
            final char ch = lexer.currentCharacter();
            if (ch == '"' || ch == '\'')
            {
                skipSubsetLiteral(ch);
            }
            else if (lexer.atText("<!--"))
            {
                lexer.skipComment();
            }
            else if (lexer.atText("<?"))
            {
                lexer.skipProcessingInstruction();
            }
            else
            {
                lexer.advance(1);
            }
        }

        private void skipSubsetLiteral(final char quote)
        {
            lexer.advance(1);
            while (!lexer.atEnd() && lexer.currentCharacter() != quote)
            {
                lexer.advance(1);
            }
            if (lexer.atEnd())
            {
                throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                        Messages.Parser_unclosedSubsetLiteral, lexer.position());
            }
            lexer.advance(1); // consume the closing quote
        }
    }

    /**
     * The parsed head of an element: its name and attributes, before the caller decides what they mean.
     */
    private record StartTag(String name, int nameEndOffset, List<FlatXmlAttribute> attributes,
            int attributesEndOffset, int startTagEndOffset, boolean selfClosing)
    {
    }
}
