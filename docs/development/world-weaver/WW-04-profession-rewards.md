# WW-04 profession reward admission

Internal developer checkpoint; gameplay admission and physical issuance remain off.
Branch `feature/world-weaver-ww04-pve-faction-integrity`, PR #158, stacked on WW-03
#157. Implementation base `f03f6e232fbd0ca02cb4d4f58ff6c4b31ce132a6`.

## Canonical admission

`PlayerProfileAuthority` and `PlayerProfileService` now expose a conditional reward
mutation using the existing section CAS. Immutable `RewardContext` survives every
bounded stale-revision or stale-generation retry. `PlayerProfileRepository` has an
explicit generation-fenced reward save port; its default refuses unsupported storage.
The production YAML implementation checks policy under the existing per-profile lock,
after both revision checks and before entering the existing WAL. A denied reward
changes no durable bytes, revision, operation receipt, notification or session health.

This is not a new reward authority, receipt store or raw profile editor. Ordinary
conditional mutation and explicit profession SET retain their canonical routes.
The existing class/pet reward save overload delegates to the same guarded implementation.

| Current ingress | Context and final admission |
| --- | --- |
| `ProfessionManager.addXp` / `addXpFor` | Explicit PROFESSION_XP/recipient validation; default captures owner player/world/location; reward-aware profile CAS |
| Block break / harvest | Original block world/position plus player causal sources, detached before IO |
| Fishing | Caught entity/provider lineage plus owner player/world/location; unavailable foreign source refuses |
| Enchanting / furnace extraction | Original source block position plus player provenance |
| Crafting / smithing / brewing | Player/world/location provenance through XP CAS |
| Canonical recipe-book crafting | Provenance captured before crafting and carried through XP and weekly continuation |
| Weekly profession contribution | Original immutable sources, WEEKLY_GOAL/recipient validation and reward-aware canonical contribution CAS |
| Weekly award / pending claim | Only previously admitted durable contribution can earn the existing award; later quarantine does not erase earned credit or pending payout |

Post-commit profession feedback and weekly contribution hops now retain player UUIDs,
resolve live state on that player's scheduler and verify ownership/online state there.
Weekly counters advance only after the canonical contribution succeeds. Missing
profile authority no longer permits memory-only contribution. Week check plus counter
publication shares the existing tick lock, preventing a rollover between those steps.
Expected quarantine refusals produce no storage-failure message or public hidden detail.

## Verification

Java 21 compilation of all main/regression sources passed with three inherited warnings.
All 35 selected PlayerProfile, profession, Weaver reward/propagation/ownership and
Prologue/lifecycle suites passed. The new normal Gradle check dependency
`weaverProfessionRewardRegressionTest` passes 74 assertions against the production
service, weekly store, YAML repository and WAL. It covers source and recipient taint
published while IO is queued, unchanged durable files/revisions/notifications on denial,
stale section/profile generation retry, mismatched recipient/channel, unavailable
policy, no-op state, restart-safe earned weekly payout and idempotent pending claim.

Fault injection on both sides of manifest publication follows canonical repository
semantics: pre-manifest interruption rolls back; post-manifest acknowledgement loss
retains the committed XP. Restart never replays the original reward. A subsequent
quarantined reward refuses in either recovered state. These are real file-backed
tests, not proof of connected-player Paper/Folia activity.

Four architecture checks, authored PvE audit, coverage and consistency (0 FAIL/WARN)
pass. Profile authority guard/self-test: 676 findings; zero unknown, stale, invalid or
transition findings. Eight affected authorities were reviewed and rehashed. Inventory
remains 323 authorities / 55 domains / 51 blockers / 1235 main Java files.

## Remaining gates

Fresh exact-head CI is required. Native connected-player profession activities,
logout/cross-region source capture and populated weekly rollover still require evidence.
Canonical item input/output lineage, prototype restrictions, recipe learning,
masterwork/Bestiary rewards, seasonal event multiplier provenance and rare gathering
are separate open integrations; player-only craft context does not prove those surfaces.
The weekly manager's shared event/goal developer surface also remains a later provider
integration. No all-profession, zero-leak, full-domain, phase-complete or production
verdict follows from this checkpoint. No provider coverage blocker has been waived.
