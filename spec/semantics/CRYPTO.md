# Standard Library Cryptography: HMAC-SHA256

Language version: 0.1
Status: Draft
Last updated: 2026-11-08
Decision: D244-C; implementation: I159.

This document is the **normative owner** of the initial ordinary Standard
Library HMAC-SHA256 operation contract. It does not add a Core prototype,
new syntax, Process authority, global symbol, host permission, or general
cryptographic service. `VALUES_AND_COLLECTIONS.md` retains ownership of
standard Bytes semantics; `MODULES.md` owns MODULE import and Actor-local
identity; `ERRORS.md` owns ordinary Error signaling; `ENTROPY.md` owns the
separately granted secure Entropy capability. `ACTORS.md` and
`PARALLEL_EXECUTION.md` own their existing transfer rules.

## 1. Module and exact public surface

The Actor-local ordinary Standard Library MODULE is imported by its exact
module key:

```protos
HMAC: import("std:crypto/hmac_sha256")
tag: HMAC.digest(keyBytes, messageBytes)
valid: HMAC.verify(keyBytes, messageBytes, tag)
```

The module object exports **exactly** `digest` and `verify`. These are
ordinary Protos callable operations with positional signatures
`digest(key, message)` and `verify(key, message, tag)`. No algorithm registry,
key type, `Mac`, `SecretKey`, signature or token API, comparison primitive,
or further public operation is introduced. The exact MODULE's identity is
Actor-local and follows `MODULES.md`; import does not acquire a permission.

Both operations are synchronous and deterministic for their exact input
bytes. They do not perform I/O, request entropy, consult a clock, create a
Task or Future, suspend, or invoke a guest callback. Receiver and argument
expressions follow ordinary Protos evaluation and association rules before
invocation.

## 2. Bytes domains and admission limits

All supplied `key`, `message`, and `tag` values must be **actual standard
Bytes**. A mere object that delegates to Bytes, a copied structural facade,
a foreign Bytes-like value, an Array, String or other type is not accepted.
No String-to-UTF-8 conversion, coercion, lookup of an ambient key, or
user-defined `size`, `at`, `each`, `toString`, `==` or `hash` dispatch occurs
inside these cryptographic operations. The raw indexed octets of actual Bytes
are the sole admitted contents, even if the caller shadows any such slots.

The inclusive admission limits are:

| Operand | Accepted length in octets |
|---|---:|
| `key` | 0..4096 |
| `message` | 0..16777216 (16 MiB) |
| `tag` for `verify` | Any standard Bytes length; only length 32 can match |

The key/message bounds are operation limits, not HMAC's algorithmic limits,
and do not guarantee execution latency or memory usage. The empty key is
algorithmically valid, but is **insecure for authentication**; applications
must supply a sufficiently unpredictable key. A non-32-octet tag is never
accepted as a truncated authentic tag.

Invalid argument domains, wrong invocation receiver/arity and out-of-bounds
key/message lengths signal a **fresh ordinary Error synchronously**, without
publishing a partial result. For `verify`, a well-typed tag of any length
other than 32 returns canonical `false` after the required key/message
validation. A wrong-domain tag signals Error, not false. No malformed
argument is silently repaired.

After admission and before cryptographic calculation, the implementation
obtains private snapshots of the actual input octets. Inputs are not mutated,
retained, or aliased by results. Subsequent caller mutations cannot change
previously returned tags and do not retrospectively alter a completed
verification. There is no callback, reentrancy, or suspension between
snapshot and computation.

## 3. `digest(key, message)`

`digest` computes the **full RFC 2104 HMAC-SHA256**, interoperable with RFC
4231 test vectors, over the exact snapshotted message octets and the
snapshotted key octets. It returns a **fresh, independent, open standard
Bytes** value containing exactly **32 octets**. The result has the canonical
standard Bytes prototype as its parent. Every successful invocation
returns a new Bytes identity even for the same arguments, without exposing
the provider's internal byte array.

HMAC-SHA256 uses SHA-256's 64-octet block size. For a key longer than this
block, first hash the key with SHA-256, then zero-pad the normalized key to
the block size; keys at most 64 octets are zero-padded directly. HMAC applies
the standard distinct inner and outer pads, not `SHA256(key || message)`.
An empty key is treated as an all-zero padded HMAC key; it does **not**
select a different algorithm or return a special value.

No truncated digest, incremental/streaming state, generic algorithm
selector or implicit use of the separately importable unkeyed
`std:crypto/sha256` module is part of this contract.

## 4. `verify(key, message, tag)`

For well-typed key, message and tag, `verify` computes the same **full
32-octet HMAC-SHA256** and returns canonical Boolean `true` iff the supplied
tag is exactly 32 octets and all 32 match. An incorrect 32-octet tag or a
well-typed tag with another length returns canonical `false`. A truncated
prefix is never accepted.

When comparing complete equal-length 32-octet tags, verification **must not
terminate early based on the values of the octets**; the comparison covers
all 32 octets irrespective of the location of a mismatch. This is a
comparison-specific rule, **not** a claim that the entire operation,
admission, allocation, HMAC implementation, JIT/VM, or caller execution
runs in constant time. A timing measurement alone is not proof of the
required comparison behavior.

`false` is never a substitute for provider failure, wrong input domain or
an oversized key/message. Those cases signal Error.

## 5. Private replaceable backend and failure semantics

The initial implementation may use Java 25 JCA `Mac.getInstance("HmacSHA256")`,
`SecretKeySpec` and `MessageDigest.isEqual` behind an **exact-module private
facility**. These are implementation details, not public Protos objects,
guest foreign interop or standard module exports. A replacement backend must
preserve this entire public contract and the RFC octet mapping.

The deferred facility belongs exclusively to the initializing
`std:crypto/hmac_sha256` module, is captured in its implementation's private
lexical environment, and its initializer slot is removed. Neither the
Prelude, Process, ordinary imported clients nor other modules can obtain the
facility through an ordinary import. The implementation must not retain a
shared mutable `Mac`, key snapshot, provider state or operation handle across
calls, Actors or Contexts. Each invocation uses independent computation
state. The facility is not a permission or capability for ambient entropy.

Provider absence, initialization failure, invalid cryptographic output and
other backend failure signal a **fresh ordinary Error** without disclosing
Java/provider diagnostics, secret key material or intermediate bytes.
Failures never answer `false`, fabricated zeros, partial Bytes, another
algorithm, or an insecure pure-Protos fallback. Fatal VM failures are not
converted into ordinary recoverable results.

The backend should wipe **owned temporary host key, message and tag arrays**
where possible when the calculation finishes, including failed calls. This
is exposure reduction, **not** a guarantee that provider-internal copies,
JVM/JIT intermediates, heap dumps, secret-bearing caller Bytes or physical
memory are zeroized.

## 6. Authority, composition and exclusions

The HMAC module receives **caller-supplied** byte sequences and needs no
Filesystem, Network, Resolver, Entropy, Process, clock, external key service,
or host permission. A different, explicitly host-provisioned secure Entropy
capability (D243-C, `../io/ENTROPY.md`) may be used by an authorized
application to obtain unpredictable key Bytes; importing HMAC neither grants
nor transfers that capability. Application key custody and secrecy remain
application responsibilities.

Completed Bytes participate in the existing Actor pass-by-value and isolated
parallel (P) transfer contracts of `ACTORS.md` and
`PARALLEL_EXECUTION.md`. No live provider context, private runtime key state,
or grant is transferred through HMAC; this document does not redefine either
transfer mechanism.

The module does **not** provide key creation, storage, rotation, revocation,
non-exportable key handles, HSM integration, replay prevention, sequence
numbers, authenticated encryption, domain separation, message framing,
password hashing, key derivation, JSON/JWS/JWT signing or validation, streaming
HMAC, or verification of truncated tags. D245 owns password hashing and D246
owns JWT/JWS semantics independently. Applications must define their own
message and replay policies when needed. Such facilities require separate
approved contracts rather than implicit extensions of this HMAC module.

The public contract is identical for a JVM build and an actually rebuilt
Native Image build. Provider reachability during Native Image construction
is a packaging detail; no global registration of all security providers is
implied.
