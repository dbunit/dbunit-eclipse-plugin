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

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;

/**
 * Works out which encoding a save of the Source page writes the text in, as far as the text decides it. When
 * the text file buffer saves, it takes the encoding that was set for the file, if there is one, else the
 * encoding that the content type manager makes out from the text as it is at that moment, which is the one
 * that its XML declaration names or else the default of its content type, and else the encoding that the
 * buffer had when the file was loaded. The encoding that the buffer reports does not follow an edit of the
 * declaration, so the dataset editor asks this class first, and leaves the last step to the buffer.
 *
 * @since 1.0.0
 */
final class SaveEncoding
{
    private static final ILog LOG = ILog.of(SaveEncoding.class);

    private static final QualifiedName[] OPTIONS = { IContentDescription.CHARSET };

    /**
     * How many characters from the start of the text the content type manager gets: far more than an XML
     * declaration, and enough for the describers to reach the root element.
     */
    private static final int PROBE_LENGTH = 8192;

    private final Supplier<IEditorInput> input;

    private final Supplier<IDocument> document;

    /**
     * Creates the finder for the text of the Source page. Both parts are asked for each time, because the
     * input and its document change when the file is saved under another name.
     *
     * @param input Returns the editor input of the Source page.
     * @param document Returns the document with the text of the Source page.
     */
    SaveEncoding(final Supplier<IEditorInput> input, final Supplier<IDocument> document)
    {
        this.input = input;
        this.document = document;
    }

    /**
     * Returns the encoding that the text decides.
     *
     * @return The name of the encoding, or empty when the file has an encoding of its own, which a save
     *         takes whatever the text says, or when no content type makes out an encoding for the text, so
     *         that the encoding of the buffer is the one.
     */
    Optional<String> decidedByText()
    {
        final IEditorInput editorInput = input.get();
        if (fileHasEncodingOfItsOwn(editorInput))
        {
            return Optional.empty();
        }
        return encodingOfText(editorInput.getName());
    }

    private static boolean fileHasEncodingOfItsOwn(final IEditorInput editorInput)
    {
        final IFile file = Adapters.adapt(editorInput, IFile.class);
        if (file == null)
        {
            return false;
        }
        try
        {
            final String charset = file.getCharset(false);
            return charset != null;
        }
        catch (final CoreException e)
        {
            LOG.log(e.getStatus());
            return false;
        }
    }

    private Optional<String> encodingOfText(final String fileName)
    {
        final IDocument text = document.get();
        final int length = Math.min(text.getLength(), PROBE_LENGTH);
        try
        {
            final String start = text.get(0, length);
            final IContentDescription description = Platform.getContentTypeManager()
                    .getDescriptionFor(new StringReader(start), fileName, OPTIONS);
            if (description == null)
            {
                return Optional.empty();
            }
            return Optional.ofNullable(description.getCharset());
        }
        catch (final IOException | BadLocationException e)
        {
            LOG.warn("The encoding of the text could not be made out, so the encoding of the file buffer is "
                    + "used.", e);
            return Optional.empty();
        }
    }
}
