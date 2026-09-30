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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PageServices}: when an edit runs and how a refused one is reported, what reaches the status
 * line, the shell that dialogs use, and the detection of a dark theme. The clipboard is not tested, because
 * the operating system's clipboard is not available to every test environment.
 */
class PageServicesTest
{
    private static final class RecordingStatusLine extends StatusLineManager
    {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void setMessage(final String message)
        {
            calls.add("message: " + message);
        }

        @Override
        public void setErrorMessage(final String message)
        {
            calls.add("error: " + message);
        }
    }

    private Shell shell;

    private RecordingStatusLine statusLine;

    private AtomicBoolean editable;

    private AtomicBoolean inputStateValid;

    private AtomicInteger inputStateChecks;

    private PageServices services;

    @BeforeEach
    void createServices()
    {
        shell = new Shell(Display.getDefault());
        statusLine = new RecordingStatusLine();
        editable = new AtomicBoolean(true);
        inputStateValid = new AtomicBoolean(true);
        inputStateChecks = new AtomicInteger();
        services = new PageServices(shell, editable::get, () ->
        {
            inputStateChecks.incrementAndGet();
            return inputStateValid.get();
        }, () -> statusLine);
    }

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testExecuteEdit_whenEditableAndTheInputIsValid_runsTheEditClearsTheErrorAndReturnsTrue()
    {
        final AtomicBoolean ran = new AtomicBoolean();

        final boolean executed = services.executeEdit(() -> ran.set(true));

        assertThat(executed).as("An edit that runs is reported as executed.").isTrue();
        assertThat(ran.get()).as("The edit must run.").isTrue();
        assertThat(statusLine.calls).as("A successful edit must clear the error message.")
                .containsExactly("error: null");
    }

    @Test
    void testExecuteEdit_whenNotEditable_doesNotRunTheEditOrAskAboutTheInput()
    {
        editable.set(false);
        final AtomicBoolean ran = new AtomicBoolean();

        final boolean executed = services.executeEdit(() -> ran.set(true));

        assertThat(executed).as("An edit on a page that is not editable is refused.").isFalse();
        assertThat(ran.get()).as("The edit must not run.").isFalse();
        assertThat(inputStateChecks.get()).as("The input's state is only asked for when the page is editable.")
                .isZero();
        assertThat(statusLine.calls).as("A refusal without a message leaves the status line alone.").isEmpty();
    }

    @Test
    void testExecuteEdit_whenTheInputStateIsNotValid_doesNotRunTheEdit()
    {
        inputStateValid.set(false);
        final AtomicBoolean ran = new AtomicBoolean();

        final boolean executed = services.executeEdit(() -> ran.set(true));

        assertThat(executed).as("An edit on an input that cannot be edited is refused.").isFalse();
        assertThat(ran.get()).as("The edit must not run.").isFalse();
    }

    @Test
    void testExecuteEdit_whenTheEditIsRefused_showsItsMessageInTheStatusLineAndReturnsFalse()
    {
        final boolean executed = services.executeEdit(() ->
        {
            throw new DatasetEditException("The row does not exist.");
        });

        assertThat(executed).as("A refused edit is reported as not executed.").isFalse();
        assertThat(statusLine.calls).as("The refusal must reach the status line, and nothing must clear it.")
                .containsExactly("error: The row does not exist.");
    }

    @Test
    void testExecuteEdit_whenTheEditFailsForAnotherReason_letsTheExceptionThrough()
    {
        assertThatThrownBy(() -> services.executeEdit(() ->
        {
            throw new IllegalStateException("broken");
        })).as("Only a refusal is reported; a defect must not be hidden.")
                .isInstanceOf(IllegalStateException.class).hasMessage("broken");
    }

    @Test
    void testExecuteMultiCellEdit_whenEditableAndTheInputIsValid_runsTheEditClearsTheErrorAndReturnsTrue()
    {
        final AtomicBoolean ran = new AtomicBoolean();

        final boolean executed = services.executeMultiCellEdit("Paste", () -> ran.set(true));

        assertThat(executed).as("An edit that runs is reported as executed.").isTrue();
        assertThat(ran.get()).as("The edit must run.").isTrue();
        assertThat(statusLine.calls).as("A successful edit must clear the error message.")
                .containsExactly("error: null");
    }

    @Test
    void testExecuteMultiCellEdit_whenNotEditable_doesNotRunTheEdit()
    {
        editable.set(false);
        final AtomicBoolean ran = new AtomicBoolean();

        final boolean executed = services.executeMultiCellEdit("Paste", () -> ran.set(true));

        assertThat(executed).as("An edit on a page that is not editable is refused.").isFalse();
        assertThat(ran.get()).as("The edit must not run.").isFalse();
    }

    @Test
    void testExecuteMultiCellEdit_whenTheInputStateIsNotValid_doesNotRunTheEdit()
    {
        inputStateValid.set(false);
        final AtomicBoolean ran = new AtomicBoolean();

        final boolean executed = services.executeMultiCellEdit("Paste", () -> ran.set(true));

        assertThat(executed).as("An edit on an input that cannot be edited is refused.").isFalse();
        assertThat(ran.get()).as("The edit must not run.").isFalse();
    }

    @Test
    void testGetShell_whenCalled_returnsTheShellOfTheControl()
    {
        assertThat(services.getShell()).as("Dialogs must open on the page's own shell.").isSameAs(shell);
    }

    @Test
    void testSetStatusMessage_whenCalled_showsTheMessageInTheStatusLine()
    {
        services.setStatusMessage("3 rows selected");

        assertThat(statusLine.calls).as("The message must reach the status line.")
                .containsExactly("message: 3 rows selected");
    }

    @Test
    void testSetStatusErrorMessage_whenCalled_showsTheErrorInTheStatusLine()
    {
        services.setStatusErrorMessage("Not allowed.");

        assertThat(statusLine.calls).as("The error must reach the status line.")
                .containsExactly("error: Not allowed.");
    }

    @Test
    void testIsDarkTheme_whenTheBackgroundIsBlack_isTrue()
    {
        shell.setBackground(shell.getDisplay().getSystemColor(SWT.COLOR_BLACK));

        assertThat(services.isDarkTheme()).as("A black background is a dark theme.").isTrue();
    }

    @Test
    void testIsDarkTheme_whenTheBackgroundIsWhite_isFalse()
    {
        shell.setBackground(shell.getDisplay().getSystemColor(SWT.COLOR_WHITE));

        assertThat(services.isDarkTheme()).as("A white background is a light theme.").isFalse();
    }
}
