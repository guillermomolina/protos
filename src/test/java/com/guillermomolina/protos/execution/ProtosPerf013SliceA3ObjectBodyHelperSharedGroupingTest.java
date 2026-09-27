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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.source.Source;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF013 Slice A3 focal evidence: an object-construction helper root now
 * shares the physical {@code BytecodeRootNodes<ProtosBytecodeRootNode>} group
 * of the lexical lowering unit that contains the object literal, exactly like
 * Slice A/A2 already do for Closures. Sharing that physical group does not
 * make the helper root a genuine lexical execution context: a Closure
 * declared inside an object body must still capture the enclosing genuine
 * lexical context(s), never the constructed object itself (see {@code
 * EXECUTION_AND_CONTROL.md} "Object Construction Is Not a Lexical Capture
 * Scope").
 *
 * <p>This slice does not adopt {@code MaterializedLocalAccessor} (PERF013
 * Slice B) and does not change the captured-local read/write mechanism.
 * Existing capture semantics remain covered by {@link
 * ProtosI068Slice5CapturedMaterializedLexicalLoweringTest}.
 */
final class ProtosPerf013SliceA3ObjectBodyHelperSharedGroupingTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void objectBodyHelperRootSharesGroupWithOwner() throws Exception {
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
                                        "perf013-slicea3-object-body-shared-group.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);
                CanonicalObject objectDefinition =
                        assertInstanceOf(
                                CanonicalObject.class, sequence.expressions().get(0));

                CanonicalToBytecodeLowerer lowerer =
                        new CanonicalToBytecodeLowerer(language, source);
                ProtosBytecodeRootNode root = lowerer.lowerRoot(sequence);

                ProtosObjectValue object =
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                root.getCallTarget().call(module));
                assertTrue(object.hasLocalSlot("x"));

                ProtosBytecodeRootNode helperRoot =
                        lowerer.objectBodyHelperRootForTesting(objectDefinition);

                assertSame(
                        root.getRootNodes(),
                        helperRoot.getRootNodes(),
                        "object-body helper root must share owner's BytecodeRootNodes group");
                assertEquals(
                        2,
                        root.getRootNodes().count(),
                        "expected exactly owner + object-body helper roots");
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void closureInObjectBodySharesGroupWithOuterOwnerAndCapturesOuterLexicalContext()
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                ProtosObjectValue lexical =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue objectLocal =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("seed", lexical);
                module.context().createLocalSlot("objectValue", objectLocal);

                String characters =
                        "x: seed\n"
                                + "obj: {\n"
                                + "  x: objectValue\n"
                                + "  method: () => x\n"
                                + "}\n"
                                + "obj";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "perf013-slicea3-object-body-closure.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);
                CanonicalObject objectDefinition = findObjectDefinition(sequence, "obj");

                CanonicalToBytecodeLowerer lowerer =
                        new CanonicalToBytecodeLowerer(language, source);
                ProtosBytecodeRootNode root = lowerer.lowerRoot(sequence);

                ProtosObjectValue object =
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                root.getCallTarget().call(module));
                assertSame(
                        objectLocal,
                        object.readLocalSlot("x").orElseThrow(),
                        "object slot storage must remain unchanged (receiver state, not a lexical local)");

                ProtosClosureValue method =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                object.readLocalSlot("method").orElseThrow());
                ProtosClosureExecutionPlan methodPlan =
                        method.executionPlan().orElseThrow();
                ProtosBytecodeRootNode methodRoot =
                        methodPlan.bytecodeActivationRootForTesting();

                ProtosBytecodeRootNode helperRoot =
                        lowerer.objectBodyHelperRootForTesting(objectDefinition);

                BytecodeRootNodes<?> group = root.getRootNodes();
                assertSame(
                        group,
                        helperRoot.getRootNodes(),
                        "object-body helper root must share owner's BytecodeRootNodes group");
                assertSame(
                        group,
                        methodRoot.getRootNodes(),
                        "Closure declared in object body must share owner's BytecodeRootNodes group");
                assertEquals(
                        3,
                        group.count(),
                        () -> "expected owner + object-body helper + method Closure roots, got "
                                + group.count());

                ProtosClosureValue bound = method.bindMethod(object, object);
                ProtosActivation invocation =
                        ProtosActivation.forClosureInvocation(
                                bound,
                                java.util.List.of(),
                                module.prelude().orElseThrow(),
                                module.actorModuleState(),
                                module.currentModuleKey().orElse(null),
                                module.executionDomain());

                /*
                 * If object construction had incorrectly entered the lexical
                 * capture chain, this would return objectLocal instead.
                 */
                assertSame(
                        lexical,
                        bound.executionPlan()
                                .orElseThrow()
                                .executeBytecodeActivationForTesting(invocation),
                        "method must resolve the bare name against the outer genuine "
                                + "lexical context, not the constructed object's own slot");
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void multiDepthClosuresInObjectBodyShareGroupWithOuterOwner() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                ProtosObjectValue lexical =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("seed", lexical);

                String characters =
                        "x: seed\n"
                                + "obj: {\n"
                                + "  method: () => {\n"
                                + "    () => x\n"
                                + "  }\n"
                                + "}\n"
                                + "obj";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "perf013-slicea3-object-body-multi-depth.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);
                CanonicalObject objectDefinition = findObjectDefinition(sequence, "obj");

                CanonicalToBytecodeLowerer lowerer =
                        new CanonicalToBytecodeLowerer(language, source);
                ProtosBytecodeRootNode root = lowerer.lowerRoot(sequence);

                ProtosObjectValue object =
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                root.getCallTarget().call(module));

                ProtosClosureValue method =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                object.readLocalSlot("method").orElseThrow());
                ProtosClosureExecutionPlan methodPlan =
                        method.executionPlan().orElseThrow();
                ProtosBytecodeRootNode methodRoot =
                        methodPlan.bytecodeActivationRootForTesting();

                ProtosActivation methodInvocation =
                        ProtosActivation.forClosureInvocation(
                                method.bindMethod(object, object),
                                java.util.List.of(),
                                module.prelude().orElseThrow(),
                                module.actorModuleState(),
                                module.currentModuleKey().orElse(null),
                                module.executionDomain());
                ProtosClosureValue innerClosure =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                methodPlan.executeBytecodeActivationForTesting(
                                        methodInvocation));
                ProtosClosureExecutionPlan innerPlan =
                        innerClosure.executionPlan().orElseThrow();
                ProtosBytecodeRootNode innerRoot =
                        innerPlan.bytecodeActivationRootForTesting();

                ProtosBytecodeRootNode helperRoot =
                        lowerer.objectBodyHelperRootForTesting(objectDefinition);

                BytecodeRootNodes<?> group = root.getRootNodes();
                assertSame(group, helperRoot.getRootNodes());
                assertSame(group, methodRoot.getRootNodes());
                assertSame(group, innerRoot.getRootNodes());
                assertEquals(
                        4,
                        group.count(),
                        () -> "expected owner + object-body helper + method Closure + "
                                + "inner Closure roots, got " + group.count());

                ProtosActivation innerInvocation =
                        ProtosActivation.forClosureInvocation(
                                innerClosure,
                                java.util.List.of(),
                                module.prelude().orElseThrow(),
                                module.actorModuleState(),
                                module.currentModuleKey().orElse(null),
                                module.executionDomain());
                assertSame(
                        lexical,
                        innerPlan.executeBytecodeActivationForTesting(innerInvocation),
                        "innermost Closure must still resolve the bare name against the "
                                + "outer genuine lexical context through the object-body "
                                + "helper root");
            } finally {
                context.leave();
            }
        }
    }

    private static CanonicalObject findObjectDefinition(
            CanonicalSequence sequence, String name) {
        for (CanonicalExpression expression : sequence.expressions()) {
            if (expression instanceof CanonicalCreate create
                    && name.equals(create.name())
                    && create.value() instanceof CanonicalObject object) {
                return object;
            }
        }
        throw new AssertionError("no object literal bound to '" + name + "' found");
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
