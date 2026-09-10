<p align="center">
  <img src="../assets/branding/protos-logo.png" alt="Protos" width="280">
</p>

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


## Source style reference

[`SOURCE_STYLE.md`](SOURCE_STYLE.md) records the non-normative project convention
for choosing between idiomatic syntactic sugar and an equivalent canonical or
expanded protocol form. Ordinary hand-written source normally uses the idiomatic
surface; the explicit form remains appropriate when the underlying mechanism,
bootstrap boundary, dispatch behavior, or semantic equivalence is the point of
the code.

## Tracked documentation work

The Programming Guide is part of
[`DOC001 — Protos Programming Documentation`](../project/work/DOC001/DOC001_PROGRAMMING_DOCUMENTATION.md).

DOC001 tracks this guide as one documentation initiative with independently
auditable slices. A blocker on one chapter does not automatically block unrelated
documentation areas whose semantics and implementation are already closed.

## Current chapters

1. [Bindings, contexts, and object state](01-bindings-contexts-and-state.md)
2. [Objects, delegation, and composition](02-objects-delegation-and-composition.md)
3. [Closures, methods, and receivers](03-closures-methods-and-receivers.md)
4. [Control flow through ordinary protocols](04-control-flow-through-protocols.md)
5. [Values, identity, equality, and collections](05-values-identity-equality-and-collections.md)
6. [Modules and imports](06-modules-and-imports.md)
7. [Errors, handlers, `ensure`, and resource lifetime](07-errors-handlers-ensure-and-resource-lifetime.md)
8. [Futures and structured concurrency](08-futures-and-structured-concurrency.md)
9. [Isolated parallel execution](09-isolated-parallel-execution.md)
10. [Actors, ActorRefs, and Actor Groups](10-actors-actorrefs-and-groups.md)
11. [Process, I/O, Filesystems, and Authority](11-process-io-filesystems-and-authority.md)

## Current guide state

The control-flow dependency that originally blocked chapter 04 is closed.

D044 / specification revision `0.1.381` defines the complete standard Closure
`while` protocol, D045 / specification revision `0.1.382` clarifies task-scoped
structured ownership for returned task-backed Futures, and
[`I023 — Standard while protocol`](../project/registries/IMPLEMENTATION_STATUS.md#i023--standard-while-protocol)
has published the reference implementation plus full conformance closure.
[`B007`](../project/registries/IMPLEMENTATION_BLOCKERS.md#b007--standard-while-protocol-semantics)
is CLOSED.

`DOC001-E` is CLOSED with
[chapter 04](04-control-flow-through-protocols.md), which explains the published
control-flow behavior as ordinary protocols without redefining the language.

`DOC001-F` is CLOSED with
[chapter 05](05-values-identity-equality-and-collections.md), which explains the
current value-identity/equality model, indexing, Core collection mechanisms, and
their Standard Library extensions.

`DOC001-G` is CLOSED with
[chapter 06](06-modules-and-imports.md), which explains module contexts,
ordinary `import(...)`, canonical ModuleKeys, Actor-local module instances,
cache-before-execute cycles, failure/retry semantics, and the resolver-policy
boundary.

`DOC001-H` is CLOSED with
[chapter 07](07-errors-handlers-ensure-and-resource-lifetime.md), which explains
Error objects, non-resumable signaling, dynamic handlers, unwind-safe `ensure`,
cleanup precedence, and deterministic explicit resource release.

`DOC001-I` is CLOSED with
[chapter 08](08-futures-and-structured-concurrency.md), which explains Future
identity/state and observation, adoption, `then`, deterministic `Future.all`,
cooperative cancellation, task-scoped structured ownership, and `detach()`.

`DOC001-J` is CLOSED with
[chapter 09](09-isolated-parallel-execution.md), which explains explicit P
isolation, Closure projection, argument snapshots, deterministic Array parallel
operations, cooperative cancellation/structured ownership, and controlled
Bytes/ByteRegion writable partitioning.

`DOC001-K` is CLOSED with
[chapter 10](10-actors-actorrefs-and-groups.md), which explains Actor isolation,
ActorRef incarnation identity, explicit snapshot/capability transfer,
send/request/backpressure and acceptance boundaries, lifecycle monitoring,
Actor Group identity, GroupRef routing, and Group/Process lifetime separation.

`DOC001-L` is now CLOSED with
[chapter 11](11-process-io-filesystems-and-authority.md), which explains
bootstrap-local Process authority, args/environment snapshots, independently
optional byte streams and explicit Encoding, TextReader/TextWriter ownership,
I/O Future/commitment/lifecycle rules, structural Path values, confined
Filesystem/File capabilities, namespace mutation, and the current D046/I024
specified-versus-runnable boundary. No independently READY Programming Guide
slice remains: `DOC001-M` is blocked until TOOL001 and TOOL002 close, and
`DOC001-N` remains the final consistency closure.

## Planned progression

Future chapters should cover, as the guide grows:

- packages, testing, and the bundled toolchain.

These headings organize explanatory work only. They do not define planned
language behavior or override current implementation status.
