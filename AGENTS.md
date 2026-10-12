# Contributor instructions

## Branches and compatibility

- Preserve the target branch's supported Scala/Java versions and dependency compatibility.
- `0.12.x` retains ScalaPB 0.11.x, protobuf 3.x, json4s 4.0.x, and Java 8 support.
  Do not backport master's dependency upgrades or stricter parsing defaults without explicit approval.
- Preserve public APIs and previously accepted JSON inputs on maintenance releases.
  Call out intentional behavior changes, including changes to invalid-input handling.
- Read exact dependency versions and MiMa baselines from the build files; do not duplicate them here.

## Implementation and validation

- Add focused regression tests for parser fixes; compare with protobuf-java where appropriate.
- Edit test `.proto` files rather than generated Scala or Java sources.
- Run relevant tests for code changes. For changes affecting compatibility, run
  `sbt +test +mimaReportBinaryIssues`.
- Follow existing formatting; check with `sbt scalafmtCheckAll scalafmtSbtCheck`.

## Changelog and releases

- Update `CHANGELOG.md` under `Unreleased` for user-visible changes.
  Create the file if the target branch does not have one.
- Describe observable behavior, compatibility implications, and migration steps
  rather than implementation details.
- Release versions come from Git tags. Do not tag or publish releases without explicit authorization.
