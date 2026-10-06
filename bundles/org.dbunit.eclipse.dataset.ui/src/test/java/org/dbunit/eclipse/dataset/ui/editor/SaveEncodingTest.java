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

import java.nio.charset.StandardCharsets;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link SaveEncoding}: which encoding the text decides, and that a file with an encoding of its own
 * leaves the decision to the file buffer.
 */
class SaveEncodingTest
{
    private static final String DECLARING_ISO_8859_1 =
            "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><dataset/>";

    @Test
    void testDecidedByText_whenTheTextDeclaresAnEncoding_returnsIt()
    {
        final SaveEncoding saveEncoding = saveEncodingOf("dataset.xml", new Document(DECLARING_ISO_8859_1));

        assertThat(saveEncoding.decidedByText())
                .as("The encoding that the declaration names must be the one.").contains("ISO-8859-1");
    }

    @Test
    void testDecidedByText_whenTheDeclarationIsEditedAfterwards_returnsTheEncodingItNamesNow()
            throws BadLocationException
    {
        final Document document = new Document(DECLARING_ISO_8859_1);
        final SaveEncoding saveEncoding = saveEncodingOf("dataset.xml", document);
        final int offset = DECLARING_ISO_8859_1.indexOf("ISO-8859-1");

        document.replace(offset, "ISO-8859-1".length(), "US-ASCII");

        assertThat(saveEncoding.decidedByText())
                .as("The text is asked each time, so an edit of the declaration counts at once.")
                .contains("US-ASCII");
    }

    @Test
    void testDecidedByText_whenTheTextHasNoDeclaration_returnsTheDefaultOfTheXmlContentType()
    {
        final SaveEncoding saveEncoding = saveEncodingOf("dataset.xml", new Document("<dataset/>"));

        assertThat(saveEncoding.decidedByText())
                .as("The file buffer takes the default of the content type for XML without a declaration.")
                .contains("UTF-8");
    }

    @Test
    void testDecidedByText_whenTheTextIsMuchLongerThanTheStartThatIsLookedAt_stillFindsTheDeclaration()
    {
        final String longText = DECLARING_ISO_8859_1 + "<!--" + "x".repeat(100_000) + "-->";
        final SaveEncoding saveEncoding = saveEncodingOf("dataset.xml", new Document(longText));

        assertThat(saveEncoding.decidedByText())
                .as("The declaration is at the start, so the length of the rest must not matter.")
                .contains("ISO-8859-1");
    }

    @Test
    void testDecidedByText_whenTheDocumentIsEmpty_returnsTheDefaultOfTheXmlContentType()
    {
        final SaveEncoding saveEncoding = saveEncodingOf("dataset.xml", new Document(""));

        assertThat(saveEncoding.decidedByText())
                .as("An empty text has no declaration, and the file name makes it XML.").contains("UTF-8");
    }

    @Test
    void testDecidedByText_whenNoContentTypeMatches_returnsEmpty()
    {
        final SaveEncoding saveEncoding = saveEncodingOf("data.zzz", new Document("not xml, no known type"));

        assertThat(saveEncoding.decidedByText())
                .as("Without a content type, the encoding of the file buffer is the one.").isEmpty();
    }

    @Test
    void testDecidedByText_whenTheFileHasNoEncodingOfItsOwn_returnsTheOneOfTheText() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DECLARING_ISO_8859_1,
                    StandardCharsets.ISO_8859_1);
            final SaveEncoding saveEncoding = new SaveEncoding(() -> new FileEditorInput(file),
                    () -> new Document(DECLARING_ISO_8859_1));

            assertThat(saveEncoding.decidedByText())
                    .as("A file that has no encoding of its own is saved in the encoding of its text.")
                    .contains("ISO-8859-1");
        }
    }

    @Test
    void testDecidedByText_whenTheFileHasAnEncodingOfItsOwn_returnsEmpty() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", DECLARING_ISO_8859_1,
                    StandardCharsets.ISO_8859_1);
            file.setCharset("UTF-8", null);
            final SaveEncoding saveEncoding = new SaveEncoding(() -> new FileEditorInput(file),
                    () -> new Document(DECLARING_ISO_8859_1));

            assertThat(saveEncoding.decidedByText())
                    .as("A file with an encoding of its own is saved in it, whatever the text says.")
                    .isEmpty();
        }
    }

    private static SaveEncoding saveEncodingOf(final String fileName, final IDocument document)
    {
        final IFileStore fileStore = EFS.getLocalFileSystem().getStore(new Path("/dbunit-test/" + fileName));
        final IEditorInput input = new FileStoreEditorInput(fileStore);
        return new SaveEncoding(() -> input, () -> document);
    }
}
