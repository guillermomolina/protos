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

"""I157 / D243-C executable smoke: explicit secure Entropy on JVM or Native.

Usage:
    python3 scripts/i157-entropy-smoke.py /path/to/protos --mode jvm
    python3 scripts/i157-entropy-smoke.py /path/to/protos-native --mode native

The supplied executable must already be built. This script does not build,
install or update anything. Native mode uses independent process startups.

Identical samples across several Native startups are a diagnostic symptom
worth investigating, not a statistical proof of (in)security. Samples are
never printed, logged, or persisted.
"""

import argparse
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TIMEOUT = 30


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def command(executable, *arguments):
    environment = os.environ.copy()
    environment["PROTOS_HOME"] = str(ROOT)
    return subprocess.run(
        [str(executable), *arguments],
        env=environment,
        cwd=ROOT,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=TIMEOUT,
        check=False,
    )


def expect(executable, expected, description, *arguments):
    result = command(executable, *arguments)
    require(
        result.returncode == expected,
        (
            f"{description}: expected code {expected}, "
            f"received {result.returncode}; "
            f"stderr={result.stderr.decode('utf-8', 'replace')[-2000:]!r}"
        ),
    )
    return result.stdout.decode("utf-8", "replace").strip()


def inspect_permission_boundaries(executable):
    denied = expect(executable, 1, "ungranted entropy",
                    "-e", "entropy")
    require(not denied, "ungranted read must not produce guest data")

    absent = expect(
        executable, 0, "absent bootstrap slot",
        "-e", 'print(context.hasSlot("entropy"))')
    require(absent == "false", "entropy must be absent without grant")

    permitted = expect(
        executable, 0, "explicit entropy grant",
        "--allow-entropy", "-e",
        'print(context.hasSlot("entropy"))')
    require(permitted == "true", "explicit grant must install entropy")

    network_only = expect(
        executable, 0, "Network does not imply Entropy",
        "--allow-network", "-e",
        'print(context.hasSlot("entropy"))')
    require(network_only == "false",
            "Network must not authorize Entropy")

    entropy_only = expect(
        executable, 0, "Entropy does not imply Network",
        "--allow-entropy", "-e",
        'print(context.hasSlot("network"))')
    require(entropy_only == "false",
            "Entropy must not authorize Network")

    combined = expect(
        executable, 0, "independent combined grants",
        "--allow-network", "--allow-entropy", "-e",
        'print(context.hasSlot("entropy"))\n'
        'print(context.hasSlot("network"))')
    require(combined == "true\ntrue",
            "independent explicit grants must compose")

    print("I157_PERMISSION_BOUNDARIES=PASS")


def inspect_generation(executable):
    sizes = expect(
        executable, 0, "exact 16/32-byte generation",
        "--allow-entropy", "-e",
        "first: entropy.bytes(16).value()\n"
        "second: entropy.bytes(32).value()\n"
        "print(first.size())\n"
        "print(second.size())\n"
        "entropy.close().value()\n",
    ).splitlines()
    require(sizes == ["16", "32"],
            f"wrong generated sizes: {sizes!r}")

    empty = expect(
        executable, 0, "empty fresh Bytes",
        "--allow-entropy", "-e",
        "print(entropy.bytes(0).value().size())",
    )
    require(empty == "0", "zero-length result must contain no octets")

    invalid = expect(
        executable, 0, "invalid argument fails as Future",
        "--allow-entropy", "-e",
        "failed: InvalidIOArgument.handle("
        "() => { entropy.bytes(65537).value(); false },"
        "(error) => { true })\n"
        "print(failed)",
    )
    require(invalid == "true",
            "invalid count must signal InvalidIOArgument")

    print("I157_EXACT_GENERATION=PASS")


def inspect_tool_denial(executable):
    invalid_commands = [
        ("--allow-entropy", "--allow-entropy", "-e", "null"),
        ("--allow-entropy", "--help"),
        ("--allow-entropy", "--version"),
        ("--allow-entropy", "package"),
        ("package", "--allow-entropy"),
        ("test", "--allow-entropy"),
        ("format", "--allow-entropy"),
        ("lint", "--allow-entropy"),
        ("language-server", "--allow-entropy"),
    ]
    for arguments in invalid_commands:
        expect(
            executable, 2,
            f"usage denial for {' '.join(arguments)}",
            *arguments,
        )

    print("I157_TOOL_DENIAL=PASS")


def inspect_direct_source(executable):
    with tempfile.TemporaryDirectory(prefix="i157-entropy-") as directory:
        source = Path(directory) / "direct.protos"
        source.write_text(
            'print(entropy.bytes(16).value().size())\n',
            encoding="utf-8",
        )

        expect(
            executable, 1,
            "ungranted direct source",
            str(source),
        )
        allowed = expect(
            executable, 0,
            "granted direct source",
            "--allow-entropy", str(source),
        )
        require(allowed == "16",
                "direct source must generate exactly 16 octets")

    print("I157_DIRECT_SOURCE=PASS")


def native_startup_fingerprint(executable, attempts):
    require(attempts >= 3, "at least three Native startups are required")

    # Print sample octets only into the private subprocess pipe.
    # The smoke itself never exposes the 32-byte sample in its output.
    lines = [
        "sample: entropy.bytes(32).value()",
        "print(sample.size())",
    ]
    for index in range(32):
        lines.append(f"print(sample[{index}])")
    source = "\n".join(lines) + "\n"

    fingerprints = []
    for number in range(attempts):
        result = expect(
            executable, 0,
            f"Native fresh-process startup {number + 1}",
            "--allow-entropy", "-e", source,
        ).splitlines()

        require(len(result) == 33,
                "Native sample must contain one length and 32 octets")
        require(result[0] == "32",
                "Native sample length must be 32")
        octets = tuple(int(text) for text in result[1:])
        require(all(0 <= octet <= 255 for octet in octets),
                "Native sample contains an invalid octet")
        fingerprints.append(octets)

    require(
        len(set(fingerprints)) == attempts,
        "identical samples across independent Native startups; "
        "inspect runtime RNG initialization and Native Image heap capture",
    )

    print(f"I157_NATIVE_COLD_STARTS={attempts}")
    print("I157_NATIVE_NO_FROZEN_SAMPLE=PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "executable", type=Path,
        help="already built JVM launcher or actual Native executable")
    parser.add_argument(
        "--mode", choices=("jvm", "native"), required=True)
    parser.add_argument(
        "--native-attempts", type=int, default=4)
    args = parser.parse_args()

    executable = args.executable.resolve()
    require(executable.is_file(),
            f"executable does not exist: {executable}")
    require(os.access(executable, os.X_OK),
            f"not executable: {executable}")

    inspect_permission_boundaries(executable)
    inspect_generation(executable)
    inspect_tool_denial(executable)
    inspect_direct_source(executable)

    if args.mode == "native":
        native_startup_fingerprint(executable, args.native_attempts)

    print(f"I157_{args.mode.upper()}_EXECUTABLE_SMOKE=PASS")


if __name__ == "__main__":
    main()
