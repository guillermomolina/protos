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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.bytecode.Instruction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * I115-H7 EXPERIMENTAL: GraalPy-shaped physical convergence of the
 * {@code method-call} workload (Bytecode While, ReadGlobal, GetMethod,
 * CallBinaryMethod, NumberAdd, NumberInPlaceAdd, Lt, CopyArguments), and
 * I115-H7-R1 regression protection for the contracts those operations must
 * keep (caller protocol, rebinding, numeric exactness, strict conditions,
 * evaluation order and side effects).
 *
 * <p>These tests pin the physical Bytecode DSL and the behavior actually
 * implemented. They do not claim conformance of the recorded experimental
 * divergences (docs/experiments/I115-H7-GRAALPY-PHYSICAL-CONVERGENCE.md) and
 * produce no Tier 1/Tier 2 data.
 */
final class ProtosI115H7MethodCallConvergenceTest {

    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final Path OUTPUT = Path.of("target", "i115-h7-method-call");

    private static final int WARM = 32;

    private static final String METHOD_CALL = """
            receiver: {
                identity: (value) => { value }
            }

            run: () => {
                i: 0
                sink: 0

                (() => i < 1000).whileTrue() {
                    sink = sink + receiver.identity(i)
                    i = i + 1
                }

                sink
            }
            """;

    /**
     * The exact run root of METHOD_CALL (quickening suffixes removed): both
     * assignments {@code x = x + e} are NumberInPlaceAdd, the condition is
     * one Lt consumed by the While exit branch. I115-H7-R3: the send is
     * ReadGlobal (bound once as a Block stack value), GetMethod over its
     * first reload (emitted as dup, being immediately after the bind), the
     * second reload, the argument, CallBinaryMethod(callable, receiver,
     * argument), and the Block's store.stackvalue cleanup that leaves the
     * call result in place of the bound receiver.
     */
    private static final List<String> RUN_DSL = List.of(
            "c.EnterRoot",
            "load.constant",
            "store.local",
            "load.constant",
            "store.local",
            "load.local",
            "load.constant",
            "c.Lt",
            "branch.false",
            "load.local",
            "c.ReadGlobal",
            "dup",
            "c.GetMethod",
            "load.stackvalue",
            "load.local",
            "c.CallBinaryMethod",
            "store.stackvalue",
            "c.NumberInPlaceAdd",
            "store.local",
            "load.local",
            "load.constant",
            "c.NumberInPlaceAdd",
            "store.local",
            "branch.backward",
            "load.local",
            "c.EpilogForReturn",
            "return");

    /** The identity root: CopyArguments prologue and one direct local read. */
    private static final List<String> IDENTITY_DSL = List.of(
            "c.EnterRoot",
            "c.CopyArguments",
            "load.local",
            "c.EpilogForReturn",
            "return");

    /** Machinery the H7 path replaces; never emitted by an admitted compact root. */
    private static final List<String> REPLACED = List.of(
            "PrepareStructuredCall",
            "StructuredPrepareInlineChildCall",
            "AdmitsInlineLiteralWhile",
            "StructuredWhileCondition",
            "StructuredWhileRetainBodyResult",
            "StructuredFinishWithChildResult",
            "CompleteClosureCall",
            "SelectInlineCapturedMaterializedOwnerFrame",
            "ResolveInlineCapturedMaterializedWritableLexicalTarget",
            "load.local.mat",
            "PrepareInlineSendOne",
            "PrepareSendOne",
            "IsStructuredCall",
            "RequiresStructuredDispatch",
            "EnterStraightLineSourceCall",
            "IsPrimitiveNumericSendMiss",
            "MaterializePrimitiveNumericCarrier",
            "TryPrimitiveNumeric",
            "TryDirectSendOne",
            "PromoteFrameLexicalAuthority",
            "InstallFrameLexicalAuthority",
            "ExperimentalCanonicalBooleanCondition",
            "BindUnaryFrameParameter");

    // ---- A. Result and the GraalPy-shaped census -------------------------------------

    @Test
    void methodCallResult() throws Exception {
        try (ProtosStandaloneHostedSession session = open("result", METHOD_CALL)) {
            Value run = session.prepareTopLevel("run").executable();
            for (int call = 0; call < WARM; call++) {
                Value result = run.execute();
                assertTrue(result.isNumber());
                assertTrue(result.fitsInInt());
                assertEquals(499500L, result.asLong());
            }
            System.out.println("I115_H7_RESULT=499500");
        }
    }

    @Test
    void runIsOneDirectCompactRoot() throws Exception {
        try (ProtosStandaloneHostedSession session = open("compact", METHOD_CALL)) {
            ProtosBytecodeRootNode run = root(session, "run");
            List<String> names = unquickened(names(run));
            System.out.println("I115_H7_RUN_INSTRUCTIONS=" + names);
            assertEquals(RUN_DSL, names, "I115-H7 run DSL");
            assertReplacedAbsent("run", names);
            assertDirectLoop(run);
            assertTrue(run.getBytecodeNode().getLocals().stream()
                            .noneMatch(local -> "compactWhileResult".equals(local.getName())),
                    "a statement whileTrue keeps no result local");
            assertEquals(499500L,
                    session.prepareTopLevel("run").executable().execute().asLong());
            // The executed (possibly quickened) bytecode keeps the same operations.
            assertEquals(RUN_DSL, unquickened(names(run)));
        }
    }

    @Test
    void identityIsOneDirectRoot() throws Exception {
        try (ProtosStandaloneHostedSession session = open("identity", METHOD_CALL)) {
            ProtosBytecodeRootNode run = root(session, "run");
            ProtosBytecodeRootNode identity = methodRoot(session, "run", "receiver", "identity");
            assertSame(run.getRootNodes(), identity.getRootNodes(),
                    "identity is a root of the examined compilation");
            assertNotSame(run, identity);
            assertEquals(IDENTITY_DSL, unquickened(names(identity)));
            assertTrue(identity.isExperimentalPrimitiveArgumentRoot());
            assertTrue(identity.provablyNonSuspendingBody(),
                    "the direct CallBinaryMethod tier admits identity");
            assertEquals(499500L,
                    session.prepareTopLevel("run").executable().execute().asLong());
        }
    }

    @Test
    void completeDslCensus() throws Exception {
        try (ProtosStandaloneHostedSession session = open("census", METHOD_CALL)) {
            ProtosBytecodeRootNode run = root(session, "run");
            ProtosBytecodeRootNode identity = methodRoot(session, "run", "receiver", "identity");
            String report = session.processContextForTesting()
                    .callForRuntime(() -> census(run, identity));
            Path output = OUTPUT.resolve("dsl-full.txt");
            Files.writeString(output, report, StandardCharsets.UTF_8);
            System.out.println("I115_H7_DSL_FILE=" + output.toAbsolutePath());
            assertEquals(499500L,
                    session.prepareTopLevel("run").executable().execute().asLong());
        }
    }

    // ---- Evaluation order and per-operation fixtures ---------------------------------

    @Test
    void receiverArgumentAndCallAreEvaluatedOnceInOrder() throws Exception {
        try (ProtosStandaloneHostedSession session = open("order", """
                trace: 0

                target: {
                    record: (value) => {
                        trace = trace * 10 + 3
                        value
                    }
                }

                probe: {
                    pick: (unused) => {
                        trace = trace * 10 + 1
                        target
                    }
                    argument: (value) => {
                        trace = trace * 10 + 2
                        value
                    }
                }

                run: () => {
                    result: probe.pick(0).record(probe.argument(7))
                    result * 1000 + trace
                }
                """)) {
            List<String> names = unquickened(names(root(session, "run")));
            assertEquals(3, count(names, "c.GetMethod"), "three ordinary sends: " + names);
            assertEquals(3, count(names, "c.CallBinaryMethod"), names.toString());
            // I115-H7-R3: each receiver is bound once and reloaded twice (dup, load).
            assertEquals(3, count(names, "dup"), names.toString());
            assertEquals(3, count(names, "load.stackvalue"), names.toString());
            assertEquals(3, count(names, "store.stackvalue"), names.toString());
            // receiver (1), then argument (2), then the call itself (3), once each.
            assertEquals(7123L,
                    session.prepareTopLevel("run").executable().execute().asLong());
        }
    }

    @Test
    void eachOperationHasItsOwnPhysicalSite() throws Exception {
        try (ProtosStandaloneHostedSession session = open("operations", """
                accumulator: {
                    twice: (n) => { n + n }
                }

                plainSum: () => {
                    a: 40
                    b: 2
                    a + b
                }

                accumulate: () => {
                    t: 0
                    t = t + 5
                    t
                }

                loopCompare: () => {
                    k: 0
                    (() => k < 3).whileTrue() {
                        k = 5
                    }
                    k
                }

                sendOne: () => {
                    accumulator.twice(4)
                }

                renamedLoop: () => {
                    k: 0
                    total: 0
                    (() => k < 5).whileTrue() {
                        total = total + accumulator.twice(k)
                        k = k + 1
                    }
                    total
                }
                """)) {
            List<String> sum = unquickened(names(root(session, "plainSum")));
            assertEquals(1, count(sum, "c.NumberAdd"), "a + b is NumberAdd: " + sum);
            assertEquals(0, count(sum, "c.NumberInPlaceAdd"), sum.toString());
            assertReplacedAbsent("plainSum", sum);
            assertNumber(session, "plainSum", 42L);

            List<String> accumulate = unquickened(names(root(session, "accumulate")));
            assertEquals(1, count(accumulate, "c.NumberInPlaceAdd"),
                    "t = t + 5 is NumberInPlaceAdd: " + accumulate);
            assertEquals(0, count(accumulate, "c.NumberAdd"), accumulate.toString());
            assertReplacedAbsent("accumulate", accumulate);
            assertNumber(session, "accumulate", 5L);

            ProtosBytecodeRootNode loopRoot = root(session, "loopCompare");
            List<String> loop = unquickened(names(loopRoot));
            assertEquals(1, count(loop, "c.Lt"), "k < 3 is Lt: " + loop);
            assertTrue(loop.get(loop.indexOf("c.Lt") + 1).equals("branch.false"),
                    "the While branch consumes Lt directly: " + loop);
            assertEquals(0, count(loop, "c.NumberAdd") + count(loop, "c.NumberInPlaceAdd"));
            assertReplacedAbsent("loopCompare", loop);
            assertDirectLoop(loopRoot);
            assertNumber(session, "loopCompare", 5L);

            List<String> send = unquickened(names(root(session, "sendOne")));
            int get = send.indexOf("c.GetMethod");
            int call = send.indexOf("c.CallBinaryMethod");
            assertTrue(get > 0 && call > get, "GetMethod then CallBinaryMethod: " + send);
            assertEquals(List.of("c.ReadGlobal", "dup", "c.GetMethod",
                            "load.stackvalue", "load.constant", "c.CallBinaryMethod",
                            "store.stackvalue"),
                    send.subList(get - 2, call + 2),
                    "receiver bound once, selection, receiver, argument, call");
            assertReplacedAbsent("sendOne", send);
            assertNumber(session, "sendOne", 8L);

            // Different names and selector: the same general operations.
            ProtosBytecodeRootNode renamedRoot = root(session, "renamedLoop");
            List<String> renamed = unquickened(names(renamedRoot));
            for (String required : List.of("c.ReadGlobal", "c.GetMethod",
                    "c.CallBinaryMethod", "c.NumberInPlaceAdd", "c.Lt")) {
                assertTrue(renamed.contains(required), required + ": " + renamed);
            }
            assertReplacedAbsent("renamedLoop", renamed);
            assertDirectLoop(renamedRoot);
            assertNumber(session, "renamedLoop", 20L);

            ProtosBytecodeRootNode twice =
                    methodRoot(session, "renamedLoop", "accumulator", "twice");
            List<String> twiceNames = unquickened(names(twice));
            assertTrue(twiceNames.contains("c.CopyArguments"), twiceNames.toString());
            assertEquals(1, count(twiceNames, "c.NumberAdd"), twiceNames.toString());
        }
    }

    // ---- Numbers ----------------------------------------------------------------------

    @Test
    void primitiveIntegersCrossBoundariesAsGuestNumbers() throws Exception {
        try (ProtosStandaloneHostedSession session = open("integers", """
                receiver: {
                    identity: (value) => { value }
                }

                compareLong: () => {
                    n: 2999999998
                    (() => n < 3000000000).whileTrue() {
                        n = n + 1
                    }
                    n
                }

                sumOverflow: () => {
                    a: 2147483647
                    b: 1
                    a + b
                }

                inPlaceOverflow: () => {
                    i: 2147483647
                    i = i + 1
                    i
                }

                overflowLong: () => {
                    j: 9223372036854775807
                    j = j + 1
                    j
                }

                identityResult: () => {
                    receiver.identity(41) + 1
                }

                identityDirect: () => {
                    receiver.identity(7)
                }

                identityLarge: () => {
                    receiver.identity(2147483647) + 2147483647
                }
                """)) {
            assertNumber(session, "compareLong", 3000000000L);
            // C: exceeding int keeps the exact value and a guest representation.
            assertNumber(session, "sumOverflow", 2147483648L);
            assertNumber(session, "inPlaceOverflow", 2147483648L);
            assertNumber(session, "identityResult", 42L);
            assertNumber(session, "identityDirect", 7L);
            assertNumber(session, "identityLarge", 4294967294L);
            for (int call = 0; call < WARM; call++) {
                assertNumber(session, "sumOverflow", 2147483648L);
            }

            Value large = session.prepareTopLevel("overflowLong").executable().execute();
            assertTrue(large.isNumber(), "large Integer is a guest number");
            assertFalse(large.fitsInLong(), "overflow promotes beyond signed-64");
            assertTrue(large.toString().contains("9223372036854775808"),
                    "exact large Integer: " + large);
        }
    }

    // ---- Conditions -------------------------------------------------------------------

    /** D: a redefined {@code <} returning a non-Boolean is the standard Error. */
    @Test
    void nonBooleanLessSignalsStandardErrorWithoutRunningTheBody() throws Exception {
        try (ProtosStandaloneHostedSession session = open("non-boolean", """
                visits: 0

                tracker: {
                    mark: (n) => {
                        visits = visits + n
                        visits
                    }
                }

                notBoolean: { compare: (other) => { "neither" } }
                notBooleanGate: notBoolean.alias("compare", "<")

                falseSource: { compare: (other) => { false } }
                falseGate: falseSource.alias("compare", "<")

                probeNotBoolean: () => {
                    (() => notBooleanGate < 1).whileTrue() {
                        tracker.mark(1)
                    }
                    0
                }

                probeFalse: () => {
                    (() => falseGate < 1).whileTrue() {
                        tracker.mark(1)
                    }
                    0
                }

                readVisits: () => { visits }
                """)) {
            List<String> names = unquickened(names(root(session, "probeNotBoolean")));
            assertEquals(1, count(names, "c.Lt"), names.toString());
            assertReplacedAbsent("probeNotBoolean", names);

            assertNumber(session, "probeFalse", 0L);
            assertNumber(session, "readVisits", 0L);

            Value probe = session.prepareTopLevel("probeNotBoolean").executable();
            for (int attempt = 0; attempt < 3; attempt++) {
                PolyglotException failure =
                        assertThrows(PolyglotException.class, probe::execute);
                assertTrue(failure.isGuestException(), "a guest Error: " + failure);
                assertFalse(failure.isInternalError(), "not an internal error: " + failure);
            }
            assertNumber(session, "readVisits", 0L);
        }
    }

    /** E: the condition prefix runs once although the body runs zero times. */
    @Test
    void conditionSideEffectRunsWithZeroIterations() throws Exception {
        try (ProtosStandaloneHostedSession session = open("condition-effect", """
                sideEffect: () => {
                    probes: 0
                    n: 5
                    (() => {
                        probes = probes + 1
                        n < 3
                    }).whileTrue() {
                        n = n + 1
                    }
                    probes * 100 + n
                }
                """)) {
            ProtosBytecodeRootNode root = root(session, "sideEffect");
            List<String> names = unquickened(names(root));
            assertEquals(1, count(names, "c.Lt"), names.toString());
            int prefix = names.indexOf("c.NumberInPlaceAdd");
            int test = names.indexOf("c.Lt");
            assertTrue(prefix >= 0 && prefix < test, "the prefix precedes the test: " + names);
            assertEquals(2, root.getRootNodes().getNodes().size(),
                    "module and sideEffect only: no callback roots");
            assertNumber(session, "sideEffect", 105L);
        }
    }

    // ---- Method selection and calls ---------------------------------------------------

    @Test
    void methodSelectionCacheBehavior() throws Exception {
        try (ProtosStandaloneHostedSession session = open("cache", """
                first: {
                    value: (x) => { 1 }
                    succ: (u) => { second }
                }
                second: {
                    value: (x) => { 2 }
                    succ: (u) => { third }
                }
                third: {
                    value: (x) => { 3 }
                    succ: (u) => { fourth }
                }
                fourth: {
                    value: (x) => { 4 }
                    succ: (u) => { first }
                }

                monomorphic: () => {
                    n: 0
                    total: 0
                    (() => n < 8).whileTrue() {
                        total = total + first.value(n)
                        n = n + 1
                    }
                    total
                }

                polymorphic: () => {
                    n: 0
                    total: 0
                    current: first
                    (() => n < 8).whileTrue() {
                        total = total + current.value(n)
                        current = current.succ(0)
                        n = n + 1
                    }
                    total
                }

                plain: { f: 5 }

                useNonClosure: () => { plain.f(1) }
                """)) {
            for (int call = 0; call < WARM; call++) {
                assertNumber(session, "monomorphic", 8L);
                // Four receivers at one site: beyond the PIC limit of three.
                assertNumber(session, "polymorphic", 20L);
            }
            for (String name : List.of("monomorphic", "polymorphic")) {
                List<String> names = unquickened(names(root(session, name)));
                assertTrue(names.contains("c.GetMethod"), name + ": " + names);
                assertReplacedAbsent(name, names);
            }

            /*
             * Generic tier: a selected non-Closure value is sent ordinarily by
             * CallBinaryMethod, never as an internal error.
             */
            Value nonClosure = session.prepareTopLevel("useNonClosure").executable();
            try {
                System.out.println("I115_H7_NON_CLOSURE_SELECTION_OUTCOME="
                        + nonClosure.execute());
            } catch (PolyglotException failure) {
                assertTrue(failure.isGuestException(), "a guest outcome: " + failure);
                assertFalse(failure.isInternalError(), failure.toString());
                System.out.println("I115_H7_NON_CLOSURE_SELECTION_OUTCOME=guest-error");
            }
        }
    }

    /** B: a hot call then redefined uses the new method, not the cached target. */
    @Test
    void redefinedMethodReplacesTheCachedTarget() throws Exception {
        try (ProtosStandaloneHostedSession session = open("redefined", """
                swap: { f: (x) => { x } }

                hot: () => {
                    n: 0
                    total: 0
                    (() => n < 100).whileTrue() {
                        total = total + swap.f(n)
                        n = n + 1
                    }
                    total
                }

                replaceSwap: () => {
                    swap.f = (x) => { 100 }
                    0
                }
                """)) {
            Value hot = session.prepareTopLevel("hot").executable();
            for (int call = 0; call < WARM; call++) {
                assertEquals(4950L, hot.execute().asLong());
            }
            session.prepareTopLevel("replaceSwap").executable().execute();
            for (int call = 0; call < WARM; call++) {
                assertEquals(10000L, hot.execute().asLong(), "the redefined method is called");
            }
        }
    }

    /**
     * A: a compact send in a Closure that inherits its prelude from an
     * untransported caller. I115-H7-R3 (RECORDED DIVERGENCE): the H7
     * GetMethod selection reads no caller prelude, so inner no longer
     * registers NEEDS_CALLER_PRELUDE (formerly asserted); the result is
     * unchanged and every root's needs stay converged. outer obtains inner with a zero-argument
     * source send, which (unlike an admitted Send1) leaves outer unpublished.
     */
    @Test
    void inheritedPreludeSendRegistersAndConverges() throws Exception {
        try (ProtosStandaloneHostedSession session = open("caller-prelude", """
                makeInner: () => { (target) => { target.identity(7) } }
                inner: makeInner()

                holder: {
                    identity: (value) => { value }
                    nextCall: () => { inner }
                }

                makeOuter: () => { (target) => {
                    next: target.nextCall()
                    next(target)
                } }
                outer: makeOuter()

                run: () => { outer(holder) }
                """)) {
            ProtosBytecodeRootNode innerRoot = root(session, "inner");
            ProtosBytecodeRootNode outerRoot = root(session, "outer");
            ProtosBytecodeRootNode runRoot = root(session, "run");
            List<String> innerNames = unquickened(names(innerRoot));
            assertTrue(innerNames.contains("c.GetMethod")
                            && innerNames.contains("c.CallBinaryMethod"),
                    "inner uses the H7 send: " + innerNames);
            assertReplacedAbsent("inner", innerNames);
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;

            Value run = session.prepareTopLevel("run").executable();
            for (int call = 0; call < WARM; call++) {
                assertEquals(7L, run.execute().asLong());
            }
            assertFalse(innerRoot.hasCallerNeed(need),
                    "inner's H7 selection reads no inherited prelude");
            assertFalse(runRoot.hasCallerNeed(need), "run owns its prelude");

            List<ProtosBytecodeRootNode> roots = List.of(innerRoot, outerRoot, runRoot);
            List<Assumption> settled = new ArrayList<>();
            for (ProtosBytecodeRootNode root : roots) {
                settled.add(root.callerNeedsStability());
            }
            for (int call = 0; call < WARM; call++) {
                assertEquals(7L, run.execute().asLong());
            }
            for (int index = 0; index < roots.size(); index++) {
                assertSame(settled.get(index), roots.get(index).callerNeedsStability(),
                        "root " + index + ": no repeated invalidation");
                assertTrue(settled.get(index).isValid());
            }
        }
    }

    /**
     * R2: the experimental B5 method-call header uses an unobservable
     * ReturnHome. A selected Closure with a materialized captured home
     * must therefore bypass the direct and indirect H7 tiers.
     */
    @Test
    void observableReturnHomeIsNotAdmittedToH7DirectHeader() throws Exception {
        try (ProtosStandaloneHostedSession session =
                open("return-home-admission", METHOD_CALL)) {
            ProtosClosureValue anchor =
                    session.prepareTopLevel("run").closureForTesting();

            session.processContextForTesting().callForRuntime(() -> {
                ProtosLexicalEnvironment module =
                        anchor.capturedLexicalEnvironmentForRuntime();
                ProtosObjectValue receiver =
                        assertInstanceOf(ProtosObjectValue.class,
                                module.readLocalSlotForRuntime("receiver").orElseThrow());
                ProtosClosureValue identity =
                        assertInstanceOf(ProtosClosureValue.class,
                                receiver.readLocalSlot("identity").orElseThrow());

                ProtosLanguageContext context = ProtosLanguageContext.current();

                assertTrue(identity.hasUnobservableInvocationReturnHomeForRuntime(),
                        "identity retains the unobservable-home fast path");
                assertTrue(
                        ProtosExperimentalCallBinaryMethodNode.admittedTargetOrNull(
                                identity, context) != null,
                        "identity must still admit a direct target");

                ProtosReturnHome capturedHome = new ProtosReturnHome();
                ProtosClosureValue captured =
                        new ProtosClosureValue(
                                identity.definition(),
                                identity.capturedLexicalContexts(),
                                identity.capturedReceiver(),
                                identity.methodHome().orElse(null),
                                capturedHome,
                                identity.prelude().orElse(null),
                                identity.executionPlan().orElseThrow());

                assertFalse(
                        captured.hasUnobservableInvocationReturnHomeForRuntime(),
                        "a real captured ReturnHome is observable");
                assertNull(
                        ProtosExperimentalCallBinaryMethodNode.admittedTargetOrNull(
                                captured, context),
                        "the unobservable B5 header must not receive a real home");
                assertTrue(capturedHome.isActive(),
                        "rejected admission must not complete the captured home");
                return null;
            });
        }
    }

    // ---- I115-H7-R3 physical contract ------------------------------------------------

    /** GetMethod answers the selected value itself; no descriptor type remains. */
    @Test
    void getMethodReturnsTheSelectedValue() throws Exception {
        assertTrue(java.util.Arrays.stream(
                        ProtosExperimentalGetMethodNode.class.getDeclaredClasses())
                        .noneMatch(nested -> nested.getSimpleName().equals("Method")),
                "the six-field Method descriptor is removed");
        try (ProtosStandaloneHostedSession session = open("get-method-value", METHOD_CALL)) {
            ProtosClosureValue anchor = session.prepareTopLevel("run").closureForTesting();
            session.processContextForTesting().callForRuntime(() -> {
                ProtosObjectValue receiver = assertInstanceOf(ProtosObjectValue.class,
                        anchor.capturedLexicalEnvironmentForRuntime()
                                .readLocalSlotForRuntime("receiver").orElseThrow());
                Object identity = receiver.readLocalSlot("identity").orElseThrow();
                assertSame(identity,
                        ProtosExperimentalGetMethodNode.selectGeneric(receiver, "identity", null));
                assertSame(identity, ProtosExperimentalGetMethodNode.selectOrNull(
                                receiver,
                                com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
                                        "identity",
                                        com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16))
                        .currentValue(),
                        "the cached selection is installed without a prelude");
                assertNull(ProtosExperimentalGetMethodNode.selectGeneric(receiver, "absent", null));
                return null;
            });
        }
    }

    /**
     * CopyArguments is one specialization storing supplied argument 0; the
     * authoritative BindUnaryFrameParameter fallback is gone.
     */
    @Test
    void copyArgumentsStoresTheSingleArgumentWithoutFallback() throws Exception {
        List<String> specializations = new ArrayList<>();
        for (java.lang.reflect.Method method :
                ProtosBytecodeRootNode.CopyArguments.class.getDeclaredMethods()) {
            assertNull(method.getAnnotation(com.oracle.truffle.api.dsl.Fallback.class),
                    "no fallback: " + method.getName());
            if (method.getAnnotation(com.oracle.truffle.api.dsl.Specialization.class) != null) {
                specializations.add(method.getName());
            }
        }
        assertEquals(List.of("copy"), specializations);
        try (ProtosStandaloneHostedSession session = open("copy-arguments", METHOD_CALL)) {
            ProtosBytecodeRootNode identity = methodRoot(session, "run", "receiver", "identity");
            assertEquals(IDENTITY_DSL, unquickened(names(identity)));
            for (int call = 0; call < WARM; call++) {
                assertEquals(499500L,
                        session.prepareTopLevel("run").executable().execute().asLong());
            }
        }
    }

    /**
     * identity is entered through the direct tier, and the caller transports
     * nothing because identity requested nothing.
     */
    @Test
    void identityIsCalledDirectlyWithoutCallerTransport() throws Exception {
        try (ProtosStandaloneHostedSession session = open("direct-identity", METHOD_CALL)) {
            ProtosBytecodeRootNode run = root(session, "run");
            ProtosBytecodeRootNode identity = methodRoot(session, "run", "receiver", "identity");
            Value executable = session.prepareTopLevel("run").executable();
            for (int call = 0; call < WARM; call++) {
                assertEquals(499500L, executable.execute().asLong());
            }
            assertFalse(identity.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
            assertFalse(identity.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION));
            assertFalse(run.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION),
                    "run never published its activation for the send");
            List<String> executed = names(run);
            // The forced quickening of doDirect (not doIndirect) is executed.
            assertTrue(executed.stream()
                            .filter(name -> name.startsWith("c.CallBinaryMethod$"))
                            .map(name -> name.toLowerCase(java.util.Locale.ROOT))
                            .anyMatch(name -> name.contains("direct")
                                    && !name.contains("indirect")),
                    "the site is quickened to the direct tier: " + executed);
        }
    }

    /** The cached ReadGlobal location observes a rebinding without another lookup. */
    @Test
    void cachedGlobalLocationObservesRebinding() throws Exception {
        try (ProtosStandaloneHostedSession session = open("global-location", """
                first: { f: (x) => { x } }
                second: { f: (x) => { x + 100 } }
                target: first

                hot: () => {
                    n: 0
                    total: 0
                    (() => n < 100).whileTrue() {
                        total = total + target.f(n)
                        n = n + 1
                    }
                    total
                }

                retarget: () => {
                    target = second
                    0
                }
                """)) {
            ProtosClosureValue anchor = session.prepareTopLevel("hot").closureForTesting();
            ProtosFrameLexicalBindingAuthority.ExperimentalGlobalReadLocation location =
                    session.processContextForTesting().callForRuntime(() -> {
                        ProtosLexicalEnvironment module =
                                anchor.capturedLexicalEnvironmentForRuntime();
                        ProtosFrameLexicalBindingAuthority authority = assertInstanceOf(
                                ProtosFrameLexicalBindingAuthority.class,
                                module.lexicalBindingAuthorityForRuntime());
                        int ordinal = authority.storedLayout().offsetOf("target");
                        assertNull(authority.experimentalGlobalReadLocationOrNull("first", ordinal),
                                "a mismatched name is not installed");
                        return authority.experimentalGlobalReadLocationOrNull("target", ordinal);
                    });
            assertTrue(location != null, "target has a physical location");
            Object before = session.processContextForTesting().callForRuntime(location::read);

            Value hot = session.prepareTopLevel("hot").executable();
            for (int call = 0; call < WARM; call++) {
                assertEquals(4950L, hot.execute().asLong());
            }
            session.prepareTopLevel("retarget").executable().execute();
            for (int call = 0; call < WARM; call++) {
                assertEquals(14950L, hot.execute().asLong(), "the rebound global is read");
            }
            if (session.processContextForTesting().callForRuntime(location::isCurrent)) {
                Object after = session.processContextForTesting().callForRuntime(location::read);
                assertNotSame(before, after, "the location reads the frame, not a copy");
            }
        }
    }

    /** A Closure outside the H7 admission and a missing selector use the generic tier. */
    @Test
    void genericFallbackRemainsReachable() throws Exception {
        try (ProtosStandaloneHostedSession session = open("generic-fallback", """
                plain: { f: 5 }
                missing: () => { plain.absent(1) }
                """)) {
            Value missing = session.prepareTopLevel("missing").executable();
            for (int attempt = 0; attempt < 3; attempt++) {
                PolyglotException failure = assertThrows(PolyglotException.class, missing::execute);
                assertTrue(failure.isGuestException(), "a guest Error: " + failure);
                assertFalse(failure.isInternalError(), failure.toString());
            }
        }
        try (ProtosStandaloneHostedSession session =
                open("generic-unadmitted", METHOD_CALL)) {
            ProtosClosureValue anchor = session.prepareTopLevel("run").closureForTesting();
            session.processContextForTesting().callForRuntime(() -> {
                assertNull(ProtosExperimentalCallBinaryMethodNode.admittedTargetOrNull(
                                anchor, ProtosLanguageContext.current()),
                        "run is not a CopyArguments root: the ordinary send handles it");
                return null;
            });
        }
    }

    // ---- Helpers ----------------------------------------------------------------------

    /*
     * The physical roots of the examined compilation: run, identity (the
     * root of the selected Closure's own execution plan) and exactly one
     * further semantic activation root, the module. No root is hidden.
     */
    private static String census(ProtosBytecodeRootNode run, ProtosBytecodeRootNode identity) {
        run.getRootNodes().ensureSourceInformation();
        List<ProtosBytecodeRootNode> roots = run.getRootNodes().getNodes().stream()
                .map(ProtosBytecodeRootNode.class::cast)
                .toList();
        StringBuilder report = new StringBuilder();
        int runCount = 0;
        int identityCount = 0;
        List<ProtosBytecodeRootNode> others = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            ProtosBytecodeRootNode current = roots.get(index);
            assertSame(run.getRootNodes(), current.getRootNodes());
            String role;
            if (current == run) {
                role = "run";
                runCount++;
            } else if (current == identity) {
                role = "identity";
                identityCount++;
            } else {
                role = "module";
                others.add(current);
            }
            List<Instruction> instructions =
                    current.getBytecodeNode().getInstructionsAsList();
            report.append("ROOT_INDEX=").append(index)
                    .append(" ROLE=").append(role)
                    .append(" ROOT=").append(current)
                    .append(" SOURCE=").append(current.getSourceSection())
                    .append(" DSL_FULL=").append(instructions.size())
                    .append('\n');
            for (int n = 0; n < instructions.size(); n++) {
                Instruction instruction = instructions.get(n);
                report.append("INSTRUCTION[").append(index).append("][").append(n)
                        .append("]=").append(ProtosInstructionNames.of(instruction))
                        .append(" | ").append(instruction)
                        .append('\n');
            }
            System.out.println("I115_H7_ROOT[" + index + "]_ROLE=" + role
                    + " DSL_FULL=" + instructions.size());
        }
        assertEquals(1, runCount, "run root: " + report);
        assertEquals(1, identityCount, "identity root: " + report);
        assertEquals(1, others.size(),
                "exactly one module root; no callback roots for the loop: " + report);
        assertTrue(others.get(0).isSemanticActivationRoot(), "the module is a semantic root");
        assertEquals(3, roots.size(), "module, run and identity roots: " + report);
        assertEquals(RUN_DSL, unquickened(names(run)));
        assertEquals(IDENTITY_DSL, unquickened(names(identity)));
        return report.toString();
    }

    /*
     * The root of the Closure selected by {@code holder.selector}, read from
     * the module captured by the top-level Closure {@code topLevel}.
     */
    private static ProtosBytecodeRootNode methodRoot(
            ProtosStandaloneHostedSession session,
            String topLevel,
            String holder,
            String selector) throws Exception {
        ProtosClosureValue anchor =
                session.prepareTopLevel(topLevel).closureForTesting();
        return session.processContextForTesting().callForRuntime(() -> {
            ProtosLexicalEnvironment module = anchor.capturedLexicalEnvironmentForRuntime();
            ProtosObjectValue object = assertInstanceOf(ProtosObjectValue.class,
                    module.readLocalSlotForRuntime(holder).orElseThrow());
            ProtosClosureValue method = assertInstanceOf(ProtosClosureValue.class,
                    object.readLocalSlot(selector).orElseThrow());
            return method.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
        });
    }

    /* The While: one conditional exit and one backward edge, no callback call. */
    private static void assertDirectLoop(ProtosBytecodeRootNode root) {
        List<Instruction> instructions = root.getBytecodeNode().getInstructionsAsList();
        boolean exit = false;
        boolean backward = false;
        for (Instruction instruction : instructions) {
            exit |= instruction.getName().startsWith("branch.false");
            for (Instruction.Argument argument : instruction.getArguments()) {
                if (argument.getKind() == Instruction.Argument.Kind.BYTECODE_INDEX
                        && argument.asBytecodeIndex() < instruction.getBytecodeIndex()) {
                    backward = true;
                }
            }
        }
        assertTrue(exit, "the While tests its condition");
        assertTrue(backward, "the While has a backward edge");
    }

    private static void assertReplacedAbsent(String root, List<String> names) {
        for (String replaced : REPLACED) {
            assertTrue(names.stream().noneMatch(name -> name.contains(replaced)),
                    root + " emits " + replaced + ": " + names);
        }
    }

    private static void assertNumber(
            ProtosStandaloneHostedSession session, String name, long expected)
            throws Exception {
        Value result = session.prepareTopLevel(name).executable().execute();
        assertTrue(result.isNumber(), name + " is a guest number: " + result);
        assertTrue(result.fitsInLong(), name + ": " + result);
        assertEquals(expected, result.asLong(), name);
    }

    private static int count(List<String> names, String name) {
        return (int) names.stream().filter(name::equals).count();
    }

    private static ProtosBytecodeRootNode root(
            ProtosStandaloneHostedSession session, String name) throws Exception {
        return assertInstanceOf(
                ProtosBytecodeRootNode.class,
                session.prepareTopLevel(name).hostExecutableForTesting()
                        .target.getRootNode());
    }

    private static List<String> names(ProtosBytecodeRootNode root) {
        return root.getBytecodeNode().getInstructionsAsList().stream()
                .map(ProtosInstructionNames::of)
                .toList();
    }

    /* Instruction names without their quickening suffix. */
    private static List<String> unquickened(List<String> names) {
        return names.stream()
                .map(name -> {
                    int quickened = name.indexOf('$');
                    return quickened < 0 ? name : name.substring(0, quickened);
                })
                .toList();
    }

    private static ProtosStandaloneHostedSession open(String name, String source)
            throws Exception {
        Files.createDirectories(OUTPUT);
        Path sourceFile = OUTPUT.resolve(name + ".protos");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, sourceFile);
        if (session.initialOutcome().state() != ProtosExecutionOutcome.State.COMPLETED) {
            session.close();
            throw new AssertionError("Module initialization failed: " + name);
        }
        return session;
    }
}
