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
package com.guillermomolina.protos.runtime.io;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosExecutionOutcome;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedSession;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ClockGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.EntropyGrant;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClockCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** I163: Clock authority never crosses Actor or P boundaries. */
final class ProtosClockTransferTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");

    @Test
    void sampledIntegerExecutesInTheOwningHostedParallelPlacement(@TempDir Path directory)
            throws Exception {
        Path entry = directory.resolve("clock-parallel.protos");
        Files.writeString(entry, """
                parallelProbe: () => {
                    sample: clock.epochNanoseconds()
                    ((value) => { Integer.recognizes(value) }).parallel(sample).value()
                }
                null
                """);
        try (ProtosStandaloneHostedSession session = ProtosStandaloneHostedSession.open(
                CORE, entry, List.of(), InputStream.nullInputStream(),
                OutputStream.nullOutputStream(), OutputStream.nullOutputStream(),
                NetworkGrant.NONE, EntropyGrant.NONE, ClockGrant.HOST_CLOCK)) {
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, session.initialOutcome().state());
            ProtosExecutionOutcome result = session.invokeTopLevel("parallelProbe");
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, result.state());
            assertSame(ProtosBooleanValue.TRUE, result.value());
        }
    }

    @Test
    void actorMessagesAndRequestsRejectTheGrant() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosClockCapabilityValue grant = grant(prelude);

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotValue(grant, source));

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotArguments(
                        List.of(grant), source));

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotArguments(
                        List.of(ProtosNumericValueSupport.integer(7), grant),
                        source));
    }

    @Test
    void requestsAndRepliesUseTheSameNonTransferableSnapshot()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosClockCapabilityValue grant = grant(prelude);

        ProtosObjectValue request =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        request.createLocalSlot("requestedClock", grant);

        ProtosObjectValue reply =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        reply.createLocalSlot("returnedClock", grant);

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotValue(request, source));

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotValue(reply, source));
    }

    @Test
    void actorSpawnNeverDelegatesClock() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosClockCapabilityValue grant = grant(prelude);

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotSpawnArguments(
                        List.of(grant), source));

        ProtosObjectValue wrapped =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        wrapped.createLocalSlot("authority", grant);

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotSpawnArguments(
                        List.of(wrapped), source));
    }

    @Test
    void delegationParentsAndNestedGraphsCannotSmuggleClock()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosClockCapabilityValue grant = grant(prelude);

        ProtosObjectValue descendant = new ProtosObjectValue(grant);
        descendant.createLocalSlot(
                "data", ProtosNumericValueSupport.integer(42));

        ProtosObjectValue nested =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        nested.createLocalSlot("child", descendant);
        nested.createLocalSlot("cycle", nested);

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotValue(
                        descendant, source));

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotValue(nested, source));

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotArguments(
                        List.of(nested, grant), source));
    }

    @Test
    void parallelCaptureRejectsTheGrantAndItsDescendants()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosClockCapabilityValue grant = grant(prelude);
        ProtosObjectValue descendant = new ProtosObjectValue(grant);

        ProtosObjectValue holder =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("authority", grant);

        Method copy = parallelCopyMethod();
        assertNonParallel(copy, grant, activation);
        assertNonParallel(copy, descendant, activation);
        assertNonParallel(copy, holder, activation);
    }

    @Test
    void sampledIntegersRemainOrdinaryActorAndParallelData() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        Object integer = ProtosNumericValueSupport.integerFromHost(
                new java.math.BigInteger("10413792000000000000"), prelude);
        Object transferred = ProtosActorValueTransfer.snapshotValue(integer, source);
        assertTrue(ProtosNumericValueSupport.isCurrentInteger(transferred));
        assertEquals(ProtosNumericValueSupport.integerDecimalText(integer),
                ProtosNumericValueSupport.integerDecimalText(transferred));
        Object parallel = parallelCopyMethod().invoke(null, integer, source,
                new IdentityHashMap<Object, Object>());
        assertTrue(ProtosNumericValueSupport.isCurrentInteger(parallel));
        assertEquals(ProtosNumericValueSupport.integerDecimalText(integer),
                ProtosNumericValueSupport.integerDecimalText(parallel));
    }

    private static ProtosClockCapabilityValue grant(
            ProtosPrelude prelude) {
        return new ProtosClockCapabilityValue(prelude, () -> {
            throw new AssertionError("transfer may never read the clock");
        });
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    private static void assertNonTransferable(
            ProtosPrelude prelude, ThrowingAction action) {
        ProtosSignalException failure = assertThrows(
                ProtosSignalException.class, action::run);

        assertSame(
                prelude.bindings().readLocalSlot("NonTransferableValue").orElseThrow(),
                failure.error().parent().orElseThrow());
    }

    private static void assertNonParallel(
            Method copy, Object value, ProtosActivation activation) {
        InvocationTargetException failure = assertThrows(
                InvocationTargetException.class,
                () -> copy.invoke(
                        null, value, activation,
                        new IdentityHashMap<Object, Object>()));

        assertNotNull(failure.getCause());
        assertEquals("NonParallel",
                failure.getCause().getClass().getSimpleName());
    }

    private static Method parallelCopyMethod() throws Exception {
        Class<?> transfer = Class.forName(
                "com.guillermomolina.protos.execution."
                        + "ProtosParallelRuntime$Transfer");
        Method copy = transfer.getDeclaredMethod(
                "copy",
                Object.class,
                ProtosActivation.class,
                IdentityHashMap.class);
        copy.setAccessible(true);
        return copy;
    }

    @FunctionalInterface
    private interface ThrowingAction {
        Object run();
    }
}
