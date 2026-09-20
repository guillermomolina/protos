# Protos reference material

Loaded on demand when a work-type instruction names it. Never loaded by
default. Contains the design philosophy, the semantic integrity constraints,
the durable-documentation path policy, the license notice and metadata
rules, and historical notes.

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

## Semantic integrity

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

<!-- DOC002 ROLE-FIRST-DOCUMENTATION-PATH-POLICY -->
## Durable project documentation path policy

DOC002-B ratified the role-first information architecture for durable project
documentation. Durable project records now live in
`guillermomolina/protos-project-docs`; `guillermomolina/protos` remains the
operational control plane for formal identifiers, GitHub Issues, owner
coordination, approvals, and derived Project scheduling.

The canonical non-normative path contract is
`guillermomolina/protos-project-docs:docs/project/work/DOC002/DOC002_DOCUMENTATION_PATH_CONTRACT.md`.

Agents creating a **new** durable record under `docs/project/**` MUST create it
in `guillermomolina/protos-project-docs` and place it in the selected role-first
destination when the role and owner are unambiguous:

- `docs/project/work/<formal-work-item>/` for records primarily owned by one
  formally tracked work item;
- `docs/project/decisions/language/` for durable decision records whose
  primary domain is observable language/specification semantics; `Dxxx` is not
  by itself a language-role classifier;
- `docs/project/decisions/tooling/` for durable implementation-independent
  tooling/package-system/Package Tool/Test Tool decisions that neither change
  observable Protos semantics nor select host/runtime architecture;
- `docs/project/decisions/platform/` for durable `PLATxxx` platform/runtime
  decision records;
- `docs/project/architecture/` for cross-cutting implementation architecture;
- `docs/project/governance/` for repository/project rationale and maintained
  policy that does not need an invented formal work family;
- `docs/project/registries/` for durable registries and closure/evidence ledgers;
- `docs/project/evidence/<formal-work-item>/` for immutable or snapshot-like
  evidence with a genuine formal owner; and
- `docs/project/history/` for retired or superseded historical snapshots.

`guillermomolina/protos-project-docs:docs/project/README.md` is the role-first
navigation entry point. Repository-root policy/legal/community files and the
established `docs/guide/`, `docs/design/`, and `spec/` authority boundaries in
`guillermomolina/protos` are not relocated merely for symmetry.

The DOC002 migration is complete. A new durable record MUST use its canonical
role-first destination in `guillermomolina/protos-project-docs` when role and
owner are unambiguous; creating a new unclassified flat durable record directly
under `docs/project/` is non-compliant.

If an unexpected legacy or unclassified durable path is discovered later, edit
it only as necessary at its actual path until an explicit bounded
classification/migration change owns relocation. Do not opportunistically move
it, guess a destination, duplicate it under the role-first tree, or create a
compatibility copy merely for symmetry. Historical records may retain old path
spellings when changing them would falsify the repository state they document.

References to current registries, decisions, architecture and work records MUST
identify `guillermomolina/protos-project-docs` as their repository. In
particular, the blocker/unblock-condition ledger is
`guillermomolina/protos-project-docs:docs/project/registries/IMPLEMENTATION_BLOCKERS.md`,
the implementation/closure ledger is
`guillermomolina/protos-project-docs:docs/project/registries/IMPLEMENTATION_STATUS.md`,
and the platform decision registry is
`guillermomolina/protos-project-docs:docs/project/registries/PLATFORM_ARCHITECTURE_DECISIONS.md`.

If a new durable document does not fit one selected role cleanly, do not invent
a new directory, identifier family, or classification to force symmetry.
Resolve the classification through the owning tracked work or an explicit
documentation-governance decision before establishing a durable path. For
decision records specifically, classify by the decision's primary domain
(language/specification, tooling/package-system, or platform/runtime), never
solely by identifier prefix.

DOC002-A remains a historical audit snapshot, not a permanent placement manifest.
The durable rule is structural: `guillermomolina/protos-project-docs` must keep
`docs/project/**` role-first, and later work that discovers a real
classification ambiguity must resolve it explicitly rather than silently
weakening the layout.

## License compliance

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

## Historical notes

- GITHUB015 native-parent enforcement became active at `2026-09-13T10:02:02Z`;
  formal Issues closed before that instant may bootstrap a missing native
  parent from an explicit textual/form declaration, while absence of both is
  unrecoverable legacy structure and MUST NOT be guessed (see
  `AGENTS.work/COORDINATION.md`).
- GITHUB001-F: `guillermomolina/protos-project-docs:docs/project/history/OPEN_TASKS.md`
  is a retired historical backlog snapshot; do not add actionable work to it
  (see `AGENTS.work/COORDINATION.md`).
- The DOC002 role-first migration is complete; DOC002-A remains a historical
  audit snapshot, not a placement manifest (see above).
