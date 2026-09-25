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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.nebula.widgets.nattable.coordinate.PositionCoordinate;
import org.eclipse.nebula.widgets.nattable.coordinate.Range;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;

/**
 * A snapshot of a grid's selection, computed once per selection or model change and shared by every
 * {@code GridAction} so none of them has to call {@code SelectionLayer.getSelectedCellPositions()}, whose
 * cost grows with the number of selected cells.
 *
 * @param tableKey The key of the table the selection belongs to, or null when there is no active grid.
 * @param columnCount The table's current column count.
 * @param anchorColumnIndex The selection anchor's column index, or {@code SelectionLayer.NO_SELECTION}.
 * @param anchorRowIndex The selection anchor's row index, or {@code SelectionLayer.NO_SELECTION}.
 * @param rowIndexes The selected row indexes, sorted ascending.
 * @param columnIndexes The selected column indexes, sorted ascending.
 * @param firstRowIndex The lowest selected row index, or -1 when {@code rowIndexes} is empty.
 * @param lastRowIndex The highest selected row index, or -1 when {@code rowIndexes} is empty.
 * @param firstColumnIndex The lowest selected column index, or -1 when {@code columnIndexes} is empty.
 * @param lastColumnIndex The highest selected column index, or -1 when {@code columnIndexes} is empty.
 * @param wholeRowsSelected True when every selected row is selected in full, and at least one row is
 *                          selected.
 * @since 1.0.0
 */
public record GridSelection(String tableKey, int columnCount, int anchorColumnIndex, int anchorRowIndex,
        List<Integer> rowIndexes, List<Integer> columnIndexes, int firstRowIndex, int lastRowIndex,
        int firstColumnIndex, int lastColumnIndex, boolean wholeRowsSelected)
{
    /**
     * The selection of a page with no active grid: no table, no anchor, no selected rows or columns.
     */
    public static final GridSelection NONE = new GridSelection(null, 0, SelectionLayer.NO_SELECTION,
            SelectionLayer.NO_SELECTION, List.of(), List.of(), -1, -1, -1, -1, false);

    /**
     * Computes a selection snapshot from a grid's current selection.
     *
     * @param tableKey The key of the table the grid displays.
     * @param columnCount The table's current column count.
     * @param selectionLayer The layer to read the selection from.
     * @return The computed snapshot.
     */
    public static GridSelection compute(final String tableKey, final int columnCount,
            final SelectionLayer selectionLayer)
    {
        final PositionCoordinate anchor = selectionLayer.getSelectionAnchor();
        final List<Integer> rowIndexes = new ArrayList<>();
        for (final Range range : selectionLayer.getSelectedRowPositions())
        {
            for (int rowIndex = range.start; rowIndex < range.end; rowIndex++)
            {
                rowIndexes.add(rowIndex);
            }
        }
        Collections.sort(rowIndexes);
        final List<Integer> columnIndexes = new ArrayList<>();
        for (final int columnIndex : selectionLayer.getSelectedColumnPositions())
        {
            columnIndexes.add(columnIndex);
        }
        Collections.sort(columnIndexes);
        boolean wholeRowsSelected = !rowIndexes.isEmpty();
        for (final int rowIndex : rowIndexes)
        {
            if (!selectionLayer.isRowPositionFullySelected(rowIndex))
            {
                wholeRowsSelected = false;
                break;
            }
        }
        return new GridSelection(tableKey, columnCount, anchor.columnPosition, anchor.rowPosition,
                rowIndexes, columnIndexes, boundStart(rowIndexes), boundEnd(rowIndexes),
                boundStart(columnIndexes), boundEnd(columnIndexes), wholeRowsSelected);
    }

    private static int boundStart(final List<Integer> indexes)
    {
        return indexes.isEmpty() ? -1 : indexes.get(0);
    }

    private static int boundEnd(final List<Integer> indexes)
    {
        return indexes.isEmpty() ? -1 : indexes.get(indexes.size() - 1);
    }
}
