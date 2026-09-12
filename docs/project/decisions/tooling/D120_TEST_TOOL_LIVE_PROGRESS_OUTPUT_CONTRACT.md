# D120 — Test Tool live progress output contract

Status: **RATIFIED — Candidate E selected**

Owning work item: `TOOL004` / GitHub Issue `#454`
Decision issue: GitHub Issue `#455`

## Decision

`protos test` reports live runner progress as **plain UTF-8 line-oriented
diagnostics on `stderr`**, while preserving guest/test output isolation and all
existing result/exit semantics.

The selected baseline is **Candidate E — deterministic completion milestones on
stderr + immediate failures + phase summaries**.

Progress is derived from Test Tool-owned execution state. It is not inferred from
guest stdout/stderr and does not require tests themselves to emit anything.

## Required observable behavior

For every Test Tool plan (`main`, `actor`, `group`, and `package-toml`):

1. emit a phase-start progress line containing the selected case count;
2. advance progress only when cases reach the existing terminal-result boundary;
3. emit a bounded deterministic set of completion milestones derived from the
   selected case count;
4. report a failing terminal case immediately with its exact Test Tool case
   identity rather than waiting for the next milestone;
5. emit one phase-completion summary; and
6. emit one final aggregate summary across the invocation.

The normal baseline targets approximately ten intermediate progress updates per
non-trivial phase. The exact integer milestone formula is implementation detail
only if it preserves all of these laws:

- deterministic from the selected case count;
- independent of wall-clock scheduling;
- monotonic;
- bounded independently of suite size; and
- always includes terminal completion.

A later change that materially alters the public reporting shape or semantics
must use the normal explicit decision gate.

## Output authority

Live progress/report diagnostics belong to:

```text
process.stderr()
```

The decision does not grant progress reporting access to captured guest output.

Guest stdout/stderr remain private to the individual case execution boundary
except where the existing Test Tool result/diagnostic contract already exposes
them.

The baseline does not move Test Tool progress to stdout and does not merge guest
streams with runner diagnostics.

## Completion semantics

A progress counter represents **terminal/completed work**, not admission.

In particular, under `--jobs N`:

```text
admitted != completed
```

A case contributes to progress only after the existing Test Tool attempt/result
machinery has reached its terminal boundary.

Therefore progress must not make a queued, admitted, executing, suspended or
resource-blocked case appear completed.

This decision does not change:

- admission;
- scheduling;
- `--jobs N`;
- resource accounting;
- Process/Actor/Task semantics;
- retry/failure semantics;
- result ordering; or
- manifest ordering.

## Parallel execution

Parallel terminal completions may arrive in an execution-dependent order.

The progress mechanism may count those completions as they occur, but:

- final Test Tool result authority remains unchanged;
- manifest/result ordering remains unchanged;
- progress rendering must not serialize guest execution except for the minimal
  emission boundary needed to keep diagnostic lines coherent; and
- a progress event is observational only.

The progress reporter must not become a scheduler, barrier or ownership source.

## Failure visibility

A failing terminal case is reported immediately rather than being hidden until
the next deterministic milestone.

The failure progress record identifies the exact Test Tool case using the
existing case identity model.

Immediate failure visibility does not imply fail-fast behavior. D120 changes no
continuation/abort policy after a test failure.

Infrastructure-abort paths must preserve enough last-known phase/progress
information to identify where the invocation stopped.

## Presentation

The ratified baseline is intentionally small:

```text
Protos test
[main] 0/888
[main] 89/888
[main] 178/888
...
[main] 888/888 passed
[actor] 0/11
[actor] 11/11 passed
[group] 10/10 passed
[package-toml] 102/102 passed
1011 passed, 0 failed
```

This example is illustrative of the selected presentation family. Exact wording,
spacing and milestone arithmetic may be fixed by TOOL004 implementation evidence
provided they remain deterministic and obey this decision.

The baseline is:

- line-oriented;
- append-only;
- plain UTF-8;
- readable in local terminals and CI logs;
- free of terminal cursor manipulation; and
- bounded for suites with thousands of cases.

## Deliberately rejected baseline features

D120 does **not** authorize:

- TTY detection;
- `isatty` or an equivalent new runtime/host capability;
- ANSI color or cursor control;
- a spinner or progress-bar framework;
- terminal-width discovery;
- `--quiet`;
- `--verbose`;
- JSON progress;
- machine-readable event streaming;
- a third-party terminal UI dependency;
- per-passing-test output for the complete suite;
- a wall-clock heartbeat;
- scheduler changes; or
- performance/runtime architecture changes.

These remain additive future work if a concrete consumer justifies them.

## Why not TTY-adaptive presentation now

Mature tools commonly provide rich interactive consoles and plain CI output.
That is a valid future direction.

Protos currently has no ordinary Test Tool TTY capability, and TOOL004 does not
need one to solve the actual problem: a multi-minute invocation must visibly
demonstrate forward progress.

Adding host terminal detection solely for presentation would create platform
surface unrelated to test correctness. A deterministic line-oriented stderr
contract solves the current need with less mechanism and better portability.

## Why not emit every passing test

The current suite already contains more than one thousand selected cases.

One line per successful case makes log volume grow linearly with suite size and
creates noisy CI output while adding little information after liveness is
established.

D120 instead selects bounded deterministic milestones. Failures remain immediate.

## Why not use a time heartbeat

A wall-clock heartbeat can prove liveness, but it is scheduling-dependent and can
emit repeated identical counts while one test is slow.

The selected contract ties normal progress to semantically meaningful terminal
completion and keeps output deterministic from the plan plus its terminal
results.

## Internal event boundary

TOOL004 should keep the runner-to-presentation boundary **presentation-neutral**.

The internal mechanism may expose events such as:

```text
phaseStarted
caseTerminal
phaseCompleted
invocationCompleted
```

without making this particular textual renderer the scheduling authority.

That boundary is intentionally reusable by a future verbose, IDE or structured
reporter if such a consumer is later approved.

D120 does not itself publish that internal event representation as a language or
Standard Library API.

## Timing information

Elapsed timing may be displayed as diagnostic information only if TOOL004 can do
so without changing correctness or adding a new authority dependency.

Timing values never participate in:

- pass/fail;
- milestone selection;
- ordering;
- scheduling; or
- exit status.

A first implementation may omit elapsed timing while still fully satisfying
D120.

## Prior-art basis

The comparative audit considered materially different reporting approaches from:

- pytest;
- Gradle;
- Cargo / the Rust test harness;
- Go `test`;
- Jest; and
- Maven Surefire.

The recurring useful separation is:

```text
test-owned output
    !=
runner-owned progress/reporting
```

Mature tools also demonstrate that quiet/verbose, structured streams and rich
TTY rendering can be layered later without requiring test semantics to own those
features.

Candidate E scored:

```text
Aguante de futuro: 10 / 10
Escalabilidad:      10 / 10
Filosofía Protos:   10 / 10
Observabilidad:      9 / 10
Simplicidad:         9 / 10
Total:              48 / 50
```

## Strongest argument against

A TTY-aware single-line progress bar is visually cleaner for interactive use and
can expose richer active-work state without adding many physical log lines.

The project deliberately does not pay that platform/terminal cost yet. The
selected line-oriented contract already solves local and CI observability and
keeps the execution/presentation boundary simpler.

## Future-regret scenario and escape path

A future IDE, CI service or external tool may require machine-readable live test
events. Parsing human text would then be the wrong integration boundary.

The escape path is additive:

1. retain TOOL004's presentation-neutral internal progress event boundary;
2. approve a structured reporter when a real consumer exists; and
3. render the same execution events as JSON or another structured protocol
   without changing Test Tool scheduling or semantic result authority.

Likewise, a future terminal-specific renderer may be added separately without
invalidating the plain line-oriented baseline.

## Consequence

D120 releases `TOOL004-B` to implement the smallest internal terminal-progress
event boundary and then `TOOL004-C` to render the approved stderr presentation.

Implementation must retain:

- guest stdout/stderr isolation;
- deterministic final result authority;
- bounded diagnostic volume;
- parallel safety;
- unchanged `--jobs N` semantics;
- unchanged result/exit semantics; and
- no hidden scheduler/runtime coupling.

If implementation exposes a new substantive semantic, CLI, runtime, terminal or
architecture choice, the affected slice stops and uses the normal Dxxx/PLATxxx
approval gate before publication.
