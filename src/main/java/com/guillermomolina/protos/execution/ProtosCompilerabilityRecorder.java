/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * TEST009-T run-local correlation of one JVM's compilation lifecycle callbacks into
 * machine-readable causal-trace events ({@value #SCHEMA}).
 *
 * <p>Each compilation start receives the next value of a monotonic per-JVM sequence. Later
 * callbacks for the same target are joined to that sequence through the target's object
 * identity, which is used only as an in-process map key and is never emitted. Every event is
 * written immediately, as one prefixed JSON line, so that the harness can also attribute the
 * upstream textual traces printed between a compilation's {@code start} and terminal events.
 *
 * <p>Lifecycle per sequence: {@code start -> truffle_tier -> graal_tier -> success}, or {@code
 * failure} after any of the first three. A callback that does not fit (no active compilation for
 * the target, a target started twice, an out-of-order tier) is never guessed: it is emitted as
 * an {@code error} event, which makes the acquisition incomplete.
 *
 * <p>All methods are thread-safe; emission happens under the recorder lock so the sequence order
 * and the event order in the stream agree.
 */
final class ProtosCompilerabilityRecorder {
    static final String SCHEMA = "protos.compilerability.causal/v1";
    static final String PREFIX = "[protos-compilerability] ";
    static final int TOP_NODE_TYPES = 15;

    private enum Phase {
        STARTED,
        TRUFFLE_TIER,
        GRAAL_TIER
    }

    private static final class Active {
        final long sequence;
        Phase phase = Phase.STARTED;

        Active(long sequence) {
            this.sequence = sequence;
        }
    }

    /** A compilation result as delivered on success; absent for failed compilations. */
    record Result(
            long compilationId,
            int targetCodeSize,
            int totalFrameSize,
            int exceptionHandlersCount,
            int infopointsCount) {}

    /** Truffle-tier inlining summary; {@code inlinedTargetKeys} holds durable keys or null. */
    record Inlining(int calls, int inlinedCalls, List<String> inlinedTargetKeys) {}

    private final Consumer<String> sink;
    private final Map<Object, Active> active = new IdentityHashMap<>();
    private long nextSequence = 1;

    ProtosCompilerabilityRecorder(Consumer<String> sink) {
        this.sink = sink;
    }

    synchronized void installed(String runtimeClass) {
        Map<String, Object> event = event("installed");
        event.put("runtime_class", runtimeClass);
        emit(event);
    }

    synchronized void installError(String code, String detail) {
        Map<String, Object> event = event("install_error");
        event.put("code", code);
        event.put("detail", detail);
        emit(event);
    }

    synchronized void error(String code, String detail) {
        Map<String, Object> event = event("error");
        event.put("code", code);
        event.put("detail", detail);
        emit(event);
    }

    synchronized void started(
            Object target,
            ProtosCompilerabilityRootIdentity identity,
            int tier,
            int astNonTrivialNodeCount,
            long engineId,
            long targetId) {
        if (active.containsKey(target)) {
            Active previous = active.remove(target);
            error("CORRELATION_TARGET_RESTARTED",
                    "a target started while sequence " + previous.sequence + " was still active");
        }
        Active compilation = new Active(nextSequence++);
        active.put(target, compilation);
        Map<String, Object> event = event("start");
        event.put("seq", compilation.sequence);
        event.putAll(identity.toJson());
        event.put("tier", tier);
        event.put("ast_non_trivial_node_count", astNonTrivialNodeCount);
        event.put("active_compilations", active.size());
        event.put("run_local", runLocal(engineId, targetId));
        emit(event);
    }

    synchronized void truffleTierFinished(
            Object target, int graphNodes, String[] nodeTypes, Inlining inlining) {
        Active compilation = advance(target, Phase.STARTED, Phase.TRUFFLE_TIER, "truffle_tier");
        if (compilation == null) {
            return;
        }
        Map<String, Object> event = event("truffle_tier");
        event.put("seq", compilation.sequence);
        event.put("graph_nodes", graphNodes);
        event.put("top_node_types", topNodeTypes(nodeTypes));
        Map<String, Object> inliningJson = new LinkedHashMap<>();
        inliningJson.put("calls", inlining.calls());
        inliningJson.put("inlined_calls", inlining.inlinedCalls());
        inliningJson.put("inlined_targets", countKeys(inlining.inlinedTargetKeys()));
        event.put("inlining", inliningJson);
        emit(event);
    }

    synchronized void graalTierFinished(Object target, int graphNodes, String[] nodeTypes) {
        Active compilation = advance(target, Phase.TRUFFLE_TIER, Phase.GRAAL_TIER, "graal_tier");
        if (compilation == null) {
            return;
        }
        Map<String, Object> event = event("graal_tier");
        event.put("seq", compilation.sequence);
        event.put("graph_nodes", graphNodes);
        event.put("top_node_types", topNodeTypes(nodeTypes));
        emit(event);
    }

    synchronized void succeeded(Object target, Result result) {
        Active compilation = active.get(target);
        if (compilation == null || compilation.phase != Phase.GRAAL_TIER) {
            lifecycleError(target, compilation, "success");
            return;
        }
        active.remove(target);
        Map<String, Object> event = event("success");
        event.put("seq", compilation.sequence);
        event.put("compilation_id", result.compilationId());
        event.put("target_code_size", result.targetCodeSize());
        event.put("total_frame_size", result.totalFrameSize());
        event.put("exception_handlers_count", result.exceptionHandlersCount());
        event.put("infopoints_count", result.infopointsCount());
        emit(event);
    }

    synchronized void failed(
            Object target, String reason, boolean bailout, boolean permanentBailout, int tier) {
        Active compilation = active.remove(target);
        if (compilation == null) {
            error("CORRELATION_UNMATCHED_CALLBACK", "failure without an active compilation");
            return;
        }
        Map<String, Object> event = event("failure");
        event.put("seq", compilation.sequence);
        event.put("phase_reached", compilation.phase.name());
        event.put("tier", tier);
        event.put("bailout", bailout);
        event.put("permanent_bailout", permanentBailout);
        event.put("reason", reason);
        emit(event);
    }

    private Active advance(Object target, Phase expected, Phase next, String callback) {
        Active compilation = active.get(target);
        if (compilation == null || compilation.phase != expected) {
            lifecycleError(target, compilation, callback);
            return null;
        }
        compilation.phase = next;
        return compilation;
    }

    private void lifecycleError(Object target, Active compilation, String callback) {
        if (compilation == null) {
            error("CORRELATION_UNMATCHED_CALLBACK", callback + " without an active compilation");
        } else {
            active.remove(target);
            error("CORRELATION_LIFECYCLE_VIOLATION",
                    callback + " after " + compilation.phase + " for sequence " + compilation.sequence);
        }
    }

    /** Most frequent node types first, ties by name; deterministic for equal graphs. */
    static List<Object> topNodeTypes(String[] nodeTypes) {
        Map<String, Integer> counts = new TreeMap<>();
        if (nodeTypes != null) {
            for (String type : nodeTypes) {
                counts.merge(String.valueOf(type), 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((left, right) -> right.getValue().equals(left.getValue())
                ? left.getKey().compareTo(right.getKey())
                : Integer.compare(right.getValue(), left.getValue()));
        List<Object> top = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sorted.subList(0, Math.min(TOP_NODE_TYPES, sorted.size()))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", entry.getKey());
            item.put("count", entry.getValue());
            top.add(item);
        }
        return top;
    }

    /** Distinct inlined keys with multiplicity, in key order; a missing key stays explicit. */
    private static List<Object> countKeys(List<String> keys) {
        Map<String, Integer> counts = new TreeMap<>();
        int unkeyed = 0;
        for (String key : keys) {
            if (key == null) {
                unkeyed++;
            } else {
                counts.merge(key, 1, Integer::sum);
            }
        }
        List<Object> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("durable_root_key", entry.getKey());
            item.put("count", entry.getValue());
            out.add(item);
        }
        if (unkeyed > 0) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("durable_root_key", null);
            item.put("count", unkeyed);
            out.add(item);
        }
        return out;
    }

    /**
     * Engine-local trace ids, published only so the harness can join this event with the same
     * compilation's {@code [engine] opt ...} trace line. They are run-local and are discarded by
     * the harness; they never form a durable key.
     */
    private static Map<String, Object> runLocal(long engineId, long targetId) {
        Map<String, Object> runLocal = new LinkedHashMap<>();
        runLocal.put("engine", engineId);
        runLocal.put("id", targetId);
        return runLocal;
    }

    private static Map<String, Object> event(String name) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("schema", SCHEMA);
        event.put("event", name);
        return event;
    }

    private void emit(Map<String, Object> event) {
        sink.accept(PREFIX + ProtosCompilerabilityJson.encode(event));
    }
}
