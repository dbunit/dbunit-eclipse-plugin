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
import java.util.List;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;

/**
 * Records what code of the dataset editor bundle logs while a test runs, and stops recording when closed.
 */
final class LogRecorder implements AutoCloseable
{
    private final ILog log;

    private final List<IStatus> statuses = new ArrayList<>();

    private final ILogListener listener = (status, plugin) -> statuses.add(status);

    /**
     * Starts recording the log of the bundle that a class belongs to.
     *
     * @param owner A class of the bundle whose log to record.
     */
    LogRecorder(final Class<?> owner)
    {
        log = ILog.of(owner);
        log.addLogListener(listener);
    }

    List<IStatus> statuses()
    {
        return List.copyOf(statuses);
    }

    @Override
    public void close()
    {
        log.removeLogListener(listener);
    }
}
