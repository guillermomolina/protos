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

package com.guillermomolina.protos.lsp;

import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import com.guillermomolina.protos.execution.ProtosPolyglotRuntimeHost;
import com.guillermomolina.protos.execution.ProtosStandardLibraryModuleResolver;
import com.guillermomolina.protos.execution.ProtosToolchainRoots;
import com.guillermomolina.protos.execution.ProtosWholeDocumentFormatter;
import java.nio.file.Path;

/**
 * Whole-document formatting operation consumed by the LSP formatting edge.
 *
 * <p>The production operation delegates to {@link ProtosWholeDocumentFormatter}
 * (TOOL010) with a runtime host scoped to the single request; nothing is
 * retained between requests.</p>
 */
@FunctionalInterface
interface ProtosLspDocumentFormatter {
    ProtosWholeDocumentFormatter.Result format(ProtosDocumentSnapshot snapshot) throws Exception;

    static ProtosLspDocumentFormatter toolchain() {
        return snapshot -> {
            Path core = ProtosToolchainRoots.core();
            try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
                return ProtosWholeDocumentFormatter.format(
                        core,
                        ProtosToolchainRoots.formatterTool(core),
                        new ProtosStandardLibraryModuleResolver(core.getParent()),
                        snapshot,
                        ignored -> {},
                        runtimeHost);
            }
        };
    }
}
