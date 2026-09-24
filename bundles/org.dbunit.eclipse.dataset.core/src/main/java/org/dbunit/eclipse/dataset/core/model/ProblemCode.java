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
package org.dbunit.eclipse.dataset.core.model;

/**
 * The kind of a dataset problem.
 *
 * @since 1.0.0
 */
public enum ProblemCode
{
    /**
     * The XML is not well-formed; the message has the details.
     */
    NOT_WELL_FORMED(true),

    /**
     * The root element is missing or is not {@code dataset}.
     */
    ROOT_NOT_DATASET(true),

    /**
     * An element is nested inside a row element.
     */
    NESTED_ELEMENT(true),

    /**
     * An entity reference other than one of the five predefined entities was used.
     */
    UNSUPPORTED_ENTITY(true),

    /**
     * A column has values in a later row but not in the table's first element, without a DTD and
     * without column sensing.
     */
    COLUMN_NOT_IN_FIRST_ROW(false),

    /**
     * The table's first element has no attributes, but a later row has values.
     */
    FIRST_ELEMENT_WITHOUT_ATTRIBUTES(false),

    /**
     * One table is spelled with more than one letter case.
     */
    TABLE_NAME_CASE_VARIANTS(false),

    /**
     * One column of a table is spelled with more than one letter case.
     */
    COLUMN_NAME_CASE_VARIANTS(false),

    /**
     * An element has two attributes whose names differ only in case; dbUnit uses the value of the last
     * one.
     */
    DUPLICATE_COLUMN_IN_ROW(false),

    /**
     * A table that has elements is not declared in the DTD, so dbUnit fails to load the dataset.
     */
    TABLE_NOT_DECLARED_IN_DTD(false),

    /**
     * A row has an attribute that the DTD does not declare for the table; dbUnit ignores the value, or,
     * with column sensing, fails to load the dataset.
     */
    COLUMN_NOT_DECLARED_IN_DTD(false),

    /**
     * The DTD's {@code dataset} content model names an element that the DTD declares with neither an
     * element type nor an attribute list, so dbUnit fails to load the dataset.
     */
    DTD_TABLE_WITHOUT_DECLARATION(false),

    /**
     * The DOCTYPE's external DTD could not be read.
     */
    DTD_NOT_LOADED(false),

    /**
     * A parameter entity or a conditional section in the DTD was ignored.
     */
    UNSUPPORTED_DTD_CONSTRUCT(false),

    /**
     * Text or CDATA content inside {@code dataset} or a row element was ignored.
     */
    TEXT_CONTENT_IGNORED(false),

    /**
     * An element without attributes was found in a table that has rows, and it is not the table's first
     * element.
     */
    REDUNDANT_EMPTY_ELEMENT(false);

    private final boolean blocksEditing;

    ProblemCode(final boolean blocksEditing)
    {
        this.blocksEditing = blocksEditing;
    }

    /**
     * Returns whether this kind of problem blocks editing.
     *
     * @return True when the source cannot be edited while a problem of this kind exists.
     */
    public boolean blocksEditing()
    {
        return blocksEditing;
    }
}
