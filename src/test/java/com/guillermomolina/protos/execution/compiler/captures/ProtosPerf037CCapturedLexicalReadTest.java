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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * PERF037-C root-level captured reads, reconciled with I115. A module-owned
 * binding is one {@code ReadGlobal} with constant name and depth operands. A
 * binding owned by a Closure root and captured by a nested Closure is one
 * cell, created by the owning activation and shared by reference with every
 * capturer at every nesting level; the read is a {@code LoadCell} of the
 * reader's own local, with no owner-frame selection and no name fallback.
 * These programs drive one captured read site through the uncached and
 * cached interpreters, from two activations of the same defining root, and
 * check owner tier transitions, current-value reads, PRESENT(null), the
 * deferred-to-materialized Context transition and per-activation isolation.
 *
 * <p>KNOWN I115 SEMANTIC DIVERGENCE (D179): a cell is never removed or
 * recreated, and a nearer dynamic binding does not shadow a CELL read. The
 * former nearer-late-creation, owner-removal retargeting, exhausted-chain
 * SlotNotFound and recreation assertions for Closure-owned bindings are not
 * asserted here.
 */
final class ProtosPerf037CCapturedLexicalReadTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final int WARM_UP_CALLS = 40;

    @Test
    void slotReadWorkloadUsesConstantOperandRootSelection() throws Exception {
        withCore(module -> {
            ProtosClosureValue run =
                    closure("holder: { value: 1 }\nrun: () => { holder.value }\nrun", module);
            // I115-R6: holder is module-owned (ROOT), so it is one ReadGlobal.
            List<Instruction> reads = instructionsOf(run, "ReadGlobal");
            assertEquals(
                    1,
                    reads.size(),
                    () -> "expected one module-global read in " + names(run));
            // The name operand is the interned TruffleString of the attribute.
            assertEquals("holder", constantArgument(reads.get(0), "name").toString());
            assertTrue(
                    (Integer) constantArgument(reads.get(0), "lexicalDepth") >= 1,
                    "the proven lexical depth is an immediate constant operand");
            assertTrue(instructionsOf(
                    run, "ReadCapturedFrameLocalAtRoot").isEmpty());
            assertTrue(instructionsOf(
                    run, "SelectCapturedMaterializedOwnerFrameAtRoot").isEmpty());
            assertTrue(instructionsOf(
                    run, "IsCapturedOwnerFrameSelected").isEmpty());
            assertTrue(instructionsOf(
                    run, "ReadCapturedFallbackAtRoot").isEmpty());
            assertTrue(instructionsOf(run, "LoadCell").isEmpty());
            assertFalse(
                    names(run).contains("load.local.mat"),
                    "a module-global read uses no materialized local read");

            for (int call = 0; call < WARM_UP_CALLS; call++) {
                assertEquals("1", integerValue(call(run, module)));
            }
            evaluate("holder.value = 5", module);
            assertEquals("5", integerValue(call(run, module)));
            evaluate("holder = { value: 6 }", module);
            assertEquals("6", integerValue(call(run, module)));
        });
        System.out.println("PERF037C_SLOT_READ_WORKLOAD=PASS");
    }

    /*
     * wrap owns holder; the reader is nested two Closures deep, so it reads
     * the cell wrap created, carried through the maker. The module's holder
     * is a farther binding of the same name.
     */
    private static final String NESTED_PROGRAM =
            "holder: { value: 1 }\n"
                    + "other: { value: 2 }\n"
                    + "observed: null\n"
                    + "noop: () => null\n"
                    + "probe: () => { observed = first() }\n"
                    + "wrap: (g) => {\n"
                    + "  g()\n"
                    + "  holder: { value: 10 }\n"
                    + "  () => { () => { holder.value } }\n"
                    + "}\n";

    @Test
    void capturedReadSurvivesOwnerTierTransitionAcrossTiersAndActivations()
            throws Exception {
        withCore(module -> {
            evaluate(NESTED_PROGRAM, module);
            ProtosClosureValue wrap = closure("wrap", module);
            ProtosClosureExecutionPlan ownerPlan = wrap.executionPlan().orElseThrow();
            ProtosBytecodeRootNode ownerRoot = ownerPlan.bytecodeActivationRootForTesting();
            ownerRoot.getBytecodeNode().setUncachedThreshold(1);
            assertEquals(BytecodeTier.UNCACHED, ownerRoot.getBytecodeNode().getTier());

            // A1: the owner root runs uncached; its cell escapes.
            ProtosClosureValue firstMaker =
                    assertInstanceOf(
                            ProtosClosureValue.class,
                            ownerPlan.executeBytecodeActivationForTesting(
                                    invocationOf(module, wrap, closure("noop", module))));
            assertEquals(
                    BytecodeTier.UNCACHED,
                    ownerRoot.getBytecodeNode().getTier(),
                    "A1 and its cell belong to the uncached owner tier");
            ProtosClosureValue first =
                    assertInstanceOf(ProtosClosureValue.class, call(firstMaker, module));
            module.context().createLocalSlot("first", first);

            BytecodeNode readerNode = bytecodeNode(first);
            readerNode.setUncachedThreshold(4);
            assertEquals(BytecodeTier.UNCACHED, readerNode.getTier());

            /*
             * A2: the same owner root enters the cached tier and reads A1's
             * escaped cell before A2's own holder exists (BUG018).
             */
            ProtosClosureValue secondMaker =
                    assertInstanceOf(
                            ProtosClosureValue.class,
                            ownerPlan.executeBytecodeActivationForTesting(
                                    invocationOf(module, wrap, closure("probe", module))));
            assertEquals(
                    BytecodeTier.CACHED,
                    ownerRoot.getBytecodeNode().getTier(),
                    "A2 must have moved the owner root to the cached tier");
            assertEquals("10", integerValue(evaluate("observed", module)));
            ProtosClosureValue second =
                    assertInstanceOf(ProtosClosureValue.class, call(secondMaker, module));
            assertSame(
                    bytecodeNode(first).getBytecodeRootNode(),
                    bytecodeNode(second).getBytecodeRootNode(),
                    "both readers are activations of one lowered root");
            assertFalse(instructionsOf(first, "LoadCell").isEmpty(),
                    () -> "the captured read must be a cell read: " + names(first));
            assertTrue(instructionsOf(first, "AccessNamedLexical").isEmpty(),
                    () -> "the captured read takes no name fallback: " + names(first));

            // Binding identity: each activation owns one cell, shared across nesting levels.
            ProtosLexicalCell firstCell = onlyCell(first);
            ProtosLexicalCell secondCell = onlyCell(second);
            assertSame(firstCell, onlyCell(firstMaker));
            assertSame(secondCell, onlyCell(secondMaker));
            assertNotSame(firstCell, secondCell);

            for (int call = 0; call < WARM_UP_CALLS; call++) {
                assertEquals("10", integerValue(call(first, module)));
                assertEquals("10", integerValue(call(second, module)));
            }
            assertEquals(
                    BytecodeTier.CACHED,
                    bytecodeNode(first).getTier(),
                    "the read site must also run in the cached interpreter");

            // The current owner value is read, not a specialized copy.
            evaluate("other.value = 3", module);
            firstCell.setForRuntime(evaluate("other", module));
            assertEquals("3", integerValue(call(first, module)));
            assertEquals("10", integerValue(call(second, module)),
                    "another activation of the same root keeps its own binding");

            /*
             * The nearer maker Context is materialized only now, through the
             * chain the reader shares by reference; materialization yields one
             * Context identity and never changes the captured read.
             */
            ProtosObjectValue firstNearer =
                    first.capturedLexicalEnvironmentForRuntime().context();
            assertSame(
                    firstNearer,
                    first.capturedLexicalEnvironmentForRuntime().context(),
                    "materialization yields one Context identity");
            assertEquals("3", integerValue(call(first, module)));
            assertEquals("10", integerValue(call(second, module)));
        });
        System.out.println("PERF037C_OWNER_TIER_TRANSITION=PASS");
        System.out.println("PERF037C_CACHED_AND_UNCACHED=PASS");
    }

    @Test
    void nearerPresentNullShadowsAndAbsenceSignals() throws Exception {
        withCore(module -> {
            evaluate(
                    "slot: 1\n"
                            + "makeSlot: () => { () => { slot } }\n"
                            + "reader: makeSlot()\n",
                    module);
            ProtosClosureValue reader = closure("reader", module);
            for (int call = 0; call < WARM_UP_CALLS; call++) {
                assertEquals("1", integerValue(call(reader, module)));
            }

            /*
             * D179 C0: a reader whose creating activation never exposes its
             * Context cannot have a nearer binding created between it and
             * the module (guest code creates one only through an observed
             * Context), so the module binding is its nearest selected binding
             * and the read stays the static module read.
             */
            assertEquals(1, instructionsOf(reader, "ReadGlobal").size(), () -> names(reader).toString());

            /*
             * A reader whose creator exposes its Context selects live: a
             * nearer binding created there shadows the module binding, even
             * PRESENT(null), and its removal uncovers the module binding.
             */
            evaluate(
                    "makeScoped: () => {\n"
                            + "  scope: context\n"
                            + "  [() => { slot }, scope]\n"
                            + "}\n"
                            + "scoped: makeScoped()\n"
                            + "shadowed: scoped[0]\n"
                            + "nearer: scoped[1]\n",
                    module);
            ProtosClosureValue shadowed = closure("shadowed", module);
            assertTrue(instructionsOf(shadowed, "ReadGlobal").isEmpty(), () -> names(shadowed).toString());
            assertEquals("1", integerValue(call(shadowed, module)));
            evaluate("nearer.slot: null\n", module);
            assertSame(ProtosNullValue.INSTANCE, call(shadowed, module), "a nearer PRESENT(null) shadows");
            assertEquals("1", integerValue(call(reader, module)), "the unobserved reader is unaffected");
            evaluate("nearer.removeSlot(\"slot\")\n", module);
            assertEquals("1", integerValue(call(shadowed, module)), "removal uncovers the module binding");

            // PRESENT(null) in the module is not ABSENT.
            module.context().assignLocalSlot("slot", ProtosNullValue.INSTANCE);
            assertSame(
                    ProtosNullValue.INSTANCE,
                    call(reader, module),
                    "a module PRESENT(null) binding is not ABSENT");

            module.context().removeLocalSlot("slot");
            ProtosSignalException absent =
                    assertThrows(ProtosSignalException.class, () -> call(reader, module));
            assertSame(
                    ProtosCoreErrors.prototype(module, ProtosCoreErrors.StandardError.SLOT_NOT_FOUND),
                    absent.error().parent().orElse(null),
                    "an ABSENT module binding is SlotNotFound");
            assertSlotNotFound(module, () -> call(shadowed, module));
        });
        System.out.println("PERF037C_PRESENT_NULL_VERSUS_ABSENT=PASS");
    }

    /*
     * Two activations of one owner root share the reader site, the owner
     * Context materializes after warm-up, and each owner binding then takes
     * PRESENT(null) and other values; every read still selects the current
     * invocation's own binding exactly.
     */
    @Test
    void ownerBindingsStayExactPerActivationAfterWarmUp() throws Exception {
        withCore(module -> {
            evaluate(
                    "makeSlot: (initial) => {\n"
                            + "  slot: initial\n"
                            + "  () => { slot }\n"
                            + "}\n"
                            + "readerA: makeSlot(1)\n"
                            + "readerB: makeSlot(2)\n",
                    module);
            ProtosClosureValue readerA = closure("readerA", module);
            ProtosClosureValue readerB = closure("readerB", module);
            assertSame(
                    bytecodeNode(readerA),
                    bytecodeNode(readerB),
                    "both readers share one read site");
            for (int call = 0; call < WARM_UP_CALLS; call++) {
                assertEquals("1", integerValue(call(readerA, module)));
                assertEquals("2", integerValue(call(readerB, module)));
            }

            // Materializing the owner Context after warm-up changes nothing.
            readerA.capturedLexicalEnvironmentForRuntime().context();
            assertEquals("1", integerValue(call(readerA, module)));
            assertEquals("2", integerValue(call(readerB, module)));

            ProtosLexicalCell cellA = onlyCell(readerA);
            assertNotSame(cellA, onlyCell(readerB));
            cellA.setForRuntime(ProtosNullValue.INSTANCE);
            assertSame(ProtosNullValue.INSTANCE, call(readerA, module));
            for (int call = 0; call < WARM_UP_CALLS; call++) {
                assertSame(ProtosNullValue.INSTANCE, call(readerA, module));
                assertEquals("2", integerValue(call(readerB, module)),
                        "the other activation's owner is unaffected");
            }

            for (int round = 0; round < 3; round++) {
                cellA.setForRuntime(evaluate(Integer.toString(round + 5), module));
                assertEquals(Integer.toString(round + 5), integerValue(call(readerA, module)));
                cellA.setForRuntime(ProtosNullValue.INSTANCE);
                assertSame(ProtosNullValue.INSTANCE, call(readerA, module));
                assertEquals("2", integerValue(call(readerB, module)));
            }
        });
        System.out.println("PERF037D_OWNER_BINDINGS_EXACT=PASS");
    }

    /*
     * Alternating deferred and materialized owner scopes at one read site:
     * the captured read never depends on whether the owner's Context exists.
     */
    @Test
    void deferredAndMaterializedOwnersReadIdentically() throws Exception {
        withCore(module -> {
            ProtosClosureValue makeSlot =
                    closure("() => {\n  slot: 1\n  () => { slot }\n}", module);
            ProtosClosureValue deferredReader =
                    assertInstanceOf(ProtosClosureValue.class, call(makeSlot, module));
            ProtosClosureValue materializedReader =
                    assertInstanceOf(ProtosClosureValue.class, call(makeSlot, module));
            ProtosLexicalEnvironment deferred =
                    deferredReader.capturedLexicalEnvironmentForRuntime();
            ProtosLexicalEnvironment materialized =
                    materializedReader.capturedLexicalEnvironmentForRuntime();
            materialized.context();
            assertTrue(deferred.isDeferredForRuntime());
            assertFalse(materialized.isDeferredForRuntime());
            assertSame(bytecodeNode(deferredReader), bytecodeNode(materializedReader));

            for (int round = 0; round < WARM_UP_CALLS; round++) {
                assertEquals("1", integerValue(call(deferredReader, module)));
                assertEquals("1", integerValue(call(materializedReader, module)));
            }
            assertTrue(deferred.isDeferredForRuntime(),
                    "reading a captured cell never materializes its owner Context");
        });
        System.out.println("PERF037D_REPRESENTATION_INDEPENDENT=PASS");
    }

    /*
     * A two-level capture shares the owner's cell with every level, and the
     * cells of two owner activations stay distinct.
     */
    @Test
    void nestedCapturersShareTheOwnerCellAcrossLevels() throws Exception {
        withCore(module -> {
            evaluate(
                    "makeSlot: (initial) => {\n"
                            + "  slot: initial\n"
                            + "  () => { () => { slot } }\n"
                            + "}\n"
                            + "factoryA: makeSlot(1)\n"
                            + "factoryB: makeSlot(2)\n"
                            + "readerA: factoryA()\n"
                            + "readerB: factoryB()\n",
                    module);
            ProtosClosureValue factoryA = closure("factoryA", module);
            ProtosClosureValue readerA = closure("readerA", module);
            ProtosClosureValue readerB = closure("readerB", module);
            for (int call = 0; call < WARM_UP_CALLS; call++) {
                assertEquals("1", integerValue(call(readerA, module)));
                assertEquals("2", integerValue(call(readerB, module)));
            }
            ProtosLexicalCell cellA = onlyCell(readerA);
            assertSame(cellA, onlyCell(factoryA));
            assertNotSame(cellA, onlyCell(readerB));

            cellA.setForRuntime(ProtosNullValue.INSTANCE);
            assertSame(ProtosNullValue.INSTANCE, call(readerA, module));
            assertSame(ProtosNullValue.INSTANCE,
                    call(assertInstanceOf(ProtosClosureValue.class, call(factoryA, module)), module),
                    "a new reader of the same owner activation reads the same cell");
            assertEquals("2", integerValue(call(readerB, module)));
        });
        System.out.println("PERF037D_SHARED_OWNER_CELL=PASS");
    }

    /*
     * A nested captured read selects its proven owner, never a farther
     * module binding of the same name.
     */
    @Test
    void nestedCaptureReadsItsOwnerNotTheFartherModuleBinding() throws Exception {
        withCore(module -> {
            evaluate(
                    "slot: 0\n"
                            + "makeSlot: () => {\n"
                            + "  slot: 1\n"
                            + "  () => { () => { slot } }\n"
                            + "}\n"
                            + "factory: makeSlot()\n"
                            + "reader: factory()\n",
                    module);
            ProtosClosureValue reader = closure("reader", module);
            for (int call = 0; call < WARM_UP_CALLS; call++) {
                assertEquals("1", integerValue(call(reader, module)));
            }
            assertTrue(instructionsOf(reader, "ReadGlobal").isEmpty(),
                    () -> "the owner is the Closure binding: " + names(reader));
            onlyCell(reader).setForRuntime(ProtosNullValue.INSTANCE);
            for (int call = 0; call < WARM_UP_CALLS; call++) {
                assertSame(ProtosNullValue.INSTANCE, call(reader, module));
            }
            assertEquals("0", integerValue(evaluate("slot", module)));
        });
        System.out.println("PERF037D_OWNER_NOT_FARTHER_BINDING=PASS");
    }

    @FunctionalInterface
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
                Source.newBuilder(ProtosLanguage.ID, characters, "perf037c-captured-read.protos")
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return target.call(activation);
    }

    private static ProtosClosureValue closure(String characters, ProtosActivation activation) {
        return assertInstanceOf(ProtosClosureValue.class, evaluate(characters, activation));
    }

    /** One compact direct source call of {@code closure}, as a guest call site makes it. */
    private static Object call(
            ProtosClosureValue closure,
            ProtosActivation caller) {
        // I115: a direct function call through the shared dispatcher.
        return ProtosNumericValueSupport.guestValue(
                ProtosCallRequest.call(closure, java.util.List.of(), caller).callUncached());
    }

    private static ProtosActivation invocationOf(
            ProtosActivation module, ProtosClosureValue closure, Object argument) {
        return ProtosActivation.forClosureInvocation(
                closure,
                List.of(argument),
                module.prelude().orElseThrow(),
                module.actorModuleState(),
                module.currentModuleKey().orElse(null),
                module.executionDomain());
    }

    private static ProtosLexicalCell onlyCell(ProtosClosureValue closure) {
        ProtosLexicalCell[] cells = closure.capturedCellsForRuntime();
        assertEquals(1, cells.length, () -> "expected one captured cell");
        return cells[0];
    }

    private static BytecodeNode bytecodeNode(ProtosClosureValue closure) {
        return closure.executionPlan()
                .orElseThrow()
                .bytecodeActivationRootForTesting()
                .getBytecodeNode();
    }

    private static List<Instruction> instructionsOf(ProtosClosureValue closure, String operation) {
        return bytecodeNode(closure).getInstructionsAsList().stream()
                .filter(instruction -> instruction.getName().contains(operation))
                .toList();
    }

    private static List<String> names(ProtosClosureValue closure) {
        return bytecodeNode(closure).getInstructionsAsList().stream()
                .map(Instruction::getName)
                .toList();
    }

    private static Object constantArgument(Instruction instruction, String name) {
        Instruction.Argument argument =
                instruction.getArguments().stream()
                        .filter(candidate -> candidate.getName().equals(name))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no constant " + name + " operand"));
        return switch (argument.getKind()) {
            case INTEGER -> argument.asInteger();
            default -> argument.asConstant();
        };
    }

    private static String integerValue(Object value) {
        return com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value);
    }

    private static void assertSlotNotFound(ProtosActivation module, Executable read) {
        ProtosSignalException absent = assertThrows(ProtosSignalException.class, read);
        assertSame(
                ProtosCoreErrors.prototype(module, ProtosCoreErrors.StandardError.SLOT_NOT_FOUND),
                absent.error().parent().orElse(null));
    }
}
