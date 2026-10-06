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
package org.dbunit.eclipse.dataset.core.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dbunit.eclipse.dataset.core.model.DatasetColumn;
import org.dbunit.eclipse.dataset.core.model.DatasetModel;
import org.dbunit.eclipse.dataset.core.model.DatasetRow;
import org.dbunit.eclipse.dataset.core.model.DatasetTable;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link TableChanges}: which tables of a model are tables of the model before it under other keys,
 * and which are new.
 */
class TableChangesTest
{
    private static DatasetTable table(final String key, final List<String> columnNames,
            final List<List<String>> rowValues)
    {
        final List<DatasetColumn> columns = new ArrayList<>();
        for (final String columnName : columnNames)
        {
            columns.add(new DatasetColumn(columnName, false, true, false));
        }
        final List<DatasetRow> rows = new ArrayList<>();
        for (final List<String> values : rowValues)
        {
            rows.add(new DatasetRow(values));
        }
        return new DatasetTable(key, key, columns, rows, false);
    }

    private static DatasetTable table(final String key)
    {
        return table(key, List.of("ID"), List.of(List.of("1")));
    }

    private static DatasetModel model(final DatasetTable... tables)
    {
        return new DatasetModel(List.of(tables), List.of(), true);
    }

    @Test
    void testBetween_whenATableHasAnotherKeyAndTheSameContent_isRenamed()
    {
        final DatasetModel before = model(table("USERS"), table("ORDERS"));
        final DatasetModel after = model(table("CUSTOMERS"), table("ORDERS"));

        final TableChanges changes = TableChanges.between(before, after);

        assertThat(changes).as("The table under the new key is the old table, renamed.")
                .isEqualTo(new TableChanges(Map.of("USERS", "CUSTOMERS"), List.of()));
    }

    @Test
    void testBetween_whenTheSameKeyIsInBothModels_isNeitherRenamedNorAdded()
    {
        final DatasetModel before = model(table("USERS", List.of("ID"), List.of(List.of("1"))));
        final DatasetModel after = model(table("USERS", List.of("ID", "NAME"), List.of(List.of("2", "Bob"))));

        final TableChanges changes = TableChanges.between(before, after);

        assertThat(changes).as("A key that both models have is the same table, whatever changed in it.")
                .isEqualTo(TableChanges.NONE);
    }

    @Test
    void testBetween_whenTheTableUnderAnotherKeyHasOtherValues_isAddedNotRenamed()
    {
        final DatasetModel before = model(table("USERS", List.of("ID"), List.of(List.of("1"))));
        final DatasetModel after = model(table("CUSTOMERS", List.of("ID"), List.of(List.of("2"))));

        final TableChanges changes = TableChanges.between(before, after);

        assertThat(changes).as("A table with other values is another table.")
                .isEqualTo(new TableChanges(Map.of(), List.of("CUSTOMERS")));
    }

    @Test
    void testBetween_whenTheTableUnderAnotherKeyHasOtherColumns_isAddedNotRenamed()
    {
        final DatasetModel before = model(table("USERS", List.of("ID"), List.of(List.of("1"))));
        final DatasetModel after = model(table("CUSTOMERS", List.of("NUMBER"), List.of(List.of("1"))));

        final TableChanges changes = TableChanges.between(before, after);

        assertThat(changes).as("A table with other columns is another table, whatever its values are.")
                .isEqualTo(new TableChanges(Map.of(), List.of("CUSTOMERS")));
    }

    @Test
    void testBetween_whenTheTableUnderAnotherKeyHasMoreRows_isAddedNotRenamed()
    {
        final DatasetModel before = model(table("USERS", List.of("ID"), List.of(List.of("1"))));
        final DatasetModel after =
                model(table("CUSTOMERS", List.of("ID"), List.of(List.of("1"), List.of("2"))));

        final TableChanges changes = TableChanges.between(before, after);

        assertThat(changes).as("A table with more rows is another table.")
                .isEqualTo(new TableChanges(Map.of(), List.of("CUSTOMERS")));
    }

    @Test
    void testBetween_whenTheTableUnderAnotherKeyHasOtherColumnsInAnotherOrder_isAddedNotRenamed()
    {
        final DatasetModel before =
                model(table("USERS", List.of("ID", "NAME"), List.of(List.of("1", "Alice"))));
        final DatasetModel after =
                model(table("CUSTOMERS", List.of("NAME", "ID"), List.of(List.of("1", "Alice"))));

        final TableChanges changes = TableChanges.between(before, after);

        assertThat(changes).as("The columns of a table are in order, so another order is another table.")
                .isEqualTo(new TableChanges(Map.of(), List.of("CUSTOMERS")));
    }

    @Test
    void testBetween_whenOnlyTheFlagsOfTheColumnsDiffer_isStillRenamed()
    {
        final DatasetTable declared = new DatasetTable("USERS", "USERS",
                List.of(new DatasetColumn("ID", true, true, false)), List.of(new DatasetRow(List.of("1"))),
                false);
        final DatasetTable plain = table("CUSTOMERS");

        final TableChanges changes = TableChanges.between(model(declared), model(plain));

        assertThat(changes).as("The names of the columns and the rows decide, not what declares the columns.")
                .isEqualTo(new TableChanges(Map.of("USERS", "CUSTOMERS"), List.of()));
    }

    @Test
    void testBetween_whenAValueIsNull_comparesItAsAValue()
    {
        final DatasetModel before =
                model(table("USERS", List.of("ID", "NAME"), List.of(Arrays.asList("1", null))));
        final DatasetModel sameAfter =
                model(table("CUSTOMERS", List.of("ID", "NAME"), List.of(Arrays.asList("1", null))));
        final DatasetModel emptyStringAfter =
                model(table("CUSTOMERS", List.of("ID", "NAME"), List.of(Arrays.asList("1", ""))));

        final TableChanges forNull = TableChanges.between(before, sameAfter);
        final TableChanges forEmptyString = TableChanges.between(before, emptyStringAfter);

        assertThat(List.of(forNull, forEmptyString)).as("NULL equals NULL and is not the empty string.")
                .containsExactly(new TableChanges(Map.of("USERS", "CUSTOMERS"), List.of()),
                        new TableChanges(Map.of(), List.of("CUSTOMERS")));
    }

    @Test
    void testBetween_whenTwoTablesHaveTheSameContentAndBothAreRenamed_pairsThemInOrder()
    {
        final DatasetModel before = model(table("A"), table("B"));
        final DatasetModel after = model(table("X"), table("Y"));

        final TableChanges changes = TableChanges.between(before, after);

        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("A", "X");
        expected.put("B", "Y");
        assertThat(changes).as("Tables that cannot be told apart are paired in the order of the models.")
                .isEqualTo(new TableChanges(expected, List.of()));
    }

    @Test
    void testBetween_whenOneNewTableMatchesTwoGoneTables_isTheRenameOfTheFirstOnly()
    {
        final DatasetModel before = model(table("A"), table("B"));
        final DatasetModel after = model(table("X"));

        final TableChanges changes = TableChanges.between(before, after);

        assertThat(changes).as("A new table is the rename of one old table, the first that it matches.")
                .isEqualTo(new TableChanges(Map.of("A", "X"), List.of()));
    }

    @Test
    void testBetween_whenOneGoneTableMatchesTwoNewTables_isRenamedToTheFirstAndTheOtherIsAdded()
    {
        final DatasetModel before = model(table("A"));
        final DatasetModel after = model(table("X"), table("Y"));

        final TableChanges changes = TableChanges.between(before, after);

        assertThat(changes).as("The first new table that matches is the rename, and the other one is new.")
                .isEqualTo(new TableChanges(Map.of("A", "X"), List.of("Y")));
    }

    @Test
    void testBetween_whenSeveralTablesWereAdded_listsTheirKeysInTheOrderOfTheNewModel()
    {
        final DatasetModel before = model(table("USERS", List.of("ID"), List.of(List.of("1"))));
        final DatasetModel after = model(table("B", List.of("B"), List.of()),
                table("USERS", List.of("ID"), List.of(List.of("1"))), table("A", List.of("A"), List.of()));

        final TableChanges changes = TableChanges.between(before, after);

        assertThat(changes.addedKeys()).as("The keys must come in the order of the new model.")
                .containsExactly("B", "A");
    }

    @Test
    void testBetween_whenTheOldModelHasNoTables_addsEveryTable()
    {
        final DatasetModel after = model(table("USERS"), table("ORDERS"));

        final TableChanges changes = TableChanges.between(DatasetModel.EMPTY, after);

        assertThat(changes).as("Every table of the new model is new.")
                .isEqualTo(new TableChanges(Map.of(), List.of("USERS", "ORDERS")));
    }

    @Test
    void testBetween_whenTheNewModelHasNoTables_changesNothing()
    {
        final TableChanges changes = TableChanges.between(model(table("USERS")), DatasetModel.EMPTY);

        assertThat(changes).as("A table that is gone is neither renamed nor added.")
                .isEqualTo(TableChanges.NONE);
    }

    @Test
    void testBetween_whenBothModelsAreTheSame_changesNothing()
    {
        final DatasetModel same = model(table("USERS"), table("ORDERS"));

        assertThat(TableChanges.between(same, same)).as("Nothing changed.").isEqualTo(TableChanges.NONE);
    }

    @Test
    void testBetween_whenAModelHasTwoTablesWithOneKey_countsTheFirstOnly()
    {
        final DatasetModel before = model(table("USERS", List.of("ID"), List.of(List.of("1"))));
        final DatasetModel after = model(table("CUSTOMERS", List.of("ID"), List.of(List.of("1"))),
                table("CUSTOMERS", List.of("ID"), List.of(List.of("2"))));

        final TableChanges changes = TableChanges.between(before, after);

        assertThat(changes).as("Of two tables with one key, the first is the one that is shown.")
                .isEqualTo(new TableChanges(Map.of("USERS", "CUSTOMERS"), List.of()));
    }

    @Test
    void testConstructor_whenTheGivenCollectionsChangeAfterwards_keepsWhatItWasGiven()
    {
        final Map<String, String> renamedKeys = new LinkedHashMap<>(Map.of("A", "X"));
        final List<String> addedKeys = new ArrayList<>(List.of("Y"));
        final TableChanges changes = new TableChanges(renamedKeys, addedKeys);

        renamedKeys.put("B", "Z");
        addedKeys.add("W");

        assertThat(changes).as("A later change of the given collections must not change the changes.")
                .isEqualTo(new TableChanges(Map.of("A", "X"), List.of("Y")));
    }

    @Test
    void testAccessors_whenAReturnedCollectionIsModified_throws()
    {
        final TableChanges changes = new TableChanges(Map.of("A", "X"), List.of("Y"));

        assertThatThrownBy(() -> changes.renamedKeys().put("B", "Z"))
                .as("The returned renames must not let a caller change the changes.")
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> changes.addedKeys().add("W"))
                .as("The returned added keys must not let a caller change the changes.")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testNone_whenRead_hasNoRenamedAndNoAddedTable()
    {
        assertThat(List.of(TableChanges.NONE.renamedKeys().size(), TableChanges.NONE.addedKeys().size()))
                .as("NONE must have no renames and no added tables.").containsExactly(0, 0);
    }
}
