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
package org.dbunit.eclipse.dataset.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.text.Document;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.edit.command.EditSelectionCommand;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectCellCommand;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ActiveGrid}: the selection of the grid of the selected tab, the cells and regions that it
 * selects, the cell editor that is open in it, and the address of its anchor cell.
 */
class ActiveGridTest
{
    private static final String USERS =
            "<dataset><USERS ID=\"1\" NAME=\"a\"/><USERS ID=\"2\" NAME=\"b\"/></dataset>";

    private Shell shell;

    private Document document;

    private FlatXmlDatasetDocument datasetDocument;

    private CTabFolder tabFolder;

    private TableTabs tabs;

    private ActiveGrid activeGrid;

    @BeforeEach
    void createActiveGrid()
    {
        shell = new Shell(Display.getDefault());
        shell.setLayout(new FillLayout());
        shell.setSize(400, 300);
        document = new Document("<dataset/>");
        datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE, FlatXmlOptions.DBUNIT_DEFAULTS,
                () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        tabFolder = new CTabFolder(shell, SWT.TOP | SWT.BORDER | SWT.FLAT);
        final LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources(), shell);
        tabs = new TableTabs(tabFolder, new StubGridContext(datasetDocument), resources, () ->
        {
            // The tests ask for the selection when they need it.
        });
        activeGrid = new ActiveGrid(tabFolder, tabs::activeGrid);
        shell.open();
        UiTestWorkspace.processEvents();
    }

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testGetSelection_whenNoTabExists_isNone()
    {
        assertThat(activeGrid.getSelection()).as("Without a grid nothing is selected.")
                .isEqualTo(GridSelection.NONE);
    }

    @Test
    void testGetSelection_whenATabIsSelected_isTheSelectionOfItsGrid()
    {
        show(USERS);

        assertThat(activeGrid.getSelection()).as("The first cell is selected when the grid opens.")
                .isEqualTo(new GridSelection("USERS", 2, 2, 0, 0, List.of(0), List.of(0), 0, 0, 0, 0, false));
    }

    @Test
    void testSelectRegion_whenATabIsSelected_selectsTheRegionOfItsGrid()
    {
        show(USERS);

        activeGrid.selectRegion(0, 0, 2, 2);

        assertThat(activeGrid.getSelection()).as("Both rows and both columns must be selected.")
                .isEqualTo(new GridSelection("USERS", 2, 2, 0, 0, List.of(0, 1), List.of(0, 1), 0, 1, 0, 1,
                        true));
    }

    @Test
    void testSelectRegion_whenNoTabExists_doesNothing()
    {
        activeGrid.selectRegion(0, 0, 2, 2);

        assertThat(activeGrid.getSelection()).as("Without a grid nothing is selected.")
                .isEqualTo(GridSelection.NONE);
    }

    @Test
    void testGetSelectedCellPositions_afterSelectingARegion_areTheColumnAndRowOfEachCell()
    {
        show(USERS);

        activeGrid.selectRegion(0, 1, 2, 1);

        assertThat(activeGrid.getSelectedCellPositions()).as("The cells of the region must be listed.")
                .containsExactlyInAnyOrder(new Point(0, 1), new Point(1, 1));
    }

    @Test
    void testGetSelectedCellPositions_whenNoTabExists_isEmpty()
    {
        assertThat(activeGrid.getSelectedCellPositions()).as("Without a grid no cell is selected.").isEmpty();
    }

    @Test
    void testSelectAll_whenATabIsSelected_selectsEveryCellOfItsGrid()
    {
        show(USERS);

        activeGrid.selectAll();

        assertThat(activeGrid.getSelectedCellPositions()).as("Every cell must be selected.")
                .containsExactlyInAnyOrder(new Point(0, 0), new Point(1, 0), new Point(0, 1),
                        new Point(1, 1));
    }

    @Test
    void testSelectAll_whenNoTabExists_doesNothing()
    {
        activeGrid.selectAll();

        assertThat(activeGrid.getSelectedCellPositions()).as("Without a grid no cell is selected.").isEmpty();
    }

    @Test
    void testEditCellInDialog_whenNoTabExists_doesNothing()
    {
        activeGrid.editCellInDialog();

        assertThat(activeGrid.hasActiveCellEditor()).as("Without a grid no editor opens.").isFalse();
    }

    @Test
    void testHasActiveCellEditor_whenNoTabExists_isFalse()
    {
        assertThat(activeGrid.hasActiveCellEditor()).as("Without a grid no cell is edited.").isFalse();
    }

    @Test
    void testHasActiveCellEditor_whenNoCellIsBeingEdited_isFalse()
    {
        show(USERS);

        assertThat(activeGrid.hasActiveCellEditor()).as("No editor is open.").isFalse();
    }

    @Test
    void testHasActiveCellEditor_whileACellIsBeingEdited_isTrue()
    {
        show(USERS);
        editCell(1, 2);

        assertThat(activeGrid.hasActiveCellEditor()).as("An editor is open.").isTrue();
    }

    @Test
    void testHasActiveCellEditor_whenTheSelectedTabHoldsNoGrid_isFalse()
    {
        final CTabItem item = new CTabItem(tabFolder, SWT.NONE);
        item.setControl(new Composite(tabFolder, SWT.NONE));
        tabFolder.setSelection(item);

        assertThat(activeGrid.hasActiveCellEditor()).as("Only a grid has cell editors.").isFalse();
    }

    @Test
    void testGetActiveCellEditorText_whenNoTabExists_isNull()
    {
        assertThat(activeGrid.getActiveCellEditorText()).as("Without a grid no cell is edited.").isNull();
    }

    @Test
    void testGetActiveCellEditorText_whenNoCellIsBeingEdited_isNull()
    {
        show(USERS);

        assertThat(activeGrid.getActiveCellEditorText()).as("No editor is open.").isNull();
    }

    @Test
    void testGetActiveCellEditorText_whileACellIsBeingEdited_isTheTextOfTheEditor()
    {
        show(USERS);
        final NatTable natTable = editCell(1, 2);
        natTable.getActiveCellEditor().setEditorValue("20");

        final Text text = activeGrid.getActiveCellEditorText();

        assertThat(text.getText()).as("The editor's text must be the value being edited.").isEqualTo("20");
    }

    @Test
    void testGetActiveCellEditorText_whenTheSelectedTabHoldsNoGrid_isNull()
    {
        final CTabItem item = new CTabItem(tabFolder, SWT.NONE);
        item.setControl(new Composite(tabFolder, SWT.NONE));
        tabFolder.setSelection(item);

        assertThat(activeGrid.getActiveCellEditorText()).as("Only a grid has cell editors.").isNull();
    }

    @Test
    void testCurrentCellAddress_whenNoTabExists_isNull()
    {
        assertThat(activeGrid.currentCellAddress()).as("Without a grid there is no anchor cell.").isNull();
    }

    @Test
    void testCurrentCellAddress_whenTheGridHasAnAnchorCell_isItsAddress()
    {
        show(USERS);

        activeGrid.selectRegion(1, 0, 1, 1);

        assertThat(activeGrid.currentCellAddress()).as("The address must name the table, row, and column.")
                .isEqualTo(new CellAddress("USERS", 0, 1));
    }

    @Test
    void testCurrentCellAddress_whenTheTableHasNoRows_isNull()
    {
        show("<dataset><EMPTY/></dataset>");

        assertThat(activeGrid.currentCellAddress()).as("A table without rows has no anchor cell.").isNull();
    }

    private void show(final String content)
    {
        document.set(content);
        datasetDocument.refresh();
        tabs.reconcile(datasetDocument.getModel());
        shell.layout(true, true);
        UiTestWorkspace.processEvents();
    }

    private NatTable editCell(final int columnPosition, final int rowPosition)
    {
        final NatTable natTable = (NatTable) tabFolder.getSelection().getControl();
        natTable.doCommand(new SelectCellCommand(natTable, columnPosition, rowPosition, false, false));
        natTable.doCommand(new EditSelectionCommand(natTable, natTable.getConfigRegistry()));
        return natTable;
    }
}
