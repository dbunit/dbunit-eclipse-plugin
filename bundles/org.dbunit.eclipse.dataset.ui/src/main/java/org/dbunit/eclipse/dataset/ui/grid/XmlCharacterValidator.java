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

import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.nebula.widgets.nattable.data.validate.DataValidator;
import org.eclipse.nebula.widgets.nattable.data.validate.ValidationFailedException;
import org.eclipse.osgi.util.NLS;

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
            if (!isXmlChar(codePoint))
            {
                final String character = String.format("U+%04X", codePoint);
                throw new ValidationFailedException(
                        NLS.bind(Messages.XmlCharacterValidator_invalidCharacter, character));
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isXmlChar(final int codePoint)
    {
        return codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                || codePoint >= 0x20 && codePoint <= 0xD7FF || codePoint >= 0xE000 && codePoint <= 0xFFFD
                || codePoint >= 0x10000 && codePoint <= 0x10FFFF;
    }
}
