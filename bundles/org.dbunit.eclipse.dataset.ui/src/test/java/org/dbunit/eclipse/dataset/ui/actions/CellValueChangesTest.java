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
package org.dbunit.eclipse.dataset.ui.actions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.edit.CellChange;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlDatasetDocument;
import org.dbunit.eclipse.dataset.core.flatxml.FlatXmlOptions;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.eclipse.jface.text.Document;
import org.eclipse.swt.graphics.Point;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link CellValueChanges}: one change for each cell, with the row of the cell, the name of its column,
 * and the value.
 */
class CellValueChangesTest
{
    @Test
    void testSetting_forCellsOfSeveralRowsAndColumns_returnsOneChangeForEachInTheOrderOfTheCells()
    {
        final DatasetTable table = usersTable();
        final List<Point> cells = List.of(new Point(1, 0), new Point(0, 1), new Point(1, 1));

        final List<CellChange> changes = CellValueChanges.setting(cells, table, "x");

        assertThat(changes).as("Each cell must get its row, its column's name, and the value.")
                .containsExactly(new CellChange(0, "NAME", "x"), new CellChange(1, "ID", "x"),
                        new CellChange(1, "NAME", "x"));
    }

    @Test
    void testSetting_forNullAndForTheEmptyString_keepsTheValueAsGiven()
    {
        final DatasetTable table = usersTable();
        final List<Point> cells = List.of(new Point(0, 0));

        final List<List<CellChange>> changes = List.of(CellValueChanges.setting(cells, table, null),
                CellValueChanges.setting(cells, table, ""));

        assertThat(changes).as("NULL must stay NULL and the empty string must stay the empty string.")
                .containsExactly(List.of(new CellChange(0, "ID", null)),
                        List.of(new CellChange(0, "ID", "")));
    }

    @Test
    void testSetting_forNoCells_returnsNoChanges()
    {
        final List<CellChange> changes = CellValueChanges.setting(List.of(), usersTable(), "x");

        assertThat(changes).as("Without cells there is nothing to change.").isEmpty();
    }

    private static DatasetTable usersTable()
    {
        final String text =
                "<dataset><USERS ID=\"1\" NAME=\"Alice\"/><USERS ID=\"2\" NAME=\"Bob\"/></dataset>";
        final FlatXmlDatasetDocument datasetDocument = new FlatXmlDatasetDocument(new Document(text),
                DtdSource.NONE, FlatXmlOptions.DBUNIT_DEFAULTS, () -> StandardCharsets.UTF_8);
        datasetDocument.refresh();
        return datasetDocument.requireTable("USERS");
    }
}
