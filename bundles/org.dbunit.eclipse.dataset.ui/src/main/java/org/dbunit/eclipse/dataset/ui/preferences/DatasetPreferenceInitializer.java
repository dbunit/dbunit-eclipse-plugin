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
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

/**
 * Sets the default values of the dataset editor's preferences.
 *
 * @since 1.0.0
 */
public final class DatasetPreferenceInitializer extends AbstractPreferenceInitializer
{
    @Override
    public void initializeDefaultPreferences()
    {
        final IPreferenceStore store = DatasetUiPlugin.getDefault().getPreferenceStore();
        store.setDefault(PreferenceKeys.NULL_DISPLAY_TEXT, "(null)");
        store.setDefault(PreferenceKeys.ASSUME_COLUMN_SENSING, false);
        store.setDefault(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES, false);
    }
}
