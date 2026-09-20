# Protos implementation agent guidelines

These instructions apply to the entire `src/` tree in addition to the
repository-root `AGENTS.md` and `AGENTS.work/IMPLEMENTATION.md`.

Work here as a language implementation engineer.

The implementation realizes Protos; it does not define Protos.

## Do not redesign the language while implementing it

Before changing observable behavior, read the governing files under `spec/`
(see the canonical specification map in the repository-root AGENTS.md).
Implement the specified semantics faithfully.

If the specification is ambiguous, contradictory, incomplete, unexpectedly
expensive, or appears impossible to implement faithfully, report the conflict
instead of silently inventing, weakening, or replacing semantics in source
code.

Implementation agents should challenge the implementation. Language-design
questions belong to the specification-design process
(`AGENTS.work/DESIGN.md`).

The durable blocker ledger and its maintenance rules are in
`AGENTS.work/IMPLEMENTATION.md`. Before starting implementation work, review
the ledger's relevant `BLOCKED` and `READY` entries against the current
normative specification on the current `main` branch of
`guillermomolina/protos`.

## Preserve observable semantics

Representation, caching, compilation strategy, specialization, storage,
dispatch, scheduling machinery, synchronization primitives, and other
internal choices may change freely only when observable Protos semantics
remain the same.

Keep Truffle/GraalVM/JVM mechanisms behind semantic boundaries. Host behavior
is an implementation tool, not an implicit language rule.

## Pay only for what is used

Do not make simple execution paths pay substantial runtime, allocation,
synchronization, scheduling, indirection, or coordination cost for
capabilities they do not use.

A single-threaded program should not acquire concurrency overhead merely
because Protos supports concurrency. Local objects should not require
distributed or cross-thread machinery merely because such use is possible
elsewhere.

Measure and justify unavoidable costs when an implementation choice would
make a cross-cutting capability affect all programs.

## Minimize shared state and blocking

Prefer local state and independent progress. Avoid global mutable runtime
state, coarse global locks, serialization of unrelated operations, or
central coordination points when a simpler less-coupled implementation is
available.

Use synchronization when the Protos semantics require it, not merely because
it simplifies implementation. Conversely, do not introduce mandatory
asynchrony, lock-free algorithms, background workers, or complex concurrency
machinery when a simple local synchronous operation has lower total cost.

Prefer the least coordination necessary to preserve the specified semantics.

## No implementation pets

Do not introduce observable behavior that depends on the identity of special
runtime objects merely for implementation convenience. Internal sentinels,
caches, singleton helpers, or optimized representations are acceptable only
when they remain semantically invisible or when their identity is explicitly
defined by `spec/`.

## Reuse established layers

Maintain clear boundaries among lexical analysis, parsing, semantic AST,
validation, runtime object model, invocation/dispatch, execution contexts,
Truffle execution, standard protocols, and host interoperability.

Prefer extending an existing abstraction to creating a parallel mechanism
when the existing abstraction genuinely owns the responsibility. Do not
overload an abstraction with unrelated responsibilities merely to avoid
adding a justified new layer.

## Code quality

Code must be clean, idiomatic, understandable, and maintainable.

New or modified code MUST NOT introduce linter, compiler, static-analysis, or
formatting errors in the affected scope. Follow the project's established
style and tool configuration rather than inventing local conventions.

Prefer clear code over clever code. Names should communicate intent, control
flow should remain understandable, and unnecessary duplication or incidental
complexity should be avoided.

Do not suppress warnings merely to make tooling pass. A suppression is
acceptable only when the warning is genuinely inapplicable, the suppression
is narrowly scoped, and its reason is clear.

## Documentation quality

Document code enough to make its intent, invariants, public contracts, and
non-obvious decisions understandable, but do not over-document self-evident
implementation details.

Document especially when relevant:

- public APIs and externally relevant contracts;
- non-obvious invariants;
- semantic constraints inherited from the Protos specification;
- concurrency, ownership, lifecycle, visibility, or synchronization
  assumptions;
- surprising implementation choices and why they exist.

Do not add comments that merely restate the code. Prefer expressive names and
straightforward structure when the code can reasonably explain itself.

Comments and documentation must remain accurate. Stale or misleading
documentation is worse than no documentation.

## Optimization discipline

Correct generic semantics come first. Optimize only after the semantic path
is understood. Optimized paths must remain equivalent to the generic
semantic path.

An optimization that requires observable special cases, semantic weakening,
or additional language restrictions is a language-design proposal, not an
implementation optimization.

## Verification and tests

For source changes, request the narrowest applicable formatter, linter,
compiler check, or static analysis for the modified scope (static
verification rules: `AGENTS.work/IMPLEMENTATION.md`). Static verification
does not imply running tests.

Follow the impact-aware validation policy in `AGENTS.work/IMPLEMENTATION.md`.
As a default heuristic, production changes under `src/main/**` are
executable-impact changes and test changes under `src/test/**` are
test-impact changes; defer to the root impact classifier whenever a change
can alter executable behavior regardless of its directory. During
interactive work, use the smallest complete affected test set while the
change is evolving and defer the integrated full suite until the policy
requires it. When integrated full validation is required, hand the
repository Makefile entry point (`make test`) to the human. Use tests to
validate specified semantics, not historical implementation accidents.

## Implementation architecture

Keep language semantics independent from the implementation framework.

### Core bootstrap boundary

Bootstrap only the irreducible host machinery required to execute Protos.
Do not grow a parallel hardcoded standard library in Java when standard objects
or behavior can be constructed or installed faithfully through ordinary Protos
mechanisms.

The detailed non-normative bootstrap architecture is documented in
`guillermomolina/protos-project-docs:docs/project/architecture/CORE_BOOTSTRAP_ARCHITECTURE.md`.
Source for distributable Core behavior that can be expressed in Protos belongs
under `protos/lib/core/`.
Host-native primitives should be exposed through the ordinary object/protocol
model wherever that model can represent them.

Temporary Java-side bootstrap scaffolding must be identified as temporary and
must not be extended into additional standard families merely for convenience.

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

## Parser

Use the grammar document as the authority for accepted syntax.

Do not accept additional syntax simply because it is easy for the parser to recognize.

Do not silently reject syntax defined by the grammar because it is inconvenient to implement.

Preserve significant newlines according to the grammar. Protos does not use JavaScript-style Automatic Semicolon Insertion.

Operator precedence, associativity, the fixed standard operator surface, contextual "...", trailing closures, indexed access, and assignment must follow the grammar rather than host-language conventions.

When possible, keep parsing and semantic validation separate. A construct that is syntactically valid but semantically invalid should not require grammar distortion merely to reject it earlier.

## Runtime

Implement observable behavior from the applicable normative specification owners, not from the informative runtime pseudocode or assumptions about Java objects.

Do not expose Java "null" as Protos "null".

Do not use Java object identity directly as the definition of Protos "===" where the specification defines semantic value identity.

Do not use Java exceptions as an implicit substitute for the Protos error/condition model at language boundaries. Internal Java exceptions may be implementation mechanisms, but they must be translated into the specified Protos behavior.

Do not allow Java collection, numeric, string, threading, or equality semantics to leak into Protos when they differ from the specification.

Implementation optimizations are allowed only when they preserve observable semantics.

## Truffle and GraalVM

Treat Truffle as the implementation platform, not as the language definition.

Prefer standard Truffle mechanisms for AST specialization, frames, instrumentation, interop, and optimization when they preserve Protos semantics.

Do not distort the language model solely to obtain an optimization.

Keep Truffle-specific behavior localized where practical so that semantic code remains understandable independently of the framework.

Avoid premature optimization. Establish correct semantics and useful tests before adding specialized execution paths.

When an optimization is introduced, preserve a clear generic semantic path against which specialized behavior can be understood and tested.

## Guest execution backend and Java execution tests
<!-- AUD012 PLAT035 BYTECODE-ONLY-EXECUTION -->

PLAT035 establishes Truffle Bytecode DSL as the only executable Protos backend.
The canonical AST remains the backend-independent semantic representation; it is
not an executable fallback.

The retired executable-AST backend, including `CanonicalToTruffleLowerer`,
`ProtosExpressionNode`, `ProtosRootNode`, `ProtosRootFactory`,
`ProtosExecution`, and the associated legacy execution-node tree, MUST NOT be
reintroduced.

Production and production-representative guest execution must run inside the
appropriate initialized Polyglot/Process execution context and use the canonical
Bytecode-backed execution boundary. Source-backed closures crossing Context
ownership boundaries must be rematerialized for the entered
`ProtosLanguageContext`; a CallTarget owned by another Context must not be reused.

`ProtosSourceCompiler` itself is not globally forbidden: compiler/frontend,
backend, compile-only, architecture, or other explicitly bounded implementation
tests may legitimately exercise it. New direct Java guest-entry paths must not be
introduced merely for convenience. Ordinary Java semantic tests that need real
guest execution should use the established hosted/test execution boundary unless
the test is explicitly about a lower-level compiler or execution component.

`scripts/legacy_execution_guard.py` is the fail-closed repository prevention
gate for growth of direct/legacy execution dependencies and for reintroduction
of the retired executable-AST backend. Do not weaken, bypass, or reset that gate
to accommodate a new dependency; classify and justify the execution path or use
the canonical Bytecode-backed boundary.
