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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.dbunit.eclipse.dataset.core.DatasetCore;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link EditContext}: what it hands out, when it reads the text, and the encoder that it falls back
 * to when the charset supplier gives none or fails.
 */
class EditContextTest
{
    private static final ParsedDataset PARSED = ParsedDataset.of("<dataset/>");

    private static EditContext contextWith(final Supplier<Charset> charset, final List<IStatus> statuses)
    {
        return new EditContext(() -> "text", PARSED.index(), PARSED.layout(), charset,
                FlatXmlOptions.DBUNIT_DEFAULTS, statuses::add);
    }

    @Test
    void testText_whenCalledTwice_readsTheSupplierEachTime()
    {
        final AtomicInteger reads = new AtomicInteger();
        final EditContext context = new EditContext(() -> "text" + reads.incrementAndGet(), PARSED.index(),
                PARSED.layout(), () -> StandardCharsets.UTF_8, FlatXmlOptions.DBUNIT_DEFAULTS, status ->
                {
                    // Nothing is logged here.
                });

        final String first = context.text();
        final String second = context.text();

        assertThat(List.of(first, second)).as("The text must be read when it is asked for.")
                .containsExactly("text1", "text2");
    }

    @Test
    void testIndex_whenCalled_returnsTheIndexItWasGiven()
    {
        final EditContext context = contextWith(() -> StandardCharsets.UTF_8, new ArrayList<>());

        assertThat(context.index()).as("The index must be the one of the parse.").isSameAs(PARSED.index());
    }

    @Test
    void testLayout_whenCalled_returnsTheLayoutItWasGiven()
    {
        final EditContext context = contextWith(() -> StandardCharsets.UTF_8, new ArrayList<>());

        assertThat(context.layout()).as("The layout must be the one of the parse.")
                .isSameAs(PARSED.layout());
    }

    @Test
    void testEncoder_whenTheSupplierReturnsACharset_encodesInThatCharset()
    {
        final List<IStatus> statuses = new ArrayList<>();
        final EditContext context = contextWith(() -> StandardCharsets.ISO_8859_1, statuses);

        final CharsetEncoder encoder = context.encoder();

        assertThat(encoder.charset()).as("The encoder must use the document's charset.")
                .isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(statuses).as("Nothing goes wrong, so nothing is logged.").isEmpty();
    }

    @Test
    void testEncoder_whenTheSupplierReturnsNull_fallsBackToUtf8WithoutLogging()
    {
        final List<IStatus> statuses = new ArrayList<>();
        final EditContext context = contextWith(() -> null, statuses);

        final CharsetEncoder encoder = context.encoder();

        assertThat(encoder.charset()).as("A missing charset means UTF-8.").isEqualTo(StandardCharsets.UTF_8);
        assertThat(statuses).as("A missing charset is not a failure.").isEmpty();
    }

    @Test
    void testEncoder_whenTheSupplierThrows_fallsBackToUtf8AndLogsAWarning()
    {
        final IllegalStateException failure = new IllegalStateException("broken");
        final List<IStatus> statuses = new ArrayList<>();
        final EditContext context = contextWith(() ->
        {
            throw failure;
        }, statuses);

        final CharsetEncoder encoder = context.encoder();

        assertThat(encoder.charset()).as("A failing supplier means UTF-8.").isEqualTo(StandardCharsets.UTF_8);
        assertThat(statuses).as("The failure must be logged once, with its cause.")
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(new Status(IStatus.WARNING, DatasetCore.PLUGIN_ID,
                        "The charset supplier failed, so the document escapes values for UTF-8.", failure));
    }

    @Test
    void testEncoder_whenCalledTwice_returnsANewEncoderEachTime()
    {
        final EditContext context = contextWith(() -> StandardCharsets.UTF_8, new ArrayList<>());

        assertThat(context.encoder()).as("An encoder keeps state, so it must not be shared.")
                .isNotSameAs(context.encoder());
    }

    @Test
    void testFirstElementDefinesColumns_whenThereIsNoDoctypeAndColumnSensingIsOff_isTrue()
    {
        final EditContext context = ParsedDataset.of("<dataset><T ID=\"1\"/></dataset>").editContext();

        assertThat(context.firstElementDefinesColumns())
                .as("dbUnit takes the columns from the first element, so the rule applies.").isTrue();
    }

    @Test
    void testFirstElementDefinesColumns_whenColumnSensingIsOn_isFalse()
    {
        final ParsedDataset parsed = ParsedDataset.of("<dataset><T ID=\"1\"/></dataset>",
                new FlatXmlOptions(false, true), "\n");

        assertThat(parsed.editContext().firstElementDefinesColumns())
                .as("With column sensing, dbUnit adds the columns of every element.").isFalse();
    }

    @Test
    void testFirstElementDefinesColumns_whenTheDocumentHasADoctype_isFalse()
    {
        final ParsedDataset parsed = ParsedDataset.withDtd(
                "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><T ID=\"1\"/></dataset>",
                "<!ELEMENT dataset (T*)><!ATTLIST T ID CDATA #REQUIRED>");

        assertThat(parsed.editContext().firstElementDefinesColumns())
                .as("With a DTD, dbUnit takes the columns from the DTD.").isFalse();
    }
}
