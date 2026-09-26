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

    /**
     * An edit is rejected because the source has errors that block editing.
     */
    public static String Edit_sourceHasErrors;

    /**
     * An edit names a table that does not exist; {0} is the table key.
     */
    public static String Edit_noSuchTable;

    /**
     * A cell change names a row that does not exist; {0} is the row index, {1} the table.
     */
    public static String Edit_noSuchRow;

    /**
     * An edit names a column that does not exist; {0} is the column, {1} the table.
     */
    public static String Edit_noSuchColumn;

    /**
     * A cell change would leave a row without values; {0} is the row index, {1} the table.
     */
    public static String Edit_rowWouldBeEmpty;

    /**
     * Rows cannot be inserted into a table without columns; {0} is the table.
     */
    public static String Edit_tableHasNoColumns;

    /**
     * A row index is out of range; {0} is the row index, {1} the table.
     */
    public static String Edit_rowOutOfRange;

    /**
     * A new row has the wrong number of values; {0} is the number of columns, {1} the table.
     */
    public static String Edit_wrongValueCount;

    /**
     * A block of rows to move is out of range; {0} is the table.
     */
    public static String Edit_rowBlockOutOfRange;

    /**
     * A block of rows can move only one position at a time.
     */
    public static String Edit_moveByOnePosition;

    /**
     * A block of rows cannot move above the first row; {0} is the table.
     */
    public static String Edit_moveBeforeFirstRow;

    /**
     * A block of rows cannot move below the last row; {0} is the table.
     */
    public static String Edit_moveAfterLastRow;

    /**
     * A column name is not an XML name; {0} is the name.
     */
    public static String Edit_invalidColumnName;

    /**
     * A table already has a column of that name; {0} is the table, {1} the column.
     */
    public static String Edit_columnExists;

    /**
     * A column cannot be renamed while a row has two attributes for it; {0} is the column, {1} the table.
     */
    public static String Edit_renameColumnWithCaseVariants;

    /**
     * A column cannot be deleted because a row would have no values left; {0} is the column, {1} the table.
     */
    public static String Edit_deleteColumnWouldEmptyRow;

    /**
     * A table name is not an XML name; {0} is the name.
     */
    public static String Edit_invalidTableName;

    /**
     * A table name is the name of the root element; {0} is the name.
     */
    public static String Edit_reservedTableName;

    /**
     * A table of that name already exists; {0} is the name.
     */
    public static String Edit_tableExists;

    /**
     * An empty dataset can only be created in a blank document.
     */
    public static String Edit_documentNotBlank;

    /**
     * A value has a character that XML 1.0 does not allow; {0} is its code point in hexadecimal.
     */
    public static String Codec_notXmlCharacter;

    /**
     * An ampersand in an attribute value does not start a reference.
     */
    public static String Codec_invalidAmpersand;

    /**
     * An entity reference other than the predefined ones; {0} is the entity name.
     */
    public static String Codec_unsupportedEntity;

    /**
     * A character reference without digits.
     */
    public static String Codec_referenceWithoutDigits;

    /**
     * A character reference with invalid digits; {0} is the digits.
     */
    public static String Codec_invalidReference;

    /**
     * A character reference beyond the last Unicode code point.
     */
    public static String Codec_referenceTooLarge;

    /**
     * A character reference to a character that XML 1.0 does not allow; {0} is its code point in
     * hexadecimal.
     */
    public static String Codec_referenceNotXmlCharacter;

    /**
     * A parser message with the position it refers to; {0} is the message, {1} the line, {2} the column.
     */
    public static String Parser_position;

    /**
     * The document has no root element.
     */
    public static String Parser_noRootElement;

    /**
     * A character that is not markup precedes the root element.
     */
    public static String Parser_unexpectedBeforeRoot;

    /**
     * The root element is not named dataset; {0} is its name.
     */
    public static String Parser_rootNotDataset;

    /**
     * Content other than comments and processing instructions follows the root element.
     */
    public static String Parser_unexpectedAfterRoot;

    /**
     * The document ends inside a start tag.
     */
    public static String Parser_endedInStartTag;

    /**
     * An element name is not followed by whitespace, '/>', or '>'.
     */
    public static String Parser_expectedAfterElementName;

    /**
     * Two attributes are not separated by whitespace.
     */
    public static String Parser_expectedWhitespaceBetweenAttributes;

    /**
     * An element repeats an attribute; {0} is the attribute.
     */
    public static String Parser_duplicateAttribute;

    /**
     * An attribute name is not followed by '='.
     */
    public static String Parser_expectedEquals;

    /**
     * An attribute's '=' is not followed by a quoted value.
     */
    public static String Parser_expectedQuotedValue;

    /**
     * An attribute value contains '<'.
     */
    public static String Parser_lessThanInValue;

    /**
     * An attribute value has no closing quote.
     */
    public static String Parser_unclosedValue;

    /**
     * The document ends before an element's end tag; {0} is the element name.
     */
    public static String Parser_endedBeforeEndTag;

    /**
     * An element is nested inside a row element.
     */
    public static String Parser_nestedElement;

    /**
     * An end tag has no closing '>'; {0} is the element name.
     */
    public static String Parser_unclosedEndTag;

    /**
     * An end tag does not match its start tag; {0} is the expected name, {1} the found one.
     */
    public static String Parser_mismatchedEndTag;

    /**
     * Text between elements, which dbUnit ignores.
     */
    public static String Parser_textIgnored;

    /**
     * A DOCTYPE lacks the root name.
     */
    public static String Parser_expectedDoctypeName;

    /**
     * A DOCTYPE has no closing '>'.
     */
    public static String Parser_unclosedDoctype;

    /**
     * A DOCTYPE lacks a quoted literal.
     */
    public static String Parser_expectedQuotedLiteral;

    /**
     * A DOCTYPE literal has no closing quote.
     */
    public static String Parser_unclosedLiteral;

    /**
     * A literal in a DOCTYPE's internal subset has no closing quote.
     */
    public static String Parser_unclosedSubsetLiteral;

    /**
     * A DOCTYPE's internal subset has no closing ']'.
     */
    public static String Parser_unclosedSubset;

    /**
     * A name is expected.
     */
    public static String Parser_expectedName;

    /**
     * A processing instruction has no closing '?>'.
     */
    public static String Parser_unclosedProcessingInstruction;

    /**
     * A comment has no closing '-->'.
     */
    public static String Parser_unclosedComment;

    /**
     * A CDATA section has no closing ']]>'.
     */
    public static String Parser_unclosedCdata;

    /**
     * A DTD's parameter entity declarations are ignored.
     */
    public static String Dtd_parameterEntityDeclarationsIgnored;

    /**
     * A DTD's conditional sections are ignored.
     */
    public static String Dtd_conditionalSectionsIgnored;

    /**
     * A DTD's parameter entity references are ignored.
     */
    public static String Dtd_parameterEntityReferencesIgnored;

    static
    {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {
    }
}
