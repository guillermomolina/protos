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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ProtosTestToolGroupFullCorpusTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path GROUP_ROOT =
            Path.of("protos", "tests", "conformance", "group");
    private static final Path GROUP_MODULES = GROUP_ROOT.resolve("modules");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-g3-group-full-corpus.protos");

    @Test
    void bundledRunnerOwnsCompleteRetainedGroupCorpusWithoutSelectingRoutingMember()
            throws Exception {
        ProtosModuleResolver standardLibraryResolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude testPrelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosBundledToolModuleResolver(
                                        "test",
                                        TOOL_ROOT,
                                        standardLibraryResolver));
        ProtosPrelude groupPrelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosExactModuleOverlayResolver(
                                        Map.of(
                                                "workers",
                                                new ProtosExactModuleOverlayResolver.ExactModule(
                                                        new ProtosModuleKey(
                                                                "tool002-group:workers"),
                                                        GROUP_MODULES.resolve(
                                                                "workers.protos"))),
                                        standardLibraryResolver));
        ProtosActivation activation = testPrelude.newModuleActivation();
        ProtosExactExecutionFacility.install(
                activation, "groupExecution", groupPrelude);
        ProtosExactExecutionFacility.installInspection(
                activation, "groupExecutionInspect", groupPrelude);

        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(GROUP_ROOT)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            ProtosObjectValue rawFilesystem =
                    ProtosStandardFilesystemProtocol.createCapability(
                            testPrelude.bytesPrototypeForRuntime(),
                            activation,
                            backend);
            ProtosFilesystemValue filesystem =
                    assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
            activation.context().createLocalSlot("groupFilesystem", filesystem);

            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(
                            new ProtosSourceCompiler()
                                    .compile(
                                            Files.readString(
                                                    FIXTURE,
                                                    StandardCharsets.UTF_8)),
                            activation);

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "G3 Group full-corpus fixture failed; error=" + outcome.error());
            if (outcome.value() instanceof ProtosStringValue failedCase) {
                fail("Group corpus case failed: " + failedCase.value());
            }
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
        }
    }
}
