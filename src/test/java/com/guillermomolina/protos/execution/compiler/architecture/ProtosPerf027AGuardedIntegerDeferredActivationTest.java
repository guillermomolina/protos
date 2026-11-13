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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosExecutionContextValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
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
 * PERF027-A established the deferred native path after exact PERF016 guarded
 * Integer selection. The PERF025 pay-as-you-grow extension covered here executes
 * successful exact canonical local Integer arithmetic through an immediate
 * result shape with no rich method Activation. Error-producing, wrong-domain,
 * inherited and otherwise non-canonical cases retain the PERF027-A deferred
 * selected-Closure path. Guest-level Integer semantics remain covered by
 * protos/tests/conformance/integer.
 */
final class ProtosPerf027AGuardedIntegerDeferredActivationTest {
    private static final BigInteger HUGE = BigInteger.TWO.pow(200).add(BigInteger.ONE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @Test
    void successfulCanonicalArithmeticExecutesWithoutGeneralNativeInvocationState()
            throws Exception {
        inEnteredContext(prelude -> {
            Long receiver = Long.valueOf(Long.MAX_VALUE);
            ProtosCallRequest prepared = send(prelude, receiver, "+", integer(1));

            ProtosClosureValue selected =
                    (ProtosClosureValue)
                            ProtosValueLookup.lookup(receiver, "+", prelude)
                                    .orElseThrow()
                                    .value();
            assertTrue(selected.nativeBody().isPresent());
            assertSame(selected, ((ProtosBoundMethod) prepared.callable()).callable());

            Object result = ProtosNumericValueSupport.guestValue(prepared.callUncached());
            assertEquals(String.valueOf(LONG_MAX.add(BigInteger.ONE)), integerValue(result));
            // I115: the builtin root materializes the method activation from its
            // B5 header; the former immediate-result shape no longer exists.
            assertTrue(
                    !ProtosFrameArguments.hasPublishedActivation(prepared.arguments()),
                    "successful canonical Integer hit must not own a rich Activation");
        });
    }

    @Test
    void representativeOperationsRetainExactResults() throws Exception {
        inEnteredContext(prelude -> {
            assertEquals(String.valueOf(HUGE.subtract(BigInteger.ONE)), integerValue(run(prelude, com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(HUGE, prelude), "-", integer(1))));
            assertEquals(String.valueOf(HUGE.multiply(HUGE)), integerValue(run(prelude, com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(HUGE, prelude), "*",
                            com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(HUGE, prelude))));
            assertEquals(String.valueOf(HUGE.divide(BigInteger.valueOf(7))), integerValue(run(prelude, com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(HUGE, prelude), "div", integer(7))));
            assertEquals(String.valueOf(HUGE.mod(BigInteger.valueOf(7))), integerValue(run(prelude, com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(HUGE, prelude), "mod", integer(7))));
            assertSame(Boolean.TRUE,
                    run(prelude, com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(HUGE, prelude), ">", integer(0)));
            assertSame(Boolean.FALSE, run(prelude, integer(0), ">", integer(0)));
        });
    }

    /**
     * D197-C: one selection serves both concrete Integer families, because the
     * inherited canonical operation has the same Closure and Integer home
     * whether lookup starts at SmallInteger or at BigInteger. I115: GetMethod
     * performs the selection.
     */
    @Test
    void oneGuardServesBothConcreteIntegerFamilies() throws Exception {
        inEnteredContext(prelude -> {
            Object big = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(HUGE, prelude);
            ProtosActivation caller = prelude.newModuleActivation();
            ProtosBoundMethod fromSmall = (ProtosBoundMethod)
                    ProtosExperimentalGetMethodNode.select(integer(7), "+", null, caller);
            ProtosBoundMethod fromBig = (ProtosBoundMethod)
                    ProtosExperimentalGetMethodNode.select(big, "+", null, caller);
            assertSame(prelude.integerPrototype(), fromSmall.methodHome());
            assertSame(fromSmall.callable(), fromBig.callable());
            assertSame(fromSmall.methodHome(), fromBig.methodHome());

            Object result = run(prelude, big, "+", integer(1));
            assertEquals(String.valueOf(HUGE.add(BigInteger.ONE)), integerValue(result));
            assertSame(
                    prelude.bigIntegerPrototype(),
                    ((com.guillermomolina.protos.runtime.ProtosObjectValue) result)
                            .parent().orElseThrow());

            // A family-local slot is never the shared Integer selection.
            ProtosBoundMethod local = (ProtosBoundMethod)
                    ProtosExperimentalGetMethodNode.select(integer(7), "recognizes", null, caller);
            assertNotSame(prelude.integerPrototype(), local.methodHome());
        });
    }

    @Test
    void exactQuotientStaysImmediateAndFractionArgumentDefersToSelectedMethod()
            throws Exception {
        inEnteredContext(prelude -> {
            // D197-C: an integral quotient is an Integer, never a Float.
            assertEquals(4L, assertInstanceOf(
                    Long.class,
                    run(prelude, integer(8), "/", integer(2))).longValue());

            Object half = run(prelude, integer(7), "/", integer(2));
            assertTrue(ProtosNumericValueSupport.isFraction(half));

            // A Fraction operand: the selected native Integer method computes the
            // exact rational result.
            assertTrue(ProtosNumericValueSupport.isFraction(run(prelude, integer(1), "+", half)));
        });
    }

    @Test
    void allCanonicalLocalIntegerOperationsCarryDirectCapability()
            throws Exception {
        inEnteredContext(prelude -> {
            ProtosActivation caller = prelude.newModuleActivation();
            for (String selector : new String[] {"+", "-", "*", "/", "div", "mod"}) {
                ProtosBoundMethod selected = (ProtosBoundMethod)
                        ProtosExperimentalGetMethodNode.select(integer(7), selector, null, caller);
                assertSame(prelude.integerPrototype(), selected.methodHome(), selector);
                assertTrue(selected.callable().nativeBody().isPresent(), selector);
                assertNotNull(run(prelude, integer(7), selector, integer(2)), selector);
            }

            ProtosBoundMethod ordering = (ProtosBoundMethod)
                    ProtosExperimentalGetMethodNode.select(integer(7), ">", null, caller);
            assertNotSame(
                    prelude.integerPrototype(),
                    ordering.methodHome(),
                    "inherited Number ordering remains outside this bounded slice");
            assertSame(Boolean.TRUE, run(prelude, integer(7), ">", integer(2)));
        });
    }

    @Test
    void zeroDivisionSignalsTheSelectedActivationsError() throws Exception {
        inEnteredContext(prelude -> {
            for (String selector : new String[] {"div", "mod"}) {
                ProtosCallRequest prepared =
                        send(prelude, integer(1), selector, integer(0));
                ProtosSignalException signal =
                        assertThrows(ProtosSignalException.class, prepared::callUncached);
                assertSame(prelude.errorPrototype(), signal.error().parent().orElseThrow());
                // The selected method's activation stays observable after the Error path.
                ProtosActivation activation = ProtosFrameArguments.activation(prepared.arguments());
                assertNotNull(activation.context());
                assertEquals(1, activation.arguments().orElseThrow()
                        .indexedSnapshot().size());
            }
        });
    }

    @Test
    void wrongDomainArgumentSignalsError() throws Exception {
        inEnteredContext(prelude -> {
            ProtosCallRequest prepared =
                    send(prelude, integer(1), "+", Boolean.TRUE);
            ProtosSignalException signal =
                    assertThrows(ProtosSignalException.class, prepared::callUncached);
            assertSame(prelude.errorPrototype(), signal.error().parent().orElseThrow());
        });
    }

    @Test
    void separateContextsKeepIndependentDeferredFallbackActivations() throws Exception {
        ProtosActivation[] activations = new ProtosActivation[2];
        for (int index = 0; index < activations.length; index++) {
            int slot = index;
            inEnteredContext(prelude -> {
                ProtosCallRequest prepared =
                        send(prelude, integer(5), ">", integer(1));
                assertSame(Boolean.TRUE, prepared.callUncached());
                ProtosActivation activation = ProtosFrameArguments.activation(prepared.arguments());
                assertSame(prelude, activation.prelude().orElseThrow());
                activations[slot] = activation;
            });
        }
        assertNotSame(
                activations[0].prelude().orElseThrow(),
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
        return ProtosNumericValueSupport.guestValue(
                send(prelude, receiver, selector, argument).callUncached());
    }

    /** I115: an ordinary send: GetMethod selection, then the shared dispatcher. */
    private static ProtosCallRequest send(
            ProtosPrelude prelude, Object receiver, String selector, Object argument) {
        assertNotNull(ProtosLanguageContext.currentIfEnteredForRuntime());
        var generic = ProtosValueLookup.lookup(receiver, selector, prelude).orElseThrow();
        ProtosCallRequest request =
                ProtosCallRequest.send(
                        receiver, selector, prelude.newModuleActivation(), List.of(argument));
        assertSame(generic.value(), ((ProtosBoundMethod) request.callable()).callable());
        assertSame(generic.home(), ((ProtosBoundMethod) request.callable()).methodHome());
        return request;
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

    private static Long integer(long value) {
        return Long.valueOf(value);
    }

    private static String integerValue(Object value) {
        return com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value);
    }

    private static Object privateField(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
