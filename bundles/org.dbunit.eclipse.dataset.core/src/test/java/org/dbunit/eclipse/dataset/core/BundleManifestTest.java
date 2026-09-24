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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

/**
 * Guards the bundle manifest headers that the build, the feature, and the
 * update site depend on.
 */
class BundleManifestTest
{
    private static final Path MANIFEST_PATH = Path.of("META-INF", "MANIFEST.MF");

    @Test
    void testManifest_whenRead_declaresPluginIdAsSingletonSymbolicName()
            throws IOException
    {
        final Attributes attributes = readMainAttributes();

        final String symbolicName = attributes.getValue("Bundle-SymbolicName");

        assertThat(symbolicName).as(
                "Bundle-SymbolicName must match DatasetCore.PLUGIN_ID and be a singleton.")
                .isEqualTo(DatasetCore.PLUGIN_ID + ";singleton:=true");
    }

    @Test
    void testManifest_whenRead_requiresJava17ExecutionEnvironment()
            throws IOException
    {
        final Attributes attributes = readMainAttributes();

        final String executionEnvironment =
                attributes.getValue("Bundle-RequiredExecutionEnvironment");

        assertThat(executionEnvironment).as(
                "The bundle must stay installable on Eclipse releases running Java 17.")
                .isEqualTo("JavaSE-17");
    }

    private Attributes readMainAttributes() throws IOException
    {
        try (InputStream manifestStream = Files.newInputStream(MANIFEST_PATH))
        {
            final Manifest manifest = new Manifest(manifestStream);
            return manifest.getMainAttributes();
        }
    }
}
