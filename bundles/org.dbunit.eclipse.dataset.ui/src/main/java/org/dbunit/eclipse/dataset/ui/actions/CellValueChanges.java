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
package org.dbunit.eclipse.dataset.ui.actions;

import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.swt.graphics.Point;

/**
 * Builds the changes that set cells of a table to one value, for the commands that set the selected cells
 * to NULL, to the empty string, or to a pasted value.
 *
 * @since 1.0.0
 */
final class CellValueChanges
{
    private CellValueChanges()
    {
    }

    /**
     * Returns the changes that set cells to a value.
     *
     * @param cells The cells as {@code (columnIndex, rowIndex)} points, such as the cells that the active
     *              grid has selected.
     * @param table The table that the cells belong to.
     * @param value The value to set, or null for NULL.
     * @return One change for each cell, in the order of the cells.
     */
    static List<CellChange> setting(final List<Point> cells, final DatasetTable table, final String value)
    {
        final List<CellChange> changes = new ArrayList<>();
        for (final Point cell : cells)
        {
            final String columnName = table.getColumns().get(cell.x).name();
            changes.add(new CellChange(cell.y, columnName, value));
        }
        return changes;
    }
}
