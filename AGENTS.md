Protos Agent Guidelines

Project purpose

Protos is an experimental prototype-based programming language designed from first principles.

The project deliberately separates language design from implementation. The implementation must realize the language defined by the specification; it must not silently redefine the language to accommodate implementation convenience.

When behavior is unclear, incomplete, or contradictory, do not invent semantics. Report the ambiguity and ask for a design decision.

Canonical language specification

The canonical language definition is maintained in:

- "docs/PROTOS_LANGUAGE_SPEC.md"
- "docs/PROTOS_GRAMMAR.md"
- "docs/PROTOS_RUNTIME_SEMANTICS.md"

Treat these documents as the source of truth for language behavior.

`docs/PROTOS_CONCURRENCY_MODEL.md` is also part of the documentation, but with a less complete status: it is a concurrency design ledger, not a canonical language specification document. See "Concurrency design work" below.

Before implementing or modifying syntax, parsing, object semantics, invocation, lookup, control flow, errors, concurrency, built-in protocols, or other observable language behavior, inspect the relevant specification sections first.

The documents have distinct responsibilities:

- `PROTOS_LANGUAGE_SPEC.md` defines the language model and observable semantics.
- `PROTOS_GRAMMAR.md` defines lexical structure, syntax, precedence, parsing rules, mandatory desugarings, and the canonical semantic AST.
- `PROTOS_RUNTIME_SEMANTICS.md` defines executable semantic rules and runtime pseudocode.

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

- `docs/PROTOS_CONCURRENCY_MODEL.md` is part of the documentation, but with a less complete status than the canonical specification documents: it is a design ledger that mixes CLOSED decisions with OPEN, PENDING, and design-only material.
- All documentation documents share the `Document revision` number. This document carries that common revision and has no independent revision or versioning scheme.
- Record every change to this file in `docs/PROTOS_SPEC_CHANGELOG.md`, like changes to the canonical specification documents.
- When a CLOSED decision from this ledger is incorporated into canonical specification documents, update the affected specification files and record that canonical change in `docs/PROTOS_SPEC_CHANGELOG.md`.
- Do not treat OPEN, PENDING, or design-only material in this ledger as normative language semantics.
