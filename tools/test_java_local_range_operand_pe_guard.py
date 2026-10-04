#!/usr/bin/env python3
# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
# THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
# FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
# https://github.com/guillermomolina/protos
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.

"""TEST009-A self-tests for tools/java_local_range_operand_pe_guard.py (synthetic snippets only)."""

from __future__ import print_function

import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import java_local_range_operand_pe_guard as guard  # noqa: E402

HEADER = """
package p;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
"""

OPERATION = """
    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class, name = "locals")
    @ConstantOperand(type = int.class, name = "ordinal")
    public static final class %s {
        @Specialization
        public static void perform(
                LocalRangeAccessor locals,
                int ordinal,
                Object value,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
%s
        }
    }
"""

# A per-invocation runtime object owning a LocalRangeAccessor field and a
# stable declaring root, in the shape of ProtosFrameLexicalBindingAuthority.
AUTHORITY = """
final class Authority {
    private final LocalRangeAccessor locals;
    private final BytecodeRootNode declaringRoot;
    private final MaterializedFrame frame;
    Authority(LocalRangeAccessor locals, BytecodeNode node, MaterializedFrame frame) {
        this.locals = locals;
        this.declaringRoot = node.getBytecodeRootNode();
        this.frame = frame;
    }
    private BytecodeNode currentBytecodeNode() {
        return declaringRoot.getBytecodeNode();
    }
%s
}
"""


def root(body, operations=""):
    return HEADER + "public final class Root {\n%s\n%s\n}\n" % (operations, body)


def analyze(*sources):
    items = [("p/F%d.java" % position, source) for position, source in enumerate(sources)]
    return guard.analyze_sources(items)[0]


def sink_in(sinks, method_prefix, operation=None):
    found = [
        sink for sink in sinks
        if sink.method.startswith(method_prefix) and (operation is None or sink.operation == operation)
    ]
    assert len(found) == 1, [sink.to_json() for sink in sinks]
    return found[0]


def run_check(source_text, entries):
    with tempfile.TemporaryDirectory() as directory:
        root_dir = Path(directory)
        source = root_dir / "src" / "p"
        source.mkdir(parents=True)
        (source / "Root.java").write_text(source_text, encoding="utf-8")
        baseline = root_dir / "baseline.json"
        baseline.write_text(json.dumps({"schema": guard.BASELINE_SCHEMA, "entries": entries}), encoding="utf-8")
        report = root_dir / "out" / "report.json"
        out = io.StringIO()
        status = guard.check(root_dir / "src", baseline, report, root_dir, out)
        candidate = json.loads((report.parent / guard.CANDIDATE_NAME).read_text(encoding="utf-8"))
        return status, out.getvalue(), candidate["entries"]


DIRECT = root("", OPERATION % ("Direct", """
            if (locals.isCleared(bytecodeNode, frame, ordinal)) {
                locals.setObject(bytecodeNode, frame, ordinal, value);
            }"""))

HELPERS = root("""
    static void helper(LocalRangeAccessor accessor, BytecodeNode node, VirtualFrame frame, int ordinal) {
        LocalRangeAccessor alias = accessor;
        inner(alias, node, frame, ordinal);
    }

    static void inner(LocalRangeAccessor accessor, BytecodeNode node, VirtualFrame frame, int ordinal) {
        accessor.clear(node, frame, ordinal);
    }
""", OPERATION % ("ViaHelpers", "            helper(locals, bytecodeNode, frame, ordinal);"))


class LocalProvenanceTest(unittest.TestCase):
    def test_01_constant_operand_receiver_and_bound_node_are_proven(self):
        sinks = analyze(DIRECT)
        self.assertEqual(len(sinks), 2)
        for sink in sinks:
            self.assertEqual(sink.receiver_class, guard.RECEIVER_CONSTANT_OPERAND)
            self.assertEqual(sink.node_class, guard.NODE_BOUND)
            self.assertEqual(sink.pe_reachability, guard.PE_REACHABLE)
            self.assertEqual((sink.receiver_pe, sink.node_pe), (guard.PROVEN, guard.PROVEN))

    def test_02_lossless_helper_propagation_is_proven(self):
        sink = sink_in(analyze(HELPERS), "inner(")
        self.assertEqual(sink.receiver_class, guard.RECEIVER_HELPER_PARAMETER)
        self.assertEqual(sink.node_class, guard.NODE_HELPER_PARAMETER)
        self.assertEqual(sink.pe_reachability, guard.PE_REACHABLE)
        self.assertEqual((sink.receiver_pe, sink.node_pe), (guard.PROVEN, guard.PROVEN))
        self.assertEqual(sink.pe_paths[0]["receiver"], guard.RECEIVER_CONSTANT_OPERAND)
        self.assertEqual(sink.pe_paths[0]["bytecode_node"], guard.NODE_BOUND)
        self.assertEqual([guard.base.short_key(each).split(".")[-1] for each in sink.pe_call_chain],
                         ["perform", "helper", "inner"])

    def test_03_final_field_receiver_is_a_risk(self):
        source = root(AUTHORITY % """
    void read(BytecodeNode node, int ordinal) {
        locals.getObject(node, frame, ordinal);
    }
""" + "\n", OPERATION % ("Read", "            authority().read(bytecodeNode, ordinal);")
            + "    static Authority authority() { return null; }\n")
        sink = sink_in(analyze(source), "read(")
        self.assertEqual(sink.receiver_class, guard.RECEIVER_FIELD_DERIVED)
        self.assertIn("final", sink.receiver_reason)
        self.assertEqual(sink.node_class, guard.NODE_HELPER_PARAMETER)
        self.assertEqual((sink.pe_reachability, sink.receiver_pe, sink.node_pe),
                         (guard.PE_REACHABLE, guard.RISK, guard.PROVEN))

    def test_04_current_root_derived_node_is_a_risk(self):
        source = root("""
    static void use(LocalRangeAccessor locals, BytecodeRootNode declaringRoot, VirtualFrame frame) {
        BytecodeNode node = declaringRoot.getBytecodeNode();
        locals.isCleared(node, frame, 0);
    }
""", OPERATION % ("Use", "            use(locals, null, frame);"))
        sink = sink_in(analyze(source), "use(")
        self.assertEqual(sink.receiver_class, guard.RECEIVER_HELPER_PARAMETER)
        self.assertEqual(sink.node_class, guard.NODE_ROOT_DERIVED)
        self.assertEqual((sink.receiver_pe, sink.node_pe), (guard.PROVEN, guard.RISK))

    def test_04b_zero_argument_helper_returning_get_bytecode_node_is_root_derived(self):
        source = root(AUTHORITY % """
    boolean has(int ordinal) {
        return !locals.isCleared(currentBytecodeNode(), frame, ordinal);
    }
""", OPERATION % ("Has", "            authority().has(ordinal);")
            + "    static Authority authority() { return null; }\n")
        sink = sink_in(analyze(source), "has(")
        self.assertEqual(sink.node_class, guard.NODE_ROOT_DERIVED)
        self.assertIn("currentBytecodeNode() returns", sink.node_reason)
        self.assertEqual((sink.receiver_pe, sink.node_pe), (guard.RISK, guard.RISK))

    def test_05_truffle_boundary_cuts(self):
        source = root(AUTHORITY % """
    @TruffleBoundary
    boolean has(int ordinal) {
        return !locals.isCleared(currentBytecodeNode(), frame, ordinal);
    }
    boolean viaBoundary(int ordinal) {
        return slow(ordinal);
    }
    @TruffleBoundary
    private boolean slow(int ordinal) {
        return helper(currentBytecodeNode(), ordinal);
    }
    private boolean helper(BytecodeNode node, int ordinal) {
        return locals.isCleared(node, frame, ordinal);
    }
""", OPERATION % ("Has", "            authority().has(ordinal); authority().viaBoundary(ordinal);")
            + "    static Authority authority() { return null; }\n")
        sinks = analyze(source)
        direct = sink_in(sinks, "has(")
        self.assertTrue(direct.boundary)
        self.assertEqual(direct.pe_reachability, guard.BOUNDARY_CUT)
        indirect = sink_in(sinks, "helper(")
        self.assertFalse(indirect.boundary)
        self.assertEqual(indirect.pe_reachability, guard.BOUNDARY_CUT)
        self.assertTrue(indirect.pe_call_chain[0].endswith("slow(int)"))

    def test_06_unreachable_sink_is_not_pe_reachable(self):
        source = root(AUTHORITY % """
    Object read(int ordinal) {
        return locals.getObject(currentBytecodeNode(), frame, ordinal);
    }
""")
        sink = sink_in(analyze(source), "read(")
        self.assertEqual(sink.receiver_class, guard.RECEIVER_FIELD_DERIVED)
        self.assertEqual(sink.pe_reachability, guard.NOT_PE_REACHABLE)

    def test_07_ambiguous_edge_that_can_reach_a_root_is_unknown(self):
        source = root("""
    static void store(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, Object value) {
        locals.setObject(node, frame, 0, value);
    }

    static void store(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, String value) {
        locals.setObject(node, frame, 0, value);
    }
""", OPERATION % ("Store", "            store(locals, bytecodeNode, frame, mystery());"))
        sinks = analyze(source)
        self.assertEqual([sink.pe_reachability for sink in sinks], [guard.PE_REACHABILITY_UNKNOWN] * 2)
        self.assertTrue(all("ambiguous overload" in sink.pe_unknown[0] for sink in sinks))

    def test_07b_unknown_local_provenance_outside_boundary_is_unknown(self):
        source = root("""
    static void read(LocalRangeAccessor[] all, BytecodeNode node, VirtualFrame frame) {
        all[0].getObject(node, frame, 0);
    }
""")
        sink = sink_in(analyze(source), "read(")
        self.assertEqual(sink.pe_reachability, guard.PE_REACHABILITY_UNKNOWN)

    def test_10_comments_and_strings_are_ignored(self):
        source = root("""
    // locals.isCleared(declaringRoot.getBytecodeNode(), frame, 0);
    /* locals.setObject(declaringRoot.getBytecodeNode(), frame, 0, value); */
    static final String TEXT = "locals.getObject(declaringRoot.getBytecodeNode(), frame, 0)";
""", OPERATION % ("Commented", "            // locals.clear(other.getBytecodeNode(), frame, ordinal);"))
        self.assertEqual(analyze(source), [])

    def test_11_overload_propagation_tracks_the_right_parameters(self):
        source = root(AUTHORITY % """
    void write(LocalRangeAccessor operand, BytecodeNode node, int ordinal, Object value) {
        operand.setObject(node, frame, ordinal, value);
    }
    void write(int ordinal, Object value) {
        write(locals, currentBytecodeNode(), ordinal, value);
    }
""", OPERATION % ("Write", "            authority().write(locals, bytecodeNode, ordinal, value);")
            + "    static Authority authority() { return null; }\n")
        sinks = analyze(source)
        sink = sink_in(sinks, "write(LocalRangeAccessor")
        self.assertEqual(sink.pe_reachability, guard.PE_REACHABLE)
        self.assertEqual((sink.receiver_pe, sink.node_pe), (guard.PROVEN, guard.PROVEN))
        self.assertEqual(len(sink.pe_paths), 1)
        # Once the runtime-derived overload is reached from a root, the same sink becomes a risk.
        reached = source.replace("authority().write(locals, bytecodeNode, ordinal, value);",
                                 "authority().write(locals, bytecodeNode, ordinal, value); "
                                 "authority().write(ordinal, value);")
        sink = sink_in(analyze(reached), "write(LocalRangeAccessor")
        self.assertEqual((sink.receiver_pe, sink.node_pe), (guard.RISK, guard.RISK))
        self.assertEqual(sorted((path["receiver"], path["bytecode_node"]) for path in sink.pe_paths), [
            (guard.RECEIVER_CONSTANT_OPERAND, guard.NODE_BOUND),
            (guard.RECEIVER_FIELD_DERIVED, guard.NODE_ROOT_DERIVED),
        ])

    def test_root_runtime_operand_receiver_is_not_proven(self):
        source = root("""
    @Operation
    public static final class Runtime {
        @Specialization
        public static void perform(
                LocalRangeAccessor locals,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            locals.clear(bytecodeNode, frame, 0);
        }
    }
""")
        sink = sink_in(analyze(source), "perform(")
        self.assertEqual(sink.receiver_class, guard.RECEIVER_HELPER_PARAMETER)
        self.assertEqual(sink.pe_paths[0]["receiver"], guard.PROV_ROOT_RUNTIME_OPERAND)
        self.assertEqual((sink.receiver_pe, sink.node_pe), (guard.RISK, guard.PROVEN))

    def test_root_called_as_helper_follows_its_callers(self):
        source = root("""
    static void forward(LocalRangeAccessor field, BytecodeNode node, VirtualFrame frame) {
        Direct.perform(field, 0, null, node, frame);
    }
""", OPERATION % ("Direct", "            locals.clear(bytecodeNode, frame, ordinal);")
            + OPERATION % ("Caller", "            forward(locals, bytecodeNode, frame);"))
        sink = sink_in(analyze(source), "perform(")
        self.assertEqual(sink.receiver_class, guard.RECEIVER_CONSTANT_OPERAND)
        self.assertEqual((sink.receiver_pe, sink.node_pe), (guard.PROVEN, guard.PROVEN))
        risky = source.replace("forward(locals, bytecodeNode, frame);", "forward(null, bytecodeNode, frame);")
        sink = sink_in(analyze(risky), "perform(")
        self.assertEqual(sink.receiver_pe, guard.RISK)


class CheckTest(unittest.TestCase):
    def test_exact_baseline_passes(self):
        status, _, candidate = run_check(HELPERS, [])
        self.assertEqual(status, 1)
        status, output, _ = run_check(HELPERS, candidate)
        self.assertEqual(status, 0, output)
        self.assertIn("PE_REACHABLE_RECEIVER_RISK=0", output)

    def test_08_new_unregistered_risk_fails(self):
        source = root(AUTHORITY % """
    Object read(int ordinal) {
        return locals.getObject(currentBytecodeNode(), frame, ordinal);
    }
""")
        status, _, candidate = run_check(source, [])
        status, output, _ = run_check(source, candidate)
        self.assertEqual(status, 0, output)
        reached = source.replace("public final class Root {", "public final class Root {\n" + OPERATION % (
            "Read", "            authority().read(ordinal);") + "    static Authority authority() { return null; }\n")
        status, output, new_candidate = run_check(reached, candidate)
        self.assertEqual(status, 1)
        self.assertIn("PE_REACHABLE_RECEIVER_RISK ", output)
        self.assertIn("PE_REACHABLE_BYTECODE_NODE_RISK ", output)
        self.assertEqual(new_candidate, [], "risks are never offered as baseline entries")

    def test_09_stale_drifted_and_duplicate_entries_fail(self):
        status, _, candidate = run_check(HELPERS, [])
        stale = dict(candidate[0], method="gone()")
        status, output, _ = run_check(HELPERS, candidate + [stale])
        self.assertEqual(status, 1)
        self.assertIn("STALE_BASELINE_ENTRY", output)
        drifted = [dict(candidate[0], pe_reachability=guard.NOT_PE_REACHABLE)] + candidate[1:]
        status, output, _ = run_check(HELPERS, drifted)
        self.assertEqual(status, 1)
        self.assertIn("TOPOLOGY_DRIFT", output)
        status, output, _ = run_check(HELPERS, candidate + candidate[:1])
        self.assertEqual(status, 1)
        self.assertIn("DUPLICATE_BASELINE_ENTRY", output)

    def test_risk_entries_are_never_baselineable(self):
        entry = {
            "path": "src/p/Root.java", "class": "p.Root", "method": "m()", "operation": "clear",
            "receiver": "locals", "bytecode_node": "node", "occurrence": 0,
            "receiver_class": guard.RECEIVER_FIELD_DERIVED, "bytecode_node_class": guard.NODE_BOUND,
            "pe_reachability": guard.PE_REACHABLE,
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "baseline.json"
            path.write_text(json.dumps({"schema": guard.BASELINE_SCHEMA, "entries": [entry]}), encoding="utf-8")
            with self.assertRaises(guard.GuardError):
                guard.load_baseline(path)
            entry["pe_reachability"] = guard.PE_REACHABILITY_UNKNOWN
            entry["receiver_class"] = guard.RECEIVER_CONSTANT_OPERAND
            path.write_text(json.dumps({"schema": guard.BASELINE_SCHEMA, "entries": [entry]}), encoding="utf-8")
            with self.assertRaises(guard.GuardError):
                guard.load_baseline(path)


if __name__ == "__main__":
    unittest.main(verbosity=2)
