# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
# THE CONTENTS OF THIS FILE.
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.

from __future__ import print_function

import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest


HERE = Path(__file__).resolve().parent
GUARD_PATH = HERE / "test_ownership_guard.py"
SPEC = importlib.util.spec_from_file_location("test_ownership_guard", str(GUARD_PATH))
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class TestOwnershipGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = Path(tempfile.mkdtemp(prefix="protos-test-ownership-"))
        (self.temp / "protos/tests").mkdir(parents=True)
        (self.temp / "fixtures").mkdir()
        (self.temp / "host").mkdir()
        (self.temp / "fixtures/semantic.protos").write_text("true\n", encoding="utf-8")
        (self.temp / "host/RuntimeTest.java").write_text(
            "final class RuntimeTest {}\n", encoding="utf-8"
        )

    def tearDown(self):
        shutil.rmtree(self.temp)

    def write(self, contracts):
        (self.temp / "protos/tests/test_ownership.json").write_text(
            json.dumps({"version": 1, "contracts": contracts}, indent=2) + "\n",
            encoding="utf-8",
        )

    def semantic(self, **updates):
        value = {
            "id": "language.sample",
            "classification": "PROTOS_SEMANTIC",
            "state": "RECONCILED",
            "primary": {
                "owner": "TOOL002",
                "evidence": ["fixtures/semantic.protos"],
            },
            "secondary": [],
            "migration_ref": "TEST001-D",
        }
        value.update(updates)
        return value

    def test_empty_registry_is_valid_migration_start(self):
        self.write([])
        self.assertEqual(0, GUARD.run(self.temp))

    def test_reconciled_tool002_contract_is_valid(self):
        self.write([self.semantic()])
        result = GUARD.validate(self.temp)
        self.assertEqual(1, result["reconciled"])

    def test_duplicate_contract_id_is_rejected(self):
        self.write([self.semantic(), self.semantic()])
        with self.assertRaisesRegex(
            GUARD.TestOwnershipGuardError, "duplicate semantic contract id"
        ):
            GUARD.validate(self.temp)

    def test_reconciled_junit_primary_is_rejected(self):
        item = self.semantic(primary={
            "owner": "JUNIT",
            "evidence": ["host/RuntimeTest.java"],
        })
        self.write([item])
        with self.assertRaisesRegex(GUARD.TestOwnershipGuardError, "must have TOOL002"):
            GUARD.validate(self.temp)

    def test_distinct_host_secondary_is_valid(self):
        item = self.semantic(secondary=[{
            "owner": "JUNIT",
            "role": "HOST_RUNTIME",
            "evidence": ["host/RuntimeTest.java"],
            "reason": "Retains implementation-only runtime invariant coverage.",
        }])
        self.write([item])
        GUARD.validate(self.temp)

    def test_reconciled_semantic_overlap_is_rejected(self):
        item = self.semantic(secondary=[{
            "owner": "JUNIT",
            "role": "MIGRATION_OVERLAP",
            "evidence": ["host/RuntimeTest.java"],
            "reason": "Temporary duplicate semantic policy during migration.",
        }])
        self.write([item])
        with self.assertRaisesRegex(
            GUARD.TestOwnershipGuardError, "cannot retain MIGRATION_OVERLAP"
        ):
            GUARD.validate(self.temp)

    def test_migration_overlap_requires_explicit_overlap_evidence(self):
        item = self.semantic(state="MIGRATION_OVERLAP")
        self.write([item])
        with self.assertRaisesRegex(
            GUARD.TestOwnershipGuardError, "requires explicit secondary"
        ):
            GUARD.validate(self.temp)

    def test_explicit_junit_exception_requires_reason(self):
        item = self.semantic(
            state="EXCEPTION",
            primary={"owner": "JUNIT", "evidence": ["host/RuntimeTest.java"]},
            exception_reason="short",
        )
        self.write([item])
        with self.assertRaisesRegex(GUARD.TestOwnershipGuardError, "exception_reason"):
            GUARD.validate(self.temp)

    def test_registry_must_be_sorted(self):
        first = self.semantic(id="language.z")
        second = self.semantic(id="language.a")
        self.write([first, second])
        with self.assertRaisesRegex(GUARD.TestOwnershipGuardError, "sorted by id"):
            GUARD.validate(self.temp)


if __name__ == "__main__":
    unittest.main()
