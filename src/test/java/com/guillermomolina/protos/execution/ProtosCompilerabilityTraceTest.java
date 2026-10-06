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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * TEST009-T focal coverage of the diagnostic compilerability causal trace: activation, the
 * pinned runtime listener contract, run-local correlation, durable root keys and event encoding.
 * No guest program runs and no compilation is forced; the real acquisition is the manual {@code
 * make diagnose-truffle-compilation} surface.
 */
final class ProtosCompilerabilityTraceTest {
    private static final Source SOURCE =
            Source.newBuilder(ProtosLanguage.ID, "a: () => { 1 }\n", "dir/a \"b\".protos").build();
    private static final ProtosCompilerabilityRootIdentity.Span CLOSURE_SPAN =
            new ProtosCompilerabilityRootIdentity.Span(
                    SOURCE, 3, 11, ProtosCompilerabilityRootIdentity.CLOSURE);

    @Test
    void absentOrNonTruePropertyNeverActivates() {
        assertFalse(ProtosCompilerabilityTrace.isEnabled(null));
        assertFalse(ProtosCompilerabilityTrace.isEnabled("false"));
        assertFalse(ProtosCompilerabilityTrace.isEnabled("TRUE"));
        assertTrue(ProtosCompilerabilityTrace.isEnabled("true"));
        // The surefire JVM never sets the private diagnostic property.
        assertFalse(ProtosCompilerabilityTrace.ENABLED);

        AtomicInteger installs = new AtomicInteger();
        ProtosCompilerabilityTrace.Activation disabled =
                new ProtosCompilerabilityTrace.Activation(false, installs::incrementAndGet);
        disabled.activate();
        disabled.activate();
        assertEquals(0, installs.get());
    }

    @Test
    void enabledActivationInstallsExactlyOnceUnderConcurrentContextCreation() throws Exception {
        AtomicInteger installs = new AtomicInteger();
        ProtosCompilerabilityTrace.Activation enabled =
                new ProtosCompilerabilityTrace.Activation(true, installs::incrementAndGet);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        try {
            for (int index = 0; index < 32; index++) {
                pool.submit(() -> {
                    go.await();
                    enabled.activate();
                    return null;
                });
            }
            go.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        }
        enabled.activate();
        assertEquals(1, installs.get());
    }

    @Test
    void pinnedRuntimeExposesTheExactListenerContractReflectively() throws Exception {
        ProtosCompilerabilityListenerBridge.Contract contract =
                ProtosCompilerabilityListenerBridge.Contract.resolve(
                        ProtosCompilerabilityTraceTest.class.getClassLoader());
        assertEquals(ProtosCompilerabilityListenerBridge.LISTENER_INTERFACE,
                contract.listenerInterface().getName());
        assertTrue(contract.listenerInterface().isInterface());
        assertEquals(6, contract.onFailed().getParameterCount());
        assertEquals(4, contract.onSuccess().getParameterCount());
        assertEquals(2, contract.onGraalTierFinished().getParameterCount());
        assertNotNull(contract.targetId());
    }

    @Test
    void unreachableRuntimeFailsClosedWithMachineReadableInstallError() {
        List<String> lines = new ArrayList<>();
        // java.lang.Object's bootstrap loader cannot see truffle-runtime.
        ProtosCompilerabilityListenerBridge.install(
                new Object(), new ProtosCompilerabilityRecorder(lines::add));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith(ProtosCompilerabilityRecorder.PREFIX + "{\"schema\":\""
                + ProtosCompilerabilityRecorder.SCHEMA + "\",\"event\":\"install_error\""));
        assertTrue(lines.get(0).contains("\"code\":\"RUNTIME_CLASS_MISSING\""));
    }

    @Test
    void successLifecycleIsCorrelatedByTargetWithMonotonicSequence() {
        List<String> lines = new ArrayList<>();
        ProtosCompilerabilityRecorder recorder = new ProtosCompilerabilityRecorder(lines::add);
        Object first = new Object();
        Object second = new Object();
        recorder.started(first, semanticIdentity(), 2, 40, 1, 17);
        recorder.truffleTierFinished(first, 5000, new String[] {"PiNode", "IfNode", "PiNode"},
                new ProtosCompilerabilityRecorder.Inlining(3, 1, List.of("K")));
        recorder.graalTierFinished(first, 7000, new String[] {"IfNode"});
        recorder.succeeded(first, new ProtosCompilerabilityRecorder.Result(77, 4096, 64, 2, 30));
        recorder.started(second, semanticIdentity(), 1, 5, 1, 18);

        assertEquals(List.of("start", "truffle_tier", "graal_tier", "success", "start"), events(lines));
        assertTrue(lines.get(0).contains("\"seq\":1,"));
        assertTrue(lines.get(3).contains("\"seq\":1,\"compilation_id\":77,\"target_code_size\":4096"));
        assertTrue(lines.get(4).contains("\"seq\":2,"));
        assertTrue(lines.get(1).contains(
                "\"top_node_types\":[{\"type\":\"PiNode\",\"count\":2},{\"type\":\"IfNode\",\"count\":1}]"));
        assertTrue(lines.get(1).contains(
                "\"inlined_targets\":[{\"durable_root_key\":\"K\",\"count\":1}]"));
        String identityHash = Integer.toHexString(System.identityHashCode(first));
        for (String line : lines) {
            assertFalse(line.contains("@" + identityHash));
        }
    }

    @Test
    void codeTooLargeFailureAfterGraalTierIsCorrelatedWithoutAnInventedSize() {
        List<String> lines = new ArrayList<>();
        ProtosCompilerabilityRecorder recorder = new ProtosCompilerabilityRecorder(lines::add);
        Object target = new Object();
        recorder.started(target, semanticIdentity(), 2, 40, 1, 17);
        recorder.truffleTierFinished(target, 5000, new String[0],
                new ProtosCompilerabilityRecorder.Inlining(0, 0, List.of()));
        recorder.graalTierFinished(target, 7000, new String[0]);
        recorder.failed(target, "BailoutException: Code installation failed: code is too large",
                true, true, 2);

        assertEquals(List.of("start", "truffle_tier", "graal_tier", "failure"), events(lines));
        String failure = lines.get(3);
        assertTrue(failure.contains("\"seq\":1,\"phase_reached\":\"GRAAL_TIER\",\"tier\":2,"
                + "\"bailout\":true,\"permanent_bailout\":true"));
        assertFalse(failure.contains("target_code_size"));
    }

    @Test
    void uncorrelatableCallbacksAreReportedNeverGuessed() {
        List<String> lines = new ArrayList<>();
        ProtosCompilerabilityRecorder recorder = new ProtosCompilerabilityRecorder(lines::add);
        Object target = new Object();
        recorder.graalTierFinished(target, 1, new String[0]);
        recorder.started(target, semanticIdentity(), 2, 1, 1, 1);
        recorder.graalTierFinished(target, 1, new String[0]);
        recorder.started(target, semanticIdentity(), 2, 1, 1, 1);
        recorder.started(target, semanticIdentity(), 2, 1, 1, 1);

        assertEquals(List.of("error", "start", "error", "start", "error", "start"), events(lines));
        assertTrue(lines.get(0).contains("CORRELATION_UNMATCHED_CALLBACK"));
        assertTrue(lines.get(2).contains("CORRELATION_LIFECYCLE_VIOLATION"));
        assertTrue(lines.get(4).contains("CORRELATION_TARGET_RESTARTED"));
    }

    @Test
    void semanticDurableKeyIsSourceBasedDeterministicAndContinuationNormalized() {
        ProtosCompilerabilityRootIdentity identity = semanticIdentity();
        String uri = SOURCE.getURI().toString();
        assertEquals("SEMANTIC_BYTECODE_ROOT|CLOSURE|" + uri + "|3|11", identity.durableKey());
        assertEquals(identity, semanticIdentity());
        assertNull(identity.keyProblem());

        ProtosCompilerabilityRootIdentity continuation =
                ProtosCompilerabilityRootIdentity.compose(
                        ProtosCompilerabilityRootIdentity.SEMANTIC_BYTECODE_ROOT,
                        "ProtosSemanticBytecodeRootNodeGen", null, true, 42, CLOSURE_SPAN,
                        "bc9:00", null);
        assertEquals(identity.durableKey(), continuation.sourceRootKey());
        assertEquals(identity.durableKey() + "|continuation@42", continuation.durableKey());

        ProtosCompilerabilityRootIdentity noResume =
                ProtosCompilerabilityRootIdentity.compose(
                        ProtosCompilerabilityRootIdentity.SEMANTIC_BYTECODE_ROOT,
                        "ProtosSemanticBytecodeRootNodeGen", null, true, null, CLOSURE_SPAN,
                        "bc9:00", null);
        assertNull(noResume.durableKey());
        assertEquals("CONTINUATION_RESUME_BCI_UNAVAILABLE", noResume.keyProblem());
    }

    @Test
    void rootsWithoutStableMetadataFailClosedInsteadOfUsingAnAddress() {
        ProtosCompilerabilityRootIdentity unrecorded =
                ProtosCompilerabilityRootIdentity.compose(
                        ProtosCompilerabilityRootIdentity.SEMANTIC_BYTECODE_ROOT,
                        "ProtosSemanticBytecodeRootNodeGen", null, false, null, null, "bc9:00",
                        null);
        assertNull(unrecorded.durableKey());
        assertEquals("SEMANTIC_ROOT_SPAN_UNRECORDED", unrecorded.keyProblem());

        ProtosCompilerabilityRootIdentity untagged =
                ProtosCompilerabilityRootIdentity.compose(
                        ProtosCompilerabilityRootIdentity.UNTAGGED_BYTECODE_ROOT,
                        "ProtosBytecodeRootNodeGen", null, false, null, null, "bc9:00ff", null);
        assertEquals("UNTAGGED_BYTECODE_ROOT|ProtosBytecodeRootNodeGen|bc9:00ff",
                untagged.durableKey());

        RootNode other = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        };
        ProtosCompilerabilityRootIdentity described =
                ProtosCompilerabilityRootIdentity.describe(other);
        assertEquals(ProtosCompilerabilityRootIdentity.OTHER_TRUFFLE_ROOT, described.family());
        assertNull(described.durableKey());
        assertEquals("OTHER_ROOT_WITHOUT_STABLE_IDENTITY", described.keyProblem());
        assertFalse(described.toJson().toString().contains(
                Integer.toHexString(System.identityHashCode(other))));
    }

    @Test
    void instructionNamesDropOnlyTheQuickeningSuffix() {
        assertEquals("load.local",
                ProtosCompilerabilityRootIdentity.normalizedInstructionName("load.local$Int$unboxed"));
        assertEquals("c.Lookup", ProtosCompilerabilityRootIdentity.normalizedInstructionName("c.Lookup"));
    }

    @Test
    void jsonEscapesQuotesNewlinesControlAndNonAsciiCharacters() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("uri", "file:///a \"b\"\\c\nd\te\r\u0001\u00e9");
        event.put("n", 7L);
        event.put("flag", false);
        event.put("none", null);
        event.put("list", List.of(1, "x"));
        assertEquals("{\"uri\":\"file:///a \\\"b\\\"\\\\c\\nd\\te\\r\\u0001\\u00e9\",\"n\":7,"
                + "\"flag\":false,\"none\":null,\"list\":[1,\"x\"]}",
                ProtosCompilerabilityJson.encode(event));
        assertThrows(IllegalArgumentException.class,
                () -> ProtosCompilerabilityJson.encode(List.of(new Object())));
        assertThrows(IllegalArgumentException.class,
                () -> ProtosCompilerabilityJson.encode(List.of(1.5d)));
    }

    @Test
    void startEventCarriesEscapedSourceIdentity() {
        List<String> lines = new ArrayList<>();
        new ProtosCompilerabilityRecorder(lines::add)
                .started(new Object(), semanticIdentity(), 2, 40, 1, 17);
        String line = lines.get(0);
        assertFalse(line.substring(ProtosCompilerabilityRecorder.PREFIX.length()).contains("\n"));
        assertTrue(line.contains("\"source_start\":3,\"source_length\":11,"
                + "\"semantic_root_kind\":\"CLOSURE\""));
        assertTrue(line.contains("\"run_local\":{\"engine\":1,\"id\":17}"));
    }

    private static ProtosCompilerabilityRootIdentity semanticIdentity() {
        return ProtosCompilerabilityRootIdentity.compose(
                ProtosCompilerabilityRootIdentity.SEMANTIC_BYTECODE_ROOT,
                "ProtosSemanticBytecodeRootNodeGen", null, false, null, CLOSURE_SPAN, "bc9:00",
                null);
    }

    private static List<String> events(List<String> lines) {
        List<String> events = new ArrayList<>();
        for (String line : lines) {
            int start = line.indexOf("\"event\":\"") + "\"event\":\"".length();
            events.add(line.substring(start, line.indexOf('"', start)));
        }
        return events;
    }
}
