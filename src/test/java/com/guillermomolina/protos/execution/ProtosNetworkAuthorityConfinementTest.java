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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Retained I028-B4 end-to-end authority-confinement evidence. */
final class ProtosNetworkAuthorityConfinementTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void networkPrototypeAndProcessProtocolExposeNoAmbientAuthorityRecovery() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

        assertSame(
                prelude.networkPrototype(),
                prelude.bindings().readLocalSlot("Network").orElseThrow());
        assertFalse(
                prelude.bindings().hasLocalSlot("network"),
                "Prelude must publish only authority-free Network, never a concrete network grant");
        assertEquals(
                Set.of(
                        "args",
                        "environment",
                        "stdin",
                        "stdinEncoding",
                        "stdout",
                        "stdoutEncoding",
                        "stderr",
                        "stderrEncoding"),
                prelude.processPrototype().localSlotsSnapshot().keySet(),
                "Process must not grow a Network recovery selector");

        ProtosActivation activation = prelude.newModuleActivation();
        assertSame(
                prelude.networkPrototype(),
                ProtosActorValueTransfer.snapshotValue(prelude.networkPrototype(), activation),
                "the authority-free standard prototype remains ordinary shared Core state");
        assertSame(
                prelude.networkPrototype(),
                pCopyMethod()
                        .invoke(
                                null,
                                prelude.networkPrototype(),
                                activation,
                                new IdentityHashMap<Object, Object>()),
                "P may observe the authority-free standard prototype without receiving authority");
    }

    @Test
    void importedModuleCannotResolveRootNetworkAsAmbientName() throws Exception {
        Resolver resolver =
                new Resolver()
                        .module(
                                "root-import-probe",
                                "child: import(\"network-import-probe\")\n"
                                        + "boot: () => { {} }")
                        .module("network-import-probe", "capturedNetwork: network");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosNetworkCapabilityValue network =
                new ProtosNetworkCapabilityValue(prelude, new Object());
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(
                        prelude.actorRefPrototypeForRuntime(), null, network);

        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                                .initialize(
                                        process.rootActorForRuntime(),
                                        prelude,
                                        new ProtosModuleKey("root-import-probe"),
                                        "boot",
                                        List.of()));
    }

    @Test
    void hostedActorCannotResolveRootNetworkAsAmbientName() throws Exception {
        Resolver resolver =
                new Resolver()
                        .module("root-ok", "boot: () => { {} }")
                        .module(
                                "network-actor-probe",
                                "capturedNetwork: network\nboot: () => { {} }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosNetworkCapabilityValue network =
                new ProtosNetworkCapabilityValue(prelude, new Object());
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(
                        prelude.actorRefPrototypeForRuntime(), null, network);
        ProtosActorBootstrap bootstrap =
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver));

        assertTrue(
                bootstrap.initialize(
                        process.rootActorForRuntime(),
                        prelude,
                        new ProtosModuleKey("root-ok"),
                        "boot",
                        List.of()));

        ProtosActor child =
                process.createHostedActorForRuntime(prelude.actorRefPrototypeForRuntime());
        assertThrows(
                ProtosSignalException.class,
                () ->
                        bootstrap.initialize(
                                child,
                                prelude,
                                new ProtosModuleKey("network-actor-probe"),
                                "boot",
                                List.of()));
    }

    @Test
    void actualRootBootstrapGrantIsRejectedByActorAndPBoundaries() throws Exception {
        Resolver resolver = new Resolver().module("root-transfer-probe", "boot: () => { {} }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosNetworkCapabilityValue network =
                new ProtosNetworkCapabilityValue(prelude, new Object());
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(
                        prelude.actorRefPrototypeForRuntime(), null, network);
        ProtosActor root = process.rootActorForRuntime();
        ProtosModuleKey key = new ProtosModuleKey("root-transfer-probe");

        assertTrue(
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                        .initialize(root, prelude, key, "boot", List.of()));
        ProtosObjectValue module =
                root.moduleState().lookup(key).orElseThrow().instance();
        Object granted = module.readLocalSlot("network").orElseThrow();
        assertSame(network, granted);
        assertSame(network, process.rootNetworkForRuntime().orElseThrow());

        ProtosActivation activation =
                prelude.newModuleActivation(
                        root.moduleState(), key, module, root.executionDomain());
        ProtosSignalException actorFailure =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosActorValueTransfer.snapshotValue(granted, activation));
        assertSame(
                prelude.bindings().readLocalSlot("NonTransferableValue").orElseThrow(),
                actorFailure.error().parent().orElseThrow());

        InvocationTargetException pFailure =
                assertThrows(
                        InvocationTargetException.class,
                        () ->
                                pCopyMethod()
                                        .invoke(
                                                null,
                                                granted,
                                                activation,
                                                new IdentityHashMap<Object, Object>()));
        assertNotNull(pFailure.getCause());
        assertEquals("NonParallel", pFailure.getCause().getClass().getSimpleName());
    }

    private static Method pCopyMethod() throws Exception {
        Class<?> transfer =
                Class.forName(
                        "com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer");
        Method copy =
                transfer.getDeclaredMethod(
                        "copy", Object.class, ProtosActivation.class, IdentityHashMap.class);
        copy.setAccessible(true);
        return copy;
    }

    private static final class Resolver implements ProtosModuleResolver {
        private final Map<String, String> sources = new HashMap<>();

        Resolver module(String key, String source) {
            sources.put(key, source);
            return this;
        }

        @Override
        public ProtosModuleKey resolve(
                String exactSpecifier, Optional<ProtosModuleKey> importingModule) {
            if (!sources.containsKey(exactSpecifier)) {
                throw new IllegalArgumentException("unknown module: " + exactSpecifier);
            }
            return new ProtosModuleKey(exactSpecifier);
        }

        @Override
        public ProtosModuleSource loadSource(ProtosModuleKey key) {
            String source = sources.get(key.canonicalId());
            if (source == null) {
                throw new IllegalArgumentException("unknown module: " + key.canonicalId());
            }
            return ProtosModuleSource.fromCharacters(key, source);
        }
    }
}
