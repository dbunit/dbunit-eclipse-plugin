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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.swt.graphics.Point;

/**
 * Copies, in each selected column on its own, the topmost selected value into that column's other
 * selected cells; a column with a single selected cell copies from the row above instead.
 *
 * @since 1.0.0
 */
public final class FillDownAction extends GridAction
{
    private final DatasetGridContext context;

    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public FillDownAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.FILL_DOWN, context);
        this.context = context;
        setText(Messages.Action_fillDown);
        setImageDescriptor(DatasetImages.getImageDescriptor(DatasetImages.IMG_FILL_DOWN));
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final DatasetTable table =
                context.getDatasetDocument().getModel().findTable(selection.tableKey()).orElseThrow();
        final List<CellChange> changes = new ArrayList<>();
        for (final Map.Entry<Integer, List<Integer>> entry : selectedRowsByColumn(context).entrySet())
        {
            final int columnIndex = entry.getKey();
            final List<Integer> selectedRows = entry.getValue();
            final int sourceRowIndex = sourceRowIndex(selectedRows);
            if (sourceRowIndex < 0)
            {
                continue;
            }
            final String columnName = table.getColumns().get(columnIndex).name();
            final String sourceValue = table.getRows().get(sourceRowIndex).getValue(columnIndex);
            for (final int rowIndex : selectedRows)
            {
                if (rowIndex != sourceRowIndex)
                {
                    changes.add(new CellChange(rowIndex, columnName, sourceValue));
                }
            }
        }
        context.executeMultiCellEdit(Messages.Action_fillDown,
                () -> context.getDatasetDocument().setCells(selection.tableKey(), changes));
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        if (selection.columnIndexes().isEmpty())
        {
            return false;
        }
        for (final List<Integer> selectedRows : selectedRowsByColumn(context).values())
        {
            if (sourceRowIndex(selectedRows) >= 0)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Groups the selected cell positions by column, each column's rows sorted ascending.
     */
    private static Map<Integer, List<Integer>> selectedRowsByColumn(final DatasetGridContext context)
    {
        final Map<Integer, List<Integer>> selectedRowsByColumn = new TreeMap<>();
        for (final Point cell : context.getSelectedCellPositions())
        {
            selectedRowsByColumn.computeIfAbsent(cell.x, key -> new ArrayList<>()).add(cell.y);
        }
        for (final List<Integer> selectedRows : selectedRowsByColumn.values())
        {
            Collections.sort(selectedRows);
        }
        return selectedRowsByColumn;
    }

    /**
     * Returns a column's fill source row: its own topmost selected row when it has several selected
     * cells, or the row above its single selected cell, or -1 when that single selected cell is in row 0.
     */
    private static int sourceRowIndex(final List<Integer> selectedRowsInColumn)
    {
        return selectedRowsInColumn.size() > 1 ? selectedRowsInColumn.get(0)
                : selectedRowsInColumn.get(0) - 1;
    }
}
