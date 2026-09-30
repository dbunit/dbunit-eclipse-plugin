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

import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;

/**
 * The DTD declarations that a document's DOCTYPE brings in, and how the document relates to its DTD.
 *
 * @param declarations The declarations, or null when the document has no DOCTYPE.
 * @param state Whether the document has no DTD, or its DTD was loaded or could not be loaded.
 */
record DtdResolution(DtdDeclarations declarations, DtdState state)
{
}
