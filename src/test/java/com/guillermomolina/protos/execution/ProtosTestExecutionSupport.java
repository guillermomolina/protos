/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Test-only bridge for retained Java semantic harnesses that must cross a post-B6B execution
 * boundary.
 *
 * <p>This class does not create a second production entry architecture. Production Process
 * drivers already own a {@link ProtosPolyglotExecutionContext}. Retained Java tests predate that
 * hosting cutover and often construct semantic {@link ProtosActivation}s directly. The shared
 * context below supplies only the physical entered Truffle context/public-parse prerequisite while
 * preserving each test's existing Activation, Actor, execution domain, capabilities, resolver,
 * and semantic state.
 *
 * <p>One context is intentionally shared across the Surefire JVM so a source-backed Closure/Future
 * created by one test operation and resumed by a later test-side dispatch re-enters the same
 * {@link ProtosLanguageContext}. All semantic mutable state remains in the supplied Activation.
 */
public final class ProtosTestExecutionSupport {
    private static final ProtosPolyglotExecutionContext CONTEXT =
            ProtosPolyglotExecutionContext.open(
                    InputStream.nullInputStream(),
                    OutputStream.nullOutputStream(),
                    OutputStream.nullOutputStream());

    static {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        CONTEXT.close();
                                    } catch (RuntimeException | Error ignored) {
                                        // Test JVM teardown must not hide the actual test result.
                                    }
                                },
                                "protos-test-entered-context-close"));
    }

    private ProtosTestExecutionSupport() {}

    public static ProtosExecutionOutcome execute(
            String source, ProtosActivation activation) {
        return execute("<retained-java-harness>", source, activation);
    }

    public static ProtosExecutionOutcome execute(
            String sourceName, String source, ProtosActivation activation) {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(activation, "activation");
        return callEntered(
                () -> {
                    Source exactSource =
                            Source.newBuilder(ProtosLanguage.ID, source, sourceName)
                                    .mimeType(ProtosLanguage.MIME_TYPE)
                                    .build();
                    CallTarget target =
                            ProtosLanguageContext.current().parsePublic(exactSource);
                    return ProtosRootTaskExecution.execute(target, activation);
                });
    }

    public static ProtosExecutionOutcome execute(
            ProtosModuleSource source, ProtosActivation activation) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(activation, "activation");
        return callEntered(
                () -> {
                    ProtosLanguageContext languageContext =
                            ProtosLanguageContext.current();
                    CallTarget target =
                            languageContext.parsePublic(
                                    languageContext.materializeModuleSource(source));
                    return ProtosRootTaskExecution.execute(target, activation);
                });
    }

    public static Object evaluate(
            String source, ProtosActivation activation) {
        return evaluate("<retained-java-harness>", source, activation);
    }

    public static Object evaluate(
            String sourceName, String source, ProtosActivation activation) {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(activation, "activation");
        return callEntered(
                () -> {
                    Source exactSource =
                            Source.newBuilder(ProtosLanguage.ID, source, sourceName)
                                    .mimeType(ProtosLanguage.MIME_TYPE)
                                    .build();
                    CallTarget target =
                            ProtosLanguageContext.current().parsePublic(exactSource);
                    return target.call(activation);
                });
    }

    public static Object evaluateFile(
            Path path, ProtosActivation activation) throws IOException {
        Objects.requireNonNull(path, "path");
        return evaluate(
                path.toString(),
                Files.readString(path, StandardCharsets.UTF_8),
                activation);
    }

    public static Object executeFileValue(
            Path path, ProtosActivation activation) throws IOException {
        Objects.requireNonNull(path, "path");
        ProtosExecutionOutcome outcome =
                execute(
                        path.toString(),
                        Files.readString(path, StandardCharsets.UTF_8),
                        activation);
        return switch (outcome.state()) {
            case COMPLETED -> outcome.value();
            case FAILED -> throw new com.guillermomolina.protos.runtime.ProtosSignalException(
                    outcome.error());
            case CANCELLED -> throw new AssertionError(
                    "retained Java harness RootTask unexpectedly cancelled: " + path);
        };
    }

    public static Object executeFileValueWithFreshModuleActivation(
            Path path, ProtosActivation template) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(template, "template");
        com.guillermomolina.protos.runtime.ProtosPrelude prelude =
                template.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "fresh retained module activation requires an owning Core prelude"));
        com.guillermomolina.protos.runtime.ProtosObjectValue context =
                prelude.newExecutionContext();
        template.context()
                .localSlotsSnapshot()
                .forEach(context::createLocalSlot);
        ProtosActivation fresh =
                prelude.newModuleActivation(
                        template.actorModuleState(),
                        template.currentModuleKey().orElse(null),
                        context,
                        template.executionDomain());
        return executeFileValue(path, fresh);
    }

    public static <T> T callEnteredAndDrain(
            ProtosActivation activation, Supplier<T> action) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(action, "action");
        return callEntered(
                () -> {
                    T result = action.get();
                    activation.executionDomain().dispatchUntilIdle();
                    return result;
                });
    }

    public static void runEnteredAndDrain(
            ProtosActivation activation, Runnable action) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(action, "action");
        callEnteredAndDrain(
                activation,
                () -> {
                    action.run();
                    return null;
                });
    }

    public static <T> T callEntered(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        if (ProtosPolyglotExecutionContext.hasEnteredContextForRuntime()) {
            return action.get();
        }
        return CONTEXT.callEntered(action);
    }

    public static void runEntered(Runnable action) {
        Objects.requireNonNull(action, "action");
        callEntered(
                () -> {
                    action.run();
                    return null;
                });
    }

    public static boolean dispatchOne(ProtosActorExecutionDomain domain) {
        Objects.requireNonNull(domain, "domain");
        return callEntered(domain::dispatchOne);
    }

    public static void dispatchUntilIdle(ProtosActorExecutionDomain domain) {
        Objects.requireNonNull(domain, "domain");
        runEntered(domain::dispatchUntilIdle);
    }
}
