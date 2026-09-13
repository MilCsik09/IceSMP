# WorldWeaver 1.0 — practical scope

Product authority: the 2026-09-10 “Practical Full Completion + Scope Pruning” request.
This document supersedes universal completeness requirements in design-v2, WW-00
coverage, phase evidence and older resume sections. Historical evidence remains historical.
Source inventory is not runtime proof; optional domains never gate activation.

## KEEP

- Primary-developer artifact ownership, lifecycle and generic session GUI.
- Typed subjects/values/catalogs and provider registry validation; Folia owner routing.
- SANDBOX/LIVE_GM, single-use arming, bounded execution, durable journal, audit,
  observed recovery and conditional Undo where an actual native inverse exists.
- Temporary projections, Thread, PvE combat Imprint, native bounded PvE Fork,
  bounded AREA inspection and AREA-enter VFX Binding.
- Practical Minecraft, PvE, Faction, Territory, Event, Itemization, Trash and
  developer/session surfaces. A provider need not implement every capability.
- Actual native reward denial and causal propagation reached by sandbox combat,
  items, fields, events and projections.

### Source classification

| Category | Source groups and concrete decision |
| --- | --- |
| KEEP_CORE | `dev/artifact`, `DevItemManager`, `HiddenDevAuthority`; `dev/weaver` kernel/session/arming; `api`, `gui`, `subject`, `area`, `execution`, `persistence`, `projection`, `integrity`. Each has executable artifact/provider consumers. |
| KEEP_VERTICAL_SLICE | Registered Minecraft, PvE, Faction, Territory, Itemization, Trash adapters; added Developer Binding and limited Event adapter. Native actions retain their domain authority. |
| KEEP_SHARED_HARDENING | Profile CAS/WAL, native item mutation journal, faction membership, quest reward/statistics settlement, Trash history/activation/field services, authored creature lifecycle and shutdown/owner fixes. |
| REMOVE_SCOPE_OVERENGINEERING | Runtime completeness registry, its dedicated regression/build task, global deferred-domain release rejection. |
| REMOVE_DEAD | Minecraft material catalog/type and location Thread export without a consumer. Catalog entries without a compatible importer no longer create unusable Threads. |
| REVIEW → KEEP | Compensation interfaces, effect deltas and journal recovery schemas: real current Itemization/Trash/Faction/Territory recovery consumers. No canonical player-data migrations are removed. |

## REMOVED

- `WorldWeaverCoverageRegistry.java`: zero production references; existed only to
  require exact domain coverage and reject deferred domains.
- `WorldWeaverCoverageRegressionSuite.java` and its Gradle task: tested that obsolete
  product rule. Type, manifest, authority, ownership and persistence safety suites stay.
- `DEFERRED_BLOCKER` runtime status: replaced by nonblocking `OPTIONAL_FUTURE`.
  `audit_world_weaver_coverage.py --release` remains a compatibility invocation of
  the source inventory audit, with no universal feature-completeness rejection.
- `MinecraftWeaverProvider` material enumeration and orphan location export.
  Game-mode catalog remains because a real guarded native action consumes it.
- Session projection recipes without Undo are allowed only with explicit expiry of at most 120 seconds; persistent projections still require a conditional inverse.
- Universal assumptions that all actions need Undo/AREA/Binding and all providers
  need Thread/Imprint/Fork. No additional provider is registered solely for coverage.

## RETAINED SHARED HARDENING

PlayerProfile conditional transactions/WAL; native item identity and exact inventory
mutation/Undo; faction role-cleanup ownership; native Trash history and rule-field
lifecycle; earned quest/Knowledge/statistics atomic settlement; actual source-context
capture; native authored-creature spawn ownership; shutdown ordering. These protect
normal IceSMP gameplay independently of WW.

The Event slice uses `GatheringBuffManager`'s native immutable window identity and
atomic reward policy. It does not introduce a second event-state store. Sandbox
windows suppress bonus drops and XP multipliers in the native calculation methods.

## OPTIONAL FUTURE

Additional event families, Class/spec, Profession, Economy, Pet, achievements,
community/challenge, market and other subsystems can gain a limited surface only
when a concrete developer workflow justifies it. Their existing gameplay and safety
code remains. Their inventory entries do not promise WW functionality.

## NO LONGER A RELEASE BLOCKER

55/55 full providers; zero deferred/optional domains; universal import/export,
Imprint, Fork, Binding, AREA or Undo; speculative transitive provenance for future
producers. Reachable sandbox exploit prevention remains a release requirement.

## Practical composition contracts

- Thread: a mob's current ability catalog → typed Thread → compatible target's
  ability projection. Catalog selection is re-resolved against a fresh target snapshot.
- Imprint: rank, archetype, template reference and at most 32 ability IDs. Reject
  unknown fields, identity, owner, history, receipts, profile revisions and economy.
  Apply is SANDBOX SESSION only and uses the existing combat projection consumer.
- Fork: native authored nonpersistent mob, 120-second lifespan, 16 admissions per
  session. The original is read-only. Spawn begins inert/invulnerable; combat starts
  only after durable event/entity quarantine. Native cleanup runs on session end,
  explicit removal and expiry. No natural reward identity is copied.
- Binding: AREA enter → recipient VFX, SANDBOX SESSION only, at most 16 executions,
  2-second cooldown, 120-second expiry, no recursion or arbitrary callable actions.
- Projection: canonical state remains authoritative; clear removes effective override
  while the required sandbox reward quarantine tail remains.

## Validation and activation

Completion requires the requested Java 21 build/check, relevant regression suites,
Paper/Folia probes and repository/resource-pack/authority gates on the final commit.
Connected-player interaction staging is reported separately. This document defines
scope; it does not by itself assert those checks passed or authorize a merge/release.
