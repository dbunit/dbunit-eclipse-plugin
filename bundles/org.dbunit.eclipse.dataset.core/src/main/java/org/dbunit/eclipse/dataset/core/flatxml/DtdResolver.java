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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.dbunit.eclipse.dataset.core.DatasetCore;
import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdReader;
import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * Reads the DTD declarations that a document's DOCTYPE brings in: those of its internal subset, and those of
 * the external DTD that its system identifier names, which are loaded through a {@link DtdSource} and cached
 * per system identifier until they are reloaded. A source that fails is treated as one that finds no DTD, and
 * the failure is reported through the log sink, which the core bundle cannot replace with the platform log.
 */
final class DtdResolver
{
    private final Map<String, CachedDtd> dtdCache = new LinkedHashMap<>();

    private final DtdSource dtdSource;

    private final Consumer<IStatus> log;

    DtdResolver(final DtdSource dtdSource, final Consumer<IStatus> log)
    {
        this.dtdSource = dtdSource;
        this.log = log;
    }

    /**
     * Resolves the declarations of a DOCTYPE.
     *
     * @param doctype The document's DOCTYPE declaration, or null when it has none.
     * @return The declarations, and whether the external DTD that the DOCTYPE names could be loaded.
     */
    DtdResolution resolve(final FlatXmlDoctype doctype)
    {
        if (doctype == null)
        {
            return new DtdResolution(null, DtdState.NONE);
        }
        final String internalSubsetText = doctype.internalSubset();
        final String rootName = doctype.rootName();
        DtdDeclarations internalSubset =
                DtdReader.read(internalSubsetText == null ? "" : internalSubsetText, rootName);
        if (internalSubsetText != null)
        {
            internalSubset = internalSubset.withProblemsShiftedBy(doctype.internalSubsetOffset());
        }
        if (doctype.systemId() == null)
        {
            return new DtdResolution(internalSubset, DtdState.LOADED);
        }
        final String externalText = loadExternalDtd(doctype.publicId(), doctype.systemId());
        if (externalText == null)
        {
            return new DtdResolution(internalSubset, DtdState.NOT_LOADED);
        }
        final DtdDeclarations external = DtdReader.read(externalText, rootName)
                .withProblemsAt(doctype.offset(), doctype.endOffset() - doctype.offset());
        return new DtdResolution(internalSubset.merge(external), DtdState.LOADED);
    }

    /**
     * Reads every cached DTD again through the source, and replaces the cached text of each DTD whose text
     * differs, including one that became readable or unreadable.
     *
     * @return True when any cached text changed.
     */
    boolean reload()
    {
        boolean changed = false;
        for (final Map.Entry<String, CachedDtd> entry : new LinkedHashMap<>(dtdCache).entrySet())
        {
            final String systemId = entry.getKey();
            final CachedDtd cached = entry.getValue();
            final String freshText = loadFromDtdSource(cached.publicId(), systemId);
            if (!Objects.equals(freshText, cached.text()))
            {
                dtdCache.put(systemId, new CachedDtd(cached.publicId(), freshText));
                changed = true;
            }
        }
        return changed;
    }

    private String loadExternalDtd(final String publicId, final String systemId)
    {
        final CachedDtd cached = dtdCache.get(systemId);
        if (cached != null)
        {
            return cached.text();
        }
        final String text = loadFromDtdSource(publicId, systemId);
        dtdCache.put(systemId, new CachedDtd(publicId, text));
        return text;
    }

    /**
     * Loads from {@link #dtdSource}, treating a failure the same as a DTD it could not find, so that no
     * {@link DtdSource} implementation can break the model by letting an unchecked exception escape. The
     * failure is logged.
     */
    private String loadFromDtdSource(final String publicId, final String systemId)
    {
        try
        {
            return dtdSource.load(publicId, systemId).orElse(null);
        }
        catch (final RuntimeException e)
        {
            final String message = "The DTD source failed to load the DTD \"" + systemId
                    + "\", so it is treated as not found.";
            warn(message, e);
            return null;
        }
    }

    private void warn(final String message, final RuntimeException cause)
    {
        final IStatus status = new Status(IStatus.WARNING, DatasetCore.PLUGIN_ID, message, cause);
        log.accept(status);
    }

    private record CachedDtd(String publicId, String text)
    {
    }
}
