# Hashing and HMAC-SHA256 in Protos

`std:crypto/sha256` and `std:crypto/hmac_sha256` solve **different** problems.
Both work with explicit raw Bytes, and neither implicitly encodes a String.
The normative owner of HMAC's contract is
[`spec/semantics/CRYPTO.md`](../../../spec/semantics/CRYPTO.md).

## Unkeyed digest versus authentication

- `std:crypto/sha256` provides deterministic, **unkeyed** SHA-256 hashing.
  A digest detects changes to bytes but is not evidence that they came from
  someone who knows a secret; it is not a password hash.
- `std:crypto/hmac_sha256` provides a **keyed** message authentication code:
  only parties who know a suitable secret key should be able to produce a
  matching tag. Its only exports are `digest(key, message)` and
  `verify(key, message, tag)`.

```protos
HMAC: import("std:crypto/hmac_sha256")

// These values are intentionally public demonstration data, not a secret.
key: Encoding.UTF8.encode("Jefe")
message: Encoding.UTF8.encode("what do ya want for nothing?")

tag: HMAC.digest(key, message)
valid: HMAC.verify(key, message, tag)
```

The result `tag` is a **fresh, mutable/open standard Bytes** value of exactly
32 octets. `valid` is the canonical Boolean `true` when the full tag matches,
and `false` for a wrong but well-typed tag, including every tag shorter or
longer than 32 octets. Verification never accepts a truncated prefix. HMAC's
complete 32-octet comparison does not stop early because a byte is wrong;
that does **not** establish all-operation constant-time behavior.

## Text must become Bytes explicitly

The module accepts actual standard `Bytes`, not String, Array, objects that
delegate to Bytes, or foreign byte-like values. Use the appropriate
`Encoding` operation to convert text **before** signing; for example,
`Encoding.UTF8.encode(text)` when the message protocol specifies UTF-8.
Sign the exact transmitted bytes, not an implicit string representation.
If peers normalize, frame or canonicalize data differently, their HMACs
will differ. HMAC does not provide message framing or canonicalization.

## Limits, failure and ownership

Keys contain **0..4096** octets and messages **0..16,777,216** octets
(inclusive). A wrong key/message domain, oversized input, invalid tag domain,
wrong receiver/arity or unavailable cryptographic provider signals a fresh
ordinary `Error` synchronously; provider failure is **not** Boolean `false`.
A standard Bytes tag of length other than 32 answers `false` after valid
key/message admission. Inputs are snapshotted, never altered or retained by
HMAC; changing an input or returned tag later does not modify any earlier
result or any other returned tag.

Empty keys are valid for the HMAC algorithm but **insecure for authentication**.
Choose an adequately unpredictable secret and protect it in application
storage and logs. Applications with an independently authorized D243-C
`entropy` capability can obtain suitable raw key Bytes from that *separate*
host-provisioned resource; the HMAC module neither imports nor grants it.
The availability of the entropy capability in a particular installed build
must be checked separately. Never substitute a hard-coded test-vector key
for an application secret.

A module's private cryptographic backend is not exported, and no permission
is gained on import. The module has no Task, Future, clock or I/O activity.
Ordinary completed Bytes follow the existing Actor and isolated-parallel
copying rules; an HMAC call does not carry its provider state across Actors.
See the [Actors, ActorRefs and groups](../10-actors-actorrefs-and-groups.md) and the normative
`ACTORS.md`/`PARALLEL_EXECUTION.md` owners for their transfer contracts.

## What this facility does not promise

The HMAC module provides no constant-time guarantee for **all** of admission
or computation, no complete physical memory erasure or key zeroization,
no key storage/rotation, no HSM, no streaming, no replay protection,
no JWT/JWS parsing, and no password hashing or password storage policy.
Replay-resistant message formats, secure key custody, password derivation
and token handling require their own explicit application protocols.

Try the runnable [HMAC example](../../../protos/examples/crypto/hmac-sha256.protos)
or the [HMAC tutorial](../../../protos/tutorials/12-system-resources/03-hmac-sha256.protos).
