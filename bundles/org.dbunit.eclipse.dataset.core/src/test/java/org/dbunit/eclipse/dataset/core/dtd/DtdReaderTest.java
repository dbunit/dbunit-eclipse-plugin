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
package org.dbunit.eclipse.dataset.core.dtd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DtdReader} against both layouts dbUnit's {@code FlatDtdWriter} produces and the DTD
 * constructs {@link DtdDeclarations#tables()} and {@link DtdDeclarations#merge} must handle.
 */
class DtdReaderTest
{
    @Test
    void testRead_whenContentModelIsASequence_extractsNamesInOrder()
    {
        final DtdDeclarations declarations = DtdReader
                .read("<!ELEMENT dataset (USERS*, ORDERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ELEMENT ORDERS EMPTY>");

        assertThat(declarations.tables()).as("The sequence content model must list both tables in order.")
                .extracting(DtdTable::name).containsExactly("USERS", "ORDERS");
    }

    @Test
    void testRead_whenContentModelIsAChoice_extractsNamesInOrder()
    {
        final DtdDeclarations declarations = DtdReader.read(
                "<!ELEMENT dataset (USERS|ORDERS)*>\n<!ELEMENT USERS EMPTY>\n<!ELEMENT ORDERS EMPTY>");

        assertThat(declarations.tables()).as("The choice content model must list both tables in order.")
                .extracting(DtdTable::name).containsExactly("USERS", "ORDERS");
    }

    @Test
    void testRead_whenAttributesHaveEveryTypeAndDefaultVariant_extractsAllColumnNames()
    {
        final DtdDeclarations declarations = DtdReader.read("<!ELEMENT dataset (USERS*)>\n"
                + "<!ELEMENT USERS EMPTY>\n" + "<!ATTLIST USERS\n" + "    ID CDATA #REQUIRED\n"
                + "    NAME CDATA #IMPLIED\n" + "    STATUS (ACTIVE|INACTIVE) #FIXED \"x\"\n"
                + "    KIND NOTATION (a|b) #IMPLIED\n" + ">");

        final List<DtdTable> tables = declarations.tables();

        assertThat(tables).as("There must be exactly one table.").hasSize(1);
        assertThat(tables.get(0).columns()).as(
                "Every attribute must contribute its name regardless of its type or default.")
                .containsExactly("ID", "NAME", "STATUS", "KIND");
    }

    @Test
    void testRead_whenSeveralAttlistsDeclareOneElement_accumulatesColumnsInOrder()
    {
        final DtdDeclarations declarations =
                DtdReader.read("<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n<!ATTLIST USERS NAME CDATA #IMPLIED>");

        assertThat(declarations.tables().get(0).columns())
                .as("Columns from several ATTLISTs of the same element must accumulate in order.")
                .containsExactly("ID", "NAME");
    }

    @Test
    void testRead_whenAttlistHasNoElementDeclaration_stillDeclaresTheElement()
    {
        final DtdDeclarations declarations =
                DtdReader.read("<!ELEMENT dataset (USERS*)>\n<!ATTLIST USERS ID CDATA #REQUIRED>");

        assertThat(declarations.tables().get(0).columns())
                .as("An ATTLIST alone must declare the element.").containsExactly("ID");
    }

    @Test
    void testRead_whenElementHasNoAttlist_hasNoColumns()
    {
        final DtdDeclarations declarations =
                DtdReader.read("<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>");

        assertThat(declarations.tables().get(0).columns())
                .as("An ELEMENT without an ATTLIST has no columns.").isEmpty();
    }

    @Test
    void testRead_whenACommentContainsAGreaterThanCharacter_isSkippedWhole()
    {
        final DtdDeclarations declarations = DtdReader
                .read("<!-- a > b -->\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>");

        assertThat(declarations.tables()).as("The comment must not break parsing of the declarations.")
                .extracting(DtdTable::name).containsExactly("USERS");
    }

    @Test
    void testRead_whenAParameterEntityIsDeclared_producesOneInfoAndIsIgnored()
    {
        final DtdDeclarations declarations =
                DtdReader.read("<!ENTITY % common \"ID CDATA #REQUIRED\">\n"
                        + "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>");

        assertThat(declarations.getProblems()).as("A parameter entity must produce exactly one info.")
                .extracting(DatasetProblem::code).containsExactly(ProblemCode.UNSUPPORTED_DTD_CONSTRUCT);
        assertThat(declarations.tables()).as("Parsing must continue after the ignored entity.")
                .extracting(DtdTable::name).containsExactly("USERS");
    }

    @Test
    void testRead_whenContentModelIsAny_makesEveryDeclaredElementATableInDeclarationOrder()
    {
        final DtdDeclarations declarations = DtdReader
                .read("<!ELEMENT dataset ANY>\n<!ELEMENT USERS EMPTY>\n<!ELEMENT ORDERS EMPTY>");

        assertThat(declarations.tables()).as("ANY must make every declared element a table, in order.")
                .extracting(DtdTable::name).containsExactly("USERS", "ORDERS");
    }

    @Test
    void testRead_whenAnElementIsDeclaredOutsideTheContentModel_isNotATable()
    {
        final DtdDeclarations declarations = DtdReader.read(
                "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n<!ELEMENT AUDIT_LOG EMPTY>");

        assertThat(declarations.tables()).as(
                "Only the content model's names are tables; AUDIT_LOG is declared but not listed.")
                .extracting(DtdTable::name).containsExactly("USERS");
    }

    @Test
    void testRead_whenDatasetHasNoContentModel_hasNoTables()
    {
        final DtdDeclarations declarations = DtdReader.read("<!ELEMENT USERS EMPTY>");

        assertThat(declarations.tables()).as("Without a dataset declaration, there are no tables.")
                .isEmpty();
    }

    @Test
    void testRead_whenAContentModelNameHasNoDeclaration_isATableWithoutColumnsAndIsMissing()
    {
        final DtdDeclarations declarations =
                DtdReader.read("<!ELEMENT dataset (USERS*, ORDERS*)>\n<!ELEMENT USERS EMPTY>");

        final List<DtdTable> tables = declarations.tables();

        assertThat(tables).as("ORDERS must still be a table.").extracting(DtdTable::name)
                .containsExactly("USERS", "ORDERS");
        assertThat(tables.get(1).columns()).as("ORDERS has no declaration, so it has no columns.")
                .isEmpty();
        assertThat(declarations.missingDeclarations())
                .as("ORDERS must be reported as a missing declaration.").containsExactly("ORDERS");
    }

    @Test
    void testMerge_whenBothHaveAContentModel_keepsTheFirstAndAppendsLaterColumns()
    {
        final DtdDeclarations internalSubset = DtdReader
                .read("<!ELEMENT dataset (USERS*)>\n<!ATTLIST USERS ID CDATA #REQUIRED>");
        final DtdDeclarations externalDtd =
                DtdReader.read("<!ELEMENT dataset ANY>\n<!ATTLIST USERS NAME CDATA #IMPLIED>");

        final DtdDeclarations merged = internalSubset.merge(externalDtd);

        assertThat(merged.tables()).as("The first (internal subset) content model must win.")
                .extracting(DtdTable::name).containsExactly("USERS");
        assertThat(merged.tables().get(0).columns())
                .as("Columns must merge by element name, the internal subset's first, then the "
                        + "external DTD's, without duplicates.")
                .containsExactly("ID", "NAME");
    }

    @Test
    void testMerge_whenAnElementIsDeclaredInBoth_doesNotDuplicateAColumn()
    {
        final DtdDeclarations internalSubset =
                DtdReader.read("<!ELEMENT dataset (USERS*)>\n<!ATTLIST USERS ID CDATA #REQUIRED>");
        final DtdDeclarations externalDtd = DtdReader
                .read("<!ATTLIST USERS ID CDATA #REQUIRED>\n<!ATTLIST USERS NAME CDATA #IMPLIED>");

        final DtdDeclarations merged = internalSubset.merge(externalDtd);

        assertThat(merged.tables().get(0).columns()).as("A column declared in both must appear once.")
                .containsExactly("ID", "NAME");
    }
}
