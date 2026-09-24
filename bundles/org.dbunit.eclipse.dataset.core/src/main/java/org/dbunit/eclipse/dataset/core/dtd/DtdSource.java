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
package org.dbunit.eclipse.dataset.core.dtd;

import java.util.Optional;

/**
 * Loads the text of an external DTD referenced by a DOCTYPE.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface DtdSource
{
    /**
     * A source that never finds a DTD.
     */
    DtdSource NONE = (publicId, systemId) -> Optional.empty();

    /**
     * Loads a DTD's text.
     *
     * @param publicId The DOCTYPE's public identifier, or null when there is none.
     * @param systemId The DOCTYPE's system identifier, or null when there is none.
     * @return The DTD's text, or an empty {@link Optional} when it could not be read.
     */
    Optional<String> load(String publicId, String systemId);
}
