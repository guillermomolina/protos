# Protos Package ContentIdentity v2 — fixed conformance vectors

**Current v2 contract (D242/I152):** All canonical streams and SHA-256
values below use `package.toml` and method `protos-package-tree-v2`.
`protos-package-tree-v1` with `protos.toml` remains available only in
Git history as the original TOOL001-F2E1 frozen reference. It cannot be
used as an alias for the current verifier.

Status: **CURRENT — D242/I152**; original TOOL001-F2E1C v1 frozen baseline is historical
Method: `protos-package-tree-v2`
Initial mandatory algorithm: `sha256`
Owning contract: `docs/design/PACKAGE_CONTENT_IDENTITY.md`

## Purpose and oracle independence

These constants close the initial ContentIdentity design by making E1A/E1B
externally testable.

The historical v1 values were independently established before the repository
Java focal using two different Python implementations. The v2 constants below
reflect D242/I152, with a new magic prefix and manifest name. The original
reference procedures were:

1. implementation A materialized the full canonical byte stream and passed it to
   `hashlib.sha256`;
2. implementation B updated a SHA-256 state directly record-by-record and fed
   file contents using different chunk boundaries.

The historical implementations agreed on each v1 digest. These published
current v2 expected digests are fixed values independent of production code.

The repository test `ProtosPackageContentIdentityV2ConformanceTest` is a third
implementation written in Java/JDK primitives. It consumes the constants rather
than generating the expected values.

The original E1C closure predates the now-existing production ContentIdentity implementation.

## Canonical stream reminder

```text
MAGIC = ASCII("protos-package-tree-v2") 00

FILE =
    01
    varuint(path length)
    path ASCII bytes
    varuint(content length)
    exact content bytes

END = 00
```

FILE records are ordered by unsigned lexicographic comparison of the complete
canonical path bytes.

## Positive fixed vectors

### V1 — minimal empty manifest

Logical tree:

```text
package.toml -> empty byte sequence
```

Canonical stream hex:

```text
70726f746f732d7061636b6167652d747265652d763200010c7061636b6167652e746f6d6c0000
```

Expected ContentIdentity:

```text
protos-package-tree-v2 sha256:7b84ceb16a689651d3de513f3aa9552554736735971ef9720a7818daff93c764
```

### V2 — canonical ordering plus empty file

Input is intentionally listed non-canonically:

```text
z.protos    -> ASCII "Z"
package.toml -> ASCII "[package]\n"
a.protos    -> empty
```

Canonical order is `a.protos`, `package.toml`, `z.protos`.

Canonical stream hex:

```text
70726f746f732d7061636b6167652d747265652d7632000108612e70726f746f7300010c7061636b6167652e746f6d6c0a5b7061636b6167655d0a01087a2e70726f746f73015a00
```

Expected:

```text
protos-package-tree-v2 sha256:f401793adfe18d9ffb00747b47a6581c7184dddc62cd5665a24fb6d217a4a9cb
```

### V3 — binary resource bytes

Logical tree:

```text
Main.protos      -> ASCII "value: 42\n"
assets/data.bin  -> 00 01 02 ff 7f 80
package.toml      -> ASCII "id = \"pkg\"\n"
```

Canonical stream hex:

```text
70726f746f732d7061636b6167652d747265652d763200010b4d61696e2e70726f746f730a76616c75653a2034320a010f6173736574732f646174612e62696e06000102ff7f80010c7061636b6167652e746f6d6c0b6964203d2022706b67220a00
```

Expected:

```text
protos-package-tree-v2 sha256:2291f7d8a6bccceceec105ce20ebdf17f2d76a3b82ff869f5a82fbddd29049e0
```

### V4 — varuint boundaries

Logical tree:

```text
package.toml -> empty
<128 ASCII "a" path octets> -> <300 ASCII "Z" content octets>
```

Required framing:

```text
path length 128    -> 80 01
content length 300 -> ac 02
```

Expected:

```text
protos-package-tree-v2 sha256:6a63f12d9befb69810f8b8e84a7ef878a06bc1b67b35447f149cc3946cb7a0a0
```

Separate fixed varuint values:

```text
0       -> 00
1       -> 01
127     -> 7f
128     -> 80 01
255     -> ff 01
300     -> ac 02
16384   -> 80 80 01
2^70    -> 80 80 80 80 80 80 80 80 80 80 01
```

The last vector prevents 64-bit width from becoming part of the abstract format.

## Framing-separation pair

Common root: `package.toml -> empty`.

Tree F1:

```text
a -> ASCII "bc"
```

Stream:

```text
70726f746f732d7061636b6167652d747265652d763200010161026263010c7061636b6167652e746f6d6c0000
```

Expected:

```text
sha256:475b53fd2652994d031f03f4df60adeedb024e64f28c7e34d0df58614e06e6fa
```

Tree F2:

```text
ab -> ASCII "c"
```

Stream:

```text
70726f746f732d7061636b6167652d747265652d763200010261620163010c7061636b6167652e746f6d6c0000
```

Expected:

```text
sha256:aec33065fd8efae5bb7510e14d8d8910425d6cd7a7933ae4e420f0635374303b
```

## Exact-case identity pair

Uppercase tree:

```text
Main.protos -> ASCII "x"
package.toml -> empty
sha256:b56cbd69fb745176b1d76856445dc232c57f2693c8a4db9fc9c757ad63da2383
```

Lowercase tree:

```text
main.protos -> ASCII "x"
package.toml -> empty
sha256:157416a3c715142ac09ee3d7238badba2ce2a5546ffb5819886a218a5697c719
```

The two trees are separately valid and have different identities. One tree may
not contain both spellings because E1A rejects the sibling case-fold collision.

## One-byte manifest mutation pair

```text
package.toml -> ASCII "x"
sha256:742f238871ea7fca7338532428ef7627f7d67662df7b84d467ecb7f658348950

package.toml -> ASCII "y"
sha256:0748b3c2622b01b68c9d419983111b74c31cacde737f2c52b6525036b1ad705d
```

## Negative domain vectors

Each condition is rejected before canonical stream generation.

| ID | Payload condition | Required result |
|---|---|---|
| N1 | no root regular `package.toml` | invalid logical tree |
| N2 | `a//b` | invalid empty segment |
| N3 | `a/../b` | invalid `..` |
| N4 | `name.` | invalid trailing `.` |
| N5 | `bad name` | invalid v1 character |
| N6 | `café.protos` | invalid non-ASCII v1 path |
| N7 | `CON.txt` | Windows-reserved basename |
| N8 | sibling `Parser.protos` + `parser.protos` | ASCII-case-fold collision |
| N9 | `Data/a` + `data/b` | implied-directory ASCII-case-fold collision |
| N10 | symbolic link/junction/reparse traversal entry | reject; never follow |
| N11 | FIFO/socket/device/other special entry | reject |
| N12 | only root `protos.toml` exists | reject without fallback |
| N13 | both manifest names exist at root | reject |
| N14 | `package.toml` root is a symlink or directory | reject |
| N15 | forbidden root `protos.toml` is a symlink | reject without following |
| N16 | expected method is `protos-package-tree-v1` | reject without alias |

## Equivalence vectors

These differences do not change ContentIdentity when logical regular-file
path+bytes are unchanged:

| ID | Difference | Required relation |
|---|---|---|
| E1 | add/remove empty directory | equal |
| E2 | file/directory mode or executable bit only | equal |
| E3 | timestamps/owner/ACL/xattr/inode/link-count only | equal |
| E4 | package-store absolute path only | equal |
| E5 | archive/container/compression representation only | equal |
| E6 | read chunk sizes/buffer boundaries only | equal |

## Closure scope

These vectors freeze `TOOL001-F2E1`; they do not implement Filesystem
enumeration/stat, a production verifier, store layout, fetch/network,
archive canonicalization, or external-node execution.

F2E2 is blocked by B009 until the general Filesystem tree-observation semantics
needed to verify these vectors are normatively available.

## Post-D046 dependency note

The closure statement above records the state when F2E1C was published.

D046 / specification revision `0.1.383` subsequently satisfies B009's
normative tree-observation requirement with general `Filesystem.entries` and
`Filesystem.captureTree` semantics. I024-D now closes the general Core implementation/conformance prerequisite and
B009. F2E2 is READY to consume D046 capture for verified store binding.

D242/I152 deliberately replaces the original v1 streams and digests with the
current v2 values. The original v1 vectors remain available in Git history.

D171 later removed the public `Filesystem.captureTree` selector. The vectors are
now exercised against a host-captured read-only Filesystem supplied by
package-content custody (PLAT012); their trees and expected digests are
unchanged.
