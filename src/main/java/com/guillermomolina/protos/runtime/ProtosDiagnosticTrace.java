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

package com.guillermomolina.protos.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * CLI008-C1 (PLAT049 Candidate C) inert snapshot of the semantic guest frames of one terminal
 * Error occurrence.
 *
 * <p>The snapshot belongs to the occurrence (the failing transfer or terminal Task failure), never
 * to the Error value: the same Error signalled twice yields two independent snapshots and the
 * Error itself is never mutated. Once captured, this value is the only durable authority; it holds
 * plain strings and integers and retains no Truffle frame, call target, root, bytecode, Source or
 * Context object.
 *
 * <p>Frames are ordered innermost first. At most {@link #MAX_FRAMES} semantic guest frames are
 * retained; when more existed, the innermost ones are kept and {@link #truncated()} is set. The
 * number of omitted frames is deliberately not recorded.
 */
public record ProtosDiagnosticTrace(List<Frame> frames, boolean truncated) {
    /** PLAT049 bound on retained semantic guest frames per occurrence. */
    public static final int MAX_FRAMES = 64;

    public ProtosDiagnosticTrace {
        frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
        if (frames.size() > MAX_FRAMES) {
            throw new IllegalArgumentException(
                    "diagnostic trace retains at most " + MAX_FRAMES + " frames");
        }
        if (truncated && frames.size() != MAX_FRAMES) {
            throw new IllegalArgumentException(
                    "only a full diagnostic trace can be truncated");
        }
    }

    /**
     * One semantic guest activation.
     *
     * @param label the executable label, present only when an existing authority genuinely knows
     *     it; an anonymous Closure stays anonymous
     * @param sourceName the Source/module identity of the activation's code
     * @param displayPath the physical path of that Source when it has one
     * @param line one-based line of the activation's current position
     * @param column one-based column of the activation's current position
     */
    public record Frame(
            String label,
            String sourceName,
            String displayPath,
            int line,
            int column) {
        public Frame {
            Objects.requireNonNull(sourceName, "sourceName");
            if (line < 1 || column < 1) {
                throw new IllegalArgumentException("diagnostic frame position is one-based");
            }
        }

        public Optional<String> knownLabel() {
            return Optional.ofNullable(label);
        }

        public Optional<String> knownDisplayPath() {
            return Optional.ofNullable(displayPath);
        }
    }
}
