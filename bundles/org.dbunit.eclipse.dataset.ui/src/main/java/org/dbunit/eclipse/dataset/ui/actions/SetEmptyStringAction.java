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
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.swt.graphics.Point;

/**
 * Sets every selected cell to the empty string.
 *
 * @since 1.0.0
 */
public final class SetEmptyStringAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public SetEmptyStringAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.SET_EMPTY_STRING, context);
        setText("Set to Empty String");
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final DatasetTable table =
                context.getDatasetDocument().getModel().findTable(selection.tableKey()).orElseThrow();
        final List<CellChange> changes = new ArrayList<>();
        for (final Point cell : context.getSelectedCellPositions())
        {
            changes.add(new CellChange(cell.y, table.getColumns().get(cell.x).name(), ""));
        }
        context.executeEdit(() -> context.getDatasetDocument().setCells(selection.tableKey(), changes));
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return !selection.rowIndexes().isEmpty() && !selection.columnIndexes().isEmpty();
    }
}
