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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.cli.ProtosCli;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * LIB025 presentation-only subdivision of the {@code protos/library/http} progress group into one
 * group per suite source, after the TOOL012 design. Case identity, selection and the leaf graph
 * are unchanged.
 */
final class ProtosTestToolLib025HttpProgressGroupingTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path HTTP_TESTS = Path.of("protos", "tests", "library", "http");
    private static final String CORPUS = "protos/corpus/library/http";
    private static final int MAX_CASES_PER_GROUP = 100;

    /** Plan-ordered suite sources and their expected progress groups. */
    private static final List<String> SOURCES =
            List.of(
                    "request-head",
                    "response-head",
                    "request-body",
                    "server-admission",
                    "server-request",
                    "server-response",
                    "server-lifecycle",
                    "host-construction",
                    "host-connection",
                    "host-lifecycle",
                    "client-admission",
                    "client-request",
                    "client-response-head",
                    "client-response-body",
                    "client-lifecycle");

    private static final String PRELUDE =
            "SuiteGraph: import(\"self:SuiteGraph\")\n"
                    + "RepositorySuite: import(\"self:RepositorySuite\")\n"
                    + "Arrays: import(\"std:collections/Array\")\n"
                    + "leaves: SuiteGraph.flattenLeaves(RepositorySuite.root)\n"
                    + "http: Arrays.filter(leaves, (leaf) => leaf.id == \"protos/library/http\")[0]\n";

    private static final Pattern TEST_SELECTOR = Pattern.compile("^    Test\\(\"([^\"]+)\",");
    private static final Pattern LISTED_CASE =
            Pattern.compile("\\{\"ref\":\"(v1\\.[0-9a-f]+)\",\"display\":\"([^\"]*)\"\\}");
    private static final Pattern GROUP_START = Pattern.compile("^\\[([^\\]]+)\\] 0/(\\d+)$");

    @Test
    void everySourceCasePathProducesExactlyItsGroup() throws Exception {
        List<String> expected = new ArrayList<>();
        StringBuilder source = new StringBuilder(PRELUDE).append("Array(");
        for (int index = 0; index < SOURCES.size(); index++) {
            String name = SOURCES.get(index);
            expected.add(group(name));
            source.append(index == 0 ? "" : ", ")
                    .append("RepositorySuite.progressGroupName(http, \"")
                    .append(casePath(name))
                    .append("\", \"any selector\")");
        }
        source.append(")");
        assertEquals(
                expected,
                strings(assertInstanceOf(ProtosArrayValue.class, completed(List.of(), source.toString()))));
    }

    @Test
    void completeCorpusAssignmentIsConsistentBoundedAndPreservesEveryCase() throws Exception {
        List<String[]> keys = corpusCaseKeys();
        assertTrue(keys.size() > MAX_CASES_PER_GROUP, "corpus must exceed one group");

        ProtosArrayValue result =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                keys,
                                PRELUDE
                                        + "assigned: RepositorySuite.progressGroupAssignment(http, keys)\n"
                                        + "names: RepositorySuite.progressGroupNames(http, keys)\n"
                                        + "owners: Arrays.map(keys, (key) => {\n"
                                        + "    (casePath, selector): key\n"
                                        + "    RepositorySuite.progressGroupName(http, casePath, selector)\n"
                                        + "})\n"
                                        + "indexed: Arrays.map(keys, (key) => {\n"
                                        + "    (casePath, selector): key\n"
                                        + "    RepositorySuite.progressGroupIndex(http, names, casePath, selector)\n"
                                        + "})\n"
                                        + "Array(assigned.names, names, owners, indexed, "
                                        + "assigned.caseGroupIndexes)"));

        List<String> names = strings(arrayAt(result, 0));
        assertEquals(names, strings(arrayAt(result, 1)));
        List<String> owners = strings(arrayAt(result, 2));
        List<Integer> indexed = integers(arrayAt(result, 3));
        List<Integer> assigned = integers(arrayAt(result, 4));

        // Index alignment with the original Cases: one group per Case, and the three
        // entry points agree.
        assertEquals(keys.size(), owners.size());
        assertEquals(keys.size(), assigned.size());
        assertEquals(indexed, assigned);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index = 0; index < keys.size(); index++) {
            String owner = owners.get(index);
            assertEquals(names.get(assigned.get(index)), owner);
            assertEquals(group(sourceName(keys.get(index)[0])), owner, keys.get(index)[1]);
            counts.merge(owner, 1, Integer::sum);
        }

        // Exactly one group per source, in plan order of first Case, no duplicates, none empty.
        List<String> expected = new ArrayList<>();
        for (String source : SOURCES) {
            expected.add(group(source));
        }
        assertEquals(expected, names);
        assertEquals(new LinkedHashSet<>(names).size(), names.size());
        assertEquals(new ArrayList<>(counts.keySet()), names);
        assertEquals(keys.size(), counts.values().stream().mapToInt(Integer::intValue).sum());
        counts.forEach(
                (name, count) ->
                        assertTrue(count <= MAX_CASES_PER_GROUP, name + " has " + count + " Cases"));
    }

    @Test
    void twoCasesOfOneSourceShareTheirGroup() throws Exception {
        ProtosArrayValue result =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                List.of(),
                                PRELUDE
                                        + "Array(RepositorySuite.progressGroupName(http, \""
                                        + casePath("server-response")
                                        + "\", \"first\"), RepositorySuite.progressGroupName(http, \""
                                        + casePath("server-response")
                                        + "\", \"second\"))"));
        assertEquals(
                List.of(group("server-response"), group("server-response")), strings(result));
    }

    @Test
    void onlyGroupsOwningRetainedCasesArePresented() throws Exception {
        List<String[]> keys =
                List.of(
                        new String[] {casePath("server-lifecycle"), "a"},
                        new String[] {casePath("request-head"), "b"},
                        new String[] {casePath("server-lifecycle"), "c"});
        ProtosArrayValue result =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                keys,
                                PRELUDE
                                        + "assigned: RepositorySuite.progressGroupAssignment(http, keys)\n"
                                        + "empty: RepositorySuite.progressGroupAssignment(http, Array())\n"
                                        + "Array(assigned.names, assigned.caseGroupIndexes, empty.names, "
                                        + "empty.caseGroupIndexes)"));
        assertEquals(
                List.of(group("server-lifecycle"), group("request-head")),
                strings(arrayAt(result, 0)));
        assertEquals(List.of(0, 1, 0), integers(arrayAt(result, 1)));
        assertEquals(List.of(), strings(arrayAt(result, 2)));
        assertEquals(List.of(), integers(arrayAt(result, 3)));
        assertTrue(arrayAt(result, 0).isFrozen());
        assertTrue(arrayAt(result, 1).isFrozen());
    }

    @Test
    void unknownAndMalformedHttpCasePathsFailClosed() throws Exception {
        for (String path :
                List.of(
                        "",
                        "http/",
                        "http/unknown.protos",
                        "request-head.protos",
                        "http/request-head",
                        "http//request-head.protos",
                        "library/http/request-head.protos",
                        "http/request-head.protos/")) {
            assertFailed(
                    PRELUDE + "RepositorySuite.progressGroupName(http, \"" + path + "\", \"t\")");
            assertFailed(
                    PRELUDE
                            + "RepositorySuite.progressGroupAssignment(http, "
                            + "Array(Array(\""
                            + path
                            + "\", \"t\")))");
        }
        assertFailed(PRELUDE + "RepositorySuite.progressGroupName(http, 5, \"t\")");
        assertFailed(
                PRELUDE
                        + "RepositorySuite.progressGroupIndex(http, Array(\""
                        + group("request-head")
                        + "\"), \""
                        + casePath("response-head")
                        + "\", \"t\")");
        // No monolithic catch-all group remains.
        assertFailed(
                PRELUDE
                        + "RepositorySuite.progressGroupIndex(http, Array(\"library/http\"), \""
                        + casePath("request-head")
                        + "\", \"t\")");
    }

    @Test
    void otherLeavesKeepTheirPreviousPolicy() throws Exception {
        ProtosArrayValue result =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                List.of(),
                                PRELUDE
                                        + "regex: Arrays.filter(leaves, (leaf) => "
                                        + "leaf.id == \"protos/library/regex\")[0]\n"
                                        + "Array(RepositorySuite.progressGroupName(regex, "
                                        + "\"regex/compile.protos\", \"t\"), "
                                        + "RepositorySuite.progressGroupNames(regex, Array())[0], "
                                        + "RepositorySuite.progressGroupName(regex, "
                                        + "\"http/request-head.protos\", \"t\"))"));
        assertEquals(
                List.of("library/regex", "library/regex", "library/regex"), strings(result));
    }

    @Test
    void listCasesPreservesTheCompleteCaseSetAndIdentities() throws Exception {
        List<String[]> listed = listCases("--directory", HTTP_TESTS.toString());
        List<String> expected = new ArrayList<>();
        for (String[] key : corpusCaseKeys()) {
            expected.add(CORPUS + ":" + key[0] + "::" + key[1]);
        }
        List<String> displayed = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        for (String[] entry : listed) {
            refs.add(entry[0]);
            displayed.add(entry[1]);
        }
        assertEquals(expected, displayed);
        assertEquals(refs.size(), new LinkedHashSet<>(refs).size());
    }

    @Test
    void fileSelectionPresentsOnlyItsOwnGroup() throws Exception {
        String file = HTTP_TESTS.resolve("response-head.protos").toString();
        int cases = listCases("--file", file).size();
        assertEquals(sourceCaseCount("response-head"), cases);

        R result = run("test", "--file", file);

        assertEquals(0, result.code(), result::err);
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put(group("response-head"), cases);
        assertEquals(expected, groupStarts(result.err()));
        assertTrue(result.err().endsWith(cases + " passed, 0 failed\n"), result::err);
    }

    // ---- oracles ----

    private static String casePath(String source) {
        return "http/" + source + ".protos";
    }

    private static String group(String source) {
        return "library/http/" + source;
    }

    private static String sourceName(String casePath) {
        return casePath.substring("http/".length(), casePath.length() - ".protos".length());
    }

    /** Array(casePath, selector) of every HTTP Case, in plan order. */
    private static List<String[]> corpusCaseKeys() throws Exception {
        List<String[]> keys = new ArrayList<>();
        for (String source : SOURCES) {
            for (String line :
                    Files.readAllLines(
                            HTTP_TESTS.resolve(source + ".protos"), StandardCharsets.UTF_8)) {
                Matcher matcher = TEST_SELECTOR.matcher(line);
                if (matcher.find()) {
                    keys.add(new String[] {casePath(source), matcher.group(1)});
                }
            }
        }
        return keys;
    }

    private static int sourceCaseCount(String source) throws Exception {
        int count = 0;
        for (String[] key : corpusCaseKeys()) {
            if (key[0].equals(casePath(source))) {
                count++;
            }
        }
        return count;
    }

    // ---- harness (as TOOL012) ----

    private static List<String[]> listCases(String... selectors) {
        String[] args = new String[selectors.length + 2];
        args[0] = "test";
        args[1] = "--list-cases";
        System.arraycopy(selectors, 0, args, 2, selectors.length);
        R listing = run(args);
        assertEquals(0, listing.code(), listing::err);
        assertEquals("", listing.err());
        List<String[]> cases = new ArrayList<>();
        Matcher matcher = LISTED_CASE.matcher(listing.out());
        while (matcher.find()) {
            cases.add(new String[] {matcher.group(1), matcher.group(2)});
        }
        return cases;
    }

    private static Map<String, Integer> groupStarts(String err) {
        Map<String, Integer> starts = new LinkedHashMap<>();
        for (String line : err.split("\n")) {
            Matcher matcher = GROUP_START.matcher(line);
            if (matcher.matches()) {
                starts.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
            }
        }
        return starts;
    }

    private static ProtosArrayValue arrayAt(ProtosArrayValue array, int index) {
        return assertInstanceOf(ProtosArrayValue.class, array.indexedAt(index));
    }

    private static List<String> strings(ProtosArrayValue array) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.indexedSize(); index++) {
            values.add(
                    assertInstanceOf(ProtosStringValue.class, array.indexedAt(index)).value());
        }
        return values;
    }

    private static List<Integer> integers(ProtosArrayValue array) {
        List<Integer> values = new ArrayList<>();
        for (int index = 0; index < array.indexedSize(); index++) {
            values.add(ProtosNumericValueSupport.exactInt(array.indexedAt(index)));
        }
        return values;
    }

    private static Object completed(List<String[]> keys, String source) throws Exception {
        ProtosPrelude prelude = newPrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        List<ProtosArrayValue> elements = new ArrayList<>(keys.size());
        for (String[] key : keys) {
            elements.add(
                    prelude.newArray(
                            List.of(new ProtosStringValue(key[0]), new ProtosStringValue(key[1]))));
        }
        activation.context().createLocalSlot("keys", prelude.newArray(elements));
        ProtosExecutionOutcome outcome = ProtosTestExecutionSupport.execute(source, activation);
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "expected completion, error=" + outcome.error());
        return outcome.value();
    }

    private static void assertFailed(String source) throws Exception {
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(source, newPrelude().newModuleActivation());
        assertEquals(
                ProtosExecutionOutcome.State.FAILED,
                outcome.state(),
                () -> "expected fail-closed result, value=" + outcome.value());
    }

    private static ProtosPrelude newPrelude() throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        return new ProtosCoreBootstrap().bootstrap(CORE, resolver);
    }

    private static R run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code =
                new ProtosCli()
                        .run(
                                args,
                                InputStream.nullInputStream(),
                                new PrintStream(out),
                                new PrintStream(err));
        return new R(
                code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record R(int code, String out, String err) {}
}
