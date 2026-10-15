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

import com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Discovers external provider plugins from exactly the configured provider paths (I085-A).
 *
 * <p>This host-configuration class is the only owner of plugin class loading and discovery; the
 * {@code ProtosForeign*} substrate stays free of any discovery or class-loader access.
 *
 * <p>One {@link URLClassLoader} is created over those paths, delegating to the Protos class loader
 * for the public SPI and the Polyglot API. {@link ServiceLoader} declarations are honored only for
 * plugin classes defined by that loader, so a provider visible on the global class path is never
 * picked up. The loader is owned by the RuntimeHost and closed with it. Discovery runs only when
 * the configuration names at least one path; Native Image builds offer no such dynamic loading.
 */
final class ProtosExternalProviderPluginLoader {
    private ProtosExternalProviderPluginLoader() {}

    record Loaded(List<ProtosForeignProviderDescriptor> descriptors, URLClassLoader classLoader) {}

    static Loaded load(ProtosForeignProviderConfiguration configuration) {
        List<Path> paths = configuration.providerPaths();
        URL[] urls = new URL[paths.size()];
        for (int i = 0; i < urls.length; i++) {
            Path path = paths.get(i);
            if (!Files.isRegularFile(path) && !Files.isDirectory(path)) {
                throw new IllegalArgumentException("foreign provider path does not exist: " + path);
            }
            try {
                urls[i] = path.toUri().toURL();
            } catch (MalformedURLException failure) {
                throw new IllegalArgumentException("invalid foreign provider path: " + path, failure);
            }
        }
        URLClassLoader loader =
                new URLClassLoader(
                        "protos-foreign-providers",
                        urls,
                        ProtosForeignProviderPlugin.class.getClassLoader());
        try {
            List<ProtosForeignProviderDescriptor> descriptors = new ArrayList<>();
            Set<String> registered = new HashSet<>();
            for (ServiceLoader.Provider<ProtosForeignProviderPlugin> provider :
                    ServiceLoader.load(ProtosForeignProviderPlugin.class, loader).stream()
                            .filter(candidate -> candidate.type().getClassLoader() == loader)
                            .toList()) {
                ProtosForeignProviderPlugin plugin = provider.get();
                String id = plugin.providerId();
                ProtosForeignProviderDescriptor descriptor =
                        ProtosForeignPluginProvider.trustedDescriptor(
                                plugin,
                                id == null
                                        ? Map.of()
                                        : configuration.providerOptions().getOrDefault(id, Map.of()));
                descriptors.add(descriptor);
                registered.add(descriptor.id().value());
            }
            if (descriptors.isEmpty()) {
                throw new IllegalArgumentException(
                        "no foreign provider plugin found on the configured provider paths");
            }
            for (String id : configuration.providerOptions().keySet()) {
                if (!registered.contains(id)) {
                    throw new IllegalArgumentException(
                            "options supplied for an unknown foreign provider: " + id);
                }
            }
            return new Loaded(List.copyOf(descriptors), loader);
        } catch (ServiceConfigurationError failure) {
            closeAfterFailure(loader, failure);
            throw new IllegalArgumentException("malformed foreign provider plugin", failure);
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(loader, failure);
            throw failure;
        }
    }

    private static void closeAfterFailure(URLClassLoader loader, Throwable failure) {
        try {
            loader.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    /** A plugin hook; any exception it throws is a provider failure. */
    @FunctionalInterface
    interface Hook<T> {
        T run() throws Exception;
    }

    /**
     * Runs a plugin hook with the plugin's class loader as the thread context class loader, so a
     * provider can find its own dependencies.
     */
    static <T> T withLoader(ProtosForeignProviderPlugin plugin, Hook<T> hook) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(plugin.getClass().getClassLoader());
        try {
            return hook.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /** Runs a hook whose internal caller cannot propagate a checked failure. */
    static <T> T unchecked(ProtosForeignProviderPlugin plugin, Hook<T> hook) {
        try {
            return withLoader(plugin, hook);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("foreign provider plugin failed", failure);
        }
    }
}
