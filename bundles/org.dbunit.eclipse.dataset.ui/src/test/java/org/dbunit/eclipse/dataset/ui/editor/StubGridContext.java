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

import java.util.List;

import org.dbunit.eclipse.dataset.core.edit.DatasetDocument;
import org.dbunit.eclipse.dataset.ui.grid.DatasetGridContext;
import org.dbunit.eclipse.dataset.ui.grid.GridSelection;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * A {@link DatasetGridContext} for tests that need grids but not a page: it is editable, runs every edit
 * directly, and does nothing else.
 */
final class StubGridContext implements DatasetGridContext
{
    private final DatasetDocument datasetDocument;

    StubGridContext(final DatasetDocument datasetDocument)
    {
        this.datasetDocument = datasetDocument;
    }

    @Override
    public DatasetDocument getDatasetDocument()
    {
        return datasetDocument;
    }

    @Override
    public boolean isEditable()
    {
        return true;
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
        edit.run();
        return true;
    }

    @Override
    public boolean executeMultiCellEdit(final String title, final Runnable edit)
    {
        edit.run();
        return true;
    }

    @Override
    public void fillContextMenu(final IMenuManager menu, final String region)
    {
        // The stub has no actions.
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
        // The stub has no selection.
    }

    @Override
    public Shell getShell()
    {
        return null;
    }

    @Override
    public void expectRename(final String oldKey, final String newKey)
    {
        // The stub has no tabs.
    }

    @Override
    public void cancelExpectedRename()
    {
        // The stub has no tabs.
    }

    @Override
    public void cancelExpectedNewTableSelected()
    {
        // The stub has no tabs.
    }

    @Override
    public void expectNewTableSelected(final String tableKey)
    {
        // The stub has no tabs.
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
        // The stub has no selection.
    }

    @Override
    public void editCellInDialog()
    {
        // The stub has no dialog.
    }

    @Override
    public void showInSource()
    {
        // The stub has no source page.
    }

    @Override
    public void setStatusMessage(final String message)
    {
        // The stub has no status line.
    }

    @Override
    public void setStatusErrorMessage(final String message)
    {
        // The stub has no status line.
    }

    @Override
    public void writeClipboardText(final String text)
    {
        // The stub has no clipboard.
    }

    @Override
    public String readClipboardText()
    {
        return null;
    }
}
