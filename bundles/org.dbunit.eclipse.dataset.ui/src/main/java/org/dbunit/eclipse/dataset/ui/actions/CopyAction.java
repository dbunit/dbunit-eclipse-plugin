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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.tsv.TabSeparatedValues;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Text;

/**
 * Copies the bounding rectangle of the selection to the clipboard as tab-separated text; cells inside it
 * that are not selected copy as NULL.
 *
 * @since 1.0.0
 */
public final class CopyAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public CopyAction(final DatasetGridContext context)
    {
        super(context);
        setText(Messages.Action_copy);
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        copySelectedCellsToClipboard(context);
    }

    /**
     * Copies the bounding rectangle of the active grid's selection to the clipboard, for reuse by
     * {@link CutAction}.
     *
     * @param context What this needs from the page that hosts the grid.
     */
    static void copySelectedCellsToClipboard(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final DatasetTable table =
                context.getDatasetDocument().getModel().findTable(selection.tableKey()).orElseThrow();
        final Set<Point> selectedCells = new HashSet<>(context.getSelectedCellPositions());
        final List<List<String>> block = new ArrayList<>();
        for (int rowIndex = selection.firstRowIndex(); rowIndex <= selection.lastRowIndex(); rowIndex++)
        {
            final List<String> line = new ArrayList<>();
            for (int columnIndex = selection.firstColumnIndex(); columnIndex <= selection
                    .lastColumnIndex(); columnIndex++)
            {
                final boolean isSelected = selectedCells.contains(new Point(columnIndex, rowIndex));
                line.add(isSelected ? table.getRows().get(rowIndex).getValue(columnIndex) : null);
            }
            block.add(line);
        }
        final String text = TabSeparatedValues.format(block, System.lineSeparator());
        context.writeClipboardText(text);
    }

    @Override
    protected boolean changesDataset()
    {
        return false;
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
            text.copy();
        }
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return !selection.rowIndexes().isEmpty() && !selection.columnIndexes().isEmpty();
    }
}
