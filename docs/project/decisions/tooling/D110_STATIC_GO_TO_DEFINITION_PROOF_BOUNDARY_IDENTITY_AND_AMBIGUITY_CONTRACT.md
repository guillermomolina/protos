# D110 — Static go-to-definition proof boundary, identity and ambiguity contract

Status: **RATIFIED — Candidate B′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #435

Primary consumer: `LM009-G4` / GitHub #360

Predecessors: `D079`, `D082`, `D085`, `D089`, `D102`, `D106` — RATIFIED; `LM009-G3` — CLOSED

Nature: implementation-independent editor/tooling definition-resolution contract

Normative Protos language effect: **none**. D110 constrains only what the static language service may claim as a source definition; Core lookup, delegation, assignment, composition and module semantics remain owned by `spec/`.

Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

## Decision boundary

LM009-G3 publishes exact project/source custody and workspace-symbol search, but a workspace symbol with the same spelling is not proof of a Protos definition.

Core lookup is deliberately dynamic and uniform:

- a bare read searches local slots of the current lexical context and its lexical parents before receiver fallback;
- object bodies are not lexical capture scopes;
- receiver fallback and explicit member reads use ordinary delegation;
- bare assignment selects an existing local destination under rules different from reads;
- composition can contribute local slots at runtime while direct declarations only reserve names against composition;
- `super` depends on dynamic `methodHome`;
- `import(specifier)` returns the real mutable module context;
- cyclic imports may expose partially initialized modules; and
- duplicate same-name source slot creations are valid independent occurrences.

D110 therefore answers one tooling question only:

> When may the static language service claim that a source reference has one or more source definitions?

## Selected contract — Candidate B′

Protos selects **complete finite static proof-set, singleton-first**.

A standard `textDocument/definition` response is successful only when the static analysis can prove a complete finite set of exact source-backed binding origins for the reference under the authoritative current source/project snapshot.

The baseline contract is:

1. Every returned target is an exact source binding origin that can semantically satisfy the reference under current Protos rules and the facts proven by the analysis.
2. The returned set must be **complete** for the analysis claim. A known subset is not a definition result if another runtime-selectable source origin may have been omitted.
3. A proven singleton returns one definition location.
4. A proven complete set with several targets may return all targets through the standard LSP multi-location result.
5. Multiple proven targets have equal semantic status. Ordering is deterministic canonical ordering only; it is not relevance ranking.
6. No definition-result cap may truncate a proven set. If an implementation cannot prove or return the complete set within its supported analysis/resource envelope, it returns no definition rather than a partial semantic claim.
7. Failure to prove a definition is an ordinary **no-result** outcome, not an Error and not permission to guess.
8. Generation 1 is deliberately **singleton-first / mechanically exact**. It may implement only cheap proof forms and decline harder references. Later analysis may monotonically add newly provable singleton or complete finite multi-target cases without changing D110.
9. D106 `workspace/symbol` is an exploratory/name-search facility and is never a fallback definition authority. Fuzzy, same-name, proximity, path, package, popularity, open-document, registration-order or other ranking heuristics do not establish definition identity.
10. Static definition requests execute no guest code, create no live Truffle Context, inspect no Actor/Process runtime state and perform no runtime-assisted dispatch.
11. Project/source identity comes from the already-ratified canonical ProjectBinding/package/source authorities. Filesystem ancestry, recursive scan, basename or editor-workspace guessing is forbidden.
12. The exact current open-document overlay may replace the canonical file content only for the exact canonical URI already owned by the project binding. Stale/current-snapshot mismatch or parse failure produces no definition.
13. The definition universe is **not limited to D079 document/workspace symbols**. Exact lexical binding origins such as Closure parameters and match binders/aliases/capture-interface bindings may be valid definition targets even though D079 intentionally does not publish them as document/workspace symbols.
14. A `:` slot creation is a binding origin. A `=` assignment is not a new definition; when its destination is statically provable, navigation targets the existing selected binding origin under the exact Protos assignment rules.
15. Bare-name references may resolve only when the analysis proves the complete result under the exact lexical-chain-before-`this` lookup order. A potential unresolved receiver fallback makes the reference unproven.
16. Explicit member reads, receiver fallback, delegation and `super` are supported only when the effective receiver/lookup origin and complete selected source-binding set are statically proven. Unknown receiver, dynamic parent/delegation state or unknown `methodHome` yields no definition.
17. Composition provenance may be used only when the complete effective contribution is statically proven and maps to exact explicit source origins. Dynamic/unknown composition does not authorize same-name guessing.
18. Import/module navigation may consume only an existing canonical static package/module resolution authority. Literal spelling by itself is not a filesystem path rule. Module-member proof must respect actual module identity, source ownership, initialization order and cyclic/partially initialized module semantics.
19. Prelude/Core/Standard-Library/dependency/runtime-only targets without an authorized exact source origin yield no source definition in this baseline. Expanding source authority is a separate decision, not an implicit G4 side effect.
20. Independent projects are never joined by same-name search. Multi-project definition identity remains partitioned by exact project/source authority.
21. Proof computation/index/storage is replaceable. Incremental, persistent, sharded or remote machinery may implement D110 as long as successful results preserve completeness and exact identity.
22. A future user-facing “possible implementors”, “candidate definitions” or heuristic-navigation feature may be added separately. It must not silently weaken `textDocument/definition`.

## Why complete proof rather than best effort

The most important separation is:

```text
workspace/symbol
    relaxed exploratory name search
    D106 ranking + global response cap

textDocument/definition
    semantic source-identity proof
    complete set or no result
```

This mirrors the useful distinction visible in dynamic object environments such as Self and Smalltalk: selector/name exploration is valuable, but it is not equivalent to the slot/method selected by actual lookup.

A wrong definition result creates more architectural debt than a missing one. Later references, rename and refactoring facilities need stable identity. D110 makes successful definition results trustworthy enough to become a future input to those tools without making those later features part of this decision.

## Generation-1 implementation envelope

D110 does not require G4 generation 1 to solve every finite static ambiguity.

The first implementation should preferentially support forms whose ownership is mechanical and exact, including where the existing source model can prove them:

- Closure-parameter references;
- match binder/alias/capture references within their exact binding scope;
- lexical/local slot reads whose selected earlier source binding is structurally proven under the real lexical-context rules;
- assignments whose existing destination binding is structurally proven;
- module-top-level local bindings where the same proof is exact; and
- other exact forms discovered during the implementation audit that require no new semantic or durable architectural decision.

Generation 1 should initially decline, unless existing authority proves them completely:

- unresolved receiver fallback through `this`;
- explicit member/inherited lookup with unknown receiver or delegation state;
- `super` with unproven effective lookup origin;
- composition-contributed bindings with dynamic/partial provenance;
- dynamic imports or guessed module paths;
- module-member cases whose result depends on unproven cyclic/partial initialization state;
- prelude/std/dependency source targets without current source authority; and
- any path-dependent/runtime-created binding space for which the static result set is incomplete.

This is an implementation coverage boundary, not a permanent semantic prohibition. Stronger future analysis can expand the successful proof domain without reopening D110.

## Prior-art audit

The D110 audit deliberately covered prototype/message-oriented systems, dynamic languages, gradually/static analyzers and nominal/static counterexamples.

### Self

Self is the closest conceptual precedent. Slot/message lookup is rooted in actual receiver/parent structure and the environment separately offers broad selector/slot exploration. The lesson for Protos is that selector/name search is not definition identity when delegation state matters.

### Pharo / Smalltalk

Smalltalk environments distinguish runtime method lookup from broad “implementors of selector” browsing. This reinforces a separate exploratory candidate-navigation feature instead of weakening exact definition semantics.

### TypeScript / tsserver

TypeScript resolves navigation through compiler symbol/declaration identity and exposes an array-capable definition API. It demonstrates that multiple locations are compatible with strong semantic identity, but its nominal/static assumptions do not transfer wholesale to Protos.

### Python — Pyright and Jedi

Pyright generally anchors navigation in analyzer declarations and does not turn workspace symbol search into a blind fallback when dynamic framework typing is unavailable. Jedi explicitly permits multiple `goto()` results because Python can be dynamic. Together they are strong evidence for honest multi-target results without requiring heuristic incompleteness.

### Ruby — Ruby LSP / RubyIndexer, Solargraph, Steep

Ruby LSP provides the strongest counter-policy: when receiver inference is unavailable it can return a bounded set of same-name method candidates. This is useful UX, but the set is intentionally heuristic/incomplete and therefore unsuitable as Protos baseline definition identity. Solargraph and Steep show how richer maps/types can increase precision, at the cost of additional analysis/type authority.

### LuaLS

LuaLS obtains definition candidates from its VM/static-analysis relation (`getDefs`) and can return several deterministic locations. It demonstrates that a dynamic language can expose a result set from semantic analysis rather than only nominal declarations.

### ElixirSense

ElixirSense uses cursor environment, variable/version metadata and module/function introspection, returning an exact location or no result. Its bounded redirection from `use`-injected behavior back to an explicit `__using__` definition is useful evidence for provenance-aware navigation instead of same-name guessing.

### clojure-lsp

clojure-lsp consumes its analysis database and emits a definition only when the analysis relation identifies one. Unsupported interop/analysis surfaces remain partial rather than being promoted to guessed definition identity.

### Erlang ELP

ELP is a strong scaling precedent: an incremental semantic database supports IDE navigation without making query-time global text search the semantic authority.

### rust-analyzer

rust-analyzer is the strongest static semantic precedent. `goto_definition` classifies identifiers through semantic HIR, descends macro expansions, maps exact definitions back to source and may produce a vector of navigation targets. Multiple targets therefore do not imply heuristic identity.

### clangd

clangd demonstrates excellent project-scale indexing and semantic navigation. Some declaration/best-known-location fallbacks are useful C/C++ product choices, but D110 deliberately does not import a weaker “best known” identity into Protos.

### SourceKit-LSP

SourceKit-LSP combines compiler/sourcekitd semantics with index/build information and demonstrates that project-scale source navigation can remain separate from storage/index topology. “Best known” fallbacks remain weaker than D110's proof contract.

### Eclipse JDT LS

JDT's mature Java model demonstrates semantic/project/classpath authority and large-workspace scalability. Its nominal declaration model is a counterexample to assuming that Protos has comparable statically unique member identity.

### LSP protocol

The protocol permits one location, multiple locations/location links, or no result. It does not require a language server to collapse semantic ambiguity to one guess. D110 uses that flexibility directly and adds no protocol extension.

## Prior-art suitability for D110

Scores measure suitability as a D110 precedent, not overall tool quality.

| System | Future endurance /10 | Scalability /10 | Protos philosophy /10 |
| --- | ---: | ---: | ---: |
| LSP result flexibility | **10** | **10** | **10** |
| rust-analyzer | **10** | **10** | 9.5 |
| Erlang ELP | **10** | **10** | 9 |
| Pyright | 9.5 | 9.5 | 9.5 |
| ElixirSense | 9 | 8.5 | 9.5 |
| clojure-lsp | 9 | 9 | 9 |
| TypeScript / tsserver | 9.5 | 9.5 | 8 |
| Jedi | 8.5 | 7 | 8 |
| LuaLS | 8.5 | 8.5 | 7.5 |
| Ruby LSP | 8.5 | 8.5 | 5.5 |
| clangd | 9.5 | **10** | 8 |
| SourceKit-LSP | 9.5 | 9.5 | 7.5 |
| Self environment | 8 | 6 | **10** |
| Pharo / Smalltalk | 8 | 7 | 8.5 |
| Erlang LS fuzzy-definition precedent | 6.5 | 7.5 | 4.5 |

## Candidate set

### A — unique-proof-only, fail closed

Return only one exact definition; ambiguity always yields no result.

Strongly safe and simple, but unnecessarily discards honest finite ambiguity that LSP can represent and future static analysis may prove.

### B — complete finite proof set

Return every exact possible source definition when the complete finite set is proven.

Semantically strong but underspecified about how a first implementation avoids overcommitting to expensive whole-program proof.

### B′ — complete finite proof set, singleton-first — SELECTED

Use B as the durable meaning and F's narrow exact coverage as an implementation strategy. Successful results remain exact/complete; generation 1 can deliberately decline hard cases and expand monotonically later.

### C — best-effort language-server heuristic

Return plausible same-name/proximity/receiver candidates despite incomplete semantic proof.

Rejected because it turns useful exploratory hints into definition identity.

### D — exact result plus heuristic fallback

Mix exact and heuristic truth levels in the standard definition request.

Rejected because common LSP clients cannot reliably communicate that distinction to users, and the fallback would become compatibility behavior.

### E — runtime-assisted definition

Execute/evaluate enough guest state to discover current runtime lookup.

Rejected for the static baseline: it depends on Actor/process/runtime state, violates no-guest requirements, changes lifecycle/cost and would describe one runtime state rather than source semantics generally.

### F — narrow mechanically exact subset as the permanent contract

Excellent generation-1 strategy and highly Protos-aligned, but too restrictive as a durable contract. It would require a new decision merely to add a later complete multi-target proof.

### G — defer G4

Semantically safe but unnecessary. Exact useful source bindings already exist, and LM009 requires useful S5 navigation rather than universal dispatch resolution.

## Mandatory GITHUB010 scorecard

Scores are 1–5; confidence is HIGH except where a candidate's future inference cost is inherently workload-dependent.

| Criterion | A | B | **B′** | C | D | E | F | G |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | 5 | 5 | **5** | 2.5 | 3.5 | 4 | 5 | 5 |
| Protos alignment | 5 | 5 | **5** | 2 | 2.5 | 1 | 5 | 5 |
| Future-option resilience | 4 | 5 | **5** | 3.5 | 4 | 2 | 3.5 | 3 |
| Scalability | 5 | 4 | **5** | 4 | 3.5 | 1.5 | 5 | 5 |
| Conceptual simplicity | 5 | 3.5 | **4.5** | 4 | 2.5 | 1.5 | 5 | 5 |
| Portability / implementation freedom | 5 | 5 | **5** | 4 | 3 | 1 | 5 | 5 |
| Runtime / resource cost | 5 | 4 | **5** | 4 | 3 | 1 | 5 | 5 |
| Failure / operability | 5 | 5 | **5** | 2 | 2.5 | 1 | 5 | 5 |
| Reversibility / migration cost | 4 | 5 | **5** | 2 | 3 | 2 | 4 | 5 |
| Evidence maturity / implementation risk | 5 | 4.5 | **5** | 4.5 | 3.5 | 3 | 4.5 | 4 |
| **Total / 50** | **48** | **46** | **49.5** | **32.5** | **30.5** | **18** | **47** | **47** |

Arithmetic is advisory. G scores well because doing nothing is safe, but it delivers no G4/S5 navigation. F scores well because it is an excellent first implementation, but it unnecessarily freezes today's analysis coverage as tomorrow's semantic contract.

### Focused project-owner scores

| Candidate | Aguante de futuro /10 | Escalabilidad /10 | Filosofía Protos /10 |
| --- | ---: | ---: | ---: |
| A | 8.5 | **10** | **10** |
| B | **10** | 8.5 | **10** |
| **B′** | **10** | **10** | **10** |
| C | 6.5 | 8 | 4 |
| D | 7.5 | 7 | 4.5 |
| E | 4 | 3 | 2 |
| F | 7.5 | **10** | **10** |
| G | 5 | **10** | 9 |

## Scalability and future stress

### Large repositories

Definition does not scan all same-name workspace symbols. Proof can stay source/scope/project-local and later consume incremental indexes. Unsupported expensive cases fail closed. No Top-N semantic truncation is allowed.

### Many projects / remote analysis

Canonical ProjectBinding/source identity remains the partition key. A persistent, sharded or remote proof engine can preserve the same response contract.

### Concurrent edits

The exact immutable open-document snapshot discipline remains authoritative. A stale parse/proof is discarded rather than published.

### Dynamic objects, Tasks, Actors and Processes

No live object graph, Actor, Task, Process or Context is consulted. One runtime state cannot silently become editor semantic authority.

### Richer future analysis

Flow analysis, abstract interpretation, provenance tracking or future language-owned type information may prove more references. B′ admits that growth without changing successful-result meaning.

### Generated/virtual/dependency/stdlib sources

D110 does not fabricate source custody. New exact source authorities may be introduced by later approved decisions and then consumed by the same proof contract.

## Strongest argument against B′

B′ can initially feel conservative in a prototype/delegation language. Finding one likely same-name method is much easier than proving that no other receiver/delegation/composition target can apply. Ruby LSP deliberately favors useful fallback candidates in such situations.

That is a real UX trade-off. Protos nevertheless chooses trustworthiness for the standard definition operation: a missing definition is visible and recoverable through workspace symbol search, whereas a wrong definition silently teaches users and later tooling a false identity relation.

## Regret trigger and escape path

**Regret trigger:** real user evidence shows that the exact-only definition surface is too sparse for practical navigation even after reasonable static analysis improvements.

**Escape path:** add a separate candidate/implementor navigation command or explicit future protocol surface that is allowed to be heuristic. Do not reinterpret existing successful `textDocument/definition` results as guesses. D106 workspace-symbol search already supplies one exploratory path.

## Intentionally deferred

D110 does not decide:

- references, rename or find-all-usages;
- completion, hover or signature help;
- a type system or declaration taxonomy;
- a public implementors/candidate-navigation feature;
- dependency/stdlib/generated-source custody;
- persistent/remote index representation;
- live runtime/debugger-assisted navigation;
- exact generation-1 proof coverage beyond the principle that it must be mechanically sound and complete for every result it returns.

## Implementation consequence

After D110 publication, `LM009-G4` is `READY_FOR_IMPLEMENTATION_AUDIT`.

That audit may choose mechanically necessary internal representations and decompose generation-1 exact proof coverage. If it exposes a new substantive semantic or durable architecture choice, G4 must stop at the normal Dxxx/PLATxxx approval gate.
