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
package org.dbunit.eclipse.dataset.ui.grid;

import org.eclipse.nebula.widgets.nattable.edit.editor.MultiLineTextCellEditor;
import org.eclipse.nebula.widgets.nattable.widget.EditModeEnum;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/**
 * The multi-line text editor that a {@link DatasetGrid} opens in a dialog. NatTable does not report a dialog's
 * editor as the active cell editor, so this editor remembers the dialog's shell, which lets the grid close the
 * dialog without a value when the table changes under it.
 *
 * @since 1.0.0
 */
final class DialogTextCellEditor extends MultiLineTextCellEditor
{
    private Shell dialogShell;

    DialogTextCellEditor()
    {
        super(false);
    }

    @Override
    protected Control activateCell(final Composite parent, final Object originalCanonicalValue)
    {
        final Control control = super.activateCell(parent, originalCanonicalValue);
        if (editMode == EditModeEnum.DIALOG)
        {
            dialogShell = parent.getShell();
        }
        return control;
    }

    /**
     * Tells whether this editor is open in a dialog.
     *
     * @return True while the dialog that this editor was last activated in is showing.
     */
    boolean isDialogOpen()
    {
        return dialogShell != null && !dialogShell.isDisposed();
    }

    /**
     * Closes the dialog that this editor is open in, as the dialog's Cancel button does, so that the dialog's
     * value is not written. Does nothing when no dialog is open.
     */
    void cancelDialog()
    {
        final Shell shell = dialogShell;
        if (shell != null && !shell.isDisposed())
        {
            close();
            shell.close();
        }
    }
}
