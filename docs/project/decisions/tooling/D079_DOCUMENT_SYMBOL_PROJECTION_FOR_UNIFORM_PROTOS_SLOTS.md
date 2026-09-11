# D079 — Document-symbol projection for uniform Protos slots

Status: **RATIFIED — Candidate A′ selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #364

Triggered by: `LM009-G1` closure at
`993a3bc1648ce2dc4aa7663ee91723d7773c875d` / Protos `0.2.376-SNAPSHOT`.

Primary consumer: `LM009-G2` / GitHub #360

Nature: implementation-independent editor/tooling projection contract

Normative Protos language effect: **none**.

Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

## Decision boundary

LSP `DocumentSymbol` requires every exposed symbol to carry a `SymbolKind` and
permits hierarchical `children`, but Protos deliberately does not own the
mainstream declaration taxonomy that those presentation categories suggest.

The current Protos source model already gives G2 one exact source-level fact:
`SurfaceSlotCreation` represents explicit slot creation, with exact source spans
and a target that can be a bare name or a member target. Protos also already
separates slot creation (`:`) from assignment (`=`). It does **not** define a
parallel semantic distinction among global/local/property bindings, and a
Closure stored in a slot does not become a second value kind called Function or
Method merely because of the slot in which it is stored.

D079 therefore decides only how that existing source model is projected into an
editor outline. It does not add a declaration system, a type system, a callable
classification, module ownership, runtime object identity, or lookup semantics.

## Ratified principles

The following three principles are part of the selected contract.

### 1. Language-model authority

`DocumentSymbol` projects constructs that Protos already recognizes. LSP is a
presentation adapter and MUST NOT create semantic categories that Protos itself
does not own.

### 2. Slot identity over value shape

The symbol category of a `SurfaceSlotCreation` does not depend on the value
stored by that creation. In particular, a Closure-valued slot remains the same
slot symbol as a Number-, String-, Object-, Future- or other-valued slot.

G2 MUST NOT recategorize a slot as LSP `Function` or `Method` merely because its
value expression is a Closure.

### 3. Source-containment-only hierarchy

`DocumentSymbol.children` expresses only exact syntactic nesting of explicit slot
creations inside the value subtree of another slot creation.

That hierarchy MUST NOT be interpreted as or derived from runtime object
ownership, delegation, lookup ownership, receiver identity, module/package
ownership, lexical binding ownership, or live object containment.

## Ratified decision — Candidate A′

Every **explicit named `SurfaceSlotCreation`** is one baseline document symbol.

The exact projection is:

- bare `name: value` produces a symbol named `name`;
- member creation `receiver.name: value` also produces a symbol named `name`, the
  final slot-name component;
- `=` assignment never creates a document symbol;
- Closure parameters are not baseline document symbols because they are
  parameter syntax rather than `SurfaceSlotCreation` expressions;
- anonymous object, Closure and literal expressions are not assigned invented
  document-symbol names;
- every slot symbol uses LSP `SymbolKind.Property` strictly as the nearest
  portable **presentation label** for a named Protos slot;
- `Property` does not establish a Protos semantic Property category and does not
  distinguish a member slot from a context/local/global slot;
- the value being a Closure does not change the symbol kind to `Function` or
  `Method`;
- children are explicit named slot creations syntactically nested inside the
  value subtree of the parent slot creation;
- symbol `range` is the full `SurfaceSlotCreation` range;
- `selectionRange` is the exact final slot-name span;
- duplicate names remain distinct source occurrences and are not coalesced into
  a semantic index; and
- when the current source snapshot cannot be parsed into the required exact
  source structure, G2 returns no guessed/stale current symbol tree.

`SymbolKind.Property` is deliberately a compatibility label at the LSP boundary,
not a language declaration. If a future protocol offers a neutral slot kind, or
Protos later gains a genuinely distinct declaration category, that future choice
must cross the ordinary compatibility/design gate rather than being inferred
from this adapter spelling.

## Expanded comparative prior-art review

The approval review compared the actual document-outline implementations and
source models of materially different language/tooling families. The important
pattern is not that other tools converge on one LSP icon. They do not. The
relevant pattern is that mature tools normally project distinctions that their
own language/source model already possesses, and scalable tools avoid requiring
heavier semantic authority when local syntax is sufficient.

### Self — closest conceptual precedent

Self's programming environment and outliner are slot-centric: slots are the
primary structural unit presented to the programmer. Self also distinguishes
method, data, argument, parent and assignable-slot roles because those
categories belong to Self's own object model.

For Protos, Self strongly supports **slot as the outline unit** but does not
justify importing Self's method/data subcategories. Protos's Closure/method role
is intentionally different.

### TypeScript / tsserver / typescript-language-server

The TypeScript language server consumes tsserver's navigation tree and maps real
TypeScript `ScriptElementKind` values to LSP categories. Its adapter keeps
`property`, `field`, `var`/`local var`, `function` and `method` distinct because
TypeScript itself supplies those distinctions.

This is evidence against manufacturing the same taxonomy in Protos. The
language-owned navigation model comes first; LSP classification comes second.

### Python / Pyright

Pyright builds document symbols from its parsed/analyzed symbol and declaration
model. Recursive hierarchy follows real Python declaration categories such as
classes and functions rather than treating an arbitrary variable as a function
because of the current value shape.

The relevant precedent is again to project existing language authority rather
than infer editor-only declaration kinds.

### Ruby / Ruby LSP / Prism

Ruby LSP listens to real Prism AST constructs and maps `class`, `module`, `def`,
constants, instance variables and class variables to the corresponding LSP
presentation kinds. Its Method/Function/Field/Variable choices follow distinct
Ruby syntax/semantics.

This strongly supports using Protos's own AST as the source of truth and not
building a second editor parser or declaration taxonomy.

### Go / gopls

gopls builds document symbols directly from Go syntax declarations. Its symbol
infrastructure explicitly prefers syntax over type checking where syntax already
provides enough useful structure, avoiding the significantly higher cost of
unnecessary type analysis.

That is a particularly strong architectural precedent for G2: the Protos
Surface AST already determines explicit slot creation and exact containment, so
a workspace index or runtime semantic pass is not needed for the document
outline.

### Rust / rust-analyzer

rust-analyzer obtains file structure from the analysis layer and only then
translates its language-owned `SymbolKind` representation to the LSP protocol.
This keeps the protocol adapter downstream of the language model rather than
making LSP kinds authoritative.

### Java / Eclipse JDT LS

JDT LS can expose rich Type/Field/Method hierarchy because Java and JDT already
own those element categories. This is strong evidence for faithful projection in
Java but weak evidence for imposing Java-like declaration categories on a
uniform-slot prototype language.

### Lua / LuaLS — strongest counterexample

LuaLS deliberately performs richer presentation inference: it may classify an
assigned value as Boolean, String, Number, Object/Array, Function or Method, and
it also exposes anonymous values/control blocks as outline structure. It then
builds hierarchy using value ranges.

This demonstrates that Candidate C is technically feasible and visually richer.
It also exposes its cost: the outline starts describing value shape/editor
presentation rather than only a stable declaration category. Protos rejects that
trade-off for the baseline because Closure-valued slots are still ordinary slots
and `method` is an invocation role rather than a distinct stored value kind.

### Clojure LSP / EDN

Clojure LSP provides a useful natural comparison between code and data. For
ordinary Clojure source it projects analyzed definitions. For EDN data it can
classify entries by value shape such as String, Number, Array or Struct and build
structural hierarchy.

This reinforces the distinction relevant to Protos: value-shape classification
is appropriate for a data-document view, while a source-code outline should
prefer the language's own source constructs.

### OCaml / ocaml-lsp

OCaml tooling likewise preserves hierarchical document symbols and maps
language-level constructs through the LSP boundary. The useful precedent is the
same separation between language structure and protocol presentation, not the
particular nominal categories OCaml owns.

## Candidate set

### A′ — uniform slot creation + `Property` + syntactic hierarchy

**Selected.**

Use the exact Protos slot-creation fact, one uniform LSP presentation kind, and
source-containment-only hierarchy.

### A″ — uniform slot creation + `Variable` + syntactic hierarchy

Mechanically as simple and scalable as A′. Rejected because `Variable` carries a
stronger local-storage/binding connotation and encourages users/tools to read a
false distinction between context variables and object slots. `Property` is the
less misleading portable presentation label for a named slot, provided the
adapter-only status is explicit.

### B — uniform slots, flat list

Semantically safe but discards exact source nesting that is already available.
Large files become unnecessarily difficult to navigate and the lost hierarchy
would have to be reconstructed later despite having no semantic ambiguity when
defined strictly as source containment.

### C — context/value-sensitive Variable/Property/Function/Method taxonomy

Familiar and visually rich, with LuaLS demonstrating feasibility. Rejected
because it would make source context or initializer shape create declaration
categories that Protos deliberately does not own. It creates compatibility debt
for little semantic value.

### D — object/public/structural slots only

Could reduce outline noise, but there is no current Protos semantic authority for
an editor-only public/structural importance threshold. Rejected as a new
institution rather than a projection of the source model.

### E — defer until a complete semantic/workspace index exists

Maximally conservative but unnecessary. The real parser already determines the
facts G2 needs. Deferral would couple an O(document) source-outline feature to
workspace/module identity work owned by later slices.

## Required GITHUB010 scorecard

Scores are 1–5. Arithmetic is supporting evidence, not decision authority.
Confidence is **HIGH** except D, whose editor-importance boundary is
**MEDIUM-HIGH**.

| Criterion | A′ Property + hierarchy | A″ Variable + hierarchy | B flat uniform | C contextual/value-sensitive | D structural/public only | E defer |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | **5** | **5** | **5** | 2 | 3 | **5** |
| Protos alignment | **5** | 4 | **5** | 1 | 3 | 4 |
| Future-option resilience | **5** | **5** | 4 | 2 | 3 | **5** |
| Scalability | **5** | **5** | 3 | 4 | **5** | **5** |
| Conceptual simplicity | **5** | **5** | **5** | 2 | 3 | **5** |
| Portability / implementation freedom | **5** | **5** | **5** | 4 | **5** | **5** |
| Runtime / resource cost | **5** | **5** | **5** | 4 | **5** | **5** |
| Failure / operability | **5** | **5** | **5** | 3 | 4 | **5** |
| Reversibility / migration cost | 4 | 4 | **5** | 2 | 3 | **5** |
| Evidence maturity / implementation risk | **5** | 4 | 4 | **5** | 4 | 3 |
| **Total / 50** | **49** | **47** | **46** | **29** | **38** | **47** |

### Score rationale and confidence

- **A′ — HIGH:** uses only exact parser/source facts, stays O(AST) per request,
  needs no resolver/index/runtime authority, and introduces one explicitly
  presentation-only LSP label.
- **A″ — HIGH:** same mechanics as A′, but `Variable` is a less faithful UI word
  for a language where contexts and objects expose the same slot mechanism.
- **B — HIGH:** extremely safe and reversible but knowingly throws away exact
  nesting and degrades navigation as a file grows.
- **C — HIGH:** rich precedent exists, particularly LuaLS, but only by accepting
  value/context-sensitive editor classification. In Protos that would be a false
  semantic cue and expensive compatibility habit to unwind.
- **D — MEDIUM-HIGH:** cheap to execute but requires a new policy for deciding
  which ordinary slots are important enough to appear.
- **E — HIGH:** safest against premature semantic inference, but blocks useful
  exact behavior on infrastructure the feature demonstrably does not require.

## Focused owner-requested long-term scorecard

The expanded follow-up audit also scored the candidates directly on the three
axes requested by the project owner. Scores are 0–10.

| Candidate | Future endurance | Scalability | Protos philosophy | Total / 30 |
| --- | ---: | ---: | ---: | ---: |
| **A′ slot + Property + syntactic hierarchy** | **10** | **10** | **10** | **30** |
| A″ slot + Variable + hierarchy | 10 | 10 | 8 | 28 |
| B flat uniform slots | 9 | 6 | 10 | 25 |
| C contextual/value-sensitive taxonomy | 5 | 8 | 2 | 15 |
| D structural/public-only | 7 | 9 | 5 | 21 |
| E defer for semantic index | 10 | 10 | 8 | 28* |

`E` has a practical veto despite its high arithmetic score: it does not deliver
G2 and waits for authority the current parser already proves unnecessary.

## Future-scenario stress test

### Large source files / many slots

A′ requires one traversal of the already parsed document structure plus result
allocation: O(AST) time and O(S) returned symbol storage for S explicit slot
creations. It creates no workspace-wide index merely to serve a document-local
outline.

### Many open documents / clients

The decision adds no global registry. Each request can operate from the existing
LM009-F client-session immutable snapshot authority, so per-document state and
client lifetime remain unchanged.

### Concurrent edits / cancellation

G2 may capture the same immutable snapshot discipline already used by the static
service. A result that is stale at the existing freshness boundary is not
published. D079 adds no Task/Actor/Process semantics and no runtime cancellation
institution.

### Workspace/package evolution

A′ needs neither canonical module identity nor cross-file filesystem inference.
G3 workspace symbols and G4 definition identity can therefore evolve using the
proper module/package authorities without retroactively changing the G2 outline
contract.

### Bytecode DSL / non-Truffle backend

The decision depends on Surface AST/source spans, not Truffle execution nodes or
runtime values. Backend replacement does not affect the contract.

### Richer future callable/declaration model

If Protos later introduces a genuinely distinct source declaration or callable
category, the adapter may expose a new LSP kind for that new language-owned
construct after the appropriate compatibility/design decision. D079 does not
pre-allocate that category by guessing from today's Closure-valued slots.

### Distributed / multiprocess execution

Runtime distribution is orthogonal. No live Process/Actor/object enumeration or
runtime ownership is consulted to build the source outline.

### Alternative editors / future LSP replacement

The durable fact remains “explicit slot creation with source-containment
hierarchy.” `SymbolKind.Property` is deliberately an adapter detail. A future
editor protocol can project the same Protos facts without inheriting a false
language-level Property institution.

## Strongest argument against A′

Uniform `Property` icons are visually less rich than TypeScript, Ruby, Java or
Lua outlines. Users may reasonably want callable-looking slots to stand out.

That usability argument is real, but it is not sufficient to let an editor infer
new Protos declaration categories from initializer shape. Richer presentation
can later use non-semantic detail text, semantic tokens, or a genuinely
language-owned future distinction without changing slot identity.

## Regret trigger and escape path

**Regret trigger:** Protos introduces first-class source declaration categories
that are genuinely distinct from slots, or broad user evidence shows that the
uniform adapter kind materially harms navigation.

**Escape path:** keep explicit slot creations and exact syntactic hierarchy as
the stable source facts. Add new LSP kinds only for genuinely new constructs; or
change the presentation kind at a compatibility-visible decision gate. This is
materially cheaper than undoing a false Function/Method/Variable taxonomy after
editor clients and users have come to depend on it.

## Intentionally deferred

D079 does **not** decide:

- workspace-symbol inclusion, search semantics, index lifetime or index
  authority (`LM009-G3`);
- go-to-definition identity/resolution (`LM009-G4`);
- references, rename, completion, hover or signature semantics (`LM009-H` or
  later);
- semantic tokens or icon decoration beyond the baseline LSP kind;
- runtime/live-object browsing;
- module/package symbol ownership;
- any future Protos syntax for genuinely new declaration categories; or
- a general public semantic-index API.

## LM009-G2 release boundary

Once this ratification is published to `main`, `LM009-G2` is released to
implement exactly the selected document-symbol projection over the current real
parser/source-snapshot authorities.

G2 may implement the bounded traversal, LSP response mapping and focused tests
needed for this contract. It MUST stop again if implementation exposes a new
substantive semantic or durable architecture choice instead of inferring an
answer from editor convention.

G2 MUST NOT in the same slice select G3 workspace-index policy, G4 definition
identity, LM009-H semantics, runtime/live-object browsing, or a new Protos
declaration taxonomy.

## Approval record

The project owner explicitly approved **Candidate A′** on 2026-09-11 after the
initial GITHUB010 decision packet and an expanded follow-up audit covering Self,
TypeScript/tsserver, Pyright, Ruby LSP/Prism, Go/gopls, rust-analyzer,
Eclipse JDT LS, LuaLS, Clojure LSP/EDN and OCaml-LSP, with focused scoring for
future endurance, scalability and Protos philosophy.

This ratification records that approval. It contains no LM009-G2 executable
implementation, no specification change and no implementation-version change.

## Change classification

`VALIDATION_CLASS=GOVERNANCE_DOCUMENTATION_ONLY`

This publication changes durable tooling/editor-governance records and releases
the already-approved G2 implementation boundary. It changes no Protos language
semantics, specification, runtime/native implementation, public language API,
Maven implementation version, package format or executable language-server
behavior.
