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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorRefValue;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosGroupRefValue;
import com.guillermomolina.protos.runtime.ProtosGroupSendOperationValue;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPathValue;
import com.guillermomolina.protos.runtime.ProtosProcessCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamValue;
import com.guillermomolina.protos.runtime.ProtosSendOperationValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.io.ProtosFileValue;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpConnectionValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerValue;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Private runtime facility of {@code std:inspect} (D205, LIB022-B).
 *
 * <p>The public operations are ordinary Closures of {@code lib/inspect.protos}. This frozen
 * facility is a deferred standard initial member of exactly that module, created only when an
 * authorized Actor-local instance of the module initializes; its bootstrap slot is removed, so it
 * is never module surface or a Prelude binding.
 *
 * <p>Authority is exclusively the Context option {@code protos.Inspect}: the module import is
 * guarded by {@link #requireAuthorizedImport}, and every operation re-checks the currently
 * entered Context, so a retained reference to a module function grants nothing in an
 * unauthorized Context. Every successful operation answers a non-empty, implementation-specific
 * diagnostic String; every failure signals a fresh ordinary Error.
 *
 * <p>No operation calls the inspected Closure, binds or prepares an activation, or creates a Task,
 * Actor or Process. Physical roots are resolved only through the Context-local Bytecode
 * projection of the entered Context and never escape as guest values. The facility retains no
 * Context, root, plan or registry.
 */
public final class ProtosInspectFacility {
    public static final ProtosModuleKey MODULE_KEY = new ProtosModuleKey("std:inspect");
    public static final String BOOTSTRAP_SLOT = "_inspectFacility";

    /** Explicit bound on one diagnostic String; a longer rendering is marked as truncated. */
    static final int MAX_OUTPUT_CHARS = 64 * 1024;

    static final String TRUNCATION_MARKER = "... [truncated: diagnostic output limit reached]";

    private static final String UNSTABLE_NOTE = "implementation-specific diagnostic; unstable format";

    private ProtosInspectFacility() {}

    public static ProtosObjectValue createFacility() {
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        facility.createLocalSlot(
                "representation",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                representation(
                                        argument(facility, activation, supplied), activation)));
        facility.createLocalSlot(
                "callableInfo",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                callableInfo(
                                        closureArgument(facility, activation, supplied),
                                        activation)));
        facility.createLocalSlot(
                "bytecode",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                bytecode(
                                        closureArgument(facility, activation, supplied),
                                        activation)));
        facility.createLocalSlot(
                "truffleTree",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                truffleTree(
                                        closureArgument(facility, activation, supplied),
                                        activation)));
        return facility.freeze();
    }

    /**
     * Import guard of {@code std:inspect}: called by the module runtime before any Actor-local
     * cache lookup, so neither a fresh nor a cached instance is reachable without the authority of
     * the currently entered Context. Every other module key is unaffected.
     */
    public static void requireAuthorizedImport(ProtosModuleKey key, ProtosActivation caller) {
        if (MODULE_KEY.equals(key)) {
            requireAuthorizedContext(caller);
        }
    }

    // ---- Shared validation ----

    @TruffleBoundary
    private static Object argument(
            ProtosObjectValue facility, ProtosActivation activation, List<?> supplied) {
        if (activation.receiver() != facility || supplied.size() != 1) {
            throw invalid(activation);
        }
        requireAuthorizedContext(activation);
        return supplied.get(0);
    }

    private static ProtosClosureValue closureArgument(
            ProtosObjectValue facility, ProtosActivation activation, List<?> supplied) {
        if (argument(facility, activation, supplied) instanceof ProtosClosureValue closure) {
            return closure;
        }
        throw invalid(activation);
    }

    /**
     * The authority is the option of the Context that is actually entered now; the receiver check
     * above is an additional guard, never a substitute.
     */
    @TruffleBoundary
    private static ProtosLanguageContext requireAuthorizedContext(ProtosActivation activation) {
        ProtosLanguageContext context;
        try {
            context = ProtosLanguageContext.currentIfEnteredForRuntime();
        } catch (RuntimeException unavailable) {
            throw invalid(activation);
        }
        if (context == null || !context.inspectEnabledForRuntime()) {
            throw invalid(activation);
        }
        return context;
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    private static ProtosStringValue text(StringBuilder rendering) {
        if (rendering.length() == 0) {
            throw new IllegalStateException("empty inspection diagnostic");
        }
        return new ProtosStringValue(rendering.toString());
    }

    // ---- representation(value) ----

    @TruffleBoundary
    private static Object representation(Object value, ProtosActivation activation) {
        String family = shallowFamily(value);
        if (family == null) {
            // A value that is neither a Protos runtime value nor an opaque foreign object cannot
            // be classified safely.
            throw invalid(activation);
        }
        StringBuilder rendering = new StringBuilder();
        rendering.append("Protos value representation (").append(UNSTABLE_NOTE).append(")\n");
        rendering.append("family: ").append(family).append('\n');
        return text(rendering);
    }

    /**
     * Shallow classification written by Protos: never enumerates slots, members, elements,
     * String contents, captures, receivers, callbacks or handles, and never invokes guest code or
     * renders a Java class name or {@code toString()} of the value.
     */
    private static String shallowFamily(Object value) {
        if (value == ProtosNullValue.INSTANCE) {
            return "null";
        }
        if (value instanceof ProtosBooleanValue) {
            return "Boolean";
        }
        if (ProtosNumericValueSupport.isCurrentInteger(value)) {
            return "Integer";
        }
        if (ProtosNumericValueSupport.isCurrentFloat(value)) {
            return "Float";
        }
        if (value instanceof ProtosStringValue) {
            return "String";
        }
        if (value instanceof ProtosClosureValue closure) {
            return closure.nativeBody().isPresent()
                    ? "Closure (native body)"
                    : "Closure (source-backed)";
        }
        if (value instanceof ProtosArrayValue) {
            return "Array";
        }
        if (value instanceof ProtosBytesValue) {
            return "Bytes";
        }
        if (value instanceof ProtosMapValue) {
            return "Map";
        }
        if (value instanceof ProtosIdentityMapValue) {
            return "IdentityMap";
        }
        if (value instanceof ProtosFutureValue) {
            return "Future";
        }
        if (value instanceof ProtosActorRefValue) {
            return "Actor reference";
        }
        if (value instanceof ProtosGroupRefValue) {
            return "Group reference";
        }
        if (value instanceof ProtosSendOperationValue) {
            return "send operation";
        }
        if (value instanceof ProtosGroupSendOperationValue) {
            return "Group send operation";
        }
        String capability = capabilityFamily(value);
        if (capability != null) {
            return "runtime capability (" + capability + ")";
        }
        if (value instanceof ProtosObjectValue object) {
            return object.getClass() == ProtosObjectValue.class
                    ? "Object (ordinary)"
                    : "special runtime object (opaque)";
        }
        if (value instanceof TruffleObject) {
            // Any other runtime-special or foreign interop value: classified without inspection.
            return "opaque value (runtime-special or foreign)";
        }
        return null;
    }

    private static String capabilityFamily(Object value) {
        if (value instanceof ProtosEncodingValue) {
            return "Encoding";
        }
        if (value instanceof ProtosEnvironmentValue) {
            return "Environment";
        }
        if (value instanceof ProtosPathValue) {
            return "Path";
        }
        if (value instanceof ProtosProcessCapabilityValue) {
            return "Process";
        }
        if (value instanceof ProtosProcessStandardStreamValue) {
            return "Process standard stream";
        }
        if (value instanceof ProtosFilesystemValue) {
            return "Filesystem";
        }
        if (value instanceof ProtosFileValue) {
            return "File";
        }
        if (value instanceof ProtosNetworkCapabilityValue) {
            return "Network";
        }
        if (value instanceof ProtosTcpConnectionValue) {
            return "TCP connection";
        }
        if (value instanceof ProtosTcpListenerValue) {
            return "TCP listener";
        }
        return null;
    }

    // ---- callableInfo(closure) ----

    @TruffleBoundary
    private static Object callableInfo(ProtosClosureValue closure, ProtosActivation activation) {
        try {
            StringBuilder rendering = new StringBuilder();
            rendering.append("Protos callable (").append(UNSTABLE_NOTE).append(")\n");
            if (closure.nativeBody().isPresent()) {
                rendering.append("kind: native Closure\n");
                rendering.append("semantic definition: none (native body)\n");
                rendering.append("bytecode/truffleTree: unavailable (no source-backed definition)\n");
                return text(rendering);
            }
            CanonicalClosure definition = closure.definition();
            if (definition == null) {
                throw invalid(activation);
            }
            SourceSpan span = definition.span();
            rendering.append("kind: source-backed Closure\n");
            rendering
                    .append("semantic definition: canonical Closure, ")
                    .append(definition.parameters().size())
                    .append(" declared parameter(s)\n");
            rendering
                    .append("source span: offsets ")
                    .append(span.startOffset())
                    .append("..")
                    .append(span.endOffset())
                    .append(" (length ")
                    .append(span.length())
                    .append(")\n");
            ProtosClosureExecutionPlan plan = closure.executionPlan().orElse(null);
            if (plan != null) {
                Source source = plan.source().orElse(null);
                rendering
                        .append("source: ")
                        .append(source == null ? "unavailable" : source.getName())
                        .append('\n');
                rendering.append("plan: prepared\n");
            } else if (closure.executionPlanRematerializerForParallelRuntime().isPresent()) {
                rendering.append("source: not observed (plan not prepared)\n");
                rendering.append(
                        "plan: deferred; rematerialized only on an explicit bytecode/truffleTree"
                                + " request\n");
            } else {
                rendering.append("source: unavailable\n");
                rendering.append("plan: unavailable\n");
            }
            rendering.append(
                    "physical root: not resolved by callableInfo; bytecode/truffleTree resolve the"
                            + " entered Context's own projection\n");
            return text(rendering);
        } catch (ProtosSignalException signal) {
            throw signal;
        } catch (RuntimeException unavailable) {
            throw invalid(activation);
        }
    }

    // ---- Context-local physical root ----

    /**
     * Resolves the physical Bytecode activation root of {@code closure} in the entered Context.
     * Only the existing Context-local projection is used; the root of the template plan, which may
     * belong to another Context, is never used directly. The root stays internal to this class.
     */
    private static ProtosBytecodeRootNode contextLocalRoot(
            ProtosClosureValue closure, ProtosActivation activation) {
        if (closure.nativeBody().isPresent()) {
            throw invalid(activation);
        }
        CanonicalClosure definition = closure.definition();
        if (definition == null) {
            throw invalid(activation);
        }
        ProtosClosureExecutionPlan template =
                closure.executionPlan()
                        .orElseGet(
                                // Explicit, authorized rematerialization; no call is performed.
                                closure::executionPlanForRuntimeInvocation);
        if (template.source().isEmpty()) {
            throw invalid(activation);
        }
        ProtosLanguageContext context = requireAuthorizedContext(activation);
        ProtosClosureExecutionPlan projected =
                context.bytecodeExecutionPlanForEnteredClosure(closure, template);
        if (projected.language().orElse(null) != context.languageForRuntime()) {
            throw invalid(activation);
        }
        ProtosBytecodeRootNode root = projected.bytecodeActivationRootForInspection();
        if (root == null || !root.isSemanticActivationRoot() || root.getBytecodeNode() == null) {
            throw invalid(activation);
        }
        return root;
    }

    // ---- bytecode(closure) ----

    @TruffleBoundary
    private static Object bytecode(ProtosClosureValue closure, ProtosActivation activation) {
        try {
            ProtosBytecodeRootNode root = contextLocalRoot(closure, activation);
            BytecodeNode bytecodeNode = root.getBytecodeNode();
            List<Instruction> instructions = bytecodeNode.getInstructionsAsList();
            if (instructions.isEmpty()) {
                throw invalid(activation);
            }
            String header =
                    "Protos physical Bytecode DSL of a semantic Closure activation root ("
                            + UNSTABLE_NOTE
                            + ")\ninstructions: "
                            + instructions.size()
                            + "\n";
            List<String> lines = new ArrayList<>(instructions.size());
            for (Instruction instruction : instructions) {
                lines.add(instructionLine(instruction));
            }
            return text(new StringBuilder(boundedLines(header, lines)));
        } catch (RuntimeException unavailable) {
            // Includes ProtosSignalException from preparation, rematerialization or projection:
            // every failure is a fresh ordinary Error of this inspection call.
            throw invalid(activation);
        }
    }

    /**
     * One real instruction: its bytecode index, its DSL name and, per argument, only its DSL name
     * and structural kind. Constant operands, cached nodes, profiles and tag nodes are never
     * rendered; only bytecode indices and local slots, which are structural, show their number.
     */
    private static String instructionLine(Instruction instruction) {
        StringBuilder line = new StringBuilder();
        line.append(String.format(Locale.ROOT, "%6d  %s", instruction.getBytecodeIndex(),
                instruction.getName()));
        for (Instruction.Argument argument : instruction.getArguments()) {
            Instruction.Argument.Kind kind = argument.getKind();
            line.append(' ').append(argument.getName()).append(':');
            switch (kind) {
                case BYTECODE_INDEX -> line.append("bci=").append(argument.asBytecodeIndex());
                case LOCAL_OFFSET -> line.append("local-offset=").append(argument.asLocalOffset());
                case LOCAL_INDEX -> line.append("local-index=").append(argument.asLocalIndex());
                default -> line.append(kind.name().toLowerCase(Locale.ROOT));
            }
        }
        return line.append('\n').toString();
    }

    // ---- truffleTree(closure) ----

    @TruffleBoundary
    private static Object truffleTree(ProtosClosureValue closure, ProtosActivation activation) {
        try {
            ProtosBytecodeRootNode root = contextLocalRoot(closure, activation);
            String tree = NodeUtil.printCompactTreeToString(root);
            if (tree == null || tree.isBlank()) {
                throw invalid(activation);
            }
            String header =
                    "Protos physical Truffle node tree of a semantic Closure activation root"
                            + " (not the semantic AST, not Graal compiler IR; "
                            + UNSTABLE_NOTE
                            + ")\n";
            return text(new StringBuilder(boundedText(header, tree)));
        } catch (RuntimeException unavailable) {
            // Includes ProtosSignalException from preparation, rematerialization or projection:
            // every failure is a fresh ordinary Error of this inspection call.
            throw invalid(activation);
        }
    }

    // ---- Output bound ----

    private static final String TRUNCATION_LINE = TRUNCATION_MARKER + "\n";

    /**
     * {@code header} followed by whole {@code lines}; when everything does not fit in {@link
     * #MAX_OUTPUT_CHARS}, as many whole lines as fit beside the complete truncation line. The
     * result, marker included, never exceeds the bound.
     */
    static String boundedLines(String header, List<String> lines) {
        long total = header.length();
        for (String line : lines) {
            total += line.length();
        }
        StringBuilder rendering = new StringBuilder(header);
        if (total <= MAX_OUTPUT_CHARS) {
            lines.forEach(rendering::append);
            return rendering.toString();
        }
        int limit = MAX_OUTPUT_CHARS - TRUNCATION_LINE.length();
        for (String line : lines) {
            if (rendering.length() + line.length() > limit) {
                break;
            }
            rendering.append(line);
        }
        return rendering.append(TRUNCATION_LINE).toString();
    }

    /**
     * {@code header} followed by {@code body}; when that exceeds {@link #MAX_OUTPUT_CHARS}, a
     * prefix of {@code body}, a line break and the complete truncation line, never exceeding the
     * bound.
     */
    static String boundedText(String header, String body) {
        if ((long) header.length() + body.length() <= MAX_OUTPUT_CHARS) {
            return header + body;
        }
        int room = MAX_OUTPUT_CHARS - header.length() - 1 - TRUNCATION_LINE.length();
        return header + body.substring(0, Math.max(0, room)) + "\n" + TRUNCATION_LINE;
    }
}
