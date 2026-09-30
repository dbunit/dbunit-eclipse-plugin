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
import java.util.List;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * Plans the text edit that inserts new rows into a table: it validates the position and the values against
 * the table, writes each row as a self-closing element with the table's columns in order, and finds the
 * place for them so that they keep the indentation and the line delimiters of the document. It plans
 * against the index and the layout of the {@link EditContext} that it is created with, which is meant for
 * one operation.
 */
final class RowInsertEdits
{
    private final EditContext context;

    private final FlatXmlIndex index;

    private final FlatXmlTextLayout layout;

    RowInsertEdits(final EditContext context)
    {
        this.context = context;
        this.index = context.index();
        this.layout = context.layout();
    }

    /**
     * Validates new rows and returns the edit that inserts them before the row at rowIndex, or after the
     * last row when rowIndex is the row count, computed against the current index; no edit when there are
     * no rows.
     */
    List<TextEdit> plan(final String tableKey, final DatasetTable table, final int rowIndex,
            final List<List<String>> rows)
    {
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        requireInsertPosition(table, rowElements.size(), rowIndex);
        for (final List<String> values : rows)
        {
            requireInsertableRow(table, values);
        }
        if (rows.isEmpty())
        {
            return List.of();
        }

        final CharsetEncoder encoder = context.encoder();
        final List<String> rowTexts = new ArrayList<>();
        for (final List<String> values : rows)
        {
            rowTexts.add(buildRowText(table, values, encoder));
        }

        final TextEdit edit = insertEdit(tableKey, rowElements, rowIndex, rowTexts);
        return List.of(edit);
    }

    private static void requireInsertPosition(final DatasetTable table, final int rowCount,
            final int rowIndex)
    {
        if (table.getColumns().isEmpty())
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_tableHasNoColumns, table.getName()));
        }
        if (rowIndex < 0 || rowIndex > rowCount)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_rowOutOfRange, rowIndex, table.getName()));
        }
    }

    private static void requireInsertableRow(final DatasetTable table, final List<String> values)
    {
        if (values.size() != table.getColumns().size())
        {
            throw new DatasetEditException(NLS.bind(Messages.Edit_wrongValueCount,
                    table.getColumns().size(), table.getName()));
        }
        if (isAllNull(values))
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_newRowWouldBeEmpty, table.getName()));
        }
    }

    /**
     * Returns the edit that inserts the texts of new rows before the row at rowIndex, after the last row
     * when rowIndex is the row count, or into the table's empty element or the root when it has no rows.
     */
    private TextEdit insertEdit(final String tableKey, final List<FlatXmlElement> rowElements,
            final int rowIndex, final List<String> rowTexts)
    {
        final String delimiter = layout.getLineDelimiter();
        final TextEdit edit;
        if (rowIndex < rowElements.size())
        {
            final FlatXmlElement anchor = rowElements.get(rowIndex);
            final String indent = layout.indentOf(anchor);
            final StringBuilder insertText = new StringBuilder();
            for (final String rowText : rowTexts)
            {
                insertText.append(rowText).append(delimiter).append(indent);
            }
            edit = new InsertEdit(anchor.offset(), insertText.toString());
        }
        else if (!rowElements.isEmpty())
        {
            final FlatXmlElement last = rowElements.get(rowElements.size() - 1);
            final String indent = layout.indentOf(last);
            final StringBuilder insertText = new StringBuilder();
            for (final String rowText : rowTexts)
            {
                insertText.append(delimiter).append(indent).append(rowText);
            }
            edit = new InsertEdit(last.endOffset(), insertText.toString());
        }
        else if (!index.getMarkerElements(tableKey).isEmpty())
        {
            final FlatXmlElement marker = index.getMarkerElements(tableKey).get(0);
            final String indent = layout.indentOf(marker);
            final String joined = String.join(delimiter + indent, rowTexts);
            edit = new ReplaceEdit(marker.offset(), marker.endOffset() - marker.offset(), joined);
        }
        else
        {
            final String indent = layout.childIndentation(index.getRoot(), index.getElements());
            final String joined = String.join(delimiter + indent, rowTexts);
            edit = layout.insertAsLastChildOfRoot(index.getRoot(), index.getElements(), joined);
        }
        return edit;
    }

    /**
     * Returns the text of a new row element: the table's display name, one attribute per non-null value
     * in table column order, and a self-closing end.
     */
    private static String buildRowText(final DatasetTable table, final List<String> values,
            final CharsetEncoder encoder)
    {
        final StringBuilder text = new StringBuilder();
        text.append('<').append(table.getName());
        for (int columnIndex = 0; columnIndex < table.getColumns().size(); columnIndex++)
        {
            final String value = values.get(columnIndex);
            if (value != null)
            {
                text.append(' ').append(table.getColumns().get(columnIndex).name()).append("=\"")
                        .append(AttributeValueCodec.escape(value, encoder)).append('"');
            }
        }
        text.append("/>");
        return text.toString();
    }

    /**
     * Returns whether every value is null; a new row with no values would have an element without
     * attributes, which is not a valid row.
     */
    private static boolean isAllNull(final List<String> values)
    {
        for (final String value : values)
        {
            if (value != null)
            {
                return false;
            }
        }
        return true;
    }
}
