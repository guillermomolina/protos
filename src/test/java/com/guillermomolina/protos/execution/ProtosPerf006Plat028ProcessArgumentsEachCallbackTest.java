/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.guillermomolina.protos.runtime.ProtosProcessArgumentsValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPerf006Plat028ProcessArgumentsEachCallbackTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void processArgumentsEachOwnsSuspendingCallbackLoopInsideCPrime() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosProcessArgumentsValue arguments = arguments(prelude, "one", "two");
            ProtosFutureValue gate =
                    new ProtosFutureValue(prelude.futurePrototype(), domain);
            AtomicInteger callbacks = new AtomicInteger();

            module.context().createLocalSlot("arguments", arguments);
            module.context().createLocalSlot("gate", gate);
            module.context().createLocalSlot(
                    "probe",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                callbacks.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));

            ProtosTask task =
                    execute(
                            domain,
                            module,
                            lowerRoot(
                                    scope.language(),
                                    "arguments.each((argument) => { probe(argument)\ngate.value() })",
                                    "perf006-plat028-process-arguments-each-suspend.protos"));

            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(1, callbacks.get());

            assertTrue(gate.resolve(ProtosNullValue.INSTANCE, module));
            assertTrue(domain.dispatchOne());

            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(arguments, task.result().orElseThrow());
            assertEquals(2, callbacks.get());
        }

        System.out.println("PERF006_PLAT028_PROCESS_ARGUMENTS_EACH_SUSPEND_RESUME=PASS");
        System.out.println("PERF006_PLAT028_PROCESS_ARGUMENTS_EACH_COMPLETED_PREFIX_REPLAY=NO");
        System.out.println("PERF006_PLAT028_PROCESS_ARGUMENTS_EACH_RESULT_RECEIVER_IDENTITY=PASS");
    }

    @Test
    void canonicalRecognitionRejectsCopiedStandardEachAtAnotherHome() throws Exception {
        ProtosPrelude prelude = core();
        ProtosObjectValue canonicalHome =
                ProtosStandardProcessArgumentsProtocol.createPrototype();
        ProtosClosureValue each =
                (ProtosClosureValue) canonicalHome.readLocalSlot("each").orElseThrow();

        ProtosProcessArgumentsValue canonical =
                arguments(prelude, canonicalHome, "one");

        ProtosObjectValue aliasHome =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        aliasHome.createLocalSlot("each", each);
        aliasHome.freeze();
        ProtosProcessArgumentsValue aliased =
                arguments(prelude, aliasHome, "one");

        assertTrue(
                ProtosStandardProcessArgumentsProtocol.isCanonicalStandardEachSelection(
                        each, canonical, canonicalHome));
        assertFalse(
                ProtosStandardProcessArgumentsProtocol.isCanonicalStandardEachSelection(
                        each, canonical, aliasHome));
        assertFalse(
                ProtosStandardProcessArgumentsProtocol.isCanonicalStandardEachSelection(
                        each, aliased, aliasHome));

        System.out.println("PERF006_PLAT028_PROCESS_ARGUMENTS_EACH_EXACT_HOME=PASS");
        System.out.println("PERF006_PLAT028_PROCESS_ARGUMENTS_EACH_COPIED_HOME_PRIVILEGE=NO");
    }

    private static ProtosProcessArgumentsValue arguments(
            ProtosPrelude prelude, String... values) {
        return arguments(
                prelude,
                ProtosStandardProcessArgumentsProtocol.createPrototype(),
                values);
    }

    private static ProtosProcessArgumentsValue arguments(
            ProtosPrelude prelude,
            ProtosObjectValue prototype,
            String... values) {
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.AVAILABLE,
                process.establishArgumentsForRuntime(
                        prototype,
                        List.of(values)));
        return process.argumentsSnapshotForRuntime().orElseThrow();
    }

    private static ProtosTask execute(
            ProtosActorExecutionDomain domain,
            ProtosActivation activation,
            ProtosBytecodeRootNode root) {
        return domain.createTask(
                null,
                current ->
                        ProtosBytecodeTaskExecution.execute(
                                current, root.getCallTarget(), activation));
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
