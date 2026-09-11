# PLAT025 — Bytecode module-initialization composition boundary

Status: **RATIFIED — Candidate A′ selected**

Nature: durable non-normative JVM/Truffle/Bytecode-DSL module-initialization architecture decision

Approved by project owner: **2026-09-11**

GitHub Issue: **#368**

Primary consumer: `PERF006-B6A5` / GitHub #276 and the later PERF006-B6 production cutover.

Normative effect: **none**. PLAT025 does not change Protos module identity, import
resolution, cache-before-execute, recursive-import, failure, cancellation, ordering, or
suspension semantics. It selects only how the JVM/Truffle C-prime backend composes the
already-defined standard `import` lifecycle with a module body that may suspend.

## Decision

Select **Candidate A′ — exact-standard post-lookup `import` intrinsic plus a
backend-private module-lifecycle carrier and nested Bytecode C-prime child root**.

The essential boundary is:

```text
ordinary call lookup
        |
        v
exact canonical standard import implementation selected
        |
        v
validate specifier + resolve ModuleKey
        |
        +-- cached READY ---------> exact module instance
        |
        +-- cached INITIALIZING --> exact same module instance
        |
        v
cache miss
        |
        v
create module instance + exact ModuleRecord
cache INITIALIZING before body execution
        |
        v
nested Bytecode module child root
        |
        +-- yield/suspend --> ordinary C-prime ContinuationResult
        |                    lifecycle remains INITIALIZING
        |
        +-- normal completion --> mark READY exactly once
        |                        return module instance
        |
        +-- escaping failure/cancellation --> removeIfSame exact record
                                             propagate transfer
```

The module-lifecycle carrier is **not a continuation representation**. It may retain only
the implementation state required to finish one exact initialization, such as the
`ModuleKey`, exact active `ModuleRecord`, module instance, child target/activation and
one-shot lifecycle-finalization state. `ContinuationResult` / the already-ratified
PLAT014 C-prime chain remains the sole interpreter continuation authority.

## Existing semantic and platform authority

The following remain fixed and are not amended by PLAT025:

- current module semantics: Actor-local identity/cache, cache-before-execute, exact
  `INITIALIZING` cycle visibility, successful transition to `READY`, exact-record removal
  on failed initialization, and no rollback of already-observed effects/references;
- module-specifier resolution introduces no implicit suspension under the current
  language contract;
- PLAT014: Bytecode DSL C-prime is the sole cooperative continuation architecture;
- PLAT017: exact-standard implementation specialization occurs only after ordinary
  semantic lookup/selection, and a generic native-to-guest request ABI is not implicitly
  authorized;
- PLAT019: native semantic suspension capability remains explicit; ordinary synchronous
  native bodies remain direct;
- PLAT021: suspension is not unwind and Bytecode owns structured dynamic control;
- PLAT010/PLAT011: normal logical suspension must not retain one physical carrier/thread
  per suspended Task;
- PLAT001: Process/Context isolation and current Truffle hosting rules remain authoritative.

## Why a new platform decision was required

Before B6A5, `ProtosModuleRuntime` could synchronously perform:

```text
create/cache INITIALIZING
    -> call module CallTarget
    -> mark READY
    -> return module instance
```

After production roots become Bytecode C-prime, the child module call can produce a
`ContinuationResult`. Treating that as ordinary completion would mark the module READY
too early and return the continuation object instead of the module instance.

PLAT017 deliberately rejected making a generic native-to-Bytecode composed guest-call
request protocol the default architecture. It explicitly left a later concrete native
wrapper that genuinely needs guest-child composition as a new platform decision. Standard
`import` is the first such concrete case.

## Exhaustive current Truffle implementation survey

The approval audit screened all **16 principal** and **21 experimental/historical**
implementations in the current Truffle catalogue required by GITHUB010. The goal was not
to copy another language, but to identify where real implementations place module
lifecycle, guest execution, asynchronous state and continuation ownership.

### Principal implementations

| Implementation | Relevant architecture/evidence | PLAT025 contribution |
| --- | --- | --- |
| Enso | Production Truffle runtime with substantial project/module machinery; no maintained generic native-wrapper guest-call continuation ABI found. | Scale evidence; no counterexample to A′. |
| Espresso | Continuation state belongs to the interpreter/VM stack machinery; native/VM frames and held locks are delicate suspension boundaries. | Strong evidence that the Java/native `import` wrapper must not become the continuation. |
| FastR | R-compatible package/namespace loading remains synchronous; managed/native crossings are explicit and can be expensive. | Negative evidence against gratuitous native/guest round trips or hidden continuation protocols. |
| GraalJS | `CyclicModuleRecord` owns `EvaluatingAsync`, cycle root, async parents, pending async dependencies, top-level capability, evaluation error and execution result. | Strongest direct evidence that pending module completion belongs to module lifecycle. Its ECMAScript TLA dependency graph is language semantics and must not be copied into Protos. |
| GraalPy | Python/importlib-compatible import remains distinct from Python async; concurrency still has an explicit GIL boundary. | Supports keeping import lifecycle separate from generic async machinery; global serialization is not Protos-aligned. |
| GraalWasm | Separates module evaluation/representation from instantiation and import resolution. | Supports explicit module lifecycle/value state without making control state part of the module value. |
| grCUDA | Explicit interop/resource/callable boundaries; no materially comparable suspendible module initializer. | No counterexample; useful only as FFI/resource-boundary evidence. |
| **Apple Pkl** | `ModuleCache` creates/caches the empty module before initialization for recursive identity; `VmLanguage.initializeModule()` then calls the guest module `CallTarget`. External readers may use `Future`, but their loader boundary ultimately waits synchronously. | Closest structural precedent: module-specific cache/lifecycle outside child guest execution. Protos keeps that separation but replaces physical waiting with C-prime yield/resume. |
| SimpleLanguage | Official Truffle/Bytecode-DSL reference for ordinary call targets, specialization and generated bytecode operations. | Strong substrate evidence for nested C-prime child execution; not a module-lifecycle precedent itself. |
| SOMns | Actors/promises are explicit language/runtime mechanisms rather than hidden wrappers around arbitrary native facilities. | Supports not inventing a hidden child Task/Promise solely for import. |
| Sulong / LLVM | Keeps code identity, intrinsic/native implementation and guest execution boundaries explicit. | Supports specific implementation specialization rather than a universal wrapper request language. |
| TRegex | Specialized internal regex engine, not a general module-loading language runtime. | Screened; not materially comparable. |
| TruffleRuby | `RequireNode` owns loaded-feature/lock lifecycle, parses to `RootCallTarget`, executes the guest target, and publishes loaded state only after success. | Strong precedent for a loader-specific lifecycle around child guest execution; its synchronous locks must not be retained through Protos yield. |
| TruffleSOM | Smalltalk-family implementation with synchronous class/source loading; no comparable suspendible module lifecycle. | Simplicity evidence only. |
| TruffleSqueak | Represents Smalltalk Process/Context state explicitly and performs process switches as VM/language control rather than assuming Java-stack identity. | Supports separating logical retained state from physical carrier stack. |
| Yona | Historical Truffle implementation centered on transparent async; current project direction has moved beyond the old Truffle backend. | Portability warning: the durable PLAT025 concept should survive a future non-Truffle backend. |

### Experimental / historical implementations

| Implementation | Relevant lesson |
| --- | --- |
| BACIL | CIL/assembly loading; no stronger suspendible-module precedent. |
| bf | No meaningful module lifecycle. |
| brainfuck-jvm | No meaningful module lifecycle. |
| Cover | Experimental C++ subset; no stronger module/continuation boundary. |
| DynSem | Semantics framework; no production loader analogue stronger than the selected design. |
| Heap Language | Tutorial/interop language; no comparable lifecycle. |
| hextruffe | Small experimental language; no comparable lifecycle. |
| islisp-truffle | Synchronous load/eval family; no C-prime module-init precedent. |
| LuaTruffle | Lua-style synchronous load/require family; no stronger suspendible lifecycle. |
| Mozart-Graal | Historical HotSpot coroutine patch for many lightweight threads. | Demonstrates feasibility of runtime-specific coroutine machinery but with substantially worse portability/carrier coupling for Protos. |
| Mumbler | Experimental Lisp; no stronger loader precedent. |
| PorcE | Explicit continuation objects capture variables plus `RootCallTarget` because Orc semantics are continuation-centric. | Shows explicit continuations can work when they are a language mechanism; does not justify a generic hidden native-wrapper ABI in Protos. |
| ProloGraal | Backtracking-oriented runtime; no module-initialization analogue stronger than A′. |
| PureScript | Mostly static/compiled module model; no comparable runtime module suspension. |
| Reactive Ruby | Reactivity/control is explicit language behavior rather than hidden import machinery. |
| shen-truffle | Experimental port; no stronger lifecycle precedent. |
| TruffleBF | No meaningful module lifecycle. |
| streamblocks-graalvm | Actor/dataflow scheduling is explicit in the language/runtime model. | Supports not introducing an invisible import-only scheduling graph. |
| TruffleMATE | Highly reified Smalltalk runtime; no direct suspendible module-init evidence stronger than selected architecture. |
| TrufflePascal | Synchronous program/module loading family; no comparable C-prime boundary. |
| ZipPy | Historical Python implementation; import remains Python-family synchronous lifecycle and is superseded as current evidence by GraalPy. |

No maintained implementation surveyed establishes a compelling precedent for a **generic
native wrapper returning an arbitrary guest-call request that the Bytecode interpreter
must execute and later return to the native wrapper** as the normal module-loading
architecture.

## Strongest transferable precedents

### GraalJS — asynchronous module lifecycle stays in the module record

GraalJS is the strongest direct counterexample to the assumption that module evaluation
must always be synchronous. Its cyclic module record explicitly owns asynchronous
evaluation state, including pending async dependencies and async parent modules.

The transferable rule is:

> pending module completion belongs to module lifecycle state.

The non-transferable part is ECMAScript's top-level-await Promise/dependency graph. Protos
has no top-level-await module semantics and its recursive `INITIALIZING` import returns the
same instance immediately. PLAT025 therefore adopts lifecycle ownership without importing
a new async-module graph.

### Apple Pkl — cache-before-initialize plus child guest execution

Apple Pkl is the closest maintained structural precedent.

Its `ModuleCache` cannot simply use `computeIfAbsent` because eager class initialization
can recursively load the same module and module/class identity must remain stable. It
creates an empty module, caches it before initialization, then invokes a module initializer.

`VmLanguage.initializeModule()` parses/builds the module and executes its guest
`CallTarget`. This gives the same architecture shape needed by Protos:

```text
module cache/lifecycle
    -> child guest execution
```

Pkl's external reader machinery can use `CompletableFuture`/`Future`, but the loader
boundary ultimately blocks on `Future.get()`. Protos explicitly does **not** copy that
physical waiting because PLAT010/011/014 require bounded reusable carriers and logical
suspension. The transferable idea is the responsibility split, not the blocking mechanism.

### Espresso — interpreter continuation, not native wrapper continuation

Espresso demonstrates the danger of native/VM frames as part of a resumable stack. Its
continuation machinery captures guest stack/interpreter state; native/VM frames and held
locks constrain where suspension is legal.

For PLAT025, this is strong evidence that `ProtosModuleRuntime.import`'s Java call stack
must not become the resumable continuation. The child Bytecode root yields an ordinary
C-prime `ContinuationResult`; the module-lifecycle carrier merely survives beside it.

### TruffleRuby — loader-specific lifecycle around a guest child

`RequireNode` owns feature-loading lifecycle, circular-load coordination and loaded-state
publication around a parsed guest `RootCallTarget`.

This is a strong precedent for a **specific loader lifecycle** rather than a generic
native-wrapper request ABI. TruffleRuby's physical locks/synchronous execution are not
portable to a Protos suspension extent and are therefore not copied.

### SOMns, TruffleSqueak, StreamBlocks and PorcE

These systems reinforce that process switching, promises, actors, dataflow and
continuations belong in explicit runtime/language mechanisms when the language defines
them. They do not justify manufacturing a hidden Task, Promise graph or second
continuation family solely to implement `import`.

## Relevant non-Truffle evidence

The audit also considered mature non-Truffle systems where they expose an analogous
lifecycle boundary:

- **ECMAScript specification / V8** — top-level await places pending evaluation and
  dependency state in the module graph/records. Strong lifecycle evidence, but its async
  graph is language semantics not present in Protos.
- **CPython** — module identity is inserted into `sys.modules` before module code executes
  to support recursive imports, and a failing import removes the failing module entry.
  Strong cache-before-execute/failure-cleanup evidence; execution itself is synchronous.
- **OpenJDK/JVM class initialization** — explicit per-class initialization state and
  recursive initialization rules rather than a generic arbitrary-call continuation ABI.
  Its thread-blocking rules are not suitable for Protos.
- **.NET type initialization** — explicit type-initialization lifecycle; blocking/waiting
  inside initialization has well-known deadlock hazards. Useful negative evidence against
  carrier blocking.
- **BEAM/actor runtimes** — scheduling state belongs to processes/actors rather than being
  hidden inside ordinary loader wrappers. Supports preserving Protos Task/Actor ownership.

## Candidate set

### A′ — exact-standard `import` intrinsic + prepared module lifecycle child

**Selected.**

Ordinary lookup/selection remains authoritative. Only after the exact canonical standard
`import` behavior has been selected may Bytecode replace its physical native wrapper with
a backend-private lifecycle carrier and nested C-prime child root.

Properties:

- no generic native request language;
- no new Protos-visible callable/import entity;
- synchronous READY/INITIALIZING cache hits remain cheap;
- cache miss retains only O(1) lifecycle state plus genuinely live C-prime child state;
- recursive `INITIALIZING` behavior remains immediate;
- yield is not completion or unwind;
- normal completion and escaping failure each finalize the exact record once;
- module body remains an ordinary Bytecode root and optimizer/tooling surface.

### B — module-specific continuation envelope

The native import implementation would return a backend-private
`ModuleInitializationContinuation` wrapping the child continuation plus module lifecycle.

Not selected. It can be made correct, but creates a second continuation-shaped value that
generic yield/resume paths must understand. It blurs the already-clean distinction between
module lifecycle and interpreter continuation.

### C — generic native composed-guest-call request ABI

Any native Closure could return a generic request asking Bytecode to invoke a guest target
and later resume the native wrapper.

Not selected. It is reusable in theory, but PLAT017 already rejected this as the default
architecture. No maintained Truffle implementation survey produced strong enough evidence
to justify the additional cross-layer protocol, retained state and optimization/tooling
surface now.

### D — child Task for module initialization

Create another Task for a cache-miss body and wait from the importing Task.

Rejected. It changes Task ownership, cancellation, ordering and dynamic-control structure;
same-Actor import cycles can acquire artificial waits; it adds semantics not present in
the language.

### E — block/park a physical carrier

Execute module initialization synchronously and park the physical host thread/carrier
while the guest is logically suspended.

Rejected by PLAT010/011/014. Memory/thread scaling becomes proportional to suspended work
and the production C-prime motivation is defeated.

### F — retain an AST/replay island for imported module bodies

Rejected as a migration closure strategy. It preserves the exact replay/interpreter
architecture B6 exists to remove and prevents one complete production continuation path.

### G — source-backed standard import wrapper plus smaller native primitive

Move more import lifecycle into guest Protos source and expose a native primitive that
resolves/starts/finalizes a module child.

Credible future direction, but not selected now. It still requires a privileged
module-execution/finalization boundary, broadens the current slice and risks exposing a
new internal module protocol without removing the underlying composition problem.

### H — ECMAScript-style asynchronous module dependency graph

Give Protos modules Promise-like pending dependencies, async parents and explicit async
module evaluation state.

Rejected because it changes language semantics. It is a mature solution to ECMAScript's
top-level-await problem, not to the current Protos contract.

### I — Pkl-style synchronous/blocking loader

Keep the loader Java-synchronous even when a module body waits, blocking the physical
carrier until completion.

Rejected. Pkl makes this trade-off because its module execution model does not need Protos
C-prime Task suspension. For Protos it would violate bounded-carrier and pay-only-for-live-
logical-state goals.

## Required GITHUB010 scorecard

Scores are 1–5. Confidence: `H` high, `M` medium. Arithmetic supports comparison but does
not override semantic or architectural disqualifiers.

| Criterion | **A′** | B | C | D | E | F | G | H | I |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | **5/H** | 4/H | 4/H | 2/H | 3/H | 4/H short-term | 4/M | 2/H | 3/H |
| Protos alignment | **5/H** | 3/H | 2/H | 2/H | 1/H | 1/H | 4/M | 2/H | 1/H |
| Future-option resilience | **5/H** | 4/H | 4/H | 3/H | 2/H | 1/H | 4/M | 4/H | 2/H |
| Scalability | **5/H** | 4/H | 3/H | 3/H | 1/H | 2/H | 4/M | 4/H | 1/H |
| Conceptual simplicity | 4/H | 3/H | 2/H | 3/H | 3/H | 3/H | 3/M | 1/H | **5/H** |
| Portability / implementation freedom | **5/H** | 4/H | 3/H | 4/H | 2/H | 2/H | 4/M | 3/H | 2/H |
| Runtime / resource cost | **5/H** | 4/H | 3/H | 2/H | 1/H | 1/H | 4/M | 2/H | 1/H |
| Failure / operability | **5/H** | 3/H | 3/H | 2/H | 2/H | 3/H | 3/M | 3/H | 2/H |
| Reversibility / migration cost | **5/H** | 4/H | 3/H | 2/H | 2/H | 1/H | 4/M | 2/H | 3/H |
| Evidence maturity / implementation risk | **5/H** | 3/H | 3/H | 3/H | **5/H** | **5/H** | 3/M | **5/H** | **5/H** |
| **Total / 50** | **49** | **36** | **30** | **26** | **22** | **23** | **37** | **28** | **25** |

### Focused owner-axis scoring

The project-owner requested additional focus on future endurance, scalability and Protos
philosophy. Scores are 0–10.

| Candidate | Future endurance | Scalability | Protos philosophy | Mean |
| --- | ---: | ---: | ---: | ---: |
| **A′ exact-standard lifecycle + child C′** | **9.8** | **9.8** | **10.0** | **9.87** |
| B module continuation envelope | 7.4 | 8.6 | 6.8 | 7.60 |
| C generic native→guest ABI | 8.0 | 7.0 | 5.2 | 6.73 |
| D child Task | 5.0 | 5.5 | 2.5 | 4.33 |
| E blocked carrier | 2.0 | 1.0 | 1.0 | 1.33 |
| F AST/replay island | 1.0 | 2.0 | 1.0 | 1.33 |
| G guest wrapper + native primitive | 7.6 | 8.0 | 7.2 | 7.60 |
| H ECMAScript-style async module graph | 8.5 | 7.0 | 4.0 | 6.50 |
| I Pkl-style blocking loader | 6.0 | 2.0 | 2.0 | 3.33 |

A′ is selected because it clears all hard invariants while also leading the comparative
scorecard. The arithmetic total alone is not the authority.

## Required implementation invariants for PERF006-B6A5

1. Ordinary lookup/selection happens before any standard-import specialization.
2. Recognition is by exact canonical implementation/provenance, not selector spelling or
   receiver shape alone.
3. READY and INITIALIZING cache hits return the exact cached module instance without
   creating continuation/lifecycle child state.
4. A cache miss creates one module instance and inserts the exact active record as
   INITIALIZING before the body executes.
5. The body executes as one nested Bytecode C-prime module root under the already-defined
   module activation.
6. The module-lifecycle carrier is not exposed to guest code, interop or tooling as a
   Protos value and is not a continuation family.
7. `ContinuationResult` / C-prime remains the sole interpreter continuation
   representation.
8. Yield/suspension leaves the record INITIALIZING and performs neither READY publication
   nor failure cleanup.
9. Normal completion marks the exact record READY exactly once and returns the module
   instance, not the module body's final expression value.
10. Escaping Error, cancellation or other terminal failure removes the exact active record
    using identity-safe semantics and propagates the original transfer.
11. Recursive same-Actor import observing INITIALIZING returns immediately; no wait graph,
    Promise graph, Task join or SCC scheduler is introduced.
12. No Java lock, host carrier or global mutex may remain logically owned across a
    suspended module child.
13. No global module-initialization continuation registry is introduced.
14. No generic native→guest composed-call ABI is introduced by B6A5.
15. No AST/replay fallback remains on the B6A5 production module-child path.
16. Module resolution remains synchronous/non-suspending under the current semantic
    contract; PLAT025 does not pre-authorize network/async resolver semantics.
17. PLAT004/005/008/013/015 tooling/source/debugger authorities remain applicable to the
    nested module root.
18. Cross-Actor/Process module-cache isolation remains unchanged.
19. A stable compiled child target may be cached/shared only where existing Context/language
    authority already permits it; PLAT025 creates no new global compiled-code registry.
20. If B6A5 discovers an observable module semantic not already determined by the current
    specification, implementation stops at the applicable Dxxx/specification gate.

## Scalability and future stress

### Many already-loaded imports

READY/INITIALIZING hits remain a synchronous lookup-and-return path. There is no retained
module-lifecycle carrier after the import has completed.

### Deep import chains with real suspension

Retained state is proportional to genuinely live work:

```text
module-lifecycle state  O(live initializing module depth)
C-prime continuation    O(live suspended guest continuation frames)
physical carriers       O(runtime carrier capacity), not O(suspended modules)
global init registry    O(0)
```

### Cycles

The existing cache-before-execute rule remains decisive. `A -> B -> A` observes A's
INITIALIZING instance and returns it immediately. No Promise/SCC wait graph is introduced,
so the selected architecture does not manufacture an import deadlock that the language
does not define.

### Many Actors and Processes

The module cache remains Actor-local. A′ adds no cross-Actor mutable table, global lock or
per-Process daemon. Multiple Actors can initialize their own module instances without a
shared lifecycle bottleneck.

### Cancellation and unwind

A suspended child is resumed/unwound through the ordinary C-prime chain. Cancellation is
not converted into module completion. Failure cleanup occurs only when terminal control
escapes the lifecycle extent, and exact-record identity prevents deleting a replacement
record accidentally.

### Optimizer and Bytecode DSL evolution

The module child remains an ordinary stable Bytecode root/CallTarget, keeping normal
partial-evaluation and direct-call specialization opportunities. The durable PLAT025
contract does not depend on a particular Java carrier class or exact generated Bytecode
DSL API shape.

### Native Image / AOT

A′ requires no dynamic class generation beyond the already-selected Bytecode DSL/runtime
model, no global reflective registry and no thread-per-suspended-module design. Backend-
private lifecycle structures can remain closed-world ordinary runtime objects.

### Future non-Truffle backend

The durable architecture can be restated backend-neutrally:

```text
select ordinary standard import
    -> own one module-init lifecycle
    -> run one nested guest computation
    -> suspend/resume through the backend's sole continuation mechanism
    -> commit READY or exact-record failure cleanup
```

A non-Truffle backend can implement the same lifecycle without exposing Java or
`ContinuationResult`.

### Distributed / remote future

PLAT025 does not create distributed module-cache semantics. If a future Protos runtime
supports distributed Actors or remote module stores, each Actor's logical module identity
and lifecycle authority still has a local owner; remote source acquisition would require
its own authority/suspension decision rather than silently becoming part of PLAT025.

## Regret trigger and escape path

The most plausible regret trigger is evidence that **many** standard/native facilities
need the same semantically invisible pattern:

```text
native/host lifecycle setup
    -> one suspendible guest child computation
    -> native/host lifecycle finalization
```

At sufficient repetition, multiple exact-standard intrinsics would become noisy and a
narrow reusable composed-native facility could reduce implementation duplication.

**Escape path:** collect those concrete cases and reopen/extend PLAT017/PLAT025 with
evidence. A′ keeps the lifecycle carrier backend-private and leaves all suspension in the
ordinary C-prime child, so several specialized implementations can later be factored into
a common helper without changing Protos semantics or existing continuation values.

This is intentionally preferable to selecting a generic request ABI before multiple
concrete consumers prove it is needed.

## Strongest argument against A′

A′ adds another exact-standard backend intrinsic. That is implementation coupling:
recognition must remain correct across standard-library/bootstrap evolution and tooling
must preserve the same logical call observations that the ordinary standard behavior
would have produced.

If exact-standard intrinsics proliferate, the backend could accumulate special-case
recognizers.

The cost is accepted because:

- specialization is behind ordinary lookup/selection, preserving Protos's object model;
- no new public or semantic entity is created;
- cache hits remain minimal;
- suspension stays in one continuation architecture;
- retained state is local and bounded by live work; and
- the design keeps a clean evidence-driven path to later factoring if repetition appears.

## Deliberately deferred questions

PLAT025 does **not** decide:

- asynchronous/network module resolver semantics;
- a public async-import feature or top-level-await module graph;
- distributed/shared module caches;
- a generic native-to-guest composed-call ABI;
- exact Java class names or field layout for the lifecycle carrier;
- exact child-target cache representation;
- post-B6 refactoring of `ProtosModuleRuntime` once C-prime is the sole production backend;
- optimizer/runtime distribution wiring owned by later PERF006 closure work;
- future package/module hot reload or cache invalidation semantics.

Any one of those that becomes substantive requires its own existing authority or a new
explicit decision.

## Sources / implementation evidence

Primary evidence used by the approval audit included:

- Truffle language catalogue:
  `https://github.com/oracle/graal/blob/master/truffle/docs/Languages.md`
- Truffle Bytecode DSL:
  `https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/BytecodeDSL.md`
- Bytecode DSL `GenerateBytecode` / continuation API:
  `https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/GenerateBytecode.html`
- GraalJS `CyclicModuleRecord`:
  `https://github.com/oracle/graaljs/blob/master/graal-js/src/com.oracle.truffle.js/src/com/oracle/truffle/js/runtime/objects/CyclicModuleRecord.java`
- Apple Pkl `VmLanguage`:
  `https://github.com/apple/pkl/blob/main/pkl-core/src/main/java/org/pkl/core/runtime/VmLanguage.java`
- Apple Pkl `ModuleCache`:
  `https://github.com/apple/pkl/blob/main/pkl-core/src/main/java/org/pkl/core/runtime/ModuleCache.java`
- Apple Pkl `ExternalModuleResolverImpl`:
  `https://github.com/apple/pkl/blob/main/pkl-core/src/main/java/org/pkl/core/externalreader/ExternalModuleResolverImpl.java`
- Apple Pkl `MessageTransports`:
  `https://github.com/apple/pkl/blob/main/pkl-core/src/main/java/org/pkl/core/messaging/MessageTransports.java`
- TruffleRuby `RequireNode`:
  `https://github.com/truffleruby/truffleruby/blob/master/src/main/java/org/truffleruby/language/loader/RequireNode.java`
- TruffleSqueak:
  `https://github.com/hpi-swa/trufflesqueak`
- PorcE:
  `https://github.com/orc-lang/orc/tree/master/PorcE`
- SOMns:
  `https://github.com/smarr/SOMns`
- StreamBlocks GraalVM:
  `https://github.com/streamblocks/streamblocks-graalvm`
- OpenJDK/JLS class initialization:
  `https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html`
- Python import system:
  `https://docs.python.org/3/reference/import.html`
- ECMAScript modules:
  `https://tc39.es/ecma262/multipage/ecmascript-language-scripts-and-modules.html`

The repository-local PLAT014/017/019/021 records and the current module runtime are the
authoritative Protos implementation constraints consumed by this decision.
