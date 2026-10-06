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

"""Run the publication validation selected for one immutable candidate commit.

AUD007 isolation contract:

- candidate evidence is bound to the exact commit: tracked changes and any
  untracked (or ignored-but-read) path that the selected validation can
  observe fail closed before validation runs, while paths the shared
  validation taxonomy classifies as unobservable are preserved and ignored;
- Maven never writes a shared local repository: each validation gets a
  private, uniquely named writable head and reads the shared repository only
  as a Maven 3.9 ``maven.repo.local.tail`` (Resolver
  ``ChainedLocalRepositoryManager`` adds artifacts and metadata to the head
  only). Resolver's default named-lock factory is JVM-local, so two Maven
  JVMs sharing one writable tree have no proven multi-process
  synchronization.
"""

from __future__ import print_function

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ElementTree


PRIVATE_MAVEN_PREFIX = "protos-validation-m2-"
PRIVATE_MAVEN_OWNER = "OWNER.json"
SHARED_MAVEN_REPOSITORY_ENV = "PROTOS_SHARED_MAVEN_REPOSITORY"
CHILD_TERMINATION_SECONDS = 10.0
MAX_REPORTED_PATHS = 20


class PublicationValidationError(Exception):
    pass


def _git(repo, *args):
    return subprocess.check_output(
        ["git", "-C", str(repo)] + list(args),
        text=True,
    ).strip()


def _resolve_commit(repo, ref):
    try:
        return _git(repo, "rev-parse", str(ref) + "^{commit}")
    except subprocess.CalledProcessError as exc:
        raise PublicationValidationError(
            "cannot resolve commit ref: " + str(ref)
        ) from exc


def verify_candidate_state(repo, head):
    candidate = _resolve_commit(repo, head)
    current = _resolve_commit(repo, "HEAD")
    if candidate != current:
        raise PublicationValidationError(
            "candidate head mismatch: requested %s but worktree HEAD is %s"
            % (candidate, current)
        )

    tracked, untracked = worktree_status(repo)
    if tracked:
        raise PublicationValidationError(
            "candidate worktree has tracked changes after CANDIDATE_SHA: "
            + _bounded(tracked)
        )

    observable = observable_untracked_inputs(repo, untracked)
    if observable:
        raise PublicationValidationError(
            "candidate worktree has untracked validation-observable input "
            "absent from CANDIDATE_SHA: " + _bounded(observable)
        )
    return candidate


def _bounded(paths):
    shown = list(paths[:MAX_REPORTED_PATHS])
    if len(paths) > MAX_REPORTED_PATHS:
        shown.append("(+%d more)" % (len(paths) - MAX_REPORTED_PATHS))
    return ", ".join(shown)


def worktree_status(repo):
    """Return (tracked changes, untracked paths) for the candidate worktree."""
    data = subprocess.check_output([
        "git", "-C", str(repo), "status", "--porcelain=v1", "-z",
        "--untracked-files=all",
    ])
    fields = data.decode("utf-8", "surrogateescape").split("\0")
    tracked = []
    untracked = []
    index = 0
    while index < len(fields):
        entry = fields[index]
        index += 1
        if not entry:
            continue
        code, path = entry[:2], entry[3:]
        if code == "??":
            untracked.append(path)
            continue
        tracked.append(path)
        if code[0] in "RC":
            index += 1
    return tracked, untracked


def load_candidate_selector(repo):
    """Load the candidate's own selector module without writing bytecode."""
    path = Path(repo) / "scripts" / "validation_impact.py"
    if not path.is_file():
        raise PublicationValidationError(
            "repository validation selector is missing: " + str(path)
        )
    namespace = {"__name__": "candidate_validation_impact",
                 "__file__": str(path)}
    try:
        exec(compile(path.read_text(encoding="utf-8"), str(path), "exec"),
             namespace)
    except Exception as exc:
        raise PublicationValidationError(
            "repository validation selector cannot be loaded: " + str(exc)
        ) from exc
    for name in ("observable_paths", "IGNORED_OBSERVABLE_PREFIXES"):
        if name not in namespace:
            raise PublicationValidationError(
                "repository validation selector lacks " + name
            )
    return namespace


def observable_untracked_inputs(repo, untracked):
    """Untracked or ignored-but-read paths the selected validation can see."""
    selector = load_candidate_selector(repo)
    observable = list(selector["observable_paths"](untracked))
    prefixes = tuple(selector["IGNORED_OBSERVABLE_PREFIXES"])
    if prefixes:
        data = subprocess.check_output(
            ["git", "-C", str(repo), "ls-files", "-z", "--others",
             "--ignored", "--exclude-standard", "--"] + list(prefixes)
        )
        for path in data.decode("utf-8", "surrogateescape").split("\0"):
            if path and path not in observable:
                observable.append(path)
    return sorted(observable)


def parse_selector_result(text):
    try:
        data = json.loads(text)
    except (TypeError, ValueError) as exc:
        raise PublicationValidationError("selector output is not valid JSON") from exc

    if not isinstance(data, dict):
        raise PublicationValidationError("selector output must be a JSON object")

    required = (
        "validation_impact",
        "affected_test_set",
        "full_test_suite",
        "reason",
    )
    missing = [name for name in required if name not in data]
    if missing:
        raise PublicationValidationError(
            "selector output missing fields: " + ",".join(missing)
        )

    for name in required:
        if not isinstance(data[name], str) or not data[name]:
            raise PublicationValidationError(
                "selector field must be a non-empty string: " + name
            )

    impact = data["validation_impact"]
    tests = data["affected_test_set"]
    full = data["full_test_suite"]

    if impact == "FULL":
        if tests != "ALL" or full != "REQUIRED":
            raise PublicationValidationError(
                "FULL selector result has inconsistent affected/full fields"
            )
        return data

    if impact in ("TOOL_LOCAL:PACKAGE", "TOOL_LOCAL:TEST"):
        if tests == "ALL" or full != "SKIP_ALLOWED":
            raise PublicationValidationError(
                "tool-local selector result has inconsistent affected/full fields"
            )
        return data

    raise PublicationValidationError(
        "selector returned unsupported impact class: " + impact
    )


def invoke_source_style_guard(repo, base, head):
    guard = Path(repo) / "scripts" / "source_style_guard.py"
    if not guard.is_file():
        raise PublicationValidationError(
            "repository source-style guard is missing: " + str(guard)
        )
    completed = subprocess.run(
        [sys.executable, str(guard), "--repo", str(repo),
         "--base", str(base), "--head", str(head)],
        cwd=str(repo), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise PublicationValidationError(
            "repository source-style guard failed" + ((": " + detail) if detail else "")
        )
    return completed.stdout.strip()



def invoke_legacy_execution_guard(repo, base, head):
    guard = Path(repo) / "scripts" / "legacy_execution_guard.py"
    if not guard.is_file():
        raise PublicationValidationError(
            "repository legacy-execution guard is missing: " + str(guard)
        )
    completed = subprocess.run(
        [
            sys.executable,
            str(guard),
            "--repo",
            str(repo),
            "--base",
            str(base),
            "--head",
            str(head),
        ],
        cwd=str(repo),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise PublicationValidationError(
            "repository legacy-execution guard failed"
            + ((": " + detail) if detail else "")
        )
    return completed.stdout.strip()

def invoke_selector(repo, base, head, top_level_closure):
    selector = Path(repo) / "scripts" / "validation_impact.py"
    if not selector.is_file():
        raise PublicationValidationError(
            "repository validation selector is missing: " + str(selector)
        )

    command = [
        sys.executable,
        str(selector),
        "--repo",
        str(repo),
        "--base",
        str(base),
        "--head",
        str(head),
        "--format",
        "json",
    ]
    if top_level_closure:
        command.append("--top-level-closure")

    completed = subprocess.run(
        command,
        cwd=str(repo),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        if detail:
            detail = ": " + detail
        raise PublicationValidationError(
            "repository validation selector failed" + detail
        )
    return parse_selector_result(completed.stdout)


def shared_maven_repository(env=None):
    """The shared local repository, used by validation as read-only tail."""
    env = os.environ if env is None else env
    explicit = env.get(SHARED_MAVEN_REPOSITORY_ENV)
    if explicit:
        return Path(explicit).expanduser().resolve()
    home = Path(env.get("HOME") or Path.home())
    settings = home / ".m2" / "settings.xml"
    if settings.is_file():
        try:
            root = ElementTree.parse(str(settings)).getroot()
        except ElementTree.ParseError as exc:
            raise PublicationValidationError(
                "cannot parse Maven user settings: " + str(settings)
            ) from exc
        namespace = re.match(r"\{[^}]*\}", root.tag)
        tag = (namespace.group(0) if namespace else "") + "localRepository"
        element = root.find(tag)
        if element is not None and (element.text or "").strip():
            text = element.text.strip().replace("${user.home}", str(home))
            if "${" in text:
                raise PublicationValidationError(
                    "unsupported interpolation in Maven localRepository: "
                    + text
                )
            return Path(text).expanduser().resolve()
    return (home / ".m2" / "repository").resolve()


def maven_isolation_flags(private_repository, shared_repository):
    """Maven 3.9 flags: private writable head, shared read-only tail."""
    for path in (private_repository, shared_repository):
        if re.search(r"\s|,", str(path)):
            raise PublicationValidationError(
                "Maven repository path must not contain whitespace or commas: "
                + str(path)
            )
    return [
        "-Dmaven.repo.local=" + str(private_repository),
        "-Dmaven.repo.local.tail=" + str(shared_repository),
    ]


def recover_private_maven_residue(parent):
    """Remove private repositories whose owning validation process died."""
    recovered = []
    parent = Path(parent)
    if not parent.is_dir():
        return recovered
    for entry in sorted(parent.iterdir()):
        owner = entry / PRIVATE_MAVEN_OWNER
        if not entry.name.startswith(PRIVATE_MAVEN_PREFIX) or not owner.is_file():
            continue
        try:
            pid = int(json.loads(owner.read_text(encoding="utf-8"))["pid"])
        except (OSError, ValueError, KeyError, TypeError):
            continue
        if _process_alive(pid):
            continue
        shutil.rmtree(str(entry), ignore_errors=True)
        recovered.append(entry.name)
    return recovered


def _process_alive(pid):
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    stat = Path("/proc") / str(pid) / "stat"
    try:
        return stat.read_text().rsplit(")", 1)[1].split()[0] != "Z"
    except (OSError, IndexError):
        return True


class PrivateMavenRepository(object):
    """Uniquely named writable Maven repository owned by one validation."""

    def __init__(self, parent=None):
        self.parent = Path(parent or tempfile.gettempdir())
        self.root = None

    def __enter__(self):
        recover_private_maven_residue(self.parent)
        self.root = Path(tempfile.mkdtemp(prefix=PRIVATE_MAVEN_PREFIX,
                                          dir=str(self.parent)))
        (self.root / PRIVATE_MAVEN_OWNER).write_text(
            json.dumps({"pid": os.getpid()}), encoding="utf-8")
        repository = self.root / "repository"
        repository.mkdir()
        return repository

    def __exit__(self, *exc):
        if self.root is not None:
            shutil.rmtree(str(self.root), ignore_errors=True)
            self.root = None
        return False


def validation_command(selection, maven_flags=(), env=None):
    env = os.environ if env is None else env
    maven_flags = list(maven_flags)
    if selection["validation_impact"] == "FULL":
        flags = env.get("MVN_FLAGS", "").split() + maven_flags
        command = ["make", "test"]
        if flags:
            command.append("MVN_FLAGS=" + " ".join(flags))
        return command
    return (["mvn"] + maven_flags
            + ["-Dtest=" + selection["affected_test_set"], "test"])


def run_validation_child(command, cwd):
    """Run the validation in its own process group; never leak it."""
    child = subprocess.Popen(command, cwd=str(cwd), start_new_session=True)
    try:
        return child.wait()
    except BaseException:
        _terminate_group(child)
        raise


def _terminate_group(child):
    for sig in (signal.SIGTERM, signal.SIGKILL):
        try:
            os.killpg(child.pid, sig)
        except ProcessLookupError:
            return
        try:
            child.wait(timeout=CHILD_TERMINATION_SECONDS)
            return
        except subprocess.TimeoutExpired:
            continue


def run(repo, base, head, top_level_closure=False):
    repo = Path(repo).resolve()

    try:
        candidate = verify_candidate_state(repo, head)
        source_style_output = invoke_source_style_guard(repo, base, candidate)
        legacy_execution_output = invoke_legacy_execution_guard(
            repo,
            base,
            candidate,
        )
        selection = invoke_selector(
            repo,
            base,
            candidate,
            top_level_closure,
        )
    except PublicationValidationError as exc:
        print("PUBLICATION_VALIDATION: FAIL_CLOSED", file=sys.stderr)
        print("PUBLICATION_VALIDATION_ERROR: " + str(exc), file=sys.stderr)
        return 2

    impact = selection["validation_impact"]
    tests = selection["affected_test_set"]

    if source_style_output:
        print(source_style_output)
    print("SOURCE_STYLE_PREVENTION_GATE: PASS")
    if legacy_execution_output:
        print(legacy_execution_output)
    print("LEGACY_EXECUTION_PREVENTION_GATE: PASS")
    print("VALIDATION_IMPACT: " + impact)
    print("AFFECTED_TEST_SET: " + tests)
    print("VALIDATION_REASON: " + selection["reason"])

    print("TOOL_TESTS: INCLUDED_BY_SELECTED_SCOPE")

    if top_level_closure:
        print("TOP_LEVEL_RECONCILIATION: REQUIRED_FULL")
    else:
        print("TOP_LEVEL_RECONCILIATION: NOT_REQUIRED_FOR_THIS_CHILD_SLICE")

    try:
        shared = shared_maven_repository()
        with PrivateMavenRepository() as private:
            command = validation_command(
                selection, maven_isolation_flags(private, shared))
            print("MAVEN_LOCAL_REPOSITORY: PRIVATE_HEAD " + str(private))
            print("MAVEN_LOCAL_REPOSITORY_TAIL: READ_ONLY " + str(shared))
            returncode = run_validation_child(command, repo)
    except PublicationValidationError as exc:
        print("PUBLICATION_VALIDATION: FAIL_CLOSED", file=sys.stderr)
        print("PUBLICATION_VALIDATION_ERROR: " + str(exc), file=sys.stderr)
        return 2

    if returncode != 0:
        if impact == "FULL":
            print("FULL_TEST_SUITE: FAIL")
        else:
            print("AFFECTED_TESTS: FAIL")
            print(
                "FULL_TEST_SUITE: SKIPPED "
                "(affected tool-local validation failed before publication)"
            )
        return returncode

    if impact == "FULL":
        print("AFFECTED_TESTS: INCLUDED_IN_FULL_SUITE")
        print("FULL_TEST_SUITE: PASS")
    else:
        print("AFFECTED_TESTS: PASS")
        print(
            "FULL_TEST_SUITE: SKIPPED "
            "(impact-aware tool-local intermediate publication)"
        )

    print("PUBLICATION_VALIDATION: PASS")
    return 0

def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--top-level-closure", action="store_true")
    args = parser.parse_args(argv)

    def on_term(signum, frame):
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, on_term)
    return run(
        args.repo,
        args.base,
        args.head,
        top_level_closure=args.top_level_closure,
    )


if __name__ == "__main__":
    sys.exit(main())
