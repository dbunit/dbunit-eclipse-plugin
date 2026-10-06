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
 * Tests {@link EditCellInDialogAction} against the Commands specification: it opens the anchor cell in a
 * dialog editor.
 */
class EditCellInDialogActionTest extends GridActionFixture
{
    @Test
    void testEditCellInDialog_run_opensTheAnchorCellInADialogEditor()
    {
        final FlatXmlDatasetDocument datasetDocument = create("<dataset><USERS ID=\"1\"/></dataset>");
        final TestContext context = new TestContext(datasetDocument, "USERS");
        context.anchorColumnIndex = 0;
        context.anchorRowIndex = 0;
        final EditCellInDialogAction action = new EditCellInDialogAction(context);

        action.run();

        assertThat(context.editCellInDialogCalled)
                .as("Edit Cell in Dialog must open the anchor cell's editor.").isTrue();
    }
}
