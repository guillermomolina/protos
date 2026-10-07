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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.util.Objects;

/**
 * Foreign import routing and Actor-local foreign module initialization (PLAT053, D188).
 *
 * <p>Routing and provider descriptors come from the RuntimeHost of the importing Actor's hosted
 * Process; an unhosted caller or an empty registry routes nothing. Initialization follows the
 * ordinary module lifecycle: the facade is cached {@code INITIALIZING} before the provider
 * acquires the target, so a reentrant import observes the same partial facade. Provider sessions
 * come exclusively from the Process lifecycle; this class keeps no cache of its own.
 */
final class ProtosForeignModuleImports {
    private ProtosForeignModuleImports() {}

    /**
     * Returns the canonical foreign key when {@code exactSpecifier} uses a scheme routed by the
     * caller's RuntimeHost, or null so the ordinary source resolver keeps its authority.
     */
    static ProtosModuleKey resolveOrNull(String exactSpecifier, ProtosActivation caller)
            throws Exception {
        int colon = exactSpecifier.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String scheme = exactSpecifier.substring(0, colon);
        if (!ProtosForeignImportRoute.isCandidateScheme(scheme)) {
            return null;
        }
        ProtosPolyglotProcessContext host = processHostOrNull(caller);
        if (host == null) {
            return null;
        }
        ProtosForeignProviderDescriptor descriptor =
                host.foreignProviderRegistryForRuntime().lookupImportScheme(scheme).orElse(null);
        if (descriptor == null) {
            return null;
        }
        if (descriptor.profile() == ProtosForeignProviderExecutionProfile.UNAVAILABLE) {
            throw new IllegalStateException(
                    "foreign provider is unavailable: " + descriptor.id().value());
        }
        String canonicalTarget =
                Objects.requireNonNull(
                        route(descriptor).modules().canonicalTarget(
                                exactSpecifier.substring(colon + 1)),
                        "foreign canonical target");
        return ProtosForeignModuleKey.encode(scheme, canonicalTarget);
    }

    /**
     * Creates, caches, and initializes the Actor-local facade of one foreign key absent from
     * {@code actorState}. On any failure the exact record is removed before the failure escapes.
     */
    static ProtosActorModuleState.ModuleRecord initialize(
            ProtosModuleKey key, ProtosActivation caller, ProtosActorModuleState actorState)
            throws Exception {
        ProtosForeignModuleKey.Address address = ProtosForeignModuleKey.decode(key);
        ProtosPolyglotProcessContext host = processHostOrNull(caller);
        if (host == null) {
            throw new IllegalStateException("foreign module import requires a hosted Process");
        }
        ProtosForeignProviderDescriptor descriptor =
                host.foreignProviderRegistryForRuntime()
                        .lookupImportScheme(address.scheme())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "no foreign provider routes the ModuleKey"));
        ProtosActor actor =
                caller.executionDomain()
                        .currentActorForRuntime()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "foreign import requires an Actor"));

        ProtosForeignModuleFacadeValue facade = new ProtosForeignModuleFacadeValue();
        ProtosActorModuleState.ModuleRecord record =
                new ProtosActorModuleState.ModuleRecord(facade);
        actorState.put(key, record); // normative cache-before-initialization point
        boolean ready = false;
        try {
            ProtosForeignProviderSessionBinding session =
                    host.foreignSessionForRuntime(actor, descriptor.id());
            Object target =
                    Objects.requireNonNull(
                            route(descriptor)
                                    .modules()
                                    .acquireTarget(
                                            session.sessionForRuntime(),
                                            address.canonicalTarget()),
                            "foreign module target");
            if (!session.isOpenForRuntime()) {
                throw new IllegalStateException(
                        "foreign provider session closed during import");
            }
            facade.attachForRuntime(
                    new ProtosForeignModuleFacadeValue.Attachment(
                            session, address.canonicalTarget(), target));
            record.markReady();
            ready = true;
            return record;
        } finally {
            if (!ready) {
                actorState.removeIfSame(key, record);
            }
        }
    }

    private static ProtosForeignImportRoute route(ProtosForeignProviderDescriptor descriptor) {
        return descriptor.importRoute().orElseThrow();
    }

    private static ProtosPolyglotProcessContext processHostOrNull(ProtosActivation caller) {
        return caller.executionDomain()
                        .currentActorForRuntime()
                        .flatMap(ProtosActor::processForRuntime)
                        .flatMap(process -> process.executionHostForRuntime())
                        .orElse(null)
                instanceof ProtosPolyglotProcessContext host
                ? host
                : null;
    }
}
