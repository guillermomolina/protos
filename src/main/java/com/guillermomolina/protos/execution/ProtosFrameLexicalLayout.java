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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * PERF012: backend-private, immutable description of one genuine {@code
 * ROOT}/{@code CLOSURE} Bytecode root's frame-backed lexical layout —
 * declaration-order names and their {@code LocalRangeAccessor} ordinals.
 *
 * <p>{@code CanonicalToBytecodeLowerer} builds exactly one instance per root
 * while lowering that root, from the same declaration-order name array
 * previously carried as a raw {@code String[]} constant operand. The
 * resulting instance is itself installed as a Bytecode {@code
 * ConstantOperand}, so every invocation of the root's {@code
 * InstallFrameLexicalAuthority} operation observes the identical, already
 * validated layout object instead of re-deriving it.
 *
 * <p>{@link ProtosFrameLexicalBindingAuthority} instances — one per
 * invocation — hold a reference to this shared layout rather than rebuilding
 * their own name/offset metadata, which removes the JDK {@code
 * LinkedHashMap} construction that previously ran on every authority
 * installation from the partial-evaluation-visible path (PERF010-B Finding
 * A). This class carries no per-invocation state: it describes layout only,
 * never a value or presence store.
 */
final class ProtosFrameLexicalLayout {
    private final String[] names;
    private final Map<String, Integer> offsets;

    /*
     * PERF025-D179-A: one-way, root/name-scoped speculation shared by every
     * invocation using this lowered layout. This is compiler stability
     * metadata only: LocalAccessor cleared state remains the semantic presence
     * authority. A successful PRESENT -> ABSENT transition invalidates the
     * corresponding token permanently; recreation never renews it.
     */
    private final Assumption[] presentContinuity;

    private ProtosFrameLexicalLayout(String[] names, Map<String, Integer> offsets) {
        this.names = names;
        this.offsets = offsets;
        this.presentContinuity = new Assumption[names.length];
        for (int ordinal = 0; ordinal < names.length; ordinal++) {
            presentContinuity[ordinal] =
                    Truffle.getRuntime()
                            .createAssumption(
                                    "Protos lexical binding remains present: "
                                            + names[ordinal]);
        }
    }

    static ProtosFrameLexicalLayout of(String[] frameBackedNames) {
        Objects.requireNonNull(frameBackedNames, "frameBackedNames");
        String[] names = frameBackedNames.clone();
        LinkedHashMap<String, Integer> offsets = new LinkedHashMap<>();
        for (int index = 0; index < names.length; index++) {
            Object candidate =
                    Objects.requireNonNull(names[index], "frameBackedNames[" + index + "]");
            if (!(candidate instanceof String name)) {
                throw new IllegalArgumentException(
                        "frame-backed binding name must be a String at index "
                                + index
                                + ": "
                                + candidate.getClass().getName());
            }
            if (offsets.put(name, index) != null) {
                throw new IllegalArgumentException(
                        "duplicate frame-backed binding name: " + name);
            }
        }
        return new ProtosFrameLexicalLayout(names, Collections.unmodifiableMap(offsets));
    }

    int length() {
        return names.length;
    }

    String nameAt(int ordinal) {
        return names[ordinal];
    }

    Integer offsetOf(String name) {
        return offsets.get(name);
    }

    /**
     * Defensive replay check for the Bytecode DSL's retained parser. The
     * canonical scope must reproduce the identical declaration-order layout
     * on every reparse; a mismatch would make previously emitted local
     * ordinals and root/name membership assumptions unsafe to reuse.
     */
    void requireSameNames(String[] frameBackedNames) {
        Objects.requireNonNull(frameBackedNames, "frameBackedNames");
        if (frameBackedNames.length != names.length) {
            throw new IllegalStateException(
                    "frame-backed lexical layout changed across Bytecode reparse: expected "
                            + names.length
                            + " names but found "
                            + frameBackedNames.length);
        }

        for (int ordinal = 0; ordinal < names.length; ordinal++) {
            String replayed =
                    Objects.requireNonNull(
                            frameBackedNames[ordinal],
                            "frameBackedNames[" + ordinal + "]");
            if (!names[ordinal].equals(replayed)) {
                throw new IllegalStateException(
                        "frame-backed lexical layout changed across Bytecode reparse at ordinal "
                                + ordinal
                                + ": expected "
                                + names[ordinal]
                                + " but found "
                                + replayed);
            }
        }
    }

    Assumption presentContinuityAt(int ordinal) {
        return presentContinuity[ordinal];
    }

    void invalidatePresentContinuityAt(int ordinal) {
        presentContinuity[ordinal].invalidate();
    }
}
