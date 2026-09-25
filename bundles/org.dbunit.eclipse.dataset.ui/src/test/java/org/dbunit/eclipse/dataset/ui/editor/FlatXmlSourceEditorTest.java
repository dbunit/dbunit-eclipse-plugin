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

import java.util.LinkedHashMap;
import java.util.Map;

import org.dbunit.eclipse.dataset.ui.source.XmlTokenColors;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.Test;

/**
 * Tests the syntax coloring of {@link FlatXmlSourceEditor}, the Source page of a real dataset editor.
 */
class FlatXmlSourceEditorTest
{
    private static final String CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- Users -->
            <dataset>
                <USERS ID="1"/>
            </dataset>
            """;

    @Test
    void testOpen_whenFileIsAFlatXmlDataset_colorsEachXmlConstructWithItsThemeColor() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", CONTENT);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final StyledText sourceText = sourceText(editor);
            final ColorRegistry colorRegistry = currentColorRegistry();

            final Map<String, String> colorIdsBySnippet = Map.of("<?xml", XmlTokenColors.DECLARATION_COLOR,
                    "<!--", XmlTokenColors.COMMENT_COLOR, "USERS", XmlTokenColors.TAG_COLOR, "ID",
                    XmlTokenColors.ATTRIBUTE_NAME_COLOR, "\"1\"", XmlTokenColors.ATTRIBUTE_VALUE_COLOR);

            final Map<String, RGB> colors = new LinkedHashMap<>();
            final Map<String, RGB> themeColors = new LinkedHashMap<>();
            colorIdsBySnippet.forEach((snippet, colorId) ->
            {
                colors.put(snippet, foregroundAt(sourceText, CONTENT.indexOf(snippet)));
                themeColors.put(snippet, colorRegistry.getRGB(colorId));
            });

            assertThat(colors).as("The Source page must color each XML construct with its theme color.")
                    .isEqualTo(themeColors);
        }
    }

    @Test
    void testThemeColorChange_whileTheEditorIsOpen_recolorsTheSource() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", CONTENT);
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            final StyledText sourceText = sourceText(editor);
            final ColorRegistry colorRegistry = currentColorRegistry();
            final RGB originalColor = colorRegistry.getRGB(XmlTokenColors.TAG_COLOR);
            final RGB changedColor = new RGB(1, 2, 3);
            try
            {
                colorRegistry.put(XmlTokenColors.TAG_COLOR, changedColor);
                UiTestWorkspace.processEvents();

                assertThat(foregroundAt(sourceText, CONTENT.indexOf("USERS")))
                        .as("A theme color change must recolor the open Source page.")
                        .isEqualTo(changedColor);
            }
            finally
            {
                colorRegistry.put(XmlTokenColors.TAG_COLOR, originalColor);
            }
        }
    }

    private static StyledText sourceText(final FlatXmlDatasetEditor editor)
    {
        return (StyledText) editor.getSourceEditor().getAdapter(Control.class);
    }

    private static ColorRegistry currentColorRegistry()
    {
        return PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry();
    }

    private static RGB foregroundAt(final StyledText sourceText, final int offset)
    {
        final StyleRange styleRange = sourceText.getStyleRangeAtOffset(offset);
        if (styleRange == null || styleRange.foreground == null)
        {
            return null;
        }
        return styleRange.foreground.getRGB();
    }
}
