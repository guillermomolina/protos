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

"""Fail-closed validation-impact selection for Protos publication deltas."""

from __future__ import print_function

import argparse
import fnmatch
import json
import os
import subprocess
import sys


PACKAGE_TEST_SET = (
    "ProtosPackage*Test,"
    "ProtosExternalPackage*Test,"
    "ProtosWorkspace*Test,"
    "ProtosTestToolPackage*Test,"
    "ProtosCliTest"
)

TEST_TOOL_TEST_SET = (
    "ProtosTestTool*Test,"
    "ProtosCliTest"
)

NEUTRAL_PREFIXES = ("docs/",)
NEUTRAL_EXACT = frozenset(("CHANGELOG.md",))

PACKAGE_PREFIXES = (
    "protos/tools/package/",
    "protos/tests/package-tool/",
)

TEST_TOOL_PREFIXES = ("protos/tools/test/",)

PACKAGE_TEST_GLOBS = (
    "src/test/java/com/guillermomolina/protos/execution/tools/package/ProtosPackage*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/integration/packages/ProtosPackage*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/integration/modules/ProtosPackage*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/tools/test/execution/ProtosTestToolPackage*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/packages/ProtosPackage*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/packages/ProtosExternalPackage*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/workspace/ProtosWorkspace*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/ProtosPackage*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/ProtosExternalPackage*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/ProtosWorkspace*Test.java",
    "src/test/java/com/guillermomolina/protos/cli/ProtosWorkspace*Test.java",
)

TEST_TOOL_TEST_GLOBS = (
    "src/test/java/com/guillermomolina/protos/execution/tools/test/*/ProtosTestTool*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/ProtosTestTool*Test.java",
    "src/test/java/com/guillermomolina/protos/cli/ProtosTestTool*Test.java",
)

# Production Java that is exclusively owned by the bundled Test Tool. Keep
# ProtosCli.java itself outside this list: it is shared CLI surface and receives
# a narrower method-body check in classify_delta().
TEST_TOOL_SOURCE_GLOBS = (
    "src/main/java/com/guillermomolina/protos/execution/tools/test/*/ProtosTestTool*.java",
    "src/main/java/com/guillermomolina/protos/cli/ProtosTestCorpusRegistry.java",
    "src/main/java/com/guillermomolina/protos/cli/ProtosTestExecutionRequirementRegistry.java",
    "src/main/java/com/guillermomolina/protos/cli/ProtosTestTool*.java",
    "src/main/java/com/guillermomolina/protos/execution/ProtosTestTool*.java",
)

# AUD007: gitignored roots that Maven still reads from the checkout. Git does
# not report them as untracked, so candidate-evidence binding must list them
# explicitly.
IGNORED_OBSERVABLE_PREFIXES = (".mvn/",)

PROTOS_CLI_PATH = "src/main/java/com/guillermomolina/protos/cli/ProtosCli.java"
PROTOS_CLI_TEST_TOOL_METHOD = "    private int runBundledTestTool("


# Host-side impact routing only. Actual test membership remains owned by Protos.
LIBRARY_OWNERS = (
    "auth/jwt", "cli", "collections", "crypto/hmac_sha256", "crypto/password",
    "crypto/sha256", "csv", "datetime", "ffi", "http", "inspect", "json",
    "logging", "markdown", "math/integer", "math/linear-algebra",
    "network/ip-addresses", "network/ip-endpoints", "network/resolvers",
    "regex", "runtime", "semver", "test", "text", "text/base64url", "tls", "toml", "uri",
)
# Conservative reverse dependencies. Shared foundational libraries have unknown
# closure and require all libraries; no import parser is used here.
LIBRARY_DEPENDENTS = {
    "crypto/sha256": ("crypto/hmac_sha256", "crypto/password", "auth/jwt"),
    "crypto/hmac_sha256": ("auth/jwt",),
    "text/base64url": ("auth/jwt",),
    "network/ip-addresses": ("network/ip-endpoints", "network/resolvers", "http", "tls"),
    "network/ip-endpoints": ("network/resolvers", "http", "tls"),
    "network/resolvers": ("http", "tls"),
    "tls": ("http",), "uri": ("http",),
    "json": ("toml", "logging", "auth/jwt"),
    "datetime": ("toml", "logging", "auth/jwt", "http"),
}
SHARED_LIBRARIES = frozenset(("collections", "text", "test", "runtime", "inspect"))
CORE_PREFIXES = tuple("src/main/java/com/guillermomolina/protos/" + part + "/"
                      for part in ("analysis", "lexer", "parser", "runtime", "semantic", "source", "execution/bytecode", "execution/runtime")) + (
    "protos/lib/core/", "protos/tests/conformance/",
)


class Selection(object):
    def __init__(self, impact, test_set, reason, skip_allowed, libraries=None):
        self.impact, self.test_set, self.reason = impact, test_set, reason
        self.skip_allowed = bool(skip_allowed)
        self.libraries = ("all" if impact == "FULL" else "none") if libraries is None else libraries

    def to_dict(self):
        return {
            "validation_impact": self.impact,
            "affected_test_set": self.test_set,
            "full_test_suite": "SKIP_ALLOWED" if self.skip_allowed else "REQUIRED",
            "library_validation": self.libraries,
            "reason": self.reason,
        }


def validate_requirements(data):
    """Validate the shared selector contract before executing or accepting work."""
    required = {"validation_impact", "affected_test_set", "full_test_suite", "library_validation", "reason"}
    if not isinstance(data, dict) or not required <= set(data):
        raise ValueError("missing validation requirement fields")
    for key in required - {"library_validation"}:
        if not isinstance(data[key], str) or not data[key]:
            raise ValueError("invalid validation requirement: " + key)
    profile, libraries = data["validation_impact"], data["library_validation"]
    selected = isinstance(libraries, list) and bool(libraries)
    if selected:
        if (not all(isinstance(owner, str) and owner in LIBRARY_OWNERS for owner in libraries)
                or libraries != sorted(set(libraries))):
            raise ValueError("invalid selected library owners")
    elif libraries not in ("none", "all"):
        raise ValueError("invalid library validation requirement")
    expected = {"FULL": ("ALL", "REQUIRED"), "CORE": ("ALL", "SKIP_ALLOWED"),
                "SELECTIVE": ("ALL", "SKIP_ALLOWED"),
                "TOOL_LOCAL:PACKAGE": (PACKAGE_TEST_SET, "SKIP_ALLOWED")}
    if profile not in expected or (data["affected_test_set"], data["full_test_suite"]) != expected[profile]:
        raise ValueError("contradictory validation profile")
    if ((profile == "FULL" and libraries != "all")
            or (profile == "SELECTIVE" and not selected)
            or (profile in ("CORE", "TOOL_LOCAL:PACKAGE") and libraries != "none")):
        raise ValueError("contradictory profile and library scope")
    return data


def required_scopes(data):
    validate_requirements(data)
    libraries = data["library_validation"]
    return ["core"] + (["libraries"] if libraries == "all" else
                       ["library:" + owner for owner in libraries] if isinstance(libraries, list) else [])


def library_owner(path):
    for prefix in ("protos/lib/", "protos/tests/library/", "protos/tests/conformance/library/"):
        if path.startswith(prefix):
            relative = path[len(prefix):]
            if prefix == "protos/lib/":
                aliases = {"math/linear_algebra/": "math/linear-algebra/",
                           "network/ip_addresses/": "network/ip-addresses/",
                           "network/ip_endpoints/": "network/ip-endpoints/"}
                for source, canonical in aliases.items():
                    if relative.startswith(source):
                        relative = canonical + relative[len(source):]
                        break
            matches = [owner for owner in LIBRARY_OWNERS
                       if relative == owner or relative.startswith(owner + "/")]
            if matches:
                return max(matches, key=len)
    return None


def library_closure(owners):
    result = set(owners)
    pending = list(result)
    while pending:
        for dependent in LIBRARY_DEPENDENTS.get(pending.pop(), ()):
            if dependent not in result:
                result.add(dependent)
                pending.append(dependent)
    return sorted(result)


def _has_prefix(path, prefixes):
    return any(path.startswith(prefix) for prefix in prefixes)


def _matches(path, patterns):
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def _kind(path):
    if path in NEUTRAL_EXACT or _has_prefix(path, NEUTRAL_PREFIXES):
        return "NEUTRAL"

    if _has_prefix(path, PACKAGE_PREFIXES) or _matches(path, PACKAGE_TEST_GLOBS):
        return "PACKAGE"

    if _has_prefix(path, TEST_TOOL_PREFIXES):
        return "TEST"

    if path.startswith("protos/tests/tooling/"):
        base = path.rsplit("/", 1)[-1]
        if base.startswith("tool002-") and base.endswith(".protos"):
            return "TEST"
        return "FULL"

    if _matches(path, TEST_TOOL_TEST_GLOBS):
        return "TEST"

    if _matches(path, TEST_TOOL_SOURCE_GLOBS):
        return "TEST"

    owner = library_owner(path)
    if owner is not None:
        return "LIBRARY:" + owner
    if path.startswith("protos/tests/conformance/library/"):
        return "FULL"
    if _has_prefix(path, CORE_PREFIXES):
        return "CORE"
    return "FULL"


def _normalize_path(raw):
    # Strip only a literal "./" prefix: dot-directories such as ".mvn/" keep
    # their leading dot so they cannot alias a different root.
    path = raw.replace("\\", "/")
    while path.startswith("./"):
        path = path[2:]
    return path.lstrip("/")


def path_is_validation_observable(path):
    """True when a checkout path can influence executable validation.

    This is the same taxonomy that classifies commit deltas: only NEUTRAL
    paths are unobservable, so every unknown root fails closed.
    """
    normalized = _normalize_path(path)
    return bool(normalized) and _kind(normalized) != "NEUTRAL"


def observable_paths(paths):
    """Return the given paths that validation can observe, in input order."""
    return [path for path in paths if path_is_validation_observable(path)]


def classify_paths(paths, top_level_closure=False, kind_overrides=None):
    normalized = []
    for raw in paths:
        if raw is None:
            continue
        path = _normalize_path(raw)
        if path and path not in normalized:
            normalized.append(path)

    if not normalized:
        return Selection(
            "FULL", "ALL",
            "empty definitive delta requires complete validation",
            False,
        )

    overrides = {} if kind_overrides is None else dict(kind_overrides)
    kinds = [overrides.get(path, _kind(path)) for path in normalized]
    active = set(kinds) - {"NEUTRAL"}
    if "FULL" in active or "TEST" in active:
        return Selection("FULL", "ALL", "unknown, shared or test-selection impact requires exhaustive validation", False)
    owners = {kind[len("LIBRARY:"):] for kind in active if kind.startswith("LIBRARY:")}
    if owners & SHARED_LIBRARIES:
        return Selection("FULL", "ALL", "shared library dependency closure requires all libraries", False)
    if "PACKAGE" in active and len(active) > 1:
        return Selection("FULL", "ALL", "cross-boundary Tool impact requires exhaustive validation", False)
    if owners:
        return Selection("SELECTIVE", "ALL", "ordinary validation plus conservative library dependency closure",
                         True, library_closure(owners))
    if active == {"PACKAGE"} and not top_level_closure:
        return Selection("TOOL_LOCAL:PACKAGE", PACKAGE_TEST_SET, "bounded Package Tool intermediate validation", True)
    return Selection("CORE", "ALL", "complete ordinary affected coverage", True)


def _java_method_span(source, signature):
    # Return [start,end) for one Java method, failing closed on ambiguity.
    start = source.find(signature)
    if start < 0 or source.find(signature, start + 1) >= 0:
        return None

    brace = source.find("{", start + len(signature))
    if brace < 0:
        return None

    depth = 0
    index = brace
    state = "code"
    while index < len(source):
        char = source[index]
        nxt = source[index + 1] if index + 1 < len(source) else ""

        if state == "code":
            if char == '"':
                state = "string"
            elif char == "'":
                state = "char"
            elif char == "/" and nxt == "/":
                state = "line-comment"
                index += 1
            elif char == "/" and nxt == "*":
                state = "block-comment"
                index += 1
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return (start, index + 1)
                if depth < 0:
                    return None
        elif state == "string":
            if char == "\\":
                index += 1
            elif char == '"':
                state = "code"
        elif state == "char":
            if char == "\\":
                index += 1
            elif char == "'":
                state = "code"
        elif state == "line-comment":
            if char == "\n":
                state = "code"
        elif state == "block-comment":
            if char == "*" and nxt == "/":
                state = "code"
                index += 1

        index += 1

    return None


def _without_java_method(source, signature):
    span = _java_method_span(source, signature)
    if span is None:
        return None
    start, end = span
    return source[:start] + "<TEST_TOOL_METHOD_BODY>\n" + source[end:]


def _git_text(repo, ref, path):
    try:
        data = subprocess.check_output([
            "git", "-C", repo, "show", ref + ":" + path
        ])
    except subprocess.CalledProcessError:
        return None
    return data.decode("utf-8", "strict")


def protos_cli_change_is_test_tool_only(repo, base, head):
    # True only when ProtosCli.java changed inside runBundledTestTool().
    before = _git_text(repo, base, PROTOS_CLI_PATH)
    after = _git_text(repo, head, PROTOS_CLI_PATH)
    if before is None or after is None or before == after:
        return False

    before_outer = _without_java_method(before, PROTOS_CLI_TEST_TOOL_METHOD)
    after_outer = _without_java_method(after, PROTOS_CLI_TEST_TOOL_METHOD)
    return (
        before_outer is not None
        and after_outer is not None
        and before_outer == after_outer
    )


def classify_delta(repo, base, head, top_level_closure=False):
    paths = changed_paths(repo, base, head)
    overrides = {}
    normalized = [_normalize_path(p) for p in paths]
    if PROTOS_CLI_PATH in normalized:
        if protos_cli_change_is_test_tool_only(repo, base, head):
            overrides[PROTOS_CLI_PATH] = "TEST"

    return classify_paths(
        paths,
        top_level_closure=top_level_closure,
        kind_overrides=overrides,
    )


def parse_name_status_z(data):
    """Parse `git diff --name-status -z`, retaining both paths for R/C."""
    fields = data.split(b"\0")
    if fields and fields[-1] == b"":
        fields.pop()

    paths = []
    index = 0
    while index < len(fields):
        status = fields[index].decode("utf-8", "strict")
        index += 1
        if not status:
            raise ValueError("empty git diff status")

        code = status[0]
        if code in ("R", "C"):
            if index + 1 >= len(fields):
                raise ValueError("truncated rename/copy record")
            paths.append(fields[index].decode("utf-8", "strict"))
            paths.append(fields[index + 1].decode("utf-8", "strict"))
            index += 2
        else:
            if index >= len(fields):
                raise ValueError("truncated git diff record")
            paths.append(fields[index].decode("utf-8", "strict"))
            index += 1
    return paths


def changed_paths(repo, base, head):
    data = subprocess.check_output([
        "git", "-C", repo, "diff", "--name-status", "-z",
        "--find-renames", "--find-copies", base, head, "--",
    ])
    return parse_name_status_z(data)


def render_env(selection):
    data = selection.to_dict()
    return "\n".join((
        "VALIDATION_IMPACT=" + data["validation_impact"],
        "AFFECTED_TEST_SET=" + data["affected_test_set"],
        "FULL_TEST_SUITE=" + data["full_test_suite"],
        "VALIDATION_REASON=" + data["reason"],
        "LIBRARY_VALIDATION=" + json.dumps(data["library_validation"], separators=(",", ":")),
    ))


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--base")
    parser.add_argument("--head")
    parser.add_argument("--exhaustive", action="store_true")
    parser.add_argument("--ci-output", help="write revision-bound CI requirements and matrices")
    parser.add_argument("--shard-count", type=int, default=4)
    parser.add_argument("--top-level-closure", action="store_true")
    parser.add_argument("--format", choices=("env", "json"), default="env")
    args = parser.parse_args(argv)

    if args.exhaustive:
        selection = Selection("FULL", "ALL", "explicit exhaustive validation", False)
    else:
        try:
            if not args.base or not args.head or args.base == "0" * 40:
                raise ValueError("unavailable revision range")
            selection = classify_delta(os.path.abspath(args.repo), args.base, args.head,
                                       top_level_closure=args.top_level_closure)
        except (ValueError, UnicodeError, subprocess.CalledProcessError):
            selection = Selection("FULL", "ALL", "unavailable or malformed revision range", False)
    if args.ci_output:
        if args.shard_count < 1:
            parser.error("shard count must be positive")
        candidate = os.environ.get("GITHUB_SHA", args.head or "")
        if len(candidate) != 40 or any(c not in "0123456789abcdef" for c in candidate):
            parser.error("CI requires an exact candidate revision")
        document = dict(selection.to_dict(), revision=candidate, shardCount=args.shard_count)
        scopes = required_scopes(document)
        document["scopes"] = scopes
        with open(args.ci_output, "w") as stream:
            json.dump(document, stream, sort_keys=True)
        print("scopes=" + json.dumps(scopes, separators=(",", ":")))
        print("planning_matrix=" + json.dumps([dict(scope=scope, scopeIndex=i)
              for i, scope in enumerate(scopes)], separators=(",", ":")))
        print("matrix=" + json.dumps([dict(scope=scope, scopeIndex=i, shard=n)
              for i, scope in enumerate(scopes) for n in range(1, args.shard_count + 1)], separators=(",", ":")))
        return 0

    if args.format == "json":
        print(json.dumps(selection.to_dict(), sort_keys=True))
    else:
        print(render_env(selection))
    return 0


if __name__ == "__main__":
    sys.exit(main())
