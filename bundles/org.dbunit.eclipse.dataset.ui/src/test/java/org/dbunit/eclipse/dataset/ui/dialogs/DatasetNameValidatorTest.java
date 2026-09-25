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

import org.junit.jupiter.api.Test;

/**
 * Tests {@link DatasetNameValidator} against the Column Commands specification's naming rules.
 */
class DatasetNameValidatorTest
{
    @Test
    void testIsValid_forANameThatIsNotAValidXmlName_returnsAMessage()
    {
        final DatasetNameValidator validator = new DatasetNameValidator(List.of("ID"));

        assertThat(validator.isValid("1BAD")).as("A name that is not a valid XML name must be rejected.")
                .isNotNull();
    }

    @Test
    void testIsValid_forANameAlreadyUsed_returnsAMessageRegardlessOfCase()
    {
        final DatasetNameValidator validator = new DatasetNameValidator(List.of("ID", "NAME"));

        assertThat(validator.isValid("name")).as("A name already used, in any case, must be rejected.")
                .isNotNull();
    }

    @Test
    void testIsValid_forANameEqualIgnoringCaseToAUsedNameYetDistinct_returnsNull()
    {
        final DatasetNameValidator validator = new DatasetNameValidator(List.of("\u0130D"));

        assertThat(validator.isValid("ID"))
                .as("A name with another key than every used name must be accepted, although it equals "
                        + "one of them ignoring case.")
                .isNull();
    }

    @Test
    void testIsValid_forANameThatSharesAKeyWithAUsedNameYetIsNotEqualIgnoringCase_returnsAMessage()
    {
        final DatasetNameValidator validator = new DatasetNameValidator(List.of("straße"));

        assertThat(validator.isValid("STRASSE"))
                .as("A name with the key of a used name must be rejected, although the two names are not "
                        + "equal ignoring case.")
                .isNotNull();
    }

    @Test
    void testIsValid_forAUniqueValidXmlName_returnsNull()
    {
        final DatasetNameValidator validator = new DatasetNameValidator(List.of("ID", "NAME"));

        assertThat(validator.isValid("EMAIL")).as("A unique, valid XML name must be accepted.").isNull();
    }

    @Test
    void testIsValid_whenTheGivenNamesChangeAfterwards_stillRejectsTheNamesItWasGiven()
    {
        final List<String> existingNames = new ArrayList<>(List.of("ID"));
        final DatasetNameValidator validator = new DatasetNameValidator(existingNames);

        existingNames.clear();

        assertThat(validator.isValid("ID"))
                .as("A later change to the caller's list must not change which names are rejected.")
                .isNotNull();
    }
}
