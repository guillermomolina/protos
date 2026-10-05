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
import com.oracle.truffle.api.bytecode.BytecodeLocal;
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
 *
 * <p>BUG018-C: when the scope is lowered as a genuine root, each ordinal also
 * records the public {@link BytecodeLocal#getLocalOffset()} of the root-scoped
 * {@code BytecodeLocal} backing it, derived from the same local array that
 * forms the root's {@code LocalRangeAccessor}. A retained-frame value read of
 * a {@link ProtosFrameLexicalBindingAuthority} uses that offset with {@link
 * com.oracle.truffle.api.bytecode.BytecodeNode#getLocalValue}. Only the
 * derived integers are kept, never the parse-local {@code BytecodeLocal}
 * objects themselves. The same scope may also be lowered as an inline
 * callback, whose block-scoped locals have context-dependent offsets; that
 * lowering never installs such an authority and contributes no offsets.
 */
final class ProtosFrameLexicalLayout {
    private final String[] names;
    private final Map<String, Integer> offsets;
    /* Root-lowering local offsets; null until the scope is lowered as a root. */
    private int[] rootLocalOffsets;

    /*
     * PERF025-D179-A: one-way, root/name-scoped speculation shared by every
     * invocation using this lowered layout. This is compiler stability
     * metadata only: LocalAccessor cleared state remains the semantic presence
     * authority. A successful PRESENT -> ABSENT transition invalidates the
     * corresponding token permanently; recreation never renews it.
     */
    private final Assumption[] presentContinuity;

    private ProtosFrameLexicalLayout(
            String[] names,
            Map<String, Integer> offsets,
            int[] rootLocalOffsets) {
        this.names = names;
        this.offsets = offsets;
        this.rootLocalOffsets = rootLocalOffsets;
        this.presentContinuity = new Assumption[names.length];
        for (int ordinal = 0; ordinal < names.length; ordinal++) {
            presentContinuity[ordinal] =
                    Truffle.getRuntime()
                            .createAssumption(
                                    "Protos lexical binding remains present: "
                                            + names[ordinal]);
        }
    }

    /**
     * The public local offsets of {@code locals}, in array order: the root
     * local offsets of the layout whose {@code LocalRangeAccessor} is built
     * from the same array.
     */
    static int[] localOffsetsOf(BytecodeLocal[] locals) {
        Objects.requireNonNull(locals, "locals");
        int[] result = new int[locals.length];
        for (int index = 0; index < locals.length; index++) {
            result[index] =
                    Objects.requireNonNull(locals[index], "locals[" + index + "]")
                            .getLocalOffset();
        }
        return result;
    }

    /** A layout whose scope has not (yet) been lowered as a root. */
    static ProtosFrameLexicalLayout of(String[] frameBackedNames) {
        return create(frameBackedNames, null);
    }

    /** A layout of a root whose root-scoped locals have these public offsets. */
    static ProtosFrameLexicalLayout of(String[] frameBackedNames, int[] rootLocalOffsets) {
        return create(
                frameBackedNames,
                Objects.requireNonNull(rootLocalOffsets, "rootLocalOffsets"));
    }

    private static ProtosFrameLexicalLayout create(
            String[] frameBackedNames, int[] rootLocalOffsets) {
        Objects.requireNonNull(frameBackedNames, "frameBackedNames");
        if (rootLocalOffsets != null && rootLocalOffsets.length != frameBackedNames.length) {
            throw new IllegalArgumentException(
                    "frame-backed local-offset count must match binding-name count");
        }
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
        return new ProtosFrameLexicalLayout(
                names,
                Collections.unmodifiableMap(offsets),
                rootLocalOffsets == null ? null : rootLocalOffsets.clone());
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
     * The public {@link BytecodeLocal#getLocalOffset()} of the root-scoped
     * local backing {@code ordinal}. Only a root lowering installs a frame
     * authority, and it records these offsets before emitting that
     * installation.
     */
    int localOffsetAt(int ordinal) {
        if (rootLocalOffsets == null) {
            throw new IllegalStateException(
                    "frame-backed lexical layout has no root local offsets: " + names[ordinal]);
        }
        return rootLocalOffsets[ordinal];
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

    /**
     * BUG018-C: records, on the first root lowering of this scope, the public
     * offsets of its root-scoped locals; every later root lowering, including
     * a Bytecode reparse, must reproduce them exactly, because retained-frame
     * reads address each binding through its offset. Runs only while lowering
     * (single-threaded parser), before any authority of the root exists.
     */
    void bindRootLocalOffsets(int[] frameBackedLocalOffsets) {
        Objects.requireNonNull(frameBackedLocalOffsets, "frameBackedLocalOffsets");
        if (rootLocalOffsets == null) {
            if (frameBackedLocalOffsets.length != names.length) {
                throw new IllegalArgumentException(
                        "frame-backed local-offset count must match binding-name count");
            }
            rootLocalOffsets = frameBackedLocalOffsets.clone();
            return;
        }
        int[] localOffsets = rootLocalOffsets;
        if (frameBackedLocalOffsets.length != localOffsets.length) {
            throw new IllegalStateException(
                    "frame-backed lexical layout changed across Bytecode reparse: expected "
                            + localOffsets.length
                            + " local offsets but found "
                            + frameBackedLocalOffsets.length);
        }
        for (int ordinal = 0; ordinal < localOffsets.length; ordinal++) {
            if (localOffsets[ordinal] != frameBackedLocalOffsets[ordinal]) {
                throw new IllegalStateException(
                        "frame-backed lexical layout changed across Bytecode reparse at ordinal "
                                + ordinal
                                + " ("
                                + names[ordinal]
                                + "): expected local offset "
                                + localOffsets[ordinal]
                                + " but found "
                                + frameBackedLocalOffsets[ordinal]);
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
