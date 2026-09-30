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

import java.util.Optional;

import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link CellLocator}: the text range of each kind of address, and the address of each kind of text
 * offset, on datasets that are parsed for each test.
 */
class CellLocatorTest
{
    private static final String TWO_ROWS =
            "<dataset><USERS ID=\"1\" NAME=\"Bob\"/><USERS ID=\"2\" NAME=\"Alice\"/></dataset>";

    private static CellLocator locatorFor(final String text)
    {
        return locatorFor(text, FlatXmlOptions.DBUNIT_DEFAULTS);
    }

    private static CellLocator locatorFor(final String text, final FlatXmlOptions options)
    {
        final ParsedDataset parsed = ParsedDataset.of(text, options, "\n");
        return new CellLocator(parsed.model(), parsed.index(), parsed.options());
    }

    private static Optional<IRegion> region(final int offset, final int length)
    {
        return Optional.of(new Region(offset, length));
    }

    @Test
    void testLocate_whenTheCellHasAValue_returnsTheRangeOfTheAttributeValue()
    {
        final Optional<IRegion> located = locatorFor(TWO_ROWS).locate(new CellAddress("USERS", 0, 1));

        assertThat(located).as("The range must cover the raw value between the quotes.")
                .isEqualTo(region(TWO_ROWS.indexOf("Bob"), "Bob".length()));
    }

    @Test
    void testLocate_whenTheRowIsTheSecondOne_returnsTheRangeInThatRow()
    {
        final Optional<IRegion> located = locatorFor(TWO_ROWS).locate(new CellAddress("USERS", 1, 0));

        assertThat(located).as("The range must be the ID value of the second row.")
                .isEqualTo(region(TWO_ROWS.indexOf("\"2\"") + 1, 1));
    }

    @Test
    void testLocate_whenTheCellIsNull_returnsTheRangeOfTheElementName()
    {
        final String text = "<dataset><USERS ID=\"1\" NAME=\"Bob\"/><USERS ID=\"2\"/></dataset>";

        final Optional<IRegion> located = locatorFor(text).locate(new CellAddress("USERS", 1, 1));

        assertThat(located).as("A cell without an attribute is located at the name of its element.")
                .isEqualTo(region(text.lastIndexOf("<USERS") + 1, "USERS".length()));
    }

    @Test
    void testLocate_whenTheColumnIndexIsMinusOne_returnsTheRangeOfTheWholeRow()
    {
        final String row = "<USERS ID=\"1\" NAME=\"Bob\"/>";

        final Optional<IRegion> located = locatorFor(TWO_ROWS).locate(new CellAddress("USERS", 0, -1));

        assertThat(located).as("A row address must be located at its whole element.")
                .isEqualTo(region(TWO_ROWS.indexOf(row), row.length()));
    }

    @Test
    void testLocate_whenTheTableDoesNotExist_isEmpty()
    {
        final Optional<IRegion> located = locatorFor(TWO_ROWS).locate(new CellAddress("ORDERS", 0, 0));

        assertThat(located).as("An unknown table has no location.").isEmpty();
    }

    @Test
    void testLocate_whenTheRowIndexIsNegative_isEmpty()
    {
        final Optional<IRegion> located = locatorFor(TWO_ROWS).locate(new CellAddress("USERS", -1, 0));

        assertThat(located).as("A negative row index has no location.").isEmpty();
    }

    @Test
    void testLocate_whenTheRowIndexIsPastTheLastRow_isEmpty()
    {
        final Optional<IRegion> located = locatorFor(TWO_ROWS).locate(new CellAddress("USERS", 2, 0));

        assertThat(located).as("A row past the last one has no location.").isEmpty();
    }

    @Test
    void testLocate_whenTheColumnIndexIsPastTheLastColumn_isEmpty()
    {
        final Optional<IRegion> located = locatorFor(TWO_ROWS).locate(new CellAddress("USERS", 0, 2));

        assertThat(located).as("A column past the last one has no location.").isEmpty();
    }

    @Test
    void testLocate_whenARowSpellsTheColumnNameInAnotherCase_findsTheAttributeIgnoringCase()
    {
        final String text = "<dataset><USERS ID=\"1\" name=\"a\"/><USERS ID=\"2\" NAME=\"b\"/></dataset>";

        final Optional<IRegion> located = locatorFor(text).locate(new CellAddress("USERS", 1, 1));

        assertThat(located).as("The attribute NAME must be found for the column that is spelled name.")
                .isEqualTo(region(text.indexOf("\"b\"") + 1, 1));
    }

    @Test
    void testCellAt_whenTheOffsetIsInsideAnAttributeValue_returnsThatColumn()
    {
        final int offset = TWO_ROWS.indexOf("Bob") + 1;

        final Optional<CellAddress> address = locatorFor(TWO_ROWS).cellAt(offset);

        assertThat(address).as("An offset inside the NAME value belongs to the NAME cell of the first row.")
                .contains(new CellAddress("USERS", 0, 1));
    }

    @Test
    void testCellAt_whenTheOffsetIsInsideAnAttributeName_returnsThatColumn()
    {
        final int offset = TWO_ROWS.indexOf("NAME") + 1;

        final Optional<CellAddress> address = locatorFor(TWO_ROWS).cellAt(offset);

        assertThat(address).as("An offset inside an attribute name belongs to its cell.")
                .contains(new CellAddress("USERS", 0, 1));
    }

    @Test
    void testCellAt_whenTheOffsetIsInTheElementName_returnsTheFirstColumn()
    {
        final int offset = TWO_ROWS.indexOf("<USERS") + 2;

        final Optional<CellAddress> address = locatorFor(TWO_ROWS).cellAt(offset);

        assertThat(address).as("An offset outside every attribute belongs to the first column.")
                .contains(new CellAddress("USERS", 0, 0));
    }

    @Test
    void testCellAt_whenTheOffsetIsInTheSecondRow_returnsThatRow()
    {
        final int offset = TWO_ROWS.indexOf("Alice");

        final Optional<CellAddress> address = locatorFor(TWO_ROWS).cellAt(offset);

        assertThat(address).as("The row index must be the position among the table's rows.")
                .contains(new CellAddress("USERS", 1, 1));
    }

    @Test
    void testCellAt_whenTheOffsetIsAtTheEndOfARowThatTheNextRowFollowsDirectly_returnsTheNextRow()
    {
        final int nextRowOffset = TWO_ROWS.lastIndexOf("<USERS");

        final Optional<CellAddress> address = locatorFor(TWO_ROWS).cellAt(nextRowOffset);

        assertThat(address).as("The end of an element is exclusive, so its successor owns that offset.")
                .contains(new CellAddress("USERS", 1, 0));
    }

    @Test
    void testCellAt_whenTheOffsetIsInTheRootElementsTag_isEmpty()
    {
        final Optional<CellAddress> address = locatorFor(TWO_ROWS).cellAt(2);

        assertThat(address).as("The root's own tag is not in any row.").isEmpty();
    }

    @Test
    void testCellAt_whenTheOffsetIsInTheRootElementsEndTag_isEmpty()
    {
        final Optional<CellAddress> address = locatorFor(TWO_ROWS).cellAt(TWO_ROWS.length() - 2);

        assertThat(address).as("The root's end tag is not in any row.").isEmpty();
    }

    @Test
    void testCellAt_whenTheOffsetIsInAnElementWithoutAttributes_isEmpty()
    {
        final String text = "<dataset><AUDIT_LOG/><USERS ID=\"1\"/></dataset>";

        final Optional<CellAddress> address = locatorFor(text).cellAt(text.indexOf("AUDIT_LOG"));

        assertThat(address).as("An element without attributes is a marker, not a row.").isEmpty();
    }

    @Test
    void testCellAt_whenTheModelDoesNotKnowTheAttribute_returnsTheFirstColumn()
    {
        final ParsedDataset known = ParsedDataset.of("<dataset><USERS ID=\"1\"/></dataset>");
        final String text = "<dataset><USERS ID=\"1\" EXTRA=\"x\"/></dataset>";
        final ParsedDataset current = ParsedDataset.of(text);
        final CellLocator locator = new CellLocator(known.model(), current.index(), current.options());

        final Optional<CellAddress> address = locator.cellAt(text.indexOf("EXTRA") + 1);

        assertThat(address).as("An attribute without a column in the model falls back to the first column.")
                .contains(new CellAddress("USERS", 0, 0));
    }

    @Test
    void testCellAt_whenTableNamesAreCaseInsensitive_returnsTheUpperCaseTableKey()
    {
        final String text = "<dataset><users ID=\"1\"/></dataset>";

        final Optional<CellAddress> address = locatorFor(text).cellAt(text.indexOf("ID"));

        assertThat(address).as("The default options key tables by their upper-cased name.")
                .contains(new CellAddress("USERS", 0, 0));
    }

    @Test
    void testCellAt_whenTableNamesAreCaseSensitive_returnsTheTableKeyAsSpelled()
    {
        final String text = "<dataset><users ID=\"1\"/></dataset>";

        final Optional<CellAddress> address =
                locatorFor(text, new FlatXmlOptions(true, false)).cellAt(text.indexOf("ID"));

        assertThat(address).as("Case-sensitive options key tables by their name as spelled.")
                .contains(new CellAddress("users", 0, 0));
    }
}
