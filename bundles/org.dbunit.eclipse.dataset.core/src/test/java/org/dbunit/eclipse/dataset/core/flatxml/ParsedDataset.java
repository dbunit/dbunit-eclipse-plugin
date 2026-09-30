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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdReader;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

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
        return of(text, null, options, lineDelimiter);
    }

    /**
     * Parses a text with dbUnit's default options and the line delimiter of a line feed, the way the dataset
     * document does for a DOCTYPE that brings in a DTD: the tables that the DTD declares and the text does not
     * use become tables without rows.
     *
     * @param text The dataset text.
     * @param dtdText The text of the DTD that the dataset's DOCTYPE names.
     * @return The parse.
     */
    static ParsedDataset withDtd(final String text, final String dtdText)
    {
        return of(text, DtdReader.read(dtdText), FlatXmlOptions.DBUNIT_DEFAULTS, "\n");
    }

    private static ParsedDataset of(final String text, final DtdDeclarations declarations,
            final FlatXmlOptions options, final String lineDelimiter)
    {
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final FlatXmlModelBuilder.Result built =
                FlatXmlModelBuilder.build(text, parse, declarations, options, Map.of());
        final FlatXmlTextLayout layout = new FlatXmlTextLayout(text, lineDelimiter);
        return new ParsedDataset(text, built.model(), built.index(), layout, options);
    }

    /**
     * Returns the context of an operation on this parse, for a document that is saved as UTF-8.
     *
     * @return The context.
     */
    EditContext editContext()
    {
        return editContext(StandardCharsets.UTF_8);
    }

    /**
     * Returns the context of an operation on this parse.
     *
     * @param charset The charset that the document is saved in.
     * @return The context.
     */
    EditContext editContext(final Charset charset)
    {
        return new EditContext(() -> text, index, layout, () -> charset, status ->
        {
            // These tests do not look at the log.
        });
    }

    /**
     * Applies planned edits to this text.
     *
     * @param edits The edits, which must not overlap.
     * @return The text after the edits.
     * @throws BadLocationException When an edit does not fit the text.
     */
    String apply(final List<TextEdit> edits) throws BadLocationException
    {
        final Document document = new Document(text);
        final MultiTextEdit root = new MultiTextEdit();
        for (final TextEdit edit : edits)
        {
            root.addChild(edit);
        }
        root.apply(document);
        return document.get();
    }
}
