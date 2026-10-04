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


"""TEST009-D self-tests for tools/java_generated_bytecode_bci_compilation_check.py (synthetic logs only)."""

from __future__ import print_function

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import java_generated_bytecode_bci_compilation_check as check  # noqa: E402

DONE = ("[engine] opt done   id=12   ProtosBytecodeRootNodeGen@1a2b |Tier 2|Time   120(  90+30  )ms|AST   40\n"
        "[engine] opt done   id=13   ProtosSemanticBytecodeRootNodeGen@3c4d |Tier 2|Time   80(  60+20  )ms|AST   9")
OUTPUT = "600\n200\n"
GENERATED_FAILURE = """[engine] opt failed id=7    work                    |Tier 2|Time   300|Reason: jdk.graal.compiler.core.common.PermanentBailoutException: Partial evaluation did not reduce value to a constant, is a regular compiler node: 36664|Pi
	at com.oracle.truffle.api.CompilerAsserts.partialEvaluationConstant(CompilerAsserts.java:111)
	at com.guillermomolina.protos.execution.ProtosBytecodeRootNodeGen$CachedBytecodeNode.continueAt(ProtosBytecodeRootNodeGen.java:6389)"""
OTHER_PE_FAILURE = """[engine] opt failed id=8    other                   |Tier 2|Reason: PermanentBailoutException: Partial evaluation did not reduce value to a constant
	at com.guillermomolina.protos.execution.LocalRangeAccessor.read(LocalRangeAccessor.java:10)"""
GRAPH_TOO_BIG = """[engine] opt failed id=9    big                     |Tier 2|Reason: jdk.graal.compiler.truffle.GraphTooBigBailoutException: Graph too big to safely compile
	at somewhere"""


class EvaluateTest(unittest.TestCase):

    def test_clean_run_passes(self):
        result = check.evaluate(DONE + "\n" + OUTPUT, 0)
        self.assertEqual(result["failures"], [])
        self.assertEqual(result["COMPILATIONS_DONE"], 2)
        self.assertEqual(result["GENERATED_BCI_COMPILATION_FAILURES"], 0)

    def test_generated_bci_failure_fails(self):
        result = check.evaluate("\n".join([DONE, GENERATED_FAILURE, DONE, OUTPUT]), 0)
        self.assertEqual(result["GENERATED_BCI_COMPILATION_FAILURES"], 1)
        self.assertTrue(result["failures"][0].startswith(check.GENERATED_BCI))

    def test_other_pe_constant_failure_fails_unattributed(self):
        result = check.evaluate("\n".join([DONE, OTHER_PE_FAILURE, OUTPUT]), 0)
        self.assertEqual(result["OTHER_PE_CONSTANT_FAILURES"], 1)
        self.assertEqual(result["GENERATED_BCI_COMPILATION_FAILURES"], 0)
        self.assertTrue(result["failures"])

    def test_other_permanent_failure_is_reported_not_owned(self):
        result = check.evaluate("\n".join([DONE, GRAPH_TOO_BIG, OUTPUT]), 0)
        self.assertEqual(result["OTHER_PERMANENT_FAILURES"], 1)
        self.assertEqual(result["failures"], [])

    def test_records_are_split_at_next_engine_line(self):
        records = check.failure_records("\n".join([GENERATED_FAILURE, DONE, GRAPH_TOO_BIG]))
        self.assertEqual(len(records), 2)
        self.assertEqual([check.classify_failure(record) for record in records],
                         [check.GENERATED_BCI, check.OTHER_PERMANENT])

    def test_each_generated_root_must_compile(self):
        only_structured = DONE.splitlines()[0]
        result = check.evaluate(only_structured + "\n" + OUTPUT, 0)
        self.assertEqual(result["failures"],
                         ["ROOT_NOT_COMPILED no successful compilation of a ProtosSemanticBytecodeRootNodeGen root"])

    def test_no_compilation_fails(self):
        result = check.evaluate(OUTPUT, 0)
        self.assertTrue(any(failure.startswith("NO_COMPILATION") for failure in result["failures"]))

    def test_wrong_semantic_result_fails(self):
        result = check.evaluate(DONE + "\n599\n200\n", 0)
        self.assertTrue(any(failure.startswith("SEMANTIC_RESULT") for failure in result["failures"]))

    def test_nonzero_exit_fails(self):
        result = check.evaluate(DONE + "\n" + OUTPUT, 1)
        self.assertTrue(any(failure.startswith("PROGRAM_EXIT_CODE") for failure in result["failures"]))

    def test_engine_options_are_the_verified_set(self):
        for option in ("AllowExperimentalOptions=true", "CompileImmediately=true", "BackgroundCompilation=false",
                       "CompilationFailureAction=Print"):
            self.assertIn("-Dpolyglot.engine." + option, check.ENGINE_OPTIONS)

    def test_missing_build_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(check.CheckError):
                check.launcher_command(Path(directory), "java")


if __name__ == "__main__":
    unittest.main(verbosity=1)
