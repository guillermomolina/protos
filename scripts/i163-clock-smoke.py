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

"""I163 / D253-C1 smoke against an already-built JVM or Native executable.

Does not build, install, publish or change repository/configuration files.
Temporary service sources are removed after use. Repeated civil samples are
valid; the freshness probe uses the host's clock window, never monotonic order.
"""

import argparse
import os
import subprocess
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def expect(executable, code, *arguments):
    environment = os.environ.copy()
    environment["PROTOS_HOME"] = str(ROOT)
    result = subprocess.run(
        [str(executable), *arguments], cwd=ROOT, env=environment,
        stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        timeout=30, check=False,
    )
    require(result.returncode == code,
            f"expected exit {code}, got {result.returncode}: "
            f"{result.stderr.decode('utf-8', 'replace')[-2000:]}")
    return result.stdout.decode("utf-8", "strict").strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("executable", type=Path)
    parser.add_argument("--mode", choices=("jvm", "native"), required=True)
    args = parser.parse_args()
    executable = args.executable.resolve()
    require(executable.is_file() and os.access(executable, os.X_OK),
            "supply an already-built executable")

    probe = 'print(context.hasSlot("clock"))'
    require(expect(executable, 0, "-e", probe) == "false", "Clock must be absent")
    expect(executable, 1, "-e", "clock.epochNanoseconds()")
    require(expect(executable, 0, "--allow-clock", "-e", probe) == "true",
            "explicit authorization must install Clock")
    for permission in ("--allow-network", "--allow-entropy"):
        require(expect(executable, 0, permission, "-e", probe) == "false",
                f"{permission} must not imply Clock")
    require(expect(executable, 0, "--allow-clock", "-e",
                   'print(context.hasSlot("network"))\nprint(context.hasSlot("entropy"))')
            == "false\nfalse", "Clock must grant no other authority")
    require(expect(executable, 0, "--allow-clock", "-e",
                   'print(Integer.recognizes(clock.epochNanoseconds()))') == "true",
            "sample must be a genuine Integer")
    require(expect(executable, 0, "--allow-clock", "-e",
                   'Instant: import("std:datetime/instant")\n'
                   'print(Instant.recognizes(Instant(clock.epochNanoseconds())))') == "true",
            "sample must compose with the existing Instant constructor")
    require(expect(executable, 0, "--allow-clock", "-e",
                   'print(InvalidIOArgument.handle('
                   '() => { clock.epochNanoseconds(1); false }, (error) => { true }))')
            == "true", "wrong arity must signal synchronously")
    expect(executable, 2, "--allow-clock", "--allow-clock", "-e", "null")

    with tempfile.TemporaryDirectory(prefix="i163-clock-") as directory:
        source = Path(directory) / "service.protos"
        source.write_text(
            'writer: TextWriter(process.stdout(), process.stdoutEncoding())\n'
            'if(context.hasSlot("clock")) { writer.writeLine("true").value() } '
            'else { writer.writeLine("false").value() }\n'
            'service: () => { Error().signal() }\n', encoding="utf-8")
        require(expect(executable, 1, "--service=service", str(source)) == "false",
                "service mode must not authorize Clock")

    # Fresh independent launches, including Native startup. The generous host-time
    # window tolerates coarse resolution; this does not assert increasing samples.
    for _ in range(4):
        before = time.time_ns()
        sample = int(expect(executable, 0, "--allow-clock", "-e",
                            "print(clock.epochNanoseconds())"))
        after = time.time_ns()
        tolerance = 5_000_000_000
        require(min(before, after) - tolerance <= sample <= max(before, after) + tolerance,
                "sample outside runtime host-clock window; inspect cached build-time state or host skew")

    print(f"I163_{args.mode.upper()}_EXECUTABLE_SMOKE=PASS")


if __name__ == "__main__":
    main()
