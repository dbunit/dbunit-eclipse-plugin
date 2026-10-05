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
 * Tests {@link FlatXmlDoctype}.
 */
class FlatXmlDoctypeTest
{
    private static FlatXmlDoctype doctype(final String publicId, final String systemId,
            final String internalSubset)
    {
        return new FlatXmlDoctype("dataset", publicId, systemId, internalSubset, 30, 0, 60);
    }

    @Test
    void testMayDeclareEntities_whenTheDoctypeHasNoIdentifierAndNoInternalSubset_isFalse()
    {
        final FlatXmlDoctype doctype = doctype(null, null, null);

        assertThat(doctype.mayDeclareEntities()).as("Nothing in the declaration can declare an entity.")
                .isFalse();
    }

    @Test
    void testMayDeclareEntities_whenTheDoctypeHasASystemIdentifier_isTrue()
    {
        final FlatXmlDoctype doctype = doctype(null, "dataset.dtd", null);

        assertThat(doctype.mayDeclareEntities()).as("The external DTD may declare an entity.").isTrue();
    }

    @Test
    void testMayDeclareEntities_whenTheDoctypeHasAPublicIdentifier_isTrue()
    {
        final FlatXmlDoctype doctype = doctype("-//DbUnit//DTD Dataset//EN", "dataset.dtd", null);

        assertThat(doctype.mayDeclareEntities()).as("The external DTD may declare an entity.").isTrue();
    }

    @Test
    void testMayDeclareEntities_whenTheInternalSubsetDeclaresAnEntity_isTrue()
    {
        final FlatXmlDoctype doctype =
                doctype(null, null, "<!ELEMENT dataset ANY>\n<!ENTITY nbsp \"&#160;\">\n");

        assertThat(doctype.mayDeclareEntities()).as("The internal subset declares an entity.").isTrue();
    }

    @Test
    void testMayDeclareEntities_whenTheInternalSubsetDeclaresAParameterEntity_isTrue()
    {
        final FlatXmlDoctype doctype = doctype(null, null, "<!ENTITY % declarations \"<!ENTITY a 'b'>\">\n");

        assertThat(doctype.mayDeclareEntities())
                .as("A parameter entity may bring in declarations, so entities may be declared.").isTrue();
    }

    @Test
    void testMayDeclareEntities_whenTheInternalSubsetDeclaresOnlyElementsAndAttributes_isFalse()
    {
        final FlatXmlDoctype doctype = doctype(null, null,
                "<!ELEMENT dataset (USERS*)>\n<!ELEMENT USERS EMPTY>\n<!ATTLIST USERS ID CDATA #IMPLIED>\n");

        assertThat(doctype.mayDeclareEntities())
                .as("Element and attribute list declarations declare no entity.").isFalse();
    }
}
