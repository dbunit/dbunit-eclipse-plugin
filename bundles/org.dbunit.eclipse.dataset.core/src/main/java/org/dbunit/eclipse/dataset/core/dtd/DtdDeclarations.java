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
package org.dbunit.eclipse.dataset.core.dtd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.model.DatasetProblem;

/**
 * The declarations of a DTD relevant to a flat XML dataset: the {@code dataset} element's content model
 * and each declared element's columns, in declaration order.
 *
 * @since 1.0.0
 */
public final class DtdDeclarations
{
    private final boolean contentModelDeclared;

    private final boolean contentModelAny;

    private final List<String> contentModelNames;

    private final Map<String, List<String>> declaredElements;

    private final List<DatasetProblem> problems;

    /**
     * Creates DTD declarations.
     *
     * @param contentModelDeclared True when the {@code dataset} element has an {@code ELEMENT}
     *                             declaration.
     * @param contentModelAny True when that content model is {@code ANY}; meaningless otherwise.
     * @param contentModelNames The content model's names, in order; empty when contentModelAny is true
     *                          or contentModelDeclared is false.
     * @param declaredElements Each declared element's columns, in declaration order, keyed by element
     *                         name in declaration order; copied defensively.
     * @param problems The problems found while reading, copied defensively.
     */
    DtdDeclarations(final boolean contentModelDeclared, final boolean contentModelAny,
            final List<String> contentModelNames, final Map<String, List<String>> declaredElements,
            final List<DatasetProblem> problems)
    {
        this.contentModelDeclared = contentModelDeclared;
        this.contentModelAny = contentModelAny;
        this.contentModelNames = List.copyOf(contentModelNames);
        final Map<String, List<String>> copy = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : declaredElements.entrySet())
        {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.declaredElements = copy;
        this.problems = List.copyOf(problems);
    }

    /**
     * Returns the problems found while reading the DTD.
     *
     * @return An unmodifiable list of problems.
     */
    public List<DatasetProblem> getProblems()
    {
        return problems;
    }

    /**
     * Returns the DTD's tables, as dbUnit's {@code FlatDtdProducer} derives them: the content
     * model's names in order, or, for {@code ANY}, every declared element in declaration order.
     *
     * @return An unmodifiable list of tables, each with the columns of its element. Empty when the
     *         {@code dataset} element has no content model.
     */
    public List<DtdTable> tables()
    {
        if (!contentModelDeclared)
        {
            return List.of();
        }
        final List<String> names =
                contentModelAny ? List.copyOf(declaredElements.keySet()) : contentModelNames;
        final List<DtdTable> tables = new ArrayList<>();
        for (final String name : names)
        {
            tables.add(new DtdTable(name, declaredElements.getOrDefault(name, List.of())));
        }
        return List.copyOf(tables);
    }

    /**
     * Returns the content model names that no {@code ELEMENT} or {@code ATTLIST} declares.
     *
     * @return An unmodifiable list, in content model order.
     */
    public List<String> missingDeclarations()
    {
        if (!contentModelDeclared || contentModelAny)
        {
            return List.of();
        }
        final List<String> missing = new ArrayList<>();
        for (final String name : contentModelNames)
        {
            if (!declaredElements.containsKey(name))
            {
                missing.add(name);
            }
        }
        return List.copyOf(missing);
    }

    /**
     * Combines these declarations, read from a DOCTYPE's internal subset, with those of an external DTD,
     * in the order XML parsers read them.
     *
     * @param later The declarations of the external DTD.
     * @return The merged declarations: this content model when declared, otherwise later's; elements
     *         merged by name, appending later's columns after this's and skipping duplicates.
     */
    public DtdDeclarations merge(final DtdDeclarations later)
    {
        final boolean mergedDeclared = contentModelDeclared || later.contentModelDeclared;
        final boolean mergedAny = contentModelDeclared ? contentModelAny : later.contentModelAny;
        final List<String> mergedNames = contentModelDeclared ? contentModelNames : later.contentModelNames;
        final Map<String, List<String>> mergedElements = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : declaredElements.entrySet())
        {
            mergedElements.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (final Map.Entry<String, List<String>> entry : later.declaredElements.entrySet())
        {
            final List<String> columns =
                    mergedElements.computeIfAbsent(entry.getKey(), key -> new ArrayList<>());
            for (final String column : entry.getValue())
            {
                if (!columns.contains(column))
                {
                    columns.add(column);
                }
            }
        }
        final List<DatasetProblem> mergedProblems = new ArrayList<>(problems);
        mergedProblems.addAll(later.problems);
        return new DtdDeclarations(mergedDeclared, mergedAny, mergedNames, mergedElements, mergedProblems);
    }
}
