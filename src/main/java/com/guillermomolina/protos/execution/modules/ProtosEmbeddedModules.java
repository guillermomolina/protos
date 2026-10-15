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

import java.util.Map;
import java.util.Objects;
import org.graalvm.polyglot.Context;

/**
 * Host API that supplies Protos application modules to a standard Polyglot {@link Context}
 * (PLAT055).
 *
 * <p>A host builds an ordinary Context, installs its catalog, and then evaluates its main source:
 *
 * <pre>{@code
 * try (Context context = Context.newBuilder("protos").build()) {
 *     ProtosEmbeddedModules.install(context, Map.of("app:worker", workerSource));
 *     context.eval("protos", "worker: Actor.spawn(\"app:worker\", \"start\")\n...");
 * }
 * }</pre>
 *
 * <p>Each catalog entry maps an exact {@code app:} specifier to the Protos source characters of
 * that module. The specifier is the module's canonical identity, so {@code import(specifier)} and
 * {@code Actor.spawn(specifier, ...)} resolve it through the ordinary module runtime of the
 * Process: Actor-local instances, cache-before-execute, cycles, and failed-initialization retry are
 * unchanged, and a module is read and compiled only when first imported. Host evaluations never
 * acquire a catalog identity, whatever their Source name, URI, or content.
 *
 * <p>Supplying code grants the program no authority: Filesystem, Network, host access, and thread
 * creation remain exactly what the Context allows.
 */
public final class ProtosEmbeddedModules {
    private ProtosEmbeddedModules() {}

    /**
     * Installs the application module catalog of {@code context}.
     *
     * <p>The catalog is copied: later changes to {@code modules} have no effect. An empty catalog is
     * a valid installation. Installation is allowed once per Context, after it is built and before
     * its Protos Process starts bootstrapping (the first evaluation); initializing the language,
     * reading its bindings, or parsing a Source beforehand does not prevent it, and installing
     * does not create the Process.
     *
     * @throws NullPointerException if an argument, a specifier, or a source is {@code null}
     * @throws IllegalArgumentException if a specifier is not {@code app:} followed by a non-empty
     *     name, or if the Context does not support the Protos language
     * @throws IllegalStateException if the Context is closed, already has a catalog, its Process
     *     bootstrap has begun, or its Protos Process is owned by a Protos host driver
     */
    public static void install(Context context, Map<String, String> modules) {
        Objects.requireNonNull(context, "context");
        // Validation precedes any Context interaction, so an invalid catalog publishes nothing.
        Map<String, String> catalog = ProtosApplicationModuleResolver.authorizedCatalog(modules);
        // Creates the language Context only; the Process stays lazy.
        context.initialize(ProtosLanguage.ID);
        context.enter();
        try {
            ProtosLanguageContext.current().installApplicationModules(catalog);
        } finally {
            context.leave();
        }
    }
}
