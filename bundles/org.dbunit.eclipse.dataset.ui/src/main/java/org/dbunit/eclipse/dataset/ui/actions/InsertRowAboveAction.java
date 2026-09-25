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

import java.util.Collections;
import java.util.List;

import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;

/**
 * Inserts a blank row above the selection anchor and selects it.
 *
 * @since 1.0.0
 */
public final class InsertRowAboveAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public InsertRowAboveAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.INSERT_ROW_ABOVE, context);
        setText("Insert Row Above");
        setImageDescriptor(DatasetImages.getImageDescriptor(DatasetImages.IMG_INSERT_ROW_ABOVE));
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final int rowIndex = Math.max(selection.anchorRowIndex(), 0);
        final int columnIndex = Math.max(selection.anchorColumnIndex(), 0);
        final List<String> blankRow = Collections.nCopies(selection.columnCount(), null);
        final boolean applied = context.executeEdit(() -> context.getDatasetDocument()
                .insertRows(selection.tableKey(), rowIndex, List.of(blankRow)));
        if (applied)
        {
            context.selectRegion(columnIndex, rowIndex, 1, 1);
        }
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return selection.tableKey() != null && selection.columnCount() > 0;
    }
}
