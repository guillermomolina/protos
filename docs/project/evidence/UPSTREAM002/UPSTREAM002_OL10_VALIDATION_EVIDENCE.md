# UPSTREAM002 — OL10 validation evidence

Retained validation evidence for the OL10 container base migration. The
environment this evidence was produced in is recorded in
[`UPSTREAM002_OL10_ENVIRONMENT_SNAPSHOT.md`](UPSTREAM002_OL10_ENVIRONMENT_SNAPSHOT.md).

## Full Maven test suite

Owner-executed in the OL10 container on 2026-09-15, after the OL10 base commit
`21b9ce14` (`Update base OS to ol10`). The container shares the repository
checkout through the devcontainer workspace mount, so `target/surefire-reports/`
on the checkout records the run.

```text
surefire report files produced 2026-09-15 (post-21b9ce14): 428
aggregate: Tests run 1950, Failures 0, Errors 0, Skipped 0
```

The suite ran under `JAVA_HOME=/opt/graalvm-community-java25i3` with the OS
`maven` package (Apache Maven 3.9.9, Red Hat 3.9.9-3).

## Toolchain verifier unit tests

```text
$ python3 tools/test_verify_toolchain.py
TOOLCHAIN_VERIFIER_TESTS: PASS
```

Run after the DIST004-B `devcontainer.maven` binding change (OS-package
provisioning model; the historical `ARG MAVEN_VERSION=` pin form is now
detected as drift).

## Toolchain verifier development scope

```text
TOOLCHAIN_BINDING: pom.bytecode status=PASS expected=21 actual=21
TOOLCHAIN_BINDING: devcontainer.image status=PASS expected=ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol10-20260825 actual=ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol10-20260825
TOOLCHAIN_BINDING: devcontainer.maven status=PASS expected=os-package actual=os-package
```

The development scope additionally reports the four CI bindings
(`ci.tests.image`, `ci.tests.java_feature`, `ci.tests.java_version`,
`ci.tests.maven`) as missing because the `Tests` workflow is suspended under
GITHUB017 / TOOL005. That suspension drift predates this migration and is
already recorded in `docs/project/evidence/DIST003/DIST003_F_STATIC_FINDINGS.txt`;
CI reactivation validation is owned by GITHUB017.

## Portable-distribution build and smoke

Not yet executed for the OL10 container at the time this file was first
retained. The portable build and extracted-distribution smoke in the OL10
container are owned by DIST004-C
(`docs/project/work/DIST004/DIST004_OL10_CONTAINER_TOOLING_MIGRATION.md`).
The distribution CI path itself is suspended (GITHUB017 / TOOL005).

## Interpretation limits

The full-suite result is evidence for the OL10 container with the OS Maven
package on the pinned image. The suspended CI surfaces are intentionally not
claimed as validated; their reactivation validation belongs to GITHUB017.
