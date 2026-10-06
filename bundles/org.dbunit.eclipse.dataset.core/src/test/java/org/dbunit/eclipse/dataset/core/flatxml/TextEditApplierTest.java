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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link TextEditApplier} on plain {@link Document}s: how edits are applied, joined, and rejected,
 * and how batches group changes into undo steps.
 */
class TextEditApplierTest
{
    private static final String DIGITS = "0123456789";

    private static final String LONG_TEXT =
            "abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz";

    @FunctionalInterface
    private interface UndoManagerConsumer
    {
        void accept(IDocumentUndoManager undoManager) throws Exception;
    }

    private static final class DocumentChangeCounter implements IDocumentListener
    {
        private int count;

        private final List<Integer> replacedLengths = new ArrayList<>();

        @Override
        public void documentAboutToBeChanged(final DocumentEvent event)
        {
            // Only the changes themselves are counted.
        }

        @Override
        public void documentChanged(final DocumentEvent event)
        {
            count++;
            replacedLengths.add(event.getLength());
        }
    }

    private static void withUndoManager(final IDocument document, final UndoManagerConsumer consumer)
            throws Exception
    {
        DocumentUndoManagerRegistry.connect(document);
        final IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(TextEditApplierTest.class);
        try
        {
            consumer.accept(undoManager);
        }
        finally
        {
            undoManager.disconnect(TextEditApplierTest.class);
            DocumentUndoManagerRegistry.disconnect(document);
        }
    }

    private static List<TextEdit> insertsAtEveryOffsetFromZeroTo(final int lastOffset)
    {
        final List<TextEdit> edits = new ArrayList<>();
        for (int offset = 0; offset <= lastOffset; offset++)
        {
            edits.add(new InsertEdit(offset, "-"));
        }
        return edits;
    }

    private static String textWithADashBeforeEachOffsetFromZeroTo(final String text, final int lastOffset)
    {
        final StringBuilder expected = new StringBuilder();
        for (int offset = 0; offset < text.length(); offset++)
        {
            if (offset <= lastOffset)
            {
                expected.append('-');
            }
            expected.append(text.charAt(offset));
        }
        return expected.toString();
    }

    @Test
    void testApply_whenGivenOneEdit_changesTheText()
    {
        final IDocument document = new Document(DIGITS);

        new TextEditApplier().apply(document, List.of(new ReplaceEdit(1, 2, "XY")));

        assertThat(document.get()).as("The replaced range must hold the new text.").isEqualTo("0XY3456789");
    }

    @Test
    void testApply_whenGivenEditsOfEveryKind_appliesAllOfThemAgainstTheOriginalOffsets()
    {
        final IDocument document = new Document(DIGITS);
        final List<TextEdit> edits =
                List.of(new InsertEdit(0, "<"), new ReplaceEdit(2, 2, "__"), new DeleteEdit(6, 2));

        new TextEditApplier().apply(document, edits);

        assertThat(document.get()).as("Each edit must use the offsets of the text before any edit.")
                .isEqualTo("<01__4589");
    }

    @Test
    void testApply_whenGivenExactlyFiftyEdits_appliesAllOfThem()
    {
        final String text = LONG_TEXT;
        final IDocument document = new Document(text);

        new TextEditApplier().apply(document, insertsAtEveryOffsetFromZeroTo(49));

        assertThat(document.get()).as("Fifty edits are still applied one by one, with the same result.")
                .isEqualTo(textWithADashBeforeEachOffsetFromZeroTo(text, 49));
    }

    @Test
    void testApply_whenGivenMoreThanFiftyEdits_changesTheDocumentOnce()
    {
        final String text = LONG_TEXT;
        final IDocument document = new Document(text);
        final DocumentChangeCounter changes = new DocumentChangeCounter();
        document.addDocumentListener(changes);

        new TextEditApplier().apply(document, insertsAtEveryOffsetFromZeroTo(50));

        assertThat(document.get()).as("Joining the edits must not change the result.")
                .isEqualTo(textWithADashBeforeEachOffsetFromZeroTo(text, 50));
        assertThat(changes.count).as("More than fifty edits must change the document once.").isEqualTo(1);
    }

    @Test
    void testApply_whenMoreThanFiftyEditsAreFarApart_changesOnlyTheTextOfEachRunOfNearbyEdits()
    {
        final String text = "a".repeat(300_000);
        final IDocument document = new Document(text);
        final DocumentChangeCounter changes = new DocumentChangeCounter();
        document.addDocumentListener(changes);
        final List<TextEdit> edits = new ArrayList<>();
        final StringBuilder expected = new StringBuilder(text);
        for (int offset = 250_050; offset >= 250_000; offset--)
        {
            expected.insert(offset, '-');
        }
        for (int offset = 50; offset >= 0; offset--)
        {
            expected.insert(offset, '-');
        }
        for (int offset = 0; offset <= 50; offset++)
        {
            edits.add(new InsertEdit(offset, "-"));
            edits.add(new InsertEdit(250_000 + offset, "-"));
        }

        new TextEditApplier().apply(document, edits);

        assertThat(document.get()).as("Splitting the edits into runs must not change the result.")
                .isEqualTo(expected.toString());
        assertThat(changes.replacedLengths)
                .as("The two runs of edits must each replace a short span, not the 250000 characters "
                        + "between them.")
                .hasSize(2).allMatch(length -> length <= 60);
    }

    @Test
    void testApply_whenMoreThanFiftyEditsAreSeparatedByAShortGap_changesTheDocumentOnce()
    {
        final String text = "a".repeat(100_000);
        final IDocument document = new Document(text);
        final DocumentChangeCounter changes = new DocumentChangeCounter();
        document.addDocumentListener(changes);
        final List<TextEdit> edits = new ArrayList<>();
        for (int index = 0; index <= 50; index++)
        {
            edits.add(new InsertEdit(index * 1_000, "-"));
        }

        new TextEditApplier().apply(document, edits);

        assertThat(changes.count).as("Edits that are close together are joined into one change.")
                .isEqualTo(1);
    }

    @Test
    void testApply_whenJoinedEditsOverlap_throwsIllegalStateException()
    {
        final IDocument document = new Document(LONG_TEXT);
        final List<TextEdit> edits = new ArrayList<>();
        edits.add(new ReplaceEdit(0, 5, "x"));
        edits.add(new ReplaceEdit(3, 4, "y"));
        for (int offset = 10; offset < 60; offset++)
        {
            edits.add(new InsertEdit(offset, "-"));
        }

        assertThatThrownBy(() -> new TextEditApplier().apply(document, edits))
                .as("Joining overlapping edits must be refused.").isInstanceOf(IllegalStateException.class)
                .hasMessage("Computed text edits overlap.");
    }

    @Test
    void testApply_whenFewEditsOverlap_throwsMalformedTreeException()
    {
        final IDocument document = new Document(DIGITS);
        final List<TextEdit> edits = List.of(new ReplaceEdit(0, 5, "x"), new ReplaceEdit(3, 4, "y"));

        assertThatThrownBy(() -> new TextEditApplier().apply(document, edits))
                .as("Combining overlapping edits must be refused by the edit tree.")
                .isInstanceOf(MalformedTreeException.class);
    }

    @Test
    void testApply_whenAnEditIsBeyondTheEndOfTheDocument_throwsIllegalStateException()
    {
        final IDocument document = new Document(DIGITS);

        assertThatThrownBy(() -> new TextEditApplier().apply(document, List.of(new InsertEdit(100, "x"))))
                .as("An edit outside the document must be reported as one that does not fit.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Computed text edits do not fit the document.");
    }

    @Test
    void testApply_whenJoinedEditsAreBeyondTheEndOfTheDocument_throwsIllegalStateException()
    {
        final IDocument document = new Document(DIGITS);

        assertThatThrownBy(() -> new TextEditApplier().apply(document, insertsAtEveryOffsetFromZeroTo(60)))
                .as("Joined edits outside the document must be reported as ones that do not fit.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Computed text edits do not fit the document.");
    }

    @Test
    void testApply_whenJoinedEditsHoldAnUnsupportedKind_throwsIllegalStateException()
    {
        final IDocument document = new Document(LONG_TEXT);
        final List<TextEdit> edits = new ArrayList<>(insertsAtEveryOffsetFromZeroTo(50));
        edits.add(new MultiTextEdit(0, 0));

        assertThatThrownBy(() -> new TextEditApplier().apply(document, edits))
                .as("Only replace, insert, and delete edits can be joined.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported text edit org.eclipse.text.edits.MultiTextEdit.");
    }

    @Test
    void testApply_whenNotInABatch_isOneUndoStepPerCall() throws Exception
    {
        final IDocument document = new Document(DIGITS);
        withUndoManager(document, undoManager ->
        {
            final TextEditApplier applier = new TextEditApplier();
            applier.apply(document, List.of(new InsertEdit(0, "a")));
            applier.apply(document, List.of(new InsertEdit(0, "b")));

            undoManager.undo();

            assertThat(document.get()).as("One undo must revert only the last call.")
                    .isEqualTo("a" + DIGITS);
        });
    }

    @Test
    void testApply_whenSeveralEditsAreGiven_isOneUndoStep() throws Exception
    {
        final IDocument document = new Document(DIGITS);
        withUndoManager(document, undoManager ->
        {
            new TextEditApplier().apply(document,
                    List.of(new InsertEdit(0, "<"), new ReplaceEdit(2, 2, "__"), new DeleteEdit(6, 2)));

            undoManager.undo();

            assertThat(document.get()).as("One undo must revert all the edits of a call.").isEqualTo(DIGITS);
        });
    }

    @Test
    void testBatch_whenAppliedTwiceInside_isOneUndoStep() throws Exception
    {
        final IDocument document = new Document(DIGITS);
        withUndoManager(document, undoManager ->
        {
            final TextEditApplier applier = new TextEditApplier();

            applier.batch(document, () ->
            {
                applier.apply(document, List.of(new InsertEdit(0, "a")));
                applier.apply(document, List.of(new InsertEdit(0, "b")));
            });

            assertThat(document.get()).as("Both calls inside the batch must be applied.")
                    .isEqualTo("ba" + DIGITS);
            undoManager.undo();
            assertThat(document.get()).as("One undo must revert the whole batch.").isEqualTo(DIGITS);
        });
    }

    @Test
    void testBatch_whenNested_isOneUndoStepAndTheNextCallStartsItsOwn() throws Exception
    {
        final IDocument document = new Document(DIGITS);
        withUndoManager(document, undoManager ->
        {
            final TextEditApplier applier = new TextEditApplier();

            applier.batch(document, () ->
            {
                applier.apply(document, List.of(new InsertEdit(0, "a")));
                applier.batch(document, () -> applier.apply(document, List.of(new InsertEdit(0, "b"))));
                applier.apply(document, List.of(new InsertEdit(0, "c")));
            });
            applier.apply(document, List.of(new InsertEdit(0, "d")));

            undoManager.undo();
            assertThat(document.get()).as("The call after the batch must be a step of its own.")
                    .isEqualTo("cba" + DIGITS);
            undoManager.undo();
            assertThat(document.get()).as("An inner batch must not end the outer one.").isEqualTo(DIGITS);
        });
    }

    @Test
    void testBatch_whenTheOperationsThrow_endsTheBatchAndRethrows() throws Exception
    {
        final IDocument document = new Document(DIGITS);
        withUndoManager(document, undoManager ->
        {
            final TextEditApplier applier = new TextEditApplier();

            assertThatThrownBy(() -> applier.batch(document, () ->
            {
                applier.apply(document, List.of(new InsertEdit(0, "a")));
                throw new IllegalArgumentException("failed");
            })).as("The exception of the operations must reach the caller.")
                    .isInstanceOf(IllegalArgumentException.class);
            applier.apply(document, List.of(new InsertEdit(0, "b")));

            undoManager.undo();
            assertThat(document.get()).as("After a failed batch, the next call must be a step of its own.")
                    .isEqualTo("a" + DIGITS);
            undoManager.undo();
            assertThat(document.get()).as("The failed batch's change must be one step too.")
                    .isEqualTo(DIGITS);
        });
    }

    @Test
    void testBatch_whenNoUndoManagerIsConnected_runsTheOperations()
    {
        final IDocument document = new Document(DIGITS);
        final TextEditApplier applier = new TextEditApplier();

        applier.batch(document, () -> applier.apply(document, List.of(new InsertEdit(0, "a"))));

        assertThat(document.get()).as("The operations must run without an undo manager.")
                .isEqualTo("a" + DIGITS);
    }
}
