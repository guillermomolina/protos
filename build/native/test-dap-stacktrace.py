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

import json
import os
import socket
import subprocess
import sys
import tempfile
import threading
from pathlib import Path

TIMEOUT = 10
root = Path(__file__).resolve().parents[2]
native_bin = Path(sys.argv[1]).resolve()

with tempfile.TemporaryDirectory(prefix="protos-native-dap-") as tmp:
    source = Path(tmp) / "main.protos"
    source.write_text('x: 41\nprint(x)\nprint("done")\n')

    env = os.environ.copy()
    env["PROTOS_HOME"] = str(root)

    process = subprocess.Popen(
        [str(native_bin), "debug", str(source)],
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1,
        env=env,
    )

    stderr_lines = []

    def drain_stderr():
        for line in process.stderr:
            stderr_lines.append(line.rstrip())

    threading.Thread(target=drain_stderr, daemon=True).start()

    sock = None

    try:
        readiness = process.stdout.readline().strip()
        prefix = "PROTOS_DEBUG_READY "
        if not readiness.startswith(prefix):
            raise RuntimeError(f"bad readiness record: {readiness}")

        endpoint = json.loads(readiness[len(prefix):])

        sock = socket.create_connection(
            (endpoint["host"], int(endpoint["port"])),
            timeout=TIMEOUT,
        )
        sock.settimeout(TIMEOUT)

        wire = bytearray()
        deferred = []
        sequence = 0

        def send(command, arguments=None):
            nonlocal_sequence[0] += 1
            body = {
                "seq": nonlocal_sequence[0],
                "type": "request",
                "command": command,
                "arguments": arguments or {},
            }
            raw = json.dumps(body, separators=(",", ":")).encode("utf-8")
            sock.sendall(
                f"Content-Length: {len(raw)}\r\n\r\n".encode("ascii") + raw
            )
            return nonlocal_sequence[0]

        nonlocal_sequence = [sequence]

        def receive():
            while b"\r\n\r\n" not in wire:
                chunk = sock.recv(4096)
                if not chunk:
                    raise EOFError("DAP socket closed reading headers")
                wire.extend(chunk)

            header_end = wire.index(b"\r\n\r\n")
            headers = bytes(wire[:header_end]).decode("ascii")

            length = None
            for line in headers.split("\r\n"):
                if line.lower().startswith("content-length:"):
                    length = int(line.split(":", 1)[1].strip())

            if length is None:
                raise RuntimeError("missing Content-Length")

            body_start = header_end + 4
            body_end = body_start + length

            while len(wire) < body_end:
                chunk = sock.recv(4096)
                if not chunk:
                    raise EOFError("DAP socket closed reading body")
                wire.extend(chunk)

            raw = bytes(wire[body_start:body_end])
            del wire[:body_end]
            return json.loads(raw.decode("utf-8"))

        def wait_response(request_seq):
            for index, message in enumerate(deferred):
                if (
                    message.get("type") == "response"
                    and message.get("request_seq") == request_seq
                ):
                    return deferred.pop(index)

            while True:
                message = receive()
                if (
                    message.get("type") == "response"
                    and message.get("request_seq") == request_seq
                ):
                    return message
                deferred.append(message)

        def wait_event(name):
            for index, message in enumerate(deferred):
                if (
                    message.get("type") == "event"
                    and message.get("event") == name
                ):
                    return deferred.pop(index)

            while True:
                message = receive()
                if (
                    message.get("type") == "event"
                    and message.get("event") == name
                ):
                    return message
                deferred.append(message)

        def require_success(response, command):
            if response.get("success") is not True:
                raise RuntimeError(f"{command} failed: {response}")

        req = send(
            "initialize",
            {
                "adapterID": "protos",
                "clientID": "native-regression",
                "linesStartAt1": True,
                "columnsStartAt1": True,
                "pathFormat": "path",
            },
        )
        require_success(wait_response(req), "initialize")
        wait_event("initialized")

        req = send("launch", {})
        require_success(wait_response(req), "launch")

        req = send(
            "setBreakpoints",
            {
                "source": {
                    "name": source.name,
                    "path": str(source),
                },
                "lines": [1],
                "breakpoints": [{"line": 1}],
                "sourceModified": False,
            },
        )
        require_success(wait_response(req), "setBreakpoints")

        req = send("setExceptionBreakpoints", {"filters": []})
        require_success(wait_response(req), "setExceptionBreakpoints")

        req = send("configurationDone", {})
        require_success(wait_response(req), "configurationDone")

        stopped = wait_event("stopped")
        thread_id = stopped["body"]["threadId"]

        print("NATIVE_DAP_BREAKPOINT=PASS")

        req = send(
            "stackTrace",
            {
                "threadId": thread_id,
                "startFrame": 0,
                "levels": 20,
            },
        )
        response = wait_response(req)
        require_success(response, "stackTrace")

        frames = response.get("body", {}).get("stackFrames", [])
        if not frames:
            raise RuntimeError("stackTrace returned no frames")

        top = frames[0]

        if top.get("line") != 1:
            raise RuntimeError(f"wrong top-frame line: {top}")

        if top.get("source", {}).get("path") != str(source):
            raise RuntimeError(f"wrong top-frame source: {top}")

        print(f"NATIVE_DAP_STACKTRACE_FRAMES={len(frames)}")
        print("NATIVE_DAP_STACKTRACE=PASS")

        req = send("continue", {"threadId": thread_id})
        require_success(wait_response(req), "continue")

        print("NATIVE_DAP_CONTINUE=PASS")

        wait_event("terminated")

        sock.close()
        sock = None

        process.wait(timeout=TIMEOUT)

        if process.returncode != 0:
            raise RuntimeError(f"protos debug exited {process.returncode}")

        print("NATIVE_DAP_PROCESS_STATUS=0")
        print("NATIVE_DAP_REGRESSION=PASS")

    except Exception:
        if stderr_lines:
            print("=== NATIVE DAP STDERR ===", file=sys.stderr)
            for line in stderr_lines[-80:]:
                print(line, file=sys.stderr)
        raise

    finally:
        if sock is not None:
            try:
                sock.close()
            except Exception:
                pass

        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
