# PlayerProfile full authority completion

## Scope

This stacked branch completes the greenfield transition of every IceSMP-owned, restart-durable player domain to the modular PlayerProfile repository.

The following domains must use PlayerProfile sections as their sole durable authority:

- spellbook, spell mastery and favorites;
- talents;
- faction membership, membership history, sinner state and player cooldowns;
- wallet, bank, tax debt and pending refunds;
- professions, XP, specialization and learned recipes;
- quests, objectives and operation receipts;
- durable companion state;
- achievements, bestiary and milestone claims;
- statistics and counters;
- preferences, HUD, scoreboard, notifications and privacy;
- moderation reference and summary state.

Shared guild, party, market, claim, treasury, council, raid, season and audit-log aggregates remain separate. PlayerProfile may store only stable references to those aggregates.

## Forbidden runtime patterns

After completion, no gameplay path may treat any player PDC key, standalone player YAML file or manager-owned durable map as an authority for the domains above. There is no legacy migration, dual read, dual write, fallback or runtime kill switch.

PDC remains permitted only for:

- rebuildable runtime or UI mirrors;
- item metadata and provenance;
- entity metadata and short-lived runtime identity;
- non-authoritative integration hints that can be recreated from PlayerProfile or shared aggregates.

## Required invariants

- Every durable mutation is owner-bound and section-CAS protected.
- Cross-section mutations use the PlayerProfile transaction/WAL protocol.
- Cache state becomes authoritative only after durable commit.
- Join, quit, reconnect and plugin-disable preserve session fencing and bounded drain semantics.
- Decode, owner, revision or persistence failure is fail-closed and preserves evidence.
- Folia entity access remains on the owning entity/region scheduler; persistence I/O remains asynchronous.
- Runtime mirrors are rebuilt from PlayerProfile after join and invalidated on quit/reset.

## Merge-readiness gates

- Java 21 `clean build` and the complete regression suite pass.
- Every migrated domain has targeted persistence, CAS, recovery, reconnect and lifecycle regressions.
- `check_player_profile_authority.py` reports no unknown, stale, invalid or transition authority findings.
- No player-owned durable PDC/YAML/map authority remains outside explicitly approved metadata/mirror categories.
- Repository consistency, Markdown links, tooling self-tests and strict repository/documentation inventory pass with zero blocking or review-required findings.
- The authority matrix marks every player-owned domain complete.

This branch is stacked on PR #78. It must not be merged by the implementation workflow, and force push is forbidden.
