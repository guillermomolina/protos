/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the LICENSE.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * D212: structured (send-site) and direct (extracted) execution of the
 * standard {@code whileFalse}, {@code untilTrue} and {@code untilFalse}
 * Closure loops agree on activation order, counts and the exact last body
 * result; overrides stay ordinary and validation precedes any activation.
 */
final class ProtosD212StandardLoopPolaritiesTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    /**
     * One loop scenario: the selector, whether its receiver is the body, the
     * scripted condition results, and the expected activation trace (C for
     * a condition activation, B for a body activation).
     */
    private record Scenario(
            String selector,
            boolean postTest,
            List<ProtosBooleanValue> conditions,
            String expectedTrace) {}

    private static final List<Scenario> SCENARIOS =
            List.of(
                    new Scenario(
                            "whileFalse",
                            false,
                            List.of(ProtosBooleanValue.FALSE, ProtosBooleanValue.FALSE, ProtosBooleanValue.TRUE),
                            "CBCBC"),
                    new Scenario(
                            "untilTrue",
                            true,
                            List.of(ProtosBooleanValue.FALSE, ProtosBooleanValue.FALSE, ProtosBooleanValue.TRUE),
                            "BCBCBC"),
                    new Scenario(
                            "untilFalse",
                            true,
                            List.of(ProtosBooleanValue.TRUE, ProtosBooleanValue.TRUE, ProtosBooleanValue.FALSE),
                            "BCBCBC"));

    @Test
    void structuredAndDirectLoopsAgreeOnOrderAndLastBodyResult() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            for (Scenario scenario : SCENARIOS) {
                StringBuilder trace = new StringBuilder();
                List<ProtosObjectValue> bodyResults = new ArrayList<>();
                AtomicInteger conditionIndex = new AtomicInteger();
                ProtosClosureValue condition =
                        nativeClosure(
                                (activation, supplied) -> {
                                    trace.append('C');
                                    return scenario.conditions().get(
                                            conditionIndex.getAndIncrement() % scenario.conditions().size());
                                });
                ProtosClosureValue body =
                        nativeClosure(
                                (activation, supplied) -> {
                                    trace.append('B');
                                    ProtosObjectValue fresh =
                                            new ProtosObjectValue(ProtosObjectValue.rootObject());
                                    bodyResults.add(fresh);
                                    return fresh;
                                });
                ProtosClosureValue receiver = scenario.postTest() ? body : condition;
                ProtosClosureValue argument = scenario.postTest() ? condition : body;

                ProtosActivation module = activation(prelude, domain);
                module.context().createLocalSlot("receiver", receiver);
                module.context().createLocalSlot("argument", argument);
                ProtosTask task =
                        execute(
                                domain,
                                module,
                                lowerRoot(
                                        scope.language(),
                                        "receiver." + scenario.selector() + "(argument)",
                                        "d212-" + scenario.selector() + "-structured.protos"));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state(), scenario.selector());
                assertEquals(scenario.expectedTrace(), trace.toString(), scenario.selector());
                assertSame(bodyResults.get(bodyResults.size() - 1), task.result().orElseThrow());

                trace.setLength(0);
                bodyResults.clear();
                ProtosClosureValue extracted =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                ProtosValueLookup.readMember(receiver, scenario.selector(), prelude)
                                        .orElseThrow());
                // Direct invocation runs outside any Task, on a fresh activation.
                Object direct =
                        ProtosClosureInvoker.invoke(
                                extracted, List.of(argument), activation(prelude, domain));
                assertEquals(scenario.expectedTrace(), trace.toString(), scenario.selector());
                assertSame(bodyResults.get(bodyResults.size() - 1), direct);
            }
        }

        System.out.println("D212_STRUCTURED_DIRECT_LOOP_PARITY=PASS");
    }

    @Test
    void postTestBodySuspensionResumesBeforeTheFirstConditionWithoutReplay()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue bodyGate = new ProtosFutureValue(prelude.futurePrototype(), domain);
            AtomicInteger conditionCalls = new AtomicInteger();
            AtomicInteger bodyPrefix = new AtomicInteger();

            module.context().createLocalSlot("bodyGate", bodyGate);
            module.context().createLocalSlot(
                    "condition",
                    nativeClosure(
                            (activation, supplied) -> {
                                conditionCalls.incrementAndGet();
                                return ProtosBooleanValue.TRUE;
                            }));
            module.context().createLocalSlot(
                    "bodyProbe",
                    nativeClosure(
                            (activation, supplied) -> {
                                bodyPrefix.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "body",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => { bodyProbe()\nbodyGate.value() }",
                            "d212-until-suspend-body.protos"));

            ProtosTask task =
                    execute(
                            domain,
                            module,
                            lowerRoot(
                                    scope.language(),
                                    "body.untilTrue(condition)",
                                    "d212-until-suspend-top.protos"));

            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(0, conditionCalls.get(), "the post-test body runs first");
            assertEquals(1, bodyPrefix.get());

            ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
            assertTrue(bodyGate.resolve(resolved, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(resolved, task.result().orElseThrow());
            assertEquals(1, conditionCalls.get());
            assertEquals(1, bodyPrefix.get(), "completed body prefix must not replay");
        }

        System.out.println("D212_POST_TEST_SUSPENSION_RESUME=PASS");
    }

    @Test
    void invalidConditionAndArgumentFailWithoutFurtherActivation() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            AtomicInteger bodyCalls = new AtomicInteger();

            module.context().createLocalSlot(
                    "body",
                    nativeClosure(
                            (activation, supplied) -> {
                                bodyCalls.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "objectCondition",
                    nativeClosure(
                            (activation, supplied) ->
                                    new ProtosObjectValue(ProtosObjectValue.rootObject())));
            module.context().createLocalSlot(
                    "notClosure",
                    new ProtosObjectValue(ProtosObjectValue.rootObject()));

            assertThrows(
                    ProtosSignalException.class,
                    () ->
                            lowerRoot(
                                            scope.language(),
                                            "body.untilFalse(notClosure)",
                                            "d212-invalid-argument.protos")
                                    .getCallTarget()
                                    .call(module));
            assertEquals(0, bodyCalls.get(), "validation precedes the first body");

            ProtosTask task =
                    execute(
                            domain,
                            module,
                            lowerRoot(
                                    scope.language(),
                                    "body.untilTrue(objectCondition)",
                                    "d212-invalid-condition.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.FAILED, task.state());
            assertInstanceOf(ProtosObjectValue.class, task.failure().orElseThrow());
            assertEquals(1, bodyCalls.get(), "no body after a noncanonical condition");
        }

        System.out.println("D212_VALIDATION_AND_STRICT_CONDITION=PASS");
    }

    @Test
    void identicallyNamedOverrideIsOrdinaryDispatch() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            AtomicInteger argumentCalls = new AtomicInteger();
            ProtosObjectValue overrideResult = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosObjectValue custom = new ProtosObjectValue(ProtosObjectValue.rootObject());
            for (String selector : List.of("whileFalse", "untilTrue", "untilFalse")) {
                custom.createLocalSlot(
                        selector,
                        nativeClosure((activation, supplied) -> overrideResult));
            }
            ProtosClosureValue argument =
                    nativeClosure(
                            (activation, supplied) -> {
                                argumentCalls.incrementAndGet();
                                return ProtosBooleanValue.TRUE;
                            });

            for (String selector : List.of("whileFalse", "untilTrue", "untilFalse")) {
                // Each Task owns its own module activation.
                ProtosActivation module = activation(prelude, domain);
                module.context().createLocalSlot("custom", custom);
                module.context().createLocalSlot("argument", argument);
                ProtosTask task =
                        execute(
                                domain,
                                module,
                                lowerRoot(
                                        scope.language(),
                                        "custom." + selector + "(argument)",
                                        "d212-override-" + selector + ".protos"));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state(), selector);
                assertSame(overrideResult, task.result().orElseThrow(), selector);
            }
            assertEquals(0, argumentCalls.get());
        }

        System.out.println("D212_LOOP_SELECTOR_PET=NO");
    }

    private static ProtosClosureValue nativeClosure(
            com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody body) {
        return ProtosClosureValue.suspensionCapableNativeClosure(body, body);
    }

    private static ProtosClosureValue sourceClosure(
            ProtosLanguage language,
            ProtosActivation creator,
            String characters,
            String sourceName)
            throws Exception {
        CanonicalClosure definition = closureDefinition(characters);
        ProtosClosureExecutionPlan plan =
                ProtosClosureExecutionPlan.bytecode(
                        definition,
                        language,
                        source(characters, sourceName));
        return new ProtosClosureValue(
                definition,
                creator.lexicalContextsForClosureCapture(),
                creator.receiver(),
                creator.methodHome().orElse(null),
                creator.returnHome().orElse(null),
                creator.prelude().orElseThrow(),
                plan);
    }

    private static ProtosTask execute(
            ProtosActorExecutionDomain domain,
            ProtosActivation activation,
            ProtosBytecodeRootNode root) {
        return domain.createTask(
                null,
                current ->
                        ProtosBytecodeTaskExecution.execute(
                                current,
                                root.getCallTarget(),
                                activation));
    }

    private static LanguageScope languageScope() {
        Context context = Context.newBuilder(ProtosLanguage.ID).build();
        context.initialize(ProtosLanguage.ID);
        context.enter();
        return new LanguageScope(context, LANGUAGE_REF.get(null));
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private static ProtosActivation activation(
            ProtosPrelude prelude,
            ProtosActorExecutionDomain domain) {
        return prelude.newModuleActivation(
                new ProtosActorModuleState(),
                null,
                prelude.newExecutionContext(),
                domain);
    }

    private static CanonicalClosure closureDefinition(String characters) {
        CanonicalSequence sequence = canonicalize(characters);
        assertEquals(1, sequence.expressions().size());
        return assertInstanceOf(CanonicalClosure.class, sequence.expressions().get(0));
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer()
                        .canonicalize(new ProtosParser(characters).parseProgram());
    }

    private static Source source(String characters, String name) throws Exception {
        return Source.newBuilder(ProtosLanguage.ID, characters, name).build();
    }

    private static ProtosBytecodeRootNode lowerRoot(
            ProtosLanguage language,
            String characters,
            String sourceName)
            throws Exception {
        Source source = source(characters, sourceName);
        return new CanonicalToBytecodeLowerer(language, source)
                .lowerRoot(canonicalize(characters));
    }

    private record LanguageScope(Context context, ProtosLanguage language)
            implements AutoCloseable {
        @Override
        public void close() {
            context.leave();
            context.close();
        }
    }
}
