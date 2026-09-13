# Player-Facing Messaging Integrity Hardening

Parent authority: PR #144 exact head `a256a52264ac0d58427c9db33d80f66af1ce73d3`.

This is a bounded sibling hardening layer to the parallel Trash / Anomaly work. It closes the verified player-facing messaging review blockers without introducing a localization framework or changing staging/master.

## Mandatory closure

- M0: 2/2 closed.
- M1: 11/11 closed.
- M2: bounded player-facing terminology, duplicate authority and raw-enum issues closed; broad fallback-only and unused-key inventories are explicitly retained for per-key compatibility decisions rather than bulk migration/deletion.
- M3: reviewed orthography defect closed.

## Runtime-truth highlights

- Crate receipts now render crate, batch count and actual reward in the intended placeholder order; key-purchase recovery guidance targets the real crate id.
- Market GUI receipts no longer assert durable finality that the current manager outcome does not represent; player-controlled item names are MiniMessage-escaped before template parsing.
- Pending physical quest rewards tell the player that inventory space is required and that the durable reward remains pending.
- Profession-gated Board quests remain visible as locked guidance with the required profession/level and `/profile` route.
- World-boss broadcasts describe contribution-gated settlement, and a committed personal boss-specific reward receives an item/amount receipt.
- `/spec info` has a normal-player projection; raw profile diagnostics are restricted to staff-capable viewers.
- Profession, crate browser and Bestiary surfaces use player-facing labels instead of raw template/enum/UUID vocabulary.

## Verification boundary

The dedicated exact-head audit parses the root `messages.yml` and every bundled `messages/*.yml` file before evaluating finding-specific semantic gates. It then proves the crate format contracts, world-boss contribution/reward copy, single territory message ownership, daily and party terminology, player/staff spec projection, market non-final receipts and MiniMessage escaping, pending quest reward guidance, profession-gate discoverability, committed personal boss receipts, profession/item internal-vocabulary cleanup, and raw-enum GUI removal. The workflow additionally reruns the cumulative #144 quest/item, bootstrap, config/content, profession, progression, resource-pack, Java 21, Paper and Folia gates.

## Preserved positive gates

- Money Pouch unopened amount/currency remains hidden.
- Menedék guest semantics remain truthful.
- Authored daily remains the sole live daily content authority.
- No raw Bukkit scheduler path was introduced.
- No quest/item/content authority was redesigned.

Human staging remains required for burst/actionbar readability, onboarding density, HUD scale, boss-reward satisfaction and staff copy comprehension.
