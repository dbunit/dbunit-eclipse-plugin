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

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Confirms the modal dialog that the code under test opens on a parent shell. A test cannot confirm it
 * itself, because the call that opens a modal dialog does not return until the dialog is closed. The dialog
 * must have one text field and a default button that confirms it.
 */
final class ModalDialogDriver
{
    private static final int POLL_MILLIS = 20;

    private static final int GIVE_UP_MILLIS = 10_000;

    private final Shell parent;

    private final Display display;

    private final Runnable giveUp = this::closeLeftOverDialogs;

    private boolean armed;

    private boolean confirmed;

    ModalDialogDriver(final Shell parent)
    {
        this.parent = parent;
        display = parent.getDisplay();
    }

    /**
     * Confirms the next dialog that opens on the parent shell, with the text of its text field as it opens.
     */
    void confirmNextDialog()
    {
        arm(text ->
        {
        });
    }

    /**
     * Replaces the text of the text field of the next dialog that opens on the parent shell, and confirms
     * the dialog.
     *
     * @param newText The text to enter into the dialog's text field.
     */
    void confirmNextDialogWithText(final String newText)
    {
        arm(text -> text.setText(newText));
    }

    /**
     * Tells whether a dialog opened and was confirmed.
     *
     * @return True once the dialog's default button was pressed.
     */
    boolean hasConfirmed()
    {
        return confirmed;
    }

    /**
     * Stops waiting for a dialog; a test calls it when it ends, so that it cannot confirm a dialog of a
     * later test.
     */
    void disarm()
    {
        armed = false;
        display.timerExec(-1, giveUp);
    }

    private void arm(final Consumer<Text> edit)
    {
        armed = true;
        display.timerExec(GIVE_UP_MILLIS, giveUp);
        display.asyncExec(() -> poll(edit));
    }

    private void poll(final Consumer<Text> edit)
    {
        if (!armed || parent.isDisposed())
        {
            return;
        }
        final Shell[] dialogs = parent.getShells();
        final Optional<Shell> dialog = Arrays.stream(dialogs).filter(ModalDialogDriver::isConfirmable)
                .findFirst();
        if (dialog.isEmpty())
        {
            display.timerExec(POLL_MILLIS, () -> poll(edit));
            return;
        }
        confirm(dialog.get(), edit);
    }

    private void confirm(final Shell dialog, final Consumer<Text> edit)
    {
        armed = false;
        final Optional<Text> text = findText(dialog);
        edit.accept(text.orElseThrow(() -> new AssertionError("The dialog has no text field.")));
        confirmed = true;
        dialog.getDefaultButton().notifyListeners(SWT.Selection, new Event());
    }

    /**
     * Closes every dialog still open when waiting for one took too long, or when confirming one did not
     * close it, so that the test fails instead of blocking the build in the dialog's event loop.
     */
    private void closeLeftOverDialogs()
    {
        armed = false;
        if (parent.isDisposed())
        {
            return;
        }
        for (final Shell dialog : parent.getShells())
        {
            dialog.dispose();
        }
    }

    private static boolean isConfirmable(final Shell dialog)
    {
        return dialog.isVisible() && dialog.getDefaultButton() != null;
    }

    private static Optional<Text> findText(final Control control)
    {
        if (control instanceof Text)
        {
            return Optional.of((Text) control);
        }
        if (control instanceof Composite)
        {
            for (final Control child : ((Composite) control).getChildren())
            {
                final Optional<Text> found = findText(child);
                if (found.isPresent())
                {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
