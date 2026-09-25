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

import org.eclipse.nebula.widgets.nattable.coordinate.PositionCoordinate;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectAllCommand;
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
        final SelectionLayer selectionLayer = new SelectionLayer(new DataLayer(new FixedSizeDataProvider(
                rowCount, columnCount)))
        {
            @Override
            public PositionCoordinate[] getSelectedCellPositions()
            {
                throw new AssertionError(
                        "GridSelection must compute bounds and row indexes without calling "
                                + "getSelectedCellPositions().");
            }
        };
        selectionLayer.doCommand(new SelectAllCommand());

        final GridSelection selection = GridSelection.compute("USERS", columnCount, selectionLayer);

        assertThat(selection.rowIndexes()).as("Select All must select every row.").hasSize(rowCount);
        assertThat(selection.firstRowIndex()).as("The first selected row must be 0.").isZero();
        assertThat(selection.lastRowIndex()).as("The last selected row must be the last row index.")
                .isEqualTo(rowCount - 1);
        assertThat(selection.firstColumnIndex()).as("The first selected column must be 0.").isZero();
        assertThat(selection.lastColumnIndex()).as("The last selected column must be the last column index.")
                .isEqualTo(columnCount - 1);
        assertThat(selection.wholeRowsSelected()).as("Select All must select every row in full.").isTrue();
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
