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

package com.guillermomolina.protos.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosModuleResolver;
import com.guillermomolina.protos.execution.ProtosModuleRuntime;
import com.guillermomolina.protos.execution.ProtosSourceFileLoader;
import com.guillermomolina.protos.execution.ProtosStandardActorProtocol;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** LM005-C language-level Group/GroupRef conformance over the closed I011 surface. */
final class ProtosGroupLanguageConformanceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path ROOT = Path.of("protos", "tests", "conformance", "group");
    private static final Path MANIFEST = ROOT.resolve("manifest.tsv");
    private static final Path MODULES = ROOT.resolve("modules");

    @TestFactory
    Stream<DynamicTest> groupLanguageConformanceCases() throws IOException {
        List<Case> cases =
                Files.readAllLines(MANIFEST, StandardCharsets.UTF_8).stream()
                        .filter(line -> !line.isBlank())
                        .filter(line -> !line.stripLeading().startsWith("#"))
                        .map(ProtosGroupLanguageConformanceTest::parseCase)
                        .toList();

        return cases.stream()
                .map(testCase ->
                        DynamicTest.dynamicTest(
                                "group/" + testCase.path(),
                                () -> executeCase(testCase)));
    }

    private static void executeCase(Case testCase) throws Exception {
        Harness harness = Harness.create();
        Path source = ROOT.resolve(testCase.path());

        switch (testCase.expectation()) {
            case "boolean" -> {
                Object result = harness.evaluate(source);
                harness.drainUntilIdle();
                assertEquals(expectedBoolean(testCase.expectedValue()), result);
            }
            case "future-integer" -> {
                ProtosFutureValue future =
                        assertInstanceOf(ProtosFutureValue.class, harness.evaluate(source));
                harness.awaitTerminal(future);
                assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
                ProtosIntegerValue value =
                        assertInstanceOf(
                                ProtosIntegerValue.class,
                                future.resolvedValue().orElseThrow());
                assertEquals(new BigInteger(testCase.expectedValue()), value.value());
                harness.drainUntilIdle();
            }
            case "future-integer-one-of" -> {
                ProtosFutureValue future =
                        assertInstanceOf(ProtosFutureValue.class, harness.evaluate(source));
                harness.awaitTerminal(future);
                assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
                ProtosIntegerValue value =
                        assertInstanceOf(
                                ProtosIntegerValue.class,
                                future.resolvedValue().orElseThrow());
                Set<BigInteger> allowed = parseIntegerSet(testCase.expectedValue());
                assertTrue(
                        allowed.contains(value.value()),
                        "Group routing result must come from one eligible member");
                harness.drainUntilIdle();
            }
            case "future-boolean" -> {
                ProtosFutureValue future =
                        assertInstanceOf(ProtosFutureValue.class, harness.evaluate(source));
                harness.awaitTerminal(future);
                assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
                assertEquals(
                        expectedBoolean(testCase.expectedValue()),
                        future.resolvedValue().orElseThrow());
                harness.drainUntilIdle();
            }
            case "error" -> {
                assertThrows(ProtosSignalException.class, () -> harness.evaluate(source));
                harness.drainUntilIdle();
            }
            default ->
                    throw new IllegalArgumentException(
                            "unsupported Group conformance expectation: "
                                    + testCase.expectation());
        }
    }

    private static ProtosBooleanValue expectedBoolean(String value) {
        return switch (value) {
            case "true" -> ProtosBooleanValue.TRUE;
            case "false" -> ProtosBooleanValue.FALSE;
            default -> throw new IllegalArgumentException(
                    "boolean expectation must be true or false");
        };
    }

    private static Set<BigInteger> parseIntegerSet(String value) {
        LinkedHashSet<BigInteger> result = new LinkedHashSet<>();
        for (String element : value.split(",", -1)) {
            if (element.isBlank()) {
                throw new IllegalArgumentException(
                        "future-integer-one-of requires comma-separated integers");
            }
            result.add(new BigInteger(element));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "future-integer-one-of requires at least one integer");
        }
        return Set.copyOf(result);
    }

    private static Case parseCase(String line) {
        List<String> fields = Arrays.asList(line.split("\t", -1));
        if (fields.size() != 3) {
            throw new IllegalArgumentException(
                    "Group manifest row must have exactly 3 tab-separated fields: " + line);
        }
        return new Case(Path.of(fields.get(0)), fields.get(1), fields.get(2));
    }

    private static final class Harness {
        private final ProtosActivation activation;
        private final ManualExecutor executor;
        private final ProtosSourceFileLoader loader = new ProtosSourceFileLoader();

        private Harness(ProtosActivation activation, ManualExecutor executor) {
            this.activation = activation;
            this.executor = executor;
        }

        private static Harness create() throws Exception {
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
            ManualExecutor executor = new ManualExecutor();
            ProtosModuleResolver resolver = new GroupModuleResolver();
            ProtosObjectValue actorRefPrototype =
                    new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosStandardActorProtocol protocol =
                    new ProtosStandardActorProtocol(
                            new ProtosModuleRuntime(resolver),
                            executor,
                            actorRefPrototype,
                            new ProtosObjectValue(ProtosObjectValue.rootObject()));
            ProtosObjectValue actorObject =
                    protocol.installActorObject(
                            new ProtosObjectValue(ProtosObjectValue.rootObject()));
            protocol.installGroupRefPrototype(
                    new ProtosObjectValue(ProtosObjectValue.rootObject()));

            ProtosProcessRuntime process =
                    new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
            ProtosActor root = process.rootActorForRuntime();
            ProtosObjectValue context = prelude.newExecutionContext();
            context.createLocalSlot("Actor", actorObject);
            ProtosActivation activation =
                    prelude.newModuleActivation(
                            root.moduleState(),
                            new ProtosModuleKey("lm005c:entry"),
                            context,
                            root.executionDomain());
            return new Harness(activation, executor);
        }

        private Object evaluate(Path source) throws IOException {
            return loader.load(source).call(activation);
        }

        private void awaitTerminal(ProtosFutureValue future) {
            int steps = 0;
            while (future.state() == ProtosFutureValue.State.PENDING) {
                if (!driveOne()) {
                    throw new AssertionError(
                            "Group conformance Future is pending with no deterministic runnable work");
                }
                steps++;
                if (steps > 100000) {
                    throw new AssertionError(
                            "Group conformance Future exceeded bounded runner progress");
                }
            }
        }

        private void drainUntilIdle() {
            int steps = 0;
            while (driveOne()) {
                steps++;
                if (steps > 100000) {
                    throw new AssertionError(
                            "Group conformance fixture exceeded bounded idle drain");
                }
            }
        }

        private boolean driveOne() {
            boolean progressed = executor.runOne();
            if (activation.executionDomain().dispatchOne()) {
                progressed = true;
            }
            return progressed;
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable command) {
            pending.addLast(command);
        }

        private boolean runOne() {
            Runnable command;
            synchronized (this) {
                command = pending.pollFirst();
            }
            if (command == null) {
                return false;
            }
            command.run();
            return true;
        }
    }

    private static final class GroupModuleResolver implements ProtosModuleResolver {
        private static final String PREFIX = "lm005c:";
        private final Map<String, Path> modules =
                Map.of("workers", MODULES.resolve("workers.protos"));

        @Override
        public ProtosModuleKey resolve(
                String exactSpecifier, Optional<ProtosModuleKey> importingModule)
                throws IOException {
            if (!modules.containsKey(exactSpecifier)) {
                throw new IOException("unknown LM005-C Group module: " + exactSpecifier);
            }
            return new ProtosModuleKey(PREFIX + exactSpecifier);
        }

        @Override
        public String loadSource(ProtosModuleKey key) throws IOException {
            String canonical = key.canonicalId();
            if (!canonical.startsWith(PREFIX)) {
                throw new IOException("foreign LM005-C Group module key: " + canonical);
            }
            Path source = modules.get(canonical.substring(PREFIX.length()));
            if (source == null) {
                throw new IOException("unknown LM005-C Group module key: " + canonical);
            }
            return Files.readString(source, StandardCharsets.UTF_8);
        }
    }

    private record Case(Path path, String expectation, String expectedValue) {}
}
