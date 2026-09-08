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


def ci_workflow(image, feature, version, maven):
    return """jobs:
  job:
    container:
      image: %s
    env:
      PROTOS_PRIMARY_JDK_FEATURE: "%s"
      PROTOS_PRIMARY_JDK_VERSION: "%s"
      PROTOS_MAVEN_VERSION: "%s"
""" % (image, feature, version, maven)


def make_fixture(root, *, development_drift=False, distribution_drift=False, old_c_state=False):
    selected_image = TOOLCHAIN["graalvm"]["container_image"]
    write(root / "toolchain.json", json.dumps(TOOLCHAIN, indent=2) + "\n")

    components = "24.0.0" if old_c_state else "25.3.4.1"
    write(
        root / "pom.xml",
        """<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><properties><maven.compiler.release>21</maven.compiler.release><graalvm.version>%s</graalvm.version></properties><build><plugins><plugin><configuration><transformers><transformer implementation=\"org.apache.maven.plugins.shade.resource.ServicesResourceTransformer\"/><transformer implementation=\"org.apache.maven.plugins.shade.resource.ManifestResourceTransformer\"><manifestEntries><Multi-Release>true</Multi-Release></manifestEntries></transformer></transformers><filters><filter><excludes><exclude>META-INF/*.SF</exclude><exclude>META-INF/*.DSA</exclude><exclude>META-INF/*.RSA</exclude></excludes></filter></filters></configuration></plugin></plugins></build></project>\n""" % components,
    )
    if old_c_state:
        write(root / "pom.xml", """<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><properties><maven.compiler.release>21</maven.compiler.release><graalvm.version>24.0.0</graalvm.version></properties></project>\n""")
    write(root / ".devcontainer" / "Dockerfile", "FROM %s\nARG MAVEN_VERSION=3.9.9\n" % selected_image)

    dev_image = "ghcr.io/graalvm/graalvm-community:25-ol8" if development_drift else selected_image
    dev_feature = "21" if development_drift else "25"
    dev_version = "21" if development_drift else "25.0.4.1"
    dev_maven = "3.9.8" if development_drift else "3.9.9"
    write(root / ".github" / "workflows" / "tests.yml", ci_workflow(dev_image, dev_feature, dev_version, dev_maven))

    if old_c_state:
        write(
            root / ".github" / "workflows" / "distribution.yml",
            "distribution: graalvm-community\njava-version: \"22.0.0\"\n",
        )
        dist_components = "24.0.0"
        feature = "22"
        java_version = "22"
        graal_release = "24.0.0"
        launcher = "expected_feature=$(sed -n 's/^java_feature=//p' \"$RUNTIME_META\")\n"
    else:
        dist_image = "ghcr.io/graalvm/graalvm-community:25-ol8" if distribution_drift else selected_image
        dist_feature = "24" if distribution_drift else "25"
        dist_version = "24" if distribution_drift else "25.0.4.1"
        dist_maven = "3.9.8" if distribution_drift else "3.9.9"
        write(root / ".github" / "workflows" / "distribution.yml", ci_workflow(dist_image, dist_feature, dist_version, dist_maven))
        dist_components = "25.3.4.1"
        feature = "25"
        java_version = "25.0.4.1"
        graal_release = "25.3.4.1"
        launcher = """expected_version=$(sed -n 's/^java_version=//p' \"$RUNTIME_META\")
actual_version=25.0.4.1
[ \"$actual_version\" = \"$expected_version\" ] || supported=0
"""

    write(
        root / "dist" / "runtime-pom.xml",
        """<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><properties><graalvm.version>%s</graalvm.version></properties></project>\n""" % dist_components,
    )
    write(
        root / "dist" / "build_portable.py",
        'SUPPORTED_JAVA_FEATURE = "%s"\nSUPPORTED_JAVA_VERSION = "%s"\nSUPPORTED_GRAALVM_RELEASE = "%s"\nEXPECTED_TRUFFLE_VERSION = "%s"\n'
        % (feature, java_version, graal_release, dist_components),
    )
    write(
        root / "dist" / "smoke_optimizing_runtime.sh",
        "EXPECTED_FEATURE=%s\nEXPECTED_JAVA_VERSION=%s\nEXPECTED_TRUFFLE_VERSION=%s\n"
        % (feature, java_version, dist_components),
    )
    write(root / "bin" / "protos", launcher)


def run(verifier, root, mode, scope="all"):
    return subprocess.run(
        [sys.executable, str(verifier), "--root", str(root), "--mode", mode, "--scope", scope],
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
        tmp = Path(tmp)

        root = tmp / "aligned"
        make_fixture(root)
        result = run(verifier, root, "check")
        require(result.returncode == 0, "aligned fixture unexpectedly failed", result)
        require("TOOLCHAIN_DRIFT_COUNT: 0" in result.stdout, "aligned fixture did not report zero drift", result)

        root = tmp / "development-drift"
        make_fixture(root, development_drift=True)
        result = run(verifier, root, "check", "development")
        require(result.returncode == 1, "development drift did not fail closed", result)
        for binding in ("ci.tests.image", "ci.tests.java_feature", "ci.tests.java_version", "ci.tests.maven"):
            require(binding in result.stdout, "development drift did not identify %s" % binding, result)

        root = tmp / "distribution-drift"
        make_fixture(root, distribution_drift=True)
        result = run(verifier, root, "check")
        require(result.returncode == 1, "distribution drift did not fail closed", result)
        for binding in (
            "ci.distribution.image",
            "ci.distribution.java_feature",
            "ci.distribution.java_version",
            "ci.distribution.maven",
        ):
            require(binding in result.stdout, "distribution drift did not identify %s" % binding, result)

        root = tmp / "pre-c"
        make_fixture(root, old_c_state=True)
        result = run(verifier, root, "check", "development")
        require(result.returncode == 0, "pre-C state should remain development-aligned", result)
        result = run(verifier, root, "check")
        require(result.returncode == 1, "pre-C all-surface drift did not fail closed", result)
        for binding in (
            "pom.graal_components",
            "pom.shade_multi_release",
            "pom.shade_services",
            "pom.shade_signature_filter",
            "ci.distribution.image",
            "dist.runtime_pom.graal_components",
            "dist.builder.java_version",
            "dist.smoke.graal_components",
            "dist.launcher.java_version_gate",
        ):
            require(binding in result.stdout, "pre-C drift did not identify %s" % binding, result)

        malformed = json.loads(json.dumps(TOOLCHAIN))
        malformed["graal_components"]["version"] = "25.3.4"
        write(root / "toolchain.json", json.dumps(malformed, indent=2) + "\n")
        result = run(verifier, root, "contract")
        require(result.returncode == 2, "malformed contract did not fail closed", result)

    print("TOOLCHAIN_VERIFIER_TESTS: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
