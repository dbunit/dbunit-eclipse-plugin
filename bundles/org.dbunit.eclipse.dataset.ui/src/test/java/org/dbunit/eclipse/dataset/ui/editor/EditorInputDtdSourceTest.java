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
package org.dbunit.eclipse.dataset.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link EditorInputDtdSource} against the DTD Resolution rules.
 */
class EditorInputDtdSourceTest
{
    @Test
    void testLoad_whenSystemIdIsARelativeWorkspacePath_readsTheWorkspaceFile() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            workspace.createFile("my.dtd", "<!ELEMENT dataset (USERS*)>");
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, "my.dtd");

            assertThat(loaded)
                    .as("A relative system ID must resolve against the dataset file's folder.")
                    .contains("<!ELEMENT dataset (USERS*)>");
        }
    }

    @Test
    void testLoad_whenSystemIdIsARelativeReferenceFromANonWorkspaceInput_readsThroughItsFileStore(
            @TempDir final Path tempDir) throws Exception
    {
        Files.writeString(tempDir.resolve("my.dtd"), "<!ELEMENT dataset (USERS*)>");
        final IFileStore datasetStore = EFS.getStore(tempDir.resolve("dataset.xml").toUri());
        final EditorInputDtdSource source = new EditorInputDtdSource(fileStoreInput(datasetStore));

        final Optional<String> loaded = source.load(null, "my.dtd");

        assertThat(loaded)
                .as("A relative system ID must resolve against a non-workspace input's own location.")
                .contains("<!ELEMENT dataset (USERS*)>");
    }

    @Test
    void testLoad_whenSystemIdIsAFileUri_readsThatFile(@TempDir final Path tempDir) throws Exception
    {
        final Path dtdPath = tempDir.resolve("external.dtd");
        Files.writeString(dtdPath, "<!ELEMENT dataset (USERS*)>");
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, dtdPath.toUri().toString());

            assertThat(loaded).as("A file: URI system ID must be read directly.")
                    .contains("<!ELEMENT dataset (USERS*)>");
        }
    }

    @Test
    void testLoad_whenSystemIdIsHttp_returnsEmpty() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, "http://example.com/my.dtd");

            assertThat(loaded).as("The editor must never read an http: system ID.").isEmpty();
        }
    }

    @Test
    void testLoad_whenTheFileDoesNotExist_returnsEmpty() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, "does-not-exist.dtd");

            assertThat(loaded).as("A missing file must yield no DTD text.").isEmpty();
        }
    }

    private static IEditorInput fileInput(final IFile file)
    {
        return minimalInput(file.getName(), adapterClass -> adapterClass == IFile.class ? file : null);
    }

    private static IEditorInput fileStoreInput(final IFileStore store)
    {
        return minimalInput(store.getName(),
                adapterClass -> adapterClass == IFileStore.class ? store : null);
    }

    private static IEditorInput minimalInput(final String name, final Function<Class<?>, Object> adapters)
    {
        return new IEditorInput()
        {
            @Override
            public boolean exists()
            {
                return true;
            }

            @Override
            public ImageDescriptor getImageDescriptor()
            {
                return ImageDescriptor.getMissingImageDescriptor();
            }

            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public IPersistableElement getPersistable()
            {
                return null;
            }

            @Override
            public String getToolTipText()
            {
                return name;
            }

            @Override
            public <T> T getAdapter(final Class<T> adapterClass)
            {
                return adapterClass.cast(adapters.apply(adapterClass));
            }
        };
    }
}
