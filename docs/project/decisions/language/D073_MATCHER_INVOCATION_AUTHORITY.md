# D073 — Matcher invocation authority and public selector contract

Status: **RATIFIED**
Specification revision: **`0.1.394`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative decision record; rationale for normative matcher invocation semantics
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#353`

## Decision

The required public matcher protocol has one ordinary-message semantic authority:

```text
pattern.match(subject)
```

Its normal matcher result is exactly the D072 carrier. Core does not require a
paired `matches(subject)` selector, a caller-visible recognition/extraction mode
or hint, callback/CPS matching, or a mutable capture sink.

Effects, Error, non-local return, cancellation, and explicit suspension therefore
have one observable matcher invocation path. Implementations may specialize that
path only with observationally equivalent behavior.

## Why one authority

The exhaustive review covered Scala/F# extractors, Raku, Ruby, Swift, TC39,
compiler-owned systems, prototype/message-oriented languages, pattern libraries,
and mature regex engines including Rust regex, .NET Regex, RE2 and PCRE2.

Separate predicate/extraction APIs are valuable for deterministic engine-owned
matchers because they can avoid capture work. For arbitrary Protos user behavior,
however, two public paths or one mode-sensitive path can disagree in success,
effects, Error/control transfer, or suspension and require a permanent
cross-path consistency institution.

A single authority is simpler and more reversible. If profiling later proves
ignored extraction cost material, an optional optimization protocol may be
audited separately without changing the semantic success relation established by
`match(subject)`.

## Intentionally deferred

D073 does not select matching-expression syntax, arms/defaults, literal patterns,
guards, exhaustivity, standard pattern taxonomy, structural projection details,
nested capture flattening, named bindings, or a future optional recognition-only
fast path.
