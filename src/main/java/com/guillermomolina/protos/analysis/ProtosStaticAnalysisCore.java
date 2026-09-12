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

package com.guillermomolina.protos.analysis;

import com.guillermomolina.protos.parser.ParseError;
import com.guillermomolina.protos.parser.ProtosParser;
import java.util.Objects;
import java.util.Optional;

/**
 * Editor-neutral static source-analysis entry point.
 *
 * <p>The initial LM009-F1 surface deliberately owns no workspace state, LSP
 * serialization, Truffle Context, guest execution, or module-resolution policy.
 * It reuses the real Protos parser directly.</p>
 */
public final class ProtosStaticAnalysisCore {

    public ProtosStaticParseResult parse(ProtosDocumentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        try {
            return new ProtosStaticParseResult.Parsed(
                    snapshot,
                    new ProtosParser(snapshot.characters()).parseProgram());
        } catch (ParseError error) {
            return new ProtosStaticParseResult.Failed(
                    snapshot,
                    error.getMessage(),
                    error.span(),
                    error.isUnexpectedEndOfSource());
        }
    }

    /**
     * Resolves the complete generation-1 D110 definition proof for one source
     * offset in the supplied immutable snapshot.
     *
     * <p>Parse failure, a non-reference offset, or an unproven definition is an
     * ordinary empty result.</p>
     */
    public Optional<ProtosStaticDefinitionResult> definition(
            ProtosDocumentSnapshot snapshot,
            int sourceOffset) {
        Objects.requireNonNull(snapshot, "snapshot");

        ProtosStaticParseResult parsed = parse(snapshot);
        if (!(parsed instanceof ProtosStaticParseResult.Parsed successful)) {
            return Optional.empty();
        }
        return ProtosStaticDefinitions.resolve(successful, sourceOffset);
    }
}
