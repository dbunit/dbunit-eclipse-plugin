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
package org.dbunit.eclipse.dataset.ui.grid;

import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.eclipse.jface.action.IMenuManager;

/**
 * What a {@link DatasetGrid} needs from the page that hosts it.
 *
 * @since 1.0.0
 */
public interface DatasetGridContext
{
    /**
     * Returns the dataset the grid displays.
     *
     * @return The dataset document.
     */
    DatasetDocument getDatasetDocument();

    /**
     * Returns whether the grid may be edited.
     *
     * @return True when the source has no errors that block editing and the input is modifiable.
     */
    boolean isEditable();

    /**
     * Returns the text shown in place of a NULL cell's value.
     *
     * @return The configured NULL display text.
     */
    String getNullDisplayText();

    /**
     * Returns whether the editor is showing in a dark color theme.
     *
     * @return True when the relative luminance of the editor's background is below 0.5.
     */
    boolean isDarkTheme();

    /**
     * Runs an edit, reporting a rejected edit through the editor's status line.
     *
     * @param edit The edit to run.
     * @return True when the edit ran; false when the page is not editable, the editor input could not be
     *         validated, or the edit was rejected.
     */
    boolean executeEdit(Runnable edit);

    /**
     * Runs a multi-cell edit (paste, fill down, delete rows), reporting a rejected edit through a modal
     * error dialog instead of just the status line, since the user expects a larger effect.
     *
     * @param title The dialog's title, naming the command.
     * @param edit The edit to run.
     * @return True when the edit ran; false when the page is not editable, the editor input could not be
     *         validated, or the edit was rejected.
     */
    boolean executeMultiCellEdit(String title, Runnable edit);

    /**
     * Adds this page's actions for a grid region to a context menu.
     *
     * @param menu The menu to add to.
     * @param region One of NatTable's {@code GridRegion} constants naming where the menu was requested.
     */
    void fillContextMenu(IMenuManager menu, String region);

    /**
     * Returns whether a grid cell editor is currently open.
     *
     * @return True while a cell editor is active, so row, column, and table actions must do nothing.
     */
    boolean hasActiveCellEditor();

    /**
     * Returns the active grid's current selection.
     *
     * @return The selection snapshot, or {@link GridSelection#NONE} when there is no active grid.
     */
    GridSelection getSelection();

    /**
     * Requests the cell to select on the active grid the next time it refreshes.
     *
     * @param columnIndex The column of the cell to select.
     * @param rowIndex The row of the cell to select.
     */
    void setPendingSelection(int columnIndex, int rowIndex);
}
