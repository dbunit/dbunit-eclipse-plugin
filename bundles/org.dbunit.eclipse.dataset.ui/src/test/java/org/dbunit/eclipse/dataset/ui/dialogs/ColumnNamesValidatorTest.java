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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ColumnNamesValidator} against the Add Table dialog's column-name rules.
 */
class ColumnNamesValidatorTest
{
    @Test
    void testIsValid_forAColumnNameThatIsNotAValidXmlName_returnsAMessage()
    {
        final ColumnNamesValidator validator = new ColumnNamesValidator();

        assertThat(validator.isValid(List.of("1st col")))
                .as("A name that is not a valid XML name must be rejected.").isNotNull();
    }

    @Test
    void testIsValid_forDuplicateColumnNames_returnsAMessageRegardlessOfCase()
    {
        final ColumnNamesValidator validator = new ColumnNamesValidator();

        assertThat(validator.isValid(List.of("ID", "id")))
                .as("Two column names that match, in any case, must be rejected.").isNotNull();
    }

    @Test
    void testIsValid_forUniqueValidColumnNames_returnsNull()
    {
        final ColumnNamesValidator validator = new ColumnNamesValidator();

        assertThat(validator.isValid(List.of("ID", "NAME"))).as("Unique, valid XML names must be accepted.")
                .isNull();
    }

    @Test
    void testIsValid_forNoColumnNames_returnsNull()
    {
        final ColumnNamesValidator validator = new ColumnNamesValidator();

        assertThat(validator.isValid(List.of())).as("An empty column list must be accepted.").isNull();
    }
}
