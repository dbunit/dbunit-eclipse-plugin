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

import java.util.Set;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.MultiPageEditorActionBarContributor;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

/**
 * Routes the global Edit, Revert, and Print menu actions to whichever page of {@link FlatXmlDatasetEditor}
 * is active, and shows the status fields of the text editor while its Source page is.
 *
 * @since 1.0.0
 */
public final class DatasetEditorContributor extends MultiPageEditorActionBarContributor
{
    private static final String[] GLOBAL_ACTION_IDS = { ActionFactory.UNDO.getId(),
            ActionFactory.REDO.getId(), ActionFactory.CUT.getId(), ActionFactory.COPY.getId(),
            ActionFactory.PASTE.getId(), ActionFactory.DELETE.getId(), ActionFactory.SELECT_ALL.getId(),
            ActionFactory.FIND.getId(), ActionFactory.PRINT.getId(), ActionFactory.REVERT.getId() };

    private static final String[] TEXT_EDITOR_ACTION_IDS = { ITextEditorActionConstants.UNDO,
            ITextEditorActionConstants.REDO, ITextEditorActionConstants.CUT,
            ITextEditorActionConstants.COPY, ITextEditorActionConstants.PASTE,
            ITextEditorActionConstants.DELETE, ITextEditorActionConstants.SELECT_ALL,
            ITextEditorActionConstants.FIND, ITextEditorActionConstants.PRINT,
            ITextEditorActionConstants.REVERT };

    /**
     * The global actions that stay the text editor's on the Tables page, because they act on the document
     * that both pages share.
     */
    private static final Set<String> SHARED_DOCUMENT_ACTION_IDS = Set.of(ActionFactory.REVERT.getId());

    private final SourceStatusFields statusFields = new SourceStatusFields();

    private FlatXmlDatasetEditor multiPageEditor;

    /**
     * Returns the status fields of the Source page, for a test to read what they show.
     *
     * @return The status fields that this contributor shares among the dataset editors.
     */
    SourceStatusFields getStatusFields()
    {
        return statusFields;
    }

    /**
     * Remembers the dataset editor that became active, then installs the global action handlers of its
     * active page.
     *
     * @param part The editor that became active.
     */
    @Override
    public void setActiveEditor(final IEditorPart part)
    {
        multiPageEditor = part instanceof final FlatXmlDatasetEditor datasetEditor ? datasetEditor : null;
        super.setActiveEditor(part);
    }

    /**
     * Adds the status fields of the Source page to the status line. They stay hidden until the Source page
     * of a dataset editor is active.
     *
     * @param statusLineManager The manager of the status line of the dataset editors.
     */
    @Override
    public void contributeToStatusLine(final IStatusLineManager statusLineManager)
    {
        super.contributeToStatusLine(statusLineManager);
        statusFields.contributeTo(statusLineManager);
    }

    /**
     * Installs the global action handlers of the active dataset editor's active page: the text editor's
     * actions for the Source page, and the Tables page's actions for the Tables page, which has no Find or
     * Print action, and which reverts the document with the text editor's Revert action. The status fields
     * of the text editor are shown for the Source page, and hidden for the Tables page. The page comes from
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
        final boolean sourcePageActive = multiPageEditor.isSourcePageActive();
        installGlobalActionHandlers(multiPageEditor, sourcePageActive);
        if (sourcePageActive)
        {
            statusFields.showFor(multiPageEditor.getSourceEditor());
        }
        else
        {
            statusFields.hide();
        }
        getActionBars().updateActionBars();
    }

    private void installGlobalActionHandlers(final FlatXmlDatasetEditor datasetEditor,
            final boolean sourcePageActive)
    {
        final IActionBars actionBars = getActionBars();
        final ITextEditor sourceEditor = datasetEditor.getSourceEditor();
        final TablesPage tablesPage = datasetEditor.getTablesPage();
        for (int index = 0; index < GLOBAL_ACTION_IDS.length; index++)
        {
            final String globalActionId = GLOBAL_ACTION_IDS[index];
            final IAction action;
            if (sourcePageActive || SHARED_DOCUMENT_ACTION_IDS.contains(globalActionId))
            {
                action = sourceEditor.getAction(TEXT_EDITOR_ACTION_IDS[index]);
            }
            else
            {
                action = tablesPage.getGlobalActionHandler(globalActionId);
            }
            actionBars.setGlobalActionHandler(globalActionId, action);
        }
    }
}
