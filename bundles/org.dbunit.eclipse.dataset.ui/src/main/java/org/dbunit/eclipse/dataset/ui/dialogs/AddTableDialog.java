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
package org.dbunit.eclipse.dataset.ui.dialogs;

import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Prompts for a new table's name and an optional comma-separated list of column names.
 *
 * @since 1.0.0
 */
public class AddTableDialog extends Dialog
{
    private final IInputValidator nameValidator;

    private Text nameText;

    private Text columnsText;

    private String tableName;

    private List<String> columnNames = List.of();

    /**
     * Creates the dialog.
     *
     * @param parentShell The shell to parent this dialog on.
     * @param nameValidator The validator for the entered table name.
     */
    public AddTableDialog(final Shell parentShell, final IInputValidator nameValidator)
    {
        super(parentShell);
        this.nameValidator = nameValidator;
    }

    @Override
    protected void configureShell(final Shell newShell)
    {
        super.configureShell(newShell);
        newShell.setText(Messages.TableDialog_addTitle);
    }

    @Override
    protected Control createDialogArea(final Composite parent)
    {
        final Composite composite = (Composite) super.createDialogArea(parent);
        composite.setLayout(new GridLayout(2, false));

        final Label nameLabel = new Label(composite, SWT.NONE);
        nameLabel.setText(Messages.TableDialog_name);
        nameText = new Text(composite, SWT.BORDER);
        nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        final Label columnsLabel = new Label(composite, SWT.NONE);
        columnsLabel.setText(Messages.TableDialog_columnNames);
        columnsLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
        columnsText = new Text(composite, SWT.BORDER);
        columnsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        return composite;
    }

    @Override
    protected void okPressed()
    {
        final String enteredName = nameText.getText().trim();
        final String validationMessage = nameValidator.isValid(enteredName);
        if (validationMessage != null)
        {
            MessageDialog.openError(getShell(), Messages.TableDialog_addTitle, validationMessage);
            return;
        }
        final List<String> enteredColumnNames = parseColumnNames(columnsText.getText());
        final String columnValidationMessage = new ColumnNamesValidator().isValid(enteredColumnNames);
        if (columnValidationMessage != null)
        {
            MessageDialog.openError(getShell(), Messages.TableDialog_addTitle, columnValidationMessage);
            return;
        }
        tableName = enteredName;
        columnNames = enteredColumnNames;
        super.okPressed();
    }

    /**
     * Returns the entered table name.
     *
     * @return The table name; only meaningful after the dialog closes with {@code Window.OK}.
     */
    public String getTableName()
    {
        return tableName;
    }

    /**
     * Returns the entered column names.
     *
     * @return The column names, in the entered order; empty when none were entered.
     */
    public List<String> getColumnNames()
    {
        return columnNames;
    }

    private static List<String> parseColumnNames(final String text)
    {
        final List<String> names = new ArrayList<>();
        for (final String part : text.split(","))
        {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty())
            {
                names.add(trimmed);
            }
        }
        return names;
    }
}
