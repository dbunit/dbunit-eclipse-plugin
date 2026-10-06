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
import java.util.function.BooleanSupplier;

import org.eclipse.jface.action.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link SharedHistoryAction}: it runs the Source page's action and then refreshes the model, does
 * nothing while a cell editor is active, and follows the label, the tool tip, and the enablement of the
 * Source page's action until it is disposed.
 */
class SharedHistoryActionTest
{
    private final List<String> calls = new ArrayList<>();

    private Action sourceAction;

    @BeforeEach
    void createSourceAction()
    {
        sourceAction = new Action("Undo")
        {
            @Override
            public void run()
            {
                calls.add("source action");
            }
        };
    }

    @Test
    void testConstructor_whenTheSourceActionHasALabelAToolTipAndIsDisabled_takesThemOver()
    {
        sourceAction.setText("Undo Typing@");
        sourceAction.setToolTipText("Undo Typing, the last change");
        sourceAction.setEnabled(false);

        final SharedHistoryAction action = newAction(() -> false);

        assertThat(List.of(action.getText(), action.getToolTipText(), action.isEnabled()))
                .as("The action must show what the Source page's action shows.")
                .containsExactly("Undo Typing@", "Undo Typing, the last change", false);
    }

    @Test
    void testSourceAction_whenItsLabelToolTipAndEnablementChange_theActionFollows()
    {
        final SharedHistoryAction action = newAction(() -> false);

        sourceAction.setText("Undo Typing@");
        sourceAction.setToolTipText("Undo Typing, the last change");
        sourceAction.setEnabled(false);

        assertThat(List.of(action.getText(), action.getToolTipText(), action.isEnabled()))
                .as("The action must follow each change of the Source page's action.")
                .containsExactly("Undo Typing@", "Undo Typing, the last change", false);
    }

    @Test
    void testRun_whenNoCellEditorIsActive_runsTheSourceActionAndThenRefreshesTheModel()
    {
        final SharedHistoryAction action = newAction(() -> false);

        action.run();

        assertThat(calls).as("The source action must run first, so that the refresh sees what it changed.")
                .containsExactly("source action", "refresh");
    }

    @Test
    void testRun_whileACellEditorIsActive_doesNothing()
    {
        final SharedHistoryAction action = newAction(() -> true);

        action.run();

        assertThat(calls).as("Undo and redo must decline while a cell editor is open.").isEmpty();
    }

    @Test
    void testDispose_whenCalled_stopsFollowingTheSourceAction()
    {
        final SharedHistoryAction action = newAction(() -> false);

        action.dispose();
        sourceAction.setText("Undo Typing@");
        sourceAction.setEnabled(false);

        assertThat(List.of(action.getText(), action.isEnabled()))
                .as("A disposed action must not follow the Source page's action.")
                .containsExactly("Undo", true);
    }

    private SharedHistoryAction newAction(final BooleanSupplier hasActiveCellEditor)
    {
        return new SharedHistoryAction(sourceAction, hasActiveCellEditor, () -> calls.add("refresh"));
    }
}
