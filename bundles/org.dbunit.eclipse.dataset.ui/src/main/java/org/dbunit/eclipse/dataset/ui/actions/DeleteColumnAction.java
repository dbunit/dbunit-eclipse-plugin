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

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

/**
 * Confirms, then deletes the anchor column, naming it and the number of values that will be removed.
 *
 * @since 1.0.0
 */
public class DeleteColumnAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public DeleteColumnAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.DELETE_COLUMN, context);
        setText("Delete Column");
        setImageDescriptor(DatasetImages.getImageDescriptor(DatasetImages.IMG_DELETE_COLUMN));
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final DatasetTable table =
                context.getDatasetDocument().getModel().findTable(selection.tableKey()).orElseThrow();
        final DatasetColumn column = table.getColumns().get(selection.anchorColumnIndex());
        final int valueCount = countValues(table, selection.anchorColumnIndex());
        if (!confirmDelete(context.getShell(), column, valueCount))
        {
            return;
        }
        context.executeEdit(
                () -> context.getDatasetDocument().deleteColumn(selection.tableKey(), column.name()));
    }

    /**
     * Opens the dialog that confirms the deletion.
     *
     * @param shell The shell to parent the dialog on.
     * @param column The column that would be deleted.
     * @param valueCount The number of values that would be removed.
     * @return True when the user confirmed the deletion.
     */
    boolean confirmDelete(final Shell shell, final DatasetColumn column, final int valueCount)
    {
        final StringBuilder message = new StringBuilder();
        message.append("Delete column '").append(column.name()).append("'? This removes ")
                .append(valueCount).append(valueCount == 1 ? " value." : " values.");
        if (column.declared())
        {
            message.append(" dbUnit reads columns from the DTD, so the DTD must be updated too.");
        }
        return MessageDialog.openConfirm(shell, "Delete Column", message.toString());
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return selection.tableKey() != null && selection.anchorColumnIndex() >= 0
                && selection.anchorColumnIndex() < selection.columnCount();
    }

    private static int countValues(final DatasetTable table, final int columnIndex)
    {
        int count = 0;
        for (final DatasetRow row : table.getRows())
        {
            if (row.getValue(columnIndex) != null)
            {
                count++;
            }
        }
        return count;
    }
}
