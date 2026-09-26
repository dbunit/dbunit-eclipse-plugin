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
 * Selects and reveals the anchor cell's range on the Source page, and switches to it.
 *
 * @since 1.0.0
 */
public final class ShowInSourceAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public ShowInSourceAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.SHOW_IN_SOURCE, context);
        setText(Messages.Action_showInSource);
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        context.showInSource();
    }

    @Override
    protected boolean changesDataset()
    {
        return false;
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return selection.anchorColumnIndex() >= 0 && selection.anchorRowIndex() >= 0;
    }
}
