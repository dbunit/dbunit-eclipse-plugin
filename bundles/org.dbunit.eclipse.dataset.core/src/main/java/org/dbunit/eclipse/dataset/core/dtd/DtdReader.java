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

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.flatxml.AttributeValueCodec;
import org.dbunit.eclipse.dataset.core.flatxml.AttributeValueException;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.osgi.util.NLS;

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
        final DtdLexer lexer = new DtdLexer(dtdText);
        return new Scanner(lexer).scan();
    }

    /**
     * Holds the mutable state of one read. A fresh instance is used for each call to {@link #read}.
     */
    private static final class Scanner
    {
        private final DtdLexer lexer;

        private final Map<String, List<String>> elements = new LinkedHashMap<>();

        private final Map<String, Map<String, String>> attributeDefaults = new LinkedHashMap<>();

        private final List<DatasetProblem> problems = new ArrayList<>();

        private boolean contentModelDeclared;

        private boolean contentModelAny;

        private List<String> contentModelNames = List.of();

        private Scanner(final DtdLexer lexer)
        {
            this.lexer = lexer;
        }

        private DtdDeclarations scan()
        {
            while (true)
            {
                lexer.skipWhitespace();
                if (lexer.atEnd())
                {
                    break;
                }
                if (lexer.atText("<!"))
                {
                    scanMarkupDeclaration();
                }
                else if (lexer.atText("<?"))
                {
                    lexer.skipProcessingInstruction();
                }
                else if (lexer.atCharacter('%'))
                {
                    scanParameterEntityReference();
                }
                else
                {
                    skipUnexpectedContent();
                }
            }
            return new DtdDeclarations(contentModelDeclared, contentModelAny, contentModelNames, elements,
                    attributeDefaults, problems);
        }

        private void scanMarkupDeclaration()
        {
            if (lexer.atText("<!--"))
            {
                lexer.skipComment();
            }
            else if (lexer.atText("<!ELEMENT"))
            {
                scanElementDeclaration();
            }
            else if (lexer.atText("<!ATTLIST"))
            {
                scanAttlistDeclaration();
            }
            else if (lexer.atText("<!ENTITY"))
            {
                scanIgnoredDeclaration(Messages.Dtd_parameterEntityDeclarationsIgnored);
            }
            else if (lexer.atText("<!NOTATION"))
            {
                scanIgnoredDeclaration(null);
            }
            else if (lexer.atText("<!["))
            {
                scanConditionalSection();
            }
            else
            {
                skipUnexpectedContent();
            }
        }

        /**
         * Advances past one character of unexpected content, so that a malformed DTD cannot loop forever. DTD
         * files are not edited, so reporting a precise syntax error here is not worth the complexity.
         */
        private void skipUnexpectedContent()
        {
            lexer.advance(1);
        }

        private void scanElementDeclaration()
        {
            lexer.advance("<!ELEMENT".length());
            lexer.skipWhitespace();
            final String name = lexer.scanName();
            lexer.skipWhitespace();
            final String contentSpec = scanContentSpec();
            lexer.skipWhitespace();
            lexer.skipCharacter('>');
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

        private String scanContentSpec()
        {
            return lexer.atCharacter('(') ? lexer.scanParenthesizedContentSpec() : lexer.scanBareWord();
        }

        private void scanAttlistDeclaration()
        {
            lexer.advance("<!ATTLIST".length());
            lexer.skipWhitespace();
            final String elementName = lexer.scanName();
            final boolean collectsColumns = !elementName.isEmpty() && !"dataset".equals(elementName);
            if (collectsColumns)
            {
                elements.computeIfAbsent(elementName, key -> new ArrayList<>());
            }
            while (true)
            {
                lexer.skipWhitespace();
                if (lexer.atDeclarationEnd())
                {
                    break;
                }
                final String attributeName = lexer.scanName();
                if (attributeName.isEmpty())
                {
                    break;
                }
                lexer.skipWhitespace();
                scanAttributeType();
                lexer.skipWhitespace();
                final String defaultValue = scanAttributeDefault(elementName, attributeName);
                if (collectsColumns)
                {
                    addColumn(elementName, attributeName, defaultValue);
                }
            }
            lexer.skipCharacter('>');
        }

        /**
         * Adds a column to an element, with its default value when it has one. The first declaration of an
         * attribute is binding, as in XML, so a later declaration of the same attribute adds neither a
         * column nor a default.
         */
        private void addColumn(final String elementName, final String attributeName,
                final String defaultValue)
        {
            final List<String> columns = elements.get(elementName);
            if (columns.contains(attributeName))
            {
                return;
            }
            columns.add(attributeName);
            if (defaultValue != null)
            {
                final Map<String, String> defaults =
                        attributeDefaults.computeIfAbsent(elementName, key -> new LinkedHashMap<>());
                defaults.put(attributeName, defaultValue);
            }
        }

        private void scanAttributeType()
        {
            if (lexer.atCharacter('('))
            {
                lexer.skipParenthesizedList();
                return;
            }
            final String word = lexer.scanBareWord();
            if ("NOTATION".equals(word))
            {
                lexer.skipWhitespace();
                if (lexer.atCharacter('('))
                {
                    lexer.skipParenthesizedList();
                }
            }
        }

        /**
         * Scans an attribute's default declaration: {@code #REQUIRED}, {@code #IMPLIED}, {@code #FIXED}
         * with a value, or just a value.
         *
         * @return The default or fixed value, decoded the way an XML parser decodes an attribute value, or
         *         null when the attribute has none or its value cannot be decoded.
         */
        private String scanAttributeDefault(final String elementName, final String attributeName)
        {
            if (lexer.atCharacter('#'))
            {
                final String word = lexer.scanBareWord();
                if ("#FIXED".equals(word))
                {
                    lexer.skipWhitespace();
                    return scanDefaultValue(elementName, attributeName);
                }
                return null;
            }
            return scanDefaultValue(elementName, attributeName);
        }

        private String scanDefaultValue(final String elementName, final String attributeName)
        {
            final int start = lexer.position();
            final String literal = lexer.scanQuotedLiteralIfPresent();
            if (literal == null)
            {
                return null;
            }
            try
            {
                return AttributeValueCodec.decode(literal);
            }
            catch (final AttributeValueException e)
            {
                final String message = NLS.bind(Messages.Dtd_attributeDefaultNotShown,
                        new Object[] { attributeName, elementName, e.getMessage() });
                addInfoProblem(message, start, lexer.position() - start);
                return null;
            }
        }

        private void scanIgnoredDeclaration(final String message)
        {
            final int start = lexer.position();
            lexer.skipToMatchingCloseAngleBracket();
            if (message != null)
            {
                final int end = lexer.position();
                addInfoProblem(message, start, end - start);
            }
        }

        private void scanConditionalSection()
        {
            final int start = lexer.position();
            lexer.skipConditionalSection();
            final int end = lexer.position();
            addInfoProblem(Messages.Dtd_conditionalSectionsIgnored, start, end - start);
        }

        private void scanParameterEntityReference()
        {
            final int start = lexer.position();
            lexer.skipParameterEntityReference();
            final int end = lexer.position();
            addInfoProblem(Messages.Dtd_parameterEntityReferencesIgnored, start, end - start);
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
