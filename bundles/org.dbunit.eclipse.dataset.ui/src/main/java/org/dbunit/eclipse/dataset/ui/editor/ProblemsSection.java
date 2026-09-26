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
import java.util.function.Consumer;

import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnLayoutData;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * A section shown below {@link TablesPage}'s grid, listing the dataset model's problems in a table at most
 * six rows high, and hidden when there are none.
 *
 * @since 1.0.0
 */
final class ProblemsSection
{
    private static final int MAX_VISIBLE_ROWS = 6;

    private final Composite control;

    private final Label header;

    private final GridData tableData;

    private final TableViewer viewer;

    ProblemsSection(final Composite parent, final Consumer<DatasetProblem> onDoubleClick)
    {
        control = new Composite(parent, SWT.NONE);
        control.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        final GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        control.setLayout(layout);

        header = new Label(control, SWT.NONE);
        header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        final Composite tableComposite = new Composite(control, SWT.NONE);
        tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableComposite.setLayoutData(tableData);
        final TableColumnLayout columnLayout = new TableColumnLayout();
        tableComposite.setLayout(columnLayout);
        viewer = new TableViewer(tableComposite, SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.BORDER);

        addColumn(columnLayout, new ColumnPixelData(24), new ColumnLabelProvider()
        {
            @Override
            public String getText(final Object element)
            {
                return "";
            }

            @Override
            public Image getImage(final Object element)
            {
                return severityImage(((ProblemRow) element).problem().severity());
            }
        });
        addColumn(columnLayout, new ColumnPixelData(120), new ColumnLabelProvider()
        {
            @Override
            public String getText(final Object element)
            {
                return ((ProblemRow) element).tableName();
            }
        });
        addColumn(columnLayout, new ColumnWeightData(100), new ColumnLabelProvider()
        {
            @Override
            public String getText(final Object element)
            {
                return ((ProblemRow) element).problem().message();
            }
        });

        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.addDoubleClickListener(event ->
        {
            final Object selected =
                    ((IStructuredSelection) event.getSelection()).getFirstElement();
            if (selected != null)
            {
                onDoubleClick.accept(((ProblemRow) selected).problem());
            }
        });

        hide();
    }

    Control getControl()
    {
        return control;
    }

    String getHeaderText()
    {
        return header.getText();
    }

    void update(final DatasetModel model)
    {
        final List<ProblemRow> problems = problemRows(model);
        if (problems.isEmpty())
        {
            hide();
            return;
        }
        header.setText(NLS.bind(Messages.ProblemsSection_header, problems.size()));
        viewer.setInput(problems.toArray());
        final Table table = viewer.getTable();
        final int visibleRows = Math.min(problems.size(), MAX_VISIBLE_ROWS);
        tableData.heightHint = table.computeTrim(0, 0, 0, table.getItemHeight() * visibleRows).height;
        reveal();
        // The trim can include room for a horizontal scroll bar that does not show.
        final int surplus = table.getClientArea().height - table.getItemHeight() * visibleRows;
        if (surplus > 0)
        {
            tableData.heightHint -= surplus;
            reveal();
        }
    }

    private static List<ProblemRow> problemRows(final DatasetModel model)
    {
        final List<ProblemRow> rows = new ArrayList<>();
        for (final DatasetProblem problem : model.getProblems())
        {
            rows.add(new ProblemRow(problem, tableName(model, problem.tableKey())));
        }
        return rows;
    }

    private static String tableName(final DatasetModel model, final String tableKey)
    {
        if (tableKey == null)
        {
            return "";
        }
        return model.findTable(tableKey).map(DatasetTable::getName).orElse(tableKey);
    }

    Table getTable()
    {
        return viewer.getTable();
    }

    private void addColumn(final TableColumnLayout columnLayout, final ColumnLayoutData width,
            final ColumnLabelProvider labelProvider)
    {
        final TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        columnLayout.setColumnData(column.getColumn(), width);
        column.setLabelProvider(labelProvider);
    }

    private void hide()
    {
        if (!isExcluded())
        {
            setExcluded(true);
            control.getParent().layout();
        }
    }

    private void reveal()
    {
        setExcluded(false);
        control.getParent().layout(true, true);
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

    private static Image severityImage(final ProblemSeverity severity)
    {
        final String key = switch (severity)
        {
            case ERROR -> ISharedImages.IMG_OBJS_ERROR_TSK;
            case WARNING -> ISharedImages.IMG_OBJS_WARN_TSK;
            case INFO -> ISharedImages.IMG_OBJS_INFO_TSK;
        };
        return PlatformUI.getWorkbench().getSharedImages().getImage(key);
    }

    /**
     * A problem as the section lists it, with the name of its table as the table's tab shows it.
     */
    private record ProblemRow(DatasetProblem problem, String tableName)
    {
    }
}
