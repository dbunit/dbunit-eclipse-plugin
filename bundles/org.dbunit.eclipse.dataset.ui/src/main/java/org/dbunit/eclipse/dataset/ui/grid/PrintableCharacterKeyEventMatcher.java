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
 * non-control character typed with no modifier, Shift, or AltGr.
 *
 * @since 1.0.0
 */
final class PrintableCharacterKeyEventMatcher implements IKeyEventMatcher
{
    private static final int ALT_GR = SWT.MOD1 | SWT.MOD3;

    @Override
    public boolean matches(final KeyEvent event)
    {
        if (event.character == 0 || Character.isISOControl(event.character))
        {
            return false;
        }
        final int modifiers = event.stateMask & SWT.MODIFIER_MASK;
        return modifiers == SWT.NONE || modifiers == SWT.SHIFT || modifiers == ALT_GR
                || modifiers == (ALT_GR | SWT.SHIFT);
    }
}
