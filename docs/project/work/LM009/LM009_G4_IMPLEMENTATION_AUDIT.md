# LM009-G4 — Static go-to-definition generation-1 implementation audit

Status: **CLOSED — G4-A1 READY**

Date: **2026-09-12**

Parent: `LM009-G` / GitHub #360

Decision authority: `D110` / GitHub #435 — **RATIFIED, Candidate B′**

Nature: non-normative implementation audit and mechanical slice decomposition

Specification effect: **none**

Executable implementation in this audit: **none**

## Purpose

Translate the already-ratified D110 contract — complete finite static proof-set,
singleton-first — into the smallest sound generation-1 implementation surface.

This audit does not weaken D110, select new Protos semantics, or make
`workspace/symbol` a definition authority. It classifies which proof forms can be
implemented mechanically from the current parser/source/runtime semantics and
which forms must continue to return no result until stronger proof machinery is
published.

## Fixed authorities

The audit consumes these existing authorities without reopening them:

- `D110`: a successful definition response is a complete finite set of exact
  source-backed binding origins; incomplete proof means no result.
- `EXECUTION_AND_CONTROL.md`: bare lookup searches local slots of the current
  lexical context and then lexical parents before receiver fallback.
- `EXECUTION_AND_CONTROL.md`: execution contexts are ordinary mutable Protos
  objects; bare creation, assignment, explicit member mutation and `removeSlot`
  can change their local slot state.
- `CALLABLES.md`: Closure parameters are installed strictly left-to-right; a
  parameter becomes a local binding only after its value/default completes
  normally.
- `MATCHING.md` / D088 / D090 / D103: successful arm bindings become ordinary
  positional parameters of the arm activation; ordered OR alternatives share a
  logical binding interface but may obtain one logical binding from distinct
  source positions.
- D082/D085/D089/D102: project/source identity and exact canonical source custody
  come only from ProjectBinding authority.
- G1/G2/G3: the language server already has exact immutable open-document
  snapshots, parser/source spans, document symbols, canonical project binding
  and exact open-document overlay.

## Current implementation inventory

The current language server has no `textDocument/definition` implementation and
does not advertise `definitionProvider`.

The static-analysis core is already editor-neutral. The LSP layer delegates
parsing/symbol work to that core rather than maintaining a TypeScript or LSP-local
semantic model. G4 must preserve that boundary.

The current surface AST already carries enough source provenance for a useful
subset:

- `SurfaceName(name, span)` identifies a bare-name reference occurrence.
- `SurfaceParameter(name, ..., span)` retains the source construct that declares
  a Closure parameter.
- `SurfaceMatchPattern.Binder(name, span)` retains one binder origin.
- `SurfaceMatchPattern.Alias(name, pattern, span)` retains one alias origin.
- `SurfaceMatchPattern.CaptureInterface` retains declared names but only one
  aggregate interface `SourceSpan`, not one span per declared capture name.
- canonicalization preserves a bare `SurfaceName` as `CanonicalLookup(name,
  span)`; execution therefore does not provide a separate nominal-variable
  identity that an editor could reuse instead.

The production match lowering confirms that binders/aliases/capture interfaces
are materialized as ordinary arm parameters. That is useful proof evidence, but
the editor must still return the original source binding origin, not a synthetic
runtime parameter.

## Critical soundness finding — lexical syntax alone is insufficient

A source name being lexically inside a Closure does **not** by itself prove its
definition.

Execution contexts are ordinary mutable objects. Code may mutate or remove local
slots through ordinary Protos operations. Closures capture lexical contexts by
reference. Therefore a parameter or captured binding that was present earlier
can cease to be the selected bare-name origin before a later reference, and a
nearer context can acquire a same-name local slot.

Consequently generation 1 must never implement a Java-like "nearest syntactic
declaration wins" rule.

The safe generation-1 abstraction is a small intraprocedural set of
**exact-local-origin facts**:

```text
name -> exact source binding origin
```

A fact is usable only while the analysis can prove it still holds. Any operation
whose effects can structurally mutate the current activation context and whose
effects are not otherwise proven must invalidate the affected proof facts
(fail-closed barrier). The implementation may be deliberately conservative and
invalidate all current-context origin facts at such a barrier.

This is implementation machinery, not a durable architecture. D110 explicitly
allows proof computation to be replaced or strengthened later as long as every
successful result remains exact and complete.

## Generation-1 classification

### GEN1_EXACT — G4-A1 may prove

#### 1. Closure parameters in the same activation

A parameter origin is an exact local origin after that parameter has been
installed and while no proof-invalidating context-structure effect has occurred.

Parameter defaults require the normative left-to-right temporal rule:

- earlier successfully installed parameters may be exact while evaluating a
  later default, subject to the same effect barrier;
- the parameter currently being defaulted and every later parameter are not yet
  local bindings;
- after a parameter value/default completes normally, that parameter's own
  origin becomes exact;
- a default expression is potentially effectful. When the supplied/default
  alternatives cannot preserve an earlier fact on every normal path, that
  earlier fact is dropped rather than guessed.

The body begins from the joined facts that are true for every successful
parameter-binding path, not from a declaration-list approximation.

#### 2. Match Binder and Alias bindings in the same arm activation

After a pattern succeeds, its logical binding interface is installed as ordinary
parameters of the arm activation. A `Binder` or `Alias` with one proven source
origin may therefore become an exact local-origin fact for guard/body analysis.

The fact remains usable only until a proof-invalidating context-structure effect.
A guard is ordinary guest execution; G4-A1 must not assume that an arbitrary
guard preserves binder slots merely because the source name is still in lexical
scope.

#### 3. Singleton provenance through structural pattern composition

Array/Map/group/alias structure may be traversed to collect the source
provenance of each logical arm binding. G4-A1 accepts a logical binding only
when the complete source provenance set is a singleton and the current local
fact remains exact.

An alias outside an OR may therefore remain singleton even when its nested
pattern contains alternatives.

#### 4. References before an opaque-effect barrier

For eligible same-activation facts, the resolver follows actual Protos
left-to-right evaluation order. A reference may succeed when its origin is exact
at the instant that reference is evaluated, even if a later subexpression is
effectful.

The implementation is free to begin with a deliberately small set of
effect-preserving surface forms. Unknown forms invalidate proof; they never
trigger heuristic recovery.

### LATER_EXACT — sound in principle, deliberately deferred

#### Closure-captured outer lexical bindings

A nested activation may acquire/remove same-name local slots before a captured
outer binding is read. Proving an outer capture therefore requires facts across
the intervening activation and its effects, not merely a lexical parent pointer.

Generation 1 returns no result for such outer-capture references unless a later
bounded slice proves the complete intervening-context state.

#### Capture-interface names

`CaptureInterface` semantically declares exact logical bindings, but the current
surface carrier stores one aggregate span for the whole interface rather than a
source span per declared name.

G4-A1 does not fabricate token ranges or reparses text independently. A later
mechanical parser-carrier refinement may preserve per-name spans and release
these targets without reopening D110.

#### Ordered-OR branch-specific origins

D090 allows one logical arm binding to come from different structural positions
in different alternatives. That is a natural future D110 multi-location case:
the complete finite definition set is every proven alternative origin.

G4-A1 is singleton-first. When provenance contains more than one distinct source
origin it returns no result; a later multi-target slice may return the whole set
without changing the contract.

#### Bare local slot creation and assignment

D110 explicitly allows local `:` origins and `=` navigation when exact. The
language also permits structural mutation/removal of execution-context slots, so
faithful support requires a stronger intraprocedural state/effect proof than the
first parameter/binder slice needs.

These forms remain no-result in G4-A1 and may be added monotonically later.

#### Module-top-level local bindings

Module top level is a real lexical context, but its mutable slots, prelude parent,
execution order and import/cycle behavior require the same exact state proof plus
canonical project/module source custody. Defer.

### NO_RESULT_UNTIL_PROVEN — no generation-1 fallback

The following remain unsupported unless a later analyzer proves the complete
D110 set:

- receiver fallback through `this`;
- explicit member lookup and inherited/delegated members;
- `super` with dynamic `methodHome`;
- composition-contributed slots;
- dynamic or merely spelling-based imports;
- cyclic/partially initialized module-member cases;
- prelude/Core/stdlib/dependency/runtime-only targets without authorized exact
  source origins;
- duplicate same-name workspace symbols;
- any case requiring guest execution, runtime state, filesystem guessing,
  nearest-name selection, Top-N truncation or D106 ranking.

Reserved intrinsics such as `this`, `context` and `args` are not ordinary
source-backed binding declarations and therefore do not acquire a synthetic
definition target merely to satisfy LSP.

## G4-A1 implementation boundary

`LM009-G4-A1` is released as the first executable slice.

It should add an editor-neutral static-definition query over the existing
`ProtosDocumentSnapshot`/real parser model and focused unit tests for the exact
same-activation parameter/binder proof described above.

Required properties:

1. Input reference identity is the exact current source snapshot plus source
   offset/span; no workspace name search.
2. Output is an editor-neutral immutable source-origin result, not an LSP
   `Location`.
3. The proof engine understands parameter temporal installation and match-arm
   binding provenance.
4. It uses conservative effect barriers; unknown effect means loss of proof.
5. It returns either the complete proven singleton for G4-A1 or no result.
6. It performs no guest execution and creates no Truffle Context.
7. It does not advertise `definitionProvider` yet.
8. It does not implement cross-document/module/member/receiver navigation.
9. It does not change Protos semantics, parser grammar, D079 symbols or D106
   ranking.
10. Focused tests must include negative cases proving fail-closed behavior after
    an effect barrier and for branch-multiple OR provenance.

`LM009-G4-A2` is blocked only by A1 and may then wire the standard LSP
`textDocument/definition` request:

- advertise `definitionProvider`;
- translate exact LSP position to the existing source offset representation;
- require exact canonical ProjectBinding ownership for the request document;
- use the exact open-document overlay only for that canonical URI;
- translate the editor-neutral source origin to standard LSP `Location`;
- return empty/no result on stale snapshot, parse failure, loose/unowned source or
  unproven definition.

A2 adds no second resolver and does not query `workspace/symbol`.

## Deferred widening after A1/A2

Potential later bounded slices, ordered by increasing proof cost:

- per-name capture-interface source provenance;
- exact local `:` / `=` state flow;
- outer lexical-capture proof across intervening activations;
- complete finite multi-target OR provenance;
- module-top-level/cross-module exact bindings;
- statically proven receiver/member/delegation cases.

This order is not a semantic promise. It is only a cost-aware implementation
sequence. Any widening that exposes a new substantive semantic or durable
architecture choice stops at the normal Dxxx/PLATxxx gate.

## Audit conclusion

No new substantive decision is required before G4-A1.

The current source model and ratified language semantics are sufficient for a
small trustworthy first proof engine, provided it treats mutable execution
contexts as real dynamic state and fails closed at opaque effects.

Final classification:

```text
LM009-G4_IMPLEMENTATION_AUDIT = CLOSED
LM009-G4-A1                  = READY
LM009-G4-A2                  = BLOCKED_BY_G4_A1
D110_CHANGED                 = NO
SPECIFICATION_CHANGED        = NO
IMPLEMENTATION_CHANGED       = NO
IMPLEMENTATION_VERSION_CHANGED = NO
```
