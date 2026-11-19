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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.Debugger;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.diagnostics.ProtosDiagnosticTrace;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPerf006B5BSourceInstrumentationLocationTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void bytecodeSourceAndTagTreePreserveExactMinimalToolingProjection()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            String characters = "target()\nnull";
            Source source =
                    Source.newBuilder(
                                    ProtosLanguage.ID,
                                    characters,
                                    "perf006-b5b-tags.protos")
                            .build();
            CanonicalSequence sequence = canonicalize(characters);
            ProtosBytecodeRootNode root =
                    new CanonicalToBytecodeLowerer(scope.language(), source)
                            .lowerRoot(sequence);

            root.getRootNodes().ensureComplete();
            SourceSection rootSection = root.ensureSourceSection();
            assertSame(source, rootSection.getSource());
            assertEquals(sequence.span().startOffset(), rootSection.getCharIndex());
            assertEquals(sequence.span().length(), rootSection.getCharLength());
            assertEquals(
                    characters.substring(
                            sequence.span().startOffset(),
                            sequence.span().endOffset()),
                    rootSection.getCharacters().toString());

            TagTree tree = root.getBytecodeNode().getTagTree();
            List<TagTree> statements =
                    collectTags(tree, StandardTags.StatementTag.class);
            List<TagTree> expressions =
                    collectTags(tree, StandardTags.ExpressionTag.class);
            List<TagTree> calls = collectTags(tree, StandardTags.CallTag.class);

            assertEquals(2, statements.size());
            assertEquals(
                    List.of("target()", "null"),
                    sourceCharacters(statements));
            assertTrue(
                    statements.stream()
                            .allMatch(tag -> tag.getSourceSection().getSource() == source));

            assertEquals(
                    statements.size(),
                    expressions.size(),
                    "PLAT034 Expression membership must equal Statement membership");
            assertEquals(
                    sourceCharacters(statements),
                    sourceCharacters(expressions),
                    "Expression and Statement tags must own identical source ranges");
            for (int index = 0; index < statements.size(); index++) {
                assertSame(
                        statements.get(index),
                        expressions.get(index),
                        "Bytecode DSL must use one shared tag region for Statement+Expression");
            }

            assertEquals(1, calls.size());
            assertEquals("target()", calls.get(0).getSourceSection().getCharacters().toString());
            assertSame(source, calls.get(0).getSourceSection().getSource());

            // PLAT042 B′: the source root is the tagged semantic root itself.
            assertEquals(1, collectTags(tree, StandardTags.RootTag.class).size());
            assertEquals(1, collectTags(tree, StandardTags.RootBodyTag.class).size());
            assertEquals(
                    0,
                    collectTags(tree, DebuggerTags.AlwaysHalt.class).size(),
                    "PLAT034 provides AlwaysHalt compatibility but no guest halt locations");
        }

        System.out.println("PERF006_B5B_ROOT_SOURCE_IDENTITY=PASS");
        System.out.println("PERF006_B5B_STATEMENT_TAG_SOURCE_EXACT=PASS");
        System.out.println("PERF006_B5B_CALL_TAG_SOURCE_EXACT=PASS");
        System.out.println("PERF006_B5B_PLAT034_EXPRESSION_EQUALS_STATEMENT=PASS");
        System.out.println("PERF006_B5B_PLAT034_ALWAYS_HALT_LOCATIONS=ZERO");
    }

    /**
     * The fused indexed operations keep the Statement/Expression regions and the
     * exact source sections of their sends, receiver, index and RHS; like the
     * fused numeric operations they carry no CallTag, so a standard access is
     * never presented as a dynamic invocation. Each operand is evaluated once,
     * before its operation.
     */
    @Test
    void fusedIndexedOperationsKeepSourceSectionsWithoutAnInvocationTag()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            String characters = "a[k + 1] = k * 2\na[k - 1]\na.size()";
            Source source =
                    Source.newBuilder(
                                    ProtosLanguage.ID,
                                    characters,
                                    "perf006-b5b-indexed.protos")
                            .build();
            ProtosBytecodeRootNode root =
                    new CanonicalToBytecodeLowerer(scope.language(), source)
                            .lowerRoot(canonicalize(characters));
            root.getRootNodes().ensureComplete();

            TagTree tree = root.getBytecodeNode().getTagTree();
            List<TagTree> statements =
                    collectTags(tree, StandardTags.StatementTag.class);
            assertEquals(
                    List.of("a[k + 1] = k * 2", "a[k - 1]", "a.size()"),
                    sourceCharacters(statements));
            assertEquals(statements, collectTags(tree, StandardTags.ExpressionTag.class));
            assertEquals(
                    List.of(),
                    sourceCharacters(collectTags(tree, StandardTags.CallTag.class)),
                    "a fused indexed operation emits no separate CallTag");

            List<com.oracle.truffle.api.bytecode.Instruction> instructions =
                    root.getBytecodeNode().getInstructionsAsList();
            int setItem = onlyInstruction(instructions, "c.SetItem");
            int subscript = onlyInstruction(instructions, "c.BinarySubscript");
            int length = onlyInstruction(instructions, "c.GetLen");
            int index = onlyInstruction(instructions, "c.NumberAdd");
            int rhs = onlyInstruction(instructions, "c.NumberMultiply");
            int readIndex = onlyInstruction(instructions, "c.NumberSubtract");

            assertEquals("a[k + 1] = k * 2", sectionOf(instructions, setItem));
            assertEquals("a[k - 1]", sectionOf(instructions, subscript));
            assertEquals("a.size()", sectionOf(instructions, length));
            assertEquals("k + 1", sectionOf(instructions, index));
            assertEquals("k * 2", sectionOf(instructions, rhs));
            assertEquals("k - 1", sectionOf(instructions, readIndex));
            // Index, then RHS, then the write: each evaluated once, before its operation.
            assertTrue(index < rhs && rhs < setItem && setItem < readIndex
                            && readIndex < subscript && subscript < length,
                    instructions.toString());
        }
    }

    /**
     * With a debugger session attached, the instrumented indexed sites keep the
     * guest result, evaluate receiver, index and RHS exactly once in order, and
     * a redefined selection is still invoked inside the operation; an Error
     * signaled by the redefined method still reaches the host as a guest
     * Error. The terminal provenance of that Error is checked separately, in
     * an execution without a debugger, by
     * {@link #redefinedSelectionErrorLocatesTheAccessAndTheSignal()}.
     */
    @Test
    void instrumentedIndexedSitesKeepResultsOrderAndRedefinedErrors() {
        String trace = "trace: { value: 0 }\n"
                + "step: (digit, value) => {\n"
                + "    trace.value = trace.value * 10 + digit\n"
                + "    value\n"
                + "}\n";
        String program = trace + """
                Box: Array { at: (index) => { 100 + index } }
                a: [1, 2, 3]
                b: Box(1, 2)
                step(1, a)[step(2, 0)] = step(3, 9)
                r: step(4, a)[step(5, 0)] + step(6, b)[step(7, 1)]
                s: step(8, a).size()
                trace.value * 1000 + r + s
                """;
        try (Context context = Context.newBuilder(ProtosLanguage.ID)
                        .option("protos.CoreRoot",
                                Path.of("protos", "lib", "core").toAbsolutePath().toString())
                        .build();
                DebuggerSession session =
                        Debugger.find(context.getEngine()).startSession(event -> {})) {
            assertEquals(12345678113L, context.eval(ProtosLanguage.ID, program).asLong());

            org.graalvm.polyglot.PolyglotException error = null;
            try {
                context.eval(ProtosLanguage.ID, REDEFINED_FAILING);
            } catch (org.graalvm.polyglot.PolyglotException caught) {
                error = caught;
            }
            assertNotNull(error, "the redefined at must signal its Error");
            assertTrue(error.isGuestException(), error.toString());
        }
    }

    private static final String REDEFINED_FAILING_NAME =
            "perf006-b5b-redefined-failing.protos";
    private static final String REDEFINED_FAILING = """
            Failing: Array { at: (index) => { Error().signal() } }
            f: Failing(1)
            read: () => {
                f[0]
            }
            read()
            """;

    private static final String RESUMED_FAILING_NAME =
            "perf006-b5b-resumed-failing.protos";
    private static final String RESUMED_FAILING = """
            Late: Array {
                at: (index) => {
                    (() => { 7 }).future().value()
                    Error().signal()
                }
            }
            l: Late(1)
            read: () => {
                l[0]
            }
            read()
            """;

    private static final String SIGNAL = "Error().signal()";

    /*
     * The resumed scenario suspends and resumes the root task itself: an Error
     * signaled inside a producer Task would reach the root only through a
     * Future, and the terminal trace of a failed Future is consumer-local by
     * contract, so it could not name the producer's access or signal.
     *
     * Terminal provenance, without a debugger: this is a separate execution
     * through the terminal-outcome boundary, not the instrumented one above.
     */
    @Test
    void redefinedSelectionErrorLocatesTheAccessAndTheSignal() throws Exception {
        ProtosExecutionOutcome outcome = ProtosTestExecutionSupport.execute(
                REDEFINED_FAILING_NAME, REDEFINED_FAILING, core().newModuleActivation());
        assertAccessAndSignalLocations(
                failedTrace(outcome), REDEFINED_FAILING_NAME, REDEFINED_FAILING, "f[0]");
    }

    @Test
    void resumedSelectionErrorLocatesTheAccessAndTheSignal() throws Exception {
        ProtosExecutionOutcome outcome = ProtosTestExecutionSupport.execute(
                RESUMED_FAILING_NAME, RESUMED_FAILING, core().newModuleActivation());
        assertAccessAndSignalLocations(
                failedTrace(outcome), RESUMED_FAILING_NAME, RESUMED_FAILING, "l[0]");
    }

    private static ProtosDiagnosticTrace failedTrace(ProtosExecutionOutcome outcome) {
        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state(),
                () -> "value=" + outcome.value());
        return outcome.failureDiagnosticTrace().orElseThrow();
    }

    /*
     * The terminal diagnostic trace of an Error signaled by a selected indexed
     * behavior names two distinct positions of the scenario's own source,
     * innermost first: the signal inside the selected method body and, in an
     * outer frame, the indexed access that invoked the behavior. Positions are
     * coordinates: each frame must be on the exact line holding its expression
     * and at a column inside that expression; any other line or column of the
     * source is rejected, and the signal position never stands in for the
     * access position.
     */
    private static void assertAccessAndSignalLocations(
            ProtosDiagnosticTrace trace, String sourceName, String source, String access) {
        List<ProtosDiagnosticTrace.Frame> frames = trace.frames();
        int accessFrame = -1;
        for (int i = 0; i < frames.size() && accessFrame < 0; i++) {
            if (locatedAt(frames.get(i), sourceName, source, access)) {
                accessFrame = i;
            }
        }
        assertTrue(accessFrame >= 0,
                "no guest frame located at " + access + ": " + framesOf(frames));
        boolean signalFrame = false;
        for (int i = 0; i < accessFrame && !signalFrame; i++) {
            signalFrame = locatedAt(frames.get(i), sourceName, source, SIGNAL);
        }
        assertTrue(signalFrame,
                "no signal frame distinct from and inner to " + access + ": "
                        + framesOf(frames));
    }

    /* The frame's line holds the only occurrence of the expression and its column lies inside it. */
    private static boolean locatedAt(
            ProtosDiagnosticTrace.Frame frame, String sourceName, String source, String expression) {
        if (!sourceName.equals(frame.sourceName())) {
            return false;
        }
        String[] lines = source.split("\n", -1);
        int line = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(expression)) {
                assertEquals(-1, line, expression + " occurs on more than one line");
                line = i + 1;
            }
        }
        assertTrue(line > 0, expression + " does not occur in " + sourceName);
        int firstColumn = lines[line - 1].indexOf(expression) + 1;
        int lastColumn = firstColumn + expression.length() - 1;
        return frame.line() == line
                && frame.column() >= firstColumn && frame.column() <= lastColumn;
    }

    private static List<String> framesOf(List<ProtosDiagnosticTrace.Frame> frames) {
        return frames.stream()
                .map(frame -> frame.sourceName() + ":" + frame.line() + ":" + frame.column()
                        + frame.knownLabel().map(label -> " " + label).orElse(""))
                .toList();
    }

    /**
     * With a debugger session attached, a selected {@code at} that suspends
     * inside a Task resumes without repeating the receiver or index
     * operands, and an Error it signals after resuming still reaches the
     * host as a guest Error. The terminal provenance of that Error is checked
     * separately, in an execution without a debugger, by
     * {@link #resumedSelectionErrorLocatesTheAccessAndTheSignal()}.
     */
    @Test
    void instrumentedSuspendingIndexedSiteKeepsItsAccessSourceAcrossResumption() {
        String trace = "trace: { value: 0 }\n"
                + "step: (digit, value) => {\n"
                + "    trace.value = trace.value * 10 + digit\n"
                + "    value\n"
                + "}\n";
        String resuming = trace + """
                Lazy: Array {
                    at: (index) => {
                        trace.value = trace.value * 10 + 9
                        (() => { 40 + index }).future().value()
                    }
                }
                l: Lazy(1, 2, 3)
                read: () => { step(1, l)[step(2, 1)] }
                r: (() => { read() }).future().value()
                trace.value * 100 + r
                """;
        try (Context context = Context.newBuilder(ProtosLanguage.ID)
                        .option("protos.CoreRoot",
                                Path.of("protos", "lib", "core").toAbsolutePath().toString())
                        .build();
                DebuggerSession session =
                        Debugger.find(context.getEngine()).startSession(event -> {})) {
            // Receiver (1), index (2) and the selected body (9) each run once.
            assertEquals(12941L, context.eval(ProtosLanguage.ID, resuming).asLong());

            org.graalvm.polyglot.PolyglotException error = null;
            try {
                context.eval(ProtosLanguage.ID, RESUMED_FAILING);
            } catch (org.graalvm.polyglot.PolyglotException caught) {
                error = caught;
            }
            assertNotNull(error, "the resumed at must signal its Error");
            assertTrue(error.isGuestException(), error.toString());
        }
    }

    private static int onlyInstruction(
            List<com.oracle.truffle.api.bytecode.Instruction> instructions, String prefix) {
        int found = -1;
        for (int i = 0; i < instructions.size(); i++) {
            if (ProtosInstructionNames.of(instructions.get(i)).startsWith(prefix)) {
                assertEquals(-1, found, "more than one " + prefix + ": " + instructions);
                found = i;
            }
        }
        assertTrue(found >= 0, "missing " + prefix + ": " + instructions);
        return found;
    }

    private static String sectionOf(
            List<com.oracle.truffle.api.bytecode.Instruction> instructions, int index) {
        SourceSection section = instructions.get(index).getSourceSection();
        assertNotNull(section, instructions.get(index).toString());
        return section.getCharacters().toString();
    }

    /**
     * PLAT067-B: with and without an attached (inactive) debugger session the one lowering
     * requests no tags until an explicit update, then materializes the explicit semantic RootTag
     * in place, keeping the root group and CallTarget identities.
     */
    @Test
    void explicitSemanticRootTagReparsesLazilyWithAndWithoutDebugger() throws Exception {
        for (String mode : List.of("no debugger", "debugger attached")) {
            try (LanguageScope scope = languageScope();
                    DebuggerSession attached =
                            mode.equals("debugger attached")
                                    ? Debugger.find(scope.context().getEngine())
                                            .startSession(event -> {})
                                    : null) {
                String characters = "target()\nnull";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "i111-a-explicit-root-tag.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);
                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(scope.language(), source)
                                .lowerRoot(sequence);

                // BytecodeConfig.DEFAULT requests no tag materialization.
                assertNull(
                        root.getBytecodeNode().getTagTree(),
                        mode + ": the TagTree must not be materialized before an update");
                BytecodeRootNodes<?> group = root.getRootNodes();
                RootCallTarget callTarget = root.getCallTarget();

                group.update(
                        ProtosBytecodeRootNodeGen.newConfigBuilder()
                                .addSource()
                                .addTag(StandardTags.RootTag.class)
                                .addTag(StandardTags.RootBodyTag.class)
                                .build());
                assertExplicitSemanticRootTag(mode, root, sequence, source);
                assertSame(group, root.getRootNodes(), mode + ": group identity");
                assertSame(callTarget, root.getCallTarget(), mode + ": CallTarget identity");
                assertTrue(group.getNodes().contains(root), mode + ": root group membership");

                group.ensureComplete();
                assertExplicitSemanticRootTag(mode, root, sequence, source);
                assertSame(group, root.getRootNodes(), mode + ": group identity");
                assertSame(callTarget, root.getCallTarget(), mode + ": CallTarget identity");
            }
        }

        System.out.println("I111_A_EXPLICIT_ROOT_TAG_LAZY_REPARSE=PASS");
    }

    private static void assertExplicitSemanticRootTag(
            String mode,
            ProtosBytecodeRootNode root,
            CanonicalSequence sequence,
            Source source) {
        TagTree tree = root.getBytecodeNode().getTagTree();
        assertNotNull(tree, mode + ": the TagTree must be materialized after the update");
        List<TagTree> rootTags = collectTags(tree, StandardTags.RootTag.class);
        assertEquals(1, rootTags.size(), mode + ": exactly one explicit RootTag");
        assertEquals(
                1,
                collectTags(tree, StandardTags.RootBodyTag.class).size(),
                mode + ": exactly one physical RootBodyTag");
        SourceSection section = rootTags.get(0).getSourceSection();
        assertNotNull(section, mode + ": the RootTag must carry a SourceSection");
        assertSame(source, section.getSource());
        assertEquals(sequence.span().startOffset(), section.getCharIndex());
        assertEquals(sequence.span().length(), section.getCharLength());
    }

    @Test
    void composedContinuationRetainsStableCallerAndCalleeLocationsWithoutPrefixReplay()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActivation module = prelude.newModuleActivation();
            AtomicInteger prefixCalls = new AtomicInteger();
            ProtosObjectValue marker =
                    new ProtosObjectValue(ProtosObjectValue.rootObject());

            module.context().createLocalSlot(
                    "probe",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                prefixCalls.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));

            String childCharacters = "() => { null }";
            Source childSource =
                    Source.newBuilder(
                                    ProtosLanguage.ID,
                                    childCharacters,
                                    "perf006-b5b-child.protos")
                            .build();
            CanonicalClosure childDefinition = closureDefinition(childCharacters);
            ProtosBytecodeRootNode childRoot =
                    yieldingRoot(
                            scope.language(),
                            childSource,
                            childDefinition.body().span(),
                            marker);
            childRoot.getRootNodes().ensureComplete();
            ProtosClosureExecutionPlan childPlan =
                    ProtosClosureExecutionPlan.bytecode(
                            childDefinition,
                            scope.language(),
                            childSource,
                            childRoot);
            ProtosBytecodeRootNode childSemanticRoot =
                    assertInstanceOf(
                            ProtosBytecodeRootNode.class,
                            childPlan
                                    .bytecodeActivationTargetForComposition()
                                    .getRootNode());
            module.context().createLocalSlot(
                    "entry",
                    semanticClosure(
                            childDefinition,
                            childPlan,
                            module));

            String parentCharacters = "probe()\nentry()";
            Source parentSource =
                    Source.newBuilder(
                                    ProtosLanguage.ID,
                                    parentCharacters,
                                    "perf006-b5b-parent.protos")
                            .build();
            ProtosBytecodeRootNode parentRoot =
                    new CanonicalToBytecodeLowerer(scope.language(), parentSource)
                            .lowerRoot(canonicalize(parentCharacters));
            parentRoot.getRootNodes().ensureComplete();

            Object first = parentRoot.getCallTarget().call(module);
            assertEquals(1, prefixCalls.get());

            /*
             * I112 / PLAT062-T: entry() is the parent's terminal call, so the parent root returns
             * its terminal transport instead of a caller ContinuationResult. The transport keeps
             * the logical location of that call; the child keeps its own continuation.
             */
            ProtosTailCallContinuation parent =
                    assertInstanceOf(ProtosTailCallContinuation.class, first);
            ContinuationResult child =
                    assertInstanceOf(ContinuationResult.class, parent.pendingResult());
            ProtosActivation observed = activationOf(child);
            assertSame(observed, activationOf(child), "tooling observes the same invocation");

            BytecodeLocation parentLocation = parent.callerLocation();
            BytecodeLocation childLocation = child.getBytecodeLocation();
            assertNotNull(parentLocation);
            assertNotNull(childLocation);
            parentLocation = parentLocation.ensureSourceInformation();
            childLocation = childLocation.ensureSourceInformation();

            SourceSection parentSection = parentLocation.getSourceLocation();
            SourceSection childSection = childLocation.getSourceLocation();
            assertNotNull(parentSection);
            assertNotNull(childSection);
            assertSame(parentSource, parentSection.getSource());
            assertEquals("entry()", parentSection.getCharacters().toString());
            assertSame(childSource, childSection.getSource());
            assertEquals(
                    childCharacters.substring(
                            childDefinition.body().span().startOffset(),
                            childDefinition.body().span().endOffset()),
                    childSection.getCharacters().toString());

            assertSame(
                    parentRoot,
                    parentLocation.getBytecodeNode().getBytecodeRootNode(),
                    "logical caller location must remain owned by the same Bytecode root");
            assertSame(
                    childSemanticRoot,
                    childLocation.getBytecodeNode().getBytecodeRootNode(),
                    "logical callee location must remain owned by the semantic Bytecode composition root");

            Object completed = ProtosTailCallContinuation.resume(parent, ProtosNullValue.INSTANCE);
            assertSame(marker, completed);
            assertEquals(
                    1,
                    prefixCalls.get(),
                    "completed parent statement before suspension must not replay");

            BytecodeLocation updatedParentLocation = parentLocation.update();
            BytecodeLocation updatedChildLocation = childLocation.update();
            assertSame(
                    parentRoot,
                    updatedParentLocation.getBytecodeNode().getBytecodeRootNode());
            assertSame(
                    childSemanticRoot,
                    updatedChildLocation.getBytecodeNode().getBytecodeRootNode());

            SourceSection updatedParentSection = updatedParentLocation.getSourceLocation();
            SourceSection updatedChildSection = updatedChildLocation.getSourceLocation();
            assertNotNull(updatedParentSection);
            assertNotNull(updatedChildSection);
            assertSame(parentSource, updatedParentSection.getSource());
            assertEquals(parentSection.getCharIndex(), updatedParentSection.getCharIndex());
            assertEquals(parentSection.getCharLength(), updatedParentSection.getCharLength());
            assertEquals(
                    parentSection.getCharacters().toString(),
                    updatedParentSection.getCharacters().toString());
            assertSame(childSource, updatedChildSection.getSource());
            assertEquals(childSection.getCharIndex(), updatedChildSection.getCharIndex());
            assertEquals(childSection.getCharLength(), updatedChildSection.getCharLength());
            assertEquals(
                    childSection.getCharacters().toString(),
                    updatedChildSection.getCharacters().toString());
        }

        System.out.println("PERF006_B5B_CONTINUATION_CALLER_LOCATION=PASS");
        System.out.println("PERF006_B5B_CONTINUATION_CALLEE_LOCATION=PASS");
        System.out.println("PERF006_B5B_LOGICAL_LOCATION_STABLE_AFTER_RESUME=PASS");
        System.out.println("PERF006_B5B_COMPLETED_PREFIX_REPLAY=NO");
    }

    private static ProtosBytecodeRootNode yieldingRoot(
            ProtosLanguage language,
            Source source,
            SourceSpan span,
            Object finalValue) {
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(span.startOffset(), span.length());
                            builder.beginRoot();
                            builder.beginTag(StandardTags.RootTag.class);
                            builder.beginBlock();
                            builder.beginTag(StandardTags.StatementTag.class);
                            builder.beginYield();
                            builder.emitLoadArgument(0);
                            builder.endYield();
                            builder.endTag(StandardTags.StatementTag.class);
                            builder.beginReturn();
                            builder.emitLoadConstant(finalValue);
                            builder.endReturn();
                            builder.endBlock();
                            builder.endTag(StandardTags.RootTag.class);
                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });
        return roots.getNode(0);
    }

    private static List<TagTree> collectTags(
            TagTree tree,
            Class<? extends com.oracle.truffle.api.instrumentation.Tag> tag) {
        List<TagTree> result = new ArrayList<>();
        if (tree == null) {
            return result;
        }
        if (tree.hasTag(tag)) {
            result.add(tree);
        }
        for (TagTree child : tree.getTreeChildren()) {
            result.addAll(collectTags(child, tag));
        }
        return result;
    }

    private static List<String> sourceCharacters(List<TagTree> tags) {
        return tags.stream()
                .map(tag -> tag.getSourceSection().getCharacters().toString())
                .toList();
    }

    private static ProtosClosureValue semanticClosure(
            CanonicalClosure definition,
            ProtosClosureExecutionPlan plan,
            ProtosActivation creator) {
        return new ProtosClosureValue(
                definition,
                creator.lexicalContextsForClosureCapture(),
                creator.receiver(),
                creator.methodHome().orElse(null),
                creator.returnHome().orElse(null),
                creator.prelude().orElseThrow(),
                plan);
    }

    private static CanonicalClosure closureDefinition(String characters) {
        return assertInstanceOf(
                CanonicalClosure.class,
                canonicalize(characters).expressions().get(0));
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer()
                        .canonicalize(new ProtosParser(characters).parseProgram());
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private static LanguageScope languageScope() {
        Context context = Context.newBuilder(ProtosLanguage.ID).build();
        context.initialize(ProtosLanguage.ID);
        context.enter();
        return new LanguageScope(context, LANGUAGE_REF.get(null));
    }

    private record LanguageScope(Context context, ProtosLanguage language)
            implements AutoCloseable {
        @Override
        public void close() {
            context.leave();
            context.close();
        }
    }

    private static ProtosActivation activationOf(ContinuationResult continuation) {
        // The compact B5 header publishes its genuine activation only on observation.
        return ProtosFrameArguments.activation(continuation.getFrame());
    }

}
