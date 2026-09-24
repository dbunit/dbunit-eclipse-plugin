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

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;

/**
 * The images of the dataset editor UI, registered in {@link DatasetUiPlugin}'s image registry.
 *
 * @since 1.0.0
 */
public final class DatasetImages
{
    /**
     * The key for the dataset object icon.
     */
    public static final String IMG_DATASET = "IMG_DATASET";

    /**
     * The key for the insert-row-above toolbar icon.
     */
    public static final String IMG_INSERT_ROW_ABOVE = "IMG_INSERT_ROW_ABOVE";

    /**
     * The key for the insert-row-below toolbar icon.
     */
    public static final String IMG_INSERT_ROW_BELOW = "IMG_INSERT_ROW_BELOW";

    /**
     * The key for the duplicate-rows toolbar icon.
     */
    public static final String IMG_DUPLICATE_ROWS = "IMG_DUPLICATE_ROWS";

    /**
     * The key for the delete-rows toolbar icon.
     */
    public static final String IMG_DELETE_ROWS = "IMG_DELETE_ROWS";

    /**
     * The key for the add-column toolbar icon.
     */
    public static final String IMG_ADD_COLUMN = "IMG_ADD_COLUMN";

    /**
     * The key for the delete-column toolbar icon.
     */
    public static final String IMG_DELETE_COLUMN = "IMG_DELETE_COLUMN";

    /**
     * The key for the add-table toolbar icon.
     */
    public static final String IMG_ADD_TABLE = "IMG_ADD_TABLE";

    /**
     * The key for the set-null toolbar icon.
     */
    public static final String IMG_SET_NULL = "IMG_SET_NULL";

    /**
     * The key for the fill-down toolbar icon.
     */
    public static final String IMG_FILL_DOWN = "IMG_FILL_DOWN";

    /**
     * The key for the New Dataset wizard's banner image.
     */
    public static final String IMG_NEW_DATASET_WIZBAN = "IMG_NEW_DATASET_WIZBAN";

    private DatasetImages()
    {
    }

    static void initializeImageRegistry(final ImageRegistry registry)
    {
        register(registry, IMG_DATASET, "icons/obj16/dataset.png");
        register(registry, IMG_INSERT_ROW_ABOVE, "icons/etool16/insert_row_above.png");
        register(registry, IMG_INSERT_ROW_BELOW, "icons/etool16/insert_row_below.png");
        register(registry, IMG_DUPLICATE_ROWS, "icons/etool16/duplicate_rows.png");
        register(registry, IMG_DELETE_ROWS, "icons/etool16/delete_rows.png");
        register(registry, IMG_ADD_COLUMN, "icons/etool16/add_column.png");
        register(registry, IMG_DELETE_COLUMN, "icons/etool16/delete_column.png");
        register(registry, IMG_ADD_TABLE, "icons/etool16/add_table.png");
        register(registry, IMG_SET_NULL, "icons/etool16/set_null.png");
        register(registry, IMG_FILL_DOWN, "icons/etool16/fill_down.png");
        register(registry, IMG_NEW_DATASET_WIZBAN, "icons/wizban/new_dataset_wiz.png");
    }

    private static void register(final ImageRegistry registry, final String key, final String path)
    {
        registry.put(key, AbstractUIPlugin.imageDescriptorFromPlugin(DatasetUiPlugin.PLUGIN_ID, path));
    }

    /**
     * Returns one of this class's images.
     *
     * @param key One of this class's {@code IMG_} constants.
     * @return The image, or a placeholder when the key is missing or the image failed to load.
     */
    public static Image getImage(final String key)
    {
        return DatasetUiPlugin.getDefault().getImageRegistry().get(key);
    }

    /**
     * Returns the descriptor of one of this class's images.
     *
     * @param key One of this class's {@code IMG_} constants.
     * @return The image descriptor, or null when the key is missing.
     */
    public static ImageDescriptor getImageDescriptor(final String key)
    {
        return DatasetUiPlugin.getDefault().getImageRegistry().getDescriptor(key);
    }
}
