## Parent / parallel topology

- Base: `feature/quest-item-content-integrity-hardening` / PR #144
- Exact parent: `a256a52264ac0d58427c9db33d80f66af1ce73d3`
- Parallel sibling: PR #146 `feature/trash-anomaly-archaeology`, also based on #144. This PR does not depend on or modify #146.
- Keep OPEN, DRAFT and unmerged.

## Review authority

`IceSMP_Full_Player_Facing_Messaging_Review` — exhaustive 4904/4904 surface audit.

Original findings:
- M0: 2
- M1: 11
- M2: 8
- M3: 1

## Blocking closure

- `MSG-M0-001`: crate success receipt placeholder parity repaired.
- `MSG-M0-002`: market GUI no longer claims durable finality that the manager outcome cannot prove.
- `MSG-M1-001`: crate no-key guidance renders the correct required count / price / currency / crate id contract.
- `MSG-M1-002`: pending physical quest reward tells the player why it is pending and what to do.
- `MSG-M1-003`: profession-gated Board quests remain discoverable as locked guidance with profession/level and `/profile` route.
- `MSG-M1-004`: world-boss broadcast describes meaningful contribution and personal reward semantics.
- `MSG-M1-005`: committed personal world-boss reward gets an item/amount receipt.
- `MSG-M1-006`: `/spec info` is player-readable; raw profile diagnostics are staff-only.
- `MSG-M1-007`: spec GUI uses exact-arity dedicated success keys.
- `MSG-M1-008`: market item-name substitution escapes MiniMessage tags.
- `MSG-M1-009`: reviewed profession/item surfaces use player language instead of raw template/enum/persistence vocabulary.
- `MSG-M1-010`: crate race path no longer deterministically hits String.format fallback.
- `MSG-M1-011`: spell mastery insufficient-funds GUI includes the actual upgrade cost.

M0 `2/2 CLOSED`; M1 `11/11 CLOSED`.

## Bounded M2/M3

Closed:
- authored daily copy no longer leaks `kanonikus`;
- `territory.yml` is the sole bundled owner of `territory-*` message keys;
- party terminology normalized to `csapat` while retaining `/party` command literals;
- crate browser and Bestiary no longer expose raw enum/domain identifiers on the reviewed paths;
- `/quest choose` ordinary help explains that clickable dialogue handles the opaque token path automatically;
- reviewed normal-player spec/spell/profession wording normalized;
- reviewed Job GUI orthography repaired.

Intentionally deferred non-blocking:
- `MSG-M2-006`: no bulk migration of all 698 fallback-only keys; repeated/core paths implicated by blockers were aligned without building a localization framework.
- `MSG-M2-008`: no blind deletion of all unused compatibility keys; retired daily vocabulary and divergent territory ownership were cleaned while per-key compatibility decisions remain bounded follow-up.

## Preserved gates

- Money Pouch unopened amount/currency/range leak: 0.
- Menedék contradiction: 0.
- second live daily authority: 0.
- literal placeholder leak: 0 on reviewed blocking paths.
- raw format-token leak: 0 on reviewed blocking paths.
- raw Bukkit scheduler use introduced: 0.

## Verification

- dedicated `scripts/audit_player_facing_messaging_hardening.py` finding-driven gate;
- cumulative quest/item, bootstrap, config/content, profession, progression and resource-pack audits;
- Java 21 clean build + full regressions;
- Paper 1.21.11 cumulative runtime;
- Folia 1.21.11 cumulative runtime;
- exact parent / merge-base and `git diff --check`.

Machine-readable evidence: `docs/development/player-facing-messaging-integrity-hardening.json`.

Human staging still required for actionbar/title/HUD readability, onboarding density, boss reward satisfaction, daily feel and staff copy comprehension.
