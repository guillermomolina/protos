#!/usr/bin/env python3
"""LM009-B structural validation for the declarative VS Code extension wiring."""

from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "package.json"
CONFIG = ROOT / "language-configuration.json"
GRAMMAR = ROOT / "syntaxes" / "protos.tmLanguage.json"
README = ROOT / "README.md"

def fail(message):
    print("LM009_B_EXTENSION_VALIDATION_FAILED: " + message, file=sys.stderr)
    raise SystemExit(2)

def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail("%s: %s" % (path, exc))

def main():
    package = read_json(PACKAGE)
    config = read_json(CONFIG)
    grammar = read_json(GRAMMAR)

    expected_scalar = {
        "name": "protos",
        "displayName": "Protos",
        "version": "0.1.0",
        "publisher": "guillermomolina",
        "license": "APL-1.0",
    }
    for key, expected in expected_scalar.items():
        if package.get(key) != expected:
            fail("%s must be %r" % (key, expected))

    if package.get("engines") != {"vscode": "^1.104.0"}:
        fail("engines.vscode must remain exactly ^1.104.0")
    if package.get("categories") != ["Programming Languages"]:
        fail("category must remain Programming Languages")

    for forbidden in ("main", "browser", "activationEvents", "scripts",
                      "dependencies", "devDependencies"):
        if forbidden in package:
            fail("declarative LM009-B manifest must not contain %s" % forbidden)

    contributes = package.get("contributes")
    if not isinstance(contributes, dict):
        fail("contributes must be an object")
    if set(contributes.keys()) != {"languages", "grammars"}:
        fail("LM009-B contributes only languages and grammars")

    languages = contributes["languages"]
    if languages != [{
        "id": "protos",
        "aliases": ["Protos"],
        "extensions": [".protos"],
        "configuration": "./language-configuration.json",
    }]:
        fail("language association must be the exact approved Protos mapping")

    grammars = contributes["grammars"]
    if grammars != [{
        "language": "protos",
        "scopeName": "source.protos",
        "path": "./syntaxes/protos.tmLanguage.json",
    }]:
        fail("grammar contribution must bind protos to source.protos")

    if grammar.get("name") != "Protos" or grammar.get("scopeName") != "source.protos":
        fail("existing TextMate grammar identity changed unexpectedly")

    if config.get("comments") != {
        "lineComment": "//",
        "blockComment": ["/*", "*/"],
    }:
        fail("comment configuration must follow the normative delimiters")
    if config.get("brackets") != [["{", "}"], ["[", "]"], ["(", ")"]]:
        fail("bracket configuration must contain only structural delimiter pairs")
    if config.get("autoClosingPairs") != [
        {"open": "{", "close": "}"},
        {"open": "[", "close": "]"},
        {"open": "(", "close": ")"},
    ]:
        fail("autoClosingPairs must remain limited to structural delimiters")
    if config.get("surroundingPairs") != [["{", "}"], ["[", "]"], ["(", ")"]]:
        fail("surroundingPairs must remain limited to structural delimiters")

    for unsupported in ("wordPattern", "indentationRules", "onEnterRules",
                        "folding", "colorizedBracketPairs"):
        if unsupported in config:
            fail("LM009-B must not guess editor semantics via %s" % unsupported)

    try:
        readme = README.read_text(encoding="utf-8")
    except OSError as exc:
        fail(str(exc))
    for marker in (
        "guillermomolina.protos",
        "^1.104.0",
        "--extensionDevelopmentPath",
        "S1 live VS Code",
    ):
        if marker not in readme:
            fail("README missing manifest/S1 marker: " + marker)

    print("LM009_B_EXTENSION_VALIDATION: PASS")
    print("EXTENSION_ID=guillermomolina.protos")
    print("EXTENSION_VERSION=0.1.0")
    print("ENGINES_VSCODE=^1.104.0")
    print("LANGUAGE_ASSOCIATION=.protos->protos")
    print("GRAMMAR_BINDING=protos->source.protos")
    print("LANGUAGE_CONFIGURATION=PASS")
    print("DECLARATIVE_ONLY=PASS")

if __name__ == "__main__":
    main()
