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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.util.IPropertyChangeListener;

/**
 * The Tables page's Undo or Redo: runs the Source page's action on the history that both pages share, and
 * shows the label, the tool tip, and the enablement that the Source page's action has, so that both pages
 * name the operation in the same way. It does nothing while a cell editor is active. The model and the grids
 * catch up before the action returns, because a command that is already queued behind it would otherwise act
 * on the rows that the undo or redo removed.
 *
 * @since 1.0.0
 */
final class SharedHistoryAction extends Action
{
    private final IAction sourceAction;

    private final BooleanSupplier hasActiveCellEditor;

    private final Runnable refreshModel;

    private final IPropertyChangeListener sourceActionListener = event -> followSourceAction();

    /**
     * Creates an undo or a redo action that runs the Source page's own.
     *
     * @param sourceAction The Source page's undo or redo action, which acts on the history of the document
     *                     and tells when its label, its tool tip, or its enablement changes.
     * @param hasActiveCellEditor Returns true while a grid cell editor is open, so that the action declines
     *                            to run.
     * @param refreshModel Brings the model and the grids up to date with the document, and runs once the
     *                     history has changed it.
     */
    SharedHistoryAction(final IAction sourceAction, final BooleanSupplier hasActiveCellEditor,
            final Runnable refreshModel)
    {
        this.sourceAction = sourceAction;
        this.hasActiveCellEditor = hasActiveCellEditor;
        this.refreshModel = refreshModel;
        sourceAction.addPropertyChangeListener(sourceActionListener);
        followSourceAction();
    }

    @Override
    public void run()
    {
        if (hasActiveCellEditor.getAsBoolean())
        {
            return;
        }
        sourceAction.run();
        refreshModel.run();
    }

    /**
     * Stops following the Source page's action.
     */
    void dispose()
    {
        sourceAction.removePropertyChangeListener(sourceActionListener);
    }

    private void followSourceAction()
    {
        setText(sourceAction.getText());
        setToolTipText(sourceAction.getToolTipText());
        setEnabled(sourceAction.isEnabled());
    }
}
