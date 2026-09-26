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
package org.dbunit.eclipse.dataset.ui;

import org.eclipse.osgi.util.NLS;

/**
 * Externalized, user-visible strings of the dataset editor UI bundle.
 *
 * @since 1.0.0
 */
public final class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.dbunit.eclipse.dataset.ui.messages";

    /**
     * The title of the dialog shown when the Source page fails to open.
     */
    public static String Editor_openErrorTitle;

    /**
     * The title of the Tables page.
     */
    public static String Editor_tablesPageText;

    /**
     * The title of the Source page.
     */
    public static String Editor_sourcePageText;

    /**
     * The description at the top of the preference page.
     */
    public static String PreferencePage_description;

    /**
     * The label of the preference page's NULL display text field.
     */
    public static String PreferencePage_nullDisplayText;

    /**
     * The preference page's error message for a blank NULL display text.
     */
    public static String PreferencePage_nullDisplayTextBlank;

    /**
     * The label of the preference page's column sensing option.
     */
    public static String PreferencePage_assumeColumnSensing;

    /**
     * The label of the preference page's table name case option.
     */
    public static String PreferencePage_caseSensitiveTableNames;

    /**
     * The window title of the New dbUnit Flat XML Dataset wizard.
     */
    public static String NewDatasetWizard_windowTitle;

    /**
     * The title of the New dbUnit Flat XML Dataset wizard's page.
     */
    public static String NewDatasetWizard_pageTitle;

    /**
     * The description of the New dbUnit Flat XML Dataset wizard's page.
     */
    public static String NewDatasetWizard_pageDescription;

    /**
     * The title of the dialog shown when the new dataset file fails to open in an editor.
     */
    public static String NewDatasetWizard_openErrorTitle;

    /**
     * The label of the Insert Row Above command in menus.
     */
    public static String Action_insertRowAbove;

    /**
     * The label of the Insert Row Below command in menus and on the toolbar.
     */
    public static String Action_insertRowBelow;

    /**
     * The label of the Duplicate Rows command in menus.
     */
    public static String Action_duplicateRows;

    /**
     * The label of the Delete Rows command in menus and on the toolbar, and the title of its error dialog.
     */
    public static String Action_deleteRows;

    /**
     * The label of the Move Rows Up command in menus.
     */
    public static String Action_moveRowsUp;

    /**
     * The label of the Move Rows Down command in menus.
     */
    public static String Action_moveRowsDown;

    /**
     * The label of the Add Column command in menus and on the toolbar.
     */
    public static String Action_addColumn;

    /**
     * The label of the Rename Column command in menus.
     */
    public static String Action_renameColumn;

    /**
     * The label of the Delete Column command in menus and on the toolbar, and the title of its confirmation.
     */
    public static String Action_deleteColumn;

    /**
     * The label of the Add Table command in menus and on the toolbar.
     */
    public static String Action_addTable;

    /**
     * The label of the Rename Table command in menus.
     */
    public static String Action_renameTable;

    /**
     * The label of the Delete Table command in menus, and the title of its confirmation.
     */
    public static String Action_deleteTable;

    /**
     * The label of the Set to NULL command in menus.
     */
    public static String Action_setNull;

    /**
     * The label of the Set to Empty String command in menus.
     */
    public static String Action_setEmptyString;

    /**
     * The label of the Fill Down command in menus, and the title of its error dialog.
     */
    public static String Action_fillDown;

    /**
     * The label of the Edit Cell in Dialog command in menus.
     */
    public static String Action_editCellInDialog;

    /**
     * The label of the Show in Source command in menus.
     */
    public static String Action_showInSource;

    /**
     * The label of the Tables page's Cut action.
     */
    public static String Action_cut;

    /**
     * The label of the Tables page's Copy action.
     */
    public static String Action_copy;

    /**
     * The label of the Tables page's Paste action, and the title of its error dialog.
     */
    public static String Action_paste;

    /**
     * The label of the Tables page's Delete action.
     */
    public static String Action_delete;

    /**
     * The label of the Tables page's Select All action.
     */
    public static String Action_selectAll;

    /**
     * The label of the Tables page's Undo action.
     */
    public static String Action_undo;

    /**
     * The label of the Tables page's Redo action.
     */
    public static String Action_redo;

    /**
     * The title of the dialog that asks for a new column's name.
     */
    public static String ColumnDialog_addTitle;

    /**
     * The title of the dialog that asks for a column's new name.
     */
    public static String ColumnDialog_renameTitle;

    /**
     * The prompt for a column name.
     */
    public static String ColumnDialog_name;

    /**
     * The prompt for a new column's name in a table the DTD declares, with the reason to update the DTD.
     */
    public static String ColumnDialog_nameWithDtdWarning;

    /**
     * The confirmation of deleting a column with other than one value; {0} is the column, {1} the number of
     * values.
     */
    public static String DeleteColumnDialog_message;

    /**
     * The confirmation of deleting a column with one value; {0} is the column.
     */
    public static String DeleteColumnDialog_messageOneValue;

    /**
     * The note added to the confirmation of deleting a column that the DTD declares.
     */
    public static String DeleteColumnDialog_dtdNote;

    /**
     * The confirmation of deleting a table with other than one row; {0} is the table, {1} the number of rows.
     */
    public static String DeleteTableDialog_message;

    /**
     * The confirmation of deleting a table with one row; {0} is the table.
     */
    public static String DeleteTableDialog_messageOneRow;

    /**
     * The title of the dialog that asks for a new table's name and columns.
     */
    public static String TableDialog_addTitle;

    /**
     * The title of the dialog that asks for a table's new name.
     */
    public static String TableDialog_renameTitle;

    /**
     * The prompt for a table name.
     */
    public static String TableDialog_name;

    /**
     * The prompt for a new table's optional column names.
     */
    public static String TableDialog_columnNames;

    /**
     * The error for an empty table or column name.
     */
    public static String NameValidator_empty;

    /**
     * The error for a name that is not an XML name; {0} is the name.
     */
    public static String NameValidator_invalid;

    /**
     * The error for a name already in use; {0} is the name.
     */
    public static String NameValidator_used;

    /**
     * The status message for one pasted column beyond the table's last column; {0} is 1.
     */
    public static String Paste_ignoredColumn;

    /**
     * The status message for pasted columns beyond the table's last column; {0} is their number.
     */
    public static String Paste_ignoredColumns;

    /**
     * The Tables page banner's link to the problem's text on the Source page.
     */
    public static String ErrorBanner_showInSource;

    /**
     * The Tables page banner's message for a dataset that cannot be changed.
     */
    public static String ErrorBanner_readOnly;

    /**
     * The header of the Tables page's problems list; {0} is the number of problems.
     */
    public static String ProblemsSection_header;

    /**
     * The Tables page's message for an empty file.
     */
    public static String TablesPage_emptyFile;

    /**
     * The label of the Tables page's button that writes an empty dataset into an empty file.
     */
    public static String TablesPage_createEmptyDataset;

    /**
     * The Tables page's message for a dataset without tables.
     */
    public static String TablesPage_noTables;

    /**
     * The tooltip of a table's sheet tab; {0} is the row count text, {1} the column count text.
     */
    public static String TablesPage_tableTooltip;

    /**
     * The row count text of a sheet tab's tooltip for a table with one row.
     */
    public static String TablesPage_tableTooltipOneRow;

    /**
     * The row count text of a sheet tab's tooltip for a table with other than one row; {0} is the number of
     * rows.
     */
    public static String TablesPage_tableTooltipRows;

    /**
     * The column count text of a sheet tab's tooltip for a table with one column.
     */
    public static String TablesPage_tableTooltipOneColumn;

    /**
     * The column count text of a sheet tab's tooltip for a table with other than one column; {0} is the
     * number of columns.
     */
    public static String TablesPage_tableTooltipColumns;

    /**
     * The tooltip of the sheet tab of a table that only the DTD declares.
     */
    public static String TablesPage_declaredOnlyTableTooltip;

    /**
     * The column header tooltip of a column the DTD declares that has no values.
     */
    public static String ColumnHeaderTooltip_declaredWithoutValues;

    /**
     * The column header tooltip of a pending column.
     */
    public static String ColumnHeaderTooltip_pending;

    /**
     * The error for a cell value with a character XML does not allow; {0} is the character's code point.
     */
    public static String XmlCharacterValidator_invalidCharacter;

    static
    {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {
    }
}
