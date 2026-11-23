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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I115: bracket reads, indexed assignment and size over the
 * Bytecode DSL operations. BinarySubscript/SetItem/GetLen own the selection:
 * standard Array/Bytes behavior executes in their standard specializations,
 * and every other selected behavior is invoked by their fallback, with the
 * result composed as a possible continuation.
 */
final class ProtosI115IndexedOperationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path OUTPUT = Path.of("target", "i115-indexed-operations");
    private static final String BYTES = "source: Encoding.UTF8.encode(\"jobs\")\n";
    private static final String TRACE = "trace: { value: 0 }\n"
            + "step: (digit, value) => {\n"
            + "    trace.value = trace.value * 10 + digit\n"
            + "    value\n"
            + "}\n";

    @Test
    void failingIndexedOperandDoesNotCommitItsNumericConsumer() {
        assertTrue(evalBoolean("""
                state: { order: 0 }
                run: () => {
                    total: 5
                    read: () => total
                    values: [7]
                    index: () => {
                        state.order = state.order * 10 + 2
                        9
                    }
                    left: () => {
                        state.order = state.order * 10 + 1
                        total
                    }
                    Error.handle(() => { total = left() + values[index()] }, (error) => null)
                    (state.order == 12) && (read() == 5)
                }
                run()
                """));
    }

    @Test
    void arrayReadWriteBoundsAndFrozenState() {
        assertEquals(44L, evalLong("a: [1, 2, 3]\na[1] = 40\na[0] + a[1] + a[2]"));
        assertEquals(3L, evalLong("a: [1, 2, 3]\na[2] = 9\na.size()"));
        assertTrue(evalBoolean("a: [1, 2, 3]\na[1] = \"x\"\na[1] == \"x\""));
        assertTrue(signals("a: [1, 2, 3]\na[3] = 1"));
        assertTrue(signals("a: [1, 2, 3]\na[0 - 1] = 1"));
        assertTrue(signals("a: [1, 2, 3]\na[1.0] = 1"));
        assertTrue(signals("a: [1, 2, 3]\na.freeze()\na[0] = 1"));
        assertTrue(signals("a: [1, 2, 3]\na.freeze()\na[7] = 1"));
        assertEquals(1L, evalLong("a: [1, 2, 3]\na.freeze()\n"
                + "Error.handle(() => { a[0] = 9 }, (caught) => null)\na[0]"));
    }

    private static final long[] SIXTY_FOUR = {
        13, 18, 6, 42, 43, 1, 58, 4,
        7, 32, 8, 59, 9, 64, 38, 37,
        25, 61, 24, 21, 23, 63, 35, 31,
        34, 30, 17, 48, 51, 41, 27, 40,
        15, 33, 46, 2, 28, 44, 54, 56,
        62, 11, 22, 12, 19, 50, 26, 55,
        60, 53, 57, 36, 5, 45, 14, 16,
        20, 39, 47, 10, 3, 52, 29, 49
    };

    private static final String MAKE_SIXTY_FOUR = "make: () => [\n"
            + "    13, 18, 6, 42, 43, 1, 58, 4,\n"
            + "    7, 32, 8, 59, 9, 64, 38, 37,\n"
            + "    25, 61, 24, 21, 23, 63, 35, 31,\n"
            + "    34, 30, 17, 48, 51, 41, 27, 40,\n"
            + "    15, 33, 46, 2, 28, 44, 54, 56,\n"
            + "    62, 11, 22, 12, 19, 50, 26, 55,\n"
            + "    60, 53, 57, 36, 5, 45, 14, 16,\n"
            + "    20, 39, 47, 10, 3, 52, 29, 49\n"
            + "]\n"
            + "weigh: (a) => {\n"
            + "    sum: 0\n"
            + "    i: 0\n"
            + "    while(i < a.size()) {\n"
            + "        sum = sum + a[i] * (i + 1)\n"
            + "        i = i + 1\n"
            + "    }\n"
            + "    sum\n"
            + "}\n";

    @Test
    void repeatedSixtyFourIntegerLiteralYieldsFreshOriginalArrays() {
        long weight = 0;
        for (int index = 0; index < SIXTY_FOUR.length; index++) {
            weight += SIXTY_FOUR[index] * (index + 1);
        }
        String weighs = "weight: " + weight + "\n";
        // Each round re-evaluates the same construction site, verifies the
        // original contents, mutates the result and observes the next one.
        assertEquals(6L, evalLong(MAKE_SIXTY_FOUR + weighs + """
                previous: null
                rounds: 0
                passed: 0
                while(rounds < 6) {
                    a: make()
                    fresh: (a.size() == 64) && (weigh(a) == weight) && (a[0] == 13) && (a[63] == 49) && (a !== previous)
                    if(previous !== null) {
                        fresh = fresh && (previous[0] == 1000) && (previous[63] == 2000)
                    }
                    written: (a[0] = 1000) == 1000
                    selected: a.atPut(63, 2000) == 2000
                    middle: (a[31] = 3000) == 3000
                    mutated: (a[0] == 1000) && (a[63] == 2000) && (a[31] == 3000) && (a.size() == 64)
                    if(fresh && written && selected && middle && mutated) {
                        passed = passed + 1
                    }
                    previous = a
                    rounds = rounds + 1
                }
                passed
                """));
        StringBuilder exact = new StringBuilder("a: make()\na[0] = 0\na[63] = 0\nb: make()\n");
        for (int index = 0; index < SIXTY_FOUR.length; index++) {
            exact.append(index == 0 ? "" : " && ")
                    .append("(b[").append(index).append("] == ")
                    .append(SIXTY_FOUR[index]).append(')');
        }
        assertTrue(evalBoolean(MAKE_SIXTY_FOUR + exact));
        assertTrue(signals(MAKE_SIXTY_FOUR + "a: make()\na[64]"));
        assertTrue(signals(MAKE_SIXTY_FOUR + "a: make()\na[64] = 1"));
        assertTrue(signals(MAKE_SIXTY_FOUR + "a: make()\na[0 - 65] = 1"));
        assertTrue(signals(MAKE_SIXTY_FOUR + "a: make()\na[1.0] = 1"));
        assertTrue(signals(MAKE_SIXTY_FOUR + "a: make()\na.atPut(64, 1)"));
        assertTrue(signals(MAKE_SIXTY_FOUR + "a: make()\na.freeze()\na[0] = 1"));
        assertTrue(evalBoolean(MAKE_SIXTY_FOUR + """
                a: make()
                Error.handle(() => { a[64] = 1 }, (caught) => null)
                a.freeze()
                Error.handle(() => { a[0] = 1 }, (caught) => null)
                b: make()
                b[0] = 7
                (a[0] == 13) && (a[63] == 49) && (a.size() == 64) && (b[0] == 7)
                """));
        // A dynamic override on one Array takes its semantic route; the next
        // Array from the same site keeps the standard selection.
        assertTrue(evalBoolean(MAKE_SIXTY_FOUR + """
                a: make()
                a.at: (index) => { index + 100 }
                a.atPut: (index, value) => { 99 }
                overridden: (a[5] == 105) && (a.atPut(0, 7) == 99) && ((a[0] = 8) == 8)
                b: make()
                standard: (b[5] == 1) && (b.atPut(0, 7) == 7) && (b[0] == 7) && (b[63] == 49)
                overridden && standard
                """));
    }

    @Test
    void explicitAtPutUsesSetItemAndPreservesSelectedResult() throws Exception {
        try (ProtosStandaloneHostedSession session = open("""
                a: [1, 2]
                run: () => { a.atPut(0, 7) }
                """)) {
            var prepared = session.prepareTopLevel("run");
            ProtosBytecodeRootNode root = (ProtosBytecodeRootNode)
                    prepared.hostExecutableForTesting().target.getRootNode();
            List<String> instructions = names(root);
            assertTrue(instructions.stream().anyMatch(name -> name.startsWith("c.SetItem")),
                    instructions.toString());
            assertEquals(7L, prepared.executable().execute().asLong());
        }
        assertEquals(999L, evalLong("box: { atPut: (index, value) => { 999 } }\nbox.atPut(0, 7)"));
        assertEquals(7L, evalLong("box: { atPut: (index, value) => { 999 } }\n(box[0] = 7)"));
    }

    @Test
    void rhsOverrideAndFreezeAreObservedBeforeIndexedSelection() {
        assertEquals(99L, evalLong("""
                a: [1, 2]
                value: () => { a.atPut: (index, item) => { 99 }; 7 }
                a.atPut(0, value())
                """));
        assertTrue(evalBoolean("""
                a: [1, 2]
                value: () => { a.freeze(); 7 }
                rejected: Error.handle(() => { a.atPut(0, value()); false }, (caught) => true)
                (rejected === true) && (a[0] == 1)
                """));
    }

    @Test
    void largeSignedLongIndexesSignalGuestErrors() {
        for (String index : List.of(
                "9223372036854775807",
                "0 - 9223372036854775807 - 1",
                "4294967296",
                "0 - 4294967296",
                "2147483648")) {
            assertTrue(signals("a: [1, 2, 3]\na[" + index + "]"), index);
            assertTrue(signals("a: [1, 2, 3]\na[" + index + "] = 1"), index);
            assertTrue(signals(BYTES + "source[" + index + "]"), index);
            assertTrue(signals(BYTES + "source[" + index + "] = 1"), index);
        }
    }

    /*
     * Array bounds over signed long indexes: 0..size-1 is accepted, every
     * negative index and every index at or above the size signals the
     * guest Error without normalization or truncation, and a handled Error
     * leaves the original elements in place; an empty Array rejects all.
     */
    @Test
    void unsignedArrayBoundsPreserveLongIndexSemantics() {
        assertEquals(10L, evalLong("a: [10, 20, 30]\na[0]"));
        assertEquals(20L, evalLong("a: [10, 20, 30]\na[1]"));
        assertEquals(30L, evalLong("a: [10, 20, 30]\na[2]"));
        assertEquals(7L, evalLong("a: [10, 20, 30]\na[0] = 7\na[0]"));
        assertEquals(8L, evalLong("a: [10, 20, 30]\na[2] = 8\na[2]"));
        for (String index : List.of(
                "0 - 1",
                "0 - 3",
                "0 - 9223372036854775807 - 1",
                "3",
                "4",
                "2147483647",
                "2147483648",
                "4294967296",
                "9223372036854775807")) {
            assertTrue(signals("a: [10, 20, 30]\na[" + index + "]"), index);
            assertTrue(signals("a: [10, 20, 30]\na[" + index + "] = 1"), index);
            assertTrue(evalBoolean("a: [10, 20, 30]\n"
                    + "Error.handle(() => { a[" + index + "] }, (caught) => null)\n"
                    + "Error.handle(() => { a[" + index + "] = 99 }, (caught) => null)\n"
                    + "(a.size() == 3) && (a[0] == 10) && (a[1] == 20) && (a[2] == 30)"),
                    index);
        }
        for (String index : List.of(
                "0", "1", "0 - 1", "2147483648", "9223372036854775807",
                "0 - 9223372036854775807 - 1")) {
            assertTrue(signals("a: []\na[" + index + "]"), index);
            assertTrue(signals("a: []\na[" + index + "] = 1"), index);
        }
        assertTrue(evalBoolean("a: []\n"
                + "Error.handle(() => { a[0] = 1 }, (caught) => null)\na.size() == 0"));
    }

    /*
     * One read site and one write site alternate valid, negative, size and
     * extremely large indexes: each failure leaves the storage untouched,
     * each valid write answers the assigned value, and receiver, index and
     * value are evaluated once per execution.
     */
    @Test
    void unsignedArrayBoundsRemainCorrectAtOneSite() {
        assertTrue(evalBoolean(TRACE + """
                count: { value: 0 }
                once: (v) => { count.value = count.value + 1; v }
                read: (r, i) => { r[i] }
                write: (r, i, v) => { once(r)[once(i)] = once(v) }
                failed: (f) => { Error.handle(() => { f(); false }, (caught) => true) }
                a: [10, 20, 30]
                ok: true
                k: 0
                while(k < 12) {
                    ok = ok && (read(a, 0) == 10) && (write(a, 1, k) == k)
                    ok = ok && failed(() => { read(a, 0 - 1) }) && failed(() => { write(a, 0 - 1, 5) })
                    ok = ok && (read(a, 2) == 30) && (a[1] == k)
                    ok = ok && failed(() => { read(a, 3) }) && failed(() => { write(a, 3, 5) })
                    ok = ok && (read(a, 1) == k)
                    ok = ok && failed(() => { read(a, 9223372036854775807) })
                    ok = ok && failed(() => { write(a, 4294967296, 5) })
                    ok = ok && (a.size() == 3) && (a[0] == 10) && (a[2] == 30)
                    k = k + 1
                }
                ok && (count.value == 12 * 4 * 3) && (a[1] == 11)
                """));
        assertEquals(1237L, evalLong(TRACE
                + "a: [1, 2, 3]\n"
                + "write: (r, i, v) => { step(1, r)[step(2, i)] = step(3, v) }\n"
                + "write(a, 1, 7)\ntrace.value = 0\n"
                + "result: write(a, 0, 7)\n"
                + "trace.value * 10 + result"));
    }

    @Test
    void bytesReadWriteOctetRangeBoundsAndFrozenState() {
        assertEquals(107L, evalLong(BYTES + "source[0] = 107\nsource[0]"));
        assertEquals(4L, evalLong(BYTES + "source.size()"));
        assertTrue(signals(BYTES + "source[0] = 256"));
        assertTrue(signals(BYTES + "source[0] = 0 - 1"));
        assertTrue(signals(BYTES + "source[4] = 1"));
        assertTrue(signals(BYTES + "source.freeze()\nsource[0] = 1"));
    }

    @Test
    void mapInsertionReplacementLookupMissingKeyAndSize() {
        assertEquals(7L, evalLong("m: Map()\nm[\"key\"] = 7\nm[\"key\"]"));
        assertEquals(1L, evalLong("m: Map()\nm[\"key\"] = 7\nm[\"key\"] = 8\nm.size()"));
        assertEquals(8L, evalLong("m: Map()\nm[\"key\"] = 7\nm[\"key\"] = 8\nm[\"key\"]"));
        assertTrue(signals("m: Map()\nm[\"missing\"]"));
        assertEquals(0L, evalLong("m: Map()\nm.size()"));
    }

    @Test
    void identityMapInsertionReplacementIdentityLookupAndSize() {
        assertTrue(evalBoolean("a: { value: 1 }\nb: { value: 1 }\n"
                + "m: IdentityMap()\nm[a] = 10\nm[b] = 20\nm[a] = 11\n"
                + "(m.size() == 2) && (m[a] == 11) && (m[b] == 20)"));
        assertTrue(evalBoolean("key: {}\nvisiting: IdentityMap()\n"
                + "visiting[key] = true\nvisiting.containsKey(key)"));
        assertTrue(signals("m: IdentityMap()\nm[{}]"));
    }

    @Test
    void customObjectsProvideAtAtPutAndSize() {
        String box = "log: { value: 0 }\n"
                + "box: {\n"
                + "    at: (index) => { index * 2 }\n"
                + "    atPut: (index, value) => {\n"
                + "        log.value = index * 100 + value\n"
                + "        999\n"
                + "    }\n"
                + "    size: () => { 7 }\n"
                + "}\n";
        assertEquals(42L, evalLong(box + "box[21]"));
        assertEquals(7L, evalLong(box + "box.size()"));
        assertEquals(305L, evalLong(box + "box[3] = 5\nlog.value"));
        assertEquals(5L, evalLong(box + "(box[3] = 5)"));
        assertEquals(13L, evalLong(box + "box[3] + box.size()"));
    }

    @Test
    void receiverSpecificOverridesOfStandardIndexedBehaviorAreSelected() {
        String special = "log: { value: 0 }\n"
                + "Special: Array {\n"
                + "    at: (index) => { 42 }\n"
                + "    atPut: (index, value) => {\n"
                + "        log.value = value\n"
                + "        0\n"
                + "    }\n"
                + "    size: () => { 99 }\n"
                + "}\n"
                + "s: Special(1, 2, 3)\n";
        assertEquals(42L, evalLong(special + "s[0]"));
        assertEquals(99L, evalLong(special + "s.size()"));
        assertEquals(5L, evalLong(special + "s[0] = 5\nlog.value"));
        assertEquals(5L, evalLong(special + "(s[0] = 5)"));
        // The plain Array keeps its standard behavior in the same program.
        assertEquals(3L, evalLong(special + "plain: [1, 2, 3]\nplain[0] = 3\nplain[0]"));
    }

    /*
     * The inherited Array chain of these sites is FROZEN, so their cached
     * selections skip structural revalidation; the receiver Shape guard still
     * observes every receiver-local override and its removal at the same
     * read, write and size sites, without leaking to another Array.
     */
    @Test
    void frozenInheritedIndexedSelectionStillObservesReceiverOverrides() {
        assertTrue(evalBoolean(TRACE + WARMED_SITES + """
                count: (r) => { r.size() }
                traced: (r, i, v) => { step(1, r)[step(2, i)] = step(3, v) }
                a: [1, 2, 3]
                b: [4, 5, 6]
                ok: true
                k: 0
                while(k < 20) {
                    ok = ok && (read(a, 1) == 2) && (write(a, 0, 1) == 1)
                    ok = ok && (put(a, 2, 3) == 3) && (count(a) == 3)
                    k = k + 1
                }
                a.at: (index) => { index + 100 }
                ok = ok && (read(a, 1) == 101) && (read(a, 9) == 109) && (read(b, 1) == 5)
                a.removeSlot("at")
                ok = ok && (read(a, 1) == 2)
                ok = ok && Error.handle(() => { read(a, 3); false }, (caught) => isError(caught))
                a.atPut: (index, value) => { 77 }
                ok = ok && (put(a, 9, 8) == 77) && (write(a, 9, 8) == 8) && (a[0] == 1)
                ok = ok && (put(b, 0, 40) == 40) && (b[0] == 40)
                a.removeSlot("atPut")
                ok = ok && (put(a, 0, 6) == 6) && (read(a, 0) == 6)
                ok = ok && Error.handle(() => { write(a, 3, 1); false }, (caught) => isError(caught))
                a.size: () => { 99 }
                ok = ok && (count(a) == 99) && (count(b) == 3)
                a.removeSlot("size")
                ok = ok && (count(a) == 3)
                trace.value = 0
                rhsCount.value = 0
                ok = ok && (traced(a, 1, 7) == 7) && (trace.value == 123) && (a[1] == 7)
                trace.value = 0
                ok = ok && Error.handle(() => { traced(a, 5, 7); false }, (caught) => isError(caught))
                ok = ok && (trace.value == 123)
                ok = ok && (write(a, 2, 9) == 9) && (rhsCount.value == 1)
                ok && (read(b, 2) == 6) && (count(b) == 3)
                """));
    }

    @Test
    void indexedAssignmentEvaluatesReceiverIndexValueOnceInOrder() {
        assertEquals(1237L, evalLong(TRACE
                + "a: [1, 2, 3]\n"
                + "result: (step(1, a)[step(2, 0)] = step(3, 7))\n"
                + "trace.value * 10 + result"));
        assertEquals(123L, evalLong(TRACE
                + "m: Map()\nstep(1, m)[step(2, \"k\")] = step(3, 7)\ntrace.value"));
        assertEquals(7L, evalLong(TRACE
                + "m: Map()\nresult: (step(1, m)[step(2, \"k\")] = step(3, 7))\nresult"));
        assertEquals(1234L, evalLong(TRACE
                + "box: { atPut: (index, value) => { step(4, 999) } }\n"
                + "step(1, box)[step(2, 0)] = step(3, 7)\ntrace.value"));
    }

    @Test
    void failingMutationStillEvaluatesReceiverIndexValueInOrder() {
        assertEquals(123L, evalLong(TRACE
                + "a: [1, 2]\n"
                + "Error.handle(() => {\n"
                + "    step(1, a)[step(2, 5)] = step(3, 7)\n"
                + "}, (caught) => null)\ntrace.value"));
        assertEquals(123L, evalLong(TRACE
                + "a: [1, 2]\na.freeze()\n"
                + "Error.handle(() => {\n"
                + "    step(1, a)[step(2, 0)] = step(3, 7)\n"
                + "}, (caught) => null)\ntrace.value"));
        assertEquals(123L, evalLong(TRACE
                + "Error.handle(() => {\n"
                + "    step(1, {})[step(2, 0)] = step(3, 7)\n"
                + "}, (caught) => null)\ntrace.value"));
    }

    @Test
    void consumedAndDiscardedIndexedAssignmentsYieldTheRhs() {
        assertEquals(5L, evalLong("m: Map()\nm[\"a\"] = 1\nr: (m[\"b\"] = 2)\nr + m[\"a\"] + m.size()"));
        assertTrue(evalBoolean("marker: {}\nxs: [10, 20]\nwritten: (xs[0] = marker)\n"
                + "(written === marker) && (xs[0] === marker)"));
        assertTrue(evalBoolean("marker: {}\nm: IdentityMap()\nwritten: (m[marker] = marker)\n"
                + "written === marker"));
    }

    @Test
    void atPutReturningAFutureDoesNotReplaceTheRhs() {
        assertEquals(5L, evalLong(
                "box: { atPut: (index, value) => { (() => 9).future() } }\n"
                        + "(box[1] = 5)"));
    }

    @Test
    void customIndexedBehaviorsPropagateErrors() {
        assertTrue(signals("box: { at: (index) => { Error().signal() } }\nbox[1]"));
        assertTrue(signals("box: { atPut: (index, value) => { Error().signal() } }\nbox[1] = 2"));
        assertTrue(signals("box: { size: () => { Error().signal() } }\nbox.size()"));
        assertTrue(signals("box: {}\nbox[1]"));
        assertTrue(signals("box: {}\nbox[1] = 2"));
        assertTrue(signals("box: {}\nbox.size()"));
    }

    @Test
    void nonLocalReturnFromIndexedBehaviorMatchesOrdinarySend() {
        String viaIndex = "f: () => {\n"
                + "    source: { get: (index) => { ^42 } }\n"
                + "    view: source.alias(\"get\", \"at\")\n"
                + "    view[1]\n"
                + "    0\n"
                + "}\n";
        String viaSend = "g: () => {\n"
                + "    source: { get: (index) => { ^42 } }\n"
                + "    source.get(1)\n"
                + "    0\n"
                + "}\n";
        assertEquals(
                evalLong(viaSend + "g()"),
                evalLong(viaIndex + "f()"));
    }

    @Test
    void indexedOperationsOwnTheirSelectionWithoutAnExternalGuard()
            throws Exception {
        String source = """
                Box: {
                    at: (index) => { index + 100 }
                    atPut: (index, value) => { value * 2 }
                    size: () => { 9 }
                }
                run: () => {
                    m: Map()
                    m["k"] = 1
                    im: IdentityMap()
                    im[m] = 2
                    a: [5, 6]
                    a[0] = 7
                    b: Encoding.UTF8.encode("jobs")
                    b[0] = 107
                    m["k"] + m.size() + im[m] + im.size() + a[0] + a.size() + b[0] + b.size() + Box[1] + Box.atPut(0, 4) + Box.size()
                }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            ProtosBytecodeRootNode run = (ProtosBytecodeRootNode)
                    session.prepareTopLevel("run").hostExecutableForTesting()
                            .target.getRootNode();
            List<String> names = names(run);
            for (String operation : List.of("c.SetItem", "c.BinarySubscript", "c.GetLen")) {
                assertTrue(names.stream().anyMatch(name -> name.startsWith(operation)),
                        "missing " + operation + " in " + names);
            }
            assertTrue(names.stream().noneMatch(name -> name.startsWith("c.IsStandardIndexedSelection")),
                    "unexpected external selector in " + names);
            for (int i = 0; i < 5; i++) {
                assertEquals(1L + 1L + 2L + 1L + 7L + 2L + 107L + 4L + 101L + 8L + 9L,
                        session.prepareTopLevel("run").executable().execute().asLong());
            }
        }
    }

    @Test
    void simpleIndexedAccessesEmitOperandsDirectlyIntoTheOperation()
            throws Exception {
        String source = """
                read: (a, i) => { a[i] }
                write: (a, i, v) => { a[i] = v; 0 }
                length: (a) => { a.size() }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            for (String function : List.of("read", "write", "length")) {
                ProtosBytecodeRootNode root = (ProtosBytecodeRootNode)
                        session.prepareTopLevel(function).hostExecutableForTesting()
                                .target.getRootNode();
                List<String> names = names(root);
                assertTrue(names.stream().noneMatch(name -> name.startsWith("c.BindStackValue")
                                || name.startsWith("load.stackvalue")
                                || name.startsWith("store.stackvalue")),
                        function + " stages operands only to transport them: " + names);
                // No external selector and no standard/dynamic branch around the operation.
                for (String absent : List.of(
                        "c.IsStandardIndexedSelection",
                        "c.GetMethod",
                        "c.CallNilaryMethod",
                        "c.CallUnaryMethod",
                        "c.CallBinaryMethod")) {
                    assertTrue(names.stream().noneMatch(name -> name.startsWith(absent)),
                            function + " keeps external " + absent + ": " + names);
                }
            }
        }
    }

    /*
     * Independent budgets around each fused indexed operation of a simple
     * root: fused operations, external selection, external dispatch,
     * continuation checks, result stores, stack transport and the cleanup of
     * the operation's own result local.
     * Every class except the fused operation itself is an upper bound, so a
     * correct reduction to zero (no result local, no composition, no
     * cleanup) still passes. Ownership of a composition is structural (see
     * IndexedCensus), never the nearest preceding producer.
     */
    @Test
    void fusedIndexedOperationsCarryAtMostTheirOwnResultTransport()
            throws Exception {
        String source = """
                g: () => { 0 }
                read: (a, i) => { a[i] }
                discardRead: (a, i) => { a[i]; 0 }
                write: (a, i, v) => { a[i] = v }
                discardWrite: (a, i, v) => { a[i] = v; 0 }
                put: (a, i, v) => { a.atPut(i, v) }
                length: (a) => { a.size() }
                callThenRead: (a, i) => { g(); a[i] }
                readThenConstant: (a, i) => {
                    a[i]
                    0
                }
                writeThenConstant: (a, i, v) => {
                    a[i] = v
                    0
                }
                discardPut: (a, i, v) => { a.atPut(i, v); 0 }
                discardLength: (a) => { a.size(); 0 }
                readThenCall: (a, i) => { a[i]; g() }
                callThenWrite: (a, i, v) => { g(); a[i] = v }
                callThenPut: (a, i, v) => { g(); a.atPut(i, v) }
                callThenLength: (a) => { g(); a.size() }
                """;
        List<String> withCall = List.of(
                "callThenRead", "readThenCall", "callThenWrite", "callThenPut",
                "callThenLength");
        List<String> answeredWrites = List.of("write", "callThenWrite");
        try (ProtosStandaloneHostedSession session = open(source)) {
            for (String function : List.of(
                    "read", "discardRead", "write", "discardWrite", "put", "length",
                    "callThenRead", "readThenConstant", "writeThenConstant",
                    "discardPut", "discardLength", "readThenCall", "callThenWrite",
                    "callThenPut", "callThenLength")) {
                IndexedCensus census = IndexedCensus.of(root(session, function));
                List<String> names = census.names;
                List<Integer> operations = census.positions(
                        ProtosI115IndexedOperationTest::isIndexedOperation);
                assertEquals(1, operations.size(), function + ": " + names);
                int operation = operations.get(0);
                String operand = names.get(operation - 1);
                assertTrue(!operand.startsWith("store.local") && !operand.startsWith("clear.local"),
                        function + " stages an operand before the operation: " + names);

                assertEquals(0L, census.count(name -> name.startsWith("c.IsStandardIndexedSelection")),
                        function + " keeps the external selector: " + names);
                long outerCalls = withCall.contains(function) ? 1L : 0L;
                assertTrue(census.count(IndexedCensus::isExternalDispatch) <= outerCalls,
                        function + " keeps external dispatch: " + names);

                List<Integer> owned = census.ownedCompositions(operation);
                assertTrue(owned.size() <= 1,
                        function + " composes the operation more than once: " + names);
                assertTrue(census.count(name -> name.startsWith("c.IsContinuation"))
                                <= census.positions(ProtosI115IndexedOperationTest::isProducer).size(),
                        function + " checks more continuations than producers: " + names);

                assertTrue(census.resultStores(operation) <= 2,
                        function + " stores its result more than into one temporary"
                                + " and one result local: " + names);
                assertTrue(census.compositionStores() <= 2L * census.compositionCount(),
                        function + " stores more than resume and write-back per composition: "
                                + names);

                assertEquals(0L, census.count(name -> name.equals("dup")),
                        function + " duplicates an operand: " + names);
                long binds = census.count(name -> name.startsWith("c.BindStackValue"));
                long stackLoads = census.count(name -> name.startsWith("load.stackvalue"));
                if (answeredWrites.contains(function)) {
                    // The answered RHS is reused: each operand is bound at most once
                    // and loaded at most once, plus the single RHS answer.
                    assertTrue(binds <= 3 && stackLoads <= 4,
                            function + " transports beyond its reused RHS: " + names);
                } else {
                    assertEquals(0L, binds + stackLoads
                                    + census.count(name -> name.contains("stackvalue")),
                            function + " binds operands it consumes once: " + names);
                }

                // Cleanup is checked only on the local the indexed operation's
                // own result is stored into; parameter and other-local clears
                // are not charged to the operation.
                census.assertResultLocalReleased(operation, function);
                assertTrue(census.cleanupInsideComposition() == 0,
                        function + " clears a local inside a composition: " + names);
            }
        }
    }

    /*
     * One root reuses its continuation scratch for several indexed reads,
     * writes, sizes and calls before its final value: releasing the indexed
     * result at its last use never disturbs a later operand or result, both
     * when the final value is an indexed read and when an indexed write ends
     * a discarded prefix.
     */
    @Test
    void reusedScratchKeepsLaterOperandsAndFinalValue() throws Exception {
        String source = """
                g: () => { 0 }
                reuseThenRead: (a, i) => {
                    a[i] = a[i] + 1
                    g()
                    a[i + 1] = a[i] * 2
                    a.size()
                    a[i + 1]
                }
                reuseThenConstant: (a, i) => {
                    a[i] = a.size()
                    a[i + 1]
                    a[i + 1] = a[i] + 5
                    0
                }
                run: () => {
                    a: [1, 2, 3]
                    r: reuseThenRead(a, 0)
                    b: [5, 6, 7]
                    c: reuseThenConstant(b, 1)
                    first: (r == 4) && (a[0] == 2) && (a[1] == 4) && (a[2] == 3)
                    first && (c == 0) && (b[0] == 5) && (b[1] == 3) && (b[2] == 8)
                }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            assertTrue(session.prepareTopLevel("run").executable().execute().asBoolean());
        }
    }

    /*
     * Nested indexed and numeric operations: the arithmetic computing an
     * index or a RHS is distinguished from the arithmetic consuming an
     * indexed result by bytecode order (an operand is emitted before the
     * operation consuming it) and, where available, by source sections.
     * Every semantic occurrence is emitted once, no external indexed
     * selector returns, and each composition is owned by at most one
     * producer, so a numeric continuation is never imputed to an indexed
     * operation or vice versa.
     */
    @Test
    void nestedIndexedAndNumericOperationsOwnTheirCostsSeparately()
            throws Exception {
        String source = """
                read: (a, k) => { a[k + 1] }
                write: (a, k, v) => { a[k + 1] = v * 2 }
                chain: (a, k) => { a[k + 1] + a[k - 1] }
                runNested: () => {
                    a: [10, 20, 30]
                    r: read(a, 0)
                    w: write(a, 0, 4)
                    c: chain(a, 1)
                    (r == 20) && (w == 8) && (a[1] == 8) && (c == 40)
                }
                accumulate: (a, k, s) => { s + a[k] }
                runAccumulate: () => {
                    accumulate([10, 20, 30], 1, 5) == 25
                }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            IndexedCensus read = IndexedCensus.of(root(session, "read"));
            assertProducers(read, List.of("c.NumberAdd", "c.BinarySubscript"));
            assertIndexOperandInside(read, 0, 1);

            IndexedCensus write = IndexedCensus.of(root(session, "write"));
            assertProducers(write, List.of("c.NumberAdd", "c.NumberMultiply", "c.SetItem"));
            assertIndexOperandInside(write, 0, 2);
            assertIndexOperandInside(write, 1, 2);

            IndexedCensus chain = IndexedCensus.of(root(session, "chain"));
            assertProducers(chain, List.of(
                    "c.NumberAdd", "c.BinarySubscript",
                    "c.NumberSubtract", "c.BinarySubscript",
                    "c.NumberAdd"));
            assertIndexOperandInside(chain, 0, 1);
            assertIndexOperandInside(chain, 2, 3);
            assertConsumerCovers(chain, 4, 1, 3);
            assertDirectIndexedOperands(chain, "chain", List.of(1, 3), 4);

            IndexedCensus accumulate = IndexedCensus.of(root(session, "accumulate"));
            assertProducers(accumulate, List.of("c.BinarySubscript", "c.NumberAdd"));
            assertConsumerCovers(accumulate, 1, 0, 0);
            assertDirectIndexedOperands(accumulate, "accumulate", List.of(0), 1);

            for (IndexedCensus census : List.of(read, write, chain, accumulate)) {
                assertEquals(0L, census.count(name -> name.startsWith("c.IsStandardIndexedSelection")),
                        "external indexed selector: " + census.names);
                assertEquals(0L, census.count(IndexedCensus::isExternalDispatch),
                        "external dispatch: " + census.names);
                List<Integer> producers = census.positions(ProtosI115IndexedOperationTest::isProducer);
                java.util.Set<Integer> claimed = new java.util.HashSet<>();
                for (int producer : producers) {
                    List<Integer> owned = census.ownedCompositions(producer);
                    assertTrue(owned.size() <= 1,
                            census.names.get(producer) + " composes more than once: " + census.names);
                    for (int composition : owned) {
                        assertTrue(claimed.add(composition),
                                "composition imputed to two producers: " + census.names);
                    }
                }
                assertTrue(census.compositionCount() <= producers.size(), census.names.toString());
                assertTrue(census.cleanupInsideComposition() == 0, census.names.toString());
            }
            assertTrue(session.prepareTopLevel("runNested").executable().execute().asBoolean());
            assertTrue(session.prepareTopLevel("runAccumulate").executable().execute().asBoolean());
        }
    }

    /*
     * Each indexed operand (by producer ordinal) reaches its numeric
     * consumer directly: one guarded continuation per operation, one
     * release of the indexed outcome as the consumer's operand, and no
     * intermediate operand-local copy.
     */
    private static void assertDirectIndexedOperands(
            IndexedCensus census, String label, List<Integer> indexedOrdinals,
            int consumerOrdinal) {
        List<Integer> producers = census.positions(ProtosI115IndexedOperationTest::isProducer);
        int consumer = producers.get(consumerOrdinal);
        assertTrue(census.names.get(consumer).startsWith("c.Number"),
                label + ": consumer is not a numeric operation: " + census.names);
        assertTrue(census.ownsResult(consumer),
                label + ": numeric consumer does not store its own result: " + census.names);
        assertTrue(census.names.get(consumer + 1).startsWith("branch.false")
                        || census.names.get(consumer + 1).startsWith("pop")
                        || census.names.get(consumer + 1).startsWith("load.local"),
                label + ": numeric consumer lacks its own result guard: " + census.names);
        for (int ordinal : indexedOrdinals) {
            int indexed = producers.get(ordinal);
            assertTrue(isIndexedOperation(census.names.get(indexed)),
                    label + ": producer " + ordinal + " is not indexed: " + census.names);
            census.assertDirectlyConsumed(indexed, consumer, label);
        }
        assertEquals(0L, census.count(IndexedCensus::isExternalDispatch),
                label + ": ordinary indexed path uses an external dispatcher: " + census.names);
    }

    private static void assertProducers(IndexedCensus census, List<String> expected) {
        List<String> producers = census.positions(ProtosI115IndexedOperationTest::isProducer)
                .stream().map(census.names::get).toList();
        assertEquals(expected.size(), producers.size(), census.names.toString());
        for (int i = 0; i < expected.size(); i++) {
            assertTrue(producers.get(i).startsWith(expected.get(i)),
                    "producer " + i + " is " + producers.get(i) + ", expected "
                            + expected.get(i) + ": " + census.names);
        }
    }

    /*
     * The operand producer (by producer ordinal) precedes the operation
     * consuming it; when both carry distinct source sections, the operand's
     * section lies within the operation's.
     */
    private static void assertIndexOperandInside(
            IndexedCensus census, int operandOrdinal, int operationOrdinal) {
        List<Integer> producers = census.positions(ProtosI115IndexedOperationTest::isProducer);
        int operand = producers.get(operandOrdinal);
        int operation = producers.get(operationOrdinal);
        assertTrue(census.bci(operand) < census.bci(operation), census.names.toString());
        com.oracle.truffle.api.source.SourceSection inner = census.section(operand);
        com.oracle.truffle.api.source.SourceSection outer = census.section(operation);
        if (inner != null && outer != null && !inner.equals(outer)) {
            assertTrue(outer.getCharIndex() <= inner.getCharIndex()
                            && inner.getCharEndIndex() <= outer.getCharEndIndex(),
                    "operand section " + inner.getCharacters() + " outside "
                            + outer.getCharacters() + ": " + census.names);
        }
    }

    /*
     * The consuming operation follows both indexed operands; when sections
     * are available it covers both of them.
     */
    private static void assertConsumerCovers(
            IndexedCensus census, int consumerOrdinal, int leftOrdinal, int rightOrdinal) {
        List<Integer> producers = census.positions(ProtosI115IndexedOperationTest::isProducer);
        int consumer = producers.get(consumerOrdinal);
        for (int ordinal : List.of(leftOrdinal, rightOrdinal)) {
            int operand = producers.get(ordinal);
            assertTrue(census.bci(operand) < census.bci(consumer), census.names.toString());
            com.oracle.truffle.api.source.SourceSection inner = census.section(operand);
            com.oracle.truffle.api.source.SourceSection outer = census.section(consumer);
            if (inner != null && outer != null && !inner.equals(outer)) {
                assertTrue(outer.getCharIndex() <= inner.getCharIndex()
                                && inner.getCharEndIndex() <= outer.getCharEndIndex(),
                        "consumer " + outer.getCharacters() + " does not cover "
                                + inner.getCharacters() + ": " + census.names);
            }
        }
    }

    private static boolean isIndexedOperation(String name) {
        return name.startsWith("c.BinarySubscript")
                || name.startsWith("c.SetItem")
                || name.startsWith("c.GetLen");
    }

    private static boolean isProducer(String name) {
        return isIndexedOperation(name)
                || name.startsWith("c.Number")
                || name.startsWith("c.Call");
    }

    /*
     * Instructions of one root in bytecode-index order, with structural
     * composition ownership. A composition (an IsContinuation and its loop up
     * to the next branch.backward) is owned by a producer only when its
     * checked value is that producer's result: either the IsContinuation
     * consumes the producer's stack result directly, or it consumes a
     * load.local of the local the producer's result was stored into, with no
     * other producer storing into that local in between. A composition that
     * matches neither condition is owned by nobody.
     */
    /*
     * A frame local's identity in a listing: its offset, and its local index
     * when the instruction publishes one. Two locals of different scopes may
     * reuse one offset; when both sides publish an index, the index separates
     * them, and a side without one cannot be distinguished by offset alone.
     */
    private record LocalRef(int offset, int index) {
        static final int UNKNOWN_INDEX = -1;

        boolean sameAs(LocalRef other) {
            return offset == other.offset
                    && (index == UNKNOWN_INDEX || other.index == UNKNOWN_INDEX
                            || index == other.index);
        }

        /* This local, taking the index other publishes when this one has none. */
        LocalRef refinedBy(LocalRef other) {
            return index == UNKNOWN_INDEX ? new LocalRef(offset, other.index) : this;
        }
    }

    private static final class IndexedCensus {
        final List<com.oracle.truffle.api.bytecode.Instruction> instructions;
        final List<String> names;

        private IndexedCensus(List<com.oracle.truffle.api.bytecode.Instruction> instructions) {
            this.instructions = instructions;
            this.names = instructions.stream().map(ProtosInstructionNames::of).toList();
            for (int i = 1; i < instructions.size(); i++) {
                assertTrue(bci(i - 1) < bci(i), "instructions out of bytecode order: " + names);
            }
        }

        static IndexedCensus of(ProtosBytecodeRootNode root) {
            return new IndexedCensus(root.getBytecodeNode().getInstructionsAsList());
        }

        static boolean isExternalDispatch(String name) {
            return name.startsWith("c.GetMethod")
                    || name.startsWith("c.CallNilaryMethod")
                    || name.startsWith("c.CallUnaryMethod")
                    || name.startsWith("c.CallBinaryMethod")
                    || name.startsWith("c.CallRequest");
        }

        int bci(int position) {
            return instructions.get(position).getBytecodeIndex();
        }

        com.oracle.truffle.api.source.SourceSection section(int position) {
            return instructions.get(position).getSourceSection();
        }

        long count(java.util.function.Predicate<String> predicate) {
            return names.stream().filter(predicate).count();
        }

        List<Integer> positions(java.util.function.Predicate<String> predicate) {
            List<Integer> found = new java.util.ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                if (predicate.test(names.get(i))) {
                    found.add(i);
                }
            }
            return found;
        }

        /*
         * Locals accessed, with their identity: the offset, plus the local
         * index when the instruction publishes it (a LocalAccessor does, as
         * "LocalAccessor[localOffset=N, localIndex=M]"; LOCAL_OFFSET
         * arguments do not, and answer UNKNOWN_INDEX).
         */
        private List<LocalRef> localRefs(int position) {
            List<LocalRef> refs = new java.util.ArrayList<>();
            for (com.oracle.truffle.api.bytecode.Instruction.Argument argument
                    : instructions.get(position).getArguments()) {
                switch (argument.getKind()) {
                    case LOCAL_OFFSET -> refs.add(
                            new LocalRef(argument.asLocalOffset(), LocalRef.UNKNOWN_INDEX));
                    case CONSTANT -> {
                        if (argument.asConstant()
                                instanceof com.oracle.truffle.api.bytecode.LocalAccessor accessor) {
                            String form = accessor.toString();
                            java.util.regex.Matcher offset = java.util.regex.Pattern
                                    .compile("localOffset=(-?\\d+)").matcher(form);
                            assertTrue(offset.find(), "unexpected LocalAccessor form: " + form);
                            java.util.regex.Matcher index = java.util.regex.Pattern
                                    .compile("localIndex=(-?\\d+)").matcher(form);
                            refs.add(new LocalRef(Integer.parseInt(offset.group(1)),
                                    index.find() ? Integer.parseInt(index.group(1))
                                            : LocalRef.UNKNOWN_INDEX));
                        }
                    }
                    default -> { }
                }
            }
            return refs;
        }

        /* The first local at position that may be local, or null. */
        private LocalRef matching(int position, LocalRef local) {
            for (LocalRef candidate : localRefs(position)) {
                if (candidate.sameAs(local)) {
                    return candidate;
                }
            }
            return null;
        }

        /*
         * Whether the operation at position stores its own result through
         * its constant outcome LocalAccessor: a numeric or indexed producer.
         */
        boolean ownsResult(int position) {
            String name = names.get(position);
            return (isIndexedOperation(name) || name.startsWith("c.Number")
                            || name.startsWith("c.RichCompare"))
                    && !localRefs(position).isEmpty();
        }

        /* The local the producer's result is stored into, or null. */
        private LocalRef resultLocalRef(int producer) {
            if (ownsResult(producer)) {
                return localRefs(producer).get(0);
            }
            if (producer + 1 < names.size()
                    && (names.get(producer + 1).startsWith("store.local")
                            || names.get(producer + 1).startsWith("c.StoreAndTestContinuation"))) {
                List<LocalRef> stored = localRefs(producer + 1);
                return stored.isEmpty() ? null : stored.get(0);
            }
            return null;
        }

        /*
         * Whether position ends a linear run: past it, bytecode order is not
         * execution order (synthetic continuation branches, loops, returns).
         */
        private boolean endsLinearRun(int position) {
            String name = names.get(position);
            return name.startsWith("branch") || name.startsWith("return")
                    || name.startsWith("yield") || name.startsWith("throw")
                    || name.startsWith("c.IsContinuation") || name.startsWith("c.Resume");
        }

        /*
         * From the release of local at release, along the linear run that
         * follows it and until a new write of the local, the local is neither
         * read nor released again. Every execution reaching the release
         * executes that run in order, so a hit is a real use after release.
         */
        private void assertNotUsedInLinearRunAfter(int release, LocalRef local, String label) {
            for (int i = release + 1; i < names.size() && !endsLinearRun(i - 1); i++) {
                LocalRef hit = matching(i, local);
                if (hit == null) {
                    continue;
                }
                String name = names.get(i);
                if (isResultWrite(i)) {
                    return;
                }
                assertTrue(!name.startsWith("c.LoadAndClearTempLocal"), label + " releases local "
                        + local + " twice without a new write: " + names);
                assertTrue(!name.startsWith("load.local"), label + " reads local " + local
                        + " after its release without a new write: " + names);
            }
        }

        /*
         * A composition is owned by producer when its IsContinuation consumes
         * the producer's stack result directly, or when it consumes a
         * load.local whose local is, by LocalRef identity, the local of the
         * nearest preceding producer-result write (a producer storing its own
         * result through its outcome LocalAccessor, or a store.local or
         * StoreAndTestContinuation immediately following a producer). The
         * nearest such write is unique, so one composition never has two
         * owners, and a logical local that only reuses the same physical
         * offset under a different published index never takes ownership.
         */
        List<Integer> ownedCompositions(int producer) {
            List<Integer> owned = new java.util.ArrayList<>();
            for (int j = producer + 1; j < names.size(); j++) {
                if (!names.get(j).startsWith("c.IsContinuation")) {
                    continue;
                }
                if (compositionOwner(j) == producer) {
                    owned.add(j);
                }
            }
            return owned;
        }

        /* The producer owning the composition at isContinuation, or -1. */
        private int compositionOwner(int isContinuation) {
            if (isContinuation < 1) {
                return -1;
            }
            if (isProducer(names.get(isContinuation - 1))) {
                return isContinuation - 1;
            }
            if (!names.get(isContinuation - 1).startsWith("load.local")) {
                return -1;
            }
            List<LocalRef> read = localRefs(isContinuation - 1);
            if (read.isEmpty()) {
                return -1;
            }
            for (int k = isContinuation - 2; k >= 0; k--) {
                if (ownsResult(k)) {
                    LocalRef owned = resultLocalRef(k);
                    if (owned != null && owned.sameAs(read.get(0))) {
                        return k;
                    }
                    continue;
                }
                if (k < 1 || !isResultWrite(k) || !isProducer(names.get(k - 1))) {
                    continue;
                }
                LocalRef written = resultLocalRef(k - 1);
                if (written != null && written.sameAs(read.get(0))) {
                    return k - 1;
                }
            }
            return -1;
        }

        /* A write of an operation result into its destination local. */
        private boolean isResultWrite(int position) {
            String name = names.get(position);
            return name.startsWith("store.local")
                    || name.startsWith("c.StoreAndTestContinuation")
                    || ownsResult(position);
        }

        /*
         * The indexed producer writes its own result through its outcome
         * LocalAccessor, with no separate StoreAndTestContinuation; its
         * answer is the following branch.false, the result is released exactly once
         * outside its composition before any new write of its local, and the
         * instruction after that release is not a further store.local copy:
         * the release is itself the consumer's operand. Neither read nor
         * release follows it in the linear run without a new write.
         */
        void assertDirectlyConsumed(int producer, int consumer, String label) {
            assertTrue(ownsResult(producer),
                    label + " does not store its own result: " + names);
            assertTrue(names.get(producer + 1).startsWith("branch.false"),
                    label + " does not guard its composition: " + names);
            LocalRef local = resultLocalRef(producer);
            assertTrue(local != null, label + " has no result destination: " + names);
            boolean[] inside = compositionMask();
            int release = -1;
            for (int i = producer + 2; i < consumer; i++) {
                // The composition's own read of the outcome belongs to it.
                if (inside[i] || names.get(i + 1).startsWith("c.IsContinuation")) {
                    continue;
                }
                LocalRef hit = matching(i, local);
                if (hit == null) {
                    continue;
                }
                if (isResultWrite(i)) {
                    break;
                }
                String name = names.get(i);
                assertTrue(!name.startsWith("load.local") && !name.startsWith("clear.local"),
                        label + " copies or clears its result separately: " + names);
                if (name.startsWith("c.LoadAndClearTempLocal")) {
                    assertTrue(release < 0, label + " releases its result twice: " + names);
                    release = i;
                    local = local.refinedBy(hit);
                }
            }
            assertTrue(release > 0, label + " does not release its result before "
                    + names.get(consumer) + ": " + names);
            assertTrue(!names.get(release + 1).startsWith("store.local"),
                    label + " copies its result into another local: " + names);
            assertNotUsedInLinearRunAfter(release, local, label);
        }

        long compositionCount() {
            return count(name -> name.startsWith("c.IsContinuation"));
        }

        private boolean[] compositionMask() {
            boolean[] inside = new boolean[names.size()];
            boolean in = false;
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).startsWith("c.IsContinuation")) {
                    in = true;
                }
                inside[i] = in;
                if (names.get(i).startsWith("branch.backward")) {
                    in = false;
                }
            }
            return inside;
        }

        /*
         * Stores after the operation and outside any composition loop, up to
         * the next producer: stores of later producers are not attributed to
         * this operation.
         */
        long resultStores(int operation) {
            boolean[] inside = compositionMask();
            long stores = 0;
            for (int i = operation + 1; i < names.size() && !isProducer(names.get(i)); i++) {
                if (!inside[i] && isResultWrite(i)) {
                    stores++;
                }
            }
            return stores;
        }

        long compositionStores() {
            boolean[] inside = compositionMask();
            long stores = 0;
            for (int i = 0; i < names.size(); i++) {
                if (inside[i] && names.get(i).startsWith("store.local")) {
                    stores++;
                }
            }
            return stores;
        }

        /*
         * The producer's result local need not exist. When it does, it is
         * consumed (loaded, or loaded by its last use) or cleared before the
         * root's final return, and it never receives two explicit clears
         * without a new write in between. A load that clears the local is
         * its last use, so no clear may follow it without a new write. Only
         * instructions publishing the local's offset are attributed to it,
         * so parameter and other-local clears are never charged here.
         */
        void assertResultLocalReleased(int producer, String label) {
            LocalRef local = resultLocalRef(producer);
            if (local == null) {
                return;
            }
            int lastReturn = -1;
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).startsWith("return")) {
                    lastReturn = i;
                }
            }
            boolean released = false;
            boolean cleared = false;
            for (int i = producer + 2; i < names.size(); i++) {
                LocalRef hit = matching(i, local);
                if (hit == null) {
                    continue;
                }
                local = local.refinedBy(hit);
                String name = names.get(i);
                if (isResultWrite(i)) {
                    cleared = false;
                } else if (name.startsWith("clear.local")) {
                    assertTrue(!cleared, label + " clears the result local " + local
                            + " twice without a new write: " + names);
                    cleared = true;
                    released |= i < lastReturn;
                } else if (name.startsWith("c.LoadAndClearTempLocal")) {
                    assertTrue(!cleared, label + " loads the released result local "
                            + local + " without a new write: " + names);
                    cleared = true;
                    released |= i < lastReturn;
                } else if (name.startsWith("load.local")) {
                    released |= i < lastReturn;
                }
            }
            assertTrue(released, label + " leaves its result local " + local
                    + " neither consumed nor cleared before the return: " + names);
        }

        /*
         * Release checks limited to intervals the indexed operations identify
         * themselves; the listing as a whole is not one execution path, since
         * continuation compositions add synthetic branches and loops.
         *
         * Staged operands: the operand reads immediately before an indexed
         * operation are its own operands. Each one released there is released
         * once within that group, and is neither read nor released again in
         * the linear run after the operation.
         *
         * Results: an indexed result stored into a local and released by a
         * LoadAndClearTempLocal within the linear run after the operation is
         * neither read nor released again in the linear run after that
         * release. A release reached only across a branch is not checked
         * here. A clear.local by the owner of shared scratch is not charged.
         */
        void assertNoUseAfterRelease(String label) {
            for (int operation : positions(ProtosI115IndexedOperationTest::isIndexedOperation)) {
                List<LocalRef> releasedOperands = new java.util.ArrayList<>();
                for (int i = operation - 1; i >= 0 && isOperandRead(names.get(i)); i--) {
                    if (!names.get(i).startsWith("c.LoadAndClearTempLocal")) {
                        continue;
                    }
                    for (LocalRef local : localRefs(i)) {
                        assertTrue(releasedOperands.stream().noneMatch(local::sameAs),
                                label + " releases operand local " + local
                                        + " twice for one operation: " + names);
                        releasedOperands.add(local);
                    }
                }
                for (LocalRef local : releasedOperands) {
                    assertNotUsedInLinearRunAfter(operation, local, label);
                }

                LocalRef result = resultLocalRef(operation);
                if (result == null) {
                    continue;
                }
                for (int i = operation + 2; i < names.size() && !endsLinearRun(i - 1); i++) {
                    LocalRef hit = matching(i, result);
                    if (hit == null) {
                        continue;
                    }
                    if (names.get(i).startsWith("c.LoadAndClearTempLocal")) {
                        assertNotUsedInLinearRunAfter(i, result.refinedBy(hit), label);
                    }
                    if (!names.get(i).startsWith("load.local")) {
                        break;
                    }
                }
            }
        }

        private static boolean isOperandRead(String name) {
            return name.startsWith("load.") || name.startsWith("c.LoadAndClearTempLocal");
        }

        /*
         * The operand emitted immediately before the indexed operation: its
         * staged local and whether it is released (LoadAndClearTempLocal) or
         * only read (load.local) there. Null when it is not a staged local.
         */
        LocalRef stagedLastOperand(int operation, boolean released) {
            String operand = names.get(operation - 1);
            String expected = released ? "c.LoadAndClearTempLocal" : "load.local";
            if (!operand.startsWith(expected)) {
                return null;
            }
            List<LocalRef> refs = localRefs(operation - 1);
            return refs.isEmpty() ? null : refs.get(0);
        }

        /*
         * Whether local is released by a LoadAndClearTempLocal after position,
         * before any new write of the same local: a release following a new
         * write belongs to that later value, not to the one read at position.
         */
        boolean releasedAfter(int position, LocalRef local) {
            for (int i = position + 1; i < names.size(); i++) {
                LocalRef hit = matching(i, local);
                if (hit == null) {
                    continue;
                }
                if (names.get(i).startsWith("store.local")) {
                    return false;
                }
                if (names.get(i).startsWith("c.LoadAndClearTempLocal")) {
                    return true;
                }
            }
            return false;
        }

        long cleanupInsideComposition() {
            boolean[] inside = compositionMask();
            long cleanup = 0;
            for (int i = 0; i < names.size(); i++) {
                if (inside[i] && names.get(i).startsWith("clear.local")) {
                    cleanup++;
                }
            }
            return cleanup;
        }
    }

    /*
     * Operands composed by calls are staged in locals and released at their
     * last use: once each, never read again, and never before a reused RHS
     * is published as the assignment's value. The indexed operations keep
     * their own selection (no external selector), a redefined selection runs
     * once, a suspending one resumes without repeating any operand, and
     * results stay exact when several operations reuse the same scratch.
     */
    @Test
    void stagedOperandsAreReleasedAtTheirLastUse() throws Exception {
        String source = TRACE + """
                values: [1, 2, 3]
                box: {
                    at: (index) => {
                        trace.value = trace.value * 10 + 9
                        index + 100
                    }
                    atPut: (index, value) => {
                        trace.value = trace.value * 10 + 8
                        value * 2
                    }
                }
                reset: () => { trace.value = 0 }
                traced: () => { trace.value }
                readComposed: () => { step(1, values)[step(2, 1)] }
                writeConsumed: () => { (step(1, values)[step(2, 0)] = step(3, 7)) }
                writeDiscarded: () => {
                    step(1, values)[step(2, 1)] = step(3, 9)
                    0
                }
                putDistinct: () => { step(1, box).atPut(step(2, 0), step(3, 5)) }
                assignDistinct: () => { (step(1, box)[step(2, 0)] = step(3, 5)) }
                readSelected: () => { step(1, box)[step(2, 3)] }
                consumedByAnother: () => { (values[step(1, 0)] = step(2, 4)) + 1 }
                scratchReuse: () => {
                    values[step(1, 0)] = step(2, 10)
                    values[step(3, 1)] = step(4, 20)
                    values[step(5, 0)] + values[step(6, 1)]
                }
                fixed: [1, 2, 3]
                readViaCall: () => { fixed[step(1, 1)] }
                readTyped: () => { fixed[1] }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            assertTraced(session, "readComposed", 2L, 12L);
            assertTraced(session, "writeConsumed", 7L, 123L);
            assertTraced(session, "writeDiscarded", 0L, 123L);
            assertTraced(session, "putDistinct", 10L, 1238L);
            assertTraced(session, "assignDistinct", 5L, 1238L);
            assertTraced(session, "readSelected", 103L, 129L);
            assertTraced(session, "consumedByAnother", 5L, 12L);
            assertTraced(session, "scratchReuse", 30L, 123456L);

            for (String function : List.of(
                    "readComposed", "writeConsumed", "writeDiscarded", "putDistinct",
                    "assignDistinct", "readSelected", "consumedByAnother", "scratchReuse")) {
                IndexedCensus census = IndexedCensus.of(root(session, function));
                assertEquals(0L, census.count(name -> name.startsWith("c.IsStandardIndexedSelection")),
                        function + " keeps the external selector: " + census.names);
                census.assertNoUseAfterRelease(function);
                assertTrue(census.cleanupInsideComposition() == 0,
                        function + " clears a local inside a composition: " + census.names);
            }

            IndexedCensus consumed = IndexedCensus.of(root(session, "writeConsumed"));
            int consumedWrite = onlyOperation(consumed, "c.SetItem");
            LocalRef retained = consumed.stagedLastOperand(consumedWrite, false);
            assertTrue(retained != null,
                    "the reused RHS is released before the write publishes it: " + consumed.names);
            assertTrue(consumed.releasedAfter(consumedWrite, retained),
                    "the reused RHS is not released when published: " + consumed.names);

            IndexedCensus discarded = IndexedCensus.of(root(session, "writeDiscarded"));
            int discardedWrite = onlyOperation(discarded, "c.SetItem");
            assertTrue(discarded.stagedLastOperand(discardedWrite, true) != null,
                    "a discarded write keeps its RHS beyond the write: " + discarded.names);

            IndexedCensus put = IndexedCensus.of(root(session, "putDistinct"));
            int putOperation = onlyOperation(put, "c.SetItem");
            assertTrue(put.stagedLastOperand(putOperation, true) != null,
                    "an explicit atPut keeps its argument beyond the operation: " + put.names);

            for (int i = 0; i < 10; i++) {
                session.prepareTopLevel("reset").executable().execute();
                assertEquals(2L, session.prepareTopLevel("readViaCall")
                        .executable().execute().asLong());
            }
            // The index call (step) is a real invocation; the subscript itself
            // stays the fused operation, without external selection or a
            // permanent fallback.
            var guest = session.prepareTopLevel("readViaCall").executable().execute();
            assertTrue(guest.isNumber() && guest.fitsInLong() && guest.asLong() == 2L,
                    "readViaCall no longer answers the long 2: " + guest);
            List<String> viaCall = names(root(session, "readViaCall"));
            List<String> subscripts = viaCall.stream()
                    .filter(name -> name.startsWith("c.BinarySubscript")).toList();
            assertEquals(1, subscripts.size(), viaCall.toString());
            assertTrue(!subscripts.get(0).contains("Selected")
                            && !subscripts.get(0).contains("Fallback"),
                    "readViaCall degraded to the fallback: " + viaCall);
            assertEquals(0L, viaCall.stream()
                            .filter(name -> name.startsWith("c.IsStandardIndexedSelection")).count(),
                    "readViaCall keeps the external selector: " + viaCall);
            assertEquals(1L, viaCall.stream().filter(IndexedCensus::isExternalDispatch).count(),
                    "readViaCall dispatches beyond its index call: " + viaCall);

            // The subscript name test above also accepts the generic
            // c.BinarySubscript. Specialization is shown separately on a
            // standard read of a long-storage Array with a long index and no
            // call between the operands: the same site, executed repeatedly,
            // must reach its c.BinarySubscript$LongStorage variant. The
            // executions before warm-up may run the generic instruction.
            for (int i = 0; i < 20; i++) {
                assertEquals(2L, session.prepareTopLevel("readTyped")
                        .executable().execute().asLong());
            }
            List<String> typed = names(root(session, "readTyped"));
            List<String> typedSubscripts = typed.stream()
                    .filter(name -> name.startsWith("c.BinarySubscript")).toList();
            assertEquals(1, typedSubscripts.size(), typed.toString());
            assertTrue(typedSubscripts.get(0).startsWith("c.BinarySubscript$LongStorage"),
                    "readTyped did not quicken to its long-storage variant: " + typed);
            assertTrue(typed.stream().noneMatch(name -> name.startsWith("c.IsStandardIndexedSelection")),
                    "readTyped keeps the external selector: " + typed);
            assertTrue(typed.stream().noneMatch(IndexedCensus::isExternalDispatch),
                    "readTyped dispatches a message: " + typed);
        }
        // A suspending selected at with composed operands: 1, 2, then 9 once.
        assertEquals(12942L, evalLong(TRACE + """
                Lazy: Array {
                    at: (index) => {
                        trace.value = trace.value * 10 + 9
                        (() => { 40 + index }).future().value()
                    }
                }
                lazy: Lazy(1, 2, 3)
                readLazy: () => { step(1, lazy)[step(2, 2)] }
                r: (() => { readLazy() }).future().value()
                trace.value * 100 + r
                """));
    }

    private static void assertTraced(
            ProtosStandaloneHostedSession session, String function, long result, long trace)
            throws Exception {
        session.prepareTopLevel("reset").executable().execute();
        assertEquals(result, session.prepareTopLevel(function).executable().execute().asLong(),
                function + " result");
        assertEquals(trace, session.prepareTopLevel("traced").executable().execute().asLong(),
                function + " evaluation order and count");
    }

    private static int onlyOperation(IndexedCensus census, String prefix) {
        List<Integer> found = census.positions(name -> name.startsWith(prefix));
        assertEquals(1, found.size(), census.names.toString());
        return found.get(0);
    }

    private static ProtosBytecodeRootNode root(
            ProtosStandaloneHostedSession session, String function) throws Exception {
        return (ProtosBytecodeRootNode) session.prepareTopLevel(function)
                .hostExecutableForTesting().target.getRootNode();
    }

    @Test
    void standardAccessesConsumedAndDiscarded() {
        assertTrue(evalBoolean("""
                a: [1, 2, 3]
                k: 0
                a[k]
                a[1] = 20
                a.size()
                a.atPut(0, 10)
                x: a[1]
                y: (a[2] = 30)
                z: a.atPut(0, 11)
                (x == 20) && (y == 30) && (z == 11) && (a[0] == 11) && (a[2] == 30) && (a.size() == 3)
                """));
        assertTrue(signals("a: [1]\na[5]\nnull"));
        assertTrue(signals("a: [1]\na[5] = 2\nnull"));
        assertTrue(signals("a: [1]\na.freeze()\na.atPut(0, 2)\nnull"));
    }

    /**
     * Redefined {@code atPut} and {@code size} that suspend inside a Task run
     * once each: the resumption completes the selected invocation and never
     * repeats it. Indexed assignment still answers its RHS, the explicit send
     * answers the selected result.
     */
    @Test
    void suspendingSelectedAtPutAndSizeRunOnceAndResume() {
        assertEquals(12054207L, evalLong(TRACE + """
                Lazy: Array {
                    atPut: (index, value) => {
                        trace.value = trace.value + 1
                        (() => { 42 }).future().value()
                    }
                    size: () => {
                        trace.value = trace.value + 10
                        (() => { 7 }).future().value()
                    }
                }
                l: Lazy(1, 2, 3)
                write: () => { l[0] = 5 }
                put: () => { l.atPut(0, 5) }
                length: () => { l.size() }
                w: (() => { write() }).future().value()
                p: (() => { put() }).future().value()
                n: (() => { length() }).future().value()
                trace.value * 1000000 + w * 10000 + p * 100 + n
                """));
    }

    /**
     * Every indexed selector, over every Array storage representation and
     * over selected behaviors, stores its exact result through the
     * operation's own outcome local: no separate StoreAndTestContinuation
     * follows the operation, its answer is the guard of its composition,
     * and the composition (when present) reads and writes back that same
     * local. Results, including a suspended and resumed selection, an
     * Error from a selection and the RHS answer of indexed assignment,
     * stay exact, and each operand is evaluated once.
     */
    @Test
    void indexedProducersStoreTheirOwnResultsAcrossSelectorsAndStorage() throws Exception {
        String source = TRACE + """
                longs: [1, 2, 3]
                doubles: [1.5, 2.5]
                booleans: [true, false]
                generic: ["a", 2]
                Box: Array {
                    at: (index) => { index + 100 }
                    atPut: (index, value) => { 999 }
                    size: () => { 77 }
                }
                Failing: Array {
                    at: (index) => { Error().signal() }
                }
                box: Box(1, 2)
                failing: Failing(1)
                read: (a, i) => { a[i] }
                assign: (a, i, v) => { (a[i] = v) }
                put: (a, i, v) => { a.atPut(i, v) }
                length: (a) => { a.size() }
                nested: (a, b) => { a[b[0]] + a.size() * 10 }
                ordered: () => { step(1, longs)[step(2, longs[0])] + step(3, 10) }
                reset: () => { trace.value = 0 }
                traced: () => { trace.value }
                scratch: [0, 0]
                longRead: () => { read(longs, 1) }
                doubleRead: () => { read(doubles, 1) }
                booleanRead: () => { read(booleans, 1) }
                genericRead: () => { read(generic, 1) }
                longAssign: () => { assign(scratch, 0, 9) }
                boxPut: () => { put(box, 0, 7) }
                boxAssign: () => { assign(box, 0, 7) }
                longLength: () => { length(longs) }
                boxLength: () => { length(box) }
                boxRead: () => { read(box, 1) }
                nestedLongs: () => { nested(longs, [1]) }
                targets: [longs, box]
                alternate: () => {
                    total: 0
                    i: 0
                    while(i < 6) {
                        total = total + read(targets[i.mod(2)], 1)
                        i = i + 1
                    }
                    total
                }
                failed: () => {
                    Error.handle(() => { read(failing, 0); false }, (caught) => { true })
                }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            for (int round = 0; round < 3; round++) {
                assertEquals(2L, call(session, "longRead"));
                assertEquals(2.5, session.prepareTopLevel("doubleRead").executable().execute()
                        .asDouble());
                assertTrue(!session.prepareTopLevel("booleanRead").executable().execute()
                        .asBoolean());
                assertEquals(2L, call(session, "genericRead"));
                assertEquals(9L, call(session, "longAssign"));
                assertEquals(999L, call(session, "boxPut"));
                assertEquals(7L, call(session, "boxAssign"));
                assertEquals(3L, call(session, "longLength"));
                assertEquals(77L, call(session, "boxLength"));
                assertEquals(101L, call(session, "boxRead"));
                assertEquals(32L, call(session, "nestedLongs"));
                assertEquals(3L * 2 + 3L * 101, call(session, "alternate"));
                assertTrue(session.prepareTopLevel("failed").executable().execute().asBoolean());
                assertTraced(session, "ordered", 12L, 123L);
            }
            for (String function : List.of("read", "assign", "put", "length", "nested", "ordered")) {
                IndexedCensus census = IndexedCensus.of(root(session, function));
                assertEquals(0L, census.count(name -> name.startsWith("c.StoreAndTestContinuation")),
                        function + " stores an indexed result separately: " + census.names);
                List<Integer> operations = census.positions(
                        ProtosI115IndexedOperationTest::isIndexedOperation);
                assertTrue(!operations.isEmpty(), function + ": " + census.names);
                for (int operation : operations) {
                    assertTrue(census.ownsResult(operation),
                            function + " operation lacks its outcome local: " + census.names);
                    assertTrue(census.names.get(operation + 1).startsWith("branch.false"),
                            function + " operation answer is not its guard: " + census.names);
                    assertTrue(census.ownedCompositions(operation).size() <= 1,
                            function + " composes one operation twice: " + census.names);
                    if (operations.size() == 1) {
                        // As elsewhere, only a single-operation root is charged with
                        // the release of its result local; nested roots share scratch.
                        census.assertResultLocalReleased(operation, function);
                    }
                }
                census.assertNoUseAfterRelease(function);
                // Only the three explicit step(...) calls of ordered are sends.
                assertEquals(function.equals("ordered") ? 3L : 0L,
                        census.count(IndexedCensus::isExternalDispatch),
                        function + " dispatches its standard path externally: " + census.names);
            }
        }
        // Suspended at, atPut and size each run once, after their operands,
        // which are evaluated once across the resumption.
        assertTrue(evalBoolean(TRACE + """
                Lazy: Array {
                    at: (index) => {
                        trace.value = trace.value * 10 + 4
                        (() => { index + 40 }).future().value()
                    }
                    atPut: (index, value) => {
                        trace.value = trace.value * 10 + 5
                        (() => { 42 }).future().value()
                    }
                    size: () => {
                        trace.value = trace.value * 10 + 6
                        (() => { 7 }).future().value()
                    }
                }
                l: Lazy(1, 2, 3)
                r: (() => { step(1, l)[step(2, 1)] }).future().value()
                p: (() => { step(3, l).atPut(step(4, 0), step(5, 9)) }).future().value()
                a: (() => { (step(6, l)[step(7, 0)] = step(8, 6)) }).future().value()
                n: (() => { step(9, l).size() }).future().value()
                (trace.value == 1243455678596) && (r == 41) && (p == 42) && (a == 6) && (n == 7)
                """));
        // Inside one Task, a binding captured by read keeps its prior value until
        // each suspended indexed selection completes; the completed result is then
        // visible through read, a suspended selected Error publishes nothing, and
        // every receiver/index/RHS runs once before its selection.
        assertTrue(evalBoolean(TRACE + """
                Lazy: Array {
                    at: (index) => {
                        trace.value = trace.value * 10 + 4
                        (() => { index + 40 }).future().value()
                    }
                    atPut: (index, value) => {
                        trace.value = trace.value * 10 + 5
                        (() => { 42 }).future().value()
                    }
                    size: () => {
                        trace.value = trace.value * 10 + 6
                        (() => { 7 }).future().value()
                    }
                }
                Bad: Array {
                    atPut: (index, value) => {
                        (() => { 0 }).future().value()
                        Error().signal()
                    }
                }
                l: Lazy(1, 2, 3)
                bad: Bad(1)
                task: () => {
                    got: 0
                    read: () => got
                    before: read()
                    got = step(1, l)[step(2, 1)]
                    afterAt: read()
                    got = (step(3, l)[step(5, 0)] = step(6, 8))
                    afterAssign: read()
                    put: step(7, l).atPut(0, 9)
                    got = step(8, l).size()
                    afterSize: read()
                    Error.handle(() => { got = (bad[0] = 3) }, (caught) => { 0 })
                    afterError: read()
                    early: (before == 0) && (afterAt == 41) && (afterAssign == 8)
                    late: (put == 42) && (afterSize == 7) && (afterError == 7) && (bad[0] == 1)
                    early && late
                }
                ok: (() => { task() }).future().value()
                ok && (trace.value == 12435657586)
                """));
        // A selected Error publishes no assignment or size result.
        assertTrue(evalBoolean("""
                Bad: Array {
                    atPut: (index, value) => { Error().signal() }
                    size: () => { Error().signal() }
                }
                b: Bad(1)
                r: 0
                Error.handle(() => { r = (b[0] = 5) }, (caught) => { 0 })
                n: 0
                Error.handle(() => { n = b.size() }, (caught) => { 0 })
                (r == 0) && (n == 0) && (b[0] == 1)
                """));
        // One site follows a mutated selection and then the standard one.
        assertTrue(evalBoolean("""
                Flip: Array { at: (index) => { index + 100 } }
                x: Flip(1, 2)
                read: (a, i) => { a[i] }
                first: read(x, 0)
                Flip.at = (index) => { index + 200 }
                second: read(x, 0)
                standard: read([5, 6], 1)
                (first == 100) && (second == 200) && (standard == 6)
                """));
        // Standard Bytes indexing and size through the same producers.
        assertTrue(evalBoolean(BYTES + """
                read: (r, i) => { r[i] }
                length: (r) => { r.size() }
                (read(source, 0) == 106) && (length(source) == source.size())
                """));
        // Suspended selections resume into the producer's own outcome local
        // without repeating the selection; indexed assignment answers its RHS.
        assertEquals(41004206L, evalLong("""
                Lazy: Array {
                    at: (index) => { (() => { index + 40 }).future().value() }
                    atPut: (index, value) => { (() => { 42 }).future().value() }
                }
                l: Lazy(1, 2, 3)
                read: () => { l[1] }
                put: () => { l.atPut(0, 5) }
                assign: () => { l[0] = 6 }
                r: (() => { read() }).future().value()
                p: (() => { put() }).future().value()
                a: (() => { assign() }).future().value()
                r * 1000000 + p * 100 + a
                """));
    }

    private static long call(ProtosStandaloneHostedSession session, String function)
            throws Exception {
        return session.prepareTopLevel(function).executable().execute().asLong();
    }

    @Test
    void errorsSignaledByRedefinedAtPutAndSizeReachTheHandler() {
        assertTrue(signals("Box: Array { atPut: (index, value) => { Error().signal() } }\n"
                + "b: Box(1)\nb[0] = 2"));
        assertTrue(signals("Box: Array { atPut: (index, value) => { Error().signal() } }\n"
                + "b: Box(1)\nb.atPut(0, 2)"));
        assertTrue(signals("Box: Array { size: () => { Error().signal() } }\n"
                + "b: Box(1)\nb.size()"));
        assertEquals(43L, evalLong("f: () => {\n"
                + "    Box: Array { atPut: (index, value) => { ^43 } }\n"
                + "    b: Box(1)\n    b[0] = 1\n    0\n}\nf()"));
        assertEquals(44L, evalLong("f: () => {\n"
                + "    Box: Array { size: () => { ^44 } }\n"
                + "    b: Box(1)\n    b.size()\n    0\n}\nf()"));
    }

    @Test
    void indexedAssignmentWhoseRhsIsReusedBindsItsOperands() throws Exception {
        String source = """
                reuse: (a, i, v) => { (a[i] = v) }
                discard: (a, i, v) => { a[i] = v; 0 }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            ProtosBytecodeRootNode reuse = (ProtosBytecodeRootNode)
                    session.prepareTopLevel("reuse").hostExecutableForTesting()
                            .target.getRootNode();
            ProtosBytecodeRootNode discard = (ProtosBytecodeRootNode)
                    session.prepareTopLevel("discard").hostExecutableForTesting()
                            .target.getRootNode();
            assertTrue(names(reuse).stream().anyMatch(name -> name.startsWith("c.SetItem")),
                    names(reuse).toString());
            long reuseBinds = names(reuse).stream()
                    .filter(name -> name.contains("stackvalue") || name.startsWith("c.BindStackValue"))
                    .count();
            long discardBinds = names(discard).stream()
                    .filter(name -> name.contains("stackvalue") || name.startsWith("c.BindStackValue"))
                    .count();
            assertTrue(reuseBinds > discardBinds,
                    "reused RHS is not bound: " + names(reuse) + " / " + names(discard));
        }
        assertEquals(7L, evalLong("box: { atPut: (index, value) => { 999 } }\n(box[0] = 7)"));
        assertEquals(999L, evalLong("box: { atPut: (index, value) => { 999 } }\nbox.atPut(0, 7)"));
    }

    @Test
    void warmedIndexedSiteQuickensPrimitiveStorageReads() throws Exception {
        String source = """
                longs: [1, 2, 3]
                doubles: [1.5, 2.5]
                booleans: [true, false]
                readLong: () => { longs[1] }
                readDouble: () => { doubles[1] }
                readBoolean: () => { booleans[1] }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            for (int i = 0; i < 20; i++) {
                assertEquals(2L, session.prepareTopLevel("readLong").executable().execute().asLong());
                assertEquals(2.5, session.prepareTopLevel("readDouble").executable().execute().asDouble());
                assertTrue(!session.prepareTopLevel("readBoolean").executable().execute().asBoolean());
            }
            assertQuickenedSubscript(session, "readLong", "LongStorage");
            assertQuickenedSubscript(session, "readDouble", "DoubleStorage");
            ProtosBytecodeRootNode booleans = (ProtosBytecodeRootNode)
                    session.prepareTopLevel("readBoolean").hostExecutableForTesting()
                            .target.getRootNode();
            assertTrue(names(booleans).stream().anyMatch(name -> name.startsWith("c.BinarySubscript")),
                    names(booleans).toString());
        }
    }

    @Test
    void sameSiteStandardRedefinedStandardKeepsTheStandardSpecialization()
            throws Exception {
        String source = """
                values: [1, 2, 3]
                read: () => { values[1] }
                redefine: () => { values.at: (index) => { 50 } }
                restore: () => { values.removeSlot("at") }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            for (int i = 0; i < 10; i++) {
                assertEquals(2L, session.prepareTopLevel("read").executable().execute().asLong());
            }
            session.prepareTopLevel("redefine").executable().execute();
            for (int i = 0; i < 3; i++) {
                assertEquals(50L, session.prepareTopLevel("read").executable().execute().asLong());
            }
            session.prepareTopLevel("restore").executable().execute();
            for (int i = 0; i < 10; i++) {
                assertEquals(2L, session.prepareTopLevel("read").executable().execute().asLong());
            }
            assertLongTypedSubscript(session, "read", 2L);
        }
    }

    /*
     * A site that alternated between the standard and a redefined selection
     * (the caller checks the standard, redefined and restored results) still
     * answers the exact long element through one fused operation, with no
     * external selector and no external dispatch. The operation's instruction
     * name is not read as evidence of an internal specialization, and typed
     * transport locals are not evidence that a particular specialization ran;
     * published quickened names are covered by
     * warmedIndexedSiteQuickensPrimitiveStorageReads().
     */
    private static void assertLongTypedSubscript(
            ProtosStandaloneHostedSession session, String function, long expected)
            throws Exception {
        var guest = session.prepareTopLevel(function).executable().execute();
        assertTrue(guest.isNumber() && guest.fitsInLong(),
                function + " no longer answers a long: " + guest);
        assertEquals(expected, guest.asLong(), function + " standard result");
        List<String> names = names(root(session, function));
        List<String> subscripts = names.stream()
                .filter(name -> name.startsWith("c.BinarySubscript")).toList();
        assertEquals(1, subscripts.size(), names.toString());
        String subscript = subscripts.get(0);
        assertTrue(!subscript.contains("Selected") && !subscript.contains("Fallback"),
                function + " degraded permanently to the fallback: " + names);
        assertTrue(names.stream().noneMatch(name -> name.startsWith("c.IsStandardIndexedSelection")),
                function + " keeps the external selector: " + names);
        assertTrue(names.stream().noneMatch(IndexedCensus::isExternalDispatch),
                function + " keeps external dispatch: " + names);
    }

    private static void assertQuickenedSubscript(
            ProtosStandaloneHostedSession session, String function, String storage)
            throws Exception {
        ProtosBytecodeRootNode root = (ProtosBytecodeRootNode)
                session.prepareTopLevel(function).hostExecutableForTesting()
                        .target.getRootNode();
        List<String> names = names(root);
        assertTrue(names.stream().anyMatch(name -> name.startsWith("c.BinarySubscript$")
                        && name.contains(storage)),
                function + " did not quicken to " + storage + ": " + names);
    }

    @Test
    void standardLongArrayReadsAndWritesAcrossALoop() {
        assertTrue(evalBoolean("""
                a: [1, 2, 3, 4, 5]
                i: 0
                last: 0
                while(i < 5) {
                    last = (a[i] = a[i] * 10 + i)
                    i = i + 1
                }
                (a[0] == 10) && (a[1] == 21) &&
                    (a[2] == 32) && (a[3] == 43) &&
                    (a[4] == 54) && (a.size() == 5) &&
                    (last == 54) && (a[4].parent() === SmallInteger)
                """));
    }

    @Test
    void constantLongArraySortedInNestedLoopsIsFreshEachRun() {
        assertTrue(evalBoolean("""
                sort: () => {
                    a: [5, 3, 9, 1, 7, 2, 8, 4, 6, 0]
                    n: a.size()
                    i: 0
                    while(i < n) {
                        j: 0
                        while(j < n - 1 - i) {
                            if(a[j] > a[j + 1]) {
                                t: a[j]
                                a[j] = a[j + 1]
                                a[j + 1] = t
                            }
                            j = j + 1
                        }
                        i = i + 1
                    }
                    a
                }
                ok: true
                previous: null
                k: 0
                while(k < 5) {
                    sorted: sort()
                    m: 0
                    while(m < 10) {
                        ok = ok && (sorted[m] == m)
                        m = m + 1
                    }
                    ok = ok && (sorted !== previous)
                    previous = sorted
                    k = k + 1
                }
                ok
                """));
    }

    @Test
    void longStorageArrayErrorsFrozenWritesAndAtPutResult() {
        assertTrue(signals("""
                a: [1, 2, 3]
                a["0"]
                """));

        assertTrue(signals("""
                a: [1, 2, 3]
                a[0 - 1]
                """));

        assertTrue(signals("""
                a: [1, 2, 3]
                a[3]
                """));

        assertTrue(signals("""
                a: [1, 2, 3]
                a[1.0]
                """));

        assertTrue(signals("""
                a: [1, 2, 3]
                a.freeze()
                a[0] = 4
                """));

        assertTrue(signals("""
                a: [1, 2, 3]
                a.freeze()
                a[9] = 4
                """));
        assertTrue(evalBoolean("""
                a: [1, 2, 3]
                value: { tag: 1 }
                same: a.atPut(0, value) === value
                stored: a[0] === value
                b: [4, 5]
                c: (b[1] = 2.5) == 2.5
                same && stored && c && (b[1] == 2.5) && (b[0] == 4)
                """));
    }

    @Test
    void sameSiteAlternatesStandardAndCustomIndexedSelection() {
        assertTrue(evalBoolean("""
                Custom: Array { at: (index) => { 100 } }
                read: (r) => { r[1] }
                ok: true
                i: 0
                while(i < 12) {
                    plain: [1, 2, 3]
                    custom: Custom(1, 2, 3)
                    ok = ok && (read(plain) == 2) && (read(custom) == 100)
                    i = i + 1
                }
                Custom.at = (index) => { 200 }
                ok = ok && (read(Custom(1, 2)) == 200) && (read([7, 8]) == 8)
                Custom.removeSlot("at")
                ok && (read(Custom(1, 2)) == 2)
                """));
    }

    @Test
    void overridingReceiversDoNotDisableTheStandardIndexedPath() {
        assertTrue(evalBoolean("""
                Special: Array {
                    at: (index) => { 42 }
                    atPut: (index, value) => { 0 }
                    size: () => { 99 }
                }
                s: Special(1, 2, 3)
                read: (r, index) => { r[index] }
                write: (r, index, value) => { r[index] = value }
                count: (r) => { r.size() }
                ok: true
                i: 0
                while(i < 20) {
                    plain: [1, 2, 3]
                    other: [7, 8, 9]
                    ok = ok && (read(plain, 1) == 2)
                    ok = ok && (read(s, 1) == 42)
                    ok = ok && (read(other, 2) == 9)
                    ok = ok && (write(s, 0, 5) == 5) && (s[0] == 42)
                    ok = ok && (write(other, 0, 70) == 70) && (other[0] == 70)
                    ok = ok && (count(s) == 99)
                    ok = ok && (count(other) == 3)
                    i = i + 1
                }
                ok
                """));
    }

    @Test
    void reassignedSelectedBehaviorIsObservedWithoutShapeChange() {
        assertTrue(evalBoolean("""
                Box: Array { at: (index) => { 1 } }
                b: Box(5, 6)
                read: (r) => { r[0] }
                first: 0
                i: 0
                while(i < 10) {
                    first = read(b)
                    i = i + 1
                }
                Box.at = (index) => { 2 }
                (first == 1) && (read(b) == 2)
                """));
    }

    @Test
    void indexedErrorsAndFrozenPrecedence() {
        assertTrue(signals("a: [1, 2, 3]\na[0 - 1]"));
        assertTrue(signals("a: [1, 2, 3]\na[3]"));
        assertTrue(signals("a: [1, 2, 3]\na[9223372036854775807]"));
        assertTrue(signals("a: [1, 2, 3]\na[0 - 9223372036854775807 - 1]"));
        assertTrue(signals("a: [1, 2, 3]\na[\"x\"]"));
        assertTrue(signals("a: [1, 2, 3]\na.freeze()\na[1] = 1"));
        assertTrue(signals("a: [1, 2, 3]\na.freeze()\na[0 - 1] = 1"));
        assertEquals(1L, evalLong("a: [1, 2, 3]\na.freeze()\n"
                + "Error.handle(() => { a[0 - 1] = 9 }, (caught) => null)\na[0]"));
        // A closed Array still replaces an existing element.
        assertEquals(9L, evalLong("a: [1, 2, 3]\na.close()\na[0] = 9\na[0]"));
        assertTrue(signals("a: [1, 2, 3]\na.close()\na[3] = 9"));
    }

    @Test
    void failingIndexedReadEvaluatesReceiverAndIndexOnceInOrder() {
        assertEquals(12L, evalLong(TRACE
                + "a: [1, 2]\n"
                + "Error.handle(() => { step(1, a)[step(2, 5)] }, (caught) => null)\n"
                + "trace.value"));
        assertEquals(123L, evalLong(TRACE
                + "a: [1, 2]\na.freeze()\n"
                + "Error.handle(() => {\n"
                + "    step(1, a)[step(2, 0 - 1)] = step(3, 7)\n"
                + "}, (caught) => null)\ntrace.value"));
    }

    @Test
    void customIndexedBehaviorKeepsReceiverBindingAndResultIdentity()
            throws Exception {
        String source = """
                marker: {}
                Box: Array {
                    at: (index) => { (this.size() == 2) && (index == 1) }
                    atPut: (index, value) => { marker }
                }
                run: () => {
                    b: Box(5, 6)
                    v: {}
                    written: (b[0] = v)
                    plain: [1, 2]
                    plain[1] = 3
                    b[1] && (written === v) &&
                        (b.atPut(0, v) === marker) &&
                        (plain[1] == 3)
                }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            ProtosBytecodeRootNode run = (ProtosBytecodeRootNode)
                    session.prepareTopLevel("run").hostExecutableForTesting()
                            .target.getRootNode();
            List<String> names = names(run);
            for (String operation : List.of("c.SetItem", "c.BinarySubscript")) {
                assertTrue(names.stream().anyMatch(name -> name.startsWith(operation)),
                        "missing " + operation + " in " + names);
            }
            assertTrue(names.stream().noneMatch(name -> name.startsWith("c.IsStandardIndexedSelection")),
                    "unexpected external selector in " + names);
            for (int i = 0; i < 5; i++) {
                assertTrue(session.prepareTopLevel("run")
                        .executable().execute().asBoolean());
            }
        }
        assertTrue(signals("Box: Array { at: (index) => { Error().signal() } }\n"
                + "b: Box(1)\nb[0]"));
        assertEquals(42L, evalLong("f: () => {\n"
                + "    Box: Array { at: (index) => { ^42 } }\n"
                + "    b: Box(1)\n    b[0]\n    0\n}\nf()"));
    }

    @Test
    void repeatedIndexedErrorsInNestedClosuresReachTheEnclosingHandler() {
        assertEquals(30L, evalLong("""
                a: [1, 2, 3]
                read: (r, index) => { r[index] }
                write: (r, index, value) => { r[index] = value }
                caught: 0
                i: 0
                while(i < 10) {
                    Error.handle(() => { read(a, 3) }, (e) => { caught = caught + 1 })
                    Error.handle(() => { read(a, "x") }, (e) => { caught = caught + 1 })
                    Error.handle(() => { write(a, 0 - 1, 4) }, (e) => { caught = caught + 1 })
                    i = i + 1
                }
                caught
                """));
    }

    @Test
    void inheritedOverrideTwoLevelsUpIsSelectedAndItsRemovalRestoresStandard() {
        assertTrue(evalBoolean("""
                Base: Array { at: (index) => { 77 } }
                Derived: Base {}
                read: (r) => { r[0] }
                ok: true
                i: 0
                while(i < 12) {
                    ok = ok && (read(Derived(1, 2)) == 77) && (read([5, 6]) == 5)
                    i = i + 1
                }
                Base.removeSlot("at")
                ok && (read(Derived(1, 2)) == 1)
                """));
    }

    /**
     * Operands that stay on the operand stack (bindings and a direct numeric
     * index): one read site and one write site alternate the standard Array
     * behavior, a redefined {@code at}/{@code atPut} and the restored
     * standard behavior; the explicit {@code atPut} keeps the selected
     * result and the assignment syntax yields its RHS.
     */
    @Test
    void stackOperandSitesAlternateStandardRedefinedAndRestoredSelection() {
        String sites = """
                Custom: Array {}
                read: (r, k) => { r[k + 1] }
                write: (r, k, v) => { r[k + 1] = v }
                put: (r, k, v) => { r.atPut(k + 1, v) }
                ok: true
                c: Custom(1, 2, 3)
                i: 0
                while(i < 12) {
                    c = Custom(1, 2, 3)
                    ok = ok && (read(c, 0) == 2) && (write(c, 0, 5) == 5) && (c[1] == 5)
                    ok = ok && (put(c, 1, 6) == 6) && (c[2] == 6)
                    i = i + 1
                }
                """;
        String redefine = """
                Custom.at: (index) => { 100 }
                Custom.atPut: (index, value) => { 0 }
                """;
        String redefinedChecks =
                "ok = ok && (read(c, 0) == 100) && (write(c, 0, 7) == 7) && (put(c, 0, 7) == 0)\n";
        String restore = """
                Custom.removeSlot("at")
                Custom.removeSlot("atPut")
                """;
        assertTrue(evalBoolean(sites + "ok"),
                "standard phase: reads, writes and atPut over twelve iterations");
        assertEquals(100L, evalLong(sites + redefine + "read(c, 0)"),
                "redefined at is selected at the read site");
        assertEquals(7L, evalLong(sites + redefine + "write(c, 0, 7)"),
                "indexed assignment yields its RHS under a redefined atPut");
        assertEquals(0L, evalLong(sites + redefine + "put(c, 0, 7)"),
                "explicit atPut yields the redefined method's result");
        assertTrue(evalBoolean(sites + redefine + redefinedChecks + "ok"),
                "redefined phase");
        assertEquals(5L, evalLong(sites + redefine + redefinedChecks + restore + "read(c, 0)"),
                "restored at reads the standard element");
        assertEquals(8L, evalLong(sites + redefine + redefinedChecks + restore
                        + "write(c, 0, 8)\nc[1]"),
                "restored atPut writes the standard element");
        assertEquals(9L, evalLong(sites + redefine + redefinedChecks + restore + "put(c, 1, 9)"),
                "restored explicit atPut yields the standard RHS");
        assertTrue(evalBoolean(sites + redefine + redefinedChecks + restore
                + "ok = ok && (read(c, 0) == 5) && (write(c, 0, 8) == 8) && (c[1] == 8)\n"
                + "ok && (put(c, 1, 9) == 9) && (c[2] == 9)"),
                "complete alternation over the same send sites");
    }

    /**
     * A selected {@code at} reached with stack operands runs once per read,
     * including when it suspends inside a Task; the read result is the
     * selected method's result after its resumption.
     */
    @Test
    void suspendingSelectedAtWithStackOperandsRunsOnce() {
        assertEquals(1042L, evalLong(TRACE + """
                Lazy: Array {
                    at: (index) => {
                        trace.value = trace.value + 1
                        (() => { 42 }).future().value()
                    }
                }
                l: Lazy(1, 2, 3)
                k: 0
                read: () => { l[k + 1] }
                result: (() => { read() }).future().value()
                trace.value * 1000 + result
                """));
    }

    /*
     * An indexed result consumed by an addition whose sum is written into a
     * captured binding, which a second Closure reads: the binding observed
     * through that Closure is exactly the completed sum, across primitive
     * long and double storage, large Integer promotion, an object indexed
     * value under a selected addition, and a Closure-held fallback callback
     * that writes the same binding after the loop.
     */
    @Test
    void indexedSumStoredIntoCapturedBindingAcrossCarriers() {
        String loop = """
                run: () => {
                    values: %s
                    total: %s
                    read: () => total
                    for(i, 0, values.size()) {
                        total = total + values[i]
                    }
                    %s
                }
                run()
                """;
        assertEquals(10010L, evalLong(loop.formatted(
                "[1, 2, 3, 4]", "0", "read() * 1000 + total")));
        assertTrue(evalBoolean(loop.formatted(
                "[1.5, 2.5]", "0.0", "(read() == 4.0) && (total == 4.0)")));
        assertTrue(evalBoolean(loop.formatted(
                "[9223372036854775807, 1]", "0",
                "read() == (9223372036854775807 + 1)")));
        assertTrue(evalBoolean(loop.formatted(
                "[(0 - 9223372036854775807), (0 - 1), (0 - 1)]", "0",
                "read() == ((0 - 9223372036854775807) - 2)")));
        // Object indexed values reach a selected addition, which answers itself.
        assertEquals(13L, evalLong("""
                seen: { value: 0 }
                plusSource: { add: (other) => {
                    seen.value = seen.value * 10 + other.n
                    customPlus
                } }
                customPlus: plusSource.alias("add", "+")
                run: () => {
                    values: [{ n: 1 }, { n: 3 }]
                    total: customPlus
                    read: () => total
                    for(i, 0, values.size()) {
                        total = total + values[i]
                    }
                    read() === customPlus
                }
                run()
                seen.value
                """));
        // The retained fallback callback shares the binding with read.
        assertEquals(7L, evalLong("""
                run: () => {
                    values: [1, 2, 3]
                    total: 0
                    read: () => total
                    step: (i) => { total = total + values[i] }
                    Range(0, 3).each(step)
                    step(0)
                    read()
                }
                run()
                """));
    }

    /*
     * Selection changes at the same sites of the captured indexed sum:
     * a selected addition answering an Integer hands the binding to the
     * standard path (custom to standard), a nonstandard selected at is
     * invoked once per element, and an override installed between two
     * invocations is observed by the second while the first stays
     * standard (standard to custom).
     */
    @Test
    void capturedIndexedSumObservesSelectedAtAndArithmetic() {
        assertEquals(46L, evalLong("""
                plusSource: { add: (other) => { 41 } }
                customPlus: plusSource.alias("add", "+")
                run: () => {
                    values: [1, 2, 3]
                    total: customPlus
                    read: () => total
                    for(i, 0, values.size()) {
                        total = total + values[i]
                    }
                    read()
                }
                run()
                """));
        assertEquals(60300L, evalLong("""
                Custom: Array {}
                c: Custom(1, 2, 3)
                run: () => {
                    total: 0
                    read: () => total
                    for(i, 0, 3) {
                        total = total + c[i]
                    }
                    read()
                }
                first: run()
                Custom.at: (index) => { 100 }
                first * 10000 + run()
                """));
        assertTrue(evalBoolean("""
                Custom: Array {}
                standardAt: Custom.at
                c: Custom(7)
                run: () => {
                    total: 0
                    read: () => total
                    for(i, 0, 1) { total = total + c[i] }
                    read()
                }
                a: run()
                Custom.at: (index) => { 41 }
                b: run()
                Custom.at = standardAt
                d: run()
                (a == 7) && (b == 41) && (d == 7)
                """));
        assertTrue(evalBoolean("""
                source: { add: (other) => { 41 } }
                selected: source.alias("add", "+")
                values: [7]
                run: (seed) => {
                    total: seed
                    read: () => total
                    for(i, 0, 1) { total = total + values[i] }
                    read()
                }
                (run(0) == 7) && (run(selected) == 41) && (run(0) == 7)
                """));
        assertEquals(3042L, evalLong(TRACE + """
                Counted: Array {
                    at: (index) => {
                        trace.value = trace.value + 1
                        14
                    }
                }
                c: Counted(1, 2, 3)
                run: () => {
                    total: 0
                    read: () => total
                    for(i, 0, 3) {
                        total = total + c[i]
                    }
                    read()
                }
                r: run()
                trace.value * 1000 + r
                """));
    }

    /*
     * Suspension inside the captured indexed sum: a selected at that
     * suspends after the indexed operands are evaluated and before the
     * addition, and a selected addition that suspends before its result
     * is complete, after the indexed access. Each selected method runs once
     * per element, and the binding observed through another Closure while
     * the addition is suspended is still the previous value: no
     * intermediate result reaches the cell before the addition completes.
     */
    @Test
    void suspendedSelectionsInCapturedIndexedSumResumeOnce() {
        assertEquals(3030L, evalLong(TRACE + """
                Lazy: Array {
                    at: (index) => {
                        trace.value = trace.value + 1
                        (() => { 10 }).future().value()
                    }
                }
                l: Lazy(1, 2, 3)
                run: () => {
                    total: 0
                    read: () => total
                    for(i, 0, 3) {
                        total = total + l[i]
                    }
                    read()
                }
                result: (() => { run() }).future().value()
                trace.value * 1000 + result
                """));
        assertEquals(11012L, evalLong(TRACE + """
                holder: { read: 0 }
                seen: { value: 0 }
                plusSource: { add: (other) => {
                    trace.value = trace.value + 1
                    if(holder.read() === customPlus) { seen.value = 10 }
                    (() => { 7 }).future().value()
                } }
                customPlus: plusSource.alias("add", "+")
                run: () => {
                    values: [1, 2, 3]
                    total: customPlus
                    holder.read = () => total
                    for(i, 0, values.size()) {
                        total = total + values[i]
                    }
                    holder.read()
                }
                result: (() => { run() }).future().value()
                (trace.value + seen.value) * 1000 + result
                """));
    }

    @Test
    void capturedSumKeepsTheLeftOperandWhenSelectedAtMutatesItsCell() {
        for (boolean suspend : List.of(false, true)) {
            assertTrue(evalBoolean("""
                    state: { visits: 0 }
                    run: () => {
                        total: 5
                        read: () => total
                        Mutating: Array { at: (index) => {
                            state.visits = state.visits + 1
                            total = 100
                            %s
                        } }
                        values: Mutating(1)
                        for(i, 0, 1) {
                            total = total + values[i]
                        }
                        (read() == 12) && (state.visits == 1)
                    }
                    (() => { run() }).future().value()
                    """.formatted(suspend ? "(() => { 7 }).future().value()" : "7")));
        }
    }

    @Test
    void capturedNumericProducerOwnsCommitAndOnlyDefersItForSuspension() throws Exception {
        String source = """
                values: [7]
                run: () => {
                    total: 5
                    read: () => total
                    for(i, 0, 1) { total = total + values[i] }
                    read()
                }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            assertEquals(12L, call(session, "run"));
            ProtosBytecodeRootNode run = root(session, "run");
            run.getRootNodes().ensureSourceInformation();
            ProtosPerf045RangeEachInlineTest.assertProducerCommitsCapturedCell(
                    run.getBytecodeNode().getInstructionsAsList(), "NumberAdd");
            IndexedCensus census = IndexedCensus.of(run);
            int indexed = onlyOperation(census, "c.BinarySubscript");
            int numeric = onlyOperation(census, "c.NumberAdd");
            census.assertDirectlyConsumed(indexed, numeric, "captured indexed sum");
            assertEquals(1, census.ownedCompositions(numeric).size(),
                    "the numeric producer retains exactly one continuation composition");
        }
    }

    /*
     * Abrupt completion of the captured indexed sum: an indexed Error and a
     * selected addition's Error leave the binding at its last completed
     * value, and a non-local return after a write answers that write.
     */
    @Test
    void abruptCompletionLeavesCapturedSumAtLastCompletedWrite() {
        assertEquals(3L, evalLong("""
                run: () => {
                    values: [1, 2]
                    total: 0
                    read: () => total
                    Error.handle(() => {
                        for(i, 0, 3) {
                            total = total + values[i]
                        }
                    }, (caught) => 0)
                    read()
                }
                run()
                """));
        assertTrue(evalBoolean("""
                failSource: { add: (other) => { Error().signal() } }
                customFail: failSource.alias("add", "+")
                run: () => {
                    values: [1, 2]
                    total: customFail
                    read: () => total
                    Error.handle(() => {
                        for(i, 0, values.size()) {
                            total = total + values[i]
                        }
                    }, (caught) => 0)
                    read() === customFail
                }
                run()
                """));
        assertEquals(6L, evalLong("""
                run: () => {
                    values: [1, 2, 3, 4]
                    total: 0
                    read: () => total
                    for(i, 0, values.size()) {
                        total = total + values[i]
                        if(i == 2) { ^read() }
                    }
                    0
                }
                run()
                """));
    }

    /*
     * A captured receiver (a cell shared with keep) indexed by a callback
     * formal (viaFormal), a plain LOCAL (viaLocal) or a while-loop LOCAL
     * (sumLocal) uses the cell/local indexed form, which reads both bindings
     * itself. Its results equal the ordinary at protocol over every Array
     * storage and Bytes; out-of-range and invalid indexes signal the
     * standard Error; an exact large Integer index reaches a selected at
     * unchanged; an override installed between invocations is selected by
     * the warmed site; a suspending selected at runs once per access and
     * resumes with its own index; an Error signaled by a selected at runs
     * the selection once and reaches the handler.
     */
    private static final String CELL_LOCAL_SITES = TRACE + """
            longs: [10, 20, 30]
            doubles: [1.5, 2.5]
            booleans: [true, false]
            objects: ["a", { n: 7 }]
            bytes: Encoding.UTF8.encode("jobs")
            big: 9223372036854775807 + 1000
            Custom: Array { at: (index) => { step(1, index - 9223372036854775807) } }
            custom: Custom(1, 2, 3)
            shared: [1, 2, 3]
            override: () => { shared.at: (index) => { step(2, index + 100) } }
            Lazy: Array { at: (index) => { step(3, (() => { index * 10 }).future().value()) } }
            lazy: Lazy(0, 0, 0)
            Failing: Array { at: (index) => { step(4, index); Error().signal() } }
            failing: Failing(1)
            viaFormal: (source, k) => {
                values: source
                keep: () => values
                result: null
                Array(k).each((element) => { result = values[element] })
                result
            }
            viaLocal: (source, k) => {
                values: source
                keep: () => values
                index: k
                values[index]
            }
            sumLocal: (source) => {
                values: source
                keep: () => values
                total: 0
                i: 0
                while(i < 3) {
                    total = total * 100 + values[i]
                    i = i + 1
                }
                total
            }
            fails: (source, k) => {
                Error.handle(() => { viaFormal(source, k); false }, (caught) => true) && Error.handle(() => { viaLocal(source, k); false }, (caught) => true)
            }
            standard: () => {
                ok: (viaFormal(longs, 1) == 20) && (viaLocal(longs, 2) == 30)
                ok = ok && (viaFormal(doubles, 1) == 2.5) && (viaLocal(doubles, 0) == 1.5)
                ok = ok && (viaFormal(booleans, 1) == false) && (viaLocal(booleans, 0) == true)
                ok = ok && (viaFormal(objects, 0) == "a") && (viaLocal(objects, 1).n == 7)
                ok = ok && (viaFormal(bytes, 0) == 106) && (viaLocal(bytes, 3) == 115)
                ok = ok && (sumLocal(longs) == 102030) && (viaFormal(shared, 1) == 2)
                ok = ok && fails(longs, 3) && fails(longs, 0 - 1) && fails(longs, 1.0)
                ok = ok && fails(longs, "x") && fails(longs, big) && fails(bytes, 4)
                ok && (viaLocal(longs, 0) == 10)
            }
            exactIndex: () => {
                trace.value = 0
                (viaFormal(custom, big) == 1000) && (viaLocal(custom, big) == 1000) && (trace.value == 11)
            }
            overridden: () => {
                trace.value = 0
                (viaFormal(shared, 1) == 101) && (viaLocal(shared, 2) == 102) && (trace.value == 22)
            }
            suspended: () => {
                trace.value = 0
                result: (() => { viaFormal(lazy, 2) * 1000 + viaLocal(lazy, 1) }).future().value()
                trace.value * 100000 + result
            }
            failed: () => {
                trace.value = 0
                fails(failing, 0) && (trace.value == 44)
            }
            """;

    @Test
    void capturedReceiverIndexedByLocalBindingsKeepsTheAtProtocol() throws Exception {
        try (ProtosStandaloneHostedSession session = open(CELL_LOCAL_SITES)) {
            for (int round = 0; round < 3; round++) {
                assertTrue(session.prepareTopLevel("standard").executable().execute().asBoolean(),
                        "standard storage, Bytes and Errors, round " + round);
                assertTrue(session.prepareTopLevel("exactIndex").executable().execute().asBoolean(),
                        "an exact large Integer index reaches the selected at once");
            }
            for (String function : List.of("viaFormal", "viaLocal", "sumLocal")) {
                List<String> names = names(root(session, function));
                int subscript = -1;
                for (int i = 0; i < names.size(); i++) {
                    if (names.get(i).startsWith("c.BinarySubscript")) {
                        assertEquals(-1, subscript, function + " one indexed operation: " + names);
                        subscript = i;
                    }
                }
                assertTrue(subscript >= 2
                                && names.get(subscript).startsWith("c.BinarySubscriptCellLocal"),
                        function + " reads its cell receiver and local index itself: " + names);
                assertTrue(!(names.get(subscript - 2).startsWith("c.LoadCell")
                                && names.get(subscript - 1).startsWith("load.local")),
                        function + " keeps a separate operand pair: " + names);
                assertTrue(names.stream().noneMatch(name -> name.startsWith("c.IsStandardIndexedSelection")),
                        function + " adds an external selector: " + names);
            }
            session.prepareTopLevel("override").executable().execute();
            assertTrue(session.prepareTopLevel("overridden").executable().execute().asBoolean(),
                    "an override installed between invocations is selected once per access");
            assertTrue(session.prepareTopLevel("failed").executable().execute().asBoolean(),
                    "an Error signaled by a selected at runs the selection once");
            assertTrue(!session.prepareTopLevel("standard").executable().execute().asBoolean(),
                    "the overridden shared receiver no longer answers its standard element");
        }
        // Suspension runs inside a Task of a polyglot Context, as every other
        // suspending selection of this file does.
        assertEquals(3320010L, evalLong(CELL_LOCAL_SITES + "suspended()\n"),
                "each suspending selected at runs once and resumes with its own index");
    }

    /**
     * Primitive {@code long}, {@code double} and {@code boolean} storage
     * through stack operands; errors for bounds, mutability and index type.
     */
    @Test
    void primitiveStorageAndErrorsThroughStackOperands() {
        assertEquals(30L, evalLong("a: [10, 20, 30]\nk: 1\na[k + 1]"));
        assertEquals(9L, evalLong("a: [10, 20, 30]\nk: 0\na[k + 1] = 9\na[k + 1]"));
        assertTrue(evalBoolean("a: [1.5, 2.5]\nk: 0\na[k + 1] = 3.5\na[k + 1] == 3.5"));
        assertTrue(evalBoolean("a: [true, false]\nk: 0\na[k + 1] = true\na[k + 1] === true"));
        assertTrue(signals("a: [1, 2]\nk: 1\na[k + 1]"));
        assertTrue(signals("a: [1, 2]\nk: 0\na[k - 1] = 3"));
        assertTrue(signals("a: [1, 2]\na.freeze()\nk: 0\na[k + 1] = 3"));
        assertTrue(signals("a: [1, 2]\nk: \"x\"\na[k]"));
        assertTrue(signals("a: [1, 2]\nk: 1.5\na[k] = 3"));
    }

    @Test
    void frozenBytesRejectRepeatedWritesAndKeepTheirContent() {
        assertTrue(evalBoolean(BYTES + """
                source.freeze()
                write: (r, index, value) => { r[index] = value }
                caught: 0
                i: 0
                while(i < 10) {
                    Error.handle(() => { write(source, 0, 1) }, (e) => { caught = caught + 1 })
                    i = i + 1
                }
                (caught == 10) && (source[0] == 106)
                """));
    }

    @Test
    void longStorageArrayKeepsSmallIntegerElementsAfterRepeatedMutation() {
        assertTrue(evalBoolean("""
                a: [0, 0, 0, 0]
                i: 0
                while(i < 10) {
                    j: 0
                    while(j < 4) {
                        a[j] = a[j] + i * 4 + j
                        j = j + 1
                    }
                    i = i + 1
                }
                (a[0] == 180) && (a[1] == 190) && (a[2] == 200) &&
                    (a[3] == 210) && (a[3].parent() === SmallInteger)
                """));
    }

    @Test
    void floatAndBooleanStorageReadsAndSameFamilyWrites() {
        assertTrue(evalBoolean("a: [1.5, 2.5, 3.5]\n(a[0] == 1.5) && (a[2] == 3.5)"));
        assertTrue(evalBoolean("a: [true, false]\n(a[0] === true) && (a[1] === false)"));
        assertTrue(evalBoolean("a: [1.5, 2.5]\na[1] = 0.25\n(a[1] == 0.25) && (a[0] == 1.5)"));
        assertTrue(evalBoolean("a: [true, false]\na[1] = true\n(a[1] === true) && (a.size() == 2)"));
    }

    @Test
    void incompatibleWritesPromoteStorageAndKeepExactPriorElements() {
        assertTrue(evalBoolean("a: [1.5, 2.5]\na[0] = 7\n"
                + "(a[0] == 7) && (a[1] == 2.5) && (a.size() == 2)"));
        assertTrue(evalBoolean("a: [1, 9223372036854775807]\na[0] = 0.5\n"
                + "(a[0] == 0.5) && (a[1] == 9223372036854775807)"));
        assertTrue(evalBoolean("item: { value: 1 }\na: [true, false]\na[1] = item\n"
                + "(a[1] === item) && (a[0] === true)"));
        assertTrue(evalBoolean("a: [true, false]\na[0] = 1\na[0] = true\n"
                + "(a[0] === true) && (a[1] === false)"));
    }

    @Test
    void snapshotsAroundPrimitiveWritesStayIndependent() {
        String collect = "seen: [null, null]\nposition: { value: 0 }\n";
        // Snapshot before mutation: each observes the original elements.
        assertTrue(evalBoolean(collect + "a: [1.5, 2.5]\n"
                + "a.each((e) => { a[1] = 9.5\nseen[position.value] = e\nposition.value = position.value + 1 })\n"
                + "(seen[1] == 2.5) && (a[1] == 9.5)"));
        // Snapshot after mutation: each observes the written element.
        assertTrue(evalBoolean(collect + "a: [true, true]\na[1] = false\n"
                + "a.each((e) => { seen[position.value] = e\nposition.value = position.value + 1 })\n(seen[1] === false)"));
        // Publication followed by a same-family write.
        assertTrue(evalBoolean(collect + "a: [true, false]\n"
                + "a.each((e) => { a[0] = false\nseen[position.value] = e\nposition.value = position.value + 1 })\n"
                + "(seen[0] === true) && (a[0] === false) && (a[1] === false)"));
        // Publication followed by an incompatible write.
        assertTrue(evalBoolean(collect + "a: [1.5, 2.5]\n"
                + "a.each((e) => { a[1] = \"x\"\nseen[position.value] = e\nposition.value = position.value + 1 })\n"
                + "(seen[1] == 2.5) && (a[1] == \"x\") && (a[0] == 1.5)"));
    }

    @Test
    void primitiveStorageFrozenBoundsAndCustomSelections() {
        assertTrue(evalBoolean("a: [1.5, 2.5]\na.freeze()\na[1] == 2.5"));
        assertTrue(evalBoolean("a: [true]\na.freeze()\na[0] === true"));
        assertTrue(signals("a: [1.5, 2.5]\na.freeze()\na[0] = 3.5"));
        assertTrue(signals("a: [true]\na.freeze()\na[0] = false"));
        assertTrue(signals("a: [1.5]\na[9223372036854775807]"));
        assertTrue(signals("a: [true]\na[9223372036854775807] = false"));
        assertEquals(77L, evalLong("a: [1.5, 2.5]\na.at: (index) => { 77 }\na[0]"));
        assertEquals(88L, evalLong("a: [true]\na.atPut: (index, value) => { 88 }\na.atPut(0, false)"));
        assertTrue(evalBoolean("a: [true]\na.atPut: (index, value) => { 88 }\na[0] = false\na[0] === true"));
    }

    @Test
    void standardCustomStandardSameSiteOverPrimitiveStorage() {
        assertTrue(evalBoolean("""
                read: (receiver) => { receiver[0] }
                doubles: [1.5]
                booleans: [true]
                custom: { at: (index) => { 5 } }
                (read(doubles) == 1.5) && (read(booleans) === true) && (read(custom) == 5) && (read(doubles) == 1.5) && (read(booleans) === true)
                """));
    }

    private static final List<String> INVALID_INDEXES = List.of(
            "0 - 1",
            "3",
            "4",
            "2147483648",
            "9223372036854775807",
            "0 - 9223372036854775807 - 1");

    private static final String WARMED_SITES = """
            isError: (e) => { e.parent() === Error }
            read: (r, i) => { r[i] }
            rhsCount: { value: 0 }
            rhs: (v) => { rhsCount.value = rhsCount.value + 1; v }
            write: (r, i, v) => { r[i] = rhs(v) }
            put: (r, i, v) => { r.atPut(i, v) }
            """;

    /*
     * One read site warmed with valid indexes signals the standard guest
     * Error for every invalid signed long index, and the same site keeps
     * answering valid reads after each Error.
     */
    @Test
    void warmedReadSiteSignalsEveryInvalidIndexAndKeepsReading() {
        StringBuilder body = new StringBuilder(WARMED_SITES + """
                a: [10, 20, 30]
                ok: true
                k: 0
                while(k < 20) {
                    ok = ok && (read(a, 0) == 10) && (read(a, 2) == 30)
                    k = k + 1
                }
                """);
        for (String index : INVALID_INDEXES) {
            body.append("ok = ok && Error.handle(() => { read(a, ").append(index)
                    .append("); false }, (caught) => isError(caught))\n")
                    .append("ok = ok && (read(a, 1) == 20)\n");
        }
        body.append("ok && (a.size() == 3)");
        assertTrue(evalBoolean(body.toString()));
    }

    /*
     * One write site warmed with valid writes rejects invalid indexes
     * without changing content or size, evaluates its RHS once per
     * execution, answers the RHS, and still writes after the Errors; a
     * promotion through the same site keeps an earlier snapshot intact.
     */
    @Test
    void warmedWriteSiteRejectsInvalidIndexesAndKeepsWriting() {
        StringBuilder body = new StringBuilder(WARMED_SITES + """
                a: [1, 2, 3]
                ok: true
                k: 0
                while(k < 20) {
                    ok = ok && (write(a, 1, k) == k)
                    k = k + 1
                }
                ok = ok && (a[1] == 19) && (rhsCount.value == 20)
                """);
        int executions = 20;
        for (String index : INVALID_INDEXES) {
            executions++;
            body.append("ok = ok && Error.handle(() => { write(a, ").append(index)
                    .append(", 99); false }, (caught) => isError(caught))\n")
                    .append("ok = ok && (a[0] == 1) && (a[1] == 19) && (a[2] == 3)")
                    .append(" && (a.size() == 3) && (rhsCount.value == ")
                    .append(executions).append(")\n");
        }
        body.append("""
                ok = ok && (write(a, 2, 30) == 30) && (a[2] == 30) && (a[0] == 1)
                seen: [null, null, null]
                position: { value: 0 }
                a.each((e) => {
                    write(a, 2, "x")
                    seen[position.value] = e
                    position.value = position.value + 1
                })
                ok && (seen[2] == 30) && (a[2] == "x") && (a[0] == 1) && (a.size() == 3)
                """);
        assertTrue(evalBoolean(body.toString()));
    }

    /*
     * A write site warmed on a mutable Array rejects a FROZEN receiver
     * (valid or invalid index) without changes, and on a CLOSED receiver
     * replaces existing elements but rejects indexes outside its size.
     */
    @Test
    void warmedWriteSiteObservesFrozenAndClosedReceivers() {
        assertTrue(evalBoolean(WARMED_SITES + """
                m: [1, 2, 3]
                ok: true
                k: 0
                while(k < 20) {
                    ok = ok && (write(m, 0, k) == k)
                    k = k + 1
                }
                f: [4.5, 5.5]
                f.freeze()
                ok = ok && Error.handle(() => { write(f, 0, 1.5); false }, (caught) => isError(caught))
                ok = ok && Error.handle(() => { write(f, 9, 1.5); false }, (caught) => isError(caught))
                ok = ok && Error.handle(() => { write(f, 0, "x"); false }, (caught) => isError(caught))
                ok = ok && (f[0] == 4.5) && (f[1] == 5.5) && (f.size() == 2)
                c: [7, 8, 9]
                c.close()
                ok = ok && (write(c, 1, 80) == 80) && (c[1] == 80)
                ok = ok && Error.handle(() => { write(c, 3, 1); false }, (caught) => isError(caught))
                ok = ok && Error.handle(() => { write(c, 0 - 1, 1); false }, (caught) => isError(caught))
                ok = ok && (c[0] == 7) && (c[1] == 80) && (c[2] == 9) && (c.size() == 3)
                ok && (write(m, 2, 33) == 33) && (m[2] == 33)
                """));
    }

    /*
     * After warm-up, each indexed failure reaches Error.handle as its own
     * guest Error occurrence, unwrapped, and execution continues normally.
     */
    @Test
    void warmedIndexedErrorsReachTheHandlerAsDistinctOccurrences() {
        assertTrue(evalBoolean(WARMED_SITES + """
                a: [1, 2, 3]
                k: 0
                while(k < 20) {
                    read(a, 1)
                    write(a, 1, 2)
                    k = k + 1
                }
                seen: { value: null }
                first: Error.handle(() => { read(a, 3) }, (caught) => { seen.value = caught; caught })
                firstSeen: seen.value
                second: Error.handle(() => { write(a, 5, 1) }, (caught) => { seen.value = caught; caught })
                third: Error.handle(() => { read(a, 3) }, (caught) => caught)
                distinct: (first !== second) && (first !== third) && (second !== third)
                unwrapped: (firstSeen === first) && (seen.value === second)
                kinds: isError(first) && isError(second) && isError(third)
                distinct && unwrapped && kinds && (read(a, 2) == 3) && (write(a, 0, 5) == 5) && (a[0] == 5)
                """));
    }

    /*
     * Warmed standard sites still select a redefined at/atPut installed
     * afterwards, return to the standard behavior once the redefinitions
     * are removed, and the same sites compose a suspending selected at and
     * a non-local return from a selected at before signaling a standard
     * indexed Error again.
     */
    @Test
    void warmedSitesSelectOverridesSuspensionAndReturnHomeAroundErrors() {
        assertTrue(evalBoolean(WARMED_SITES + """
                a: [1, 2, 3]
                ok: true
                k: 0
                while(k < 20) {
                    ok = ok && (read(a, 1) == 2) && (write(a, 0, 1) == 1) && (put(a, 2, 3) == 3)
                    k = k + 1
                }
                ok = ok && Error.handle(() => { read(a, 3); false }, (caught) => isError(caught))
                a.at: (index) => { index + 100 }
                ok = ok && (read(a, 1) == 101) && (read(a, 7) == 107)
                a.atPut: (index, value) => { 77 }
                ok = ok && (put(a, 0, 9) == 77) && (write(a, 0, 9) == 9)
                a.removeSlot("at")
                a.removeSlot("atPut")
                ok = ok && (a[0] == 1) && (read(a, 1) == 2)
                ok = ok && (write(a, 0, 6) == 6) && (put(a, 1, 8) == 8) && (read(a, 0) == 6) && (read(a, 1) == 8)
                ok = ok && Error.handle(() => { read(a, 3); false }, (caught) => isError(caught))
                ok = ok && Error.handle(() => { write(a, 3, 1); false }, (caught) => isError(caught))
                Lazy: Array {
                    at: (index) => { (() => { 40 + index }).future().value() }
                }
                lazy: Lazy(1, 2, 3)
                suspended: (() => { read(lazy, 2) }).future().value()
                home: () => {
                    Box: Array { at: (index) => { ^42 } }
                    read(Box(1), 0)
                    0
                }
                ok = ok && (suspended == 42) && (home() == 42)
                ok = ok && Error.handle(() => { read(a, 0 - 1); false }, (caught) => isError(caught))
                ok && (read(a, 2) == 3) && (a.size() == 3)
                """));
    }

    private static List<String> names(ProtosBytecodeRootNode root) {
        return root.getBytecodeNode().getInstructionsAsList().stream()
                .map(ProtosInstructionNames::of).toList();
    }

    private static ProtosStandaloneHostedSession open(String source) throws Exception {
        Files.createDirectories(OUTPUT);
        Path file = OUTPUT.resolve("indexed-operations.protos");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        ProtosStandaloneHostedSession session = ProtosStandaloneHostedSession.open(CORE, file);
        if (session.initialOutcome().state() != ProtosExecutionOutcome.State.COMPLETED) {
            session.close();
            throw new AssertionError("Module initialization failed");
        }
        return session;
    }

    private static boolean signals(String body) {
        return evalBoolean(
                "Error.handle(() => {\n" + body + "\nfalse\n}, (caught) => true)");
    }

    private static long evalLong(String source) {
        try (Context context = context()) {
            return context.eval(ProtosLanguage.ID, source).asLong();
        }
    }

    private static boolean evalBoolean(String source) {
        try (Context context = context()) {
            return context.eval(ProtosLanguage.ID, source).asBoolean();
        }
    }

    private static Context context() {
        String core = CORE.toAbsolutePath().toString();
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", core).build();
    }
}
