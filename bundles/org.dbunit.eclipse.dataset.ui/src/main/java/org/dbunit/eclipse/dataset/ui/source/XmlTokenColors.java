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

import java.util.Map;

import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.themes.IThemeManager;

/**
 * The colored tokens of the Source page's XML coloring. Their colors come from the theme color
 * definitions that {@code plugin.xml} declares, and follow theme and color preference changes.
 *
 * @since 1.0.0
 */
public final class XmlTokenColors
{
    /**
     * The theme color definition of tag delimiters and element names.
     */
    public static final String TAG_COLOR = "org.dbunit.eclipse.dataset.ui.source.tagColor";

    /**
     * The theme color definition of attribute names.
     */
    public static final String ATTRIBUTE_NAME_COLOR =
            "org.dbunit.eclipse.dataset.ui.source.attributeNameColor";

    /**
     * The theme color definition of quoted attribute values.
     */
    public static final String ATTRIBUTE_VALUE_COLOR =
            "org.dbunit.eclipse.dataset.ui.source.attributeValueColor";

    /**
     * The theme color definition of comments.
     */
    public static final String COMMENT_COLOR = "org.dbunit.eclipse.dataset.ui.source.commentColor";

    /**
     * The theme color definition of processing instructions and the DOCTYPE.
     */
    public static final String DECLARATION_COLOR = "org.dbunit.eclipse.dataset.ui.source.declarationColor";

    private final Map<String, Token> tokens = Map.of(TAG_COLOR, new Token(null), ATTRIBUTE_NAME_COLOR,
            new Token(null), ATTRIBUTE_VALUE_COLOR, new Token(null), COMMENT_COLOR, new Token(null),
            DECLARATION_COLOR, new Token(null));

    private final IPropertyChangeListener themeListener = this::themeChanged;

    private final IThemeManager themeManager;

    private final Runnable colorsChanged;

    /**
     * Creates the tokens with the current theme's colors and starts following theme changes.
     *
     * @param themeManager The theme manager whose current theme supplies the colors.
     * @param colorsChanged The action to run after a theme change has updated the tokens' colors, for
     *            example to redraw the text that uses them.
     */
    public XmlTokenColors(final IThemeManager themeManager, final Runnable colorsChanged)
    {
        this.themeManager = themeManager;
        this.colorsChanged = colorsChanged;
        readColors();
        themeManager.addPropertyChangeListener(themeListener);
    }

    /**
     * Returns the token colored by a theme color definition; its color changes with the theme.
     *
     * @param colorId One of the color definition identifiers of this class, such as {@link #TAG_COLOR}.
     * @return The token, whose data is a {@link TextAttribute} with the color as its foreground.
     */
    public IToken getToken(final String colorId)
    {
        return tokens.get(colorId);
    }

    /**
     * Stops following theme changes.
     */
    public void dispose()
    {
        themeManager.removePropertyChangeListener(themeListener);
    }

    private void themeChanged(final PropertyChangeEvent event)
    {
        final String property = event.getProperty();
        if (IThemeManager.CHANGE_CURRENT_THEME.equals(property) || tokens.containsKey(property))
        {
            readColors();
            colorsChanged.run();
        }
    }

    private void readColors()
    {
        final ColorRegistry colorRegistry = themeManager.getCurrentTheme().getColorRegistry();
        tokens.forEach((colorId, token) -> token.setData(new TextAttribute(colorRegistry.get(colorId))));
    }
}
