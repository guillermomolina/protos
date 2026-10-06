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

"""AUD007-B1 local-only publication/reconciliation race harness.

This is a test fixture, not a launcher. It models the retained GITHUB003
publication phase against a local bare repository so that interleavings can
be forced with explicit checkpoints instead of timing:

- expensive validation is represented by a token bound to the validated base
  and the exact patch-owned blob ids;
- remote movement inside patch ownership or the declared dependency closure
  rejects token reuse;
- disjoint movement rematerializes the same patch bytes on the new base, reruns
  only cheap gates, and reuses the token;
- publication is a plain non-force push, retried at most ``retry_limit`` times,
  never merged or rebased.

Checkpoint hooks run in the publisher's own thread/process, so a controller
can move the remote, wait on a barrier, or inject a catchable interruption at
an exact point.
"""

from __future__ import print_function

import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import sys
import time


CHECKPOINTS = ("validation", "validated", "rematerialized", "before_publish")
OWNER_FILE = "OWNER.json"
WORK_PREFIX = "publisher-"
FORBIDDEN_GIT_VERBS = frozenset(("merge", "rebase", "pull", "cherry-pick", "am"))
SAFETY_TIMEOUT_SECONDS = 10.0

# Stand-in for a long validation child: it owns one loopback listener and
# blocks until its stdin closes or it is terminated.
VALIDATION_CHILD_SOURCE = (
    "import socket, sys\n"
    "s = socket.socket()\n"
    "s.bind(('127.0.0.1', 0))\n"
    "s.listen(1)\n"
    "print(s.getsockname()[1], flush=True)\n"
    "sys.stdin.read()\n"
)


class HarnessInterrupt(Exception):
    """Catchable interruption injected at a checkpoint."""


def _git_env():
    env = dict((k, v) for k, v in os.environ.items() if not k.startswith("GIT_"))
    env["GIT_CONFIG_NOSYSTEM"] = "1"
    env["GIT_CONFIG_GLOBAL"] = os.devnull
    env["GIT_TERMINAL_PROMPT"] = "0"
    return env


def git(cwd, *args, **kwargs):
    record = kwargs.pop("record", None)
    check = kwargs.pop("check", True)
    if kwargs:
        raise TypeError("unexpected arguments: " + ",".join(kwargs))
    if record is not None:
        record.append(tuple(args))
    completed = subprocess.run(
        [
            "git",
            "-c", "user.name=Race Harness",
            "-c", "user.email=race-harness@example.invalid",
            "-c", "commit.gpgsign=false",
            "-c", "init.defaultBranch=main",
        ] + list(args),
        cwd=str(cwd),
        env=_git_env(),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if check and completed.returncode != 0:
        raise RuntimeError(
            "git %s failed: %s" % (" ".join(args), completed.stderr.strip())
        )
    return completed


def _write_files(root, files):
    for relative, content in sorted(files.items()):
        path = Path(root) / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


def create_remote(root, files):
    """Create a bare fixture remote whose main holds one seed commit."""
    root = Path(root)
    remote = root / "remote.git"
    git(root, "init", "--bare", "-q", "-b", "main", str(remote))
    git(remote, "config", "receive.denyNonFastForwards", "true")
    git(remote, "config", "receive.denyDeletes", "true")
    git(remote, "config", "core.logAllRefUpdates", "always")
    seed = root / "seed"
    git(root, "init", "-q", "-b", "main", str(seed))
    _write_files(seed, files)
    git(seed, "add", "-A")
    git(seed, "commit", "-q", "-m", "seed")
    git(seed, "push", "-q", str(remote), "main:refs/heads/main")
    shutil.rmtree(str(seed))
    return remote


def remote_main(remote):
    return git(remote, "rev-parse", "refs/heads/main").stdout.strip()


def remote_history_is_fast_forward_only(remote):
    """True when every recorded update of remote main was a fast-forward."""
    log = Path(remote) / "logs" / "refs" / "heads" / "main"
    lines = log.read_text(encoding="utf-8").splitlines()
    if not lines:
        return False
    for line in lines:
        old, new = line.split(" ", 2)[:2]
        if set(old) == {"0"}:
            continue
        if git(remote, "merge-base", "--is-ancestor", old, new,
               check=False).returncode != 0:
            return False
    return True


def advance_remote(remote, work, files, message="controller movement"):
    """Controller-side ordinary publication of ``files`` onto remote main."""
    work = Path(work)
    if work.exists():
        shutil.rmtree(str(work))
    git(work.parent, "clone", "-q", str(remote), str(work))
    _write_files(work, files)
    git(work, "add", "-A")
    git(work, "commit", "-q", "-m", message)
    git(work, "push", "-q", "origin", "HEAD:refs/heads/main")
    sha = git(work, "rev-parse", "HEAD").stdout.strip()
    shutil.rmtree(str(work))
    return sha


def changed_paths(repo, old, new):
    out = git(repo, "diff", "--name-only", "-z", "--no-renames", old, new).stdout
    return frozenset(p for p in out.split("\0") if p)


def _in_closure(path, closure):
    for entry in closure:
        prefix = entry.rstrip("/")
        if path == prefix or path.startswith(prefix + "/"):
            return True
    return False


def process_alive(pid):
    """True for a live, non-zombie process."""
    stat = Path("/proc") / str(pid) / "stat"
    if Path("/proc").is_dir():
        try:
            text = stat.read_text()
        except (FileNotFoundError, ProcessLookupError):
            return False
        return text.rsplit(")", 1)[1].split()[0] != "Z"
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    return True


def port_listening(port):
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=1.0):
            return True
    except OSError:
        return False


def wait_until(predicate, timeout=SAFETY_TIMEOUT_SECONDS):
    """Poll a terminal condition; the bound is a safety timeout only."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return True
        time.sleep(0.02)
    return predicate()


class Outcome(object):
    def __init__(self, result, reuse, revalidation, reason, retry_count, sha=None):
        self.result = result
        self.validation_reuse = reuse
        self.revalidation_required = revalidation
        self.reason = reason
        self.retry_count = retry_count
        self.published_sha = sha

    def __repr__(self):
        return "Outcome(%r)" % (self.__dict__,)


class Publisher(object):
    def __init__(self, name, remote, work_parent, patch, closure=(),
                 retry_limit=2, hook=None, validation_child=True):
        self.name = name
        self.remote = Path(remote)
        self.work = Path(work_parent) / (WORK_PREFIX + name)
        self.repo = self.work / "repo"
        self.patch = dict(patch)
        self.owned = frozenset(self.patch)
        self.closure = tuple(closure)
        self.retry_limit = retry_limit
        self.hook = hook
        self.use_validation_child = validation_child
        self.commands = []
        self.push_results = []
        self.child = None
        self.child_port = None
        self.token = None
        self.candidate = None

    def _git(self, *args, **kwargs):
        return git(self.repo, *args, record=self.commands, **kwargs)

    def _checkpoint(self, name):
        if self.hook is not None:
            self.hook(self, name)

    def prepare(self):
        self.work.mkdir(parents=True)
        (self.work / OWNER_FILE).write_text(
            json.dumps({"pid": os.getpid(), "pgid": os.getpgid(0),
                        "name": self.name}),
            encoding="utf-8",
        )
        git(self.work, "clone", "-q", str(self.remote), str(self.repo),
            record=self.commands)
        base = self._git("rev-parse", "origin/main").stdout.strip()
        self.candidate = self._materialize(base)

    def _materialize(self, base):
        self._git("checkout", "-q", "--detach", base)
        _write_files(self.repo, self.patch)
        self._git("add", "--", *sorted(self.owned))
        self._git("commit", "-q", "-m", "candidate " + self.name)
        return self._git("rev-parse", "HEAD").stdout.strip()

    def _owned_blobs(self, commit):
        return dict(
            (p, self._git("rev-parse", commit + ":" + p).stdout.strip())
            for p in sorted(self.owned)
        )

    def validate(self):
        if self.use_validation_child:
            self.child = subprocess.Popen(
                [sys.executable, "-c", VALIDATION_CHILD_SOURCE],
                stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True,
            )
            self.child_port = int(self.child.stdout.readline())
        self._checkpoint("validation")
        self._stop_child()
        self.token = {
            "base": self._git("rev-parse", self.candidate + "^").stdout.strip(),
            "blobs": self._owned_blobs(self.candidate),
        }
        self._checkpoint("validated")

    def _abort(self, reuse, reason, retries):
        return Outcome("SAFE_ABORT", reuse, "YES", reason, retries)

    def reconcile_and_publish(self):
        retries = 0
        while True:
            self._git("fetch", "-q", "origin",
                      "refs/heads/main:refs/remotes/origin/main")
            head = self._git("rev-parse", "origin/main").stdout.strip()
            current_base = self._git(
                "rev-parse", self.candidate + "^").stdout.strip()
            reuse = "NOT_REQUIRED"
            if head != current_base:
                moved = changed_paths(self.repo, self.token["base"], head)
                if moved & self.owned:
                    return self._abort("REJECTED", "direct patch-ownership overlap",
                                       retries)
                if any(_in_closure(p, self.closure) for p in moved):
                    return self._abort("REJECTED", "dependency-closure movement",
                                       retries)
                self.candidate = self._materialize(head)
                self._checkpoint("rematerialized")
                if self._owned_blobs(self.candidate) != self.token["blobs"]:
                    return self._abort("REJECTED", "patch-owned bytes changed",
                                       retries)
                if changed_paths(self.repo, head, self.candidate) != self.owned:
                    return self._abort("REJECTED", "rematerialized delta escaped "
                                       "patch ownership", retries)
                reuse = "ACCEPTED"
            self._checkpoint("before_publish")
            pushed = self._git(
                "push", "-q", "origin", self.candidate + ":refs/heads/main",
                check=False,
            ).returncode == 0
            self.push_results.append(pushed)
            if pushed:
                return Outcome("PUBLISHED", reuse, "NO", "published", retries,
                               self.candidate)
            retries += 1
            if retries > self.retry_limit:
                return self._abort("REJECTED", "retry bound exhausted",
                                   retries - 1)

    def _stop_child(self):
        child, self.child = self.child, None
        if child is None:
            return
        if child.poll() is None:
            child.terminate()
            try:
                child.wait(timeout=SAFETY_TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
        for stream in (child.stdin, child.stdout):
            if stream is not None:
                stream.close()

    def cleanup(self):
        """Idempotent graceful cleanup of state owned by this publisher only."""
        self._stop_child()
        if self.work.exists():
            shutil.rmtree(str(self.work))

    def run(self):
        try:
            self.prepare()
            self.validate()
            return self.reconcile_and_publish()
        finally:
            self.cleanup()

    def forbidden_commands(self):
        bad = []
        for args in self.commands:
            if args and args[0] in FORBIDDEN_GIT_VERBS:
                bad.append(args)
            elif any(a in ("-f", "--force", "--force-with-lease") or
                     a.startswith("--force") or a.startswith("+")
                     for a in args):
                bad.append(args)
        return bad


def recover_crash_residue(work_parent):
    """Recover publisher work roots whose owner died without cleanup.

    Only directories carrying a valid OWNER file whose owner pid is dead are
    touched; their former process group is terminated so orphaned validation
    children cannot keep listeners alive. Returns the recovered names.
    """
    recovered = []
    parent = Path(work_parent)
    if not parent.is_dir():
        return recovered
    for entry in sorted(parent.iterdir()):
        owner = entry / OWNER_FILE
        if not entry.name.startswith(WORK_PREFIX) or not owner.is_file():
            continue
        try:
            data = json.loads(owner.read_text(encoding="utf-8"))
            pid, pgid = int(data["pid"]), int(data["pgid"])
        except (ValueError, KeyError, TypeError):
            continue
        if process_alive(pid):
            continue
        if pgid == pid:
            try:
                os.killpg(pgid, signal.SIGTERM)
            except ProcessLookupError:
                pass
        shutil.rmtree(str(entry))
        recovered.append(entry.name)
    return recovered


def _held_publisher_main(argv):
    """Subprocess publisher that stops at one checkpoint until told to go.

    Protocol: prints ``VALIDATION_CHILD <pid> <port>`` and
    ``CHECKPOINT <name>``, then blocks on one stdin line. SIGTERM becomes a
    catchable HarnessInterrupt.
    """
    remote, work_parent, hold_at = argv

    def on_term(signum, frame):
        raise HarnessInterrupt("SIGTERM")

    signal.signal(signal.SIGTERM, on_term)

    def hook(publisher, name):
        if name != hold_at:
            return
        if publisher.child is not None:
            print("VALIDATION_CHILD %d %d" % (publisher.child.pid,
                                              publisher.child_port), flush=True)
        print("CHECKPOINT " + name, flush=True)
        sys.stdin.readline()

    publisher = Publisher("held", remote, work_parent,
                          {"held.txt": "held\n"}, hook=hook)
    try:
        outcome = publisher.run()
    except HarnessInterrupt:
        print("INTERRUPTED", flush=True)
        return 130
    print("RESULT " + outcome.result, flush=True)
    return 0


if __name__ == "__main__":
    if len(sys.argv) == 5 and sys.argv[1] == "held-publisher":
        sys.exit(_held_publisher_main(sys.argv[2:]))
    print("usage: publication_race_harness.py held-publisher REMOTE WORK CHECKPOINT",
          file=sys.stderr)
    sys.exit(2)
