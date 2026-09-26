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

import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.jface.window.DefaultToolTip;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.swt.widgets.Event;

/**
 * Shows a column header's problems and why a column has no values, when it is declared in the DTD without
 * values or pending, as a tooltip.
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
     * @return The tooltip text, or null when the column has no problems, is not declared in the DTD
     *         without values, and is not pending.
     */
    String textForColumn(final int columnPosition)
    {
        final DatasetColumn column = bodyDataProvider.getColumn(columnPosition);
        if (column == null)
        {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        for (final DatasetProblem problem : bodyDataProvider.getContext().getDatasetDocument().getModel()
                .getProblems(bodyDataProvider.getTableKey()))
        {
            if (column.name().equals(problem.columnName()))
            {
                lines.add(problem.message());
            }
        }
        if (column.declared() && !column.hasValues())
        {
            lines.add(Messages.ColumnHeaderTooltip_declaredWithoutValues);
        }
        if (column.pending())
        {
            lines.add(Messages.ColumnHeaderTooltip_pending);
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }
}
