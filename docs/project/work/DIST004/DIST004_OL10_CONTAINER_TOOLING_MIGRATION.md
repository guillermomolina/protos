# DIST004 — Development container Oracle Linux 10 base-OS and OS-tooling migration

Status: CLOSED
Live coordination: GitHub Issue #526
Upstream evaluation: UPSTREAM002 (GitHub #525),
`docs/project/work/UPSTREAM002/UPSTREAM002_OL10_CONTAINER_BASE_MIGRATION.md`

## Purpose

Own the repository-side migration evaluated by UPSTREAM002: the devcontainer
base move from the GraalVM Community Oracle Linux 8 image to the pinned Oracle
Linux 10 counterpart, plus the development-tooling provisioning contract
reconciliation the migration requires.

## Approved decisions

On 2026-09-15 the project owner selected:

- adopt the OL10 OS-provided Maven as the provisioning model while retaining the
  exact DIST002 `3.9.9` coordinate, because the OS package is Apache Maven
  3.9.9 (Red Hat 3.9.9-3);
- allocate DIST004 as the formal owning work item for the migration.

## Slices

### DIST004-A — Container base migration — CLOSED

Published by the owner before formal allocation:

- `033bbed8` — Move VS Code extension to dedicated repository
- `f657f4bd` — Remove node from container
- `21b9ce14` — Update base OS to ol10: image
  `ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol8-20260825` →
  `ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol10-20260825`; `python39`
  package → image-provided Python 3.12.13; manual checksum-verified Apache
  Maven bootstrap → OS `maven` package.

GraalVM/JDK/Truffle coordinates are unchanged. Environment and validation
evidence is retained under `docs/project/evidence/UPSTREAM002/`.

### DIST004-B — Maven OS-package provisioning contract reconciliation — CLOSED

`tools/verify_toolchain.py` re-encodes the `devcontainer.maven` binding: the
devcontainer must install the OS `maven` package (expected `os-package`),
because the exact version is anchored by the pinned image tag and the unchanged
`toolchain.json` `maven.version=3.9.9` coordinate. The previous
`ARG MAVEN_VERSION=` bootstrap form is now reported as drift by the verifier
tests. The DIST002 record carries an explicit post-closure amendment documenting
that the provisioning model changed while the exact coordinate did not.

Verifier unit tests pass (`TOOLCHAIN_VERIFIER_TESTS: PASS`); development-scope
static drift is zero. The all-surface CI bindings continue to report the
pre-existing GITHUB017/TOOL005 CI-suspension drift already recorded in
`docs/project/evidence/DIST003/`; CI reactivation validation is owned by
GITHUB017.

### DIST004-C — Portable-distribution validation and closure — CLOSED

Owner-executed in the OL10 container on 2026-09-15: `mvn package -DskipTests`
BUILD SUCCESS, `dist/build_portable.py` `DIST_BUILD: PASS` producing
`protos-0.3.0-SNAPSHOT-posix-jvm.zip`, and the complete B5 cross-slice gate
(`bash dist/validate_portable.sh`) passing B2 clean-source/archive identity
(1770 files), B3 caller-CWD + Package Tool, B4A bundled Test Tool, and B4B
exact GraalVM JDK `25.0.4.1` / Truffle `25.3.4.1`
`com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime`, with the archive
byte-for-byte unchanged (`sha256=dc0a459431e22163a660e285160d22ba157f16d735f435b9d7cbfb0fbb82ed28`).
Evidence retained under `docs/project/evidence/UPSTREAM002/`.

The distribution CI path itself is suspended (GITHUB017 / TOOL005) and is not
exercised by this item.

## Closure

DIST004 is complete: the development container runs the pinned GraalVM
Community `25i3` / JDK `25.0.4.1` image on Oracle Linux 10 with unchanged
GraalVM/JDK/Truffle coordinates, image-provided Python 3.12.13, and the OS
`maven` package providing the exact DIST002 Maven 3.9.9 coordinate under the
OS-package provisioning model. The complete 1950-test Maven suite and the
portable-distribution B5 gate pass in the OL10 container. Historical OL8 /
manual-Maven evidence remains unchanged.

## Boundaries

- no GraalVM/JDK/Truffle coordinate change;
- no Protos language/specification change;
- historical OL8/Maven-3.9.9 evidence remains historically accurate.
