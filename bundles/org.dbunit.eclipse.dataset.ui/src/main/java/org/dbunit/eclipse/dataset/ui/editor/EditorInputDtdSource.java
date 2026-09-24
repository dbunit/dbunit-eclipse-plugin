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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.ide.ResourceUtil;

/**
 * Loads an external DTD relative to an editor input, without ever accessing the network.
 *
 * @since 1.0.0
 */
final class EditorInputDtdSource implements DtdSource
{
    private static final String XML_CONTENT_TYPE = "org.eclipse.core.runtime.xml";

    private final IEditorInput input;

    EditorInputDtdSource(final IEditorInput input)
    {
        this.input = input;
    }

    @Override
    public Optional<String> load(final String publicId, final String systemId)
    {
        if (systemId == null || systemId.isEmpty())
        {
            return Optional.empty();
        }
        try
        {
            return loadResolved(systemId);
        }
        catch (final RuntimeException e)
        {
            return Optional.empty();
        }
    }

    private Optional<String> loadResolved(final String systemId)
    {
        if (isWindowsPath(systemId))
        {
            return readLocalFile(Paths.get(systemId));
        }
        final URI asUri = parseUri(systemId);
        if (asUri != null && asUri.isAbsolute())
        {
            if (!"file".equalsIgnoreCase(asUri.getScheme()))
            {
                return Optional.empty();
            }
            return readLocalFile(Paths.get(asUri));
        }
        return loadRelative(systemId);
    }

    private Optional<String> loadRelative(final String systemId)
    {
        final IFile datasetFile = ResourceUtil.getFile(input);
        if (datasetFile != null)
        {
            final URI location = datasetFile.getLocationURI();
            if (location != null)
            {
                return loadUriRelative(location, systemId);
            }
            return loadWorkspaceRelative(datasetFile, systemId);
        }
        final IFileStore fileStore = input.getAdapter(IFileStore.class);
        if (fileStore != null)
        {
            return loadUriRelative(fileStore.toURI(), systemId);
        }
        return Optional.empty();
    }

    private static Optional<String> loadWorkspaceRelative(final IFile datasetFile, final String systemId)
    {
        final IContainer folder = datasetFile.getParent();
        if (folder == null)
        {
            return Optional.empty();
        }
        final IFile dtdFile = folder.getFile(new org.eclipse.core.runtime.Path(systemId));
        if (!dtdFile.exists())
        {
            return Optional.empty();
        }
        try (InputStream stream = dtdFile.getContents())
        {
            final Charset charset = Charset.forName(dtdFile.getCharset());
            return Optional.of(new String(stream.readAllBytes(), charset));
        }
        catch (final CoreException | IOException e)
        {
            return Optional.empty();
        }
    }

    private static Optional<String> loadUriRelative(final URI datasetUri, final String systemId)
    {
        final URI resolved;
        try
        {
            resolved = datasetUri.resolve(systemId);
        }
        catch (final IllegalArgumentException e)
        {
            return Optional.empty();
        }
        if (!"file".equalsIgnoreCase(resolved.getScheme()))
        {
            return Optional.empty();
        }
        return readLocalFile(Paths.get(resolved));
    }

    private static Optional<String> readLocalFile(final Path path)
    {
        try
        {
            final byte[] content = Files.readAllBytes(path);
            final Charset charset = detectCharset(content);
            return Optional.of(charset.newDecoder().decode(ByteBuffer.wrap(content)).toString());
        }
        catch (final IOException e)
        {
            return Optional.empty();
        }
    }

    /**
     * Returns the charset Eclipse's XML content-type describer detects from a byte-order mark or a text
     * declaration in content, the same way {@link IFile#getCharset()} detects it for a workspace file,
     * falling back to UTF-8 when nothing is detected.
     */
    private static Charset detectCharset(final byte[] content)
    {
        final IContentType xmlContentType =
                Platform.getContentTypeManager().getContentType(XML_CONTENT_TYPE);
        if (xmlContentType == null)
        {
            return StandardCharsets.UTF_8;
        }
        try (InputStream stream = new ByteArrayInputStream(content))
        {
            final IContentDescription description =
                    xmlContentType.getDescriptionFor(stream, IContentDescription.ALL);
            final String charsetName = description == null ? null : description.getCharset();
            return charsetName == null ? StandardCharsets.UTF_8 : Charset.forName(charsetName);
        }
        catch (final IOException | IllegalArgumentException e)
        {
            return StandardCharsets.UTF_8;
        }
    }

    private static URI parseUri(final String value)
    {
        try
        {
            return new URI(value);
        }
        catch (final URISyntaxException e)
        {
            return null;
        }
    }

    private static boolean isWindowsPath(final String value)
    {
        return value.length() >= 2 && Character.isLetter(value.charAt(0)) && value.charAt(1) == ':';
    }
}
