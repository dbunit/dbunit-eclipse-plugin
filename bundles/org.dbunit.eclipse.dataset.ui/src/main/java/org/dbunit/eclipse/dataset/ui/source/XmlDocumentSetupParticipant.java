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

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.rules.FastPartitioner;

/**
 * Installs the XML partitioning on each document that the file buffer manager creates for a flat XML
 * dataset file, whichever editor opens the file.
 *
 * @since 1.0.0
 */
public final class XmlDocumentSetupParticipant implements IDocumentSetupParticipant
{
    /**
     * The name of the partitioning that divides a dataset document into comments, processing
     * instructions, the DOCTYPE, and tags.
     */
    public static final String PARTITIONING = "org.dbunit.eclipse.dataset.ui.xmlPartitioning";

    /**
     * Installs a partitioner for {@link #PARTITIONING} on the document.
     *
     * @param document The document to set up.
     */
    @Override
    public void setup(final IDocument document)
    {
        if (document instanceof final IDocumentExtension3 extension)
        {
            final XmlPartitionScanner scanner = new XmlPartitionScanner();
            final String[] partitionTypes = XmlPartitionScanner.PARTITION_TYPES.toArray(String[]::new);
            final IDocumentPartitioner partitioner = new FastPartitioner(scanner, partitionTypes);
            extension.setDocumentPartitioner(PARTITIONING, partitioner);
            partitioner.connect(document);
        }
    }
}
