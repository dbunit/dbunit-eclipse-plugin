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

import java.util.function.Consumer;

import org.dbunit.eclipse.dataset.ui.source.XmlDocumentSetupParticipant;
import org.dbunit.eclipse.dataset.ui.source.XmlSourceViewerConfiguration;
import org.dbunit.eclipse.dataset.ui.source.XmlTokenColors;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.ForwardingDocumentProvider;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.themes.IThemeManager;

/**
 * The Source page of {@link FlatXmlDatasetEditor}: a text editor over the same document that colors the
 * XML syntax, with the additions the multi-page editor needs: to detect changes made outside the
 * workbench, to let the Tables page drop a cell edit in progress before the document is reverted, and to
 * close the whole dataset editor when it is asked to close itself.
 *
 * @since 1.0.0
 */
public class FlatXmlSourceEditor extends TextEditor
{
    private final XmlTokenColors tokenColors;

    private final Runnable beforeRevert;

    private final Consumer<Boolean> closeDatasetEditor;

    /**
     * Creates the Source page editor, which colors the XML syntax with the current theme's colors.
     *
     * @param beforeRevert       Runs before the editor reverts its document to the text of the last save, so
     *                           that a cell edit in progress on the Tables page does not write its value
     *                           into the reverted document.
     * @param closeDatasetEditor Closes the dataset editor that holds this page, saving its changes first
     *                           when it is given true.
     */
    public FlatXmlSourceEditor(final Runnable beforeRevert, final Consumer<Boolean> closeDatasetEditor)
    {
        this.beforeRevert = beforeRevert;
        this.closeDatasetEditor = closeDatasetEditor;
        final IThemeManager themeManager = PlatformUI.getWorkbench().getThemeManager();
        tokenColors = new XmlTokenColors(themeManager, this::redrawSyntaxColors);
        setSourceViewerConfiguration(new XmlSourceViewerConfiguration(getPreferenceStore(), tokenColors));
        setDocumentProvider(new ForwardingDocumentProvider(XmlDocumentSetupParticipant.PARTITIONING,
                new XmlDocumentSetupParticipant(), new TextFileDocumentProvider()));
    }

    /**
     * Checks whether the editor input changed or was deleted outside the workbench, prompting to reload
     * or save as needed. The multi-page editor calls this when it or its window is activated, so that the
     * check does not depend on how the inherited editor follows the activation of the part that holds it.
     */
    public void checkExternalModification()
    {
        safelySanityCheckState(getEditorInput());
    }

    /**
     * Runs the action that was given to the constructor, then reverts the document to the text of the last
     * save.
     */
    @Override
    protected void performRevert()
    {
        beforeRevert.run();
        super.performRevert();
    }

    /**
     * Closes the dataset editor that holds this page, because the workbench page knows only the dataset
     * editor, so it would ignore a request to close this page alone. Sanity checking is turned off first, as
     * the inherited implementation does, so that a change outside the workbench does not prompt again for an
     * editor that is about to close.
     *
     * @param save True to save the changes first, false to discard them.
     */
    @Override
    public void close(final boolean save)
    {
        enableSanityChecking(false);
        closeDatasetEditor.accept(save);
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
