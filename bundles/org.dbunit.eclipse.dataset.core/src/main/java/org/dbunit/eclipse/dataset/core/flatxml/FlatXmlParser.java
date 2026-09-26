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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
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
        return new Scanner(text).scan();
    }

    /**
     * Holds the mutable state of one scan. A fresh instance is used for each call to {@link #parse}.
     */
    private static final class Scanner
    {
        private static final StopScanException STOP_SCAN = new StopScanException();

        private final CharSequence text;

        private final int length;

        private final List<FlatXmlElement> elements = new ArrayList<>();

        private final List<DatasetProblem> problems = new ArrayList<>();

        /**
         * Each distinct element and attribute name, so that the elements share one string per name
         * instead of holding one per occurrence.
         */
        private final Map<String, String> names = new HashMap<>();

        /**
         * The attribute names of the start tag being scanned; start tags never nest, so one set serves
         * them all.
         */
        private final Set<String> startTagAttributeNames = new HashSet<>();

        private int pos;

        private FlatXmlDoctype doctype;

        private FlatXmlRoot root;

        private boolean wellFormed = true;

        private Scanner(final CharSequence text)
        {
            this.text = text;
            this.length = text.length();
        }

        private FlatXmlParseResult scan()
        {
            try
            {
                skipBom();
                scanProlog();
                scanRoot();
                scanEpilog();
            }
            catch (final StopScanException e)
            {
                // The scan stopped at the first blocking problem; the fields already hold the partial
                // result.
            }
            return new FlatXmlParseResult(doctype, root, List.copyOf(elements), List.copyOf(problems),
                    wellFormed);
        }

        private void skipBom()
        {
            if (length > 0 && text.charAt(0) == '﻿')
            {
                pos = 1;
            }
        }

        private void scanProlog()
        {
            while (true)
            {
                skipWhitespace();
                if (pos >= length)
                {
                    throw blockingError(ProblemCode.ROOT_NOT_DATASET,
                            Messages.Parser_noRootElement, pos);
                }
                if (matchesAt(pos, "<?"))
                {
                    skipProcessingInstruction();
                }
                else if (matchesAt(pos, "<!--"))
                {
                    skipComment();
                }
                else if (doctype == null && matchesAt(pos, "<!DOCTYPE"))
                {
                    scanDoctype();
                }
                else if (isElementStart())
                {
                    return;
                }
                else
                {
                    throw blockingError(ProblemCode.NOT_WELL_FORMED,
                            Messages.Parser_unexpectedBeforeRoot, pos);
                }
            }
        }

        private void scanRoot()
        {
            final int rootOffset = pos;
            final StartTag startTag = scanStartTag();
            if (!"dataset".equals(startTag.name()))
            {
                throw blockingError(ProblemCode.ROOT_NOT_DATASET,
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
            root = new FlatXmlRoot(rootOffset, startTag.startTagEndOffset(), false, endTagOffset, pos);
        }

        private void scanEpilog()
        {
            while (pos < length)
            {
                if (isWhitespace(text.charAt(pos)))
                {
                    pos++;
                }
                else if (matchesAt(pos, "<?"))
                {
                    skipProcessingInstruction();
                }
                else if (matchesAt(pos, "<!--"))
                {
                    skipComment();
                }
                else
                {
                    throw blockingError(ProblemCode.NOT_WELL_FORMED,
                            Messages.Parser_unexpectedAfterRoot, pos);
                }
            }
        }

        private boolean isElementStart()
        {
            return text.charAt(pos) == '<' && pos + 1 < length
                    && XmlNames.isNameStartChar(Character.codePointAt(text, pos + 1));
        }

        private void scanRowElement()
        {
            final int elementOffset = pos;
            final StartTag startTag = scanStartTag();
            if (startTag.selfClosing())
            {
                elements.add(new FlatXmlElement(startTag.name(), elementOffset, startTag.nameEndOffset(),
                        startTag.attributes(), startTag.attributesEndOffset(),
                        startTag.startTagEndOffset(), true, -1, startTag.startTagEndOffset()));
                return;
            }
            final int endTagOffset = scanChildren(startTag.name(), false);
            elements.add(new FlatXmlElement(startTag.name(), elementOffset, startTag.nameEndOffset(),
                    startTag.attributes(), startTag.attributesEndOffset(), startTag.startTagEndOffset(),
                    false, endTagOffset, pos));
        }

        /**
         * Scans an element's start tag: {@code <name} followed by zero or more attributes, then
         * {@code >} or {@code />}. Used for both the root and row elements; the caller decides what an
         * element's name and attributes mean.
         */
        private StartTag scanStartTag()
        {
            pos++; // consume '<'
            final String name = scanName();
            final int nameEndOffset = pos;
            final List<FlatXmlAttribute> attributes = new ArrayList<>();
            startTagAttributeNames.clear();
            int attributesEndOffset = nameEndOffset;
            while (true)
            {
                final int segmentOffset = pos;
                skipWhitespace();
                final boolean hadWhitespace = pos > segmentOffset;
                if (matchesAt(pos, "/>"))
                {
                    pos += 2;
                    return new StartTag(name, nameEndOffset, List.copyOf(attributes), attributesEndOffset,
                            pos, true);
                }
                if (matchesAt(pos, ">"))
                {
                    pos++;
                    return new StartTag(name, nameEndOffset, List.copyOf(attributes), attributesEndOffset,
                            pos, false);
                }
                if (pos >= length)
                {
                    throw blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_endedInStartTag, pos);
                }
                if (!hadWhitespace)
                {
                    throw blockingError(ProblemCode.NOT_WELL_FORMED,
                            attributes.isEmpty()
                                    ? Messages.Parser_expectedAfterElementName
                                    : Messages.Parser_expectedWhitespaceBetweenAttributes,
                            pos);
                }
                final FlatXmlAttribute attribute = scanAttribute(segmentOffset);
                if (!startTagAttributeNames.add(attribute.name()))
                {
                    final String message = NLS.bind(Messages.Parser_duplicateAttribute, attribute.name());
                    throw blockingError(ProblemCode.NOT_WELL_FORMED, message, attribute.nameOffset());
                }
                attributes.add(attribute);
                attributesEndOffset = attribute.endOffset();
            }
        }

        private FlatXmlAttribute scanAttribute(final int segmentOffset)
        {
            final int nameOffset = pos;
            final String name = scanName();
            skipWhitespace();
            if (pos >= length || text.charAt(pos) != '=')
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_expectedEquals,
                        pos);
            }
            pos++; // consume '='
            skipWhitespace();
            if (pos >= length || (text.charAt(pos) != '"' && text.charAt(pos) != '\''))
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_expectedQuotedValue,
                        pos);
            }
            final char quote = text.charAt(pos);
            pos++; // consume the opening quote
            final int valueOffset = pos;
            while (pos < length)
            {
                final char valueChar = text.charAt(pos);
                if (valueChar == quote)
                {
                    break;
                }
                if (valueChar == '<')
                {
                    throw blockingError(ProblemCode.NOT_WELL_FORMED,
                            Messages.Parser_lessThanInValue, pos);
                }
                pos++;
            }
            if (pos >= length)
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED,
                        Messages.Parser_unclosedValue, valueOffset - 1);
            }
            final int valueEndOffset = pos;
            final String value = decodeValue(text.subSequence(valueOffset, valueEndOffset), valueOffset);
            pos++; // consume the closing quote
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
                throw blockingError(code, e.getMessage(), valueOffset + e.getOffset());
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
            int runStart = -1;
            boolean runSignificant = false;
            while (true)
            {
                if (pos >= length)
                {
                    throw blockingError(ProblemCode.NOT_WELL_FORMED,
                            NLS.bind(Messages.Parser_endedBeforeEndTag, endTagName), pos);
                }
                final char ch = text.charAt(pos);
                if (ch != '<')
                {
                    // Whitespace does not end a run of text (rule 5): only markup does, so that
                    // "stray text" reports as one problem rather than one per word.
                    if (runStart < 0)
                    {
                        runStart = pos;
                    }
                    if (!isWhitespace(ch))
                    {
                        runSignificant = true;
                    }
                    pos++;
                    continue;
                }
                if (matchesAt(pos, "</"))
                {
                    flushTextRun(runStart, runSignificant, pos);
                    return scanEndTag(endTagName);
                }
                if (matchesAt(pos, "<?"))
                {
                    flushTextRun(runStart, runSignificant, pos);
                    runStart = -1;
                    runSignificant = false;
                    skipProcessingInstruction();
                }
                else if (matchesAt(pos, "<!--"))
                {
                    flushTextRun(runStart, runSignificant, pos);
                    runStart = -1;
                    runSignificant = false;
                    skipComment();
                }
                else if (matchesAt(pos, "<![CDATA["))
                {
                    if (runStart < 0)
                    {
                        runStart = pos;
                    }
                    runSignificant = true;
                    skipCData();
                }
                else if (allowElements)
                {
                    flushTextRun(runStart, runSignificant, pos);
                    runStart = -1;
                    runSignificant = false;
                    scanRowElement();
                }
                else
                {
                    throw blockingError(ProblemCode.NESTED_ELEMENT,
                            Messages.Parser_nestedElement, pos);
                }
            }
        }

        private int scanEndTag(final String expectedName)
        {
            final int endTagStart = pos;
            pos += 2; // consume "</"
            final String name = scanName();
            skipWhitespace();
            if (pos >= length || text.charAt(pos) != '>')
            {
                final String message = NLS.bind(Messages.Parser_unclosedEndTag, name);
                throw blockingError(ProblemCode.NOT_WELL_FORMED, message, pos);
            }
            pos++; // consume '>'
            if (!name.equals(expectedName))
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED,
                        NLS.bind(Messages.Parser_mismatchedEndTag, expectedName, name), endTagStart);
            }
            return endTagStart;
        }

        private void flushTextRun(final int start, final boolean significant, final int end)
        {
            if (start >= 0 && significant)
            {
                addInfoProblem(ProblemCode.TEXT_CONTENT_IGNORED, Messages.Parser_textIgnored, start,
                        end - start);
            }
        }

        private void scanDoctype()
        {
            final int doctypeOffset = pos;
            pos += "<!DOCTYPE".length();
            final int beforeNameWhitespace = pos;
            skipWhitespace();
            if (pos == beforeNameWhitespace || pos >= length
                    || !XmlNames.isNameStartChar(Character.codePointAt(text, pos)))
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED,
                        Messages.Parser_expectedDoctypeName, pos);
            }
            final String rootName = scanName();
            skipWhitespace();
            String publicId = null;
            String systemId = null;
            if (matchesAt(pos, "SYSTEM"))
            {
                pos += "SYSTEM".length();
                skipWhitespace();
                systemId = scanQuotedLiteral();
                skipWhitespace();
            }
            else if (matchesAt(pos, "PUBLIC"))
            {
                pos += "PUBLIC".length();
                skipWhitespace();
                publicId = scanQuotedLiteral();
                skipWhitespace();
                systemId = scanQuotedLiteral();
                skipWhitespace();
            }
            String internalSubset = null;
            if (pos < length && text.charAt(pos) == '[')
            {
                pos++;
                final int subsetStart = pos;
                skipToMatchingCloseBracket();
                internalSubset = text.subSequence(subsetStart, pos).toString();
                pos++; // consume ']'
                skipWhitespace();
            }
            if (pos >= length || text.charAt(pos) != '>')
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED,
                        Messages.Parser_unclosedDoctype, pos);
            }
            pos++; // consume '>'
            doctype = new FlatXmlDoctype(rootName, publicId, systemId, internalSubset, doctypeOffset, pos);
        }

        private String scanQuotedLiteral()
        {
            if (pos >= length || (text.charAt(pos) != '"' && text.charAt(pos) != '\''))
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_expectedQuotedLiteral, pos);
            }
            final char quote = text.charAt(pos);
            pos++;
            final int start = pos;
            while (pos < length && text.charAt(pos) != quote)
            {
                pos++;
            }
            if (pos >= length)
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_unclosedLiteral,
                        start - 1);
            }
            final String value = text.subSequence(start, pos).toString();
            pos++; // consume the closing quote
            return value;
        }

        /**
         * Advances to the ']' that closes an internal subset, skipping quoted literals, comments, and
         * processing instructions so that '>' and ']' inside them do not end it early.
         */
        private void skipToMatchingCloseBracket()
        {
            while (pos < length && text.charAt(pos) != ']')
            {
                final char ch = text.charAt(pos);
                if (ch == '"' || ch == '\'')
                {
                    pos++;
                    while (pos < length && text.charAt(pos) != ch)
                    {
                        pos++;
                    }
                    if (pos >= length)
                    {
                        throw blockingError(ProblemCode.NOT_WELL_FORMED,
                                Messages.Parser_unclosedSubsetLiteral, pos);
                    }
                    pos++; // consume the closing quote
                }
                else if (matchesAt(pos, "<!--"))
                {
                    skipComment();
                }
                else if (matchesAt(pos, "<?"))
                {
                    skipProcessingInstruction();
                }
                else
                {
                    pos++;
                }
            }
            if (pos >= length)
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_unclosedSubset,
                        pos);
            }
        }

        private String scanName()
        {
            final int start = pos;
            if (pos >= length || !XmlNames.isNameStartChar(Character.codePointAt(text, pos)))
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_expectedName, pos);
            }
            pos += Character.charCount(Character.codePointAt(text, pos));
            while (pos < length)
            {
                final int codePoint = Character.codePointAt(text, pos);
                if (!XmlNames.isNameChar(codePoint))
                {
                    break;
                }
                pos += Character.charCount(codePoint);
            }
            final String name = text.subSequence(start, pos).toString();
            final String sharedName = names.putIfAbsent(name, name);
            return sharedName != null ? sharedName : name;
        }

        private void skipWhitespace()
        {
            while (pos < length && isWhitespace(text.charAt(pos)))
            {
                pos++;
            }
        }

        private static boolean isWhitespace(final char ch)
        {
            return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r';
        }

        private boolean matchesAt(final int position, final String literal)
        {
            if (position + literal.length() > length)
            {
                return false;
            }
            for (int i = 0; i < literal.length(); i++)
            {
                if (text.charAt(position + i) != literal.charAt(i))
                {
                    return false;
                }
            }
            return true;
        }

        private void skipProcessingInstruction()
        {
            final int start = pos;
            pos += 2; // "<?"
            while (pos < length && !matchesAt(pos, "?>"))
            {
                pos++;
            }
            if (pos >= length)
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED,
                        Messages.Parser_unclosedProcessingInstruction, start);
            }
            pos += 2;
        }

        private void skipComment()
        {
            final int start = pos;
            pos += 4; // "<!--"
            while (pos < length && !matchesAt(pos, "-->"))
            {
                pos++;
            }
            if (pos >= length)
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_unclosedComment,
                        start);
            }
            pos += 3;
        }

        private void skipCData()
        {
            final int start = pos;
            pos += "<![CDATA[".length();
            while (pos < length && !matchesAt(pos, "]]>"))
            {
                pos++;
            }
            if (pos >= length)
            {
                throw blockingError(ProblemCode.NOT_WELL_FORMED,
                        Messages.Parser_unclosedCdata, start);
            }
            pos += 3;
        }

        private void addInfoProblem(final ProblemCode code, final String message, final int offset,
                final int problemLength)
        {
            problems.add(new DatasetProblem(code, ProblemSeverity.INFO, message, null, null, -1, offset,
                    problemLength));
        }

        private StopScanException blockingError(final ProblemCode code, final String message,
                final int offset)
        {
            problems.add(new DatasetProblem(code, ProblemSeverity.ERROR, withLocation(message, offset),
                    null, null, -1, offset, 0));
            wellFormed = false;
            return STOP_SCAN;
        }

        private String withLocation(final String message, final int offset)
        {
            int line = 1;
            int column = 1;
            int index = 0;
            final int end = Math.min(offset, length);
            while (index < end)
            {
                final char ch = text.charAt(index);
                if (ch == '\n')
                {
                    line++;
                    column = 1;
                    index++;
                }
                else if (ch == '\r')
                {
                    line++;
                    column = 1;
                    index++;
                    if (index < end && text.charAt(index) == '\n')
                    {
                        index++;
                    }
                }
                else
                {
                    column++;
                    index++;
                }
            }
            return NLS.bind(Messages.Parser_position, new Object[] { message, line, column });
        }
    }

    /**
     * The parsed head of an element: its name and attributes, before the caller decides what they mean.
     */
    private record StartTag(String name, int nameEndOffset, List<FlatXmlAttribute> attributes,
            int attributesEndOffset, int startTagEndOffset, boolean selfClosing)
    {
    }

    /**
     * Thrown internally to unwind the scan as soon as a blocking problem is recorded; carries no state of
     * its own.
     */
    private static final class StopScanException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private StopScanException()
        {
            super(null, null, false, false);
        }
    }
}
