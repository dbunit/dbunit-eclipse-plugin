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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.swt.graphics.GC;

/**
 * Remembers each column's width by column key for the life of the editor, so a column keeps the width the
 * user gave it across model refreshes.
 *
 * @since 1.0.0
 */
final class ColumnWidths
{
    private static final int MINIMUM_WIDTH = 48;

    private static final int MAXIMUM_WIDTH = 400;

    private static final int SAMPLE_ROW_LIMIT = 200;

    private final Map<String, Integer> widthsByColumnKey = new HashMap<>();

    /**
     * Records the current width of every column of a table, so it survives a structural refresh even when
     * the table is about to be replaced.
     */
    void remember(final DatasetTable table, final DataLayer bodyDataLayer)
    {
        if (table == null)
        {
            return;
        }
        for (int index = 0; index < table.getColumns().size(); index++)
        {
            widthsByColumnKey.put(columnKey(table, index), bodyDataLayer.getColumnWidthByPosition(index));
        }
    }

    /**
     * Applies each column's remembered width, or, the first time a column is seen, an automatically
     * computed one.
     */
    void applyTo(final DatasetTable table, final DataLayer bodyDataLayer, final NatTable natTable)
    {
        for (int index = 0; index < table.getColumns().size(); index++)
        {
            final String key = columnKey(table, index);
            final Integer remembered = widthsByColumnKey.get(key);
            final int width = remembered != null ? remembered : computeAutoWidth(natTable, table, index);
            bodyDataLayer.setColumnWidthByPosition(index, width);
            widthsByColumnKey.put(key, width);
        }
    }

    private static String columnKey(final DatasetTable table, final int columnIndex)
    {
        return table.getColumns().get(columnIndex).name().toUpperCase(Locale.ENGLISH);
    }

    private static int computeAutoWidth(final NatTable natTable, final DatasetTable table,
            final int columnIndex)
    {
        final GC gc = new GC(natTable);
        try
        {
            gc.setFont(natTable.getFont());
            int width = gc.textExtent(table.getColumns().get(columnIndex).name()).x + 24;
            final int rowLimit = Math.min(table.getRows().size(), SAMPLE_ROW_LIMIT);
            for (int row = 0; row < rowLimit; row++)
            {
                final String value = table.getRows().get(row).getValue(columnIndex);
                if (value != null)
                {
                    width = Math.max(width, gc.textExtent(value).x + 12);
                }
            }
            return Math.max(MINIMUM_WIDTH, Math.min(MAXIMUM_WIDTH, width));
        }
        finally
        {
            gc.dispose();
        }
    }
}
