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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** TOOL005-A1/A2/A3A conformance for D122/D123 suite identity and D125 execution requirements. */
final class ProtosTestToolSuiteGraphTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");

    @Test
    void explicitNestedCompositionExpandsLeavesInDeclarationOrder() throws Exception {
        ProtosArrayValue flattened =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                                        + "a: SuiteGraph.leaf(\"a\", \"test/ordinary\")\n"
                                        + "b: SuiteGraph.leaf(\"b\", \"test/ordinary\")\n"
                                        + "c: SuiteGraph.leaf(\"c\", \"test/ordinary\")\n"
                                        + "d: SuiteGraph.leaf(\"d\", \"test/ordinary\")\n"
                                        + "e: SuiteGraph.leaf(\"e\", \"test/ordinary\")\n"
                                        + "f: SuiteGraph.leaf(\"f\", \"test/ordinary\")\n"
                                        + "g: SuiteGraph.leaf(\"g\", \"test/ordinary\")\n"
                                        + "deep: SuiteGraph.suite(\"deep\", Array(d, e))\n"
                                        + "nested: SuiteGraph.suite(\"nested\", Array(b, c, deep))\n"
                                        + "root: SuiteGraph.suite(\"root\", Array(a, nested, f, g))\n"
                                        + "SuiteGraph.flattenLeafIds(root)"));

        assertEquals(7, flattened.indexedSize().intValueExact());
        assertTrue(flattened.isFrozen());
        assertLeafId(flattened, 0, "a");
        assertLeafId(flattened, 1, "b");
        assertLeafId(flattened, 2, "c");
        assertLeafId(flattened, 3, "d");
        assertLeafId(flattened, 4, "e");
        assertLeafId(flattened, 5, "f");
        assertLeafId(flattened, 6, "g");
    }

    @Test
    void stableLogicalIdentityIsPreservedLiterally() throws Exception {
        String logicalId = "protos/library.leaf-a";
        ProtosArrayValue flattened =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                                        + "leaf: SuiteGraph.leaf(\""
                                        + logicalId
                                        + "\", \"test/ordinary\")\n"
                                        + "root: SuiteGraph.suite(\"protos/root-suite\", Array(leaf))\n"
                                        + "SuiteGraph.flattenLeafIds(root)"));

        assertEquals(1, flattened.indexedSize().intValueExact());
        assertLeafId(flattened, 0, logicalId);
    }

    @Test
    void repositorySuiteUsesRatifiedRootAndExactCurrentLeafOrder() throws Exception {
        ProtosStringValue rootId =
                assertInstanceOf(
                        ProtosStringValue.class,
                        completed(
                                "RepositorySuite: import(\"self:RepositorySuite\")\n"
                                        + "RepositorySuite.root.id"));
        assertEquals("protos/repository", rootId.value());

        ProtosArrayValue flattened =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                                        + "RepositorySuite: import(\"self:RepositorySuite\")\n"
                                        + "SuiteGraph.flattenLeafIds(RepositorySuite.root)"));

        assertEquals(4, flattened.indexedSize().intValueExact());
        assertTrue(flattened.isFrozen());
        assertLeafId(flattened, 0, "protos/conformance");
        assertLeafId(flattened, 1, "protos/actor");
        assertLeafId(flattened, 2, "protos/group");
        assertLeafId(flattened, 3, "protos/package-toml");
    }

    @Test
    void repositorySuiteUsesRatifiedD125RequirementsInLeafOrder() throws Exception {
        ProtosArrayValue leaves =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                                        + "RepositorySuite: import(\"self:RepositorySuite\")\n"
                                        + "SuiteGraph.flattenLeaves(RepositorySuite.root)"));

        assertEquals(4, leaves.indexedSize().intValueExact());
        assertTrue(leaves.isFrozen());
        assertLeafRequirement(
                leaves, 0, "protos/conformance", "protos/test/ordinary");
        assertLeafRequirement(
                leaves, 1, "protos/actor", "protos/test/actor");
        assertLeafRequirement(
                leaves, 2, "protos/group", "protos/test/group");
        assertLeafRequirement(
                leaves, 3, "protos/package-toml", "protos/test/package");
    }

    @Test
    void d125ExecutionRequirementIdsAreMandatoryCanonicalAndLeafLocal()
            throws Exception {
        assertFailed(
                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                        + "SuiteGraph.leaf(\"protos/a\", \"Protos/test\")");
        assertFailed(
                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                        + "SuiteGraph.leaf(\"protos/a\", \"protos//test\")");
        assertFailed(
                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                        + "leaf: {\n"
                        + "    kind: \"leaf\"\n"
                        + "    id: \"protos/a\"\n"
                        + "    children: Array()\n"
                        + "}\n"
                        + "SuiteGraph.flattenLeafIds(leaf)");
    }

    @Test
    void repositorySuiteDescriptorsAreFrozen() throws Exception {
        assertFailed(
                "RepositorySuite: import(\"self:RepositorySuite\")\n"
                        + "RepositorySuite.root.id = \"protos/changed\"");
        assertFailed(
                "RepositorySuite: import(\"self:RepositorySuite\")\n"
                        + "leaf: RepositorySuite.root.children[0]\n"
                        + "leaf.id = \"protos/changed\"");
        assertFailed(
                "RepositorySuite: import(\"self:RepositorySuite\")\n"
                        + "leaf: RepositorySuite.root.children[0]\n"
                        + "leaf.executionRequirement = \"protos/test/changed\"");
    }

    @Test
    void d123CanonicalSuiteIdsRejectNonCanonicalForms() throws Exception {
        assertFailed("SuiteGraph: import(\"self:SuiteGraph\")\nSuiteGraph.leaf(\"Protos/actor\", \"test/ordinary\")");
        assertFailed("SuiteGraph: import(\"self:SuiteGraph\")\nSuiteGraph.leaf(\"protos//actor\", \"test/ordinary\")");
        assertFailed("SuiteGraph: import(\"self:SuiteGraph\")\nSuiteGraph.leaf(\"/protos/actor\", \"test/ordinary\")");
        assertFailed("SuiteGraph: import(\"self:SuiteGraph\")\nSuiteGraph.leaf(\"protos/actor/\", \"test/ordinary\")");
        assertFailed("SuiteGraph: import(\"self:SuiteGraph\")\nSuiteGraph.leaf(\"protos/actor suite\", \"test/ordinary\")");
        assertFailed("SuiteGraph: import(\"self:SuiteGraph\")\nSuiteGraph.leaf(\"protos/actor:host\", \"test/ordinary\")");
    }

    @Test
    void duplicateIdsFailClosed() throws Exception {
        assertFailed(
                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                        + "first: SuiteGraph.leaf(\"same\", \"test/ordinary\")\n"
                        + "second: SuiteGraph.leaf(\"same\", \"test/ordinary\")\n"
                        + "root: SuiteGraph.suite(\"root\", Array(first, second))\n"
                        + "SuiteGraph.flattenLeafIds(root)");
    }

    @Test
    void cyclesFailClosed() throws Exception {
        assertFailed(
                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                        + "children: Array(null)\n"
                        + "cycle: {\n"
                        + "    kind: \"suite\"\n"
                        + "    id: \"cycle\"\n"
                        + "    children: children\n"
                        + "}\n"
                        + "children[0] = cycle\n"
                        + "SuiteGraph.flattenLeafIds(cycle)");
    }

    @Test
    void unknownNodeKindsFailClosed() throws Exception {
        assertFailed(
                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                        + "unknown: {\n"
                        + "    kind: \"tree\"\n"
                        + "    id: \"unknown\"\n"
                        + "    children: Array()\n"
                        + "}\n"
                        + "SuiteGraph.flattenLeafIds(unknown)");
    }

    @Test
    void emptyIdsFailClosed() throws Exception {
        assertFailed(
                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                        + "SuiteGraph.leaf(\"\", \"test/ordinary\")");
    }

    @Test
    void nonStringIdsFailClosed() throws Exception {
        assertFailed(
                "SuiteGraph: import(\"self:SuiteGraph\")\n"
                        + "SuiteGraph.leaf(42, \"test/ordinary\")");
    }

    private static void assertLeafRequirement(
            ProtosArrayValue leaves,
            int index,
            String expectedId,
            String expectedRequirement) {
        ProtosObjectValue leaf =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        leaves.indexedAt(BigInteger.valueOf(index)));
        assertTrue(leaf.isFrozen());
        assertEquals(
                expectedId,
                assertInstanceOf(
                                ProtosStringValue.class,
                                leaf.readLocalSlot("id").orElseThrow())
                        .value());
        assertEquals(
                expectedRequirement,
                assertInstanceOf(
                                ProtosStringValue.class,
                                leaf.readLocalSlot("executionRequirement").orElseThrow())
                        .value());
    }

    private static void assertLeafId(ProtosArrayValue flattened, int index, String expected) {
        assertEquals(
                expected,
                assertInstanceOf(
                                ProtosStringValue.class,
                                flattened.indexedAt(BigInteger.valueOf(index)))
                        .value());
    }

    private static Object completed(String source) throws Exception {
        ProtosExecutionOutcome outcome = execute(source);
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "expected completion, error=" + outcome.error());
        return outcome.value();
    }

    private static void assertFailed(String source) throws Exception {
        ProtosExecutionOutcome outcome = execute(source);
        assertEquals(
                ProtosExecutionOutcome.State.FAILED,
                outcome.state(),
                () -> "expected fail-closed result, value=" + outcome.value());
    }

    private static ProtosExecutionOutcome execute(String source) throws Exception {
        Fixture fixture = fixture();
        return ProtosTestExecutionSupport.execute(source, fixture.activation());
    }

    private static Fixture fixture() throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        return new Fixture(prelude, prelude.newModuleActivation());
    }

    private record Fixture(ProtosPrelude prelude, ProtosActivation activation) {}
}
