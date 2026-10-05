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
package org.dbunit.eclipse.dataset.core.dtd;

import org.dbunit.eclipse.dataset.core.flatxml.XmlNames;

/**
 * A cursor over DTD text with the character-level operations {@link DtdReader} needs: testing what comes
 * next, skipping whitespace, comments, and processing instructions, and scanning names, bare words, and
 * quoted literals. It knows nothing of what a declaration means and never fails: it stops at the end of
 * the text. Each instance holds the position of one read.
 */
final class DtdLexer
{
    private final String dtdText;

    private final int length;

    private int pos;

    DtdLexer(final String dtdText)
    {
        this.dtdText = dtdText;
        this.length = dtdText.length();
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

    boolean atCharacter(final char expected)
    {
        return pos < length && dtdText.charAt(pos) == expected;
    }

    boolean atDeclarationEnd()
    {
        return pos >= length || dtdText.charAt(pos) == '>';
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

    void skipWhitespace()
    {
        while (pos < length && Character.isWhitespace(dtdText.charAt(pos)))
        {
            pos++;
        }
    }

    void skipCharacter(final char expected)
    {
        if (atCharacter(expected))
        {
            pos++;
        }
    }

    void skipComment()
    {
        pos += "<!--".length();
        while (pos < length && !matchesAt(pos, "-->"))
        {
            pos++;
        }
        if (pos < length)
        {
            pos += "-->".length();
        }
    }

    void skipProcessingInstruction()
    {
        pos += "<?".length();
        while (pos < length && !matchesAt(pos, "?>"))
        {
            pos++;
        }
        if (pos < length)
        {
            pos += "?>".length();
        }
    }

    void skipConditionalSection()
    {
        pos += "<![".length();
        while (pos < length && !matchesAt(pos, "]]>"))
        {
            pos++;
        }
        if (pos < length)
        {
            pos += "]]>".length();
        }
    }

    void skipParameterEntityReference()
    {
        pos++; // consume '%'
        scanName();
        if (pos < length && dtdText.charAt(pos) == ';')
        {
            pos++;
        }
    }

    void skipToMatchingCloseAngleBracket()
    {
        while (pos < length && dtdText.charAt(pos) != '>')
        {
            final char ch = dtdText.charAt(pos);
            if (isQuote(ch))
            {
                pos++;
                while (pos < length && dtdText.charAt(pos) != ch)
                {
                    pos++;
                }
            }
            pos++;
        }
        if (pos < length)
        {
            pos++; // consume '>'
        }
    }

    void skipParenthesizedList()
    {
        while (pos < length && dtdText.charAt(pos) != ')')
        {
            pos++;
        }
        if (pos < length)
        {
            pos++; // consume ')'
        }
    }

    /**
     * Scans a quoted literal when the text continues with a quote.
     *
     * @return The literal's text between the quotes, or null when the text does not continue with a quote.
     */
    String scanQuotedLiteralIfPresent()
    {
        if (pos < length && isQuote(dtdText.charAt(pos)))
        {
            return scanQuotedLiteral();
        }
        return null;
    }

    /**
     * Scans the quoted literal the text continues with.
     *
     * @return The literal's text between the quotes; a literal that is never closed ends at the end of the
     *         text.
     */
    String scanQuotedLiteral()
    {
        final char quote = dtdText.charAt(pos);
        pos++;
        final int start = pos;
        while (pos < length && dtdText.charAt(pos) != quote)
        {
            pos++;
        }
        final String literal = dtdText.substring(start, pos);
        if (pos < length)
        {
            pos++; // consume the closing quote
        }
        return literal;
    }

    String scanParenthesizedContentSpec()
    {
        final int start = pos;
        int depth = 0;
        while (pos < length)
        {
            final char ch = dtdText.charAt(pos);
            pos++;
            if (ch == '(')
            {
                depth++;
            }
            else if (ch == ')')
            {
                depth--;
                if (depth == 0)
                {
                    break;
                }
            }
        }
        if (pos < length && isQuantifier(dtdText.charAt(pos)))
        {
            pos++;
        }
        return dtdText.substring(start, pos);
    }

    String scanName()
    {
        final int start = pos;
        if (pos < length && XmlNames.isNameStartChar(Character.codePointAt(dtdText, pos)))
        {
            pos += Character.charCount(Character.codePointAt(dtdText, pos));
            while (pos < length && XmlNames.isNameChar(Character.codePointAt(dtdText, pos)))
            {
                pos += Character.charCount(Character.codePointAt(dtdText, pos));
            }
        }
        return dtdText.substring(start, pos);
    }

    String scanBareWord()
    {
        final int start = pos;
        while (pos < length && !Character.isWhitespace(dtdText.charAt(pos))
                && dtdText.charAt(pos) != '>')
        {
            pos++;
        }
        return dtdText.substring(start, pos);
    }

    private boolean matchesAt(final int position, final String literal)
    {
        if (position + literal.length() > length)
        {
            return false;
        }
        for (int i = 0; i < literal.length(); i++)
        {
            if (dtdText.charAt(position + i) != literal.charAt(i))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isQuote(final char ch)
    {
        return ch == '"' || ch == '\'';
    }

    private static boolean isQuantifier(final char ch)
    {
        return ch == '*' || ch == '?' || ch == '+';
    }
}
