# Protos Programming Guide

This directory contains the non-normative programming guide for Protos.

The guide answers a different question from the normative specification:

> Given the Protos semantics, how should a programmer think about them and use
> them effectively?

The specification under [`../../spec/`](../../spec/) remains authoritative for
observable syntax and semantics. If this guide and the specification disagree,
the specification wins.

## How to use the learning material

Three complementary resources are maintained:

- this guide explains concepts and mental models;
- [`../../protos/tutorials/`](../../protos/tutorials/) contains small,
  progressive executable programs;
- [`../../protos/examples/`](../../protos/examples/) is a task-oriented
  cookbook for answering "how do I do X in Protos?"

Where practical, guide chapters point to executable tutorial programs rather
than duplicating large source examples that can drift independently.

## Tracked documentation work

The Programming Guide is part of
[`DOC001 — Protos Programming Documentation`](../project/DOC001_PROGRAMMING_DOCUMENTATION.md).

DOC001 tracks this guide as one documentation initiative with independently
auditable slices. A blocker on one chapter does not automatically block unrelated
documentation areas whose semantics and implementation are already closed.

## Current chapters

1. [Bindings, contexts, and object state](01-bindings-contexts-and-state.md)
2. [Objects, delegation, and composition](02-objects-delegation-and-composition.md)
3. [Closures, methods, and receivers](03-closures-methods-and-receivers.md)

## Current guide dependency

The planned control-flow chapter is not published yet, but its implementation
dependency is now closed.

D044 / specification revision `0.1.381` defines the complete standard Closure
`while` protocol, D045 / specification revision `0.1.382` clarifies task-scoped
structured ownership for returned task-backed Futures, and
[`I023 — Standard while protocol`](../project/IMPLEMENTATION_STATUS.md#i023--standard-while-protocol)
has published the reference implementation plus full conformance closure.
[`B007`](../project/IMPLEMENTATION_BLOCKERS.md#b007--standard-while-protocol-semantics)
is CLOSED.

The affected documentation slice `DOC001-E` has therefore been freshly
re-audited to READY. The guide sequence deliberately remains at chapter 03 until
DOC001-E itself is written and published; READY means the chapter may now
describe current runnable behavior, not that the documentation already exists.

## Planned progression

Future chapters should cover, as the guide grows:

- control flow through ordinary protocols;
- values, identity, equality, and collections;
- modules and imports;
- Errors, handlers, `ensure`, and resource lifetime;
- Futures and structured concurrency;
- isolated parallel execution;
- Actors and Actor groups;
- Process, I/O, filesystem capabilities, and authority;
- packages, testing, and the bundled toolchain.

These headings organize explanatory work only. They do not define planned
language behavior or override current implementation status.
