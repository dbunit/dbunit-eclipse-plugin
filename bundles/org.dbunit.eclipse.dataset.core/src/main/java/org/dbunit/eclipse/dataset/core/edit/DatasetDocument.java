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
package org.dbunit.eclipse.dataset.core.edit;

import java.util.List;

import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;

/**
 * An editable dataset bound to its source. Not thread-safe: use it from one thread (the UI thread in the
 * editor). Every edit operation first refreshes a stale model, then validates its arguments, then applies
 * all of its changes as one undoable change, then refreshes the model and notifies listeners with
 * {@link ChangeOrigin#EDIT}. Invalid arguments or rule violations throw {@link DatasetEditException} and
 * change nothing.
 *
 * @since 1.0.0
 */
public interface DatasetDocument
{
    /**
     * Returns the model of the last refresh. Never reparses.
     *
     * @return The current model.
     */
    DatasetModel getModel();

    /**
     * Returns whether the source changed since the last refresh.
     *
     * @return True when the source is stale.
     */
    boolean isStale();

    /**
     * Rebuilds the model when stale and notifies listeners with {@link ChangeOrigin#DOCUMENT}. Does
     * nothing when the model is not stale.
     */
    void refresh();

    /**
     * Registers a listener notified after every model change.
     *
     * @param listener The listener to add.
     */
    void addModelListener(DatasetModelListener listener);

    /**
     * Unregisters a previously added listener.
     *
     * @param listener The listener to remove.
     */
    void removeModelListener(DatasetModelListener listener);

    /**
     * Runs several operations as one undoable change. Batches may nest; only the outermost batch begins
     * and ends the undoable change.
     *
     * @param operations The operations to run.
     */
    void batch(Runnable operations);

    /**
     * Changes one or more cells of a table.
     *
     * @param tableKey The key of the table.
     * @param changes The changes to apply.
     */
    void setCells(String tableKey, List<CellChange> changes);

    /**
     * Inserts rows into a table.
     *
     * @param tableKey The key of the table.
     * @param rowIndex The index to insert before; equal to the row count to append.
     * @param rows The values of the new rows, aligned with the table's columns.
     */
    void insertRows(String tableKey, int rowIndex, List<List<String>> rows);

    /**
     * Changes cells of existing rows and appends rows after a table's last row, as pasting a block that
     * reaches past the last row does. Unlike a batch of {@link #setCells} and {@link #insertRows}, it
     * rebuilds the model only once.
     *
     * @param tableKey The key of the table.
     * @param changes The changes to existing rows' cells; may be empty.
     * @param rows The values of the rows to append, aligned with the table's columns; may be empty.
     */
    void setCellsAndAppendRows(String tableKey, List<CellChange> changes, List<List<String>> rows);

    /**
     * Inserts copies of rows directly after the last of them, in their order.
     *
     * @param tableKey The key of the table.
     * @param rowIndexes The indexes of the rows to duplicate.
     */
    void duplicateRows(String tableKey, int[] rowIndexes);

    /**
     * Deletes rows from a table.
     *
     * @param tableKey The key of the table.
     * @param rowIndexes The indexes of the rows to delete.
     */
    void deleteRows(String tableKey, int[] rowIndexes);

    /**
     * Moves a contiguous block of rows up or down.
     *
     * @param tableKey The key of the table.
     * @param firstRowIndex The index of the first row of the block.
     * @param rowCount The number of rows in the block.
     * @param delta The number of positions to move: -1 moves the block up, +1 moves it down.
     */
    void moveRows(String tableKey, int firstRowIndex, int rowCount, int delta);

    /**
     * Appends a pending column to a table; it has no values yet.
     *
     * @param tableKey The key of the table.
     * @param columnName The name of the new column.
     */
    void addColumn(String tableKey, String columnName);

    /**
     * Renames a column of a table.
     *
     * @param tableKey The key of the table.
     * @param columnName The current name of the column.
     * @param newColumnName The new name of the column.
     */
    void renameColumn(String tableKey, String columnName, String newColumnName);

    /**
     * Deletes a column from a table.
     *
     * @param tableKey The key of the table.
     * @param columnName The name of the column to delete.
     */
    void deleteColumn(String tableKey, String columnName);

    /**
     * Returns the key a table with this name would have: the identity {@link DatasetTable#getKey()}
     * carries across refreshes, accounting for this document's table-name case sensitivity. Callers use
     * this to predict a table's key before an edit that creates or renames it completes.
     *
     * @param tableName The name to compute the key for.
     * @return The key.
     */
    String tableKeyOf(String tableName);

    /**
     * Appends an empty table.
     *
     * @param tableName The name of the new table.
     * @param columnNames The names of pending columns to add to the new table.
     */
    void addTable(String tableName, List<String> columnNames);

    /**
     * Renames a table.
     *
     * @param tableKey The key of the table.
     * @param newTableName The new name of the table.
     */
    void renameTable(String tableKey, String newTableName);

    /**
     * Deletes a table.
     *
     * @param tableKey The key of the table to delete.
     */
    void deleteTable(String tableKey);

    /**
     * Releases the resources held by this document.
     */
    void dispose();
}
