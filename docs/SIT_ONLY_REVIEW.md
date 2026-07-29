# Native sitting review boundary

**Scope:** stable sitting only.

The native sitting replacement keeps `/sit`, `/sit fel`, click-to-sit, supported block-shape geometry, access policy, atomic reservation and complete transient lifecycle cleanup.

The following features are explicitly outside the retained product scope and have no command, configuration, permission, message, runtime or test wiring in this branch:

- lay / lying;
- crawl;
- `LayPoseBridge` or LibsDisguises pose integration;
- NMS or reflection pose support;
- player/NPC sitting and stacking;
- full GSit compatibility.

## Automated review boundary

The sit-only branch is based directly on target commit `2dede0eee5513a9102be6d22140d572aa7ee1513`. The review checks require:

- `./gradlew sitRegressionTest --no-daemon --stacktrace`;
- `./gradlew clean build --no-daemon --stacktrace`;
- `python3 scripts/check_consistency.py`;
- `git diff --check`;
- source guards proving the removed pose vocabulary and wiring are absent.

Passing CI proves code-review and repository consistency. It does not by itself prove that GSit can be removed from production. Real Folia playtests remain required for concurrent reservations, all supported block shapes, scheduler retirement, damage/sneak/break/teleport/death/quit/kick/dismount cleanup, reload/disable cleanup and transient seat-entity removal.
