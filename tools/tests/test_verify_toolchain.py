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
import re
import runpy
import subprocess
import sys
import tempfile
from pathlib import Path

TOOLCHAIN = {
    "schema": "protos-toolchain-v2",
    "java": {"bytecode_release": 25},
    "graalvm": {
        "distribution": "graalvm-community",
        "release": "25.5.5.0.3",
        "jdk_feature": 25,
        "jdk_version": "25.0.5.0.3",
        "container_channel": "25i5",
        "container_image": "ghcr.io/graalvm/native-image-community:25i5-25.0.5.0.3-ol10@sha256:2800b5bd11cbb9bf11c92591d05ea37d9a735522bdc0e86827302d998843ad79",
    },
    "graal_components": {"version": "25.5.5.0.3"},
    "maven": {"minimum_version": "3.9.9", "supported_major": 3},
    "policy": {
        "primary_runtime_alignment": "development-ci-distribution",
        "upgrade_mode": "explicit-validated-change",
        "floating_primary_runtime": False,
    },
}

CI_IMAGE_LOCK = {
    "schema": "protos-ci-image-lock-v1",
    "image": "ghcr.io/guillermomolina/protos-ci@sha256:94c01739a95d6bbcb197180b86ef3aaa8c429483686d29d2b12d50ae35028fc1",
}

CI_IMAGE = CI_IMAGE_LOCK["image"]
STALE_CI_IMAGE = (
    "ghcr.io/guillermomolina/protos-ci@sha256:"
    + ("0" * 64)
)


REPOSITORY = Path(__file__).resolve().parents[2]
CACHE_ACTION_PATH = Path(".github") / "actions" / "maven-cache-restore" / "action.yml"
DIAGNOSTICS_PATH = Path(".github") / "workflows" / "ci-diagnostics.yml"

RESTORE_STEP = """      - name: Restore Maven dependency cache
        id: maven-cache
        uses: ./.github/actions/maven-cache-restore
"""
SAVE_KEY = "${{ steps.maven-cache.outputs.cache-key }}"
SAVE_STEP = """      - name: Save Maven dependency cache
        if: success() && steps.maven-cache.outputs.cache-hit != 'true'
        continue-on-error: true
        uses: actions/cache/save@v6
        with:
          path: /root/.m2/repository
          key: %s
""" % SAVE_KEY


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def cache_action_text():
    # The shared restore action is exercised as committed.
    return (REPOSITORY / CACHE_ACTION_PATH).read_text(encoding="utf-8")


def diagnostics_text(image):
    # The committed diagnostic workflow, rebound to the fixture CI image.
    text = (REPOSITORY / DIAGNOSTICS_PATH).read_text(encoding="utf-8")
    return re.sub(r"(?m)^(\s+image: ).+$", lambda m: m.group(1) + image, text)


def ci_job(name, image, command, cache_action="actions/cache/save@v6", workers="2"):
    extra = ""
    if name == "prepare":
        extra = """    outputs:
      count: ${{ steps.plan.outputs.count }}
      indices: ${{ steps.plan.outputs.indices }}
"""
    elif name == "java-parallel":
        extra = """    needs: prepare
    strategy:
      fail-fast: false
      matrix:
        shard: ${{ fromJSON(needs.prepare.outputs.indices) }}
    env:
      JAVA_TEST_SHARD_COUNT: ${{ needs.prepare.outputs.count }}
      JAVA_TEST_SHARD_INDEX: ${{ matrix.shard }}
"""
    elif name == "java-serial":
        extra = "    needs: prepare\n"
    body = "  %s:\n%s    container:\n      image: %s\n    steps:\n" % (name, extra, image)
    body += RESTORE_STEP
    if name in ("java-parallel", "java-serial"):
        body += """      - uses: actions/download-artifact@v4
        with:
          name: java-plan-${{ github.sha }}
"""
    body += "".join("      - name: Execute target\n        run: %s\n" % c for c in command.split("\n"))
    if name == "prepare":
        body += SAVE_STEP.replace("actions/cache/save@v6", cache_action)
        body += '      - run: python3 tools/ci_java_test_shards.py outputs >> "$GITHUB_OUTPUT"\n'
    return body


def ci_workflow(test_image, test_command, cache_action="actions/cache/save@v6", workers="2"):
    commands = {
        "prepare": "make prepare-java-test-shards",
        "compilerability": "make check",
        "java-parallel": test_command,
        "java-serial": "make test-java-serial",
        "protos-suite": "make prepare-protos-tests\nmake test-protos-suite",
        "protos-foreign": "make prepare-protos-tests\nmake test-protos-foreign",
        "native-image": "make test-native",
    }
    text = """name: CI
on:
  push:
    branches:
      - main
  pull_request:
  workflow_dispatch:
env:
  JAVA_TEST_SHARD_COUNT: "4"
  JAVA_TEST_JOBS: "%s"
  PROTOS_TEST_JOBS: "%s"
defaults:
  run:
    shell: bash
jobs:
""" % (workers, workers)
    text += "".join(ci_job(name, test_image, command, cache_action, workers)
                    for name, command in commands.items())
    return text + """  aggregate:
    if: always()
    needs: [prepare, compilerability, java-parallel, java-serial, protos-suite, protos-foreign, native-image]
    steps:
      - uses: actions/download-artifact@v4
        with:
          name: java-plan-${{ github.sha }}
      - uses: actions/download-artifact@v4
        with:
          pattern: java-evidence-*-${{ github.sha }}
      - run: python3 tools/ci_validation_aggregate.py --plan p --results r --dependencies "$DEPENDENCIES"
        env:
          DEPENDENCIES: ${{ toJSON(needs) }}
"""


def make_fixture(
    root,
    *,
    development_drift=False,
    old_c_state=False,
    ci_command_drift=False,
    ci_cache_drift=False,
    ci_workers_drift=False,
):
    selected_image = TOOLCHAIN["graalvm"]["container_image"]
    write(root / "toolchain.json", json.dumps(TOOLCHAIN, indent=2) + "\n")
    write(
        root / ".github" / "ci-image-lock.json",
        json.dumps(CI_IMAGE_LOCK, indent=2) + "\n",
    )

    components = "24.0.0" if old_c_state else "25.5.5.0.3"
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
<properties><maven.compiler.release>25</maven.compiler.release><graalvm.version>%s</graalvm.version></properties>
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
    test_command = "make test-protos" if ci_command_drift else "make test-java-parallel"
    test_workers = "6" if ci_workers_drift else "2"
    test_cache_action = (
        "actions/cache/save@v5"
        if ci_cache_drift
        else "actions/cache/save@v6"
    )
    if old_c_state:
        dist_components = "24.0.0"
        feature = "22"
        java_version = "22"
        graal_release = "24.0.0"
        launcher = "expected_feature=$(sed -n 's/^java_feature=//p' \"$RUNTIME_META\")\n"
    else:
        dist_components = "25.5.5.0.3"
        feature = "25"
        java_version = "25.0.5.0.3"
        graal_release = "25.5.5.0.3"
        launcher = """expected_version=$(sed -n 's/^java_version=//p' "$RUNTIME_META")
actual_version=25.0.5.0.3
[ "$actual_version" = "$expected_version" ] || supported=0
"""

    write(
        root / ".github" / "workflows" / "tests.yml",
        ci_workflow(
            test_ci_image,
            test_command,
            test_cache_action,
            test_workers,
        ),
    )
    write(root / CACHE_ACTION_PATH, cache_action_text())
    write(root / DIAGNOSTICS_PATH, diagnostics_text(test_ci_image))

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
        root / "dist" / "smoke" / "smoke_optimizing_runtime.sh",
        "EXPECTED_BYTECODE_RELEASE=%s\nEXPECTED_FEATURE=%s\nEXPECTED_JAVA_VERSION=%s\nEXPECTED_TRUFFLE_VERSION=%s\n"
        % (
            TOOLCHAIN["java"]["bytecode_release"],
            feature,
            java_version,
            dist_components,
        ),
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
    verifier = Path(__file__).resolve().parents[1] / "verify_toolchain.py"
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
Java version: 25.0.5.0.3, vendor: GraalVM Community, runtime: /opt/graalvm-community-java25i5
"""
    runtime_rows = evaluate_maven_runtime(
        TOOLCHAIN,
        good_maven_output,
        "/opt/graalvm-community-java25i5",
        ["maven-3.9.10-1.el10.noarch", "maven-unbound-3.9.10-1.el10.noarch"],
    )
    require(
        all(expected == actual for _, expected, actual in runtime_rows),
        "aligned Maven runtime evidence unexpectedly drifted",
    )

    below_floor_rows = evaluate_maven_runtime(
        TOOLCHAIN,
        good_maven_output.replace("Apache Maven 3.9.10", "Apache Maven 3.9.8"),
        "/opt/graalvm-community-java25i5",
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
        "/opt/graalvm-community-java25i5",
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
            "runtime: /opt/graalvm-community-java25i5",
            "runtime: /usr/lib/jvm/java-25-openjdk",
        ),
        "/opt/graalvm-community-java25i5",
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
        "/opt/graalvm-community-java25i5",
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

        # PLAT073: a Java 21 project contract is no longer accepted.
        root = tmp / "java21-contract-rejected"
        make_fixture(root)
        old_contract = json.loads(json.dumps(TOOLCHAIN))
        old_contract["java"]["bytecode_release"] = 21
        write(
            root / "toolchain.json",
            json.dumps(old_contract, indent=2) + "\n",
        )
        result = run(verifier, root, "contract")
        require(
            result.returncode == 2,
            "Java 21 toolchain contract was accepted",
            result,
        )
        require(
            "PLAT073 requires Java 25" in result.stderr,
            "PLAT073 rejection reason is missing",
            result,
        )

        # Maven must not silently compile Protos for the older target.
        root = tmp / "java21-pom-rejected"
        make_fixture(root)
        pom_path = root / "pom.xml"
        pom_text = pom_path.read_text(encoding="utf-8")
        current = "<maven.compiler.release>25</maven.compiler.release>"
        require(
            pom_text.count(current) == 1,
            "Java 25 Maven fixture is missing",
        )
        write(
            pom_path,
            pom_text.replace(
                current,
                "<maven.compiler.release>21</maven.compiler.release>",
                1,
            ),
        )
        result = run(verifier, root, "check", "development")
        require(
            result.returncode == 1,
            "Java 21 Maven compiler drift was accepted",
            result,
        )
        require(
            "pom.bytecode" in result.stdout,
            "Maven bytecode drift was not reported",
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

        # PLAT073: the portable smoke must follow the same bytecode floor.
        root = tmp / "java21-smoke-drift"
        make_fixture(root)
        smoke_path = root / "dist" / "smoke" / "smoke_optimizing_runtime.sh"
        smoke_text = smoke_path.read_text(encoding="utf-8")
        expected = "EXPECTED_BYTECODE_RELEASE=25"
        require(
            smoke_text.count(expected) == 1,
            "Java 25 smoke fixture is missing",
        )
        write(
            smoke_path,
            smoke_text.replace(
                expected,
                "EXPECTED_BYTECODE_RELEASE=21",
                1,
            ),
        )
        result = run(verifier, root, "check")
        require(
            result.returncode == 1,
            "Java 21 portable smoke metadata was accepted",
            result,
        )
        require(
            "dist.smoke.java_bytecode" in result.stdout,
            "Portable bytecode drift was not identified",
            result,
        )

        module = runpy.run_path(str(verifier))
        workflow_command = module["workflow_job_test_command"]
        workflow_workers = module["workflow_job_test_workers"]
        workflow_image = module["workflow_job_container_image"]
        workflow_cache = module["workflow_job_maven_cache_contract"]
        workflow = tmp / "distributed-fixture.yml"
        good_ci = ci_workflow(CI_IMAGE, "make test-java-parallel")
        write(workflow, good_ci)
        require(workflow_command(workflow, "aggregate") == "distributed validation gates",
                "distributed fixture was rejected")
        for lane in ("prepare", "compilerability", "java-parallel", "java-serial",
                     "protos-suite", "protos-foreign", "native-image", "aggregate"):
            write(workflow, good_ci.replace("  " + lane + ":\n", "  missing-lane:\n", 1))
            require(workflow_command(workflow, "aggregate") != "distributed validation gates",
                    "missing lane accepted: " + lane)
        for before, after in (("  push:\n", "  disabled-push:\n"),
                              ("  pull_request:\n", "  disabled-pr:\n"),
                              ("native-image]", "protos-suite]"),
                              ("run: make check", "continue-on-error: true\n        run: make check")):
            write(workflow, good_ci.replace(before, after, 1))
            require(workflow_command(workflow, "aggregate") != "distributed validation gates",
                    "unsafe workflow accepted: " + before)
        for duplicate in ("test", "test-java", "test-protos"):
            write(workflow, good_ci.replace("run: make check", "run: make check\n      - run: make " + duplicate))
            require(workflow_command(workflow, "aggregate") != "distributed validation gates",
                    "duplicate aggregate accepted: " + duplicate)
        for before, after in (('JAVA_TEST_SHARD_COUNT: "4"', 'JAVA_TEST_SHARD_COUNT: "3"'),
                              ('JAVA_TEST_JOBS: "2"', 'JAVA_TEST_JOBS: "4"'),
                              ('PROTOS_TEST_JOBS: "2"', 'PROTOS_TEST_JOBS: "4"'),
                              ("fail-fast: false", "fail-fast: true"),
                              ("fromJSON(needs.prepare.outputs.indices)", "fromJSON('[1,2,3,4]')")):
            write(workflow, good_ci.replace(before, after, 1))
            require(workflow_workers(workflow, "java-parallel") != "4 shards; Java=2 Protos=2",
                    "unbounded worker configuration accepted: " + before)
        write(workflow, good_ci.replace(CI_IMAGE, STALE_CI_IMAGE, 1))
        require(workflow_image(workflow, "prepare") != CI_IMAGE, "single-job image drift accepted")
        write(workflow, good_ci.replace("actions/cache/save@v6", "actions/cache/save@v5", 1))
        require("drift" in workflow_cache(workflow, "prepare"), "single-job cache drift accepted")

        # Maven cache lifecycle: one shared restore per job, one explicit
        # non-blocking save in prepare consuming the precomputed key.
        expected_cache = module["MAVEN_CACHE_EXPECTED"]
        write(workflow, good_ci)
        for lane in ("prepare", "compilerability", "java-parallel", "java-serial",
                     "protos-suite", "protos-foreign", "native-image"):
            require(workflow_cache(workflow, lane) == expected_cache,
                    "good cache lifecycle rejected: " + lane)
        combined = good_ci.replace(
            "        uses: ./.github/actions/maven-cache-restore\n",
            "        uses: actions/cache@v6\n        with:\n          path: /root/.m2/repository\n"
            "          key: k\n", 1)
        hashed_key = "${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}"
        lifecycle_drift = (
            ("combined cache action", "prepare", combined),
            ("hashFiles save key", "prepare", good_ci.replace(SAVE_KEY, hashed_key, 1)),
            ("recomputed save key", "prepare",
             good_ci.replace(SAVE_KEY, "${{ runner.os }}-maven-${{ github.sha }}", 1)),
            ("incorrect save path", "prepare",
             good_ci.replace("          path: /root/.m2/repository\n", "          path: /root/.m2\n", 1)),
            ("incorrect save version", "prepare",
             good_ci.replace("actions/cache/save@v6", "actions/cache/save@v5", 1)),
            ("missing save", "prepare", good_ci.replace(SAVE_STEP, "", 1)),
            ("blocking save", "prepare",
             good_ci.replace("        continue-on-error: true\n", "", 1)),
            ("missing shared restore", "native-image",
             good_ci.replace("  native-image:\n    container:\n      image: %s\n    steps:\n%s"
                             % (CI_IMAGE, RESTORE_STEP),
                             "  native-image:\n    container:\n      image: %s\n    steps:\n" % CI_IMAGE, 1)),
            ("save from unexpected job", "compilerability",
             good_ci.replace("        run: make check\n", "        run: make check\n" + SAVE_STEP, 1)),
            ("restore after validation", "compilerability",
             good_ci.replace("  compilerability:\n    container:\n      image: %s\n    steps:\n%s"
                             "      - name: Execute target\n        run: make check\n"
                             % (CI_IMAGE, RESTORE_STEP),
                             "  compilerability:\n    container:\n      image: %s\n    steps:\n"
                             "      - name: Execute target\n        run: make check\n%s"
                             % (CI_IMAGE, RESTORE_STEP), 1)),
        )
        for label, lane, text in lifecycle_drift:
            require(text != good_ci, "cache drift fixture did not apply: " + label)
            write(workflow, text)
            require(workflow_cache(workflow, lane) != expected_cache,
                    "cache lifecycle drift accepted: " + label)
        for label, text in (
                ("combined cache action", combined),
                ("hashFiles expression", good_ci.replace(SAVE_KEY, hashed_key, 1)),
                ("tolerant validation step",
                 good_ci.replace("run: make check", "continue-on-error: true\n        run: make check", 1)),
                ("tolerant aggregation step",
                 good_ci.replace("      - run: python3 tools/ci_validation_aggregate.py",
                                 "      - continue-on-error: true\n        run: python3 tools/ci_validation_aggregate.py", 1)),
                ("job-level tolerance",
                 good_ci.replace("  native-image:\n", "  native-image:\n    continue-on-error: true\n", 1)),
                ("tolerance with expression",
                 good_ci.replace("        continue-on-error: true\n",
                                 "        continue-on-error: ${{ always() }}\n", 1))):
            require(text != good_ci, "suppression fixture did not apply: " + label)
            write(workflow, text)
            require(workflow_command(workflow, "aggregate") != "distributed validation gates",
                    "failure suppression accepted: " + label)

        action_contract = module["maven_cache_action_contract"]
        action_root = tmp / "cache-action"
        good_action = cache_action_text()
        write(action_root / CACHE_ACTION_PATH, good_action)
        require(action_contract(action_root), "shared restore action rejected")
        for label, before, after in (
                ("incorrect action path", "path: /root/.m2/repository", "path: /root/.m2"),
                ("incorrect restore version", "actions/cache/restore@v6", "actions/cache/restore@v5"),
                ("combined action", "actions/cache/restore@v6", "actions/cache@v6"),
                ("hashFiles key", "key: ${{ steps.key.outputs.key }}",
                 "key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}"),
                ("lost restore prefix", "          ${{ runner.os }}-maven-\n", "          maven-\n"),
                ("missing cache-hit output", "  cache-hit:\n", "  hit:\n")):
            require(good_action.count(before) == 1, "action fixture anchor missing: " + label)
            write(action_root / CACHE_ACTION_PATH, good_action.replace(before, after, 1))
            require(not action_contract(action_root), "shared action drift accepted: " + label)
        (action_root / CACHE_ACTION_PATH).unlink()
        require(not action_contract(action_root), "missing shared restore action accepted")

        diagnostics_contract = module["diagnostics_workflow_contract"]
        expected_diagnostics = module["DIAGNOSTIC_EXPECTED"]
        push_branches = module["workflow_push_branches"]
        permissions = module["workflow_permissions"]
        selection = module["diagnostic_selection"]

        # Line-bounded extraction never consumes later workflow sections.
        bounded = ("on:\n  push:\n    branches:\n      - a\n  workflow_dispatch:\n    inputs:\n"
                   "      x:\n        options:\n          - y\n"
                   "permissions:\n  contents: read\n  packages: read\n\n"
                   "concurrency:\n  group: g\nenv:\n  A: \"1\"\njobs:\n  j:\n    runs-on: x\n")
        require(push_branches(bounded) == ["a"], "branch extraction consumed later sections")
        require(permissions(bounded) == {"contents": "read", "packages": "read"},
                "permissions extraction consumed later sections")
        require(permissions("permissions:\n  contents: read\n    nested: x\n") is None,
                "malformed permissions block accepted")

        diag_root = tmp / "diagnostics"
        good_diag = diagnostics_text(CI_IMAGE)
        require(push_branches(good_diag) == ["ci-validation", "ci-validation-http", "ci-validation-java-2"],
                "diagnostic branches not extracted exactly")
        require(permissions(good_diag) == {"contents": "read", "packages": "read"},
                "diagnostic permissions not extracted exactly")
        require(selection(good_diag, "push", "ci-validation-http")
                == {"LANE": "http-root-failure", "SHARD": "1", "REPETITIONS": "20"},
                "HTTP bootstrap branch does not select 20 repetitions")
        require(selection(good_diag, "push", "ci-validation-java-2")
                == {"LANE": "java-shard", "SHARD": "2", "REPETITIONS": "1"},
                "Java bootstrap branch does not select shard 2")
        require(selection(good_diag, "push", "ci-validation")["LANE"] == "cache",
                "cache bootstrap branch does not select the cache lane")
        require(selection(good_diag, "push", "main")["LANE"] == "invalid",
                "an undeclared branch selects a runnable lane")

        def diagnostic_drift(text, image=CI_IMAGE):
            write(diag_root / DIAGNOSTICS_PATH, text)
            result = diagnostics_contract(diag_root, image)
            if result == expected_diagnostics:
                return set()
            match = re.match(r"^<drift:ci\.diagnostics:(.+)>$", result)
            return set(match.group(1).split(",")) if match else {result}

        def remove_step(text, name):
            begin = text.index("      - name: %s\n" % name)
            finish = text.index("      - name: ", begin + 1)
            return text[:begin] + text[finish:]

        def insert_after(text, anchor, addition):
            require(text.count(anchor) == 1, "diagnostic fixture anchor not unique: " + anchor)
            return text.replace(anchor, anchor + addition, 1)

        require(diagnostic_drift(good_diag) == set(), "diagnostic workflow rejected")
        require(diagnostic_drift(good_diag, STALE_CI_IMAGE) == {"image"},
                "stale expected image not isolated to the image contract")

        suppress = "        continue-on-error: true\n"
        cases = (
            ("stale workflow image", good_diag.replace(CI_IMAGE, STALE_CI_IMAGE), {"image"}),
            ("automatic main execution",
             good_diag.replace("      - ci-validation\n", "      - main\n", 1), {"branches"}),
            ("additional main branch",
             insert_after(good_diag, "      - ci-validation-java-2\n", "      - main\n"), {"branches"}),
            ("wildcard branch",
             insert_after(good_diag, "      - ci-validation-java-2\n", "      - ci-validation-*\n"), {"branches"}),
            ("pull request trigger",
             good_diag.replace("  workflow_dispatch:\n", "  pull_request:\n  workflow_dispatch:\n", 1), {"triggers"}),
            ("pull request target trigger",
             good_diag.replace("  workflow_dispatch:\n", "  pull_request_target:\n  workflow_dispatch:\n", 1),
             {"triggers"}),
            ("changed HTTP lane mapping",
             good_diag.replace("'ci-validation-http' && 'http-root-failure'", "'ci-validation-http' && 'cache'", 1),
             {"push_lanes"}),
            ("changed HTTP repetitions",
             good_diag.replace("'ci-validation-http' && '20'", "'ci-validation-http' && '5'", 1), {"push_lanes"}),
            ("changed Java shard",
             good_diag.replace("'ci-validation-java-2' && '2'", "'ci-validation-java-2' && '3'", 1), {"push_lanes"}),
            ("runnable fallback lane",
             good_diag.replace("|| 'invalid' }}", "|| 'cache' }}", 1), {"push_lanes"}),
            ("unknown input",
             good_diag.replace("      shard:\n", "      command:\n        description: Shell\n"
                               "        type: string\n      shard:\n", 1), {"inputs.declaration"}),
            ("unrestricted lane input",
             good_diag.replace("        type: choice\n        default: cache\n",
                               "        type: string\n        default: cache\n", 1), {"inputs.declaration"}),
            ("unbounded repetitions",
             good_diag.replace('          - "20"\n', '          - "20"\n          - "1000"\n', 1), {"inputs.choices"}),
            ("missing lane choice",
             good_diag.replace("          - http-root-failure\n", "", 1), {"inputs.choices"}),
            ("missing shard validation",
             good_diag.replace("            1|2|3|4) ;;\n", "            *) ;;\n", 1), {"input_validation"}),
            ("permissive lane validation",
             good_diag.replace("|http-root-failure) ;;", "|http-root-failure|*) ;;", 1), {"input_validation"}),
            ("write permission",
             good_diag.replace("  contents: read\n", "  contents: write\n", 1), {"permissions", "isolation"}),
            ("different runner",
             good_diag.replace("    runs-on: ubuntu-latest\n", "    runs-on: self-hosted\n", 1), {"runner"}),
            ("worker drift",
             good_diag.replace('  JAVA_TEST_JOBS: "2"\n', '  JAVA_TEST_JOBS: "4"\n', 1), {"workers"}),
            ("no obsolete cancellation",
             good_diag.replace("  cancel-in-progress: true\n", "  cancel-in-progress: false\n", 1), {"concurrency"}),
            ("hashFiles expression",
             good_diag.replace("key: ${{ steps.maven-cache.outputs.cache-key }}",
                               "key: ${{ hashFiles('**/pom.xml') }}", 1), {"cache_lifecycle"}),
            ("bypassed shared restore",
             good_diag.replace("uses: ./.github/actions/maven-cache-restore", "uses: actions/cache/restore@v6", 1),
             {"cache_lifecycle"}),
            ("missing diagnostic save", remove_step(good_diag, "Save cache round-trip marker"), {"cache_roundtrip"}),
            ("missing exact restore", remove_step(good_diag, "Restore cache round-trip marker"), {"cache_roundtrip"}),
            ("fallback restore keys",
             insert_after(good_diag, "          fail-on-cache-miss: true\n",
                          "          restore-keys: protos-ci-diagnostic-roundtrip-\n"), {"cache_roundtrip"}),
            ("missing marker removal", remove_step(good_diag, "Remove local cache round-trip marker"),
             {"cache_roundtrip"}),
            ("missing marker verification", remove_step(good_diag, "Verify cache round-trip marker"),
             {"cache_roundtrip"}),
            ("suppressed marker verification",
             insert_after(good_diag, "      - name: Verify cache round-trip marker\n", suppress),
             {"failure_suppression"}),
            ("suppressed HTTP diagnostic",
             insert_after(good_diag, "      - name: Repeat HTTP root-actor failure test\n", suppress),
             {"failure_suppression"}),
            ("suppressed Java shard",
             insert_after(good_diag, "      - name: Run selected Java parallel partition\n", suppress),
             {"failure_suppression"}),
            ("ignored Java shard status",
             good_diag.replace("tee target/ci-logs/java-parallel.log\n",
                               "tee target/ci-logs/java-parallel.log || true\n", 1), {"failure_suppression"}),
            ("HTTP automatic retries",
             good_diag.replace("-Dsurefire.rerunFailingTestsCount=0", "-Dsurefire.rerunFailingTestsCount=2", 1),
             {"http_execution"}),
            ("HTTP accepts no executed test",
             good_diag.replace("status=97", "status=0", 1), {"http_execution"}),
            ("aggregation replacement",
             good_diag.replace("      - name: Record lane wall time\n",
                               "      - name: Aggregate\n        run: python3 tools/ci_validation_aggregate.py\n"
                               "      - name: Record lane wall time\n", 1), {"isolation"}),
            ("foreign artifact name",
             good_diag.replace("name: ci-diagnostics-${{ env.LANE }}", "name: diag-${{ env.LANE }}", 1),
             {"artifacts"}),
            ("mandatory artifact name",
             good_diag.replace("name: ci-diagnostics-${{ env.LANE }}", "name: java-evidence-${{ env.LANE }}", 1),
             {"artifacts", "isolation"}),
        )
        for label, text, expected in cases:
            require(text != good_diag, "diagnostic fixture did not apply: " + label)
            actual = diagnostic_drift(text)
            require(actual == expected,
                    "diagnostic drift %s reported %s, expected %s" % (label, sorted(actual), sorted(expected)))
        (diag_root / DIAGNOSTICS_PATH).unlink()
        require(diagnostics_contract(diag_root, CI_IMAGE) != expected_diagnostics,
                "missing diagnostic workflow accepted")

        # The toolchain audit reports the specific failing diagnostic contract.
        root = tmp / "diagnostic-branch-drift"
        make_fixture(root)
        result = run(verifier, root, "check", "development")
        require(result.returncode == 0, "aligned diagnostic fixture failed", result)
        write(root / DIAGNOSTICS_PATH,
              diagnostics_text(CI_IMAGE).replace("      - ci-validation\n", "      - main\n", 1))
        result = run(verifier, root, "check", "development")
        require(result.returncode == 1, "diagnostic branch drift did not fail closed", result)
        require("<drift:ci.diagnostics:branches>" in result.stdout,
                "diagnostic drift did not name the failing contract", result)

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

        root = tmp / "ci-workers-drift"
        make_fixture(root, ci_workers_drift=True)
        result = run(verifier, root, "check", "development")
        require(
            result.returncode == 1,
            "CI worker drift did not fail closed",
            result,
        )
        require(
            "ci.tests.workers" in result.stdout,
            "CI worker drift did not identify ci.tests.workers",
            result,
        )

        root = tmp / "ci-cache-drift"
        make_fixture(root, ci_cache_drift=True)
        result = run(verifier, root, "check", "development")
        require(
            result.returncode == 1,
            "CI Maven cache drift did not fail closed",
            result,
        )
        require(
            "ci.tests.maven_cache" in result.stdout,
            "CI Maven cache drift did not identify ci.tests.maven_cache",
            result,
        )

        # A single source of container authority: changing the development
        # Dockerfile image must fail the development-scope binding check.
        root = tmp / "unified-container-drift"
        make_fixture(root)
        docker_path = root / ".devcontainer" / "Dockerfile"
        old_docker = docker_path.read_text(encoding="utf-8")
        selected = TOOLCHAIN["graalvm"]["container_image"]
        unexpected = selected.replace(
            "ghcr.io/graalvm/native-image-community:",
            "ghcr.io/graalvm/graalvm-community:",
            1,
        )
        require(unexpected != selected, "Fixture is not Native Image based")
        require(old_docker.count("FROM " + selected) == 1,
                "Cannot replace the unified Dockerfile test image")
        write(docker_path, old_docker.replace("FROM " + selected,
                                              "FROM " + unexpected, 1))
        result = run(verifier, root, "check", "development")
        require(result.returncode == 1,
                "An inconsistent devcontainer image was accepted", result)
        require("devcontainer.image" in result.stdout,
                "The devcontainer image mismatch was not reported", result)

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

        write(root / "toolchain.json", json.dumps(TOOLCHAIN, indent=2) + "\n")
        malformed_ci_lock = {
            "schema": "protos-ci-image-lock-v1",
            "image": "ghcr.io/guillermomolina/protos-ci:ci",
        }
        write(
            root / ".github" / "ci-image-lock.json",
            json.dumps(malformed_ci_lock, indent=2) + "\n",
        )
        result = run(verifier, root, "contract")
        require(
            result.returncode == 2,
            "floating CI image lock did not fail closed",
            result,
        )

    print("TOOLCHAIN_VERIFIER_TESTS: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
