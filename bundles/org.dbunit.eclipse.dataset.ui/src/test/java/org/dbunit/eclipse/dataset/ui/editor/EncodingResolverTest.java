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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link EncodingResolver}'s fallback to UTF-8 and the once-per-name log of an unsupported encoding.
 */
class EncodingResolverTest
{
    @Test
    void testCharsetOf_whenTheEncodingIsSupported_returnsItsCharsetAndLogsNothing()
    {
        try (LogRecorder log = new LogRecorder(EncodingResolver.class))
        {
            final Charset charset = new EncodingResolver().charsetOf("ISO-8859-1");

            assertThat(charset).as("A supported encoding must resolve to its own charset.")
                    .isEqualTo(StandardCharsets.ISO_8859_1);
            assertThat(log.statuses()).as("A supported encoding must not be logged.").isEmpty();
        }
    }

    @Test
    void testCharsetOf_whenTheEncodingIsUnsupported_returnsUtf8AndLogsAWarning()
    {
        try (LogRecorder log = new LogRecorder(EncodingResolver.class))
        {
            final Charset charset = new EncodingResolver().charsetOf("no-such-charset");

            assertThat(charset).as("An unsupported encoding must fall back to UTF-8.")
                    .isEqualTo(StandardCharsets.UTF_8);
            assertThat(log.statuses()).as("An unsupported encoding must be logged once.").hasSize(1);
            final IStatus warning = log.statuses().get(0);
            assertThat(warning).as("The warning must name the encoding.").usingRecursiveComparison()
                    .ignoringFields("exception")
                    .isEqualTo(new Status(IStatus.WARNING, "org.dbunit.eclipse.dataset.ui",
                            "The encoding \"no-such-charset\" is not supported, so the dataset editor "
                                    + "uses UTF-8 instead.",
                            null));
            assertThat(warning.getException()).as("The warning must carry the reason.")
                    .isInstanceOf(UnsupportedCharsetException.class);
        }
    }

    @Test
    void testCharsetOf_whenTheEncodingNameIsIllegal_returnsUtf8AndLogsAWarning()
    {
        try (LogRecorder log = new LogRecorder(EncodingResolver.class))
        {
            final Charset charset = new EncodingResolver().charsetOf("not a charset!");

            assertThat(charset).as("An illegal encoding name must fall back to UTF-8.")
                    .isEqualTo(StandardCharsets.UTF_8);
            assertThat(log.statuses()).as("An illegal encoding name must be logged once.").hasSize(1);
            assertThat(log.statuses().get(0).getException()).as("The warning must carry the reason.")
                    .isInstanceOf(IllegalCharsetNameException.class);
        }
    }

    @Test
    void testCharsetOf_whenTheSameUnsupportedEncodingIsAskedAgain_logsItOnlyOnce()
    {
        try (LogRecorder log = new LogRecorder(EncodingResolver.class))
        {
            final EncodingResolver resolver = new EncodingResolver();

            resolver.charsetOf("no-such-charset");
            resolver.charsetOf("no-such-charset");

            assertThat(log.statuses())
                    .as("An encoding that stays unsupported must be logged once, not on every request.")
                    .hasSize(1);
        }
    }

    @Test
    void testCharsetOf_whenAnotherUnsupportedEncodingFollows_logsItToo()
    {
        try (LogRecorder log = new LogRecorder(EncodingResolver.class))
        {
            final EncodingResolver resolver = new EncodingResolver();

            resolver.charsetOf("no-such-charset");
            resolver.charsetOf("another-missing-charset");

            assertThat(log.statuses()).as("A different unsupported encoding must be logged as well.")
                    .hasSize(2);
        }
    }
}
