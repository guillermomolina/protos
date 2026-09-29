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

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.util.Objects;
import java.util.Optional;

/**
 * Module resolver whose Process-specific part is selected by the entered Protos Process Context.
 *
 * <p>A Prelude fixes its module resolver at Core bootstrap. Installing this resolver lets one
 * bootstrapped Prelude serve many fresh Processes that each need their own resolver (for example
 * one {@link ProtosDirectFileModuleResolver} per logical Case) without rebuilding Core per
 * Process. The selection is read from the {@link ProtosLanguageContext}, which is owned by exactly
 * one hosted Process, so no resolver state is shared between Processes.
 *
 * <p>When no Process resolver has been bound, or no Context is entered (as during the Core
 * bootstrap itself), resolution is delegated to the fallback resolver supplied at construction.
 */
final class ProtosContextBoundModuleResolver implements ProtosModuleResolver {
    private final ProtosModuleResolver fallback;

    ProtosContextBoundModuleResolver(ProtosModuleResolver fallback) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public ProtosModuleKey resolve(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws Exception {
        return selected().resolve(exactSpecifier, importingModule);
    }

    @Override
    public ProtosModuleSource loadSource(ProtosModuleKey key) throws Exception {
        return selected().loadSource(key);
    }

    private ProtosModuleResolver selected() {
        ProtosLanguageContext context = ProtosLanguageContext.currentIfEnteredForRuntime();
        ProtosModuleResolver bound =
                context == null ? null : context.boundModuleResolverForRuntime();
        return bound != null ? bound : fallback;
    }
}
