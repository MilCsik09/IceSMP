# WW-04 native server challenge integrity

Internal developer checkpoint on `feature/world-weaver-ww04-pve-faction-integrity`,
PR #158, stacked on WW-03 #157. Thematic parent is
`5b77faecfd152cd05e8492aac7cefd5a3d95b269`. Production enable gates remain closed.

## Canonical route and findings

The existing `ServerChallengeManager` previously accepted a bare objective kind.
Mining/harvest had no source admission, and the kill adapter discarded its original
immutable provenance before counting. Counter increment, expiry, stop and start used
separate atomic/volatile fields, allowing a contribution to cross a window reset.

The manager now owns one `ServerChallengeRun`: native per-run UUID, objective, deadline,
progress and terminal status. Its transition lock serializes source admission and
increment; the manager's lifecycle lock serializes this with start/stop/expiry. Late,
wrong-kind, unavailable-policy and quarantined contributions cannot increment. Counts
stop at the target. A succeeded/expired/stopped instance cannot reopen or credit a
later run. This is the current event manager's state, not a second event framework.

`ServerChallengeListener` passes original kill provenance or owner-captured player and
block/world/location sources. Admission appends the current canonical event UUID to
that bounded context and uses the neutral SERVER_CHALLENGE policy. Full contexts fail
closed instead of dropping provenance to make space. No receipt or Bukkit event is
fabricated.

The reward owner continuation carries UUID and the immutable completed run snapshot.
It resolves the live recipient on its entity scheduler and checks ownership, online
state and shutdown before delivery. It rechecks the original event identity and fresh
recipient location at SERVER_CHALLENGE, ITEM_ACQUISITION, VANILLA_XP and EVENT_REWARD
boundaries. A new event does not replace the completed event's identity. Old boss-bar
callbacks cannot hide an active newer challenge; announcements use the global region
scheduler. No live player is retained in the new continuations.

## Verification

Java 21 compiles the complete main/regression set. All 49 selected suites pass. The new normal `check` dependency
`serverChallengeIntegrityRegressionTest` passes 125 assertions, including all six source
kinds, contributor/event quarantine, source bounds, unbound/broken policy, terminal
states, exact deadline, 30 rounds of concurrent contributions and expiry contention,
and final reward-source/recipient drift. Native adapter source contracts supplement
executable state/policy tests; they do not replace connected-player evidence.

The inventory now records 324 authorities across 55 domains; 51 blockers remain.
The native run is explicitly classified in the existing event.server_challenge domain.
Its provider remains a blocker: typed adapter start needs journal PREPARED before any
sandbox effect, expected-instance stop and observed recovery. No coverage is waived.

## Remaining evidence and scope

Exact new-head CI and connected Paper/Folia player tests remain required. The previous
head's successful native/artifact jobs are recorded in `WW-04-statistics-rewards.md`;
the inherited damaged Trash source PNG gate still blocks the full build.

This change does not introduce a durable challenge reward outbox: the existing event
is ephemeral, and crash/retired-player delivery can lose a scheduled native reward.
Production acceptance must retain that lifecycle limitation or integrate the canonical
item delivery authority when persistent event entitlements are introduced. No replay
or duplicate-payout recovery is claimed here. Item/prototype and native damage
propagation, community/season/raid/bounty final admission and the complete Event
provider are separate open work. No universal zero-leak, WW-04 completion, merge-ready
or production-ready verdict follows from these tests.
