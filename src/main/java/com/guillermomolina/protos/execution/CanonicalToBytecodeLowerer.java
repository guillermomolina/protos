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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosNumberLiteral;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCompose;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalIndexedAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMember;
import com.guillermomolina.protos.semantic.ast.CanonicalNotIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalReturn;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import com.guillermomolina.protos.semantic.ast.CanonicalSuperSend;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;

/**
 * Parallel canonical-to-Bytecode lowering seam for the PERF006-B migration.
 *
 * <p>PERF006-B2D5C completes B2 invocation composition by supporting
 * caller-supplied spread in call/send default expressions as well as Closure
 * bodies. Body and default paths share the same backend-private supplied-vector
 * representation and the same immediate shallow Array snapshot rule at each
 * spread item's exact left-to-right position. Non-spread body/default fast paths
 * remain unchanged.
 * PERF006-B6A1 extends the same C-prime lowering with read-only member,
 * identity and execution-intrinsic expressions. PERF006-B6A2 adds canonical
 * slot creation/assignment and indexed assignment while preserving the AST
 * validation/evaluation order and composing ordinary atPut dispatch through
 * the same continuation backend. PERF006-B6A3A adds canonical super sends,
 * preserving physical method-home lookup, dynamic receiver identity, spread
 * argument semantics and C-prime suspension composition. PERF006-B6A3B adds
 * Closure literal materialization with exact lexical/receiver/method-home/
 * return-home/prelude capture and a pre-lowered Bytecode execution-plan
 * template per canonical Closure position. PERF006-B6A3C adds Object
 * literals and contextual composition using a pre-lowered child Bytecode
 * root per Object position plus a non-Closure construction carrier, so
 * construction activation/lexical-capture semantics and suspension are
 * preserved without manufacturing ReturnHome ownership. The ordinary
 * {@link ProtosSourceCompiler}
 * remains on the established AST lowerer until the remaining canonical forms
 * are migrated and B6 performs the production cutover.</p>
 */
final class CanonicalToBytecodeLowerer {
    private final ProtosLanguage language;
    private final Source source;
    private final java.util.IdentityHashMap<CanonicalClosure, ProtosClosureExecutionPlan>
            bytecodeClosurePlans = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<CanonicalObject, RootCallTarget>
            bytecodeObjectBodyTargets = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<CanonicalCompose, java.util.Set<String>>
            bytecodeComposeReservedNames = new java.util.IdentityHashMap<>();

    CanonicalToBytecodeLowerer(ProtosLanguage language, Source source) {
        this.language = Objects.requireNonNull(language, "language");
        this.source = Objects.requireNonNull(source, "source");
    }

    private ProtosClosureExecutionPlan bytecodeClosurePlan(
            CanonicalClosure definition) {
        ProtosClosureExecutionPlan existing = bytecodeClosurePlans.get(definition);
        if (existing != null) {
            return existing;
        }
        ProtosClosureExecutionPlan plan =
                ProtosClosureExecutionPlan.bytecode(definition, language, source);
        bytecodeClosurePlans.put(definition, plan);
        return plan;
    }

    private RootCallTarget bytecodeObjectBodyTarget(
            CanonicalObject object) {
        RootCallTarget existing = bytecodeObjectBodyTargets.get(object);
        if (existing != null) {
            return existing;
        }

        java.util.Set<String> reservedNames = object.reservedLocalSlotNames();
        for (CanonicalExpression expression : object.body().expressions()) {
            if (expression instanceof CanonicalCompose compose) {
                java.util.Set<String> previous =
                        bytecodeComposeReservedNames.put(compose, reservedNames);
                if (previous != null && !previous.equals(reservedNames)) {
                    throw new IllegalStateException(
                            "canonical composition item belongs to multiple object bodies");
                }
            }
        }

        RootCallTarget target = lowerRoot(object.body(), null).getCallTarget();
        bytecodeObjectBodyTargets.put(object, target);
        return target;
    }

    private java.util.Set<String> composeReservedNames(
            CanonicalCompose compose) {
        java.util.Set<String> reservedNames = bytecodeComposeReservedNames.get(compose);
        if (reservedNames == null) {
            throw new AssertionError(
                    "contextual composition item was not registered by its object body");
        }
        return reservedNames;
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
                                                        CanonicalToBytecodeLowerer::requiresComposedInvocation);
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

                                    if (requiresComposedInvocation(expression)) {
                                        emitBodyExpressionToLocal(
                                                builder,
                                                expression,
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
                || expression instanceof CanonicalLookup
                || expression instanceof CanonicalIntrinsic) {
            return;
        }
        if (expression instanceof CanonicalClosure closure) {
            bytecodeClosurePlan(closure);
            return;
        }
        if (expression instanceof CanonicalObject object) {
            object.parent().ifPresent(this::validateSupportedDefaultExpression);
            bytecodeObjectBodyTarget(object);
            return;
        }
        if (expression instanceof CanonicalMember member) {
            validateSupportedDefaultExpression(member.receiver());
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            validateSupportedDefaultExpression(identity.left());
            validateSupportedDefaultExpression(identity.right());
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            validateSupportedDefaultExpression(identity.left());
            validateSupportedDefaultExpression(identity.right());
            return;
        }
        if (expression instanceof CanonicalCreate create) {
            create.target().ifPresent(this::validateSupportedDefaultExpression);
            validateSupportedDefaultExpression(create.value());
            return;
        }
        if (expression instanceof CanonicalAssign assign) {
            assign.target().ifPresent(this::validateSupportedDefaultExpression);
            validateSupportedDefaultExpression(assign.value());
            return;
        }
        if (expression instanceof CanonicalIndexedAssign indexedAssign) {
            validateSupportedDefaultExpression(indexedAssign.receiver());
            validateSupportedDefaultExpression(indexedAssign.index());
            validateSupportedDefaultExpression(indexedAssign.value());
            return;
        }
        if (expression instanceof CanonicalCall call) {
            validateSupportedDefaultExpression(call.receiver());
            for (CanonicalExpression argument : call.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedDefaultExpression(
                            spread.expression());
                } else {
                    validateSupportedDefaultExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalSend send) {
            validateSupportedDefaultExpression(send.receiver());
            for (CanonicalExpression argument : send.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedDefaultExpression(
                            spread.expression());
                } else {
                    validateSupportedDefaultExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalSuperSend send) {
            for (CanonicalExpression argument : send.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedDefaultExpression(spread.expression());
                } else {
                    validateSupportedDefaultExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalReturn returnExpression) {
            validateSupportedDefaultExpression(returnExpression.value());
            return;
        }
        throw new UnsupportedOperationException(
                "PERF006-B2C3B3 default expression is not migrated: "
                        + expression.getClass().getSimpleName());
    }

    private static boolean hasComposedDefault(CanonicalClosure definition) {
        return definition.parameters().stream()
                .flatMap(parameter -> parameter.defaultValue().stream())
                .anyMatch(CanonicalToBytecodeLowerer::requiresComposedInvocation);
    }

    private void emitClosureParameterBindings(
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
                } else if (defaultExpression instanceof CanonicalReturn
                        || requiresComposedInvocation(defaultExpression)) {
                    emitDefaultExpressionToLocal(
                            builder,
                            defaultExpression,
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

    private void emitBindDefaultLocal(
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
                .map(CanonicalToBytecodeLowerer::suppliedArgumentExpression)
                .anyMatch(CanonicalToBytecodeLowerer::requiresComposedInvocation);
    }

    private static boolean requiresComposedInvocation(
            CanonicalExpression expression) {
        if (expression instanceof CanonicalCall
                || expression instanceof CanonicalSend
                || expression instanceof CanonicalSuperSend
                || expression instanceof CanonicalReturn
                || expression instanceof CanonicalObject
                || expression instanceof CanonicalCompose) {
            return true;
        }
        if (expression instanceof CanonicalMember member) {
            return requiresComposedInvocation(member.receiver());
        }
        if (expression instanceof CanonicalIdentity identity) {
            return requiresComposedInvocation(identity.left())
                    || requiresComposedInvocation(identity.right());
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            return requiresComposedInvocation(identity.left())
                    || requiresComposedInvocation(identity.right());
        }
        if (expression instanceof CanonicalCreate
                || expression instanceof CanonicalAssign
                || expression instanceof CanonicalIndexedAssign) {
            return true;
        }
        return false;
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

    private void emitBodySpreadArgumentVector(
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

    private void emitDefaultSpreadArgumentVector(
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
                                    ? "defaultSpreadArgumentValue"
                                    : "defaultArgumentValue",
                            null);
            emitDefaultExpressionToLocal(
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

    private void emitBodyExpressionToLocal(
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
        if (expression instanceof CanonicalSuperSend send) {
            builder.beginSourceSection(
                    send.span().startOffset(),
                    send.span().length());
            emitComposedSuperSend(
                    builder,
                    send,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.endSourceSection();
            return;
        }
        if (expression instanceof CanonicalObject object) {
            emitBodyObjectLiteral(
                    builder,
                    object,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalCompose compose) {
            emitBodyCompose(
                    builder,
                    compose,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalReturn returnExpression) {
            emitBodyExpressionToLocal(
                    builder,
                    returnExpression.value(),
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.beginStoreLocal(target);
            builder.beginRaiseNonLocalReturn();
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(target);
            builder.endRaiseNonLocalReturn();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalMember member) {
            BytecodeLocal receiverValue = builder.createLocal("memberReceiver", null);
            emitBodyExpressionToLocal(
                    builder, member.receiver(), receiverValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginReadMember();
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadConstant(member.name());
            builder.endReadMember();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            BytecodeLocal leftValue = builder.createLocal("identityLeft", null);
            BytecodeLocal rightValue = builder.createLocal("identityRight", null);
            emitBodyExpressionToLocal(
                    builder, identity.left(), leftValue, preparedCall, childResult, resumeValue);
            emitBodyExpressionToLocal(
                    builder, identity.right(), rightValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginIdentity();
            builder.emitLoadLocal(leftValue);
            builder.emitLoadLocal(rightValue);
            builder.endIdentity();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            BytecodeLocal leftValue = builder.createLocal("notIdentityLeft", null);
            BytecodeLocal rightValue = builder.createLocal("notIdentityRight", null);
            emitBodyExpressionToLocal(
                    builder, identity.left(), leftValue, preparedCall, childResult, resumeValue);
            emitBodyExpressionToLocal(
                    builder, identity.right(), rightValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginNotIdentity();
            builder.emitLoadLocal(leftValue);
            builder.emitLoadLocal(rightValue);
            builder.endNotIdentity();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalCreate create) {
            emitBodyCreate(
                    builder, create, target, preparedCall, childResult, resumeValue);
            return;
        }
        if (expression instanceof CanonicalAssign assign) {
            emitBodyAssign(
                    builder, assign, target, preparedCall, childResult, resumeValue);
            return;
        }
        if (expression instanceof CanonicalIndexedAssign indexedAssign) {
            emitBodyIndexedAssign(
                    builder, indexedAssign, target, preparedCall, childResult, resumeValue);
            return;
        }
        builder.beginStoreLocal(target);
        emitExpression(builder, expression);
        builder.endStoreLocal();
    }

    private void emitDefaultExpressionToLocal(
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
        if (expression instanceof CanonicalSuperSend send) {
            emitComposedDefaultSuperSend(
                    builder,
                    send,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalObject object) {
            emitDefaultObjectLiteral(
                    builder,
                    object,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalReturn returnExpression) {
            emitDefaultExpressionToLocal(
                    builder,
                    returnExpression.value(),
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.beginStoreLocal(target);
            builder.beginRaiseNonLocalReturn();
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(target);
            builder.endRaiseNonLocalReturn();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalMember member) {
            BytecodeLocal receiverValue = builder.createLocal("defaultMemberReceiver", null);
            emitDefaultExpressionToLocal(
                    builder, member.receiver(), receiverValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginReadMember();
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadConstant(member.name());
            builder.endReadMember();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            BytecodeLocal leftValue = builder.createLocal("defaultIdentityLeft", null);
            BytecodeLocal rightValue = builder.createLocal("defaultIdentityRight", null);
            emitDefaultExpressionToLocal(
                    builder, identity.left(), leftValue, preparedCall, childResult, resumeValue);
            emitDefaultExpressionToLocal(
                    builder, identity.right(), rightValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginIdentity();
            builder.emitLoadLocal(leftValue);
            builder.emitLoadLocal(rightValue);
            builder.endIdentity();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            BytecodeLocal leftValue = builder.createLocal("defaultNotIdentityLeft", null);
            BytecodeLocal rightValue = builder.createLocal("defaultNotIdentityRight", null);
            emitDefaultExpressionToLocal(
                    builder, identity.left(), leftValue, preparedCall, childResult, resumeValue);
            emitDefaultExpressionToLocal(
                    builder, identity.right(), rightValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginNotIdentity();
            builder.emitLoadLocal(leftValue);
            builder.emitLoadLocal(rightValue);
            builder.endNotIdentity();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalCreate create) {
            emitDefaultCreate(
                    builder, create, target, preparedCall, childResult, resumeValue);
            return;
        }
        if (expression instanceof CanonicalAssign assign) {
            emitDefaultAssign(
                    builder, assign, target, preparedCall, childResult, resumeValue);
            return;
        }
        if (expression instanceof CanonicalIndexedAssign indexedAssign) {
            emitDefaultIndexedAssign(
                    builder, indexedAssign, target, preparedCall, childResult, resumeValue);
            return;
        }
        builder.beginStoreLocal(target);
        emitExpression(builder, expression);
        builder.endStoreLocal();
    }

    private void emitBodyObjectLiteral(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalObject object,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(result, preparedCall, childResult, resumeValue);
        BytecodeLocal parent = builder.createLocal("objectParent", null);
        BytecodeLocal construction = builder.createLocal("preparedObjectConstruction", null);

        if (object.parent().isPresent()) {
            emitBodyExpressionToLocal(
                    builder,
                    object.parent().orElseThrow(),
                    parent,
                    preparedCall,
                    childResult,
                    resumeValue);
        } else {
            builder.beginStoreLocal(parent);
            builder.emitLoadConstant(ProtosObjectValue.rootObject());
            builder.endStoreLocal();
        }

        builder.beginStoreLocal(construction);
        builder.beginPrepareObjectConstruction();
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(parent);
        builder.emitLoadConstant(bytecodeObjectBodyTarget(object));
        builder.endPrepareObjectConstruction();
        builder.endStoreLocal();

        emitPreparedObjectConstruction(
                builder,
                result,
                construction,
                childResult,
                resumeValue);
    }

    private void emitDefaultObjectLiteral(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalObject object,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(result, preparedCall, childResult, resumeValue);
        BytecodeLocal parent = builder.createLocal("defaultObjectParent", null);
        BytecodeLocal construction = builder.createLocal("defaultPreparedObjectConstruction", null);

        if (object.parent().isPresent()) {
            emitDefaultExpressionToLocal(
                    builder,
                    object.parent().orElseThrow(),
                    parent,
                    preparedCall,
                    childResult,
                    resumeValue);
        } else {
            builder.beginStoreLocal(parent);
            builder.emitLoadConstant(ProtosObjectValue.rootObject());
            builder.endStoreLocal();
        }

        builder.beginStoreLocal(construction);
        builder.beginPrepareObjectConstruction();
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(parent);
        builder.emitLoadConstant(bytecodeObjectBodyTarget(object));
        builder.endPrepareObjectConstruction();
        builder.endStoreLocal();

        emitPreparedObjectConstruction(
                builder,
                result,
                construction,
                childResult,
                resumeValue);
    }

    private void emitBodyCompose(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCompose compose,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal sourceValue = builder.createLocal("compositionSource", null);
        emitBodyExpressionToLocal(
                builder,
                compose.object(),
                sourceValue,
                preparedCall,
                childResult,
                resumeValue);
        builder.beginStoreLocal(result);
        builder.beginComposeLocalSlots();
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(sourceValue);
        builder.emitLoadConstant(composeReservedNames(compose));
        builder.endComposeLocalSlots();
        builder.endStoreLocal();
    }

    private void emitPreparedObjectConstruction(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal construction,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginStoreLocal(childResult);
        builder.beginEnterObjectConstruction();
        builder.emitLoadLocal(construction);
        builder.endEnterObjectConstruction();
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
        builder.beginResumeObjectConstruction();
        builder.emitLoadLocal(construction);
        builder.emitLoadLocal(childResult);
        builder.emitLoadLocal(resumeValue);
        builder.endResumeObjectConstruction();
        builder.endStoreLocal();
        builder.endBlock();
        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginFinishObjectConstruction();
        builder.emitLoadLocal(construction);
        builder.emitLoadLocal(childResult);
        builder.endFinishObjectConstruction();
        builder.endStoreLocal();
    }

    private void emitBodyCreate(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCreate create,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal value = builder.createLocal("createValue", null);
        if (create.target().isEmpty()) {
            emitBodyExpressionToLocal(
                    builder, create.value(), value, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(result);
            builder.beginCreateLocalSlot();
            builder.emitLoadArgument(0);
            builder.beginLoadIntrinsic();
            builder.emitLoadArgument(0);
            builder.emitLoadConstant(CanonicalIntrinsic.Kind.CONTEXT);
            builder.endLoadIntrinsic();
            builder.emitLoadConstant(create.name());
            builder.emitLoadLocal(value);
            builder.endCreateLocalSlot();
            builder.endStoreLocal();
            return;
        }

        BytecodeLocal rawTarget = builder.createLocal("createRawTarget", null);
        BytecodeLocal mutationTarget = builder.createLocal("createMutationTarget", null);
        emitBodyExpressionToLocal(
                builder,
                create.target().orElseThrow(),
                rawTarget,
                preparedCall,
                childResult,
                resumeValue);
        builder.beginStoreLocal(mutationTarget);
        builder.beginRequireObjectMutationTarget();
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(rawTarget);
        builder.endRequireObjectMutationTarget();
        builder.endStoreLocal();
        emitBodyExpressionToLocal(
                builder, create.value(), value, preparedCall, childResult, resumeValue);
        builder.beginStoreLocal(result);
        builder.beginCreateLocalSlot();
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(mutationTarget);
        builder.emitLoadConstant(create.name());
        builder.emitLoadLocal(value);
        builder.endCreateLocalSlot();
        builder.endStoreLocal();
    }

    private void emitBodyAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal value = builder.createLocal("assignValue", null);
        BytecodeLocal mutationTarget = builder.createLocal("assignMutationTarget", null);

        if (assign.target().isPresent()) {
            BytecodeLocal rawTarget = builder.createLocal("assignRawTarget", null);
            emitBodyExpressionToLocal(
                    builder,
                    assign.target().orElseThrow(),
                    rawTarget,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.beginStoreLocal(mutationTarget);
            builder.beginRequireObjectMutationTarget();
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(rawTarget);
            builder.endRequireObjectMutationTarget();
            builder.endStoreLocal();
        } else {
            /* AST authority resolves the writable lexical destination before RHS evaluation. */
            builder.beginStoreLocal(mutationTarget);
            builder.beginResolveWritableLexicalContext();
            builder.emitLoadArgument(0);
            builder.emitLoadConstant(assign.name());
            builder.endResolveWritableLexicalContext();
            builder.endStoreLocal();
        }

        emitBodyExpressionToLocal(
                builder, assign.value(), value, preparedCall, childResult, resumeValue);
        builder.beginStoreLocal(result);
        builder.beginAssignLocalSlot();
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(mutationTarget);
        builder.emitLoadConstant(assign.name());
        builder.emitLoadLocal(value);
        builder.endAssignLocalSlot();
        builder.endStoreLocal();
    }

    private void emitBodyIndexedAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalIndexedAssign indexedAssign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal receiver = builder.createLocal("indexedAssignReceiver", null);
        BytecodeLocal index = builder.createLocal("indexedAssignIndex", null);
        BytecodeLocal value = builder.createLocal("indexedAssignValue", null);
        BytecodeLocal dispatchResult = builder.createLocal("indexedAssignDispatchResult", null);

        emitBodyExpressionToLocal(
                builder,
                indexedAssign.receiver(),
                receiver,
                preparedCall,
                childResult,
                resumeValue);
        emitBodyExpressionToLocal(
                builder,
                indexedAssign.index(),
                index,
                preparedCall,
                childResult,
                resumeValue);
        emitBodyExpressionToLocal(
                builder,
                indexedAssign.value(),
                value,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(preparedCall);
        builder.beginPrepareSendArguments();
        builder.emitLoadLocal(receiver);
        builder.emitLoadConstant("atPut");
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(index);
        builder.emitLoadLocal(value);
        builder.endPrepareSendArguments();
        builder.endStoreLocal();
        emitPreparedInvocation(
                builder,
                dispatchResult,
                preparedCall,
                childResult,
                resumeValue);

        /* Indexed assignment returns the exact RHS, never the atPut result. */
        builder.beginStoreLocal(result);
        builder.emitLoadLocal(value);
        builder.endStoreLocal();
    }

    private void emitDefaultCreate(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCreate create,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal value = builder.createLocal("defaultCreateValue", null);
        if (create.target().isEmpty()) {
            emitDefaultExpressionToLocal(
                    builder, create.value(), value, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(result);
            builder.beginCreateLocalSlot();
            builder.emitLoadArgument(0);
            builder.beginLoadIntrinsic();
            builder.emitLoadArgument(0);
            builder.emitLoadConstant(CanonicalIntrinsic.Kind.CONTEXT);
            builder.endLoadIntrinsic();
            builder.emitLoadConstant(create.name());
            builder.emitLoadLocal(value);
            builder.endCreateLocalSlot();
            builder.endStoreLocal();
            return;
        }

        BytecodeLocal rawTarget = builder.createLocal("defaultCreateRawTarget", null);
        BytecodeLocal mutationTarget = builder.createLocal("defaultCreateMutationTarget", null);
        emitDefaultExpressionToLocal(
                builder,
                create.target().orElseThrow(),
                rawTarget,
                preparedCall,
                childResult,
                resumeValue);
        builder.beginStoreLocal(mutationTarget);
        builder.beginRequireObjectMutationTarget();
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(rawTarget);
        builder.endRequireObjectMutationTarget();
        builder.endStoreLocal();
        emitDefaultExpressionToLocal(
                builder, create.value(), value, preparedCall, childResult, resumeValue);
        builder.beginStoreLocal(result);
        builder.beginCreateLocalSlot();
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(mutationTarget);
        builder.emitLoadConstant(create.name());
        builder.emitLoadLocal(value);
        builder.endCreateLocalSlot();
        builder.endStoreLocal();
    }

    private void emitDefaultAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal value = builder.createLocal("defaultAssignValue", null);
        BytecodeLocal mutationTarget = builder.createLocal("defaultAssignMutationTarget", null);

        if (assign.target().isPresent()) {
            BytecodeLocal rawTarget = builder.createLocal("defaultAssignRawTarget", null);
            emitDefaultExpressionToLocal(
                    builder,
                    assign.target().orElseThrow(),
                    rawTarget,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.beginStoreLocal(mutationTarget);
            builder.beginRequireObjectMutationTarget();
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(rawTarget);
            builder.endRequireObjectMutationTarget();
            builder.endStoreLocal();
        } else {
            builder.beginStoreLocal(mutationTarget);
            builder.beginResolveWritableLexicalContext();
            builder.emitLoadArgument(0);
            builder.emitLoadConstant(assign.name());
            builder.endResolveWritableLexicalContext();
            builder.endStoreLocal();
        }

        emitDefaultExpressionToLocal(
                builder, assign.value(), value, preparedCall, childResult, resumeValue);
        builder.beginStoreLocal(result);
        builder.beginAssignLocalSlot();
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(mutationTarget);
        builder.emitLoadConstant(assign.name());
        builder.emitLoadLocal(value);
        builder.endAssignLocalSlot();
        builder.endStoreLocal();
    }

    private void emitDefaultIndexedAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalIndexedAssign indexedAssign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal receiver = builder.createLocal("defaultIndexedAssignReceiver", null);
        BytecodeLocal index = builder.createLocal("defaultIndexedAssignIndex", null);
        BytecodeLocal value = builder.createLocal("defaultIndexedAssignValue", null);
        BytecodeLocal dispatchResult = builder.createLocal("defaultIndexedAssignDispatchResult", null);

        emitDefaultExpressionToLocal(
                builder,
                indexedAssign.receiver(),
                receiver,
                preparedCall,
                childResult,
                resumeValue);
        emitDefaultExpressionToLocal(
                builder,
                indexedAssign.index(),
                index,
                preparedCall,
                childResult,
                resumeValue);
        emitDefaultExpressionToLocal(
                builder,
                indexedAssign.value(),
                value,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(preparedCall);
        builder.beginPrepareSendArguments();
        builder.emitLoadLocal(receiver);
        builder.emitLoadConstant("atPut");
        builder.emitLoadArgument(0);
        builder.emitLoadLocal(index);
        builder.emitLoadLocal(value);
        builder.endPrepareSendArguments();
        builder.endStoreLocal();
        emitPreparedInvocation(
                builder,
                dispatchResult,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(result);
        builder.emitLoadLocal(value);
        builder.endStoreLocal();
    }

    private void emitComposedSuperSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSuperSend send,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(result, preparedCall, childResult, resumeValue);
        boolean stageArguments = hasComposedArgument(send.arguments());
        boolean spreadArguments = hasSpreadArgument(send.arguments());
        boolean stageInputs = stageArguments || spreadArguments;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues = java.util.List.of();

        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            if (spreadArguments) {
                suppliedVector = builder.createLocal("superSuppliedArgumentVector", null);
                emitBodySpreadArgumentVector(
                        builder,
                        send.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues = new java.util.ArrayList<>(send.arguments().size());
                for (CanonicalExpression argument : send.arguments()) {
                    BytecodeLocal argumentValue = builder.createLocal("superArgument", null);
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
            builder.beginPrepareSuperSendVector();
            builder.emitLoadConstant(send.message());
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareSuperSendVector();
        } else {
            builder.beginPrepareSuperSendArguments();
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
            builder.endPrepareSuperSendArguments();
        }
        builder.endStoreLocal();

        emitPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    private void emitComposedDefaultSuperSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSuperSend send,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(result, preparedCall, childResult, resumeValue);
        boolean stageArguments = hasComposedArgument(send.arguments());
        boolean spreadArguments = hasSpreadArgument(send.arguments());
        boolean stageInputs = stageArguments || spreadArguments;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues = java.util.List.of();

        builder.beginSourceSection(
                send.span().startOffset(),
                send.span().length());
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            if (spreadArguments) {
                suppliedVector = builder.createLocal("defaultSuperSuppliedArgumentVector", null);
                emitDefaultSpreadArgumentVector(
                        builder,
                        send.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues = new java.util.ArrayList<>(send.arguments().size());
                for (CanonicalExpression argument : send.arguments()) {
                    BytecodeLocal argumentValue = builder.createLocal("defaultSuperArgument", null);
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
        }

        builder.beginStoreLocal(preparedCall);
        if (spreadArguments) {
            builder.beginPrepareSuperSendVector();
            builder.emitLoadConstant(send.message());
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareSuperSendVector();
        } else {
            builder.beginPrepareSuperSendArguments();
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
            builder.endPrepareSuperSendArguments();
        }
        builder.endStoreLocal();

        emitPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
        builder.endSourceSection();
    }

    private void emitComposedDefaultCall(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCall call,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(
                result,
                preparedCall,
                childResult,
                resumeValue);
        CanonicalExpression receiver = call.receiver();
        boolean stageReceiver = requiresComposedInvocation(receiver);
        boolean stageArguments =
                hasComposedArgument(call.arguments());
        boolean spreadArguments =
                hasSpreadArgument(call.arguments());
        boolean stageInputs =
                stageReceiver
                        || stageArguments
                        || spreadArguments;
        BytecodeLocal receiverValue = null;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues =
                java.util.List.of();

        builder.beginSourceSection(
                call.span().startOffset(),
                call.span().length());
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            receiverValue =
                    builder.createLocal(
                            "defaultCallReceiver",
                            null);
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

            if (spreadArguments) {
                suppliedVector =
                        builder.createLocal(
                                "defaultSuppliedArgumentVector",
                                null);
                emitDefaultSpreadArgumentVector(
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
                                    "defaultCallArgument",
                                    null);
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
        }

        builder.beginStoreLocal(preparedCall);
        if (spreadArguments) {
            builder.beginPrepareClosureCallVector();
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareClosureCallVector();
        } else {
            builder.beginPrepareDefaultClosureCallArguments();
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
            builder.endPrepareDefaultClosureCallArguments();
        }
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

    private void emitComposedDefaultSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(
                result,
                preparedCall,
                childResult,
                resumeValue);
        CanonicalExpression receiver = send.receiver();
        boolean stageReceiver = requiresComposedInvocation(receiver);
        boolean stageArguments =
                hasComposedArgument(send.arguments());
        boolean spreadArguments =
                hasSpreadArgument(send.arguments());
        boolean stageInputs =
                stageReceiver
                        || stageArguments
                        || spreadArguments;
        BytecodeLocal receiverValue = null;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues =
                java.util.List.of();

        builder.beginSourceSection(
                send.span().startOffset(),
                send.span().length());
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            receiverValue =
                    builder.createLocal(
                            "defaultSendReceiver",
                            null);
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

            if (spreadArguments) {
                suppliedVector =
                        builder.createLocal(
                                "defaultSuppliedArgumentVector",
                                null);
                emitDefaultSpreadArgumentVector(
                        builder,
                        send.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues =
                        new java.util.ArrayList<>(
                                send.arguments().size());
                for (CanonicalExpression argument :
                        send.arguments()) {
                    BytecodeLocal argumentValue =
                            builder.createLocal(
                                    "defaultSendArgument",
                                    null);
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
        }

        builder.beginStoreLocal(preparedCall);
        if (spreadArguments) {
            builder.beginPrepareSendVector();
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadConstant(send.message());
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareSendVector();
        } else {
            builder.beginPrepareSendArguments();
            if (stageInputs) {
                builder.emitLoadLocal(receiverValue);
            } else {
                emitExpression(builder, receiver);
            }
            builder.emitLoadConstant(send.message());
            builder.emitLoadArgument(0);
            if (stageInputs) {
                for (BytecodeLocal argumentValue :
                        argumentValues) {
                    builder.emitLoadLocal(argumentValue);
                }
            } else {
                for (CanonicalExpression argument :
                        send.arguments()) {
                    emitExpression(builder, argument);
                }
            }
            builder.endPrepareSendArguments();
        }
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

    private void emitComposedPreparedDefaultInvocation(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        emitPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
    }

    private void emitPreparedInvocation(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(
                result,
                preparedCall,
                childResult,
                resumeValue);

        BytecodeLocal structuredEnsure =
                builder.createLocal("structuredEnsureCall", null);
        BytecodeLocal structuredEnsureCancellationWasUnwinding =
                builder.createLocal("structuredEnsureCancellationWasUnwinding", null);
        BytecodeLocal structuredChild =
                builder.createLocal("structuredEnsureChildCall", null);
        BytecodeLocal structuredHandler =
                builder.createLocal("structuredErrorHandlerCall", null);
        BytecodeLocal structuredHandlerChild =
                builder.createLocal("structuredErrorHandlerChildCall", null);
        BytecodeLocal structuredWhile =
                builder.createLocal("structuredWhileCall", null);
        BytecodeLocal structuredWhileChild =
                builder.createLocal("structuredWhileChildCall", null);
        BytecodeLocal structuredWhileConditionResult =
                builder.createLocal("structuredWhileConditionResult", null);
        BytecodeLocal structuredBoolean =
                builder.createLocal("structuredBooleanCall", null);
        BytecodeLocal structuredBooleanChild =
                builder.createLocal("structuredBooleanChildCall", null);
        BytecodeLocal structuredArrayEach =
                builder.createLocal("structuredArrayEachCall", null);
        BytecodeLocal structuredArrayEachChild =
                builder.createLocal("structuredArrayEachChildCall", null);
        BytecodeLocal structuredBytesEach =
                builder.createLocal("structuredBytesEachCall", null);
        BytecodeLocal structuredBytesEachChild =
                builder.createLocal("structuredBytesEachChildCall", null);
        BytecodeLocal structuredProcessArgumentsEach =
                builder.createLocal("structuredProcessArgumentsEachCall", null);
        BytecodeLocal structuredProcessArgumentsEachChild =
                builder.createLocal("structuredProcessArgumentsEachChildCall", null);
        BytecodeLocal structuredEnvironmentEach =
                builder.createLocal("structuredEnvironmentEachCall", null);
        BytecodeLocal structuredEnvironmentEachChild =
                builder.createLocal("structuredEnvironmentEachChildCall", null);
        BytecodeLocal structuredIdentityMapEach =
                builder.createLocal("structuredIdentityMapEachCall", null);
        BytecodeLocal structuredIdentityMapEachChild =
                builder.createLocal("structuredIdentityMapEachChildCall", null);
        BytecodeLocal structuredMapEach =
                builder.createLocal("structuredMapEachCall", null);
        BytecodeLocal structuredMapEachChild =
                builder.createLocal("structuredMapEachChildCall", null);
        BytecodeLocal structuredMapReadLookup =
                builder.createLocal("structuredMapReadLookupCall", null);
        BytecodeLocal structuredMapReadLookupChild =
                builder.createLocal("structuredMapReadLookupChildCall", null);

        builder.beginIfThenElse();

        builder.beginIsStructuredEnsureCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredEnsureCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        /* Validation precedes the protected semantic extent. */
        builder.beginStoreLocal(structuredEnsure);
        builder.beginPrepareStructuredEnsureCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredEnsureCall();
        builder.endStoreLocal();

        builder.beginTryFinally(
                () -> {
                    /*
                     * Snapshot cancellation ownership before cleanup runs.
                     * A cancellation initiated by cleanup is itself the later
                     * transfer and must supersede the body's pending
                     * error/return/normal outcome. Only cancellation that was
                     * already UNWINDING on entry to cleanup may be superseded
                     * by a still-later cleanup transfer.
                     */
                    builder.beginStoreLocal(
                            structuredEnsureCancellationWasUnwinding);
                    builder.beginIsCancellationUnwindActive();
                    builder.emitLoadArgument(0);
                    builder.endIsCancellationUnwindActive();
                    builder.endStoreLocal();

                    /*
                     * TryCatch is deliberately inside the generated finally.
                     * It sees only a later transfer escaping cleanup; the
                     * original pending transfer is rethrown by the outer
                     * TryFinally after this generator returns.
                     */
                    builder.beginTryCatch();

                    builder.beginBlock();
                    builder.beginStoreLocal(structuredChild);
                    builder.beginLoadStructuredEnsureCleanupCall();
                    builder.emitLoadLocal(structuredEnsure);
                    builder.endLoadStructuredEnsureCleanupCall();
                    builder.endStoreLocal();
                    emitScopedOrdinaryPreparedInvocation(
                            builder,
                            childResult,
                            structuredChild,
                            childResult,
                            resumeValue);
                    builder.endBlock();

                    builder.beginBlock();
                    builder.beginSupersedeCancellationUnwindIfActive();
                    builder.emitLoadArgument(0);
                    builder.emitLoadLocal(
                            structuredEnsureCancellationWasUnwinding);
                    builder.endSupersedeCancellationUnwindIfActive();
                    builder.beginRethrowTruffleException();
                    builder.emitLoadException();
                    builder.endRethrowTruffleException();
                    builder.endBlock();

                    builder.endTryCatch();
                });
        builder.beginBlock();
        builder.beginStoreLocal(structuredChild);
        builder.beginLoadStructuredEnsureBodyCall();
        builder.emitLoadLocal(structuredEnsure);
        builder.endLoadStructuredEnsureBodyCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                result,
                structuredChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredErrorHandlerCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredErrorHandlerCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        /*
         * Receiver/arity/body/handler validation happens before frame
         * installation. The returned descriptor owns exactly one Task/direct
         * dynamic handler token for the protected extent.
         */
        builder.beginStoreLocal(structuredHandler);
        builder.beginPrepareStructuredErrorHandlerCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredErrorHandlerCall();
        builder.endStoreLocal();

        builder.beginTryFinally(
                () -> {
                    builder.beginLeaveStructuredErrorHandlerFrame();
                    builder.emitLoadLocal(structuredHandler);
                    builder.endLeaveStructuredErrorHandlerFrame();
                });
        builder.beginBlock();

        /*
         * Bytecode DSL TryCatch is a void operation. Each branch writes the
         * semantic Error.handle result directly into the shared result local;
         * the TryCatch itself must not be used as a value-producing child.
         */
        builder.beginTryCatch();

        builder.beginBlock();
        builder.beginStoreLocal(structuredHandlerChild);
        builder.beginLoadStructuredErrorHandlerBodyCall();
        builder.emitLoadLocal(structuredHandler);
        builder.endLoadStructuredErrorHandlerBodyCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                result,
                structuredHandlerChild,
                childResult,
                resumeValue);
        builder.endBlock();

        builder.beginBlock();
        builder.beginStoreLocal(structuredHandlerChild);
        builder.beginPrepareSelectedStructuredErrorHandlerCall();
        builder.emitLoadLocal(structuredHandler);
        builder.emitLoadException();
        builder.endPrepareSelectedStructuredErrorHandlerCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                result,
                structuredHandlerChild,
                childResult,
                resumeValue);
        builder.endBlock();

        builder.endTryCatch();

        builder.endBlock();
        builder.endTryFinally();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredWhileCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredWhileCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        /* Validate the standard while receiver/body before the first condition activation. */
        builder.beginStoreLocal(structuredWhile);
        builder.beginPrepareStructuredWhileCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredWhileCall();
        builder.endStoreLocal();

        /*
         * PLAT021: loop phase lives in Bytecode control state. Every logical
         * condition/body activation is prepared fresh so each invocation owns
         * its own activation/ReturnHome; suspension resumes at the exact loop
         * PC without a replay checkpoint or callback compaction cursor.
         */
        builder.beginWhile();

        builder.beginBlock();
        builder.beginStoreLocal(structuredWhileChild);
        builder.beginPrepareStructuredWhileConditionCall();
        builder.emitLoadLocal(structuredWhile);
        builder.endPrepareStructuredWhileConditionCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                structuredWhileConditionResult,
                structuredWhileChild,
                childResult,
                resumeValue);
        builder.beginStructuredWhileCondition();
        builder.emitLoadLocal(structuredWhile);
        builder.emitLoadLocal(structuredWhileConditionResult);
        builder.endStructuredWhileCondition();
        builder.endBlock();

        builder.beginBlock();
        builder.beginStoreLocal(structuredWhileChild);
        builder.beginPrepareStructuredWhileBodyCall();
        builder.emitLoadLocal(structuredWhile);
        builder.endPrepareStructuredWhileBodyCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredWhileChild,
                childResult,
                resumeValue);
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.emitLoadConstant(ProtosNullValue.INSTANCE);
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredBooleanCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredBooleanCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredBoolean);
        builder.beginPrepareStructuredBooleanCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredBooleanCall();
        builder.endStoreLocal();

        builder.beginIfThenElse();
        builder.beginStructuredBooleanHasCallback();
        builder.emitLoadLocal(structuredBoolean);
        builder.endStructuredBooleanHasCallback();

        builder.beginBlock();
        builder.beginStoreLocal(structuredBooleanChild);
        builder.beginPrepareStructuredBooleanCallbackCall();
        builder.emitLoadLocal(structuredBoolean);
        builder.endPrepareStructuredBooleanCallbackCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredBooleanChild,
                childResult,
                resumeValue);
        builder.beginStoreLocal(result);
        builder.beginFinishStructuredBooleanCallback();
        builder.emitLoadLocal(structuredBoolean);
        builder.emitLoadLocal(childResult);
        builder.endFinishStructuredBooleanCallback();
        builder.endStoreLocal();
        builder.endBlock();

        builder.beginBlock();
        builder.beginStoreLocal(result);
        builder.beginStructuredBooleanImmediateResult();
        builder.emitLoadLocal(structuredBoolean);
        builder.endStructuredBooleanImmediateResult();
        builder.endStoreLocal();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredArrayEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredArrayEachCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredArrayEach);
        builder.beginPrepareStructuredArrayEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredArrayEachCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginStructuredArrayEachHasNext();
        builder.emitLoadLocal(structuredArrayEach);
        builder.endStructuredArrayEachHasNext();

        builder.beginBlock();
        builder.beginStoreLocal(structuredArrayEachChild);
        builder.beginPrepareStructuredArrayEachElementCall();
        builder.emitLoadLocal(structuredArrayEach);
        builder.endPrepareStructuredArrayEachElementCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredArrayEachChild,
                childResult,
                resumeValue);
        builder.beginAdvanceStructuredArrayEach();
        builder.emitLoadLocal(structuredArrayEach);
        builder.endAdvanceStructuredArrayEach();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginFinishStructuredArrayEach();
        builder.emitLoadLocal(structuredArrayEach);
        builder.endFinishStructuredArrayEach();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredBytesEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredBytesEachCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredBytesEach);
        builder.beginPrepareStructuredBytesEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredBytesEachCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginStructuredBytesEachHasNext();
        builder.emitLoadLocal(structuredBytesEach);
        builder.endStructuredBytesEachHasNext();

        builder.beginBlock();
        builder.beginStoreLocal(structuredBytesEachChild);
        builder.beginPrepareStructuredBytesEachElementCall();
        builder.emitLoadLocal(structuredBytesEach);
        builder.endPrepareStructuredBytesEachElementCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredBytesEachChild,
                childResult,
                resumeValue);
        builder.beginAdvanceStructuredBytesEach();
        builder.emitLoadLocal(structuredBytesEach);
        builder.endAdvanceStructuredBytesEach();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginFinishStructuredBytesEach();
        builder.emitLoadLocal(structuredBytesEach);
        builder.endFinishStructuredBytesEach();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredProcessArgumentsEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredProcessArgumentsEachCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredProcessArgumentsEach);
        builder.beginPrepareStructuredProcessArgumentsEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredProcessArgumentsEachCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginStructuredProcessArgumentsEachHasNext();
        builder.emitLoadLocal(structuredProcessArgumentsEach);
        builder.endStructuredProcessArgumentsEachHasNext();

        builder.beginBlock();
        builder.beginStoreLocal(structuredProcessArgumentsEachChild);
        builder.beginPrepareStructuredProcessArgumentsEachElementCall();
        builder.emitLoadLocal(structuredProcessArgumentsEach);
        builder.endPrepareStructuredProcessArgumentsEachElementCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredProcessArgumentsEachChild,
                childResult,
                resumeValue);
        builder.beginAdvanceStructuredProcessArgumentsEach();
        builder.emitLoadLocal(structuredProcessArgumentsEach);
        builder.endAdvanceStructuredProcessArgumentsEach();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginFinishStructuredProcessArgumentsEach();
        builder.emitLoadLocal(structuredProcessArgumentsEach);
        builder.endFinishStructuredProcessArgumentsEach();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredEnvironmentEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredEnvironmentEachCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredEnvironmentEach);
        builder.beginPrepareStructuredEnvironmentEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredEnvironmentEachCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginStructuredEnvironmentEachHasNext();
        builder.emitLoadLocal(structuredEnvironmentEach);
        builder.endStructuredEnvironmentEachHasNext();

        builder.beginBlock();
        builder.beginStoreLocal(structuredEnvironmentEachChild);
        builder.beginPrepareStructuredEnvironmentEachEntryCall();
        builder.emitLoadLocal(structuredEnvironmentEach);
        builder.endPrepareStructuredEnvironmentEachEntryCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredEnvironmentEachChild,
                childResult,
                resumeValue);
        builder.beginAdvanceStructuredEnvironmentEach();
        builder.emitLoadLocal(structuredEnvironmentEach);
        builder.endAdvanceStructuredEnvironmentEach();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginFinishStructuredEnvironmentEach();
        builder.emitLoadLocal(structuredEnvironmentEach);
        builder.endFinishStructuredEnvironmentEach();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredIdentityMapEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredIdentityMapEachCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredIdentityMapEach);
        builder.beginPrepareStructuredIdentityMapEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredIdentityMapEachCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginStructuredIdentityMapEachHasNext();
        builder.emitLoadLocal(structuredIdentityMapEach);
        builder.endStructuredIdentityMapEachHasNext();

        builder.beginBlock();
        builder.beginStoreLocal(structuredIdentityMapEachChild);
        builder.beginPrepareStructuredIdentityMapEachEntryCall();
        builder.emitLoadLocal(structuredIdentityMapEach);
        builder.endPrepareStructuredIdentityMapEachEntryCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredIdentityMapEachChild,
                childResult,
                resumeValue);
        builder.beginAdvanceStructuredIdentityMapEach();
        builder.emitLoadLocal(structuredIdentityMapEach);
        builder.endAdvanceStructuredIdentityMapEach();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginFinishStructuredIdentityMapEach();
        builder.emitLoadLocal(structuredIdentityMapEach);
        builder.endFinishStructuredIdentityMapEach();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredMapEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredMapEachCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredMapEach);
        builder.beginPrepareStructuredMapEachCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredMapEachCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginStructuredMapEachHasNext();
        builder.emitLoadLocal(structuredMapEach);
        builder.endStructuredMapEachHasNext();

        builder.beginBlock();
        builder.beginStoreLocal(structuredMapEachChild);
        builder.beginPrepareStructuredMapEachEntryCall();
        builder.emitLoadLocal(structuredMapEach);
        builder.endPrepareStructuredMapEachEntryCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredMapEachChild,
                childResult,
                resumeValue);
        builder.beginAdvanceStructuredMapEach();
        builder.emitLoadLocal(structuredMapEach);
        builder.endAdvanceStructuredMapEach();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginFinishStructuredMapEach();
        builder.emitLoadLocal(structuredMapEach);
        builder.endFinishStructuredMapEach();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredMapReadLookupCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredMapReadLookupCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredMapReadLookup);
        builder.beginPrepareStructuredMapReadLookupCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredMapReadLookupCall();
        builder.endStoreLocal();

        /* Hash callback: comparison scope spans suspension but not result validation. */
        builder.beginEnterStructuredMapReadLookupComparison();
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endEnterStructuredMapReadLookupComparison();
        builder.beginTryFinally(
                () -> {
                    builder.beginLeaveStructuredMapReadLookupComparison();
                    builder.emitLoadLocal(structuredMapReadLookup);
                    builder.endLeaveStructuredMapReadLookupComparison();
                });
        builder.beginBlock();
        builder.beginStoreLocal(structuredMapReadLookupChild);
        builder.beginPrepareStructuredMapReadLookupHashCall();
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endPrepareStructuredMapReadLookupHashCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredMapReadLookupChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();

        builder.beginAcceptStructuredMapReadLookupHashResult();
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.emitLoadLocal(childResult);
        builder.endAcceptStructuredMapReadLookupHashResult();

        /* Candidate equality callbacks repeat in insertion order over the hash-filtered snapshot. */
        builder.beginWhile();
        builder.beginStructuredMapReadLookupNeedsEquality();
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endStructuredMapReadLookupNeedsEquality();

        builder.beginBlock();
        builder.beginEnterStructuredMapReadLookupComparison();
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endEnterStructuredMapReadLookupComparison();
        builder.beginTryFinally(
                () -> {
                    builder.beginLeaveStructuredMapReadLookupComparison();
                    builder.emitLoadLocal(structuredMapReadLookup);
                    builder.endLeaveStructuredMapReadLookupComparison();
                });
        builder.beginBlock();
        builder.beginStoreLocal(structuredMapReadLookupChild);
        builder.beginPrepareStructuredMapReadLookupEqualityCall();
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endPrepareStructuredMapReadLookupEqualityCall();
        builder.endStoreLocal();
        emitScopedOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredMapReadLookupChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();

        builder.beginAcceptStructuredMapReadLookupEqualityResult();
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.emitLoadLocal(childResult);
        builder.endAcceptStructuredMapReadLookupEqualityResult();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginFinishStructuredMapReadLookup();
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endFinishStructuredMapReadLookup();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        emitOrdinaryPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
    }

    private void emitScopedOrdinaryPreparedInvocation(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();
        emitOrdinaryPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();
    }

    private void emitOrdinaryPreparedInvocation(
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
        builder.emitLoadLocal(preparedCall);
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

    private void emitBindSuppliedClosureParameter(
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
                || expression instanceof CanonicalLookup
                || expression instanceof CanonicalIntrinsic) {
            return;
        }
        if (expression instanceof CanonicalClosure closure) {
            bytecodeClosurePlan(closure);
            return;
        }
        if (expression instanceof CanonicalObject object) {
            object.parent().ifPresent(this::validateSupportedExpression);
            bytecodeObjectBodyTarget(object);
            return;
        }
        if (expression instanceof CanonicalCompose compose) {
            if (!bytecodeComposeReservedNames.containsKey(compose)) {
                throw new UnsupportedOperationException(
                        "CanonicalCompose is valid only in its registered Object body");
            }
            validateSupportedExpression(compose.object());
            return;
        }
        if (expression instanceof CanonicalMember member) {
            validateSupportedExpression(member.receiver());
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            validateSupportedExpression(identity.left());
            validateSupportedExpression(identity.right());
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            validateSupportedExpression(identity.left());
            validateSupportedExpression(identity.right());
            return;
        }
        if (expression instanceof CanonicalCreate create) {
            create.target().ifPresent(this::validateSupportedExpression);
            validateSupportedExpression(create.value());
            return;
        }
        if (expression instanceof CanonicalAssign assign) {
            assign.target().ifPresent(this::validateSupportedExpression);
            validateSupportedExpression(assign.value());
            return;
        }
        if (expression instanceof CanonicalIndexedAssign indexedAssign) {
            validateSupportedExpression(indexedAssign.receiver());
            validateSupportedExpression(indexedAssign.index());
            validateSupportedExpression(indexedAssign.value());
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
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedExpression(spread.expression());
                } else {
                    validateSupportedExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalSuperSend send) {
            for (CanonicalExpression argument : send.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedExpression(spread.expression());
                } else {
                    validateSupportedExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalReturn returnExpression) {
            validateSupportedExpression(returnExpression.value());
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

    private void emitExpression(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression) {
        if (expression instanceof CanonicalLiteral literal) {
            builder.emitLoadConstant(materialize(literal));
            return;
        }
        if (expression instanceof CanonicalClosure closure) {
            builder.beginMaterializeClosure();
            builder.emitLoadArgument(0);
            builder.emitLoadConstant(closure);
            builder.emitLoadConstant(bytecodeClosurePlan(closure));
            builder.endMaterializeClosure();
            return;
        }
        if (expression instanceof CanonicalLookup lookup) {
            emitLookup(builder, lookup);
            return;
        }
        if (expression instanceof CanonicalIntrinsic intrinsic) {
            builder.beginLoadIntrinsic();
            builder.emitLoadArgument(0);
            builder.emitLoadConstant(intrinsic.kind());
            builder.endLoadIntrinsic();
            return;
        }
        if (expression instanceof CanonicalMember member) {
            builder.beginReadMember();
            builder.emitLoadArgument(0);
            emitExpression(builder, member.receiver());
            builder.emitLoadConstant(member.name());
            builder.endReadMember();
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            builder.beginIdentity();
            emitExpression(builder, identity.left());
            emitExpression(builder, identity.right());
            builder.endIdentity();
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            builder.beginNotIdentity();
            emitExpression(builder, identity.left());
            emitExpression(builder, identity.right());
            builder.endNotIdentity();
            return;
        }
        throw new AssertionError(
                "validated value-shaped Bytecode expression became unsupported: "
                        + expression.getClass().getSimpleName());
    }

    private void emitLookup(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalLookup lookup) {
        builder.beginLookup();
        builder.emitLoadArgument(0);
        builder.emitLoadConstant(lookup.name());
        builder.endLookup();
    }

    private void emitComposedSend(
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
        boolean stageReceiver = requiresComposedInvocation(receiver);
        boolean stageArguments =
                hasComposedArgument(send.arguments());
        boolean spreadArguments =
                hasSpreadArgument(send.arguments());
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
                            "sendReceiver",
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
                        send.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues =
                        new java.util.ArrayList<>(
                                send.arguments().size());
                for (CanonicalExpression argument :
                        send.arguments()) {
                    BytecodeLocal argumentValue =
                            builder.createLocal(
                                    "sendArgument",
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
            builder.beginPrepareSendVector();
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadConstant(send.message());
            builder.emitLoadArgument(0);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareSendVector();
        } else {
            builder.beginPrepareSendArguments();
            if (stageInputs) {
                builder.emitLoadLocal(receiverValue);
            } else {
                emitExpression(builder, receiver);
            }
            builder.emitLoadConstant(send.message());
            builder.emitLoadArgument(0);
            if (stageInputs) {
                for (BytecodeLocal argumentValue :
                        argumentValues) {
                    builder.emitLoadLocal(argumentValue);
                }
            } else {
                for (CanonicalExpression argument :
                        send.arguments()) {
                    emitExpression(builder, argument);
                }
            }
            builder.endPrepareSendArguments();
        }
        builder.endStoreLocal();


        emitPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    private void emitComposedCall(
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
        boolean stageReceiver = requiresComposedInvocation(receiver);
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


        emitPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
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
