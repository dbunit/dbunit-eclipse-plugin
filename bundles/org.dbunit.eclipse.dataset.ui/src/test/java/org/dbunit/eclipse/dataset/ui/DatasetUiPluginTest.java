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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Verifies the bundle starts inside a running workbench, which also proves the
 * OSGi UI test harness is wired correctly.
 */
class DatasetUiPluginTest
{
    @Test
    void testGetDefault_whenWorkbenchRunning_returnsActivatedPlugin()
    {
        final boolean workbenchRunning = PlatformUI.isWorkbenchRunning();
        final Bundle bundle = FrameworkUtil.getBundle(DatasetUiPlugin.class);
        final DatasetUiPlugin plugin = DatasetUiPlugin.getDefault();

        assertThat(workbenchRunning).as("UI tests must run inside a workbench.")
                .isTrue();
        assertThat(bundle.getSymbolicName()).as(
                "The activator must belong to the dataset UI bundle.")
                .isEqualTo(DatasetUiPlugin.PLUGIN_ID);
        assertThat(plugin).as("Loading the activator class must start the bundle.")
                .isNotNull();
    }
}
