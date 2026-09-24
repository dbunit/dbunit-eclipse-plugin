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

import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.nebula.widgets.nattable.config.AbstractRegistryConfiguration;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.painter.cell.ICellPainter;
import org.eclipse.nebula.widgets.nattable.style.CellStyleAttributes;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.nebula.widgets.nattable.style.Style;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * Registers how a {@link DatasetGrid} styles NULL values, pending columns, and columns with problems.
 *
 * @since 1.0.0
 */
final class GridStyleConfiguration extends AbstractRegistryConfiguration
{
    @Override
    public void configureRegistry(final IConfigRegistry configRegistry)
    {
        final Style nullValueStyle = new Style();
        nullValueStyle.setAttributeValue(CellStyleAttributes.FONT,
                JFaceResources.getFontRegistry().getItalic(JFaceResources.DEFAULT_FONT));
        nullValueStyle.setAttributeValue(CellStyleAttributes.FOREGROUND_COLOR,
                JFaceResources.getColorRegistry().get(JFacePreferences.QUALIFIER_COLOR));
        registerForNormalAndSelect(configRegistry, nullValueStyle, DatasetCellLabels.NULL_VALUE);

        final Style pendingColumnStyle = new Style();
        pendingColumnStyle.setAttributeValue(CellStyleAttributes.FONT,
                JFaceResources.getFontRegistry().getItalic(JFaceResources.DEFAULT_FONT));
        registerForNormalAndSelect(configRegistry, pendingColumnStyle, ColumnHeaderLabels.PENDING_COLUMN);

        registerProblemPainter(configRegistry, ColumnHeaderLabels.COLUMN_WARNING,
                ISharedImages.IMG_OBJS_WARN_TSK);
        registerProblemPainter(configRegistry, ColumnHeaderLabels.COLUMN_ERROR,
                ISharedImages.IMG_OBJS_ERROR_TSK);
    }

    private static void registerForNormalAndSelect(final IConfigRegistry configRegistry, final Style style,
            final String label)
    {
        configRegistry.registerConfigAttribute(CellConfigAttributes.CELL_STYLE, style, DisplayMode.NORMAL,
                label);
        configRegistry.registerConfigAttribute(CellConfigAttributes.CELL_STYLE, style, DisplayMode.SELECT,
                label);
    }

    private static void registerProblemPainter(final IConfigRegistry configRegistry, final String label,
            final String sharedImageKey)
    {
        final ICellPainter painter = new ProblemHeaderPainter(sharedImage(sharedImageKey));
        configRegistry.registerConfigAttribute(CellConfigAttributes.CELL_PAINTER, painter, DisplayMode.NORMAL,
                label);
        configRegistry.registerConfigAttribute(CellConfigAttributes.CELL_PAINTER, painter, DisplayMode.SELECT,
                label);
    }

    private static Image sharedImage(final String key)
    {
        return PlatformUI.getWorkbench().getSharedImages().getImage(key);
    }
}
