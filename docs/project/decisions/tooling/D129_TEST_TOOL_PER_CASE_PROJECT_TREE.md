# D129 — Test Tool per-case project-tree corpus provisioning contract

Status: RATIFIED — Candidate A′ selected
Issue: #501
Primary consumer: TOOL005 / #468
Depends on: D122, D123, D125, D126

## Decision

Per-case project-tree provisioning belongs to **CorpusBinding/TestPlan
materialization**, not to a new `ExecutionRequirementId` and not to the generic
resource/provider model.

```text
SuiteId
  -> CorpusId
       -> CorpusBinding
            -> TestPlan
                 -> CaseSpec
                      -> CaseAuthority
```

The execution lane remains independently selected by the already-ratified
`ExecutionRequirementId`.

## Normative invariants

```text
CASE_PROJECT_TREE_ISOLATED=YES
CASE_PROJECT_TREE_AUTHORITY_EXACT=YES
CASE_PROJECT_TREE_LIFETIME=CASE_SCOPED
CASE_PROJECT_TREE_TEARDOWN=DETERMINISTIC
CASE_PROJECT_TREE_DISCOVERY=NONE
CASE_PROJECT_TREE_PARALLEL_SAFE=YES
CASE_PROJECT_TREE_DECLARED_BEFORE_SCHEDULING=YES
CASE_PROJECT_TREE_PHYSICAL_PATH_NOT_LOGICAL_IDENTITY=YES
```

## Identity separation

```text
CorpusId
!= SuiteId
!= ExecutionRequirementId
!= ResourceKey
!= provider/profile identity
!= filesystem path
!= worker identity
!= content digest
```

No new execution requirement such as `protos/test/package-project-tree` is
introduced merely to select project-tree provisioning.

## CorpusBinding and CaseAuthority

`CorpusBinding` owns logical corpus/TestPlan materialization.

A project-tree TestPlan contains explicit case descriptors. Each case may carry
an inert, serializable fixture description for its project tree.

The physical `CaseAuthority` is materialized when that case is admitted to
execution:

```text
plan materialization
  -> logical CaseAuthority descriptor

case scheduling
  -> create physical isolated authority
  -> execute
  -> deterministic teardown
```

This preserves D126's pre-scheduling plan validation while avoiding eager
creation of all temporary trees for large corpora and bounded `--jobs N`.

## Isolation and lifecycle

Each concurrently executing project-tree case owns its own writable/mutable
authority.

No concurrent cases may share writable roots, mutable state, current-working
directory state, or cleanup lifecycle.

Teardown is deterministic on success and failure. A later execution of the same
logical case receives a fresh authority.

## Authority policy

The case descriptor explicitly records its authority policy. Generation 1 must
be able to distinguish the current Package Tool project-tree behavior from plain
read-only corpus roots.

Future read-only, writable and mutable fixture modes may be introduced without
creating execution requirements for each mode.

No authority is inferred from physical paths or ambient process working
directory.

## Parallelism

Case-scoped project trees are safe for bounded `--jobs N` because mutable
authority is not shared.

```text
--jobs 2
case A -> authority A
case B -> authority B
```

Scheduling must not infer safety from path names.

## Resource-model boundary

D129 does not reclassify project-tree authority as a D077/D098 provider/resource.
Those models remain for genuine independently scheduled execution resources such
as devices, GPUs or exclusive external services.

A per-case project tree is corpus/test-fixture authority.

## Explicit membership / no discovery

Project-tree fixtures are explicit TestPlan/CaseSpec membership.

Forbidden:

```text
recursive filesystem discovery
glob-based accidental membership
ambient workspace inference
SuiteId-to-path guessing
nearest-directory fallback
current-working-directory inference
```

Unknown fixture identity, malformed fixture descriptor or missing host
materialization authority fails closed.

## Package Tool consequence

The broader Package Tool corpus may use:

```text
CorpusId = protos/corpus/package-tool
ExecutionRequirementId = protos/test/package
```

and its `project-tree` cases may carry explicit CaseAuthority descriptors.

The existing `protos/corpus/package-toml` TOML-syntax corpus remains separate and
unchanged.

## Prior-art conclusion

The decision follows the common scalable pattern seen in Bazel, Nix, Buck2,
Gradle TestKit, pytest/pytest-xdist, Go `T.TempDir`, JUnit 5 `@TempDir`, Cargo,
MSTest and Jest:

```text
logical test
  -> explicit fixture
  -> isolated case authority
  -> execution
  -> teardown
```

The architectural lesson is to keep fixture authority orthogonal to execution
identity and external resource identity.

## Candidate score

```text
D129_SELECTED_CANDIDATE=A_PRIME
D129_FUTURE_RESILIENCE=10/10
D129_SCALABILITY=10/10
D129_PROTOS_PHILOSOPHY=10/10
```

## Supersession boundary

D129 does not generally supersede D125 or D126.

It refines their concrete boundary:

```text
D125:
  ExecutionRequirementId -> execution/inspection mechanics

D126:
  CorpusId / CorpusBinding -> corpus and TestPlan materialization

D129:
  case-scoped project-tree authority -> inside that corpus/TestPlan boundary
```

## Implementation authorization

After ratification, TOOL005 may enroll the broader Package Tool corpus using
`CorpusId -> explicit TestPlan cases -> CaseAuthority`, without opening another
decision merely for the already-known `plain` and `project-tree` Package Tool
profiles.

A new decision remains required if implementation discovers a materially
independent architectural axis not covered by this contract.
