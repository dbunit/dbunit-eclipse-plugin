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

import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * Plans the text edits that change cell values: it validates the changes against a table, and rewrites the
 * start tag of each changed row, which is how a value is set, changed, or made NULL in flat XML. It plans
 * against the index and the text of the {@link EditContext} that it is created with, which is meant for one
 * operation.
 */
final class CellEdits
{
    private final EditContext context;

    CellEdits(final EditContext context)
    {
        this.context = context;
    }

    /**
     * Validates cell changes and returns the start-tag rewrites that make them, one per changed row
     * element, computed against the current index.
     */
    List<TextEdit> plan(final String tableKey, final DatasetTable table, final List<CellChange> changes)
    {
        requireExistingCells(table, changes);

        final Map<Integer, Map<String, String>> changesByRow = new LinkedHashMap<>();
        for (final CellChange change : changes)
        {
            final String columnKey = change.columnName().toUpperCase(Locale.ENGLISH);
            changesByRow.computeIfAbsent(change.rowIndex(), unused -> new LinkedHashMap<>())
                    .put(columnKey, change.value());
        }
        requireFirstElementColumnsKept(tableKey, table, changesByRow);

        final List<FlatXmlElement> rowElements = context.index().getRowElements(tableKey);
        final CharsetEncoder encoder = context.encoder();
        final List<TextEdit> edits = new ArrayList<>();
        final String text = context.text();
        for (final Map.Entry<Integer, Map<String, String>> entry : changesByRow.entrySet())
        {
            final int rowIndex = entry.getKey();
            final Map<String, String> rowChanges = entry.getValue();
            final FlatXmlElement element = rowElements.get(rowIndex);
            final String ambiguousKey = findCaseVariantColumnKey(element, rowChanges.keySet());
            if (ambiguousKey != null)
            {
                final String columnName = table.getColumns().get(table.getColumnIndex(ambiguousKey)).name();
                throw new DatasetEditException(NLS.bind(Messages.Edit_cellHasCaseVariantAttributes,
                        new Object[] { columnName, rowIndex, table.getName() }));
            }
            requireRowNotEmptied(table, rowIndex, rowChanges);
            final String rewritten = StartTagRewriter.rewrite(text, element, table.getColumns(),
                    rowChanges, Map.of(), encoder);
            if (rewritten != null)
            {
                edits.add(new ReplaceEdit(element.nameEndOffset(),
                        element.attributesEndOffset() - element.nameEndOffset(), rewritten));
            }
        }
        return edits;
    }

    private static void requireExistingCells(final DatasetTable table, final List<CellChange> changes)
    {
        for (final CellChange change : changes)
        {
            if (change.rowIndex() < 0 || change.rowIndex() >= table.getRows().size())
            {
                throw new DatasetEditException(
                        NLS.bind(Messages.Edit_noSuchRow, change.rowIndex(), table.getName()));
            }
            if (table.getColumnIndex(change.columnName()) < 0)
            {
                throw new DatasetEditException(
                        NLS.bind(Messages.Edit_noSuchColumn, change.columnName(), table.getName()));
            }
        }
    }

    /**
     * Refuses changes that take a column off the first row while another row keeps a value for it, because
     * dbUnit takes a table's columns from its first row and would then ignore those values.
     */
    private void requireFirstElementColumnsKept(final String tableKey, final DatasetTable table,
            final Map<Integer, Map<String, String>> changesByRow)
    {
        final Map<String, String> firstRowChanges = changesByRow.get(0);
        if (firstRowChanges == null)
        {
            return;
        }
        final FirstElementColumns firstElement = new FirstElementColumns(context, tableKey, table);
        if (firstElement.isEmpty())
        {
            return;
        }
        final Set<Integer> prospectiveColumns = new HashSet<>(firstElement.columnIndexes());
        for (final Map.Entry<String, String> change : firstRowChanges.entrySet())
        {
            final int columnIndex = table.getColumnIndex(change.getKey());
            if (change.getValue() == null)
            {
                prospectiveColumns.remove(columnIndex);
            }
            else
            {
                prospectiveColumns.add(columnIndex);
            }
        }
        firstElement.requireKept(prospectiveColumns,
                columnIndex -> hasValueInOtherRow(table, changesByRow, columnIndex));
    }

    /**
     * Returns whether a row after the first will have a value for a column once the changes are applied.
     */
    private static boolean hasValueInOtherRow(final DatasetTable table,
            final Map<Integer, Map<String, String>> changesByRow, final int columnIndex)
    {
        final String columnKey = table.getColumns().get(columnIndex).name().toUpperCase(Locale.ENGLISH);
        for (int rowIndex = 1; rowIndex < table.getRows().size(); rowIndex++)
        {
            final Map<String, String> rowChanges = changesByRow.getOrDefault(rowIndex, Map.of());
            final String value = rowChanges.containsKey(columnKey) ? rowChanges.get(columnKey)
                    : table.getRows().get(rowIndex).getValue(columnIndex);
            if (value != null)
            {
                return true;
            }
        }
        return false;
    }

    private void requireRowNotEmptied(final DatasetTable table, final int rowIndex,
            final Map<String, String> rowChanges)
    {
        if (wouldEmptyRow(table, rowIndex, rowChanges))
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_rowWouldBeEmpty, rowIndex, table.getName()));
        }
    }

    /**
     * Returns whether applying rowChanges (column key to new value) to a row would leave every column
     * NULL; a row with no values would leave its element without attributes, which is not a valid row.
     */
    private static boolean wouldEmptyRow(final DatasetTable table, final int rowIndex,
            final Map<String, String> rowChanges)
    {
        final DatasetRow row = table.getRows().get(rowIndex);
        for (int columnIndex = 0; columnIndex < table.getColumns().size(); columnIndex++)
        {
            final String columnKey = table.getColumns().get(columnIndex).name().toUpperCase(Locale.ENGLISH);
            final String value = rowChanges.containsKey(columnKey) ? rowChanges.get(columnKey)
                    : row.getValue(columnIndex);
            if (value != null)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the first key in columnKeys whose element has two or more attributes that differ only in
     * letter case, or null when none do.
     */
    private static String findCaseVariantColumnKey(final FlatXmlElement element, final Set<String> columnKeys)
    {
        for (final String columnKey : columnKeys)
        {
            if (element.hasCaseVariantAttributes(columnKey))
            {
                return columnKey;
            }
        }
        return null;
    }
}
