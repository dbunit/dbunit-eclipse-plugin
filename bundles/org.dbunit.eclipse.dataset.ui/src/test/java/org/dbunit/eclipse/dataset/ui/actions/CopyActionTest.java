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
package org.dbunit.eclipse.dataset.ui.actions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link CopyAction} against the Clipboard specification: what is copied from a block and from a
 * selection that is not a block, the refusal of a selection whose rows have different columns, the
 * default value of a cell, and the text of a cell editor.
 */
class CopyActionTest extends GridActionFixture
{
    @Test
    void testCopy_ofA2x2Range_putsTabSeparatedTextOnTheClipboard()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(1, 0), new Point(0, 1), new Point(1, 1));
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText).as("Copy must put the range as tab-separated text.")
                .isEqualTo("1\tAlice" + System.lineSeparator() + "2\tBob" + System.lineSeparator());
    }

    @Test
    void testCopy_ofNonAdjacentWholeRows_copiesOnlyTheSelectedRows()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 2);
        context.columnIndexes = List.of(0, 1);
        context.wholeRowsSelected = true;
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(1, 0), new Point(0, 2), new Point(1, 2));
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText)
                .as("The row between two rows selected with Ctrl is not selected, so it must not be "
                        + "copied as an empty row.")
                .isEqualTo("1\tAlice" + System.lineSeparator() + "3\tCarol" + System.lineSeparator());
        assertThat(context.statusErrorMessage).as("Rows that line up are copyable.").isNull();
    }

    @Test
    void testCopy_ofNonAdjacentColumns_copiesOnlyTheSelectedColumns()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset>"
                + "<USERS ID=\"1\" NAME=\"Alice\" CITY=\"Rome\"/><USERS ID=\"2\" NAME=\"Bob\" CITY=\"Paris\"/>"
                + "</dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 2);
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(2, 0), new Point(0, 1), new Point(2, 1));
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText)
                .as("The column between two columns selected with Ctrl is not selected, so it must not be "
                        + "copied as an empty column.")
                .isEqualTo("1\tRome" + System.lineSeparator() + "2\tParis" + System.lineSeparator());
    }

    @Test
    void testCopy_ofNonAdjacentCellsInOneColumn_copiesThemNextToEachOther()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 2);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0), new Point(1, 2));
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText)
                .as("Two cells of one column selected with Ctrl must copy as two adjacent cells.")
                .isEqualTo("Alice" + System.lineSeparator() + "Carol" + System.lineSeparator());
    }

    @Test
    void testCopy_whenTheSelectedRowsHaveDifferentSelectedColumns_refusesAndLeavesTheClipboardAlone()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(1, 1));
        context.clipboardText = "kept";
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(context.clipboardText)
                .as("A selection with no sensible rectangle must copy nothing, not a rectangle with empty "
                        + "cells in the gaps, which pastes as NULL.")
                .isEqualTo("kept");
        assertThat(context.statusErrorMessage).as("Copy must say why it copied nothing.")
                .isEqualTo("Cannot copy or cut this selection, because its rows have different selected "
                        + "columns. Select the same columns in every selected row.");
    }

    @Test
    void testCopy_whileACellEditorIsActive_actsOnTheEditorsTextInsteadOfTheGrid()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.hasActiveCellEditor = true;
        final Text text = new Text(shell, SWT.NONE);
        text.setText("hello world");
        text.setSelection(0, 5);
        context.activeCellEditorText = text;
        final CopyAction action = new CopyAction(context);

        action.run();

        assertThat(text.getSelectionText()).as("The editor's own selection is what Copy must act on.")
                .isEqualTo("hello");
        assertThat(context.clipboardText)
                .as("With an active cell editor, Copy must not use the grid-level clipboard write.")
                .isNull();
    }

    @Test
    void testUpdate_forCopy_staysEnabledWhileACellEditorIsActive()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0);
        context.hasActiveCellEditor = true;
        final CopyAction action = new CopyAction(context);

        action.update(context.getSelection());

        assertThat(action.isEnabled()).as("Copy must stay enabled while a cell editor is active.").isTrue();
    }

    @Test
    void testCopy_ofACellThatShowsADefaultValue_copiesTheDefault()
    {
        final FlatXmlDatasetDocument datasetDocument = create(DEFAULTS_DATASET);
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(1, 0));

        new CopyAction(context).run();

        assertThat(context.clipboardText)
                .as("Copy must put the value dbUnit loads, so a cell without a value copies its default.")
                .isEqualTo("1\tACTIVE" + System.lineSeparator());
    }
}
