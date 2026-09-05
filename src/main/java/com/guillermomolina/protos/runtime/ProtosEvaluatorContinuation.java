/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import com.guillermomolina.protos.execution.ProtosExpressionNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Per-task replay tape used to reconstruct only the host stack, never completed Protos effects. */
public final class ProtosEvaluatorContinuation {
    public record Entry(int index, boolean completed, Object result) {}

    private static final class Event {
        final ProtosExpressionNode node;
        boolean completed;
        Object result;
        int end;
        Event(ProtosExpressionNode node) { this.node = node; }
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

    public Entry enter(ProtosExpressionNode node) {
        Objects.requireNonNull(node, "node");
        if (!segmentActive) throw new IllegalStateException("no evaluator segment active");
        final int index;
        if (cursor < events.size()) {
            Event event = events.get(cursor);
            if (event.node != node) {
                throw new IllegalStateException("resumable evaluator replay diverged at event " + cursor);
            }
            index = cursor++;
            if (event.completed) {
                cursor = event.end;
                return new Entry(index, true, event.result);
            }
        } else {
            index = events.size();
            events.add(new Event(node));
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

    public ProtosActivation invocationActivation(Supplier<ProtosActivation> factory) {
        Objects.requireNonNull(factory, "factory");
        Active current = active.peek();
        if (current == null) return factory.get();
        InvocationKey key = new InvocationKey(current.eventIndex, current.invocationOrdinal++);
        return invocationActivations.computeIfAbsent(key, ignored -> factory.get());
    }
}
