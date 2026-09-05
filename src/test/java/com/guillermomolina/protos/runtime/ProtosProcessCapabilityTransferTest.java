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
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosProcessCapabilityTransferTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void runtimeProvisioningCreatesDistinctLocalWrappersToOneProcessAuthority() throws Exception {
        ProtosPrelude prelude = core();
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        ProtosObjectValue processPrototype = processPrototype();

        ProtosProcessCapabilityValue first =
                process.provisionCapabilityForRuntime(processPrototype);
        ProtosProcessCapabilityValue second =
                process.provisionCapabilityForRuntime(processPrototype);

        assertNotSame(first, second);
        assertFalse(ProtosIdentity.identical(first, second));
        assertSame(process, first.processForRuntime());
        assertSame(process, second.processForRuntime());
        assertSame(processPrototype, first.representedDelegationParent(prelude));
        assertSame(processPrototype, second.representedDelegationParent(prelude));
    }

    @Test
    void actorTransferDelegatesFreshProxyAndPreservesWholeGraphAliases() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        ProtosProcessCapabilityValue capability =
                process.provisionCapabilityForRuntime(processPrototype());
        ProtosObjectValue authorityBearingDescendant = new ProtosObjectValue(capability);
        authorityBearingDescendant.createLocalSlot("authority", capability);

        List<Object> copied =
                ProtosActorValueTransfer.snapshotArguments(
                        List.of(capability, capability, authorityBearingDescendant), source);
        ProtosProcessCapabilityValue delegated =
                assertInstanceOf(ProtosProcessCapabilityValue.class, copied.get(0));
        ProtosObjectValue copiedDescendant =
                assertInstanceOf(ProtosObjectValue.class, copied.get(2));

        assertNotSame(capability, delegated);
        assertSame(delegated, copied.get(1), "one transfer memo must preserve aliases");
        assertFalse(ProtosIdentity.identical(capability, delegated));
        assertSame(process, delegated.processForRuntime());
        assertSame(delegated, copiedDescendant.parent().orElseThrow());
        assertSame(delegated, copiedDescendant.readLocalSlot("authority").orElseThrow());
    }

    @Test
    void independentActorTransfersUseIndependentWrappersToTheSameProcess() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        ProtosProcessCapabilityValue capability =
                process.provisionCapabilityForRuntime(processPrototype());

        ProtosProcessCapabilityValue first =
                assertInstanceOf(
                        ProtosProcessCapabilityValue.class,
                        ProtosActorValueTransfer.snapshotValue(capability, source));
        ProtosProcessCapabilityValue second =
                assertInstanceOf(
                        ProtosProcessCapabilityValue.class,
                        ProtosActorValueTransfer.snapshotValue(capability, source));

        assertNotSame(first, second);
        assertFalse(ProtosIdentity.identical(first, second));
        assertSame(process, first.processForRuntime());
        assertSame(process, second.processForRuntime());
    }

    @Test
    void processCapabilityAndAuthorityBearingDescendantsHaveNoPTransferContract()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        ProtosProcessCapabilityValue capability =
                process.provisionCapabilityForRuntime(processPrototype());
        ProtosObjectValue descendant = new ProtosObjectValue(capability);

        Class<?> transfer =
                Class.forName(
                        "com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer");
        Method copy =
                transfer.getDeclaredMethod(
                        "copy", Object.class, ProtosActivation.class, IdentityHashMap.class);
        copy.setAccessible(true);

        assertNonParallel(copy, capability, activation);
        assertNonParallel(copy, descendant, activation);
    }

    @Test
    void newCapabilityProvisioningStopsAtTheProcessTerminationCutover() {
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        ProtosObjectValue processPrototype = processPrototype();
        process.provisionCapabilityForRuntime(processPrototype);

        assertTrue(process.requestTerminationForRuntime());
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
        assertThrows(
                IllegalStateException.class,
                () -> process.provisionCapabilityForRuntime(processPrototype));
    }

    private static void assertNonParallel(
            Method copy, Object value, ProtosActivation activation) {
        InvocationTargetException failure =
                assertThrows(
                        InvocationTargetException.class,
                        () ->
                                copy.invoke(
                                        null,
                                        value,
                                        activation,
                                        new IdentityHashMap<Object, Object>()));
        assertNotNull(failure.getCause());
        assertEquals("NonParallel", failure.getCause().getClass().getSimpleName());
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    private static ProtosObjectValue processPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }
}
