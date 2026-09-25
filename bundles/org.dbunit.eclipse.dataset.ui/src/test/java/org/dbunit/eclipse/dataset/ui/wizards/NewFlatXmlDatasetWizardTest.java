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
package org.dbunit.eclipse.dataset.ui.wizards;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.dbunit.eclipse.dataset.ui.editor.FlatXmlDatasetEditor;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWizard;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.wizards.IWizardDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Tests {@link NewFlatXmlDatasetWizard} through its registration and by finishing it in a wizard dialog.
 */
class NewFlatXmlDatasetWizardTest
{
    private static final String FLAT_XML_CONTENT_TYPE_ID = "org.dbunit.eclipse.dataset.flatxml";

    private static final int DELETE_ATTEMPTS = 10;

    private static final long DELETE_RETRY_DELAY_MILLIS = 200;

    private IProject project;

    private WizardDialog dialog;

    @BeforeEach
    void createProject() throws CoreException
    {
        project = ResourcesPlugin.getWorkspace().getRoot()
                .getProject("new-dataset-wizard-test-" + UUID.randomUUID());
        project.create(null);
        project.open(null);
    }

    @AfterEach
    void closeDialogAndEditorsAndDeleteProject() throws CoreException, InterruptedException
    {
        if (dialog != null)
        {
            dialog.close();
        }
        activePage().closeAllEditors(false);
        processEvents();
        deleteProject();
    }

    /**
     * Deletes the test project, retrying for a while, because Windows may keep a file that was just
     * written open for a moment, for example while a virus scanner reads it.
     */
    private void deleteProject() throws CoreException, InterruptedException
    {
        for (int attempt = 1;; attempt++)
        {
            try
            {
                project.delete(true, true, null);
                return;
            }
            catch (final CoreException e)
            {
                if (attempt == DELETE_ATTEMPTS)
                {
                    throw e;
                }
                Thread.sleep(DELETE_RETRY_DELAY_MILLIS);
                processEvents();
            }
        }
    }

    @Test
    void testFindWizard_inTheNewWizardRegistry_isInTheDbUnitCategoryAndCreatesThisWizard()
            throws CoreException
    {
        final IWizardDescriptor descriptor =
                PlatformUI.getWorkbench().getNewWizardRegistry().findWizard(NewFlatXmlDatasetWizard.ID);

        assertThat(descriptor).as("The wizard must be registered as a new wizard.").isNotNull();
        assertThat(descriptor.getCategory().getLabel()).as("The wizard must be in the dbUnit category.")
                .isEqualTo("dbUnit");
        final IWorkbenchWizard wizard = descriptor.createWizard();
        try
        {
            assertThat(wizard).as("The registered wizard must be the new flat XML dataset wizard.")
                    .isInstanceOf(NewFlatXmlDatasetWizard.class);
        }
        finally
        {
            wizard.dispose();
        }
    }

    @Test
    void testPerformFinish_withTheDefaultFileName_createsAnEmptyDatasetAndOpensItInTheDatasetEditor()
            throws Exception
    {
        setProjectLineDelimiter("\n");
        final NewFlatXmlDatasetWizard wizard = createWizardInDialog();

        final boolean finished = wizard.performFinish();
        processEvents();

        assertThat(finished).as("Finishing the wizard must succeed.").isTrue();
        final IFile file = project.getFile(NewFlatXmlDatasetPage.DEFAULT_FILE_NAME);
        assertThat(read(file)).as("The new file must hold the XML declaration and an empty dataset.")
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dataset>\n</dataset>\n");
        assertThat(file.getContentDescription().getContentType().getId())
                .as("The new file must be detected as a flat XML dataset.")
                .isEqualTo(FLAT_XML_CONTENT_TYPE_ID);
        final IEditorPart editor = activePage().getActiveEditor();
        assertThat(editor).as("The new file must open in the dataset editor.")
                .isInstanceOf(FlatXmlDatasetEditor.class);
        assertThat(editor.getEditorInput().getAdapter(IFile.class)).as("The editor must edit the new file.")
                .isEqualTo(file);
    }

    @Test
    void testPerformFinish_withANameWithoutExtensionAndACrLfProject_createsAnXmlFileWithCrLf()
            throws Exception
    {
        setProjectLineDelimiter("\r\n");
        final NewFlatXmlDatasetWizard wizard = createWizardInDialog();
        page(wizard).setFileName("orders");

        wizard.performFinish();
        processEvents();

        final IFile file = project.getFile("orders.xml");
        assertThat(file.exists()).as("The wizard must add the xml extension to the file name.").isTrue();
        assertThat(read(file)).as("The new file must use the project's line delimiter.")
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<dataset>\r\n</dataset>\r\n");
    }

    /**
     * Initializes a wizard with the test project selected and creates its dialog and page controls, as
     * opening it from File &gt; New does, without blocking on the dialog.
     */
    private NewFlatXmlDatasetWizard createWizardInDialog()
    {
        final NewFlatXmlDatasetWizard wizard = new NewFlatXmlDatasetWizard();
        wizard.init(PlatformUI.getWorkbench(), new StructuredSelection(project));
        dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
        dialog.create();
        return wizard;
    }

    private static NewFlatXmlDatasetPage page(final NewFlatXmlDatasetWizard wizard)
    {
        return (NewFlatXmlDatasetPage) wizard.getPage(NewFlatXmlDatasetPage.PAGE_NAME);
    }

    private void setProjectLineDelimiter(final String lineDelimiter) throws BackingStoreException
    {
        final IEclipsePreferences preferences = new ProjectScope(project).getNode(Platform.PI_RUNTIME);
        preferences.put(Platform.PREF_LINE_SEPARATOR, lineDelimiter);
        preferences.flush();
    }

    private static String read(final IFile file) throws CoreException, IOException
    {
        try (InputStream contents = file.getContents())
        {
            return new String(contents.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static IWorkbenchPage activePage()
    {
        return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    }

    private static void processEvents()
    {
        final Display display = Display.getCurrent() != null ? Display.getCurrent() : Display.getDefault();
        while (display.readAndDispatch())
        {
            // Drain pending SWT events so asynchronous workbench work completes.
        }
    }
}
