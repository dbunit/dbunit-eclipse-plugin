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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * The fixture of the tests of the actions of the Tables page: a shell for the dialogs and cell editors
 * that an action opens, the documents that the actions act on, and a grid context in which a test sets
 * the selection and the state of the page, and reads what the action did.
 */
abstract class GridActionFixture
{
    static final String DEFAULTS_DATASET = "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n"
            + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #REQUIRED STATUS CDATA \"ACTIVE\">\n]>\n"
            + "<dataset><USERS ID=\"1\"/><USERS ID=\"2\" STATUS=\"x\"/></dataset>";

    Shell shell;

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

    static List<String> firstColumnValues(final FlatXmlDatasetDocument datasetDocument,
            final String tableKey)
    {
        final DatasetTable table = datasetDocument.getModel().findTable(tableKey).orElseThrow();
        final List<String> values = new ArrayList<>();
        for (final DatasetRow row : table.getRows())
        {
            values.add(row.getValues().get(0));
        }
        return values;
    }

    static FlatXmlDatasetDocument create(final String content)
    {
        return create(new Document(content));
    }

    static FlatXmlDatasetDocument create(final IDocument document)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(document, DtdSource.NONE,
                FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }

    static FlatXmlDatasetDocument createWithDtdFile(final String content, final String dtdText)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(new Document(content),
                (publicId, systemId) -> Optional.of(dtdText), FlatXmlOptions.DBUNIT_DEFAULTS,
                () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }

    static FlatXmlDatasetDocument createCaseSensitive(final String content)
    {
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(new Document(content),
                DtdSource.NONE, new FlatXmlOptions(true, FlatXmlOptions.DBUNIT_DEFAULTS.columnSensing()),
                () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument;
    }

    final class TestContext implements DatasetGridContext
    {
        final DatasetDocument datasetDocument;

        final String tableKey;

        boolean editable = true;

        boolean hasActiveCellEditor;

        final List<String> multiCellEditTitles = new ArrayList<>();

        int anchorColumnIndex = -1;

        int anchorRowIndex = -1;

        List<Integer> rowIndexes = List.of();

        List<Integer> columnIndexes = List.of();

        boolean wholeRowsSelected;

        List<Point> selectedCellPositions = List.of();

        int selectedCellPositionReads;

        Text activeCellEditorText;

        boolean selectAllCalled;

        boolean editCellInDialogCalled;

        boolean showInSourceCalled;

        String statusMessage;

        String statusErrorMessage;

        GridSelection staleSelection;

        String clipboardText;

        Rectangle selectedRegion;

        TestContext(final DatasetDocument datasetDocument, final String tableKey)
        {
            this.datasetDocument = datasetDocument;
            this.tableKey = tableKey;
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
                return true;
            }
            catch (final DatasetEditException e)
            {
                return false;
            }
        }

        @Override
        public boolean executeMultiCellEdit(final String title, final Runnable edit)
        {
            multiCellEditTitles.add(title);
            return executeEdit(edit);
        }

        @Override
        public void fillContextMenu(final IMenuManager menu, final String region)
        {
        }

        @Override
        public boolean hasActiveCellEditor()
        {
            return hasActiveCellEditor;
        }

        @Override
        public GridSelection getSelection()
        {
            if (staleSelection != null)
            {
                return staleSelection;
            }
            final DatasetTable table = datasetDocument.getModel().findTable(tableKey).orElseThrow();
            final int rowCount = table.getRows().size();
            final int columnCount = table.getColumns().size();
            return new GridSelection(tableKey, rowCount, columnCount, anchorColumnIndex, anchorRowIndex,
                    rowIndexes, columnIndexes, wholeRowsSelected);
        }

        @Override
        public void selectRegion(final int firstColumnIndex, final int firstRowIndex, final int columnCount,
                final int rowCount)
        {
            selectedRegion = new Rectangle(firstColumnIndex, firstRowIndex, columnCount, rowCount);
        }

        @Override
        public Shell getShell()
        {
            return shell;
        }

        @Override
        public Text getActiveCellEditorText()
        {
            return activeCellEditorText;
        }

        @Override
        public List<Point> getSelectedCellPositions()
        {
            selectedCellPositionReads++;
            return selectedCellPositions;
        }

        @Override
        public void selectAll()
        {
            selectAllCalled = true;
        }

        @Override
        public void editCellInDialog()
        {
            editCellInDialogCalled = true;
        }

        @Override
        public void showInSource()
        {
            showInSourceCalled = true;
        }

        @Override
        public void setStatusMessage(final String message)
        {
            statusMessage = message;
        }

        @Override
        public void setStatusErrorMessage(final String message)
        {
            statusErrorMessage = message;
        }

        @Override
        public void writeClipboardText(final String text)
        {
            clipboardText = text;
        }

        @Override
        public String readClipboardText()
        {
            return clipboardText;
        }
    }
}
