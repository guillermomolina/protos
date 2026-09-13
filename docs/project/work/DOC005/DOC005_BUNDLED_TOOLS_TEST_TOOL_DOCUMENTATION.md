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
| DOC005-B | READY | Test Tool fundamentals | First use, current corpus/expectation model, isolation, private output, deterministic reporting and published `--jobs` behavior. Examples must be checked against current executable sources/fixtures. |
| DOC005-C | READY | Resource-aware Test Tool execution | Requirements/catalog/provider/profile, admission/reservation and one complete resource-backed example from closed TOOL002-I authority. |
| DOC005-D | READY | Results, diagnostics, exit status and CI recipes | Consume the ratified/published D108/D114/D116 + TOOL002-J result boundary; no pre-emptive new reporting semantics. |
| DOC005-E | BLOCKED_BY_B_C_D | Consistency and closure | Verify examples/commands/links, reconcile current `--help` and CLI behavior, reconcile DOC001-M, and absorb any published TOOL005 corpus-routing change before DOC005 closure. |

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

DOC005-B/C/D may document the Test Tool surface that is already published.
DOC005-E must reconcile whatever TOOL005 has actually published by closure time,
especially corpus/suite selection, without teaching an unimplemented suite graph
as though users can already invoke it.

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

DOC005-A closes only the common Tool-model slice. The parent remains
**IN_PROGRESS**.
