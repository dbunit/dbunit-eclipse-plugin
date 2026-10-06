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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;

/**
 * The columns that were added in an editor session and are not yet in the text of the document, per table
 * key, in the order they were added. They exist only here, so a change to them is an undoable step of the
 * text document's undo history of its own. Such a step keeps the columns as they were before it and as they
 * are after it, and undo and redo set those outright: the columns held now may still belong to a text state
 * that an undo of the text has left since, because nothing refreshed after that undo. A snapshot for each
 * modification stamp brings the columns back when undo or redo returns the text to an earlier state; the
 * owner keeps as many snapshots as the undo history can still lead back to.
 * Whenever such a change is undone or redone, the owner is asked to refresh through the callback that it
 * gave. The columns that a table keeps after all its rows were deleted are recorded here too; they have no
 * step of their own, because the delete of the rows is the step, and the snapshots bring them back with it.
 */
final class PendingColumns
{
    private static final int MINIMUM_SNAPSHOT_COUNT = 200;

    private static final int SNAPSHOTS_PER_UNDO_STEP = 4;

    private final Map<String, List<String>> pendingColumns = new LinkedHashMap<>();

    private final Map<Long, Map<String, List<String>>> pendingColumnsHistory = new LinkedHashMap<>();

    private final Runnable refreshAfterChange;

    private long lastRefreshModificationStamp = IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;

    /**
     * Creates the pending columns of a dataset document.
     *
     * @param refreshAfterChange Marks the dataset document's model stale and refreshes it, which runs after
     *                           each change of the pending columns, undone and redone ones included.
     */
    PendingColumns(final Runnable refreshAfterChange)
    {
        this.refreshAfterChange = refreshAfterChange;
    }

    /**
     * Returns the modification stamp of a text document, which undo and redo restore along with its text,
     * and which the snapshots of the pending columns are recorded under.
     *
     * @param document The text document.
     * @return The document's current modification stamp, or
     *         {@link IDocumentExtension4#UNKNOWN_MODIFICATION_STAMP} when it does not support one.
     */
    static long modificationStampOf(final IDocument document)
    {
        if (document instanceof final IDocumentExtension4 extension)
        {
            return extension.getModificationStamp();
        }
        return IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
    }

    /**
     * Returns how many snapshots of the pending columns are worth keeping for a text document. Undo and redo
     * can return the text only to a state that a step of the undo history leads to, so a snapshot is needed
     * for each of those steps, and a few more, because a refresh may come between two steps. The editor lets
     * the user choose how many steps the history keeps, and a number of snapshots that does not follow it
     * loses the pending columns of the oldest steps.
     *
     * @param document The text document.
     * @return Four snapshots for each step that the document's undo history keeps, and at least 200; 200
     *         when the document has no undo manager.
     */
    static int snapshotsToKeep(final IDocument document)
    {
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        if (undoManager == null)
        {
            return MINIMUM_SNAPSHOT_COUNT;
        }
        final IOperationHistory history = OperationHistoryFactory.getOperationHistory();
        final int undoSteps = history.getLimit(undoManager.getUndoContext());
        final long snapshotCount = (long) SNAPSHOTS_PER_UNDO_STEP * undoSteps;
        return (int) Math.max(MINIMUM_SNAPSHOT_COUNT, Math.min(Integer.MAX_VALUE, snapshotCount));
    }

    /**
     * Returns the pending columns, per table key, for the model builder to add to the columns of the tables.
     *
     * @return An unmodifiable view of the pending columns, which shows later changes.
     */
    Map<String, List<String>> asMap()
    {
        return Collections.unmodifiableMap(pendingColumns);
    }

    /**
     * Adds a pending column to a table, as a step of the document's undo history.
     *
     * @param document The text document whose undo history gets the step.
     * @param tableKey The key of the table.
     * @param columnName The name of the new column.
     */
    void addColumn(final IDocument document, final String tableKey, final String columnName)
    {
        applyPendingColumnsChange(document, "Add pending column",
                columns -> columns.computeIfAbsent(tableKey, unused -> new ArrayList<>()).add(columnName));
    }

    /**
     * Renames a pending column of a table, as a step of the document's undo history.
     *
     * @param document The text document whose undo history gets the step.
     * @param tableKey The key of the table.
     * @param columnName The current name of the column.
     * @param newColumnName The new name of the column.
     */
    void renameColumn(final IDocument document, final String tableKey, final String columnName,
            final String newColumnName)
    {
        applyPendingColumnsChange(document, "Rename pending column",
                columns -> renamePendingColumn(columns, tableKey, columnName, newColumnName));
    }

    /**
     * Deletes a pending column of a table, as a step of the document's undo history; undoing it puts the
     * column back at its position.
     *
     * @param document The text document whose undo history gets the step.
     * @param tableKey The key of the table.
     * @param columnName The name of the column.
     */
    void deleteColumn(final IDocument document, final String tableKey, final String columnName)
    {
        applyPendingColumnsChange(document, "Delete pending column",
                columns -> removePendingColumn(columns, tableKey, columnName));
    }

    /**
     * Records the columns that a new table starts with, which no text of the document has yet.
     *
     * @param tableKey The key of the new table.
     * @param columnNames The names of its columns; nothing is recorded when there are none.
     */
    void addTable(final String tableKey, final List<String> columnNames)
    {
        if (!columnNames.isEmpty())
        {
            pendingColumns.put(tableKey, new ArrayList<>(columnNames));
        }
    }

    /**
     * Moves the pending columns of a renamed table to its new key; nothing changes when it has none.
     *
     * @param tableKey The old key of the table.
     * @param newTableKey The new key of the table.
     */
    void renameTable(final String tableKey, final String newTableKey)
    {
        final List<String> pending = pendingColumns.remove(tableKey);
        if (pending != null)
        {
            pendingColumns.put(newTableKey, pending);
        }
    }

    /**
     * Drops the pending columns of a deleted table.
     *
     * @param tableKey The key of the table.
     */
    void deleteTable(final String tableKey)
    {
        pendingColumns.remove(tableKey);
    }

    /**
     * Records the data columns of a table as pending columns, ahead of the pending columns that it has.
     * A data column is one that only the rows give the table, because the DTD does not declare it, and a
     * flat XML file stores it only as attributes of rows, so the table loses it when all its rows are
     * deleted unless the editor session keeps it. The columns that the DTD declares need no record, and the
     * pending ones are recorded already.
     *
     * @param table The table as it was before its rows were deleted; nothing is recorded when it has no data
     *              column.
     */
    void keepDataColumns(final DatasetTable table)
    {
        final List<String> dataColumns = new ArrayList<>();
        for (final DatasetColumn column : table.getColumns())
        {
            if (!column.declared() && !column.pending())
            {
                dataColumns.add(column.name());
            }
        }
        if (dataColumns.isEmpty())
        {
            return;
        }
        final List<String> alreadyPending = pendingColumns.getOrDefault(table.getKey(), List.of());
        dataColumns.addAll(alreadyPending);
        pendingColumns.put(table.getKey(), dataColumns);
    }

    /**
     * Moves the pending columns of each table to the key that the table has under new options, such as
     * other case sensitivity of table names, and forgets the snapshots, whose keys are the old ones. Two
     * tables whose keys become one keep the columns of both, in order and without repeats. An entry whose
     * table is not among the keys keeps its key, because that table may come back.
     *
     * @param newKeysByOldKey The new key of each table of the model, by the key that it has had.
     */
    void rekey(final Map<String, String> newKeysByOldKey)
    {
        final Map<String, List<String>> rekeyed = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : pendingColumns.entrySet())
        {
            final String newKey = newKeysByOldKey.getOrDefault(entry.getKey(), entry.getKey());
            final List<String> columns = rekeyed.computeIfAbsent(newKey, unused -> new ArrayList<>());
            for (final String column : entry.getValue())
            {
                if (!columns.contains(column))
                {
                    columns.add(column);
                }
            }
        }
        pendingColumns.clear();
        pendingColumns.putAll(rekeyed);
        forgetHistory();
    }

    /**
     * Forgets the snapshots of earlier document states, because the dataset document is bound to another
     * text document, whose modification stamps have nothing to do with them.
     */
    void forgetHistory()
    {
        pendingColumnsHistory.clear();
        lastRefreshModificationStamp = IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
    }

    /**
     * Restores pendingColumns from the snapshot recorded for modificationStamp, when that stamp differs
     * from the stamp that the columns were last refreshed or set for, and a snapshot was recorded for it.
     * This is how the pending columns that belonged to an earlier document state come back once undo or
     * redo, which restores the document's modification stamp along with its text, returns the document to
     * that earlier state. The columns then belong to that stamp.
     */
    void restore(final long modificationStamp)
    {
        if (modificationStamp == lastRefreshModificationStamp)
        {
            return;
        }
        final Map<String, List<String>> snapshot = pendingColumnsHistory.get(modificationStamp);
        if (snapshot == null)
        {
            return;
        }
        replaceColumns(snapshot);
        lastRefreshModificationStamp = modificationStamp;
    }

    /**
     * Records a deep copy of pendingColumns under modificationStamp, so that a later undo or redo back to
     * this exact modification stamp can restore it. The snapshots pile up until {@link #keepOnly} discards
     * the oldest.
     */
    void record(final long modificationStamp)
    {
        pendingColumnsHistory.put(modificationStamp, copyOf(pendingColumns));
        lastRefreshModificationStamp = modificationStamp;
    }

    /**
     * Discards the oldest snapshots, the ones that were recorded first, until at most a number are left.
     *
     * @param snapshotCount How many snapshots to keep, as {@link #snapshotsToKeep} says.
     */
    void keepOnly(final int snapshotCount)
    {
        final Iterator<Long> oldest = pendingColumnsHistory.keySet().iterator();
        while (pendingColumnsHistory.size() > snapshotCount && oldest.hasNext())
        {
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * Drops each table's pending columns that a fresh build now finds backed by data or the DTD, and a
     * table's whole entry once none is left, so a pending column never lingers once it is no longer
     * needed. A table that the build lacks keeps its entry.
     */
    void pruneBackedColumns(final List<DatasetTable> tables)
    {
        for (final DatasetTable table : tables)
        {
            final List<String> pending = pendingColumns.get(table.getKey());
            if (pending == null)
            {
                continue;
            }
            pending.removeIf(name -> isBackedByRealColumn(table, name));
            if (pending.isEmpty())
            {
                pendingColumns.remove(table.getKey());
            }
        }
    }

    /**
     * Drops the entry of each table that a fresh build lacks. Call it only for a build that lists every
     * table of the document: one from text that is not well-formed, or without the external DTD that
     * declares some tables, lacks tables that exist, and their pending columns would be lost for good.
     */
    void pruneMissingTables(final List<DatasetTable> tables)
    {
        final Set<String> tableKeys = new HashSet<>();
        for (final DatasetTable table : tables)
        {
            tableKeys.add(table.getKey());
        }
        pendingColumns.keySet().removeIf(key -> !tableKeys.contains(key));
    }

    /**
     * Runs a pendingColumns change that makes no text edit of its own: when a document undo manager is
     * connected, through a custom operation on its undo context, so the change still becomes its own step
     * in the document's undo history instead of being invisible to undo; otherwise directly.
     */
    private void applyPendingColumnsChange(final IDocument document, final String label,
            final Consumer<Map<String, List<String>>> change)
    {
        final Map<String, List<String>> before = copyOf(pendingColumns);
        final Map<String, List<String>> after = copyOf(pendingColumns);
        change.accept(after);
        final PendingColumnsOperation operation =
                new PendingColumnsOperation(label, document, before, after);
        final IDocumentUndoManager undoManager =
                DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        try
        {
            if (undoManager == null)
            {
                operation.execute(null, null);
            }
            else
            {
                operation.addContext(undoManager.getUndoContext());
                final IOperationHistory history = OperationHistoryFactory.getOperationHistory();
                history.execute(operation, null, null);
            }
        }
        catch (final ExecutionException e)
        {
            throw new IllegalStateException("A pending column change failed.", e);
        }
    }

    private static void removePendingColumn(final Map<String, List<String>> columns, final String tableKey,
            final String columnName)
    {
        final List<String> pending = columns.get(tableKey);
        if (pending == null)
        {
            return;
        }
        final String columnKey = columnName.toUpperCase(Locale.ENGLISH);
        pending.removeIf(name -> name.toUpperCase(Locale.ENGLISH).equals(columnKey));
        if (pending.isEmpty())
        {
            columns.remove(tableKey);
        }
    }

    private static void renamePendingColumn(final Map<String, List<String>> columns, final String tableKey,
            final String oldName, final String newName)
    {
        final List<String> pending = columns.get(tableKey);
        if (pending == null)
        {
            return;
        }
        final int index = indexOfColumn(pending, oldName);
        if (index >= 0)
        {
            pending.set(index, newName);
        }
    }

    private void replaceColumns(final Map<String, List<String>> columns)
    {
        pendingColumns.clear();
        pendingColumns.putAll(copyOf(columns));
    }

    private static Map<String, List<String>> copyOf(final Map<String, List<String>> columns)
    {
        final Map<String, List<String>> copy = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : columns.entrySet())
        {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private static int indexOfColumn(final List<String> names, final String name)
    {
        final String columnKey = name.toUpperCase(Locale.ENGLISH);
        for (int i = 0; i < names.size(); i++)
        {
            if (names.get(i).toUpperCase(Locale.ENGLISH).equals(columnKey))
            {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBackedByRealColumn(final DatasetTable table, final String name)
    {
        final int columnIndex = table.getColumnIndex(name);
        return columnIndex >= 0 && !table.getColumns().get(columnIndex).pending();
    }

    /**
     * An undoable operation for a pendingColumns change that has no text edit of its own to carry it: it
     * shares the connected document undo manager's own undo context, so undo and redo interleave with text
     * edits in the same chronological order the user made them. It holds the columns from before and after
     * the change and sets them outright, as the columns of the document's current modification stamp, so
     * the refresh that follows does not install a snapshot that an earlier refresh left at that stamp.
     */
    private final class PendingColumnsOperation extends AbstractOperation
    {
        private final IDocument document;

        private final Map<String, List<String>> before;

        private final Map<String, List<String>> after;

        private PendingColumnsOperation(final String label, final IDocument document,
                final Map<String, List<String>> before, final Map<String, List<String>> after)
        {
            super(label);
            this.document = document;
            this.before = before;
            this.after = after;
        }

        @Override
        public IStatus execute(final IProgressMonitor monitor, final IAdaptable info)
        {
            return setAndRefresh(after);
        }

        @Override
        public IStatus redo(final IProgressMonitor monitor, final IAdaptable info)
        {
            return setAndRefresh(after);
        }

        @Override
        public IStatus undo(final IProgressMonitor monitor, final IAdaptable info)
        {
            return setAndRefresh(before);
        }

        private IStatus setAndRefresh(final Map<String, List<String>> columns)
        {
            replaceColumns(columns);
            lastRefreshModificationStamp = modificationStampOf(document);
            refreshAfterChange.run();
            return Status.OK_STATUS;
        }
    }
}
