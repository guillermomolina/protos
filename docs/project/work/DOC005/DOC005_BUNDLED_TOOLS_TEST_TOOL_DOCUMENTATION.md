# DOC005 — Bundled Tools and Test Tool documentation

GitHub coordination: Issue [#448](https://github.com/guillermomolina/protos/issues/448), labelled `family:DOC`.

Status: **IN_PROGRESS**

Validation class by default: **GOVERNANCE_DOCUMENTATION_ONLY**

## Purpose

DOC005 creates maintained programmer/user-facing documentation for the Protos
Bundled Tool model and for TOOL002 / the Test Tool in depth.

The work is explanatory. It must describe already-published behavior and must not
invent Tool semantics, reinterpret an unresolved decision, or promote current
Java/Truffle implementation details into public Tool contracts.

## Authority

The common Tool model is reconstructed primarily from:

- [`TOOLCHAIN_TOOL_ARCHITECTURE.md`](../../../design/TOOLCHAIN_TOOL_ARCHITECTURE.md);
- [`TOOL001`](../TOOL001/TOOL001_PACKAGE_TOOL.md), only as needed to explain the
  shared Tool boundary;
- [`TOOL002`](../TOOL002/TOOL002_TEST_TOOL.md);
- current bundled Tool source under `protos/tools/`;
- current CLI dispatch and bundled-tool resolver behavior; and
- ratified tooling decisions that have actually reached their owning executable
  implementation when a section describes current runnable behavior.

The Programming Guide remains non-normative. If documentation conflicts with an
owning specification, ratified Tool contract, retained conformance evidence or
current published executable surface, DOC005 must stop at the mismatch rather
than selecting a behavior inside documentation work.

## Documentation shape

DOC005 uses the DOC002-compatible split:

```text
docs/guide/tools/
  README.md       # common Bundled Tools model
  test-tool.md    # detailed TOOL002 user guide, published by later slices
```

The durable work record remains under `docs/project/work/DOC005/`.

## Slice ledger

| Slice | Status | Scope | Boundary |
|---|---|---|---|
| DOC005-A | CLOSED | Bundled Tools concept + maintained navigation | Publishes only the common Tool/Core/stdlib/host boundary, current Tool inventory and private-bootstrap explanation. No Test Tool command/result contract is newly defined. |
| DOC005-B | CLOSED | Test Tool fundamentals | Publishes current first use, four-plan corpus/expectation model, fresh-Process isolation, private captured output, deterministic logical ordering and published `--jobs` behavior. Commands/examples are checked against current sources/fixtures without running the Test Tool suite. |
| DOC005-C | BLOCKED_BY_TOOL006 | Resource-aware Test Tool execution | Current public `Main.protos` does not attach `resource-requirements.toml` before D108 scheduling; restore already-published wiring under TOOL006/#473 before documenting a runnable resource-backed example. |
| DOC005-D | CLOSED | Results, diagnostics, exit status and CI recipes | Documents current D108/D114/D116 + TOOL002-J result lanes, bounded diagnostics and CI consumption without introducing a structured-report schema. |
| DOC005-E | BLOCKED_BY_C | Consistency and closure | Wait for DOC005-C/TOOL006, then verify examples/commands/links, reconcile current `--help` and CLI behavior, reconcile DOC001-M, and absorb any published TOOL005 corpus-routing change before DOC005 closure. |

## DOC005-A publication

DOC005-A publishes
[`docs/guide/tools/README.md`](../../../guide/tools/README.md) and links it from
the maintained Programming Guide navigation.

The chapter establishes only already-selected/common behavior:

- one public `protos` driver can dispatch to exact bundled Tools;
- Package Tool and Test Tool are the two currently shipped bundled Tools;
- `protos/tools/shared/` is private toolchain support, not a third Tool;
- language/Core, Standard Library, bundled Tool policy and host/runtime mechanism
  are separate responsibility layers;
- bundled Tools are acquired from the selected toolchain rather than through the
  project package graph;
- Tool policy should normally live in ordinary Protos code while irreducible
  mechanisms/capabilities stay below it;
- `self:` and `tool-shared:` are explained only as confined/private Tool
  implementation structure, not public application API;
- driver operations such as REPL/run/debug/language-server are not relabelled as
  bundled Tools merely because they share the `protos` executable; and
- public Tool behavior must not be documented as JVM/Truffle-specific without an
  intentional public contract.

DOC005-A intentionally does not document detailed Package Tool behavior and does
not publish the detailed `protos test` command/result contract.

## DOC005-B publication

DOC005-B publishes
[`docs/guide/tools/test-tool.md`](../../../guide/tools/test-tool.md) from current
executable evidence rather than from superseded design sketches.

At this publication boundary, the Test Tool still selects exactly four explicit
repository/toolchain plans: primary conformance, Actor, Actor Group and Package
Tool TOML syntax. The guide therefore documents that current surface and states
explicitly that arbitrary working-directory discovery and the D122 suite graph
are not yet executable public behavior.

The fundamentals chapter also records the already-published execution contract:

- retained `manifest.tsv` plans select exact relative test sources plus
  expectations;
- each exact test execution runs in a fresh semantic Process;
- guest standard streams are captured privately per execution;
- `--jobs N` is positive logical outer test-case capacity, defaulting to one;
- `--jobs` does not redefine concurrency inside an individual test;
- bounded parallel execution preserves logical TestPlan/manifest result order;
- the exact separate-token spelling `--jobs N` is selected, while the historical
  TOOL002 projection continues to ignore unknown/attached forms such as
  `--jobs=N`.

The examples are source/fixture checked. DOC005-B deliberately does **not** run
`protos test` as validation because that command is itself the executable test
suite prohibited for this documentation-only slice.

## DOC005-C audit blocker — TOOL006 / #473

The DOC005-C source audit found an executable mismatch and therefore stopped
instead of documenting a candidate or historical intent as current behavior.

The durable TOOL002-I record says I8D5C completed the public resource-aware
Main/CLI cutover for all four owned plans. The current published `Main.protos`,
however, loads those plans directly through `Manifest` and sends them to the
D108 resource-aware Runner without importing or invoking
`ResourceRequirements.loadFromCorpus(...)`.

The already-published components still exist:

- exact `resource-requirements.toml` discovery and strict parsing;
- D091 full-plan referential attachment;
- resource catalog parsing;
- requirement/catalog binding;
- atomic reservation;
- provider request projection and resourceful execution;
- D108 terminal/infrastructure handling.

What is missing is the public plan-side attachment step that makes those
requirements reach the scheduler.

This is tracked by
[`TOOL006 / #473`](https://github.com/guillermomolina/protos/issues/473).
DOC005-C is therefore **BLOCKED_BY_TOOL006**. Documentation must not provide the
issue-required complete resource-backed runnable example until that wiring is
restored and published.

## DOC005-D publication

DOC005-D extends
[`docs/guide/tools/test-tool.md`](../../../guide/tools/test-tool.md) with the
current published result, diagnostic, exit-status and CI contract.

The guide now explains:

- D116 completed aggregation across every actually executed owned plan;
- exit `0` for completed all-healthy execution;
- exit `1` for completed ordinary guest/test failure;
- D114/D108 `infrastructure-aborted` as a separate outer outcome with exit `3`
  and precedence over earlier guest failures;
- preservation of already-produced guest CaseRuns and separation from
  infrastructure evidence;
- the current bounded infrastructure-abort stderr summary;
- unexpected Test Tool/runtime failure as the separate exit-`70` lane;
- the practical distinction between a completed test-failure `1` and a
  Tool/configuration Error that is also currently reported by the host as
  `Test tool error: ...` with exit `1`;
- progress on the Tool stderr channel while guest stdout/stderr remain private;
- fail-closed CI use of `bin/protos test --jobs 2`; and
- a shell classification recipe that consumes exit status rather than physical
  completion order.

No machine-readable report schema, retry/flaky policy, timeout/recovery,
remote-worker protocol or event-stream API is invented.

Validation is source/static only. Command examples are checked against the
current CLI/Runner/Progress sources but are not executed because `protos test`
is itself the executable suite excluded from this documentation-only slice.

## D122 / TOOL005 boundary

D122 is ratified and releases TOOL005 to implement an explicit composable Test
Tool suite graph.

That ratification is architectural authority for TOOL005, but it is **not** by
itself current executable Test Tool behavior. DOC005 must therefore distinguish:

```text
ratified future/owning architecture
            !=
published current user behavior
```

DOC005-B documents the currently published four-plan executable surface.
DOC005-C/D may add the Test Tool resource/result surfaces that are already
published. DOC005-E must reconcile whatever TOOL005 has actually published by
closure time, especially corpus/suite selection, without teaching an
unimplemented suite graph as though users can already invoke it.

## Validation policy

Pure DOC005 changes are documentation-only by default.

For such slices:

- do not run `mvn test`, `make test`, a focal Maven suite or the full executable
  suite;
- do not bump the implementation version or specification revision;
- run `git diff --check`;
- validate repository-relative links in touched documentation;
- inspect the current owning source/records needed to confirm command spelling,
  inventory and examples;
- when a later slice contains an executable example, exercise that individual
  example/command where practical without converting the documentation slice
  into a Maven-suite run;
- ensure touched files remain documentation/governance surfaces only; and
- if checking an example exposes a real implementation/contract mismatch, stop
  that section and route the discrepancy to the owning TOOL/Dxxx work.

The validation class escalates only if a DOC005 slice actually changes an
executable, specification, test, build or other validation-relevant surface.

## Closure rule

DOC005 closes only when:

1. the common Bundled Tools model is documented and discoverable;
2. the Test Tool guide covers the complete published public surface;
3. resource-aware execution and final result/diagnostic behavior are described
   from ratified **and published** authority;
4. executable examples/commands have been checked individually where practical;
5. current TOOL005 corpus-routing behavior, if published, is reconciled;
6. DOC001-M can reference/reconcile DOC005 without duplicating the same material;
   and
7. final navigation/link consistency is green.

DOC005-A, DOC005-B and DOC005-D are **CLOSED**. The parent remains
**IN_PROGRESS**. DOC005-C is **BLOCKED_BY_TOOL006 / #473**, and final
consistency/closure remains deferred to DOC005-E after C is released.
