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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link GridAction}, the base of the actions of the Tables page, against the Commands
 * specification: the refresh of the model before a command runs, a command that does nothing while a cell
 * editor is open, the report of a rejected edit or of a table that is gone as the error message, the
 * enablement of the actions on a read-only page, and the icons of the actions.
 */
class GridActionTest extends GridActionFixture
{
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
    void testRun_whenTheTextChangedAndNothingRefreshedTheDocument_refreshesItBeforeTheCommandRuns()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final List<Boolean> staleWhenTheCommandRan = new ArrayList<>();
        final GridAction action = new GridAction(context)
        {
            @Override
            protected void runOnGrid(final DatasetGridContext gridContext)
            {
                staleWhenTheCommandRan.add(datasetDocument.isStale());
            }

            @Override
            protected boolean isEnabledFor(final GridSelection selection)
            {
                return true;
            }
        };
        document.replace(document.get().indexOf("<USERS"), 0, "<AUDIT_LOG/>");

        action.run();

        assertThat(staleWhenTheCommandRan)
                .as("A command that is queued behind a change of the text must run on the model of the "
                        + "text as it is now.")
                .containsExactly(false);
    }

    @Test
    void testRun_whileACellEditorIsActiveAndTheTextChanged_doesNotRefreshTheDocument() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.hasActiveCellEditor = true;
        final Text text = new Text(shell, SWT.NONE);
        text.setText("hello world");
        text.setSelection(0, 5);
        context.activeCellEditorText = text;
        document.replace(document.get().indexOf("<USERS"), 0, "<AUDIT_LOG/>");

        new CopyAction(context).run();

        assertThat(datasetDocument.isStale())
                .as("A refresh rebuilds the grids and can close the editor, so a command that works on "
                        + "the editor's text must leave it to the page.")
                .isTrue();
    }

    @Test
    void testRun_whenRunOnGridThrowsADatasetEditException_reportsItAsAnErrorMessageInsteadOfPropagating()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final GridAction action = new GridAction(context)
        {
            @Override
            protected void runOnGrid(final DatasetGridContext context)
            {
                throw new DatasetEditException("The selection is out of date.");
            }

            @Override
            protected boolean isEnabledFor(final GridSelection selection)
            {
                return true;
            }
        };

        action.run();

        assertThat(context.statusErrorMessage)
                .as("A DatasetEditException thrown directly by runOnGrid must be reported as the status "
                        + "line's error message, like a rejected edit, instead of propagating.")
                .isEqualTo("The selection is out of date.");
        assertThat(context.statusMessage)
                .as("The rejection must not be reported as a normal message, which an earlier error "
                        + "message would hide.")
                .isNull();
    }

    @Test
    void testRun_whenTheSelectionNamesATableThatNoLongerExists_reportsItAsAnErrorMessage()
    {
        final List<Function<DatasetGridContext, GridAction>> actionsOfASelectedTable =
                List.of(AddColumnAction::new, CopyAction::new, DeleteColumnAction::new, DeleteTableAction::new,
                        FillDownAction::new, PasteAction::new, RenameColumnAction::new, RenameTableAction::new,
                        SetEmptyStringAction::new, SetNullAction::new);
        final Map<String, String> errorMessages = new LinkedHashMap<>();

        for (final Function<DatasetGridContext, GridAction> newAction : actionsOfASelectedTable)
        {
            final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
            final TestContext context = new TestContext(datasetDocument, "USERS");
            context.staleSelection = new GridSelection("GONE", 1, 1, 0, 0, List.of(0), List.of(0), false);
            context.clipboardText = "X";
            final GridAction action = newAction.apply(context);

            action.run();

            errorMessages.put(action.getClass().getSimpleName(), context.statusErrorMessage);
        }

        assertThat(errorMessages).as("Each action that needs the selected table must have been run.")
                .hasSize(actionsOfASelectedTable.size())
                .allSatisfy((actionName, errorMessage) -> assertThat(errorMessage)
                        .as("A selection that names a table missing from the model must be reported as the "
                                + "status line's error message by " + actionName + ".")
                        .isEqualTo("There is no table 'GONE'."));
    }

    @Test
    void testUpdate_onAReadOnlyPage_enablesCopyAndSelectAllButNotCut()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0);
        context.editable = false;
        final List<GridAction> actions =
                List.of(new CopyAction(context), new SelectAllAction(context), new CutAction(context));
        final List<Boolean> enabled = new ArrayList<>();
        for (final GridAction action : actions)
        {
            action.update(context.getSelection());
            enabled.add(action.isEnabled());
        }

        assertThat(enabled).as("A read-only page must still copy and select cells, but not cut them.")
                .containsExactly(true, true, false);
    }

    @Test
    void testImageDescriptor_ofEachActionWithAGeneratedIcon_isThatIcon()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");

        final List<ImageDescriptor> actionImages = List.of(
                new InsertRowAboveAction(context).getImageDescriptor(),
                new InsertRowBelowAction(context).getImageDescriptor(),
                new DuplicateRowsAction(context).getImageDescriptor(),
                new DeleteRowsAction(context).getImageDescriptor(),
                new AddColumnAction(context).getImageDescriptor(),
                new DeleteColumnAction(context).getImageDescriptor(),
                new AddTableAction(context).getImageDescriptor(),
                new SetNullAction(context).getImageDescriptor(),
                new FillDownAction(context).getImageDescriptor());

        assertThat(actionImages).as("Every command with a generated icon must show it in toolbars and menus.")
                .containsExactly(DatasetImages.getImageDescriptor(DatasetImages.IMG_INSERT_ROW_ABOVE),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_INSERT_ROW_BELOW),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_DUPLICATE_ROWS),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_DELETE_ROWS),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_ADD_COLUMN),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_DELETE_COLUMN),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_ADD_TABLE),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_SET_NULL),
                        DatasetImages.getImageDescriptor(DatasetImages.IMG_FILL_DOWN));
    }
}
