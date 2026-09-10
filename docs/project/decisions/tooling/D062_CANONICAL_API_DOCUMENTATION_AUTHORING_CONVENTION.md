# D062 — Canonical API documentation authoring convention

Status: **RATIFIED**

Allocated: **2026-09-10**

Explicit project-owner approval: **2026-09-10**

Nature: durable implementation-independent tooling/documentation decision

Triggered by: D061 / GitHub #311 and WEB001-J7B / GitHub #301

Decision issue: GitHub #313

Normative language effect: **none**. D062 defines a documentation-tool authoring convention over
already-valid ordinary Protos comments and canonical supplemental Markdown. It does not introduce
new lexical tokens, parser semantics, runtime objects, reflection state, exports, privacy,
annotations, types, execution behavior, or Standard Library semantics.

## Upstream boundary

D061 ratified Candidate F:

- mechanically observable module/symbol facts come from Protos-owned extraction;
- authored API explanation remains canonical in `guillermomolina/protos`;
- the two layers are validated together;
- one versioned implementation-neutral documentation model is emitted for downstream consumers;
- the website is not API authority; and
- implementation source alone must not create stability or semantic-contract promises.

D061 intentionally deferred the authoring convention. D062 resolves only that deferred question.

## Problem

Protos needs an authoring convention that is:

- explicit enough that legal/implementation comments cannot accidentally become public API docs;
- source-local enough that symbol documentation follows the symbol it explains;
- independent of Astro/Starlight and reusable by CLI, IDE/LSP and package tooling;
- compatible with exact-revision deterministic extraction;
- scalable to a larger Standard Library and third-party packages; and
- small enough not to introduce a documentation subsystem into the language/runtime.

The current source corpus makes ambiguity concrete: Standard Library files already begin with long
ordinary `//` APL license headers, and future implementation comments must remain ordinary comments
rather than implicit API documentation.

## Exhaustive ecosystem audit

The decision was reviewed across prototype/object-centric, dynamic, systems, functional and
mainstream ecosystems. Scores below evaluate how well adopting the relevant authoring pattern
would fit Protos, not the quality of the language itself.

| Ecosystem | Dominant documentation pattern relevant to D062 | Future | Scale | Protos | Total |
| --- | --- | ---: | ---: | ---: | ---: |
| Self | object/slot annotations and environment-attached comments | 8 | 7 | 9 | 24 |
| Io | explicit `//doc`, `/*doc ... */`, `//metadoc` conventions | 9 | 8 | 10 | 27 |
| Smalltalk / Pharo | object/class/method-centric documentation in image/browser | 8 | 7 | 9 | 24 |
| JavaScript + JSDoc/TypeDoc | distinguished source comments, often structured/tagged | 8 | 8 | 7 | 23 |
| Lua + LuaDoc/LDoc | distinguished comments + inferred declarations + Markdown | 8 | 8 | 9 | 25 |
| Go | ordinary preceding comments associated with declarations | 9 | 10 | 9 | 28 |
| Rust / rustdoc | `///` symbol docs + `//!` container/module docs | 10 | 10 | 8 | 28 |
| Zig | `///` declaration docs + `//!` container docs | 9 | 9 | 10 | 28 |
| C++ + Doxygen | distinguished line/block docs + extracted declarations | 9 | 10 | 7 | 26 |
| C# | `///` documentation comments + compiler-known symbol model | 10 | 10 | 9 | 29 |
| Java / Javadoc | declaration-attached docs; modern Javadoc also supports `///` Markdown comments | 10 | 10 | 8 | 28 |
| Swift / Symbol Graph + DocC | compiler symbols + source docs + supplemental articles | 10 | 10 | 9 | 29 |
| Kotlin / KDoc + Dokka | declaration-attached structured source documentation | 9 | 10 | 8 | 27 |
| Haskell / Haddock | distinguished markers inside otherwise ordinary comments | 9 | 9 | 10 | 28 |
| Dart | `///` source documentation associated with declarations | 9 | 9 | 9 | 27 |
| OCaml / odoc | declaration-attached docs plus larger documentation units | 9 | 10 | 8 | 27 |
| Scala / Scaladoc | structured declaration-attached documentation | 9 | 9 | 7 | 25 |
| Ruby / RDoc | ordinary comments associated with definitions | 8 | 8 | 8 | 24 |
| Python | runtime-visible docstrings + external narrative documentation | 10 | 10 | 4 | 24 |
| Julia | docstrings/doc metadata integrated with language/runtime tooling | 9 | 9 | 4 | 22 |
| Elixir / ExDoc | explicit `@doc` / `@moduledoc` metadata | 10 | 10 | 6 | 26 |

### Prototype/object-centric findings

#### Self

Self is important because documentation and tool-facing metadata are naturally associated with
object/slot identity rather than a website representation. This strongly supports D061's identity
model. Copying Self's live-image annotation machinery, however, would introduce a substantially
larger institution than file-first Protos needs.

#### Io

Io is the closest prototype-language precedent for D062's key distinction: explicit documentation
markers are separate from ordinary implementation comments while still being lightweight and
source-adjacent. Protos should retain that distinction but avoid Io-style repetition of names when
the D061 extractor can bind symbol identity mechanically.

#### Smalltalk / Pharo

Smalltalk-family practice reinforces two principles: documentation should attach to the object,
class or method identity being described, and useful method documentation explains use, result,
effects and contracts rather than paraphrasing implementation. Its image-centric storage model is
not appropriate as Protos' canonical transport because Protos is Git/source/exact-revision based.

#### JavaScript

Prototype semantics do not imply a runtime documentation object. JSDoc/TypeDoc demonstrate that a
prototype-based language can keep documentation source-adjacent and tool-owned. Heavy tag sets,
however, tend to duplicate parameter/type facts and admit multiple competing conventions.

#### Lua

Lua/LDoc is a strong dynamic-language analogue: distinguished documentation comments, inferred
declaration facts, supplemental Markdown and explicit handling of boilerplate/legal comments.
This directly supports separating Protos' APL headers from API documentation.

### Mainstream/systems findings

Go demonstrates that ordinary preceding comments can scale extremely well when declaration and
export structure is exceptionally crisp. Protos does not have a separate export declaration and
already has large ordinary-comment license preambles, so generic proximity would introduce
avoidable ambiguity.

Rust and Zig both validate the specific split between `///` for the following symbol and `//!`
for the containing module/container. C#, Dart, Doxygen and modern Javadoc independently validate
`///` as a widely understood explicit opt-in marker.

Swift DocC provides the strongest complete-model precedent: source-local symbol documentation is
combined with compiler/symbol facts and supplemental long-form articles. This closely matches the
ratified D061 architecture.

C++/Doxygen, Java/Javadoc, Kotlin/Dokka, TypeDoc and Scaladoc show that distinguished comments can
scale to very large APIs, but their structured tag-heavy traditions are more machinery than Protos
needs for the initial authoring convention.

### Functional/static findings

Haddock is particularly Protos-aligned: special documentation markers remain comments to the
language/compiler while documentation tooling interprets their placement. This supports a
tooling-only convention and fail-fast association rules.

OCaml/odoc similarly demonstrates that symbol-local documentation and larger narrative material
can coexist without making generated HTML the authority.

### Runtime-metadata findings

Python, Julia and Elixir prove that runtime/compiler documentation metadata can scale. That power
is not presently required by WEB001, CLI, IDE/LSP or package documentation. Introducing runtime
docstrings/metadata would expand Protos' semantic/tooling universe and violate pay-only-for-use
without an established runtime-reflection use case.

## Cross-ecosystem conclusions

1. Prototype-based languages strengthen the need to bind documentation to canonical module/slot
   identity rather than to website routes.
2. Explicit documentation markers are safer for Protos than generic ordinary-comment proximity.
3. `///` has broad independent precedent; `//!` has strong module/container precedent in Rust/Zig.
4. Long-form conceptual material should remain separate from symbol-local comments.
5. Mechanical facts such as symbol name and callable parameter shape should not be manually
   repeated in documentation tags when D061 can extract them.
6. Documentation tooling need not become language/runtime semantics.
7. The source convention must coexist cleanly with APL license headers and ordinary maintainer
   comments.

## Candidate comparison

| Candidate | Future viability | Scalability | Protos philosophy | Total | Outcome |
| --- | ---: | ---: | ---: | ---: | --- |
| A — ordinary preceding `//` | 8 | 8 | 8 | 24/30 | viable, but ambiguous in current Protos source |
| B — explicit `///` / `//!` source docs | 10 | 10 | 10 | 30/30 | strongest symbol/module-local primitive |
| C — structured block docs | 8 | 9 | 7 | 24/30 | mature but heavier/noisier than necessary |
| D — sidecar Markdown only | 8 | 8 | 7 | 23/30 | useful narrative layer, weak local authoring alone |
| **E-prime — explicit source docs + supplemental Markdown** | **10** | **10** | **10** | **30/30** | **selected complete authoring model** |
| F — language/runtime docstrings or metadata | 10 | 10 | 4 | 24/30 | powerful but unnecessary institution |

B and E-prime tie because B is the best local primitive. E-prime is selected as the complete
authoring model because it composes B with a separate long-form layer instead of forcing guides,
large examples and family-level concepts into source comments.

## Ratified decision — Candidate E-prime

### Source-adjacent symbol documentation

A contiguous block of lines beginning with `///` documents the immediately following
**documentable top-level symbol/slot**.

Conceptual form:

```protos
/// Creates a set containing `elements`.
call: (...elements) => {
    ...
}
```

Rules:

1. The `///` block must be contiguous.
2. It must immediately precede the symbol it documents.
3. No blank line, unrelated ordinary comment or executable/source construct may intervene.
4. An implementation/legal comment between a `///` block and a binding breaks association.
5. `///` at unsupported/nested scope is a documentation-validation error rather than an inferred
   alternate meaning.
6. D062 does not define which mechanically observable top-level slots are publishable API; that
   coverage/publication decision remains separate.
7. Absence of `///` does not mean private, unstable, unsupported or hidden.

### Module documentation

A contiguous block of lines beginning with `//!` documents the containing canonical module/file
documentation unit.

Conceptual form:

```protos
//! Identity-based set operations.
//!
//! Membership uses identity rather than value equality.
```

Rules:

1. The module-doc block belongs to the module preamble before the first executable/top-level
   source construct.
2. Ordinary legal/preamble comments, including the current APL header, may precede the module-doc
   block.
3. At most one module-doc block is accepted per canonical module documentation unit.
4. `//!` outside the permitted module preamble is a documentation-validation error.
5. D062 does not define a language-level module-doc runtime value or reflection slot.

### Ordinary comments remain ordinary

These remain implementation/legal comments and are never API documentation by default:

```text
// ordinary comment
/* ordinary block comment */
```

This distinction is deliberate. Tooling must not guess that proximity turns an ordinary comment
into API documentation.

### Language/runtime boundary

`///` and `//!` are **not new Protos lexical or semantic categories**. They are source text that
already begins with the ordinary `//` comment introducer. Their documentation meaning exists only
in Protos documentation tooling.

Consequences:

- ordinary Protos parsing/execution semantics do not change;
- no new token is required;
- no runtime `Documentation` object is introduced;
- no slot/reflection state is created;
- no export/private rule is created;
- normal programs pay no runtime cost; and
- source that contains `///`/`//!` remains ordinary-valid Protos comment syntax independently of
  whether documentation tooling is invoked.

### Authored text

The baseline authored body is Markdown-oriented prose suitable for transformation into the
D061 neutral documentation model.

D062 deliberately does **not** require Javadoc-style repetitions such as:

```text
@param element ...
@return ...
```

when parameter names/signature shape can be extracted mechanically under D061. Later structured
semantic metadata may be added only through its own explicit design decision.

### Supplemental Markdown

Long-form conceptual material, family/module guides and substantial examples may live as
canonical Markdown in `guillermomolina/protos` and be merged by Protos-owned documentation
tooling with the extracted source documentation.

D062 selects the existence of this **supplemental narrative layer**, but deliberately does not
select:

- its exact repository path;
- article identity/association encoding;
- the neutral-model schema representing it;
- website routes/layout;
- cross-package linking; or
- publication/coverage policy.

Those remain later decisions where needed.

## Scalability

### Large Standard Library

Symbol docs remain directly adjacent to the API they explain. Mechanical identity/signature facts
are extracted once rather than copied. Supplemental narrative material prevents large source files
from becoming manuals.

### Third-party packages

The convention depends only on ordinary Protos comments plus canonical package-owned Markdown.
Packages do not depend on `protos-website`, Astro, Starlight or a particular renderer.

### Multiple consumers

The same authored material can feed the D061 neutral model for web, CLI, IDE/LSP and future
package documentation without independent parsing conventions per consumer.

### Incremental tooling

Documentation extraction can operate per exact source revision and per changed module. No global
mutable documentation registry or runtime module execution is required.

### Contributor ergonomics

`///` versus `//` makes author intent visible during review. `//!` makes module scope explicit.
The simple prefix forms are formatter/editor friendly and avoid block-comment terminator
complexity.

## Protos philosophy alignment

Candidate E-prime scores 10/10 because it preserves:

- **small semantic universe:** no language/runtime documentation object or syntax extension;
- **mechanisms over institutions:** ordinary comments plus a documentation-tool convention;
- **explicit distinctions:** API docs, module docs and implementation comments are visibly
  different;
- **ordinary things remain ordinary:** modules and slots keep existing semantics;
- **fail at invariant violations:** ambiguous/malformed placement is rejected rather than guessed;
- **pay only for what is used:** documentation processing exists only in tooling paths;
- **composition:** source-local docs combine with supplemental Markdown and the D061 neutral model;
- **avoid duplicated facts:** extractor-owned symbol facts are not rewritten as documentation
  metadata; and
- **future evolution:** richer schema/metadata can be added without replacing the authoring
  primitive.

## Rejected shortcuts

D062 rejects:

- publishing arbitrary adjacent ordinary `//` comments as API docs;
- treating APL/legal headers as module docs;
- requiring manual repetition of selectors or callable signatures;
- making undocumented symbols private/unsupported by implication;
- introducing a runtime docstring/annotation object solely for documentation;
- making the website repository own API prose;
- selecting website routes/layout as part of the authoring convention; and
- treating supplemental Markdown as a second semantic authority.

## Intentionally deferred decisions

D062 does not select:

- neutral documentation model schema/serialization;
- stable symbol-ID encoding;
- API coverage/publication policy;
- public/private/stable/experimental vocabulary;
- deprecation metadata vocabulary;
- structured error/effect/capability metadata;
- doctest/executable-example policy;
- supplemental Markdown repository path/association encoding;
- cross-package linking;
- generated-source documentation policy beyond compatibility with the convention;
- CLI command spelling;
- IDE presentation; or
- website rendering/layout.

Any deferred item that materially constrains semantics, packages, compatibility or cross-tool
behavior must cross the normal Dxxx gate when it becomes necessary.

## Ratification closure

The project owner explicitly approved **Candidate E-prime** on 2026-09-10 after the initial
multi-ecosystem audit was expanded to include prototype-based/object-centric systems and a broader
21-ecosystem comparison scored by future viability, scalability and Protos philosophy.

Result:

```text
D062                    RATIFIED — Candidate E-prime
MODULE_DOC_MARKER       //!
SYMBOL_DOC_MARKER       ///
ORDINARY_COMMENTS       NOT API DOCUMENTATION
MARKERS_LANGUAGE_SYNTAX NO — tooling convention over ordinary // comments
SUPPLEMENTAL_MARKDOWN   YES — canonical in guillermomolina/protos
RUNTIME_DOC_OBJECT      NO
SIGNATURE_DUPLICATION   NO
MODEL_SCHEMA            DEFERRED
SYMBOL_ID_ENCODING      DEFERRED
COVERAGE_POLICY         DEFERRED
DEPRECATION_VOCABULARY  DEFERRED
WEBSITE_AUTHORITY       NO
```

This ratification changes no Protos specification, runtime/implementation, implementation version,
observable language behavior, Standard Library semantics, export/private rules, package format,
website repository or deployment configuration.
