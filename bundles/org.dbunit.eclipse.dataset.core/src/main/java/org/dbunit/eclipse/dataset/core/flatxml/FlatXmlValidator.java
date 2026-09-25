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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            final ColumnOccurrences occurrences =
                    ColumnOccurrences.scan(table, index.getRowElements(table.getKey()));
            validateFirstRowColumns(problems, index, table, occurrences, dtdState, options);
            validateTableNameCaseVariants(problems, index, table, options);
            validateColumnNameCaseVariants(problems, table, occurrences);
            validateDuplicateColumnInRow(problems, table, occurrences);
            validateTableNotDeclaredInDtd(problems, index, table, dtdState, dtdTableKeys);
            validateColumnNotDeclaredInDtd(problems, table, occurrences, dtdState, options, dtdTableKeys);
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
            final FlatXmlIndex index, final DatasetTable table, final ColumnOccurrences occurrences,
            final DtdState dtdState, final FlatXmlOptions options)
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
        // The first element has attributes, so it is the table's first row.
        for (final DatasetColumn column : table.getColumns())
        {
            final Occurrence occurrence = occurrences.firstValue(column.name());
            if (occurrence == null || occurrence.rowIndex() == 0)
            {
                continue;
            }
            problems.add(new DatasetProblem(ProblemCode.COLUMN_NOT_IN_FIRST_ROW, ProblemSeverity.WARNING,
                    NLS.bind(Messages.Validator_columnNotInFirstRow, column.name(), table.getName()),
                    table.getKey(), column.name(), occurrence.rowIndex(), occurrence.attribute().nameOffset(),
                    occurrence.attribute().name().length()));
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
            final DatasetTable table, final ColumnOccurrences occurrences)
    {
        for (final DatasetColumn column : table.getColumns())
        {
            final FlatXmlAttribute variant = occurrences.firstSpellingVariant(column.name());
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

    private static void validateDuplicateColumnInRow(final List<DatasetProblem> problems,
            final DatasetTable table, final ColumnOccurrences occurrences)
    {
        for (final FlatXmlAttribute attribute : occurrences.duplicates())
        {
            problems.add(new DatasetProblem(ProblemCode.DUPLICATE_COLUMN_IN_ROW, ProblemSeverity.WARNING,
                    NLS.bind(Messages.Validator_duplicateColumnInRow, attribute.name()), table.getKey(), null,
                    -1, attribute.nameOffset(), attribute.name().length()));
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
            final DatasetTable table, final ColumnOccurrences occurrences, final DtdState dtdState,
            final FlatXmlOptions options, final Set<String> dtdTableKeys)
    {
        if (dtdState != DtdState.LOADED || !dtdTableKeys.contains(table.getKey()))
        {
            return;
        }
        final ProblemSeverity severity =
                options.columnSensing() ? ProblemSeverity.ERROR : ProblemSeverity.WARNING;
        for (final DatasetColumn column : table.getColumns())
        {
            if (column.declared())
            {
                continue;
            }
            final Occurrence occurrence = occurrences.firstValue(column.name());
            if (occurrence == null)
            {
                continue;
            }
            final String message = severity == ProblemSeverity.ERROR
                    ? NLS.bind(Messages.Validator_columnNotDeclaredInDtdError, column.name(), table.getName())
                    : NLS.bind(Messages.Validator_columnNotDeclaredInDtdWarning, column.name(),
                            table.getName());
            final FlatXmlAttribute attribute = occurrence.attribute();
            problems.add(new DatasetProblem(ProblemCode.COLUMN_NOT_DECLARED_IN_DTD, severity, message,
                    table.getKey(), column.name(), -1, attribute.nameOffset(), attribute.name().length()));
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

    private static String columnKey(final String name)
    {
        return name.toUpperCase(Locale.ENGLISH);
    }

    /**
     * Where a column first has a value.
     *
     * @param rowIndex The index of the first row that has a value for the column.
     * @param attribute The attribute that supplies that row's value: the last of the row's attributes
     *                  for the column, as in dbUnit.
     */
    private record Occurrence(int rowIndex, FlatXmlAttribute attribute)
    {
    }

    /**
     * What one pass over a table's rows finds about its columns, so that the checks take time in
     * proportion to the number of attributes, however many columns the table has.
     */
    private static final class ColumnOccurrences
    {
        private final Map<String, Occurrence> firstValues = new HashMap<>();

        private final Map<String, FlatXmlAttribute> firstSpellingVariants = new HashMap<>();

        private final List<FlatXmlAttribute> duplicates = new ArrayList<>();

        private ColumnOccurrences()
        {
        }

        /**
         * Scans a table's rows in document order.
         *
         * @param table The table, whose column names are the expected spellings.
         * @param rowElements The table's row elements, in document order.
         * @return What the scan found.
         */
        static ColumnOccurrences scan(final DatasetTable table, final List<FlatXmlElement> rowElements)
        {
            final Map<String, String> columnNamesByKey = new HashMap<>();
            for (final DatasetColumn column : table.getColumns())
            {
                columnNamesByKey.put(columnKey(column.name()), column.name());
            }
            final ColumnOccurrences occurrences = new ColumnOccurrences();
            final Map<String, FlatXmlAttribute> rowAttributesByKey = new LinkedHashMap<>();
            for (int rowIndex = 0; rowIndex < rowElements.size(); rowIndex++)
            {
                rowAttributesByKey.clear();
                for (final FlatXmlAttribute attribute : rowElements.get(rowIndex).attributes())
                {
                    final String key = columnKey(attribute.name());
                    if (rowAttributesByKey.put(key, attribute) != null)
                    {
                        occurrences.duplicates.add(attribute);
                    }
                    final String columnName = columnNamesByKey.get(key);
                    if (columnName != null && !attribute.name().equals(columnName)
                            && !occurrences.firstSpellingVariants.containsKey(key))
                    {
                        occurrences.firstSpellingVariants.put(key, attribute);
                    }
                }
                for (final Map.Entry<String, FlatXmlAttribute> entry : rowAttributesByKey.entrySet())
                {
                    final String key = entry.getKey();
                    if (!occurrences.firstValues.containsKey(key))
                    {
                        occurrences.firstValues.put(key, new Occurrence(rowIndex, entry.getValue()));
                    }
                }
            }
            return occurrences;
        }

        /**
         * Returns where a column first has a value.
         *
         * @param columnName The column's name, matched case-insensitively.
         * @return The first row with a value and the attribute that supplies it, or null when no row has
         *         a value.
         */
        Occurrence firstValue(final String columnName)
        {
            return firstValues.get(columnKey(columnName));
        }

        /**
         * Returns the first attribute that spells a column differently from the column's name.
         *
         * @param columnName The column's name, matched case-insensitively.
         * @return That attribute, or null when every attribute spells the column as its name does.
         */
        FlatXmlAttribute firstSpellingVariant(final String columnName)
        {
            return firstSpellingVariants.get(columnKey(columnName));
        }

        /**
         * Returns the attributes whose element has an earlier attribute for the same column.
         *
         * @return Those attributes, in document order.
         */
        List<FlatXmlAttribute> duplicates()
        {
            return duplicates;
        }
    }
}
