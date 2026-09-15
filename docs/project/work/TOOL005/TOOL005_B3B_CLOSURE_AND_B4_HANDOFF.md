# TOOL005-B3B Closure and TOOL005-B4 Handoff

Status: **B3B implementation complete; closure delta validated**

Date: **2026-09-15**

Parent: **TOOL005 — Repository-wide Protos test corpus execution**

Next slice: **TOOL005-B4 — Process-snapshot corpus cutover under D135**

## Purpose

This record closes the bounded TOOL005-B3B responsibility and defines the clean
handoff into B4.

B3B owns the Package Tool project-tree corpus cutover into the official
`protos test` / TOOL002 execution lane.

B4 owns the newly discovered Process-snapshot corpus cutover under ratified
D135.

The slices are intentionally separated to avoid accumulating unrelated
execution/bootstrap work in one implementation unit.

## B3B completed scope

B3B established and validated:

- D133 CaseAuthority scheduling integration;
- D134 infrastructure-failure classification;
- host-side CaseAuthority execution bridge;
- case-scoped project-tree provisioning;
- exact corpus bindings for:
  - `protos/corpus/package-tool/resolution-root`;
  - `protos/corpus/package-tool/execution-plan`;
  - `protos/corpus/package-tool/project-projection`;
- enrollment of those three corpora in `RepositorySuite`;
- preservation of D108/D114/D116 execution and aggregation semantics;
- preservation of D077/D098 resource separation;
- preservation of bounded `--jobs N`;
- productive execution through public `protos test`.

Known productive corpus counts:

```text
resolution-root     8
execution-plan     28
project-projection  4
---------------------
B3B total          40
```

Previously validated public repository run:

```text
1286 passed
0 failed
```

## Java / Protos ownership correction

The historical Package Tool Java test class was mixed:

```text
src/test/java/com/guillermomolina/protos/execution/
    ProtosPackageToolProtosTest.java
```

It contained:

1. legitimate Java/host/runtime integration tests; and
2. one Java full-corpus runner for repository-owned `.protos` corpora.

TOOL005 requires those responsibilities to be separated.

The B3B closure delta therefore removes only:

```text
RUNNER_MANIFEST
manifestDrivenCorporaUseTheSingleTool001Runner()
runPlainSuite()
runProjectTreeSuite()
protos/tests/package-tool/java-runner.tsv
```

The remaining Java tests are retained because they exercise host/runtime
integration mechanics rather than acting as the official runner for complete
Protos corpora.

The class documentation is changed from the temporary Java corpus-bridge role to
the durable host/runtime integration-test role.

## Closure validation

After removing the Package Tool Java corpus runner, the retained Java class was
validated with:

```bash
mvn -DskipTests compile
mvn -Dtest=ProtosPackageToolProtosTest test
```

Observed result:

```text
Tests run: 28
Failures: 0
Errors: 0
Skipped: 0
```

Therefore:

```text
PACKAGE_TOOL_JAVA_FULL_CORPUS_RUNNER=REMOVED
PACKAGE_TOOL_HOST_JUNIT_TESTS=28/28_PASS
PACKAGE_TOOL_PROTOS_CORPORA_OWNER=TOOL002
```

## B3B closure state

```text
TOOL005_B3B_STATUS=CLOSED

CASE_AUTHORITY_MECHANISM=CLOSED
CASE_AUTHORITY_PRODUCTIVE_WIRING=CLOSED

PACKAGE_TOOL_PROJECT_TREE_CORPORA=3
PACKAGE_TOOL_PROJECT_TREE_CASES=40

PACKAGE_TOOL_JAVA_FULL_CORPUS_RUNNER=NO
PACKAGE_TOOL_HOST_JUNIT_TESTS=28/28_PASS

PUBLIC_TEST_TOOL_EVIDENCE=1286/1286_PASS

SPECIFICATION_CHANGED=NO
IMPLEMENTATION_CHANGED=YES
IMPLEMENTATION_VERSION=0.3.1-SNAPSHOT
```

## Why B4 is separate

The final Java-vs-Protos ownership audit found another Java-owned Protos corpus
runner:

```text
src/test/java/com/guillermomolina/protos/conformance/
    ProtosProcessSnapshotLanguageConformanceTest.java
```

It executes:

```text
protos/tests/conformance/process/manifest.tsv
```

with 15 `.protos` cases.

Unlike the Package Tool B3B runner, this class also creates deterministic
per-test guest bootstrap state:

- exact Process arguments snapshot;
- exact Environment snapshot;
- primary `process`;
- independent `otherProcess`;
- `emptyProcess`;
- fresh state per attempt.

That problem required a new architectural decision and therefore does not belong
inside B3B.

## D135

D135 selected:

```text
D135_SELECTED_CANDIDATE=A_PRIME

SuiteId                = protos/process-snapshot
CorpusId               = protos/corpus/process-snapshot
ExecutionRequirementId = protos/test/process-snapshot

BOOTSTRAP_OWNERSHIP=EXECUTION_REQUIREMENT
BOOTSTRAP_PER_ATTEMPT=FRESH
BOOTSTRAP_SHARED_MUTABLE_STATE=NO

CORPUS_BINDING_OWNS_BOOTSTRAP=NO
CASE_AUTHORITY_OWNS_BOOTSTRAP=NO
PROCESS_SNAPSHOT_BOOTSTRAP_IS_D077_RESOURCE=NO

BOOTSTRAP_FIXTURE_ID_INITIAL=NO
BOOTSTRAP_DESCRIPTOR_IN_CASESPEC=NO
TEST_ONLY_PROTOS_API=NO
```

Durable ratification publication:

```text
5823590e D135: ratify deterministic guest-bootstrap ownership
```

## TOOL005-B4 scope

B4 should implement only the D135 consumer work.

Planned scope:

1. add `SuiteId = protos/process-snapshot`;
2. add `CorpusId = protos/corpus/process-snapshot`;
3. add `ExecutionRequirementId = protos/test/process-snapshot`;
4. bind the existing 15-case Process manifest through the D126 corpus registry;
5. add one exact D125 execution binding for deterministic Process-snapshot
   bootstrap;
6. create fresh arguments/Environment/Process state per admitted attempt;
7. enroll the leaf in `RepositorySuite`;
8. prove all 15 cases through public `protos test`;
9. remove the duplicate Java full-corpus runner;
10. retain genuinely Java/bootstrap implementation tests if any remain necessary;
11. re-audit `src/test/java/**` for any remaining repository-owned `.protos`
    full-corpus runner before TOOL005 closure.

B4 must not:

- create `BootstrapFixtureId`;
- widen CaseAuthority;
- create a D077/D098 resource;
- add test-only Protos APIs;
- change language semantics;
- reactivate automatic CI.

## Target post-B4 architecture

```text
Java / JUnit
    -> Java implementation
    -> Truffle
    -> runtime
    -> host
    -> bootstrap implementation tests

Protos / TOOL002
    -> bin/protos test --jobs N
    -> repository-owned .protos semantic/conformance corpora
```

Automatic CI remains suspended until GITHUB017 explicitly validates the final
two-lane workflow.

---

# Installation script — B3B closure delta

This script applies only the final B3B ownership-separation delta.

It assumes the preceding B3B implementation is already present in the working
tree or baseline.

Save as, for example:

```text
/tmp/tool005-b3b-close.sh
```

and run from the repository root.

```bash
#!/usr/bin/env bash
set -euo pipefail

repo="${1:-.}"
cd "$repo"

test -f pom.xml
test -f src/test/java/com/guillermomolina/protos/execution/ProtosPackageToolProtosTest.java

python3 - <<'PY'
from pathlib import Path

p = Path(
    "src/test/java/com/guillermomolina/protos/execution/"
    "ProtosPackageToolProtosTest.java"
)
s = p.read_text()

old_doc = """/**
 * Temporary single Java execution bridge for Protos-owned TOOL001 fixture corpora.
 *
 * <p>Until TOOL002 owns this boundary, new TOOL001 observable-behavior fixtures belong under
 * {@code protos/tests/package-tool/**} and are executed through this class rather than through
 * one Java wrapper class per corpus.
 */"""

new_doc = """/**
 * Host/runtime integration tests for TOOL001 Package Tool mechanics.
 *
 * <p>Repository-owned Protos semantic corpora are executed by TOOL002 through {@code protos test}.
 */"""

if old_doc in s:
    s = s.replace(old_doc, new_doc, 1)
elif new_doc not in s:
    raise SystemExit("unexpected ProtosPackageToolProtosTest class documentation")

constant = (
    '    private static final Path RUNNER_MANIFEST = '
    'TEST_ROOT.resolve("java-runner.tsv");\n'
)

if constant in s:
    s = s.replace(constant, "", 1)

marker = "    @Test\n    void manifestDrivenCorporaUseTheSingleTool001Runner()"
next_test = "    @Test\n    void manifestCommandReadsValidManifest()"

if marker in s:
    start = s.index(marker)
    try:
        end = s.index(next_test, start)
    except ValueError:
        raise SystemExit("cannot locate end of Java corpus-runner block")
    s = s[:start] + s[end:]

for forbidden in (
    "RUNNER_MANIFEST",
    "manifestDrivenCorporaUseTheSingleTool001Runner",
    "runPlainSuite",
    "runProjectTreeSuite",
    "Temporary single Java execution bridge",
):
    if forbidden in s:
        raise SystemExit(f"runner residue remains in Java class: {forbidden}")

p.write_text(s)
PY

rm -f protos/tests/package-tool/java-runner.tsv

git diff --check

grep -n -E \
  'RUNNER_MANIFEST|manifestDrivenCorporaUseTheSingleTool001Runner|runPlainSuite|runProjectTreeSuite|Temporary single Java execution bridge' \
  src/test/java/com/guillermomolina/protos/execution/ProtosPackageToolProtosTest.java \
  && {
      echo "ERROR: Java corpus-runner residue remains" >&2
      exit 1
  } || true

mvn -DskipTests compile
mvn -Dtest=ProtosPackageToolProtosTest test

echo
echo "TOOL005_B3B_CLOSURE_DELTA=PASS"
echo "PACKAGE_TOOL_JAVA_FULL_CORPUS_RUNNER=REMOVED"
echo "EXPECTED_RETAINED_JUNIT_TEST_COUNT=28"
```

## Final validation before B3B publication

Recommended final validation:

```bash
git diff --check
```

```bash
mvn -DskipTests compile
```

```bash
mvn -Dtest=ProtosPackageToolProtosTest test
```

Expected:

```text
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
```

Then rerun the public Protos lane:

```bash
bin/protos test --jobs 8
```

Expected repository-wide result should remain green.

## Publication boundary

B3B publication should include the accumulated B3B implementation plus this
runner-retirement delta.

B4 should begin only after B3B is published.

Do not mix D135 Process-snapshot implementation into the B3B commit.

Before pushing B3B, synchronize normally with current `origin/main`; do not
rewrite published history and do not force-push.
