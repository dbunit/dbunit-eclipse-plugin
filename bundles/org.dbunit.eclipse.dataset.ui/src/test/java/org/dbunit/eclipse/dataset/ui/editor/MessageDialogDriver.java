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
package org.dbunit.eclipse.dataset.ui.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

/**
 * Presses a button of the next dialog that the code under test opens on the display of the workbench,
 * whichever shell it opens on, and closes every other dialog that opens. A test cannot do it itself, because
 * the call that opens a modal dialog does not return until the dialog is closed. The buttons are identified
 * by their position, from 0, which JFace gives them as their identifier, so a test does not depend on the
 * language of their labels.
 * <p>
 * A dialog that the test did not expect is closed at once, the way the close box of its window closes it,
 * which cancels it, so that the test fails on it instead of blocking the build in the dialog's event loop.
 * Closing it any other way would answer it, for example by choosing to save, and the answer could open
 * another dialog. The driver watches until it is closed, so close it when the test ends.
 */
final class MessageDialogDriver implements AutoCloseable
{
    /**
     * The position of Save in the prompt that a closing editor with unsaved changes shows.
     */
    static final int SAVE_BUTTON_OF_CLOSE_PROMPT = 0;

    /**
     * The position of Close in the dialog that an editor shows when its file was deleted.
     */
    static final int CLOSE_BUTTON_OF_DELETED_FILE_DIALOG = 1;

    /**
     * The position of Replace in the dialog that an editor shows when its file changed outside the workbench.
     */
    static final int REPLACE_BUTTON_OF_CHANGED_FILE_DIALOG = 0;

    /**
     * The position of Ignore in the dialog that an editor shows when its file changed outside the workbench.
     */
    static final int IGNORE_BUTTON_OF_CHANGED_FILE_DIALOG = 1;

    /**
     * The position of Change in the dialog that a cell editor shows when its value fails validation, which
     * keeps the cell editor open.
     */
    static final int CHANGE_BUTTON_OF_VALIDATION_DIALOG = 0;

    /**
     * The position of Discard in the dialog that a cell editor shows when its value fails validation, which
     * closes the cell editor without writing the value.
     */
    static final int DISCARD_BUTTON_OF_VALIDATION_DIALOG = 1;

    private static final int POLL_MILLIS = 20;

    private final Display display;

    private final Runnable poller = this::poll;

    private final List<String> unexpectedDialogTitles = new ArrayList<>();

    private final Set<Shell> closedDialogs = new HashSet<>();

    private final Set<Shell> pressedDialogs = new HashSet<>();

    private Predicate<Shell> expectedDialog = dialog -> false;

    private boolean watching;

    private boolean handled;

    private boolean everyDialogExpected;

    MessageDialogDriver()
    {
        display = PlatformUI.getWorkbench().getDisplay();
    }

    /**
     * Presses a button of the next dialog that opens. Any dialog after that one is not expected.
     *
     * @param buttonIndex The position of the button among the dialog's buttons, from 0.
     */
    void pressButtonOfNextDialog(final int buttonIndex)
    {
        watch(dialog -> pressButton(dialog, buttonIndex));
    }

    /**
     * Presses a button of every dialog that opens, for code that shows the same dialog more than once.
     *
     * @param buttonIndex The position of the button among the dialog's buttons, from 0.
     */
    void pressButtonOfEveryDialog(final int buttonIndex)
    {
        watch(dialog -> pressButton(dialog, buttonIndex));
        everyDialogExpected = true;
    }

    /**
     * Expects that no dialog opens, so that every dialog that does is closed and reported.
     */
    void expectNoDialog()
    {
        watch(dialog -> false);
    }

    /**
     * Tells whether the expected dialog opened and its button was pressed.
     *
     * @return True once the button was pressed.
     */
    boolean hasHandledDialog()
    {
        return handled;
    }

    /**
     * Lists the dialogs that opened without being expected, and were closed.
     *
     * @return The titles of the dialogs, in the order they opened.
     */
    List<String> unexpectedDialogTitles()
    {
        return List.copyOf(unexpectedDialogTitles);
    }

    /**
     * Stops watching, so that the driver cannot touch a dialog of a later test.
     */
    @Override
    public void close()
    {
        watching = false;
        display.timerExec(-1, poller);
    }

    private void watch(final Predicate<Shell> expected)
    {
        expectedDialog = expected;
        handled = false;
        everyDialogExpected = false;
        unexpectedDialogTitles.clear();
        closedDialogs.clear();
        pressedDialogs.clear();
        watching = true;
        display.asyncExec(poller);
    }

    private void poll()
    {
        if (!watching)
        {
            return;
        }
        final Shell[] shells = display.getShells();
        for (final Shell shell : shells)
        {
            if (shell.isVisible() && shell.getData() instanceof Dialog)
            {
                handle(shell);
            }
        }
        display.timerExec(POLL_MILLIS, poller);
    }

    private void handle(final Shell dialog)
    {
        final boolean mayBeExpected = everyDialogExpected ? !pressedDialogs.contains(dialog) : !handled;
        if (mayBeExpected && expectedDialog.test(dialog))
        {
            handled = true;
            pressedDialogs.add(dialog);
            return;
        }
        if (closedDialogs.add(dialog))
        {
            unexpectedDialogTitles.add(dialog.getText());
            dialog.close();
        }
    }

    private static boolean pressButton(final Shell dialog, final int buttonIndex)
    {
        final Optional<Button> found = findButton(dialog, buttonIndex);
        if (found.isEmpty())
        {
            return false;
        }
        final Button button = found.get();
        button.notifyListeners(SWT.Selection, new Event());
        return true;
    }

    private static Optional<Button> findButton(final Control control, final int buttonIndex)
    {
        if (control instanceof final Button button && Integer.valueOf(buttonIndex).equals(button.getData()))
        {
            return Optional.of(button);
        }
        if (control instanceof final Composite composite)
        {
            final Control[] children = composite.getChildren();
            for (final Control child : children)
            {
                final Optional<Button> found = findButton(child, buttonIndex);
                if (found.isPresent())
                {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
