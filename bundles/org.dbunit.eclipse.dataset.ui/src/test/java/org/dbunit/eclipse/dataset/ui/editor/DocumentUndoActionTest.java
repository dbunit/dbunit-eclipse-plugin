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

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DocumentUndoAction} against the Undo and Redo specification's enablement and
 * active-cell-editor rules.
 */
class DocumentUndoActionTest
{
    private final List<String> textsAtRefresh = new ArrayList<>();

    private IDocument document;

    @BeforeEach
    void connectDocument()
    {
        document = new Document("Alice");
        DocumentUndoManagerRegistry.connect(document);
        DocumentUndoManagerRegistry.getDocumentUndoManager(document).connect(this);
    }

    @AfterEach
    void disconnectDocument()
    {
        DocumentUndoManagerRegistry.getDocumentUndoManager(document).disconnect(this);
        DocumentUndoManagerRegistry.disconnect(document);
    }

    @Test
    void testIsEnabled_beforeAnyEdit_followsUndoableAndRedoable()
    {
        final DocumentUndoAction undo = undoAction();
        final DocumentUndoAction redo = redoAction();

        assertThat(undo.isEnabled()).as("There is nothing to undo before any edit.").isFalse();
        assertThat(redo.isEnabled()).as("There is nothing to redo before any edit.").isFalse();
    }

    @Test
    void testRun_afterAnEdit_undoesItAndBecomesRedoable() throws BadLocationException
    {
        document.replace(0, 5, "Carol");
        final DocumentUndoAction undo = undoAction();

        assertThat(undo.isEnabled()).as("An edit must make undo enabled.").isTrue();
        undo.run();

        assertThat(document.get()).as("Undo must restore the previous text.").isEqualTo("Alice");

        final DocumentUndoAction redo = redoAction();
        assertThat(redo.isEnabled()).as("Undoing must make redo enabled.").isTrue();
        redo.run();

        assertThat(document.get()).as("Redo must reapply the edit.").isEqualTo("Carol");
    }

    @Test
    void testRun_whileACellEditorIsActive_changesNothing() throws BadLocationException
    {
        document.replace(0, 5, "Carol");
        final DocumentUndoAction undo =
                new DocumentUndoAction(() -> document, false, () -> true, this::recordRefresh);

        undo.run();

        assertThat(document.get()).as("Undo must do nothing while a cell editor is active.")
                .isEqualTo("Carol");
        assertThat(textsAtRefresh).as("Nothing changed, so there is nothing to refresh.").isEmpty();
    }

    @Test
    void testRun_afterAnEdit_refreshesTheModelAfterTheUndoAndAfterTheRedo() throws BadLocationException
    {
        document.replace(0, 5, "Carol");
        final DocumentUndoAction undo = undoAction();
        final DocumentUndoAction redo = redoAction();

        undo.run();
        redo.run();

        assertThat(textsAtRefresh)
                .as("The model must be refreshed once after each change, with the changed text already in "
                        + "the document.")
                .containsExactly("Alice", "Carol");
    }

    @Test
    void testRun_whenTheDocumentHasNoUndoHistory_doesNotRefresh()
    {
        final IDocument unconnected = new Document("Alice");
        final DocumentUndoAction undo =
                new DocumentUndoAction(() -> unconnected, false, () -> false, this::recordRefresh);

        undo.run();

        assertThat(textsAtRefresh).as("A document without a history has nothing to refresh after.").isEmpty();
    }

    private DocumentUndoAction undoAction()
    {
        return new DocumentUndoAction(() -> document, false, () -> false, this::recordRefresh);
    }

    private DocumentUndoAction redoAction()
    {
        return new DocumentUndoAction(() -> document, true, () -> false, this::recordRefresh);
    }

    private void recordRefresh()
    {
        textsAtRefresh.add(document.get());
    }
}
