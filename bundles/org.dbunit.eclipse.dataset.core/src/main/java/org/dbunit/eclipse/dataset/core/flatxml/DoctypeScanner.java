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

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;

/**
 * Scans the DOCTYPE declaration of flat XML text: its root name, its SYSTEM or PUBLIC identifiers, and the
 * offset and raw text of its internal subset. Malformed text records a blocking problem through the scan's
 * {@link ScanProblems} and stops the scan, as everywhere else in the scanner.
 */
final class DoctypeScanner
{
    private final XmlLexer lexer;

    private final ScanProblems problems;

    DoctypeScanner(final XmlLexer lexer, final ScanProblems problems)
    {
        this.lexer = lexer;
        this.problems = problems;
    }

    /**
     * Scans the DOCTYPE declaration that starts at the lexer's position.
     *
     * @return The declaration, with the offsets of its start, its internal subset, and its end.
     */
    FlatXmlDoctype scan()
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
            skipWhitespaceBeforeLiteral(Messages.Parser_expectedWhitespaceAfterIdKeyword);
            systemId = lexer.scanQuotedLiteral();
            lexer.skipWhitespace();
        }
        else if (lexer.atText("PUBLIC"))
        {
            lexer.advance("PUBLIC".length());
            skipWhitespaceBeforeLiteral(Messages.Parser_expectedWhitespaceAfterIdKeyword);
            publicId = lexer.scanQuotedLiteral();
            skipWhitespaceBeforeLiteral(Messages.Parser_expectedWhitespaceBetweenIds);
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
        return new FlatXmlDoctype(rootName, publicId, systemId, internalSubset, internalSubsetOffset,
                doctypeOffset, doctypeEndOffset);
    }

    /**
     * Skips the whitespace that must come before a quoted literal, and records a blocking problem when a
     * quote follows with none. Anything else that follows is for the scan of the literal to refuse, with
     * the message that says what is missing.
     *
     * @param message The message of the problem.
     */
    private void skipWhitespaceBeforeLiteral(final String message)
    {
        final int before = lexer.position();
        lexer.skipWhitespace();
        final boolean quoteFollows = lexer.atCharacter('"') || lexer.atCharacter('\'');
        if (lexer.position() == before && quoteFollows)
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, message, lexer.position());
        }
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
