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

"""TEST009-B self-tests for tools/java_local_accessor_pe_guard.py (synthetic snippets only)."""

from __future__ import print_function

import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import java_local_accessor_pe_guard as guard  # noqa: E402

HEADER = """
package p;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.MaterializedLocalAccessor;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
"""

LOCAL_OPERATION = """
    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    public static final class %s {
        @Specialization
        public static Object perform(
                LocalAccessor accessor,
                Object value,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
%s
            return value;
        }
    }
"""

MATERIALIZED_OPERATION = """
    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    public static final class %s {
        @Specialization
        public static Object perform(
                MaterializedLocalAccessor accessor,
                MaterializedFrame owner,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode) {
%s
            return owner;
        }
    }
"""

LOWERER = """
final class Lowerer {
    void lower(RootGen.Builder builder, BytecodeLocal local, Object notALocal) {
%s
    }
}
"""


def root(body, operations="", annotation="@GenerateBytecode(languageClass = L.class)"):
    return HEADER + "%s\nabstract class Root extends RootNode implements BytecodeRootNode {\n%s\n%s\n}\n" % (
        annotation, operations, body)


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


LOCAL_DIRECT = root("", LOCAL_OPERATION % ("Direct", """
            if (!accessor.isCleared(bytecodeNode, frame)) {
                accessor.setObject(bytecodeNode, frame, value);
            }"""))

LOCAL_HELPERS = root("""
    static Object helper(LocalAccessor accessor, BytecodeNode node, VirtualFrame frame) {
        LocalAccessor alias = accessor;
        return inner(alias, node, frame);
    }

    static Object inner(LocalAccessor accessor, BytecodeNode node, VirtualFrame frame) {
        return accessor.getObject(node, frame);
    }
""", LOCAL_OPERATION % ("ViaHelpers", "            helper(accessor, bytecodeNode, frame);"))

MATERIALIZED_DIRECT = root("""
    static Object read(MaterializedLocalAccessor accessor, MaterializedFrame owner, BytecodeNode node) {
        return accessor.isCleared(node, owner) ? null : accessor.getObject(node, owner);
    }
""", MATERIALIZED_OPERATION % ("ReadCaptured", "            read(accessor, owner, bytecodeNode);"))

MATERIALIZED_EMITTED = MATERIALIZED_DIRECT + LOWERER % """
        builder.beginReadCaptured(local);
        builder.endReadCaptured();"""


class LocalAccessorTest(unittest.TestCase):
    def test_01_constant_operand_receiver_and_bound_node_are_proven(self):
        sinks = analyze(LOCAL_DIRECT)
        self.assertEqual(len(sinks), 2)
        for sink in sinks:
            self.assertEqual(sink.family, guard.LOCAL_ACCESSOR)
            self.assertEqual(sink.receiver_class, guard.RECEIVER_CONSTANT_OPERAND)
            self.assertEqual(sink.node_class, guard.NODE_BOUND)
            self.assertEqual(sink.pe_reachability, guard.PE_REACHABLE_PROVEN)
            self.assertEqual((sink.receiver_pe, sink.node_pe, sink.declaring_pe), (guard.PROVEN, guard.PROVEN, None))

    def test_02_lossless_helper_propagation_is_proven(self):
        sink = sink_in(analyze(LOCAL_HELPERS), "inner(")
        self.assertEqual(sink.receiver_class, guard.RECEIVER_HELPER_PARAMETER)
        self.assertEqual(sink.node_class, guard.NODE_HELPER_PARAMETER)
        self.assertEqual(sink.pe_reachability, guard.PE_REACHABLE_PROVEN)

    def test_03_one_unproven_caller_makes_the_helper_a_risk(self):
        source = LOCAL_HELPERS.replace("            helper(accessor, bytecodeNode, frame);", """
            helper(accessor, bytecodeNode, frame);
            helper(accessor, bytecodeNode.getBytecodeRootNode().getBytecodeNode(), frame);""")
        sink = sink_in(analyze(source), "inner(")
        self.assertEqual(sink.pe_reachability, guard.PE_REACHABLE_RISK)
        self.assertEqual((sink.receiver_pe, sink.node_pe), (guard.PROVEN, guard.RISK))

    def test_04_final_field_receiver_is_a_risk(self):
        source = root("""
    private static final LocalAccessor SHARED = null;
    static Object helper(BytecodeNode node, VirtualFrame frame) {
        return SHARED.getObject(node, frame);
    }
""", LOCAL_OPERATION % ("ViaField", "            helper(bytecodeNode, frame);"))
        sink = sink_in(analyze(source), "helper(")
        self.assertEqual(sink.receiver_class, guard.RECEIVER_FIELD_DERIVED)
        self.assertEqual(sink.pe_reachability, guard.PE_REACHABLE_RISK)
        self.assertEqual(sink.receiver_pe, guard.RISK)

    def test_05_accessor_of_another_type_operand_is_not_proven(self):
        # A LocalAccessor received at the position of a differently typed constant operand.
        source = root("", """
    @Operation
    @ConstantOperand(type = Object.class, name = "accessor")
    public static final class Mistyped {
        @Specialization
        public static Object perform(
                LocalAccessor accessor,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            return accessor.getObject(bytecodeNode, frame);
        }
    }
""")
        sink = sink_in(analyze(source), "perform(")
        self.assertEqual(sink.receiver_class, guard.RECEIVER_HELPER_PARAMETER)
        self.assertEqual(sink.pe_reachability, guard.PE_REACHABLE_RISK)
        self.assertEqual(sink.receiver_pe, guard.RISK)

    def test_06_truffle_boundary_cuts(self):
        source = root("""
    @TruffleBoundary
    static Object cold(LocalAccessor accessor, BytecodeNode node, VirtualFrame frame) {
        return helper(accessor, node, frame);
    }

    static Object helper(LocalAccessor accessor, BytecodeNode node, VirtualFrame frame) {
        return accessor.getObject(node, frame);
    }
""", LOCAL_OPERATION % ("ViaBoundary", "            cold(accessor, bytecodeNode, frame);"))
        self.assertEqual(sink_in(analyze(source), "helper(").pe_reachability, guard.BOUNDARY_CUT)

    def test_07_unreachable_sink_is_not_pe_reachable(self):
        source = root("""
    static Object unused(LocalAccessor accessor, BytecodeNode node, VirtualFrame frame) {
        return accessor.getObject(node, frame);
    }
""")
        self.assertEqual(sink_in(analyze(source), "unused(").pe_reachability, guard.NOT_PE_REACHABLE)

    def test_08_unresolved_edge_that_can_reach_a_root_is_unknown(self):
        source = root("""
    static Object helper(LocalAccessor accessor, BytecodeNode node, VirtualFrame frame) {
        return accessor.getObject(node, frame);
    }
""", LOCAL_OPERATION % ("ViaUnknown", "            mystery().helper(accessor, bytecodeNode, frame);"))
        sink = sink_in(analyze(source), "helper(")
        self.assertEqual(sink.pe_reachability, guard.PE_REACHABILITY_UNKNOWN)

    def test_09_unresolved_receiver_type_fails_closed(self):
        source = root("""
    static Object helper(BytecodeNode node, VirtualFrame frame) {
        return mystery().getObject(node, frame);
    }
""")
        sink = sink_in(analyze(source), "helper(")
        self.assertEqual(sink.family, guard.FAMILY_UNRESOLVED)
        self.assertEqual(sink.pe_reachability, guard.PE_REACHABILITY_UNKNOWN)

    def test_10_other_receiver_types_are_not_sinks(self):
        source = root("""
    static Object helper(java.util.Map<String, Object> map, Other other, BytecodeNode node, VirtualFrame frame) {
        // accessor.getObject(node, frame) in a comment
        String text = "accessor.getObject(node, frame)";
        other.getObject(node, frame);
        return map.getOrDefault("a", text);
    }
""") + "final class Other { Object getObject(BytecodeNode n, VirtualFrame f) { return null; } }\n"
        self.assertEqual(analyze(source), [])

    def test_11_lambda_body_is_never_proven(self):
        source = root("", LOCAL_OPERATION % ("InLambda", """
            Runnable later = () -> accessor.clear(bytecodeNode, frame);
            later.run();"""))
        sink = sink_in(analyze(source), "perform(")
        self.assertEqual(sink.pe_reachability, guard.PE_REACHABILITY_UNKNOWN)


class MaterializedAccessorTest(unittest.TestCase):
    def test_20_same_root_operand_node_and_bytecode_local_emission_prove_the_declaring_node(self):
        sinks = analyze(MATERIALIZED_EMITTED)
        self.assertEqual(len(sinks), 2)
        for sink in sinks:
            self.assertEqual(sink.family, guard.MATERIALIZED_ACCESSOR)
            self.assertEqual(sink.pe_reachability, guard.PE_REACHABLE_PROVEN, sink.to_json())
            self.assertEqual((sink.receiver_pe, sink.node_pe, sink.declaring_pe),
                             (guard.PROVEN, guard.PROVEN, guard.PROVEN))
            self.assertEqual(sink.pe_paths[0]["declaring_bytecode_node"], guard.DECLARING_SAME_ROOT_GROUP)

    def test_21_emission_without_a_bytecode_local_is_a_declaring_risk(self):
        source = MATERIALIZED_DIRECT + LOWERER % """
        builder.beginReadCaptured(local);
        builder.beginReadCaptured(notALocal);"""
        for sink in analyze(source):
            self.assertEqual(sink.pe_reachability, guard.PE_REACHABLE_RISK)
            self.assertEqual((sink.receiver_pe, sink.node_pe, sink.declaring_pe),
                             (guard.PROVEN, guard.PROVEN, guard.RISK))
            self.assertEqual(sink.pe_paths[0]["declaring_bytecode_node"], guard.DECLARING_UNPROVEN_EMISSION)
            self.assertTrue(sink.emission_failures)

    def test_22_operation_outside_a_generated_root_is_a_declaring_risk(self):
        source = root("", MATERIALIZED_OPERATION % ("Plain", """
            accessor.getObject(bytecodeNode, owner);"""), annotation="")
        sink = sink_in(analyze(source), "perform(")
        self.assertEqual((sink.receiver_pe, sink.node_pe, sink.declaring_pe),
                         (guard.PROVEN, guard.PROVEN, guard.RISK))
        self.assertEqual(sink.pe_paths[0]["declaring_bytecode_node"], guard.DECLARING_NOT_GENERATED_ROOT)

    def test_23_supplied_node_from_another_root_breaks_every_materialized_dimension_it_touches(self):
        source = MATERIALIZED_EMITTED.replace(
            "read(accessor, owner, bytecodeNode);",
            "read(accessor, owner, bytecodeNode.getBytecodeRootNode().getBytecodeNode());")
        sink = sink_in(analyze(source), "read(", "getObject")
        self.assertEqual((sink.receiver_pe, sink.node_pe, sink.declaring_pe),
                         (guard.PROVEN, guard.RISK, guard.RISK))

    def test_24_accessor_from_runtime_state_is_a_receiver_and_declaring_risk(self):
        source = root("""
    static Object read(Holder holder, MaterializedFrame owner, BytecodeNode node) {
        MaterializedLocalAccessor accessor = holder.accessor;
        return accessor.getObject(node, owner);
    }
""", MATERIALIZED_OPERATION % ("ViaHolder", "            read(null, owner, bytecodeNode);")) + (
            "final class Holder { final MaterializedLocalAccessor accessor = null; }\n")
        sink = sink_in(analyze(source), "read(")
        self.assertEqual(sink.receiver_class, guard.RECEIVER_FIELD_DERIVED)
        self.assertEqual((sink.receiver_pe, sink.node_pe, sink.declaring_pe), (guard.RISK, guard.PROVEN, guard.RISK))

    def test_25_proofs_from_different_roots_do_not_combine(self):
        # Each root proves only one dimension; neither path proves the joint relationship.
        source = root("""
    static Object read(MaterializedLocalAccessor accessor, MaterializedFrame owner, BytecodeNode node) {
        return accessor.getObject(node, owner);
    }
    static MaterializedLocalAccessor any() { return null; }
""", """
    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    public static final class OnlyAccessor {
        @Specialization
        public static Object perform(MaterializedLocalAccessor accessor, MaterializedFrame owner, BytecodeNode node) {
            return read(accessor, owner, node);
        }
    }

    @Operation
    public static final class OnlyNode {
        @Specialization
        public static Object perform(MaterializedFrame owner, @Bind("$bytecodeNode") BytecodeNode bytecodeNode) {
            return read(any(), owner, bytecodeNode);
        }
    }
""")
        sink = sink_in(analyze(source), "read(")
        self.assertEqual((sink.receiver_pe, sink.node_pe, sink.declaring_pe), (guard.RISK, guard.RISK, guard.RISK))


class CheckTest(unittest.TestCase):
    def test_exact_baseline_passes(self):
        status, output, candidate = run_check(MATERIALIZED_EMITTED, [])
        self.assertEqual(status, 1)
        self.assertIn("NEW_UNBASELINED_SITE", output)
        status, output, _ = run_check(MATERIALIZED_EMITTED, candidate)
        self.assertEqual(status, 0, output)
        for key in guard.ACCEPTANCE_KEYS:
            self.assertIn("%s=0" % key, output)

    def test_risks_fail_and_are_not_offered_as_candidates(self):
        source = MATERIALIZED_DIRECT + LOWERER % "        builder.beginReadCaptured(notALocal);"
        status, output, candidate = run_check(source, [])
        self.assertEqual(status, 1)
        self.assertIn("PE_REACHABLE_MATERIALIZED_DECLARING_NODE_RISK", output)
        self.assertEqual(candidate, [])

    def test_stale_drifted_and_duplicate_entries_fail(self):
        _, _, candidate = run_check(LOCAL_HELPERS, [])
        status, output, _ = run_check(LOCAL_HELPERS, candidate + [dict(candidate[0], method="gone()")])
        self.assertEqual(status, 1)
        self.assertIn("STALE_BASELINE_ENTRY", output)
        drifted = [dict(candidate[0], pe_reachability=guard.NOT_PE_REACHABLE)] + candidate[1:]
        status, output, _ = run_check(LOCAL_HELPERS, drifted)
        self.assertEqual(status, 1)
        self.assertIn("TOPOLOGY_DRIFT", output)
        status, output, _ = run_check(LOCAL_HELPERS, candidate + candidate[:1])
        self.assertEqual(status, 1)
        self.assertIn("DUPLICATE_BASELINE_ENTRY", output)

    def test_risk_and_unknown_entries_are_never_baselineable(self):
        entry = {
            "path": "src/p/Root.java", "class": "p.Root", "method": "m()", "family": guard.LOCAL_ACCESSOR,
            "operation": "clear", "receiver": "accessor", "bytecode_node": "node", "occurrence": 0,
            "receiver_class": guard.RECEIVER_FIELD_DERIVED, "bytecode_node_class": guard.NODE_BOUND,
            "pe_reachability": guard.PE_REACHABLE_PROVEN,
        }
        variants = [
            dict(entry),
            dict(entry, receiver_class=guard.RECEIVER_CONSTANT_OPERAND, pe_reachability=guard.PE_REACHABLE_RISK),
            dict(entry, receiver_class=guard.RECEIVER_CONSTANT_OPERAND,
                 pe_reachability=guard.PE_REACHABILITY_UNKNOWN),
            dict(entry, family=guard.FAMILY_UNRESOLVED, pe_reachability=guard.NOT_PE_REACHABLE),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "baseline.json"
            for variant in variants:
                path.write_text(json.dumps({"schema": guard.BASELINE_SCHEMA, "entries": [variant]}),
                                encoding="utf-8")
                with self.assertRaises(guard.GuardError):
                    guard.load_baseline(path)


if __name__ == "__main__":
    unittest.main(verbosity=2)
