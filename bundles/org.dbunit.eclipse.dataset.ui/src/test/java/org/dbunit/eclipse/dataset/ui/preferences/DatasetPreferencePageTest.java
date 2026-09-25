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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.ui.DatasetUiPlugin;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.jface.preference.IPreferenceNode;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DatasetPreferencePage} through the controls a user sees.
 */
class DatasetPreferencePageTest
{
    private Shell shell;

    @BeforeEach
    void createShell()
    {
        shell = new Shell(Display.getDefault());
    }

    @AfterEach
    void disposeShellAndRestoreDefaults()
    {
        shell.dispose();
        final IPreferenceStore store = preferenceStore();
        store.setToDefault(PreferenceKeys.NULL_DISPLAY_TEXT);
        store.setToDefault(PreferenceKeys.ASSUME_COLUMN_SENSING);
        store.setToDefault(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES);
    }

    @Test
    void testCreatePage_ofTheRegisteredPreferenceNode_createsADatasetPreferencePage()
    {
        final IPreferenceNode node =
                PlatformUI.getWorkbench().getPreferenceManager().find(DatasetPreferencePage.ID);
        assertThat(node).as("The page must be registered in the workbench preferences.").isNotNull();

        node.createPage();
        try
        {
            assertThat(node.getPage()).as("The registered page must be the dataset preference page.")
                    .isInstanceOf(DatasetPreferencePage.class);
        }
        finally
        {
            node.disposeResources();
        }
    }

    @Test
    void testCreateControl_withStoredValues_showsEveryStoredValue()
    {
        final IPreferenceStore store = preferenceStore();
        store.setValue(PreferenceKeys.NULL_DISPLAY_TEXT, "<NULL>");
        store.setValue(PreferenceKeys.ASSUME_COLUMN_SENSING, true);
        store.setValue(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES, true);
        final DatasetPreferencePage page = new DatasetPreferencePage();

        page.createControl(shell);

        assertThat(nullDisplayTextField(page).getText())
                .as("The page must show the stored NULL display text.").isEqualTo("<NULL>");
        assertThat(checkBox(page, Messages.PreferencePage_assumeColumnSensing).getSelection())
                .as("The page must show the stored column sensing option.").isTrue();
        assertThat(checkBox(page, Messages.PreferencePage_caseSensitiveTableNames).getSelection())
                .as("The page must show the stored table name case option.").isTrue();
    }

    @Test
    void testPerformOk_afterEditingEveryField_storesTheEditedValues()
    {
        final DatasetPreferencePage page = new DatasetPreferencePage();
        page.createControl(shell);
        nullDisplayTextField(page).setText("NULL!");
        checkBox(page, Messages.PreferencePage_assumeColumnSensing).setSelection(true);
        checkBox(page, Messages.PreferencePage_caseSensitiveTableNames).setSelection(true);

        page.performOk();

        final IPreferenceStore store = preferenceStore();
        assertThat(store.getString(PreferenceKeys.NULL_DISPLAY_TEXT))
                .as("OK must store the edited NULL display text.").isEqualTo("NULL!");
        assertThat(store.getBoolean(PreferenceKeys.ASSUME_COLUMN_SENSING))
                .as("OK must store the edited column sensing option.").isTrue();
        assertThat(store.getBoolean(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES))
                .as("OK must store the edited table name case option.").isTrue();
    }

    @Test
    void testNullDisplayTextField_whenTypedBlank_makesThePageInvalidUntilTextIsTyped()
    {
        final DatasetPreferencePage page = new DatasetPreferencePage();
        page.createControl(shell);
        final Text field = nullDisplayTextField(page);

        type(field, "  ");

        assertThat(page.isValid()).as("A blank NULL display text must make the page invalid.").isFalse();
        assertThat(page.getErrorMessage())
                .as("The page must explain why a blank NULL display text is invalid.")
                .isEqualTo(Messages.PreferencePage_nullDisplayTextBlank);

        type(field, "-");

        assertThat(page.isValid()).as("A non-blank NULL display text must make the page valid again.")
                .isTrue();
    }

    private static IPreferenceStore preferenceStore()
    {
        return DatasetUiPlugin.getDefault().getPreferenceStore();
    }

    /**
     * Replaces the field's text and releases a key, as typing does, so the field editor validates it.
     */
    private static void type(final Text field, final String text)
    {
        field.setText(text);
        field.notifyListeners(SWT.KeyUp, new Event());
    }

    private static Text nullDisplayTextField(final DatasetPreferencePage page)
    {
        final List<Text> fields = new ArrayList<>();
        collect(page.getControl(), Text.class, fields);
        assertThat(fields).as("The page must have exactly one text field.").hasSize(1);
        return fields.get(0);
    }

    private static Button checkBox(final DatasetPreferencePage page, final String label)
    {
        final List<Button> buttons = new ArrayList<>();
        collect(page.getControl(), Button.class, buttons);
        for (final Button button : buttons)
        {
            if ((button.getStyle() & SWT.CHECK) != 0 && label.equals(button.getText()))
            {
                return button;
            }
        }
        throw new AssertionError("No check box labeled " + label + ".");
    }

    private static <T extends Control> void collect(final Control control, final Class<T> type,
            final List<T> found)
    {
        if (type.isInstance(control))
        {
            found.add(type.cast(control));
        }
        if (control instanceof final Composite composite)
        {
            for (final Control child : composite.getChildren())
            {
                collect(child, type, found);
            }
        }
    }
}
