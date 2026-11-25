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

### Host native library authorization (D259-B2+A)

Native library authorization is a host-supplied table mapping logical library
aliases to native targets. Only the host supplies it; no Protos program,
import, descriptor, or module can create, read, or widen it. The empty table
authorizes no library. The presence of a native foreign provider never
authorizes any library by itself; in particular, it does not authorize
`sqlite3`.

`import("ffi:sqlite3")` names the logical alias `sqlite3`. An import never
selects a file, a path, or a native name, and never widens authorization.

A library alias:

- is case-sensitive;
- matches `[A-Za-z_][A-Za-z0-9_]{0,127}`; and
- authorizes nothing unless the host table explicitly contains it.

An alias is a logical identifier only. It is never interpreted as a file
path or as a native load name.

An import of a syntactically valid alias that is not in the table fails with
`ForeignError` category `unauthorized-library`. A syntactically invalid alias
never reaches native acquisition; it follows ordinary import resolution
(`MODULES.md`). An import of an authorized alias whose target cannot be loaded
fails with `ForeignError` category `library-unavailable`.

For the acquisition of that library by `protos-ffm`, alias authorization is
checked before the FFM provider creates any Linker or Arena for that
acquisition and before that library is loaded. This rule does not guarantee
that no other provider, Actor, or trusted Java code has previously executed
native code. A logical provider session may exist
without constituting a native load; this contract does not promise that a
denied import creates no logical provider session.

A native target is exactly one of:

- an exact load name, such as `libsqlite3.so.0`, which Protos passes unchanged
  to the operating-system library loader; or
- an absolute path supplied by the host, which Protos passes unchanged as an
  absolute path.

Protos searches no additional directories, derives no library names, adds no
prefixes or suffixes, and selects no alternative versions. Protos constructs
no relative paths and performs no fallback search; relative paths are not
valid targets. The operating-system loader may apply its own search rules,
dependency loading, symbolic-link resolution, and native initializers; those
effects are part of the risk the host accepts by authorizing the library. An
authorized target is not promised to identify cryptographically, or otherwise,
one immutable file.

Ordinary Protos permissions (`Filesystem`, `Network`, `Resolver`, `Entropy`,
`Clock`, external execution) remain independent of the table: authorizing a
library grants none of them, and none of them authorizes a library.

An explicitly authorized native library executes `TRUSTED_IN_PROCESS`: it may
produce every effect the operating-system process may produce, including I/O,
blocking, faults, and process termination. Protos MODULEs, descriptors, and
managed foreign resources keep their existing identity, custody, and transfer
rules. The internal global state of a trusted C library receives no isolation
guarantee among Actors, Contexts, or Protos Processes that share one
operating-system process (`../concurrency/ACTORS.md` §24K).

Library loading remains lazy, importing `std:ffi` still acquires no native
resource, and the R1 ABI, existing error categories, and synchronous call
semantics are unchanged. This contract adds no public `std:ffi` API.

#### Embedding configuration

An embedding host supplies the table through the existing `protos-ffm`
foreign provider, as immutable provider options of the form
`library.<alias> = <target>`. For example:

```text
library.sqlite3 = libsqlite3.so.0
library.zlib    = /usr/lib/x86_64-linux-gnu/libz.so.1
```

authorizes exactly the aliases `sqlite3` and `zlib`. Without such options the
table is empty and no library is authorized. This adds no SPI and no public
Protos API.

## Public declarations

The MODULE exports exactly thirteen scalar descriptors (`void`, `i8`, `u8`,
`i16`, `u16`, `i32`, `u32`, `i64`, `u64`, `f32`, `f64`, `sizeT`, `utf8z`)
and nineteen ordinary Closures (`opaque`, `nullable`, `owned`, `out`, `bind`,
`bindPreserving`, `release`, `field`, `struct`, `union`, `array`, `pointer`,
`borrowedPointer`, `addressOf`, `sizeOf`, `alignOf`, `offsetOf`, `prototype`,
`layoutOf`). Helpers, descriptor adaptation metadata, layout metadata, the
pointee-to-provider pointer descriptor map and release dispatch metadata are
private and Actor-local. No public descriptor
slot layout is defined. Descriptors are immutable ordinary frozen Protos
objects, not exposed provider classes or records. Repeated reads of one scalar
descriptor in one MODULE instance preserve its identity; distinct scalar
kinds are distinct objects. Ordinary object identity/equality rules apply;
structurally similar objects do not forge declarations.

| Descriptor | Input / result domain |
|---|---|
| `void` | Return only; direct result is `null` |
| `i8` | Exact Integer in -128..127 |
| `u8` | Exact Integer in 0..255 |
| `i16` | Exact Integer in -32768..32767 |
| `u16` | Exact Integer in 0..65535 |
| `i32` | Exact Integer in -2147483648..2147483647 |
| `u32` | Exact Integer in 0..4294967295 |
| `i64` | Exact Integer in -9223372036854775808..9223372036854775807 |
| `u64` | Exact Integer in 0..18446744073709551615 |
| `sizeT` | Exact Integer in 0..18446744073709551615 in the initial LP64 profile |
| `f32` | Float input rounded once to IEEE binary32; result is the exact Float value of the binary32 result; no Integer-to-Float coercion |
| `f64` | Float, IEEE binary64; no Integer-to-Float coercion |
| `utf8z` | Input-only String, strict UTF-8 with one trailing NUL; interior NUL and invalid Unicode are rejected |
| opaque | Non-null foreign resource of the declared logical type, library, session and live generation |
| nullable opaque | The same opaque domain plus `null` |
| owned opaque | Result-only resource acquisition with declared destructor and provider custody; native NULL becomes `null` |
| typed pointer | Live pass-only pointer capability of a compatible pointee (see Typed native pointer capabilities); native NULL result is a `ForeignError` |
| nullable typed pointer | The same domain plus `null`; native NULL result becomes `null` |
| borrowed typed pointer | Return and `out` only; non-null pointer capability valid until the borrow epoch advances; native NULL result is a `ForeignError` |
| struct or union layout | By value: an input is a live native region of that exact layout identity, copied for the call; a return or `out` result is a fresh independently owned allocation root (see By-value struct and union invocation) |

Native integral results become exact Protos Integers, including unsigned
`u64` and `sizeT` values above the signed 64-bit range; an unsigned result is
never negative. Integer inputs are never rounded, truncated, wrapped, parsed
from text, or implicitly numerically coerced: a signed or unsigned descriptor
admits exactly its stated range, and a negative Integer is never admitted for
an unsigned descriptor. A Float is never admitted for an Integer descriptor
and an Integer is never admitted for `f32` or `f64`.

`f32` is the only descriptor that rounds. The binary64 Float input is rounded
once to binary32 according to IEEE 754 roundTiesToEven, including its
specified overflow threshold. Finite values above the largest finite binary32
value may round to that largest finite value or to an infinity of the same
sign according to this rounding rule. The sign of zero is kept, values below
the binary32 normal range round to binary32 subnormals or to a zero of the
same sign, infinities are kept, and NaN remains the canonical semantic NaN.
A binary32 result or output is widened exactly to a Float. Protos acquires no binary32 value
family: the semantic value is always a Float.

The physical carrier of a scalar is fixed by its width (8, 16, 32 or 64 bits
for the integral descriptors; binary32 or binary64 for `f32` and `f64`).
Signedness is semantic: a signed and an unsigned descriptor of the same width
share one physical ABI carrier, which holds the two's-complement bit pattern
of the exact admitted value.

`FFI.opaque(library, nativeTypeName)` declares a logical opaque type associated
with that exact library facade and original session. The name is a String
C identifier (ASCII letter or underscore followed by ASCII letters, digits or
underscores, at most 128 characters). No native address can be constructed.

`FFI.nullable(descriptor)` accepts a plain opaque declaration or a plain,
non-nullable typed pointer descriptor (see Declarative layouts). For an opaque
declaration it answers a nullable declaration retaining the same library,
session and type. For a typed pointer it answers a fresh frozen nullable
typed pointer descriptor retaining the exact pointer descriptor identity.
It does not make scalars, layouts other than typed pointers, owned
declarations, output positions or nullable declarations nullable.

`FFI.owned(opaqueDescriptor, releaseSymbol, releaseReturnDescriptor)` accepts
a plain opaque declaration, a String C identifier for a destructor, and
`void` or `i32` as destructor return descriptor. The destructor takes exactly
one reference of that opaque type. Declaration resolves the destructor under
the original library authority; it does not itself acquire a resource.

`FFI.out(descriptor)` declares a zero-initialized native output slot. Supported
targets are the integral scalars, `f32`, `f64`, opaque, nullable opaque,
owned opaque, typed pointer, nullable typed pointer, borrowed typed pointer,
struct and union declarations. `void`, `utf8z`, nested `out`, field and fixed
array descriptors, and non-descriptors are rejected. An output position
consumes no Protos invocation argument. Declaring an output position is
declarative: it acquires no provider session and no library, even for a
struct or union target, and an output descriptor is not a storage layout.

## Declarative layouts

Layout descriptors describe native data under the Linux x86-64 LP64 profile.
They are pure, immutable Protos declarations: declaring or querying one
acquires no library, opens no foreign provider session, enters no native code,
allocates no native memory and carries no native address. Every declaration
answers a fresh frozen ordinary object associated with private immutable
metadata by exact identity; structurally similar objects do not forge one.
Declarative layout capability is distinct from native memory and from
invocation: storage described by a layout is allocated and accessed only as
specified in Native memory, and struct and union layouts are passed and
returned by value only as specified in By-value struct and union invocation.

A *storage layout* is one of the integral scalars, `f32`, `f64`, a struct, a
union, a fixed array, a typed pointer or a nullable typed pointer. `void`,
`utf8z`, `out`, opaque, nullable opaque, owned and field descriptors are not
storage layouts.

| Storage layout | Size | Alignment |
|---|---:|---:|
| `i8`, `u8` | 1 | 1 |
| `i16`, `u16` | 2 | 2 |
| `i32`, `u32`, `f32` | 4 | 4 |
| `i64`, `u64`, `sizeT`, `f64` | 8 | 8 |
| typed pointer, nullable typed pointer | 8 | 8 |

Layout sizes and offsets are exact Integers in the signed-addressable domain
0..9223372036854775807. Every size and offset computation is performed with
exact Integer arithmetic and checked against that bound before it becomes
layout metadata; a declaration whose intermediate or final size or offset
would exceed it is rejected with an ordinary Error. Rounding an offset up to
an alignment adds `(alignment - (offset mod alignment)) mod alignment`.

`FFI.field(name, layout)` requires exactly two arguments: a String name and a
storage layout. Every String is a valid field name, including the empty
String, whitespace, punctuation, dots, reserved words and non-identifiers; no
C identifier rule applies. It answers a fresh field declaration.

`FFI.struct(fields)` and `FFI.union(fields)` require exactly one ordinary Array
of at least one field declaration. The Array is shallowly snapshotted before
use; later mutation does not change the declaration. Field names must be
pairwise distinct under String equality; a duplicate is rejected.

- A struct lays out its fields in source order. For each field, the occupied
  size is rounded up to the field's alignment, that value is the field's
  offset, and the field's size is added. The struct's alignment is the largest
  field alignment, and its size is the occupied size rounded up to that
  alignment, which includes internal and tail padding.
- A union places every field at offset zero. Its alignment is the largest
  field alignment and its size is the largest field size rounded up to that
  alignment. A union has no active-member tag and no member is selected or
  inferred.

`FFI.array(element, count)` requires exactly two arguments: a storage layout
and an exact positive Integer count. Its stride is the element size, its size
is `count` times the stride and its alignment is the element alignment. It
retains the exact element descriptor identity and count; its metadata does not
grow with the count.

`FFI.pointer(pointee)` accepts a storage layout or `FFI.void` and answers a
typed data pointer descriptor retaining the exact pointee identity. A pointer
to `void` has no pointee size. `FFI.nullable(pointer)` answers a nullable
typed pointer retaining the exact pointer and pointee identities. Nullable and
non-nullable typed pointers have identical size, alignment and array stride,
and both may be aggregate members, array elements and pointee layouts;
nullability affects only the admission of native values, not physical
storage. A nullable typed pointer is distinct from the nominal R1 nullable
opaque declaration, which is not a storage layout. Both typed pointer kinds are
also invocation descriptors (see Typed native pointer capabilities); neither
defines an address, dereference, pointer arithmetic or memory access.

`FFI.borrowedPointer(pointer)` requires exactly one plain, non-nullable typed
pointer descriptor and answers a fresh frozen borrowed typed pointer
declaration retaining the exact pointer and pointee identities. It is a
return and `out` declaration only, never a storage layout: it is rejected by
`sizeOf`, `alignOf`, `offsetOf`, `field`, `array`, `pointer`, `nullable` and
`prototype`, and as a direct parameter. A nullable or already borrowed
pointer, any other descriptor and any non-descriptor are rejected. Declaration
requires no native provider.

`FFI.sizeOf(layout)` and `FFI.alignOf(layout)` answer the exact size and
alignment of a storage layout. `FFI.offsetOf(layout, path)` answers the byte
offset reached from a storage layout by an ordinary Array path, which is
shallowly snapshotted. An empty path answers zero. At a struct or union, the
selector must be a String naming one of its fields, and the field offset is
added; a String is always one selector, so a dot does not separate selectors.
At a fixed array, the selector must be an exact Integer in `0..count-1`, and
the index times the stride is added. Any other selector kind, an unknown
field, an index out of range, or a selector applied at a scalar or at a typed
pointer is rejected; a pointer is never dereferenced.

Every rejected layout declaration or query is an ordinary Error.

The integral scalar storage layouts, `f32` and `f64` are the scalar
descriptors of Public declarations and are admitted by `bind`,
`bindPreserving` and `out` as described there; typed pointer declarations are
admitted in the positions of Typed native pointer capabilities; struct and
union layouts are admitted as by-value parameters, returns and `out` targets
(By-value struct and union invocation). Fixed array and field descriptors are
not invocation descriptors: used as a direct return, parameter or `out` target
they are rejected with an ordinary Error before any provider signature is
composed and before native entry. A fixed array is passed only as storage
nested inside a struct or union. An aggregate is never silently passed as a
pointer.

### Prototype association

A layout describes immutable native ABI storage; an ordinary Protos prototype
describes Protos behavior. They remain separate objects, and an association
between them is Actor-local state of one `std:ffi` MODULE instance.

`FFI.prototype(layout, ordinaryPrototype)` requires exactly two arguments: a
storage layout and an ordinary object. Specialized values (`null`, Booleans,
numbers, Strings, collections, Closures, Bytes and other specialized values)
are not ordinary objects; an ordinary object is admitted whatever its
delegation parent, local slots, or open, closed or frozen state. The
association maps the exact prototype identity to the exact layout descriptor
identity. Registering the same layout identity again for the same prototype is
idempotent; a different layout identity, even one with identical size,
alignment and fields, is rejected with an ordinary Error and leaves the
existing association unchanged. The operation answers the exact supplied
prototype.

`FFI.layoutOf(prototype)` requires exactly one argument and answers the exact
layout descriptor associated with that exact prototype identity. An absent
association is an ordinary Error; no delegation parent or descendant is
consulted and no layout is inferred from structure.

Neither operation mutates, freezes, closes, constructs, initializes or sends
any message to the prototype, and neither uses its equality or hash. Ordinary
`call`, `construct` and `init` behavior of the prototype is unchanged.
Association requires no native authority, library or foreign provider
session. Another Actor's `std:ffi` MODULE instance has its own, independent
associations.

### Not yet implemented

The following capabilities are not provided by this revision. They are not
rejected design directions: passing or returning a C array directly by value,
and projection (snapshot conversion) of native aggregates to or from ordinary
Protos Maps or Objects. Struct and union by-value invocation is specified in
By-value struct and union invocation, and native memory in Native memory
below.



## Binding and invocation

`FFI.bind(library, symbol, returnDescriptor, argumentDescriptors)` accepts an
authorized library facade, a String C identifier, a supported return descriptor,
and an ordinary Array of parameter descriptors. Foreign indexed containers
and objects imitating Arrays are not Arrays. The Array sequence is shallowly
copied before retaining a signature or Closure; later mutations do not change
the binding. Library-associated descriptors cannot silently migrate to another
library or session. A return is scalar except `utf8z`, opaque, nullable opaque,
owned opaque, typed pointer, nullable typed pointer, borrowed typed pointer,
struct or union. Parameters are non-void input scalars, opaque, nullable
opaque, typed pointer, nullable typed pointer, struct, union, or supported
`out` positions; owned and borrowed typed pointer declarations and fixed arrays
are not direct input positions. The complete declaration is validated before
any provider signature is composed.

`FFI.bindPreserving(library, symbol, returnDescriptor, argumentDescriptors)`
has exactly the admission, invocation, result and failure semantics of `bind`,
and additionally declares the trusted ABI assertion that the C function leaves
earlier borrowed native regions valid (see Borrowed memory and isolation).
Each of `bind` and `bindPreserving` answers a fresh ordinary Closure on every
successful declaration.

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

### Typed native pointer capabilities

A typed pointer declaration names an exact pointee layout identity (or
`FFI.void`). Independently constructed pointer declarations of the same
pointee layout are compatible; distinct pointee layouts are never compatible,
whatever their structure, and no implicit conversion between them exists.
Typed pointer declarations are library-neutral within the RootActor's native
session: a pointer obtained through one authorized library may be passed to a
compatible parameter of another authorized library of the same session.

A non-null native result in a typed, nullable typed or borrowed typed pointer
position (return or `out`) becomes a fresh pass-only pointer capability. A
capability is a foreign reference with no readable member, no indexed access,
no readable extent, no destructor and no release operation; `FFI.release`
rejects it. It carries no numeric address: no Integer converts to a pointer
and no pointer converts to an Integer, a String or a hash derived from an
address. A capability's only use is to travel back as an argument. No
ownership is inferred from address equality, symbol spelling or other
results, and a capability is never registered in resource custody.

Native NULL in a nullable typed pointer result position becomes `null`. Native
NULL in a typed or borrowed typed pointer result position fails native result
conversion after entry with a fresh `ForeignError`; owned results of the same
call were already adopted (see Custody, identity and release).

A typed pointer parameter admits only a live capability of the current
session whose pointee identity is the declared one; a `FFI.void` pointer
parameter admits any live capability without dereferencing it, but a
capability whose pointee is `FFI.void` is never admitted by a parameter of
another pointee. A nullable typed pointer parameter also admits `null`; a
non-nullable one does not. Opaque handles, Integers, Strings, descriptors,
ordinary objects and any other value are rejected. Every rejection, including
of an expired or incompatible capability, is an ordinary Error before native
entry.

A capability is admissible only while every condition it retains holds: its
originating session is open; when owner-backed, its original owned resource
is still live; and when borrowed, the session borrow epoch still equals the
epoch reached after the call that produced it. A borrowed capability is
therefore revoked by every later validated call of a binding not declared
preserving, every destructor call, and session close; a rejected call does
not revoke it, and a `bindPreserving` call keeps it. A capability passed to an
admitted non-preserving call is marshalled before that call advances the epoch:
the call receives it and later uses are rejected. Revocation is permanent;
reuse of the same machine address by a later result or acquisition never
restores an earlier capability.

`FFI.addressOf(ownedHandle)` with an active registered owned C handle of this
MODULE instance answers a fresh pass-only capability with
pointee `FFI.void` that retains that original owned resource. The handle keeps
its custody, destructor authority, release dispatch and exact destructor
result; the capability is revoked when the handle is released or its session
ends. Arbitrary values, unregistered or released handles, non-owned references
and pointer capabilities are rejected with an ordinary Error. No other library
is acquired. `FFI.addressOf` of a live native region is specified in Native
memory below.

### By-value struct and union invocation

A struct or union layout is admitted in three positions of `bind` and
`bindPreserving`, with the actual C ABI of the native backend: as a parameter
it is passed by value; as the return it is returned by value; as an `out`
target the C function receives, like every output position, a pointer to its
output storage, which here is a fresh allocation root. The native signature is built from the real physical
group layout of the declaration: member carriers in order, the internal and
tail padding implied by the offsets and size `std:ffi` computed, union members
overlapping at offset zero with padding up to the union's rounded size, and
fixed arrays as nested sequences. A group is never reduced to a pointer, to
its size or alignment, or to a sequence of separate scalar parameters, and
field names never affect the native signature. Physical identity is not
nominal identity: two separately declared layouts with identical physical
structure share a native signature, but neither admits the other's regions.

A struct or union parameter consumes exactly one invocation argument. It
admits only a live native region of the current session whose layout is the
exact declared layout identity, which spans exactly the layout's size at an
address aligned to its alignment: an allocation root, a group result, or a
compatible non-owning typed view or nested region, including a read-only one.
An ordinary Map, Object or Array, `null`, a pointer capability, a byte slice,
a region of a structurally identical but separately declared layout, a region
of another session, a released root and an expired borrowed view are rejected
with an ordinary Error. No ordinary Protos value is implicitly converted to a
native group. Admission completes before anything is copied or allocated.

The admitted region is copied once, completely, into storage private to the
call, before the borrow epoch advances; the callee receives that copy, so a
mutation of its parameter never reaches the source region. The copy is a
shallow physical copy: a pointer member keeps its pointer bits, its pointee is
not copied, and the copy confers no lifetime or extent authority over that
pointee.

A struct or union return, and each struct or union `out` position, answers a
fresh independently owned allocation root of the declared layout. Its storage
is allocated, zero-initialized, by the provider before native entry and
registered in session custody before the call; the C function writes the
result into it, for an `out` through a pointer to that storage. The root is
independent of every argument, of every other result and of the call: it
remains live after the call returns, is unaffected by later borrow epochs, and
is released only by `FFI.release` (exactly as a root answered by
`FFI.allocate`, never by a C destructor) or by session close. The result
convention is unchanged: with outputs, the answer is a fresh ordinary Array
`[nativeReturn, out0, out1, ...]` whose struct and union positions are those
roots; without outputs, the group root is answered directly. Every result
region supports the ordinary native region operations (Native memory). A union
result has overlapping storage and no active member.

Every native resource of a call, group result roots and owned C resources
alike, is in custody before any fallible result conversion. When a later
conversion fails after entry (for example native NULL in a non-nullable
pointer `out`), the call fails with a fresh `ForeignError`, nothing is
published, and the already registered roots and resources remain in custody
until session close, which reclaims each exactly once.

A rejected call (a wrong arity, any inadmissible argument, or a closed
session) is an ordinary Error before native entry: it publishes no result
root, writes no native byte, does not enter C, and does not advance the borrow
epoch. Result storage prepared for a call that fails before entry is released
and never published. An admitted call of a binding not declared preserving
advances the borrow epoch exactly as any other call; a `bindPreserving` call
does not. A borrowed view admitted as a by-value argument is copied before its
own epoch is revoked by that call.

Native C code remains trusted in-process code: a wrong by-value declaration
has the consequences of any wrong ABI signature.

## Native memory

Native memory is bounded, typed native storage of the RootActor's native
provider session. It is described by storage layouts (Declarative layouts),
whose sizes, alignments, member offsets, counts and strides are computed by
the `std:ffi` MODULE; the provider receives them as immutable physical access
metadata, checks their physical consistency, and never computes a layout of
its own.

### Native context and authority

Every operation in this section except argument validation needs the
production native FFM provider to be available to the host and the current
Actor to be the Process's RootActor (RootActor-only native authority). The
first such operation acquires the RootActor's existing provider session, the
same session that `ffi:` imports of that Actor use, and verifies the native
platform. It resolves no library authorization, loads no library and performs
no symbol lookup. Allocation therefore requires an available provider but no
acquired, authorized or loaded C library, and grants no library authority.
Importing `std:ffi`, declaring layouts and querying them still acquire
nothing. Whether a Native Image executable activates its integrated provider
without any host provider option is host policy outside this contract.

A refusal before a native operation is entered, including a non-RootActor
caller, an unavailable provider, a closed session and every argument
rejection below, is an ordinary Error. A failure of an admitted native
operation after entry (for example an allocation the platform cannot satisfy,
or NULL read from a non-nullable pointer field) is a fresh `ForeignError`.

### C type spellings

`FFI.cType(name)` requires exactly one String and answers the exact existing
descriptor singleton for these exact spellings of the LP64 profile: `"int"`
answers `FFI.i32`; `"long"` and `"long long"` answer `FFI.i64`; `"size_t"`
answers `FFI.sizeT`; `"double"` answers `FFI.f64`. Every other String,
including other C types (`"float"`, `"char"`, `"_Bool"`, `"long double"`),
typedef names and spellings with additional or different whitespace or case,
is an ordinary Error, as is a non-String or a wrong arity. A supported
spelling verifies the native platform through the native context; no library
is acquired and no native function is called.

### Native regions

A native region is a foreign reference to bounded native memory of one exact
storage layout or, for a slice, of a byte range. It has no readable member and
no numeric address. Its only access is typed indexed access (Typed access).

`FFI.allocate(layout)` requires exactly one storage layout (a scalar, struct,
union, fixed array or typed pointer layout; not `FFI.void`, `FFI.utf8z`, a
field, a borrowed pointer or an R1 declaration), validated before the native
context is acquired. It allocates zero-initialized native storage, padding
included, of exactly the layout's size and alignment and answers a fresh
allocation root: an independently owned region registered in the session's
resource custody before it is answered. Each allocation is independent of
every other allocation and of every C library. Allocation does not change any
layout identity or association. A struct or union result of a by-value call
(By-value struct and union invocation) is an allocation root of the same kind.

`FFI.slice(region, byteOffset, byteLength)` requires a live region, an exact
nonnegative Integer offset and an exact nonnegative Integer length whose sum
does not exceed the region's size. It answers a non-owning byte view of that
range, indexed by position as `u8` octets. Nothing is copied or allocated: the
view aliases the region's storage, and it never extends the source's extent,
permission or lifetime. A zero length answers an empty view, also at the
exact end of the region; it has no admissible index, cannot be released, and
is revoked with its source like any other view.

A view, slice or nested region retains the effective lifetime of its source
directly (the owning allocation root, or the borrowed pointer or C anchor the
source was derived from), not each intermediate view, so a long chain of
slices keeps only that original lifetime alive.

`FFI.addressOf(region)`, for a live region or view of this MODULE instance's
session, answers a fresh pointer capability whose pointee is the region's
exact layout identity (`FFI.void` for a byte view), whose known extent is the
region's size, which keeps the region's permission and retains the region as
its provenance. It carries no destructor authority and is never released. A
region is never implicitly converted to a pointer argument: passing it to a
bound function requires `FFI.addressOf`.

`FFI.view(pointer, layout)` requires a live pointer capability of the session
whose known extent is at least the layout's size, whose address is aligned to
the layout's alignment, and whose pointee is the layout's exact identity or
`FFI.void`. It answers a borrowed region of that layout over the pointer's
memory, with the pointer's permission and lifetime. A pointer without a known
extent, including every pointer returned by C and every pointer read from a
pointer field, is never viewed: `view` is never an implicit extent assertion.

### Typed access

A region answers `at` and `atPut` through the existing foreign indexed
projection; no field is a foreign member, so a field named `at`, `call`,
`init`, `slotValue`, `createSlot` or `size` is reached only by its String
index and ordinary lookup is unchanged. A struct or union region is indexed by
the exact String name of an existing member; an array region or byte view by
an exact Integer from zero to its count minus one; a scalar or pointer region
by exactly `0`. Any other index is an ordinary Error.

The whole access is admitted before entry: the session, the region's
liveness and provenance, the index, the bounds and native alignment of the
addressed member, and, for `atPut`, write permission and the complete value.
Admission reads and writes no native memory; a rejected access leaves every
byte unchanged and does not change the session's borrow epoch.

Reading a scalar member answers its exact value by the descriptor's existing
conversion (signed and unsigned widths, unsigned 64-bit values beyond the
signed range, `f32` widening with signed zero and NaN). Reading a nested
struct, union or array member answers a non-owning region over that member,
with the source's permission and lifetime. Reading a typed pointer member
answers a fresh pass-only pointer capability that retains the region as
provenance, has no known extent and is admitted as a C-origin pointer by
`assumeExtent`; native NULL answers `null` for a nullable member and is a
`ForeignError` for a non-nullable one. A pointer loaded from native memory
does not prove whether its pointee was allocated by C or by Protos, nor how
long the pointee lives: it keeps only the lifetime of the region it was read
from, which does not guarantee the pointee's lifetime. Giving it an extent
requires an explicit `assumeExtent`; an allocation-root anchor checks the
complete physical range against that allocation, and every C-managed anchor
remains a trusted declaration.

Writing a scalar member admits exactly the values its descriptor admits as an
argument (exact range, no Integer to Float coercion, one binary32 rounding for
`f32`) and performs exactly one typed store. Writing a struct, union or array
member admits only a live region of the same exact layout identity and copies
exactly that many bytes; a structurally equal layout of another identity, a
byte view, null or an ordinary object is rejected, and ordinary Objects are
never interpreted as C aggregates. Writing a pointer member admits a live
pointer capability of the session with the member's pointee identity (any
pointee for a `FFI.void` member) or, for a nullable member, `null`; Integers
are never pointers. Storing a pointer stores its address only: it extends no
lifetime. `atPut` answers the exact supplied value.

### Extent assertions

`FFI.assumeExtent(pointer, byteLength, anchor, access)` requires exactly four
arguments: a live pointer capability of the session, an exact positive
Integer length within the signed-addressable range, an anchor, and exactly
the String `"read"` or `"readWrite"`. It answers a fresh pointer capability
with that known extent; it is neither an allocation nor an owner. The admitted
anchors are exactly:

1. a live allocation root of this MODULE instance, when the complete asserted
   range lies inside that root's actual allocation;
2. a live owned C handle of this MODULE instance, a trusted claim that the
   memory lives as long as that C resource;
3. the pointer itself, when it is a borrowed pointer, a trusted claim
   governed by its borrow epoch; and
4. an authorized C library facade, a trusted claim that the memory is static
   storage of that library. Only an actually acquired native library facade
   of the pointer's own open session is recognized as this anchor; the
   recognition reads no member and enters no foreign code, so another
   provider's facade, a facade of another session, a raw foreign reference
   or an ordinary object with a matching slot is refused before any of its
   members is read.

Any other anchor, including the session context, ordinary objects, Strings,
Integers, pointers other than the asserted one, views, slices and released
roots, is an ordinary Error. Anchors 2 to 4 admit only pointers that do not
originate solely in Protos allocations. An assertion never widens a known
extent, never upgrades a read-only pointer to `"readWrite"`, and never revives
an expired pointer; the result's permission is the intersection of the
pointer's permission and the requested access, and its lifetime is the
conjunction of every condition the pointer already retains with the anchor's
lifetime.

Assertions over C-managed memory (anchors 2 to 4, and pointers read from
native memory) are trusted declarations with the same safety limits as an ABI
signature: they do not prove that the C storage exists, has that extent,
permits writing or lives that long. An incorrect assertion can corrupt memory
or terminate the process.

### Ownership, provenance and revocation

Only an allocation root owns native memory. `FFI.release(root)` requires an
active root of this MODULE instance: it removes the release dispatch, retires
the root before freeing its storage, and answers `null`. A duplicate release,
and the release of a slice, view, nested region, pointer or any other value,
is an ordinary Error. Releasing a root does not advance the session's borrow
epoch and does not affect any other allocation.

Every region and pointer derived from a root, a C resource, a borrowed
pointer or another derived capability retains its source, and it is live
only while the session is open and every retained condition holds. Releasing
a root therefore revokes every view, slice, nested region and pointer derived
from it; releasing an owned C handle revokes everything accredited through it;
a non-preserving call, a destructor call or session close revokes everything
governed by the borrow epoch. Revocation is permanent; reuse of the same
native address never restores an earlier region or pointer. Session close
retires and frees every remaining allocation root together with the remaining
owned C resources, and nothing reopens the session.

### Native Image signature admission

In a Native Image executable, a declaration is admitted only when the exact
physical carrier shape of its signature (return and parameter carriers,
including address carriers for outputs and the complete recursive physical
structure of every by-value struct or union) belongs to the finite set
registered for that image. A valid declaration of any other shape is refused
with an ordinary Error before symbol lookup, before linking and before native
entry; no fallback shape, coercion or alternative calling route is attempted.
A JVM admits every valid scalar declaration and every by-value group
declaration that its native linker supports; a group signature the linker
rejects is refused the same way. Signed and unsigned descriptors of one width
share a registered shape, as do separately declared layouts of identical
physical structure. A registered shape grants no library authorization: host
library authorization remains independently required.

## Custody, identity and release

Every non-null owned acquisition enters provider custody before publication,
whether delivered directly or through `out`, even when C also returns an error
status. All owned carriers of a call are adopted before fallible result
conversion. Struct and union result roots enter custody when allocated, before
native entry, and are never adopted a second time. If conversion fails before publication, provider custody still
retains those acquisitions for session shutdown.

Published owned handles have a private identity-based association with their
originating library in the Actor-local `std:ffi` MODULE. That map is dispatch
metadata only, not resource custody. Borrowed or non-owned references and
pointer capabilities are not registered as owned. Handles remain the existing foreign references; their
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
The existing private R1 provider mechanism uses `borrowed` declarations,
`preserving` signatures, bounded `copy` (at most 16777216 bytes, a limit of
that private R1 copy only, not of native memory), and epoch
revocation. A validated non-preserving native call or destructor invalidates
earlier regions before native entry; a rejected pre-entry call does not.
Session close revokes all such regions. A preserving declaration is a trusted
ABI assertion, not a physical proof of native memory lifetime. A copy is
independent of subsequent native mutations.

A binding declared by `FFI.bindPreserving` carries this preserving assertion:
its validated calls do not advance the borrow epoch, so they leave earlier
borrowed regions valid; a binding declared by `FFI.bind` does advance it.
The public API defines no `FFI.borrowed`, `FFI.preserving`, `FFI.copy`, or
equivalent memory-reading method. Consequently neither `bind` nor
`bindPreserving` accepts the private borrowed-memory descriptor, and neither
publishes a borrowed-memory wrapper with an extended lifetime promise. A
public borrowed typed pointer (`FFI.borrowedPointer`) is a pass-only
capability governed by the same epoch; it is not readable memory and is not
admitted by the private `copy`. The private mechanism remains available for the separately owned
LIB029 adaptation; it does not authorize a new public method in this slice.

### RootActor-only native authority

Only the Process's RootActor can acquire a session of the production native
FFM provider (`protos-ffm`), and only the RootActor directly uses that native
authority. An `ffi:` import, or any other acquisition of that provider, from
any other Actor fails with an ordinary Error before any provider session,
library load or native entry; the failure leaves no cached MODULE or session,
and the RootActor's own session never authorizes another Actor. Host library
authorization remains independently required: RootActor identity alone
authorizes no library. RootActor identity is the logical Actor identity of the
Process, not a particular Java or operating-system thread.

Importing `std:ffi` grants no native authority. Its source-backed declarations,
layout queries and prototype associations are ordinary Actor-local MODULE
behavior, available to every Actor. Native code entered by the RootActor
remains trusted in-process code: its effects, including threads it creates,
are not Protos Actors and are not confined by this restriction. Other foreign
providers keep their own existing contracts.

Live native sessions, library authority, bindings capturing such authority,
resources, borrowed regions, native regions (allocation roots, views and
slices) and native pointer capabilities (plain, borrowed, owner-backed or
with a known extent) have no automatic Actor or P transfer contract
(`../concurrency/ACTORS.md`, `../concurrency/PARALLEL_EXECUTION.md`). Importing
`std:ffi` in another Actor creates its ordinary local MODULE instance, not
inherited or delegated authority. No native call implicitly spawns work,
suspends, or runs in parallel. Ordinary transferred scalar results and ordinary
Arrays follow their existing transfer rules; containing a live resource does
not make an otherwise forbidden transfer valid.

## Exclusions

R1 excludes numeric pointers, pointer arithmetic (byte slices excepted),
direct C array-by-value invocation, aggregate projection to ordinary Maps or
Objects, packed layouts, bitfields, flexible array members, callbacks and
upcalls (including function-pointer upcalls), variadic functions, C++, arbitrary library paths, `SQLITE_TRANSIENT`,
a mandatory binding generator, external plugin execution in Native Image,
and a second semantics of invocation. It defines no SQLite connection,
statement, TEXT/BLOB input, transaction or row-mapping API (LIB029).
