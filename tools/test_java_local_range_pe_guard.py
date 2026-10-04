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

"""PERF030-F/G self-tests for tools/java_local_range_pe_guard.py (synthetic snippets only)."""

from __future__ import print_function

import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import java_local_range_pe_guard as guard  # noqa: E402

HEADER = """
package p;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
"""

HELPER = """
final class Helper {
    private final LocalRangeAccessor locals;
    private final Layout layout;
    Helper(LocalRangeAccessor locals, Layout layout) { this.locals = locals; this.layout = layout; }
%s
}
"""


def analyze(body, wrap=True):
    source = HEADER + (HELPER % body if wrap else body)
    return guard.analyze_file("p/T.java", source)


def only(sinks):
    assert len(sinks) == 1, [sink.to_json() for sink in sinks]
    return sinks[0]


def baseline_document(entries):
    return {"schema": guard.BASELINE_SCHEMA, "entries": entries}


def entry_for(sink, rationale="test"):
    entry = dict(zip(guard.BASELINE_KEYS, sink.key()))
    entry["rationale"] = rationale
    return entry


class ClassificationTest(unittest.TestCase):
    def test_constant_operand_ordinal_is_proven(self):
        sink = only(analyze("""
@Operation
@ConstantOperand(type = LocalRangeAccessor.class, name = "frameBackedLocals")
@ConstantOperand(type = int.class, name = "ordinal")
public static final class Bind {
    @Specialization
    public static void perform(
            LocalRangeAccessor frameBackedLocals,
            int ordinal,
            Object value,
            @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
            @Bind("$frame") VirtualFrame frame) {
        // frameBackedLocals.setObject(bytecodeNode, frame, 7, value) in a comment is ignored
        String text = "frameBackedLocals.clear(bytecodeNode, frame, ordinal)";
        frameBackedLocals
                .setObject(bytecodeNode,
                        frame, ordinal, value);
    }
}
"""))
        self.assertEqual(guard.PROVEN_CONSTANT_OPERAND, sink.classification)
        self.assertEqual("Helper.Bind", sink.type_name)
        self.assertEqual("setObject", sink.operation)
        self.assertEqual(HEADER.count("\n") + 21, sink.line)

    def test_constant_operand_name_alone_is_not_proof(self):
        # Same parameter name, but no positional int @ConstantOperand.
        sink = only(analyze("""
@Operation
@ConstantOperand(type = LocalRangeAccessor.class, name = "frameBackedLocals")
public static final class NotConstant {
    @Specialization
    public static boolean perform(LocalRangeAccessor frameBackedLocals, int ordinal,
            @Bind("$bytecodeNode") BytecodeNode bytecodeNode, @Bind("$frame") VirtualFrame frame) {
        return frameBackedLocals.isCleared(bytecodeNode, frame, ordinal);
    }
}
"""))
        self.assertEqual(guard.METHOD_PARAMETER, sink.classification)

    def test_ordinary_method_parameter_is_not_safe(self):
        sink = only(analyze("""
    Object read(BytecodeNode node, Frame frame, int ordinal) {
        return locals.getObject(node, frame, ordinal);
    }
"""))
        self.assertEqual(guard.METHOD_PARAMETER, sink.classification)
        self.assertEqual("read(BytecodeNode,Frame,int)", sink.method)
        self.assertEqual("locals", sink.receiver)

    def test_offset_of_local_is_runtime_name_derived(self):
        sink = only(analyze("""
    boolean contains(BytecodeNode node, Frame frame, String name) {
        Integer offset = layout.offsetOf(name);
        return offset != null && !locals.isCleared(node, frame, offset);
    }
"""))
        self.assertEqual(guard.RUNTIME_NAME_DERIVED, sink.classification)

    def test_offset_of_through_one_alias_is_runtime_name_derived(self):
        sink = only(analyze("""
    boolean contains(BytecodeNode node, Frame frame, String name) {
        Integer offset = layout.offsetOf(name);
        int slot = offset;
        return !this.locals.isCleared(node, frame, slot);
    }
"""))
        self.assertEqual(guard.RUNTIME_NAME_DERIVED, sink.classification)
        self.assertIn("alias", sink.reason)
        self.assertEqual("this.locals", sink.receiver)

    def test_loop_ordinal_is_loop_index(self):
        sinks = analyze("""
    boolean empty(BytecodeNode node, Frame frame) {
        for (int ordinal = 0; ordinal < layout.length(); ordinal++) {
            if (!locals.isCleared(node, frame, ordinal)) {
                return false;
            }
        }
        for (int ordinal = 0; ordinal < layout.length(); ordinal++) {
            locals.clear(node, frame, ordinal);
        }
        return true;
    }
""")
        self.assertEqual([guard.LOOP_INDEX, guard.LOOP_INDEX], [sink.classification for sink in sinks])
        self.assertIn("ordinal<layout.length()", sinks[0].reason)

    def test_loop_variable_written_in_body_is_unknown(self):
        sink = only(analyze("""
    void skip(BytecodeNode node, Frame frame) {
        for (int ordinal = 0; ordinal < layout.length(); ordinal++) {
            ordinal += 1;
            locals.clear(node, frame, ordinal);
        }
    }
"""))
        self.assertEqual(guard.UNKNOWN, sink.classification)

    def test_truffle_boundary_method(self):
        sinks = analyze("""
    @TruffleBoundary
    void put(BytecodeNode node, Frame frame, String name, Object value) {
        Integer offset = layout.offsetOf(name);
        locals.setObject(node, frame, offset, value);
    }

    void caller(BytecodeNode node, Frame frame, int ordinal) {
        put(node, frame, "x", null);
        locals.clear(node, frame, ordinal);
    }
""")
        self.assertEqual(
            [guard.TRUFFLE_BOUNDARY, guard.METHOD_PARAMETER],
            [sink.classification for sink in sinks],
        )
        self.assertIn(guard.RUNTIME_NAME_DERIVED, sinks[0].reason)

    def test_unrelated_receivers_are_not_sinks(self):
        sinks = analyze("""
    void unrelated(BytecodeNode node, Frame frame, java.util.Map<String, Object> map, Other other,
            LocalAccessor accessor) {
        other.getObject(node, frame, 3);
        other.setObject(node, frame, 3, null);
        accessor.getObject(node, frame);
        accessor.setObject(node, frame, map);
        map.clear();
        Statics.getObject(node, frame, 3);
    }
""")
        self.assertEqual([], [sink.to_json() for sink in sinks])

    def test_direct_literal_is_direct_constant(self):
        sink = only(analyze("""
    Object first(BytecodeNode node, Frame frame) {
        return locals.getObject(node, frame, 0);
    }
"""))
        self.assertEqual(guard.DIRECT_CONSTANT, sink.classification)

    def test_ambiguous_provenance_is_unknown(self):
        sinks = analyze("""
    private int cursor;

    Object ambiguous(BytecodeNode node, Frame frame, int base, java.util.List<Integer> ordinals) {
        Object a = locals.getObject(node, frame, base + 1);
        Object b = locals.getObject(node, frame, cursor);
        int computed = compute();
        Object c = locals.getObject(node, frame, computed);
        for (int each : ordinals) {
            locals.clear(node, frame, each);
        }
        Runnable r = () -> locals.clear(node, frame, base);
        holder().locals.clear(node, frame, 1);
        var alias = locals;
        alias.clear(node, frame, 1);
        mystery.clear(node, frame, 1);
        return a;
    }
""")
        self.assertEqual(8, len(sinks), [sink.to_json() for sink in sinks])
        self.assertEqual([guard.UNKNOWN] * 8, [sink.classification for sink in sinks])

    def test_unterminated_comment_fails_closed(self):
        with self.assertRaises(guard.GuardError):
            analyze("/* never closed\n void f() { locals.clear(a, b, 0); }")


class BaselineTest(unittest.TestCase):
    SOURCE = HEADER + HELPER % """
    Object read(BytecodeNode node, Frame frame, int ordinal) {
        return locals.getObject(node, frame, ordinal);
    }

    boolean contains(BytecodeNode node, Frame frame, String name) {
        Integer offset = layout.offsetOf(name);
        return !locals.isCleared(node, frame, offset);
    }
"""

    def run_check(self, source, entries):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java = root / "src" / "p" / "Helper.java"
            java.parent.mkdir(parents=True)
            java.write_text(source, encoding="utf-8")
            baseline = root / "baseline.json"
            baseline.write_text(json.dumps(baseline_document(entries)), encoding="utf-8")
            report = root / "target" / "report.json"
            out = io.StringIO()
            status = guard.check(root / "src", baseline, report, root, out)
            document = json.loads(report.read_text(encoding="utf-8")) if report.exists() else None
            return status, out.getvalue(), document

    def current_entries(self):
        return [entry_for(sink) for sink in guard.analyze_file("src/p/Helper.java", self.SOURCE)]

    def test_exact_baseline_passes(self):
        status, output, document = self.run_check(self.SOURCE, self.current_entries())
        self.assertEqual(0, status, output)
        self.assertEqual("PASS", document["result"])
        self.assertEqual(2, document["totals"]["BASELINED_RISKS"])
        self.assertEqual(0, document["totals"]["UNKNOWN"])
        self.assertIn("NOT proven PE-safe", output)

    def test_new_sink_absent_from_baseline_fails(self):
        source = self.SOURCE.replace(
            "        return locals.getObject(node, frame, ordinal);",
            "        locals.clear(node, frame, ordinal);\n        return locals.getObject(node, frame, ordinal);",
        )
        status, output, document = self.run_check(source, self.current_entries())
        self.assertEqual(1, status)
        self.assertIn("NEW_UNBASELINED_RISK", output)
        self.assertEqual(1, document["totals"]["NEW_UNBASELINED_RISKS"])

    def test_changed_provenance_fails(self):
        source = self.SOURCE.replace("Integer offset = layout.offsetOf(name);", "Integer offset = 2 * name.length();")
        status, output, _ = self.run_check(source, self.current_entries())
        self.assertEqual(1, status)
        self.assertIn("UNKNOWN", output)
        self.assertIn("STALE_BASELINE_ENTRY", output)

    def test_stale_baseline_entry_fails(self):
        entries = self.current_entries()
        stale = dict(entries[0])
        stale["method"] = "removed(BytecodeNode,Frame,int)"
        status, output, _ = self.run_check(self.SOURCE, entries + [stale])
        self.assertEqual(1, status)
        self.assertIn("STALE_BASELINE_ENTRY", output)

    def test_duplicate_baseline_entry_fails(self):
        entries = self.current_entries()
        status, output, _ = self.run_check(self.SOURCE, entries + [dict(entries[0])])
        self.assertEqual(1, status)
        self.assertIn("DUPLICATE_BASELINE_ENTRY", output)

    def test_wildcard_and_safe_entries_are_rejected(self):
        entries = self.current_entries()
        wildcard = dict(entries[0])
        wildcard["method"] = "*"
        status, output, _ = self.run_check(self.SOURCE, [wildcard] + entries[1:])
        self.assertEqual(1, status)
        self.assertIn("ANALYSIS_FAILED", output)
        safe = dict(entries[0])
        safe["classification"] = guard.TRUFFLE_BOUNDARY
        status, output, _ = self.run_check(self.SOURCE, [safe] + entries[1:])
        self.assertEqual(1, status)
        self.assertIn("ANALYSIS_FAILED", output)

    def test_unanalyzable_source_fails(self):
        status, output, _ = self.run_check(self.SOURCE + "\n}", self.current_entries())
        self.assertEqual(1, status)
        self.assertIn("ANALYSIS_FAILED", output)



# --------------------------------------------------------------------------
# PERF030-G: PE reachability
# --------------------------------------------------------------------------

BIND = '@Bind("$bytecodeNode") BytecodeNode node, @Bind("$frame") VirtualFrame frame'
CONSTANT_ORDINAL = """
    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class, name = "locals")
    @ConstantOperand(type = int.class, name = "ordinal")
"""
RUNTIME_ORDINAL = """
    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class, name = "locals")
"""
HELPER_SIG = "Root.helper(LocalRangeAccessor,BytecodeNode,VirtualFrame,int)"


def operation(name, annotations, body, extra_params=""):
    return """%s
    public static final class %s {
        @Specialization
        public static Object perform(LocalRangeAccessor locals, int ordinal%s, %s) {
            %s
        }
    }
""" % (annotations, name, extra_params, BIND, body)


def root_key(name, extra=""):
    return "Root.%s.perform(LocalRangeAccessor,int%s,BytecodeNode,VirtualFrame)" % (name, extra)


def reach(source, label="p/Root.java"):
    sinks, parsed = guard.parse_sources([(label, HEADER + source)])
    guard.analyze_reachability(sinks, parsed)
    return sinks


def sink_in(sinks, method_prefix):
    found = [sink for sink in sinks if sink.method.startswith(method_prefix + "(")]
    assert len(found) == 1, [(sink.type_name, sink.method) for sink in sinks]
    return found[0]


HELPER_METHOD = """
    static Object helper(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, int index) {
        return locals.getObject(node, frame, index);
    }
"""


class ReachabilityTest(unittest.TestCase):
    def test_01_direct_constant_operand_root(self):
        sinks = reach("final class Root {%s}" % operation(
            "Read", CONSTANT_ORDINAL, "return locals.getObject(node, frame, ordinal);"))
        sink = only(sinks)
        self.assertEqual(guard.PROVEN_CONSTANT_OPERAND, sink.classification)
        self.assertEqual(guard.PE_REACHABLE_PROVEN_CONSTANT, sink.pe_reachability)
        self.assertEqual([guard.PROV_OPERATION_CONSTANT], sink.pe_provenance)
        self.assertEqual([root_key("Read")], sink.pe_call_chain)

    def test_02_constant_through_one_helper(self):
        sinks = reach("final class Root {%s%s}" % (HELPER_METHOD, operation(
            "Read", CONSTANT_ORDINAL, "return helper(locals, node, frame, ordinal);")))
        sink = only(sinks)
        self.assertEqual(guard.METHOD_PARAMETER, sink.classification)
        self.assertEqual(guard.PE_REACHABLE_PROVEN_CONSTANT, sink.pe_reachability)
        self.assertEqual([guard.PROV_OPERATION_CONSTANT], sink.pe_provenance)
        self.assertEqual([root_key("Read"), HELPER_SIG], sink.pe_call_chain)

    def test_03_constant_through_two_helpers(self):
        sinks = reach("""final class Root {
    static Object outer(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, int slot) {
        int alias = slot;
        return Root.helper(locals, node, frame, alias);
    }
%s%s}""" % (HELPER_METHOD, operation("Read", CONSTANT_ORDINAL, "return outer(locals, node, frame, ordinal);")))
        sink = only(sinks)
        self.assertEqual(guard.PE_REACHABLE_PROVEN_CONSTANT, sink.pe_reachability)
        self.assertEqual(
            [root_key("Read"), "Root.outer(LocalRangeAccessor,BytecodeNode,VirtualFrame,int)", HELPER_SIG],
            sink.pe_call_chain)

    def test_04_runtime_name_derived_from_root(self):
        sinks = reach("""final class Root {
    static boolean contains(LocalRangeAccessor locals, Layout layout, BytecodeNode node, VirtualFrame frame, String name) {
        Integer offset = layout.offsetOf(name);
        return !locals.isCleared(node, frame, offset);
    }
%s}""" % operation("Contains", CONSTANT_ORDINAL, "return contains(locals, layout, node, frame, name);",
                   ", Layout layout, String name"))
        sink = only(sinks)
        self.assertEqual(guard.RUNTIME_NAME_DERIVED, sink.classification)
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)
        self.assertEqual([guard.RUNTIME_NAME_DERIVED], sink.pe_provenance)
        self.assertEqual(
            [root_key("Contains", ",Layout,String"),
             "Root.contains(LocalRangeAccessor,Layout,BytecodeNode,VirtualFrame,String)"],
            sink.pe_call_chain)

    def test_05_runtime_operand_from_root(self):
        sinks = reach("final class Root {%s%s}" % (HELPER_METHOD, operation(
            "Read", RUNTIME_ORDINAL, "return helper(locals, node, frame, ordinal);")))
        sink = only(sinks)
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)
        self.assertEqual([guard.PROV_RUNTIME_OPERAND], sink.pe_provenance)
        self.assertEqual([root_key("Read"), HELPER_SIG], sink.pe_call_chain)
        # Directly inside a root, a runtime operand is a one-element risk chain.
        direct = only(reach("final class Root {%s}" % operation(
            "Direct", RUNTIME_ORDINAL, "return locals.getObject(node, frame, ordinal);")))
        self.assertEqual(guard.METHOD_PARAMETER, direct.classification)
        self.assertEqual(guard.PE_REACHABLE_RISK, direct.pe_reachability)
        self.assertEqual([root_key("Direct")], direct.pe_call_chain)

    def test_06_same_helper_constant_and_runtime_paths(self):
        sinks = reach("final class Root {%s%s%s}" % (
            HELPER_METHOD,
            operation("Constant", CONSTANT_ORDINAL, "return helper(locals, node, frame, ordinal);"),
            operation("Runtime", RUNTIME_ORDINAL, "return helper(locals, node, frame, ordinal);")))
        sink = only(sinks)
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)
        self.assertEqual([guard.PROV_OPERATION_CONSTANT, guard.PROV_RUNTIME_OPERAND], sink.pe_provenance)
        self.assertEqual([root_key("Runtime"), HELPER_SIG], sink.pe_call_chain)
        self.assertEqual(2, len(sink.pe_paths))

    def test_07_reachable_only_through_truffle_boundary(self):
        sinks = reach("""final class Root {
    @TruffleBoundary
    static Object slow(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, int index) {
        return helper(locals, node, frame, index);
    }
%s%s}""" % (HELPER_METHOD, operation("Read", RUNTIME_ORDINAL, "return slow(locals, node, frame, ordinal);")))
        sink = only(sinks)
        self.assertEqual(guard.METHOD_PARAMETER, sink.classification)
        self.assertEqual(guard.BOUNDARY_CUT, sink.pe_reachability)
        self.assertEqual(["Root.slow(LocalRangeAccessor,BytecodeNode,VirtualFrame,int)", HELPER_SIG],
                         sink.pe_call_chain)
        self.assertEqual([], sink.pe_provenance)

    def test_08_not_reachable_from_any_root(self):
        sinks = reach("""final class Root {
    static Object unused(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame) {
        return helper(locals, node, frame, 4);
    }
%s%s}""" % (HELPER_METHOD, operation("Other", RUNTIME_ORDINAL, "return null;")))
        sink = only(sinks)
        self.assertEqual(guard.NOT_PE_REACHABLE, sink.pe_reachability)
        self.assertEqual([], sink.pe_call_chain)
        self.assertEqual([], sink.pe_unknown)

    def test_09_ambiguous_overload_is_unknown(self):
        sinks = reach("""final class Root {
    static Object helper(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, Object index) {
        return null;
    }
%s%s}""" % (HELPER_METHOD, operation("Read", RUNTIME_ORDINAL,
                                     "return helper(locals, node, frame, compute(value));", ", Object value")))
        sink = only(sinks)
        self.assertEqual(guard.PE_REACHABILITY_UNKNOWN, sink.pe_reachability)
        self.assertIn("ambiguous overload", sink.pe_unknown[0])
        # A statically typed argument disambiguates the same overload pair.
        resolved = only(reach("""final class Root {
    static Object helper(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, Object index) {
        return null;
    }
%s%s}""" % (HELPER_METHOD, operation("Read", RUNTIME_ORDINAL, "return helper(locals, node, frame, ordinal);"))))
        self.assertEqual(guard.PE_REACHABLE_RISK, resolved.pe_reachability)

    def test_10_unknown_receiver_reaching_a_sink_is_unknown(self):
        source = """final class Helper {
    Object read(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, int index) {
        return locals.getObject(node, frame, index);
    }
}
final class Unrelated {
    Object read(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, int index) {
        return null;
    }
}
final class Root {%s}"""
        sinks = reach(source % operation("Read", RUNTIME_ORDINAL, "return mystery.read(locals, node, frame, 1);"))
        sink = only(sinks)
        self.assertEqual(guard.PE_REACHABILITY_UNKNOWN, sink.pe_reachability)
        self.assertIn("mystery", sink.pe_unknown[0])
        self.assertIn("reaches PE root", sink.pe_unknown[0])
        # A statically known unrelated receiver is not an edge at all.
        sinks = reach(source % operation(
            "Read", RUNTIME_ORDINAL, "return unrelated.read(locals, node, frame, 1);", ", Unrelated unrelated"))
        self.assertEqual(guard.NOT_PE_REACHABLE, only(sinks).pe_reachability)
        # An unresolved receiver in a method no PE root reaches is immaterial.
        sinks = reach((source % operation("Read", RUNTIME_ORDINAL, "return null;")).replace(
            "final class Root {", "final class Offline {\n    Object go(LocalRangeAccessor locals, BytecodeNode node, "
            "VirtualFrame frame) { return mystery.read(locals, node, frame, 1); }\n}\nfinal class Root {"))
        self.assertEqual(guard.NOT_PE_REACHABLE, only(sinks).pe_reachability)

    def test_11_recursive_call_graph_terminates_deterministically(self):
        source = """final class Root {
    static Object a(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, int index, int depth) {
        return depth > 0 ? b(locals, node, frame, index, depth - 1) : null;
    }
    static Object b(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, int index, int depth) {
        if (depth > 1) {
            return a(locals, node, frame, index, depth);
        }
        return locals.getObject(node, frame, index);
    }
%s}""" % operation("Read", RUNTIME_ORDINAL, "return a(locals, node, frame, ordinal, 3);")
        first = only(reach(source))
        self.assertEqual(guard.PE_REACHABLE_RISK, first.pe_reachability)
        self.assertEqual(
            [root_key("Read"),
             "Root.a(LocalRangeAccessor,BytecodeNode,VirtualFrame,int,int)",
             "Root.b(LocalRangeAccessor,BytecodeNode,VirtualFrame,int,int)"],
            first.pe_call_chain)
        self.assertEqual(first.to_json(), only(reach(source)).to_json())

    def test_interface_dispatch_reaches_implementation(self):
        sinks = reach("""interface Authority {
    boolean isEmpty();
}
final class FrameAuthority implements Authority {
    private final LocalRangeAccessor locals;
    private final BytecodeNode node;
    private final VirtualFrame frame;
    @Override
    public boolean isEmpty() {
        for (int ordinal = 0; ordinal < 3; ordinal++) {
            if (!locals.isCleared(node, frame, ordinal)) {
                return false;
            }
        }
        return true;
    }
}
final class Root {%s%s}""" % (
            operation("Names", RUNTIME_ORDINAL, "return names.isEmpty() || names.get(0).isEmpty();",
                      ", java.util.List<String> names"),
            operation("Dispatch", RUNTIME_ORDINAL, "return holder.authority().isEmpty();", ", Holder holder"),
        ) + """
final class Holder {
    Authority authority() { return null; }
}""")
        sink = only(sinks)
        self.assertEqual(guard.LOOP_INDEX, sink.classification)
        self.assertEqual(guard.PE_REACHABLE_RISK, sink.pe_reachability)
        self.assertEqual([guard.LOOP_INDEX], sink.pe_provenance)
        self.assertEqual([root_key("Dispatch", ",Holder"), "FrameAuthority.isEmpty()"], sink.pe_call_chain)
        self.assertEqual(1, sink.pe_paths[0]["roots"])  # List<String>.isEmpty() is not an edge

    def test_pattern_bindings_and_contextual_names_resolve_receivers(self):
        source = """interface Authority {
    boolean isEmpty();
}
final class FrameAuthority implements Authority {
    private final LocalRangeAccessor locals;
    private final BytecodeNode node;
    private final VirtualFrame frame;
    Object readAt(int ordinal) {
        return locals.getObject(node, frame, ordinal);
    }
    @Override
    public boolean isEmpty() {
        for (int ordinal = 0; ordinal < 3; ordinal++) {
            if (!locals.isCleared(node, frame, ordinal)) {
                return false;
            }
        }
        return true;
    }
}
final class Module {
    void markReady() {}
    void compose(FrameAuthority source, java.util.List<String> names) { source.isEmpty(); }
    void compose(FrameAuthority source, java.util.Set<String> names) {}
}
final class Root {%s%s%s}""" % (
            operation("Positive", RUNTIME_ORDINAL,
                      "if (value instanceof FrameAuthority authority && authority.isEmpty()) { "
                      "return authority.readAt(ordinal); } return null;", ", Object value"),
            operation("Negated", RUNTIME_ORDINAL,
                      "if (!(value instanceof java.util.Optional<?> body)) { return null; } "
                      "Module record = new Module(); record.markReady(); return body.isEmpty();",
                      ", Object value"),
            operation("Overload", RUNTIME_ORDINAL,
                      "if (!(value instanceof FrameAuthority source)) { return null; } "
                      "new Module().compose(source, names); return null;",
                      ", Object value, java.util.List<String> names"))
        sinks = reach(source)
        self.assertEqual([], [sink.pe_unknown for sink in sinks if sink.pe_unknown])
        read = sink_in(sinks, "readAt")
        self.assertEqual(guard.PE_REACHABLE_RISK, read.pe_reachability)
        self.assertEqual([guard.PROV_RUNTIME_OPERAND], read.pe_provenance)
        self.assertEqual([root_key("Positive", ",Object"), "FrameAuthority.readAt(int)"], read.pe_call_chain)
        empty = sink_in(sinks, "isEmpty")
        self.assertEqual(guard.PE_REACHABLE_RISK, empty.pe_reachability)
        # Positive (direct) and Overload (via the exact List overload); the
        # Optional pattern binding in Negated is not an edge.
        self.assertEqual(2, empty.pe_paths[0]["roots"])

    def test_15_representative_path_is_deterministic(self):
        source = "final class Root {%s%s%s}" % (
            HELPER_METHOD,
            operation("Beta", RUNTIME_ORDINAL, "return helper(locals, node, frame, ordinal);"),
            operation("Alpha", RUNTIME_ORDINAL, "return helper(locals, node, frame, ordinal);"))
        runs = [json.dumps([sink.to_json() for sink in reach(source)], sort_keys=True) for _ in range(3)]
        self.assertEqual(1, len(set(runs)))
        self.assertEqual(2, only(reach(source)).pe_paths[0]["roots"])


class ReachabilityBaselineTest(unittest.TestCase):
    SOURCE = HEADER + "final class Root {%s%s%s}" % (
        HELPER_METHOD,
        operation("Read", RUNTIME_ORDINAL, "return helper(locals, node, frame, ordinal);"),
        """
    static Object offline(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, int index) {
        return locals.getObject(node, frame, index);
    }
""")

    def run_check(self, source, reachability_entries):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java = root / "src" / "p" / "Root.java"
            java.parent.mkdir(parents=True)
            java.write_text(source, encoding="utf-8")
            sinks, _ = guard.scan_tree(root / "src", root)
            baseline = root / "baseline.json"
            baseline.write_text(json.dumps(baseline_document(
                [entry_for(sink) for sink in sinks if sink.classification in guard.RISK_CLASSIFICATIONS])),
                encoding="utf-8")
            reachability = root / "reachability.json"
            reachability.write_text(json.dumps(
                {"schema": guard.REACHABILITY_BASELINE_SCHEMA, "entries": reachability_entries}), encoding="utf-8")
            report = root / "target" / "report.json"
            out = io.StringIO()
            status = guard.check(root / "src", baseline, report, root, out, reachability_baseline=reachability)
            document = json.loads(report.read_text(encoding="utf-8")) if report.exists() else None
            candidate_path = report.parent / guard.REACHABILITY_CANDIDATE_NAME
            candidate = json.loads(candidate_path.read_text(encoding="utf-8")) if candidate_path.exists() else None
            return status, out.getvalue(), document, candidate

    def current_entries(self, source=None):
        return self.run_check(source or self.SOURCE, [])[3]["entries"]

    def test_exact_reachability_baseline_passes(self):
        entries = self.current_entries()
        self.assertEqual(
            [guard.PE_REACHABLE_RISK, guard.NOT_PE_REACHABLE], [entry["pe_reachability"] for entry in entries])
        status, output, document, _ = self.run_check(self.SOURCE, entries)
        self.assertEqual(0, status, output)
        self.assertEqual(1, document["totals"][guard.PE_REACHABLE_RISK])
        self.assertEqual(1, document["totals"][guard.NOT_PE_REACHABLE])
        self.assertEqual(0, document["totals"][guard.PE_REACHABILITY_UNKNOWN])
        self.assertIn("PE reachability analysis complete", output)
        self.assertIn("PE_REACHABLE_RISK=1 site(s) remain and are NOT proven safe", output)
        self.assertIn("-> Root.helper", output)

    def test_12_stale_reachability_entry_fails(self):
        entries = self.current_entries()
        stale = dict(entries[0])
        stale["method"] = "removed(LocalRangeAccessor,BytecodeNode,VirtualFrame,int)"
        status, output, document, _ = self.run_check(self.SOURCE, entries + [stale])
        self.assertEqual(1, status)
        self.assertIn("STALE_REACHABILITY_ENTRY", output)
        self.assertEqual(1, document["totals"]["STALE_REACHABILITY_ENTRIES"])

    def test_13_newly_pe_reachable_risk_fails(self):
        entries = self.current_entries()
        source = self.SOURCE.replace("return helper(locals, node, frame, ordinal);",
                                     "offline(locals, node, frame, ordinal);\n            "
                                     "return helper(locals, node, frame, ordinal);")
        status, output, _, _ = self.run_check(source, entries)
        self.assertEqual(1, status)
        self.assertIn("REACHABILITY_DRIFT", output)
        self.assertIn('expected "NOT_PE_REACHABLE" actual "PE_REACHABLE_RISK"', output)
        # A sink absent from the reachability baseline is reported as new.
        status, output, document, _ = self.run_check(self.SOURCE, entries[:1])
        self.assertEqual(1, status)
        self.assertIn("NEW_UNBASELINED_REACHABILITY", output)
        self.assertEqual(1, document["totals"]["NEW_UNBASELINED_REACHABILITY"])

    def test_14_removed_pe_reachable_risk_makes_entry_stale(self):
        entries = self.current_entries()
        source = self.SOURCE.replace("return helper(locals, node, frame, ordinal);", "return null;")
        source = source.replace(HELPER_METHOD, "")
        status, output, _, _ = self.run_check(source, [entries[1]] + entries[:1])
        self.assertEqual(1, status)
        self.assertIn("STALE_REACHABILITY_ENTRY", output)

    def test_chain_and_boundary_drift_fail(self):
        entries = self.current_entries()
        slow = """
    @TruffleBoundary
    static Object slow(LocalRangeAccessor locals, BytecodeNode node, VirtualFrame frame, int index) {
        return helper(locals, node, frame, index);
    }
"""
        bounded = self.SOURCE.replace(HELPER_METHOD, HELPER_METHOD + slow).replace(
            "return helper(locals, node, frame, ordinal);", "return slow(locals, node, frame, ordinal);")
        bounded_entries = self.current_entries(bounded)
        self.assertEqual(guard.BOUNDARY_CUT, bounded_entries[0]["pe_reachability"])
        status, output, _, _ = self.run_check(bounded, bounded_entries)
        self.assertEqual(0, status, output)
        status, output, _, _ = self.run_check(bounded.replace("    @TruffleBoundary\n", ""), bounded_entries)
        self.assertEqual(1, status)
        self.assertIn('pe_reachability expected "BOUNDARY_CUT" actual "PE_REACHABLE_RISK"', output)
        changed = [dict(entry) for entry in entries]
        changed[0]["pe_call_chain"] = list(reversed(changed[0]["pe_call_chain"]))
        status, output, _, _ = self.run_check(self.SOURCE, changed)
        self.assertEqual(1, status)
        self.assertIn("pe_call_chain expected", output)

    def test_unknown_reachability_is_never_baselineable(self):
        entries = self.current_entries()
        unknown = dict(entries[0])
        unknown["pe_reachability"] = guard.PE_REACHABILITY_UNKNOWN
        status, output, _, _ = self.run_check(self.SOURCE, [unknown] + entries[1:])
        self.assertEqual(1, status)
        self.assertIn("ANALYSIS_FAILED", output)


if __name__ == "__main__":
    unittest.main()
