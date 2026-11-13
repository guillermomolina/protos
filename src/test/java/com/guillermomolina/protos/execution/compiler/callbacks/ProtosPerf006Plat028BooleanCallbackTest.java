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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPerf006Plat028BooleanCallbackTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void selectedIfTrueIfFalseCallbackSuspendsAndResumesInsideCPrime() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue future = new ProtosFutureValue(prelude.futurePrototype(), domain);
            AtomicInteger prefixCalls = new AtomicInteger();

            module.context().createLocalSlot("future", future);
            module.context().createLocalSlot(
                    "probe",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                prefixCalls.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "body",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => { probe()\nfuture.value() }",
                            "perf006-plat028-boolean-selected-body.protos"));

            ProtosTask task =
                    execute(
                            domain,
                            module,
                            lowerRoot(
                                    scope.language(),
                                    "true.ifTrueIfFalse(body, 99)",
                                    "perf006-plat028-boolean-selected-top.protos"));

            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(1, prefixCalls.get());

            Long result = Long.valueOf(42);
            assertTrue(future.resolve(result, module));
            assertEquals(ProtosTask.State.RUNNABLE, task.state());
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(result, task.result().orElseThrow());
            assertEquals(1, prefixCalls.get(), "selected callback prefix must not replay");
        }

        System.out.println("PERF006_PLAT028_BOOLEAN_SELECTED_CALLBACK_CPRIME=PASS");
        System.out.println("PERF006_PLAT028_BOOLEAN_UNSELECTED_NONCALLABLE=PASS");
        System.out.println("PERF006_PLAT028_BOOLEAN_CALLBACK_PREFIX_REPLAY=NO");
    }

    @Test
    void ordinarySelectedConditionalsSuspendWithoutReplayAndReturnExactObjects() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            for (String expression : List.of(
                    "true.ifTrue(...[body])",
                    "false.ifFalse(...[body])",
                    "true.ifTrueIfFalse(...[body, 99])",
                    "false.ifTrueIfFalse(...[99, body])",
                    "selected: true.ifTrue\nselected(body)")) {
                ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                ProtosActivation module = activation(prelude, domain);
                ProtosFutureValue lower = new ProtosFutureValue(prelude.futurePrototype(), domain);
                AtomicInteger prefix = new AtomicInteger();
                module.context().createLocalSlot("future", lower);
                module.context().createLocalSlot("probe", ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            prefix.incrementAndGet();
                            return ProtosNullValue.INSTANCE;
                        }));
                module.context().createLocalSlot("body", sourceClosure(
                        scope.language(), module, "() => { probe()\nfuture.value() }",
                        "ordinary-conditional-callback.protos"));
                ProtosTask task = execute(domain, module, lowerRoot(
                        scope.language(), expression, "ordinary-conditional.protos"));

                assertTrue(domain.dispatchOne(), expression);
                assertEquals(ProtosTask.State.SUSPENDED, task.state(), expression);
                assertEquals(1, prefix.get(), expression);
                ProtosObjectValue exact = new ProtosObjectValue(ProtosObjectValue.rootObject());
                assertTrue(lower.resolve(exact, module));
                assertTrue(domain.dispatchOne(), expression);
                assertEquals(ProtosTask.State.COMPLETED, task.state(), expression);
                assertSame(exact, task.result().orElseThrow(), expression);
                assertEquals(1, prefix.get(), "callback prefix must not replay: " + expression);
            }
        }
    }

    @Test
    void ordinaryConditionalReturnsAPendingFutureWithoutObservingIt() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            for (String expression : List.of(
                    "true.ifTrue(...[() => { returned }])",
                    "false.ifFalse(...[() => { returned }])",
                    "true.ifTrueIfFalse(...[() => { returned }, 99])",
                    "false.ifTrueIfFalse(...[99, () => { returned }])")) {
                ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                ProtosActivation module = activation(prelude, domain);
                ProtosFutureValue returned = new ProtosFutureValue(prelude.futurePrototype(), domain);
                module.context().createLocalSlot("returned", returned);
                assertSame(returned, ProtosTestExecutionSupport.evaluate(expression, module), expression);
                assertEquals(ProtosFutureValue.State.PENDING, returned.state(), expression);
            }
        }
    }

    @Test
    void ordinaryUnselectedConditionalsDoNotInvokeTheirArgument() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            for (String expression : List.of(
                    "false.ifTrue(...[99])", "true.ifFalse(...[99])")) {
                ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                ProtosActivation module = activation(prelude, domain);
                ProtosTask task = execute(domain, module, lowerRoot(
                        scope.language(), expression, "ordinary-unselected-conditional.protos"));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state(), expression);
                assertSame(ProtosNullValue.INSTANCE, task.result().orElseThrow(), expression);
            }
        }
    }

    @Test
    void andCallbackSuspendsAndValidatesBooleanAfterResume() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue future = new ProtosFutureValue(prelude.futurePrototype(), domain);

            module.context().createLocalSlot("future", future);
            module.context().createLocalSlot(
                    "body",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => future.value()",
                            "perf006-plat028-boolean-and-body.protos"));

            ProtosTask task =
                    execute(
                            domain,
                            module,
                            lowerRoot(
                                    scope.language(),
                                    "true.and(body)",
                                    "perf006-plat028-boolean-and-top.protos"));

            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());

            assertTrue(future.resolve(Boolean.TRUE, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(Boolean.TRUE, task.result().orElseThrow());
        }

        System.out.println("PERF006_PLAT028_BOOLEAN_AND_SUSPEND_RESUME=PASS");
        System.out.println("PERF006_PLAT028_BOOLEAN_AND_POSTCALL_VALIDATION=PASS");
    }

    @Test
    void shortCircuitDoesNotRequireCallbackToBeInvokable() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);

            ProtosTask ifTrueTask =
                    execute(
                            domain,
                            module,
                            lowerRoot(
                                    scope.language(),
                                    "false.ifTrue(99)",
                                    "perf006-plat028-boolean-short-iftrue.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, ifTrueTask.state());
            assertSame(ProtosNullValue.INSTANCE, ifTrueTask.result().orElseThrow());

            ProtosActivation secondModule = activation(prelude, domain);
            ProtosTask orTask =
                    execute(
                            domain,
                            secondModule,
                            lowerRoot(
                                    scope.language(),
                                    "true.or(99)",
                                    "perf006-plat028-boolean-short-or.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, orTask.state());
            assertSame(Boolean.TRUE, orTask.result().orElseThrow());
        }

        System.out.println("PERF006_PLAT028_BOOLEAN_SHORT_CIRCUIT_SELECTED_ONLY=PASS");
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
        return (CanonicalClosure) sequence.expressions().get(0);
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
