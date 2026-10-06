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

import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;

/**
 * Moves a contiguous block of selected rows by one position and keeps it selected.
 *
 * @since 1.0.0
 */
abstract class MoveRowsAction extends GridAction
{
    /**
     * The change of position that moves a block of rows up.
     */
    static final int UP = -1;

    /**
     * The change of position that moves a block of rows down.
     */
    static final int DOWN = 1;

    private final int delta;

    /**
     * Creates the action.
     *
     * @param commandId The command id of the action.
     * @param context What this action needs from the page that hosts the grid.
     * @param delta The number of positions that the block moves, {@link #UP} or {@link #DOWN}.
     */
    MoveRowsAction(final String commandId, final DatasetGridContext context, final int delta)
    {
        super(commandId, context);
        this.delta = delta;
    }

    @Override
    protected final void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final int rowCount = selection.rowIndexes().size();
        final int newFirstRowIndex = selection.firstRowIndex() + delta;
        final boolean applied = context.executeEdit(() -> context.getDatasetDocument()
                .moveRows(selection.tableKey(), selection.firstRowIndex(), rowCount, delta));
        if (applied)
        {
            context.selectRegion(selection.firstColumnIndex(), newFirstRowIndex, selection.columnSpan(),
                    rowCount);
        }
    }
}
