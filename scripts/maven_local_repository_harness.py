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

"""AUD007-B2 local-only Maven local-repository concurrency fixture.

Two independent Maven JVMs resolve the same parent POM from a loopback HTTP
fixture remote. The fixture holds the artifact download behind a two-party
barrier: both JVMs can only pass it if both are inside resolution of the same
artifact for the same local repository at the same time. A Maven configuration
with multi-process synchronization would make the second JVM wait for the
first, so the barrier would expire instead.

Resolution happens during model building of a ``pom``-packaged project in the
``validate`` phase, so no plugin is needed and no network is used. Only
temporary directories are touched; the user's own local repository is never
read or written.
"""

from __future__ import print_function

import hashlib
import http.server
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading


GROUP = "aud007"
ARTIFACT = "probe-parent"
VERSION = "1.0"
ARTIFACT_PATH = "%s/%s/%s/%s-%s.pom" % (GROUP, ARTIFACT, VERSION, ARTIFACT, VERSION)
TRACKING_PATH = "%s/%s/%s/_remote.repositories" % (GROUP, ARTIFACT, VERSION)
MIRROR_ID = "aud007-fixture"
BARRIER_SECONDS = 8.0
MAVEN_SECONDS = 60.0
MAX_LISTED_PATHS = 12

PARENT_POM = (
    '<project xmlns="http://maven.apache.org/POM/4.0.0">'
    "<modelVersion>4.0.0</modelVersion>"
    "<groupId>%s</groupId><artifactId>%s</artifactId>"
    "<version>%s</version><packaging>pom</packaging>"
    "</project>\n" % (GROUP, ARTIFACT, VERSION)
).encode("utf-8")

HERE = Path(__file__).resolve().parent


def load_publication_validation():
    spec = importlib.util.spec_from_file_location(
        "publication_validation", str(HERE / "publication_validation.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class FixtureRemote(object):
    """Loopback Maven remote whose artifact download waits on a barrier."""

    def __init__(self, parties=2):
        self.barrier = threading.Barrier(parties, timeout=BARRIER_SECONDS)
        self.lock = threading.Lock()
        self.artifact_requests = 0
        self.overlapped = False
        self.content = {
            ARTIFACT_PATH: PARENT_POM,
            ARTIFACT_PATH + ".sha1":
                hashlib.sha1(PARENT_POM).hexdigest().encode("ascii"),
        }
        remote = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                path = self.path.split("?", 1)[0].lstrip("/")
                if path == ARTIFACT_PATH:
                    remote._hold()
                body = remote.content.get(path)
                if body is None:
                    self.send_response(404)
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_HEAD(self):
                self.send_response(404)
                self.send_header("Content-Length", "0")
                self.end_headers()

            def log_message(self, *args):
                pass

        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.server.daemon_threads = True
        self.thread = threading.Thread(target=self.server.serve_forever)
        self.thread.daemon = True

    @property
    def url(self):
        return "http://127.0.0.1:%d/" % self.server.server_address[1]

    def _hold(self):
        with self.lock:
            self.artifact_requests += 1
        try:
            self.barrier.wait()
            with self.lock:
                self.overlapped = True
        except threading.BrokenBarrierError:
            pass

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *exc):
        self.barrier.abort()
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        return False


def write_settings(path, url):
    path.write_text(
        "<settings><mirrors><mirror>"
        "<id>%s</id><mirrorOf>*</mirrorOf><url>%s</url>"
        "</mirror></mirrors></settings>\n" % (MIRROR_ID, url),
        encoding="utf-8",
    )


def write_project(directory, name):
    directory.mkdir(parents=True)
    (directory / "pom.xml").write_text(
        '<project xmlns="http://maven.apache.org/POM/4.0.0">'
        "<modelVersion>4.0.0</modelVersion>"
        "<parent><groupId>%s</groupId><artifactId>%s</artifactId>"
        "<version>%s</version><relativePath/></parent>"
        "<artifactId>%s</artifactId><packaging>pom</packaging>"
        "</project>\n" % (GROUP, ARTIFACT, VERSION, name),
        encoding="utf-8",
    )


def maven_env():
    env = dict(os.environ)
    for name in ("MAVEN_ARGS", "MAVEN_CONFIG", "MVN_FLAGS"):
        env.pop(name, None)
    return env


def run_maven_pair(root, settings, flags):
    """Start both Maven JVMs before waiting for either one."""
    children = []
    for name, extra in zip(("a", "b"), flags):
        project = root / ("project-" + name)
        write_project(project, "child-" + name)
        log = open(str(root / ("maven-" + name + ".log")), "w")
        command = ["mvn", "-B", "-e", "-ntp", "-s", str(settings),
                   "-gs", str(settings)] + list(extra) + ["validate"]
        children.append((subprocess.Popen(
            command, cwd=str(project), env=maven_env(),
            stdout=log, stderr=subprocess.STDOUT), log))
    exits = []
    for child, log in children:
        try:
            exits.append(child.wait(timeout=MAVEN_SECONDS))
        except subprocess.TimeoutExpired:
            child.kill()
            exits.append(child.wait())
        finally:
            log.close()
    return exits


def tree_snapshot(root):
    snapshot = {}
    root = Path(root)
    if not root.is_dir():
        return snapshot
    for path in sorted(root.rglob("*")):
        if path.is_file():
            stat = path.stat()
            snapshot[path.relative_to(root).as_posix()] = (
                stat.st_size, stat.st_mtime_ns,
                hashlib.sha256(path.read_bytes()).hexdigest())
    return snapshot


def written_paths(before, after):
    return sorted(p for p in after if before.get(p) != after[p])


def residue_counts(snapshot):
    names = list(snapshot)
    return {
        "TEMPORARY_FILES_LEFT": sum(
            1 for p in names
            if p.endswith((".tmp", ".part")) or ".tmp" in p.rsplit("/", 1)[-1]),
        "LAST_UPDATED_FILES_LEFT": sum(
            1 for p in names if p.endswith(".lastUpdated")),
        "LOCK_FILES_OBSERVED": sum(
            1 for p in names if p.endswith(".lock") or "/.locks/" in "/" + p),
    }


def tracking_is_stable(repository):
    path = Path(repository) / TRACKING_PATH
    if not path.is_file():
        return False
    entries = [line for line in path.read_text(encoding="utf-8").splitlines()
               if line and not line.startswith("#")]
    expected = "%s-%s.pom>%s=" % (ARTIFACT, VERSION, MIRROR_ID)
    return entries == [expected]


def _bounded(paths):
    shown = paths[:MAX_LISTED_PATHS]
    suffix = "" if len(paths) <= MAX_LISTED_PATHS else ",(+%d)" % (
        len(paths) - MAX_LISTED_PATHS)
    return "%d:%s%s" % (len(paths), ",".join(shown), suffix)


def shared_writable_experiment(root):
    """Both JVMs write one shared local repository (pre-repair behavior)."""
    root = Path(root)
    shared = root / "shared-repository"
    shared.mkdir()
    before = tree_snapshot(shared)
    with FixtureRemote() as remote:
        settings = root / "settings.xml"
        write_settings(settings, remote.url)
        flag = ["-Dmaven.repo.local=" + str(shared)]
        exits = run_maven_pair(root, settings, (flag, flag))
        overlapped = remote.overlapped
        requests = remote.artifact_requests
    after = tree_snapshot(shared)
    written = written_paths(before, after)
    artifact = shared / ARTIFACT_PATH
    result = {
        "MAVEN_PROCESS_A_EXIT": exits[0],
        "MAVEN_PROCESS_B_EXIT": exits[1],
        "CONCURRENT_ARTIFACT_RESOLUTION": "YES" if overlapped else "NO",
        "ARTIFACT_DOWNLOADS": requests,
        "SHARED_REPOSITORY_WRITES": "YES" if written else "NO",
        "WRITTEN_PATHS": _bounded(written),
        "ARTIFACT_CONTENT_STABLE":
            "YES" if artifact.is_file() and artifact.read_bytes() == PARENT_POM
            else "NO",
        "METADATA_CONTENT_STABLE": "YES" if tracking_is_stable(shared) else "NO",
    }
    result.update(residue_counts(after))
    if overlapped:
        result["MULTIPROCESS_SYNCHRONIZATION"] = "NOT_PROVEN"
    elif requests == 1 and exits == [0, 0]:
        result["MULTIPROCESS_SYNCHRONIZATION"] = "PROVEN"
    else:
        result["MULTIPROCESS_SYNCHRONIZATION"] = "NOT_PROVEN"
    return result


def isolated_experiment(root, policy=None):
    """Both JVMs use the publication-validation isolation policy."""
    policy = policy or load_publication_validation()
    root = Path(root)
    shared = root / "shared-repository"
    (shared / ARTIFACT_PATH).parent.mkdir(parents=True)
    (shared / ARTIFACT_PATH).write_bytes(PARENT_POM)
    before = tree_snapshot(shared)
    heads = root / "private"
    heads.mkdir()
    with FixtureRemote() as remote:
        settings = root / "settings.xml"
        write_settings(settings, remote.url)
        old_tempdir = tempfile.tempdir
        tempfile.tempdir = str(heads)
        try:
            with policy.PrivateMavenRepository() as head_a, \
                    policy.PrivateMavenRepository() as head_b:
                flags = (policy.maven_isolation_flags(head_a, shared),
                         policy.maven_isolation_flags(head_b, shared))
                exits = run_maven_pair(root, settings, flags)
                distinct = head_a != head_b
        finally:
            tempfile.tempdir = old_tempdir
        requests = remote.artifact_requests
    after = tree_snapshot(shared)
    written = written_paths(before, after)
    return {
        "MAVEN_PROCESS_A_EXIT": exits[0],
        "MAVEN_PROCESS_B_EXIT": exits[1],
        "SHARED_REPOSITORY_WRITES": "YES" if written else "NO",
        "WRITTEN_PATHS": _bounded(written),
        "ARTIFACT_DOWNLOADS": requests,
        "PRIVATE_HEADS_DISTINCT": "YES" if distinct else "NO",
        "PRIVATE_HEADS_LEFT": len(list(heads.iterdir())),
        "WRITABLE_STATE_ISOLATED":
            "YES" if distinct and not written else "NO",
    }


def main():
    if shutil.which("mvn") is None:
        print("AUD007_F1_HARNESS=SKIPPED (mvn not found)")
        return 2
    for name, experiment in (("SHARED", shared_writable_experiment),
                             ("ISOLATED", isolated_experiment)):
        root = Path(tempfile.mkdtemp(prefix="protos-aud007-m2-"))
        try:
            for key, value in experiment(root).items():
                print("F1_%s_%s=%s" % (name, key, value))
        finally:
            shutil.rmtree(str(root))
    return 0


if __name__ == "__main__":
    sys.exit(main())
