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

import org.dbunit.eclipse.dataset.core.model.DatasetProblem;

/**
 * The result of scanning a flat XML document once, left to right.
 *
 * @param doctype The parsed DOCTYPE declaration, or null when there is none.
 * @param root The root element, or null when no root element was found.
 * @param elements The root's children, in document order.
 * @param problems The problems found while scanning.
 * @param wellFormed False when a problem that blocks editing was found; root and elements then hold only
 *                    what was parsed before that problem.
 */
record FlatXmlParseResult(FlatXmlDoctype doctype, FlatXmlRoot root, List<FlatXmlElement> elements,
        List<DatasetProblem> problems, boolean wellFormed)
{
}
