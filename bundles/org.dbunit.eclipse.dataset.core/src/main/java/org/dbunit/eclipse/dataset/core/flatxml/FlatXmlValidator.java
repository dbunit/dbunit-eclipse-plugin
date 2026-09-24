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
package org.dbunit.eclipse.dataset.core.flatxml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdTable;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.osgi.util.NLS;

/**
 * Warns about flat XML content dbUnit ignores or that would make dbUnit fail to load the dataset,
 * without blocking editing.
 */
final class FlatXmlValidator
{
    private FlatXmlValidator()
    {
    }

    /**
     * Validates a built model against dbUnit's flat XML semantics.
     *
     * @param parse The parse result the model was built from; its own problems are passed through.
     * @param index The index of the same parse.
     * @param tables The model's tables.
     * @param dtdState How the document relates to a DTD.
     * @param dtd The merged DTD declarations, or null when dtdState is {@link DtdState#NONE}; its own
     *            problems are passed through.
     * @param options The case-sensitivity and column-sensing options.
     * @return The parser's and the DTD reader's problems, followed by the problems found here.
     */
    static List<DatasetProblem> validate(final FlatXmlParseResult parse, final FlatXmlIndex index,
            final List<DatasetTable> tables, final DtdState dtdState, final DtdDeclarations dtd,
            final FlatXmlOptions options)
    {
        final List<DatasetProblem> problems = new ArrayList<>(parse.problems());
        if (dtd != null)
        {
            problems.addAll(dtd.getProblems());
        }
        final Set<String> dtdTableKeys = dtdTableKeys(dtdState, dtd, options);
        for (final DatasetTable table : tables)
        {
            validateFirstRowColumns(problems, index, table, dtdState, options);
            validateTableNameCaseVariants(problems, index, table, options);
            validateColumnNameCaseVariants(problems, index, table);
            validateDuplicateColumnInRow(problems, index, table);
            validateTableNotDeclaredInDtd(problems, index, table, dtdState, dtdTableKeys);
            validateColumnNotDeclaredInDtd(problems, index, table, dtdState, options, dtdTableKeys);
            validateRedundantEmptyElement(problems, index, table);
        }
        validateDtdTableWithoutDeclaration(problems, index, dtdState, dtd);
        validateDtdNotLoaded(problems, index, dtdState);
        return List.copyOf(problems);
    }

    private static Set<String> dtdTableKeys(final DtdState dtdState, final DtdDeclarations dtd,
            final FlatXmlOptions options)
    {
        final Set<String> keys = new HashSet<>();
        if (dtdState == DtdState.LOADED && dtd != null)
        {
            for (final DtdTable dtdTable : dtd.tables())
            {
                keys.add(tableKey(dtdTable.name(), options));
            }
        }
        return keys;
    }

    private static void validateFirstRowColumns(final List<DatasetProblem> problems,
            final FlatXmlIndex index, final DatasetTable table, final DtdState dtdState,
            final FlatXmlOptions options)
    {
        if (dtdState != DtdState.NONE || options.columnSensing())
        {
            return;
        }
        final List<FlatXmlElement> elements = index.getAllElementsInOrder(table.getKey());
        if (elements.isEmpty())
        {
            return;
        }
        final FlatXmlElement first = elements.get(0);
        if (first.attributes().isEmpty())
        {
            if (!table.getRows().isEmpty())
            {
                problems.add(new DatasetProblem(ProblemCode.FIRST_ELEMENT_WITHOUT_ATTRIBUTES,
                        ProblemSeverity.WARNING,
                        NLS.bind(Messages.Validator_firstElementWithoutAttributes, table.getName()),
                        table.getKey(), null, -1, first.offset(),
                        first.nameEndOffset() - first.offset()));
            }
            return;
        }
        final Set<String> firstElementColumnKeys = new HashSet<>();
        for (final FlatXmlAttribute attribute : first.attributes())
        {
            firstElementColumnKeys.add(attribute.name().toUpperCase(Locale.ENGLISH));
        }
        final List<FlatXmlElement> rowElements = index.getRowElements(table.getKey());
        for (final DatasetColumn column : table.getColumns())
        {
            final String columnKey = column.name().toUpperCase(Locale.ENGLISH);
            if (firstElementColumnKeys.contains(columnKey))
            {
                continue;
            }
            reportFirstLaterValue(problems, table, rowElements, columnKey, column.name());
        }
    }

    private static void reportFirstLaterValue(final List<DatasetProblem> problems,
            final DatasetTable table, final List<FlatXmlElement> rowElements, final String columnKey,
            final String columnName)
    {
        for (int rowIndex = 1; rowIndex < rowElements.size(); rowIndex++)
        {
            final FlatXmlAttribute attribute = findAttribute(rowElements.get(rowIndex), columnKey);
            if (attribute != null)
            {
                problems.add(new DatasetProblem(ProblemCode.COLUMN_NOT_IN_FIRST_ROW,
                        ProblemSeverity.WARNING,
                        NLS.bind(Messages.Validator_columnNotInFirstRow, columnName, table.getName()),
                        table.getKey(), columnName, rowIndex, attribute.nameOffset(),
                        attribute.name().length()));
                return;
            }
        }
    }

    private static void validateTableNameCaseVariants(final List<DatasetProblem> problems,
            final FlatXmlIndex index, final DatasetTable table, final FlatXmlOptions options)
    {
        if (options.caseSensitiveTableNames())
        {
            return;
        }
        for (final FlatXmlElement element : index.getAllElementsInOrder(table.getKey()))
        {
            if (!element.name().equals(table.getName()))
            {
                problems.add(new DatasetProblem(ProblemCode.TABLE_NAME_CASE_VARIANTS,
                        ProblemSeverity.WARNING,
                        NLS.bind(Messages.Validator_tableNameCaseVariants, table.getName(),
                                element.name()),
                        table.getKey(), null, -1, element.offset(),
                        element.nameEndOffset() - element.offset()));
                return;
            }
        }
    }

    private static void validateColumnNameCaseVariants(final List<DatasetProblem> problems,
            final FlatXmlIndex index, final DatasetTable table)
    {
        for (final DatasetColumn column : table.getColumns())
        {
            final String columnKey = column.name().toUpperCase(Locale.ENGLISH);
            final FlatXmlAttribute variant = findSpellingVariant(index.getRowElements(table.getKey()),
                    columnKey, column.name());
            if (variant != null)
            {
                problems.add(new DatasetProblem(ProblemCode.COLUMN_NAME_CASE_VARIANTS,
                        ProblemSeverity.WARNING,
                        NLS.bind(Messages.Validator_columnNameCaseVariants,
                                new Object[] { column.name(), table.getName(), variant.name() }),
                        table.getKey(), column.name(), -1, variant.nameOffset(),
                        variant.name().length()));
            }
        }
    }

    private static FlatXmlAttribute findSpellingVariant(final List<FlatXmlElement> rowElements,
            final String columnKey, final String columnName)
    {
        for (final FlatXmlElement row : rowElements)
        {
            for (final FlatXmlAttribute attribute : row.attributes())
            {
                if (attribute.name().toUpperCase(Locale.ENGLISH).equals(columnKey)
                        && !attribute.name().equals(columnName))
                {
                    return attribute;
                }
            }
        }
        return null;
    }

    private static void validateDuplicateColumnInRow(final List<DatasetProblem> problems,
            final FlatXmlIndex index, final DatasetTable table)
    {
        for (final FlatXmlElement row : index.getRowElements(table.getKey()))
        {
            final Set<String> seen = new HashSet<>();
            for (final FlatXmlAttribute attribute : row.attributes())
            {
                if (!seen.add(attribute.name().toUpperCase(Locale.ENGLISH)))
                {
                    problems.add(new DatasetProblem(ProblemCode.DUPLICATE_COLUMN_IN_ROW,
                            ProblemSeverity.WARNING,
                            NLS.bind(Messages.Validator_duplicateColumnInRow, attribute.name()),
                            table.getKey(), null, -1, attribute.nameOffset(),
                            attribute.name().length()));
                }
            }
        }
    }

    private static void validateTableNotDeclaredInDtd(final List<DatasetProblem> problems,
            final FlatXmlIndex index, final DatasetTable table, final DtdState dtdState,
            final Set<String> dtdTableKeys)
    {
        if (dtdState != DtdState.LOADED || table.isDeclaredOnly() || dtdTableKeys.contains(table.getKey()))
        {
            return;
        }
        final List<FlatXmlElement> elements = index.getAllElementsInOrder(table.getKey());
        if (elements.isEmpty())
        {
            return;
        }
        final FlatXmlElement first = elements.get(0);
        problems.add(new DatasetProblem(ProblemCode.TABLE_NOT_DECLARED_IN_DTD, ProblemSeverity.ERROR,
                NLS.bind(Messages.Validator_tableNotDeclaredInDtd, table.getName()), table.getKey(), null,
                -1, first.offset(), first.nameEndOffset() - first.offset()));
    }

    private static void validateColumnNotDeclaredInDtd(final List<DatasetProblem> problems,
            final FlatXmlIndex index, final DatasetTable table, final DtdState dtdState,
            final FlatXmlOptions options, final Set<String> dtdTableKeys)
    {
        if (dtdState != DtdState.LOADED || !dtdTableKeys.contains(table.getKey()))
        {
            return;
        }
        final ProblemSeverity severity =
                options.columnSensing() ? ProblemSeverity.ERROR : ProblemSeverity.WARNING;
        final List<FlatXmlElement> rowElements = index.getRowElements(table.getKey());
        for (final DatasetColumn column : table.getColumns())
        {
            if (column.declared())
            {
                continue;
            }
            final String columnKey = column.name().toUpperCase(Locale.ENGLISH);
            for (final FlatXmlElement row : rowElements)
            {
                final FlatXmlAttribute attribute = findAttribute(row, columnKey);
                if (attribute != null)
                {
                    final String message = severity == ProblemSeverity.ERROR
                            ? NLS.bind(Messages.Validator_columnNotDeclaredInDtdError, column.name(),
                                    table.getName())
                            : NLS.bind(Messages.Validator_columnNotDeclaredInDtdWarning, column.name(),
                                    table.getName());
                    problems.add(new DatasetProblem(ProblemCode.COLUMN_NOT_DECLARED_IN_DTD, severity,
                            message, table.getKey(), column.name(), -1, attribute.nameOffset(),
                            attribute.name().length()));
                    break;
                }
            }
        }
    }

    private static void validateDtdTableWithoutDeclaration(final List<DatasetProblem> problems,
            final FlatXmlIndex index, final DtdState dtdState, final DtdDeclarations dtd)
    {
        if (dtdState != DtdState.LOADED || dtd == null)
        {
            return;
        }
        for (final String name : dtd.missingDeclarations())
        {
            problems.add(new DatasetProblem(ProblemCode.DTD_TABLE_WITHOUT_DECLARATION,
                    ProblemSeverity.ERROR, NLS.bind(Messages.Validator_dtdTableWithoutDeclaration, name),
                    null, null, -1, doctypeOffset(index), doctypeLength(index)));
        }
    }

    private static void validateDtdNotLoaded(final List<DatasetProblem> problems,
            final FlatXmlIndex index, final DtdState dtdState)
    {
        if (dtdState != DtdState.NOT_LOADED)
        {
            return;
        }
        problems.add(new DatasetProblem(ProblemCode.DTD_NOT_LOADED, ProblemSeverity.WARNING,
                Messages.Validator_dtdNotLoaded, null, null, -1, doctypeOffset(index),
                doctypeLength(index)));
    }

    private static void validateRedundantEmptyElement(final List<DatasetProblem> problems,
            final FlatXmlIndex index, final DatasetTable table)
    {
        if (table.getRows().isEmpty())
        {
            return;
        }
        final List<FlatXmlElement> elements = index.getAllElementsInOrder(table.getKey());
        final FlatXmlElement first = elements.isEmpty() ? null : elements.get(0);
        for (final FlatXmlElement marker : index.getMarkerElements(table.getKey()))
        {
            if (marker != first)
            {
                problems.add(new DatasetProblem(ProblemCode.REDUNDANT_EMPTY_ELEMENT, ProblemSeverity.INFO,
                        NLS.bind(Messages.Validator_redundantEmptyElement, table.getName()),
                        table.getKey(), null, -1, marker.offset(), marker.endOffset() - marker.offset()));
            }
        }
    }

    private static FlatXmlAttribute findAttribute(final FlatXmlElement element, final String columnKey)
    {
        FlatXmlAttribute found = null;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            if (attribute.name().toUpperCase(Locale.ENGLISH).equals(columnKey))
            {
                found = attribute;
            }
        }
        return found;
    }

    private static int doctypeOffset(final FlatXmlIndex index)
    {
        return index.getDoctype() != null ? index.getDoctype().offset() : -1;
    }

    private static int doctypeLength(final FlatXmlIndex index)
    {
        return index.getDoctype() != null ? index.getDoctype().endOffset() - index.getDoctype().offset()
                : 0;
    }

    private static String tableKey(final String name, final FlatXmlOptions options)
    {
        return options.caseSensitiveTableNames() ? name : name.toUpperCase(Locale.ENGLISH);
    }
}
