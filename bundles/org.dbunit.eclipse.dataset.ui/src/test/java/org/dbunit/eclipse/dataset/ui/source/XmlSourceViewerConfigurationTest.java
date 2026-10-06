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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.TypedRegion;
import org.eclipse.jface.text.presentation.IPresentationDamager;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.IPresentationRepairer;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the syntax coloring that {@link XmlSourceViewerConfiguration} sets up, for a tag whose attribute
 * value runs over several lines.
 */
class XmlSourceViewerConfigurationTest
{
    private static final String MULTI_LINE_TAG = "<R NOTE=\"first\nit's second\"/>";

    private XmlTokenColors tokenColors;

    private IPresentationReconciler reconciler;

    @BeforeEach
    void createConfiguration()
    {
        tokenColors = new XmlTokenColors(PlatformUI.getWorkbench().getThemeManager(), () ->
        {
        });
        final XmlSourceViewerConfiguration configuration =
                new XmlSourceViewerConfiguration(new PreferenceStore(), tokenColors);
        reconciler = configuration.getPresentationReconciler(null);
    }

    @AfterEach
    void disposeTokenColors()
    {
        tokenColors.dispose();
    }

    @Test
    void testGetDamageRegion_forAChangeOnALaterLineOfAMultiLineTag_isTheWholeTag()
    {
        final Document document = new Document(MULTI_LINE_TAG);
        final IPresentationDamager damager = reconciler.getDamager(XmlPartitionScanner.TAG);
        damager.setDocument(document);
        final ITypedRegion tag = new TypedRegion(0, document.getLength(), XmlPartitionScanner.TAG);
        final DocumentEvent apostropheTyped = apostropheTypedIn(document);

        final IRegion damage = damager.getDamageRegion(tag, apostropheTyped, false);

        assertThat(damage)
                .as("A scan that starts on a later line of a tag does not know that a quoted value began "
                        + "on an earlier line, so the whole tag must be colored again.")
                .isEqualTo(tag);
    }

    @Test
    void testCreatePresentation_afterAChangeOnALaterLineOfAMultiLineValue_colorsTheWholeValueAsAValue()
    {
        final Document document = new Document(MULTI_LINE_TAG);
        final IPresentationDamager damager = reconciler.getDamager(XmlPartitionScanner.TAG);
        final IPresentationRepairer repairer = reconciler.getRepairer(XmlPartitionScanner.TAG);
        damager.setDocument(document);
        repairer.setDocument(document);
        final ITypedRegion tag = new TypedRegion(0, document.getLength(), XmlPartitionScanner.TAG);
        final DocumentEvent apostropheTyped = apostropheTypedIn(document);
        final IRegion damage = damager.getDamageRegion(tag, apostropheTyped, false);
        final TextPresentation presentation = new TextPresentation();

        repairer.createPresentation(presentation,
                new TypedRegion(damage.getOffset(), damage.getLength(), XmlPartitionScanner.TAG));

        final Color valueColor = foregroundOf(XmlTokenColors.ATTRIBUTE_VALUE_COLOR);
        assertThat(colorAt(presentation, MULTI_LINE_TAG.indexOf("it's")))
                .as("The start of the second line is still inside the quoted value, so it takes the color "
                        + "of a value.")
                .isEqualTo(valueColor);
        assertThat(colorAt(presentation, MULTI_LINE_TAG.indexOf("second")))
                .as("The rest of the second line is inside the quoted value too.").isEqualTo(valueColor);
        assertThat(colorAt(presentation, MULTI_LINE_TAG.indexOf("/>")))
                .as("The end of the tag is not a value.").isNotEqualTo(valueColor);
    }

    private static DocumentEvent apostropheTypedIn(final Document document)
    {
        return new DocumentEvent(document, MULTI_LINE_TAG.indexOf('\''), 0, "'");
    }

    private Color foregroundOf(final String colorId)
    {
        final TextAttribute attribute = (TextAttribute) tokenColors.getToken(colorId).getData();
        return attribute.getForeground();
    }

    private static Color colorAt(final TextPresentation presentation, final int offset)
    {
        final var ranges = presentation.getAllStyleRangeIterator();
        while (ranges.hasNext())
        {
            final StyleRange range = ranges.next();
            if (range.start <= offset && offset < range.start + range.length)
            {
                return range.foreground;
            }
        }
        return null;
    }
}
