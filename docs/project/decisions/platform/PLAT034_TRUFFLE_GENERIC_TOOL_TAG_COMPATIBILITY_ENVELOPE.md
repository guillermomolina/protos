# PLAT034 — Truffle generic-tool tag compatibility envelope

Status: **RATIFIED — Candidate C′ selected**

Nature: durable non-normative Truffle instrumentation/tooling architecture decision

Approved by project owner: **2026-09-13**

GitHub Issue: **#486**

Primary consumers: `PERF006-C3` and later Truffle-hosted generic debugger/LSP compatibility work.

Normative effect: **none**. PLAT034 changes no Protos language semantics, execution ordering,
object model, binding model, Task/Future/Actor/Process behavior, Error/control semantics,
source-language grammar, standard-library contract, scope/value meaning, or debugger-visible
language semantics. It selects only the truthful Truffle tag surface that generic tools may
request from the production Bytecode DSL implementation.

## Decision trigger

PERF006-C3 real-tool validation exposed a concrete Bytecode DSL compatibility boundary after
production replay retirement and optimizer-runtime closure work:

- GraalVM debugger/DAP instrumentation requests `DebuggerTags.AlwaysHalt`;
- Graal dynamic-LSP paths request `StandardTags.ExpressionTag`;
- some generic LSP searches combine `ExpressionTag` with `StatementTag`, `RootTag`, and in a
  separate path `ReadVariableTag`;
- the Bytecode DSL rejects instrumentation requests for tags the guest language did not declare
  as provided.

This evidence resolves a deliberate PLAT005 deferral. It does **not** justify publishing every
standard/debugger tag that generic Graal tools may know about.

PLAT005 remains authoritative for the semantic-minimum instrumentation model. PLAT026 remains
authoritative for truthful semantic `RootTag` exposure. PLAT034 extends that model only where a
truthful Protos projection already exists or where a compatibility category may truthfully have
zero guest locations.

## Selected architecture

Select **Candidate C′ — semantic-minimum generic-tool compatibility using existing semantic
boundaries**.

The durable projection is:

```text
existing PLAT005 / PLAT026 tooling surface

CanonicalSequence direct executable child
          |
          +-- StatementTag
          +-- ExpressionTag        NEW, exact same membership

source-level invocation sites
          +-- CallTag              unchanged

truthful semantic roots
          +-- RootTag              unchanged by PLAT034

DebuggerTags.AlwaysHalt
          +-- provided category    NEW
          +-- actual locations     ZERO
```

No new source-level execution boundary is created by PLAT034.

## ExpressionTag contract

`StandardTags.ExpressionTag` becomes a provided Protos tooling tag.

Its membership is **exactly** the current PLAT005 `StatementTag` membership:

> each direct executable child expression of a `CanonicalSequence`.

This is truthful because the PLAT005 statement boundary is already an executable Protos
expression boundary. PLAT034 therefore reuses existing canonical-role authority instead of
creating a second expression-granularity institution.

The equality is architectural, not accidental:

```text
Expression-membership-set == Statement-membership-set
```

until another explicitly approved decision changes that relationship.

Consequences:

- statement/event cardinality does not increase;
- a one-expression body remains one expression-tooling point;
- two sequential expressions remain two independent points even on one physical source line;
- a multiline expression remains one point when it is one sequence child;
- receiver, argument, literal, lookup, arithmetic, matching-helper, runtime-helper and other
  structural subexpressions do not become independent expression points merely because they
  execute;
- physical Bytecode DSL operation shape is never tooling authority by itself;
- helper roots and backend-private child roots do not acquire expression authority.

If a future tooling feature requires finer expression coverage, that is a new architectural
choice. PLAT034 does not pre-authorize it.

## AlwaysHalt contract

`DebuggerTags.AlwaysHalt` becomes a provided compatibility category with **zero actual Protos
locations**.

Truffle's `AlwaysHalt` category models a guest instruction whose own semantics require the
debugger to stop, such as a language-level debugger/trap statement. Protos currently has no such
guest construct.

Therefore PLAT034 explicitly prohibits manufacturing `AlwaysHalt` membership on:

- ordinary statements;
- call/send/super-send sites;
- semantic roots;
- helper roots;
- scheduler/suspension boundaries;
- Error/control-flow machinery;
- native/runtime helper operations.

Declaring the category does not claim that Protos has a halt instruction. Its truthful current
membership is empty.

A future genuine guest halt/debugger construct would require its own semantic/platform review
before any real `AlwaysHalt` location may exist.

## Generic-LSP boundary

The audit found that Graal's generic LSP does not use only one tag shape.

Representative generic searches include conceptually:

```text
ExpressionTag + StatementTag + RootTag
```

and, for another path:

```text
ExpressionTag + ReadVariableTag + StatementTag
```

PLAT034 must not become an open-ended rule that Protos advertises every tag a generic tool might
request.

In particular, `ReadVariableTag` remains **unprovided** and unresolved. Protos' binding/context
model must not be collapsed into a conventional local-variable model merely for a generic LSP.

If a real later LSP request reaches a path that fails specifically because `ReadVariableTag`,
`WriteVariableTag`, `RootBodyTag`, `TryBlockTag`, or another undeclared family is required, the
affected implementation slice must stop and allocate a new explicit decision. It may not broaden
PLAT034 silently.

## Comparative Truffle audit

The approval followed an exhaustive review of the principal maintained Truffle implementations
plus materially relevant historical/experimental implementations, including Apple Pkl.

The scores below evaluate each implementation as a **precedent for PLAT034**, not as a score for
the language itself.

| Implementation | Relevant tooling profile | Future endurance | Scalability | Protos philosophy |
| --- | --- | ---: | ---: | ---: |
| Apple Pkl | narrow, need-driven custom expression tag | 9.5 | 10.0 | 10.0 |
| Espresso | Root / RootBody / Statement; no broad expression surface | 9.5 | 9.5 | 9.5 |
| GraalWasm | Root / RootBody / Statement; deliberately narrow | 9.5 | 10.0 | 9.5 |
| TruffleRuby | selective root/statement/variable/custom surface | 9.5 | 9.0 | 9.5 |
| Sulong / LLVM | selective Statement/Call/Root/RootBody plus real halt support | 9.5 | 9.5 | 9.5 |
| TruffleSqueak | selective standard/debugger surface; useful empty-membership precedent | 9.0 | 9.5 | 10.0 |
| FastR | standard core plus R-specific tags | 8.5 | 9.0 | 8.5 |
| TRegex | extremely narrow/root-oriented tooling surface | 9.0 | 10.0 | 8.5 |
| GraalJS | rich Expression/AlwaysHalt/variable/root tooling profile | 9.5 | 8.5 | 6.5 |
| GraalPy | rich Expression/AlwaysHalt/variable tooling profile | 9.5 | 8.5 | 6.5 |
| Enso | rich standard plus language-specific tooling tags | 9.0 | 8.5 | 7.0 |
| SimpleLanguage | broad demonstration-oriented standard/debugger profile | 8.5 | 8.0 | 6.0 |
| Yona | rich Expression/AlwaysHalt/Call/Statement/Root/variable profile | 5.5 | 7.0 | 6.0 |
| SOMns | broad research/instrumentation surface | 6.5 | 7.5 | 6.5 |
| TruffleSOM | Root/Statement/Expression plus custom node tag | 5.5 | 7.0 | 6.5 |
| grCUDA | effectively no generic ProvidedTags contract | 4.0 | 9.5 | 6.0 |

### Apple Pkl

Pkl is the strongest philosophical precedent for the selected rule. It does not advertise a broad
standard tag palette merely because Truffle defines one. Its expression instrumentation is
need-driven and uses a Pkl-specific expression tag because Pkl has a concrete value-tracking need.

For Protos the transferable lesson is:

```text
real tooling need
    -> truthful existing semantic category
    -> explicit tag membership
```

not:

```text
framework knows a tag
    -> guest language must advertise it everywhere
```

### Espresso and GraalWasm

Both reinforce that production Truffle languages can remain deliberately narrow. A debugger- or
runtime-capable language does not need to expose every standard tag category.

This is strong evidence against turning PLAT034 into a generic "provide all tool tags" policy.

### Sulong and TruffleSqueak

These implementations are important for `AlwaysHalt` reasoning. Where a real guest trap/debugger
operation exists, a halt tag can have actual membership. Conversely, declaration of a debugger
category does not require inventing guest halt semantics when the language has no matching
construct.

That supports Protos' explicit empty membership for `AlwaysHalt`.

### GraalJS, GraalPy, Enso and SimpleLanguage

These demonstrate that rich tag palettes can be correct and scalable when their language/tooling
ecosystems genuinely own the represented categories.

They are evidence that broad tagging is technically viable, but not evidence that broad tagging
is semantically appropriate for Protos. Copying their complete profile would convert framework
capability into language/tooling authority and would violate PLAT005's semantic-minimum rule.

### Yona, SOMns, TruffleSOM and grCUDA

These provide useful secondary/historical contrast. Their weight is lower because some are
archived/research-oriented or solve materially different tooling problems, but they reinforce the
absence of one universal Truffle tag profile.

## Candidate comparison

### Candidate A — broad rich-tooling profile

Provide Expression, AlwaysHalt, variable, root-body and adjacent tag families proactively.

- Future endurance: **7.5/10**
- Scalability: **7.5/10**
- Protos philosophy: **4/10**

Rejected because generic-tool vocabulary would become the authority for Protos tooling categories.
It would also pre-decide unresolved variable/binding semantics.

### Candidate B — declaration-only Expression and AlwaysHalt

Declare both requested categories but give both empty membership.

- Future endurance: **8.5/10**
- Scalability: **10/10**
- Protos philosophy: **7/10**

Rejected because empty `ExpressionTag` membership is unnecessarily weak: Protos already has a
truthful executable expression boundary in PLAT005.

### Candidate C′ — semantic-minimum compatibility

**Selected.**

- Future endurance: **10/10**
- Scalability: **10/10**
- Protos philosophy: **10/10**

`ExpressionTag` reuses exactly the existing statement boundary. `AlwaysHalt` is declared but has
zero locations because no matching guest construct exists.

### Candidate D — full expression instrumentation

Tag all or most canonical/Bytecode subexpressions.

- Future endurance: **9/10**
- Scalability: **8/10**
- Protos philosophy: **7/10**

Rejected because it introduces a new event-granularity institution, increases tooling cardinality,
and makes physical/lowering structure more likely to leak into user-facing tooling behavior.

### Candidate E — suppress/ignore unsupported tag requests in the Bytecode backend

- Future endurance: **4.5/10**
- Scalability: **9/10**
- Protos philosophy: **5/10**

Rejected because it works around the framework contract rather than publishing the truthful
categories Protos can support.

### Candidate F — fork or adapt Graal generic DAP/LSP tag behavior

- Future endurance: **3/10**
- Scalability: **5/10**
- Protos philosophy: **2/10**

Rejected due permanent tool fork/coupling and avoidable maintenance.

### Candidate G — separate tooling AST/backend

- Future endurance: **1/10**
- Scalability: **3/10**
- Protos philosophy: **1/10**

Rejected because it recreates a second execution/tooling truth after PERF006 deliberately converged
production execution on the C-prime Bytecode backend.

## Ratified invariants

```text
StatementTag membership unchanged                       YES
CallTag membership unchanged                            YES
RootTag membership unchanged                            YES

ExpressionTag provided                                  YES
ExpressionTag membership == StatementTag membership     YES
Expression-only additional execution points             NO

AlwaysHalt provided                                     YES
AlwaysHalt actual locations                             ZERO
invented debugger/trap guest semantics                  NO

ReadVariableTag provided                                NO
WriteVariableTag provided                               NO
RootBodyTag provided by PLAT034                         NO
TryBlockTag provided                                    NO

helper roots promoted                                   NO
backend physical operations used as semantic authority  NO
generic-tool requested tags auto-adopted                NO
Protos semantics changed                                NO
```

## Validation contract for consuming implementation

The consuming implementation slice must prove the architecture rather than merely compile it.

At minimum:

1. static/structural evidence that `ExpressionTag` is declared and generated only where the
   existing statement boundary is generated;
2. exact equality between observed statement and expression membership for representative
   production Bytecode roots;
3. zero observed `AlwaysHalt` locations;
4. unchanged `CallTag` and `RootTag` behavior;
5. real debugger/DAP attach, breakpoint, stepping, stack, scope/value and clean disconnect behavior
   remains valid;
6. the affected dynamic Graal-LSP path no longer fails because `ExpressionTag` is undeclared;
7. no hidden debugger stop or additional expression event appears;
8. ordinary non-tool execution semantics remain unchanged.

If validation exposes a different undeclared tag as the next real generic-tool blocker, stop the
affected slice and route that category through another explicit decision.

## Scalability rationale

PLAT034 adds no new source execution points. `ExpressionTag` aliases an already-retained compact
semantic classification at the same canonical sequence-child boundary, so event cardinality does
not grow with subexpression tree complexity.

`AlwaysHalt` adds no event location at all.

The architecture therefore scales with the number of meaningful sequence execution boundaries,
not with every parser node, Bytecode operation, runtime helper, actor/task transition, or optimizer
rewrite.

No global mutable tag registry, debugger synchronization, scheduler coupling, or per-Task/Actor
state is introduced. Tag projection remains local derived tooling metadata compatible with future
Bytecode DSL materialization/rewriting.

## Protos design fit

Candidate C′ preserves the project principles already established by PLAT005:

- **mechanisms over institutions** — reuse one existing execution boundary rather than invent an
  expression-debugger universe;
- **ordinary things remain ordinary** — a sequence child is already an executable expression;
- **no pets** — helper roots and implementation operations do not become privileged tooling
  entities;
- **one runtime truth** — production Bytecode execution and tooling observe the same canonical
  boundaries;
- **fail where the invariant is violated** — a genuinely new tag requirement returns through
  governance rather than being guessed or auto-adopted;
- **pay only for what is real** — no additional expression-stop cardinality and zero halt points;
- **scale by composition** — future truthful categories can be added independently without
  replacing the baseline.

## Relationship to PLAT005 and PLAT026

PLAT034 is a later resolution of the `ExpressionTag` part of PLAT005's deliberate deferral.

It does **not** supersede PLAT005. All PLAT005 source ownership, statement/call meaning,
wrapper/materialization, replay-independence and semantic-minimum rules remain in force.

PLAT026 remains authoritative for `RootTag` and helper-root exclusion. PLAT034 does not alter root
membership and does not authorize `RootBodyTag`.

## Regret scenario and escape path

The current need for empty `AlwaysHalt` declaration is driven by a real generic-tool/Bytecode-DSL
compatibility interaction. A future Truffle release may stop requesting/materializing a tag that a
language does not semantically use.

If that happens, Protos may remove the no-location `AlwaysHalt` declaration after bounded evidence
shows generic-tool compatibility no longer requires it. Because PLAT034 gives `AlwaysHalt` zero
membership, that escape path changes no guest semantics or tooling event locations.

The `ExpressionTag` projection is independently durable because it expresses a true relationship:
PLAT005 statement points are already executable expressions.

## Consumer release

Ratification releases the affected PERF006-C3 tooling-compatibility implementation from its
PLAT034 decision block.

The implementation may add only the selected tag declaration/projection and bounded conformance
coverage required by this decision. It may not use PLAT034 as authority for variable tags, finer
expression instrumentation, new debugger semantics, helper-root exposure, or a separate tooling
backend.

## Deliberately deferred

PLAT034 does not decide:

- read/write-variable tag semantics;
- whether Protos will ever expose a conventional variable-tag projection over bindings/contexts;
- root-body tagging;
- try/catch tag semantics;
- finer expression-level coverage;
- profiler-specific custom tags;
- a guest `debugger`/trap construct;
- static language-server architecture beyond already-ratified decisions;
- future Truffle upgrade behavior after generic-tool tag handling changes.
