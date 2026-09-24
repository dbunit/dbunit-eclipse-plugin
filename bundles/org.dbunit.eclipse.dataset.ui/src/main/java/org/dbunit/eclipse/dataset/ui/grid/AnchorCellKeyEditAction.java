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

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.coordinate.PositionCoordinate;
import org.eclipse.nebula.widgets.nattable.edit.command.EditSelectionCommand;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectCellCommand;
import org.eclipse.nebula.widgets.nattable.ui.action.IKeyAction;
import org.eclipse.swt.events.KeyEvent;

/**
 * Reduces the selection to its anchor cell, then edits that cell: the typed character replaces the value
 * when the key event carries a printable character, otherwise the existing value is kept.
 *
 * @since 1.0.0
 */
final class AnchorCellKeyEditAction implements IKeyAction
{
    private final SelectionLayer selectionLayer;

    AnchorCellKeyEditAction(final SelectionLayer selectionLayer)
    {
        this.selectionLayer = selectionLayer;
    }

    @Override
    public void run(final NatTable natTable, final KeyEvent event)
    {
        final PositionCoordinate anchor = selectionLayer.getSelectionAnchor();
        if (anchor.columnPosition == SelectionLayer.NO_SELECTION
                || anchor.rowPosition == SelectionLayer.NO_SELECTION)
        {
            return;
        }
        natTable.doCommand(new SelectCellCommand(selectionLayer, anchor.columnPosition, anchor.rowPosition,
                false, false));
        if (isPrintable(event.character))
        {
            natTable.doCommand(
                    new EditSelectionCommand(natTable, natTable.getConfigRegistry(), event.character));
        }
        else
        {
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        }
    }

    private static boolean isPrintable(final char character)
    {
        return character != 0 && !Character.isISOControl(character);
    }
}
