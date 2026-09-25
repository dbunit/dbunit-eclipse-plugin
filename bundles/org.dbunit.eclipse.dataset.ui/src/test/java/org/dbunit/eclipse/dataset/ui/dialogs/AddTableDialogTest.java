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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.Test;

/**
 * Tests the column names {@link AddTableDialog} returns after the user confirms it.
 */
class AddTableDialogTest
{
    @Test
    void testGetColumnNames_whenTheEnteredListHasBlankPartsAndATrailingComma_skipsThem()
    {
        final Shell parent = new Shell(Display.getDefault());
        try
        {
            final ConfirmableDialog dialog = new ConfirmableDialog(parent);

            dialog.enterAndConfirm("USERS", " ID , ,NAME,");

            assertThat(dialog.getColumnNames()).as("Blank parts of the entered list must be skipped.")
                    .isEqualTo(List.of("ID", "NAME"));
        }
        finally
        {
            parent.dispose();
        }
    }

    @Test
    void testGetColumnNames_whenTheReturnedListIsModified_throws()
    {
        final Shell parent = new Shell(Display.getDefault());
        try
        {
            final ConfirmableDialog dialog = new ConfirmableDialog(parent);
            dialog.enterAndConfirm("USERS", "ID,NAME");

            assertThatThrownBy(() -> dialog.getColumnNames().add("EXTRA"))
                    .as("The returned column names must not let a caller change the dialog.")
                    .isInstanceOf(UnsupportedOperationException.class);
        }
        finally
        {
            parent.dispose();
        }
    }

    /**
     * An {@link AddTableDialog} that a test can fill in and confirm without showing it.
     */
    private static final class ConfirmableDialog extends AddTableDialog
    {
        ConfirmableDialog(final Shell parent)
        {
            super(parent, name -> null);
        }

        void enterAndConfirm(final String tableName, final String columnNames)
        {
            create();
            final Text[] texts = textFields((Composite) getDialogArea());
            texts[0].setText(tableName);
            texts[1].setText(columnNames);
            okPressed();
        }

        private static Text[] textFields(final Composite area)
        {
            return Arrays.stream(area.getChildren()).filter(Text.class::isInstance).map(Text.class::cast)
                    .toArray(Text[]::new);
        }
    }
}
