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

import org.eclipse.nebula.widgets.nattable.config.AbstractUiBindingConfiguration;
import org.eclipse.nebula.widgets.nattable.edit.action.MouseEditAction;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.ui.binding.UiBindingRegistry;
import org.eclipse.nebula.widgets.nattable.ui.matcher.CellEditorMouseEventMatcher;
import org.eclipse.nebula.widgets.nattable.ui.matcher.KeyEventMatcher;
import org.eclipse.nebula.widgets.nattable.ui.matcher.MouseEventMatcher;
import org.eclipse.swt.SWT;

/**
 * Replaces NatTable's default single-click editing with spreadsheet-style editing: F2 or a typed character
 * edits the selection anchor, and a double-click edits the clicked cell. A right-click selects the cell,
 * row, or column it hits, unless it is already selected, before the context menu opens.
 *
 * @since 1.0.0
 */
final class SpreadsheetEditBindings extends AbstractUiBindingConfiguration
{
    private final SelectionLayer selectionLayer;

    SpreadsheetEditBindings(final SelectionLayer selectionLayer)
    {
        this.selectionLayer = selectionLayer;
    }

    @Override
    public void configureUiBindings(final UiBindingRegistry uiBindingRegistry)
    {
        final AnchorCellKeyEditAction editAnchorCell = new AnchorCellKeyEditAction(selectionLayer);
        uiBindingRegistry.registerKeyBinding(new KeyEventMatcher(SWT.NONE, SWT.F2), editAnchorCell);
        uiBindingRegistry.registerKeyBinding(new PrintableCharacterKeyEventMatcher(), editAnchorCell);
        uiBindingRegistry.registerDoubleClickBinding(new CellEditorMouseEventMatcher(GridRegion.BODY),
                new MouseEditAction());
        final ContextMenuTarget contextMenuTarget = new ContextMenuTarget(selectionLayer);
        uiBindingRegistry.registerMouseDownBinding(MouseEventMatcher.bodyRightClick(SWT.NONE),
                contextMenuTarget);
        uiBindingRegistry.registerMouseDownBinding(MouseEventMatcher.rowHeaderRightClick(SWT.NONE),
                contextMenuTarget);
        uiBindingRegistry.registerMouseDownBinding(MouseEventMatcher.columnHeaderRightClick(SWT.NONE),
                contextMenuTarget);
    }
}
