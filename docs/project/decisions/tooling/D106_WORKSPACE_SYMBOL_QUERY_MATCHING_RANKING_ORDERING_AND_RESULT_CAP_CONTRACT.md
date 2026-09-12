# D106 — Workspace-symbol query matching, ranking, ordering and result-cap contract

Status: **RATIFIED — Candidate C′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #425

Primary consumer: `LM009-G3` / GitHub #360

Predecessors: `D079`, `D082`, `D085`, `D089`, `D102` — RATIFIED; `LM009-G3P` — CLOSED

Nature: implementation-independent editor/tooling query contract

Normative core-language effect: **none**. This decision defines only baseline `workspace/symbol` search behavior over the already-authorized D079/D082 symbol set.

Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

## Decision boundary

D079 defines the indexed declaration unit: every explicit named `SurfaceSlotCreation`
is represented uniformly as an LSP `Property`. D082 defines the project/index authority:
per-project incremental index domains consume canonical `ProjectBinding`, exact open
buffers overlay canonical project sources, loose documents do not create workspace
identity, session-level queries aggregate all exact project domains, dependencies and
`std:` are outside the baseline, and index construction remains lazy/pay-for-use.
D085/D089/D102/G3P provide the exact project/source authority needed to instantiate that
architecture.

One public behavior remained intentionally undecided: how an LSP
`workspace/symbol` query makes symbols eligible, how eligible results are ranked and
totally ordered, and what response bound applies. D106 fixes that baseline contract
without selecting a concrete index data structure.

## Selected contract — Candidate C′

Protos selects **portable relaxed ordered-subsequence matching with transparent bounded
ranking**.

The baseline contract is:

1. The server matches only the D079 **symbol name**. Project, package, module, source
   path, container text and editor workspace-folder spelling do not participate in
   baseline eligibility.
2. An empty query returns **no results**. Baseline Protos does not use an empty standard
   `workspace/symbol` request as an unbounded symbol-enumeration API.
3. For a non-empty query, both query and candidate name are normalized for comparison
   only using Unicode NFC plus locale-independent Unicode Default Case Folding. The
   original source spelling is preserved in returned LSP items.
4. A candidate is eligible iff the folded query code points occur in order as a
   subsequence of the folded candidate-name code points.
5. Exact, prefix and contiguous-substring relationships are **ranking classes**, not
   stricter eligibility gates. Eligible candidates are ranked, best first, as:

   ```text
   folded exact
       > folded prefix
       > folded contiguous substring
       > other folded ordered subsequence
   ```

6. No baseline ranking weight is assigned for camel-case boundaries, `_`, punctuation,
   package/module/project proximity, source path, declaration kind, container name,
   edit distance, typo correction, open-document origin or workspace registration
   order.
7. Within one ranking class, a relation that also holds under the original NFC spelling
   without case folding sorts before an otherwise equivalent folded-only relation.
8. Remaining ties sort by shorter original symbol-name code-point length, then by one
   total deterministic canonical identity key. The baseline tie key is, in order:
   original symbol-name UTF-8 bytes, canonical project identity/root, PackageId,
   logicalModule, source selection start, and source selection end. Its purpose is only
   determinism; these fields do not improve semantic relevance.
9. D079 duplicate declarations remain distinct results. Equal names are not collapsed.
10. The open-document overlay changes the authoritative current source contents only.
    Being open or unsaved provides no relevance bonus.
11. Session aggregation ranks one combined candidate stream across all exact D082
    project domains, then applies one **global hard cap of 100 results**. The cap is not
    100 per project/root/package.
12. Truncation at 100 is a response/resource envelope, not index/source membership and
    not proof that no further matches exist. Baseline D106 adds no public matcher/cap
    configuration and no new protocol extension.
13. Matching and storage machinery remains replaceable. A linear scan, Top-K heap,
    FST, trigram index, Bloom prefilter, persistent index, sharded index or remote index
    may implement the same contract without changing D106.
14. Any future change that makes naming separators, package/module paths, proximity,
    symbol kinds, history or user configuration semantically affect eligibility or
    ranking is a new public tooling decision and must cross the ordinary Dxxx approval
    gate.

## Empty-query refinement and superseded exploratory text

The first exploratory recommendation comment on GitHub #425 proposed making every
baseline symbol eligible for an empty query, following the LSP allowance that clients
may use `""` to request all symbols.

The later exhaustive audit compared actual mature-server behavior and revised Candidate
C′ before project-owner approval. gopls, Pyright, JDT LS and ZLS all demonstrate that a
server may legitimately decline blank/too-broad workspace-symbol queries for resource
and UX reasons; SourceKit-LSP goes further and rejects patterns shorter than three
characters. The project-owner approval was given after this revised audit.

Therefore the ratified rule is unambiguous:

```text
query == ""  ->  []
query != ""  ->  ordinary C′ relaxed matching, including one- and two-code-point queries
```

The earlier `empty query makes every indexed baseline symbol eligible` statement in the
issue discussion is exploratory and superseded by this ratification.

## Why subsequence is eligibility but not a rich fuzzy institution

LSP 3.18/3.19 recommends interpreting the query in a relaxed way: case-insensitive
ordered-character matching is the stated rule of thumb, and prefix/substring should not
be used as strict eligibility requirements because editors may perform additional
highlighting/scoring.

Candidate C′ follows that interoperability guidance while deliberately keeping ranking
small. The server can answer normal abbreviations such as:

```text
ctr -> counter
lmn -> longMethodName
```

without establishing a language-specific fuzzy-scoring institution.

The four ranking classes are mechanically explainable and stable. They do not infer
semantic importance from package ownership, filesystem position, declaration kind or
host/editor state.

## Unicode and language identity

Protos source identifiers are Unicode XID and source spelling is NFC/case-sensitive.
D106 does not change identifier equality. `Foo` and `foo` remain distinct Protos names.

Case folding exists only in the editor search projection:

```text
language identity:     case-sensitive original NFC spelling
workspace search:      locale-independent folded comparison view
returned LSP spelling: original source spelling
```

Host default locale must not influence search. ASCII-only lowercasing, underscore
elision or camel-case semantics are not the D106 contract.

## Determinism and duplicate identity

Search result order must not depend on `HashMap` iteration, filesystem enumeration,
thread scheduling, project registration order, concurrent index refresh order or which
project happens to answer first.

The canonical tie key is deliberately relevance-neutral. It exists only to make the
order total after the approved relevance dimensions are exhausted.

Independent declarations that share a name continue to be distinct because D079/D082
identity and source ownership remain authoritative. D106 never performs name-only
semantic deduplication.

## Result cap and aggregation

The baseline result budget is exactly **100** after ranking the combined session result
space.

The conceptual operation is:

```text
for every exact D082 project domain
    produce eligible current-overlay-aware candidates
aggregate candidates across the session
rank by the D106 total order
return first min(100, matchCount)
```

An implementation need not materialize or sort every match. Maintaining a bounded
Top-K structure is permitted and preferred when it preserves the exact final order.

The cap is intentionally not configurable in generation 1. Configuration would create
another public institution before Protos has evidence that multiple policies are needed.
A later Dxxx may raise/configure the cap or add partial-result/local-search mechanisms
without changing D079/D082 project/source identity.

## Prior-art audit

The audit compared actual workspace/navigation-symbol behavior rather than merely
whether a server advertises the LSP method.

### Language Server Protocol 3.18/3.19

The protocol itself provides the strongest interoperability guidance: interpret query
relaxedly, prefer case-insensitive ordered-character matching, and avoid prefix or
substring as strict matching requirements. It also permits an empty query to request all
symbols, but does not require every server to make such a request cheap or useful.

**Contribution:** authoritative evidence for relaxed ordered-subsequence eligibility and
for leaving additional UI scoring to clients.

### clangd / LLVM

clangd uses a dedicated `FuzzyMatcher`, word/camel segmentation, quality/relevance
signals, a Top-N collector and index APIs that also support background/remote indexes.
The normal server result limit defaults to 100.

**Contribution:** strongest precedent that bounded top-candidate selection scales from
local to remote indexes. Its C/C++-specific scoring rules are deliberately not copied.

### rust-analyzer

rust-analyzer defaults workspace symbol queries to case-insensitive fuzzy matching where
query characters occur in order. Its symbol index uses FST machinery and incremental
per-file/per-library construction. Its documented workspace-symbol result limit defaults
to 128.

**Contribution:** strongest direct precedent for separating portable subsequence
semantics from replaceable scalable index machinery.

### gopls

gopls defaults interactive workspace symbols to `FastFuzzy`, uses a linear Unicode-aware
matcher with explicit scoring for segments/words/package qualification, and retains at
most 100 workspace-symbol results. Its implementation explicitly describes its scorer as
heuristic rather than globally optimal.

**Contribution:** evidence for Unicode-aware bounded Top-K search; negative evidence
against making language/package-specific score factors baseline Protos semantics.

### Pyright

Pyright workspace symbols use an `isPatternInSymbol` relation whose essential rule is
that typed characters appear in order. It searches user code, does no search for an
empty query, and carries tests for non-trivial Unicode lowercasing behavior.

**Contribution:** particularly strong precedent for a small, comprehensible relaxed
matcher without an elaborate relevance institution.

### SourceKit-LSP

Current SourceKit-LSP performs unqualified workspace-symbol matching through
case-insensitive subsequence index queries and has tests such as `lmn` matching
`longMethodName`. It rejects patterns shorter than three characters to contain cost and
noise, while newer qualified-query support adds Swift-specific container semantics.

**Contribution:** strong subsequence/index evidence and a useful negative comparison.
Protos keeps one-/two-code-point queries valid and uses Top-100 rather than a minimum
pattern length; Swift container-query semantics are not imported.

### Metals / Scala

Metals uses fuzzy matching plus Bloom-filter preselection. Short queries use prefix
search, and lowercase input may generate capitalization hypotheses to fit Scala naming
conventions.

**Contribution:** mature evidence that prefilters can scale fuzzy search. The
capitalization/naming heuristics demonstrate exactly why storage optimization and public
query semantics should remain separate.

### Eclipse JDT LS / Java search

JDT LS builds on Eclipse's mature indexed Java-search machinery, including camel-case
and pattern matching and optional result bounds; blank workspace-symbol queries are not
used as an all-symbol enumeration path.

**Contribution:** evidence for long-term indexed scalability and result budgets, but its
Java type/member/camel taxonomy does not fit D079's uniform slot model.

### ZLS

ZLS maintains per-file trigram stores, uses a Cuckoo-filter precheck and posting-list
intersections, and rejects empty workspace-symbol queries. Its trigram normalization is
ASCII-oriented and treats `_` specially.

**Contribution:** excellent storage/scalability precedent; negative evidence against
allowing one concrete prefilter representation to define Protos Unicode/search
semantics.

### Ruby LSP / RubyIndexer

Ruby LSP describes workspace symbol as fuzzy declaration search. RubyIndexer currently
uses Jaro-Winkler similarity with a 0.7 threshold over normalized qualified names and
sorts by similarity.

**Contribution:** credible proof that statistical fuzzy ranking can produce useful UX;
negative evidence for Protos baseline because an opaque numeric threshold would become
public observable policy without language-semantic grounding.

### Haskell Language Server / HieDb

HLS can answer workspace symbol queries from persistent HIE database state rather than
requiring every query to reparse the entire source universe.

**Contribution:** evidence that persistence can arrive later behind stable query/source
identity. Its exact matching policy is less informative for D106 than its storage
architecture.

### OCaml-LSP

Current OCaml-LSP workspace symbols enumerate build artifacts and apply a comparatively
simple query filter over symbol names; an empty query may return the complete discovered
set.

**Contribution:** useful conservative baseline and negative scaling evidence for
query-time broad enumeration in large workspaces.

### TypeScript / tsserver NavigateTo

TypeScript's navigation stack has historically used match-kind/pattern metadata and
bounded result budgets, with project membership established by ProjectService before
navigation search. Current implementation migration toward native tooling makes exact
source details less stable than the other precedents.

**Contribution:** mature evidence for separating project authority, match quality and
response budgeting; not used as the sole basis for the Protos contract.

## Prior-art suitability for this decision

Scores below measure suitability as a D106 precedent, not overall tool quality.

| System | Future endurance /10 | Scalability /10 | Protos philosophy /10 |
| --- | ---: | ---: | ---: |
| LSP 3.18/3.19 guidance | **10** | 8 | **10** |
| rust-analyzer | **10** | **10** | 9 |
| clangd | **10** | **10** | 8 |
| gopls | 9.5 | **10** | 7.5 |
| Pyright | 9 | 8 | **9.5** |
| SourceKit-LSP | 9 | 9 | 8 |
| Metals | 9 | **10** | 6.5 |
| Eclipse JDT LS | 8.5 | 9.5 | 6 |
| ZLS | 8.5 | 9.5 | 6.5 |
| Ruby LSP | 8 | 7 | 6.5 |
| HLS / HieDb | 8 | 9 | 7 |
| TypeScript / tsserver | 8.5 | 9 | 7 |
| OCaml-LSP | 5.5 | 3.5 | 5.5 |

The common architecture is more important than any individual score:

```text
portable eligibility contract
        +
small observable ranking contract
        +
response budget
        |
        v
replaceable candidate/index machinery
```

## Candidate set

### A — case-sensitive substring, canonical order, unbounded

Simple status-quo-style search. Rejected because strict substring eligibility conflicts
with current LSP guidance, normal abbreviations fail, and unbounded responses scale
poorly.

### B — case-insensitive exact/prefix/substring tiers with a bound

A credible conservative alternative. Rejected as sole eligibility because it still
excludes legitimate relaxed ordered-character queries that mature servers and LSP
expect.

### C — rich fuzzy scorer

Use camel/word boundaries, separators, qualification, project/package/path proximity,
possibly typo/edit-distance semantics, and a response bound, similar in spirit to
clangd/gopls/Metals.

Rejected for baseline despite strong UX/scaling precedent. Those signals encode
language/ecosystem naming conventions and would create a new independent relevance
institution before Protos has real usage evidence.

### C′ — portable relaxed subsequence + transparent bounded ranking — SELECTED

Use Unicode locale-independent relaxed ordered-subsequence eligibility, only four simple
relationship tiers, no semantic/path/editor weighting, a deterministic total tie order
and one global Top-100 response envelope.

### D — exact/prefix only

Rejected as too restrictive for standard workspace symbol UX and contrary to the
protocol's relaxed-query guidance.

### E — ignore query and return all symbols

Delegates filtering entirely to clients. Rejected for standard baseline because it
wastes CPU/transport, scales poorly and discards the server's query parameter. A future
name-list/local-search extension remains possible.

### F — configurable matcher and cap

Technically flexible and supported by mature precedent such as gopls and rust-analyzer.
Rejected for generation 1 because configuration is an institution and compatibility
surface; Protos has no evidence yet that multiple search contracts are required.

### G — defer G3

Semantically safe but no longer justified after G3P closure and mature prior art. It
would keep an otherwise implementable LM009 capability blocked without reducing a hard
semantic risk.

## Mandatory GITHUB010 scorecard

Scores are 1–5. Confidence is `HIGH` except where explicitly noted.

| Criterion | A | B | C | **C′** | D | E | F | G |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | 2.5 | 3.0 | 5.0 | **5.0** | 2.0 | 3.0 | 4.5 | 5.0 |
| Protos alignment | 3.5 | 4.0 | 3.0 | **5.0** | 3.5 | 3.5 | 2.5 | 2.0 |
| Future-option resilience | 3.0 | 3.5 | 4.0 | **5.0** | 2.5 | 4.0 | **5.0** | 2.0 |
| Scalability | 2.5 | **5.0** | **5.0** | **5.0** | **5.0** | 1.5 | **5.0** | **5.0** |
| Conceptual simplicity | **5.0** | 4.5 | 2.5 | **4.5** | **5.0** | **5.0** | 2.0 | **5.0** |
| Portability / implementation freedom | **5.0** | **5.0** | 4.0 | **5.0** | **5.0** | 4.5 | 4.5 | **5.0** |
| Runtime / resource cost | 3.0 | **5.0** | 4.0 | **5.0** | **5.0** | 1.5 | 4.5 | **5.0** |
| Failure / operability | 3.0 | 4.0 | 3.5 | **4.5** | 3.0 | 3.0 | 3.5 | 2.0 |
| Reversibility / migration cost | 4.5 | 4.0 | 3.5 | **4.5** | 4.0 | 4.5 | **5.0** | **5.0** |
| Evidence maturity / implementation risk | 3.0 | 4.0 | **5.0** | **5.0** | 3.5 | 3.5 | **5.0** | **5.0** |
| **Total / 50** | **35.0** | **42.0** | **39.5** | **48.5** | **38.5** | **34.0** | **41.5** | **41.0** |

### Score justification and confidence

**A — HIGH.** Cheap and obvious but fails ordinary abbreviation UX, conflicts with the
protocol's relaxed-query guidance, and becomes transport-heavy when unbounded. It is easy
to migrate away from because it has little structure.

**B — HIGH.** Bounded exact/prefix/substring is fast, predictable and portable, but
leaves interoperability/UX value on the table because its eligibility remains stricter
than the mature-server norm.

**C — HIGH for implementation/scaling; MEDIUM for relevance quality.** Mature systems
prove sophisticated scorers work, but their exact coefficients and naming signals are
ecosystem-specific. Adopting them would be technically feasible while making future
behavior harder to explain and stabilize.

**C′ — HIGH overall; MEDIUM only for user-preference details and full Unicode folding
edge cases.** It follows the protocol's portable matcher, bounds resources, preserves
all index/backend choices and has a small observable ranking surface. Exact Unicode
case-fold implementation deserves dedicated tests, but it does not require language
semantic change.

**D — HIGH.** Extremely cheap and scalable, but intentionally poor workspace-search UX
for abbreviation-heavy symbol navigation.

**E — HIGH.** Semantically neutral and reversible, but unbounded enumeration/transport
is the wrong default scaling model for a server-side query API.

**F — HIGH.** Maximum future flexibility and good mature precedent, but public
configuration itself becomes long-lived compatibility surface and is premature for the
first Protos users.

**G — HIGH.** Operationally cheapest and fully reversible, but has zero delivered G3
utility and no longer protects an unresolved hard invariant.

## Project-owner three-axis comparison

| Candidate | Future endurance /10 | Scalability /10 | Protos philosophy /10 |
| --- | ---: | ---: | ---: |
| A | 6 | 5 | 7 |
| B | 7 | 10 | 8 |
| C | 8 | 9.5 | 6 |
| **C′** | **10** | **10** | **10** |
| D | 5 | 10 | 6 |
| E | 7 | 2.5 | 6.5 |
| F | 9 | 9 | 5.5 |
| G | 10 | 10 | 8 |

## Future-scenario stress test

### Very large monorepo

C′ does not require sorting/materializing every match. Any implementation may maintain a
Top-100 structure while scanning/prefiltering candidates. Persistent, sharded or remote
indexes remain compatible because eligibility/ranking do not depend on local traversal
order.

### Many independent ProjectBindings

The cap is session-global, so adding roots does not multiply the response budget. The
canonical tie key prevents project-registration order from becoming observable ranking.

### Open unsaved buffers

D082 overlay semantics replace stale indexed content before matching. D106 does not
privilege the fact that the source is open, so editing state cannot become relevance.

### Persistent / remote symbol index

A backend may precompute folded names, posting lists, FST states or trigram filters. It
must reproduce C′ results but need not preserve the in-memory representation.

### Alternative editor/client

C′ follows standard LSP relaxed-query expectations and does not depend on VS Code fuzzy
scoring. Clients may still apply presentation highlighting/scoring to the bounded result
set.

### Unicode evolution

Search comparison is intentionally distinct from language identifier equality. If the
project later updates its Unicode data version, the tooling can update its case-fold
data together with the supported language/toolchain while returned source spelling and
identity remain unchanged.

### Dependencies and Standard Library

If D082 later adds immutable dependency/`std:` index layers, D106 can rank the larger
candidate set unchanged. Adding scope weighting would be a separate decision; baseline
C′ has none.

### Distributed/multi-process server

Project-local indexes can return candidates carrying the canonical D082 identity key to
a session aggregator. The aggregator can merge Top-K streams into the same deterministic
Top-100 order without sharing a mutable global index.

## Strongest argument against C′

The hard Top-100 response bound can silently omit a valid result the user wanted. Unlike
completion, ordinary `workspace/symbol` has no standard `isIncomplete` bit telling the
client that refinement is necessary. In a huge workspace, a sophisticated clangd/gopls
scorer might also place intuitive matches more accurately than four transparent tiers.

This is a real cost, not dismissed as theoretical.

The countervailing evidence is mature: gopls and clangd normally use 100, while
rust-analyzer defaults to 128. A bounded answer prevents a broad single-character query
from becoming an accidental whole-index transfer, and modern editors commonly reissue
the query as users type more text.

## Regret scenario

We would regret C′ if real large Protos workspaces show one or more of these behaviors:

- users routinely need results beyond rank 100 and their clients do not naturally refine
  queries;
- project/package qualification becomes essential for disambiguating very large symbol
  universes;
- four-tier subsequence ranking makes abbreviations feel materially worse than a richer
  scorer;
- client-local search over an efficiently transferred name index proves both faster and
  more useful than repeated server requests; or
- a remote index requires protocol-level pagination/continuation rather than silent
  truncation.

## Escape path

None of those scenarios require changing D079 symbol identity, D082 ProjectBinding/index
partitioning, D102 package ownership or G3 source membership.

A later approved Dxxx may independently:

- raise or configure the cap;
- introduce LSP partial-result streaming or a Protos extension with continuation;
- add qualified/package-aware query syntax;
- adopt richer scoring over the same eligible candidate universe;
- expose a SourceKit-like sorted symbol-name list for client-local search; or
- change persistent/remote index data structures.

The migration remains localized to the query/result layer.

## Out of scope

D106 does not decide:

- ProjectBinding/project discovery/source ownership;
- D079 declaration taxonomy;
- dependency or Standard Library scope;
- persistent/remote index representation;
- workspace-symbol resolve payload beyond baseline needs;
- G4 go-to-definition identity/resolution;
- references, rename, completion, hover or signature-help semantics;
- VS Code-only UI behavior; or
- general Protos identifier equality/case semantics.

## Downstream effect

Ratification releases LM009-G3 implementation to build the already-approved D082
per-project incremental workspace-symbol index and exact open-document overlay, aggregate
across exact ProjectBindings, apply D106 C′ matching/ranking/Top-100, and publish the
standard LSP `workspace/symbol` handler.

If implementation exposes a new semantic or architectural choice outside this exact
contract, G3 must stop at the ordinary approval gate rather than extending D106 locally.

No production implementation is included in D106 ratification.
