# TOOL005-B5 Closure — Java/Protos Test Ownership Reconciliation

## Status

```text
TOOL005-B5_STATUS=CLOSED
VALIDATION_CLASS=TEST_OWNERSHIP_RECONCILIATION
PRODUCTION_IMPLEMENTATION_CHANGED=NO
LANGUAGE_SEMANTICS_CHANGED=NO
STANDARD_LIBRARY_SURFACE_CHANGED=NO
JAVA_FULL_CORPUS_DUPLICATION=REMOVED
OFFICIAL_PROTOS_CORPUS_OWNER=protos test
JAVA_HOST_RUNTIME_TESTS=PRESERVED
AUTOMATIC_TEST_CI=REMAINS_SUSPENDED
```

## Purpose

TOOL005-B5 reconciles Java/JUnit and Protos Test Tool ownership after the repository-wide
Protos corpus cutovers completed by the preceding TOOL005 slices.

The target ownership model is:

```text
mvn test
    -> Java / Truffle / runtime / host / bootstrap / architecture / integration evidence

protos test
    -> repository-owned tests written in Protos and selected for official validation
```

B5 does **not** migrate Java-authored semantic tests into Protos. That work is outside
TOOL005 and remains a separate concern.

The B5 rule is:

```text
existing .protos corpus already selected by official protos test
AND Java/JUnit reruns that corpus as guest semantic validation
    -> duplicate owner; remove or reduce

Java/JUnit test validates host/runtime/Truffle/bootstrap/integration/mechanism behavior
    -> retain

Java-authored semantic test with no existing official .protos corpus ownership
    -> out of scope for B5; retain
```

## Changes

### Removed duplicate Java/JUnit corpus owners

The following Java tests duplicated ownership already provided by the official Protos
Test Tool path and were removed:

```text
src/test/java/com/guillermomolina/protos/execution/ProtosCryptoSha256ModuleTest.java
src/test/java/com/guillermomolina/protos/execution/ProtosTestToolActorFullCorpusTest.java
src/test/java/com/guillermomolina/protos/execution/ProtosTestToolD4CorpusOwnershipTest.java
src/test/java/com/guillermomolina/protos/execution/ProtosTestToolDeferredFutureCorpusTest.java
src/test/java/com/guillermomolina/protos/execution/ProtosTestToolGroupFullCorpusTest.java
src/test/java/com/guillermomolina/protos/execution/ProtosTestToolPackageFullPlanExecutionTest.java
```

### Removed orphaned TOOL002 fixtures

These fixtures existed only to support the removed duplicate Java/JUnit corpus owners and
are no longer part of an authoritative validation path:

```text
protos/tests/tooling/tool002-d4-corpus-ownership.protos
protos/tests/tooling/tool002-e2b2-package-toml-full-plan.protos
protos/tests/tooling/tool002-f4b1-deferred-future-corpus.protos
protos/tests/tooling/tool002-g2-actor-full-corpus.protos
protos/tests/tooling/tool002-g3-group-full-corpus.protos
```

### Reduced mixed Java wrappers

The following Java tests previously combined legitimate Java/host structural assertions
with execution of Protos corpora that are now owned by `protos test`.

The duplicate Protos corpus execution was removed while the Java-specific assertions were
retained:

```text
src/test/java/com/guillermomolina/protos/execution/ProtosCommandLineModuleTest.java
src/test/java/com/guillermomolina/protos/execution/ProtosCsvModuleTest.java
src/test/java/com/guillermomolina/protos/execution/ProtosMathIntegerModuleTest.java
src/test/java/com/guillermomolina/protos/execution/ProtosNetworkingIpAddressesModuleTest.java
src/test/java/com/guillermomolina/protos/execution/ProtosNetworkingIpEndpointsModuleTest.java
src/test/java/com/guillermomolina/protos/execution/ProtosUriModuleTest.java
```

The retained Java coverage is structural/host-side evidence such as exported module
surface and runtime representation checks. Guest semantic corpus ownership remains with
the Test Tool.

### Hardened Actor/Group ownership guard

`ProtosTestToolActorGroupOwnershipArchitectureTest` was updated so that it no longer
reads the retired Java wrappers or TOOL002 fixtures.

Instead, it asserts that those legacy owners remain absent while continuing to verify the
production RepositorySuite/registry ownership path.

This makes the B5 result durable:

```text
LEGACY_ACTOR_JAVA_FULL_CORPUS_OWNER=ABSENT
LEGACY_GROUP_JAVA_FULL_CORPUS_OWNER=ABSENT
LEGACY_ACTOR_TOOLING_FULL_CORPUS_FIXTURE=ABSENT
LEGACY_GROUP_TOOLING_FULL_CORPUS_FIXTURE=ABSENT
```

## Retained Java tests

B5 deliberately retained Java/JUnit tests that refer to `.protos` files when their purpose
is host/runtime/Truffle/bootstrap/integration/mechanism evidence rather than duplicate
guest corpus ownership.

Examples include:

```text
ProtosArrayMatchExecutionTest
ProtosMapMatchExecutionTest
ProtosGuardMatchExecutionTest
ProtosMatchExecutionTest
ProtosMatchBytecodeExecutionTest

ProtosFilesystemLanguageConformanceTest
ProtosFilesystemLibraryConformanceTest
ProtosProcessIntegratedConformanceTest

ProtosPackageToolProtosTest
ProtosPackageExecutionPlanAdapterTest
ProtosWorkspaceRunCliTest
ProtosWorkspaceRunDriverTest
ProtosWorkspacePackagePreflightTest
ProtosWorkspacePackageAuthorityIsolationIntegrationTest

ProtosTestToolManifestPlanTest
ProtosTestToolFutureResolvedMechanismTest
ProtosTestToolFutureTerminalMechanismTest
ProtosTestToolFutureFreshInspectionFixtureSuiteTest
ProtosTestToolFutureStoredInspectionFixtureSuiteTest
ProtosTestToolFutureObservationPolicyFixtureSuiteTest

ProtosTestToolH2B3PublicIntegrationTest
```

These tests exercise Java/runtime representation, exact host bindings, lifecycle,
authority, transport, scheduling, Test Tool mechanics, or architecture. Their use of a
Protos source fixture does not make them corpus owners.

## Repository-wide duplicate-owner audit

B5 performed repository-wide searches for:

- Java tests referencing official `protos/tests/...` corpus roots;
- full-corpus or corpus-ownership naming patterns;
- `Files.walk` / `Files.list` / manifest traversal;
- `Manifest.load*` usage;
- `Runner.run*` execution APIs;
- stale references to removed wrappers and fixtures.

The remaining Java references to official Protos corpus paths were classified as
host/runtime/integration/mechanism evidence or architecture guards.

No remaining Java/JUnit test was found to own an already-selected official Protos corpus
as a full guest-semantic runner.

## Validation

Final B5 validation was performed after all ownership reconciliation was complete.

### Diff integrity

```text
git diff --check
PASS
```

### Java/JUnit lane

```text
mvn test

Tests run: 1876
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
Total time: 04:39 min
```

### Package

```text
mvn -DskipTests package
PASS
```

### Official Protos Test Tool lane

```text
bin/protos test --jobs 16

1301 passed, 0 failed
```

The official Protos total remained unchanged because B5 removed only duplicate Java
owners and their non-authoritative tooling fixtures. No official Protos corpus case was
removed.

## Resulting ownership boundary

After B5:

```text
Java/JUnit
    owns
        Java implementation evidence
        Truffle / Bytecode evidence
        runtime representation evidence
        host integration
        bootstrap
        authority/lifecycle integration
        architecture guards
        Test Tool mechanism tests

Protos Test Tool
    owns
        official repository-owned tests written in Protos
        selected through RepositorySuite / CorpusBinding
```

There is no longer a required Java/JUnit full-corpus runner for any Protos corpus selected
by the official TOOL005 repository suite.

## Performance observation

B5 exposed a separate test-performance concern that is **not part of this slice**.

The Java lane still contains several slow tests, notably:

```text
ProtosTomlParserStressTest                 ~142.6 s
ProtosTomlEncoderModuleTest                 ~14.85 s
ProtosPackageToolProtosTest                 ~11.5 s
ProtosExternalPackagePlanningPreflightTest  ~10.4 s
ProtosWorkspaceRunCliTest                    ~9.5 s
ProtosJsonParserModuleTest                   ~9.2 s
```

These require a separate audit to classify them as:

```text
migratable to Protos
genuine Java/host/runtime tests to refactor or parallelize
stress/performance tests to move outside the ordinary mvn test lane
```

No such performance policy change is included in B5.

## Relationship to TEST001

TOOL005-B5 does not reopen the historical TEST001 Java-to-Protos migration work.

TEST001 and TOOL005 intentionally do not own rewriting arbitrary Java/JUnit semantic tests
as Protos tests.

B5 only reconciles duplicate ownership for `.protos` corpora already selected by the
official Test Tool.

Any future Java-to-Protos semantic migration must be tracked independently.

## CI status

Automatic test CI remains suspended.

B5 establishes the ownership boundary needed for separate lanes, but it does not by
itself reactivate CI.

The intended CI model is now:

```text
Java lane:
    mvn test

Protos lane:
    bin/protos test --jobs N
```

GITHUB017 must still review and restore CI using these independent ownership lanes.

```text
AUTOMATIC_TEST_CI_REACTIVATED_BY_B5=NO
GITHUB017_STATUS=REMAINS_BLOCKED_PENDING_CI_REVIEW
```

## Closure statement

```text
TOOL005-B5_STATUS=CLOSED
JAVA_LANE=1876/1876_PASS
PROTOS_LANE=1301/1301_PASS
PACKAGE=PASS
DIFF_CHECK=PASS
JAVA_FULL_CORPUS_DUPLICATION=REMOVED
OFFICIAL_PROTOS_CORPUS_OWNER=protos_test
JAVA_HOST_RUNTIME_EVIDENCE=PRESERVED
CI_REACTIVATED=NO
```

TOOL005-B5 is complete.

The next coordination step is to evaluate TOOL005 parent closure against the remaining
portable-distribution and CI-boundary responsibilities, without reintroducing corpus
ownership into the Java lane.
