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
package org.dbunit.eclipse.dataset.core.flatxml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.TestDatasets;
import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdReader;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlValidator} against every code and location of the Validator section of the flat
 * XML specification.
 */
class FlatXmlValidatorTest
{
    @Test
    void testValidate_whenALaterRowHasAColumnMissingFromTheFirstElement_reportsColumnNotInFirstRow()
    {
        final List<DatasetProblem> problems = validate(
                "<dataset><USERS ID=\"1\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, false);

        assertThat(onlyCode(problems, ProblemCode.COLUMN_NOT_IN_FIRST_ROW)).as(
                "A column missing from the first element but present later must be reported once.")
                .hasSize(1);
    }

    @Test
    void testValidate_whenEveryRowHasTheFirstElementsColumns_doesNotReportColumnNotInFirstRow()
    {
        final List<DatasetProblem> problems = validate(
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, false);

        assertThat(onlyCode(problems, ProblemCode.COLUMN_NOT_IN_FIRST_ROW))
                .as("No column is missing from the first element.").isEmpty();
    }

    @Test
    void testValidate_whenColumnSensingIsEnabled_suppressesColumnNotInFirstRow()
    {
        final List<DatasetProblem> problems =
                validate("<dataset><USERS ID=\"1\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>",
                        new FlatXmlOptions(false, true), false);

        assertThat(onlyCode(problems, ProblemCode.COLUMN_NOT_IN_FIRST_ROW))
                .as("Column sensing must suppress COLUMN_NOT_IN_FIRST_ROW.").isEmpty();
    }

    @Test
    void testValidate_whenADoctypeIsPresent_suppressesColumnNotInFirstRowAndFirstElementWithoutAttributes()
    {
        final List<DatasetProblem> problems = validate(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #IMPLIED>\n]>\n"
                        + "<dataset><USERS ID=\"1\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, false);

        assertThat(onlyCode(problems, ProblemCode.COLUMN_NOT_IN_FIRST_ROW))
                .as("A DOCTYPE must suppress COLUMN_NOT_IN_FIRST_ROW.").isEmpty();
        assertThat(onlyCode(problems, ProblemCode.FIRST_ELEMENT_WITHOUT_ATTRIBUTES))
                .as("A DOCTYPE must suppress FIRST_ELEMENT_WITHOUT_ATTRIBUTES.").isEmpty();
    }

    @Test
    void testValidate_whenDtdIsNotLoaded_suppressesColumnNotInFirstRowAndFirstElementWithoutAttributes()
    {
        final List<DatasetProblem> problems = validate(
                "<!DOCTYPE dataset SYSTEM \"missing.dtd\">\n<dataset><USERS/><USERS ID=\"1\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, true);

        assertThat(onlyCode(problems, ProblemCode.FIRST_ELEMENT_WITHOUT_ATTRIBUTES))
                .as("DTD_NOT_LOADED must suppress FIRST_ELEMENT_WITHOUT_ATTRIBUTES.").isEmpty();
        assertThat(onlyCode(problems, ProblemCode.COLUMN_NOT_IN_FIRST_ROW))
                .as("DTD_NOT_LOADED must suppress COLUMN_NOT_IN_FIRST_ROW.").isEmpty();
        assertThat(onlyCode(problems, ProblemCode.DTD_NOT_LOADED))
                .as("DTD_NOT_LOADED itself must still be reported.").hasSize(1);
    }

    @Test
    void testValidate_whenFirstElementHasNoAttributesButTableHasRows_reportsFirstElementWithoutAttributesAndSuppressesColumnNotInFirstRow()
    {
        final List<DatasetProblem> problems = validate(
                "<dataset><USERS/><USERS ID=\"1\" NAME=\"Alice\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, false);

        assertThat(onlyCode(problems, ProblemCode.FIRST_ELEMENT_WITHOUT_ATTRIBUTES))
                .as("The empty first element must be reported.").hasSize(1);
        assertThat(onlyCode(problems, ProblemCode.COLUMN_NOT_IN_FIRST_ROW)).as(
                "FIRST_ELEMENT_WITHOUT_ATTRIBUTES must suppress COLUMN_NOT_IN_FIRST_ROW for its table.")
                .isEmpty();
    }

    @Test
    void testValidate_whenATableIsSpelledTwoWays_reportsTableNameCaseVariantsAtTheSecondSpelling()
    {
        final String text = "<dataset><USERS ID=\"1\"/><users ID=\"2\"/></dataset>";

        final List<DatasetProblem> problems = validate(text, FlatXmlOptions.DBUNIT_DEFAULTS, false);

        final DatasetProblem problem = onlyCode(problems, ProblemCode.TABLE_NAME_CASE_VARIANTS).get(0);
        assertThat(text.substring(problem.offset(), problem.offset() + problem.length()))
                .as("The location must be the element with the second spelling.").isEqualTo("<users");
    }

    @Test
    void testValidate_whenCaseSensitiveTableNames_doesNotReportTableNameCaseVariants()
    {
        final List<DatasetProblem> problems = validate(
                "<dataset><USERS ID=\"1\"/><users ID=\"2\"/></dataset>", new FlatXmlOptions(true, false),
                false);

        assertThat(onlyCode(problems, ProblemCode.TABLE_NAME_CASE_VARIANTS))
                .as("Case-sensitive table names make USERS and users different tables, not variants.")
                .isEmpty();
    }

    @Test
    void testValidate_whenAColumnIsSpelledTwoWays_reportsColumnNameCaseVariantsAtTheSecondSpelling()
    {
        final String text = "<dataset><USERS name=\"Alice\"/><USERS NAME=\"Bob\"/></dataset>";

        final List<DatasetProblem> problems = validate(text, FlatXmlOptions.DBUNIT_DEFAULTS, false);

        final DatasetProblem problem = onlyCode(problems, ProblemCode.COLUMN_NAME_CASE_VARIANTS).get(0);
        assertThat(text.substring(problem.offset(), problem.offset() + problem.length()))
                .as("The location must be the attribute with the second spelling.").isEqualTo("NAME");
    }

    @Test
    void testValidate_whenAnElementHasTwoAttributesDifferingOnlyInCase_reportsDuplicateColumnInRowAtTheSecondAttribute()
    {
        final String text = "<dataset><USERS id=\"1\" ID=\"2\"/></dataset>";

        final List<DatasetProblem> problems = validate(text, FlatXmlOptions.DBUNIT_DEFAULTS, false);

        final DatasetProblem problem = onlyCode(problems, ProblemCode.DUPLICATE_COLUMN_IN_ROW).get(0);
        assertThat(text.substring(problem.offset(), problem.offset() + problem.length()))
                .as("The location must be the second attribute.").isEqualTo("ID");
    }

    @Test
    void testValidate_whenAnElementIsDeclaredOutsideTheContentModel_reportsTableNotDeclaredInDtd()
    {
        final List<DatasetProblem> problems = validate(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n<!ELEMENT AUDIT_LOG EMPTY>\n]>\n"
                        + "<dataset><USERS ID=\"1\"/><AUDIT_LOG ACTION=\"x\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, false);

        assertThat(onlyCode(problems, ProblemCode.TABLE_NOT_DECLARED_IN_DTD)).as(
                "AUDIT_LOG has rows but is declared outside the content model, so it is not a DTD table.")
                .hasSize(1);
    }

    @Test
    void testValidate_whenColumnIsNotDeclaredInDtd_isWarningWithoutColumnSensingAndErrorWithIt()
    {
        final String text = TestDatasets.read("internal-subset.xml");

        final List<DatasetProblem> warning = validate(text, FlatXmlOptions.DBUNIT_DEFAULTS, false);
        final List<DatasetProblem> error = validate(text, new FlatXmlOptions(false, true), false);

        assertThat(onlyCode(warning, ProblemCode.COLUMN_NOT_DECLARED_IN_DTD).get(0).severity())
                .as("Without column sensing, dbUnit merely ignores the undeclared column's values.")
                .isEqualTo(ProblemSeverity.WARNING);
        assertThat(onlyCode(error, ProblemCode.COLUMN_NOT_DECLARED_IN_DTD).get(0).severity())
                .as("With column sensing, dbUnit fails to load the dataset.")
                .isEqualTo(ProblemSeverity.ERROR);
    }

    @Test
    void testValidate_whenTheContentModelNamesAnUndeclaredElement_reportsDtdTableWithoutDeclaration()
    {
        final List<DatasetProblem> problems = validate(
                "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*, ORDERS*)>\n<!ELEMENT USERS EMPTY>\n"
                        + "<!ATTLIST USERS ID CDATA #REQUIRED>\n]>\n<dataset><USERS ID=\"1\"/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, false);

        assertThat(onlyCode(problems, ProblemCode.DTD_TABLE_WITHOUT_DECLARATION))
                .as("ORDERS is named in the content model but has neither ELEMENT nor ATTLIST.")
                .hasSize(1);
    }

    @Test
    void testValidate_whenATableHasARedundantMarker_reportsRedundantEmptyElement()
    {
        final List<DatasetProblem> problems = validate("<dataset><USERS ID=\"1\"/><USERS/></dataset>",
                FlatXmlOptions.DBUNIT_DEFAULTS, false);

        assertThat(onlyCode(problems, ProblemCode.REDUNDANT_EMPTY_ELEMENT))
                .as("A marker after a table already has rows is redundant.").hasSize(1);
    }

    @Test
    void testValidate_whenAMarkerIsTheTablesOnlyElement_doesNotReportRedundantEmptyElement()
    {
        final List<DatasetProblem> problems =
                validate("<dataset><USERS/></dataset>", FlatXmlOptions.DBUNIT_DEFAULTS, false);

        assertThat(onlyCode(problems, ProblemCode.REDUNDANT_EMPTY_ELEMENT))
                .as("The table's only (first) element is not redundant.").isEmpty();
    }

    @Test
    void testValidate_whenTheParserFindsIgnoredText_passesTheProblemThrough()
    {
        final List<DatasetProblem> problems =
                validate("<dataset>stray<USERS ID=\"1\"/></dataset>", FlatXmlOptions.DBUNIT_DEFAULTS,
                        false);

        assertThat(onlyCode(problems, ProblemCode.TEXT_CONTENT_IGNORED))
                .as("Parser problems must be passed through in the validator's returned list.")
                .hasSize(1);
    }

    private static List<DatasetProblem> onlyCode(final List<DatasetProblem> problems,
            final ProblemCode code)
    {
        final List<DatasetProblem> result = new ArrayList<>();
        for (final DatasetProblem problem : problems)
        {
            if (problem.code() == code)
            {
                result.add(problem);
            }
        }
        return result;
    }

    private static List<DatasetProblem> validate(final String text, final FlatXmlOptions options,
            final boolean dtdNotLoaded)
    {
        final FlatXmlParseResult parse = FlatXmlParser.parse(text);
        final DtdState dtdState;
        final DtdDeclarations dtd;
        if (parse.doctype() == null)
        {
            dtdState = DtdState.NONE;
            dtd = null;
        }
        else
        {
            dtdState = dtdNotLoaded ? DtdState.NOT_LOADED : DtdState.LOADED;
            final String internalSubset = parse.doctype().internalSubset();
            dtd = DtdReader.read(internalSubset == null ? "" : internalSubset);
        }
        final FlatXmlModelBuilder.Result built =
                FlatXmlModelBuilder.build(text, parse, dtd, options, Map.of());
        return FlatXmlValidator.validate(parse, built.index(), built.model().getTables(), dtdState, dtd,
                options);
    }
}
