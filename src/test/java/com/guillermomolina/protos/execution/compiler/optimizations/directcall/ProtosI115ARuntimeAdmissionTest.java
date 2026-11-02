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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

/**
 * I115-A foundations for direct Closure admission and execution: the
 * Prelude-free {@code call} selection, the reusable canonical root
 * {@code Object.call} assumption, and the per-definition executable facts a
 * later direct-call cache entry installs. These are structural assertions on
 * real guest-materialized Closures plus guest call sites whose results prove
 * the generic path still observes {@code call} overrides; nothing here
 * measures time or depends on compilation.
 */
final class ProtosI115ARuntimeAdmissionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final int PAST_CACHE_LIMITS = 12;

    /**
     * I115: one call site through the shared dispatcher's three tiers
     * (FunctionCachedCallNode): a repeated Closure (identity), fresh Closures
     * of one definition (code root, each with its own captures) and distinct
     * definitions past the cache limits (megamorphic, IndirectCallNode). The
     * selected CallTarget belongs to the entered Context.
     */
    @Test
    void sharedDispatcherPicKeepsResultsAcrossIdentityRootAndMegamorphicTiers()
            throws Exception {
        withCore(module -> {
            evaluate("""
                    callOne: (f, value) => { f(value) }
                    make: (seed) => { (value) => { seed + value } }
                    same: make(1)
                    """, "i115-pic-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(11, evaluate("callOne(same, 10)\n", "i115-pic-identity.protos", module));
            }
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(10 + i,
                        evaluate("callOne(make(" + i + "), 10)\n", "i115-pic-root.protos", module));
            }
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1 + i,
                        evaluate("callOne((value) => { value + " + i + " }, 1)\n",
                                "i115-pic-megamorphic.protos", module));
            }
            assertInteger(11, evaluate("callOne(same, 10)\n", "i115-pic-after.protos", module));

            ProtosLanguageContext context = ProtosLanguageContext.current();
            RootCallTarget target =
                    ProtosClosureCallPreparation.functionCallTarget(closure("same\n", module), context);
            assertNotNull(target);
            assertSame(
                    context.languageForRuntime(),
                    target.getRootNode().getLanguage(ProtosLanguage.class),
                    "the CallTarget belongs to the entered Context");
            assertTrue(closure("same\n", module).codeStableAssumptionForRuntime().isValid());
        });
    }

    @Test
    void freshClosuresOfOneDefinitionShareExecutableFactsButNotCaptures() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: (seed) => { (value) => { seed + value } }
                    first: make(1)
                    second: make(2)
                    """, "i115a-fresh-define.protos", module);
            ProtosClosureValue first = closure("first\n", module);
            ProtosClosureValue second = closure("second\n", module);
            assertNotSame(first, second);
            assertSame(first.definition(), second.definition());

            ProtosClosureExecutionPlan template = first.preparedExecutionPlanOrNullForRuntime();
            assertNotNull(template);
            assertSame(template, second.preparedExecutionPlanOrNullForRuntime());
            assertSame(first.definition(), template.definitionForRuntime());

            ProtosLanguageContext context = ProtosLanguageContext.current();
            DispatchFacts firstFacts =
                    dispatchFacts(
                            first, context);
            DispatchFacts secondFacts =
                    dispatchFacts(
                            second, context);
            assertNotNull(firstFacts);
            assertNotNull(secondFacts);
            assertSame(first.definition(), firstFacts.definition());
            assertSame(template, firstFacts.template());
            assertSame(template, firstFacts.effectivePlan());
            assertSame(context, firstFacts.enteredContext());
            assertFalse(firstFacts.projectionRequired());
            assertSame(template.bytecodeActivationTargetForComposition(), firstFacts.target());
            assertSame(firstFacts.target(), secondFacts.target());
            // I115: the dispatcher's function path does not depend on the root
            // Object.call certificate (approved D013 divergence).

            // Captures and receivers stay per instance.
            assertNotSame(first.capturedLexicalContexts(), second.capturedLexicalContexts());
            assertInteger(11, evaluate("first(10)\n", "i115a-fresh-first.protos", module));
            assertInteger(12, evaluate("second(10)\n", "i115a-fresh-second.protos", module));
        });
    }

    @Test
    void localCallAbsenceCertificateInvalidatesAndRestores()
            throws Exception {
        withCore(module -> {
            evaluate(
                    "make: () => { (value) => { value } }\n",
                    "i115c-certificate-definition.protos",
                    module);

            ProtosClosureValue first = closure("make()\n", module);
            ProtosClosureValue second = closure("make()\n", module);

            assertSame(first.definition(), second.definition());

            Assumption firstCertificate =
                    first.localCallAbsentSelectionAssumptionOrNullForRuntime();
            Assumption secondCertificate =
                    second.localCallAbsentSelectionAssumptionOrNullForRuntime();

            assertNotNull(firstCertificate);
            assertNotNull(secondCertificate);
            assertTrue(firstCertificate.isValid());
            assertTrue(secondCertificate.isValid());

            first.createLocalSlot("call", ProtosNullValue.INSTANCE);

            assertFalse(firstCertificate.isValid());
            assertTrue(secondCertificate.isValid());
            assertNull(
                    first.localCallAbsentSelectionAssumptionOrNullForRuntime());

            first.assignLocalSlot(
                    "call",
                    closure("make()\n", module));

            assertNull(
                    first.localCallAbsentSelectionAssumptionOrNullForRuntime());

            first.removeLocalSlot("call");

            Assumption restored =
                    first.localCallAbsentSelectionAssumptionOrNullForRuntime();

            assertNotNull(restored);
            assertTrue(restored.isValid());
            assertNotSame(firstCertificate, restored);

            second.createLocalSlot("call", ProtosNullValue.INSTANCE);

            assertFalse(secondCertificate.isValid());
            assertTrue(restored.isValid());

            first.createLocalSlot("call", ProtosNullValue.INSTANCE);
            assertFalse(restored.isValid());
        });
    }


    @Test
    void projectionCertificateInvalidatesOnTransition()
            throws Exception {
        withCore(module -> {
            evaluate(
                    "make: () => { (value) => { value } }\n",
                    "i115c-projection-definition.protos",
                    module);

            ProtosClosureValue first = closure("make()\n", module);
            ProtosClosureValue second = closure("make()\n", module);

            assertFalse(
                    first.requiresContextLocalExecutionProjectionForRuntime());

            Assumption firstCertificate =
                    first.contextProjectionStabilityForRuntime();
            Assumption secondCertificate =
                    second.contextProjectionStabilityForRuntime();

            assertTrue(firstCertificate.isValid());
            assertTrue(secondCertificate.isValid());

            first.requireContextLocalExecutionProjectionForRuntime();

            assertFalse(firstCertificate.isValid());
            assertTrue(secondCertificate.isValid());
            assertTrue(
                    first.requiresContextLocalExecutionProjectionForRuntime());

            Assumption restored =
                    first.contextProjectionStabilityForRuntime();

            assertNotSame(firstCertificate, restored);
            assertTrue(restored.isValid());

            first.requireContextLocalExecutionProjectionForRuntime();

            assertTrue(restored.isValid());
            assertTrue(secondCertificate.isValid());
        });
    }

    @Test
    void homeCertificateRejectsObservableCapturedHome()
            throws Exception {
        withCore(module -> {
            evaluate(
                    "make: () => { (value) => { value } }\n",
                    "i115c-home-definition.protos",
                    module);

            ProtosClosureValue ordinary = closure("make()\n", module);
            ProtosClosureExecutionPlan template =
                    ordinary.preparedExecutionPlanOrNullForRuntime();

            ProtosLanguageContext context =
                    ProtosLanguageContext.current();

            DispatchFacts facts =
                    dispatchFacts(
                                    ordinary, context);

            assertNotNull(facts);
            assertTrue(
                    stableUnobservableHome(
                            ordinary, facts));

            ProtosReturnHome home = new ProtosReturnHome();

            ProtosClosureValue captured =
                    new ProtosClosureValue(
                            ordinary.definition(),
                            ordinary.capturedLexicalContexts(),
                            ordinary.capturedReceiver(),
                            null,
                            home,
                            module.prelude().orElseThrow(),
                            template);

            assertSame(
                    home,
                    captured.invocationReturnHomeForRuntime());

            assertFalse(
                    stableUnobservableHome(
                            captured, facts));
        });
    }


    @Test
    void definitionTierHomeEligibilityIsPerInstance()
            throws Exception {
        withCore(module -> {
            evaluate(
                    "make: () => { (value) => { value } }\n",
                    "i115c-definition-home.protos",
                    module);

            ProtosClosureValue ordinary =
                    closure("make()\n", module);

            ProtosClosureExecutionPlan template =
                    ordinary.preparedExecutionPlanOrNullForRuntime();

            assertNotNull(template);
            assertTrue(
                    template.returnHomeProvenUnobservableForRuntime());

            // A Closure without a captured ReturnHome relies
            // on the immutable template proof.
            ProtosClosureValue owning =
                    new ProtosClosureValue(
                            ordinary.definition(),
                            ordinary.capturedLexicalContexts(),
                            ordinary.capturedReceiver(),
                            null,
                            null,
                            module.prelude().orElseThrow(),
                            template);

            assertTrue(
                    owning.hasUnobservableReturnHomeUnderTemplateProofForRuntime(
                            true));

            assertFalse(
                    owning.hasUnobservableReturnHomeUnderTemplateProofForRuntime(
                            false));

            // A real captured ReturnHome cannot be replaced by
            // the unobservable marker, regardless of the plan.
            ProtosReturnHome realHome = new ProtosReturnHome();

            ProtosClosureValue observable =
                    new ProtosClosureValue(
                            ordinary.definition(),
                            ordinary.capturedLexicalContexts(),
                            ordinary.capturedReceiver(),
                            null,
                            realHome,
                            module.prelude().orElseThrow(),
                            template);

            assertSame(
                    realHome,
                    observable.invocationReturnHomeForRuntime());

            assertFalse(
                    observable.hasUnobservableReturnHomeUnderTemplateProofForRuntime(
                            true));

            assertFalse(
                    observable.hasUnobservableReturnHomeUnderTemplateProofForRuntime(
                            false));

            // A Closure capturing the explicit unobservable
            // marker does not need the template proof.
            ProtosClosureValue marker =
                    new ProtosClosureValue(
                            ordinary.definition(),
                            ordinary.capturedLexicalContexts(),
                            ordinary.capturedReceiver(),
                            null,
                            ProtosReturnHome.unobservable(),
                            module.prelude().orElseThrow(),
                            template);

            assertTrue(
                    marker.hasUnobservableReturnHomeUnderTemplateProofForRuntime(
                            true));

            assertTrue(
                    marker.hasUnobservableReturnHomeUnderTemplateProofForRuntime(
                            false));

            // All three Closures share the executable template.
            assertSame(
                    template,
                    owning.preparedExecutionPlanOrNullForRuntime());

            assertSame(
                    template,
                    observable.preparedExecutionPlanOrNullForRuntime());

            assertSame(
                    template,
                    marker.preparedExecutionPlanOrNullForRuntime());
            // Definition-tier admission must be established
            // before caching the executable target.
            assertTrue(
                    templateHomeAdmitted(
                            owning, template));
            assertFalse(
                    templateHomeAdmitted(
                            observable, template));
            assertTrue(
                    templateHomeAdmitted(
                            marker, template));
            assertFalse(
                    templateHomeAdmitted(
                            owning, null));

        });
    }

    @Test
    void sourceFunctionSelectionIgnoresLocalCallOverrides() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115a-select-define.protos", module);
            ProtosClosureValue source = closure("make()\n", module);
            ProtosLanguageContext context = ProtosLanguageContext.current();

            assertSame(source, functionSelection(source));
            assertSame(source, functionSelection(source));

            Object standardCall =
                    ProtosObjectValue.rootObject().readLocalSlot("call").orElseThrow();
            ProtosClosureValue nativeClosure =
                    assertInstanceOf(ProtosClosureValue.class, standardCall);
            assertRejected(nativeClosure, context);
            assertRejected(ProtosNullValue.INSTANCE, context);
            assertRejected(module.context(), context);

            source.createLocalSlot("call", ProtosNullValue.INSTANCE);
            assertTrue(source.hasLocalCallSlotForRuntime());
            assertSame(source, functionSelection(source));
            assertNotNull(dispatchFacts(source, context));
            source.assignLocalSlot("call", closure("make()\n", module));
            assertTrue(source.hasLocalCallSlotForRuntime());
            assertSame(source, functionSelection(source));
            assertNotNull(dispatchFacts(source, context));
            // Even a local canonical alias does not redirect source-function calls.
            source.assignLocalSlot("call", standardCall);
            assertTrue(source.hasLocalCallSlotForRuntime());
            assertSame(source, functionSelection(source));
            assertNotNull(dispatchFacts(source, context));
            source.removeLocalSlot("call");
            assertFalse(source.hasLocalCallSlotForRuntime());
            assertSame(source, functionSelection(source));
            assertNotNull(dispatchFacts(source, context));
            assertNull(dispatchFacts(source, null));
        });
    }

    @Test
    void warmCallSiteKeepsDirectSourceDispatchAcrossCallOverrides() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    callOne: (f, value) => { f(value) }
                    late: make()
                    """, "i115a-warm-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callOne(make(), 7)\n", "i115a-warm.protos", module));
            }
            for (int round = 0; round < 2; round++) {
                evaluate("late.call: (value) => { 100 + value }\n", "i115a-warm-override.protos", module);
                assertInteger(7, evaluate("callOne(late, 7)\n", "i115a-warm-overridden.protos", module));
                assertInteger(107, evaluate("late.call(7)\n", "i115a-explicit-overridden.protos", module));
                evaluate("late.removeSlot(\"call\")\n", "i115a-warm-remove.protos", module);
                assertInteger(7, evaluate("callOne(late, 7)\n", "i115a-warm-restored.protos", module));
                evaluate("late.call: (value) => { 200 + value }\n", "i115a-warm-recreate.protos", module);
                assertInteger(7, evaluate("callOne(late, 7)\n", "i115a-warm-recreated.protos", module));
                assertInteger(207, evaluate("late.call(7)\n", "i115a-explicit-recreated.protos", module));
                evaluate("late.removeSlot(\"call\")\n", "i115a-warm-remove-again.protos", module);
                assertInteger(7, evaluate("callOne(make(), 7)\n", "i115a-warm-fresh.protos", module));
            }
        });
    }

    @Test
    void callCreatedWhileEvaluatingArgumentKeepsDirectFunctionSelection() throws Exception {
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
                    """, "i115a-argument-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callWith(make(), plain)\n", "i115a-argument-warm.protos", module));
            }
            for (int round = 0; round < 2; round++) {
                assertInteger(7, evaluate("callWith(late, arm)\n", "i115a-argument-arm.protos", module));
                assertInteger(107, evaluate("late.call(7)\n", "i115a-argument-explicit.protos", module));
                evaluate("late.removeSlot(\"call\")\n", "i115a-argument-remove.protos", module);
                assertInteger(7, evaluate("callWith(late, plain)\n", "i115a-argument-plain.protos", module));
            }
        });
    }

    @Test
    void validRootCertificateIsReusedAndTheFrozenRootRejectsMutation() throws Exception {
        withCore(module -> {
            Assumption first = ProtosStandardObjectProtocol.canonicalRootCallAssumptionOrNullForRuntime();
            assertNotNull(first);
            assertTrue(first.isValid());
            Object certificate = canonicalRootCallCertificate();
            assertNotNull(certificate);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertSame(first, ProtosStandardObjectProtocol.canonicalRootCallAssumptionOrNullForRuntime());
                assertTrue(ProtosStandardObjectProtocol.isCanonicalRootCallSelectionForRuntime());
                assertSame(certificate, canonicalRootCallCertificate());
            }

            ProtosObjectValue root = ProtosObjectValue.rootObject();
            assertTrue(root.isFrozen());
            Object standardCall = root.readLocalSlot("call").orElseThrow();
            assertThrows(IllegalStateException.class,
                    () -> root.assignLocalSlot("call", ProtosNullValue.INSTANCE));
            assertThrows(IllegalStateException.class, () -> root.removeLocalSlot("call"));
            assertSame(standardCall, root.readLocalSlot("call").orElseThrow());
            assertTrue(first.isValid());
        });
    }

    /**
     * The bootstrapped root is frozen and shared by the whole suite, so root
     * {@code call} mutations run against a separate copy of the Protos
     * classes (and therefore a separate, still-open root singleton) loaded by
     * an isolated class loader.
     */
    @Test
    void rootCallCertificateFollowsRootMutationsInAnIsolatedBootstrap() throws Throwable {
        URL[] locations = {
            ProtosObjectValue.class.getProtectionDomain().getCodeSource().getLocation(),
            ProtosI115ARuntimeAdmissionTest.class.getProtectionDomain().getCodeSource().getLocation()
        };
        try (ProtosFirstClassLoader loader = new ProtosFirstClassLoader(
                locations, ProtosI115ARuntimeAdmissionTest.class.getClassLoader())) {
            Class<?> scenario = Class.forName(IsolatedRootCallScenario.class.getName(), true, loader);
            assertNotSame(IsolatedRootCallScenario.class, scenario);
            Method run = scenario.getDeclaredMethod("run");
            run.setAccessible(true);
            try {
                run.invoke(null);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

    @Test
    void distinctTemplatesOfOneDefinitionKeepDistinctTargets() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115a-templates-define.protos", module);
            ProtosClosureValue first = closure("make()\n", module);
            ProtosLanguageContext context = ProtosLanguageContext.current();
            ProtosClosureExecutionPlan template = first.preparedExecutionPlanOrNullForRuntime();
            ProtosClosureExecutionPlan otherTemplate = template.rebuildBytecodeForLanguage(
                    first.definition(), context.languageForRuntime());
            assertNotSame(template, otherTemplate);
            ProtosClosureValue other = new ProtosClosureValue(
                    first.definition(), first.capturedLexicalContexts(), first.capturedReceiver(),
                    null, null, module.prelude().orElseThrow(), otherTemplate);

            DispatchFacts firstFacts =
                    dispatchFacts(first, context);
            DispatchFacts otherFacts =
                    dispatchFacts(other, context);
            assertSame(firstFacts.definition(), otherFacts.definition());
            assertSame(template.bytecodeActivationTargetForComposition(), firstFacts.target());
            assertSame(otherTemplate.bytecodeActivationTargetForComposition(), otherFacts.target());
            assertNotSame(firstFacts.target(), otherFacts.target());
        });
    }

    @Test
    void foreignContextClosureUsesTheEnteredContextProjection() throws Exception {
        try (Harness first = new Harness(null);
                Harness second = new Harness(null)) {
            ProtosClosureValue firstClosure = first.call(() -> first.closure());
            ProtosClosureValue secondClosure = second.call(() -> second.closure());
            assertProjectedInto(second, firstClosure);
            assertProjectedInto(first, secondClosure);

            first.run(() -> {
                DispatchFacts own =
                        dispatchFacts(
                                firstClosure, ProtosLanguageContext.current());
                assertFalse(own.projectionRequired());
                assertSame(own.template(), own.effectivePlan());
                assertSame(
                        ProtosLanguageContext.current().languageForRuntime(),
                        own.effectivePlan().language().orElseThrow());
            });
        }
    }

    /**
     * Two Contexts of one shared Engine each own their language instance, so a
     * template of the other Context is projected through the entered
     * Context's cache. PLAT067-B: no compilation profile is involved.
     */
    @Test
    void otherContextTemplateOfASharedEngineIsProjected() throws Exception {
        try (Engine engine = Engine.create();
                Harness first = new Harness(engine);
                Harness second = new Harness(engine)) {
            ProtosClosureValue firstClosure = first.call(() -> first.closure());
            ProtosClosureValue secondClosure = second.call(() -> second.closure());
            assertProjectedInto(second, firstClosure);
            assertProjectedInto(first, secondClosure);
        }
    }

    @Test
    void deferredPlanIsNeverMaterializedByAdmission() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115a-deferred-define.protos", module);
            ProtosClosureValue prepared = closure("make()\n", module);
            ProtosClosureExecutionPlan plan = prepared.preparedExecutionPlanOrNullForRuntime();
            ProtosPrelude prelude = module.prelude().orElseThrow();
            ProtosLanguageContext context = ProtosLanguageContext.current();
            int[] rematerializations = {0};

            ProtosClosureValue deferred = prepared.parallelProjectionDeferred(
                    List.of(prelude.newExecutionContext()), ProtosNullValue.INSTANCE, prelude,
                    () -> {
                        rematerializations[0]++;
                        return plan;
                    });
            assertNull(deferred.preparedExecutionPlanOrNullForRuntime());
            assertSame(deferred, functionSelection(deferred));
            assertNull(dispatchFacts(deferred, context));
            assertFalse(deferred.hasUnobservableInvocationReturnHomeForRuntime());
            assertEquals(0, rematerializations[0]);

            ProtosClosureValue overridden = prepared.parallelProjectionDeferred(
                    List.of(prelude.newExecutionContext()), ProtosNullValue.INSTANCE, prelude,
                    () -> {
                        rematerializations[0]++;
                        throw new IllegalStateException("I115-A: admission must not prepare a plan");
                    });
            overridden.createLocalSlot("call", prepared);
            // I115: source functions bypass local call selection.
            // The deferred plan must still remain unmaterialized.
            assertSame(overridden, functionSelection(overridden));
            assertNull(dispatchFacts(overridden, context));
            assertEquals(0, rematerializations[0]);

            // Lazy rematerialization still happens when an invocation needs it.
            assertSame(plan, deferred.executionPlanForRuntimeInvocation());
            assertEquals(1, rematerializations[0]);
            assertSame(plan, deferred.preparedExecutionPlanOrNullForRuntime());
            DispatchFacts facts =
                    dispatchFacts(deferred, context);
            assertNotNull(facts);
            assertSame(plan.bytecodeActivationTargetForComposition(), facts.target());
            assertEquals(1, rematerializations[0]);
        });
    }

    private static void assertProjectedInto(
            Harness destination, ProtosClosureValue foreign) throws Exception {
        ProtosClosureExecutionPlan template = foreign.preparedExecutionPlanOrNullForRuntime();
        RootCallTarget foreignTarget = template.bytecodeActivationTargetForComposition();
        destination.run(() -> {
            ProtosLanguageContext context = ProtosLanguageContext.current();
            DispatchFacts facts =
                    dispatchFacts(foreign, context);
            assertNotNull(facts);
            assertTrue(facts.projectionRequired());
            assertSame(template, facts.template());
            assertNotSame(template, facts.effectivePlan());
            assertSame(facts.effectivePlan(),
                    context.bytecodeExecutionPlanForDefinition(foreign.definition(), template));
            assertSame(context.languageForRuntime(), facts.effectivePlan().language().orElseThrow());
            assertNotSame(foreignTarget, facts.target());
            assertSame(facts.effectivePlan().bytecodeActivationTargetForComposition(), facts.target());

            DispatchFacts again =
                    dispatchFacts(foreign, context);
            assertSame(facts.effectivePlan(), again.effectivePlan());
            assertSame(facts.target(), again.target());
        });
    }

    private static void assertRejected(Object receiver, ProtosLanguageContext context) {
        assertNull(functionSelection(receiver));
        assertNull(functionSelection(receiver));
        if (receiver instanceof ProtosClosureValue closure) {
            assertNull(dispatchFacts(closure, context));
        }
    }

    private static Object canonicalRootCallCertificate() throws ReflectiveOperationException {
        Field field = ProtosStandardObjectProtocol.class.getDeclaredField("canonicalRootCall");
        field.setAccessible(true);
        return field.get(null);
    }

    /**
     * Runs inside {@link ProtosFirstClassLoader}: every Protos class it names
     * is that loader's private copy, whose root Object has not been
     * bootstrapped or frozen.
     */
    public static final class IsolatedRootCallScenario {
        private IsolatedRootCallScenario() {}

        public static void run() throws Exception {
            ProtosObjectValue root = ProtosObjectValue.rootObject();
            assertFalse(root.isFrozen());
            assertFalse(root.hasLocalSlot("call"));
            assertNull(certificate());

            Field field = ProtosStandardObjectProtocol.class.getDeclaredField("STANDARD_CALL");
            field.setAccessible(true);
            Object standardCall = field.get(null);
            ProtosClosureValue sourceShaped =
                    new ProtosClosureValue(null, List.of(), ProtosNullValue.INSTANCE);
            // This fixture has no source definition: it is never a
            // source function, regardless of the root call certificate.
            assertNull(functionSelection(sourceShaped));

            root.createLocalSlot("call", standardCall);
            Assumption a = certificate();
            assertNotNull(a);
            assertTrue(a.isValid());
            assertSame(a, certificate());
            assertNull(functionSelection(sourceShaped));

            root.assignLocalSlot("call", ProtosNullValue.INSTANCE);
            assertFalse(a.isValid());
            assertNull(certificate());
            assertFalse(ProtosStandardObjectProtocol.isCanonicalRootCallSelectionForRuntime());
            assertNull(functionSelection(sourceShaped));

            root.assignLocalSlot("call", standardCall);
            Assumption b = certificate();
            assertNotNull(b);
            assertTrue(b.isValid());
            assertNotSame(a, b);

            root.removeLocalSlot("call");
            assertFalse(b.isValid());
            assertNull(certificate());
            assertNull(functionSelection(sourceShaped));

            root.createLocalSlot("call", standardCall);
            Assumption c = certificate();
            assertNotNull(c);
            assertTrue(c.isValid());
            assertNotSame(a, c);
            assertNotSame(b, c);
            assertFalse(a.isValid());
            assertFalse(b.isValid());

            // Assigning even the identical value invalidates value dependencies.
            root.assignLocalSlot("call", standardCall);
            assertFalse(c.isValid());
            Assumption d = certificate();
            assertNotNull(d);
            assertTrue(d.isValid());
            assertNotSame(c, d);
            assertNull(functionSelection(sourceShaped));
        }

        private static Assumption certificate() {
            return ProtosStandardObjectProtocol.canonicalRootCallAssumptionOrNullForRuntime();
        }
    }

    /** Child-first for Protos classes only; platform and test libraries stay shared. */
    private static final class ProtosFirstClassLoader extends URLClassLoader {
        private static final String PROTOS_PREFIX = "com.guillermomolina.protos.";

        ProtosFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PROTOS_PREFIX)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface Producer<T> {
        T get() throws Exception;
    }

    /** One raw Polyglot Context with a published Core, entered only while an action runs. */
    private static final class Harness implements AutoCloseable {
        private final Context context;
        private ProtosPrelude prelude;

        /** A Context of {@code engine}, or of its own Engine when {@code engine} is null. */
        Harness(Engine engine) {
            Context.Builder builder = Context.newBuilder(ProtosLanguage.ID);
            if (engine != null) {
                builder.engine(engine);
            }
            this.context = builder.build();
            context.initialize(ProtosLanguage.ID);
        }

        void run(Action action) throws Exception {
            call(() -> {
                action.run();
                return null;
            });
        }

        <T> T call(Producer<T> action) throws Exception {
            context.enter();
            try {
                if (prelude == null) {
                    prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                }
                return action.get();
            } finally {
                context.leave();
            }
        }

        /** Must run entered. */
        ProtosClosureValue closure() {
            return ProtosI115ARuntimeAdmissionTest.closure(
                    "(value) => { value }\n", prelude.newModuleActivation());
        }

        @Override
        public void close() {
            context.close();
        }
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
                evaluate(characters, "i115a-closure.protos", activation));
    }

    private static void assertInteger(long expected, Object actual) {
        // Compact Truffle roots may return an unboxed JVM integer.
        if (actual instanceof Long number) {
            assertEquals(expected, number.longValue());
            return;
        }
        if (actual instanceof Integer number) {
            assertEquals(expected, number.longValue());
            return;
        }

        // Preserve exact semantic Integer checking for guest values.
        assertEquals(
                Long.toString(expected),
                com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                        .integerDecimalText(actual));
    }

    /*
     * I115: the facts the shared dispatcher's function path uses for one
     * source Closure in the entered Context (ProtosFunctionCallDispatchers):
     * the effective plan and CallTarget, never the Closure instance.
     */
    private record DispatchFacts(
            com.guillermomolina.protos.semantic.ast.CanonicalClosure definition,
            ProtosClosureExecutionPlan template,
            ProtosClosureExecutionPlan effectivePlan,
            ProtosLanguageContext enteredContext,
            boolean projectionRequired,
            RootCallTarget target) {}

    private static DispatchFacts dispatchFacts(
            ProtosClosureValue closure, ProtosLanguageContext context) {
        if (context == null
                || !ProtosFunctionCallDispatchers.CallNode.isSourceFunction(closure)) {
            return null;
        }

        // Admission inspection must not materialize a deferred execution plan.
        ProtosClosureExecutionPlan template =
                closure.preparedExecutionPlanOrNullForRuntime();
        if (template == null) {
            return null;
        }

        RootCallTarget target = ProtosClosureCallPreparation.functionCallTarget(closure, context);
        if (target == null) {
            return null;
        }
        boolean projectionRequired =
                closure.requiresContextLocalExecutionProjectionForRuntime()
                        || template.language().orElseThrow() != context.languageForRuntime();
        ProtosClosureExecutionPlan effective =
                projectionRequired
                        ? context.bytecodeExecutionPlanForDefinition(closure.definition(), template)
                        : template;
        return new DispatchFacts(
                closure.definition(), template, effective, context, projectionRequired, target);
    }

    /** The function-path selection: a source Closure by representation only. */
    private static Object functionSelection(Object value) {
        return value instanceof ProtosClosureValue closure
                        && ProtosFunctionCallDispatchers.CallNode.isSourceFunction(closure)
                ? closure
                : null;
    }

    private static boolean templateHomeAdmitted(
            ProtosClosureValue closure, ProtosClosureExecutionPlan template) {
        return closure != null
                && template != null
                && closure.hasUnobservableReturnHomeUnderTemplateProofForRuntime(
                        template.returnHomeProvenUnobservableForRuntime());
    }

    private static boolean stableUnobservableHome(ProtosClosureValue closure, DispatchFacts facts) {
        return closure != null
                && facts != null
                && closure.preparedExecutionPlanOrNullForRuntime() == facts.template()
                && closure.hasUnobservableInvocationReturnHomeForRuntime();
    }

}
