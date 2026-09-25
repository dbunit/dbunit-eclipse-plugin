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
import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;

/**
 * Copies the value of the selection's top row into its other selected rows, one column at a time; with a
 * one-row selection, copies from the row above instead.
 *
 * @since 1.0.0
 */
public final class FillDownAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public FillDownAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.FILL_DOWN, context);
        setText("Fill Down");
        setImageDescriptor(DatasetImages.getImageDescriptor(DatasetImages.IMG_FILL_DOWN));
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final DatasetTable table =
                context.getDatasetDocument().getModel().findTable(selection.tableKey()).orElseThrow();
        final boolean multipleRowsSelected = selection.rowIndexes().size() > 1;
        final int sourceRowIndex =
                multipleRowsSelected ? selection.firstRowIndex() : selection.firstRowIndex() - 1;
        final List<CellChange> changes = new ArrayList<>();
        for (int columnIndex = selection.firstColumnIndex(); columnIndex <= selection.lastColumnIndex();
                columnIndex++)
        {
            final String columnName = table.getColumns().get(columnIndex).name();
            final String sourceValue = table.getRows().get(sourceRowIndex).getValue(columnIndex);
            if (multipleRowsSelected)
            {
                for (final int rowIndex : selection.rowIndexes())
                {
                    if (rowIndex != sourceRowIndex)
                    {
                        changes.add(new CellChange(rowIndex, columnName, sourceValue));
                    }
                }
            }
            else
            {
                changes.add(new CellChange(selection.firstRowIndex(), columnName, sourceValue));
            }
        }
        context.executeMultiCellEdit("Fill Down",
                () -> context.getDatasetDocument().setCells(selection.tableKey(), changes));
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return !selection.rowIndexes().isEmpty() && !selection.columnIndexes().isEmpty()
                && (selection.rowIndexes().size() > 1 || selection.firstRowIndex() > 0);
    }
}
