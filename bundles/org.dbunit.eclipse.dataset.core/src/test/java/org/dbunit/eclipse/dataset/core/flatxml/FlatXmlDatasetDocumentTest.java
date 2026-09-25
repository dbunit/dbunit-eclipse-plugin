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
package org.dbunit.eclipse.dataset.core.flatxml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.eclipse.dataset.core.TestDatasets;
import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.edit.ChangeOrigin;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.DocumentRewriteSessionEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlDatasetDocument} against the staleness, refresh, and navigation rules of the Edit
 * Engine specification, using a plain {@link Document} (no workbench needed).
 */
class FlatXmlDatasetDocumentTest
{
    @Test
    void testDocumentChanged_whenTheDocumentChanges_marksTheModelStaleWithoutRefreshing()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        document.replace(0, 0, "<!-- comment -->");

        assertThat(datasetDocument.isStale()).as("Any document change must mark the model stale.")
                .isTrue();
        assertThat(datasetDocument.getModel().getTables()).as("getModel must never reparse.").hasSize(1);
    }

    @Test
    void testRefresh_whenStale_notifiesListenersOnceWithDocumentOrigin()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final AtomicInteger notifications = new AtomicInteger();
        final List<ChangeOrigin> origins = new ArrayList<>();
        datasetDocument.addModelListener(event ->
        {
            notifications.incrementAndGet();
            origins.add(event.origin());
        });

        datasetDocument.refresh();

        assertThat(notifications.get()).as("refresh() on a stale model must notify exactly once.")
                .isEqualTo(1);
        assertThat(origins).as("A plain refresh must carry ChangeOrigin.DOCUMENT.")
                .containsExactly(ChangeOrigin.DOCUMENT);
    }

    @Test
    void testRefresh_whenNotStale_doesNotNotify()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();
        final AtomicInteger notifications = new AtomicInteger();
        datasetDocument.addModelListener(event -> notifications.incrementAndGet());

        datasetDocument.refresh();

        assertThat(notifications.get()).as("refresh() must do nothing when the model is not stale.")
                .isEqualTo(0);
    }

    @Test
    void testCreateEmptyDataset_whenDocumentIsBlank_replacesItAndUndoRestoresTheBlankText()
            throws Exception
    {
        final IDocument document = new Document("   ");
        DocumentUndoManagerRegistry.connect(document);
        final IDocumentUndoManager undoManager =
                DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(this);
        try
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.createEmptyDataset();

            assertThat(document.get()).as("createEmptyDataset must replace the blank text.")
                    .contains("<dataset>").contains("</dataset>");
            assertThat(undoManager.undoable()).as("The replacement must be one undoable change.")
                    .isTrue();

            undoManager.undo();

            assertThat(document.get()).as("Undo must restore the original blank text.").isEqualTo("   ");
        }
        finally
        {
            undoManager.disconnect(this);
            DocumentUndoManagerRegistry.disconnect(document);
        }
    }

    @Test
    void testCreateEmptyDataset_whenDocumentDelimiterIsCrLf_writesTheEmptyDatasetTextWithCrLf()
    {
        final Document document = new Document("");
        document.setInitialLineDelimiter("\r\n");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.createEmptyDataset();

        assertThat(document.get()).as("createEmptyDataset must end every line with the document's delimiter.")
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<dataset>\r\n</dataset>\r\n");
    }

    @Test
    void testEmptyDatasetText_withLf_returnsTheXmlDeclarationAndAnEmptyDatasetRoot()
    {
        final String text = FlatXmlDatasetDocument.emptyDatasetText("\n");

        assertThat(text).as("The empty dataset must be the UTF-8 declaration and an empty dataset root.")
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dataset>\n</dataset>\n");
    }

    @Test
    void testCreateEmptyDataset_whenDocumentIsNotBlank_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(datasetDocument::createEmptyDataset)
                .as("createEmptyDataset must reject a non-blank document.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo("<dataset></dataset>");
    }

    @Test
    void testSetOptions_whenCalled_refreshesTheModel()
    {
        final IDocument document =
                new Document("<dataset><USERS ID=\"1\"/><users ID=\"2\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();
        assertThat(datasetDocument.getModel().getTables()).as("Case-insensitive by default: one table.")
                .hasSize(1);

        datasetDocument.setOptions(new FlatXmlOptions(true, false));

        assertThat(datasetDocument.getModel().getTables())
                .as("setOptions must refresh immediately with the new options.").hasSize(2);
    }

    @Test
    void testReloadDtd_whenTheCachedDtdTextChanges_refreshes()
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><USERS ID=\"1\"/></dataset>");
        final MutableDtdSource dtdSource = new MutableDtdSource(
                "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY><!ATTLIST USERS ID CDATA #REQUIRED>");
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, dtdSource,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        assertThat(datasetDocument.getModel().getTables().get(0).getColumns()).as("ID must be declared.")
                .hasSize(1);

        dtdSource.setText("<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY>"
                + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>");
        datasetDocument.reloadDtd();

        assertThat(datasetDocument.getModel().getTables().get(0).getColumns())
                .as("reloadDtd must refresh once the cached DTD text changes.").hasSize(2);
    }

    @Test
    void testReloadDtd_whenTheCachedDtdTextIsUnchanged_doesNotNotify()
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><USERS ID=\"1\"/></dataset>");
        final String dtdText =
                "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY><!ATTLIST USERS ID CDATA #REQUIRED>";
        final MutableDtdSource dtdSource = new MutableDtdSource(dtdText);
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, dtdSource,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        final AtomicInteger notifications = new AtomicInteger();
        datasetDocument.addModelListener(event -> notifications.incrementAndGet());

        datasetDocument.reloadDtd();

        assertThat(notifications.get())
                .as("reloadDtd must not refresh or notify when the DTD text is unchanged.")
                .isEqualTo(0);
        assertThat(datasetDocument.isStale()).as("The model must not be marked stale either.").isFalse();
    }

    @Test
    void testLocateAndCellAt_whenGivenEveryNonNullCellOfEditorSample_roundTrip()
            throws AttributeValueException
    {
        final IDocument document = new Document(TestDatasets.read("editor-sample.xml"));
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();
        final DatasetModel model = datasetDocument.getModel();

        for (final DatasetTable table : model.getTables())
        {
            for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++)
            {
                for (int columnIndex = 0; columnIndex < table.getColumns().size(); columnIndex++)
                {
                    assertCellRoundTrips(document, datasetDocument, table, rowIndex, columnIndex);
                }
            }
        }
    }

    private void assertCellRoundTrips(final IDocument document,
            final FlatXmlDatasetDocument datasetDocument, final DatasetTable table, final int rowIndex,
            final int columnIndex) throws AttributeValueException
    {
        final String value = table.getRows().get(rowIndex).getValue(columnIndex);
        if (value == null)
        {
            return;
        }
        final CellAddress address = new CellAddress(table.getKey(), rowIndex, columnIndex);
        final Optional<IRegion> region = datasetDocument.locate(address);
        assertThat(region).as("locate must find every non-null cell.").isPresent();
        final String rawLocated = document.get().substring(region.get().getOffset(),
                region.get().getOffset() + region.get().getLength());
        assertThat(AttributeValueCodec.decode(rawLocated))
                .as("The located raw text, decoded, must equal the model's value.").isEqualTo(value);
        final Optional<CellAddress> roundTripped = datasetDocument.cellAt(region.get().getOffset());
        assertThat(roundTripped).as("cellAt must find the same cell locate pointed at.").contains(address);
    }

    @Test
    void testLocate_whenCellIsNull_returnsTheElementNameRange()
    {
        final IDocument document = new Document(TestDatasets.read("editor-sample.xml"));
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();
        final DatasetTable users = datasetDocument.getModel().findTable("USERS").orElseThrow();
        final int emailColumn = users.getColumnIndex("EMAIL");
        int nullRow = -1;
        for (int i = 0; i < users.getRows().size(); i++)
        {
            if (users.getRows().get(i).getValue(emailColumn) == null)
            {
                nullRow = i;
                break;
            }
        }
        assertThat(nullRow).as("editor-sample.xml must have a USERS row with no EMAIL.")
                .isGreaterThanOrEqualTo(0);

        final Optional<IRegion> region =
                datasetDocument.locate(new CellAddress(users.getKey(), nullRow, emailColumn));

        assertThat(region).as("A NULL cell must still be located.").isPresent();
        final String located = document.get().substring(region.get().getOffset(),
                region.get().getOffset() + region.get().getLength());
        assertThat(located).as("For a NULL cell, locate must return the element name.").isEqualTo("USERS");
    }

    @Test
    void testSetCells_whenChangingDoubleAndSingleQuotedValues_keepsEachAttributesQuoteStyle()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME='Bob'/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS",
                List.of(new CellChange(0, "ID", "2"), new CellChange(0, "NAME", "Rob")));

        assertThat(document.get()).as("Each attribute must keep its own quote style.")
                .isEqualTo("<dataset><USERS ID=\"2\" NAME='Rob'/></dataset>");
    }

    @Test
    void testSetCells_whenSettingNull_removesTheAttributeAndItsLeadingWhitespace()
    {
        final IDocument document =
                new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x.org\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", null)));

        assertThat(document.get()).as("Setting NULL must remove the attribute and its leading whitespace.")
                .isEqualTo("<dataset><USERS ID=\"1\" EMAIL=\"a@x.org\"/></dataset>");
    }

    @Test
    void testSetCells_whenSettingAValueOnANullCellThatIsTheFirstColumn_insertsItFirst()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x.org\"/>"
                + "<USERS NAME=\"Bob\" EMAIL=\"b@x.org\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS", List.of(new CellChange(1, "ID", "2")));

        assertThat(document.get()).as("A value for the first column must be inserted first.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x.org\"/>"
                        + "<USERS ID=\"2\" NAME=\"Bob\" EMAIL=\"b@x.org\"/></dataset>");
    }

    @Test
    void testSetCells_whenSettingAValueOnANullCellThatIsAMiddleColumn_insertsItInTheMiddle()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x.org\"/>"
                + "<USERS ID=\"2\" EMAIL=\"b@x.org\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS", List.of(new CellChange(1, "NAME", "Bob")));

        assertThat(document.get()).as("A value for a middle column must be inserted between its neighbors.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x.org\"/>"
                        + "<USERS ID=\"2\" NAME=\"Bob\" EMAIL=\"b@x.org\"/></dataset>");
    }

    @Test
    void testSetCells_whenSettingAValueOnANullCellThatIsTheLastColumn_insertsItLast()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x.org\"/>"
                + "<USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS", List.of(new CellChange(1, "EMAIL", "b@x.org")));

        assertThat(document.get()).as("A value for the last column must be inserted last.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x.org\"/>"
                        + "<USERS ID=\"2\" NAME=\"Bob\" EMAIL=\"b@x.org\"/></dataset>");
    }

    @Test
    void testSetCells_whenChangingSeveralCellsOfOneElement_isOneUndoStep() throws Exception
    {
        final IDocument document =
                new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"a@x.org\"/></dataset>");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.setCells("USERS",
                    List.of(new CellChange(0, "NAME", "Bob"), new CellChange(0, "EMAIL", "b@x.org")));

            assertThat(document.get()).as("Both changes to the one element must be applied.")
                    .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Bob\" EMAIL=\"b@x.org\"/></dataset>");
            undoManager.undo();
            assertThat(document.get()).as("One undo must revert both changes to the one element.")
                    .isEqualTo(original);
        });
    }

    @Test
    void testSetCells_whenChangingSeveralRows_isOneUndoStepThatRestoresTheOriginalTextExactly()
            throws Exception
    {
        final IDocument document = new Document(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.setCells("USERS",
                    List.of(new CellChange(0, "NAME", "Alicia"), new CellChange(1, "NAME", "Robert")));

            assertThat(document.get()).as("Both rows must be changed.").isEqualTo(
                    "<dataset><USERS ID=\"1\" NAME=\"Alicia\"/><USERS ID=\"2\" NAME=\"Robert\"/></dataset>");
            undoManager.undo();
            assertThat(document.get()).as("One undo must restore the original text exactly.")
                    .isEqualTo(original);
        });
    }

    @Test
    void testBatch_whenHoldingTwoSetCellsCalls_isOneUndoStep() throws Exception
    {
        final IDocument document = new Document(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.batch(() ->
            {
                datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Alicia")));
                datasetDocument.setCells("USERS", List.of(new CellChange(1, "NAME", "Robert")));
            });

            assertThat(document.get()).as("Both calls inside the batch must be applied.").isEqualTo(
                    "<dataset><USERS ID=\"1\" NAME=\"Alicia\"/><USERS ID=\"2\" NAME=\"Robert\"/></dataset>");
            undoManager.undo();
            assertThat(document.get()).as("One undo must revert the whole batch.").isEqualTo(original);
        });
    }

    @Test
    void testBatch_whenHoldingASetCellsOfMoreThan50RowsAndAnother_changesTheDocumentOncePerCall()
            throws Exception
    {
        final StringBuilder xml = new StringBuilder("<dataset>");
        final StringBuilder expected = new StringBuilder("<dataset>");
        for (int i = 0; i < 60; i++)
        {
            xml.append("<USERS ID=\"").append(i).append("\" NAME=\"Name").append(i).append("\"/>");
            expected.append("<USERS ID=\"").append(i).append("\" NAME=\"Changed").append(i).append("\"/>");
        }
        xml.append("<ORDERS ID=\"1\" TOTAL=\"5\"/></dataset>");
        expected.append("<ORDERS ID=\"1\" TOTAL=\"9\"/></dataset>");
        final IDocument document = new Document(xml.toString());
        final String original = document.get();

        withUndoManager(document, undoManager ->
        {
            final List<DocumentRewriteSessionEvent> sessionEvents = new ArrayList<>();
            ((IDocumentExtension4) document).addDocumentRewriteSessionListener(sessionEvents::add);
            final DocumentChangeCounter changes = new DocumentChangeCounter();
            document.addDocumentListener(changes);
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            final List<CellChange> manyChanges = new ArrayList<>();
            for (int i = 0; i < 60; i++)
            {
                manyChanges.add(new CellChange(i, "NAME", "Changed" + i));
            }

            datasetDocument.batch(() ->
            {
                datasetDocument.setCells("USERS", manyChanges);
                datasetDocument.setCells("ORDERS", List.of(new CellChange(0, "TOTAL", "9")));
            });

            assertThat(document.get()).as("All changes must be applied.").isEqualTo(expected.toString());
            assertThat(changes.count).as("Each call must change the document once, however many rows it "
                    + "changes.").isEqualTo(2);
            assertThat(sessionEvents).as("A batch must start no rewrite session, because a text viewer "
                    + "redraws its whole document when a session stops.").isEmpty();
            undoManager.undo();
            assertThat(document.get()).as("A batch's changes must be one undo step.").isEqualTo(original);
        });
    }

    @Test
    void testDeleteRows_whenDeletingMoreThan50ScatteredRows_changesTheDocumentOnceAndUndoRestoresIt()
            throws Exception
    {
        final StringBuilder xml = new StringBuilder("<dataset>\n");
        final StringBuilder expected = new StringBuilder("<dataset>\n");
        final int[] oddRows = new int[60];
        for (int i = 0; i < 120; i++)
        {
            final String line = "  <USERS ID=\"" + i + "\"/>\n";
            xml.append(line);
            if (i % 2 == 0)
            {
                expected.append(line);
            }
            else
            {
                oddRows[i / 2] = i;
            }
        }
        xml.append("</dataset>\n");
        expected.append("</dataset>\n");
        final IDocument document = new Document(xml.toString());
        final String original = document.get();

        withUndoManager(document, undoManager ->
        {
            final DocumentChangeCounter changes = new DocumentChangeCounter();
            document.addDocumentListener(changes);
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.deleteRows("USERS", oddRows);

            assertThat(document.get()).as("Every other row's line must be deleted and nothing else.")
                    .isEqualTo(expected.toString());
            assertThat(changes.count).as("Deleting many rows must change the document once.").isEqualTo(1);
            undoManager.undo();
            assertThat(document.get()).as("One undo must restore the original text exactly.")
                    .isEqualTo(original);
        });
    }

    @Test
    void testSetCells_whenValueContainsSpecialCharacters_escapesThem()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NOTE=\"x\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS", List.of(new CellChange(0, "NOTE", "a<b & \"c\" \t\nd")));

        assertThat(document.get()).as("Every special character must be escaped correctly.").isEqualTo(
                "<dataset><USERS ID=\"1\" NOTE=\"a&lt;b &amp; &quot;c&quot; &#09;&#xA;d\"/></dataset>");
    }

    @Test
    void testSetCells_whenValueIsUnchanged_doesNothingAndAddsNoUndoStep() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Alice")));

            assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
            assertThat(undoManager.undoable()).as("No undo step must be added for a no-op change.")
                    .isFalse();
        });
    }

    @Test
    void testSetCells_whenStartTagSpansSeveralLines_keepsItsLayout()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"\n       NAME=\"Alice\"\n"
                + "       EMAIL=\"a@x.org\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Bob")));

        assertThat(document.get())
                .as("Only the changed attribute's value changes; the multi-line layout stays.")
                .isEqualTo("<dataset><USERS ID=\"1\"\n       NAME=\"Bob\"\n"
                        + "       EMAIL=\"a@x.org\"/></dataset>");
    }

    @Test
    void testSetCells_whenDocumentHasCommentsAndCrLfLineEnds_preservesThem()
    {
        final IDocument document = new Document("<dataset>\r\n    <!-- a comment -->\r\n"
                + "    <USERS ID=\"1\" NAME=\"Alice\"/>\r\n</dataset>\r\n");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Bob")));

        assertThat(document.get()).as("Comments and CR LF line ends elsewhere must be untouched.")
                .isEqualTo("<dataset>\r\n    <!-- a comment -->\r\n"
                        + "    <USERS ID=\"1\" NAME=\"Bob\"/>\r\n</dataset>\r\n");
    }

    @Test
    void testSetCells_whenCharsetCannotEncodeTheValue_usesACharacterReference()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NOTE=\"x\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.ISO_8859_1);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS", List.of(new CellChange(0, "NOTE", "€")));

        assertThat(document.get()).as(
                "A character the document's encoder cannot represent must become a numeric character "
                        + "reference.")
                .isEqualTo("<dataset><USERS ID=\"1\" NOTE=\"&#x20AC;\"/></dataset>");
    }

    @Test
    void testSetCells_whenChangeWouldEmptyARow_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.setCells("USERS", List.of(new CellChange(0, "ID", null))))
                .as("Emptying a row's only value must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testSetCells_whenValueContainsANonXmlCharacter_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(
                () -> datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "\u0001"))))
                .as("A value with a character outside the XML 1.0 Char range must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testSetCells_afterADirectDocumentReplaceElsewhere_usesFreshOffsets() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        document.replace(document.get().indexOf("<USERS"), 0, "<AUDIT_LOG/>");

        datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Bob")));

        assertThat(document.get()).as("setCells must refresh and use fresh offsets after an external change.")
                .isEqualTo("<dataset><AUDIT_LOG/><USERS ID=\"1\" NAME=\"Bob\"/></dataset>");
    }

    @Test
    void testInsertRows_whenInsertingBeforeTheFirstRow_placesTheNewRowThereWithMatchingIndentation()
            throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.insertRows("USERS", 0, List.of(List.of("0")));

            assertThat(document.get())
                    .as("The new row must be inserted before the first, indented to match.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"0\"/>\n    <USERS ID=\"1\"/>\n"
                            + "    <USERS ID=\"2\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).as("Undo must restore the original text.").isEqualTo(original);
        });
    }

    @Test
    void testInsertRows_whenInsertingInTheMiddle_placesTheNewRowBetweenItsNeighbors() throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"3\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.insertRows("USERS", 1, List.of(List.of("2")));

            assertThat(document.get()).as("The new row must be inserted between its neighbors.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n"
                            + "    <USERS ID=\"3\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testInsertRows_whenInsertingAfterTheLastRow_appendsTheNewRowWithMatchingIndentation()
            throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.insertRows("USERS", 2, List.of(List.of("3")));

            assertThat(document.get())
                    .as("The new row must be appended after the last, indented to match.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n"
                            + "    <USERS ID=\"3\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testInsertRows_whenIndentationUsesTabs_copiesTabIndentation() throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n\t<USERS ID=\"1\"/>\n\t<USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.insertRows("USERS", 1, List.of(List.of("3")));

            assertThat(document.get()).as("The new row must copy the tab indentation.").isEqualTo(
                    "<dataset>\n\t<USERS ID=\"1\"/>\n\t<USERS ID=\"3\"/>\n\t<USERS ID=\"2\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testInsertRows_whenTableHasOnlyAMarker_replacesTheMarker() throws Exception
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n    <USERS/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.insertRows("USERS", 0, List.of(List.of("1"), List.of("2")));

            assertThat(document.get()).as("The marker must be replaced by the new rows.").isEqualTo(
                    "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n"
                            + "    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testInsertRows_whenTableIsDtdOnly_addsTheElementBeforeTheEndTag() throws Exception
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*, ORDERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n<!ELEMENT ORDERS EMPTY>\n"
                        + "<!ATTLIST ORDERS ID CDATA #REQUIRED>\n]>\n<dataset>\n"
                        + "    <USERS ID=\"1\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.insertRows("ORDERS", 0, List.of(List.of("5")));

            assertThat(document.get())
                    .as("The new row must be added as the last child, before the end tag.")
                    .isEqualTo("<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*, ORDERS*)>\n"
                            + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #REQUIRED>\n"
                            + "<!ELEMENT ORDERS EMPTY>\n<!ATTLIST ORDERS ID CDATA #REQUIRED>\n]>\n"
                            + "<dataset>\n    <USERS ID=\"1\"/>\n    <ORDERS ID=\"5\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testInsertRows_whenAllValuesAreNull_getsTheEmptyStringInItsFirstColumn() throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.insertRows("USERS", 1, List.of(Arrays.asList(null, null)));

            assertThat(document.get())
                    .as("An all-null row must get the empty string in its first column.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n"
                            + "    <USERS ID=\"\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testInsertRows_whenInsertingSeveralRows_keepsTheGivenOrder() throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.insertRows("USERS", 1,
                    List.of(List.of("2"), List.of("3"), List.of("4")));

            assertThat(document.get()).as("Several new rows must keep the given order.").isEqualTo(
                    "<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n    <USERS ID=\"3\"/>\n"
                            + "    <USERS ID=\"4\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testInsertRows_whenRowIndexIsOutOfRange_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.insertRows("USERS", 5, List.of(List.of("2"))))
                .as("An out-of-range row index must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testDeleteRows_whenDeletingMiddleRowsAloneOnTheirLines_removesTheirWholeLines() throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\"/>\n"
                + "    <USERS ID=\"2\"/>\n    <USERS ID=\"3\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.deleteRows("USERS", new int[] { 1 });

            assertThat(document.get())
                    .as("Deleting a middle row alone on its line must remove the whole line.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"3\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testDeleteRows_whenDeletingInlineElements_removesOnlyTheElements() throws Exception
    {
        final IDocument document =
                new Document("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/><USERS ID=\"3\"/></dataset>");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.deleteRows("USERS", new int[] { 1 });

            assertThat(document.get()).as("Deleting an inline element must remove only that element.")
                    .isEqualTo("<dataset><USERS ID=\"1\"/><USERS ID=\"3\"/></dataset>");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testDeleteRows_whenDeletingAllRowsWithoutAMarker_leavesAMarkerElement() throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.deleteRows("USERS", new int[] { 0, 1 });

            assertThat(document.get()).as("Deleting every row must leave a marker so the table stays.")
                    .isEqualTo("<dataset>\n    <USERS/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testDeleteRows_whenDeletingAllRowsOfATableThatHasAMarker_keepsOnlyTheMarker() throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS/>\n    <USERS ID=\"1\"/>\n"
                + "    <USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.deleteRows("USERS", new int[] { 0, 1 });

            assertThat(document.get())
                    .as("With an existing marker, deleting all rows must not add another.")
                    .isEqualTo("<dataset>\n    <USERS/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testDeleteRows_whenARowIndexIsOutOfRange_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.deleteRows("USERS", new int[] { 5 }))
                .as("An out-of-range row index must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testDuplicateRows_whenDuplicatingSelectedRows_copiesTheirTextVerbatimAfterTheLastSelected()
            throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\"/>\n"
                + "    <USERS ID=\"2\"/>\n    <USERS ID=\"3\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.duplicateRows("USERS", new int[] { 0, 2 });

            assertThat(document.get())
                    .as("Duplicates must be inserted verbatim, in row order, after the last selected row.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n"
                            + "    <USERS ID=\"3\"/>\n    <USERS ID=\"1\"/>\n"
                            + "    <USERS ID=\"3\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testDuplicateRows_whenARowIndexIsOutOfRange_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.duplicateRows("USERS", new int[] { 5 }))
                .as("An out-of-range row index must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testMoveRows_whenMovingDown_swapsWithTheNextRow() throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.moveRows("USERS", 0, 1, 1);

            assertThat(document.get()).as("Moving down must swap the row with its next neighbor.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"2\"/>\n    <USERS ID=\"1\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testMoveRows_whenMovingUp_swapsWithThePreviousRow() throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.moveRows("USERS", 1, 1, -1);

            assertThat(document.get()).as("Moving up must swap the row with its previous neighbor.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"2\"/>\n    <USERS ID=\"1\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testMoveRows_whenMovingABlockDown_movesTheWholeBlockPastItsNeighbor() throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\"/>\n"
                + "    <USERS ID=\"2\"/>\n    <USERS ID=\"3\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.moveRows("USERS", 0, 2, 1);

            assertThat(document.get())
                    .as("Moving a block down must move it past its next neighbor as a unit.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"3\"/>\n    <USERS ID=\"1\"/>\n"
                            + "    <USERS ID=\"2\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testMoveRows_whenTableSegmentsAreInterleavedWithAnotherTable_stillRotatesCorrectly()
            throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\"/>\n"
                + "    <ORDERS ID=\"10\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.moveRows("USERS", 0, 1, 1);

            assertThat(document.get())
                    .as("Moving must rotate element texts in place, working across an interleaved "
                            + "segment.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"2\"/>\n    <ORDERS ID=\"10\"/>\n"
                            + "    <USERS ID=\"1\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testMoveRows_whenMovingPastTheFirstRow_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.moveRows("USERS", 0, 1, -1))
                .as("Moving the first row up must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testMoveRows_whenMovingPastTheLastRow_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/><USERS ID=\"2\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.moveRows("USERS", 1, 1, 1))
                .as("Moving the last row down must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testAddColumn_whenColumnDoesNotExist_addsAPendingColumnWithoutChangingText()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.addColumn("USERS", "NAME");

        assertThat(document.get()).as("Adding a column must not change the text.").isEqualTo(original);
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        final int columnIndex = table.getColumnIndex("NAME");
        assertThat(table.getColumns().get(columnIndex))
                .as("The new column must be pending, undeclared, and without values.")
                .isEqualTo(new DatasetColumn("NAME", false, false, true));
    }

    @Test
    void testAddColumn_whenSettingAValueInIt_becomesADataColumnAndDropsThePendingEntry()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();
        datasetDocument.addColumn("USERS", "NAME");

        datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Alice")));

        assertThat(document.get()).as("Setting the value must write the new attribute.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        final int columnIndex = table.getColumnIndex("NAME");
        assertThat(table.getColumns().get(columnIndex))
                .as("The column must become a plain data column once it has a value.")
                .isEqualTo(new DatasetColumn("NAME", false, true, false));
    }

    @Test
    void testAddColumn_whenNameIsInvalid_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.addColumn("USERS", "1BAD"))
                .as("An invalid column name must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumnIndex("1BAD"))
                .as("No pending column must have been recorded.").isEqualTo(-1);
    }

    @Test
    void testAddColumn_whenNameAlreadyExists_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.addColumn("USERS", "name"))
                .as("A name that already exists, case-insensitively, must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testRenameColumn_whenColumnHasMatchingAttributes_renamesEveryOccurrenceCaseInsensitively()
            throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n"
                + "    <USERS ID=\"2\" name=\"Bob\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.renameColumn("USERS", "NAME", "FULL_NAME");

            assertThat(document.get())
                    .as("Every attribute matching the column, regardless of case, must be renamed and "
                            + "nothing else changed.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"1\" FULL_NAME=\"Alice\"/>\n"
                            + "    <USERS ID=\"2\" FULL_NAME=\"Bob\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testRenameColumn_whenColumnIsPending_renamesThePendingEntryWithoutChangingText()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();
        datasetDocument.addColumn("USERS", "EXTRA");

        datasetDocument.renameColumn("USERS", "EXTRA", "NOTES");

        assertThat(document.get()).as("Renaming a pending column must not change the text.")
                .isEqualTo(original);
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
        assertThat(table.getColumnIndex("EXTRA")).as("The old pending name must be gone.").isEqualTo(-1);
        assertThat(table.getColumns().get(table.getColumnIndex("NOTES")))
                .as("The pending column must be renamed in place.")
                .isEqualTo(new DatasetColumn("NOTES", false, false, true));
    }

    @Test
    void testRenameColumn_whenNewNameIsInvalid_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.renameColumn("USERS", "NAME", "1BAD"))
                .as("An invalid new column name must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testRenameColumn_whenNewNameAlreadyExists_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.renameColumn("USERS", "NAME", "id"))
                .as("Renaming to a name that already exists, case-insensitively, must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testRenameColumn_whenAnElementHasTwoAttributesForTheColumnDifferingOnlyInCase_throwsAndChangesNothing()
    {
        final IDocument document =
                new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\" name=\"Bob\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.renameColumn("USERS", "NAME", "FULL_NAME"))
                .as("Renaming must be rejected when a row has two attributes for the column.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testDeleteColumn_whenColumnExistsInMultipleRows_removesTheAttributeEverywhere() throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n"
                + "    <USERS ID=\"2\" NAME=\"Bob\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.deleteColumn("USERS", "NAME");

            assertThat(document.get()).as("The attribute must be removed from every row.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testDeleteColumn_whenItIsARowsOnlyValue_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS NAME=\"Alice\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.deleteColumn("USERS", "NAME"))
                .as("Deleting a row's only value must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testDeleteColumn_whenColumnIsPending_dropsItWithoutChangingText()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();
        datasetDocument.addColumn("USERS", "EXTRA");

        datasetDocument.deleteColumn("USERS", "EXTRA");

        assertThat(document.get()).as("Deleting a pending column must not change the text.")
                .isEqualTo(original);
        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumnIndex("EXTRA"))
                .as("The pending column must be gone.").isEqualTo(-1);
    }

    @Test
    void testDeleteColumn_whenColumnIsDtdDeclared_keepsItInTheModel() throws Exception
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #REQUIRED>\n]>\n"
                        + "<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.deleteColumn("USERS", "NAME");

            assertThat(document.get()).as("The attribute must be removed from the row.").isEqualTo(
                    "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #REQUIRED>\n]>\n"
                            + "<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            final int columnIndex = table.getColumnIndex("NAME");
            assertThat(table.getColumns().get(columnIndex))
                    .as("A DTD-declared column must stay in the model even without values.")
                    .isEqualTo(new DatasetColumn("NAME", true, false, false));
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testAddTable_whenTheEndTagIsAloneOnItsLine_insertsAtTheStartOfThatLine() throws Exception
    {
        final IDocument document = new Document("<dataset>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.addTable("USERS", List.of());

            assertThat(document.get())
                    .as("The table must be added at the start of the end tag's own line.")
                    .isEqualTo("<dataset>\n  <USERS/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testAddTable_whenTheEndTagSharesItsLineWithOtherContent_insertsBeforeTheEndTag()
            throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <ORDERS ID=\"1\"/></dataset>");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.addTable("USERS", List.of());

            assertThat(document.get())
                    .as("The table must be added on its own line, right before the end tag.")
                    .isEqualTo("<dataset>\n    <ORDERS ID=\"1\"/>\n    <USERS/>\n</dataset>");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testAddTable_whenRootIsSelfClosing_replacesItWithAnOpenAndCloseTag() throws Exception
    {
        final IDocument document = new Document("<dataset/>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.addTable("USERS", List.of());

            assertThat(document.get())
                    .as("A self-closing root must become an open and close tag around the new table.")
                    .isEqualTo("<dataset>\n  <USERS/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testAddTable_whenATableAlreadyHasRows_insertsAfterThemUsingTheirIndentation() throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <ORDERS ID=\"1\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.addTable("USERS", List.of());

            assertThat(document.get())
                    .as("The table must be added after existing rows, matching their indentation.")
                    .isEqualTo("<dataset>\n    <ORDERS ID=\"1\"/>\n    <USERS/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testAddTable_whenColumnNamesAreGiven_recordsThemAsPendingColumns() throws Exception
    {
        final IDocument document = new Document("<dataset>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.addTable("ITEMS", List.of("SKU", "QTY"));

            assertThat(document.get()).isEqualTo("<dataset>\n  <ITEMS/>\n</dataset>\n");
            final DatasetTable table = datasetDocument.getModel().findTable("ITEMS").orElseThrow();
            assertThat(table.getColumns()).as("Both given names must be recorded as pending columns.")
                    .containsExactly(new DatasetColumn("SKU", false, false, true),
                            new DatasetColumn("QTY", false, false, true));
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testAddTable_whenNameIsInvalid_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.addTable("1BAD", List.of()))
                .as("An invalid table name must be rejected.").isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testAddTable_whenNameAlreadyExists_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.addTable("users", List.of()))
                .as("A name that already exists, case-insensitively, must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testAddTable_whenNameIsDataset_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.addTable("dataset", List.of()))
                .as("The reserved name 'dataset' must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testAddTable_whenAColumnNameIsInvalid_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.addTable("ORDERS", List.of("1st col")))
                .as("An invalid column name must be rejected.").isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
        assertThat(datasetDocument.getModel().findTable("ORDERS")).as("No table must have been added.")
                .isEmpty();
    }

    @Test
    void testAddTable_whenColumnNamesHaveADuplicateCaseInsensitively_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.addTable("ORDERS", List.of("ID", "id")))
                .as("A duplicate column name, case-insensitively, must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
        assertThat(datasetDocument.getModel().findTable("ORDERS")).as("No table must have been added.")
                .isEmpty();
    }

    @Test
    void testRenameTable_whenElementsAreSelfClosing_renamesTheStartTagAcrossAllSegments()
            throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\"/>\n"
                + "    <ORDERS ID=\"10\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.renameTable("USERS", "CUSTOMERS");

            assertThat(document.get())
                    .as("Every segment of the table, wherever it appears, must be renamed.")
                    .isEqualTo("<dataset>\n    <CUSTOMERS ID=\"1\"/>\n    <ORDERS ID=\"10\"/>\n"
                            + "    <CUSTOMERS ID=\"2\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testRenameTable_whenAnElementHasAnExplicitEndTag_renamesBothTags() throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\"></USERS>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.renameTable("USERS", "CUSTOMERS");

            assertThat(document.get()).as("Both the start and end tag names must be replaced.")
                    .isEqualTo("<dataset>\n    <CUSTOMERS ID=\"1\"></CUSTOMERS>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testRenameTable_whenColumnsArePending_movesThemToTheNewKey() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "EXTRA");

            datasetDocument.renameTable("USERS", "CUSTOMERS");

            assertThat(document.get()).isEqualTo("<dataset><CUSTOMERS ID=\"1\"/></dataset>");
            assertThat(datasetDocument.getModel().findTable("USERS"))
                    .as("The old table key must be gone.").isEmpty();
            final DatasetTable table = datasetDocument.getModel().findTable("CUSTOMERS").orElseThrow();
            assertThat(table.getColumns())
                    .as("The pending column must move to the renamed table.")
                    .contains(new DatasetColumn("EXTRA", false, false, true));
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testRenameTable_whenNewNameIsInvalid_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.renameTable("USERS", "1BAD"))
                .as("An invalid new table name must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testRenameTable_whenNewNameAlreadyExists_throwsAndChangesNothing()
    {
        final IDocument document =
                new Document("<dataset><USERS ID=\"1\"/><ORDERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.renameTable("USERS", "orders"))
                .as("A new name that already exists, case-insensitively, must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testRenameTable_whenNewNameIsDataset_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.renameTable("USERS", "dataset"))
                .as("The reserved name 'dataset' must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testDeleteTable_whenAnotherTableIsInterleaved_leavesItByteIdentical() throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\"/>\n"
                + "    <ORDERS ID=\"10\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.deleteTable("USERS");

            assertThat(document.get())
                    .as("The other table's element must be left byte-identical.")
                    .isEqualTo("<dataset>\n    <ORDERS ID=\"10\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testDeleteTable_whenTableIsDtdDeclared_leavesADeclaredOnlyTable() throws Exception
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n"
                        + "    <USERS ID=\"1\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.deleteTable("USERS");

            assertThat(document.get()).as("The table's only element must be removed.").isEqualTo(
                    "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.isDeclaredOnly())
                    .as("A DTD-declared table must remain, as declared-only.").isTrue();
            assertThat(table.getColumns()).as("Its declared column must still be listed.")
                    .containsExactly(new DatasetColumn("ID", true, false, false));
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    private static void withUndoManager(final IDocument document, final UndoManagerConsumer consumer)
            throws Exception
    {
        DocumentUndoManagerRegistry.connect(document);
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(FlatXmlDatasetDocumentTest.class);
        try
        {
            consumer.accept(undoManager);
        }
        finally
        {
            undoManager.disconnect(FlatXmlDatasetDocumentTest.class);
            DocumentUndoManagerRegistry.disconnect(document);
        }
    }

    private static FlatXmlDatasetDocument create(final IDocument document)
    {
        return new FlatXmlDatasetDocument(document, DtdSource.NONE, FlatXmlOptions.DBUNIT_DEFAULTS,
                () -> StandardCharsets.UTF_8);
    }

    private static final class MutableDtdSource implements DtdSource
    {
        private String text;

        private MutableDtdSource(final String text)
        {
            this.text = text;
        }

        private void setText(final String newText)
        {
            this.text = newText;
        }

        @Override
        public Optional<String> load(final String publicId, final String systemId)
        {
            return Optional.of(text);
        }
    }

    @FunctionalInterface
    private interface UndoManagerConsumer
    {
        void accept(IDocumentUndoManager undoManager) throws Exception;
    }

    /**
     * Counts the changes of a document.
     */
    private static final class DocumentChangeCounter implements IDocumentListener
    {
        private int count;

        @Override
        public void documentAboutToBeChanged(final DocumentEvent event)
        {
        }

        @Override
        public void documentChanged(final DocumentEvent event)
        {
            count++;
        }
    }
}
