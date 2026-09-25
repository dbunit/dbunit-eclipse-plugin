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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextUtilities;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link XmlPartitionScanner} through the partitioner that {@link XmlDocumentSetupParticipant}
 * installs.
 */
class XmlPartitionScannerTest
{
    private static final String TEXT = IDocument.DEFAULT_CONTENT_TYPE;

    private static final String COMMENT = XmlPartitionScanner.COMMENT;

    private static final String PROCESSING_INSTRUCTION = XmlPartitionScanner.PROCESSING_INSTRUCTION;

    private static final String DOCTYPE = XmlPartitionScanner.DOCTYPE;

    private static final String TAG = XmlPartitionScanner.TAG;

    @Test
    void testNextToken_whenTextHasEveryConstruct_partitionsCommentProcessingInstructionDoctypeTagAndText()
            throws Exception
    {
        final IDocument document = setUpDocument("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE dataset SYSTEM "dataset.dtd">
                <!-- Users > orders -->
                <dataset>
                    <USERS ID="1" NOTE="a > b" NAME='c > d'/>
                </dataset>
                """);

        final List<Partition> partitions = partitions(document);

        assertThat(partitions)
                .as("Each construct must be one partition, and a quoted > must not end its tag.")
                .isEqualTo(List.of(
                        new Partition(PROCESSING_INSTRUCTION, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                        new Partition(TEXT, "\n"),
                        new Partition(DOCTYPE, "<!DOCTYPE dataset SYSTEM \"dataset.dtd\">"),
                        new Partition(TEXT, "\n"),
                        new Partition(COMMENT, "<!-- Users > orders -->"),
                        new Partition(TEXT, "\n"),
                        new Partition(TAG, "<dataset>"),
                        new Partition(TEXT, "\n    "),
                        new Partition(TAG, "<USERS ID=\"1\" NOTE=\"a > b\" NAME='c > d'/>"),
                        new Partition(TEXT, "\n"),
                        new Partition(TAG, "</dataset>"),
                        new Partition(TEXT, "\n")));
    }

    @Test
    void testNextToken_whenTheLastTagHasNoEnd_extendsTheTagToTheEndOfTheText() throws Exception
    {
        final IDocument document = setUpDocument("<dataset>\n<USERS NOTE=\"a\"");

        final List<Partition> partitions = partitions(document);

        assertThat(partitions).as("A tag without its > must extend to the end of the text.")
                .isEqualTo(List.of(new Partition(TAG, "<dataset>"), new Partition(TEXT, "\n"),
                        new Partition(TAG, "<USERS NOTE=\"a\"")));
    }

    @Test
    void testNextToken_afterAChangeInsideAQuotedValue_keepsTheTagInOnePartition() throws Exception
    {
        final IDocument document = setUpDocument("""
                <dataset>
                    <USERS ID="1"
                           NOTE="a > b"/>
                </dataset>
                """);

        document.replace(document.get().indexOf("a >") + 1, 0, "x");

        assertThat(partitions(document))
                .as("After a change inside a quoted value, the > in that value must still not end the tag.")
                .isEqualTo(List.of(
                        new Partition(TAG, "<dataset>"),
                        new Partition(TEXT, "\n    "),
                        new Partition(TAG, "<USERS ID=\"1\"\n           NOTE=\"ax > b\"/>"),
                        new Partition(TEXT, "\n"),
                        new Partition(TAG, "</dataset>"),
                        new Partition(TEXT, "\n")));
    }

    private static IDocument setUpDocument(final String text)
    {
        final IDocument document = new Document(text);
        final XmlDocumentSetupParticipant participant = new XmlDocumentSetupParticipant();
        participant.setup(document);
        return document;
    }

    private static List<Partition> partitions(final IDocument document) throws BadLocationException
    {
        final ITypedRegion[] regions = TextUtilities.computePartitioning(document,
                XmlDocumentSetupParticipant.PARTITIONING, 0, document.getLength(), false);
        final List<Partition> partitions = new ArrayList<>();
        for (final ITypedRegion region : regions)
        {
            final String text = document.get(region.getOffset(), region.getLength());
            partitions.add(new Partition(region.getType(), text));
        }
        return partitions;
    }

    private record Partition(String type, String text)
    {
    }
}
