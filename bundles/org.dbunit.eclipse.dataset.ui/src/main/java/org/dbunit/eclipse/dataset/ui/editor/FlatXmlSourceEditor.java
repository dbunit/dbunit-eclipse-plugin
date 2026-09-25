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

import org.dbunit.eclipse.dataset.ui.source.XmlSourceViewerConfiguration;
import org.dbunit.eclipse.dataset.ui.source.XmlTokenColors;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.themes.IThemeManager;

/**
 * The Source page of {@link FlatXmlDatasetEditor}: a text editor over the same document that colors the
 * XML syntax, with the one addition the multi-page editor needs to detect changes made outside the
 * workbench.
 *
 * @since 1.0.0
 */
public class FlatXmlSourceEditor extends TextEditor
{
    private final XmlTokenColors tokenColors;

    /**
     * Creates the Source page editor, which colors the XML syntax with the current theme's colors.
     */
    public FlatXmlSourceEditor()
    {
        final IThemeManager themeManager = PlatformUI.getWorkbench().getThemeManager();
        tokenColors = new XmlTokenColors(themeManager, this::redrawSyntaxColors);
        setSourceViewerConfiguration(new XmlSourceViewerConfiguration(getPreferenceStore(), tokenColors));
    }

    /**
     * Checks whether the editor input changed or was deleted outside the workbench, prompting to reload
     * or save as needed. The multi-page editor calls this on the nested editor's behalf, because a nested
     * editor never receives part activation notifications directly.
     */
    public void checkExternalModification()
    {
        safelySanityCheckState(getEditorInput());
    }

    /**
     * Stops following theme changes and disposes the editor.
     */
    @Override
    public void dispose()
    {
        tokenColors.dispose();
        super.dispose();
    }

    private void redrawSyntaxColors()
    {
        final ISourceViewer sourceViewer = getSourceViewer();
        if (sourceViewer == null)
        {
            return;
        }
        final StyledText textWidget = sourceViewer.getTextWidget();
        if (textWidget != null && !textWidget.isDisposed())
        {
            sourceViewer.invalidateTextPresentation();
        }
    }
}
