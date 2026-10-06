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
package org.dbunit.eclipse.dataset.ui.grid;

import java.util.function.Supplier;

import org.eclipse.nebula.widgets.nattable.data.convert.DisplayConverter;

/**
 * Converts a cell's canonical value to its display text for {@code DisplayMode.NORMAL}: NULL becomes the
 * configured NULL display text, and each line break becomes {@code ⏎}.
 *
 * @since 1.0.0
 */
final class NullAwareDisplayConverter extends DisplayConverter
{
    private static final String LINE_BREAK_DISPLAY = "⏎";

    private final Supplier<String> nullDisplayText;

    /**
     * Creates the converter of one grid.
     *
     * @param nullDisplayText Supplies the text to show for NULL each time a value is converted, so that a
     *        change of the configured text shows when the grid repaints.
     */
    NullAwareDisplayConverter(final Supplier<String> nullDisplayText)
    {
        this.nullDisplayText = nullDisplayText;
    }

    @Override
    public Object canonicalToDisplayValue(final Object canonicalValue)
    {
        if (canonicalValue == null)
        {
            return nullDisplayText.get();
        }
        return canonicalValue.toString().replace("\r\n", LINE_BREAK_DISPLAY).replace("\r", LINE_BREAK_DISPLAY)
                .replace("\n", LINE_BREAK_DISPLAY);
    }

    @Override
    public Object displayToCanonicalValue(final Object displayValue)
    {
        return displayValue;
    }
}
