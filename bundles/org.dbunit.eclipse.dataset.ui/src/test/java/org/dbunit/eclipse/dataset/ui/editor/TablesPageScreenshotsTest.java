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
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Captures {@link TablesPage} for a handful of states as PNGs under {@code target/screenshots}, for visual
 * review after a build. Each case asserts that its image was saved and is not empty; the value is the
 * image itself, so review the PNGs after changing anything these states render. The build directory comes
 * from the {@code dbunit.build.directory} system property that the bundle's pom.xml sets; without it, as
 * in a PDE JUnit launch, the images go to a temporary directory. The class closes the intro for the
 * captures and opens it again afterwards, so that the test classes that run after it find the workbench as
 * they would without it.
 */
class TablesPageScreenshotsTest
{
    private static final String BUILD_DIRECTORY_PROPERTY = "dbunit.build.directory";

    private static boolean introClosed;

    private static boolean introWasInStandby;

    private static Path screenshotDirectory;

    @BeforeAll
    static void closeIntro()
    {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        final IIntroManager introManager = workbench.getIntroManager();
        final IIntroPart intro = introManager.getIntro();
        if (intro != null)
        {
            introWasInStandby = introManager.isIntroStandby(intro);
            introClosed = introManager.closeIntro(intro);
        }
        UiTestWorkspace.processEvents();
    }

    @BeforeAll
    static void createScreenshotDirectory() throws IOException
    {
        final String buildDirectory = System.getProperty(BUILD_DIRECTORY_PROPERTY);
        if (buildDirectory == null)
        {
            screenshotDirectory = Files.createTempDirectory("dbunit-screenshots");
        }
        else
        {
            screenshotDirectory = Files.createDirectories(Path.of(buildDirectory, "screenshots"));
        }
    }

    @AfterAll
    static void reopenIntro()
    {
        if (introClosed)
        {
            final IWorkbench workbench = PlatformUI.getWorkbench();
            final IIntroManager introManager = workbench.getIntroManager();
            introManager.showIntro(workbench.getActiveWorkbenchWindow(), introWasInStandby);
            UiTestWorkspace.processEvents();
            assertThat(introManager.getIntro())
                    .as("The intro must be open again for the test classes that run after this one.")
                    .isNotNull();
        }
    }

    @Test
    void testCapture_whenTheFileIsEmpty_savesANonEmptyImage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("empty.xml", "");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openInDatasetEditor(file);

            final Path screenshot = capture(editor, "empty-file.png");

            assertThat(screenshot).as("The screenshot of an empty file must be saved and not be empty.")
                    .exists().isNotEmptyFile();
        }
    }

    @Test
    void testCapture_whenTheEmptyFileIsReadOnly_savesANonEmptyImage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.openReadOnlyExternalFile("");

            final Path screenshot = capture(editor, "read-only-empty-file.png");

            assertThat(screenshot)
                    .as("The screenshot of a read-only empty file must be saved and not be empty.").exists()
                    .isNotEmptyFile();
        }
    }

    @Test
    void testCapture_whenTheDatasetHasNoTables_savesANonEmptyImage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml", "<dataset/>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            final Path screenshot = capture(editor, "no-tables.png");

            assertThat(screenshot)
                    .as("The screenshot of a dataset without tables must be saved and not be empty.")
                    .exists().isNotEmptyFile();
        }
    }

    @Test
    void testCapture_whenTheTablesHaveData_savesANonEmptyImage() throws Exception
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

            final Path screenshot = capture(editor, "tables-with-data.png");

            assertThat(screenshot)
                    .as("The screenshot of tables with data must be saved and not be empty.").exists()
                    .isNotEmptyFile();
        }
    }

    @Test
    void testCapture_whenTheSourceHasABlockingError_savesANonEmptyImage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("broken.xml", "<dataset><USERS ID=\"1\"</dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            final Path screenshot = capture(editor, "blocking-error.png");

            assertThat(screenshot)
                    .as("The screenshot of a source with a blocking error must be saved and not be empty.")
                    .exists().isNotEmptyFile();
        }
    }

    @Test
    void testCapture_whenADtdTableHasNoRows_savesANonEmptyImage() throws Exception
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

            final Path screenshot = capture(editor, "table-with-no-rows.png");

            assertThat(screenshot)
                    .as("The screenshot of a DTD table without rows must be saved and not be empty.")
                    .exists().isNotEmptyFile();
        }
    }

    @Test
    void testCapture_whenTheDatasetHasProblems_savesANonEmptyImage() throws Exception
    {
        try (UiTestWorkspace workspace = new UiTestWorkspace())
        {
            final IFile file = workspace.createFile("dataset.xml",
                    "<dataset><USERS ID=\"1\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>");
            final FlatXmlDatasetEditor editor = (FlatXmlDatasetEditor) workspace.open(file);

            final Path screenshot = capture(editor, "problems-warning.png");

            assertThat(screenshot)
                    .as("The screenshot of a dataset with problems must be saved and not be empty.").exists()
                    .isNotEmptyFile();
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

    private static Path capture(final FlatXmlDatasetEditor editor, final String fileName) throws Exception
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
            final Path screenshot = screenshotDirectory.resolve(fileName);
            final ImageLoader loader = new ImageLoader();
            loader.data = new ImageData[] { image.getImageData() };
            loader.save(screenshot.toString(), SWT.IMAGE_PNG);
            return screenshot;
        }
        finally
        {
            image.dispose();
        }
    }
}
