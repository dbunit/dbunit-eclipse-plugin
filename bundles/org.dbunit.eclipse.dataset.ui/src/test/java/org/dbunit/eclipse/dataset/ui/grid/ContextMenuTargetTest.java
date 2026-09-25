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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.coordinate.PositionCoordinate;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.grid.layer.DefaultGridLayer;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectRegionCommand;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link ContextMenuTarget} selects what a right-click hits unless it is already selected, and
 * finds the region of any point of a grid, also beyond its cells.
 */
class ContextMenuTargetTest
{
    private static final int COLUMNS = 3;

    private static final int ROWS = 4;

    private Shell shell;

    private NatTable natTable;

    private SelectionLayer selectionLayer;

    @BeforeEach
    void createGrid()
    {
        shell = new Shell(Display.getDefault());
        shell.setLayout(new FillLayout());
        shell.setSize(600, 400);
        final DefaultGridLayer gridLayer = new DefaultGridLayer(new CellNames(), new ColumnNames());
        selectionLayer = gridLayer.getBodyLayer().getSelectionLayer();
        natTable = new NatTable(shell, gridLayer);
        shell.open();
        final Display display = Display.getCurrent();
        while (display.readAndDispatch())
        {
            // Let NatTable lay out, so that points map to cells.
        }
    }

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testRun_onACellOutsideTheSelection_selectsOnlyThatCell()
    {
        selectionLayer.setSelectedCell(1, 1);

        new ContextMenuTarget(selectionLayer).run(natTable, rightClick(cellX(0), cellY(2)));

        assertThat(selectedCells()).as("A right-click must select the cell it hits.")
                .containsExactly(List.of(0, 2));
    }

    @Test
    void testRun_onACellInsideTheSelection_keepsTheSelection()
    {
        selectionLayer.doCommand(new SelectRegionCommand(selectionLayer, 0, 1, COLUMNS, 2, false, false));
        final List<List<Integer>> selectionBefore = selectedCells();

        new ContextMenuTarget(selectionLayer).run(natTable, rightClick(cellX(1), cellY(2)));

        assertThat(selectedCells())
                .as("A right-click inside the selection must keep it, for a command to act on all of it.")
                .isEqualTo(selectionBefore);
    }

    @Test
    void testRun_onAColumnHeader_selectsThatColumn()
    {
        selectionLayer.setSelectedCell(0, 0);

        new ContextMenuTarget(selectionLayer).run(natTable, rightClick(cellX(2), headerY()));

        assertThat(selectionLayer.isColumnPositionFullySelected(2))
                .as("A right-click on a column header must select that column.").isTrue();
        assertThat(selectionLayer.getSelectionAnchor().columnPosition)
                .as("The column must hold the selection anchor, which the column commands act on.")
                .isEqualTo(2);
    }

    @Test
    void testRun_onARowHeader_selectsThatRow()
    {
        selectionLayer.setSelectedCell(0, 0);

        new ContextMenuTarget(selectionLayer).run(natTable, rightClick(headerX(), cellY(3)));

        assertThat(selectionLayer.getFullySelectedRowPositions())
                .as("A right-click on a row header must select that row instead of the previous selection.")
                .containsExactly(3);
    }

    @Test
    void testRegionAt_ofEachPartOfTheGrid_namesItsRegionAndTheBodyBeyondTheCells()
    {
        final Rectangle clientArea = natTable.getClientArea();

        final List<String> regions = List.of(ContextMenuTarget.regionAt(natTable, cellX(1), cellY(1)),
                ContextMenuTarget.regionAt(natTable, cellX(1), headerY()),
                ContextMenuTarget.regionAt(natTable, headerX(), cellY(1)),
                ContextMenuTarget.regionAt(natTable, clientArea.width - 2, clientArea.height - 2));

        assertThat(regions)
                .as("Each point must name its region, and the empty area beyond the cells the body.")
                .containsExactly(GridRegion.BODY, GridRegion.COLUMN_HEADER, GridRegion.ROW_HEADER,
                        GridRegion.BODY);
    }

    private int cellX(final int column)
    {
        final int position = column + 1;
        return natTable.getStartXOfColumnPosition(position) + natTable.getColumnWidthByPosition(position) / 2;
    }

    private int cellY(final int row)
    {
        final int position = row + 1;
        return natTable.getStartYOfRowPosition(position) + natTable.getRowHeightByPosition(position) / 2;
    }

    private int headerX()
    {
        return natTable.getColumnWidthByPosition(0) / 2;
    }

    private int headerY()
    {
        return natTable.getRowHeightByPosition(0) / 2;
    }

    private MouseEvent rightClick(final int x, final int y)
    {
        final Event event = new Event();
        event.widget = natTable;
        event.x = x;
        event.y = y;
        event.button = 3;
        return new MouseEvent(event);
    }

    private List<List<Integer>> selectedCells()
    {
        final List<List<Integer>> cells = new ArrayList<>();
        for (final PositionCoordinate position : selectionLayer.getSelectedCellPositions())
        {
            cells.add(List.of(position.columnPosition, position.rowPosition));
        }
        return cells;
    }

    private static final class CellNames implements IDataProvider
    {
        @Override
        public Object getDataValue(final int columnIndex, final int rowIndex)
        {
            return columnIndex + "," + rowIndex;
        }

        @Override
        public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getColumnCount()
        {
            return COLUMNS;
        }

        @Override
        public int getRowCount()
        {
            return ROWS;
        }
    }

    private static final class ColumnNames implements IDataProvider
    {
        @Override
        public Object getDataValue(final int columnIndex, final int rowIndex)
        {
            return "C" + columnIndex;
        }

        @Override
        public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getColumnCount()
        {
            return COLUMNS;
        }

        @Override
        public int getRowCount()
        {
            return 1;
        }
    }
}
