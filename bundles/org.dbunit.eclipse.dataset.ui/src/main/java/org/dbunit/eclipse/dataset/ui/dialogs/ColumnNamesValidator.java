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

import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.flatxml.XmlNames;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.osgi.util.NLS;

/**
 * Validates a table's optional column names: each must be a valid XML name, and no two may match,
 * case-insensitively.
 *
 * @since 1.0.0
 */
public final class ColumnNamesValidator
{
    /**
     * Validates a list of column names.
     *
     * @param columnNames The column names to validate, in the entered order.
     * @return An error message naming the first invalid or duplicate column; null when every name is a
     *         valid, unique XML name.
     */
    public String isValid(final List<String> columnNames)
    {
        final List<String> seenColumnNames = new ArrayList<>();
        for (final String columnName : columnNames)
        {
            if (!XmlNames.isValidName(columnName))
            {
                return NLS.bind(Messages.NameValidator_invalid, columnName);
            }
            for (final String seenColumnName : seenColumnNames)
            {
                if (seenColumnName.equalsIgnoreCase(columnName))
                {
                    return NLS.bind(Messages.NameValidator_used, columnName);
                }
            }
            seenColumnNames.add(columnName);
        }
        return null;
    }
}
