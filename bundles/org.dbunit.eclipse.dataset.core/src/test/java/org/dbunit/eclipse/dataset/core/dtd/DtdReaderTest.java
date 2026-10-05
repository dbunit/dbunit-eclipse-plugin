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
import java.util.Map;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.osgi.util.NLS;
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
    void testRead_whenContentModelRepeatsAName_listsItOnce()
    {
        final DtdDeclarations declarations = DtdReader
                .read("<!ELEMENT dataset (A*, B*, A*)>\n<!ELEMENT A EMPTY>\n<!ELEMENT B EMPTY>");

        assertThat(declarations.tables()).as("A repeated content model name must be listed only once.")
                .extracting(DtdTable::name).containsExactly("A", "B");
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
    void testRead_whenAParameterEntityValueHoldsAGreaterThan_infoCoversTheWholeDeclaration()
    {
        final String declaration = "<!ENTITY % gt \"a > b\">";

        final DtdDeclarations declarations =
                DtdReader.read(declaration + "\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>");

        assertThat(declarations.getProblems())
                .as("The info must span the declaration up to its real closing bracket.")
                .containsExactly(new DatasetProblem(ProblemCode.UNSUPPORTED_DTD_CONSTRUCT,
                        ProblemSeverity.INFO, Messages.Dtd_parameterEntityDeclarationsIgnored, null, null, -1,
                        0, declaration.length()));
    }

    @Test
    void testRead_whenAConditionalSectionIsDeclared_infoCoversTheSectionAndItsContentIsIgnored()
    {
        final String prefix = "<!ELEMENT dataset (USERS*)>\n";
        final String section = "<![IGNORE[ <!ELEMENT SKIPPED EMPTY> ]]>";

        final DtdDeclarations declarations = DtdReader.read(prefix + section + "\n<!ELEMENT USERS EMPTY>");

        assertThat(declarations.getProblems()).as("The info must span exactly the conditional section.")
                .containsExactly(new DatasetProblem(ProblemCode.UNSUPPORTED_DTD_CONSTRUCT,
                        ProblemSeverity.INFO, Messages.Dtd_conditionalSectionsIgnored, null, null, -1,
                        prefix.length(), section.length()));
        assertThat(declarations.missingDeclarations()).as("Nothing inside the section may be declared.")
                .isEmpty();
    }

    @Test
    void testRead_whenAParameterEntityIsReferenced_infoCoversTheReference()
    {
        final String prefix = "<!ELEMENT dataset (USERS*)>\n";

        final DtdDeclarations declarations =
                DtdReader.read(prefix + "%common;\n<!ELEMENT USERS EMPTY>");

        assertThat(declarations.getProblems())
                .as("The info must span the percent sign through the semicolon.")
                .containsExactly(new DatasetProblem(ProblemCode.UNSUPPORTED_DTD_CONSTRUCT,
                        ProblemSeverity.INFO, Messages.Dtd_parameterEntityReferencesIgnored, null, null, -1,
                        prefix.length(), "%common;".length()));
    }

    @Test
    void testRead_whenAParameterEntityReferenceHasNoSemicolon_infoCoversTheNameOnly()
    {
        final String prefix = "<!ELEMENT dataset (USERS*)>\n";

        final DtdDeclarations declarations =
                DtdReader.read(prefix + "%common\n<!ELEMENT USERS EMPTY>");

        assertThat(declarations.getProblems())
                .as("The info must end after the name when no semicolon follows.")
                .containsExactly(new DatasetProblem(ProblemCode.UNSUPPORTED_DTD_CONSTRUCT,
                        ProblemSeverity.INFO, Messages.Dtd_parameterEntityReferencesIgnored, null, null, -1,
                        prefix.length(), "%common".length()));
    }

    @Test
    void testRead_whenANotationIsDeclared_isIgnoredWithoutAProblem()
    {
        final DtdDeclarations declarations = DtdReader.read("<!NOTATION gif SYSTEM \"image/gif\">\n"
                + "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>");

        assertThat(declarations.getProblems()).as("A notation declaration is skipped silently.").isEmpty();
        assertThat(declarations.tables()).as("Parsing must continue after the notation.")
                .extracting(DtdTable::name).containsExactly("USERS");
    }

    @Test
    void testRead_whenAProcessingInstructionPrecedesTheDeclarations_isSkipped()
    {
        final DtdDeclarations declarations = DtdReader.read("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>");

        assertThat(declarations.getProblems()).as("A processing instruction is skipped silently.").isEmpty();
        assertThat(declarations.tables()).as("Parsing must continue after the processing instruction.")
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
    void testRead_whenContentModelIsAnyAndDatasetHasAnAttlist_excludesDatasetFromTables()
    {
        final DtdDeclarations declarations = DtdReader.read(
                "<!ELEMENT dataset ANY>\n<!ATTLIST dataset xmlns CDATA #IMPLIED>\n<!ELEMENT USERS EMPTY>");

        assertThat(declarations.tables()).as("The dataset root element must never be listed as a table.")
                .extracting(DtdTable::name).containsExactly("USERS");
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

    @Test
    void testRead_whenAttributesHaveDefaultAndFixedValues_recordsThemOnTheirColumns()
    {
        final DtdDeclarations declarations = DtdReader.read("<!ELEMENT dataset (USERS*)>\n"
                + "<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS\n    ID CDATA #REQUIRED\n    NAME CDATA #IMPLIED\n"
                + "    STATUS CDATA \"ACTIVE\"\n    KIND (A|B) #FIXED 'A'\n>");

        assertThat(declarations.tables())
                .as("Only the columns declared with a default or a fixed value may have a default.")
                .containsExactly(new DtdTable("USERS", List.of("ID", "NAME", "STATUS", "KIND"),
                        Map.of("STATUS", "ACTIVE", "KIND", "A")));
    }

    @Test
    void testRead_whenADefaultIsTheEmptyString_recordsTheEmptyString()
    {
        final DtdDeclarations declarations = DtdReader.read(
                "<!ELEMENT dataset (USERS*)>\n<!ATTLIST USERS ID CDATA #REQUIRED STATUS CDATA \"\">");

        assertThat(declarations.tables())
                .as("An empty default is a value, not an absent one: dbUnit loads an empty string.")
                .containsExactly(new DtdTable("USERS", List.of("ID", "STATUS"), Map.of("STATUS", "")));
    }

    @Test
    void testRead_whenADefaultHasReferencesAndWhitespace_decodesItLikeAnAttributeValue()
    {
        final DtdDeclarations declarations = DtdReader.read("<!ELEMENT dataset (USERS*)>\n"
                + "<!ATTLIST USERS NOTE CDATA \"a&#38;b&lt;c  d&#10;e\tf\">");

        assertThat(declarations.tables()).as("Character references and the predefined entities must "
                + "decode, a line feed written as a reference must stay one, and a literal tab must "
                + "become a space.")
                .containsExactly(new DtdTable("USERS", List.of("NOTE"), Map.of("NOTE", "a&b<c  d\ne f")));
    }

    @Test
    void testRead_whenADefaultIsSingleQuoted_mayContainADoubleQuote()
    {
        final DtdDeclarations declarations =
                DtdReader.read("<!ELEMENT dataset (USERS*)>\n<!ATTLIST USERS NOTE CDATA 'it\"s'>");

        assertThat(declarations.tables()).as("A single-quoted default may hold a double quote.")
                .containsExactly(new DtdTable("USERS", List.of("NOTE"), Map.of("NOTE", "it\"s")));
    }

    @Test
    void testRead_whenADefaultUsesAnEntityReference_hasNoDefaultAndReportsAnInfoAtTheValue()
    {
        final String prefix = "<!ELEMENT dataset (USERS*)>\n<!ATTLIST USERS NOTE CDATA ";
        final String literal = "\"&active;\"";

        final DtdDeclarations declarations = DtdReader.read(prefix + literal + ">");

        assertThat(declarations.tables()).as("A default the editor cannot decode must not be shown.")
                .containsExactly(new DtdTable("USERS", List.of("NOTE")));
        final String reason = NLS.bind(Messages.Codec_unsupportedEntity, "active");
        assertThat(declarations.getProblems()).as("The info must cover the default value's literal.")
                .containsExactly(new DatasetProblem(ProblemCode.UNSUPPORTED_DTD_CONSTRUCT,
                        ProblemSeverity.INFO, NLS.bind(Messages.Dtd_attributeDefaultNotShown,
                                new Object[] { "NOTE", "USERS", reason }),
                        null, null, -1, prefix.length(), literal.length()));
    }

    @Test
    void testRead_whenAnAttributeIsDeclaredTwiceInOneAttlist_theFirstDeclarationWins()
    {
        final DtdDeclarations declarations = DtdReader.read("<!ELEMENT dataset (USERS*)>\n"
                + "<!ATTLIST USERS STATUS CDATA \"FIRST\" STATUS CDATA \"SECOND\">");

        assertThat(declarations.tables()).as("The first declaration of an attribute is binding in XML.")
                .containsExactly(new DtdTable("USERS", List.of("STATUS"), Map.of("STATUS", "FIRST")));
    }

    @Test
    void testRead_whenALaterAttlistGivesADefaultToAnAttributeDeclaredWithoutOne_theFirstDeclarationWins()
    {
        final DtdDeclarations declarations = DtdReader.read("<!ELEMENT dataset (USERS*)>\n"
                + "<!ATTLIST USERS STATUS CDATA #IMPLIED>\n<!ATTLIST USERS STATUS CDATA \"LATE\">");

        assertThat(declarations.tables())
                .as("The earlier declaration without a default is binding, so the column has none.")
                .containsExactly(new DtdTable("USERS", List.of("STATUS")));
    }

    @Test
    void testMerge_whenBothDeclareTheSameColumnWithDefaults_keepsTheInternalSubsetsDefault()
    {
        final DtdDeclarations internalSubset = DtdReader
                .read("<!ELEMENT dataset (USERS*)>\n<!ATTLIST USERS STATUS CDATA \"INTERNAL\">");
        final DtdDeclarations externalDtd =
                DtdReader.read("<!ATTLIST USERS STATUS CDATA \"EXTERNAL\" ID CDATA \"0\">");

        final DtdDeclarations merged = internalSubset.merge(externalDtd);

        assertThat(merged.tables()).as("The internal subset is read first, so its declaration is binding, "
                + "and the external DTD's other columns keep their defaults.")
                .containsExactly(new DtdTable("USERS", List.of("STATUS", "ID"),
                        Map.of("STATUS", "INTERNAL", "ID", "0")));
    }

    @Test
    void testMerge_whenTheInternalSubsetDeclaresAColumnWithoutADefault_dropsTheExternalDefault()
    {
        final DtdDeclarations internalSubset = DtdReader
                .read("<!ELEMENT dataset (USERS*)>\n<!ATTLIST USERS STATUS CDATA #IMPLIED>");
        final DtdDeclarations externalDtd = DtdReader.read("<!ATTLIST USERS STATUS CDATA \"EXTERNAL\">");

        final DtdDeclarations merged = internalSubset.merge(externalDtd);

        assertThat(merged.tables())
                .as("The internal subset's declaration without a default is binding.")
                .containsExactly(new DtdTable("USERS", List.of("STATUS")));
    }

    @Test
    void testWithProblemsShiftedBy_whenColumnsHaveDefaults_keepsTheDefaults()
    {
        final DtdDeclarations declarations = DtdReader
                .read("<!ELEMENT dataset (USERS*)>\n<!ATTLIST USERS STATUS CDATA \"ACTIVE\">");

        assertThat(declarations.withProblemsShiftedBy(10).tables())
                .as("Relocating the problems must not lose the defaults.")
                .isEqualTo(declarations.tables());
    }

    @Test
    void testWithProblemsAt_whenColumnsHaveDefaults_keepsTheDefaults()
    {
        final DtdDeclarations declarations = DtdReader
                .read("<!ELEMENT dataset (USERS*)>\n<!ATTLIST USERS STATUS CDATA \"ACTIVE\">");

        assertThat(declarations.withProblemsAt(3, 4).tables())
                .as("Relocating the problems must not lose the defaults.")
                .isEqualTo(declarations.tables());
    }

    @Test
    void testLocateElementNames_whenTheDtdDeclaresTables_findsEachNameInTheOrderTheTextWritesIt()
    {
        final String dtd = "<!ELEMENT dataset (USERS*, ORDERS*)>\n<!ELEMENT USERS EMPTY>\n"
                + "<!ATTLIST USERS ID CDATA #REQUIRED>\n<!ELEMENT ORDERS EMPTY>";

        final List<DtdElementName> names = DtdReader.locateElementNames(dtd);

        assertThat(names).as("The content model lists the tables first, then each declaration names its "
                + "element.")
                .containsExactly(new DtdElementName("USERS", dtd.indexOf("USERS*")),
                        new DtdElementName("ORDERS", dtd.indexOf("ORDERS*")),
                        new DtdElementName("USERS", dtd.indexOf("USERS EMPTY")),
                        new DtdElementName("USERS", dtd.indexOf("USERS ID")),
                        new DtdElementName("ORDERS", dtd.indexOf("ORDERS EMPTY")));
    }

    @Test
    void testLocateElementNames_whenSeveralAttlistsNameTheElement_findsEachOne()
    {
        final String dtd = "<!ATTLIST USERS ID CDATA #REQUIRED>\n<!ATTLIST USERS NAME CDATA #IMPLIED>";

        assertThat(DtdReader.locateElementNames(dtd))
                .as("Every ATTLIST declaration of the element writes its name.")
                .containsExactly(new DtdElementName("USERS", dtd.indexOf("USERS ID")),
                        new DtdElementName("USERS", dtd.indexOf("USERS NAME")));
    }

    @Test
    void testLocateElementNames_whenTheContentModelIsNestedOrAChoice_findsEachNameWhereItIsWritten()
    {
        final String dtd = "<!ELEMENT dataset ((A|B)+, (C?, A))>";

        assertThat(DtdReader.locateElementNames(dtd))
                .as("A name that the model writes twice is found twice, and no structural character is "
                        + "part of a name.")
                .containsExactly(new DtdElementName("A", dtd.indexOf("A|")),
                        new DtdElementName("B", dtd.indexOf("B)")),
                        new DtdElementName("C", dtd.indexOf("C?")),
                        new DtdElementName("A", dtd.indexOf("A)")));
    }

    @Test
    void testLocateElementNames_whenTheContentModelHasPcdataAndNamesWithACommonPrefix_findsOnlyNames()
    {
        final String dtd = "<!ELEMENT dataset (#PCDATA | USERS | USERS_AUDIT)*>";

        assertThat(DtdReader.locateElementNames(dtd))
                .as("#PCDATA is no element name, and a name is found whole, not as the prefix of another.")
                .containsExactly(new DtdElementName("USERS", dtd.indexOf("USERS |")),
                        new DtdElementName("USERS_AUDIT", dtd.indexOf("USERS_AUDIT")));
    }

    @Test
    void testLocateElementNames_whenOnlyTheDatasetElementIsDeclared_findsNoName()
    {
        final String dtd = "<!ELEMENT dataset ANY>\n<!ATTLIST dataset ID CDATA #IMPLIED>";

        assertThat(DtdReader.locateElementNames(dtd))
                .as("The dataset element is never a table, so its own name is not one to rename.")
                .isEmpty();
    }

    @Test
    void testLocateElementNames_whenANameIsInACommentAnEntityOrADefaultValue_isNotFound()
    {
        final String dtd = "<!-- <!ELEMENT USERS EMPTY> -->\n<!ENTITY % tables \"USERS\">\n"
                + "<!ELEMENT ORDERS EMPTY>\n<!ATTLIST ORDERS KIND CDATA \"USERS\">";

        assertThat(DtdReader.locateElementNames(dtd))
                .as("Only the names that declarations give their elements are found, not text that "
                        + "happens to spell one.")
                .containsExactly(new DtdElementName("ORDERS", dtd.indexOf("ORDERS EMPTY")),
                        new DtdElementName("ORDERS", dtd.indexOf("ORDERS KIND")));
    }
}
