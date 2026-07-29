# Native crate and sit integration verification

## Immutable integration state

- Integration target before crate merge: `2432e031a1f63fa4d77bcc5ac25245de597b2675`
- Target includes merged sit-only PR #47.
- Reviewed crate branch before integration: `11fcaffa2903e24d275c9466c2fdff52b90ce1fb`
- Common pre-feature target: `2dede0eee5513a9102be6d22140d572aa7ee1513`

The integration uses a real three-way merge. It preserves the sit-only runtime and the reviewed crate
settlement/recovery implementation while keeping the target CI workflow unchanged.

## Verified checks

The integrated source passed locally with the retained Gradle cache:

- `./gradlew crateRegressionTest sitRegressionTest --offline --no-daemon --stacktrace`;
- `./gradlew clean build --offline --no-daemon --stacktrace --no-configuration-cache`;
- crate, sit, moderation, MOTD, persistence and DEV regressions through Gradle `check`;
- `python3 scripts/check_consistency.py` with `0 FAIL / 0 WARN`;
- `git diff --check`.

A normal remote CI run is required on the published integration commit before PR #49 is merged.

## Guarantee boundary

This proves repository-level lifecycle, validation, scheduler, cleanup and recovery invariants. It
does not claim a distributed transaction or process-crash exactly-once guarantee. CrazyCrates and
GSit remain conditionally removable only after their documented real Folia and fault-injection
playtests succeed.
