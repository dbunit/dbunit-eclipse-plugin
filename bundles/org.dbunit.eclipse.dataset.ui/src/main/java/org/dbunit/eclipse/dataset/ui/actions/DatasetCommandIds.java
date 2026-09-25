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

    private DatasetCommandIds()
    {
    }
}
