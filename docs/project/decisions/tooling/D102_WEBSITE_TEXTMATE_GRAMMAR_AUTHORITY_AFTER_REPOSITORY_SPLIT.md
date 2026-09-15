# D102 — Website TextMate grammar authority after VS Code repository split

## Decision checkpoint

**Formal identifier:** `D102`

**Triggered by:** `WEB006 / guillermomolina/protos-website#2` after the VS Code extension moved from `guillermomolina/protos/editors/vscode` to `guillermomolina/protos-vscode-extension`.

**Decision state:** `NEEDS_USER_DECISION`

**Blocks:** WEB006 authority cleanup and website syntax-highlighting source-contract changes.

## Exact question

After the VS Code extension became an independent repository, where should the public website obtain the reusable non-normative Protos TextMate grammar used for source rendering/syntax highlighting?

The previous website contract consumed:

```text
protos/editors/vscode/syntaxes/protos.tmLanguage.json
```

That path no longer exists because the extension now owns the VS Code product and its grammar in:

```text
protos-vscode-extension/syntaxes/protos.tmLanguage.json
```

The decision must preserve the authority separation already established by WEB001/D066 and must not silently create a second grammar authority.

## Fixed boundaries

- `guillermomolina/protos/spec/` remains authoritative for language semantics.
- `guillermomolina/protos` remains canonical for implementation-independent language documentation and maintained language sources it owns.
- `guillermomolina/protos-vscode-extension` owns the VS Code extension product, including its non-normative TextMate grammar asset.
- `protos-website` is a derived public presentation layer and must not maintain an independently drifting grammar fork.
- The grammar is editor/presentation metadata, not normative language semantics.
- Exact-revision provenance remains required for reproducible website builds whenever the grammar is consumed as a source input.

## Candidate models

### A — Website consumes the grammar from `protos-vscode-extension` at an exact locked revision

Extend the website source-lock model so it can identify both:

- the exact canonical Protos source revision used for language/specification/documentation inputs; and
- the exact `protos-vscode-extension` revision used for the non-normative TextMate grammar.

The website materializer reads the grammar from that exact extension revision and never modifies or maintains a second canonical grammar.

### B — `protos` republishes/materializes the grammar as a canonical source asset

Keep the VS Code repo as implementation owner but copy/synchronize the grammar back into `protos` so website and other consumers continue to consume a Protos-owned path.

This creates a second maintained synchronization boundary and risks making it unclear whether `protos` or the extension is the canonical grammar producer.

### C — Website stops consuming the TextMate grammar

Remove the grammar as a canonical source input and replace syntax highlighting with renderer-local metadata or a different presentation mechanism.

This reduces cross-repository coupling but either loses existing syntax highlighting quality or creates another grammar/lexer interpretation in the website.

### D — Website owns its own grammar fork

Copy the grammar into `protos-website` and maintain it independently.

This is explicitly considered only as a negative baseline because it creates semantic/presentation drift and a third authority surface.

## Required comparative evaluation

Evaluate the candidates against:

1. **future durability / aguante de futuro** — ability to evolve repositories independently without losing a truthful source relationship;
2. **scalability** — multiple consumers, future IDE/editor products, release cadence and many exact source revisions;
3. **Protos philosophy fit** — explicit authority separation, ordinary mechanisms, no unnecessary synchronization institutions, pay-only-for-use and implementation freedom;
4. correctness / authority clarity;
5. reproducibility / provenance;
6. update cadence and coordination cost;
7. portability / implementation freedom;
8. migration and rollback cost;
9. security/supply-chain implications for a public read-only dependency;
10. long-term interoperability with other non-VS Code consumers.

## Current evidence

The independent extension currently exposes the grammar as a normal repository asset at:

```text
syntaxes/protos.tmLanguage.json
```

and its own repository governance states that the extension repository owns the standalone VS Code product. The website currently has an exact-SHA source lock and preparation machinery that assumes the old `editors/vscode/...` path exists.

## Stress scenarios

Evaluate each candidate against at least:

- the VS Code extension changing grammar cadence independently from Protos language releases;
- multiple future editor integrations sharing or not sharing the same grammar;
- Protos specification changes that require corresponding editor grammar changes;
- an extension revision being temporarily unavailable or unsuitable for a website release;
- reproducible historical website builds;
- a future non-VS Code syntax consumer;
- replacing TextMate with another editor grammar technology;
- avoiding circular repository authority where `protos` would consume the extension while the extension independently consumes `protos`.

## Approval boundary

Opening D102 selects nothing. The project owner must explicitly approve the durable authority model before WEB006 changes the website source lock, AGENTS governance, materializer, or related CI contracts.

If the chosen model exposes a broader cross-repository authority or release-coordination issue, stop and route that issue through the normal Dxxx governance path rather than embedding it in WEB006 implementation.
