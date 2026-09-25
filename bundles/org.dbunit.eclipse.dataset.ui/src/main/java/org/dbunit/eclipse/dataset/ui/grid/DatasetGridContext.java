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

import java.util.List;

import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

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
     * Selects a block of cells of the active grid, clamped to its table, moves the selection anchor to the
     * block's first cell, and scrolls that cell into view. An operation calls it after its edit, which has
     * then already refreshed the grid.
     *
     * @param firstColumnIndex The column of the block's first cell.
     * @param firstRowIndex The row of the block's first cell.
     * @param columnCount The number of columns to select.
     * @param rowCount The number of rows to select.
     */
    void selectRegion(int firstColumnIndex, int firstRowIndex, int columnCount, int rowCount);

    /**
     * Returns the shell to parent dialogs on.
     *
     * @return The page's shell.
     */
    Shell getShell();

    /**
     * Tells the page that the table currently keyed {@code oldKey} is about to become {@code newKey}, so
     * the next reconciliation keeps its tab instead of disposing and recreating it.
     *
     * @param oldKey The table's key before the rename.
     * @param newKey The table's key after the rename.
     */
    void expectRename(String oldKey, String newKey);

    /**
     * Tells the page that a table with this key is about to be added, so the next reconciliation selects
     * its new tab.
     *
     * @param tableKey The key of the table about to be added.
     */
    void expectNewTableSelected(String tableKey);

    /**
     * Returns the active cell editor's text control.
     *
     * @return The control, or null when no cell editor is active or its editor is not backed by a
     *         {@code Text} control.
     */
    Text getActiveCellEditorText();

    /**
     * Returns the exact cells the active grid has selected, which may not form a full rectangle.
     *
     * @return The selected cell positions, as {@code (columnIndex, rowIndex)} points.
     */
    List<Point> getSelectedCellPositions();

    /**
     * Selects every cell of the active grid.
     */
    void selectAll();

    /**
     * Opens the anchor cell's value in a multi-line dialog editor.
     */
    void editCellInDialog();

    /**
     * Selects and reveals the anchor cell's range on the Source page, and switches to it.
     */
    void showInSource();

    /**
     * Shows an informational message on the editor's status line.
     *
     * @param message The message to show, or null to clear it.
     */
    void setStatusMessage(String message);

    /**
     * Writes text to the system clipboard.
     *
     * @param text The text to write.
     */
    void writeClipboardText(String text);

    /**
     * Reads text from the system clipboard.
     *
     * @return The clipboard's text, or null when it holds no text.
     */
    String readClipboardText();
}
