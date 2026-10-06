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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.nebula.widgets.nattable.data.validate.ValidationFailedException;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link XmlCharacterValidator} against the XML 1.0 Char production.
 */
class XmlCharacterValidatorTest
{
    @Test
    void testValidate_whenTheValueContainsANonXmlCharacter_throwsNamingTheCharacter()
    {
        final XmlCharacterValidator validator = new XmlCharacterValidator();

        assertThatThrownBy(() -> validator.validate(0, 0, "a\u0001b"))
                .as("A code point outside the XML 1.0 Char production must be rejected.")
                .isInstanceOf(ValidationFailedException.class)
                .hasMessage("The character U+0001 is not allowed in an XML 1.0 document.");
    }
}
