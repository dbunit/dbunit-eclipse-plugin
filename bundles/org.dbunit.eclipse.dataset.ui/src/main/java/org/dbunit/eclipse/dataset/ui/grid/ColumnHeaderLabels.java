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

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.nebula.widgets.nattable.layer.cell.IConfigLabelAccumulator;

/**
 * Labels column header cells of a {@link DatasetGrid} for styling.
 *
 * @since 1.0.0
 */
final class ColumnHeaderLabels implements IConfigLabelAccumulator
{
    static final String PENDING_COLUMN = "PENDING_COLUMN";

    static final String COLUMN_WARNING = "COLUMN_WARNING";

    static final String COLUMN_ERROR = "COLUMN_ERROR";

    private final TableBodyDataProvider bodyDataProvider;

    ColumnHeaderLabels(final TableBodyDataProvider bodyDataProvider)
    {
        this.bodyDataProvider = bodyDataProvider;
    }

    @Override
    public void accumulateConfigLabels(final LabelStack configLabels, final int columnPosition,
            final int rowPosition)
    {
        final DatasetColumn column = bodyDataProvider.getColumn(columnPosition);
        if (column == null)
        {
            return;
        }
        if (column.pending())
        {
            configLabels.addLabel(PENDING_COLUMN);
        }
        final ProblemSeverity worst = worstSeverityFor(column);
        if (worst == ProblemSeverity.ERROR)
        {
            configLabels.addLabel(COLUMN_ERROR);
        }
        else if (worst == ProblemSeverity.WARNING)
        {
            configLabels.addLabel(COLUMN_WARNING);
        }
    }

    private ProblemSeverity worstSeverityFor(final DatasetColumn column)
    {
        final List<DatasetProblem> problems = bodyDataProvider.getContext().getDatasetDocument().getModel()
                .getProblems(bodyDataProvider.getTableKey());
        ProblemSeverity worst = null;
        for (final DatasetProblem problem : problems)
        {
            if (!column.name().equals(problem.columnName()))
            {
                continue;
            }
            if (problem.severity() == ProblemSeverity.ERROR)
            {
                return ProblemSeverity.ERROR;
            }
            if (problem.severity() == ProblemSeverity.WARNING)
            {
                worst = ProblemSeverity.WARNING;
            }
        }
        return worst;
    }
}
