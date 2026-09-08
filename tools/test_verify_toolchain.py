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


def tests_workflow(*, drift=False):
    image = (
        "ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol8-20260825"
        if not drift
        else "ghcr.io/graalvm/graalvm-community:25-ol8"
    )
    feature = "25" if not drift else "21"
    version = "25.0.4.1" if not drift else "21"
    maven = "3.9.9" if not drift else "3.9.8"
    return """jobs:
  test:
    container:
      image: %s
    env:
      PROTOS_PRIMARY_JDK_FEATURE: \"%s\"
      PROTOS_PRIMARY_JDK_VERSION: \"%s\"
      PROTOS_MAVEN_VERSION: \"%s\"
""" % (image, feature, version, maven)


def make_fixture(root, *, development_drift=False, remaining_c_drift=False):
    write(root / "toolchain.json", json.dumps(TOOLCHAIN, indent=2) + "\n")
    components = "24.0.0" if remaining_c_drift else "25.3.4.1"
    write(
        root / "pom.xml",
        """<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><properties><maven.compiler.release>21</maven.compiler.release><graalvm.version>%s</graalvm.version></properties></project>\n""" % components,
    )
    write(
        root / ".devcontainer" / "Dockerfile",
        "FROM ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol8-20260825\nARG MAVEN_VERSION=3.9.9\n",
    )
    write(
        root / ".github" / "workflows" / "tests.yml",
        tests_workflow(drift=development_drift),
    )
    if remaining_c_drift:
        dist_java = "22.0.0"
        dist_components = "24.0.0"
        feature = "22"
        smoke_java = "22"
    else:
        dist_java = "25.0.4.1"
        dist_components = "25.3.4.1"
        feature = "25"
        smoke_java = "25.0.4.1"
    write(
        root / ".github" / "workflows" / "distribution.yml",
        "distribution: graalvm-community\njava-version: \"%s\"\n" % dist_java,
    )
    write(
        root / "dist" / "runtime-pom.xml",
        """<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><properties><graalvm.version>%s</graalvm.version></properties></project>\n""" % dist_components,
    )
    write(
        root / "dist" / "build_portable.py",
        'SUPPORTED_JAVA_FEATURE = "%s"\nEXPECTED_TRUFFLE_VERSION = "%s"\n' % (feature, dist_components),
    )
    write(
        root / "dist" / "smoke_optimizing_runtime.sh",
        "EXPECTED_FEATURE=%s\nEXPECTED_JAVA_VERSION=%s\nEXPECTED_TRUFFLE_VERSION=%s\n" % (
            feature,
            smoke_java,
            dist_components,
        ),
    )


def run(verifier, root, mode, scope="all"):
    return subprocess.run(
        [
            sys.executable,
            str(verifier),
            "--root",
            str(root),
            "--mode",
            mode,
            "--scope",
            scope,
        ],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def require(condition, message, result=None):
    if condition:
        return
    if result is not None:
        print(result.stdout)
        print(result.stderr, file=sys.stderr)
    raise SystemExit(message)


def main():
    verifier = Path(__file__).resolve().with_name("verify_toolchain.py")
    with tempfile.TemporaryDirectory(prefix="protos-toolchain-test-") as tmp:
        root = Path(tmp) / "repo"

        make_fixture(root)
        result = run(verifier, root, "check")
        require(result.returncode == 0, "aligned fixture unexpectedly failed", result)
        require("TOOLCHAIN_DRIFT_COUNT: 0" in result.stdout, "aligned fixture did not report zero drift", result)

        root = Path(tmp) / "dev-drift"
        make_fixture(root, development_drift=True)
        result = run(verifier, root, "check", "development")
        require(result.returncode == 1, "development drift did not fail closed", result)
        for binding in (
            "ci.tests.image",
            "ci.tests.java_feature",
            "ci.tests.java_version",
            "ci.tests.maven",
        ):
            require(binding in result.stdout, "development drift did not identify %s" % binding, result)

        root = Path(tmp) / "c-pending"
        make_fixture(root, remaining_c_drift=True)
        result = run(verifier, root, "check", "development")
        require(result.returncode == 0, "DIST002-B transitional fixture failed development scope", result)
        require("TOOLCHAIN_DRIFT_COUNT: 0" in result.stdout, "development scope retained unexpected drift", result)
        result = run(verifier, root, "check", "all")
        require(result.returncode == 1, "remaining DIST002-C drift did not fail all-scope check", result)
        require("pom.graal_components" in result.stdout, "remaining C drift did not include pom components", result)
        require("ci.distribution.java" in result.stdout, "remaining C drift did not include distribution CI runtime", result)
        result = run(verifier, root, "report", "all")
        require(result.returncode == 0, "all-scope report should be non-blocking", result)

        malformed = json.loads(json.dumps(TOOLCHAIN))
        malformed["graal_components"]["version"] = "25.3.4"
        write(root / "toolchain.json", json.dumps(malformed, indent=2) + "\n")
        result = run(verifier, root, "contract")
        require(result.returncode == 2, "malformed contract did not fail closed", result)

    print("TOOLCHAIN_VERIFIER_TESTS: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
