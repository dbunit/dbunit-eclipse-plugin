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

import java.util.List;

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.Token;

/**
 * Divides XML text into comment, processing instruction, DOCTYPE, and tag partitions; everything else,
 * such as the whitespace between tags, stays in the default partition.
 *
 * @since 1.0.0
 */
final class XmlPartitionScanner extends RuleBasedPartitionScanner
{
    /**
     * The content type of a comment, from {@code <!--} to {@code -->}.
     */
    static final String COMMENT = "__xml_comment";

    /**
     * The content type of a processing instruction, such as the XML declaration.
     */
    static final String PROCESSING_INSTRUCTION = "__xml_processing_instruction";

    /**
     * The content type of the DOCTYPE, which ends at its first {@code >}, so a DOCTYPE with an internal
     * subset ends early.
     */
    static final String DOCTYPE = "__xml_doctype";

    /**
     * The content type of a start, end, or empty-element tag.
     */
    static final String TAG = "__xml_tag";

    /**
     * The content types of the partitions this scanner finds, without the default content type.
     */
    static final List<String> PARTITION_TYPES = List.of(COMMENT, PROCESSING_INSTRUCTION, DOCTYPE, TAG);

    XmlPartitionScanner()
    {
        setPredicateRules(new IPredicateRule[] {
                new MultiLineRule("<!--", "-->", new Token(COMMENT), (char) 0, true),
                new MultiLineRule("<?", "?>", new Token(PROCESSING_INSTRUCTION), (char) 0, true),
                new MultiLineRule("<!DOCTYPE", ">", new Token(DOCTYPE), (char) 0, true),
                new TagRule(new Token(TAG)) });
    }

    /**
     * Matches a tag from its {@code <} to the first {@code >} outside a quoted attribute value. A tag
     * without its {@code >} extends to the end of the scanned range.
     */
    private static final class TagRule implements IPredicateRule
    {
        private final IToken successToken;

        TagRule(final IToken successToken)
        {
            this.successToken = successToken;
        }

        @Override
        public IToken getSuccessToken()
        {
            return successToken;
        }

        @Override
        public IToken evaluate(final ICharacterScanner scanner)
        {
            return evaluate(scanner, false);
        }

        @Override
        public IToken evaluate(final ICharacterScanner scanner, final boolean resume)
        {
            if (!resume)
            {
                final int first = scanner.read();
                if (first != '<')
                {
                    scanner.unread();
                    return Token.UNDEFINED;
                }
            }
            int quote = 0;
            int c = scanner.read();
            while (c != ICharacterScanner.EOF)
            {
                if (quote == 0)
                {
                    if (c == '>')
                    {
                        return successToken;
                    }
                    if (c == '"' || c == '\'')
                    {
                        quote = c;
                    }
                }
                else if (c == quote)
                {
                    quote = 0;
                }
                c = scanner.read();
            }
            scanner.unread();
            return successToken;
        }
    }
}
