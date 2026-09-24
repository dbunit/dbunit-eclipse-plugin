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

import org.eclipse.jface.action.IAction;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.MultiPageEditorActionBarContributor;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

/**
 * Routes the global Edit menu actions to whichever page of {@link FlatXmlDatasetEditor} is active.
 *
 * @since 1.0.0
 */
public final class DatasetEditorContributor extends MultiPageEditorActionBarContributor
{
    private static final String[] GLOBAL_ACTION_IDS = { ActionFactory.UNDO.getId(),
            ActionFactory.REDO.getId(), ActionFactory.CUT.getId(), ActionFactory.COPY.getId(),
            ActionFactory.PASTE.getId(), ActionFactory.DELETE.getId(), ActionFactory.SELECT_ALL.getId(),
            ActionFactory.FIND.getId() };

    private static final String[] TEXT_EDITOR_ACTION_IDS = { ITextEditorActionConstants.UNDO,
            ITextEditorActionConstants.REDO, ITextEditorActionConstants.CUT,
            ITextEditorActionConstants.COPY, ITextEditorActionConstants.PASTE,
            ITextEditorActionConstants.DELETE, ITextEditorActionConstants.SELECT_ALL,
            ITextEditorActionConstants.FIND };

    private FlatXmlDatasetEditor multiPageEditor;

    @Override
    public void setActiveEditor(final IEditorPart part)
    {
        super.setActiveEditor(part);
        multiPageEditor = (FlatXmlDatasetEditor) part;
    }

    @Override
    public void setActivePage(final IEditorPart activeEditor)
    {
        final IActionBars actionBars = getActionBars();
        for (int index = 0; index < GLOBAL_ACTION_IDS.length; index++)
        {
            final IAction action = activeEditor instanceof ITextEditor
                    ? ((ITextEditor) activeEditor).getAction(TEXT_EDITOR_ACTION_IDS[index]) : null;
            actionBars.setGlobalActionHandler(GLOBAL_ACTION_IDS[index], action);
        }
        actionBars.updateActionBars();
    }
}
