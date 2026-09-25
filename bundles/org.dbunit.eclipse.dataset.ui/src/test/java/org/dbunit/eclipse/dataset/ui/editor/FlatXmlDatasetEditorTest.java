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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.ui.DatasetUiPlugin;
import org.dbunit.eclipse.dataset.ui.preferences.PreferenceKeys;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.data.convert.IDisplayConverter;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.layer.event.ILayerEvent;
import org.eclipse.nebula.widgets.nattable.layer.event.VisualRefreshEvent;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlDatasetEditor} against the Editor Structure lifecycle rules and its reaction to
 * preference changes.
 */
class FlatXmlDatasetEditorTest
{
    private static final int TABLES_PAGE_INDEX = 0;

    private static final int SOURCE_PAGE_INDEX = 1;

    @AfterEach
    void restoreDefaultPreferences()
    {
        final IPreferenceStore store = preferenceStore();
        store.setToDefault(PreferenceKeys.NULL_DISPLAY_TEXT);
        store.setToDefault(PreferenceKeys.ASSUME_COLUMN_SENSING);
        store.setToDefault(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES);
    }

    @Test
    void testOpen_whenFileIsFlatXml_showsTablesAndSourcePages() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");

            final IEditorPart editor = workspace.open(file);

            assertThat(editor).as("A flat XML file must open in the dataset editor.")
                    .isInstanceOf(FlatXmlDatasetEditor.class);
            final FlatXmlDatasetEditor datasetEditor = (FlatXmlDatasetEditor) editor;
            assertThat(datasetEditor.getPageTitle(TABLES_PAGE_INDEX)).as("Page 0 must be Tables.")
                    .isEqualTo("Tables");
            assertThat(datasetEditor.getPageTitle(SOURCE_PAGE_INDEX)).as("Page 1 must be Source.")
                    .isEqualTo("Source");
        }
    }

    @Test
    void testCurrentCharset_whenXmlDeclaresIso88591_returnsThatCharset() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final String content = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><dataset/>";
            final IFile file = workspace.createFile("dataset.xml", content, StandardCharsets.ISO_8859_1);

            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            assertThat(editor.currentCharset())
                    .as("The XML declaration's encoding must become the editor's charset.")
                    .isEqualTo(StandardCharsets.ISO_8859_1);
        }
    }

    @Test
    void testContentType_whenFileIsNotADataset_isNeitherTheFlatXmlTypeNorTheDefaultEditor()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            // Not opened: without an XML editor in the test runtime, Eclipse may launch an external
            // program for the resolved default editor.
            final IFile file = workspace.createFile("other.xml", "<notADataset/>");

            final String contentTypeId = file.getContentDescription().getContentType().getId();

            assertThat(contentTypeId).as("A non-dataset XML file must not get the flat XML content type.")
                    .isNotEqualTo("org.dbunit.eclipse.dataset.flatxml");
            final var defaultEditor = IDE.getDefaultEditor(file);
            assertThat(defaultEditor == null || !FlatXmlDatasetEditor.ID.equals(defaultEditor.getId()))
                    .as("The dataset editor must not be the default editor for a non-dataset XML file.")
                    .isTrue();
        }
    }

    @Test
    void testSourceEdit_whenDocumentChanges_makesEditorDirtyAndDoSaveWritesTheFile() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final ITextEditor sourceEditor = editor.getSourceEditor();

            sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput()).replace(0, 0,
                    "<!--edited-->");
            UiTestWorkspace.processEvents();
            assertThat(editor.isDirty()).as("A source edit must make the editor dirty.").isTrue();

            editor.doSave(new NullProgressMonitor());
            UiTestWorkspace.processEvents();

            assertThat(editor.isDirty()).as("Saving must clear the dirty state.").isFalse();
            final String written;
            try (InputStream contents = file.getContents())
            {
                written = new String(contents.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertThat(written).as("The saved file must contain the source edit.")
                    .isEqualTo("<!--edited--><dataset><USERS ID=\"1\"/></dataset>");
        }
    }

    @Test
    void testOpen_whenFileHasAnXmlError_opensOnTheSourcePage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file =
                    workspace.createFile("broken.xml", "<dataset><USERS ID=\"1\"</dataset>");

            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            assertThat(editor.getActivePage())
                    .as("A file with an XML error must open on the Source page.")
                    .isEqualTo(SOURCE_PAGE_INDEX);
        }
    }

    @Test
    void testFileDeleted_whenEditorIsClean_closesTheEditor() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final IEditorPart editor = workspace.open(file);
            final IEditorInput input = editor.getEditorInput();
            final IWorkbenchPage page =
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

            file.delete(true, null);
            UiTestWorkspace.processEvents();

            assertThat(page.findEditor(input)).as("Deleting a clean file must close its editor.")
                    .isNull();
        }
    }

    @Test
    void testGetAdapter_forIGotoMarker_switchesToTheSourcePage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            assertThat(editor.getActivePage()).as("An editable dataset must start on the Tables page.")
                    .isEqualTo(TABLES_PAGE_INDEX);

            final IGotoMarker gotoMarker = editor.getAdapter(IGotoMarker.class);

            assertThat(gotoMarker).as("IGotoMarker must be available from the source editor.")
                    .isNotNull();
            assertThat(editor.getActivePage())
                    .as("Requesting IGotoMarker must switch to the Source page.")
                    .isEqualTo(SOURCE_PAGE_INDEX);
        }
    }

    @Test
    void testPreferenceChange_ofCaseSensitiveTableNames_regroupsTheTablesOfTheOpenEditor() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\"/><users ID=\"2\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            assertThat(tabItems(editor)).extracting(CTabItem::getText)
                    .as("By default, table names that differ only in case must be one table.")
                    .containsExactly("USERS");

            preferenceStore().setValue(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES, true);
            UiTestWorkspace.processEvents();

            assertThat(tabItems(editor)).extracting(CTabItem::getText)
                    .as("Case-sensitive table names must split the open editor's table in two.")
                    .containsExactly("USERS", "users");

            preferenceStore().setValue(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES, false);
            UiTestWorkspace.processEvents();

            assertThat(tabItems(editor)).extracting(CTabItem::getText)
                    .as("Case-insensitive table names must merge the open editor's tables again.")
                    .containsExactly("USERS");
        }
    }

    @Test
    void testPreferenceChange_ofAssumeColumnSensing_revalidatesTheOpenEditor() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            assertThat(problems(editor)).extracting(DatasetProblem::code)
                    .as("Without column sensing, a column missing from the first row must be reported.")
                    .contains(ProblemCode.COLUMN_NOT_IN_FIRST_ROW);

            preferenceStore().setValue(PreferenceKeys.ASSUME_COLUMN_SENSING, true);
            UiTestWorkspace.processEvents();

            assertThat(problems(editor)).extracting(DatasetProblem::code)
                    .as("With column sensing, dbUnit reads the column, so the editor must stop reporting it.")
                    .doesNotContain(ProblemCode.COLUMN_NOT_IN_FIRST_ROW);
        }
    }

    @Test
    void testPreferenceChange_ofNullDisplayText_repaintsTheGridsWithTheNewText() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final NatTable natTable = (NatTable) tabItems(editor).get(0).getControl();
            final List<ILayerEvent> events = new ArrayList<>();
            natTable.addLayerListener(events::add);

            preferenceStore().setValue(PreferenceKeys.NULL_DISPLAY_TEXT, "<NULL>");
            UiTestWorkspace.processEvents();

            assertThat(events).as("Changing the NULL display text must repaint the open grids.")
                    .anyMatch(VisualRefreshEvent.class::isInstance);
            final IDisplayConverter converter = natTable.getConfigRegistry().getConfigAttribute(
                    CellConfigAttributes.DISPLAY_CONVERTER, DisplayMode.NORMAL, GridRegion.BODY);
            assertThat(converter.canonicalToDisplayValue(null))
                    .as("The repainted grids must show NULL cells with the new text.").isEqualTo("<NULL>");
        }
    }

    @Test
    void testPreferenceChange_onABackgroundThread_regroupsTheTablesOnTheUiThread() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\"/><users ID=\"2\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            final Thread thread = new Thread(
                    () -> preferenceStore().setValue(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES, true));
            thread.start();
            thread.join();

            assertThat(editor.getDatasetDocument().getModel().getTables())
                    .as("A change on a background thread must not touch the editor on that thread.")
                    .hasSize(1);

            UiTestWorkspace.processEvents();

            assertThat(tabItems(editor)).extracting(CTabItem::getText)
                    .as("The UI thread must then regroup the open editor's tables.")
                    .containsExactly("USERS", "users");
        }
    }

    private static IPreferenceStore preferenceStore()
    {
        return DatasetUiPlugin.getDefault().getPreferenceStore();
    }

    private static List<CTabItem> tabItems(final FlatXmlDatasetEditor editor)
    {
        return List.of(editor.getTablesPage().getTabFolder().getItems());
    }

    private static List<DatasetProblem> problems(final FlatXmlDatasetEditor editor)
    {
        return editor.getDatasetDocument().getModel().getProblems();
    }
}
