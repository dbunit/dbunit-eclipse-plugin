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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.flatxml.XmlNames;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;

/**
 * Reads the {@code ELEMENT} and {@code ATTLIST} declarations of a DTD, serving both external DTD files
 * and DOCTYPE internal subsets. DTD text is not edited, so, unlike {@code FlatXmlParser}, this reader
 * does not need to track offsets for rewriting.
 *
 * @since 1.0.0
 */
public final class DtdReader
{
    private DtdReader()
    {
    }

    /**
     * Reads a DTD's declarations.
     *
     * @param dtdText The DTD text: an external DTD file's content, or a DOCTYPE's internal subset.
     * @return The declarations found.
     */
    public static DtdDeclarations read(final String dtdText)
    {
        return new Scanner(dtdText).scan();
    }

    /**
     * Holds the mutable state of one read. A fresh instance is used for each call to {@link #read}.
     */
    private static final class Scanner
    {
        private final String dtdText;

        private final int length;

        private final Map<String, List<String>> elements = new LinkedHashMap<>();

        private final List<DatasetProblem> problems = new ArrayList<>();

        private int pos;

        private boolean contentModelDeclared;

        private boolean contentModelAny;

        private List<String> contentModelNames = List.of();

        private Scanner(final String dtdText)
        {
            this.dtdText = dtdText;
            this.length = dtdText.length();
        }

        private DtdDeclarations scan()
        {
            while (true)
            {
                skipWhitespace();
                if (pos >= length)
                {
                    break;
                }
                if (matchesAt(pos, "<!--"))
                {
                    skipComment();
                }
                else if (matchesAt(pos, "<?"))
                {
                    skipProcessingInstruction();
                }
                else if (matchesAt(pos, "<!ELEMENT"))
                {
                    scanElementDeclaration();
                }
                else if (matchesAt(pos, "<!ATTLIST"))
                {
                    scanAttlistDeclaration();
                }
                else if (matchesAt(pos, "<!ENTITY"))
                {
                    scanIgnoredDeclaration("Parameter entity declarations are ignored.");
                }
                else if (matchesAt(pos, "<!NOTATION"))
                {
                    scanIgnoredDeclaration(null);
                }
                else if (matchesAt(pos, "<!["))
                {
                    scanConditionalSection();
                }
                else if (dtdText.charAt(pos) == '%')
                {
                    scanParameterEntityReference();
                }
                else
                {
                    // Unexpected content; advance so a malformed DTD cannot loop forever. DTD files are
                    // not edited, so reporting a precise syntax error here is not worth the complexity.
                    pos++;
                }
            }
            return new DtdDeclarations(contentModelDeclared, contentModelAny, contentModelNames, elements,
                    problems);
        }

        private void scanElementDeclaration()
        {
            pos += "<!ELEMENT".length();
            skipWhitespace();
            final String name = scanName();
            skipWhitespace();
            final String contentSpec =
                    pos < length && dtdText.charAt(pos) == '(' ? scanParenthesizedContentSpec()
                            : scanBareWord();
            skipWhitespace();
            if (pos < length && dtdText.charAt(pos) == '>')
            {
                pos++;
            }
            if ("dataset".equals(name))
            {
                contentModelDeclared = true;
                contentModelAny = "ANY".equals(contentSpec);
                contentModelNames =
                        contentModelAny ? List.of() : extractContentModelNames(contentSpec);
            }
            else if (!name.isEmpty())
            {
                elements.computeIfAbsent(name, key -> new ArrayList<>());
            }
        }

        private void scanAttlistDeclaration()
        {
            pos += "<!ATTLIST".length();
            skipWhitespace();
            final String elementName = scanName();
            if (!elementName.isEmpty())
            {
                elements.computeIfAbsent(elementName, key -> new ArrayList<>());
            }
            while (true)
            {
                skipWhitespace();
                if (pos >= length || dtdText.charAt(pos) == '>')
                {
                    break;
                }
                final String attributeName = scanName();
                if (attributeName.isEmpty())
                {
                    break;
                }
                skipWhitespace();
                scanAttributeType();
                skipWhitespace();
                scanAttributeDefault();
                if (!elementName.isEmpty())
                {
                    final List<String> columns = elements.get(elementName);
                    if (!columns.contains(attributeName))
                    {
                        columns.add(attributeName);
                    }
                }
            }
            if (pos < length && dtdText.charAt(pos) == '>')
            {
                pos++;
            }
        }

        private void scanAttributeType()
        {
            if (pos < length && dtdText.charAt(pos) == '(')
            {
                skipParenthesizedList();
                return;
            }
            final String word = scanBareWord();
            if ("NOTATION".equals(word))
            {
                skipWhitespace();
                if (pos < length && dtdText.charAt(pos) == '(')
                {
                    skipParenthesizedList();
                }
            }
        }

        private void scanAttributeDefault()
        {
            if (pos < length && dtdText.charAt(pos) == '#')
            {
                final String word = scanBareWord();
                if ("#FIXED".equals(word))
                {
                    skipWhitespace();
                    if (pos < length && isQuote(dtdText.charAt(pos)))
                    {
                        scanQuotedLiteral();
                    }
                }
            }
            else if (pos < length && isQuote(dtdText.charAt(pos)))
            {
                scanQuotedLiteral();
            }
        }

        private void scanIgnoredDeclaration(final String message)
        {
            final int start = pos;
            skipToMatchingCloseAngleBracket();
            if (message != null)
            {
                addInfoProblem(message, start, pos - start);
            }
        }

        private void scanConditionalSection()
        {
            final int start = pos;
            pos += "<![".length();
            while (pos < length && !matchesAt(pos, "]]>"))
            {
                pos++;
            }
            if (pos < length)
            {
                pos += "]]>".length();
            }
            addInfoProblem("Conditional sections are ignored.", start, pos - start);
        }

        private void scanParameterEntityReference()
        {
            final int start = pos;
            pos++; // consume '%'
            scanName();
            if (pos < length && dtdText.charAt(pos) == ';')
            {
                pos++;
            }
            addInfoProblem("Parameter entity references are ignored.", start, pos - start);
        }

        private void skipToMatchingCloseAngleBracket()
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

        private void skipParenthesizedList()
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

        private void scanQuotedLiteral()
        {
            final char quote = dtdText.charAt(pos);
            pos++;
            while (pos < length && dtdText.charAt(pos) != quote)
            {
                pos++;
            }
            if (pos < length)
            {
                pos++; // consume the closing quote
            }
        }

        private String scanParenthesizedContentSpec()
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

        private String scanName()
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

        private String scanBareWord()
        {
            final int start = pos;
            while (pos < length && !Character.isWhitespace(dtdText.charAt(pos))
                    && dtdText.charAt(pos) != '>')
            {
                pos++;
            }
            return dtdText.substring(start, pos);
        }

        private void skipWhitespace()
        {
            while (pos < length && Character.isWhitespace(dtdText.charAt(pos)))
            {
                pos++;
            }
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

        private void skipComment()
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

        private void skipProcessingInstruction()
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

        private static boolean isQuote(final char ch)
        {
            return ch == '"' || ch == '\'';
        }

        private static boolean isQuantifier(final char ch)
        {
            return ch == '*' || ch == '?' || ch == '+';
        }

        private static List<String> extractContentModelNames(final String contentSpec)
        {
            final List<String> names = new ArrayList<>();
            final StringBuilder token = new StringBuilder();
            for (int i = 0; i < contentSpec.length(); i++)
            {
                final char ch = contentSpec.charAt(i);
                if (isStructuralChar(ch) || Character.isWhitespace(ch))
                {
                    addToken(names, token);
                }
                else
                {
                    token.append(ch);
                }
            }
            addToken(names, token);
            return names;
        }

        private static boolean isStructuralChar(final char ch)
        {
            return ch == '(' || ch == ')' || ch == '*' || ch == '?' || ch == '+' || ch == ',' || ch == '|';
        }

        private static void addToken(final List<String> names, final StringBuilder token)
        {
            if (token.length() > 0)
            {
                final String value = token.toString();
                if (!"#PCDATA".equals(value))
                {
                    names.add(value);
                }
                token.setLength(0);
            }
        }

        private void addInfoProblem(final String message, final int offset, final int problemLength)
        {
            problems.add(new DatasetProblem(ProblemCode.UNSUPPORTED_DTD_CONSTRUCT, ProblemSeverity.INFO,
                    message, null, null, -1, offset, problemLength));
        }
    }
}
