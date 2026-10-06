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
import java.util.List;

import org.dbunit.eclipse.dataset.core.flatxml.XmlNames;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.osgi.util.NLS;

/**
 * Validates a column name: it must be a valid XML name, and not already used.
 *
 * @since 1.0.0
 */
public final class DatasetNameValidator implements IInputValidator
{
    private final Collection<String> existingNames;

    /**
     * Creates a validator.
     *
     * @param existingNames The names already in use; the entered name must not have the same key as any
     *                       of them, which is how the dataset model tells columns apart, see
     *                       {@link DatasetColumn#keyOf}. The validator keeps its own copy of them.
     */
    public DatasetNameValidator(final Collection<String> existingNames)
    {
        this.existingNames = List.copyOf(existingNames);
    }

    @Override
    public String isValid(final String newText)
    {
        if (newText == null || newText.isEmpty())
        {
            return Messages.NameValidator_empty;
        }
        if (!XmlNames.isValidName(newText))
        {
            return NLS.bind(Messages.NameValidator_invalid, newText);
        }
        final String newKey = DatasetColumn.keyOf(newText);
        for (final String existingName : existingNames)
        {
            if (DatasetColumn.keyOf(existingName).equals(newKey))
            {
                return NLS.bind(Messages.NameValidator_used, newText);
            }
        }
        return null;
    }
}
