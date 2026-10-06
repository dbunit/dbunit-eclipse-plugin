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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dbunit.eclipse.dataset.core.edit.TableChanges;
import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGrid;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * The sheet tabs of the Tables page: one tab with a {@link DatasetGrid} for each table of the dataset model,
 * in the order of the model, with the name, the tooltip, the font, and the problem icon of each tab. The page
 * tells the tabs which tables of the model are tables that they show under other keys, so that a renamed
 * table keeps its tab and its grid, and which table's tab to select. A tab stays selected when the tabs
 * move, and when the selected table is gone, the tab that takes its place is selected. All of it belongs to
 * the tab folder, so it may be used on the UI thread only.
 */
final class TableTabs
{
    private final CTabFolder tabFolder;

    private final DatasetGridContext context;

    private final ResourceManager resources;

    private final Runnable gridStateChanged;

    private final Map<String, CTabItem> tabsByKey = new LinkedHashMap<>();

    private final Map<String, DatasetGrid> gridsByKey = new LinkedHashMap<>();

    /**
     * Creates the tabs of a page.
     *
     * @param tabFolder The folder that holds the tabs.
     * @param context The context that each grid works with.
     * @param resources The manager that owns the fonts of the tabs and disposes them with the page.
     * @param gridStateChanged Runs when the selected tab changes, when the selection of a grid changes, and
     *                         when a cell editor of a grid opens or closes.
     */
    TableTabs(final CTabFolder tabFolder, final DatasetGridContext context, final ResourceManager resources,
            final Runnable gridStateChanged)
    {
        this.tabFolder = tabFolder;
        this.context = context;
        this.resources = resources;
        this.gridStateChanged = gridStateChanged;
    }

    /**
     * Commits the value of an open cell editor, so that it is in the document before the document is saved
     * or the Source page shows it.
     *
     * @return True when no cell editor is open anymore; false when the value of one was not accepted and
     *         its editor stays open, so that the value is not in the document.
     */
    boolean commitActiveCellEditor()
    {
        boolean noEditorLeft = true;
        for (final DatasetGrid grid : gridsByKey.values())
        {
            final boolean closed = grid.commitActiveCellEditor();
            noEditorLeft = noEditorLeft && closed;
        }
        return noEditorLeft;
    }

    /**
     * Closes an open cell editor without writing its value, so that the value cannot reach a document that
     * is about to be replaced.
     */
    void cancelActiveCellEditor()
    {
        for (final DatasetGrid grid : gridsByKey.values())
        {
            grid.cancelActiveCellEditor();
        }
    }

    /**
     * Repaints every grid, for example after the NULL display text changed.
     */
    void repaintGrids()
    {
        for (final DatasetGrid grid : gridsByKey.values())
        {
            grid.repaint();
        }
    }

    /**
     * Has every grid apply the theme of the workbench again when it changed.
     */
    void updateThemes()
    {
        for (final DatasetGrid grid : gridsByKey.values())
        {
            grid.updateTheme();
        }
    }

    DatasetGrid activeGrid()
    {
        final CTabItem selected = tabFolder.getSelection();
        if (selected == null)
        {
            return null;
        }
        for (final DatasetGrid grid : gridsByKey.values())
        {
            if (grid.getControl().equals(selected.getControl()))
            {
                return grid;
            }
        }
        return null;
    }

    void selectCell(final CellAddress address)
    {
        final CTabItem item = tabsByKey.get(address.tableKey());
        if (item != null)
        {
            tabFolder.setSelection(item);
            gridStateChanged.run();
        }
        final DatasetGrid grid = gridsByKey.get(address.tableKey());
        if (grid == null || address.columnIndex() < 0)
        {
            return;
        }
        grid.selectCell(address.columnIndex(), address.rowIndex());
    }

    /**
     * Brings the tabs in line with the tables of a model, when nothing is known about how they came from
     * the tables that the tabs show: a table under a new key gets a new tab.
     *
     * @param model The model whose tables the tabs show.
     */
    void reconcile(final DatasetModel model)
    {
        reconcile(model, TableChanges.NONE, null);
    }

    /**
     * Brings the tabs in line with the tables of the model. A table that was renamed keeps its tab and its
     * grid under its new key. The tabs of tables that are gone are disposed first, so that they do not make
     * the tabs after them move, then the tab of each table is created or moved to the table's place and
     * updated. The tab that was selected stays selected, also when it moves, and when its table is gone, the
     * tab that takes its place is selected. The tab of the table to select is selected in any case.
     *
     * @param model The model whose tables the tabs show.
     * @param changes Which tables of the model are tables that the tabs show under other keys.
     * @param tableKeyToSelect The key of the table whose tab is selected, or null to keep the selection.
     */
    void reconcile(final DatasetModel model, final TableChanges changes, final String tableKeyToSelect)
    {
        final Map<String, DatasetTable> tables = tablesByKey(model);
        final DatasetGrid selectedGrid = activeGrid();
        final int selectedIndex = tabFolder.getSelectionIndex();

        applyRenames(changes.renamedKeys(), tables.keySet());
        disposeStaleTabs(tables.keySet());
        int index = 0;
        for (final DatasetTable table : tables.values())
        {
            final String key = table.getKey();
            final CTabItem item = placeTab(key, index);
            gridsByKey.get(key).tableChanged(table);
            updateTab(item, table, model);
            index++;
        }
        selectTab(selectedGrid, selectedIndex, tableKeyToSelect);
    }

    private static Map<String, DatasetTable> tablesByKey(final DatasetModel model)
    {
        final Map<String, DatasetTable> tables = new LinkedHashMap<>();
        for (final DatasetTable table : model.getTables())
        {
            tables.putIfAbsent(table.getKey(), table);
        }
        return tables;
    }

    private void applyRenames(final Map<String, String> renamedKeys, final Set<String> tableKeys)
    {
        for (final Map.Entry<String, String> renamed : renamedKeys.entrySet())
        {
            final String oldKey = renamed.getKey();
            final String newKey = renamed.getValue();
            final boolean renameApplies = tableKeys.contains(newKey) && tabsByKey.containsKey(oldKey)
                    && !tabsByKey.containsKey(newKey);
            if (renameApplies)
            {
                final CTabItem item = tabsByKey.remove(oldKey);
                tabsByKey.put(newKey, item);
                final DatasetGrid grid = gridsByKey.remove(oldKey);
                grid.tableRenamed(newKey);
                gridsByKey.put(newKey, grid);
            }
        }
    }

    private void disposeStaleTabs(final Set<String> tableKeys)
    {
        final Iterator<Map.Entry<String, CTabItem>> iterator = tabsByKey.entrySet().iterator();
        while (iterator.hasNext())
        {
            final Map.Entry<String, CTabItem> entry = iterator.next();
            if (!tableKeys.contains(entry.getKey()))
            {
                entry.getValue().getControl().dispose();
                entry.getValue().dispose();
                iterator.remove();
                gridsByKey.remove(entry.getKey());
            }
        }
    }

    private CTabItem placeTab(final String key, final int index)
    {
        CTabItem item = tabsByKey.get(key);
        if (item == null)
        {
            item = createTab(key, index);
        }
        else if (tabFolder.indexOf(item) != index)
        {
            item = moveTab(item, index);
            tabsByKey.put(key, item);
        }
        return item;
    }

    private CTabItem createTab(final String key, final int index)
    {
        final DatasetGrid grid = new DatasetGrid(tabFolder, context, key);
        grid.selectCell(0, 0);
        grid.addSelectionListener(gridStateChanged);
        grid.addCellEditorListener(gridStateChanged);
        gridsByKey.put(key, grid);
        final CTabItem item = new CTabItem(tabFolder, SWT.NONE, index);
        item.setControl(grid.getControl());
        tabsByKey.put(key, item);
        return item;
    }

    // A tab folder cannot move a tab, so the tab is created again at its new index. The new tab shares the
    // grid and takes over the selection before the old tab is disposed, so the folder never selects another
    // tab in between and never hides the grid.
    private CTabItem moveTab(final CTabItem oldItem, final int index)
    {
        final CTabItem newItem = new CTabItem(tabFolder, SWT.NONE, index);
        newItem.setControl(oldItem.getControl());
        if (oldItem.equals(tabFolder.getSelection()))
        {
            tabFolder.setSelection(newItem);
        }
        oldItem.dispose();
        return newItem;
    }

    private void selectTab(final DatasetGrid previousGrid, final int previousIndex,
            final String tableKeyToSelect)
    {
        final CTabItem tabToSelect = tabsByKey.get(tableKeyToSelect);
        if (tabToSelect != null)
        {
            tabFolder.setSelection(tabToSelect);
        }
        else if (tabOf(previousGrid) == null)
        {
            selectTabNearIndex(previousIndex);
        }
    }

    private CTabItem tabOf(final DatasetGrid grid)
    {
        CTabItem tab = null;
        for (final Map.Entry<String, DatasetGrid> entry : gridsByKey.entrySet())
        {
            if (entry.getValue().equals(grid))
            {
                tab = tabsByKey.get(entry.getKey());
                break;
            }
        }
        return tab;
    }

    private void selectTabNearIndex(final int index)
    {
        final int lastIndex = tabFolder.getItemCount() - 1;
        if (lastIndex >= 0)
        {
            tabFolder.setSelection(Math.max(0, Math.min(index, lastIndex)));
        }
    }

    private void updateTab(final CTabItem item, final DatasetTable table, final DatasetModel model)
    {
        item.setText(table.getName());
        final String tooltip = table.isDeclaredOnly() ? Messages.TablesPage_declaredOnlyTableTooltip
                : countsTooltip(table);
        item.setToolTipText(tooltip);
        item.setFont(table.getRows().isEmpty() ? italicFont() : null);
        item.setImage(problemImage(model.getProblems(table.getKey())));
    }

    private static String countsTooltip(final DatasetTable table)
    {
        final int rowCount = table.getRows().size();
        final int columnCount = table.getColumns().size();
        final String rows = rowCount == 1 ? Messages.TablesPage_tableTooltipOneRow
                : NLS.bind(Messages.TablesPage_tableTooltipRows, rowCount);
        final String columns = columnCount == 1 ? Messages.TablesPage_tableTooltipOneColumn
                : NLS.bind(Messages.TablesPage_tableTooltipColumns, columnCount);
        return NLS.bind(Messages.TablesPage_tableTooltip, rows, columns);
    }

    private Font italicFont()
    {
        final FontDescriptor descriptor = FontDescriptor.createFrom(tabFolder.getFont()).setStyle(SWT.ITALIC);
        return resources.createFont(descriptor);
    }

    private static Image problemImage(final List<DatasetProblem> problems)
    {
        boolean hasWarning = false;
        for (final DatasetProblem problem : problems)
        {
            if (problem.severity() == ProblemSeverity.ERROR)
            {
                return sharedImage(ISharedImages.IMG_OBJS_ERROR_TSK);
            }
            if (problem.severity() == ProblemSeverity.WARNING)
            {
                hasWarning = true;
            }
        }
        return hasWarning ? sharedImage(ISharedImages.IMG_OBJS_WARN_TSK) : null;
    }

    private static Image sharedImage(final String key)
    {
        return PlatformUI.getWorkbench().getSharedImages().getImage(key);
    }
}
