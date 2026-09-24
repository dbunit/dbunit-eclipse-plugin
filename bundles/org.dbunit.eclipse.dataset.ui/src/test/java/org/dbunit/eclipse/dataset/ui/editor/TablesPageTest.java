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

import java.util.List;

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.eclipse.core.resources.IFile;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link TablesPage} against the Tables Page layout, reconciliation, and refresh-scheduling rules.
 */
class TablesPageTest
{
    @Test
    void testTablesPage_whenOpened_showsOneTabPerTableInModelOrder() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\"/><ORDERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final CTabFolder tabFolder = editor.getTablesPage().getTabFolder();

            assertThat(tabFolder.getItemCount()).as("There must be one tab per table.").isEqualTo(2);
            assertThat(tabFolder.getItem(0).getText()).as("Tabs must be in model order.")
                    .isEqualTo("USERS");
            assertThat(tabFolder.getItem(1).getText()).as("Tabs must be in model order.")
                    .isEqualTo("ORDERS");
        }
    }

    @Test
    void testTablesPage_whenTablesAreAddedAndRemoved_updatesTheTabs() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();

            datasetDocument.addTable("ORDERS", List.of());

            assertThat(tablesPage.getTabFolder().getItemCount()).as("Adding a table must add a tab.")
                    .isEqualTo(2);

            datasetDocument.deleteTable("USERS");

            assertThat(tablesPage.getTabFolder().getItemCount())
                    .as("Deleting a table must remove its tab.").isEqualTo(1);
            assertThat(tablesPage.getTabFolder().getItem(0).getText())
                    .as("The remaining table's tab must survive.").isEqualTo("ORDERS");
        }
    }

    @Test
    void testTablesPage_whenRenamingATableThroughExpectRename_keepsTheSameTabItem() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final CTabItem originalItem = tablesPage.getTabFolder().getItem(0);

            tablesPage.expectRename("USERS", "CUSTOMERS");
            editor.getDatasetDocument().renameTable("USERS", "CUSTOMERS");

            assertThat(tablesPage.getTabFolder().getItemCount()).as("The rename must not add a tab.")
                    .isEqualTo(1);
            assertThat(tablesPage.getTabFolder().getItem(0))
                    .as("expectRename must keep the same tab item, not recreate it.")
                    .isSameAs(originalItem);
            assertThat(tablesPage.getTabFolder().getItem(0).getText())
                    .as("The surviving tab must show the new name.").isEqualTo("CUSTOMERS");
        }
    }

    @Test
    void testTablesPage_whenXmlHasABlockingError_showsTheBannerAndIsNotEditable() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("broken.xml", "<dataset><USERS ID=\"1\"</dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();

            assertThat(tablesPage.isEditable())
                    .as("A blocking XML error must make the page non-editable.").isFalse();
            assertThat(tablesPage.getErrorBanner().getControl().getVisible())
                    .as("A blocking XML error must show the banner.").isTrue();
        }
    }

    @Test
    void testTablesPage_whenFileIsBlank_showsCreateEmptyDatasetButtonThatCreatesIt() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("blank.xml", "");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();

            assertThat(tablesPage.isShowingBlankState())
                    .as("A blank file must show the blank-document state.").isTrue();

            tablesPage.getCreateEmptyDatasetButton().notifyListeners(SWT.Selection, new Event());
            UiTestWorkspace.processEvents();

            assertThat(editor.getDatasetDocument().isBlank())
                    .as("Clicking Create Empty Dataset must create the dataset.").isFalse();
            assertThat(tablesPage.isShowingBlankState())
                    .as("The page must switch away from the blank-document state.").isFalse();
        }
    }

    @Test
    void testTablesPage_whenFileIsBlank_centersTheMessageAndButtonWithoutTheBanner() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("blank.xml", "");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final Composite page = (Composite) tablesPage.getControl();
            page.setSize(800, 600);
            page.layout(true, true);
            final Button button = tablesPage.getCreateEmptyDatasetButton();
            final Rectangle buttonBounds = page.getDisplay().map(button.getParent(), page, button.getBounds());

            assertThat(tablesPage.getErrorBanner().getControl().getVisible())
                    .as("A blank file must not show the error banner, as the blank state explains it.")
                    .isFalse();
            assertThat(Math.abs(buttonBounds.y + buttonBounds.height / 2 - 300))
                    .as("The button must sit with the message at the center of the page, not at its bottom.")
                    .isLessThan(100);
        }
    }

    @Test
    void testTablesPage_whenBlankFileIsReadOnly_disablesCreateEmptyDatasetAndLeavesTheFileBlank()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openReadOnlyExternalFile("");
            final Button button = editor.getTablesPage().getCreateEmptyDatasetButton();

            assertThat(button.getEnabled()).as("A read-only file must disable Create Empty Dataset.").isFalse();

            button.notifyListeners(SWT.Selection, new Event());
            UiTestWorkspace.processEvents();

            assertThat(editor.getDatasetDocument().isBlank())
                    .as("Create Empty Dataset must not change a file whose input state does not validate.")
                    .isTrue();
        }
    }

    @Test
    void testRefreshScheduling_whenTablesPageIsNotActive_refreshesOnlyOnActivation() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            tablesPage.deactivate();

            final ITextEditor sourceEditor = editor.getSourceEditor();
            sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput()).replace(0, 0,
                    "<!--x-->");
            UiTestWorkspace.processEvents();

            assertThat(editor.getDatasetDocument().isStale())
                    .as("A source change while the Tables page is inactive must not refresh immediately.")
                    .isTrue();

            tablesPage.activate();

            assertThat(editor.getDatasetDocument().isStale())
                    .as("Activating the Tables page must refresh the stale model.").isFalse();
        }
    }
}
