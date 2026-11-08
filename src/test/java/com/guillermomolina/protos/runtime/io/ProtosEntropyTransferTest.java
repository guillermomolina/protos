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
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosEntropyCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** I157: Entropy authority never crosses Actor or P boundaries. */
final class ProtosEntropyTransferTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");

    @Test
    void actorMessagesAndRequestsRejectTheGrant() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosEntropyCapabilityValue grant = grant(prelude);

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
        ProtosEntropyCapabilityValue grant = grant(prelude);

        ProtosObjectValue request =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        request.createLocalSlot("requestedEntropy", grant);

        ProtosObjectValue reply =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        reply.createLocalSlot("returnedEntropy", grant);

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotValue(request, source));

        assertNonTransferable(prelude,
                () -> ProtosActorValueTransfer.snapshotValue(reply, source));
    }

    @Test
    void actorSpawnNeverDelegatesEntropy() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosEntropyCapabilityValue grant = grant(prelude);

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
    void delegationParentsAndNestedGraphsCannotSmuggleEntropy()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosEntropyCapabilityValue grant = grant(prelude);

        ProtosObjectValue descendant = new ProtosObjectValue(grant);
        descendant.createLocalSlot(
                "data", ProtosNumericValueSupport.integer(42));

        ProtosObjectValue nested =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        nested.createLocalSlot("child", descendant);

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
        ProtosEntropyCapabilityValue grant = grant(prelude);
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
    void bytesAreCopiedByValueWithoutSharedMutableStorage()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();

        ProtosBytesValue bytes =
                new ProtosBytesValue(prelude.bytesPrototypeForRuntime());
        bytes.indexedAdd(ProtosNumericValueSupport.integer(0));
        bytes.indexedAdd(ProtosNumericValueSupport.integer(127));
        bytes.indexedAdd(ProtosNumericValueSupport.integer(128));
        bytes.indexedAdd(ProtosNumericValueSupport.integer(255));

        ProtosBytesValue transferred = assertInstanceOf(
                ProtosBytesValue.class,
                ProtosActorValueTransfer.snapshotValue(bytes, source));

        assertNotSame(bytes, transferred);
        assertArrayEquals(bytes.octetSnapshot(),
                transferred.octetSnapshot());
        assertFalse(transferred.isFrozen());

        transferred.indexedPut(
                2, ProtosNumericValueSupport.integer(17));

        assertEquals(128L, ProtosNumericValueSupport.exactLong(
                bytes.indexedAt(2)));
        assertEquals(17L, ProtosNumericValueSupport.exactLong(
                transferred.indexedAt(2)));

        Method copy = parallelCopyMethod();
        ProtosBytesValue parallel = assertInstanceOf(
                ProtosBytesValue.class,
                copy.invoke(null, bytes, source,
                        new IdentityHashMap<Object, Object>()));

        assertNotSame(bytes, parallel);
        assertArrayEquals(bytes.octetSnapshot(),
                parallel.octetSnapshot());
    }

    private static ProtosEntropyCapabilityValue grant(
            ProtosPrelude prelude) {
        return new ProtosEntropyCapabilityValue(
                prelude,
                new ProtosEntropyBackend() {
                    @Override
                    public Cancellation generate(
                            int count, Completion completion) {
                        throw new AssertionError(
                                "transfer may never consult host entropy");
                    }

                    @Override
                    public void close() {}
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
