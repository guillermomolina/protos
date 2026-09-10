# AUD002 — GraalVM / Truffle editor-tooling compatibility audit

Status: **CLOSED — HYBRID TRUFFLE-FIRST ARCHITECTURE APPROVED**

Nature: non-normative implementation/tooling architecture audit

Authoring evidence base: `09b593c96d6ad6e2f5b496975531cb18c9fe2e0c`

Audit date: 2026-09-08

## Purpose

Determine how much of a future Protos editor/debugging experience can reuse
GraalVM's language-agnostic tooling instead of duplicating runtime knowledge in
a VS Code extension or implementing an independent debugger and language server
from scratch.

On 2026-09-08 the project owner explicitly approved the audit recommendation:
Alternative C, the hybrid Truffle-first architecture. This closure records that
durable project choice and allocates only the runtime/compiler foundation as
`I026`; it does not change Protos semantics or claim current DAP/LSP support.

## Repository material inspected

Project/governance material:

- `AGENTS.md`
- `ROADMAP.md`
- `docs/design/TOOLCHAIN_TOOL_ARCHITECTURE.md`
- `docs/project/registries/IMPLEMENTATION_STATUS.md`
- `docs/project/history/OPEN_TASKS.md`

Implementation material:

- `pom.xml`
- `src/main/java/com/guillermomolina/protos/execution/ProtosRootNode.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosExpressionNode.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosSourceCompiler.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosSourceFileLoader.java`
- `src/main/java/com/guillermomolina/protos/source/SourceSpan.java`
- repository-wide searches for `TruffleLanguage`, `InstrumentableNode`,
  `SourceSection`, `StandardTags`, `ExportLibrary`, `InteropLibrary`,
  `NodeLibrary`, `--dap`, and `--lsp`

No normative specification change is required merely to expose existing source,
stack, scope, or value information to development tooling. Any future tool
feature that would change observable Protos semantics remains subject to the
normal specification/design process.

## External GraalVM evidence

The audit used current GraalVM 25.3 documentation and Truffle Javadocs:

- GraalVM tools overview:
  `https://www.graalvm.org/jdk25.3/reference-manual/tools/`
- GraalVM Language Server Protocol:
  `https://www.graalvm.org/jdk25.3/tools/lsp/`
- GraalVM Debug Adapter Protocol:
  `https://www.graalvm.org/jdk25.3/tools/dap/`
- Truffle `RootNode` Javadoc:
  `https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html`
- Truffle `InstrumentableNode` Javadoc:
  `https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/InstrumentableNode.html`
- Truffle `TruffleLanguage` Javadoc:
  `https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html`
- Truffle `NodeLibrary` Javadoc:
  `https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/NodeLibrary.html`
- Truffle `InteropLibrary` Javadoc:
  `https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html`

The GraalVM tools overview lists both LSP and DAP among the tools for Graal
languages. The DAP documentation describes attaching VS Code to a GraalVM guest
language through the Debug Adapter Protocol. The LSP documentation is more
specific about its role: the Graal language server primarily contributes
runtime-derived dynamic information and can delegate to language-specific
servers for static information, merging the results.

The `RootNode` Javadoc is the decisive compatibility constraint for the current
implementation. A root constructed without an associated `TruffleLanguage` is
considered not instrumentable. The same Javadoc lists the expected ingredients
for instrumentation: a non-null language, an instrumentable root, source
sections, instrumentable AST nodes, and preferably standard tags.

## Current Protos findings

### 1. Protos uses Truffle ASTs but is not currently a registered Truffle guest language

`ProtosRootNode` extends Truffle `RootNode`, so execution already uses real
Truffle call targets and AST nodes.

However its constructor currently calls:

```java
super(null);
```

The repository contains no `TruffleLanguage` subclass or
`@TruffleLanguage.Registration` integration. Under the current Truffle contract,
that means the root has no guest-language identity and is not instrumentable by
the normal tooling framework.

This is the first hard prerequisite. Using Truffle nodes alone is not enough to
make Protos a tooling-visible guest language.

### 2. Execution nodes retain spans but do not expose Truffle source sections

`ProtosExpressionNode` already stores a `SourceSpan` for every execution node:

```java
private final SourceSpan span;
```

The canonical-to-Truffle lowering pipeline preserves those spans when creating
execution nodes. This is valuable existing infrastructure: the implementation
already knows which source range produced each semantic/execution node.

But the nodes do not currently expose Truffle `SourceSection` values, do not
implement `InstrumentableNode`, and do not provide `StandardTags` such as
statement/call/root tags. Repository search found no current use of these
instrumentation APIs.

Therefore breakpoints, stepping, source-attributed profiling, and debugger event
locations cannot yet be expected to map reliably onto Protos source through the
standard Truffle instrumentation layer.

### 3. Source range provenance exists, but source identity is currently lost at the compiler boundary

`SourceSpan` stores half-open offsets, which is enough to derive line/column
locations once paired with the actual source object.

The current compiler boundary is:

```java
public CallTarget compile(String source)
```

and `ProtosSourceFileLoader` reads a `Path` into a Java `String` and then calls
that method. The selected file/path identity is therefore not propagated into
the compiled root as a Truffle `Source`.

A tooling-capable implementation needs to preserve both pieces:

```text
source identity / URI / name
+
SourceSpan offsets
=
Truffle SourceSection
```

The existing spans make this a bounded plumbing problem rather than a parser
redesign, but the source/compiler/root APIs must carry source identity far enough
for tooling to reconstruct exact locations.

### 4. Debugger-visible scopes are not currently exported through Truffle tooling APIs

Protos already has its own semantic activation/context machinery, including
`ProtosActivation`, task state, lexical contexts, receiver semantics, and object
slots. That semantic model must remain authoritative.

Repository search found no current Truffle top-scope export, `NodeLibrary` local
scope implementation, or equivalent debugger scope bridge. Truffle's debugger
uses language/top scopes and node-local scopes to enumerate variables visible at
a suspension point.

This means a future debugger bridge must adapt the existing Protos execution
context model into tooling views without inventing a second variable model or
changing lookup semantics.

### 5. Protos object values are not currently exported as a general Truffle interop value model

Repository search found no current `InteropLibrary` / `ExportLibrary` integration
for the Protos runtime value model.

Truffle debugger values and scopes are interop-facing. Primitive Java values
already have defined interop representations, but ordinary Protos objects,
Closures, Arrays, Maps, Errors, Futures, Actors, Paths, Files, and other runtime
values need an explicit debugger/tooling view if their members, display strings,
executability, source locations, or meta-information are to be inspected
usefully.

That bridge must preserve Protos identity and access semantics. It must not make
Java reflection, host field names, or implementation-only state appear as Protos
slots merely because that would be convenient for a debugger.

### 6. The current CLI does not expose Graal DAP/LSP launcher options

Repository search found no current `--dap` or `--lsp` integration in the Protos
CLI. The current runtime is launched as the Protos Java application rather than
as a registered language launcher exposing Graal language options.

Even after instrumentation is added, an explicit launcher/embedding integration
will be needed to enable and configure the desired Graal tools for a Protos
process.

### 7. Editor integration is already consistent with the project roadmap, but its ownership is not yet selected

`ROADMAP.md` already lists editor integration and language-aware tooling as a
later project direction.

The selected toolchain architecture distinguishes official bundled Protos tools
from external editor/plugin mechanisms and explicitly leaves third-party
extension architecture as a separate future problem. Therefore this audit does
not classify a VS Code extension or language-service host as `TOOL003` merely
because it is developer tooling.

That ownership decision should be made together with the editor architecture,
not smuggled in through identifier allocation.

## What GraalVM can plausibly provide

### Debugging: high reuse potential

If Protos becomes a properly registered and instrumentable Truffle language,
GraalVM's DAP implementation is a strong candidate for the protocol/debugger
engine layer. It can potentially provide the standard VS Code-facing debugging
protocol, breakpoint plumbing, stepping, stack handling, and threading support
without a Protos-specific DAP server.

This is **not yet proven for Protos**. The current audit establishes prerequisites
and plausibility, not successful runtime compatibility. A dedicated smoke slice
must demonstrate source breakpoints, stepping, stack frames, scopes and values on
the actual Protos runtime before the project claims DAP support.

### Language services: useful but not a complete static Protos language server

The GraalVM LSP is useful for runtime-derived dynamic information. Its own
documentation explicitly describes static source information as something that
may come from a language-specific delegate server.

That distinction matters for Protos. An editor must work before code has run and
for modules that may never execute during the current session. Parse diagnostics,
workspace/module resolution, document symbols, definition lookup, rename safety,
and many completion cases depend on the Protos parser, semantic AST and package
resolver rather than runtime observation alone.

Therefore Graal LSP should be evaluated as reusable dynamic tooling, not assumed
to eliminate all need for Protos-specific static language intelligence.

## Alternatives

### Alternative A — Implement Protos-specific LSP and DAP from scratch

Advantages:

- full control over protocol behavior;
- no dependency on Graal tooling implementation details.

Costs/risks:

- duplicates debugger machinery already available in the Truffle ecosystem;
- risks maintaining source, stack, scope and runtime knowledge in parallel;
- increases the chance that editor/debugger semantics drift from the actual
  runtime;
- larger VS Code integration and maintenance surface.

Assessment: unjustified as the default starting point, especially for DAP.

### Alternative B — Use only GraalVM LSP/DAP and implement no Protos-specific language service

Advantages:

- smallest custom tooling surface;
- maximizes reuse.

Costs/risks:

- current Protos runtime is not instrumentable yet;
- Graal LSP is intentionally dynamic-data oriented and does not replace all
  static source analysis;
- Protos package/module/delegation semantics need language-specific knowledge;
- usefulness for unexecuted workspace code would be limited.

Assessment: insufficient as a complete editor strategy.

### Alternative C — Hybrid Truffle-first tooling

Conceptual shape:

```text
Protos parser / semantic model / package resolver
                  |
                  +---- static Protos language intelligence
                  |
Protos Truffle language + source/instrumentation/interop
                  |
                  +---- GraalVM DAP
                  +---- GraalVM dynamic LSP data
                               |
                               v
                       thin VS Code extension
```

Advantages:

- one runtime truth for execution/debugging;
- reuses Graal's language-agnostic debugger protocol implementation;
- keeps static analysis tied to the real Protos parser/resolver instead of a
  duplicated TypeScript grammar;
- permits runtime-derived information to complement static analysis;
- keeps the VS Code extension primarily as editor/transport integration.

Costs/risks:

- requires first-class `TruffleLanguage` integration and instrumentation work;
- scope/value interop must be designed carefully around Protos semantics;
- some static LSP features will still require Protos-specific implementation;
- actual Graal DAP/LSP compatibility must be demonstrated rather than inferred.

Assessment: strongest current candidate.

## Selected architecture — explicit project-owner approval

**Selected direction:** Alternative C is the durable editor-tooling architecture:

1. make Protos a real registered `TruffleLanguage` without changing language
   semantics;
2. propagate source identity alongside existing `SourceSpan` data and expose
   exact `SourceSection`s;
3. instrument the relevant execution nodes and tags through standard Truffle
   instrumentation;
4. expose debugger scopes and Protos values through semantically faithful
   Truffle scope/interop views;
5. prove GraalVM DAP against real Protos source before implementing a custom DAP;
6. evaluate GraalVM LSP as dynamic augmentation rather than as the sole language
   service;
7. implement Protos-specific static language-service features by reusing the
   existing parser, semantic AST, module/package resolution, and diagnostics;
8. keep the VS Code extension thin and avoid reimplementing Protos semantics in
   TypeScript.

The project owner explicitly approved this architecture on 2026-09-08. The
approval selects the responsibility boundaries above; it does not convert
unproven GraalVM compatibility into a support claim, and the DAP/LSP smoke gates
remain required implementation evidence.

## Approved implementation decomposition

The runtime/compiler foundation A-G is allocated as `I026` because it modifies
the Protos implementation boundary rather than bundled-tool policy or general CLI
UX. Public CLI integration, the VS Code extension and Protos-specific static
language-service work remain separately classified future work.

```text
A  registered TruffleLanguage + Source ownership
B  RootNode/source-section integration
C  InstrumentableNode + StandardTags coverage
D  local/top scope bridge
E  interop/debug display for core runtime values
F  Graal DAP source-breakpoint/stepping/scope smoke gate
G  Graal dynamic-LSP smoke gate
H  thin VS Code language association/run/debug integration
I  static Protos language-service diagnostics/navigation/completion as needed
```

`I026` owns only the Truffle tooling foundation and proof gates. This approval
does not allocate `TOOL003`; later CLI/editor/static-language-service work must
follow the already selected family boundaries when each surface becomes concrete.

## Audit outcome

Evidence phase: **COMPLETE**

Architecture selection: **SELECTED — ALTERNATIVE C / HYBRID TRUFFLE-FIRST**

Current runtime compatibility claim: **NOT YET — prerequisites missing**

Normative specification changed: **NO**

Implementation version changed: **NO**

Implementation source changed: **NO**

License terms changed: **NO**
