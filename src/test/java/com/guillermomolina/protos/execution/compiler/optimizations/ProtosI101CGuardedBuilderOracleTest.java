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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I101-C focal evidence: independent Builder oracles for NORMAL roots whose specialization is a
 * dynamic guard with a semantically necessary physical fallback.
 *
 * <p>Every expected root is a handwritten {@link ProtosSemanticBytecodeRootNodeGen.Builder}
 * program. The oracles never consult the parser, the canonicalizer, the lowerer or any lowered
 * root; the shared invocation helpers below restate the physical prepared-invocation protocol
 * operation by operation. Only the actual roots come from the production {@code ParsingRequest}
 * path of an entered Context with a published Core.
 *
 * <p>Signatures carry every instruction (including tag, branch, cleanup and fallback
 * instructions) with every argument name, kind and value. Profiles carry no stable value and
 * compare by kind; metadata constants compare by their exact class.
 */
final class ProtosI101CGuardedBuilderOracleTest {
    private static final java.nio.file.Path CORE = java.nio.file.Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final String NOT_WITNESS = "(receiver) => { receiver.not() }";
    private static final String INLINE_WITNESS = "() => { (() => { 1 })() }";

    /** The I095 dynamic receiver: canonical HIT paths and the ordinary-send MISS. */
    @Test
    void i101CGuardedNotMatchesIndependentBuilderIncludingMiss() throws Exception {
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL)) {
            normal.run(() -> {
                ProtosSemanticBytecodeRootNode module = compile(NOT_WITNESS, "not-normal");
                BytecodeRootNodes<?> group = module.getRootNodes();
                List<ProtosSemanticBytecodeRootNode> roots = groupRoots(module);
                // Module and Closure roots only; D203 lowers no arity-rejection root.
                assertEquals(2, roots.size());
                assertSame(module, roots.get(0));
                ProtosSemanticBytecodeRootNode actualRoot = roots.get(1);
                List<RootCallTarget> targets =
                        roots.stream().map(RootNode::getCallTarget).toList();

                ProtosSemanticBytecodeRootNode oracle = handwrittenGuardedNotRoot();
                // The oracle is a single-root group: it has no module root.
                assertEquals(1, groupRoots(oracle).size());

                List<String> expected = completeSignature(oracle);
                List<String> actual = completeSignature(actualRoot);
                System.out.println("I101_C_NOT_ORACLE_SIGNATURE=" + expected);
                System.out.println("I101_C_NOT_ACTUAL_SIGNATURE=" + actual);
                assertEquals(expected, actual, "guarded not root vs independent Builder");

                int guard = indexOfOperation(actual, "TryCompactCanonicalBooleanOne");
                int onTrue = indexOfOperation(actual, "IsCanonicalIfTrueTrue");
                int onFalse = indexOfOperation(actual, "IsCanonicalBooleanFalseClass");
                int fallback = indexOfOperation(actual, "PrepareSendZero");
                assertTrue(guard >= 0, actual::toString);
                assertTrue(onTrue > guard, actual::toString);
                assertTrue(onFalse > onTrue, actual::toString);
                assertTrue(fallback > onFalse, actual::toString);
                assertNoOperation(actual, "IsNormalExecutionMode");

                group.ensureComplete();
                assertSame(group, module.getRootNodes());
                assertEquals(roots, groupRoots(module));
                for (int index = 0; index < roots.size(); index++) {
                    assertSame(roots.get(index), group.getNode(index));
                    assertSame(targets.get(index), roots.get(index).getCallTarget());
                }
                assertEquals(actual, signature(actualRoot));

                ProtosClosureValue closure = assertInstanceOf(
                        ProtosClosureValue.class,
                        module.getCallTarget().call(normal.moduleActivation()));
                ProtosClosureExecutionPlan plan = closure.executionPlan().orElseThrow();
                assertEquals(ProtosExecutionMode.NORMAL, plan.bytecodeProfileForTesting());
                assertSame(actualRoot, plan.bytecodeActivationRootForTesting());
                assertSame(targets.get(1), plan.bytecodeActivationTargetForComposition());
                assertNotSemantics(normal, closure, "NORMAL");
                assertEquals(actual, signature(actualRoot));
                System.out.println("I101_C_NOT_BUILDER_ORACLE=PASS");
            });
        }

        try (Harness debug = new Harness(ProtosExecutionMode.DEBUG)) {
            debug.run(() -> {
                ProtosSemanticBytecodeRootNode module = compile(NOT_WITNESS, "not-debug");
                BytecodeRootNodes<?> group = module.getRootNodes();
                List<ProtosSemanticBytecodeRootNode> roots = groupRoots(module);
                assertEquals(2, roots.size());
                ProtosSemanticBytecodeRootNode root = roots.get(1);
                RootCallTarget target = root.getCallTarget();

                List<String> signature = completeSignature(root);
                assertNoOperation(signature, "TryCompactCanonicalBooleanOne");
                assertTrue(indexOfOperation(signature, "PrepareSendZero") >= 0,
                        signature::toString);

                group.ensureComplete();
                assertSame(group, module.getRootNodes());
                assertEquals(roots, groupRoots(module));
                assertSame(root, group.getNode(1));
                assertSame(target, root.getCallTarget());
                assertEquals(signature, signature(root));
                assertNotNull(root.getSourceSection());
                assertTrue(hasRootTag(root.getBytecodeNode().getTagTree()));

                ProtosClosureValue closure = assertInstanceOf(
                        ProtosClosureValue.class,
                        module.getCallTarget().call(debug.moduleActivation()));
                assertEquals(
                        ProtosExecutionMode.DEBUG,
                        closure.executionPlan().orElseThrow().bytecodeProfileForTesting());
                assertNotSemantics(debug, closure, "DEBUG");
                System.out.println("I101_C_NOT_DEBUG_CONTROL=PASS");
            });
        }
    }

    /** The I096 literal Closure call: admitted inline body and the B-prime physical fallback. */
    @Test
    void i101CGuardedInlineClosureMatchesIndependentBuilder() throws Exception {
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL)) {
            normal.run(() -> {
                ProtosSemanticBytecodeRootNode module = compile(INLINE_WITNESS, "inline-normal");
                BytecodeRootNodes<?> group = module.getRootNodes();
                List<ProtosSemanticBytecodeRootNode> roots = groupRoots(module);
                // Module, outer Closure and the inner Closure, which keeps its physical root.
                assertEquals(3, roots.size());
                assertSame(module, roots.get(0));
                List<RootCallTarget> targets =
                        roots.stream().map(RootNode::getCallTarget).toList();

                BytecodeRootNodes<ProtosSemanticBytecodeRootNode> oracle =
                        handwrittenGuardedInlineRoots();
                List<ProtosSemanticBytecodeRootNode> oracleRoots = groupRoots(oracle.getNode(0));
                // Outer and inner Closure roots; the oracle has no module root.
                assertEquals(2, oracleRoots.size());

                List<String> expectedOuter = completeSignature(oracleRoots.get(0));
                List<String> expectedInner = completeSignature(oracleRoots.get(1));
                List<String> actualOuter = completeSignature(roots.get(1));
                List<String> actualInner = completeSignature(roots.get(2));
                System.out.println("I101_C_INLINE_ORACLE_OUTER=" + expectedOuter);
                System.out.println("I101_C_INLINE_ACTUAL_OUTER=" + actualOuter);
                System.out.println("I101_C_INLINE_ORACLE_INNER=" + expectedInner);
                System.out.println("I101_C_INLINE_ACTUAL_INNER=" + actualInner);
                assertEquals(expectedOuter, actualOuter, "outer root vs independent Builder");
                assertEquals(expectedInner, actualInner, "inner root vs independent Builder");

                for (String operation : List.of(
                        "PrepareInlineClosureLiteralCall",
                        "AdmitsInlineClosureLiteralCall",
                        "LoadInlineLiteralFallbackCall",
                        "RequiresStructuredDispatch",
                        "EnterNestedStructuredDispatch",
                        "EnterClosureCall",
                        "ResumeContinuation",
                        "CompleteClosureCall")) {
                    assertTrue(indexOfOperation(actualOuter, operation) >= 0,
                            () -> operation + " in " + actualOuter);
                }
                assertNoOperation(actualOuter, "IsNormalExecutionMode");
                // The caller's RootTag and the inline callee's own RootTag.
                assertTrue(
                        countRootTags(roots.get(1).getBytecodeNode().getTagTree()) >= 2,
                        "caller and inline callee RootTags");

                group.ensureComplete();
                assertSame(group, module.getRootNodes());
                assertEquals(roots, groupRoots(module));
                for (int index = 0; index < roots.size(); index++) {
                    assertSame(roots.get(index), group.getNode(index));
                    assertSame(targets.get(index), roots.get(index).getCallTarget());
                }
                assertEquals(actualOuter, signature(roots.get(1)));
                assertEquals(actualInner, signature(roots.get(2)));

                ProtosClosureValue closure = assertInstanceOf(
                        ProtosClosureValue.class,
                        module.getCallTarget().call(normal.moduleActivation()));
                assertSame(roots.get(1),
                        closure.executionPlan().orElseThrow().bytecodeActivationRootForTesting());
                for (int repetition = 0; repetition < 2; repetition++) {
                    assertInteger(1, callClosure(normal, closure, ""));
                }
                System.out.println("I101_C_INLINE_BUILDER_ORACLE=PASS");
            });
        }

        try (Harness debug = new Harness(ProtosExecutionMode.DEBUG)) {
            debug.run(() -> {
                ProtosSemanticBytecodeRootNode module = compile(INLINE_WITNESS, "inline-debug");
                List<ProtosSemanticBytecodeRootNode> roots = groupRoots(module);
                assertEquals(3, roots.size(), "DEBUG keeps the inner physical root");
                List<String> outer = completeSignature(roots.get(1));
                assertNoOperation(outer, "PrepareInlineClosureLiteralCall");
                assertNoOperation(outer, "AdmitsInlineClosureLiteralCall");
                assertTrue(indexOfOperation(outer, "PrepareClosureCall") >= 0,
                        outer::toString);

                ProtosClosureValue closure = assertInstanceOf(
                        ProtosClosureValue.class,
                        module.getCallTarget().call(debug.moduleActivation()));
                for (int repetition = 0; repetition < 2; repetition++) {
                    assertInteger(1, callClosure(debug, closure, ""));
                }
                System.out.println("I101_C_INLINE_DEBUG_CONTROL=PASS");
            });
        }
    }

    /** HIT for both canonical receivers and MISS through the receiver's own {@code not}. */
    private static void assertNotSemantics(
            Harness harness, ProtosClosureValue closure, String label) throws Exception {
        for (int repetition = 0; repetition < 2; repetition++) {
            assertSame(ProtosBooleanValue.FALSE, callClosure(harness, closure, "true"),
                    label + " true.not()");
            assertSame(ProtosBooleanValue.TRUE, callClosure(harness, closure, "false"),
                    label + " false.not()");
            assertInteger(7, callClosure(harness, closure, "{ not: () => { 7 } }"));
        }
    }

    /**
     * Handwritten NORMAL root of {@code (receiver) => { receiver.not() }}: frame-native parameter
     * binding, the staged receiver, the canonical classification with its two immediate results
     * and the complete ordinary-send MISS fallback; zero-argument sends return through the I091
     * carrier boundary.
     */
    private static ProtosSemanticBytecodeRootNode handwrittenGuardedNotRoot() {
        String characters = NOT_WITNESS;
        Source source = Source.newBuilder(
                        ProtosLanguage.ID, characters, "i101-c-not-oracle.protos")
                .build();
        int bodyStart = characters.indexOf('{');
        int bodyLength = characters.lastIndexOf('}') + 1 - bodyStart;
        int sendStart = characters.indexOf("receiver.not()");
        int sendLength = "receiver.not()".length();

        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> nodes =
                ProtosSemanticBytecodeRootNodeGen.create(
                        LANGUAGE_REF.get(null),
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(bodyStart, bodyLength);
                            builder.beginRoot();

                            BytecodeLocal receiver = builder.createLocal("receiver", null);
                            BytecodeLocal[] locals = {receiver};
                            ProtosFrameLexicalLayout layout = ProtosFrameLexicalLayout.of(
                                    new String[] {"receiver"},
                                    ProtosFrameLexicalLayout.localOffsetsOf(locals));

                            // Not I104-A: the body is a send, not a read of the parameter.
                            builder.beginBindClosureFrameParameter(locals, layout, 0);
                            builder.emitLoadConstant("receiver");
                            builder.beginLoadFrameClosureArgument();
                            builder.emitLoadConstant(0);
                            builder.endLoadFrameClosureArgument();
                            builder.endBindClosureFrameParameter();

                            BytecodeLocal prepared =
                                    builder.createLocal("preparedClosureCall", null);
                            BytecodeLocal child = builder.createLocal("childResult", null);
                            BytecodeLocal resumed = builder.createLocal("resumeValue", null);
                            BytecodeLocal result = builder.createLocal("sequenceResult", null);

                            beginOracleStatement(builder, sendStart, sendLength);
                            builder.beginSourceSection(sendStart, sendLength);
                            builder.beginTag(StandardTags.CallTag.class);
                            builder.beginBlock();

                            BytecodeLocal receiverValue =
                                    builder.createLocal("normalBooleanControlReceiver", null);
                            builder.beginStoreLocal(receiverValue);
                            builder.beginMaterializePrimitiveNumericCarrier();
                            builder.beginReadRootFrameLocal(
                                    receiver, layout.presentContinuityAt(0));
                            builder.emitLoadConstant("receiver");
                            builder.endReadRootFrameLocal();
                            builder.emitLoadConstant(true);
                            builder.endMaterializePrimitiveNumericCarrier();
                            builder.endStoreLocal();

                            BytecodeLocal classification =
                                    builder.createLocal("normalBooleanControlClass", null);
                            builder.beginStoreLocal(classification);
                            builder.beginTryCompactCanonicalBooleanOne();
                            builder.emitLoadLocal(receiverValue);
                            builder.endTryCompactCanonicalBooleanOne();
                            builder.endStoreLocal();

                            builder.beginIfThenElse();
                            builder.beginIsCanonicalIfTrueTrue();
                            builder.emitLoadLocal(classification);
                            builder.endIsCanonicalIfTrueTrue();
                            builder.beginBlock();
                            emitOracleStoreConstant(builder, result, ProtosBooleanValue.FALSE);
                            builder.endBlock();
                            builder.beginBlock();

                            builder.beginIfThenElse();
                            builder.beginIsCanonicalBooleanFalseClass();
                            builder.emitLoadLocal(classification);
                            builder.endIsCanonicalBooleanFalseClass();
                            builder.beginBlock();
                            emitOracleStoreConstant(builder, result, ProtosBooleanValue.TRUE);
                            builder.endBlock();
                            builder.beginBlock();

                            // MISS: the ordinary send with the already staged receiver.
                            builder.beginStoreLocal(prepared);
                            builder.beginPrepareSendZero();
                            builder.emitLoadLocal(receiverValue);
                            builder.emitLoadConstant("not");
                            builder.emitCurrentCallerReference();
                            builder.endPrepareSendZero();
                            builder.endStoreLocal();
                            emitOraclePreparedInvocation(builder, result, prepared, child, resumed);

                            builder.endBlock();
                            builder.endIfThenElse();

                            builder.endBlock();
                            builder.endIfThenElse();

                            builder.endBlock();
                            builder.endTag(StandardTags.CallTag.class);
                            builder.endSourceSection();
                            endOracleStatement(builder);

                            builder.beginReturn();
                            builder.beginMaterializeReturnCarrier();
                            builder.emitLoadLocal(result);
                            builder.endMaterializeReturnCarrier();
                            builder.endReturn();

                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });
        return nodes.getNode(0);
    }

    /**
     * Handwritten NORMAL roots of {@code () => { (() => { 1 })() }}: the outer root with the I096
     * admission, the inline body under its own RootTag and the B-prime physical fallback, and the
     * inner Closure root, which keeps its physical identity in the group.
     */
    private static BytecodeRootNodes<ProtosSemanticBytecodeRootNode>
            handwrittenGuardedInlineRoots() {
        String characters = INLINE_WITNESS;
        Source source = Source.newBuilder(
                        ProtosLanguage.ID, characters, "i101-c-inline-oracle.protos")
                .build();
        int outerBodyStart = characters.indexOf('{');
        int outerBodyLength = characters.lastIndexOf('}') + 1 - outerBodyStart;
        int callStart = characters.indexOf("(() =>");
        int callLength = characters.indexOf(")()") + ")()".length() - callStart;
        int innerStart = callStart + 1;
        int innerEnd = characters.indexOf("{ 1 }") + "{ 1 }".length();
        int innerBodyStart = characters.indexOf("{ 1 }");
        int innerBodyLength = "{ 1 }".length();
        int literalStart = characters.indexOf('1');

        // Oracle-owned metadata constants; only their classes enter the signature.
        CanonicalClosure literal = new CanonicalClosure(
                List.of(),
                new CanonicalSequence(
                        List.of(new CanonicalLiteral(
                                CanonicalLiteral.Kind.NUMBER,
                                "1",
                                new SourceSpan(literalStart, literalStart + 1))),
                        new SourceSpan(innerBodyStart, innerBodyStart + innerBodyLength)),
                new SourceSpan(innerStart, innerEnd));
        ProtosClosureExecutionPlanCell literalPlan = ProtosClosureExecutionPlanCell.pendingGroup();

        return ProtosSemanticBytecodeRootNodeGen.create(
                LANGUAGE_REF.get(null),
                BytecodeConfig.DEFAULT,
                builder -> {
                    builder.beginSource(source);
                    builder.beginSourceSection(outerBodyStart, outerBodyLength);
                    builder.beginRoot();

                    BytecodeLocal prepared = builder.createLocal("preparedClosureCall", null);
                    BytecodeLocal child = builder.createLocal("childResult", null);
                    BytecodeLocal resumed = builder.createLocal("resumeValue", null);
                    BytecodeLocal result = builder.createLocal("sequenceResult", null);

                    beginOracleStatement(builder, callStart, callLength);
                    builder.beginSourceSection(callStart, callLength);
                    builder.beginTag(StandardTags.CallTag.class);
                    builder.beginBlock();

                    BytecodeLocal callReceiver = builder.createLocal("callReceiver", null);
                    builder.beginStoreLocal(callReceiver);

                    // The inner root is registered in this group before its Closure materializes.
                    builder.beginSourceSection(innerBodyStart, innerBodyLength);
                    builder.beginRoot();
                    builder.beginReturn();
                    builder.emitLoadConstant(1L);
                    builder.endReturn();
                    builder.endRoot();
                    builder.endSourceSection();

                    builder.beginMaterializeCurrentClosure();
                    builder.emitCurrentCallerReference();
                    builder.emitLoadConstant(literal);
                    builder.emitLoadConstant(literalPlan);
                    builder.endMaterializeCurrentClosure();
                    builder.endStoreLocal();

                    BytecodeLocal inlineCall =
                            builder.createLocal("inlineClosureLiteralCall", null);
                    builder.beginStoreLocal(inlineCall);
                    builder.beginPrepareInlineClosureLiteralCall();
                    builder.emitLoadLocal(callReceiver);
                    builder.emitCurrentActivation();
                    builder.endPrepareInlineClosureLiteralCall();
                    builder.endStoreLocal();

                    builder.beginIfThenElse();
                    builder.beginAdmitsInlineClosureLiteralCall();
                    builder.emitLoadLocal(inlineCall);
                    builder.emitLoadConstant(literalPlan);
                    builder.endAdmitsInlineClosureLiteralCall();

                    // Admitted: the callee body inline, under its own semantic RootTag.
                    builder.beginBlock();
                    BytecodeLocal callbackCall = builder.createLocal("inlineCallbackCall", null);
                    BytecodeLocal callbackResult =
                            builder.createLocal("inlineCallbackResult", null);
                    builder.beginStoreLocal(callbackCall);
                    builder.emitLoadLocal(inlineCall);
                    builder.endStoreLocal();
                    builder.beginSourceSection(innerBodyStart, innerBodyLength);
                    builder.beginTag(StandardTags.RootTag.class);
                    beginOracleStatement(builder, literalStart, 1);
                    emitOracleStoreConstant(
                            builder,
                            callbackResult,
                            ProtosNumericValueSupport.integer(1L));
                    endOracleStatement(builder);
                    builder.endTag(StandardTags.RootTag.class);
                    builder.endSourceSection();
                    builder.beginStoreLocal(result);
                    builder.emitLoadLocal(callbackResult);
                    builder.endStoreLocal();
                    builder.endBlock();

                    // Not admitted: the exact physical fallback of the prepared carrier.
                    builder.beginBlock();
                    emitOracleInlineLiteralFallback(builder, result, inlineCall, child, resumed);
                    builder.endBlock();

                    builder.endIfThenElse();

                    builder.endBlock();
                    builder.endTag(StandardTags.CallTag.class);
                    builder.endSourceSection();
                    endOracleStatement(builder);

                    builder.beginReturn();
                    builder.emitLoadLocal(result);
                    builder.endReturn();

                    builder.endRoot();
                    builder.endSourceSection();
                    builder.endSource();
                });
    }

    // ---- Shared Builder restatement of the physical prepared-invocation protocol. ----

    private static void beginOracleStatement(
            ProtosSemanticBytecodeRootNodeGen.Builder builder, int start, int length) {
        builder.beginSourceSection(start, length);
        builder.beginTag(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class);
        builder.beginBlock();
    }

    private static void endOracleStatement(ProtosSemanticBytecodeRootNodeGen.Builder builder) {
        builder.endBlock();
        builder.endTag(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class);
        builder.endSourceSection();
    }

    private static void emitOracleStoreConstant(
            ProtosSemanticBytecodeRootNodeGen.Builder builder, BytecodeLocal target, Object value) {
        builder.beginStoreLocal(target);
        builder.emitLoadConstant(value);
        builder.endStoreLocal();
    }

    /** A child continuation is yielded through this root until the child completes. */
    private static void emitOracleContinuationComposition(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal prepared,
            BytecodeLocal child,
            BytecodeLocal resumed) {
        builder.beginWhile();
        builder.beginIsContinuationForCall();
        builder.emitLoadLocal(prepared);
        builder.emitLoadLocal(child);
        builder.endIsContinuationForCall();

        builder.beginBlock();
        builder.beginStoreLocal(resumed);
        builder.beginYield();
        builder.emitLoadLocal(child);
        builder.endYield();
        builder.endStoreLocal();

        builder.beginStoreLocal(child);
        builder.beginResumeContinuation();
        builder.emitLoadLocal(prepared);
        builder.emitLoadLocal(child);
        builder.emitLoadLocal(resumed);
        builder.endResumeContinuation();
        builder.endStoreLocal();
        builder.endBlock();

        builder.endWhile();
    }

    private static void emitOracleOrdinaryPreparedInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal prepared,
            BytecodeLocal child,
            BytecodeLocal resumed) {
        builder.beginIfThenElse();
        builder.beginAdmitsStraightLineSourceCall();
        builder.emitLoadLocal(prepared);
        builder.endAdmitsStraightLineSourceCall();

        builder.beginBlock();
        builder.beginStoreLocal(result);
        builder.beginEnterStraightLineSourceCall();
        builder.emitLoadLocal(prepared);
        builder.endEnterStraightLineSourceCall();
        builder.endStoreLocal();
        builder.endBlock();

        builder.beginBlock();
        builder.beginStoreLocal(child);
        builder.beginEnterClosureCall();
        builder.emitLoadLocal(prepared);
        builder.endEnterClosureCall();
        builder.endStoreLocal();
        emitOracleContinuationComposition(builder, prepared, child, resumed);
        builder.beginStoreLocal(result);
        builder.beginFinishClosureCall();
        builder.emitLoadLocal(prepared);
        builder.emitLoadLocal(child);
        builder.endFinishClosureCall();
        builder.endStoreLocal();
        builder.endBlock();

        builder.endIfThenElse();
    }

    private static void emitOracleNestedStructuredInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal prepared,
            BytecodeLocal child,
            BytecodeLocal resumed) {
        builder.beginStoreLocal(child);
        builder.beginEnterNestedStructuredDispatch();
        builder.emitLoadLocal(prepared);
        builder.endEnterNestedStructuredDispatch();
        builder.endStoreLocal();
        emitOracleContinuationComposition(builder, prepared, child, resumed);
        builder.beginStoreLocal(result);
        builder.emitLoadLocal(child);
        builder.endStoreLocal();
    }

    private static void emitOracleDispatchedPreparedInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal prepared,
            BytecodeLocal child,
            BytecodeLocal resumed) {
        builder.beginIfThenElse();
        builder.beginRequiresStructuredDispatch();
        builder.emitLoadLocal(prepared);
        builder.endRequiresStructuredDispatch();

        builder.beginBlock();
        emitOracleNestedStructuredInvocation(builder, result, prepared, child, resumed);
        builder.endBlock();

        builder.beginBlock();
        emitOracleOrdinaryPreparedInvocation(builder, result, prepared, child, resumed);
        builder.endBlock();

        builder.endIfThenElse();
    }

    /**
     * The B-prime physical fallback of a prepared inline carrier. Its completion is a
     * TryFinally, so it also runs on Error, cancellation and control-transfer exits.
     */
    private static void emitOracleInlineLiteralFallback(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal inlineChild,
            BytecodeLocal child,
            BytecodeLocal resumed) {
        builder.beginBlock();
        BytecodeLocal fallback = builder.createLocal("inlineCallbackFallback", null);

        builder.beginStoreLocal(fallback);
        builder.beginLoadInlineLiteralFallbackCall();
        builder.emitLoadLocal(inlineChild);
        builder.endLoadInlineLiteralFallbackCall();
        builder.endStoreLocal();

        builder.beginIfThenElse();
        builder.beginRequiresStructuredDispatch();
        builder.emitLoadLocal(fallback);
        builder.endRequiresStructuredDispatch();

        builder.beginBlock();
        emitOracleNestedStructuredInvocation(builder, result, fallback, child, resumed);
        builder.endBlock();

        builder.beginBlock();
        builder.beginTryFinally(() -> {
            builder.beginCompleteClosureCall();
            builder.emitLoadLocal(fallback);
            builder.endCompleteClosureCall();
        });
        builder.beginBlock();
        emitOracleOrdinaryPreparedInvocation(builder, result, fallback, child, resumed);
        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();
    }

    /** The standard Boolean protocol of a prepared send without inline literal candidates. */
    private static void emitOracleLocalBooleanPreparedInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal prepared,
            BytecodeLocal child,
            BytecodeLocal resumed) {
        builder.beginBlock();
        BytecodeLocal structuredBoolean = builder.createLocal("structuredBoolean", null);
        BytecodeLocal structuredBooleanChild =
                builder.createLocal("structuredBooleanChild", null);

        builder.beginTryFinally(() -> {
            builder.beginCompleteClosureCall();
            builder.emitLoadLocal(prepared);
            builder.endCompleteClosureCall();
        });
        builder.beginBlock();

        builder.beginStoreLocal(structuredBoolean);
        builder.beginPrepareStructuredBooleanCall();
        builder.emitLoadLocal(prepared);
        builder.endPrepareStructuredBooleanCall();
        builder.endStoreLocal();

        builder.beginIfThenElse();
        builder.beginStructuredBooleanHasCallback();
        builder.emitLoadLocal(structuredBoolean);
        builder.endStructuredBooleanHasCallback();

        builder.beginBlock();
        builder.beginStoreLocal(structuredBooleanChild);
        builder.beginPrepareInlineStructuredBooleanCallbackCall();
        builder.emitLoadLocal(structuredBoolean);
        builder.endPrepareInlineStructuredBooleanCallbackCall();
        builder.endStoreLocal();
        emitOracleInlineLiteralFallback(builder, child, structuredBooleanChild, child, resumed);
        builder.beginStoreLocal(result);
        builder.beginFinishStructuredBooleanCallback();
        builder.emitLoadLocal(structuredBoolean);
        builder.emitLoadLocal(child);
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
    }

    private static void emitOraclePreparedInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal prepared,
            BytecodeLocal child,
            BytecodeLocal resumed) {
        builder.beginIfThenElse();
        builder.beginIsStructuredBooleanCall();
        builder.emitLoadLocal(prepared);
        builder.endIsStructuredBooleanCall();

        emitOracleLocalBooleanPreparedInvocation(builder, result, prepared, child, resumed);

        builder.beginBlock();
        emitOracleDispatchedPreparedInvocation(builder, result, prepared, child, resumed);
        builder.endBlock();

        builder.endIfThenElse();
    }

    // ---- Actual roots, signatures and guest calls. ----

    /** The production ParsingRequest path of the entered Context. */
    private static ProtosSemanticBytecodeRootNode compile(String characters, String name) {
        Source source = Source.newBuilder(
                        ProtosLanguage.ID, characters, "i101-c-oracle-" + name + ".protos")
                .build();
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class, ProtosLanguageContext.current().parsePublic(source));
        return assertInstanceOf(ProtosSemanticBytecodeRootNode.class, target.getRootNode());
    }

    private static List<ProtosSemanticBytecodeRootNode> groupRoots(
            ProtosSemanticBytecodeRootNode root) {
        return root.getRootNodes().getNodes().stream()
                .map(ProtosSemanticBytecodeRootNode.class::cast)
                .toList();
    }

    private static int indexOfOperation(List<String> signature, String operation) {
        for (int index = 0; index < signature.size(); index++) {
            String name = signature.get(index).split(" ", 2)[0];
            if (name.equals(operation) || name.equals("c." + operation)) {
                return index;
            }
        }
        return -1;
    }

    private static void assertNoOperation(List<String> signature, String operation) {
        assertTrue(
                signature.stream().noneMatch(entry -> entry.split(" ", 2)[0].contains(operation)),
                () -> operation + " in " + signature);
    }

    private static List<String> completeSignature(ProtosSemanticBytecodeRootNode root) {
        root.getRootNodes().ensureComplete();
        return signature(root);
    }

    /** Every instruction with every argument; nothing is filtered. */
    private static List<String> signature(ProtosSemanticBytecodeRootNode root) {
        List<String> entries = new ArrayList<>();
        for (Instruction instruction : root.getBytecodeNode().getInstructionsAsList()) {
            StringBuilder entry = new StringBuilder(instruction.getName());
            for (Instruction.Argument argument : instruction.getArguments()) {
                entry.append(' ')
                        .append(argument.getName())
                        .append(':')
                        .append(argument.getKind())
                        .append('=')
                        .append(operand(argument));
            }
            entries.add(entry.toString());
        }
        return entries;
    }

    private static String operand(Instruction.Argument argument) {
        return switch (argument.getKind()) {
            case CONSTANT -> constant(argument.asConstant());
            case BYTECODE_INDEX -> Integer.toString(argument.asBytecodeIndex());
            case INTEGER -> Integer.toString(argument.asInteger());
            case LOCAL_OFFSET -> Integer.toString(argument.asLocalOffset());
            case LOCAL_INDEX -> Integer.toString(argument.asLocalIndex());
            case TAG_NODE -> tags(argument.asTagNode());
            case NODE_PROFILE, BRANCH_PROFILE -> "profile";
        };
    }

    private static String constant(Object value) {
        if (value == null) {
            return "host-null";
        }
        if (value instanceof Double number) {
            return "Double(bits=" + Long.toHexString(Double.doubleToRawLongBits(number)) + ")";
        }
        if (value instanceof Float number) {
            return "Float(bits=" + Integer.toHexString(Float.floatToRawIntBits(number)) + ")";
        }
        if (value instanceof Long
                || value instanceof Integer
                || value instanceof Short
                || value instanceof Byte
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof String
                || value instanceof BigInteger) {
            return value.getClass().getSimpleName() + "(" + value + ")";
        }
        if (value == ProtosNullValue.INSTANCE) {
            return "Protos(null)";
        }
        if (value == ProtosBooleanValue.TRUE || value == ProtosBooleanValue.FALSE) {
            return "Protos(" + (value == ProtosBooleanValue.TRUE) + ")";
        }
        if (value instanceof Class<?> type) {
            return "Class(" + type.getName() + ")";
        }
        // Internal metadata (plans, layouts, definitions) has no stable value across roots.
        return "Metadata(" + value.getClass().getName() + ")";
    }

    private static String tags(TagTreeNode node) {
        if (node == null) {
            return "no-tag-node";
        }
        return node.getTags().stream().map(Class::getSimpleName).sorted().toList().toString();
    }

    private static boolean hasRootTag(TagTree tree) {
        return countRootTags(tree) > 0;
    }

    private static int countRootTags(TagTree tree) {
        if (tree == null) {
            return 0;
        }
        int count = tree.hasTag(StandardTags.RootTag.class) ? 1 : 0;
        for (TagTree child : tree.getTreeChildren()) {
            count += countRootTags(child);
        }
        return count;
    }

    /** Calls a Closure value through an ordinary guest call in a fresh module activation. */
    private static Object callClosure(
            Harness harness, ProtosClosureValue closure, String arguments) {
        ProtosActivation module = harness.moduleActivation();
        module.context().createLocalSlot("candidate", closure);
        return compile(
                        "candidate(" + arguments + ")",
                        "call-" + System.identityHashCode(closure) + "-" + arguments.hashCode())
                .getCallTarget()
                .call(module);
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(
                Long.toString(expected), ProtosNumericValueSupport.integerDecimalText(value));
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    /** One raw Polyglot Context with a published Core, entered only while an action runs. */
    private static final class Harness implements AutoCloseable {
        private final ProtosExecutionMode mode;
        private final Context context;
        private ProtosPrelude prelude;

        Harness(ProtosExecutionMode mode) {
            this.mode = mode;
            Context.Builder builder = Context.newBuilder(ProtosLanguage.ID);
            if (mode == ProtosExecutionMode.DEBUG) {
                builder.allowExperimentalOptions(true).option("protos.Debug", "true");
            }
            this.context = builder.build();
            context.initialize(ProtosLanguage.ID);
        }

        void run(Action action) throws Exception {
            context.enter();
            try {
                assertEquals(mode, ProtosLanguageContext.current().executionMode());
                if (prelude == null) {
                    // Publishes the standard root before any witness is lowered.
                    prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                }
                action.run();
            } finally {
                context.leave();
            }
        }

        ProtosActivation moduleActivation() {
            return prelude.newModuleActivation();
        }

        @Override
        public void close() {
            context.close();
        }
    }
}
