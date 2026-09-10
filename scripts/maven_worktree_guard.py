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

"""Protect repository-root Maven runs from same-worktree output races and source drift."""

from __future__ import print_function

import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import sys

try:
    import fcntl
except ImportError:  # pragma: no cover - the selected development container is Linux.
    fcntl = None


class MavenWorktreeGuardError(Exception):
    pass


class WorktreeSnapshot(object):
    def __init__(self, head, files):
        self.head = head
        self.files = files
        digest = hashlib.sha256()
        digest.update(("HEAD\0" + head + "\0").encode("utf-8"))
        for path in sorted(files):
            digest.update(path.encode("utf-8", "surrogateescape"))
            digest.update(b"\0")
            digest.update(files[path].encode("ascii"))
            digest.update(b"\0")
        self.digest = digest.hexdigest()


def _git(repo, *args, **kwargs):
    text = kwargs.pop("text", True)
    if kwargs:
        raise TypeError("unexpected keyword arguments: " + ",".join(kwargs))
    return subprocess.check_output(
        ["git", "-C", str(repo)] + list(args),
        text=text,
    )


def _repo_root(repo):
    try:
        return Path(_git(repo, "rev-parse", "--show-toplevel").strip()).resolve()
    except subprocess.CalledProcessError as exc:
        raise MavenWorktreeGuardError("not inside a Git worktree") from exc


def _lock_path(repo):
    raw = _git(repo, "rev-parse", "--git-path", "protos-maven-worktree.lock").strip()
    path = Path(raw)
    if not path.is_absolute():
        path = repo / path
    return path.resolve()


def _acquire_lock(repo):
    if fcntl is None:
        raise MavenWorktreeGuardError(
            "selected developer Maven guard requires POSIX file locking"
        )
    path = _lock_path(repo)
    path.parent.mkdir(parents=True, exist_ok=True)
    handle = path.open("a+")
    try:
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError as exc:
        handle.close()
        raise MavenWorktreeGuardError(
            "another guarded Maven command is already active in this worktree: "
            + str(path)
        ) from exc
    handle.seek(0)
    handle.truncate()
    handle.write("pid=%d\n" % os.getpid())
    handle.flush()
    return handle, path


def _release_lock(handle):
    try:
        if fcntl is not None:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
    finally:
        handle.close()


def _listed_input_paths(repo):
    raw = _git(
        repo,
        "ls-files",
        "-z",
        "--cached",
        "--others",
        "--exclude-standard",
        text=False,
    )
    paths = []
    for item in raw.split(b"\0"):
        if item:
            paths.append(os.fsdecode(item))
    return sorted(set(paths))


def _hash_regular_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            block = handle.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def _file_signature(path):
    try:
        stat = path.lstat()
    except FileNotFoundError:
        return "missing"

    mode = stat.st_mode & 0o7777
    if path.is_symlink():
        target = os.fsencode(os.readlink(str(path)))
        return "symlink:%o:%s" % (mode, hashlib.sha256(target).hexdigest())
    if path.is_file():
        return "file:%o:%s" % (mode, _hash_regular_file(path))
    return "other:%o" % mode


def capture_snapshot(repo):
    try:
        head = _git(repo, "rev-parse", "HEAD^{commit}").strip()
        files = {}
        for relative in _listed_input_paths(repo):
            files[relative] = _file_signature(repo / relative)
        return WorktreeSnapshot(head, files)
    except (OSError, subprocess.CalledProcessError) as exc:
        raise MavenWorktreeGuardError(
            "cannot capture repository input snapshot: " + str(exc)
        ) from exc


def snapshot_changes(before, after):
    changes = []
    if before.head != after.head:
        changes.append("HEAD %s -> %s" % (before.head, after.head))

    before_paths = set(before.files)
    after_paths = set(after.files)
    for path in sorted(before_paths - after_paths):
        changes.append("removed: " + path)
    for path in sorted(after_paths - before_paths):
        changes.append("added: " + path)
    for path in sorted(before_paths & after_paths):
        if before.files[path] != after.files[path]:
            changes.append("changed: " + path)
    return changes


def _normalize_command(command):
    command = list(command)
    if command and command[0] == "--":
        command = command[1:]
    if not command:
        raise MavenWorktreeGuardError("no Maven command supplied after --")
    return command


def run(repo, command):
    try:
        repo = _repo_root(repo)
        command = _normalize_command(command)
        lock_handle, lock_path = _acquire_lock(repo)
    except MavenWorktreeGuardError as exc:
        print("MAVEN_WORKTREE_GUARD: FAIL_CLOSED", file=sys.stderr)
        print("MAVEN_WORKTREE_GUARD_ERROR: " + str(exc), file=sys.stderr)
        return 2

    print("MAVEN_WORKTREE_LOCK: ACQUIRED " + str(lock_path), flush=True)
    try:
        try:
            before = capture_snapshot(repo)
        except MavenWorktreeGuardError as exc:
            print("MAVEN_WORKTREE_GUARD: FAIL_CLOSED", file=sys.stderr)
            print("MAVEN_WORKTREE_GUARD_ERROR: " + str(exc), file=sys.stderr)
            return 2

        print("MAVEN_WORKTREE_INPUT: " + before.digest, flush=True)
        completed = subprocess.run(command, cwd=str(repo))

        try:
            after = capture_snapshot(repo)
        except MavenWorktreeGuardError as exc:
            print("MAVEN_WORKTREE_INTEGRITY: FAIL_CLOSED", file=sys.stderr)
            print("MAVEN_WORKTREE_GUARD_ERROR: " + str(exc), file=sys.stderr)
            return 2

        changes = snapshot_changes(before, after)
        if changes:
            print("MAVEN_WORKTREE_INTEGRITY: FAIL_CHANGED_DURING_COMMAND", file=sys.stderr)
            print("MAVEN_WORKTREE_INPUT_BEFORE: " + before.digest, file=sys.stderr)
            print("MAVEN_WORKTREE_INPUT_AFTER: " + after.digest, file=sys.stderr)
            for change in changes[:20]:
                print("MAVEN_WORKTREE_CHANGE: " + change, file=sys.stderr)
            if len(changes) > 20:
                print(
                    "MAVEN_WORKTREE_CHANGE: ... %d additional changes"
                    % (len(changes) - 20),
                    file=sys.stderr,
                )
            print(
                "MAVEN_WORKTREE_GUARD: FAIL_CLOSED; discard Maven test/build result",
                file=sys.stderr,
            )
            return 2

        print("MAVEN_WORKTREE_INTEGRITY: PASS", flush=True)
        return completed.returncode
    finally:
        _release_lock(lock_handle)
        print("MAVEN_WORKTREE_LOCK: RELEASED", flush=True)


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    return run(args.repo, args.command)


if __name__ == "__main__":
    sys.exit(main())
