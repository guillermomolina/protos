# Package Tool

**D242/I152:** The current manifest is `package.toml` (TOML 1.0,
`manifest-version = 1`). An exact root `protos.toml` entry is forbidden,
even if `package.toml` also exists; symlinks are rejected without following.
The bundled tool operates through the same confined Filesystem and performs
no fallback to the old filename.

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
| `protos package manifest` | Implemented: validates `package.toml` in the current directory |
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

1. opens exactly `package.toml` in the current directory;
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
package.toml: valid schema v1
```

### Schema v1 in brief

| Table / field | Required | Content |
|---|---|---|
| `manifest-version` | yes | `1` |
| `[package]` `id` | yes | Non-empty opaque package identifier String |
| `[package]` `version` | yes | Release version String, `MAJOR.MINOR.PATCH[-PRERELEASE]` |
| `[package]` `locator` | no | Human-facing locator String, for example `"community/parser"` |
| `[compatibility]` `language` | when the table is present | Non-empty String; no constraint grammar is defined yet |
| `[exports]` | no | Public logical module to internal module directory containing `module.protos` |
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
| Valid manifest | `package.toml: valid schema v1` | — | `0` |
| Missing file or not valid UTF-8 | — | `package.toml: cannot read as UTF-8`, then `Package tool error: ...` | `1` |
| Invalid TOML or schema | — | `package.toml: invalid TOML/schema v1`, then `Package tool error: ...` | `1` |
| Legacy `protos.toml` entry, alone or alongside `package.toml` | — | `protos package: protos.toml is forbidden; use package.toml` | `1` |
| Driver/bootstrap failure | — | `Package tool runtime error: ...` | `1` |

The second stderr line is the guest Error report from the driver. The
validator does not yet say *which* field or line is wrong.

## Runnable learning projects

The [MODULE/SOURCE guide](../06-modules-and-imports.md) explains the public
object/private initializer boundary, and these checked-in projects include both
a manifest and matching `protos.lock`:

- [Module-exports tutorial](../../../protos/tutorials/08-language-interactions/06-package-exports/package.toml)
  (one root package, `self:` and private SOURCE helper);
- [pricing example](../../../protos/examples/modules/public-module-pricing/package.toml)
  (a reusable MODULE with an encapsulated discount rule);
- [invoice workspace example](../../../protos/examples/modules/package-invoice/package.toml)
  (root and workspace-member manifests, explicit `[exports]`, authorized
  `dep:rates/Rates`, negative private-path/source-access cases).

From **each project's root directory** use the current build's executable:

```sh
/path/to/protos/bin/protos package manifest
/path/to/protos/bin/protos run Main
```

The standalone `bin/protos <file>` launcher instead selects a SOURCE entry and
does **not** provide the package resolver. A valid manifest alone is
insufficient for `run`: preflight also loads a consistent `protos.lock`, and
there is no public command to generate one. `protos.project` retains its
filename. The current package content-identity method is
`protos-package-tree-v2`; neither `protos-resolution-input-v1` nor
`protos-project-metadata-v1` changed its method name. Behavior described here
is for source HEAD, not necessarily older published releases.

## Authority

The Tool receives a filesystem capability confined to the current directory
and to the metadata files `package.toml` and `protos.lock` (and their staging
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
