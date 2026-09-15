# TOOL005-B4 — Process Snapshot Corpus Cutover Closure and B5 Handoff

## Status

```text
TOOL005-B4_STATUS=CLOSED
TOOL005_STATUS=IN_PROGRESS
NEXT_SLICE=TOOL005-B5
AUTOMATIC_TEST_CI=REMAINS_SUSPENDED
```

## Purpose

TOOL005-B4 migrated the repository-owned Process Snapshot Protos corpus from its legacy Java/JUnit full-corpus runner into the official bundled Test Tool path.

The resulting ownership is:

```text
mvn test
    -> Java / Truffle / runtime / host / bootstrap / bounded integration evidence

bin/protos test
    -> repository-owned Protos corpus execution
```

B4 does not reactivate CI. CI remains suspended until the Java and Protos validation lanes have been fully reconciled and independently reviewed.

## Ratified architecture consumed

B4 implements D135 Candidate A′.

```text
SuiteId                = protos/process-snapshot
CorpusId               = protos/corpus/process-snapshot
ExecutionRequirementId = protos/test/process-snapshot
```

The deterministic Process Snapshot bootstrap is owned by the execution requirement.

```text
BOOTSTRAP_OWNERSHIP=EXECUTION_REQUIREMENT
BOOTSTRAP_PER_ATTEMPT=FRESH
BOOTSTRAP_SHARED_MUTABLE_STATE=NO
BOOTSTRAP_AMBIENT_DISCOVERY=NO

CORPUS_BINDING_OWNS_BOOTSTRAP=NO
CASE_AUTHORITY_OWNS_BOOTSTRAP=NO
PROCESS_SNAPSHOT_BOOTSTRAP_IS_CASE_AUTHORITY=NO
PROCESS_SNAPSHOT_BOOTSTRAP_IS_D077_RESOURCE=NO
RESOURCE_CAPACITY_ACCOUNTING_CHANGED=NO
```

## Implemented production path

```text
RepositorySuite
    -> SuiteId protos/process-snapshot
    -> CorpusId protos/corpus/process-snapshot
    -> manifest plan materialization
    -> ExecutionRequirementId protos/test/process-snapshot
    -> async Process Snapshot execution facility
    -> exact D135 bootstrap
    -> deterministic Test Tool aggregation
```

The Process Snapshot corpus contains 15 cases.

## Legacy owner retired

The legacy Java/JUnit full-corpus runner:

```text
src/test/java/com/guillermomolina/protos/conformance/
ProtosProcessSnapshotLanguageConformanceTest.java
```

was validated once more as the old oracle:

```text
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
```

and then removed.

Bounded Java-side Process Snapshot mechanism tests remain.

## Validation evidence

### Focal B4 validation

```text
TOOL005-B4-1   D135 synchronous bootstrap proof              PASS
TOOL005-B4-2A  async transport                               PASS
TOOL005-B4-2B  productive Test Tool scope integration        PASS
TOOL005-B4-2C  exact D125 execution-requirement binding      PASS
TOOL005-B4-3A  CorpusId + manifest source authority          PASS
TOOL005-B4-3B  suite leaf + productive repository execution  PASS
```

### Public Protos Test Tool

```text
process-snapshot 15/15 passed
TOTAL=1301/1301_PASS
```

### Java validation

The Java validation lane completed successfully after removal of the Process Snapshot full-corpus wrapper.

Two stale/duplicating Java validation details were also reconciled while validating B4:

1. `ProtosCliPolyglotRoutingArchitectureTest` was updated to recognize the current `installWithCaseAuthorities(...)` production entry point introduced by B3B.
2. `ProtosTestToolStdoutCompletionTest` no longer recursively executes the complete `protos test` corpus from `mvn test`; it retains the two bounded Java-side stdout completion/failure mechanism tests.

These are validation-lane reconciliations only. They do not change Protos semantics or the D135 architecture.

## Launcher observation

During B4 validation, a stale checkout JAR initially caused the public Test Tool to observe new Protos source with old Java registry code.

`bin/protos` checkout mode executes the built `target/protos-*.jar`, not `target/classes`.

Therefore public validation after Java changes requires package regeneration:

```text
mvn -DskipTests package
bin/protos test --jobs 16
```

This is a build/validation workflow observation, not a semantic change.

## Final B4 closure state

```text
TOOL005_B4_STATUS=CLOSED
PROCESS_SNAPSHOT_CORPUS_CASES=15
PROCESS_SNAPSHOT_PROTOS_TEST=15/15_PASS
PUBLIC_PROTOS_TEST=1301/1301_PASS

PROCESS_SNAPSHOT_JAVA_FULL_CORPUS_RUNNER=REMOVED
BOUNDED_JAVA_BOOTSTRAP_EVIDENCE=RETAINED

SPECIFICATION_CHANGED=NO
LANGUAGE_SEMANTICS_CHANGED=NO
STANDARD_LIBRARY_SURFACE_CHANGED=NO

AUTOMATIC_TEST_CI_REACTIVATED=NO
TOOL005_PARENT_STATUS=IN_PROGRESS
```

## Next slice — TOOL005-B5

### Title

```text
TOOL005-B5 — Java/Protos test ownership reconciliation
```

### Goal

Ensure that Java/JUnit owns only genuine Java/Truffle/runtime/host/bootstrap/architecture/stress/integration evidence, while repository-owned Protos corpus behavior is owned by `protos test`.

### Scope

1. Audit `src/test/java` references to `protos/tests`.
2. Classify each relevant Java test as:
   - `DUPLICATE_CORPUS`
   - `JAVA_HOST_RUNTIME`
   - `MIXED`
3. Remove duplicate Java corpus owners.
4. Split mixed tests, retaining only genuine Java-side evidence.
5. Keep legitimate Java host/runtime/integration/stress coverage.
6. Re-run the complete Java lane.
7. Re-run the complete Protos corpus lane.
8. Prove that no official repository-owned `.protos` corpus requires JUnit.

### Initial evidence already collected

```text
DUPLICATE_CORPUS:
  ProtosCryptoSha256ModuleTest
  ProtosTestToolActorFullCorpusTest
  ProtosTestToolGroupFullCorpusTest

MIXED:
  ProtosCsvModuleTest
  ProtosUriModuleTest
  ProtosMathIntegerModuleTest
  ProtosNetworkingIpAddressesModuleTest
  ProtosNetworkingIpEndpointsModuleTest

JAVA_HOST_RUNTIME examples to retain:
  ProtosJsonParserModuleTest
  ProtosExternalPackagePlanningPreflightTest
  ProtosWorkspaceRunCliTest
```

These are initial classifications only. B5 must audit each candidate before mutation.

### B5 closure criterion

```text
mvn test
    -> PASS
    -> no required ownership of repository Protos corpora

bin/protos test --jobs N
    -> PASS
    -> authoritative execution of every selected repository-owned Protos corpus

NO_OFFICIAL_PROTOS_CORPUS_REQUIRES_JUNIT=YES
```

Only after B5 evidence is complete should TOOL005 parent closure be evaluated.

CI remains suspended until the independently visible Java and Protos lanes are reviewed and the separate CI restoration work is ready.
