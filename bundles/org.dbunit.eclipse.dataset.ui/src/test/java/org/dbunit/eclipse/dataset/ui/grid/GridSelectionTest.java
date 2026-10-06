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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.nebula.widgets.nattable.coordinate.PositionCoordinate;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectAllCommand;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectCellCommand;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectRowsCommand;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link GridSelection} against the Grid specification's rule that it must never call
 * {@code SelectionLayer.getSelectedCellPositions()}, whose cost grows with the number of selected cells.
 */
class GridSelectionTest
{
    @Test
    void testCompute_ofSelectAllOnA100000RowTable_hasTheRightBoundsAndRowIndexes()
    {
        final int rowCount = 100_000;
        final int columnCount = 3;
        final SelectionLayer selectionLayer = selectionLayerThatRefusesToListCells(rowCount, columnCount);
        selectionLayer.doCommand(new SelectAllCommand());

        final GridSelection selection =
                GridSelection.compute("USERS", rowCount, columnCount, selectionLayer);

        assertThat(selection.rowIndexes()).as("Select All must select every row.").hasSize(rowCount);
        assertThat(selection.firstRowIndex()).as("The first selected row must be 0.").isZero();
        assertThat(selection.lastRowIndex()).as("The last selected row must be the last row index.")
                .isEqualTo(rowCount - 1);
        assertThat(selection.firstColumnIndex()).as("The first selected column must be 0.").isZero();
        assertThat(selection.lastColumnIndex()).as("The last selected column must be the last column index.")
                .isEqualTo(columnCount - 1);
        assertThat(selection.wholeRowsSelected()).as("Select All must select every row in full.").isTrue();
    }

    @Test
    void testCompute_ofCellsSelectedSeparately_listsEveryTouchedRowAndColumn()
    {
        final SelectionLayer selectionLayer =
                new SelectionLayer(new DataLayer(new FixedSizeDataProvider(3, 3)));
        selectionLayer.doCommand(new SelectCellCommand(selectionLayer, 0, 0, false, false));
        selectionLayer.doCommand(new SelectCellCommand(selectionLayer, 1, 2, false, true));

        final GridSelection selection = GridSelection.compute("USERS", 3, 3, selectionLayer);

        assertThat(selection.rowIndexes()).as("Both rows with a selected cell, and not the row between them.")
                .containsExactly(0, 2);
        assertThat(selection.columnIndexes()).as("Both columns with a selected cell.")
                .containsExactly(0, 1);
        assertThat(selection.wholeRowsSelected()).as("Single cells are not whole rows.").isFalse();
    }

    @Test
    void testCompute_ofNonAdjacentRowsSelectedInFull_listsOnlyThoseRowsAndEveryColumn()
    {
        final SelectionLayer selectionLayer =
                new SelectionLayer(new DataLayer(new FixedSizeDataProvider(3, 2)));
        selectionLayer.doCommand(new SelectRowsCommand(selectionLayer, 0, 0, false, false));
        selectionLayer.doCommand(new SelectRowsCommand(selectionLayer, 0, 2, false, true));

        final GridSelection selection = GridSelection.compute("USERS", 3, 2, selectionLayer);

        assertThat(selection.rowIndexes()).as("The row between two rows selected with Ctrl is not selected.")
                .containsExactly(0, 2);
        assertThat(selection.columnIndexes()).as("A selected row has every column selected.")
                .containsExactly(0, 1);
        assertThat(selection.wholeRowsSelected()).as("Both rows are selected in full.").isTrue();
    }

    @Test
    void testHasMultipleCells_whenNothingIsSelected_isFalse()
    {
        final SelectionLayer selectionLayer =
                new SelectionLayer(new DataLayer(new FixedSizeDataProvider(3, 3)));

        final GridSelection selection = GridSelection.compute("USERS", 3, 3, selectionLayer);

        assertThat(selection.hasMultipleCells()).as("No cell is selected, so not several.").isFalse();
        assertThat(selectionLayer.getSelectedCellPositions()).as("NatTable lists no cell either.").isEmpty();
    }

    @Test
    void testHasMultipleCells_whenOneCellIsSelected_isFalse()
    {
        final SelectionLayer selectionLayer =
                new SelectionLayer(new DataLayer(new FixedSizeDataProvider(3, 3)));
        selectionLayer.doCommand(new SelectCellCommand(selectionLayer, 1, 1, false, false));

        final GridSelection selection = GridSelection.compute("USERS", 3, 3, selectionLayer);

        assertThat(selection.hasMultipleCells()).as("One cell is not several.").isFalse();
        assertThat(selectionLayer.getSelectedCellPositions()).as("NatTable lists the one cell.").hasSize(1);
    }

    @Test
    void testHasMultipleCells_whenTwoCellsOfOneColumnAreSelected_isTrue()
    {
        final SelectionLayer selectionLayer =
                new SelectionLayer(new DataLayer(new FixedSizeDataProvider(3, 3)));
        selectionLayer.doCommand(new SelectCellCommand(selectionLayer, 1, 0, false, false));
        selectionLayer.doCommand(new SelectCellCommand(selectionLayer, 1, 2, false, true));

        final GridSelection selection = GridSelection.compute("USERS", 3, 3, selectionLayer);

        assertThat(selection.hasMultipleCells()).as("Two rows of one column are two cells.").isTrue();
        assertThat(selectionLayer.getSelectedCellPositions()).as("NatTable lists the two cells.")
                .hasSize(2);
    }

    @Test
    void testHasMultipleCells_whenTwoCellsOfOneRowAreSelected_isTrue()
    {
        final SelectionLayer selectionLayer =
                new SelectionLayer(new DataLayer(new FixedSizeDataProvider(3, 3)));
        selectionLayer.doCommand(new SelectCellCommand(selectionLayer, 0, 1, false, false));
        selectionLayer.doCommand(new SelectCellCommand(selectionLayer, 2, 1, false, true));

        final GridSelection selection = GridSelection.compute("USERS", 3, 3, selectionLayer);

        assertThat(selection.hasMultipleCells()).as("Two columns of one row are two cells.").isTrue();
        assertThat(selectionLayer.getSelectedCellPositions()).as("NatTable lists the two cells.")
                .hasSize(2);
    }

    @Test
    void testHasMultipleCells_whenARowOfATableWithOneColumnIsSelected_isFalse()
    {
        final SelectionLayer selectionLayer =
                new SelectionLayer(new DataLayer(new FixedSizeDataProvider(3, 1)));
        selectionLayer.doCommand(new SelectRowsCommand(selectionLayer, 0, 1, false, false));

        final GridSelection selection = GridSelection.compute("USERS", 3, 1, selectionLayer);

        assertThat(selection.hasMultipleCells()).as("A whole row of a one-column table is one cell.")
                .isFalse();
        assertThat(selectionLayer.getSelectedCellPositions()).as("NatTable lists the one cell.").hasSize(1);
    }

    @Test
    void testHasMultipleCells_whenARowOfATableWithTwoColumnsIsSelected_isTrue()
    {
        final SelectionLayer selectionLayer =
                new SelectionLayer(new DataLayer(new FixedSizeDataProvider(3, 2)));
        selectionLayer.doCommand(new SelectRowsCommand(selectionLayer, 0, 1, false, false));

        final GridSelection selection = GridSelection.compute("USERS", 3, 2, selectionLayer);

        assertThat(selection.hasMultipleCells()).as("A whole row of a two-column table is two cells.")
                .isTrue();
        assertThat(selectionLayer.getSelectedCellPositions()).as("NatTable lists the two cells.")
                .hasSize(2);
    }

    @Test
    void testHasMultipleCells_ofSelectAllOnA100000RowTable_isTrueWithoutListingTheCells()
    {
        final SelectionLayer selectionLayer = selectionLayerThatRefusesToListCells(100_000, 3);
        selectionLayer.doCommand(new SelectAllCommand());

        final GridSelection selection = GridSelection.compute("USERS", 100_000, 3, selectionLayer);

        assertThat(selection.hasMultipleCells())
                .as("Select All selects a lot of cells, and the answer must not list them.").isTrue();
    }

    @Test
    void testConstructor_whenTheGivenListsChangeAfterwards_keepsTheIndexesItWasGiven()
    {
        final List<Integer> rowIndexes = new ArrayList<>(List.of(1, 2));
        final List<Integer> columnIndexes = new ArrayList<>(List.of(0));
        final GridSelection selection =
                new GridSelection("USERS", 3, 1, 0, 1, rowIndexes, columnIndexes, 1, 2, 0, 0, true);

        rowIndexes.add(3);
        columnIndexes.add(1);

        assertThat(selection).as("A later change to the caller's lists must not change the selection.")
                .isEqualTo(new GridSelection("USERS", 3, 1, 0, 1, List.of(1, 2), List.of(0), 1, 2, 0, 0,
                        true));
    }

    @Test
    void testIndexes_whenAReturnedListIsModified_throws()
    {
        final GridSelection selection = new GridSelection("USERS", 3, 1, 0, 1,
                new ArrayList<>(List.of(1, 2)), new ArrayList<>(List.of(0)), 1, 2, 0, 0, true);

        assertThatThrownBy(() -> selection.rowIndexes().add(3))
                .as("The returned row indexes must not let a caller change the selection.")
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> selection.columnIndexes().add(1))
                .as("The returned column indexes must not let a caller change the selection.")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static SelectionLayer selectionLayerThatRefusesToListCells(final int rowCount,
            final int columnCount)
    {
        return new SelectionLayer(new DataLayer(new FixedSizeDataProvider(rowCount, columnCount)))
        {
            @Override
            public PositionCoordinate[] getSelectedCellPositions()
            {
                throw new AssertionError("GridSelection must compute what it knows without calling "
                        + "getSelectedCellPositions().");
            }
        };
    }

    private static final class FixedSizeDataProvider implements IDataProvider
    {
        private final int rowCount;

        private final int columnCount;

        FixedSizeDataProvider(final int rowCount, final int columnCount)
        {
            this.rowCount = rowCount;
            this.columnCount = columnCount;
        }

        @Override
        public int getColumnCount()
        {
            return columnCount;
        }

        @Override
        public int getRowCount()
        {
            return rowCount;
        }

        @Override
        public Object getDataValue(final int columnIndex, final int rowIndex)
        {
            return null;
        }

        @Override
        public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue)
        {
        }
    }
}
