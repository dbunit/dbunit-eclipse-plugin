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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.ui.Messages;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.dialogs.WizardNewFileCreationPage;

/**
 * The page of {@link NewFlatXmlDatasetWizard}: the folder and name of the new file, which starts as an
 * empty dataset.
 *
 * @since 1.0.0
 */
final class NewFlatXmlDatasetPage extends WizardNewFileCreationPage
{
    private static final ILog LOG = ILog.of(NewFlatXmlDatasetPage.class);

    static final String PAGE_NAME = "newFlatXmlDatasetPage";

    static final String DEFAULT_FILE_NAME = "dataset.xml";

    private static final String FILE_EXTENSION = "xml";

    NewFlatXmlDatasetPage(final IStructuredSelection selection)
    {
        super(PAGE_NAME, selection);
        setTitle(Messages.NewDatasetWizard_pageTitle);
        setDescription(Messages.NewDatasetWizard_pageDescription);
        setFileName(DEFAULT_FILE_NAME);
        setFileExtension(FILE_EXTENSION);
    }

    @Override
    protected InputStream getInitialContents()
    {
        final String text = FlatXmlDatasetDocument.emptyDatasetText(lineDelimiter());
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the line delimiter for new files in the project of the chosen folder, as the text editors
     * use it: the project's preference, else the workspace's, else the operating system's.
     */
    private String lineDelimiter()
    {
        final IPath containerPath = getContainerFullPath();
        if (containerPath == null || containerPath.segmentCount() == 0)
        {
            return System.lineSeparator();
        }
        final String projectName = containerPath.segment(0);
        final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        try
        {
            return project.getDefaultLineSeparator();
        }
        catch (final CoreException e)
        {
            LOG.log(e.getStatus());
            return System.lineSeparator();
        }
    }
}
