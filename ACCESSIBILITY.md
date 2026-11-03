# Accessibility at Protos

Protos is an experimental prototype-based programming language. We want people with different accessibility needs to be able to learn Protos, write and run programs, understand diagnostics, use its development tools, and participate in the project.

Accessibility is part of the usability of a programming language and its surrounding tools. We welcome reports of barriers, including reports from people who cannot provide a complete technical reproduction.

This document describes our accessibility priorities, the relevant project surfaces, and how to provide feedback. It is not a declaration that every Protos tool or resource is accessible.

## Scope

Accessibility concerns may involve different parts of the Protos ecosystem:

- **Language documentation:** the README, programming guides, tutorials, examples, API documentation, diagrams, and other learning materials.
- **Command-line experience:** the `protos` CLI, interactive REPL, help output, errors, diagnostics, and bundled developer tools.
- **Language tooling:** the formatter, linter, language server, and debugger integration points.
- **Editor integration:** the official Protos VS Code extension and development environment.
- **Public website:** documentation presentation, navigation, readable content, and interactive website features.
- **Community participation:** project instructions, issue reporting, discussions, and contribution documentation.

These surfaces have different implementations and ownership boundaries. The Protos language and its normative specification remain independent of any particular editor, terminal, browser, user interface, or host runtime.

Accessibility improvements to documentation or tools do not by themselves introduce or change Protos language semantics.

## Accessibility priorities

When developing or improving Protos-facing resources, we aim to consider:

- Clear language, descriptive headings, meaningful links, and readable documentation structure.
- Text alternatives for informative images, diagrams, and other visual explanations.
- Source examples and instructions that remain understandable without relying exclusively on visual presentation.
- Error messages and diagnostic information that can be understood without depending solely on color, highlighting, or visual symbols.
- Keyboard-based operation and understandable focus behavior in project-controlled interactive interfaces.
- Readability, contrast, text resizing, and reduced-motion preferences where applicable to graphical or web interfaces.
- Practical alternatives when a particular presentation or workflow creates an accessibility barrier.

These are improvement priorities, not claims that every surface currently satisfies them.

For web-facing content, the [Web Content Accessibility Guidelines (WCAG) 2.2](https://www.w3.org/TR/WCAG22/) provide a useful reference. They are not a language-runtime specification, and this statement does not claim WCAG conformance for Protos or its website.

## Current accessibility status and limitations

Protos is under active development. A comprehensive accessibility assessment covering its documentation, website, command-line tools, editor integration, and community workflows has not been established.

We do not currently claim:

- A verified WCAG conformance level for the complete Protos ecosystem.
- Certified compatibility with particular screen readers or other assistive technologies.
- Accessibility guarantees across all terminal emulators, operating systems, browsers, editors, or input devices.
- That accessibility characteristics of GraalVM, Truffle, Java, VS Code, or other dependencies automatically apply to Protos.

Individual capabilities and limitations should be described in the documentation of the component that owns them.

For example, the Protos linter supports text and JSON diagnostic output, as described in the [CLI guide](docs/guide/tools/cli.md). A textual diagnostic workflow may provide an alternative to an editor presentation in some environments, but it does not establish universal accessibility.

Confirmed accessibility problems and available workarounds can be recorded in the appropriate repository's issues. The absence of a reported issue does not establish that a component has been tested or is free of accessibility barriers.

## Reporting an accessibility barrier

Accessibility reports are welcome, whether they concern an existing feature, a missing alternative, or difficulty using project documentation.

Use the reporting destination associated with the affected surface:

- **Protos language, documentation, CLI, REPL, language server, bundled tools, and runtime diagnostics:** [Protos Issues](https://github.com/guillermomolina/protos/issues).
- **Website navigation, layout, presentation, and website-specific interactions:** [Protos Website Issues](https://github.com/guillermomolina/protos-website/issues).
- **VS Code extension-specific behavior and editor integration:** [Protos VS Code Extension Issues](https://github.com/guillermomolina/protos-vscode-extension/issues).
- **Questions, uncertain ownership, or reports that are difficult to formulate as a reproducible issue:** [Protos Discussions](https://github.com/guillermomolina/protos/discussions).

If a problem appears in documentation displayed on the website, use the main Protos repository for problems with the maintained source content, and the website repository for problems caused by its presentation.

You do not need to determine the underlying implementation responsible for a problem before reporting it.

### Useful information to include

Share whatever information is available and relevant:

- The affected Protos component, documentation page, command, or workflow.
- What you were trying to accomplish.
- What prevented you from completing the task.
- What you expected to happen.
- Steps or an example that demonstrate the barrier, when possible.
- The affected version, environment, browser, terminal, or editor, if relevant.
- Any workaround or alternative presentation that would help.

Information about a screen reader, magnification tool, keyboard workflow, or other assistive technology can be useful, but is optional.

A textual description is sufficient to start a discussion. Screenshots, videos, medical information, disability diagnoses, and personal details are not required.

Existing bug-report forms may request a minimal reproduction. If you cannot provide one, start with a Discussion explaining the barrier instead.

## Privacy and sensitive reports

GitHub Issues and Discussions are public. Do not include credentials, personal medical information, private documents, or other sensitive information in a public report.

If an accessibility concern requires private discussion, you may make a minimal public request asking the maintainers to arrange an appropriate reporting channel, without disclosing the sensitive details.

The project does not currently publish a separate, verified private accessibility contact channel. GitHub-based reporting may itself be an accessibility limitation for some users.

For security vulnerabilities, follow the [Security Policy](SECURITY.md) rather than posting vulnerability details publicly.

For concerns about community conduct, see the [Code of Conduct](CODE_OF_CONDUCT.md).

## Handling accessibility feedback

Accessibility reports are considered alongside other usability, documentation, and engineering issues.

Depending on the affected component, follow-up work may involve a documentation correction, an improved diagnostic, a tooling change, a website or editor integration change, or a documented workaround.

Reports should be tracked in the repository that owns the relevant implementation or documentation. When a problem spans multiple repositories, coordination should identify the responsible work without duplicating ownership.

Protos is an experimental open-source project maintained on a best-effort basis. No specific response time, remediation deadline, accessibility certification, or production-support guarantee is implied.

For general project support and contribution guidance, see [SUPPORT.md](SUPPORT.md) and [CONTRIBUTING.md](CONTRIBUTING.md).
