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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.layer.cell.ILayerCell;
import org.eclipse.nebula.widgets.nattable.painter.cell.AbstractCellPainter;
import org.eclipse.nebula.widgets.nattable.painter.cell.ICellPainter;
import org.eclipse.nebula.widgets.nattable.painter.cell.ImagePainter;
import org.eclipse.nebula.widgets.nattable.painter.cell.TextPainter;
import org.eclipse.nebula.widgets.nattable.painter.cell.decorator.CellPainterDecorator;
import org.eclipse.nebula.widgets.nattable.ui.util.CellEdgeEnum;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

/**
 * Paints the header of a column with problems as the grid's theme paints a header in the cell's display
 * mode, with a warning or error image over its right edge, so that the header keeps the theme's colors
 * and the image stays while the column is selected.
 *
 * @since 1.0.0
 */
final class ProblemHeaderPainter extends AbstractCellPainter
{
    private final ICellPainter imagePainter;

    ProblemHeaderPainter(final Image image)
    {
        imagePainter = new ImagePainter(image, false);
    }

    @Override
    public void paintCell(final ILayerCell cell, final GC gc, final Rectangle bounds,
            final IConfigRegistry configRegistry)
    {
        final ICellPainter decorated = new CellPainterDecorator(themePainter(cell, configRegistry),
                CellEdgeEnum.RIGHT, 0, imagePainter, false, false);
        decorated.paintCell(cell, gc, bounds, configRegistry);
    }

    @Override
    public int getPreferredWidth(final ILayerCell cell, final GC gc, final IConfigRegistry configRegistry)
    {
        final ICellPainter themePainter = themePainter(cell, configRegistry);
        final int textWidth = themePainter.getPreferredWidth(cell, gc, configRegistry);
        return textWidth + imagePainter.getPreferredWidth(cell, gc, configRegistry);
    }

    @Override
    public int getPreferredHeight(final ILayerCell cell, final GC gc, final IConfigRegistry configRegistry)
    {
        final ICellPainter themePainter = themePainter(cell, configRegistry);
        final int textHeight = themePainter.getPreferredHeight(cell, gc, configRegistry);
        return Math.max(textHeight, imagePainter.getPreferredHeight(cell, gc, configRegistry));
    }

    /**
     * Returns the painter that the grid's configuration, its theme included, gives the cell without its
     * problem labels.
     */
    private static ICellPainter themePainter(final ILayerCell cell, final IConfigRegistry configRegistry)
    {
        final List<String> labels = new ArrayList<>(cell.getConfigLabels().getLabels());
        labels.remove(ColumnHeaderLabels.COLUMN_WARNING);
        labels.remove(ColumnHeaderLabels.COLUMN_ERROR);
        final ICellPainter painter = configRegistry.getConfigAttribute(CellConfigAttributes.CELL_PAINTER,
                cell.getDisplayMode(), labels);
        return painter == null ? new TextPainter() : painter;
    }
}
