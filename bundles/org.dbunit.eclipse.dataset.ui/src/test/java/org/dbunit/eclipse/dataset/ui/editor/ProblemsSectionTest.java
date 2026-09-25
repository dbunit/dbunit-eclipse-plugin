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

import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ProblemsSection}: its height follows the number of problems, up to six rows, the message
 * column takes the width the other columns leave, and each row shows its severity's image and its table's
 * name.
 */
class ProblemsSectionTest
{
    private Shell shell;

    private ProblemsSection section;

    @BeforeEach
    void createSection()
    {
        shell = new Shell(Display.getDefault());
        shell.setLayout(new GridLayout());
        shell.setSize(800, 600);
        section = new ProblemsSection(shell, problem ->
        {
        });
        shell.open();
        UiTestWorkspace.processEvents();
    }

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testUpdate_withOneProblem_showsOneRowWithTheMessageColumnFillingTheWidth()
    {
        section.update(model(problems(1)));
        UiTestWorkspace.processEvents();

        final Table table = section.getTable();
        final int itemHeight = table.getItemHeight();
        assertThat(table.getClientArea().height)
                .as("One problem must show one whole row, and no more than half of another.")
                .isBetween(itemHeight, itemHeight + itemHeight / 2);
        int columnsWidth = 0;
        for (int column = 0; column < table.getColumnCount(); column++)
        {
            columnsWidth += table.getColumn(column).getWidth();
        }
        assertThat(columnsWidth).as("The message column must take the width that the other columns leave.")
                .isGreaterThan(table.getClientArea().width - 30);
    }

    @Test
    void testUpdate_withMoreThanSixProblems_showsSixRows()
    {
        section.update(model(problems(8)));
        UiTestWorkspace.processEvents();

        final Table table = section.getTable();
        final int itemHeight = table.getItemHeight();
        assertThat(table.getClientArea().height)
                .as("More than six problems must show six whole rows, and no more than half of another.")
                .isBetween(6 * itemHeight, 6 * itemHeight + itemHeight / 2);
    }

    @Test
    void testUpdate_withProblemsOfEachSeverity_showsEachSeveritysImage()
    {
        section.update(model(List.of(problem(ProblemSeverity.ERROR), problem(ProblemSeverity.WARNING),
                problem(ProblemSeverity.INFO))));
        UiTestWorkspace.processEvents();

        final Table table = section.getTable();
        final List<Image> images = new ArrayList<>();
        for (int row = 0; row < table.getItemCount(); row++)
        {
            images.add(table.getItem(row).getImage(0));
        }
        assertThat(images).as("Each problem must show the image of its severity.").containsExactly(
                sharedImage(ISharedImages.IMG_OBJS_ERROR_TSK), sharedImage(ISharedImages.IMG_OBJS_WARN_TSK),
                sharedImage(ISharedImages.IMG_OBJS_INFO_TSK));
    }

    @Test
    void testUpdate_withAProblemOfATable_showsTheTableNameAsItsTabDoes()
    {
        section.update(model(problems(1)));
        UiTestWorkspace.processEvents();

        assertThat(section.getTable().getItem(0).getText(1))
                .as("The table column must show the table's name, not its case-folded key.")
                .isEqualTo("users");
    }

    private static DatasetModel model(final List<DatasetProblem> problems)
    {
        final DatasetTable users = new DatasetTable("USERS", "users", List.of(), List.of(), false);
        return new DatasetModel(List.of(users), problems, true);
    }

    private static List<DatasetProblem> problems(final int count)
    {
        final List<DatasetProblem> problems = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            problems.add(new DatasetProblem(ProblemCode.COLUMN_NOT_IN_FIRST_ROW, ProblemSeverity.WARNING,
                    "Column \"C" + i + "\" of table \"USERS\" is missing from the first element, so dbUnit "
                            + "ignores its value in this row.",
                    "USERS", "C" + i, 1, 0, 0));
        }
        return problems;
    }

    private static DatasetProblem problem(final ProblemSeverity severity)
    {
        final String message = severity + " problem";
        return new DatasetProblem(ProblemCode.COLUMN_NOT_IN_FIRST_ROW, severity, message, "USERS", "ID", 1, 0,
                0);
    }

    private static Image sharedImage(final String key)
    {
        return PlatformUI.getWorkbench().getSharedImages().getImage(key);
    }
}
