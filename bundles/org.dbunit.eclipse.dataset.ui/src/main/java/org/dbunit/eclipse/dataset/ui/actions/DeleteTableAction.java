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

import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;

/**
 * Confirms, then deletes the active table, naming it and the number of rows that will be removed; the
 * table's tab is removed by the normal reconciliation that follows the edit.
 *
 * @since 1.0.0
 */
public class DeleteTableAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public DeleteTableAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.DELETE_TABLE, context);
        setText(Messages.Action_deleteTable);
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final DatasetTable table = context.getDatasetDocument().getModel().findTable(selection.tableKey())
                .orElseThrow(() -> new DatasetEditException(
                        NLS.bind(Messages.Edit_noSuchTable, selection.tableKey())));
        if (!confirmDelete(context.getShell(), table))
        {
            return;
        }
        context.executeEdit(() -> context.getDatasetDocument().deleteTable(selection.tableKey()));
    }

    /**
     * Opens the dialog that confirms the deletion.
     *
     * @param shell The shell to parent the dialog on.
     * @param table The table that would be deleted.
     * @return True when the user confirmed the deletion.
     */
    boolean confirmDelete(final Shell shell, final DatasetTable table)
    {
        return MessageDialog.openConfirm(shell, Messages.Action_deleteTable, confirmMessage(table));
    }

    /**
     * Builds the confirmation message, warning that dbUnit reads tables from the DTD when the table
     * exists only there.
     *
     * @param table The table that would be deleted.
     * @return The confirmation message.
     */
    static String confirmMessage(final DatasetTable table)
    {
        final int rowCount = table.getRows().size();
        final String deleteMessage = rowCount == 1
                ? NLS.bind(Messages.DeleteTableDialog_messageOneRow, table.getName())
                : NLS.bind(Messages.DeleteTableDialog_message, table.getName(), rowCount);
        return table.isDeclaredOnly() ? deleteMessage + ' ' + Messages.DeleteTableDialog_dtdNote
                : deleteMessage;
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return selection.tableKey() != null;
    }
}
