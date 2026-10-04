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

"""PERF030-F self-tests for tools/java_local_range_pe_guard.py (synthetic snippets only)."""

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


if __name__ == "__main__":
    unittest.main()
