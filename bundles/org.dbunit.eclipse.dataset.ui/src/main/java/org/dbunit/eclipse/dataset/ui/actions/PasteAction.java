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

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.tsv.TabSeparatedValues;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Text;

/**
 * Pastes tab-separated text from the clipboard at the selection's top-left cell: a single value fills
 * every selected cell; otherwise the block is pasted at the anchor, with columns beyond the last column
 * ignored and rows beyond the last row appended, all as one undo step.
 *
 * @since 1.0.0
 */
public final class PasteAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public PasteAction(final DatasetGridContext context)
    {
        super(context);
        setText("Paste");
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        final String text = context.readClipboardText();
        if (text == null || text.isEmpty())
        {
            return;
        }
        final List<List<String>> parsedRows = TabSeparatedValues.parse(text);
        if (parsedRows.isEmpty())
        {
            return;
        }
        final GridSelection selection = context.getSelection();
        final DatasetTable table =
                context.getDatasetDocument().getModel().findTable(selection.tableKey()).orElseThrow();
        final List<Point> selectedCells = context.getSelectedCellPositions();
        if (parsedRows.size() == 1 && parsedRows.get(0).size() == 1 && selectedCells.size() > 1)
        {
            fillSelection(context, selection, table, selectedCells, parsedRows.get(0).get(0));
            return;
        }
        pasteBlock(context, selection, table, parsedRows);
    }

    private void fillSelection(final DatasetGridContext context, final GridSelection selection,
            final DatasetTable table, final List<Point> selectedCells, final String value)
    {
        final List<CellChange> changes = new ArrayList<>();
        for (final Point cell : selectedCells)
        {
            changes.add(new CellChange(cell.y, table.getColumns().get(cell.x).name(), value));
        }
        context.executeMultiCellEdit("Paste",
                () -> context.getDatasetDocument().setCells(selection.tableKey(), changes));
    }

    private void pasteBlock(final DatasetGridContext context, final GridSelection selection,
            final DatasetTable table, final List<List<String>> parsedRows)
    {
        final int anchorRowIndex = selection.firstRowIndex();
        final int anchorColumnIndex = selection.firstColumnIndex();
        if (anchorRowIndex < 0 || anchorColumnIndex < 0)
        {
            return;
        }
        final int columnCount = table.getColumns().size();
        final int rowCount = table.getRows().size();
        final int pastedColumnCount = parsedRows.get(0).size();
        final int ignoredColumnCount = Math.max(0, anchorColumnIndex + pastedColumnCount - columnCount);
        final List<CellChange> changes = new ArrayList<>();
        final List<List<String>> appendedRows = new ArrayList<>();
        for (int sourceRowIndex = 0; sourceRowIndex < parsedRows.size(); sourceRowIndex++)
        {
            final int targetRowIndex = anchorRowIndex + sourceRowIndex;
            final List<String> sourceRow = parsedRows.get(sourceRowIndex);
            if (targetRowIndex < rowCount)
            {
                addExistingRowChanges(table, changes, targetRowIndex, anchorColumnIndex, columnCount,
                        sourceRow);
            }
            else
            {
                appendedRows.add(buildAppendedRow(anchorColumnIndex, columnCount, sourceRow));
            }
        }
        final boolean edited = context.executeMultiCellEdit("Paste", () -> context.getDatasetDocument()
                .batch(() ->
                {
                    if (!changes.isEmpty())
                    {
                        context.getDatasetDocument().setCells(selection.tableKey(), changes);
                    }
                    if (!appendedRows.isEmpty())
                    {
                        context.getDatasetDocument().insertRows(selection.tableKey(), rowCount,
                                appendedRows);
                    }
                }));
        if (!edited)
        {
            return;
        }
        context.selectRegion(anchorColumnIndex, anchorRowIndex, pastedColumnCount, parsedRows.size());
        if (ignoredColumnCount > 0)
        {
            final String columnWord = ignoredColumnCount == 1 ? "column" : "columns";
            context.setStatusMessage(
                    "Ignored " + ignoredColumnCount + " pasted " + columnWord + " beyond the table's last "
                            + "column.");
        }
    }

    private static void addExistingRowChanges(final DatasetTable table, final List<CellChange> changes,
            final int targetRowIndex, final int anchorColumnIndex, final int columnCount,
            final List<String> sourceRow)
    {
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

    @Override
    protected boolean isEnabledWhileEditing()
    {
        return true;
    }

    @Override
    protected void runWhileEditing(final DatasetGridContext context)
    {
        final Text text = context.getActiveCellEditorText();
        if (text != null)
        {
            text.paste();
        }
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return !selection.rowIndexes().isEmpty() && !selection.columnIndexes().isEmpty();
    }
}
