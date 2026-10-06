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
import org.eclipse.nebula.widgets.nattable.selection.command.SelectCellCommand;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link AnchorCellKeyEditAction}, which starts an edit of the selected cell with the character
 * that was typed.
 */
class AnchorCellKeyEditActionTest
{
    private static final String FOUR_USERS = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/>"
            + "<USERS ID=\"2\" NAME=\"Bob\"/><USERS ID=\"3\" NAME=\"Carol\"/><USERS ID=\"4\" NAME=\"Dave\"/>"
            + "</dataset>";

    private Shell shell;

    @BeforeEach
    void createShell()
    {
        shell = new Shell(Display.getDefault());
    }

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testRun_whenTheKeyEventCarriesAPrintableCharacter_opensTheEditorSeededWithIt()
    {
        final DatasetGrid grid = openGrid(create(new Document(FOUR_USERS)), "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));

        new AnchorCellKeyEditAction(grid.getSelectionLayer()).run(natTable, keyEvent('x', 'x', SWT.NONE));

        assertThat(natTable.getActiveCellEditor().getEditorValue())
                .as("Typing a character on a selected cell must start an edit with that character.")
                .isEqualTo("x");
    }

    @Test
    void testRun_whenTheKeyEventCarriesHalfOfASupplementaryCharacter_doesNotSeedTheEditorWithIt()
    {
        final DatasetGrid grid = openGrid(create(new Document(FOUR_USERS)), "USERS");
        final NatTable natTable = grid.getNatTable();
        natTable.doCommand(new SelectCellCommand(natTable, 2, 2, false, false));
        final char highSurrogate = Character.highSurrogate(0x1F600);

        final KeyEvent typedHalf = keyEvent(highSurrogate, 0, SWT.NONE);
        new AnchorCellKeyEditAction(grid.getSelectionLayer()).run(natTable, typedHalf);

        final Object editorValue = natTable.getActiveCellEditor().getEditorValue();
        assertThat(String.valueOf(editorValue))
                .as("An editor seeded with half of a pair would hold a character that is not well formed.")
                .doesNotContain(String.valueOf(highSurrogate));
    }

    private KeyEvent keyEvent(final char character, final int keyCode, final int stateMask)
    {
        final Event event = new Event();
        event.widget = shell;
        event.character = character;
        event.keyCode = keyCode;
        event.stateMask = stateMask;
        return new KeyEvent(event);
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
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }
}
