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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.ControlFlowException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosPerf006B4ATransferSubstrateTest {
    @Test
    void guestErrorLaneIsTruffleCatchableWithoutReplacingTheProtosOccurrence() {
        ProtosObjectValue error = ProtosObjectValue.rootObject();
        ProtosSignalException signal = new ProtosSignalException(error);

        assertInstanceOf(AbstractTruffleException.class, signal);
        assertSame(error, signal.error());

        System.out.println("PERF006_B4A_GUEST_ERROR_TRUFFLE_LANE=PASS");
        System.out.println("PERF006_B4A_GUEST_ERROR_EXACT_OCCURRENCE=YES");
    }

    @Test
    void internalControlFamiliesStayDistinctFromTheGuestErrorLane() {
        ProtosReturnHome home = new ProtosReturnHome();
        Object value = new Object();
        ProtosNonLocalReturnException nonLocalReturn =
                new ProtosNonLocalReturnException(home, value);
        ProtosTaskCancellationException cancellation =
                new ProtosTaskCancellationException();

        assertInstanceOf(ControlFlowException.class, nonLocalReturn);
        assertInstanceOf(ControlFlowException.class, cancellation);
        assertFalse(AbstractTruffleException.class.isInstance(nonLocalReturn));
        assertFalse(AbstractTruffleException.class.isInstance(cancellation));
        assertSame(home, nonLocalReturn.target());
        assertSame(value, nonLocalReturn.value());

        System.out.println("PERF006_B4A_NLR_CONTROL_LANE=PASS");
        System.out.println("PERF006_B4A_CANCELLATION_CONTROL_LANE=PASS");
    }

    @Test
    void directUnwindReplacesTheRetiredBridgeAndInterceptor() throws Exception {
        assertThrows(
                ClassNotFoundException.class,
                () ->
                        Class.forName(
                                "com.guillermomolina.protos.execution."
                                        + "ProtosBytecodeControlTransferException"));
        assertTrue(
                Arrays.stream(ProtosBytecodeRootNode.class.getDeclaredMethods())
                        .noneMatch(
                                method ->
                                        method.getName()
                                                .equals("interceptControlFlowException")),
                "selected transfers must not be intercepted into another representation");
        assertTrue(
                Arrays.stream(ProtosBytecodeRootNode.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("interceptTruffleException")),
                "the guest Error lane keeps its interception");

        String root = read(
                "src/main/java/com/guillermomolina/protos/execution/bytecode/activation/ProtosBytecodeRootNode.java");
        int unwind = root.indexOf("unwindExceptions = {");
        assertTrue(unwind > 0, "the generated interpreter must configure direct unwind");
        String configured = root.substring(unwind, root.indexOf('}', unwind));
        assertTrue(configured.contains("ProtosNonLocalReturnException.class"));
        assertTrue(configured.contains("ProtosTaskCancellationException.class"));
        assertFalse(configured.contains("ProtosSignalException"));
        assertFalse(configured.contains("ControlFlowException.class"));
        assertFalse(configured.contains("Throwable.class"));

        System.out.println("PLAT070_TRUFFLE_UNWIND_EXCEPTIONS=EXACT_NLR_AND_CANCELLATION");
        System.out.println("PLAT070_CONTROL_TRANSFER_BRIDGE=REMOVED");
        System.out.println("PLAT070_CONTROL_TRANSFER_INTERCEPTOR=REMOVED");
    }

    @Test
    void selectedTransfersRunActiveFinallyCleanupButNeverEnterGuestCatches() throws Exception {
        ProtosActivation module = module();
        AtomicInteger cleanups = new AtomicInteger();
        AtomicInteger handlers = new AtomicInteger();
        module.context().createLocalSlot("cleanup", counter(cleanups));
        module.context().createLocalSlot("handlerProbe", counter(handlers));

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-direct-unwind.protos",
                        """
                        m: () => {
                            Error.handle(() => {
                                (() => { ^7 }).ensure(cleanup)
                            }, (caught) => { handlerProbe() })
                            0
                        }
                        m()
                        """,
                        module);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "error=" + outcome.error());
        assertEquals(7L, integer(outcome.value()));
        assertEquals(1, cleanups.get(), "the crossed ensure cleanup runs exactly once");
        assertEquals(0, handlers.get(), "a non-local return never enters Error.handle");

        System.out.println("PLAT070_SELECTED_TRANSFER_FINALLY=RUN");
        System.out.println("PLAT070_SELECTED_TRANSFER_GUEST_CATCH=NO");
    }

    @Test
    void guestErrorsRemainCatchableUnderDirectUnwind() throws Exception {
        ProtosActivation module = module();
        AtomicInteger cleanups = new AtomicInteger();
        module.context().createLocalSlot("cleanup", counter(cleanups));

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-guest-error-lane.protos",
                        """
                        Error.handle(() => {
                            (() => { Error().signal() }).ensure(cleanup)
                        }, (caught) => { 5 })
                        """,
                        module);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "error=" + outcome.error());
        assertEquals(5L, integer(outcome.value()));
        assertEquals(1, cleanups.get());

        System.out.println("PLAT070_GUEST_ERROR_LANE=UNCHANGED");
    }

    @Test
    void unselectedControlFlowExceptionIsNeverConvertedByAPreparedOwner()
            throws Exception {
        ProtosActivation caller = module();
        ProtosReturnHome home = new ProtosReturnHome();

        ControlFlowException foreign = new ControlFlowException();

        ProtosClosureValue foreignBody =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            throw foreign;
                        });

        ControlFlowException observedForeign =
                assertThrows(
                        ControlFlowException.class,
                        () -> ProtosTestExecutionSupport.callEntered(
                                () -> ProtosFunctionCallDispatchers
                                        .callFunctionUncached(
                                                foreignBody,
                                                new Object[0],
                                                caller)));

        assertSame(
                foreign,
                observedForeign,
                "the shared dispatcher must preserve foreign control identity");

        ProtosNonLocalReturnException nonLocalReturn =
                new ProtosNonLocalReturnException(
                        home, ProtosNullValue.INSTANCE);

        ProtosClosureValue nonLocalBody =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            throw nonLocalReturn;
                        });

        ProtosNonLocalReturnException observedReturn =
                assertThrows(
                        ProtosNonLocalReturnException.class,
                        () -> ProtosTestExecutionSupport.callEntered(
                                () -> ProtosFunctionCallDispatchers
                                        .callFunctionUncached(
                                                nonLocalBody,
                                                new Object[0],
                                                caller)));

        assertSame(
                nonLocalReturn,
                observedReturn,
                "the original non-local return transfer must be preserved");

        assertTrue(
                home.isActive(),
                "an unrelated invocation must not close this ReturnHome");

        System.out.println("PLAT070_FOREIGN_CONTROL_CONVERTED=NO");
        System.out.println("PLAT070_ORIGINAL_CFE_IDENTITY=PRESERVED");
    }

    private static ProtosClosureValue counter(AtomicInteger count) {
        ProtosNativeClosureBody body =
                (activation, supplied) -> {
                    count.incrementAndGet();
                    return ProtosNullValue.INSTANCE;
                };
        return ProtosClosureValue.suspensionCapableNativeClosure(body, body);
    }

    private static long integer(Object value) {
        return assertInstanceOf(Long.class, value);
    }

    private static ProtosActivation module() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"))
                .newModuleActivation();
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
