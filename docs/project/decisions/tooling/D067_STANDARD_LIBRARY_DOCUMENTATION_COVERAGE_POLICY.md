# D067 — Standard Library documentation coverage and API-reference publication policy

Status: **RATIFIED — Candidate D selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #326

Nature: durable implementation-independent documentation/tooling publication-policy decision

Triggered by: D061 / #311, D062 / #313, D064 / #317, D066 / #325,
TOOL003 / #322, and WEB001-J7B / #301.

Normative language effect: **none**. D067 does not add visibility, exports,
privacy, stability, runtime documentation objects, nominal types, or Standard
Library semantics.

## Decision

The canonical documentation artifact contains the complete mechanically
observable surface of every importable `std:` module and its mechanically
discoverable top-level slots, independently of whether authored documentation
is present.

Authored documentation remains a separate optional fact:

- `//!` supplies canonical module documentation under D062;
- `///` supplies canonical immediately-following top-level-symbol documentation
  under D062; and
- absence of either is represented as missing documentation, not as hidden,
  private, unsupported, unstable, deprecated, or otherwise semantically
  classified.

The public API-reference presentation MUST visibly distinguish:

1. **documented entries** — mechanical facts plus canonical authored prose; and
2. **undocumented observable entries** — mechanical facts only, explicitly
   labelled as lacking authored API documentation and carrying no inferred
   support, stability, compatibility, or semantic promise.

Missing documentation is initially a deterministic coverage signal, not a build
failure. A later explicitly approved policy may impose coverage thresholds
without changing symbol identity.

## Existing authority retained

D067 composes with, and does not reopen:

- D061: mechanical structure and authored semantic explanation are distinct;
- D062: `//!` / `///` are tooling-only authoring conventions and their absence
  does not imply private/unsupported status;
- D064: semantic symbol identity is distinct from exact occurrence/provenance;
- D066: implement only the minimum mechanism required by the current real
  WEB001-J7B consumer;
- `STANDARD_LIBRARY_NAMING.md`: canonical importable identity is
  `std:<logical-name> -> protos/lib/<logical-name>.protos`, while
  `protos/lib/core/**` is not an importable `std:core` namespace; and
- TOOL003-A: the already-published neutral model and deterministic JSON remain
  available for the bounded extractor.

## Prior-art evidence

The selected policy follows a recurring separation in mature ecosystems:

- **Rust/rustdoc**: public reachability is distinct from prose coverage;
  `missing_docs` diagnoses coverage while deliberate hiding is explicit.
- **Go**: exported declarations exist independently of doc comments; comment
  coverage is a quality convention rather than the source of export status.
- **Java/Javadoc**: inclusion follows language visibility, not documentation
  presence.
- **C# XML documentation**: publicly visible members remain members when
  comments are absent; missing coverage can be diagnosed separately.
- **Swift Symbol Graph + DocC**: compiler-known symbols and authored markup are
  separate inputs, allowing symbol inventory to remain complete while prose
  evolves.
- **Elixir/ExDoc**: documentation hiding is explicit (`@doc false` /
  `@moduledoc false`) rather than inferred from absent prose.

The lesson adopted by Protos is not any particular syntax. It is that
structural observability, documentation completeness, and intentional hiding
are separate dimensions.

## Candidate set

### A — flat mechanical publication

Publish every mechanical module/slot uniformly and make prose optional.

This is simple and complete, but risks visually equating an undocumented
helper-like slot with deliberately documented API.

### B — documentation-gated publication

Publish only modules/symbols carrying `//!` / `///`.

Rejected because comment absence would become an implicit publication/visibility
filter, contrary to D062.

### C — strict full-documentation gate

Treat the complete mechanical surface as the intended reference and fail
publication until every entry is documented.

Attractive for quality, but currently too globally blocking: the Standard
Library has not yet completed documentation rollout and Protos has no explicit
hide/internal mechanism.

### D — complete inventory + explicit missing-doc state

**Selected.**

Keep complete mechanical truth in the artifact and presentation, while
explicitly distinguishing documented entries from undocumented observable
entries. Missing docs are reported deterministically but do not initially fail
publication.

### E — explicit documentation-hide mechanism now

Add a new marker/sidecar mechanism analogous to `doc(hidden)` / `@doc false`.

Deferred because it would introduce another documentation institution before a
real recurring need has been demonstrated and could become a shadow visibility
system.

### F — separate publication manifest

Maintain an explicit list of which mechanical identities are published.

Rejected for now because it duplicates mechanically known identities, adds
staleness/coordination failure modes, and solves a distinction Protos does not
yet semantically possess.

## GITHUB010 comparative matrix

Scores use the common 1–5 scale. `H` = high confidence, `M` = medium confidence.
Totals are comparison aids only.

| Candidate | Correctness | Protos fit | Future | Scale | Simplicity | Portability | Resource cost | Operability | Reversibility | Evidence | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| A — flat mechanical publication | 4/H | 4/H | 4/H | 5/H | 5/H | 5/H | 5/H | 4/H | 4/H | 5/H | 45 |
| B — docs-gated publication | 3/H | 3/H | 3/H | 4/H | 5/H | 5/H | 5/H | 3/H | 3/H | 4/H | 38 |
| C — strict full-doc gate | 5/H | 4/M | 4/H | 3/M | 4/H | 5/H | 5/H | 2/H | 4/H | 5/H | 41 |
| **D — complete inventory + explicit missing-doc state** | **5/H** | **5/H** | **5/H** | **5/H** | **4/H** | **5/H** | **5/H** | **5/H** | **5/H** | **5/H** | **49** |
| E — explicit hide mechanism now | 4/M | 2/H | 4/H | 4/H | 2/H | 4/H | 4/H | 4/M | 3/M | 5/H | 36 |
| F — publication manifest | 4/H | 2/H | 4/H | 3/M | 2/H | 5/H | 4/H | 3/H | 3/M | 4/H | 34 |

Candidate D wins qualitatively because it preserves the actual current semantic
surface without inventing publication intent, hidden state, or a second identity
registry.

## Failure modes and counterexamples

### Accidental top-level helper

Today such a slot is observable because Protos has no distinct export/private
boundary for it. Candidate D shows that fact honestly as an undocumented
observable entry rather than silently hiding it or presenting polished prose.

If helper-like observable slots become a recurring practical problem, that is
evidence for a separate explicit hide/internal or language-visibility decision.

### Incomplete documentation rollout

The artifact remains deterministic and useful. Coverage remains visible rather
than preventing the entire reference from existing.

### Accidental documentation removal

The symbol remains present but transitions into the deterministic missing-doc
set. This makes the regression visible instead of making the symbol disappear
from reference.

### Mechanical presence mistaken for stability

Renderers must clearly state that undocumented mechanical presence carries no
inferred support, maturity, stability, compatibility, or semantic promise.
D061 already forbids that inference.

## Future-scenario stress test

### Larger Standard Library

The policy scales linearly with discovered modules/symbols. It requires no
central publication registry and permits coverage metrics to be calculated
directly from the artifact.

### Future package ecosystem

D067 selects the Standard Library coverage policy required by the immediate
consumer. It does not require the current implementation slice to generalize
package extraction. If later package documentation uses the D064 model, its
publication policy can be validated against its actual requirements.

### Future visibility/export semantics

If Protos later gains a real semantic visibility/export distinction, the
mechanical extractor can incorporate that authoritative fact. D067 does not
pre-empt such language design by treating comment presence as visibility today.

### Future explicit documentation hiding

A separately approved explicit exclusion mechanism can later narrow public
presentation. Because D067 keeps full truth now, migration remains deliberate
and auditable.

### Future release-gating coverage

A later policy may turn selected missing-doc classes from report/warning into
error without redefining module/symbol identity.

## Future-regret question

**What plausible future requirement would make us regret Candidate D?**

A Standard Library with many intentionally callable-but-not-user-facing helper
slots could make the complete observable reference noisy.

**Escape path:** introduce an explicit, separately approved documentation
exclusion mechanism or real language visibility rule. D067 deliberately leaves
that path open.

## Strongest argument against Candidate D

Candidate C offers a stronger documentation-quality guarantee: everything
visible in API reference is documented, and incomplete coverage prevents
publication.

That is attractive once the documentation corpus and exclusion semantics are
mature. It is not selected now because Protos currently has neither complete
Standard Library authored documentation nor a deliberate hide/internal
mechanism. Making documentation rollout a global publication blocker would add
coordination cost before evidence justifies it.

## Bounded implementation consequence

D067 releases the next post-D066 implementation slice with this exact scope:

1. retain documentation-comment occurrences without changing ordinary lexer
   token semantics;
2. traverse only canonical importable Standard Library `.protos` modules outside
   `protos/lib/core/**`;
3. derive canonical `std:` identity directly from the ratified physical mapping;
4. reuse the real parser to extract top-level bare slot identity and directly
   knowable Closure parameter/rest shape;
5. associate D062 `//!` / `///` documentation;
6. emit the existing TOOL003-A deterministic D064 JSON artifact, including
   undocumented entries with `documentation: null`;
7. emit deterministic coverage counts and missing-documentation identities; and
8. leave website rendering to the immediate downstream WEB001-J7B slice.

The implementation MUST NOT introduce a general package/CLI/IDE/search API,
runtime documentation state, new visibility semantics, stability vocabulary,
documentation-hide marker, doctest system, website-specific schema, or another
publication manifest.

## Closure

```text
D067                     RATIFIED — Candidate D
D061_D062_D064_D066      RATIFIED / unchanged
TOOL003_A                PUBLISHED / retained
TOOL003_MIN_EXTRACTOR    READY
WEB001_J7B               BLOCKED only until minimum extractor publication
```
