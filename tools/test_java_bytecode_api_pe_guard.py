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

"""TEST009-C self-tests for tools/java_bytecode_api_pe_guard.py (synthetic snippets only)."""

from __future__ import print_function

import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import java_bytecode_api_pe_guard as guard  # noqa: E402

HEADER = """
package p;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
"""

OPERATION = """
    @Operation
    %s
    public static final class %s {
        @Specialization
        public static Object perform(
                %s
                Object value,
                @Bind("$bytecodeNode") BytecodeNode bytecode,
                @Bind("$node") Node node,
                @Bind("$bytecodeIndex") int boundBci,
                @Bind("$frame") VirtualFrame frame) {
%s
            return value;
        }
    }
"""


def operation(name, body, constant="", parameter=""):
    return OPERATION % (constant, name, parameter, body)


def root(body, operations=""):
    return HEADER + ("@GenerateBytecode(languageClass = L.class)\n"
                     "abstract class Root extends RootNode implements BytecodeRootNode {\n%s\n%s\n}\n") % (
        operations, body)


def analyze(*sources):
    items = [("p/F%d.java" % position, source) for position, source in enumerate(sources)]
    return guard.analyze_sources(items)[0]


def sinks_in(sinks, method_prefix, operation=None):
    return [sink for sink in sinks
            if sink.method.startswith(method_prefix) and (operation is None or sink.operation == operation)]


def sink_in(sinks, method_prefix, operation=None):
    found = sinks_in(sinks, method_prefix, operation)
    assert len(found) == 1, [sink.to_json() for sink in sinks]
    return found[0]


def pe(sink, role):
    return [argument.pe for argument in sink.arguments if guard._report_role(argument.role) == role][0]


def klass(sink, role):
    return [argument.klass for argument in sink.arguments if guard._report_role(argument.role) == role][0]


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
        candidate_path = report.parent / guard.CANDIDATE_NAME
        if not candidate_path.exists():  # a rejected baseline fails before any report is written
            return status, out.getvalue(), None
        candidate = json.loads(candidate_path.read_text(encoding="utf-8"))
        return status, out.getvalue(), candidate["entries"]


SAFE_SOURCE = root("""
    static Object[] names(BytecodeNode bytecode, int bci) {
        return bytecode.getLocalNames(bci);
    }
""", operation("Literal", "            bytecode.getLocalNames(0);")
     + operation("Helper", "            names(bytecode, bci);", '@ConstantOperand(type = int.class, name = "bci")',
                 "int bci,"))

RISK_SOURCE = root("", operation("Runtime", """
            int bci = node.hashCode();
            bytecode.getLocalNames(bci);"""))


class LocalTableTest(unittest.TestCase):
    def test_01_literal_and_structural_bci_are_proven(self):
        sinks = analyze(root("", operation("Literal", "            bytecode.getLocalNames(0);")
                             + operation("Bound", "            bytecode.getLocalNames(boundBci);")
                             + operation("Operand", "            bytecode.getLocalNames(bci);",
                                         '@ConstantOperand(type = int.class, name = "bci")', "int bci,")))
        self.assertEqual(3, len(sinks))
        self.assertEqual(["INT_CONSTANT", "BOUND_BYTECODE_INDEX", "OPERAND_CONSTANT"],
                         [klass(sink, guard.ROLE_BCI) for sink in sinks])
        for sink in sinks:
            self.assertEqual(guard.PE_REACHABLE_PROVEN, sink.pe_reachability, sink.to_json())

    def test_02_helper_parameter_bci_proven_at_its_root(self):
        sink = sink_in(analyze(SAFE_SOURCE), "names", "getLocalNames")
        self.assertEqual(guard.HELPER_PARAMETER, klass(sink, guard.ROLE_BCI))
        self.assertEqual(guard.PE_REACHABLE_PROVEN, sink.pe_reachability, sink.to_json())

    def test_03_runtime_bci_is_a_risk_whatever_its_name(self):
        sink = sink_in(analyze(RISK_SOURCE), "perform", "getLocalNames")
        self.assertEqual(guard.RUNTIME_VALUE, klass(sink, guard.ROLE_BCI))
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)
        sinks = analyze(root("""
    static Object[] names(TagTreeNode tag) {
        BytecodeNode bytecode = tag.getBytecodeNode();
        int bytecodeIndex = tag.getEnterBytecodeIndex();
        return bytecode.getLocalNames(bytecodeIndex);
    }
""", operation("Tag", "            names((TagTreeNode) value);")))
        sink = sink_in(sinks, "names", "getLocalNames")
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability, sink.to_json())

    def test_04_get_local_values_and_infos(self):
        sinks = analyze(root("""
    int cached;
""", operation("Read", """
            bytecode.getLocalValues(boundBci, frame);
            bytecode.getLocalInfos(cached);""")))
        self.assertEqual(guard.PE_REACHABLE_PROVEN, sink_in(sinks, "perform", "getLocalValues").pe_reachability)
        infos = sink_in(sinks, "perform", "getLocalInfos")
        self.assertEqual(guard.PE_REACHABLE_RISK, infos.pe_reachability)
        self.assertEqual(guard.RUNTIME_VALUE, klass(infos, guard.ROLE_BCI))

    def test_05_set_local_values(self):
        sinks = analyze(root("", operation("Write", "            bytecode.setLocalValues(boundBci, frame, null);")))
        sink = sink_in(sinks, "perform", "setLocalValues")
        self.assertEqual(guard.PE_REACHABLE_PROVEN, sink.pe_reachability)

    def test_06_copy_local_values_simple(self):
        sinks = analyze(root("", operation("Copy", "            bytecode.copyLocalValues(boundBci, frame, frame);")))
        sink = sink_in(sinks, "perform", "copyLocalValues")
        self.assertEqual(3, sink.arity)
        self.assertEqual([guard.ROLE_BCI], [argument.role for argument in sink.arguments])
        self.assertEqual(guard.PE_REACHABLE_PROVEN, sink.pe_reachability)

    def test_07_copy_overload_dynamic_offset_is_a_risk(self):
        sinks = analyze(root("", operation("Copy", """
            int offset = node.hashCode();
            bytecode.copyLocalValues(boundBci, frame, frame, offset, 2);""")))
        sink = sink_in(sinks, "perform", "copyLocalValues")
        self.assertEqual(5, sink.arity)
        self.assertEqual(guard.PROVEN, pe(sink, guard.ROLE_BCI))
        self.assertEqual(guard.RISK, pe(sink, guard.ROLE_OFFSET))
        self.assertEqual(guard.PROVEN, pe(sink, guard.ROLE_COUNT))
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)

    def test_08_copy_overload_dynamic_count_is_a_risk(self):
        sinks = analyze(root("""
    static final int OFFSET = 1;
""", operation("Copy", """
            for (int count = 0; count < 3; count++) {
                bytecode.copyLocalValues(boundBci, frame, frame, OFFSET, count);
            }""")))
        sink = sink_in(sinks, "perform", "copyLocalValues")
        self.assertEqual(guard.STATIC_FINAL_FIELD, klass(sink, guard.ROLE_OFFSET))
        self.assertEqual(guard.PROVEN, pe(sink, guard.ROLE_OFFSET))
        self.assertEqual(guard.RISK, pe(sink, guard.ROLE_COUNT))


class LocalSlotTest(unittest.TestCase):
    def assert_slot(self, call, operation_name, bci, offset):
        sinks = analyze(root("", operation("Slot", "            %s;" % call,
                                           '@ConstantOperand(type = int.class, name = "slot")', "int slot,")))
        sink = sink_in(sinks, "perform", operation_name)
        self.assertEqual(guard.BYTECODE_NODE, sink.family)
        self.assertEqual(bci, pe(sink, guard.ROLE_BCI), sink.to_json())
        if offset is not None:
            self.assertEqual(offset, pe(sink, guard.ROLE_OFFSET), sink.to_json())
        expected = guard.PE_REACHABLE_PROVEN if (bci, offset) in (
            (guard.PROVEN, guard.PROVEN), (guard.PROVEN, None)) else guard.PE_REACHABLE_RISK
        self.assertEqual(expected, sink.pe_reachability, sink.to_json())

    def test_22_get_local_value(self):
        self.assert_slot("bytecode.getLocalValue(boundBci, frame, slot)", "getLocalValue", guard.PROVEN, guard.PROVEN)
        self.assert_slot("bytecode.getLocalValue(node.hashCode(), frame, slot)", "getLocalValue",
                         guard.RISK, guard.PROVEN)
        self.assert_slot("bytecode.getLocalValue(boundBci, frame, node.hashCode())", "getLocalValue",
                         guard.PROVEN, guard.RISK)

    def test_23_set_local_value(self):
        self.assert_slot("bytecode.setLocalValue(0, frame, 1, value)", "setLocalValue", guard.PROVEN, guard.PROVEN)
        self.assert_slot("bytecode.setLocalValue(node.hashCode(), frame, slot, value)", "setLocalValue",
                         guard.RISK, guard.PROVEN)
        self.assert_slot("bytecode.setLocalValue(boundBci, frame, node.hashCode(), value)", "setLocalValue",
                         guard.PROVEN, guard.RISK)

    def test_24_get_local_name_and_info(self):
        for name in ("getLocalName", "getLocalInfo"):
            self.assert_slot("bytecode.%s(boundBci, slot)" % name, name, guard.PROVEN, guard.PROVEN)
            self.assert_slot("bytecode.%s(node.hashCode(), slot)" % name, name, guard.RISK, guard.PROVEN)
            self.assert_slot("bytecode.%s(boundBci, node.hashCode())" % name, name, guard.PROVEN, guard.RISK)

    def test_25_get_local_count(self):
        self.assert_slot("bytecode.getLocalCount(boundBci)", "getLocalCount", guard.PROVEN, None)
        self.assert_slot("bytecode.getLocalCount(node.hashCode())", "getLocalCount", guard.RISK, None)

    def test_26_production_style_boundary_helper_is_cut(self):
        sinks = analyze(root("""
    @TruffleBoundary
    static Object carrier(TagTreeNode node, Frame frame) {
        BytecodeNode bytecode = node.getBytecodeNode();
        int bytecodeIndex = node.getEnterBytecodeIndex();
        Object[] names = bytecode.getLocalNames(bytecodeIndex);
        for (int offset = names.length - 1; offset >= 0; offset--) {
            if (bytecode.getLocalValue(bytecodeIndex, frame, offset) != null) {
                return names[offset];
            }
        }
        return null;
    }
""", operation("Carrier", "            carrier((TagTreeNode) value, frame);")))
        sink = sink_in(sinks, "carrier", "getLocalValue")
        self.assertEqual(guard.BYTECODE_NODE, sink.family)
        self.assertEqual(guard.RUNTIME_VALUE, klass(sink, guard.ROLE_BCI))
        self.assertNotIn(klass(sink, guard.ROLE_OFFSET), guard.SAFE_CLASSES)
        self.assertEqual(guard.BOUNDARY_CUT, sink.pe_reachability)

    def test_27_unresolved_slot_receiver_fails_closed(self):
        sinks = analyze(root("""
    static Object reachable(Helper helper, Frame frame) {
        helper.find().setLocalValue(0, frame, 0, null);
        helper.find().getLocalName(0, 0);
        helper.find().getLocalInfo(0, 0);
        helper.find().getLocalCount(0);
        return helper.find().getLocalValue(0, frame, 0);
    }
""", operation("Unresolved", "            reachable(null, frame);")))
        found = sinks_in(sinks, "reachable")
        self.assertEqual(sorted(guard.LOCAL_SLOT_OPERATIONS), sorted(sink.operation for sink in found))
        for sink in found:
            self.assertEqual(guard.FAMILY_UNRESOLVED, sink.family)
            self.assertEqual(guard.PE_REACHABILITY_UNKNOWN, sink.pe_reachability, sink.to_json())


class NodeAndConfigTest(unittest.TestCase):
    def test_09_bytecode_node_get_with_bound_node_is_proven(self):
        sink = sink_in(analyze(root("", operation("Get", "            BytecodeNode.get(node);"))), "perform", "get")
        self.assertEqual(guard.BYTECODE_NODE, sink.family)
        self.assertEqual(guard.BOUND_NODE, klass(sink, guard.ROLE_NODE))
        self.assertEqual(guard.PE_REACHABLE_PROVEN, sink.pe_reachability)

    def test_09b_canonical_bare_bind_is_proven_only_for_bytecode_node(self):
        bare = root("", operation("Get", "            BytecodeNode.get(bytecode);")).replace(
            '@Bind("$bytecodeNode") BytecodeNode', "@Bind BytecodeNode")
        sink = sink_in(analyze(bare), "perform", "get")
        self.assertEqual(guard.BOUND_NODE, klass(sink, guard.ROLE_NODE))
        self.assertEqual(guard.PE_REACHABLE_PROVEN, sink.pe_reachability)
        untyped = root("", operation("Get", "            BytecodeNode.get(node);")).replace(
            '@Bind("$node") Node', "@Bind Node")
        sink = sink_in(analyze(untyped), "perform", "get")
        self.assertNotEqual(guard.BOUND_NODE, klass(sink, guard.ROLE_NODE))

    def test_10_bytecode_node_get_with_runtime_node_is_a_risk(self):
        sinks = analyze(root("""
    Node child;
    static BytecodeNode find(Node location) {
        return BytecodeNode.get(location);
    }
""", operation("Get", "            find(node.getParent());")))
        sink = sink_in(sinks, "find", "get")
        self.assertEqual(guard.HELPER_PARAMETER, klass(sink, guard.ROLE_NODE))
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)
        self.assertEqual(guard.RISK, pe(sink, guard.ROLE_NODE))

    def test_11_update_with_static_final_or_api_config_is_proven(self):
        sinks = analyze(root("""
    static final BytecodeConfig CONFIG = BytecodeConfig.DEFAULT;
    static void refresh(BytecodeRootNodes<?> nodes) {
        nodes.update(CONFIG);
        nodes.update(BytecodeConfig.WITH_SOURCE);
    }
""", operation("Update", "            refresh(null);")))
        found = sinks_in(sinks, "refresh", "update")
        self.assertEqual([guard.STATIC_FINAL_FIELD, guard.API_CONSTANT],
                         [klass(sink, guard.ROLE_CONFIG) for sink in found])
        for sink in found:
            self.assertEqual(guard.BYTECODE_ROOT_NODES, sink.family)
            self.assertEqual(guard.PE_REACHABLE_PROVEN, sink.pe_reachability, sink.to_json())

    def test_12_update_with_unproven_config_is_a_risk(self):
        sinks = analyze(root("""
    BytecodeConfig chosen;
    static void refresh(BytecodeRootNodes<?> nodes, BytecodeConfig config) {
        nodes.update(config);
    }
    void caller(BytecodeRootNodes<?> nodes) {
        refresh(nodes, chosen);
    }
""", operation("Update", "            refresh(null, BytecodeConfig.DEFAULT);")))
        sink = sink_in(sinks, "refresh", "update")
        self.assertEqual(guard.PE_REACHABLE_PROVEN, sink.pe_reachability)  # 'caller' is no PE entry
        sinks = analyze(root("""
    static void refresh(BytecodeRootNodes<?> nodes, BytecodeConfig config) {
        nodes.update(config);
    }
""", operation("Update", "            refresh(null, (BytecodeConfig) value);")))
        sink = sink_in(sinks, "refresh", "update")
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)
        self.assertEqual(guard.RISK, pe(sink, guard.ROLE_CONFIG))

    def test_13_location_get_with_bound_node_is_proven(self):
        sink = sink_in(analyze(root("", operation("Where", "            BytecodeLocation.get(node, boundBci);"))),
                       "perform", "get")
        self.assertEqual(guard.BYTECODE_LOCATION, sink.family)
        self.assertEqual(2, sink.arity)
        self.assertEqual(guard.PE_REACHABLE_PROVEN, sink.pe_reachability)

    def test_14_location_get_stack_trace_wrapper_is_never_proven(self):
        sinks = analyze(root("""
    static BytecodeLocation where(TruffleStackTraceElement element) {
        return BytecodeLocation.get(element);
    }
""", operation("Where", "            where((TruffleStackTraceElement) value);")))
        sink = sink_in(sinks, "where", "get")
        self.assertEqual(1, sink.arity)
        self.assertEqual(guard.STACK_TRACE_DERIVED, klass(sink, guard.ROLE_NODE))
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)


class ReachabilityTest(unittest.TestCase):
    def test_15_truffle_boundary_cuts(self):
        sinks = analyze(root("""
    @TruffleBoundary
    static Object[] inside(BytecodeNode bytecode, TagTreeNode tag) {
        return bytecode.getLocalNames(tag.getEnterBytecodeIndex());
    }
    static Object[] below(BytecodeNode bytecode, TagTreeNode tag) {
        return bytecode.getLocalNames(tag.getEnterBytecodeIndex());
    }
    @TruffleBoundary
    static Object[] cut(BytecodeNode bytecode, TagTreeNode tag) {
        return below(bytecode, tag);
    }
""", operation("Cut", """
            inside(bytecode, null);
            cut(bytecode, null);""")))
        self.assertEqual(guard.BOUNDARY_CUT, sink_in(sinks, "inside").pe_reachability)
        self.assertEqual(guard.BOUNDARY_CUT, sink_in(sinks, "below").pe_reachability)

    def test_16_comments_and_strings_are_ignored(self):
        sinks = analyze(root("""
    // bytecode.getLocalNames(runtime); BytecodeNode.get(node);
    /* nodes.update(config); */
    static final String TEXT = "bytecode.getLocalNames(runtime) BytecodeLocation.get(element)";
"""))
        self.assertEqual([], sinks)

    def test_17_unresolved_receiver_type_fails_closed_when_reachable(self):
        sinks = analyze(root("""
    static Object[] reachable(Helper helper) {
        return helper.find().getLocalNames(0);
    }
    static Object[] unreachable(Helper helper) {
        return helper.find().getLocalNames(0);
    }
""", operation("Unresolved", "            reachable(null);")))
        sink = sink_in(sinks, "reachable")
        self.assertEqual(guard.FAMILY_UNRESOLVED, sink.family)
        self.assertEqual(guard.PE_REACHABILITY_UNKNOWN, sink.pe_reachability)
        self.assertEqual(guard.NOT_PE_REACHABLE, sink_in(sinks, "unreachable").pe_reachability)

    def test_18_overload_and_receiver_type_resolution(self):
        sinks = analyze(root("""
    static void other(BytecodeLocation location, java.util.Map<String, Object> map, Updater updater,
            BytecodeNode bytecode, TagTreeNode tag) {
        location.update();
        map.get("key");
        updater.update(tag.getEnterBytecodeIndex());
        bytecode.getLocalNames();
    }
    static final class Updater {
        void update(int value) {}
    }
""", operation("Overloads", "            other(null, null, null, bytecode, null);")))
        self.assertEqual([], sinks, [sink.to_json() for sink in sinks])

    def test_19_library_export_is_a_pe_entry(self):
        sinks = analyze(HEADER + """
@ExportLibrary(value = NodeLibrary.class, receiverType = TagTreeNode.class)
final class Exports {
    @ExportMessage
    static boolean hasScope(TagTreeNode node, Frame frame) {
        return names(node).length > 0;
    }
    private static Object[] names(TagTreeNode node) {
        BytecodeNode bytecode = node.getBytecodeNode();
        int bytecodeIndex = node.getEnterBytecodeIndex();
        return bytecode.getLocalNames(bytecodeIndex);
    }
}
""")
        sink = sink_in(sinks, "names")
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)
        self.assertIn("TRUFFLE_DSL", " ".join(sink.pe_paths[0]["entries"]))

    def test_20_externally_invoked_node_override_is_a_pe_entry(self):
        sinks = analyze(HEADER + """
final class Probe extends ExecutionEventNode {
    private final TagTreeNode tag;
    Probe(TagTreeNode tag) {
        this.tag = tag;
    }
    @Override
    protected void onEnter(VirtualFrame frame) {
        tag.getBytecodeNode().getLocalNames(tag.getEnterBytecodeIndex());
    }
}
""")
        sink = sink_in(sinks, "onEnter")
        self.assertEqual(guard.BYTECODE_NODE, sink.family)
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)

    def test_21_static_import_and_lambda(self):
        sinks = analyze(HEADER.replace("package p;", "package p;\nimport static com.oracle.truffle.api.bytecode."
                                       "BytecodeNode.get;") + """
final class Imported {
    @TruffleBoundary
    static BytecodeNode find(Node location) {
        return get(location);
    }
}
""", root("", operation("Lambda", """
            Runnable later = () -> bytecode.getLocalNames(0);""")))
        imported = sink_in(sinks, "find")
        self.assertEqual(guard.BYTECODE_NODE, imported.family)
        self.assertEqual(guard.BOUNDARY_CUT, imported.pe_reachability)
        self.assertEqual(guard.PE_REACHABILITY_UNKNOWN, sink_in(sinks, "perform").pe_reachability)


class CheckTest(unittest.TestCase):
    def test_exact_baseline_passes(self):
        status, _, candidate = run_check(SAFE_SOURCE, [])
        self.assertEqual(1, status)
        self.assertEqual(2, len(candidate))
        status, output, again = run_check(SAFE_SOURCE, candidate)
        self.assertEqual(0, status, output)
        self.assertEqual(candidate, again)
        self.assertIn("PE_REACHABLE_BYTECODE_INDEX_RISK=0", output)

    def test_new_topology_fails(self):
        _, _, candidate = run_check(SAFE_SOURCE, [])
        status, output, _ = run_check(SAFE_SOURCE, candidate[:1])
        self.assertEqual(1, status)
        self.assertIn("NEW_UNBASELINED_SITE", output)

    def test_stale_drifted_and_duplicate_entries_fail(self):
        _, _, candidate = run_check(SAFE_SOURCE, [])
        stale = dict(candidate[0], method="gone()")
        drifted = dict(candidate[1], pe_reachability=guard.NOT_PE_REACHABLE)
        status, output, _ = run_check(SAFE_SOURCE, [candidate[0], candidate[0], drifted, stale])
        self.assertEqual(1, status)
        self.assertIn("STALE_BASELINE_ENTRY", output)
        self.assertIn("TOPOLOGY_DRIFT", output)
        self.assertIn("DUPLICATE_BASELINE_ENTRY", output)

    def test_risks_fail_and_are_never_baselineable(self):
        status, output, candidate = run_check(RISK_SOURCE, [])
        self.assertEqual(1, status)
        self.assertIn("PE_REACHABLE_BYTECODE_INDEX_RISK", output)
        self.assertEqual([], candidate)
        sink = sink_in(analyze(RISK_SOURCE), "perform")
        entry = dict(sink.entry(), pe_reachability=guard.PE_REACHABLE_PROVEN)
        status, output, _ = run_check(RISK_SOURCE, [entry])
        self.assertEqual(1, status)
        self.assertIn("never baselineable", output)
        status, output, _ = run_check(RISK_SOURCE, [dict(entry, pe_reachability=guard.PE_REACHABLE_RISK)])
        self.assertIn("not baselineable", output)


if __name__ == "__main__":
    unittest.main(verbosity=2)
