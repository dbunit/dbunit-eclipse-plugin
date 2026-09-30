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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.dbunit.eclipse.dataset.core.DatasetCore;
import org.dbunit.eclipse.dataset.core.Messages;
import org.dbunit.eclipse.dataset.core.dtd.DtdDeclarations;
import org.dbunit.eclipse.dataset.core.dtd.DtdSource;
import org.dbunit.eclipse.dataset.core.dtd.DtdTable;
import org.dbunit.eclipse.dataset.core.model.DatasetProblem;
import org.dbunit.eclipse.dataset.core.model.ProblemCode;
import org.dbunit.eclipse.dataset.core.model.ProblemSeverity;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DtdResolver}: what a DOCTYPE resolves to, the caching of external DTDs, the reload of the
 * cache, and the warnings for a source that fails.
 */
class DtdResolverTest
{
    private static final String INTERNAL_SUBSET =
            "<!ELEMENT dataset (USERS*)><!ELEMENT USERS EMPTY><!ATTLIST USERS ID CDATA #REQUIRED>";

    private static final String EXTERNAL_DTD = "<!ATTLIST USERS NAME CDATA #IMPLIED>";

    private static final FlatXmlDoctype EXTERNAL_DOCTYPE =
            new FlatXmlDoctype("dataset", null, "my.dtd", INTERNAL_SUBSET, 30, 0, 130);

    private static FlatXmlDoctype internalOnly(final String subset, final int subsetOffset)
    {
        return new FlatXmlDoctype("dataset", null, null, subset, subsetOffset, 0, subsetOffset + 50);
    }

    private static DtdResolver resolverFor(final DtdSource source)
    {
        return new DtdResolver(source, status ->
        {
            // These tests do not look at the log.
        });
    }

    private static DatasetProblem entityDeclarationInfo(final int offset, final int length)
    {
        return new DatasetProblem(ProblemCode.UNSUPPORTED_DTD_CONSTRUCT, ProblemSeverity.INFO,
                Messages.Dtd_parameterEntityDeclarationsIgnored, null, null, -1, offset, length);
    }

    private static List<DtdTable> tablesOf(final DtdResolution resolution)
    {
        final DtdDeclarations declarations = resolution.declarations();
        return declarations.tables();
    }

    @Test
    void testResolve_whenThereIsNoDoctype_returnsNoDeclarationsInTheNoneState()
    {
        final DtdResolution resolution = resolverFor(DtdSource.NONE).resolve(null);

        assertThat(resolution).as("A document without DOCTYPE has no declarations.")
                .isEqualTo(new DtdResolution(null, DtdState.NONE));
    }

    @Test
    void testResolve_whenTheDoctypeHasAnInternalSubsetOnly_readsItAndIsLoaded()
    {
        final DtdResolution resolution =
                resolverFor(DtdSource.NONE).resolve(internalOnly(INTERNAL_SUBSET, 20));

        assertThat(resolution.state()).as("A DOCTYPE without a system identifier is complete.")
                .isEqualTo(DtdState.LOADED);
        assertThat(tablesOf(resolution)).as("The tables must come from the internal subset.")
                .containsExactly(new DtdTable("USERS", List.of("ID")));
    }

    @Test
    void testResolve_whenTheDoctypeHasNothingToRead_isLoadedWithoutTables()
    {
        final DtdResolution resolution = resolverFor(DtdSource.NONE).resolve(internalOnly(null, -1));

        assertThat(resolution.state()).as("A bare DOCTYPE is complete.").isEqualTo(DtdState.LOADED);
        assertThat(tablesOf(resolution)).as("A bare DOCTYPE declares no tables.").isEmpty();
    }

    @Test
    void testResolve_whenTheInternalSubsetHasProblems_movesThemToTheirPlaceInTheDocument()
    {
        final String subset = "<!ENTITY % e \"x\">" + INTERNAL_SUBSET;

        final DtdResolution resolution = resolverFor(DtdSource.NONE).resolve(internalOnly(subset, 25));

        assertThat(resolution.declarations().getProblems())
                .as("A problem's offset must be relative to the document, not to the subset.")
                .containsExactly(entityDeclarationInfo(25, "<!ENTITY % e \"x\">".length()));
    }

    @Test
    void testResolve_whenTheDoctypeNamesAnExternalDtd_mergesItAfterTheInternalSubset()
    {
        final DtdSource source = (publicId, systemId) -> Optional.of(EXTERNAL_DTD);

        final DtdResolution resolution = resolverFor(source).resolve(EXTERNAL_DOCTYPE);

        assertThat(resolution.state()).as("The DTD was loaded.").isEqualTo(DtdState.LOADED);
        assertThat(tablesOf(resolution)).as("The external columns must follow the internal ones.")
                .containsExactly(new DtdTable("USERS", List.of("ID", "NAME")));
    }

    @Test
    void testResolve_whenTheExternalDtdHasProblems_pointsThemAtTheDoctype()
    {
        final DtdSource source = (publicId, systemId) -> Optional.of("<!ENTITY % e \"x\">");

        final DtdResolution resolution = resolverFor(source).resolve(EXTERNAL_DOCTYPE);

        assertThat(resolution.declarations().getProblems())
                .as("A problem in a DTD that is not open must point at the DOCTYPE.")
                .containsExactly(entityDeclarationInfo(0, 130));
    }

    @Test
    void testResolve_whenTheSourceFindsNoDtd_isNotLoadedButKeepsTheInternalSubset()
    {
        final DtdResolution resolution = resolverFor(DtdSource.NONE).resolve(EXTERNAL_DOCTYPE);

        assertThat(resolution.state()).as("A DTD that cannot be read leaves the declarations incomplete.")
                .isEqualTo(DtdState.NOT_LOADED);
        assertThat(tablesOf(resolution)).as("The internal subset must still be read.")
                .containsExactly(new DtdTable("USERS", List.of("ID")));
    }

    @Test
    void testResolve_whenTheSourceThrows_isNotLoadedAndLogsAWarning()
    {
        final IllegalStateException failure = new IllegalStateException("broken");
        final List<IStatus> statuses = new ArrayList<>();
        final DtdResolver resolver = new DtdResolver((publicId, systemId) ->
        {
            throw failure;
        }, statuses::add);

        final DtdResolution resolution = resolver.resolve(EXTERNAL_DOCTYPE);

        assertThat(resolution.state()).as("A failing source is treated as one that finds nothing.")
                .isEqualTo(DtdState.NOT_LOADED);
        assertThat(statuses).as("The failure must be logged once, with its cause.")
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(new Status(IStatus.WARNING, DatasetCore.PLUGIN_ID,
                        "The DTD source failed to load the DTD \"my.dtd\", so it is treated as not found.",
                        failure));
    }

    @Test
    void testResolve_whenTheSameDtdIsResolvedTwice_loadsItOnce()
    {
        final AtomicInteger loads = new AtomicInteger();
        final DtdSource source = (publicId, systemId) ->
        {
            loads.incrementAndGet();
            return Optional.of(EXTERNAL_DTD);
        };
        final DtdResolver resolver = resolverFor(source);

        resolver.resolve(EXTERNAL_DOCTYPE);
        resolver.resolve(EXTERNAL_DOCTYPE);

        assertThat(loads.get()).as("The second resolution must use the cached text.").isEqualTo(1);
    }

    @Test
    void testResolve_whenTheFirstLoadFoundNothing_doesNotAskAgain()
    {
        final AtomicInteger loads = new AtomicInteger();
        final DtdSource source = (publicId, systemId) ->
        {
            loads.incrementAndGet();
            return Optional.empty();
        };
        final DtdResolver resolver = resolverFor(source);

        resolver.resolve(EXTERNAL_DOCTYPE);
        resolver.resolve(EXTERNAL_DOCTYPE);

        assertThat(loads.get()).as("A DTD that was not found is cached as not found until a reload.")
                .isEqualTo(1);
    }

    @Test
    void testReload_whenNothingWasResolved_returnsFalse()
    {
        final boolean changed = resolverFor(DtdSource.NONE).reload();

        assertThat(changed).as("An empty cache has nothing that could change.").isFalse();
    }

    @Test
    void testReload_whenTheTextIsUnchanged_returnsFalse()
    {
        final DtdResolver resolver = resolverFor((publicId, systemId) -> Optional.of(EXTERNAL_DTD));
        resolver.resolve(EXTERNAL_DOCTYPE);

        final boolean changed = resolver.reload();

        assertThat(changed).as("The same text must not count as a change.").isFalse();
    }

    @Test
    void testReload_whenTheTextChanged_returnsTrueAndTheNextResolutionSeesTheNewText()
    {
        final AtomicReference<String> text = new AtomicReference<>(EXTERNAL_DTD);
        final DtdResolver resolver = resolverFor((publicId, systemId) -> Optional.of(text.get()));
        resolver.resolve(EXTERNAL_DOCTYPE);
        text.set("<!ATTLIST USERS EMAIL CDATA #IMPLIED>");

        final boolean changed = resolver.reload();

        assertThat(changed).as("A different text must count as a change.").isTrue();
        assertThat(tablesOf(resolver.resolve(EXTERNAL_DOCTYPE)))
                .as("The cache must hold the reloaded text.")
                .containsExactly(new DtdTable("USERS", List.of("ID", "EMAIL")));
    }

    @Test
    void testReload_whenTheDtdBecameReadable_returnsTrue()
    {
        final AtomicReference<Optional<String>> text = new AtomicReference<>(Optional.empty());
        final DtdResolver resolver = resolverFor((publicId, systemId) -> text.get());
        resolver.resolve(EXTERNAL_DOCTYPE);
        text.set(Optional.of(EXTERNAL_DTD));

        final boolean changed = resolver.reload();

        assertThat(changed).as("A DTD that can be read now must count as a change.").isTrue();
        assertThat(resolver.resolve(EXTERNAL_DOCTYPE).state()).as("The DTD is loaded after the reload.")
                .isEqualTo(DtdState.LOADED);
    }

    @Test
    void testReload_whenTheDtdBecameUnreadable_returnsTrue()
    {
        final AtomicReference<Optional<String>> text = new AtomicReference<>(Optional.of(EXTERNAL_DTD));
        final DtdResolver resolver = resolverFor((publicId, systemId) -> text.get());
        resolver.resolve(EXTERNAL_DOCTYPE);
        text.set(Optional.empty());

        final boolean changed = resolver.reload();

        assertThat(changed).as("A DTD that can no longer be read must count as a change.").isTrue();
        assertThat(resolver.resolve(EXTERNAL_DOCTYPE).state())
                .as("The DTD is not loaded after the reload.").isEqualTo(DtdState.NOT_LOADED);
    }

    @Test
    void testReload_whenTheSourceThrows_treatsTheDtdAsNotFoundAndLogsAWarning()
    {
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        final List<IStatus> statuses = new ArrayList<>();
        final DtdResolver resolver = new DtdResolver((publicId, systemId) ->
        {
            if (failure.get() != null)
            {
                throw failure.get();
            }
            return Optional.of(EXTERNAL_DTD);
        }, statuses::add);
        resolver.resolve(EXTERNAL_DOCTYPE);
        failure.set(new IllegalStateException("broken"));

        final boolean changed = resolver.reload();

        assertThat(changed).as("A DTD that failed to reload counts as one that became unreadable.").isTrue();
        assertThat(statuses).as("The failure must be logged.").hasSize(1);
    }
}
