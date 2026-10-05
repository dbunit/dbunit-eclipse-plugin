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
package org.dbunit.eclipse.dataset.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link DatasetEditorContributor} installs the global Edit, Revert, and Print actions of the
 * active dataset editor's active page, and shows the status fields of the Source page only while it is
 * active.
 */
class DatasetEditorContributorTest
{
    private static final String DATASET = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";

    private static final String[] GLOBAL_ACTION_IDS = { ActionFactory.UNDO.getId(), ActionFactory.REDO.getId(),
            ActionFactory.CUT.getId(), ActionFactory.COPY.getId(), ActionFactory.PASTE.getId(),
            ActionFactory.DELETE.getId(), ActionFactory.SELECT_ALL.getId(), ActionFactory.FIND.getId() };

    private static final String[] TEXT_EDITOR_ACTION_IDS = { ITextEditorActionConstants.UNDO,
            ITextEditorActionConstants.REDO, ITextEditorActionConstants.CUT, ITextEditorActionConstants.COPY,
            ITextEditorActionConstants.PASTE, ITextEditorActionConstants.DELETE,
            ITextEditorActionConstants.SELECT_ALL, ITextEditorActionConstants.FIND };

    @Test
    void testSetActiveEditor_whenAnEditorOpensOnTheTablesPage_installsTheTablesPageActions() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DATASET);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            assertThat(installedActions(editor))
                    .as("An editor that opens on the Tables page must install that page's Edit actions.")
                    .isEqualTo(tablesPageActions(editor));
        }
    }

    @Test
    void testSetActivePage_toTheSourcePage_installsTheTextEditorActions() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DATASET);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            editor.showOnSourcePage(0, 0);
            UiTestWorkspace.processEvents();

            assertThat(installedActions(editor)).as("The Source page must install the text editor's Edit actions.")
                    .isEqualTo(textEditorActions(editor.getSourceEditor()));
        }
    }

    @Test
    void testSetActiveEditor_withTwoDatasetEditorsOpen_installsTheActivatedEditorsActions() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final FlatXmlDatasetEditor first =
                    (FlatXmlDatasetEditor) workspace.open(workspace.createFile("first.xml", DATASET));
            final FlatXmlDatasetEditor second =
                    (FlatXmlDatasetEditor) workspace.open(workspace.createFile("second.xml", DATASET));

            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(first);
            UiTestWorkspace.processEvents();

            assertThat(installedActions(first))
                    .as("Activating an editor must install its own Tables page's Edit actions, not those of the "
                            + "editor that was active before.")
                    .isEqualTo(tablesPageActions(first))
                    .isNotEqualTo(tablesPageActions(second));
        }
    }

    @Test
    void testSetActiveEditor_whenAnEditorOpensOnTheTablesPage_installsTheTextEditorsRevertAction()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DATASET);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            assertThat(installedAction(editor, ActionFactory.REVERT))
                    .as("File > Revert must work on the Tables page too, which edits the same document.")
                    .isNotNull()
                    .isSameAs(editor.getSourceEditor().getAction(ITextEditorActionConstants.REVERT));
        }
    }

    @Test
    void testSetActiveEditor_whenAnEditorOpensOnTheTablesPage_installsNoPrintAction() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DATASET);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            assertThat(installedAction(editor, ActionFactory.PRINT))
                    .as("The grid has nothing to print, so File > Print must stay disabled on the Tables page.")
                    .isNull();
        }
    }

    @Test
    void testSetActivePage_toTheSourcePage_installsTheTextEditorsPrintAndRevertActions() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DATASET);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final ITextEditor sourceEditor = editor.getSourceEditor();

            editor.showOnSourcePage(0, 0);
            UiTestWorkspace.processEvents();

            assertThat(installedAction(editor, ActionFactory.PRINT))
                    .as("The Source page must install the text editor's Print action.").isNotNull()
                    .isSameAs(sourceEditor.getAction(ITextEditorActionConstants.PRINT));
            assertThat(installedAction(editor, ActionFactory.REVERT))
                    .as("The Source page must install the text editor's Revert action.").isNotNull()
                    .isSameAs(sourceEditor.getAction(ITextEditorActionConstants.REVERT));
        }
    }

    @Test
    void testSetActivePage_backToTheTablesPage_removesThePrintActionAndKeepsTheRevertAction() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DATASET);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            editor.showOnSourcePage(0, 0);
            UiTestWorkspace.processEvents();

            showTablesPage(editor);

            assertThat(installedAction(editor, ActionFactory.PRINT))
                    .as("Leaving the Source page must remove its Print action.").isNull();
            assertThat(installedAction(editor, ActionFactory.REVERT))
                    .as("The text editor's Revert action must stay installed on the Tables page.")
                    .isNotNull()
                    .isSameAs(editor.getSourceEditor().getAction(ITextEditorActionConstants.REVERT));
        }
    }

    @Test
    void testSetActiveEditor_withTwoDatasetEditorsOpen_installsTheActivatedEditorsRevertAction()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final FlatXmlDatasetEditor first =
                    (FlatXmlDatasetEditor) workspace.open(workspace.createFile("first.xml", DATASET));
            final FlatXmlDatasetEditor second =
                    (FlatXmlDatasetEditor) workspace.open(workspace.createFile("second.xml", DATASET));

            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(first);
            UiTestWorkspace.processEvents();

            assertThat(installedAction(first, ActionFactory.REVERT))
                    .as("Activating an editor must install its own Revert action, not that of the editor "
                            + "that was active before.")
                    .isSameAs(first.getSourceEditor().getAction(ITextEditorActionConstants.REVERT))
                    .isNotSameAs(second.getSourceEditor().getAction(ITextEditorActionConstants.REVERT));
        }
    }

    @Test
    void testContributeToStatusLine_whenAnEditorOpens_addsTheTextEditorsFieldsToItsStatusLine()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DATASET);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IActionBars actionBars = editor.getEditorSite().getActionBars();
            final IStatusLineManager statusLine = actionBars.getStatusLineManager();

            final List<IContributionItem> fields = new ArrayList<>();
            for (final String category : StatusFieldProbe.CATEGORIES)
            {
                fields.add(statusLine.find(category));
            }

            assertThat(fields).as("The status line of the dataset editor must have the Source page's fields.")
                    .doesNotContainNull();
        }
    }

    @Test
    void testSetActivePage_toTheSourcePage_showsTheStatusFieldsOfTheSourcePage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DATASET);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final int usersOffset = DATASET.indexOf("<USERS");

            editor.showOnSourcePage(usersOffset, 0);
            UiTestWorkspace.processEvents();

            try (StatusFieldProbe probe = new StatusFieldProbe(statusFieldsOf(editor)))
            {
                assertThat(probe.visibleCategories()).as("The Source page must show its status fields.")
                        .containsExactly(StatusFieldProbe.ELEMENT_STATE, StatusFieldProbe.INPUT_MODE,
                                StatusFieldProbe.INPUT_POSITION);
                assertThat(probe.position())
                        .as("The position field must show the text cursor of the Source page.")
                        .isEqualTo("1:" + (usersOffset + 1));
            }
        }
    }

    @Test
    void testSetActiveEditor_whenAnEditorOpensOnTheSourcePage_showsTheStatusFields() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("broken.xml", "<dataset><USERS ID=\"1\"</dataset>");

            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            try (StatusFieldProbe probe = new StatusFieldProbe(statusFieldsOf(editor)))
            {
                assertThat(editor.isSourcePageActive())
                        .as("A file with an XML error must open on the Source page.").isTrue();
                assertThat(probe.visibleCategories())
                        .as("An editor that opens on the Source page must show its status fields.")
                        .containsExactly(StatusFieldProbe.ELEMENT_STATE, StatusFieldProbe.INPUT_MODE,
                                StatusFieldProbe.INPUT_POSITION);
            }
        }
    }

    @Test
    void testSetActivePage_backToTheTablesPage_hidesTheStatusFields() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DATASET);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            editor.showOnSourcePage(0, 0);
            UiTestWorkspace.processEvents();

            showTablesPage(editor);

            try (StatusFieldProbe probe = new StatusFieldProbe(statusFieldsOf(editor)))
            {
                assertThat(probe.visibleCategories())
                        .as("The Tables page has no use for the status fields of the text editor.").isEmpty();
            }
        }
    }

    @Test
    void testSetActiveEditor_withTwoDatasetEditorsOpen_showsTheStatusFieldsOfTheActivatedSourcePageOnly()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final FlatXmlDatasetEditor first =
                    (FlatXmlDatasetEditor) workspace.open(workspace.createFile("first.xml", DATASET));
            first.showOnSourcePage(0, 0);
            final FlatXmlDatasetEditor second =
                    (FlatXmlDatasetEditor) workspace.open(workspace.createFile("second.xml", DATASET));
            final int usersOffset = DATASET.indexOf("<USERS");
            try (StatusFieldProbe probe = new StatusFieldProbe(statusFieldsOf(second)))
            {
                final List<String> whileTheSecondEditorShowsItsTablesPage = probe.visibleCategories();

                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(first);
                UiTestWorkspace.processEvents();
                second.getSourceEditor().selectAndReveal(1, 0);
                first.getSourceEditor().selectAndReveal(usersOffset, 0);

                assertThat(whileTheSecondEditorShowsItsTablesPage)
                        .as("An editor on its Tables page must show no status fields.").isEmpty();
                assertThat(probe.visibleCategories())
                        .as("Activating an editor on its Source page must show the fields.")
                        .containsExactly(StatusFieldProbe.ELEMENT_STATE, StatusFieldProbe.INPUT_MODE,
                                StatusFieldProbe.INPUT_POSITION);
                assertThat(probe.position())
                        .as("The fields must follow the activated editor, not the one before.")
                        .isEqualTo("1:" + (usersOffset + 1));
            }
        }
    }

    private static SourceStatusFields statusFieldsOf(final FlatXmlDatasetEditor editor)
    {
        final DatasetEditorContributor contributor =
                (DatasetEditorContributor) editor.getEditorSite().getActionBarContributor();
        return contributor.getStatusFields();
    }

    private static IAction installedAction(final FlatXmlDatasetEditor editor, final ActionFactory factory)
    {
        return editor.getEditorSite().getActionBars().getGlobalActionHandler(factory.getId());
    }

    private static void showTablesPage(final FlatXmlDatasetEditor editor)
    {
        final CTabFolder pages = (CTabFolder) editor.getTablesPage().getControl().getParent();
        final CTabItem tablesTab = pages.getItem(0);
        pages.setSelection(tablesTab);
        final Event click = new Event();
        click.item = tablesTab;
        pages.notifyListeners(SWT.Selection, click);
        UiTestWorkspace.processEvents();
    }

    private static List<IAction> installedActions(final FlatXmlDatasetEditor editor)
    {
        final IActionBars actionBars = editor.getEditorSite().getActionBars();
        final List<IAction> actions = new ArrayList<>();
        for (final String id : GLOBAL_ACTION_IDS)
        {
            actions.add(actionBars.getGlobalActionHandler(id));
        }
        return actions;
    }

    private static List<IAction> tablesPageActions(final FlatXmlDatasetEditor editor)
    {
        final List<IAction> actions = new ArrayList<>();
        for (final String id : GLOBAL_ACTION_IDS)
        {
            actions.add(editor.getTablesPage().getGlobalActionHandler(id));
        }
        return actions;
    }

    private static List<IAction> textEditorActions(final ITextEditor textEditor)
    {
        final List<IAction> actions = new ArrayList<>();
        for (final String id : TEXT_EDITOR_ACTION_IDS)
        {
            actions.add(textEditor.getAction(id));
        }
        return actions;
    }
}
