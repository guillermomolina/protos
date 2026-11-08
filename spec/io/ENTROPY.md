# Protos Secure Entropy v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-11-08

This document is the primary normative owner of the D243-C explicit secure
entropy capability. `PROCESS_IO.md` owns its RootActor bootstrap placement;
`IO_CORE.md` owns cross-cutting Future-returning I/O operations, commitment,
cancellation, error categories and `Closable` lifecycle; `ACTORS.md` and
`PARALLEL_EXECUTION.md` own value-transfer boundaries. This document does not
create a separate Future model, I/O scheduler or general cryptography API.

## 1. Capability and authority

Secure entropy is a separately host-provisioned, live resource capability. The
host may grant one concrete `Entropy` capability to the initial RootActor module
of a Process, exposing it under the exact local name `entropy`. The grant is
optional and explicit: without it, the slot is **absent**, not present with
`null` as its value. Possession of the grant authorizes precisely the operations
of that grant; it does not authorize any other host capability.

The frozen standard `Entropy` protocol prototype is ordinary, authority-free
Protos state delegating to `Object`. Its methods are `bytes` and `close`. The
prototype itself does not mint grants; constructing an ordinary child, obtaining
its methods, or imitating its structure is not equivalent to possessing a
authentic host-granted capability. No public `Entropy` constructor, importable
secure random generator, grant registry or ambient `entropy` binding is added.

`entropy` is installed before the first expression of the initial RootActor
SOURCE or MODULE, including the message-service entry. It is local to that
initial module's private execution context, not a prelude binding, module
export, imported module's local slot or automatically inherited Actor state.
The `Process` capability does not expose or recover Entropy authority.

Entropy is independent of Process, Filesystem, Network, Resolver, socket and
thread authorization, source/package loading, inspection, foreign interop and
the choice between direct and RootActor message-service bootstrap modes. No
combination of those permissions implies Entropy. The host's independent
explicit grant is required in each Process or embedding Context.

## 2. Public protocol

```protos
entropy.bytes(count)  // Future<Bytes>
entropy.close()        // Future<entropy>
```

Every successfully dispatched invocation returns a fresh ordinary Future.
These methods are owned by the standard `Entropy` protocol and authenticate
the receiver's actual grant, not merely its shape or delegation ancestry.
Ordinary callable argument association rules still apply before operation
semantics; there is no new named-argument or dispatch mechanism.

`bytes` requires exactly one semantic `Integer` argument in the inclusive
range `0..65536`. No coercion from Float, String, Boolean or another value is
allowed. Missing, surplus, non-Integer, negative and greater-than-65536 counts
return a failed Future carrying a fresh `InvalidIOArgument` occurrence, without
starting random generation or exposing partial output. An invalid receiver
gains no authority and cannot trigger secure generation.

On success, `bytes(count)` resolves to exactly `count` cryptographically secure
octets in a **new, open, mutable, independent `Bytes`** value with the canonical
Bytes prototype. Each octet is an Integer in `0..255`. This applies to zero:
`bytes(0)` resolves to a fresh empty Bytes and performs no random-generator
request. Distinct successful operations never share mutable result storage.
The resulting Bytes may be modified through the standard Bytes protocol;
there is no automatically frozen or secret-specific Bytes subtype.

An unavailable, rejected or failing secure provider causes an `IOError` in the
operation's failed Future. Incorrect or incomplete provider output is a failure,
never a short, zero-padded, reused or partially published Bytes result. No
provider diagnostics or partial secret bytes are part of the portable result.

`close()` accepts zero arguments and has the ordinary idempotent `Closable`
semantics. It returns a fresh Future resolving on success to the exact receiver
`entropy`. A call with arguments fails with fresh `InvalidIOArgument`.
The first valid `close` invocation irreversibly begins that grant's closure;
followers have separate Future identities but observe the same logical close
outcome, without repeating release or re-opening the grant. A `bytes` call
after close cutover returns a Future failed with fresh `IOLifecycleError`.
Closure affects only this grant, never independent grants or the host's
cryptographic subsystem.

## 3. Asynchrony, commitment and cancellation

`bytes` returns its Future without waiting for generation. The potentially
blocking secure provider does not execute inside the invoking Actor turn.
Successful observation of a pending Future suspends only according to the
ordinary Future/Task rules; there is no implicit suspension in non-Task
RootActor bootstrap or in an unsuspendible host entry.

The operation's **commitment point** is the atomic publication of one complete,
independently owned Bytes result. Before that point a successful cancellation
suppresses publication; no partial Bytes becomes visible. Cancellation neither
rewinds nor promises to preserve the private internal state of a secure random
provider, and does not require reversing any internal generation already run.
Once committed, cancellation and close cannot rewrite that success.

An admitted operation cut off by close before commitment fails with
`IOLifecycleError`, under `IO_CORE.md`'s lifecycle ordering. A late callback,
duplicate completion, or completion after cancellation or termination cannot
resurrect the operation, disclose partial data or execute guest code in a dead
Actor. Mandatory residual host cleanup remains under host/runtime custody;
no further guest callback is guaranteed after Process/Actor termination.

`IO_CORE.md` §§2, 7 and 8 and `FUTURES_AND_TASKS.md` own the detailed
admission, cancellation, commitment, terminal-state, idempotent close and
fresh-Future rules. The Entropy contract adds no special Future state or hidden
Actor scheduler.

## 4. Isolation and transfer

A concrete Entropy grant, or any ordinary object graph whose local-slot or
delegation-parent reachability includes that grant, is **not Actor
transferable**. `ActorRef.send`, `ActorRef.request`, request replies and
`Actor.spawn` initialization use the existing atomic `NonTransferableValue`
rule. `Actor.spawn` does not enjoy a privileged Entropy-delegation exception.
No child Actor or imported module receives Entropy merely by being created by
or imported into an authorized RootActor.

Entropy grants are also **not P-transferable**. Captures, inputs or output
graphs carrying such authority fail with `NonParallelValue` under the
`PARALLEL_EXECUTION.md` boundary. No isolated parallel computation inherits
or acquires Entropy automatically.

Completed Bytes are ordinary value data, not a grant. They may cross Actor
and P boundaries under the standard value-copying rules, preserving logical
content but without sharing mutable byte-buffer identity. Possessing a copied
Bytes value confers no ability to request further entropy.

An authorized Actor may provide a **broker** behavior: accept an ordinary
message specifying a bounded count, invoke its own `entropy.bytes(count)` and
reply with the completed Bytes. Other Actors receive only an `ActorRef` or
`GroupRef` communication capability and copied result data. They do not receive
or delegate the Entropy grant.

The `initialization` payload of `std:http/Host.owningWithFactory` crosses into
Workers through ordinary Actor transfer. A broker reference can be passed in
that payload; the Entropy grant cannot. This does not define an HTTP-specific
permission, an alternate spawn transfer, or automatic Worker entropy access.

## 5. Lifetime, failure isolation and security assumptions

Each grant belongs to at most one Process incarnation and its provisioning
host. Closing its lifecycle, Process termination or host closure revokes its
usable authority; after termination no new Protos operation can recover it.
The host may finish or discard already initiated work privately without
restoring a withdrawn grant. Terminating one Process or closing one Context
does not revoke an independently provisioned grant in another Process or
Context. Grant identity and the mutable state of an active provider are not
shared accidentally across independent grants.

Security assumes a trusted, securely initialized cryptographically secure
random-number generator supplied by the host operating system/provider.
Portable Protos defines the bytes-and-authority contract, **not** a particular
Java class, native API, algorithm, entropy source, blocking strategy, thread
count, queue size or provider selection mechanism. JVM and Native executions
must observe the same language semantics. A frozen deterministic generator
state captured during Native Image build, fixed or predictable seeding, silent
fallback to a non-cryptographic PRNG and recycling older byte sequences are
forbidden by this contract.

CSPRNG use does not guarantee mathematical uniqueness of independently
generated samples, absolute unpredictability against a compromised host, a
particular entropy-bit count, or secure zeroization of returned mutable Bytes.
Applications must protect any secret material they retain, transmit or log.
The optional private bounds on outstanding generation are not a public quota
policy or a source of additional authority; provider failures fail closed.

This revision does **not** define deterministic PRNG APIs, public entropy
providers, HSM registries, direct grant delegation/attenuation, HMAC, password
hashing, signing, JWT, token storage or key-management policy. Those features
require separate normative decisions.
