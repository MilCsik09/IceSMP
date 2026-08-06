# PlayerProfile authority matrix

This matrix is versioned together with `scripts/player_profile_authority_allowlist.json` and enforced by CI. `TRANSITION` rows are existing authorities that must disappear in the stacked integration PRs; the final stack permits only PlayerProfile authority, runtime state, derived mirrors, item/entity metadata and explicit shared-aggregate references.

| Current data | Current authority | Final section or aggregate | Runtime mirror | External visibility | Transition |
| --- | --- | --- | --- | --- | --- |
| class/spec, class XP, loadouts, DARK seals, Soulforge progression | PlayerProfile class-spec section | `class-spec` | rebuildable PDC/UI mirror | public/self/admin filtered | complete in root |
| spell grants, selection, favorites, mastery | legacy player PDC/managers | `spellbook` | selected-spell UI mirror | self/admin; selected public by privacy | stacked spellbook scope |
| talent points and purchased talents | legacy player PDC/manager | `talents` | GUI cache only | self/admin | stacked spellbook/talent scope |
| faction membership/history/sinner/player cooldowns | managers/PDC/YAML | `faction` | scoreboard/tag mirror | privacy filtered | stacked faction scope |
| wallets, bank, tax debt, pending refunds | player stores/managers | `economy` | display cache | self/admin | stacked economy scope |
| professions, XP, level, specialization, recipes | PDC/managers | `professions` | HUD/GUI mirror | self/public summary | stacked professions scope |
| quest state, objectives and reward receipts | quest managers/YAML/PDC | `quests` and `operations` | tracker UI | self/admin | stacked quest scope |
| pets/minions and durable companion state | PlayerProfile namespace + runtime manager | `companions` and `class-spec` | live entity map | privacy filtered | root plus lifecycle hardening |
| achievements, bestiary and milestone claims | managers/PDC/YAML | `achievements` | toast/UI cache | privacy filtered | stacked progression scope |
| kills/deaths/events/season counters | stats managers/YAML | `statistics` | scoreboard cache | privacy filtered | stacked statistics scope |
| language/HUD/scoreboard/notification/privacy | config/PDC/managers | `preferences` | online UI state | public visibility flags/self | stacked preferences scope |
| moderation case details | global moderation aggregate | reference/summary in `moderation` | runtime enforcement cache | admin only | reference integration |
| guild/party/market/claim/treasury/raid/season | separate global aggregates | stable reference only | runtime membership cache | composed PlayerView | remain separate |

Run `python3 scripts/check_player_profile_authority.py --root .` to verify the exact code-level inventory.
