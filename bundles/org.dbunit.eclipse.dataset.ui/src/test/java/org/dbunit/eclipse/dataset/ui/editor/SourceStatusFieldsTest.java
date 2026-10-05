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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.dbunit.eclipse.dataset.ui.editor.StatusFieldProbe.ELEMENT_STATE;
import static org.dbunit.eclipse.dataset.ui.editor.StatusFieldProbe.INPUT_MODE;
import static org.dbunit.eclipse.dataset.ui.editor.StatusFieldProbe.INPUT_POSITION;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.texteditor.StatusLineContributionItem;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link SourceStatusFields} with the Source pages of real dataset editors, which fill the fields as
 * a text editor does.
 */
class SourceStatusFieldsTest
{
    private static final String DATASET =
            "<dataset>\n  <USERS ID=\"1\"/>\n  <ORDERS ID=\"2\"/>\n</dataset>\n";

    private static final int SECOND_LINE_START = DATASET.indexOf('\n') + 1;

    private static final int THIRD_LINE_START = DATASET.indexOf('\n', SECOND_LINE_START) + 1;

    private final SourceStatusFields fields = new SourceStatusFields();

    @Test
    void testContributeTo_whenCalled_addsTheTextEditorsFieldsInTheirOrder()
    {
        final StatusLineManager manager = new StatusLineManager();

        fields.contributeTo(manager);

        final List<String> fieldIds = Arrays.stream(manager.getItems())
                .filter(StatusLineContributionItem.class::isInstance).map(IContributionItem::getId).toList();
        assertThat(fieldIds)
                .as("The status line must get the four fields of a text editor, in the order of its own.")
                .containsExactlyElementsOf(StatusFieldProbe.CATEGORIES);
    }

    @Test
    void testContributeTo_whenCalled_keepsTheFieldsHiddenUntilAnEditorIsShown()
    {
        fields.contributeTo(new StatusLineManager());

        try (StatusFieldProbe probe = new StatusFieldProbe(fields))
        {
            assertThat(probe.visibleCategories()).as("No editor is shown yet, so no field may be visible.")
                    .isEmpty();
        }
    }

    @Test
    void testItem_whenTheCategoryIsUnknown_throwsAnException()
    {
        assertThatThrownBy(() -> fields.item("noSuchCategory"))
                .as("A category that no field has must be refused.")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("noSuchCategory");
    }

    @Test
    void testShowFor_whenAnEditorIsShown_showsAllFieldsButTheFindField() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                StatusFieldProbe probe = new StatusFieldProbe(fields))
        {
            fields.showFor(sourceEditorOf(workspace, "dataset.xml"));

            assertThat(probe.visibleCategories())
                    .as("The Source page's fields must be visible; the find field shows only during a find.")
                    .containsExactly(ELEMENT_STATE, INPUT_MODE, INPUT_POSITION);
        }
    }

    @Test
    void testShowFor_whenTheCaretMoves_showsItsLineAndColumn() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                StatusFieldProbe probe = new StatusFieldProbe(fields))
        {
            final FlatXmlSourceEditor sourceEditor = sourceEditorOf(workspace, "dataset.xml");
            fields.showFor(sourceEditor);

            sourceEditor.selectAndReveal(SECOND_LINE_START + 2, 0);
            final String afterTheFirstMove = probe.position();
            sourceEditor.selectAndReveal(0, 0);

            assertThat(List.of(afterTheFirstMove, probe.position()))
                    .as("The position field must show the caret's line and column, and follow the caret.")
                    .containsExactly("2:3", "1:1");
        }
    }

    @Test
    void testShowFor_whenAnEditorIsShown_showsTheInputMode() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                StatusFieldProbe probe = new StatusFieldProbe(fields))
        {
            fields.showFor(sourceEditorOf(workspace, "dataset.xml"));

            assertThat(probe.text(INPUT_MODE)).as("The input mode field must say how typing works.")
                    .isNotBlank();
        }
    }

    @Test
    void testShowFor_whenTheFileIsReadOnly_showsAnotherStateThanForAWritableFile() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                StatusFieldProbe probe = new StatusFieldProbe(fields))
        {
            fields.showFor(sourceEditorOf(workspace, "dataset.xml"));
            final String writableState = probe.text(ELEMENT_STATE);
            final FlatXmlDatasetEditor readOnlyEditor =
                    (FlatXmlDatasetEditor) workspace.openReadOnlyExternalFile(DATASET);

            fields.showFor(readOnlyEditor.getSourceEditor());

            assertThat(writableState).as("The state field must say that the file is writable.").isNotBlank();
            assertThat(probe.text(ELEMENT_STATE)).as("The state field must say that the file is read-only.")
                    .isNotBlank().isNotEqualTo(writableState);
        }
    }

    @Test
    void testShowFor_whenAnotherEditorIsShown_followsOnlyTheSecondEditor() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                StatusFieldProbe probe = new StatusFieldProbe(fields))
        {
            final FlatXmlSourceEditor first = sourceEditorOf(workspace, "first.xml");
            final FlatXmlSourceEditor second = sourceEditorOf(workspace, "second.xml");
            second.selectAndReveal(SECOND_LINE_START, 0);
            fields.showFor(first);
            fields.showFor(second);

            first.selectAndReveal(THIRD_LINE_START, 0);
            final String afterMovingTheFirst = probe.position();
            second.selectAndReveal(0, 0);

            assertThat(List.of(afterMovingTheFirst, probe.position()))
                    .as("The fields must follow the editor that they were shown for last, not the one before.")
                    .containsExactly("2:1", "1:1");
        }
    }

    @Test
    void testHide_afterAnEditorWasShown_hidesTheFieldsAndStopsFollowingTheEditor() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                StatusFieldProbe probe = new StatusFieldProbe(fields))
        {
            final FlatXmlSourceEditor sourceEditor = sourceEditorOf(workspace, "dataset.xml");
            fields.showFor(sourceEditor);
            sourceEditor.selectAndReveal(SECOND_LINE_START + 2, 0);
            final String positionWhileShown = probe.position();

            fields.hide();
            sourceEditor.selectAndReveal(0, 0);

            assertThat(probe.visibleCategories()).as("Hidden fields must not be visible.").isEmpty();
            assertThat(probe.position()).as("A hidden field must not follow the editor any more.")
                    .isEqualTo(positionWhileShown);
        }
    }

    @Test
    void testShowFor_afterTheFieldsWereHidden_showsThemAgainForTheEditor() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                StatusFieldProbe probe = new StatusFieldProbe(fields))
        {
            final FlatXmlSourceEditor sourceEditor = sourceEditorOf(workspace, "dataset.xml");
            fields.showFor(sourceEditor);
            fields.hide();
            sourceEditor.selectAndReveal(SECOND_LINE_START + 2, 0);

            fields.showFor(sourceEditor);

            assertThat(probe.visibleCategories()).as("The fields must be visible again.")
                    .containsExactly(ELEMENT_STATE, INPUT_MODE, INPUT_POSITION);
            assertThat(probe.position()).as("The fields must show the caret where it is now.")
                    .isEqualTo("2:3");
        }
    }

    @Test
    void testShowFor_whenTheStatusLineExists_putsTheVisibleFieldsInItAndHideTakesThemOut() throws Exception
    {
        final Shell shell = new Shell(Display.getDefault());
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final StatusLineManager manager = new StatusLineManager();
            final Composite statusLine = (Composite) manager.createControl(shell);
            fields.contributeTo(manager);
            final FlatXmlSourceEditor sourceEditor = sourceEditorOf(workspace, "dataset.xml");

            fields.showFor(sourceEditor);
            final long shownFields = fieldCount(statusLine);
            fields.hide();

            assertThat(List.of(shownFields, fieldCount(statusLine)))
                    .as("The status line must show the three visible fields, and none after they are hidden.")
                    .containsExactly(3L, 0L);
        }
        finally
        {
            shell.dispose();
        }
    }

    private static long fieldCount(final Composite statusLine)
    {
        return Arrays.stream(statusLine.getChildren()).map(Control::getData)
                .filter(StatusLineContributionItem.class::isInstance).distinct().count();
    }

    private static FlatXmlSourceEditor sourceEditorOf(final UiTestWorkspace workspace, final String fileName)
            throws Exception
    {
        final FlatXmlDatasetEditor editor =
                (FlatXmlDatasetEditor) workspace.open(workspace.createFile(fileName, DATASET));
        return editor.getSourceEditor();
    }
}
