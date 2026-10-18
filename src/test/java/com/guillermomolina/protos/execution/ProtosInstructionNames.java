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

import com.oracle.truffle.api.bytecode.Instruction;
import java.util.Map;

/**
 * I113 test support: the protocol-level name of a Bytecode instruction.
 *
 * <p>I113 merged operations that differ only in a builder-fixed phase into
 * one operation with a discriminating constant operand. Instruction-order and
 * absence oracles still need to identify that phase exactly, so for those
 * operations this answers the name of the phase the constant selects (for
 * example {@code c.IsPreparedClosureInvocation} for
 * {@code c.IsResultKind} with {@link ProtosResultKind#PREPARED_CLOSURE_INVOCATION}).
 * Every other instruction keeps {@link Instruction#getName()}.
 */
public final class ProtosInstructionNames {
    private ProtosInstructionNames() {}

    private static final Map<String, Map<Object, String>> PHASES = Map.ofEntries(
            Map.entry("IsResultKind", Map.of(
                    ProtosResultKind.COMPACT_BOOLEAN_MISS, "IsCompactCanonicalBooleanMiss",
                    ProtosResultKind.COMPACT_BOOLEAN_NO_CALLBACK, "IsCompactCanonicalBooleanNoCallback",
                    ProtosResultKind.INLINE_LITERAL_CALL, "IsNativeIfTrueInlineCallback",
                    ProtosResultKind.PRIMITIVE_NUMERIC_SEND_MISS, "IsPrimitiveNumericSendMiss",
                    ProtosResultKind.PREPARED_CLOSURE_INVOCATION, "IsPreparedClosureInvocation",
                    ProtosResultKind.DIRECT_SEND_MISS, "IsDirectSendMiss",
                    ProtosResultKind.CAPTURED_OWNER_FRAME_SELECTED, "IsCapturedOwnerFrameSelected",
                    ProtosResultKind.BUFFERED_WRITER_STEP_REQUIRED, "IsBufferedByteWriterStepRequired",
                    ProtosResultKind.TEXT_READER_NEED_INPUT, "IsTextReaderNeedInput",
                    ProtosResultKind.CONTINUATION, "IsContinuation")),
            Map.entry("IsCanonicalBooleanClass", Map.of(
                    ProtosCanonicalBooleanOneNode.CLASS_TRUE, "IsCanonicalIfTrueTrue",
                    ProtosCanonicalBooleanOneNode.CLASS_FALSE, "IsCanonicalBooleanFalseClass")),
            Map.entry("Identity", Map.of(false, "Identity", true, "NotIdentity")),
            Map.entry("MutateLocalSlot", Map.of(
                    ProtosSlotMutation.CREATE, "CreateLocalSlot",
                    ProtosSlotMutation.ASSIGN, "AssignLocalSlot",
                    ProtosSlotMutation.ASSIGN_RESOLVED, "AssignResolvedLexicalTarget")),
            Map.entry("AccessNamedLexical", Map.of(
                    ProtosNamedLexicalAccess.LOOKUP, "Lookup",
                    ProtosNamedLexicalAccess.CAPTURED_FALLBACK, "ReadCapturedFallback",
                    ProtosNamedLexicalAccess.RESOLVE_WRITE, "ResolveWritableLexicalTarget")),
            Map.entry("AdmitsInlineLiteralWhileChild", Map.of(
                    false, "AdmitsInlineLiteralWhileCondition", true, "AdmitsInlineLiteralWhileBody")),
            Map.entry("AdmitsInlineLiteralEach", Map.of(
                    false, "AdmitsInlineLiteralIndexedEach", true, "AdmitsInlineLiteralTwoParameterEach")),
            Map.entry("AccessCapturedFrameLocal", Map.of(
                    false, "ReadCapturedFrameLocal", true, "ResolveCapturedWritableLexicalTarget")),
            Map.entry("AccessCapturedMaterializedLocal", Map.of(
                    false, "SelectCapturedMaterializedOwnerFrame",
                    true, "ResolveCapturedMaterializedWritableLexicalTarget")),
            Map.entry("AccessInlineCapturedFrameLocal", Map.of(
                    false, "ReadInlineCapturedFrameLocal",
                    true, "ResolveInlineCapturedWritableLexicalTarget")),
            Map.entry("AccessInlineCapturedMaterializedLocal", Map.of(
                    false, "SelectInlineCapturedMaterializedOwnerFrame",
                    true, "ResolveInlineCapturedMaterializedWritableLexicalTarget")),
            Map.entry("LoadInlineCallbackContext", Map.of(
                    false, "MaterializeInlineCallbackActivation", true, "LoadInlineCallbackReceiver")),
            Map.entry("LoadInlineLiteralCallPart", Map.of(
                    false, "LoadInlineCallbackActivation", true, "LoadInlineLiteralFallbackCall")),
            Map.entry("MapConstructionFactoryCall", Map.of(
                    false, "PrepareMapConstructionFactoryCall", true, "FinishMapConstructionFactoryCall")),
            Map.entry("CurrentActivation", Map.of(false, "CurrentActivation", true, "CurrentCallerReference")),
            Map.entry("StructuredPrepareInlineChildCall", Map.of(
                    false, "StructuredPrepareInlineChildCall",
                    true, "PrepareInlineStructuredWhileBodyCall")),
            Map.entry("IoInvocationSucceeded", Map.of(false, "IoInvocationSucceeded", true, "IoAwaitTargetFuture")),
            Map.entry("IoPrepareTargetCall", Map.of(false, "IoPrepareTargetCall", true, "IoPrepareWriterFlushCall")),
            Map.entry("BindClosureParameter", Map.of(false, "BindClosureParameter", true, "BindClosureRest")),
            Map.entry("BindClosureIndexedParameter", Map.of(
                    false, "BindClosureIndexedParameter", true, "BindClosureIndexedRest")),
            Map.entry("FinishClosureCall", Map.of(
                    false, "FinishClosureCall", true, "FinishCPrimeClosureCall")),
            Map.entry("PrepareClosureCallVector", Map.of(
                    false, "PrepareClosureCallVector", true, "PrepareSuperSendVector")),
            Map.entry("StructuredHasNext", Map.of(
                    ProtosStructuredPhase.STEP, "StructuredHasNext",
                    ProtosStructuredPhase.CHILD, "StructuredNeedsChildCall",
                    ProtosStructuredPhase.EQUALITY, "MapNeedsEquality")),
            Map.entry("StructuredPrepareChildCall", Map.of(
                    ProtosStructuredPhase.CHILD, "StructuredPrepareChildCall",
                    ProtosStructuredPhase.SECONDARY_CHILD, "StructuredPrepareSecondaryChildCall",
                    ProtosStructuredPhase.HASH, "MapPrepareHash",
                    ProtosStructuredPhase.EQUALITY, "MapPrepareEquality")),
            Map.entry("StructuredAcceptChildOutcome", Map.of(
                    ProtosStructuredPhase.CHILD, "StructuredAcceptChildOutcome",
                    ProtosStructuredPhase.HASH, "MapAcceptHash",
                    ProtosStructuredPhase.EQUALITY, "MapAcceptEquality")),
            Map.entry("StructuredAdvance", Map.of(
                    ProtosStructuredPhase.STEP, "StructuredAdvance",
                    ProtosStructuredPhase.ENTER_COMPARISON, "MapEnterComparison",
                    ProtosStructuredPhase.LEAVE_COMPARISON, "MapLeaveComparison")),
            Map.entry("ApplyBufferedByteWriterOutcome", Map.of(
                    false, "ApplyBufferedByteWriterFirstOutcome",
                    true, "ApplyBufferedByteWriterFollowupOutcome")));

    public static String of(Instruction instruction) {
        String name = instruction.getName();
        int start = name.startsWith("c.") ? 2 : 0;
        int end = name.indexOf('$', start);
        String operation = name.substring(start, end < 0 ? name.length() : end);
        Map<Object, String> phases = PHASES.get(operation);
        if (phases == null) {
            return name;
        }
        for (Instruction.Argument argument : instruction.getArguments()) {
            if (argument.getKind() == Instruction.Argument.Kind.CONSTANT) {
                String phase = phases.get(argument.asConstant());
                if (phase != null) {
                    return name.substring(0, start) + phase
                            + (end < 0 ? "" : name.substring(end));
                }
            }
        }
        throw new AssertionError("Undiscriminated " + name);
    }
}
