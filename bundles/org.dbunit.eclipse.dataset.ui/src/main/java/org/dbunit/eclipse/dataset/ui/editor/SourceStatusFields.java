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

import java.util.List;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.StatusLineContributionItem;

/**
 * The status line fields of the Source page: whether the file is writable, the input mode, and the line and
 * column of the text cursor, which a text editor shows through the fields of its own action bar
 * contributor. The Source page is nested in the dataset editor, so the dataset editor's contributor adds
 * the fields to the status line, shows them for the Source page, and hides them for the Tables page, where
 * they mean nothing. It may be used on the UI thread only.
 */
final class SourceStatusFields
{
    private static final int FIND_FIELD_WIDTH_IN_CHARS = 40;

    private static final int ELEMENT_STATE_WIDTH_IN_CHARS = 20;

    private static final int POSITION_AND_MODE_WIDTH_IN_CHARS = 19;

    /**
     * A field of the status line.
     *
     * @param category The category that the text editor knows the field by.
     * @param actionId The id of the editor's action that a double click on the field runs, or null for none.
     * @param shownWithEditor True when the field is visible whenever the Source page is, false for a field
     *                        that the editor shows only while it needs it, such as the incremental find.
     * @param item The item that the status line shows.
     */
    private record Field(String category, String actionId, boolean shownWithEditor,
            StatusLineContributionItem item)
    {
    }

    private final List<Field> fields = List.of(
            field(ITextEditorActionConstants.STATUS_CATEGORY_FIND_FIELD, null, false,
                    FIND_FIELD_WIDTH_IN_CHARS),
            field(ITextEditorActionConstants.STATUS_CATEGORY_ELEMENT_STATE, null, true,
                    ELEMENT_STATE_WIDTH_IN_CHARS),
            field(ITextEditorActionConstants.STATUS_CATEGORY_INPUT_MODE,
                    ITextEditorActionDefinitionIds.TOGGLE_OVERWRITE, true, POSITION_AND_MODE_WIDTH_IN_CHARS),
            field(ITextEditorActionConstants.STATUS_CATEGORY_INPUT_POSITION,
                    ITextEditorActionConstants.GOTO_LINE, true, POSITION_AND_MODE_WIDTH_IN_CHARS));

    private IStatusLineManager statusLineManager;

    private FlatXmlSourceEditor shownEditor;

    /**
     * Adds the fields to a status line, where they stay hidden until an editor is shown.
     *
     * @param manager The manager of the status line that the fields belong to.
     */
    void contributeTo(final IStatusLineManager manager)
    {
        statusLineManager = manager;
        for (final Field field : fields)
        {
            manager.add(field.item());
        }
    }

    /**
     * Shows the fields for an editor, which keeps them up to date from now on, and stops the editor that
     * they were shown for before from doing so.
     *
     * @param editor The editor of the Source page that is shown.
     */
    void showFor(final FlatXmlSourceEditor editor)
    {
        detach();
        shownEditor = editor;
        for (final Field field : fields)
        {
            final StatusLineContributionItem item = field.item();
            item.setActionHandler(actionOf(editor, field.actionId()));
            editor.setStatusField(item, field.category());
            item.setVisible(field.shownWithEditor());
        }
        updateStatusLine();
    }

    /**
     * Hides the fields and stops the editor that they were shown for from keeping them up to date.
     */
    void hide()
    {
        detach();
        for (final Field field : fields)
        {
            field.item().setVisible(false);
        }
        updateStatusLine();
    }

    /**
     * Returns the item of a field, for a test to read what the field shows.
     *
     * @param category The category of the field, one of the {@code STATUS_CATEGORY} constants of
     *                 {@link ITextEditorActionConstants}.
     * @return The item of the field.
     */
    StatusLineContributionItem item(final String category)
    {
        for (final Field field : fields)
        {
            if (field.category().equals(category))
            {
                return field.item();
            }
        }
        throw new IllegalArgumentException("There is no status field of the category " + category + ".");
    }

    private void detach()
    {
        if (shownEditor == null)
        {
            return;
        }
        for (final Field field : fields)
        {
            shownEditor.setStatusField(null, field.category());
        }
        shownEditor = null;
    }

    private void updateStatusLine()
    {
        if (statusLineManager != null)
        {
            statusLineManager.update(true);
        }
    }

    private static IAction actionOf(final FlatXmlSourceEditor editor, final String actionId)
    {
        if (actionId == null)
        {
            return null;
        }
        return editor.getAction(actionId);
    }

    private static Field field(final String category, final String actionId, final boolean shownWithEditor,
            final int widthInChars)
    {
        final StatusLineContributionItem item = new StatusLineContributionItem(category, false, widthInChars);
        return new Field(category, actionId, shownWithEditor, item);
    }
}
