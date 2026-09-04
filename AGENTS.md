Protos Agent Guidelines

Project purpose

Protos is an experimental prototype-based programming language designed from first principles.

The project deliberately separates language design from implementation. The implementation must realize the language defined by the specification; it must not silently redefine the language to accommodate implementation convenience.

When behavior is unclear, incomplete, or contradictory, do not invent semantics. Report the ambiguity and ask for a design decision.

<!-- BEGIN PROTOS DESIGN PHILOSOPHY -->
## Protos design philosophy

These principles apply to the whole repository. They constrain language design,
implementation architecture, libraries, tests, and documentation.

### Build a small universe, not a collection of features

Protos aims for a small conceptual foundation from which powerful behavior can
emerge. Minimize total conceptual complexity, not the number of named concepts.
A new abstraction is justified when it removes more independent rules, special
cases, or overloaded responsibilities than it introduces.

### Prefer mechanisms over institutions

Prefer general mechanisms from which higher-level abstractions can be built over
adding each useful abstraction as a permanent language-level category. Do not
promote a common pattern to a primitive merely because it is familiar or useful.
Programs should build institutions inside the Protos universe whenever the
existing mechanisms can express them naturally.

### No pets

Avoid individually privileged objects, values, contexts, constructs, or runtime
entities. Behavior should follow from semantic properties and general rules, not
from identity-based exceptions.

Prefer:

    Any object satisfying X behaves as Y.

over:

    This particular object behaves as Y.

Magic objects, privileged instances, distinguished roots, well-known singleton
entities, or identity checks must earn their existence through fundamental
semantics, not implementation convenience. Internal optimizations may recognize
specific objects only when observable behavior remains unchanged.

### Ordinary things should remain ordinary

Language facilities should remain ordinary participants in the object model
wherever possible. Do not create parallel semantic universes for contexts,
callables, built-ins, libraries, or runtime facilities when ordinary Protos
mechanisms can represent them.

### General rules beat special cases

Prefer rules that explain multiple behaviors naturally. Treat "except when",
"only for this object", "normally, but", and similar clauses as design smells.
Exceptions are sometimes necessary, but they require semantic justification.
Implementation convenience is not sufficient justification.

### Preserve orthogonality and composability

Features should compose through their existing semantics. A new feature should
not normally require pairwise special rules with objects, slots, delegation,
lookup, assignment, contexts, closures, calls, errors, mutation, concurrency, or
shared state. A large interaction matrix is evidence that the abstraction may be
wrong.

### Semantic distinctions should remain visible

Do not collapse semantically different operations merely because their
implementations are similar. If operations have different invariants, effects,
lookup behavior, or failure modes, that distinction should remain visible where
it improves reasoning. Do not make the runtime guess programmer intent.

### Fail where the invariant is violated

Invalid operations should fail at the point where the language invariant is
violated. Do not silently turn errors into absence or fallback behavior merely
for convenience. `null` is not a universal substitute for failure.

### Pay only for what you use

Protos must be useful from a trivial single-threaded `hello world` to highly
concurrent and very large deployments without imposing the cost of unused
capability.

Support for concurrency, distribution, clustering, isolation, scheduling, or
other advanced facilities must not impose unnecessary conceptual, syntactic,
runtime, memory, synchronization, or coordination cost on programs that do not
use them.

Simple things must stay simple; complex things must remain possible; programs
should pay only for the complexity they actually use.

### Scale by composition, not by changing universes

Protos should scale by composing the same underlying mechanisms rather than by
requiring a fundamentally different programming model at larger scale. New
layers are justified when they resolve a real semantic, lifecycle, isolation, or
scalability boundary while preserving the simplicity of lower layers.

The best scaling abstractions should feel like missing pieces of the existing
model, not foreign subsystems attached to it.

### Generality must be earned

Do not add abstraction merely in the name of generality. A design earns
generality by surviving substantially different realistic use cases with the
same small set of semantics. Prefer the simplest abstraction that solves the
motivating problem, composes with existing mechanisms, survives unrelated
scenarios, and does not impose significant cost on simpler programs.

### Minimize shared mutable state

Minimize shared mutable state in both language semantics and library design.
Prefer local state, immutability, isolation, ownership, message passing, or
other less-coupled mechanisms when they provide equivalent semantics. Narrow
the scope and lifetime of state that genuinely must be shared. Avoid global
mutable state.

The default question is:

    Can this state remain local?

If yes, keep it local.

### Prefer independence over coordination

When several semantically valid designs exist, prefer the one that allows the
most independent progress and requires the least necessary blocking and
coordination. Avoid serializing unrelated work merely because serialization is
easier to implement.

This does not mean mandatory asynchrony or lock-free algorithms at any cost. A
simple synchronous operation on local state can be preferable to additional
concurrency machinery. Minimize total coordination and complexity, not the raw
number of locks.

### Respect qualitative thresholds

Not all changes of size one have the same conceptual cost. Crossing from zero
instances of a mechanism to one instance can change the nature of the language.
Treat the first reserved word, privileged entity, implicit conversion, hidden
heuristic, platform-dependent semantic branch, or similar new category as an
architectural decision.

The distance from 0 to 1 may be greater than the distance from 1 to 10.

### Preserve global language properties

Before accepting an apparently local change, ask whether it creates a category
of thing that Protos previously did not have. Protect global properties such as
the absence of reserved words or unnecessary privileged entities unless the
benefit clearly justifies crossing that qualitative threshold.

### Solve ambiguity structurally before adding syntax

When syntax is ambiguous, first try to resolve the ambiguity by making the
grammar more precise, using existing delimiters, structural rules, or explicit
formal disambiguation. Do not add keywords, punctuation, or markers solely to
make parsing easier. Parser convenience is not sufficient reason to expand the
surface language.

### Keep platform differences at the boundary

Define portable semantics wherever a coherent portable abstraction exists.
Operating-system, host-runtime, JVM, Windows, Linux, or POSIX differences should
not leak unnecessarily into the core language model. Encapsulate genuine host
differences behind explicit system boundaries and capabilities.

Portability does not mean designing to the weakest common denominator. Define
the strongest coherent portable abstraction Protos can guarantee; when a
capability is genuinely platform-specific, make that dependence explicit rather
than accidental.

### Semantics before syntax; semantics before implementation

First define the capability, semantics, invariants, interactions, failure
behavior, and concurrency implications. Only then design syntax and
implementation. Truffle, GraalVM, the JVM, the parser, tests, or current runtime
behavior do not define Protos semantics.

### The specification defines Protos

The normative specifications under `spec/` define Protos. Everything else
implements, tests, explains, records, or uses them.

Not every file under `spec/` is normative. Design ledgers, changelogs, agent
instructions, OPEN/PENDING material, and exploratory documents do not become
language semantics merely by residing under `spec/`.

If implementation, tests, documentation, or historical behavior disagree with
normative specification, investigate the contradiction rather than silently
choosing the implementation.

### Observable behavior matters; machinery does not

Specify observable semantics precisely without unnecessarily prescribing
implementation machinery. Implementations may change representation, caching,
compilation, scheduling, dispatch, storage, or other internals as long as
observable Protos semantics remain unchanged.

### Concurrency is language semantics

Real multithreading and shared-state behavior must have explicit Protos
semantics where observable. "The JVM handles it" is not a Protos concurrency or
memory model.

### Learn from precedent; do not be ruled by it

Other languages, runtimes, specifications, research, and implementations are
evidence. They reveal known solutions, failures, edge cases, and trade-offs.
They are not authority over Protos. Familiarity and popularity are subordinate
to coherence with the Protos universe.

<!-- END PROTOS DESIGN PHILOSOPHY -->

## Scoped agent instructions

Before reading, editing, reviewing, or generating files in a repository
subtree, check for an `AGENTS.md` governing that subtree and apply it together
with this repository-root file.

In particular:

- work under `spec/` is additionally governed by `spec/AGENTS.md`;
- work under `src/` is additionally governed by `src/AGENTS.md`.

More specific instructions may add or refine rules for their scope, but they do
not silently discard repository-wide requirements.

## Unresolved language-design questions

When a request raises an unresolved question about what Protos should mean,
treat it as language-design work before treating it as implementation work.

Apply the specification-design process in `spec/AGENTS.md`: research relevant
prior art, compare alternatives and trade-offs, test them against the Protos
design philosophy, attempt to falsify candidate designs, and recommend the best
Protos semantics before implementation begins.

Do not bypass this process merely because the original request was phrased as
"implement X".


## Implementation blockers

`docs/IMPLEMENTATION_BLOCKERS.md` is the repository-wide ledger for
implementation work that cannot proceed without unresolved normative semantics.
It records implementation state only; it is not part of the Protos specification.

When implementation work reaches a semantic dependency that is not normatively
closed or otherwise precise enough to implement without guessing:

- do not invent the missing semantics;
- do not block unrelated implementation work;
- add or update a blocker in `docs/IMPLEMENTATION_BLOCKERS.md`;
- give the blocker a stable identifier;
- identify the blocked implementation area;
- state the exact normative dependency;
- identify the specification documents or sections that own the decision;
- state an objective, verifiable unblock condition;
- record what implementation work may safely continue independently.

A blocker must be based on a normative condition, not on a predicted revision,
commit SHA, implementation strategy, agent-specific memory, or expected wording
of a future specification change.

Before beginning implementation work, inspect
`docs/IMPLEMENTATION_BLOCKERS.md`. Re-check every relevant `BLOCKED` or `READY`
entry against the current normative specification on the current `main` branch.
Never rely on the specification state that existed when the blocker was
recorded.

If a `BLOCKED` entry's unblock condition is now satisfied, mark it `READY`,
re-audit the affected semantics against the current specification, and resume
the work when it is within the current task or when no higher-priority
independent work takes precedence.

When the blocked implementation is completed, superseded, or no longer
applicable, mark the entry `CLOSED` and briefly record why. Do not delete closed
entries merely to hide project history.

Canonical language specification

The canonical core language definition is maintained in:

- "spec/PROTOS_LANGUAGE_SPEC.md"
- "spec/PROTOS_GRAMMAR.md"
- "spec/runtime/ABSTRACT_RUNTIME.md"

Normative domain models supplement those core documents for semantically substantial standard subsystems. The current normative domain model is:

- `spec/semantics/OBJECT_MODEL.md` — primary normative owner of migrated object-model semantics.
- `spec/semantics/EXECUTION_AND_CONTROL.md` — primary normative owner of migrated execution/control semantics.
- `spec/semantics/CALLABLES.md` — primary normative owner of migrated callable/invocation semantics.
- `spec/semantics/MODULES.md` — primary normative owner of migrated module semantics.
- `spec/semantics/ERRORS.md` — primary normative owner of migrated Error semantics.
- `spec/semantics/VALUES_AND_COLLECTIONS.md` — primary normative owner of migrated value/collection semantics.

- "spec/io/IO_CORE.md" — observable I/O, text/binary adapter, filesystem-authority, and Process-I/O-bootstrap semantics.

- `spec/concurrency/FUTURES_AND_TASKS.md` — primary normative owner of migrated Core Future/task semantics.
- `spec/concurrency/ACTORS.md` — primary normative owner of migrated Core Actor semantics.
- `spec/concurrency/PARALLEL_EXECUTION.md` — primary normative owner of migrated Core isolated-parallel (P) semantics.
- `spec/PROTOS_CONCURRENCY_MODEL.md` — non-normative historical/design ledger; it contains no normative observable behavior after revision 328.
- `spec/concurrency/DISTRIBUTED_RUNTIME.md` — primary normative owner of migrated Process/Node/Cluster/Group/distributed-runtime semantics; unresolved design-qualified material remains in the transitional ledger.

Treat the canonical core documents together with applicable normative domain models as the source of truth for observable language/standard semantics in their respective domains.

During concurrency modularization, normative ownership is split deliberately between the migrated domain modules above and the still-unmigrated CLOSED portions of `spec/PROTOS_CONCURRENCY_MODEL.md`. A compatibility anchor in the ledger never creates a second authority. See "Concurrency design work" below.

Before implementing or modifying syntax, parsing, object semantics, invocation, lookup, control flow, errors, concurrency, built-in protocols, or other observable language behavior, inspect the relevant specification sections first.

The documents have distinct responsibilities:

- `PROTOS_LANGUAGE_SPEC.md` defines the language model and observable semantics.
- `PROTOS_GRAMMAR.md` defines lexical structure, syntax, precedence, parsing rules, mandatory desugarings, and the canonical semantic AST.
- `runtime/ABSTRACT_RUNTIME.md` is a non-normative abstract execution/pseudocode model constrained by the normative owners.
- `spec/io/IO_CORE.md` owns observable I/O-domain semantics and should be referenced rather than duplicated when another document only needs an I/O rule.

Do not treat runtime pseudocode as permission to change language semantics.

If the documents appear inconsistent, stop and report the inconsistency instead of choosing one interpretation silently.

Specification changes

Do not change the language design merely because a different implementation would be easier.

A change to observable language behavior is a specification change, not an implementation detail.

Examples include:

- syntax or precedence
- name lookup
- slot creation or assignment
- delegation
- "this", "context", or "super"
- closure capture
- invocation or argument binding
- equality or identity
- object construction
- error propagation
- Future behavior
- concurrency semantics
- built-in protocol behavior

Do not make such changes unless the user explicitly requests or approves the language-design change.

When an implementation task exposes an unspecified semantic case, report it clearly and wait for a decision.

When an approved semantic change is made, update all affected canonical specification documents in the same change so they remain mutually consistent.

Semantic integrity

Preserve the fundamental Protos design principles.

In particular:

- Everything is an object.
- There are no classes.
- "Object" is the unique root of delegation.
- Every other object has exactly one immutable delegation parent.
- Reads may delegate; writes never delegate.
- ":" creates a slot.
- "=" modifies an existing slot.
- Missing lookup is an error, not "null".
- "null" is the only absence value.
- Execution contexts are objects.
- Object slots are not lexical variables.
- Closures capture genuine lexical execution contexts by reference.
- Methods dynamically receive their receiver through "this".
- "super" is lookup syntax, not an ordinary value.
- Closures are the single executable value kind.
- Parentheses perform polymorphic invocation.
- Evaluation is left-to-right except where explicitly specified as lazy.
- Language mechanisms should use ordinary object protocols where practical rather than parallel special mechanisms.

Do not introduce Java, JavaScript, Python, Smalltalk, or other host/source-language semantics merely because they are familiar.

Implementation architecture

Keep language semantics independent from the implementation framework.

Prefer a pipeline with clear boundaries:

source
  ↓
lexer
  ↓
tokens
  ↓
parser
  ↓
semantic AST
  ↓
Truffle lowering / execution
  ↓
runtime

The parser should produce a semantic representation of Protos, not directly encode incidental Truffle implementation details.

Keep the semantic AST conceptually separate from Truffle execution nodes.

Runtime objects and operations should represent Protos semantics rather than expose Java implementation details.

Host-language implementation details must not become observable language behavior unless explicitly defined by the specification.

Parser

Use the grammar document as the authority for accepted syntax.

Do not accept additional syntax simply because it is easy for the parser to recognize.

Do not silently reject syntax defined by the grammar because it is inconvenient to implement.

Preserve significant newlines according to the grammar. Protos does not use JavaScript-style Automatic Semicolon Insertion.

Operator precedence, associativity, custom operators, contextual "...", trailing closures, indexed access, and assignment must follow the grammar rather than host-language conventions.

When possible, keep parsing and semantic validation separate. A construct that is syntactically valid but semantically invalid should not require grammar distortion merely to reject it earlier.

Runtime

Implement observable behavior from the language specification and runtime semantics, not from assumptions about Java objects.

Do not expose Java "null" as Protos "null".

Do not use Java object identity directly as the definition of Protos "===" where the specification defines semantic value identity.

Do not use Java exceptions as an implicit substitute for the Protos error/condition model at language boundaries. Internal Java exceptions may be implementation mechanisms, but they must be translated into the specified Protos behavior.

Do not allow Java collection, numeric, string, threading, or equality semantics to leak into Protos when they differ from the specification.

Implementation optimizations are allowed only when they preserve observable semantics.

Truffle and GraalVM

Treat Truffle as the implementation platform, not as the language definition.

Prefer standard Truffle mechanisms for AST specialization, frames, instrumentation, interop, and optimization when they preserve Protos semantics.

Do not distort the language model solely to obtain an optimization.

Keep Truffle-specific behavior localized where practical so that semantic code remains understandable independently of the framework.

Avoid premature optimization. Establish correct semantics and useful tests before adding specialized execution paths.

When an optimization is introduced, preserve a clear generic semantic path against which specialized behavior can be understood and tested.

Change scope

Keep changes scoped to the requested task.

Do not combine feature work with unrelated refactors, formatting, renaming, dependency upgrades, architecture changes, or cleanup.

Report relevant unrelated problems instead of modifying them unless they block the requested work.

Do not silently introduce a new architectural or semantic pattern when an established one already covers the need.

Before implementing a feature, inspect nearby code and comparable functionality and reuse established abstractions when appropriate.

Compatibility

During language version "0.1", the design may evolve, but implementation changes must still follow the current specification.

Do not preserve accidental implementation behavior when it contradicts the specification.

Conversely, do not introduce a breaking semantic change merely to simplify existing code.

If existing implementation behavior and the current specification disagree, the specification wins unless the user explicitly decides otherwise.

Clearly report compatibility consequences when changing already implemented behavior.

Test execution

Do not run tests automatically.

Only run tests when the user explicitly asks you to do so.

When tests are requested, run only those directly relevant to the current change. Do not run the complete test suite unless the user explicitly requests it.

Do not repeatedly rerun failing tests without first understanding and changing the likely cause.

Static verification

For source-code changes, run the narrowest applicable formatter check, linter,
compiler check, or static-analysis command needed to verify the modified scope,
unless the check is unavailable, would be expensive, or would require starting
a long-running process.

Prefer dedicated validation/check modes over commands that rewrite files or
perform broader build phases. Static verification MUST NOT implicitly run tests
when tests were not explicitly requested. Formatting tools must not introduce
unrelated formatting changes.

If an applicable verification command would also execute tests, use a non-test
alternative or report that the check was not run.

New or modified code must not introduce compiler, static-analysis, lint, or
formatting diagnostics that indicate correctness, type-safety, maintainability,
or project-style problems in the affected scope.

Do not claim a source change is clean if the applicable static checks were not
run. Report which checks were run, which were not run, and why.

A clean build achieved only by suppressing applicable warnings is not the same
as clean code. Do not add suppressions merely to make tooling pass; use narrowly
scoped suppressions only when the warning is genuinely inapplicable and the
reason is defensible.

Testing philosophy

Tests should validate Protos semantics rather than implementation accidents.

Prefer small semantic tests that demonstrate observable language behavior.

When fixing a semantic bug, add or update a focused regression test when tests are part of the requested work.

Where useful, test the same semantic rule through multiple execution paths, especially when Truffle specialization or optimized nodes could diverge from generic behavior.

Do not weaken a test merely to make an incorrect implementation pass.

If a test contradicts the current specification, report the contradiction instead of treating the test as authoritative.

Operational boundaries

Never commit or push changes unless the user explicitly requests it.

Do not create releases, tags, branches, or pull requests unless explicitly requested.

Do not modify repository history.

Before performing a potentially expensive or long-running operation, explain the expected cost or duration and ask the user for confirmation.

Do not automatically start long-running language servers, development servers, REPL sessions, benchmarks, profiling runs, or watch processes.

Dependencies

Avoid adding dependencies when the functionality is small and reasonably implementable within the project.

Before adding a dependency, check whether the JDK, GraalVM/Truffle platform, or an existing project dependency already provides the required functionality.

Do not upgrade Java, GraalVM, Truffle, build tooling, parser libraries, or other foundational dependencies as part of unrelated work.

Keep GraalVM and Truffle versions deliberately pinned and compatible.

Dependency changes that affect the language implementation architecture should be explained before they are made.

Source file size

Keep hand-written source files focused and reasonably small.

When a source file reaches approximately 600 lines, assess whether it contains separable responsibilities.

Do not normally let a hand-written source file exceed 800 lines; split it into cohesive components before adding substantial new code.

These thresholds are guidelines rather than mechanical limits.

Split by semantic or architectural responsibility, not into arbitrary numbered fragments.

Existing oversized files do not have to be split for a small unrelated change.

Generated files, vendored code, lock files, fixtures, and primarily declarative data are exempt.

Consistency and reuse

Before implementing or changing a feature, inspect nearby code and comparable features to identify established project patterns.

Prefer extending existing lexer, parser, AST, runtime, protocol, and Truffle abstractions over introducing parallel implementations.

New work must fit the conventions of the layer being modified.

Keep responsibilities clear between:

- lexical analysis
- parsing
- semantic AST
- semantic validation
- runtime object model
- invocation and dispatch
- execution contexts
- Truffle execution
- standard protocols
- host interoperability

Do not move behavior between these layers merely for convenience if doing so obscures the semantic model.

Documentation

Developer documentation must describe the current implementation and specification accurately.

Do not document planned behavior as though it already exists.

When implementation differs intentionally from the specification because work is incomplete, state that explicitly.

Examples in documentation must use valid Protos syntax according to the current grammar.

The README is an introduction to the project, not an alternative language specification. Detailed semantic rules belong in the canonical specification documents.

Development language

All development-related content MUST be written in English.

This includes:

- Java package, class, method, field, variable, and internal identifier names
- Protos implementation identifiers
- source code comments
- Javadocs and other developer documentation
- test names and test descriptions
- README files
- specification documents
- CHANGELOG entries
- commit messages
- configuration comments
- developer-facing log and diagnostic messages

The language used by the user when giving instructions does not affect the development language: all development artifacts must remain in English.

User-facing Protos programs and future localization facilities are separate concerns and must not force implementation or developer-facing content into a particular natural language.

Task completion

At the end of a coding task, summarize:

- what changed
- which files were changed
- whether the specification was affected
- whether tests were run
- which formatter, linter, compiler, or static-analysis checks were run, and any applicable checks that were not run
- any remaining ambiguity, limitation, or follow-up that is directly relevant to the requested task
- whether license compliance was checked when source files were added or modified

Do not claim tests passed if they were not run.

Do not claim behavior is specification-compliant unless the relevant semantics were actually checked against the canonical documents.

### Documentation editing

When editing existing documentation:

- Make minimal, localized edits. Do not reflow, rewrap, or reconstruct surrounding paragraphs unless explicitly required.
- Preserve existing line breaks, paragraph boundaries, Markdown formatting, and intentional whitespace outside the exact text being changed.
- Do not concatenate words, punctuation, inline code, or Markdown elements across edit boundaries.
- After editing, inspect the actual resulting file content around every changed region rather than relying only on the generated patch.
- Before reporting completion, run `git diff --check` and review `git diff` for accidental whitespace, missing spaces, line-break changes, or unrelated formatting changes.
- Treat existing Markdown hard line breaks (two trailing spaces) as intentional unless the task explicitly requests formatting cleanup.
- Do not perform unrelated formatting cleanup in a semantic specification revision.

### License compliance

Protos is licensed under the Adaptive Public License 1.0 (APL-1.0).

The canonical repository license is `LICENSE.TXT`, including the completed Exhibit A. Do not modify the APL license text, Exhibit A selections, Initial Contributor information, Designated Web Site, governing jurisdiction, Third Party selection, patent selection, or other license configuration unless the user explicitly requests a licensing change.

For every new Protos-owned source-code file, add the exact APL Part 5 License Notice at the beginning of the file before any package declaration, imports, shebang, or source content, using the comment syntax appropriate for that file type.

For existing Protos-owned source files that are modified, verify that the required APL Part 5 License Notice is present and still matches the notice in `LICENSE.TXT`. Line wrapping and comment-prefix characters may differ, but the notice text itself must not be paraphrased, shortened, replaced with only an SPDX identifier, or otherwise altered.

The current Java form of the required notice is:

```java
/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
```

Do not automatically apply the Protos APL notice to generated files, vendored files, third-party source, Maven Wrapper files, dependencies, imported fixtures, or other material not owned by the Protos project. Preserve upstream copyright and license notices on such files.

When adding a new source-code file type or source directory, determine whether it is Protos-owned Source Code under the APL and, if so, add the corresponding Part 5 notice using a syntactically valid comment form.

Keep current project documentation and build metadata consistent with the active license:

- `README.md` must identify APL-1.0 and link to `LICENSE.TXT`.
- Maven license metadata must identify Adaptive Public License 1.0 / SPDX `APL-1.0`.
- Distributed JAR artifacts must continue to contain `META-INF/LICENSE.TXT`.
- Do not reintroduce current SSPL licensing references.
- Historical documentation may describe the license that applied at that historical time, but must not misleadingly imply that APL applied retroactively.

Before completing any coding task that creates, removes, renames, or modifies source files, perform a license-compliance check:

- verify every new Protos-owned source file has the required Part 5 notice;
- verify modified Protos-owned source files still have the required notice;
- verify no Protos APL notice was added to third-party or generated material;
- verify `LICENSE.TXT` still exists;
- if build configuration affecting packaging was changed, verify that `META-INF/LICENSE.TXT` remains included in the distributed JAR.

Do not change licensing terms or make licensing-policy decisions implicitly as part of unrelated work. Report any ambiguity before making such a change.

### Concurrency design work

- `spec/PROTOS_CONCURRENCY_MODEL.md` is a mixed document: a section whose status is exactly `CLOSED` or `CLOSED --- REVISED` is normative concurrency-domain semantics; a section whose status contains OPEN, PENDING, DIRECTION, DETAILS OPEN, API OPEN, or another design-only qualifier is non-normative unless and until its status is changed explicitly.
- Normative status is section-local. A CLOSED section may explicitly state that a named API, syntax, policy, mechanism, or implementation detail remains open; that open subtopic is not made normative merely by appearing inside the CLOSED section.
- The global specification revision is the revision of the newest entry in `spec/PROTOS_SPEC_CHANGELOG.md`.
- A normative document's `Document revision` records the newest global specification revision that changed that document. Document revisions are intentionally not synchronized.
- Never edit or stage an otherwise-unaffected specification document solely to advance its `Document revision`.
- Record every normative or specification-structure change in `spec/PROTOS_SPEC_CHANGELOG.md`.
- When a CLOSED decision from this ledger is incorporated into canonical specification documents, update the affected specification files and record that canonical change in `spec/PROTOS_SPEC_CHANGELOG.md`.
- When concurrency semantics overlap responsibilities of a canonical core document or another normative domain model, update every affected normative document in the same change so the normative set remains mutually consistent.
- Never infer semantics from OPEN, PENDING, DIRECTION, DETAILS OPEN, API OPEN, or design-only material.
