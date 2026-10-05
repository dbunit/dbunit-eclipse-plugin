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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;

/**
 * Plans where the rows of pasted text go in a table: the new values of the cells of the existing rows, and
 * the rows to append after the last row. A pasted value in a column beyond the table's last column has no
 * cell to go to, so the plan leaves it out. A row to append that has no value, such as a blank line, cannot
 * be a row of a flat XML dataset, so the plan skips it.
 *
 * @param changes The new values of the cells of the existing rows; a null value makes a cell NULL.
 * @param appendedRows The rows to append after the last row, each with one value for every column of the
 *                     table, in column order, and with at least one value that is not null; a null value is
 *                     a column the new row has no value for.
 * @param pastedRowCount The number of rows of the pasted text that the paste writes, as changes to existing
 *                       rows or as appended rows.
 * @param pastedColumnCount The number of columns of the pasted text.
 * @param skippedRowCount The number of rows to append that the plan skipped because they have no value.
 * @param ignoredColumnCount The number of pasted columns beyond the table's last column.
 */
record PastePlan(List<CellChange> changes, List<List<String>> appendedRows, int pastedRowCount,
        int pastedColumnCount, int skippedRowCount, int ignoredColumnCount)
{
    /**
     * Creates a plan with its own unmodifiable copies of the changes and of the appended rows, so that
     * neither the caller that built them nor a caller of the accessors can change the plan.
     */
    PastePlan
    {
        changes = List.copyOf(changes);
        appendedRows = List.copyOf(appendedRows);
    }

    /**
     * Plans a paste of parsed text into a table.
     *
     * @param table The table to paste into.
     * @param anchorRowIndex The index of the row that the first pasted row goes to; the rows from the row
     *                       count on are appended.
     * @param anchorColumnIndex The index of the column that the first pasted column goes to.
     * @param parsedRows The rows of pasted values, all of the same width, at least one.
     * @return The plan.
     */
    static PastePlan of(final DatasetTable table, final int anchorRowIndex, final int anchorColumnIndex,
            final List<List<String>> parsedRows)
    {
        final int rowCount = table.getRows().size();
        final int columnCount = table.getColumns().size();
        final int pastedColumnCount = parsedRows.get(0).size();
        final int ignoredColumnCount = Math.max(0, anchorColumnIndex + pastedColumnCount - columnCount);
        final List<CellChange> changes = new ArrayList<>();
        final List<List<String>> appendedRows = new ArrayList<>();
        int skippedRowCount = 0;
        for (int sourceRowIndex = 0; sourceRowIndex < parsedRows.size(); sourceRowIndex++)
        {
            final int targetRowIndex = anchorRowIndex + sourceRowIndex;
            final List<String> sourceRow = parsedRows.get(sourceRowIndex);
            if (targetRowIndex < rowCount)
            {
                addExistingRowChanges(table, changes, targetRowIndex, anchorColumnIndex, sourceRow);
            }
            else
            {
                final List<String> appendedRow = buildAppendedRow(anchorColumnIndex, columnCount, sourceRow);
                if (hasValue(appendedRow))
                {
                    appendedRows.add(appendedRow);
                }
                else
                {
                    skippedRowCount++;
                }
            }
        }
        final int pastedRowCount = parsedRows.size() - skippedRowCount;
        return new PastePlan(changes, appendedRows, pastedRowCount, pastedColumnCount, skippedRowCount,
                ignoredColumnCount);
    }

    private static void addExistingRowChanges(final DatasetTable table, final List<CellChange> changes,
            final int targetRowIndex, final int anchorColumnIndex, final List<String> sourceRow)
    {
        final int columnCount = table.getColumns().size();
        for (int sourceColumnIndex = 0; sourceColumnIndex < sourceRow.size(); sourceColumnIndex++)
        {
            final int targetColumnIndex = anchorColumnIndex + sourceColumnIndex;
            if (targetColumnIndex >= columnCount)
            {
                continue;
            }
            final String value = sourceRow.get(sourceColumnIndex);
            changes.add(new CellChange(targetRowIndex, table.getColumns().get(targetColumnIndex).name(),
                    value));
        }
    }

    private static List<String> buildAppendedRow(final int anchorColumnIndex, final int columnCount,
            final List<String> sourceRow)
    {
        final List<String> newRow = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++)
        {
            final int sourceColumnIndex = columnIndex - anchorColumnIndex;
            final String value = sourceColumnIndex >= 0 && sourceColumnIndex < sourceRow.size()
                    ? sourceRow.get(sourceColumnIndex) : null;
            newRow.add(value);
        }
        return newRow;
    }

    private static boolean hasValue(final List<String> row)
    {
        return row.stream().anyMatch(Objects::nonNull);
    }
}
