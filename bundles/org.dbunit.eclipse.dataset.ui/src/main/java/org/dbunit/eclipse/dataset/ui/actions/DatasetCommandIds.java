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
package org.dbunit.eclipse.dataset.ui.actions;

/**
 * The identifiers of the commands the Tables page contributes, as declared in {@code plugin.xml}.
 *
 * @since 1.0.0
 */
public final class DatasetCommandIds
{
    /**
     * The prefix every command id in this plugin starts with.
     */
    public static final String PREFIX = "org.dbunit.eclipse.dataset.ui.";

    /**
     * Inserts a row above the selection anchor.
     */
    public static final String INSERT_ROW_ABOVE = PREFIX + "insertRowAbove";

    /**
     * Inserts a row below the selection anchor.
     */
    public static final String INSERT_ROW_BELOW = PREFIX + "insertRowBelow";

    /**
     * Deletes the selected rows.
     */
    public static final String DELETE_ROWS = PREFIX + "deleteRows";

    /**
     * Inserts copies of the selected rows directly after them.
     */
    public static final String DUPLICATE_ROWS = PREFIX + "duplicateRows";

    /**
     * Moves the selected rows up by one position.
     */
    public static final String MOVE_ROWS_UP = PREFIX + "moveRowsUp";

    /**
     * Moves the selected rows down by one position.
     */
    public static final String MOVE_ROWS_DOWN = PREFIX + "moveRowsDown";

    /**
     * Adds a pending column to the active table.
     */
    public static final String ADD_COLUMN = PREFIX + "addColumn";

    /**
     * Renames the anchor column.
     */
    public static final String RENAME_COLUMN = PREFIX + "renameColumn";

    /**
     * Deletes the anchor column.
     */
    public static final String DELETE_COLUMN = PREFIX + "deleteColumn";

    /**
     * Appends a new table.
     */
    public static final String ADD_TABLE = PREFIX + "addTable";

    /**
     * Renames the active table.
     */
    public static final String RENAME_TABLE = PREFIX + "renameTable";

    /**
     * Deletes the active table.
     */
    public static final String DELETE_TABLE = PREFIX + "deleteTable";

    /**
     * Sets the selected cells to NULL.
     */
    public static final String SET_NULL = PREFIX + "setNull";

    /**
     * Sets the selected cells to the empty string.
     */
    public static final String SET_EMPTY_STRING = PREFIX + "setEmptyString";

    /**
     * Copies the top row of the selection down into the other selected rows.
     */
    public static final String FILL_DOWN = PREFIX + "fillDown";

    /**
     * Opens the anchor cell's value in a multi-line dialog editor.
     */
    public static final String EDIT_CELL_IN_DIALOG = PREFIX + "editCellInDialog";

    /**
     * Selects and reveals the anchor cell's range on the Source page, and switches to it.
     */
    public static final String SHOW_IN_SOURCE = PREFIX + "showInSource";

    private DatasetCommandIds()
    {
    }
}
