# PLAT026 — Bytecode production root-tag compatibility boundary

Status: **RATIFIED — Candidate C′+ selected**

Nature: durable non-normative Truffle/Bytecode-DSL instrumentation and debugger architecture decision

Approved by project owner: **2026-09-11**

GitHub Issue: **#375**

Primary consumer: `PERF006-B6A6` / GitHub #276 and the later PERF006-B6 production cutover and replay retirement.

Normative effect: **none**. PLAT026 changes no Protos call, Closure, Object, module, stack, source, scope, scheduling, suspension, continuation, failure or debugger semantics. It selects only how the JVM/Truffle implementation projects already-existing semantic execution roots to Truffle tooling while keeping implementation-helper roots invisible as guest roots.

## Decision

Select **Candidate C′+ — truthful semantic-root configurations with automatic `StandardTags.RootTag`, while helper Bytecode roots remain untagged**.

```text
ProtosLanguage
    provides StandardTags.RootTag
    does not yet provide StandardTags.RootBodyTag

semantic Bytecode roots
    top-level / public-source execution
    module-body execution
    Closure activation
        -> automatic Truffle RootTag

implementation/helper Bytecode roots
    Object construction/body child roots
    future implementation-only child roots
        -> NO RootTag
```

The durable authority is the **semantic-root versus helper-root** distinction, not a fixed Java class hierarchy. A physical `RootNode`, `CallTarget`, generated-interpreter root or child execution target does not by itself become a tool-visible Protos root.

Where a Bytecode configuration represents only semantic roots, Protos uses the Truffle Bytecode DSL's automatic root tagging so the framework owns the required pre-prolog probe boundary. PLAT026 does not select manual root-tag emulation as the production mechanism.

`RootBodyTag` remains deliberately deferred. The production evidence that triggered PLAT026 proves a need for `RootTag` in the real GraalVM debugger/DAP contract; it does not establish a correct Protos `RootBodyTag` boundary.

## Trigger and existing authority

PERF006-B6A6 reached the production parse/root-task cutover after B6A5 closed. Its candidate demonstrated public parse lowering to the C-prime Bytecode backend while retaining existing source/StatementTag/CallTag, native fast-path and module-init behavior. Full debugger/DAP validation then failed because `StandardTags.RootTag` was requested by the real Truffle debugger but not provided by `ProtosLanguage`.

That is the production evidence PLAT005 explicitly required before broadening its instrumentation baseline.

PLAT005 remains authoritative that tag membership projects canonical/semantic execution roles rather than physical Java/Truffle topology, helper roots must not become semantic frames merely because they are `RootNode`s or `CallTarget`s, StatementTag/CallTag remain unchanged, and new tooling families require evidence. PLAT026 resolves **only the RootTag part** that PLAT005 deferred.

PLAT004 remains authoritative for source ownership; PLAT008 for logical replay-site identity; PLAT013/015 for debugger values/scopes; PLAT014 for C-prime continuation structure; and PLAT018 for real GraalVM DAP session hosting.

## Framework evidence

Truffle defines `RootTag` as the root of a guest function, method or Closure and uses it for debugger root/frame operations. `RootBodyTag` is a separate body-after-prolog concept.

Current Bytecode DSL `GenerateBytecode` supports automatic root tagging and explicitly recommends it over manually recreating root tagging because the probe must be notified before the root prolog. Root-body tagging is independently configurable.

The framework consequence is therefore:

> shape the generated-root population to match semantic roots, then use automatic RootTag there.

It is not:

> tag every physical root because the framework can do so.

## Comparative Truffle survey

### GraalPy

GraalPy publishes Root/RootBody and richer tags and uses Bytecode DSL tag instrumentation. Its Bytecode compiler creates roots around semantic top-level code units such as modules, functions and classes. **Lesson:** automatic root tagging is robust when generated roots are already meaningful guest code units.

### Oracle SimpleLanguage

SimpleLanguage publishes Root/RootBody, but its Bytecode configuration deliberately disables implicit `RootBodyTag` when initialization must run before the true body boundary. **Lesson:** RootTag and RootBodyTag are separable, and semantic/prolog truth overrides convenience.

### TruffleRuby

TruffleRuby publishes `RootTag` without requiring RootBodyTag and explicitly marks semantic body/root roles rather than relying on Java `RootNode` existence. **Lesson:** semantic guest role, not physical class identity, determines tooling identity.

### GraalJS

GraalJS publishes Root/RootBody but also has physical parse/program wrapper roots that are internal and non-instrumentable. **Lesson:** a physical `RootNode` may remain invisible when it is infrastructure rather than a guest frame.

### Espresso

Espresso's instrumentable roots correspond closely to executable Java methods and can truthfully report Root/RootBody. **Lesson:** broad root tagging is simple when physical and semantic root cardinality already align.

### Sulong / LLVM

Sulong publishes standard root tags around source-level LLVM function execution while substantial runtime/helper machinery remains outside source-function identity. **Lesson:** the semantic/helper distinction remains viable at large runtime scale.

### GraalWasm

GraalWasm exposes root tags on instrumentable function execution while module parsing/decoding and other root-shaped infrastructure remain separate. **Lesson:** function-root visibility need not promote module/runtime helpers.

### FastR

FastR publishes standard roots plus R-specific instrumentation and assigns root identity to meaningful executable/body nodes. **Lesson:** richer tooling remains manageable when ownership follows language structure.

### TruffleSqueak

TruffleSqueak publishes `RootTag` without RootBodyTag and explicitly marks interpreter roots. **Lesson:** RootTag-only is a legitimate narrow production configuration for a dynamic object-oriented language.

### Enso

Enso distinguishes root-tagged blocks, root-body blocks, statement blocks and blocks deliberately invisible to tooling. **Lesson:** one execution substrate can expose different tooling roles according to semantic use — closely matching Protos's semantic roots versus Object-construction helpers.

### Apple Pkl

Apple Pkl publishes the custom `EXPRESSION` tag it actually needs and does not advertise generic RootTag merely because Truffle offers it. **Lesson:** expose a tooling concept only when it can be represented truthfully. Pkl argues against both indiscriminate helper tagging and declaration-only fake compatibility. It is not a direct template for Protos because Protos intentionally integrates the real generic GraalVM DAP, which consumes ROOT.

Specialized/internal engines such as TRegex and minimal/tutorial languages do not provide a stronger precedent for this production debugger boundary than the implementations above.

## Relevant non-Truffle evidence

HotSpot/JDI, LLVM/DWARF and .NET/CLR likewise present source-language method/subprogram/frame concepts separately from compiler/runtime helper functions. Their mechanisms differ, but they reinforce the portable principle: **debugger root identity belongs to guest semantic execution units, not backend helper topology**.

## Candidate set

### A′ — declare RootTag, emit none

Rejected as the durable design. It is minimal and could satisfy the immediate tag-set validation, but advertises ROOT support while providing no truthful ROOT locations and leaves ROOT-sensitive generic stepping under-specified.

### B — automatically tag every current Bytecode root

Rejected. It promotes Object construction/body helper roots to guest function roots and violates PLAT005's semantic/tooling correspondence invariant.

### C′+ — selective semantic-root configurations + automatic RootTag

**Selected.** Provide `RootTag`; arrange generated Bytecode root/configuration authority so semantic source/module/Closure activations receive automatic RootTag while helper Object-construction/body roots do not. Keep RootBodyTag deferred. Shared operations/lowering machinery remains an implementation detail.

### D — manual RootTag regions inside the mixed configuration

Rejected as baseline. Truffle warns that correct root tagging around the root prolog is non-trivial and recommends automatic root tagging; manual regions would unnecessarily own subtle framework protocol.

### E — customize/fork DAP to avoid ROOT

Rejected. LM009 intentionally integrates the real GraalVM DAP; a Protos-specific adapter would create a second debugger-protocol authority and reduce generic Truffle tooling compatibility.

### F — retain an AST debugger island

Rejected. Debug-only backend switching would preserve the replay/AST architecture PERF006-B6 is retiring and create backend-dependent debugger behavior.

## Required GITHUB010 scorecard

Scores are 1–5. Confidence: `H` high, `M` medium. Arithmetic is advisory; a hard truthfulness violation can disqualify a candidate.

| Criterion | A′ | B | **C′+** | D | E | F |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | 3/H | 2/H | **5/H** | 4/M | 3/H | 4/H short-term |
| Protos alignment | 3/H | 1/H | **5/H** | 3/M | 2/H | 1/H |
| Future-option resilience | 3/M | 3/M | **5/H** | 3/M | 2/H | 1/H |
| Scalability | **5/H** | 4/H | **5/H** | 4/H | 3/M | 2/H |
| Conceptual simplicity | **5/H** | **5/H** | 4/H | 3/M | 2/H | 3/H |
| Portability / implementation freedom | 3/M | 3/M | **5/H** | 3/M | 1/H | 2/H |
| Runtime / resource cost | **5/H** | 4/H | **5/H** | 4/H | 2/H | 2/H |
| Failure / operability | 3/M | 2/H | **5/H** | 3/M | 2/H | 3/H |
| Reversibility / migration cost | 3/M | 2/H | **5/H** | 3/M | 2/H | 1/H |
| Evidence maturity / implementation risk | 3/M | 4/H | **5/H** | 3/M | 3/H | 4/H |
| **Total / 50** | **36** | **30** | **49** | **33** | **22** | **23** |

A′ scores well on cost/scaling but claims a capability it does not realize. B is mechanically mature but violates the current helper-root invariant. C′+ follows the framework-preferred mechanism while keeping the semantic contract truthful. D can work but assumes avoidable root-prolog protocol risk. E sacrifices generic tooling compatibility. F carries severe backend-divergence and migration cost.

### Owner-focus scoring

| Candidate | Future endurance | Scalability | Protos philosophy | Mean |
| --- | ---: | ---: | ---: | ---: |
| A′ declaration-only | 4.0 | **9.5** | 4.5 | 6.00 |
| B all physical roots | 4.5 | 8.5 | 2.0 | 5.00 |
| **C′+ semantic roots + automatic RootTag** | **10.0** | **9.5** | **10.0** | **9.83** |
| D manual root regions | 5.0 | 8.0 | 6.5 | 6.50 |
| E custom DAP behavior | 3.0 | 4.5 | 2.0 | 3.17 |
| F AST debugger island | 1.0 | 3.0 | 1.0 | 1.67 |

## Required PERF006-B6A6 invariants

1. `ProtosLanguage.@ProvidedTags` may add `StandardTags.RootTag`; no other tag family is approved here.
2. `RootBodyTag` remains unprovided/disabled unless separately approved.
3. Existing StatementTag and CallTag semantics remain unchanged.
4. A semantic-root Bytecode configuration contains only roots that can truthfully appear as guest execution/function roots.
5. Current top-level/public-source, module-body and Closure activation roots are within the approved semantic-root family.
6. Current Object-body/construction child roots are helpers and must not receive RootTag.
7. New helper root categories are untagged by default until semantic-root status is established.
8. Semantic Bytecode roots use automatic RootTag rather than manually emulating root-prolog protocol.
9. Exact generated Java class count, names and operation-sharing mechanism are implementation details.
10. Root tagging must not alter lookup, receiver/method-home identity, parameter/default evaluation, module lifecycle, return/unwind, scheduling or suspension.
11. No root membership is inferred solely from `RootNode`, `CallTarget`, source-section or generated-class identity.
12. Helper roots must not leak as extra debugger stack/root stop points solely because they execute source-backed helper work.
13. Source ownership remains PLAT004-authoritative; value/scope projections remain PLAT013/015-authoritative; continuation ownership remains PLAT014-authoritative.
14. Ordinary execution without attached instrumentation gets no global root registry or debugger lock.
15. Multi-Context execution shares no mutable root-tag authority beyond already-approved immutable/generated code sharing.
16. Native Image/AOT requires no runtime-generated root classification registry.
17. Real debugger/DAP validation must prove ROOT-sensitive stepping, stack frames, scopes and termination with helpers hidden.
18. If implementation needs new guest-frame semantics, RootBodyTag or another tooling category, stop at the applicable decision gate.

## Scalability and future stress

Root authority is immutable generated/configuration metadata, not per-activation state. There is no per-Task/Actor/Process root registry and no central lock. Instrumentation probes/events remain pay-only-when-attached.

C-prime suspension may occur inside or across semantic roots without making RootTag continuation identity. Cancellation/unwind stays under PLAT014/021. Multi-Context source/value/scope state remains context-local under existing decisions.

The invariant survives optimizer/inlining changes: tooling retains semantic root identity even when physical execution is fused or the generated interpreter shape changes. A future Bytecode DSL feature that provides a cheaper per-root semantic classification can replace today's configuration split without reopening PLAT026 if tool-visible membership remains identical.

A non-Truffle backend can implement the same rule directly: expose debugger function/frame roots for semantic Protos execution units and hide compiler/runtime helper units. Distributed debugging remains a separate transport/session problem; PLAT026 creates no distributed root registry.

## Regret trigger and escape path

The most plausible regret trigger is a future Bytecode DSL API that makes separate semantic/helper configurations unnecessary by providing a first-class per-root root-tag classification hook.

**Escape path:** keep the semantic/helper invariant and replace the internal split with that mechanism. PLAT026 deliberately does not mandate a Java class count or lowering shape.

A second regret trigger is a future language decision making Object-body evaluation itself a first-class callable/frame with observable stack semantics. That would require language/spec authority; once ratified, PLAT026 can be amended to include that newly semantic activation.

## Strongest argument against C′+

C′+ adds implementation structure during an already-complex production cutover. A′ is smaller and B uses the Bytecode DSL's all-roots automatic path directly.

That bounded cost is accepted because both shortcuts encode the wrong durable tooling contract: A′ claims ROOT without real roots, while B leaks helper roots. It is safer and more reversible to refactor two truthful internal root families later than to retract false debugger-visible roots or repair generic tools taught a misleading capability.

## Deliberately deferred

PLAT026 does **not** select `RootBodyTag`, Expression/read/write-variable tags, exact generated class count, exact operation-sharing mechanism, future inlining/materialization representation, function-breakpoint naming, distributed debugger protocol, non-Truffle debugger implementation, or any Protos-visible stack-frame semantic.

## PERF006-B6A6 release boundary

Publication releases the tooling/root-tag architecture portion of PERF006-B6A6. The consumer may add RootTag to `ProtosLanguage`, establish semantic-root Bytecode automatic tagging, keep helper Object-body roots untagged, adapt lowering/factories without semantic change, and add focused plus real DAP evidence.

It may not silently add RootBodyTag, broaden other tag families, expose helpers, fork DAP, alter Protos semantics or retain an AST debugger island. Separately identified normal-dispatch compatibility work remains independently owned by PERF006.

## Approval record

The project owner explicitly approved **Candidate C′+** on 2026-09-11 after the expanded comparative audit of GraalPy Bytecode DSL, SimpleLanguage, TruffleRuby, GraalJS, Espresso, Sulong/LLVM, GraalWasm, FastR, TruffleSqueak, Enso and Apple Pkl, together with GraalVM DAP `SourceElement.ROOT`, Bytecode DSL automatic-root-tag requirements and future/scalability/Protos-philosophy scoring.

This approval resolves PLAT005's deferred RootTag boundary only. RootBodyTag and all other deliberately deferred choices remain open.

## Primary implementation evidence

- Truffle `StandardTags`, debugger `SourceElement`, Bytecode DSL `GenerateBytecode` and GraalVM DAP `DebugProtocolServerImpl` in `oracle/graal`.
- Oracle SimpleLanguage in `oracle/graal`.
- GraalPy in `oracle/graalpython`.
- GraalJS in `oracle/graaljs`.
- TruffleRuby in `truffleruby/truffleruby`.
- Espresso, Sulong/LLVM and GraalWasm in `oracle/graal`.
- FastR in `oracle/fastr`.
- TruffleSqueak in `hpi-swa/trufflesqueak`.
- Enso runtime in `enso-org/enso`.
- Apple Pkl instrumentation in `apple/pkl`.

Repository-local PLAT004/005/008/013/014/015/018 and current PERF006 Bytecode lowering remain the authoritative Protos-side constraints.
