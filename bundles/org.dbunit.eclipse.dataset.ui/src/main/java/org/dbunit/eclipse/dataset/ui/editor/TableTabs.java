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

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * The sheet tabs of the Tables page: one tab with a {@link DatasetGrid} for each table of the dataset model,
 * in the order of the model, with the name, the tooltip, the font, and the problem icon of each tab. The page
 * tells the tabs before it renames or adds a table, so that the renamed table keeps its tab and its grid and
 * the added table's tab is selected. All of it belongs to the tab folder, so it may be used on the UI thread
 * only.
 */
final class TableTabs
{
    private final CTabFolder tabFolder;

    private final DatasetGridContext context;

    private final ResourceManager resources;

    private final Runnable selectionChanged;

    private final Map<String, CTabItem> tabsByKey = new LinkedHashMap<>();

    private final Map<String, DatasetGrid> gridsByKey = new LinkedHashMap<>();

    private String expectedRenameOldKey;

    private String expectedRenameNewKey;

    private String expectedNewTableKey;

    /**
     * Creates the tabs of a page.
     *
     * @param tabFolder The folder that holds the tabs.
     * @param context The context that each grid works with.
     * @param resources The manager that owns the fonts of the tabs and disposes them with the page.
     * @param selectionChanged Runs when the selected tab changes and when the selection of a grid changes.
     */
    TableTabs(final CTabFolder tabFolder, final DatasetGridContext context, final ResourceManager resources,
            final Runnable selectionChanged)
    {
        this.tabFolder = tabFolder;
        this.context = context;
        this.resources = resources;
        this.selectionChanged = selectionChanged;
    }

    /**
     * Commits the value of an open cell editor, so that it is in the document before the document is saved
     * or the Source page shows it.
     */
    void commitActiveCellEditor()
    {
        for (final DatasetGrid grid : gridsByKey.values())
        {
            grid.commitActiveCellEditor();
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

    void expectRename(final String oldKey, final String newKey)
    {
        expectedRenameOldKey = oldKey;
        expectedRenameNewKey = newKey;
    }

    void expectNewTableSelected(final String tableKey)
    {
        expectedNewTableKey = tableKey;
    }

    void cancelExpectedRename()
    {
        expectedRenameOldKey = null;
        expectedRenameNewKey = null;
    }

    void cancelExpectedNewTableSelected()
    {
        expectedNewTableKey = null;
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
            selectionChanged.run();
        }
        final DatasetGrid grid = gridsByKey.get(address.tableKey());
        if (grid == null || address.columnIndex() < 0)
        {
            return;
        }
        grid.selectCell(address.columnIndex(), address.rowIndex());
    }

    void reconcile(final DatasetModel model)
    {
        final Set<String> seenKeys = new HashSet<>();
        int index = 0;
        for (final DatasetTable table : model.getTables())
        {
            final String key = resolveRenamedKey(table.getKey());
            if (!seenKeys.add(key))
            {
                continue;
            }
            CTabItem item = tabsByKey.get(key);
            DatasetGrid grid = gridsByKey.get(key);
            if (item == null)
            {
                grid = new DatasetGrid(tabFolder, context, key);
                grid.selectCell(0, 0);
                grid.addSelectionListener(selectionChanged);
                gridsByKey.put(key, grid);
                item = new CTabItem(tabFolder, SWT.NONE, index);
                item.setControl(grid.getControl());
                tabsByKey.put(key, item);
                if (key.equals(expectedNewTableKey))
                {
                    tabFolder.setSelection(item);
                }
            }
            else if (tabFolder.indexOf(item) != index)
            {
                item = moveTab(item, index);
                tabsByKey.put(key, item);
            }
            grid.tableChanged(table);
            updateTab(item, table, model);
            index++;
        }
        expectedRenameOldKey = null;
        expectedRenameNewKey = null;
        expectedNewTableKey = null;

        disposeStaleTabs(seenKeys);
        selectFirstTabIfNoneSelected();
    }

    private void disposeStaleTabs(final Set<String> seenKeys)
    {
        final Iterator<Map.Entry<String, CTabItem>> iterator = tabsByKey.entrySet().iterator();
        while (iterator.hasNext())
        {
            final Map.Entry<String, CTabItem> entry = iterator.next();
            if (!seenKeys.contains(entry.getKey()))
            {
                entry.getValue().getControl().dispose();
                entry.getValue().dispose();
                iterator.remove();
                gridsByKey.remove(entry.getKey());
            }
        }
    }

    private void selectFirstTabIfNoneSelected()
    {
        if (tabFolder.getSelection() == null && tabFolder.getItemCount() > 0)
        {
            tabFolder.setSelection(0);
        }
    }

    private String resolveRenamedKey(final String currentKey)
    {
        if (currentKey.equals(expectedRenameNewKey) && tabsByKey.containsKey(expectedRenameOldKey))
        {
            tabsByKey.put(currentKey, tabsByKey.remove(expectedRenameOldKey));
            final DatasetGrid grid = gridsByKey.remove(expectedRenameOldKey);
            grid.tableRenamed(currentKey);
            gridsByKey.put(currentKey, grid);
        }
        return currentKey;
    }

    private CTabItem moveTab(final CTabItem oldItem, final int index)
    {
        final Control tabControl = oldItem.getControl();
        oldItem.setControl(null);
        oldItem.dispose();
        final CTabItem newItem = new CTabItem(tabFolder, SWT.NONE, index);
        newItem.setControl(tabControl);
        return newItem;
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
