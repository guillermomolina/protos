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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosLargeIntegerValue;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
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

    // ---- I115-H7-R5 return representation ---------------------------------------------

    /**
     * An admitted H7 root and its host entry must BOTH return the raw
     * signed-64 Long. Checking Value.asLong alone would miss an accidental
     * ProtosIntegerValue allocation at either boundary.
     */
    @Test
    void h7LongRemainsRawAcrossRootAndHostBoundary() throws Exception {
        try (ProtosStandaloneHostedSession session =
                open("r5-raw-integer", METHOD_CALL)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosHostExecutableClosure host = prepared.hostExecutableForTesting();
            ProtosBytecodeRootNode run = assertInstanceOf(
                    ProtosBytecodeRootNode.class, host.target.getRootNode());

            assertTrue(run.isExperimentalH7RawIntegerReturn(),
                    "the compact H7 root must admit raw Long transport");

            for (int call = 0; call < WARM; call++) {
                assertEquals(499500L, prepared.executable().execute().asLong());
            }

            session.processContextForTesting().callForRuntime(() -> {
                Object rootResult = host.target.call(
                        ProtosFrameArguments.compactDirectClosureCallZeroNoHome(
                                host.closure, host.caller));

                assertEquals(499500L,
                        assertInstanceOf(Long.class, rootResult).longValue(),
                        "EpilogForReturn must not allocate ProtosIntegerValue");

                Object hostResult;
                try {
                    hostResult = InteropLibrary.getUncached().execute(host);
                } catch (InteropException unexpected) {
                    throw new AssertionError(unexpected);
                }

                assertEquals(499500L,
                        assertInstanceOf(Long.class, hostResult).longValue(),
                        "the Polyglot host boundary must preserve the raw Long");
                return null;
            });
        }
    }

    /**
     * The R5 admission is narrow: ordinary guest-number returns still use
     * the guest representation, and a result exceeding 32 bits is the same
     * raw signed-64 Long, never truncated.
     */
    @Test
    void r5RawIntegerAdmissionDoesNotChangeOtherNumericResults() throws Exception {
        try (ProtosStandaloneHostedSession session =
                open("r5-representation-boundaries", """
                        plain: () => { 7 }

                        wide: () => {
                            a: 2147483647
                            a + 1
                        }
                        """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel plain =
                    session.prepareTopLevel("plain");
            ProtosStandaloneHostedSession.PreparedTopLevel wide =
                    session.prepareTopLevel("wide");

            assertFalse(root(session, "plain").isExperimentalH7RawIntegerReturn(),
                    "an ordinary literal root is not an H7 return root");
            assertTrue(root(session, "wide").isExperimentalH7RawIntegerReturn(),
                    "the H7 addition is admitted even when the result exceeds 32 bits");

            session.processContextForTesting().callForRuntime(() -> {
                InteropLibrary interop = InteropLibrary.getUncached();
                try {
                    Object plainResult =
                            interop.execute(plain.hostExecutableForTesting());
                    Object wideResult =
                            interop.execute(wide.hostExecutableForTesting());

                    assertEquals(7L,
                            assertInstanceOf(
                                    ProtosIntegerValue.class, plainResult).longValue(),
                            "ordinary results retain their guest representation");
                    assertEquals(2147483648L,
                            assertInstanceOf(Long.class, wideResult).longValue(),
                            "a result beyond 32 bits remains the raw signed-64 Long");
                } catch (InteropException unexpected) {
                    throw new AssertionError(unexpected);
                }
                return null;
            });

            assertEquals(7L, plain.executable().execute().asLong());
            assertEquals(2147483648L, wide.executable().execute().asLong());
        }
    }

    // ---- I115 long/double numeric representation -------------------------------------

    /** The compact long, double and mixed operations of the regressions below. */
    private static final String LONG_DOUBLE = """
            sumLong: () => {
                a: 40
                b: 2
                a + b
            }

            sumBeyond32: () => {
                a: 2147483647
                b: 2147483647
                a + b
            }

            storedSum: () => {
                a: 40
                b: 1
                c: a + b
                c + 1
            }

            inPlace: () => {
                t: 2147483647
                t = t + 5
                t
            }

            sumDouble: () => {
                a: 0.1
                b: 0.2
                a + b
            }

            sumLongDouble: () => {
                a: 16777216
                b: 1.0
                a + b
            }

            sumDoubleLong: () => {
                a: 1.0
                b: 16777216
                a + b
            }

            lessLongDouble: () => {
                k: 0
                (() => k < 2.5).whileTrue() {
                    k = k + 1
                }
                k
            }
            """;

    /*
     * 1, 4, 7, 8, 10: long results of NumberAdd and NumberInPlaceAdd stay raw
     * Longs through locals, EpilogForReturn and the host boundary, both
     * before and after the operations specialize.
     */
    @Test
    void longResultsStayRawLongs() throws Exception {
        try (ProtosStandaloneHostedSession session =
                open("long-results", LONG_DOUBLE)) {
            List<String> stored = unquickened(names(root(session, "storedSum")));
            assertEquals(2, count(stored, "c.NumberAdd"),
                    "NumberAdd -> StoreLocal -> LoadLocal -> NumberAdd: " + stored);
            assertEquals(3, count(stored, "store.local"),
                    "a, b and the intermediate sum are stored locals: " + stored);
            assertReplacedAbsent("storedSum", stored);
            List<String> inPlace = unquickened(names(root(session, "inPlace")));
            assertEquals(1, count(inPlace, "c.NumberInPlaceAdd"), inPlace.toString());

            for (int call = 0; call < WARM; call++) {
                assertRawLong(session, "sumLong", 42L);
                assertRawLong(session, "sumBeyond32", 4294967294L);
                assertRawLong(session, "storedSum", 42L);
                assertRawLong(session, "inPlace", 2147483652L);
            }
            assertNumber(session, "sumBeyond32", 4294967294L);
            assertNumber(session, "inPlace", 2147483652L);
        }
    }

    /*
     * 6: double and mixed long/double operations keep double precision.
     * Float literals are not admitted to the compact lane, so the double
     * fast paths of NumberAdd/NumberInPlaceAdd and Lt are checked directly;
     * the source cases check the observable values.
     */
    @Test
    void doubleOperationsKeepDoublePrecision() throws Exception {
        // 16777217 has no Java float representation.
        assertEquals(16777217.0, ProtosExperimentalNumberAddFastPathsBase.doLD(16777216L, 1.0));
        assertEquals(16777217.0, ProtosExperimentalNumberAddFastPathsBase.doDL(1.0, 16777216L));
        assertEquals(0.1 + 0.2, ProtosExperimentalNumberAddFastPathsBase.doDD(0.1, 0.2));
        assertTrue(ProtosExperimentalLessNode.doLD(2L, 2.5));
        assertFalse(ProtosExperimentalLessNode.doDL(2.5, 2L));
        assertTrue(ProtosExperimentalLessNode.doDD(16777216.0, 16777217.0));
        try (ProtosStandaloneHostedSession session =
                open("double-results", LONG_DOUBLE)) {
            for (int call = 0; call < WARM; call++) {
                assertDouble(session, "sumDouble", 0.1 + 0.2);
                // 16777217 has no Java float representation.
                assertDouble(session, "sumLongDouble", 16777217.0);
                assertDouble(session, "sumDoubleLong", 16777217.0);
                assertNumber(session, "lessLongDouble", 3L);
            }
        }
    }

    /*
     * 2, 3: the compact subtraction and multiplication return a Java long
     * on their primitive paths, also after the overflow specialization
     * replaced them; an int never appears as a carrier.
     */
    @Test
    void compactIntegerOperationsReturnLongs() {
        assertEquals(42L, ProtosBytecodeRootNode.ExperimentalIntegerAdd.doLong(40L, 2L));
        assertEquals(38L, ProtosBytecodeRootNode.ExperimentalIntegerSubtract.doLong(40L, 2L));
        assertEquals(80L, ProtosBytecodeRootNode.ExperimentalIntegerMultiply.doLong(40L, 2L));
        assertEquals(4294967294L, assertInstanceOf(Long.class,
                ProtosBytecodeRootNode.ExperimentalIntegerAdd.doLongWithOverflow(
                        2147483647L, 2147483647L, null, null)));
        assertEquals(-4294967296L, assertInstanceOf(Long.class,
                ProtosBytecodeRootNode.ExperimentalIntegerSubtract.doLongWithOverflow(
                        -2147483648L, 2147483648L, null, null)));
        assertEquals(4611686014132420609L, assertInstanceOf(Long.class,
                ProtosBytecodeRootNode.ExperimentalIntegerMultiply.doLongWithOverflow(
                        2147483647L, 2147483647L, null, null)));
        assertEquals(Long.MIN_VALUE, assertInstanceOf(Long.class,
                ProtosBytecodeRootNode.ExperimentalIntegerMultiply.doLongWithOverflow(
                        Long.MIN_VALUE, 1L, null, null)));
        assertThrows(ArithmeticException.class,
                () -> ProtosBytecodeRootNode.ExperimentalIntegerMultiply.doLong(
                        Long.MIN_VALUE, -1L));
    }

    /* 5: a real signed-64 overflow of +, - and * is the exact large Integer. */
    @Test
    void signed64OverflowPromotesExactly() throws Exception {
        try (ProtosStandaloneHostedSession session = open("signed-64-overflow", """
                sumDown: (n) => {
                    result: 9223372036854775807

                    (n > 0).ifTrue() {
                        result = sumDown(n - 1) + n
                    }

                    result
                }

                differenceDown: (n) => {
                    result: 9223372036854775807

                    (n > 0).ifTrue() {
                        result = n - differenceDown(n - 1)
                    }

                    result
                }

                factorial: (n) => {
                    result: 1

                    (n > 1).ifTrue() {
                        result = n * factorial(n - 1)
                    }

                    result
                }

                inPlaceOverflow: () => {
                    j: 9223372036854775807
                    j = j + 1
                    j
                }

                runSum: () => { sumDown(2) }
                runDifference: () => { differenceDown(2) }
                runProduct: () => { factorial(21) }
                runFits: () => { factorial(20) }
                """)) {
            for (int call = 0; call < WARM; call++) {
                assertLarge(session, "runSum", "9223372036854775810");
                assertLarge(session, "runDifference", "9223372036854775808");
                assertLarge(session, "runProduct", "51090942171709440000");
                assertLarge(session, "inPlaceOverflow", "9223372036854775808");
                assertNumber(session, "runFits", 2432902008176640000L);
            }
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
                        ProtosClosureCallPreparation.functionCallTarget(identity, context) != null,
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
                // I115: the dispatcher's B5 header carries the captured home by
                // identity, never the unobservable marker.
                assertSame(
                        capturedHome,
                        ProtosDispatchTestSupport.direct(captured, null).targetArguments()[4],
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
                // I115: GetMethod answers a bound method on the receiver.
                ProtosBoundMethod bound = assertInstanceOf(ProtosBoundMethod.class,
                        ProtosExperimentalGetMethodNode.select(receiver, "identity", null,
                                anchor.prelude().orElseThrow().newModuleActivation()));
                assertSame(identity, bound.callable());
                assertSame(identity, ProtosExperimentalGetMethodNode.selectOrNull(
                                receiver,
                                com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
                                        "identity",
                                        com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16))
                        .currentValue(),
                        "the cached selection is installed without a prelude");
                assertSame(receiver, bound.receiver());
                assertSame(receiver, bound.methodHome());
                return null;
            });
        }
    }

    @Test
    void h11UnaryEntryOmitsClosureWithoutReadGlobal() throws Exception {
        try (ProtosStandaloneHostedSession session = open(
                "h11-entry-selection",
                """
                shared: 7

                holder: {
                    identity: (x) => { x }
                    captured: (x) => { shared }
                }

                runIdentity: () => { holder.identity(1) }
                runCaptured: () => { holder.captured(0) }
                """)) {
            ProtosBytecodeRootNode identity =
                    methodRoot(session, "runIdentity", "holder", "identity");
            ProtosBytecodeRootNode captured =
                    methodRoot(session, "runCaptured", "holder", "captured");

            assertTrue(identity.isExperimentalPrimitiveArgumentRoot());
            assertTrue(captured.isExperimentalPrimitiveArgumentRoot());

            assertFalse(identity.experimentalUnaryNeedsCapturedOwner());
            assertTrue(captured.experimentalUnaryNeedsCapturedOwner());

            assertNumber(session, "runIdentity", 1L);
            assertNumber(session, "runCaptured", 7L);
        }
    }

    @Test
    void h11CapturedUnaryAbiRetainsTheGlobalReadOwner() throws Exception {
        try (ProtosStandaloneHostedSession session = open(
                "h11-captured-unary",
                "global: 7\nrun: (value) => { global }\n")) {
            ProtosClosureValue closure =
                    session.prepareTopLevel("run").closureForTesting();
            Object supplied = Long.valueOf(42L);
            Object[] arguments =
                    ProtosFrameArguments.experimentalCapturedUnaryCall(
                            closure, supplied);

            assertEquals(2, arguments.length);
            assertSame(closure, arguments[0]);
            assertSame(supplied,
                    ProtosFrameArguments.experimentalSuppliedArgument0(arguments));
            assertSame(closure,
                    ProtosExperimentalReadGlobalNode.compactClosureOrNull(arguments));

            session.processContextForTesting().callForRuntime(() -> {
                assertSame(
                        closure.capturedLexicalEnvironmentForRuntime(),
                        ProtosExperimentalReadGlobalNode.capturedModuleOrNull(
                                arguments, 1));
                return null;
            });
        }
    }

    private static final String H7_OVERFLOW = """
            limit: 9223372036854775807

            holder: {
                grow: (n) => { n + 9223372036854775807 }
                growCaptured: (n) => { n + limit }
                same: (n) => { n }
                plusOne: (n) => { n + 1 }
                escape: (n) => { ^n }
            }

            runGrow: () => { holder.grow(1) }
            runGrowCaptured: () => { holder.growCaptured(1) }
            runKeep: () => { holder.same(41) + 1 }
            runPlusOne: () => { holder.plusOne(41) }
            runEscape: () => { holder.escape(5) }
            """;

    /**
     * I115: a signed-64 overflow in a CallBinaryMethod callee, without
     * ReadGlobal (grow) and with it (growCaptured). ABI selection: with no
     * need, grow's entry would be exactly {@code {supplied0}} even though
     * its Closure has its own Prelude; growCaptured's {@code {closure,
     * supplied0}}. Both arithmetic callees are admitted to the direct H7
     * tier (no global non-suspension proof is required): the overflow is
     * exact, grow's minimal entry registers NEEDS_CALLER_PRELUDE once and
     * then transports the Prelude, and the need stays stable.
     *
     * <p>RECORDED I115 DIVERGENCE: the minimal entry omits the Closure, so
     * the first recovery may use the caller's effective Prelude instead of
     * the Closure's own; its prototype identity is reported, not required.
     */
    @Test
    void h7MethodOverflowUsesTheInvocationPrelude() throws Exception {
        try (ProtosStandaloneHostedSession session = open("h7-method-overflow", H7_OVERFLOW)) {
            for (String[] fixture : List.of(
                    new String[] {"runGrow", "grow"},
                    new String[] {"runGrowCaptured", "growCaptured"})) {
                String run = fixture[0];
                String selector = fixture[1];
                boolean captured = "growCaptured".equals(selector);
                ProtosClosureValue closure = methodClosure(session, run, "holder", selector);
                // Before the first call the Context target does not exist yet; the
                // template root has the same lowering and ABI facts.
                ProtosBytecodeRootNode template = methodRoot(session, run, "holder", selector);
                ProtosBytecodeRootNode caller = root(session, run);
                Object ownPrelude = session.processContextForTesting().callForRuntime(
                        closure::preludeOrNullForRuntime);

                List<String> calleeNames = unquickened(names(template));
                List<String> runNames = unquickened(names(caller));
                assertTrue(calleeNames.contains("c.CopyArguments"), calleeNames.toString());
                assertEquals(1, count(calleeNames, "c.NumberAdd"), calleeNames.toString());
                assertEquals(1, count(runNames, "c.GetMethod"), runNames.toString());
                assertEquals(1, count(runNames, "c.CallBinaryMethod"), runNames.toString());
                for (List<String> names : List.of(calleeNames, runNames)) {
                    assertTrue(names.stream().noneMatch(
                                    name -> name.contains("MaterializeCurrentClosure")),
                            names.toString());
                    assertReplacedAbsent(selector, names);
                }
                assertEquals(captured, template.experimentalUnaryNeedsCapturedOwner(), selector);
                assertFalse(template.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));

                // I115: the reduced H11 entry is retired; the callee is entered
                // with the dispatcher's B5 method header.
                // First overflow: exact, through the admitted direct H7 entry.
                Object first = rawResult(session, run);
                ProtosLargeIntegerValue large =
                        assertInstanceOf(ProtosLargeIntegerValue.class, first);
                assertLarge(session, run, "9223372036854775808");
                ProtosBytecodeRootNode callee = admittedCallee(session, closure);
                assertEquals(captured, callee.experimentalUnaryNeedsCapturedOwner(), selector);
                assertEquals(calleeNames, unquickened(names(callee)), "same lowering");
                if (captured && ownPrelude != null) {
                    // {closure, supplied0} retains the Closure: its own Prelude, no need.
                    assertSame(((ProtosPrelude) ownPrelude).bigIntegerPrototype(),
                            large.parent().orElseThrow());
                    assertFalse(callee.hasCallerNeed(
                            ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
                } else {
                    /*
                     * {supplied0} carried no Prelude: the admitted callee registered
                     * its need (an ordinary B5 send would have found the Closure's
                     * own Prelude and registered nothing).
                     */
                    assertTrue(callee.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE),
                            selector + " registered its caller Prelude need");
                    if (ownPrelude != null
                            && ((ProtosPrelude) ownPrelude).bigIntegerPrototype()
                                    != large.parent().orElseThrow()) {
                        System.out.println("I115_H7_RECORDED_DIVERGENCE[" + selector
                                + "]=first recovery used the caller Prelude");
                    }
                }

                Assumption stable = callee.callerNeedsStability();
                for (int call = 0; call < WARM; call++) {
                    assertLarge(session, run, "9223372036854775808");
                }
                assertSame(stable, callee.callerNeedsStability(), "no repeated registration");
                assertTrue(stable.isValid());
                assertSame(callee, admittedCallee(session, closure), "the same direct target");
                assertEquals(calleeNames, unquickened(names(callee)));
                assertEquals(calleeNames, unquickened(names(template)));
                assertEquals(runNames, unquickened(names(caller)));
            }
        }
    }

    /**
     * The direct H7 tier without needs: an admitted method (no arithmetic)
     * is entered through {@code {supplied0}} and never requests the caller
     * Prelude; a non-overflowing NumberAdd in the H7 caller returns a raw
     * Long and requests nothing either.
     */
    @Test
    void h7MethodWithoutOverflowRequestsNoPrelude() throws Exception {
        try (ProtosStandaloneHostedSession session = open("h7-method-keep", H7_OVERFLOW)) {
            ProtosClosureValue closure = methodClosure(session, "runKeep", "holder", "same");
            assertEquals(42L, assertInstanceOf(Long.class, rawResult(session, "runKeep")));
            ProtosBytecodeRootNode callee = admittedCallee(session, closure);
            assertFalse(callee.experimentalUnaryNeedsCapturedOwner());
            ProtosBytecodeRootNode caller = root(session, "runKeep");

            Assumption calleeStable = callee.callerNeedsStability();
            Assumption callerStable = caller.callerNeedsStability();
            for (int call = 0; call < WARM; call++) {
                assertEquals(42L, assertInstanceOf(Long.class, rawResult(session, "runKeep")));
            }
            for (ProtosBytecodeRootNode root : List.of(callee, caller)) {
                assertFalse(root.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
            }
            assertSame(calleeStable, callee.callerNeedsStability());
            assertSame(callerStable, caller.callerNeedsStability());
            assertTrue(calleeStable.isValid() && callerStable.isValid());
        }
    }

    /**
     * I115: an arithmetic H7 method (NumberAdd, whose generic route keeps an
     * ordinary send) is admitted to the direct CallBinaryMethod tier and
     * entered with {@code {supplied0}}; its long result comes back raw.
     * The raw Long proves the direct entry: the ordinary send would have
     * projected it to a guest Integer.
     */
    @Test
    void h7ArithmeticMethodUsesTheDirectTier() throws Exception {
        try (ProtosStandaloneHostedSession session = open("h7-method-plus-one", H7_OVERFLOW)) {
            ProtosClosureValue closure =
                    methodClosure(session, "runPlusOne", "holder", "plusOne");
            ProtosBytecodeRootNode template =
                    methodRoot(session, "runPlusOne", "holder", "plusOne");
            ProtosBytecodeRootNode caller = root(session, "runPlusOne");
            List<String> calleeNames = unquickened(names(template));
            List<String> runNames = unquickened(names(caller));
            assertTrue(calleeNames.contains("c.CopyArguments"), calleeNames.toString());
            assertEquals(1, count(calleeNames, "c.NumberAdd"), calleeNames.toString());
            assertEquals(1, count(runNames, "c.GetMethod"), runNames.toString());
            assertEquals(1, count(runNames, "c.CallBinaryMethod"), runNames.toString());
            for (List<String> names : List.of(calleeNames, runNames)) {
                assertTrue(names.stream().noneMatch(
                                name -> name.contains("MaterializeCurrentClosure")),
                        names.toString());
                assertReplacedAbsent("plusOne", names);
            }

            assertEquals(42L, assertInstanceOf(Long.class, rawResult(session, "runPlusOne")));
            ProtosBytecodeRootNode callee = admittedCallee(session, closure);
            assertTrue(callee.isExperimentalPrimitiveArgumentRoot());
            assertFalse(callee.experimentalUnaryNeedsCapturedOwner());
            assertEquals(calleeNames, unquickened(names(callee)), "the expected H7 root");

            Assumption stable = callee.callerNeedsStability();
            for (int call = 0; call < WARM; call++) {
                assertEquals(42L,
                        assertInstanceOf(Long.class, rawResult(session, "runPlusOne")));
            }
            assertFalse(callee.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
            assertSame(stable, callee.callerNeedsStability());
            assertTrue(stable.isValid());
            assertSame(callee, admittedCallee(session, closure));
            assertEquals(calleeNames, unquickened(names(callee)), "after quickening");
            assertEquals(runNames, unquickened(names(caller)), "after quickening");
            // The call site quickened to the doDirect specialization (DirectCallNode PIC).
            List<String> quickened = names(caller);
            assertTrue(quickened.stream().anyMatch(name ->
                            name.startsWith("c.CallBinaryMethod$") && name.contains("Direct")),
                    "CallBinaryMethod uses its direct specialization: " + quickened);
            // As GraalPy callPFunction: no suspension test follows the direct call.
            assertTrue(java.util.Arrays.stream(
                                    ProtosExperimentalCallBinaryMethodNode.class.getDeclaredMethods())
                            .noneMatch(method -> method.getName().equals("directOutcome")),
                    "no post-call outcome check");
        }
    }

    /**
     * Admission stays limited to H7 roots: a method outside the subset (a
     * non-local return, observable ReturnHome) is not admitted and keeps the
     * ordinary send, with its correct result; plusOne in the same holder is
     * admitted.
     */
    @Test
    void nonH7MethodKeepsTheOrdinarySend() throws Exception {
        try (ProtosStandaloneHostedSession session = open("h7-method-fallback", H7_OVERFLOW)) {
            assertNumber(session, "runEscape", 5L);
            assertNumber(session, "runPlusOne", 42L);
            ProtosClosureValue escape = methodClosure(session, "runEscape", "holder", "escape");
            ProtosClosureValue plusOne = methodClosure(session, "runPlusOne", "holder", "plusOne");
            // I115: the H7 admission tier is retired; every source method is
            // dispatched by the shared function PIC. The results stay checked.
            assertTrue(admittedCallee(session, plusOne).isExperimentalPrimitiveArgumentRoot());
            for (int call = 0; call < WARM; call++) {
                assertNumber(session, "runEscape", 5L);
            }
        }
    }

    /**
     * Every H11 entry recognizes its layout explicitly, also when the guest
     * argument is itself a Closure, and keeps that argument by identity; the
     * Prelude is never read as the argument.
     */
    @Test
    void h11EntriesAreUnambiguousForClosureArguments() throws Exception {
        try (ProtosStandaloneHostedSession session = open("h11-closure-argument", H7_OVERFLOW)) {
            ProtosClosureValue owner = methodClosure(session, "runGrow", "holder", "growCaptured");
            ProtosClosureValue argument = methodClosure(session, "runGrow", "holder", "grow");
            ProtosPrelude prelude = (ProtosPrelude) session.processContextForTesting()
                    .callForRuntime(session.prepareTopLevel("runGrow").closureForTesting()
                            ::preludeOrNullForRuntime);
            assertTrue(prelude != null);

            Object[] minimal = ProtosFrameArguments.experimentalMinimalUnaryCall(argument);
            Object[] minimalExtended =
                    ProtosFrameArguments.experimentalMinimalUnaryCall(argument, prelude);
            Object[] captured = ProtosFrameArguments.experimentalCapturedUnaryCall(owner, argument);
            Object[] capturedExtended =
                    ProtosFrameArguments.experimentalCapturedUnaryCall(owner, argument, prelude);

            for (Object[] entry : List.of(minimal, minimalExtended, captured, capturedExtended)) {
                assertTrue(ProtosFrameArguments.isExperimentalReducedCall(entry));
                assertSame(argument, ProtosFrameArguments.experimentalSuppliedArgument0(entry));
            }
            assertEquals(1, minimal.length);
            assertTrue(ProtosFrameArguments.isExperimentalMinimalUnaryWithPrelude(minimalExtended));
            assertFalse(ProtosFrameArguments.isExperimentalMinimalUnaryWithPrelude(capturedExtended));

            assertNull(ProtosFrameArguments.experimentalCapturedOwnerOrNull(minimal));
            assertNull(ProtosFrameArguments.experimentalCapturedOwnerOrNull(minimalExtended));
            assertSame(owner, ProtosFrameArguments.experimentalCapturedOwnerOrNull(captured));
            assertSame(owner, ProtosFrameArguments.experimentalCapturedOwnerOrNull(capturedExtended));
            assertNull(ProtosExperimentalReadGlobalNode.compactClosureOrNull(minimalExtended));
            assertSame(owner, ProtosExperimentalReadGlobalNode.compactClosureOrNull(capturedExtended));

            assertNull(ProtosFrameArguments.experimentalTransportedPreludeOrNull(minimal));
            assertSame(prelude,
                    ProtosFrameArguments.experimentalTransportedPreludeOrNull(minimalExtended));
            assertSame(prelude,
                    ProtosFrameArguments.experimentalTransportedPreludeOrNull(capturedExtended));
            assertSame(session.processContextForTesting().callForRuntime(
                            owner::preludeOrNullForRuntime),
                    ProtosFrameArguments.experimentalTransportedPreludeOrNull(captured));
        }
    }

    @Test
    void h11MinimalUnaryAbiSharesTheArgumentWithLegacyEntries() {
        Object value = Long.valueOf(42L);
        Object[] minimal =
                ProtosFrameArguments.experimentalMinimalUnaryCall(value);
        assertEquals(1, minimal.length);
        assertSame(
                value,
                ProtosFrameArguments.experimentalSuppliedArgument0(minimal));

        Object[] previous =
                ProtosFrameArguments.experimentalStaticUnaryCall(value, null);
        assertSame(
                value,
                ProtosFrameArguments.experimentalSuppliedArgument0(previous));
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

    // ---- I115-H7-R4 Object global location --------------------------------------------

    /** A compact global read retains the original Object after quickening. */
    @Test
    void objectGlobalLocationReadsCurrentValueAfterQuickening() throws Exception {
        try (ProtosStandaloneHostedSession session = open("r4-object-location", METHOD_CALL)) {
            ProtosClosureValue anchor =
                    session.prepareTopLevel("run").closureForTesting();
            Value run = session.prepareTopLevel("run").executable();

            for (int call = 0; call < WARM; call++) {
                assertEquals(499500L, run.execute().asLong());
            }

            session.processContextForTesting().callForRuntime(() -> {
                ProtosLexicalEnvironment module =
                        anchor.capturedLexicalEnvironmentForRuntime();
                ProtosFrameLexicalBindingAuthority authority = assertInstanceOf(
                        ProtosFrameLexicalBindingAuthority.class,
                        module.lexicalBindingAuthorityForRuntime());
                int ordinal = authority.storedLayout().offsetOf("receiver");
                Object receiver = module.readLocalSlotForRuntime("receiver").orElseThrow();

                com.oracle.truffle.api.strings.TruffleString name =
                        com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
                                "receiver",
                                com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16);

                ProtosFrameLexicalBindingAuthority.ExperimentalGlobalReadLocation location =
                        ProtosExperimentalReadGlobalNode.locationOrNull(
                                anchor, name, ordinal, 1);

                assertTrue(location != null, "the Object location is admitted");
                assertTrue(location.isCurrent(), "the location belongs to the current root");
                assertEquals(ordinal, location.ordinal());
                assertSame(receiver, location.read(),
                        "the physical location returns the original Protos object");
                return null;
            });

            assertEquals(499500L, run.execute().asLong());
        }
    }

    /** The cached physical location reads a rebound Object, not a cached value. */
    @Test
    void objectGlobalLocationObservesRebinding() throws Exception {
        try (ProtosStandaloneHostedSession session = open("r4-object-rebinding", """
                first: { f: (x) => { x } }
                second: { f: (x) => { x + 100 } }
                target: first

                hot: () => { target.f(7) }

                retarget: () => {
                    target = second
                    0
                }
                """)) {
            ProtosClosureValue anchor =
                    session.prepareTopLevel("hot").closureForTesting();
            Value hot = session.prepareTopLevel("hot").executable();

            for (int call = 0; call < WARM; call++) {
                assertEquals(7L, hot.execute().asLong());
            }

            Object before = session.processContextForTesting().callForRuntime(() -> {
                ProtosLexicalEnvironment module =
                        anchor.capturedLexicalEnvironmentForRuntime();
                ProtosFrameLexicalBindingAuthority authority = assertInstanceOf(
                        ProtosFrameLexicalBindingAuthority.class,
                        module.lexicalBindingAuthorityForRuntime());
                int ordinal = authority.storedLayout().offsetOf("target");

                ProtosFrameLexicalBindingAuthority.ExperimentalGlobalReadLocation location =
                        authority.experimentalGlobalReadLocationOrNull("target", ordinal);

                assertTrue(location != null, "initial Object location is admitted");
                Object value = module.readLocalSlotForRuntime("target").orElseThrow();
                assertSame(value, location.read());
                return value;
            });

            session.prepareTopLevel("retarget").executable().execute();

            for (int call = 0; call < WARM; call++) {
                assertEquals(107L, hot.execute().asLong());
            }

            session.processContextForTesting().callForRuntime(() -> {
                ProtosLexicalEnvironment module =
                        anchor.capturedLexicalEnvironmentForRuntime();
                ProtosFrameLexicalBindingAuthority authority = assertInstanceOf(
                        ProtosFrameLexicalBindingAuthority.class,
                        module.lexicalBindingAuthorityForRuntime());
                int ordinal = authority.storedLayout().offsetOf("target");

                ProtosFrameLexicalBindingAuthority.ExperimentalGlobalReadLocation location =
                        authority.experimentalGlobalReadLocationOrNull("target", ordinal);

                assertTrue(location != null, "rebound Object location remains admitted");
                Object after = module.readLocalSlotForRuntime("target").orElseThrow();

                assertNotSame(before, after, "the global binding was rebound");
                assertSame(after, location.read(),
                        "the location observes the current physical value");
                return null;
            });
        }
    }

    /** The pinned generator mapping addresses the actual retained module frame. */
    @Test
    void globalLocationValidatesGeneratedFrameLayout() throws Exception {
        try (ProtosStandaloneHostedSession session = open("r4-physical-layout", METHOD_CALL)) {
            ProtosClosureValue anchor =
                    session.prepareTopLevel("run").closureForTesting();

            session.processContextForTesting().callForRuntime(() -> {
                ProtosLexicalEnvironment module =
                        anchor.capturedLexicalEnvironmentForRuntime();
                ProtosFrameLexicalBindingAuthority authority = assertInstanceOf(
                        ProtosFrameLexicalBindingAuthority.class,
                        module.lexicalBindingAuthorityForRuntime());
                ProtosFrameLexicalLayout layout = authority.storedLayout();
                int ordinal = layout.offsetOf("receiver");
                int localOffset = layout.localOffsetAt(ordinal);

                com.oracle.truffle.api.frame.MaterializedFrame frame =
                        authority.retainedMaterializedFrameForCapturedAccess();

                // Pinned Truffle 25.4.4.1.1 generated USER_LOCALS_START_INDEX.
                int physicalIndex = 2 + localOffset;

                assertTrue(localOffset >= 0);
                assertTrue(physicalIndex >= 2);
                assertTrue(physicalIndex < frame.getFrameDescriptor().getNumberOfSlots());
                assertTrue(frame.isObject(physicalIndex),
                        "the module global has an Object carrier");

                Object receiver = module.readLocalSlotForRuntime("receiver").orElseThrow();

                assertSame(receiver, frame.getValue(physicalIndex),
                        "the generated physical slot holds the original binding");

                ProtosFrameLexicalBindingAuthority.ExperimentalGlobalReadLocation location =
                        authority.experimentalGlobalReadLocationOrNull("receiver", ordinal);

                assertTrue(location != null, "validated Object location is admitted");
                assertSame(frame.getValue(physicalIndex), location.read());

                assertNull(authority.experimentalGlobalReadLocationOrNull(
                                "wrong-name", ordinal),
                        "the name must match the selected ordinal");
                assertNull(authority.experimentalGlobalReadLocationOrNull(
                                "receiver", -1),
                        "a negative ordinal is never admitted");
                assertNull(authority.experimentalGlobalReadLocationOrNull(
                                "receiver", layout.length()),
                        "an out-of-range ordinal is never admitted");
                return null;
            });
        }
    }

    /** A published B5 invocation never enters the compact Closure PIC. */
    @Test
    void publishedHeaderDoesNotUseCompactGlobalCache() throws Exception {
        try (ProtosStandaloneHostedSession session =
                open("r4-published-header", METHOD_CALL)) {
            ProtosClosureValue anchor =
                    session.prepareTopLevel("run").closureForTesting();

            session.processContextForTesting().callForRuntime(() -> {
                ProtosActivation activation =
                        ProtosActivation.forClosureInvocation(
                                anchor,
                                List.of(),
                                anchor.prelude().orElseThrow());

                Object[] published =
                        ProtosFrameArguments.publishedInvocation(activation);

                assertTrue(ProtosFrameArguments.hasPublishedActivation(published));
                assertNull(
                        ProtosExperimentalReadGlobalNode.compactClosureOrNull(published),
                        "a published activation is not a compact Closure");

                Object[] compact =
                        ProtosFrameArguments.compactDirectClosureCallZeroNoHome(
                                anchor, activation);

                assertSame(
                        anchor,
                        ProtosExperimentalReadGlobalNode.compactClosureOrNull(compact),
                        "the original compact Closure remains admissible");

                ProtosLexicalEnvironment module =
                        anchor.capturedLexicalEnvironmentForRuntime();
                ProtosFrameLexicalBindingAuthority authority = assertInstanceOf(
                        ProtosFrameLexicalBindingAuthority.class,
                        module.lexicalBindingAuthorityForRuntime());

                int ordinal = authority.storedLayout().offsetOf("receiver");

                com.oracle.truffle.api.strings.TruffleString name =
                        com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
                                "receiver",
                                com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16);

                Object expected =
                        module.readLocalSlotForRuntime("receiver").orElseThrow();

                assertSame(
                        expected,
                        ProtosExperimentalReadGlobalNode.resolveInitial(
                                published, ordinal, name, 1),
                        "the published header resolves through the generic path");

                return null;
            });
        }
    }

    /**
     * An actual primitive frame slot is rejected by Object admission.
     * Generic resolution projects the primitive carrier to a guest Integer.
     */
    @Test
    void nonObjectGlobalUsesGenericFallback() throws Exception {
        try (ProtosStandaloneHostedSession session =
                open("r4-primitive-fallback", """
                        target: { f: 5 }
                        reader: () => { target }
                        """)) {
            ProtosClosureValue anchor =
                    session.prepareTopLevel("reader").closureForTesting();

            session.processContextForTesting().callForRuntime(() -> {
                ProtosLexicalEnvironment module =
                        anchor.capturedLexicalEnvironmentForRuntime();

                ProtosFrameLexicalBindingAuthority authority = assertInstanceOf(
                        ProtosFrameLexicalBindingAuthority.class,
                        module.lexicalBindingAuthorityForRuntime());

                ProtosFrameLexicalLayout layout = authority.storedLayout();
                int ordinal = layout.offsetOf("target");
                int physicalIndex = 2 + layout.localOffsetAt(ordinal);

                com.oracle.truffle.api.frame.MaterializedFrame frame =
                        authority.retainedMaterializedFrameForCapturedAccess();

                assertTrue(
                        physicalIndex >= 2
                                && physicalIndex < frame.getFrameDescriptor().getNumberOfSlots(),
                        "the pinned generated index must be in bounds");
                assertTrue(frame.isObject(physicalIndex),
                        "the fixture starts with a real Object slot");

                ProtosFrameLexicalBindingAuthority.ExperimentalGlobalReadLocation location =
                        authority.experimentalGlobalReadLocationOrNull(
                                "target", ordinal);

                assertTrue(location != null, "the initial Object is admitted");

                Object original = frame.getValue(physicalIndex);

                ProtosActivation activation =
                        ProtosActivation.forClosureInvocation(
                                anchor,
                                List.of(),
                                anchor.prelude().orElseThrow());

                Object[] published =
                        ProtosFrameArguments.publishedInvocation(activation);

                com.oracle.truffle.api.strings.TruffleString name =
                        com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
                                "target",
                                com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16);

                try {
                    /*
                     * Test-only physical representation transition.
                     * This is the actual retained module frame, not a mock.
                     */
                    frame.setLong(physicalIndex, 41L);

                    assertTrue(frame.isLong(physicalIndex));
                    assertFalse(frame.isObject(physicalIndex));

                    assertNull(
                            authority.experimentalGlobalReadLocationOrNull(
                                    "target", ordinal),
                            "primitive storage must not install an Object location");

                    ProtosIntegerValue generic = assertInstanceOf(
                            ProtosIntegerValue.class,
                            ProtosExperimentalReadGlobalNode.resolveInitial(
                                    published, ordinal, name, 1));

                    assertEquals(41L, generic.longValue(),
                            "the generic route converts the long carrier");

                    ProtosIntegerValue recovered = assertInstanceOf(
                            ProtosIntegerValue.class,
                            location.read());

                    assertEquals(41L, recovered.longValue(),
                            "the old Object location recovers after a type change");

                    assertFalse(location.isCurrent(),
                            "the changed-type location must be invalidated");
                } finally {
                    frame.setObject(physicalIndex, original);
                }

                assertTrue(frame.isObject(physicalIndex),
                        "the original physical representation is restored");
                assertSame(original, frame.getValue(physicalIndex));

                return null;
            });
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
            // I115: no H7 admission exists; run keeps its own root.
            assertNotNull(session.processContextForTesting().callForRuntime(() ->
                    ProtosClosureCallPreparation.functionCallTarget(
                            anchor, ProtosLanguageContext.current())));
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

    /*
     * The Context-owned target CallBinaryMethod enters for closure (admitted);
     * it exists once the method has been called in this Context.
     */
    private static ProtosBytecodeRootNode admittedCallee(
            ProtosStandaloneHostedSession session, ProtosClosureValue closure) {
        return session.processContextForTesting().callForRuntime(() ->
                assertInstanceOf(ProtosBytecodeRootNode.class,
                        ProtosClosureCallPreparation.functionCallTarget(
                                        closure, ProtosLanguageContext.current())
                                .getRootNode()));
    }

    /* The raw result of a zero-argument top-level root, inside its Context. */
    private static Object rawResult(ProtosStandaloneHostedSession session, String name)
            throws Exception {
        ProtosHostExecutableClosure host = session.prepareTopLevel(name).hostExecutableForTesting();
        return session.processContextForTesting().callForRuntime(() ->
                host.target.call(ProtosFrameArguments.compactDirectClosureCallZeroNoHome(
                        host.closure, host.caller)));
    }

    private static ProtosClosureValue methodClosure(
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
            return assertInstanceOf(ProtosClosureValue.class,
                    object.readLocalSlot(selector).orElseThrow());
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

    /* The host-boundary result of an admitted H7 root: the raw Long. */
    private static void assertRawLong(
            ProtosStandaloneHostedSession session, String name, long expected)
            throws Exception {
        ProtosHostExecutableClosure host =
                session.prepareTopLevel(name).hostExecutableForTesting();
        ProtosBytecodeRootNode target = assertInstanceOf(
                ProtosBytecodeRootNode.class, host.target.getRootNode());
        assertTrue(target.isExperimentalH7RawIntegerReturn(), name);
        Object result = session.processContextForTesting().callForRuntime(() -> {
            try {
                return InteropLibrary.getUncached().execute(host);
            } catch (InteropException unexpected) {
                throw new AssertionError(unexpected);
            }
        });
        assertEquals(expected, assertInstanceOf(Long.class, result,
                name + " must not carry Integer or ProtosIntegerValue").longValue(), name);
    }

    private static void assertDouble(
            ProtosStandaloneHostedSession session, String name, double expected)
            throws Exception {
        Value result = session.prepareTopLevel(name).executable().execute();
        assertTrue(result.isNumber(), name + " is a guest number: " + result);
        assertTrue(result.fitsInDouble(), name + ": " + result);
        assertEquals(expected, result.asDouble(), 0.0, name);
    }

    private static void assertLarge(
            ProtosStandaloneHostedSession session, String name, String expected)
            throws Exception {
        Value result = session.prepareTopLevel(name).executable().execute();
        assertTrue(result.isNumber(), name + " is a guest number: " + result);
        assertFalse(result.fitsInLong(), name + " promotes beyond signed-64: " + result);
        assertTrue(result.toString().contains(expected),
                name + " exact large Integer: " + result);
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
