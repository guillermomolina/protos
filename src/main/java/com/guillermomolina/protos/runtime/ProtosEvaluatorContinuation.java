/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import com.guillermomolina.protos.execution.ProtosClosureExecutionPlan;
import com.guillermomolina.protos.execution.ProtosExpressionNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Per-task replay tape used to reconstruct only the host stack, never completed Protos effects. */
public final class ProtosEvaluatorContinuation {
    public record Entry(int index, boolean completed, Object result) {}

    private static final class Event {
        final ProtosExpressionNode replaySiteIdentity;
        boolean completed;
        Object result;
        int end;
        Event(ProtosExpressionNode replaySiteIdentity) { this.replaySiteIdentity = replaySiteIdentity; }
    }
    private static final class Active {
        final int eventIndex;
        int invocationOrdinal;
        Active(int eventIndex) { this.eventIndex = eventIndex; }
    }
    private record InvocationKey(int eventIndex, int ordinal) {}

    private final ArrayList<Event> events = new ArrayList<>();
    private final ArrayDeque<Active> active = new ArrayDeque<>();
    private final Map<InvocationKey, ProtosActivation> invocationActivations = new HashMap<>();
    private final IdentityHashMap<ProtosClosureExecutionPlan, ProtosClosureExecutionPlan>
            legacyAstFallbackPlans = new IdentityHashMap<>();
    private ProtosActivation rootInvocationActivation;
    private int cursor;
    private boolean segmentActive;
    private boolean controlUnwind;

    public void beginSegment() {
        if (segmentActive) throw new IllegalStateException("evaluator segment already active");
        cursor = 0;
        active.clear();
        controlUnwind = false;
        segmentActive = true;
    }

    public void endSegment() {
        active.clear();
        segmentActive = false;
        controlUnwind = false;
    }

    public boolean segmentActive() { return segmentActive; }
    public boolean controlUnwind() { return controlUnwind; }
    public void markControlUnwind() { controlUnwind = true; }

    public Entry enter(ProtosExpressionNode replaySiteIdentity) {
        Objects.requireNonNull(replaySiteIdentity, "replaySiteIdentity");
        if (!segmentActive) throw new IllegalStateException("no evaluator segment active");
        final int index;
        if (cursor < events.size()) {
            Event event = events.get(cursor);
            if (event.replaySiteIdentity != replaySiteIdentity) {
                throw new IllegalStateException("resumable evaluator replay diverged at event " + cursor);
            }
            index = cursor++;
            if (event.completed) {
                cursor = event.end;
                return new Entry(index, true, event.result);
            }
        } else {
            index = events.size();
            events.add(new Event(replaySiteIdentity));
            cursor++;
        }
        active.push(new Active(index));
        return new Entry(index, false, null);
    }

    public void complete(Entry entry, Object result) {
        Active current = active.pop();
        if (current.eventIndex != entry.index()) throw new IllegalStateException("evaluator stack mismatch");
        Event event = events.get(entry.index());
        event.result = result;
        event.completed = true;
        event.end = cursor;
    }

    public void leaveIncomplete(Entry entry) {
        Active current = active.pop();
        if (current.eventIndex != entry.index()) throw new IllegalStateException("evaluator stack mismatch");
    }

    /** Current replay-tape cursor after the events already traversed in this segment. */
    public int cursorPosition() {
        if (!segmentActive) {
            throw new IllegalStateException("no evaluator segment active");
        }
        return cursor;
    }

    /**
     * Re-enters a native control primitive after one direct Closure invocation has already
     * semantically completed in an earlier segment.
     *
     * <p>The skipped invocation still consumes its parent-event invocation ordinal so a later
     * direct invocation (for example ensure cleanup) reuses the same replay-stable activation.
     * The cursor jump skips only the previously traversed body event range recorded by that
     * control primitive.
     */
    public void skipInvocationReplayTo(int targetCursor) {
        if (!segmentActive) {
            throw new IllegalStateException("no evaluator segment active");
        }
        Active current = active.peek();
        if (current == null) {
            throw new IllegalStateException("no active evaluator event for invocation replay");
        }
        if (targetCursor < cursor || targetCursor > events.size()) {
            throw new IllegalStateException(
                    "invalid replay cursor jump from " + cursor + " to " + targetCursor);
        }
        current.invocationOrdinal = Math.addExact(current.invocationOrdinal, 1);
        cursor = targetCursor;
    }

    /**
     * Commits one normally completed child execution nested in the current active event.
     *
     * <p>The child event suffix and its invocation activations are no longer needed once the
     * child has completed normally: future replay may resume only at a later semantic point.
     * Truncating the suffix back to a stable checkpoint lets iterative native control reuse the
     * same tape positions instead of retaining history proportional to completed iterations.
     * The caller states how many invocation ordinals on the current parent event belong to the
     * enclosing control primitive itself and therefore must survive the compaction.
     */
    public void compactCompletedChildExecution(
            int checkpointCursor, int retainedParentInvocationOrdinals) {
        if (!segmentActive) {
            throw new IllegalStateException("no evaluator segment active");
        }
        if (retainedParentInvocationOrdinals < 0) {
            throw new IllegalArgumentException("retained invocation prefix must be non-negative");
        }
        Active current = active.peek();
        if (current == null) {
            throw new IllegalStateException("no active evaluator event for child compaction");
        }
        if (checkpointCursor <= current.eventIndex
                || checkpointCursor > cursor
                || cursor > events.size()) {
            throw new IllegalStateException(
                    "invalid completed-child checkpoint "
                            + checkpointCursor
                            + " for parent event "
                            + current.eventIndex
                            + " at cursor "
                            + cursor
                            + " / "
                            + events.size());
        }
        if (current.invocationOrdinal < retainedParentInvocationOrdinals) {
            throw new IllegalStateException(
                    "parent invocation ordinal is before retained control prefix");
        }
        for (Active retained : active) {
            if (retained.eventIndex >= checkpointCursor) {
                throw new IllegalStateException(
                        "cannot compact an evaluator suffix containing an active child event");
            }
        }

        int parentEventIndex = current.eventIndex;
        invocationActivations.keySet().removeIf(
                key -> key.eventIndex() >= checkpointCursor
                        || (key.eventIndex() == parentEventIndex
                                && key.ordinal() >= retainedParentInvocationOrdinals));
        events.subList(checkpointCursor, events.size()).clear();
        cursor = checkpointCursor;
        current.invocationOrdinal = retainedParentInvocationOrdinals;
    }

    int retainedEventCount() {
        return events.size();
    }

    int retainedInvocationActivationCount() {
        return invocationActivations.size();
    }

    /**
     * Replay-stable activation for the one root Closure executed by a cooperative Task.
     *
     * <p>At Task entry no evaluator event is active yet, so ordinary invocationActivation()
     * cannot provide a stable key. Retaining the root activation across segments preserves
     * its lexical execution context and ReturnHome while the event tape reconstructs only
     * the host stack.
     */
    /**
     * Task-local stable AST projection for the temporary B6B replay fallback.
     *
     * <p>Replay compares exact ProtosExpressionNode identity across evaluator segments.
     * Rebuilding a fallback AST on resume would therefore diverge even with identical
     * canonical source. This cache belongs only to one Task's evaluator continuation.
     */
    public ProtosClosureExecutionPlan replayStableLegacyAstPlan(
            ProtosClosureExecutionPlan template,
            Supplier<ProtosClosureExecutionPlan> factory) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(factory, "factory");
        if (!segmentActive) {
            throw new IllegalStateException(
                    "legacy AST replay projection requested outside an evaluator segment");
        }
        return legacyAstFallbackPlans.computeIfAbsent(
                template,
                ignored ->
                        Objects.requireNonNull(
                                factory.get(),
                                "legacy AST replay projection"));
    }

    public int retainedLegacyAstFallbackPlanCountForTesting() {
        return legacyAstFallbackPlans.size();
    }

    public ProtosActivation rootInvocationActivation(Supplier<ProtosActivation> factory) {
        Objects.requireNonNull(factory, "factory");
        if (!segmentActive) {
            throw new IllegalStateException("no evaluator segment active");
        }
        if (!active.isEmpty()) {
            throw new IllegalStateException(
                    "root invocation activation requested after evaluator events started");
        }
        if (rootInvocationActivation == null) {
            rootInvocationActivation = factory.get();
        }
        return rootInvocationActivation;
    }

    public ProtosActivation invocationActivation(Supplier<ProtosActivation> factory) {
        Objects.requireNonNull(factory, "factory");
        Active current = active.peek();
        if (current == null) return factory.get();
        InvocationKey key = new InvocationKey(current.eventIndex, current.invocationOrdinal++);
        return invocationActivations.computeIfAbsent(key, ignored -> factory.get());
    }
}
