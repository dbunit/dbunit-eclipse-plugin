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

import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.dialogs.AddTableDialog;
import org.dbunit.eclipse.dataset.ui.dialogs.DatasetNameValidator;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;

/**
 * Prompts for a name and optional column names, and appends a new table; the new table's tab is selected.
 *
 * @since 1.0.0
 */
public class AddTableAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public AddTableAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.ADD_TABLE, context);
        setText(Messages.Action_addTable);
        setImageDescriptor(DatasetImages.getImageDescriptor(DatasetImages.IMG_ADD_TABLE));
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final List<String> existingNames = new ArrayList<>();
        for (final DatasetTable table : context.getDatasetDocument().getModel().getTables())
        {
            existingNames.add(table.getName());
        }
        final AddTableDialog dialog =
                openDialog(context.getShell(), new DatasetNameValidator(existingNames));
        if (dialog == null)
        {
            return;
        }
        final String tableName = dialog.getTableName();
        final List<String> columnNames = dialog.getColumnNames();
        context.expectNewTableSelected(context.getDatasetDocument().tableKeyOf(tableName));
        context.executeEdit(() -> context.getDatasetDocument().addTable(tableName, columnNames));
    }

    /**
     * Opens the dialog that asks for the new table's name and columns.
     *
     * @param shell The shell to parent the dialog on.
     * @param nameValidator The validator for the entered table name.
     * @return The dialog, holding the entered name and columns, when confirmed; null when cancelled.
     */
    AddTableDialog openDialog(final Shell shell, final DatasetNameValidator nameValidator)
    {
        final AddTableDialog dialog = new AddTableDialog(shell, nameValidator);
        return dialog.open() == Window.OK ? dialog : null;
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return true;
    }
}
