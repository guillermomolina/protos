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
import runpy
import subprocess
import sys
import tempfile
from pathlib import Path

TOOLCHAIN = {
    "schema": "protos-toolchain-v2",
    "java": {"bytecode_release": 21},
    "graalvm": {
        "distribution": "graalvm-community",
        "release": "25.4.4.1.1",
        "jdk_feature": 25,
        "jdk_version": "25.0.4.1.1",
        "container_channel": "25i4",
        "container_image": "ghcr.io/graalvm/graalvm-community:25i4-25.0.4.1.1-ol10@sha256:a7b4810d7c755e9627feaa1459eb5a93338643b16d745d4f3fc86db71e5da7f5",
    },
    "graal_components": {"version": "25.4.4.1.1"},
    "maven": {"minimum_version": "3.9.9", "supported_major": 3},
    "ci": {
        "image": "ghcr.io/guillermomolina/protos-ci@sha256:94c01739a95d6bbcb197180b86ef3aaa8c429483686d29d2b12d50ae35028fc1",
    },
    "policy": {
        "primary_runtime_alignment": "development-ci-distribution",
        "upgrade_mode": "explicit-validated-change",
        "floating_primary_runtime": False,
    },
}

NATIVE_IMAGE = (
    "ghcr.io/graalvm/native-image-community:"
    "25i4-25.0.4.1.1-ol10"
)
STALE_NATIVE_IMAGE = (
    "ghcr.io/graalvm/native-image-community:"
    "25i3-25.0.4.1-ol10-20260825"
)

CI_IMAGE = TOOLCHAIN["ci"]["image"]
STALE_CI_IMAGE = (
    "ghcr.io/guillermomolina/protos-ci@sha256:"
    + ("0" * 64)
)


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def ci_job(name, image, command):
    return """  %s:
    container:
      image: %s
    steps:
      - name: Run repository tests
        run: |
          %s
""" % (name, image, command)


def ci_workflow(test_image, test_command):
    return "jobs:\n" + ci_job(
        "test",
        test_image,
        test_command,
    )

def make_fixture(
    root,
    *,
    development_drift=False,
    native_drift=False,
    old_c_state=False,
    ci_command_drift=False,
):
    selected_image = TOOLCHAIN["graalvm"]["container_image"]
    write(root / "toolchain.json", json.dumps(TOOLCHAIN, indent=2) + "\n")

    native_image = STALE_NATIVE_IMAGE if native_drift else NATIVE_IMAGE
    native_maven = (
        "RUN microdnf install -y \\\n"
        "        maven \\\n"
        "    && microdnf clean all\n"
        if native_drift
        else
        "RUN microdnf install -y \\\n"
        "        --enablerepo=ol10_codeready_builder \\\n"
        "        maven \\\n"
        "        maven-unbound \\\n"
        "    && microdnf clean all\n"
    )
    write(
        root / "build" / "native" / "Dockerfile",
        "FROM %s\n%s"
        'ENV PATH="${JAVA_HOME}/bin:${PATH}"\n' % (native_image, native_maven),
    )

    components = "24.0.0" if old_c_state else "25.4.4.1.1"
    graal_dependencies = """<dependencies>
<dependency><groupId>org.graalvm.sdk</groupId><artifactId>graal-sdk</artifactId><version>${graalvm.version}</version></dependency>
<dependency><groupId>org.graalvm.polyglot</groupId><artifactId>polyglot</artifactId><version>${graalvm.version}</version></dependency>
<dependency><groupId>org.graalvm.truffle</groupId><artifactId>truffle-api</artifactId><version>${graalvm.version}</version></dependency>
<dependency><groupId>org.graalvm.truffle</groupId><artifactId>truffle-runtime</artifactId><version>${graalvm.version}</version><scope>runtime</scope></dependency>
<dependency><groupId>org.graalvm.polyglot</groupId><artifactId>dap</artifactId><version>${graalvm.version}</version><type>pom</type><scope>runtime</scope></dependency>
<dependency><groupId>org.graalvm.polyglot</groupId><artifactId>lsp</artifactId><version>${graalvm.version}</version><type>pom</type><scope>test</scope></dependency>
</dependencies>"""

    graph_projection = ""
    shade_externalization = ""
    if not old_c_state:
        graph_projection = """<plugin>
<groupId>org.apache.maven.plugins</groupId><artifactId>maven-dependency-plugin</artifactId><version>3.11.0</version>
<executions><execution><id>materialize-canonical-graal-runtime-plane</id><configuration><graphRoots>
<graphRoot><groupId>org.graalvm.sdk</groupId><artifactId>graal-sdk</artifactId></graphRoot>
<graphRoot><groupId>org.graalvm.polyglot</groupId><artifactId>polyglot</artifactId></graphRoot>
<graphRoot><groupId>org.graalvm.truffle</groupId><artifactId>truffle-api</artifactId></graphRoot>
<graphRoot><groupId>org.graalvm.truffle</groupId><artifactId>truffle-runtime</artifactId></graphRoot>
<graphRoot><groupId>org.graalvm.polyglot</groupId><artifactId>dap</artifactId></graphRoot>
</graphRoots></configuration></execution></executions>
</plugin>"""
        shade_externalization = (
            "<artifactSet><excludes><exclude>org.graalvm.*:*</exclude>"
            "</excludes></artifactSet>"
        )

    pom = """<project xmlns="http://maven.apache.org/POM/4.0.0">
<properties><maven.compiler.release>21</maven.compiler.release><graalvm.version>%s</graalvm.version></properties>
%s
<build><plugins>
<plugin><artifactId>maven-shade-plugin</artifactId><configuration>
%s
<transformers><transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
<transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer"><manifestEntries><Multi-Release>true</Multi-Release></manifestEntries></transformer></transformers>
<filters><filter><excludes><exclude>META-INF/*.SF</exclude><exclude>META-INF/*.DSA</exclude><exclude>META-INF/*.RSA</exclude></excludes></filter></filters>
</configuration></plugin>
%s
</plugins></build></project>
""" % (components, graal_dependencies, shade_externalization, graph_projection)
    write(root / "pom.xml", pom)
    if development_drift:
        write(
            root / ".devcontainer" / "Dockerfile",
            "FROM %s\n"
            "RUN microdnf install -y \\\n"
            "        maven \\\n"
            "    && microdnf clean all\n" % selected_image,
        )
    else:
        write(
            root / ".devcontainer" / "Dockerfile",
            "FROM %s\n"
            "RUN microdnf install -y \\\n"
            "        findutils \\\n"
            "        procps \\\n"
            "    && microdnf clean all\n"
            "RUN microdnf install -y \\\n"
            "        --enablerepo=ol10_codeready_builder \\\n"
            "        maven \\\n"
            "        maven-unbound \\\n"
            "    && microdnf clean all\n"
            'ENV PATH="${JAVA_HOME}/bin:${PATH}"\n' % selected_image,
        )

    test_ci_image = STALE_CI_IMAGE if development_drift else CI_IMAGE
    test_command = (
        "make test JAVA_TEST_JOBS=4 PROTOS_TEST_JOBS=4"
        if ci_command_drift
        else "make test"
    )
    if old_c_state:
        dist_components = "24.0.0"
        feature = "22"
        java_version = "22"
        graal_release = "24.0.0"
        launcher = "expected_feature=$(sed -n 's/^java_feature=//p' \"$RUNTIME_META\")\n"
    else:
        dist_components = "25.4.4.1.1"
        feature = "25"
        java_version = "25.0.4.1.1"
        graal_release = "25.4.4.1.1"
        launcher = """expected_version=$(sed -n 's/^java_version=//p' "$RUNTIME_META")
actual_version=25.0.4.1.1
[ "$actual_version" = "$expected_version" ] || supported=0
"""

    write(
        root / ".github" / "workflows" / "tests.yml",
        ci_workflow(
            test_ci_image,
            test_command,
        ),
    )

    if old_c_state:
        write(
            root / "dist" / "runtime-pom.xml",
            """<project xmlns="http://maven.apache.org/POM/4.0.0"><properties><graalvm.version>%s</graalvm.version></properties></project>
""" % dist_components,
        )
        builder_projection = (
            'DEPENDENCY_PLUGIN = "legacy"\n'
            'runtime_descriptor = "dist/runtime-pom.xml"\n'
        )
    else:
        builder_projection = (
            'source_runtime_dir = root / "target" / "runtime"\n'
            '"runtime_authority=pom.xml"\n'
        )

    write(
        root / "dist" / "build_portable.py",
        'SUPPORTED_JAVA_FEATURE = "%s"\nSUPPORTED_JAVA_VERSION = "%s"\nSUPPORTED_GRAALVM_RELEASE = "%s"\nEXPECTED_TRUFFLE_VERSION = "%s"\n%s'
        % (feature, java_version, graal_release, dist_components, builder_projection),
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
    verifier_api = runpy.run_path(str(verifier))
    maven_version_supported = verifier_api["maven_version_supported"]
    evaluate_maven_runtime = verifier_api["evaluate_maven_runtime"]

    for version in ("3.9.9", "3.9.10", "3.10.0", "3.99.1"):
        require(
            maven_version_supported(version, TOOLCHAIN),
            "supported Maven version was rejected: %s" % version,
        )
    for version in ("3.9.8", "4.0.0", "3.9.9-rc-1", "3.9", "garbage"):
        require(
            not maven_version_supported(version, TOOLCHAIN),
            "unsupported Maven version was accepted: %s" % version,
        )

    good_maven_output = """Apache Maven 3.9.10 (Red Hat 3.9.10-1)
Maven home: /usr/share/maven
Java version: 25.0.4.1.1, vendor: GraalVM Community, runtime: /opt/graalvm-community-java25i4
"""
    runtime_rows = evaluate_maven_runtime(
        TOOLCHAIN,
        good_maven_output,
        "/opt/graalvm-community-java25i4",
        ["maven-3.9.10-1.el10.noarch", "maven-unbound-3.9.10-1.el10.noarch"],
    )
    require(
        all(expected == actual for _, expected, actual in runtime_rows),
        "aligned Maven runtime evidence unexpectedly drifted",
    )

    below_floor_rows = evaluate_maven_runtime(
        TOOLCHAIN,
        good_maven_output.replace("Apache Maven 3.9.10", "Apache Maven 3.9.8"),
        "/opt/graalvm-community-java25i4",
        [],
    )
    require(
        any(
            name == "runtime.maven.version" and expected != actual
            for name, expected, actual in below_floor_rows
        ),
        "below-floor Maven runtime was accepted",
    )

    wrong_java_rows = evaluate_maven_runtime(
        TOOLCHAIN,
        good_maven_output.replace("vendor: GraalVM Community", "vendor: Oracle"),
        "/opt/graalvm-community-java25i4",
        [],
    )
    require(
        any(
            name == "runtime.maven.java_vendor" and expected != actual
            for name, expected, actual in wrong_java_rows
        ),
        "non-GraalVM Maven Java vendor was accepted",
    )

    wrong_runtime_rows = evaluate_maven_runtime(
        TOOLCHAIN,
        good_maven_output.replace(
            "runtime: /opt/graalvm-community-java25i4",
            "runtime: /usr/lib/jvm/java-25-openjdk",
        ),
        "/opt/graalvm-community-java25i4",
        [],
    )
    require(
        any(
            name == "runtime.maven.java_runtime" and expected != actual
            for name, expected, actual in wrong_runtime_rows
        ),
        "Maven runtime outside JAVA_HOME was accepted",
    )

    redundant_jdk_rows = evaluate_maven_runtime(
        TOOLCHAIN,
        good_maven_output,
        "/opt/graalvm-community-java25i4",
        ["java-25-openjdk-headless-25.0.1.0.8-1.el10.x86_64"],
    )
    require(
        any(
            name == "runtime.redundant_openjdk_rpm" and expected != actual
            for name, expected, actual in redundant_jdk_rows
        ),
        "redundant OpenJDK RPM was accepted",
    )

    with tempfile.TemporaryDirectory(prefix="protos-toolchain-test-") as tmp:
        tmp = Path(tmp)

        root = tmp / "aligned"
        make_fixture(root)
        result = run(verifier, root, "check")
        require(result.returncode == 0, "aligned fixture unexpectedly failed", result)
        require(
            "TOOLCHAIN_DRIFT_COUNT: 0" in result.stdout,
            "aligned fixture did not report zero drift",
            result,
        )

        root = tmp / "development-drift"
        make_fixture(root, development_drift=True)
        result = run(verifier, root, "check", "development")
        require(result.returncode == 1, "development drift did not fail closed", result)
        for binding in (
            "devcontainer.maven_provisioning",
            "devcontainer.maven_repository_scope",
            "devcontainer.java_path",
            "ci.tests.container",
        ):
            require(
                binding in result.stdout,
                "development drift did not identify %s" % binding,
                result,
            )

        root = tmp / "ci-command-drift"
        make_fixture(root, ci_command_drift=True)
        result = run(verifier, root, "check", "development")
        require(
            result.returncode == 1,
            "CI command drift did not fail closed",
            result,
        )
        require(
            "ci.tests.command" in result.stdout,
            "CI command drift did not identify ci.tests.command",
            result,
        )

        root = tmp / "native-drift"
        make_fixture(root, native_drift=True)

        result = run(verifier, root, "check", "development")
        require(
            result.returncode == 0,
            "native-only drift should not affect development scope",
            result,
        )

        result = run(verifier, root, "check")
        require(
            result.returncode == 1,
            "native-only drift did not fail the all-surface check",
            result,
        )
        for binding in (
            "native.image",
            "native.maven_provisioning",
            "native.maven_repository_scope",
        ):
            require(
                binding in result.stdout,
                "native-only drift did not identify %s" % binding,
                result,
            )

        root = tmp / "pre-c"
        make_fixture(root, old_c_state=True)
        result = run(verifier, root, "check", "development")
        require(
            result.returncode == 0,
            "pre-C state should remain development-aligned",
            result,
        )
        result = run(verifier, root, "check")
        require(
            result.returncode == 1,
            "pre-C all-surface drift did not fail closed",
            result,
        )
        for binding in (
            "pom.graal_components",
            "pom.shade_multi_release",
            "pom.shade_services",
            "pom.shade_signature_filter",
            "pom.runtime_plane.graph_roots",
            "pom.runtime_plane.shade_externalization",
            "dist.runtime_pom_absent",
            "dist.builder.canonical_runtime_projection",
            "dist.builder.java_version",
            "dist.smoke.graal_components",
            "dist.launcher.java_version_gate",
        ):
            require(
                binding in result.stdout,
                "pre-C drift did not identify %s" % binding,
                result,
            )

        old_exact_schema = json.loads(json.dumps(TOOLCHAIN))
        old_exact_schema["schema"] = "protos-toolchain-v1"
        old_exact_schema["maven"] = {"version": "3.9.9"}
        write(root / "toolchain.json", json.dumps(old_exact_schema, indent=2) + "\n")
        result = run(verifier, root, "contract")
        require(
            result.returncode == 2,
            "legacy exact-Maven schema did not fail closed",
            result,
        )

        malformed_maven = json.loads(json.dumps(TOOLCHAIN))
        malformed_maven["maven"]["minimum_version"] = "3.9"
        write(root / "toolchain.json", json.dumps(malformed_maven, indent=2) + "\n")
        result = run(verifier, root, "contract")
        require(
            result.returncode == 2,
            "malformed Maven minimum did not fail closed",
            result,
        )

        malformed = json.loads(json.dumps(TOOLCHAIN))
        malformed["graal_components"]["version"] = "25.3.4"
        write(root / "toolchain.json", json.dumps(malformed, indent=2) + "\n")
        result = run(verifier, root, "contract")
        require(
            result.returncode == 2,
            "malformed contract did not fail closed",
            result,
        )

    print("TOOLCHAIN_VERIFIER_TESTS: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
