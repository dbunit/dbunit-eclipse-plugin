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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;

/**
 * A disposable workspace project for editor tests: creates files, opens them, drains pending SWT events,
 * then closes every editor it opened and deletes the project and the files it created outside it.
 */
final class UiTestWorkspace implements AutoCloseable
{
    private final IProject project;

    private final List<IEditorPart> openedEditors = new ArrayList<>();

    private final List<Path> externalFiles = new ArrayList<>();

    UiTestWorkspace() throws CoreException
    {
        project = ResourcesPlugin.getWorkspace().getRoot()
                .getProject("dataset-editor-test-" + UUID.randomUUID());
        project.create(null);
        project.open(null);
    }

    IFile createFile(final String name, final String content) throws CoreException
    {
        return createFile(name, content, StandardCharsets.UTF_8);
    }

    IFile createFile(final String name, final String content, final Charset charset) throws CoreException
    {
        final IFile file = project.getFile(name);
        file.create(new ByteArrayInputStream(content.getBytes(charset)), true, null);
        return file;
    }

    IEditorPart open(final IFile file) throws PartInitException
    {
        final IEditorPart editor = IDE.openEditor(activePage(), file);
        openedEditors.add(editor);
        processEvents();
        return editor;
    }

    /**
     * Creates a read-only file outside the workspace, as the editor gets for a file that it must not change,
     * and opens it in the dataset editor.
     */
    IEditorPart openReadOnlyExternalFile(final String content) throws IOException, PartInitException
    {
        final Path file = Files.createTempFile("dataset-editor-test-", ".xml");
        externalFiles.add(file);
        Files.writeString(file, content);
        file.toFile().setReadOnly();
        final IFileStore fileStore = EFS.getLocalFileSystem().getStore(file.toUri());
        final IEditorPart editor = activePage().openEditor(new FileStoreEditorInput(fileStore),
                FlatXmlDatasetEditor.ID);
        openedEditors.add(editor);
        processEvents();
        return editor;
    }

    static void processEvents()
    {
        final Display display = Display.getCurrent() != null ? Display.getCurrent() : Display.getDefault();
        while (display.readAndDispatch())
        {
            // Drain pending SWT events so asynchronous editor and workbench work completes.
        }
    }

    private static IWorkbenchPage activePage()
    {
        return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    }

    @Override
    public void close() throws CoreException, IOException
    {
        final IWorkbenchPage page = activePage();
        for (final IEditorPart editor : openedEditors)
        {
            page.closeEditor(editor, false);
        }
        processEvents();
        project.delete(true, true, null);
        for (final Path file : externalFiles)
        {
            file.toFile().setWritable(true);
            Files.delete(file);
        }
    }
}
