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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Guards which problem codes block editing, since the editor and the UI both depend on this set staying
 * exactly these four.
 */
class ProblemCodeTest
{
    @Test
    void testBlocksEditing_whenCheckedForEveryCode_isTrueForExactlyTheFourBlockingCodes()
    {
        final Set<ProblemCode> blocking = EnumSet.noneOf(ProblemCode.class);
        for (final ProblemCode code : ProblemCode.values())
        {
            if (code.blocksEditing())
            {
                blocking.add(code);
            }
        }

        assertThat(blocking).as("Exactly NOT_WELL_FORMED, ROOT_NOT_DATASET, NESTED_ELEMENT, and "
                + "UNSUPPORTED_ENTITY must block editing.")
                .containsExactlyInAnyOrder(ProblemCode.NOT_WELL_FORMED, ProblemCode.ROOT_NOT_DATASET,
                        ProblemCode.NESTED_ELEMENT, ProblemCode.UNSUPPORTED_ENTITY);
    }
}
