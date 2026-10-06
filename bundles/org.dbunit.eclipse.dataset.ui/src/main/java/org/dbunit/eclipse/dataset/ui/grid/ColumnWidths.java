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
import java.util.Map;

import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.data.convert.IDisplayConverter;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.swt.graphics.GC;

/**
 * Remembers each column's width by column key for the life of the editor, so a column keeps the width the
 * user gave it across model refreshes.
 * <p>
 * The widths are kept as the data layer stores them, unscaled. The layer displays them multiplied by the
 * scaling factor of the display, so reading the displayed width back would apply the factor again at every
 * refresh.
 * </p>
 * <p>
 * A column that has no width yet gets one that fits its name and the text that the grid paints in its first
 * rows. That text is the display text of each value, so a NULL cell counts as the NULL display text and a
 * line break counts as the glyph that stands for it.
 * </p>
 *
 * @since 1.0.0
 */
final class ColumnWidths
{
    private static final int MINIMUM_WIDTH = 48;

    private static final int MAXIMUM_WIDTH = 400;

    private static final int SAMPLE_ROW_LIMIT = 200;

    private final Map<String, Integer> widthsByColumnKey = new HashMap<>();

    private final IDisplayConverter displayConverter;

    /**
     * Creates the widths of one grid.
     *
     * @param displayConverter Turns the value of a cell into the text that the grid paints in it, which is
     *        what the automatic widths are measured with.
     */
    ColumnWidths(final IDisplayConverter displayConverter)
    {
        this.displayConverter = displayConverter;
    }

    /**
     * Records the current width of every column of a table that has been given one, so it survives a
     * structural refresh even when the table is about to be replaced.
     */
    void remember(final DatasetTable table, final DataLayer bodyDataLayer)
    {
        if (table == null)
        {
            return;
        }
        for (int index = 0; index < table.getColumns().size(); index++)
        {
            final int width = bodyDataLayer.getConfiguredColumnWidthByPosition(index);
            if (width >= 0)
            {
                widthsByColumnKey.put(table.getColumns().get(index).key(), width);
            }
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
            final String key = table.getColumns().get(index).key();
            final Integer remembered = widthsByColumnKey.get(key);
            final int width = remembered != null ? remembered : computeAutoWidth(natTable, table, index);
            bodyDataLayer.setColumnWidthByPosition(index, width);
            widthsByColumnKey.put(key, width);
        }
    }

    private int computeAutoWidth(final NatTable natTable, final DatasetTable table, final int columnIndex)
    {
        final GC gc = new GC(natTable);
        try
        {
            gc.setFont(natTable.getFont());
            int width = gc.textExtent(table.getColumns().get(columnIndex).name()).x + 24;
            final int rowLimit = Math.min(table.getRows().size(), SAMPLE_ROW_LIMIT);
            for (int row = 0; row < rowLimit; row++)
            {
                final String value = table.getEffectiveValue(row, columnIndex);
                final Object displayValue = displayConverter.canonicalToDisplayValue(value);
                final String text = String.valueOf(displayValue);
                width = Math.max(width, gc.textExtent(text).x + 12);
            }
            return Math.max(MINIMUM_WIDTH, Math.min(MAXIMUM_WIDTH, width));
        }
        finally
        {
            gc.dispose();
        }
    }
}
