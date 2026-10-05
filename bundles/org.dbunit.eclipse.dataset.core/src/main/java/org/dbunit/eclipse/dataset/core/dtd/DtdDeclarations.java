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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final Map<String, Map<String, String>> attributeDefaults;

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
     * @param attributeDefaults The default or {@code #FIXED} value of each declared column that has one,
     *                          keyed by element name and then by column name; copied defensively.
     * @param problems The problems found while reading, copied defensively.
     */
    DtdDeclarations(final boolean contentModelDeclared, final boolean contentModelAny,
            final List<String> contentModelNames, final Map<String, List<String>> declaredElements,
            final Map<String, Map<String, String>> attributeDefaults, final List<DatasetProblem> problems)
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
        final Map<String, Map<String, String>> defaultsCopy = new LinkedHashMap<>();
        for (final Map.Entry<String, Map<String, String>> entry : attributeDefaults.entrySet())
        {
            defaultsCopy.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        this.attributeDefaults = defaultsCopy;
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
     * Returns a copy of these declarations with every problem's offset shifted by delta, its length
     * unchanged, so a problem found while reading an internal subset can be relocated to where that
     * subset sits in the surrounding document.
     *
     * @param delta The number of characters to add to each problem's offset.
     * @return The relocated declarations.
     */
    public DtdDeclarations withProblemsShiftedBy(final int delta)
    {
        final List<DatasetProblem> relocated = new ArrayList<>();
        for (final DatasetProblem problem : problems)
        {
            relocated.add(new DatasetProblem(problem.code(), problem.severity(), problem.message(),
                    problem.tableKey(), problem.columnName(), problem.rowIndex(),
                    problem.offset() + delta, problem.length()));
        }
        return new DtdDeclarations(contentModelDeclared, contentModelAny, contentModelNames,
                declaredElements, attributeDefaults, relocated);
    }

    /**
     * Returns a copy of these declarations with every problem's offset and length replaced by offset and
     * length, so a problem found while reading an external DTD, which is not open in the editor, points
     * at the DOCTYPE declaration instead of an arbitrary range of the dataset document.
     *
     * @param offset The new offset for every problem.
     * @param length The new length for every problem.
     * @return The relocated declarations.
     */
    public DtdDeclarations withProblemsAt(final int offset, final int length)
    {
        final List<DatasetProblem> relocated = new ArrayList<>();
        for (final DatasetProblem problem : problems)
        {
            relocated.add(new DatasetProblem(problem.code(), problem.severity(), problem.message(),
                    problem.tableKey(), problem.columnName(), problem.rowIndex(), offset, length));
        }
        return new DtdDeclarations(contentModelDeclared, contentModelAny, contentModelNames,
                declaredElements, attributeDefaults, relocated);
    }

    /**
     * Returns the DTD's tables, as dbUnit's {@code FlatDtdProducer} derives them: the content
     * model's names in order, or, for {@code ANY}, every declared element in declaration order.
     *
     * @return An unmodifiable list of tables, one per distinct name, each with the columns of its
     *         element and their default values. Empty when the {@code dataset} element has no content
     *         model.
     */
    public List<DtdTable> tables()
    {
        if (!contentModelDeclared)
        {
            return List.of();
        }
        final List<String> names =
                contentModelAny ? List.copyOf(declaredElements.keySet()) : contentModelNames;
        final Set<String> seenNames = new HashSet<>();
        final List<DtdTable> tables = new ArrayList<>();
        for (final String name : names)
        {
            if (!seenNames.add(name))
            {
                continue;
            }
            tables.add(new DtdTable(name, declaredElements.getOrDefault(name, List.of()),
                    attributeDefaults.getOrDefault(name, Map.of())));
        }
        return List.copyOf(tables);
    }

    /**
     * Returns the content model names that no {@code ELEMENT} or {@code ATTLIST} declares.
     *
     * @return An unmodifiable list, one per distinct name, in content model order.
     */
    public List<String> missingDeclarations()
    {
        if (!contentModelDeclared || contentModelAny)
        {
            return List.of();
        }
        final Set<String> seenNames = new HashSet<>();
        final List<String> missing = new ArrayList<>();
        for (final String name : contentModelNames)
        {
            if (!seenNames.add(name))
            {
                continue;
            }
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
     *         merged by name, appending later's columns after this's and skipping duplicates. A column
     *         declared in both keeps the default value of this one, as the first declaration of an
     *         attribute is binding.
     */
    public DtdDeclarations merge(final DtdDeclarations later)
    {
        final boolean mergedDeclared = contentModelDeclared || later.contentModelDeclared;
        final boolean mergedAny = contentModelDeclared ? contentModelAny : later.contentModelAny;
        final List<String> mergedNames = contentModelDeclared ? contentModelNames : later.contentModelNames;
        final Map<String, List<String>> mergedElements = new LinkedHashMap<>();
        final Map<String, Map<String, String>> mergedDefaults = new LinkedHashMap<>();
        addColumnsNotYetDeclared(mergedElements, mergedDefaults, this);
        addColumnsNotYetDeclared(mergedElements, mergedDefaults, later);
        final List<DatasetProblem> mergedProblems = new ArrayList<>(problems);
        mergedProblems.addAll(later.problems);
        return new DtdDeclarations(mergedDeclared, mergedAny, mergedNames, mergedElements, mergedDefaults,
                mergedProblems);
    }

    /**
     * Adds a source's elements, their columns, and the columns' default values to the merged ones, except
     * the columns the merged elements already have.
     */
    private static void addColumnsNotYetDeclared(final Map<String, List<String>> mergedElements,
            final Map<String, Map<String, String>> mergedDefaults, final DtdDeclarations source)
    {
        for (final Map.Entry<String, List<String>> entry : source.declaredElements.entrySet())
        {
            final String elementName = entry.getKey();
            final List<String> columns =
                    mergedElements.computeIfAbsent(elementName, key -> new ArrayList<>());
            final Map<String, String> sourceDefaults =
                    source.attributeDefaults.getOrDefault(elementName, Map.of());
            for (final String column : entry.getValue())
            {
                if (!columns.contains(column))
                {
                    columns.add(column);
                    addDefault(mergedDefaults, elementName, column, sourceDefaults.get(column));
                }
            }
        }
    }

    private static void addDefault(final Map<String, Map<String, String>> mergedDefaults,
            final String elementName, final String column, final String defaultValue)
    {
        if (defaultValue != null)
        {
            final Map<String, String> elementDefaults =
                    mergedDefaults.computeIfAbsent(elementName, key -> new LinkedHashMap<>());
            elementDefaults.put(column, defaultValue);
        }
    }
}
