#!/usr/bin/env python3
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

from __future__ import print_function

import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


TOOLCHAIN = {
    "schema": "protos-toolchain-v1",
    "java": {"bytecode_release": 21},
    "graalvm": {
        "distribution": "graalvm-community",
        "release": "25.3.4.1",
        "jdk_feature": 25,
        "jdk_version": "25.0.4.1",
        "container_channel": "25i3",
        "container_image": "ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol8-20260825",
    },
    "graal_components": {"version": "25.3.4.1"},
    "maven": {"version": "3.9.9"},
    "policy": {
        "primary_runtime_alignment": "development-ci-distribution",
        "upgrade_mode": "explicit-validated-change",
        "floating_primary_runtime": False,
    },
}


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def make_fixture(root, drift=False):
    write(root / "toolchain.json", json.dumps(TOOLCHAIN, indent=2) + "\n")
    write(
        root / "pom.xml",
        """<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><properties><maven.compiler.release>21</maven.compiler.release><graalvm.version>25.3.4.1</graalvm.version></properties></project>\n""",
    )
    write(
        root / ".devcontainer" / "Dockerfile",
        "FROM ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol8-20260825\nARG MAVEN_VERSION=3.9.9\n",
    )
    tests_distribution = "temurin" if drift else "graalvm-community"
    tests_java = "21" if drift else "25.0.4.1"
    write(
        root / ".github" / "workflows" / "tests.yml",
        "distribution: %s\njava-version: \"%s\"\n" % (tests_distribution, tests_java),
    )
    write(
        root / ".github" / "workflows" / "distribution.yml",
        "distribution: graalvm-community\njava-version: \"25.0.4.1\"\n",
    )
    write(
        root / "dist" / "runtime-pom.xml",
        """<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><properties><graalvm.version>25.3.4.1</graalvm.version></properties></project>\n""",
    )
    write(
        root / "dist" / "build_portable.py",
        'SUPPORTED_JAVA_FEATURE = "25"\nEXPECTED_TRUFFLE_VERSION = "25.3.4.1"\n',
    )
    write(
        root / "dist" / "smoke_optimizing_runtime.sh",
        "EXPECTED_FEATURE=25\nEXPECTED_JAVA_VERSION=25.0.4.1\nEXPECTED_TRUFFLE_VERSION=25.3.4.1\n",
    )


def run(verifier, root, mode):
    return subprocess.run(
        [sys.executable, str(verifier), "--root", str(root), "--mode", mode],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def main():
    verifier = Path(__file__).resolve().with_name("verify_toolchain.py")
    with tempfile.TemporaryDirectory(prefix="protos-toolchain-test-") as tmp:
        root = Path(tmp) / "repo"
        make_fixture(root, drift=False)
        result = run(verifier, root, "check")
        if result.returncode != 0:
            print(result.stdout)
            print(result.stderr, file=sys.stderr)
            raise SystemExit("aligned fixture unexpectedly failed")
        if "TOOLCHAIN_DRIFT_COUNT: 0" not in result.stdout:
            raise SystemExit("aligned fixture did not report zero drift")

        shutil.rmtree(root)
        make_fixture(root, drift=True)
        result = run(verifier, root, "check")
        if result.returncode != 1:
            print(result.stdout)
            print(result.stderr, file=sys.stderr)
            raise SystemExit("drift fixture did not fail closed")
        if "ci.tests.distribution" not in result.stdout or "ci.tests.java" not in result.stdout:
            raise SystemExit("drift fixture did not identify CI mismatch")

        result = run(verifier, root, "report")
        if result.returncode != 0 or "TOOLCHAIN_DRIFT_COUNT: 2" not in result.stdout:
            print(result.stdout)
            print(result.stderr, file=sys.stderr)
            raise SystemExit("report mode did not preserve non-blocking drift report")

        malformed = json.loads(json.dumps(TOOLCHAIN))
        malformed["graal_components"]["version"] = "25.3.4"
        write(root / "toolchain.json", json.dumps(malformed, indent=2) + "\n")
        result = run(verifier, root, "contract")
        if result.returncode != 2:
            raise SystemExit("malformed contract did not fail closed")

    print("TOOLCHAIN_VERIFIER_TESTS: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
