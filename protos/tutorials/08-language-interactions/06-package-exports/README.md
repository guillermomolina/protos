# Lesson 06 — Package MODULE exports

This is a real root-package fixture, **not** a standalone SOURCE file. It
includes `package.toml`, `protos.lock`, `Main/module.protos` and
`Prices/module.protos` plus the private SOURCE `Prices/Rules.protos`.

From this directory, use the current source-HEAD `bin/protos` executable through
its absolute path (or on your `PATH`):

```sh
/path/to/protos/bin/protos package manifest
/path/to/protos/bin/protos run Main
```

Expected output from `run Main`: `package-total=44`. It checks that the
imported MODULE object is cached in the Actor, that `context` differs from
`this` in the MODULE initializer, and that the private SOURCE helper is
captured by the public Closure. To see ordinary missing-public-slot failure,
run `protos run CheckPrivate` from this directory; it should fail with a
language-level Error, because `Prices.secretDiscount` was not published.

The manifest uses schema v1 (`manifest-version = 1`) and explicitly maps the
external logical name `Prices` to the internal directory `Prices` containing
`module.protos`. This does not export the `Rules.protos` SOURCE. The lockfile is
provided; the CLI does not generate locks, install dependencies or fetch code.
See [Modules and imports](../../../../docs/guide/06-modules-and-imports.md).
