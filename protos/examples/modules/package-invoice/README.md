# Invoice with workspace-member MODULE exports

This package project calculates a small invoice using a root-owned `Billing`
MODULE, a root-private `Billing/Rules.protos` SOURCE, and a workspace dependency
whose **only** public export is `Rates = "Public/Rates"`.

Its shape:

```text
package-invoice/
  package.toml             # root: [exports] Invoice = "Billing"
  protos.lock              # exact workspace dependency and resolution digest
  Main/module.protos       # public package entry
  Billing/module.protos    # private discount, published invoiceTotal Closure
  Billing/Rules.protos     # private root SOURCE, handling fee
  rates/package.toml       # member: [exports] Rates = "Public/Rates"
  rates/Public/Rates/module.protos
  rates/Private/RateTable.protos
```

From **the `package-invoice/` directory** with the current source-HEAD
executable:

```sh
/path/to/protos/bin/protos package manifest
/path/to/protos/bin/protos run Main
```

Expected output is `invoice-total=49` (subtotal 40 + handling 7 - private
discount 3 + dependency delivery 5). `Main` also checks the cached identity of
`self:Billing` and `context !== this` captured at initialization.

These are intentionally rejected package entry points, launched **from the
same directory**:

```sh
/path/to/protos/bin/protos run CheckPrivate
/path/to/protos/bin/protos run CheckDepPrivate
/path/to/protos/bin/protos run CheckDepSource
/path/to/protos/bin/protos run CheckMissingInitializer
```

Each is expected to fail with a language-level Error: an unexported initializer
binding, an undeclared dependency MODULE route, forbidden direct dependency
SOURCE access, and a directory without mandatory `module.protos`, respectively.
The DOC020 Java integration test asserts their nonzero outcomes; they are not
standalone CLI programs. `dep:rates/Rates` is authorized by the member's exact
`[exports]` key; the directory path `Private`, its SOURCE file, and spelling
variants are not package exports.

The repository currently provides no public lock-generation, install, fetch or
publish command. This example includes the exact `protos.lock` matching its
root and workspace member; `protos.project` is not needed for this local run.
The current package ContentIdentity method is `protos-package-tree-v2` (for
content-addressed package trees), while `protos-resolution-input-v1` determines
the lock header; they are distinct algorithms.
See the [modules guide](../../../../docs/guide/06-modules-and-imports.md) and
[Package Tool guide](../../../../docs/guide/tools/package-tool.md).
