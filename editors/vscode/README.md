# Protos editor assets

This directory is the approved current home for Protos editor integration.

`LM009-B` currently publishes only reusable, non-normative lexical assets:

- `syntaxes/protos.tmLanguage.json` — TextMate syntax grammar;
- `test/fixtures/lexical.protos` — representative valid Protos source;
- `test/validate_grammar.py` — bounded structural guard for the lexical asset.

The normative lexical and syntactic authority remains
`../../spec/PROTOS_GRAMMAR.md`. These files do not define Protos and must not be
used to add syntax that the normative grammar does not accept.

There is intentionally **no `package.json` yet**. VS Code requires an
`engines.vscode` compatibility range for an installable extension, and LM009-B
keeps that support-floor choice behind its explicit approval checkpoint. Until
that decision is made, this directory is not an installable VS Code extension.

Later LM009 work may add the thin VS Code manifest, language association,
language configuration, run/debug integration and language-service client
wiring. None of those surfaces should duplicate the Protos parser or semantic
model in TypeScript.
