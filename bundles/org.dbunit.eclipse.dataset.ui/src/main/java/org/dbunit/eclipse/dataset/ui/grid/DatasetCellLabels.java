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

import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.nebula.widgets.nattable.layer.cell.IConfigLabelAccumulator;

/**
 * Labels body cells of a {@link DatasetGrid} for styling.
 *
 * @since 1.0.0
 */
final class DatasetCellLabels implements IConfigLabelAccumulator
{
    static final String NULL_VALUE = "NULL_VALUE";

    private final TableBodyDataProvider bodyDataProvider;

    DatasetCellLabels(final TableBodyDataProvider bodyDataProvider)
    {
        this.bodyDataProvider = bodyDataProvider;
    }

    @Override
    public void accumulateConfigLabels(final LabelStack configLabels, final int columnPosition,
            final int rowPosition)
    {
        if (bodyDataProvider.getDataValue(columnPosition, rowPosition) == null)
        {
            configLabels.addLabel(NULL_VALUE);
        }
    }
}
