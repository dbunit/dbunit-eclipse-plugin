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

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;

/**
 * Presents a table's column names as a single-row header data source.
 *
 * @since 1.0.0
 */
final class ColumnHeaderDataProvider implements IDataProvider
{
    private final TableBodyDataProvider bodyDataProvider;

    ColumnHeaderDataProvider(final TableBodyDataProvider bodyDataProvider)
    {
        this.bodyDataProvider = bodyDataProvider;
    }

    @Override
    public int getColumnCount()
    {
        return bodyDataProvider.getColumnCount();
    }

    @Override
    public int getRowCount()
    {
        return 1;
    }

    @Override
    public Object getDataValue(final int columnIndex, final int rowIndex)
    {
        final DatasetColumn column = bodyDataProvider.getColumn(columnIndex);
        return column == null ? "" : column.name();
    }

    @Override
    public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue)
    {
        throw new UnsupportedOperationException("Column headers are not editable.");
    }
}
