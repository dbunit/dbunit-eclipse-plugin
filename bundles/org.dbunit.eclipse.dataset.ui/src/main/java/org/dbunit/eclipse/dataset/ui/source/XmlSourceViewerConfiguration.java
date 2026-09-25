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
package org.dbunit.eclipse.dataset.ui.source;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;

/**
 * Configures the Source page's viewer: XML syntax coloring over the partitioning that
 * {@link XmlDocumentSetupParticipant} installs, and the text editor defaults for everything else.
 *
 * @since 1.0.0
 */
public final class XmlSourceViewerConfiguration extends TextSourceViewerConfiguration
{
    private final XmlTokenColors tokenColors;

    /**
     * Creates the configuration.
     *
     * @param preferenceStore The preference store of the text editor defaults, such as the tab width.
     * @param tokenColors The colored tokens of the syntax coloring.
     */
    public XmlSourceViewerConfiguration(final IPreferenceStore preferenceStore,
            final XmlTokenColors tokenColors)
    {
        super(preferenceStore);
        this.tokenColors = tokenColors;
    }

    /**
     * Returns the XML partitioning.
     *
     * @param sourceViewer The viewer to configure.
     * @return {@link XmlDocumentSetupParticipant#PARTITIONING}.
     */
    @Override
    public String getConfiguredDocumentPartitioning(final ISourceViewer sourceViewer)
    {
        return XmlDocumentSetupParticipant.PARTITIONING;
    }

    /**
     * Returns the content types of the XML partitioning.
     *
     * @param sourceViewer The viewer to configure.
     * @return The default content type followed by the comment, processing instruction, DOCTYPE, and tag
     *         content types.
     */
    @Override
    public String[] getConfiguredContentTypes(final ISourceViewer sourceViewer)
    {
        return new String[] { IDocument.DEFAULT_CONTENT_TYPE, XmlPartitionScanner.COMMENT,
                XmlPartitionScanner.PROCESSING_INSTRUCTION, XmlPartitionScanner.DOCTYPE,
                XmlPartitionScanner.TAG };
    }

    /**
     * Returns a presentation reconciler that colors each partition: tags by their parts, comments,
     * processing instructions, and the DOCTYPE each in one color, and text between tags in the default
     * color.
     *
     * @param sourceViewer The viewer to configure.
     * @return The presentation reconciler.
     */
    @Override
    public IPresentationReconciler getPresentationReconciler(final ISourceViewer sourceViewer)
    {
        final PresentationReconciler reconciler = new PresentationReconciler();
        reconciler.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        final XmlTagScanner tagScanner = new XmlTagScanner(tokenColors.getToken(XmlTokenColors.TAG_COLOR),
                tokenColors.getToken(XmlTokenColors.ATTRIBUTE_NAME_COLOR),
                tokenColors.getToken(XmlTokenColors.ATTRIBUTE_VALUE_COLOR));
        setDamagerRepairer(reconciler, XmlPartitionScanner.TAG, tagScanner);
        setDamagerRepairer(reconciler, XmlPartitionScanner.COMMENT,
                singleColorScanner(XmlTokenColors.COMMENT_COLOR));
        setDamagerRepairer(reconciler, XmlPartitionScanner.PROCESSING_INSTRUCTION,
                singleColorScanner(XmlTokenColors.DECLARATION_COLOR));
        setDamagerRepairer(reconciler, XmlPartitionScanner.DOCTYPE,
                singleColorScanner(XmlTokenColors.DECLARATION_COLOR));
        setDamagerRepairer(reconciler, IDocument.DEFAULT_CONTENT_TYPE, new RuleBasedScanner());
        return reconciler;
    }

    private RuleBasedScanner singleColorScanner(final String colorId)
    {
        final RuleBasedScanner scanner = new RuleBasedScanner();
        scanner.setDefaultReturnToken(tokenColors.getToken(colorId));
        return scanner;
    }

    private static void setDamagerRepairer(final PresentationReconciler reconciler, final String contentType,
            final ITokenScanner scanner)
    {
        final DefaultDamagerRepairer damagerRepairer = new DefaultDamagerRepairer(scanner);
        reconciler.setDamager(damagerRepairer, contentType);
        reconciler.setRepairer(damagerRepairer, contentType);
    }
}
