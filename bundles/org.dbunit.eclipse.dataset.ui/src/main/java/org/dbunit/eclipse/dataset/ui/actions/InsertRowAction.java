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

import java.util.Optional;

import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;

/**
 * Inserts a blank row next to the selection anchor and selects it. A blank row has an empty string in its
 * first column. When it becomes the first row of a table whose columns dbUnit takes from its first row, it
 * has an empty string in every column of the old first row too, and the status line says so.
 *
 * @since 1.0.0
 */
abstract class InsertRowAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param commandId The command id of the action.
     * @param context What this action needs from the page that hosts the grid.
     */
    InsertRowAction(final String commandId, final DatasetGridContext context)
    {
        super(commandId, context);
    }

    /**
     * Returns the index that the blank row is inserted at.
     *
     * @param selection The active grid's current selection.
     * @return The index of the row that the blank row goes before, which may be the row count.
     */
    abstract int insertionIndex(GridSelection selection);

    @Override
    protected final void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final int rowIndex = insertionIndex(selection);
        final int columnIndex = Math.max(selection.anchorColumnIndex(), 0);
        final boolean applied = context.executeEdit(
                () -> context.getDatasetDocument().insertBlankRow(selection.tableKey(), rowIndex));
        if (applied)
        {
            context.selectRegion(columnIndex, rowIndex, 1, 1);
            reportNewFirstRow(context, selection.tableKey(), rowIndex);
        }
    }

    @Override
    protected final boolean isEnabledFor(final GridSelection selection)
    {
        return selection.tableKey() != null && selection.columnCount() > 0;
    }

    /**
     * Tells the user, on the status line, that the blank row has an empty string in more than its first
     * column, which it has when it became the first row of a table whose columns dbUnit takes from its
     * first row.
     */
    private static void reportNewFirstRow(final DatasetGridContext context, final String tableKey,
            final int rowIndex)
    {
        final Optional<DatasetTable> table = context.getDatasetDocument().getModel().findTable(tableKey);
        if (table.isEmpty() || rowIndex >= table.get().getRows().size())
        {
            return;
        }
        final DatasetRow row = table.get().getRows().get(rowIndex);
        if (valueCount(row) > 1)
        {
            context.setStatusMessage(Messages.InsertRow_newFirstRow);
        }
    }

    private static int valueCount(final DatasetRow row)
    {
        int count = 0;
        for (final String value : row.getValues())
        {
            if (value != null)
            {
                count++;
            }
        }
        return count;
    }
}
