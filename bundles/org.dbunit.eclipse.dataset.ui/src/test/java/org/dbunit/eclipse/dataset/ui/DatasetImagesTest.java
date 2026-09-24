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

import java.util.List;

import org.eclipse.swt.graphics.Image;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DatasetImages} against the requirement that every declared key loads an image.
 */
class DatasetImagesTest
{
    @Test
    void testGetImage_forEveryDeclaredKey_returnsAUsableImage()
    {
        final List<String> keys = List.of(DatasetImages.IMG_DATASET, DatasetImages.IMG_INSERT_ROW_ABOVE,
                DatasetImages.IMG_INSERT_ROW_BELOW, DatasetImages.IMG_DUPLICATE_ROWS,
                DatasetImages.IMG_DELETE_ROWS, DatasetImages.IMG_ADD_COLUMN,
                DatasetImages.IMG_DELETE_COLUMN, DatasetImages.IMG_ADD_TABLE, DatasetImages.IMG_SET_NULL,
                DatasetImages.IMG_FILL_DOWN, DatasetImages.IMG_NEW_DATASET_WIZBAN);

        for (final String key : keys)
        {
            final Image image = DatasetImages.getImage(key);
            assertThat(image).as("Key '" + key + "' must load an image.").isNotNull();
            assertThat(image.isDisposed()).as("Key '" + key + "' must load a usable, undisposed image.")
                    .isFalse();
        }
    }
}
