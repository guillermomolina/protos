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
 * Bytecode DSL operations. Standard Array/Bytes receivers whose selected
 * behavior is the standard one use BinarySubscript/SetItem/GetLen; every
 * other receiver uses GetMethod and Call*Method with composed continuations.
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
    void dynamicPathsUseGetMethodAndCallMethodBehindTheStandardGuard()
            throws Exception {
        String source = """
                run: () => {
                    m: Map()
                    m["k"] = 1
                    a: [5, 6]
                    a[0] = 7
                    m["k"] + m.size() + a[0] + a.size()
                }
                """;
        try (ProtosStandaloneHostedSession session = open(source)) {
            ProtosBytecodeRootNode run = (ProtosBytecodeRootNode)
                    session.prepareTopLevel("run").hostExecutableForTesting()
                            .target.getRootNode();
            List<String> names = names(run);
            for (String operation : List.of(
                    "c.IsStandardIndexedSelection",
                    "c.SetItem",
                    "c.BinarySubscript",
                    "c.GetLen",
                    "c.GetMethod",
                    "c.CallNilaryMethod",
                    "c.CallUnaryMethod",
                    "c.CallBinaryMethod")) {
                assertTrue(names.stream().anyMatch(name -> name.startsWith(operation)),
                        "missing " + operation + " in " + names);
            }
            assertTrue(names.contains("yield"), "dynamic path is not composed: " + names);
            assertEquals(11L, session.prepareTopLevel("run")
                    .executable().execute().asLong());
        }
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
            for (String operation : List.of(
                    "c.IsStandardIndexedSelection",
                    "c.SetItem",
                    "c.BinarySubscript",
                    "c.GetMethod")) {
                assertTrue(names.stream().anyMatch(name -> name.startsWith(operation)),
                        "missing " + operation + " in " + names);
            }
            assertTrue(names.stream().anyMatch(name -> name.startsWith("c.Call")),
                    "missing Call*Method in " + names);
            assertTrue(names.contains("yield"), "dynamic path is not composed: " + names);
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
