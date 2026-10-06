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
package org.dbunit.eclipse.dataset.core.flatxml;

import java.util.List;
import java.util.Optional;

import org.dbunit.eclipse.dataset.core.model.CellAddress;
import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

/**
 * Finds where the cells of a parsed dataset are in its text, and which cell a text offset belongs to. It
 * reads the model and the index of one parse, so it must be created for the parse that its offsets refer to.
 */
final class CellLocator
{
    private final DatasetModel model;

    private final FlatXmlIndex index;

    private final FlatXmlOptions options;

    CellLocator(final DatasetModel model, final FlatXmlIndex index, final FlatXmlOptions options)
    {
        this.model = model;
        this.index = index;
        this.options = options;
    }

    /**
     * Locates a cell or a row in the text.
     *
     * @param address The cell to locate, or a row when its column index is -1.
     * @return The attribute value range of the cell, the element name range when the cell is NULL, or the
     *         element range for a row address; empty when the address does not exist.
     */
    Optional<IRegion> locate(final CellAddress address)
    {
        final Optional<DatasetTable> maybeTable = model.findTable(address.tableKey());
        if (maybeTable.isEmpty())
        {
            return Optional.empty();
        }
        final DatasetTable table = maybeTable.get();
        final List<FlatXmlElement> rowElements = index.getRowElements(address.tableKey());
        if (address.rowIndex() < 0 || address.rowIndex() >= rowElements.size())
        {
            return Optional.empty();
        }
        final FlatXmlElement element = rowElements.get(address.rowIndex());
        if (address.columnIndex() < 0)
        {
            return Optional.of(new Region(element.offset(), element.endOffset() - element.offset()));
        }
        if (address.columnIndex() >= table.getColumns().size())
        {
            return Optional.empty();
        }
        final String columnName = table.getColumns().get(address.columnIndex()).name();
        final FlatXmlAttribute attribute = findAttribute(element, columnName);
        if (attribute == null)
        {
            return Optional.of(new Region(element.offset() + 1,
                    element.nameEndOffset() - element.offset() - 1));
        }
        return Optional.of(
                new Region(attribute.valueOffset(), attribute.valueEndOffset() - attribute.valueOffset()));
    }

    /**
     * Finds the cell at a text offset.
     *
     * @param offset The offset to look up.
     * @return The attribute under the offset, or the row's first column when the offset is not inside an
     *         attribute; empty when the offset is not inside a row element.
     */
    Optional<CellAddress> cellAt(final int offset)
    {
        final FlatXmlElement element = findElementContaining(offset);
        if (element == null)
        {
            return Optional.empty();
        }
        final String key = options.tableKey(element.name());
        final List<FlatXmlElement> rowElements = index.getRowElements(key);
        final int rowIndex = rowElements.indexOf(element);
        if (rowIndex < 0)
        {
            return Optional.empty();
        }
        final Optional<DatasetTable> table = model.findTable(key);
        if (table.isEmpty())
        {
            return Optional.empty();
        }
        final int columnIndex = columnIndexAt(table.get(), element, offset);
        return Optional.of(new CellAddress(key, rowIndex, columnIndex));
    }

    private static int columnIndexAt(final DatasetTable table, final FlatXmlElement element, final int offset)
    {
        int columnIndex = 0;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            if (offset >= attribute.nameOffset() && offset < attribute.endOffset())
            {
                columnIndex = Math.max(0, table.getColumnIndex(attribute.name()));
            }
        }
        return columnIndex;
    }

    private FlatXmlElement findElementContaining(final int offset)
    {
        final List<FlatXmlElement> elements = index.getElements();
        int low = 0;
        int high = elements.size() - 1;
        while (low <= high)
        {
            final int middle = (low + high) >>> 1;
            final FlatXmlElement element = elements.get(middle);
            if (offset < element.offset())
            {
                high = middle - 1;
            }
            else if (offset >= element.endOffset())
            {
                low = middle + 1;
            }
            else
            {
                return element;
            }
        }
        return null;
    }

    private static FlatXmlAttribute findAttribute(final FlatXmlElement element, final String columnName)
    {
        final String columnKey = DatasetColumn.keyOf(columnName);
        FlatXmlAttribute found = null;
        for (final FlatXmlAttribute attribute : element.attributes())
        {
            if (attribute.key().equals(columnKey))
            {
                found = attribute;
            }
        }
        return found;
    }
}
