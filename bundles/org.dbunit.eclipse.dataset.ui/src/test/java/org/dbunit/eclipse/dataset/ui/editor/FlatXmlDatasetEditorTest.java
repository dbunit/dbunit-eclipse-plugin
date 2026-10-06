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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.ui.DatasetUiPlugin;
import org.dbunit.eclipse.dataset.ui.preferences.PreferenceKeys;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.data.convert.IDisplayConverter;
import org.eclipse.nebula.widgets.nattable.edit.command.EditSelectionCommand;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.layer.event.ILayerEvent;
import org.eclipse.nebula.widgets.nattable.layer.event.VisualRefreshEvent;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlDatasetEditor} against the Editor Structure lifecycle rules and its reaction to
 * preference changes and to a file that changed outside the workbench. Some of the tests work end to end
 * through a real editor and show that the Tables page makes the DTD declarations of a dataset visible and
 * follows a DTD that changed.
 */
class FlatXmlDatasetEditorTest
{
    private static final int TABLES_PAGE_INDEX = 0;

    private static final int SOURCE_PAGE_INDEX = 1;

    private static final String SAVED_USERS = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";

    private static final String INVALID_VALUE = "a" + (char) 1 + "b";

    private static final String UTF8_DECLARED_USERS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";

    private static final String CHANGED_USERS = "<dataset><USERS ID=\"1\" NAME=\"Zed\"/></dataset>";

    private static final String OTHER_DATASET = "<dataset><ORDERS ID=\"1\"/></dataset>";

    @AfterEach
    void restoreDefaultPreferences()
    {
        final IPreferenceStore store = preferenceStore();
        store.setToDefault(PreferenceKeys.NULL_DISPLAY_TEXT);
        store.setToDefault(PreferenceKeys.ASSUME_COLUMN_SENSING);
        store.setToDefault(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES);
        final IEclipsePreferences resourcePreferences =
                InstanceScope.INSTANCE.getNode(ResourcesPlugin.PI_RESOURCES);
        resourcePreferences.remove(ResourcesPlugin.PREF_LIGHTWEIGHT_AUTO_REFRESH);
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
    void testCurrentCharset_whenTheDeclarationWasEditedAfterOpening_returnsTheCharsetThatItNamesNow()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", UTF8_DECLARED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            replaceInDocument(editor, "UTF-8", "ISO-8859-1");

            assertThat(editor.currentCharset())
                    .as("A save writes the text in the encoding that its declaration names when it is saved, "
                            + "not in the one that it named when the file was loaded.")
                    .isEqualTo(StandardCharsets.ISO_8859_1);
        }
    }

    @Test
    void testCurrentCharset_whenTheFileHasACharsetOfItsOwn_returnsItWhateverTheDeclarationNames()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", UTF8_DECLARED_USERS);
            file.setCharset("ISO-8859-1", null);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            assertThat(editor.currentCharset())
                    .as("A save writes a file that has a charset of its own in that charset, whatever its "
                            + "declaration names.")
                    .isEqualTo(StandardCharsets.ISO_8859_1);
        }
    }

    @Test
    void testSetCells_whenTheDeclarationWasEditedToAnEncodingThatLacksTheCharacter_writesAReferenceThatSaves()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            dialogDriver.expectNoDialog();
            final IFile file = workspace.createFile("dataset.xml", UTF8_DECLARED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            replaceInDocument(editor, "UTF-8", "ISO-8859-1");

            editor.getDatasetDocument().setCells("USERS", List.of(new CellChange(0, "NAME", "\u20AC")));
            UiTestWorkspace.processEvents();

            final String expectedText = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
                    + "<dataset><USERS ID=\"1\" NAME=\"&#x20AC;\"/></dataset>";
            assertThat(documentText(editor)).as("The euro sign is not in ISO-8859-1, so the grid edit must "
                    + "write it as a character reference.").isEqualTo(expectedText);
            editor.doSave(new NullProgressMonitor());
            UiTestWorkspace.processEvents();
            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("The save must not fail on a character that its encoding cannot hold.").isEmpty();
            assertThat(editor.isDirty()).as("Saving must clear the dirty state.").isFalse();
            try (InputStream contents = file.getContents())
            {
                assertThat(new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1))
                        .as("The saved file must hold the text.").isEqualTo(expectedText);
            }
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
    void testDoSave_whenACellEditorKeepsAValueThatFailedValidation_writesNothingAndCancelsTheMonitor()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            makeDirty(editor);
            openCellEditorHolding(editor, INVALID_VALUE);
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.CHANGE_BUTTON_OF_VALIDATION_DIALOG);
            final NullProgressMonitor monitor = new NullProgressMonitor();

            editor.doSave(monitor);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.hasHandledDialog()).as("The invalid value must be reported.").isTrue();
            assertThat(monitor.isCanceled())
                    .as("A save that cannot take the value of the open cell editor must be canceled, so that "
                            + "closing the editor stops as well.")
                    .isTrue();
            assertThat(fileText(file)).as("Nothing may be written.").isEqualTo(SAVED_USERS);
            assertThat(editor.isDirty()).as("The changes must stay unsaved.").isTrue();
            assertThat(editor.getTablesPage().hasActiveCellEditor())
                    .as("The cell editor stays open, so that the value can be changed.").isTrue();
        }
    }

    @Test
    void testDoSave_whenTheValueThatFailedValidationIsDiscarded_savesTheDocument() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            makeDirty(editor);
            openCellEditorHolding(editor, INVALID_VALUE);
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.DISCARD_BUTTON_OF_VALIDATION_DIALOG);
            final NullProgressMonitor monitor = new NullProgressMonitor();

            editor.doSave(monitor);
            UiTestWorkspace.processEvents();

            assertThat(monitor.isCanceled()).as("Nothing is left to hold the save back.").isFalse();
            assertThat(fileText(file)).as("The document without the discarded value must be written.")
                    .isEqualTo("<!--edited-->" + SAVED_USERS);
            assertThat(editor.isDirty()).as("Saving must clear the dirty state.").isFalse();
            assertThat(editor.getTablesPage().hasActiveCellEditor())
                    .as("Discarding the value closes the cell editor.").isFalse();
        }
    }

    @Test
    void testDoSaveAs_whenACellEditorKeepsAValueThatFailedValidation_asksForNoFile() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            makeDirty(editor);
            openCellEditorHolding(editor, INVALID_VALUE);
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.CHANGE_BUTTON_OF_VALIDATION_DIALOG);

            editor.doSaveAs();
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("Save As must not ask for a file while the value of the open cell editor is "
                            + "invalid.")
                    .isEmpty();
            assertThat(editor.getTablesPage().hasActiveCellEditor())
                    .as("The cell editor stays open, so that the value can be changed.").isTrue();
        }
    }

    @Test
    void testShowOnSourcePage_whenACellEditorKeepsAValueThatFailedValidation_staysOnTheTablesPage()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            openCellEditorHolding(editor, INVALID_VALUE);
            dialogDriver.pressButtonOfEveryDialog(MessageDialogDriver.CHANGE_BUTTON_OF_VALIDATION_DIALOG);

            editor.showOnSourcePage(0, 0);
            UiTestWorkspace.processEvents();

            assertThat(editor.getActivePage())
                    .as("The Tables page must stay, because leaving it would leave its cell editor open with "
                            + "a value that is not accepted.")
                    .isEqualTo(TABLES_PAGE_INDEX);
            assertThat(editor.getTablesPage().isActive())
                    .as("A page change that is refused leaves the Tables page the page that is active.")
                    .isTrue();
            assertThat(documentText(editor)).as("The value that is not accepted must not reach the document.")
                    .isEqualTo(SAVED_USERS);
            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("No dialog but the one about the invalid value may open.").isEmpty();
        }
    }

    @Test
    void testShowOnSourcePage_whenTheValueThatFailedValidationIsDiscarded_showsTheSourcePage()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            openCellEditorHolding(editor, INVALID_VALUE);
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.DISCARD_BUTTON_OF_VALIDATION_DIALOG);

            editor.showOnSourcePage(0, 0);
            UiTestWorkspace.processEvents();

            assertThat(editor.getActivePage()).as("With the value discarded, nothing holds the page back.")
                    .isEqualTo(SOURCE_PAGE_INDEX);
            assertThat(editor.getTablesPage().hasActiveCellEditor())
                    .as("Discarding the value closes the cell editor.").isFalse();
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
    void testOpen_whenFileIsBlank_opensOnTheTablesPage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("blank.xml", "");

            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openInDatasetEditor(file);

            assertThat(editor.getActivePage())
                    .as("A blank file has no error, and its Tables page offers to create the empty dataset.")
                    .isEqualTo(TABLES_PAGE_INDEX);
        }
    }

    @Test
    void testOpen_whenFileHasContentThatIsNotADataset_opensOnTheSourcePage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("other.xml", "<notdataset/>");

            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openInDatasetEditor(file);

            assertThat(editor.getActivePage())
                    .as("A file with content whose root is not a dataset must open on the Source page, "
                            + "unlike a blank one.")
                    .isEqualTo(SOURCE_PAGE_INDEX);
        }
    }

    @Test
    void testOpen_whenDoctypeSystemIdClimbsAboveTheWorkspaceRoot_opensOnTheTablesPage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<!DOCTYPE dataset SYSTEM \"../dataset.dtd\"><dataset><USERS ID=\"1\"/></dataset>");

            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            assertThat(editor.getActivePage())
                    .as("A DOCTYPE system ID that climbs above the workspace root must not keep the "
                            + "editor from opening on the Tables page.")
                    .isEqualTo(TABLES_PAGE_INDEX);
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
    void testFileDeleted_whenEditorIsDirty_keepsTheEditorOpen() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IEditorInput input = editor.getEditorInput();
            final IWorkbenchPage page =
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            makeDirty(editor);

            file.delete(true, null);
            UiTestWorkspace.processEvents();

            assertThat(page.findEditor(input))
                    .as("Deleting the file of an editor with unsaved changes must leave the editor open, "
                            + "so that the changes can still be saved.")
                    .isNotNull();
        }
    }

    @Test
    void testFileDeleted_whenEditorIsDirtyAndTheUserChoosesClose_closesTheEditorWithoutSaving()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IEditorInput input = editor.getEditorInput();
            final IWorkbenchPage page =
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            editor.getSourceEditor().checkExternalModification();
            workspace.open(workspace.createFile("other.xml", "<dataset><ORDERS ID=\"1\"/></dataset>"));
            makeDirty(editor);
            file.delete(true, null);
            UiTestWorkspace.processEvents();
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.CLOSE_BUTTON_OF_DELETED_FILE_DIALOG);

            page.activate(editor);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.hasHandledDialog())
                    .as("Activating an editor whose file was deleted must ask whether to save or close.")
                    .isTrue();
            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("Choosing Close must not lead to another dialog.").isEmpty();
            assertThat(page.findEditor(input)).as("Choosing Close must close the editor.").isNull();
            assertThat(file.exists()).as("Closing without saving must not bring the deleted file back.")
                    .isFalse();
        }
    }

    @Test
    void testFileChanged_whenEditorIsDirtyOnTheTablesPageAndTheUserChoosesReplace_replacesTheText()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IWorkbenchPage page = activePage();
            workspace.open(workspace.createFile("other.xml", OTHER_DATASET));
            makeDirty(editor);
            UiTestWorkspace.changeOnDisk(file, CHANGED_USERS);
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.REPLACE_BUTTON_OF_CHANGED_FILE_DIALOG);

            page.activate(editor);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.hasHandledDialog())
                    .as("Activating an editor with unsaved changes whose file changed on disk must ask "
                            + "whether to replace the text, although the Tables page is shown.")
                    .isTrue();
            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("Choosing Replace must not lead to another dialog.").isEmpty();
            assertThat(documentText(editor)).as("Replace must give the editor the text of the file.")
                    .isEqualTo(CHANGED_USERS);
            assertThat(editor.isDirty()).as("An editor with the text of its file is not dirty.").isFalse();
            assertThat(nameOfFirstUser(editor)).as("The grid must show the file's change.")
                    .isEqualTo("Zed");
        }
    }

    @Test
    void testFileChanged_whenEditorIsDirtyOnTheTablesPageAndTheUserChoosesIgnore_keepsTheTextAndAsksOnlyOnce()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IWorkbenchPage page = activePage();
            final IEditorPart other = workspace.open(workspace.createFile("other.xml", OTHER_DATASET));
            makeDirty(editor);
            final String unsavedText = documentText(editor);
            UiTestWorkspace.changeOnDisk(file, CHANGED_USERS);
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.IGNORE_BUTTON_OF_CHANGED_FILE_DIALOG);

            page.activate(editor);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.hasHandledDialog())
                    .as("Activating an editor with unsaved changes whose file changed on disk must ask "
                            + "whether to replace the text.")
                    .isTrue();
            assertThat(documentText(editor)).as("Ignoring the change must keep the unsaved text.")
                    .isEqualTo(unsavedText);
            assertThat(editor.isDirty()).as("Ignoring the change must keep the unsaved changes.").isTrue();

            dialogDriver.expectNoDialog();
            page.activate(other);
            page.activate(editor);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("An ignored change must not be asked about again at the next activation.").isEmpty();
        }
    }

    @Test
    void testFileChanged_whenEditorIsDirtyOnTheSourcePageAndTheUserChoosesReplace_asksOnceAndReplacesTheText()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IWorkbenchPage page = activePage();
            final IEditorPart other = workspace.open(workspace.createFile("other.xml", OTHER_DATASET));
            editor.showOnSourcePage(0, 0);
            page.activate(editor);
            UiTestWorkspace.processEvents();
            page.activate(other);
            makeDirty(editor);
            UiTestWorkspace.changeOnDisk(file, CHANGED_USERS);
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.REPLACE_BUTTON_OF_CHANGED_FILE_DIALOG);

            page.activate(editor);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.hasHandledDialog())
                    .as("Activating an editor with unsaved changes whose file changed on disk must ask "
                            + "whether to replace the text on the Source page too.")
                    .isTrue();
            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("The change must be asked about once, not by two checks.").isEmpty();
            assertThat(documentText(editor)).as("Replace must give the editor the text of the file.")
                    .isEqualTo(CHANGED_USERS);
        }
    }

    @Test
    void testFileChanged_whenEditorIsCleanAndItsFileIsOutsideTheWorkspace_asksAndReplacesTheText()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final Path file = workspace.createExternalFile(SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openExternalFile(file);
            final IWorkbenchPage page = activePage();
            workspace.open(workspace.createFile("other.xml", OTHER_DATASET));
            UiTestWorkspace.changeOnDisk(file, CHANGED_USERS);
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.REPLACE_BUTTON_OF_CHANGED_FILE_DIALOG);

            page.activate(editor);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.hasHandledDialog())
                    .as("The workspace does not refresh a file outside it, so activating its editor must "
                            + "ask whether to replace the text with the changed file's.")
                    .isTrue();
            assertThat(documentText(editor)).as("Replace must give the editor the text of the file.")
                    .isEqualTo(CHANGED_USERS);
            assertThat(nameOfFirstUser(editor)).as("The grid must show the file's change.")
                    .isEqualTo("Zed");
        }
    }

    @Test
    void testFileChanged_whenEditorIsCleanAndTheWorkspaceRefreshesItsFiles_doesNotAsk() throws Exception
    {
        setLightweightAutoRefresh(true);
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IWorkbenchPage page = activePage();
            workspace.open(workspace.createFile("other.xml", OTHER_DATASET));
            UiTestWorkspace.changeOnDisk(file, CHANGED_USERS);
            dialogDriver.expectNoDialog();

            page.activate(editor);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("The workspace reloads the clean editor of one of its files by itself, so "
                            + "activating the editor must not ask.")
                    .isEmpty();
        }
    }

    @Test
    void testFileChanged_whenEditorIsCleanAndTheWorkspaceDoesNotRefreshItsFiles_asksAndReplacesTheText()
            throws Exception
    {
        setLightweightAutoRefresh(false);
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IWorkbenchPage page = activePage();
            workspace.open(workspace.createFile("other.xml", OTHER_DATASET));
            UiTestWorkspace.changeOnDisk(file, CHANGED_USERS);
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.REPLACE_BUTTON_OF_CHANGED_FILE_DIALOG);

            page.activate(editor);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.hasHandledDialog())
                    .as("Nothing reloads the clean editor of a file that the workspace does not refresh, "
                            + "so activating the editor must ask whether to replace the text.")
                    .isTrue();
            assertThat(documentText(editor)).as("Replace must give the editor the text of the file.")
                    .isEqualTo(CHANGED_USERS);
        }
    }

    @Test
    void testGetAdapter_forAbstractTextEditorOnTheTablesPage_returnsTheSourceEditor() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            assertThat(editor.getActivePage()).as("An editable dataset must start on the Tables page.")
                    .isEqualTo(TABLES_PAGE_INDEX);

            final AbstractTextEditor textEditor = editor.getAdapter(AbstractTextEditor.class);

            assertThat(textEditor)
                    .as("The Source editor treats the part that answers with it as its own when it checks "
                            + "its file for changes, so the dataset editor must answer on every page.")
                    .isSameAs(editor.getSourceEditor());
        }
    }

    @Test
    void testGetAdapter_forIGotoMarker_leavesTheTablesPageActive() throws Exception
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
                    .as("Getting the IGotoMarker adapter must not switch pages by itself.")
                    .isEqualTo(TABLES_PAGE_INDEX);
        }
    }

    @Test
    void testSelectAndReveal_throughTheTextEditorAdapterOnTheTablesPage_showsTheSourcePageAndSelectsTheRange()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final ITextEditor textEditor = editor.getAdapter(ITextEditor.class);
            final int offset = SAVED_USERS.indexOf("USERS");

            textEditor.selectAndReveal(offset, "USERS".length());
            UiTestWorkspace.processEvents();

            assertThat(editor.getActivePage())
                    .as("A search result or a console link that selects text through the text editor "
                            + "adapter must show it, so the Source page must come forward.")
                    .isEqualTo(SOURCE_PAGE_INDEX);
            final ITextSelection selection =
                    (ITextSelection) editor.getSourceEditor().getSelectionProvider().getSelection();
            assertThat(selection.getText()).as("The requested range must be the selection.")
                    .isEqualTo("USERS");
        }
    }

    @Test
    void testShowSourcePage_beforeThePagesAreCreated_doesNothing()
    {
        final FlatXmlDatasetEditor editor = new FlatXmlDatasetEditor();

        assertThatCode(editor::showSourcePage)
                .as("Text that is selected while the editor is set up has no page to bring forward yet.")
                .doesNotThrowAnyException();
    }

    @Test
    void testSelectAndReveal_whenTheSourcePageIsActive_keepsItActive() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            editor.showOnSourcePage(0, 0);
            final ITextEditor textEditor = editor.getAdapter(ITextEditor.class);

            textEditor.selectAndReveal(1, 7);
            UiTestWorkspace.processEvents();

            assertThat(editor.getActivePage()).as("The Source page stays the active page.")
                    .isEqualTo(SOURCE_PAGE_INDEX);
        }
    }

    @Test
    void testGotoMarker_withAMarkerOnTheFile_activatesTheSourcePageAndSelectsItsRange() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset><USERS ID=\"1\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IGotoMarker gotoMarker = editor.getAdapter(IGotoMarker.class);
            final IMarker marker = file.createMarker(IMarker.TEXT);
            marker.setAttribute(IMarker.CHAR_START, 1);
            marker.setAttribute(IMarker.CHAR_END, 8);

            gotoMarker.gotoMarker(marker);
            UiTestWorkspace.processEvents();

            assertThat(editor.getActivePage()).as("Going to a marker must activate the Source page.")
                    .isEqualTo(SOURCE_PAGE_INDEX);
            final ITextSelection selection =
                    (ITextSelection) editor.getSourceEditor().getSelectionProvider().getSelection();
            assertThat(selection.getOffset()).as("Going to a marker must select its range's start.")
                    .isEqualTo(1);
            assertThat(selection.getLength()).as("Going to a marker must select its range's length.")
                    .isEqualTo(7);
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

    private static void makeDirty(final FlatXmlDatasetEditor editor) throws BadLocationException
    {
        final ITextEditor sourceEditor = editor.getSourceEditor();
        sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput()).replace(0, 0,
                "<!--edited-->");
        UiTestWorkspace.processEvents();
        assertThat(editor.isDirty()).as("A source edit must make the editor dirty.").isTrue();
    }

    private static void replaceInDocument(final FlatXmlDatasetEditor editor, final String oldText,
            final String newText) throws BadLocationException
    {
        final ITextEditor sourceEditor = editor.getSourceEditor();
        final IEditorInput input = sourceEditor.getEditorInput();
        final IDocument document = sourceEditor.getDocumentProvider().getDocument(input);
        final int offset = document.get().indexOf(oldText);
        document.replace(offset, oldText.length(), newText);
        UiTestWorkspace.processEvents();
    }

    private static void openCellEditorHolding(final FlatXmlDatasetEditor editor, final String value)
    {
        final TablesPage tablesPage = editor.getTablesPage();
        final Composite page = (Composite) tablesPage.getControl();
        page.setSize(800, 600);
        page.layout(true, true);
        UiTestWorkspace.processEvents();
        final NatTable natTable = (NatTable) tablesPage.getTabFolder().getSelection().getControl();
        tablesPage.selectRegion(1, 1, 1, 1);
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        natTable.getActiveCellEditor().setEditorValue(value);
    }

    private static String fileText(final IFile file) throws CoreException, IOException
    {
        try (InputStream contents = file.getContents())
        {
            return new String(contents.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void setLightweightAutoRefresh(final boolean enabled)
    {
        final IEclipsePreferences resourcePreferences =
                InstanceScope.INSTANCE.getNode(ResourcesPlugin.PI_RESOURCES);
        resourcePreferences.putBoolean(ResourcesPlugin.PREF_LIGHTWEIGHT_AUTO_REFRESH, enabled);
    }

    private static String documentText(final FlatXmlDatasetEditor editor)
    {
        final ITextEditor sourceEditor = editor.getSourceEditor();
        return sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput()).get();
    }

    private static String nameOfFirstUser(final FlatXmlDatasetEditor editor)
    {
        final DatasetTable users = editor.getDatasetDocument().getModel().getTables().get(0);
        final int nameColumn = users.getColumnIndex("NAME");
        return users.getEffectiveValue(0, nameColumn);
    }

    private static IWorkbenchPage activePage()
    {
        return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
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

    @Test
    void testTablesPage_withAnInternalDtdSubset_showsDeclaredColumnsInOrderAndADeclaredOnlyTable()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*,ORDERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n"
                            + "<!ELEMENT ORDERS EMPTY>\n<!ATTLIST ORDERS ID CDATA #REQUIRED>\n]>\n"
                            + "<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final DatasetModel model = editor.getDatasetDocument().getModel();

            assertThat(tablesPage.getTabFolder().getItemCount())
                    .as("Both the real and the declared-only table must get a tab.").isEqualTo(2);

            final DatasetTable usersTable = model.findTable("USERS").orElseThrow();
            assertThat(usersTable.getColumns()).as("Declared columns must appear in DTD order.")
                    .containsExactly(new DatasetColumn("ID", true, true, false),
                            new DatasetColumn("NAME", true, false, false));
            assertThat(usersTable.isDeclaredOnly())
                    .as("A table with an element in the document is not declared-only.").isFalse();

            final DatasetTable ordersTable = model.findTable("ORDERS").orElseThrow();
            assertThat(ordersTable.isDeclaredOnly())
                    .as("A table with no elements in the document must be declared-only.").isTrue();

            final CTabItem ordersTab = tabForTable(tablesPage, "ORDERS");
            assertThat(ordersTab.getToolTipText())
                    .as("A declared-only table's tab must explain why it has no rows.")
                    .isEqualTo("Declared in the DTD; no rows");
            assertThat(ordersTab.getFont().getFontData()[0].getStyle() & SWT.ITALIC)
                    .as("A declared-only table's tab must be italic.").isEqualTo(SWT.ITALIC);
        }
    }

    @Test
    void testHandleWindowActivated_whenAnotherWindowIsActivated_doesNotReloadTheDtd() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final FlatXmlDatasetEditor editor = openWithDtdThatThenGainsAColumn(workspace);

            editor.handleWindowActivated(anotherWindow());
            UiTestWorkspace.processEvents();

            assertThat(columnNamesOfUsers(editor))
                    .as("Another window that is activated must not make this editor read its files.")
                    .containsExactly("ID");
        }
    }

    @Test
    void testHandleWindowActivated_whenTheWindowOfTheEditorIsActivated_reloadsTheDtd() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final FlatXmlDatasetEditor editor = openWithDtdThatThenGainsAColumn(workspace);

            editor.handleWindowActivated(editor.getEditorSite().getWorkbenchWindow());
            UiTestWorkspace.processEvents();

            assertThat(columnNamesOfUsers(editor))
                    .as("The window of the editor that is activated must make it read the changed DTD.")
                    .containsExactly("ID", "NAME");
        }
    }

    /**
     * Opens a dataset whose external DTD declares the column ID, then changes the DTD on disk so that it
     * declares NAME too, without telling the editor.
     */
    private static FlatXmlDatasetEditor openWithDtdThatThenGainsAColumn(final UiTestWorkspace workspace)
            throws Exception
    {
        final IFile dtdFile = workspace.createFile("my.dtd", "<!ELEMENT dataset (USERS*)>\n"
                + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #REQUIRED>\n");
        final IFile datasetFile = workspace.createFile("dataset.xml",
                "<!DOCTYPE dataset SYSTEM \"my.dtd\">\n<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
        final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(datasetFile);
        dtdFile.setContents(
                new ByteArrayInputStream(("<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n")
                                .getBytes(StandardCharsets.UTF_8)),
                true, false, null);
        return editor;
    }

    private static List<String> columnNamesOfUsers(final FlatXmlDatasetEditor editor)
    {
        final DatasetTable users = editor.getDatasetDocument().getModel().findTable("USERS").orElseThrow();
        return users.getColumns().stream().map(DatasetColumn::name).toList();
    }

    /**
     * Returns a window that is not the one of any editor, for an editor to tell it from its own.
     */
    private static IWorkbenchWindow anotherWindow()
    {
        return (IWorkbenchWindow) Proxy.newProxyInstance(IWorkbenchWindow.class.getClassLoader(),
                new Class<?>[] { IWorkbenchWindow.class }, (proxy, method, arguments) -> null);
    }

    @Test
    void testTablesPage_afterReloadingAChangedExternalDtd_updatesTheColumns() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile dtdFile = workspace.createFile("my.dtd",
                    "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED>\n");
            final IFile datasetFile = workspace.createFile("dataset.xml",
                    "<!DOCTYPE dataset SYSTEM \"my.dtd\">\n<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(datasetFile);
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("Before the DTD changes, only the originally declared column must appear.")
                    .containsExactly(new DatasetColumn("ID", true, true, false));

            dtdFile.setContents(
                    new ByteArrayInputStream(("<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n")
                                    .getBytes(StandardCharsets.UTF_8)),
                    true, false, null);
            datasetDocument.reloadDtd();
            UiTestWorkspace.processEvents();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("Reloading a changed DTD must show the newly declared column.")
                    .containsExactly(new DatasetColumn("ID", true, true, false),
                            new DatasetColumn("NAME", true, false, false));
        }
    }

    @Test
    void testTablesPage_whenTheExternalDtdIsMissing_reportsDtdNotLoaded() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<!DOCTYPE dataset SYSTEM "
                    + "\"missing.dtd\">\n<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            final List<DatasetProblem> problems = editor.getDatasetDocument().getModel().getProblems();

            assertThat(problems).as("A missing external DTD must report DTD_NOT_LOADED.")
                    .anyMatch(problem -> problem.code() == ProblemCode.DTD_NOT_LOADED);
        }
    }

    private static CTabItem tabForTable(final TablesPage tablesPage, final String tableName)
    {
        for (final CTabItem item : tablesPage.getTabFolder().getItems())
        {
            if (item.getText().equals(tableName))
            {
                return item;
            }
        }
        throw new AssertionError("No tab named " + tableName + ".");
    }
}
