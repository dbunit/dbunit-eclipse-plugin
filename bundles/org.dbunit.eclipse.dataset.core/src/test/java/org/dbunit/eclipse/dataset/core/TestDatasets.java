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
package org.dbunit.eclipse.dataset.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads test fixture files from {@code src/test/resources/datasets/}, relative to the module directory,
 * which is the surefire working directory.
 */
public final class TestDatasets
{
    private static final Path DATASETS_DIRECTORY = Path.of("src", "test", "resources", "datasets");

    private TestDatasets()
    {
    }

    /**
     * Reads a fixture file as UTF-8 text.
     *
     * @param name The file name, relative to the datasets directory; a DTD fixture is named
     *             {@code "dtd/<name>.dtd"}.
     * @return The file's content.
     */
    public static String read(final String name)
    {
        return read(name, StandardCharsets.UTF_8);
    }

    /**
     * Reads a fixture file as text in a given charset.
     *
     * @param name The file name, relative to the datasets directory.
     * @param charset The charset the file's bytes are encoded in.
     * @return The file's content.
     */
    public static String read(final String name, final Charset charset)
    {
        final Path path = DATASETS_DIRECTORY.resolve(name);
        try
        {
            final byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, charset);
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException("Could not read test dataset '" + name + "'.", e);
        }
    }
}
