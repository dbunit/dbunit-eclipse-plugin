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
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.dbunit.eclipse.dataset.core.DatasetCore;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * What the classes that plan the edits of one operation read: the text of the document, the index and the
 * layout of the parse that all offsets come from, and an encoder for the charset that the document is saved
 * in. The dataset document creates one after it refreshed, for the operation that it is about to plan, so
 * the index and the layout always describe the text as it is. The text and the encoder are read when they
 * are asked for, not when the context is created.
 */
final class EditContext
{
    private final Supplier<String> text;

    private final FlatXmlIndex index;

    private final FlatXmlTextLayout layout;

    private final Supplier<Charset> charset;

    private final FlatXmlOptions options;

    private final Consumer<IStatus> log;

    /**
     * Creates the context of an operation.
     *
     * @param text Returns the current text of the document.
     * @param index The index of the parse that the offsets come from.
     * @param layout The layout of the text that was parsed.
     * @param charset Returns the document's current charset; a null result or a thrown exception falls back
     *                to UTF-8.
     * @param options The case-sensitivity and column-sensing options that the model was built with.
     * @param log Receives a warning status for each failure of the charset supplier.
     */
    EditContext(final Supplier<String> text, final FlatXmlIndex index, final FlatXmlTextLayout layout,
            final Supplier<Charset> charset, final FlatXmlOptions options, final Consumer<IStatus> log)
    {
        this.text = text;
        this.index = index;
        this.layout = layout;
        this.charset = charset;
        this.options = options;
        this.log = log;
    }

    /**
     * Returns the current text of the document.
     *
     * @return The text, which equals the text that the index was parsed from.
     */
    String text()
    {
        return text.get();
    }

    /**
     * Returns the index of the parse that all offsets come from.
     *
     * @return The index.
     */
    FlatXmlIndex index()
    {
        return index;
    }

    /**
     * Returns the layout of the text that was parsed.
     *
     * @return The layout.
     */
    FlatXmlTextLayout layout()
    {
        return layout;
    }

    /**
     * Returns whether dbUnit takes the columns of a table from the attributes of the table's first element,
     * which it does when the document has no DOCTYPE, so no DTD, and column sensing is off.
     *
     * @return True when an element's attribute that the table's first element lacks is ignored by dbUnit.
     */
    boolean firstElementDefinesColumns()
    {
        return index.getDoctype() == null && !options.columnSensing();
    }

    /**
     * Returns an encoder for the document's current charset, falling back to UTF-8 when the supplier
     * returns null or throws; a thrown exception is logged.
     */
    CharsetEncoder encoder()
    {
        try
        {
            final Charset result = charset.get();
            return (result != null ? result : StandardCharsets.UTF_8).newEncoder();
        }
        catch (final RuntimeException e)
        {
            final String message = "The charset supplier failed, so the document escapes values for UTF-8.";
            warn(message, e);
            return StandardCharsets.UTF_8.newEncoder();
        }
    }

    private void warn(final String message, final RuntimeException cause)
    {
        final IStatus status = new Status(IStatus.WARNING, DatasetCore.PLUGIN_ID, message, cause);
        log.accept(status);
    }
}
