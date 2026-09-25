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

    /**
     * Remembers the dataset editor that became active, then installs the global action handlers of its
     * active page.
     *
     * @param part The editor that became active.
     */
    @Override
    public void setActiveEditor(final IEditorPart part)
    {
        multiPageEditor = part instanceof FlatXmlDatasetEditor ? (FlatXmlDatasetEditor) part : null;
        super.setActiveEditor(part);
    }

    /**
     * Installs the global action handlers of the active dataset editor's active page: the text editor's
     * actions for the Source page, and the Tables page's actions for the Tables page. The page comes from
     * the active dataset editor rather than from the argument, because every open dataset editor reports
     * its page changes to this contributor, which the editors share.
     *
     * @param activeEditor The nested editor of the page that became active, or null for the Tables page.
     */
    @Override
    public void setActivePage(final IEditorPart activeEditor)
    {
        if (multiPageEditor == null || multiPageEditor.getTablesPage() == null)
        {
            return;
        }
        final IActionBars actionBars = getActionBars();
        final boolean sourcePageActive = multiPageEditor.isSourcePageActive();
        final ITextEditor sourceEditor = multiPageEditor.getSourceEditor();
        final TablesPage tablesPage = multiPageEditor.getTablesPage();
        for (int index = 0; index < GLOBAL_ACTION_IDS.length; index++)
        {
            final String globalActionId = GLOBAL_ACTION_IDS[index];
            final IAction action;
            if (sourcePageActive)
            {
                action = sourceEditor.getAction(TEXT_EDITOR_ACTION_IDS[index]);
            }
            else if (ActionFactory.FIND.getId().equals(globalActionId))
            {
                action = null;
            }
            else
            {
                action = tablesPage.getGlobalActionHandler(globalActionId);
            }
            actionBars.setGlobalActionHandler(globalActionId, action);
        }
        actionBars.updateActionBars();
    }
}
