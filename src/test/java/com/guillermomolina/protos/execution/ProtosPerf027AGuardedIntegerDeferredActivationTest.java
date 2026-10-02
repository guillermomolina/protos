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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedClosureCall;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosExecutionContextValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF027-A: the guarded Integer native send keeps the exact PERF016 selection
 * but prepares the selected method invocation through the PLAT040 deferred
 * activation, so a successful operation leaves the guest execution Context and
 * supplied guest Array unmaterialized while every observer still sees them.
 * Guest-level Integer semantics remain covered by protos/tests/conformance/integer.
 */
final class ProtosPerf027AGuardedIntegerDeferredActivationTest {
    private static final BigInteger HUGE = BigInteger.TWO.pow(200).add(BigInteger.ONE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @Test
    void successfulArithmeticKeepsSelectionAndDefersGuestInvocationState() throws Exception {
        inEnteredContext(prelude -> {
            ProtosIntegerValue receiver = new ProtosIntegerValue(LONG_MAX);
            PreparedClosureCall prepared = guardedSend(prelude, receiver, "+", integer(1));

            ProtosActivation activation = prepared.activation();
            ProtosClosureValue selected =
                    (ProtosClosureValue) ProtosValueLookup.lookup(receiver, "+", prelude)
                            .orElseThrow().value();
            assertTrue(prepared.isNative());
            assertTrue(selected.nativeBody().isPresent());
            assertSame(receiver, activation.receiver());
            assertSame(
                    ProtosValueLookup.lookup(receiver, "+", prelude).orElseThrow().home(),
                    activation.methodHome().orElseThrow());
            assertSame(prelude, activation.prelude().orElseThrow());
            assertTrue(activation.ownsReturnHome());
            assertTrue(activation.returnHome().orElseThrow().isActive());
            assertDeferred(activation);

            Object result = prepared.finish(prepared.enterNative());
            assertEquals(LONG_MAX.add(BigInteger.ONE), integerValue(result));
            assertDeferred(activation);
            assertTrue(!activation.returnHome().orElseThrow().isActive());

            ProtosArrayValue arguments = activation.arguments().orElseThrow();
            assertSame(arguments, activation.arguments().orElseThrow());
            assertEquals(1, arguments.indexedSnapshot().size());
            ProtosExecutionContextValue context =
                    assertInstanceOf(ProtosExecutionContextValue.class, activation.context());
            assertSame(context, activation.context());
        });
    }

    @Test
    void representativeOperationsRetainExactResults() throws Exception {
        inEnteredContext(prelude -> {
            assertEquals(HUGE.subtract(BigInteger.ONE),
                    integerValue(run(prelude, new ProtosIntegerValue(HUGE), "-", integer(1))));
            assertEquals(HUGE.multiply(HUGE),
                    integerValue(run(prelude, new ProtosIntegerValue(HUGE), "*",
                            new ProtosIntegerValue(HUGE))));
            assertEquals(HUGE.divide(BigInteger.valueOf(7)),
                    integerValue(run(prelude, new ProtosIntegerValue(HUGE), "div", integer(7))));
            assertEquals(HUGE.mod(BigInteger.valueOf(7)),
                    integerValue(run(prelude, new ProtosIntegerValue(HUGE), "mod", integer(7))));
            assertSame(ProtosBooleanValue.TRUE,
                    run(prelude, new ProtosIntegerValue(HUGE), ">", integer(0)));
            assertSame(ProtosBooleanValue.FALSE, run(prelude, integer(0), ">", integer(0)));
        });
    }

    @Test
    void zeroDivisionSignalsTheSelectedActivationsError() throws Exception {
        inEnteredContext(prelude -> {
            for (String selector : new String[] {"div", "mod"}) {
                PreparedClosureCall prepared = guardedSend(prelude, integer(1), selector, integer(0));
                ProtosSignalException signal =
                        assertThrows(ProtosSignalException.class, prepared::enterNative);
                assertSame(prelude.errorPrototype(), signal.error().parent().orElseThrow());
                // The deferred state stays observable after the Error path.
                assertNotNull(prepared.activation().context());
                assertEquals(1, prepared.activation().arguments().orElseThrow()
                        .indexedSnapshot().size());
            }
        });
    }

    @Test
    void wrongDomainArgumentSignalsError() throws Exception {
        inEnteredContext(prelude -> {
            PreparedClosureCall prepared =
                    guardedSend(prelude, integer(1), "+", ProtosBooleanValue.TRUE);
            ProtosSignalException signal =
                    assertThrows(ProtosSignalException.class, prepared::enterNative);
            assertSame(prelude.errorPrototype(), signal.error().parent().orElseThrow());
        });
    }

    @Test
    void separateContextsPrepareIndependentActivations() throws Exception {
        ProtosActivation[] activations = new ProtosActivation[2];
        for (int index = 0; index < activations.length; index++) {
            int slot = index;
            inEnteredContext(prelude -> {
                PreparedClosureCall prepared = guardedSend(prelude, integer(5), "-", integer(1));
                assertEquals(BigInteger.valueOf(4), integerValue(prepared.enterNative()));
                assertSame(prelude, prepared.activation().prelude().orElseThrow());
                activations[slot] = prepared.activation();
            });
        }
        assertNotSame(activations[0].prelude().orElseThrow(),
                activations[1].prelude().orElseThrow());
    }

    private interface EnteredBody {
        void run(ProtosPrelude prelude) throws Exception;
    }

    private static void inEnteredContext(EnteredBody body) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                body.run(new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core")));
            } finally {
                context.leave();
            }
        }
    }

    private static Object run(
            ProtosPrelude prelude, Object receiver, String selector, Object argument) {
        PreparedClosureCall prepared = guardedSend(prelude, receiver, selector, argument);
        return prepared.finish(prepared.enterNative());
    }

    private static PreparedClosureCall guardedSend(
            ProtosPrelude prelude, Object receiver, String selector, Object argument) {
        ProtosLanguageContext entered = ProtosLanguageContext.currentIfEnteredForRuntime();
        assertNotNull(entered);
        PrepareSendArguments.GuardedIntegerSend cached =
                PrepareSendArguments.createGuardedIntegerSend(receiver, selector, prelude);
        assertNotNull(cached, "standard native Integer selection must stay guarded");
        var generic = ProtosValueLookup.lookup(receiver, selector, prelude).orElseThrow();
        assertSame(generic.value(), cached.closure());
        assertSame(generic.home(), cached.methodHome());

        ProtosActivation caller =
                ProtosActivation.forClosureInvocation(cached.closure(), List.of(), prelude);
        return PrepareSendArguments.guardedIntegerSend(
                receiver, selector, caller, new Object[] {argument},
                entered, prelude, selector, entered, prelude, cached);
    }

    private static void assertDeferred(ProtosActivation activation) throws Exception {
        assertNull(privateField(activation, "context"),
                "guarded Integer success must not materialize the guest Context");
        assertNull(privateField(activation, "arguments"));
        Object deferred = privateField(activation, "deferredSuppliedArguments");
        assertNotNull(deferred, "guarded Integer send must use the PLAT040 deferred activation");
        assertNull(privateField(deferred, "guestArray"),
                "guarded Integer success must not materialize the guest argument Array");
    }

    private static ProtosIntegerValue integer(long value) {
        return new ProtosIntegerValue(BigInteger.valueOf(value));
    }

    private static BigInteger integerValue(Object value) {
        return assertInstanceOf(ProtosIntegerValue.class, value).value();
    }

    private static Object privateField(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
