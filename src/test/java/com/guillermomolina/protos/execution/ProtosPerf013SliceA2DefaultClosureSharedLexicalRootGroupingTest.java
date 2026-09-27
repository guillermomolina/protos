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

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.source.Source;
import java.math.BigInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF013 Slice A2 focal evidence: a parameter default-value Closure literal
 * is lowered into the exact same shared {@code
 * BytecodeRootNodes<ProtosBytecodeRootNode>} group as its lexical owner and
 * any ordinary body Closure lexically reached from the same owner, instead of
 * opening an independent {@code create()} call the way the pre-A2 validation
 * path did.
 *
 * <p>This slice does not change the captured-local read/write mechanism
 * (that remains PERF013 Slice B): these tests prove root-group topology and
 * that default-expression capture-by-reference/earlier-parameter-visibility
 * semantics are unaffected. Ordinary (non-default) Closure grouping, sibling
 * grouping, and the object-body boundary are already covered by {@link
 * ProtosPerf013SliceASharedLexicalRootGroupingTest} and are not duplicated
 * here.
 */
final class ProtosPerf013SliceA2DefaultClosureSharedLexicalRootGroupingTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void directDefaultClosureSharesOwnerGroupAndCapturesEarlierParameterByReference()
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters =
                        "f: (x, g = () => x) => g\n"
                                + "result: f(42)\n"
                                + "result()";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "perf013-slicea2-direct-default.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source)
                                .lowerRoot(sequence);

                Object finalResult = root.getCallTarget().call(module);
                ProtosIntegerValue integer =
                        assertInstanceOf(ProtosIntegerValue.class, finalResult);
                assertEquals(BigInteger.valueOf(42), integer.value());

                ProtosClosureValue closureF =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                module.context().readLocalSlot("f").orElseThrow());
                ProtosClosureValue closureDefault =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                module.context().readLocalSlot("result").orElseThrow());

                ProtosBytecodeRootNode rootF =
                        closureF.executionPlan()
                                .orElseThrow()
                                .bytecodeActivationRootForTesting();
                ProtosBytecodeRootNode rootDefault =
                        closureDefault.executionPlan()
                                .orElseThrow()
                                .bytecodeActivationRootForTesting();

                BytecodeRootNodes<?> group = root.getRootNodes();
                assertSame(
                        group,
                        rootF.getRootNodes(),
                        "owner module root and Closure f must share one BytecodeRootNodes group");
                assertSame(
                        group,
                        rootDefault.getRootNodes(),
                        "default-value Closure must share the same BytecodeRootNodes "
                                + "group as its lexical owner instead of opening an "
                                + "independent create() call");
                assertEquals(
                        3,
                        group.count(),
                        () -> "expected module root + f + default Closure, got " + group.count());

                assertNotSame(root.getFrameDescriptor(), rootF.getFrameDescriptor());
                assertNotSame(rootF.getFrameDescriptor(), rootDefault.getFrameDescriptor());
            } finally {
                context.leave();
            }
        }

        System.out.println("DEFAULT_CLOSURE_OWNER_SHARED_GROUP=PASS");
        System.out.println("DEFAULT_CLOSURE_INDEPENDENT_CREATE=NO");
        System.out.println("DEFAULT_CAPTURE_EARLIER_PARAMETER=PASS");
        System.out.println("DEFAULT_CAPTURE_BY_REFERENCE=PASS");
    }

    @Test
    void nestedClosureInsideDefaultClosureSharesOwnerGroupAtEveryDepth()
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters =
                        "f: (x, g = () => { () => x }) => g\n"
                                + "h: f(42)\n"
                                + "i: h()\n"
                                + "i()";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "perf013-slicea2-multi-depth-default.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source)
                                .lowerRoot(sequence);

                Object finalResult = root.getCallTarget().call(module);
                ProtosIntegerValue integer =
                        assertInstanceOf(ProtosIntegerValue.class, finalResult);
                assertEquals(
                        BigInteger.valueOf(42),
                        integer.value(),
                        "x must remain visible by reference two Closure levels below "
                                + "the default expression that first captured it");

                ProtosClosureValue closureF =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                module.context().readLocalSlot("f").orElseThrow());
                ProtosClosureValue closureDefault =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                module.context().readLocalSlot("h").orElseThrow());
                ProtosClosureValue closureNested =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                module.context().readLocalSlot("i").orElseThrow());

                ProtosBytecodeRootNode rootF =
                        closureF.executionPlan()
                                .orElseThrow()
                                .bytecodeActivationRootForTesting();
                ProtosBytecodeRootNode rootDefault =
                        closureDefault.executionPlan()
                                .orElseThrow()
                                .bytecodeActivationRootForTesting();
                ProtosBytecodeRootNode rootNested =
                        closureNested.executionPlan()
                                .orElseThrow()
                                .bytecodeActivationRootForTesting();

                BytecodeRootNodes<?> group = root.getRootNodes();
                assertSame(group, rootF.getRootNodes());
                assertSame(
                        group,
                        rootDefault.getRootNodes(),
                        "default-value Closure must share the owner's group");
                assertSame(
                        group,
                        rootNested.getRootNodes(),
                        "a Closure nested inside a default-value Closure's body must "
                                + "still share the same owner group");
                assertEquals(
                        4,
                        group.count(),
                        () ->
                                "expected module root + f + default Closure + nested "
                                        + "Closure, got "
                                        + group.count());

                assertNotSame(root.getFrameDescriptor(), rootF.getFrameDescriptor());
                assertNotSame(rootF.getFrameDescriptor(), rootDefault.getFrameDescriptor());
                assertNotSame(rootDefault.getFrameDescriptor(), rootNested.getFrameDescriptor());
            } finally {
                context.leave();
            }
        }

        System.out.println("DEFAULT_CLOSURE_MULTI_DEPTH_GROUPING=PASS");
        System.out.println("DEFAULT_CLOSURE_MULTI_DEPTH_SHARED_GROUP=PASS");
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
