# LM009 — Editor & IDE Language Maturity

Status: **IN_PROGRESS**

Current published slice after this record: **LM009-A CLOSED**

Nature: non-normative language-maturity / editor-tooling evidence

Live coordination: GitHub Issue #288

LM009-A coordination: GitHub Issue #289

Authoring evidence snapshot: `36be9e0a8613c690f2cb23043fe5ce35fab57b04`.
The publication launcher revalidates the relevant execution-time repository
anchors; this authoring SHA is evidence provenance, not a required future
`PUBLICATION_BASE`.

## Purpose

Define and prove the maturity needed for ordinary Protos development from a
modern IDE while keeping the editor subordinate to the real Protos language,
runtime, parser, resolver and tooling authorities.

Visual Studio Code is the first reference integration and end-to-end acceptance
surface. It is not a Protos semantic authority, and LM009 must not make future
editor support depend on reproducing Protos semantics inside a VS Code
extension.

## Existing foundation

LM009 consumes the already-approved hybrid Truffle-first editor architecture
from AUD002 and the closed I026 Truffle tooling foundation.

The current repository already establishes the following foundations:

- Protos is a registered Truffle guest language and retains exact source
  identity and source sections.
- user-executable source locations are exposed through the ratified
  instrumentation surface;
- debugger values and activation-native local scopes are available through
  faithful read-only Truffle projections;
- real GraalVM DAP behavior has been proven against Protos source for
  breakpoints, stepping, threads, stack frames, scopes, representative values,
  continue and clean disconnect;
- real GraalVM dynamic-LSP transport/document synchronization has been proven,
  while its current static-intelligence boundary is deliberately limited;
- `ProtosCli` already accepts an ordinary source path and executes that file
  through the real Protos runtime;
- parser failures already retain exact `SourceSpan` data suitable for
  source-accurate static diagnostics.

The corresponding durable I026 record is
[`../I026/I026_TRUFFLE_TOOLING_FOUNDATION.md`](../I026/I026_TRUFFLE_TOOLING_FOUNDATION.md).
The selected editor-tooling architecture is retained in
[`../AUD002/AUD002_GRAALVM_EDITOR_TOOLING_AUDIT.md`](../AUD002/AUD002_GRAALVM_EDITOR_TOOLING_AUDIT.md).

## LM009-A classification vocabulary

Every baseline capability is classified as exactly one of:

- `FOUNDATION_PROVEN` — the underlying Protos/runtime behavior already has
  evidence; later editor work may consume it without redefining it.
- `EDITOR_INTEGRATION_MISSING` — the required runtime/tool behavior exists or is
  straightforwardly invokable, but no user-facing VS Code integration is
  published.
- `STATIC_LANGUAGE_SERVICE_MISSING` — the capability requires Protos-specific
  static language intelligence over the real parser/source/module/package
  authorities.
- `DEFERRED_NON_BASELINE` — useful editor functionality that is intentionally
  unnecessary for initial LM009 closure.
- `DECISION_REQUIRED` — a substantive unresolved architecture/public-contract
  choice has been exposed. The owning later slice may audit alternatives but
  cannot silently select one.

`DECISION_REQUIRED` does not imply `PLATxxx` automatically. A later decision
uses the family appropriate to the actual authority: platform/runtime-specific
architecture may require `PLATxxx`; public CLI/tool policy or another durable
boundary must use its own proper owner. Identifier shape is not authority.

## Capability baseline

| Capability | Classification | Current evidence / missing boundary | LM009 owner |
|---|---|---|---|
| `.protos` language association | `EDITOR_INTEGRATION_MISSING` | No shipped editor language registration; VS Code must recognize the canonical extension. | B |
| Syntax highlighting | `EDITOR_INTEGRATION_MISSING` | Existing GitHub #11 already defines the bounded lexical-highlighting work. | B / #11 |
| Comments, brackets and basic editor configuration | `EDITOR_INTEGRATION_MISSING` | Editor metadata is absent; it must follow the normative grammar rather than invent syntax. | B |
| Run current file | `EDITOR_INTEGRATION_MISSING` | Runtime foundation exists: the CLI already accepts a source path directly. VS Code wiring is absent. | C |
| Runtime discovery/configuration | `EDITOR_INTEGRATION_MISSING` | The editor has no published way to locate/select the Protos runtime. | C |
| Public DAP launch/server lifecycle | `DECISION_REQUIRED` | I026 proved the real GraalVM DAP only as bounded test evidence and explicitly did not select public CLI, port, hosting or lifecycle policy. | D |
| Source breakpoints | `FOUNDATION_PROVEN` | I026-F proved verified file-backed Protos breakpoints through real GraalVM DAP. | E consumes |
| Stepping | `FOUNDATION_PROVEN` | I026-F proved real `next` to the following Protos source line. | E consumes |
| Threads and stack frames | `FOUNDATION_PROVEN` | I026-F proved threads and exact Protos stack/source location. | E consumes |
| Activation-local scopes | `FOUNDATION_PROVEN` | I026-E/F proved the PLAT015 activation-native read-only scope with no artificial global/receiver/parent fiction. | E consumes |
| Representative scalar/indexed debugger values | `FOUNDATION_PROVEN` | I026-D/F proved scalar values and expandable indexed Array values through the real debugger path. | E consumes |
| Static parse diagnostics on unexecuted source | `STATIC_LANGUAGE_SERVICE_MISSING` | The real parser and `ParseError`/`SourceSpan` foundation exist, but no static editor service publishes diagnostics. | F/G |
| Document symbols | `STATIC_LANGUAGE_SERVICE_MISSING` | Graal dynamic LSP does not currently advertise this; static Protos source knowledge is required. | G |
| Workspace symbols | `STATIC_LANGUAGE_SERVICE_MISSING` | Graal dynamic LSP does not currently advertise this; workspace/package knowledge is required. | G |
| Go to definition | `STATIC_LANGUAGE_SERVICE_MISSING` | Graal dynamic LSP does not currently advertise definition support; faithful lookup must reuse Protos source/module resolution. | G |
| References | `STATIC_LANGUAGE_SERVICE_MISSING` | Graal dynamic LSP does not currently advertise references; static indexing/semantic knowledge is required. | H |
| Completion | `STATIC_LANGUAGE_SERVICE_MISSING` | The current Graal dynamic-LSP result is faithfully empty; do not fabricate completion from a fake editor-side model. | H |
| Hover | `STATIC_LANGUAGE_SERVICE_MISSING` | The current Graal dynamic-LSP result is faithfully empty; static Protos intelligence is required for baseline-useful hover. | H |
| Signature help | `STATIC_LANGUAGE_SERVICE_MISSING` | The current Graal dynamic-LSP result is faithfully empty; any useful result must follow real callable semantics. | H |
| Install/package + clean end-to-end setup | `EDITOR_INTEGRATION_MISSING` | No reference VS Code extension/package/install workflow is published. | I |
| VS Code extension ownership/repository/packaging topology | `DECISION_REQUIRED` | AUD002 selected a thin extension but deliberately did not select its durable distribution/repository topology. | B/C decision checkpoint |
| Static language-service hosting/transport/lifecycle | `DECISION_REQUIRED` | AUD002 selected reuse of the real parser/semantic/resolver authorities but did not select a standalone/delegate server hosting architecture. | F decision checkpoint |
| Graal dynamic-LSP augmentation beyond the baseline | `DEFERRED_NON_BASELINE` | Transport/capability evidence exists, but initial IDE usability does not require manufacturing runtime-derived features that are currently empty. | later LM009 |
| Formatter / canonical formatting | `DEFERRED_NON_BASELINE` | Formatting policy is not required for the initial usable editor baseline. | deferred |
| Rename/refactoring engine | `DEFERRED_NON_BASELINE` | Useful later; not required for initial LM009 closure. | deferred |
| Debugger expression evaluation/mutation | `DEFERRED_NON_BASELINE` | PLAT013 intentionally kept the debugger baseline read-only. | deferred |

## Architecture boundaries exposed by the baseline

LM009-A selects **no new architecture**. It records three checkpoints that later
work must resolve before crossing their durable boundaries:

1. **Reference extension topology.** B/C may establish lexical metadata and thin
   invocation wiring, but repository/distribution/ownership topology must not be
   selected accidentally as an incidental `package.json` location.
2. **Public DAP launch contract.** D must compare how the proven GraalVM DAP is
   enabled, addressed, started/stopped and bound to an execution without turning
   test-only evidence into an accidental public contract.
3. **Static language-service hosting boundary.** F must determine how the real
   parser/source/module/package authorities are exposed to editor protocols
   without implementing a second Protos parser or semantic model in TypeScript.

If one of these is platform/runtime-specific and materially constrains later
implementations, it belongs at a `PLATxxx` approval gate. If it is public
CLI/tool/editor policy rather than platform architecture, it must be routed to
that proper authority instead. LM009 itself is not a generic decision bucket.

## Reference acceptance scenarios

Later slices must preserve these concrete scenarios.

### S1 — Open and edit

From a clean VS Code installation plus the documented Protos integration:

1. open a workspace containing ordinary `.protos` files;
2. VS Code recognizes them as Protos;
3. comments, delimiters and lexical categories are highlighted/configured
   consistently with the normative grammar;
4. no editor grammar changes compiler/runtime behavior.

### S2 — Run current file

Given an ordinary standalone source file:

1. open the file in VS Code;
2. invoke the reference Run Protos File action;
3. the integration invokes the real Protos runtime rather than an editor-side
   evaluator;
4. stdout/stderr and exit outcome are visible through the ordinary editor
   workflow;
5. the user does not need Maven or repository-internal class names merely to run
   the file.

### S3 — Debug ordinary Protos source

Given a file containing at least two instrumentable source statements and
representative local scalar/indexed values:

1. set a source breakpoint in VS Code;
2. launch debugging with F5 or the documented equivalent;
3. stop at the verified Protos source location;
4. observe threads and the correct stack frame;
5. inspect the activation-local scope and representative values;
6. step to the next expected Protos source location;
7. continue to normal completion;
8. terminate/disconnect cleanly;
9. do not expose artificial global scope, receiver binding, lexical-parent
   fiction or host implementation state.

### S4 — Diagnose unexecuted source

Given a `.protos` file that has not been executed:

1. introduce a syntax error;
2. receive a source-accurate diagnostic derived from the real Protos parser
   authority;
3. fix the error and observe the diagnostic clear;
4. no duplicate editor grammar/parser is treated as semantic authority.

### S5 — Navigate unexecuted workspace source

Given a workspace with a faithful symbol/definition relationship expressible by
current Protos source/module semantics:

1. inspect the symbol without first executing the source;
2. navigate to its definition using the static Protos language service;
3. source/module/package resolution follows the same canonical authorities used
   by Protos rather than editor-specific path guessing.

LM009 baseline closure requires useful symbol/navigation support, not every
possible dynamic dispatch target to become statically decidable.

### S6 — Clean installation

From a clean supported environment:

1. install/configure the reference VS Code integration from published
   instructions;
2. configure or discover an appropriate Protos runtime;
3. repeat S1, S2, S3 and the baseline static-language scenarios without a
   checkout-specific hidden setup;
4. remove/disable the extension without altering ordinary Protos runtime
   behavior.

## Slice dependency map after A

Publication of LM009-A releases investigation/implementation work as follows:

```text
LM009-A  baseline
├── LM009-B  language association + highlighting (#11)
├── LM009-C  run-current-file integration
├── LM009-D  public DAP launch-contract audit/decision path
└── LM009-F  static language-service architecture audit
      |
      └── LM009-G  diagnostics + symbols + definition

LM009-D ──> LM009-E  VS Code debugging integration
LM009-F/G ──> LM009-H completion + hover + signature help + references
B + C + E + G + required H baseline subset ──> LM009-I packaging/end-to-end closure
```

The graph expresses dependency order, not automatic approval. B/C/D/F must stop
at any substantive decision checkpoint identified above.

## LM009-A closure evidence

LM009-A is closed by publication of this record when all of the following are
true:

- the required capability set is classified;
- every baseline capability has a later owner;
- current CLI, parser, I026 DAP and I026 dynamic-LSP evidence is distinguished
  from missing editor/static-service integration;
- exact reference scenarios S1-S6 are recorded;
- unresolved durable architecture is visible rather than silently selected;
- no VS Code extension, public DAP launcher or static language server is
  implemented by A;
- no specification, runtime implementation, public API, license terms or Maven
  implementation version changes are made.

The next independent work fronts after publication are LM009-B/#11, LM009-C,
LM009-D investigation and LM009-F investigation.
