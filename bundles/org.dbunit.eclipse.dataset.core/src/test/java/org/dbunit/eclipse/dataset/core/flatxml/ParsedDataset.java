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

import java.util.Map;

import org.dbunit.eclipse.dataset.core.model.DatasetModel;

/**
 * A dataset text together with the model, the index, and the layout of one parse of it, which the tests of
 * the classes that read a parse need as their input.
 *
 * @param text The dataset text that was parsed.
 * @param model The model that the parse produced.
 * @param index The index that the parse produced.
 * @param layout The layout of the text, with the line delimiter that was given.
 * @param options The options that the parse used.
 */
record ParsedDataset(String text, DatasetModel model, FlatXmlIndex index, FlatXmlTextLayout layout,
        FlatXmlOptions options)
{
    /**
     * Parses a text with dbUnit's default options and the line delimiter of a line feed.
     *
     * @param text The dataset text.
     * @return The parse.
     */
    static ParsedDataset of(final String text)
    {
        return of(text, FlatXmlOptions.DBUNIT_DEFAULTS, "\n");
    }

    /**
     * Parses a text.
     *
     * @param text The dataset text.
     * @param options The options of the parse.
     * @param lineDelimiter The line delimiter that the layout uses for new lines.
     * @return The parse.
     */
    static ParsedDataset of(final String text, final FlatXmlOptions options, final String lineDelimiter)
    {
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final FlatXmlModelBuilder.Result built =
                FlatXmlModelBuilder.build(text, parse, null, options, Map.of());
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, lineDelimiter);
        return new ParsedDataset(text, built.model(), built.index(), layout, options);
    }
}
