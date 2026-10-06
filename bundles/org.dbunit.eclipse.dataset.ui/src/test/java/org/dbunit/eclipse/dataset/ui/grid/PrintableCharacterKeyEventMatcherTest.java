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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PrintableCharacterKeyEventMatcher}, which decides whether a key event is typing that
 * starts an edit of the selected cell.
 */
class PrintableCharacterKeyEventMatcherTest
{
    private Shell shell;

    @BeforeEach
    void createShell()
    {
        shell = new Shell(Display.getDefault());
    }

    @AfterEach
    void disposeShell()
    {
        shell.dispose();
    }

    @Test
    void testMatches_onANonMacPlatform_forPrintableCharactersWithNoOrExpectedModifiers_returnsTrue()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(false);

        assertThat(matcher.matches(keyEvent('a', 'a', SWT.NONE))).as("A plain letter must match.").isTrue();
        assertThat(matcher.matches(keyEvent('5', '5', SWT.NONE))).as("A plain digit must match.").isTrue();
        assertThat(matcher.matches(keyEvent('.', '.', SWT.NONE))).as("Plain punctuation must match.")
                .isTrue();
        assertThat(matcher.matches(keyEvent(' ', ' ', SWT.NONE))).as("A plain space must match.").isTrue();
        assertThat(matcher.matches(keyEvent('A', 'a', SWT.SHIFT))).as("Shift plus a letter must match.")
                .isTrue();
        assertThat(matcher.matches(keyEvent('a', 'a', SWT.MOD1 | SWT.MOD3)))
                .as("AltGr (Ctrl+Alt) plus a character must match.").isTrue();
        assertThat(matcher.matches(keyEvent('A', 'a', SWT.MOD1 | SWT.MOD3 | SWT.SHIFT)))
                .as("AltGr with Shift plus a character must match.").isTrue();
    }

    @Test
    void testMatches_onANonMacPlatform_forAltAlone_returnsFalse()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(false);

        assertThat(matcher.matches(keyEvent('a', 'a', SWT.ALT)))
                .as("Alt alone must not match outside macOS, since it drives menu mnemonics.").isFalse();
        assertThat(matcher.matches(keyEvent('A', 'a', SWT.ALT | SWT.SHIFT)))
                .as("Alt with Shift must not match outside macOS either.").isFalse();
    }

    @Test
    void testMatches_onMacOs_forPrintableCharactersTypedWithOption_returnsTrue()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(true);

        assertThat(matcher.matches(keyEvent('a', 'a', SWT.NONE))).as("A plain letter must match.").isTrue();
        assertThat(matcher.matches(keyEvent('A', 'a', SWT.SHIFT))).as("Shift plus a letter must match.")
                .isTrue();
        assertThat(matcher.matches(keyEvent('@', 'l', SWT.ALT)))
                .as("A character typed with Option alone must match on macOS.").isTrue();
        assertThat(matcher.matches(keyEvent('€', 'e', SWT.ALT | SWT.SHIFT)))
                .as("A character typed with Option and Shift must match on macOS.").isTrue();
    }

    @Test
    void testMatches_onMacOs_forCommandModifiedCharacters_returnsFalse()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(true);

        assertThat(matcher.matches(keyEvent('a', 'a', SWT.COMMAND))).as("Command alone must not match.")
                .isFalse();
        assertThat(matcher.matches(keyEvent('a', 'a', SWT.COMMAND | SWT.ALT)))
                .as("Command plus Option must not match, even though SWT.MOD1 | SWT.MOD3 is Command "
                        + "plus Option on macOS.")
                .isFalse();
    }

    @Test
    void testMatches_forControlCharactersOrNonTypingKeys_returnsFalse()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(false);

        assertThat(matcher.matches(keyEvent((char) 3, 'c', SWT.MOD1))).as("Ctrl+C must not match.")
                .isFalse();
        assertThat(matcher.matches(keyEvent((char) 0, SWT.ARROW_RIGHT, SWT.NONE)))
                .as("An arrow key must not match.").isFalse();
        assertThat(matcher.matches(keyEvent((char) 0, SWT.F2, SWT.NONE)))
                .as("A function key must not match.").isFalse();
    }

    @Test
    void testMatches_forEitherHalfOfASupplementaryCharacter_returnsFalse()
    {
        final PrintableCharacterKeyEventMatcher matcher = new PrintableCharacterKeyEventMatcher(false);
        final int emoji = 0x1F600;

        assertThat(matcher.matches(keyEvent(Character.highSurrogate(emoji), 0, SWT.NONE)))
                .as("The first half of an emoji, which SWT sends as a key event of its own, must not match.")
                .isFalse();
        assertThat(matcher.matches(keyEvent(Character.lowSurrogate(emoji), 0, SWT.NONE)))
                .as("The second half of an emoji must not match either.").isFalse();
    }

    private KeyEvent keyEvent(final char character, final int keyCode, final int stateMask)
    {
        final Event event = new Event();
        event.widget = shell;
        event.character = character;
        event.keyCode = keyCode;
        event.stateMask = stateMask;
        return new KeyEvent(event);
    }
}
