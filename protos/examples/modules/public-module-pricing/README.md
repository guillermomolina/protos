# Public MODULE pricing service

A simple reusable quote operation (`Prices.quote(40) == 42`) using a MODULE
initializer with a private lexical discount and a private SOURCE rules file.
Only `this.quote` and `this.separateContexts` become the MODULE's public own
slots. Consumers import `self:Prices` and cannot read `Prices.privateMargin`.

From **this directory** with the current source-HEAD toolchain:

```sh
/path/to/protos/bin/protos package manifest
/path/to/protos/bin/protos run Main
```

Expected output: `price=42`. For the expected negative case:

```sh
/path/to/protos/bin/protos run CheckPrivate
```

This must return a nonzero status because a MODULE private initializer binding
is absent from the public module object. Do not run `Main/module.protos` with
the standalone file launcher: it is a package MODULE entry, not a SOURCE entry.

`package.toml` is the only accepted manifest filename and the matching
`protos.lock` is checked in. The current CLI does not generate locks or install
packages. The original [direct-file example](../direct-file-import/main.protos)
is the comparison case: its `.protos` import yields a complete SOURCE context.
