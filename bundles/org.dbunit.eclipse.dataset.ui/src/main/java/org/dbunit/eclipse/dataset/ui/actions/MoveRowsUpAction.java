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

import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;

/**
 * Moves a contiguous block of selected rows up by one position and keeps it selected.
 *
 * @since 1.0.0
 */
public final class MoveRowsUpAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public MoveRowsUpAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.MOVE_ROWS_UP, context);
        setText(Messages.Action_moveRowsUp);
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final int newFirstRowIndex = selection.firstRowIndex() - 1;
        final boolean applied = context.executeEdit(() -> context.getDatasetDocument().moveRows(
                selection.tableKey(), selection.firstRowIndex(), selection.rowIndexes().size(), -1));
        if (applied)
        {
            context.selectRegion(selection.firstColumnIndex(), newFirstRowIndex, selection.columnSpan(),
                    selection.rowIndexes().size());
        }
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return selection.isContiguousRowSelection() && selection.firstRowIndex() > 0;
    }
}
