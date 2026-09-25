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
import org.eclipse.jface.action.Action;

/**
 * A command that acts on the Tables page's active grid: reaches it through a {@link DatasetGridContext},
 * updates its enablement from a {@link GridSelection}, and by default needs an editable page and does
 * nothing while a cell editor is active.
 *
 * @since 1.0.0
 */
public abstract class GridAction extends Action
{
    private final DatasetGridContext context;

    /**
     * Creates a grid action bound to a command id.
     *
     * @param commandId The command id to report through {@code getActionDefinitionId()}.
     * @param context What this action needs from the page that hosts the grid.
     */
    protected GridAction(final String commandId, final DatasetGridContext context)
    {
        this.context = context;
        setActionDefinitionId(commandId);
    }

    /**
     * Creates a grid action with no command id, for one installed as a global retargetable action instead
     * of a workbench command.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    protected GridAction(final DatasetGridContext context)
    {
        this.context = context;
    }

    @Override
    public final void run()
    {
        if (context.hasActiveCellEditor())
        {
            if (isEnabledWhileEditing())
            {
                runWhileEditing(context);
            }
            return;
        }
        runOnGrid(context);
    }

    /**
     * Refreshes this action's enablement from the page's editable state and the active grid's selection.
     *
     * @param selection The active grid's current selection.
     */
    public final void update(final GridSelection selection)
    {
        final boolean pageAllowsThis = context.isEditable() || !changesDataset();
        final boolean editingAllowsThis = !context.hasActiveCellEditor() || isEnabledWhileEditing();
        setEnabled(pageAllowsThis && editingAllowsThis && isEnabledFor(selection));
    }

    /**
     * Runs this command's edit on the active grid.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    protected abstract void runOnGrid(DatasetGridContext context);

    /**
     * Returns whether this command applies to a selection, with the page's editable state and the active
     * cell editor already accounted for.
     *
     * @param selection The active grid's current selection.
     * @return True when this command should be enabled.
     */
    protected abstract boolean isEnabledFor(GridSelection selection);

    /**
     * Returns whether this command changes the dataset, and so needs an editable page.
     *
     * @return True by default, and false for a command that only reads or selects cells.
     */
    protected boolean changesDataset()
    {
        return true;
    }

    /**
     * Returns whether this command stays enabled and dispatches to {@link #runWhileEditing} while a cell
     * editor is active, instead of doing nothing.
     *
     * @return True when this command has an editing-mode behavior; false by default.
     */
    protected boolean isEnabledWhileEditing()
    {
        return false;
    }

    /**
     * Runs this command's fallback while a cell editor is active, for a command whose
     * {@link #isEnabledWhileEditing()} returns true. The default does nothing.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    protected void runWhileEditing(final DatasetGridContext context)
    {
    }
}
