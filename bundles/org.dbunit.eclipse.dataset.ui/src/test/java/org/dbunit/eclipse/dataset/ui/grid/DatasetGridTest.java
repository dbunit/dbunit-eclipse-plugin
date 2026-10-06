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
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.coordinate.PositionCoordinate;
import org.eclipse.nebula.widgets.nattable.data.convert.IDisplayConverter;
import org.eclipse.nebula.widgets.nattable.edit.EditConfigAttributes;
import org.eclipse.nebula.widgets.nattable.edit.command.EditSelectionCommand;
import org.eclipse.nebula.widgets.nattable.edit.editor.ICellEditor;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.layer.FixedScalingDpiConverter;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.nebula.widgets.nattable.layer.cell.CellDisplayConversionUtils;
import org.eclipse.nebula.widgets.nattable.layer.cell.ILayerCell;
import org.eclipse.nebula.widgets.nattable.layer.command.ConfigureScalingCommand;
import org.eclipse.nebula.widgets.nattable.resize.command.ColumnResizeCommand;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer.MoveDirectionEnum;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectCellCommand;
import org.eclipse.nebula.widgets.nattable.style.CellStyleAttributes;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.nebula.widgets.nattable.style.IStyle;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DatasetGrid} against the Grid specification's assembly, data, labels, and refresh rules. The
 * nested class {@code Editing} tests the editing of cells in the grid.
 */
class DatasetGridTest
{
    private static final String DEFAULTS_DOCTYPE = "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
            + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #IMPLIED STATUS CDATA \"ACTIVE\">\n]>\n";

    private static final String DEFAULTS_DATASET =
            DEFAULTS_DOCTYPE + "<dataset><USERS ID=\"1\"/><USERS STATUS=\"x\"/></dataset>";

    private static final int ENLARGED_DPI = 144;

    private static final int USER_COLUMN_WIDTH = 300;

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
    void testUpdateTheme_whenTheWorkbenchTurnsDarkAndLightAgain_appliesTheThemeEachTime()
    {
        final TestContext context = new TestContext(create("<dataset><USERS ID=\"1\"/></dataset>"));
        final DatasetGrid grid = new DatasetGrid(shell, context, "USERS");
        final boolean darkAtFirst = isDark(bodyBackground(grid));

        context.dark = true;
        grid.updateTheme();
        final boolean darkAfterTheSwitch = isDark(bodyBackground(grid));
        context.dark = false;
        grid.updateTheme();
        final boolean darkAfterTheSwitchBack = isDark(bodyBackground(grid));

        assertThat(List.of(darkAtFirst, darkAfterTheSwitch, darkAfterTheSwitchBack))
                .as("A grid that is open when the workbench changes its theme must follow it, in both "
                        + "directions.")
                .containsExactly(false, true, false);
    }

    @Test
    void testUpdateTheme_whenTheThemeIsTheSame_keepsTheStyleObjects()
    {
        final TestContext context = new TestContext(create("<dataset><USERS ID=\"1\"/></dataset>"));
        final DatasetGrid grid = new DatasetGrid(shell, context, "USERS");
        final IStyle before = bodyStyle(grid);

        grid.updateTheme();

        assertThat(bodyStyle(grid)).as("A grid whose theme did not change must not apply its theme again.")
                .isSameAs(before);
    }

    private static IStyle bodyStyle(final DatasetGrid grid)
    {
        return grid.getNatTable().getConfigRegistry().getConfigAttribute(CellConfigAttributes.CELL_STYLE,
                DisplayMode.NORMAL);
    }

    private static Color bodyBackground(final DatasetGrid grid)
    {
        return bodyStyle(grid).getAttributeValue(CellStyleAttributes.BACKGROUND_COLOR);
    }

    private static boolean isDark(final Color color)
    {
        final double luminance =
                (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255.0;
        return luminance < 0.5;
    }

    @Test
    void testContextMenu_whenTheMouseOpensItOverHeadersAndTheCorner_isForThatRegion()
    {
        final TestContext context = new TestContext(create("<dataset><USERS ID=\"1\"/></dataset>"));
        final DatasetGrid grid = new DatasetGrid(shell, context, "USERS");
        shell.layout();
        processEvents();

        final List<String> regions = List.of(regionOfTheMenuAt(grid, context, 0, 0, SWT.MENU_MOUSE),
                regionOfTheMenuAt(grid, context, 1, 0, SWT.MENU_MOUSE),
                regionOfTheMenuAt(grid, context, 0, 1, SWT.MENU_MOUSE),
                regionOfTheMenuAt(grid, context, 1, 1, SWT.MENU_MOUSE));

        assertThat(regions).as("A menu that the mouse opens is for the part of the grid under the mouse.")
                .containsExactly(GridRegion.CORNER, GridRegion.COLUMN_HEADER, GridRegion.ROW_HEADER,
                        GridRegion.BODY);
    }

    @Test
    void testContextMenu_whenTheKeyboardOpensItWithThePointerOverAHeader_isForTheBody()
    {
        final TestContext context = new TestContext(create("<dataset><USERS ID=\"1\"/></dataset>"));
        final DatasetGrid grid = new DatasetGrid(shell, context, "USERS");
        shell.layout();
        processEvents();

        final List<String> regions = List.of(regionOfTheMenuAt(grid, context, 0, 0, SWT.MENU_KEYBOARD),
                regionOfTheMenuAt(grid, context, 1, 0, SWT.MENU_KEYBOARD),
                regionOfTheMenuAt(grid, context, 0, 1, SWT.MENU_KEYBOARD));

        assertThat(regions).as("The pointer has nothing to do with a menu that the keyboard opens, so it is "
                + "for the cell that has the focus.")
                .containsExactly(GridRegion.BODY, GridRegion.BODY, GridRegion.BODY);
    }

    /**
     * Requests the context menu of a grid for the point in the middle of a cell of the grid layer, as the
     * operating system does for a mouse click or for the menu key, and returns the region that the page
     * was asked to fill the menu for.
     */
    private static String regionOfTheMenuAt(final DatasetGrid grid, final TestContext context,
            final int columnPosition, final int rowPosition, final int detail)
    {
        final NatTable natTable = grid.getNatTable();
        final Rectangle bounds = natTable.getBoundsByPosition(columnPosition, rowPosition);
        final Point onDisplay = natTable.toDisplay(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
        final Event menuDetect = new Event();
        menuDetect.x = onDisplay.x;
        menuDetect.y = onDisplay.y;
        menuDetect.detail = detail;
        natTable.notifyListeners(SWT.MenuDetect, menuDetect);
        natTable.getMenu().notifyListeners(SWT.Show, new Event());
        return context.contextMenuRegion;
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
    void testBodyDataProvider_whenARowOmitsAnAttributeTheDtdDefaults_returnsTheDefaultAsTheCellValue()
    {
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(create(DEFAULTS_DATASET)), "USERS");

        final TableBodyDataProvider provider = grid.getBodyDataProvider();

        assertThat(List.of(provider.getDataValue(1, 0), provider.getDataValue(1, 1)))
                .as("A cell without an attribute must hold the column's default, and a cell with one its "
                        + "own value.")
                .containsExactly("ACTIVE", "x");
        assertThat(provider.getDataValue(0, 1))
                .as("A cell without an attribute and without a default is NULL.").isNull();
    }

    @Test
    void testCellLabels_ofACellThatShowsADefault_getTheDefaultLabelAndNotTheNullLabel()
    {
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(create(DEFAULTS_DATASET)), "USERS");

        assertThat(grid.cellLabelsFor(1, 0).getLabels())
                .as("A cell that shows its column's default must be labeled as a default.")
                .containsExactly("DEFAULT_VALUE");
        assertThat(grid.cellLabelsFor(1, 1).getLabels()).as("A cell with its own value must get no label.")
                .isEmpty();
        assertThat(grid.cellLabelsFor(0, 1).getLabels())
                .as("A cell without a value and without a default must still be labeled NULL.")
                .containsExactly("NULL_VALUE");
    }

    @Test
    void testCellLabels_ofACellThatShowsAMultiLineDefault_getBothTheDefaultAndTheMultiLineLabel()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NOTE CDATA \"a&#10;b\">\n]>\n"
                        + "<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");

        assertThat(grid.cellLabelsFor(1, 0).getLabels())
                .as("A multi-line default must still open in the dialog editor.")
                .containsExactlyInAnyOrder("DEFAULT_VALUE", "MULTI_LINE_VALUE");
    }

    @Test
    void testDisplayText_ofACellThatShowsADefault_isTheDefaultNotTheNullDisplayText()
    {
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(create(DEFAULTS_DATASET)), "USERS");
        shell.layout();
        processEvents();

        final List<String> texts = List.of(displayText(grid, 2, 1), displayText(grid, 2, 2),
                displayText(grid, 1, 2));

        assertThat(texts).as("A defaulted cell shows the default, a cell with a value its value, and a cell "
                + "without either the NULL display text.").containsExactly("ACTIVE", "x", "(null)");
    }

    @Test
    void testColumnHeaderTooltip_forAColumnWithADefault_showsTheDefaultInsteadOfNoValuesYet()
    {
        final FlatXmlDatasetDocument datasetDocument = create(DEFAULTS_DOCTYPE
                + "<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");

        assertThat(grid.getColumnHeaderTooltip().textForColumn(1))
                .as("A declared column whose cells all show the default must name the default, not claim "
                        + "that it has no values.")
                .isEqualTo("The DTD gives this column the default value \"ACTIVE\", which dbUnit loads for a "
                        + "row without a value.");
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
    void testColumnHeaderTooltip_forEvent_usesTheHoveredColumnsOwnTooltipNotTheNextColumns()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");
        shell.layout();
        processEvents();
        final NatTable natTable = grid.getNatTable();
        final Event hover = new Event();
        hover.x = natTable.getStartXOfColumnPosition(1);
        hover.y = natTable.getStartYOfRowPosition(0) + 2;

        assertThat(grid.getColumnHeaderTooltip().getText(hover))
                .as("Hovering the first body column's header (ID, which has a value) must show no "
                        + "tooltip, not the DTD-declared-without-values text that belongs to NAME.")
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
    void testTableChanged_whenRowsAreInsertedAtAnEnlargedDisplay_keepsTheColumnWidths()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");
        final SelectionLayer bodyLayer = grid.getSelectionLayer();
        bodyLayer.doCommand(new ConfigureScalingCommand(new FixedScalingDpiConverter(ENLARGED_DPI)));
        grid.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());
        bodyLayer.doCommand(new ColumnResizeCommand(bodyLayer, 0, USER_COLUMN_WIDTH, true));
        final int automaticWidth = bodyLayer.getColumnWidthByPosition(1);

        datasetDocument.insertRows("USERS", 1, List.of(List.of("2", "Bob")));
        grid.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());
        datasetDocument.insertRows("USERS", 2, List.of(List.of("3", "Carol")));
        grid.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());

        assertThat(List.of(bodyLayer.getColumnWidthByPosition(0), bodyLayer.getColumnWidthByPosition(1)))
                .as("Inserting two rows must keep the width the user gave the first column and the "
                        + "automatic width of the second.")
                .containsExactly(USER_COLUMN_WIDTH, automaticWidth);
    }

    @Test
    void testTableChanged_whenAValueHasALineBreak_givesItsColumnAWidthThatFitsTheTextTheGridPaints()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS NOTE=\"first line&#10;second line\"/></dataset>");
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), "USERS");

        grid.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());
        shell.layout();
        processEvents();

        assertThat(grid.getSelectionLayer().getColumnWidthByPosition(0))
                .as("The column must be at least as wide as the text that the grid paints in its cell, which "
                        + "shows the line break as a glyph.")
                .isGreaterThanOrEqualTo(textWidth(grid, displayText(grid, 1, 1)));
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

    private static int textWidth(final DatasetGrid grid, final String text)
    {
        final NatTable natTable = grid.getNatTable();
        final GC gc = new GC(natTable);
        try
        {
            gc.setFont(natTable.getFont());
            return gc.textExtent(text).x;
        }
        finally
        {
            gc.dispose();
        }
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

        private String contextMenuRegion;

        private boolean dark;

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
            return dark;
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
            contextMenuRegion = region;
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
        public void setStatusErrorMessage(final String message)
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

    /**
     * Tests the editing of cells in a real grid against the Grid specification: the in-place editor, the
     * dialog editor, and the cancellation of an edit when the table changes under it.
     */
    @Nested
    class Editing
    {
        private static final String FOUR_USERS = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/>"
                + "<USERS ID=\"4\" NAME=\"Dave\"/></dataset>";

        private static final String USERS_WITHOUT_ALICE = "<dataset><USERS ID=\"2\" NAME=\"Bob\"/>"
                + "<USERS ID=\"3\" NAME=\"Carol\"/><USERS ID=\"4\" NAME=\"Dave\"/></dataset>";

        private static final String EDIT_CANCELLED_MESSAGE =
                "The table changed while a cell was being edited, so the edit was cancelled.";

        private ModalDialogDriver dialogDriver;

        @BeforeEach
        void armDialogDriver()
        {
            dialogDriver = new ModalDialogDriver(shell);
        }

        @AfterEach
        void disarmDialogDriver()
        {
            dialogDriver.disarm();
        }

        @Test
        void testCommit_whenTheInPlaceEditorOfADefaultedCellIsUntouched_keepsTheAttributeOut()
        {
            final IDocument document = new Document(DEFAULTS_DATASET);
            final NatTable natTable = openGrid(create(document), "USERS").getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 2, 1, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            final ICellEditor cellEditor = natTable.getActiveCellEditor();

            final Object editorValue = cellEditor.getEditorValue();
            cellEditor.commit(MoveDirectionEnum.NONE);

            assertThat(editorValue)
                    .as("The editor of a cell that shows a default must start with the default.")
                    .isEqualTo("ACTIVE");
            assertThat(document.get())
                    .as("Committing the editor of a defaulted cell without typing must not write the "
                            + "default.")
                    .isEqualTo(DEFAULTS_DATASET);
        }

        @Test
        void testEditCellInDialog_whenTheCellShowsADefaultAndTheDialogIsConfirmedUnchanged_keepsTheAttributeOut()
        {
            final IDocument document = new Document(DEFAULTS_DATASET);
            final DatasetGrid grid = openGrid(create(document), "USERS");
            grid.selectRegion(1, 0, 1, 1);
            dialogDriver.confirmNextDialog();

            grid.editCellInDialog();

            assertThat(dialogDriver.hasConfirmed()).as("The dialog must open and be confirmed.").isTrue();
            assertThat(document.get())
                    .as("Confirming the dialog of a defaulted cell without a change must not write the "
                            + "default.")
                    .isEqualTo(DEFAULTS_DATASET);
        }

        @Test
        void testCommit_ofAnEditThatMovesTheSelection_closesTheEditorWithoutEditingTheNextCell()
        {
            final IDocument document = new Document("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>");
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final NatTable natTable = openGrid(datasetDocument, "USERS").getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 1, 1, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            final ICellEditor cellEditor = natTable.getActiveCellEditor();
            cellEditor.setEditorValue("10");

            cellEditor.commit(MoveDirectionEnum.DOWN);

            assertThat(natTable.getActiveCellEditor())
                    .as("Committing with Enter or Tab must move the selection without editing the next cell.")
                    .isNull();
            assertThat(document.get()).as("The committed value must reach the document.")
                    .isEqualTo("<dataset><USERS ID=\"10\"/><USERS ID=\"2\"/></dataset>");
        }

        @Test
        void testCommit_whenTheTextOfTheInPlaceEditorIsCleared_storesTheEmptyString()
        {
            final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
            final NatTable natTable = openGrid(create(document), "USERS").getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 2, 1, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            final ICellEditor cellEditor = natTable.getActiveCellEditor();
            cellEditor.setEditorValue("");

            cellEditor.commit(MoveDirectionEnum.NONE);

            assertThat(document.get())
                    .as("Clearing the text of a cell with a value must store the empty string, not NULL.")
                    .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"\"/></dataset>");
        }

        @Test
        void testCommit_whenTheInPlaceEditorOfANullCellIsUntouched_keepsItNull()
        {
            final String originalText =
                    "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>";
            final IDocument document = new Document(originalText);
            final NatTable natTable = openGrid(create(document), "USERS").getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            final ICellEditor cellEditor = natTable.getActiveCellEditor();

            cellEditor.commit(MoveDirectionEnum.NONE);

            assertThat(document.get())
                    .as("Committing the editor of a NULL cell without typing anything must keep it NULL.")
                    .isEqualTo(originalText);
        }

        @Test
        void testCommitActiveCellEditor_whileACellIsBeingEdited_writesItsValueAndClosesTheEditor()
        {
            final IDocument document = new Document("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>");
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS");
            final NatTable natTable = grid.getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 1, 2, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            natTable.getActiveCellEditor().setEditorValue("20");

            final boolean closed = grid.commitActiveCellEditor();

            assertThat(closed).as("Committing a valid value must close the editor.").isTrue();
            assertThat(natTable.getActiveCellEditor()).as("No cell editor may stay open.").isNull();
            assertThat(document.get()).as("The open editor's value must reach the document, as saving needs.")
                    .isEqualTo("<dataset><USERS ID=\"1\"/><USERS ID=\"20\"/></dataset>");
        }

        @Test
        void testCancelActiveCellEditor_whileACellIsBeingEdited_closesTheEditorWithoutWritingItsValue()
        {
            final String originalText = "<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>";
            final IDocument document = new Document(originalText);
            final DatasetGrid grid = openGrid(create(document), "USERS");
            final NatTable natTable = grid.getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 1, 2, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            natTable.getActiveCellEditor().setEditorValue("20");

            grid.cancelActiveCellEditor();

            assertThat(natTable.getActiveCellEditor()).as("No cell editor may stay open.").isNull();
            assertThat(document.get()).as("The value of a cancelled editor must not reach the document.")
                    .isEqualTo(originalText);
        }

        @Test
        void testCancelActiveCellEditor_whenNoCellIsBeingEdited_doesNothing()
        {
            final String originalText = "<dataset><USERS ID=\"1\"/></dataset>";
            final IDocument document = new Document(originalText);
            final DatasetGrid grid = openGrid(create(document), "USERS");

            grid.cancelActiveCellEditor();

            assertThat(document.get()).as("Cancelling without an open editor must leave the document alone.")
                    .isEqualTo(originalText);
        }

        @Test
        void testEditCellInDialog_whenAnEmptyStringIsConfirmedUnchanged_keepsTheEmptyString()
        {
            final String originalText = "<dataset><USERS ID=\"1\" NAME=\"\"/></dataset>";
            final IDocument document = new Document(originalText);
            final DatasetGrid grid = openGrid(create(document), "USERS");
            grid.selectRegion(1, 0, 1, 1);
            dialogDriver.confirmNextDialog();

            grid.editCellInDialog();

            assertThat(dialogDriver.hasConfirmed()).as("The dialog must open and be confirmed.").isTrue();
            assertThat(document.get())
                    .as("Confirming the dialog without a change must keep the empty string, not make it "
                            + "NULL.")
                    .isEqualTo(originalText);
        }

        @Test
        void testEditCellInDialog_whenTheDialogTextIsCleared_storesTheEmptyString()
        {
            final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
            final DatasetGrid grid = openGrid(create(document), "USERS");
            grid.selectRegion(1, 0, 1, 1);
            dialogDriver.confirmNextDialogWithText("");

            grid.editCellInDialog();

            assertThat(dialogDriver.hasConfirmed()).as("The dialog must open and be confirmed.").isTrue();
            assertThat(document.get())
                    .as("Clearing the text of a cell with a value must store the empty string, not NULL.")
                    .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"\"/></dataset>");
        }

        @Test
        void testEditCellInDialog_whenTheCellIsNullAndTheDialogIsConfirmedUnchanged_keepsItNull()
        {
            final String originalText =
                    "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>";
            final IDocument document = new Document(originalText);
            final DatasetGrid grid = openGrid(create(document), "USERS");
            grid.selectRegion(1, 1, 1, 1);
            dialogDriver.confirmNextDialog();

            grid.editCellInDialog();

            assertThat(dialogDriver.hasConfirmed()).as("The dialog must open and be confirmed.").isTrue();
            assertThat(document.get())
                    .as("Confirming the dialog of a NULL cell without typing anything must keep it NULL.")
                    .isEqualTo(originalText);
        }

        @Test
        void testEditCellInDialog_whenTheDialogTextIsChanged_storesTheNewValue()
        {
            final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
            final DatasetGrid grid = openGrid(create(document), "USERS");
            grid.selectRegion(1, 0, 1, 1);
            dialogDriver.confirmNextDialogWithText("Bob");

            grid.editCellInDialog();

            assertThat(dialogDriver.hasConfirmed()).as("The dialog must open and be confirmed.").isTrue();
            assertThat(document.get()).as("The text entered in the dialog must reach the document.")
                    .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Bob\"/></dataset>");
        }

        @Test
        void testTableChanged_whenARowAboveTheEditedCellIsRemoved_closesTheEditorWithoutWritingItsValue()
        {
            final IDocument document = new Document(FOUR_USERS);
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS");
            final NatTable natTable = grid.getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            natTable.getActiveCellEditor().setEditorValue("Zed");

            changeDocument(datasetDocument, document, grid, USERS_WITHOUT_ALICE);
            grid.commitActiveCellEditor();

            assertThat(natTable.getActiveCellEditor())
                    .as("A table whose rows changed must close the open editor.").isNull();
            assertThat(document.get())
                    .as("The value of Bob's cell must not land in the row that took Bob's place.")
                    .isEqualTo(USERS_WITHOUT_ALICE);
        }

        @Test
        void testTableChanged_whenTheEditedColumnIsRemoved_closesTheEditor()
        {
            final IDocument document = new Document(
                    "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS");
            final NatTable natTable = grid.getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            natTable.getActiveCellEditor().setEditorValue("Zed");
            final String changedText = "<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>";

            changeDocument(datasetDocument, document, grid, changedText);
            grid.commitActiveCellEditor();

            assertThat(natTable.getActiveCellEditor())
                    .as("A table that lost the edited column must close the editor.").isNull();
            assertThat(document.get()).as("The value of a column that is gone must not be written.")
                    .isEqualTo(changedText);
        }

        @Test
        void testTableChanged_whenTheEditorIsCancelled_tellsTheUserOnTheStatusLine()
        {
            final IDocument document = new Document(FOUR_USERS);
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final EditableGridContext context = new EditableGridContext(datasetDocument);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS", context);
            final NatTable natTable = grid.getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));

            changeDocument(datasetDocument, document, grid, USERS_WITHOUT_ALICE);

            assertThat(context.lastStatusMessage).as("The user must be told why the edit went away.")
                    .isEqualTo(EDIT_CANCELLED_MESSAGE);
        }

        @Test
        void testTableChanged_whenNoCellIsBeingEdited_leavesTheStatusLineAlone()
        {
            final IDocument document = new Document(FOUR_USERS);
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final EditableGridContext context = new EditableGridContext(datasetDocument);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS", context);

            changeDocument(datasetDocument, document, grid, USERS_WITHOUT_ALICE);

            assertThat(context.lastStatusMessage).as("Without an edit there is nothing to report.").isNull();
        }

        @Test
        void testTableChanged_afterTheDialogWasConfirmed_leavesTheStatusLineAlone()
        {
            final IDocument document = new Document(FOUR_USERS);
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final EditableGridContext context = new EditableGridContext(datasetDocument);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS", context);
            grid.selectRegion(1, 1, 1, 1);
            dialogDriver.confirmNextDialogWithText("Zed");
            grid.editCellInDialog();

            changeDocument(datasetDocument, document, grid, USERS_WITHOUT_ALICE);

            assertThat(context.lastStatusMessage).as("A dialog that is closed has no edit left to cancel.")
                    .isNull();
        }

        @Test
        void testTableChanged_whenOnlyAnotherValueChanges_keepsTheEditorOpenAndWritesItsValue()
        {
            final IDocument document = new Document(FOUR_USERS);
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS");
            final NatTable natTable = grid.getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            natTable.getActiveCellEditor().setEditorValue("Zed");

            changeDocument(datasetDocument, document, grid, FOUR_USERS.replace("Dave", "David"));

            assertThat(natTable.getActiveCellEditor())
                    .as("A change that keeps the table's rows and columns must not close the editor.")
                    .isNotNull();
            grid.commitActiveCellEditor();
            assertThat(document.get()).as("The edited cell must still get its value.")
                    .isEqualTo(FOUR_USERS.replace("Dave", "David").replace("Bob", "Zed"));
        }

        @Test
        void testEditCellInDialog_whenARowIsRemovedWhileTheDialogIsOpen_closesTheDialogWithoutWritingItsValue()
        {
            final IDocument document = new Document(FOUR_USERS);
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final EditableGridContext context = new EditableGridContext(datasetDocument);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS", context);
            grid.selectRegion(1, 1, 1, 1);
            dialogDriver.changeThenConfirmNextDialogWithText(
                    () -> changeDocument(datasetDocument, document, grid, USERS_WITHOUT_ALICE), "Zed");

            grid.editCellInDialog();

            assertThat(dialogDriver.hasOpened()).as("The dialog must open.").isTrue();
            assertThat(dialogDriver.hasConfirmed())
                    .as("The dialog of a cell whose table changed shape must be closed.").isFalse();
            assertThat(document.get()).as("The value must not land in the row that took Bob's place.")
                    .isEqualTo(USERS_WITHOUT_ALICE);
            assertThat(context.lastStatusMessage).as("The user must be told why the dialog went away.")
                    .isEqualTo(EDIT_CANCELLED_MESSAGE);
            assertThat(dialogCellEditor(grid).isClosed())
                    .as("The editor must be closed as the dialog's Cancel button closes it, to release its "
                            + "resources.")
                    .isTrue();
        }

        @Test
        void testEditCellInDialog_whenOnlyAnotherValueChangesWhileTheDialogIsOpen_stillStoresTheNewValue()
        {
            final IDocument document = new Document(FOUR_USERS);
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS");
            grid.selectRegion(1, 1, 1, 1);
            dialogDriver.changeThenConfirmNextDialogWithText(
                    () -> changeDocument(datasetDocument, document, grid,
                            FOUR_USERS.replace("Dave", "David")),
                    "Zed");

            grid.editCellInDialog();

            assertThat(dialogDriver.hasConfirmed())
                    .as("A change that keeps the table's rows and columns must not close the "
                            + "dialog.").isTrue();
            assertThat(document.get()).as("The edited cell must still get its value.")
                    .isEqualTo(FOUR_USERS.replace("Dave", "David").replace("Bob", "Zed"));
        }

        @Test
        void testEdit_whenAMultiLineCellOpensItsDialogAndARowIsRemoved_closesTheDialogWithoutWritingItsValue()
        {
            final IDocument document = new Document("<dataset><USERS ID=\"1\" NOTE=\"first&#xA;second\"/>"
                    + "<USERS ID=\"2\" NOTE=\"third&#xA;fourth\"/></dataset>");
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS");
            final NatTable natTable = grid.getNatTable();
            natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
            final String changedText = "<dataset><USERS ID=\"2\" NOTE=\"third&#xA;fourth\"/></dataset>";
            dialogDriver.changeThenConfirmNextDialogWithText(
                    () -> changeDocument(datasetDocument, document, grid, changedText), "other");

            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));

            assertThat(dialogDriver.hasOpened()).as("A multi-line value must open in the dialog.").isTrue();
            assertThat(dialogDriver.hasConfirmed())
                    .as("The dialog of a table that changed shape must be closed.")
                    .isFalse();
            assertThat(document.get()).as("The dialog's value must not be written.").isEqualTo(changedText);
        }

        @Test
        void testTableChanged_ofAnotherGridWhileTheDialogIsOpen_leavesTheDialogOpen()
        {
            final String originalText =
                    "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><ORDERS ID=\"1\"/></dataset>";
            final IDocument document = new Document(originalText);
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final DatasetGrid users = openGrid(datasetDocument, "USERS");
            final DatasetGrid orders = openGrid(datasetDocument, "ORDERS");
            users.selectRegion(1, 0, 1, 1);
            dialogDriver.changeThenConfirmNextDialogWithText(() ->
            {
                datasetDocument.insertRows("ORDERS", 1, List.of(List.of("2")));
                users.tableChanged(datasetDocument.getModel().findTable("USERS").orElseThrow());
                orders.tableChanged(datasetDocument.getModel().findTable("ORDERS").orElseThrow());
            }, "Bob");

            users.editCellInDialog();

            assertThat(dialogDriver.hasConfirmed())
                    .as("A change to another table's rows must not close this table's dialog.").isTrue();
            assertThat(document.get())
                    .as("Both the other table's new row and the dialog's value must be there.")
                    .contains("<USERS ID=\"1\" NAME=\"Bob\"/>", "<ORDERS ID=\"2\"/>");
        }

        @Test
        void testTableChanged_whenAMultiLineCellIsEditedInPlace_closesTheEditorAndLeavesTheWindowOpen()
        {
            final IDocument document = new Document("<dataset><USERS ID=\"1\" NOTE=\"first&#xA;second\"/>"
                    + "<USERS ID=\"2\" NOTE=\"third&#xA;fourth\"/></dataset>");
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final DatasetGrid grid = openGrid(datasetDocument, "USERS");
            final NatTable natTable = grid.getNatTable();
            natTable.getConfigRegistry().registerConfigAttribute(EditConfigAttributes.OPEN_IN_DIALOG,
                    Boolean.FALSE, DisplayMode.EDIT, DatasetCellLabels.MULTI_LINE_VALUE);
            natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            final String changedText = "<dataset><USERS ID=\"2\" NOTE=\"third&#xA;fourth\"/></dataset>";

            changeDocument(datasetDocument, document, grid, changedText);

            assertThat(natTable.getActiveCellEditor())
                    .as("The editor of a table that changed shape must close.")
                    .isNull();
            assertThat(shell.isDisposed())
                    .as("Closing an editor that is not in a dialog must not close a window.").isFalse();
        }

        @Test
        void testDispose_whileTheDialogIsOpen_closesTheDialogWithoutWritingItsValue()
        {
            final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
            final IDocument document = new Document(originalText);
            final DatasetGrid grid = openGrid(create(document), "USERS");
            grid.selectRegion(1, 0, 1, 1);
            dialogDriver.changeThenConfirmNextDialogWithText(() -> grid.getControl().dispose(), "Bob");

            grid.editCellInDialog();

            assertThat(dialogDriver.hasOpened()).as("The dialog must open.").isTrue();
            assertThat(dialogDriver.hasConfirmed()).as("The dialog of a grid that is gone must be closed.")
                    .isFalse();
            assertThat(document.get()).as("The dialog's value must not be written.").isEqualTo(originalText);
        }

        @Test
        void testAddCellEditorListener_whenAnEditorOpensAndIsCommitted_reportsTheEditorStateAtEachStep()
        {
            final FlatXmlDatasetDocument datasetDocument = create(new Document(FOUR_USERS));
            final DatasetGrid grid = openGrid(datasetDocument, "USERS");
            final NatTable natTable = grid.getNatTable();
            final List<Boolean> editorOpenAtEachCall = new ArrayList<>();
            grid.addCellEditorListener(
                    () -> editorOpenAtEachCall.add(natTable.getActiveCellEditor() != null));
            natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));

            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            natTable.commitAndCloseActiveCellEditor();

            assertThat(editorOpenAtEachCall)
                    .as("A listener must find the editor active when it opens and gone when it closes.")
                    .containsExactly(true, false);
        }

        @Test
        void testAddCellEditorListener_whenAnEditorOpensAndIsCancelled_reportsTheEditorStateAtEachStep()
        {
            final FlatXmlDatasetDocument datasetDocument = create(new Document(FOUR_USERS));
            final DatasetGrid grid = openGrid(datasetDocument, "USERS");
            final NatTable natTable = grid.getNatTable();
            final List<Boolean> editorOpenAtEachCall = new ArrayList<>();
            grid.addCellEditorListener(
                    () -> editorOpenAtEachCall.add(natTable.getActiveCellEditor() != null));
            natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));

            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
            grid.cancelActiveCellEditor();

            assertThat(editorOpenAtEachCall)
                    .as("A listener must find the editor active when it opens and gone when it is cancelled.")
                    .containsExactly(true, false);
        }

        @Test
        void testAddCellEditorListener_whenTheGridIsDisposedWithAnOpenEditor_reportsNoClosing()
        {
            final FlatXmlDatasetDocument datasetDocument = create(new Document(FOUR_USERS));
            final DatasetGrid grid = openGrid(datasetDocument, "USERS");
            final NatTable natTable = grid.getNatTable();
            final List<Boolean> editorOpenAtEachCall = new ArrayList<>();
            grid.addCellEditorListener(
                    () -> editorOpenAtEachCall.add(natTable.getActiveCellEditor() != null));
            natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
            natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));

            natTable.dispose();

            assertThat(editorOpenAtEachCall)
                    .as("An editor that goes away with its grid must not be reported, as the grid is gone.")
                    .containsExactly(true);
        }

        /**
         * Replaces the document's text as a reload from disk does, then refreshes the model and the grid as
         * the Tables page does after a text change.
         */
        private static void changeDocument(final FlatXmlDatasetDocument datasetDocument,
                final IDocument document, final DatasetGrid grid, final String newText)
        {
            document.set(newText);
            datasetDocument.refresh();
            final String tableKey = grid.getBodyDataProvider().getTableKey();
            grid.tableChanged(datasetDocument.getModel().findTable(tableKey).orElseThrow());
        }

        private static ICellEditor dialogCellEditor(final DatasetGrid grid)
        {
            return grid.getNatTable().getConfigRegistry().getConfigAttribute(EditConfigAttributes.CELL_EDITOR,
                    DisplayMode.EDIT, DatasetCellLabels.EDIT_IN_DIALOG);
        }

        private DatasetGrid openGrid(final FlatXmlDatasetDocument datasetDocument, final String tableKey)
        {
            return openGrid(datasetDocument, tableKey, new EditableGridContext(datasetDocument));
        }

        private DatasetGrid openGrid(final FlatXmlDatasetDocument datasetDocument, final String tableKey,
                final EditableGridContext context)
        {
            shell.setSize(400, 300);
            final DatasetGrid grid = new DatasetGrid(shell, context, tableKey);
            grid.tableChanged(datasetDocument.getModel().findTable(tableKey).orElseThrow());
            grid.getControl().setBounds(shell.getClientArea());
            shell.open();
            final Display display = Display.getCurrent();
            while (display.readAndDispatch())
            {
                // Let NatTable lay out before the test edits its cells.
            }
            return grid;
        }

        private static FlatXmlDatasetDocument create(final IDocument document)
        {
            final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document,
                    DtdSource.NONE, FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
            datasetDocument.refresh();
            return datasetDocument;
        }
    }
}
