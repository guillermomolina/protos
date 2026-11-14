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
