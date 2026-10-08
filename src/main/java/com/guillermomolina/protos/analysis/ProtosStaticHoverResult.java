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

import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import java.util.Objects;

/**
 * Protocol-neutral LM010-A hover projection for one exact source span.
 *
 * <p>Each section carries one information kind. {@link Kind#PROVEN_BINDING}
 * facts come only from D110 generation-1 exact definition proofs;
 * {@link Kind#SYNTAX} facts come only from the parser-authoritative surface
 * AST and never claim that another occurrence resolves to that syntax.</p>
 */
public record ProtosStaticHoverResult(
        ProtosDocumentSnapshot snapshot,
        SourceSpan span,
        List<Section> sections) {
    public ProtosStaticHoverResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(span, "span");
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("hover result must contain at least one section");
        }
    }

    public enum Kind {
        PROVEN_BINDING,
        SYNTAX
    }

    public record Section(Kind kind, List<String> facts) {
        public Section {
            Objects.requireNonNull(kind, "kind");
            facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
            if (facts.isEmpty()) {
                throw new IllegalArgumentException("hover section must contain at least one fact");
            }
        }
    }
}
