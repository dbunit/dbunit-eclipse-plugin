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

import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * A banner shown at the top of {@link TablesPage} for a blocking problem or a read-only dataset, hidden
 * otherwise.
 *
 * @since 1.0.0
 */
final class ErrorBanner
{
    private final FlatXmlDatasetEditor editor;

    private final Composite control;

    private final Label image;

    private final Label message;

    private final Link showInSourceLink;

    private DatasetProblem shownProblem;

    ErrorBanner(final Composite parent, final FlatXmlDatasetEditor editor)
    {
        this.editor = editor;
        control = new Composite(parent, SWT.NONE);
        control.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        final GridLayout layout = new GridLayout(3, false);
        control.setLayout(layout);

        image = new Label(control, SWT.NONE);
        message = new Label(control, SWT.NONE);
        message.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        showInSourceLink = new Link(control, SWT.NONE);
        showInSourceLink.setText(Messages.ErrorBanner_showInSource);
        showInSourceLink.addSelectionListener(SelectionListener.widgetSelectedAdapter(event ->
        {
            if (shownProblem != null)
            {
                editor.showOnSourcePage(shownProblem.offset(), shownProblem.length());
            }
        }));

        hide();
    }

    Control getControl()
    {
        return control;
    }

    void showBlocking(final DatasetProblem problem)
    {
        shownProblem = problem;
        image.setImage(sharedImage(ISharedImages.IMG_OBJS_ERROR_TSK));
        message.setText(problem.message());
        showInSourceLink.setVisible(true);
        reveal();
    }

    void showReadOnly()
    {
        shownProblem = null;
        image.setImage(sharedImage(ISharedImages.IMG_OBJS_WARN_TSK));
        message.setText(Messages.ErrorBanner_readOnly);
        showInSourceLink.setVisible(false);
        reveal();
    }

    void hide()
    {
        shownProblem = null;
        if (!isExcluded())
        {
            setExcluded(true);
            control.getParent().layout();
        }
    }

    private void reveal()
    {
        control.layout();
        if (isExcluded())
        {
            setExcluded(false);
            control.getParent().layout();
        }
    }

    private boolean isExcluded()
    {
        return ((GridData) control.getLayoutData()).exclude;
    }

    private void setExcluded(final boolean excluded)
    {
        control.setVisible(!excluded);
        ((GridData) control.getLayoutData()).exclude = excluded;
    }

    private static Image sharedImage(final String key)
    {
        return PlatformUI.getWorkbench().getSharedImages().getImage(key);
    }
}
