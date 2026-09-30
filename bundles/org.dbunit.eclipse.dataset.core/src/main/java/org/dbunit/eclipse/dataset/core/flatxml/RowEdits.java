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
import java.util.Arrays;
import java.util.List;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.text.IRegion;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * Plans the text edits that duplicate, delete, and move rows that a table already has: it validates the
 * rows or the block of rows against the table, and works out the edits from the elements of the rows, so that
 * a copy keeps its row's indentation, a deleted row leaves no blank line, and a moved block swaps its text
 * with its neighbor. It plans against the index, the layout, and the text of the {@link EditContext} that it
 * is created with, which is meant for one operation.
 */
final class RowEdits
{
    private final EditContext context;

    private final FlatXmlIndex index;

    private final FlatXmlTextLayout layout;

    RowEdits(final EditContext context)
    {
        this.context = context;
        this.index = context.index();
        this.layout = context.layout();
    }

    /**
     * Plans the copies of rows: one insert of a copy of each row, in row order, after the last of the rows.
     *
     * @param tableKey The key of the table.
     * @param table The table.
     * @param rowIndexes The indexes of the rows to copy, which need not be in order.
     * @return The edits; none when no row is given.
     * @throws org.dbunit.eclipse.dataset.core.edit.DatasetEditException When a row does not exist.
     */
    List<TextEdit> duplicateEdits(final String tableKey, final DatasetTable table, final int[] rowIndexes)
    {
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        final int[] sorted = sortedRowIndexes(table, rowElements, rowIndexes);
        final List<TextEdit> edits = new ArrayList<>();
        if (sorted.length > 0)
        {
            final FlatXmlElement lastSelected = rowElements.get(sorted[sorted.length - 1]);
            final String delimiter = layout.getLineDelimiter();
            final String indent = layout.indentOf(lastSelected);
            final String text = context.text();
            final StringBuilder insertText = new StringBuilder();
            for (final int rowIndex : sorted)
            {
                final FlatXmlElement element = rowElements.get(rowIndex);
                insertText.append(delimiter).append(indent).append(text, element.offset(),
                        element.endOffset());
            }
            edits.add(new InsertEdit(lastSelected.endOffset(), insertText.toString()));
        }
        return edits;
    }

    /**
     * Plans the deletion of rows: each row goes with its line when it is alone on it. When every row of a
     * table that has no marker element goes, the first is replaced by an empty element instead, so that the
     * table stays in the document.
     *
     * @param tableKey The key of the table.
     * @param table The table.
     * @param rowIndexes The indexes of the rows to delete, which need not be in order.
     * @return The edits; none when no row is given.
     * @throws org.dbunit.eclipse.dataset.core.edit.DatasetEditException When a row does not exist.
     */
    List<TextEdit> deleteEdits(final String tableKey, final DatasetTable table, final int[] rowIndexes)
    {
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        final int[] sorted = sortedRowIndexes(table, rowElements, rowIndexes);
        final boolean deletingAllRows = sorted.length == rowElements.size();
        final boolean hasMarker = !index.getMarkerElements(tableKey).isEmpty();
        final List<TextEdit> edits = new ArrayList<>();
        for (int position = 0; position < sorted.length; position++)
        {
            final FlatXmlElement element = rowElements.get(sorted[position]);
            if (position == 0 && deletingAllRows && !hasMarker)
            {
                edits.add(new ReplaceEdit(element.offset(), element.endOffset() - element.offset(),
                        "<" + table.getName() + "/>"));
            }
            else
            {
                final IRegion region = layout.lineExtent(element);
                edits.add(new DeleteEdit(region.getOffset(), region.getLength()));
            }
        }

        return edits;
    }

    /**
     * Plans moving a block of rows one position up or down.
     *
     * @param tableKey The key of the table.
     * @param table The table.
     * @param firstRowIndex The index of the first row of the block.
     * @param rowCount The number of rows in the block.
     * @param delta -1 to move the block up, 1 to move it down.
     * @return The edits.
     * @throws org.dbunit.eclipse.dataset.core.edit.DatasetEditException When the block is not in the table,
     *         the distance is not one position, or there is no room to move.
     */
    List<TextEdit> moveEdits(final String tableKey, final DatasetTable table, final int firstRowIndex,
            final int rowCount, final int delta)
    {
        final List<FlatXmlElement> rowElements = index.getRowElements(tableKey);
        requireRowBlock(table, rowElements.size(), firstRowIndex, rowCount);
        requireOneRowStep(delta);
        requireRoomToMove(table, rowElements.size(), firstRowIndex, rowCount, delta);

        return rowMoveEdits(rowElements, firstRowIndex, rowCount, delta);
    }

    /**
     * Returns a sorted copy of row indexes, which must all be rows of the table.
     */
    private static int[] sortedRowIndexes(final DatasetTable table, final List<FlatXmlElement> rowElements,
            final int[] rowIndexes)
    {
        final int[] sorted = rowIndexes.clone();
        Arrays.sort(sorted);
        for (final int rowIndex : sorted)
        {
            if (rowIndex < 0 || rowIndex >= rowElements.size())
            {
                throw new DatasetEditException(
                        NLS.bind(Messages.Edit_rowOutOfRange, rowIndex, table.getName()));
            }
        }
        return sorted;
    }

    private static void requireRowBlock(final DatasetTable table, final int totalRows,
            final int firstRowIndex, final int blockSize)
    {
        if (firstRowIndex < 0 || blockSize < 1 || firstRowIndex + blockSize > totalRows)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_rowBlockOutOfRange, table.getName()));
        }
    }

    private static void requireOneRowStep(final int delta)
    {
        if (delta != -1 && delta != 1)
        {
            throw new DatasetEditException(Messages.Edit_moveByOnePosition);
        }
    }

    private static void requireRoomToMove(final DatasetTable table, final int totalRows,
            final int firstRowIndex, final int blockSize, final int delta)
    {
        if (delta < 0 && firstRowIndex == 0)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_moveBeforeFirstRow, table.getName()));
        }
        if (delta > 0 && firstRowIndex + blockSize >= totalRows)
        {
            throw new DatasetEditException(
                    NLS.bind(Messages.Edit_moveAfterLastRow, table.getName()));
        }
    }

    /**
     * Returns the edits that move a block of rows one position up or down: one replace edit for each row of
     * the block and for the neighbouring row it passes, which rotates their texts by one position. The
     * arguments are already validated.
     */
    private List<TextEdit> rowMoveEdits(final List<FlatXmlElement> rowElements, final int firstRowIndex,
            final int rowCount, final int delta)
    {
        final int startPosition = delta < 0 ? firstRowIndex - 1 : firstRowIndex;
        final int positionCount = rowCount + 1;
        final String text = context.text();
        final List<FlatXmlElement> positions = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        for (int i = 0; i < positionCount; i++)
        {
            final FlatXmlElement element = rowElements.get(startPosition + i);
            positions.add(element);
            texts.add(text.substring(element.offset(), element.endOffset()));
        }

        final List<TextEdit> edits = new ArrayList<>();
        for (int i = 0; i < positionCount; i++)
        {
            final int sourceIndex =
                    delta > 0 ? Math.floorMod(i - 1, positionCount) : Math.floorMod(i + 1, positionCount);
            final FlatXmlElement position = positions.get(i);
            edits.add(new ReplaceEdit(position.offset(), position.endOffset() - position.offset(),
                    texts.get(sourceIndex)));
        }
        return edits;
    }
}
