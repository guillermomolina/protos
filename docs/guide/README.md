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

## Current guide blocker

The planned control-flow chapter is intentionally not published yet.

While auditing that chapter, the current normative execution owner was found to
name a Closure-based `while` shape without uniquely defining the complete
observable standard protocol needed by independent implementations. The current
reference implementation also exposes no standard `while` selector.

[`B007`](../project/IMPLEMENTATION_BLOCKERS.md#b007--standard-while-protocol-semantics)
records the normative blocker. This guide sequence stops at chapter 03 until
B007's unblock condition is satisfied; it must not fill the gap by guessing
loop semantics or present the specified shape as current runnable behavior.

The affected documentation slice is `DOC001-E`. B007 blocks that control-flow
chapter only; other DOC001 slices may proceed independently when their own
normative and implementation prerequisites are satisfied.

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
