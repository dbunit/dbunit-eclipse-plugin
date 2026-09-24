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
import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.coordinate.PositionCoordinate;
import org.eclipse.nebula.widgets.nattable.data.convert.IDisplayConverter;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DatasetGrid} against the Grid specification's assembly, data, labels, and refresh rules.
 */
class DatasetGridTest
{
    private Shell shell;

    @BeforeEach
    void createShell()
    {
        shell = new Shell(Display.getDefault());
        shell.setLayout(new FillLayout());
        shell.setSize(400, 300);
        shell.open();
        processEvents();
    }

    private static void processEvents()
    {
        final Display display = Display.getCurrent();
        while (display.readAndDispatch())
        {
            // Let NatTable finish laying out before commands rely on its viewport.
        }
    }

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testDatasetGrid_whenDisplayed_showsRowsAndColumnsMatchingTheTable()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");

        final TableBodyDataProvider bodyDataProvider = grid.getBodyDataProvider();
        assertThat(bodyDataProvider.getRowCount()).as("The row count must match the table.").isEqualTo(2);
        assertThat(bodyDataProvider.getColumnCount()).as("The column count must match the table.")
                .isEqualTo(2);
        assertThat(bodyDataProvider.getDataValue(1, 1)).as("Cell values must match the table.")
                .isEqualTo("Bob");
    }

    @Test
    void testDisplayConverter_whenValueIsNull_showsTheNullDisplayTextButKeepsTheDataValueNull()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");

        assertThat(grid.getBodyDataProvider().getDataValue(1, 1))
                .as("The canonical data value of a NULL cell must stay null.").isNull();

        final IDisplayConverter converter = normalConverter(grid);
        assertThat(converter.canonicalToDisplayValue(null))
                .as("The NORMAL converter must show the configured NULL display text.")
                .isEqualTo("(null)");
    }

    @Test
    void testDisplayConverter_whenValueHasLineBreaks_showsTheLineBreakGlyph()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");

        final IDisplayConverter converter = normalConverter(grid);

        assertThat(converter.canonicalToDisplayValue("line1\nline2\r\nline3\rline4"))
                .as("Every line break sequence must become the line break glyph.")
                .isEqualTo("line1⏎line2⏎line3⏎line4");
    }

    @Test
    void testColumnHeaderLabels_whenColumnIsPending_getsThePendingColumnLabel()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        datasetDocument.addColumn("USERS", "EXTRA");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");

        final LabelStack idLabels = grid.columnHeaderLabelsFor(0);
        final LabelStack extraLabels = grid.columnHeaderLabelsFor(1);

        assertThat(idLabels.hasLabel("PENDING_COLUMN")).as("A data column must not be pending.")
                .isFalse();
        assertThat(extraLabels.hasLabel("PENDING_COLUMN")).as("A pending column must get the label.")
                .isTrue();
    }

    @Test
    void testTableChanged_whenStructureIsUnchanged_keepsTheSelection()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");
        processEvents();
        grid.getSelectionLayer().moveSelectionAnchor(1, 1);

        datasetDocument.setCells("USERS", List.of(new CellChange(1, "NAME", "Carol")));
        grid.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());

        final PositionCoordinate anchor = grid.getSelectionLayer().getSelectionAnchor();
        assertThat(anchor.columnPosition).as("A structurally unchanged model must keep the selection.")
                .isEqualTo(1);
        assertThat(anchor.rowPosition).as("A structurally unchanged model must keep the selection.")
                .isEqualTo(1);
    }

    @Test
    void testTableChanged_whenARowIsInserted_updatesTheRowCountAndKeepsTheAnchor()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");
        processEvents();
        grid.getSelectionLayer().setSelectedCell(0, 1);

        datasetDocument.insertRows("USERS", 2, List.of(List.of("3")));
        grid.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());

        assertThat(grid.getBodyDataProvider().getRowCount())
                .as("Inserting a row must update the row count.").isEqualTo(3);
        assertThat(selectedCells(grid))
                .as("The selected cell must stay selected after an insertion elsewhere.")
                .containsExactly(List.of(0, 1));
        final PositionCoordinate anchor = grid.getSelectionLayer().getSelectionAnchor();
        assertThat(List.of(anchor.columnPosition, anchor.rowPosition))
                .as("The selected cell must stay the anchor after an insertion elsewhere.")
                .isEqualTo(List.of(0, 1));
    }

    private static List<List<Integer>> selectedCells(final DatasetGrid grid)
    {
        final List<List<Integer>> cells = new ArrayList<>();
        for (final PositionCoordinate position : grid.getSelectionLayer().getSelectedCellPositions())
        {
            cells.add(List.of(position.columnPosition, position.rowPosition));
        }
        return cells;
    }

    private static IDisplayConverter normalConverter(final DatasetGrid grid)
    {
        return grid.getNatTable().getConfigRegistry().getConfigAttribute(
                CellConfigAttributes.DISPLAY_CONVERTER, DisplayMode.NORMAL);
    }

    private static FlatXmlDatasetDocument create(final String content)
    {
        final IDocument document = new Document(content);
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }

    private static final class TestContext implements DatasetGridContext
    {
        private final DatasetDocument datasetDocument;

        TestContext(final DatasetDocument datasetDocument)
        {
            this.datasetDocument = datasetDocument;
        }

        @Override
        public DatasetDocument getDatasetDocument()
        {
            return datasetDocument;
        }

        @Override
        public boolean isEditable()
        {
            return false;
        }

        @Override
        public String getNullDisplayText()
        {
            return "(null)";
        }

        @Override
        public boolean isDarkTheme()
        {
            return false;
        }

        @Override
        public boolean executeEdit(final Runnable edit)
        {
            edit.run();
            return true;
        }

        @Override
        public void fillContextMenu(final IMenuManager menu, final String region)
        {
        }
    }
}
