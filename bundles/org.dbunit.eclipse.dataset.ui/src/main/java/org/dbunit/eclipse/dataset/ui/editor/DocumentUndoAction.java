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

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;

/**
 * Undoes or redoes a document's shared undo history, so the Tables page and the Source page act on the
 * same history; does nothing while a cell editor is active.
 *
 * @since 1.0.0
 */
final class DocumentUndoAction extends Action
{
    private static final ILog LOG = ILog.of(DocumentUndoAction.class);

    private final Supplier<IDocument> document;

    private final boolean redo;

    private final BooleanSupplier hasActiveCellEditor;

    /**
     * Creates an undo or a redo action for the editor's current document.
     *
     * @param document Returns the document whose shared undo history to act on, which changes when the
     *                 editor's input does.
     * @param redo True for redo, false for undo.
     * @param hasActiveCellEditor Returns true while a grid cell editor is open, so the action can decline
     *                            to run.
     */
    DocumentUndoAction(final Supplier<IDocument> document, final boolean redo,
            final BooleanSupplier hasActiveCellEditor)
    {
        super(redo ? "Redo" : "Undo");
        this.document = document;
        this.redo = redo;
        this.hasActiveCellEditor = hasActiveCellEditor;
        update();
    }

    @Override
    public void run()
    {
        if (hasActiveCellEditor.getAsBoolean())
        {
            return;
        }
        final IDocumentUndoManager manager =
                DocumentUndoManagerRegistry.getDocumentUndoManager(document.get());
        if (manager == null)
        {
            return;
        }
        try
        {
            if (redo)
            {
                manager.redo();
            }
            else
            {
                manager.undo();
            }
        }
        catch (final ExecutionException e)
        {
            LOG.error((redo ? "Redo" : "Undo") + " failed.", e);
        }
    }

    /**
     * Refreshes enablement from the document's current undo history.
     */
    void update()
    {
        final IDocumentUndoManager manager =
                DocumentUndoManagerRegistry.getDocumentUndoManager(document.get());
        final boolean enabled = manager != null && (redo ? manager.redoable() : manager.undoable());
        setEnabled(enabled);
    }
}
