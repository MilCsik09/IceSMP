# Runtime hardening audit — 2026-08-03/04

## Root causes and fixes

- **DARK territory daylight:** protection was behind mob-scaling early returns and only ran at spawn. It is now an independent,
  reversible capability reconciled on spawn, entity load, movement and teleport. Baseline vanilla flags are restored on exit;
  event/boss PDC protection takes precedence. Only daylight combustion is cancelled, never block/entity fire globally.
- **Vanish:** the visibility ledger prevented `hidePlayer` from being reissued after client retracking. Hide is now idempotently
  reasserted after join, teleport, world change and respawn. Invulnerability is not stored on the Player; damage immunity is an
  explicit event capability and is removed automatically when vanish is disabled.
- **Claim geometry:** membership and rendering independently used stored Y bounds and drew a 3D box. Both now consume one
  normalized, inclusive X–Z `ClaimFootprint`; legacy Y fields are persistence-only. Preview tasks are single-owner and cleaned on
  replacement/logout.
- **World events:** invasions spawned at the selected player and bosses used a hard-coded 24–40 block ring. A shared bounded
  guard now enforces all relevant players, world spawn, world border, territory/claim/region, loaded chunk, safe surface and
  concurrent-event reservations. No valid candidate means a logged, controlled abort — never a close fallback.
- **Profession recipes:** one exact fishing-rod duplicate was removed. Bukkit-owned recipe keys are cleaned on reload/disable,
  semantic validation is deterministic, and catalog reload now builds an immutable private candidate before one atomic snapshot
  publication. Rejected reloads preserve the previous working generation instead of exposing empty or partial maps.
- **Config GUI:** edits are staged per admin and saved as one optimistic-concurrency transaction. Cancel, close and logout write
  nothing; stale sessions cannot overwrite a second admin or external file edit. Entry type/default/range and mandatory runtime
  coverage are build-validated.
- **GUI/resource-pack icons:** every referenced namespaced item model is checked against both the documented manifest and the
  checked-in pack. Public model identifiers were not renumbered; `PAPER` remains the explicit vanilla fallback.

## Measured audit results

- Config schema scalar entries: **9592**
- GUI-displayed entries: **203**
- Intentionally file/command-only entries: **9389**
- Missing, stale or duplicate GUI entries: **0 / 0 / 0**
- Profession recipes: **437** after removal of one proven duplicate
- Profession recipe key duplicates / semantic duplicates: **0 / 0**
- Used GUI/item models: **269**
- Manifest models / checked-in pack models considered by the validator: **298 / 298**
- Missing manifest / pack mappings: **0 / 0**

## Automated validation evidence

The final review tree was tested after all implementation-only workflow scripts and encoded payloads had been deleted:

- `./gradlew clean check --console=plain --no-daemon --stacktrace` — **BUILD SUCCESSFUL**;
- 30 Gradle tasks: 29 executed, 1 up-to-date;
- runtime hardening, event-spawn safety, config transaction/coverage and profession recipe audit suites passed;
- all existing repository regression suites included by `check` passed;
- `scripts/validate_gui_icons.py` — `used=269`, `missing_manifest=0`, `missing_pack=0`;
- `scripts/check_consistency.py` — **0 FAIL, 0 WARN** across 160 quests, 269 item models,
  16 permission nodes and 147 command names;
- implementation scaffolding cleanup commit: `167c711ec8a83f84cf19dc7bc3990827688741b2`.

The Java compiler still reports nine pre-existing deprecation/removal warnings in unrelated APIs; no new compile warning was
introduced by this scope.

## Manual runtime checks still required

Paper/Folia integration tests cover pure geometry/policy and source contracts. A staging server should additionally verify packet-
level tablist/nametag behaviour with the production scoreboard plugin set, particle appearance over uneven terrain, WorldGuard
region integration, real chunk unload/reload and restart transitions for DARK mobs, and a full resource-pack client join. These
checks are visual or server-integration-specific and are not claimed as fully automated.
