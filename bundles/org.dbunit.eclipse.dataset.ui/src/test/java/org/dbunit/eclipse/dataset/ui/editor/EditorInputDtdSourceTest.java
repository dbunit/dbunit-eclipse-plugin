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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
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
    void testLoad_whenTheRelativeSystemIdHasASpace_readsTheFile(@TempDir final Path tempDir) throws Exception
    {
        Files.createDirectories(tempDir.resolve("test data"));
        Files.writeString(tempDir.resolve("test data").resolve("my dtd.dtd"), "<!ELEMENT dataset (USERS*)>");
        final EditorInputDtdSource source = new EditorInputDtdSource(
                fileStoreInput(EFS.getStore(tempDir.resolve("dataset.xml").toUri())));

        final Optional<String> loaded = source.load(null, "test data/my dtd.dtd");

        assertThat(loaded).as("An XML parser escapes a space in a system ID and reads the file, so must the "
                + "editor.").contains("<!ELEMENT dataset (USERS*)>");
    }

    @Test
    void testLoad_whenTheRelativeSystemIdHasAnEscapedSpace_readsTheFileWithTheSpace(
            @TempDir final Path tempDir) throws Exception
    {
        Files.createDirectories(tempDir.resolve("test data"));
        Files.writeString(tempDir.resolve("test data").resolve("my.dtd"), "<!ELEMENT dataset (USERS*)>");
        final EditorInputDtdSource source = new EditorInputDtdSource(
                fileStoreInput(EFS.getStore(tempDir.resolve("dataset.xml").toUri())));

        final Optional<String> loaded = source.load(null, "test%20data/my.dtd");

        assertThat(loaded).as("An escape sequence in a system ID is already escaped, and stays so.")
                .contains("<!ELEMENT dataset (USERS*)>");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testLoad_whenTheRelativeSystemIdUsesBackslashesOnWindows_readsTheFile(@TempDir final Path tempDir)
            throws Exception
    {
        Files.createDirectories(tempDir.resolve("dtd"));
        Files.writeString(tempDir.resolve("dtd").resolve("my.dtd"), "<!ELEMENT dataset (USERS*)>");
        final EditorInputDtdSource source = new EditorInputDtdSource(
                fileStoreInput(EFS.getStore(tempDir.resolve("dataset.xml").toUri())));

        final Optional<String> loaded = source.load(null, "dtd\\my.dtd");

        assertThat(loaded).as("On Windows a parser takes a backslash in a system ID for a slash.")
                .contains("<!ELEMENT dataset (USERS*)>");
    }

    @Test
    void testLoad_whenTheFileUriHasASpace_readsTheFile(@TempDir final Path tempDir) throws Exception
    {
        Files.createDirectories(tempDir.resolve("test data"));
        Files.writeString(tempDir.resolve("test data").resolve("my.dtd"), "<!ELEMENT dataset (USERS*)>");
        final EditorInputDtdSource source = new EditorInputDtdSource(
                fileStoreInput(EFS.getStore(tempDir.resolve("dataset.xml").toUri())));

        final Optional<String> loaded = source.load(null, tempDir.toUri() + "test data/my.dtd");

        assertThat(loaded).as("A file: URI with a space is escaped like a relative path.")
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
    void testLoad_whenSystemIdIsAFileUriInAnotherCharset_readsUsingItsDeclaredEncoding(
            @TempDir final Path tempDir) throws Exception
    {
        final Path dtdPath = tempDir.resolve("external.dtd");
        final String dtdText = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<!ELEMENT dataset (USERS*)>\n<!-- café -->\n";
        Files.write(dtdPath, dtdText.getBytes(StandardCharsets.ISO_8859_1));
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, dtdPath.toUri().toString());

            assertThat(loaded)
                    .as("A file: URI system ID must be decoded using its own declared encoding.")
                    .contains(dtdText);
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

    @Test
    void testLoad_whenSystemIdIsAnOpaqueFileUri_returnsEmpty() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, "file:dataset.dtd");

            assertThat(loaded).as("An opaque file: URI must not throw; it must yield no DTD text.")
                    .isEmpty();
        }
    }

    @Test
    void testLoad_whenSystemIdIsAFileUriWithAQuery_returnsEmpty() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, "file:///dtd/dataset.dtd?x=1");

            assertThat(loaded)
                    .as("A file: URI with a query must not throw, on every platform, even though it "
                            + "resolves to no DTD text.")
                    .isEmpty();
        }
    }

    @Test
    void testLoad_whenSystemIdClimbsAboveTheWorkspaceRoot_returnsEmpty() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, "../dataset.dtd");

            assertThat(loaded)
                    .as("A system ID that climbs above the workspace root must not throw.").isEmpty();
        }
    }

    @Test
    void testLoad_whenSystemIdClimbsAboveTheProjectToARealFile_readsItsRealLocationOnDisk()
            throws Exception
    {
        final Path siblingDtd = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath()
                .resolve("editor-input-dtd-source-test-sibling.dtd");
        Files.writeString(siblingDtd, "<!ELEMENT dataset (USERS*)>");
        try
        {
            try (UiTestWorkspace workspace = new UiTestWorkspace())
            {
                final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
                final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

                final Optional<String> loaded =
                        source.load(null, "../" + siblingDtd.getFileName());

                assertThat(loaded)
                        .as("A relative system ID that leaves the project must resolve against the "
                                + "dataset file's real location on disk, not a workspace-relative path.")
                        .contains("<!ELEMENT dataset (USERS*)>");
            }
        }
        finally
        {
            Files.deleteIfExists(siblingDtd);
        }
    }

    @Test
    void testLoad_whenSystemIdIsAnAbsolutePlatformPath_readsTheFile(@TempDir final Path tempDir)
            throws Exception
    {
        final Path dtdPath = tempDir.resolve("my.dtd");
        Files.writeString(dtdPath, "<!ELEMENT dataset (USERS*)>");
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, dtdPath.toString());

            assertThat(loaded).as("An absolute path in the platform's own syntax must be read directly.")
                    .contains("<!ELEMENT dataset (USERS*)>");
        }
    }

    @Test
    void testLoad_whenSystemIdIsAWindowsPathWithAForbiddenCharacter_returnsEmpty() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, "C:\\invalid?name.dtd");

            assertThat(loaded).as("A platform path with a forbidden character must not throw.").isEmpty();
        }
    }

    @Test
    void testLoad_whenTheWorkspaceFilesCharsetOverrideIsUnsupported_ignoresItAndReadsTheFile()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile dtdFile = workspace.createFile("my.dtd", "<!ELEMENT dataset (USERS*)>");
            dtdFile.setCharset("totally-bogus-charset", null);
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, "my.dtd");

            assertThat(loaded)
                    .as("Resolving by the file's real location, like dbUnit's own parser, must not "
                            + "consult a workspace-only charset override that does not apply outside "
                            + "Eclipse.")
                    .contains("<!ELEMENT dataset (USERS*)>");
        }
    }

    @Test
    void testLoad_whenTheDtdFileDoesNotExist_logsAWarningWithTheReason() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                LogRecorder log = new LogRecorder(EditorInputDtdSource.class))
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            source.load(null, "does-not-exist.dtd");

            assertThat(log.statuses()).as("A DTD that cannot be read must be logged once.").hasSize(1);
            final IStatus warning = log.statuses().get(0);
            assertThat(warning).as("The warning must name the DTD.").usingRecursiveComparison()
                    .ignoringFields("exception").isEqualTo(new Status(IStatus.WARNING,
                            "org.dbunit.eclipse.dataset.ui", "The DTD \"does-not-exist.dtd\" could not be read.",
                            null));
            assertThat(warning.getException()).as("The warning must carry the reason the read failed.")
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void testLoad_whenTheSystemIdCannotBeResolved_logsAWarningWithTheReason() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                LogRecorder log = new LogRecorder(EditorInputDtdSource.class))
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            source.load(null, "file:///dtd/dataset.dtd?x=1");

            assertThat(log.statuses()).as("A system ID that cannot be resolved must be logged once.")
                    .hasSize(1);
            final IStatus warning = log.statuses().get(0);
            assertThat(warning).as("The warning must name the system ID.").usingRecursiveComparison()
                    .ignoringFields("exception").isEqualTo(new Status(IStatus.WARNING,
                            "org.dbunit.eclipse.dataset.ui",
                            "The DTD \"file:///dtd/dataset.dtd?x=1\" could not be read.", null));
            assertThat(warning.getException()).as("The warning must carry the reason the resolution failed.")
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void testLoad_whenSystemIdIsANetworkPathReference_refusesItBeforeReadingIt() throws Exception
    {
        assertRefusedBeforeReading("//dtd-host.invalid/share/dataset.dtd");
    }

    @Test
    void testLoad_whenSystemIdIsAFileUriWithAHost_refusesItBeforeReadingIt() throws Exception
    {
        assertRefusedBeforeReading("file://dtd-host.invalid/share/dataset.dtd");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testLoad_whenSystemIdIsAUncPath_refusesItBeforeReadingIt() throws Exception
    {
        assertRefusedBeforeReading("\\\\dtd-host.invalid\\share\\dataset.dtd");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testLoad_whenSystemIdLooksLikeAUncPathWhereABackslashIsNoSeparator_looksForAFileOfThatName()
            throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                LogRecorder log = new LogRecorder(EditorInputDtdSource.class))
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, "\\\\dtd-host.invalid\\share\\dataset.dtd");

            assertThat(loaded).as("No file of that name exists beside the dataset.").isEmpty();
            assertThat(log.statuses()).as("The missing file must be logged once.").hasSize(1);
            assertThat(log.statuses().get(0).getException())
                    .as("Where a backslash is an ordinary character, as the XML parser takes it, the text "
                            + "names a file beside the dataset, and nothing is on a network share.")
                    .isInstanceOf(NoSuchFileException.class);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testLoad_whenTheRelativeSystemIdHasABackslashWhereItIsNoSeparator_readsTheFileWithThatName(
            @TempDir final Path tempDir) throws Exception
    {
        Files.writeString(tempDir.resolve("dtd\\my.dtd"), "<!ELEMENT dataset (USERS*)>");
        final EditorInputDtdSource source = new EditorInputDtdSource(
                fileStoreInput(EFS.getStore(tempDir.resolve("dataset.xml").toUri())));

        final Optional<String> loaded = source.load(null, "dtd\\my.dtd");

        assertThat(loaded)
                .as("Where a backslash is an ordinary character, a system ID with one names a file.")
                .contains("<!ELEMENT dataset (USERS*)>");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testLoad_whenSystemIdIsAFileUriWhosePathIsAUncPath_refusesItBeforeReadingIt() throws Exception
    {
        assertRefusedBeforeReading("file:////dtd-host.invalid/share/dataset.dtd");
    }

    @Test
    void testLoad_whenTheSameDtdFailsAgain_logsItOnlyOnce() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                LogRecorder log = new LogRecorder(EditorInputDtdSource.class))
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            source.load(null, "does-not-exist.dtd");
            source.load(null, "does-not-exist.dtd");

            assertThat(log.statuses())
                    .as("A DTD that stays unreadable must be logged once, not on every reload.").hasSize(1);
        }
    }

    @Test
    void testLoad_whenTheDtdBecomesReadableAndThenFailsAgain_logsTheSecondFailure(
            @TempDir final Path tempDir) throws Exception
    {
        final Path dtdPath = tempDir.resolve("my.dtd");
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                LogRecorder log = new LogRecorder(EditorInputDtdSource.class))
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));
            source.load(null, dtdPath.toString());
            Files.writeString(dtdPath, "<!ELEMENT dataset EMPTY>");
            source.load(null, dtdPath.toString());
            Files.delete(dtdPath);

            source.load(null, dtdPath.toString());

            assertThat(log.statuses()).as("A failure after a successful load must be logged again.")
                    .hasSize(2);
        }
    }

    @Test
    void testLoad_whenTheDtdIsRead_logsNothing() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                LogRecorder log = new LogRecorder(EditorInputDtdSource.class))
        {
            workspace.createFile("my.dtd", "<!ELEMENT dataset (USERS*)>");
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            source.load(null, "my.dtd");

            assertThat(log.statuses()).as("A DTD that is read must not be logged.").isEmpty();
        }
    }

    /**
     * Loads a system ID that names a host and asserts that the editor refused it before the file system
     * opened it. Windows opens a UNC path with a connection to the host, which sends the user's
     * credentials, and a read that failed after that would show as an IOException.
     */
    private static void assertRefusedBeforeReading(final String systemId) throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace();
                LogRecorder log = new LogRecorder(EditorInputDtdSource.class))
        {
            final IFile datasetFile = workspace.createFile("dataset.xml", "<dataset/>");
            final EditorInputDtdSource source = new EditorInputDtdSource(fileInput(datasetFile));

            final Optional<String> loaded = source.load(null, systemId);

            assertThat(loaded).as("A DTD on a network host must yield no DTD text.").isEmpty();
            assertThat(log.statuses()).as("A refused DTD must be logged once.").hasSize(1);
            assertThat(log.statuses().get(0).getException())
                    .as("The editor must refuse a network location itself, not hand it to the file system.")
                    .isInstanceOf(IllegalArgumentException.class);
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
