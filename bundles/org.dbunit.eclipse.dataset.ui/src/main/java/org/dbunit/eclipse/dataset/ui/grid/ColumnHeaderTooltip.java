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
import org.eclipse.jface.window.DefaultToolTip;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.swt.widgets.Event;

/**
 * Shows the tooltip "Declared in the DTD; no values yet" over a column header whose column is declared in
 * the DTD but has no values.
 *
 * @since 1.0.0
 */
final class ColumnHeaderTooltip extends DefaultToolTip
{
    private final NatTable natTable;

    private final TableBodyDataProvider bodyDataProvider;

    ColumnHeaderTooltip(final NatTable natTable, final TableBodyDataProvider bodyDataProvider)
    {
        super(natTable);
        this.natTable = natTable;
        this.bodyDataProvider = bodyDataProvider;
    }

    @Override
    protected boolean shouldCreateToolTip(final Event event)
    {
        return super.shouldCreateToolTip(event) && getText(event) != null;
    }

    @Override
    protected String getText(final Event event)
    {
        final LabelStack regionLabels = natTable.getRegionLabelsByXY(event.x, event.y);
        if (regionLabels == null || !regionLabels.hasLabel(GridRegion.COLUMN_HEADER))
        {
            return null;
        }
        return textForColumn(natTable.getColumnPositionByX(event.x));
    }

    /**
     * Returns the tooltip text for a column header, by column position.
     *
     * @param columnPosition The column's position.
     * @return The tooltip text, or null when the column is not declared in the DTD without values.
     */
    String textForColumn(final int columnPosition)
    {
        final DatasetColumn column = bodyDataProvider.getColumn(columnPosition);
        return column != null && column.declared() && !column.hasValues()
                ? "Declared in the DTD; no values yet" : null;
    }
}
