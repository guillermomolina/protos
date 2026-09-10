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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.Source;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Instrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosI026EDebuggerIntegrationTest {
    private static final long TIMEOUT_SECONDS = 5L;

    @Test
    void realDebuggerSessionReadsTheActivationNativeScope() throws Exception {
        Source source = source("marker\n0", "i026-e2-single.protos");
        ProtosActivation activation = activationWithMarker("single");
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        AtomicReference<String> observedMarker = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();

        try (ProtosPolyglotExecutionContext polyglot = open()) {
            Debugger debugger = debugger(polyglot);
            try (DebuggerSession session =
                    debugger.startSession(
                            event -> {
                                try {
                                    callbacks.incrementAndGet();
                                    assertEquals(
                                            1,
                                            event.getSourceSection().getStartLine(),
                                            "breakpoint must suspend at the Protos statement");
                                    DebugScope scope = event.getTopStackFrame().getScope();
                                    assertNotNull(scope, "debugger frame must expose the E1 scope");
                                    assertEquals("scope", scope.getName());
                                    assertNull(
                                            scope.getParent(),
                                            "PLAT015 baseline has no scope-parent hierarchy");
                                    assertNull(
                                            scope.getReceiver(),
                                            "PLAT015 baseline invents no named receiver");
                                    DebugValue marker = scope.getDeclaredValue("marker");
                                    assertNotNull(marker, "activation-local marker must be visible");
                                    observedMarker.set(marker.toDisplayString());
                                } catch (Throwable failure) {
                                    callbackFailure.compareAndSet(null, failure);
                                } finally {
                                    event.prepareContinue();
                                }
                            })) {
                assertNull(
                        polyglot.callEntered(
                                () -> session.getTopScope(ProtosLanguage.ID)),
                        "Protos must not gain an artificial language top scope");

                session.install(Breakpoint.newBuilder(source).lineIs(1).build());

                ProtosExecutionOutcome outcome = polyglot.execute(source, activation);
                assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
            }
        }

        rethrowCallbackFailure(callbackFailure);
        assertEquals(1, callbacks.get(), "exactly one source breakpoint must suspend");
        assertEquals("single", observedMarker.get());
    }

    @Test
    void twoSameContextCarriersSuspendConcurrentlyWithIndependentActivationScopes()
            throws Exception {
        CountDownLatch bothAtGuestGate = new CountDownLatch(2);
        CountDownLatch bothSuspended = new CountDownLatch(2);
        Set<Thread> guestCarriers = ConcurrentHashMap.newKeySet();
        Set<Thread> debuggerCarriers = ConcurrentHashMap.newKeySet();
        Set<ProtosLanguageContext> guestLanguageContexts = ConcurrentHashMap.newKeySet();
        Set<ProtosLanguageContext> debuggerLanguageContexts = ConcurrentHashMap.newKeySet();
        Set<String> observedMarkers = ConcurrentHashMap.newKeySet();
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();

        ProtosClosureValue gate =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (!supplied.isEmpty()) {
                                throw new AssertionError("gate must receive no arguments");
                            }
                            guestCarriers.add(Thread.currentThread());
                            guestLanguageContexts.add(ProtosLanguageContext.current());
                            bothAtGuestGate.countDown();
                            await(
                                    bothAtGuestGate,
                                    "both carriers must overlap before debugger suspension");
                            return ProtosNullValue.INSTANCE;
                        });

        ProtosPrelude prelude = minimalPrelude();
        ProtosActivation firstActivation = activationWith(prelude, gate, "first");
        ProtosActivation secondActivation = activationWith(prelude, gate, "second");
        Source source = source("gate()\nmarker", "i026-e2-concurrent.protos");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try (ProtosPolyglotExecutionContext polyglot = open()) {
            Debugger debugger = debugger(polyglot);
            try (DebuggerSession session =
                    debugger.startSession(
                            event -> observeConcurrentSuspension(
                                    event,
                                    bothSuspended,
                                    callbacks,
                                    debuggerCarriers,
                                    debuggerLanguageContexts,
                                    observedMarkers,
                                    callbackFailure))) {
                assertNull(
                        polyglot.callEntered(
                                () -> session.getTopScope(ProtosLanguage.ID)),
                        "same-Context debugging must not manufacture global scope state");

                session.install(Breakpoint.newBuilder(source).lineIs(2).build());

                Future<ProtosExecutionOutcome> first =
                        executor.submit(() -> polyglot.execute(source, firstActivation));
                Future<ProtosExecutionOutcome> second =
                        executor.submit(() -> polyglot.execute(source, secondActivation));

                awaitLatchOrPropagate(
                        bothAtGuestGate,
                        "both guest carriers must overlap inside one Process-scoped Context",
                        first,
                        second);

                ProtosExecutionOutcome firstOutcome =
                        first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                ProtosExecutionOutcome secondOutcome =
                        second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertEquals(ProtosExecutionOutcome.State.COMPLETED, firstOutcome.state());
                assertEquals(ProtosExecutionOutcome.State.COMPLETED, secondOutcome.state());
            }

            rethrowCallbackFailure(callbackFailure);

            assertEquals(2, callbacks.get(), "both guest executions must suspend");
            assertEquals(
                    0L,
                    bothSuspended.getCount(),
                    "both debugger callbacks must overlap before either resumes");
            assertEquals(
                    Set.of("first", "second"),
                    observedMarkers,
                    "each suspended scope must retain its own activation-local value");
            assertEquals(2, guestCarriers.size(), "two distinct guest carriers must execute");
            assertEquals(
                    2,
                    debuggerCarriers.size(),
                    "synchronous debugger callbacks must run on both guest carriers");
            assertEquals(
                    1,
                    guestLanguageContexts.size(),
                    "both executions must share one exact Protos language context");
            assertEquals(
                    1,
                    debuggerLanguageContexts.size(),
                    "both debugger callbacks must observe one exact Protos language context");
            assertSame(
                    guestLanguageContexts.iterator().next(),
                    debuggerLanguageContexts.iterator().next(),
                    "debugger suspension must remain inside the same Process-scoped context");
        } finally {
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "debugger integration executor must terminate");
        }
    }

    private static void observeConcurrentSuspension(
            SuspendedEvent event,
            CountDownLatch bothSuspended,
            AtomicInteger callbacks,
            Set<Thread> debuggerCarriers,
            Set<ProtosLanguageContext> debuggerLanguageContexts,
            Set<String> observedMarkers,
            AtomicReference<Throwable> callbackFailure) {
        try {
            callbacks.incrementAndGet();
            debuggerCarriers.add(Thread.currentThread());
            debuggerLanguageContexts.add(ProtosLanguageContext.current());

            assertEquals(
                    2,
                    event.getSourceSection().getStartLine(),
                    "concurrent breakpoint must suspend after the guest overlap gate");

            bothSuspended.countDown();
            await(
                    bothSuspended,
                    "both guest threads must be suspended concurrently by one DebuggerSession");

            DebugScope scope = event.getTopStackFrame().getScope();
            assertNotNull(scope, "each suspended frame must expose one activation-native scope");
            assertEquals("scope", scope.getName());
            assertNull(scope.getParent());
            assertNull(scope.getReceiver());

            DebugValue marker = scope.getDeclaredValue("marker");
            assertNotNull(marker, "each activation-local marker must remain debugger-visible");
            observedMarkers.add(marker.toDisplayString());
        } catch (Throwable failure) {
            callbackFailure.compareAndSet(null, failure);
        } finally {
            event.prepareContinue();
        }
    }

    private static Debugger debugger(ProtosPolyglotExecutionContext polyglot) {
        Instrument instrument = polyglot.engineForTesting().getInstruments().get("debugger");
        assertNotNull(instrument, "GraalVM debugger instrument must be installed");
        Debugger debugger = instrument.lookup(Debugger.class);
        assertNotNull(debugger, "debugger instrument must expose Debugger service");
        return debugger;
    }

    private static ProtosActivation activationWithMarker(String marker) {
        ProtosObjectValue context = new ProtosObjectValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("marker", new ProtosStringValue(marker));
        return new ProtosActivation(context, List.of(), context);
    }

    private static ProtosActivation activationWith(
            ProtosPrelude prelude,
            ProtosClosureValue gate,
            String marker) {
        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("gate", gate);
        activation.context().createLocalSlot("marker", new ProtosStringValue(marker));
        return activation;
    }

    private static ProtosPrelude minimalPrelude() {
        ProtosStandardObjectProtocol.install();

        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue errorPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue arrayPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot("Error", errorPrototype);
        bindings.createLocalSlot("Array", arrayPrototype);
        bindings.freeze();
        return new ProtosPrelude(bindings, contextPrototype);
    }

    private static Source source(String characters, String name) {
        return Source.newBuilder(ProtosLanguage.ID, characters, name)
                .uri(URI.create("memory:///" + name))
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }

    @SafeVarargs
    private static void awaitLatchOrPropagate(
            CountDownLatch latch,
            String message,
            Future<?>... work)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (latch.getCount() != 0 && System.nanoTime() < deadline) {
            for (Future<?> future : work) {
                if (future.isDone()) {
                    future.get();
                }
            }
            latch.await(10L, TimeUnit.MILLISECONDS);
        }
        for (Future<?> future : work) {
            if (latch.getCount() != 0 && future.isDone()) {
                future.get();
            }
        }
        assertEquals(0L, latch.getCount(), message);
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), message);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void rethrowCallbackFailure(
            AtomicReference<Throwable> callbackFailure) {
        Throwable failure = callbackFailure.get();
        if (failure == null) {
            return;
        }
        if (failure instanceof AssertionError assertionError) {
            throw assertionError;
        }
        throw new AssertionError("Debugger callback failed", failure);
    }

    private static ProtosPolyglotExecutionContext open() {
        return ProtosPolyglotExecutionContext.open(
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream());
    }
}
