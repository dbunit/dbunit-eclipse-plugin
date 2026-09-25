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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PageSelectionSync} against the Page Synchronization rules.
 */
class PageSelectionSyncTest
{
    @Test
    void testOnDeactivate_withASelectedCell_selectsAndRevealsItsAttributeValueOnSource()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final List<int[]> revealed = new ArrayList<>();
        final PageSelectionSync sync = new PageSelectionSync(() -> new Region(0, 0),
                (offset, length) -> revealed.add(new int[] { offset, length }), datasetDocument);

        sync.onDeactivate(new CellAddress("USERS", 0, 1));

        assertThat(revealed).as("Leaving with a selected cell must select and reveal it once.").hasSize(1);
        final int offset = revealed.get(0)[0];
        final int length = revealed.get(0)[1];
        assertThat(document.get().substring(offset, offset + length))
                .as("The revealed range must be the cell's attribute value.").isEqualTo("Alice");
    }

    @Test
    void testOnActivate_whenTheSourceSelectionIsUnchanged_keepsTheGridSelectionEvenForANullCell()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\"/></dataset>");
        final int[] currentSelection = new int[2];
        final PageSelectionSync sync = new PageSelectionSync(
                () -> new Region(currentSelection[0], currentSelection[1]), (offset, length) ->
                {
                    currentSelection[0] = offset;
                    currentSelection[1] = length;
                }, datasetDocument);

        sync.onDeactivate(new CellAddress("USERS", 1, 1));

        final Optional<CellAddress> result = sync.onActivate();

        assertThat(result)
                .as("An unchanged source selection must keep the grid selection, even for a NULL cell "
                        + "whose range is just its element name.")
                .isEmpty();
    }

    @Test
    void testOnActivate_whenTheSourceSelectionMoved_returnsTheCellAtTheNewCaretOffset()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final int nameValueOffset = document.get().indexOf("Alice");
        final PageSelectionSync sync = new PageSelectionSync(() -> new Region(nameValueOffset, 0),
                (offset, length) ->
                {
                }, datasetDocument);

        sync.onDeactivate(new CellAddress("USERS", 0, 0));

        final Optional<CellAddress> result = sync.onActivate();

        assertThat(result).as("A caret moved into a different attribute must select that cell.")
                .contains(new CellAddress("USERS", 0, 1));
    }

    private static FlatXmlDatasetDocument create(final String content)
    {
        return create(new Document(content));
    }

    private static FlatXmlDatasetDocument create(final IDocument document)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }
}
