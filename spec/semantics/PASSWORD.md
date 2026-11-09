# Standard Library Password Storage: Argon2id

Language version: 0.1
Status: Draft
Last updated: 2026-11-09
Decision: D245-C; implementation: I160.

This document is the normative owner of the initial Protos password-storage
module `std:crypto/password`. The D245-C design approval defines its public
semantics; this document does not approve an additional algorithm or grant.
`MODULES.md` owns MODULE identity; `ERRORS.md` owns ordinary Error behavior;
`VALUES_AND_COLLECTIONS.md` owns String and Bytes semantics; `io/ENTROPY.md`
owns explicit secure entropy authority; `concurrency/FUTURES_AND_TASKS.md`
and `concurrency/ACTORS.md` own Future transitions and Actor lifecycle.
`CRYPTO.md` independently owns HMAC-SHA256, not password storage.

## 1. Module and public API

```protos
Password: import("std:crypto/password")
recordFuture: Password.hash(passwordString, entropy)
recordFuture: Password.hashWithSalt(passwordString, saltBytes)
validFuture: Password.verify(passwordString, storedRecordString)
rehashRequired: Password.needsRehash(storedRecordString)
```

The MODULE object exports **exactly** `hash`, `hashWithSalt`, `verify`,
`needsRehash`, and the ordinary `PasswordError` prototype. It is Actor-local
under ordinary MODULE import rules. Importing it provides no entropy, file,
network, clock, Process, foreign-code or provider authority. The private host
bridge is not a public selector, object, permission or accessible module export.

`hash`, `hashWithSalt`, and `verify` return Futures; an ordinary dispatch that
reaches these methods reports operation failures by failing those Futures with
fresh `PasswordError` occurrences. Invocation/lookup/arity errors before
successful dispatch retain ordinary Protos language semantics. `needsRehash`
returns a Boolean synchronously or signals a fresh `PasswordError`.

## 2. Inputs and Unicode policy

Passwords must be semantic Protos String values. No coercion, trimming,
case folding, implicit conversion, null truncation, locale-specific processing,
or change to the supplied String object is permitted. Before deriving the
password verifier, normalize text to **Unicode NFC** and encode using strict
UTF-8, rejecting ill-formed input instead of silently replacing invalid
sequences. The result may contain at most **65,536 octets** of post-NFC UTF-8.
Implementations must also bound pathological pre-normalization input work;
this is a DoS protection rather than an additional password-equivalence rule.
An empty password is algorithmically admitted; password-strength policy
belongs to the application.

`hashWithSalt` accepts **only actual standard Bytes** containing exactly
**16 octets**. It snapshots the octets before asynchronous admission and does
not retain or mutate the caller's mutable Bytes. A caller-supplied salt must
be independently unpredictable for real password storage; a published fixed
salt is appropriate only for known-answer testing.

`hash` requires the caller to supply an authentic, still-live D243-C `entropy`
capability owned by the same Process and initial RootActor. It requests exactly
16 random salt octets using that capability and then uses the same password
hashing path. Other Actors must request salt from an authorized RootActor
broker; transfer of the entropy capability itself is not permitted. A missing,
retired, forged or differently owned grant never triggers ambient randomness.

## 3. Algorithm and canonical persistent record

The initial creation profile is Argon2id version **19 (0x13)** with
**m=65536 KiB (64 MiB)**, **t=3 iterations**, **p=4 lanes**, a **16-octet salt**,
and a **32-octet tag**. These parameters are not guest-adjustable and no weaker
algorithm or smaller parameter set is an automatic fallback. The primitive
may be provided by a private, validated Java/Native backend; its selection is
not observable as a new Protos API.

The only newly created persistent representation in I160 is:

```text
protos-password-v1:nfc:$argon2id$v=19$m=65536,t=3,p=4$<salt>$<tag>
```

The outer `protos-password-v1:nfc:` envelope identifies the text preprocessing
policy; the inner core follows PHC-style Argon2id fields. Both binary fields
use canonical **standard unpadded Base64** (`A-Z`, `a-z`, `0-9`, `+`, `/`):
16 salt octets encode to 22 characters and 32 tag octets to 43 characters.
The complete initial record contains exactly **120 ASCII characters**.
Canonical encoding requires zero unused trailing bits, no `=` padding, no
whitespace and no unexpected fields. The initial grammar fixes algorithm,
version, parameter names, order, exact decimal spelling and values. Duplicate
parameters, leading zeros, overflows, alternate algorithm identifiers and
modified costs are invalid rather than an invitation to allocate more memory.

The parser checks the bounded record structure **before** any Argon2id
calculation. It does not guess or repair malformed input, accept an
unversioned PHC-only record, or silently import a legacy password digest.

## 4. Operation results and record inspection

`hash(password, entropy)` and `hashWithSalt(password, salt)` answer a
`Future<String>` containing a complete fresh canonical record on success.
Neither exposes the derived tag as a public operation result, and neither
publishes a partial record.

`verify(password, storedRecord)` parses an exact supported record, derives a
32-octet tag using that record's salt and policy, then answers `Future<Boolean>`.
Only an incorrect password against a **valid, supported** record resolves to
`false`. Malformed/unsupported records, invalid argument domains, unavailable
providers and capacity failure must fail rather than return `false`. Compare
the complete 32-octet tags without secret-dependent early termination; this
is not a promise of constant-time execution across all layers.

`needsRehash(storedRecord)` is cheap, bounded and synchronous. It answers
`false` for the sole initial creation profile. It must reject malformed or
unsupported records rather than treat them as current or ask to rehash them.
Future profile registration requires a separate normative revision. When a
future explicitly supported profile differs from the creation profile,
`needsRehash` may answer `true`; no such older profile is registered by I160.
An application updates its stored record only after successful verification,
with concurrency controls to avoid overwriting a newer password change.

## 5. Errors and failure channel

The module's `PasswordError` is an ordinary exported Standard Library Error
prototype; each error occurrence has its own stable String slot `kind`:

| `kind` | Meaning |
|---|---|
| `invalidArgument` | Incorrect password, salt or record argument domain, or input bound |
| `invalidRecord` | Malformed spelling or fields within an otherwise recognized record family |
| `unsupportedRecord` | Unrecognized profile or record format not admitted for verification |
| `capacity` | Bounded KDF executor cannot admit further work |
| `unavailable` | Process/host/provider is unavailable, retired, or fails while deriving |

Invalid arguments that have reached an operation return failed Futures for
the three asynchronous operations. `needsRehash` signals the same prototype
synchronously. No Java exception, stack trace, provider name, password,
normalized bytes, salt, tag or private diagnostic is part of an error.

## 6. Actor isolation, cancellation and capacity

Expensive Argon2id is performed off the Actor's guest execution turn.
Results are delivered through the original Actor's targeted Future-completion
mechanism; workers never execute guest callbacks, allocate Actor-owned guest
objects, or settle guest Futures directly. Every job belongs to the exact
originating Actor and Process. When cancellation wins before full publication,
no partial success becomes visible. Actor/Process termination retires the
associated work; the host must discard any late result and must not re-enter
a terminated Actor.

The initial private executor is bounded per RuntimeHost to at most **two
simultaneous derivations** and **sixteen queued jobs**. Admission must not
create an unbounded queue of password Strings or spawn unbounded memory-hard
workers. Saturation is `capacity`; a closed or absent host is `unavailable`.
An already-running provider call need not be physically interruptible, but
its eventual result must be discarded when ownership is revoked. Private
byte arrays and intermediate tags receive best-effort clearing; this does
not guarantee physical erasure throughout the JVM, heap copies, JIT or provider.

## 7. Security, deployment and portability limitations

Argon2id reduces offline password-guessing throughput; it does not supply
rate limits, account lockout, password policy, credential reset, account
enumeration defense, secure database storage, session management, JWT, HSM,
pepper custody or compromised-password checking. Applications must implement
these independently. A suitable work factor must be benchmarked on deployment
hardware without quietly lowering the standardized profile.

Neither FIPS certification nor Native Image provider availability follows
from compiling this module. The selected private backend must be verified
against independent Argon2id vectors on JVM and freshly rebuilt Native Image;
unsupported platforms fail closed. This contract provides no universal
constant-time guarantee and no perfect memory zeroization guarantee. Adding
PBKDF2, bcrypt, scrypt, alternate Argon2 profiles or arbitrary KDF settings
requires a separate normative decision.
