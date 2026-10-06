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

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.ITokenScanner;

/**
 * Colors a tag again as a whole whenever a part of it changes. The inherited repairer colors only the lines
 * that changed, and a scan that starts on a later line of a tag cannot know that a quoted attribute value
 * began on an earlier line, so it would take the text before the closing quote for the rest of the tag.
 *
 * @since 1.0.0
 */
final class WholeTagDamagerRepairer extends DefaultDamagerRepairer
{
    /**
     * Creates the damager and repairer.
     *
     * @param scanner The scanner that finds the colored tokens of a tag.
     */
    WholeTagDamagerRepairer(final ITokenScanner scanner)
    {
        super(scanner);
    }

    /**
     * Returns the whole tag as the region to color again.
     *
     * @param partition The tag that changed.
     * @param event The change of the document.
     * @param documentPartitioningChanged Whether the change altered the partitioning.
     * @return The partition.
     */
    @Override
    public IRegion getDamageRegion(final ITypedRegion partition, final DocumentEvent event,
            final boolean documentPartitioningChanged)
    {
        return partition;
    }
}
