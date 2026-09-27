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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.source.Source;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF013 Slice A focal evidence: a lexical owner root and the Closures it
 * lexically contains are lowered into one shared {@code
 * BytecodeRootNodes<ProtosBytecodeRootNode>} group (nested {@code
 * beginRoot()}/{@code endRoot()} in the same open builder) instead of each
 * Closure opening its own independent {@code create()} call.
 *
 * <p>This slice does not yet change the captured-local read/write mechanism
 * (that is PERF013 Slice B): these tests prove topology only. Existing
 * capture semantics (PLAT036/I068/I071) are covered by {@link
 * ProtosI068Slice5CapturedMaterializedLexicalLoweringTest} and must remain
 * unaffected.
 */
final class ProtosPerf013SliceASharedLexicalRootGroupingTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void ownerAndDirectChildClosureShareBytecodeRootNodes() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters = "() => 1";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "perf013-slicea-direct-child.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source)
                                .lowerRoot(sequence);

                ProtosClosureValue closure =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                root.getCallTarget().call(module));
                ProtosClosureExecutionPlan plan =
                        closure.executionPlan().orElseThrow();
                ProtosBytecodeRootNode childRoot =
                        plan.bytecodeActivationRootForTesting();

                BytecodeRootNodes<?> ownerGroup = root.getRootNodes();
                BytecodeRootNodes<?> childGroup = childRoot.getRootNodes();

                assertSame(
                        ownerGroup,
                        childGroup,
                        "owner and directly nested Closure must share one BytecodeRootNodes group");
                assertTrue(
                        ownerGroup.count() >= 2,
                        () -> "expected at least owner + child roots, got " + ownerGroup.count());
                assertNotSame(
                        root.getFrameDescriptor(),
                        childRoot.getFrameDescriptor(),
                        "distinct lexical roots must keep distinct frame/local layouts");
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void ownerAndMultiDepthChildClosuresShareBytecodeRootNodes() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters = "() => { () => { () => 1 } }";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "perf013-slicea-multi-depth.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source)
                                .lowerRoot(sequence);

                ProtosClosureValue closureA =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                root.getCallTarget().call(module));
                ProtosClosureExecutionPlan planA =
                        closureA.executionPlan().orElseThrow();
                ProtosBytecodeRootNode rootA =
                        planA.bytecodeActivationRootForTesting();

                ProtosActivation invocationA =
                        ProtosActivation.forClosureInvocation(
                                closureA,
                                java.util.List.of(),
                                module.prelude().orElseThrow(),
                                module.actorModuleState(),
                                module.currentModuleKey().orElse(null),
                                module.executionDomain());
                ProtosClosureValue closureB =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                planA.executeBytecodeActivationForTesting(invocationA));
                ProtosClosureExecutionPlan planB =
                        closureB.executionPlan().orElseThrow();
                ProtosBytecodeRootNode rootB =
                        planB.bytecodeActivationRootForTesting();

                ProtosActivation invocationB =
                        ProtosActivation.forClosureInvocation(
                                closureB,
                                java.util.List.of(),
                                module.prelude().orElseThrow(),
                                module.actorModuleState(),
                                module.currentModuleKey().orElse(null),
                                module.executionDomain());
                ProtosClosureValue closureC =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                planB.executeBytecodeActivationForTesting(invocationB));
                ProtosClosureExecutionPlan planC =
                        closureC.executionPlan().orElseThrow();
                ProtosBytecodeRootNode rootC =
                        planC.bytecodeActivationRootForTesting();

                BytecodeRootNodes<?> group = root.getRootNodes();
                assertSame(group, rootA.getRootNodes());
                assertSame(group, rootB.getRootNodes());
                assertSame(group, rootC.getRootNodes());
                assertEquals(
                        4,
                        group.count(),
                        () -> "expected exactly owner + 3 nested Closure roots in one group");

                assertNotSame(root.getFrameDescriptor(), rootA.getFrameDescriptor());
                assertNotSame(rootA.getFrameDescriptor(), rootB.getFrameDescriptor());
                assertNotSame(rootB.getFrameDescriptor(), rootC.getFrameDescriptor());
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void siblingClosuresInSameOwnerShareGroupButHaveDistinctRoots() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters =
                        "a: () => 1\n"
                                + "b: () => 2\n"
                                + "a";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "perf013-slicea-siblings.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source)
                                .lowerRoot(sequence);

                ProtosClosureValue closureA =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                root.getCallTarget().call(module));
                ProtosClosureExecutionPlan planA =
                        closureA.executionPlan().orElseThrow();
                ProtosBytecodeRootNode rootA =
                        planA.bytecodeActivationRootForTesting();

                ProtosClosureValue closureB =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                module.context().readLocalSlot("b").orElseThrow());
                ProtosClosureExecutionPlan planB =
                        closureB.executionPlan().orElseThrow();
                ProtosBytecodeRootNode rootB =
                        planB.bytecodeActivationRootForTesting();

                assertSame(root.getRootNodes(), rootA.getRootNodes());
                assertSame(root.getRootNodes(), rootB.getRootNodes());
                assertNotSame(
                        rootA,
                        rootB,
                        "the closure execution plan for each Closure literal must "
                                + "target its own distinct group root");
                assertEquals(3, root.getRootNodes().count());
            } finally {
                context.leave();
            }
        }
    }

    /**
     * PERF013 Slice A3 supersedes the topology this test originally proved
     * (an object-body helper root was its own independent {@code
     * BytecodeRootNodes} group). Since Slice A3 the helper root shares the
     * owner's group instead; see {@code
     * ProtosPerf013SliceA3ObjectBodyHelperSharedGroupingTest} for the current
     * focal coverage of object-body helper root topology.
     */
    @Test
    void objectBodyHelperRootSharesOwnerGroupSincePerf013SliceA3() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters = "{\n  x: 1\n}";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "perf013-slicea-object-body-boundary.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source)
                                .lowerRoot(sequence);

                ProtosObjectValue object =
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                root.getCallTarget().call(module));
                assertEquals(2, root.getRootNodes().count());
                assertTrue(object.hasLocalSlot("x"));
            } finally {
                context.leave();
            }
        }
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer()
                        .canonicalize(
                                new ProtosParser(characters)
                                        .parseProgram());
    }

    private static ProtosActivation moduleActivation() {
        ProtosStandardObjectProtocol.install();

        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings =
                new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot(
                "Error",
                new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.createLocalSlot(
                "Array",
                new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();

        return new ProtosPrelude(bindings, contextPrototype)
                .newModuleActivation();
    }
}
