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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.Debugger;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

/**
 * B5[3] is exactly null, a transported Prelude, or the exact caller Activation.
 * Cold recovery can observe caller frames only while guest calls are active;
 * no invocation retains another invocation's argument array. Caller needs are
 * registered per root and their Assumptions converge after first observation.
 * Guest regressions retain captures, control, suspension and Context projection.
 */
final class ProtosI115BDeferredCallerTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final int PAST_CACHE_LIMITS = 12;
    private static final Object SUPPLIED = ProtosNullValue.INSTANCE;

    // ---- Legal B5 payloads and publication ----------------------------------------

    @Test
    void b5LayoutAcceptsExactlyTheThreeCallerPayloadForms() throws Exception {
        withCore(module -> {
            ProtosClosureValue callee = closure("(value) => { value }\n", module);
            Object[] payloads = {module, module.prelude().orElseThrow(), null};
            for (Object payload : payloads) {
                Object[] header = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                        callee, payload, SUPPLIED);
                assertEquals(6, header.length);
                assertSame(callee, header[0]);
                assertSame(ProtosFrameArguments.compactDirectClosureCallOne(
                        callee, module, null, SUPPLIED)[1], header[1]);
                assertSame(null, header[2]);
                assertSame(payload, header[3]);
                assertSame(ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime(), header[4]);
                assertSame(SUPPLIED, header[5]);
                assertTrue(ProtosFrameArguments.acceptsPrimitiveResult(header));
                assertUnpublished(header);
            }
            assertSame(module, ProtosFrameArguments.resolveExactCaller(module));
            Object[] valid = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    callee, (Object) module, SUPPLIED);
            for (Object invalid : new Object[] {"invalid", new Object(), new Object[0], valid}) {
                assertThrows(IllegalArgumentException.class,
                        () -> ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                                callee, invalid, SUPPLIED));
            }
            for (Object unavailable : new Object[] {null, module.prelude().orElseThrow(), new Object[0]}) {
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.resolveExactCaller(unavailable));
            }
            assertUnpublished(valid);
        });
    }

    @Test
    void publishedExactCallerRetainsHeaderAndOneIdentity() throws Exception {
        withCore(module -> {
            ProtosClosureValue callee = closure("(value) => { value }\n", module);
            Object[] header = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    callee, (Object) module, SUPPLIED);
            Object[] before = header.clone();
            ProtosActivation child = ProtosFrameArguments.activation(header);
            assertNotSame(module, child);
            assertSame(child, header[0]);
            assertSame(child, ProtosFrameArguments.activation(header));
            assertSame(child, ProtosFrameArguments.publishedActivation(header));
            assertSame(module, ProtosFrameArguments.compactCaller(header));
            for (int i = 1; i < header.length; i++) {
                assertSame(before[i], header[i]);
            }
            assertSameProvenance(module, child);
            assertSame(ProtosFrameArguments.compactReturnHome(header), child.returnHome().orElseThrow());
            assertNotNull(ProtosCoreErrors.newError(child));
            assertSame(child, ProtosFrameArguments.activation(header));
        });
    }

    @Test
    void preludeOnlyHeaderIsReadWithoutPublishingExactCaller() throws Exception {
        withCore(module -> {
            ProtosClosureValue callee = preludeFree(closure("(value) => { value }\n", module));
            ProtosPrelude prelude = module.prelude().orElseThrow();
            Object[] header = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    callee, (Object) prelude, SUPPLIED);
            assertSame(prelude, header[3]);
            assertSame(prelude, ProtosFrameArguments.preludeOrNull(header));
            assertSame(prelude, ProtosFrameArguments.admittedPreludeOrNull(header));
            assertSame(null, ProtosFrameArguments.compactTask(header));
            assertSame(null, ProtosFrameArguments.admittedCompactTask(header));
            assertFalse(ProtosFrameArguments.hasPublishedActivation(header));
        });
    }

    @Test
    void closureOwnPreludeTakesPrecedenceOverTransportedPrelude() throws Exception {
        withCore(module -> {
            ProtosClosureValue template = closure("(value) => { value }\n", module);
            ProtosPrelude own = module.prelude().orElseThrow();
            ProtosPrelude other = otherPreludeModule().prelude().orElseThrow();
            assertNotSame(own, other);
            Object[] owning = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    ownPrelude(template, own), (Object) other, SUPPLIED);
            Object[] inherited = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    preludeFree(template), (Object) other, SUPPLIED);
            Object[] absent = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    preludeFree(template), (Object) null, SUPPLIED);
            assertSame(own, ProtosFrameArguments.preludeOrNull(owning));
            assertSame(own, ProtosFrameArguments.admittedPreludeOrNull(owning));
            assertSame(other, inherited[3]);
            assertSame(other, ProtosFrameArguments.preludeOrNull(inherited));
            assertSame(other, ProtosFrameArguments.admittedPreludeOrNull(inherited));
            assertSame(null, ProtosFrameArguments.preludeOrNull(absent));
            assertSame(null, ProtosFrameArguments.admittedPreludeOrNull(absent));
            for (Object[] header : List.of(owning, inherited, absent)) {
                assertUnpublished(header);
            }
        });
    }

    @Test
    void explicitAndInheritedTaskStayExact() throws Exception {
        withCore(module -> {
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosTask inherited = domain.createTask(null, current -> {});
            ProtosTask explicit = domain.createTask(null, current -> {});
            ProtosActivation owner = domainModule(module, domain);
            owner.attachTask(inherited);
            ProtosClosureValue callee = closure("(value) => { value }\n", module);
            for (ProtosTask supplied : new ProtosTask[] {null, explicit}) {
                Object[] header = ProtosFrameArguments.compactDirectClosureCallOne(
                        callee, owner, supplied, SUPPLIED);
                ProtosTask expected = supplied == null ? inherited : explicit;
                assertSame(owner, header[3]);
                assertSame(expected, ProtosFrameArguments.compactTask(header));
                assertSame(expected, ProtosFrameArguments.admittedCompactTask(header));
                assertUnpublished(header);
                assertSame(expected, ProtosFrameArguments.activation(header).task().orElseThrow());
                assertSame(owner, header[3]);
            }
        });
    }

    @Test
    void dynamicControlAndReturnHomeFollowExactActivation() throws Exception {
        withCore(module -> {
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation owner = domainModule(module, domain);
            ProtosDynamicControlState state = owner.dynamicControlState();
            ProtosClosureValue callee = closure("(value) => { value }\n", module);
            Object[] first = ProtosFrameArguments.compactDirectClosureCallOne(
                    callee, owner, null, SUPPLIED);
            Object firstHome = first[4];
            ProtosActivation child = ProtosFrameArguments.activation(first);
            assertSame(state, child.dynamicControlStateIfPresent().orElseThrow());
            assertSame(firstHome, child.returnHome().orElseThrow());
            ProtosActivation unrelated = domainModule(module, domain);
            Object[] second = ProtosFrameArguments.compactDirectClosureCallOne(
                    callee, unrelated, null, SUPPLIED);
            ProtosActivation other = ProtosFrameArguments.activation(second);
            assertNotSame(child, other);
            assertTrue(other.dynamicControlStateIfPresent().isEmpty());
            assertSame(owner, first[3]);
            assertSame(unrelated, second[3]);
            assertSame(child, first[0]);
            assertSame(other, second[0]);
            assertSame(firstHome, first[4]);
        });
    }

    @Test
    void realGuestCallRegistersExactCallerNeedOnlyOnce() throws Exception {
        withCore(module -> {
            evaluate("""
                    callee: (value) => { context.hasSlot("value"); value }
                    caller: (value) => { callee(value) }
                    """, "i115-exact-define.protos", module);
            ProtosBytecodeRootNode root = functionRoot("callee", module);
            enterCachedTier("caller", module);
            root.getBytecodeNode().setUncachedThreshold(0);
            int need = ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION;
            boolean alreadyNeeded = root.hasCallerNeed(need);
            var before = root.callerNeedsStability();
            assertInteger(9, evaluate("caller(9)\n", "i115-exact.protos", module));
            assertTrue(root.hasCallerNeed(need));
            var registered = root.callerNeedsStability();
            if (!alreadyNeeded) {
                assertFalse(before.isValid());
                assertNotSame(before, registered);
            }
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(9, evaluate("caller(9)\n", "i115-exact-repeat.protos", module));
            }
            assertSame(registered, root.callerNeedsStability());
            assertTrue(registered.isValid());
        });
    }

    @Test
    void provenAndUnknownEntriesUseLegalCallerPayloads() throws Exception {
        withCore(module -> {
            evaluate("""
                    helper: (value) => { value }
                    proven: () => { (value) => { value } }
                    unknown: () => { (value) => { helper(value) } }
                    """, "i115-entry-payloads.protos", module);
            for (String maker : List.of("proven()\n", "unknown()\n")) {
                ProtosClosureValue callee = closure(maker, module);
                RootCallTarget target = directTarget(callee);
                assertEquals(maker.startsWith("proven"), provenNonSuspending(target));
                Object[] header = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                        callee, (Object) module, 6L);
                assertTrue(ProtosFrameArguments.acceptsPrimitiveResult(header));
                assertInteger(6, ProtosNumericValueSupport.guestValue(target.call(header)));
                assertSame(module, header[3]);
                assertUnpublished(header);
                ProtosActivation observed = ProtosFrameArguments.activation(header);
                assertSame(observed, ProtosFrameArguments.publishedActivation(header));
                assertSame(module, header[3]);
            }
        });
    }

    @Test
    void malformedPayloadIsRejectedBeforePublication() throws Exception {
        withCore(module -> {
            ProtosClosureValue callee = closure("(value) => { value }\n", module);
            for (Object invalid : new Object[] {"invalid", new Object(), new Object[0]}) {
                assertThrows(IllegalArgumentException.class,
                        () -> ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                                callee, invalid, SUPPLIED));
            }
            for (Object missing : new Object[] {null, module.prelude().orElseThrow(), new Object[0]}) {
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.resolveExactCaller(missing));
            }
        });
    }

    @Test
    void unknownTerminalEntryThatSuspendsResumesWithItsOwnState() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                ProtosActivation module =
                        prelude.newModuleActivation(
                                new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
                ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
                ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("wait", wait);
                evaluate("""
                        waiter: (value) => {
                            result: wait.value()
                            keep: (other) => { result }
                            keep(value)
                        }
                        callOne: (f, value) => { f(value) }
                        """, "i115b-suspend-define.protos", module);
                enterCachedTier("callOne", module);
                CallTarget top = ProtosLanguageContext.current().parsePublic(
                        Source.newBuilder(ProtosLanguage.ID, "callOne(waiter, 0)\n", "i115b-suspend-top.protos")
                                .mimeType(ProtosLanguage.MIME_TYPE)
                                .build());

                ProtosTask task = domain.createTask(
                        null, current -> ProtosBytecodeTaskExecution.execute(current, top, module));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state(), "really suspended");

                List<RootCallTarget> entries = tierEntries("callOne", module);
                assertEquals(1, entries.size());
                assertFalse(provenNonSuspending(entries.get(0)), "UNKNOWN entry");

                assertTrue(wait.resolve(resolved, module));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertSame(resolved, task.result().orElseThrow(), "exact resumed result");
            } finally {
                context.leave();
            }
        }
    }

    // ---- E, H, J. Guest terminal sites through both cache tiers ----------------------

    @Test
    void freshClosuresShareTheDefinitionTargetButKeepTheirCaptures() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: (seed) => { (value) => { seed + value } }
                    callOne: (f, value) => { f(value) }
                    """, "i115b-h-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(11, evaluate("callOne(make(10), 1)\n", "i115b-h-ten.protos", module));
                assertInteger(21, evaluate("callOne(make(20), 1)\n", "i115b-h-twenty.protos", module));
            }
            // I115: one code-root entry serves every fresh Closure of the definition.
            List<RootCallTarget> entries = tierEntries("callOne", module);
            assertEquals(1, entries.size());
            RootCallTarget target = entries.get(0);
            ProtosClosureExecutionPlan template =
                    closure("make(10)\n", module).preparedExecutionPlanOrNullForRuntime();
            assertSame(template.bytecodeActivationTargetForComposition(), target);
            assertSame(target, directTarget(closure("make(20)\n", module)));
        });
    }

    @Test
    void identityTargetIsProvenAndEntersSynchronously() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    callOne: (f, value) => { f(value) }
                    """, "i115b-j-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("callOne(make(), 1)\n", "i115b-j-run.protos", module));
            }
            // The saturated identity PIC need not retain a direct-call
            // entry. Verify the effective code target independently.
            ProtosClosureValue first = closure("make()\n", module);
            ProtosClosureValue second = closure("make()\n", module);
            assertNotSame(first, second);
            RootCallTarget target = directTarget(first);
            assertSame(target, directTarget(second));
            assertTrue(provenNonSuspending(target));
            assertTrue(bytecodeTarget(target));
        });
    }

    @Test
    void entryClassificationFollowsTheTargetsEffectiveRootProof() throws Exception {
        withCore(module -> {
            evaluate("""
                    helper: (value) => { value }
                    make: () => { (value) => { helper(value) } }
                    callOne: (f, value) => { f(value) }
                    """, "i115b-unknown-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(5, evaluate("callOne(make(), 5)\n", "i115b-unknown-run.protos", module));
            }
            for (RootCallTarget target : tierEntries("callOne", module)) {
                ProtosBytecodeRootNode root =
                        assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
                assertEquals(
                        root.provablyNonSuspendingBody(),
                        provenNonSuspending(target));
            }
        });
    }

    @Test
    void escapingClosureCreatedByTheCalleeKeepsItsCaptures() throws Exception {
        withCore(module -> {
            evaluate("""
                    wrap: (value) => { (other) => { value + other } }
                    callOne: (f, value) => { f(value) }
                    apply: (h, other) => { h(other) }
                    """, "i115b-e-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(
                        3, evaluate("apply(callOne(wrap, 1), 2)\n", "i115b-e-run.protos", module));
                assertInteger(
                        30, evaluate("apply(callOne(wrap, 10), 20)\n", "i115b-e-escape.protos", module));
            }
        });
    }

    // ---- G. Direct source-function selection after operand evaluation --------------

    @Test
    void callCreatedWhileEvaluatingArgumentKeepsDirectFunctionTarget() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    late: make()
                    plain: () => { 7 }
                    arm: () => {
                        late.call: (value) => { 100 + value }
                        7
                    }
                    callWith: (f, g) => { f(g()) }
                    """, "i115b-g-define.protos", module);
            enterCachedTier("callWith", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callWith(make(), plain)\n", "i115b-g-warm.protos", module));
                assertInteger(7, evaluate("callWith(late, plain)\n", "i115b-g-late.protos", module));
            }
            for (int round = 0; round < 3; round++) {
                assertInteger(7, evaluate("callWith(late, arm)\n", "i115b-g-arm.protos", module));
                assertTrue(late(module).hasLocalCallSlotForRuntime());
                evaluate("late.removeSlot(\"call\")\n", "i115b-g-remove.protos", module);
                assertFalse(late(module).hasLocalCallSlotForRuntime());
                assertInteger(7, evaluate("callWith(late, plain)\n", "i115b-g-restored.protos", module));
                assertInteger(7, evaluate("callWith(make(), plain)\n", "i115b-g-fresh.protos", module));
            }
            /*
             * Argument evaluation installs a local call slot, but the
             * source-function dispatcher continues to invoke the source body.
             * The slot remains present and is removed explicitly above.
             */
        });
    }

    // ---- I, L. Two Contexts of one Engine; an attached debugger ----------------------

    /**
     * Each Context of one Engine owns its language instance, so a terminal site projects the
     * other Context's Closure in both directions. PLAT067-B: no compilation profile is involved.
     */
    @Test
    void terminalSiteProjectsAnotherContextsClosureInBothDirections() throws Exception {
        try (Engine engine = Engine.create();
                Harness first = new Harness(false, engine);
                Harness second = new Harness(false, engine)) {
            ProtosClosureValue fromFirst = first.call(Harness::identityClosure);
            ProtosClosureValue fromSecond = second.call(Harness::identityClosure);
            assertProjectedTerminalCall(second, fromFirst);
            assertProjectedTerminalCall(first, fromSecond);
        }
    }

    @Test
    void attachedDebuggerTerminalCallObservesOneCallerIdentity() throws Exception {
        try (Harness debug = new Harness(true, null)) {
            debug.call(module -> {
                evaluate("""
                        make: () => { (value) => { value } }
                        wrap: (value) => { (other) => { value + other } }
                        callOne: (f, value) => { f(value) }
                        apply: (h, other) => { h(other) }
                        """, "i115b-debug-define.protos", module);
                enterCachedTier("callOne", module);
                for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                    assertInteger(4, evaluate("callOne(make(), 4)\n", "i115b-debug-run.protos", module));
                    assertInteger(
                            5,
                            evaluate("apply(callOne(wrap, 2), 3)\n", "i115b-debug-escape.protos", module));
                }
                return null;
            });
        }
    }

    private static void assertProjectedTerminalCall(
            Harness destination, ProtosClosureValue foreign) throws Exception {
        ProtosClosureExecutionPlan template = foreign.preparedExecutionPlanOrNullForRuntime();
        assertNotNull(template);
        RootCallTarget foreignTarget = template.bytecodeActivationTargetForComposition();
        destination.call(module -> {
            evaluate("callOne: (f, value) => { f(value) }\n", "i115b-i-define.protos", module);
            module.context().createLocalSlot("foreign", foreign);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callOne(foreign, 7)\n", "i115b-i-run.protos", module));
            }
            ProtosLanguageContext context = ProtosLanguageContext.current();
            List<RootCallTarget> entries = tierEntries("callOne", module);
            assertEquals(1, entries.size());
            RootCallTarget target = entries.get(0);
            assertSame(template, foreign.preparedExecutionPlanOrNullForRuntime());
            assertNotSame(foreignTarget, target);
            ProtosClosureExecutionPlan effective =
                    context.bytecodeExecutionPlanForDefinition(foreign.definition(), template);
            assertSame(effective.bytecodeActivationTargetForComposition(), target);
            assertSame(context.languageForRuntime(), effective.language().orElseThrow());
            return null;
        });
    }

    @Test
    void provenTerminalCallPropagatesOnlyToAcceptingNumericCaller()
            throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    forwardInteger: (f) => { f(7) }
                    forwardFloat: (f) => { f(7.5) }
                    """, "i115d2-numeric-define.protos", module);

            enterCachedTier("forwardInteger", module);
            enterCachedTier("forwardFloat", module);

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(
                        7,
                        evaluate(
                                "forwardInteger(make())\n",
                                "i115d2-integer-warm.protos",
                                module));
                assertTrue(
                        ProtosNumericValueSupport.isCurrentFloat(
                                evaluate(
                                        "forwardFloat(make())\n",
                                        "i115d2-float-warm.protos",
                                        module)));
            }

            ProtosClosureValue inner = closure("make()\n", module);

            for (String name : List.of("forwardInteger", "forwardFloat")) {
                ProtosClosureValue forward = assertInstanceOf(
                        ProtosClosureValue.class,
                        module.context().readLocalSlot(name).orElseThrow());

                // PRUIBA DE I115, en algún momento esto volverá a funcionar
                // assertTrue(
                        // functionRoot(name, module)
                                // .epilogProjectsReturnCarrier());

                RootCallTarget target = directTarget(forward);

                // The inner terminal call is PROVEN.
                // An accepting B5+ caller receives the raw carrier.
                Object[] accepting =
                        ProtosFrameArguments
                                .compactAdmittedTerminalClosureCallOneNoHome(
                                        forward, module, inner);

                Object carrier = target.call(accepting);

                if (name.equals("forwardInteger")) {
                    assertEquals(7L, assertInstanceOf(Long.class, carrier));
                } else {
                    assertEquals(
                            7.5d,
                            assertInstanceOf(Double.class, carrier));
                }

                // A direct RootCallTarget invocation bypasses the call-site
                // dispatcher. Like GraalPy, the root epilogue returns the raw
                // outcome even when its header does not admit a carrier.
                Object[] ordinary =
                        ProtosFrameArguments.compactDirectClosureCallOne(
                                forward, module, null,
                                inner);
                Object internal = target.call(ordinary);
                if (name.equals("forwardInteger")) {
                    assertEquals(7L, assertInstanceOf(Long.class, internal));
                } else {
                    assertEquals(7.5d, assertInstanceOf(Double.class, internal));
                }

                // The observable boundary is the source-call dispatcher,
                // not RootCallTarget.call(): the callee epilogue stays raw,
                // and an ordinary caller receives a proper Protos value.
                Object[] dispatched =
                        ProtosFrameArguments.compactDirectClosureCallOne(
                                forward, module, null,
                                inner);
                Object guest = ProtosFunctionCallDispatchers.callUncached(
                        forward, dispatched, module);
                // Long is Protos' canonical Integer representation; the
                // internal-carrier classifier also recognizes Long, so it
                // cannot be used to reject an observable Integer result.
                if (name.equals("forwardInteger")) {
                    assertInteger(7, guest);
                } else {
                    // A Double, unlike Long, must be materialized to the
                    // guest Float representation at the call boundary.
                    assertFalse(ProtosNumericValueSupport.isPrimitiveCarrier(guest));
                    assertTrue(ProtosNumericValueSupport.isCurrentFloat(guest));
                }
            }
        });
    }

    // ---- I115. Admitted B5 accessors (GraalPy PArguments / ReadFrameNode) ------------

    /**
     * Both source-entry classifications retain their exact caller, Prelude
     * and Task without changing the caller payload.
     */
    @Test
    void admittedAccessorsReadTheCallerOfProvenAndUnknownEntries() throws Exception {
        withCore(module -> {
            evaluate("""
                    helper: (value) => { value }
                    proven: () => { (value) => { value } }
                    unknown: () => { (value) => { helper(value) } }
                    """, "i115-entries-define.protos", module);
            for (String maker : List.of("proven()\n", "unknown()\n")) {
                ProtosClosureValue callee = closure(maker, module);
                RootCallTarget target = directTarget(callee);
                ((ProtosBytecodeRootNode) target.getRootNode()).getBytecodeNode()
                        .setUncachedThreshold(0);
                assertEquals(maker.startsWith("proven"),
                        provenNonSuspending(target), maker);
                Object[] calleeArguments = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                        callee, (Object) module, evaluate("8\n", "i115-eight.protos", module));
                assertInteger(8, ProtosNumericValueSupport.guestValue(target.call(calleeArguments)));
                assertSame(module, calleeArguments[3]);
                assertSame(module.prelude().orElse(null),
                        ProtosFrameArguments.admittedPreludeOrNull(calleeArguments), maker);
                assertSame(module.task().orElse(null),
                        ProtosFrameArguments.admittedCompactTask(calleeArguments), maker);
                assertSame(module, calleeArguments[3]);
            }
        });
    }

    // ---- I115. Per-root caller needs (GraalPy CallerFlagsAssumptionSet) -------------

    @Test
    void callerNeedsAreMonotonicPerRootAndConverge() throws Exception {
        withCore(module -> {
            evaluate("""
                    first: (value) => { value }
                    second: (value) => { value }
                    """, "i115-needs-define.protos", module);
            ProtosBytecodeRootNode first = functionRoot("first", module);
            ProtosBytecodeRootNode second = functionRoot("second", module);
            assertNotSame(first, second);
            int need = ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION;

            com.oracle.truffle.api.Assumption otherBefore = second.callerNeedsStability();
            boolean otherNeeded = second.hasCallerNeed(need);
            com.oracle.truffle.api.Assumption before = first.callerNeedsStability();
            boolean alreadyNeeded = first.hasCallerNeed(need);

            first.requestCallerNeed(need);
            assertTrue(first.hasCallerNeed(need));
            com.oracle.truffle.api.Assumption registered = first.callerNeedsStability();
            assertTrue(registered.isValid());
            if (!alreadyNeeded) {
                assertNotSame(before, registered, "a new need replaces the Assumption");
                assertFalse(before.isValid(), "the previous Assumption is invalidated");
            }

            // Convergence: the same need never invalidates again.
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                first.requestCallerNeed(need);
            }
            assertSame(registered, first.callerNeedsStability());
            assertTrue(registered.isValid());

            // The state belongs to one root: the other root is unaffected.
            assertSame(otherBefore, second.callerNeedsStability());
            assertEquals(otherNeeded, second.hasCallerNeed(need));
        });
    }

    /**
     * A root that publishes its own still-compact activation (here for
     * {@code context}) registers NEEDS_EXACT_ACTIVATION with exactly one
     * Assumption replacement; later publications of new invocations of the
     * same root do not invalidate again.
     */
    @Test
    void realPublicationRegistersTheExactActivationNeedOnce() throws Exception {
        withCore(module -> {
            evaluate("""
                    publishing: () => { (value) => {
                        context.hasSlot("value")
                        value
                    } }
                    """, "i115-needs-publish-define.protos", module);
            ProtosClosureValue callee = closure("publishing()\n", module);
            RootCallTarget target = directTarget(callee);
            ProtosBytecodeRootNode root =
                    assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
            int need = ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION;
            assertFalse(root.hasCallerNeed(need), "a fresh root has no caller need");
            com.oracle.truffle.api.Assumption before = root.callerNeedsStability();

            Object[] first = ProtosFrameArguments.compactDirectClosureCallOne(
                    callee, module, null, SUPPLIED);
            target.call(first);
            ProtosActivation published = assertInstanceOf(ProtosActivation.class, first[0]);
            assertSameProvenance(module, published);
            assertTrue(root.hasCallerNeed(need));
            assertFalse(before.isValid(), "the first need invalidates once");
            com.oracle.truffle.api.Assumption registered = root.callerNeedsStability();
            assertTrue(registered.isValid());

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                Object[] next = ProtosFrameArguments.compactDirectClosureCallOne(
                        callee, module, null, SUPPLIED);
                target.call(next);
                assertInstanceOf(ProtosActivation.class, next[0]);
            }
            assertSame(registered, root.callerNeedsStability(), "no repeated invalidation");
            assertTrue(registered.isValid());
        });
    }

    /**
     * An instance-local call override does not redirect a source-function
     * invocation. Direct dispatch must not request an exact caller merely
     * because that override exists. Repeated calls keep the needs stable.
     */
    @Test
    void terminalSourceFunctionDoesNotFallbackForLocalCall() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    late: make()
                    late.call: (value) => { 100 + value }
                    callOne: (f, value) => { f(value) }
                    """, "i115-needs-fallback-define.protos", module);
            enterCachedTier("callOne", module);
            ProtosBytecodeRootNode root = functionRoot("callOne", module);
            int need = ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION;
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(3, evaluate("callOne(make(), 3)\n", "i115-needs-warm.protos", module));
            }
            com.oracle.truffle.api.Assumption beforeOverride =
                    root.callerNeedsStability();
            boolean beforeExactNeed = root.hasCallerNeed(need);
            assertInteger(4, evaluate("callOne(late, 4)\n", "i115-needs-late.protos", module));
            assertTrue(late(module).hasLocalCallSlotForRuntime());
            assertSame(beforeOverride, root.callerNeedsStability(),
                    "local call override must not trigger a fallback");
            assertEquals(beforeExactNeed, root.hasCallerNeed(need));
            com.oracle.truffle.api.Assumption settled = root.callerNeedsStability();
            boolean registered = root.hasCallerNeed(need);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(5, evaluate("callOne(late, 5)\n", "i115-needs-late2.protos", module));
                assertInteger(6, evaluate("callOne(make(), 6)\n", "i115-needs-admitted.protos", module));
            }
            assertSame(settled, root.callerNeedsStability(), "no repeated invalidation");
            assertTrue(settled.isValid());
            assertEquals(registered, root.hasCallerNeed(need));
        });
    }

    // ---- Caller demand, transport and active guest recovery -----------------------

    @Test
    void callerNeedControlsLegalPayloadKindsInRealGuestCalls() throws Exception {
        withCore(module -> {
            evaluate("""
                    callee: (value) => { value }
                    caller: (value) => { callee(value) }
                    observer: (value) => { context.hasSlot("value"); value }
                    observe: (value) => { observer(value) }
                    """, "i115-need-kinds.protos", module);
            ProtosBytecodeRootNode callee = functionRoot("callee", module);
            int preludeNeed = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;
            int exactNeed = ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION;
            assertFalse(callee.hasCallerNeed(preludeNeed));
            assertFalse(callee.hasCallerNeed(exactNeed));
            assertInteger(11, evaluate("caller(11)\n", "i115-no-need.protos", module));
            var before = callee.callerNeedsStability();
            callee.requestCallerNeed(preludeNeed);
            assertFalse(before.isValid());
            assertInteger(11, evaluate("caller(11)\n", "i115-prelude-need.protos", module));
            ProtosClosureValue template = closure("callee\n", module);
            Object[] transported = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    preludeFree(template), (Object) module.prelude().orElseThrow(), SUPPLIED);
            assertSame(module.prelude().orElseThrow(),
                    ProtosFrameArguments.admittedPreludeOrNull(transported));
            var preludeStable = callee.callerNeedsStability();
            callee.requestCallerNeed(exactNeed);
            assertFalse(preludeStable.isValid());
            assertInteger(11, evaluate("caller(11)\n", "i115-exact-need.protos", module));
            assertInteger(13, evaluate("observe(13)\n", "i115-exact-observe.protos", module));
            assertTrue(functionRoot("observer", module).hasCallerNeed(exactNeed));
            var stable = callee.callerNeedsStability();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(11, evaluate("caller(11)\n", "i115-needs-repeat.protos", module));
            }
            assertTrue(callee.hasCallerNeed(preludeNeed));
            assertTrue(callee.hasCallerNeed(exactNeed));
            assertSame(stable, callee.callerNeedsStability());
            assertTrue(stable.isValid());
        });
    }

    @Test
    void callerPreludeNeedPropagatesTransitivelyAndConverges() throws Exception {
        withCore(module -> {
            evaluate("""
                    a: (value) => { b(value) }
                    b: (value) => { c(value) }
                    c: (value) => { d(value) }
                    d: (value) => { value }
                    """, "i115-transitive.protos", module);
            List<ProtosBytecodeRootNode> roots = List.of(functionRoot("a", module),
                    functionRoot("b", module), functionRoot("c", module), functionRoot("d", module));
            int preludeNeed = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;
            int exactNeed = ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION;
            boolean[] preludeBefore = new boolean[4];
            boolean[] exactBefore = new boolean[4];
            for (int i = 0; i < roots.size(); i++) {
                preludeBefore[i] = roots.get(i).hasCallerNeed(preludeNeed);
                exactBefore[i] = roots.get(i).hasCallerNeed(exactNeed);
            }
            assertInteger(17, evaluate("a(17)\n", "i115-transitive-first.protos", module));
            for (int i = 0; i < roots.size(); i++) {
                assertTrue(!preludeBefore[i] || roots.get(i).hasCallerNeed(preludeNeed));
                assertTrue(!exactBefore[i] || roots.get(i).hasCallerNeed(exactNeed));
                preludeBefore[i] = roots.get(i).hasCallerNeed(preludeNeed);
                exactBefore[i] = roots.get(i).hasCallerNeed(exactNeed);
            }
            roots.get(3).requestCallerNeed(preludeNeed);
            assertInteger(17, evaluate("a(17)\n", "i115-transitive-request.protos", module));
            assertTrue(roots.get(3).hasCallerNeed(preludeNeed));
            List<com.oracle.truffle.api.Assumption> stable = new ArrayList<>();
            for (int i = 0; i < roots.size(); i++) {
                assertTrue(!preludeBefore[i] || roots.get(i).hasCallerNeed(preludeNeed));
                assertTrue(!exactBefore[i] || roots.get(i).hasCallerNeed(exactNeed));
                stable.add(roots.get(i).callerNeedsStability());
            }
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(17, evaluate("a(17)\n", "i115-transitive-repeat.protos", module));
            }
            for (int i = 0; i < roots.size(); i++) {
                assertSame(stable.get(i), roots.get(i).callerNeedsStability());
                assertTrue(stable.get(i).isValid());
            }
        });
    }

    /**
     * Integration over real guest calls A → B → C → D: A is a module
     * function with its own prelude; B, C and D are non-capturing Closures
     * (no own prelude) and each of A, B and C fetches the next Closure by a
     * guarded source send (which, unlike a native send such as indexing,
     * does not publish the sender) and ends in an admitted terminal Call1. The callee root each terminal site caches is the root of the
     * target its DirectCallNode invokes, and the needs land on exactly
     * roots that genuinely request an inherited prelude. With the
     * cached H7 method selections, D does not request one, so C has
     * nothing to transport to D and B has nothing to transport to C.
     * Admitted Send0 selection does not require caller publication.
     * A does not request an inherited caller prelude. Once the needs
     * settle, later calls transport the requested Prelude without
     * repeated invalidation or unnecessary caller publication.
     */
    @Test
    void realChainAvoidsUnneededCallerPreludeAndConverges() throws Exception {
        withCore(module -> {
            evaluate("""
                    makeB: () => { (k) => {
                        next: k.toC()
                        next(k)
                    } }
                    makeC: () => { (k) => {
                        next: k.toD()
                        next(k)
                    } }
                    makeD: () => { (k) => { k.identity(7) } }
                    b: makeB()
                    c: makeC()
                    d: makeD()
                    chain: {
                        identity: (value) => { value }
                        toB: () => { b }
                        toC: () => { c }
                        toD: () => { d }
                    }
                    a: (k) => {
                        next: k.toB()
                        next(k)
                    }
                    """, "i115-ref-chain-define.protos", module);
            ProtosBytecodeRootNode rootA = functionRoot("a", module);
            List<ProtosBytecodeRootNode> inherited = new ArrayList<>();
            for (String name : List.of("b", "c", "d")) {
                ProtosClosureValue closure = assertInstanceOf(ProtosClosureValue.class,
                        module.context().readLocalSlot(name).orElseThrow());
                assertSame(null, closure.preludeOrNullForRuntime(),
                        name + ": non-capturing fixture inherits its prelude");
                inherited.add(assertInstanceOf(ProtosBytecodeRootNode.class,
                        directTarget(closure).getRootNode()));
            }
            ProtosBytecodeRootNode rootB = inherited.get(0);
            ProtosBytecodeRootNode rootC = inherited.get(1);
            ProtosBytecodeRootNode rootD = inherited.get(2);
            List<ProtosBytecodeRootNode> roots = List.of(rootA, rootB, rootC, rootD);
            java.util.Set<ProtosBytecodeRootNode> distinct =
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            distinct.addAll(roots);
            assertEquals(4, distinct.size(), "four distinct roots");
            for (ProtosBytecodeRootNode root : roots) {
                root.getBytecodeNode().setUncachedThreshold(0);
            }
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("a(chain)\n", "i115-ref-chain-warm.protos", module));
            }

            // Each terminal site's cached callee is the root its DirectCallNode invokes.
            assertSame(rootB, tailCallee(rootA));
            assertSame(rootC, tailCallee(rootB));
            assertSame(rootD, tailCallee(rootC));

            /*
             * I115-H7-R3 (RECORDED DIVERGENCE): D's only send is a cached H7
             * GetMethod selection, which reads no caller prelude, so D no
             * longer registers the need (formerly asserted true).
             */
            assertFalse(rootD.hasCallerNeed(need),
                    "D's cached H7 selection reads no inherited prelude");
            assertFalse(rootC.hasCallerNeed(need),
                    "C must not transport a prelude D did not request");
            assertFalse(rootB.hasCallerNeed(need),
                    "B must not transport a prelude C did not request");
            assertFalse(rootA.hasCallerNeed(need), "A owns its prelude");

            List<com.oracle.truffle.api.Assumption> settled = new ArrayList<>();
            for (ProtosBytecodeRootNode root : roots) {
                settled.add(root.callerNeedsStability());
            }
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("a(chain)\n", "i115-ref-chain-again.protos", module));
            }
            for (int i = 0; i < roots.size(); i++) {
                assertSame(settled.get(i), roots.get(i).callerNeedsStability(),
                        "root " + i + ": no repeated invalidation");
                assertTrue(settled.get(i).isValid());
            }
        });
    }

    /**
     * I115-H7-R3: a compact H7 callee that requested nothing from its caller
     * receives a null caller slot from CallBinaryMethod. Neither the caller
     * activation is published nor caller information is requested.
     */
    @Test
    void directMethodCallWithoutCalleeNeedsTransportsNoCaller() throws Exception {
        withCore(module -> {
            evaluate("""
                    holder: { identity: (value) => { value } }
                    sender: (h) => { h.identity(7) }
                    """, "i115-h7r3-no-needs-define.protos", module);
            ProtosObjectValue holder = assertInstanceOf(ProtosObjectValue.class,
                    module.context().readLocalSlot("holder").orElseThrow());
            ProtosClosureValue identity = assertInstanceOf(ProtosClosureValue.class,
                    holder.readLocalSlot("identity").orElseThrow());
            ProtosBytecodeRootNode sender = functionRoot("sender", module);
            ProtosBytecodeRootNode callee = assertInstanceOf(ProtosBytecodeRootNode.class,
                    directTarget(identity).getRootNode());
            // I115: identity is an ordinary root of the single lowering.
            List<String> calleeNames = callee.getBytecodeNode().getInstructionsAsList().stream()
                    .map(ProtosInstructionNames::of)
                    .toList();
            assertTrue(calleeNames.stream().anyMatch(name -> name.startsWith("c.LoadSuppliedArgument")),
                    calleeNames::toString);
            assertTrue(calleeNames.stream().noneMatch(name -> name.contains("CopyArguments")
                            || name.contains("ReadGlobal")),
                    calleeNames::toString);
            sender.getBytecodeNode().setUncachedThreshold(0);
            callee.getBytecodeNode().setUncachedThreshold(0);

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("sender(holder)\n", "i115-h7r3-no-needs.protos", module));
            }
            assertFalse(callee.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
            assertFalse(callee.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION));
            assertFalse(sender.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION),
                    "the sender never published its own activation");

            Object[] header = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    identity, (Object) null, SUPPLIED);
            assertEquals(ProtosFrameArguments.HEADER_LENGTH + 1, header.length);
            assertSame(identity, header[0]);
            assertSame(null, header[3]);
            assertSame(SUPPLIED, header[ProtosFrameArguments.SUPPLIED_ARGUMENT_OFFSET]);
            assertSame(SUPPLIED, ProtosFrameArguments.compactSuppliedArgumentOrNull(header, 0));
            assertTrue(ProtosFrameArguments.hasActivation(header));
            assertFalse(ProtosFrameArguments.hasPublishedActivation(header));
            assertSame(SUPPLIED, ProtosNumericValueSupport.guestValue(directTarget(identity).call(header)));
            assertFalse(ProtosFrameArguments.hasPublishedActivation(header));
            assertSame(null, header[3]);
            assertFalse(callee.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
            assertFalse(callee.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION));
        });
    }

    @Test
    void firstUntransportedPreludeReadRegistersTheNeedOnceAndRecovers() throws Exception {
        withCore(module -> {
            evaluate("""
                    first: (value) => { value }
                    caller: (value) => { first(value) }
                    """, "i115-first-prelude.protos", module);
            ProtosBytecodeRootNode root = functionRoot("first", module);
            ProtosClosureValue template = closure("first\n", module);
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;
            assertFalse(root.hasCallerNeed(need));
            var before = root.callerNeedsStability();
            // This is the observed root's own header, never a callee's slot 3.
            Object[] currentFrameArguments = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    preludeFree(template), (Object) null, SUPPLIED);
            assertSame(null, ProtosFunctionCallDispatchers.admittedCallerPrelude(root, currentFrameArguments));
            assertTrue(root.hasCallerNeed(need));
            assertFalse(before.isValid());
            var registered = root.callerNeedsStability();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(19, evaluate("caller(19)\n", "i115-prelude-entry.protos", module));
                assertSame(null,
                        ProtosFunctionCallDispatchers.admittedCallerPrelude(root, currentFrameArguments));
            }
            Object[] transported = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    preludeFree(template), (Object) module.prelude().orElseThrow(), SUPPLIED);
            assertSame(module.prelude().orElseThrow(),
                    ProtosFunctionCallDispatchers.admittedCallerPrelude(root, transported));
            assertSame(registered, root.callerNeedsStability());
            assertTrue(registered.isValid());
            assertFalse(root.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION));
            assertUnpublished(currentFrameArguments);
            assertUnpublished(transported);
        });
    }

    @Test
    void transportedPreludeAnswersWithoutWalkingAndKeepsPrecedence() throws Exception {
        withCore(module -> {
            ProtosClosureValue template = closure("(value) => { value }\n", module);
            ProtosPrelude own = module.prelude().orElseThrow();
            ProtosActivation other = otherPreludeModule();
            ProtosPrelude transported = other.prelude().orElseThrow();
            assertNotSame(own, transported);
            for (Object payload : new Object[] {transported, other}) {
                for (boolean owns : new boolean[] {false, true}) {
                    Object[] header = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            owns ? ownPrelude(template, own) : preludeFree(template), payload, SUPPLIED);
                    ProtosPrelude expected = owns ? own : transported;
                    assertSame(expected, ProtosFrameArguments.preludeOrNull(header));
                    assertSame(expected, ProtosFrameArguments.admittedPreludeOrNull(header));
                    assertSame(payload, header[3]);
                    assertUnpublished(header);
                }
            }
        });
    }

    @Test
    void independentHeadersPublishOnceAndKeepTaskAndReturnHome() throws Exception {
        withCore(module -> {
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation owner = domainModule(module, domain);
            ProtosTask task = domain.createTask(null, current -> {});
            owner.attachTask(task);
            ProtosDynamicControlState state = owner.dynamicControlState();
            ProtosClosureValue callee = closure("(value) => { value }\n", module);
            Object[] first = ProtosFrameArguments.compactDirectClosureCallOne(callee, owner, null, SUPPLIED);
            Object[] second = ProtosFrameArguments.compactDirectClosureCallOne(callee, owner, null, SUPPLIED);
            ProtosActivation firstActivation = ProtosFrameArguments.activation(first);
            assertUnpublished(second);
            ProtosActivation secondActivation = ProtosFrameArguments.activation(second);
            assertNotSame(firstActivation, secondActivation);
            for (Object[] header : List.of(first, second)) {
                ProtosActivation activation = ProtosFrameArguments.activation(header);
                assertSame(activation, ProtosFrameArguments.publishedActivation(header));
                assertSame(owner, header[3]);
                assertSame(owner, ProtosFrameArguments.resolveExactCaller(header[3]));
                assertSame(owner, ProtosFrameArguments.resolveAdmittedCaller(header[3]));
                assertSame(task, ProtosFrameArguments.compactTask(header));
                assertSame(task, ProtosFrameArguments.admittedCompactTask(header));
                assertSame(task, activation.task().orElseThrow());
                assertSame(header[4], activation.returnHome().orElseThrow());
                assertSame(state, activation.dynamicControlStateIfPresent().orElseThrow());
                assertSameProvenance(owner, activation);
            }
        });
    }

    @Test
    void deepRealGuestChainPreservesResultAndStableCallerNeeds() throws Exception {
        withCore(module -> {
            StringBuilder source = new StringBuilder();
            for (int i = 0; i < 64; i++) {
                source.append("hop").append(i).append(": (value) => { hop")
                        .append(i + 1).append("(value) }\n");
            }
            source.append("hop64: (value) => { value }\n");
            evaluate(source.toString(), "i115-deep-source.protos", module);
            int prelude = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;
            int exact = ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION;
            boolean[] previousPrelude = new boolean[65];
            boolean[] previousExact = new boolean[65];
            for (int run = 0; run < 2; run++) {
                assertInteger(37, evaluate("hop0(37)\n", "i115-deep-call.protos", module));
                for (int i = 0; i <= 64; i++) {
                    ProtosBytecodeRootNode root = functionRoot("hop" + i, module);
                    assertTrue(!previousPrelude[i] || root.hasCallerNeed(prelude));
                    assertTrue(!previousExact[i] || root.hasCallerNeed(exact));
                    previousPrelude[i] = root.hasCallerNeed(prelude);
                    previousExact[i] = root.hasCallerNeed(exact);
                }
            }
        });
    }

    @Test
    void illegalCallerPayloadsCannotBeConstructed() throws Exception {
        withCore(module -> {
            ProtosClosureValue callee = closure("(value) => { value }\n", module);
            Object[] self = new Object[1];
            self[0] = self;
            Object[] valid = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    callee, (Object) module, SUPPLIED);
            for (Object invalid : new Object[] {self, valid, new Object[0], new Object(), "invalid"}) {
                assertThrows(IllegalArgumentException.class,
                        () -> ProtosFrameArguments.compactDirectClosureCallOneNoHome(callee, invalid, SUPPLIED));
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.resolveExactCaller(invalid));
            }
            assertThrows(IllegalStateException.class, () -> ProtosFrameArguments.resolveExactCaller(null));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.resolveExactCaller(module.prelude().orElseThrow()));
            assertUnpublished(valid);
            assertSame(module, valid[3]);
        });
    }

    @Test
    void terminalSendRegistersTheCallerPreludeNeedOnceAndConverges() throws Exception {
        withCore(module -> {
            evaluate("""
                    receiver: { identity: (value) => { value } }
                    make: () => { (target) => { target.identity(7) } }
                    inner: make()
                    callInner: (target) => { inner(target) }
                    """, "i115-terminal-send.protos", module);
            ProtosClosureValue inner = closure("inner\n", module);
            assertSame(null, inner.preludeOrNullForRuntime());
            ProtosBytecodeRootNode root = functionRoot("inner", module);
            root.getBytecodeNode().setUncachedThreshold(0);
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;
            assertFalse(root.hasCallerNeed(need));
            var before = root.callerNeedsStability();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callInner(receiver)\n", "i115-terminal-warm.protos", module));
            }
            assertFalse(root.hasCallerNeed(need));
            assertSame(before, root.callerNeedsStability());
            assertTrue(before.isValid());
            root.requestCallerNeed(need);
            assertTrue(root.hasCallerNeed(need));
            assertFalse(before.isValid());
            var stable = root.callerNeedsStability();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callInner(receiver)\n", "i115-terminal-repeat.protos", module));
            }
            Object[] header = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    inner, (Object) module.prelude().orElseThrow(), SUPPLIED);
            assertSame(module.prelude().orElseThrow(), header[3]);
            assertSame(module.prelude().orElseThrow(), ProtosFrameArguments.admittedPreludeOrNull(header));
            assertSame(stable, root.callerNeedsStability());
            assertTrue(stable.isValid());
        });
    }

    /** Registered caller demand preserves the real task suspension and resumed result. */
    @Test
    void transportedCallerSurvivesSuspensionAndResumption() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                ProtosActivation module =
                        prelude.newModuleActivation(
                                new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
                ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
                ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("wait", wait);
                evaluate("""
                        make: () => { (future) => {
                            result: future.value()
                            result
                        } }
                        callOne: (f, value) => { f(value) }
                        """, "i115-ref-suspend-define.protos", module);
                ProtosClosureValue inner = closure("make()\n", module);
                assertSame(null, inner.preludeOrNullForRuntime(),
                        "non-capturing fixture inherits its prelude");
                ProtosBytecodeRootNode innerRoot = assertInstanceOf(
                        ProtosBytecodeRootNode.class, directTarget(inner).getRootNode());
                innerRoot.requestCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE);
                enterCachedTier("callOne", module);
                CallTarget top = ProtosLanguageContext.current().parsePublic(
                        Source.newBuilder(ProtosLanguage.ID, "callOne(make(), wait)\n",
                                        "i115-ref-suspend-top.protos")
                                .mimeType(ProtosLanguage.MIME_TYPE)
                                .build());

                ProtosTask task = domain.createTask(
                        null, current -> ProtosBytecodeTaskExecution.execute(current, top, module));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state(), "really suspended");

                assertTrue(wait.resolve(resolved, module));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertSame(resolved, task.result().orElseThrow(), "exact resumed result");
            } finally {
                context.leave();
            }
        }
    }

    /** Caller needs belong to one Context's root: registering in one leaves the other unchanged. */
    @Test
    void callerNeedsAreIsolatedBetweenContexts() throws Exception {
        String define = "make: () => { (value) => { value } }\n";
        try (Harness first = new Harness(false, null);
                Harness second = new Harness(false, null)) {
            ModuleAction<ProtosBytecodeRootNode> innerRoot = module -> {
                evaluate(define, "i115-ref-context-define.protos", module);
                return assertInstanceOf(ProtosBytecodeRootNode.class,
                        directTarget(closure("make()\n", module)).getRootNode());
            };
            ProtosBytecodeRootNode firstRoot = first.call(innerRoot);
            ProtosBytecodeRootNode secondRoot = second.call(innerRoot);
            assertNotSame(firstRoot, secondRoot);
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;
            com.oracle.truffle.api.Assumption secondBefore = secondRoot.callerNeedsStability();
            boolean secondNeeded = secondRoot.hasCallerNeed(need);

            first.call(module -> {
                firstRoot.requestCallerNeed(need);
                return null;
            });
            assertTrue(firstRoot.hasCallerNeed(need));
            assertSame(secondBefore, secondRoot.callerNeedsStability());
            assertEquals(secondNeeded, secondRoot.hasCallerNeed(need));
        }
    }

    // ---- I115. Captured reads after the activation is published ---------------------

    /**
     * A captured read in a root whose activation was published (here by
     * {@code context}) keeps reading the owner frame, and abandons its
     * selection once the owner binding is removed and reads the recreated
     * binding of the same owner afterwards (D179 C0).
     */
    @Test
    void capturedReadAfterPublicationFollowsTheOwnerBinding() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: (seed) => {
                        owner: context
                        middle: (unused) => {
                            (v) => {
                                context.hasSlot("v")
                                seed + v
                            }
                        }
                        reader: middle(0)
                        [reader, () => { owner.removeSlot("seed") }, () => { owner.seed: 30 }]
                    }
                    pair: make(10)
                    read: pair[0]
                    drop: pair[1]
                    recreate: pair[2]
                    """, "i115-captured-define.protos", module);
            ProtosClosureValue read = assertInstanceOf(
                    ProtosClosureValue.class, module.context().readLocalSlot("read").orElseThrow());
            read.executionPlan().orElseThrow()
                    .bytecodeActivationRootForTesting().getBytecodeNode().setUncachedThreshold(0);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(10 + i, evaluate("read(" + i + ")\n", "i115-captured-read.protos", module));
            }
            evaluate("drop()\n", "i115-captured-drop.protos", module);
            assertThrows(com.guillermomolina.protos.runtime.ProtosSignalException.class,
                    () -> evaluate("read(1)\n", "i115-captured-removed.protos", module));
            // D179: the recreated binding of the same owner is read again.
            evaluate("recreate()\n", "i115-captured-recreate.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(30 + i, evaluate("read(" + i + ")\n", "i115-captured-again.protos", module));
            }
        });
    }

    // ---- helpers ---------------------------------------------------------------------

    /** The same definition and captures, without an own prelude; never executed. */
    private static ProtosClosureValue preludeFree(ProtosClosureValue template) {
        return new ProtosClosureValue(
                template.definition(),
                template.capturedLexicalContexts(),
                template.capturedReceiver());
    }

    /** The same definition and captures, with {@code prelude} as its own prelude; never executed. */
    private static ProtosClosureValue ownPrelude(ProtosClosureValue template, ProtosPrelude prelude) {
        return new ProtosClosureValue(
                template.definition(),
                template.capturedLexicalContexts(),
                template.capturedReceiver(),
                template.methodHome().orElse(null),
                template.returnHome().orElse(null),
                prelude);
    }

    /** A module of a second, independently bootstrapped prelude in the current Context. */
    private static ProtosActivation otherPreludeModule() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
    }

    private static ProtosActivation domainModule(
            ProtosActivation module, ProtosActorExecutionDomain domain) {
        ProtosPrelude prelude = module.prelude().orElseThrow();
        return prelude.newModuleActivation(
                new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
    }

    private static void assertUnpublished(Object[] arguments) {
        assertInstanceOf(ProtosClosureValue.class, arguments[0]);
    }

    private static void assertSameProvenance(ProtosActivation expected, ProtosActivation actual) {
        assertSame(expected.prelude().orElse(null), actual.prelude().orElse(null));
        assertSame(expected.actorModuleState(), actual.actorModuleState());
        assertSame(expected.currentModuleKey().orElse(null), actual.currentModuleKey().orElse(null));
        assertSame(expected.executionDomain(), actual.executionDomain());
        assertSame(expected.task().orElse(null), actual.task().orElse(null));
    }

    private static ProtosClosureValue late(ProtosActivation module) {
        return assertInstanceOf(
                ProtosClosureValue.class, module.context().readLocalSlot("late").orElseThrow());
    }

    /**
     * Moves the named module function's root to the cached Bytecode tier on
     * its next entry, so its operation nodes and their caches exist.
     */
    private static void enterCachedTier(String function, ProtosActivation module) {
        functionRoot(function, module).getBytecodeNode().setUncachedThreshold(0);
    }

    private static ProtosBytecodeRootNode functionRoot(String function, ProtosActivation module) {
        ProtosClosureValue closure = assertInstanceOf(
                ProtosClosureValue.class,
                module.context().readLocalSlot(function).orElseThrow());
        return closure.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException absent) {
                // Continue with the superclass.
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

    private interface ModuleAction<T> {
        T run(ProtosActivation module) throws Exception;
    }

    /**
     * One Context of a possibly shared Engine. With {@code debugger}, a Truffle debugger session
     * is attached to the Engine before any lowering (PLAT067-B).
     */
    private static final class Harness implements AutoCloseable {
        private final Context context;
        private final DebuggerSession debugger;
        private ProtosPrelude prelude;

        Harness(boolean debugger, Engine engine) {
            Context.Builder builder = Context.newBuilder(ProtosLanguage.ID);
            if (engine != null) {
                builder.engine(engine);
            }
            this.context = builder.build();
            this.debugger =
                    debugger
                            ? Debugger.find(context.getEngine()).startSession(event -> {})
                            : null;
            context.initialize(ProtosLanguage.ID);
        }

        <T> T call(ModuleAction<T> action) throws Exception {
            context.enter();
            try {
                if (prelude == null) {
                    prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                }
                return action.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }

        static ProtosClosureValue identityClosure(ProtosActivation module) {
            return closure("(value) => { value }\n", module);
        }

        @Override
        public void close() {
            if (debugger != null) {
                debugger.close();
            }
            context.close();
        }
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

    private static Object evaluate(String characters, String name, ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, name)
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return target.call(activation);
    }

    private static ProtosClosureValue closure(String characters, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosClosureValue.class,
                evaluate(characters, "i115b-closure.protos", activation));
    }

    private static void assertInteger(long expected, Object actual) {
        // Compact Truffle roots may return JVM primitive integers.
        if (actual instanceof Long number) {
            assertEquals(expected, number.longValue());
            return;
        }
        if (actual instanceof Integer number) {
            assertEquals(expected, number.longValue());
            return;
        }
        assertEquals(
                Long.toString(expected),
                com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                        .integerDecimalText(actual));
    }

    /** I115: the effective CallTarget the shared dispatcher enters for a source Closure. */
    private static RootCallTarget directTarget(ProtosClosureValue closure) {
        RootCallTarget target =
                ProtosClosureCallPreparation.functionCallTarget(
                        closure, ProtosLanguageContext.current());
        assertNotNull(target);
        return target;
    }

    private static boolean provenNonSuspending(RootCallTarget target) {
        return target != null
                && target.getRootNode() instanceof ProtosBytecodeRootNode root
                && root.provablyNonSuspendingBody();
    }

    private static boolean bytecodeTarget(RootCallTarget target) {
        return target != null && target.getRootNode() instanceof ProtosBytecodeRootNode;
    }

    /**
     * I115: the callee root of a root's single terminal Call1 site, read from
     * the DirectCallNode(s) its shared-dispatcher PIC installed.
     */
    private static ProtosBytecodeRootNode tailCallee(ProtosBytecodeRootNode root)
            throws Exception {
        List<RootCallTarget> targets = siteTargets(root);
        assertTrue(!targets.isEmpty(), "the terminal site has a direct entry");
        ProtosBytecodeRootNode callee = null;
        for (RootCallTarget target : targets) {
            ProtosBytecodeRootNode cached =
                    assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
            if (callee != null) {
                assertSame(callee, cached, "one callee per site in this chain");
            }
            callee = cached;
        }
        return callee;
    }

    /** I115: the direct CallTargets of a function's single CallUnaryMethod site. */
    private static List<RootCallTarget> tierEntries(String function, ProtosActivation module)
            throws Exception {
        return siteTargets(functionRoot(function, module));
    }

    private static List<RootCallTarget> siteTargets(ProtosBytecodeRootNode root) throws Exception {
        Node[] cachedNodes = (Node[]) field(root.getBytecodeNode(), "cachedNodes_");
        List<RootCallTarget> targets = new ArrayList<>();
        int sites = 0;
        for (Node node : cachedNodes) {
            if (node == null || !node.getClass().getSimpleName().startsWith("CallUnaryMethod")) {
                continue;
            }
            sites++;
            collectDirectTargets(
                    node,
                    targets,
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()),
                    0);
        }
        assertEquals(1, sites, "expected exactly one CallUnaryMethod site");
        return targets;
    }

    /* Walks only DSL-generated holders of the site; never parents or roots. */
    private static void collectDirectTargets(
            Object value, List<RootCallTarget> targets, java.util.Set<Object> seen, int depth)
            throws ReflectiveOperationException {
        if (value == null || depth > 8 || !seen.add(value)) {
            return;
        }
        if (value instanceof com.oracle.truffle.api.nodes.DirectCallNode direct) {
            targets.add((RootCallTarget) direct.getCallTarget());
            return;
        }
        if (value instanceof com.oracle.truffle.api.nodes.RootNode
                || value instanceof com.oracle.truffle.api.bytecode.BytecodeNode
                || !value.getClass().getName().contains("Gen")) {
            return;
        }
        for (Class<?> type = value.getClass();
                type != null && type != Node.class && type != Object.class;
                type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                collectDirectTargets(field.get(value), targets, seen, depth + 1);
            }
        }
    }

}
