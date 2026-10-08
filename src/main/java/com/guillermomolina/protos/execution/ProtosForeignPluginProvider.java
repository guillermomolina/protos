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

import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginEnvironment;
import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginSession;
import com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Internal bridge from one external {@link ProtosForeignProviderPlugin} to the I082 provider
 * substrate (I085-A).
 *
 * <p>The plugin contributes only its identity, scheme, canonicalization, and session/module
 * acquisition. Everything else is the existing provider machinery: the canonical foreign {@code
 * ModuleKey}, Actor-local facades with their cache and retry, lazy Process compartments, one
 * session per Actor, generation validation, and closure; values are projected through the single
 * plugin's own {@link com.guillermomolina.protos.spi.foreign.ProtosForeignValueOperations} through
 * {@link ProtosForeignPluginValueAdapter}. Module handles are opaque: Protos never assumes they are
 * Polyglot values or any other representation.
 *
 * <p>Building the descriptor calls only {@code providerId()} and {@code scheme()}. Plugin hooks run
 * through {@link ProtosExternalProviderPluginLoader}, the only owner of plugin class loading.
 */
final class ProtosForeignPluginProvider
        implements ProtosForeignModuleProvider, ProtosForeignProviderFactory {
    private final ProtosForeignProviderPlugin plugin;
    private final ProtosForeignProviderId id;
    private final ProtosForeignPluginEnvironment environment;

    private ProtosForeignPluginProvider(
            ProtosForeignProviderPlugin plugin,
            ProtosForeignProviderId id,
            Map<String, String> options) {
        this.plugin = plugin;
        this.id = id;
        Map<String, String> copied = Map.copyOf(options);
        this.environment =
                new ProtosForeignPluginEnvironment() {
                    @Override
                    public String providerId() {
                        return id.value();
                    }

                    @Override
                    public Map<String, String> options() {
                        return copied;
                    }
                };
    }

    /**
     * The descriptor of a plugin the host explicitly trusts in process. A generic external JAR
     * carries no PLAT052 enforcement mechanism, so trust is the only profile it can be admitted
     * under; the host, never the plugin, makes that choice.
     */
    static ProtosForeignProviderDescriptor trustedDescriptor(
            ProtosForeignProviderPlugin plugin, Map<String, String> options) {
        Objects.requireNonNull(plugin, "plugin");
        String rawId = ProtosExternalProviderPluginLoader.unchecked(plugin, plugin::providerId);
        String scheme = ProtosExternalProviderPluginLoader.unchecked(plugin, plugin::scheme);
        if (rawId == null || scheme == null) {
            throw new IllegalArgumentException(
                    "foreign provider plugin declares no identity or scheme: "
                            + plugin.getClass().getName());
        }
        ProtosForeignProviderId id = new ProtosForeignProviderId(rawId);
        ProtosForeignPluginProvider provider = new ProtosForeignPluginProvider(plugin, id, options);
        return new ProtosForeignProviderDescriptor(
                id,
                ProtosForeignProviderExecutionProfile.TRUSTED_IN_PROCESS,
                Optional.empty(),
                provider,
                Optional.of(new ProtosForeignImportRoute(scheme, provider)),
                new ProtosForeignPluginValueAdapter(plugin));
    }

    @Override
    public String canonicalTarget(String exactTarget) throws Exception {
        return Objects.requireNonNull(
                ProtosExternalProviderPluginLoader.withLoader(plugin, () -> plugin.canonicalTarget(exactTarget)),
                "canonical foreign target");
    }

    @Override
    public Object acquireTarget(ProtosForeignProviderSession session, String canonicalTarget)
            throws Exception {
        ProtosForeignPluginSession pluginSession = ((Session) session).plugin;
        return Objects.requireNonNull(
                ProtosExternalProviderPluginLoader.withLoader(plugin, () -> pluginSession.acquireModule(canonicalTarget)),
                "foreign module value");
    }

    /** The compartment is logical only: the plugin owns its physical topology per session. */
    @Override
    public ProtosForeignProviderCompartment openCompartment() {
        return new ProtosForeignProviderCompartment() {
            @Override
            public ProtosForeignProviderId providerId() {
                return id;
            }

            @Override
            public ProtosForeignProviderSession openSession() {
                return new Session(
                        plugin,
                        Objects.requireNonNull(
                                ProtosExternalProviderPluginLoader.unchecked(plugin, () -> plugin.openSession(environment)),
                                "foreign plugin session"));
            }

            @Override
            public void close() {}
        };
    }

    /** Runtime-side session wrapping exactly one plugin session. */
    static final class Session implements ProtosForeignProviderSession {
        private final ProtosForeignProviderPlugin owner;
        final ProtosForeignPluginSession plugin;

        private Session(ProtosForeignProviderPlugin owner, ProtosForeignPluginSession plugin) {
            this.owner = owner;
            this.plugin = plugin;
        }

        @Override
        public void close() {
            ProtosExternalProviderPluginLoader.unchecked(
                    owner,
                    () -> {
                        plugin.close();
                        return null;
                    });
        }
    }
}
