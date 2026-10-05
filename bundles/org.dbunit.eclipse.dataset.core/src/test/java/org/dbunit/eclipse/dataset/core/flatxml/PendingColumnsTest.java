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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PendingColumns}: the changes of the pending columns with and without an undo manager, the
 * operations for tables, the pruning after a parse, and the snapshots per modification stamp.
 */
class PendingColumnsTest
{
    @FunctionalInterface
    private interface UndoManagerConsumer
    {
        void accept(IDocumentUndoManager undoManager) throws Exception;
    }

    private static void withUndoManager(final IDocument document, final UndoManagerConsumer consumer)
            throws Exception
    {
        DocumentUndoManagerRegistry.connect(document);
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(PendingColumnsTest.class);
        try
        {
            consumer.accept(undoManager);
        }
        finally
        {
            undoManager.disconnect(PendingColumnsTest.class);
            DocumentUndoManagerRegistry.disconnect(document);
        }
    }

    private static DatasetTable table(final String key, final DatasetColumn... columns)
    {
        return new DatasetTable(key, key, List.of(columns), List.of(), false);
    }

    private static DatasetColumn column(final String name, final boolean pending)
    {
        return new DatasetColumn(name, false, true, pending);
    }

    @Test
    void testAsMap_whenNothingWasAdded_isEmpty()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });

        assertThat(pending.asMap()).as("A new instance has no pending columns.").isEmpty();
    }

    @Test
    void testAsMap_whenAChangeFollowsTheCall_showsTheChange()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        final Map<String, List<String>> view = pending.asMap();

        pending.addTable("USERS", List.of("EXTRA"));

        assertThat(view).as("The view must show the pending columns as they are now.")
                .containsExactly(Map.entry("USERS", List.of("EXTRA")));
    }

    @Test
    void testAsMap_whenTheViewIsModified_throwsUnsupportedOperationException()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        final Map<String, List<String>> view = pending.asMap();

        assertThatThrownBy(() -> view.put("USERS", List.of("EXTRA")))
                .as("Only the pending columns may change their content.")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testAddColumn_whenNoUndoManagerIsConnected_addsTheColumnAndRefreshesOnce()
    {
        final AtomicInteger refreshes = new AtomicInteger();
        final PendingColumns pending = new PendingColumns(refreshes::incrementAndGet);

        pending.addColumn(new Document(), "USERS", "EXTRA");

        assertThat(pending.asMap()).as("The column must be pending.")
                .containsExactly(Map.entry("USERS", List.of("EXTRA")));
        assertThat(refreshes.get()).as("The owner must be asked to refresh once.").isEqualTo(1);
    }

    @Test
    void testAddColumn_whenAnotherColumnIsAdded_keepsTheOrderOfAddition()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addColumn(new Document(), "USERS", "ZETA");

        pending.addColumn(new Document(), "USERS", "ALPHA");

        assertThat(pending.asMap()).as("Columns must stay in the order they were added.")
                .containsExactly(Map.entry("USERS", List.of("ZETA", "ALPHA")));
    }

    @Test
    void testAddColumn_whenUndoneAndRedone_removesAndAddsItAndRefreshesEachTime() throws Exception
    {
        final IDocument document = new Document("<dataset/>");
        withUndoManager(document, undoManager ->
        {
            final AtomicInteger refreshes = new AtomicInteger();
            final PendingColumns pending = new PendingColumns(refreshes::incrementAndGet);
            pending.addColumn(document, "USERS", "EXTRA");

            undoManager.undo();

            assertThat(pending.asMap()).as("Undo must remove the column, and with it the table's entry.")
                    .isEmpty();
            assertThat(refreshes.get()).as("The add and the undo must each refresh.").isEqualTo(2);
            undoManager.redo();
            assertThat(pending.asMap()).as("Redo must add the column again.")
                    .containsExactly(Map.entry("USERS", List.of("EXTRA")));
            assertThat(refreshes.get()).as("The redo must refresh too.").isEqualTo(3);
        });
    }

    @Test
    void testAddColumn_whenUndoneAfterTheColumnsChangedMeanwhile_setsTheColumnsFromBeforeTheAdd()
            throws Exception
    {
        final IDocument document = new Document("<dataset/>");
        withUndoManager(document, undoManager ->
        {
            final PendingColumns pending = new PendingColumns(() ->
            {
            });
            pending.addTable("USERS", List.of("A"));
            pending.addColumn(document, "USERS", "B");
            pending.deleteTable("USERS");

            undoManager.undo();

            assertThat(pending.asMap())
                    .as("Undo must set the columns that existed before the add, not take the column out "
                            + "of whatever is left.")
                    .containsExactly(Map.entry("USERS", List.of("A")));
        });
    }

    @Test
    void testAddColumn_whenRedoneAfterTheColumnsChangedMeanwhile_setsTheColumnsFromAfterTheAdd()
            throws Exception
    {
        final IDocument document = new Document("<dataset/>");
        withUndoManager(document, undoManager ->
        {
            final PendingColumns pending = new PendingColumns(() ->
            {
            });
            pending.addColumn(document, "USERS", "A");
            undoManager.undo();
            pending.addTable("USERS", List.of("A"));

            undoManager.redo();

            assertThat(pending.asMap())
                    .as("Redo must set the columns that existed after the add, not add the column a "
                            + "second time.")
                    .containsExactly(Map.entry("USERS", List.of("A")));
        });
    }

    @Test
    void testAddColumn_whenUndoneAfterTheTextWentBack_aRestoreOfTheCurrentStampChangesNothing()
            throws Exception
    {
        final IDocument document = new Document("<dataset/>");
        withUndoManager(document, undoManager ->
        {
            final PendingColumns pending = new PendingColumns(() ->
            {
            });
            pending.addColumn(document, "USERS", "EXTRA");
            pending.record(PendingColumns.modificationStampOf(document));
            document.replace(0, 0, " ");
            pending.record(PendingColumns.modificationStampOf(document));
            undoManager.undo();
            undoManager.undo();

            pending.restore(PendingColumns.modificationStampOf(document));

            assertThat(pending.asMap())
                    .as("The refresh that follows the undone add must not bring the column back from the "
                            + "snapshot that the add itself left at this stamp.")
                    .isEmpty();
        });
    }

    @Test
    void testRenameColumn_whenTheOldNameDiffersInCase_renamesTheColumn()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("Extra"));

        pending.renameColumn(new Document(), "USERS", "EXTRA", "Other");

        assertThat(pending.asMap()).as("The column must be found ignoring case.")
                .containsExactly(Map.entry("USERS", List.of("Other")));
    }

    @Test
    void testRenameColumn_whenUndone_restoresTheOldName() throws Exception
    {
        final IDocument document = new Document("<dataset/>");
        withUndoManager(document, undoManager ->
        {
            final PendingColumns pending = new PendingColumns(() ->
            {
            });
            pending.addTable("USERS", List.of("EXTRA"));
            pending.renameColumn(document, "USERS", "EXTRA", "OTHER");

            undoManager.undo();

            assertThat(pending.asMap()).as("Undo must bring the old name back.")
                    .containsExactly(Map.entry("USERS", List.of("EXTRA")));
        });
    }

    @Test
    void testRenameColumn_whenTheTableHasNoPendingColumns_changesNothingButRefreshes()
    {
        final AtomicInteger refreshes = new AtomicInteger();
        final PendingColumns pending = new PendingColumns(refreshes::incrementAndGet);

        pending.renameColumn(new Document(), "USERS", "EXTRA", "OTHER");

        assertThat(pending.asMap()).as("There is nothing to rename.").isEmpty();
        assertThat(refreshes.get()).as("The change still refreshes the owner.").isEqualTo(1);
    }

    @Test
    void testDeleteColumn_whenItWasTheLastOne_dropsTheTablesEntry()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("EXTRA"));

        pending.deleteColumn(new Document(), "USERS", "extra");

        assertThat(pending.asMap()).as("An empty list must not stay behind for the table.").isEmpty();
    }

    @Test
    void testDeleteColumn_whenUndone_putsTheColumnBackAtItsPosition() throws Exception
    {
        final IDocument document = new Document("<dataset/>");
        withUndoManager(document, undoManager ->
        {
            final PendingColumns pending = new PendingColumns(() ->
            {
            });
            pending.addTable("USERS", List.of("A", "B", "C"));
            pending.deleteColumn(document, "USERS", "B");
            assertThat(pending.asMap()).as("The column must be gone.")
                    .containsExactly(Map.entry("USERS", List.of("A", "C")));

            undoManager.undo();

            assertThat(pending.asMap()).as("Undo must restore the column at its old position.")
                    .containsExactly(Map.entry("USERS", List.of("A", "B", "C")));
        });
    }

    @Test
    void testDeleteColumn_whenUndoneAfterTheTableLostItsOtherColumns_putsBothColumnsBack() throws Exception
    {
        final IDocument document = new Document("<dataset/>");
        withUndoManager(document, undoManager ->
        {
            final PendingColumns pending = new PendingColumns(() ->
            {
            });
            pending.addTable("USERS", List.of("A", "B"));
            pending.deleteColumn(document, "USERS", "B");
            pending.deleteTable("USERS");

            undoManager.undo();

            assertThat(pending.asMap())
                    .as("Undo must set the columns that existed before the delete, though its saved "
                            + "position is past the end of the columns left.")
                    .containsExactly(Map.entry("USERS", List.of("A", "B")));
        });
    }

    @Test
    void testDeleteColumn_whenTheColumnWasNotPending_undoChangesNothing() throws Exception
    {
        final IDocument document = new Document("<dataset/>");
        withUndoManager(document, undoManager ->
        {
            final PendingColumns pending = new PendingColumns(() ->
            {
            });
            pending.addTable("USERS", List.of("A"));
            pending.deleteColumn(document, "USERS", "MISSING");

            undoManager.undo();

            assertThat(pending.asMap()).as("A column that was not pending must not appear on undo.")
                    .containsExactly(Map.entry("USERS", List.of("A")));
        });
    }

    @Test
    void testAddTable_whenColumnsAreGiven_recordsACopyOfThem()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        final List<String> given = new ArrayList<>(List.of("A", "B"));

        pending.addTable("USERS", given);
        given.add("C");

        assertThat(pending.asMap()).as("Later changes of the caller's list must not reach the record.")
                .containsExactly(Map.entry("USERS", List.of("A", "B")));
    }

    @Test
    void testAddTable_whenNoColumnsAreGiven_recordsNothing()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });

        pending.addTable("USERS", List.of());

        assertThat(pending.asMap()).as("A table without columns has nothing pending.").isEmpty();
    }

    @Test
    void testRenameTable_whenTheTableHasPendingColumns_movesThemToTheNewKey()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("A"));

        pending.renameTable("USERS", "PEOPLE");

        assertThat(pending.asMap()).as("The columns must follow the table to its new key.")
                .containsExactly(Map.entry("PEOPLE", List.of("A")));
    }

    @Test
    void testRenameTable_whenTheTableHasNoPendingColumns_changesNothing()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("ORDERS", List.of("A"));

        pending.renameTable("USERS", "PEOPLE");

        assertThat(pending.asMap()).as("Other tables must be left alone.")
                .containsExactly(Map.entry("ORDERS", List.of("A")));
    }

    @Test
    void testDeleteTable_whenTheTableHasPendingColumns_dropsThem()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("A"));
        pending.addTable("ORDERS", List.of("B"));

        pending.deleteTable("USERS");

        assertThat(pending.asMap()).as("Only the deleted table's columns must go.")
                .containsExactly(Map.entry("ORDERS", List.of("B")));
    }

    @Test
    void testKeepDataColumns_whenTheTableHasDataColumns_recordsThemInColumnOrder()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        final DatasetTable users = table("USERS", column("ID", false), column("NAME", false));

        pending.keepDataColumns(users);

        assertThat(pending.asMap())
                .as("Every column that only the rows give the table must be kept, in column order.")
                .containsExactly(Map.entry("USERS", List.of("ID", "NAME")));
    }

    @Test
    void testKeepDataColumns_whenTheTableHasPendingColumns_recordsTheDataColumnsAheadOfThem()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("LATER"));
        final DatasetTable users = table("USERS", column("ID", false), column("LATER", true));

        pending.keepDataColumns(users);

        assertThat(pending.asMap())
                .as("The columns must keep the order that the table showed: data columns, then pending ones.")
                .containsExactly(Map.entry("USERS", List.of("ID", "LATER")));
    }

    @Test
    void testKeepDataColumns_whenAColumnIsDeclaredInTheDtd_doesNotRecordIt()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        final DatasetTable users =
                table("USERS", new DatasetColumn("ID", true, true, false), column("EXTRA", false));

        pending.keepDataColumns(users);

        assertThat(pending.asMap()).as("The DTD keeps a column that it declares, so it is not pending.")
                .containsExactly(Map.entry("USERS", List.of("EXTRA")));
    }

    @Test
    void testKeepDataColumns_whenOnlyDeclaredAndPendingColumnsExist_changesNothing()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("LATER"));
        final DatasetTable users =
                table("USERS", new DatasetColumn("ID", true, false, false), column("LATER", true));

        pending.keepDataColumns(users);

        assertThat(pending.asMap()).as("There is no data column to keep, so the entry must stay as it is.")
                .containsExactly(Map.entry("USERS", List.of("LATER")));
    }

    @Test
    void testKeepDataColumns_whenNoColumnNeedsKeeping_recordsNoEntryForTheTable()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });

        pending.keepDataColumns(table("USERS", new DatasetColumn("ID", true, false, false)));

        assertThat(pending.asMap()).as("A table without data columns must not get an empty entry.").isEmpty();
    }

    @Test
    void testPruneBackedColumns_whenAColumnIsNowBackedByARealColumn_dropsIt()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("EXTRA", "LATER"));
        final DatasetTable users = table("USERS", column("ID", false), column("extra", false),
                column("LATER", true));

        pending.pruneBackedColumns(List.of(users));

        assertThat(pending.asMap()).as("Only a column that a real column backs must be dropped.")
                .containsExactly(Map.entry("USERS", List.of("LATER")));
    }

    @Test
    void testPruneBackedColumns_whenEveryPendingColumnIsBacked_dropsTheTablesEntry()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("EXTRA"));

        pending.pruneBackedColumns(List.of(table("USERS", column("EXTRA", false))));

        assertThat(pending.asMap()).as("A table without pending columns must have no entry.").isEmpty();
    }

    @Test
    void testPruneBackedColumns_whenTheTableIsNotListed_keepsItsEntry()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("EXTRA"));
        pending.addTable("ORDERS", List.of("TOTAL"));

        pending.pruneBackedColumns(List.of(table("ORDERS", column("TOTAL", true))));

        assertThat(pending.asMap()).as("A table that the build lacks may still exist, so it keeps its entry.")
                .containsExactly(Map.entry("USERS", List.of("EXTRA")), Map.entry("ORDERS", List.of("TOTAL")));
    }

    @Test
    void testPruneMissingTables_whenTheTableIsNotListed_dropsItsEntry()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("EXTRA"));
        pending.addTable("ORDERS", List.of("TOTAL"));

        pending.pruneMissingTables(List.of(table("ORDERS", column("TOTAL", true))));

        assertThat(pending.asMap()).as("The entry of a table that is gone must not linger.")
                .containsExactly(Map.entry("ORDERS", List.of("TOTAL")));
    }

    @Test
    void testPruneMissingTables_whenAListedTableHasABackedColumn_leavesItsColumnsAlone()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("EXTRA"));

        pending.pruneMissingTables(List.of(table("USERS", column("EXTRA", false))));

        assertThat(pending.asMap()).as("Dropping missing tables must not look at the columns of listed ones.")
                .containsExactly(Map.entry("USERS", List.of("EXTRA")));
    }

    @Test
    void testRestore_whenAnEarlierStampIsGiven_bringsBackTheColumnsOfThatState()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.record(1L);
        pending.addTable("USERS", List.of("EXTRA"));
        pending.record(2L);

        pending.restore(1L);

        assertThat(pending.asMap()).as("The state recorded for stamp 1 had no pending columns.").isEmpty();
        pending.record(1L);
        pending.restore(2L);
        assertThat(pending.asMap()).as("The state recorded for stamp 2 must come back.")
                .containsExactly(Map.entry("USERS", List.of("EXTRA")));
    }

    @Test
    void testRestore_whenCalledTwiceForTheSameStamp_changesNothingTheSecondTime()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.record(1L);
        pending.addTable("USERS", List.of("EXTRA"));
        pending.record(2L);
        pending.restore(1L);
        pending.addTable("ORDERS", List.of("TOTAL"));

        pending.restore(1L);

        assertThat(pending.asMap())
                .as("The columns belong to stamp 1 once it was restored, so a second restore of it must "
                        + "leave them alone.")
                .containsExactly(Map.entry("ORDERS", List.of("TOTAL")));
    }

    @Test
    void testRestore_whenTheStampIsTheLastRecordedOne_changesNothing()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.record(5L);
        pending.addTable("USERS", List.of("EXTRA"));

        pending.restore(5L);

        assertThat(pending.asMap()).as("The text has not moved to another state, so nothing comes back.")
                .containsExactly(Map.entry("USERS", List.of("EXTRA")));
    }

    @Test
    void testRestore_whenNothingWasRecordedForTheStamp_changesNothing()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.record(1L);
        pending.addTable("USERS", List.of("EXTRA"));

        pending.restore(99L);

        assertThat(pending.asMap()).as("A stamp without a snapshot must leave the columns alone.")
                .containsExactly(Map.entry("USERS", List.of("EXTRA")));
    }

    @Test
    void testRecord_whenTheColumnsChangeAfterwards_keepsTheStateAsItWas()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("A"));
        pending.record(1L);
        pending.deleteTable("USERS");
        pending.addTable("USERS", List.of("B"));
        pending.record(2L);

        pending.restore(1L);

        assertThat(pending.asMap()).as("A snapshot must hold a copy, not the live lists.")
                .containsExactly(Map.entry("USERS", List.of("A")));
    }

    @Test
    void testRecord_whenMoreThanTwoHundredSnapshotsAreRecorded_discardsTheOldest()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        for (long stamp = 1; stamp <= 201; stamp++)
        {
            pending.deleteTable("USERS");
            pending.addTable("USERS", List.of("C" + stamp));
            pending.record(stamp);
        }
        pending.deleteTable("USERS");

        pending.restore(1L);
        assertThat(pending.asMap()).as("The oldest of 201 snapshots must be gone.").isEmpty();
        pending.restore(2L);

        assertThat(pending.asMap()).as("The second oldest must still be there.")
                .containsExactly(Map.entry("USERS", List.of("C2")));
    }

    @Test
    void testForgetHistory_whenSnapshotsWereRecorded_forgetsThem()
    {
        final PendingColumns pending = new PendingColumns(() ->
        {
        });
        pending.addTable("USERS", List.of("A"));
        pending.record(1L);
        pending.record(2L);
        pending.deleteTable("USERS");

        pending.forgetHistory();
        pending.restore(1L);

        assertThat(pending.asMap()).as("No snapshot may survive being bound to another document.").isEmpty();
    }
}
