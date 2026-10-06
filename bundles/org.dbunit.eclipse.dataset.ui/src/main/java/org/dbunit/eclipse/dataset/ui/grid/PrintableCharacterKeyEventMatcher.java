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
package org.dbunit.eclipse.dataset.ui.grid;

import org.eclipse.nebula.widgets.nattable.ui.matcher.IKeyEventMatcher;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;

/**
 * Matches a key event that types a character to start editing the selection anchor: a non-zero,
 * non-control character, which is not one half of a supplementary character, typed with no modifier,
 * Shift, AltGr (Ctrl+Alt, or Command+Option on macOS), or, on macOS only, Option alone, since a character
 * typed with Option there arrives with only {@link SWT#ALT} in the state mask.
 *
 * @since 1.0.0
 */
final class PrintableCharacterKeyEventMatcher implements IKeyEventMatcher
{
    private static final int ALT_GR = SWT.MOD1 | SWT.MOD3;

    private final boolean mac;

    /**
     * Creates the matcher.
     *
     * @param mac True to accept Option alone instead of AltGr, matching macOS; false for every other
     *            platform.
     */
    PrintableCharacterKeyEventMatcher(final boolean mac)
    {
        this.mac = mac;
    }

    /**
     * Tells whether the character of a key event is one that can start an edit when it is typed: not
     * nothing, not a control character, and not one half of a supplementary character such as an emoji.
     * SWT sends each half as a key event of its own, so an editor that is started by one of them would hold
     * a character that is not well formed.
     *
     * @param character The character of the key event.
     * @return True when typing the character can start an edit that is seeded with it.
     */
    static boolean isPrintable(final char character)
    {
        return character != 0 && !Character.isISOControl(character) && !Character.isSurrogate(character);
    }

    @Override
    public boolean matches(final KeyEvent event)
    {
        if (!isPrintable(event.character))
        {
            return false;
        }
        final int modifiers = event.stateMask & SWT.MODIFIER_MASK;
        if (modifiers == SWT.NONE || modifiers == SWT.SHIFT)
        {
            return true;
        }
        return mac ? modifiers == SWT.ALT || modifiers == (SWT.ALT | SWT.SHIFT)
                : modifiers == ALT_GR || modifiers == (ALT_GR | SWT.SHIFT);
    }
}
