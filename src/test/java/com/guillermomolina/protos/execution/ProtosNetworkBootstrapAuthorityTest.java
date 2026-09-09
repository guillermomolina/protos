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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ProtosNetworkBootstrapAuthorityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void rootInitialModuleReceivesExactNetworkBeforeItsFirstSourceExpression()
            throws Exception {
        Resolver resolver =
                new Resolver()
                        .module(
                                "root-network",
                                "capturedNetwork: network\n"
                                        + "boot: () => { { observed: network } }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosNetworkCapabilityValue network =
                new ProtosNetworkCapabilityValue(prelude, new Object());
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(
                        prelude.actorRefPrototypeForRuntime(), null, network);
        ProtosActor root = process.rootActorForRuntime();
        ProtosModuleKey key = new ProtosModuleKey("root-network");

        assertTrue(
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                        .initialize(root, prelude, key, "boot", List.of()));

        ProtosObjectValue module =
                root.moduleState().lookup(key).orElseThrow().instance();
        assertSame(network, module.readLocalSlot("network").orElseThrow());
        assertSame(network, module.readLocalSlot("capturedNetwork").orElseThrow());
        assertSame(
                network,
                root.currentBehavior().orElseThrow().readLocalSlot("observed").orElseThrow());
        assertSame(prelude.networkPrototype(), network.representedDelegationParent(prelude));
        assertFalse(module.hasLocalSlot("filesystem"));
    }

    @Test
    void absentNetworkGrantCreatesNoRootLocalSlot() throws Exception {
        Resolver resolver =
                new Resolver().module("root-network-absent", "boot: () => { {} }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        ProtosModuleKey key = new ProtosModuleKey("root-network-absent");

        assertTrue(
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                        .initialize(
                                process.rootActorForRuntime(),
                                prelude,
                                key,
                                "boot",
                                List.of()));
        assertFalse(
                process.rootActorForRuntime()
                        .moduleState()
                        .lookup(key)
                        .orElseThrow()
                        .instance()
                        .hasLocalSlot("network"));
    }

    @Test
    void importedModulesAndHostedActorsDoNotReceiveRootNetworkGrant()
            throws Exception {
        Resolver resolver =
                new Resolver()
                        .module(
                                "root-with-import",
                                "child: import(\"imported-child\")\nboot: () => { {} }")
                        .module("imported-child", "value: 1")
                        .module("hosted-child", "boot: () => { {} }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosNetworkCapabilityValue network =
                new ProtosNetworkCapabilityValue(prelude, new Object());
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(
                        prelude.actorRefPrototypeForRuntime(), null, network);
        ProtosActorBootstrap bootstrap =
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver));

        ProtosModuleKey rootKey = new ProtosModuleKey("root-with-import");
        assertTrue(
                bootstrap.initialize(
                        process.rootActorForRuntime(),
                        prelude,
                        rootKey,
                        "boot",
                        List.of()));
        ProtosObjectValue imported =
                process.rootActorForRuntime()
                        .moduleState()
                        .lookup(new ProtosModuleKey("imported-child"))
                        .orElseThrow()
                        .instance();
        assertFalse(imported.hasLocalSlot("network"));

        ProtosActor child =
                process.createHostedActorForRuntime(prelude.actorRefPrototypeForRuntime());
        ProtosModuleKey childKey = new ProtosModuleKey("hosted-child");
        assertTrue(bootstrap.initialize(child, prelude, childKey, "boot", List.of()));
        assertFalse(
                child.moduleState()
                        .lookup(childKey)
                        .orElseThrow()
                        .instance()
                        .hasLocalSlot("network"));
    }

    @Test
    void standaloneBootstrapUsesExactOptionalNetworkGrant() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosNetworkCapabilityValue network =
                new ProtosNetworkCapabilityValue(prelude, new Object());

        ProtosStandaloneProcessBootstrap.Result granted =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of(),
                        exactDomain(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        network);
        assertSame(
                network,
                granted.activation().context().readLocalSlot("network").orElseThrow());

        ProtosStandaloneProcessBootstrap.Result absent =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of(),
                        exactDomain(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        assertFalse(absent.activation().context().hasLocalSlot("network"));
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactDomain() {
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
