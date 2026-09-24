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

import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator of the dataset editor UI bundle.
 *
 * @since 1.0.0
 */
public final class DatasetUiPlugin extends AbstractUIPlugin
{
    /**
     * The symbolic name of the dataset editor UI bundle.
     */
    public static final String PLUGIN_ID = "org.dbunit.eclipse.dataset.ui";

    private static DatasetUiPlugin plugin;

    /**
     * Returns the shared instance, which exists while the bundle is active.
     *
     * @return The shared instance, or null when the bundle is not active.
     */
    public static DatasetUiPlugin getDefault()
    {
        return plugin;
    }

    /**
     * Starts the bundle and makes this instance the shared instance.
     *
     * @param context The context of this bundle.
     * @throws Exception If the superclass fails to start the bundle.
     */
    @Override
    public void start(final BundleContext context) throws Exception
    {
        super.start(context);
        plugin = this;
    }

    /**
     * Clears the shared instance and stops the bundle.
     *
     * @param context The context of this bundle.
     * @throws Exception If the superclass fails to stop the bundle.
     */
    @Override
    public void stop(final BundleContext context) throws Exception
    {
        plugin = null;
        super.stop(context);
    }

    @Override
    protected void initializeImageRegistry(final ImageRegistry registry)
    {
        DatasetImages.initializeImageRegistry(registry);
    }
}
