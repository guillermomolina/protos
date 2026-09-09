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

import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Objects;
import java.util.Optional;

public final class ProtosRootNode extends RootNode {
    @Child
    private ProtosExpressionNode body;
    private final Source source;

    public ProtosRootNode(ProtosExpressionNode body) {
        this(null, null, body);
    }

    ProtosRootNode(
            ProtosLanguage language,
            Source source,
            ProtosExpressionNode body) {
        super(language);
        this.source = source;
        this.body = Objects.requireNonNull(body, "body");
    }

    Optional<Source> source() {
        return Optional.ofNullable(source);
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public SourceSection getSourceSection() {
        return sourceSectionFor(body.span());
    }

    @CompilerDirectives.TruffleBoundary
    SourceSection sourceSectionFor(SourceSpan span) {
        Objects.requireNonNull(span, "span");
        if (source == null) {
            return null;
        }
        if (span.endOffset() > source.getLength()) {
            throw new IllegalStateException(
                    "source span "
                            + span
                            + " exceeds owning Truffle Source length "
                            + source.getLength()
                            + " for "
                            + source.getName());
        }
        return source.createSection(span.startOffset(), span.length());
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return body.execute(frame);
    }
}
