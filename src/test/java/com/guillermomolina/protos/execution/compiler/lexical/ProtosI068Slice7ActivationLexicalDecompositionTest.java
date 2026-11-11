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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Method;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I068 Slice 7 focused evidence that generic lexical-name resolution is no
 * longer an intrinsic responsibility of {@link ProtosActivation}, while the
 * residual Candidate/Dynamic fallback remains available and statically proven
 * current/captured bindings continue to bypass it.
 */
final class ProtosI068Slice7ActivationLexicalDecompositionTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void activationNoLongerOwnsGenericLexicalReadOrWriteResolvers() {
        List<String> methodNames =
                java.util.Arrays.stream(ProtosActivation.class.getDeclaredMethods())
                        .map(Method::getName)
                        .toList();

        assertFalse(
                methodNames.contains("lookup"),
                () -> "ProtosActivation still owns generic lexical lookup: " + methodNames);
        assertFalse(
                methodNames.contains("writableLexicalContext"),
                () ->
                        "ProtosActivation still owns generic writable lexical resolution: "
                                + methodNames);

        assertTrue(methodNames.contains("capturedLexicalContexts"));
        assertTrue(methodNames.contains("lexicalContextsForClosureCapture"));
    }

    @Test
    void provenCurrentReadUsesFramePathWithoutGenericLookupInstruction()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    String characters = "x: 1\nx";
                    ProtosBytecodeRootNode root =
                            lowerRoot(language, characters, "i068-slice7-current.protos");

                    List<String> instructions = instructionNames(root);

                    assertContains(instructions, "ReadGlobal");
                    assertNotContains(instructions, "Lookup");

                    root.getCallTarget().call(module);
                });
    }

    @Test
    void provenCapturedReadUsesFramePathWithoutGenericLookupInstruction()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    String characters =
                            "x: 1\n"
                                    + "() => x";

                    ProtosBytecodeRootNode root =
                            lowerRoot(language, characters, "i068-slice7-captured.protos");

                    ProtosClosureValue closure =
                            assertInstanceOf(
                                    ProtosClosureValue.class,
                                    root.getCallTarget().call(module));

                    ProtosClosureExecutionPlan plan =
                            closure.executionPlan().orElseThrow();

                    List<String> instructions =
                            instructionNames(
                                    plan.bytecodeActivationRootForTesting());

                    /*
                     * I115-R6: a captured module binding reads its module
                     * context through ReadGlobal at its physical depth.
                     */
                    assertContains(instructions, "ReadGlobal");
                    assertNotContains(instructions, "Lookup");
                });
    }

    @Test
    void candidateReadRetainsExactGenericFallback()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    ProtosObjectValue outerValue =
                            new ProtosObjectValue(ProtosObjectValue.rootObject());
                    module.context().createLocalSlot("x", outerValue);

                    String characters =
                            "() => { before: x\n"
                                    + "x: 1\n"
                                    + "before }";

                    ProtosBytecodeRootNode root =
                            lowerRoot(language, characters, "i068-slice7-candidate.protos");

                    ProtosClosureValue closure =
                            assertInstanceOf(
                                    ProtosClosureValue.class,
                                    root.getCallTarget().call(module));

                    ProtosClosureExecutionPlan plan =
                            closure.executionPlan().orElseThrow();

                    List<String> instructions =
                            instructionNames(
                                    plan.bytecodeActivationRootForTesting());

                    // I115-S1c-L: the Candidate own LOCAL read is the
                    // presence-aware physical read, which continues with live
                    // lexical selection while the binding is ABSENT.
                    assertContains(instructions, "LoadLexicalLocal");
                    assertNotContains(instructions, "Lookup");

                    ProtosActivation invocation =
                            ProtosActivation.forClosureInvocation(
                                    closure,
                                    List.of(),
                                    module.prelude().orElseThrow(),
                                    module.actorModuleState(),
                                    module.currentModuleKey().orElse(null),
                                    module.executionDomain());

                    assertSame(
                            outerValue,
                            plan.executeBytecodeActivationForTesting(invocation));
                });
    }

    @Test
    void candidateReadWithoutOuterBindingSignalsSlotNotFound()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    ProtosSignalException failure =
                            assertThrows(
                                    ProtosSignalException.class,
                                    () -> executeClosure(
                                            language,
                                            module,
                                            "() => { before: x\nx: 1\nbefore }",
                                            "i115-s1a-absent.protos"));

                    assertSame(
                            ProtosCoreErrors.prototype(
                                    module,
                                    ProtosCoreErrors.StandardError.SLOT_NOT_FOUND),
                            failure.error().parent().orElseThrow());
                });
    }

    @Test
    void readAfterLocalCreationSelectsNewLocalBinding()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    ProtosObjectValue outerValue =
                            new ProtosObjectValue(ProtosObjectValue.rootObject());
                    ProtosObjectValue localValue =
                            new ProtosObjectValue(ProtosObjectValue.rootObject());
                    module.context().createLocalSlot("x", outerValue);
                    module.context().createLocalSlot("replacement", localValue);

                    assertSame(
                            localValue,
                            executeClosure(
                                    language,
                                    module,
                                    "() => { before: x\nx: replacement\nx }",
                                    "i115-s1a-established.protos"));
                });
    }

    // ---- I115-S1c-L ------------------------------------------------------------

    @Test
    void removedBindingContinuesToOuterBindingThenReceiver()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    ProtosObjectValue outerValue =
                            new ProtosObjectValue(ProtosObjectValue.rootObject());
                    module.context().createLocalSlot("x", outerValue);

                    assertSame(
                            outerValue,
                            executeClosure(
                                    language,
                                    module,
                                    "() => { x: 1\nsaved: context\nsaved.removeSlot(\"x\")\nx }",
                                    "i115-s1cl-removed-outer.protos"));

                    ProtosSignalException failure =
                            assertThrows(
                                    ProtosSignalException.class,
                                    () -> executeClosure(
                                            language,
                                            module,
                                            "() => { y: 1\nsaved: context\nsaved.removeSlot(\"y\")\ny }",
                                            "i115-s1cl-removed-absent.protos"));
                    assertSame(
                            ProtosCoreErrors.prototype(
                                    module,
                                    ProtosCoreErrors.StandardError.SLOT_NOT_FOUND),
                            failure.error().parent().orElseThrow());
                });
    }

    @Test
    void assignmentDestinationIsFixedBeforeTheRightHandSide()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    ProtosObjectValue outerValue =
                            new ProtosObjectValue(ProtosObjectValue.rootObject());
                    module.context().createLocalSlot("x", outerValue);

                    // The RHS removes the selected own binding; the assignment
                    // must fail instead of selecting the outer x afterwards.
                    assertThrows(
                            ProtosSignalException.class,
                            () -> executeClosure(
                                    language,
                                    module,
                                    "() => { x: 1\nsaved: context\nx = saved.removeSlot(\"x\") }",
                                    "i115-s1cl-fixed-destination.protos"));
                    assertSame(outerValue, module.context().readLocalSlot("x").orElseThrow());
                });
    }

    @Test
    void capturedBindingKeepsIdentityAfterContextObservation()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    ProtosObjectValue replacement =
                            new ProtosObjectValue(ProtosObjectValue.rootObject());
                    module.context().createLocalSlot("replacement", replacement);

                    assertSame(
                            replacement,
                            executeClosure(
                                    language,
                                    module,
                                    "() => { x: 1\nreader: () => x\nsaved: context\n"
                                            + "saved.x = replacement\nreader() }",
                                    "i115-s1cl-captured-identity.protos"));
                });
    }

    private static Object executeClosure(
            ProtosLanguage language,
            ProtosActivation module,
            String characters,
            String sourceName) {
        ProtosClosureValue closure =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        lowerRoot(language, characters, sourceName)
                                .getCallTarget().call(module));
        ProtosActivation invocation =
                ProtosActivation.forClosureInvocation(
                        closure,
                        List.of(),
                        module.prelude().orElseThrow(),
                        module.actorModuleState(),
                        module.currentModuleKey().orElse(null),
                        module.executionDomain());
        return closure.executionPlan().orElseThrow()
                .executeBytecodeActivationForTesting(invocation);
    }

    @Test
    void dynamicReadRetainsExactGenericFallback()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    String characters = "Context";
                    ProtosBytecodeRootNode root =
                            lowerRoot(language, characters, "i068-slice7-dynamic.protos");

                    List<String> instructions = instructionNames(root);

                    assertContains(instructions, "Lookup");
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            root.getCallTarget().call(module));
                });
    }

    private static ProtosBytecodeRootNode lowerRoot(
            ProtosLanguage language,
            String characters,
            String sourceName) {
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                characters,
                                sourceName)
                        .build();

        return new CanonicalToBytecodeLowerer(language, source)
                .lowerRoot(canonicalize(characters));
    }

    private static List<String> instructionNames(
            ProtosBytecodeRootNode root) {
        return root.getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .map(ProtosInstructionNames::of)
                .toList();
    }

    private static void assertContains(
            List<String> instructions,
            String expected) {
        assertTrue(
                instructions.stream()
                        .anyMatch(name -> name.contains(expected)),
                () ->
                        "expected instruction containing "
                                + expected
                                + ": "
                                + instructions);
    }

    private static void assertNotContains(
            List<String> instructions,
            String forbidden) {
        assertFalse(
                instructions.stream()
                        .anyMatch(name -> name.contains(forbidden)),
                () ->
                        "unexpected instruction containing "
                                + forbidden
                                + ": "
                                + instructions);
    }

    private static CanonicalSequence canonicalize(
            String characters) {
        return (CanonicalSequence)
                new Canonicalizer()
                        .canonicalize(
                                new ProtosParser(characters)
                                        .parseProgram());
    }

    private interface LanguageTest {
        void run(
                ProtosLanguage language,
                ProtosActivation module)
                throws Exception;
    }

    private static void withEnteredLanguage(
            LanguageTest test)
            throws Exception {
        try (Context context =
                Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                test.run(
                        LANGUAGE_REF.get(null),
                        moduleActivation());
            } finally {
                context.leave();
            }
        }
    }

    private static ProtosActivation moduleActivation() {
        ProtosStandardObjectProtocol.install();

        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject());
        ProtosObjectValue bindings =
                new ProtosObjectValue(contextPrototype);
        ProtosObjectValue errorPrototype =
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject());

        bindings.createLocalSlot(
                "Context", contextPrototype);
        bindings.createLocalSlot(
                "Error", errorPrototype);
        bindings.createLocalSlot(
                "SlotNotFound",
                new ProtosObjectValue(errorPrototype));
        bindings.createLocalSlot(
                "Array",
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject()));
        bindings.freeze();

        return new ProtosPrelude(
                        bindings, contextPrototype)
                .newModuleActivation();
    }
}
