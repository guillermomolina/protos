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

Owner-executed in the OL10 container on 2026-09-15. The shaded build and the
portable distribution are produced with the OS `maven` package under
`JAVA_HOME=/opt/graalvm-community-java25i3`.

```text
$ mvn package -DskipTests
[INFO] Replacing original artifact with shaded artifact.
[INFO] Replacing /workspaces/protos/target/protos-0.3.0-SNAPSHOT.jar with /workspaces/protos/target/protos-0.3.0-SNAPSHOT-shaded.jar
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.504 s
[INFO] Finished at: 2026-09-15T10:43:06Z

$ python3 dist/build_portable.py
phase=dist02 materialize toolchain tree
phase=dist03 copy canonical root-POM runtime projection
DIST_RUNTIME_PROJECTION_CHECK: PASS
DIST_RUNTIME_PROJECTION_JARS: 12
phase=dist04 create archive
DIST_ARCHIVE_CRC_CHECK: PASS
DIST_LAYOUT_CHECK: PASS
DIST_SOURCE_IDENTITY_CHECK: PASS
DIST_RUNTIME_METADATA_CHECK: PASS
DIST_LAUNCHER_MODE_CHECK: PASS
DIST_ARCHIVE: /workspaces/protos/target/distributions/protos-0.3.0-SNAPSHOT-posix-jvm.zip
DIST_ARTIFACT_KIND: development-distribution
DIST_BUILD: PASS
```

The complete B5 cross-slice gate then passed against that archive:

```text
$ bash dist/validate_portable.sh
DIST_ARCHIVE_CRC_CHECK: PASS
DIST_SINGLE_ROOT_CHECK: PASS
DIST_SOURCE_REVISION_CHECK: PASS revision=62b34188e13d06320c48e061f975cded36a69afc
DIST_ARTIFACT_MODE_CHECK: PASS mode=development-distribution
DIST_SOURCE_CLEAN_CHECK: PASS
DIST_INTERNAL_CHECKSUM_COVERAGE_CHECK: PASS files=1770
DIST_INTERNAL_CHECKSUM_VALUE_CHECK: PASS
DIST001_B2_VERIFY: PASS
DIST_OUTSIDE_CHECKOUT_CHECK: PASS
DIST_B3_RUNTIME_ISOLATION_CHECK: PASS mode=supported
DIST_CALLER_CWD_SOURCE_CHECK: PASS
DIST_PACKAGE_TOOL_CWD_CHECK: PASS
DIST001_B3_SMOKE: PASS
DIST_B4A_OUTSIDE_CHECKOUT_CHECK: PASS
DIST_B4A_RUNTIME_ISOLATION_CHECK: PASS mode=supported
DIST_BUNDLED_TEST_TOOL_CHECK: PASS
DIST001_B4A_SMOKE: PASS
DIST_B4B_OUTSIDE_CHECKOUT_CHECK: PASS
DIST_SELECTED_JDK_CHECK: PASS java.version=25.0.4.1
DIST_SELECTED_RUNTIME_GATE_CHECK: PASS
DIST_OPTIMIZER_JAR_INTACT_CHECK: PASS version=25.3.4.1
DIST_TRUFFLE_COMPILER_CHECK: PASS version=25.3.4.1
DIST_DAP_RUNTIME_CLOSURE_CHECK: PASS version=25.3.4.1
DIST_OPTIMIZING_RUNTIME_CHECK: PASS class=com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime
DIST001_B4B_SMOKE: PASS
DIST_B5_SINGLE_ARCHIVE_CHECK: PASS sha256=dc0a459431e22163a660e285160d22ba157f16d735f435b9d7cbfb0fbb82ed28
DIST_B5_ARTIFACT_MODE_CHECK: PASS mode=development
DIST_B5_ARCHIVE_IDENTITY_CHECK: PASS
DIST_B5_CWD_PACKAGE_CHECK: PASS
DIST_B5_TEST_TOOL_CHECK: PASS
DIST_B5_OPTIMIZING_RUNTIME_CHECK: PASS
DIST001_B5_CROSS_SLICE: PASS
```

The archive remains byte-for-byte unchanged across the B5 gate (single-archive
SHA-256 `dc0a459431e22163a660e285160d22ba157f16d735f435b9d7cbfb0fbb82ed28`).

## Interpretation limits

The full-suite and B5 results are evidence for the OL10 container with the OS
Maven package on the pinned image. The suspended CI surfaces are intentionally
not claimed as validated; their reactivation validation belongs to GITHUB017.
