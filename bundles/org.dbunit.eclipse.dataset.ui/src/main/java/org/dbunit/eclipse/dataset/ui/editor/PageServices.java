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

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * What the Tables page offers the grids and their actions from the workbench around it: running an edit
 * when the page is editable and reporting the refusal of one, the status line, the clipboard, the shell for
 * dialogs, and whether the workbench theme is dark. All of it belongs to the page's control, so it may be
 * used on the UI thread only.
 */
final class PageServices
{
    private final Control control;

    private final BooleanSupplier editable;

    private final BooleanSupplier inputStateValid;

    private final Supplier<IStatusLineManager> statusLine;

    /**
     * Creates the services of a page.
     *
     * @param control The page's control, which provides the shell, the display, and the background color.
     * @param editable Tells whether the page can be edited at the moment.
     * @param inputStateValid Validates the editor input's state, which may ask the user to make a read-only
     *                        file writable; true when the input can be edited.
     * @param statusLine Returns the editor's status line.
     */
    PageServices(final Control control, final BooleanSupplier editable, final BooleanSupplier inputStateValid,
            final Supplier<IStatusLineManager> statusLine)
    {
        this.control = control;
        this.editable = editable;
        this.inputStateValid = inputStateValid;
        this.statusLine = statusLine;
    }

    boolean isDarkTheme()
    {
        return relativeLuminance(control.getBackground()) < 0.5;
    }

    boolean executeEdit(final Runnable edit)
    {
        if (!editable.getAsBoolean())
        {
            return false;
        }
        return executeWithValidInput(edit);
    }

    /**
     * Runs an edit that the page allows although the page is not editable, such as the creation of the first
     * content of a blank document, when the input of the editor can be edited. A refusal of the edit is
     * shown in the status line.
     *
     * @param edit The edit to run.
     * @return True when the edit ran and was not refused.
     */
    boolean executeWithValidInput(final Runnable edit)
    {
        if (!inputStateValid.getAsBoolean())
        {
            return false;
        }
        try
        {
            edit.run();
        }
        catch (final DatasetEditException e)
        {
            setStatusErrorMessage(e.getMessage());
            return false;
        }
        statusLine.get().setErrorMessage(null);
        return true;
    }

    boolean executeMultiCellEdit(final String title, final Runnable edit)
    {
        if (!editable.getAsBoolean() || !inputStateValid.getAsBoolean())
        {
            return false;
        }
        try
        {
            edit.run();
        }
        catch (final DatasetEditException e)
        {
            MessageDialog.openError(control.getShell(), title, e.getMessage());
            return false;
        }
        statusLine.get().setErrorMessage(null);
        return true;
    }

    Shell getShell()
    {
        return control.getShell();
    }

    void setStatusMessage(final String message)
    {
        statusLine.get().setMessage(message);
    }

    void setStatusErrorMessage(final String message)
    {
        statusLine.get().setErrorMessage(message);
        Display.getCurrent().beep();
    }

    void writeClipboardText(final String text)
    {
        final Clipboard clipboard = new Clipboard(control.getDisplay());
        try
        {
            clipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
        }
        finally
        {
            clipboard.dispose();
        }
    }

    String readClipboardText()
    {
        final Clipboard clipboard = new Clipboard(control.getDisplay());
        try
        {
            return (String) clipboard.getContents(TextTransfer.getInstance());
        }
        finally
        {
            clipboard.dispose();
        }
    }

    private static double relativeLuminance(final Color color)
    {
        return 0.2126 * linearize(color.getRed()) + 0.7152 * linearize(color.getGreen())
                + 0.0722 * linearize(color.getBlue());
    }

    private static double linearize(final int channelValue)
    {
        final double normalized = channelValue / 255.0;
        return normalized <= 0.03928 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
