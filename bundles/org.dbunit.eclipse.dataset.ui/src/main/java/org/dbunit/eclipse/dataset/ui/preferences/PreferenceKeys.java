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

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.ui.DatasetUiPlugin;
import org.eclipse.jface.preference.IPreferenceStore;

/**
 * The keys of the dataset editor's preferences, stored in the UI bundle's instance-scope preference
 * store.
 *
 * @since 1.0.0
 */
public final class PreferenceKeys
{
    /**
     * The text shown for a NULL cell.
     */
    public static final String NULL_DISPLAY_TEXT = "nullDisplayText";

    /**
     * Whether to validate as if the tests enable dbUnit column sensing.
     */
    public static final String ASSUME_COLUMN_SENSING = "assumeColumnSensing";

    /**
     * Whether to group tables case-sensitively, as with dbUnit's {@code caseSensitiveTableNames}.
     */
    public static final String CASE_SENSITIVE_TABLE_NAMES = "caseSensitiveTableNames";

    private PreferenceKeys()
    {
    }

    /**
     * Builds the flat XML options that the current preference values describe.
     *
     * @return A {@link FlatXmlOptions} matching the current {@link #CASE_SENSITIVE_TABLE_NAMES} and
     *         {@link #ASSUME_COLUMN_SENSING} preference values.
     */
    public static FlatXmlOptions readOptions()
    {
        final IPreferenceStore store = DatasetUiPlugin.getDefault().getPreferenceStore();
        return new FlatXmlOptions(store.getBoolean(CASE_SENSITIVE_TABLE_NAMES),
                store.getBoolean(ASSUME_COLUMN_SENSING));
    }
}
