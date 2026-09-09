# I026-A4B3 — Production driver cutover and legacy-entry retirement

Status: **IN_PROGRESS**

Live coordination: GitHub Issue #89 (I026-A4B3), parent Issue #42, under current GITHUB001 coordination
Platform authority: PLAT001 (RATIFIED)
Entry dependency: I026-A4B2 (CLOSED in `0.2.280-SNAPSHOT`)

## Coordination / authority boundary

This file is durable repository evidence for the one existing I026-A4B3 work item. GitHub Issue #89 owns live operational coordination for this durable sub-item, with Issue #42 remaining the I026 parent. The current GitHub coordination policy classifies the existing work item with `family:I`; no new identifier is allocated here. The execution phases below are mechanical
implementation planning inside I026-A4B3; they are not new durable work-item identifiers or GitHub
sub-Issues unless a later phase independently satisfies the repository's durable-granularity rule.

`docs/project/OPEN_TASKS.md` is a retired historical backlog snapshot and
`docs/project/IMPLEMENTATION_STATUS.md` is a durable implementation/closure-evidence registry under
GITHUB001-F. Neither is live scheduling/status authority, and neither is modified by this cutover.

## Published phase — ordinary CLI and REPL entry

Published in `0.2.287-SNAPSHOT`: each ordinary CLI/REPL session creates one explicit non-global
`ProtosPolyglotRuntimeHost`, binds its exact semantic Process once to one
`ProtosPolyglotProcessContext`, and routes `-e`, file and persistent REPL Sources through that
Process-scoped Context.

The semantic Process is still created by `ProtosStandaloneProcessBootstrap`. One-shot `-e` and file
execution parse and execute as RootActor tasks through the entered Process Context. REPL units parse
and call Context-owned CallTargets inside that same Process Context while deliberately retaining the
historical persistent task-free top-level activation: creating a fresh RootActor task per REPL line
would try to reattach one persistent activation to multiple tasks and is not the REPL execution model.

Polyglot `Context` input/output streams are the same host streams selected by the CLI, while
Protos-visible Process streams remain the already-existing explicit semantic bindings. No new
ambient Protos I/O authority is introduced. Process termination remains authoritative. After a successful one-shot root task (or task-free
persistent REPL unit), session teardown requests semantic Process termination; the already-published
Process/host binding closes its exact Context when semantic termination completes, and only then may
the explicit Engine owner close. No additional polling, termination-drain scheduler, or host-side
semantic authority is introduced. The A4B2 invariant therefore remains intact: Engine close never
implicitly terminates a live Process.

The file launcher constructs a Truffle `Source` with the normalized file URI instead of reducing
the file to an anonymous string. `-e` and REPL units use explicit synthetic source names only.

Nested roots retain the exact Source identity captured by parsing. Language-bound Closure
parameter/body roots and object-literal body roots defer RootNode/CallTarget materialization until
first execution; materialization resolves the exact current `ProtosLanguage` from the already-entered
Process Context and constructs the nested RootNode there instead of reusing the parser's earlier
sharing-layer association. Direct/source-only staging templates preserve their published eager
construction when no Polyglot Context is entered. If such a source-backed Closure is later invoked
inside an entered Process Context (notably a Core bootstrap Closure), invocation reuses the existing
Context-local execution-plan projection map and rebuilds/caches the executable plan for that exact
Context language before any nested CallTarget executes. Closure execution still keeps one template
plan per Closure syntax site rather than one plan per Closure instance; staged-direct behavior stays
unchanged outside entered Contexts.

The temporary bundled-tool staged-direct compatibility path ended in `0.2.291-SNAPSHOT`. Package Tool and Test Tool
now enter through their semantic Process Contexts. Test Tool exact/inspection execution carries inert
Truffle `Source` values into fresh child Processes and parses only after those child Processes are
bound to distinct Contexts on the driver-owned RuntimeHost/Engine. Workspace preflight and application
Processes use the same host-per-driver / Context-per-Process topology; application canonical-module
preparation is deliberately only *entered* in its owning Context here and remains mechanically direct
until the next initial-module cutover phase.

## Published phase — bundled tool + exact/fresh/captured/workspace hosting

Published in `0.2.291-SNAPSHOT`: the CLI no longer has `legacyToolSession` or a Session-owned direct
`ProtosSourceCompiler`. Bundled tool entry Sources use the same Process-scoped public parse/root-task
path as ordinary one-shot CLI execution. The Test Tool grants its exact-source facilities the outer
driver's explicit `ProtosPolyglotRuntimeHost`; every exact/fresh/captured child remains a fresh semantic
Process with a distinct Context, but child Contexts reuse the driver's Engine and close before control
returns to the tool. The no-host helper overloads retain a self-owned temporary RuntimeHost for
standalone Java consumers.

`ProtosFreshProcessExecutor` and `ProtosCapturedProcessExecution` now accept exact inert `Source`
objects rather than caller-compiled `CallTarget`s. Parsing, root execution, cooperative child-domain
drain, live-result inspection and inspector-Closure invocation occur only inside the fresh Process
Context. This removes the cross-layer AST handoff while preserving Process arguments/environment,
private captured streams, detached result observation and fresh-Process semantics.

`ProtosWorkspaceRunDriver` owns one explicit RuntimeHost for a run. Package preflight and application
remain separate Processes and separate Contexts on that Engine. The application Process enters its
Context before the existing `ProtosCanonicalInitialModuleExecution` runs, preventing direct roots from
being born in a foreign sharing layer without pre-empting the next mechanical phase, which still owns
ordinary module + RootActor initial-module public-parse cutover.

## Remaining mechanical phases inside I026-A4B3

1. ordinary module + RootActor initial-module execution cutover;
2. final direct-production-entry retirement, architecture guard, and A4B3/A4B/A4 closure.

These remain inside Issue #89 as mechanical phases unless one later exposes an independently blockable/reviewable durable unit. Issue #42 remains the parent I026 outcome. `ContextPolicy.EXCLUSIVE` remains active; `REUSE` and `SHARED` remain deferred.

## Evidence

Focused CLI and REPL suites preserve argument, stream, error, multiline and persistent-context
behavior. A Java architecture guard asserts the ordinary CLI/REPL Process-context path and also
asserts the temporary bundled-tool staging marker. Reflection-based REPL tests terminate their
private sessions so the newly real Context/Engine lifetime is not leaked. Existing A+ multi-Process
evidence remains unchanged and continues to prove distinct Context-local plans/CallTargets.
