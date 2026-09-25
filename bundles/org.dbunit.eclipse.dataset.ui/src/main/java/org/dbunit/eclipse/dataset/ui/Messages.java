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

    static
    {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {
    }
}
