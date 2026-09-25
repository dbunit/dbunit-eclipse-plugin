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
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.coordinate.PositionCoordinate;
import org.eclipse.nebula.widgets.nattable.data.convert.IDisplayConverter;
import org.eclipse.nebula.widgets.nattable.edit.EditConfigAttributes;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.nebula.widgets.nattable.layer.cell.CellDisplayConversionUtils;
import org.eclipse.nebula.widgets.nattable.layer.cell.ILayerCell;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
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
    void testCellLabels_ofAValueWithALineBreak_openItInTheDialogEditor()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NOTE=\"first&#10;second\"/><USERS ID=\"2\" NOTE=\"plain\"/>"
                        + "</dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");
        final List<String> multiLineLabels = grid.cellLabelsFor(1, 0).getLabels();
        final IConfigRegistry configRegistry = grid.getNatTable().getConfigRegistry();

        assertThat(multiLineLabels).as("A value with a line break must get the multi-line label.")
                .containsExactly("MULTI_LINE_VALUE");
        assertThat(grid.cellLabelsFor(1, 1).getLabels()).as("A single-line value must get no label.")
                .isEmpty();
        assertThat(configRegistry.getConfigAttribute(EditConfigAttributes.OPEN_IN_DIALOG, DisplayMode.EDIT,
                multiLineLabels))
                .as("A multi-line value must open in the dialog editor, which keeps its line breaks.")
                .isTrue();
    }

    @Test
    void testDisplayText_ofTheCornerHeadersAndBody_showsTheNullDisplayTextOnlyForNullBodyCells()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");
        shell.layout();
        processEvents();

        final List<String> texts = List.of(displayText(grid, 0, 0), displayText(grid, 0, 2),
                displayText(grid, 1, 0), displayText(grid, 2, 2));

        assertThat(texts).as("The corner must be blank, the headers must show row numbers and column names, "
                + "and a NULL body cell must show the NULL display text.")
                .containsExactly("", "2", "ID", "(null)");
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
    void testColumnHeaderTooltip_overTheEmptyAreaBeyondTheCells_hasNoText()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");
        grid.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());
        shell.layout();
        processEvents();
        final Event hover = new Event();
        hover.x = grid.getNatTable().getClientArea().width - 2;
        hover.y = grid.getNatTable().getClientArea().height - 2;

        assertThat(grid.getColumnHeaderTooltip().getText(hover))
                .as("Hovering beyond the last column and row must show no tooltip instead of failing.")
                .isNull();
    }

    @Test
    void testColumnHeaderTooltip_forADeclaredColumnWithoutValues_showsTheDtdText()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");

        final String idText = grid.getColumnHeaderTooltip().textForColumn(0);
        final String nameText = grid.getColumnHeaderTooltip().textForColumn(1);

        assertThat(idText).as("A declared column with a value must get no tooltip.").isNull();
        assertThat(nameText).as("A declared column without values must explain why it is empty.")
                .isEqualTo("Declared in the DTD; no values yet");
    }

    @Test
    void testColumnHeaderTooltip_forAPendingColumn_explainsWhenTheColumnIsSaved()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        datasetDocument.addColumn("USERS", "EXTRA");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");

        final String idText = grid.getColumnHeaderTooltip().textForColumn(0);
        final String extraText = grid.getColumnHeaderTooltip().textForColumn(1);

        assertThat(idText).as("A column with values must get no tooltip.").isNull();
        assertThat(extraText).as("A pending column must explain that it is saved once a row has a value.")
                .isEqualTo("This column has no values yet; it is saved when at least one row has a value.");
    }

    @Test
    void testColumnHeaderLabelsAndTooltip_whenAColumnIsMissingFromTheFirstRow_warnAndListTheProblem()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");

        assertThat(grid.columnHeaderLabelsFor(0).hasLabel("COLUMN_WARNING"))
                .as("A column with no problems must not get the warning label.").isFalse();
        assertThat(grid.columnHeaderLabelsFor(1).hasLabel("COLUMN_WARNING"))
                .as("A column missing from the first row must get the warning label.").isTrue();
        assertThat(grid.getColumnHeaderTooltip().textForColumn(1))
                .as("The column header tooltip must list the problem.")
                .contains("Column \"NAME\" of table \"USERS\" is missing from the first element");
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

    @Test
    void testSelectRegion_reachingBeyondTheTable_selectsTheBlockClampedToTheTable()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"A\"/>"
                + "<USERS ID=\"2\" NAME=\"B\"/><USERS ID=\"3\" NAME=\"C\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");
        grid.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());
        processEvents();

        grid.selectRegion(1, 1, 5, 5);

        assertThat(selectedCells(grid)).as("The block must be clamped to the table's last column and row.")
                .containsExactlyInAnyOrder(List.of(1, 1), List.of(1, 2));
        final PositionCoordinate anchor = grid.getSelectionLayer().getSelectionAnchor();
        assertThat(List.of(anchor.columnPosition, anchor.rowPosition))
                .as("The block's first cell must become the selection anchor.").isEqualTo(List.of(1, 1));
    }

    @Test
    void testSelectCell_inARowBelowTheVisibleRows_scrollsItIntoView()
    {
        final StringBuilder text = new StringBuilder("<dataset>");
        for (int row = 1; row <= 100; row++)
        {
            text.append("<USERS ID=\"").append(row).append("\"/>");
        }
        final FlatXmlDatasetDocument datasetDocument = create(text.append("</dataset>").toString());
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");
        grid.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());
        shell.layout();
        processEvents();

        grid.selectCell(0, 90);

        final NatTable natTable = grid.getNatTable();
        final List<Integer> visibleRowIndexes = new ArrayList<>();
        for (int position = 1; position < natTable.getRowCount(); position++)
        {
            visibleRowIndexes.add(natTable.getRowIndexByPosition(position));
        }
        assertThat(visibleRowIndexes).as("Selecting a cell must scroll its row into view.").contains(90);
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
                CellConfigAttributes.DISPLAY_CONVERTER, DisplayMode.NORMAL, GridRegion.BODY);
    }

    /**
     * Returns the text that NatTable's text painters show for a cell: the cell's value, converted by the
     * display converter that the cell's display mode and labels select.
     */
    private static String displayText(final DatasetGrid grid, final int columnPosition, final int rowPosition)
    {
        final NatTable natTable = grid.getNatTable();
        final ILayerCell cell = natTable.getCellByPosition(columnPosition, rowPosition);
        return CellDisplayConversionUtils.convertDataType(cell, natTable.getConfigRegistry());
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
        public boolean executeMultiCellEdit(final String title, final Runnable edit)
        {
            edit.run();
            return true;
        }

        @Override
        public void fillContextMenu(final IMenuManager menu, final String region)
        {
        }

        @Override
        public boolean hasActiveCellEditor()
        {
            return false;
        }

        @Override
        public GridSelection getSelection()
        {
            return GridSelection.NONE;
        }

        @Override
        public void selectRegion(final int firstColumnIndex, final int firstRowIndex, final int columnCount,
                final int rowCount)
        {
        }

        @Override
        public Shell getShell()
        {
            return null;
        }

        @Override
        public void expectRename(final String oldKey, final String newKey)
        {
        }

        @Override
        public void expectNewTableSelected(final String tableName)
        {
        }

        @Override
        public Text getActiveCellEditorText()
        {
            return null;
        }

        @Override
        public List<Point> getSelectedCellPositions()
        {
            return List.of();
        }

        @Override
        public void selectAll()
        {
        }

        @Override
        public void editCellInDialog()
        {
        }

        @Override
        public void showInSource()
        {
        }

        @Override
        public void setStatusMessage(final String message)
        {
        }

        @Override
        public void writeClipboardText(final String text)
        {
        }

        @Override
        public String readClipboardText()
        {
            return null;
        }
    }
}
