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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I115: focal smoke test of the Bytecode DSL lowering of ifTrue, ifFalse,
 * ifTrueIfFalse, whileTrue, whileFalse, untilTrue and untilFalse. Results
 * only; nothing here measures time or depends on compilation.
 */
final class ProtosI115StructuredControlSmokeTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void conditionalsSelectExactlyTheirCallback() throws Exception {
        withCore(module -> {
            assertInteger(1, run(module, """
                    n: 0
                    true.ifTrue(() => { n = n + 1 })
                    false.ifTrue(() => { n = n + 10 })
                    n
                    """));
            assertInteger(1, run(module, """
                    n: 0
                    false.ifFalse(() => { n = n + 1 })
                    true.ifFalse(() => { n = n + 10 })
                    n
                    """));
            assertInteger(12, run(module, """
                    trace: 0
                    true.ifTrueIfFalse(() => { trace = trace * 10 + 1 }, () => { trace = trace * 10 + 3 })
                    false.ifTrueIfFalse(() => { trace = trace * 10 + 4 }, () => { trace = trace * 10 + 2 })
                    trace
                    """));
        });
    }

    @Test
    void conditionalExpressionsYieldExactResultOrNull() throws Exception {
        withCore(module -> {
            assertInteger(7, run(module, "true.ifTrue(() => { 7 })"));
            assertSame(Boolean.TRUE, run(module, "false.ifTrue(() => { 7 }) === null"));
            assertSame(Boolean.TRUE, run(module, "true.ifFalse(() => { 7 }) === null"));
            assertInteger(2, run(module, "false.ifTrueIfFalse(() => { 1 }, () => { 2 })"));
            assertInteger(5, run(module, """
                    value: true.ifTrue(() => { 5 })
                    value
                    """));
        });
    }

    @Test
    void operandsAndSelectedCallbackAreEvaluatedOnce() throws Exception {
        withCore(module -> {
            assertInteger(11, run(module, """
                    count: 0
                    condition: () => {
                        count = count + 1
                        true
                    }
                    condition().ifTrueIfFalse(() => { count = count + 10 }, () => { count = count + 100 })
                    count
                    """));
            assertInteger(1000, run(module, """
                    count: 0
                    pick: () => {
                        count = count + 1000
                        () => { 0 }
                    }
                    false.ifTrue(pick())
                    count
                    """));
        });
    }

    @Test
    void preTestLoopsRunZeroOrSeveralIterations() throws Exception {
        withCore(module -> {
            assertInteger(3, run(module, """
                    n: 0
                    (() => { n < 3 }).whileTrue() { n = n + 1 }
                    n
                    """));
            assertInteger(3, run(module, """
                    n: 0
                    (() => { n >= 3 }).whileFalse() { n = n + 1 }
                    n
                    """));
            assertInteger(0, run(module, """
                    n: 0
                    (() => { false }).whileTrue() { n = n + 1 }
                    n
                    """));
            assertInteger(0, run(module, """
                    n: 0
                    (() => { true }).whileFalse() { n = n + 1 }
                    n
                    """));
            assertSame(Boolean.TRUE, run(module, """
                    value: (() => { false }).whileTrue() { 1 }
                    value === null
                    """));
        });
    }

    @Test
    void postTestLoopsRunTheBodyBeforeTheCondition() throws Exception {
        withCore(module -> {
            assertInteger(12, run(module, """
                    trace: 0
                    (() => { trace = trace * 10 + 1 }).untilTrue(() => {
                        trace = trace * 10 + 2
                        true
                    })
                    trace
                    """));
            assertInteger(12, run(module, """
                    trace: 0
                    (() => { trace = trace * 10 + 1 }).untilFalse(() => {
                        trace = trace * 10 + 2
                        false
                    })
                    trace
                    """));
            assertInteger(3, run(module, """
                    n: 0
                    (() => { n = n + 1 }).untilTrue(() => { n >= 3 })
                    n
                    """));
            assertInteger(3, run(module, """
                    n: 0
                    (() => { n = n + 1 }).untilFalse(() => { n < 3 })
                    n
                    """));
        });
    }

    @Test
    void loopExpressionKeepsTheLastBodyResult() throws Exception {
        withCore(module -> {
            assertInteger(30, run(module, """
                    n: 0
                    (() => { n < 3 }).whileTrue() {
                        n = n + 1
                        n * 10
                    }
                    """));
            assertInteger(20, run(module, """
                    n: 0
                    (() => {
                        n = n + 1
                        n * 10
                    }).untilTrue(() => { n >= 2 })
                    """));
        });
    }

    @Test
    void nonBooleanConditionSignalsAGuestError() throws Exception {
        withCore(module -> {
            assertInteger(42, run(module, """
                    Error.handle(() => {
                        (() => { 1 }).whileTrue() { 0 }
                        0
                    }, (caught) => { 42 })
                    """));
            assertInteger(42, run(module, """
                    Error.handle(() => {
                        (() => { 0 }).untilFalse(() => { null })
                        0
                    }, (caught) => { 42 })
                    """));
            assertInteger(42, run(module, """
                    Error.handle(() => {
                        null.ifTrue(() => { 0 })
                        0
                    }, (caught) => { 42 })
                    """));
        });
    }

    /*
     * VALUES_AND_COLLECTIONS: ifTrue, ifFalse and ifTrueIfFalse are ordinary
     * messages. A site that is not lowered internally (here, a spread argument
     * list) reaches the standard behavior by ordinary dispatch; a receiver
     * that only delegates to true selects that standard behavior and fails
     * its receiver-domain check, on both the generic and the lowered site.
     */
    @Test
    void genericSendsReachTheStandardConditionalBehavior() throws Exception {
        withCore(module -> {
            assertInteger(5, run(module, "true.ifTrue(...[() => { 5 }])"));
            assertSame(Boolean.TRUE,
                    run(module, "false.ifTrue(...[() => { 5 }]) === null"));
            assertInteger(4, run(module, "false.ifFalse(...[() => { 4 }])"));
            assertInteger(1, run(module, "true.ifTrueIfFalse(...[() => { 1 }, () => { 2 }])"));
            assertInteger(2, run(module, "false.ifTrueIfFalse(...[() => { 1 }, () => { 2 }])"));
            // The unselected callback is neither validated nor invoked.
            assertInteger(1, run(module, "true.ifTrueIfFalse(...[() => { 1 }, 17])"));
            assertInteger(42, run(module, """
                    delegate: (true) { }
                    Error.handle(() => { delegate.ifTrue(...[() => { 1 }]) }, (caught) => { 42 })
                    """));
            assertInteger(42, run(module, """
                    delegate: (true) { }
                    select: (condition) => { condition.ifTrue(() => { 1 }) }
                    Error.handle(() => { select(delegate) }, (caught) => { 42 })
                    """));
        });
    }

    /*
     * Squeak/Pharo mustBeBoolean: a non-Boolean receiver of an inline
     * conditional receives a genuine callback created over the live frame.
     * Its reads and writes reach the same binding as the inline execution,
     * including after the creating activation has returned, and the inline
     * path keeps that binding in a plain local (no cell).
     */
    @Test
    void customReceiverGetsCallbackOverTheLiveFrame() throws Exception {
        withCore(module -> {
            evaluate("""
                    keep: null
                    twice: { ifTrue: (callback) => { callback()\n callback() } }
                    later: { ifTrue: (callback) => { keep = callback\n 0 } }
                    count: (condition) => {
                        n: 1
                        condition.ifTrue(() => { n = n + 10 })
                        n
                    }
                    """, "i115-control-custom-define.protos", module);
            assertInteger(11, evaluate("count(true)\n", "i115-control-custom-true.protos", module));
            assertSame(Boolean.TRUE,
                    evaluate("count(false) == 1\n", "i115-control-custom-false.protos", module));
            assertInteger(21, evaluate("count(twice)\n", "i115-control-custom-twice.protos", module));
            assertInteger(1, evaluate("count(later)\n", "i115-control-custom-later.protos", module));
            assertInteger(11, evaluate("keep()\n", "i115-control-custom-keep1.protos", module));
            assertInteger(21, evaluate("keep()\n", "i115-control-custom-keep2.protos", module));

            ProtosClosureValue count = assertInstanceOf(ProtosClosureValue.class,
                    evaluate("count\n", "i115-control-custom-count.protos", module));
            ProtosBytecodeRootNode root =
                    count.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
            root.getRootNodes().ensureComplete();
            List<String> names = root.getBytecodeNode().getInstructionsAsList().stream()
                    .map(Instruction::getName)
                    .toList();
            assertTrue(names.stream().noneMatch(name -> isInstruction(name, "c.CreateCell")),
                    names::toString);
            assertTrue(names.stream().anyMatch(
                    name -> isInstruction(name, "c.MaterializeSelectedCallback")), names::toString);
        });
    }

    @Test
    void dynamicCallbackIsAnOrdinaryNilaryCall() throws Exception {
        withCore(module -> {
            evaluate("apply: (condition, callback) => { condition.ifTrue(callback) }\n",
                    "i115-control-apply-define.protos", module);
            assertInteger(5, evaluate("apply(true, () => { 5 })\n",
                    "i115-control-apply-true.protos", module));
            assertSame(Boolean.TRUE, evaluate("apply(false, () => { 5 }) === null\n",
                    "i115-control-apply-false.protos", module));

            ProtosClosureValue apply = assertInstanceOf(ProtosClosureValue.class,
                    evaluate("apply\n", "i115-control-apply.protos", module));
            ProtosBytecodeRootNode root =
                    apply.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
            root.getRootNodes().ensureComplete();
            List<String> names = root.getBytecodeNode().getInstructionsAsList().stream()
                    .map(Instruction::getName)
                    .toList();
            assertEquals(1,
                    names.stream().filter(name -> isInstruction(name, "c.CallNilaryMethod")).count(),
                    names::toString);
            assertTrue(names.stream().anyMatch(name -> isInstruction(name, "c.CanonicalControlCondition")),
                    names::toString);
            // VALUES_AND_COLLECTIONS: ifTrue is an ordinary message, so a
            // non-Boolean receiver selects its own method; that selection
            // appears only behind the standard Boolean guard.
            int guard = java.util.stream.IntStream.range(0, names.size())
                    .filter(index -> isInstruction(names.get(index), "c.IsCanonicalBooleanOperand"))
                    .findFirst()
                    .orElse(-1);
            assertTrue(guard >= 0, names::toString);
            assertTrue(names.subList(0, guard).stream()
                            .noneMatch(name -> isInstruction(name, "c.GetMethod")),
                    names::toString);
            assertTrue(names.subList(guard, names.size()).stream()
                            .anyMatch(name -> isInstruction(name, "c.GetMethod")),
                    names::toString);
        });
    }

    private static boolean isInstruction(String name, String operation) {
        return name.equals(operation) || name.startsWith(operation + "$");
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

    /**
     * Evaluates {@code body} as the body of a fresh {@code run} Closure and
     * calls it, in a fresh module of the same Prelude: creating {@code run}
     * again in one module is a slot-creation error (EXECUTION_AND_CONTROL).
     */
    private static Object run(ProtosActivation module, String body) {
        return evaluate("run: () => {\n" + body + "\n}\nrun()\n",
                "i115-control-smoke.protos",
                module.prelude().orElseThrow().newModuleActivation());
    }

    private static Object evaluate(String characters, String name, ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, name)
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        try {
            return target.call(activation);
        } catch (ProtosSignalException signal) {
            throw new AssertionError(describe(signal) + "\n" + characters, signal);
        }
    }

    /** The guest error's message and the instruction and source position that signalled it. */
    private static String describe(ProtosSignalException signal) {
        StringBuilder text = new StringBuilder("guest error");
        signal.error().readLocalSlot("message")
                .ifPresent(message -> text.append(" message=").append(message));
        BytecodeNode origin = signal.originBytecodeForRuntime();
        if (origin != null) {
            BytecodeLocation location =
                    origin.getBytecodeLocation(signal.originBytecodeIndexForRuntime());
            text.append(" instruction=").append(location.getInstruction())
                    .append(" source=").append(location.getSourceLocation());
        }
        return text.toString();
    }

    private static void assertInteger(long expected, Object actual) {
        assertEquals(
                Long.toString(expected),
                ProtosNumericValueSupport.integerDecimalText(actual));
    }
}
