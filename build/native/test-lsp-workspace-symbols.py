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

"""BUG023: real Native/JVM workspace-symbol JSON-RPC regression."""

import hashlib
import json
import os
import select
import subprocess
import sys
import tempfile
import threading
import time
from collections import deque
from pathlib import Path

TIMEOUT = 20
ROOT = Path(__file__).resolve().parents[2]


def append_metadata_record(digest, name, content):
    name_bytes = name.encode("utf-8")
    digest.update(str(len(name_bytes)).encode("ascii"))
    digest.update(b":")
    digest.update(name_bytes)
    digest.update(b"\n")
    digest.update(str(len(content)).encode("ascii"))
    digest.update(b":")
    digest.update(content)
    digest.update(b"\n")


def create_project(base):
    root = (base / "canonical-project")
    root.mkdir()
    source = root / "Main.protos"

    source.write_text(
        "f: 1\nStraße: 2\nCAFÉ: 3\n",
        encoding="utf-8",
    )

    manifest = b"root metadata\n"
    lock = b"lock metadata\n"

    (root / "protos.toml").write_bytes(manifest)
    (root / "protos.lock").write_bytes(lock)

    digest = hashlib.sha256()
    digest.update(b"protos-project-metadata-v1\n")
    append_metadata_record(digest, "protos.toml", manifest)
    append_metadata_record(digest, "protos.lock", lock)

    resolution = "0123456789abcdef" * 4

    project_text = (
        "project-format 1\n"
        "resolution-input protos-resolution-input-v1 sha256:"
        + resolution
        + "\n"
        "metadata-content protos-project-metadata-v1 sha256:"
        + digest.hexdigest()
        + "\n\n"
        'root workspace "root"\n'
    )

    (root / "protos.project").write_text(
        project_text, encoding="utf-8"
    )
    return root


class Session:
    def __init__(self, command):
        env = os.environ.copy()
        env["PROTOS_HOME"] = str(ROOT)
        env.pop("PROTOS_IMPLEMENTATION_VERSION", None)

        self.process = subprocess.Popen(
            command,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0,
            env=env,
        )
        self.buffer = bytearray()
        self.stderr_lines = deque(maxlen=100)
        self.next_id = 0

        def drain_stderr():
            for raw in self.process.stderr:
                self.stderr_lines.append(
                    raw.decode("utf-8", "replace").rstrip()
                )

        self.stderr_thread = threading.Thread(
            target=drain_stderr, daemon=True
        )
        self.stderr_thread.start()

    def diagnostics(self):
        return "\n".join(self.stderr_lines)

    def alive(self):
        status = self.process.poll()
        if status is not None:
            raise RuntimeError(
                f"LSP terminated unexpectedly: status={status}\n"
                f"{self.diagnostics()}"
            )

    def send(self, message):
        self.alive()
        body = json.dumps(
            message, ensure_ascii=False, separators=(",", ":")
        ).encode("utf-8")
        frame = (
            f"Content-Length: {len(body)}\r\n\r\n".encode("ascii")
            + body
        )
        self.process.stdin.write(frame)
        self.process.stdin.flush()

    def receive(self, deadline):
        while True:
            header_end = self.buffer.find(b"\r\n\r\n")

            if header_end >= 0:
                headers = bytes(self.buffer[:header_end])
                length = None

                for line in headers.split(b"\r\n"):
                    name, separator, value = line.partition(b":")
                    if separator and name.strip().lower() == b"content-length":
                        length = int(value.strip())

                if length is None or length < 0:
                    raise RuntimeError("Invalid LSP Content-Length")

                body_start = header_end + 4
                body_end = body_start + length

                if len(self.buffer) >= body_end:
                    body = bytes(self.buffer[body_start:body_end])
                    del self.buffer[:body_end]
                    return json.loads(body.decode("utf-8"))

            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError(
                    f"LSP response timeout\n{self.diagnostics()}"
                )

            readable, _, _ = select.select(
                [self.process.stdout.fileno()],
                [],
                [],
                remaining,
            )

            if not readable:
                raise TimeoutError(
                    f"LSP response timeout\n{self.diagnostics()}"
                )

            chunk = os.read(self.process.stdout.fileno(), 65536)

            if not chunk:
                raise RuntimeError(
                    f"LSP stdout closed: status={self.process.poll()}\n"
                    f"{self.diagnostics()}"
                )

            self.buffer.extend(chunk)

    def request(self, method, params=None):
        self.next_id += 1
        request_id = self.next_id

        message = {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
        }
        if params is not None:
            message["params"] = params

        self.send(message)
        deadline = time.monotonic() + TIMEOUT

        while True:
            response = self.receive(deadline)
            if response.get("id") != request_id:
                continue

            if "error" in response:
                raise RuntimeError(
                    f"JSON-RPC error in {method}: {response['error']}"
                )

            if "result" not in response:
                raise RuntimeError(
                    f"Missing JSON-RPC result in {method}: {response}"
                )

            return response["result"]

    def notify(self, method, params=None):
        message = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            message["params"] = params
        self.send(message)

    def shutdown(self):
        if self.request("shutdown") is not None:
            raise RuntimeError("Invalid LSP shutdown result")

        self.notify("exit")
        self.process.stdin.close()
        status = self.process.wait(timeout=TIMEOUT)

        if status != 0:
            raise RuntimeError(
                f"LSP shutdown status={status}\n{self.diagnostics()}"
            )

    def cleanup(self):
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()

        self.stderr_thread.join(timeout=1)
        self.process.stdout.close()
        self.process.stderr.close()
        if not self.process.stdin.closed:
            self.process.stdin.close()


def symbol_names(result):
    if not isinstance(result, list):
        raise RuntimeError(f"Expected LSP symbol list: {result}")
    if any(not isinstance(item, dict) or "name" not in item for item in result):
        raise RuntimeError(f"Invalid LSP symbol entry: {result}")
    return [item["name"] for item in result]


def run_session(command, project):
    session = Session(command)
    try:
        if project is None:
            root_uri = None
            folders = []
        else:
            root_uri = project.as_uri()
            folders = [{
                "uri": root_uri,
                "name": project.name,
            }]

        initialized = session.request("initialize", {
            "processId": None,
            "rootUri": root_uri,
            "workspaceFolders": folders,
            "capabilities": {},
        })

        if not isinstance(initialized, dict) or "capabilities" not in initialized:
            raise RuntimeError("Invalid LSP initialize result")

        session.notify("initialized", {})

        if project is None:
            queries = ["#f", "f"]
        else:
            queries = ["f", "STRASSE", "cafe\u0301"]

        results = {}
        for query in queries:
            result = session.request(
                "workspace/symbol", {"query": query}
            )
            symbol_names(result)
            results[query] = result
            session.alive()

        session.shutdown()
        return results

    except Exception as error:
        raise RuntimeError(
            f"{command}: {error}\nLSP STDERR:\n{session.diagnostics()}"
        ) from error
    finally:
        session.cleanup()


def assert_names(results, query, expected):
    actual = symbol_names(results[query])
    if actual != expected:
        raise AssertionError(
            f"Query {query!r}: expected {expected}, got {actual}"
        )


def main():
    if len(sys.argv) != 2:
        raise ValueError("Usage: test-lsp-workspace-symbols.py NATIVE_BINARY")

    native = Path(sys.argv[1]).resolve()
    if not native.is_file():
        raise FileNotFoundError(native)

    jvm_command = [str(ROOT / "bin/protos"), "language-server"]
    native_command = [str(native), "language-server"]

    with tempfile.TemporaryDirectory(prefix="protos-bug023-") as temporary:
        project = create_project(Path(temporary))

        jvm_unbound = run_session(jvm_command, None)
        native_unbound = run_session(native_command, None)

        for results in (jvm_unbound, native_unbound):
            assert_names(results, "#f", [])
            assert_names(results, "f", [])

        print("NATIVE_LSP_UNBOUND=PASS", flush=True)

        jvm_bound = run_session(jvm_command, project)
        native_bound = run_session(native_command, project)

        for results in (jvm_bound, native_bound):
            # Substring matching also admits CAFÉ for query "f".
            assert_names(results, "f", ["f", "CAFÉ"])
            assert_names(results, "STRASSE", ["Straße"])
            assert_names(results, "cafe\u0301", ["CAFÉ"])

        print("NATIVE_LSP_CANONICAL_PROJECT=PASS", flush=True)

        if native_unbound != jvm_unbound:
            raise AssertionError("Native/JVM unbound LSP result mismatch")

        if native_bound != jvm_bound:
            raise AssertionError("Native/JVM bound LSP result mismatch")

        print("NATIVE_LSP_UNICODE_JVM_PARITY=PASS", flush=True)
        print("NATIVE_LSP_CLEAN_SHUTDOWN=PASS", flush=True)
        print("NATIVE_LSP_WORKSPACE_SYMBOLS=PASS", flush=True)


if __name__ == "__main__":
    main()
