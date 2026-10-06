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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.graphics.Point;

/**
 * Sets every selected cell to NULL, and tells the user when a cell cannot be NULL because the DTD gives its
 * column a default value.
 *
 * @since 1.0.0
 */
public final class SetNullAction extends GridAction
{
    /**
     * Creates the action.
     *
     * @param context What this action needs from the page that hosts the grid.
     */
    public SetNullAction(final DatasetGridContext context)
    {
        super(DatasetCommandIds.SET_NULL, context);
        setText(Messages.Action_setNull);
        setImageDescriptor(DatasetImages.getImageDescriptor(DatasetImages.IMG_SET_NULL));
    }

    @Override
    protected void runOnGrid(final DatasetGridContext context)
    {
        setSelectedCellsToNull(context);
    }

    /**
     * Sets every cell the active grid has selected to NULL, for reuse by {@link CutAction} and
     * {@link DeleteAction}.
     *
     * @param context What this needs from the page that hosts the grid.
     */
    static void setSelectedCellsToNull(final DatasetGridContext context)
    {
        final GridSelection selection = context.getSelection();
        final DatasetTable table = context.getDatasetDocument().requireTable(selection.tableKey());
        final List<Point> cells = context.getSelectedCellPositions();
        final List<CellChange> changes = CellValueChanges.setting(cells, table, null);
        final boolean edited = context
                .executeEdit(() -> context.getDatasetDocument().setCells(selection.tableKey(), changes));
        if (edited)
        {
            reportColumnsWithDefaultValues(context, table, cells);
        }
    }

    /**
     * Tells the user, on the status line, that a cell cannot be NULL where the DTD gives its column a default
     * value, because dbUnit loads the default for a row without the attribute.
     */
    private static void reportColumnsWithDefaultValues(final DatasetGridContext context,
            final DatasetTable table, final List<Point> cells)
    {
        final Set<DatasetColumn> defaultedColumns = new LinkedHashSet<>();
        for (final Point cell : cells)
        {
            final DatasetColumn column = table.getColumns().get(cell.x);
            if (column.hasDefaultValue())
            {
                defaultedColumns.add(column);
            }
        }
        if (defaultedColumns.isEmpty())
        {
            return;
        }
        final DatasetColumn first = defaultedColumns.iterator().next();
        final String message = defaultedColumns.size() == 1
                ? NLS.bind(Messages.SetNull_defaultValue, first.name(), first.defaultValue())
                : NLS.bind(Messages.SetNull_defaultValues, columnNames(defaultedColumns));
        context.setStatusMessage(message);
    }

    private static String columnNames(final Set<DatasetColumn> columns)
    {
        final List<String> names = new ArrayList<>();
        for (final DatasetColumn column : columns)
        {
            names.add(column.name());
        }
        return String.join(", ", names);
    }

    @Override
    protected boolean isEnabledFor(final GridSelection selection)
    {
        return !selection.rowIndexes().isEmpty() && !selection.columnIndexes().isEmpty();
    }
}
