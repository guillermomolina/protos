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

import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TEST009-T durable, cross-run identity of a compiled Truffle root for the compilerability
 * causal trace.
 *
 * <p>The key is derived only from stable metadata, never from object identity, identity hash
 * codes, generated node ids or {@code toString()} labels:
 *
 * <ul>
 *   <li>A generated {@link ContinuationRootNode} is first normalized to its semantic source root
 *       (the {@link ProtosDiagnosticTraceCapture} pattern) and keeps its resume bytecode index,
 *       which PERF006-B5 location stability makes deterministic.
 *   <li>A semantic root is keyed by its kind and the exact source span the lowerer recorded for
 *       it ({@link Span}). Its own {@link RootNode#getSourceSection()} is not usable: source
 *       information is lazy, and materializing it reparses the root, which this observational
 *       trace must never do.
 *   <li>An untagged (structured-dispatch / C-prime) root has no source; it is keyed by a digest of
 *       its normalized instruction stream ({@link #fingerprint}).
 *   <li>Any root without such metadata yields no key and a {@link #keyProblem()}: the trace fails
 *       closed instead of falling back to an address.
 * </ul>
 *
 * <p>Every bytecode root also carries the structural fingerprint, so a consumer can reject two
 * structurally different roots that claim the same source-derived key.
 */
record ProtosCompilerabilityRootIdentity(
        String family,
        String rootClass,
        String rootName,
        boolean continuation,
        Integer resumeBytecodeIndex,
        String sourceUri,
        Integer sourceStart,
        Integer sourceLength,
        String semanticRootKind,
        String structuralFingerprint,
        String sourceRootKey,
        String durableKey,
        String keyProblem) {

    static final String SEMANTIC_BYTECODE_ROOT = "SEMANTIC_BYTECODE_ROOT";
    static final String UNTAGGED_BYTECODE_ROOT = "UNTAGGED_BYTECODE_ROOT";
    static final String OTHER_TRUFFLE_ROOT = "OTHER_TRUFFLE_ROOT";

    static final String TOP_LEVEL = "TOP_LEVEL";
    static final String CLOSURE = "CLOSURE";

    /**
     * The exact root span the lowerer knew when it ended a semantic root, recorded only while the
     * causal trace is enabled. Immutable; never read by guest execution.
     */
    record Span(Source source, int start, int length, String kind) {}

    /** Describes {@code target} without mutating it (no reparse, no source materialization). */
    static ProtosCompilerabilityRootIdentity describe(RootNode target) {
        boolean continuation = target instanceof ContinuationRootNode;
        Integer resumeBytecodeIndex = null;
        RootNode root = target;
        if (target instanceof ContinuationRootNode continuationRoot) {
            BytecodeRootNode sourceRoot = continuationRoot.getSourceRootNode();
            root = sourceRoot instanceof RootNode sourceRootNode ? sourceRootNode : null;
            BytecodeLocation location = continuationRoot.getLocation();
            resumeBytecodeIndex = location == null ? null : location.getBytecodeIndex();
        }
        if (root == null) {
            return compose(OTHER_TRUFFLE_ROOT, target.getClass().getName(), null, true, null, null,
                    null, null);
        }
        String family;
        Span span = null;
        if (root instanceof ProtosSemanticBytecodeRootNode semantic) {
            family = SEMANTIC_BYTECODE_ROOT;
            span = semantic.compilerabilityRootSpan();
        } else if (root instanceof ProtosBytecodeRootNode) {
            family = UNTAGGED_BYTECODE_ROOT;
        } else {
            family = OTHER_TRUFFLE_ROOT;
        }
        String fingerprint =
                root instanceof BytecodeRootNode bytecodeRoot
                        ? fingerprint(bytecodeRoot.getBytecodeNode())
                        : null;
        SourceSection otherSection = family.equals(OTHER_TRUFFLE_ROOT) ? root.getSourceSection() : null;
        return compose(family, root.getClass().getName(), root.getName(), continuation,
                resumeBytecodeIndex, span, fingerprint, otherSection);
    }

    /** Pure key derivation; package-private for focal tests. */
    static ProtosCompilerabilityRootIdentity compose(
            String family,
            String rootClass,
            String rootName,
            boolean continuation,
            Integer resumeBytecodeIndex,
            Span span,
            String fingerprint,
            SourceSection otherSection) {
        String sourceUri = null;
        Integer sourceStart = null;
        Integer sourceLength = null;
        String kind = null;
        String base = null;
        String problem = null;
        switch (family) {
            case SEMANTIC_BYTECODE_ROOT -> {
                if (span == null) {
                    problem = "SEMANTIC_ROOT_SPAN_UNRECORDED";
                } else {
                    sourceUri = sourceUri(span.source());
                    sourceStart = span.start();
                    sourceLength = span.length();
                    kind = span.kind();
                    base = String.join("|", family, kind, sourceUri,
                            Integer.toString(sourceStart), Integer.toString(sourceLength));
                }
            }
            case UNTAGGED_BYTECODE_ROOT -> {
                if (fingerprint == null) {
                    problem = "UNTAGGED_ROOT_WITHOUT_BYTECODE";
                } else {
                    base = String.join("|", family, rootClass, fingerprint);
                }
            }
            default -> {
                if (otherSection == null || !otherSection.isAvailable()) {
                    problem = "OTHER_ROOT_WITHOUT_STABLE_IDENTITY";
                } else {
                    sourceUri = sourceUri(otherSection.getSource());
                    sourceStart = otherSection.getCharIndex();
                    sourceLength = otherSection.getCharLength();
                    base = String.join("|", family, rootClass, String.valueOf(rootName), sourceUri,
                            Integer.toString(sourceStart), Integer.toString(sourceLength));
                }
            }
        }
        String key = base;
        if (base != null && continuation) {
            if (resumeBytecodeIndex == null) {
                problem = "CONTINUATION_RESUME_BCI_UNAVAILABLE";
                key = null;
            } else {
                key = base + "|continuation@" + resumeBytecodeIndex;
            }
        }
        return new ProtosCompilerabilityRootIdentity(family, rootClass, rootName, continuation,
                resumeBytecodeIndex, sourceUri, sourceStart, sourceLength, kind, fingerprint,
                continuation ? base : null, key, problem);
    }

    private static String sourceUri(Source source) {
        return source.getURI().toString();
    }

    /**
     * A digest of the root's instruction stream. Instrumentation instructions are skipped, the
     * quickening/boxing-elimination suffix ({@code $...}) is removed from instruction names, and
     * only scalar operands with run-independent values contribute their value; cached nodes,
     * profiles, tag nodes and non-scalar constants contribute only their kind or class.
     */
    static String fingerprint(BytecodeNode bytecode) {
        if (bytecode == null) {
            return null;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        int count = 0;
        StringBuilder entry = new StringBuilder();
        for (Instruction instruction : bytecode.getInstructions()) {
            if (instruction.isInstrumentation()) {
                continue;
            }
            count++;
            entry.setLength(0);
            entry.append(normalizedInstructionName(instruction.getName()));
            for (Instruction.Argument argument : instruction.getArguments()) {
                entry.append(' ').append(argument.getKind().name()).append('=')
                        .append(stableArgumentValue(argument));
            }
            entry.append('\n');
            digest.update(entry.toString().getBytes(StandardCharsets.UTF_8));
        }
        byte[] hash = digest.digest();
        StringBuilder out = new StringBuilder("bc").append(count).append(':');
        for (int index = 0; index < 8; index++) {
            out.append(String.format("%02x", hash[index]));
        }
        return out.toString();
    }

    static String normalizedInstructionName(String name) {
        int suffix = name.indexOf('$');
        return suffix < 0 ? name : name.substring(0, suffix);
    }

    private static String stableArgumentValue(Instruction.Argument argument) {
        return switch (argument.getKind()) {
            case INTEGER -> Integer.toString(argument.asInteger());
            case BYTECODE_INDEX -> Integer.toString(argument.asBytecodeIndex());
            case LOCAL_OFFSET -> Integer.toString(argument.asLocalOffset());
            case LOCAL_INDEX -> Integer.toString(argument.asLocalIndex());
            case CONSTANT -> stableConstant(argument.asConstant());
            default -> "";
        };
    }

    private static String stableConstant(Object constant) {
        if (constant == null) {
            return "null";
        }
        if (constant instanceof String || constant instanceof Boolean || constant instanceof Character
                || constant instanceof Integer || constant instanceof Long
                || constant instanceof Enum<?>) {
            return constant.getClass().getSimpleName() + ":" + constant;
        }
        return constant.getClass().getName();
    }

    /** The identity fields as an insertion-ordered JSON object, without run-local data. */
    Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("durable_root_key", durableKey);
        json.put("durable_root_key_problem", keyProblem);
        json.put("root_family", family);
        json.put("root_class", rootClass);
        json.put("root_name", rootName);
        json.put("source_uri", sourceUri);
        json.put("source_start", sourceStart);
        json.put("source_length", sourceLength);
        json.put("semantic_root_kind", semanticRootKind);
        json.put("continuation", continuation);
        json.put("continuation_source_root", sourceRootKey);
        json.put("continuation_resume_bci", resumeBytecodeIndex);
        json.put("structural_fingerprint", structuralFingerprint);
        return json;
    }
}
