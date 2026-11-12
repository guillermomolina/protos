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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;

import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF025-H1 focal evidence, reconciled with I115: every Closure root binds
 * its parameters through the one unified parameter lowering. Each supplied
 * positional argument is copied as a contiguous group when all parameters
 * use LOCAL storage without defaults or rest. Other parameters use constant
 * positional reads, presence tests for defaults and exact suffix reads for
 * rest, storing into the same LOCAL or CELL storage.
 *
 * <p>Defaults execute in the real activation and can observe or change its
 * Context. Parameter establishment after a default therefore applies ordinary
 * creation checks. The guest assertions cover reflection, duplicate creation,
 * structural state, capture by reference and removal/recreation.
 */
final class ProtosPerf025H1IndexedParameterEstablishmentTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final String SUPPLIED = "LoadSuppliedArgument";
    private static final String HAS_SUPPLIED = "HasSuppliedArgument";
    private static final String REST = "LoadRestArguments";
    private static final String CREATE_CELL = "CreateCell";
    private static final String STORE_CELL = "StoreCell";
    /* Retired parameter and authority operations; none may reappear. */
    private static final List<String> RETIRED =
            List.of(
                    "InstallFrameLexicalAuthority",
                    "BindClosureIndexedParameter",
                    "BindClosureIndexedRest",
                    "BindClosureParameter",
                    "BindClosureRest",
                    "BindClosureFrameParameter",
                    "BindUnaryFrameParameter");

    @Test
    void parametersAreBoundThroughTheUnifiedParameterLowering() throws Exception {
        withCore(module -> {
            ProtosClosureValue required =
                    closure("(value) => { here: context\nvalue }", module);
            List<String> requiredNames = instructionNames(required);
            assertContains(requiredNames, "CopySuppliedArguments");
            assertNone(requiredNames, SUPPLIED);
            assertNone(requiredNames, HAS_SUPPLIED);
            assertNoRetired(requiredNames);

            ProtosClosureValue defaults =
                    closure("probe: (c) => 3\n"
                            + "(a, b: a, c: probe(context)) => { here: context\nc }", module);
            List<String> defaultNames = instructionNames(defaults);
            assertNoRetired(defaultNames);
            // a, b and c each read their own supplied position; b and c test it first.
            assertEquals(
                    List.<Object>of(0, 1, 2),
                    constantArguments(defaults, SUPPLIED, "index").stream()
                            .distinct().sorted().toList());
            assertEquals(
                    List.<Object>of(1, 2),
                    constantArguments(defaults, HAS_SUPPLIED, "index").stream()
                            .distinct().sorted().toList());

            ProtosClosureValue rest =
                    closure("(head, ...tail) => { here: context\ntail }", module);
            List<String> restNames = instructionNames(rest);
            assertContains(restNames, REST);
            assertNoRetired(restNames);

            ProtosObjectValue token = newObject();
            ProtosActivation activation = invocation(required, module, List.of(token));
            assertSame(token, plan(required).executeBytecodeActivationForTesting(activation));
        });
        System.out.println("STATIC_PARAMETER_USES_UNIFIED_PARAMETER_BINDING=YES");
        System.out.println("GENERIC_NAME_LOOKUP_FOR_STATIC_PARAMETER=NO");
    }

    @Test
    void suppliedPositionAndRestPrefixAreInstructionConstants() throws Exception {
        withCore(module -> {
            ProtosClosureValue rest =
                    closure("(head, ...tail) => { here: context\ntail }", module);
            assertEquals(
                    List.<Object>of(0),
                    constantArguments(rest, SUPPLIED, "index"),
                    "PERF030-I: the supplied parameter position is an instruction constant");
            assertEquals(
                    List.<Object>of(1),
                    constantArguments(rest, REST, "positionalParametersBeforeRest"),
                    "PERF030-I: the rest prefix length is an instruction constant");
        });
        System.out.println("PERF030_I_PARAMETER_CONSTANT_POSITION=YES");
    }

    @Test
    void sequentialDefaultsKeepCurrentAndLaterParametersAbsent() throws Exception {
        withCore(module -> {
            List<Object> results =
                    array(evaluate(
                            "value: 5\n"
                                    + "later: 6\n"
                                    + "marker: 7\n"
                                    + "pair: (a, b: a) => { here: context\nb }\n"
                                    + "own: (value: value) => { here: context\nvalue }\n"
                                    + "ahead: (first: later, later: marker) => { here: context\n[first, later] }\n"
                                    + "[pair(1), pair(1, 2), own(), ahead()]",
                            module));
            assertEquals(1L, integer(results.get(0)));
            assertEquals(2L, integer(results.get(1)));
            assertEquals(5L, integer(results.get(2)));
            List<Object> ahead = array(results.get(3));
            assertEquals(6L, integer(ahead.get(0)));
            assertEquals(7L, integer(ahead.get(1)));
        });
        System.out.println("LEFT_TO_RIGHT_PARAMETER_BINDING=PASS");
        System.out.println("DEFAULT_SEMANTICS=PASS");
    }

    @Test
    void staticIdentityNeverImpliesStaticPresence() throws Exception {
        withCore(module -> {
            List<Object> results =
                    array(evaluate(
                            "seen: { ctx: null }\n"
                                    + "plant: (c) => { seen.ctx = c\nc.b: 1\n0 }\n"
                                    + "plantNull: (c) => { c.b: null\n0 }\n"
                                    + "cycle: (c) => { c.b: 1\nc.removeSlot(\"b\")\nc.w: 3\n0 }\n"
                                    + "planted: (a: plant(context), b: 2) => { here: context\nb }\n"
                                    + "plantedNull: (a: plantNull(context), b: 2) => { here: context\nb }\n"
                                    + "recreated: (a: cycle(context), b: 2) => {\n"
                                    + "    here: context\n"
                                    + "    [b, here.hasSlot(\"b\"), here.slotNames()]\n"
                                    + "}\n"
                                    + "duplicate: false\n"
                                    + "duplicateNull: false\n"
                                    + "Error.handle(() => { planted()\nnull }, (error) => { duplicate = true\nnull })\n"
                                    + "Error.handle(() => { plantedNull()\nnull }, (error) => {\n"
                                    + "    duplicateNull = true\n"
                                    + "    null\n"
                                    + "})\n"
                                    + "[duplicate, seen.ctx.slotValue(\"b\"), duplicateNull, recreated()]",
                            module));
            assertTrue(bool(results.get(0)), "a PRESENT parameter binding is a duplicate creation");
            assertEquals(1L, integer(results.get(1)), "the PRESENT binding was not overwritten");
            assertTrue(bool(results.get(2)), "PRESENT(null) is not ABSENT");
            List<Object> recreated = array(results.get(3));
            assertEquals(2L, integer(recreated.get(0)));
            assertTrue(bool(recreated.get(1)));
            // slotNames is reflection in Unicode scalar order, not establishment order.
            assertEquals(List.of("a", "b", "here", "w"), strings(recreated.get(2)));

            ProtosClosureValue cycled =
                    // Same module activation as above: a fresh module-level name.
                    closure("cycleAgain: (c) => { c.b: 1\nc.removeSlot(\"b\")\nc.w: 3\n0 }\n"
                            + "(a: cycleAgain(context), b: 2) => { here: context\nb }", module);
            assertContains(instructionNames(cycled), HAS_SUPPLIED);
            assertNoRetired(instructionNames(cycled));
            ProtosActivation activation = invocation(cycled, module, List.of());
            assertEquals(2L, integer(plan(cycled).executeBytecodeActivationForTesting(activation)));
        });
        System.out.println("STATIC_BINDING_PRESENCE=NO");
        System.out.println("DUPLICATE_CREATION_ERROR=PASS");
        System.out.println("PRESENT_NULL_DISTINCT_FROM_ABSENT=PASS");
        System.out.println("D179_C0_REMOVE_RECREATE=PASS");
        System.out.println("ESTABLISHMENT_ORDER_SEMANTICS=PASS");
    }

    @Test
    void defaultsCannotPrecreateRequiredCapturedOrRestParameters() throws Exception {
        withCore(module -> {
            List<Object> results = array(evaluate(
                    "plant: (c) => { c.b: 1\n0 }\n"
                            + "plantTail: (c) => { c.tail: []\n0 }\n"
                            + "required: (a: plant(context), b) => b\n"
                            + "captured: (a: plant(context), b: 2) => { get: () => b\nget() }\n"
                            + "rest: (a: plantTail(context), ...tail) => tail\n"
                            + "own: (b: plant(context)) => b\n"
                            + "[Error.handle(() => required(), (e) => true),\n"
                            + " Error.handle(() => captured(), (e) => true),\n"
                            + " Error.handle(() => rest(), (e) => true),\n"
                            + " Error.handle(() => own(), (e) => true)]",
                    module));
            assertEquals(4, results.size());
            for (Object result : results) {
                assertTrue(bool(result), "parameter establishment rejects a pre-existing binding");
            }
        });
    }

    @Test
    void nonOpenContextTakesTheNamedCreationRule() throws Exception {
        withCore(module -> {
            List<Object> results =
                    array(evaluate(
                            "closeIt: (c) => { c.close()\n0 }\n"
                                    + "freezeIt: (c) => { c.freeze()\n0 }\n"
                                    + "closing: (a: 1, b: closeIt(context), c: 3) => { here: context\nc }\n"
                                    + "freezing: (a: 1, b: freezeIt(context), c: 3) => { here: context\nc }\n"
                                    + "closedFailed: false\n"
                                    + "frozenFailed: false\n"
                                    + "Error.handle(() => { closing()\nnull }, (error) => { closedFailed = true\nnull })\n"
                                    + "Error.handle(() => { freezing()\nnull }, (error) => { frozenFailed = true\nnull })\n"
                                    + "[closedFailed, frozenFailed, closing(1, 2, 3), freezing(1, 2, 3)]",
                            module));
            assertTrue(bool(results.get(0)), "creation in a CLOSED context fails");
            assertTrue(bool(results.get(1)), "creation in a FROZEN context fails");
            assertEquals(3L, integer(results.get(2)));
            assertEquals(3L, integer(results.get(3)));

            // Once the activation's own Context is no longer OPEN, a NAME
            // creation through it is refused, as before.
            ProtosClosureValue required =
                    closure("(value) => { here: context\nvalue }", module);
            ProtosActivation activation = invocation(required, module, List.of(newObject()));
            plan(required).executeBytecodeActivationForTesting(activation);
            activation.context().close();
            assertThrows(
                    IllegalStateException.class,
                    () -> activation.createCurrentLocalSlotForRuntime("late", newObject()));
        });
        System.out.println("OPEN_CLOSED_FROZEN_RULES=PASS");
        System.out.println("GENERIC_DYNAMIC_PARAMETER_FALLBACK_PRESERVED=YES");
    }

    @Test
    void restIsAFreshFrozenExactSuffixEstablishedByOrdinal() throws Exception {
        withCore(module -> {
            List<Object> results =
                    array(evaluate(
                            "f: (head, ...tail) => { here: context\ntail }\n"
                                    + "[f(1, 2, 3), f(1, 2, 3), f(1)]",
                            module));
            ProtosArrayValue first = assertInstanceOf(ProtosArrayValue.class, results.get(0));
            ProtosArrayValue second = assertInstanceOf(ProtosArrayValue.class, results.get(1));
            ProtosArrayValue empty = assertInstanceOf(ProtosArrayValue.class, results.get(2));
            assertNotSame(first, second);
            for (ProtosArrayValue rest : List.of(first, second, empty)) {
                assertSame(ProtosObjectValue.MutationState.FROZEN, rest.mutationState());
            }
            assertEquals(List.of(2L, 3L), first.indexedSnapshot().stream()
                    .map(ProtosPerf025H1IndexedParameterEstablishmentTest::integer)
                    .toList());
            assertEquals(List.of(), empty.indexedSnapshot());

            ProtosClosureValue rest =
                    closure("(head, ...tail) => { here: context\ntail }", module);
            ProtosObjectValue a = newObject();
            ProtosObjectValue b = newObject();
            ProtosActivation activation = invocation(rest, module, List.of(a, b));
            ProtosArrayValue tail =
                    assertInstanceOf(
                            ProtosArrayValue.class,
                            plan(rest).executeBytecodeActivationForTesting(activation));
            assertNotSame(activation.arguments().orElseThrow(), tail);
            assertEquals(List.of(b), tail.indexedSnapshot());
            assertContains(instructionNames(rest), REST);
        });
        System.out.println("REST_SEMANTICS=PASS");
    }

    @Test
    void contextReflectionAndCapturesObserveTheSingleBinding() throws Exception {
        withCore(module -> {
            List<Object> reflected =
                    array(evaluate(
                            "pairOf: (x, y) => [x, y]\n"
                                    + "f: (a, b: pairOf(context.hasSlot(\"a\"), context.hasSlot(\"b\"))) => {\n"
                                    + "    here: context\n"
                                    + "    [b, here.hasSlot(\"b\"), here.slotValue(\"a\"), here.slotNames()]\n"
                                    + "}\n"
                                    + "f(7)",
                            module));
            List<Object> duringDefault = array(reflected.get(0));
            assertTrue(bool(duringDefault.get(0)), "an earlier parameter is visible to a later default");
            assertFalse(bool(duringDefault.get(1)), "a parameter is ABSENT during its own default");
            assertTrue(bool(reflected.get(1)));
            assertEquals(7L, integer(reflected.get(2)));
            assertEquals(List.of("a", "b", "here"), strings(reflected.get(3)));

            List<Object> captured =
                    array(evaluate(
                            "g: (x) => {\n"
                                    + "    get: () => x\n"
                                    + "    set: (v) => { x = v }\n"
                                    + "    set(5)\n"
                                    + "    first: get()\n"
                                    + "    here: context\n"
                                    + "    here.removeSlot(\"x\")\n"
                                    + "    here.x: 9\n"
                                    + "    [first, get(), x]\n"
                                    + "}\n"
                                    + "g(1)",
                            module));
            assertEquals(5L, integer(captured.get(0)));
            assertEquals(9L, integer(captured.get(1)));
            assertEquals(9L, integer(captured.get(2)));
            // A captured parameter is bound straight into its own cell.
            List<String> capturedNames =
                    instructionNames(closure("(x) => { get: () => x\nget }", module));
            assertContains(capturedNames, SUPPLIED);
            assertContains(capturedNames, CREATE_CELL);
            assertContains(capturedNames, STORE_CELL);
            assertNoRetired(capturedNames);
        });
        System.out.println("CONTEXT_REFLECTION=PASS");
        System.out.println("CAPTURE_BY_REFERENCE=PASS");
    }

    @Test
    void frameNativeParameterRootUsesTheUnifiedParameterBinding() throws Exception {
        withCore(module -> {
            ProtosClosureValue identity = closure("(value) => value", module);
            List<String> names = instructionNames(identity);
            assertEquals(1L, names.stream()
                    .filter(name -> name.contains("CopySuppliedArguments")).count());
            assertNone(names, SUPPLIED);
            assertNone(names, CREATE_CELL);
            assertNoRetired(names);
            assertEquals(4L, integer(evaluate("f: (value) => value\nf(4)", module)));
        });
        System.out.println("FRAME_NATIVE_PARAMETER_PATH_PRESERVED=YES");
    }

    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

    private static void withCore(CoreTest test) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                test.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    private static Object evaluate(String characters, ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, "perf025-h1-parameters.protos")
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        try {
            return target.call(activation);
        } catch (ProtosSignalException signal) {
            throw new AssertionError("guest snippet signaled:\n" + characters, signal);
        }
    }

    /** Evaluates a source whose final expression is the Closure under test. */
    private static ProtosClosureValue closure(String characters, ProtosActivation activation) {
        return assertInstanceOf(ProtosClosureValue.class, evaluate(characters, activation));
    }

    private static ProtosClosureExecutionPlan plan(ProtosClosureValue closure) {
        return closure.executionPlan().orElseThrow();
    }

    private static ProtosActivation invocation(
            ProtosClosureValue closure, ProtosActivation module, List<?> supplied) {
        return ProtosActivation.forClosureInvocation(
                closure,
                supplied,
                module.prelude().orElseThrow(),
                module.actorModuleState(),
                module.currentModuleKey().orElse(null),
                module.executionDomain());
    }

    private static List<String> instructionNames(ProtosClosureValue closure) {
        return plan(closure)
                .bytecodeActivationRootForTesting()
                .getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .map(ProtosInstructionNames::of)
                .toList();
    }

    /**
     * The immediate constant operand {@code argument} of every {@code
     * operation} instruction, in order; a stack operand fails the lookup.
     */
    private static List<Object> constantArguments(
            ProtosClosureValue closure, String operation, String argument) {
        return plan(closure)
                .bytecodeActivationRootForTesting()
                .getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .filter(instruction -> ProtosInstructionNames.of(instruction).contains(operation))
                .map(instruction -> {
                    Instruction.Argument constant =
                            instruction.getArguments().stream()
                                    .filter(candidate -> candidate.getName().equals(argument))
                                    .findFirst()
                                    .orElseThrow(() -> new AssertionError(
                                            "no constant " + argument + " operand of " + operation));
                    return switch (constant.getKind()) {
                        case CONSTANT -> constant.asConstant();
                        case INTEGER -> (Object) constant.asInteger();
                        default -> throw new AssertionError(argument + " is not a constant: " + constant);
                    };
                })
                .toList();
    }

    private static void assertNoRetired(List<String> names) {
        for (String retired : RETIRED) {
            assertNone(names, retired);
        }
    }

    private static void assertContains(List<String> names, String operation) {
        assertTrue(
                names.stream().anyMatch(name -> name.contains(operation)),
                () -> "expected " + operation + " in: " + names);
    }

    private static void assertNone(List<String> names, String operation) {
        assertTrue(
                names.stream().noneMatch(name -> name.contains(operation)),
                () -> "unexpected " + operation + " in: " + names);
    }

    private static ProtosObjectValue newObject() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static List<Object> array(Object value) {
        return assertInstanceOf(ProtosArrayValue.class, value).indexedSnapshot();
    }

    private static List<String> strings(Object value) {
        return array(value).stream()
                .map(element -> assertInstanceOf(ProtosStringValue.class, element).value())
                .toList();
    }

    private static boolean bool(Object value) {
        return assertInstanceOf(ProtosBooleanValue.class, value).value();
    }

    private static long integer(Object value) {
        return Long.parseLong(com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value));
    }
}
