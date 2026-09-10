"use strict";

const path = require("path");

const RUN_CURRENT_FILE_COMMAND = "protos.runCurrentFile";
const DEFAULT_RUNTIME_EXECUTABLE = "protos";

function createRunCurrentFile(vscode, pathModule = path) {
    return async function runCurrentFile() {
        if (!vscode.workspace.isTrusted) {
            await vscode.window.showErrorMessage(
                "Protos execution is disabled in Restricted Mode. Trust this workspace to run code."
            );
            return;
        }

        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            await vscode.window.showErrorMessage("Open a Protos file before running it.");
            return;
        }

        const document = editor.document;
        if (document.languageId !== "protos") {
            await vscode.window.showErrorMessage(
                "The active editor is not a Protos document."
            );
            return;
        }

        if (document.uri.scheme !== "file") {
            await vscode.window.showErrorMessage(
                "Run Current File requires a file-backed Protos document."
            );
            return;
        }

        if (document.isDirty) {
            const saved = await document.save();
            if (!saved) {
                await vscode.window.showWarningMessage(
                    "Protos run cancelled because the current file was not saved."
                );
                return;
            }
        }

        const configuredRuntime = vscode.workspace
            .getConfiguration("protos")
            .get("runtime.executable", DEFAULT_RUNTIME_EXECUTABLE);

        if (
            typeof configuredRuntime !== "string" ||
            configuredRuntime.trim().length === 0
        ) {
            await vscode.window.showErrorMessage(
                "Configure protos.runtime.executable with a Protos launcher executable."
            );
            return;
        }

        const sourcePath = document.uri.fsPath;
        const cwd = pathModule.dirname(sourcePath);
        const execution = new vscode.ProcessExecution(
            configuredRuntime,
            [sourcePath],
            { cwd }
        );

        const workspaceFolder = vscode.workspace.getWorkspaceFolder(document.uri);
        const taskScope = workspaceFolder || vscode.TaskScope.Workspace;
        const task = new vscode.Task(
            { type: "protos", command: "runCurrentFile" },
            taskScope,
            "Run Current Protos File",
            "Protos",
            execution,
            []
        );

        task.detail = sourcePath;
        task.presentationOptions = {
            reveal: vscode.TaskRevealKind.Always,
            panel: vscode.TaskPanelKind.Dedicated,
            clear: true,
            focus: false,
            showReuseMessage: false
        };

        try {
            await vscode.tasks.executeTask(task);
        } catch (error) {
            const detail =
                error instanceof Error ? error.message : String(error);
            await vscode.window.showErrorMessage(
                `Unable to start the Protos runtime: ${detail}`
            );
        }
    };
}

function activate(context) {
    const vscode = require("vscode");
    const disposable = vscode.commands.registerCommand(
        RUN_CURRENT_FILE_COMMAND,
        createRunCurrentFile(vscode)
    );
    context.subscriptions.push(disposable);
}

function deactivate() {}

module.exports = {
    activate,
    deactivate,
    createRunCurrentFile,
    RUN_CURRENT_FILE_COMMAND,
    DEFAULT_RUNTIME_EXECUTABLE
};
