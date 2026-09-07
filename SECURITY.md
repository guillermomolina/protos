# Security Policy

## Project status

Protos is under active development and the Core v0.1 specification remains a
draft. The project does not currently make a production-security or long-term
support guarantee.

Security reports are nevertheless welcome and should be treated as engineering
issues to investigate carefully.

## Supported versions

The current `main` branch is the primary supported development line.

Until Protos starts publishing explicitly supported release lines, older
snapshots and arbitrary historical commits should not be assumed to receive
security fixes.

## Reporting a vulnerability

Please do **not** publish exploit details, credentials, private data, or a
working vulnerability demonstration in a normal public issue or Discussion.

Preferred reporting path:

1. Open the repository's **Security** tab.
2. Use **Report a vulnerability** if GitHub private vulnerability reporting is
   available.
3. Include the affected revision/version, impact, reproduction conditions, and
   the smallest useful technical evidence.

If the repository does not expose a private vulnerability-reporting path, open a
minimal public issue asking the maintainer to establish a private reporting
channel. Do not include the vulnerability details in that issue.

## What to expect

A report may involve the language implementation, bundled tools, dependency or
package handling, sandbox/capability boundaries, build or release
infrastructure, or documentation that creates a security-relevant
misunderstanding.

The project will evaluate severity and remediation based on the actual impact
and the current maturity of the affected component. A report is not considered
resolved merely because a generated fix passes a test; the same review,
specification, and validation expectations used elsewhere in Protos apply to
security changes.
