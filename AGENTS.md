Protos Agent Guidelines

## Project purpose

Protos is an experimental prototype-based programming language designed from
first principles. Language design and implementation are deliberately
separated: the implementation must realize the language defined by the
specification and must not silently redefine it to accommodate implementation
convenience.

When behavior is unclear, incomplete, or contradictory, do not invent
semantics. Report the ambiguity and ask for a design decision.

## Human-executor mode

This repository uses a human-executor workflow.

The agent is responsible for investigation, reasoning, and editing. The human
is responsible for executing builds, tests, programs, validation commands,
state-changing Git operations, and repository-content publication.

### Agent responsibilities

- Read and apply the instruction files selected by the composition rules
  below.
- Edit, create, or remove repository files when required by the task.
- Inspect the repository directly as needed.
- Search and read files incrementally, starting from the task and following
  concrete evidence; expand the investigation whenever the evidence requires
  it.
- Reason from the evidence already available before requesting more context.
- Do not launch subagents unless explicitly requested by the human.

### Human-executed operations

Do not autonomously execute:

- builds or compilation;
- tests or test suites;
- project programs or runtimes;
- validation or publication scripts;
- package installation or environment modification;
- Git operations that modify repository state or communicate with remotes,
  including add, commit, merge, rebase, push, pull, fetch, or checkout; and
- other local commands whose purpose is to validate, execute, publish, or
  mutate repository or environment state rather than inspect it.

When one of these operations is required, provide the smallest useful command
or command block to the human and wait for the result before continuing.

The human may summarize successful command output (for example, "PASS" or
"green") instead of returning the complete output. If a command fails,
request only the failure evidence needed to diagnose the problem.

Live GitHub coordination is a governed exception: Issue/Project mutations
performed under the coordination instructions in AGENTS.work/COORDINATION.md
remain agent-executable.

There is no standing autonomous repository-publication exception. For every
repository, including guillermomolina/protos-project-docs, repository-content
commit, push, and publication are human-executed unless the project owner
explicitly authorizes a bounded exception for a specific task.

### Read-only operations

Read-only inspection is allowed when useful, including targeted file reads,
searches, repository-status inspection, diffs, and history inspection.

Do not perform broad scans merely to gather context. Gather evidence
incrementally and stop reading once there is enough information to make the
next technical decision.

### Execution boundary

Never infer that a build, test, validation, or publication succeeded because
the code appears correct. Wait for the human to execute the requested command
and report the result.

After editing, identify the next validation step and give the human the exact
command to run. Continue from the reported result.

## Instruction composition and loading

Instructions are layered. The universal rules in this file are a binding
floor: no scoped rule may weaken them.

Select exactly one primary work-type file from the table below based on the
task's formal identifier family or its nature, and read it before starting
that work:

| Task class | File |
|---|---|
| Dxxx, PLATxxx, UPSTREAMxxx, substantive semantic/architectural decisions | AGENTS.work/DESIGN.md |
| AUDxxx audits | AGENTS.work/AUDIT.md |
| Ixxx, CLIxxx, DISTxxx, LMxxx, familyless implementation | AGENTS.work/IMPLEMENTATION.md |
| BUGxxx defects and regressions | AGENTS.work/BUG.md |
| DOCxxx documentation | AGENTS.work/DOCUMENTATION.md |
| PERFxxx performance | AGENTS.work/PERFORMANCE.md |
| LIBxxx Standard Library | AGENTS.work/LIBRARY.md |
| TOOLxxx bundled tools | AGENTS.work/TOOL.md |
| TESTxxx tests | AGENTS.work/TEST.md |
| Live GitHub coordination (Issues, Project, allocation, closure) | AGENTS.work/COORDINATION.md |
| Release publication | AGENTS.work/RELEASE.md |

Tasks that produce or change code or tests also read
AGENTS.work/IMPLEMENTATION.md for the shared implementation discipline. Load
additional work-type files only when the task genuinely spans families.

Path-scoped instructions apply automatically when work enters their trees:
spec/AGENTS.md, src/AGENTS.md, and protos/AGENTS.md.

AGENTS.work/REFERENCE.md is loaded only when a work-type instruction names
it; it is never loaded by default.

If a scoped instruction requires behavior incompatible with a broader rule,
STOP and report the conflict instead of silently choosing. Specific
instructions may refine or sharpen broader rules; they never weaken them.

## Design authority boundary

The project owner is the final authority on substantive design decisions. A
substantive decision changes, narrows, or newly defines observable Protos
semantics, syntax, public protocols, public Standard Library/tool behavior,
compatibility promises, durable architecture, or the settled state of a
design question. Substantive semantic/architectural decisions route through
Dxxx/PLATxxx and the decision process in AGENTS.work/DESIGN.md, including
explicit owner approval.

Routine implementation machinery that cannot change observable behavior does
not require a design checkpoint. Implementation work MUST NOT embed an
unresolved design choice as though it were approved, and MUST NOT infer
design approval from broad instructions such as "implement X", "continue",
or permission to investigate.

When a request raises an unresolved question about what Protos should mean,
treat it as language-design work before implementation work, even if the
request was phrased as "implement X".

Already-approved, already-ratified decisions remain authoritative and
discharge the corresponding design/audit work: do not repeat the approval or
the design-space investigation. Reopen a recorded decision only when concrete
current-repository evidence conflicts with it, and then only with explicit
owner approval.

## Minimum investigation floor

Before implementation work, establish:

1. the applicable instruction files above (root, work-type, path);
2. the directly governing normative specification owners for the behavior in
   question, found through the canonical specification map below;
3. the affected implementation and tests in the current repository revision;
4. the relevant entries of the durable blocker ledger
   (AGENTS.work/IMPLEMENTATION.md) that touch the task's surface; and
5. the durable decision/work record governing the task when one exists.

This floor is targeted and incremental by default. Expand the investigation
when concrete evidence shows ambiguity, contradiction, drift, or a missing
decision. Do not design an implementation first and then search the
specification for justification.

The final report MUST enumerate the files materially inspected under this
contract and the constraints derived from them, so review can distinguish a
real audit from a source-code-only edit.

## Canonical specification authority

The normative specifications under spec/ define Protos. Everything else
implements, tests, explains, records, or uses them. Not every file under
spec/ is normative: changelogs, ledgers, agent instructions, OPEN/PENDING
material, and exploratory documents do not become language semantics merely
by residing there. If implementation, tests, or documentation disagree with
the normative specification, investigate and report the contradiction.

Core documents: spec/PROTOS_LANGUAGE_SPEC.md (language model and observable
semantics) and spec/PROTOS_GRAMMAR.md (lexical structure, syntax, precedence,
parsing rules, mandatory desugarings, canonical semantic AST).
spec/runtime/ABSTRACT_RUNTIME.md is an informative execution model, not
normative authority.

Domain owners:

- spec/semantics/OBJECT_MODEL.md — object-model semantics.
- spec/semantics/EXECUTION_AND_CONTROL.md — execution contexts,
  lookup/evaluation, iteration/control semantics.
- spec/semantics/CALLABLES.md — Closure capture and callable/invocation
  semantics.
- spec/semantics/MODULES.md — module semantics.
- spec/semantics/ERRORS.md — Error semantics.
- spec/semantics/VALUES_AND_COLLECTIONS.md — value-family, equality/identity,
  indexing, and collection semantics.
- spec/semantics/MATCHING.md — matching protocol, matcher outcomes,
  structural projection.
- spec/io/IO_CORE.md — cross-cutting I/O capability, commitment, lifecycle,
  wrapper semantics.
- spec/io/BYTE_IO.md — byte-I/O protocol semantics.
- spec/io/TEXT_IO.md — encoding and text-I/O semantics.
- spec/io/FILESYSTEM.md — filesystem authority, File, Path, file-URL
  semantics.
- spec/io/PROCESS_IO.md — Process I/O bootstrap, environment/arguments,
  standard streams, Process-local I/O authority.
- spec/io/NETWORK.md — network I/O semantics.
- spec/concurrency/FUTURES_AND_TASKS.md — Core Future/task semantics.
- spec/concurrency/ACTORS.md — Core Actor semantics.
- spec/concurrency/PARALLEL_EXECUTION.md — Core isolated-parallel (P)
  semantics.
- spec/concurrency/DISTRIBUTED_RUNTIME.md — Process/Node/Cluster/Group/
  distributed-runtime semantics.

docs/design/CONCURRENCY_DESIGN.md is entirely non-normative.

## Current project coordinates

These are exact spellings; copy them from authoritative repository state
rather than memory:

- GitHub repository: guillermomolina/protos
- Durable project-record repository: guillermomolina/protos-project-docs
- Maven groupId: com.guillermomolina
- Java package root: com.guillermomolina.protos
- Java source/test package path root: com/guillermomolina/protos

In particular, `guillermolina` (one `mo` sequence) is not a valid current
coordinate for this project. If authoritative sources disagree, report the
inconsistency instead of guessing. A future move to a different namespace
must be an explicit migration updating this section and all affected
references coherently.

## Publication and repository-content safety

The current publication model:

- external contributions use a branch/fork + Pull Request + CI/review;
- maintainer/agent interactive work uses the maintainer's current checkout:
  the agent edits directly, and the human executes commit, push, and
  publication commands;
- maintainer Pull Requests are optional and SHOULD be used when their review
  or audit value justifies the cost; and
- main is not PR-only protected at this stage.

<!-- GITHUB002-C HYBRID-CONTRIBUTION-PUBLICATION-CONTRACT -->

Work from the repository state the human is actually using. Do not create
temporary branches, temporary worktrees, staging repositories, or
out-of-checkout publication environments for an ordinary change.

Before each edit, inspect the current changed-path set and the current
content of every path the edit owns. Existing tracked or untracked work
outside the patch-owned paths MUST remain untouched. When a patch-owned path
already carries unrelated edits, proceed only when the requested
transformation preserves them; otherwise stop and report the overlap.

The staged set must be exactly the patch-owned paths: provide explicit
`git add <path>` lines in the commands handed to the human; never `git add
-A` or `git add .`. Never force-push. Never modify repository history. Never
create or push temporary remote branches. Do not create releases, tags,
branches, or pull requests unless explicitly requested.

Before publication, request the human to synchronize with current
`origin/main`, then re-check the assumptions the change depends on.

<!-- GITHUB003 CONCURRENT-PUBLICATION-POLICY -->
When `origin/main` moves during interactive work, re-evaluate only the
preconditions and dependency surfaces that movement can affect; benign
unrelated movement does not invalidate already-correct substantive work.

<!-- GITHUB-CONNECTOR-WRITE-SAFETY -->
GitHub connector/API access has two distinct roles: read/live coordination
(governed by AGENTS.work/COORDINATION.md) and repository publication
(human-executed). Capability, permission, connectivity, existence, schema,
and error-path probes MUST be read-only; never create sentinel/probe
artifacts or probe commits. Connector/API repository-content writes are
forbidden: repository-content publication is human-executed, with no
standing agent-direct exception. If an accidental mutation is detected, stop
the affected work immediately, report the exact commit/ref and paths, and
never hide it with a cleanup commit.

## Validation discipline

Select validation from the definitive changed-file delta plus a conservative
dependency closure, not from a work-item identifier. The impact classes and
the integrated full-suite policy are in AGENTS.work/IMPLEMENTATION.md.

Proceed from cheapest/highest-signal evidence to the most expensive required
gate: inspect the changed-path set; `git diff --check`; inspect the exact
diff; applicable static checks; the complete focal/affected regression set;
the integrated full suite only when the policy requires it. A failed earlier
gate stops progression. Do not repeatedly request the full suite after every
small intermediate edit.

For source changes, request the narrowest applicable
formatter/linter/compiler/static check for the modified scope; prefer check
modes that do not rewrite files; static checks MUST NOT implicitly run
tests; report unavailable or skipped applicable checks.

Before requesting an expensive or long-running validation from the human,
explain the expected cost or duration.

Give the human the next validation command and continue from the reported
result. Never claim tests passed that were not run, or that behavior is
specification-compliant unless the relevant semantics were checked against
the canonical documents.

## Universal constraints

- Keep changes scoped to the requested task. Do not combine feature work
  with unrelated refactors, formatting, renaming, dependency upgrades, or
  cleanup. Report relevant unrelated problems instead of modifying them.
- All development-related content MUST be in English: identifiers, comments,
  Javadocs, documentation, test names, specification text, CHANGELOG
  entries, commit messages, configuration comments, and developer-facing
  diagnostics.
- License: Protos is licensed under the Adaptive Public License 1.0
  (LICENSE.TXT). Every new Protos-owned source file carries the Part 5
  notice; modified Protos-owned source files keep it; never apply it to
  generated, vendored, or third-party material. The exact notice and the
  metadata requirements are in AGENTS.work/REFERENCE.md. Before completing
  any coding task that creates, removes, renames, or modifies source files,
  verify license compliance per that reference.
- Durable project records live in guillermomolina/protos-project-docs under
  its role-first layout (AGENTS.work/REFERENCE.md). A durable record MUST
  NOT be forced into a product commit: publish the product commit first,
  then the record naming the exact revision.
- AI-assisted and AI-generated contributions are allowed. Do not reject,
  devalue, or impose additional requirements on a contribution solely
  because AI produced it; do not require prompt transcripts or model logs.
  Include the AI-assistance disclosure requested by CONTRIBUTING.md; never
  fabricate human authorship or review. For substantive AI-generated work,
  seek an independent review when practical (a separate agent requires
  explicit human approval under Human-executor mode).
- Task completion summary: what changed and which files; whether the
  specification was affected; which validation/static checks were requested
  and their reported results, plus any applicable checks not run; remaining
  ambiguity, limitation, or follow-up directly relevant to the task; and
  license-compliance verification.
