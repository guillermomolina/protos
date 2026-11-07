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

"""I154: real executable/loopback TCP + HTTP and explicit Network denial.

Usage:
    python3 scripts/i154-cli-service-http-smoke.py /path/to/protos-native

Supply the actual Native binary for native evidence, not the JVM fallback
wrapper. PROTOS_HOME is the repository/distribution root.
"""

import os
import select
import socket
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/test/resources/i154/cli-http-service.protos"
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


def request(port):
    with socket.create_connection(("127.0.0.1", port), timeout=TIMEOUT) as sock:
        sock.settimeout(TIMEOUT)
        sock.sendall(b"GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")

        chunks = []
        while True:
            received = sock.recv(8192)
            if not received:
                break
            chunks.append(received)

    response = b"".join(chunks)
    require(b"HTTP/1.1 200" in response, f"bad HTTP status: {response!r}")
    require(b"i154-http" in response, f"bad HTTP body: {response!r}")


def main():
    if len(sys.argv) != 2:
        raise ValueError("supply the absolute path to the executable to test")

    executable = Path(sys.argv[1]).resolve()
    require(executable.is_file(), f"missing executable: {executable}")
    require(os.access(executable, os.X_OK), f"not executable: {executable}")

    environment = os.environ.copy()
    environment["PROTOS_HOME"] = str(ROOT)

    denied = subprocess.run(
        [str(executable), "--service=service", str(SOURCE)],
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=TIMEOUT,
        check=False,
    )
    require(
        denied.returncode == 1,
        f"missing Network grant must fail as guest error: {denied.returncode}, "
        f"{denied.stderr!r}",
    )
    print("NETWORK_DENIED=PASS")

    import tempfile

    with tempfile.TemporaryFile() as errors:
        process = subprocess.Popen(
            [
                str(executable),
                "--allow-network",
                "--service=service",
                str(SOURCE),
            ],
            env=environment,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=errors,
        )
        try:
            port = read_port(process, errors)
            require(process.poll() is None, "service ended after bootstrap")

            request(port)
            require(process.poll() is None, "service ended after first request")
            request(port)
            require(process.poll() is None, "service ended after second request")

            print("TCP_HTTP_TWO_REQUESTS=PASS")
            print("ROOT_SERVICE_PERSISTENCE=PASS")
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
            if process.stdout:
                process.stdout.close()

    print("I154_EXECUTABLE_SMOKE=PASS")


if __name__ == "__main__":
    main()
