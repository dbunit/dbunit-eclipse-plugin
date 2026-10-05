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
import org.eclipse.osgi.util.NLS;

/**
 * Scans the text between the markup of an element against the rules that XML 1.0 sets for character data. The
 * editor ignores such text, as dbUnit does, but the XML parser that dbUnit uses fails to load a dataset whose
 * text breaks a rule: every character must be an XML character, an {@code &} must start a well-formed
 * reference, and {@code ]]>} may only end a CDATA section. Malformed text records a blocking problem through
 * the scan's {@link ScanProblems} and stops the scan, as everywhere else in the scanner.
 */
final class TextContentScanner
{
    private static final int DECIMAL = 10;

    private static final int HEXADECIMAL = 16;

    private final XmlLexer lexer;

    private final ScanProblems problems;

    TextContentScanner(final XmlLexer lexer, final ScanProblems problems)
    {
        this.lexer = lexer;
        this.problems = problems;
    }

    /**
     * Skips the text that starts at the lexer's position and ends at the next {@code <} or at the end of the
     * text.
     *
     * @param entitiesMayBeDeclared False when no DTD can declare an entity, which makes a reference to any
     *                              entity other than the five predefined ones an error; true when a DTD may
     *                              declare it, which makes such a reference acceptable.
     * @return True when the text holds more than whitespace.
     */
    boolean skip(final boolean entitiesMayBeDeclared)
    {
        boolean significant = false;
        lexer.skipWhitespace();
        while (!lexer.atEnd() && !lexer.atCharacter('<'))
        {
            significant = true;
            skipCharacter(entitiesMayBeDeclared);
            lexer.skipWhitespace();
        }
        return significant;
    }

    /**
     * Skips the character or reference at the current position, which is not whitespace.
     */
    private void skipCharacter(final boolean entitiesMayBeDeclared)
    {
        final char ch = lexer.currentCharacter();
        if (ch == '&')
        {
            skipReference(entitiesMayBeDeclared);
        }
        else if (ch == ']' && lexer.atText("]]>"))
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Parser_cdataEndInText,
                    lexer.position());
        }
        else
        {
            lexer.skipXmlCharacter();
        }
    }

    private void skipReference(final boolean entitiesMayBeDeclared)
    {
        final int ampersandOffset = lexer.position();
        lexer.advance(1);
        if (lexer.atCharacter('#'))
        {
            lexer.advance(1);
            skipCharacterReference(ampersandOffset);
        }
        else
        {
            skipEntityReference(ampersandOffset, entitiesMayBeDeclared);
        }
    }

    /**
     * Skips what follows {@code &#} in a character reference, which must name an XML character.
     */
    private void skipCharacterReference(final int ampersandOffset)
    {
        final int radix = skipRadixMarker();
        final int digitsOffset = lexer.position();
        while (!lexer.atEnd() && Character.isLetterOrDigit(lexer.currentCharacter()))
        {
            lexer.advance(1);
        }
        final String digits = lexer.textBetween(digitsOffset, lexer.position()).toString();
        skipSemicolon(ampersandOffset);
        try
        {
            AttributeValueCodec.parseCharacterReference(digits, radix, ampersandOffset);
        }
        catch (final AttributeValueException e)
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED, e.getMessage(), e.getOffset(), e);
        }
    }

    private int skipRadixMarker()
    {
        int radix = DECIMAL;
        if (lexer.atCharacter('x'))
        {
            lexer.advance(1);
            radix = HEXADECIMAL;
        }
        return radix;
    }

    /**
     * Skips what follows {@code &} in an entity reference.
     */
    private void skipEntityReference(final int ampersandOffset, final boolean entitiesMayBeDeclared)
    {
        if (!lexer.atNameStart())
        {
            throw invalidReference(ampersandOffset);
        }
        final String name = lexer.scanName();
        skipSemicolon(ampersandOffset);
        final boolean undeclared = !entitiesMayBeDeclared && !AttributeValueCodec.isPredefinedEntity(name);
        if (undeclared)
        {
            throw problems.blockingError(ProblemCode.NOT_WELL_FORMED,
                    NLS.bind(Messages.Parser_entityNotDeclared, name), ampersandOffset);
        }
    }

    private void skipSemicolon(final int ampersandOffset)
    {
        if (!lexer.atCharacter(';'))
        {
            throw invalidReference(ampersandOffset);
        }
        lexer.advance(1);
    }

    private StopScanException invalidReference(final int ampersandOffset)
    {
        return problems.blockingError(ProblemCode.NOT_WELL_FORMED, Messages.Codec_invalidAmpersand,
                ampersandOffset);
    }
}
