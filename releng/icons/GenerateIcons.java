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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

/**
 * Draws the dataset editor's icons as 16x16 (or, for the wizard banner, 75x66) PNGs plus their 32x32
 * {@code @2x} variants, into {@code bundles/org.dbunit.eclipse.dataset.ui/icons/}. Run from the
 * repository root: {@code java releng/icons/GenerateIcons.java}.
 */
public class GenerateIcons
{
    private static final Path ICONS_ROOT = Paths.get("bundles/org.dbunit.eclipse.dataset.ui/icons");

    private static final Color GRID = new Color(0x8C, 0x8C, 0x8C);

    private static final Color HIGHLIGHT = new Color(0x4A, 0x90, 0xD9);

    private static final Color PLUS = new Color(0x3C, 0x9A, 0x3C);

    private static final Color MINUS = new Color(0xD9, 0x53, 0x4F);

    public static void main(final String[] args) throws IOException
    {
        draw("obj16/dataset.png", 16, 16, GenerateIcons::paintDataset);
        draw("etool16/insert_row_above.png", 16, 16, GenerateIcons::paintInsertRowAbove);
        draw("etool16/insert_row_below.png", 16, 16, GenerateIcons::paintInsertRowBelow);
        draw("etool16/duplicate_rows.png", 16, 16, GenerateIcons::paintDuplicateRows);
        draw("etool16/delete_rows.png", 16, 16, GenerateIcons::paintDeleteRows);
        draw("etool16/add_column.png", 16, 16, GenerateIcons::paintAddColumn);
        draw("etool16/delete_column.png", 16, 16, GenerateIcons::paintDeleteColumn);
        draw("etool16/add_table.png", 16, 16, GenerateIcons::paintAddTable);
        draw("etool16/set_null.png", 16, 16, GenerateIcons::paintSetNull);
        draw("etool16/fill_down.png", 16, 16, GenerateIcons::paintFillDown);
        draw("wizban/new_dataset_wiz.png", 75, 66, GenerateIcons::paintNewDatasetWizban);
        System.out.println("Generated 11 icons (22 files, including @2x) under " + ICONS_ROOT);
    }

    private static void draw(final String relativePath, final int width, final int height,
            final Consumer<Graphics2D> painter) throws IOException
    {
        saveIcon(relativePath, width, height, 1, painter);
        saveIcon(withAt2x(relativePath), width, height, 2, painter);
    }

    private static String withAt2x(final String relativePath)
    {
        final int dot = relativePath.lastIndexOf('.');
        return relativePath.substring(0, dot) + "@2x" + relativePath.substring(dot);
    }

    private static void saveIcon(final String relativePath, final int width, final int height,
            final int scale, final Consumer<Graphics2D> painter) throws IOException
    {
        final BufferedImage image =
                new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(scale, scale);
        painter.accept(g);
        g.dispose();

        final Path outputPath = ICONS_ROOT.resolve(relativePath);
        Files.createDirectories(outputPath.getParent());
        ImageIO.write(image, "png", outputPath.toFile());
    }

    private static void paintDataset(final Graphics2D g)
    {
        final int left = 2;
        final int top = 2;
        final int cellWidth = 4;
        final int cellHeight = 4;
        final int columns = 3;
        final int rows = 3;
        g.setColor(HIGHLIGHT);
        g.fillRect(left, top, cellWidth * columns, cellHeight);
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1f));
        for (int row = 0; row <= rows; row++)
        {
            final int y = top + row * cellHeight;
            g.drawLine(left, y, left + cellWidth * columns, y);
        }
        for (int column = 0; column <= columns; column++)
        {
            final int x = left + column * cellWidth;
            g.drawLine(x, top, x, top + cellHeight * rows);
        }
    }

    private static void paintInsertRowAbove(final Graphics2D g)
    {
        drawPlus(g, 8, 4, 2);
        drawBar(g, 2, 9, 12, 4);
    }

    private static void paintInsertRowBelow(final Graphics2D g)
    {
        drawBar(g, 2, 3, 12, 4);
        drawPlus(g, 8, 12, 2);
    }

    private static void paintDuplicateRows(final Graphics2D g)
    {
        drawBar(g, 1, 3, 11, 4);
        drawBar(g, 4, 9, 11, 4);
    }

    private static void paintDeleteRows(final Graphics2D g)
    {
        drawBar(g, 2, 6, 12, 4);
        drawCross(g, 8, 8, 5);
    }

    private static void paintAddColumn(final Graphics2D g)
    {
        drawBar(g, 9, 2, 4, 12);
        drawPlus(g, 3, 4, 2);
    }

    private static void paintDeleteColumn(final Graphics2D g)
    {
        drawBar(g, 6, 2, 4, 12);
        drawCross(g, 8, 8, 5);
    }

    private static void paintAddTable(final Graphics2D g)
    {
        final int left = 1;
        final int top = 1;
        final int cellSize = 4;
        g.setColor(HIGHLIGHT);
        g.fillRect(left, top, cellSize * 2, cellSize);
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1f));
        for (int row = 0; row <= 2; row++)
        {
            final int y = top + row * cellSize;
            g.drawLine(left, y, left + cellSize * 2, y);
        }
        for (int column = 0; column <= 2; column++)
        {
            final int x = left + column * cellSize;
            g.drawLine(x, top, x, top + cellSize * 2);
        }
        drawPlus(g, 12, 12, 3);
    }

    private static void paintSetNull(final Graphics2D g)
    {
        g.setColor(MINUS);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(3, 3, 10, 10);
        g.drawLine(5, 11, 11, 5);
    }

    private static void paintFillDown(final Graphics2D g)
    {
        g.setColor(HIGHLIGHT);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(8, 2, 8, 11);
        g.drawLine(8, 13, 4, 9);
        g.drawLine(8, 13, 12, 9);
    }

    private static void paintNewDatasetWizban(final Graphics2D g)
    {
        final int left = 8;
        final int top = 10;
        final int cellWidth = 12;
        final int cellHeight = 10;
        final int columns = 3;
        final int rows = 3;
        g.setColor(HIGHLIGHT);
        g.fillRect(left, top, cellWidth * columns, cellHeight);
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1.5f));
        for (int row = 0; row <= rows; row++)
        {
            final int y = top + row * cellHeight;
            g.drawLine(left, y, left + cellWidth * columns, y);
        }
        for (int column = 0; column <= columns; column++)
        {
            final int x = left + column * cellWidth;
            g.drawLine(x, top, x, top + cellHeight * rows);
        }
        drawPlus(g, left + cellWidth * columns + 8, top + 4, 5);
    }

    private static void drawBar(final Graphics2D g, final int x, final int y, final int width,
            final int height)
    {
        g.setColor(HIGHLIGHT);
        g.fillRect(x, y, width, height);
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x, y, width, height);
    }

    private static void drawPlus(final Graphics2D g, final int centerX, final int centerY,
            final int armLength)
    {
        g.setColor(PLUS);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(centerX - armLength, centerY, centerX + armLength, centerY);
        g.drawLine(centerX, centerY - armLength, centerX, centerY + armLength);
    }

    private static void drawCross(final Graphics2D g, final int centerX, final int centerY,
            final int armLength)
    {
        g.setColor(MINUS);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(centerX - armLength, centerY - armLength, centerX + armLength, centerY + armLength);
        g.drawLine(centerX - armLength, centerY + armLength, centerX + armLength, centerY - armLength);
    }
}
