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

import java.net.URI;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link SystemIdentifier}: which characters of a system identifier are escaped to make a URI
 * reference of it, as the XML parser of dbUnit does.
 */
class SystemIdentifierTest
{
    @Test
    void testEscaped_whenTheTextIsAValidUriReference_isUnchanged()
    {
        final String text = "file:///C:/dtds/my-data_set.v2.dtd?a=1&b=2#part";

        assertThat(new SystemIdentifier(text, false).escaped()).as("A valid URI reference needs no escape.")
                .isEqualTo(text);
    }

    @Test
    void testEscaped_whenTheTextHasASpace_escapesIt()
    {
        assertThat(new SystemIdentifier("test data/my dtd.dtd", false).escaped())
                .as("A space is escaped with its code.").isEqualTo("test%20data/my%20dtd.dtd");
    }

    @Test
    void testEscaped_whenTheTextHasCharactersThatAUriCannotHold_escapesEach()
    {
        assertThat(new SystemIdentifier("a\"<>^`{|}[]b", false).escaped())
                .as("Each of these characters is no part of a URI.")
                .isEqualTo("a%22%3C%3E%5E%60%7B%7C%7D%5B%5Db");
    }

    @Test
    void testEscaped_whenTheTextHasAControlCharacterOrASpaceSeparator_escapesItAsUtf8()
    {
        assertThat(new SystemIdentifier("a\tb\u00a0c", false).escaped())
                .as("A control character and a no-break space are escaped, the latter as two bytes.")
                .isEqualTo("a%09b%C2%A0c");
    }

    @Test
    void testEscaped_whenTheTextHasLettersBeyondAscii_keepsThem()
    {
        final String text = "caf\u00e9/\ud83d\ude00.dtd";

        assertThat(new SystemIdentifier(text, false).escaped())
                .as("A URI may hold such letters, which the parser does not escape either.").isEqualTo(text);
    }

    @Test
    void testEscaped_whenThePercentSignStartsAnEscapeSequence_keepsIt()
    {
        assertThat(new SystemIdentifier("test%20data/a%2fb%E9.dtd", false).escaped())
                .as("An escape sequence is escaped already.").isEqualTo("test%20data/a%2fb%E9.dtd");
    }

    @Test
    void testEscaped_whenThePercentSignStartsNoEscapeSequence_escapesIt()
    {
        assertThat(new SystemIdentifier("100%.dtd 5%2 %zz 7%", false).escaped())
                .as("A percent sign without two hexadecimal digits is a character of its own.")
                .isEqualTo("100%25.dtd%205%252%20%25zz%207%25");
    }

    @Test
    void testEscaped_whenBackslashIsTheSeparator_turnsBackslashesIntoSlashes()
    {
        assertThat(new SystemIdentifier("dtd\\sub dir\\my.dtd", true).escaped())
                .as("Where the file system separates names with a backslash, a parser reads a slash.")
                .isEqualTo("dtd/sub%20dir/my.dtd");
    }

    @Test
    void testEscaped_whenBackslashIsNotTheSeparator_escapesBackslashes()
    {
        assertThat(new SystemIdentifier("dtd\\my.dtd", false).escaped())
                .as("Elsewhere a backslash is a character of the name.").isEqualTo("dtd%5Cmy.dtd");
    }

    @Test
    void testToUri_whenTheTextHasASpace_decodesToTheSameText() throws Exception
    {
        final URI uri = new SystemIdentifier("test data/my.dtd", false).toUri();

        assertThat(uri.getPath()).as("The path of the URI must be the text that was written.")
                .isEqualTo("test data/my.dtd");
    }
}
