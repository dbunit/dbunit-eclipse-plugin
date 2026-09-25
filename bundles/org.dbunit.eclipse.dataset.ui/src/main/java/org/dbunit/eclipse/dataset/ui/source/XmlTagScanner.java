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
package org.dbunit.eclipse.dataset.ui.source;

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;

/**
 * Scans the inside of a tag partition: quoted attribute values, attribute names, and, with the tag
 * token, the element name and everything else.
 *
 * @since 1.0.0
 */
final class XmlTagScanner extends RuleBasedScanner
{
    XmlTagScanner(final IToken tagToken, final IToken attributeNameToken, final IToken attributeValueToken)
    {
        setRules(new IRule[] {
                new MultiLineRule("\"", "\"", attributeValueToken, (char) 0, true),
                new MultiLineRule("'", "'", attributeValueToken, (char) 0, true),
                new NameRule(tagToken, attributeNameToken) });
        setDefaultReturnToken(tagToken);
    }

    /**
     * Matches a name: an attribute name when an equals sign follows it, possibly after whitespace, and
     * otherwise the element name.
     */
    private static final class NameRule implements IRule
    {
        private static final String DELIMITERS = "<>/=\"'";

        private final IToken elementNameToken;

        private final IToken attributeNameToken;

        NameRule(final IToken elementNameToken, final IToken attributeNameToken)
        {
            this.elementNameToken = elementNameToken;
            this.attributeNameToken = attributeNameToken;
        }

        @Override
        public IToken evaluate(final ICharacterScanner scanner)
        {
            int c = scanner.read();
            if (!isNameCharacter(c))
            {
                scanner.unread();
                return Token.UNDEFINED;
            }
            while (isNameCharacter(c))
            {
                c = scanner.read();
            }
            int readAhead = 1;
            while (Character.isWhitespace(c))
            {
                c = scanner.read();
                readAhead++;
            }
            final IToken token = c == '=' ? attributeNameToken : elementNameToken;
            for (int i = 0; i < readAhead; i++)
            {
                scanner.unread();
            }
            return token;
        }

        private static boolean isNameCharacter(final int c)
        {
            return c != ICharacterScanner.EOF && !Character.isWhitespace(c) && DELIMITERS.indexOf(c) < 0;
        }
    }
}
