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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

/**
 * Shows the fields of a {@link SourceStatusFields} in a shell of its own, as a status line does, so that a
 * test can read what they show. A field is filled in when it is first read. Use one probe for a test, and
 * read the fields only after the last change of their visibility, because a status line that is updated
 * fills the fields again and takes them over.
 */
final class StatusFieldProbe implements AutoCloseable
{
    static final String ELEMENT_STATE = ITextEditorActionConstants.STATUS_CATEGORY_ELEMENT_STATE;

    static final String INPUT_MODE = ITextEditorActionConstants.STATUS_CATEGORY_INPUT_MODE;

    static final String INPUT_POSITION = ITextEditorActionConstants.STATUS_CATEGORY_INPUT_POSITION;

    /**
     * The categories of the four fields, in the order of the status line.
     */
    static final List<String> CATEGORIES = List.of(ITextEditorActionConstants.STATUS_CATEGORY_FIND_FIELD,
            ELEMENT_STATE, INPUT_MODE, INPUT_POSITION);

    private static final Pattern LINE_AND_COLUMN = Pattern.compile("(\\d+)\\D+(\\d+)");

    private final SourceStatusFields fields;

    private final Shell shell = new Shell(Display.getDefault());

    private final Composite statusLine = new Composite(shell, SWT.NONE);

    private final Map<String, CLabel> labels = new HashMap<>();

    StatusFieldProbe(final SourceStatusFields fields)
    {
        this.fields = fields;
    }

    /**
     * Returns the text that a field shows now.
     *
     * @param category The category of the field.
     * @return The text of the field.
     */
    String text(final String category)
    {
        return labels.computeIfAbsent(category, this::fillIntoStatusLine).getText();
    }

    /**
     * Returns the line and the column that the position field shows, such as {@code 2:3}, so that a test
     * depends neither on the spacing nor on the offset that newer text editors add.
     *
     * @return The line and the column, separated by a colon.
     */
    String position()
    {
        final String text = text(INPUT_POSITION);
        final Matcher lineAndColumn = LINE_AND_COLUMN.matcher(text);
        if (lineAndColumn.find())
        {
            return lineAndColumn.group(1) + ":" + lineAndColumn.group(2);
        }
        throw new IllegalStateException("The position field shows no line and column: " + text);
    }

    /**
     * Returns the categories of the fields that are visible, in the order of the status line.
     *
     * @return The categories of the visible fields.
     */
    List<String> visibleCategories()
    {
        final List<String> visible = new ArrayList<>();
        for (final String category : CATEGORIES)
        {
            if (fields.item(category).isVisible())
            {
                visible.add(category);
            }
        }
        return visible;
    }

    @Override
    public void close()
    {
        shell.dispose();
    }

    private CLabel fillIntoStatusLine(final String category)
    {
        final int childCountBefore = statusLine.getChildren().length;
        fields.item(category).fill(statusLine);
        final Control[] children = statusLine.getChildren();
        return Arrays.stream(children).skip(childCountBefore).filter(CLabel.class::isInstance)
                .map(CLabel.class::cast).findFirst().orElseThrow();
    }
}
