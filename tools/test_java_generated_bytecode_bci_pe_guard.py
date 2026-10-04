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


"""TEST009-D self-tests for tools/java_generated_bytecode_bci_pe_guard.py (synthetic generated Java only)."""

from __future__ import print_function

import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import java_generated_bytecode_bci_pe_guard as guard  # noqa: E402

ROOT_TEMPLATE = """
package p;

public final class %(root)s extends ProtosRoot {

    @Override
    public Object execute(VirtualFrame frame) {
        return continueAt(bytecode, 0, stackBase, (FrameWithoutBoxing) frame, null);
    }

    private Object continueAt(AbstractBytecodeNode bc, long bci, long sp, FrameWithoutBoxing frame, ContinuationRootNodeImpl continuationRootNode) {
        boolean wasCompiled = CompilerDirectives.inCompiledCode();
        long state = ((sp & 0xFFFFFFFFL) << 32) | (bci & 0xFFFFFFFFL);
        while (true) {
            state = bc.continueAt(this, frame, state, continuationRootNode);
            if ((int) state == 0xFFFFFFFF) {
                break;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                state = oldBytecode.transition(bc, state, frame, continuationRootNode, wasCompiled);
            }
        }
        return null;
    }

    private abstract static class AbstractBytecodeNode extends BytecodeNode {
        @CompilationFinal(dimensions = 1) final byte[] bytecodes;
        @CompilationFinal(dimensions = 1) final int[] handlers;
        @Child TagRootNode tagRoot;

        abstract long continueAt(%(root)s $root, FrameWithoutBoxing frame, long startState, ContinuationRootNodeImpl continuationRootNode);
    }

%(cached)s

    private static final class UncachedBytecodeNode extends AbstractBytecodeNode {
        @Override
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        long continueAt(%(root)s $root, FrameWithoutBoxing frame_, long startState, ContinuationRootNodeImpl continuationRootNode) {
            long bci = (int) startState;
            loop: while (true) {
                CompilerAsserts.partialEvaluationConstant(bci);
                bci = this.someField;
            }
        }
    }

    private static final class TagNode extends TagTreeNode {
        final int enterBci;
        @CompilationFinal int returnBci;
    }

    private static final class TagRootNode extends Node {
        @CompilationFinal(dimensions = 1) final TagNode[] tagNodes;
    }

    private static final class ContinuationRootNodeImpl extends ContinuationRootNode {
        final %(root)s root;
        final int sp;
        @CompilationFinal volatile BytecodeLocation location;

        @Override
        public Object execute(VirtualFrame frame_) {
            BytecodeLocation bytecodeLocation = location;
            AbstractBytecodeNode bytecodeNode = (AbstractBytecodeNode) bytecodeLocation.getBytecodeNode();
            return root.continueAt(bytecodeNode, bytecodeLocation.getBytecodeIndex(), sp, targetFrame, this);
        }
    }
}
"""

CACHED_TEMPLATE = """
    private static final class %(name)s extends AbstractBytecodeNode implements BytecodeOSRNode {

        @Override
        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        long continueAt(%(root)s $root, FrameWithoutBoxing frame_, long startState, ContinuationRootNodeImpl continuationRootNode) {
            byte[] bc = ACCESS.uncheckedCast(this.bytecodes, byte[].class);
            long bci = (int) startState;
            long sp = ((int) (startState >>> 32));
            loop: while (true) {
                %(assertion)s
                int op = BYTES.getShort(bc, bci);
                try {
                    switch (op) {
%(cases)s
                        case Instructions.RETURN :
                            return handleReturn(frame, bc, bci, sp);
                    }
                } catch (Throwable originalThrowable) {
                    long state = handleException(frame, bc, bci, sp, originalThrowable, 0);
                    bci = (int) state;
                    if (bci == 0xFFFFFFFF) {
                        return state;
                    }
                    sp = ((int) (state >>> 32));
                }
            }
        }

%(helpers)s

        private long handleControlFlowException(FrameWithoutBoxing frame, byte[] bc, long bci, long sp, ControlFlowException cfe) throws Throwable {
            return ((root.stackBase & 0xFFFFFFFFL) << 32) | 0xFFFFFFFFL;
        }

        private long handleException(FrameWithoutBoxing frame, byte[] bc, long originalBci, long originalSp, Throwable originalThrowable, int counter) {
            long bci = originalBci;
            long sp = originalSp;
            if (throwable instanceof ControlFlowException cfe) {
                long target = handleControlFlowException(frame, bc, bci, sp, cfe);
                return target;
            }
            int[] handlerTable = this.handlers;
            int handler = -EXCEPTION_HANDLER_LENGTH;
            while ((handler = resolveHandler(bci, handler + EXCEPTION_HANDLER_LENGTH, handlerTable)) != -1) {
                switch (handlerTable[handler + EXCEPTION_HANDLER_OFFSET_KIND]) {
                    case HANDLER_TAG_EXCEPTIONAL :
                        TagNode node = this.tagRoot.tagNodes[handlerTable[handler + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]];
                        if (result == ProbeNode.UNWIND_ACTION_REENTER) {
                            bci = node.enterBci;
                        } else {
                            bci = node.returnBci + 8;
                        }
                        break;
                    default :
                        %(handler_bci)s
                        break;
                }
                return ((sp & 0xFFFFFFFFL) << 32) | (bci & 0xFFFFFFFFL);
            }
            throw sneakyThrow(throwable);
        }

        @ExplodeLoop
        private int resolveHandler(long bci, int handler, int[] localHandlers) {
            return -1;
        }

        private long handleReturn(FrameWithoutBoxing frame, byte[] bc, long bci, long sp) {
            return 0xFFFFFFFF;
        }

        @Override
        public Object executeOSR(VirtualFrame frame, long target, Object unused) {
            return continueAt(getRoot(), (FrameWithoutBoxing) frame, target, null);
        }
    }
"""

DEFAULT_CASES = """
                        case Instructions.NEXT :
                            bci += 4;
                            break;
"""

DEFAULT_HANDLER_BCI = "bci = handlerTable[handler + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI];"


def helper(name, body, params="FrameWithoutBoxing frame, byte[] bc, long bci, long sp"):
    return "        private long %s(%s) {\n%s\n        }\n" % (name, params, body)


def cached(root, name="CachedBytecodeNode", cases=DEFAULT_CASES, helpers="",
           assertion="CompilerAsserts.partialEvaluationConstant(bci);", handler_bci=DEFAULT_HANDLER_BCI):
    return CACHED_TEMPLATE % dict(root=root, name=name, cases=cases, helpers=helpers, assertion=assertion,
                                  handler_bci=handler_bci)


def root_source(root, *cached_classes):
    return ROOT_TEMPLATE % dict(root=root, cached="\n".join(cached_classes) or cached(root))


ROOT_A, ROOT_B = guard.EXPECTED_ROOTS


def analyze(sources):
    return guard.analyze_texts({name: ("%s.java" % name, text) for name, text in sources.items()})


def analyze_root(text, root=ROOT_A):
    sources = {ROOT_A: root_source(ROOT_A), ROOT_B: root_source(ROOT_B)}
    sources[root] = text
    return analyze(sources)


def transitions(result, root=ROOT_A, dispatch="CachedBytecodeNode.continueAt"):
    entries, dispatches = result.roots[root]
    for each in dispatches:
        if each.name() == dispatch:
            return each.transitions
    raise AssertionError("no dispatch %s in %s" % (dispatch, [each.name() for each in dispatches]))


def single_case(case_body, helpers=""):
    cases = """
                        case Instructions.OP :
%s
                            break;
""" % case_body
    return analyze_root(root_source(ROOT_A, cached(ROOT_A, cases=cases, helpers=helpers)))


def case_transition(result):
    found = [each for each in transitions(result) if each.site == "case Instructions.OP"]
    assert len(found) == 1, [vars(each) for each in transitions(result)]
    return found[0]


class ClassificationTest(unittest.TestCase):

    def test_01_dispatch_with_bci_assertion_is_found(self):
        result = analyze_root(root_source(ROOT_A))
        self.assertEqual(result.failures, [])
        _, dispatches = result.roots[ROOT_A]
        self.assertEqual([each.name() for each in dispatches], ["CachedBytecodeNode.continueAt"])
        self.assertEqual(dispatches[0].assertions, 1)
        self.assertEqual([each.kind for each in dispatches[0].initializations], [guard.START_STATE_DECODE])

    def test_02_compound_literal_delta_is_proven(self):
        transition = case_transition(single_case("bci += 6;"))
        self.assertEqual((transition.kind, transition.status), (guard.SEQUENTIAL_CONSTANT_DELTA, guard.PROVEN))

    def test_03_explicit_literal_delta_is_proven(self):
        transition = case_transition(single_case("bci = bci + 10;"))
        self.assertEqual((transition.kind, transition.status), (guard.SEQUENTIAL_CONSTANT_DELTA, guard.PROVEN))

    def test_04_structural_branch_immediate_is_proven(self):
        transition = case_transition(single_case(
            "bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);"))
        self.assertEqual((transition.kind, transition.status), (guard.BYTECODE_IMMEDIATE_TARGET, guard.PROVEN))

    def test_04b_non_bytecode_index_immediate_is_unknown(self):
        transition = case_transition(single_case("bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm constant */);"))
        self.assertEqual(transition.status, guard.UNKNOWN)

    def test_04c_immediate_on_non_compilation_final_array_is_unknown(self):
        source = root_source(ROOT_A, cached(ROOT_A, cases="""
                        case Instructions.OP :
                            bci = BYTES.getIntUnaligned(other, bci + 2 /* imm branch_target */);
                            break;
"""))
        self.assertEqual(case_transition(analyze_root(source)).status, guard.UNKNOWN)

    def test_05_merge_explode_key_is_proven(self):
        transition = case_transition(single_case("bci = CompilerDirectives.mergeExplodeKey(bci);"))
        self.assertEqual((transition.kind, transition.status), (guard.MERGE_EXPLODE_KEY, guard.PROVEN))

    def test_06_helper_returning_constant_delta_is_proven(self):
        transition = case_transition(single_case(
            "bci = handleNext(frame, bc, bci, sp);", helper("handleNext", "            return bci + 4;")))
        self.assertEqual((transition.kind, transition.status), (guard.PROVEN_HANDLER_RETURN, guard.PROVEN))
        self.assertIn("bci+4", transition.detail)

    def test_07_helper_returning_branch_immediate_is_proven(self):
        transition = case_transition(single_case(
            "bci = handleBranchFalse(frame, bc, bci, sp);",
            helper("handleBranchFalse", "            if (x) {\n                return bci + 10;\n            }\n"
                                        "            return BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);")))
        self.assertEqual((transition.kind, transition.status), (guard.PROVEN_HANDLER_RETURN, guard.PROVEN))

    def test_07b_helper_with_renamed_parameters_binds_by_position(self):
        transition = case_transition(single_case(
            "bci = handleNext(frame, bc, bci, sp);",
            helper("handleNext", "            return at + 4;", params="Frame f, byte[] code, long at, long s")))
        self.assertEqual(transition.status, guard.PROVEN)

    def test_08_helper_returning_ordinary_parameter_is_risk(self):
        transition = case_transition(single_case(
            "bci = handleJump(frame, bc, bci, sp);", helper("handleJump", "            return sp;")))
        self.assertEqual((transition.kind, transition.status), (guard.RUNTIME_PARAMETER, guard.RISK))

    def test_08b_helper_reassigning_bci_parameter_is_unknown(self):
        transition = case_transition(single_case(
            "bci = handleNext(frame, bc, bci, sp);",
            helper("handleNext", "            bci = sp;\n            return bci + 4;")))
        self.assertEqual(transition.status, guard.UNKNOWN)

    def test_08c_helper_without_return_that_throws_is_proven(self):
        transition = case_transition(single_case(
            "bci = handleThrow(frame, bc, bci, sp);", helper("handleThrow", "            throw sneakyThrow(e);")))
        self.assertEqual((transition.kind, transition.status), (guard.THROWING_HANDLER, guard.PROVEN))

    def test_09_unknown_method_call_is_unknown(self):
        transition = case_transition(single_case("bci = computeTarget(frame, bc, bci, sp);"))
        self.assertEqual(transition.status, guard.UNKNOWN)
        self.assertIn("unresolved", transition.detail)

    def test_09b_ambiguous_helper_is_unknown(self):
        helpers = helper("handleNext", "            return bci + 4;") + helper(
            "handleNext", "            return bci + 4;", params="FrameWithoutBoxing frame, byte[] bc, long bci")
        transition = case_transition(single_case("bci = handleNext(frame, bc, bci, sp);", helpers))
        self.assertEqual(transition.status, guard.UNKNOWN)
        self.assertIn("ambiguous", transition.detail)

    def test_09c_helper_returning_unknown_call_is_unknown(self):
        transition = case_transition(single_case(
            "bci = handleNext(frame, bc, bci, sp);", helper("handleNext", "            return lookup(bci);")))
        self.assertEqual(transition.status, guard.UNKNOWN)

    def test_10_instance_field_is_risk(self):
        transition = case_transition(single_case("bci = this.resumeBci;"))
        self.assertEqual((transition.kind, transition.status), (guard.INSTANCE_FIELD, guard.RISK))

    def test_11_frame_state_is_risk(self):
        transition = case_transition(single_case("bci = FRAMES.getLong(frame, BCI_INDEX);"))
        self.assertEqual((transition.kind, transition.status), (guard.RUNTIME_STATE, guard.RISK))

    def test_11b_non_literal_delta_is_unknown(self):
        transition = case_transition(single_case("bci += sp;"))
        self.assertEqual(transition.status, guard.UNKNOWN)

    def test_11c_write_followed_by_return_is_dispatch_exit(self):
        transition = case_transition(single_case(
            "bci = handleYield(frame, bc, bci, sp);\n                            return 0xFFFFFFFFL;",
            helper("handleYield", "            return sp;")))
        self.assertEqual((transition.kind, transition.status), (guard.DISPATCH_EXIT, guard.PROVEN))

    def test_12_missing_bci_assertion_fails(self):
        result = analyze_root(root_source(ROOT_A, cached(ROOT_A, assertion="")))
        self.assertTrue(any(failure.startswith("BCI_ASSERTION_MISSING") for failure in result.failures))

    def test_12b_missing_cached_dispatch_fails(self):
        source = root_source(ROOT_A).replace("@ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)\n        long continueAt(%s $root, FrameWithoutBoxing frame_, long startState, ContinuationRootNodeImpl continuationRootNode) {\n            byte[]" % ROOT_A, "long continueAt(%s $root, FrameWithoutBoxing frame_, long startState, ContinuationRootNodeImpl continuationRootNode) {\n            byte[]" % ROOT_A)
        result = analyze_root(source)
        self.assertTrue(any(failure.startswith("CACHED_DISPATCH_MISSING") for failure in result.failures),
                        result.failures)

    def test_13_missing_generated_root_fails(self):
        result = analyze({ROOT_A: root_source(ROOT_A)})
        self.assertIn("GENERATED_ROOT_MISSING %s.java" % ROOT_B, result.failures)
        with tempfile.TemporaryDirectory() as directory:
            generated = Path(directory) / "annotations" / "p"
            generated.mkdir(parents=True)
            (generated / (ROOT_A + ".java")).write_text(root_source(ROOT_A), encoding="utf-8")
            _, failures = guard.discover(Path(directory))
            self.assertEqual(failures, ["GENERATED_ROOT_MISSING %s.java under %s" % (ROOT_B, directory)])
            _, failures = guard.discover(Path(directory) / "absent")
            self.assertTrue(failures[0].startswith("GENERATED_SOURCES_MISSING"))

    def test_13b_root_without_cached_class_fails(self):
        source = root_source(ROOT_A).replace("class CachedBytecodeNode ", "class OtherBytecodeNode ")
        result = analyze_root(source)
        self.assertTrue(any(failure.startswith("CACHED_DISPATCH_MISSING") for failure in result.failures))

    def test_17_comments_and_strings_are_ignored(self):
        transition = case_transition(single_case(
            "// bci = this.resumeBci;\n                            /* bci = sp; */\n"
            "                            String s = \"bci = sp;\";\n                            bci += 2;"))
        self.assertEqual((transition.kind, transition.status), (guard.SEQUENTIAL_CONSTANT_DELTA, guard.PROVEN))

    def test_18_two_roots_are_independent(self):
        risky = root_source(ROOT_B, cached(ROOT_B, cases="""
                        case Instructions.OP :
                            bci = this.resumeBci;
                            break;
"""))
        result = analyze({ROOT_A: root_source(ROOT_A), ROOT_B: risky})
        statuses_a = {each.status for each in transitions(result, ROOT_A)}
        statuses_b = {each.status for each in transitions(result, ROOT_B)}
        self.assertNotIn(guard.RISK, statuses_a)
        self.assertIn(guard.RISK, statuses_b)

    def test_19_cached_and_tail_call_dispatches_are_separate(self):
        source = root_source(ROOT_A, cached(ROOT_A), cached(ROOT_A, name="CachedBytecodeNodeTailCall"))
        result = analyze_root(source)
        self.assertEqual(result.failures, [])
        _, dispatches = result.roots[ROOT_A]
        self.assertEqual(sorted(each.name() for each in dispatches),
                         ["CachedBytecodeNode.continueAt", "CachedBytecodeNodeTailCall.continueAt"])
        keys = [each.key() for each in result.all_topology()]
        self.assertEqual(len(keys), len(set(keys)))

    def test_20_uncached_dispatch_is_not_cached_proof(self):
        result = analyze_root(root_source(ROOT_A))
        _, dispatches = result.roots[ROOT_A]
        self.assertNotIn("UncachedBytecodeNode.continueAt", [each.name() for each in dispatches])
        self.assertFalse(any("someField" in each.statement for each in result.all_topology()))

    def test_21_exception_handler_state_is_structural_and_dynamic(self):
        result = analyze_root(root_source(ROOT_A))
        found = [each for each in transitions(result) if each.kind == guard.EXCEPTION_HANDLER_TABLE]
        self.assertEqual(len(found), 1)
        self.assertEqual((found[0].status, found[0].site), (guard.DYNAMIC, "catch(Throwable)"))

    def test_21b_exception_handler_runtime_bci_is_unknown(self):
        result = analyze_root(root_source(ROOT_A, cached(ROOT_A, handler_bci="bci = FRAMES.getLong(frame, 3);")))
        found = [each for each in transitions(result) if each.site == "catch(Throwable)"]
        self.assertEqual([each.status for each in found], [guard.UNKNOWN])

    def test_21c_non_compilation_final_handler_table_is_unknown(self):
        source = root_source(ROOT_A).replace("@CompilationFinal(dimensions = 1) final int[] handlers;",
                                             "int[] handlers;")
        found = [each for each in transitions(analyze_root(source)) if each.site == "catch(Throwable)"]
        self.assertEqual([each.status for each in found], [guard.UNKNOWN])

    def test_22_entry_sites_are_classified(self):
        result = analyze_root(root_source(ROOT_A))
        entries, _ = result.roots[ROOT_A]
        self.assertEqual(sorted(each.kind for each in entries), sorted([
            guard.ENTRY_LITERAL_ZERO, guard.ENTRY_ROOT_STATE_LOOP, guard.ENTRY_OSR_TARGET,
            guard.ENTRY_CONTINUATION_LOCATION]))

    def test_22b_unknown_entry_makes_start_state_unknown(self):
        source = root_source(ROOT_A).replace(
            "    private abstract static class AbstractBytecodeNode",
            "    void resume(long at) {\n        bytecode.continueAt(this, frame, this.savedState, null);\n    }\n\n"
            "    private abstract static class AbstractBytecodeNode")
        result = analyze_root(source)
        entries, dispatches = result.roots[ROOT_A]
        self.assertIn(guard.UNKNOWN, [each.status for each in entries])
        self.assertEqual([each.status for each in dispatches[0].initializations], [guard.UNKNOWN])

    def test_22c_continuation_location_must_be_compilation_final(self):
        source = root_source(ROOT_A).replace("@CompilationFinal volatile BytecodeLocation location;",
                                             "volatile BytecodeLocation location;")
        entries, _ = analyze_root(source).roots[ROOT_A]
        self.assertIn(guard.UNKNOWN, [each.status for each in entries])


class BaselineTest(unittest.TestCase):

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        base = Path(self.directory.name)
        self.generated = base / "generated-sources" / "annotations" / "p"
        self.generated.mkdir(parents=True)
        for name in guard.EXPECTED_ROOTS:
            (self.generated / (name + ".java")).write_text(root_source(name), encoding="utf-8")
        self.baseline = base / "baseline.json"
        texts = {name: (name, root_source(name)) for name in guard.EXPECTED_ROOTS}
        self.data = guard.candidate_baseline(guard.analyze_texts(texts))
        self.write(self.data)

    def tearDown(self):
        self.directory.cleanup()

    def write(self, data):
        self.baseline.write_text(json.dumps(data), encoding="utf-8")

    def run_check(self):
        out = io.StringIO()
        status = guard.check(self.generated.parent.parent, self.baseline, None, None, out=out)
        return status, out.getvalue()

    def test_exact_baseline_passes(self):
        status, output = self.run_check()
        self.assertEqual(status, 0, output)
        self.assertIn("BCI_TRANSITION_RISK=0", output)
        self.assertIn("GENERATED_ROOTS_FOUND=2", output)

    def test_14_duplicate_baseline_entry_fails(self):
        data = dict(self.data)
        data["topology"] = data["topology"] + data["topology"][:1]
        self.write(data)
        status, output = self.run_check()
        self.assertEqual(status, 1)
        self.assertIn("DUPLICATE_TOPOLOGY", output)

    def test_14b_duplicate_computed_transition_fails(self):
        source = root_source(ROOT_A, cached(ROOT_A, cases="""
                        case Instructions.OP :
                            bci += 2;
                            bci += 2;
                            break;
"""))
        (self.generated / (ROOT_A + ".java")).write_text(source, encoding="utf-8")
        status, output = self.run_check()
        self.assertEqual(status, 1)
        self.assertIn("DUPLICATE_TOPOLOGY", output)

    def test_15_stale_baseline_fails(self):
        data = dict(self.data)
        data["topology"] = data["topology"] + [
            "%s|CachedBytecodeNode.continueAt|case Instructions.GONE|bci += 2;|SEQUENTIAL_CONSTANT_DELTA|bci+2"
            % ROOT_A]
        self.write(data)
        status, output = self.run_check()
        self.assertEqual(status, 1)
        self.assertIn("STALE_BASELINE", output)

    def test_16_new_topology_fails(self):
        source = root_source(ROOT_A, cached(ROOT_A, cases=DEFAULT_CASES + """
                        case Instructions.EXTRA :
                            bci += 8;
                            break;
"""))
        (self.generated / (ROOT_A + ".java")).write_text(source, encoding="utf-8")
        status, output = self.run_check()
        self.assertEqual(status, 1)
        self.assertIn("NEW_TOPOLOGY", output)

    def test_risk_is_never_baselineable(self):
        data = dict(self.data)
        data["topology"] = data["topology"] + [
            "%s|CachedBytecodeNode.continueAt|case Instructions.OP|bci = sp;|RUNTIME_PARAMETER|sp" % ROOT_A]
        self.write(data)
        status, output = self.run_check()
        self.assertEqual(status, 1)
        self.assertIn("never baselineable", output)

    def test_risk_fails_check(self):
        source = root_source(ROOT_A, cached(ROOT_A, cases="""
                        case Instructions.OP :
                            bci = this.resumeBci;
                            break;
"""))
        (self.generated / (ROOT_A + ".java")).write_text(source, encoding="utf-8")
        status, output = self.run_check()
        self.assertEqual(status, 1)
        self.assertIn("BCI_TRANSITION_RISK=1", output)

    def test_missing_generated_root_fails_check(self):
        (self.generated / (ROOT_B + ".java")).unlink()
        status, output = self.run_check()
        self.assertEqual(status, 1)
        self.assertIn("GENERATED_ROOT_MISSING", output)


if __name__ == "__main__":
    unittest.main(verbosity=1)
