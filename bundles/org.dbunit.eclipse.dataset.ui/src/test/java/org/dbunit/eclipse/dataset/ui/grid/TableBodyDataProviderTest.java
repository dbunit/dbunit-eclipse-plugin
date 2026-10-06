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

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link TableBodyDataProvider#setDataValue}, where an edit of a cell reaches the dataset
 * document, against the Grid specification's rules for editing: the values NULL and the empty string,
 * default values, line breaks, and a cell that is no longer in the table.
 */
class TableBodyDataProviderTest
{
    private static final String DEFAULTS_DATASET = "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
            + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #IMPLIED STATUS CDATA \"ACTIVE\">\n]>\n"
            + "<dataset><USERS ID=\"1\"/><USERS STATUS=\"x\"/></dataset>";

    private static final String CELL_GONE_MESSAGE =
            "The cell is no longer in the table, so its new value was not saved.";

    @Test
    void testSetDataValue_whenAValueChanges_rewritesOnlyThatAttribute()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final EditableGridContext context = new EditableGridContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "Carol");

        assertThat(document.get()).as("Only the changed attribute's text must change.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"Carol\"/></dataset>");
    }

    @Test
    void testSetDataValue_whenEditingANullCellWithTheEmptyString_staysNull()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>";
        final IDocument document = new Document(originalText);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final EditableGridContext context = new EditableGridContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 1, "");

        assertThat(provider.getDataValue(1, 1)).as("An empty edit of a NULL cell must keep it NULL.")
                .isNull();
        assertThat(document.get()).as("An empty edit of a NULL cell must not change the document.")
                .isEqualTo(originalText);
    }

    @Test
    void testSetDataValue_whenCommittingTheDisplayedDefaultOfAnOmittedAttribute_leavesTheDocumentUnchanged()
    {
        final IDocument document = new Document(DEFAULTS_DATASET);
        final TableBodyDataProvider provider =
                new TableBodyDataProvider(new EditableGridContext(create(document)), "USERS");

        provider.setDataValue(1, 0, "ACTIVE");

        assertThat(document.get())
                .as("Committing the default that the cell displays must not write an attribute for it.")
                .isEqualTo(DEFAULTS_DATASET);
    }

    @Test
    void testSetDataValue_whenReplacingTheDisplayedDefault_writesTheAttribute()
    {
        final IDocument document = new Document(DEFAULTS_DATASET);
        final TableBodyDataProvider provider =
                new TableBodyDataProvider(new EditableGridContext(create(document)), "USERS");

        provider.setDataValue(1, 0, "INACTIVE");

        assertThat(document.get()).as("A value other than the default must be written as an attribute.")
                .isEqualTo(DEFAULTS_DATASET.replace("<USERS ID=\"1\"/>",
                        "<USERS ID=\"1\" STATUS=\"INACTIVE\"/>"));
    }

    @Test
    void testSetDataValue_whenClearingTheDisplayedDefault_storesTheEmptyString()
    {
        final IDocument document = new Document(DEFAULTS_DATASET);
        final TableBodyDataProvider provider =
                new TableBodyDataProvider(new EditableGridContext(create(document)), "USERS");

        provider.setDataValue(1, 0, "");

        assertThat(document.get())
                .as("Clearing a cell that shows a default must store the empty string, which overrides it.")
                .isEqualTo(DEFAULTS_DATASET.replace("<USERS ID=\"1\"/>", "<USERS ID=\"1\" STATUS=\"\"/>"));
    }

    @Test
    void testSetDataValue_whenCommittingTheEmptyDefaultOfAnOmittedAttribute_leavesTheDocumentUnchanged()
    {
        final String originalText = DEFAULTS_DATASET.replace("\"ACTIVE\"", "\"\"");
        final IDocument document = new Document(originalText);
        final TableBodyDataProvider provider =
                new TableBodyDataProvider(new EditableGridContext(create(document)), "USERS");

        provider.setDataValue(1, 0, "");

        assertThat(document.get())
                .as("Committing an empty default that the cell displays must not write an attribute.")
                .isEqualTo(originalText);
    }

    @Test
    void testSetDataValue_whenEditingANonNullCellWithTheEmptyString_storesTheEmptyString()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final EditableGridContext context = new EditableGridContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "");

        assertThat(provider.getDataValue(1, 0))
                .as("An empty edit of a non-NULL cell must store the empty string, not NULL.").isEqualTo("");
        assertThat(document.get()).as("The stored empty string must appear as an empty attribute.")
                .isEqualTo("<dataset><USERS ID=\"1\" NAME=\"\"/></dataset>");
    }

    @Test
    void testSetDataValue_whenTheEditWouldEmptyARow_leavesTheDocumentUnchangedAndSetsTheStatusLineError()
    {
        final String originalText = "<dataset><USERS ID=\"1\"/></dataset>";
        final IDocument document = new Document(originalText);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final EditableGridContext context = new EditableGridContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(0, 0, null);

        assertThat(document.get()).as("A rejected edit must leave the document unchanged.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage)
                .as("A rejected edit must set the status line error message.")
                .isEqualTo("This change would leave row 0 of table 'USERS' with no values. Use Delete "
                        + "Rows to remove it instead.");
    }

    @Test
    void testSetDataValue_whenTheInputIsReadOnly_rejectsTheEdit()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final EditableGridContext context = new EditableGridContext(datasetDocument, false);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "Carol");

        assertThat(document.get()).as("A read-only input must reject the edit.").isEqualTo(originalText);
    }

    @Test
    void testSetDataValue_whenTheRowIsPastTheEnd_leavesTheDocumentUnchangedAndSetsTheStatusLineError()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final EditableGridContext context = new EditableGridContext(create(document));
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 1, "Carol");

        assertThat(document.get()).as("An edit of a row that is gone must leave the document unchanged.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage).as("The user must be told that the edit was not saved.")
                .isEqualTo(CELL_GONE_MESSAGE);
    }

    @Test
    void testSetDataValue_whenNatTableFoundNoRow_leavesTheDocumentUnchangedAndSetsTheStatusLineError()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final EditableGridContext context = new EditableGridContext(create(document));
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, -1, "Carol");

        assertThat(document.get()).as("NatTable passes -1 for a position that is out of range.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage).as("The user must be told that the edit was not saved.")
                .isEqualTo(CELL_GONE_MESSAGE);
    }

    @Test
    void testSetDataValue_whenTheColumnIsPastTheEnd_leavesTheDocumentUnchangedAndSetsTheStatusLineError()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final EditableGridContext context = new EditableGridContext(create(document));
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(2, 0, "Carol");

        assertThat(document.get()).as("An edit of a column that is gone must leave the document unchanged.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage).as("The user must be told that the edit was not saved.")
                .isEqualTo(CELL_GONE_MESSAGE);
    }

    @Test
    void testSetDataValue_whenNatTableFoundNoColumn_leavesTheDocumentUnchangedAndSetsTheStatusLineError()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final EditableGridContext context = new EditableGridContext(create(document));
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(-1, 0, "Carol");

        assertThat(document.get()).as("NatTable passes -1 for a position that is out of range.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage).as("The user must be told that the edit was not saved.")
                .isEqualTo(CELL_GONE_MESSAGE);
    }

    @Test
    void testSetDataValue_whenTheEditorTurnedLineFeedsIntoCrLf_keepsTheLineFeeds()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NOTE=\"first&#xA;second\"/></dataset>";
        final IDocument document = new Document(originalText);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final EditableGridContext context = new EditableGridContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "first\r\nsecond");

        assertThat(document.get())
                .as("A text widget that writes CR LF must not change a value with line feeds.")
                .isEqualTo(originalText);
    }

    @Test
    void testSetDataValue_whenTheValueUsedCrLf_keepsCrLf()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NOTE=\"a&#xD;&#xA;b\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final EditableGridContext context = new EditableGridContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "a\r\nb\r\nc");

        assertThat(document.get()).as("A value that used CR LF line breaks must keep them.")
                .isEqualTo("<dataset><USERS ID=\"1\" NOTE=\"a&#xD;&#xA;b&#xD;&#xA;c\"/></dataset>");
    }

    private static FlatXmlDatasetDocument create(final IDocument document)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }
}
