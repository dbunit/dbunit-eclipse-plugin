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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.text.Document;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.layer.FixedScalingDpiConverter;
import org.eclipse.nebula.widgets.nattable.layer.command.ConfigureScalingCommand;
import org.eclipse.nebula.widgets.nattable.resize.command.ColumnResizeCommand;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ColumnWidths} against a body layer that scales the widths it displays, as NatTable does when
 * the scaling of the display differs from the one SWT draws with.
 */
class ColumnWidthsTest
{
    private static final int UNSCALED_DPI = 96;

    private static final int ENLARGED_DPI = 144;

    private static final int REDUCED_DPI = 84;

    private static final int USER_WIDTH = 300;

    private static final String NULL_DISPLAY_TEXT = "(this cell has no value at all)";

    private static final String NOTE_DECLARED_BY_DTD = "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
            + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #IMPLIED NOTE CDATA #IMPLIED>\n]>\n";

    private Shell shell;

    private NatTable natTable;

    @BeforeEach
    void createNatTable()
    {
        shell = new Shell(Display.getDefault());
        shell.setLayout(new FillLayout());
        final DataLayer unrelatedLayer = new DataLayer(new CountsDataProvider(0, 0));
        natTable = new NatTable(shell, NatTable.DEFAULT_STYLE_OPTIONS, unrelatedLayer, false);
    }

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testApplyTo_afterRememberingAtAnUnscaledDisplay_keepsTheDisplayedWidths()
    {
        assertDisplayedWidthsSurviveRefreshesAt(UNSCALED_DPI);
    }

    @Test
    void testApplyTo_afterRememberingAtAnEnlargedDisplay_keepsTheDisplayedWidths()
    {
        assertDisplayedWidthsSurviveRefreshesAt(ENLARGED_DPI);
    }

    @Test
    void testApplyTo_afterRememberingAtAReducedDisplay_keepsTheDisplayedWidths()
    {
        assertDisplayedWidthsSurviveRefreshesAt(REDUCED_DPI);
    }

    @Test
    void testApplyTo_afterAUserResizedAColumnAtAnEnlargedDisplay_keepsTheWidthWhenTheColumnMoves()
    {
        final DatasetTable before = table("<dataset><USERS NAME=\"Alice\"/></dataset>");
        final DatasetTable after = table("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final DataLayer bodyLayer = bodyLayer(before, ENLARGED_DPI);
        final ColumnWidths columnWidths = newColumnWidths();
        columnWidths.applyTo(before, bodyLayer, natTable);
        bodyLayer.doCommand(new ColumnResizeCommand(bodyLayer, 0, USER_WIDTH, true));

        columnWidths.remember(before, bodyLayer);
        columnWidths.applyTo(after, bodyLayer, natTable);

        assertThat(bodyLayer.getColumnWidthByPosition(1))
                .as("A column must keep the width the user dragged it to when it moves to another position.")
                .isEqualTo(USER_WIDTH);
    }

    @Test
    void testApplyTo_whenAColumnHadNoExplicitWidthWhenRemembered_givesItAnAutomaticWidth()
    {
        final DatasetTable table = table("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final DataLayer bodyLayer = bodyLayer(table, UNSCALED_DPI);
        final DataLayer referenceLayer = bodyLayer(table, UNSCALED_DPI);
        newColumnWidths().applyTo(table, referenceLayer, natTable);
        final ColumnWidths columnWidths = newColumnWidths();

        columnWidths.remember(table, bodyLayer);
        columnWidths.applyTo(table, bodyLayer, natTable);

        assertThat(displayedWidths(bodyLayer, table))
                .as("Columns that were never given a width must get the automatic width, as for a first "
                        + "layout.")
                .isEqualTo(displayedWidths(referenceLayer, table));
    }

    @Test
    void testApplyTo_whenEveryValueOfAColumnIsNull_givesItTheWidthOfTheNullDisplayText()
    {
        final DatasetTable nullNotes =
                table(NOTE_DECLARED_BY_DTD + "<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>");
        final DatasetTable notesThatSpellOutTheNullText = table(NOTE_DECLARED_BY_DTD
                + "<dataset><USERS ID=\"1\" NOTE=\"" + NULL_DISPLAY_TEXT + "\"/><USERS ID=\"2\" NOTE=\""
                + NULL_DISPLAY_TEXT + "\"/></dataset>");

        assertThat(automaticWidths(nullNotes))
                .as("A column of NULL cells must be as wide as the NULL display text that the grid paints in "
                        + "them.")
                .isEqualTo(automaticWidths(notesThatSpellOutTheNullText));
    }

    @Test
    void testApplyTo_whenAValueHasALineFeed_givesItTheWidthOfTheLineBreakGlyph()
    {
        final DatasetTable lineFeed =
                table("<dataset><USERS NOTE=\"first line&#10;second line\"/></dataset>");
        final DatasetTable glyph = table("<dataset><USERS NOTE=\"first line⏎second line\"/></dataset>");

        assertThat(automaticWidths(lineFeed))
                .as("A value with a line feed must be as wide as the text that the grid paints, with the "
                        + "glyph in place of the line break.")
                .isEqualTo(automaticWidths(glyph));
    }

    @Test
    void testApplyTo_whenAValueHasACarriageReturnAndALineFeed_givesItTheWidthOfOneLineBreakGlyph()
    {
        final DatasetTable carriageReturnAndLineFeed =
                table("<dataset><USERS NOTE=\"first line&#13;&#10;second line\"/></dataset>");
        final DatasetTable glyph = table("<dataset><USERS NOTE=\"first line⏎second line\"/></dataset>");

        assertThat(automaticWidths(carriageReturnAndLineFeed))
                .as("A carriage return and a line feed must count as one line break glyph, as the grid "
                        + "paints them.")
                .isEqualTo(automaticWidths(glyph));
    }

    private List<Integer> automaticWidths(final DatasetTable table)
    {
        final DataLayer bodyLayer = bodyLayer(table, UNSCALED_DPI);
        newColumnWidths().applyTo(table, bodyLayer, natTable);
        return displayedWidths(bodyLayer, table);
    }

    private static ColumnWidths newColumnWidths()
    {
        return new ColumnWidths(new NullAwareDisplayConverter(() -> NULL_DISPLAY_TEXT));
    }

    private void assertDisplayedWidthsSurviveRefreshesAt(final int dpi)
    {
        final DatasetTable table = table("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final DataLayer bodyLayer = bodyLayer(table, dpi);
        final ColumnWidths columnWidths = newColumnWidths();
        columnWidths.applyTo(table, bodyLayer, natTable);
        final List<Integer> firstLayout = displayedWidths(bodyLayer, table);

        refresh(columnWidths, table, bodyLayer);
        refresh(columnWidths, table, bodyLayer);

        assertThat(displayedWidths(bodyLayer, table))
                .as("Two refreshes at %d dpi must leave the widths that the first layout displayed.", dpi)
                .isEqualTo(firstLayout);
    }

    private void refresh(final ColumnWidths columnWidths, final DatasetTable table, final DataLayer bodyLayer)
    {
        columnWidths.remember(table, bodyLayer);
        columnWidths.applyTo(table, bodyLayer, natTable);
    }

    private static List<Integer> displayedWidths(final DataLayer bodyLayer, final DatasetTable table)
    {
        final List<Integer> widths = new ArrayList<>();
        for (int position = 0; position < table.getColumns().size(); position++)
        {
            widths.add(bodyLayer.getColumnWidthByPosition(position));
        }
        return widths;
    }

    private static DataLayer bodyLayer(final DatasetTable table, final int dpi)
    {
        final IDataProvider dataProvider =
                new CountsDataProvider(table.getColumns().size(), table.getRows().size());
        final DataLayer bodyLayer = new DataLayer(dataProvider);
        bodyLayer.doCommand(new ConfigureScalingCommand(new FixedScalingDpiConverter(dpi)));
        return bodyLayer;
    }

    private static DatasetTable table(final String content)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(new Document(content),
                DtdSource.NONE, FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument.getModel().findTable("USERS").orElseThrow();
    }

    private static final class CountsDataProvider implements IDataProvider
    {
        private final int columnCount;

        private final int rowCount;

        CountsDataProvider(final int columnCount, final int rowCount)
        {
            this.columnCount = columnCount;
            this.rowCount = rowCount;
        }

        @Override
        public Object getDataValue(final int columnIndex, final int rowIndex)
        {
            return null;
        }

        @Override
        public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue)
        {
        }

        @Override
        public int getColumnCount()
        {
            return columnCount;
        }

        @Override
        public int getRowCount()
        {
            return rowCount;
        }
    }
}
