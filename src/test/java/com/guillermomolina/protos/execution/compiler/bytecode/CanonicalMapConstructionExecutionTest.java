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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalMapConstructionExecutionTest {
    @Test
    void bytecodeBackendExecutesMapConstruction() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                ProtosTestExecutionSupport.evaluate(
                        "%{ \"answer\": 42 }[\"answer\"] == 42",
                        activation);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void bytecodeBackendRejectsDuplicateInitialKey() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosTestExecutionSupport.evaluate(
                                "%{ \"key\": 1; \"key\": 2 }",
                                activation));
    }

    @Test
    void bytecodeBackendUsesInheritedFactoryAndBypassesAtPut()
            throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                ProtosTestExecutionSupport.evaluate(
                        "DerivedMap: Map {\n"
                                + "    marker: 7\n"
                                + "    atPut: (key, value) => value\n"
                                + "}\n"
                                + "Map: DerivedMap\n"
                                + "mapping: %{ \"first\": 1; \"second\": 2 }\n"
                                + "(mapping.marker == 7) && "
                                + "(mapping[\"first\"] == 1) && "
                                + "(mapping[\"second\"] == 2)",
                        activation);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void bytecodeBackendRejectsArbitraryShadowFactory()
            throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosTestExecutionSupport.evaluate(
                                "Map: () => { "
                                        + "{ atPut: (key, value) => value } "
                                        + "}\n"
                                        + "%{ \"key\": 1 }",
                                activation));
    }

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }

    @Test
    void bytecodeBackendConstructsEmptyMapDirectly() throws Exception {
        ProtosPrelude prelude = core();
        Object result =
                ProtosTestExecutionSupport.evaluate(
                        "%{}", prelude.newModuleActivation());
        ProtosMapValue map = assertInstanceOf(ProtosMapValue.class, result);
        assertEquals(0, map.keyedSize());
        assertSame(prelude.mapPrototype(), map.parent().orElseThrow());
    }

    @Test
    void bytecodeBackendPreservesTheActualDerivedFactoryAsParent()
            throws Exception {
        ProtosPrelude prelude = core();
        Object result =
                ProtosTestExecutionSupport.evaluate(
                        "DerivedMap: Map {\n"
                                + "    marker: 7\n"
                                + "}\n"
                                + "Map: DerivedMap\n"
                                + "%{ \"first\": 1 }",
                        prelude.newModuleActivation());
        ProtosMapValue map = assertInstanceOf(ProtosMapValue.class, result);
        ProtosObjectValue parent =
                assertInstanceOf(ProtosObjectValue.class, map.parent().orElseThrow());
        assertNotSame(prelude.mapPrototype(), parent);
        assertTrue(parent.hasLocalSlot("marker"));
        assertSame(prelude.mapPrototype(), parent.parent().orElseThrow());
        assertEquals(1, map.keyedSize());
    }

    @Test
    void bytecodeBackendRejectsIncompatibleFactoryBeforeEntryEffects()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosTestExecutionSupport.evaluate(
                                "effects: 0\n"
                                        + "touch: () => { effects = effects + 1\n effects }\n"
                                        + "Map: { atPut: (key, value) => value }\n"
                                        + "%{ touch(): touch() }",
                                activation));
        // The module bindings of the activation survive the signalled Error.
        assertSame(
                Boolean.TRUE,
                ProtosTestExecutionSupport.evaluate("effects == 0", activation));
    }

    @Test
    void bytecodeBackendObservesChangedFactorySelectionAtOneSite()
            throws Exception {
        ProtosPrelude prelude = core();
        Object result =
                ProtosTestExecutionSupport.evaluate(
                        "First: Map { marker: 1 }\n"
                                + "Second: Map { marker: 2 }\n"
                                + "Map: First\n"
                                + "make: () => { %{ \"key\": 0 } }\n"
                                + "first: make()\n"
                                + "again: make()\n"
                                + "Map = Second\n"
                                + "second: make()\n"
                                + "secondMarker: second.marker\n"
                                + "Second.marker = 3\n"
                                + "third: make()\n"
                                + "(first.marker == 1) && (again.marker == 1) && "
                                + "(secondMarker == 2) && (third.marker == 3) && "
                                + "(second.marker == 3)",
                        prelude.newModuleActivation());
        assertSame(Boolean.TRUE, result);
    }

    @Test
    void bytecodeBackendNeverInvokesAtPutOrConstructDuringInitialDefinition()
            throws Exception {
        ProtosPrelude prelude = core();
        Object result =
                ProtosTestExecutionSupport.evaluate(
                        "calls: 0\n"
                                + "Counted: Map {\n"
                                + "    atPut: (key, value) => { calls = calls + 1\n value }\n"
                                + "}\n"
                                + "Map: Counted\n"
                                + "mapping: %{ 1: \"one\"; 2: \"two\"; 3: \"three\" }\n"
                                + "(calls == 0) && (mapping.size() == 3) && (mapping[2] == \"two\")",
                        prelude.newModuleActivation());
        assertSame(Boolean.TRUE, result);
    }

    @Test
    void compilerEmitsDirectMapConstructionWithoutFactoryCallRequest() {
        List<String> names = ProtosTestExecutionSupport.callEntered(
                () -> lower("factory: Map\nmapping: %{ \"a\": 1 }\nmapping")
                        .getBytecodeNode().getInstructionsAsList().stream()
                        .map(Instruction::getName)
                        .toList());
        assertEquals(1, names.stream().filter(name -> name.endsWith("MakeMap")).count());
        assertTrue(names.stream().noneMatch(name -> name.contains("MapConstructionFactoryCall")));
    }

    /*
     * Keys whose hash and == are guest methods recording their calls in
     * trace: make(id, h) answers a fresh key equal by id with hash h.
     */
    private static final String TRACED_KEYS =
            """
            trace: ""
            counts: {
                hash: 0
                equality: 0
            }
            source: {
                hash: () => {
                    trace = trace + "h" + this.id
                    counts.hash = counts.hash + 1
                    this.h
                }
                equals: (other) => {
                    trace = trace + "e" + this.id + other.id
                    counts.equality = counts.equality + 1
                    this.id == other.id
                }
            }
            view: source.alias("equals", "==")
            make: (i, hv) => view {
                id: i
                h: hv
            }
            key: (i, hv) => {
                trace = trace + "k" + i
                make(i, hv)
            }
            val: (i) => {
                trace = trace + "v" + i
                i
            }
            """;

    private static Object evaluateWithTracedKeys(String source) throws Exception {
        return ProtosTestExecutionSupport.evaluate(
                TRACED_KEYS + source, core().newModuleActivation());
    }

    @Test
    void bytecodeBackendKeysByIntegers() throws Exception {
        assertSame(Boolean.TRUE, ProtosTestExecutionSupport.evaluate(
                "m: %{ 1: \"a\"; 2: \"b\"; 0 - 3: \"c\"; 9223372036854775807: \"max\";"
                        + " 0 - 9223372036854775807 - 1: \"min\" }\n"
                        + "(m.size() == 5) && (m[1] == \"a\") && (m[2] == \"b\") && (m[0 - 3] == \"c\")"
                        + " && (m[9223372036854775807] == \"max\")"
                        + " && (m[0 - 9223372036854775807 - 1] == \"min\") && !m.containsKey(4)",
                core().newModuleActivation()));
    }

    @Test
    void bytecodeBackendKeysByFloats() throws Exception {
        assertSame(Boolean.TRUE, ProtosTestExecutionSupport.evaluate(
                "m: %{ 1.5: \"x\"; 2.25: \"y\"; 0.0: \"zero\" }\n"
                        + "(m.size() == 3) && (m[1.5] == \"x\") && (m[2.25] == \"y\")"
                        + " && (m[-0.0] == \"zero\") && !m.containsKey(1.25)",
                core().newModuleActivation()));
    }

    @Test
    void bytecodeBackendFindsEquivalentNumbersAcrossRepresentations() throws Exception {
        assertSame(Boolean.TRUE, ProtosTestExecutionSupport.evaluate(
                "m: %{ 1: \"one\"; 1/2: \"half\"; 4.0: \"four\" }\n"
                        + "(m[1.0] == \"one\") && (m[Complex(1, 0)] == \"one\") && (m[0.5] == \"half\")"
                        + " && (m[4] == \"four\") && (m[8/2] == \"four\")",
                core().newModuleActivation()));
    }

    @Test
    void bytecodeBackendRejectsEquivalentNumericDuplicates() throws Exception {
        for (String literal : List.of(
                "%{ 1: \"a\"; 1.0: \"b\" }",
                "%{ 1/2: \"a\"; 0.5: \"b\" }",
                "%{ 2.0: \"a\"; Complex(2, 0): \"b\" }",
                "%{ 0.0: \"a\"; -0.0: \"b\" }",
                "%{ 1180591620717411303424: \"a\"; 1180591620717411303424.0: \"b\" }")) {
            ProtosPrelude prelude = core();
            assertThrows(
                    ProtosSignalException.class,
                    () -> ProtosTestExecutionSupport.evaluate(
                            literal, prelude.newModuleActivation()),
                    literal);
        }
    }

    @Test
    void bytecodeBackendKeysByIntegersBeyondSigned64() throws Exception {
        assertSame(Boolean.TRUE, ProtosTestExecutionSupport.evaluate(
                "big: 1180591620717411303424\n"
                        + "m: %{ 1180591620717411303424: \"big\"; 9223372036854775807 + 1: \"edge\";"
                        + " 0 - 1180591620717411303424: \"negative\" }\n"
                        + "(m.size() == 3) && (m[big] == \"big\") && (m[9223372036854775808] == \"edge\")"
                        + " && (m[0 - big] == \"negative\") && (m[1180591620717411303424.0] == \"big\")",
                core().newModuleActivation()));
    }

    @Test
    void bytecodeBackendKeysByStringsAndObjects() throws Exception {
        assertSame(Boolean.TRUE, ProtosTestExecutionSupport.evaluate(
                "o: {}\n"
                        + "p: {}\n"
                        + "m: %{ \"a\": 1; \"b\": 2; o: 3; p: 4; true: 5; null: 6 }\n"
                        + "(m.size() == 6) && (m[\"a\"] == 1) && (m[\"b\"] == 2) && (m[o] == 3)"
                        + " && (m[p] == 4) && (m[true] == 5) && (m[null] == 6)"
                        + " && !m.containsKey({}) && !m.containsKey(\"c\") && !m.containsKey(false)",
                core().newModuleActivation()));
    }

    @Test
    void bytecodeBackendInvokesCustomHashAndEqualityInOrder() throws Exception {
        // Key, value, then hash for every entry in source order; equality
        // only against a same-hash candidate, query key as receiver.
        assertSame(Boolean.TRUE, evaluateWithTracedKeys(
                "m: %{ key(\"1\", 7): val(\"1\"); key(\"2\", 8): val(\"2\"); key(\"3\", 7): val(\"3\") }\n"
                        + "(m.size() == 3) && (trace == \"k1v1h1k2v2h2k3v3h3e31\")"
                        + " && (counts.hash == 3) && (counts.equality == 1)"));
    }

    @Test
    void bytecodeBackendRejectsCustomDuplicateBeforeLaterEntryEffects() throws Exception {
        ProtosActivation activation = core().newModuleActivation();
        ProtosTestExecutionSupport.evaluate(TRACED_KEYS, activation);
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosTestExecutionSupport.evaluate(
                        "%{ key(\"1\", 7): val(\"1\"); key(\"1\", 7): val(\"x\"); key(\"3\", 7): val(\"3\") }",
                        activation));
        assertSame(Boolean.TRUE, ProtosTestExecutionSupport.evaluate(
                "(trace == \"k1v1h1k1vxh1e11\") && (counts.equality == 1)", activation));
    }

    @Test
    void bytecodeBackendRejectsInvalidCustomHashAndEqualityResults() throws Exception {
        for (String program : List.of(
                "bad: { hash: () => 1.5 }\n%{ bad: 1 }",
                "bad: { hash: () => \"1\" }\n%{ bad: 1 }",
                "bad: { hash: () => 1\n equals: (other) => 1 }.alias(\"equals\", \"==\")\n"
                        + "other: { hash: () => 1 }\n%{ other: 1; bad: 2 }",
                "bad: { hash: () => 1\n equals: (other) => null }.alias(\"equals\", \"==\")\n"
                        + "other: { hash: () => 1 }\n%{ other: 1; bad: 2 }")) {
            ProtosPrelude prelude = core();
            assertThrows(
                    ProtosSignalException.class,
                    () -> ProtosTestExecutionSupport.evaluate(
                            program, prelude.newModuleActivation()),
                    program);
        }
    }

    @Test
    void bytecodeBackendWarmedSiteInvokesOverridingHashOfAnotherShape() throws Exception {
        // The site first sees standard Object, Integer and String hashes, then
        // a key whose parent overrides hash: the override is invoked.
        assertSame(Boolean.TRUE, ProtosTestExecutionSupport.evaluate(
                "calls: 0\n"
                        + "Plain: {}\n"
                        + "Custom: { hash: () => { calls = calls + 1\n 3 } }\n"
                        + "build: (k) => { %{ k: 1 } }\n"
                        + "plain: Plain {}\n"
                        + "[1, 2, 3, 4, 5, 6].each((i) => { build(plain)\n build(i)\n build(\"s\") })\n"
                        + "custom: Custom {}\n"
                        + "built: build(custom)\n"
                        + "(calls == 1) && (built[custom] == 1) && (calls == 2)",
                core().newModuleActivation()));
    }

    @Test
    void bytecodeBackendWarmedSiteObservesInPlaceHashReassignment() throws Exception {
        ProtosActivation activation = core().newModuleActivation();
        ProtosTestExecutionSupport.evaluate(
                "calls: 0\n"
                        + "Keyed: { hash: () => 1 }\n"
                        + "Counting: { hash: () => { calls = calls + 1\n 1 } }\n"
                        + "k: Keyed {}\n"
                        + "build: (key) => { %{ key: 1 } }\n"
                        + "[1, 2, 3, 4, 5, 6].each((i) => { build(k) })\n",
                activation);
        reassign(activation, "Keyed", "Counting", "hash");
        assertSame(Boolean.TRUE, ProtosTestExecutionSupport.evaluate(
                "build(k)\nbuild(k)\ncalls == 2", activation));
    }

    @Test
    void bytecodeBackendWarmedSiteObservesInPlaceEqualityReassignment() throws Exception {
        ProtosActivation activation = core().newModuleActivation();
        ProtosTestExecutionSupport.evaluate(
                "calls: 0\n"
                        + "Keyed: { hash: () => 1\n equals: (other) => false }.alias(\"equals\", \"==\")\n"
                        + "Counting: { equals: (other) => { calls = calls + 1\n false } }"
                        + ".alias(\"equals\", \"==\")\n"
                        + "build: (a, b) => { %{ a: 1; b: 2 } }\n"
                        + "[1, 2, 3, 4, 5, 6].each((i) => { build(Keyed {}, Keyed {}) })\n",
                activation);
        reassign(activation, "Keyed", "Counting", "==");
        assertSame(Boolean.TRUE, ProtosTestExecutionSupport.evaluate(
                "(build(Keyed {}, Keyed {}).size() == 2) && (calls == 1)", activation));
    }

    /* Rewrites a slot of a module object in place with the same slot of another. */
    private static void reassign(
            ProtosActivation activation, String target, String source, String slot) {
        ProtosObjectValue context = activation.context();
        ProtosObjectValue targetObject =
                (ProtosObjectValue) context.readLocalSlot(target).orElseThrow();
        ProtosObjectValue sourceObject =
                (ProtosObjectValue) context.readLocalSlot(source).orElseThrow();
        targetObject.assignLocalSlot(slot, sourceObject.readLocalSlot(slot).orElseThrow());
    }

    @Test
    void compilerEmitsSharedDirectKeyOperationBeforeEachOrdinaryComparison() {
        List<String> names = ProtosTestExecutionSupport.callEntered(
                () -> lower("mapping: %{ \"a\": 1; \"b\": 2 }\nmapping")
                        .getBytecodeNode().getInstructionsAsList().stream()
                        .map(Instruction::getName)
                        .toList());
        // One hash and one equality attempt per entry, each guarding the
        // ordinary prepared send that runs only when admission declines.
        assertEquals(4, names.stream()
                .filter(name -> name.contains("StructuredTryStandardKeyOperation")).count());
        assertEquals(4, names.stream()
                .filter(name -> name.contains("StructuredPrepareChildCall")).count());
        assertEquals(2, names.stream()
                .filter(name -> name.contains("StructuredComparisonCandidate")).count());
    }

    private static ProtosBytecodeRootNode lower(String characters) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "map-construction.protos").build();
        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source).lowerRoot(sequence);
    }
}
