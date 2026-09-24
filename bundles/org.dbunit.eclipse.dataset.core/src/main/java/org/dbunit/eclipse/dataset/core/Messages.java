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
package org.dbunit.eclipse.dataset.core;

import org.eclipse.osgi.util.NLS;

/**
 * Externalized, user-visible strings of the dataset core bundle.
 *
 * @since 1.0.0
 */
public final class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.dbunit.eclipse.dataset.core.messages";

    /**
     * A column has a value in a later row but is missing from the table's first element.
     */
    public static String Validator_columnNotInFirstRow;

    /**
     * The table's first element has no attributes, but a later row has values.
     */
    public static String Validator_firstElementWithoutAttributes;

    /**
     * A table is spelled with more than one letter case.
     */
    public static String Validator_tableNameCaseVariants;

    /**
     * A column of a table is spelled with more than one letter case.
     */
    public static String Validator_columnNameCaseVariants;

    /**
     * An element has two attributes whose names differ only in case.
     */
    public static String Validator_duplicateColumnInRow;

    /**
     * A table that has elements is not declared in the DTD.
     */
    public static String Validator_tableNotDeclaredInDtd;

    /**
     * A row has an attribute the DTD does not declare for the table, without column sensing.
     */
    public static String Validator_columnNotDeclaredInDtdWarning;

    /**
     * A row has an attribute the DTD does not declare for the table, with column sensing.
     */
    public static String Validator_columnNotDeclaredInDtdError;

    /**
     * The DTD content model names an element the DTD does not declare.
     */
    public static String Validator_dtdTableWithoutDeclaration;

    /**
     * The DOCTYPE's external DTD could not be read.
     */
    public static String Validator_dtdNotLoaded;

    /**
     * An empty element in a table that has rows, other than the table's first element.
     */
    public static String Validator_redundantEmptyElement;

    static
    {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {
    }
}
