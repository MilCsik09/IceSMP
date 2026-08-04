# IceSMP PlayerProfile platform

<!-- icesmp-doc-id: feature.platform.player_profile -->

## Canonical model

`PlayerProfileSnapshot` is the single logical aggregate for IceSMP-owned, restart-durable player state. It is immutable, Bukkit/YAML/SQL/HTTP independent and owner-bound by UUID. The root contains identity, lifecycle, onboarding, faction, economy, class-spec, professions, spellbook, talents, quests, companions, relics, achievements, statistics, preferences, social links, moderation and operations sections. Shared guild, party, market, claim, treasury, council, raid, season and audit-log aggregates remain separate and are referenced only by stable IDs.

## Storage boundary

Gameplay, commands, GUIs and APIs depend on `PlayerProfileRepository` and `PlayerProfileTransactionManager`, not YAML. `YamlPlayerProfileRepository` is the current adapter; a future SQL adapter must implement the same contracts. The class/spec model is `ClassSpecSection`; no independent ClassProfile aggregate or opaque ICS2 profile blob exists.

## Revisions and snapshots

Each section has schema and revision; the manifest carries global generation and the committed section revision map. Missing sections initialize with `-1 -> 0`; normal saves require `n -> n+1`. Full reads validate manifest generation before and after section loading, so snapshots are never an arbitrary time mixture.

## Transactions and recovery

Cross-section operations persist an owner-bound WAL and operation fingerprint, write prepared section files, atomically commit the manifest generation, close the operation receipt, apply runtime effects and clean temporary state. Restart before manifest commit rolls back; restart after commit finalizes. Duplicate operation IDs with a different fingerprint fail closed.

## Lifecycle and Folia

Join creates a session generation, recovers WALs, loads/initializes sections asynchronously, validates health, rebuilds derived mirrors, reconciles DARK/spells/companions, then marks the session ready. Quit fences mutations, drains transactions, flushes sections, cleans runtime and invalidates cache. Disable stops HTTP/admission, drains, flushes, cleans runtime and shuts executors down with a bounded timeout. Bukkit entity access remains in owner/region-thread adapters; YAML I/O is asynchronous.
