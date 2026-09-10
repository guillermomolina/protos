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

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumberLiteral;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;

/**
 * Parallel canonical-to-Bytecode lowering seam for the PERF006-B migration.
 *
 * <p>PERF006-B2D5A keeps the B2C parameter/default activation contract and
 * adds caller-supplied spread to ordinary parenthesized calls in Closure bodies.
 * A call target is staged exactly once before its argument items. Spread
 * expressions are evaluated in source order and their standard Array indexed
 * snapshots are appended immediately at that same position, so later argument
 * effects or suspension cannot change already-contributed positional elements.
 * Message-send spread and default-expression spread remain deliberately deferred.
 * The ordinary {@link ProtosSourceCompiler} remains
 * on the established AST lowerer
 * until later PERF006-B slices have migrated calls, suspension and control
 * semantics coherently.</p>
 */
final class CanonicalToBytecodeLowerer {
    private final ProtosLanguage language;
    private final Source source;

    CanonicalToBytecodeLowerer(ProtosLanguage language, Source source) {
        this.language = Objects.requireNonNull(language, "language");
        this.source = Objects.requireNonNull(source, "source");
    }

    CallTarget lower(CanonicalSequence sequence) {
        return lowerRoot(sequence).getCallTarget();
    }

    ProtosBytecodeRootNode lowerRoot(CanonicalSequence sequence) {
        return lowerRoot(sequence, null);
    }

    ProtosBytecodeRootNode lowerClosureActivationRoot(
            CanonicalClosure definition) {
        Objects.requireNonNull(definition, "definition");
        validateSupportedDefaults(definition);
        return lowerRoot(definition.body(), definition);
    }

    private ProtosBytecodeRootNode lowerRoot(
            CanonicalSequence sequence,
            CanonicalClosure activationDefinition) {
        Objects.requireNonNull(sequence, "sequence");
        validateSupported(sequence);
        validateSpan(sequence.span());

        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            SourceSpan rootSpan = sequence.span();

                            /*
                             * Root-inside-SourceSection is the Bytecode DSL shape
                             * that gives the root a reliable exact source section
                             * once lazy source information is materialized.
                             */
                            builder.beginSource(source);
                            builder.beginSourceSection(
                                    rootSpan.startOffset(),
                                    rootSpan.length());
                            builder.beginRoot();

                            BytecodeLocal defaultValue = null;
                            BytecodeLocal defaultPreparedCall = null;
                            BytecodeLocal defaultChildResult = null;
                            BytecodeLocal defaultResumeValue = null;
                            if (activationDefinition != null
                                    && hasComposedDefault(activationDefinition)) {
                                defaultValue = builder.createLocal("defaultValue", null);
                                defaultPreparedCall = builder.createLocal("defaultPreparedClosureCall", null);
                                defaultChildResult = builder.createLocal("defaultChildResult", null);
                                defaultResumeValue = builder.createLocal("defaultResumeValue", null);
                            }

                            if (activationDefinition != null) {
                                emitClosureParameterBindings(
                                        builder,
                                        activationDefinition,
                                        defaultValue,
                                        defaultPreparedCall,
                                        defaultChildResult,
                                        defaultResumeValue);
                            }

                            if (sequence.expressions().isEmpty()) {
                                builder.beginReturn();
                                builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                                builder.endReturn();
                            } else {
                                BytecodeLocal result =
                                        builder.createLocal("sequenceResult", null);
                                boolean hasComposedInvocation =
                                        sequence.expressions().stream()
                                                .anyMatch(
                                                        expression ->
                                                                expression instanceof CanonicalCall
                                                                        || expression
                                                                                instanceof CanonicalSend);
                                BytecodeLocal preparedCall =
                                        hasComposedInvocation
                                                ? builder.createLocal(
                                                        "preparedClosureCall",
                                                        null)
                                                : null;
                                BytecodeLocal childResult =
                                        hasComposedInvocation
                                                ? builder.createLocal(
                                                        "childResult",
                                                        null)
                                                : null;
                                BytecodeLocal resumeValue =
                                        hasComposedInvocation
                                                ? builder.createLocal(
                                                        "resumeValue",
                                                        null)
                                                : null;

                                for (CanonicalExpression expression :
                                        sequence.expressions()) {
                                    SourceSpan span = expression.span();

                                    builder.beginSourceSection(
                                            span.startOffset(),
                                            span.length());
                                    builder.beginTag(
                                            StandardTags.StatementTag.class);
                                    builder.beginBlock();

                                    if (expression instanceof CanonicalCall call) {
                                        emitComposedCall(
                                                builder,
                                                call,
                                                result,
                                                preparedCall,
                                                childResult,
                                                resumeValue);
                                    } else if (expression instanceof CanonicalSend send) {
                                        emitComposedSend(
                                                builder,
                                                send,
                                                result,
                                                preparedCall,
                                                childResult,
                                                resumeValue);
                                    } else {
                                        builder.beginStoreLocal(result);
                                        emitExpression(builder, expression);
                                        builder.endStoreLocal();
                                    }

                                    builder.endBlock();
                                    builder.endTag(
                                            StandardTags.StatementTag.class);
                                    builder.endSourceSection();
                                }

                                builder.beginReturn();
                                builder.emitLoadLocal(result);
                                builder.endReturn();
                            }

                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });

        return roots.getNode(0);
    }

    private void validateSupportedDefaults(
            CanonicalClosure definition) {
        for (CanonicalParameter parameter : definition.parameters()) {
            if (parameter.defaultValue().isEmpty()) {
                continue;
            }
            validateSupportedDefaultExpression(parameter.defaultValue().orElseThrow());
        }
    }

    private void validateSupportedDefaultExpression(
            CanonicalExpression expression) {
        validateSpan(expression.span());
        if (expression instanceof CanonicalLiteral
                || expression instanceof CanonicalLookup) {
            return;
        }
        if (expression instanceof CanonicalCall call) {
            validateSupportedDefaultExpression(call.receiver());
            for (CanonicalExpression argument : call.arguments()) {
                validateSupportedDefaultExpression(argument);
            }
            return;
        }
        if (expression instanceof CanonicalSend send) {
            validateSupportedDefaultExpression(send.receiver());
            for (CanonicalExpression argument : send.arguments()) {
                validateSupportedDefaultExpression(argument);
            }
            return;
        }
        throw new UnsupportedOperationException(
                "PERF006-B2C3B3 default expression is not migrated: "
                        + expression.getClass().getSimpleName());
    }

    private static boolean hasComposedDefault(CanonicalClosure definition) {
        return definition.parameters().stream()
                .flatMap(parameter -> parameter.defaultValue().stream())
                .anyMatch(expression -> expression instanceof CanonicalCall
                        || expression instanceof CanonicalSend);
    }

    private static void emitClosureParameterBindings(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalClosure definition,
            BytecodeLocal defaultValue,
            BytecodeLocal defaultPreparedCall,
            BytecodeLocal defaultChildResult,
            BytecodeLocal defaultResumeValue) {
        int positionalIndex = 0;
        boolean hasRest = false;

        for (CanonicalParameter parameter : definition.parameters()) {
            if (parameter.rest()) {
                builder.beginBindClosureRest();
                builder.emitLoadArgument(0);
                builder.emitLoadConstant(parameter.name());
                builder.emitLoadConstant(positionalIndex);
                builder.endBindClosureRest();
                hasRest = true;
                continue;
            }

            if (parameter.defaultValue().isPresent()) {
                CanonicalExpression defaultExpression =
                        parameter.defaultValue().orElseThrow();

                builder.beginIfThenElse();

                builder.beginHasClosureArgument();
                builder.emitLoadArgument(0);
                builder.emitLoadConstant(positionalIndex);
                builder.endHasClosureArgument();

                builder.beginBlock();
                emitBindSuppliedClosureParameter(
                        builder,
                        parameter,
                        positionalIndex);
                builder.endBlock();

                builder.beginBlock();
                if (defaultExpression instanceof CanonicalCall defaultCall) {
                    emitComposedDefaultCall(
                            builder,
                            defaultCall,
                            defaultValue,
                            defaultPreparedCall,
                            defaultChildResult,
                            defaultResumeValue);
                    emitBindDefaultLocal(builder, parameter, defaultValue);
                } else if (defaultExpression instanceof CanonicalSend defaultSend) {
                    emitComposedDefaultSend(
                            builder,
                            defaultSend,
                            defaultValue,
                            defaultPreparedCall,
                            defaultChildResult,
                            defaultResumeValue);
                    emitBindDefaultLocal(builder, parameter, defaultValue);
                } else {
                    builder.beginBindClosureParameter();
                    builder.emitLoadArgument(0);
                    builder.emitLoadConstant(parameter.name());
                    builder.beginSourceSection(
                            defaultExpression.span().startOffset(),
                            defaultExpression.span().length());
                    emitExpression(builder, defaultExpression);
                    builder.endSourceSection();
                    builder.endBindClosureParameter();
                }
                builder.endBlock();

                builder.endIfThenElse();
            } else {
                emitBindSuppliedClosureParameter(
                        builder,
                        parameter,
                        positionalIndex);
            }

            positionalIndex++;
        }

        if (!hasRest) {
            builder.beginCheckClosureArgumentUpperBound();
            builder.emitLoadArgument(0);
            builder.emitLoadConstant(positionalIndex);
            builder.endCheckClosureArgumentUpperBound();
        }
    }

    private static void emitBindDefaultLocal(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalParameter parameter,
            BytecodeLocal defaultValue) {
        if (defaultValue == null) {
            throw new AssertionError("composed default value local was not allocated");
        }
        builder.beginBindClosureParameter();
        builder.emitLoadArgument(0);
        builder.emitLoadConstant(parameter.name());
        builder.emitLoadLocal(defaultValue);
        builder.endBindClosureParameter();
    }

    private static boolean hasComposedArgument(
            java.util.List<CanonicalExpression> arguments) {
        return arguments.stream()
                .anyMatch(
                        argument ->
                                argument instanceof CanonicalCall
                                        || argument instanceof CanonicalSend);
    }

    private static boolean hasSpreadArgument(
            java.util.List<CanonicalExpression> arguments) {
        return arguments.stream()
                .anyMatch(CanonicalSpread.class::isInstance);
    }

    private static CanonicalExpression suppliedArgumentExpression(
            CanonicalExpression argument) {
        if (argument instanceof CanonicalSpread spread) {
            return spread.expression();
        }
        return argument;
    }

    private static void emitBodySpreadArgumentVector(
            ProtosBytecodeRootNodeGen.Builder builder,
            java.util.List<CanonicalExpression> arguments,
            BytecodeLocal suppliedVector,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginStoreLocal(suppliedVector);
        builder.emitCreateSuppliedArgumentVector();
        builder.endStoreLocal();

        for (CanonicalExpression argument : arguments) {
            CanonicalExpression valueExpression =
                    suppliedArgumentExpression(argument);
            BytecodeLocal value =
                    builder.createLocal(
                            argument instanceof CanonicalSpread
                                    ? "spreadArgumentValue"
                                    : "callArgumentValue",
                            null);
            emitBodyExpressionToLocal(
                    builder,
                    valueExpression,
                    value,
                    preparedCall,
                    childResult,
                    resumeValue);

            if (argument instanceof CanonicalSpread) {
                builder.beginAppendSpreadSuppliedArgument();
                builder.emitLoadLocal(suppliedVector);
                builder.emitLoadLocal(value);
                builder.emitLoadArgument(0);
                builder.endAppendSpreadSuppliedArgument();
            } else {
                builder.beginAppendSuppliedArgument();
                builder.emitLoadLocal(suppliedVector);
                builder.emitLoadLocal(value);
                builder.endAppendSuppliedArgument();
            }
        }
    }

    private static void emitBodyExpressionToLocal(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression,
            BytecodeLocal target,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (expression instanceof CanonicalCall call) {
            builder.beginSourceSection(
                    call.span().startOffset(),
                    call.span().length());
            emitComposedCall(
                    builder,
                    call,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.endSourceSection();
            return;
        }
        if (expression instanceof CanonicalSend send) {
            builder.beginSourceSection(
                    send.span().startOffset(),
                    send.span().length());
            emitComposedSend(
                    builder,
                    send,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.endSourceSection();
            return;
        }
        builder.beginStoreLocal(target);
        emitExpression(builder, expression);
        builder.endStoreLocal();
    }

    private static void emitDefaultExpressionToLocal(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression,
            BytecodeLocal target,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (expression instanceof CanonicalCall call) {
            emitComposedDefaultCall(
                    builder,
                    call,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalSend send) {
            emitComposedDefaultSend(
                    builder,
                    send,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        builder.beginStoreLocal(target);
        emitExpression(builder, expression);
        builder.endStoreLocal();
    }

    private static void emitComposedDefaultCall(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCall call,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(result, preparedCall, childResult, resumeValue);
        CanonicalExpression receiver = call.receiver();
        boolean stageReceiver =
                receiver instanceof CanonicalCall
                        || receiver instanceof CanonicalSend;
        boolean stageArguments = hasComposedArgument(call.arguments());
        boolean stageInputs = stageReceiver || stageArguments;
        BytecodeLocal receiverValue = null;
        java.util.List<BytecodeLocal> argumentValues = java.util.List.of();

        builder.beginSourceSection(call.span().startOffset(), call.span().length());
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            receiverValue = builder.createLocal("defaultCallReceiver", null);
            if (stageReceiver) {
                emitDefaultExpressionToLocal(
                        builder,
                        receiver,
                        receiverValue,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                builder.beginStoreLocal(receiverValue);
                emitExpression(builder, receiver);
                builder.endStoreLocal();
            }

            argumentValues = new java.util.ArrayList<>(call.arguments().size());
            for (CanonicalExpression argument : call.arguments()) {
                BytecodeLocal argumentValue =
                        builder.createLocal("defaultCallArgument", null);
                emitDefaultExpressionToLocal(
                        builder,
                        argument,
                        argumentValue,
                        preparedCall,
                        childResult,
                        resumeValue);
                argumentValues.add(argumentValue);
            }
        }

        builder.beginStoreLocal(preparedCall);
        builder.beginPrepareDefaultClosureCallArguments();
        if (stageInputs) {
            builder.emitLoadLocal(receiverValue);
        } else {
            emitExpression(builder, receiver);
        }
        builder.emitLoadArgument(0);
        if (stageInputs) {
            for (BytecodeLocal argumentValue : argumentValues) {
                builder.emitLoadLocal(argumentValue);
            }
        } else {
            for (CanonicalExpression argument : call.arguments()) {
                emitExpression(builder, argument);
            }
        }
        builder.endPrepareDefaultClosureCallArguments();
        builder.endStoreLocal();
        emitComposedPreparedDefaultInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
        builder.endSourceSection();
    }

    private static void emitComposedDefaultSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(result, preparedCall, childResult, resumeValue);
        CanonicalExpression receiver = send.receiver();
        boolean stageReceiver =
                receiver instanceof CanonicalCall
                        || receiver instanceof CanonicalSend;
        boolean stageArguments = hasComposedArgument(send.arguments());
        boolean stageInputs = stageReceiver || stageArguments;
        BytecodeLocal receiverValue = null;
        java.util.List<BytecodeLocal> argumentValues = java.util.List.of();

        builder.beginSourceSection(send.span().startOffset(), send.span().length());
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            receiverValue = builder.createLocal("defaultSendReceiver", null);
            if (stageReceiver) {
                emitDefaultExpressionToLocal(
                        builder,
                        receiver,
                        receiverValue,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                builder.beginStoreLocal(receiverValue);
                emitExpression(builder, receiver);
                builder.endStoreLocal();
            }

            argumentValues = new java.util.ArrayList<>(send.arguments().size());
            for (CanonicalExpression argument : send.arguments()) {
                BytecodeLocal argumentValue =
                        builder.createLocal("defaultSendArgument", null);
                emitDefaultExpressionToLocal(
                        builder,
                        argument,
                        argumentValue,
                        preparedCall,
                        childResult,
                        resumeValue);
                argumentValues.add(argumentValue);
            }
        }

        builder.beginStoreLocal(preparedCall);
        builder.beginPrepareSendArguments();
        if (stageInputs) {
            builder.emitLoadLocal(receiverValue);
        } else {
            emitExpression(builder, receiver);
        }
        builder.emitLoadConstant(send.message());
        builder.emitLoadArgument(0);
        if (stageInputs) {
            for (BytecodeLocal argumentValue : argumentValues) {
                builder.emitLoadLocal(argumentValue);
            }
        } else {
            for (CanonicalExpression argument : send.arguments()) {
                emitExpression(builder, argument);
            }
        }
        builder.endPrepareSendArguments();
        builder.endStoreLocal();
        emitComposedPreparedDefaultInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
        builder.endSourceSection();
    }

    private static void emitComposedPreparedDefaultInvocation(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginStoreLocal(childResult);
        builder.beginEnterClosureCall();
        builder.emitLoadLocal(preparedCall);
        builder.endEnterClosureCall();
        builder.endStoreLocal();
        builder.beginWhile();
        builder.beginIsContinuation();
        builder.emitLoadLocal(childResult);
        builder.endIsContinuation();
        builder.beginBlock();
        builder.beginStoreLocal(resumeValue);
        builder.beginYield();
        builder.emitLoadLocal(childResult);
        builder.endYield();
        builder.endStoreLocal();
        builder.beginStoreLocal(childResult);
        builder.beginResumeContinuation();
        builder.emitLoadLocal(childResult);
        builder.emitLoadLocal(resumeValue);
        builder.endResumeContinuation();
        builder.endStoreLocal();
        builder.endBlock();
        builder.endWhile();
        builder.beginStoreLocal(result);
        builder.beginFinishClosureCall();
        builder.emitLoadLocal(preparedCall);
        builder.emitLoadLocal(childResult);
        builder.endFinishClosureCall();
        builder.endStoreLocal();
    }

    private static void requireDefaultScratch(
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (result == null || preparedCall == null || childResult == null || resumeValue == null) {
            throw new AssertionError("Bytecode composed-default scratch locals were not allocated");
        }
    }

    private static void emitBindSuppliedClosureParameter(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalParameter parameter,
            int positionalIndex) {
        builder.beginBindClosureParameter();
        builder.emitLoadArgument(0);
        builder.emitLoadConstant(parameter.name());
        builder.beginLoadClosureArgument();
        builder.emitLoadArgument(0);
        builder.emitLoadConstant(positionalIndex);
        builder.endLoadClosureArgument();
        builder.endBindClosureParameter();
    }

    private void validateSupported(CanonicalSequence sequence) {
        for (CanonicalExpression expression : sequence.expressions()) {
            validateSupportedExpression(expression);
        }
    }

    private void validateSupportedExpression(
            CanonicalExpression expression) {
        validateSpan(expression.span());
        if (expression instanceof CanonicalLiteral
                || expression instanceof CanonicalLookup) {
            return;
        }
        if (expression instanceof CanonicalCall call) {
            validateSupportedExpression(call.receiver());
            for (CanonicalExpression argument : call.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedExpression(spread.expression());
                } else {
                    validateSupportedExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalSend send) {
            validateSupportedExpression(send.receiver());
            for (CanonicalExpression argument : send.arguments()) {
                validateSupportedExpression(argument);
            }
            return;
        }
        throw new UnsupportedOperationException(
                "PERF006-B2B Bytecode lowerer does not yet support "
                        + expression.getClass().getSimpleName());
    }

    private void validateSpan(SourceSpan span) {
        Objects.requireNonNull(span, "span");
        if (span.endOffset() > source.getLength()) {
            throw new IllegalArgumentException(
                    "source span "
                            + span
                            + " exceeds owning Truffle Source length "
                            + source.getLength()
                            + " for "
                            + source.getName());
        }
    }

    private static void emitExpression(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression) {
        if (expression instanceof CanonicalLiteral literal) {
            builder.emitLoadConstant(materialize(literal));
            return;
        }
        if (expression instanceof CanonicalLookup lookup) {
            emitLookup(builder, lookup);
            return;
        }
        throw new AssertionError(
                "validated value-shaped Bytecode expression became unsupported: "
                        + expression.getClass().getSimpleName());
    }

    private static void emitLookup(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalLookup lookup) {
        builder.beginLookup();
        builder.emitLoadArgument(0);
        builder.emitLoadConstant(lookup.name());
        builder.endLookup();
    }

    private static void emitComposedSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (result == null
                || preparedCall == null
                || childResult == null
                || resumeValue == null) {
            throw new AssertionError(
                    "Bytecode send result/scratch locals were not allocated");
        }

        CanonicalExpression receiver = send.receiver();
        boolean stageReceiver =
                receiver instanceof CanonicalCall
                        || receiver instanceof CanonicalSend;
        boolean stageArguments = hasComposedArgument(send.arguments());
        boolean stageInputs = stageReceiver || stageArguments;
        BytecodeLocal receiverValue = null;
        java.util.List<BytecodeLocal> argumentValues = java.util.List.of();

        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            receiverValue = builder.createLocal("sendReceiver", null);
            if (stageReceiver) {
                emitBodyExpressionToLocal(
                        builder,
                        receiver,
                        receiverValue,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                builder.beginStoreLocal(receiverValue);
                emitExpression(builder, receiver);
                builder.endStoreLocal();
            }

            argumentValues = new java.util.ArrayList<>(send.arguments().size());
            for (CanonicalExpression argument : send.arguments()) {
                BytecodeLocal argumentValue =
                        builder.createLocal("sendArgument", null);
                emitBodyExpressionToLocal(
                        builder,
                        argument,
                        argumentValue,
                        preparedCall,
                        childResult,
                        resumeValue);
                argumentValues.add(argumentValue);
            }
        }

        builder.beginStoreLocal(preparedCall);
        builder.beginPrepareSendArguments();
        if (stageInputs) {
            builder.emitLoadLocal(receiverValue);
        } else {
            emitExpression(builder, receiver);
        }
        builder.emitLoadConstant(send.message());
        builder.emitLoadArgument(0);
        if (stageInputs) {
            for (BytecodeLocal argumentValue : argumentValues) {
                builder.emitLoadLocal(argumentValue);
            }
        } else {
            for (CanonicalExpression argument : send.arguments()) {
                emitExpression(builder, argument);
            }
        }
        builder.endPrepareSendArguments();
        builder.endStoreLocal();

        builder.beginStoreLocal(childResult);
        builder.beginEnterClosureCall();
        builder.emitLoadLocal(preparedCall);
        builder.endEnterClosureCall();
        builder.endStoreLocal();

        builder.beginWhile();

        builder.beginIsContinuation();
        builder.emitLoadLocal(childResult);
        builder.endIsContinuation();

        builder.beginBlock();

        builder.beginStoreLocal(resumeValue);
        builder.beginYield();
        builder.emitLoadLocal(childResult);
        builder.endYield();
        builder.endStoreLocal();

        builder.beginStoreLocal(childResult);
        builder.beginResumeContinuation();
        builder.emitLoadLocal(childResult);
        builder.emitLoadLocal(resumeValue);
        builder.endResumeContinuation();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginFinishClosureCall();
        builder.emitLoadLocal(preparedCall);
        builder.emitLoadLocal(childResult);
        builder.endFinishClosureCall();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    private static void emitComposedCall(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCall call,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (result == null
                || preparedCall == null
                || childResult == null
                || resumeValue == null) {
            throw new AssertionError(
                    "Bytecode call result/scratch locals were not allocated");
        }

        CanonicalExpression receiver = call.receiver();
        boolean stageReceiver =
                receiver instanceof CanonicalCall
                        || receiver instanceof CanonicalSend;
        boolean stageArguments = hasComposedArgument(call.arguments());
        boolean spreadArguments = hasSpreadArgument(call.arguments());
        boolean stageInputs =
                stageReceiver
                        || stageArguments
                        || spreadArguments;
        BytecodeLocal receiverValue = null;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues =
                java.util.List.of();

        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            receiverValue =
                    builder.createLocal(
                            "callReceiver",
                            null);
            if (stageReceiver) {
                emitBodyExpressionToLocal(
                        builder,
                        receiver,
                        receiverValue,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                builder.beginStoreLocal(receiverValue);
                emitExpression(builder, receiver);
                builder.endStoreLocal();
            }

            if (spreadArguments) {
                suppliedVector =
                        builder.createLocal(
                                "suppliedArgumentVector",
                                null);
                emitBodySpreadArgumentVector(
                        builder,
                        call.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues =
                        new java.util.ArrayList<>(
                                call.arguments().size());
                for (CanonicalExpression argument :
                        call.arguments()) {
                    BytecodeLocal argumentValue =
                            builder.createLocal(
                                    "callArgument",
                                    null);
                    emitBodyExpressionToLocal(
                            builder,
                            argument,
                            argumentValue,
                            preparedCall,
                            childResult,
                            resumeValue);
                    argumentValues.add(argumentValue);
                }
            }
        }

        builder.beginStoreLocal(preparedCall);
        if (spreadArguments) {
            builder.beginPrepareClosureCallVector();
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareClosureCallVector();
        } else if (call.arguments().isEmpty()) {
            builder.beginPrepareClosureCall();
            if (stageInputs) {
                builder.emitLoadLocal(receiverValue);
            } else {
                emitExpression(builder, receiver);
            }
            builder.emitLoadArgument(0);
            builder.endPrepareClosureCall();
        } else {
            builder.beginPrepareClosureCallArguments();
            if (stageInputs) {
                builder.emitLoadLocal(receiverValue);
            } else {
                emitExpression(builder, receiver);
            }
            builder.emitLoadArgument(0);
            if (stageInputs) {
                for (BytecodeLocal argumentValue :
                        argumentValues) {
                    builder.emitLoadLocal(argumentValue);
                }
            } else {
                for (CanonicalExpression argument :
                        call.arguments()) {
                    emitExpression(builder, argument);
                }
            }
            builder.endPrepareClosureCallArguments();
        }
        builder.endStoreLocal();

        builder.beginStoreLocal(childResult);
        builder.beginEnterClosureCall();
        builder.emitLoadLocal(preparedCall);
        builder.endEnterClosureCall();
        builder.endStoreLocal();

        builder.beginWhile();

        builder.beginIsContinuation();
        builder.emitLoadLocal(childResult);
        builder.endIsContinuation();

        builder.beginBlock();

        builder.beginStoreLocal(resumeValue);
        builder.beginYield();
        builder.emitLoadLocal(childResult);
        builder.endYield();
        builder.endStoreLocal();

        builder.beginStoreLocal(childResult);
        builder.beginResumeContinuation();
        builder.emitLoadLocal(childResult);
        builder.emitLoadLocal(resumeValue);
        builder.endResumeContinuation();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endWhile();

        /*
         * A composed call is statement-shaped in the Bytecode builder: prepare,
         * enter, yield/resume and finish are sibling operations. Only the final
         * FinishClosureCall is value-producing, so only that operation occupies
         * the StoreLocal child slot.
         */
        builder.beginStoreLocal(result);
        builder.beginFinishClosureCall();
        builder.emitLoadLocal(preparedCall);
        builder.emitLoadLocal(childResult);
        builder.endFinishClosureCall();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    private static Object materialize(CanonicalLiteral literal) {
        return switch (literal.kind()) {
            case NUMBER -> ProtosNumberLiteral.materialize(literal.value());
            case STRING -> new ProtosStringValue(literal.value());
            case TRUE -> ProtosBooleanValue.TRUE;
            case FALSE -> ProtosBooleanValue.FALSE;
            case NULL -> ProtosNullValue.INSTANCE;
        };
    }
}
