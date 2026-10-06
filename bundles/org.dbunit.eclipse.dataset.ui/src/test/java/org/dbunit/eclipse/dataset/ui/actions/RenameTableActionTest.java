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
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link RenameTableAction} against the Commands specification: the table gets the new name and the
 * internal subset follows it, a rejected edit changes nothing, the dialog refuses a name that another
 * table has, and the dialog tells when a DTD declares the table.
 */
class RenameTableActionTest extends GridActionFixture
{
    @Test
    void testRenameTableNameDialogMessage_whenTheTableIsNotDeclaredOnly_isJustThePrompt()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(RenameTableAction.nameDialogMessage(table))
                .as("A table that is not declared-only must get a plain prompt.")
                .isEqualTo("Table name:");
    }

    @Test
    void testRenameTableNameDialogMessage_whenTheTableIsDeclaredOnly_warnsThatDbUnitReadsTheDtd()
    {
        final FlatXmlDatasetDocument datasetDocument = create(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n</dataset>\n");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(RenameTableAction.nameDialogMessage(table))
                .as("A declared-only table must warn that dbUnit reads tables from the DTD.")
                .contains("dbUnit reads a flat XML dataset's tables from its DTD");
    }

    @Test
    void testRenameTableNameDialogMessage_whenADtdFileDeclaresTheTable_warnsToRenameItThereToo()
    {
        final FlatXmlDatasetDocument datasetDocument = createWithDtdFile(
                "<!DOCTYPE dataset SYSTEM \"my.dtd\"><dataset><USERS ID=\"1\"/></dataset>",
                "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY><!ATTLIST USERS ID CDATA #REQUIRED>");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(RenameTableAction.nameDialogMessage(table))
                .as("The editor does not change a DTD file, so the dialog must say that the file needs the "
                        + "new name too.")
                .contains("a DTD file declares this table", "rename the table there too");
    }

    @Test
    void testRenameTableNameDialogMessage_whenOnlyTheInternalSubsetDeclaresTheTable_isJustThePrompt()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<!DOCTYPE dataset [\n"
                + "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset><USERS ID=\"1\"/></dataset>\n");
        final DatasetTable table = datasetDocument.getModel().findTable("USERS").orElseThrow();

        assertThat(RenameTableAction.nameDialogMessage(table))
                .as("The editor renames a table in the internal subset itself, so there is nothing to warn "
                        + "about.")
                .isEqualTo("Table name:");
    }

    @Test
    void testRenameTable_whenTheInternalSubsetDeclaresTheTable_theDtdStillDeclaresTheRenamedTable()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<!DOCTYPE dataset [\n"
                + "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset>\n    <USERS ID=\"1\"/>\n</dataset>\n");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final RenameTableAction action = new RenameTableAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetTable table)
            {
                return "Customers";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().getTables())
                .as("The renamed table must replace the old one, with no table left that only the DTD "
                        + "declares.")
                .extracting(DatasetTable::getName).containsExactly("Customers");
        assertThat(datasetDocument.getModel().getProblems())
                .as("The DTD must declare the renamed table, so dbUnit can load the dataset.").isEmpty();
    }

    @Test
    void testRenameTable_whenTheDialogReturnsANewName_renamesTheTable()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        final RenameTableAction action = new RenameTableAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetTable table)
            {
                return "Customers";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("CUSTOMERS")).as("Rename Table must apply the entered name.")
                .isPresent();
    }

    @Test
    void testRenameTable_whenTheEditIsRejected_leavesTheTableAsItWas()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.editable = false;
        final RenameTableAction action = new RenameTableAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetTable table)
            {
                return "Customers";
            }
        };

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS"))
                .as("A rejected edit must not rename the table.").isPresent();
    }

    @Test
    void testRenameTable_whenCaseSensitiveAndAnotherTableHasTheTargetNameExactly_theDialogValidatorRejectsIt()
    {
        final FlatXmlDatasetDocument datasetDocument =
                createCaseSensitive("<dataset><users ID=\"1\"/><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "users");
        final List<IInputValidator> capturedValidator = new ArrayList<>();
        final RenameTableAction action = new RenameTableAction(context)
        {
            @Override
            String openNameDialog(final Shell shell, final String currentName,
                    final IInputValidator validator, final DatasetTable table)
            {
                capturedValidator.add(validator);
                return null;
            }
        };

        action.run();

        assertThat(capturedValidator.get(0).isValid("USERS"))
                .as("Renaming 'users' to 'USERS' must be rejected because a separate table already has "
                        + "that exact name, even though the validator excludes the table being renamed.")
                .isNotNull();
    }
}
