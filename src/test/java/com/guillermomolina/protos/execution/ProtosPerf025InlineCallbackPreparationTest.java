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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF025 / PLAT044 B′ focal structural evidence for pay-as-you-grow inline
 * callback preparation.
 *
 * <p>An eligible immediate literal callback must first perform authoritative
 * ordinary {@code call} selection and retain only its compact invocation
 * carrier. The generated source root must test PLAT044 admission before
 * materializing the callback activation, and the physical
 * {@code PreparedClosureCall} fallback must exist only on the miss branch.
 *
 * <p>This is deliberately structural rather than a benchmark: PERF026 already
 * owns the observable Boolean/while/each semantics, tooling, control-transfer
 * and suspension regressions. This test freezes only the PERF025 preparation
 * boundary shared by those families.
 */
final class ProtosPerf025InlineCallbackPreparationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void booleanLiteralAdmissionPrecedesActivationAndPhysicalFallback() throws Exception {
        assertPreparationOrder(
                """
                run: () => {
                    true.ifTrue(() => {
                        probe()
                        1
                    })
                }
                run()
                """,
                "PrepareInlineStructuredBooleanCallbackCall",
                "AdmitsInlineLiteralCallback",
                "PrepareStructuredBooleanCallbackCall");

        System.out.println(
                "PERF025_PLAT044_BOOLEAN_RICH_PREPARATION_BEFORE_ADMISSION=NO");
    }

    @Test
    void whileLiteralAdmissionPrecedesActivationAndPhysicalFallback() throws Exception {
        assertPreparationOrder(
                """
                run: () => {
                    n: 0
                    (() => {
                        probe()
                        n < 1
                    }).whileTrue() {
                        probe()
                        n = n + 1
                    }
                }
                run()
                """,
                "PrepareInlineStructuredWhileConditionCall",
                "AdmitsInlineLiteralWhileCondition",
                "PrepareStructuredWhileConditionCall");

        System.out.println(
                "PERF025_PLAT044_WHILE_RICH_PREPARATION_BEFORE_ADMISSION=NO");
    }

    @Test
    void eachLiteralAdmissionPrecedesActivationAndPhysicalFallback() throws Exception {
        assertPreparationOrder(
                """
                run: () => {
                    Array(1).each((element) => {
                        probe(element)
                        element
                    })
                }
                run()
                """,
                "PrepareInlineStructuredLocalEachChildCall",
                "AdmitsInlineLiteralLocalEachChild",
                "PrepareStructuredLocalEachChildCall");

        System.out.println(
                "PERF025_PLAT044_EACH_RICH_PREPARATION_BEFORE_ADMISSION=NO");
    }

    private static void assertPreparationOrder(
            String characters,
            String prepareInline,
            String admission,
            String forbiddenRichPreparation)
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                Probe probe = new Probe();
                ProtosSemanticBytecodeRootNode root = lowerRoot(characters);
                root.getCallTarget().call(probe.module());

                ProtosSemanticBytecodeRootNode callbackHost =
                        probe.callbackHost.get();

                assertNotNull(
                        callbackHost,
                        "callback must execute in a semantic source root");

                List<String> names = instructionNames(callbackHost);

                assertOrdered(
                        names,
                        prepareInline,
                        admission,
                        "LoadInlineCallbackActivation",
                        "LoadInlineLiteralFallbackCall");

                assertFalse(
                        names.stream()
                                .anyMatch(
                                        name ->
                                                name.contains(
                                                        forbiddenRichPreparation)),
                        () ->
                                "inline source root must not contain "
                                        + "pre-admission rich preparation "
                                        + forbiddenRichPreparation
                                        + ": "
                                        + names);
            } finally {
                context.leave();
            }
        }
    }

    /**
     * The linear Bytecode instruction order reflects branch construction:
     * preparation and admission are common-prefix operations, the activation
     * load is emitted only in the admitted then-branch, and fallback
     * materialization is emitted in the later else-branch.
     */
    private static void assertOrdered(
            List<String> names,
            String... operations) {
        int previous = -1;

        for (String operation : operations) {
            int expectedAfter = previous;
            int current =
                    indexOfContainingAfter(
                            names,
                            operation,
                            expectedAfter);

            assertTrue(
                    current >= 0,
                    () ->
                            "missing "
                                    + operation
                                    + " after instruction "
                                    + expectedAfter
                                    + " in "
                                    + names);

            previous = current;
        }
    }

    private static int indexOfContainingAfter(
            List<String> names,
            String operation,
            int previous) {
        for (int index = previous + 1; index < names.size(); index++) {
            if (names.get(index).contains(operation)) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> instructionNames(
            ProtosSemanticBytecodeRootNode root) {
        root.getRootNodes().ensureComplete();

        return root.getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .map(Instruction::getName)
                .toList();
    }

    private static ProtosSemanticBytecodeRootNode lowerRoot(
            String characters) {
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                characters,
                                "perf025-inline-callback-preparation.protos")
                        .build();

        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer()
                                .canonicalize(
                                        new ProtosParser(characters)
                                                .parseProgram());

        return new CanonicalToBytecodeLowerer(
                        LANGUAGE_REF.get(null),
                        source)
                .lowerRoot(sequence);
    }

    /**
     * Native callback used only to recover the semantic source root that owns
     * the admitted inline region. PLAT044 removes the callback frame itself, so
     * the first semantic frame observed here is the enclosing Closure root.
     */
    private static final class Probe {
        private final AtomicReference<ProtosSemanticBytecodeRootNode>
                callbackHost = new AtomicReference<>();

        ProtosActivation module() throws Exception {
            ProtosActivation module =
                    new ProtosCoreBootstrap()
                            .bootstrap(CORE)
                            .newModuleActivation();

            module.context()
                    .createLocalSlot(
                            "probe",
                            ProtosClosureValue.nativeClosure(
                                    (activation, supplied) -> {
                                        Truffle.getRuntime()
                                                .iterateFrames(
                                                        frame -> {
                                                            RootNode root =
                                                                    ((RootCallTarget)
                                                                                    frame
                                                                                            .getCallTarget())
                                                                            .getRootNode();

                                                            if (root
                                                                    instanceof
                                                                    ProtosSemanticBytecodeRootNode
                                                                            semantic) {
                                                                callbackHost
                                                                        .compareAndSet(
                                                                                null,
                                                                                semantic);
                                                                return semantic;
                                                            }

                                                            return null;
                                                        });

                                        return ProtosNullValue.INSTANCE;
                                    }));

            return module;
        }
    }
}
