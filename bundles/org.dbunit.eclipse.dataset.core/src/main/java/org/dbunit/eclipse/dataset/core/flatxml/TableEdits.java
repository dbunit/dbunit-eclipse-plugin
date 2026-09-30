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
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.text.IRegion;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * Plans the text edits that add, rename, and delete tables, and checks the names of tables. A table is
 * nothing but the elements that share a name: adding one inserts an empty element, renaming one rewrites the
 * start tag and the end tag of each of its elements, and deleting one removes them. It plans against the
 * index and the layout of the {@link EditContext}, and checks names against the model that it is created
 * with, all of which are meant for one operation.
 */
final class TableEdits
{
    private final FlatXmlIndex index;

    private final FlatXmlTextLayout layout;

    private final DatasetModel model;

    private final UnaryOperator<String> tableKeyOf;

    /**
     * Creates the planner of an operation.
     *
     * @param context The context of the operation.
     * @param model The model of the parse that the context describes.
     * @param tableKeyOf Turns a table name into its key, which decides which names are the same table.
     */
    TableEdits(final EditContext context, final DatasetModel model, final UnaryOperator<String> tableKeyOf)
    {
        this.index = context.index();
        this.layout = context.layout();
        this.model = model;
        this.tableKeyOf = tableKeyOf;
    }

    /**
     * Plans adding an empty table as the last element of the dataset.
     *
     * @param tableName The name of the new table.
     * @param columnNames The names of the columns that the table starts with.
     * @return The edit that inserts the element.
     * @throws DatasetEditException When the table name or a column name cannot be used.
     */
    List<TextEdit> addEdits(final String tableName, final List<String> columnNames)
    {
        requireValidTableName(tableName);
        requireUnusedTableName(tableName, Set.of());
        ColumnEdits.requireNewColumnNames(tableName, columnNames);

        final TextEdit insertion =
                layout.insertAsLastChildOfRoot(index.getRoot(), index.getElements(), "<" + tableName + "/>");
        return List.of(insertion);
    }

    /**
     * Plans renaming a table: the start tag and the end tag of each of its elements get the new name.
     *
     * @param tableKey The key of the table.
     * @param table The table.
     * @param newTableName The new name.
     * @return The edits; none when the table has no element, which a table that a DTD declares does not.
     * @throws DatasetEditException When the table is only declared or the new name cannot be used.
     */
    List<TextEdit> renameEdits(final String tableKey, final DatasetTable table, final String newTableName)
    {
        if (table.isDeclaredOnly())
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_renameTableIsDeclaredOnly, table.getName()));
        }
        requireValidTableName(newTableName);
        requireUnusedTableName(newTableName, Set.of(tableKey));

        final List<FlatXmlElement> elements = index.getAllElementsInOrder(tableKey);
        return tableNameEdits(elements, newTableName);
    }

    /**
     * Plans deleting a table: each of its elements goes with its line when it is alone on it.
     *
     * @param tableKey The key of the table.
     * @param table The table.
     * @return The edits.
     * @throws DatasetEditException When the table is only declared, so that there is nothing to delete.
     */
    List<TextEdit> deleteEdits(final String tableKey, final DatasetTable table)
    {
        if (table.isDeclaredOnly())
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_deleteTableIsDeclaredOnly, table.getName()));
        }

        final List<FlatXmlElement> elements = index.getAllElementsInOrder(tableKey);
        final List<TextEdit> edits = new ArrayList<>();
        for (final FlatXmlElement element : elements)
        {
            final IRegion region = layout.lineExtent(element);
            edits.add(new DeleteEdit(region.getOffset(), region.getLength()));
        }
        return edits;
    }

    private void requireValidTableName(final String tableName)
    {
        if (!XmlNames.isValidName(tableName))
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_invalidTableName, tableName));
        }
        if (isReservedRootName(tableName))
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_reservedTableName, tableName));
        }
    }

    /**
     * Requires that no table has the name, other than the tables with the keys to ignore.
     *
     * @param ignoredTableKeys The keys of the tables that may have the name: the one that is being renamed.
     */
    private void requireUnusedTableName(final String tableName, final Set<String> ignoredTableKeys)
    {
        for (final DatasetTable existing : model.getTables())
        {
            if (!ignoredTableKeys.contains(existing.getKey()) && sameTableName(existing.getName(), tableName))
            {
                throw new DatasetEditException(NLS.bind(Messages.Edit_tableExists, tableName));
            }
        }
    }

    /**
     * Returns the edits that rename the start tag and the end tag of each element to a table name.
     */
    private static List<TextEdit> tableNameEdits(final List<FlatXmlElement> elements, final String tableName)
    {
        final List<TextEdit> edits = new ArrayList<>();
        for (final FlatXmlElement element : elements)
        {
            edits.add(new ReplaceEdit(element.offset() + 1, element.name().length(), tableName));
            if (!element.selfClosing())
            {
                edits.add(new ReplaceEdit(element.endTagOffset() + 2, element.name().length(), tableName));
            }
        }
        return edits;
    }

    private boolean isReservedRootName(final String tableName)
    {
        return sameTableName("dataset", tableName);
    }

    private boolean sameTableName(final String oneName, final String otherName)
    {
        return tableKeyOf.apply(oneName).equals(tableKeyOf.apply(otherName));
    }
}
