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
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Tuple


class ToolchainError(Exception):
    pass


DEVELOPMENT_BINDINGS = {
    "pom.bytecode",
    "devcontainer.image",
    "devcontainer.maven",
    "ci.tests.image",
    "ci.tests.java_feature",
    "ci.tests.java_version",
    "ci.tests.maven",
}


def read_text(path):
    # type: (Path) -> str
    if not path.is_file():
        raise ToolchainError("required file is missing: %s" % path)
    return path.read_text(encoding="utf-8")


def load_contract(root):
    # type: (Path) -> Dict[str, object]
    path = root / "toolchain.json"
    try:
        data = json.loads(read_text(path))
    except ValueError as exc:
        raise ToolchainError("invalid toolchain.json: %s" % exc)

    if data.get("schema") != "protos-toolchain-v1":
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
        maven = str(data["maven"]["version"])
        policy = data["policy"]
    except (KeyError, TypeError, ValueError) as exc:
        raise ToolchainError("incomplete toolchain contract: %s" % exc)

    if graal.get("distribution") != "graalvm-community":
        raise ToolchainError("primary runtime distribution must be graalvm-community")
    if bytecode > feature:
        raise ToolchainError("bytecode target cannot exceed selected JDK feature")
    if not jdk_version.startswith(str(feature) + "."):
        raise ToolchainError("jdk_version does not match jdk_feature")
    if graal_release != components:
        raise ToolchainError("GraalVM release and Graal/Truffle component version must match")
    if channel not in image or jdk_version not in image:
        raise ToolchainError("container image does not encode selected GraalVM channel/JDK")
    if not re.match(r"^[0-9]+\.[0-9]+\.[0-9]+$", maven):
        raise ToolchainError("Maven version must be an exact x.y.z coordinate")
    if policy.get("primary_runtime_alignment") != "development-ci-distribution":
        raise ToolchainError("unexpected primary runtime alignment policy")
    if policy.get("upgrade_mode") != "explicit-validated-change":
        raise ToolchainError("runtime upgrades must be explicit validated changes")
    if policy.get("floating_primary_runtime") is not False:
        raise ToolchainError("primary runtime must not float")

    return data


def xml_property(path, property_name):
    # type: (Path, str) -> str
    tree = ET.parse(str(path))
    root = tree.getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    value = root.findtext("m:properties/m:%s" % property_name, namespaces=ns)
    return (value or "").strip()


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


def audit_bindings(root, contract):
    # type: (Path, Dict[str, object]) -> List[Tuple[str, str, str]]
    graal = contract["graalvm"]
    bytecode = str(contract["java"]["bytecode_release"])
    components = str(contract["graal_components"]["version"])
    maven = str(contract["maven"]["version"])
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

    docker = read_text(root / ".devcontainer" / "Dockerfile")
    rows.append((
        "devcontainer.image",
        str(graal["container_image"]),
        first_match(docker, r"^FROM[ \t]+([^ \t\n]+)", "FROM"),
    ))
    rows.append((
        "devcontainer.maven",
        maven,
        first_match(docker, r"^ARG[ \t]+MAVEN_VERSION=([^ \t\n]+)", "MAVEN_VERSION"),
    ))

    tests_workflow = root / ".github" / "workflows" / "tests.yml"
    rows.append(("ci.tests.image", str(graal["container_image"]), workflow_container_image(tests_workflow)))
    rows.append(("ci.tests.java_feature", feature, workflow_scalar(tests_workflow, "PROTOS_PRIMARY_JDK_FEATURE")))
    rows.append(("ci.tests.java_version", jdk_version, workflow_scalar(tests_workflow, "PROTOS_PRIMARY_JDK_VERSION")))
    rows.append(("ci.tests.maven", maven, workflow_scalar(tests_workflow, "PROTOS_MAVEN_VERSION")))

    distribution_workflow = root / ".github" / "workflows" / "distribution.yml"
    rows.append(("ci.distribution.image", str(graal["container_image"]), workflow_container_image(distribution_workflow)))
    rows.append(("ci.distribution.java_feature", feature, workflow_scalar(distribution_workflow, "PROTOS_PRIMARY_JDK_FEATURE")))
    rows.append(("ci.distribution.java_version", jdk_version, workflow_scalar(distribution_workflow, "PROTOS_PRIMARY_JDK_VERSION")))
    rows.append(("ci.distribution.maven", maven, workflow_scalar(distribution_workflow, "PROTOS_MAVEN_VERSION")))

    rows.append((
        "dist.runtime_pom.graal_components",
        components,
        xml_property(root / "dist" / "runtime-pom.xml", "graalvm.version"),
    ))

    builder = read_text(root / "dist" / "build_portable.py")
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

    smoke = read_text(root / "dist" / "smoke_optimizing_runtime.sh")
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
    print("GRAAL_COMPONENTS_VERSION: %s" % contract["graal_components"]["version"])
    print("MAVEN_VERSION: %s" % contract["maven"]["version"])
    print("TOOLCHAIN_CONTRACT: PASS")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Verify the repository-owned Protos toolchain contract and static bindings.")
    parser.add_argument("--root", default=None, help="repository root; defaults to parent of tools/")
    parser.add_argument("--mode", choices=("contract", "report", "check"), default="check")
    parser.add_argument("--scope", choices=("all", "development"), default="all")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parents[1]
    try:
        contract = load_contract(root)
        print_contract(contract)
        if args.mode == "contract":
            return 0

        rows = audit_bindings(root, contract)
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
