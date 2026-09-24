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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
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
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.ide.ResourceUtil;

/**
 * Loads an external DTD relative to an editor input, without ever accessing the network.
 *
 * @since 1.0.0
 */
final class EditorInputDtdSource implements DtdSource
{
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
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        }
        catch (final IOException e)
        {
            return Optional.empty();
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
