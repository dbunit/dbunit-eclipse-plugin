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
 * @param offset The offset of the declaration's opening angle bracket.
 * @param endOffset The offset just after the declaration's closing {@code >}.
 */
record FlatXmlDoctype(String rootName, String publicId, String systemId, String internalSubset, int offset,
        int endOffset)
{
}
