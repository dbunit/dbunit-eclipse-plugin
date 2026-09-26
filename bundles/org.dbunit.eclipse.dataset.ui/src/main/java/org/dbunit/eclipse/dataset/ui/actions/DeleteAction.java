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
import org.eclipse.swt.widgets.Text;

/**
 * The global Delete action: sets the selected cells to NULL, or, while a cell editor is active, deletes
 * the editor's text selection or the character after the caret.
 *
 * @since 1.0.0
 */
public final class DeleteAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public DeleteAction(final DatasetGridContext context)
    {
        super(context);
        setText(Messages.Action_delete);
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        SetNullAction.setSelectedCellsToNull(context);
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
        if (text == null)
        {
            return;
        }
        if (text.getSelectionCount() > 0)
        {
            text.insert("");
            return;
        }
        final int caretPosition = text.getCaretPosition();
        if (caretPosition < text.getCharCount())
        {
            text.setSelection(caretPosition, caretPosition + 1);
            text.insert("");
        }
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return !selection.rowIndexes().isEmpty() && !selection.columnIndexes().isEmpty();
    }
}
