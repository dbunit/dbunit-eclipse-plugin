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
package org.dbunit.eclipse.dataset.core.flatxml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link FlatXmlOptions}: the key that the options give a table.
 */
class FlatXmlOptionsTest
{
    @Test
    void testTableKey_whenTableNamesAreCaseInsensitive_returnsTheNameInUpperCase()
    {
        final FlatXmlOptions options = new FlatXmlOptions(false, false);

        assertThat(options.tableKey("Users")).as("dbUnit's own defaults key a table by its upper-cased name.")
                .isEqualTo("USERS");
    }

    @Test
    void testTableKey_whenTableNamesAreCaseSensitive_returnsTheNameAsItIs()
    {
        final FlatXmlOptions options = new FlatXmlOptions(true, false);

        assertThat(options.tableKey("Users")).as("A case-sensitive name is its own key.").isEqualTo("Users");
    }

    @Test
    void testTableKey_whenOnlyColumnSensingIsOn_stillUpperCasesTheName()
    {
        final FlatXmlOptions options = new FlatXmlOptions(false, true);

        assertThat(options.tableKey("Users")).as("Column sensing has nothing to do with the key of a table.")
                .isEqualTo("USERS");
    }

    @Test
    void testTableKey_forTheDefaults_isCaseInsensitive()
    {
        assertThat(FlatXmlOptions.DBUNIT_DEFAULTS.tableKey("users")).as("The defaults ignore letter case.")
                .isEqualTo("USERS");
    }
}
