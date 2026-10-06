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
package org.dbunit.eclipse.dataset.ui.actions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.eclipse.swt.graphics.Point;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link SetEmptyStringAction} against the Commands specification: it sets the selected cells to the
 * empty string, not to NULL.
 */
class SetEmptyStringActionTest extends GridActionFixture
{
    @Test
    void testSetEmptyString_ofSelectedCells_setsThemToTheEmptyString()
    {
        final FlatXmlDatasetDocument datasetDocument =
                create("<dataset><USERS ID=\"1\" NAME=\"Alice\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.rowIndexes = List.of(0);
        context.columnIndexes = List.of(1);
        context.selectedCellPositions = List.of(new Point(1, 0));
        final SetEmptyStringAction action = new SetEmptyStringAction(context);

        action.run();

        assertThat(datasetDocument.getModel().findTable("USERS").orElseThrow().getRows().get(0).getValue(1))
                .as("Set to Empty String must store the empty string, not NULL.").isEqualTo("");
    }
}
