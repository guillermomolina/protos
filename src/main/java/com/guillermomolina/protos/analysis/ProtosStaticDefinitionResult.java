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

public record ProtosStaticDefinitionResult(
        ProtosDocumentSnapshot referenceSnapshot,
        SourceSpan referenceSpan,
        List<Target> targets) {
    public ProtosStaticDefinitionResult {
        Objects.requireNonNull(referenceSnapshot, "referenceSnapshot");
        Objects.requireNonNull(referenceSpan, "referenceSpan");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("definition result must contain at least one target");
        }
    }

    static ProtosStaticDefinitionResult singleton(
            ProtosDocumentSnapshot snapshot,
            SourceSpan referenceSpan,
            SourceSpan definitionSpan) {
        return new ProtosStaticDefinitionResult(
                snapshot,
                referenceSpan,
                List.of(new Target(snapshot, definitionSpan)));
    }

    public record Target(ProtosDocumentSnapshot snapshot, SourceSpan span) {
        public Target {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(span, "span");
        }
    }
}
