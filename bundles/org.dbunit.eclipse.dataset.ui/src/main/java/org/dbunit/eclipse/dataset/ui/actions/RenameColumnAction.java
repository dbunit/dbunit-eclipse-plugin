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

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.dialogs.DatasetNameValidator;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;

/**
 * Prompts for a new name and renames the anchor column.
 *
 * @since 1.0.0
 */
public class RenameColumnAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public RenameColumnAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.RENAME_COLUMN, context);
        setText(Messages.Action_renameColumn);
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final DatasetTable table =
                context.getDatasetDocument().getModel().findTable(selection.tableKey()).orElseThrow();
        final String currentName = table.getColumns().get(selection.anchorColumnIndex()).name();
        final List<String> existingNames = new ArrayList<>();
        for (final DatasetColumn column : table.getColumns())
        {
            if (!column.name().equalsIgnoreCase(currentName))
            {
                existingNames.add(column.name());
            }
        }
        final String newName =
                openNameDialog(context.getShell(), currentName, new DatasetNameValidator(existingNames));
        if (newName == null || newName.equals(currentName))
        {
            return;
        }
        context.executeEdit(() -> context.getDatasetDocument().renameColumn(selection.tableKey(),
                currentName, newName));
    }

    /**
     * Opens the dialog that asks for the column's new name.
     *
     * @param shell The shell to parent the dialog on.
     * @param currentName The column's current name, shown as the dialog's initial value.
     * @param validator The validator for the entered name.
     * @return The entered name, or null when the dialog was cancelled.
     */
    String openNameDialog(final Shell shell, final String currentName, final IInputValidator validator)
    {
        final InputDialog dialog = new InputDialog(shell, Messages.ColumnDialog_renameTitle,
                Messages.ColumnDialog_name, currentName, validator);
        return dialog.open() == Window.OK ? dialog.getValue() : null;
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return selection.tableKey() != null && selection.anchorColumnIndex() >= 0
                && selection.anchorColumnIndex() < selection.columnCount();
    }
}
