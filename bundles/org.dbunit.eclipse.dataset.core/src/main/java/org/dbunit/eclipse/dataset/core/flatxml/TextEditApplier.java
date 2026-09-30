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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;

/**
 * Applies the text edits of a dataset document to its text document, each operation's edits as one change,
 * and groups the changes of several operations into one step of the text document's undo history. Only the
 * nesting depth of the running batches is state: the text document is given with each call, so a dataset
 * document that is bound to another text document keeps a batch that is running.
 */
final class TextEditApplier
{
    private static final int JOIN_EDITS_THRESHOLD = 50;

    private int batchDepth;

    /**
     * Runs operations so that all the edits they apply form one compound change in the undo history of the
     * document. Batches nest: only the outermost one starts and ends the compound change.
     *
     * @param document The text document that the operations edit.
     * @param operations The operations to run.
     */
    void batch(final IDocument document, final Runnable operations)
    {
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        final boolean outermost = batchDepth == 0;
        batchDepth++;
        if (outermost && undoManager != null)
        {
            undoManager.beginCompoundChange();
        }
        try
        {
            operations.run();
        }
        finally
        {
            batchDepth--;
            if (batchDepth == 0 && undoManager != null)
            {
                undoManager.endCompoundChange();
            }
        }
    }

    /**
     * Applies edits to the document as one change of it, and, outside a batch, as one step of its undo
     * history.
     *
     * @param document The text document to change.
     * @param edits The edits to apply; they must not overlap and must fit the document.
     * @throws IllegalStateException When the edits overlap or do not fit the document.
     */
    void apply(final IDocument document, final List<TextEdit> edits)
    {
        final TextEdit change =
                edits.size() > JOIN_EDITS_THRESHOLD ? joinEdits(document, edits) : combineEdits(edits);
        final boolean outermost = batchDepth == 0;
        final IDocumentUndoManager undoManager =
                DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        if (outermost && undoManager != null)
        {
            undoManager.beginCompoundChange();
        }
        try
        {
            applyToDocument(document, change);
        }
        finally
        {
            if (outermost && undoManager != null)
            {
                undoManager.endCompoundChange();
            }
        }
    }

    private void applyToDocument(final IDocument document, final TextEdit change)
    {
        try
        {
            change.apply(document, TextEdit.NONE);
        }
        catch (final MalformedTreeException | BadLocationException e)
        {
            throw new IllegalStateException("Computed text edits do not fit the document.", e);
        }
    }

    private static MultiTextEdit combineEdits(final List<TextEdit> edits)
    {
        final MultiTextEdit root = new MultiTextEdit();
        for (final TextEdit edit : edits)
        {
            root.addChild(edit);
        }
        return root;
    }

    /**
     * Joins edits into one replacement of the text from the first edit to the last, which keeps the text
     * between the edits as it is. The document changes once, however many edits there are: each edit
     * that widens the text store's gap past its limit makes the store copy the whole text, so applying
     * many scattered edits one by one takes time in proportion to their number times the document's
     * length.
     *
     * @param document The text document that the edits apply to.
     * @param edits The edits to join; they must not overlap.
     * @return The replacement.
     */
    private ReplaceEdit joinEdits(final IDocument document, final List<TextEdit> edits)
    {
        final List<TextEdit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparingInt(TextEdit::getOffset));
        final int start = ordered.get(0).getOffset();
        final int end = ordered.get(ordered.size() - 1).getExclusiveEnd();
        final String original;
        try
        {
            original = document.get(start, end - start);
        }
        catch (final BadLocationException e)
        {
            throw new IllegalStateException("Computed text edits do not fit the document.", e);
        }
        final StringBuilder replacement = new StringBuilder(original.length());
        int position = start;
        for (final TextEdit edit : ordered)
        {
            if (edit.getOffset() < position)
            {
                throw new IllegalStateException("Computed text edits overlap.");
            }
            replacement.append(original, position - start, edit.getOffset() - start);
            replacement.append(newTextOf(edit));
            position = edit.getExclusiveEnd();
        }
        return new ReplaceEdit(start, end - start, replacement.toString());
    }

    private static String newTextOf(final TextEdit edit)
    {
        if (edit instanceof final ReplaceEdit replaceEdit)
        {
            return replaceEdit.getText();
        }
        if (edit instanceof final InsertEdit insertEdit)
        {
            return insertEdit.getText();
        }
        if (edit instanceof DeleteEdit)
        {
            return "";
        }
        throw new IllegalStateException("Unsupported text edit " + edit.getClass().getName() + ".");
    }
}
