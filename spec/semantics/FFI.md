# Standard Library FFI R1 (D238-B1-SQL-R1)

## Scope and authority

This document owns the public `std:ffi` R1 contract. It is a Standard Library
MODULE implemented with ordinary Protos objects and Closures over the existing
foreign-value boundary (D188, `VALUES_AND_COLLECTIONS.md`, Foreign Values).
It introduces no Core value family, syntax, privileged prototype, or second
invocation semantics. SQLite connections and statements are outside this contract.

`FFI: import("std:ffi")` is ordinary MODULE import under D242 (`MODULES.md`).
Importing it performs no foreign discovery, native acquisition, library load,
or FFM session initialization, and grants no native authority. Only the host
may authorize native code, through logical library aliases. A library argument
is an already acquired foreign library facade, not a path, String alias,
Boolean, null, Integer address, or ordinary object imitating its members.
A program cannot expand the host's authorization by declaring a descriptor.

The initial native execution profile is trusted in-process execution on Linux
x86-64 LP64, the JVM, JDK 25 and the supported GraalVM 25.5 toolchain. This is
not a portability promise for other ABIs or Native Image plugin loading.
An incorrect ABI signature can corrupt memory or terminate the process.
A C library is not confined by Protos `Filesystem` authority. No recovery from
arbitrary native faults, memory corruption, or process termination is promised.

## Public declarations

The MODULE exports exactly six scalar descriptors (`void`, `i32`, `i64`,
`f64`, `sizeT`, `utf8z`) and six ordinary Closures (`opaque`, `nullable`,
`owned`, `out`, `bind`, `release`). Helpers, descriptor adaptation metadata,
and release dispatch metadata are private and Actor-local. No public descriptor
slot layout is defined. Descriptors are immutable ordinary frozen Protos
objects, not exposed provider classes or records. Repeated reads of one scalar
descriptor in one MODULE instance preserve its identity; distinct scalar
kinds are distinct objects. Ordinary object identity/equality rules apply;
structurally similar objects do not forge declarations.

| Descriptor | Input / result domain |
|---|---|
| `void` | Return only; direct result is `null` |
| `i32` | Exact Integer in -2147483648..2147483647 |
| `i64` | Exact Integer in -9223372036854775808..9223372036854775807 |
| `sizeT` | Exact Integer in 0..18446744073709551615 in the initial LP64 profile |
| `f64` | Float, IEEE binary64; no Integer-to-Float coercion |
| `utf8z` | Input-only String, strict UTF-8 with one trailing NUL; interior NUL and invalid Unicode are rejected |
| opaque | Non-null foreign resource of the declared logical type, library, session and live generation |
| nullable opaque | The same opaque domain plus `null` |
| owned opaque | Result-only resource acquisition with declared destructor and provider custody; native NULL becomes `null` |

Native integral results become exact Protos Integers, including unsigned
`sizeT` values above the signed 64-bit range. Inputs are never rounded,
truncated, wrapped, parsed from text, or implicitly numerically coerced.

`FFI.opaque(library, nativeTypeName)` declares a logical opaque type associated
with that exact library facade and original session. The name is a String
C identifier (ASCII letter or underscore followed by ASCII letters, digits or
underscores, at most 128 characters). No native address can be constructed.

`FFI.nullable(opaqueDescriptor)` accepts only a plain opaque declaration and
answers a nullable declaration retaining the same library, session and type.
It does not make scalars, owned declarations, or output positions nullable.

`FFI.owned(opaqueDescriptor, releaseSymbol, releaseReturnDescriptor)` accepts
a plain opaque declaration, a String C identifier for a destructor, and
`void` or `i32` as destructor return descriptor. The destructor takes exactly
one reference of that opaque type. Declaration resolves the destructor under
the original library authority; it does not itself acquire a resource.

`FFI.out(descriptor)` declares a zero-initialized native output slot. Supported
targets are `i32`, `i64`, `f64`, `sizeT`, opaque, nullable opaque, and owned
opaque declarations. `void`, `utf8z`, nested `out`, and non-descriptors are
rejected. An output position consumes no Protos invocation argument.

## Binding and invocation

`FFI.bind(library, symbol, returnDescriptor, argumentDescriptors)` accepts an
authorized library facade, a String C identifier, a supported return descriptor,
and an ordinary Array of parameter descriptors. Foreign indexed containers
and objects imitating Arrays are not Arrays. The Array sequence is shallowly
copied before retaining a signature or Closure; later mutations do not change
the binding. Library-associated descriptors cannot silently migrate to another
library or session. A return is scalar except `utf8z`, opaque, nullable opaque,
or owned opaque. Parameters are non-void input scalars, opaque, nullable opaque,
or supported `out` positions; owned declarations are not direct input positions.

The result is a new ordinary Closure capturing the native binding and private
adaptation metadata. Repeated declarations may reuse a provider's cached native
target; they do not share public Closure identity. The normal evaluation order,
dynamic `call` selection, receiver binding, Errors and control semantics of
`CALLABLES.md` apply unchanged. Invocation forwards the exact supplied positional
vector in order. Admission checks arity, scalar type/range, nullability, text
encoding, resource type, library, original session, generation and liveness
before entering native code. There is no callback or implicit suspension.
Calls are synchronous; blocking C code delays its Actor and session shutdown.

With no outputs, the admitted scalar or opaque reference is returned directly;
`void` returns `null`. With outputs, each successful call answers a fresh,
independent ordinary open Array `[nativeReturn, out0, out1, ...]`, in ABI
position order, including a `null` first element for a void return. The provider's
private indexed result is read by index and never presented as a public Array.
Array copying is shallow: opaque elements retain their admitted identities.
Nonzero or negative C status codes remain data; the library does not interpret
them as Protos failures.

Public operation argument-domain failures and provider admission refusals are
ordinary Errors before native entry. A failure of an entered foreign operation
(such as symbol resolution or native result conversion) is a fresh `ForeignError`
under D188, with only its existing safe payload; no new Error prototype is added.
The public adapter does not reinterpret or swallow those failures.

## Custody, identity and release

Every non-null owned acquisition enters provider custody before publication,
whether delivered directly or through `out`, even when C also returns an error
status. All owned carriers of a call are adopted before fallible result
conversion. If conversion fails before publication, provider custody still
retains those acquisitions for session shutdown.

Published owned handles have a private identity-based association with their
originating library in the Actor-local `std:ffi` MODULE. That map is dispatch
metadata only, not resource custody. Borrowed or non-owned references are not
registered as owned. Handles remain the existing foreign references; their
native admission rules are not replaced by public wrappers.

`FFI.release(ownedHandle)` requires an active registered owned handle of this
MODULE instance; arbitrary objects, unregistered references and duplicate
release attempts are rejected. It removes the dispatch association before
calling the original library's release. The provider irrevocably retires the
resource before invoking its destructor. The exact native destructor result
is returned, including an error status. Neither an exception nor a non-success
status permits a second destructor attempt. Session custody releases remaining
owned resources on shutdown; repeated close does not double-destroy them.
Released handles cannot be used again. Identity belongs to one acquisition in
one session/generation, never a numeric native address; address reuse does not
revive retired references or make new acquisitions identical.

## Borrowed memory and isolation

Borrowed native memory must be copied within its accredited lifetime window.
The existing private provider mechanism uses `borrowed` declarations,
`preserving` signatures, bounded `copy` (at most 16777216 bytes), and epoch
revocation. A validated non-preserving native call or destructor invalidates
earlier regions before native entry; a rejected pre-entry call does not.
Session close revokes all such regions. A preserving declaration is a trusted
ABI assertion, not a physical proof of native memory lifetime. A copy is
independent of subsequent native mutations.

R1 defines no public `FFI.borrowed`, `FFI.preserving`, `FFI.copy`, or equivalent
memory-reading method. Consequently public `bind` accepts no borrowed-memory
descriptor and publishes no borrowed-memory wrapper with an extended lifetime
promise. The private mechanism remains available for the separately owned
LIB029 adaptation; it does not authorize a new public method in this slice.

Live native sessions, library authority, bindings capturing such authority,
resources and borrowed regions have no automatic Actor or P transfer contract
(`../concurrency/ACTORS.md`, `../concurrency/PARALLEL_EXECUTION.md`). Importing
`std:ffi` in another Actor creates its ordinary local MODULE instance, not
inherited or delegated authority. No native call implicitly spawns work,
suspends, or runs in parallel. Ordinary transferred scalar results and ordinary
Arrays follow their existing transfer rules; containing a live resource does
not make an otherwise forbidden transfer valid.

## Exclusions

R1 excludes numeric pointers, pointer arithmetic, C structures, callbacks and
upcalls, variadic functions, C++, arbitrary library paths, `SQLITE_TRANSIENT`,
a mandatory binding generator, external plugin execution in Native Image,
and a second semantics of invocation. It defines no SQLite connection,
statement, TEXT/BLOB input, transaction or row-mapping API (LIB029).
