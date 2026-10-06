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

import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.edit.ChangeOrigin;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PasteAction} against the Clipboard specification: pasting a block, a single value, and a
 * text with blank lines, into existing rows and into a table that has no rows, and what is pasted after
 * a copy.
 */
class PasteActionTest extends GridActionFixture
{
    @Test
    void testCopyThenPaste_ofNonAdjacentRowsOntoTheLastRow_pastesThemOneAfterTheOther()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 2);
        context.columnIndexes = List.of(0, 1);
        context.wholeRowsSelected = true;
        context.selectedCellPositions =
                List.of(new Point(0, 0), new Point(1, 0), new Point(0, 2), new Point(1, 2));
        new CopyAction(context).run();
        context.rowIndexes = List.of(2);
        context.columnIndexes = List.of(0);
        context.wholeRowsSelected = false;
        context.selectedCellPositions = List.of(new Point(0, 2));

        new PasteAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("The first copied row must overwrite the last row and the second must be appended, "
                        + "with no empty row between them.")
                .containsExactly(List.of("1", "Alice"), List.of("2", "Bob"), List.of("1", "Alice"),
                        List.of("3", "Carol"));
        assertThat(context.statusErrorMessage).as("Nothing may be rejected.").isNull();
    }

    @Test
    void testCopyThenPaste_ofNonAdjacentCellsOfOneColumn_writesNoNullBetweenTheTargets()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 2);
        context.columnIndexes = List.of(0);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(0, 2));
        new CopyAction(context).run();
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0));

        new PasteAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValue(1))
                .as("The two copied cells must go into the next two cells of the target column, and the "
                        + "third cell must stay as it was.")
                .containsExactly("1", "3", "Carol");
    }

    @Test
    void testCopyThenPaste_ofAnEmptyStringCell_preservesItAsEmptyStringNotNull()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0));
        new SetEmptyStringAction(context).run();

        new CopyAction(context).run();
        context.rowIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 1));
        new PasteAction(context).run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(1).getValue(1))
                .as("Pasting a copied empty-string cell must set the empty string, not NULL.")
                .isEqualTo("");
    }

    @Test
    void testPaste_ofABlockAtTheLastRow_appendsOneRowInOneUndoStep() throws ExecutionException
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        DocumentUndoManagerRegistry.connect(document);
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(this);
        try
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final TestContext context = new TestContext(datasetDocument, "USERS");
            context.rowIndexes = List.of(0);
            context.columnIndexes = List.of(0, 1);
            context.clipboardText = "10\tX\n20\tY\n30\tZ\n";
            final PasteAction action = new PasteAction(context);
            final List<ChangeOrigin> modelRebuilds = new ArrayList<>();
            datasetDocument.addModelListener(event -> modelRebuilds.add(event.origin()));

            action.run();

            assertThat(modelRebuilds)
                    .as("A paste that changes a row and appends rows must rebuild the model once.")
                    .containsExactly(ChangeOrigin.EDIT);
            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getRows()).as("A 3-row paste at the last row must append two rows.")
                    .hasSize(3);
            assertThat(table.getRows().get(0).getValue(0)).isEqualTo("10");
            assertThat(table.getRows().get(0).getValue(1)).isEqualTo("X");
            assertThat(table.getRows().get(1).getValue(0)).isEqualTo("20");
            assertThat(table.getRows().get(1).getValue(1)).isEqualTo("Y");
            assertThat(table.getRows().get(2).getValue(0)).isEqualTo("30");
            assertThat(table.getRows().get(2).getValue(1)).isEqualTo("Z");
            assertThat(context.selectedRegion).as("Paste must select the pasted block.")
                    .isEqualTo(new Rectangle(0, 0, 2, 3));

            undoManager.undo();
            datasetDocument.refresh();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .as("The existing-row change and the appended rows must be one undo step.").hasSize(1);
            assertThat(context.multiCellEditTitles)
                    .as("Paste must report a rejected edit through a modal dialog, not just the status "
                            + "line.")
                    .containsExactly("Paste");
        }
        finally
        {
            undoManager.disconnect(this);
            DocumentUndoManagerRegistry.disconnect(document);
        }
    }

    @Test
    void testPaste_ofASingleValueOntoAMultiCellSelection_fillsEverySelectedCell()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0), new Point(1, 1));
        context.clipboardText = "X";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(0).getValue(1))
                .as("A single pasted value onto a multi-cell selection must fill every selected cell.")
                .isEqualTo("X");
        assertThat(table.getRows().get(1).getValue(1)).isEqualTo("X");
        assertThat(context.multiCellEditTitles).containsExactly("Paste");
    }

    @Test
    void testPaste_ofASingleValueOntoAMultiCellSelection_listsTheSelectedCellsOnce()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0), new Point(1, 1));
        context.clipboardText = "X";

        new PasteAction(context).run();

        assertThat(context.selectedCellPositionReads)
                .as("The cells to fill are listed once, to fill them, and not again to find out that there "
                        + "are several.")
                .isEqualTo(1);
    }

    @Test
    void testPaste_ofASingleValueOntoTwoCellsOfOneRow_fillsBothCells()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 1), new Point(1, 1));
        context.clipboardText = "X";

        new PasteAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("Two selected cells in one row are several cells, so the value must fill both of them "
                        + "and leave the other row alone.")
                .containsExactly(List.of("1", "Alice"), List.of("X", "X"));
    }

    @Test
    void testPaste_ofASingleValueOntoOneCell_pastesItWithoutListingTheSelectedCells()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 1));
        context.clipboardText = "X";

        new PasteAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("The value must go into the one selected cell.")
                .containsExactly(List.of("1", "Alice"), List.of("2", "X"));
        assertThat(context.selectedCellPositionReads)
                .as("One cell is the anchor of a block, and the snapshot of the selection says so, so "
                        + "the paste must not list the selected cells.")
                .isZero();
    }

    @Test
    void testPaste_ofABlockOntoAMultiCellSelection_pastesItAtTheAnchorWithoutListingTheSelectedCells()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0, 1, 2);
        context.columnIndexes = List.of(0, 1);
        context.selectedCellPositions = List.of(new Point(0, 0), new Point(0, 1), new Point(0, 2),
                new Point(1, 0), new Point(1, 1), new Point(1, 2));
        context.clipboardText = "10\tX\n20\tY\n";

        new PasteAction(context).run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("The block must go in at the top-left cell of the selection, and the third row stays.")
                .containsExactly(List.of("10", "X"), List.of("20", "Y"), List.of("3", "Carol"));
        assertThat(context.selectedCellPositionReads)
                .as("A block goes in at the anchor, so the paste must read the snapshot of the selection "
                        + "and not list every selected cell, which is a million for a big table.")
                .isZero();
    }

    @Test
    void testPaste_withMorePastedColumnsThanFit_ignoresThemWithAStatusMessage()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0);
        context.clipboardText = "10\tX\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0).getValue(0))
                .as("A column that fits must still be pasted.").isEqualTo("10");
        assertThat(context.statusMessage)
                .as("A pasted column beyond the table's last column must be ignored with a status message.")
                .isEqualTo("Ignored 1 pasted column beyond the table's last column.");
    }

    @Test
    void testPaste_withNoValidAnchorOnANonEmptyTable_doesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.clipboardText = "X\tY\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows())
                .as("A paste with no valid anchor on a non-empty table must do nothing, not overwrite "
                        + "row 0.")
                .hasSize(1);
        assertThat(table.getRows().get(0).getValue(0)).isEqualTo("1");
        assertThat(table.getRows().get(0).getValue(1)).isEqualTo("Alice");
    }

    @Test
    void testPaste_forAnEmptyTable_isEnabledOnlyWithColumns()
    {
        final FlatXmlDatasetDocument withColumns = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
        final TestContext withColumnsContext = new TestContext(withColumns, "USERS");
        final PasteAction withColumnsAction = new PasteAction(withColumnsContext);

        withColumnsAction.update(withColumnsContext.getSelection());
        assertThat(withColumnsAction.isEnabled())
                .as("An empty table with columns must enable Paste whatever the clipboard holds.")
                .isTrue();

        final FlatXmlDatasetDocument withoutColumns = create("<dataset><NONE/></dataset>");
        final TestContext withoutColumnsContext = new TestContext(withoutColumns, "NONE");
        withoutColumnsContext.clipboardText = "1\n";
        final PasteAction withoutColumnsAction = new PasteAction(withoutColumnsContext);

        withoutColumnsAction.update(withoutColumnsContext.getSelection());
        assertThat(withoutColumnsAction.isEnabled())
                .as("A table without columns must stay disabled for Paste even with clipboard text.")
                .isFalse();
    }

    @Test
    void testPaste_forAnEmptyTableWithAnEmptyClipboard_doesNothing()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).as("Paste with an empty clipboard must not add a row.").isEmpty();
    }

    @Test
    void testPaste_of2x2BlockIntoAnEmpty3ColumnTable_appendsTwoRowsFromColumnZeroInOneUndoStep()
            throws ExecutionException
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED CITY CDATA #IMPLIED>\n]>\n"
                        + "<dataset>\n</dataset>\n");
        DocumentUndoManagerRegistry.connect(document);
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(this);
        try
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            final TestContext context = new TestContext(datasetDocument, "USERS");
            context.clipboardText = "1\tAlice\n2\tBob\n";
            final PasteAction action = new PasteAction(context);
            final List<ChangeOrigin> modelRebuilds = new ArrayList<>();
            datasetDocument.addModelListener(event -> modelRebuilds.add(event.origin()));

            action.run();

            assertThat(modelRebuilds).as("Pasting into an empty table must rebuild the model once.")
                    .containsExactly(ChangeOrigin.EDIT);
            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getRows()).as("The paste must append two rows.").hasSize(2);
            assertThat(table.getRows().get(0).getValue(0)).isEqualTo("1");
            assertThat(table.getRows().get(0).getValue(1)).isEqualTo("Alice");
            assertThat(table.getRows().get(1).getValue(0)).isEqualTo("2");
            assertThat(table.getRows().get(1).getValue(1)).isEqualTo("Bob");
            assertThat(context.selectedRegion).as("Paste must select the pasted block.")
                    .isEqualTo(new Rectangle(0, 0, 2, 2));

            undoManager.undo();
            datasetDocument.refresh();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows())
                    .as("Appending the pasted rows must be one undo step.").isEmpty();
        }
        finally
        {
            undoManager.disconnect(this);
            DocumentUndoManagerRegistry.disconnect(document);
        }
    }

    @Test
    void testPaste_ofABlankLineBetweenRowsToAppend_skipsItAndAppendsTheOtherRows()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(0, 1);
        context.clipboardText = "10\tX\n\n30\tZ\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("A pasted blank line cannot become a row, so the rows around it must be pasted without "
                        + "it.")
                .containsExactly(List.of("10", "X"), List.of("30", "Z"));
        assertThat(context.selectedRegion).as("Paste must select the rows it wrote.")
                .isEqualTo(new Rectangle(0, 0, 2, 2));
        assertThat(context.statusMessage).as("Paste must say that it skipped a row.")
                .isEqualTo("Skipped 1 pasted row without values, because a new row needs at least one "
                        + "value.");
    }

    @Test
    void testPaste_ofTextEndingInABlankLineIntoAnEmptyTable_appendsTheRowsAndSkipsTheBlankLine()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset>\n</dataset>\n");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.clipboardText = "1\tAlice\n2\tBob\n\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("A trailing blank line must not fail the paste of the rows before it.")
                .containsExactly(List.of("1", "Alice"), List.of("2", "Bob"));
        assertThat(context.selectedRegion).as("Paste must select the rows it wrote, not the skipped one.")
                .isEqualTo(new Rectangle(0, 0, 2, 2));
        assertThat(context.statusMessage).as("Paste must say that it skipped a row.")
                .isEqualTo("Skipped 1 pasted row without values, because a new row needs at least one "
                        + "value.");
    }

    @Test
    void testPaste_whenEveryRowToAppendHasNoValue_changesNothingAndSaysWhy()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset>\n</dataset>\n");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.clipboardText = "\n\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).as("Blank lines must not add rows.").isEmpty();
        assertThat(context.multiCellEditTitles).as("A paste with nothing to write must not run an edit.")
                .isEmpty();
        assertThat(context.selectedRegion).as("A paste that wrote nothing must not select anything.")
                .isNull();
        assertThat(context.statusMessage).as("Paste must say why nothing was added.")
                .isEqualTo("Skipped 2 pasted rows without values, because a new row needs at least one "
                        + "value.");
    }

    @Test
    void testPaste_whenOnlyValuesBeyondTheLastColumnWouldMakeUpARowToAppend_reportsBothOmissions()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        context.clipboardText = "A\tB\n\tD\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows()).extracting(row -> row.getValues())
                .as("The value that fits must be pasted, and the row left without values must be skipped.")
                .containsExactly(List.of("1", "A"));
        assertThat(context.statusMessage).as("Paste must report the ignored column and the skipped row.")
                .isEqualTo("Ignored 1 pasted column beyond the table's last column. Skipped 1 pasted row "
                        + "without values, because a new row needs at least one value.");
    }

    @Test
    void testPaste_ofACopiedNullCellOntoAnExistingCell_setsItToNull()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(1);
        context.columnIndexes = List.of(1);
        context.clipboardText = "\n";
        final PasteAction action = new PasteAction(context);

        action.run();

        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getRows().get(1).getValues())
                .as("A blank line is a NULL cell, which Copy writes for one, so Paste must set the target "
                        + "cell to NULL.")
                .containsExactly("2", null);
        assertThat(context.statusMessage).as("Pasting onto an existing row skips nothing.").isNull();
    }
}
