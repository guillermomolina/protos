# Package Tool

The Package Tool (`TOOL001`) is the bundled Tool behind `protos package`. Its
policy is ordinary Protos code under
[`protos/tools/package/`](../../../protos/tools/package/), loaded directly from
the installed toolchain (see [Bundled Tools](README.md)).

The Package Tool is **still evolving**. This page documents only what the
current public command line does. Much more package logic exists inside the
Tool — lockfile syntax, version selection, content identity, project metadata —
but it is internal machinery used by the toolchain and tests, not user
commands.

## What works today

| Command | Status |
|---|---|
| `protos package manifest` | Implemented: validates `protos.toml` in the current directory |
| `protos run <entry>` | Implemented: runs a root-package entry; uses the Package Tool's planning internally ([CLI guide](cli.md#protos-run)) |
| Any other `protos package ...` invocation | Prints a bootstrap banner; performs no package operation |
| `init`, `add`, `install`, `update`, `lock`, `fetch`, `publish`, `build` | Not implemented |

There is no package registry, no network fetching, and no command that writes
`protos.lock` or `protos.project`.

## `protos package manifest`

Run from a project directory:

```sh
protos package manifest
```

The command:

1. opens exactly `protos.toml` in the current directory;
2. decodes it as UTF-8;
3. parses it as TOML 1.0 and validates manifest schema version 1;
4. reports the result without modifying any file.

It resolves no dependencies, reads no lockfile, uses no network, and never
rewrites the manifest.

### A minimal manifest

```toml
manifest-version = 1

[package]
id = "my-app"
version = "1.0.0"
```

```text
$ protos package manifest
protos.toml: valid schema v1
```

### Schema v1 in brief

| Table / field | Required | Content |
|---|---|---|
| `manifest-version` | yes | `1` |
| `[package]` `id` | yes | Non-empty opaque package identifier String |
| `[package]` `version` | yes | Release version String, `MAJOR.MINOR.PATCH[-PRERELEASE]` |
| `[package]` `locator` | no | Human-facing locator String, for example `"community/parser"` |
| `[compatibility]` `language` | when the table is present | Non-empty String; no constraint grammar is defined yet |
| `[exports]` | no | Export name to package-internal logical module name |
| `[dependencies]` | no | Registry, Git, or path dependency entries |
| `[workspace]` `members` | no | Workspace member package roots |

Unknown top-level tables or fields (`tool`, `build`, `scripts`, and so on) are
rejected. The full structural schema, including the exact dependency forms, is
[`PACKAGE_MANIFEST_SCHEMA_V1.md`](../../design/PACKAGE_MANIFEST_SCHEMA_V1.md).
Declaring a dependency is accepted by the validator; acquiring it is not a
public operation yet.

### Results and exit status

| Situation | stdout | stderr | Status |
|---|---|---|---|
| Valid manifest | `protos.toml: valid schema v1` | — | `0` |
| Missing file or not valid UTF-8 | — | `protos.toml: cannot read as UTF-8`, then `Package tool error: ...` | `1` |
| Invalid TOML or schema | — | `protos.toml: invalid TOML/schema v1`, then `Package tool error: ...` | `1` |
| Driver/bootstrap failure | — | `Package tool runtime error: ...` | `1` |

The second stderr line is the guest Error report from the driver. The
validator does not yet say *which* field or line is wrong.

## Authority

The Tool receives a filesystem capability confined to the current directory
and to the metadata files `protos.toml` and `protos.lock` (and their staging
files). It has no network capability and cannot read the rest of your project.

## Native and portable JVM

`protos package manifest` behaves identically in both distributions; the Tool
is ordinary Protos code shipped with the toolchain.

## Related design documents

The design behind future package operations is non-normative and does not
describe current commands:

- [`PACKAGE_TOOL_ARCHITECTURE.md`](../../design/PACKAGE_TOOL_ARCHITECTURE.md)
- [`PACKAGE_MANIFEST_FORMAT.md`](../../design/PACKAGE_MANIFEST_FORMAT.md)
- [`PACKAGE_LOCKFILE_FORMAT.md`](../../design/PACKAGE_LOCKFILE_FORMAT.md)
- [`PACKAGE_VERSION_RESOLUTION.md`](../../design/PACKAGE_VERSION_RESOLUTION.md)
- [`PACKAGE_IDENTITY_VERSIONING.md`](../../design/PACKAGE_IDENTITY_VERSIONING.md)
