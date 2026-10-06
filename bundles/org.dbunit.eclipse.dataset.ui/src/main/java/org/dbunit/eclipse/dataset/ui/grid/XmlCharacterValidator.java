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
package org.dbunit.eclipse.dataset.ui.grid;

import org.dbunit.eclipse.dataset.core.flatxml.AttributeValueCodec;
import org.eclipse.nebula.widgets.nattable.data.validate.DataValidator;
import org.eclipse.nebula.widgets.nattable.data.validate.ValidationFailedException;

/**
 * Rejects an edited value that contains a code point that is not an XML 1.0 {@code Char}.
 *
 * @since 1.0.0
 */
final class XmlCharacterValidator extends DataValidator
{
    @Override
    public boolean validate(final int columnIndex, final int rowIndex, final Object newValue)
    {
        if (newValue == null)
        {
            return true;
        }
        final String text = newValue.toString();
        for (int offset = 0; offset < text.length();)
        {
            final int codePoint = text.codePointAt(offset);
            if (!AttributeValueCodec.isXmlChar(codePoint))
            {
                throw new ValidationFailedException(AttributeValueCodec.notXmlCharacterMessage(codePoint));
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }
}
