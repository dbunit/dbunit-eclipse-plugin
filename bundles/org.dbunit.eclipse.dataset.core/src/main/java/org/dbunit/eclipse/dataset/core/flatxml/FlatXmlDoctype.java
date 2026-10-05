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

/**
 * A parsed {@code <!DOCTYPE ...>} declaration.
 *
 * @param rootName The document type name.
 * @param publicId The public identifier, or null when none was given.
 * @param systemId The system identifier, or null when none was given.
 * @param internalSubset The raw text of the internal subset, or null when there is none.
 * @param internalSubsetOffset The offset of the internal subset's first character, right after its
 *                              opening {@code [}; meaningless when internalSubset is null.
 * @param offset The offset of the declaration's opening angle bracket.
 * @param endOffset The offset just after the declaration's closing {@code >}.
 */
record FlatXmlDoctype(String rootName, String publicId, String systemId, String internalSubset,
        int internalSubsetOffset, int offset, int endOffset)
{
    /**
     * Tells whether the declaration can declare an entity: its DTD has an external subset, or its internal
     * subset holds an {@code ENTITY} declaration. Without either, only the five predefined entities exist.
     *
     * @return True when an entity reference may name an entity that this declaration declares.
     */
    boolean mayDeclareEntities()
    {
        final boolean hasExternalSubset = systemId != null || publicId != null;
        final boolean hasInternalEntity = internalSubset != null && internalSubset.contains("<!ENTITY");
        return hasExternalSubset || hasInternalEntity;
    }
}
