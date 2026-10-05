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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.eclipse.dataset.core.TestDatasets;
import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.edit.ChangeOrigin;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
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
    void testRefresh_whenAnExternalDtdDeclaresTheTable_marksTheTableDeclaredInTheExternalDtd()
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><USERS ID=\"1\"/></dataset>");
        final MutableDtdSource dtdSource = new MutableDtdSource(
                "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY><!ATTLIST USERS ID CDATA #REQUIRED>");
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, dtdSource,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);

        datasetDocument.refresh();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().isDeclaredInExternalDtd())
                .as("The editor cannot rename the table in the DTD file, so the model must say that the "
                        + "file declares it.")
                .isTrue();
    }

    @Test
    void testRefresh_whenOnlyTheInternalSubsetDeclaresTheTable_doesNotMarkItDeclaredInTheExternalDtd()
    {
        final IDocument document = new Document("<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
                + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n"
                + "<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);

        datasetDocument.refresh();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().isDeclaredInExternalDtd())
                .as("The editor renames a table in the internal subset itself, so no DTD file is "
                        + "involved.")
                .isFalse();
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
    void testRefresh_whenTheDtdSourceThrows_logsAWarningThatNamesTheDtd()
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><USERS ID=\"1\"/></dataset>");
        final IllegalArgumentException failure = new IllegalArgumentException("URI is not hierarchical");
        final DtdSource throwingSource = (publicId, systemId) ->
        {
            throw failure;
        };
        final List<IStatus> logged = new ArrayList<>();
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, throwingSource,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8, logged::add);

        datasetDocument.refresh();

        assertThat(logged).as("A DtdSource that throws must be logged once, with its failure.")
                .usingRecursiveComparison()
                .isEqualTo(List.of(new Status(IStatus.WARNING, "org.dbunit.eclipse.dataset.core",
                        "The DTD source failed to load the DTD \"my.dtd\", so it is treated as not found.",
                        failure)));
    }

    @Test
    void testReloadDtd_whenTheDtdSourceThrows_treatsTheDtdAsNotLoadedAndLogsAWarning()
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><USERS ID=\"1\"/></dataset>");
        final AtomicBoolean failing = new AtomicBoolean();
        final IllegalStateException failure = new IllegalStateException("The DTD source is broken.");
        final DtdSource source = (publicId, systemId) ->
        {
            if (failing.get())
            {
                throw failure;
            }
            return Optional.of(
                    "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY><!ATTLIST USERS ID CDATA #REQUIRED>");
        };
        final List<IStatus> logged = new ArrayList<>();
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, source,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8, logged::add);
        datasetDocument.refresh();
        failing.set(true);

        datasetDocument.reloadDtd();

        assertThat(datasetDocument.getModel().getProblems()).extracting(DatasetProblem::code)
                .as("A DtdSource that throws on reload must leave the DTD not loaded, like one that finds "
                        + "nothing.")
                .contains(ProblemCode.DTD_NOT_LOADED);
        assertThat(logged).as("The failure while reloading must be logged once.")
                .usingRecursiveComparison()
                .isEqualTo(List.of(new Status(IStatus.WARNING, "org.dbunit.eclipse.dataset.core",
                        "The DTD source failed to load the DTD \"my.dtd\", so it is treated as not found.",
                        failure)));
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
    void testSetCells_whenTheCharsetSupplierThrows_escapesForUtf8AndLogsAWarning()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NOTE=\"x\"/></dataset>");
        final IllegalStateException failure = new IllegalStateException("The charset is unknown.");
        final List<IStatus> logged = new ArrayList<>();
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () ->
                {
                    throw failure;
                }, logged::add);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS", List.of(new CellChange(0, "NOTE", "€")));

        assertThat(document.get()).as("A failing charset supplier must fall back to UTF-8, which can "
                + "represent the euro sign.").isEqualTo("<dataset><USERS ID=\"1\" NOTE=\"€\"/></dataset>");
        assertThat(logged).as("The failing charset supplier must be logged once, with its failure.")
                .usingRecursiveComparison()
                .isEqualTo(List.of(new Status(IStatus.WARNING, "org.dbunit.eclipse.dataset.core",
                        "The charset supplier failed, so the document escapes values for UTF-8.", failure)));
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
    void testSetCells_whenRowHasTwoAttributesForTheColumnDifferingOnlyInCase_throwsAndChangesNothing()
    {
        final IDocument document =
                new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\" name=\"Bob\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(
                () -> datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Carol"))))
                .as("Changing a cell must be rejected when its row has two attributes for the column, "
                        + "instead of silently overwriting both.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testSetCells_whenAttributesShareAKeyButAreNotEqualIgnoringCase_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS straße=\"1\" STRASSE=\"2\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(
                () -> datasetDocument.setCells("USERS", List.of(new CellChange(0, "straße", "3"))))
                .as("Changing a cell must be rejected with the case-variant error, not fail on the "
                        + "lookup of its column, when its row has two attributes with the column's key.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testSetCells_whenSettingNullOnAColumnWithCaseVariantAttributes_throwsAndChangesNothing()
    {
        final IDocument document =
                new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\" name=\"Bob\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", null))))
                .as("Clearing a cell must be rejected when its row has two attributes for the column, "
                        + "instead of silently removing both.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testSetCells_whenEditingAnUnambiguousColumnInARowWithCaseVariantAttributes_changesOnlyThatAttribute()
    {
        final IDocument document = new Document(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\" name=\"Bob\" EMAIL=\"a@x.org\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.setCells("USERS", List.of(new CellChange(0, "EMAIL", "b@x.org")));

        assertThat(document.get())
                .as("A column without case-variant attributes must stay editable, and the ambiguous "
                        + "attributes must be left byte-identical.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Alice\" name=\"Bob\" EMAIL=\"b@x.org\"/></dataset>");
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
    void testSetCellsAndAppendRows_whenChangingTheLastRowAndAppending_rebuildsTheModelOnceInOneUndoStep()
            throws Exception
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n"
                + "    <USERS ID=\"2\" NAME=\"Bob\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            final List<ChangeOrigin> refreshes = new ArrayList<>();
            datasetDocument.addModelListener(event -> refreshes.add(event.origin()));

            datasetDocument.setCellsAndAppendRows("USERS", List.of(new CellChange(1, "NAME", "Robert")),
                    List.of(List.of("3", "Carol"), Arrays.asList("4", null)));

            assertThat(document.get())
                    .as("The last row must change, and the new rows must follow it, indented to match.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n"
                            + "    <USERS ID=\"2\" NAME=\"Robert\"/>\n    <USERS ID=\"3\" NAME=\"Carol\"/>\n"
                            + "    <USERS ID=\"4\"/>\n</dataset>\n");
            assertThat(refreshes).as("The model must be rebuilt once.").containsExactly(ChangeOrigin.EDIT);
            undoManager.undo();
            assertThat(document.get()).as("One undo must restore the original text.").isEqualTo(original);
        });
    }

    @Test
    void testSetCellsAndAppendRows_whenAChangeWouldEmptyARow_throwsAndAppendsNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.setCellsAndAppendRows("USERS",
                List.of(new CellChange(0, "ID", null)), List.of(List.of("2"))))
                .as("A change that would empty a row must be rejected.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("A rejected call must not append its rows either.")
                .isEqualTo("<dataset><USERS ID=\"1\"/></dataset>");
    }

    @Test
    void testSetCellsAndAppendRows_whenAnAppendedRowIsAllNull_throwsAndAppendsNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.setCellsAndAppendRows("USERS", List.of(),
                List.of(Arrays.asList((String) null))))
                .as("An appended row with no values must be rejected, the same as a change that would "
                        + "empty an existing row.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("A rejected call must not append its rows either.")
                .isEqualTo("<dataset><USERS ID=\"1\"/></dataset>");
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
    void testInsertBlankRow_aboveTheFirstRowOfATableWithoutDtd_hasEveryColumnOfTheOldFirstRow()
            throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.insertBlankRow("USERS", 0);

            assertThat(document.get())
                    .as("The blank row becomes the first row, so it needs every column dbUnit takes from "
                            + "the old first row.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"\" NAME=\"\"/>\n"
                            + "    <USERS ID=\"1\" NAME=\"Alice\"/>\n</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).as("One undo step must remove the blank row.").isEqualTo(original);
        });
    }

    @Test
    void testInsertBlankRow_belowTheFirstRow_hasAnEmptyStringInTheFirstColumnOnly() throws Exception
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.insertBlankRow("USERS", 1);

            assertThat(document.get()).as("A row after the first needs only a value that makes it a row.")
                    .isEqualTo("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n    <USERS ID=\"\"/>\n"
                            + "</dataset>\n");
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testInsertBlankRow_aboveTheFirstRowWhenTheDocumentHasADtd_hasAnEmptyStringInTheFirstColumnOnly()
            throws Exception
    {
        final String text = "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                + "<!ATTLIST USERS ID CDATA #IMPLIED NAME CDATA #IMPLIED>\n]>\n<dataset>\n"
                + "    <USERS ID=\"1\" NAME=\"Alice\"/>\n</dataset>\n";
        final IDocument document = new Document(text);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.insertBlankRow("USERS", 0);

        assertThat(document.get()).as("With a DTD, the first row does not decide the columns.")
                .isEqualTo(text.replace("    <USERS ID=\"1\"", "    <USERS ID=\"\"/>\n    <USERS ID=\"1\""));
    }

    @Test
    void testInsertBlankRow_aboveTheFirstRowWhenColumnSensingIsOn_hasAnEmptyStringInTheFirstColumnOnly()
    {
        final String text = "<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n</dataset>\n";
        final IDocument document = new Document(text);
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                new FlatXmlOptions(false, true), () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();

        datasetDocument.insertBlankRow("USERS", 0);

        assertThat(document.get()).as("With column sensing, the first row does not decide the columns.")
                .isEqualTo(text.replace("    <USERS ID=\"1\"", "    <USERS ID=\"\"/>\n    <USERS ID=\"1\""));
    }

    @Test
    void testInsertBlankRow_whenTheTableHasNoColumns_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset>\n    <USERS/>\n</dataset>\n");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.insertBlankRow("USERS", 0))
                .as("A row cannot be written without columns.").isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testSetCells_whenTheFirstRowWouldLoseAColumnThatAnotherRowHas_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n"
                + "    <USERS ID=\"2\" NAME=\"Bob\"/>\n</dataset>\n");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", null))))
                .as("dbUnit would ignore Bob's name, so the edit must be refused.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testInsertRows_whenAllValuesAreNull_throwsAndChangesNothing()
    {
        final IDocument document =
                new Document("<dataset>\n    <USERS ID=\"1\" NAME=\"Alice\"/>\n</dataset>\n");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(
                () -> datasetDocument.insertRows("USERS", 1, List.of(Arrays.asList(null, null))))
                .as("An all-null new row must be rejected, the same as a change that would empty an "
                        + "existing row.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
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
                .as("An out-of-range row index must be rejected with a message naming the row and table.")
                .isInstanceOf(DatasetEditException.class)
                .hasMessage("Row 5 is out of range for table 'USERS'.");
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
    void testAddColumn_whenAnEarlierEditExists_undoRemovesOnlyThePendingColumn() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Bob")));
            final String afterCellEdit = document.get();

            datasetDocument.addColumn("USERS", "EXTRA");

            undoManager.undo();
            assertThat(document.get())
                    .as("One undo must remove the pending column, not the earlier cell edit.")
                    .isEqualTo(afterCellEdit);
            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumnIndex("EXTRA"))
                    .as("The pending column must be gone after undo.").isEqualTo(-1);
            undoManager.undo();
            assertThat(document.get()).as("A second undo must revert the earlier cell edit.")
                    .isEqualTo(original);
        });
    }

    @Test
    void testAddColumn_whenPendingAddIsUndone_redoRestoresIt() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "EXTRA");

            undoManager.undo();
            undoManager.redo();

            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getColumns().get(table.getColumnIndex("EXTRA")))
                    .as("Redo must bring the pending column back.")
                    .isEqualTo(new DatasetColumn("EXTRA", false, false, true));
        });
    }

    @Test
    void testSetCells_whenFillingAPendingColumnIsUndone_theColumnStaysPending() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "NAME");

            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Alice")));
            undoManager.undo();
            datasetDocument.refresh();

            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getColumns().get(table.getColumnIndex("NAME")))
                    .as("Undoing the typed value must leave the column pending again, not remove it.")
                    .isEqualTo(new DatasetColumn("NAME", false, false, true));
        });
    }

    @Test
    void testAddColumn_whenFilledAndBothStepsAreUndoneBeforeAnyRefresh_theColumnIsGone() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "NAME");
            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Alice")));

            undoManager.undo();
            undoManager.undo();

            assertThat(document.get()).as("Both undo steps must bring back the original text.")
                    .isEqualTo(original);
            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("Undoing the add must remove the column, though the typed value's undo left "
                            + "the model without a refresh.")
                    .containsExactly(new DatasetColumn("ID", false, true, false));
        });
    }

    @Test
    void testAddColumn_whenFilledAndBothStepsAreUndoneBeforeAnyRefresh_redoOfTheAddLeavesNoTwinToRename()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "NAME");
            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Alice")));
            undoManager.undo();
            undoManager.undo();

            undoManager.redo();
            datasetDocument.renameColumn("USERS", "NAME", "OTHER");

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("Redo of the add must list the column once, or the rename leaves a twin behind "
                            + "under the old name.")
                    .containsExactly(new DatasetColumn("ID", false, true, false),
                            new DatasetColumn("OTHER", false, false, true));
        });
    }

    @Test
    void testAddColumn_whenFilledAndBothStepsAreUndoneAndRedoneBeforeAnyRefresh_theColumnHoldsTheValue()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "NAME");
            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Alice")));
            undoManager.undo();
            undoManager.undo();

            undoManager.redo();
            undoManager.redo();
            datasetDocument.refresh();

            assertThat(document.get()).as("Redoing both steps must bring back the typed value.")
                    .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("The column must be a data column once, without a pending twin.")
                    .containsExactly(new DatasetColumn("ID", false, true, false),
                            new DatasetColumn("NAME", false, true, false));
        });
    }

    @Test
    void testDeleteColumn_whenAnotherColumnWasFilledAndBothStepsAreUndoneBeforeAnyRefresh_bothAreBack()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "FIRST");
            datasetDocument.addColumn("USERS", "SECOND");
            datasetDocument.deleteColumn("USERS", "SECOND");
            datasetDocument.setCells("USERS", List.of(new CellChange(0, "FIRST", "x")));

            undoManager.undo();
            undoManager.undo();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("Undoing the delete must bring the deleted column back after the other one, "
                            + "though the typed value's undo left the model without a refresh.")
                    .containsExactly(new DatasetColumn("ID", false, true, false),
                            new DatasetColumn("FIRST", false, false, true),
                            new DatasetColumn("SECOND", false, false, true));
        });
    }

    @Test
    void testDeleteColumn_whenAReloadedDtdNowDeclaresTheOtherColumn_undoPutsTheDeletedColumnBack()
            throws Exception
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><USERS ID=\"1\"/></dataset>");
        final MutableDtdSource dtdSource = new MutableDtdSource(
                "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY><!ATTLIST USERS ID CDATA #REQUIRED>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, dtdSource,
                    FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "FIRST");
            datasetDocument.addColumn("USERS", "SECOND");
            datasetDocument.deleteColumn("USERS", "SECOND");
            dtdSource.setText("<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY>"
                    + "<!ATTLIST USERS ID CDATA #REQUIRED FIRST CDATA #IMPLIED>");
            datasetDocument.reloadDtd();

            undoManager.undo();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("Undo must put the deleted column back, though the reload dropped the other "
                            + "pending column from the list because the DTD declares it now.")
                    .containsExactly(new DatasetColumn("ID", true, true, false),
                            new DatasetColumn("FIRST", true, false, false),
                            new DatasetColumn("SECOND", false, false, true));
        });
    }

    @Test
    void testRefresh_whenTheTextIsNotWellFormedForAWhile_keepsThePendingColumnsOfTheTablesAfterTheError()
            throws Exception
    {
        final IDocument document = new Document("<dataset><A x=\"1\"/><T ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();
        datasetDocument.addColumn("T", "C");

        document.replace(document.get().indexOf("<A"), 0, "<");
        datasetDocument.refresh();
        assertThat(datasetDocument.getModel().isEditable())
                .as("The stray character must stop the parse before the table.").isFalse();
        document.replace(document.get().indexOf("<<A"), 1, "");
        datasetDocument.refresh();

        assertThat(datasetDocument.getModel().findTable("T").orElseThrow().getColumns())
                .as("The pending column must survive a model that lacked its table for a moment.")
                .containsExactly(new DatasetColumn("ID", false, true, false),
                        new DatasetColumn("C", false, false, true));
    }

    @Test
    void testRefresh_whenATextChangeFollowsAnUnrefreshedUndo_keepsThePendingColumnThatTheUndoBroughtBack()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "NAME");
            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Alice")));
            undoManager.undo();

            document.replace(document.get().indexOf("</dataset>"), 0, "<ORDERS ID=\"5\"/>");
            datasetDocument.refresh();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("The column must stay pending, though a new text change followed the undo of its "
                            + "value before anything refreshed.")
                    .containsExactly(new DatasetColumn("ID", false, true, false),
                            new DatasetColumn("NAME", false, false, true));
        });
    }

    @Test
    void testRenameTable_whenUndoneAndRedoneBeforeAnyRefresh_thePendingColumnFollowsTheTable()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "EXTRA");
            datasetDocument.renameTable("USERS", "CUSTOMERS");

            undoManager.undo();
            undoManager.redo();
            datasetDocument.refresh();

            assertThat(datasetDocument.getModel().findTable("CUSTOMERS").orElseThrow().getColumns())
                    .as("The redo of the rename must leave the pending column with the renamed table.")
                    .containsExactly(new DatasetColumn("ID", false, true, false),
                            new DatasetColumn("EXTRA", false, false, true));
        });
    }

    @Test
    void testBatch_whenUndoneAndRedoneWithAPendingColumnInTheTable_theColumnStaysPending() throws Exception
    {
        final IDocument document = new Document(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "EXTRA");
            datasetDocument.batch(() ->
            {
                datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Alicia")));
                datasetDocument.setCells("USERS", List.of(new CellChange(1, "NAME", "Robert")));
            });

            undoManager.undo();
            undoManager.redo();
            datasetDocument.refresh();

            assertThat(document.get()).as("The redo must bring back both changes of the batch.").isEqualTo(
                    "<dataset><USERS ID=\"1\" NAME=\"Alicia\"/><USERS ID=\"2\" NAME=\"Robert\"/></dataset>");
            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("The pending column must stay, whichever intermediate state the redo passes "
                            + "through.")
                    .containsExactly(new DatasetColumn("ID", false, true, false),
                            new DatasetColumn("NAME", false, true, false),
                            new DatasetColumn("EXTRA", false, false, true));
        });
    }

    @Test
    void testRefresh_whenTheExternalDtdIsNotLoadedForAWhile_keepsThePendingColumnsOfATableOnlyItDeclares()
    {
        final IDocument document =
                new Document("<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset></dataset>");
        final AtomicBoolean failing = new AtomicBoolean();
        final DtdSource source = (publicId, systemId) ->
        {
            if (failing.get())
            {
                throw new IllegalStateException("The DTD source is broken.");
            }
            return Optional.of(
                    "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY><!ATTLIST USERS ID CDATA #REQUIRED>");
        };
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, source,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        datasetDocument.addColumn("USERS", "NOTES");

        failing.set(true);
        datasetDocument.reloadDtd();
        assertThat(datasetDocument.getModel().findTable("USERS"))
                .as("Without the DTD the model must not list the table that only the DTD declares.")
                .isEmpty();
        failing.set(false);
        datasetDocument.reloadDtd();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                .as("The pending column must survive the DTD being unreadable for a moment.")
                .containsExactly(new DatasetColumn("ID", true, false, false),
                        new DatasetColumn("NOTES", false, false, true));
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
    void testRenameColumn_whenPendingColumnsAreEqualIgnoringCaseYetDistinct_renamesOnlyTheNamedOne()
    {
        final IDocument document = new Document("<dataset><USERS NAME=\"Alice\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();
        datasetDocument.addColumn("USERS", "ID");
        datasetDocument.addColumn("USERS", "\u0130D");

        datasetDocument.renameColumn("USERS", "\u0130D", "CODE");

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                .as("Renaming one pending column must not rename another whose name equals it only "
                        + "ignoring case.")
                .containsExactly(new DatasetColumn("NAME", false, true, false),
                        new DatasetColumn("ID", false, false, true),
                        new DatasetColumn("CODE", false, false, true));
    }

    @Test
    void testRenameColumn_whenPendingRenameIsUndone_restoresTheOldPendingName() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "EXTRA");

            datasetDocument.renameColumn("USERS", "EXTRA", "NOTES");
            undoManager.undo();

            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getColumnIndex("NOTES")).as("The new pending name must be gone.")
                    .isEqualTo(-1);
            assertThat(table.getColumns().get(table.getColumnIndex("EXTRA")))
                    .as("Undo must restore the old pending name.")
                    .isEqualTo(new DatasetColumn("EXTRA", false, false, true));
        });
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
                .isInstanceOf(DatasetEditException.class)
                .hasMessage("Table 'USERS' already has a column named 'id'.");
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
    void testRenameColumn_whenColumnIsDeclaredWithoutValues_throwsAndChangesNothing()
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.renameColumn("USERS", "NAME", "FULL_NAME"))
                .as("A declared column with no values must be rejected instead of silently doing "
                        + "nothing.")
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
    void testDeleteColumn_whenPendingColumnsAreEqualIgnoringCaseYetDistinct_dropsAndRestoresOnlyTheNamedOne()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS NAME=\"Alice\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "ID");
            datasetDocument.addColumn("USERS", "\u0130D");
            final List<DatasetColumn> beforeDelete =
                    datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns();

            datasetDocument.deleteColumn("USERS", "\u0130D");

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("Deleting one pending column must not drop another whose name equals it only "
                            + "ignoring case.")
                    .containsExactly(new DatasetColumn("NAME", false, true, false),
                            new DatasetColumn("ID", false, false, true));

            undoManager.undo();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("Undo must restore the deleted pending column after the one it is equal to "
                            + "only ignoring case.")
                    .isEqualTo(beforeDelete);
        });
    }

    @Test
    void testDeleteColumn_whenPendingColumnDeleteIsUndone_restoresItAtItsOriginalPosition()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "FIRST");
            datasetDocument.addColumn("USERS", "SECOND");
            datasetDocument.addColumn("USERS", "THIRD");
            final List<DatasetColumn> beforeDelete =
                    datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns();

            datasetDocument.deleteColumn("USERS", "SECOND");
            undoManager.undo();

            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getColumns())
                    .as("Undo must restore the deleted pending column at its original position.")
                    .isEqualTo(beforeDelete);
        });
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
    void testAddTable_whenRootIsSelfClosingWithAttributes_keepsThemOnTheOpenTag() throws Exception
    {
        final IDocument document = new Document("<dataset foo=\"bar\"/>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.addTable("USERS", List.of());

            assertThat(document.get())
                    .as("The root's attributes must survive turning a self-closing root into an open tag.")
                    .isEqualTo("<dataset foo=\"bar\">\n  <USERS/>\n</dataset>\n");
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
    void testAddTable_whenCreationIsUndoneAndTheNameIsReused_hasNoLeftoverPendingColumns()
            throws Exception
    {
        final IDocument document = new Document("<dataset>\n</dataset>\n");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addTable("ITEMS", List.of("SKU", "QTY"));

            undoManager.undo();
            datasetDocument.refresh();

            assertThat(datasetDocument.getModel().findTable("ITEMS"))
                    .as("The undone table must be gone from the model.").isEmpty();
            datasetDocument.addTable("ITEMS", List.of());

            final DatasetTable table = datasetDocument.getModel().findTable("ITEMS").orElseThrow();
            assertThat(table.getColumns())
                    .as("The undone table's pending columns must not leak into the reused name.")
                    .isEmpty();
        });
    }

    @Test
    void testAddTable_whenAdditionIsUndoneAndRedone_thePendingColumnsComeBack() throws Exception
    {
        final IDocument document = new Document("<dataset>\n</dataset>\n");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addTable("ITEMS", List.of("SKU", "QTY"));

            undoManager.undo();
            datasetDocument.refresh();
            assertThat(datasetDocument.getModel().findTable("ITEMS"))
                    .as("Undo must remove the added table.").isEmpty();

            undoManager.redo();
            datasetDocument.refresh();

            final DatasetTable table = datasetDocument.getModel().findTable("ITEMS").orElseThrow();
            assertThat(table.getColumns())
                    .as("Redo must bring the table's pending columns back with it.")
                    .containsExactly(new DatasetColumn("SKU", false, false, true),
                            new DatasetColumn("QTY", false, false, true));
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
    void testAddTable_whenAnExistingTableIsEqualIgnoringCaseYetDistinct_addsTheTable()
    {
        final IDocument document = new Document("<dataset><\u0130TEMS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.addTable("ITEMS", List.of());

        assertThat(datasetDocument.getModel().getTables()).extracting(DatasetTable::getKey)
                .as("A table whose key differs from that of every existing table must be added, although "
                        + "its name equals one of theirs ignoring case.")
                .containsExactly("\u0130TEMS", "ITEMS");
    }

    @Test
    void testAddTable_whenAnExistingTableSharesTheKeyYetIsNotEqualIgnoringCase_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><straße ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.addTable("STRASSE", List.of()))
                .as("A name with the key of an existing table must be rejected, although the two names are "
                        + "not equal ignoring case.")
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
    void testAddTable_whenColumnNamesAreEqualIgnoringCaseYetDistinct_recordsBoth()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.addTable("ORDERS", List.of("ID", "\u0130D"));

        assertThat(datasetDocument.getModel().findTable("ORDERS").orElseThrow().getColumns())
                .as("Column names with different keys must both be recorded, although they are equal "
                        + "ignoring case.")
                .containsExactly(new DatasetColumn("ID", false, false, true),
                        new DatasetColumn("\u0130D", false, false, true));
    }

    @Test
    void testAddTable_whenColumnNamesShareAKeyYetAreNotEqualIgnoringCase_throwsAndChangesNothing()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.addTable("ORDERS", List.of("straße", "STRASSE")))
                .as("Two column names with the same key must be rejected as duplicates, although they "
                        + "are not equal ignoring case.")
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
    void testRenameTable_whenUndone_thePendingColumnAddedBeforeItSurvivesUnderTheOldName()
            throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "EXTRA");

            datasetDocument.renameTable("USERS", "CUSTOMERS");
            undoManager.undo();
            datasetDocument.refresh();

            final DatasetTable tableAfterRenameUndo =
                    datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(tableAfterRenameUndo.getColumns())
                    .as("Undoing the rename must bring the pending column back under the old table name.")
                    .contains(new DatasetColumn("EXTRA", false, false, true));

            undoManager.undo();
            datasetDocument.refresh();

            final DatasetTable tableAfterColumnUndo =
                    datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(tableAfterColumnUndo.getColumnIndex("EXTRA"))
                    .as("A second undo, of the column add, must still remove the pending column.")
                    .isEqualTo(-1);
        });
    }

    @Test
    void testRenameTable_whenTheInternalSubsetDeclaresTheTable_renamesTheDeclarationsInTheSameUndoStep()
            throws Exception
    {
        final String original = "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*, ORDERS*)>\n"
                + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #REQUIRED STATUS CDATA \"ACTIVE\">\n"
                + "<!ELEMENT ORDERS EMPTY>\n<!ATTLIST ORDERS ID CDATA #REQUIRED>\n]>\n"
                + "<dataset>\n  <USERS ID=\"1\"/>\n  <ORDERS ID=\"10\"/>\n</dataset>\n";
        final IDocument document = new Document(original);
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.renameTable("USERS", "ACCOUNTS");

            assertThat(document.get()).as("The elements and the DTD must take the new name together.")
                    .isEqualTo(original.replace("USERS", "ACCOUNTS"));
            final DatasetModel model = datasetDocument.getModel();
            assertThat(model.getTables())
                    .as("The renamed table must replace the old one, and no table must be left that only "
                            + "the DTD declares.")
                    .extracting(DatasetTable::getName).containsExactly("ACCOUNTS", "ORDERS");
            assertThat(model.getProblems())
                    .as("The DTD must still declare every table of the dataset.").isEmpty();
            undoManager.undo();
            assertThat(document.get()).as("One undo must restore the elements and the DTD together.")
                    .isEqualTo(original);
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
    void testRenameTable_whenAnotherTableIsEqualIgnoringCaseYetDistinct_renamesTheTable()
    {
        final IDocument document =
                new Document("<dataset><USERS ID=\"1\"/><\u0130TEMS ID=\"9\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        datasetDocument.renameTable("USERS", "ITEMS");

        assertThat(document.get())
                .as("A new name whose key differs from that of every other table must be accepted, "
                        + "although it equals the name of one of them ignoring case.")
                .isEqualTo("<dataset><ITEMS ID=\"1\"/><\u0130TEMS ID=\"9\"/></dataset>");
    }

    @Test
    void testRenameTable_whenAnotherTableSharesTheKeyYetIsNotEqualIgnoringCase_throwsAndChangesNothing()
    {
        final IDocument document =
                new Document("<dataset><USERS ID=\"1\"/><straße ID=\"9\"/></dataset>");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.renameTable("USERS", "STRASSE"))
                .as("A new name with the key of another table must be rejected, because the rename would "
                        + "merge the two tables, although the names are not equal ignoring case.")
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
    void testRenameTable_whenTableIsDeclaredOnly_throwsAndChangesNothing()
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.renameTable("USERS", "CUSTOMERS"))
                .as("A declared-only table must be rejected instead of silently doing nothing.")
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

    @Test
    void testDeleteRows_whenEveryRowOfATableWithDefaultValuesIsDeleted_leavesADeclaredOnlyTable()
            throws Exception
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED STATUS CDATA \"ACTIVE\">\n]>\n<dataset>\n"
                        + "    <USERS ID=\"1\"/>\n    <USERS ID=\"2\"/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.deleteRows("USERS", new int[] { 0, 1 });

            assertThat(document.get())
                    .as("No empty element may be left: dbUnit loads it as a row of default values.")
                    .isEqualTo("<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED STATUS CDATA \"ACTIVE\">\n]>\n<dataset>\n"
                            + "</dataset>\n");
            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getRows()).as("The table must have no rows left.").isEmpty();
            assertThat(table.isDeclaredOnly()).as("The DTD must keep the table, as declared-only.").isTrue();
            assertThat(table.getColumns()).as("The table must keep its columns and their defaults.")
                    .containsExactly(new DatasetColumn("ID", true, false, false),
                            new DatasetColumn("STATUS", true, false, false, "ACTIVE"));
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testSetCells_whenTheRowIsAnEmptyElementOfDefaultValues_addsTheAttributeToIt() throws Exception
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #IMPLIED STATUS CDATA \"ACTIVE\">\n]>\n<dataset>\n"
                        + "    <USERS/>\n</dataset>\n");
        final String original = document.get();
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();

            datasetDocument.setCells("USERS", List.of(new CellChange(0, "ID", "5")));

            assertThat(document.get()).as("The empty element is the table's row, so it takes the value.")
                    .isEqualTo("<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #IMPLIED STATUS CDATA \"ACTIVE\">\n]>\n<dataset>\n"
                            + "    <USERS ID=\"5\"/>\n</dataset>\n");
            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getRows().get(0).getValues())
                    .as("The row must hold the new value and still no value for the defaulted column.")
                    .containsExactly("5", null);
            undoManager.undo();
            assertThat(document.get()).isEqualTo(original);
        });
    }

    @Test
    void testDeleteTable_whenTableIsDeclaredOnly_throwsAndChangesNothing()
    {
        final IDocument document = new Document(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
        final String original = document.get();
        final FlatXmlDatasetDocument datasetDocument = create(document);
        datasetDocument.refresh();

        assertThatThrownBy(() -> datasetDocument.deleteTable("USERS"))
                .as("A declared-only table must be rejected instead of silently doing nothing.")
                .isInstanceOf(DatasetEditException.class);
        assertThat(document.get()).as("The document must be unchanged.").isEqualTo(original);
    }

    @Test
    void testDeleteTable_whenUndone_itsPendingColumnsComeBack() throws Exception
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\"/></dataset>");
        withUndoManager(document, undoManager ->
        {
            final FlatXmlDatasetDocument datasetDocument = create(document);
            datasetDocument.refresh();
            datasetDocument.addColumn("USERS", "EXTRA");

            datasetDocument.deleteTable("USERS");
            undoManager.undo();
            datasetDocument.refresh();

            final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();
            assertThat(table.getColumns())
                    .as("Undoing the delete must bring the table's pending column back.")
                    .contains(new DatasetColumn("EXTRA", false, false, true));
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
