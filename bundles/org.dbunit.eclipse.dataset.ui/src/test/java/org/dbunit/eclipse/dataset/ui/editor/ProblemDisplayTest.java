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

import java.util.List;

import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.eclipse.core.resources.IFile;
import org.junit.jupiter.api.Test;

/**
 * Tests that the Tables page displays the dataset model's problems: column header and tab decoration, the
 * problems section, double-click navigation, and clearing once fixed.
 */
class ProblemDisplayTest
{
    @Test
    void testProblemDisplay_endToEnd_decoratesTheTabListsTheProblemAndSelectsTheCellOnDoubleClick()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final TablesPage tablesPage = editor.getTablesPage();
            final FlatXmlDatasetDocument datasetDocument = editor.getDatasetDocument();

            assertThat(tablesPage.getTabFolder().getItem(0).getImage())
                    .as("A table with a problem must show a warning or error image on its tab.")
                    .isNotNull();
            assertThat(tablesPage.getProblemsSection().getControl().getVisible())
                    .as("A dataset with a problem must show the problems section.").isTrue();
            assertThat(tablesPage.getProblemsSection().getHeaderText())
                    .as("The header must count the problems.").isEqualTo("Problems (1)");

            final DatasetProblem problem = datasetDocument.getModel().getProblems().get(0);
            tablesPage.selectProblem(problem);

            assertThat(tablesPage.getTabFolder().getSelectionIndex())
                    .as("Selecting the problem must select its table's tab.").isEqualTo(0);
            assertThat(tablesPage.getSelection().anchorColumnIndex())
                    .as("Selecting the problem must select its column.").isEqualTo(1);
            assertThat(tablesPage.getSelection().anchorRowIndex())
                    .as("Selecting the problem must select its row.").isEqualTo(problem.rowIndex());

            datasetDocument.setCells("USERS", List.of(new CellChange(0, "NAME", "Alice")));
            UiTestWorkspace.processEvents();

            assertThat(datasetDocument.getModel().getProblems())
                    .as("Fixing the first row must remove the problem.").isEmpty();
            assertThat(tablesPage.getProblemsSection().getControl().getVisible())
                    .as("With no problems, the problems section must hide again.").isFalse();
            assertThat(tablesPage.getTabFolder().getItem(0).getImage())
                    .as("With no problems, the tab image must clear.").isNull();
        }
    }

    @Test
    void testProblemDisplay_whenFileIsBlank_hidesTheProblemsSection() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("blank.xml", "");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            assertThat(editor.getTablesPage().getProblemsSection().getControl().getVisible())
                    .as("A blank file must not list its missing root element, as the blank state explains it.")
                    .isFalse();
        }
    }
}
