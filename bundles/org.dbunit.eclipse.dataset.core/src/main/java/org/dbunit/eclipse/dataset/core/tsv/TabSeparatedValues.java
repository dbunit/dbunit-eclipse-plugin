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
package org.dbunit.eclipse.dataset.core.tsv;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between rows of cell values and the tab-separated text spreadsheets put on the clipboard.
 *
 * @since 1.0.0
 */
public final class TabSeparatedValues
{
    private TabSeparatedValues()
    {
    }

    /**
     * Formats rows of values as tab-separated text.
     *
     * @param rows The rows to format; a null value means NULL and writes as an unquoted empty field, while
     *             the empty string writes as a quoted empty field ({@code ""}) so NULL and the empty
     *             string stay distinguishable when parsed back.
     * @param lineSeparator The line separator written after every row, including the last.
     * @return The formatted text.
     */
    public static String format(final List<List<String>> rows, final String lineSeparator)
    {
        final StringBuilder text = new StringBuilder();
        for (final List<String> row : rows)
        {
            for (int columnIndex = 0; columnIndex < row.size(); columnIndex++)
            {
                if (columnIndex > 0)
                {
                    text.append('\t');
                }
                text.append(quoteIfNeeded(row.get(columnIndex)));
            }
            text.append(lineSeparator);
        }
        return text.toString();
    }

    /**
     * Parses tab-separated text into rows of values.
     *
     * @param text The text to parse; accepts CR LF, LF, and CR line ends.
     * @return The parsed rows, each padded with NULL to the width of the widest row; an empty list when
     *         {@code text} is empty. An unquoted empty field parses as NULL; a quoted empty field
     *         ({@code ""}) parses as the empty string.
     */
    public static List<List<String>> parse(final String text)
    {
        final List<List<String>> rows;
        if (text.isEmpty())
        {
            rows = List.of();
        }
        else
        {
            rows = new Parser(text).parseRows();
        }
        return rows;
    }

    private static String resolveField(final StringBuilder field, final boolean fieldWasQuoted)
    {
        return field.length() > 0 || fieldWasQuoted ? field.toString() : null;
    }

    private static String quoteIfNeeded(final String value)
    {
        if (value == null)
        {
            return "";
        }
        if (value.isEmpty())
        {
            return "\"\"";
        }
        if (needsQuoting(value))
        {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static boolean needsQuoting(final String value)
    {
        return value.startsWith("\"") || value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('"') >= 0;
    }

    private static List<List<String>> pad(final List<List<String>> rows)
    {
        int width = 0;
        for (final List<String> row : rows)
        {
            width = Math.max(width, row.size());
        }
        for (final List<String> row : rows)
        {
            while (row.size() < width)
            {
                row.add(null);
            }
        }
        return rows;
    }

    /**
     * Holds the state of one parse: the rows, the row and the field being built, and whether the text read so
     * far is inside a quoted field.
     */
    private static final class Parser
    {
        private final String text;

        private final List<List<String>> rows = new ArrayList<>();

        private final StringBuilder field = new StringBuilder();

        private List<String> row = new ArrayList<>();

        private boolean quoted;

        private boolean fieldWasQuoted;

        private boolean pendingField;

        private int index;

        private Parser(final String text)
        {
            this.text = text;
        }

        private List<List<String>> parseRows()
        {
            while (index < text.length())
            {
                final char character = text.charAt(index);
                if (quoted)
                {
                    readInQuotedField(character);
                }
                else
                {
                    readOutsideQuotedField(character);
                }
            }
            if (pendingField)
            {
                endRow();
            }
            return pad(rows);
        }

        private void readInQuotedField(final char character)
        {
            if (character == '"' && characterAtIs(index + 1, '"'))
            {
                field.append('"');
                index += 2;
            }
            else if (character == '"')
            {
                quoted = false;
                index++;
            }
            else
            {
                field.append(character);
                index++;
            }
        }

        private void readOutsideQuotedField(final char character)
        {
            if (character == '"' && field.length() == 0)
            {
                quoted = true;
                fieldWasQuoted = true;
                pendingField = true;
                index++;
            }
            else if (character == '\t')
            {
                endField();
                pendingField = true;
                index++;
            }
            else if (character == '\n' || character == '\r')
            {
                endLine(character);
            }
            else
            {
                field.append(character);
                pendingField = true;
                index++;
            }
        }

        private void endLine(final char lineEnd)
        {
            endRow();
            index++;
            if (lineEnd == '\r' && characterAtIs(index, '\n'))
            {
                index++;
            }
        }

        private void endField()
        {
            row.add(resolveField(field, fieldWasQuoted));
            field.setLength(0);
            fieldWasQuoted = false;
        }

        private void endRow()
        {
            endField();
            rows.add(row);
            row = new ArrayList<>();
            pendingField = false;
        }

        private boolean characterAtIs(final int position, final char expected)
        {
            return position < text.length() && text.charAt(position) == expected;
        }
    }
}
