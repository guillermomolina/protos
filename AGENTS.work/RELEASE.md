# Protos release publication

These instructions apply to release/distribution publication (`DISTxxx` and
any release-adjacent work).

Investigation contract: the release policy surface below plus the validation
policy in `AGENTS.work/IMPLEMENTATION.md` for the exact candidate revision.

## Release publication discipline

Implementation publication and user-facing release publication are separate
operations.

A Maven implementation version ending in `-SNAPSHOT` is an exact development
trace point, not an instruction to create a Git tag, GitHub Release, release
asset, compatibility promise, or support window. Agents MUST NOT publish a
release merely because the implementation version advanced, tests passed, an
implementation slice closed, or `main` was successfully pushed.

Public releases are selected milestone revisions. They may skip any number of
internal implementation versions. Creating a tag or GitHub Release requires an
explicit release decision for the exact candidate revision; ordinary permission
to commit/push implementation work does not imply permission to release it.

CI snapshot artifacts, when configured, are development artifacts rather than
releases. Agents MUST keep that distinction visible in names, documentation,
reports, and automation.

While Core v0.1 remains a draft, public GitHub Releases should normally be marked
as pre-releases unless an explicit project decision changes the release channel.

A distributable Protos build MUST declare and validate its supported runtime/JDK
contract. Do not advertise generic Java compatibility merely because the shaded
JAR can start on a particular JDK; Truffle/Graal optimizing-runtime support and
fallback execution must not be conflated without evidence.

Release automation MUST preserve exact source-revision identity, required
validation, license/notices, runtime requirements, release metadata, and artifact
checksums. It MUST NOT automatically turn every successful `main` revision or
implementation-version bump into a public release.

The complete non-normative release policy and DIST001 slice boundaries are in
`guillermomolina/protos-project-docs:docs/project/work/DIST001/DIST001_RELEASE_POLICY.md`.

## Public release announcements

For every explicitly authorized public Protos release, agents MUST prepare the
public announcements as follows:

1. Derive announcement content from the exact published release identity,
   release notes, verified artifacts, accepted validation evidence, and relevant
   published changes.
2. Preserve the distinction between implementation snapshots and public
   releases.
3. Prepare the canonical English release announcement for GitHub Discussions —
   Announcements
   (`https://github.com/guillermomolina/protos/discussions/categories/announcements`).
4. Prepare a separate English Reddit publication for `r/Protos`
   (`https://www.reddit.com/r/Protos/`), using the `Release` post flair.
5. Prepare a concise English X publication for `@ProtosLang`
   (`https://x.com/ProtosLang`), suitable for the ordinary account's post-length
   limit.
6. Include the exact GitHub Release URL in both social publications.
7. Include links to the canonical GitHub Discussion or the official website
   (`https://protoslang.com/`) when useful.
8. Keep public statements factual, proportionate, and limited to published
   evidence.
9. Preserve experimental and prerelease qualifications where applicable.
10. Do not claim performance improvements, platform compatibility, production
    readiness, or implementation completeness without corresponding evidence.
11. Present the prepared content to the project owner for publication.
12. After human publication, record the actual URLs in the owning release Issue
    when available.
13. Explicitly report any pending publication rather than fabricating success.

The website already displays canonical GitHub announcements and releases
dynamically. It does not require a separate authored news copy or a
website-image rebuild for each announcement.

Reddit and X publication is a communication step after canonical release
publication, not a new release-artifact acceptance gate. This procedure does not
require new credentials, tokens, API integrations, automated posting scripts, or
social account management.
