# TEST001-H Closure — Portable-distribution TOOL002 execution

Status: **CLOSED on publication**

Nature: non-normative distribution/integration validation evidence

Live coordination: GitHub `TEST001-H / #466`

Parent: GitHub `TEST001 / #449`

## Purpose

TEST001-H proves that the portable POSIX/JVM distribution executes the bundled
Test Tool through the same public `protos test` entry point used by repository
validation, without depending on checkout-only Java/JUnit corpus wrappers.

This slice does not change Test Tool semantics, suite membership, scheduling,
resource policy, language semantics, Standard Library behavior, Java/runtime
implementation, release policy, or implementation version.

## Authoritative selection path

TOOL005 closed with the repository-owned Protos corpus selected by the explicit
D122 suite graph. The bundled Test Tool entry module loads `RepositorySuite` and
flattens its current explicit leaves before planning or scheduling cases.

TEST001-H does not copy that suite membership into distribution validation.
Instead, the portable smoke invokes the extracted artifact's own:

```text
bin/protos test
```

with no alternate corpus selector. Therefore the packaged Test Tool itself
remains the authority for the current official suite.

## Portable boundary

The existing portable builder copies the complete `protos/tools` and
`protos/tests` trees into the artifact. The Test Tool CLI derives its test roots
from the installed distribution root, not from the caller's checkout directory.

`dist/smoke_test_tool.sh` then:

1. extracts the ZIP into a disposable toolchain outside the repository;
2. creates a separate working directory outside both the checkout and extracted
   toolchain;
3. invokes only the extracted `bin/protos test`;
4. requires successful process exit;
5. requires the normal bundled Test Tool bootstrap/argument markers;
6. requires an aggregate `N passed, 0 failed` completion summary with `N > 0`;
7. retains the pre-existing supported/fallback runtime-isolation checks.

The aggregate assertion deliberately does **not** hard-code the current case
count. The official suite may grow while this acceptance proof remains valid.

## Outcome propagation

The portable command is executed as an external process and the smoke fails on
any non-zero command exit. A successful TEST001-H proof therefore demonstrates
that the extracted CLI consumed the Test Tool's successful aggregate outcome and
returned success to its caller. Existing host-side Test Tool cutover tests remain
responsible for the detailed `0` / `1` / `3` runtime outcome mapping; H does not
duplicate those implementation tests inside the portable artifact.

## Publication validation

The governed publication launcher for this slice requires:

```text
sh -n dist/smoke_test_tool.sh
git diff --check
mvn -DskipTests package
python3 dist/build_portable.py --allow-dirty --skip-project-build
sh dist/smoke_test_tool.sh <newly-built-portable-archive>
```

Publication is permitted only when the smoke reports:

```text
DIST_B4A_OUTSIDE_CHECKOUT_CHECK: PASS
DIST_BUNDLED_TEST_TOOL_CHECK: PASS
TEST001_H_PORTABLE_REPOSITORY_SUITE_CHECK: PASS passed=<non-zero> failed=0
DISTRIBUTION_TEST_TOOL_VALIDATED: YES
DIST001_B4A_SMOKE: PASS
```

## Closure signals

```text
TEST001_H_STATUS=CLOSED
PORTABLE_ARTIFACT_EXECUTES_BUNDLED_TEST_TOOL=YES
PORTABLE_TEST_INVOCATION_OUTSIDE_CHECKOUT=YES
OFFICIAL_SUITE_SELECTION_REUSED=YES
CHECKOUT_JUNIT_CORPUS_WRAPPER_REQUIRED=NO
TOOL002_SUCCESS_OUTCOME_PROPAGATED=YES
PORTABLE_SELECTED_CASES=NONZERO
PORTABLE_FAILED_CASES=0
DISTRIBUTION_TEST_TOOL_VALIDATED=YES
SPECIFICATION_CHANGED=NO
PROTOS_IMPLEMENTATION_CHANGED=NO
IMPLEMENTATION_VERSION_CHANGED=NO
```

## TEST001 follow-up

The live TEST001 scope correction on GitHub is authoritative over the older broad
C/D/E/F/G semantic-migration decomposition still present in
`TEST001_PROTOS_NATIVE_TESTING.md`. TEST001-H does not rewrite that historical
work record. TEST001-I owns the final wrapper/routing/documentation
reconciliation and parent closure after this portable-distribution evidence is
published.
