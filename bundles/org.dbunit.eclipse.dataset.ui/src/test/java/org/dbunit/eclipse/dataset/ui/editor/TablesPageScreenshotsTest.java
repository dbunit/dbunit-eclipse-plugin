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

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.intro.IIntroManager;
import org.eclipse.ui.intro.IIntroPart;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Captures {@link TablesPage} for a handful of states as PNGs under {@code target/screenshots}, for visual
 * review after a build. Each case only has to open without throwing; the value is the saved image, not an
 * assertion, so review the PNGs after changing anything these states render.
 */
class TablesPageScreenshotsTest
{
    @BeforeAll
    static void closeIntro()
    {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        final IIntroManager introManager = workbench.getIntroManager();
        final IIntroPart intro = introManager.getIntro();
        if (intro != null)
        {
            introManager.closeIntro(intro);
        }
        UiTestWorkspace.processEvents();
    }

    @Test
    void captureEmptyFile() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("empty.xml", "");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            capture(editor, "empty-file.png");
        }
    }

    @Test
    void captureReadOnlyEmptyFile() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openReadOnlyExternalFile("");

            capture(editor, "read-only-empty-file.png");
        }
    }

    @Test
    void captureNoTables() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset/>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            capture(editor, "no-tables.png");
        }
    }

    @Test
    void captureTablesWithData() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset>"
                            + "<USERS ID=\"1\" NAME=\"Alice\" EMAIL=\"alice@example.com\"/>"
                            + "<USERS ID=\"2\" NAME=\"Bob\" EMAIL=\"bob@example.com\"/>"
                            + "<ORDERS ID=\"100\" USER_ID=\"1\" TOTAL=\"19.99\"/>"
                            + "</dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            capture(editor, "tables-with-data.png");
        }
    }

    @Test
    void captureBlockingError() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("broken.xml", "<dataset><USERS ID=\"1\"</dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            capture(editor, "blocking-error.png");
        }
    }

    @Test
    void captureTableWithNoRows() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<!DOCTYPE dataset [\n<!ELEMENT dataset (USERS*,ORDERS*)>\n<!ELEMENT USERS EMPTY>\n"
                            + "<!ATTLIST USERS ID CDATA #REQUIRED NAME CDATA #REQUIRED>\n"
                            + "<!ELEMENT ORDERS EMPTY>\n<!ATTLIST ORDERS ID CDATA #REQUIRED>\n]>\n"
                            + "<dataset>\n<USERS ID=\"1\" NAME=\"Alice\"/>\n</dataset>\n");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);
            selectTab(editor.getTablesPage().getTabFolder(), "ORDERS");

            capture(editor, "table-with-no-rows.png");
        }
    }

    @Test
    void captureProblemsWarning() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            capture(editor, "problems-warning.png");
        }
    }

    private static void selectTab(final CTabFolder tabFolder, final String tableName)
    {
        for (final CTabItem item : tabFolder.getItems())
        {
            if (item.getText().equals(tableName))
            {
                tabFolder.setSelection(item);
                return;
            }
        }
    }

    private static void capture(final FlatXmlDatasetEditor editor, final String fileName) throws Exception
    {
        final Composite page = (Composite) editor.getTablesPage().getControl();
        page.setSize(800, 600);
        page.layout(true, true);
        UiTestWorkspace.processEvents();

        final Point size = page.getSize();
        final Image image = new Image(page.getDisplay(), size.x, size.y);
        try
        {
            final GC gc = new GC(image);
            try
            {
                page.print(gc);
            }
            finally
            {
                gc.dispose();
            }
            final Path screenshotDirectory = screenshotDirectory();
            Files.createDirectories(screenshotDirectory);
            final ImageLoader loader = new ImageLoader();
            loader.data = new ImageData[] { image.getImageData() };
            loader.save(screenshotDirectory.resolve(fileName).toString(), SWT.IMAGE_PNG);
        }
        finally
        {
            image.dispose();
        }
    }

    /**
     * Returns {@code target/screenshots}, derived from the workspace instance location
     * ({@code target/work/data}, see the workbench log path in CLAUDE.md's troubleshooting section) so the
     * output lands in the Maven build output directory without hard-coding an absolute path.
     */
    private static Path screenshotDirectory()
    {
        final Path workspaceLocation = Path.of(Platform.getLocation().toOSString());
        return workspaceLocation.getParent().getParent().resolve("screenshots");
    }
}
