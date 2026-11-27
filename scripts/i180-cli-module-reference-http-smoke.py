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

"""Real CLI HTTP Host smoke: SOURCE and private MODULE, owning and factory variants.

Usage: python3 scripts/i180-cli-module-reference-http-smoke.py /path/to/executable
The same routes accept the JVM launcher or actual Linux x86_64 Native executable.
"""

import os
import select
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "src/test/resources/i180/module-reference-http"
TIMEOUT = 30


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read_port(process, stderr):
    buffer = bytearray()
    deadline = time.monotonic() + TIMEOUT

    while b"\n" not in buffer:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("HTTP service did not publish listener readiness")

        readable, _, _ = select.select(
            [process.stdout.fileno()], [], [], remaining
        )
        if not readable:
            raise TimeoutError("HTTP service readiness timeout")

        chunk = os.read(process.stdout.fileno(), 4096)
        if not chunk:
            stderr.seek(0)
            raise RuntimeError(
                "HTTP service ended before readiness: "
                + stderr.read().decode("utf-8", "replace")
            )
        buffer.extend(chunk)

    line = bytes(buffer).split(b"\n", 1)[0].strip()
    port = int(line)
    require(1 <= port <= 65535, f"invalid listener port: {port}")
    return port


def request(port, path, status, body):
    with socket.create_connection(("127.0.0.1", port), timeout=TIMEOUT) as sock:
        sock.settimeout(TIMEOUT)
        sock.sendall(f"GET {path} HTTP/1.1\r\nHost: localhost\r\n\r\n".encode("ascii"))
        chunks = []
        while True:
            received = sock.recv(8192)
            if not received:
                break
            chunks.append(received)
    response = b"".join(chunks)
    headers, actual_body = response.split(b"\r\n\r\n", 1)
    actual_status = int(headers.split(b"\r\n", 1)[0].split()[1])
    require(actual_status == status, f"bad status: {response!r}")
    require(actual_body == body, f"bad body: {response!r}")


def exercise(executable, environment, name, cwd, arguments, network_index):
    denied = subprocess.run(
        [str(executable), *arguments], cwd=cwd, env=environment,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=TIMEOUT, check=False,
    )
    require(denied.returncode == 1,
            f"{name}: missing Network grant must fail: {denied.returncode}, {denied.stderr!r}")
    require(not denied.stdout.strip(), f"{name}: denied service announced readiness")
    granted = list(arguments)
    granted.insert(network_index, "--allow-network")
    with tempfile.TemporaryFile() as errors:
        process = subprocess.Popen(
            [str(executable), *granted], cwd=cwd, env=environment,
            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=errors,
        )
        try:
            port = read_port(process, errors)
            for path, status, body in [
                ("/", 200, b"module-reference-ok"),
                ("/missing", 404, b"module-reference-missing"),
                ("/", 200, b"module-reference-ok"),
                ("/missing", 404, b"module-reference-missing"),
            ]:
                require(process.poll() is None, f"{name}: service ended")
                request(port, path, status, body)
            print(f"{name}_NETWORK_DENIED_AND_HTTP=PASS")
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
            process.stdout.close()


def main():
    if len(sys.argv) != 2:
        raise ValueError("supply the actual executable path explicitly")
    executable = Path(sys.argv[1]).resolve()
    require(executable.is_file() and os.access(executable, os.X_OK),
            f"not an executable file: {executable}")
    environment = os.environ.copy()
    environment["PROTOS_HOME"] = str(ROOT)
    for name, cwd, arguments, network_index in [
        ("DIRECT_OWNING", ROOT,
         ["--service=service", str(FIXTURES / "direct/Main.protos")], 0),
        ("DIRECT_FACTORY", ROOT,
         ["--service=factoryService", str(FIXTURES / "direct/Main.protos")], 0),
        ("PACKAGE_OWNING", FIXTURES / "package",
         ["run", "--service=service", "Main"], 1),
        ("PACKAGE_FACTORY", FIXTURES / "package",
         ["run", "--service=factoryService", "Main"], 1),
    ]:
        exercise(executable, environment, name, cwd, arguments, network_index)
    print("MODULE_REFERENCE_HTTP_SMOKE=PASS")


if __name__ == "__main__":
    main()
