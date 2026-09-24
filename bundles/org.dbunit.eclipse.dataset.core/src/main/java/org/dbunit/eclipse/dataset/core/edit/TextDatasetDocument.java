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
package org.dbunit.eclipse.dataset.core.edit;

import java.util.Optional;

import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.eclipse.jface.text.IRegion;

/**
 * A dataset stored as text, supporting navigation between cells and text offsets.
 *
 * @since 1.0.0
 */
public interface TextDatasetDocument extends DatasetDocument
{
    /**
     * Returns whether the text has no content worth parsing.
     *
     * @return True when the text is empty or only whitespace.
     */
    boolean isBlank();

    /**
     * Replaces a blank text with an empty dataset: an XML declaration, {@code <dataset>}, and
     * {@code </dataset>}.
     *
     * @throws DatasetEditException When the text is not blank.
     */
    void createEmptyDataset();

    /**
     * Locates a cell or a row in the text.
     *
     * @param address The cell to locate, or a row when its column index is -1.
     * @return The attribute value range of the cell, the element name range when the cell is NULL, or
     *         the element range for a row address; empty when the address does not exist.
     */
    Optional<IRegion> locate(CellAddress address);

    /**
     * Finds the cell at a text offset.
     *
     * @param offset The offset to look up.
     * @return The attribute under the offset, or the row's first column when the offset is not inside an
     *         attribute; empty when the offset is not inside a row element.
     */
    Optional<CellAddress> cellAt(int offset);
}
