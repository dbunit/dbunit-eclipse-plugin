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
import java.util.function.Function;

import org.dbunit.eclipse.dataset.core.flatxml.XmlNames;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.osgi.util.NLS;

/**
 * Validates a table name: it must be a valid XML name, not already used, and not the name reserved for
 * the root element.
 *
 * @since 1.0.0
 */
public final class TableNameValidator implements IInputValidator
{
    private static final String RESERVED_ROOT_NAME = "dataset";

    private final Function<String, String> tableKeyOf;

    private final Collection<String> existingNames;

    /**
     * Creates a validator.
     *
     * @param tableKeyOf Computes the key a table name would have, accounting for the document's
     *                    case-sensitivity rule; see {@code DatasetDocument.tableKeyOf}.
     * @param existingNames The names already in use; the entered name must not match any of them, by key.
     *                       The validator keeps its own copy of them.
     */
    public TableNameValidator(final Function<String, String> tableKeyOf, final Collection<String> existingNames)
    {
        this.tableKeyOf = tableKeyOf;
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
        final String newKey = tableKeyOf.apply(newText);
        if (newKey.equals(tableKeyOf.apply(RESERVED_ROOT_NAME)))
        {
            return NLS.bind(Messages.TableNameValidator_reserved, newText);
        }
        for (final String existingName : existingNames)
        {
            if (tableKeyOf.apply(existingName).equals(newKey))
            {
                return NLS.bind(Messages.NameValidator_used, newText);
            }
        }
        return null;
    }
}
