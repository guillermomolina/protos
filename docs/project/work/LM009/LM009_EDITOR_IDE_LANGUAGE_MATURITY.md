# LM009 — Editor & IDE Language Maturity

Status: **IN_PROGRESS**

Current published slice after this record: **LM009-A CLOSED; LM009-B CLOSED (S1 PASS)**

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

## LM009-B approved current reference-extension topology

Decision status: **APPROVED CURRENT TOPOLOGY**

Approval: explicit project-owner approval on 2026-09-10, with future
reconsideration deliberately permitted.

The first reference VS Code integration is selected to live inside the
`guillermomolina/protos` monorepo at:

```text
editors/
└── vscode/
```

This is the current-scale ownership topology, not a permanent prohibition on a
future repository split.

The selected boundary is:

- `spec/` remains the normative lexical/syntactic authority;
- the real Protos parser, resolver, runtime and later static language-service
  implementation remain their own authorities rather than being reimplemented
  in the extension;
- `editors/vscode/` is a thin editor/product-integration subtree;
- the editor subtree may own its own package version and Node/editor
  dependencies, but ordinary Protos Maven builds and runtime execution must not
  depend on Node, npm or VS Code assets merely because the reference extension
  exists;
- lexical assets should remain reusable where practical rather than encoding a
  VS Code-only semantic model;
- a future move to a dedicated `protos-vscode` repository remains allowed when
  independent editor release cadence, contributor volume, multiple editor
  clients, or product ownership makes the additional cross-repository
  coordination worthwhile.

This approval resolves only the repository/distribution topology checkpoint
exposed by LM009-A. It deliberately does **not** select:

- a Visual Studio Marketplace publisher identifier or permanent extension ID;
- the final minimum/supported VS Code version policy;
- public VSIX/Marketplace release policy;
- Protos runtime discovery/configuration UX;
- public GraalVM DAP launcher/port/server lifecycle;
- static Protos language-service process/transport/lifecycle architecture; or
- Graal dynamic-LSP product policy.

Those remain independently owned by the later LM009 slices that expose them.
Any substantive new choice encountered while implementing LM009-B must still
stop at the normal approval gate.

The LM009-A capability matrix records the topology row as `DECISION_REQUIRED`
because that was the correct state at A closure. This section is the durable
post-A resolution of that checkpoint; the historical A classification is not
rewritten retroactively.

With this topology published, LM009-B implementation is released to add only
the approved lexical/editor-metadata surface under `editors/vscode/`: `.protos`
language association, comment/bracket configuration, non-normative TextMate
highlighting, representative fixtures/validation, and development/install-use
instructions. Run integration remains LM009-C; DAP remains LM009-D/E; static
language intelligence remains LM009-F/G/H; public packaging/Marketplace closure
remains LM009-I.

## LM009-B lexical-assets tranche

Status: **LEXICAL ASSETS PUBLISHED; INSTALLABLE MANIFEST DECISION PENDING**

This tranche establishes the reusable lexical/editor asset substrate under the
approved `editors/vscode/` topology without yet creating an installable VS Code
extension.

Published assets:

- `editors/vscode/syntaxes/protos.tmLanguage.json` — non-normative TextMate
  grammar following the current Core v0.1 lexical surface;
- `editors/vscode/test/fixtures/lexical.protos` — representative valid-source
  fixture covering comments, String forms/escapes, numeric families, exact
  reserved spellings, member-name reserved spellings, Closures, contextual
  ellipsis and standard/custom operators;
- `editors/vscode/test/validate_grammar.py` — editor-local structural guard; and
- `editors/vscode/README.md` — authority boundary and development status.

The TextMate asset intentionally highlights only the seven exact Core v0.1
reserved spellings (`this`, `context`, `args`, `super`, `true`, `false`,
`null`) as language-special tokens. Familiar spellings such as `if`, `else`,
`while`, `class`, `function`, `try`, `catch`, `async`, `await`, `var`, `let`,
`const`, `import`, and `export` are not promoted into a fake keyword set.

Reserved spellings immediately following member-access `.` are deliberately not
highlighted as language-special tokens because the normative grammar permits
them there as ordinary structural member names.

This tranche does not publish `package.json`, `language-configuration.json`,
runtime discovery, Run Current File, DAP, LSP, Marketplace metadata, or any
TypeScript semantic implementation. Therefore it does not yet satisfy LM009
scenario S1 end-to-end and does not close LM009-B.

The next LM009-B checkpoint is the already-exposed installable-manifest
compatibility decision: select the bounded `engines.vscode` support/development
floor before adding the manifest, `.protos` language association and basic
language configuration. That choice remains unselected by this publication.

## LM009-B manifest identity and VS Code support floor

Decision status: **APPROVED**

Approval: explicit project-owner approval on 2026-09-10 after confirming that
`guillermomolina` is an already-owned Visual Studio Marketplace publisher.

The approved manifest contract is:

- publisher: `guillermomolina`;
- extension name: `protos`;
- resulting extension identity: `guillermomolina.protos`;
- initial extension version: `0.1.0`;
- `engines.vscode`: `^1.104.0`;
- language id: `protos`; and
- canonical file association: `.protos`.

The extension version is intentionally independent from the Protos Maven/runtime
implementation version. The VS Code support floor is stable by default: it is
not raised merely because newer VS Code releases exist. A later floor increase
requires a demonstrated editor API or dependency need introduced after 1.104
and must validate that new compatibility boundary.

This approval does not publish a Marketplace release, choose a release cadence,
select runtime discovery, public DAP lifecycle, static-language-service hosting,
or change Protos specification/runtime semantics.

## LM009-B declarative manifest-wiring tranche

Status: **MANIFEST WIRED; S1 LIVE VS CODE EVIDENCE PENDING**

This tranche turns the already-published lexical assets into a declarative VS
Code extension surface without executable extension-host code.

Published wiring:

- `editors/vscode/package.json` contributes the `protos` language, canonical
  `.protos` association and existing `source.protos` TextMate grammar under the
  approved `guillermomolina.protos` identity and `^1.104.0` support floor;
- `editors/vscode/language-configuration.json` configures only the normative
  line/block comment delimiters plus ordinary `()`, `[]` and `{}` structural
  bracket pairs;
- `editors/vscode/test/validate_extension.py` guards identity, compatibility
  floor, language association, grammar binding and the intentionally declarative
  no-runtime-code boundary; and
- `editors/vscode/README.md` documents repository-local validation and the live
  VS Code S1 check.

The language configuration deliberately does not invent `wordPattern`,
indentation, folding, on-enter, quote, formatter, parser, or semantic behavior.
Those surfaces require their own real authority or evidence if later work needs
them.

Repository-side structural validation can prove that the VS Code manifest
wiring is internally coherent, but it cannot honestly replace observation in an
actual VS Code extension host. LM009-B therefore remains `IN_PROGRESS` until
the documented S1 live check confirms that VS Code recognizes a `.protos` file
as Protos and applies the published grammar/configuration.

No Protos runtime source, specification, Maven implementation version, public
DAP contract, static language-service architecture or Marketplace release is
changed by this tranche.

## LM009-B S1 live evidence and closure

Status: **CLOSED**

Live evidence was completed by the project owner on 2026-09-10 using a real
Visual Studio Code Extension Development Host against the published
LM009-B manifest surface from `e9ca30d61cb53645a66542710182db5e5b39db91`.

Observed S1 behavior:

- an ordinary `.protos` file was recognized as language mode **Protos**;
- the published `source.protos` TextMate grammar was visibly active over the
  representative lexical fixture;
- the editor's line/block comment actions used the configured `//` and
  `/* ... */` forms; and
- typing `(`, `[` and `{` produced the configured closing delimiters.

This completes scenario S1 together with the repository-side lexical and
manifest structural validators. LM009-B therefore satisfies its closure rule:
the approved reference-editor topology is present, `.protos` association and
useful non-normative highlighting are implemented, basic comment/bracket
configuration follows the grammar, representative fixture/validation and
development/use instructions are present, and no Protos semantic authority is
duplicated in the extension.

LM009 remains **IN_PROGRESS**. Run-current-file integration belongs to LM009-C;
public DAP launch policy and VS Code debugging remain LM009-D/E; static language
intelligence remains LM009-F/G/H; and public packaging/clean end-to-end closure
remains LM009-I.

This closure changes no Protos specification, runtime implementation, Maven
implementation version, public DAP contract, static-language-service
architecture, Marketplace release state, or Node/npm dependency boundary.
