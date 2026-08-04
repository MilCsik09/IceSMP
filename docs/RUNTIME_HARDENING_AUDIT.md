# Runtime hardening audit — corrected scope, 2026-08-03/04

## Reported failures and actual root causes

- **Vanish did not hide the player at all:** `icesmp.moderation.vanish.see` was inherited by the OP/moderation/admin-all
  permission bundles. The usual admin testing `/vanish` was therefore always an observer exempt from hiding. In addition,
  entity tracking and the tab list were not treated as two separately owned visibility surfaces.
- **Normal claims were rectangle-only:** the player flow had only `pos1`/`pos2`, so it could not create the territory-style
  multi-point outline requested for ordinary claims.
- **The BlockDisplay glass wall floated above the claim and did not cover the full boundary/height:** four stretched slabs
  were anchored to the viewer's Y instead of resolving every boundary column from its own terrain surface.
- **DARK territory mobs could spawn in the air and die from falling:** ambient undead used one
  `getHighestBlockYAt()+1` result without a stable-floor, hazard or full body-clearance contract and without bounded retries.

## Corrected implementation

### Vanish

- `icesmp.moderation.vanish.see` is explicit-only (`PermissionDefault.FALSE`) and is not inherited by OP, the moderation
  bundle or `icesmp.admin.all`.
- Every non-observer viewer gets both `hidePlayer(plugin, subject)` for the in-world entity and
  `unlistPlayer(subject)` for the per-viewer player list.
- Separate ownership ledgers restore only IceSMP-owned `showPlayer`/`listPlayer` pairs when vanish ends or the plugin stops.
- Visibility is reasserted after viewer join, subject toggle, teleport, world change, respawn and a delayed tracking rebuild.
- Damage immunity remains an event capability; no persistent `Player#setInvulnerable` state is written.

### Territory-style normal claims

- Quick square and two-corner rectangle claims remain compatible.
- Ordinary claims now also support `point`, `undo`, `clearpoints`, `points`, `polygon` and a dedicated `polywand` flow.
- The immutable `ClaimShape` is an exact set of claimed X-Z columns and supports concave simple polygons.
- Membership, overlap, column pricing, territory checks, exact WorldGuard row spans, YAML persistence, chunk lookup,
  particle preview and BlockDisplay rendering all consume the same shape.
- Polygon input is bounded by `claims.polygon-max-points` and `claims.area-max-columns`.
- Rasterization uses budgeted row scanlines rather than scanning the full bounding rectangle. Perimeter length, continuous
  area and every produced column are checked before publication; long/thin hostile inputs fail closed.
- Malformed stored polygons are rejected and skipped instead of silently widening to their bounding rectangle.

### Terrain-following BlockDisplay boundary

- Every exact boundary column owns a separate vertical BlockDisplay segment.
- Its base uses `HeightMap.MOTION_BLOCKING_NO_LEAVES` for that X-Z column, not the viewing player's Y coordinate.
- The full configured wall height is rendered around the complete exact perimeter, including concave polygon edges.
- RegionScheduler ownership and per-player preview cleanup remain Folia-safe.

### Stable DARK territory spawning

- DARK ambient undead use the shared `resolveSafeStandingLocation` contract.
- The floor must be solid, occluding, non-gravity, non-liquid and non-hazardous; three body blocks must be passable.
- Candidates must still be inside the exact target territory and pass claim/region rules.
- Each mob receives at most `dark-undead.spawn-attempts-per-mob: 12` distinct attempts.
- No valid location means no spawn. There is no airborne or close fallback.

## Other completed hardening retained in this PR

- bounded world-event/invasion/boss spawn safety and reservations;
- transactional multi-admin config GUI with stale-write rejection;
- deterministic and atomic profession recipe reload, including removal of one exact fishing-rod duplicate;
- Java/YAML item-model validation against the manifest and checked-in resource pack;
- reversible DARK daylight/zombification capability lifecycle.

## Measured audit results

- Config schema scalar entries: **9594**
- GUI-displayed entries: **203**
- Intentionally file/command-only entries: **9391**
- Missing, stale or duplicate GUI entries: **0 / 0 / 0**
- Profession recipes: **437**, after removal of one proven exact duplicate
- Profession recipe key duplicates / semantic duplicates: **0 / 0**
- Used GUI/item models: **269**
- Manifest models / checked-in pack models: **298 / 298**
- Missing manifest / pack mappings: **0 / 0**

## Automated coverage

`RuntimeHardeningRegressionSuite` now covers:

- vanilla rectangle compatibility;
- concave polygon membership and wilderness notches;
- exact polygon overlap and boundary columns;
- self-intersection rejection;
- long/thin oversized input rejection before unbounded raster work;
- bounded scanline and fail-closed persistence source contracts;
- entity plus tab-list vanish ownership and permission defaults;
- terrain-owned BlockDisplay columns;
- stable DARK standing locations and finite retries.

The full repository `check` also includes event-spawn safety, config transaction/coverage, profession recipe audit and all
previously registered regression suites.

## Cleanup and exact-head validation proof

- Corrected implementation cleanup commit: `6e958e8794b2fc422fa0ce24fcd7f2a6681ed604`.
- Cleanup gate: **Corrected runtime final cleanup gate #18 — success**.
- The gate removed the temporary workflow, both Python patch drivers and the encoded polygon payload before validation.
- On that scaffolding-free tree, `./gradlew clean check`, `scripts/validate_gui_icons.py` and
  `scripts/check_consistency.py` all completed successfully.
- Final exact tested head: `bf7d79d119470f4b6ef0b8f25fbb5731c046d970`.
- **IceSMP CI #475 — success:** Java 21 clean build, all `check` regressions, explicit faction suites, full consistency,
  targeted DEV driver and consistency delta passed.
- **Repository Docs Inventory #408 — success:** clean build, consistency, Markdown links, tooling self-tests, inventory,
  generated JSON and blocking policy passed. PR-only snapshot/strict steps were correctly skipped by workflow policy.
- No force push or merge was performed.

## Manual staging checks still required

Automated tests cannot prove the following visual/client/integration behaviour:

1. two real clients verifying vanish in the world, tab list and production scoreboard/nametag plugin stack;
2. BlockDisplay wall alignment and full height on cliffs, steps, caves and uneven terrain;
3. polygon-wand UX and concave claim protection on a running Folia server;
4. DARK undead spawn behaviour across real chunk unload/reload and server restart;
5. WorldGuard integration with production regions and a full resource-pack client join.
