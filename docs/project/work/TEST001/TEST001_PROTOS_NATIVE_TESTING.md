# TEST001 — Protos-native repository testing and validation ownership

Status: IN_PROGRESS

Nature: non-normative repository validation ownership and migration record

Live coordination: GitHub `TEST001 / #449`

Related implementation authority:

- `docs/project/work/TOOL002/TOOL002_TEST_TOOL.md`
- `docs/design/TEST_TOOL_ARCHITECTURE.md`
- `protos/tests/conformance/README.md`
- `.github/workflows/tests.yml`
- `scripts/publication_validation.py`

Audit baseline: `5c03c4d424195d5146160f9e6fa3b226a1e0c538`

## Purpose

TEST001 moves repository validation to the ownership model already enabled by
TOOL002: Protos-observable language and Standard Library behavior is primarily
proved by ordinary Protos programs run through `protos test`, while Java/JUnit
continues to prove Java/Truffle/runtime/host implementation mechanics and the
independent bootstrap/integration floor.

TEST001 does not reopen TOOL002 and does not add test syntax, a privileged Test
object, assertion primitives, or another public testing API.

## Fixed ownership classes

Every retained test family has one primary ownership class:

- `PROTOS_SEMANTIC` — observable language/Core behavior; primary owner TOOL002.
- `STANDARD_LIBRARY` — public Standard Library behavior expressible from Protos;
  primary owner TOOL002 where practical.
- `HOST_RUNTIME` — Java/Truffle/compiler/runtime/backend implementation behavior;
  primary owner JUnit or another host-level harness.
- `INTEGRATION_BOOTSTRAP` — CLI/distribution/resolver/debugger/tool startup and
  cross-boundary wiring; primary owner JUnit/shell/integration as appropriate.

The Java package or class name is not an ownership decision. A Java test may
currently encode public semantics and therefore be a migration candidate, while
a Java test that executes guest code may still be a host-mechanism test that
must remain Java-owned.

## TEST001-A — inventory and ownership classification

Status: CLOSED

The inventory below classifies the current suite at durable family boundaries.
A later migration slice must inspect the exact assertions before deleting any
individual Java test from a `MIXED` family.

### Already TOOL002-authoritative corpus

`protos/tests/conformance/` is already an implementation-independent Protos
corpus. TOOL002 owns every retained main-manifest expectation family, including
the Future families, and owns the Actor/Group plans through its published
execution paths. The retired direct Java manifest owner must not be recreated.

This existing corpus is the first source checked before creating a new fixture:
where it already proves the public contract, TEST001 should retire duplicate
Java semantic policy rather than duplicate the same case again.

### Top-level JUnit package classification

| Current package | Primary classification | TEST001 treatment |
|---|---|---|
| `analysis` | HOST_RUNTIME | Retain. Static-analysis/session/definition algorithms are tooling implementation. Public LSP behavior remains integration-owned. |
| `cli` | INTEGRATION_BOOTSTRAP, mixed | Retain CLI/REPL/DAP/LSP/tool-startup wiring. The full-corpus Test Tool JUnit checkpoint is reduced in B to a small independent bootstrap floor. |
| `conformance` | MIXED: PROTOS_SEMANTIC / STANDARD_LIBRARY / host integration | Migrate or reconcile guest-visible Filesystem, Process/resource and similar contracts in D/G; retain host provisioning/orchestration mechanics. |
| `documentation` | HOST_RUNTIME | Retain extractor/model/JSON documentation machinery tests; these do not define guest semantics. |
| `execution` | MIXED | Main migration pool. Split by the thematic families below; never bulk-delete this package. |
| `lexer` | HOST_RUNTIME plus semantic acceptance cross-check | Retain lexer/source-span/Unicode implementation tests. Observable source acceptance/rejection may also have TOOL002 conformance, but structural token assertions stay host-owned. |
| `lsp` | INTEGRATION_BOOTSTRAP / HOST_RUNTIME | Retain protocol, diagnostics, symbols and workspace server behavior. |
| `parser` | HOST_RUNTIME plus semantic acceptance cross-check | Retain AST/layout/parser-structure tests. Observable grammar acceptance/rejection belongs in conformance too where executable; parser object shape does not. |
| `runtime` | MIXED | Public Actor/Group/Process/Future/resource semantics migrate/reconcile in E/G; runtime identities, schedulers, transfer, lifecycle machinery, interop and backends remain JUnit. |
| `semantic` | HOST_RUNTIME | Retain canonicalizer and coverage-analyzer structural tests. Observable resulting semantics are independently covered through TOOL002, not by moving canonicalizer internals into the guest corpus. |

### `execution` family split

#### PROTOS_SEMANTIC candidates — TEST001-D

These families encode guest-observable semantics and must end with TOOL002 as the
primary policy owner where their assertions can be expressed by the existing
corpus model:

- canonical execution behavior for slots, lookup, objects, closures, composition
  and member mutation, while retaining lowering/node-shape assertions separately;
- `ProtosArrayConformanceCompletionTest`, `ProtosIdentityMapConformanceTest` and
  other Core collection behavior;
- match behavior (`ProtosArrayMatchExecutionTest`, `ProtosMapMatchExecutionTest`,
  `ProtosOrMatchExecutionTest`, `ProtosGuardMatchExecutionTest`, ordinary
  `ProtosMatchExecutionTest` semantics);
- equality/identity and numeric public behavior;
- ordinary non-local-return / invalid-super / receiver and closure semantics;
- public module/import behavior where the assertion is about the language
  contract rather than resolver implementation.

Many of these contracts already have matching entries under the TOOL002 main
manifest. Migration therefore begins by proving coverage equivalence and deleting
only the duplicate Java semantic owner; it must not create redundant Protos cases
by default.

#### STANDARD_LIBRARY candidates — TEST001-F

Public module/protocol behavior currently exercised through Java includes:

- Collections (`ProtosCollections*ModuleTest`);
- CLI parsing model (`ProtosCommandLine*`);
- crypto SHA-256, CSV, JSON, TOML, URI and Math/Integer modules;
- public networking address/endpoint modules;
- public Core/standard protocols for Array, Boolean, Bytes, numeric operations,
  Map, Path, String, Encoding, Future, Process and Text/Byte I/O.

The target is TOOL002 for behavior callable from ordinary Protos. Java remains
for native bridges, backend mechanics, resource provisioning and implementation
invariants. Stress tests may remain host-owned when they intentionally exercise
host resource limits or implementation-only instrumentation rather than a public
semantic bound.

#### HOST_RUNTIME — retain under JUnit

The following are explicitly not migration targets merely because they execute
Protos code:

- `ProtosPerf006*`, `ProtosPlat*`, Bytecode DSL and C-prime continuation tests;
- Truffle lowering, `CallTarget`, node/frame/activation and instrumentation tests;
- polyglot Context routing and interop projection tests;
- NIO filesystem/network/poller backend tests;
- scheduler, execution-domain, operation/lifecycle and carrier machinery;
- source compiler/loader internals and module-resolver implementation details;
- DAP/LSP/debugger and source-section integration;
- TOOL002's own scheduler/provider/resource/runner implementation tests.

TOOL002 must keep an independent Java/bootstrap floor. Migrating its own complete
implementation validation into TOOL002 would make the product its only bootstrap
proof and is therefore prohibited by TEST001.

#### INTEGRATION_BOOTSTRAP — retain or narrow

Retain host-level proof for:

- CLI command routing and external process entry;
- bundled Tool resolution and entry-module loading;
- minimal Test Tool execution and carrier/exit-status wiring;
- workspace/package resolver bootstrap boundaries;
- DAP/LSP transport and external tooling startup;
- extracted distribution startup/packaging boundaries.

The current full-corpus `ProtosCliTest` checkpoint is the important exception:
TEST001-B removes its role as the complete corpus runner and leaves only the
small independent bootstrap proof.

### `runtime` family split

`runtime` is intentionally `MIXED` rather than globally Java-owned.

Guest-observable Actor/Group/Process/Future/cancellation/resource behavior should
be reconciled with TOOL002 in TEST001-E/G. Examples include public API,
termination, message/result behavior and value-transfer consequences that an
ordinary Protos program can observe.

JUnit remains primary for execution domains, mailbox scheduling machinery,
transport implementations, host resource acquisition/provisioning, concrete NIO
objects, interop messages, Context routing and internal lifecycle state. A public
semantic test may coexist temporarily with such an internal test when the two
prove different properties; that is not duplicate primary ownership.

### Parser / lexer / canonicalizer boundary

TEST001 does not equate source-language semantics with Java frontend structure.
For example:

```text
source accepted/rejected with defined guest behavior
    -> TOOL002 conformance can be authoritative

token span / AST node shape / canonical IR / lowering topology
    -> Java frontend test remains authoritative
```

This rule preserves detailed compiler regression coverage while ensuring that a
future non-Java frontend can still be validated against implementation-independent
Protos behavior.

## TEST001-B — direct Test Tool CI ownership

Status: CLOSED

TEST001-B makes the already-closed TOOL002 corpus a direct CI authority instead
of entering the complete corpus through one slow JUnit method.

The CI order remains intentionally Java/host first and Protos-tool second:

```text
impact-aware Java/runtime validation
    -> build checkout CLI without rerunning JUnit
    -> bin/protos test --jobs 2
```

The second-stage command is the repository's ordinary checkout launcher. CI does
not call a Java test method to obtain Test Tool coverage, does not introduce a
special Test Tool entry point, and does not duplicate TOOL002 expectation policy
in shell or Java.

`ProtosCliTest` retains an independent bootstrap floor by invoking
`protos test --jobs 0`. The bundled Test Tool resolves and executes its public
`Main.protos`, but the already-ratified positive-jobs check fails before any
manifest is loaded. This keeps CLI -> bundled resolver -> Test Tool entry ->
ordinary Protos option handling under JUnit without making TOOL002 execute its
own complete corpus as its bootstrap proof.

`ProtosTestToolJClosureReconciliationTest` is reconciled in the same slice. Its
architectural guard now requires the Java-first stage, the checkout CLI build,
and the direct public `bin/protos test --jobs 2` stage in that order, while
explicitly rejecting restoration of the former JUnit/property corpus launcher.

Existing TOOL002 Java tests continue to own host/bootstrap mechanics such as
resolver wiring, async execution facilities, result-carrier validation,
provider/resource integration and public cutover invariants. TEST001-B does not
retire those tests.

No Protos language, Standard Library, Test Tool semantic, scheduler, resource,
timeout/retry, reporting, distribution or runtime behavior changes in B.

### TEST001-B validation

B is test/CI-impacting and must prove all three layers on the integrated state:

1. focal CLI/Test Tool bootstrap and TOOL002-J CI-architecture JUnit coverage;
2. the complete Java/JUnit suite;
3. a packaged checkout followed by direct `bin/protos test --jobs 2`.

The direct Test Tool command is therefore validated before publication, not only
after GitHub Actions receives the commit.

## TEST001-C — semantic-test ownership and no-duplication guard

Status: CLOSED

TEST001-C turns the duplicate-primary-owner rule into an executable repository
invariant without pretending that semantic equivalence can be inferred from Java
class names or Protos fixture filenames.

The durable mechanism is:

- `protos/tests/test_ownership.json` — machine-readable ownership ledger for
  public semantic contracts that TEST001 reconciles;
- `scripts/test_ownership_guard.py` — fail-closed validator for that ledger;
- `scripts/publication_validation.py` — invokes the ownership guard before test
  selection or Maven execution;
- `AGENTS.md` — requires later migration slices to prove equivalence before
  registering and retiring/reclassifying an owner.

The registry is intentionally empty at C closure. A and C identify the migration
families and establish the enforcement mechanism; D/E/F/G populate exact
contract records only after each bounded slice has proved which assertions are
actually equivalent. Pre-populating guessed mappings in C would recreate the
name-based ownership inference that TEST001 explicitly rejects.

### Ownership states

A registered semantic contract has one structural primary owner and one state:

- `RECONCILED` — TOOL002 is the primary owner. Any retained JUnit/shell evidence
  must be explicitly `HOST_RUNTIME` or `INTEGRATION_BOOTSTRAP`;
- `MIGRATION_OVERLAP` — TOOL002 has been selected as primary, but an explicitly
  identified duplicate semantic-policy owner temporarily remains during bounded
  migration work;
- `EXCEPTION` — JUnit remains primary because TOOL002 is genuinely inappropriate
  for that public contract; the registry requires an explicit reason.

A `RECONCILED` entry cannot contain `MIGRATION_OVERLAP`. Contract identifiers are
unique and canonical, evidence paths are exact existing repository files, and
the registry is sorted to keep review diffs deterministic.

### What the guard deliberately does not do

The guard does not compare names and declare tests equivalent. Whether two tests
own the same observable contract is a semantic audit result produced by the
responsible TEST001-D/E/F/G slice. Once that result is recorded under one stable
contract id, the guard prevents the repository from representing two primary
owners or disguising semantic overlap as reconciled host evidence.

This keeps legitimate Java implementation coverage beside Protos conformance
without allowing permanent dual semantic authority.

### TEST001-C validation

C changes repository validation machinery, so it is `TEST_IMPACT`, but its
executable impact is confined to Python repository-validation helpers:

1. focused ownership-guard unit tests exercise valid ownership, duplicate ids,
   invalid primary ownership, legitimate host secondaries, overlap rejection and
   explicit exceptions;
2. focused publication-validator integration tests prove that a missing ownership
   guard or malformed registry fails closed before Maven;
3. the real repository ownership registry is validated directly.

C does not change Java/runtime code, Protos executable corpus, Test Tool code or
CI execution wiring, so it does not mechanically rerun the Java suite or direct
Test Tool corpus. Broader validation remains impact-driven rather than automatic.

No Protos specification, runtime implementation, Standard Library behavior,
TOOL002 public behavior or implementation version changes in C.

## TEST001-D — Core/language semantic migration

Status: IN_PROGRESS

### TEST001-D1 — OR-pattern semantic ownership

Status: CLOSED

D1 reconciles the D090 OR/alternative-pattern conformance family.

The retiring Java class `ProtosOrMatchExecutionTest` directly executed seven
ordinary conformance fixtures and asserted their public outcomes. Six of those
fixtures were already authoritative TOOL002 main-manifest cases. D1 adds the
remaining `matching/or-invalid-outcome-no-retry.protos` case to the same manifest
with the already-supported `error` expectation, then removes the duplicate Java
semantic-policy owner.

The registered semantic contract is `language.match.or-alternatives` with
TOOL002 as the primary owner.

`ProtosMatchBytecodeExecutionTest` remains under JUnit as explicit
`HOST_RUNTIME` secondary evidence. It tests Bytecode-backend parity,
continuation/suspension behavior and runtime-transfer invariants; retaining it
does not create a second semantic-policy owner.

D1 does not change OR-pattern semantics, grammar, runtime implementation,
TOOL002 behavior, expectation kinds or implementation version.

#### TEST001-D1 focal validation profile

Retained executable evidence and validation dependency closure:

1. before retirement, `ProtosOrMatchExecutionTest` proves all seven fixture
   outcomes against the publication baseline;
2. after retirement, the ownership guard proves one registered primary owner;
3. manifest checks prove all seven fixtures are selected exactly once by the
   TOOL002 main plan, including the newly retained error case;
4. `ProtosTestToolManifestPlanTest` validates the retained manifest-plan
   machinery;
5. `ProtosMatchBytecodeExecutionTest` validates the retained host/runtime
   secondary owner;
6. `scripts/source_style_guard.py` validates the candidate delta;
7. the owning TEST001-D closure retains responsibility for broader integrated
   validation before D closes.

No full Maven suite or full TOOL002 corpus run is required for this bounded
ownership-retirement child.

### TEST001-D2 — Array-pattern semantic ownership

Status: CLOSED

D2 reconciles the Array-pattern conformance family without deleting legitimate
host/runtime representation coverage.

Before D2, `ProtosArrayMatchExecutionTest` was a mixed owner:

- one method directly re-executed seven ordinary `.protos` fixtures whose exact
  boolean expectations are already present in the TOOL002 main manifest;
- one method inspected host-visible materialization details of remainder Arrays:
  represented frozen state, standard Array prototype, fresh container identity
  and retained element identity.

D2 removes only the duplicate semantic-wrapper method. The Java class remains,
now explicitly host/runtime-only.

The registered semantic contract is `language.match.array-patterns` with TOOL002
as primary owner for the seven observable Array-pattern cases.

`ProtosArrayMatchExecutionTest` and `ProtosMatchBytecodeExecutionTest` remain as
`HOST_RUNTIME` secondary evidence. They cover implementation representation and
Bytecode backend parity/continuation behavior, not a second primary semantic
policy.

D2 does not change Array-pattern semantics, grammar, fixtures, manifest contents,
runtime implementation, TOOL002 behavior or implementation version.

#### TEST001-D2 focal validation profile

1. baseline `ProtosArrayMatchExecutionTest` proves both portions of the mixed
   owner before editing;
2. structural guards prove the seven TOOL002 manifest rows remain present exactly
   once and that the semantic wrapper method is absent after editing;
3. `scripts/test_ownership_guard.py` validates the reconciled ownership record;
4. post-edit `ProtosArrayMatchExecutionTest` validates the retained host
   representation contract;
5. `ProtosMatchBytecodeExecutionTest` validates the retained Bytecode host/runtime
   evidence over the same fixture family;
6. `scripts/source_style_guard.py` validates the candidate delta;
7. TEST001-D top-level closure retains responsibility for broader integrated
   validation.

No full Maven suite or full TOOL002 corpus run is required for this bounded
mixed-owner reduction.

### TEST001-D3 — Map-pattern semantic ownership

Status: CLOSED

D3 reconciles the Map-pattern family using the same mixed-owner split established
by D2.

Before D3, `ProtosMapMatchExecutionTest` contained:

- one semantic wrapper that directly re-executed eight ordinary `.protos`
  Map-pattern fixtures already present in the TOOL002 main manifest;
- one host/runtime representation test for materialized remainder Maps, including
  represented frozen state, standard Map prototype, fresh container identity,
  retained key/value identity and recorded-hash preservation.

D3 removes only the duplicate semantic-wrapper method. The Java class remains as
explicit host/runtime evidence.

The registered semantic contract is `language.match.map-patterns` with TOOL002 as
the primary owner for the eight observable Map-pattern cases.

`ProtosMapMatchExecutionTest` and `ProtosMatchBytecodeExecutionTest` remain as
`HOST_RUNTIME` secondary evidence for representation details and Bytecode
backend/continuation invariants.

D3 changes no Map-pattern semantics, grammar, fixtures, manifest rows, runtime
implementation, TOOL002 behavior or implementation version.

#### TEST001-D3 focal validation profile

1. baseline `ProtosMapMatchExecutionTest` proves both portions of the mixed owner;
2. structural guards prove all eight TOOL002 manifest rows remain present exactly
   once and the semantic wrapper method is absent after editing;
3. `scripts/test_ownership_guard.py` validates the reconciled ownership record;
4. post-edit `ProtosMapMatchExecutionTest` validates retained host representation;
5. `ProtosMatchBytecodeExecutionTest` validates retained Bytecode host/runtime
   evidence;
6. `scripts/source_style_guard.py` validates the candidate delta;
7. TEST001-D top-level closure retains responsibility for broader integrated
   validation.

No full Maven suite or full TOOL002 corpus run is required for this bounded
mixed-owner reduction.

### TEST001-D4 — Guard-pattern semantic ownership

Status: CLOSED

D4 reconciles Guard-pattern semantics while retaining the Java checks that
observe host/runtime properties stronger than the ordinary TOOL002 result
carrier.

Before D4, `ProtosGuardMatchExecutionTest` contained:

- one wrapper that directly re-executed seven ordinary successful Guard-pattern
  fixtures already present in the TOOL002 main manifest;
- an invalid-guard test that observes both the Error family and activation state
  to prove that a later arm was not retried;
- a terminal-no-match test that executes twice and proves fresh Error object
  identity in addition to the public Error outcome.

The TOOL002 main manifest already owns all nine public outcomes: seven successful
boolean cases and the two Error cases. D4 therefore removes only the seven-case
semantic wrapper and its private execution helper.

The registered semantic contract is `language.match.guard-patterns` with TOOL002
as primary owner for all nine observable results.

`ProtosGuardMatchExecutionTest` remains as `HOST_RUNTIME` secondary evidence for
exact Error parent/freshness and activation-state no-retry inspection.
`ProtosMatchBytecodeExecutionTest` remains separately as host/runtime evidence
for Bytecode backend and continuation behavior, but D4 does not rerun it because
D4 does not modify that owner.

D4 changes no Guard-pattern semantics, grammar, fixture, manifest row, runtime
implementation, TOOL002 behavior or implementation version.

#### TEST001-D4 focal validation profile

1. baseline `ProtosGuardMatchExecutionTest` proves the mixed owner before editing;
2. structural guards prove all nine TOOL002 manifest rows remain present exactly
   once and the seven-case semantic wrapper is absent after editing;
3. `scripts/test_ownership_guard.py` validates the reconciled ownership record;
4. post-edit `ProtosGuardMatchExecutionTest` alone validates the modified retained
   host/runtime owner;
5. `scripts/source_style_guard.py` validates the candidate delta;
6. unmodified secondary owners are not mechanically rerun merely because they
   are recorded as evidence;
7. TEST001-D top-level closure retains responsibility for broader integrated
   validation.

No full Maven suite, full TOOL002 corpus, Bytecode suite, Tool suite or CLI suite
is required for this bounded mixed-owner reduction.

### TEST001-D5 — fundamental Match semantic ownership

Status: CLOSED

D5 reconciles the successful fundamental Match contracts that were still
duplicated in `ProtosMatchExecutionTest`.

The three retired Java methods only re-executed ordinary `.protos` fixtures and
asserted their final `true` result:

- subject/arm order, matcher/body exactly-once and laziness;
- binder/wildcard zero/one-capture ABI;
- opaque matcher fixed/rest capture ABI.

Each fixture already self-checks the detailed behavior internally and each is
already selected exactly once by the TOOL002 main manifest. D5 therefore removes
those three Java wrapper methods and their now-unused execution helper.

The registered semantic contract is `language.match.fundamentals` with TOOL002 as
primary owner.

`ProtosMatchBytecodeExecutionTest` remains `HOST_RUNTIME` secondary evidence for
Bytecode backend parity/runtime behavior over the same fixtures. It is not
rerun by D5 because D5 does not modify it.

D5 deliberately does NOT reconcile the remaining Match error tests yet.
`noMatchSignalsFreshGenericErrors`,
`invalidAndEmptyMatcherOutcomesSignalGenericError`, and
`selectedArmArityFailureDoesNotRetryLaterArm` retain stronger Java-observed
properties such as Error parent/freshness and activation-state no-retry. Those
remain for a separate bounded audit rather than being weakened to the manifest's
plain `error` carrier.

D5 changes no Match semantics, grammar, fixture, manifest row, runtime
implementation, TOOL002 behavior or implementation version.

#### TEST001-D5 focal validation profile

1. baseline `ProtosMatchExecutionTest` proves the mixed owner before editing;
2. structural guards prove all three TOOL002 manifest rows remain present exactly
   once and the three semantic wrapper methods are absent after editing;
3. `scripts/test_ownership_guard.py` validates the reconciled ownership record;
4. post-edit `ProtosMatchExecutionTest` alone validates the modified retained
   Match error host/runtime checks;
5. `scripts/source_style_guard.py` validates the candidate delta;
6. unmodified Bytecode, Tool and CLI tests are not rerun;
7. TEST001-D top-level closure retains responsibility for broader integrated
   validation.

No full Maven suite or full TOOL002 corpus run is required for this bounded
mixed-owner reduction.

## Migration order established by A

The dependency/order for remaining slices is:

1. `TEST001-D` — reconcile Core/language semantic Java owners against existing or
   newly required TOOL002 fixtures;
2. `TEST001-E` — Process/Task/Future/Actor/Group/cancellation public semantics;
3. `TEST001-F` — Standard Library public behavior;
4. `TEST001-G` — package/module/I/O higher-level integration;
5. `TEST001-H` — extracted portable-distribution Test Tool validation;
6. `TEST001-I` — final duplicate-owner retirement, bootstrap-floor audit,
   reporting/documentation reconciliation and closure.

B now precedes broad owner retirement in the published history: the repository
proves that TOOL002 can fail CI directly and the former full-corpus JUnit wrapper
has been narrowed to an independent pre-corpus bootstrap floor.

## Per-test retirement rule

No Java test is deleted solely because this inventory labels its family a
migration candidate. Before retiring an individual Java owner, its slice must
record all of the following:

1. the exact observable contract asserted by the Java test;
2. the TOOL002 fixture/plan that proves an equivalent or stronger contract;
3. whether any remaining Java assertion proves a distinct host invariant;
4. focal validation of the new/retained TOOL002 coverage;
5. the required broader repository validation for that slice.

If one Java class mixes public semantics and host mechanics, split ownership or
retain the host portion rather than deleting the whole class mechanically.

## Duplicate ownership definition

Duplicate ownership means two test paths independently encode the same public
semantic policy as primary authority. It does **not** mean that one public
conformance test and one host/runtime implementation test happen to exercise the
same feature.

During a bounded migration slice, temporary overlap is permitted until the new
TOOL002 path is green. The slice closes only after the obsolete semantic owner is
removed or an explicit distinct-host-invariant reason for retention is recorded.

## Decision boundary

TEST001-A found no missing Protos semantic or durable platform architecture
choice. It therefore opens no Dxxx or PLATxxx.

If a later migration needs new observable test semantics, assertion APIs,
resource behavior, scheduler guarantees, timeout/retry policy or other product
behavior, the affected slice stops and routes that question through the normal
Dxxx/LIBxxx/TOOLxxx/PLATxxx approval gate.

## Slice status

| Slice | Status | Meaning |
|---|---|---|
| TEST001-A | CLOSED | Current suite classified at durable ownership-family level; per-test retirement rule fixed. |
| TEST001-B | CLOSED | CI invokes packaged checkout `bin/protos test --jobs 2` directly; JUnit retains only a pre-corpus bundled-tool bootstrap floor. |
| TEST001-C | CLOSED | Machine-readable semantic ownership ledger and fail-closed no-duplicate-primary-owner guard are active in publication validation. |
| TEST001-D | IN_PROGRESS | Core/language semantic migration under the C ownership guard; D1 OR-pattern, D2 Array-pattern, D3 Map-pattern, D4 Guard-pattern and D5 Match-fundamentals ownership closed. |
| TEST001-E | BLOCKED_BY_D | Concurrency/execution-model semantic migration. |
| TEST001-F | BLOCKED_BY_E | Standard Library migration. |
| TEST001-G | BLOCKED_BY_F | Package/modules/I/O integration migration. |
| TEST001-H | BLOCKED_BY_G | Portable-distribution Test Tool validation. |
| TEST001-I | BLOCKED_BY_H | Final retirement/reconciliation/closure. |

## TEST001-A publication classification

`VALIDATION_CLASS=GOVERNANCE_DOCUMENTATION_ONLY`

No specification, executable implementation, Test Tool behavior, CI behavior,
implementation version or public compatibility contract changes in A.

## TEST001-B publication classification

`VALIDATION_CLASS=TEST_IMPACT`

CI configuration and Java bootstrap/architecture-test behavior change. Protos
specification, runtime implementation, Test Tool semantics, public compatibility
and Maven implementation version do not change.


## TEST001-C publication classification

`VALIDATION_CLASS=TEST_IMPACT`

Repository validation machinery, validation tests and ownership governance
change. Protos specification, executable runtime implementation, Standard
Library/Test Tool public behavior and Maven implementation version do not change.


## TEST001-D1 publication classification

`VALIDATION_CLASS=TEST_IMPACT`
`VALIDATION_IMPACT=FOCAL_BOUNDED`

D1 changes test ownership, one TOOL002 manifest row, the ownership registry and
the TEST001 migration record. It removes one duplicate Java semantic-policy
owner. It does not change specification, public behavior, runtime implementation,
CI configuration or implementation version.


## TEST001-D2 publication classification

`VALIDATION_CLASS=TEST_IMPACT`
`VALIDATION_IMPACT=FOCAL_BOUNDED`

D2 narrows one mixed Java test to host/runtime ownership and registers the
existing TOOL002 Array-pattern semantic owner. It changes no fixture, manifest
row, specification, public behavior, runtime implementation, CI configuration or
implementation version.


## TEST001-D3 publication classification

`VALIDATION_CLASS=TEST_IMPACT`
`VALIDATION_IMPACT=FOCAL_BOUNDED`

D3 narrows one mixed Java test to host/runtime ownership and registers the
existing TOOL002 Map-pattern semantic owner. It changes no fixture, manifest row,
specification, public behavior, runtime implementation, CI configuration or
implementation version.


## TEST001-D4 publication classification

`VALIDATION_CLASS=TEST_IMPACT`
`VALIDATION_IMPACT=FOCAL_BOUNDED`

D4 narrows one mixed Java test to host/runtime ownership and registers the
existing TOOL002 Guard-pattern semantic owner. It changes no fixture, manifest
row, specification, public behavior, runtime implementation, CI configuration or
implementation version.


## TEST001-D5 publication classification

`VALIDATION_CLASS=TEST_IMPACT`
`VALIDATION_IMPACT=FOCAL_BOUNDED`

D5 removes three Java semantic wrappers already owned by TOOL002 while retaining
the stronger Java Match-error tests unchanged. It changes no fixture, manifest
row, specification, public behavior, runtime implementation, CI configuration or
implementation version.
