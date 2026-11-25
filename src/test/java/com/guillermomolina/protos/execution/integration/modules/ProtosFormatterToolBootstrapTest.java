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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView;
import com.guillermomolina.protos.analysis.ProtosStaticAnalysisCore;
import com.guillermomolina.protos.analysis.ProtosStaticParseResult;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceContextualFor;
import com.guillermomolina.protos.parser.ast.SurfaceContextualIf;
import com.guillermomolina.protos.parser.ast.SurfaceContextualUntil;
import com.guillermomolina.protos.parser.ast.SurfaceContextualWhile;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ProtosFormatterToolBootstrapTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY =
            Path.of("protos", "lib");
    private static final Path TOOL_ROOT =
            Path.of("protos", "tools", "formatter");

    private static ProtosPolyglotRuntimeHost runtimeHost;

    @BeforeAll
    static void openRuntimeHost() {
        runtimeHost = ProtosPolyglotRuntimeHost.open();
    }

    @AfterAll
    static void closeRuntimeHost() {
        runtimeHost.close();
    }

    @Test
    void loadsExactBundledFormatterInFreshProcessWithoutProjectAuthority()
            throws Exception {
        ProtosStandardLibraryModuleResolver standardLibraryResolver =
                new ProtosStandardLibraryModuleResolver(
                        STANDARD_LIBRARY);

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "formatter",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        standardLibraryResolver);

        ProtosModuleKey entry =
                resolver.entryModule("Verify");

        assertEquals(
                ProtosModuleKey.source(
                        "bundled-tool:formatter/Verify.protos"),
                entry);

        // D242: the formatter interface is the published MODULE self:main, reached from the
        // explicitly selected SOURCE entry; it publishes exactly its intended members.
        ProtosModuleKey module = resolver.resolve("self:main", java.util.Optional.of(entry));
        assertEquals(ProtosModuleKey.module("bundled-tool:formatter/main"), module);
        String initializer = resolver.loadSource(module).characters();
        assertTrue(initializer.contains("this.toolId: \"TOOL010\""));
        assertTrue(initializer.contains("this.acceptSourceLayout:"));
        assertTrue(initializer.contains("this.formatStructural:"));
        assertTrue(initializer.contains("Structural: import(\"../Structural.protos\")"));
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> resolver.resolve("self:main", java.util.Optional.empty()));
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> resolver.resolve("./main/module.protos", java.util.Optional.of(entry)));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        ProtosFormatterToolBootstrap.verifyAvailable(
                CORE,
                TOOL_ROOT,
                standardLibraryResolver,
                observed::set,
                runtimeHost);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void transfersExactB1ProjectionWithoutExecutingUserSource()
            throws Exception {
        String source =
                "Error().signal()\n"
                        + "(café); [1_000]\n"
                        + "%{\"k\": 0xFF}\n"
                        + "x => x\n"
                        + "(y) => y\n"
                        + "foo() /* tail */ { body() }";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-bridge",
                                        1L,
                                        source));

        ProtosStandardLibraryModuleResolver standardLibraryResolver =
                new ProtosStandardLibraryModuleResolver(
                        STANDARD_LIBRARY);

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "formatter",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        standardLibraryResolver);

        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(
                        CORE,
                        resolver);

        ProtosObjectValue projected =
                ProtosSourceLayoutToolBridge.project(
                        view,
                        prelude);

        assertNotSame(
                projected,
                ProtosSourceLayoutToolBridge.project(
                        view,
                        prelude));

        assertEquals(
                Set.of(
                        "exactSource",
                        "program",
                        "tokens",
                        "comments",
                        "structuralForms",
                        "sequenceSeparators",
                        "closureForms",
                        "trailingClosures",
                        "contextualElses"),
                projected.localSlotsSnapshot().keySet());

        assertEquals(
                source,
                string(slot(projected, "exactSource")));

        ProtosObjectValue program =
                object(slot(projected, "program"));

        assertEquals(
                "SEQUENCE",
                string(slot(program, "kind")));

        assertEquals(
                "program",
                string(
                        slot(
                                object(slot(program, "path")),
                                "root")));

        List<Object> programExpressions =
                array(slot(program, "expressions"))
                        .indexedSnapshot();

        assertEquals(7, programExpressions.size());

        ProtosObjectValue groupedExpression =
                object(programExpressions.get(1));

        assertEquals(
                "GROUP",
                string(slot(groupedExpression, "kind")));

        ProtosObjectValue groupedName =
                object(slot(groupedExpression, "expression"));

        assertEquals(
                "NAME",
                string(slot(groupedName, "kind")));

        assertEquals(
                "café",
                string(slot(groupedName, "rawText")));

        ProtosObjectValue arrayExpression =
                object(programExpressions.get(2));

        assertEquals(
                "ARRAY_CONSTRUCTION",
                string(slot(arrayExpression, "kind")));

        ProtosObjectValue arrayArgument =
                object(
                        array(slot(arrayExpression, "arguments"))
                                .indexedSnapshot()
                                .get(0));

        ProtosObjectValue arrayLiteral =
                object(slot(arrayArgument, "expression"));

        assertEquals(
                "LITERAL",
                string(slot(arrayLiteral, "kind")));

        assertEquals(
                "1_000",
                string(slot(arrayLiteral, "rawText")));

        ProtosObjectValue trailingCall =
                object(programExpressions.get(6));

        assertEquals(
                "CALL",
                string(slot(trailingCall, "kind")));

        List<String> argumentKinds =
                array(slot(trailingCall, "arguments"))
                        .indexedSnapshot()
                        .stream()
                        .map(ProtosFormatterToolBootstrapTest::object)
                        .map(
                                argument ->
                                        object(
                                                slot(
                                                        argument,
                                                        "expression")))
                        .map(
                                argument ->
                                        string(
                                                slot(
                                                        argument,
                                                        "kind")))
                        .toList();

        assertTrue(argumentKinds.contains("CLOSURE"));

        List<String> rawTokens =
                array(slot(projected, "tokens"))
                        .indexedSnapshot()
                        .stream()
                        .map(ProtosFormatterToolBootstrapTest::object)
                        .map(
                                token ->
                                        string(
                                                slot(
                                                        token,
                                                        "rawText")))
                        .toList();

        assertTrue(rawTokens.contains("café"));
        assertTrue(rawTokens.contains("1_000"));
        assertTrue(rawTokens.contains("\"k\""));
        assertTrue(rawTokens.contains("0xFF"));

        List<ProtosObjectValue> structuralForms =
                array(slot(projected, "structuralForms"))
                        .indexedSnapshot()
                        .stream()
                        .map(ProtosFormatterToolBootstrapTest::object)
                        .toList();

        assertTrue(
                structuralForms.stream()
                        .map(
                                form ->
                                        string(
                                                slot(
                                                        form,
                                                        "kind")))
                        .toList()
                        .containsAll(
                                List.of(
                                        "GROUP",
                                        "ARRAY_CONSTRUCTION",
                                        "MAP_CONSTRUCTION",
                                        "CLOSURE_EXPRESSION_BODY",
                                        "CLOSURE_BLOCK_BODY")));

        ProtosObjectValue groupForm =
                structuralForms.stream()
                        .filter(
                                form ->
                                        string(
                                                        slot(
                                                                form,
                                                                "kind"))
                                                .equals("GROUP"))
                        .findFirst()
                        .orElseThrow();

        ProtosObjectValue groupPath =
                object(slot(groupForm, "path"));

        assertEquals(
                "program",
                string(slot(groupPath, "root")));

        ProtosObjectValue firstStep =
                object(
                        array(slot(groupPath, "steps"))
                                .indexedSnapshot()
                                .get(0));

        assertEquals(
                "expression",
                string(slot(firstStep, "role")));

        assertEquals(String.valueOf(1), String.valueOf(Integer.parseInt(com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(slot(firstStep, "index")))));

        List<String> separatorKinds =
                array(slot(projected, "sequenceSeparators"))
                        .indexedSnapshot()
                        .stream()
                        .map(ProtosFormatterToolBootstrapTest::object)
                        .map(
                                separator ->
                                        string(
                                                slot(
                                                        separator,
                                                        "kind")))
                        .toList();

        assertTrue(separatorKinds.contains("SEMICOLON"));
        assertTrue(separatorKinds.contains("LOGICAL_NEWLINE"));

        List<String> closureForms =
                array(slot(projected, "closureForms"))
                        .indexedSnapshot()
                        .stream()
                        .map(ProtosFormatterToolBootstrapTest::object)
                        .map(
                                form ->
                                        string(
                                                slot(
                                                        form,
                                                        "form")))
                        .toList();

        assertTrue(
                closureForms.containsAll(
                        List.of(
                                "BARE",
                                "PARENTHESIZED")));

        List<String> rawComments =
                array(slot(projected, "comments"))
                        .indexedSnapshot()
                        .stream()
                        .map(ProtosFormatterToolBootstrapTest::object)
                        .map(
                                comment ->
                                        string(
                                                slot(
                                                        comment,
                                                        "rawText")))
                        .toList();

        assertEquals(
                List.of("/* tail */"),
                rawComments);

        assertEquals(
                1,
                array(slot(projected, "trailingClosures"))
                        .indexedSize());

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        ProtosFormatterToolBootstrap.acceptSourceLayout(
                CORE,
                TOOL_ROOT,
                standardLibraryResolver,
                view,
                observed::set,
                runtimeHost);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void bug033BridgePreservesNamedAndPositionalArgumentIdentity()
            throws Exception {
        String source =
                "f(third: 3, (local: 4), ...rest, first: 1)";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:bug033-bridge",
                                        1L,
                                        source));

        ProtosStandardLibraryModuleResolver standardLibraryResolver =
                new ProtosStandardLibraryModuleResolver(
                        STANDARD_LIBRARY);

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "formatter",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        standardLibraryResolver);

        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(
                        CORE,
                        resolver);

        ProtosObjectValue projected =
                ProtosSourceLayoutToolBridge.project(
                        view,
                        prelude);

        ProtosObjectValue program =
                object(slot(projected, "program"));

        ProtosObjectValue call =
                object(
                        array(slot(program, "expressions"))
                                .indexedSnapshot()
                                .get(0));

        assertEquals("CALL", string(slot(call, "kind")));

        List<Object> arguments =
                array(slot(call, "arguments"))
                        .indexedSnapshot();

        assertEquals(4, arguments.size());

        assertEquals(
                "third",
                string(slot(object(arguments.get(0)), "name")));

        assertEquals(
                com.guillermomolina.protos.runtime.ProtosNullValue.INSTANCE,
                slot(object(arguments.get(1)), "name"));

        assertEquals(
                "GROUP",
                string(
                        slot(
                                object(
                                        slot(
                                                object(arguments.get(1)),
                                                "expression")),
                                "kind")));

        assertEquals(
                com.guillermomolina.protos.runtime.ProtosNullValue.INSTANCE,
                slot(object(arguments.get(2)), "name"));

        assertEquals(
                "first",
                string(slot(object(arguments.get(3)), "name")));
    }

    @Test
    void bug033FormattingPreservesNamedArgumentCanonicalSemantics()
            throws Exception {
        String[] samples = {
            "f: (first, second, third) => [first, second, third]\n"
                    + "value: f(third:3,first:1,second:2)",
            "object.method(second:2,first:1)",
            "super.move(second:2,first:1)",
            "f(last:3,1,...rest,first:2)",
            "f(café:1)",
            "f((name:1))",
            "f(first:[inner:1],second:g(item:2))",
            "Array: (entry) => entry\nresult:[entry:1]",
            "[entry:1]",
            "[(entry:1)]",
            "f(value:1) { block() }"
        };

        long version = 3300L;

        for (String source : samples) {
            String formatted = formatOnce(source, version++);

            assertEquals(
                    canonicalOf(source, version++),
                    canonicalOf(formatted, version++),
                    "formatter must preserve canonical semantics: " + source);

            assertEquals(
                    formatted,
                    formatOnce(formatted, version++),
                    "formatter must be idempotent: " + source);
        }

        assertEquals(
                "value: f(third: 3, first: 1, second: 2)\n",
                formatOnce(
                        "value:f(third:3,first:1,second:2)",
                        version++));

        assertFalse(
                canonicalOf("f(a: 1)", version++)
                        .equals(
                                canonicalOf(
                                        "f((a: 1))",
                                        version++)),
                "named arguments and grouped slot creation are distinct");
    }

    @Test
    void bug033PreservesNamedArgumentCommentsAndCanonicalSemantics()
            throws Exception {
        String[] samples = {
            "f(a /* before */: 1)",
            "f(a: /* after */ 1)",
            "f(a /* before */: /* after */ 1)",
            "f(a: // after\n 1)",
            "f(a:\n// own line\n1)",
            "f(a:\n\n// separated\n\n1)",
            "f(a /* outer */: g(b /* inner */: 2))",
            "super.move(a /* before */: /* after */ 1)",
            "[a /* before */: /* after */ 1]",
            "f(a: /* before nested */ [b: 2])"
        };

        long version = 3400L;

        for (String source : samples) {
            String formatted = formatOnce(source, version++);

            assertEquals(
                    canonicalOf(source, version++),
                    canonicalOf(formatted, version++),
                    "named argument semantics changed: " + source);

            assertEquals(
                    commentCount(source),
                    commentCount(formatted),
                    "named argument comments changed: " + source);

            assertEquals(
                    formatted,
                    formatOnce(formatted, version++),
                    "named argument formatting is not idempotent: "
                            + source);
        }
    }

    @Test
    void bug033NamedArgumentSurfaceMatrixPreservesCanonicalSemantics()
            throws Exception {
        String[] samples = {
            "f(café:1, π:2, 日本語:3)",
            "receiver.send(name:1)",
            "super.move(café:2)",
            "f(1, named:2, ...rest, other:3)",
            "f((name:1))",
            "f(object.name:1)",
            "f(name:1, (other:2))",
            "[name:1]",
            "[(name:1)]",
            "Array: (name) => name\nvalue: [name:1]",
            "Array: (name) => name\nvalue: Array(name:1)",
            "f(outer:g(inner:1), last:2)",
            "f(name:1) { body() }"
        };

        long version = 3500L;

        for (String source : samples) {
            String formatted = formatOnce(source, version++);

            assertEquals(
                    canonicalOf(source, version++),
                    canonicalOf(formatted, version++),
                    "Canonical semantics changed: " + source);

            assertEquals(
                    formatted,
                    formatOnce(formatted, version++),
                    "Formatting is not idempotent: " + source);
        }
    }

    @Test
    void formatsBasicStructuralPolicyInBundledProtos()
            throws Exception {
        String source =
                "café:1_000;hex:0xFF\n"
                        + "value:foo( 1,2 ).bar[ 3 ]  +   -x\n"
                        + "items:[1, 2,3]\n"
                        + "mapping:%{\"a\":1;\"b\":2}\n"
                        + "(a,b):source\n"
                        + "target=other\n"
                        + "super.move(x,y)";

        String expected =
                "café: 1_000; hex: 0xFF\n"
                        + "value: foo(1, 2).bar[3] + -x\n"
                        + "items: [1, 2, 3]\n"
                        + "mapping: %{ \"a\": 1; \"b\": 2 }\n"
                        + "(a, b): source\n"
                        + "target = other\n"
                        + "super.move(x, y)";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-structural",
                                        2L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((expected) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void formatsObjectsClosuresAndTrailingClosuresInBundledProtos()
            throws Exception {
        String source =
                "object:{...base;name:\"Rex\"}\n"
                        + "dog:animal{name:\"Rex\"\nage:2}\n"
                        + "bare:x=>x+1\n"
                        + "paren:(y)=>y*2\n"
                        + "multi:(a,b: 2,...rest)=>{result:a+b\n^result}\n"
                        + "run(1){first\nsecond}\n"
                        + "noop:()=>{}\n"
                        + "mapping:%{\"a\":1\n\"b\":2}";

        String expected =
                "object: { ...base; name: \"Rex\" }\n"
                        + "dog: animal {\n"
                        + "    name: \"Rex\"\n"
                        + "    age: 2\n"
                        + "}\n"
                        + "bare: x => x + 1\n"
                        + "paren: (y) => y * 2\n"
                        + "multi: (a, b: 2, ...rest) => {\n"
                        + "    result: a + b\n"
                        + "    ^result\n"
                        + "}\n"
                        + "run(1) {\n"
                        + "    first\n"
                        + "    second\n"
                        + "}\n"
                        + "noop: () => {}\n"
                        + "mapping: %{\n"
                        + "    \"a\": 1\n"
                        + "    \"b\": 2\n"
                        + "}";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-structural-blocks",
                                        3L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((expected) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void wrapsBinaryAndMemberChainsAtSoftHundredColumns()
            throws Exception {
        String binaryLeft = "a".repeat(60);
        String binaryRight = "b".repeat(50);
        String memberReceiver = "r".repeat(96);

        String source =
                binaryLeft
                        + "+"
                        + binaryRight
                        + "\n"
                        + memberReceiver
                        + ".alpha.beta";

        String expected =
                binaryLeft
                        + " +\n"
                        + "    "
                        + binaryRight
                        + "\n"
                        + memberReceiver
                        + "\n"
                        + "    .alpha\n"
                        + "    .beta";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-soft-width",
                                        4L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((expected) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void wrapsDelimitedListsAtSoftHundredColumns()
            throws Exception {
        String receiver = "r".repeat(96);
        String callLeft = "a".repeat(30);
        String callRight = "b".repeat(30);

        String arrayLeft = "c".repeat(50);
        String arrayRight = "d".repeat(50);

        String parameterLeft = "e".repeat(50);
        String parameterRight = "f".repeat(50);

        String source =
                receiver
                        + "("
                        + callLeft
                        + ","
                        + callRight
                        + ")\n"
                        + "["
                        + arrayLeft
                        + ","
                        + arrayRight
                        + "]\n"
                        + "("
                        + parameterLeft
                        + ","
                        + parameterRight
                        + ")=>"
                        + parameterLeft;

        String expected =
                receiver
                        + "(\n"
                        + "    "
                        + callLeft
                        + ",\n"
                        + "    "
                        + callRight
                        + "\n"
                        + ")\n"
                        + "[\n"
                        + "    "
                        + arrayLeft
                        + ",\n"
                        + "    "
                        + arrayRight
                        + "\n"
                        + "]\n"
                        + "(\n"
                        + "    "
                        + parameterLeft
                        + ",\n"
                        + "    "
                        + parameterRight
                        + "\n"
                        + ") => "
                        + parameterLeft;

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-delimited-width",
                                        5L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((expected) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void wrapsColonAssignmentAndClosureArrowAtSoftHundredColumns()
            throws Exception {
        String slotTarget = "s".repeat(96);
        String assignmentTarget = "a".repeat(96);
        String right = "valuevalue";

        String firstTarget = "m".repeat(45);
        String secondTarget = "n".repeat(45);

        String closureParameter = "p".repeat(96);

        String source =
                slotTarget
                        + ":"
                        + right
                        + "\n"
                        + assignmentTarget
                        + "="
                        + right
                        + "\n"
                        + "("
                        + firstTarget
                        + ","
                        + secondTarget
                        + "):"
                        + right
                        + "\n"
                        + closureParameter
                        + "=>"
                        + right;

        String expected =
                slotTarget
                        + ":\n"
                        + "    "
                        + right
                        + "\n"
                        + assignmentTarget
                        + " =\n"
                        + "    "
                        + right
                        + "\n"
                        + "("
                        + firstTarget
                        + ", "
                        + secondTarget
                        + "):\n"
                        + "    "
                        + right
                        + "\n"
                        + closureParameter
                        + " =>\n"
                        + "    "
                        + right;

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-operator-width",
                                        6L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((expected) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void preservesExactLexicalPayloadSpelling()
            throws Exception {
        String triple =
                "\"\"\"alpha\r\n\tbeta\n  gamma\"\"\"";

        String source =
                "'a'\n"
                        + "\"a\"\n"
                        + "\"\\\\n\"\n"
                        + "\"\\\\u{0A}\"\n"
                        + "1000\n"
                        + "1_000\n"
                        + "0xff\n"
                        + "0xFF\n"
                        + "2e3\n"
                        + "2E3\n"
                        + "café\n"
                        + triple;

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-lexical-payload",
                                        7L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((source) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void bug022FormatsLeadingCommentsAndPreservesLicense()
            throws Exception {
        String[][] cases = {
            {
                "/* probe */\nx:  10\n",
                "/* probe */\nx: 10\n"
            },
            {
                "/* first */\n/* second */\n\nx:10",
                "/* first */\n/* second */\n\nx: 10\n"
            },
            {
                "/* first */ /* second */\nx:10",
                "/* first */ /* second */\nx: 10\n"
            },
            {
                "/* inline */x:10",
                "/* inline */ x: 10\n"
            },
            {
                "x:10\n/* footer */",
                "x: 10\n/* footer */\n"
            },
            {
                "x:10 // footer",
                "x: 10 // footer\n"
            },
            {
                "x:10\n\n/* footer */",
                "x: 10\n\n/* footer */\n"
            }
        };

        long version = 80L;

        for (String[] sample : cases) {
            ProtosWholeDocumentFormatter.Result result =
                    ProtosWholeDocumentFormatter.format(
                            CORE,
                            TOOL_ROOT,
                            new ProtosStandardLibraryModuleResolver(
                                    STANDARD_LIBRARY),
                            new ProtosDocumentSnapshot(
                                    "memory:bug022-original",
                                    version++,
                                    sample[0]),
                            ignored -> {},
                            runtimeHost);

            assertEquals(
                    ProtosWholeDocumentFormatter.Status.SUCCESS,
                    result.status());
            assertEquals(sample[1], result.source());

            ProtosWholeDocumentFormatter.Result repeated =
                    ProtosWholeDocumentFormatter.format(
                            CORE,
                            TOOL_ROOT,
                            new ProtosStandardLibraryModuleResolver(
                                    STANDARD_LIBRARY),
                            new ProtosDocumentSnapshot(
                                    "memory:bug022-idempotence",
                                    version++,
                                    result.source()),
                            ignored -> {},
                            runtimeHost);

            assertEquals(
                    ProtosWholeDocumentFormatter.Status.SUCCESS,
                    repeated.status());
            assertEquals(result.source(), repeated.source());
        }

        String example =
                java.nio.file.Files.readString(
                        Path.of(
                                "protos",
                                "examples",
                                "basics",
                                "slots.protos"));

        String license =
                example.substring(0, example.indexOf("*/") + 2);

        ProtosWholeDocumentFormatter.Result formatted =
                ProtosWholeDocumentFormatter.format(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        new ProtosDocumentSnapshot(
                                "memory:bug022-slots",
                                version++,
                                example),
                        ignored -> {},
                        runtimeHost);

        assertEquals(
                ProtosWholeDocumentFormatter.Status.SUCCESS,
                formatted.status());
        assertTrue(formatted.source().startsWith(license));

        ProtosWholeDocumentFormatter.Result repeatedLicense =
                ProtosWholeDocumentFormatter.format(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        new ProtosDocumentSnapshot(
                                "memory:bug022-slots-idempotence",
                                version++,
                                formatted.source()),
                        ignored -> {},
                        runtimeHost);

        assertEquals(
                ProtosWholeDocumentFormatter.Status.SUCCESS,
                repeatedLicense.status());
        assertEquals(formatted.source(), repeatedLicense.source());
    }

    @Test
    void bug024PreservesEndOfLineCommentsAfterBlankLines()
            throws Exception {
        String[][] cases = {
            {
                "a:1\n\nb:2 // note\nc:3",
                "a: 1\n\nb: 2 // note\nc: 3\n"
            },
            {
                "a:1\n\nb:2 // note\n\nc:3",
                "a: 1\n\nb: 2 // note\n\nc: 3\n"
            },
            {
                "a:1\n\n\nb:2 // note\nc:3",
                "a: 1\n\nb: 2 // note\nc: 3\n"
            },
            {
                "a:1\n\nb:2 /* block comment */\nc:3",
                "a: 1\n\nb: 2 /* block comment */\nc: 3\n"
            },
            {
                "a:1\n\n// own comment\nb:2 // trailing comment\nc:3",
                "a: 1\n\n// own comment\nb: 2 // trailing comment\nc: 3\n"
            },
            {
                "a:1\n\nb:2 // first\nc:3 // second\nd:4",
                "a: 1\n\nb: 2 // first\nc: 3 // second\nd: 4\n"
            },
            {
                "a:1 // first\n\nb:2",
                "a: 1 // first\n\nb: 2\n"
            },
            {
                "a:1\r\n\r\nb:2 // note\r\nc:3\r\n",
                "a: 1\n\nb: 2 // note\nc: 3\n"
            },
            {
                "Runtime: import(\"std:runtime\")\n"
                        + "info: Runtime.info()\n"
                        + "\n"
                        + "print(info.implementation )        // \"Protos\"\n"
                        + "print(info.implementationVersion ) // version of this Protos build\n"
                        + "print(info.engineVersion )         // active Polyglot Engine version\n"
                        + "print(info.platform )              // \"JVM\" or \"NATIVE_IMAGE\"\n",
                "Runtime: import(\"std:runtime\")\n"
                        + "info: Runtime.info()\n"
                        + "\n"
                        + "print(info.implementation) // \"Protos\"\n"
                        + "print(info.implementationVersion) // version of this Protos build\n"
                        + "print(info.engineVersion) // active Polyglot Engine version\n"
                        + "print(info.platform) // \"JVM\" or \"NATIVE_IMAGE\"\n"
            }
        };

        ProtosStaticAnalysisCore analysis =
                new ProtosStaticAnalysisCore();
        long version = 240L;

        for (String[] sample : cases) {
            AtomicReference<ProtosProcessRuntime> observed =
                    new AtomicReference<>();

            ProtosWholeDocumentFormatter.Result result =
                    ProtosWholeDocumentFormatter.format(
                            CORE,
                            TOOL_ROOT,
                            new ProtosStandardLibraryModuleResolver(
                                    STANDARD_LIBRARY),
                            new ProtosDocumentSnapshot(
                                    "memory:bug024-original",
                                    version++,
                                    sample[0]),
                            observed::set,
                            runtimeHost);

            assertEquals(
                    ProtosWholeDocumentFormatter.Status.SUCCESS,
                    result.status(),
                    sample[0]);
            assertEquals(sample[1], result.source());

            ProtosProcessRuntime process = observed.get();

            assertNotNull(process);
            assertEquals(
                    ProtosProcessRuntime.LifecycleState.TERMINATED,
                    process.lifecycleState());
            assertTrue(
                    process.rootFilesystemForRuntime().isEmpty());

            ProtosDocumentSnapshot formattedSnapshot =
                    new ProtosDocumentSnapshot(
                            "memory:bug024-formatted",
                            version++,
                            result.source());

            assertTrue(
                    !(analysis.parse(formattedSnapshot)
                            instanceof ProtosStaticParseResult.Failed),
                    result.source());

            assertEquals(
                    analysis.sourceLayout(
                                    new ProtosDocumentSnapshot(
                                            "memory:bug024-input",
                                            version++,
                                            sample[0]))
                            .preservationProjection()
                            .comments()
                            .stream()
                            .map(
                                    comment ->
                                            comment.rawText()
                                                    + "|"
                                                    + comment.placement()
                                                    + "|"
                                                    + comment.boundaryKind()
                                                    + "|"
                                                    + comment.preceding()
                                                    + "|"
                                                    + comment.following())
                            .toList(),
                    analysis.sourceLayout(formattedSnapshot)
                            .preservationProjection()
                            .comments()
                            .stream()
                            .map(
                                    comment ->
                                            comment.rawText()
                                                    + "|"
                                                    + comment.placement()
                                                    + "|"
                                                    + comment.boundaryKind()
                                                    + "|"
                                                    + comment.preceding()
                                                    + "|"
                                                    + comment.following())
                            .toList());

            ProtosWholeDocumentFormatter.Result repeated =
                    ProtosWholeDocumentFormatter.format(
                            CORE,
                            TOOL_ROOT,
                            new ProtosStandardLibraryModuleResolver(
                                    STANDARD_LIBRARY),
                            new ProtosDocumentSnapshot(
                                    "memory:bug024-idempotence",
                                    version++,
                                    result.source()),
                            ignored -> {},
                            runtimeHost);

            assertEquals(
                    ProtosWholeDocumentFormatter.Status.SUCCESS,
                    repeated.status());
            assertEquals(result.source(), repeated.source());
        }
    }

    @Test
    void preservesOwnLineAndEndOfLineSequenceComments()
            throws Exception {
        String source =
                "a:1 // eol  keep\ttext\n"
                        + "// own  keep\ttext\n"
                        + "b:2";

        String expected =
                "a: 1 // eol  keep\ttext\n"
                        + "// own  keep\ttext\n"
                        + "b: 2";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-sequence-comments",
                                        8L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((expected) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void preservesMultilineCommentBeforeTrailingClosure()
            throws Exception {
        String source =
                "foo() /* comment\n"
                        + "still  comment\t*/ {body()}";

        String expected =
                "foo() /* comment\n"
                        + "still  comment\t*/ { body() }";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-trailing-comment",
                                        9L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((expected) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void i119FormatsContextualIfAndKeepsElseOnTheClosingLine()
            throws Exception {
        assertFormatsContextualIf(
                "if(a){b}",
                "if(a) { b }");
        assertFormatsContextualIf(
                "if(a){b}else{c}",
                "if(a) { b } else { c }");
        assertFormatsContextualIf(
                "if(a) {\n"
                        + "    first()\n"
                        + "    b\n"
                        + "} else {\n"
                        + "    c()\n"
                        + "    d\n"
                        + "}",
                "if(a) {\n"
                        + "    first()\n"
                        + "    b\n"
                        + "} else {\n"
                        + "    c()\n"
                        + "    d\n"
                        + "}");
        assertFormatsContextualIf(
                "if(a) {\n"
                        + "    first()\n"
                        + "\n"
                        + "    b\n"
                        + "} else {\n"
                        + "    c\n"
                        + "}",
                "if(a) {\n"
                        + "    first()\n"
                        + "\n"
                        + "    b\n"
                        + "} else { c }");
        assertFormatsContextualIf(
                "if(a){if(b){1}else{2}}else{3}",
                "if(a) { if(b) { 1 } else { 2 } } else { 3 }");
        assertFormatsContextualIf(
                "if(a) {\n"
                        + "    if(b) {\n"
                        + "        first()\n"
                        + "        1\n"
                        + "    } else { 2 }\n"
                        + "    3\n"
                        + "} else { 4 }",
                "if(a) {\n"
                        + "    if(b) {\n"
                        + "        first()\n"
                        + "        1\n"
                        + "    } else { 2 }\n"
                        + "    3\n"
                        + "} else { 4 }");
    }

    @Test
    void i119PreservesContextualIfCommentsExactlyOnce()
            throws Exception {
        assertFormatsContextualIf(
                "if /* comment */ (a) { b }",
                "if /* comment */ (a) { b }");
        assertFormatsContextualIf(
                "if(/* comment */ a) { b }",
                "if(/* comment */ a) { b }");
        assertFormatsContextualIf(
                "if(a) /* comment */ { b }",
                "if(a) /* comment */ { b }");
        assertFormatsContextualIf(
                "if(a) { b } /* comment */ else { c }",
                "if(a) { b } /* comment */ else { c }");
        assertFormatsContextualIf(
                "if(a) { b } else /* comment */ { c }",
                "if(a) { b } else /* comment */ { c }");
        assertFormatsContextualIf(
                "if(a){b}/* one\n two */else{c}",
                "if(a) { b } /* one\n two */ else { c }");
        assertFormatsContextualIf(
                "if(a){b}else/* one\n two */{c}",
                "if(a) { b } else /* one\n two */ { c }");
        assertFormatsContextualIf(
                "if /* a */ (/* b */ x) /* c */ {y} /* d */ else /* e */ {z}",
                "if /* a */ (/* b */ x) /* c */ { y } /* d */ else /* e */ { z }");
        assertFormatsContextualIf(
                "if(a) {\n"
                        + "    first()\n"
                        + "    // inside true\n"
                        + "    b\n"
                        + "} else {\n"
                        + "    c() // inside false\n"
                        + "    d\n"
                        + "}",
                "if(a) {\n"
                        + "    first()\n"
                        + "    // inside true\n"
                        + "    b\n"
                        + "} else {\n"
                        + "    c() // inside false\n"
                        + "    d\n"
                        + "}");
    }

    private static void assertFormatsContextualIf(
            String source,
            String expected)
            throws Exception {
        assertFormatsIdempotently(
                source,
                expected,
                SurfaceContextualIf.class);
        String formatted = formatOnce(source, 33L);
        assertFalse(
                formatted.matches("(?s).*\\}\\s*\\n\\s*else.*"),
                () -> "a paired else must stay on the closing line: " + formatted);
        assertEquals(
                commentCount(source),
                commentCount(formatted),
                () -> "comments must be preserved exactly once: " + formatted);

        SurfaceContextualIf original = assertInstanceOf(
                SurfaceContextualIf.class,
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-i119-source",
                                        34L,
                                        source))
                        .program()
                        .expressions()
                        .get(0));
        SurfaceContextualIf reparsed = assertInstanceOf(
                SurfaceContextualIf.class,
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-i119-formatted",
                                        35L,
                                        formatted))
                        .program()
                        .expressions()
                        .get(0));
        assertEquals(
                original.falseBranch() == null,
                reparsed.falseBranch() == null,
                () -> "formatting must keep the contextual if arity: " + formatted);
    }

    private static int commentCount(String text) {
        return text.split("/\\*", -1).length
                - 1
                + text.split("//", -1).length
                - 1;
    }

    @Test
    void i117bFormatsContextualWhileAndKeepsTrailingClosureComments()
            throws Exception {
        assertFormatsIdempotently(
                "while(n > 0) /* comment */ {\n"
                        + "    n = n - 1\n"
                        + "}",
                "while(n > 0) /* comment */ { n = n - 1 }",
                SurfaceContextualWhile.class);
        assertFormatsIdempotently(
                "while(n > 0) /* comment */ {\n"
                        + "    first()\n"
                        + "    n = n - 1\n"
                        + "}",
                "while(n > 0) /* comment */ {\n"
                        + "    first()\n"
                        + "    n = n - 1\n"
                        + "}",
                SurfaceContextualWhile.class);
        assertFormatsIdempotently(
                "while(n>0){n = n - 1}",
                "while(n > 0) { n = n - 1 }",
                SurfaceContextualWhile.class);
        assertFormatsIdempotently(
                "while /* before */ (n > 0) { n = n - 1 }",
                "while /* before */ (n > 0) { n = n - 1 }",
                SurfaceContextualWhile.class);
        assertFormatsIdempotently(
                "while(/* after */ n > 0) { n = n - 1 }",
                "while(/* after */ n > 0) { n = n - 1 }",
                SurfaceContextualWhile.class);
        assertFormatsIdempotently(
                "while /* before */ (/* after */ n>0) {n = n - 1}",
                "while /* before */ (/* after */ n > 0) { n = n - 1 }",
                SurfaceContextualWhile.class);
        assertFormatsIdempotently(
                "while /* one\n two */ (n > 0) { n = n - 1 }",
                "while /* one\n two */ (n > 0) { n = n - 1 }",
                SurfaceContextualWhile.class);
        assertFormatsIdempotently(
                "while /* before */ (/* after */ n > 0) /* between */ { n = n - 1 }",
                "while /* before */ (/* after */ n > 0) /* between */ { n = n - 1 }",
                SurfaceContextualWhile.class);
        assertFormatsIdempotently(
                "foo(n) /* comment */ {body}",
                "foo(n) /* comment */ { body }",
                SurfaceCall.class);
    }

    @Test
    void i121bFormatsContextualUntilWithTheSuffixOnTheClosingLine()
            throws Exception {
        assertFormatsIdempotently(
                "{work()}until(done())",
                "{ work() } until(done())",
                SurfaceContextualUntil.class);
        assertFormatsIdempotently(
                "{} until(done)",
                "{} until(done)",
                SurfaceContextualUntil.class);
        assertFormatsIdempotently(
                "{\n"
                        + "    work()\n"
                        + "    n = n - 1\n"
                        + "}   until(  n == 0  )",
                "{\n"
                        + "    work()\n"
                        + "    n = n - 1\n"
                        + "} until(n == 0)",
                SurfaceContextualUntil.class);
        assertFormatsIdempotently(
                "{{a}until(b)}until(c)",
                "{ { a } until(b) } until(c)",
                SurfaceContextualUntil.class);
        assertFormatsIdempotently(
                "{ while(a) { b }\nif(c) { d } else { e } } until(f)",
                "{\n"
                        + "    while(a) { b }\n"
                        + "    if(c) { d } else { e }\n"
                        + "} until(f)",
                SurfaceContextualUntil.class);
    }

    @Test
    void i141FormatsContextualForHeadersWithoutExpandingTheSugar()
            throws Exception {
        assertFormatsIdempotently(
                "for(item in items){item}",
                "for(item in items) { item }",
                SurfaceContextualFor.class);
        assertFormatsIdempotently(
                "for( i ,0 , 5 ){ i }",
                "for(i, 0, 5) { i }",
                SurfaceContextualFor.class);
        assertFormatsIdempotently(
                "for(i,10,0,-2){visit(i)}",
                "for(i, 10, 0, -2) { visit(i) }",
                SurfaceContextualFor.class);
        assertFormatsIdempotently(
                "for(row in rows) {\n"
                        + "  for(i,0,row){ a\nb }\n"
                        + "  row\n"
                        + "}",
                "for(row in rows) {\n"
                        + "    for(i, 0, row) {\n"
                        + "        a\n"
                        + "        b\n"
                        + "    }\n"
                        + "    row\n"
                        + "}",
                SurfaceContextualFor.class);
        assertFormatsIdempotently(
                "for /* a */ (/* b */ item /* c */ in /* d */ items) /* e */ {item}",
                "for /* a */ (/* b */ item /* c */ in /* d */ items) /* e */ { item }",
                SurfaceContextualFor.class);
        assertFormatsIdempotently(
                "for(i, /* s */ 0, /* t */ 3, /* u */ 1) { i }",
                "for(i, /* s */ 0, /* t */ 3, /* u */ 1) { i }",
                SurfaceContextualFor.class);
        for (String sideOfComma : List.of(
                "for(i /*a*/, 0, 3) { body }",
                "for(i, /*a*/ 0, 3) { body }",
                "for(i, 0 /*a*/, 3) { body }",
                "for(i, 0, /*a*/ 3) { body }",
                "for(i, 0, 10 /*a*/, -2) { body }",
                "for(i, 0, 10, /*a*/ -2) { body }",
                "for(item /*a*/ in items) { body }",
                "for(item in /*a*/ items) { body }",
                "for(item in items) /*a*/ { body }")) {
            assertFormatsIdempotently(
                    sideOfComma,
                    sideOfComma,
                    SurfaceContextualFor.class);
        }
        assertFormatsIdempotently(
                "for(i/*a*/,0/*b*/,3){body}",
                "for(i /*a*/, 0 /*b*/, 3) { body }",
                SurfaceContextualFor.class);
        assertFormatsIdempotently(
                "(for)(i,a,b){body}",
                "(for)(i, a, b) { body }",
                SurfaceCall.class);
    }

    @Test
    void i121bPreservesContextualUntilCommentsExactlyOnce()
            throws Exception {
        assertFormatsContextualUntil(
                "{ a } /* comment */ until(b)",
                "{ a } /* comment */ until(b)");
        assertFormatsContextualUntil(
                "{ a } until /* comment */ (b)",
                "{ a } until /* comment */ (b)");
        assertFormatsContextualUntil(
                "{ a } until(/* comment */ b)",
                "{ a } until(/* comment */ b)");
        assertFormatsContextualUntil(
                "{a}/* one\n two */until(b)",
                "{ a } /* one\n two */ until(b)");
        assertFormatsContextualUntil(
                "{a}until/* one\n two */(b)",
                "{ a } until /* one\n two */ (b)");
        assertFormatsContextualUntil(
                "{a}until(/* one\n two */b)",
                "{ a } until(/* one\n two */ b)");
        assertFormatsContextualUntil(
                "{y} /* a */ until /* b */ (/* c */ x)",
                "{ y } /* a */ until /* b */ (/* c */ x)");
        assertFormatsContextualUntil(
                "{\n"
                        + "    first() // inside body\n"
                        + "    b\n"
                        + "} until(c)",
                "{\n"
                        + "    first() // inside body\n"
                        + "    b\n"
                        + "} until(c)");
    }

    @Test
    void i121bDoesNotReinterpretUnrelatedObjectLiterals()
            throws Exception {
        String source = "{ a }\nuntil(b)";
        String formatted = formatOnce(source, 36L);
        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-i121b-object",
                                        37L,
                                        formatted));
        assertEquals(2, view.program().expressions().size());
        assertInstanceOf(
                SurfaceObject.class,
                view.program().expressions().get(0));
        assertInstanceOf(
                SurfaceCall.class,
                view.program().expressions().get(1));
        assertEquals(formatted, formatOnce(formatted, 38L));

        String lineComment = "{ a } // note\nuntil(b)";
        String formattedLineComment = formatOnce(lineComment, 40L);
        ProtosSourceLayoutView lineCommentView =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-i121b-line-comment",
                                        41L,
                                        formattedLineComment));
        assertEquals(2, lineCommentView.program().expressions().size());
        assertInstanceOf(
                SurfaceObject.class,
                lineCommentView.program().expressions().get(0));
        assertInstanceOf(
                SurfaceCall.class,
                lineCommentView.program().expressions().get(1));
        assertEquals(
                commentCount(lineComment),
                commentCount(formattedLineComment),
                () -> "comments must be preserved exactly once: " + formattedLineComment);
        assertEquals(formattedLineComment, formatOnce(formattedLineComment, 42L));

        assertFormatsIdempotently(
                "{ a: 1 }.until(b)",
                "{ a: 1 }.until(b)",
                SurfaceCall.class);
    }

    private static void assertFormatsContextualUntil(
            String source,
            String expected)
            throws Exception {
        assertFormatsIdempotently(
                source,
                expected,
                SurfaceContextualUntil.class);
        String formatted = formatOnce(source, 39L);
        assertFalse(
                formatted.matches("(?s).*\\}\\s*\\n\\s*until.*"),
                () -> "the until suffix must stay on the closing line: " + formatted);
        assertEquals(
                commentCount(source),
                commentCount(formatted),
                () -> "comments must be preserved exactly once: " + formatted);
    }

    private static void assertFormatsIdempotently(
            String source,
            String expected,
            Class<? extends SurfaceExpression> expectedKind)
            throws Exception {
        String first = formatOnce(source, 30L);
        assertEquals(expected + "\n", first);

        ProtosSourceLayoutView formattedView =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-i117b-formatted",
                                        31L,
                                        first));
        assertEquals(1, formattedView.program().expressions().size());
        assertInstanceOf(
                expectedKind,
                formattedView.program().expressions().get(0));

        assertEquals(first, formatOnce(first, 32L));
    }

    @Test
    void aud022PreservesMixedContextualForReturnBitwiseAndRangeForms()
            throws Exception {
        String source =
                "run:()=>{\n"
                        + "for(item in InvertedRange(3,0).reverse())"
                        + "{mask:~item&3|item<<1\nreturn mask}\n"
                        + "for(i,5,0,-2) /* step */ "
                        + "{shifted:i>>1\nshifted}\n"
                        + "for(j,0,3){j~ ~j}\n"
                        + "return /* final */ Range(0,3).reverse()\n"
                        + "}\n";

        String formatted = formatOnce(source, 901L);

        assertEquals(
                canonicalOf(source, 902L),
                canonicalOf(formatted, 903L),
                "formatting must preserve the combined canonical semantics");
        assertEquals(
                formatted,
                formatOnce(formatted, 904L),
                "formatting the combined forms must be idempotent");

        assertTrue(formatted.contains("for(item in "));
        assertTrue(formatted.contains("for(i, "));
        assertTrue(formatted.contains("for(j, "));
        assertTrue(formatted.contains("return mask"));
        assertTrue(formatted.contains("return /* final */"));
        assertTrue(formatted.contains("~item & 3 | item << 1"));
        assertTrue(formatted.contains("j ~ ~j"));
    }

    @Test
    void formatsBitwiseOperatorsWithLexicallySafePrefixes()
            throws Exception {
        String[][] samples = {
            {"~ -a", "~ -a"},
            {"- ~a", "- ~a"},
            {"~ ~a", "~ ~a"},
            {"! ~a", "! ~a"},
            {"~(-a)", "~(-a)"},
            {"~a", "~a"},
            {"^ ~a", "^ ~a"},
            {"^ -a", "^ -a"},
            {"^ !a", "^ !a"},
            {"a~ ~b", "a ~ ~b"},
            {"a&b|c<<d>>e", "a & b | c << d >> e"},
            {"(a&b)~c", "(a & b) ~ c"},
            {"a&(b~c)", "a & (b ~ c)"},
            {"a<<(b+c)", "a << (b + c)"},
        };

        long version = 40L;
        for (String[] sample : samples) {
            String first = formatOnce(sample[0], version++);
            assertEquals(sample[1] + "\n", first, sample[0]);
            ProtosStaticParseResult reparsed =
                    new ProtosStaticAnalysisCore()
                            .parse(new ProtosDocumentSnapshot(
                                    "memory:i137-bitwise-reparse",
                                    version++,
                                    first));
            assertInstanceOf(
                    ProtosStaticParseResult.Parsed.class, reparsed, sample[0]);
            assertEquals(first, formatOnce(first, version++), sample[0]);
        }

        String continued = formatOnce("a ~\n    b", version++);
        ProtosSourceLayoutView continuedView =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(new ProtosDocumentSnapshot(
                                "memory:i137-bitwise-continuation",
                                version++,
                                continued));
        assertEquals(1, continuedView.program().expressions().size());
        assertInstanceOf(
                com.guillermomolina.protos.parser.ast.SurfaceBinary.class,
                continuedView.program().expressions().get(0));
        assertEquals(continued, formatOnce(continued, version++));
    }

    @Test
    void preservesContextualReturnSpellingAndMeaning()
            throws Exception {
        String[][] samples = {
            {"return  x", "return x"},
            {"return a+b", "return a + b"},
            {"return(x)", "return (x)"},
            {"return -x", "return -x"},
            {"return ~ x", "return ~x"},
            {"return[0]", "return [0]"},
            {"return {a:1}", "return { a: 1 }"},
            {"^ a+b", "^a + b"},
            {"(return)(x)", "(return)(x)"},
            {"(return) - x", "(return) - x"},
            {"(return)[0]", "(return)[0]"},
            {"return(x,y)", "return(x, y)"},
            {"return[0]=x", "return[0] = x"},
            {"return[0].name:x", "return[0].name: x"},
            {"f:()=>{return 1\n// done\nnext()}",
                "f: () => {\n    return 1\n    // done\n    next()\n}"},
            {"return\ncalculate()", "return\ncalculate()"},
            {"return /* note */ value", "return /* note */ value"},
            {"return  /* first\n   second */  value",
                "return /* first\n   second */ value"},
            {"return // note\nnext()", "return // note\nnext()"},
            {"^ /* note */ value", "^ /* note */ value"},
            {"^ /* first\n   second */value", "^ /* first\n   second */ value"},
            {"^value // note\nnext()", "^value // note\nnext()"},
            {"f:()=>{return /* early */ 1\nnext()}",
                "f: () => {\n    return /* early */ 1\n    next()\n}"},
        };

        long version = 60L;
        for (String[] sample : samples) {
            String first = formatOnce(sample[0], version++);
            assertEquals(sample[1] + "\n", first, sample[0]);
            assertEquals(
                    canonicalOf(sample[0], version++),
                    canonicalOf(first, version++),
                    sample[0]);
            assertEquals(first, formatOnce(first, version++), sample[0]);
        }
    }

    @Test
    void bug032PreservesCombinedCaretCommentsAndReturnNewlineRule()
            throws Exception {
        String[] samples = {
            "^ // after caret\n// on own line\n42",
            "^\n// first\n\n// second\n42",
            "^ /* embedded */ // end of line\n42",
            "^\n\n/* first\n second */\n\n42",
            "^ /* first */\n// second\n42"
        };

        long version = 3300L;

        for (String source : samples) {
            String formatted = formatOnce(source, version++);

            assertEquals(source + "\n", formatted, source);
            assertEquals(
                    canonicalOf(source, version++),
                    canonicalOf(formatted, version++),
                    source);
            assertEquals(
                    formatted,
                    formatOnce(formatted, version++),
                    source);
        }

        for (String source : List.of(
                "return\n42",
                "return // separate expression\n42")) {
            ProtosSourceLayoutView view =
                    new ProtosStaticAnalysisCore()
                            .sourceLayout(
                                    new ProtosDocumentSnapshot(
                                            "memory:bug032-return-newline",
                                            version++,
                                            source));

            assertEquals(2, view.program().expressions().size(), source);
            assertInstanceOf(
                    com.guillermomolina.protos.parser.ast.SurfaceName.class,
                    view.program().expressions().get(0),
                    source);

            String formatted = formatOnce(source, version++);

            assertEquals(
                    canonicalOf(source, version++),
                    canonicalOf(formatted, version++),
                    source);
            assertEquals(
                    formatted,
                    formatOnce(formatted, version++),
                    source);
        }
    }

    private static String canonicalOf(String source, long version)
            throws ReflectiveOperationException {
        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:i138-return-semantic",
                                        version,
                                        source));
        return canonicalSemanticProjection(
                new com.guillermomolina.protos.semantic.Canonicalizer()
                        .canonicalize(view.program()));
    }

    private static String formatOnce(String source, long version)
            throws Exception {
        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-i117b",
                                        version,
                                        source));
        return ProtosFormatterToolBootstrap.formatStructural(
                CORE,
                TOOL_ROOT,
                new ProtosStandardLibraryModuleResolver(
                        STANDARD_LIBRARY),
                view,
                ignored -> {},
                runtimeHost);
    }

    @Test
    void bug032FormatsCaretOperandCommentsWithoutChangingSemantics()
            throws Exception {
        String[][] samples = {
            {"^\n// own-line comment\n42", "// own-line comment"},
            {"^ // end-of-line comment\n42", "// end-of-line comment"},
            {"^\n/* own-line block */\n42", "/* own-line block */"},
            {"^\n/* first line\n second line */\n42",
                "/* first line\n second line */"},
            {"^\n\n// separated comment\n\n42",
                "// separated comment"},
            {"^ /* embedded */ 42", "/* embedded */"},
            {"return /* embedded */ 42", "/* embedded */"},
            {"f:()=>{\n^\n// keep return comment\n42\n}",
                "// keep return comment"}
        };

        long version = 3200L;

        for (String[] sample : samples) {
            String source = sample[0];

            String formatted = formatOnce(source, version++);

            assertTrue(
                    formatted.contains(sample[1]),
                    () -> "Comment was lost: " + source);

            assertEquals(
                    canonicalOf(source, version++),
                    canonicalOf(formatted, version++),
                    source);

            assertEquals(
                    formatted,
                    formatOnce(formatted, version++),
                    source);
        }
    }

    @Test
    void preservesEmbeddedBetweenTokensComment()
            throws Exception {
        String source =
                "foo /* embedded  keep\ttext */+bar";

        String expected =
                "foo /* embedded  keep\ttext */ + bar";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-embedded-comment",
                                        10L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((expected) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void collapsesBlankLineRunsToExactlyOne()
            throws Exception {
        String source =
                "a\n"
                        + "\n"
                        + "\n"
                        + "b\n"
                        + "c";

        String expected =
                "a\n"
                        + "\n"
                        + "b\n"
                        + "c";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-blank-lines",
                                        11L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((expected) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void preservesBlankLineSideAroundOwnLineComments()
            throws Exception {
        String source =
                "a\n"
                        + "\n"
                        + "// before\n"
                        + "b\n"
                        + "// after\n"
                        + "\n"
                        + "c\n"
                        + "\n"
                        + "// both\n"
                        + "\n"
                        + "d";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-comment-blank-lines",
                                        12L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((source) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void canonicalizesWholeDocumentWhitespaceAndFinalLf()
            throws Exception {
        String lexical =
                "\"\"\"alpha\r\n\tbeta\n  gamma\"\"\"";

        String source =
                "\r\n"
                        + "   \t\r\n"
                        + "text:" + lexical + " \t\r\n"
                        + "\r\n"
                        + "\r\n"
                        + "value:2\t \r\n"
                        + "   \t\r\n"
                        + "\r\n";

        String expected =
                "text: " + lexical + "\n"
                        + "\n"
                        + "value: 2";

        ProtosSourceLayoutView view =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-document-contract",
                                        13L,
                                        source));

        AtomicReference<ProtosProcessRuntime> observed =
                new AtomicReference<>();

        String formatted =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        view,
                        observed::set,
                        runtimeHost);

        assertEquals((expected) + "\n", formatted);

        ProtosProcessRuntime process = observed.get();

        assertNotNull(process);
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
        assertTrue(
                process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void reparsesDeterministicallyAndIsExactlyIdempotent()
            throws Exception {
        String source =
                "alpha:1\n"
                        + "\n"
                        + "// keep  text\t\n"
                        + "beta:foo /* embedded */+bar\n"
                        + "gamma:[1,2,3]\n"
                        + "delta:(x)=>x+1";

        ProtosSourceLayoutView originalView =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-correctness-original",
                                        14L,
                                        source));

        String first =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        originalView,
                        ignored -> {},
                        runtimeHost);

        String repeated =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        originalView,
                        ignored -> {},
                        runtimeHost);

        assertEquals(first, repeated);
        assertTrue(first.endsWith("\n"));

        ProtosSourceLayoutView formattedView =
                new ProtosStaticAnalysisCore()
                        .sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:tool010-correctness-formatted",
                                        15L,
                                        first));

        String second =
                ProtosFormatterToolBootstrap.formatStructural(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        formattedView,
                        ignored -> {},
                        runtimeHost);

        assertEquals(first, second);
    }

    @Test
    void preservesPositionIndependentSourceProjection()
            throws Exception {
        String[] sources = {
            "group:(1+2)\n"
                    + "array:[1_000,0xFF]\n"
                    + "mapping:%{\"a\":\"\\\\n\"}\n"
                    + "single:'a'\n"
                    + "double:\"a\"\n"
                    + "unicodeEscape:\"\\\\u{0A}\"\n"
                    + "café:2E3",

            "bare:x=>x+1\n"
                    + "paren:(y)=>y*2\n"
                    + "block:(z)=>{z}",

            "a:1 // eol  keep\ttext\n"
                    + "// own  keep\ttext\n"
                    + "b:2",

            "foo /* embedded  keep\ttext */+bar",

            "foo() /* comment\n"
                    + "still  comment\t*/ {body()}",

            "/* probe */\nx:10\n/* footer */",

            "/* first */\n/* second */\n\nx:10"
        };

        long version = 20L;

        for (String source : sources) {
            ProtosSourceLayoutView originalView =
                    new ProtosStaticAnalysisCore()
                            .sourceLayout(
                                    new ProtosDocumentSnapshot(
                                            "memory:tool010-preservation-original",
                                            version++,
                                            source));

            ProtosSourceLayoutView.SourcePreservationProjection before =
                    originalView.preservationProjection();

            String formatted =
                    ProtosFormatterToolBootstrap.formatStructural(
                            CORE,
                            TOOL_ROOT,
                            new ProtosStandardLibraryModuleResolver(
                                    STANDARD_LIBRARY),
                            originalView,
                            ignored -> {},
                            runtimeHost);

            ProtosSourceLayoutView formattedView =
                    new ProtosStaticAnalysisCore()
                            .sourceLayout(
                                    new ProtosDocumentSnapshot(
                                            "memory:tool010-preservation-formatted",
                                            version++,
                                            formatted));

            ProtosSourceLayoutView.SourcePreservationProjection after =
                    formattedView.preservationProjection();

            assertSourcePreservationInvariants(
                    before,
                    after,
                    source,
                    formatted);
        }
    }

    @Test
    void bug040PreservesMapEntryBoundaries() throws Exception {
        String[] sources = {
            "%{\n"
                        + "    0: 1 // next\n"
                        + "    1: 2\n"
                        + "}",
            "%{\n"
                        + "    0: 1\n"
                        + "    // between\n"
                        + "    1: 2\n"
                        + "}",
            "%{\n"
                        + "    0: 1 // trailing\n"
                        + "    // first\n"
                        + "\n"
                        + "    // second\n"
                        + "    1: 2\n"
                        + "\n"
                        + "    2: 3\n"
                        + "}",
            "%{\n"
                        + "    0: 1\n"
                        + "    // repeated\n"
                        + "    1: 2\n"
                        + "    // repeated\n"
                        + "    2: 3\n"
                        + "}",
            "%{\n"
                        + "    0: 1\n"
                        + "\n"
                        + "    1: 2\n"
                        + "}",
            "%{ 0: 1; 1: 2; 2: 3 }"
        };

        for (String source : sources) {
            assertBoundaryPreservation(source, source + "\n");
        }
    }

    @Test
    void bug040PreservesObjectItemBoundaries() throws Exception {
        String[] sources = {
            "{\n"
                        + "    first: 1 // next\n"
                        + "    second: 2\n"
                        + "}",
            "{\n"
                        + "    first: 1\n"
                        + "    // between\n"
                        + "    second: 2\n"
                        + "}",
            "{\n"
                        + "    first: 1 // trailing\n"
                        + "    // first\n"
                        + "\n"
                        + "    // second\n"
                        + "    second: 2\n"
                        + "\n"
                        + "    third: 3\n"
                        + "}",
            "{\n"
                        + "    first: 1\n"
                        + "    // repeated\n"
                        + "    second: 2\n"
                        + "    // repeated\n"
                        + "    third: 3\n"
                        + "}",
            "{\n"
                        + "    first: 1\n"
                        + "\n"
                        + "    second: 2\n"
                        + "}",
            "{ first: 1; second: 2; third: 3 }",
            "{\n"
                        + "    first: 1 // before composition\n"
                        + "    // composition\n"
                        + "    ...base\n"
                        + "    // after composition\n"
                        + "    second: 2\n"
                        + "}"
        };

        for (String source : sources) {
            assertBoundaryPreservation(source, source + "\n");
        }
    }

    @Test
    void bug040PreservesNestedStructuralAttachments() throws Exception {
        String[] sources = {
            "{\n"
                        + "    mapping: %{\n"
                        + "        0: {\n"
                        + "            first: 1\n"
                        + "            // repeated\n"
                        + "            second: 2\n"
                        + "        } // trailing\n"
                        + "        // repeated\n"
                        + "\n"
                        + "        1: 2\n"
                        + "    }\n"
                        + "    // repeated\n"
                        + "    action: () => {\n"
                        + "        mapping: %{\n"
                        + "            0: 1\n"
                        + "            // repeated\n"
                        + "            1: 2\n"
                        + "        }\n"
                        + "        // repeated\n"
                        + "        mapping\n"
                        + "    }\n"
                        + "}"
        };

        for (String source : sources) {
            assertBoundaryPreservation(source, source + "\n");
        }
    }

    @Test
    void bug040CountsOnlySelectedRenderingsWhenWrapping() throws Exception {
        String[] sources = {
            "consume(\n"
                        + "    %{\n"
                        + "        0: 1\n"
                        + "        // repeated\n"
                        + "        1: 2\n"
                        + "    }\n"
                        + ")",
            "[\n"
                        + "    {\n"
                        + "        first: 1\n"
                        + "        // repeated\n"
                        + "        second: 2\n"
                        + "    }\n"
                        + "]",
            "(\n"
                        + "    value: %{\n"
                        + "        0: 1\n"
                        + "        // repeated\n"
                        + "        1: 2\n"
                        + "    }\n"
                        + ") => value"
        };
        for (String source : sources) {
            assertBoundaryPreservation(source, source + "\n");
        }
    }

    @Test
    void bug040KeepsInlineFormattingPolicy() throws Exception {
        assertBoundaryPreservation(
                "%{0:1;1:2;2:3}",
                "%{ 0: 1; 1: 2; 2: 3 }\n");
        assertBoundaryPreservation(
                "{first:1;second:2;third:3}",
                "{ first: 1; second: 2; third: 3 }\n");
    }

    @Test
    void bug040RejectsAdmittedCommentsWithoutARenderingOperation() {
        // Parameter attachments are not traversed by the embedded-comment
        // renderer. Identical text inside a String is never consumption.
        for (String source : List.of(
                "(x /* unconsumed */) => x",
                "(x /* unconsumed */) => \"/* unconsumed */\"")) {
            var view = new ProtosStaticAnalysisCore().sourceLayout(
                    new ProtosDocumentSnapshot("memory:bug040-unconsumed", 4000L, source));
            assertEquals(1, view.preservationProjection().comments().size());
            var comment = view.preservationProjection().comments().get(0);
            assertEquals(ProtosSourceLayoutView.CommentBoundaryKind.NONE,
                    comment.boundaryKind());
            assertEquals(ProtosSourceLayoutView.CommentPlacement.EMBEDDED_BETWEEN_TOKENS,
                    comment.placement());
            assertTrue(comment.preceding().isPresent());
            org.junit.jupiter.api.Assertions.assertThrows(
                    java.io.IOException.class,
                    () -> formatOnce(source, 4000L),
                    source);
        }
    }

    private void assertBoundaryPreservation(String source, String expected)
            throws Exception {
        ProtosStaticAnalysisCore analysis = new ProtosStaticAnalysisCore();
        ProtosDocumentSnapshot original =
                new ProtosDocumentSnapshot("memory:bug040-original", 4001L, source);
        assertInstanceOf(ProtosStaticParseResult.Parsed.class, analysis.parse(original));
        String formatted = formatOnce(source, 4002L);
        assertEquals(expected, formatted, source);
        ProtosDocumentSnapshot candidate =
                new ProtosDocumentSnapshot("memory:bug040-formatted", 4003L, formatted);
        assertInstanceOf(ProtosStaticParseResult.Parsed.class, analysis.parse(candidate));
        assertEquals(canonicalOf(source, 4004L), canonicalOf(formatted, 4005L), source);
        assertSourcePreservationInvariants(
                analysis.sourceLayout(original).preservationProjection(),
                analysis.sourceLayout(candidate).preservationProjection(),
                source,
                formatted);
        assertEquals(formatted, formatOnce(formatted, 4006L), source);
    }

    private static void assertSourcePreservationInvariants(
            ProtosSourceLayoutView.SourcePreservationProjection before,
            ProtosSourceLayoutView.SourcePreservationProjection after,
            String source,
            String formatted) {
        String context =
                "source-preservation invariant changed"
                        + "\nSOURCE:\n"
                        + source
                        + "\nFORMATTED:\n"
                        + formatted;

        assertEquals(
                before.tokens()
                        .stream()
                        .filter(
                                token ->
                                        !"NEWLINE".equals(
                                                token.type().name()))
                        .toList(),
                after.tokens()
                        .stream()
                        .filter(
                                token ->
                                        !"NEWLINE".equals(
                                                token.type().name()))
                        .toList(),
                context);

        assertEquals(
                before.comments().size(),
                after.comments().size(),
                context);

        for (int index = 0;
                index < before.comments().size();
                index++) {
            ProtosSourceLayoutView.CommentProjection left =
                    before.comments().get(index);
            ProtosSourceLayoutView.CommentProjection right =
                    after.comments().get(index);

            assertEquals(
                    left.kind(),
                    right.kind(),
                    context);
            assertEquals(
                    left.rawText(),
                    right.rawText(),
                    context);
            assertEquals(
                    left.placement(),
                    right.placement(),
                    context);
            assertEquals(
                    left.preceding(),
                    right.preceding(),
                    context);
            assertEquals(
                    left.following(),
                    right.following(),
                    context);
            assertEquals(
                    left.boundaryKind(),
                    right.boundaryKind(),
                    context);
            assertEquals(
                    left.sequenceSeparatorKind(),
                    right.sequenceSeparatorKind(),
                    context);
            // The line preceding an end-of-line comment belongs to the
            // preceding expression, not to this comment's boundary.
            if (left.placement() == ProtosSourceLayoutView.CommentPlacement.OWN_LINE) {
                assertEquals(left.blankLineBefore(), right.blankLineBefore(), context);
            }
            assertEquals(left.blankLineAfter(), right.blankLineAfter(), context);
        }

        assertEquals(
                before.structuralForms(),
                after.structuralForms(),
                context);

        assertEquals(
                before.sequenceSeparators().size(),
                after.sequenceSeparators().size(),
                context);

        for (int index = 0;
                index < before.sequenceSeparators().size();
                index++) {
            ProtosSourceLayoutView.SequenceSeparatorProjection left =
                    before.sequenceSeparators().get(index);
            ProtosSourceLayoutView.SequenceSeparatorProjection right =
                    after.sequenceSeparators().get(index);

            assertEquals(
                    left.context(),
                    right.context(),
                    context);
            assertEquals(
                    left.preceding(),
                    right.preceding(),
                    context);
            assertEquals(
                    left.kind(),
                    right.kind(),
                    context);
            assertEquals(
                    left.following(),
                    right.following(),
                    context);
            assertEquals(left.blankLine(), right.blankLine(), context);
        }

        assertEquals(
                before.closureForms(),
                after.closureForms(),
                context);

        assertEquals(
                before.trailingClosures(),
                after.trailingClosures(),
                context);

        assertEquals(
                before.contextualElses(),
                after.contextualElses(),
                context);
    }

    @Test
    void preservesCanonicalSemanticAstWithoutExecutingSource()
            throws Exception {
        String[] sources = {
            "group:(1+2)\n"
                    + "array:[1_000,0xFF]\n"
                    + "mapping:%{\"a\":\"\\\\n\"}\n"
                    + "café:2E3",

            "bare:x=>x+1\n"
                    + "paren:(y)=>y*2\n"
                    + "block:(z)=>{z}",

            "dog:animal{\n"
                    + "name:\"Rex\"\n"
                    + "age:2\n"
                    + "}",

            "a:1;b:2",

            "foo /* embedded */+bar",

            "foo() /* trailing\n"
                    + "comment */ {body()}",

            "/* probe */\nx:10\n/* footer */",

            "/* first */\n/* second */\n\nx:10"
        };

        long version = 40L;

        for (String source : sources) {
            ProtosSourceLayoutView originalView =
                    new ProtosStaticAnalysisCore()
                            .sourceLayout(
                                    new ProtosDocumentSnapshot(
                                            "memory:tool010-semantic-original",
                                            version++,
                                            source));

            String before =
                    canonicalSemanticProjection(
                            new com.guillermomolina.protos.semantic
                                    .Canonicalizer()
                                    .canonicalize(
                                            originalView.program()));

            String formatted =
                    ProtosFormatterToolBootstrap.formatStructural(
                            CORE,
                            TOOL_ROOT,
                            new ProtosStandardLibraryModuleResolver(
                                    STANDARD_LIBRARY),
                            originalView,
                            ignored -> {},
                            runtimeHost);

            ProtosSourceLayoutView formattedView =
                    new ProtosStaticAnalysisCore()
                            .sourceLayout(
                                    new ProtosDocumentSnapshot(
                                            "memory:tool010-semantic-formatted",
                                            version++,
                                            formatted));

            String after =
                    canonicalSemanticProjection(
                            new com.guillermomolina.protos.semantic
                                    .Canonicalizer()
                                    .canonicalize(
                                            formattedView.program()));

            assertEquals(
                    before,
                    after,
                    () ->
                            "canonical semantic AST changed"
                                    + "\nSOURCE:\n"
                                    + source
                                    + "\nFORMATTED:\n"
                                    + formatted);
        }
    }

    private static String canonicalSemanticProjection(
            Object value)
            throws ReflectiveOperationException {
        if (value == null) {
            return "null";
        }

        if (value instanceof String string) {
            return "string("
                    + string.length()
                    + "):"
                    + string;
        }

        if (value instanceof Boolean
                || value instanceof Number) {
            return value.getClass().getName()
                    + ":"
                    + value;
        }

        if (value instanceof Enum<?> enumValue) {
            return "enum:"
                    + enumValue.getDeclaringClass().getName()
                    + ":"
                    + enumValue.name();
        }

        if (value instanceof java.util.Optional<?> optional) {
            if (optional.isEmpty()) {
                return "optional:empty";
            }

            return "optional:"
                    + canonicalSemanticProjection(
                            optional.orElseThrow());
        }

        if (value instanceof java.util.List<?> list) {
            StringBuilder result =
                    new StringBuilder("list[");

            for (Object element : list) {
                String projected =
                        canonicalSemanticProjection(
                                element);

                result.append(projected.length())
                        .append(':')
                        .append(projected);
            }

            return result.append(']')
                    .toString();
        }

        Class<?> type = value.getClass();

        if (type.isRecord()) {
            StringBuilder result =
                    new StringBuilder("record:")
                            .append(type.getName())
                            .append('{');

            for (java.lang.reflect.RecordComponent component
                    : type.getRecordComponents()) {
                if ("span".equals(component.getName())
                        || component.getType().getName().equals(
                                "com.guillermomolina.protos.source."
                                        + "SourceSpan")) {
                    continue;
                }

                Object componentValue =
                        component.getAccessor()
                                .invoke(value);

                String projected =
                        canonicalSemanticProjection(
                                componentValue);

                result.append(component.getName())
                        .append('=')
                        .append(projected.length())
                        .append(':')
                        .append(projected)
                        .append(';');
            }

            return result.append('}')
                    .toString();
        }

        throw new AssertionError(
                "Unsupported canonical semantic value: "
                        + type.getName());
    }

    @Test
    void wholeDocumentFormattingFailsClosedForInvalidSource()
            throws Exception {
        AtomicReference<ProtosProcessRuntime> validProcess =
                new AtomicReference<>();

        ProtosWholeDocumentFormatter.Result valid =
                ProtosWholeDocumentFormatter.format(
                        CORE,
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY),
                        new ProtosDocumentSnapshot(
                                "memory:tool010-whole-document-valid",
                                60L,
                                "value:1"),
                        validProcess::set,
                        runtimeHost);

        assertEquals(
                ProtosWholeDocumentFormatter.Status.SUCCESS,
                valid.status());
        assertEquals(
                "value: 1\n",
                valid.source());
        assertTrue(
                valid.reason().isEmpty());
        assertNotNull(
                validProcess.get());

        String[] rejected = {
            "foo(",
            "value: )"
        };

        long version = 61L;

        for (String source : rejected) {
            AtomicReference<ProtosProcessRuntime> observed =
                    new AtomicReference<>();

            ProtosWholeDocumentFormatter.Result result =
                    ProtosWholeDocumentFormatter.format(
                            CORE,
                            TOOL_ROOT,
                            new ProtosStandardLibraryModuleResolver(
                                    STANDARD_LIBRARY),
                            new ProtosDocumentSnapshot(
                                    "memory:tool010-whole-document-invalid",
                                    version++,
                                    source),
                            observed::set,
                            runtimeHost);

            assertEquals(
                    ProtosWholeDocumentFormatter.Status.FAILURE,
                    result.status());
            assertEquals(
                    source,
                    result.source());
            assertTrue(
                    result.reason().isPresent());

            org.junit.jupiter.api.Assertions.assertNull(
                    observed.get());
        }
    }

    // Guest-policy unit tests: these invoke the production Protos comparator
    // with two independently bridged layouts, not whole-document acceptance.
    @Test
    void bug040GuestPolicyAcceptsIndependentLayoutsAndShiftedOffsets() throws Exception {
        String source = "{\n"
                + "    mapping: %{\n"
                + "        0: { first: 1\n// repeated\nsecond: 2 }\n"
                + "        // repeated\n\n        1: 2\n    }\n"
                + "    // repeated\n    last: 3\n}";
        assertGuestPreservation(source, source, true);
        assertGuestPreservation("Error().signal()", "Error().signal()", true);
        assertGuestPreservation(source, "\n\n    " + source, true);
        assertGuestPreservation("/* prefix */\na:1\n/* suffix */",
                "/* prefix */\na:1\n/* suffix */", true);
    }

    @Test
    void bug040GuestPolicyAcceptsExistingFormattingTransformations() throws Exception {
        for (String source : List.of(
                "%{0:1 // trailing\n// first\n\n// second\n1:2\n\n2:3}",
                "{first:1\n// repeated\nsecond:2\n// repeated\nthird:3}",
                "consume(%{0:1\n// nested\n1:2})",
                "a:1\n\n\n\nb:2",
                "f:(x)=>x+1",
                "foo /* embedded */+bar",
                "foo() /* trailing */ {body()}",
                "if(ok){one()}else{two()}")) {
            assertGuestPreservation(source, formatOnce(source, 4100L), true);
        }
    }

    @Test
    void bug040GuestPolicyRejectsCommentLossAndRelocation() throws Exception {
        for (boolean map : List.of(true, false)) {
            String first = map ? "0: 1" : "first: 1";
            String second = map ? "1: 2" : "second: 2";
            String third = map ? "2: 3" : "third: 3";
            String source = (map ? "%{" : "{") + "\n" + first
                    + " // first\n// second\n" + second + "\n" + third + "\n}";
            String[] candidates = {
                source.replace(" // first", ""),
                source.replace("// second", "// second\n// second"),
                source.replace("// first", "// swapped")
                        .replace("// second", "// first").replace("// swapped", "// second"),
                source.replace("// second", "// changed"),
                source.replace(" // first", "\n// first"),
                source.replace("// second\n", "")
                        .replace(second + "\n", second + "\n// second\n"),
                source.replace(" // first", "")
                        .replace(first, first.substring(0, first.length() - 1)
                                + "\"// first\""),
                source.replace("// second\n", "// second\n\n")
            };
            for (String candidate : candidates) {
                assertGuestPreservation(source, candidate, false);
            }
        }
        assertGuestPreservation(
                "%{0:1\n// repeated\n1:2\n// repeated\n2:3}",
                "%{0:1\n1:2\n// repeated\n// repeated\n2:3}", false);
    }

    @Test
    void bug040GuestPolicyRejectsSeparatorAndTokenChanges() throws Exception {
        assertGuestPreservation("%{0:1;1:2}", "%{0:1\n1:2}", false);
        assertGuestPreservation("{first:1\nsecond:2}", "{first:1\n\nsecond:2}", false);
        assertGuestPreservation("value:1", "value:2", false);
        String source = "%{0:1\n1:2}";
        for (String field : List.of(
                "this.context: \"OBJECT_BODY\"",
                "preceding: candidateLayout.sequenceSeparators[0].following",
                "following: candidateLayout.sequenceSeparators[0].preceding",
                "kind: \"SEMICOLON\"", "blankLine: true")) {
            assertGuestPolicy(source, source,
                    "Preservation.equivalent(originalLayout, candidateLayout {\n"
                            + "sequenceSeparators: [(candidateLayout.sequenceSeparators[0]) { "
                            + field + " }]\n})", false);
        }
    }

    @Test
    void bug040GuestPolicyComparesEveryCommentFieldWithExistingBlankLineExceptions()
            throws Exception {
        String source = "%{0:1 // trailing\n1:2}";
        for (String field : List.of(
                "kind: \"BLOCK_COMMENT\"", "rawText: \"// changed\"",
                "placement: \"OWN_LINE\"", "preceding: null", "following: null",
                "boundaryKind: \"NONE\"; sequenceSeparatorKind: null",
                "sequenceSeparatorKind: \"SEMICOLON\"", "blankLineAfter: true")) {
            assertGuestPolicy(source, source,
                    "Preservation.equivalent(originalLayout, candidateLayout {\n"
                            + "comments: [(candidateLayout.comments[0]) { " + field + " }]\n})",
                    false);
        }
        for (String placementSource : List.of(
                source, "foo /* embedded */+bar", "/* prefix */ value:1")) {
            assertGuestPolicy(placementSource, placementSource,
                    "Preservation.equivalent(originalLayout, candidateLayout {\n"
                            + "comments: [(candidateLayout.comments[0]) {\n"
                            + "blankLineBefore: !candidateLayout.comments[0].blankLineBefore\n"
                            + "}]\n})", true);
        }
        String ownLine = "%{0:1\n// own\n1:2}";
        assertGuestPolicy(ownLine, ownLine,
                "Preservation.equivalent(originalLayout, candidateLayout {\n"
                        + "comments: [(candidateLayout.comments[0]) { blankLineBefore: true }]\n})",
                false);
    }

    @Test
    void bug040GuestPolicyComparesPathsStructurallyAndHandlesAbsence() throws Exception {
        String source = "%{0:{first:1\n// nested\nsecond:2}\n1:2}";
        String path = "originalLayout.comments[0].preceding";
        String candidatePath = "candidateLayout.comments[0].preceding";
        assertGuestPolicy(source, source,
                "Preservation.structuralPathEquivalent(" + path + ", " + candidatePath + ")",
                true);
        assertGuestPolicy(source, source,
                "Preservation.structuralPathEquivalent(null, null)", true);
        assertGuestPolicy(source, source,
                "Preservation.structuralPathEquivalent(null, " + candidatePath + ")", false);
        assertGuestPolicy(source, source,
                "Preservation.structuralPathEquivalent(" + path + ", null)", false);
        for (String patch : List.of("root: \"other\"", "steps: []")) {
            assertGuestPolicy(source, source,
                    "Preservation.structuralPathEquivalent(" + path + ", "
                            + candidatePath + " { " + patch + " })", false);
        }
        String copiedSteps = "modifiedSteps: [..." + candidatePath + ".steps]\n";
        assertGuestPolicy(source, source,
                copiedSteps + "firstStep: modifiedSteps[0]\n"
                        + "modifiedSteps[0] = modifiedSteps[1]\n"
                        + "modifiedSteps[1] = firstStep\n"
                        + "Preservation.structuralPathEquivalent(" + path + ", "
                        + candidatePath + " { steps: modifiedSteps })", false);
        for (String stepPatch : List.of("role: \"other\"", "index: 42")) {
            assertGuestPolicy(source, source,
                    copiedSteps + "last: modifiedSteps.size() - 1\n"
                            + "modifiedSteps[last] = (modifiedSteps[last]) { " + stepPatch + " }\n"
                            + "Preservation.structuralPathEquivalent(" + path + ", "
                            + candidatePath + " { steps: modifiedSteps })", false);
        }
    }

    @Test
    void bug040GuestPolicyComparesRemainingOrderedProjectionFacts() throws Exception {
        String source = "f:x=>x\nfoo(){one()}\nif(ok){one()}else{two()}";
        String[][] checks = {
            {"structuralFormsEquivalent", "structuralForms", "kind: \"GROUP\""},
            {"structuralFormsEquivalent", "structuralForms", "path: {root: \"other\"; steps: []}"},
            {"closureFormsEquivalent", "closureForms", "form: \"PARENTHESIZED\""},
            {"closureFormsEquivalent", "closureForms", "closure: {root: \"other\"; steps: []}"},
            {"trailingClosuresEquivalent", "trailingClosures", "call: {root: \"other\"; steps: []}"},
            {"trailingClosuresEquivalent", "trailingClosures", "closure: {root: \"other\"; steps: []}"},
            {"contextualElsesEquivalent", "contextualElses", "conditional: {root: \"other\"; steps: []}"},
            {"contextualElsesEquivalent", "contextualElses", "falseBranch: {root: \"other\"; steps: []}"},
            {"contextualElsesEquivalent", "contextualElses", "elseToken: 999"},
            {"tokensEquivalent", "tokens", "type: \"STRING\""},
            {"tokensEquivalent", "tokens", "rawText: \"different\""}
        };
        assertGuestPreservation(source, source, true);
        for (String[] check : checks) {
            String original = "originalLayout." + check[1] + "[0]";
            String candidate = "candidateLayout." + check[1] + "[0]";
            assertGuestPolicy(source, source,
                    "Preservation." + check[0] + "([" + original + "], [("
                            + candidate + ") { " + check[2] + " }])", false);
        }
        for (String field : List.of("structuralForms", "closureForms",
                "trailingClosures", "contextualElses", "tokens")) {
            assertGuestPolicy(source, source,
                    "Preservation.equivalent(originalLayout, candidateLayout { "
                            + field + ": [] })", false);
        }
    }

    @Test
    void bug040GuestPolicyPreservesOrderingOfEveryProjectionList() throws Exception {
        String source = "f:x=>x\ng:y=>y\nfoo(){one()}\nbar(){two()}\n"
                + "if(ok){one()}else{two()}\nif(other){three()}else{four()}";
        String[][] checks = {
            {"structuralFormsEquivalent", "structuralForms"},
            {"closureFormsEquivalent", "closureForms"},
            {"trailingClosuresEquivalent", "trailingClosures"},
            {"contextualElsesEquivalent", "contextualElses"},
            {"sequenceSeparatorsEquivalent", "sequenceSeparators"},
            {"tokensEquivalent", "tokens"}
        };
        for (String[] check : checks) {
            String original = "originalLayout." + check[1];
            String candidate = "candidateLayout." + check[1];
            assertGuestPolicy(source, source,
                    "Preservation." + check[0] + "([" + original + "[0], " + original
                            + "[1]], [" + candidate + "[1], " + candidate + "[0]])", false);
        }
    }

    @Test
    void bug040GuestPolicySignalsForMissingOrUnsupportedProjectionData() throws Exception {
        String source = "%{0:1 // trailing\n1:2}";
        List<String> fields = List.of("tokens", "comments", "sequenceSeparators",
                "structuralForms", "closureForms", "trailingClosures", "contextualElses");
        for (String omitted : fields) {
            StringBuilder candidate = new StringBuilder("{\n");
            for (String field : fields) {
                if (!field.equals(omitted)) {
                    candidate.append(field).append(": candidateLayout.")
                            .append(field).append('\n');
                }
            }
            candidate.append('}');
            assertGuestPolicyFailure(source,
                    "Preservation.equivalent(originalLayout, " + candidate + ")");
        }
        for (String patch : List.of(
                "tokens: [{type: \"UNSUPPORTED\"; rawText: \"x\"}]",
                "comments: [(candidateLayout.comments[0]) { boundaryKind: \"UNSUPPORTED\" }]",
                "comments: [(candidateLayout.comments[0]) { blankLineAfter: null }]",
                "comments: %{}",
                "comments: [{kind: \"LINE_COMMENT\"; rawText: \"// trailing\"}]",
                "sequenceSeparators: [(candidateLayout.sequenceSeparators[0]) { preceding: null }]",
                "structuralForms: [(candidateLayout.structuralForms[0]) { path: {root: \"program\"} }]")) {
            assertGuestPolicyFailure(source,
                    "Preservation.equivalent(originalLayout, candidateLayout { " + patch + " })");
        }
        assertGuestPolicyFailure(source,
                "Preservation.structuralPathEquivalent({root: null; steps: []}, null)");
        assertGuestPolicyFailure(source,
                "Preservation.structuralPathEquivalent(null, {root: \"program\"; "
                        + "steps: [{role: \"expression\"; index: \"invalid\"}]})");
        // A malformed later fact must not be masked by an earlier inequality.
        assertGuestPolicyFailure(source,
                "Preservation.equivalent(originalLayout, candidateLayout {\n"
                        + "tokens: []\ncomments: [{}]\n})");
        assertGuestPolicyFailure(source,
                "Preservation.equivalent(originalLayout { tokens: [{type: \"UNSUPPORTED\"; rawText: \"x\"}] },"
                        + " candidateLayout { tokens: [{type: \"UNSUPPORTED\"; rawText: \"x\"}] })");
    }

    private static void assertGuestPreservation(String original, String candidate, boolean expected)
            throws Exception {
        assertGuestPolicy(original, candidate,
                "Preservation.equivalent(originalLayout, candidateLayout)", expected);
    }

    private static void assertGuestPolicy(
            String original, String candidate, String operation, boolean expected) throws Exception {
        ProtosExecutionOutcome outcome = guestPreservationOutcome(original, candidate, operation);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(),
                () -> "Guest preservation policy failed: " + outcome.error()
                        + "; trace=" + outcome.failureDiagnosticTrace());
        assertEquals(expected, outcome.value(), operation);
    }

    private static void assertGuestPolicyFailure(String source, String operation) throws Exception {
        ProtosExecutionOutcome outcome = guestPreservationOutcome(source, source, operation);
        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state(), operation);
        assertNotNull(outcome.error(), operation);
    }

    private static ProtosExecutionOutcome guestPreservationOutcome(
            String original, String candidate, String operation) throws Exception {
        ProtosSourceLayoutView originalView = new ProtosStaticAnalysisCore().sourceLayout(
                new ProtosDocumentSnapshot("memory:bug040-policy-original", 4200L, original));
        ProtosSourceLayoutView candidateView = new ProtosStaticAnalysisCore().sourceLayout(
                new ProtosDocumentSnapshot("memory:bug040-policy-candidate", 4201L, candidate));
        ProtosBundledToolModuleResolver resolver = new ProtosBundledToolModuleResolver(
                "formatter", TOOL_ROOT, TOOL_ROOT.resolveSibling("shared"),
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        try (ProtosHostedExecutionTestFixture fixture = ProtosHostedExecutionTestFixture.open(prelude)) {
            ProtosObjectValue before = ProtosSourceLayoutToolBridge.project(originalView, prelude);
            ProtosObjectValue after = ProtosSourceLayoutToolBridge.project(candidateView, prelude);
            assertNotSame(before, after);
            assertNotSame(slot(object(slot(before, "program")), "path"),
                    slot(object(slot(after, "program")), "path"));
            fixture.activation().context().createLocalSlot("originalLayout", before);
            fixture.activation().context().createLocalSlot("candidateLayout", after);
            return fixture.executeAsTool("formatter", "bug040-preservation-policy.protos",
                    "Preservation: import(\"./Preservation.protos\")\n" + operation);
        }
    }

    private static Object slot(
            ProtosObjectValue object,
            String name) {
        return object.readLocalSlot(name).orElseThrow();
    }

    private static ProtosObjectValue object(Object value) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                value);
    }

    private static ProtosArrayValue array(Object value) {
        return assertInstanceOf(
                ProtosArrayValue.class,
                value);
    }

    private static String string(Object value) {
        return assertInstanceOf(
                        ProtosStringValue.class,
                        value)
                .value();
    }
}
