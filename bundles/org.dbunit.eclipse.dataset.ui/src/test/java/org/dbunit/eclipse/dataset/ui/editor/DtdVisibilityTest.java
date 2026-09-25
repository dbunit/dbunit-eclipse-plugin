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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.eclipse.core.resources.IFile;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.junit.jupiter.api.Test;

/**
 * Tests that the Tables page makes a dataset's DTD declarations visible, end to end through a real editor.
 */
class DtdVisibilityTest
{
    @Test
    void testTablesPage_withAnInternalDtdSubset_showsDeclaredColumnsInOrderAndADeclaredOnlyTable()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*,ORDERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n"
                            + "<!ELEMENT ORDERS EMPTY>\n<!ATTLIST ORDERS ID CDATA #REQUIRED>\n]>\n"
                            + "<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final DatasetModel model = editor.getDatasetDocument().getModel();

            assertThat(tablesPage.getTabFolder().getItemCount())
                    .as("Both the real and the declared-only table must get a tab.").isEqualTo(2);

            final DatasetTable usersTable = model.findTable("USERS").orElseThrow();
            assertThat(usersTable.getColumns()).as("Declared columns must appear in DTD order.")
                    .containsExactly(new DatasetColumn("ID", true, true, false),
                            new DatasetColumn("NAME", true, false, false));
            assertThat(usersTable.isDeclaredOnly())
                    .as("A table with an element in the document is not declared-only.").isFalse();

            final DatasetTable ordersTable = model.findTable("ORDERS").orElseThrow();
            assertThat(ordersTable.isDeclaredOnly())
                    .as("A table with no elements in the document must be declared-only.").isTrue();

            final CTabItem ordersTab = tabForTable(tablesPage, "ORDERS");
            assertThat(ordersTab.getToolTipText())
                    .as("A declared-only table's tab must explain why it has no rows.")
                    .isEqualTo("Declared in the DTD; no rows");
            assertThat(ordersTab.getFont().getFontData()[0].getStyle() & SWT.ITALIC)
                    .as("A declared-only table's tab must be italic.").isEqualTo(SWT.ITALIC);
        }
    }

    @Test
    void testTablesPage_afterReloadingAChangedExternalDtd_updatesTheColumns() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile dtdFile = workspace.createFile("my.dtd",
                    "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED>\n");
            final IFile datasetFile = workspace.createFile("dataset.xml",
                    "<!DOCTYPE dataset SYSTEM \"my.dtd\">\n<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(datasetFile);
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("Before the DTD changes, only the originally declared column must appear.")
                    .containsExactly(new DatasetColumn("ID", true, true, false));

            dtdFile.setContents(
                    new ByteArrayInputStream(("<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n")
                                    .getBytes(StandardCharsets.UTF_8)),
                    true, false, null);
            datasetDocument.reloadDtd();
            UiTestWorkspace.processEvents();

            assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns())
                    .as("Reloading a changed DTD must show the newly declared column.")
                    .containsExactly(new DatasetColumn("ID", true, true, false),
                            new DatasetColumn("NAME", true, false, false));
        }
    }

    @Test
    void testTablesPage_whenTheExternalDtdIsMissing_reportsDtdNotLoaded() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<!DOCTYPE dataset SYSTEM "
                    + "\"missing.dtd\">\n<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            final List<DatasetProblem> problems = editor.getDatasetDocument().getModel().getProblems();

            assertThat(problems).as("A missing external DTD must report DTD_NOT_LOADED.")
                    .anyMatch(problem -> problem.code() == ProblemCode.DTD_NOT_LOADED);
        }
    }

    private static CTabItem tabForTable(final TablesPage tablesPage, final String tableName)
    {
        for (final CTabItem item : tablesPage.getTabFolder().getItems())
        {
            if (item.getText().equals(tableName))
            {
                return item;
            }
        }
        throw new AssertionError("No tab named " + tableName + ".");
    }
}
