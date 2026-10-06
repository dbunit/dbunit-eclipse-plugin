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

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ShowInSourceAction} against the Commands specification: it selects and reveals the anchor
 * cell on the Source page, also on a read-only page.
 */
class ShowInSourceActionTest extends GridActionFixture
{
    @Test
    void testShowInSource_run_selectsAndRevealsTheAnchorCellOnTheSourcePage()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;
        final ShowInSourceAction action = new ShowInSourceAction(context);

        action.run();

        assertThat(context.showInSourceCalled)
                .as("Show in Source must select and reveal the anchor cell's range.").isTrue();
    }

    @Test
    void testUpdate_forShowInSource_staysEnabledOnAReadOnlyPage()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;
        context.editable = false;
        final ShowInSourceAction action = new ShowInSourceAction(context);

        action.update(context.getSelection());

        assertThat(action.isEnabled()).as("A read-only page must still show a cell in the source.").isTrue();
    }
}
