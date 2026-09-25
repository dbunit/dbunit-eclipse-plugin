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
package org.dbunit.eclipse.dataset.ui.dialogs;

import java.util.Collection;

import org.dbunit.eclipse.dataset.core.flatxml.XmlNames;
import org.eclipse.jface.dialogs.IInputValidator;

/**
 * Validates a column or table name: it must be a valid XML name, and not already used.
 *
 * @since 1.0.0
 */
public final class DatasetNameValidator implements IInputValidator
{
    private final Collection<String> existingNames;

    /**
     * Creates a validator.
     *
     * @param existingNames The names already in use; the entered name must not match any of them,
     *                       case-insensitively.
     */
    public DatasetNameValidator(final Collection<String> existingNames)
    {
        this.existingNames = existingNames;
    }

    @Override
    public String isValid(final String newText)
    {
        if (newText == null || newText.isEmpty())
        {
            return "Enter a name.";
        }
        if (!XmlNames.isValidName(newText))
        {
            return "'" + newText + "' is not a valid XML name.";
        }
        for (final String existingName : existingNames)
        {
            if (existingName.equalsIgnoreCase(newText))
            {
                return "'" + newText + "' is already used.";
            }
        }
        return null;
    }
}
