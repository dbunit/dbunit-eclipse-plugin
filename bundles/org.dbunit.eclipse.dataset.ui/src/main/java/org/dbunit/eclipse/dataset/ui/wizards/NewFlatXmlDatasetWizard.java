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

import org.dbunit.eclipse.dataset.ui.DatasetImages;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;

/**
 * The New dbUnit Flat XML Dataset wizard: creates a file that holds an empty flat XML dataset and opens
 * it.
 *
 * @since 1.0.0
 */
public final class NewFlatXmlDatasetWizard extends Wizard implements INewWizard
{
    /**
     * The identifier this wizard is registered under in {@code plugin.xml}.
     */
    public static final String ID = "org.dbunit.eclipse.dataset.ui.newFlatXmlDatasetWizard";

    private IWorkbench workbench;

    private IStructuredSelection selection;

    private NewFlatXmlDatasetPage page;

    /**
     * Creates the wizard, which the workbench then initializes with
     * {@link #init(IWorkbench, IStructuredSelection)}.
     */
    public NewFlatXmlDatasetWizard()
    {
        setWindowTitle(Messages.NewDatasetWizard_windowTitle);
        setDefaultPageImageDescriptor(DatasetImages.getImageDescriptor(DatasetImages.IMG_NEW_DATASET_WIZBAN));
        setNeedsProgressMonitor(true);
    }

    /**
     * Remembers the workbench and the selection, whose first resource proposes the folder of the new file.
     *
     * @param workbench The workbench.
     * @param selection The selection when the wizard started.
     */
    @Override
    public void init(final IWorkbench workbench, final IStructuredSelection selection)
    {
        this.workbench = workbench;
        this.selection = selection;
    }

    /**
     * Adds the page that asks for the folder and name of the new file.
     */
    @Override
    public void addPages()
    {
        page = new NewFlatXmlDatasetPage(selection);
        addPage(page);
    }

    /**
     * Creates the file, selects it in the views of the active workbench window, and opens it in its
     * default editor, which is the dbUnit Dataset Editor unless the user associated another editor with
     * dbUnit flat XML datasets.
     *
     * @return True when the file was created; false when it was not, which keeps the wizard open.
     */
    @Override
    public boolean performFinish()
    {
        final IFile file = page.createNewFile();
        if (file == null)
        {
            return false;
        }
        final IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        BasicNewResourceWizard.selectAndReveal(file, window);
        openEditor(window, file);
        return true;
    }

    private static void openEditor(final IWorkbenchWindow window, final IFile file)
    {
        if (window == null)
        {
            return;
        }
        final IWorkbenchPage workbenchPage = window.getActivePage();
        if (workbenchPage == null)
        {
            return;
        }
        try
        {
            IDE.openEditor(workbenchPage, file, true);
        }
        catch (final PartInitException e)
        {
            ErrorDialog.openError(window.getShell(), Messages.NewDatasetWizard_openErrorTitle, null,
                    e.getStatus());
        }
    }
}
