# UPSTREAM002 — GraalVM Community container base migration OL8 → OL10

Status: CLOSED
Impact classification: `ACTION_REQUIRED` — owned by DIST004 (GitHub #526)
Live coordination: GitHub Issue #525

## Purpose

Track and evaluate migration of the Protos development-container base image from
`ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol8-20260825` to the pinned
Oracle Linux 10 counterpart `ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol10-20260825`,
and audit the development-tooling baseline (Python, Maven, GitHub CLI) against
its actual Protos compatibility role instead of assuming that historical
provisioning pins are intrinsic requirements.

## Authority boundary

This record is non-normative upstream-impact evaluation. It does not by itself
authorize image, toolchain, or repository changes: the repository-side migration
is owned by DIST004 (`docs/project/work/DIST004/DIST004_OL10_CONTAINER_TOOLING_MIGRATION.md`),
and the exact DIST002 Maven coordinate remains project authority. GraalVM/JDK/
Truffle are runtime coordinates and remain exact under this evaluation.

## Evaluation summary

The target OL10 image exists and corresponds to the same pinned GraalVM
Community `25i3` / JDK `25.0.4.1` line. The evaluation was performed in the
devcontainer built from the OL10 image on 2026-09-15; the exact environment
outputs are retained in
`docs/project/evidence/UPSTREAM002/UPSTREAM002_OL10_ENVIRONMENT_SNAPSHOT.md`.

| Surface | Result |
|---|---|
| OS | Oracle Linux Server 10.2 |
| `JAVA_HOME` | `/opt/graalvm-community-java25i3` → GraalVM CE 25.3.4.1+1.1 / JDK 25.0.4.1+1-jvmci-25.3-b22 (exact primary runtime) |
| PATH `java` | OpenJDK 21.0.12.1 (Red_Hat) — the OL10 system JDK installed as a dependency of the OS `maven` package; Maven builds run under `JAVA_HOME` |
| Maven | Apache Maven 3.9.9 (Red Hat 3.9.9-3), `/usr/share/maven` |
| Python | `/usr/bin/python3` → Python 3.12.13 (provided by the OL10 image) |
| GitHub CLI | 2.100.0 (2026-09-03) — retained pinned/checksum-verified provisioning |
| microdnf package set | `findutils diffutils git gzip shadow-utils tar unzip xz maven procps` installs cleanly on OL10 |

GraalVM/JDK/Truffle identity is unchanged (`25i3-25.0.4.1` image line, Graal CE
25.3.4.1, jvmci-25.3-b22): the OS migration cannot mask toolchain drift because
`mvn -version` resolves Java `25.0.4.1`, vendor `GraalVM Community`, runtime
`/opt/graalvm-community-java25i3`.

A dedicated per-package OS-difference comparison (libc, OpenSSL, locale, and
certificate surfaces) was not retained as its own artifact; the operative
compatibility evidence is the clean microdnf provisioning, the complete Maven
suite, and the tooling identity checks in the retained snapshot, with the
portable-distribution build/smoke (which exercises the tar/gzip/git consumer
path) owned by DIST004-C. The observed OS-level change is recorded explicitly:
the OS `maven` package pulls the OL10 system JDK 21 onto PATH, which is
intentional for the package but does not affect builds, because Maven runs
under `JAVA_HOME` (GraalVM JDK 25.0.4.1).

## Maven audit

Why the manual bootstrap existed: DIST002-A/B/C/D centralized one reproducible
exact Maven 3.9.9 coordinate across devcontainer, CI, and distribution
validation, provisioned manually with checksum verification. That was an
environment-alignment mechanism; it is not evidence that Protos technically
requires a Maven version that a modern OS package cannot supply.

Actual requirement established: the OL10 OS `maven` package is Apache Maven
3.9.9 (Red Hat 3.9.9-3) — exactly the DIST002 coordinate. The complete
1950-test Maven suite passes in the OL10 container using that OS package
(0 failures, 0 errors, 0 skipped; surefire reports captured 2026-09-15 after
the OL10 base commit). No exact-version requirement beyond 3.9.9 exists, and
3.9.9 is satisfied by the pinned image's package.

Decision (owner-selected 2026-09-15): prefer `microdnf install maven`; remove
the manual Apache download/bootstrap path; retain `toolchain.json`
`maven.version=3.9.9` unchanged; re-encode the `tools/verify_toolchain.py`
`devcontainer.maven` binding to check the OS-package provisioning model.
Reproducibility rationale: the exact pinned image tag determines the OS package
set, so the Maven version is deterministic for the pinned environment; the
contract continues to record the exact 3.9.9 coordinate, and the runtime Maven
identity check belongs to CI when it is reactivated (GITHUB017). Maven Wrapper
is not introduced: no need for repository-owned Maven control beyond the pinned
image has been demonstrated.

## Python baseline

Repository scripts and tools carry no Python-version assumptions (no
`sys.version_info` or `python3.N` pins under `scripts/`, `tools/`, `dist/`, or
`.devcontainer/`). The OL10 image provides Python 3.12.13 at `/usr/bin/python3`
— the newest practical OL10 Python and the initial candidate of this
evaluation. The legacy `python39` provisioning is removed.

## Image/tag reference inventory

Live references migrated to the OL10 image:

- `.devcontainer/Dockerfile`
- `toolchain.json`
- `tools/test_verify_toolchain.py` (verifier test fixtures)

Historical references intentionally retained, not rewritten:

- `docs/project/work/DIST002/DIST002_TOOLCHAIN_ALIGNMENT.md` — narrative of the
  OL8/bootstrap-era contract remains historically accurate
- `docs/project/evidence/DIST003/DIST003_F_STATIC_FINDINGS.txt` — immutable
  evidence
- `tools/test_verify_toolchain.py` pre-C `22-ol8` fixture — historical-state
  emulation for drift detection

No other OL8 image references remain. The VS Code extension moved to a
dedicated repository, so this repository no longer provisions node in the
container; `.devcontainer/devcontainer.json` is unchanged and no
debugger/tooling consumer in this repository inherits the image directly.
Benchmark/PERF reference environments are historical and are not moved to OL10
by this migration; their records remain accurate for the environments they
describe.

## Validation evidence

Retained in `docs/project/evidence/UPSTREAM002/UPSTREAM002_OL10_VALIDATION_EVIDENCE.md`:

- Full Maven suite in the OL10 container (owner-executed 2026-09-15, after the
  OL10 base commit): 1950 tests, 0 failures, 0 errors, 0 skipped.
- `tools/test_verify_toolchain.py`: `TOOLCHAIN_VERIFIER_TESTS: PASS` after the
  DIST004-B binding change.
- `tools/verify_toolchain.py --mode check --scope development`: zero drift on
  `pom.bytecode`, `devcontainer.image`, and `devcontainer.maven`.
- `tools/verify_toolchain.py --mode check` (all-surface): the CI bindings
  (`ci.tests.*`, `ci.distribution.*`) report suspension drift — pre-existing
  since the GITHUB017 / TOOL005 CI suspension and already recorded in
  `docs/project/evidence/DIST003/`; CI reactivation validation is owned by
  GITHUB017.
- Portable-distribution build and extracted-distribution smoke in the OL10
  container: owned by DIST004-C (see the DIST004 work record). The distribution
  CI path itself is suspended (GITHUB017).

## Impact classification

`ACTION_REQUIRED` — concrete repository migration justified. The migration and
the toolchain-provisioning reconciliation are owned by DIST004 / GitHub #526.
UPSTREAM002 closes under the UPSTREAM family rule: impact is classified and the
required Protos work has a proper owner; derived work need not finish first.

## Closure checklist

1. OL10 compatibility evaluated with retained evidence — PASS (environment
   snapshot and validation evidence under `docs/project/evidence/UPSTREAM002/`).
2. Impact classified — PASS: `ACTION_REQUIRED`.
3. Concrete migration has a proper owning work item and all affected image
   references are identified — PASS: DIST004 / GitHub #526; inventory above.
4. No hidden GraalVM/JDK/Truffle version drift — PASS: exact `25.0.4.1` /
   Graal CE `25.3.4.1` verified through `JAVA_HOME` and `mvn -version`.
5. Python baseline and compatibility evidence explicitly recorded — PASS:
   Python 3.12.13, no repository version assumptions found.
6. Actual Maven compatibility requirement established rather than inferred —
   PASS: exact 3.9.9, satisfied by the OS package, full suite green.
7. Maven provisioning/pinning decision and reproducibility rationale explicitly
   recorded — PASS: Maven audit section above and DIST004-B.
8. Affected development/debugger/distribution/CI surfaces accounted for — PASS:
   devcontainer verified live; distribution smoke handed to DIST004-C; CI
   reactivation remains owned by GITHUB017.
9. Historical OL8/Maven-3.9.9 evidence remains historically accurate — PASS:
   DIST002 narrative, DIST003 evidence, and pre-C fixture retained unchanged.
