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
package org.dbunit.eclipse.dataset.ui.editor;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

import org.eclipse.core.runtime.ILog;

/**
 * Turns the encoding name that the Source page reports into a charset, using UTF-8 for a name that Java does
 * not support. An unsupported name is logged once, not each time the editor asks for the charset, which it
 * does for every edit that needs one.
 *
 * @since 1.0.0
 */
final class EncodingResolver
{
    private static final ILog LOG = ILog.of(EncodingResolver.class);

    private String reportedEncoding;

    /**
     * Returns the charset for an encoding name.
     *
     * @param encoding The encoding name.
     * @return The named charset, or UTF-8 when Java does not support the name.
     */
    Charset charsetOf(final String encoding)
    {
        try
        {
            return Charset.forName(encoding);
        }
        catch (final IllegalCharsetNameException | UnsupportedCharsetException e)
        {
            report(encoding, e);
            return StandardCharsets.UTF_8;
        }
    }

    private void report(final String encoding, final IllegalArgumentException cause)
    {
        if (encoding.equals(reportedEncoding))
        {
            return;
        }
        reportedEncoding = encoding;
        LOG.warn("The encoding \"" + encoding + "\" is not supported, so the dataset editor uses UTF-8 instead.",
                cause);
    }
}
