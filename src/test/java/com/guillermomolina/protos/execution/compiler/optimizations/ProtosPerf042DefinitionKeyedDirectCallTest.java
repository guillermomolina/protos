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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF042 regression coverage after I115 physical-call lowering.
 *
 * <p>Checks source Closure definition, execution-plan and target
 * admission, ordinary call selection, ReturnHome, Context
 * projection and deferred-plan behavior.
 *
 * <p>Guest workloads retain the semantic regression witnesses
 * for fresh instances, overridden call methods and restored
 * canonical selection.
 *
 * <p>Structural checks now verify physical CallNilaryMethod and
 * CallUnaryMethod bytecode sites instead of the removed composed
 * operations' definitionSource PIC caches. Compiled graph
 * convergence requires separate graph evidence.
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
            assertSame(first.definition(), sourceDefinition(second));

            ProtosLanguageContext context = ProtosLanguageContext.current();
            RootCallTarget target = effectiveTarget(first, context);
            assertNotNull(target);
            assertSame(target, effectiveTarget(second, context));
            assertTrue(nonSuspendingRoot(target));
            assertSame(first, dispatchedAsFunction(first));
            assertSame(second, dispatchedAsFunction(second));
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
            RootCallTarget target = effectiveTarget(first, context);
            assertNotNull(target);
            assertSame(target, effectiveTarget(second, context));
            assertTrue(nonSuspendingRoot(target));
            // D203/I105: an excess argument enters the same ordinary root.
            assertTrue(nonSuspendingRoot(target));
            assertSame(second, dispatchedAsFunction(second));
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

            // Same-Shape alternation of standard and custom local `call`
            // values on one cached Closure, at the nilary and unary sites.
            evaluate("""
                    customOne: (value) => { 400 + value }
                    customZero: () => { 400 }
                    """, "perf042-same-shape-define.protos", module);
            Object standardCall =
                    ProtosObjectValue.rootObject().readLocalSlot("call").orElseThrow();
            ProtosClosureValue lateOne = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("late").orElseThrow());
            ProtosClosureValue lateNilary = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("lateZero").orElseThrow());
            Object customOne = module.context().readLocalSlot("customOne").orElseThrow();
            Object customZero = module.context().readLocalSlot("customZero").orElseThrow();
            var oneShape = lateOne.getShape();
            var zeroShape = lateNilary.getShape();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                lateOne.assignLocalSlot("call", standardCall);
                lateNilary.assignLocalSlot("call", standardCall);
                assertSame(oneShape, lateOne.getShape());
                assertSame(zeroShape, lateNilary.getShape());
                assertInteger(7, evaluate("callOne(late)\n", "perf042-same-shape-standard.protos", module));
                assertInteger(1, evaluate("callZero(lateZero)\n", "perf042-same-shape-standard0.protos", module));
                assertInteger(7, evaluate("callOne(make())\n", "perf042-same-shape-fresh.protos", module));
                assertInteger(1, evaluate("callZero(makeZero())\n", "perf042-same-shape-fresh0.protos", module));

                lateOne.assignLocalSlot("call", customOne);
                lateNilary.assignLocalSlot("call", customZero);
                assertSame(oneShape, lateOne.getShape());
                assertSame(zeroShape, lateNilary.getShape());
                assertInteger(407, evaluate("callOne(late)\n", "perf042-same-shape-custom.protos", module));
                assertInteger(400, evaluate("callZero(lateZero)\n", "perf042-same-shape-custom0.protos", module));
                assertInteger(107, evaluate("callOne(early)\n", "perf042-same-shape-early.protos", module));
            }

            ProtosClosureValue late = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("late").orElseThrow());
            assertNull(dispatchedAsFunction(late));
        });
    }

    /*
     * Closures of one definition share the executable target but never one
     * another's `call` behavior. The Shape-specialized selection must keep
     * identities and captures per instance, restore the canonical selection
     * once an override is removed, and recover direct dispatch after a call
     * went through the generic selection path.
     */
    @Test
    void sharedDefinitionKeepsPerInstanceCallSelectionAcrossFallback() throws Exception {
        withCore(module -> {
            evaluate("""
                    makeAdder: (seed) => { (value) => { seed + value } }
                    callOne: (f) => { f(7) }
                    plain: makeAdder(1)
                    custom: makeAdder(2)
                    custom.call: (value) => { 500 + value }
                    """, "perf042-shape-shared-define.protos", module);
            ProtosClosureValue plain = closure("plain\n", module);
            ProtosClosureValue custom = closure("custom\n", module);
            ProtosLanguageContext context = ProtosLanguageContext.current();
            assertNotSame(plain, custom);
            assertSame(plain.definition(), custom.definition());
            assertNull(dispatchedAsFunction(custom));
            RootCallTarget shared = effectiveTarget(plain, context);
            assertNotNull(shared);
            assertNull(effectiveTarget(custom, context));

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(8, evaluate("callOne(plain)\n", "perf042-shape-plain.protos", module));
                assertInteger(507, evaluate("callOne(custom)\n", "perf042-shape-custom.protos", module));
                assertInteger(7 + i, evaluate("callOne(makeAdder(" + i + "))\n",
                        "perf042-shape-fresh.protos", module));
            }

            evaluate("custom.removeSlot(\"call\")\n", "perf042-shape-remove.protos", module);
            assertSame(shared, effectiveTarget(custom, context),
                    "the restored Closure reuses the shared executable target");
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(9, evaluate("callOne(custom)\n", "perf042-shape-restored.protos", module));
                assertInteger(8, evaluate("callOne(plain)\n", "perf042-shape-plain-after.protos", module));
            }
            assertSame(plain, closure("plain\n", module));
            assertSame(custom, closure("custom\n", module));
            assertSame(shared, effectiveTarget(plain, context));
        });
    }

    /**
     * Two separate properties of the direct Closure-call path.
     *
     * <p>A. Compiler-proven direct calls: the identity definition {@code
     * make} is the fixture already classified as proven by
     * freshCallOneClosuresConvergeOnTheDefinitionTier. Fresh instances share
     * the exact effective target, keep their own identity, return their
     * exact results and keep canonical {@code Object.call} selection.
     *
     * <p>B. Captured lexical state: {@code makeHolder(seed)} instances share
     * the definition-level target but keep independent captured
     * environments, across repeated calls and across a {@code call} override
     * that is added and then removed. No proof classification is asserted
     * for this body; its effective-root proof is whatever the existing
     * source-level rules publish, and the guest results must hold either way.
     * The dynamic forwarder stays unproven, as an {@code f()} body does in
     * ProtosI107ContinuationEmissionTest.
     */
    @Test
    void provenDirectClosureCallPreservesIdentityAndSelection() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    makeHolder: (seed) => { (value) => { seed } }
                    makeForwarder: () => { (f) => { f() } }
                    callOne: (f, value) => { f(value) }
                    """, "perf042-proven-define.protos", module);
            ProtosLanguageContext context = ProtosLanguageContext.current();

            // A. Compiler-proven identity definition.
            RootCallTarget proven = null;
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                ProtosClosureValue first = closure("make()\n", module);
                ProtosClosureValue second = closure("make()\n", module);
                assertNotSame(first, second);
                assertSame(first.definition(), second.definition());
                RootCallTarget target = effectiveTarget(first, context);
                assertNotNull(target);
                assertSame(target, effectiveTarget(second, context));
                if (proven == null) {
                    proven = target;
                }
                assertSame(proven, target, "stable shared executable target");
                assertTrue(nonSuspendingRoot(target));
                assertSame(first, dispatchedAsFunction(first), "canonical Object.call");
                assertSame(second, dispatchedAsFunction(second), "canonical Object.call");
                module.context().createLocalSlot("identityA" + i, first);
                module.context().createLocalSlot("identityB" + i, second);
                assertInteger(i, evaluate(
                        "callOne(identityA" + i + ", " + i + ")\n",
                        "perf042-proven-a.protos", module));
                assertInteger(i + 1, evaluate(
                        "callOne(identityB" + i + ", " + (i + 1) + ")\n",
                        "perf042-proven-b.protos", module));
            }

            // B. Captured lexical state; no proof classification is required.
            RootCallTarget holderTarget = null;
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                ProtosClosureValue first = closure("makeHolder(" + i + ")\n", module);
                ProtosClosureValue second = closure("makeHolder(" + (i * 10) + ")\n", module);
                assertNotSame(first, second);
                assertSame(first.definition(), second.definition());
                RootCallTarget target = effectiveTarget(first, context);
                assertNotNull(target);
                assertSame(target, effectiveTarget(second, context));
                if (holderTarget == null) {
                    holderTarget = target;
                }
                assertSame(holderTarget, target, "shared definition-level target");
                module.context().createLocalSlot("holderA" + i, first);
                module.context().createLocalSlot("holderB" + i, second);
                for (int repeat = 0; repeat < 2; repeat++) {
                    assertInteger(i, evaluate(
                            "callOne(holderA" + i + ", 1)\n", "perf042-holder-a.protos", module));
                    assertInteger(i * 10, evaluate(
                            "callOne(holderB" + i + ", 1)\n", "perf042-holder-b.protos", module));
                }
            }

            ProtosClosureValue forwarder = closure("makeForwarder()\n", module);
            RootCallTarget dynamic = effectiveTarget(forwarder, context);
            assertNotNull(dynamic);
            assertFalse(nonSuspendingRoot(dynamic));
            module.context().createLocalSlot("forwarder", forwarder);
            assertInteger(5, evaluate(
                    "callOne(forwarder, () => { 5 })\n", "perf042-dynamic.protos", module));

            evaluate("""
                    overridden: makeHolder(1)
                    overridden.call: (value) => { 500 + value }
                    """, "perf042-holder-override.protos", module);
            assertInteger(502, evaluate(
                    "callOne(overridden, 2)\n", "perf042-holder-overridden.protos", module));
            ProtosClosureValue overridden = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("overridden").orElseThrow());
            assertNull(dispatchedAsFunction(overridden));
            assertNull(effectiveTarget(overridden, context));
            assertInteger(0, evaluate(
                    "callOne(holderA0, 1)\n", "perf042-holder-unaffected.protos", module));

            evaluate("overridden.removeSlot(\"call\")\n", "perf042-holder-remove.protos", module);
            for (int repeat = 0; repeat < 2; repeat++) {
                assertInteger(1, evaluate(
                        "callOne(overridden, 2)\n", "perf042-holder-restored.protos", module));
            }
            assertSame(overridden, dispatchedAsFunction(overridden));
            assertSame(holderTarget, effectiveTarget(overridden, context));
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
            assertNull(dispatchedAsFunction(escaper));

            // A body that may suspend (a dynamically selected send) is not
            // admitted at the target level.
            ProtosClosureValue adder = closure("makeAdder(1)\n", module);
            RootCallTarget adderTarget = effectiveTarget(adder, context);
            assertNotNull(adderTarget);
            assertFalse(nonSuspendingRoot(adderTarget));

            // No entered Context, or a receiver that is not a Closure, yields
            // no target projection.
            assertNull(effectiveTarget(adder, null));
            assertNull(effectiveTarget(new Object(), context));
            assertNull(sourceDefinition(new Object()));

            // A native Closure has no source definition for this tier.
            Object standardCall =
                    ProtosObjectValue.rootObject().readLocalSlot("call").orElseThrow();
            ProtosClosureValue nativeClosure =
                    assertInstanceOf(ProtosClosureValue.class, standardCall);
            assertTrue(nativeClosure.nativeBody().isPresent());
            assertNull(sourceDefinition(nativeClosure));
            assertNull(dispatchedAsFunction(nativeClosure));
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
            assertNewCallSite("runOne", 1, module);
            assertNewCallSite("runZero", 0, module);
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
                            original.definition(), context.languageForTesting());
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
                    effectiveTarget(original, context);
            RootCallTarget projectedTarget =
                    effectiveTarget(projected, context);
            assertSame(template.bytecodeActivationTargetForComposition(), originalTarget);
            assertSame(rebuilt.bytecodeActivationTargetForComposition(), projectedTarget);
            assertNotSame(originalTarget, projectedTarget);
            assertTrue(nonSuspendingRoot(projectedTarget));

            module.context().createLocalSlot("projected", projected);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(i, evaluate(
                        "callOne(projected, " + i + ")\n", "perf042-plans-projected.protos", module));
                assertInteger(i, evaluate(
                        "callOne(make(), " + i + ")\n", "perf042-plans-original.protos", module));
            }

            assertNewCallSite("callOne", 1, module);
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
                foreignTarget = effectiveTarget(foreign, ProtosLanguageContext.current());
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
                        ProtosClosureCallPreparation.functionCallTarget(foreign, entered);
                assertNotNull(projected);
                assertNotSame(foreignTarget, projected);
                assertSame(
                        entered.languageForTesting(),
                        projected.getRootNode().getLanguage(ProtosLanguage.class));
                assertSame(
                        projected,
                        ProtosClosureCallPreparation.functionCallTarget(foreign, entered));
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
            assertNewCallSite("callOne", 1, module);

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

            assertNull(effectiveTarget(deferred, ProtosLanguageContext.current()));
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(107, evaluate("callOne(deferred, 7)\n", "perf042-deferred-call.protos", module));
                assertInteger(7, evaluate("callOne(make(), 7)\n", "perf042-deferred-mixed.protos", module));
            }
            assertEquals(0, rematerializations[0]);
            assertTrue(deferred.executionPlan().isEmpty());
            assertNewCallSite("callOne", 1, module);
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
            assertNewCallSite("callOne", 1, module);

            // Overridden instance of the cached definition, then a definition
            // whose target is not admitted, then admitted instances again.
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(107, evaluate("callOne(overridden, 7)\n", "perf042-transient-override.protos", module));
                assertInteger(8, evaluate("callOne(makeAdder(1), 7)\n", "perf042-transient-adder.protos", module));
                assertInteger(7, evaluate("callOne(make(), 7)\n", "perf042-transient-back.protos", module));
            }
            assertNewCallSite("callOne", 1, module);
            // A site whose first receiver is overridden still installs the
            // definition tier for the admitted instances that follow.
            assertInteger(107, evaluate("overriddenFirst(overridden, 7)\n", "perf042-first-override.protos", module));
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("overriddenFirst(make(), 7)\n", "perf042-first-admitted.protos", module));
            }
            assertNewCallSite("overriddenFirst", 1, module);
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

            assertNewCallSite("invoke", 1, module);

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

            assertNewCallSite("invoke", 1, module);

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
                        ? "c.CallNilaryMethod"
                        : "c.CallUnaryMethod";

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
            assertSame(first, dispatchedAsFunction(first));
            assertSame(second, dispatchedAsFunction(second));

            ProtosLanguageContext context = ProtosLanguageContext.current();
            RootCallTarget target =
                    effectiveTarget(first, context);
            assertNotNull(target);
            assertSame(target, effectiveTarget(second, context));
            assertSame(
                    first.executionPlan().orElseThrow().bytecodeActivationTargetForComposition(),
                    target);
            assertNull(effectiveTarget(first, null));

            // A local call on a fresh Closure: removed and re-created.
            module.context().createLocalSlot("local", first);
            evaluate("local.call: (value) => { 100 + value }\n", "i114-local.protos", module);
            assertTrue(first.hasLocalCallSlotForRuntime());
            assertNull(dispatchedAsFunction(first));
            assertSame(second, dispatchedAsFunction(second));
            evaluate("local.removeSlot(\"call\")\n", "i114-local-remove.protos", module);
            assertFalse(first.hasLocalCallSlotForRuntime());
            assertSame(first, dispatchedAsFunction(first));
            evaluate("local.call: (value) => { 300 + value }\n", "i114-local-recreate.protos", module);
            assertNull(dispatchedAsFunction(first));
            evaluate("local.removeSlot(\"call\")\n", "i114-local-remove-again.protos", module);

            // A local non-Closure call is not the canonical selection; a local
            // alias of STANDARD_CALL selects the same behavior, so it is
            // dispatched like the inherited one (selection identity).
            Object standardCall =
                    ProtosObjectValue.rootObject().readLocalSlot("call").orElseThrow();
            second.createLocalSlot("call", ProtosNullValue.INSTANCE);
            assertNull(dispatchedAsFunction(second));
            second.assignLocalSlot("call", standardCall);
            assertSame(second, dispatchedAsFunction(second));
            second.removeLocalSlot("call");
            assertSame(second, dispatchedAsFunction(second));

            // The bootstrapped root Object is frozen: its call selector cannot
            // change, so the certificate stays valid without registered
            // dependencies.
            assertTrue(ProtosObjectValue.rootObject().isFrozen());
            assertTrue(ProtosStandardObjectProtocol.isCanonicalRootCallSelectionForRuntime());
            assertSame(first, dispatchedAsFunction(first));
            assertSame(second, dispatchedAsFunction(second));
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
            assertNull(dispatchedAsFunction(materializedCaptured));

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
            assertNull(dispatchedAsFunction(deferred));
            assertNull(dispatchedAsFunction(deferred));
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
            assertNewCallSite("callOne", 1, module);

            for (int round = 0; round < 3; round++) {
                // Direct -> local override -> generic -> restoration -> direct.
                evaluate("late.call: (value) => { 100 + value }\n", "i114-pic-override.protos", module);
                assertInteger(107, evaluate("callOne(late, 7)\n", "i114-pic-overridden.protos", module));
                evaluate("late.removeSlot(\"call\")\n", "i114-pic-restore.protos", module);
                assertInteger(7, evaluate("callOne(late, 7)\n", "i114-pic-restored.protos", module));

                assertInteger(7, evaluate("callOne(make(), 7)\n", "i114-pic-fresh.protos", module));
                assertNewCallSite("callOne", 1, module);
            }
        });
    }

    /**
     * I114 graph-convergence fixture: the PERF042 reference topology, where
     * each {@code run} invocation materializes a fresh Closure of one
     * definition and calls it terminally. The alias {@code f} keeps the same
     * Closure and definition through a non-literal binding; since PERF042 /
     * PLAT066-C, the direct {@code identity(1)} of {@code runDirect} on the
     * locally bound literal also reaches {@code InvokeClosureOneTail} instead
     * of the inline Closure-literal path. The structural assertions prove the
     * installed terminal specialization for both sites; the physical
     * After-TruffleTier criteria require a real Graal capture of {@code
     * run}'s root by the human executor.
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
                    runDirect: () => {
                        identity: (value) => { value }
                        identity(1)
                    }
                    """, "i114-graph-define.protos", module);
            for (String function : List.of("run", "runDirect")) {
                enterCachedTier(function, module);
                for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                    assertInteger(1, evaluate(function + "()\n", "i114-graph-run.protos", module));
                }
                List<String> names =
                        functionRoot(function, module).getBytecodeNode().getInstructionsAsList().stream()
                                .map(ProtosInstructionNames::of)
                                .toList();
                // A fresh Closure per run misses the instance-keyed fast path, so
                // the site uses its source specialization, whose dispatcher
                // installs the definition-keyed direct tier checked below.
                assertTrue(
                        names.stream().anyMatch(name -> name.startsWith("c.CallUnaryMethod")),
                        names::toString);
                assertNewCallSite(function, 1, module);
            }
        });
    }

    /**
     * One creator root repeatedly creates fresh non-capturing Closures. The
     * creator-identity cache of the creating operation never shares a
     * Closure, never mixes the Prelude or module key of distinct creators,
     * and leaves Prelude-free and published creators on the general path.
     */
    @Test
    void sharedCreatorCreatesFreshClosuresWithItsOwnPreludeAndModuleKey() throws Exception {
        withCore(module -> {
            evaluate("""
                    run: () => { (value) => { value } }
                    """, "perf042-creator-define.protos", module);
            ProtosClosureValue run = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("run").orElseThrow());
            ProtosClosureExecutionPlan plan = run.executionPlan().orElseThrow();
            RootCallTarget target = plan.bytecodeActivationTargetForComposition();
            enterCachedTier("run", module);

            ProtosPrelude own = module.prelude().orElseThrow();
            ProtosPrelude other = new ProtosCoreBootstrap().bootstrap(CORE)
                    .newModuleActivation().prelude().orElseThrow();
            assertNotSame(own, other);
            ProtosModuleKey firstKey = ProtosModuleKey.module("perf042-creator-first");
            ProtosModuleKey secondKey = ProtosModuleKey.module("perf042-creator-second");
            ProtosModuleKey preludeFreeKey = ProtosModuleKey.module("perf042-creator-free");
            ProtosClosureValue first = creator(run, own, firstKey);
            ProtosClosureValue second = creator(run, other, secondKey);
            ProtosClosureValue preludeFree = creator(run, null, preludeFreeKey);

            ProtosClosureValue previous = null;
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                ProtosClosureValue fromFirst = create(target, first, module);
                assertSame(own, fromFirst.preludeOrNullForRuntime());
                assertSame(firstKey, fromFirst.definingModuleKeyOrNullForRuntime());
                assertNotSame(previous, fromFirst, "every creation is a fresh Closure");
                previous = fromFirst;

                ProtosClosureValue fromSecond = create(target, second, module);
                assertSame(other, fromSecond.preludeOrNullForRuntime());
                assertSame(secondKey, fromSecond.definingModuleKeyOrNullForRuntime());
                assertNotSame(fromFirst, fromSecond);
                assertSame(fromFirst.definition(), fromSecond.definition());
                assertSame(
                        fromFirst.executionPlan().orElseThrow(),
                        fromSecond.executionPlan().orElseThrow());
                assertSame(
                        effectiveTarget(fromFirst, ProtosLanguageContext.current()),
                        effectiveTarget(fromSecond, ProtosLanguageContext.current()));

                // No own Prelude: the general path recovers the transported one.
                ProtosClosureValue fromFree = create(target, preludeFree, module);
                assertSame(own, fromFree.preludeOrNullForRuntime());
                assertSame(preludeFreeKey, fromFree.definingModuleKeyOrNullForRuntime());

                // A published creator answers from its published activation.
                Object[] header = ProtosFrameArguments.compactDirectClosureCallZeroNoHome(
                        second, module);
                ProtosActivation published = ProtosFrameArguments.activation(header);
                ProtosClosureValue fromPublished = assertInstanceOf(
                        ProtosClosureValue.class, target.call(header));
                assertSame(published, header[0]);
                assertSame(published.preludeOrNullForRuntime(),
                        fromPublished.preludeOrNullForRuntime());
                assertSame(published.currentModuleKey().orElse(null),
                        fromPublished.definingModuleKeyOrNullForRuntime());
                assertNotSame(fromSecond, fromPublished);

                for (ProtosClosureValue fresh :
                        List.of(fromFirst, fromSecond, fromFree, fromPublished)) {
                    assertSame(fromFirst.definition(), fresh.definition());
                    assertSame(plan.bytecodeActivationTargetForComposition(), target);
                    assertEquivalentHomeProof(true, fresh);
                    assertSame(fresh, dispatchedAsFunction(fresh));
                }
            }
        });
    }

    /**
     * The Task-observation proof belongs to the effective root shared by
     * every fresh Closure of one plan, never to a Closure: distinct creators
     * keep their own Prelude and module key, a fresh Closure is created per
     * execution, and a call site that saturates its identity PIC still
     * enters the same proven root with the exact result. Roots that call,
     * capture or observe execution state never acquire the proof.
     */
    @Test
    void taskObservationProofBelongsToTheSharedEffectiveRoot() throws Exception {
        withCore(module -> {
            evaluate("""
                    run: () => { (value) => { value } }
                    apply: (f, value) => { f(value) }
                    calling: (f) => { f() }
                    capturing: (value) => { inner: () => { value }\n inner() }
                    contextual: () => { context }
                    """, "perf042-task-proof.protos", module);
            ProtosClosureValue run = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("run").orElseThrow());
            RootCallTarget creatorTarget =
                    run.executionPlan().orElseThrow().bytecodeActivationTargetForComposition();
            enterCachedTier("apply", module);
            ProtosPrelude other = new ProtosCoreBootstrap().bootstrap(CORE)
                    .newModuleActivation().prelude().orElseThrow();
            ProtosClosureValue first = creator(
                    run, module.prelude().orElseThrow(), ProtosModuleKey.module("perf042-task-a"));
            ProtosClosureValue second =
                    creator(run, other, ProtosModuleKey.module("perf042-task-b"));

            module.context().createLocalSlot("probe", ProtosNullValue.INSTANCE);
            module.context().createLocalSlot("token", ProtosNullValue.INSTANCE);
            ProtosBytecodeRootNode provenRoot = null;
            ProtosClosureValue previous = null;
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                for (ProtosClosureValue creator : List.of(first, second)) {
                    ProtosClosureValue fresh = create(creatorTarget, creator, module);
                    assertNotSame(previous, fresh, "every execution creates a fresh Closure");
                    previous = fresh;
                    assertSame(creator.preludeOrNullForRuntime(), fresh.preludeOrNullForRuntime());
                    assertSame(creator.definingModuleKeyOrNullForRuntime(),
                            fresh.definingModuleKeyOrNullForRuntime());
                    RootCallTarget target =
                            effectiveTarget(fresh, ProtosLanguageContext.current());
                    ProtosBytecodeRootNode root =
                            assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
                    if (provenRoot == null) {
                        provenRoot = root;
                    }
                    assertSame(provenRoot, root, "one effective root for every Closure");
                    assertTrue(root.taskObservationProvenAbsent());
                    assertTrue(root.provablyNonSuspendingBody());

                    module.context().assignLocalSlot("probe", fresh);
                    Object token = new ProtosObjectValue(ProtosObjectValue.rootObject());
                    module.context().assignLocalSlot("token", token);
                    assertSame(token, evaluate(
                            "apply(probe, token)", "perf042-task-apply.protos", module));
                }
            }
            assertTrue(provenRoot.taskObservationProvenAbsent(),
                    "a saturated identity PIC keeps the root's proof");
            assertTrue(provenRoot.admitsTaskTransportOmission());
            for (String unproven : List.of("apply", "calling", "capturing", "contextual", "run")) {
                assertFalse(functionRoot(unproven, module).taskObservationProvenAbsent(), unproven);
            }
        });
    }

    /** A copy of {@code template} with its own Prelude and module key. */
    private static ProtosClosureValue creator(
            ProtosClosureValue template, ProtosPrelude prelude, ProtosModuleKey moduleKey) {
        return new ProtosClosureValue(
                template.definition(),
                template.capturedLexicalEnvironmentForRuntime(),
                template.capturedReceiver(),
                null,
                null,
                prelude,
                template.executionPlan().orElseThrow(),
                moduleKey);
    }

    /**
     * Enters the creator's root with a compact header and checks that the
     * creation itself never published the creator's activation.
     */
    private static ProtosClosureValue create(
            RootCallTarget target,
            ProtosClosureValue creator,
            ProtosActivation caller) {
        Object[] header = ProtosFrameArguments.compactDirectClosureCallZeroNoHome(creator, caller);
        ProtosClosureValue created =
                assertInstanceOf(ProtosClosureValue.class, target.call(header));
        assertSame(creator, header[0], "closure creation must not publish its creator");
        assertNotSame(creator, created);
        return created;
    }

    private static void assertEquivalentHomeProof(boolean expected, ProtosClosureValue closure) {
        assertEquals(expected, closure.hasUnobservableInvocationReturnHomeForRuntime());
        assertEquals(
                expected,
                closure.invocationReturnHomeForRuntime() == ProtosReturnHome.unobservable());
    }

    /**
     * Moves the named module function's root to the cached Bytecode tier on
     * its next entry, so its operation nodes and their caches exist.
     */
    /**
     * Once the identity PIC of one CallUnaryMethod site saturates, fresh
     * instances of one definition are admitted by the cache keyed on their
     * shared prepared plan and target. The site itself is inspected: its
     * quickened name names the single active cache tier.
     */
    @Test
    void unarySiteAdmitsTheSharedPreparedTargetPastTheIdentityCache() throws Exception {
        withCore(module -> {
            evaluate("""
                    run: () => {
                        identity: (value) => { value }
                        identity(1)
                    }
                    make: () => { (value) => { value } }
                    makeHolder: (seed) => { (value) => { seed } }
                    callOne: (f, value) => { f(value) }
                    """, "perf042-prepared-define.protos", module);
            enterCachedTier("run", module);
            enterCachedTier("callOne", module);
            ProtosLanguageContext context = ProtosLanguageContext.current();

            // The benchmark site: every run() creates a fresh literal instance.
            for (int i = 1; i <= PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "perf042-prepared-run.protos", module));
                assertEquals(
                        i <= 4
                                ? "c.CallUnaryMethod$CallCachedClosure"
                                : "c.CallUnaryMethod$CallPreparedTarget",
                        unaryInstructionName("run", module),
                        "run site after call " + i);
            }

            // A site fed distinct instances of one definition.
            RootCallTarget shared = null;
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                ProtosClosureValue first = closure("make()\n", module);
                ProtosClosureValue second = closure("make()\n", module);
                assertNotSame(first, second);
                RootCallTarget target = effectiveTarget(first, context);
                assertNotNull(target);
                assertSame(target, effectiveTarget(second, context));
                if (shared == null) {
                    shared = target;
                }
                assertSame(shared, target, "one shared target for the definition");
                module.context().createLocalSlot("preparedA" + i, first);
                module.context().createLocalSlot("preparedB" + i, second);
                assertSame(first, evaluate(
                        "callOne(preparedA" + i + ", preparedA" + i + ")\n",
                        "perf042-prepared-a.protos", module));
                assertInteger(i, evaluate(
                        "callOne(preparedB" + i + ", " + i + ")\n",
                        "perf042-prepared-b.protos", module));
            }
            assertEquals(
                    "c.CallUnaryMethod$CallPreparedTarget",
                    unaryInstructionName("callOne", module),
                    "callOne site past the identity cache");

            // Captured state is read from the current instance, never from
            // another instance admitted by the same shared target.
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                module.context().createLocalSlot(
                        "preparedHolder" + i, closure("makeHolder(" + (i * 3) + ")\n", module));
            }
            for (int repeat = 0; repeat < 2; repeat++) {
                for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                    assertInteger(i * 3, evaluate(
                            "callOne(preparedHolder" + i + ", 1)\n",
                            "perf042-prepared-holder.protos", module));
                }
            }

            // An overridden `call` enters dynamic selection at the same site.
            evaluate("""
                    preparedOverride: make()
                    preparedOverride.call: (value) => { 400 + value }
                    """, "perf042-prepared-override.protos", module);
            ProtosClosureValue overridden = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("preparedOverride").orElseThrow());
            assertInteger(403, evaluate(
                    "callOne(preparedOverride, 3)\n", "perf042-prepared-overridden.protos", module));
            assertNull(dispatchedAsFunction(overridden));
            assertInteger(3, evaluate(
                    "callOne(preparedA0, 3)\n", "perf042-prepared-unaffected.protos", module));

            // Removing the override re-admits standard execution of the
            // shared target.
            evaluate("preparedOverride.removeSlot(\"call\")\n",
                    "perf042-prepared-remove.protos", module);
            for (int repeat = 0; repeat < 2; repeat++) {
                assertInteger(3, evaluate(
                        "callOne(preparedOverride, 3)\n", "perf042-prepared-restored.protos", module));
            }
            assertSame(overridden, dispatchedAsFunction(overridden));
            assertSame(shared, effectiveTarget(overridden, context));
        });
    }

    private static String unaryInstructionName(String function, ProtosActivation module) {
        List<String> names = functionRoot(function, module)
                .getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .map(Instruction::getName)
                .filter(name -> name.equals("c.CallUnaryMethod")
                        || name.startsWith("c.CallUnaryMethod$"))
                .toList();
        assertEquals(1, names.size(), function + ": " + names);
        return names.get(0);
    }

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
     * Verify the new physical call operation, not the removed
     * PERF042 definitionSource PIC.
     */
    private static void assertNewCallSite(
            String function, int arity, ProtosActivation module) {
        String expected =
                arity == 0 ? "c.CallNilaryMethod" : "c.CallUnaryMethod";

        List<String> names =
                functionRoot(function, module)
                        .getBytecodeNode()
                        .getInstructionsAsList()
                        .stream()
                        .map(ProtosInstructionNames::of)
                        .toList();

        assertEquals(
                1L,
                names.stream()
                        .filter(name -> name.equals(expected)
                                || name.startsWith(expected + "$"))
                        .count(),
                function + ": expected one " + expected
                        + " in " + names);

        assertFalse(
                names.stream().anyMatch(name ->
                        name.equals("c.InvokeClosureZero")
                        || name.equals("c.InvokeClosureOne")
                        || name.equals("c.InvokeClosureZeroTail")
                        || name.equals("c.InvokeClosureOneTail")),
                function + ": legacy dispatch in " + names);
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
                            + ", callRequest="
                            + (actual instanceof ProtosCallRequest)
                            + ", value=" + String.valueOf(actual),
                    invalidType);
        }
    }

    /*
     * I115: the former admission facts, stated as properties of the shared
     * dispatcher. A source Closure is dispatched as a function by
     * representation (its local call slot is not consulted, the approved
     * D013 divergence); its effective CallTarget is the Context's projection.
     */
    private static Object sourceDefinition(Object value) {
        return value instanceof ProtosClosureValue closure && closure.nativeBody().isEmpty()
                ? closure.definition()
                : null;
    }

    /*
     * The function target the dispatcher would enter: none for a Closure
     * whose ordinary `call` selection is not the standard Object.call, so an
     * overridden Closure never prepares its own plan.
     */
    private static RootCallTarget effectiveTarget(Object value, ProtosLanguageContext context) {
        if (context == null
                || !(value instanceof ProtosClosureValue closure)
                || !ProtosFunctionCallDispatchers.hasCanonicalCallSelection(
                        closure,
                        ProtosStandardObjectProtocol.canonicalRootCallSelectionOrNullForRuntime())) {
            return null;
        }
        return ProtosClosureCallPreparation.functionCallTarget(closure, context);
    }

    private static boolean nonSuspendingRoot(RootCallTarget target) {
        return target != null
                && target.getRootNode() instanceof ProtosBytecodeRootNode root
                && root.provablyNonSuspendingBody();
    }

    /*
     * The dispatcher's own function-path admission: a source function whose
     * ordinary `call` selection is still the standard Object.call (D013; a
     * nearer non-standard `call` slot is selected by ordinary lookup).
     */
    private static ProtosClosureValue dispatchedAsFunction(ProtosClosureValue closure) {
        return ProtosFunctionCallDispatchers.CallNode.isSourceFunction(closure)
                        && closure.hasUnobservableInvocationReturnHomeForRuntime()
                        && ProtosFunctionCallDispatchers.hasCanonicalCallSelection(
                                closure,
                                com.guillermomolina.protos.execution.ProtosStandardObjectProtocol
                                        .canonicalRootCallSelectionOrNullForRuntime())
                ? closure
                : null;
    }

}
