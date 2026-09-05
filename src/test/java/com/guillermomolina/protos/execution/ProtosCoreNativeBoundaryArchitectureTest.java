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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * I018 closure guard.
 *
 * <p>The implementation may keep native standard behavior only at an explicitly audited
 * host/representation/concurrency/resource boundary. Ordinary derived Core behavior must remain
 * source-backed. This test intentionally makes expansion of the Java-native standard boundary an
 * explicit review event instead of letting it grow silently.
 */
final class ProtosCoreNativeBoundaryArchitectureTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path JAVA_ROOT =
            Path.of("src", "main", "java", "com", "guillermomolina", "protos");

    private static final Map<String, Integer> EXPECTED_NATIVE_PROVIDERS =
            Map.ofEntries(
                    Map.entry("execution/ProtosStandardMapProtocol.java", 7),
                    Map.entry("execution/ProtosStandardHashSupport.java", 3),
                    Map.entry("execution/ProtosStandardFileProtocol.java", 10),
                    Map.entry("execution/ProtosStandardFilesystemProtocol.java", 1),
                    Map.entry("execution/ProtosStandardPathProtocol.java", 6),
                    Map.entry("execution/ProtosStandardArrayProtocol.java", 5),
                    Map.entry("execution/ProtosStandardProcessArgumentsProtocol.java", 3),
                    Map.entry("execution/ProtosStandardEnvironmentProtocol.java", 3),
                    Map.entry("execution/ProtosStandardProcessStreamProtocol.java", 2),
                    Map.entry("execution/ProtosStandardBytesProtocol.java", 7),
                    Map.entry("execution/ProtosStandardByteIoProtocol.java", 12),
                    Map.entry("execution/ProtosStandardObjectProtocol.java", 2),
                    Map.entry("execution/ProtosStandardActorProtocol.java", 8),
                    Map.entry("execution/ProtosStandardIdentityMapProtocol.java", 7),
                    Map.entry("execution/ProtosStandardStringProtocol.java", 3),
                    Map.entry("execution/ProtosStandardBufferedByteIoProtocol.java", 6),
                    Map.entry("execution/ProtosStandardErrorProtocol.java", 1),
                    Map.entry("execution/ProtosStandardImportProtocol.java", 1),
                    Map.entry("execution/ProtosStandardIntegerProtocol.java", 3),
                    Map.entry("execution/ProtosStandardFutureProtocol.java", 2),
                    Map.entry("execution/ProtosStandardFloatProtocol.java", 1),
                    Map.entry("execution/ProtosStandardBooleanProtocol.java", 1),
                    Map.entry("execution/ProtosStandardNumberEqualityProtocol.java", 1),
                    Map.entry("execution/ProtosParallelRuntime.java", 2),
                    Map.entry("execution/ProtosStandardNumberOrderingProtocol.java", 1),
                    Map.entry("execution/ProtosStandardNumericConversionProtocol.java", 1));

    @Test
    void javaNativeClosureProvidersMatchTheAuditedBoundaryExactly() throws IOException {
        Map<String, Integer> actual = new TreeMap<>();
        try (Stream<Path> files = Files.walk(JAVA_ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                int count = occurrences(source, "ProtosClosureValue.nativeClosure(");
                if (count > 0) {
                    String relative =
                            JAVA_ROOT.relativize(file).toString().replace('\\', '/');
                    actual.put(relative, count);
                }
                assertFalse(
                        source.contains(
                                "import static com.guillermomolina.protos.runtime.ProtosClosureValue.nativeClosure"),
                        () -> "static nativeClosure import would evade the provider guard: " + file);
            }
        }

        assertEquals(EXPECTED_NATIVE_PROVIDERS, actual);
        assertEquals(26, actual.size());
        assertEquals(99, actual.values().stream().mapToInt(Integer::intValue).sum());

        String inventory =
                Files.readString(Path.of("docs", "project", "CORE_NATIVE_BOUNDARY.md"));
        for (String provider : EXPECTED_NATIVE_PROVIDERS.keySet()) {
            String simpleName = provider.substring(provider.lastIndexOf('/') + 1);
            assertTrue(
                    inventory.contains("`" + simpleName + "`"),
                    () -> "native provider missing from inventory: " + simpleName);
        }
    }

    @Test
    void staticCoreSurfaceKeepsOnlyTheAuditedNativeSelectors() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

        assertTrue(
                prelude.bindings().readLocalSlot("Filesystem").isEmpty(),
                "Filesystem authority must remain host-provisioned, not a Core-prelude binding");
        assertTrue(
                prelude.bindings().readLocalSlot("Environment").isEmpty(),
                "Environment is outside the required Core prelude");

        assertNativeSelectors(
                "Object",
                ProtosObjectValue.rootObject(),
                Set.of(
                        "call",
                        "identityHash",
                        "ifTrue",
                        "ifFalse",
                        "and",
                        "or",
                        "hash",
                        "future",
                        "parallel"));
        assertSourceBacked(ProtosObjectValue.rootObject(), "init");
        assertSourceBacked(ProtosObjectValue.rootObject(), "==");
        assertSourceBacked(ProtosObjectValue.rootObject(), "!=");

        assertNativeSelectors(
                "Number",
                prelude.numberPrototype(),
                Set.of("==", "<", "<=", ">", ">=", "hash"));

        assertNativeSelectors(
                "Integer",
                prelude.integerPrototype(),
                Set.of("call", "+", "-", "*", "/", "div", "mod"));
        assertSourceBacked(prelude.integerPrototype(), "negated");
        assertSourceBacked(prelude.integerPrototype(), "%");

        assertNativeSelectors(
                "Float",
                prelude.floatPrototype(),
                Set.of("call", "+", "-", "*", "/"));
        assertSourceBacked(prelude.floatPrototype(), "negated");

        for (ProtosFixedIntegerValue.Family family : ProtosFixedIntegerValue.Family.values()) {
            assertNativeSelectors(
                    family.prototypeName(),
                    prelude.fixedIntegerPrototype(family),
                    Set.of("call"));
        }

        assertNativeSelectors("Context", prelude.contextPrototype(), Set.of());
        assertNativeSelectors("Error", prelude.errorPrototype(), Set.of("signal"));
        assertNativeSelectors(
                "Array",
                prelude.arrayPrototype(),
                Set.of(
                        "call",
                        "at",
                        "atPut",
                        "size",
                        "each",
                        "parallelMap",
                        "parallelFilter",
                        "parallelFindIndex",
                        "parallelReduce",
                        "parallelSort"));
        assertNativeSelectors(
                "String",
                prelude.stringPrototype(),
                Set.of("size", "at", "+", "hash"));
        assertNativeSelectors(
                "Map",
                prelude.mapPrototype(),
                Set.of("call", "at", "atPut", "containsKey", "remove", "size", "each"));
        assertNativeSelectors(
                "IdentityMap",
                prelude.identityMapPrototype(),
                Set.of("call", "at", "atPut", "containsKey", "remove", "size", "each"));
        assertNativeSelectors(
                "Path",
                prelude.pathPrototype(),
                Set.of("relative", "rooted", "child", "parentComponent", "==", "hash"));
        assertNativeSelectors(
                "Future",
                prelude.futurePrototype(),
                Set.of("value", "cancel", "detach", "then", "all"));
        assertNativeSelectors(
                "Actor",
                ordinaryBinding(prelude, "Actor"),
                Set.of("spawn", "current"));
        assertNativeSelectors(
                "BufferedReader",
                ordinaryBinding(prelude, "BufferedReader"),
                Set.of("call", "owning"));
        assertNativeSelectors(
                "BufferedWriter",
                ordinaryBinding(prelude, "BufferedWriter"),
                Set.of("call", "owning"));
        assertNativeSelectors(
                "import",
                ordinaryBinding(prelude, "import"),
                Set.of("call"));
    }

    @Test
    void internalAndHelperBackedStandardSurfacesStayExplicit() throws Exception {
        ProtosObjectValue processArgumentsPrototype =
                ProtosStandardProcessArgumentsProtocol.createPrototype();
        assertNativeSelectors(
                "ProcessArguments",
                processArgumentsPrototype,
                Set.of("size", "at", "each"));

        ProtosObjectValue environmentPrototype =
                ProtosStandardEnvironmentProtocol.createPrototype();
        assertNativeSelectors(
                "Environment",
                environmentPrototype,
                Set.of("get", "contains", "each"));

        ProtosObjectValue standardInputPrototype =
                ProtosStandardProcessStreamProtocol.createReadablePrototype();
        ProtosObjectValue standardOutputPrototype =
                ProtosStandardProcessStreamProtocol.createWritablePrototype();
        assertNativeSelectors(
                "ProcessStandardInput",
                standardInputPrototype,
                Set.of("read"));
        assertNativeSelectors(
                "ProcessStandardOutput",
                standardOutputPrototype,
                Set.of("write"));

        ProtosObjectValue bytesPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(bytesPrototype);
        assertNativeSelectors(
                "Bytes",
                bytesPrototype,
                Set.of("call", "size", "at", "atPut", "each", "add", "removeAt", "parallelRange"));

        ProtosObjectValue actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue sendOperationPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue actorObject =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        ProtosStandardActorProtocol actorProtocol =
                new ProtosStandardActorProtocol(
                        new ProtosModuleRuntime(ProtosModuleResolver.rejecting()),
                        Runnable::run,
                        actorRefPrototype,
                        sendOperationPrototype);
        actorProtocol.installActorObject(actorObject);

        assertNativeSelectors(
                "ActorRef", actorRefPrototype, Set.of("send", "request", "stop", "termination"));
        assertNativeSelectors(
                "SendOperation", sendOperationPrototype, Set.of("cancel", "retry"));
        assertNativeSelectors("Actor", actorObject, Set.of("spawn", "current"));

        String parallel =
                Files.readString(
                        JAVA_ROOT.resolve("execution").resolve("ProtosParallelRuntime.java"));
        assertEquals(6, occurrences(parallel, "slot(p,"));
        assertEquals(4, occurrences(parallel, "slot(r,"));

        String future =
                Files.readString(
                        JAVA_ROOT.resolve("execution").resolve("ProtosStandardFutureProtocol.java"));
        assertEquals(5, occurrences(future, "slot(futurePrototype,"));

        String bootstrap =
                Files.readString(
                        JAVA_ROOT.resolve("execution").resolve("ProtosCoreBootstrap.java"));
        assertEquals(
                2,
                occurrences(
                        bootstrap,
                        "new ProtosObjectValue(ProtosObjectValue.rootObject())"),
                "Core bootstrap may allocate only its two host construction contexts directly");
        assertFalse(bootstrap.contains("preludeBindings.createLocalSlot("));
    }

    private static ProtosObjectValue ordinaryBinding(ProtosPrelude prelude, String name) {
        Object value = prelude.bindings().readLocalSlot(name).orElseThrow();
        assertTrue(value instanceof ProtosObjectValue, "standard binding is not ordinary: " + name);
        return (ProtosObjectValue) value;
    }

    private static void assertNativeSelectors(
            String name, ProtosObjectValue object, Set<String> expected) {
        Set<String> actual =
                object.localSlotsSnapshot().entrySet().stream()
                        .filter(
                                entry ->
                                        entry.getValue() instanceof ProtosClosureValue closure
                                                && closure.nativeBody().isPresent())
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet());
        assertEquals(expected, actual, "unexpected native standard surface for " + name);
    }

    private static void assertSourceBacked(ProtosObjectValue object, String selector) {
        Object value = object.readLocalSlot(selector).orElseThrow();
        assertTrue(value instanceof ProtosClosureValue, selector + " is not a Closure");
        ProtosClosureValue closure = (ProtosClosureValue) value;
        assertNotNull(closure.definition(), selector + " lost source definition provenance");
        assertTrue(
                closure.executionPlan().isPresent(),
                selector + " lost its source execution plan");
        assertTrue(
                closure.nativeBody().isEmpty(),
                selector + " regressed to native-only behavior");
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
