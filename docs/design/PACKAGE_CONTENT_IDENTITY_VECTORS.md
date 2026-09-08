# Protos Package ContentIdentity v1 — fixed conformance vectors

Status: **FROZEN by TOOL001-F2E1C**
Method: `protos-package-tree-v1`
Initial mandatory algorithm: `sha256`
Owning contract: `docs/design/PACKAGE_CONTENT_IDENTITY.md`

## Purpose and oracle independence

These constants close the initial ContentIdentity design by making E1A/E1B
externally testable.

The expected values below were calculated before the repository Java focal using
two separate one-off Python implementations written from the E1A/E1B prose:

1. implementation A materialized the full canonical byte stream and passed it to
   `hashlib.sha256`;
2. implementation B updated a SHA-256 state directly record-by-record and fed
   file contents using different chunk boundaries.

The implementations used separate varuint/stream code paths and agreed on every
digest below.

The repository test `ProtosPackageContentIdentityV1ConformanceTest` is a third
implementation written in Java/JDK primitives. It consumes the constants rather
than generating the expected values.

No production ContentIdentity implementation exists at E1C closure.

## Canonical stream reminder

```text
MAGIC = ASCII("protos-package-tree-v1") 00

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
protos.toml -> empty byte sequence
```

Canonical stream hex:

```text
70726f746f732d7061636b6167652d747265652d763100010b70726f746f732e746f6d6c0000
```

Expected ContentIdentity:

```text
protos-package-tree-v1 sha256:beb503347f64442d909b4848e69333a72f4d049d7f5b37c32f453f409641ef04
```

### V2 — canonical ordering plus empty file

Input is intentionally listed non-canonically:

```text
z.protos    -> ASCII "Z"
protos.toml -> ASCII "[package]\n"
a.protos    -> empty
```

Canonical order is `a.protos`, `protos.toml`, `z.protos`.

Canonical stream hex:

```text
70726f746f732d7061636b6167652d747265652d7631000108612e70726f746f7300010b70726f746f732e746f6d6c0a5b7061636b6167655d0a01087a2e70726f746f73015a00
```

Expected:

```text
protos-package-tree-v1 sha256:a4e71028b12d648a10729e5dedf947d8bfbff1e6c8e1b95bb04ac170f5682e67
```

### V3 — binary resource bytes

Logical tree:

```text
Main.protos      -> ASCII "value: 42\n"
assets/data.bin  -> 00 01 02 ff 7f 80
protos.toml      -> ASCII "id = \"pkg\"\n"
```

Canonical stream hex:

```text
70726f746f732d7061636b6167652d747265652d763100010b4d61696e2e70726f746f730a76616c75653a2034320a010f6173736574732f646174612e62696e06000102ff7f80010b70726f746f732e746f6d6c0b6964203d2022706b67220a00
```

Expected:

```text
protos-package-tree-v1 sha256:2de3f9f78fb1355348861e08cb549919d10e4b50f8965f3fc933eac72fff8870
```

### V4 — varuint boundaries

Logical tree:

```text
protos.toml -> empty
<128 ASCII "a" path octets> -> <300 ASCII "Z" content octets>
```

Required framing:

```text
path length 128    -> 80 01
content length 300 -> ac 02
```

Expected:

```text
protos-package-tree-v1 sha256:4ae0bad7f7915a5e6db1ad4bb29ee71351fcf812b562b41da34f99b8fd4b8f02
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

Common root: `protos.toml -> empty`.

Tree F1:

```text
a -> ASCII "bc"
```

Stream:

```text
70726f746f732d7061636b6167652d747265652d763100010161026263010b70726f746f732e746f6d6c0000
```

Expected:

```text
sha256:43c4dd19a8fa1aea875f3930a80070d6192a9532bddd466787254c0f710ef60e
```

Tree F2:

```text
ab -> ASCII "c"
```

Stream:

```text
70726f746f732d7061636b6167652d747265652d763100010261620163010b70726f746f732e746f6d6c0000
```

Expected:

```text
sha256:552f16ad2b83e32bd9ba72037b717384257f5ae5087130fbc790a958c2fdc33f
```

## Exact-case identity pair

Uppercase tree:

```text
Main.protos -> ASCII "x"
protos.toml -> empty
sha256:9afaaf7e2a250fd11b83d12e63cf7e18527c95820d03bff6895e57a51600e450
```

Lowercase tree:

```text
main.protos -> ASCII "x"
protos.toml -> empty
sha256:ff22975888d65d4dd9563bd09ca67190c02473bdead0c536faf101cd66f72ec2
```

The two trees are separately valid and have different identities. One tree may
not contain both spellings because E1A rejects the sibling case-fold collision.

## One-byte manifest mutation pair

```text
protos.toml -> ASCII "x"
sha256:802294744fe3b655b2e6aebfaf0392ca3c62b4240b634b2f74eab11816a3df78

protos.toml -> ASCII "y"
sha256:c52cac30290c488fe48f8323c1b55229cfeede2ca92bb7da4ada7dd6de586d71
```

## Negative domain vectors

Each condition is rejected before canonical stream generation.

| ID | Payload condition | Required result |
|---|---|---|
| N1 | no root regular `protos.toml` | invalid logical tree |
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
`Filesystem.captureTree` semantics. F2E2 therefore no longer waits on an
unresolved specification question; it is dependency-blocked on implementation
item I024 until that general Core capability is published.

The fixed ContentIdentity vectors and all expected digests remain unchanged.
