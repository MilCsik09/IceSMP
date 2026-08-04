# Runtime hardening audit — 2026-08-03/04

## Root causes and fixes

- **DARK territory daylight:** protection was behind mob-scaling early returns and only ran at spawn. It is now an independent,
  reversible capability reconciled on spawn, entity load, movement and teleport. Baseline vanilla flags are restored on exit;
  event/boss PDC protection takes precedence. Only daylight combustion is cancelled, never block/entity fire globally.
- **Vanish:** `icesmp.moderation.vanish.see` was inherited by every OP/moderation super-node, so the usual admin tester was
  explicitly exempt from hiding and the feature appeared to do nothing. The observer permission is now explicit-only. Vanish
  removes both the tracked entity (`hidePlayer`) and the per-viewer player-list entry (`unlistPlayer`), reasserts both after
  tracking rebuilds, and restores only IceSMP-owned visibility pairs.
- **Claim wall:** the BlockDisplay renderer used four stretched slabs anchored to the viewer's Y, so terrain changes made the
  glass float above the area or disappear below it. Every boundary column is now region-owned and resolved from the actual
  `MOTION_BLOCKING_NO_LEAVES` surface, producing the configured wall height along the full perimeter.
- **Claim selection:** the old player flow only accepted two corners and therefore only rectangles. Normal claims now also support
  territory-style multi-point polygons, including concave shapes. One immutable `ClaimShape` drives exact column membership,
  overlap, pricing, WorldGuard row-span checks, persistence and boundary rendering; rectangle/quick claims remain compatible.
- **DARK undead footing:** ambient DARK spawns used a single `getHighestBlockYAt()+1` candidate without a stable-floor contract.
  They now retry finitely inside the exact territory shape and require an occluding, non-gravity, non-hazard floor plus three
  passable body blocks through the shared spawn guard. No valid column means no spawn; there is no airborne fallback.
- **World events:** the same stable standing-location resolver now backs bounded event searches in addition to player, spawn,
  border, territory/claim/region, loaded-chunk and reservation rules.
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
