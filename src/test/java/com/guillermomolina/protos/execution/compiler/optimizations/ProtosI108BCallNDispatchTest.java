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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.Debugger;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.InvokeClosureArguments;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosI108BCallNDispatchTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final int PAST_CACHE_LIMIT = 12;

    @Test
    void callNAndSpreadInstallDefinitionTierAndRecoverAfterOverrides()
            throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (a, b) => { a } }

                    callN: (f) => { f(7, 9) }
                    callSpread: (f, values) => { f(...values) }

                    unchanged: (f) => { 7 }

                    install: (f) => {
                        f.call: (a, b) => { 100 + a + b }
                        7
                    }

                    restore: (f) => {
                        f.removeSlot("call")
                        7
                    }

                    callWithMutation: (f, action) => {
                        f(action(f), 9)
                    }
                    """, "i108-b-define.protos", module);

            enterCachedTier("callN", module);
            enterCachedTier("callSpread", module);
            enterCachedTier("callWithMutation", module);

            for (int i = 0; i < PAST_CACHE_LIMIT; i++) {
                assertInteger(7, evaluate(
                        "callN(make())",
                        "i108-b-calln-warm.protos", module));
                assertInteger(7, evaluate(
                        "callSpread(make(), Array(7, 9))",
                        "i108-b-spread-warm.protos", module));
                assertInteger(7, evaluate(
                        "callWithMutation(make(), unchanged)",
                        "i108-b-mutation-warm.protos", module));
            }

            Object callNEntry = directEntry("callN", module);
            Object spreadEntry = directEntry("callSpread", module);
            Object mutationEntry = directEntry("callWithMutation", module);

            assertNotNull(callNEntry);
            assertNotNull(spreadEntry);
            assertNotNull(mutationEntry);

            ProtosClosureValue sample = assertInstanceOf(
                    ProtosClosureValue.class,
                    evaluate("make()", "i108-b-sample.protos", module));

            RootCallTarget target = InvokeClosureArguments.targetFor(
                    sample, module, ProtosLanguageContext.current());

            assertNotNull(target);
            assertTrue(InvokeClosureArguments.nonSuspending(target));
            assertSame(target, field(callNEntry, "cachedTarget_"));
            assertSame(target, field(spreadEntry, "cachedTarget_"));
            assertSame(target, field(mutationEntry, "cachedTarget_"));

            evaluate("changed: make()", "i108-b-changed.protos", module);

            // The receiver is staged before action(f), but Object.call
            // must be selected after that argument mutates the receiver.
            assertInteger(116, evaluate(
                    "callWithMutation(changed, install)",
                    "i108-b-install.protos", module));

            // Removal during argument evaluation must restore
            // canonical direct Closure invocation at this same site.
            assertInteger(7, evaluate(
                    "callWithMutation(changed, restore)",
                    "i108-b-restore.protos", module));

            // Reinstall an override on a separate instance of the
            // cached definition, then recover at the spread site.
            evaluate("""
                    changed.call: (a, b) => { 200 + a + b }
                    """, "i108-b-spread-override.protos", module);

            assertInteger(216, evaluate(
                    "callSpread(changed, Array(7, 9))",
                    "i108-b-spread-overridden.protos", module));

            evaluate(
                    "changed.removeSlot(\"call\")",
                    "i108-b-spread-remove.protos", module);

            assertInteger(7, evaluate(
                    "callSpread(changed, Array(7, 9))",
                    "i108-b-spread-recovered.protos", module));

            // Rejections must not discard the positive PIC entry.
            assertSame(callNEntry, directEntry("callN", module));
            assertSame(spreadEntry, directEntry("callSpread", module));
            assertSame(mutationEntry,
                    directEntry("callWithMutation", module));
        });
    }

    @Test
    void callNAndSpreadPreserveDefaultAndRestBinding()
            throws Exception {
        withCore(module -> {
            evaluate("""
                    makeDefault: () => {
                        (a, b, c: 31) => { c }
                    }

                    makeRest: () => {
                        (first, ...tail) => { tail.size() }
                    }

                    callTwo: (f) => { f(1, 2) }
                    callThree: (f) => { f(1, 2, 3) }
                    callSpread: (f, values) => { f(...values) }
                    """, "i108-b-arity-define.protos", module);

            assertInteger(31, evaluate(
                    "callTwo(makeDefault())",
                    "i108-b-default.protos", module));

            assertInteger(2, evaluate(
                    "callThree(makeRest())",
                    "i108-b-rest.protos", module));

            assertInteger(31, evaluate(
                    "callSpread(makeDefault(), Array(1, 2))",
                    "i108-b-default-spread.protos", module));

            assertInteger(2, evaluate(
                    "callSpread(makeRest(), Array(1, 2, 3))",
                    "i108-b-rest-spread.protos", module));
        });
    }


    @Test
    void genericFirstThenDirectAndBack() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (a, b) => { a } }
                    callN: (f) => { f(4, 5) }
                    callSpread: (f, values) => { f(...values) }

                    overridden: make()
                    overridden.call: (a, b) => { 100 + a + b }
                    """, "i108-b-generic-first.protos", module);

            enterCachedTier("callN", module);
            enterCachedTier("callSpread", module);

            // The first invocation is deliberately not admissible.
            assertInteger(109, evaluate(
                    "callN(overridden)",
                    "i108-b-negative-first-n.protos", module));
            assertInteger(109, evaluate(
                    "callSpread(overridden, Array(4, 5))",
                    "i108-b-negative-first-spread.protos", module));

            // A negative first observation must not poison the site.
            for (int i = 0; i < PAST_CACHE_LIMIT; i++) {
                assertInteger(4, evaluate(
                        "callN(make())",
                        "i108-b-positive-n.protos", module));
                assertInteger(4, evaluate(
                        "callSpread(make(), Array(4, 5))",
                        "i108-b-positive-spread.protos", module));
            }

            Object callNEntry = directEntry("callN", module);
            Object spreadEntry = directEntry("callSpread", module);

            assertNotNull(callNEntry);
            assertNotNull(spreadEntry);

            // Generic -> direct on the same previously overridden value.
            evaluate(
                    "overridden.removeSlot(\"call\")",
                    "i108-b-remove-call.protos", module);

            assertInteger(4, evaluate(
                    "callN(overridden)",
                    "i108-b-restored-n.protos", module));
            assertInteger(4, evaluate(
                    "callSpread(overridden, Array(4, 5))",
                    "i108-b-restored-spread.protos", module));

            // Direct -> generic when a nearer Object.call is reinstalled.
            evaluate("""
                    overridden.call: (a, b) => { 200 + a + b }
                    """, "i108-b-reinstall-call.protos", module);

            assertInteger(209, evaluate(
                    "callN(overridden)",
                    "i108-b-rejected-n.protos", module));
            assertInteger(209, evaluate(
                    "callSpread(overridden, Array(4, 5))",
                    "i108-b-rejected-spread.protos", module));

            // Generic -> direct again, without losing the positive PIC.
            evaluate(
                    "overridden.removeSlot(\"call\")",
                    "i108-b-remove-again.protos", module);

            assertInteger(4, evaluate(
                    "callN(overridden)",
                    "i108-b-recovered-n.protos", module));
            assertInteger(4, evaluate(
                    "callSpread(overridden, Array(4, 5))",
                    "i108-b-recovered-spread.protos", module));

            assertSame(callNEntry, directEntry("callN", module));
            assertSame(spreadEntry, directEntry("callSpread", module));
        });
    }

    @Test
    void uncachedCallNAndSpread() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (a, b) => { a } }
                    callN: (f) => { f(4, 5) }
                    callSpread: (f, values) => { f(...values) }

                    overridden: make()
                    overridden.call: (a, b) => { 100 + a + b }
                    """, "i108-b-uncached-define.protos", module);

            // Keep both roots on their uncached interpreter paths.
            functionRoot("callN", module)
                    .getBytecodeNode()
                    .setUncachedThreshold(Integer.MAX_VALUE);
            functionRoot("callSpread", module)
                    .getBytecodeNode()
                    .setUncachedThreshold(Integer.MAX_VALUE);

            assertInteger(4, evaluate(
                    "callN(make())",
                    "i108-b-uncached-direct-n.protos", module));
            assertInteger(4, evaluate(
                    "callSpread(make(), Array(4, 5))",
                    "i108-b-uncached-direct-spread.protos", module));

            assertInteger(109, evaluate(
                    "callN(overridden)",
                    "i108-b-uncached-override-n.protos", module));
            assertInteger(109, evaluate(
                    "callSpread(overridden, Array(4, 5))",
                    "i108-b-uncached-override-spread.protos", module));

            evaluate(
                    "overridden.removeSlot(\"call\")",
                    "i108-b-uncached-remove.protos", module);

            assertInteger(4, evaluate(
                    "callN(overridden)",
                    "i108-b-uncached-recovered-n.protos", module));
            assertInteger(4, evaluate(
                    "callSpread(overridden, Array(4, 5))",
                    "i108-b-uncached-recovered-spread.protos", module));
        });
    }


    @Test
    void callNSpreadParityWithAndWithoutDebugger() throws Exception {
        for (boolean debugger : new boolean[] {false, true}) {
            try (Context context = Context.newBuilder(ProtosLanguage.ID).build();
                    DebuggerSession attached = attach(context, debugger)) {
                context.initialize(ProtosLanguage.ID);
                context.enter();
                try {
                    ProtosActivation module =
                            new ProtosCoreBootstrap()
                                    .bootstrap(CORE)
                                    .newModuleActivation();

                    evaluate("""
                            make: () => { (a, b) => { a } }
                            callN: (f) => { f(7, 9) }
                            callSpread: (f, values) => { f(...values) }

                            overridden: make()
                            overridden.call: (a, b) => {
                                100 + a + b
                            }
                            """, "i108-b-mode-define.protos", module);

                    assertInteger(7, evaluate(
                            "callN(make())",
                            "i108-b-mode-direct-n.protos", module));
                    assertInteger(7, evaluate(
                            "callSpread(make(), Array(7, 9))",
                            "i108-b-mode-direct-spread.protos", module));

                    assertInteger(116, evaluate(
                            "callN(overridden)",
                            "i108-b-mode-override-n.protos", module));
                    assertInteger(116, evaluate(
                            "callSpread(overridden, Array(7, 9))",
                            "i108-b-mode-override-spread.protos", module));

                    evaluate(
                            "overridden.removeSlot(\"call\")",
                            "i108-b-mode-remove.protos", module);

                    assertInteger(7, evaluate(
                            "callN(overridden)",
                            "i108-b-mode-restore-n.protos", module));
                    assertInteger(7, evaluate(
                            "callSpread(overridden, Array(7, 9))",
                            "i108-b-mode-restore-spread.protos", module));
                } finally {
                    context.leave();
                }
            }
        }
    }

    @Test
    void crossContextCallNSpreadProjection() throws Exception {
        try (Context origin =
                        Context.newBuilder(ProtosLanguage.ID).build();
                Context destination =
                        Context.newBuilder(ProtosLanguage.ID).build()) {
            origin.initialize(ProtosLanguage.ID);
            destination.initialize(ProtosLanguage.ID);

            ProtosClosureValue foreign;
            RootCallTarget originTarget;

            origin.enter();
            try {
                ProtosActivation module =
                        new ProtosCoreBootstrap()
                                .bootstrap(CORE)
                                .newModuleActivation();

                evaluate("""
                        make: () => { (a, b) => { a } }
                        """, "i108-b-origin-define.protos", module);

                foreign = assertInstanceOf(
                        ProtosClosureValue.class,
                        evaluate(
                                "make()",
                                "i108-b-origin-closure.protos",
                                module));

                originTarget = InvokeClosureArguments.targetFor(
                        foreign,
                        module,
                        ProtosLanguageContext.current());

                assertNotNull(originTarget);
            } finally {
                origin.leave();
            }

            destination.enter();
            try {
                ProtosActivation module =
                        new ProtosCoreBootstrap()
                                .bootstrap(CORE)
                                .newModuleActivation();

                ProtosLanguageContext entered =
                        ProtosLanguageContext.current();

                RootCallTarget projected =
                        InvokeClosureArguments.targetFor(
                                foreign, module, entered);

                assertNotNull(projected);
                org.junit.jupiter.api.Assertions.assertNotSame(
                        originTarget, projected,
                        "foreign CallTarget must not cross Context ownership");

                assertSame(
                        entered.languageForTesting(),
                        projected.getRootNode()
                                .getLanguage(ProtosLanguage.class));

                assertSame(
                        projected,
                        InvokeClosureArguments.targetFor(
                                foreign, module, entered));

                module.context().createLocalSlot("foreign", foreign);

                evaluate("""
                        callN: (f) => { f(7, 9) }
                        callSpread: (f, values) => { f(...values) }
                        """, "i108-b-destination-define.protos", module);

                enterCachedTier("callN", module);
                enterCachedTier("callSpread", module);

                assertInteger(7, evaluate(
                        "callN(foreign)",
                        "i108-b-foreign-calln.protos", module));

                assertInteger(7, evaluate(
                        "callSpread(foreign, Array(7, 9))",
                        "i108-b-foreign-spread.protos", module));
            } finally {
                destination.leave();
            }
        }
    }


    /**
     * I108-A+B: the emitted source-root Bytecode must own ordinary
     * Closure dispatch in one semantic invocation operation.
     *
     * Unknown calls retain the structural continuation protocol.
     * This inspects physical operations, not execution timings.
     */
    @Test
    void structuralInvocationBoundariesWithAndWithoutDebugger() throws Exception {
        record Site(String function, String operation) {}

        java.util.List<Site> sites = java.util.List.of(
                new Site("callZero", "InvokeClosureZero"),
                new Site("callOne", "InvokeClosureOne"),
                new Site("callN", "InvokeClosureArguments"),
                new Site("callSpread", "InvokeClosureArguments"));

        for (boolean debugger : new boolean[] {false, true}) {
            String mode = debugger ? "debugger attached" : "no debugger";
            try (Context context = Context.newBuilder(ProtosLanguage.ID).build();
                    DebuggerSession attached = attach(context, debugger)) {
                context.initialize(ProtosLanguage.ID);
                context.enter();
                try {
                    ProtosActivation module =
                            new ProtosCoreBootstrap()
                                    .bootstrap(CORE)
                                    .newModuleActivation();

                    evaluate("""
                            callZero: (f) => { f() }
                            callOne: (f) => { f(7) }
                            callN: (f) => { f(7, 9) }
                            callSpread: (f, values) => {
                                f(...values)
                            }
                            """, "i108-structural.protos", module);

                    for (Site site : sites) {
                        ProtosBytecodeRootNode root =
                                functionRoot(site.function(), module);

                        RootCallTarget originalTarget =
                                root.getCallTarget();

                        java.util.List<String> before =
                                instructionNames(root);

                        // I112: a final positional call is terminal and spread keeps the
                        // composed protocol; PLAT067-B: an attached debugger changes neither.
                        boolean terminal = !site.function().equals("callSpread");
                        String operation =
                                terminal ? site.operation() + "Tail" : site.operation();

                        assertInvocationTopology(
                                before,
                                operation,
                                terminal,
                                mode + " " + site.function()
                                        + " before ensureComplete");

                        // Exercise the complete root-group reparse.
                        root.getRootNodes().ensureComplete();

                        assertSame(originalTarget, root.getCallTarget());

                        java.util.List<String> complete =
                                instructionNames(root);

                        assertInvocationTopology(
                                complete,
                                operation,
                                terminal,
                                mode + " " + site.function()
                                        + " after ensureComplete");

                        root.getRootNodes().ensureComplete();

                        assertEquals(
                                complete,
                                instructionNames(root),
                                mode + " " + site.function()
                                        + " complete reparse stability");
                    }
                } finally {
                    context.leave();
                }
            }
        }
    }

    /** PLAT067-B: a Truffle debugger session on the Context's Engine, or none. */
    private static DebuggerSession attach(Context context, boolean debugger) {
        return debugger ? Debugger.find(context.getEngine()).startSession(event -> {}) : null;
    }

    private static java.util.List<String> instructionNames(
            ProtosBytecodeRootNode root) {
        return root.getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .map(ProtosInstructionNames::of)
                .toList();
    }

    private static boolean containsOperation(
            java.util.List<String> instructions,
            String operation) {
        return instructions.stream().anyMatch(
                name -> name.equals(operation)
                        || name.equals("c." + operation));
    }

    private static void assertInvocationTopology(
            java.util.List<String> instructions,
            String expectedInvocation,
            boolean terminal,
            String description) {
        assertTrue(
                containsOperation(instructions, expectedInvocation),
                description + ": missing semantic invocation "
                        + expectedInvocation + " in " + instructions);

        assertEquals(
                !terminal,
                containsOperation(
                        instructions, "IsPreparedClosureInvocation"),
                description + ": prepared-result discrimination only at a composed site");

        // No second mechanical preparation/dispatch pipeline at
        // the caller site. The generic preparation belongs to the
        // semantic operation itself.
        for (String obsolete : java.util.List.of(
                "TryDirectCallZero",
                "TryDirectCallOne",
                "PrepareClosureCall",
                "PrepareClosureCallOne",
                "PrepareClosureCallArguments",
                "PrepareClosureCallVector")) {
            org.junit.jupiter.api.Assertions.assertFalse(
                    containsOperation(instructions, obsolete),
                    description + ": obsolete caller operation "
                            + obsolete);
        }

        // Unknown calls still require the real Bytecode DSL
        // suspension/resumption machinery. It must not be moved
        // into an ordinary Java-only operation.
        for (String continuation : java.util.List.of(
                "IsContinuationForCall",
                "yield",
                "ResumeContinuation")) {
            // I112: a terminal site transports a suspension instead.
            assertEquals(
                    !terminal,
                    containsOperation(instructions, continuation),
                    description + ": structural continuation " + continuation);
        }
    }


    /**
     * I108-B: a site whose direct definition cache has limit three
     * must preserve ordinary D013 semantics with five different
     * source definitions. No assertion assumes that a megamorphic
     * site must retain a particular cache representation.
     */
    @Test
    void picSaturationPreservesCallNAndSpreadSemantics()
            throws Exception {
        withCore(module -> {
            evaluate("""
                    makeA: () => { (a, b) => { a } }
                    makeB: () => { (a, b) => { b } }
                    makeC: () => { (a, b) => { 13 } }
                    makeD: () => { (a, b) => { 17 } }
                    makeE: () => { (a, b) => { 19 } }

                    callN: (f) => { f(7, 9) }
                    callSpread: (f, values) => { f(...values) }
                    """, "i108-b-polymorphic-define.protos", module);

            enterCachedTier("callN", module);
            enterCachedTier("callSpread", module);

            String[] factories = {
                    "makeA", "makeB", "makeC", "makeD", "makeE"
            };
            long[] expected = {7, 9, 13, 17, 19};

            for (int index = 0; index < factories.length; index++) {
                ProtosClosureValue closure = assertInstanceOf(
                        ProtosClosureValue.class,
                        evaluate(
                                factories[index] + "()",
                                "i108-b-polymorphic-factory-"
                                        + index + ".protos",
                                module));

                module.context().createLocalSlot(
                        "candidate" + index, closure);
            }

            // Prove that the first definition genuinely established
            // a direct entry before introducing competing definitions.
            assertInteger(7, evaluate(
                    "callN(candidate0)",
                    "i108-b-polymorphic-first-n.protos",
                    module));
            assertInteger(7, evaluate(
                    "callSpread(candidate0, Array(7, 9))",
                    "i108-b-polymorphic-first-spread.protos",
                    module));

            assertNotNull(directEntry("callN", module));
            assertNotNull(directEntry("callSpread", module));

            // Five independently parsed Closure definitions at each
            // of two stable call sites, exceeding the PIC limit of 3.
            for (int repetition = 0; repetition < 4; repetition++) {
                for (int index = 0; index < factories.length; index++) {
                    String candidate = "candidate" + index;

                    assertInteger(expected[index], evaluate(
                            "callN(" + candidate + ")",
                            "i108-b-polymorphic-n-"
                                    + repetition + "-" + index + ".protos",
                            module));

                    assertInteger(expected[index], evaluate(
                            "callSpread(" + candidate
                                    + ", Array(7, 9))",
                            "i108-b-polymorphic-spread-"
                                    + repetition + "-" + index + ".protos",
                            module));
                }
            }

            // Modify an instance after the site has crossed the
            // direct-cache limit. The current Object.call must win.
            evaluate("""
                    candidate3.call: (a, b) => { 100 + a + b }
                    """, "i108-b-polymorphic-override.protos", module);

            assertInteger(116, evaluate(
                    "callN(candidate3)",
                    "i108-b-polymorphic-override-n.protos",
                    module));

            assertInteger(116, evaluate(
                    "callSpread(candidate3, Array(7, 9))",
                    "i108-b-polymorphic-override-spread.protos",
                    module));

            evaluate(
                    "candidate3.removeSlot(\"call\")",
                    "i108-b-polymorphic-remove.protos",
                    module);

            assertInteger(17, evaluate(
                    "callN(candidate3)",
                    "i108-b-polymorphic-restored-n.protos",
                    module));

            assertInteger(17, evaluate(
                    "callSpread(candidate3, Array(7, 9))",
                    "i108-b-polymorphic-restored-spread.protos",
                    module));

            // The remaining definitions must also retain their
            // exact identities and results after override recovery.
            for (int index = 0; index < factories.length; index++) {
                assertInteger(expected[index], evaluate(
                        "callN(candidate" + index + ")",
                        "i108-b-polymorphic-final-"
                                + index + ".protos",
                        module));
            }
        });
    }

    private static void enterCachedTier(
            String function, ProtosActivation module) {
        functionRoot(function, module)
                .getBytecodeNode().setUncachedThreshold(0);
    }

    private static ProtosBytecodeRootNode functionRoot(
            String function, ProtosActivation module) {
        ProtosClosureValue closure = assertInstanceOf(
                ProtosClosureValue.class,
                module.context().readLocalSlot(function).orElseThrow());
        return closure.executionPlan().orElseThrow()
                .bytecodeActivationRootForTesting();
    }

    private static Object directEntry(
            String function, ProtosActivation module) throws Exception {
        Object bytecode = functionRoot(function, module).getBytecodeNode();
        Node[] cachedNodes = (Node[]) field(bytecode, "cachedNodes_");

        int sites = 0;
        Object entry = null;

        for (Node node : cachedNodes) {
            // I112: a final CallN is the terminal form of the same site.
            if (node == null
                    || !(node.getClass().getSimpleName()
                                    .equals("InvokeClosureArguments_Node")
                            || node.getClass().getSimpleName()
                                    .equals("InvokeClosureArgumentsTail_Node"))) {
                continue;
            }

            sites++;
            entry = field(node, "direct_cache");
        }

        assertEquals(1, sites, "expected one CallN PIC site in " + function);
        return entry;
    }

    private static Object field(Object owner, String name)
            throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass();
                type != null;
                type = type.getSuperclass()) {
            try {
                Field result = type.getDeclaredField(name);
                result.setAccessible(true);
                return result.get(owner);
            } catch (NoSuchFieldException missing) {
                // Try the superclass.
            }
        }

        throw new NoSuchFieldException(
                owner.getClass().getName() + "." + name);
    }

    private interface CoreCase {
        void run(ProtosActivation module) throws Exception;
    }

    private static void withCore(CoreCase test) throws Exception {
        try (Context context =
                Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();

            try {
                ProtosPrelude prelude =
                        new ProtosCoreBootstrap().bootstrap(CORE);
                test.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    private static Object evaluate(
            String code, String name, ProtosActivation module) {
        Source source = Source.newBuilder(ProtosLanguage.ID, code, name)
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
        CallTarget target =
                ProtosLanguageContext.current().parsePublic(source);
        return target.call(module);
    }

    private static void assertInteger(long expected, Object result) {
        assertEquals(
                Long.toString(expected),
                ProtosNumericValueSupport.integerDecimalText(result));
    }
}
