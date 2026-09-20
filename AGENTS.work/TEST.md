# Protos test work

These instructions apply to `TESTxxx` and to test authoring in any family.

## Test placement

Tests SHOULD demonstrate observable Protos behavior at the highest useful
level. When behavior can be tested faithfully through ordinary Protos source
and the bundled Test Tool, prefer Protos-owned conformance or regression
coverage. Java/JUnit remains appropriate when the primary evidence is Java,
Truffle, compiler/backend, runtime, scheduler, native/host integration, or an
independent bootstrap boundary that cannot be proved by the Test Tool alone.

Do not keep duplicate Java and Protos tests for the same semantic policy:
retained Java coverage is appropriate only when it proves a materially
different host/runtime/bootstrap invariant, not merely because JUnit is
easier or the implementation is Java. Do not contort a Protos test to reach a
purely internal Java contract.

This placement rule does not require a global semantic-ownership registry,
ownership manifest, or publication guard. The owning change and its tests
provide the evidence for why coverage belongs in one lane or is intentionally
split.

Apply the shared implementation discipline in `AGENTS.work/IMPLEMENTATION.md`
for validation and publication of test changes.

## Testing philosophy

Tests validate current Protos semantics, not implementation accidents. Prefer
small semantic regressions and workloads proportional to the correctness
property being proved.

Do not keep benchmark/stress/profiling/diagnostic-scale workloads in ordinary
tests when a smaller evidence-backed workload proves the same property; route
larger workloads to their purpose-specific surfaces.

Do not weaken semantic coverage, replace required integration evidence with
mocks, skip failures, or add production fast paths merely to reduce test cost.
Aggregate validation may legitimately be expensive because it composes many
checks; optimize/reclassify unnecessarily expensive constituents instead of
weakening the aggregate contract.

When fixing a semantic bug, add/update a focused regression when tests are part
of the work. Where useful, exercise the same semantic rule through multiple
execution paths that could diverge. Never weaken a test to make incorrect
implementation pass; if a test contradicts the current specification, report
that contradiction instead of treating the test as authoritative.

## Protos test assertion authoring
<!-- LIB016 NEW-PROTOS-TEST-ASSERTIONS -->

New Protos tests MUST NOT define local assertion helpers that duplicate the
semantics of the ratified `std:test/Assertions` operations. When a new test needs
assertion-style Boolean failure, use `Assertions.require(condition)`. When it
needs to assert that a body signals an Error matching a prototype, use
`Assertions.signals(errorPrototype, body)`.

This rule does not require every Protos test to import `std:test/Assertions`.
Tests whose expected outcome is naturally expressed by the program's final value
or by the corpus manifest/TestPlan expectation may continue to use those forms.
Do not wrap them in assertions merely for stylistic uniformity.

Existing Protos tests that predate this rule are not required to migrate
opportunistically during unrelated work. Any repository-wide reconciliation of
those tests must be handled as separately tracked work.

Legacy Java/JUnit tests that predate this rule are not required to migrate
opportunistically during unrelated work. Their bounded repository-wide
reconciliation is owned separately by TEST002.

## TOOL009 M2 test ownership for newly added Protos tests
<!-- TOOL009 M2 NEW-TEST-OWNERSHIP -->

During D152 M2, production-test ownership is explicit per source rather than by
a manually maintained domain allowlist.

- A manifest source marked `suite-native` is owned by the suite-native D153/D152
  path. The same bounded migration change MUST introduce or preserve its
  `std:test/Test` declaration surface and MUST remove that source from incumbent
  execution ownership so it cannot run twice.
- A production source that is not marked `suite-native` retains its incumbent
  owner until a bounded TOOL009 M2 migration changes that source.
- New production conformance sources added while M2 is active MUST use the
  suite-native authoring model: import `std:test/Test`, expose a finite frozen
  `tests` Array of named Tests, keep test behavior inside Test bodies rather
  than executing it during module discovery, and use the temporary
  `suite-native` manifest marker.
- A domain may therefore be partially migrated during one bounded slice without
  requiring an `AGENTS.md` edit for each family or directory. The manifest is
  the authoritative source-level ownership boundary during M2.
- Tests whose behavior completes through a Future MUST observe that Future
  explicitly inside the Test body rather than returning it for the Test Tool to
  interpret.

JUnit-owned component fixtures under `protos/tests/tooling/` are not production
TestPlan ownership merely because they contain Protos source. A fixture that is
executed externally to prove discovery, scheduling, rematerialization, result
classification, bootstrap, host/runtime, or another independent component
invariant SHOULD remain externally owned unless separate work deliberately
changes that evidence boundary.

For a suite-native source, D153 discovery must remain observational with respect
to test execution: constructing/importing declarations is allowed, but test
behavior, mutable test state, expected failures, and other case effects belong
inside the selected Test body. The physical source file is not the logical Case
identity.

This section is temporary migration policy. M3/M4 must remove or replace it when
the incumbent production-test ownership path and `suite-native` migration marker
no longer exist.
