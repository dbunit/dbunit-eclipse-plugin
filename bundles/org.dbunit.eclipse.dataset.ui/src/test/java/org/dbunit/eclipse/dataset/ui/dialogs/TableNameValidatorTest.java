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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link TableNameValidator} against the document's table-naming rules.
 */
class TableNameValidatorTest
{
    private static final Function<String, String> CASE_INSENSITIVE = name -> name.toUpperCase(Locale.ENGLISH);

    private static final Function<String, String> CASE_SENSITIVE = Function.identity();

    @Test
    void testIsValid_forANameThatIsNotAValidXmlName_returnsAMessage()
    {
        final TableNameValidator validator = new TableNameValidator(CASE_INSENSITIVE, List.of("USERS"));

        assertThat(validator.isValid("1BAD")).as("A name that is not a valid XML name must be rejected.")
                .isNotNull();
    }

    @Test
    void testIsValid_forAUniqueValidXmlName_returnsNull()
    {
        final TableNameValidator validator = new TableNameValidator(CASE_INSENSITIVE, List.of("USERS"));

        assertThat(validator.isValid("ORDERS")).as("A unique, valid XML name must be accepted.").isNull();
    }

    @Test
    void testIsValid_whenCaseInsensitive_rejectsANameUsedByAnotherTableRegardlessOfCase()
    {
        final TableNameValidator validator = new TableNameValidator(CASE_INSENSITIVE, List.of("USERS"));

        assertThat(validator.isValid("users"))
                .as("A case-insensitive document must reject a name already used, in any case.")
                .isNotNull();
    }

    @Test
    void testIsValid_whenCaseSensitive_acceptsANameUsedByAnotherTableOnlyInDifferentCase()
    {
        final TableNameValidator validator = new TableNameValidator(CASE_SENSITIVE, List.of("USERS"));

        assertThat(validator.isValid("users"))
                .as("A case-sensitive document must accept a name that differs from another table only "
                        + "in case.")
                .isNull();
    }

    @Test
    void testIsValid_whenCaseSensitive_rejectsANameUsedByAnotherTableInTheSameCase()
    {
        final TableNameValidator validator = new TableNameValidator(CASE_SENSITIVE, List.of("USERS"));

        assertThat(validator.isValid("USERS"))
                .as("A case-sensitive document must still reject a name already used in the same case.")
                .isNotNull();
    }

    @Test
    void testIsValid_whenCaseInsensitive_rejectsAnyCasingOfTheReservedRootName()
    {
        final TableNameValidator validator = new TableNameValidator(CASE_INSENSITIVE, List.of());

        assertThat(validator.isValid("Dataset"))
                .as("A case-insensitive document must reject every casing of the reserved root name.")
                .isNotNull();
    }

    @Test
    void testIsValid_whenCaseSensitive_rejectsTheReservedRootNameExactly()
    {
        final TableNameValidator validator = new TableNameValidator(CASE_SENSITIVE, List.of());

        assertThat(validator.isValid("dataset"))
                .as("A case-sensitive document must reject the reserved root name spelled exactly.")
                .isNotNull();
    }

    @Test
    void testIsValid_whenCaseSensitive_acceptsADifferentCasingOfTheReservedRootName()
    {
        final TableNameValidator validator = new TableNameValidator(CASE_SENSITIVE, List.of());

        assertThat(validator.isValid("Dataset"))
                .as("A case-sensitive document must accept a casing of the reserved root name that does "
                        + "not match it exactly.")
                .isNull();
    }

    @Test
    void testIsValid_whenTheGivenNamesChangeAfterwards_stillRejectsTheNamesItWasGiven()
    {
        final List<String> existingNames = new ArrayList<>(List.of("USERS"));
        final TableNameValidator validator = new TableNameValidator(CASE_INSENSITIVE, existingNames);

        existingNames.clear();

        assertThat(validator.isValid("USERS"))
                .as("A later change to the caller's list must not change which names are rejected.")
                .isNotNull();
    }
}
