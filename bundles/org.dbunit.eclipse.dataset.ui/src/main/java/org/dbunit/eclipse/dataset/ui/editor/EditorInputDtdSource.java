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
import java.io.File;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.ide.ResourceUtil;

/**
 * Loads an external DTD relative to an editor input, without ever accessing the network. It refuses a
 * system ID that reaches a network share, such as {@code //host/share/dataset.dtd}, which Windows opens by
 * connecting to the host. A DTD that cannot be read is logged with the reason, so that the "DTD not loaded"
 * warning has an explanation; a DTD that stays unreadable is logged once, not each time the editor loads it
 * again.
 *
 * @since 1.0.0
 */
final class EditorInputDtdSource implements DtdSource
{
    private static final ILog LOG = ILog.of(EditorInputDtdSource.class);

    private static final String XML_CONTENT_TYPE = "org.eclipse.core.runtime.xml";

    /**
     * The start of the root of a Windows path on a network share, such as {@code \\host\share\}.
     */
    private static final String UNC_ROOT_PREFIX = "\\\\";

    private static final boolean BACKSLASH_IS_SEPARATOR = File.separatorChar == '\\';

    private final IEditorInput input;

    /**
     * The reason last logged for each system ID whose DTD could not be read, until a load of it succeeds.
     */
    private final Map<String, String> reportedFailures = new HashMap<>();

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
            final Optional<String> loaded = loadResolved(systemId);
            if (loaded.isPresent())
            {
                reportedFailures.remove(systemId);
            }
            return loaded;
        }
        catch (final RuntimeException e)
        {
            reportFailure(systemId, e);
            return Optional.empty();
        }
    }

    private Optional<String> loadResolved(final String systemId)
    {
        if (isWindowsPath(systemId))
        {
            return readLocalFile(systemId, Paths.get(systemId));
        }
        final URI asUri = parseUri(systemId);
        if (asUri != null && asUri.isAbsolute())
        {
            if (!"file".equalsIgnoreCase(asUri.getScheme()))
            {
                return Optional.empty();
            }
            return readLocalFile(systemId, Paths.get(asUri));
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

    private Optional<String> loadWorkspaceRelative(final IFile datasetFile, final String systemId)
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
            reportFailure(systemId, e);
            return Optional.empty();
        }
    }

    private Optional<String> loadUriRelative(final URI datasetUri, final String systemId)
    {
        final URI resolved;
        try
        {
            resolved = datasetUri.resolve(new SystemIdentifier(systemId, BACKSLASH_IS_SEPARATOR).toUri());
        }
        catch (final URISyntaxException | IllegalArgumentException e)
        {
            reportFailure(systemId, e);
            return Optional.empty();
        }
        if (!"file".equalsIgnoreCase(resolved.getScheme()))
        {
            return Optional.empty();
        }
        return readLocalFile(systemId, Paths.get(resolved));
    }

    private Optional<String> readLocalFile(final String systemId, final Path path)
    {
        requireNotOnNetworkShare(path);
        try
        {
            final byte[] content = Files.readAllBytes(path);
            final Charset charset = detectCharset(content);
            return Optional.of(charset.newDecoder().decode(ByteBuffer.wrap(content)).toString());
        }
        catch (final IOException e)
        {
            reportFailure(systemId, e);
            return Optional.empty();
        }
    }

    /**
     * Refuses a path on a network share. Windows opens a UNC path by connecting to its host and sending the
     * user's credentials, so a DOCTYPE with a system ID that names a host, such as
     * {@code //host/share/dataset.dtd} or {@code file://host/share/dataset.dtd}, would make the editor
     * contact that host each time it loads the DTD.
     */
    private static void requireNotOnNetworkShare(final Path path)
    {
        final Path root = path.getRoot();
        if (root == null)
        {
            return;
        }
        final String rootText = root.toString();
        if (rootText.startsWith(UNC_ROOT_PREFIX))
        {
            throw new IllegalArgumentException(
                    "The editor never reads a DTD from the network, and " + path + " is on a network share.");
        }
    }

    /**
     * Logs why the DTD could not be read, unless the same reason was already logged for it since the last
     * time it was read successfully.
     */
    private void reportFailure(final String systemId, final Exception cause)
    {
        final String reason = cause.toString();
        final String previousReason = reportedFailures.put(systemId, reason);
        if (reason.equals(previousReason))
        {
            return;
        }
        LOG.warn("The DTD \"" + systemId + "\" could not be read.", cause);
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
