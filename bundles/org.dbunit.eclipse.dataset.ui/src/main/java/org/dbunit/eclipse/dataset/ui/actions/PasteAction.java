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
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Text;

/**
 * Pastes tab-separated text from the clipboard at the selection's top-left cell, or at column 0, row 0 of
 * a table with no rows yet: a single value fills every selected cell; otherwise the block is pasted at the
 * anchor, with columns beyond the last column ignored and rows beyond the last row appended, all as one
 * undo step. A row to append that would have no value, such as a blank line, is skipped, and the status
 * line says how many columns were ignored and how many rows were skipped. An unquoted empty pasted field
 * becomes NULL; a quoted empty field ({@code ""}) becomes the empty string.
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
        setText(Messages.Action_paste);
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
        final DatasetTable table = context.getDatasetDocument().requireTable(selection.tableKey());
        final boolean singleValue = parsedRows.size() == 1 && parsedRows.get(0).size() == 1;
        if (singleValue && selection.hasMultipleCells())
        {
            fillSelection(context, selection, table, parsedRows.get(0).get(0));
            return;
        }
        pasteBlock(context, selection, table, parsedRows);
    }

    private void fillSelection(final DatasetGridContext context, final GridSelection selection,
            final DatasetTable table, final String value)
    {
        final List<Point> selectedCells = context.getSelectedCellPositions();
        final List<CellChange> changes = new ArrayList<>();
        for (final Point cell : selectedCells)
        {
            changes.add(new CellChange(cell.y, table.getColumns().get(cell.x).name(), value));
        }
        context.executeMultiCellEdit(Messages.Action_paste,
                () -> context.getDatasetDocument().setCells(selection.tableKey(), changes));
    }

    private void pasteBlock(final DatasetGridContext context, final GridSelection selection,
            final DatasetTable table, final List<List<String>> parsedRows)
    {
        final int rowCount = table.getRows().size();
        final boolean noValidAnchor = selection.firstRowIndex() < 0 || selection.firstColumnIndex() < 0;
        if (rowCount > 0 && noValidAnchor)
        {
            return;
        }
        final int anchorRowIndex = Math.max(0, selection.firstRowIndex());
        final int anchorColumnIndex = Math.max(0, selection.firstColumnIndex());
        final PastePlan plan = PastePlan.of(table, anchorRowIndex, anchorColumnIndex, parsedRows);
        if (plan.pastedRowCount() > 0)
        {
            final boolean edited = context.executeMultiCellEdit(Messages.Action_paste,
                    () -> context.getDatasetDocument().setCellsAndAppendRows(selection.tableKey(),
                            plan.changes(), plan.appendedRows()));
            if (!edited)
            {
                return;
            }
            context.selectRegion(anchorColumnIndex, anchorRowIndex, plan.pastedColumnCount(),
                    plan.pastedRowCount());
        }
        reportLeftOut(context, plan);
    }

    private static void reportLeftOut(final DatasetGridContext context, final PastePlan plan)
    {
        final List<String> notes = new ArrayList<>();
        if (plan.ignoredColumnCount() > 0)
        {
            notes.add(countMessage(plan.ignoredColumnCount(), Messages.Paste_ignoredColumn,
                    Messages.Paste_ignoredColumns));
        }
        if (plan.skippedRowCount() > 0)
        {
            notes.add(countMessage(plan.skippedRowCount(), Messages.Paste_skippedRow,
                    Messages.Paste_skippedRows));
        }
        if (!notes.isEmpty())
        {
            context.setStatusMessage(String.join(" ", notes));
        }
    }

    private static String countMessage(final int count, final String singularMessage,
            final String pluralMessage)
    {
        return NLS.bind(count == 1 ? singularMessage : pluralMessage, count);
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
        if (selection.rowCount() == 0)
        {
            return selection.columnCount() > 0;
        }
        return !selection.rowIndexes().isEmpty() && !selection.columnIndexes().isEmpty();
    }
}
