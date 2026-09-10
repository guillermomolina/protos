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
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
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
 * <p>PERF006-B2C2 supports general positional arity for source-level
 * {@link CanonicalCall} whose receiver is a lexical {@link CanonicalLookup}.
 * Arguments are evaluated left-to-right after the receiver and are collected by
 * the Bytecode DSL variadic operand mechanism. Calls compose child Bytecode
 * continuations through their caller rather than treating a child continuation
 * as a guest value.
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

                            if (activationDefinition != null) {
                                builder.beginBindClosureParameters();
                                builder.emitLoadArgument(0);
                                builder.emitLoadConstant(activationDefinition);
                                builder.endBindClosureParameters();
                            }

                            if (sequence.expressions().isEmpty()) {
                                builder.beginReturn();
                                builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                                builder.endReturn();
                            } else {
                                BytecodeLocal result =
                                        builder.createLocal("sequenceResult", null);
                                boolean hasCall =
                                        sequence.expressions().stream()
                                                .anyMatch(CanonicalCall.class::isInstance);
                                BytecodeLocal preparedCall =
                                        hasCall
                                                ? builder.createLocal(
                                                        "preparedClosureCall",
                                                        null)
                                                : null;
                                BytecodeLocal childResult =
                                        hasCall
                                                ? builder.createLocal(
                                                        "childResult",
                                                        null)
                                                : null;
                                BytecodeLocal resumeValue =
                                        hasCall
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

                                    if (expression instanceof CanonicalCall call) {
                                        emitComposedCall(
                                                builder,
                                                call,
                                                result,
                                                preparedCall,
                                                childResult,
                                                resumeValue);
                                    } else {
                                        builder.beginStoreLocal(result);
                                        emitExpression(builder, expression);
                                        builder.endStoreLocal();
                                    }

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
            if (!(call.receiver() instanceof CanonicalLookup)) {
                throw new UnsupportedOperationException(
                        "PERF006-B2C2 Bytecode Closure dispatch requires a lexical lookup receiver");
            }
            validateSupportedExpression(call.receiver());
            for (CanonicalExpression argument : call.arguments()) {
                if (!(argument instanceof CanonicalLiteral)
                        && !(argument instanceof CanonicalLookup)) {
                    throw new UnsupportedOperationException(
                            "PERF006-B2C2 call argument must be literal or lexical lookup");
                }
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

        CanonicalLookup receiver =
                (CanonicalLookup) call.receiver();

        builder.beginTag(StandardTags.CallTag.class);

        builder.beginStoreLocal(preparedCall);
        if (call.arguments().isEmpty()) {
            builder.beginPrepareClosureCall();
            emitLookup(builder, receiver);
            builder.emitLoadArgument(0);
            builder.endPrepareClosureCall();
        } else {
            builder.beginPrepareClosureCallArguments();
            emitLookup(builder, receiver);
            builder.emitLoadArgument(0);
            for (CanonicalExpression argument : call.arguments()) {
                emitExpression(builder, argument);
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
