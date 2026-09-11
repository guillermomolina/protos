# LIB008 — URI reference Standard Library design

Status: **LIB008-0 RATIFIED — implementation slices released**

Owning work item: GitHub Issue `#339` — `LIB008 — URI reference parsing, resolution and serialization`

Nature: project Standard Library design record; **non-normative**

Explicit project-owner approval: **2026-09-11**

Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

Normative dependencies: the existing Core String, `null`, Object, Map, Error,
Module, invocation and transfer contracts under `spec/`; the already-ratified
Core/network boundary and the existing `IpAddress` / `IpEndpoint` data concepts
remain unchanged.

Primary external standards used by the design:

- RFC 3986 / STD 66 — Uniform Resource Identifier (URI): Generic Syntax;
- RFC 8820 / BCP 190 — URI Design and Ownership;
- RFC 3987 — Internationalized Resource Identifiers (IRIs), as deliberately
  separate future-layer evidence;
- WHATWG URL Standard, as deliberately separate web-URL evidence.

## Purpose

This record closes the `LIB008-0` comparative design/selection checkpoint for a
small generic URI-reference facility in the Protos Standard Library.

The selected design is **RFC-3986-first**. It provides strict, deterministic,
authority-free syntax processing for generic URI references. It deliberately
does not make a browser-compatible WHATWG URL object the universal Protos
resource-identifier abstraction.

The selected library adds no new Core semantic family, no privileged URI value,
no hidden resolver, no scheme registry, no filesystem authority and no network
authority. The normative Protos specification remains authoritative; LIB008 is
an optional library contract over ordinary existing Protos mechanisms.

## Selected module and distribution identity

The selected canonical module identity is:

```text
std:uri
```

The selected physical distribution path is:

```text
protos/lib/uri.protos
```

This follows the existing Standard Library naming rule exactly. `Uri` is a module
of operations, not a Core prototype or a new semantic URI family.

Importing the module grants no authority and performs no host I/O.

## Selected conceptual model

The base abstraction is the RFC 3986 **URI-reference**, not only an absolute URL.
It therefore covers both absolute URIs and relative references using one generic
seven-slot representation:

```text
scheme    String | null
userInfo  String | null
host      String | null
port      String | null
path      String
query     String | null
fragment  String | null
```

`null` means the component/delimiter is absent. An empty String means the
component is present but empty. This distinction is observable and must survive
parse/format round trips.

The exact presence rules are:

- `scheme == null`: no scheme is present and the value is a relative reference;
- `host == null`: the authority delimiter `//` is absent;
- `host == ""`: authority is present with an empty host;
- when `host == null`, both `userInfo` and `port` are also `null`;
- `userInfo == null`: no `@`; `userInfo == ""`: `@` is present with empty userinfo;
- `port == null`: no port `:` delimiter; `port == ""`: the delimiter is present
  with an empty generic RFC port;
- `path` is never `null`, but it may be `""`;
- `query == null`: no `?`; `query == ""`: `?` is present with an empty query; and
- `fragment == null`: no `#`; `fragment == ""`: `#` is present with an empty
  fragment.

All String fields preserve their exact accepted RFC spelling, including case and
percent-triplet spelling. `host` is generic RFC host text; an IP-literal retains
its bracket spelling. No field is an `IpAddress`, DNS-name object or Integer
port.

Examples:

```text
https://example.test/path
    query = null

https://example.test/path?
    query = ""

https://example.test/path#
    fragment = ""
```

`path` is always present as a String because RFC 3986's generic syntax always has
a path production, including the empty path.

### Ordinary Protos data

A successful parse returns fresh ordinary behavior-free Protos data containing
exactly the selected seven public component slots. The result is frozen before it
is returned so the parse result itself cannot acquire a second mutable state
contract.

This is **not** a new Core value-identity family. It does not change `===`, and
LIB008-0 does not define a URI-specific `==` or hash law.

The component values are only existing Core `String` values or canonical `null`.
No Closure-valued library behavior is stored in the returned data graph, and the
record delegates directly to ordinary `Object` rather than to a library
prototype containing behavior. Consequently LIB008 does not need a privileged
transfer rule, library prototype identity or behavior-bearing wrapper merely to
carry URI data across ordinary Protos boundaries.

## Selected initial public surface

The initial bounded public surface is:

```text
std:uri

    parse(text)                 -> URI-reference data
    format(reference)           -> String
    resolve(base, reference)    -> URI-reference data
```

No Builder object, mutable URL object, scheme subclass hierarchy or global
registry is selected.

A later separately bounded slice may add explicit syntax normalization, for
example:

```text
normalizeSyntax(reference) -> URI-reference data
```

Normalization is **not** an implicit side effect of the selected initial API.

## `parse(text)` contract

`parse` accepts exactly one semantic Core `String`. Wrong arity or wrong input
family signals ordinary synchronous `Error`.

Parsing is strict RFC-3986 generic-syntax validation. It is not a permissive
split helper and not a WHATWG browser-repair state machine.

A successful result preserves the accepted source spelling at the generic
component level. In particular parsing does not silently:

- lowercase the scheme or host;
- uppercase/lowercase percent-triplet hexadecimal digits;
- decode percent escapes;
- remove dot segments;
- remove or infer a default port;
- rewrite an empty component to absence or absence to empty;
- convert a registered name through IDNA;
- map raw Unicode input to UTF-8 percent escapes;
- turn a query into key/value data;
- turn a host into `IpAddress`;
- turn an authority/port into `IpEndpoint`;
- interpret `file:` as a host filesystem path; or
- perform DNS, filesystem, HTTP or Network effects.

For every accepted source String `s`, the initial contract requires:

```text
format(parse(s)) == s
```

where `==` above is ordinary String equality.

### Character and percent-encoding boundary

The initial parser accepts the ASCII URI syntax of RFC 3986. Raw non-ASCII
characters are rejected rather than silently reclassifying input as an IRI or a
WHATWG URL.

Every percent-encoded occurrence in a component must be a syntactically valid
`%` followed by exactly two ASCII hexadecimal digits. The accepted hexadecimal
letter case is preserved.

Percent-encoded octets remain encoded syntax in the base representation. `parse`
does not decode them because decoding can change the structural role of reserved
characters. For example:

```text
/a%2Fb
/a/b
```

must not become indistinguishable merely because `%2F` can denote the octet for
`/`.

### Relative-reference grammar

Parsing supports all generic RFC 3986 URI-reference forms, including relative
references. It follows the actual grammar distinction between `URI` and
`relative-ref`; it must not use an ad-hoc "first colon means scheme" rule that
accepts strings forbidden by the `path-noscheme` production.

The parser is required to fail closed on malformed syntax rather than return a
partially interpreted result for downstream consumers to reinterpret.

### Error/effect behavior

Malformed URI syntax, malformed percent escapes, raw characters excluded by the
selected RFC-3986 ASCII profile, wrong arity and wrong input families signal
ordinary synchronous `Error`.

No failure path performs an external effect or commits partially parsed global
state. Parser state is call-local.

## `format(reference)` contract

`format` accepts one reference value satisfying the selected seven-slot data
shape and validates all components before producing text.

It does not guess, repair or normalize malformed component data. Invalid data
signals ordinary synchronous `Error`.

Serialization follows RFC 3986 generic component delimiters exactly and preserves
absence versus empty presence:

```text
scheme ":"                   only when scheme is present
"//"                         only when host is not null
userinfo "@"                 only when userInfo is not null
host                          exactly as stored when authority is present
":" port                     only when port is not null
"?" query                    only when query is present
"#" fragment                 only when fragment is present
```

`format` does not apply scheme-specific default ports, authority semantics,
query semantics, Unicode conversion, filesystem conversion or network
resolution.

Construction needs no dedicated Builder institution: ordinary component data can
be constructed by callers and passed to `format`; validation remains at the
library boundary.

## `resolve(base, reference)` contract

`resolve` implements RFC 3986 section 5 reference resolution and returns fresh
ordinary frozen URI-reference data.

Both arguments are parsed URI-reference data, not Strings implicitly reparsed by
the operation. Callers that start from text use `parse` explicitly.

The base must conform to RFC `absolute-URI`: its `scheme` must be present and
its `fragment` must be `null`. A base with no scheme or with a fragment signals
ordinary synchronous `Error` rather than being silently repaired or stripped.
The `reference` may be any valid URI-reference, including an empty or
fragment-only reference. Invalid base/reference shapes signal ordinary
synchronous `Error`.

Resolution performs the generic RFC algorithm, including the required path merge
and dot-segment removal that are part of **reference resolution**. This does not
turn `parse` or `format` into normalizing operations.

The algorithm does not consult a scheme registry, default-port table, DNS,
filesystem, Network authority or HTTP behavior.

## Authority boundary

The selected base representation decomposes RFC generic authority syntax once
into `userInfo`, `host` and `port` fields. It does **not** store a second raw
`authority` field, so there is one authoritative representation rather than two
mutable sources of truth.

Authority presence is represented by `host != null`. Empty-vs-absent delimiter
states remain exact through the presence rules above, including empty userinfo,
empty host and empty port. `format` reconstructs the authority spelling exactly
from those three preserved textual fields.

RFC 3986 permits generic authority syntax involving userinfo, host and port, but
their higher-level meaning belongs to scheme/application policy. Therefore:

- generic host is **not** restricted to `IpAddress`;
- generic port is text, not an `Integer` range contract and not an `IpEndpoint`
  port;
- registered names do not cause DNS lookup;
- IP literals, registered names and IPvFuture-compatible generic spellings are
  syntax until another explicitly selected layer interprets them; and
- parsing or formatting a URI never acquires `Network` authority.

The seven-slot decomposition is data, not policy: LIB008 still does not infer
credentials, DNS identity, default ports, transport endpoints or scheme-specific
semantics from these fields.

## Query boundary

The base `query` component is URI component text. It is not automatically a Map,
sequence of pairs, form body or application/x-www-form-urlencoded value.

In particular, LIB008 assigns no generic meaning to `&`, `=`, repeated keys or
`+`. A future HTTP/form facility may define the explicit
`application/x-www-form-urlencoded` convention without changing the meaning of
URI query text.

This keeps one generic parser from silently imposing web-form policy on package,
custom-scheme or other non-web identifiers.

## Unicode, IRI and IDNA boundary

Raw Unicode IRI input, UTF-8 mapping, Unicode normalization, IDNA, UTS #46,
punycode and bidi/domain-display policy are intentionally deferred.

RFC 3987 is evidence for keeping IRI an explicit layer rather than silently
changing RFC 3986 URI semantics. A future Protos IRI/domain facility can define
explicit conversions such as:

```text
IRI -> URI
URI -> IRI where defined
```

with its own Unicode-version and IDNA contracts.

Similarly, a future browser-compatible web URL layer may adopt WHATWG URL and its
IDNA/special-scheme/file rules without redefining generic `std:uri` values.

## Normalization and equivalence boundary

Parsing and formatting preserve accepted spelling. LIB008-0 selects **no implicit
normalization** and **no one universal URI equivalence law**.

RFC URI comparison has multiple useful levels:

- exact textual equality;
- syntax-based normalization/equivalence;
- scheme-aware equivalence;
- protocol/resource equivalence.

Those levels must not be collapsed into one hidden `==` / hash behavior.

A future explicit `normalizeSyntax` operation may perform only the syntax-level
transformations selected for that API. Scheme-aware equivalence, default ports,
DNS aliases, filesystem equivalence and HTTP resource identity remain outside the
generic layer.

## Security and parser-differential analysis

The selected strict parser is intentionally fail-closed because URI strings often
cross trust boundaries and are later consumed by scheme-specific software.

The design avoids several common ambiguity sources in the generic layer:

- no browser-style backslash repair;
- no stripping of arbitrary controls/whitespace;
- no legacy permissive IPv4 interpretation selected here;
- no IDNA transformation hidden in generic parsing;
- no automatic percent decoding of reserved delimiters;
- no userinfo-to-credential or host-to-DNS side effect;
- no default-port or scheme registry lookup;
- no permissive partial split result presented as a validated URI; and
- no form-query decoding that can disagree with another consumer.

Scheme-specific security validation remains the responsibility of the layer that
owns that scheme's semantics. Generic URI validity must not be mistaken for
"safe to fetch", "safe local path", "allowed redirect", "same origin" or another
higher-level authorization decision.

## Complexity and scalability contract

The generic grammar admits a direct deterministic scan. The implementation
quality target for parsing, formatting and RFC reference resolution is linear in
the size of the input/components:

```text
parse:    O(n) time, O(n) result storage, bounded call-local parser state
format:   O(n) time, O(n) output storage
resolve:  O(n) time relative to involved component/path sizes
```

The public contract does not require a regex/backtracking engine, parser cache,
scheme registry, process-global interning table or Unicode database.

The implementation remains ordinary Protos. One compatible linear strategy is
to validate/encode the semantic source String through `Encoding.UTF8`, reject any
non-ASCII octet for this initial RFC-3986 profile, scan the resulting `Bytes`
linearly, accumulate component bytes, and decode completed ASCII components back
to Strings. This is implementation freedom, not a new Core String operation or
public URI representation requirement.

The selected operations create no thread, Task, Actor, Process, Future, event
loop, lock, network allocation or global coordination mechanism. Independent
callers can operate concurrently over independent inputs.

A streaming URI parser is deliberately not selected. The API consumes a semantic
String identifier; introducing incremental parser lifetime/backpressure/state
would add machinery without a demonstrated URI use case. This can be revisited
only if a real future consumer requires it.

## Comparative prior-art audit

LIB008-0 was selected after comparison of standards and materially different
language/library models. Scores below measure suitability as precedent for the
**generic Protos base URI layer**, not overall ecosystem quality.

| System / model | Future | Scale | Protos fit | Durable lesson |
| --- | ---: | ---: | ---: | --- |
| RFC 3986 + RFC 8820 | 5 | 5 | 5 | Generic syntax/resolution is deliberately separable from scheme/application semantics. |
| RFC 3987 IRI | 5 | 4 | 5 | Unicode identifiers are an explicit interoperable layer, not a silent mutation of URI syntax. |
| WHATWG URL | 3 generic / 5 web | 4 | 2 | Excellent web interoperability model; special schemes, IDNA, `file:` and form rules are too much policy for the universal generic layer. |
| Java `java.net.URI` | 3 | 4 | 3 | Strong authority-free immutable precedent, but historical opaque/hierarchical/equality/raw-vs-decoded baggage should not be copied wholesale. |
| .NET `System.Uri` | 3 | 4 | 2 | Mature but eager canonicalization and scheme/file/default-port behavior demonstrate long-lived policy coupling. |
| Python `urllib.parse` | 3 | 4 | 2 | Practical compatibility splitter, but intentionally non-validating parsing is too weak for a Protos validated-URI boundary. |
| Go `net/url` | 4 | 4 | 4 | Good reference resolution and raw preservation evidence; parallel raw/decoded fields show the invariant cost of dual representations. |
| Rust `url` | 4 | 5 | 3 | Excellent WHATWG implementation and strong later web-layer precedent, not a neutral generic base. |
| JavaScript / Node `URL` | 4 | 4 | 2 | Correct web object and form-query ecosystem; mutable setters and web policy are not generic URI laws. |
| Ruby `URI` | 3 | 3 | 2 | Scheme class/registry growth illustrates an institution Protos need not put in the generic layer. |
| Boost.URL | 5 | 5 | 5 | Strong strict RFC 3986 parsing/resolution/explicit-normalization precedent with implementation freedom. |
| Swift/Foundation URLComponents | 4 | 4 | 3 | Good component construction; encoded/unencoded dual surfaces and query-item conventions show why the base should stay smaller. |
| libcurl CURLU | 3 | 4 | 2 | Production transfer needs many flags and scheme policies; transport policy should not define generic identifiers. |
| Erlang `uri_string` | 5 | 5 | 5 | Very close model: pure module operations over ordinary component data with parse/recompose/resolve/normalize separated. |
| Elixir `URI` | 4 | 5 | 4 | Module + ordinary data is a strong fit; permissive/history and www-form options show why generic validity and form policy should remain explicit. |

Primary reference entry points retained for future review:

- https://www.rfc-editor.org/rfc/rfc3986
- https://www.rfc-editor.org/rfc/rfc8820
- https://www.rfc-editor.org/rfc/rfc3987
- https://url.spec.whatwg.org/
- https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/URI.html
- https://learn.microsoft.com/dotnet/api/system.uri
- https://docs.python.org/3/library/urllib.parse.html
- https://pkg.go.dev/net/url
- https://docs.rs/url/latest/url/
- https://nodejs.org/api/url.html
- https://ruby-doc.org/3.4/stdlibs/uri/URI.html
- https://www.boost.org/doc/libs/release/libs/url/
- https://developer.apple.com/documentation/foundation/urlcomponents
- https://curl.se/libcurl/c/libcurl-url.html
- https://www.erlang.org/doc/apps/stdlib/uri_string.html
- https://hexdocs.pm/elixir/URI.html

## Architecture candidates and common scoring

Five meaningful architecture families survived long enough for explicit scoring:

A. strict RFC-3986-first URI-reference mechanism over ordinary Protos data, with
   accepted spelling preserved and normalization explicit;
B. canonicalizing RFC object with eager normalization/equivalence decisions;
C. one WHATWG-first browser-compatible URL model used universally;
D. ship both a generic RFC URI stack and a WHATWG web URL stack immediately; and
E. permissive split/merge utility that defers validity to callers.

Confidence: `H` = high, `M` = medium.

| Candidate | Correct | Protos | Future | Scale | Simplicity | Portability | Cost | Failure | Reversible | Evidence | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **A strict RFC/raw/ordinary-data** | 5/H | 5/H | 5/H | 5/H | 5/M | 5/H | 5/H | 5/H | 5/H | 5/H | **50** |
| B canonicalizing RFC object | 4/H | 3/H | 3/H | 5/H | 3/H | 4/H | 4/H | 4/H | 2/H | 5/H | 37 |
| C WHATWG universal | 4/H | 2/H | 3/H | 4/H | 2/H | 4/H | 3/H | 3/H | 2/H | 5/H | 32 |
| D dual stack immediately | 5/M | 3/H | 5/H | 4/H | 2/H | 4/H | 2/H | 4/H | 3/H | 4/M | 36 |
| E permissive split/merge | 2/H | 3/H | 3/H | 5/H | 5/H | 5/H | 5/H | 1/H | 3/H | 4/H | 36 |

### Why A was selected

The arithmetic total is not the authority. Candidate A is selected because it
satisfies the generic-identifier invariant while leaving every materially
different higher-level policy independently addable.

It preserves Protos principles directly:

- **small universe:** one ordinary module and ordinary component data, not a new
  semantic URI/URL class universe;
- **mechanisms over institutions:** generic parse/format/resolve mechanisms; no
  scheme registry, browser institution or builder hierarchy;
- **ordinary things remain ordinary:** Strings, `null` and ordinary frozen data;
- **semantic distinctions remain visible:** URI syntax, web URL behavior, IRI,
  DNS, Network effects, filesystem paths and form queries are not conflated;
- **pay only for what you use:** URI processing creates no resolver, Unicode
  database, event loop, cache or network machinery;
- **minimal coordination:** all state is call-local;
- **portability:** no JVM/OS/browser behavior becomes portable Protos semantics;
- **future-option resilience:** WHATWG, IRI, HTTP, DNS/domain and filesystem
  layers can be added without changing existing generic URI meaning.

### Why the alternatives were not selected

Candidate B makes normalization/equality decisions expensive to retract and
invites scheme policy into the base value.

Candidate C is excellent when exact browser/Web compatibility is the goal, but
would make special-scheme, IDNA, file and form behavior part of package/custom
URI semantics unnecessarily.

Candidate D preserves options but imposes two substantial institutions before a
real web-URL consumer requires the second one.

Candidate E is conceptually small but fails the validated-boundary/security goal:
downstream components can disagree about whether the same split input is valid.

## Future-scenario stress analysis

### Package manifests, registries and lockfiles

Raw spelling preservation avoids silently rewriting persisted identifiers merely
because tooling inspected them. Scheme-specific package policy can be layered
above the same parsed syntax.

### HTTP clients, servers, redirects and proxies

Generic HTTP URI syntax composes with the RFC base. Exact browser-compatible URL
input or redirect behavior may require WHATWG; that becomes a distinct future
web URL layer rather than retroactively changing generic URI parsing.

### Many parsed identifiers

Values contain only their own component data. There is no registry/cache/global
mutable state whose coordination grows with URI count, Task count, Actor count,
Process count or host concurrency.

### Serialization and persisted interchange

Preserving accepted syntax and empty-vs-absent distinctions gives a stable
initial textual round trip. Future explicit normalization can be chosen by a
caller rather than occurring accidentally during persistence.

### Alternative runtimes / Bytecode DSL

The contract depends only on portable Protos data and deterministic algorithms.
No Java `URI`, browser engine, Truffle AST representation, JVM host parser or OS
facility is visible in semantics. A later Bytecode DSL or non-JVM runtime can
implement the same contract independently.

### New URI schemes

No central scheme registry needs modification for syntax parsing. Scheme-specific
libraries can validate or interpret their own identifiers independently.

### Distributed systems

URI data carries no live network/file authority and introduces no machine-local
resource identity. The library adds no distributed coordination protocol.

## Regret analysis and escape paths

### Plausible regret: Protos becomes strongly web/browser-oriented

If future applications require exact browser URL parsing, navigation and
same-origin-compatible behavior, RFC 3986 alone is insufficient.

**Escape path:** add a distinct WHATWG-oriented web URL layer. Existing
`std:uri` remains the generic identifier syntax rather than being silently
redefined.

### Plausible regret: Unicode identifiers become the dominant API

ASCII URI syntax may feel low-level for user-facing international identifiers.

**Escape path:** add an explicit IRI/domain layer with a versioned Unicode/IDNA
contract and explicit mapping to URI. Existing persisted URI syntax stays stable.

### Plausible regret: consumers need higher-level authority semantics constantly

The base already exposes generic `userInfo` / `host` / `port` syntax, but HTTP,
DNS, TLS or credential consumers may need stronger interpreted forms.

**Escape path:** add higher-level scheme/domain/network views that consume the
seven-slot record without changing its generic URI syntax or exact text
round-trip.

### Plausible regret: very large generated identifiers need allocation control

Ordinary String/component results allocate proportional to the identifier size.

**Escape path:** implementation may use slicing/shared backing internally when
that is semantically invisible. A streaming API remains separately designable if
a real use case eventually demonstrates one.

## Strongest argument against the selected design

The strongest objection is ecosystem ergonomics: users often mean "web URL" when
they say URI/URL, and a strict RFC-3986 generic parser may reject or preserve
spellings that browsers repair/canonicalize automatically. Shipping only the
generic layer initially can therefore require a later explicit `WebUrl` facility
for familiar browser behavior.

That cost is accepted because treating browser compatibility as the universal
identifier law would impose substantially more irreversible policy on package,
custom-scheme, tooling and non-web uses. A second explicit web layer later is a
smaller compatibility cost than trying to remove hidden WHATWG policy from an
already-published universal URI abstraction.

## Intentionally deferred

LIB008-0 does not select or implement:

- syntax normalization API details beyond keeping normalization explicit;
- scheme-specific normalization or equivalence;
- URI-specific `==`, `hash` or value identity;
- WHATWG `URL`, special schemes or browser repair rules;
- IRI parsing/formatting and Unicode normalization;
- IDNA / UTS #46 / punycode policy;
- higher-level authority interpretation helpers;
- DNS resolution or resolver authority;
- default-port tables;
- conversion to/from `IpAddress` or `IpEndpoint`;
- `file:` to host-path conversion;
- query-pair or application/x-www-form-urlencoded helpers;
- HTTP client/server semantics;
- credential handling/userinfo policy beyond generic syntax;
- origin/same-origin calculations;
- URI templates;
- scheme registries;
- streaming/incremental URI parsing;
- regex/parser-framework dependencies;
- host/JVM URI bridges or intrinsic implementations.

Each deferred item crosses the ordinary explicit approval gate if it introduces
a substantive public semantic or durable architecture choice.

## Implementation decomposition released by this ratification

After this ratification is published, the approved initial contract may be
implemented in small cost-aware slices without reopening LIB008-0 unless a new
substantive design question appears:

```text
LIB008-A  component-data construction/validation + strict parse
LIB008-B  format / exact text round-trip
LIB008-C  RFC 3986 relative-reference resolution
LIB008-D  integrated conformance / closure of the initial surface
```

The exact child-Issue granularity should follow the repository durable-identity
rule when each slice starts; execution-only subdivisions remain inside the owning
Issue rather than creating issue noise.

Every executable slice must use the real `std:` resolver path and include focused
conformance for accepted grammar, malformed inputs, percent escapes,
empty-vs-absent components and no helper leakage. `resolve` additionally needs
the RFC 3986 normal/abnormal resolution examples and focused path-merge/dot-
segment evidence.

If implementation exposes a new substantive language, Standard Library or
platform decision, stop the affected slice and route that question through the
explicit approval gate rather than deciding it inside a patch.

## Change classification

`VALIDATION_CLASS=GOVERNANCE_DOCUMENTATION_ONLY`

This ratification adds only this durable non-normative LIB008 design record and a
matching changelog entry. It changes no Protos specification, executable runtime,
Standard Library executable source, Maven implementation version, native
boundary, package format, license term or deployment.
