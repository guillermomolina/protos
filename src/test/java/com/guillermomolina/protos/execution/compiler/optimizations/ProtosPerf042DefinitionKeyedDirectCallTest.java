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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.InvokeClosureOne;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.InvokeClosureOneTail;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.InvokeClosureZero;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
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
import org.junit.jupiter.api.Test;

/**
 * PERF042 coverage for the definition-keyed tier of the fused direct
 * Closure Call0/Call1 operations ({@code InvokeClosureZero},
 * {@code InvokeClosureOne}).
 *
 * <p>Admission is proved through the tier's own admission helpers on real
 * fresh Closures: fresh instances of one definition share the cached
 * Context-owned target, the target-level proof holds once per definition, and
 * the instance-level proof (ordinary {@code call} selection and unobservable
 * ReturnHome) is re-established for the current instance. I114: that proof is
 * no longer a full per-instance lookup; it is the Closure's local {@code call}
 * presence plus the invalidable root {@code Object.call} certificate, and the
 * ReturnHome half never constructs a home or rematerializes a plan. Guest workloads
 * repeat every call site more often than either cache tier's limit and check
 * that ordinary semantics, including {@code call} overrides added, removed and
 * re-created after warm-up, are preserved.
 *
 * <p>Installation of the definition tier on a real call site is proved
 * structurally: the enclosing root is moved to the cached Bytecode tier and the
 * generated operation node's {@code definitionSource} cache is read
 * reflectively. This proves installation and persistence of an entry and the
 * identity of its cached target; it does not prove how often hot calls use the
 * entry or what compiled code results, which needs a Tier-2 graph.
 */
final class ProtosPerf042DefinitionKeyedDirectCallTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final int PAST_CACHE_LIMITS = 12;

    @Test
    void freshCallOneClosuresConvergeOnTheDefinitionTier() throws Exception {
        withCore(module -> {
            evaluate("""
                    run: () => {
                        identity: (value) => { value }
                        identity(1)
                    }
                    make: () => { (value) => { value } }
                    """, "perf042-c1-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "perf042-c1-run.protos", module));
            }

            ProtosClosureValue first = closure("make()\n", module);
            ProtosClosureValue second = closure("make()\n", module);
            assertNotSame(first, second);
            assertSame(first.definition(), second.definition());
            assertSame(first.definition(), InvokeClosureOne.sourceDefinitionOrNull(second));

            ProtosLanguageContext context = ProtosLanguageContext.current();
            RootCallTarget target = InvokeClosureOne.definitionTarget(first, module, context);
            assertNotNull(target);
            assertSame(target, InvokeClosureOne.definitionTarget(second, module, context));
            assertTrue(InvokeClosureOne.admittedTarget(target, 1));
            assertSame(first, InvokeClosureZero.admittedInstanceOrNull(first, module));
            assertSame(second, InvokeClosureZero.admittedInstanceOrNull(second, module));
        });
    }

    @Test
    void freshCallZeroClosuresConvergeOnTheDefinitionTier() throws Exception {
        withCore(module -> {
            evaluate("""
                    run: () => {
                        constant: () => { 1 }
                        constant()
                    }
                    make: () => { () => { 1 } }
                    """, "perf042-c0-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "perf042-c0-run.protos", module));
            }

            ProtosClosureValue first = closure("make()\n", module);
            ProtosClosureValue second = closure("make()\n", module);
            assertNotSame(first, second);

            ProtosLanguageContext context = ProtosLanguageContext.current();
            RootCallTarget target = InvokeClosureZero.definitionTarget(first, module, context);
            assertNotNull(target);
            assertSame(target, InvokeClosureZero.definitionTarget(second, module, context));
            assertTrue(InvokeClosureZero.admittedTarget(target, 0));
            // D203/I105: an excess argument enters the same ordinary root.
            assertTrue(InvokeClosureOne.admittedTarget(target, 1));
            assertSame(second, InvokeClosureZero.admittedInstanceOrNull(second, module));
        });
    }

    @Test
    void freshInstancesKeepTheirOwnIdentityAndCapturedEnvironment() throws Exception {
        withCore(module -> {
            evaluate("""
                    passThrough: () => { (value) => { value } }
                    makeAdder: (seed) => { (value) => { seed + value } }
                    makeConstant: (seed) => { () => { seed } }
                    callOne: (f, value) => { f(value) }
                    callZero: (f) => { f() }
                    """, "perf042-capture-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                ProtosClosureValue fresh = closure("passThrough()\n", module);
                module.context().createLocalSlot("fresh" + i, fresh);
                assertSame(
                        fresh,
                        evaluate("callOne(passThrough(), fresh" + i + ")\n",
                                "perf042-identity.protos", module));
                assertInteger(i + 1, evaluate(
                        "callOne(makeAdder(" + i + "), 1)\n",
                        "perf042-capture-one.protos", module));
                assertInteger(i, evaluate(
                        "callZero(makeConstant(" + i + "))\n",
                        "perf042-capture-zero.protos", module));
            }
        });
    }

    @Test
    void callOverridesAreObservedBeforeAndAfterWarmUp() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    makeZero: () => { () => { 1 } }
                    callOne: (f) => { f(7) }
                    callZero: (f) => { f() }
                    early: make()
                    early.call: (value) => { 100 + value }
                    earlyZero: makeZero()
                    earlyZero.call: () => { 100 }
                    """, "perf042-override-define.protos", module);

            // Override installed before the site ever warmed up.
            assertInteger(107, evaluate("callOne(early)\n", "perf042-early.protos", module));
            assertInteger(100, evaluate("callZero(earlyZero)\n", "perf042-early0.protos", module));

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callOne(make())\n", "perf042-warm.protos", module));
                assertInteger(1, evaluate("callZero(makeZero())\n", "perf042-warm0.protos", module));
            }

            // Override installed after warm-up on a fresh instance of the
            // cached definition; then removed, re-created and mixed with
            // standard instances of the same definition at the same site.
            evaluate("""
                    late: make()
                    late.call: (value) => { 200 + value }
                    lateZero: makeZero()
                    lateZero.call: () => { 200 }
                    """, "perf042-late-define.protos", module);
            assertInteger(207, evaluate("callOne(late)\n", "perf042-late.protos", module));
            assertInteger(200, evaluate("callZero(lateZero)\n", "perf042-late0.protos", module));
            assertInteger(7, evaluate("callOne(make())\n", "perf042-mixed.protos", module));
            assertInteger(1, evaluate("callZero(makeZero())\n", "perf042-mixed0.protos", module));

            evaluate("""
                    late.removeSlot("call")
                    lateZero.removeSlot("call")
                    """, "perf042-remove.protos", module);
            assertInteger(7, evaluate("callOne(late)\n", "perf042-removed.protos", module));
            assertInteger(1, evaluate("callZero(lateZero)\n", "perf042-removed0.protos", module));

            evaluate("""
                    late.call: (value) => { 300 + value }
                    lateZero.call: () => { 300 }
                    """, "perf042-recreate.protos", module);
            assertInteger(307, evaluate("callOne(late)\n", "perf042-recreated.protos", module));
            assertInteger(300, evaluate("callZero(lateZero)\n", "perf042-recreated0.protos", module));

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callOne(make())\n", "perf042-after.protos", module));
                assertInteger(1, evaluate("callZero(makeZero())\n", "perf042-after0.protos", module));
                assertInteger(307, evaluate("callOne(late)\n", "perf042-after-late.protos", module));
                assertInteger(107, evaluate("callOne(early)\n", "perf042-after-early.protos", module));
            }

            ProtosClosureValue late = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("late").orElseThrow());
            assertNull(InvokeClosureZero.admittedInstanceOrNull(late, module));
        });
    }

    @Test
    void admissionBoundariesRejectAndFallBack() throws Exception {
        withCore(module -> {
            evaluate("""
                    makeEscaper: () => { (value) => { ^value } }
                    makeAdder: (seed) => { (value) => { seed + value } }
                    outer: () => {
                        inner: (value) => { ^value }
                        inner(5)
                        0
                    }
                    """, "perf042-boundary-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(5, evaluate("outer()\n", "perf042-escape.protos", module));
            }

            ProtosLanguageContext context = ProtosLanguageContext.current();

            // Observable ReturnHome: the exact instance cannot prove the
            // unobservable marker.
            ProtosClosureValue escaper = closure("makeEscaper()\n", module);
            assertNull(InvokeClosureZero.admittedInstanceOrNull(escaper, module));

            // A body that may suspend (a dynamically selected send) is not
            // admitted at the target level.
            ProtosClosureValue adder = closure("makeAdder(1)\n", module);
            RootCallTarget adderTarget = InvokeClosureOne.definitionTarget(adder, module, context);
            assertNotNull(adderTarget);
            assertFalse(InvokeClosureOne.admittedTarget(adderTarget, 1));

            // No entered Context, or a receiver that is not a Closure, yields
            // no target projection.
            assertNull(InvokeClosureOne.definitionTarget(adder, module, null));
            assertNull(InvokeClosureOne.definitionTarget(new Object(), module, context));
            assertNull(InvokeClosureOne.sourceDefinitionOrNull(new Object()));

            // A native Closure has no source definition for this tier.
            Object standardCall =
                    ProtosObjectValue.rootObject().readLocalSlot("call").orElseThrow();
            ProtosClosureValue nativeClosure =
                    assertInstanceOf(ProtosClosureValue.class, standardCall);
            assertTrue(nativeClosure.nativeBody().isPresent());
            assertNull(InvokeClosureZero.sourceDefinitionOrNull(nativeClosure));
            assertNull(InvokeClosureZero.admittedInstanceOrNull(nativeClosure, module));
        });
    }

    @Test
    void definitionTierIsInstalledAtTheCanonicalCallSite() throws Exception {
        withCore(module -> {
            evaluate("""
                    runOne: () => {
                        identity: (value) => { value }
                        f: identity
                        f(1)
                    }
                    runZero: () => {
                        constant: () => { 1 }
                        f: constant
                        f()
                    }
                    """, "perf042-installed-define.protos", module);
            enterCachedTier("runOne", module);
            enterCachedTier("runZero", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("runOne()\n", "perf042-installed-one.protos", module));
                assertInteger(1, evaluate("runZero()\n", "perf042-installed-zero.protos", module));
            }

            // Every fresh literal instance shares one plan, so the fresh
            // instances past the identity tier converge on one entry.
            assertSingleAdmittedEntry(
                    definitionTierEntries("runOne", InvokeClosureOne.class, module), 1);
            assertSingleAdmittedEntry(
                    definitionTierEntries("runZero", InvokeClosureZero.class, module), 0);
        });
    }

    @Test
    void distinctExecutionPlansKeepDistinctDefinitionTierTargets() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    callOne: (f, value) => { f(value) }
                    """, "perf042-plans-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callOne(make(), 7)\n", "perf042-plans-warm.protos", module));
            }

            // A second legitimate plan for the same definition and Context:
            // the rebuild a Context projection performs, carried by a P
            // projection of a fresh instance.
            ProtosClosureValue original = closure("make()\n", module);
            ProtosClosureExecutionPlan template = original.executionPlan().orElseThrow();
            ProtosLanguageContext context = ProtosLanguageContext.current();
            ProtosClosureExecutionPlan rebuilt =
                    template.rebuildBytecodeForLanguage(
                            original.definition(), context.languageForTesting(), ProtosExecutionMode.NORMAL);
            ProtosPrelude prelude = module.prelude().orElseThrow();
            ProtosClosureValue projected =
                    original.parallelProjection(
                            List.of(prelude.newExecutionContext()),
                            ProtosNullValue.INSTANCE,
                            prelude,
                            rebuilt);
            assertSame(original.definition(), projected.definition());
            assertNotSame(template, rebuilt);

            RootCallTarget originalTarget =
                    InvokeClosureOne.definitionTarget(original, module, context);
            RootCallTarget projectedTarget =
                    InvokeClosureOne.definitionTarget(projected, module, context);
            assertSame(template.bytecodeActivationTargetForComposition(), originalTarget);
            assertSame(rebuilt.bytecodeActivationTargetForComposition(), projectedTarget);
            assertNotSame(originalTarget, projectedTarget);
            assertTrue(InvokeClosureOne.admittedTarget(projectedTarget, 1));

            module.context().createLocalSlot("projected", projected);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(i, evaluate(
                        "callOne(projected, " + i + ")\n", "perf042-plans-projected.protos", module));
                assertInteger(i, evaluate(
                        "callOne(make(), " + i + ")\n", "perf042-plans-original.protos", module));
            }

            // One entry per plan, each holding exactly its own plan's target.
            List<Object> entries = definitionTierEntries("callOne", InvokeClosureOne.class, module);
            assertEquals(2, entries.size());
            List<Object> targets = new ArrayList<>();
            for (Object entry : entries) {
                ProtosClosureExecutionPlan plan =
                        (ProtosClosureExecutionPlan) field(entry, "cachedSourcePlan_");
                assertSame(plan.bytecodeActivationTargetForComposition(), field(entry, "cachedTarget_"));
                targets.add(field(entry, "cachedTarget_"));
            }
            assertTrue(targets.contains(originalTarget));
            assertTrue(targets.contains(projectedTarget));
        });
    }

    @Test
    void foreignContextClosureProjectsATargetOwnedByTheEnteredContext() throws Exception {
        try (Context first = Context.newBuilder(ProtosLanguage.ID).build()) {
            first.initialize(ProtosLanguage.ID);
            first.enter();
            ProtosClosureValue foreign;
            RootCallTarget foreignTarget;
            try {
                ProtosActivation module =
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
                evaluate("make: () => { (value) => { value } }\n", "perf042-foreign-define.protos", module);
                foreign = closure("make()\n", module);
                foreignTarget = InvokeClosureOne.definitionTarget(
                        foreign, module, ProtosLanguageContext.current());
            } finally {
                first.leave();
            }
            ProtosClosureExecutionPlan template = foreign.executionPlan().orElseThrow();
            assertSame(template.bytecodeActivationTargetForComposition(), foreignTarget);

            withCore(module -> {
                ProtosLanguageContext entered = ProtosLanguageContext.current();
                assertNotSame(template.language().orElseThrow(), entered.languageForTesting());
                int projectionsBefore = entered.projectedBytecodeExecutionPlanCountForTesting();

                // The derivation the definition tier caches per entered
                // Context never yields the other Context's target.
                RootCallTarget projected =
                        ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
                                foreign, entered);
                assertNotNull(projected);
                assertNotSame(foreignTarget, projected);
                assertSame(
                        entered.languageForTesting(),
                        projected.getRootNode().getLanguage(ProtosLanguage.class));
                assertSame(
                        projected,
                        ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
                                foreign, entered));
                assertEquals(
                        projectionsBefore + 1,
                        entered.projectedBytecodeExecutionPlanCountForTesting());
                assertSame(template, foreign.executionPlan().orElseThrow());
            });
        }
    }

    @Test
    void overriddenCallDoesNotPrepareAnUnavailableOriginalPlan() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    callOne: (f, value) => { f(value) }
                    """, "perf042-deferred-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callOne(make(), 7)\n", "perf042-deferred-warm.protos", module));
            }
            Object entry = singleEntry("callOne", module);

            // A P-style deferred projection of the warmed definition whose
            // original plan can never be prepared.
            int[] rematerializations = {0};
            ProtosClosureValue template = closure("make()\n", module);
            ProtosPrelude prelude = module.prelude().orElseThrow();
            ProtosClosureValue deferred =
                    template.parallelProjectionDeferred(
                            List.of(prelude.newExecutionContext()),
                            ProtosNullValue.INSTANCE,
                            prelude,
                            () -> {
                                rematerializations[0]++;
                                throw new IllegalStateException(
                                        "PERF042: the original plan must not be prepared");
                            });
            assertSame(template.definition(), deferred.definition());
            module.context().createLocalSlot("deferred", deferred);
            evaluate("deferred.call: (value) => { 100 + value }\n", "perf042-deferred-override.protos", module);

            assertNull(InvokeClosureOne.definitionTarget(
                    deferred, module, ProtosLanguageContext.current()));
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(107, evaluate("callOne(deferred, 7)\n", "perf042-deferred-call.protos", module));
                assertInteger(7, evaluate("callOne(make(), 7)\n", "perf042-deferred-mixed.protos", module));
            }
            assertEquals(0, rematerializations[0]);
            assertTrue(deferred.executionPlan().isEmpty());
            assertSame(entry, singleEntry("callOne", module));
        });
    }

    @Test
    void transientRejectionsKeepTheDefinitionTierEntry() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    makeAdder: (seed) => { (value) => { seed + value } }
                    callOne: (f, value) => { f(value) }
                    overriddenFirst: (f, value) => { f(value) }
                    overridden: make()
                    overridden.call: (value) => { 100 + value }
                    """, "perf042-transient-define.protos", module);
            enterCachedTier("callOne", module);
            enterCachedTier("overriddenFirst", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callOne(make(), 7)\n", "perf042-transient-warm.protos", module));
            }
            Object entry = singleEntry("callOne", module);

            // Overridden instance of the cached definition, then a definition
            // whose target is not admitted, then admitted instances again.
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(107, evaluate("callOne(overridden, 7)\n", "perf042-transient-override.protos", module));
                assertInteger(8, evaluate("callOne(makeAdder(1), 7)\n", "perf042-transient-adder.protos", module));
                assertInteger(7, evaluate("callOne(make(), 7)\n", "perf042-transient-back.protos", module));
            }
            // I112: the final call is terminal, and terminal admission does
            // not require a non-suspending target, so the adder definition is
            // admitted with its own entry; the original entry is kept.
            List<Object> entries = definitionTierEntries("callOne", InvokeClosureOne.class, module);
            assertEquals(2, entries.size());
            assertTrue(entries.contains(entry));

            // A site whose first receiver is overridden still installs the
            // definition tier for the admitted instances that follow.
            assertInteger(107, evaluate("overriddenFirst(overridden, 7)\n", "perf042-first-override.protos", module));
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("overriddenFirst(make(), 7)\n", "perf042-first-admitted.protos", module));
            }
            assertSingleAdmittedEntry(
                    definitionTierEntries("overriddenFirst", InvokeClosureOne.class, module), 1);
        });
    }

    @Test
    void i108ArgumentMutationAndGenericDirectRecovery() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    target: make()
                    target.call: (value) => { 100 + value }

                    evaluations: 0

                    ordinaryArgument: () => {
                        evaluations = evaluations + 1
                        7
                    }

                    installOverride: () => {
                        evaluations = evaluations + 1
                        target.call: (value) => { 100 + value }
                        7
                    }

                    invoke: (callee, argumentThunk) => {
                        callee(argumentThunk())
                    }
                    """, "i108-dynamic-selection-define.protos", module);

            enterCachedTier("invoke", module);

            // Generic first: this instance already overrides Object.call.
            assertInteger(
                    107,
                    evaluate(
                            "invoke(target, ordinaryArgument)\n",
                            "i108-generic-first.protos",
                            module));

            // Remove the override, then warm the same site on direct calls.
            evaluate(
                    "target.removeSlot(\"call\")\n",
                    "i108-remove-first.protos",
                    module);

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(
                        7,
                        evaluate(
                                "invoke(target, ordinaryArgument)\n",
                                "i108-direct-warm.protos",
                                module));
            }

            Object directEntry = singleEntry("invoke", module);
            assertSingleAdmittedEntry(
                    definitionTierEntries(
                            "invoke", InvokeClosureOne.class, module),
                    1);

            // Selection MUST occur after argument evaluation.
            // The argument reinstalls Object.call on the staged receiver.
            assertInteger(
                    107,
                    evaluate(
                            "invoke(target, installOverride)\n",
                            "i108-mutation-during-argument.protos",
                            module));

            // Removing it must restore the positive direct specialization.
            evaluate(
                    "target.removeSlot(\"call\")\n",
                    "i108-remove-second.protos",
                    module);

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(
                        7,
                        evaluate(
                                "invoke(target, ordinaryArgument)\n",
                                "i108-direct-recovered.protos",
                                module));
            }

            assertSame(
                    directEntry,
                    singleEntry("invoke", module),
                    "generic fallback must not discard the admitted tier");

            // One observable argument evaluation per invocation.
            assertInteger(
                    2 + 2 * PAST_CACHE_LIMITS,
                    evaluate(
                            "evaluations\n",
                            "i108-argument-evaluation-count.protos",
                            module));

            System.out.println(
                    "I108_ARGUMENT_MUTATION_AND_PIC_RECOVERY=PASS");
        });
    }

    @Test
    void i108PreparedSourceEntryIsFusedForCallZeroAndOne() throws Exception {
        withCore(module -> {
            evaluate("""
                    makeZero: () => { () => { 1 } }
                    makeOne: () => { (value) => { value } }
                    callZero: (target) => { target() }
                    callOne: (target) => { target(7) }
                    """, "i108-prepared-entry-define.protos", module);

            for (String function : List.of("callZero", "callOne")) {
                ProtosBytecodeRootNode root =
                        functionRoot(function, module);
                root.getRootNodes().ensureComplete();

                List<String> names =
                        root.getBytecodeNode().getInstructionsAsList().stream()
                                .map(ProtosInstructionNames::of)
                                .toList();

                // I112: the final call is terminal; its operation owns the
                // fused entry and the fallback, with no caller-side protocol.
                String invocation = function.equals("callZero")
                        ? "c.InvokeClosureZeroTail"
                        : "c.InvokeClosureOneTail";

                assertTrue(
                        names.stream().anyMatch(name -> name.equals(invocation)),
                        names::toString);
                assertFalse(
                        names.stream().anyMatch(
                                name -> name.contains("EnterStraightLineSourceCall")),
                        names::toString);
                assertFalse(
                        names.stream().anyMatch(
                                name -> name.contains("IsPreparedClosureInvocation")),
                        names::toString);
                assertFalse(
                        names.stream().anyMatch(
                                name -> name.contains("AdmitsStraightLineSourceCall")),
                        names::toString);
            }

            assertInteger(
                    1,
                    evaluate(
                            "callZero(makeZero())\n",
                            "i108-prepared-entry-zero.protos",
                            module));
            assertInteger(
                    7,
                    evaluate(
                            "callOne(makeOne())\n",
                            "i108-prepared-entry-one.protos",
                            module));
        });
    }

    @Test
    void i114AdmissionUsesLocalPresenceAndTheRootCertificate() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    """, "i114-admission-define.protos", module);
            ProtosClosureValue first = closure("make()\n", module);
            ProtosClosureValue second = closure("make()\n", module);
            assertNotSame(first, second);
            assertSame(first.definition(), second.definition());
            assertFalse(first.hasLocalCallSlotForRuntime());
            assertTrue(ProtosStandardObjectProtocol.isCanonicalRootCallSelectionForRuntime());

            // The admitted instance is always the current one.
            assertSame(first, InvokeClosureOneTail.admittedInstanceOrNull(first, module));
            assertSame(second, InvokeClosureOneTail.admittedInstanceOrNull(second, module));

            ProtosLanguageContext context = ProtosLanguageContext.current();
            RootCallTarget target =
                    InvokeClosureOneTail.admittedDefinitionTarget(first, context);
            assertNotNull(target);
            assertSame(target, InvokeClosureOneTail.admittedDefinitionTarget(second, context));
            assertSame(
                    first.executionPlan().orElseThrow().bytecodeActivationTargetForComposition(),
                    target);
            assertNull(InvokeClosureOneTail.admittedDefinitionTarget(first, null));

            // A local call on a fresh Closure: removed and re-created.
            module.context().createLocalSlot("local", first);
            evaluate("local.call: (value) => { 100 + value }\n", "i114-local.protos", module);
            assertTrue(first.hasLocalCallSlotForRuntime());
            assertNull(InvokeClosureOneTail.admittedInstanceOrNull(first, module));
            assertSame(second, InvokeClosureOneTail.admittedInstanceOrNull(second, module));
            evaluate("local.removeSlot(\"call\")\n", "i114-local-remove.protos", module);
            assertFalse(first.hasLocalCallSlotForRuntime());
            assertSame(first, InvokeClosureOneTail.admittedInstanceOrNull(first, module));
            evaluate("local.call: (value) => { 300 + value }\n", "i114-local-recreate.protos", module);
            assertNull(InvokeClosureOneTail.admittedInstanceOrNull(first, module));
            evaluate("local.removeSlot(\"call\")\n", "i114-local-remove-again.protos", module);

            // A local non-Closure call, and a local alias of STANDARD_CALL:
            // neither is the canonical root selection.
            Object standardCall =
                    ProtosObjectValue.rootObject().readLocalSlot("call").orElseThrow();
            second.createLocalSlot("call", ProtosNullValue.INSTANCE);
            assertNull(InvokeClosureOneTail.admittedInstanceOrNull(second, module));
            second.assignLocalSlot("call", standardCall);
            assertNull(InvokeClosureOneTail.admittedInstanceOrNull(second, module));
            second.removeLocalSlot("call");
            assertSame(second, InvokeClosureOneTail.admittedInstanceOrNull(second, module));

            // The bootstrapped root Object is frozen: its call selector cannot
            // change, so the certificate stays valid without registered
            // dependencies.
            assertTrue(ProtosObjectValue.rootObject().isFrozen());
            assertTrue(ProtosStandardObjectProtocol.isCanonicalRootCallSelectionForRuntime());
            assertSame(first, InvokeClosureOneTail.admittedInstanceOrNull(first, module));
            assertSame(second, InvokeClosureOneTail.admittedInstanceOrNull(second, module));
        });
    }

    @Test
    void i114ReturnHomeProofNeverConstructsAHomeOrAPlan() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    makeEscaper: () => { (value) => { ^value } }
                    """, "i114-home-define.protos", module);
            ProtosClosureValue prepared = closure("make()\n", module);
            ProtosClosureExecutionPlan plan = prepared.executionPlan().orElseThrow();
            assertEquivalentHomeProof(true, prepared);

            ProtosClosureValue canonicalCaptured = new ProtosClosureValue(
                    prepared.definition(), prepared.capturedLexicalContexts(),
                    prepared.capturedReceiver(), null, ProtosReturnHome.unobservable(),
                    module.prelude().orElseThrow(), plan);
            assertEquivalentHomeProof(true, canonicalCaptured);

            ProtosClosureValue materializedCaptured = new ProtosClosureValue(
                    prepared.definition(), prepared.capturedLexicalContexts(),
                    prepared.capturedReceiver(), null, new ProtosReturnHome(),
                    module.prelude().orElseThrow(), plan);
            assertEquivalentHomeProof(false, materializedCaptured);
            assertNull(InvokeClosureZero.admittedInstanceOrNull(materializedCaptured, module));

            // The ABI-only primitive marker is not the semantic marker.
            ProtosClosureValue primitiveMarker = new ProtosClosureValue(
                    prepared.definition(), prepared.capturedLexicalContexts(),
                    prepared.capturedReceiver(), null,
                    ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime(),
                    module.prelude().orElseThrow(), plan);
            assertEquivalentHomeProof(false, primitiveMarker);

            assertEquivalentHomeProof(false, closure("makeEscaper()\n", module));

            ProtosClosureValue planless = new ProtosClosureValue(
                    prepared.definition(), prepared.capturedLexicalContexts(),
                    prepared.capturedReceiver());
            assertFalse(planless.hasUnobservableInvocationReturnHomeForRuntime());
            assertTrue(planless.executionPlan().isEmpty());

            ProtosClosureValue nativeClosure = assertInstanceOf(
                    ProtosClosureValue.class,
                    ProtosObjectValue.rootObject().readLocalSlot("call").orElseThrow());
            assertFalse(nativeClosure.hasUnobservableInvocationReturnHomeForRuntime());

            // A deferred plan with a local call override: rejected without
            // running the rematerializer.
            int[] rematerializations = {0};
            ProtosPrelude prelude = module.prelude().orElseThrow();
            ProtosClosureValue deferred =
                    prepared.parallelProjectionDeferred(
                            List.of(prelude.newExecutionContext()),
                            ProtosNullValue.INSTANCE,
                            prelude,
                            () -> {
                                rematerializations[0]++;
                                throw new IllegalStateException(
                                        "I114: admission must not prepare a plan");
                            });
            assertFalse(deferred.hasUnobservableInvocationReturnHomeForRuntime());
            deferred.createLocalSlot("call", prepared);
            assertNull(InvokeClosureOneTail.admittedInstanceOrNull(deferred, module));
            assertNull(InvokeClosureZero.admittedInstanceOrNull(deferred, module));
            assertEquals(0, rematerializations[0]);
            assertTrue(deferred.executionPlan().isEmpty());
        });
    }

    @Test
    void i114TerminalDefinitionTierSurvivesOverridesAndRestorations() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    callOne: (f, value) => { f(value) }
                    late: make()
                    """, "i114-pic-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callOne(make(), 7)\n", "i114-pic-warm.protos", module));
            }
            Object entry = singleEntry("callOne", module);
            assertTerminalDirectEntryHasNoIndirectFallback(entry);
            ProtosClosureExecutionPlan plan =
                    (ProtosClosureExecutionPlan) field(entry, "cachedSourcePlan_");
            assertSame(plan.bytecodeActivationTargetForComposition(), field(entry, "cachedTarget_"));
            // No receiver instance is retained by the definition-tier entry.
            for (Class<?> type = entry.getClass(); type != Object.class; type = type.getSuperclass()) {
                for (Field declared : type.getDeclaredFields()) {
                    assertFalse(
                            ProtosClosureValue.class.isAssignableFrom(declared.getType()),
                            declared.toString());
                }
            }

            for (int round = 0; round < 3; round++) {
                // Direct -> local override -> generic -> restoration -> direct.
                evaluate("late.call: (value) => { 100 + value }\n", "i114-pic-override.protos", module);
                assertInteger(107, evaluate("callOne(late, 7)\n", "i114-pic-overridden.protos", module));
                evaluate("late.removeSlot(\"call\")\n", "i114-pic-restore.protos", module);
                assertInteger(7, evaluate("callOne(late, 7)\n", "i114-pic-restored.protos", module));

                assertInteger(7, evaluate("callOne(make(), 7)\n", "i114-pic-fresh.protos", module));
                assertSame(entry, singleEntry("callOne", module),
                        "temporary rejections must not consume or discard the entry");
            }
        });
    }

    /**
     * I114 graph-convergence fixture: the PERF042 reference topology, where
     * each {@code run} invocation materializes a fresh Closure of one
     * definition and calls it terminally. A direct {@code identity(1)} on the
     * locally bound literal is lowered to the inline Closure-literal path
     * ({@code PrepareInlineClosureLiteralCall}), not to {@code
     * InvokeClosureOneTail}; the alias {@code f} keeps the same Closure and
     * definition while reaching the terminal operation under test. The structural assertions prove the installed terminal
     * specialization; the physical After-TruffleTier criteria require a real
     * Graal capture of {@code run}'s root by the human executor.
     */
    @Test
    void i114NormalWorkloadInstallsTheTerminalDirectSpecialization() throws Exception {
        withCore(module -> {
            evaluate("""
                    run: () => {
                        identity: (value) => { value }
                        f: identity
                        f(1)
                    }
                    """, "i114-graph-define.protos", module);
            enterCachedTier("run", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "i114-graph-run.protos", module));
            }
            List<String> names =
                    functionRoot("run", module).getBytecodeNode().getInstructionsAsList().stream()
                            .map(ProtosInstructionNames::of)
                            .toList();
            assertTrue(names.contains("c.InvokeClosureOneTail"), names::toString);
            List<Object> entries =
                    definitionTierEntries("run", InvokeClosureOneTail.class, module);
            assertSingleAdmittedEntry(entries, 1);
            assertTerminalDirectEntryHasNoIndirectFallback(entries.get(0));
        });
    }

    private static void assertEquivalentHomeProof(boolean expected, ProtosClosureValue closure) {
        assertEquals(expected, closure.hasUnobservableInvocationReturnHomeForRuntime());
        assertEquals(
                expected,
                closure.invocationReturnHomeForRuntime() == ProtosReturnHome.unobservable());
    }

    /** I114: the direct specialization caches no indirect fallback call node. */
    private static void assertTerminalDirectEntryHasNoIndirectFallback(Object entry) {
        for (Class<?> type = entry.getClass(); type != Object.class; type = type.getSuperclass()) {
            for (Field declared : type.getDeclaredFields()) {
                assertFalse(
                        com.oracle.truffle.api.nodes.IndirectCallNode.class
                                .isAssignableFrom(declared.getType()),
                        declared.toString());
                assertFalse(declared.getName().startsWith("indirectCall"), declared.toString());
            }
        }
    }

    /**
     * Moves the named module function's root to the cached Bytecode tier on
     * its next entry, so its operation nodes and their caches exist.
     */
    private static void enterCachedTier(String function, ProtosActivation module) {
        functionRoot(function, module).getBytecodeNode().setUncachedThreshold(0);
    }

    private static ProtosBytecodeRootNode functionRoot(
            String function, ProtosActivation module) {
        ProtosClosureValue closure = assertInstanceOf(
                ProtosClosureValue.class,
                module.context().readLocalSlot(function).orElseThrow());
        return closure.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
    }

    /**
     * Structural, test-only view of the generated operation node: the
     * {@code definitionSource} cache entries of every {@code operation} site
     * in the named function's cached Bytecode node.
     */
    private static List<Object> definitionTierEntries(
            String function, Class<?> operation, ProtosActivation module) throws Exception {
        Object bytecode = functionRoot(function, module).getBytecodeNode();
        Node[] cachedNodes = (Node[]) field(bytecode, "cachedNodes_");
        String nodeName = operation.getSimpleName() + "_Node";
        // I112: a final call of the function is the terminal form of the same site.
        String terminalNodeName = operation.getSimpleName() + "Tail_Node";
        List<Object> entries = new ArrayList<>();
        int sites = 0;
        for (Node node : cachedNodes) {
            if (node == null
                    || !(node.getClass().getSimpleName().equals(nodeName)
                            || node.getClass().getSimpleName().equals(terminalNodeName))) {
                continue;
            }
            sites++;
            for (Object entry = field(node, "definitionSource_cache");
                    entry != null;
                    entry = field(entry, "next_")) {
                entries.add(entry);
            }
        }
        assertEquals(1, sites, "expected exactly one " + nodeName + " site in " + function);
        return entries;
    }

    private static Object singleEntry(String function, ProtosActivation module) throws Exception {
        List<Object> entries = definitionTierEntries(function, InvokeClosureOne.class, module);
        assertEquals(1, entries.size());
        return entries.get(0);
    }

    private static void assertSingleAdmittedEntry(List<Object> entries, int arity) throws Exception {
        assertEquals(1, entries.size());
        Object entry = entries.get(0);
        RootCallTarget target = (RootCallTarget) field(entry, "cachedTarget_");
        ProtosClosureExecutionPlan plan =
                (ProtosClosureExecutionPlan) field(entry, "cachedSourcePlan_");
        assertSame(plan.bytecodeActivationTargetForComposition(), target);
        assertTrue(InvokeClosureZero.admittedTarget(target, arity));
        assertNotNull(field(entry, "node_"));
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
                evaluate(characters, "perf042-closure.protos", activation));
    }

    private static void assertInteger(long expected, Object actual) {
        try {
            assertEquals(
                    Long.toString(expected),
                    com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                            .integerDecimalText(actual));
        } catch (IllegalArgumentException invalidType) {
            throw new AssertionError(
                    "Expected Protos Integer " + expected
                            + ", actual class="
                            + (actual == null
                                    ? "null"
                                    : actual.getClass().getName())
                            + ", preparedCall="
                            + (actual instanceof ProtosPreparedCalls.PreparedClosureCall)
                            + ", value=" + String.valueOf(actual),
                    invalidType);
        }
    }
}
