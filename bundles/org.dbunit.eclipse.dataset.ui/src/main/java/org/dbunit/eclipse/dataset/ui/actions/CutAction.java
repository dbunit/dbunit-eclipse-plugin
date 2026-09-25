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
import org.eclipse.swt.widgets.Text;

/**
 * Copies the selection, then sets it to NULL; when the selection consists of whole rows, deletes those
 * rows instead, since a row cannot be all NULL and cutting rows to move them is the common intent.
 *
 * @since 1.0.0
 */
public final class CutAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public CutAction(final DatasetGridContext context)
    {
        super(context);
        setText("Cut");
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        CopyAction.copySelectedCellsToClipboard(context);
        if (selection.wholeRowsSelected())
        {
            final int[] rowIndexes = selection.rowIndexes().stream().mapToInt(Integer::intValue).toArray();
            context.executeEdit(
                    () -> context.getDatasetDocument().deleteRows(selection.tableKey(), rowIndexes));
        }
        else
        {
            SetNullAction.setSelectedCellsToNull(context);
        }
    }

    @Override
    protected boolean isEnabledWhileEditing()
    {
        return true;
    }

    @Override
    protected void runWhileEditing(final DatasetGridContext context)
    {
        final Text text = context.getActiveCellEditorText();
        if (text != null)
        {
            text.cut();
        }
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return !selection.rowIndexes().isEmpty() && !selection.columnIndexes().isEmpty();
    }
}
