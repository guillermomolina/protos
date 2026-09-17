/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ProtosCanonicalInitialModuleExecutionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final ProtosModuleKey MAIN = new ProtosModuleKey("test:Main");

    @Test
    void cachesBootstrapContextBeforeExecutingCanonicalEntry() throws Exception {
        ProtosModuleResolver resolver =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String exactSpecifier, Optional<ProtosModuleKey> importingModule) {
                        if (!exactSpecifier.equals("self")) {
                            throw new IllegalArgumentException();
                        }
                        return MAIN;
                    }

                    @Override
                    public ProtosModuleSource loadSource(ProtosModuleKey key) {
                        return ProtosModuleSource.fromCharacters(
                                key,
                                "marker: Object()\n"
                                        + "Again: import(\"self\")\n"
                                        + "Again.marker === marker\n");
                    }
                };
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        try (ProtosHostedExecutionTestFixture hosted =
                ProtosHostedExecutionTestFixture.open(prelude)) {
            ProtosExecutionOutcome outcome =
                    ProtosCanonicalInitialModuleExecution.execute(
                            prelude, resolver, MAIN, hosted.activation());

            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
            ProtosActorModuleState.ModuleRecord record =
                    hosted.activation().actorModuleState().lookup(MAIN).orElseThrow();
            assertEquals(ProtosActorModuleState.InitializationState.READY, record.state());
            assertSame(hosted.activation().context(), record.instance());
        }
    }

    @Test
    void unhostedInitialModuleExecutesThroughBytecodeBoundary() throws Exception {
        ProtosModuleResolver resolver =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String exactSpecifier,
                            Optional<ProtosModuleKey> importingModule) {
                        return MAIN;
                    }

                    @Override
                    public ProtosModuleSource loadSource(ProtosModuleKey key) {
                        return ProtosModuleSource.fromCharacters(
                                key,
                                "true\n");
                    }
                };
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        var activation = prelude.newModuleActivation();

        ProtosExecutionOutcome outcome =
                ProtosCanonicalInitialModuleExecution.execute(
                        prelude,
                        resolver,
                        MAIN,
                        activation);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
        ProtosActorModuleState.ModuleRecord record =
                activation.actorModuleState().lookup(MAIN).orElseThrow();
        assertEquals(ProtosActorModuleState.InitializationState.READY, record.state());
        assertSame(activation.context(), record.instance());
    }

    @Test
    void failedInitialModuleIsRemovedFromCache() throws Exception {
        ProtosModuleResolver resolver =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String exactSpecifier, Optional<ProtosModuleKey> importingModule) {
                        return MAIN;
                    }

                    @Override
                    public ProtosModuleSource loadSource(ProtosModuleKey key) {
                        return ProtosModuleSource.fromCharacters(key, "Error().signal()\n");
                    }
                };
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        try (ProtosHostedExecutionTestFixture hosted =
                ProtosHostedExecutionTestFixture.open(prelude)) {
            ProtosExecutionOutcome outcome =
                    ProtosCanonicalInitialModuleExecution.execute(
                            prelude, resolver, MAIN, hosted.activation());

            assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
            assertTrue(hosted.activation().actorModuleState().lookup(MAIN).isEmpty());
        }
    }
}
