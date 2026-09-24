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
package org.dbunit.eclipse.dataset.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable snapshot of a dataset: its tables and the problems found while building it.
 *
 * @since 1.0.0
 */
public final class DatasetModel
{
    /**
     * An empty, non-editable model, used before the first parse.
     */
    public static final DatasetModel EMPTY = new DatasetModel(List.of(), List.of(), false);

    private final List<DatasetTable> tables;

    private final List<DatasetProblem> problems;

    private final boolean editable;

    /**
     * Creates a dataset model.
     *
     * @param tables The tables of the dataset, copied defensively.
     * @param problems The problems found while building the model, copied defensively.
     * @param editable False when the source has errors that block editing.
     */
    public DatasetModel(final List<DatasetTable> tables, final List<DatasetProblem> problems,
            final boolean editable)
    {
        this.tables = List.copyOf(tables);
        this.problems = List.copyOf(problems);
        this.editable = editable;
    }

    /**
     * Returns the tables of the dataset.
     *
     * @return An unmodifiable list of tables, in model order.
     */
    public List<DatasetTable> getTables()
    {
        return tables;
    }

    /**
     * Finds a table by its key.
     *
     * @param tableKey The key to look up, as returned by {@link DatasetTable#getKey()}.
     * @return The matching table, or an empty {@link Optional} when no table has that key.
     */
    public Optional<DatasetTable> findTable(final String tableKey)
    {
        for (final DatasetTable table : tables)
        {
            final String key = table.getKey();
            if (key.equals(tableKey))
            {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns every problem found while building the model.
     *
     * @return An unmodifiable list of problems.
     */
    public List<DatasetProblem> getProblems()
    {
        return problems;
    }

    /**
     * Returns the problems that belong to one table.
     *
     * @param tableKey The table key to filter by.
     * @return An unmodifiable list of the problems whose table key equals the given key.
     */
    public List<DatasetProblem> getProblems(final String tableKey)
    {
        final List<DatasetProblem> filtered = new ArrayList<>();
        for (final DatasetProblem problem : problems)
        {
            if (Objects.equals(problem.tableKey(), tableKey))
            {
                filtered.add(problem);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    /**
     * Returns whether the source can be edited.
     *
     * @return False when the source has errors that block editing ({@link ProblemCode#blocksEditing()}).
     */
    public boolean isEditable()
    {
        return editable;
    }
}
