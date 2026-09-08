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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProtosPolyglotExecutionContextTest {
    private static final long TIMEOUT_SECONDS = 5L;

    @Test
    void contextParsesThroughRegisteredLanguageAndExecutesWithExplicitActivation() {
        Source source = source("42", "a4b1-entry.protos");
        ProtosActivation activation = plainActivation();

        try (ProtosPolyglotExecutionContext polyglot = open()) {
            ProtosExecutionOutcome outcome = polyglot.execute(source, activation);

            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
            ProtosIntegerValue result =
                    assertInstanceOf(ProtosIntegerValue.class, outcome.value());
            assertEquals(BigInteger.valueOf(42), result.value());
        }
    }

    @Test
    void sourceForAnotherLanguageFailsBeforeExecution() {
        Source foreign = Source.newBuilder("foreign", "42", "foreign.src").build();

        try (ProtosPolyglotExecutionContext polyglot = open()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> polyglot.execute(foreign, plainActivation()));
        }
    }

    @Test
    void sameContextAllowsTwoCarrierThreadsToExecuteConcurrently() throws Exception {
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Set<Thread> carriers = ConcurrentHashMap.newKeySet();
        Set<ProtosLanguageContext> languageContexts = ConcurrentHashMap.newKeySet();
        ProtosClosureValue gate =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (!supplied.isEmpty()) {
                                throw new AssertionError("gate must receive no arguments");
                            }
                            carriers.add(Thread.currentThread());
                            languageContexts.add(ProtosLanguageContext.current());
                            bothEntered.countDown();
                            await(release);
                            return ProtosNullValue.INSTANCE;
                        });
        Source source = source("gate()", "a4b1-concurrent.protos");
        ProtosPrelude prelude = minimalPrelude();
        ProtosActivation firstActivation = activationWith(prelude, "gate", gate);
        ProtosActivation secondActivation = activationWith(prelude, "gate", gate);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try (ProtosPolyglotExecutionContext polyglot = open()) {
            Future<ProtosExecutionOutcome> first =
                    executor.submit(() -> polyglot.execute(source, firstActivation));
            Future<ProtosExecutionOutcome> second =
                    executor.submit(() -> polyglot.execute(source, secondActivation));

            awaitLatchOrPropagate(
                    bothEntered,
                    "both carriers must overlap inside one Polyglot Context",
                    first,
                    second);
            release.countDown();

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).state());
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).state());
            assertEquals(2, carriers.size(), "two distinct host carriers must execute");
            assertEquals(
                    1,
                    languageContexts.size(),
                    "concurrent carriers must observe the same language context");
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Test
    void closeWaitsForInFlightExecutionThenRejectsLaterEntry() throws Exception {
        CountDownLatch executionEntered = new CountDownLatch(1);
        CountDownLatch releaseExecution = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);
        ProtosClosureValue gate =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            executionEntered.countDown();
                            await(releaseExecution);
                            return ProtosNullValue.INSTANCE;
                        });
        Source source = source("gate()", "a4b1-close-race.protos");
        ProtosPrelude prelude = minimalPrelude();
        ProtosActivation activation = activationWith(prelude, "gate", gate);
        ProtosPolyglotExecutionContext polyglot = open();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ProtosExecutionOutcome> running =
                    executor.submit(() -> polyglot.execute(source, activation));
            awaitLatchOrPropagate(
                    executionEntered,
                    "guest execution must reach the blocking callback",
                    running);

            Future<?> closing =
                    executor.submit(
                            () -> {
                                closeStarted.countDown();
                                try {
                                    polyglot.close();
                                } finally {
                                    closeReturned.countDown();
                                }
                            });
            assertTrue(closeStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertFalse(
                    closeReturned.await(200L, TimeUnit.MILLISECONDS),
                    "close must not finish while guest execution is still entered");

            releaseExecution.countDown();
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    running.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).state());
            closing.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThrows(
                    IllegalStateException.class,
                    () -> polyglot.execute(source("1", "after-close.protos"), plainActivation()));
        } finally {
            releaseExecution.countDown();
            polyglot.close();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Test
    void closingOneContextAllowsAFreshDistinctLanguageContext() {
        ProtosLanguageContext first = captureCurrentLanguageContext();
        ProtosLanguageContext second = captureCurrentLanguageContext();

        assertNotSame(first, second);
    }

    private static ProtosLanguageContext captureCurrentLanguageContext() {
        AtomicReference<ProtosLanguageContext> captured = new AtomicReference<>();
        ProtosClosureValue capture =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            captured.set(ProtosLanguageContext.current());
                            return ProtosNullValue.INSTANCE;
                        });
        ProtosPrelude prelude = minimalPrelude();
        try (ProtosPolyglotExecutionContext polyglot = open()) {
            ProtosExecutionOutcome outcome =
                    polyglot.execute(
                            source("capture()", "a4b1-context-id.protos"),
                            activationWith(prelude, "capture", capture));
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        }
        ProtosLanguageContext result = captured.get();
        if (result == null) {
            throw new AssertionError("capture callback did not observe a language context");
        }
        return result;
    }

    private static Source source(String characters, String name) {
        return Source.newBuilder(ProtosLanguage.ID, characters, name)
                .uri(URI.create("memory:///" + name))
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }

    private static ProtosActivation plainActivation() {
        ProtosObjectValue semanticContext =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        return new ProtosActivation(semanticContext, List.of(), semanticContext);
    }

    private static ProtosActivation activationWith(
            ProtosPrelude prelude, String name, ProtosClosureValue closure) {
        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot(name, closure);
        return activation;
    }

    private static ProtosPrelude minimalPrelude() {
        // Polymorphic call dispatch requires a Core prelude. Install the ordinary Object.call
        // protocol before concurrent execution, then build only the minimum valid prelude shape
        // needed by this focused carrier-entry fixture. No bootstrap mutation occurs on carriers.
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
        // Immediate Closure invocation materializes the supplied argument vector as a frozen
        // standard Array even when it is empty. Keep that ordinary runtime prerequisite in the
        // fixture so the carrier callback reaches the concurrency gate being tested.
        bindings.createLocalSlot("Array", arrayPrototype);
        bindings.freeze();
        return new ProtosPrelude(bindings, contextPrototype);
    }

    @SafeVarargs
    private static void awaitLatchOrPropagate(
            CountDownLatch latch, String message, Future<?>... work) throws Exception {
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
        assertTrue(latch.getCount() == 0, message);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static ProtosPolyglotExecutionContext open() {
        return ProtosPolyglotExecutionContext.open(
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream());
    }
}
