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

import com.guillermomolina.protos.execution.ProtosClosureCallPreparation.DirectClosureExecutableFacts;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
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
            DirectClosureExecutableFacts firstFacts =
                    ProtosClosureCallPreparation.establishDirectClosureExecutableFactsOrNull(
                            first, context);
            DirectClosureExecutableFacts secondFacts =
                    ProtosClosureCallPreparation.establishDirectClosureExecutableFactsOrNull(
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
            assertSame(
                    ProtosStandardObjectProtocol.canonicalRootCallAssumptionOrNullForRuntime(),
                    firstFacts.rootCallStability());
            assertTrue(firstFacts.rootCallStability().isValid());

            // Captures and receivers stay per instance.
            assertNotSame(first.capturedLexicalContexts(), second.capturedLexicalContexts());
            assertInteger(11, evaluate("first(10)\n", "i115a-fresh-first.protos", module));
            assertInteger(12, evaluate("second(10)\n", "i115a-fresh-second.protos", module));
        });
    }

    @Test
    void executableFactsRetainNoClosureInstance() {
        for (RecordComponent component : DirectClosureExecutableFacts.class.getRecordComponents()) {
            assertFalse(
                    ProtosObjectValue.class.isAssignableFrom(component.getType()),
                    component.toString());
        }
    }

    @Test
    void selectionWithoutPreludeAdmitsOnlyCanonicalSourceClosures() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115a-select-define.protos", module);
            ProtosClosureValue source = closure("make()\n", module);
            ProtosLanguageContext context = ProtosLanguageContext.current();

            assertSame(source, ProtosClosureCallPreparation
                    .directClosureCallSelectionWithoutPreludeOrNull(source));
            assertSame(source, ProtosClosureCallPreparation
                    .directClosureCallSelectionForPreludeOrNull(source, module.prelude().orElseThrow()));
            assertSame(source, ProtosClosureCallPreparation
                    .directClosureCallSelectionOrNull(source, module));
            assertSame(source, ProtosClosureCallPreparation
                    .directClosureInstanceUnderGuardedRootCallOrNull(source));

            Object standardCall =
                    ProtosObjectValue.rootObject().readLocalSlot("call").orElseThrow();
            ProtosClosureValue nativeClosure =
                    assertInstanceOf(ProtosClosureValue.class, standardCall);
            assertRejected(nativeClosure, context);
            assertRejected(ProtosNullValue.INSTANCE, context);
            assertRejected(module.context(), context);

            source.createLocalSlot("call", ProtosNullValue.INSTANCE);
            assertRejected(source, context);
            source.assignLocalSlot("call", closure("make()\n", module));
            assertRejected(source, context);
            // Even an alias of the canonical behavior is a local selection.
            source.assignLocalSlot("call", standardCall);
            assertRejected(source, context);
            source.removeLocalSlot("call");
            assertSame(source, ProtosClosureCallPreparation
                    .directClosureCallSelectionWithoutPreludeOrNull(source));
            assertNotNull(ProtosClosureCallPreparation
                    .establishDirectClosureExecutableFactsOrNull(source, context));
            assertNull(ProtosClosureCallPreparation
                    .establishDirectClosureExecutableFactsOrNull(source, null));
        });
    }

    @Test
    void warmCallSiteObservesCallOverridesCreatedAfterWarmUp() throws Exception {
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
                assertInteger(107, evaluate("callOne(late, 7)\n", "i115a-warm-overridden.protos", module));
                evaluate("late.removeSlot(\"call\")\n", "i115a-warm-remove.protos", module);
                assertInteger(7, evaluate("callOne(late, 7)\n", "i115a-warm-restored.protos", module));
                evaluate("late.call: (value) => { 200 + value }\n", "i115a-warm-recreate.protos", module);
                assertInteger(207, evaluate("callOne(late, 7)\n", "i115a-warm-recreated.protos", module));
                evaluate("late.removeSlot(\"call\")\n", "i115a-warm-remove-again.protos", module);
                assertInteger(7, evaluate("callOne(make(), 7)\n", "i115a-warm-fresh.protos", module));
            }
        });
    }

    @Test
    void callCreatedWhileEvaluatingTheArgumentIsSelected() throws Exception {
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
                assertInteger(107, evaluate("callWith(late, arm)\n", "i115a-argument-arm.protos", module));
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
                    first.definition(), context.languageForRuntime(), context.executionMode());
            assertNotSame(template, otherTemplate);
            ProtosClosureValue other = new ProtosClosureValue(
                    first.definition(), first.capturedLexicalContexts(), first.capturedReceiver(),
                    null, null, module.prelude().orElseThrow(), otherTemplate);

            DirectClosureExecutableFacts firstFacts =
                    ProtosClosureCallPreparation.establishDirectClosureExecutableFactsOrNull(first, context);
            DirectClosureExecutableFacts otherFacts =
                    ProtosClosureCallPreparation.establishDirectClosureExecutableFactsOrNull(other, context);
            assertSame(firstFacts.definition(), otherFacts.definition());
            assertSame(template.bytecodeActivationTargetForComposition(), firstFacts.target());
            assertSame(otherTemplate.bytecodeActivationTargetForComposition(), otherFacts.target());
            assertNotSame(firstFacts.target(), otherFacts.target());
        });
    }

    @Test
    void foreignContextClosureUsesTheEnteredContextProjection() throws Exception {
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL);
                Harness debug = new Harness(ProtosExecutionMode.DEBUG)) {
            ProtosClosureValue normalClosure = normal.call(() -> normal.closure());
            ProtosClosureValue debugClosure = debug.call(() -> debug.closure());
            assertProjectedInto(debug, normalClosure, ProtosExecutionMode.DEBUG);
            assertProjectedInto(normal, debugClosure, ProtosExecutionMode.NORMAL);

            normal.run(() -> {
                DirectClosureExecutableFacts own =
                        ProtosClosureCallPreparation.establishDirectClosureExecutableFactsOrNull(
                                normalClosure, ProtosLanguageContext.current());
                assertFalse(own.projectionRequired());
                assertSame(own.template(), own.effectivePlan());
                assertEquals(ProtosExecutionMode.NORMAL, own.effectivePlan().bytecodeProfileForRuntime());
            });
        }
    }

    /**
     * NORMAL and DEBUG Contexts of one shared Engine: whether or not they share
     * the language instance, a template of the other profile is projected
     * through the entered Context's cache under the entered profile.
     */
    @Test
    void otherProfileTemplateOfASharedEngineIsProjected() throws Exception {
        try (Engine engine = Engine.create();
                Harness normal = new Harness(ProtosExecutionMode.NORMAL, engine);
                Harness debug = new Harness(ProtosExecutionMode.DEBUG, engine)) {
            ProtosClosureValue normalClosure = normal.call(() -> normal.closure());
            ProtosClosureValue debugClosure = debug.call(() -> debug.closure());
            assertProjectedInto(debug, normalClosure, ProtosExecutionMode.DEBUG);
            assertProjectedInto(normal, debugClosure, ProtosExecutionMode.NORMAL);
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
            assertSame(deferred, ProtosClosureCallPreparation
                    .directClosureCallSelectionWithoutPreludeOrNull(deferred));
            assertNull(ProtosClosureCallPreparation
                    .establishDirectClosureExecutableFactsOrNull(deferred, context));
            assertFalse(deferred.hasUnobservableInvocationReturnHomeForRuntime());
            assertEquals(0, rematerializations[0]);

            ProtosClosureValue overridden = prepared.parallelProjectionDeferred(
                    List.of(prelude.newExecutionContext()), ProtosNullValue.INSTANCE, prelude,
                    () -> {
                        rematerializations[0]++;
                        throw new IllegalStateException("I115-A: admission must not prepare a plan");
                    });
            overridden.createLocalSlot("call", prepared);
            assertNull(ProtosClosureCallPreparation
                    .directClosureCallSelectionWithoutPreludeOrNull(overridden));
            assertNull(ProtosClosureCallPreparation
                    .establishDirectClosureExecutableFactsOrNull(overridden, context));
            assertEquals(0, rematerializations[0]);

            // Lazy rematerialization still happens when an invocation needs it.
            assertSame(plan, deferred.executionPlanForRuntimeInvocation());
            assertEquals(1, rematerializations[0]);
            assertSame(plan, deferred.preparedExecutionPlanOrNullForRuntime());
            DirectClosureExecutableFacts facts =
                    ProtosClosureCallPreparation.establishDirectClosureExecutableFactsOrNull(deferred, context);
            assertNotNull(facts);
            assertSame(plan.bytecodeActivationTargetForComposition(), facts.target());
            assertEquals(1, rematerializations[0]);
        });
    }

    private static void assertProjectedInto(
            Harness destination, ProtosClosureValue foreign, ProtosExecutionMode mode) throws Exception {
        ProtosClosureExecutionPlan template = foreign.preparedExecutionPlanOrNullForRuntime();
        RootCallTarget foreignTarget = template.bytecodeActivationTargetForComposition();
        destination.run(() -> {
            ProtosLanguageContext context = ProtosLanguageContext.current();
            DirectClosureExecutableFacts facts =
                    ProtosClosureCallPreparation.establishDirectClosureExecutableFactsOrNull(foreign, context);
            assertNotNull(facts);
            assertTrue(facts.projectionRequired());
            assertSame(template, facts.template());
            assertNotSame(template, facts.effectivePlan());
            assertSame(facts.effectivePlan(),
                    context.bytecodeExecutionPlanForDefinition(foreign.definition(), template));
            assertSame(context.languageForRuntime(), facts.effectivePlan().language().orElseThrow());
            assertEquals(mode, facts.effectivePlan().bytecodeProfileForRuntime());
            assertNotSame(foreignTarget, facts.target());
            assertSame(facts.effectivePlan().bytecodeActivationTargetForComposition(), facts.target());

            DirectClosureExecutableFacts again =
                    ProtosClosureCallPreparation.establishDirectClosureExecutableFactsOrNull(foreign, context);
            assertSame(facts.effectivePlan(), again.effectivePlan());
            assertSame(facts.target(), again.target());
        });
    }

    private static void assertRejected(Object receiver, ProtosLanguageContext context) {
        assertNull(ProtosClosureCallPreparation.directClosureCallSelectionWithoutPreludeOrNull(receiver));
        assertNull(ProtosClosureCallPreparation.directClosureInstanceUnderGuardedRootCallOrNull(receiver));
        if (receiver instanceof ProtosClosureValue closure) {
            assertNull(ProtosClosureCallPreparation.establishDirectClosureExecutableFactsOrNull(closure, context));
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

            root.createLocalSlot("call", standardCall);
            Assumption a = certificate();
            assertNotNull(a);
            assertTrue(a.isValid());
            assertSame(a, certificate());
            assertSame(sourceShaped,
                    ProtosClosureCallPreparation.directClosureCallSelectionWithoutPreludeOrNull(sourceShaped));

            root.assignLocalSlot("call", ProtosNullValue.INSTANCE);
            assertFalse(a.isValid());
            assertNull(certificate());
            assertFalse(ProtosStandardObjectProtocol.isCanonicalRootCallSelectionForRuntime());
            assertNull(ProtosClosureCallPreparation.directClosureCallSelectionWithoutPreludeOrNull(sourceShaped));

            root.assignLocalSlot("call", standardCall);
            Assumption b = certificate();
            assertNotNull(b);
            assertTrue(b.isValid());
            assertNotSame(a, b);

            root.removeLocalSlot("call");
            assertFalse(b.isValid());
            assertNull(certificate());
            assertNull(ProtosClosureCallPreparation.directClosureCallSelectionWithoutPreludeOrNull(sourceShaped));

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
            assertSame(sourceShaped,
                    ProtosClosureCallPreparation.directClosureCallSelectionWithoutPreludeOrNull(sourceShaped));
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
        private final ProtosExecutionMode mode;
        private final Context context;
        private ProtosPrelude prelude;

        Harness(ProtosExecutionMode mode) {
            this(mode, null);
        }

        Harness(ProtosExecutionMode mode, Engine engine) {
            this.mode = mode;
            Context.Builder builder = Context.newBuilder(ProtosLanguage.ID);
            if (engine != null) {
                builder.engine(engine);
            }
            if (mode == ProtosExecutionMode.DEBUG) {
                builder.allowExperimentalOptions(true).option("protos.Debug", "true");
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
                assertEquals(mode, ProtosLanguageContext.current().executionMode());
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
        assertEquals(
                Long.toString(expected),
                com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(actual));
    }
}
