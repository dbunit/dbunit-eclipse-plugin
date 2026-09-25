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
package org.dbunit.eclipse.dataset.ui.grid;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.nebula.widgets.nattable.layer.LayerUtil;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectCellCommand;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectColumnCommand;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectRowsCommand;
import org.eclipse.nebula.widgets.nattable.ui.action.IMouseAction;
import org.eclipse.swt.events.MouseEvent;

/**
 * The part of a grid that a context menu is for. A right-click selects the cell, row, or column under the
 * mouse unless it is already selected, so that the menu's commands act on it, as in a spreadsheet.
 *
 * @since 1.0.0
 */
final class ContextMenuTarget implements IMouseAction
{
    private final SelectionLayer selectionLayer;

    ContextMenuTarget(final SelectionLayer selectionLayer)
    {
        this.selectionLayer = selectionLayer;
    }

    /**
     * Returns the grid region at a point of a grid.
     *
     * @param natTable The grid.
     * @param x The point's x coordinate, relative to the grid.
     * @param y The point's y coordinate, relative to the grid.
     * @return {@link GridRegion#ROW_HEADER}, {@link GridRegion#COLUMN_HEADER}, or {@link GridRegion#CORNER}
     *         for a point in one of them, else {@link GridRegion#BODY}, also beyond the last column or row.
     */
    static String regionAt(final NatTable natTable, final int x, final int y)
    {
        final LabelStack regionLabels = natTable.getRegionLabelsByXY(x, y);
        if (regionLabels == null)
        {
            return GridRegion.BODY;
        }
        if (regionLabels.hasLabel(GridRegion.ROW_HEADER))
        {
            return GridRegion.ROW_HEADER;
        }
        if (regionLabels.hasLabel(GridRegion.COLUMN_HEADER))
        {
            return GridRegion.COLUMN_HEADER;
        }
        if (regionLabels.hasLabel(GridRegion.CORNER))
        {
            return GridRegion.CORNER;
        }
        return GridRegion.BODY;
    }

    @Override
    public void run(final NatTable natTable, final MouseEvent event)
    {
        final String region = regionAt(natTable, event.x, event.y);
        final int columnPosition = natTable.getColumnPositionByX(event.x);
        final int rowPosition = natTable.getRowPositionByY(event.y);
        final int column = LayerUtil.convertColumnPosition(natTable, columnPosition, selectionLayer);
        final int row = LayerUtil.convertRowPosition(natTable, rowPosition, selectionLayer);
        if (GridRegion.ROW_HEADER.equals(region))
        {
            if (row >= 0 && !selectionLayer.isRowPositionFullySelected(row))
            {
                selectionLayer.doCommand(new SelectRowsCommand(selectionLayer, 0, row, false, false));
            }
        }
        else if (GridRegion.COLUMN_HEADER.equals(region))
        {
            if (column >= 0 && !selectionLayer.isColumnPositionFullySelected(column))
            {
                selectionLayer.doCommand(new SelectColumnCommand(selectionLayer, column, 0, false, false));
            }
        }
        else if (GridRegion.BODY.equals(region) && column >= 0 && row >= 0
                && !selectionLayer.isCellPositionSelected(column, row))
        {
            selectionLayer.doCommand(new SelectCellCommand(selectionLayer, column, row, false, false));
        }
    }
}
