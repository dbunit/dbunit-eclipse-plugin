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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.ui.source.XmlDocumentSetupParticipant;
import org.dbunit.eclipse.dataset.ui.source.XmlTokenColors;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.edit.command.EditSelectionCommand;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlSourceEditor}, the Source page of a real dataset editor: its syntax coloring, the
 * revert that the dataset editor offers on both pages, and how it closes the dataset editor.
 */
class FlatXmlSourceEditorTest
{
    private static final String CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- Users -->
            <dataset>
                <USERS ID="1"/>
            </dataset>
            """;

    private static final String SAVED_USERS =
            "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>";

    private static final String EDITED_USERS =
            "<dataset><USERS ID=\"1\" NAME=\"Carol\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>";

    @Test
    void testOpen_whenFileIsAFlatXmlDataset_colorsEachXmlConstructWithItsThemeColor() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", CONTENT);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final StyledText sourceText = sourceText(editor);
            final ColorRegistry colorRegistry = currentColorRegistry();

            final Map<String, String> colorIdsBySnippet = Map.of("<?xml", XmlTokenColors.DECLARATION_COLOR,
                    "<!--", XmlTokenColors.COMMENT_COLOR, "USERS", XmlTokenColors.TAG_COLOR, "ID",
                    XmlTokenColors.ATTRIBUTE_NAME_COLOR, "\"1\"", XmlTokenColors.ATTRIBUTE_VALUE_COLOR);

            final Map<String, RGB> colors = new LinkedHashMap<>();
            final Map<String, RGB> themeColors = new LinkedHashMap<>();
            colorIdsBySnippet.forEach((snippet, colorId) ->
            {
                colors.put(snippet, foregroundAt(sourceText, CONTENT.indexOf(snippet)));
                themeColors.put(snippet, colorRegistry.getRGB(colorId));
            });

            assertThat(colors).as("The Source page must color each XML construct with its theme color.")
                    .isEqualTo(themeColors);
        }
    }

    @Test
    void testThemeColorChange_whileTheEditorIsOpen_recolorsTheSource() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", CONTENT);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final StyledText sourceText = sourceText(editor);
            final ColorRegistry colorRegistry = currentColorRegistry();
            final RGB originalColor = colorRegistry.getRGB(XmlTokenColors.TAG_COLOR);
            final RGB changedColor = new RGB(1, 2, 3);
            try
            {
                colorRegistry.put(XmlTokenColors.TAG_COLOR, changedColor);
                UiTestWorkspace.processEvents();

                assertThat(foregroundAt(sourceText, CONTENT.indexOf("USERS")))
                        .as("A theme color change must recolor the open Source page.")
                        .isEqualTo(changedColor);
            }
            finally
            {
                colorRegistry.put(XmlTokenColors.TAG_COLOR, originalColor);
            }
        }
    }

    @Test
    void testOpen_whenInputIsAReadOnlyStorageInput_installsXmlPartitioning() throws Exception
    {
        final IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        final IEditorPart editor =
                page.openEditor(new StorageEditorInput(CONTENT), FlatXmlDatasetEditor.ID);
        try
        {
            UiTestWorkspace.processEvents();
            final FlatXmlSourceEditor sourceEditor = ((FlatXmlDatasetEditor) editor).getSourceEditor();
            final IDocumentExtension3 document = (IDocumentExtension3) sourceEditor.getDocumentProvider()
                    .getDocument(sourceEditor.getEditorInput());

            assertThat(document.getDocumentPartitioner(XmlDocumentSetupParticipant.PARTITIONING))
                    .as("A read-only storage input, such as a JAR entry or a history revision, must still "
                            + "get XML partitioning.")
                    .isNotNull();
        }
        finally
        {
            page.closeEditor(editor, false);
            UiTestWorkspace.processEvents();
        }
    }

    @Test
    void testRevertAction_afterAGridEditOnTheTablesPage_restoresTheSavedText() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IAction revert = installedRevertAction(editor);

            assertThat(revert).as("The Tables page must have a Revert action.").isNotNull();
            assertThat(revert.isEnabled()).as("A document without changes has nothing to revert.").isFalse();

            editor.getDatasetDocument().setCells("USERS", List.of(new CellChange(0, "NAME", "Carol")));
            UiTestWorkspace.processEvents();

            assertThat(revert.isEnabled()).as("A change made in the grid must enable Revert.").isTrue();

            revert.run();
            UiTestWorkspace.processEvents();

            assertThat(documentText(editor)).as("Revert must restore the text of the last save.")
                    .isEqualTo(SAVED_USERS);
            assertThat(editor.isDirty()).as("A reverted editor must not be dirty.").isFalse();
            assertThat(revert.isEnabled()).as("A reverted document has nothing left to revert.").isFalse();
        }
    }

    @Test
    void testRevertAction_whileTheTablesPageEditsACell_closesTheCellEditorWithoutWritingItsValue()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            editor.getDatasetDocument().setCells("USERS", List.of(new CellChange(0, "NAME", "Carol")));
            giveTablesPageASize(editor);
            final TablesPage tablesPage = editor.getTablesPage();
            final NatTable natTable = (NatTable) tablesPage.getTabFolder().getSelection().getControl();
            tablesPage.selectRegion(1, 1, 1, 1);
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            natTable.getActiveCellEditor().setEditorValue("Zed");

            installedRevertAction(editor).run();
            UiTestWorkspace.processEvents();

            assertThat(tablesPage.hasActiveCellEditor())
                    .as("A revert must close the cell editor, or its value lands in the reverted document.")
                    .isFalse();
            natTable.commitAndCloseActiveCellEditor();
            UiTestWorkspace.processEvents();
            assertThat(documentText(editor)).as("The value of the closed cell editor must not be written.")
                    .isEqualTo(SAVED_USERS);
        }
    }

    @Test
    void testClose_whenNotSaving_closesTheDatasetEditorThatHoldsTheSourcePage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IEditorInput input = editor.getEditorInput();

            editor.getSourceEditor().close(false);

            assertThat(activePage().findEditor(input))
                    .as("The editor must still be open when the call returns, as it is after the close of "
                            + "any text editor, because the code that asked for it goes on using the editor.")
                    .isNotNull();

            UiTestWorkspace.processEvents();

            assertThat(activePage().findEditor(input))
                    .as("Closing the Source page must close the dataset editor that holds it, because the "
                            + "workbench page does not know the page of an editor by itself.")
                    .isNull();
        }
    }

    @Test
    void testClose_whenNotSavingAndTheEditorIsDirty_closesItAndLeavesTheFileAsSaved() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IEditorInput input = editor.getEditorInput();
            editor.getDatasetDocument().setCells("USERS", List.of(new CellChange(0, "NAME", "Carol")));
            UiTestWorkspace.processEvents();
            assertThat(editor.isDirty()).as("A grid edit must make the editor dirty.").isTrue();
            dialogDriver.expectNoDialog();

            editor.getSourceEditor().close(false);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("Closing without saving must not ask whether to save.").isEmpty();
            assertThat(activePage().findEditor(input)).as("Closing without saving must close the editor.")
                    .isNull();
            assertThat(fileText(file)).as("Closing without saving must leave the file as it was saved.")
                    .isEqualTo(SAVED_USERS);
        }
    }

    @Test
    void testClose_whenSavingAndTheEditorIsDirty_savesTheChangesAndCloses() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IEditorInput input = editor.getEditorInput();
            editor.getDatasetDocument().setCells("USERS", List.of(new CellChange(0, "NAME", "Carol")));
            UiTestWorkspace.processEvents();
            dialogDriver.pressButtonOfNextDialog(MessageDialogDriver.SAVE_BUTTON_OF_CLOSE_PROMPT);

            editor.getSourceEditor().close(true);
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.hasHandledDialog())
                    .as("Closing a dirty editor with its changes to be saved must ask whether to save them.")
                    .isTrue();
            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("The prompt to save must be the only dialog that opens.").isEmpty();
            assertThat(fileText(file)).as("Choosing Save must write the changes before the editor closes.")
                    .isEqualTo(EDITED_USERS);
            assertThat(activePage().findEditor(input)).as("The editor must close once it is saved.")
                    .isNull();
        }
    }

    @Test
    void testClose_whenAskedAgainBeforeTheEditorCloses_closesTheEditorOnceWithoutAnError() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                LogRecorder log = new LogRecorder(PlatformUI.class))
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IEditorInput input = editor.getEditorInput();
            final FlatXmlSourceEditor sourceEditor = editor.getSourceEditor();

            sourceEditor.close(false);
            sourceEditor.close(false);
            UiTestWorkspace.processEvents();

            assertThat(activePage().findEditor(input)).as("The editor must close.").isNull();
            assertThat(log.statuses()).filteredOn(status -> status.matches(IStatus.ERROR))
                    .extracting(IStatus::getMessage)
                    .as("A second request to close an editor that has already closed must be harmless, "
                            + "not an exception that the workbench reports.")
                    .isEmpty();
        }
    }

    @Test
    void testClose_whenTheFileIsDeletedBeforeTheEditorCloses_doesNotAskAboutTheFile() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                MessageDialogDriver dialogDriver = new MessageDialogDriver())
        {
            final IFile file = workspace.createFile("dataset.xml", SAVED_USERS);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final IEditorInput input = editor.getEditorInput();
            final FlatXmlSourceEditor sourceEditor = editor.getSourceEditor();
            sourceEditor.checkExternalModification();
            editor.getDatasetDocument().setCells("USERS", List.of(new CellChange(0, "NAME", "Carol")));
            UiTestWorkspace.processEvents();
            dialogDriver.expectNoDialog();

            sourceEditor.close(false);
            Files.delete(file.getLocation().toFile().toPath());
            sourceEditor.checkExternalModification();
            UiTestWorkspace.processEvents();

            assertThat(dialogDriver.unexpectedDialogTitles())
                    .as("An editor that is about to close must not ask what to do about its file.").isEmpty();
            assertThat(activePage().findEditor(input)).as("The editor must still close.").isNull();
        }
    }

    /**
     * Gives the grid a size, which an editor in the test workbench lacks while the intro hides its shell,
     * so that NatTable can place the editor of a cell.
     */
    private static void giveTablesPageASize(final FlatXmlDatasetEditor editor)
    {
        final Composite page = (Composite) editor.getTablesPage().getControl();
        page.setSize(800, 600);
        page.layout(true, true);
        UiTestWorkspace.processEvents();
    }

    private static IAction installedRevertAction(final FlatXmlDatasetEditor editor)
    {
        return editor.getEditorSite().getActionBars().getGlobalActionHandler(ActionFactory.REVERT.getId());
    }

    private static String documentText(final FlatXmlDatasetEditor editor)
    {
        final FlatXmlSourceEditor sourceEditor = editor.getSourceEditor();
        return sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput()).get();
    }

    private static String fileText(final IFile file) throws Exception
    {
        try (InputStream contents = file.getContents())
        {
            return new String(contents.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static IWorkbenchPage activePage()
    {
        return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    }

    private static StyledText sourceText(final FlatXmlDatasetEditor editor)
    {
        return (StyledText) editor.getSourceEditor().getAdapter(Control.class);
    }

    private static ColorRegistry currentColorRegistry()
    {
        return PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry();
    }

    private static RGB foregroundAt(final StyledText sourceText, final int offset)
    {
        final StyleRange styleRange = sourceText.getStyleRangeAtOffset(offset);
        if (styleRange == null || styleRange.foreground == null)
        {
            return null;
        }
        return styleRange.foreground.getRGB();
    }

    /**
     * A read-only, non-file input, as for a dataset stored inside a JAR or held by a history revision:
     * neither is backed by a workspace {@link IFile} or a file store, so the file buffer manager never
     * connects to it.
     */
    private static final class StorageEditorInput implements IStorageEditorInput
    {
        private final String content;

        StorageEditorInput(final String content)
        {
            this.content = content;
        }

        @Override
        public IStorage getStorage()
        {
            return new IStorage()
            {
                @Override
                public InputStream getContents()
                {
                    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public IPath getFullPath()
                {
                    return null;
                }

                @Override
                public String getName()
                {
                    return "dataset.xml";
                }

                @Override
                public boolean isReadOnly()
                {
                    return true;
                }

                @Override
                public <T> T getAdapter(final Class<T> adapter)
                {
                    return null;
                }
            };
        }

        @Override
        public boolean exists()
        {
            return true;
        }

        @Override
        public ImageDescriptor getImageDescriptor()
        {
            return null;
        }

        @Override
        public String getName()
        {
            return "dataset.xml";
        }

        @Override
        public IPersistableElement getPersistable()
        {
            return null;
        }

        @Override
        public String getToolTipText()
        {
            return "dataset.xml";
        }

        @Override
        public <T> T getAdapter(final Class<T> adapter)
        {
            return null;
        }
    }
}
