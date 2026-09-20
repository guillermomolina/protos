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
