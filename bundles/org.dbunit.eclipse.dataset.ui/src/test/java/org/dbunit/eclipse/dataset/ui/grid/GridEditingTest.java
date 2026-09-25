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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.data.validate.ValidationFailedException;
import org.eclipse.nebula.widgets.nattable.edit.command.EditSelectionCommand;
import org.eclipse.nebula.widgets.nattable.edit.editor.ICellEditor;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer.MoveDirectionEnum;
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
 * Tests in-place editing against the Grid specification: {@link TableBodyDataProvider#setDataValue},
 * {@link XmlCharacterValidator}, and {@link PrintableCharacterKeyEventMatcher}.
 */
class GridEditingTest
{
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
    void testSetDataValue_whenAValueChanges_rewritesOnlyThatAttribute()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
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
        final TestContext context = new TestContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 1, "");

        assertThat(provider.getDataValue(1, 1)).as("An empty edit of a NULL cell must keep it NULL.")
                .isNull();
        assertThat(document.get()).as("An empty edit of a NULL cell must not change the document.")
                .isEqualTo(originalText);
    }

    @Test
    void testSetDataValue_whenEditingANonNullCellWithTheEmptyString_storesTheEmptyString()
    {
        final IDocument document = new Document("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument);
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
        final TestContext context = new TestContext(datasetDocument);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(0, 0, null);

        assertThat(document.get()).as("A rejected edit must leave the document unchanged.")
                .isEqualTo(originalText);
        assertThat(context.lastErrorMessage)
                .as("A rejected edit must set the status line error message.")
                .isEqualTo("This change would leave row 0 of table 'USERS' with no values.");
    }

    @Test
    void testSetDataValue_whenTheInputIsReadOnly_rejectsTheEdit()
    {
        final String originalText = "<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>";
        final IDocument document = new Document(originalText);
        final FlatXmlDatasetDocument datasetDocument = create(document);
        final TestContext context = new TestContext(datasetDocument, false);
        final TableBodyDataProvider provider = new TableBodyDataProvider(context, "USERS");

        provider.setDataValue(1, 0, "Carol");

        assertThat(document.get()).as("A read-only input must reject the edit.").isEqualTo(originalText);
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
    void testValidate_whenTheValueContainsANonXmlCharacter_throwsNamingTheCharacter()
    {
        final XmlCharacterValidator validator = new XmlCharacterValidator();

        assertThatThrownBy(() -> validator.validate(0, 0, "a\u0001b"))
                .as("A code point outside the XML 1.0 Char production must be rejected.")
                .isInstanceOf(ValidationFailedException.class)
                .hasMessage("Character U+0001 is not allowed in XML.");
    }

    @Test
    void testMatches_forPrintableCharactersWithNoOrExpectedModifiers_returnsTrue()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher();

        assertThat(matcher.matches(keyEvent('a', 'a', SWT.NONE))).as("A plain letter must match.").isTrue();
        assertThat(matcher.matches(keyEvent('5', '5', SWT.NONE))).as("A plain digit must match.").isTrue();
        assertThat(matcher.matches(keyEvent('.', '.', SWT.NONE))).as("Plain punctuation must match.")
                .isTrue();
        assertThat(matcher.matches(keyEvent(' ', ' ', SWT.NONE))).as("A plain space must match.").isTrue();
        assertThat(matcher.matches(keyEvent('A', 'a', SWT.SHIFT))).as("Shift plus a letter must match.")
                .isTrue();
        assertThat(matcher.matches(keyEvent('a', 'a', SWT.MOD1 | SWT.MOD3)))
                .as("AltGr (Ctrl+Alt) plus a character must match.").isTrue();
        assertThat(matcher.matches(keyEvent('A', 'a', SWT.MOD1 | SWT.MOD3 | SWT.SHIFT)))
                .as("AltGr with Shift plus a character must match.").isTrue();
    }

    @Test
    void testMatches_forControlCharactersOrNonTypingKeys_returnsFalse()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher();

        assertThat(matcher.matches(keyEvent((char) 3, 'c', SWT.MOD1))).as("Ctrl+C must not match.")
                .isFalse();
        assertThat(matcher.matches(keyEvent((char) 0, SWT.ARROW_RIGHT, SWT.NONE)))
                .as("An arrow key must not match.").isFalse();
        assertThat(matcher.matches(keyEvent((char) 0, SWT.F2, SWT.NONE)))
                .as("A function key must not match.").isFalse();
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
        shell.setSize(400, 300);
        final DatasetGrid grid = new DatasetGrid(shell, new TestContext(datasetDocument), tableKey);
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

    private static final class TestContext implements DatasetGridContext
    {
        private final DatasetDocument datasetDocument;

        private final boolean editable;

        private String lastErrorMessage;

        TestContext(final DatasetDocument datasetDocument)
        {
            this(datasetDocument, true);
        }

        TestContext(final DatasetDocument datasetDocument, final boolean editable)
        {
            this.datasetDocument = datasetDocument;
            this.editable = editable;
        }

        @Override
        public DatasetDocument getDatasetDocument()
        {
            return datasetDocument;
        }

        @Override
        public boolean isEditable()
        {
            return editable;
        }

        @Override
        public String getNullDisplayText()
        {
            return "(null)";
        }

        @Override
        public boolean isDarkTheme()
        {
            return false;
        }

        @Override
        public boolean executeEdit(final Runnable edit)
        {
            if (!editable)
            {
                return false;
            }
            try
            {
                edit.run();
                lastErrorMessage = null;
                return true;
            }
            catch (final DatasetEditException e)
            {
                lastErrorMessage = e.getMessage();
                return false;
            }
        }

        @Override
        public boolean executeMultiCellEdit(final String title, final Runnable edit)
        {
            return executeEdit(edit);
        }

        @Override
        public void fillContextMenu(final IMenuManager menu, final String region)
        {
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
        public void setPendingSelection(final int columnIndex, final int rowIndex)
        {
        }

        @Override
        public Shell getShell()
        {
            return null;
        }
    }
}
