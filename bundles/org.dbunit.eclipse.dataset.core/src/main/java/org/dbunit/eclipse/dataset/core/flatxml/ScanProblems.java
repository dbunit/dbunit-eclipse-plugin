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

import java.util.ArrayList;
import java.util.List;

import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.osgi.util.NLS;

/**
 * The problems found by one scan of flat XML text: the informational ones for content that the scan
 * ignores, and the blocking one that ends the scan, whose message names the line and column of its offset.
 * Each instance serves a single scan.
 */
final class ScanProblems
{
    private final CharSequence text;

    private final int length;

    private final List<DatasetProblem> problems = new ArrayList<>();

    private boolean wellFormed = true;

    ScanProblems(final CharSequence text)
    {
        this.text = text;
        this.length = text.length();
    }

    /**
     * Returns the problems found so far, in the order they were found.
     *
     * @return An unmodifiable copy of the problems.
     */
    List<DatasetProblem> toList()
    {
        return List.copyOf(problems);
    }

    /**
     * Tells whether the scan has found no blocking problem.
     *
     * @return False once a blocking problem has been recorded.
     */
    boolean isWellFormed()
    {
        return wellFormed;
    }

    void addInfoProblem(final ProblemCode code, final String message, final int offset,
            final int problemLength)
    {
        problems.add(new DatasetProblem(code, ProblemSeverity.INFO, message, null, null, -1, offset,
                problemLength));
    }

    /**
     * Records a blocking problem at the given offset.
     *
     * @return The exception that the caller throws to stop the scan.
     */
    StopScanException blockingError(final ProblemCode code, final String message,
            final int offset)
    {
        return blockingError(code, message, offset, null);
    }

    /**
     * Records a blocking problem at the given offset, remembering the exception behind it.
     *
     * @return The exception that the caller throws to stop the scan.
     */
    StopScanException blockingError(final ProblemCode code, final String message,
            final int offset, final Throwable cause)
    {
        problems.add(new DatasetProblem(code, ProblemSeverity.ERROR, withLocation(message, offset),
                null, null, -1, offset, 0));
        wellFormed = false;
        return new StopScanException(cause);
    }

    private String withLocation(final String message, final int offset)
    {
        int line = 1;
        int column = 1;
        int index = 0;
        final int end = Math.min(offset, length);
        while (index < end)
        {
            final char ch = text.charAt(index);
            if (ch == '\n')
            {
                line++;
                column = 1;
                index++;
            }
            else if (ch == '\r')
            {
                line++;
                column = 1;
                index++;
                if (index < end && text.charAt(index) == '\n')
                {
                    index++;
                }
            }
            else
            {
                column++;
                index++;
            }
        }
        return NLS.bind(Messages.Parser_position, new Object[] { message, line, column });
    }
}
