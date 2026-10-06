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

"""AUD007-B1 deterministic publication race and cleanup tests.

Each passing case prints one ``AUD007_RESULT <CASE>=<STATUS>`` line to stderr
after its invariants have been asserted.
"""

from __future__ import print_function

import importlib.util
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import unittest


HERE = Path(__file__).resolve().parent
CALLER_REPO = HERE.parent
HARNESS_PATH = HERE / "publication_race_harness.py"

SPEC = importlib.util.spec_from_file_location(
    "publication_race_harness", str(HARNESS_PATH))
H = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(H)

SEED = {
    "src/a.txt": "a0\n",
    "src/b.txt": "b0\n",
    "lib/dep.txt": "dep0\n",
    "docs/note.txt": "note0\n",
}


def report(case, status):
    print("AUD007_RESULT %s=%s" % (case, status), file=sys.stderr)


def caller_snapshot():
    def out(*args):
        return H.git(CALLER_REPO, *args).stdout
    return (
        out("rev-parse", "HEAD"),
        out("for-each-ref", "--format=%(refname) %(objectname)"),
        out("status", "--porcelain=v1", "--untracked-files=all"),
        out("worktree", "list", "--porcelain"),
    )


class Interrupt(object):
    """Hook that raises a catchable interruption at one checkpoint."""

    def __init__(self, at):
        self.at = at
        self.observed = None

    def __call__(self, publisher, name):
        if name == self.at:
            self.observed = {
                "child_pid": publisher.child.pid if publisher.child else None,
                "child_port": publisher.child_port,
            }
            raise H.HarnessInterrupt(name)


class PublicationRaceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.caller_before = caller_snapshot()

    @classmethod
    def tearDownClass(cls):
        if caller_snapshot() != cls.caller_before:
            raise AssertionError("caller repository changed during the harness")

    def setUp(self):
        self.temp = Path(tempfile.mkdtemp(prefix="protos-aud007-race-"))
        self.remote = H.create_remote(self.temp, SEED)
        self.base = H.remote_main(self.remote)
        self.work = self.temp / "work"
        self.work.mkdir()

    def tearDown(self):
        shutil.rmtree(str(self.temp))

    def move(self, files, tag="a"):
        return H.advance_remote(self.remote, self.temp / ("ctl-" + tag), files)

    def publisher(self, name, patch, **kwargs):
        return H.Publisher(name, self.remote, self.work, patch, **kwargs)

    def assert_safe(self, *publishers):
        self.assertTrue(H.remote_history_is_fast_forward_only(self.remote))
        for p in publishers:
            self.assertEqual([], p.forbidden_commands())
            self.assertFalse(p.work.exists())
            self.assertIsNone(p.child)

    def remote_file(self, path):
        return H.git(self.remote, "show", "refs/heads/main:" + path).stdout

    # P1 -----------------------------------------------------------------
    def test_p1_disjoint_movement_reuses_validation(self):
        moved = {}

        def hook(p, name):
            if name == "validated":
                moved["sha"] = self.move({"docs/note.txt": "note1\n"})

        b = self.publisher("b", {"src/b.txt": "b1\n"}, closure=("lib/",),
                           hook=hook)
        outcome = b.run()

        self.assertEqual("PUBLISHED", outcome.result)
        self.assertEqual("ACCEPTED", outcome.validation_reuse)
        self.assertEqual("NO", outcome.revalidation_required)
        self.assertEqual([True], b.push_results)
        head = H.remote_main(self.remote)
        self.assertEqual(outcome.published_sha, head)
        self.assertEqual(moved["sha"], H.git(
            self.remote, "rev-parse", head + "^").stdout.strip())
        self.assertEqual("b1\n", self.remote_file("src/b.txt"))
        self.assertEqual("note1\n", self.remote_file("docs/note.txt"))
        self.assert_safe(b)
        report("P1_DISJOINT_REUSE", "PASS")

    # P2a ----------------------------------------------------------------
    def test_p2a_direct_overlap_rejects_reuse(self):
        def hook(p, name):
            if name == "validated":
                self.move({"src/b.txt": "b-from-a\n"})

        b = self.publisher("b", {"src/b.txt": "b1\n"}, hook=hook)
        outcome = b.run()
        moved_head = H.remote_main(self.remote)

        self.assertEqual("SAFE_ABORT", outcome.result)
        self.assertEqual("REJECTED", outcome.validation_reuse)
        self.assertEqual("YES", outcome.revalidation_required)
        self.assertEqual([], b.push_results)
        self.assertEqual("b-from-a\n", self.remote_file("src/b.txt"))
        self.assertNotEqual(self.base, moved_head)
        self.assert_safe(b)
        print("VALIDATION_REUSE=REJECTED", file=sys.stderr)
        print("PUBLICATION=SAFE_ABORT_OR_REVALIDATION_REQUIRED", file=sys.stderr)
        report("P2A_DIRECT_OVERLAP", "SAFE_ABORT")

    # P2b ----------------------------------------------------------------
    def test_p2b_dependency_closure_movement_rejects_reuse(self):
        def hook(p, name):
            if name == "validated":
                self.move({"lib/dep.txt": "dep1\n"})

        b = self.publisher("b", {"src/b.txt": "b1\n"}, closure=("lib/",),
                           hook=hook)
        outcome = b.run()

        self.assertFalse({"lib/dep.txt"} & b.owned)
        self.assertEqual("SAFE_ABORT", outcome.result)
        self.assertEqual("REJECTED", outcome.validation_reuse)
        self.assertEqual("YES", outcome.revalidation_required)
        self.assertEqual("dependency-closure movement", outcome.reason)
        self.assertEqual([], b.push_results)
        self.assertEqual("b0\n", self.remote_file("src/b.txt"))
        self.assert_safe(b)
        print("VALIDATION_REUSE=REJECTED", file=sys.stderr)
        print("REVALIDATION_REQUIRED=YES", file=sys.stderr)
        report("P2B_DEPENDENCY_CLOSURE_MOVEMENT", "SAFE_ABORT")

    # P3 -----------------------------------------------------------------
    def test_p3_simultaneous_publication_has_one_winner(self):
        barrier = threading.Barrier(2, timeout=H.SAFETY_TIMEOUT_SECONDS)

        def hook(p, name):
            if name == "before_publish" and not p.push_results:
                barrier.wait()

        a = self.publisher("a", {"src/a.txt": "from-a\n", "src/b.txt": "a\n"},
                           hook=hook)
        b = self.publisher("b", {"src/b.txt": "from-b\n"}, hook=hook)
        outcomes = {}
        errors = []

        def run(p):
            try:
                outcomes[p.name] = p.run()
            except BaseException as exc:
                errors.append(exc)

        threads = [threading.Thread(target=run, args=(p,)) for p in (a, b)]
        for t in threads:
            t.start()
        for t in threads:
            t.join(H.SAFETY_TIMEOUT_SECONDS * 2)
        self.assertEqual([], errors)
        self.assertFalse(any(t.is_alive() for t in threads))

        results = sorted(o.result for o in outcomes.values())
        self.assertEqual(["PUBLISHED", "SAFE_ABORT"], results)
        winner = [p for p in (a, b) if outcomes[p.name].result == "PUBLISHED"][0]
        loser = b if winner is a else a
        self.assertEqual([True], winner.push_results)
        self.assertEqual([False], loser.push_results)
        self.assertEqual("direct patch-ownership overlap",
                         outcomes[loser.name].reason)
        head = H.remote_main(self.remote)
        self.assertEqual(outcomes[winner.name].published_sha, head)
        self.assertEqual(self.base, H.git(
            self.remote, "rev-parse", head + "^").stdout.strip())
        self.assertEqual(winner.patch["src/b.txt"], self.remote_file("src/b.txt"))
        self.assert_safe(a, b)
        report("P3_SIMULTANEOUS_PUBLICATION", "PASS")

    # P4 -----------------------------------------------------------------
    def test_p4_retry_is_bounded(self):
        limit = 2
        moves = []

        def hook(p, name):
            if name == "before_publish":
                n = len(moves)
                moves.append(self.move({"docs/move-%d.txt" % n: "%d\n" % n},
                                       tag=str(n)))

        b = self.publisher("b", {"src/b.txt": "b1\n"}, retry_limit=limit,
                           hook=hook)
        outcome = b.run()

        self.assertEqual("SAFE_ABORT", outcome.result)
        self.assertEqual("retry bound exhausted", outcome.reason)
        self.assertLessEqual(outcome.retry_count, limit)
        self.assertEqual(limit + 1, len(b.push_results))
        self.assertFalse(any(b.push_results))
        self.assertEqual(moves[-1], H.remote_main(self.remote))
        self.assertEqual("b0\n", self.remote_file("src/b.txt"))
        self.assert_safe(b)
        print("RETRY_COUNT=%d DECLARED_LIMIT=%d" % (outcome.retry_count, limit),
              file=sys.stderr)
        print("FINAL_RESULT=SAFE_ABORT", file=sys.stderr)
        report("P4_BOUNDED_RETRY", "SAFE_ABORT")

    # Cleanup ------------------------------------------------------------
    def interrupted(self, at, move_first=False):
        interrupt = Interrupt(at)

        def hook(p, name):
            if move_first and name == "validated":
                self.move({"docs/note.txt": "note1\n"})
            interrupt(p, name)

        b = self.publisher("b", {"src/b.txt": "b1\n"}, hook=hook)
        foreign = self.work / "publisher-foreign"
        foreign.mkdir()
        (foreign / "keep.txt").write_text("keep\n", encoding="utf-8")
        before = H.remote_main(self.remote) if not move_first else None

        with self.assertRaises(H.HarnessInterrupt):
            b.run()

        head = H.remote_main(self.remote)
        if before is not None:
            self.assertEqual(before, head)
        self.assertNotEqual("b1\n", self.remote_file("src/b.txt"))
        self.assertEqual([], b.push_results)
        self.assert_safe(b)
        observed = interrupt.observed
        if observed["child_pid"] is not None:
            self.assertFalse(H.process_alive(observed["child_pid"]))
            self.assertFalse(H.port_listening(observed["child_port"]))
        b.cleanup()
        self.assertEqual("keep\n", (foreign / "keep.txt").read_text())
        self.assertEqual(head, H.remote_main(self.remote))
        return observed

    def test_cleanup_during_validation(self):
        observed = self.interrupted("validation")
        self.assertIsNotNone(observed["child_pid"])
        report("CLEANUP_DURING_VALIDATION", "PASS")

    def test_cleanup_after_rematerialization(self):
        self.interrupted("rematerialized", move_first=True)
        report("CLEANUP_AFTER_REMATERIALIZATION", "PASS")

    def test_cleanup_before_publication(self):
        self.interrupted("before_publish")
        report("CLEANUP_BEFORE_PUBLICATION", "PASS")

    # Real signals -------------------------------------------------------
    def held(self, at):
        proc = subprocess.Popen(
            [sys.executable, str(HARNESS_PATH), "held-publisher",
             str(self.remote), str(self.work), at],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True,
            start_new_session=True,
        )
        child = proc.stdout.readline().split()
        self.assertEqual("VALIDATION_CHILD", child[0])
        self.assertEqual("CHECKPOINT " + at, proc.stdout.readline().strip())
        return proc, int(child[1]), int(child[2])

    def test_sigterm_is_graceful_cleanup(self):
        proc, child_pid, port = self.held("validation")
        self.assertTrue(H.port_listening(port))
        proc.send_signal(signal.SIGTERM)
        out, _ = proc.communicate(timeout=H.SAFETY_TIMEOUT_SECONDS)
        self.assertEqual(130, proc.returncode)
        self.assertIn("INTERRUPTED", out)
        self.assertTrue(H.wait_until(lambda: not H.process_alive(child_pid)))
        self.assertFalse(H.port_listening(port))
        self.assertEqual([], list(self.work.iterdir()))
        self.assertEqual(self.base, H.remote_main(self.remote))
        report("CLEANUP_GRACEFUL_SIGTERM", "PASS")

    def test_sigkill_leaves_identified_recoverable_residue(self):
        proc, child_pid, port = self.held("validation")
        foreign = self.work / "publisher-foreign"
        foreign.mkdir()
        proc.kill()
        proc.wait(timeout=H.SAFETY_TIMEOUT_SECONDS)
        proc.stdin.close()
        proc.stdout.close()

        residue = self.work / "publisher-held"
        self.assertTrue((residue / H.OWNER_FILE).is_file())
        self.assertEqual(self.base, H.remote_main(self.remote))

        self.assertEqual(["publisher-held"], H.recover_crash_residue(self.work))
        self.assertTrue(H.wait_until(lambda: not H.process_alive(child_pid)))
        self.assertFalse(H.port_listening(port))
        self.assertFalse(residue.exists())
        self.assertEqual([], H.recover_crash_residue(self.work))
        self.assertTrue(foreign.is_dir())
        self.assertEqual(self.base, H.remote_main(self.remote))
        self.assertTrue(H.remote_history_is_fast_forward_only(self.remote))
        report("CRASH_RESIDUE_RECOVERY", "PASS")


if __name__ == "__main__":
    unittest.main()
