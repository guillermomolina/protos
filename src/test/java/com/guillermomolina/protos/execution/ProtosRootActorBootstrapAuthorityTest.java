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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ProtosRootActorBootstrapAuthorityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void rootInitialModuleReceivesProcessBeforeItsFirstSourceExpression()
            throws Exception {
        Resolver resolver =
                new Resolver()
                        .module(
                                "root",
                                "capturedProcess: process\n"
                                        + "boot: () => { { observed: process } }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        ProtosActor root = process.rootActorForRuntime();
        ProtosModuleKey rootKey = new ProtosModuleKey("root");

        assertTrue(
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                        .initialize(root, prelude, rootKey, "boot", List.of()));

        ProtosObjectValue module =
                root.moduleState().lookup(rootKey).orElseThrow().instance();
        ProtosProcessCapabilityValue capability =
                assertInstanceOf(
                        ProtosProcessCapabilityValue.class,
                        module.readLocalSlot("process").orElseThrow());

        assertSame(process, capability.processForRuntime());
        assertSame(prelude.processPrototype(), capability.representedDelegationParent(prelude));
        assertSame(capability, module.readLocalSlot("capturedProcess").orElseThrow());
        assertSame(
                capability,
                root.currentBehavior().orElseThrow().readLocalSlot("observed").orElseThrow());
        assertFalse(module.hasLocalSlot("filesystem"));
    }

    @Test
    void rootFilesystemGrantIsOptionalAndAbsentMeansNoLocalSlot()
            throws Exception {
        Resolver absentResolver =
                new Resolver().module("root-absent", "boot: () => { {} }");
        ProtosPrelude absentPrelude =
                new ProtosCoreBootstrap().bootstrap(CORE, absentResolver);
        ProtosProcessRuntime absent = new ProtosProcessRuntime(actorRefPrototype());
        ProtosModuleKey absentKey = new ProtosModuleKey("root-absent");

        assertTrue(
                new ProtosActorBootstrap(new ProtosModuleRuntime(absentResolver))
                        .initialize(
                                absent.rootActorForRuntime(),
                                absentPrelude,
                                absentKey,
                                "boot",
                                List.of()));
        assertFalse(
                absent.rootActorForRuntime()
                        .moduleState()
                        .lookup(absentKey)
                        .orElseThrow()
                        .instance()
                        .hasLocalSlot("filesystem"));

        Resolver grantedResolver =
                new Resolver()
                        .module(
                                "root-granted",
                                "capturedFilesystem: filesystem\n"
                                        + "boot: () => { { observed: filesystem } }");
        ProtosPrelude grantedPrelude =
                new ProtosCoreBootstrap().bootstrap(CORE, grantedResolver);
        ProtosFilesystemValue filesystem = new ProtosFilesystemValue();
        ProtosProcessRuntime granted =
                new ProtosProcessRuntime(actorRefPrototype(), filesystem);
        ProtosModuleKey grantedKey = new ProtosModuleKey("root-granted");

        assertTrue(
                new ProtosActorBootstrap(new ProtosModuleRuntime(grantedResolver))
                        .initialize(
                                granted.rootActorForRuntime(),
                                grantedPrelude,
                                grantedKey,
                                "boot",
                                List.of()));

        ProtosObjectValue module =
                granted.rootActorForRuntime()
                        .moduleState()
                        .lookup(grantedKey)
                        .orElseThrow()
                        .instance();
        assertSame(filesystem, module.readLocalSlot("filesystem").orElseThrow());
        assertSame(filesystem, module.readLocalSlot("capturedFilesystem").orElseThrow());
        assertSame(
                filesystem,
                granted.rootActorForRuntime()
                        .currentBehavior()
                        .orElseThrow()
                        .readLocalSlot("observed")
                        .orElseThrow());
    }

    @Test
    void importedModulesReceiveNoAmbientProcessOrFilesystemSlots()
            throws Exception {
        Resolver resolver =
                new Resolver()
                        .module("root", "child: import(\"child\")\nboot: () => { {} }")
                        .module("child", "value: 1");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosFilesystemValue filesystem = new ProtosFilesystemValue();
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(actorRefPrototype(), filesystem);
        ProtosActor root = process.rootActorForRuntime();

        assertTrue(
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                        .initialize(
                                root,
                                prelude,
                                new ProtosModuleKey("root"),
                                "boot",
                                List.of()));

        ProtosObjectValue child =
                root.moduleState()
                        .lookup(new ProtosModuleKey("child"))
                        .orElseThrow()
                        .instance();
        assertFalse(child.hasLocalSlot("process"));
        assertFalse(child.hasLocalSlot("filesystem"));
    }

    @Test
    void hostedNonRootActorReceivesNoAmbientRootBootstrapAuthority()
            throws Exception {
        Resolver resolver =
                new Resolver().module("child", "boot: () => { {} }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosFilesystemValue filesystem = new ProtosFilesystemValue();
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(actorRefPrototype(), filesystem);
        ProtosActor child = process.createHostedActorForRuntime(actorRefPrototype());
        ProtosModuleKey childKey = new ProtosModuleKey("child");

        assertTrue(
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                        .initialize(child, prelude, childKey, "boot", List.of()));

        ProtosObjectValue module =
                child.moduleState().lookup(childKey).orElseThrow().instance();
        assertFalse(module.hasLocalSlot("process"));
        assertFalse(module.hasLocalSlot("filesystem"));
    }

    @Test
    void explicitProcessDelegationStillWorksWithoutCreatingAmbientChildSlot()
            throws Exception {
        Resolver resolver =
                new Resolver()
                        .module("root", "boot: () => { {} }")
                        .module("child", "boot: (p) => { { delegated: p } }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        ProtosActor root = process.rootActorForRuntime();
        ProtosActorBootstrap bootstrap =
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver));
        ProtosModuleKey rootKey = new ProtosModuleKey("root");

        assertTrue(bootstrap.initialize(root, prelude, rootKey, "boot", List.of()));
        ProtosObjectValue rootModule =
                root.moduleState().lookup(rootKey).orElseThrow().instance();
        ProtosProcessCapabilityValue rootCapability =
                assertInstanceOf(
                        ProtosProcessCapabilityValue.class,
                        rootModule.readLocalSlot("process").orElseThrow());

        ProtosActivation rootActivation =
                prelude.newModuleActivation(
                        root.moduleState(),
                        rootKey,
                        rootModule,
                        root.executionDomain());
        ProtosProcessCapabilityValue delegated =
                assertInstanceOf(
                        ProtosProcessCapabilityValue.class,
                        ProtosActorValueTransfer.snapshotValue(rootCapability, rootActivation));

        assertNotSame(rootCapability, delegated);
        assertSame(rootCapability.processForRuntime(), delegated.processForRuntime());

        ProtosActor child = process.createHostedActorForRuntime(actorRefPrototype());
        ProtosModuleKey childKey = new ProtosModuleKey("child");
        assertTrue(
                bootstrap.initialize(
                        child, prelude, childKey, "boot", List.of(delegated)));

        ProtosObjectValue childModule =
                child.moduleState().lookup(childKey).orElseThrow().instance();
        assertFalse(childModule.hasLocalSlot("process"));
        assertFalse(childModule.hasLocalSlot("filesystem"));
        assertSame(
                delegated,
                child.currentBehavior().orElseThrow().readLocalSlot("delegated").orElseThrow());
    }

    @Test
    void recursiveImportOfRootSeesAlreadyCachedBootstrapLocalProcessSlot()
            throws Exception {
        Resolver resolver =
                new Resolver()
                        .module("root", "child: import(\"child\")\nboot: () => { {} }")
                        .module(
                                "child",
                                "rootAgain: import(\"root\")\n"
                                        + "observedProcess: rootAgain.process");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        ProtosActor root = process.rootActorForRuntime();
        ProtosModuleKey rootKey = new ProtosModuleKey("root");

        assertTrue(
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                        .initialize(root, prelude, rootKey, "boot", List.of()));

        ProtosObjectValue rootModule =
                root.moduleState().lookup(rootKey).orElseThrow().instance();
        Object processCapability = rootModule.readLocalSlot("process").orElseThrow();
        ProtosObjectValue child =
                root.moduleState()
                        .lookup(new ProtosModuleKey("child"))
                        .orElseThrow()
                        .instance();

        assertSame(processCapability, child.readLocalSlot("observedProcess").orElseThrow());
        assertFalse(child.hasLocalSlot("process"));
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
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
        public String loadSource(ProtosModuleKey key) {
            String source = sources.get(key.canonicalId());
            if (source == null) {
                throw new IllegalArgumentException("unknown module: " + key.canonicalId());
            }
            return source;
        }
    }
}
