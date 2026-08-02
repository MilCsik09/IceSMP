# Class/spec rework — Profile v2 greenfield architecture

## Status and scope

Profile v2 is the only authoritative class/spec domain model, persistence format and runtime admission source.
The server has no production player data that must be imported, therefore this implementation deliberately has:

- no legacy class/spec migration;
- no dual read or dual write;
- no legacy PDC fallback;
- no runtime kill switch;
- no supported downgrade to the previous class/spec runtime.

The compatibility base PR still owns Paper/Folia 1.21.11 integration boundaries. This PR does not implement
mechanics-core, doctrine runtime, mastery contribution, second-spec channeling or the 35 specialization kits.

## Aggregate

`ClassProfile` is immutable and requires an owner UUID. It contains:

- schema version and monotonic revision;
- `READY`, `REVIEW` or `QUARANTINED` profile status;
- canonical primary class, class XP and calculated class level;
- exactly two slot-addressed `ClassLoadout` values;
- the single active slot and second-slot unlock state;
- mastery, doctrine, Soulbond, capstone, spell selection and favorites;
- slot-bound companion rosters and mechanic state;
- bounded, restart-durable operation receipts;
- quarantine, recovery and session-block diagnostics.

Construction, decoding and every copy/build path execute the same domain validation. Collections are defensively
copied and deterministically ordered for persistence.

Important invariants include:

1. a classless profile has zero XP/level and no class/spec/loadout state;
2. only one slot may be `ACTIVE`, and `activeSlot` must identify it;
3. `EMPTY`, `SEALED`, review and quarantine state cannot activate gameplay;
4. the same specialization cannot occupy both slots;
5. a specialization must belong to the profile's primary class;
6. a locked second slot is empty;
7. Soulforge state is valid only for the Necromancer loadout;
8. companion namespaces belong to their owning specialization and slot;
9. live Bukkit entity UUIDs are never durable profile state;
10. at most one recovery-requiring durable operation may be pending;
11. revisions and all counters are non-negative and use checked arithmetic.

## ICS2 codec and envelope

`ClassProfileCodec` emits deterministic ICS2 codec version 2 bytes with explicit UTF-8, bounded strings and
collections, duplicate/collision detection, exact enum decoding, truncation and trailing-byte rejection.
The owner UUID is encoded inside the payload.

The codec appends an unkeyed SHA-256 digest. Its documented purpose is accidental corruption detection only.
It is **not authentication** and does not claim to prevent an offline operator who can rewrite the file from
recomputing the digest. Filesystem permissions and deployment access controls are the trust boundary.

The YAML envelope has an exact, type-checked field set:

- `format`;
- `owner`;
- `schema`;
- `revision`;
- `digest`;
- `payload`.

The repository verifies that the repository key/file name UUID, envelope owner and decoded payload owner all
match. Floating revisions, booleans in string fields, scientific notation, unknown keys, invalid UUIDs, invalid
digests and oversized payloads fail closed.

## Persistence and CAS

The repository contract is:

- absent profile: expected revision `-1`;
- first durable profile: revision `0`;
- mutation: exactly `n -> n+1`.

Load, expected-revision validation, encode and atomic replacement execute in one per-player critical section.
Locks are keyed by canonical repository directory and player UUID, so separate repository instances in the same
JVM serialize the same player but do not globally serialize unrelated players. A per-profile filesystem lock also
protects the CAS section when multiple processes touch the same directory.

The supported deployment model remains one authoritative IceSMP process per profile directory. The file lock
prevents a second cooperating process from bypassing CAS; shared-network-filesystem lock semantics are not
promised beyond the filesystem provider's guarantees.

Writes use a temporary file, flush/sync and atomic replacement where supported. Cache authority changes only
after the durable replacement succeeds.

## Greenfield initialization

A missing profile becomes `ClassProfile.empty(owner, 0)`. Parallel first-login initialization is resolved by CAS:
exactly one revision-0 profile wins, and the loser reloads that winner instead of blocking the session or falling
back to legacy data.

Existing class/spec PDC values are not read, imported or used to select class, specialization, spell, companion or
Soulforge state.

## Session-generation fencing

`ProfileSessionRegistry` creates a fresh UUID token for every activation attempt. The token is carried through:

- lifecycle load and initialization;
- gateway mutation admission;
- durable completion callbacks;
- runtime reconciliation;
- pet and companion callbacks;
- logout, reconnect and disable invalidation.

Every scheduler hop rechecks the token. A completion from a retired session can neither mutate the new cached
profile nor grant spells, spawn/claim companions, rebuild runtime state or clear the new session's fail-closed
status.

## DARK seal lifecycle

All five DARK specializations use the same `GateSnapshot`/`SealReason` model. `SealReason` stores the complete,
deterministically ordered missing-gate map, not only the first failure.

A seal commit:

1. persists the complete reason set;
2. clears the active slot without activating another slot;
3. revokes specialization provenance;
4. removes pet/minion/form/transient runtime state;
5. preserves mastery, doctrine, Soulbond, capstone and durable roster.

Gate reconciliation is idempotent. Recovering one gate does not unseal while another remains missing. Automatic
unseal happens once only after all restorable gates pass; admin, persistence and quarantine seals are never
removed by faction or quest events.

## Spell provenance

`SpellGrantLedger` records explicit `BASE:<class>`, `SPEC:<spec>`, `TALENT:<id>`, `QUEST:<id>` or `ADMIN`
provenance. Source-less and `LEGACY` grants are rejected.

Spec reset/seal revokes `SPEC:*`. Admin class reset revokes `BASE:*` and `SPEC:*`. Talent, quest and admin grants
survive unless their own authority explicitly revokes them. No login-time provenance inference depends on a
partially activated session.

## Pet, minion and Soulforge state

Durable companion mutation (`ADD`, `REMOVE`, `RENAME`, `STANCE`, `PROGRESS`, `EQUIPMENT`, `STATE`,
`RESPAWN_AT`, `SET_ACTIVE`, `DISMISS`) passes through the Profile v2 gateway and CAS. Rosters are owner-, slot-
and namespace-bound. Entity UUIDs remain only in token-fenced runtime registries and cleanup is idempotent.

Soulforge shard debit and rank update are one Profile v2 mutation. Operation IDs and receipts survive restart;
a repeated committed ID is a no-op. A durable commit followed by runtime failure is reported as reconciliation
failure and is not refunded or applied twice.

## Respec transaction and recovery

Respec spans the currency wallet and Profile v2 repository. `RespecTransactionJournal` persists an owner-bound
operation intent before debit. The wallet stores an exact operation witness. Recovery examines the journal,
wallet witness and Profile v2 receipt to deterministically commit or roll back each crash point.

The protocol is idempotent across duplicate callbacks and restart. A player cannot lose currency for an
uncommitted respec or receive a free second respec through repeated recovery.

## Quarantine and admin recovery

Decode/envelope/owner failures preserve evidence and create a quarantine marker. They never overwrite the
original with an empty profile and never activate legacy runtime.

Recovery is explicit:

```text
/spec recover <player|uuid> confirm
```

Permission: `icesmp.admin.spec.recover`.

Recovery validates target owner, marker and evidence ID, preserves the evidence, records an audit ID and writes a
clean, inactive Profile v2 revision 0. The player must reconnect; recovery does not auto-activate a loadout.
General reset cannot bypass quarantine.

## Shutdown and Folia boundary

New mutations are rejected after shutdown admission closes. Existing per-player mutation tails, repository I/O,
respec recovery and runtime callbacks drain with bounded timeouts. Timeout or rejected scheduler callbacks are
logged and leave a fail-closed/recovery-required state; disable never waits indefinitely.

Bukkit entity, inventory, PDC and message operations run on the owning player/entity scheduler. Persistence and
codec work run off region threads. No durable object retains a live `Player` or `Entity` reference.
