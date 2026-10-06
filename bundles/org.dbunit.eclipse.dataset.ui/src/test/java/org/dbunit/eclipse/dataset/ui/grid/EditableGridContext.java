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

import java.util.List;

import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.core.edit.DatasetEditException;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * A {@link DatasetGridContext} for the tests of the grid: it is editable unless it is told otherwise,
 * runs each edit directly, and keeps the last error message and the last status message that it was
 * given.
 */
final class EditableGridContext implements DatasetGridContext
{
    private final DatasetDocument datasetDocument;

    private final boolean editable;

    String lastErrorMessage;

    String lastStatusMessage;

    EditableGridContext(final DatasetDocument datasetDocument)
    {
        this(datasetDocument, true);
    }

    EditableGridContext(final DatasetDocument datasetDocument, final boolean editable)
    {
        this.datasetDocument = datasetDocument;
        this.editable = editable;
    }

    @Override
    public DatasetDocument getDatasetDocument()
    {
        return datasetDocument;
    }

    @Override
    public boolean isEditable()
    {
        return editable;
    }

    @Override
    public String getNullDisplayText()
    {
        return "(null)";
    }

    @Override
    public boolean isDarkTheme()
    {
        return false;
    }

    @Override
    public boolean executeEdit(final Runnable edit)
    {
        if (!editable)
        {
            return false;
        }
        try
        {
            edit.run();
            lastErrorMessage = null;
            return true;
        }
        catch (final DatasetEditException e)
        {
            lastErrorMessage = e.getMessage();
            return false;
        }
    }

    @Override
    public boolean executeMultiCellEdit(final String title, final Runnable edit)
    {
        return executeEdit(edit);
    }

    @Override
    public void fillContextMenu(final IMenuManager menu, final String region)
    {
    }

    @Override
    public boolean hasActiveCellEditor()
    {
        return false;
    }

    @Override
    public GridSelection getSelection()
    {
        return GridSelection.NONE;
    }

    @Override
    public void selectRegion(final int firstColumnIndex, final int firstRowIndex, final int columnCount,
            final int rowCount)
    {
    }

    @Override
    public Shell getShell()
    {
        return null;
    }

    @Override
    public Text getActiveCellEditorText()
    {
        return null;
    }

    @Override
    public List<Point> getSelectedCellPositions()
    {
        return List.of();
    }

    @Override
    public void selectAll()
    {
    }

    @Override
    public void editCellInDialog()
    {
    }

    @Override
    public void showInSource()
    {
    }

    @Override
    public void setStatusMessage(final String message)
    {
        lastStatusMessage = message;
    }

    @Override
    public void setStatusErrorMessage(final String message)
    {
        lastErrorMessage = message;
    }

    @Override
    public void writeClipboardText(final String text)
    {
    }

    @Override
    public String readClipboardText()
    {
        return null;
    }
}
