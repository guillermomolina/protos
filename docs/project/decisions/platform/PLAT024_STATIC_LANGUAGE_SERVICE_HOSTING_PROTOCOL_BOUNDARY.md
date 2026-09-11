# PLAT024 — Static language-service hosting and protocol boundary

Status: **RATIFIED — Candidate A′ selected**

Nature: durable non-normative static-tooling host/process/protocol architecture decision

Approved by project owner: **2026-09-11**

GitHub Issue: **#342**

Primary consumer: `LM009-F` / GitHub #340, then LM009-G/H static editor intelligence.

Normative effect: **none**. PLAT024 selects the platform/tooling-host boundary for static
editor intelligence over unexecuted Protos source. It does not define Protos syntax,
name lookup, module/package semantics, definition/reference/completion behavior,
diagnostic semantics, or any other observable language rule.

## Selected architecture — Candidate A′

The initial Protos static language service is a **dedicated toolchain-matched process**
owned by the editor/client session. Its baseline editor protocol is standard LSP over
stdin/stdout. Static analysis is implemented behind a protocol-neutral, editor-neutral
analysis core that directly reuses the real Protos parser/source/module/package
authorities.

Conceptually:

```text
VS Code / another LSP editor
        |
        | standard LSP over stdio
        v
thin protocol adapter / server host
        |
        v
editor-neutral Protos static-analysis core
        |
        +-- ProtosParser / Surface* / SourceSpan
        +-- canonical module/package resolution authorities
        +-- document/workspace snapshots and indexes
```

The initial server may be JVM-hosted because the current parser and resolver authorities
are JVM APIs. Java/JVM hosting is **not** the durable semantic contract. A future native,
Native Image, or self-hosted Protos implementation may replace the server implementation
behind the same editor-neutral LSP/core boundary.

## Fixed invariants

1. `spec/` remains normative for Protos language semantics.
2. `ProtosParser(String).parseProgram()` and the existing `Surface*` / `SourceSpan`
   source model remain the current source-structure authority unless separately evolved.
3. Existing Protos module/package resolution authorities remain canonical; editor
   URI/path guesses cannot replace them.
4. Static analysis must operate on unexecuted source; guest execution is not a
   prerequisite for baseline diagnostics/navigation/intelligence.
5. VS Code and other editor clients remain thin and must not contain a second Protos
   parser or parallel semantic model.
6. GraalVM dynamic LSP remains optional runtime-derived augmentation, not the sole
   static service.
7. Ordinary Protos execution pays no language-server process, indexing, snapshot,
   thread, synchronization, or lifecycle cost when static tooling is not active.
8. No global mutable semantic registry or mandatory system-wide daemon is introduced.
9. Remote/Dev Container/SSH service execution belongs in the workspace-host namespace
   containing the selected toolchain and source tree.
10. Static analysis state is independently disposable and must not own live Protos
    Process, Actor, Task, Future, debugger, or guest execution state.
11. Protocol/client code remains separable from the reusable Protos static-analysis
    core.
12. Backend evolution, including Truffle Bytecode DSL and a future non-Truffle runtime,
    must not require editor clients to reimplement Protos semantics.

## Client and lifecycle boundary

Baseline lifecycle is client-owned:

```text
editor/client starts server
        |
        v
LSP initialize
        |
        v
document/workspace snapshots + static requests
        |
        v
LSP shutdown / exit
        |
        v
server process terminates
```

One active editor/client session owns one server process by default. A server may own
multiple workspace-root models for that client, but roots must not silently share
mutable semantic state when their package/module authorities differ.

The baseline transport is stdio because it requires no port registry, daemon discovery,
authentication, stale-socket cleanup, or client/host path translation. Socket or other
transports may be added later for development or independently justified deployment
scenarios without changing the static-analysis authority boundary.

## Exhaustive Truffle implementation survey

The approval audit screened the complete current project Truffle catalogue: all
16 principal implementations plus the 21 experimental/historical entries required by
GITHUB010.

### Principal implementations

- Enso
- Espresso
- FastR
- GraalJS
- GraalPy
- GraalWasm
- grCUDA
- **Apple Pkl**
- SimpleLanguage
- SOMns
- Sulong / LLVM
- TRegex
- TruffleRuby
- TruffleSOM
- TruffleSqueak
- Yona

### Experimental / historical screen

- BACIL
- bf
- brainfuck-jvm
- Cover
- DynSem
- Heap Language
- hextruffe
- islisp-truffle
- LuaTruffle
- Mozart-Graal
- Mumbler
- PorcE
- ProloGraal
- PureScript
- Reactive Ruby
- shen-truffle
- TruffleBF
- streamblocks-graalvm
- TruffleMATE
- TrufflePascal
- ZipPy

No screened implementation exposed a maintained static-service topology stronger for
Protos than a dedicated editor-neutral service, an independent ecosystem static server,
or a project-oriented server separated from live guest execution. The experimental and
historical set did not reveal a distinct architecture family that improves on Candidate
A′ for Protos.

## Strongest transferable precedents

### Apple Pkl

Apple Pkl is the closest maintained Truffle precedent.

Its VS Code integration uses a thin `LanguageClient` and normally launches the Pkl
Language Server as a **separate JVM process**. Optional socket operation exists, but the
ordinary integration does not require a global daemon. Static document/project/package
state lives in the language server, which invokes Pkl parser/library authorities rather
than reimplementing Pkl semantics in TypeScript.

The transferable boundary is:

```text
thin editor client
    -> standard language-server protocol
        -> persistent static-analysis service
            -> real language/project authorities
```

This is the strongest direct precedent for A′.

### Enso

Enso maintains a project/language-server subsystem with persistent project state and
indexing. Its editor/static state is not merely a projection of the currently executing
Truffle stack. Enso is operationally heavier than the desired Protos baseline, but it
strongly supports independent lifecycle and persistent project analysis.

### Espresso / Java

Java's mature static tooling remains in compiler/language-server infrastructure such as
JDT LS rather than making an Espresso guest execution context the static source authority.
The lesson is strong execution/static-tool separation and editor-neutral service
scalability, not that Protos should copy Java's tooling complexity.

### FastR and TruffleRuby

GraalVM's dynamic-LSP model can delegate static information to language-specific servers.
FastR/R and TruffleRuby/Ruby provide strong evidence that runtime-derived information is
augmentation rather than a replacement for static source analysis.

### Sulong / LLVM

LLVM execution remains separate from high-level C/C++ static tooling. This provides strong
failure-isolation and scaling evidence even though the high-level source authority lies
above Sulong itself.

### GraalJS and GraalPy

JavaScript/TypeScript and Python static tooling similarly demonstrate that a guest runtime
need not own editor workspace semantics. Their exact static engines are ecosystem-specific,
so they carry less direct authority-reuse weight than Pkl.

### GraalWasm, grCUDA, TRegex, SimpleLanguage, SOMns, TruffleSOM,
### TruffleSqueak and Yona

These implementations were screened for contradictory evidence. Their source/tooling
models are synthetic, source-language-external, image/environment-oriented, tutorial,
research-oriented, or historically weaker for this exact decision. None provides a
strong reason to embed Protos static semantics in VS Code, bind them to RuntimeHost, or
adopt a global shared daemon.

## Outside-Truffle evidence

The audit also considered mature non-Truffle language-service designs.

- **rust-analyzer** keeps a reusable analysis architecture separate from the LSP/JSON
  protocol edge and runs as an editor-neutral persistent service.
- **Eclipse JDT LS** is a standalone Java language server built on compiler/language
  tooling rather than a live application VM.
- Mainstream LSP editor integrations use thin language clients that own or connect to a
  server process while semantic work remains server-side.

These systems reinforce the distinction between an editor protocol edge and the reusable
language-analysis authority.

## Candidate set and disposition

### A′ — dedicated toolchain-matched static LSP process over stdio

**Selected.** One editor/client session starts one matching Protos language-server
process in the workspace host. The server owns persistent static-analysis snapshots and
indexes and directly reuses Protos language/project authorities.

### B — GraalVM dynamic LSP as the outer server plus a Protos static delegate

Not selected as the baseline. It couples unexecuted-source static analysis to Truffle/Graal
tooling lifecycle and introduces capability-merging/precedence policy before that
augmentation provides sufficient value.

### C — static semantic engine inside the VS Code extension

Rejected. It violates the single-authority rule, ties semantics to one editor and creates
a second parser/model implementation surface.

### D — dedicated Protos process with a private RPC protocol

Not selected. It preserves process separation but adds a second protocol plus per-editor
translation layer when LSP already supplies the editor-neutral contract required by
LM009-G/H.

### E — one-shot `protos analyze ...` process per request/file

Not selected. It avoids persistent service lifecycle at the cost of process startup,
repeated workspace resolution, weak coherent snapshot ownership and poor symbol/reference
index scaling.

### F — global/shared Protos language-server daemon

Not selected as the baseline. It can reduce aggregate process count but immediately
introduces discovery, authentication/ownership, version skew, cross-workspace isolation,
stale-state cleanup and global service failure concerns.

### G — self-host the language server as a bundled Protos program immediately

Deferred as a future destination. It is highly aligned philosophically, but current parser
and resolver authorities are host JVM APIs. Selecting G now would require either exposing a
large privileged guest capability surface or duplicating those authorities in Protos.

### H — host static analysis in the normal RuntimeHost/debug process

Rejected. It couples editor static availability, memory and lifecycle to guest execution,
weakens failure isolation and violates the requirement that unexecuted-source analysis not
require a live Protos execution Context.

## Required GITHUB010 scorecard

Scores are 1–5. Confidence is `H` (high) or `M` (medium). Arithmetic supports comparison;
it is not decision authority.

| Criterion | **A′ dedicated LSP** | B Graal+delegate | C VS Code engine | D private RPC | E one-shot | F shared daemon | G self-host now | H RuntimeHost |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / authority reuse | **5/H** | 4/H | 2/H | 4/H | 3/H | 4/H | 3/M | 4/H |
| Protos alignment | **5/H** | 3/H | 1/H | 3/H | 3/H | 2/H | **5/M** | 2/H |
| Future-option resilience | **5/H** | 3/H | 2/H | 3/H | 2/H | 3/H | **5/M** | 2/H |
| Scalability | **5/H** | 4/H | 4/H | 4/H | 1/H | **5/H** | 3/M | 3/H |
| Conceptual simplicity | 4/H | 3/H | 4/H | 2/H | **4/H** | 2/H | 2/M | 3/H |
| Portability / implementation freedom | **5/H** | 4/H | 2/H | 3/H | **5/H** | 4/H | **5/M** | 3/H |
| Runtime / resource cost | 4/H | 3/H | 4/H | 4/H | 1/H | **5/H** | 3/M | 2/H |
| Failure / operability | **5/H** | 3/H | 3/H | 3/H | 4/H | 2/H | 2/M | 2/H |
| Reversibility / migration | **5/H** | 4/H | 2/H | 4/H | 4/H | 3/H | **5/M** | 3/H |
| Evidence maturity / implementation risk | **5/H** | 4/H | 3/H | 3/H | 4/H | 4/H | 2/M | 3/H |
| **Total / 50** | **48** | **35** | **27** | **33** | **31** | **34** | **35** | **27** |

## Focused future / scalability / Protos-philosophy scoring

The owner's final comparison additionally scored the candidates on the three requested
0–10 axes:

| Candidate | Future endurance | Scalability | Protos philosophy | Total / 30 |
| --- | ---: | ---: | ---: | ---: |
| **A′ dedicated LSP + stdio + neutral core** | **10** | **9.5** | **10** | **29.5** |
| B Graal dynamic-LSP + delegate | 7 | 8 | 6.5 | 21.5 |
| C VS Code static engine | 4 | 8 | 2 | 14 |
| D separate process + private RPC | 7 | 8 | 5.5 | 20.5 |
| E one-shot analyzer | 5 | 3 | 6 | 14 |
| F shared daemon | 7.5 | **10** | 4 | 21.5 |
| G self-host immediately | 9.5 | 7 | **10** | 26.5 |
| H RuntimeHost static analysis | 4 | 6 | 3 | 13 |

The scorecard confirms A′ as the strongest bundle, but the selection rests on the
qualitative authority/lifecycle/future constraints rather than arithmetic alone.

## Scalability and future stress

### Large workspaces and many documents

The service is persistent for the client session. Document snapshots and indexes may be
incremental and partitioned by workspace/project. Process count is not proportional to
files or requests.

Conceptually, with `E` active editor/client sessions, `R` workspace roots and `D`
documents/indexed units:

```text
server processes    O(E)
root/project models O(R)
document/index data O(D)
global daemon state O(0)
guest Processes     O(0) for baseline static analysis
Truffle Contexts    O(0) for baseline static analysis
```

Exact index structures and eviction policy are implementation choices and may evolve.

### Multiple editors and clients

Each client session is independent by default. There is no mandatory global daemon,
global port allocator or shared mutable semantic registry. This favors failure isolation
and permits independent toolchain versions to coexist.

### Remote / Dev Container / SSH

The server runs in the workspace host, next to the source tree and selected toolchain.
Client-machine/workspace-host path translation is therefore not part of the baseline
service architecture.

### Failure and restart

A language-server failure discards editor-analysis state but does not terminate an
ordinary Protos execution or debug session. The client may restart the server and rebuild
state from workspace/document snapshots.

### Bytecode DSL / optimizer evolution

The static-analysis boundary is above execution lowering. Replacing AST execution with
Bytecode DSL or changing optimization strategy does not change the editor protocol or
source-analysis authority.

### Future non-Truffle runtime

Baseline static analysis has no Polyglot Context dependency. The service may remain on
the JVM while runtime backends diversify, or its implementation may later move without
changing LSP clients.

### Future self-hosting

If parser/resolver/static-analysis authorities become ordinary reusable Protos
libraries/capabilities, a self-hosted server can replace the JVM analysis implementation
behind the same process/LSP boundary.

## Regret trigger and escape path

The main plausible regret trigger is a mature fully self-hosted compiler/static-analysis
stack where the current JVM parser/resolver APIs are no longer the canonical authorities.
At that point keeping a JVM language server solely for static tooling would become
redundant.

**Escape path:** preserve the client-owned independent process and standard LSP boundary,
but replace the JVM-hosted analysis implementation with self-hosted/native Protos.
Because LSP serialization is outside the analysis core and JVM objects are not semantic,
editor clients need not change.

A secondary regret trigger would be measured aggregate memory pressure from one JVM server
per editor session. The escape path is to optimize packaging/runtime footprint, share
strictly immutable caches, or separately approve a shared-service topology if evidence
justifies its additional coordination and version/isolation complexity.

## Strongest argument against A′

A dedicated JVM process duplicates class metadata, parser code and analysis/index state
per active editor session. A shared daemon can reduce aggregate memory when many clients
or workspaces are active.

That cost is accepted for the baseline because A′ provides substantially better failure
isolation, toolchain-version matching, editor neutrality, local lifecycle, zero
ordinary-runtime overhead and no daemon discovery/authentication/stale-state institution.
The service boundary keeps later memory optimization reversible.

## Deliberately deferred choices

PLAT024 does **not** select:

- the Java LSP implementation library (for example LSP4J);
- the exact public launcher/CLI spelling for the language server;
- final executable/JAR/Native Image packaging;
- exact document snapshot representation or indexing data structures;
- index persistence, shared immutable caches or eviction policy;
- per-root process splitting;
- socket/TCP transport as a normal baseline;
- global/shared server deployment;
- Graal dynamic-LSP merge/precedence policy;
- definition/reference/completion/hover/signature semantics;
- formatter/refactoring ownership;
- self-hosting schedule; or
- Marketplace/extension release policy.

Any of these that becomes a substantive durable platform or semantic choice must cross
its own approval gate.

## LM009-F release boundary

Ratification of PLAT024 releases **LM009-F** to implement only the static-service
foundation consistent with Candidate A′:

- editor-neutral analysis core;
- document/workspace snapshot lifecycle;
- direct parser/source/resolver authority reuse;
- dedicated client-owned service host;
- standard LSP-over-stdio protocol edge; and
- tests for lifecycle, isolation and unexecuted-source operation.

PLAT024 does not pre-approve LM009-G/H language-intelligence semantics. Any substantive
definition/reference/completion/etc. decision discovered later remains subject to the
appropriate Dxxx/specification gate.

## Approval record

The project owner explicitly approved **Candidate A′** on 2026-09-11 after the extended
exhaustive review of all 16 principal and 21 experimental/historical Truffle catalogue
entries, with Apple Pkl receiving the strongest direct precedent weight, plus focused
future-endurance, scalability and Protos-philosophy scoring.

This approval selects the architecture above and preserves the deliberately deferred
choices. It does not authorize implementation before this ratification reaches `main`.
