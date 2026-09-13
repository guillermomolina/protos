# D124 — Static references identity, ambiguity and completeness contract

Status: **RATIFIED — Candidate B′ selected**

Allocated: **2026-09-13**

Explicit project-owner approval: **2026-09-13**

Decision issue: GitHub #491

Primary consumer: `LM009-H1` / GitHub #489

Primary predecessor: `D110` / GitHub #435

Nature: implementation-independent editor/tooling reference-resolution contract

Normative Protos language effect: **none**. D124 constrains only what the static
language service may claim through standard `textDocument/references`; Core lookup,
delegation, mutation, composition, import and runtime semantics remain owned by
`spec/`.

## Decision boundary

D110 already answers the forward navigation question:

> for this source occurrence, what complete finite set of exact source-backed
> binding origins can the static analysis prove?

That is sufficient for `textDocument/definition`, including an honest complete
multi-origin result.

`textDocument/references` asks a different inverse question:

> which exact binding identity is the query about, and which source occurrences
> may be reported as references to that identity?

D110 does not mechanically answer that question when a Protos occurrence can have
a complete proof-set containing more than one independent origin.

D124 therefore decides three tooling properties:

1. **seed identity** — what a cursor position must prove before a references query
   has one exact symbol identity;
2. **occurrence membership** — when a source occurrence is soundly reportable as
   referring to that identity; and
3. **completeness meaning** — whether absence from the returned list proves
   non-reference.

It does not define rename/refactoring safety.

## Selected contract — Candidate B′

Protos selects **exact singleton seed + sound monotonic may-reference relation,
with singleton/scope-first generation-1 coverage**.

### Seed identity

A standard references request succeeds only when the query position identifies
exactly one source-backed binding origin under the D110 proof relation.

If the seed has a complete proof-set:

```text
{A}
```

then `A` is the reference-search identity.

If the seed has:

```text
{A, B, ...}
```

or no complete exact proof, standard references returns no semantic result.
Several independent origins are never collapsed into one synthetic symbol merely
to make the LSP request succeed.

A user may navigate with D110 definition first, choose one exact origin, and issue
references from that origin.

### Occurrence membership

For exact target `A`, a source occurrence is reportable when its **complete
finite D110-compatible proof-set is known and contains `A`**.

Examples:

```text
occurrence proof-set {A}       -> include for A
occurrence proof-set {A, B}    -> include for A and include for B
occurrence proof-set {B}       -> exclude for A
unknown / incomplete proof     -> omit; never guess
```

A multi-origin occurrence is not claimed to be uniquely owned by `A`; it is
reported because the analysis has proved that `A` is one genuine semantic origin
the occurrence may select.

### Sound under-approximation

The ordinary references result is a **sound monotonic under-approximation**.

The invariant is:

```text
returned location
    = statically proven possible reference to the selected exact target

missing location
    = not proven
    != proof that the location cannot reference the target
```

D124 therefore guarantees **no guessed false positives**, but does not promise
that every possible runtime-selectable reference has been discovered.

As static analysis becomes stronger, newly proven references may be added without
changing the meaning of prior successful results.

### No heuristic fallback

None of these establishes references identity:

- same spelling;
- `workspace/symbol`;
- prefix/fuzzy/camel matching;
- source proximity;
- path/basename similarity;
- package/project popularity;
- open-document preference;
- receiver guesses;
- filesystem search;
- runtime execution or current Actor/Process state.

D106 workspace-symbol search remains exploratory name search, not semantic
identity.

### No synthetic multi-seed union

An ambiguous seed `{A,B}` does not authorize the server to return the union of
references to `A` and `B`.

That would manufacture one query identity from several independent binding
origins, and ordinary LSP clients have no standard way to communicate which hit
belongs to which origin.

### No arbitrary semantic-result cap

An implementation must not arbitrarily drop already-proven reference hits merely
to satisfy a fixed result cap while presenting the remainder as the full standard
result.

Generation 1 is naturally bounded to local exact proof domains. Future
project-scale implementations may use cancellation, incremental delivery,
candidate indexes, persistent indexes, sharding or remote indexes, but those are
storage/execution mechanisms and do not redefine reference identity.

## `includeDeclaration`

LSP `ReferenceContext.includeDeclaration` is obeyed exactly as presentation of the
selected exact origin:

- `true` may include the exact selected declaration/binding-origin location;
- `false` excludes that declaration from the returned locations.

It does not alter the semantic reference relation.

## Generation-1 implementation envelope

LM009-H1 generation 1 should deliberately begin with the proof domains already
cheap and exact under D110:

1. Closure-parameter seed/reference occurrences while the relevant facts remain
   valid;
2. singleton-proven match Binder/Alias seed/reference occurrences inside their
   exact arm scope;
3. exact current document snapshot and parse success;
4. canonical deterministic ordering and deduplication;
5. exact `includeDeclaration`;
6. no name/workspace-symbol fallback;
7. no receiver/member/delegation/super/import/composition expansion until those
   origins are independently proved;
8. no guest execution/live Truffle Context;
9. no new global mutable semantic registry.

Later exact lexical/local/module/member/composition coverage may be added
monotonically under the same D124 contract.

If implementation requires a new durable index/lifetime/process architecture not
already authorized by LM009-F/G, that implementation choice must stop at the
appropriate `PLATxxx` gate. D124 selects public tooling semantics only.

## Rename/refactoring separation

D124 explicitly does **not** authorize a rename implementation to treat the
ordinary references result as exhaustive.

Rename has a stronger safety problem:

```text
ordinary references
    every returned hit must be correct

rename/refactor
    may additionally need proof that every affected reference has been found
```

A future rename decision may require a closed-world or Candidate-C-like
completeness proof for the selected identity. D124 deliberately leaves that
stronger contract available.

## Prior-art basis

The audit compared materially different implementations and product policies.

### rust-analyzer

Rust Analyzer anchors find-references in semantic compiler/HIR identity and maps
results back through source/macro structure. It is strong evidence for
identity-first references and for keeping index/storage topology separate from
language identity.

### TypeScript / tsserver

TypeScript starts from compiler/checker symbol/declaration identity and constructs
referenced-symbol groups, including language-specific alias/merged-symbol rules.
Its transferable lesson is semantic identity before search, not its nominal type
assumptions.

### Pyright

Pyright resolves declarations for the seed and uses semantic collectors over the
relevant files. Its implementation strongly favors obtaining a correct semantic
answer rather than cutting the walk merely because the result grows. Its richer
type/declaration model is not imported into Protos.

### clangd

clangd combines semantic AST identity with scalable memory/background/on-disk/
remote index machinery. Practical reference caps and index incompleteness show
that large-scale product tooling must distinguish semantic identity from coverage
and storage policy.

### gopls

gopls uses Go type/AST identity and bounds reference search to the selected build
configuration rather than pretending to search every possible build universe.
This supports an explicitly bounded authoritative analysis domain.

### SourceKit-LSP / IndexStoreDB

SourceKit-LSP uses compiler-produced symbol occurrences plus replaceable
cross-file indexing. Local-reference evolution illustrates that coverage can
improve independently from the semantic identity contract.

### Eclipse JDT LS

JDT provides mature project/classpath semantic search and large-workspace
scalability. Its nominal declaration guarantees are useful as a scaling
counterexample but are stronger than Protos can assume.

### clojure-lsp

clojure-lsp uses its analysis database and qualified symbol identity instead of
blind workspace text matching, providing a dynamic-language precedent for
analysis-derived references.

### LuaLS

Lua language tooling demonstrates that a dynamic language can retain an
analysis-derived relation for references without turning fuzzy name search into
semantic identity.

### ElixirLS

Elixir tooling has expanded reference support incrementally across symbol
families as provenance improves. This is strong precedent for monotonic coverage
growth rather than an all-at-once global completeness requirement.

### Jedi

Jedi is important negative evidence: Python reference search can become too
complex, and the implementation may conservatively stop rather than fabricate
certainty. This supports omission over guessing in a dynamic language.

### Ruby LSP

Ruby LSP evolves references by symbol family; local variables require lexical
scope tracking while method/instance-variable precision depends on available
index/type information. It demonstrates that one broad same-name search should
not be promoted to semantic reference identity.

### Self

Self explicitly separates `References`, `Senders`, `Implementors` and `Find Slot`.
This is the closest philosophical precedent for Protos: broad selector/name
exploration is valuable, but it is not the same operation as exact reference
identity.

### Pharo / Smalltalk

Smalltalk environments similarly separate references from selector-oriented
senders/implementors browsing. That distinction strongly argues against weakening
standard references into a same-name message search.

### LSP

The standard protocol returns locations and exposes `includeDeclaration`, but
does not provide a standard confidence level, definite/possible marker,
multi-seed identity group, or completeness flag. Exact and heuristic hits would
therefore be observationally indistinguishable in ordinary clients.

## Prior-art fit

Scores are suitability as a D124 precedent, not overall tool quality.

| System | Aguante de futuro /10 | Escalabilidad /10 | Filosofía Protos /10 |
| --- | ---: | ---: | ---: |
| LSP result model | 10 | 10 | 10 |
| rust-analyzer | 10 | 10 | 9.5 |
| Pyright | 10 | 9.5 | 9.5 |
| TypeScript / tsserver | 9.5 | 9.5 | 8.5 |
| clangd | 9.5 | 10 | 8 |
| gopls | 9.5 | 9.5 | 8.5 |
| SourceKit-LSP | 9.5 | 9.5 | 8.5 |
| Eclipse JDT LS | 9 | 9.5 | 7.5 |
| clojure-lsp | 9 | 9 | 9 |
| LuaLS | 9 | 8.5 | 9 |
| ElixirLS | 9 | 8.5 | 9 |
| Jedi | 8.5 | 6.5 | 8.5 |
| Ruby LSP | 9 | 8.5 | 8 |
| Self | 8.5 | 6.5 | 10 |
| Pharo / Smalltalk | 8.5 | 7.5 | 9 |

## Candidate comparison

### A — exact singleton seed + definite-only references

Only occurrences with proof-set exactly `{target}` are returned.

Safe and simple, but it hides a real occurrence whose complete proof-set is
`{target, other}` even though the occurrence can genuinely select the target.

### B — exact singleton seed + sound proven may-reference subset

Seed must identify exactly one origin. Every occurrence whose complete proof-set
contains that origin is returned; unknown/incomplete occurrences are omitted.

This supplies the selected semantic core.

### B′ — B + singleton/scope-first implementation — SELECTED

B is the durable meaning. Generation 1 deliberately implements only cheap exact
scope/provenance forms and expands monotonically as analysis improves.

### C — globally complete reference set or no result

Require proof that every possible source occurrence that may select the target has
been found before returning anything.

Semantically strong, but one unrelated unresolved dynamic occurrence can erase
all otherwise useful results. It couples a local navigation query to
whole-project analysis/index completeness.

### D — complete multi-origin seed + union references

Treat seed `{A,B,...}` as a grouped identity and return the union.

Rejected because it creates a synthetic symbol from independent binding origins
and ordinary LSP clients cannot explain per-origin membership.

### E — selector/name/text search

Return same-name slots/messages, perhaps scope/project constrained.

Useful as a future `Senders`/`Implementors`-style exploratory facility; rejected
as standard references identity.

### F — exact references + heuristic fallback

Return exact hits plus plausible same-name/receiver/proximity candidates.

Rejected because ordinary LSP locations do not carry confidence semantics.

### G — runtime-assisted references

Use current guest/runtime state to establish identity.

Rejected because it violates the static/unexecuted-source baseline, describes
one runtime state and introduces Actor/Process lifecycle dependencies.

### H — defer references

Safe but unnecessary: D110 generation 1 already supplies useful exact lexical
origins.

## Mandatory GITHUB010 scorecard

Scores 1–5.

| Criterion | A | **B′** | C | D | E | F | G | H |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | 5 | **5** | 5 | 4 | 2 | 3 | 4 | 5 |
| Protos alignment | 5 | **5** | 4.5 | 3.5 | 2 | 2.5 | 1 | 4 |
| Future-option resilience | 4 | **5** | 5 | 4.5 | 3 | 4 | 2 | 3 |
| Scalability | 5 | **5** | 3 | 4 | 5 | 4 | 1 | 5 |
| Conceptual simplicity | 5 | **4.5** | 3.5 | 3 | 5 | 2.5 | 1.5 | 5 |
| Portability / implementation freedom | 5 | **5** | 5 | 5 | 5 | 4 | 1 | 5 |
| Runtime / resource cost | 5 | **4.5** | 3 | 4 | 5 | 3.5 | 1 | 5 |
| Failure / operability | 5 | **5** | 4 | 4 | 2.5 | 2.5 | 1 | 5 |
| Reversibility / migration cost | 4 | **5** | 5 | 3 | 2 | 2.5 | 2 | 5 |
| Evidence maturity / implementation risk | 4.5 | **5** | 4.5 | 4 | 5 | 4.5 | 3 | 5 |
| **Total / 50** | **47.5** | **49.0** | **42.5** | **39.0** | **36.5** | **33.0** | **17.5** | **47.0** |

## Focused project-owner scores

| Candidate | Aguante de futuro /10 | Escalabilidad /10 | Filosofía Protos /10 |
| --- | ---: | ---: | ---: |
| A | 8.5 | 10 | 10 |
| **B′** | **10** | **10** | **10** |
| C | 10 | 6.5 | 8.5 |
| D | 9 | 8.5 | 7 |
| E | 6 | 9.5 | 4 |
| F | 7.5 | 8 | 5 |
| G | 5 | 3 | 2 |
| H | 6 | 10 | 8 |

## Strongest argument against B′

Users commonly read “Find All References” as exhaustive. Under B′, a real
reference can be absent when Protos cannot prove it statically, and ordinary LSP
clients have no standard completeness indicator.

That is a genuine UX cost.

Candidate C avoids that cost only by making one unresolved dynamic occurrence
invalidate the entire answer. For Protos, B′ keeps the stronger invariant that
every returned hit is semantically justified while allowing useful exact
navigation to survive local uncertainty.

## Regret scenario and escape path

A future safe rename/refactoring engine may need proof that **every** affected
reference has been found.

The escape path is intentionally preserved: add a separate internal/query contract
that succeeds only when the analyzer proves a complete closed reference universe
for the selected target. That stronger mode can coexist with ordinary B′
references without reinterpreting any existing result.

Likewise, if users want broad selector exploration, add a separate Self/Pharo-like
`Senders` / candidate-usages feature rather than weakening standard references.

## Ratified invariants

```text
D124_SELECTED_CANDIDATE=B_PRIME
REFERENCE_SEED_IDENTITY=EXACT_SINGLETON_OR_NO_RESULT
REFERENCE_MEMBERSHIP=COMPLETE_PROOF_SET_CONTAINS_SELECTED_TARGET
REFERENCE_RESULT_SOUND=YES
REFERENCE_RESULT_GLOBALLY_COMPLETE=NO
MISSING_REFERENCE_MEANS_NON_REFERENCE=NO
AMBIGUOUS_SEED_UNION=NO
HEURISTIC_REFERENCE_FALLBACK=NO
WORKSPACE_SYMBOL_AS_REFERENCE_AUTHORITY=NO
RUNTIME_ASSISTED_REFERENCES=NO
ARBITRARY_PROVEN_RESULT_CAP=NO
GENERATION_1=SINGLETON_SCOPE_FIRST
COVERAGE_EVOLUTION=MONOTONIC
INCLUDE_DECLARATION=LSP_CONTEXT_EXACT
RENAME_COMPLETENESS_DECIDED=NO
SENDERS_IMPLEMENTORS_STYLE_SEARCH=SEPARATE_FUTURE_FEATURE
FUTURE_RESILIENCE=10/10
SCALABILITY=10/10
PROTOS_PHILOSOPHY=10/10
```

## Implementation consequence

After this governance record is published, `LM009-H1` is released to implement
the generation-1 exact references subset under Candidate B′.

D124 does not authorize H2 hover, H3 completion or H4 signature-help semantics,
and it does not itself implement or advertise `textDocument/references`.
