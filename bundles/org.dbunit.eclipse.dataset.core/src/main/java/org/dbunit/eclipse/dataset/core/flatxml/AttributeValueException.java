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
 * Thrown when {@link AttributeValueCodec#decode(CharSequence)} finds an invalid entity or character
 * reference.
 *
 * @since 1.0.0
 */
public class AttributeValueException extends Exception
{
    private static final long serialVersionUID = 1L;

    private final int offset;

    private final boolean unsupportedEntity;

    /**
     * Creates an attribute value exception.
     *
     * @param message The complete sentence describing the problem.
     * @param offset The offset of the problem, relative to the start of the raw value.
     * @param unsupportedEntity True when the problem is an entity reference other than one of the five
     *                          predefined entities or a character reference.
     */
    public AttributeValueException(final String message, final int offset, final boolean unsupportedEntity)
    {
        super(message);
        this.offset = offset;
        this.unsupportedEntity = unsupportedEntity;
    }

    /**
     * Returns the offset of the problem.
     *
     * @return The offset, relative to the start of the raw value.
     */
    public int getOffset()
    {
        return offset;
    }

    /**
     * Returns whether the problem is an unsupported entity reference.
     *
     * @return True when the problem is an entity reference other than one of the five predefined
     *         entities or a character reference; false for any other decoding error.
     */
    public boolean isUnsupportedEntity()
    {
        return unsupportedEntity;
    }
}
