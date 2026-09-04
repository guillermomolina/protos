# Core Bootstrap Architecture

Status: non-normative implementation architecture.

This document defines where the Protos implementation should place the machinery
needed to construct the standard Core object memory. It does not define language
semantics. Observable behavior remains owned by the normative specification under
`spec/`.

## Principle

The host runtime must bootstrap only the minimum machinery that cannot yet be
expressed as ordinary Protos code. Standard objects and standard behavior should
be constructed or installed by Protos source as soon as the language can express
them faithfully.

The implementation must not grow a second hardcoded standard library in Java.

## Runtime bootstrap boundary

The host implementation may provide directly the irreducible mechanisms needed
before ordinary Protos source can execute, including:

- the unique root identity of `Object`, because `Object` is the only object with
  no delegation parent;
- runtime representation of Protos values when a host representation is needed;
- slot storage, immutable delegation-parent links, lookup, mutation-state
  machinery, execution, dispatch, signaling, and other mechanisms required to
  execute Core source;
- native primitive implementations for operations that cannot be implemented
  portably or efficiently in Protos itself;
- the bridge that exposes such primitives as ordinary Core behavior.

These mechanisms are implementation machinery. They do not authorize observable
semantics beyond the current normative specification.

## Protos Core library boundary

Once the bootstrap runtime can execute the required source forms, standard
objects and ordinary standard behavior should live under:

```text
protos/lib/core/
```

Examples include the standard `Context`, numeric-family prototype objects,
collection prototypes, Error-family objects, and ordinary methods whose behavior
can be expressed in Protos.

The exact set of required standard objects, their identities, delegation
relationships, receiver domains, state, and observable protocols comes only from
the normative specification. A file existing under `protos/lib/core/` cannot
create new language semantics by itself.

Native-backed behavior should still participate in the ordinary object model.
Where a standard slot requires host support, bootstrap should bind an ordinary
slot to a native primitive implementation rather than hide the selector in a
parallel Java-only dispatch table when the ordinary model can represent it.

## Bootstrap phases

The intended dependency direction is:

```text
host runtime mechanisms
        ↓
create the unique Object root
        ↓
establish the primitive bridge needed to execute Core source
        ↓
load protos/lib/core/
        ↓
construct/install ordinary standard objects and behavior
        ↓
execute user modules
```

A later phase may depend on an earlier phase. An earlier phase must not absorb
ordinary Core behavior merely to avoid implementing the loader or invocation
machinery.

## Temporary scaffolding

During implementation, temporary Java-side construction is permitted only when
it is clearly scaffolding required to reach the real Core-loading path and does
not silently become the permanent semantic definition.

The current Java-side `Context` construction in `ProtosCorePrelude` is such
temporary scaffolding. It establishes the already-specified
`activationContext -> Context -> Object` relationship while invocation and Core
source loading are incomplete. It should be replaced by the Core bootstrap path
once that path can construct `Context` faithfully.

New standard prototype families must not be added to this scaffolding merely
because doing so is convenient.

## Relationship to the specification

This architecture deliberately separates implementation placement from language
semantics:

- `spec/` defines what Protos means;
- `src/` implements irreducible runtime/compiler machinery;
- `protos/lib/core/` contains distributable Core behavior written in Protos;
- this document explains the implementation boundary between those layers.

If moving behavior between Java and Protos would change any observable result,
identity, lookup path, failure, evaluation order, concurrency property, or other
specified behavior, that is no longer a mere architecture move and must be
checked against the normative owner before implementation.
