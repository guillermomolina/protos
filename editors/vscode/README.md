# Protos VS Code integration

This directory is the approved current home for the Protos reference VS Code
integration.

The extension is intentionally thin and declarative at this stage. Protos
language semantics remain owned by `../../spec/PROTOS_GRAMMAR.md` and the real
parser/runtime/resolver authorities; the VS Code surface must not become a
second implementation of the language.

Current LM009-B assets:

- `package.json` — declarative VS Code extension manifest;
- `language-configuration.json` — comments and structural delimiter behavior;
- `syntaxes/protos.tmLanguage.json` — non-normative TextMate grammar;
- `test/fixtures/lexical.protos` — representative valid Protos source;
- `test/validate_grammar.py` — lexical-asset structural guard; and
- `test/validate_extension.py` — manifest/language-association/configuration
  structural guard.

The approved manifest identity and compatibility floor are:

```text
extension id:    guillermomolina.protos
extension name:  protos
publisher:       guillermomolina
extension ver.:  0.1.0
engines.vscode:  ^1.104.0
language id:     protos
file extension:  .protos
TextMate scope:  source.protos
```

The extension version is intentionally independent from the Protos Maven/runtime
implementation version. The VS Code support floor is not raised merely because a
new Stable version exists; a future increase requires a concrete editor API or
dependency need introduced after VS Code 1.104.

## Local validation

From `editors/vscode/`:

```sh
python3 test/validate_grammar.py
python3 test/validate_extension.py
```

These checks require only Python and do not add Node/npm to ordinary Protos
Maven/runtime development.

## S1 live VS Code check

The manifest can be loaded directly as an extension-development path without
publishing anything to Marketplace:

```sh
cd editors/vscode
code --new-window --extensionDevelopmentPath="$PWD"
```

In that VS Code window, open `test/fixtures/lexical.protos` and verify:

1. the language mode is **Protos**;
2. `.protos` receives the `source.protos` TextMate grammar;
3. `//` and `/* ... */` are recognized as the configured comment forms; and
4. `()`, `[]`, and `{}` use the structural bracket configuration.

That live editor observation is the remaining LM009-B S1 closure evidence after
this manifest-wiring tranche publishes. The repository-side validator proves the
declarative wiring but does not claim to substitute for an actual VS Code host.

No VSIX/Marketplace release is performed by LM009-B, and no Run action, DAP/LSP
client, runtime-discovery policy, formatter, parser, or TypeScript semantic model
is introduced here.
