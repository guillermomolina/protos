#!/usr/bin/env python3
"""LM009-B/C structural validation for the VS Code reference extension."""

from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "package.json"
CONFIG = ROOT / "language-configuration.json"
GRAMMAR = ROOT / "syntaxes" / "protos.tmLanguage.json"
EXTENSION = ROOT / "extension.js"
README = ROOT / "README.md"

def fail(message):
    print("LM009_EDITOR_EXTENSION_VALIDATION_FAILED: " + message, file=sys.stderr)
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
        "main": "./extension.js",
    }
    for key, expected in expected_scalar.items():
        if package.get(key) != expected:
            fail("%s must be %r" % (key, expected))

    if package.get("engines") != {"vscode": "^1.104.0"}:
        fail("engines.vscode must remain exactly ^1.104.0")
    if package.get("categories") != ["Programming Languages"]:
        fail("category must remain Programming Languages")

    for forbidden in ("browser", "activationEvents", "scripts",
                      "dependencies", "devDependencies"):
        if forbidden in package:
            fail("LM009-C must not add %s" % forbidden)

    expected_capabilities = {
        "untrustedWorkspaces": {
            "supported": "limited",
            "restrictedConfigurations": ["protos.runtime.executable"],
        }
    }
    if package.get("capabilities") != expected_capabilities:
        fail("Workspace Trust capability must match the approved LM009-C policy")

    contributes = package.get("contributes")
    if not isinstance(contributes, dict):
        fail("contributes must be an object")
    if set(contributes.keys()) != {
        "languages", "grammars", "commands", "menus", "configuration"
    }:
        fail("unexpected VS Code contribution surface")

    if contributes["languages"] != [{
        "id": "protos",
        "aliases": ["Protos"],
        "extensions": [".protos"],
        "configuration": "./language-configuration.json",
    }]:
        fail("LM009-B language association changed")

    if contributes["grammars"] != [{
        "language": "protos",
        "scopeName": "source.protos",
        "path": "./syntaxes/protos.tmLanguage.json",
    }]:
        fail("LM009-B grammar contribution changed")

    if contributes["commands"] != [{
        "command": "protos.runCurrentFile",
        "title": "Run Current File",
        "category": "Protos",
        "enablement": (
            "editorLangId == protos && resourceScheme == file && "
            "isWorkspaceTrusted"
        ),
    }]:
        fail("Run Current File command contribution changed")

    if contributes["menus"] != {
        "commandPalette": [{
            "command": "protos.runCurrentFile",
            "when": (
                "editorLangId == protos && resourceScheme == file && "
                "isWorkspaceTrusted"
            ),
        }]
    }:
        fail("Run Current File command-palette trust/document gating changed")

    if contributes["configuration"] != {
        "title": "Protos",
        "properties": {
            "protos.runtime.executable": {
                "type": "string",
                "default": "protos",
                "scope": "machine",
                "description": (
                    "Protos launcher executable used by Run Current File. "
                    "Defaults to 'protos' resolved through PATH."
                ),
            }
        },
    }:
        fail("runtime executable configuration changed")

    if grammar.get("name") != "Protos" or grammar.get("scopeName") != "source.protos":
        fail("existing TextMate grammar identity changed unexpectedly")

    if config.get("comments") != {
        "lineComment": "//",
        "blockComment": ["/*", "*/"],
    }:
        fail("comment configuration changed")
    if config.get("brackets") != [["{", "}"], ["[", "]"], ["(", ")"]]:
        fail("bracket configuration changed")
    if config.get("autoClosingPairs") != [
        {"open": "{", "close": "}"},
        {"open": "[", "close": "]"},
        {"open": "(", "close": ")"},
    ]:
        fail("autoClosingPairs changed")
    if config.get("surroundingPairs") != [["{", "}"], ["[", "]"], ["(", ")"]]:
        fail("surroundingPairs changed")

    for unsupported in ("wordPattern", "indentationRules", "onEnterRules",
                        "folding", "colorizedBracketPairs"):
        if unsupported in config:
            fail("editor configuration must not guess semantics via %s" % unsupported)

    try:
        extension = EXTENSION.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
    except OSError as exc:
        fail(str(exc))

    required_extension_markers = (
        'RUN_CURRENT_FILE_COMMAND = "protos.runCurrentFile"',
        'DEFAULT_RUNTIME_EXECUTABLE = "protos"',
        "vscode.workspace.isTrusted",
        'document.languageId !== "protos"',
        'document.uri.scheme !== "file"',
        "await document.save()",
        '.get("runtime.executable", DEFAULT_RUNTIME_EXECUTABLE)',
        "new vscode.ProcessExecution(",
        "[sourcePath]",
        "pathModule.dirname(sourcePath)",
        "vscode.workspace.getWorkspaceFolder(document.uri)",
        "vscode.TaskScope.Workspace",
        "vscode.TaskRevealKind.Always",
        "vscode.TaskPanelKind.Dedicated",
        "vscode.tasks.executeTask(task)",
    )
    for marker in required_extension_markers:
        if marker not in extension:
            fail("extension.js missing approved policy marker: " + marker)

    for forbidden in (
        "child_process",
        "ShellExecution",
        "java -",
        "com.guillermomolina.protos.cli.ProtosCli",
        "protos run ",
    ):
        if forbidden in extension:
            fail("extension.js must not bypass the approved launcher boundary: " + forbidden)

    for marker in (
        "guillermomolina.protos",
        "^1.104.0",
        "Protos: Run Current File",
        "`protos.runtime.executable`",
        "ProcessExecution",
        "Restricted Mode",
        "S2 live VS Code check",
    ):
        if marker not in readme:
            fail("README missing LM009-C marker: " + marker)

    print("LM009_B_EXTENSION_VALIDATION: PASS")
    print("LM009_C_RUN_WIRING_VALIDATION: PASS")
    print("EXTENSION_ID=guillermomolina.protos")
    print("EXTENSION_VERSION=0.1.0")
    print("ENGINES_VSCODE=^1.104.0")
    print("RUNTIME_BOUNDARY=EXTERNAL_PROTOS_LAUNCHER")
    print("RUNTIME_SETTING=protos.runtime.executable")
    print("RUNTIME_DEFAULT=protos")
    print("RUN_EXECUTION=TASK_PROCESS_EXECUTION")
    print("RUN_CWD=SOURCE_PARENT")
    print("WORKSPACE_TRUST=LIMITED_RUN_GATED")
    print("APPLICATION_ARGUMENTS=NOT_INCLUDED")

if __name__ == "__main__":
    main()
