# WW-04 statistics admission and quest settlement

Internal developer checkpoint on `feature/world-weaver-ww04-pve-faction-integrity`,
PR #158, stacked on WW-03 #157. Thematic parent:
`23abf8680e912f9616b14f683294ba29595a8c8a`. Gameplay enable gates remain closed.

## Native authority and fixed findings

Achievement conditions consume native player counters, so denying an immediate loot
payout alone does not prevent later progression from tainted statistics. The canonical
`PlayerProfileStatisticsStore` now carries channel/recipient-checked immutable context
through generation-fenced reward CAS for all six counter kinds, species/total/first-kill
recording, and leaderboard snapshots. Compatibility calls retain recipient admission.
No rejected candidate changes its counters, first-kill timestamp, snapshot or revision.

StatsCombatListener retains the original death/kill provenance through profile IO.
The raid-stat callback resolves the killer UUID on owner and uses the victim's captured
causal sources. AbilityCatalystListener captures activity sources before native cast
execution, then passes that same immutable context to statistics and quest progress.
A spell that changes location cannot erase its original spatial source from those
consumers. Leaderboard callbacks retain UUID rather than Player. Expected quarantine
refusal emits no severe storage diagnostic. Existing first-kill timestamps remain
write-once, and total/species changes continue to share one section commit.

The old QuestManager callback incremented quest statistics after reward settlement.
A crash/logout could lose that increment; retrying an unacknowledged callback could also
make its count diverge. `PlayerProfileQuestStore.settleReward` now updates QUESTS and
STATISTICS through the existing cross-section profile transaction/WAL. It validates
the real claimable receipt and DELIVERED physical components, settles the receipt and
increments the counter in one generation. Its deterministic native operation ID is
namespaced from the existing receipt hash; the original quest receipt is retained in
operation metadata. No second history or payout identity is introduced.

Later quarantine does not revoke an already earned claimable quest entitlement. Its
settlement/counter transaction therefore uses that validated native entitlement;
ordinary arbitrary metric increments still pass reward admission. Concurrent settlement
re-assesses profile-generation conflicts with at most four attempts. Already settled
receipt replay performs no write. A contradictory pending receipt whose settlement
operation is already recorded fails closed for reconciliation instead of replaying a
counter change. The UI no longer increments this statistic separately. Old completed
history is not guessed, reset or retroactively rewritten.

## Verification

Full Java 21 main/regression compilation and all 48 selected profile, quest, integrity,
queue, profession, knowledge, native PvE, spell/Evoker, bootstrap/onboarding and lifecycle
suites pass. New normal Gradle check dependency `weaverStatisticsRewardRegressionTest`
passes 180 assertions with the real profile YAML and transaction manager:

- six immutable source kinds across counters, species and leaderboard writes;
- queued source/recipient/unbound refusal leaves identical disk bytes and revisions;
- valid recipient/channel and first-kill timestamp constraints;
- clean total/species counter and immutable first-kill semantics;
- physical PREPARED state blocks receipt and statistic settlement together;
- two concurrent settlement requests credit exactly once in one profile generation;
- accepted settlement remains valid under later quarantine, arbitrary increment does not;
- both manifest crash boundaries recover receipt/counter atomically;
- reload/replay never increments twice or consumes an unknown receipt;
- externally contradictory pending/committed evidence refuses without a new write.

The existing statistics suite has 19 assertions, quest store 424, quest admission 169,
queue 1083 and Bestiary 49; all pass. Source contracts supplement these executable
storage tests and retain the original pre-cast and kill context paths.

Four architecture checks, authored PvE audit, coverage, repository consistency
(0 FAIL/WARN) and profile authority guard/self-test pass. The guard reports 681 findings
with no unknown, stale, invalid or transition findings. Changed authority hashes were
re-audited. Main source count remains 1236; coverage remains 323 authorities, 55 domains,
51 implementation blockers. No blocker was waived.

## Native evidence gate

The existing matrix Paper/Folia probe now also exercises the actual quest store's
acceptance/completion/atomic settlement for its isolated offline profile fixture. It
checks the real pending receipt, exactly one statistic, empty pending set and no-op
replay before clean shutdown. CI requires
`ICESMP_QUEST_STATISTICS_RUNTIME_PROBE_PASS scope=offline_profile_atomic_settlement`.
No Player, developer token or Bukkit gameplay event is fabricated. This checks native
profile transaction wiring, not a connected player's quest manager/item behavior.
Fresh exact-head CI is required for this extension. Exact preceding queue head results
are recorded in `WW-04-quest-queue.md`; its native/artifact jobs passed and only inherited
Trash source PNG validation failed the full verification job.

## Remaining scope

This is statistics ingress and accepted quest-settlement closure. Full native damage
propagation, item/prototype lineage, canonical raid/season/community/bounty reward
boundaries and other metric/reward consumers remain active implementation work. The
raid-stat gate here does not establish integrity of RaidManager scoring or bounty
settlement. NPC item hand-in and immutable pending item reward payloads still need their
canonical transaction integration. Connected-player spatial/teleport/cast, population,
logout/disable and client behavior remain explicit evidence gates. The queue/native
clean fixtures do not close those requirements. No zero-leak, full Knowledge provider,
WW-04 complete, merge-ready or production-ready verdict is asserted.
