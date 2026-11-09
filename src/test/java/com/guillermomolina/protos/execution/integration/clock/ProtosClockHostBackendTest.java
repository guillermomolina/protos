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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClockCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** I163: deterministic host-boundary admission and exact semantic Integer conversion. */
final class ProtosClockHostBackendTest {
    @Test
    void productionAdapterTakesExactlyOneInjectedSnapshot() {
        AtomicInteger calls = new AtomicInteger();
        InstantSource source = () -> { calls.incrementAndGet(); return Instant.EPOCH; };
        ProtosHostClockBackend backend = new ProtosHostClockBackend(source);
        assertEquals(Instant.EPOCH, backend.sample());
        assertEquals(1, calls.get());
    }

    @Test
    void exactConversionIncludesNegativeLargeRepeatedAndBackwardSamples() throws Exception {
        ProtosPrelude prelude = ProtosClockAuthorityTest.prelude();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Instant> next = new AtomicReference<>();
        ProtosClockCapabilityValue clock = new ProtosClockCapabilityValue(prelude,
                () -> { calls.incrementAndGet(); return next.get(); });
        var process = ProtosClockAuthorityTest.bootstrap(prelude, clock);
        try {
            List<Instant> samples = List.of(Instant.EPOCH,
                    Instant.ofEpochSecond(-1, 999_999_999), Instant.ofEpochSecond(0, 1),
                    Instant.parse("1900-01-01T00:00:00Z"), Instant.parse("2300-01-01T00:00:00Z"),
                    Instant.ofEpochSecond(7), Instant.ofEpochSecond(7), Instant.ofEpochSecond(3));
            for (Instant sample : samples) {
                next.set(sample);
                Object value = read(process.activation(), clock);
                assertTrue(ProtosNumericValueSupport.isCurrentInteger(value));
                BigInteger expected = BigInteger.valueOf(sample.getEpochSecond())
                        .multiply(BigInteger.valueOf(1_000_000_000L))
                        .add(BigInteger.valueOf(sample.getNano()));
                assertEquals(expected.toString(), ProtosNumericValueSupport.integerDecimalText(value));
                assertEquals(expected.bitLength() > 63, ProtosNumericValueSupport.isLargeInteger(value));
            }
            assertEquals(samples.size(), calls.get());
        } finally {
            process.process().requestTerminationForRuntime();
        }
    }

    @Test
    void invalidReceiverArityDomainAndRetirementNeverReadProvider() throws Exception {
        ProtosPrelude prelude = ProtosClockAuthorityTest.prelude();
        AtomicInteger reads = new AtomicInteger();
        ProtosClockCapabilityValue clock = new ProtosClockCapabilityValue(prelude,
                () -> { reads.incrementAndGet(); return Instant.EPOCH; });
        var process = ProtosClockAuthorityTest.bootstrap(prelude, clock);
        try {
            ProtosObjectValue fake = new ProtosObjectValue(prelude.clockPrototypeForRuntime());
            assertError(prelude, "InvalidIOArgument", () -> read(process.activation(), fake));
            assertError(prelude, "InvalidIOArgument", () -> read(process.activation(), new ProtosObjectValue(clock)));
            assertError(prelude, "InvalidIOArgument", () -> read(prelude.newModuleActivation(), clock));
            ProtosObjectValue bridge = ProtosClockHostFacility.createBridge();
            ProtosClosureValue method = (ProtosClosureValue) bridge.readLocalSlot("read").orElseThrow();
            ProtosActivation invocation = invocation(process.activation(), bridge, method);
            assertError(prelude, "InvalidIOArgument", () -> method.nativeBody().orElseThrow().execute(invocation, List.of()));
            assertError(prelude, "InvalidIOArgument", () -> method.nativeBody().orElseThrow().execute(
                    invocation(process.activation(), fake, method), List.of(clock)));
            clock.retireForRuntime();
            assertError(prelude, "IOLifecycleError", () -> read(process.activation(), clock));
            assertEquals(0, reads.get());
        } finally {
            process.process().requestTerminationForRuntime();
        }
    }

    @Test
    void nullAndProviderFailureAreFreshIoErrorsButSignalsAndFatalErrorsSurvive() throws Exception {
        ProtosPrelude prelude = ProtosClockAuthorityTest.prelude();
        for (boolean throwsFailure : List.of(false, true)) {
            ProtosClockCapabilityValue clock = new ProtosClockCapabilityValue(prelude, () -> {
                if (throwsFailure) throw new IllegalStateException("private provider detail");
                return null;
            });
            var process = ProtosClockAuthorityTest.bootstrap(prelude, clock);
            try {
                ProtosSignalException first = assertError(prelude, "IOError", () -> read(process.activation(), clock));
                ProtosSignalException second = assertError(prelude, "IOError", () -> read(process.activation(), clock));
                assertNotSame(first.error(), second.error());
            } finally {
                process.process().requestTerminationForRuntime();
            }
        }
        AssertionError fatal = new AssertionError("fatal");
        ProtosClockCapabilityValue clock = new ProtosClockCapabilityValue(prelude, () -> { throw fatal; });
        var process = ProtosClockAuthorityTest.bootstrap(prelude, clock);
        try {
            assertSame(fatal, assertThrows(AssertionError.class, () -> read(process.activation(), clock)));
        } finally {
            process.process().requestTerminationForRuntime();
        }
    }

    @Test
    void providerCannotRewriteAnAlreadySelectedProtosSignal() throws Exception {
        ProtosPrelude prelude = ProtosClockAuthorityTest.prelude();
        ProtosSignalException selected = new ProtosSignalException(new ProtosObjectValue(prelude.errorPrototype()));
        ProtosClockCapabilityValue clock = new ProtosClockCapabilityValue(prelude, () -> { throw selected; });
        var process = ProtosClockAuthorityTest.bootstrap(prelude, clock);
        try {
            assertSame(selected, assertThrows(ProtosSignalException.class, () -> read(process.activation(), clock)));
        } finally {
            process.process().requestTerminationForRuntime();
        }
    }

    @Test
    void hostRetirementAndAnInFlightObservationLeaveNoFurtherReadAuthority() throws Exception {
        ProtosPrelude prelude = ProtosClockAuthorityTest.prelude();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        ProtosClockCapabilityValue clock = new ProtosClockCapabilityValue(prelude, () -> {
            reads.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test provider was not released");
                }
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interruption);
            }
            return Instant.EPOCH;
        });
        var process = ProtosClockAuthorityTest.bootstrap(prelude, clock);
        try {
            CompletableFuture<Object> observation = CompletableFuture.supplyAsync(() -> {
                try {
                    return read(process.activation(), clock);
                } catch (ProtosSignalException revoked) {
                    assertSame(prelude.bindings().readLocalSlot("IOLifecycleError").orElseThrow(),
                            revoked.error().parent().orElseThrow());
                    return null;
                }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            CompletableFuture<Void> retirement = CompletableFuture.runAsync(clock::retireForRuntime);
            release.countDown();
            retirement.get(5, TimeUnit.SECONDS);
            Object result = observation.get(5, TimeUnit.SECONDS);
            // Either the admitted observation completed first, or retirement discarded its sample.
            if (result != null) {
                assertTrue(ProtosNumericValueSupport.isCurrentInteger(result));
                assertEquals("0", ProtosNumericValueSupport.integerDecimalText(result));
            }
            assertTrue(clock.retiredForRuntime());
            assertError(prelude, "IOLifecycleError", () -> read(process.activation(), clock));
            assertEquals(1, reads.get());
        } finally {
            release.countDown();
            process.process().requestTerminationForRuntime();
        }
    }

    private static Object read(ProtosActivation activation, Object receiver) {
        ProtosObjectValue bridge = ProtosClockHostFacility.createBridge();
        ProtosClosureValue method = (ProtosClosureValue) bridge.readLocalSlot("read").orElseThrow();
        return method.nativeBody().orElseThrow().execute(invocation(activation, bridge, method), List.of(receiver));
    }

    private static ProtosActivation invocation(ProtosActivation activation, Object receiver,
            ProtosClosureValue method) {
        return ProtosActivation.forImmediateMethodInvocation(method, List.of(), receiver,
                ProtosObjectValue.rootObject(), activation.prelude().orElseThrow(),
                activation.actorModuleState(), null, activation.executionDomain());
    }

    private static ProtosSignalException assertError(ProtosPrelude prelude, String name, org.junit.jupiter.api.function.Executable action) {
        ProtosSignalException failure = assertThrows(ProtosSignalException.class, action);
        assertSame(prelude.bindings().readLocalSlot(name).orElseThrow(),
                failure.error().parent().orElseThrow());
        return failure;
    }
}
