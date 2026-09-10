# Protos source style — idiomatic syntax and canonical forms

> **Status:** Approved project source-style policy  
> **Approval:** project owner, 2026-09-09  
> **Authority:** non-normative. The applicable specifications under `../../spec/`
> define Protos syntax and semantics; this document defines only the preferred
> spelling of repository and idiomatic user-facing source when several specified
> spellings are semantically equivalent.

## Default rule

Hand-written Protos source should normally use the stable, specified idiomatic
syntactic form rather than systematically exposing its canonical expanded or
protocol form.

The canonical form remains the semantic reference for understanding, specifying,
implementing and testing the underlying mechanism. It is not therefore the
mandatory writing style for ordinary programs or libraries.

This default applies in particular to:

- Standard Library source written in Protos;
- bundled tools and other ordinary Protos programs in this repository;
- tutorials, examples and documentation snippets intended to model normal code;
- Protos-source tests whose purpose is not the syntax lowering itself.

For example, where the specification defines unary `!value` through the standard
Boolean `not()` protocol, ordinary source should normally prefer:

```protos
!value
```

over spelling the underlying protocol only to expose the desugaring:

```protos
value.not()
```

Both spellings remain available when the applicable specification defines them;
this policy chooses the normal human-facing style, not their semantics.

## When to use the canonical or expanded form

Use the explicit canonical/protocol form when it is materially the clearer or
more correct source for the task, especially when:

- implementing, exercising or documenting the underlying protocol itself;
- testing parser lowering, dispatch, binding or semantic equivalence between
  the surface form and its underlying form;
- a bootstrap or layering boundary exists before the syntactic convenience is
  available, or using it would create a circular dependency;
- metaprogramming, reflection or dispatch behavior makes the explicit protocol
  invocation the actual subject of the code; or
- the expanded form communicates intent more clearly in the specific context.

The existence of syntactic sugar is therefore not a command to use it
mechanically everywhere.

## Boundaries

This policy does **not**:

- introduce, approve or imply new syntax;
- make an implementation lowering normative unless the specification already
  defines the observable equivalence;
- authorize a library or agent to invent a preferred spelling when the language
  leaves a design question open;
- require generated, lowered or intermediate implementation forms to resemble
  idiomatic hand-written Protos source; or
- justify unrelated mechanical rewrites of otherwise unaffected source merely to
  normalize style.

When a touched source region already has an established local idiom consistent
with this policy, preserve it unless the scoped change has a reason to improve
that region. New source should model the idiomatic language that Protos expects
programmers to write while keeping the canonical protocol model visible where it
is actually relevant.

## Repository conformance tracking

Repository-wide migration to this approved style was completed by
`../project/work/AUD003/AUD003_PROTOS_SOURCE_STYLE_CONFORMANCE_AUDIT.md`.

AUD003 records bounded migrations, known debt, and deliberate canonical/protocol
exceptions. It does not strengthen this policy into a language-wide ban on
canonical forms and does not authorize syntactic equivalences that the
specification has not defined.

## Repository prevention gate

Repository publication validation runs `scripts/source_style_guard.py` before
the selected executable test set. GitHub CI already routes its base/head pair
through the same `scripts/publication_validation.py` entry point, so the same
guard applies to publication launchers, pushes and pull requests.

The guard is deliberately differential, not a repository-wide ban. It examines
changed hand-written Protos source under `protos/`, plus Protos/JS fenced source
examples in the Programming Guide and root README. For the equivalence families
already confirmed by AUD003 it rejects an *increase* in explicit canonical
spellings: indexed `at`/`atPut`, expanded lazy Boolean `and`/`or`, parameterless
`not`, and parameterless `negated`.

Existing occurrences at the publication base are grandfathered, not newly
approved. Keeping or reducing their count is allowed, which lets bounded audits
such as AUD003-E continue removing historical debt.

A genuinely deliberate new canonical occurrence requires an exact entry in
`scripts/source_style_exceptions.json`: one guarded path, one family, the exact
candidate-head occurrence count, and a meaningful reason. The declared count
must equal the actual candidate count, so an exception cannot pre-authorize
future growth. Exception entries record source-style intent only; they cannot
approve new syntax, semantic equivalence, or an architectural decision.

## Rationale

The specification answers **what a Protos program means**. Idiomatic source style
answers **how humans should normally express that program**.

Keeping those responsibilities separate lets Protos retain a small, explicit
semantic model without forcing libraries and applications to read like lowered
ASTs. It also lets the Standard Library, tutorials and examples serve as genuine
examples of normal Protos rather than a privileged implementation dialect.
