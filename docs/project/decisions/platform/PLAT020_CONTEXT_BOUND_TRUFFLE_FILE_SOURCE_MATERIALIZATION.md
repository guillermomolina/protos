# PLAT020 — Context-bound Truffle file Source materialization

Status: **RATIFIED — Candidate A′ selected**

Nature: durable non-normative JVM / Truffle source-materialization architecture decision

Approved by project owner: **2026-09-11**

GitHub Issue: **#327**

Primary consumer: `LM009-E` / GitHub #318 consuming D065 / #323.

Normative effect: **none**. PLAT020 does not change Protos syntax, module/package
identity, execution semantics, debugger protocol, DAP architecture, or D065's
tooling-visible path rule.

## Selected architecture — A′

A path-backed Protos source remains outside Truffle Context ownership as immutable,
backend-neutral source facts:

- the D065-selected absolute lexically-normalized path;
- the exact already-read characters; and
- the semantic `ProtosModuleKey` when one applies.

A hosted execution materializes the physical Truffle `Source` only inside the
owning entered Protos language Context, using that Context's `Env`. The
Context-owned `TruffleFile` and the resulting Truffle `Source` do not become part
of the backend-neutral resolver contract and must not be shared across independent
Process Contexts.

The current Truffle realization is expected to follow the supported public shape:

```java
TruffleFile file = env.getPublicTruffleFile(exact.toString());
Source source = Source.newBuilder(ProtosLanguage.ID, file)
    .canonicalizePath(false)
    .content(characters)
    .mimeType(ProtosLanguage.MIME_TYPE)
    .build();
```

The exact Java record/class decomposition and any per-Context Source cache remain
implementation details and are deliberately not fixed by PLAT020.

Virtual sources remain virtual. REPL, `-e`, generated, synthetic and memory-only
sources must not pretend to be ordinary physical files merely to reuse this path.

## Why this decision is needed

D065 selected exact workspace/execution-host path presentation and requires
ordinary filesystem-backed sources to reach tooling as genuine physical
file-backed Sources. The first LM009-E implementation candidate attempted to use
an internal `Source.newBuilder(String, File)` overload. Real compilation against
the repository's Truffle dependency proved that overload is not public.

The supported internal Truffle file builder takes a `TruffleFile`, and public
user-file `TruffleFile` values are obtained through a concrete language `Env`.
That exposed a real ownership boundary: current Protos source/module resolution
can happen before a Process Context is entered, while genuine Truffle file Source
materialization is naturally Context-relative.

PLAT020 decides where that VM-specific materialization belongs without reopening
D065 or moving semantic module identity into Truffle.

## Existing authority retained

PLAT020 composes with and does not reopen:

- **D065 / #323**: tooling-visible ordinary filesystem source path is the exact
  absolute lexically-normalized path selected by the current workspace/execution
  host, with no symlink realpath rewrite solely for presentation.
- **PLAT018 / D060**: debugger launch remains one RuntimeHost/Engine with a real
  GraalVM DAP endpoint, OS-ephemeral loopback port and one Protos readiness
  record. No editor DAP proxy/path-repair layer is introduced.
- `ProtosModuleKey` remains semantic module/cache identity.
- generated/in-memory sources remain logically distinct from physical files.
- Process Context isolation remains authoritative; Context-owned VM objects are
  not hidden shared state across independent Process Contexts.
- already-selected source characters are the execution content; physical Source
  construction must not silently introduce a second read.

## Exhaustive Truffle catalogue audit

The project-owner approval followed screening of the complete public Truffle
implementation catalogue used at this checkpoint: **16 principal + 21
experimental/historical implementations**.

### Principal — 16

Enso; Espresso; FastR; GraalJS; GraalPy; GraalWasm; grCUDA; Apple Pkl;
SimpleLanguage; SOMns; Sulong; TRegex; TruffleRuby; TruffleSOM;
TruffleSqueak; Yona.

### Experimental / historical — 21

BACIL; bf; brainfuck-jvm; Cover; DynSem; Heap Language; hextruffe;
islisp-truffle; LuaTruffle; Mozart-Graal; Mumbler; PorcE; ProloGraal;
PureScript; Reactive Ruby; shen-truffle; TruffleBF; streamblocks-graalvm;
TruffleMATE; TrufflePascal; ZipPy.

No additional durable architecture family stronger than the candidates below
emerged.

`hextruffe` (Bitbucket) and `ProloGraal` (GitLab) were catalogue-screened but
their exact current source was not available through the connected GitHub source
index. Both are historical/experimental; the remaining implementation space did
not reveal a missing architecture family.

## Strongest precedents

### TruffleRuby

Current `FileLoader` is the closest direct implementation precedent. It obtains
the `TruffleFile` from the current Ruby Context, reads source bytes once for the
lexer, and constructs:

```java
Source.newBuilder(TruffleRuby.LANGUAGE_ID, file)
    .canonicalizePath(false)
    .content(alreadyReadContent)
    ...
    .build();
```

It additionally asserts that `Source#getPath()` remains the supplied path. This
closely matches D065 plus PLAT020's final materialization half.

### GraalJS / Node

Node's GraalJS compatibility layer has the motivating debugger case explicitly:
the source text is already known, but the Source is associated with the
corresponding physical file so the debugger knows where to place breakpoints.
It obtains the `TruffleFile` from the current language `Env` and supplies the
already-known code through `.content(...)`.

GraalJS also canonicalizes paths for ECMAScript module cache identity in other
code. Protos deliberately does not inherit that semantic identity rule: D065
keeps tooling presentation separate from `ProtosModuleKey`.

### GraalPy

Current GraalPy obtains `TruffleFile` values from `PythonContext`, builds physical
Sources from them and has code paths that reattach already-known source text with
`.content(src)`. It also disables Source path canonicalization in a Windows
preinitialization case, reinforcing that canonicalization is policy rather than
an unavoidable identity truth.

Its ongoing Bytecode DSL use is useful evidence that Context-owned physical
Source construction is independent of the interpreter lowering strategy.

### Sulong / LLVM

Sulong's file-eval path obtains the current LLVM Context `Env`, gets the
`TruffleFile`, constructs the Source and parses it there. Literal eval is a
separate path. This validates locating physical Source materialization adjacent
to the entered runtime boundary.

Sulong host tests also validate Candidate C's alternative outer-Polyglot model
when the host owns the entire `Context.eval/parse` ABI.

### GraalWasm

Wasm debugging distinguishes a pseudo/logical Source from a real physical Source.
The physical form receives an `Env`, obtains `getPublicTruffleFile(path)` and
constructs the Source from that file.

### TruffleSqueak

The file/polyglot path resolves a public `TruffleFile` from the active `Env` and
constructs a Source from it, adding independent Smalltalk-family evidence.

### Enso

Enso separates file, text and cached `Source` state and materializes the final
Source only when required. Its exact ownership differs from A′ because a module
source record may retain a Context-derived `TruffleFile`, but it demonstrates
that source origin/content and final VM Source need not be one eagerly-created
object.

### Apple Pkl

Pkl is the strongest evidence for A′'s **neutral source-facts half**. Its
`ResolvedModuleKey` family keeps original module key, URI and concrete origin
(`Path`, URL or source text) independently of final Truffle `Source`
construction, and `loadSource()` yields exact text.

Pkl's final module Source is intentionally literal/URI-centric. That is valid for
Pkl's module identity model but is not selected for Protos: LM009-E S3 showed that
literal + URI does not satisfy D065's ordinary physical-file debugger
presentation.

The transferable lesson is the separation:

```text
resolved semantic/origin descriptor + exact text
    !=
final VM Source
```

A′ combines that separation with the physical Context-owned materialization used
by TruffleRuby, GraalJS, GraalPy, Sulong and GraalWasm.

### FastR

FastR exposes both physical `TruffleFile` Sources and literal text associated with
file URIs because R has several source-origin modes. It also has a synchronized
weak global origin-to-Source cache. The physical path is useful precedent; the
global cache is deliberately not imported into Protos because PLAT020 requires
no global source registry and Protos prefers Context-local ownership.

### SimpleLanguage

SimpleLanguage's launcher uses public `org.graalvm.polyglot.Source(File)` and
`Context.eval`. This is clean evidence for Candidate C when the outer host owns
the complete guest-entry ABI. Protos currently executes with explicit
`ProtosActivation`/RootTask machinery, so adopting that model solely for LM009-E
would be a much broader hosting refactor.

### Historical / experimental implementations

BACIL shows both outer `Polyglot Source(File)` and runtime Context-owned
`TruffleFile` patterns. `islisp-truffle` uses active Context filesystem objects.
DynSem and several older implementations use previous-generation file Source
APIs. The remaining experimental languages mostly expose synthetic/literal
source construction and do not produce a stronger modern physical-file
ownership architecture.

## Architecture families found

The survey converged on four meaningful families:

1. **Context/Env-owned physical Source** — GraalJS, GraalPy, TruffleRuby, Sulong,
   GraalWasm, TruffleSqueak, FastR physical-file paths, BACIL runtime.
2. **Outer Polyglot Source + Context eval/parse** — SimpleLanguage and several
   host launchers/tests.
3. **Neutral origin/content descriptor + delayed VM representation** — strongest
   in Apple Pkl, structurally also visible in Enso.
4. **Literal/URI or URL as final source presentation** — valid for some runtimes'
   identity models but insufficient for D065's ordinary Protos file contract.

A′ is the Protos-specific synthesis of families 3 and 1.

## Candidate set

### A′ — context-neutral source facts + owning-Context physical materialization

**Selected.**

The resolver/source boundary carries immutable backend-neutral facts. The entered
owning Process Context materializes any required physical Truffle Source through
its own `Env`. No VM-owned file/Source leaks into the resolver contract.

### B — Context-aware resolver

Pass `Env`, `TruffleFile` or a Context-specific materializer into general
`ProtosModuleResolver.loadSource(...)`, allowing the resolver to return a final
physical Source.

Rejected because it mixes VM Context lifetime/filesystem policy into a boundary
whose durable job is module/source resolution and makes reuse across independent
Contexts and future backends harder.

### C — wholesale outer Polyglot Source/eval boundary

Construct public Polyglot Sources from host files and route execution through
ordinary `Context.eval/parse`.

Retained as a credible future hosting architecture. Rejected here because Protos
currently requires an internal CallTarget executed with explicit
`ProtosActivation`, Process/RootTask ownership and module state; changing that
boundary only to solve Source construction is disproportionate.

### D — URL-backed internal Source shortcut

Rejected. URL path semantics are not D065's host-native physical path contract,
especially across Windows/UNC/custom filesystems, and this would bypass the
Context filesystem abstraction.

### E — retain literal Source + editor/DAP path repair

Rejected by D065/PLAT018. It would preserve the known S3 defect and add a mapping
or proxy institution to repair a source that already lives in the same namespace.

### F — access internal `Source.newBuilder(String, File)`

Rejected. Real compilation proved this is not a supported public language API.
Reflection/module-export workarounds would create VM-version and Native Image
fragility.

## GITHUB010 comparative matrix

Scores use the required 1–5 scale. `H` = high confidence; `M` = medium
confidence. Totals are comparison aids only.

| Candidate | Correct. | Protos | Future | Scale | Simple | Portable | Cost | Operable | Reversible | Evidence | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **A′** | **5/H** | **5/H** | **5/H** | **5/H** | 4/H | **5/H** | **5/H** | **5/H** | **5/H** | **5/H** | **49** |
| B | 5/H | 3/H | 3/H | 4/H | 3/H | 2/H | 4/H | 4/H | 3/H | 5/H | 36 |
| C | 4/M | 3/M | 4/M | 5/H | 2/H | 3/M | 4/M | 3/M | 2/M | 5/H | 35 |
| D | 2/H | 2/H | 2/H | 5/H | 5/H | 2/H | 5/H | 3/H | 3/H | 3/H | 32 |
| E | 1/H | 1/H | 2/H | 3/H | 2/H | 2/H | 3/H | 2/H | 2/H | 4/H | 22 |
| F | 2/H | 1/H | 1/H | 4/H | 4/H | 1/H | 5/H | 1/H | 1/H | 1/H | 21 |

D, E and F additionally fail hard constraints and are not recoverable merely by
their arithmetic totals.

## Future and scalability stress test

### Many Process Contexts

Each Context materializes only the physical Sources it actually parses. No
global path registry, alias map, Source cache or editor coordination is required.
No Context-owned `TruffleFile` is shared across independent Contexts.

### Many Actors / Tasks

Actors and Tasks do not gain per-instance file handles or source registries merely
because a module/source has a physical origin. The source boundary remains at
Process/Context execution.

### Multicore / high concurrency

No new cross-Context lock or global mutable source authority is introduced.
Independent Contexts can materialize Sources independently.

### Bytecode DSL migration

The selected boundary survives AST-to-Bytecode-DSL evolution because it concerns
source provenance/materialization before lowering and does not depend on an AST
node representation.

### Native Image / AOT

Only supported public Truffle APIs are required. No reflection or internal module
exports are introduced.

### Windows / macOS / Linux / custom filesystems

The owning `Env` remains the authority for Truffle filesystem objects.
`canonicalizePath(false)` preserves D065's selected `TruffleFile.getPath()`
presentation rather than forcing a platform-specific realpath solely for
tooling. URL-path semantics are not substituted for host path semantics.

### Future non-Truffle backend

The neutral descriptor remains usable; only the backend materializer changes.

### Future outer-Polyglot hosting

If Protos later independently replaces the explicit internal CallTarget +
`ProtosActivation` entry ABI with a wholesale `Context.eval/parse` boundary, the
neutral source facts survive and the Truffle-specific materializer can be
replaced locally.

## What could make us regret A′?

A future Protos runtime that standardizes entirely on public outer Polyglot
`Source` + `Context.eval/parse` could make the internal materializer layer
redundant.

**Escape path:** retain the neutral source facts and replace only the final
materializer. Resolver, module identity, package identity and D065 tooling-path
rules do not need to change.

## Strongest argument against A′

A′ creates an explicit two-stage representation instead of storing one final
`Source` everywhere. That adds plumbing and requires tests proving hosted and
unhosted paths preserve the intended characters/location semantics.

The survey indicates this separation reflects a real ownership distinction rather
than speculative abstraction: Pkl and Enso separate source facts from final VM
representation, while maintained file-oriented Truffle runtimes materialize
physical Sources through the active Context.

## Durable constraints

1. Path-backed source facts outside an entered Truffle Context remain immutable
   and backend-neutral.
2. D065 remains authoritative for the selected physical path.
3. Exact already-read characters remain authoritative for execution content.
4. `ProtosModuleKey` remains semantic module/cache identity.
5. Physical hosted Source materialization occurs only inside the owning entered
   Protos Context.
6. Physical Source construction uses the owning Context's `Env`/`TruffleFile`.
7. `canonicalizePath(false)` preserves D065 path presentation for ordinary
   filesystem-backed sources.
8. Already-read characters are supplied to Source materialization; no silent
   second read is introduced.
9. `Env`, `TruffleFile` and final Truffle `Source` do not become durable members
   of the backend-neutral resolver contract.
10. No `TruffleFile` or Context-owned Source is shared across independent Process
    Contexts.
11. Virtual/generated/REPL/`-e`/memory sources remain virtual.
12. No global Source/path/alias registry or global lock is introduced.
13. No editor-side path map, DAP proxy or VS Code-specific source workaround is
    introduced.
14. No change is made to D060/PLAT018 debugger-session topology.
15. No change is made to Protos-visible semantics or specification authority.
16. No cache policy is mandated by PLAT020. A future measured Context-local cache
    may be introduced if it preserves these invariants.
17. Exact Java payload/materializer class names are implementation details.
18. Deliberately unhosted compiler/semantic harnesses may use a non-tooling
    literal representation of the same neutral facts; they must not claim that
    representation is the hosted physical debugger Source.
19. A future non-Truffle or outer-Polyglot backend may replace the final
    materializer without changing the neutral source facts.
20. Any newly exposed durable architecture or observable semantic choice stops
    the consuming implementation slice and crosses the normal Dxxx/PLATxxx
    approval gate.

## LM009-E release boundary

Publication of this ratification releases only the bounded LM009-E D065
source-presentation correction.

The consuming implementation should:

- replace the current eager path-backed literal+URI Source creation with neutral
  immutable path/content facts where Context ownership does not yet exist;
- materialize the genuine physical Truffle Source inside the owning entered
  Process Context;
- preserve D065 path spelling with `canonicalizePath(false)`;
- preserve already-read characters via `.content(...)`;
- cover direct CLI/debug file execution and path-backed module execution;
- keep virtual source paths unchanged;
- add regression evidence that the hosted physical Source exposes the intended
  path and that debugger presentation no longer reopens the same source as a
  virtual/plain-text document; and
- keep LM009-E open until the real VS Code S3 source-presentation retest passes.

PLAT020 does **not** itself implement those changes or close LM009-E.

## Deliberately deferred

PLAT020 does not decide:

- the concrete Java record/class names for the neutral source payload;
- whether one payload type or separate CLI/module payload types are preferable;
- a Source cache or cache key;
- outer-Polyglot guest-entry migration;
- remote client/target path mapping for genuinely distinct namespaces;
- package-content/CAS/distributed source addressing;
- source-map/generated-source protocols;
- non-Truffle backend materializer APIs; or
- any new debugger protocol or editor feature.

Those remain implementation details or future decisions unless/until they become
durable architectural or observable semantic choices.

## Approval record

The project owner explicitly approved **Candidate A′** on 2026-09-11 after
requesting an exhaustive review of the complete Truffle implementation catalogue,
including Apple Pkl, and explicit scoring for future resilience, scalability and
Protos philosophy. GitHub #327 records the research packet and approval.
