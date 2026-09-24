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
import static org.eclipse.core.runtime.content.IContentDescriber.INDETERMINATE;
import static org.eclipse.core.runtime.content.IContentDescriber.INVALID;
import static org.eclipse.core.runtime.content.IContentDescriber.VALID;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlContentDescriber} against the content-recognition rules of the specification.
 */
class FlatXmlContentDescriberTest
{
    @Test
    void testDescribe_whenContentIsAFlatDataset_returnsValid() throws IOException
    {
        assertThat(describe("<dataset><USERS ID=\"1\"/></dataset>"))
                .as("A flat dataset must be recognized as valid.").isEqualTo(VALID);
    }

    @Test
    void testDescribe_whenDoctypeReferencesADtdFileThatDoesNotExist_returnsValid() throws IOException
    {
        final String content = "<!DOCTYPE dataset SYSTEM \"does-not-exist.dtd\">"
                + "<dataset><USERS ID=\"1\"/></dataset>";
        assertThat(describe(content))
                .as("An external DTD must never be loaded, so a missing file must not matter.")
                .isEqualTo(VALID);
    }

    @Test
    void testDescribe_whenInternalSubsetDeclaresAnExternalEntity_returnsValidWithoutFileAccess()
            throws IOException
    {
        final String content = "<!DOCTYPE dataset [\n"
                + "<!ENTITY ext SYSTEM \"does-not-exist.txt\">\n" + "]>\n"
                + "<dataset><USERS NAME=\"&ext;\"/></dataset>";
        assertThat(describe(content))
                .as("An external entity must never be read, so a missing file must not matter.")
                .isEqualTo(VALID);
    }

    @Test
    void testDescribe_whenContentIsTheFullXmlDataSetFormat_returnsInvalid() throws IOException
    {
        final String content = "<dataset><table name=\"USERS\"><column>ID</column>"
                + "<row><value>1</value></row></table></dataset>";
        assertThat(describe(content)).as("The full XmlDataSet format must not be recognized.")
                .isEqualTo(INVALID);
    }

    @Test
    void testDescribe_whenRootIsNotDataset_returnsInvalid() throws IOException
    {
        assertThat(describe("<notADataset/>")).as("A root other than dataset must be invalid.")
                .isEqualTo(INVALID);
    }

    @Test
    void testDescribe_whenDatasetIsEmpty_returnsValid() throws IOException
    {
        assertThat(describe("<dataset/>")).as("A dataset with no tables must be valid.").isEqualTo(VALID);
    }

    @Test
    void testDescribe_whenContentIsMalformedAfterTheRoot_returnsValid() throws IOException
    {
        assertThat(describe("<dataset><USERS ID=\"1\""))
                .as("A file still being edited must be valid once the root has started.")
                .isEqualTo(VALID);
    }

    @Test
    void testDescribe_whenContentIsEntirelyEmpty_returnsValid() throws IOException
    {
        assertThat(describe(""))
                .as("An empty file is a dataset waiting to be created, so it must be valid.")
                .isEqualTo(VALID);
    }

    @Test
    void testDescribe_whenContentIsWhitespaceOnly_returnsValid() throws IOException
    {
        assertThat(describe(" \n\t \n")).as("A whitespace-only file must also be valid.")
                .isEqualTo(VALID);
    }

    @Test
    void testDescribe_whenContentIsMalformedBeforeTheRoot_returnsIndeterminate() throws IOException
    {
        assertThat(describe("<data")).as("A parse error before the root must be indeterminate.")
                .isEqualTo(INDETERMINATE);
    }

    @Test
    void testDescribe_readerVariant_matchesTheInputStreamVariant() throws IOException
    {
        final String content = "<dataset><USERS ID=\"1\"/></dataset>";
        final FlatXmlContentDescriber describer = new FlatXmlContentDescriber();

        final int fromReader = describer.describe(new StringReader(content), null);

        assertThat(fromReader).as("The Reader variant must match the InputStream variant.")
                .isEqualTo(describe(content));
    }

    private static int describe(final String content) throws IOException
    {
        final FlatXmlContentDescriber describer = new FlatXmlContentDescriber();
        final InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        return describer.describe(stream, null);
    }
}
