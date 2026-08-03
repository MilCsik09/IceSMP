# Runtime hardening audit — 2026-08-03

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
- **Recipes/config GUI/icons:** see `PROFESSION_RECIPE_ITEM_AUDIT.md`, `CONFIG_GUI_COVERAGE.md` and `RESOURCE_PACK_CMD.md`.

## Manual runtime checks still required

Paper/Folia integration tests cover pure geometry/policy and source contracts. A staging server should additionally verify packet-
level tablist/nametag behaviour with the production scoreboard plugin set, particle appearance over uneven terrain, WorldGuard
region integration, and a full resource-pack client join. These checks are visual/integration-specific and are not claimed as
fully automated.
