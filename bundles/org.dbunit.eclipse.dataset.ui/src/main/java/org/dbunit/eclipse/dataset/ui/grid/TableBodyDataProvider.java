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
import java.util.Optional;

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;

/**
 * Presents one table's rows and columns of a {@link DatasetGridContext}'s current model as grid data.
 *
 * @since 1.0.0
 */
final class TableBodyDataProvider implements IDataProvider
{
    private final DatasetGridContext context;

    private String tableKey;

    TableBodyDataProvider(final DatasetGridContext context, final String tableKey)
    {
        this.context = context;
        this.tableKey = tableKey;
    }

    @Override
    public int getRowCount()
    {
        return table().map(table -> table.getRows().size()).orElse(0);
    }

    @Override
    public int getColumnCount()
    {
        return table().map(table -> table.getColumns().size()).orElse(0);
    }

    @Override
    public Object getDataValue(final int columnIndex, final int rowIndex)
    {
        final Optional<DatasetTable> table = table();
        if (table.isEmpty() || rowIndex < 0 || rowIndex >= table.get().getRows().size() || columnIndex < 0
                || columnIndex >= table.get().getColumns().size())
        {
            return null;
        }
        return table.get().getRows().get(rowIndex).getValue(columnIndex);
    }

    @Override
    public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue)
    {
        final Optional<DatasetTable> table = table();
        if (table.isEmpty())
        {
            return;
        }
        final Object currentValue = getDataValue(columnIndex, rowIndex);
        if (currentValue == null && "".equals(newValue))
        {
            return;
        }
        final String columnName = table.get().getColumns().get(columnIndex).name();
        final String key = tableKey;
        final String value = keepLineBreaks((String) newValue, (String) currentValue);
        context.executeEdit(() -> context.getDatasetDocument().setCells(key,
                List.of(new CellChange(rowIndex, columnName, value))));
    }

    /**
     * Returns an edited value with the CR LF line breaks that a multi-line text widget writes on some
     * platforms turned back into line feeds, unless the value already used CR LF before the edit.
     */
    private static String keepLineBreaks(final String editedValue, final String currentValue)
    {
        if (editedValue == null || currentValue != null && currentValue.contains("\r\n"))
        {
            return editedValue;
        }
        return editedValue.replace("\r\n", "\n");
    }

    void setTableKey(final String tableKey)
    {
        this.tableKey = tableKey;
    }

    DatasetGridContext getContext()
    {
        return context;
    }

    String getTableKey()
    {
        return tableKey;
    }

    DatasetColumn getColumn(final int columnIndex)
    {
        final Optional<DatasetTable> table = table();
        if (table.isEmpty() || columnIndex < 0 || columnIndex >= table.get().getColumns().size())
        {
            return null;
        }
        return table.get().getColumns().get(columnIndex);
    }

    private Optional<DatasetTable> table()
    {
        return context.getDatasetDocument().getModel().findTable(tableKey);
    }
}
