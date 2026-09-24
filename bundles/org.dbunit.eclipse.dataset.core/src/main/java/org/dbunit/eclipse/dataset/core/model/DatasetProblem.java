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

/**
 * A problem found while building or validating a dataset model.
 *
 * @param code The kind of problem.
 * @param severity The severity of the problem.
 * @param message The complete, user-facing message.
 * @param tableKey The key of the table the problem belongs to, or null for a document-level problem.
 * @param columnName The name of the column the problem belongs to, or null.
 * @param rowIndex The index of the row the problem belongs to, or -1.
 * @param offset The document offset the problem points to, or -1 when unknown.
 * @param length The length of the text range the problem points to, or 0.
 * @since 1.0.0
 */
public record DatasetProblem(ProblemCode code, ProblemSeverity severity, String message, String tableKey,
        String columnName, int rowIndex, int offset, int length)
{
}
