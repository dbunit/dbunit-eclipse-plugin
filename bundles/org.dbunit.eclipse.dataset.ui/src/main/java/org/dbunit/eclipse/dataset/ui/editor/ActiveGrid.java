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
package org.dbunit.eclipse.dataset.ui.editor;

import java.util.List;
import java.util.function.Supplier;

import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGrid;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.edit.editor.ICellEditor;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

/**
 * What the Tables page asks of the grid of the selected tab: its selection, the region and cells to select,
 * whether one of its cells is being edited and the text of that editor, and the cell address of its anchor.
 * Without a selected tab the answers are empty and the requests do nothing. All of it belongs to the tab
 * folder, so it may be used on the UI thread only.
 */
final class ActiveGrid
{
    private final CTabFolder tabFolder;

    private final Supplier<DatasetGrid> activeGrid;

    /**
     * Creates the view on the selected tab's grid.
     *
     * @param tabFolder The folder whose selected tab holds the grid.
     * @param activeGrid Returns the grid of the selected tab, or null when no tab is selected.
     */
    ActiveGrid(final CTabFolder tabFolder, final Supplier<DatasetGrid> activeGrid)
    {
        this.tabFolder = tabFolder;
        this.activeGrid = activeGrid;
    }

    boolean hasActiveCellEditor()
    {
        final CTabItem selected = tabFolder.getSelection();
        return selected != null && selected.getControl() instanceof NatTable
                && ((NatTable) selected.getControl()).getActiveCellEditor() != null;
    }

    GridSelection getSelection()
    {
        final DatasetGrid grid = activeGrid.get();
        return grid != null ? grid.getSelection() : GridSelection.NONE;
    }

    void selectRegion(final int firstColumnIndex, final int firstRowIndex, final int columnCount,
            final int rowCount)
    {
        final DatasetGrid grid = activeGrid.get();
        if (grid != null)
        {
            grid.selectRegion(firstColumnIndex, firstRowIndex, columnCount, rowCount);
        }
    }

    Text getActiveCellEditorText()
    {
        final CTabItem selected = tabFolder.getSelection();
        if (selected == null || !(selected.getControl() instanceof NatTable))
        {
            return null;
        }
        final ICellEditor cellEditor = ((NatTable) selected.getControl()).getActiveCellEditor();
        if (cellEditor == null)
        {
            return null;
        }
        final Control editorControl = cellEditor.getEditorControl();
        return editorControl instanceof final Text text ? text : null;
    }

    List<Point> getSelectedCellPositions()
    {
        final DatasetGrid grid = activeGrid.get();
        return grid != null ? grid.getSelectedCellPositions() : List.of();
    }

    void selectAll()
    {
        final DatasetGrid grid = activeGrid.get();
        if (grid != null)
        {
            grid.selectAll();
        }
    }

    void editCellInDialog()
    {
        final DatasetGrid grid = activeGrid.get();
        if (grid != null)
        {
            grid.editCellInDialog();
        }
    }

    CellAddress currentCellAddress()
    {
        final GridSelection selection = getSelection();
        if (selection.tableKey() == null || selection.anchorRowIndex() < 0
                || selection.anchorColumnIndex() < 0)
        {
            return null;
        }
        return new CellAddress(selection.tableKey(), selection.anchorRowIndex(),
                selection.anchorColumnIndex());
    }
}
