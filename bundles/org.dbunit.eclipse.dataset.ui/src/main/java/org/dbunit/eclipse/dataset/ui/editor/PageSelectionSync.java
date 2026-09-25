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

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.dbunit.eclipse.dataset.core.edit.TextDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.eclipse.jface.text.IRegion;

/**
 * Keeps the Tables and Source pages' selections in sync as the active page switches.
 *
 * @since 1.0.0
 */
final class PageSelectionSync
{
    private final Supplier<IRegion> sourceSelection;

    private final BiConsumer<Integer, Integer> selectAndRevealOnSource;

    private final TextDatasetDocument datasetDocument;

    private IRegion lastSourceRegion;

    /**
     * Creates a page selection sync.
     *
     * @param sourceSelection Returns the Source page's current selection, as a range.
     * @param selectAndRevealOnSource Selects and reveals a range (offset, length) on the Source page.
     * @param datasetDocument The document to locate cells in and find cells by offset in.
     */
    PageSelectionSync(final Supplier<IRegion> sourceSelection,
            final BiConsumer<Integer, Integer> selectAndRevealOnSource,
            final TextDatasetDocument datasetDocument)
    {
        this.sourceSelection = sourceSelection;
        this.selectAndRevealOnSource = selectAndRevealOnSource;
        this.datasetDocument = datasetDocument;
    }

    /**
     * Called when leaving the Tables page: selects and reveals the cell's range on the Source page.
     *
     * @param selectedCell The address of the selected cell, or null when there is none.
     */
    void onDeactivate(final CellAddress selectedCell)
    {
        lastSourceRegion = null;
        if (selectedCell == null)
        {
            return;
        }
        final Optional<IRegion> region = datasetDocument.locate(selectedCell);
        if (region.isEmpty())
        {
            return;
        }
        lastSourceRegion = region.get();
        selectAndRevealOnSource.accept(lastSourceRegion.getOffset(), lastSourceRegion.getLength());
    }

    /**
     * Called when entering the Tables page: decides whether the grid selection should change.
     *
     * @return The cell to select, when the Source page's selection moved since {@link #onDeactivate}; an
     *         empty result when it is still the range that was set then, so the grid selection must be
     *         kept as is.
     */
    Optional<CellAddress> onActivate()
    {
        final IRegion selection = sourceSelection.get();
        if (lastSourceRegion != null && selection.getOffset() == lastSourceRegion.getOffset()
                && selection.getLength() == lastSourceRegion.getLength())
        {
            return Optional.empty();
        }
        return datasetDocument.cellAt(selection.getOffset());
    }
}
