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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.edit.CellEditorCreatedEvent;
import org.eclipse.nebula.widgets.nattable.layer.ILayer;
import org.eclipse.nebula.widgets.nattable.layer.event.ILayerEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * A NatTable that tells its listeners when an in-place cell editor opens or closes. NatTable hands the event
 * of a new editor to its layer listeners before it records the editor as its active one, and a dispose
 * listener that such a layer listener adds to the editor's control runs before the one in which NatTable
 * forgets the editor. A layer listener would therefore find {@link #getActiveCellEditor()} lagging behind.
 * This table reports after it has updated its own state, so that a listener finds the active editor as it
 * is.
 *
 * @since 1.0.0
 */
final class CellEditorAwareNatTable extends NatTable
{
    private final List<Runnable> cellEditorListeners = new ArrayList<>();

    private boolean disposing;

    /**
     * Creates a table for a layer stack.
     *
     * @param parent The composite to create the table in.
     * @param style The SWT style of the table.
     * @param layer The top layer of the stack that the table shows.
     * @param autoconfigure True to configure the table now; false when the caller configures it later.
     */
    CellEditorAwareNatTable(final Composite parent, final int style, final ILayer layer,
            final boolean autoconfigure)
    {
        super(parent, style, layer, autoconfigure);
        addDisposeListener(event -> disposing = true);
    }

    /**
     * Notifies a listener whenever an in-place cell editor opens, and whenever one closes while the table
     * stays; an editor that goes away with the table is not reported.
     *
     * @param listener The listener to notify; it is not told which editor changed.
     */
    void addCellEditorListener(final Runnable listener)
    {
        cellEditorListeners.add(listener);
    }

    @Override
    public void handleLayerEvent(final ILayerEvent event)
    {
        super.handleLayerEvent(event);
        if (event instanceof final CellEditorCreatedEvent created)
        {
            final Control editorControl = created.getEditor().getEditorControl();
            if (editorControl != null && !editorControl.isDisposed())
            {
                editorControl.addDisposeListener(disposed -> editorClosed());
                notifyCellEditorListeners();
            }
        }
    }

    private void editorClosed()
    {
        if (!disposing)
        {
            notifyCellEditorListeners();
        }
    }

    private void notifyCellEditorListeners()
    {
        for (final Runnable listener : cellEditorListeners)
        {
            listener.run();
        }
    }
}
