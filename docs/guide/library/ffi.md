# Native C declarations with `std:ffi`

`std:ffi` is the Protos-first public API for declaring supported C functions as
ordinary Protos Closures. It is an optional, explicitly authorized native-code
path, not a SQLite library or a general-purpose memory API. The normative
contract is [`spec/semantics/FFI.md`](../../../spec/semantics/FFI.md).

## Three separate layers

- **`std:ffi`** is a source-backed MODULE made of ordinary frozen descriptors
  and Closures. Protos code declares the ABI, makes calls, checks C results and
  manages owned handles.
- **`std:interop`** provides the four explicit operations `invoke`,
  `instantiate`, `readMember` and `writeMember` on foreign values. It does not
  declare C signatures; see [Explicit foreign operations](interop.md).
- **`ffi:`** is the private scheme of the generic FFM provider, implemented
  as an optional external provider JAR on the portable JVM and integrated into
  the installed Native executable. The I144
  FFM provider supplies generic backend operations and a host-authorized
  `ffi:sqlite3` facade. Its private `signature`/`param`/`bind` API is for
  backend authors, not the recommended application API; see the
  [I144 provider guide](../interop/ffm-provider.md).

On the portable JVM, the provider JAR is trusted executable Java code; in both
distributions, authorized C is trusted native code running in process. The
host chooses (on the JVM) whether to load the provider and (everywhere) which
library it may open. Neither importing `std:ffi` nor constructing descriptors grants that
authority.

## Availability and setup

The current external FFM provider supports the **portable JVM CLI** on **Linux
x86-64 LP64**, with **Java 25** and the supported **GraalVM 25.5** toolchain. It
requires an external FFM provider JAR and, for each native library a program
uses, an explicit host authorization (`--allow-library alias=target`). The
SQLite examples need a shared SQLite library available to the native loader as
`libsqlite3.so.0`.

The installed **Native Image** executable integrates the same generic provider,
so no provider JAR is needed there; only the host authorization is:

```sh
protos --allow-library sqlite3=libsqlite3.so.0 app.protos
```

The operating system must provide the authorized library; Java, GraalVM and a
provider JAR are not needed. A library is loaded only when the program imports
its `ffi:<alias>`. The Native executable links a finite set of physical
signatures that covers `std:sqlite`; any other signature is refused with an
ordinary `Error` before C is entered. See
[Native Image: integrated provider](../interop/ffm-provider.md#native-image-integrated-provider).

Build the portable distribution and inspect its actual versioned archive:

```sh
make dist
ls target/distributions/
```

`make dist` normally requires a clean working tree. To build from a dirty tree,
the distribution can instead be prepared with:

```sh
python3 dist/build_portable.py --allow-dirty
```

Build the separate provider JAR against the archive produced for that project
version:

```sh
python3 dist/build_ffm_provider.py \
    target/distributions/protos-<version>-posix-jvm.zip \
    --output target/ffm-provider
```

Use the archive name actually present in `target/distributions/`. Building the
JAR grants nothing. Each run must explicitly load it with
`--foreign-provider-path` and, separately, authorize each native library with
`--allow-library alias=target`. No library is authorized by default.

## Imports and authority

```protos
FFI: import("std:ffi")
```

This is an ordinary Protos MODULE import. Re-importing the same MODULE in one
Actor returns its cached identity. Importing it does not discover a native
provider, open a native session, load a shared library or grant native
authority.

```protos
sqlite: import("ffi:sqlite3")
```

This is a distinct foreign import of the logical alias `sqlite3`. It succeeds
only when the FFM provider is available (on the portable JVM, the host
explicitly loads the external provider JAR; on Native, it is integrated)
**and** the host authorizes that alias with `--allow-library sqlite3=<target>` (or the embedding provider
option `library.sqlite3`). The target is an exact load name such as
`libsqlite3.so.0` or an absolute path, passed unchanged to the operating-system
loader. The provider's presence alone authorizes no library. The application passes the resulting library facade to `FFI`;
`FFI.bind` never accepts a path or a library name String as authority.

Portable JVM:

```sh
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-version-public.protos
```

Installed Native executable:

```sh
protos --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-version-public.protos
```

Two libraries use two aliases under the same provider (portable JVM shown;
on Native, a library's functions are usable only within the registered
signature set):

```sh
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
    --allow-library sqlite3=libsqlite3.so.0 \
    --allow-library zlib=/usr/lib/x86_64-linux-gnu/libz.so.1 \
    program.protos
```

A workspace application or a debugged file uses the same options, before the
entry or file. Portable JVM:

```sh
protos run --foreign-provider-path provider.jar \
  --allow-library sqlite3=libsqlite3.so.0 Main

protos debug --foreign-provider-path provider.jar \
  --allow-library sqlite3=libsqlite3.so.0 app.protos
```

Installed Native executable:

```sh
protos run --allow-library sqlite3=libsqlite3.so.0 Main
protos debug --allow-library sqlite3=libsqlite3.so.0 app.protos
```

See the [FFM provider guide](../interop/ffm-provider.md) for both option
positions and their limits.

Loading the provider (`--foreign-provider-path`, portable JVM only), authorizing a native library
(`--allow-library`), and granting Protos capabilities (`--allow-filesystem`,
`--allow-network`, ...) are independent decisions. An authorized library runs
`TRUSTED_IN_PROCESS`: it can use native file and network access, load
transitive dependencies and run their initializers, keep global state shared
by all Actors, block indefinitely, corrupt memory through a wrong ABI
signature, or terminate the process. `--allow-library` is not a sandbox, and
authorizing a C++ library adds no C++ ABI, Qt, Boost, callback or
virtual-method support.

The public sample is
[`sqlite-version-public.protos`](../../../protos/examples/ffi/sqlite-version-public.protos).

## The public members

The MODULE exports thirteen scalar descriptor values and twenty-four ordinary
Closures. The R1 binding members:

| Member | Signature or value | Meaning and restrictions |
|---|---|---|
| `FFI.void` | scalar descriptor | Return-only C `void`; direct Protos result is `null`. |
| `FFI.i32` | scalar descriptor | Signed 32-bit Integer descriptor. |
| `FFI.i64` | scalar descriptor | Signed 64-bit Integer descriptor. |
| `FFI.i8`, `FFI.i16` | scalar descriptors | Signed 8- and 16-bit Integer descriptors. |
| `FFI.u8`, `FFI.u16`, `FFI.u32`, `FFI.u64` | scalar descriptors | Unsigned 8-, 16-, 32- and 64-bit Integer descriptors. |
| `FFI.f32` | scalar descriptor | C binary32 `float`; a Protos `Float` input is rounded once to binary32. |
| `FFI.f64` | scalar descriptor | C binary64 `double`; Protos `Float` only. |
| `FFI.sizeT` | scalar descriptor | Initial LP64 `size_t`, unsigned 64-bit Integer. |
| `FFI.utf8z` | scalar descriptor | Input-only String encoded as strict UTF-8 and terminated with NUL. |
| `FFI.opaque(library, nativeTypeName)` | Closure | Declares a non-null opaque reference tied to this library and session. The type name is a C identifier. |
| `FFI.nullable(descriptor)` | Closure | Admits `null` for a plain opaque descriptor, or declares a nullable typed pointer from a plain typed pointer; does not accept scalar, owned, `out` or already nullable descriptors. |
| `FFI.owned(opaqueDescriptor, releaseSymbol, releaseReturnDescriptor)` | Closure | Declares an owned opaque result and its one-reference destructor; destructor result descriptor is `void` or `i32`. |
| `FFI.out(descriptor)` | Closure | Declares zero-initialized native output storage and consumes no Protos argument. Targets are the integral scalars, `f32`, `f64`, opaque, nullable opaque, owned opaque, typed pointers, nullable typed pointers and borrowed typed pointers; nested `out`, `void`, `utf8z`, aggregates, fields and non-descriptors are rejected. |
| `FFI.bind(library, symbol, returnDescriptor, argumentDescriptors)` | Closure | Resolves a supported declaration through the already-authorized library and returns a new ordinary Protos Closure. The final parameter must be an ordinary Array of descriptors. |
| `FFI.bindPreserving(library, symbol, returnDescriptor, argumentDescriptors)` | Closure | Like `bind`, plus the trusted declaration that the C function leaves earlier borrowed native regions valid. |
| `FFI.release(ownedHandle)` | Closure | Irreversibly retires a registered owned handle, calls its destructor at most once and returns the exact C result. |
| `FFI.borrowedPointer(pointer)` | Closure | Declares a borrowed result of a plain typed pointer, for return and `out` positions only. |
| `FFI.addressOf(value)` | Closure | For a live owned handle acquired through `std:ffi`: a pass-only `void` pointer capability (the handle keeps its destructor and release). For a live native region: a pointer to its layout with its known extent. |

The declarative layout members, which need no library and no native
authority:

| Member | Meaning |
|---|---|
| `FFI.field(name, layout)` | A named member; any String is a valid name. |
| `FFI.struct(fields)` / `FFI.union(fields)` | A C struct with internal and tail padding, or a C union with every member at offset zero. `fields` is an ordinary Array of distinct field declarations. |
| `FFI.array(element, count)` | A fixed C array of a positive count; its metadata does not grow with the count. |
| `FFI.pointer(pointee)` | A typed data pointer to a layout or to `FFI.void`; also a pointer invocation descriptor. |
| `FFI.sizeOf(layout)`, `FFI.alignOf(layout)` | Exact size and alignment. |
| `FFI.offsetOf(layout, path)` | Byte offset reached by an Array of field names and array indices; pointers are never followed. |
| `FFI.prototype(layout, prototype)` / `FFI.layoutOf(prototype)` | Associate an exact ordinary prototype with an exact layout, and look it up. |

The native memory members, which need the RootActor and an available native
provider but no C library (see [Native memory](#native-memory)):

| Member | Meaning |
|---|---|
| `FFI.cType(name)` | The existing descriptor of `"int"`, `"long"`, `"long long"`, `"size_t"` or `"double"`; any other spelling is an Error. |
| `FFI.allocate(layout)` | Zero-initialized native storage of a layout, owned until `FFI.release`. |
| `FFI.slice(region, byteOffset, byteLength)` | A byte view of part of a region; nothing is copied. A zero length gives an empty view. |
| `FFI.view(pointer, layout)` | A typed region over a pointer with a known, sufficient extent. |
| `FFI.assumeExtent(pointer, byteLength, anchor, access)` | A trusted extent for a pointer, anchored by an allocation, an owned C handle, a borrowed pointer itself, or a library's static storage. |

These are the public members, not a public descriptor record layout. Descriptor
objects are ordinary frozen Protos objects; structurally similar objects do not
become descriptors.

## Scalar descriptors

The scalar descriptors have distinct identities. Each is an ordinary,
frozen Protos object, but each declares a different ABI domain:

| Descriptor | Exact Protos domain | C-side behavior |
|---|---|---|
| `FFI.void` | Return only | No C result; maps to `null`. |
| `FFI.i8`, `FFI.i16` | Integer from `-128` through `127`, or `-32768` through `32767` | Exact signed value. |
| `FFI.u8`, `FFI.u16`, `FFI.u32`, `FFI.u64` | Integer from `0` through `255`, `65535`, `4294967295` or `18446744073709551615` | Exact unsigned value; a negative Integer is rejected. |
| `FFI.i32` | Integer from `-2147483648` through `2147483647` | Exact signed 32-bit value; no truncation or wrapping. |
| `FFI.i64` | Integer from `-9223372036854775808` through `9223372036854775807` | Exact signed 64-bit value. |
| `FFI.sizeT` | Integer from `0` through `18446744073709551615` in the initial LP64 profile | Unsigned 64-bit `size_t`; values above signed 64-bit range remain exact. |
| `FFI.f32` | Protos `Float` | Rounded once to IEEE binary32 (round to nearest, ties to even); results widen exactly to `Float`. An Integer is not converted. |
| `FFI.f64` | Protos `Float` | IEEE binary64; an Integer is not implicitly converted. |
| `FFI.utf8z` | Input Protos `String` | Strict UTF-8 plus a trailing NUL; interior NUL and invalid Unicode are rejected. |

Native integral results are exact Protos Integers. There is no implicit
numeric conversion, pointer-to-Integer conversion or pointer construction.
`void` and `utf8z` are not output-position types.

## Declarative layouts and prototypes

Layouts describe C data for the Linux x86-64 LP64 profile. Declaring and
querying them is ordinary Protos code: no library is imported, no native
memory is allocated and no provider is entered, so they work in every Actor.

```protos
FFI: import("std:ffi")

Point: FFI.struct(Array(FFI.field("x", FFI.i32), FFI.field("y", FFI.i32)))
Tagged: FFI.struct(Array(
    FFI.field("tag", FFI.u8), FFI.field("next", FFI.nullable(FFI.pointer(Point)))))

FFI.sizeOf(Point)                        // 8
FFI.offsetOf(Tagged, Array("next"))      // 8, after padding
FFI.sizeOf(FFI.array(Point, 4))          // 32

Vector2: {
    x: 0
    y: 0
    length2: () => { this.x * this.x + this.y * this.y }
}
FFI.prototype(Point, Vector2)            // answers Vector2, unchanged
FFI.layoutOf(Vector2) === Point          // true
```

The association is exact: a descendant of `Vector2` has no layout until it is
registered itself, and the prototype is neither mutated nor called.

Declaring a layout never allocates memory. Native memory (`FFI.allocate`,
regions, slices and views) and `FFI.cType` are described in
[Native memory](#native-memory); typed pointers are bindable as described
next, and structs and unions pass and return by value as described in
[Structs and unions by value](#structs-and-unions-by-value). Field
descriptors, and fixed arrays used directly, are rejected by `bind`,
`bindPreserving` and `out`: a fixed array travels only inside a struct or
union.

## Typed native pointers

Typed pointer declarations can appear in native signatures:

| Declaration | Return | Parameter | `out` target |
|---|---|---|---|
| `FFI.pointer(layout)` / `FFI.pointer(FFI.void)` | yes (NULL is a `ForeignError`) | yes | yes |
| `FFI.nullable(pointer)` | yes (NULL is `null`) | yes (`null` passes C `NULL`) | yes |
| `FFI.borrowedPointer(pointer)` | yes (NULL is a `ForeignError`) | no | yes |

A native pointer result becomes a pass-only capability. It has no address you
can read, no size, no members and no indexed access; it can only be passed back
to a compatible pointer parameter, possibly of another authorized library.
Compatibility is the exact pointee layout identity: two independently declared
`FFI.pointer(FFI.u8)` are compatible, `FFI.pointer(FFI.i8)` is not, and two
structurally identical structs are not. A `FFI.void` pointer parameter accepts
any live capability; a `void` capability is never accepted by a typed
parameter. Integers, opaque handles, descriptors and other values are rejected
before C is entered.

A borrowed pointer stays valid until the next call of a binding declared with
`bind` (not `bindPreserving`), the next `release`, or the end of the native
session. Passing it to a `bindPreserving` binding keeps it valid:

```protos
FFI: import("std:ffi")
native: import("ffi:mylib")

bytes: FFI.pointer(FFI.u8)
buffer: FFI.bind(native, "my_buffer", FFI.borrowedPointer(bytes), Array())
checksum: FFI.bindPreserving(native, "my_checksum", FFI.i32, Array(bytes))

data: buffer()
checksum(data)       // C reads the buffer; data stays valid
checksum(data)       // still valid: preserving calls do not revoke it
```

This only lets C use the pointer. It does not let Protos dereference, copy or
index the memory it points to.

`FFI.addressOf(ownedHandle)` turns a live owned handle acquired through
`std:ffi` into a `void` pointer capability, for C functions that take an
untyped pointer to it. In this release that is its only domain. The handle
keeps its custody and destructor; once the handle is released (or the session
ends), the capability is rejected before entry, even if C later reuses the
same address.

## Native memory

Native memory is bounded, typed storage of the RootActor's native session.
Allocating needs an available FFM provider but no C library: the program can
allocate before (or without) importing any `ffi:` alias.

```protos
FFI: import("std:ffi")

Inner: FFI.struct(Array(FFI.field("a", FFI.i32), FFI.field("b", FFI.f64)))
Outer: FFI.struct(Array(
    FFI.field("tag", FFI.u8),
    FFI.field("inner", Inner),
    FFI.field("values", FFI.array(FFI.i16, 3)),
    FFI.field("next", FFI.nullable(FFI.pointer(Inner)))))

outer: FFI.allocate(Outer)     // zero-initialized, padding included
outer["tag"] = 7               // exact u8 range, or an Error
outer["inner"]["b"] = 2.5      // nested members are views, not copies
outer["values"][2] = -1        // exact Integer index, bounds checked
bytes: FFI.slice(outer, 0, 8)  // octets of the same storage
```

A region is indexed with `at`/`atPut`: struct and union members by their exact
String name (a field may be called `size`, `at` or `init`), arrays and byte
slices by exact Integer position, and a scalar allocation by `0`. Reading a
nested aggregate answers a view of the same memory. Assigning an aggregate
copies the bytes of another region of the *same* layout; a structurally equal
layout or an ordinary object is rejected. Every rejected access is an ordinary
Error that leaves the memory unchanged.

To give a region to C, ask for its address explicitly; a region is never
passed as a pointer by itself:

```protos
native: import("ffi:mylib")
fill: FFI.bind(native, "my_fill", FFI.i32, Array(FFI.pointer(Outer)))
fill(FFI.addressOf(outer))     // C sees the same bytes; Protos sees C's writes
```

`FFI.view(pointer, layout)` reads memory through a pointer only when the
pointer has a known extent covering the layout (for example one answered by
`FFI.addressOf`), is suitably aligned, and points to that layout or to
`FFI.void`. Pointers returned by C, or read from a pointer field, have no known
extent. When C documents that such memory is valid, assert it explicitly:

```protos
lookup: FFI.bind(native, "my_static_inner", FFI.pointer(Inner), Array())
inner: FFI.view(FFI.assumeExtent(lookup(), FFI.sizeOf(Inner), native, "read"), Inner)
```

The anchor states what keeps the memory alive: an allocation that contains it,
an owned C handle, the borrowed pointer itself, or the library (static
storage). The library anchor must be the facade you got from
`import("ffi:<alias>")` in the same Actor; other objects, even with an
`assumeStaticExtent` member, are rejected without being touched.

A pointer read from a pointer field only keeps the region it was read from
alive. It says nothing about who allocated the memory it points to or how long
that memory lives, so it has no extent until you assert one. Assertions about C memory are trusted, like a C signature, and are
not checked against the real C storage. An assertion never widens a known
extent, never turns read-only access into `"readWrite"` and never revives an
expired pointer.

Slices may be empty (`FFI.slice(region, 8, 0)`); an empty view has no valid
index. Views and slices keep only the original allocation (or C anchor)
alive, not every intermediate view, so long chains of slices are cheap.

`FFI.release(region)` frees an allocation and answers `null`; every view,
slice and pointer derived from it becomes unusable, even if C later reuses
the address. Slices, views and pointers cannot be released themselves.
Remaining allocations are freed when the native session ends. Releasing an
allocation does not invalidate borrowed pointers or other allocations.

## First binding

SQLite's `sqlite3_libversion_number` takes no arguments and returns a C `int`.
The public declaration and call stay in Protos:

```protos
FFI: import("std:ffi")
sqlite: import("ffi:sqlite3")

version: FFI.bind(sqlite, "sqlite3_libversion_number", FFI.i32, Array())
number: version()
```

The binding is an ordinary Protos Closure, so normal Closure identity and
invocation rules apply. The SQLite code is data: check it in Protos rather than
expecting the provider to interpret it.

## Arguments and binding snapshots

For a parameter, put its descriptor in the ordinary Array passed to `FFI.bind`:

```protos
complete: FFI.bind(sqlite, "sqlite3_complete", FFI.i32, Array(FFI.utf8z))
result: complete("SELECT 1;")
```

The descriptor Array is shallowly copied when the binding is constructed.
Later changes to the caller's Array do not alter its retained ABI signature.
Parameters are non-void scalar inputs, opaque or nullable opaque references,
typed or nullable typed pointers, struct or union layouts passed by value, or
supported `out` descriptors; an owned descriptor is for a result, not a direct
input parameter.

At invocation, arguments are admitted in order and must match the declared
arity, exact kind, range, nullability and resource lifetime. Invalid calls are
rejected before C entry.

See the runnable [public binding tutorial](../../../protos/tutorials/08-language-interactions/09-public-ffi-bindings.protos)
and the [SQL completeness example](../../../protos/examples/ffi/sqlite-complete-public.protos).

## Output parameters

An `FFI.out(T)` descriptor declares a C output parameter but consumes no
Protos call argument. Zero-initialized output storage is allocated for each
invocation; creating the descriptor does not acquire or expose native memory.
For example,
the four-parameter C function `sqlite3_open_v2` receives only three Protos
arguments when its second parameter is `FFI.out(dbOwned)`.

When a binding has output positions, every invocation returns a fresh ordinary
Protos Array:

```text
[nativeReturn, out0, out1, ...]
```

The first element is `null` for a `void` return. Outputs follow C parameter
order. The provider's private indexed result is not exposed as the public
result. Nonzero C status codes remain values that Protos code must inspect.

## Structs and unions by value

Struct and union layouts can be passed and returned by value, with the real C
calling convention. Every binding position, in this release:

| Descriptor | Return | Parameter | `out` target |
|---|---|---|---|
| integral scalars, `f32`, `f64` | yes | yes | yes |
| `void` | yes (answers `null`) | no | no |
| `utf8z` | no | yes | no |
| opaque, nullable opaque | yes | yes | yes |
| owned opaque | yes | no | yes |
| typed pointer, nullable typed pointer | yes | yes | yes |
| borrowed typed pointer | yes | no | yes |
| `FFI.struct(...)`, `FFI.union(...)` | yes, a new owned region | yes, a region of that exact layout, copied | yes, a new owned region |
| `FFI.array(...)` | no (only inside a struct or union) | no | no |
| field, `out(...)` | no | no (`out` declares an output position) | no |

A struct or union argument must be a live native region of exactly the
declared layout: an allocation, a returned region, or a typed view of one
(read-only views included). It is copied for the call, so C cannot change the
caller's region. Maps, Objects, Arrays, pointers, byte slices and regions of a
structurally identical but separately declared layout are rejected before C
is entered; nothing converts an ordinary Protos value into a C struct.

```protos
FFI: import("std:ffi")
native: import("ffi:mylib")

// C: typedef struct { int32_t a; double b; } pair;
//    pair pair_scale(pair value);
Pair: FFI.struct(Array(FFI.field("a", FFI.i32), FFI.field("b", FFI.f64)))
scale: FFI.bind(native, "pair_scale", Pair, Array(Pair))

input: FFI.allocate(Pair)
input["a"] = 5
input["b"] = 1.25
result: scale(input)
result["a"]          // read from the returned native region
input["a"]           // still 5: C received a copy

FFI.release(input)   // each region is released on its own
FFI.release(result)
```

The returned region is a new allocation, independent of the argument and of
the call: it stays valid after the call, after other calls and after the
argument is released, until `FFI.release` (or the end of the native session).
It supports every region operation: indexing, `FFI.slice`, `FFI.view` and
`FFI.addressOf`. A returned union has overlapping storage; no member is
"active".

A struct or union `out` position also answers a new owned region, in the
usual result Array:

```protos
// C: pair pair_split(pair value, int32_t delta, pair *first, choice *second);
Choice: FFI.union(Array(FFI.field("integer", FFI.i64), FFI.field("real", FFI.f64)))
split: FFI.bind(native, "pair_split", Pair,
    Array(Pair, FFI.i32, FFI.out(Pair), FFI.out(Choice)))

source: FFI.allocate(Pair)   // `input` was released above
source["a"] = 3
source["b"] = 1.0
answers: split(source, 4)    // two arguments: the outs consume none
answers[0]                   // the returned Pair region
answers[1]                   // the Pair written through `first`
answers[2]                   // the Choice written through `second`
FFI.release(source)
for(region in answers) { FFI.release(region) }
```

The copy is shallow: a pointer member is copied as a pointer, and its target
is neither copied nor kept alive. The copied struct is independent of the
caller's region, but the memory its pointer members point to is not: the
caller's region and the copy still point to the same memory, and passing by
value says nothing about how long that memory lives. Struct and union calls follow the usual
borrow rules: a call declared with `bind` revokes earlier borrowed pointers,
while a `bindPreserving` call keeps them; a rejected call changes nothing and
never enters C. Packed structs, bitfields, flexible array members, passing a C
array directly, callbacks and variadic functions are not supported.

## Owned resources

Use `FFI.opaque` for a logical C reference type and `FFI.owned` for the
descriptor of a return or `out` result that transfers custody to the provider.
Use the corresponding plain opaque descriptor when passing that handle back to
C. No native address is exposed, and identity belongs to each acquisition in
its originating library, session and generation, not to an address.

Every non-null owned acquisition enters provider custody before publication,
including a handle returned alongside an error status or a result that cannot
be converted for Protos. A C error status does not cancel that custody. The
provider also releases remaining owned resources at session shutdown.

`FFI.release(handle)` removes the public identity association before invoking
the original destructor. Retirement is irreversible: the exact destructor
result is returned, but an exception or non-success result does not permit a
retry. A duplicate release and any later attempt to pass the released handle
to C are rejected. For SQLite statements and connections, finalize the
statement before closing its connection. These declarations do not create a
`Closable` protocol or a Future.

The runnable
[owned/out tutorial](../../../protos/tutorials/12-system-resources/05-public-ffi-owned-out.protos)
and [numeric query example](../../../protos/examples/ffi/sqlite-numeric-query-public.protos)
show this sequence using only public declarations.

## C results and Protos errors

A SQLite status other than `SQLITE_OK` is an ordinary C result. For example,
`sqlite3_step` returns `SQLITE_ROW` and later `SQLITE_DONE`; neither is
automatically an Error. The program decides what those values mean.

An argument or descriptor rejection detected before entering C signals an
ordinary Protos `Error`. A failure after entering a provider operation, such
as resolving a missing symbol, signals `ForeignError` under the existing
foreign-value rules. Native crashes, memory corruption and process termination
are not recoverable Protos errors.

## Borrowed memory is not public in R1

There is no public borrowed-memory descriptor or public `FFI.copy`,
`FFI.borrowed` or `FFI.preserving` operation. SQLite TEXT and BLOB column
readers return pointers into memory owned by SQLite. Because R1 does not expose
the required lifetime-bounded copying operation, those functions cannot be
taught as available public `std:ffi` calls. The private I144 guide retains its
historical borrowed-memory material for backend authors and existing tests;
that private contract is not an application API.

R1 also does not expose a SQLite connection abstraction, TEXT/BLOB input
binding, callbacks, passing or returning structs by value, conversion of
native structs to or from Maps and Objects, variadic calls, C++, arbitrary
allocators or `SQLITE_TRANSIENT`. Public `std:ffi` itself has no SQLite Connection or
Statement objects. Those abstractions are implemented separately in ordinary
Protos as [`std:sqlite`](sqlite.md), which application developers
should use for SQLite. Its SQLite-specific borrowed-memory adaptation remains
private to that library: it adds no public `FFI.copy`, `FFI.borrowed` or
`FFI.preserving`. `std:sqlite` accepts exact i64 Integer, Float,
String/TEXT, Bytes/BLOB and null parameters, managing copies, Statements and
resources in ordinary Protos. It also provides persistent connections and manual
transactions; these library facilities do not extend public `std:ffi`.

## Security and limits

Native execution is trusted and in-process. The C ABI is declared manually;
the provider can check argument admission but cannot prove that the declared
signature matches the native function. An incorrect signature can corrupt
memory or terminate the process. There is no sandbox, and Protos `Filesystem`
authority does not confine C code. The provider accepts only host-authorized
logical library aliases, not paths supplied to `FFI.bind`.

This FFM profile is not a platform portability promise. The portable JVM loads
the external provider JAR; the installed Native Image executable integrates the
same provider with a finite set of physical signatures. Platform support is
limited to Linux x86-64 LP64.

In a Native Image executable, a binding is accepted only when its exact
physical signature is registered in the image, including the complete member
structure of every struct or union passed or returned by value; another valid
declaration is refused with an ordinary Error before C is looked up or
entered. The JVM accepts every signature its native linker supports. A
registered signature is not a library grant: each library still needs its own
`--allow-library` authorization.

## Actors and isolated parallel execution

Closures that capture a native binding, live native resources, native
regions, pointers and native authority do not automatically cross Actor
boundaries or isolated P execution.
Importing `std:ffi` in another Actor produces that Actor's ordinary local
MODULE; it does not transfer a provider session or host grant. That MODULE's
declarations, layout queries and prototype associations work in every Actor
because they need no native authority. Acquiring a native library is
different: only the Process's RootActor can import `ffi:<alias>`, and the
same import in any other Actor fails with an ordinary Error before any native
code runs, and so do native memory operations such as `FFI.allocate` and
`FFI.cType`. The host must still authorize each library explicitly. Native calls
are synchronous and do not implicitly schedule parallel work.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `import("ffi:sqlite3")` reports an ordinary resolution Error | The host did not pass the external JAR with `--foreign-provider-path`, or provider registration failed. Importing `std:ffi` alone is not authorization. |
| `import("ffi:<alias>")` signals `ForeignError` with `foreignCategory` `unauthorized-library` | The host loaded the provider but did not pass `--allow-library <alias>=<target>`. No library is authorized by default. |
| `import("ffi:<alias>")` signals `ForeignError` with `foreignCategory` `library-unavailable` | The alias is authorized but the operating-system loader cannot load its target. |
| A filename or path import is rejected | Imports name logical aliases only (`[A-Za-z_][A-Za-z0-9_]{0,127}`), never files or paths. |
| Binding a symbol signals `ForeignError` | The symbol may not exist in the authorized library or its searched dependencies; check the C symbol spelling and library available to the loader. |
| A call signals ordinary `Error` before C entry | Check argument count, exact descriptor kinds and ranges, `utf8z` interior NUL, nullability, handle type and handle liveness. |
| A released resource is rejected | Release is one-way. Do not retry its destructor or pass that handle to another binding. |
| The provider reports an unsupported platform | Use Linux x86-64 LP64: the portable JVM with Java 25/GraalVM 25.5 and the provider JAR, or the installed Native executable. Other platforms are outside this profile. |
| On the Native executable, a declaration signals `Error` although it works on the JVM | Its physical C signature is outside the Native executable's registered set; see [Native Image: integrated provider](../interop/ffm-provider.md#native-image-integrated-provider). |

Further runnable programs: the existing
[SQLite version example](../../../protos/examples/ffi/sqlite-version-public.protos),
the [descriptors tutorial](../../../protos/tutorials/08-language-interactions/08-public-ffi-descriptors.protos),
the [bindings tutorial](../../../protos/tutorials/08-language-interactions/09-public-ffi-bindings.protos),
the [owned/out tutorial](../../../protos/tutorials/12-system-resources/05-public-ffi-owned-out.protos),
the [SQL completeness example](../../../protos/examples/ffi/sqlite-complete-public.protos)
and the [numeric query example](../../../protos/examples/ffi/sqlite-numeric-query-public.protos).
