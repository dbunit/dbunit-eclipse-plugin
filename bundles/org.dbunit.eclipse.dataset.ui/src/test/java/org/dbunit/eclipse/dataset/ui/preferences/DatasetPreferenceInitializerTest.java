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

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.ui.DatasetUiPlugin;
import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DatasetPreferenceInitializer} against the documented default preference values.
 */
class DatasetPreferenceInitializerTest
{
    @Test
    void testInitializeDefaultPreferences_setsEveryDocumentedDefault()
    {
        new DatasetPreferenceInitializer().initializeDefaultPreferences();

        final IPreferenceStore store = DatasetUiPlugin.getDefault().getPreferenceStore();
        assertThat(store.getDefaultString(PreferenceKeys.NULL_DISPLAY_TEXT))
                .as("The NULL display text must default to \"(null)\".").isEqualTo("(null)");
        assertThat(store.getDefaultBoolean(PreferenceKeys.ASSUME_COLUMN_SENSING))
                .as("Assuming column sensing must default to false.").isFalse();
        assertThat(store.getDefaultBoolean(PreferenceKeys.CASE_SENSITIVE_TABLE_NAMES))
                .as("Case-sensitive table names must default to false.").isFalse();
    }

    @Test
    void testReadOptions_withDefaultPreferences_matchesDbUnitDefaults()
    {
        new DatasetPreferenceInitializer().initializeDefaultPreferences();

        assertThat(PreferenceKeys.readOptions())
                .as("The default preferences must build dbUnit's own default options.")
                .isEqualTo(FlatXmlOptions.DBUNIT_DEFAULTS);
    }
}
