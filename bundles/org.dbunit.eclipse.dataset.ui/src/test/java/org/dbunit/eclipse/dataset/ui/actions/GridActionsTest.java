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
package org.dbunit.eclipse.dataset.ui.actions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.dialogs.AddTableDialog;
import org.dbunit.eclipse.dataset.ui.dialogs.DatasetNameValidator;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link InsertRowAboveAction}, {@link InsertRowBelowAction}, and {@link DeleteRowsAction} against
 * the Commands specification's insert index, selection, enablement, and active-cell-editor rules.
 */
class GridActionsTest
{
    @Test
    void testInsertRowBelow_withAnchorInTheSecondRow_insertsAtIndex2AndSelectsTheNewRow()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 1;
        final InsertRowBelowAction action = new InsertRowBelowAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                .as("Insert Row Below must add one row.").hasSize(4);
        assertThat(context.pendingSelectionRow).as("Insert Row Below must select the new row at index 2.")
                .isEqualTo(2);
        assertThat(context.pendingSelectionColumn).as("Insert Row Below must keep the anchor column.")
                .isEqualTo(0);
    }

    @Test
    void testDeleteRows_withTheSecondAndThirdRowsSelected_deletesBothInOneUndoStep()
            throws ExecutionException
    {
        final IDocument document = new Document(
                "<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/><USERS ID=\"4\"/></dataset>");
        DocumentUndoManagerRegistry.connect(document);
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(this);
        try
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final TestContext context = new TestContext(datasetDocument, "USERS");
            context.rowIndexes = List.of(1, 2);
            final DeleteRowsAction action = new DeleteRowsAction(context);

            action.run();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .as("Deleting the selected rows must remove both.").hasSize(2);
            assertThat(context.multiCellEditTitles)
                    .as("Delete Rows must report a rejected edit through a modal dialog, not just the "
                            + "status line.")
                    .containsExactly("Delete Rows");

            undoManager.undo();
            datasetDocument.refresh();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .as("Deleting two rows must be one undo step.").hasSize(4);
        }
        finally
        {
            undoManager.disconnect(this);
            DocumentUndoManagerRegistry.disconnect(document);
        }
    }

    @Test
    void testUpdate_reflectsWhetherThePageIsEditable()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final InsertRowAboveAction action = new InsertRowAboveAction(context);

        action.update(context.getSelection());
        assertThat(action.isEnabled())
                .as("An editable page with a column must enable Insert Row Above.").isTrue();

        context.editable = false;
        action.update(context.getSelection());
        assertThat(action.isEnabled()).as("A read-only page must disable the action.").isFalse();
    }

    @Test
    void testRun_whileACellEditorIsActive_changesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.hasActiveCellEditor = true;
        final InsertRowBelowAction action = new InsertRowBelowAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                .as("Insert Row Below must do nothing while a cell editor is active.").hasSize(1);
    }

    @Test
    void testDuplicateRows_withTwoRowsSelected_insertsCopiesDirectlyAfterThemAndSelectsTheNewBlock()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        final DuplicateRowsAction action = new DuplicateRowsAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).as("Duplicating two rows must add two rows.").hasSize(5);
        assertThat(table.getRows().get(2).getValue(0)).as("The duplicate block must copy the first row.")
                .isEqualTo("1");
        assertThat(table.getRows().get(3).getValue(0))
                .as("The duplicate block must copy the second row, in order.").isEqualTo("2");
        assertThat(context.pendingSelectionRow).as("Duplicate Rows must select the new block's first row.")
                .isEqualTo(2);
    }

    @Test
    void testMoveRowsUp_withAContiguousBlockSelected_movesItUpAndKeepsItSelected()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1, 2);
        final MoveRowsUpAction action = new MoveRowsUpAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(0).getValue(0)).as("Moving the block up must place it first.")
                .isEqualTo("2");
        assertThat(table.getRows().get(1).getValue(0)).isEqualTo("3");
        assertThat(table.getRows().get(2).getValue(0))
                .as("The row the block moved past must follow it.").isEqualTo("1");
        assertThat(context.pendingSelectionRow).as("Move Rows Up must keep the block selected.")
                .isEqualTo(0);
    }

    @Test
    void testMoveRowsDown_withAContiguousBlockSelected_movesItDownAndKeepsItSelected()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        final MoveRowsDownAction action = new MoveRowsDownAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(0).getValue(0))
                .as("The row the block moved past must precede it.").isEqualTo("3");
        assertThat(table.getRows().get(1).getValue(0)).as("Moving the block down must place it after.")
                .isEqualTo("1");
        assertThat(table.getRows().get(2).getValue(0)).isEqualTo("2");
        assertThat(context.pendingSelectionRow).as("Move Rows Down must keep the block selected.")
                .isEqualTo(1);
    }

    @Test
    void testUpdate_forMoveRowsUpAndDown_disabledWhenNotContiguousOrAtTheEdge()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final MoveRowsUpAction up = new MoveRowsUpAction(context);
        final MoveRowsDownAction down = new MoveRowsDownAction(context);

        context.rowIndexes = List.of(0, 2);
        up.update(context.getSelection());
        down.update(context.getSelection());
        assertThat(up.isEnabled()).as("A non-contiguous selection must disable Move Rows Up.").isFalse();
        assertThat(down.isEnabled()).as("A non-contiguous selection must disable Move Rows Down.")
                .isFalse();

        context.rowIndexes = List.of(0, 1);
        up.update(context.getSelection());
        down.update(context.getSelection());
        assertThat(up.isEnabled()).as("A block already at the top must disable Move Rows Up.").isFalse();
        assertThat(down.isEnabled()).as("A block not at the bottom must enable Move Rows Down.").isTrue();

        context.rowIndexes = List.of(1, 2);
        up.update(context.getSelection());
        down.update(context.getSelection());
        assertThat(up.isEnabled()).as("A block not at the top must enable Move Rows Up.").isTrue();
        assertThat(down.isEnabled()).as("A block already at the bottom must disable Move Rows Down.")
                .isFalse();
    }

    @Test
    void testAddColumn_whenTheDialogReturnsAName_addsThePendingColumn()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final AddColumnAction action = new AddColumnAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final IInputValidator validator)
            {
                return "EMAIL";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                .extracting(DatasetColumn::name).as("Add Column must add the entered name.")
                .contains("EMAIL");
    }

    @Test
    void testRenameColumn_whenTheDialogReturnsANewName_renamesTheAnchorColumn()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        final RenameColumnAction action = new RenameColumnAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator)
            {
                return "USER_ID";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns().get(0).name())
                .as("Rename Column must apply the entered name.").isEqualTo("USER_ID");
    }

    @Test
    void testDeleteColumn_whenConfirmed_deletesTheAnchorColumnNamedWithItsValueCount()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 1;
        final DeleteColumnAction action = new DeleteColumnAction(context)
        {
            @Override
            boolean confirmDelete(final Shell shell, final DatasetColumn column, final int valueCount)
            {
                assertThat(column.name()).as("The confirmation must name the anchor column.")
                        .isEqualTo("NAME");
                assertThat(valueCount).as("The confirmation must count the column's current values.")
                        .isEqualTo(1);
                return true;
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                .extracting(DatasetColumn::name).as("Confirmed deletion must remove the column.")
                .doesNotContain("NAME");
    }

    @Test
    void testDeleteColumn_whenNotConfirmed_changesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 1;
        final DeleteColumnAction action = new DeleteColumnAction(context)
        {
            @Override
            boolean confirmDelete(final Shell shell, final DatasetColumn column, final int valueCount)
            {
                return false;
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                .extracting(DatasetColumn::name).as("Declining the confirmation must change nothing.")
                .contains("NAME");
    }

    @Test
    void testAddTable_whenTheDialogReturnsANameAndColumns_addsTheTableAndSelectsItsTab()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final AddTableAction action = new AddTableAction(context)
        {
            @Override
            AddTableDialog openDialog(final Shell shell, final DatasetNameValidator nameValidator)
            {
                return new AddTableDialog(shell, nameValidator)
                {
                    @Override
                    public String getTableName()
                    {
                        return "Accounts";
                    }

                    @Override
                    public List<String> getColumnNames()
                    {
                        return List.of("ID", "BALANCE");
                    }
                };
            }
        };

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("ACCOUNTS").orElseThrow();
        assertThat(table.getColumns()).extracting(DatasetColumn::name)
                .as("Add Table must create the table with the entered columns.")
                .containsExactly("ID", "BALANCE");
        assertThat(context.expectedNewTableKey)
                .as("Add Table must select the new table's tab by its case-folded key.")
                .isEqualTo("ACCOUNTS");
    }

    @Test
    void testRenameTable_whenTheDialogReturnsANewName_renamesTheTableAndKeepsItsTab()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final RenameTableAction action = new RenameTableAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator)
            {
                return "Customers";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("CUSTOMERS")).as("Rename Table must apply the entered name.")
                .isPresent();
        assertThat(context.expectedRenameOldKey).as("Rename Table must record the old tab key.")
                .isEqualTo("USERS");
        assertThat(context.expectedRenameNewKey)
                .as("Rename Table must record the new tab's case-folded key.").isEqualTo("CUSTOMERS");
    }

    @Test
    void testDeleteTable_whenConfirmed_removesTheTable()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\"/><ACCOUNTS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final DeleteTableAction action = new DeleteTableAction(context)
        {
            @Override
            boolean confirmDelete(final Shell shell, final DatasetTable table)
            {
                assertThat(table.getName()).as("The confirmation must name the active table.")
                        .isEqualTo("USERS");
                return true;
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS"))
                .as("Confirmed deletion must remove the table.").isEmpty();
        assertThat(datasetDocument.getModel().findTable("ACCOUNTS"))
                .as("Deleting one table must not affect the others.").isPresent();
    }

    @Test
    void testDeleteTable_whenNotConfirmed_changesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final DeleteTableAction action = new DeleteTableAction(context)
        {
            @Override
            boolean confirmDelete(final Shell shell, final DatasetTable table)
            {
                return false;
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS"))
                .as("Declining the confirmation must change nothing.").isPresent();
    }

    private static FlatXmlDatasetDocument create(final String content)
    {
        return create(new Document(content));
    }

    private static FlatXmlDatasetDocument create(final IDocument document)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }

    private static final class TestContext implements DatasetGridContext
    {
        private final DatasetDocument datasetDocument;

        private final String tableKey;

        private boolean editable = true;

        private boolean hasActiveCellEditor;

        private final List<String> multiCellEditTitles = new ArrayList<>();

        private int anchorColumnIndex = -1;

        private int anchorRowIndex = -1;

        private List<Integer> rowIndexes = List.of();

        private int pendingSelectionColumn = -1;

        private int pendingSelectionRow = -1;

        private String expectedRenameOldKey;

        private String expectedRenameNewKey;

        private String expectedNewTableKey;

        TestContext(final DatasetDocument datasetDocument, final String tableKey)
        {
            this.datasetDocument = datasetDocument;
            this.tableKey = tableKey;
        }

        @Override
        public DatasetDocument getDatasetDocument()
        {
            return datasetDocument;
        }

        @Override
        public boolean isEditable()
        {
            return editable;
        }

        @Override
        public String getNullDisplayText()
        {
            return "(null)";
        }

        @Override
        public boolean isDarkTheme()
        {
            return false;
        }

        @Override
        public boolean executeEdit(final Runnable edit)
        {
            if (!editable)
            {
                return false;
            }
            try
            {
                edit.run();
                return true;
            }
            catch (final DatasetEditException e)
            {
                return false;
            }
        }

        @Override
        public boolean executeMultiCellEdit(final String title, final Runnable edit)
        {
            multiCellEditTitles.add(title);
            return executeEdit(edit);
        }

        @Override
        public void fillContextMenu(final IMenuManager menu, final String region)
        {
        }

        @Override
        public boolean hasActiveCellEditor()
        {
            return hasActiveCellEditor;
        }

        @Override
        public GridSelection getSelection()
        {
            final DatasetTable table = datasetDocument.getModel().findTable(tableKey).orElseThrow();
            final int rowCount = table.getRows().size();
            final int columnCount = table.getColumns().size();
            final int firstRow = rowIndexes.isEmpty() ? -1 : rowIndexes.get(0);
            final int lastRow = rowIndexes.isEmpty() ? -1 : rowIndexes.get(rowIndexes.size() - 1);
            return new GridSelection(tableKey, rowCount, columnCount, anchorColumnIndex, anchorRowIndex,
                    rowIndexes, List.of(), firstRow, lastRow, -1, -1, !rowIndexes.isEmpty());
        }

        @Override
        public void setPendingSelection(final int columnIndex, final int rowIndex)
        {
            pendingSelectionColumn = columnIndex;
            pendingSelectionRow = rowIndex;
        }

        @Override
        public Shell getShell()
        {
            return null;
        }

        @Override
        public void expectRename(final String oldKey, final String newKey)
        {
            expectedRenameOldKey = oldKey;
            expectedRenameNewKey = newKey;
        }

        @Override
        public void expectNewTableSelected(final String tableKey)
        {
            expectedNewTableKey = tableKey;
        }
    }
}
