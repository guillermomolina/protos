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

    private static ProtosBytecodeRootNode lower(String characters) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "map-construction.protos").build();
        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source).lowerRoot(sequence);
    }
}
