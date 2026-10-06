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

import java.util.HashMap;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.eclipse.osgi.util.NLS;

/**
 * A cursor over flat XML text with the character-level operations of the scanner: testing what comes next,
 * skipping whitespace, comments, processing instructions, and CDATA sections, whose characters must be XML
 * 1.0 characters, and scanning names, quoted literals, and quoted attribute values. Whenever the text is
 * malformed, it records a blocking problem through the scan's {@link ScanProblems} and throws the exception
 * that stops the scan. Each instance holds the position of a single scan.
 */
final class XmlLexer
{
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private final CharSequence text;

    private final int length;

    private final ScanProblems problems;

    /**
     * Each distinct element and attribute name, so that the elements share one string per name
     * instead of holding one per occurrence.
     */
    private final Map<String, String> names = new HashMap<>();

    private int pos;

    XmlLexer(final CharSequence text, final ScanProblems problems)
    {
        this.text = text;
        this.length = text.length();
        this.problems = problems;
    }

    /**
     * Returns the offset of the next character to read.
     *
     * @return The offset, from 0 up to the length of the text.
     */
    int position()
    {
        return pos;
    }

    /**
     * Moves the position forward.
     *
     * @param count The number of characters to move over.
     */
    void advance(final int count)
    {
        pos += count;
    }

    /**
     * Tells whether the whole text has been read.
     *
     * @return True when no character is left.
     */
    boolean atEnd()
    {
        return pos >= length;
    }

    /**
     * Returns the character at the position, which must not be the end of the text.
     *
     * @return The next character to read.
     */
    char currentCharacter()
    {
        return text.charAt(pos);
    }

    /**
     * Tells whether the text continues with the expected character.
     *
     * @param expected The character to look for at the position.
     * @return True when the text is not at its end and its next character is the expected one.
     */
    boolean atCharacter(final char expected)
    {
        return pos < length && text.charAt(pos) == expected;
    }

    /**
     * Tells whether the text continues with the given literal.
     *
     * @param literal The text to look for at the position.
     * @return True when the literal starts at the position.
     */
    boolean atText(final String literal)
    {
        return matchesAt(pos, literal);
    }

    /**
     * Tells whether a name can start at the position.
     *
     * @return True when the text is not at its end and its next character can start a name.
     */
    boolean atNameStart()
    {
        return pos < length && XmlNames.isNameStartChar(Character.codePointAt(text, pos));
    }

    /**
     * Returns a part of the text.
     *
     * @param start The offset of the first character.
     * @param end The offset just after the last character.
     * @return The characters from start up to end.
     */
    CharSequence textBetween(final int start, final int end)
    {
        return text.subSequence(start, end);
    }

    boolean isElementStart()
    {
        return text.charAt(pos) == '<' && pos + 1 < length
                && XmlNames.isNameStartChar(Character.codePointAt(text, pos + 1));
    }

    void skipBom()
    {
        if (length > 0 && text.charAt(0) == BYTE_ORDER_MARK)
        {
            pos = 1;
        }
    }

    void skipWhitespace()
    {
        while (pos < length && XmlNames.isWhitespace(text.charAt(pos)))
        {
            pos++;
        }
    }

    /**
     * Moves over a processing instruction, whose target must be a name that whitespace or the end of the
     * instruction follows. A target that spells xml in any letter case is reserved for the XML declaration,
     * which only the very start of the text may hold.
     */
    void skipProcessingInstruction()
    {
        final int start = pos;
        pos += 2; // "<?"
        skipProcessingInstructionTarget(start);
        while (pos < length && !matchesAt(pos, "?>"))
        {
            skipXmlCharacter();
        }
        if (pos >= length)
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                    Messages.Parser_unclosedProcessingInstruction, start);
        }
        pos += 2;
    }

    /**
     * Moves over a comment, which may not contain two hyphens in a row, so it cannot end with three.
     */
    void skipComment()
    {
        final int start = pos;
        pos += 4; // "<!--"
        while (pos < length && !matchesAt(pos, "-->"))
        {
            if (matchesAt(pos, "--"))
            {
                throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                        Messages.Parser_doubleHyphenInComment, pos);
            }
            skipXmlCharacter();
        }
        if (pos >= length)
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_unclosedComment,
                    start);
        }
        pos += 3;
    }

    private void skipProcessingInstructionTarget(final int instructionStart)
    {
        if (!atNameStart())
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                    Messages.Parser_expectedProcessingInstructionTarget, pos);
        }
        final int targetStart = pos;
        final String target = scanName();
        if (isReservedTarget(target, instructionStart))
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                    NLS.bind(Messages.Parser_reservedProcessingInstructionTarget, target), targetStart);
        }
        if (!atTargetEnd())
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                    Messages.Parser_expectedSpaceAfterProcessingInstructionTarget, pos);
        }
    }

    /**
     * Tells whether a processing instruction's target is reserved: every spelling of xml is, except the
     * lowercase one of the XML declaration at the start of the text.
     */
    private boolean isReservedTarget(final String target, final int instructionStart)
    {
        final boolean isDeclaration = "xml".equals(target) && startsText(instructionStart);
        return "xml".equalsIgnoreCase(target) && !isDeclaration;
    }

    /**
     * Tells whether an offset is where the text starts, which a byte order mark does not change.
     */
    private boolean startsText(final int offset)
    {
        return offset == 0 || (offset == 1 && text.charAt(0) == BYTE_ORDER_MARK);
    }

    private boolean atTargetEnd()
    {
        return pos >= length || XmlNames.isWhitespace(text.charAt(pos)) || matchesAt(pos, "?>");
    }

    void skipCData()
    {
        final int start = pos;
        pos += "<![CDATA[".length();
        while (pos < length && !matchesAt(pos, "]]>"))
        {
            skipXmlCharacter();
        }
        if (pos >= length)
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                    Messages.Parser_unclosedCdata, start);
        }
        pos += 3;
    }

    /**
     * Moves over the character at the position, which must not be the end of the text. A surrogate pair is
     * one character. A character that XML 1.0 does not allow records a blocking problem at its offset.
     */
    void skipXmlCharacter()
    {
        final int codePoint = Character.codePointAt(text, pos);
        if (!AttributeValueCodec.isXmlChar(codePoint))
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                    AttributeValueCodec.notXmlCharacterMessage(codePoint), pos);
        }
        pos += Character.charCount(codePoint);
    }

    String scanName()
    {
        final int start = pos;
        if (pos >= length || !XmlNames.isNameStartChar(Character.codePointAt(text, pos)))
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_expectedName, pos);
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

    String scanQuotedLiteral()
    {
        final char quote = scanOpeningQuote(Messages.Parser_expectedQuotedLiteral);
        final int start = pos;
        skipPastClosingQuote(quote, Messages.Parser_unclosedLiteral);
        return text.subSequence(start, pos - 1).toString();
    }

    /**
     * Moves over the quoted literal that starts at the position, which must be at its opening quote. A
     * literal that has no closing quote records the blocking problem that the message describes, at its
     * opening quote.
     */
    void skipQuotedLiteral(final String unclosedMessage)
    {
        final char quote = text.charAt(pos);
        pos++;
        skipPastClosingQuote(quote, unclosedMessage);
    }

    /**
     * Moves over the rest of a quoted literal, past its closing quote, from the position just after its
     * opening quote.
     */
    private void skipPastClosingQuote(final char quote, final String unclosedMessage)
    {
        final int openingQuoteOffset = pos - 1;
        while (pos < length && text.charAt(pos) != quote)
        {
            pos++;
        }
        if (pos >= length)
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, unclosedMessage, openingQuoteOffset);
        }
        pos++; // consume the closing quote
    }

    /**
     * Consumes the expected character, or records the blocking problem that the message describes.
     */
    void expectCharacter(final char expected, final String message)
    {
        if (pos >= length || text.charAt(pos) != expected)
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, message, pos);
        }
        pos++;
    }

    /**
     * Consumes the quote that opens a quoted value, or records the blocking problem that the message
     * describes.
     *
     * @return The quote, which also closes the value.
     */
    char scanOpeningQuote(final String message)
    {
        final boolean atQuote = pos < length && (text.charAt(pos) == '"' || text.charAt(pos) == '\'');
        if (!atQuote)
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, message, pos);
        }
        final char quote = text.charAt(pos);
        pos++;
        return quote;
    }

    /**
     * Advances to the quote that closes an attribute value, which may not contain a {@code <}.
     *
     * @return The offset of the closing quote.
     */
    int scanToClosingQuote(final char quote, final int valueOffset)
    {
        while (pos < length && text.charAt(pos) != quote)
        {
            if (text.charAt(pos) == '<')
            {
                throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_lessThanInValue,
                        pos);
            }
            pos++;
        }
        if (pos >= length)
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_unclosedValue,
                    valueOffset - 1);
        }
        return pos;
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
}
