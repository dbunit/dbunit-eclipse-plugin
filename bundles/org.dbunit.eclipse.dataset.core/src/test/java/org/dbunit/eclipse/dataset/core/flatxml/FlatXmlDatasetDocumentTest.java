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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.eclipse.dataset.core.TestDatasets;
import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.ChangeOrigin;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
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
    void testRefresh_whenTheDtdSourceThrows_yieldsADtdNotLoadedProblem()
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><USERS ID=\"1\"/></dataset>");
        final DtdSource throwingSource = (publicId, systemId) ->
        {
            throw new IllegalArgumentException("URI is not hierarchical");
        };
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, throwingSource,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);

        datasetDocument.refresh();

        assertThat(datasetDocument.getModel().getProblems()).extracting(DatasetProblem::code)
                .as("A DtdSource that throws must yield the same DTD_NOT_LOADED warning as one that finds "
                        + "nothing.")
                .contains(ProblemCode.DTD_NOT_LOADED);
    }

    @Test
    void testRefresh_whenInternalSubsetHasAParameterEntity_pointsTheProblemAtItsOwnOffset()
    {
        final String text = "<!DOCTYPE dataset [\n<!ENTITY % common \"ID CDATA #REQUIRED\">\n"
                + "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY>\n]>\n"
                + "<dataset><USERS ID=\"1\"/></dataset>";
        final IDocument document = new Document(text);
        final FlatXmlDatasetDocument datasetDocument = create(document);

        datasetDocument.refresh();

        final DatasetProblem problem = datasetDocument.getModel().getProblems().stream()
                .filter(candidate -> candidate.code() == ProblemCode.UNSUPPORTED_DTD_CONSTRUCT).findFirst()
                .orElseThrow();
        assertThat(problem.offset())
                .as("An internal subset's problem must point at its own text in the document.")
                .isEqualTo(text.indexOf("<!ENTITY"));
    }

    @Test
    void testRefresh_whenExternalDtdHasAParameterEntity_pointsTheProblemAtTheDoctype()
    {
        final String text = "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><USERS ID=\"1\"/></dataset>";
        final IDocument document = new Document(text);
        final MutableDtdSource dtdSource = new MutableDtdSource(
                "<!ENTITY % common \"ID CDATA #REQUIRED\">"
                        + "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY>");
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, dtdSource,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);

        datasetDocument.refresh();

        final DatasetProblem problem = datasetDocument.getModel().getProblems().stream()
                .filter(candidate -> candidate.code() == ProblemCode.UNSUPPORTED_DTD_CONSTRUCT).findFirst()
                .orElseThrow();
        final int doctypeOffset = text.indexOf("<!DOCTYPE");
        final int doctypeLength = text.indexOf('>', doctypeOffset) + 1 - doctypeOffset;
        assertThat(problem.offset())
                .as("An external DTD's problem must point at the DOCTYPE, since the DTD file is not open.")
                .isEqualTo(doctypeOffset);
        assertThat(problem.length()).as("Its length must span the whole DOCTYPE declaration.")
                .isEqualTo(doctypeLength);
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
    void testLocate_whenTheRowSpellsTheColumnWithAnotherNameOfTheSameKey_returnsTheValueRange()
    {
        final String text = "<dataset><USERS ID=\"1\" straße=\"Main\"/>"
                + "<USERS ID=\"2\" STRASSE=\"Side\"/></dataset>";
        final IDocument document = new Document(text);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        final Optional<IRegion> region = datasetDocument.locate(new CellAddress("USERS", 1, 1));

        assertThat(region).as("A cell whose row spells the column with another key-equal name must be "
                + "located.").isPresent();
        final String located = text.substring(region.get().getOffset(),
                region.get().getOffset() + region.get().getLength());
        assertThat(located)
                .as("The attribute STRASSE has the same key as the column, which the first row spells "
                        + "differently, so locate must return its value, not the element name.")
                .isEqualTo("Side");
    }

    @Test
    void testCellAt_whenTheAttributeSharesTheColumnKeyYetIsNotEqualIgnoringCase_returnsThatColumn()
    {
        final String text = "<dataset><USERS ID=\"1\" straße=\"Main\"/>"
                + "<USERS ID=\"2\" STRASSE=\"Side\"/></dataset>";
        final IDocument document = new Document(text);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        final Optional<CellAddress> address = datasetDocument.cellAt(text.indexOf("STRASSE"));

        assertThat(address)
                .as("An offset in the attribute STRASSE must map to the column with the same key, not to "
                        + "the first column.")
                .contains(new CellAddress("USERS", 1, 1));
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
}
