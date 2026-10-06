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

"""AUD007-B2 F1 retained proof: shared writable Maven state and its repair.

Each case prints its measured ``AUD007_RESULT F1_<MODE>_<KEY>=<VALUE>`` lines
to stderr before asserting, so a failure still shows the evidence.
"""

from __future__ import print_function

import importlib.util
from pathlib import Path
import shutil
import sys
import tempfile
import unittest


HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "maven_local_repository_harness",
    str(HERE / "maven_local_repository_harness.py"))
H = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(H)


@unittest.skipIf(shutil.which("mvn") is None, "mvn is not installed")
class MavenLocalRepositoryTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="protos-aud007-m2-"))

    def tearDown(self):
        shutil.rmtree(str(self.root))

    def report(self, mode, result):
        for key, value in result.items():
            print("AUD007_RESULT F1_%s_%s=%s" % (mode, key, value),
                  file=sys.stderr)

    def test_shared_writable_repository_has_no_multiprocess_synchronization(self):
        result = H.shared_writable_experiment(self.root)
        self.report("SHARED", result)
        self.assertEqual("YES", result["SHARED_REPOSITORY_WRITES"])
        self.assertEqual("YES", result["CONCURRENT_ARTIFACT_RESOLUTION"],
                         "both Maven JVMs must be inside resolution of the "
                         "same artifact at once")
        self.assertEqual(2, result["ARTIFACT_DOWNLOADS"])
        # This premise is what requires the isolation policy. If it ever
        # becomes PROVEN, re-evaluate keeping a shared writable repository.
        self.assertEqual("NOT_PROVEN", result["MULTIPROCESS_SYNCHRONIZATION"])

    def test_validation_policy_isolates_writable_repository(self):
        result = H.isolated_experiment(self.root)
        self.report("ISOLATED", result)
        self.assertEqual(0, result["MAVEN_PROCESS_A_EXIT"])
        self.assertEqual(0, result["MAVEN_PROCESS_B_EXIT"])
        self.assertEqual("NO", result["SHARED_REPOSITORY_WRITES"])
        self.assertEqual(0, result["ARTIFACT_DOWNLOADS"],
                         "the shared repository must serve reads as tail")
        self.assertEqual("YES", result["PRIVATE_HEADS_DISTINCT"])
        self.assertEqual(0, result["PRIVATE_HEADS_LEFT"])
        self.assertEqual("YES", result["WRITABLE_STATE_ISOLATED"])


if __name__ == "__main__":
    unittest.main()
