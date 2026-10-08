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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable embedder configuration of external foreign provider plugins (I085-A).
 *
 * <p>Plugins are discovered only from the explicitly listed provider paths (JAR files or class
 * directories), never from the global class path or any other directory, and nothing is
 * downloaded or installed. An external JAR is executable Java code, so loading it is an explicit
 * host trust decision: the only configuration offered loads every discovered plugin as {@code
 * TRUSTED_IN_PROCESS} (PLAT052). Neither a plugin nor an import specifier can change that profile.
 */
public final class ProtosForeignProviderConfiguration {
    private final List<Path> providerPaths;
    private final Map<String, Map<String, String>> providerOptions;

    private ProtosForeignProviderConfiguration(
            List<Path> providerPaths, Map<String, Map<String, String>> providerOptions) {
        this.providerPaths = providerPaths;
        this.providerOptions = providerOptions;
    }

    /**
     * Trusts in process every plugin found on exactly {@code providerPaths}. An empty list
     * configures no provider and performs no discovery.
     */
    public static ProtosForeignProviderConfiguration trustedInProcess(List<Path> providerPaths) {
        List<Path> paths =
                Objects.requireNonNull(providerPaths, "providerPaths").stream()
                        .map(path -> path.toAbsolutePath().normalize())
                        .toList();
        return new ProtosForeignProviderConfiguration(paths, Map.of());
    }

    /** Returns a copy that supplies {@code options} to the provider registered as {@code id}. */
    public ProtosForeignProviderConfiguration withProviderOptions(
            String id, Map<String, String> options) {
        Map<String, Map<String, String>> copy = new HashMap<>(providerOptions);
        copy.put(Objects.requireNonNull(id, "id"), Map.copyOf(options));
        return new ProtosForeignProviderConfiguration(providerPaths, Map.copyOf(copy));
    }

    public List<Path> providerPaths() {
        return providerPaths;
    }

    Map<String, Map<String, String>> providerOptions() {
        return providerOptions;
    }
}
