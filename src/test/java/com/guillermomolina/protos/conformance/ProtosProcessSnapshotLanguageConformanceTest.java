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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosSourceFileLoader;
import com.guillermomolina.protos.execution.ProtosStandardEnvironmentProtocol;
import com.guillermomolina.protos.execution.ProtosStandardProcessArgumentsProtocol;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Language-level Process bootstrap-snapshot conformance.
 *
 * <p>Java provisions deterministic host bootstrap state only. Observable Process,
 * argument-snapshot, and Environment assertions live in the .protos cases.
 */
final class ProtosProcessSnapshotLanguageConformanceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path ROOT =
            Path.of("protos", "tests", "conformance", "process");
    private static final Path MANIFEST = ROOT.resolve("manifest.tsv");

    @TestFactory
    Stream<DynamicTest> processSnapshotLanguageConformanceCases() throws IOException {
        List<Case> cases =
                Files.readAllLines(MANIFEST, StandardCharsets.UTF_8).stream()
                        .filter(line -> !line.isBlank())
                        .filter(line -> !line.stripLeading().startsWith("#"))
                        .map(ProtosProcessSnapshotLanguageConformanceTest::parseCase)
                        .toList();

        return cases.stream()
                .map(testCase ->
                        DynamicTest.dynamicTest(
                                "process/" + testCase.path(),
                                () -> executeCase(testCase)));
    }

    private static void executeCase(Case testCase) throws Exception {
        Harness harness = Harness.create();
        Path source = ROOT.resolve(testCase.path());

        switch (testCase.expectation()) {
            case "boolean" -> {
                Object result = harness.evaluate(source);
                ProtosBooleanValue expected =
                        switch (testCase.expectedValue()) {
                            case "true" -> ProtosBooleanValue.TRUE;
                            case "false" -> ProtosBooleanValue.FALSE;
                            default -> throw new IllegalArgumentException(
                                    "boolean expectation must be true or false");
                        };
                assertEquals(expected, result);
            }
            case "error" ->
                    assertThrows(
                            ProtosSignalException.class,
                            () -> harness.evaluate(source));
            default ->
                    throw new IllegalArgumentException(
                            "unsupported Process snapshot conformance expectation: "
                                    + testCase.expectation());
        }
    }

    private static Case parseCase(String line) {
        List<String> fields = Arrays.asList(line.split("\\t", -1));
        if (fields.size() != 3) {
            throw new IllegalArgumentException(
                    "Process snapshot manifest row must have exactly 3 tab-separated fields: "
                            + line);
        }
        return new Case(
                Path.of(fields.get(0)), fields.get(1), fields.get(2));
    }

    private static final class Harness {
        private final ProtosActivation activation;
        private final ProtosSourceFileLoader loader = new ProtosSourceFileLoader();

        private Harness(ProtosActivation activation) {
            this.activation = activation;
        }

        private static Harness create() throws Exception {
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
            ProtosObjectValue argumentsPrototype =
                    ProtosStandardProcessArgumentsProtocol.createPrototype();
            ProtosObjectValue environmentPrototype =
                    ProtosStandardEnvironmentProtocol.createPrototype();

            List<String> arguments = List.of("alpha", "\u03b2", "\ud83d\ude00");
            String bmp = "\ue000";
            String supplementary = new String(Character.toChars(0x10000));
            List<ProtosEnvironmentValue.NativeEntry> environment =
                    List.of(
                            new ProtosEnvironmentValue.NativeEntry(
                                    supplementary, "supplementary"),
                            new ProtosEnvironmentValue.NativeEntry("A", "first"),
                            new ProtosEnvironmentValue.NativeEntry(bmp, "middle"));

            ProtosProcessRuntime primary =
                    process(
                            prelude,
                            argumentsPrototype,
                            environmentPrototype,
                            arguments,
                            environment);
            ProtosProcessRuntime other =
                    process(
                            prelude,
                            argumentsPrototype,
                            environmentPrototype,
                            arguments,
                            environment);
            ProtosProcessRuntime empty =
                    process(
                            prelude,
                            argumentsPrototype,
                            environmentPrototype,
                            List.of(),
                            List.of());

            ProtosActivation activation = prelude.newModuleActivation();
            activation.context()
                    .createLocalSlot(
                            "process",
                            primary.provisionCapabilityForRuntime(
                                    prelude.processPrototype()));
            activation.context()
                    .createLocalSlot(
                            "otherProcess",
                            other.provisionCapabilityForRuntime(
                                    prelude.processPrototype()));
            activation.context()
                    .createLocalSlot(
                            "emptyProcess",
                            empty.provisionCapabilityForRuntime(
                                    prelude.processPrototype()));
            return new Harness(activation);
        }

        private static ProtosProcessRuntime process(
                ProtosPrelude prelude,
                ProtosObjectValue argumentsPrototype,
                ProtosObjectValue environmentPrototype,
                List<String> arguments,
                List<ProtosEnvironmentValue.NativeEntry> environment) {
            ProtosProcessRuntime process =
                    new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());

            if (process.establishArgumentsForRuntime(argumentsPrototype, arguments)
                    != ProtosProcessRuntime.ArgumentsSnapshotState.AVAILABLE) {
                throw new IllegalStateException(
                        "Process snapshot fixture arguments were not representable");
            }
            if (process.establishEnvironmentForRuntime(
                            environmentPrototype, exactEnvironmentDomain(), environment)
                    != ProtosProcessRuntime.EnvironmentSnapshotState.AVAILABLE) {
                throw new IllegalStateException(
                        "Process snapshot fixture environment was not representable");
            }
            return process;
        }

        private static ProtosEnvironmentValue.NativeNameDomain exactEnvironmentDomain() {
            return new ProtosEnvironmentValue.NativeNameDomain() {
                @Override
                public boolean sameCapturedName(String left, String right) {
                    return left.equals(right);
                }

                @Override
                public boolean isQueryRepresentable(String name) {
                    return !name.contains("=") && name.indexOf('\0') < 0;
                }

                @Override
                public boolean matchesQuery(String captured, String query) {
                    return captured.equals(query);
                }
            };
        }

        private Object evaluate(Path source) throws IOException {
            return loader.load(source).call(activation);
        }
    }

    private record Case(Path path, String expectation, String expectedValue) {}
}
