# PLAT022 — Context source-readability authority for physical debugger paths

Status: **RATIFIED — Candidate D′ selected**

Nature: durable non-normative JVM / Truffle Context filesystem and tooling-authority architecture decision

Approved by project owner: **2026-09-11**

GitHub Issue: **#332**

Primary consumer: `LM009-E` / GitHub #318 consuming D065 and PLAT020.

Normative effect: **none**. PLAT022 does not add Protos filesystem semantics,
change module/package identity, change source selection, change DAP topology,
change editor path mapping, or grant a public guest file-I/O capability.

## Selected architecture — Candidate D′

Each Protos Polyglot/Process Context owns a **Context-local, deny-by-default
readability authority** for physical Sources that Protos has already selected.
The Context exposes read-only filesystem visibility only for the exact physical
source paths admitted by the existing launch/module-resolution path.

Conceptually:

```text
Context filesystem authority
    |
    +-- exact admitted physical Protos source path -> read-only host delegate
    |
    +-- every other path -> deny I/O

socket authority: none
write authority:  none
shared/global path registry: none
```

The durable rule is not tied to a particular Java collection or Polyglot helper:

> Tooling may read an already-admitted physical Source location inside the
> owning execution Context, without that readability granting authority over
> unrelated locations or redefining source/module identity.

The initial Truffle realization may use the public Polyglot custom/read-only/
composite filesystem facilities and an exact-path selector. The exact wrapper,
selector, set, cache, class decomposition and registration API remain private
implementation choices so long as the durable constraints below hold.

## Admission boundary

A physical path becomes readable only when all of the following are already true:

1. existing Protos launch/module resolution has selected that physical Source;
2. D065 has produced its exact absolute lexically-normalized tooling path;
3. PLAT020 has the exact already-read characters ready for owning-Context Source
   materialization; and
4. the path is admitted to that same owning Context **before** Truffle publishes
   or parses the physical Source, so instrumentation/debugging sees truthful
   readability from the beginning.

Admission is therefore not a resolver. It cannot search, discover, redirect,
canonicalize or substitute a different Source. It only grants read-only
visibility to the exact already-selected path.

The selector compares the D065-selected absolute lexically-normalized path. It
must not call realpath/canonical-path resolution merely to establish tooling
identity. A host filesystem may naturally follow a symbolic link when opening
that exact admitted path; that does not admit the link target spelling, parent,
siblings or any other path.

## Content authority

PLAT020 remains authoritative for execution content. The already-read characters
are supplied through Source materialization and remain the content executed by
Protos. Filesystem readability exists so Truffle tooling can truthfully classify
the physical Source as client-readable; it must not introduce a second semantic
source-selection read.

A file changing after Protos already captured its characters therefore does not
change what that execution evaluates merely because the tooling filesystem can
read the physical location.

## Run/debug transparency

The admitted-source readability mechanism belongs to normal Context hosting, not
to a debug-only mode. Ordinary run and debug Contexts use the same authority
rule. Attaching F5 must not widen the guest Context to whole-host files, writes,
sockets, or a second resolution mechanism.

This decision specifically rejects solving the debugger presentation problem by
making debugging more privileged than ordinary execution.

## Existing authority retained

PLAT022 composes with and does not reopen:

- **D065 Candidate B** — tooling-visible ordinary filesystem path is the exact
  absolute lexically-normalized selected path; no realpath rewrite solely for
  presentation.
- **PLAT020 Candidate A′** — source path/content/module facts remain
  backend-neutral outside Context ownership; physical Truffle Source
  materialization occurs only inside the owning entered Context through that
  Context's `Env`; already-read characters remain authoritative.
- **PLAT018 / D060** — VS Code connects directly to the real RuntimeHost-owned
  GraalVM DAP endpoint; there is no editor DAP proxy, source-rewriting relay,
  readiness file or fixed port.
- `ProtosModuleKey` remains semantic module/cache identity and does not become a
  filesystem key.
- virtual, generated, REPL and `-e` Sources remain virtual and do not receive a
  fabricated physical admission merely to satisfy tooling.
- independent Process Contexts do not share Context-owned `TruffleFile`, Source,
  admission-set, selector, cache or lock state.

## Why PLAT022 is needed

The first PLAT020-consuming LM009-E candidate successfully built genuine
Context-owned file-backed Truffle Sources and preserved D065 path spelling, but
the real DAP regression still reported:

```text
readable physical source must not require a virtual DAP sourceReference
expected: sourceReference absent
actual:   sourceReference = 1
```

Graal DAP does not classify a Source as an ordinary path-only source merely
because `Source.getPath()` is non-null. It asks the target Truffle Context for the
corresponding file and uses a positive `sourceReference` when that location is not
readable through the Context filesystem.

Protos Contexts intentionally had no host I/O authority, so the real physical
path was still unreadable from that Context. VS Code gives a positive
`sourceReference` precedence over the absolute path and opens a debugger-owned
`debug:` document, which is the observed duplicate Plain Text editor symptom.

PLAT022 resolves that host-platform/tooling mismatch without converting it into
Protos language semantics or broad host access.

## Exhaustive Truffle and platform survey

The approval packet re-screened the complete public Truffle implementation
catalogue used by the project at this checkpoint: **16 principal + 21
experimental/historical implementations**, and deep-dived the systems that
materially expose Context filesystem or embedding-security policy.

### Principal implementation coverage

Enso; Espresso; FastR; GraalJS; GraalPy; GraalWasm; grCUDA; **Apple Pkl**;
SimpleLanguage; SOMns; Sulong; TRegex; TruffleRuby; TruffleSOM;
TruffleSqueak; Yona.

The historical/experimental catalogue was also screened to falsify the candidate
set. Older implementations largely use earlier File/URL/literal Source APIs or
broad launcher assumptions and did not reveal a stronger modern architecture
family for this boundary.

### Strongest transferable evidence

- **Truffle / Polyglot** provides custom `FileSystem`, deny-I/O, read-only and
  composite/selective filesystem mechanisms. This is direct evidence that Context
  filesystem authority is expected to be explicit and composable.
- **Graal LSP** is the closest mechanical tooling precedent: physical Sources are
  built from `TruffleFile`, existing editor text can be supplied as Source
  content, and tooling can run against a custom read-only filesystem instead of
  unrestricted host I/O.
- **GraalJS** demonstrates granting file access independently of socket/all-host
  access where a host tool actually needs files.
- **GraalPy** treats host I/O as an explicit embedding policy and documents
  restricted/custom filesystems as the production-safe direction.
- **GraalWasm / WASI** demonstrates explicit pre-opened resource authority rather
  than ambient whole-host filesystem access.
- **Apple Pkl** strongly separates admitted module/resource authority from
  language identity and evaluation policy. Pkl does not use the same DAP path
  mechanism, but its least-authority separation is highly transferable.
- **TruffleRuby** validates modern Context-owned physical file loading while
  making file-I/O availability explicit; Ruby's legitimate broad filesystem
  semantics are intentionally not imported into Protos.
- **Espresso** demonstrates that a Context can own a virtualized filesystem view
  rather than equating Context visibility with the entire host filesystem.
- **Sulong/LLVM**, **FastR** and **TruffleSqueak** validate Context-owned file
  machinery but solve broader loader/runtime filesystem requirements than
  LM009-E, so their broad reach is not a Protos default precedent.
- **TRegex**, **grCUDA**, Pkl synthetic paths and other virtual-source designs
  reinforce that virtual Sources should remain virtual rather than receiving fake
  file identity.

No maintained implementation supplied evidence that `IOAccess.ALL` is the right
answer to a narrowly scoped debugger-readability requirement.

## Non-Truffle evidence

DAP distinguishes sources whose content/path the client can access from sources
whose content must be requested from the adapter. VS Code reflects this
separation directly: a positive `sourceReference` becomes a debugger-owned
resource even when an absolute path is also present.

Mature native/JVM debugger architectures similarly keep source presentation from
being equivalent to arbitrary debuggee file authority. Truffle DAP happens to
use target-Context readability as its discriminator, so Protos needs a narrow
hosting bridge at that boundary rather than a new editor protocol institution.

## Candidate set and disposition

### A — `IOAccess.ALL`

Rejected. It fixes readability by granting host files, sockets and writes. This
is a gross authority expansion for a source-presentation need.

### B — unrestricted host filesystem access, sockets denied

Rejected. Better than A, but still grants the complete host read/write namespace
instead of the already-selected Sources.

### C — whole-host read-only filesystem

Credible simplicity alternative, but not selected. It prevents writes/sockets
and uses mature Truffle machinery, yet every readable host file becomes visible
to the Context although LM009-E needs only admitted physical Sources.

### D′ — Context-local admitted-source read-only filesystem overlay

**Selected.** Exact already-selected physical Source paths become read-only in
the owning Context; every unrelated path remains denied. The same rule applies in
run/debug, no global registry exists, and source/module identity remains separate.

### E — Context-local content-backed source mirror

Rejected. It would provide strong snapshot isolation but create a mini virtual
filesystem/content-mirroring institution and duplicate filesystem behavior that
the observed tooling problem does not justify.

### F — debug-only filesystem elevation

Rejected. It makes program authority depend on whether the debugger is attached
and violates debugger transparency.

### G — keep positive `sourceReference` and relax the regression

Rejected by observed VS Code behavior. It preserves the duplicate debugger-owned
Plain Text document rather than solving it.

### H — editor DAP proxy, source-rewriting relay or patched/forked Graal DAP

Rejected by PLAT018/D065. It introduces a permanent protocol/mapping layer solely
to bypass the Context readability contract.

### I — Graal DAP `SourcePath`

Eliminated by implementation evidence. PLAT020 Sources already contain
characters; the Graal debug source resolver retains them, so `SourcePath` does not
change the later Context-readability/sourceReference branch.

## GITHUB010 comparative matrix

Scores use the required 1–5 scale. `H` = high confidence; `M` = medium
confidence. Arithmetic is supporting evidence, not selection authority.

| Criterion | A ALL | B host files RW | C host RO | **D′ admitted-source RO** | E source mirror | F debug-only RO |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | 5/H | 5/H | 5/H | **5/H** | 5/M | 5/H |
| Protos alignment | 1/H | 2/H | 3/H | **5/H** | 4/M | 2/H |
| Future-option resilience | 2/H | 3/H | 4/H | **5/H** | 4/M | 3/H |
| Scalability | 5/H | 5/H | 5/H | **5/H** | 5/M | 5/H |
| Conceptual simplicity | 5/H | 5/H | 4/H | 4/H | 2/H | 4/H |
| Portability / implementation freedom | 4/H | 4/H | 5/H | **5/H** | 4/M | 5/H |
| Runtime / resource cost | 5/H | 5/H | 5/H | 4/H | 3/M | 5/H |
| Failure / operability | 2/H | 2/H | 4/H | **5/H** | 4/M | 3/H |
| Reversibility / migration | 4/H | 4/H | 4/H | **5/H** | 4/M | 4/H |
| Evidence maturity / implementation risk | 5/H | 5/H | 5/H | **5/H** | 3/M | 4/H |
| **Total** | **38** | **40** | **44** | **48** | **38** | **40** |

A, B and F are additionally disqualified by authority/transparency concerns. G,
H and I fail the observed debugger/editor requirement and are not viable
architecture candidates.

## Future and scalability stress test

### Many Process Contexts

For a Context with `S` admitted physical Sources, admission memory is `O(S)`.
No host-wide registry or cross-Context coordination is introduced. Independent
Contexts can load the same path independently without sharing VM-owned objects.

### Many Tasks / Actors

Tasks and Actors create no filesystem-authority state merely because a Source is
physical. The authority remains owned by the Process/Polyglot Context.

### High-concurrency module loading

Admission must be thread-safe and idempotent. Registration happens before
physical Source publication. Lookup is expected to be read-mostly and bounded by
Context-local state; no global lock is permitted.

### Symbolic-link workspaces

The admission key is the exact D065 lexical path. No parent directory or
canonical target spelling becomes admitted by implication. This preserves editor
namespace identity while allowing the host filesystem to resolve the admitted
path normally when actually opened.

### Remote / Dev Container

The selector and host delegate live in the execution/workspace host where the
D065 path was selected. No client/remote path map, network filesystem protocol or
second namespace is introduced.

### Bytecode DSL / optimizer evolution

The rule is a Context/source-hosting boundary before guest lowering. It neither
requires AST node state nor constrains a future Bytecode DSL representation,
partial-evaluation strategy, compiled-code cache or deoptimization policy.

### Native Image / AOT

The architecture uses supported Polyglot/Truffle filesystem concepts and forbids
reflection/internal-module shortcuts. Exact helper classes remain replaceable if
GraalVM APIs evolve.

### Distributed / alternative backends

The decision is intentionally local to one execution Context. A future remote or
non-Truffle backend may implement the same durable rule differently or not need
this bridge at all. No network/distributed source registry is authorized here.

### Future public Protos filesystem capability

A later language/runtime design may make a tooling-only admitted-source overlay
redundant. It must not silently reinterpret this allow-set as guest semantic
filesystem authority. Any public guest filesystem/capability remains a separate
design decision.

## What plausible future requirement could make us regret D′?

If Protos later adopts a first-class capability-oriented filesystem in which every
physical module Source is already readable under an explicit guest capability,
a second tooling-only admission view may become redundant plumbing.

**Escape path:** retire or absorb the Truffle-specific admitted-source overlay in
the new hosting implementation. D065 path identity, PLAT020 source facts,
`ProtosModuleKey`, the editor protocol and Protos language semantics do not need
to change because D′ never made its current Java/Truffle mechanism semantic.

## Strongest argument against D′

Candidate C is substantially simpler to implement: a whole-host read-only view
requires less admission plumbing and already prevents writes/sockets. If Protos
were expected to expose ordinary host-file reads broadly anyway, C could be the
better engineering trade-off.

The project does not currently have that semantic contract. Selecting C now
would grant ambient readability over unrelated host files merely to avoid a small
Context-local selector. D′ therefore better preserves least authority, locality,
future optionality and Protos's preference for mechanisms over broad ambient
institutions.

## Durable constraints

1. Physical tooling readability is owned by one Process/Polyglot Context.
2. Default authority for unrelated paths remains deny-I/O.
3. Only exact physical source paths already selected by existing Protos
   launch/module resolution may be admitted.
4. The admission key is D065's exact absolute lexically-normalized selected path;
   no realpath/canonical rewrite is performed solely for presentation/admission.
5. Admission occurs before physical Source parse/publication so tooling observes
   truthful readability from first publication.
6. Admitted paths are read-only; writes are not authorized.
7. Socket/network authority is not authorized by this mechanism.
8. Run and debug use the same admission rule; F5 does not elevate authority.
9. Admission is thread-safe and idempotent inside one Context.
10. No global mutable path registry, global cache or cross-Context lock is
    introduced.
11. `ProtosModuleKey` and module/source resolution remain authoritative and
    separate from filesystem admission.
12. PLAT020's already-read characters remain execution content; the tooling
    filesystem must not become a second semantic source selection/read.
13. Virtual/generated/REPL/`-e` Sources remain virtual and receive no fake
    physical admission.
14. No parent directory, sibling or canonical target is admitted by implication.
15. Context-owned `TruffleFile`, Source and admission state are not shared across
    independent Process Contexts.
16. The architecture remains backend-neutral at the durable level; a non-Truffle
    backend may realize or eliminate the mechanism without changing Protos
    semantics.
17. A future public guest filesystem capability requires its own design gate and
    must not inherit this tooling allow-set silently.

## Implementation release boundary

Ratification releases a bounded LM009-E implementation slice to realize D′ in the
current Truffle host. That slice must prove at least:

- exact admitted source path is readable through the owning Context;
- unrelated physical paths remain denied;
- writes remain denied;
- sockets remain denied;
- independent Context admission sets are isolated;
- D065 symlink/path spelling is preserved;
- admission precedes PLAT020 physical Source publication;
- real Graal DAP presents the physical Source without a positive virtual
  `sourceReference`; and
- normal run/debug retain the same authority rule.

These tests and source changes belong to LM009-E; they are **not** included in
this ratification publication.

## Intentionally deferred

PLAT022 does not decide:

- a public Protos filesystem or path capability;
- directory-wide admissions or wildcard source authority;
- client/target source mapping for genuinely distinct machine namespaces;
- network/distributed source retrieval;
- persistence/serialization of filesystem authority;
- a global source registry;
- exact Java collection/selector/wrapper class names;
- per-Context caching or eviction beyond the requirement that authority remain
  Context-local and proportional to actually admitted physical Sources; or
- Marketplace/editor behavior beyond consuming the corrected real DAP Source.
