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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link RenameColumnAction} against the Commands specification: the anchor column gets the new
 * name, a name that another column has in another letter case counts as used, and the dialog tells when
 * the DTD declares the column.
 */
class RenameColumnActionTest extends GridActionFixture
{
    @Test
    void testRenameColumnNameDialogMessage_whenTheColumnIsNotDeclared_isJustThePrompt()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetColumn column =
                datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns().get(0);

        assertThat(RenameColumnAction.nameDialogMessage(column))
                .as("A column that is not DTD-declared must get a plain prompt.")
                .isEqualTo("Column name:");
    }

    @Test
    void testRenameColumnNameDialogMessage_whenTheColumnIsDeclared_warnsThatDbUnitReadsTheDtd()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n    <USERS ID=\"1\"/>\n"
                        + "</dataset>\n");
        final DatasetColumn column =
                datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns().get(0);

        assertThat(RenameColumnAction.nameDialogMessage(column))
                .as("A DTD-declared column must warn that dbUnit reads columns from the DTD.")
                .contains("dbUnit reads a flat XML dataset's columns from its DTD");
    }

    @Test
    void testRenameColumn_whenTheDialogReturnsANewName_renamesTheAnchorColumn()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        final RenameColumnAction action = new RenameColumnAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetColumn column)
            {
                return "USER_ID";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getColumns().get(0).name())
                .as("Rename Column must apply the entered name.").isEqualTo("USER_ID");
    }

    @Test
    void testRenameColumn_whenAnotherColumnIsEqualIgnoringCaseYetDistinct_countsItsNameAsUsed()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" \u0130D=\"2\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 1;
        final List<IInputValidator> validators = new ArrayList<>();
        final RenameColumnAction action = new RenameColumnAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetColumn column)
            {
                validators.add(validator);
                return null;
            }
        };

        action.run();

        assertThat(validators).as("Rename Column must open its dialog.").hasSize(1);
        assertThat(validators.get(0).isValid("ID"))
                .as("The name of the other column must count as used, although it equals the renamed "
                        + "column's name ignoring case.")
                .isNotNull();
    }
}
