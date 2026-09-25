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

import java.util.List;
import java.util.Locale;

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.DefaultNatTableStyleConfiguration;
import org.eclipse.nebula.widgets.nattable.coordinate.PositionCoordinate;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.edit.config.DefaultEditConfiguration;
import org.eclipse.nebula.widgets.nattable.grid.data.DefaultCornerDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.data.DefaultRowHeaderDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.layer.ColumnHeaderLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.CornerLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.DefaultColumnHeaderDataLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.DefaultRowHeaderDataLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.GridLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.RowHeaderLayer;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.selection.event.ISelectionEvent;
import org.eclipse.nebula.widgets.nattable.style.theme.DarkNatTableThemeConfiguration;
import org.eclipse.nebula.widgets.nattable.style.theme.ModernNatTableThemeConfiguration;
import org.eclipse.nebula.widgets.nattable.viewport.ViewportLayer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * One NatTable showing one table of a {@link DatasetGridContext}'s dataset.
 *
 * @since 1.0.0
 */
public final class DatasetGrid
{
    private final TableBodyDataProvider bodyDataProvider;

    private final DataLayer bodyDataLayer;

    private final SelectionLayer selectionLayer;

    private final NatTable natTable;

    private final DatasetCellLabels cellLabels;

    private final ColumnHeaderLabels columnHeaderLabels;

    private final ColumnWidths columnWidths = new ColumnWidths();

    private DatasetTable currentTable;

    private PositionCoordinate pendingSelection;

    /**
     * Assembles a NatTable for one table.
     *
     * @param parent The composite to create the NatTable in.
     * @param context What the grid needs from the page that hosts it.
     * @param tableKey The key of the table to display.
     */
    public DatasetGrid(final Composite parent, final DatasetGridContext context, final String tableKey)
    {
        bodyDataProvider = new TableBodyDataProvider(context, tableKey);
        bodyDataLayer = new DataLayer(bodyDataProvider);
        cellLabels = new DatasetCellLabels(bodyDataProvider);
        bodyDataLayer.setConfigLabelAccumulator(cellLabels);
        selectionLayer = new SelectionLayer(bodyDataLayer);
        final ViewportLayer viewportLayer = new ViewportLayer(selectionLayer);

        final IDataProvider columnHeaderDataProvider = new ColumnHeaderDataProvider(bodyDataProvider);
        final DataLayer columnHeaderDataLayer = new DefaultColumnHeaderDataLayer(columnHeaderDataProvider);
        columnHeaderLabels = new ColumnHeaderLabels(bodyDataProvider);
        columnHeaderDataLayer.setConfigLabelAccumulator(columnHeaderLabels);
        final ColumnHeaderLayer columnHeaderLayer =
                new ColumnHeaderLayer(columnHeaderDataLayer, viewportLayer, selectionLayer);

        final IDataProvider rowHeaderDataProvider = new DefaultRowHeaderDataProvider(bodyDataProvider);
        final DataLayer rowHeaderDataLayer = new DefaultRowHeaderDataLayer(rowHeaderDataProvider);
        final RowHeaderLayer rowHeaderLayer =
                new RowHeaderLayer(rowHeaderDataLayer, viewportLayer, selectionLayer);

        final IDataProvider cornerDataProvider =
                new DefaultCornerDataProvider(columnHeaderDataProvider, rowHeaderDataProvider);
        final CornerLayer cornerLayer =
                new CornerLayer(new DataLayer(cornerDataProvider), rowHeaderLayer, columnHeaderLayer);

        // false: skip DefaultGridLayerConfiguration, whose DefaultEditBindings open an editor on a single
        // click.
        final GridLayer gridLayer =
                new GridLayer(viewportLayer, columnHeaderLayer, rowHeaderLayer, cornerLayer, false);
        gridLayer.addConfiguration(new DefaultEditConfiguration());

        natTable = new NatTable(parent, NatTable.DEFAULT_STYLE_OPTIONS, gridLayer, false);
        natTable.addConfiguration(new DefaultNatTableStyleConfiguration());
        natTable.addConfiguration(new GridEditConfiguration(context));
        natTable.addConfiguration(new SpreadsheetEditBindings(selectionLayer));
        natTable.addConfiguration(new GridStyleConfiguration());
        natTable.configure();
        natTable.setTheme(context.isDarkTheme() ? new DarkNatTableThemeConfiguration()
                : new ModernNatTableThemeConfiguration());
        wireContextMenu(context);
    }

    private void wireContextMenu(final DatasetGridContext context)
    {
        final MenuManager menuManager = new MenuManager();
        menuManager.setRemoveAllWhenShown(true);
        menuManager.addMenuListener(manager ->
        {
            final Point cursor = natTable.toControl(natTable.getDisplay().getCursorLocation());
            context.fillContextMenu(manager, ContextMenuTarget.regionAt(natTable, cursor.x, cursor.y));
        });
        natTable.setMenu(menuManager.createContextMenu(natTable));
    }

    /**
     * Returns the control to place in the table's tab.
     *
     * @return The grid's control.
     */
    public Control getControl()
    {
        return natTable;
    }

    /**
     * Commits the value of an open cell editor, if there is one, and closes it.
     *
     * @return True when no cell editor is open anymore; false when the value failed validation and the
     *         editor stays open.
     */
    public boolean commitActiveCellEditor()
    {
        return natTable.commitAndCloseActiveCellEditor();
    }

    NatTable getNatTable()
    {
        return natTable;
    }

    TableBodyDataProvider getBodyDataProvider()
    {
        return bodyDataProvider;
    }

    SelectionLayer getSelectionLayer()
    {
        return selectionLayer;
    }

    /**
     * Returns this grid's current selection.
     *
     * @return The selection snapshot.
     */
    public GridSelection getSelection()
    {
        return GridSelection.compute(bodyDataProvider.getTableKey(), bodyDataProvider.getRowCount(),
                bodyDataProvider.getColumnCount(), selectionLayer);
    }

    /**
     * Notifies a listener whenever this grid's selection changes.
     *
     * @param listener The listener to notify; it is not told what changed.
     */
    public void addSelectionListener(final Runnable listener)
    {
        selectionLayer.addLayerListener(event ->
        {
            if (event instanceof ISelectionEvent)
            {
                listener.run();
            }
        });
    }

    LabelStack cellLabelsFor(final int columnIndex, final int rowIndex)
    {
        final LabelStack labels = new LabelStack();
        cellLabels.accumulateConfigLabels(labels, columnIndex, rowIndex);
        return labels;
    }

    LabelStack columnHeaderLabelsFor(final int columnIndex)
    {
        final LabelStack labels = new LabelStack();
        columnHeaderLabels.accumulateConfigLabels(labels, columnIndex, 0);
        return labels;
    }

    /**
     * Tells the grid its table was renamed, so it keeps showing the same table under its new key.
     *
     * @param newTableKey The table's key after the rename.
     */
    public void tableRenamed(final String newTableKey)
    {
        bodyDataProvider.setTableKey(newTableKey);
    }

    /**
     * Requests the cell to select the next time the table changes, instead of the old selection anchor
     * clamped to the new bounds.
     *
     * @param columnIndex The column of the cell to select.
     * @param rowIndex The row of the cell to select.
     */
    public void setPendingSelection(final int columnIndex, final int rowIndex)
    {
        pendingSelection = new PositionCoordinate(selectionLayer, columnIndex, rowIndex);
    }

    /**
     * Refreshes the grid for a new snapshot of its table.
     *
     * @param newTable The table's current state.
     */
    public void tableChanged(final DatasetTable newTable)
    {
        columnWidths.remember(currentTable, bodyDataLayer);
        if (currentTable != null && sameStructure(currentTable, newTable))
        {
            currentTable = newTable;
            natTable.refresh(false);
            return;
        }

        // Copy the anchor: the refresh clears the selection, which resets the layer's anchor object.
        final PositionCoordinate oldAnchor = new PositionCoordinate(selectionLayer.getSelectionAnchor());
        currentTable = newTable;
        natTable.refresh(true);
        columnWidths.applyTo(newTable, bodyDataLayer, natTable);
        reselect(oldAnchor, newTable);
    }

    private void reselect(final PositionCoordinate oldAnchor, final DatasetTable newTable)
    {
        final int rowCount = newTable.getRows().size();
        final int columnCount = newTable.getColumns().size();
        if (rowCount == 0 || columnCount == 0)
        {
            pendingSelection = null;
            return;
        }
        final PositionCoordinate target = pendingSelection != null ? pendingSelection : oldAnchor;
        pendingSelection = null;
        if (target == null || target.columnPosition < 0 || target.rowPosition < 0)
        {
            return;
        }
        final int column = Math.min(target.columnPosition, columnCount - 1);
        final int row = Math.min(target.rowPosition, rowCount - 1);
        selectionLayer.setSelectedCell(column, row);
        selectionLayer.moveSelectionAnchor(column, row);
    }

    private static boolean sameStructure(final DatasetTable oldTable, final DatasetTable newTable)
    {
        if (oldTable.getRows().size() != newTable.getRows().size())
        {
            return false;
        }
        final List<DatasetColumn> oldColumns = oldTable.getColumns();
        final List<DatasetColumn> newColumns = newTable.getColumns();
        if (oldColumns.size() != newColumns.size())
        {
            return false;
        }
        for (int index = 0; index < oldColumns.size(); index++)
        {
            if (!columnKey(oldColumns.get(index)).equals(columnKey(newColumns.get(index))))
            {
                return false;
            }
        }
        return true;
    }

    private static String columnKey(final DatasetColumn column)
    {
        return column.name().toUpperCase(Locale.ENGLISH);
    }
}
