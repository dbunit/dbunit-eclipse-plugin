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
package org.dbunit.eclipse.dataset.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.osgi.util.NLS;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link Messages} loads every message from {@code messages.properties}, and that the messages
 * follow the quoting rules of {@link NLS#bind}.
 */
class MessagesTest
{
    @Test
    void testMessages_whenLoaded_haveAValueForEveryField() throws IllegalAccessException
    {
        final Map<String, String> messages = messages();

        assertThat(messages).as("Every message must have an entry in messages.properties.")
                .allSatisfy((name, message) -> assertThat(message).as(name)
                        .doesNotStartWith("NLS missing message"));
    }

    @Test
    void testBind_ofEachMessageWithArguments_substitutesThemAllAndShowsEachApostropheOnce()
            throws IllegalAccessException
    {
        final Map<String, String> bound = new LinkedHashMap<>();
        messages().forEach((name, message) ->
        {
            if (message.contains("{0}"))
            {
                bound.put(name, NLS.bind(message, new Object[] { "a", "b", "c" }));
            }
        });

        assertThat(bound).as("A bound message must replace every argument and write '' as one apostrophe.")
                .isNotEmpty()
                .allSatisfy((name, message) -> assertThat(message).as(name).doesNotContain("{", "''"));
    }

    @Test
    void testMessages_withoutArguments_containNoDoubledApostrophes() throws IllegalAccessException
    {
        final Map<String, String> messages = messages();

        assertThat(messages).as("A message shown without binding must write an apostrophe as one character.")
                .allSatisfy((name, message) ->
                {
                    if (!message.contains("{0}"))
                    {
                        assertThat(message).as(name).doesNotContain("''");
                    }
                });
    }

    private static Map<String, String> messages() throws IllegalAccessException
    {
        final Map<String, String> messages = new LinkedHashMap<>();
        for (final Field field : Messages.class.getFields())
        {
            messages.put(field.getName(), (String) field.get(null));
        }
        return messages;
    }
}
