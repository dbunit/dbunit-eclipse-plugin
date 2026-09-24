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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Tests table lookup, per-table problem filtering, and the EMPTY constant of a dataset model.
 */
class DatasetModelTest
{
    private static final DatasetTable USERS_TABLE =
            new DatasetTable("USERS", "USERS", List.of(), List.of(), false);

    private static final DatasetProblem USERS_PROBLEM = new DatasetProblem(
            ProblemCode.TABLE_NAME_CASE_VARIANTS, ProblemSeverity.WARNING,
            "Table USERS is also spelled Users.", "USERS", null, -1, 10, 5);

    private static final DatasetProblem DOCUMENT_PROBLEM = new DatasetProblem(ProblemCode.NOT_WELL_FORMED,
            ProblemSeverity.ERROR, "The document is not well-formed.", null, null, -1, 0, 1);

    @Test
    void testFindTable_whenKeyExists_returnsTheTable()
    {
        final DatasetModel model = new DatasetModel(List.of(USERS_TABLE), List.of(), true);

        final Optional<DatasetTable> found = model.findTable("USERS");

        assertThat(found).as("findTable must return the table with a matching key.").contains(USERS_TABLE);
    }

    @Test
    void testFindTable_whenKeyIsAbsent_returnsEmpty()
    {
        final DatasetModel model = new DatasetModel(List.of(USERS_TABLE), List.of(), true);

        final Optional<DatasetTable> found = model.findTable("ORDERS");

        assertThat(found).as("findTable must return empty when no table matches.").isEmpty();
    }

    @Test
    void testGetProblemsWithTableKey_whenCalled_returnsOnlyThatTablesProblems()
    {
        final DatasetModel model = new DatasetModel(List.of(USERS_TABLE),
                List.of(USERS_PROBLEM, DOCUMENT_PROBLEM), true);

        final List<DatasetProblem> problems = model.getProblems("USERS");

        assertThat(problems).as("getProblems(tableKey) must filter by the table key.")
                .containsExactly(USERS_PROBLEM);
    }

    @Test
    void testIsEditable_whenEmpty_isFalse()
    {
        assertThat(DatasetModel.EMPTY.isEditable()).as("EMPTY must not be editable.").isFalse();
    }

    @Test
    void testGetTablesAndGetProblems_whenEmpty_areEmpty()
    {
        assertThat(DatasetModel.EMPTY.getTables()).as("EMPTY must have no tables.").isEmpty();
        assertThat(DatasetModel.EMPTY.getProblems()).as("EMPTY must have no problems.").isEmpty();
    }
}
