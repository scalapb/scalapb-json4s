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

- Maintain the canonical `CHANGELOG.md` on master for all release lines, including `0.12.x`.
- Record user-visible changes in the relevant release line's `Unreleased` section on master,
  even when the code change targets a maintenance branch. Do not create separate branch changelogs.
- Keep released entries in master's changelog as the shared release history.
- Describe observable behavior, compatibility implications, and migration steps
  rather than implementation details.
- Release versions come from Git tags. Do not tag or publish releases without explicit authorization.
