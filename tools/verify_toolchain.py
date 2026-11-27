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

import argparse
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Tuple


class ToolchainError(Exception):
    pass


DEVELOPMENT_BINDINGS = {
    "pom.bytecode",
    "devcontainer.image",
    "devcontainer.maven_provisioning",
    "devcontainer.maven_repository_scope",
    "devcontainer.java_path",
    "ci.tests.container",
    "ci.tests.command",
    "ci.tests.workers",
    "ci.tests.maven_cache",
    "ci.diagnostics.contract",
}

MAVEN_PROVISIONING_MODEL = "ol10-rpm:maven+maven-unbound"
MAVEN_REPOSITORY_SCOPE = "ol10_codeready_builder:maven-only"


def read_text(path):
    # type: (Path) -> str
    if not path.is_file():
        raise ToolchainError("required file is missing: %s" % path)
    return path.read_text(encoding="utf-8")


def stable_maven_version_tuple(value, label="Maven version"):
    # type: (str, str) -> Tuple[int, int, int]
    match = re.fullmatch(r"([0-9]+)\.([0-9]+)\.([0-9]+)", str(value))
    if not match:
        raise ToolchainError("%s must be a stable x.y.z coordinate" % label)
    return tuple(int(part) for part in match.groups())


def maven_version_supported(version, contract):
    # type: (str, Dict[str, object]) -> bool
    try:
        actual = stable_maven_version_tuple(version)
        maven = contract["maven"]
        minimum = stable_maven_version_tuple(
            str(maven["minimum_version"]),
            "Maven minimum_version",
        )
        supported_major = int(maven["supported_major"])
    except (KeyError, TypeError, ValueError, ToolchainError):
        return False
    return actual[0] == supported_major and actual >= minimum


def parse_maven_runtime(output):
    # type: (str) -> Tuple[str, str, str, str]
    header = re.search(
        r"^Apache Maven[ \t]+([^ \t\r\n]+)(?:[ \t].*)?$",
        output,
        flags=re.MULTILINE,
    )
    java = re.search(
        r"^Java version:[ \t]*([^,\r\n]+),"
        r"[ \t]*vendor:[ \t]*([^,\r\n]+),"
        r"[ \t]*runtime:[ \t]*(.+?)[ \t]*$",
        output,
        flags=re.MULTILINE,
    )
    if not header:
        raise ToolchainError("mvn -version did not report an Apache Maven version")
    if not java:
        raise ToolchainError("mvn -version did not report Java version/vendor/runtime")
    return (
        header.group(1).strip(),
        java.group(1).strip(),
        java.group(2).strip(),
        java.group(3).strip(),
    )


def evaluate_maven_runtime(contract, mvn_output, java_home, rpm_packages):
    # type: (Dict[str, object], str, str, List[str]) -> List[Tuple[str, str, str]]
    if not java_home:
        raise ToolchainError("JAVA_HOME is required for Maven runtime validation")

    maven_version, java_version, java_vendor, java_runtime = parse_maven_runtime(
        mvn_output
    )
    maven = contract["maven"]
    minimum = str(maven["minimum_version"])
    supported_major = int(maven["supported_major"])
    version_contract = "stable-%d.x>=%s" % (supported_major, minimum)

    java_home_real = os.path.realpath(java_home)
    java_runtime_real = os.path.realpath(java_runtime)
    openjdk_packages = sorted(
        package.strip()
        for package in rpm_packages
        if package.strip() and "openjdk" in package.lower()
    )

    return [
        (
            "runtime.maven.version",
            version_contract,
            version_contract
            if maven_version_supported(maven_version, contract)
            else maven_version,
        ),
        (
            "runtime.maven.java_version",
            str(contract["graalvm"]["jdk_version"]),
            java_version,
        ),
        (
            "runtime.maven.java_vendor",
            "GraalVM Community",
            java_vendor,
        ),
        (
            "runtime.maven.java_runtime",
            "JAVA_HOME",
            "JAVA_HOME" if java_runtime_real == java_home_real else java_runtime,
        ),
        (
            "runtime.redundant_openjdk_rpm",
            "absent",
            "absent" if not openjdk_packages else ",".join(openjdk_packages),
        ),
    ]


def command_output(command, label):
    # type: (List[str], str) -> str
    try:
        result = subprocess.run(
            command,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
    except OSError as exc:
        raise ToolchainError("%s could not execute: %s" % (label, exc))
    if result.returncode != 0:
        raise ToolchainError(
            "%s failed with exit %d: %s"
            % (label, result.returncode, result.stdout.strip())
        )
    return result.stdout


def audit_runtime_environment(contract):
    # type: (Dict[str, object]) -> List[Tuple[str, str, str]]
    mvn_output = command_output(["mvn", "-version"], "mvn -version")
    rpm_output = command_output(["rpm", "-qa"], "rpm -qa")
    return evaluate_maven_runtime(
        contract,
        mvn_output,
        os.environ.get("JAVA_HOME", ""),
        rpm_output.splitlines(),
    )


def microdnf_install_transactions(text):
    # type: (str) -> List[List[str]]
    pattern = re.compile(
        r"^RUN[ \t]+microdnf install[ \t]+-y[ \t]*\\\n"
        r"(?P<body>.*?)"
        r"^[ \t]*&&[ \t]+microdnf clean all[ \t]*$",
        flags=re.MULTILINE | re.DOTALL,
    )
    transactions = []
    for match in pattern.finditer(text):
        items = []
        for line in match.group("body").splitlines():
            item = line.strip()
            if item.endswith("\\"):
                item = item[:-1].rstrip()
            if item and not item.startswith("#"):
                items.append(item)
        transactions.append(items)
    return transactions


def docker_maven_bindings(text):
    # type: (str) -> Tuple[str, str]
    transactions = microdnf_install_transactions(text)
    maven_transactions = [
        items
        for items in transactions
        if "maven" in items or "maven-unbound" in items
    ]
    expected_items = {
        "--enablerepo=ol10_codeready_builder",
        "maven",
        "maven-unbound",
    }
    if len(maven_transactions) != 1:
        return "missing", "drift"

    maven_items = set(maven_transactions[0])
    provisioning = (
        MAVEN_PROVISIONING_MODEL
        if maven_items == expected_items
        else "drift"
    )

    repository_transactions = [
        set(items)
        for items in transactions
        if "--enablerepo=ol10_codeready_builder" in items
    ]
    repository_scope = (
        MAVEN_REPOSITORY_SCOPE
        if len(repository_transactions) == 1
        and repository_transactions[0] == expected_items
        else "drift"
    )
    return provisioning, repository_scope


def load_contract(root):
    # type: (Path) -> Dict[str, object]
    path = root / "toolchain.json"
    try:
        data = json.loads(read_text(path))
    except ValueError as exc:
        raise ToolchainError("invalid toolchain.json: %s" % exc)

    if data.get("schema") != "protos-toolchain-v2":
        raise ToolchainError("unsupported toolchain schema")

    try:
        bytecode = int(data["java"]["bytecode_release"])
        graal = data["graalvm"]
        graal_release = str(graal["release"])
        feature = int(graal["jdk_feature"])
        jdk_version = str(graal["jdk_version"])
        channel = str(graal["container_channel"])
        image = str(graal["container_image"])
        components = str(data["graal_components"]["version"])
        maven_contract = data["maven"]
        maven_minimum = str(maven_contract["minimum_version"])
        maven_supported_major = int(maven_contract["supported_major"])
        policy = data["policy"]
    except (KeyError, TypeError, ValueError) as exc:
        raise ToolchainError("incomplete toolchain contract: %s" % exc)

    ci_path = root / ".github" / "ci-image-lock.json"
    try:
        ci_data = json.loads(read_text(ci_path))
    except ValueError as exc:
        raise ToolchainError("invalid CI image lock: %s" % exc)

    if ci_data.get("schema") != "protos-ci-image-lock-v1":
        raise ToolchainError("unsupported CI image lock schema")

    try:
        ci_image = str(ci_data["image"])
    except (KeyError, TypeError, ValueError) as exc:
        raise ToolchainError("incomplete CI image lock: %s" % exc)

    if graal.get("distribution") != "graalvm-community":
        raise ToolchainError("primary runtime distribution must be graalvm-community")
    if bytecode < 25:
        raise ToolchainError(
            "PLAT073 requires Java 25 or newer source/bytecode baseline"
        )
    if bytecode > feature:
        raise ToolchainError("bytecode target cannot exceed selected JDK feature")
    if not jdk_version.startswith(str(feature) + "."):
        raise ToolchainError("jdk_version does not match jdk_feature")
    if graal_release != components:
        raise ToolchainError("GraalVM release and Graal/Truffle component version must match")
    if channel not in image or jdk_version not in image:
        raise ToolchainError("container image does not encode selected GraalVM channel/JDK")
    selected_expected_prefix = (
        "ghcr.io/graalvm/native-image-community:"
        + channel
        + "-"
        + jdk_version
        + "-ol10@sha256:"
    )
    if (
        not image.startswith(selected_expected_prefix)
        or re.fullmatch(
            r"[0-9a-f]{64}",
            image[len(selected_expected_prefix):],
        ) is None
    ):
        raise ToolchainError(
            "unified devcontainer requires an immutable Native Image OL10 digest"
        )
    if "version" in maven_contract:
        raise ToolchainError("legacy exact Maven version field is not supported")
    maven_minimum_tuple = stable_maven_version_tuple(
        maven_minimum,
        "Maven minimum_version",
    )
    if maven_supported_major != maven_minimum_tuple[0]:
        raise ToolchainError("Maven minimum_version must use supported_major")
    if maven_supported_major != 3:
        raise ToolchainError("current Protos Maven contract supports major 3 only")
    if not re.fullmatch(
        r"ghcr\.io/guillermomolina/protos-ci@sha256:[0-9a-f]{64}",
        ci_image,
    ):
        raise ToolchainError("CI image must be an exact protos-ci digest")
    if policy.get("primary_runtime_alignment") != "development-ci-distribution":
        raise ToolchainError("unexpected primary runtime alignment policy")
    if policy.get("upgrade_mode") != "explicit-validated-change":
        raise ToolchainError("runtime upgrades must be explicit validated changes")
    if policy.get("floating_primary_runtime") is not False:
        raise ToolchainError("primary runtime must not float")

    data["ci"] = ci_data
    return data


def xml_property(path, property_name):
    # type: (Path, str) -> str
    tree = ET.parse(str(path))
    root = tree.getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    value = root.findtext("m:properties/m:%s" % property_name, namespaces=ns)
    return (value or "").strip()


def direct_graal_runtime_roots(path):
    # type: (Path) -> List[str]
    tree = ET.parse(str(path))
    root = tree.getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    result = []
    dependencies = root.find("m:dependencies", ns)
    if dependencies is None:
        return result
    for dependency in dependencies.findall("m:dependency", ns):
        group = (dependency.findtext("m:groupId", namespaces=ns) or "").strip()
        artifact = (dependency.findtext("m:artifactId", namespaces=ns) or "").strip()
        scope = (dependency.findtext("m:scope", namespaces=ns) or "compile").strip()
        if group.startswith("org.graalvm.") and scope != "test":
            result.append("%s:%s" % (group, artifact))
    return sorted(result)


def dependency_plugin_projection(path):
    # type: (Path) -> Tuple[str, List[str]]
    tree = ET.parse(str(path))
    root = tree.getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    plugins = root.find("m:build/m:plugins", ns)
    if plugins is None:
        return "<missing>", []
    for plugin in plugins.findall("m:plugin", ns):
        artifact = (plugin.findtext("m:artifactId", namespaces=ns) or "").strip()
        if artifact != "maven-dependency-plugin":
            continue
        version = (plugin.findtext("m:version", namespaces=ns) or "").strip()
        for execution in plugin.findall("m:executions/m:execution", ns):
            execution_id = (
                execution.findtext("m:id", namespaces=ns) or ""
            ).strip()
            if execution_id != "materialize-canonical-graal-runtime-plane":
                continue
            roots = []
            for graph_root in execution.findall(
                "m:configuration/m:graphRoots/m:graphRoot", ns
            ):
                group = (
                    graph_root.findtext("m:groupId", namespaces=ns) or ""
                ).strip()
                artifact_id = (
                    graph_root.findtext("m:artifactId", namespaces=ns) or ""
                ).strip()
                roots.append("%s:%s" % (group, artifact_id))
            return version, sorted(roots)
        return version, []
    return "<missing>", []


def first_match(text, pattern, label):
    # type: (str, str, str) -> str
    match = re.search(pattern, text, flags=re.MULTILINE)
    if not match:
        return "<missing:%s>" % label
    return match.group(1).strip()


def workflow_java(path):
    # type: (Path) -> Tuple[str, str]
    text = read_text(path)
    distribution = first_match(
        text,
        r"^[ \t]*distribution:[ \t]*[\"']?([^\"'\n#]+)",
        "distribution",
    )
    version = first_match(
        text,
        r"^[ \t]*java-version:[ \t]*[\"']?([^\"'\n#]+)",
        "java-version",
    )
    return distribution, version


def workflow_scalar(path, key):
    # type: (Path, str) -> str
    text = read_text(path)
    return first_match(
        text,
        r"^[ \t]*%s:[ \t]*[\"']?([^\"'\n#]+)" % re.escape(key),
        key,
    )


def workflow_container_image(path):
    # type: (Path) -> str
    text = read_text(path)
    return first_match(
        text,
        r"^[ \t]*image:[ \t]*[\"']?([^\"'\n#]+)",
        "container image",
    )


def workflow_job_text(path, job_id):
    # type: (Path, str) -> str
    text = read_text(path)
    lines = text.splitlines()
    target = "  %s:" % job_id
    start = None
    for index, line in enumerate(lines):
        if line == target:
            start = index + 1
            break
    if start is None:
        return ""

    body = []
    for line in lines[start:]:
        if re.match(r"^  [A-Za-z0-9_-]+:[ \t]*(?:#.*)?$", line):
            break
        body.append(line)
    if not body:
        return ""
    return "\n".join(body) + "\n"


def workflow_job_scalar(path, job_id, key):
    # type: (Path, str, str) -> str
    text = workflow_job_text(path, job_id)
    return first_match(
        text,
        r"^[ \t]*%s:[ \t]*[\"']?([^\"'\n#]+)" % re.escape(key),
        "%s.%s" % (job_id, key),
    )


def workflow_job_container_image(path, job_id):
    # type: (Path, str) -> str
    text = workflow_job_text(path, job_id)
    images = re.findall(r"(?m)^      image: ([^\n#]+)$", text)
    return images[0].strip() if len(images) == 1 else "<drift:%s.container>" % job_id


CI_MAVEN_JOBS = ("prepare", "compilerability", "java-parallel", "java-serial",
                 "protos-plan", "protos-suite", "protos-foreign", "native-image")
CI_COMMANDS = {
    "prepare": ["prepare-java-test-shards", "prepare-protos-tests"],
    "compilerability": ["check"],
    "java-parallel": ["test-java-parallel"],
    "java-serial": ["test-java-serial"],
    "protos-plan": ["prepare-protos-tests"],
    "protos-suite": ["prepare-protos-tests", "test-protos-suite"],
    "protos-foreign": ["prepare-protos-tests", "test-protos-foreign"],
    "native-image": ["test-native"],
}


PROTOS_SUITE_MAKE = ('make test-protos-suite PROTOS_TEST_SHARD_COUNT="$PROTOS_TEST_SHARD_COUNT" '
                     'PROTOS_TEST_SHARD_INDEX="$PROTOS_TEST_SHARD_INDEX" PROTOS_TEST_JOBS="$PROTOS_TEST_JOBS" '
                     '2>&1 | tee target/ci-logs/protos-suite.log')
PROTOS_COMPLETE_LISTING = "bin/protos test --list-cases > target/protos-test-evidence/complete-listing.json"
PROTOS_SHARD_LISTING = ('bin/protos test --list-cases --shard-count "$PROTOS_TEST_SHARD_COUNT" '
                        '--shard-run "$PROTOS_TEST_SHARD_INDEX" > target/protos-test-evidence/shard-listing.json')
PROTOS_EXIT_TRAP = "trap 'printf \"%s\\n\" \"$?\" > target/ci-logs/protos-suite.exit' EXIT"


def step_with(blocks, token):
    found = [index for index, block in enumerate(blocks) if token in block]
    return found[0] if len(found) == 1 else None


def scoped_workflow(path):
    text = read_text(path)
    return any(token in text for token in ("protos_matrix:", "matrix.scope", "--requirements"))


def scope_selection_contract(path):
    """Validate wiring, leaving membership and matrix construction to the shared selector."""
    prepare = workflow_job_text(path, "prepare")
    blocks = workflow_step_blocks(prepare)
    selector = step_with(blocks, "scripts/validation_impact.py")
    upload = step_with(blocks, "name: validation-requirements-${{ github.sha }}")
    if selector is None or upload is None or selector >= upload:
        return False
    if not all(token in prepare for token in (
            "protos_matrix: ${{ steps.impact.outputs.matrix }}",
            "protos_planning_matrix: ${{ steps.impact.outputs.planning_matrix }}")):
        return False
    if not all(token in blocks[selector] for token in (
            "id: impact", "github.event.pull_request.base.sha || github.event.before",
            "github.event.pull_request.head.sha || github.sha",
            '--base "$IMPACT_BASE" --head "$IMPACT_HEAD"',
            '--shard-count "$PROTOS_TEST_SHARD_COUNT"',
            "--ci-output target/validation-requirements/requirements.json", '--exhaustive',
            '>> "$GITHUB_OUTPUT"')):
        return False
    commands = [line.strip() for line in blocks[selector].splitlines()
                if "python3 scripts/validation_impact.py" in line]
    if len(commands) != 1 or re.search(r"\|\||;|&&|set \+", commands[0]):
        return False
    if not all(token in blocks[upload] for token in (
            "uses: actions/upload-artifact@v4", "if-no-files-found: error",
            "path: target/validation-requirements/requirements.json")):
        return False
    if any(re.search(r"(?m)^        if:", blocks[index]) for index in (selector, upload)):
        return False
    for name, output in (("protos-plan", "protos_planning_matrix"), ("protos-suite", "protos_matrix")):
        body = workflow_job_text(path, name)
        if not all(token in body for token in (
                "    needs: prepare\n", "      fail-fast: false\n",
                "        include: ${{ fromJSON(needs.prepare.outputs." + output + ") }}",
                "      PROTOS_REPOSITORY_TEST_SCOPE: ${{ matrix.scope }}\n")):
            return False
        if len(re.findall(r"(?m)^    needs:", body)) != 1:
            return False
        if len(re.findall(r"(?m)^        include:", body)) != 1:
            return False
    return True


def protos_evidence_contract(path):
    # type: (Path) -> bool
    # The plan publishes the complete authoritative listing; each shard records
    # its own complete and I178 listings, real exit status and summary, and
    # receives no artifacts, so no compiled directory crosses runners.
    plan = workflow_job_text(path, "protos-plan")
    blocks = workflow_step_blocks(plan)
    scoped = scoped_workflow(path)
    valid = (scope_selection_contract(path) if scoped else "    needs:" not in plan)
    valid = valid and "actions/download-artifact" not in plan
    listing = step_with(blocks, "bin/protos test --list-cases > target/protos-test-shards/listing.json")
    planner = step_with(blocks, "tools/ci_protos_test_shards.py plan")
    plan_identity = ("name: protos-plan-${{ matrix.scopeIndex }}-${{ github.sha }}" if scoped
                     else "name: protos-plan-${{ github.sha }}")
    upload = step_with(blocks, plan_identity)
    valid = valid and None not in (listing, planner, upload) and listing < planner < upload
    if valid:
        preparation = "mkdir -p target/protos-test-shards target/ci-logs"
        command = "bin/protos test --list-cases > target/protos-test-shards/listing.json"
        valid = preparation in blocks[listing]
        valid = valid and blocks[listing].index(preparation) < blocks[listing].index(command)
        valid = valid and all(token in blocks[planner] for token in (
            '--count "$PROTOS_TEST_SHARD_COUNT"', '--jobs "$PROTOS_TEST_JOBS"',
            "--listing target/protos-test-shards/listing.json",
            "--output target/protos-test-shards/plan.json"))
        valid = valid and all(token in blocks[upload] for token in (
            "uses: actions/upload-artifact@v4", "if-no-files-found: error",
            "target/protos-test-shards/plan.json", "target/protos-test-shards/listing.json"))
        valid = valid and all("if:" not in blocks[i] for i in (listing, planner, upload))
        if scoped:
            valid = valid and '--scope "$PROTOS_REPOSITORY_TEST_SCOPE"' in blocks[planner]

    suite = workflow_job_text(path, "protos-suite")
    blocks = workflow_step_blocks(suite)
    valid = valid and (scoped or "    needs:" not in suite) and "actions/download-artifact" not in suite
    listings = step_with(blocks, PROTOS_SHARD_LISTING)
    run = step_with(blocks, PROTOS_SUITE_MAKE)
    report = step_with(blocks, "tools/ci_protos_test_shards.py report")
    evidence_identity = ("name: protos-evidence-suite-${{ matrix.scopeIndex }}-${{ matrix.shard }}-${{ github.sha }}"
                         if scoped else "name: protos-evidence-suite-${{ matrix.shard }}-${{ github.sha }}")
    evidence = step_with(blocks, evidence_identity)
    order = [listings, run, report, evidence]
    valid = valid and None not in order and order == sorted(order)
    if valid:
        valid = PROTOS_COMPLETE_LISTING in blocks[listings] and "if:" not in blocks[listings]
        valid = valid and PROTOS_EXIT_TRAP in blocks[run] and "if:" not in blocks[run]
        valid = valid and "        if: always()\n" in blocks[report] and all(token in blocks[report] for token in (
            "--listing target/protos-test-evidence/complete-listing.json",
            "--shard-listing target/protos-test-evidence/shard-listing.json",
            "--exit-status-file target/ci-logs/protos-suite.exit", "--log target/ci-logs/protos-suite.log",
            '--count "$PROTOS_TEST_SHARD_COUNT"', '--index "$PROTOS_TEST_SHARD_INDEX"',
            '--jobs "$PROTOS_TEST_JOBS"', "--output target/protos-test-evidence/protos-result.json"))
        if scoped:
            valid = valid and '--scope "$PROTOS_REPOSITORY_TEST_SCOPE"' in blocks[report]
        valid = valid and "        if: always()\n" in blocks[evidence]
        valid = valid and all(token in blocks[evidence] for token in (
            "uses: actions/upload-artifact@v4", "target/protos-test-evidence/",
            "target/ci-logs/protos-suite.log", "target/ci-logs/protos-suite.exit"))
    for body in (plan, suite):
        for block in workflow_step_blocks(body):
            if "ci_protos_test_shards.py" in block or "bin/protos test --list-cases" in block:
                valid = valid and not re.search(r"\|\||;|&&|set \+", block)
    return valid


def aggregate_evidence_contract(path):
    blocks = workflow_step_blocks(workflow_job_text(path, "aggregate"))
    gate = step_with(blocks, "tools/ci_validation_aggregate.py")
    if gate is None or "        if: always()\n" not in blocks[gate]:
        return False
    if re.search(r"\|\||;|&&|set \+", blocks[gate]):
        return False
    scoped = scoped_workflow(path)
    identities = [
        ("pattern: protos-plan-[0-9]*-${{ github.sha }}" if scoped else "name: protos-plan-${{ github.sha }}",
         "target/protos-test-shards"),
        ("pattern: protos-evidence-*-${{ github.sha }}", "target/ci-protos-evidence")]
    if scoped:
        identities.append(("name: validation-requirements-${{ github.sha }}", "target/validation-requirements"))
    for identity, destination in identities:
        download = step_with(blocks, identity)
        if download is None or download >= gate:
            return False
        block = blocks[download]
        if not all(token in block for token in (
                "uses: actions/download-artifact@v4", "        if: always()\n", "path: " + destination)):
            return False
        if "merge-multiple: true" in block:
            return False
    plan_argument = "--protos-plan target/protos-test-shards" + ("" if scoped else "/plan.json")
    valid = all(token in blocks[gate] for token in (
        plan_argument, "--protos-results target/ci-protos-evidence"))
    if scoped:
        valid = valid and "--requirements target/validation-requirements/requirements.json" in blocks[gate]
        valid = valid and not re.search(r"--protos-plan target/protos-test-shards/", blocks[gate])
    return valid


def workflow_job_test_command(path, job_id):
    # Inspect the complete distributed contract, including automatic entry and
    # acceptance wiring. Substring presence alone cannot establish ownership.
    text = read_text(path)
    jobs = text.split("\njobs:\n", 1)[-1]
    ids = re.findall(r"(?m)^  ([A-Za-z0-9_-]+):$", jobs)
    expected = set(CI_MAVEN_JOBS) | {"aggregate"}
    valid = len(ids) == len(expected) and set(ids) == expected
    valid = valid and "\n  push:\n" in text and "\n  pull_request:\n" in text
    valid = valid and "pull_request_target" not in text
    valid = valid and continue_on_error_confined(text)
    valid = valid and "hashFiles(" not in text and not re.search(r"actions/cache@", text)
    valid = valid and "devcontainers/ci@" not in text
    shells = re.findall(r"(?m)^\s*shell: (.+)$", text)
    valid = valid and shells == ["bash"]
    valid = valid and not re.search(r"(?m)^\s*set \+", text)
    # bash's default Actions invocation retains -e and pipefail; reject
    # explicit suppression in validation command lines.
    for name, targets in CI_COMMANDS.items():
        body = workflow_job_text(path, name)
        commands = re.findall(r"(?m)^\s*(?:-\s+)?(?:run:\s*)?make\s+([a-zA-Z0-9_-]+)([^\n]*)$", body)
        valid = valid and [target for target, _ in commands] == targets
        valid = valid and all(not re.search(r"\|\||;|&&|continue-on-error|set \+", tail)
                              for _, tail in commands)
    aggregate = workflow_job_text(path, "aggregate")
    needs = re.search(r"(?m)^    needs: \[([^\]]+)\]$", aggregate)
    valid = valid and bool(needs) and [v.strip() for v in needs.group(1).split(",")] == list(CI_MAVEN_JOBS)
    valid = valid and "    if: always()" in aggregate
    for token in ("tools/ci_validation_aggregate.py", "--plan", "--results", "--dependencies",
                  "${{ toJSON(needs) }}", "actions/download-artifact@v4", "java-plan-${{ github.sha }}",
                  "java-evidence-*-${{ github.sha }}",
                  "protos-plan-[0-9]*-${{ github.sha }}" if scoped_workflow(path) else "protos-plan-${{ github.sha }}",
                  "protos-evidence-*-${{ github.sha }}", "--protos-plan", "--protos-results",
                  '--protos-shard-count "$PROTOS_TEST_SHARD_COUNT"'):
        valid = valid and token in aggregate
    valid = valid and protos_evidence_contract(path) and aggregate_evidence_contract(path)
    for name in ("java-parallel", "java-serial"):
        body = workflow_job_text(path, name)
        valid = valid and "    needs: prepare" in body
        valid = valid and "actions/download-artifact@v4" in body
        valid = valid and "java-plan-${{ github.sha }}" in body
    return "distributed validation gates" if valid else "<drift:distributed.command>"


def workflow_job_test_workers(path, job_id):
    text = read_text(path)
    top = text.split("\njobs:\n", 1)[0]
    bounded = all(re.search(r'^  %s: "%s"$' % (name, value), top, re.MULTILINE)
                  for name, value in (("JAVA_TEST_SHARD_COUNT", "4"), ("JAVA_TEST_JOBS", "2"),
                                      ("PROTOS_TEST_JOBS", "2"), ("PROTOS_TEST_SHARD_COUNT", "4")))
    # Reject job-local worker or Protos count overrides that bypass the bounded defaults.
    bounded = bounded and not re.search(
        r"(?m)^ {6,}(JAVA_TEST_JOBS|PROTOS_TEST_JOBS|PROTOS_TEST_SHARD_COUNT):", text)
    # The Protos matrix enumerates exactly every index of the declared count,
    # never cancels siblings, and passes each control to the Makefile explicitly.
    suite = workflow_job_text(path, "protos-suite")
    if scoped_workflow(path):
        bounded = bounded and scope_selection_contract(path)
        bounded = bounded and "      PROTOS_TEST_SHARD_INDEX: ${{ matrix.shard }}\n" in suite
        bounded = bounded and PROTOS_SUITE_MAKE in suite
        bounded = bounded and not re.search(r"(?m)^\s+shard: ", suite)
    else:
        for token in ("    strategy:\n      fail-fast: false\n      matrix:\n        shard: [1, 2, 3, 4]\n",
                      "    env:\n      PROTOS_TEST_SHARD_INDEX: ${{ matrix.shard }}\n", PROTOS_SUITE_MAKE):
            bounded = bounded and token in suite
        bounded = bounded and len(re.findall(r"(?m)^\s+shard: ", suite)) == 1
    parallel = workflow_job_text(path, "java-parallel")
    prepare = workflow_job_text(path, "prepare")
    for token in ("fail-fast: false", "shard: ${{ fromJSON(needs.prepare.outputs.indices) }}",
                  "JAVA_TEST_SHARD_COUNT: ${{ needs.prepare.outputs.count }}",
                  "JAVA_TEST_SHARD_INDEX: ${{ matrix.shard }}"):
        bounded = bounded and token in parallel
    for token in ("count: ${{ steps.plan.outputs.count }}", "indices: ${{ steps.plan.outputs.indices }}",
                  "tools/ci_java_test_shards.py outputs", '>> "$GITHUB_OUTPUT"'):
        bounded = bounded and token in prepare
    return CI_WORKERS_EXPECTED if bounded else "<drift:distributed.workers>"


CI_WORKERS_EXPECTED = "Java 4 shards; Protos 4 shards; Java=2 Protos=2"
MAVEN_CACHE_ACTION = "./.github/actions/maven-cache-restore"
MAVEN_CACHE_SAVE_JOB = "prepare"
MAVEN_CACHE_EXPECTED = "shared restore; computed key; explicit prepare save"
MAVEN_CACHE_KEY_REF = "${{ steps.maven-cache.outputs.cache-key }}"


def workflow_step_blocks(text):
    # Split a job body into its step blocks (six-space "- " list items).
    return re.split(r"(?m)^      - ", text)[1:]


def continue_on_error_confined(text):
    # Failure tolerance is permitted only on explicit Maven cache save steps;
    # job-level and validation/aggregation tolerance stay forbidden.
    steps = []  # type: List[List[str]]
    for line in text.splitlines():
        if line.startswith("      - "):
            steps.append([line])
        elif line.startswith("      ") and steps:
            steps[-1].append(line)
        else:
            if "continue-on-error" in line:
                return False
            steps.append([])
    for step in steps:
        body = "\n".join(step)
        tolerances = re.findall(r"(?m)^\s*-?\s*continue-on-error:\s*(.*?)\s*$", body)
        if not tolerances:
            continue
        if tolerances != ["true"]:
            return False
        if not re.search(r"(?m)^\s*uses: actions/cache/save@v6\s*$", body):
            return False
        if re.search(r"(?m)^\s*-?\s*run:", body):
            return False
    return True


def maven_cache_action_contract(root):
    # type: (Path) -> bool
    path = root / ".github" / "actions" / "maven-cache-restore" / "action.yml"
    if not path.is_file():
        return False
    text = path.read_text(encoding="utf-8")
    valid = bool(re.search(r"(?m)^  using: composite\s*$", text))
    valid = valid and "hashFiles(" not in text and not re.search(r"actions/cache@", text)
    restores = re.findall(r"(?m)^\s*uses: (actions/cache/restore@\S+)\s*$", text)
    valid = valid and restores == ["actions/cache/restore@v6"]
    valid = valid and "actions/cache/save@" not in text
    valid = valid and bool(re.search(r"(?m)^\s*path: /root/\.m2/repository\s*$", text))
    valid = valid and bool(re.search(
        r"(?m)^\s*key: " + re.escape("${{ steps.key.outputs.key }}") + r"\s*$", text))
    valid = valid and bool(re.search(
        r"(?m)^\s*restore-keys: \|\n\s*" + re.escape("${{ runner.os }}-maven-") + r"\s*$", text))
    for token in ("import hashlib", "hashlib.sha256", "ls-files", "missing root pom.xml",
                  "value: ${{ steps.key.outputs.key }}", "value: ${{ steps.restore.outputs.cache-hit }}"):
        valid = valid and token in text
    valid = valid and bool(re.search(r"(?m)^  cache-key:\s*$", text))
    valid = valid and bool(re.search(r"(?m)^  cache-hit:\s*$", text))
    key_step = text.find("id: key")
    restore_step = text.find("uses: actions/cache/restore@")
    valid = valid and 0 <= key_step < restore_step
    return valid


def workflow_job_maven_cache_contract(path, job_id):
    text = workflow_job_text(path, job_id)
    blocks = workflow_step_blocks(text)
    valid = "hashFiles(" not in text and not re.search(r"actions/cache@", text)
    valid = valid and "actions/cache/restore@" not in text
    restores = [i for i, block in enumerate(blocks)
                if re.search(r"(?m)^\s*uses: " + re.escape(MAVEN_CACHE_ACTION) + r"\s*$", block)]
    valid = valid and len(restores) == 1
    if valid:
        restore = blocks[restores[0]]
        valid = bool(re.search(r"(?m)^\s*id: maven-cache\s*$", restore))
        # The key is computed by the restore action before any validation.
        makes = [i for i, block in enumerate(blocks) if re.search(r"(?m)\bmake\s+[a-zA-Z0-9_-]+", block)]
        valid = valid and all(restores[0] < i for i in makes)
        saves = [i for i, block in enumerate(blocks) if "actions/cache/save@" in block]
        if job_id == MAVEN_CACHE_SAVE_JOB:
            valid = valid and len(saves) == 1
            if valid:
                save = blocks[saves[0]]
                valid = bool(re.search(r"(?m)^\s*uses: actions/cache/save@v6\s*$", save))
                valid = valid and bool(re.search(r"(?m)^\s*path: /root/\.m2/repository\s*$", save))
                keys = re.findall(r"(?m)^\s*key: (.+?)\s*$", save)
                valid = valid and keys == [MAVEN_CACHE_KEY_REF]
                valid = valid and bool(re.search(
                    r"(?m)^\s*if: success\(\) && steps\.maven-cache\.outputs\.cache-hit != 'true'\s*$", save))
                valid = valid and bool(re.search(r"(?m)^\s*continue-on-error: true\s*$", save))
                valid = valid and all(i < saves[0] for i in makes)
        else:
            valid = valid and not saves
    return MAVEN_CACHE_EXPECTED if valid else "<drift:%s.maven_cache>" % job_id


DIAGNOSTIC_LANES = ("cache", "prepare", "compilerability", "java-shard", "java-serial",
                    "protos-suite", "protos-foreign", "native-image", "http-root-failure")
DIAGNOSTIC_EXPECTED = "isolated manual/ci-validation diagnostics"
DIAGNOSTIC_BRANCHES = ("ci-validation", "ci-validation-http", "ci-validation-java-2")
DIAGNOSTIC_PUSH_SELECTIONS = {
    "ci-validation": {"LANE": "cache", "SHARD": "1", "REPETITIONS": "1"},
    "ci-validation-http": {"LANE": "http-root-failure", "SHARD": "1", "REPETITIONS": "20"},
    "ci-validation-java-2": {"LANE": "java-shard", "SHARD": "2", "REPETITIONS": "1"},
}
DIAGNOSTIC_INPUT_KEYS = {"description", "type", "default", "options"}
DIAGNOSTIC_ROUNDTRIP_PATH = "target/ci-cache-roundtrip"
DIAGNOSTIC_ROUNDTRIP_KEY = "${{ steps.cache-roundtrip-key.outputs.key }}"
DIAGNOSTIC_SELECTION_CASES = (
    'case "$LANE" in',
    "            " + "|".join(DIAGNOSTIC_LANES) + ") ;;",
    'case "$SHARD" in',
    "            1|2|3|4) ;;",
    'case "$REPETITIONS" in',
    "            1|5|10|20) ;;",
)


def line_indent(line):
    # type: (str) -> int
    return len(line) - len(line.lstrip(" "))


def yaml_block(lines, key, indent):
    # Lines strictly inside the mapping entry `key:` at exactly `indent`.
    # The block ends at the first content line indented at or above the
    # header, so later sibling sections are never consumed.
    if lines is None:
        return None
    header = " " * indent + key + ":"
    for index, line in enumerate(lines):
        if line.rstrip() != header:
            continue
        body = []
        for following in lines[index + 1:]:
            stripped = following.strip()
            if stripped and not stripped.startswith("#") and line_indent(following) <= indent:
                break
            body.append(following)
        return body
    return None


def yaml_content(lines):
    # type: (List[str]) -> List[str]
    return [line for line in lines if line.strip() and not line.strip().startswith("#")]


def yaml_keys(lines, indent):
    # Direct child keys at exactly `indent`, in order.
    if lines is None:
        return None
    return [m.group(1) for m in (re.match(r"^ {%d}([A-Za-z0-9_-]+):" % indent, line) for line in lines)
            if m and line_indent(m.group(0)) == indent]


def yaml_scalars(lines, indent):
    # A flat mapping block: every content line must be `key: value` at indent.
    if lines is None:
        return None
    result = {}  # type: Dict[str, str]
    for line in yaml_content(lines):
        match = re.match(r"^ {%d}([A-Za-z0-9_-]+): (.+?)\s*$" % indent, line)
        if not match or line_indent(line) != indent:
            return None
        result[match.group(1)] = match.group(2)
    return result


def yaml_list(lines, indent):
    # A flat sequence block: every content line must be `- item` at indent.
    if lines is None:
        return None
    items = []
    for line in yaml_content(lines):
        match = re.match(r"^ {%d}- (.+?)\s*$" % indent, line)
        if not match or line_indent(line) != indent:
            return None
        items.append(match.group(1).strip('"').strip("'"))
    return items


def workflow_push_branches(text):
    # type: (str) -> object
    push = yaml_block(yaml_block(text.splitlines(), "on", 0), "push", 2)
    if push is None or yaml_keys(push, 4) != ["branches"]:
        return None
    return yaml_list(yaml_block(push, "branches", 4), 6)


def workflow_permissions(text):
    # type: (str) -> object
    return yaml_scalars(yaml_block(text.splitlines(), "permissions", 0), 2)


def workflow_dispatch_inputs(text):
    # type: (str) -> object
    dispatch = yaml_block(yaml_block(text.splitlines(), "on", 0), "workflow_dispatch", 2)
    return yaml_block(dispatch, "inputs", 4)


def workflow_choice_options(text, name):
    # type: (str, str) -> List[str]
    options = yaml_list(yaml_block(yaml_block(workflow_dispatch_inputs(text), name, 6), "options", 8), 10)
    return options or []


def diagnostic_selection(text, event_name, ref_name):
    # Evaluate the fixed LANE/SHARD/REPETITIONS selection expressions for an
    # event. Only `cond && 'literal' || ...` chains over event_name/ref_name
    # and the three declared inputs are understood; anything else is None.
    env = yaml_scalars(yaml_block(text.splitlines(), "env", 0), 2)
    if env is None:
        return None
    selection = {}  # type: Dict[str, str]
    for name in ("LANE", "SHARD", "REPETITIONS"):
        match = re.match(r"^\$\{\{ (.+) \}\}$", env.get(name, ""))
        if not match:
            return None
        chosen = None
        for term in match.group(1).split(" || "):
            parts = term.split(" && ")
            holds = True
            for condition in parts[:-1]:
                cond = re.match(r"^github\.(event_name|ref_name) == '([^']+)'$", condition)
                if not cond:
                    return None
                actual = event_name if cond.group(1) == "event_name" else ref_name
                holds = holds and actual == cond.group(2)
            value = parts[-1]
            literal = re.match(r"^'([^']*)'$", value)
            source = re.match(r"^inputs\.(lane|shard|repetitions)$", value)
            if not literal and not source:
                return None
            if holds and chosen is None:
                chosen = literal.group(1) if literal else "<input:%s>" % source.group(1)
        if chosen is None:
            return None
        selection[name] = chosen
    return selection


def workflow_steps(lines):
    # Split a steps block into step texts (six-space "- " list items).
    steps = []  # type: List[str]
    for line in lines or []:
        if line.startswith("      - "):
            steps.append(line + "\n")
        elif steps:
            steps[-1] += line + "\n"
    return steps


def step_field(step, key):
    # type: (str, str) -> List[str]
    return re.findall(r"(?m)^ {6,10}(?:- )?%s: (.+?)\s*$" % re.escape(key), step)


def diagnostics_workflow_contract(root, expected_image):
    # type: (Path, str) -> str
    path = root / ".github" / "workflows" / "ci-diagnostics.yml"
    if not path.is_file():
        return "<missing:ci-diagnostics.yml>"
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    jobs = yaml_block(lines, "jobs", 0) or []
    job = yaml_block(jobs, "diagnose", 2)
    steps = workflow_steps(yaml_block(job, "steps", 4))
    env = yaml_scalars(yaml_block(lines, "env", 0), 2) or {}
    checks = []  # type: List[Tuple[str, bool]]

    on = yaml_block(lines, "on", 0)
    checks.append(("triggers", bool(re.search(r"(?m)^name: CI Diagnostics\s*$", text))
                   and yaml_keys(on, 2) == ["push", "workflow_dispatch"]
                   and "pull_request" not in text))
    checks.append(("branches", workflow_push_branches(text) == list(DIAGNOSTIC_BRANCHES)))

    inputs = workflow_dispatch_inputs(text)
    declared = yaml_keys(inputs, 6) == ["lane", "shard", "repetitions"]
    for name in ("lane", "shard", "repetitions"):
        block = yaml_block(inputs, name, 6)
        declared = declared and block is not None and set(yaml_keys(block, 8)) <= DIAGNOSTIC_INPUT_KEYS
        declared = declared and any(line.strip() == "type: choice" and line_indent(line) == 8
                                    for line in block or [])
    checks.append(("inputs.declaration", declared))
    checks.append(("inputs.choices",
                   workflow_choice_options(text, "lane") == list(DIAGNOSTIC_LANES)
                   and workflow_choice_options(text, "shard") == ["1", "2", "3", "4"]
                   and workflow_choice_options(text, "repetitions") == ["1", "5", "10", "20"]))

    mapping = all(diagnostic_selection(text, "push", branch) == expected
                  for branch, expected in DIAGNOSTIC_PUSH_SELECTIONS.items())
    for other in ("main", "ci-validation-other", "feature"):
        selected = diagnostic_selection(text, "push", other)
        mapping = mapping and selected is not None and selected["LANE"] == "invalid"
    mapping = mapping and diagnostic_selection(text, "workflow_dispatch", "main") == {
        "LANE": "<input:lane>", "SHARD": "<input:shard>", "REPETITIONS": "<input:repetitions>"}
    checks.append(("push_lanes", mapping))

    first = steps[0] if steps else ""
    validation = "- name: Validate diagnostic selection\n" in first
    validation = validation and all(case in first for case in DIAGNOSTIC_SELECTION_CASES)
    validation = validation and first.count("; false ;;") == 3 and "uses:" not in first
    checks.append(("input_validation", validation))

    images = re.findall(r"(?m)^[ \t]+image: ([^\n#]+)$", text)
    checks.append(("image", bool(images) and all(image.strip() == expected_image for image in images)))
    checks.append(("permissions", workflow_permissions(text) == {"contents": "read", "packages": "read"}
                   and not re.search(r"(?m)^[ \t]+permissions:", text) and ": write" not in text))
    checks.append(("runner", re.findall(r"(?m)^[ \t]+runs-on: (.+?)[ \t]*$", text) == ["ubuntu-latest"]))
    checks.append(("workers", all(env.get(name) == '"%s"' % value for name, value in
                                  (("JAVA_TEST_SHARD_COUNT", "4"), ("JAVA_TEST_JOBS", "2"),
                                   ("PROTOS_TEST_JOBS", "2")))
                   and not re.search(r"(?m)^ {6,}(JAVA_TEST_JOBS|PROTOS_TEST_JOBS):", text)))
    checks.append(("concurrency", yaml_scalars(yaml_block(lines, "concurrency", 0), 2) == {
        "group": "ci-diagnostics-${{ github.ref }}", "cancel-in-progress": "true"}))

    def find(predicate):
        found = [i for i, step in enumerate(steps) if predicate(step)]
        return found[0] if len(found) == 1 else None

    def uses(step, action):
        return step_field(step, "uses") == [action]

    restore = find(lambda s: uses(s, MAVEN_CACHE_ACTION))
    maven_save = find(lambda s: uses(s, "actions/cache/save@v6")
                      and step_field(s, "path") == ["/root/.m2/repository"])
    builds = [i for i, s in enumerate(steps) if re.search(r"\b(?:make|mvn)\s+\S", s)]
    lifecycle = restore is not None and step_field(steps[restore], "id") == ["maven-cache"]
    lifecycle = lifecycle and all(restore < i for i in builds)
    lifecycle = lifecycle and maven_save is not None
    lifecycle = lifecycle and step_field(steps[maven_save], "key") == ["diagnostic-" + MAVEN_CACHE_KEY_REF]
    lifecycle = lifecycle and "hashFiles(" not in text and not re.search(r"actions/cache@", text)
    lifecycle = lifecycle and all(step_field(s, "path") in (["/root/.m2/repository"], [DIAGNOSTIC_ROUNDTRIP_PATH])
                                  for s in steps if re.search(r"actions/cache/(?:save|restore)@", s))
    checks.append(("cache_lifecycle", lifecycle))

    marker = find(lambda s: step_field(s, "id") == ["cache-roundtrip-key"])
    rt_save = find(lambda s: uses(s, "actions/cache/save@v6")
                   and step_field(s, "path") == [DIAGNOSTIC_ROUNDTRIP_PATH])
    removal = find(lambda s: "rm -rf " + DIAGNOSTIC_ROUNDTRIP_PATH in s)
    rt_restore = find(lambda s: uses(s, "actions/cache/restore@v6")
                      and step_field(s, "path") == [DIAGNOSTIC_ROUNDTRIP_PATH])
    verify = find(lambda s: "- name: Verify cache round-trip marker\n" in s)
    order = [marker, rt_save, removal, rt_restore, verify]
    roundtrip = None not in order and order == sorted(order) and maven_save is not None and maven_save < marker
    if roundtrip:
        roundtrip = all(token in steps[marker] for token in (
            "MAVEN_CACHE_KEY: " + MAVEN_CACHE_KEY_REF,
            'key="protos-ci-diagnostic-roundtrip-${MAVEN_CACHE_KEY}-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"',
            DIAGNOSTIC_ROUNDTRIP_PATH + "/marker.txt"))
        roundtrip = roundtrip and step_field(steps[rt_save], "key") == [DIAGNOSTIC_ROUNDTRIP_KEY]
        roundtrip = roundtrip and step_field(steps[rt_restore], "key") == [DIAGNOSTIC_ROUNDTRIP_KEY]
        roundtrip = roundtrip and step_field(steps[rt_restore], "fail-on-cache-miss") == ["true"]
        roundtrip = roundtrip and "restore-keys" not in steps[rt_restore]
        roundtrip = roundtrip and step_field(steps[rt_restore], "id") == ["cache-roundtrip-restore"]
        roundtrip = roundtrip and all(token in steps[verify] for token in (
            "ROUNDTRIP_HIT: ${{ steps.cache-roundtrip-restore.outputs.cache-hit }}",
            '[ "$ROUNDTRIP_HIT" = "true" ]',
            '[ "$(cat %s/marker.txt)" = "$EXPECTED_MARKER" ]' % DIAGNOSTIC_ROUNDTRIP_PATH))
    checks.append(("cache_roundtrip", bool(roundtrip)))

    compile_step = find(lambda s: "mvn -B test-compile" in s)
    http = find(lambda s: "surefire:test@default-test" in s)
    http_valid = compile_step is not None and http is not None and compile_step < http
    if http_valid:
        http_valid = all(token in steps[http] for token in (
            "HTTP_TEST_CLASS: ProtosHttpHostFactoryIntegrationTest",
            "HTTP_TEST_METHOD: rootActorFailureTerminatesTheProcessAndRetiresEveryWorker",
            '"-Dtest=$HTTP_TEST_CLASS#$HTTP_TEST_METHOD"',
            "-Dsurefire.rerunFailingTestsCount=0",
            '"-Dprotos.surefire.reports=$reports/run-$run"',
            "status=97",
            'first_failure=$run',
            '[ "$failures" -eq 0 ]'))
        http_valid = http_valid and steps[http].count("surefire:test@") == 1
    checks.append(("http_execution", bool(http_valid)))
    checks.append(("java_shard", find(lambda s: (
        'make test-java-parallel JAVA_TEST_SHARD_COUNT=4 JAVA_TEST_SHARD_INDEX="$SHARD" JAVA_TEST_JOBS=2' in s
        and "if: env.LANE == 'java-shard'" in s)) is not None))

    isolation = yaml_keys(jobs, 2) == ["diagnose"]
    isolation = isolation and not re.search(r"(?<![A-Za-z0-9_])ci_validation_aggregate\.py", text)
    for token in ("aggregate-", "java-evidence-", "java-plan-${{", "protos-evidence-",
                  "protos-plan-${{", "ci_protos_test_shards",
                  "releases", "git push", "git commit", "gh release", "contents: write"):
        isolation = isolation and token not in text
    isolation = isolation and not re.search(r"\$\{\{\s*inputs\.", "\n".join(jobs))
    checks.append(("isolation", isolation))

    checks.append(("failure_suppression", "continue-on-error" not in text
                   and not re.search(r"\|\|\s*(?:true|:)\s*$|set \+[eo]", text, flags=re.MULTILINE)
                   and yaml_scalars(yaml_block(yaml_block(lines, "defaults", 0), "run", 2), 4)
                   == {"shell": "bash"}))

    uploads = [s for s in steps if uses(s, "actions/upload-artifact@v4")]
    names = [n for s in uploads for n in step_field(s, "name") if not s.startswith("      - name: " + n)]
    checks.append(("artifacts", bool(uploads) and len(names) == len(uploads)
                   and all(n.startswith("ci-diagnostics-${{ env.LANE }}") and n.endswith("-${{ github.sha }}")
                           for n in names)))

    failed = [name for name, ok in checks if not ok]
    return DIAGNOSTIC_EXPECTED if not failed else "<drift:ci.diagnostics:%s>" % ",".join(failed)


def audit_bindings(root, contract):
    # type: (Path, Dict[str, object]) -> List[Tuple[str, str, str]]
    graal = contract["graalvm"]
    bytecode = str(contract["java"]["bytecode_release"])
    components = str(contract["graal_components"]["version"])
    feature = str(graal["jdk_feature"])
    jdk_version = str(graal["jdk_version"])

    rows = []  # type: List[Tuple[str, str, str]]

    pom = root / "pom.xml"
    rows.append(("pom.bytecode", bytecode, xml_property(pom, "maven.compiler.release")))
    rows.append(("pom.graal_components", components, xml_property(pom, "graalvm.version")))
    pom_text = read_text(pom)
    rows.append(("pom.shade_multi_release", "present", "present" if "<Multi-Release>true</Multi-Release>" in pom_text else "missing"))
    rows.append(("pom.shade_services", "present", "present" if "org.apache.maven.plugins.shade.resource.ServicesResourceTransformer" in pom_text else "missing"))
    shade_signature_filter = all(token in pom_text for token in ("<exclude>META-INF/*.SF</exclude>", "<exclude>META-INF/*.DSA</exclude>", "<exclude>META-INF/*.RSA</exclude>"))
    rows.append(("pom.shade_signature_filter", "present", "present" if shade_signature_filter else "missing"))

    canonical_roots = direct_graal_runtime_roots(pom)
    dependency_plugin_version, projected_roots = dependency_plugin_projection(pom)
    rows.append((
        "pom.runtime_plane.graph_roots",
        ",".join(canonical_roots),
        ",".join(projected_roots),
    ))
    rows.append((
        "pom.runtime_plane.dependency_plugin",
        "3.11.0",
        dependency_plugin_version,
    ))
    rows.append((
        "pom.runtime_plane.shade_externalization",
        "present",
        "present" if "<exclude>org.graalvm.*:*</exclude>" in pom_text else "missing",
    ))

    docker = read_text(root / ".devcontainer" / "Dockerfile")
    rows.append((
        "devcontainer.image",
        str(graal["container_image"]),
        first_match(docker, r"^FROM[ \t]+([^ \t\n]+)", "FROM"),
    ))
    docker_maven_provisioning, docker_maven_repository_scope = docker_maven_bindings(
        docker
    )
    rows.append((
        "devcontainer.maven_provisioning",
        MAVEN_PROVISIONING_MODEL,
        docker_maven_provisioning,
    ))
    rows.append((
        "devcontainer.maven_repository_scope",
        MAVEN_REPOSITORY_SCOPE,
        docker_maven_repository_scope,
    ))

    docker_java_path = (
        "JAVA_HOME-first"
        if 'ENV PATH="${JAVA_HOME}/bin:${PATH}"' in docker
        else "missing"
    )
    rows.append(("devcontainer.java_path", "JAVA_HOME-first", docker_java_path))

    ci_workflow = root / ".github" / "workflows" / "tests.yml"
    images = [workflow_job_container_image(ci_workflow, name) for name in CI_MAVEN_JOBS]
    expected_image = str(contract["ci"]["image"])
    rows.append(("ci.tests.container", expected_image,
                 expected_image if all(image == expected_image for image in images) else " | ".join(images)))
    rows.append(("ci.tests.command", "distributed validation gates",
                 workflow_job_test_command(ci_workflow, "aggregate")))
    rows.append(("ci.tests.workers", CI_WORKERS_EXPECTED,
                 workflow_job_test_workers(ci_workflow, "java-parallel")))
    caches = [workflow_job_maven_cache_contract(ci_workflow, name) for name in CI_MAVEN_JOBS]
    if not maven_cache_action_contract(root):
        caches.append("<drift:maven-cache-restore.action>")
    rows.append(("ci.tests.maven_cache", MAVEN_CACHE_EXPECTED,
                 MAVEN_CACHE_EXPECTED if all(cache == MAVEN_CACHE_EXPECTED for cache in caches)
                 else " | ".join(cache for cache in caches if cache != MAVEN_CACHE_EXPECTED)))
    rows.append(("ci.diagnostics.contract", DIAGNOSTIC_EXPECTED,
                 diagnostics_workflow_contract(root, expected_image)))
    rows.append((
        "dist.runtime_pom_absent",
        "absent",
        "absent" if not (root / "dist" / "runtime-pom.xml").exists() else "present",
    ))

    builder = read_text(root / "dist" / "build_portable.py")
    builder_projection = (
        "present"
        if 'source_runtime_dir = root / "target" / "runtime"' in builder
        and "runtime-pom.xml" not in builder
        and '"runtime_authority=pom.xml"' in builder
        else "missing"
    )
    rows.append((
        "dist.builder.canonical_runtime_projection",
        "present",
        builder_projection,
    ))
    rows.append((
        "dist.builder.java_feature",
        feature,
        first_match(builder, r'^SUPPORTED_JAVA_FEATURE[ \t]*=[ \t]*[\"\']([^\"\']+)', "SUPPORTED_JAVA_FEATURE"),
    ))
    rows.append((
        "dist.builder.java_version",
        jdk_version,
        first_match(builder, r'^SUPPORTED_JAVA_VERSION[ \t]*=[ \t]*[\"\']([^\"\']+)', "SUPPORTED_JAVA_VERSION"),
    ))
    rows.append((
        "dist.builder.graalvm_release",
        str(graal["release"]),
        first_match(builder, r'^SUPPORTED_GRAALVM_RELEASE[ \t]*=[ \t]*[\"\']([^\"\']+)', "SUPPORTED_GRAALVM_RELEASE"),
    ))
    rows.append((
        "dist.builder.graal_components",
        components,
        first_match(builder, r'^EXPECTED_TRUFFLE_VERSION[ \t]*=[ \t]*[\"\']([^\"\']+)', "EXPECTED_TRUFFLE_VERSION"),
    ))

    smoke = read_text(root / "dist" / "smoke" / "smoke_optimizing_runtime.sh")
    rows.append((
        "dist.smoke.java_bytecode",
        bytecode,
        first_match(
            smoke,
            r"^EXPECTED_BYTECODE_RELEASE=([0-9]+)",
            "EXPECTED_BYTECODE_RELEASE",
        ),
    ))
    rows.append((
        "dist.smoke.java_feature",
        feature,
        first_match(smoke, r"^EXPECTED_FEATURE=([^ \t\n]+)", "EXPECTED_FEATURE"),
    ))
    rows.append((
        "dist.smoke.java_version",
        jdk_version,
        first_match(smoke, r"^EXPECTED_JAVA_VERSION=([^ \t\n]+)", "EXPECTED_JAVA_VERSION"),
    ))
    rows.append((
        "dist.smoke.graal_components",
        components,
        first_match(smoke, r"^EXPECTED_TRUFFLE_VERSION=([^ \t\n]+)", "EXPECTED_TRUFFLE_VERSION"),
    ))

    launcher = read_text(root / "bin" / "protos")
    launcher_version_gate = (
        "present"
        if "s/^java_version=//p" in launcher
        and '[ "$actual_version" = "$expected_version" ] || supported=0' in launcher
        else "missing"
    )
    rows.append(("dist.launcher.java_version_gate", "present", launcher_version_gate))
    return rows


def print_contract(contract):
    graal = contract["graalvm"]
    print("TOOLCHAIN_SCHEMA: %s" % contract["schema"])
    print("JAVA_BYTECODE_RELEASE: %s" % contract["java"]["bytecode_release"])
    print("PRIMARY_GRAALVM_RELEASE: %s" % graal["release"])
    print("PRIMARY_JDK_FEATURE: %s" % graal["jdk_feature"])
    print("PRIMARY_JDK_VERSION: %s" % graal["jdk_version"])
    print("PRIMARY_CONTAINER_IMAGE: %s" % graal["container_image"])
    print("GRAAL_COMPONENTS_VERSION: %s" % contract["graal_components"]["version"])
    print("MAVEN_MINIMUM_VERSION: %s" % contract["maven"]["minimum_version"])
    print("MAVEN_SUPPORTED_MAJOR: %s" % contract["maven"]["supported_major"])
    print("MAVEN_EXACT_VERSION_REQUIRED: NO")
    print("MAVEN_RUNTIME_VALIDATION_MODE: runtime")
    print("CI_IMAGE: %s" % contract["ci"]["image"])
    print("TOOLCHAIN_CONTRACT: PASS")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Verify the repository-owned Protos toolchain contract and static bindings.")
    parser.add_argument("--root", default=None, help="repository root; defaults to parent of tools/")
    parser.add_argument(
        "--mode",
        choices=("contract", "report", "check", "runtime"),
        default="check",
    )
    parser.add_argument("--scope", choices=("all", "development"), default="all")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parents[1]
    try:
        contract = load_contract(root)
        print_contract(contract)
        if args.mode == "contract":
            return 0

        if args.mode == "runtime":
            rows = audit_runtime_environment(contract)
        else:
            rows = audit_bindings(
                root,
                contract,
            )
            if args.scope == "development":
                rows = [row for row in rows if row[0] in DEVELOPMENT_BINDINGS]
    except (ToolchainError, ET.ParseError, OSError) as exc:
        print("TOOLCHAIN_ERROR: %s" % exc, file=sys.stderr)
        return 2

    drift = []
    for name, expected, actual in rows:
        status = "PASS" if expected == actual else "DRIFT"
        print("TOOLCHAIN_BINDING: %s status=%s expected=%s actual=%s" % (name, status, expected, actual))
        if status == "DRIFT":
            drift.append(name)

    if drift:
        print("TOOLCHAIN_DRIFT_COUNT: %d" % len(drift))
        print("TOOLCHAIN_DRIFT: %s" % ",".join(drift))
        if args.mode == "check":
            return 1
    else:
        print("TOOLCHAIN_DRIFT_COUNT: 0")
        print("TOOLCHAIN_BINDINGS: PASS")
        if args.mode == "runtime":
            print("MAVEN_RUNTIME_VALIDATION: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
