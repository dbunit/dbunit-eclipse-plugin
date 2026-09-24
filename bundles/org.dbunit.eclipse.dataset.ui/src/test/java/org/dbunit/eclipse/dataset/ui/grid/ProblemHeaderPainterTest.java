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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.DefaultNatTableStyleConfiguration;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.layer.DefaultGridLayer;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectAllCommand;
import org.eclipse.nebula.widgets.nattable.style.theme.DarkNatTableThemeConfiguration;
import org.eclipse.nebula.widgets.nattable.style.theme.ModernNatTableThemeConfiguration;
import org.eclipse.nebula.widgets.nattable.style.theme.ThemeConfiguration;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests how the header of a column with problems is painted, by comparing a painted grid's pixels: the
 * header keeps the theme's colors and shows the problem image, also while the column is selected.
 */
class ProblemHeaderPainterTest
{
    private static final int PLAIN_COLUMN = 0;

    private static final int WARNING_COLUMN = 1;

    private static final int IMAGE_SIZE = 16;

    private Shell shell;

    private NatTable natTable;

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testPaintCell_inTheDarkTheme_paintsTheHeaderBackgroundAsTheThemeDoes()
    {
        final ImageData painted = paintGrid(new DarkNatTableThemeConfiguration(), false);

        assertThat(headerPixel(painted, WARNING_COLUMN, 2))
                .as("A header with a problem must keep the dark theme's header background.")
                .isEqualTo(headerPixel(painted, PLAIN_COLUMN, 2));
        assertThat(imageArea(painted, WARNING_COLUMN))
                .as("A header with a problem must show the problem image at its right edge.")
                .isNotEqualTo(imageArea(painted, PLAIN_COLUMN));
    }

    @Test
    void testPaintCell_whileTheColumnIsSelected_stillShowsTheProblemImage()
    {
        final ImageData painted = paintGrid(new ModernNatTableThemeConfiguration(), true);

        assertThat(headerPixel(painted, WARNING_COLUMN, 2))
                .as("A selected header with a problem must keep the theme's selected header background.")
                .isEqualTo(headerPixel(painted, PLAIN_COLUMN, 2));
        assertThat(imageArea(painted, WARNING_COLUMN))
                .as("A selected header with a problem must still show the problem image.")
                .isNotEqualTo(imageArea(painted, PLAIN_COLUMN));
    }

    /**
     * Paints a grid of two columns whose second header has the warning label, as DatasetGrid configures
     * its grids, and returns the painted pixels.
     */
    private ImageData paintGrid(final ThemeConfiguration theme, final boolean selectAll)
    {
        shell = new Shell(Display.getDefault());
        shell.setLayout(new FillLayout());
        shell.setSize(400, 200);
        final DefaultGridLayer gridLayer = new DefaultGridLayer(new CellValues(), new ColumnNames(), false);
        ((DataLayer) gridLayer.getColumnHeaderDataLayer()).setConfigLabelAccumulator((labels, column, row) ->
        {
            if (column == WARNING_COLUMN)
            {
                labels.addLabel(ColumnHeaderLabels.COLUMN_WARNING);
            }
        });
        natTable = new NatTable(shell, gridLayer, false);
        natTable.addConfiguration(new DefaultNatTableStyleConfiguration());
        natTable.addConfiguration(new GridStyleConfiguration());
        natTable.configure();
        natTable.setTheme(theme);
        shell.open();
        final Display display = Display.getCurrent();
        while (display.readAndDispatch())
        {
            // Let NatTable lay out before painting it.
        }
        if (selectAll)
        {
            natTable.doCommand(new SelectAllCommand());
        }
        final Rectangle bounds = natTable.getClientArea();
        final Image image = new Image(display, bounds.width, bounds.height);
        final GC gc = new GC(image);
        try
        {
            natTable.getLayerPainter().paintLayer(natTable, gc, 0, 0, bounds, natTable.getConfigRegistry());
            return image.getImageData();
        }
        finally
        {
            gc.dispose();
            image.dispose();
        }
    }

    private RGB headerPixel(final ImageData painted, final int column, final int offsetFromLeft)
    {
        final int x = natTable.getStartXOfColumnPosition(column + 1) + offsetFromLeft;
        return pixel(painted, x, 2);
    }

    private List<RGB> imageArea(final ImageData painted, final int column)
    {
        final int position = column + 1;
        final int left = natTable.getStartXOfColumnPosition(position);
        final int right = left + natTable.getColumnWidthByPosition(position);
        final int top = (natTable.getRowHeightByPosition(0) - IMAGE_SIZE) / 2;
        final List<RGB> pixels = new ArrayList<>();
        for (int y = top; y < top + IMAGE_SIZE; y++)
        {
            for (int x = right - IMAGE_SIZE - 1; x < right - 1; x++)
            {
                pixels.add(pixel(painted, x, y));
            }
        }
        return pixels;
    }

    /**
     * Returns the painted color at a point of the grid, whose coordinates the display's zoom scales to the
     * image's pixels.
     */
    private RGB pixel(final ImageData painted, final int x, final int y)
    {
        final double zoom = painted.width / (double) natTable.getClientArea().width;
        final int pixel = painted.getPixel((int) Math.round(x * zoom), (int) Math.round(y * zoom));
        return painted.palette.getRGB(pixel);
    }

    private static final class CellValues implements IDataProvider
    {
        @Override
        public Object getDataValue(final int columnIndex, final int rowIndex)
        {
            return "v";
        }

        @Override
        public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getColumnCount()
        {
            return 2;
        }

        @Override
        public int getRowCount()
        {
            return 2;
        }
    }

    private static final class ColumnNames implements IDataProvider
    {
        @Override
        public Object getDataValue(final int columnIndex, final int rowIndex)
        {
            return "C";
        }

        @Override
        public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getColumnCount()
        {
            return 2;
        }

        @Override
        public int getRowCount()
        {
            return 1;
        }
    }
}
