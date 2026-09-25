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
package org.dbunit.eclipse.dataset.ui.preferences;

import org.dbunit.eclipse.dataset.ui.DatasetUiPlugin;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * The dbUnit Dataset Editor page of the workbench preferences: the NULL display text and the dataset
 * options that decide how the editor groups tables and validates them.
 *
 * @since 1.0.0
 */
public final class DatasetPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage
{
    /**
     * The identifier this page is registered under in {@code plugin.xml}.
     */
    public static final String ID = "org.dbunit.eclipse.dataset.ui.preferencePage";

    /**
     * Creates the page over the dataset editor's preference store.
     */
    public DatasetPreferencePage()
    {
        super(GRID);
        setPreferenceStore(DatasetUiPlugin.getDefault().getPreferenceStore());
        setDescription(Messages.PreferencePage_description);
    }

    /**
     * Does nothing; the page needs nothing from the workbench.
     *
     * @param workbench The workbench.
     */
    @Override
    public void init(final IWorkbench workbench)
    {
    }

    @Override
    protected void createFieldEditors()
    {
        final StringFieldEditor nullDisplayTextEditor = new StringFieldEditor(
                PreferenceKeys.NULL_DISPLAY_TEXT, Messages.PreferencePage_nullDisplayText,
                getFieldEditorParent());
        nullDisplayTextEditor.setEmptyStringAllowed(false);
        nullDisplayTextEditor.setErrorMessage(Messages.PreferencePage_nullDisplayTextBlank);
        addField(nullDisplayTextEditor);
        addField(new BooleanFieldEditor(PreferenceKeys.ASSUME_COLUMN_SENSING,
                Messages.PreferencePage_assumeColumnSensing, getFieldEditorParent()));
        addField(new BooleanFieldEditor(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES,
                Messages.PreferencePage_caseSensitiveTableNames, getFieldEditorParent()));
    }
}
