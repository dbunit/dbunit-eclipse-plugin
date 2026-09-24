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
package org.dbunit.eclipse.dataset.ui.grid;

import org.eclipse.nebula.widgets.nattable.config.AbstractRegistryConfiguration;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.EditableRule;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.edit.EditConfigAttributes;
import org.eclipse.nebula.widgets.nattable.edit.config.DialogErrorHandling;
import org.eclipse.nebula.widgets.nattable.edit.editor.MultiLineTextCellEditor;
import org.eclipse.nebula.widgets.nattable.edit.editor.TextCellEditor;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;

/**
 * Registers how a {@link DatasetGrid}'s cells are displayed and edited.
 *
 * @since 1.0.0
 */
final class GridEditConfiguration extends AbstractRegistryConfiguration
{
    private static final String MULTI_LINE_VALUE = "MULTI_LINE_VALUE";

    private final DatasetGridContext context;

    GridEditConfiguration(final DatasetGridContext context)
    {
        this.context = context;
    }

    @Override
    public void configureRegistry(final IConfigRegistry configRegistry)
    {
        configRegistry.registerConfigAttribute(EditConfigAttributes.CELL_EDITABLE_RULE, new EditableRule()
        {
            @Override
            public boolean isEditable(final int columnIndex, final int rowIndex)
            {
                return context.isEditable();
            }
        });
        configRegistry.registerConfigAttribute(EditConfigAttributes.CELL_EDITOR,
                new TextCellEditor(true, true), DisplayMode.EDIT);
        configRegistry.registerConfigAttribute(EditConfigAttributes.CELL_EDITOR,
                new MultiLineTextCellEditor(false), DisplayMode.EDIT, MULTI_LINE_VALUE);
        configRegistry.registerConfigAttribute(EditConfigAttributes.OPEN_IN_DIALOG, Boolean.TRUE,
                DisplayMode.EDIT, MULTI_LINE_VALUE);
        configRegistry.registerConfigAttribute(EditConfigAttributes.DATA_VALIDATOR,
                new XmlCharacterValidator(), DisplayMode.EDIT);
        configRegistry.registerConfigAttribute(EditConfigAttributes.VALIDATION_ERROR_HANDLER,
                new DialogErrorHandling());
        configRegistry.registerConfigAttribute(EditConfigAttributes.OPEN_ADJACENT_EDITOR, Boolean.FALSE);
        // Without this, NatTable edits the next cell after Enter or Tab commits and moves the selection.
        configRegistry.registerConfigAttribute(EditConfigAttributes.ACTIVATE_EDITOR_ON_TRAVERSAL, Boolean.FALSE);
        configRegistry.registerConfigAttribute(EditConfigAttributes.SUPPORT_MULTI_EDIT, Boolean.FALSE);
        // Only body cells hold dataset values; the headers and the corner keep NatTable's default converter.
        configRegistry.registerConfigAttribute(CellConfigAttributes.DISPLAY_CONVERTER,
                new NullAwareDisplayConverter(context), DisplayMode.NORMAL, GridRegion.BODY);
        configRegistry.registerConfigAttribute(CellConfigAttributes.DISPLAY_CONVERTER,
                new EditDisplayConverter(), DisplayMode.EDIT, GridRegion.BODY);
    }
}
