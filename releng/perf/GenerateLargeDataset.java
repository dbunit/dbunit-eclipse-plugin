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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Writes a large dbUnit dataset for checking the dataset editor's performance: five tables, one after the
 * other, that share the rows and each have the given number of columns. Every table's first row has a
 * value in every column; later rows leave some columns NULL, and some values are empty strings or hold
 * characters that the dataset format must escape. The same arguments always produce the same file. Run
 * from the repository root: {@code java releng/perf/GenerateLargeDataset.java <format> <rows> <columns>
 * <output>}, for example {@code java releng/perf/GenerateLargeDataset.java flatxml 100000 20
 * target/perf/dataset-100000.xml}. The format names a dbUnit dataset format; so far {@code flatxml}, the
 * flat XML format, is the only one.
 */
public class GenerateLargeDataset
{
    private static final String USAGE =
            "Usage: java releng/perf/GenerateLargeDataset.java <format> <rows> <columns> <output>";

    private static final Map<String, DatasetWriter> WRITERS =
            new TreeMap<>(Map.of("flatxml", new FlatXmlWriter()));

    private static final String[] TABLE_NAMES = {"CUSTOMER", "PRODUCT", "ORDERS", "ORDER_ITEM", "PAYMENT"};

    private static final String[] WORDS = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf",
        "hotel", "india", "juliett", "kilo", "lima", "mike", "november", "oscar", "papa"};

    private static final LocalDate FIRST_DATE = LocalDate.of(2024, 1, 1);

    private static final int NULL_EVERY = 7;

    private static final int EMPTY_EVERY = 11;

    private static final int ESCAPED_EVERY = 13;

    public static void main(final String[] args) throws IOException
    {
        if (args.length != 4)
        {
            exitWithError(USAGE);
        }
        final String format = args[0];
        final DatasetWriter writer = WRITERS.get(format);
        if (writer == null)
        {
            final String formats = String.join(", ", WRITERS.keySet());
            exitWithError("Unknown format '" + format + "'; use one of: " + formats);
        }
        final int rows = Integer.parseInt(args[1]);
        final int columns = Integer.parseInt(args[2]);
        final Path output = Paths.get(args[3]);
        if (rows < TABLE_NAMES.length || columns < 1)
        {
            exitWithError("Use at least " + TABLE_NAMES.length + " rows and 1 column.");
        }
        final Path parent = output.toAbsolutePath().getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        writer.write(new GeneratedDataset(rows, columns), output);
        final long size = Files.size(output);
        System.out.printf(Locale.ENGLISH,
                "Wrote %,d rows of %d columns in %d tables as %s (%,d bytes) to %s%n", rows, columns,
                TABLE_NAMES.length, format, size, output);
    }

    private static void exitWithError(final String message)
    {
        System.err.println(message);
        System.exit(2);
    }

    /**
     * Writes a generated dataset in one dbUnit dataset format.
     */
    private interface DatasetWriter
    {
        /**
         * Writes the dataset, requesting its tables in order and each table's rows in order.
         *
         * @param dataset The dataset to write.
         * @param output The file to write.
         * @throws IOException If writing fails.
         */
        void write(GeneratedDataset dataset, Path output) throws IOException;
    }

    /**
     * Writes dbUnit's flat XML format: one element per row, with an attribute for each value that is not
     * NULL, escaped as dbUnit's flat XML writer escapes it.
     */
    private static final class FlatXmlWriter implements DatasetWriter
    {
        @Override
        public void write(final GeneratedDataset dataset, final Path output) throws IOException
        {
            final List<String> columnNames = dataset.columnNames();
            try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8))
            {
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write("<dataset>\n");
                for (int table = 0; table < TABLE_NAMES.length; table++)
                {
                    final int tableRows = dataset.rowCount(table);
                    for (int row = 0; row < tableRows; row++)
                    {
                        writeRow(writer, TABLE_NAMES[table], columnNames, dataset.nextRow(row));
                    }
                }
                writer.write("</dataset>\n");
            }
        }

        private static void writeRow(final BufferedWriter writer, final String table,
                final List<String> columnNames, final List<String> values) throws IOException
        {
            final StringBuilder text = new StringBuilder(values.size() * 24);
            text.append("  <").append(table);
            for (int column = 0; column < values.size(); column++)
            {
                final String value = values.get(column);
                if (value != null)
                {
                    text.append(' ').append(columnNames.get(column)).append("=\"");
                    text.append(escape(value)).append('"');
                }
            }
            text.append("/>\n");
            writer.write(text.toString());
        }

        private static String escape(final String value)
        {
            final StringBuilder text = new StringBuilder(value.length());
            for (int index = 0; index < value.length(); index++)
            {
                final char character = value.charAt(index);
                switch (character)
                {
                    case '&' -> text.append("&amp;");
                    case '<' -> text.append("&lt;");
                    case '>' -> text.append("&gt;");
                    case '"' -> text.append("&quot;");
                    case '\'' -> text.append("&apos;");
                    default -> text.append(character);
                }
            }
            return text.toString();
        }
    }

    /**
     * The generated tables, columns, and values, independent of any dataset format. The values come from
     * one random sequence, so the same arguments give the same values only when the tables are requested in
     * order and each table's rows in order.
     */
    private static final class GeneratedDataset
    {
        private final int rows;

        private final int columns;

        private final Random random;

        GeneratedDataset(final int rows, final int columns)
        {
            this.rows = rows;
            this.columns = columns;
            random = new Random(rows * 31L + columns);
        }

        List<String> columnNames()
        {
            final List<String> names = new ArrayList<>(columns);
            for (int column = 0; column < columns; column++)
            {
                names.add(columnName(column));
            }
            return names;
        }

        /**
         * Shares the rows among the tables, giving the first tables one more row when they do not divide
         * evenly.
         */
        int rowCount(final int table)
        {
            final int tableCount = TABLE_NAMES.length;
            final int extra = table < rows % tableCount ? 1 : 0;
            return rows / tableCount + extra;
        }

        /**
         * Returns the values of the next row, which is the given row of its table: raw text in column order,
         * with null for NULL.
         */
        List<String> nextRow(final int row)
        {
            final List<String> values = new ArrayList<>(columns);
            for (int column = 0; column < columns; column++)
            {
                final boolean firstRow = row == 0;
                final boolean nullValue = column > 0 && (row + column) % NULL_EVERY == 0;
                values.add(firstRow || !nullValue ? value(row, column) : null);
            }
            return values;
        }

        private static String columnName(final int column)
        {
            if (column == 0)
            {
                return "ID";
            }
            final int kind = column % 4;
            final String prefix = switch (kind)
            {
                case 0 -> "NAME";
                case 1 -> "AMOUNT";
                case 2 -> "CREATED";
                default -> "NOTE";
            };
            return String.format(Locale.ENGLISH, "%s_%02d", prefix, column);
        }

        /**
         * Returns a cell's value: the row number for the ID column, otherwise a word, an amount, a date, or a
         * note, which is sometimes empty or holds characters that need escaping.
         */
        private String value(final int row, final int column)
        {
            if (column == 0)
            {
                return Integer.toString(row + 1);
            }
            final int kind = column % 4;
            return switch (kind)
            {
                case 0 -> WORDS[random.nextInt(WORDS.length)] + " " + WORDS[random.nextInt(WORDS.length)];
                case 1 -> String.format(Locale.ENGLISH, "%d.%02d", random.nextInt(10_000),
                        random.nextInt(100));
                case 2 -> FIRST_DATE.plusDays(random.nextInt(1_000)).toString();
                default -> note(row, column);
            };
        }

        private String note(final int row, final int column)
        {
            if ((row + column) % EMPTY_EVERY == 0)
            {
                return "";
            }
            final String word = WORDS[random.nextInt(WORDS.length)];
            if ((row + column) % ESCAPED_EVERY == 0)
            {
                return word + " & <" + row + ">";
            }
            return "Note " + row + " about " + word;
        }
    }
}
