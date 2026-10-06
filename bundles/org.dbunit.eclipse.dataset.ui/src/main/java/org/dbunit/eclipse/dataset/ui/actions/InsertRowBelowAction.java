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

import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;

/**
 * Inserts a blank row below the selection anchor and selects it. With no anchor, it goes after the last row.
 *
 * @since 1.0.0
 */
public final class InsertRowBelowAction extends InsertRowAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public InsertRowBelowAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.INSERT_ROW_BELOW, context);
        setText(Messages.Action_insertRowBelow);
        setImageDescriptor(DatasetImages.getImageDescriptor(DatasetImages.IMG_INSERT_ROW_BELOW));
    }

    @Override
    int insertionIndex(final GridSelection selection)
    {
        if (selection.anchorRowIndex() < 0)
        {
            return selection.rowCount();
        }
        return selection.anchorRowIndex() + 1;
    }
}
