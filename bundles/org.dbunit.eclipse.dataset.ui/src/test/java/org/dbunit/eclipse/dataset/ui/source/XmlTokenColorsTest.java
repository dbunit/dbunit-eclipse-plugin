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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.themes.IThemeManager;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link XmlTokenColors} against the workbench's theme manager; each test restores every color it
 * changes.
 */
class XmlTokenColorsTest
{
    private static final List<String> COLOR_IDS = List.of(XmlTokenColors.TAG_COLOR,
            XmlTokenColors.ATTRIBUTE_NAME_COLOR, XmlTokenColors.ATTRIBUTE_VALUE_COLOR,
            XmlTokenColors.COMMENT_COLOR, XmlTokenColors.DECLARATION_COLOR);

    private static final RGB CHANGED_COLOR = new RGB(1, 2, 3);

    private final IThemeManager themeManager = PlatformUI.getWorkbench().getThemeManager();

    private final ColorRegistry colorRegistry = themeManager.getCurrentTheme().getColorRegistry();

    @Test
    void testGetToken_forEachColorDefinition_carriesTheCurrentThemeColor()
    {
        final XmlTokenColors tokenColors = new XmlTokenColors(themeManager, () ->
        {
        });
        try
        {
            final Map<String, RGB> tokenColorValues = new LinkedHashMap<>();
            final Map<String, RGB> themeColorValues = new LinkedHashMap<>();
            for (final String colorId : COLOR_IDS)
            {
                tokenColorValues.put(colorId, foreground(tokenColors.getToken(colorId)));
                themeColorValues.put(colorId, colorRegistry.getRGB(colorId));
            }

            assertThat(COLOR_IDS).as("The theme must define every Source page color.")
                    .allMatch(colorRegistry::hasValueFor);
            assertThat(tokenColorValues).as("Each token must carry its theme color.")
                    .isEqualTo(themeColorValues);
        }
        finally
        {
            tokenColors.dispose();
        }
    }

    @Test
    void testThemeColorChange_whileNotDisposed_updatesTheTokenAndReportsTheChange()
    {
        final AtomicInteger changes = new AtomicInteger();
        final XmlTokenColors tokenColors = new XmlTokenColors(themeManager, changes::incrementAndGet);
        final RGB originalColor = colorRegistry.getRGB(XmlTokenColors.TAG_COLOR);
        try
        {
            colorRegistry.put(XmlTokenColors.TAG_COLOR, CHANGED_COLOR);

            assertThat(foreground(tokenColors.getToken(XmlTokenColors.TAG_COLOR)))
                    .as("A theme color change must update its token.").isEqualTo(CHANGED_COLOR);
            assertThat(changes.get()).as("A theme color change must be reported once.").isEqualTo(1);
        }
        finally
        {
            colorRegistry.put(XmlTokenColors.TAG_COLOR, originalColor);
            tokenColors.dispose();
        }
    }

    @Test
    void testThemeColorChange_afterDispose_leavesTheTokenAndReportsNothing()
    {
        final AtomicInteger changes = new AtomicInteger();
        final XmlTokenColors tokenColors = new XmlTokenColors(themeManager, changes::incrementAndGet);
        final RGB originalColor = colorRegistry.getRGB(XmlTokenColors.TAG_COLOR);
        tokenColors.dispose();
        try
        {
            colorRegistry.put(XmlTokenColors.TAG_COLOR, CHANGED_COLOR);

            assertThat(foreground(tokenColors.getToken(XmlTokenColors.TAG_COLOR)))
                    .as("A disposed instance must keep its token's color.").isEqualTo(originalColor);
            assertThat(changes.get()).as("A disposed instance must not report theme changes.").isZero();
        }
        finally
        {
            colorRegistry.put(XmlTokenColors.TAG_COLOR, originalColor);
        }
    }

    private static RGB foreground(final IToken token)
    {
        final TextAttribute textAttribute = (TextAttribute) token.getData();
        return textAttribute.getForeground().getRGB();
    }
}
