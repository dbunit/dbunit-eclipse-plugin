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
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * Plans the text edits that rename and delete columns, and checks the names of new columns. A column is not
 * an element of a flat XML document: renaming or deleting it rewrites the start tag of every row of its
 * table, which is why a column that no row has, one that a row spells in two ways, or the only column of a
 * row cannot be changed. The edits are planned against the index and the text of the {@link EditContext}
 * that this is created with, which is meant for one operation.
 */
final class ColumnEdits
{
    private final EditContext context;

    ColumnEdits(final EditContext context)
    {
        this.context = context;
    }

    /**
     * Requires that a new column of a table can have the name: the name must be a valid XML name, and no
     * column of the table may have it.
     *
     * @param table The table that gets the column.
     * @param columnName The name of the new column.
     * @throws DatasetEditException When the name cannot be used.
     */
    static void requireNewColumn(final DatasetTable table, final String columnName)
    {
        requireValidColumnName(columnName);
        requireAvailableColumnName(table, columnName, -1);
    }

    /**
     * Returns the column of a table with a name.
     *
     * @param table The table.
     * @param columnName The name of the column, in any letter case.
     * @return The column.
     * @throws DatasetEditException When the table has no such column.
     */
    static DatasetColumn requireExistingColumn(final DatasetTable table, final String columnName)
    {
        final int columnIndex = requireColumnIndex(table, columnName);
        return table.getColumns().get(columnIndex);
    }

    /**
     * Validates renaming a column: the column must exist, the new name must be a valid XML name that no other
     * column has, and no row may spell the column in two ways.
     *
     * @param tableKey The key of the table.
     * @param table The table.
     * @param columnName The current name of the column, in any letter case.
     * @param newColumnName The new name.
     * @return The column that is renamed.
     * @throws DatasetEditException When the column cannot be renamed.
     */
    DatasetColumn requireRename(final String tableKey, final DatasetTable table, final String columnName,
            final String newColumnName)
    {
        final int columnIndex = requireColumnIndex(table, columnName);
        requireValidColumnName(newColumnName);
        requireAvailableColumnName(table, newColumnName, columnIndex);

        final DatasetColumn column = table.getColumns().get(columnIndex);
        final String key = columnName.toUpperCase(Locale.ENGLISH);
        final List<FlatXmlElement> rowElements = context.index().getRowElements(tableKey);
        for (final FlatXmlElement element : rowElements)
        {
            if (element.hasCaseVariantAttributes(key))
            {
                throw new DatasetEditException(NLS.bind(Messages.Edit_renameColumnWithCaseVariants,
                        column.name(), table.getName()));
            }
        }
        return column;
    }

    /**
     * Plans renaming a column that rows have: the start tag of each row is rewritten with the new name.
     *
     * @param tableKey The key of the table.
     * @param table The table.
     * @param column The column, as {@link #requireRename} returned it.
     * @param columnName The current name of the column, in any letter case.
     * @param newColumnName The new name.
     * @return The edits, one for each row that has the column.
     * @throws DatasetEditException When the column is only declared, so that no row has it.
     */
    List<TextEdit> renameEdits(final String tableKey, final DatasetTable table, final DatasetColumn column,
            final String columnName, final String newColumnName)
    {
        requireRenamableColumn(table, column);

        final String key = columnName.toUpperCase(Locale.ENGLISH);
        final List<FlatXmlElement> rowElements = context.index().getRowElements(tableKey);
        final Map<String, String> renames = new LinkedHashMap<>();
        renames.put(key, newColumnName);
        return startTagEdits(table, rowElements, Map.of(), renames);
    }

    /**
     * Plans deleting a column that rows have: the start tag of each row is rewritten without the column.
     *
     * @param tableKey The key of the table.
     * @param table The table.
     * @param column The column.
     * @param columnName The name of the column, in any letter case.
     * @return The edits, one for each row that has the column.
     * @throws DatasetEditException When a row has no other column, so that it would be left empty.
     */
    List<TextEdit> deleteEdits(final String tableKey, final DatasetTable table, final DatasetColumn column,
            final String columnName)
    {
        final String key = columnName.toUpperCase(Locale.ENGLISH);
        final List<FlatXmlElement> rowElements = context.index().getRowElements(tableKey);
        requireNoRowLeftEmpty(table, column, rowElements, key);

        final Map<String, String> changes = new LinkedHashMap<>();
        changes.put(key, null);
        return startTagEdits(table, rowElements, changes, Map.of());
    }

    static void requireNewColumnNames(final String tableName, final List<String> columnNames)
    {
        final Set<String> seenColumnKeys = new HashSet<>();
        for (final String columnName : columnNames)
        {
            requireValidColumnName(columnName);
            final String columnKey = columnName.toUpperCase(Locale.ENGLISH);
            if (!seenColumnKeys.add(columnKey))
            {
                throw new DatasetEditException(NLS.bind(Messages.Edit_columnExists, tableName, columnName));
            }
        }
    }

    private static int requireColumnIndex(final DatasetTable table, final String columnName)
    {
        final int columnIndex = table.getColumnIndex(columnName);
        if (columnIndex < 0)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_noSuchColumn, columnName, table.getName()));
        }
        return columnIndex;
    }

    private static void requireValidColumnName(final String columnName)
    {
        if (!XmlNames.isValidName(columnName))
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_invalidColumnName, columnName));
        }
    }

    /**
     * Requires that no other column of the table has the name.
     *
     * @param ownColumnIndex The index of the column that gets the name, or -1 for a new column.
     */
    private static void requireAvailableColumnName(final DatasetTable table, final String columnName,
            final int ownColumnIndex)
    {
        final int existingIndex = table.getColumnIndex(columnName);
        if (existingIndex >= 0 && existingIndex != ownColumnIndex)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_columnExists, table.getName(), columnName));
        }
    }

    private static void requireRenamableColumn(final DatasetTable table, final DatasetColumn column)
    {
        if (column.declared() && !column.hasValues())
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_renameColumnIsDeclaredOnly,
                    column.name(), table.getName()));
        }
    }

    private static void requireNoRowLeftEmpty(final DatasetTable table, final DatasetColumn column,
            final List<FlatXmlElement> rowElements, final String key)
    {
        for (final FlatXmlElement element : rowElements)
        {
            if (hasOnlyColumn(element, key))
            {
                throw new DatasetEditException(NLS.bind(Messages.Edit_deleteColumnWouldEmptyRow,
                        column.name(), table.getName()));
            }
        }
    }

    private static boolean hasOnlyColumn(final FlatXmlElement element, final String key)
    {
        boolean hasColumn = false;
        int remaining = 0;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            if (attribute.name().toUpperCase(Locale.ENGLISH).equals(key))
            {
                hasColumn = true;
            }
            else
            {
                remaining++;
            }
        }
        return hasColumn && remaining == 0;
    }

    /**
     * Returns the start-tag rewrites that apply changes and renames to the attributes of row elements.
     */
    private List<TextEdit> startTagEdits(final DatasetTable table, final List<FlatXmlElement> rowElements,
            final Map<String, String> changes, final Map<String, String> renames)
    {
        final CharsetEncoder encoder = context.encoder();
        final String text = context.text();
        final List<TextEdit> edits = new ArrayList<>();
        for (final FlatXmlElement element : rowElements)
        {
            final String rewritten =
                    StartTagRewriter.rewrite(text, element, table.getColumns(), changes, renames, encoder);
            if (rewritten != null)
            {
                edits.add(new ReplaceEdit(element.nameEndOffset(),
                        element.attributesEndOffset() - element.nameEndOffset(), rewritten));
            }
        }
        return edits;
    }
}
