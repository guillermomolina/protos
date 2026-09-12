# AUD006 — LIB011 CommandLine post-closure scalability and hardening audit

Status: **IN_PROGRESS**

Owning work item: GitHub Issue `#453` — `AUD006 — LIB011 CommandLine post-closure scalability and hardening audit`

Target: `LIB011` / GitHub `#428` — public `std:cli/CommandLine`

Nature: durable project audit / corrective-hardening record; **non-normative**

Trigger closure: `LIB011-F`, commit `00ffead82ff15256c36a2405105e3454961d58cb`

## Authority and boundary

LIB011 remains closed. This audit does not reopen `LIB011/#428`, replace the
ratified architecture, or redefine D111, D115, D118 or D119.

The audit exists because a post-closure implementation review found one material
scalability/conformance discrepancy plus bounded hardening and documentation debt.

Current disposition:

```text
AUD006_STATUS=IN_PROGRESS
TARGET=LIB011
LIB011_ISSUE_428=KEEP_CLOSED
LIB011_ARCHITECTURE=KEEP
PUBLIC_API=KEEP
D111=KEEP
D115=KEEP
D118=KEEP
D119=KEEP
CORRECTIVE_FOLLOWUP_REQUIRED=YES
NEW_SEMANTIC_DECISION_REQUIRED=NO_CURRENTLY
```

If corrective work exposes a substantive semantic, API, Core-collection or
durable runtime-architecture choice, that work must stop and cross the normal
explicit decision gate. AUD006 is not authority to select such a choice.

## Audit verdict

The public architecture and observable semantics remain sound.

No functional defect was found in:

- D111 maximal feasible left-biased positional allocation with suffix-minimum
  reservation;
- D115 earliest feasible exact-child traversal, literal option-value ownership,
  current-scope `--` escape and irreversible child transfer;
- D118 pure deterministic help rendering;
- D119 `valueName` semantics;
- fresh/frozen structural specification/result data;
- lossless parse provenance for the published token forms;
- invocation-local parser state and absence of a global parser registry;
- LIB011-E separation between generic parser mechanism and TOOL002 policy.

The audit's primary issue is not token correctness. It is whether the current
implementation actually satisfies the complexity requirement already published
by D115.

## F1 — balanced Array chunk accumulation and the D115 linear target

**Classification:** performance/scalability conformance defect; highest priority.

D115 requires:

```text
time:   O(T + Svisited)
memory: O(result + Svisited)
```

where `T` is argument-token count and `Svisited` is specification material for
the selected command path.

The current parser uses invocation-local balanced chunk builders. A carry merges
two accumulated chunks by constructing:

```text
Array(...left, ...chunk)
```

This avoids naive whole-prefix rebuilding for every appended occurrence and is
substantially better than quadratic append.

However, current Core `Array(...)` construction creates a fresh
`ProtosArrayValue` and copies every supplied element into fresh indexed storage.
Consequently, values in a balanced chunk participate in copies at multiple
levels of the merge tree.

For `N` accumulated entries, the static copy-cost model is therefore
approximately:

```text
level 0: O(N)
level 1: O(N)
...
level log N: O(N)

=> O(N log N) copied element references
```

rather than `O(N)`.

The effect applies to option occurrence accumulation. For unbounded positional
input it may be paid once while collecting raw positional tokens and again while
building `PositionalOccurrence` results.

The retained LIB011-F stress case with 128 options and 256 argv tokens proves
functional behavior at moderate scale. It does not prove the required
asymptotic bound.

### F1 required action

AUD006-A must:

1. add retained scaling evidence capable of separating linear, `N log N`, and
   quadratic growth for repeatable options and large/unbounded positional input;
2. confirm the current balanced-chunk cost against the actual Array construction
   semantics;
3. identify the narrowest implementation strategy that really satisfies the
   D115 target;
4. preserve all observable LIB011 semantics;
5. stop for a Dxxx/PLATxxx decision if a new durable Core/collection primitive
   or architecture is required.

The audit must not "fix" this merely by weakening the documented complexity
claim without explicit approval.

## F2 — command-tree depth is recursion proportional

**Classification:** robustness/scalability hardening debt.

Two important paths are recursive with command-tree depth:

- `CommandLine.command(...)` recursively canonicalizes child command descriptors;
- `CommandLine.parse(...)` recursively enters each selected child scope.

Ordinary CLI trees are expected to be shallow, so the audit has not established
a normal-use functional defect. A degenerate or adversarial command tree can,
however, make available host/guest stack depth the practical bound.

### F2 required action

AUD006-B must:

- add retained depth stress evidence for canonicalization and selected traversal;
- establish the practical failure profile;
- mechanically replace input-proportional recursion where feasible without
  changing semantics;
- never invent a public maximum command depth silently.

A proposal for an observable depth limit or another new policy requires a
decision gate.

## F3 — public API documentation/discoverability

**Classification:** documentation debt.

The durable LIB011 design and decision records are detailed, but the ordinary
user-facing Standard Library path does not yet present a concise CommandLine
reference proportional to the quality of the implemented API.

The module header also describes only canonical specification records even
though the module now owns specification construction, parsing and canonical
help rendering.

AUD006-C must document the final public surface:

```text
std:cli/CommandLine

option(descriptor)
positional(descriptor)
command(descriptor)
parse(spec, arguments)
renderHelp(rootSpec, commandPath)
```

The public material should include:

- canonical `OptionSpec`, `PositionalSpec` and `CommandSpec` shapes;
- recursive `ParseResult` / `CommandResult` / occurrence shapes;
- `key` versus CLI spelling versus `valueName`;
- D111 positional examples;
- D115 subcommand and `--` examples;
- exact argument provenance;
- help rendering examples;
- the explicit authority boundary: no implicit `process.args()`, output,
  environment, filesystem, network or TTY access.

No semantic change is intended.

## F4 — canonical CommandSpec precondition

**Classification:** documentation/API-contract hardening debt; not a current bug.

`parse` and `renderHelp` intentionally consume an already-canonical CommandSpec.
They do not recursively re-run `CommandLine.command(...)` validation for every
invocation.

This is an important property: the command specification can be validated and
snapshotted once, then reused without whole-tree recanonicalization.

Because the public representation is ordinary structural Protos data, a caller
can manually forge a record such as:

```text
{
    kind: "command"
    ...
}
```

without having passed constructor invariants. That does not make the current
implementation incorrect: the public precondition is that the input is a
canonical CommandSpec.

AUD006-C must make this precondition explicit and difficult to miss.

The audit explicitly rejects silently changing `parse` or `renderHelp` to
accept/revalidate arbitrary command-like records. Such a change would alter both
the API contract and its cost model and therefore requires a separate decision.

## F5 — stale secondary closure references

**Classification:** governance/documentation reconciliation.

Secondary planning/reference documents still describe `cli` principally as
"Promoted: LIB011 / #428" under the initial C-prime architecture rather than as a
closed A–F public surface.

AUD006-C must reconcile those references to the final state while preserving the
authority split:

- ratified decisions remain decision evidence;
- `LIB011_COMMAND_LINE_DESIGN.md` remains the primary project design record;
- secondary roadmap/ideas documents remain summaries only.

## F6 — TOOL002 projected argv and original provenance

**Classification:** future integration risk; no current defect.

LIB011-E preserves TOOL002's historical unknown-argument behavior by projecting
only the exact recognized pairs:

```text
--jobs VALUE
--resource-catalog PATH
```

before passing the projected Array to strict `CommandLine.parse`.

Consequently, occurrence `tokenIndex` values produced inside that parse refer to
the projected Array rather than original `process.args()` positions.

TOOL002 currently consumes only the option values. It does not expose or rely on
those occurrence token indices, so existing observable behavior remains correct.

AUD006-C must record this as an adapter boundary, not as a defect requiring
speculative implementation work.

If a future TOOL002 diagnostic needs original argv provenance, that work must
preserve or explicitly remap the original indices rather than treating projected
indices as original provenance.

## Confirmed strengths and deliberate non-goals

The following are confirmed strengths and must not be reopened merely because
AUD006 exists:

- canonical specification/result records are ordinary inspectable Protos data;
- constructor outputs and retained Arrays are fresh and frozen;
- logical keys are independent from CLI spellings;
- value presentation is independent through `valueName`;
- argument input is explicit rather than ambient;
- attached long values, clusters and literal `--` retain the intended
  provenance;
- parent/child scopes remain explicit;
- help is pure and presentation-only;
- no global parser cache/registry exists;
- TOOL002 keeps its own policy outside the generic parser.

The following remain deliberate future additive work, not audit defects:

- public structured parse errors;
- typed decoding/default projection;
- transparent remainder/foreign-CLI forwarding;
- completion APIs;
- automatic help/version actions;
- ANSI/color/TTY integration and wrapping;
- localization;
- environment/config merging;
- command execution/dispatch.

If pursued, those features require their own evidence and any substantive new
semantics must cross the normal decision gate.

## Work decomposition

### AUD006-A — complexity proof and linear-accumulation remediation

Status: **READY**

Required sequence:

1. establish retained evidence for current growth;
2. distinguish linear / `N log N` / quadratic behavior;
3. cover repeatable options and unbounded positionals;
4. evaluate the narrowest existing-mechanism remediation;
5. implement only if no new substantive architecture is required;
6. otherwise stop and open the appropriate decision;
7. retain regression and integrated validation evidence.

The first A slice is evidence-first. It must not introduce a new collection
primitive simply to make the audit pass.

### AUD006-B — depth/robustness hardening

Status: **BLOCKED_BY_A**

Required work:

- stress deep canonical command trees;
- stress deep selected parse paths;
- determine actual recursion risk;
- make traversal iterative mechanically where possible;
- retain exact shallow-tree behavior;
- open a decision gate before introducing a public depth policy.

### AUD006-C — public documentation and governance reconciliation

Status: **READY_INDEPENDENT**

Required work:

- publish/reconcile a concise public `std:cli/CommandLine` reference;
- make the canonical-spec precondition explicit;
- update the module description to include parse/help responsibilities;
- reconcile stale secondary LIB011 references;
- document TOOL002 projected-index provenance as a future adapter concern.

### AUD006-D — final closure re-audit

Status: **BLOCKED_BY_A_B_C**

Final closure must:

- re-check D111, D115, D118 and D119 against production implementation;
- rerun retained LIB011 semantic/adversarial evidence;
- run the required complete integrated validation;
- verify no hidden authority/global registry/native parser bridge was added;
- verify complexity and depth claims are supported by retained evidence;
- state explicitly whether the closed LIB011 architecture remains unchanged.

## Governance rules

1. `LIB011/#428` remains CLOSED while AUD006 executes.
2. Existing ratified decisions remain authoritative.
3. Already-determined mechanical corrections may proceed without a new Dxxx only
   while no substantive semantic/API/runtime/collection architecture is selected.
4. A new durable architectural choice stops the affected slice and crosses the
   normal explicit owner-approval gate.
5. AUD006 must not turn intentionally deferred features into defects.
6. Documentation/governance-only audit bookkeeping does not require Maven tests.
7. Executable corrective slices use the repository's impact-aware validation
   rules, with full integrated validation at AUD006-D closure.

## Closure criteria

AUD006 may close only when:

- F1 has retained evidence and D115's linear target is actually met, or an
  explicitly approved successor decision changes that requirement;
- F2 has retained depth evidence and confirmed unsafe recursion is corrected or
  explicitly governed;
- F3/F4 public documentation and the canonical-spec precondition are reconciled;
- F5 secondary durable references are reconciled;
- F6 is explicitly retained as a non-defect future adapter boundary;
- required integrated validation is green on the final state;
- no unresolved semantic/architectural choice is hidden inside audit work;
- the final record states whether LIB011 architecture and public API remained
  unchanged.

Initial state:

```text
AUD006_STATUS=IN_PROGRESS
AUD006_A_STATUS=READY
AUD006_B_STATUS=BLOCKED_BY_A
AUD006_C_STATUS=READY_INDEPENDENT
AUD006_D_STATUS=BLOCKED_BY_A_B_C
LIB011_ISSUE_428=KEEP_CLOSED
LIB011_SEMANTICS=KEEP
PRIMARY_FINDING=LINEAR_COMPLEXITY_TARGET_NOT_CURRENTLY_MET_BY_STATIC_COST_MODEL
CORRECTIVE_IMPLEMENTATION_ALLOWED=MECHANICAL_ONLY
DECISION_GATE_ON_NEW_ARCHITECTURE=REQUIRED
```
