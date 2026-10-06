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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.SAXParseException;

/**
 * Compares, for text content, comments, processing instructions, and CDATA sections, whether the parser
 * reports a dataset as not well-formed with whether real dbUnit 3.5.2 fails to load it, so that the editor
 * neither lets the user edit a file that dbUnit cannot load nor refuses one that dbUnit loads.
 */
class FlatXmlWellFormednessParityTest
{
    private static final String CONTROL_CHARACTER = String.valueOf((char) 0x1);

    private static final String NONCHARACTER_FFFE = String.valueOf((char) 0xFFFE);

    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private static final String ARROW = String.valueOf((char) 0x2190);

    private static final String COMMA = String.valueOf((char) 0x3001);

    private static final String COMPATIBILITY_IDEOGRAPH = String.valueOf((char) 0xF900);

    private static final String COMBINING_GRAVE_ACCENT = String.valueOf((char) 0x300);

    private static final String COMBINING_LETTER_X = String.valueOf((char) 0x36F);

    private static final String THAI_LETTER = String.valueOf((char) 0xE01);

    private static final String IDEOGRAPH = String.valueOf((char) 0x4E00);

    private static final String MIDDLE_DOT = String.valueOf((char) 0xB7);

    private static final String XML_DECLARATION = "<?xml version=\"1.0\"?>\n";

    private static final String EXTERNAL_DOCTYPE =
            XML_DECLARATION + "<!DOCTYPE dataset SYSTEM \"plain.dtd\">\n";

    private static final String INTERNAL_SUBSET_START = XML_DECLARATION + "<!DOCTYPE dataset [\n"
            + "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS ANY>\n<!ATTLIST USERS ID CDATA #IMPLIED>\n";

    @TempDir
    private Path tempDir;

    @BeforeEach
    void writeTheDtdFiles() throws Exception
    {
        final String declarations =
                "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS ANY>\n<!ATTLIST USERS ID CDATA #IMPLIED>\n";
        Files.writeString(tempDir.resolve("plain.dtd"), declarations, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("entity.dtd"), declarations + "<!ENTITY nbsp \"&#160;\">\n",
                StandardCharsets.UTF_8);
    }

    private static String body(final String content)
    {
        return "<dataset>\n    " + content + "\n    <USERS ID=\"1\"/>\n</dataset>\n";
    }

    private static String rowOf(final String tableName, final String columnName)
    {
        return "<dataset>\n    <" + tableName + " " + columnName + "=\"1\"/>\n</dataset>\n";
    }

    private static String rowText(final String content)
    {
        return "<dataset>\n    <USERS ID=\"1\">" + content + "</USERS>\n</dataset>\n";
    }

    private static Stream<Arguments> malformedTexts()
    {
        return Stream.of(
                Arguments.of("a bare ampersand in body text", body("R&D fixtures")),
                Arguments.of("an ampersand and a space", body("R & D")),
                Arguments.of("an ampersand and a semicolon", body("&;")),
                Arguments.of("a reference without its semicolon", body("&amp more")),
                Arguments.of("a character reference without digits", body("&#;")),
                Arguments.of("a hexadecimal character reference without digits", body("&#x;")),
                Arguments.of("a character reference with a letter", body("&#12a;")),
                Arguments.of("a character reference with an uppercase X", body("&#X41;")),
                Arguments.of("a reference to a control character", body("&#1;")),
                Arguments.of("a reference to a surrogate", body("&#xD800;")),
                Arguments.of("a reference to U+FFFE", body("&#xFFFE;")),
                Arguments.of("a reference beyond U+10FFFF", body("&#x110000;")),
                Arguments.of("an undeclared entity in body text", body("&nbsp;")),
                Arguments.of("an undeclared entity in row text", rowText("&nbsp;")),
                Arguments.of("an undeclared entity next to an internal subset without entities",
                        INTERNAL_SUBSET_START + "]>\n" + body("&nbsp;")),
                Arguments.of("an undeclared entity in a DOCTYPE without a DTD",
                        XML_DECLARATION + "<!DOCTYPE dataset>\n" + body("&nbsp;")),
                Arguments.of("a CDATA end in body text", body("a ]]> b")),
                Arguments.of("a CDATA end in row text", rowText("a ]]> b")),
                Arguments.of("a CDATA end after a CDATA section", body("<![CDATA[x]]>]]>")),
                Arguments.of("a control character in body text", body("a" + CONTROL_CHARACTER + "b")),
                Arguments.of("a control character in row text", rowText("a" + CONTROL_CHARACTER + "b")),
                Arguments.of("U+FFFE in body text", body("a" + NONCHARACTER_FFFE + "b")),
                Arguments.of("a control character in a comment",
                        body("<!-- a" + CONTROL_CHARACTER + "b -->")),
                Arguments.of("U+FFFE in a comment", body("<!-- a" + NONCHARACTER_FFFE + "b -->")),
                Arguments.of("a control character in a processing instruction",
                        body("<?target a" + CONTROL_CHARACTER + "b?>")),
                Arguments.of("a control character in a CDATA section",
                        body("<![CDATA[a" + CONTROL_CHARACTER + "b]]>")),
                Arguments.of("a control character in a comment before the root",
                        "<!-- a" + CONTROL_CHARACTER + "b -->\n" + body("")),
                Arguments.of("a control character in a comment after the root",
                        body("") + "<!-- a" + CONTROL_CHARACTER + "b -->\n"),
                Arguments.of("a control character in a processing instruction before the root",
                        "<?target a" + CONTROL_CHARACTER + "b?>\n" + body("")),
                Arguments.of("a bare ampersand next to an external DTD", EXTERNAL_DOCTYPE + body("R&D")),
                Arguments.of("a CDATA end next to an external DTD", EXTERNAL_DOCTYPE + body("a ]]> b")),
                Arguments.of("a control character next to an external DTD",
                        EXTERNAL_DOCTYPE + body("a" + CONTROL_CHARACTER + "b")),
                Arguments.of("a reference to a control character next to an external DTD",
                        EXTERNAL_DOCTYPE + body("&#1;")),
                Arguments.of("a double hyphen in a comment", body("<!-- a -- b -->")),
                Arguments.of("a comment that ends with three hyphens", body("<!-- a --->")),
                Arguments.of("a double hyphen in a comment before the root",
                        "<!-- ---- USERS ---- -->\n" + body("")),
                Arguments.of("a double hyphen in a comment after the root",
                        body("") + "<!-- a -- b -->\n"),
                Arguments.of("a double hyphen in a comment of the internal subset",
                        INTERNAL_SUBSET_START + "<!-- a -- b -->\n]>\n" + body("")),
                Arguments.of("an XML declaration after white space", "\n" + XML_DECLARATION + body("")),
                Arguments.of("an XML declaration after a comment",
                        "<!-- c -->\n" + XML_DECLARATION + body("")),
                Arguments.of("a second XML declaration", XML_DECLARATION + XML_DECLARATION + body("")),
                Arguments.of("an XML declaration inside the root", body(XML_DECLARATION)),
                Arguments.of("an XML declaration after the root", body("") + XML_DECLARATION),
                Arguments.of("an XML declaration in the internal subset",
                        INTERNAL_SUBSET_START + XML_DECLARATION + "]>\n" + body("")),
                Arguments.of("a processing instruction target in capitals", body("<?XML data?>")),
                Arguments.of("a processing instruction target in mixed case", body("<?Xml data?>")),
                Arguments.of("an XML declaration in capitals", "<?XML version=\"1.0\"?>\n" + body("")),
                Arguments.of("a processing instruction without a target", body("<? data?>")),
                Arguments.of("a processing instruction target that does not start with a name character",
                        body("<?1abc data?>")),
                Arguments.of("a processing instruction target followed by a quote", body("<?target\"x\"?>")),
                Arguments.of("a table name that starts with an arrow", rowOf(ARROW + "T", "ID")),
                Arguments.of("a column name with an arrow in it", rowOf("T", "A" + ARROW + "B")),
                Arguments.of("a table name that starts with an ideographic comma", rowOf(COMMA + "T", "ID")),
                Arguments.of("a column name that starts with a compatibility ideograph",
                        rowOf("T", COMPATIBILITY_IDEOGRAPH + "ID")),
                Arguments.of("a column name with a supplementary character in it",
                        rowOf("T", "A" + EMOJI + "B")),
                Arguments.of("a table name that is a supplementary character", rowOf(EMOJI, "ID")),
                Arguments.of("a column name with a combining character that the fourth edition lacks",
                        rowOf("T", "A" + COMBINING_LETTER_X)));
    }

    private static Stream<Arguments> wellFormedTexts()
    {
        return Stream.of(
                Arguments.of("the five predefined entities in body text",
                        body("&amp; &lt; &gt; &quot; &apos;")),
                Arguments.of("the five predefined entities in row text",
                        rowText("&amp; &lt; &gt; &quot; &apos;")),
                Arguments.of("character references in body text",
                        body("&#65; &#x41; &#9; &#10; &#13; &#x1F600; &#xFDD0; &#x85;")),
                Arguments.of("greater-than signs and close brackets in body text", body("a > b ]] > c ] d")),
                Arguments.of("a CDATA end in a comment", body("<!-- a ]]> b -->")),
                Arguments.of("a CDATA end in a processing instruction", body("<?target a ]]> b?>")),
                Arguments.of("a CDATA end in an attribute value",
                        "<dataset>\n    <USERS ID=\"a]]>b\"/>\n</dataset>\n"),
                Arguments.of("a bare ampersand in a comment", body("<!-- R&D -->")),
                Arguments.of("a bare ampersand in a processing instruction", body("<?target R&D?>")),
                Arguments.of("a bare ampersand in a CDATA section", body("<![CDATA[R&D]]>")),
                Arguments.of("an undeclared entity in a CDATA section", body("<![CDATA[&nbsp;]]>")),
                Arguments.of("a bracket before the end of a CDATA section", body("<![CDATA[x]]]>")),
                Arguments.of("supplementary characters in text, a comment, and a CDATA section",
                        body("a" + EMOJI + "b <!-- " + EMOJI + " --><![CDATA[" + EMOJI + "]]>")),
                Arguments.of("DEL, NEL, and a noncharacter in body text",
                        body("a" + (char) 0x7F + (char) 0x85 + (char) 0xFDD0 + "b")),
                Arguments.of("an undeclared entity next to a SYSTEM identifier",
                        EXTERNAL_DOCTYPE + body("&nbsp;")),
                Arguments.of("an undeclared entity in row text next to a SYSTEM identifier",
                        EXTERNAL_DOCTYPE + rowText("&nbsp;")),
                Arguments.of("an undeclared entity next to PUBLIC and SYSTEM identifiers",
                        XML_DECLARATION + "<!DOCTYPE dataset PUBLIC \"-//DbUnit//DTD Test//EN\" "
                                + "\"plain.dtd\">\n" + body("&nbsp;")),
                Arguments.of("an entity that the external DTD declares",
                        XML_DECLARATION + "<!DOCTYPE dataset SYSTEM \"entity.dtd\">\n" + body("&nbsp;")),
                Arguments.of("an entity that the internal subset declares",
                        INTERNAL_SUBSET_START + "<!ENTITY nbsp \"&#160;\">\n]>\n" + body("&nbsp;")),
                Arguments.of("an entity that the internal subset declares, in row text",
                        INTERNAL_SUBSET_START + "<!ENTITY nbsp \"&#160;\">\n]>\n" + rowText("&nbsp;")),
                Arguments.of("an XML declaration at the start", XML_DECLARATION + body("")),
                Arguments.of("an XML declaration after a byte order mark",
                        "\uFEFF" + XML_DECLARATION + body("")),
                Arguments.of("an XML declaration with an encoding and a standalone flag",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + body("")),
                Arguments.of("a processing instruction whose target starts with xml",
                        body("<?xml-stylesheet href=\"a.xsl\"?>")),
                Arguments.of("a processing instruction before the root", "<?target data?>\n" + body("")),
                Arguments.of("a processing instruction without data", body("<?target?>")),
                Arguments.of("a processing instruction target with hyphens, dots, and digits",
                        body("<?a-b.c1 data?>")),
                Arguments.of("single hyphens in a comment", body("<!-- a - b - c -->")),
                Arguments.of("an empty comment", body("<!---->")),
                Arguments.of("a comment with a hyphen before its end", body("<!-- a- -->")),
                Arguments.of("a comment of hyphens and spaces", body("<!-- - - - -->")),
                Arguments.of("a comment in the internal subset",
                        INTERNAL_SUBSET_START + "<!-- a - b -->\n]>\n" + body("")),
                Arguments.of("a column name that starts with a Thai letter",
                        rowOf("USERS", THAI_LETTER + "ID")),
                Arguments.of("a column name that starts with an ideograph", rowOf("USERS", IDEOGRAPH + "ID")),
                Arguments.of("a column name with a middle dot and a combining accent in it",
                        rowOf("USERS", "A" + MIDDLE_DOT + "B" + COMBINING_GRAVE_ACCENT)),
                Arguments.of("a column name with a hyphen, a dot, and digits in it",
                        rowOf("USERS", "A-1.2")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedTexts")
    void testBuild_whenTheTextIsNotWellFormed_dbUnitFailsToLoadItAndTheParserBlocksEditing(
            final String description, final String text) throws Exception
    {
        final File file = write(text);

        assertThatThrownBy(() -> new FlatXmlDataSetBuilder().build(file))
                .as("dbUnit must fail to load " + description + ", because its XML parser rejects it.")
                .isInstanceOf(DataSetException.class).hasRootCauseInstanceOf(SAXParseException.class);
        final FlatXmlParseResult result = FlatXmlParser.parse(text);
        assertThat(result.wellFormed())
                .as("The parser must report " + description + " as not well-formed, as dbUnit does.")
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wellFormedTexts")
    void testBuild_whenTheTextIsWellFormed_dbUnitLoadsItAndTheParserAllowsEditing(final String description,
            final String text) throws Exception
    {
        final File file = write(text);

        final IDataSet dataSet = new FlatXmlDataSetBuilder().build(file);
        assertThat(dataSet.getTableNames()).as("dbUnit must load " + description + " as a dataset.")
                .containsExactly("USERS");
        final FlatXmlParseResult result = FlatXmlParser.parse(text);
        assertThat(result.wellFormed())
                .as("The parser must accept " + description + ", as dbUnit does.").isTrue();
    }

    private File write(final String text) throws Exception
    {
        final Path file = tempDir.resolve("dataset.xml");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file.toFile();
    }
}
