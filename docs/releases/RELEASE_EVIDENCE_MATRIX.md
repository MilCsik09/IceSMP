# IceSMP release-bizonyítékmátrix

<!-- icesmp-doc-id: release.evidence-matrix -->

## Cél és bizonyítási szintek

Ez a technikai mátrix összeköti a deployed JAR-t, a dokumentált release
forrását, a config/resource bizonyítékot, a tényleges elérési útvonalat,
az automatizált tesztet és a továbbítható dokumentációt.

| Jelölés | Jelentés |
|---|---|
| JAR | bytecode-, descriptor- vagy bundled resource-bizonyíték |
| SRC | bootstrap, manager/service, listener, command vagy GUI forrásbizonyíték |
| CFG | bundled YAML/JSON/datapack bizonyíték |
| TEST | automatizált regressziós/consistency bizonyíték |
| RUNTIME | valódi szerveres átvételi bizonyíték szükséges |
| LIVE UNKNOWN | az élő külső config vagy persistent adat nem állt rendelkezésre |

Dokumentált release: `master@4643ab53586f0c1ee7352df16dcd477013e6fad4`.
Deployed baseline: `IceSMP-1.0-TESTING.jar`,
SHA-256 `da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05`,
forrásmapping `BINARY_ONLY`.

## Fontos funkciók bizonyítéka

| Funkció | Deployed JAR | Release forrás | Config/resource | Elérési bizonyíték | Regresszió | Dokumentáció | Deployed állapot | Release állapot |
|---|---|---|---|---|---|---|---|---|
| Kaszt és szint | class/managerek, `/class` | bootstrap + class manager/listenerek | `classes.yml` | `/class`, profil/kaszt GUI, automatikus XP | build + consistency | [katalógus](../reference/FEATURE_CATALOGUE.md) | Már elérhető | Aktív |
| Specializáció | spec manager/GUI | 35 enum ID, routing és unlock | `classes.yml` | `/spec`, profil/spec GUI | build + config drift | [adatkatalógus](../reference/DATA_CONTENT_CATALOGUE.md) | 31 bizonyított baseline ID | Aktív, 35 ID |
| Spell/spellbook | spell komponensek | spell registry, cast listenerek | `spells.yml`, `spells-balance.yml` | `/spell`, `/spellbook`, Lélekkapocs | build + consistency | [funkciók](../reference/FEATURE_CATALOGUE.md) | Már elérhető | Aktív; runtime balance kell |
| Talent | manager/GUI | manager, GUI és listener | `classes.yml` | `/talent`, talent GUI | build | [GUI](../reference/GUI_REFERENCE.md) | Már elérhető | Aktív |
| Frakció | faction manager/parancs | négy frakció, manager/listenerek | `factions.yml` | `/faction`, GUI, automatikus passzívák | build + consistency | [funkciók](../reference/FEATURE_CATALOGUE.md) | Már elérhető | Aktív |
| Politika/király/tanács | részben jelen | king/council/war/raid rendszerek | `factions.yml`, `world.yml` | `/faction`, `/tanacs`, `/parbaj` | build | [fő guide](ICESMP_RELEASE_AND_TEAM_GUIDE.md) | Részben | Aktív; rollout teszt |
| Bűn/bounty/Suttogók | sinner/bounty útvonalak | managerek, listenerek, parancsok | faction/economy/world config | `/sinner`, `/bounty`, `/suttogas` | build | [parancsok](../reference/COMMAND_REFERENCE.md) | Már elérhető/részben | Aktív |
| Fizikai valuta | currency manager/parancs | token factory, ledger/store | `economy.yml` | `/currency`, drop/reward | build + consistency | [config](../reference/CONFIGURATION_REFERENCE.md) | Már elérhető | Aktív |
| Bank/váltás | bank manager/parancs | manager, GUI/NPC routing | `economy.yml` | `/bank`, `/exchangeboard`, NPC | build | [parancsok](../reference/COMMAND_REFERENCE.md) | Már elérhető | Aktív; tranzakcióteszt |
| Market/aukció | market manager/GUI | manager/store/GUI/listener | `economy.yml` | `/market`, GUI | build | [GUI](../reference/GUI_REFERENCE.md) | Már elérhető | Aktív |
| Territory | manager/parancs/listener | zónatípusok, index, policy | `world.yml`, `factions.yml` | `/territory`, automatikus protection | build + consistency | [builder guide](../guides/BUILDER_AND_WORLD_DESIGNER_GUIDE.md) | Már elérhető | Aktív, builderigényes |
| Claim | manager/parancs/listener | claim store és protection | world/economy config | `/claim`, trust GUI | build | [builder guide](../guides/BUILDER_AND_WORLD_DESIGNER_GUIDE.md) | Már elérhető | Aktív |
| World regen | regen manager/store/listenerek | snapshot/WAL/lifecycle | `world.yml` | robbanás és world event automatikus | build + consistency | [funkciók](../reference/FEATURE_CATALOGUE.md) | Már elérhető | Aktív; fault teszt |
| Quest | 45 baseline quest | 160 quest, manager/listenerek | `quests.yml` | `/quest`, GUI, NPC és események | build + consistency | [adatkatalógus](../reference/DATA_CONTENT_CATALOGUE.md) | Már elérhető | Jelentősen bővült |
| Achievement/advancement | achievement rendszer | 21 achievement + datapack registrar | `quests.yml`, datapack JSON | `/achievements`, vanilla advancement | build + JSON check | [adatkatalógus](../reference/DATA_CONTENT_CATALOGUE.md) | Már elérhető | Bővült |
| Profession | 8 profession | manager/listenerek | `professions.yml` | `/profession`, profil GUI | build | [adatkatalógus](../reference/DATA_CONTENT_CATALOGUE.md) | Már elérhető | Aktív |
| Recept/blueprint | 124 recept, 29 blueprint | 438 recept, 54 blueprint | `profession-recipes.yml` | recept GUI, craft listener | build + consistency | [adatkatalógus](../reference/DATA_CONTENT_CATALOGUE.md) | Már elérhető | Jelentősen bővült |
| Unique item/relikvia | unique factory, 5 relikvia | factoryk, 6 relikvia, 21 rituálé | crafting/item/relic config | craft, loot, `/relic`, rituálé | build + consistency | [adatkatalógus](../reference/DATA_CONTENT_CATALOGUE.md) | Már elérhető | Bővült |
| Pet/party/céh | managerek/parancsok | manager/store/GUI/listenerek | pets/faction config | `/pet`, `/party`, `/ceh` | build | [funkciók](../reference/FEATURE_CATALOGUE.md) | Részben/már elérhető | Aktív |
| Parkour/dungeon | manager/parancs | store, zóna- és lootútvonalak | world/quest config | `/parkour`, `/territory` | build | [builder guide](../guides/BUILDER_AND_WORLD_DESIGNER_GUIDE.md) | Már elérhető | Aktív, builderigényes |
| Event/boss | event/boss managerek | major gate, spawn guard, reward | `world.yml`, `loot.yml` | `/events`, automatikus scheduler | build | [acceptance](RELEASE_ACCEPTANCE_CHECKLIST.md) | Már elérhető | Bővült; runtime kell |
| NPC binding | parancs/listener | persistent binding + bridge | quest/shop hivatkozások | `/npcbind`, NPC-interakció | build | [builder guide](../guides/BUILDER_AND_WORLD_DESIGNER_GUIDE.md) | Már elérhető | Aktív; élő binding ismeretlen |
| HUD | HUD manager | manager/listenerek | `general.yml` | `/hud`, automatikus render | build | [funkciók](../reference/FEATURE_CATALOGUE.md) | Már elérhető | Bővült |
| Tablista | részleges HUD-tab | külön tablistaréteg és AFK ordering | `tablist.yml` | automatikus player update | AFK/tab regresszió | [fő guide](ICESMP_RELEASE_AND_TEAM_GUIDE.md) | Részben | Aktív; runtime kell |
| MOTD | nincs | ping listener + immutable snapshot | `motd.yml`, ikonok | server-list ping | MOTD regresszió | [acceptance](RELEASE_ACCEPTANCE_CHECKLIST.md) | Nincs | Új, rollout-kapu |
| Moderáció/punishment | csak régi report/mute rész | punishment store/service/commands | `moderation.yml`, messages | warning/kick/mute/ban/history/GUI | moderation regresszió | [admin guide](../guides/ADMIN_AND_MODERATOR_GUIDE.md) | Natív suite nincs | Új, rollout-kapu |
| PM/SocialSpy | nincs | message service/commands/listener | moderation/messages | `/msg`, aliasok, `/reply`, `/socialspy` | moderation regresszió | [admin guide](../guides/ADMIN_AND_MODERATOR_GUIDE.md) | Nincs | Új, rollout-kapu |
| Vanish | nincs | service/command/listenerek | moderation/messages | `/vanish`, visibility permission | moderation regresszió | [admin guide](../guides/ADMIN_AND_MODERATOR_GUIDE.md) | Nincs | Új, rollout-kapu |
| Inventory admin | nincs | invsee service/escrow/store/GUI | moderation/messages | `/invsee`, GUI | moderation regresszió | [admin guide](../guides/ADMIN_AND_MODERATOR_GUIDE.md) | Nincs | Új, rollout-kapu |
| Offline teleport | nincs | command + persistent request | messages/store | `/offlinetp`, következő login | moderation regresszió | [admin guide](../guides/ADMIN_AND_MODERATOR_GUIDE.md) | Nincs | Új, rollout-kapu |
| Sit-only | nincs | sit manager/listener/seat policy | `sit.yml` | `/sit`, kattintás | sit regression | [builder guide](../guides/BUILDER_AND_WORLD_DESIGNER_GUIDE.md) | Nincs | Új, rollout-kapu |
| Crate | nincs | manager/domain/store/GUI/listener | `crates.yml` | `/crate`, fizikai blokk és GUI | crate regression | [crate fejezet](ICESMP_RELEASE_AND_TEAM_GUIDE.md) | Nincs | Új, fault teszt kell |
| Globális AFK | nincs | tracker, command, activity és reward gate | `afk.yml`, AFK messages | `/afk`, automatikus trigger, tablista | AFK regression | [AFK fejezet](ICESMP_RELEASE_AND_TEAM_GUIDE.md) | Nincs | Új, aktív |
| Jutalmazó AFK-zóna | nincs | nincs runtime útvonal | nincs aktív zónaconfig | nincs | consistency guard | [scope](EXTERNAL_PLUGIN_STATUS.md) | Nincs | Elvetett/out of scope |
| Warden-XP | nincs | world tweak listener | `world.yml` | automatikus entity death | build/consistency | [acceptance](RELEASE_ACCEPTANCE_CHECKLIST.md) | Nincs | Natív megfelelő, kézi teszt |
| Crop protection | nincs | player és mob listenerútvonal | `world.yml` | automatikus PHYSICAL/interact | build/consistency | [acceptance](RELEASE_ACCEPTANCE_CHECKLIST.md) | Nincs | Natív megfelelő, kézi teszt |

## Scope-negatív bizonyíték

| Tiltott/elvetett terület | Bináris baseline | Release-forrás | Release-resource | Dokumentációs döntés |
|---|---|---|---|---|
| Jutalmazó AFK-zóna | nincs | nincs zónamanager/payout/tick wiring | nincs `afk.zones`, reward vagy bossbar | Elvetett / out of scope |
| Lay | nincs | nincs regisztrált command/listener | nincs aktív config | Elvetett / out of scope |
| Crawl | nincs | nincs regisztrált command/listener | nincs aktív config | Elvetett / out of scope |
| Stacking | nincs | nincs regisztrált command/listener | nincs aktív config | Elvetett / out of scope |
| Player/NPC sitting | nincs | a seat célja saját ülés | nincs aktív config | Elvetett / out of scope |

## Interface- és content-leltár

| Leltár | Release | Deployed baseline | Elsődleges dokumentum |
|---|---:|---:|---|
| Root parancs | 68 | 30 | [COMMAND_REFERENCE](../reference/COMMAND_REFERENCE.md) |
| Root alias | 79 | 56 | [COMMAND_REFERENCE](../reference/COMMAND_REFERENCE.md) |
| Funkcionális command route | 286 | 110 auditált baseline route | [COMMAND_REFERENCE](../reference/COMMAND_REFERENCE.md) |
| Route-alias | 93 | a baseline auditban a route-ok részeként | [COMMAND_REFERENCE](../reference/COMMAND_REFERENCE.md) |
| Permission | 44 | 16 | [PERMISSION_REFERENCE](../reference/PERMISSION_REFERENCE.md) |
| GUI | 22 | 14 család | [GUI_REFERENCE](../reference/GUI_REFERENCE.md) |
| Config/data path | 13 550 egyedi dokumentált path (12 223 scanner + 1 327 kiegészítés) | 4 483 | [CONFIGURATION_REFERENCE](../reference/CONFIGURATION_REFERENCE.md) |
| Message key | 1 614 nem üres unió / 1 269 bundled node | 906 bundled default | [MESSAGE_REFERENCE](../reference/MESSAGE_REFERENCE.md) |
| Main Java komponens | 545 | 298 top-level class | [coverage](RELEASE_DOCUMENTATION_COVERAGE.md) |

## Forrásbizonyítékok kezelésének szabálya

- A command csak bootstrap-regisztrációval aktív.
- A GUI csak holder és listener/click routing együttese alapján aktív.
- A config default nem bizonyít élő override-ot.
- A registry-ID nem bizonyít kész fizikai helyszínt.
- A lore/teaser nem runtime-bizonyíték.
- A teszt jelenléte nem bizonyít production szerveres átvételt.
- A deployed JAR-ban hiányzó, release-ben jelen lévő funkció `Új`; a
  baseline-ban bizonyított funkció nem jelölhető automatikusan újként.

## Ismert forrás/config eltérések

Az inventory név szerint őrzi az öt kódban olvasott, de bundled defaultból
hiányzó configpathot és a `factions.raid.duration-minutes` integer/long
típusdriftet. Ezek nem kerültek elhallgatásra vagy gameplay-javításként a
dokumentációs PR-ba; a
[konfigurációs referencia](../reference/CONFIGURATION_REFERENCE.md) ismert
korlátként, fallbackkel és deploymentteendővel dokumentálja őket.

Az AFK-jutalomkapu jelenleg nem egységes: profession-XP az
`afk.block-rewards` kulcsot olvassa; a közös kill/boss kapu a
`kill-rewards.afk-block` kulcsot, az AFK-kulcsra visszaesve; a fishing
windfall és ambient pénzjutalom mindig tilt AFK esetén. Ez forrásból
bizonyított ismert eltérés, nem production garancia és nem rejtett
gameplay-javítás a dokumentációs PR-ban.

## Runtime bizonyíték helye

A kézi tesztek bizonyítékát a
[RELEASE_ACCEPTANCE_CHECKLIST.md](RELEASE_ACCEPTANCE_CHECKLIST.md) által előírt
helyen kell rögzíteni. Elfogadható bizonyíték: időbélyeges log, screenshot/video,
tesztelő és szerepkör, build SHA/hash, config snapshot, előkészítés,
várt–kapott eredmény és szükség esetén recovery/audit rekord.

<!-- BEGIN GENERATED COMPONENT MANIFEST MARKERS -->
## Dokumentációs markerregiszter — production komponensek

Ez a rövid technikai tábla az aktuális production source inventory minden komponensét pontosan egy dokumentált funkcióhoz rendeli. A sorok coverage-markerek; a kategória, forrásút és deployed-class bizonyíték a buildben generált release inventoryban marad.

| Stabil component ID | Dokumentált feature ID |
|---|---|
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.IceSMP --> `component.hu.taliann.icesmp.IceSMP` | `feature.core.lifecycle` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.IceSMPBootstrap --> `component.hu.taliann.icesmp.IceSMPBootstrap` | `feature.core.lifecycle` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.IceSMPLoader --> `component.hu.taliann.icesmp.IceSMPLoader` | `feature.core.lifecycle` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.AbstractDispatchCommand --> `component.hu.taliann.icesmp.commands.AbstractDispatchCommand` | `feature.core.shared_services` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.AchievementsCommand --> `component.hu.taliann.icesmp.commands.AchievementsCommand` | `feature.progression.achievements_stats` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ActivePunishmentsCommand --> `component.hu.taliann.icesmp.commands.ActivePunishmentsCommand` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.AfkCommand --> `component.hu.taliann.icesmp.commands.AfkCommand` | `feature.player.global_afk` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.BankCommand --> `component.hu.taliann.icesmp.commands.BankCommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.BestiaryCommand --> `component.hu.taliann.icesmp.commands.BestiaryCommand` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.BountyCommand --> `component.hu.taliann.icesmp.commands.BountyCommand` | `feature.economy.rewards_bounty` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ClaimCommand --> `component.hu.taliann.icesmp.commands.ClaimCommand` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.CrateCommand --> `component.hu.taliann.icesmp.commands.CrateCommand` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.CurrencyCommand --> `component.hu.taliann.icesmp.commands.CurrencyCommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.DailyCommand --> `component.hu.taliann.icesmp.commands.DailyCommand` | `feature.economy.rewards_bounty` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.DonationChestCommand --> `component.hu.taliann.icesmp.commands.DonationChestCommand` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.EventsCommand --> `component.hu.taliann.icesmp.commands.EventsCommand` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ExchangeBoardCommand --> `component.hu.taliann.icesmp.commands.ExchangeBoardCommand` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.FactionCommand --> `component.hu.taliann.icesmp.commands.FactionCommand` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.GuildCommand --> `component.hu.taliann.icesmp.commands.GuildCommand` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.HonorDuelCommand --> `component.hu.taliann.icesmp.commands.HonorDuelCommand` | `feature.world.combat_rules` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.HudCommand --> `component.hu.taliann.icesmp.commands.HudCommand` | `feature.player.hud_tablist` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.IceSMPCommand --> `component.hu.taliann.icesmp.commands.IceSMPCommand` | `feature.admin.control_panel` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.InvseeCommand --> `component.hu.taliann.icesmp.commands.InvseeCommand` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ItemGiveCommand --> `component.hu.taliann.icesmp.commands.ItemGiveCommand` | `feature.developer.items_debug` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.JobCommand --> `component.hu.taliann.icesmp.commands.JobCommand` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.KompCommand --> `component.hu.taliann.icesmp.commands.KompCommand` | `feature.world.travel_npc` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.KronikaCommand --> `component.hu.taliann.icesmp.commands.KronikaCommand` | `feature.progression.story_lore` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.LeaderboardCommand --> `component.hu.taliann.icesmp.commands.LeaderboardCommand` | `feature.progression.achievements_stats` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.LoreCommand --> `component.hu.taliann.icesmp.commands.LoreCommand` | `feature.progression.story_lore` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.MarketCommand --> `component.hu.taliann.icesmp.commands.MarketCommand` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.MemoryCommand --> `component.hu.taliann.icesmp.commands.MemoryCommand` | `feature.progression.story_lore` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.MenuCommand --> `component.hu.taliann.icesmp.commands.MenuCommand` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ModerationActionCommand --> `component.hu.taliann.icesmp.commands.ModerationActionCommand` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ModerationCommandSupport --> `component.hu.taliann.icesmp.commands.ModerationCommandSupport` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ModerationGuiCommand --> `component.hu.taliann.icesmp.commands.ModerationGuiCommand` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ModerationRevokeCommand --> `component.hu.taliann.icesmp.commands.ModerationRevokeCommand` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.MuteCommand --> `component.hu.taliann.icesmp.commands.MuteCommand` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.NpcBindCommand --> `component.hu.taliann.icesmp.commands.NpcBindCommand` | `feature.world.travel_npc` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.OfflineTeleportCommand --> `component.hu.taliann.icesmp.commands.OfflineTeleportCommand` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ParkourCommand --> `component.hu.taliann.icesmp.commands.ParkourCommand` | `feature.world.parkour_discovery` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.PartyCommand --> `component.hu.taliann.icesmp.commands.PartyCommand` | `feature.progression.party` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.PetCommand --> `component.hu.taliann.icesmp.commands.PetCommand` | `feature.progression.pets` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.PrivateMessageCommand --> `component.hu.taliann.icesmp.commands.PrivateMessageCommand` | `feature.admin.reports_messaging` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ProfessionCommand --> `component.hu.taliann.icesmp.commands.ProfessionCommand` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ProfessionWeeklyCommand --> `component.hu.taliann.icesmp.commands.ProfessionWeeklyCommand` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ProfileCommand --> `component.hu.taliann.icesmp.commands.ProfileCommand` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.PunishmentHistoryCommand --> `component.hu.taliann.icesmp.commands.PunishmentHistoryCommand` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.QuestCommand --> `component.hu.taliann.icesmp.commands.QuestCommand` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.RelicCommand --> `component.hu.taliann.icesmp.commands.RelicCommand` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ReportCommand --> `component.hu.taliann.icesmp.commands.ReportCommand` | `feature.admin.reports_messaging` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ReportsCommand --> `component.hu.taliann.icesmp.commands.ReportsCommand` | `feature.admin.reports_messaging` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SinnerCommand --> `component.hu.taliann.icesmp.commands.SinnerCommand` | `feature.world.corruption_sin` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SitCommand --> `component.hu.taliann.icesmp.commands.SitCommand` | `feature.world.sit_only` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SocialSpyCommand --> `component.hu.taliann.icesmp.commands.SocialSpyCommand` | `feature.admin.reports_messaging` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SoulCommand --> `component.hu.taliann.icesmp.commands.SoulCommand` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SoulforgeCommand --> `component.hu.taliann.icesmp.commands.SoulforgeCommand` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SpecCommand --> `component.hu.taliann.icesmp.commands.SpecCommand` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SpellCommand --> `component.hu.taliann.icesmp.commands.SpellCommand` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SpellbookCommand --> `component.hu.taliann.icesmp.commands.SpellbookCommand` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SpyCommand --> `component.hu.taliann.icesmp.commands.SpyCommand` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.StatsCommand --> `component.hu.taliann.icesmp.commands.StatsCommand` | `feature.progression.achievements_stats` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.Subcommand --> `component.hu.taliann.icesmp.commands.Subcommand` | `feature.core.shared_services` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.TalentCommand --> `component.hu.taliann.icesmp.commands.TalentCommand` | `feature.progression.talents_skills` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.TanacsCommand --> `component.hu.taliann.icesmp.commands.TanacsCommand` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.TerritoryCommand --> `component.hu.taliann.icesmp.commands.TerritoryCommand` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.UnmuteCommand --> `component.hu.taliann.icesmp.commands.UnmuteCommand` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.VanishCommand --> `component.hu.taliann.icesmp.commands.VanishCommand` | `feature.admin.vanish` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.WhisperCommand --> `component.hu.taliann.icesmp.commands.WhisperCommand` | `feature.admin.reports_messaging` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.bank.BankBalanceSubcommand --> `component.hu.taliann.icesmp.commands.bank.BankBalanceSubcommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.bank.BankDepositSubcommand --> `component.hu.taliann.icesmp.commands.bank.BankDepositSubcommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.bank.BankSubcommand --> `component.hu.taliann.icesmp.commands.bank.BankSubcommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.bank.BankWithdrawSubcommand --> `component.hu.taliann.icesmp.commands.bank.BankWithdrawSubcommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencyBalanceSubcommand --> `component.hu.taliann.icesmp.commands.currency.CurrencyBalanceSubcommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencyExchangeSubcommand --> `component.hu.taliann.icesmp.commands.currency.CurrencyExchangeSubcommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencyPaySubcommand --> `component.hu.taliann.icesmp.commands.currency.CurrencyPaySubcommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencyRatesSubcommand --> `component.hu.taliann.icesmp.commands.currency.CurrencyRatesSubcommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencySetSubcommand --> `component.hu.taliann.icesmp.commands.currency.CurrencySetSubcommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencySubcommand --> `component.hu.taliann.icesmp.commands.currency.CurrencySubcommand` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionCaravanSubcommand --> `component.hu.taliann.icesmp.commands.faction.FactionCaravanSubcommand` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionDonateSubcommand --> `component.hu.taliann.icesmp.commands.faction.FactionDonateSubcommand` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionJoinSubcommand --> `component.hu.taliann.icesmp.commands.faction.FactionJoinSubcommand` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionKingSubcommand --> `component.hu.taliann.icesmp.commands.faction.FactionKingSubcommand` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionLeaveSubcommand --> `component.hu.taliann.icesmp.commands.faction.FactionLeaveSubcommand` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionRaidSubcommand --> `component.hu.taliann.icesmp.commands.faction.FactionRaidSubcommand` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionSetSubcommand --> `component.hu.taliann.icesmp.commands.faction.FactionSetSubcommand` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionSubcommand --> `component.hu.taliann.icesmp.commands.faction.FactionSubcommand` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionSwitchRules --> `component.hu.taliann.icesmp.commands.faction.FactionSwitchRules` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionTreasurySubcommand --> `component.hu.taliann.icesmp.commands.faction.FactionTreasurySubcommand` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionWarSubcommand --> `component.hu.taliann.icesmp.commands.faction.FactionWarSubcommand` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobAddXpSubcommand --> `component.hu.taliann.icesmp.commands.job.JobAddXpSubcommand` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobAdminSubcommand --> `component.hu.taliann.icesmp.commands.job.JobAdminSubcommand` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobGiveCatalystSubcommand --> `component.hu.taliann.icesmp.commands.job.JobGiveCatalystSubcommand` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobListSpellsSubcommand --> `component.hu.taliann.icesmp.commands.job.JobListSpellsSubcommand` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobSetXpSubcommand --> `component.hu.taliann.icesmp.commands.job.JobSetXpSubcommand` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobStatusSubcommand --> `component.hu.taliann.icesmp.commands.job.JobStatusSubcommand` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobSubcommand --> `component.hu.taliann.icesmp.commands.job.JobSubcommand` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobUnlockSpellSubcommand --> `component.hu.taliann.icesmp.commands.job.JobUnlockSpellSubcommand` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.core.IceSMPCore --> `component.hu.taliann.icesmp.core.IceSMPCore` | `feature.core.lifecycle` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.core.Permissions --> `component.hu.taliann.icesmp.core.Permissions` | `feature.admin.control_panel` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateAuditWriter --> `component.hu.taliann.icesmp.crates.CrateAuditWriter` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateCallbackGate --> `component.hu.taliann.icesmp.crates.CrateCallbackGate` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateCommandBatch --> `component.hu.taliann.icesmp.crates.CrateCommandBatch` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateFormatting --> `component.hu.taliann.icesmp.crates.CrateFormatting` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateLedger --> `component.hu.taliann.icesmp.crates.CrateLedger` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateOpeningLifecycle --> `component.hu.taliann.icesmp.crates.CrateOpeningLifecycle` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateRecoveryLedger --> `component.hu.taliann.icesmp.crates.CrateRecoveryLedger` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateRewardProgress --> `component.hu.taliann.icesmp.crates.CrateRewardProgress` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateRules --> `component.hu.taliann.icesmp.crates.CrateRules` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateTaskLease --> `component.hu.taliann.icesmp.crates.CrateTaskLease` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateTaskSubmission --> `component.hu.taliann.icesmp.crates.CrateTaskSubmission` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.KeyConsumption --> `component.hu.taliann.icesmp.crates.KeyConsumption` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.WeightedSelector --> `component.hu.taliann.icesmp.crates.WeightedSelector` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.CraftingRule --> `component.hu.taliann.icesmp.data.CraftingRule` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.CurrencyType --> `component.hu.taliann.icesmp.data.CurrencyType` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.FactionType --> `component.hu.taliann.icesmp.data.FactionType` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.JobType --> `component.hu.taliann.icesmp.data.JobType` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.ProfessionCategory --> `component.hu.taliann.icesmp.data.ProfessionCategory` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.ProfessionSpecializationType --> `component.hu.taliann.icesmp.data.ProfessionSpecializationType` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.ProfessionType --> `component.hu.taliann.icesmp.data.ProfessionType` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.SpecializationType --> `component.hu.taliann.icesmp.data.SpecializationType` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.SpellSchool --> `component.hu.taliann.icesmp.data.SpellSchool` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.Territory --> `component.hu.taliann.icesmp.data.Territory` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.TerritoryType --> `component.hu.taliann.icesmp.data.TerritoryType` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.data.Wallet --> `component.hu.taliann.icesmp.data.Wallet` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.BestiaryHolder --> `component.hu.taliann.icesmp.gui.BestiaryHolder` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CharacterMenuContext --> `component.hu.taliann.icesmp.gui.CharacterMenuContext` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ClaimTrustGUI --> `component.hu.taliann.icesmp.gui.ClaimTrustGUI` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ClaimTrustHolder --> `component.hu.taliann.icesmp.gui.ClaimTrustHolder` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CommandMenuContext --> `component.hu.taliann.icesmp.gui.CommandMenuContext` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CommandMenuHolder --> `component.hu.taliann.icesmp.gui.CommandMenuHolder` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CommandMenus --> `component.hu.taliann.icesmp.gui.CommandMenus` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ConfigMenuGUI --> `component.hu.taliann.icesmp.gui.ConfigMenuGUI` | `feature.core.configuration` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ConfigMenuHolder --> `component.hu.taliann.icesmp.gui.ConfigMenuHolder` | `feature.core.configuration` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CrateBrowserGUI --> `component.hu.taliann.icesmp.gui.CrateBrowserGUI` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CrateBrowserHolder --> `component.hu.taliann.icesmp.gui.CrateBrowserHolder` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CrateSpinGUI --> `component.hu.taliann.icesmp.gui.CrateSpinGUI` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CrateSpinHolder --> `component.hu.taliann.icesmp.gui.CrateSpinHolder` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.DonationChestGUI --> `component.hu.taliann.icesmp.gui.DonationChestGUI` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.DonationChestHolder --> `component.hu.taliann.icesmp.gui.DonationChestHolder` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.GuiUtil --> `component.hu.taliann.icesmp.gui.GuiUtil` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.InvseeGUI --> `component.hu.taliann.icesmp.gui.InvseeGUI` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.InvseeHolder --> `component.hu.taliann.icesmp.gui.InvseeHolder` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.JobGUI --> `component.hu.taliann.icesmp.gui.JobGUI` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.JobGUIHolder --> `component.hu.taliann.icesmp.gui.JobGUIHolder` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.MarketGUI --> `component.hu.taliann.icesmp.gui.MarketGUI` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.MarketHolder --> `component.hu.taliann.icesmp.gui.MarketHolder` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ModerationGUI --> `component.hu.taliann.icesmp.gui.ModerationGUI` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ModerationGuiHolder --> `component.hu.taliann.icesmp.gui.ModerationGuiHolder` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.PetGUI --> `component.hu.taliann.icesmp.gui.PetGUI` | `feature.progression.pets` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.PetGUIHolder --> `component.hu.taliann.icesmp.gui.PetGUIHolder` | `feature.progression.pets` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfessionGUI --> `component.hu.taliann.icesmp.gui.ProfessionGUI` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfessionHolder --> `component.hu.taliann.icesmp.gui.ProfessionHolder` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfessionRecipeGUI --> `component.hu.taliann.icesmp.gui.ProfessionRecipeGUI` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfessionRecipeHolder --> `component.hu.taliann.icesmp.gui.ProfessionRecipeHolder` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfileGUI --> `component.hu.taliann.icesmp.gui.ProfileGUI` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfileHolder --> `component.hu.taliann.icesmp.gui.ProfileHolder` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.QuestBuilderGUI --> `component.hu.taliann.icesmp.gui.QuestBuilderGUI` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.QuestBuilderHolder --> `component.hu.taliann.icesmp.gui.QuestBuilderHolder` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.QuestLogGUI --> `component.hu.taliann.icesmp.gui.QuestLogGUI` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.QuestLogHolder --> `component.hu.taliann.icesmp.gui.QuestLogHolder` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ShopGUI --> `component.hu.taliann.icesmp.gui.ShopGUI` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ShopHolder --> `component.hu.taliann.icesmp.gui.ShopHolder` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SkillTreeGUI --> `component.hu.taliann.icesmp.gui.SkillTreeGUI` | `feature.progression.talents_skills` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SkillTreeHolder --> `component.hu.taliann.icesmp.gui.SkillTreeHolder` | `feature.progression.talents_skills` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SpecGUI --> `component.hu.taliann.icesmp.gui.SpecGUI` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SpecHolder --> `component.hu.taliann.icesmp.gui.SpecHolder` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SpellbookGUI --> `component.hu.taliann.icesmp.gui.SpellbookGUI` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SpellbookHolder --> `component.hu.taliann.icesmp.gui.SpellbookHolder` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.TalentGUI --> `component.hu.taliann.icesmp.gui.TalentGUI` | `feature.progression.talents_skills` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.TalentHolder --> `component.hu.taliann.icesmp.gui.TalentHolder` | `feature.progression.talents_skills` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.DruidDisguise --> `component.hu.taliann.icesmp.integration.DruidDisguise` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.FancyNpcsQuestBridge --> `component.hu.taliann.icesmp.integration.FancyNpcsQuestBridge` | `feature.core.integrations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.IceSMPPlaceholders --> `component.hu.taliann.icesmp.integration.IceSMPPlaceholders` | `feature.core.integrations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.LuckPermsBridge --> `component.hu.taliann.icesmp.integration.LuckPermsBridge` | `feature.core.integrations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.ProtectionBridge --> `component.hu.taliann.icesmp.integration.ProtectionBridge` | `feature.core.integrations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.SpyDisguise --> `component.hu.taliann.icesmp.integration.SpyDisguise` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.BlueprintItemFactory --> `component.hu.taliann.icesmp.items.BlueprintItemFactory` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.CaptureItemFactory --> `component.hu.taliann.icesmp.items.CaptureItemFactory` | `feature.progression.pets` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.CatalystItemFactory --> `component.hu.taliann.icesmp.items.CatalystItemFactory` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.CrateKeyFactory --> `component.hu.taliann.icesmp.items.CrateKeyFactory` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.CurrencyItemFactory --> `component.hu.taliann.icesmp.items.CurrencyItemFactory` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.DevItemFactory --> `component.hu.taliann.icesmp.items.DevItemFactory` | `feature.developer.items_debug` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.ItemDataFactory --> `component.hu.taliann.icesmp.items.ItemDataFactory` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.MoneyPouchItemFactory --> `component.hu.taliann.icesmp.items.MoneyPouchItemFactory` | `feature.economy.rewards_bounty` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.RelicItemFactory --> `component.hu.taliann.icesmp.items.RelicItemFactory` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.SiegeWeaponFactory --> `component.hu.taliann.icesmp.items.SiegeWeaponFactory` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.SignatureEnchantKeys --> `component.hu.taliann.icesmp.items.SignatureEnchantKeys` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.items.UniqueMaterialFactory --> `component.hu.taliann.icesmp.items.UniqueMaterialFactory` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.AbilityCatalystListener --> `component.hu.taliann.icesmp.listeners.AbilityCatalystListener` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.AbundanceListener --> `component.hu.taliann.icesmp.listeners.AbundanceListener` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.AfkActivityListener --> `component.hu.taliann.icesmp.listeners.AfkActivityListener` | `feature.player.global_afk` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ArcheologyShareListener --> `component.hu.taliann.icesmp.listeners.ArcheologyShareListener` | `feature.world.parkour_discovery` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.BestiaryListener --> `component.hu.taliann.icesmp.listeners.BestiaryListener` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.BlueprintUseListener --> `component.hu.taliann.icesmp.listeners.BlueprintUseListener` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CampfireStoryListener --> `component.hu.taliann.icesmp.listeners.CampfireStoryListener` | `feature.progression.story_lore` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CapitalLawListener --> `component.hu.taliann.icesmp.listeners.CapitalLawListener` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CaravanListener --> `component.hu.taliann.icesmp.listeners.CaravanListener` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CatalystCraftSafetyListener --> `component.hu.taliann.icesmp.listeners.CatalystCraftSafetyListener` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CatalystProtectionListener --> `component.hu.taliann.icesmp.listeners.CatalystProtectionListener` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CharacterGUIListener --> `component.hu.taliann.icesmp.listeners.CharacterGUIListener` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ChatFormatListener --> `component.hu.taliann.icesmp.listeners.ChatFormatListener` | `feature.player.chat_feedback` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ChatModerationListener --> `component.hu.taliann.icesmp.listeners.ChatModerationListener` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ClaimProtectionListener --> `component.hu.taliann.icesmp.listeners.ClaimProtectionListener` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ClaimTrustGUIListener --> `component.hu.taliann.icesmp.listeners.ClaimTrustGUIListener` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ClassXpListener --> `component.hu.taliann.icesmp.listeners.ClassXpListener` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CombatTagListener --> `component.hu.taliann.icesmp.listeners.CombatTagListener` | `feature.world.combat_rules` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CommandMenuListener --> `component.hu.taliann.icesmp.listeners.CommandMenuListener` | `feature.player.menus_profile` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ConfigMenuGUIListener --> `component.hu.taliann.icesmp.listeners.ConfigMenuGUIListener` | `feature.core.configuration` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CorruptionAuraListener --> `component.hu.taliann.icesmp.listeners.CorruptionAuraListener` | `feature.world.corruption_sin` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CorruptionListener --> `component.hu.taliann.icesmp.listeners.CorruptionListener` | `feature.world.corruption_sin` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CrateBrowserGUIListener --> `component.hu.taliann.icesmp.listeners.CrateBrowserGUIListener` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CrateListener --> `component.hu.taliann.icesmp.listeners.CrateListener` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CrateSpinGUIListener --> `component.hu.taliann.icesmp.listeners.CrateSpinGUIListener` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CurrencyCraftListener --> `component.hu.taliann.icesmp.listeners.CurrencyCraftListener` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CurrencyItemRefreshListener --> `component.hu.taliann.icesmp.listeners.CurrencyItemRefreshListener` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CursedGearListener --> `component.hu.taliann.icesmp.listeners.CursedGearListener` | `feature.world.corruption_sin` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DailyQuestListener --> `component.hu.taliann.icesmp.listeners.DailyQuestListener` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DamageIndicatorListener --> `component.hu.taliann.icesmp.listeners.DamageIndicatorListener` | `feature.player.chat_feedback` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DeathRecapListener --> `component.hu.taliann.icesmp.listeners.DeathRecapListener` | `feature.player.chat_feedback` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DevItemProtectionListener --> `component.hu.taliann.icesmp.listeners.DevItemProtectionListener` | `feature.world.rules_protection` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DisplayFxCleanupListener --> `component.hu.taliann.icesmp.listeners.DisplayFxCleanupListener` | `feature.player.chat_feedback` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DonationChestListener --> `component.hu.taliann.icesmp.listeners.DonationChestListener` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DungeonGateListener --> `component.hu.taliann.icesmp.listeners.DungeonGateListener` | `feature.world.dungeons` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DungeonLootListener --> `component.hu.taliann.icesmp.listeners.DungeonLootListener` | `feature.world.dungeons` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ElytraRelicListener --> `component.hu.taliann.icesmp.listeners.ElytraRelicListener` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.EscortListener --> `component.hu.taliann.icesmp.listeners.EscortListener` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.FactionFoodListener --> `component.hu.taliann.icesmp.listeners.FactionFoodListener` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.FactionPassiveListener --> `component.hu.taliann.icesmp.listeners.FactionPassiveListener` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.FactionSpawnListener --> `component.hu.taliann.icesmp.listeners.FactionSpawnListener` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.FishingWindfallListener --> `component.hu.taliann.icesmp.listeners.FishingWindfallListener` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.GatheringBuffListener --> `component.hu.taliann.icesmp.listeners.GatheringBuffListener` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.HealthRegenListener --> `component.hu.taliann.icesmp.listeners.HealthRegenListener` | `feature.world.combat_rules` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.HudListener --> `component.hu.taliann.icesmp.listeners.HudListener` | `feature.player.hud_tablist` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.IntroListener --> `component.hu.taliann.icesmp.listeners.IntroListener` | `feature.player.onboarding` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.InvseeGUIListener --> `component.hu.taliann.icesmp.listeners.InvseeGUIListener` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ItemProvenanceListener --> `component.hu.taliann.icesmp.listeners.ItemProvenanceListener` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.JobCraftRestrictionListener --> `component.hu.taliann.icesmp.listeners.JobCraftRestrictionListener` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.JobGUIListener --> `component.hu.taliann.icesmp.listeners.JobGUIListener` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.LowHealthBorderListener --> `component.hu.taliann.icesmp.listeners.LowHealthBorderListener` | `feature.player.chat_feedback` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MarketDeliveryListener --> `component.hu.taliann.icesmp.listeners.MarketDeliveryListener` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MarketGUIListener --> `component.hu.taliann.icesmp.listeners.MarketGUIListener` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MasterworkCraftListener --> `component.hu.taliann.icesmp.listeners.MasterworkCraftListener` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MetelytepoRelicListener --> `component.hu.taliann.icesmp.listeners.MetelytepoRelicListener` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MinionProtectionListener --> `component.hu.taliann.icesmp.listeners.MinionProtectionListener` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MobLootListener --> `component.hu.taliann.icesmp.listeners.MobLootListener` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MobMoneyDropListener --> `component.hu.taliann.icesmp.listeners.MobMoneyDropListener` | `feature.economy.rewards_bounty` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MobScalingListener --> `component.hu.taliann.icesmp.listeners.MobScalingListener` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ModerationGUIListener --> `component.hu.taliann.icesmp.listeners.ModerationGUIListener` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ModerationLoginListener --> `component.hu.taliann.icesmp.listeners.ModerationLoginListener` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MoneyPouchListener --> `component.hu.taliann.icesmp.listeners.MoneyPouchListener` | `feature.economy.rewards_bounty` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MotdListener --> `component.hu.taliann.icesmp.listeners.MotdListener` | `feature.player.motd` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.OnboardingListener --> `component.hu.taliann.icesmp.listeners.OnboardingListener` | `feature.player.onboarding` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ParkourListener --> `component.hu.taliann.icesmp.listeners.ParkourListener` | `feature.world.parkour_discovery` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PartyListener --> `component.hu.taliann.icesmp.listeners.PartyListener` | `feature.progression.party` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PetCaptureListener --> `component.hu.taliann.icesmp.listeners.PetCaptureListener` | `feature.progression.pets` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PetCombatListener --> `component.hu.taliann.icesmp.listeners.PetCombatListener` | `feature.progression.pets` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PetCommandListener --> `component.hu.taliann.icesmp.listeners.PetCommandListener` | `feature.progression.pets` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PetGUIListener --> `component.hu.taliann.icesmp.listeners.PetGUIListener` | `feature.progression.pets` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PetXpListener --> `component.hu.taliann.icesmp.listeners.PetXpListener` | `feature.progression.pets` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PlayerSessionCleanupListener --> `component.hu.taliann.icesmp.listeners.PlayerSessionCleanupListener` | `feature.core.lifecycle` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PortalGuardListener --> `component.hu.taliann.icesmp.listeners.PortalGuardListener` | `feature.world.rules_protection` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ProfessionRecipeBookListener --> `component.hu.taliann.icesmp.listeners.ProfessionRecipeBookListener` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ProfessionRecipeListener --> `component.hu.taliann.icesmp.listeners.ProfessionRecipeListener` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ProfessionXpListener --> `component.hu.taliann.icesmp.listeners.ProfessionXpListener` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.QuestBuilderListener --> `component.hu.taliann.icesmp.listeners.QuestBuilderListener` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.QuestLogListener --> `component.hu.taliann.icesmp.listeners.QuestLogListener` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.QuestProgressListener --> `component.hu.taliann.icesmp.listeners.QuestProgressListener` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RelicCraftSafetyListener --> `component.hu.taliann.icesmp.listeners.RelicCraftSafetyListener` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RelicInactivityListener --> `component.hu.taliann.icesmp.listeners.RelicInactivityListener` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RelicItemRefreshListener --> `component.hu.taliann.icesmp.listeners.RelicItemRefreshListener` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RelicPvpTransferListener --> `component.hu.taliann.icesmp.listeners.RelicPvpTransferListener` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RelicTriggerListener --> `component.hu.taliann.icesmp.listeners.RelicTriggerListener` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ReportFeedbackListener --> `component.hu.taliann.icesmp.listeners.ReportFeedbackListener` | `feature.admin.reports_messaging` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ResourceCombatListener --> `component.hu.taliann.icesmp.listeners.ResourceCombatListener` | `feature.world.combat_rules` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RitualListener --> `component.hu.taliann.icesmp.listeners.RitualListener` | `feature.world.rituals` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RuneApplyListener --> `component.hu.taliann.icesmp.listeners.RuneApplyListener` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RuneEffectListener --> `component.hu.taliann.icesmp.listeners.RuneEffectListener` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SchoolCounterAnvilListener --> `component.hu.taliann.icesmp.listeners.SchoolCounterAnvilListener` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SelectionWandListener --> `component.hu.taliann.icesmp.listeners.SelectionWandListener` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ServerChallengeListener --> `component.hu.taliann.icesmp.listeners.ServerChallengeListener` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ShopListener --> `component.hu.taliann.icesmp.listeners.ShopListener` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SiegeWeaponListener --> `component.hu.taliann.icesmp.listeners.SiegeWeaponListener` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SignatureItemListener --> `component.hu.taliann.icesmp.listeners.SignatureItemListener` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SinListener --> `component.hu.taliann.icesmp.listeners.SinListener` | `feature.world.corruption_sin` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SitListener --> `component.hu.taliann.icesmp.listeners.SitListener` | `feature.world.sit_only` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SkillTreeGUIListener --> `component.hu.taliann.icesmp.listeners.SkillTreeGUIListener` | `feature.progression.talents_skills` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SoulShardListener --> `component.hu.taliann.icesmp.listeners.SoulShardListener` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SoulstoneListener --> `component.hu.taliann.icesmp.listeners.SoulstoneListener` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SpellDamageListener --> `component.hu.taliann.icesmp.listeners.SpellDamageListener` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SpellProjectileListener --> `component.hu.taliann.icesmp.listeners.SpellProjectileListener` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SpellStateListener --> `component.hu.taliann.icesmp.listeners.SpellStateListener` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SpellbookListener --> `component.hu.taliann.icesmp.listeners.SpellbookListener` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SpyRevealListener --> `component.hu.taliann.icesmp.listeners.SpyRevealListener` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.StatsCombatListener --> `component.hu.taliann.icesmp.listeners.StatsCombatListener` | `feature.progression.achievements_stats` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.StrangerListener --> `component.hu.taliann.icesmp.listeners.StrangerListener` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.TalentAttributeListener --> `component.hu.taliann.icesmp.listeners.TalentAttributeListener` | `feature.progression.talents_skills` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.TerritoryListener --> `component.hu.taliann.icesmp.listeners.TerritoryListener` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.TerritoryProtectionListener --> `component.hu.taliann.icesmp.listeners.TerritoryProtectionListener` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.TheftListener --> `component.hu.taliann.icesmp.listeners.TheftListener` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.TreasureListener --> `component.hu.taliann.icesmp.listeners.TreasureListener` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.UniqueMaterialProtectionListener --> `component.hu.taliann.icesmp.listeners.UniqueMaterialProtectionListener` | `feature.world.rules_protection` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.VanishListener --> `component.hu.taliann.icesmp.listeners.VanishListener` | `feature.admin.vanish` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.WhisperListener --> `component.hu.taliann.icesmp.listeners.WhisperListener` | `feature.admin.reports_messaging` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.WildHuntListener --> `component.hu.taliann.icesmp.listeners.WildHuntListener` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.WorldBossListener --> `component.hu.taliann.icesmp.listeners.WorldBossListener` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.WorldGameRuleListener --> `component.hu.taliann.icesmp.listeners.WorldGameRuleListener` | `feature.world.rules_protection` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.WorldTweaksListener --> `component.hu.taliann.icesmp.listeners.WorldTweaksListener` | `feature.world.rules_protection` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.AbundanceManager --> `component.hu.taliann.icesmp.managers.AbundanceManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.AchievementManager --> `component.hu.taliann.icesmp.managers.AchievementManager` | `feature.progression.achievements_stats` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.AdvancementService --> `component.hu.taliann.icesmp.managers.AdvancementService` | `feature.progression.achievements_stats` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.AfkManager --> `component.hu.taliann.icesmp.managers.AfkManager` | `feature.player.global_afk` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.AmbientEventManager --> `component.hu.taliann.icesmp.managers.AmbientEventManager` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ArcheologyManager --> `component.hu.taliann.icesmp.managers.ArcheologyManager` | `feature.world.parkour_discovery` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.BardManager --> `component.hu.taliann.icesmp.managers.BardManager` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.BestiaryManager --> `component.hu.taliann.icesmp.managers.BestiaryManager` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.BlockRegenService --> `component.hu.taliann.icesmp.managers.BlockRegenService` | `feature.world.rules_protection` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.BloodMoonManager --> `component.hu.taliann.icesmp.managers.BloodMoonManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.BuyerService --> `component.hu.taliann.icesmp.managers.BuyerService` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CaravanManager --> `component.hu.taliann.icesmp.managers.CaravanManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ChronicleManager --> `component.hu.taliann.icesmp.managers.ChronicleManager` | `feature.progression.story_lore` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CityGuardManager --> `component.hu.taliann.icesmp.managers.CityGuardManager` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ClaimManager --> `component.hu.taliann.icesmp.managers.ClaimManager` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ClassHealthService --> `component.hu.taliann.icesmp.managers.ClassHealthService` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CombatTagManager --> `component.hu.taliann.icesmp.managers.CombatTagManager` | `feature.world.combat_rules` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CommunityGoalManager --> `component.hu.taliann.icesmp.managers.CommunityGoalManager` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CommunitySeasonState --> `component.hu.taliann.icesmp.managers.CommunitySeasonState` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ConfigManager --> `component.hu.taliann.icesmp.managers.ConfigManager` | `feature.core.configuration` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ConfigValidator --> `component.hu.taliann.icesmp.managers.ConfigValidator` | `feature.core.configuration` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CorruptionManager --> `component.hu.taliann.icesmp.managers.CorruptionManager` | `feature.world.corruption_sin` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CouncilManager --> `component.hu.taliann.icesmp.managers.CouncilManager` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CraftingRestrictionManager --> `component.hu.taliann.icesmp.managers.CraftingRestrictionManager` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CrateManager --> `component.hu.taliann.icesmp.managers.CrateManager` | `feature.economy.crates` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CrownCurseManager --> `component.hu.taliann.icesmp.managers.CrownCurseManager` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CultistEventManager --> `component.hu.taliann.icesmp.managers.CultistEventManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CurrencyManager --> `component.hu.taliann.icesmp.managers.CurrencyManager` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CurrencyStorageUnavailableException --> `component.hu.taliann.icesmp.managers.CurrencyStorageUnavailableException` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CursedGearService --> `component.hu.taliann.icesmp.managers.CursedGearService` | `feature.world.corruption_sin` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DailyQuestManager --> `component.hu.taliann.icesmp.managers.DailyQuestManager` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DarkUndeadAmbienceManager --> `component.hu.taliann.icesmp.managers.DarkUndeadAmbienceManager` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DevItemManager --> `component.hu.taliann.icesmp.managers.DevItemManager` | `feature.developer.items_debug` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DevItemStateData --> `component.hu.taliann.icesmp.managers.DevItemStateData` | `feature.developer.items_debug` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DialogService --> `component.hu.taliann.icesmp.managers.DialogService` | `feature.progression.story_lore` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DonationChestManager --> `component.hu.taliann.icesmp.managers.DonationChestManager` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DungeonLootService --> `component.hu.taliann.icesmp.managers.DungeonLootService` | `feature.world.dungeons` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.EconomyEventManager --> `component.hu.taliann.icesmp.managers.EconomyEventManager` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.EscortManager --> `component.hu.taliann.icesmp.managers.EscortManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.EventSpawnGuard --> `component.hu.taliann.icesmp.managers.EventSpawnGuard` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.EventSpawnPointManager --> `component.hu.taliann.icesmp.managers.EventSpawnPointManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ExchangeBoardManager --> `component.hu.taliann.icesmp.managers.ExchangeBoardManager` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ExchangeRateService --> `component.hu.taliann.icesmp.managers.ExchangeRateService` | `feature.economy.currency_bank` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.FactionManager --> `component.hu.taliann.icesmp.managers.FactionManager` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.FactionRelationManager --> `component.hu.taliann.icesmp.managers.FactionRelationManager` | `feature.factions.membership_relations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.FactionTreasuryManager --> `component.hu.taliann.icesmp.managers.FactionTreasuryManager` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.FerryManager --> `component.hu.taliann.icesmp.managers.FerryManager` | `feature.world.travel_npc` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.GatheringBuffManager --> `component.hu.taliann.icesmp.managers.GatheringBuffManager` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.GlobalAfkTracker --> `component.hu.taliann.icesmp.managers.GlobalAfkTracker` | `feature.player.global_afk` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.GuildManager --> `component.hu.taliann.icesmp.managers.GuildManager` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.HiddenSpotManager --> `component.hu.taliann.icesmp.managers.HiddenSpotManager` | `feature.world.parkour_discovery` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.HolidayService --> `component.hu.taliann.icesmp.managers.HolidayService` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.HonorDuelManager --> `component.hu.taliann.icesmp.managers.HonorDuelManager` | `feature.world.combat_rules` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.HudManager --> `component.hu.taliann.icesmp.managers.HudManager` | `feature.player.hud_tablist` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.IntroManager --> `component.hu.taliann.icesmp.managers.IntroManager` | `feature.player.onboarding` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.InvasionManager --> `component.hu.taliann.icesmp.managers.InvasionManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.InvseeManager --> `component.hu.taliann.icesmp.managers.InvseeManager` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ItemRarityService --> `component.hu.taliann.icesmp.managers.ItemRarityService` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.JobManager --> `component.hu.taliann.icesmp.managers.JobManager` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.KingManager --> `component.hu.taliann.icesmp.managers.KingManager` | `feature.factions.guilds_leadership` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.LootTable --> `component.hu.taliann.icesmp.managers.LootTable` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MajorEventGate --> `component.hu.taliann.icesmp.managers.MajorEventGate` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MarketManager --> `component.hu.taliann.icesmp.managers.MarketManager` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MetelytepoManager --> `component.hu.taliann.icesmp.managers.MetelytepoManager` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MeteorEventManager --> `component.hu.taliann.icesmp.managers.MeteorEventManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MinionManager --> `component.hu.taliann.icesmp.managers.MinionManager` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MobScalingManager --> `component.hu.taliann.icesmp.managers.MobScalingManager` | `feature.world.mobs_loot` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ModerationManager --> `component.hu.taliann.icesmp.managers.ModerationManager` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.NpcBindingManager --> `component.hu.taliann.icesmp.managers.NpcBindingManager` | `feature.world.travel_npc` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ParkourManager --> `component.hu.taliann.icesmp.managers.ParkourManager` | `feature.world.parkour_discovery` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.PartyManager --> `component.hu.taliann.icesmp.managers.PartyManager` | `feature.progression.party` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.PetManager --> `component.hu.taliann.icesmp.managers.PetManager` | `feature.progression.pets` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.PlayerCaravanManager --> `component.hu.taliann.icesmp.managers.PlayerCaravanManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ProfessionManager --> `component.hu.taliann.icesmp.managers.ProfessionManager` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ProfessionRecipeCatalog --> `component.hu.taliann.icesmp.managers.ProfessionRecipeCatalog` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ProfessionRecipeManager --> `component.hu.taliann.icesmp.managers.ProfessionRecipeManager` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager --> `component.hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.QuestManager --> `component.hu.taliann.icesmp.managers.QuestManager` | `feature.progression.quests_daily` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.RaidManager --> `component.hu.taliann.icesmp.managers.RaidManager` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.RelicCooldownService --> `component.hu.taliann.icesmp.managers.RelicCooldownService` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.RelicManager --> `component.hu.taliann.icesmp.managers.RelicManager` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ReportManager --> `component.hu.taliann.icesmp.managers.ReportManager` | `feature.admin.reports_messaging` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ResourceBonusService --> `component.hu.taliann.icesmp.managers.ResourceBonusService` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ResourceManager --> `component.hu.taliann.icesmp.managers.ResourceManager` | `feature.progression.professions` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.RespecService --> `component.hu.taliann.icesmp.managers.RespecService` | `feature.progression.talents_skills` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.RitualManager --> `component.hu.taliann.icesmp.managers.RitualManager` | `feature.world.rituals` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonFinaleManager --> `component.hu.taliann.icesmp.managers.SeasonFinaleManager` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonManager --> `component.hu.taliann.icesmp.managers.SeasonManager` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonMonumentManager --> `component.hu.taliann.icesmp.managers.SeasonMonumentManager` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonRewardStateData --> `component.hu.taliann.icesmp.managers.SeasonRewardStateData` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonStoryTeller --> `component.hu.taliann.icesmp.managers.SeasonStoryTeller` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonalModifierService --> `component.hu.taliann.icesmp.managers.SeasonalModifierService` | `feature.world.seasons_ambient` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ServerChallengeManager --> `component.hu.taliann.icesmp.managers.ServerChallengeManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ShopManager --> `component.hu.taliann.icesmp.managers.ShopManager` | `feature.economy.market_shops` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SinManager --> `component.hu.taliann.icesmp.managers.SinManager` | `feature.world.corruption_sin` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SitManager --> `component.hu.taliann.icesmp.managers.SitManager` | `feature.world.sit_only` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SitState --> `component.hu.taliann.icesmp.managers.SitState` | `feature.world.sit_only` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SoulShardManager --> `component.hu.taliann.icesmp.managers.SoulShardManager` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SoulforgeManager --> `component.hu.taliann.icesmp.managers.SoulforgeManager` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SpecializationManager --> `component.hu.taliann.icesmp.managers.SpecializationManager` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SpellFavoritesManager --> `component.hu.taliann.icesmp.managers.SpellFavoritesManager` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SpellMasteryManager --> `component.hu.taliann.icesmp.managers.SpellMasteryManager` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SpellRegistry --> `component.hu.taliann.icesmp.managers.SpellRegistry` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SpyManager --> `component.hu.taliann.icesmp.managers.SpyManager` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.StatsManager --> `component.hu.taliann.icesmp.managers.StatsManager` | `feature.progression.achievements_stats` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.StrangerNpcManager --> `component.hu.taliann.icesmp.managers.StrangerNpcManager` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TablistManager --> `component.hu.taliann.icesmp.managers.TablistManager` | `feature.player.hud_tablist` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TablistOrdering --> `component.hu.taliann.icesmp.managers.TablistOrdering` | `feature.player.hud_tablist` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TalentManager --> `component.hu.taliann.icesmp.managers.TalentManager` | `feature.progression.talents_skills` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TerritoryManager --> `component.hu.taliann.icesmp.managers.TerritoryManager` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TerritoryProtectionService --> `component.hu.taliann.icesmp.managers.TerritoryProtectionService` | `feature.factions.land_claims` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TotemManager --> `component.hu.taliann.icesmp.managers.TotemManager` | `feature.world.rituals` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TreasureEventManager --> `component.hu.taliann.icesmp.managers.TreasureEventManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.VanishManager --> `component.hu.taliann.icesmp.managers.VanishManager` | `feature.admin.vanish` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.WarWindowManager --> `component.hu.taliann.icesmp.managers.WarWindowManager` | `feature.factions.conflict_espionage` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.WhisperManager --> `component.hu.taliann.icesmp.managers.WhisperManager` | `feature.admin.reports_messaging` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.WildHuntManager --> `component.hu.taliann.icesmp.managers.WildHuntManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.WorldBossManager --> `component.hu.taliann.icesmp.managers.WorldBossManager` | `feature.world.events` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.EntityTaskSubmission --> `component.hu.taliann.icesmp.moderation.EntityTaskSubmission` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.InventoryEscrowGate --> `component.hu.taliann.icesmp.moderation.InventoryEscrowGate` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.InventoryEscrowQueue --> `component.hu.taliann.icesmp.moderation.InventoryEscrowQueue` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.InventoryTransferBarrier --> `component.hu.taliann.icesmp.moderation.InventoryTransferBarrier` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.InventoryWriteRecovery --> `component.hu.taliann.icesmp.moderation.InventoryWriteRecovery` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.InvseeEscrowSchema --> `component.hu.taliann.icesmp.moderation.InvseeEscrowSchema` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.LastKnownLocation --> `component.hu.taliann.icesmp.moderation.LastKnownLocation` | `feature.admin.inventory_teleport` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.ModerationDuration --> `component.hu.taliann.icesmp.moderation.ModerationDuration` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.ModerationMutationGate --> `component.hu.taliann.icesmp.moderation.ModerationMutationGate` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.ModerationSpamGuard --> `component.hu.taliann.icesmp.moderation.ModerationSpamGuard` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.ModerationTextFilter --> `component.hu.taliann.icesmp.moderation.ModerationTextFilter` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.PaperEntityTaskSubmission --> `component.hu.taliann.icesmp.moderation.PaperEntityTaskSubmission` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.PunishmentLedger --> `component.hu.taliann.icesmp.moderation.PunishmentLedger` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.PunishmentRecord --> `component.hu.taliann.icesmp.moderation.PunishmentRecord` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.PunishmentState --> `component.hu.taliann.icesmp.moderation.PunishmentState` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.PunishmentType --> `component.hu.taliann.icesmp.moderation.PunishmentType` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.ReplyPartnerRegistry --> `component.hu.taliann.icesmp.moderation.ReplyPartnerRegistry` | `feature.admin.reports_messaging` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.SchedulerCallbackGate --> `component.hu.taliann.icesmp.moderation.SchedulerCallbackGate` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.StrictYamlNumber --> `component.hu.taliann.icesmp.moderation.StrictYamlNumber` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.TaskLease --> `component.hu.taliann.icesmp.moderation.TaskLease` | `feature.admin.moderation` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.motd.MotdGenerationGate --> `component.hu.taliann.icesmp.motd.MotdGenerationGate` | `feature.player.motd` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.motd.MotdIconValidator --> `component.hu.taliann.icesmp.motd.MotdIconValidator` | `feature.player.motd` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.motd.MotdSelector --> `component.hu.taliann.icesmp.motd.MotdSelector` | `feature.player.motd` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.RelicDefinition --> `component.hu.taliann.icesmp.relics.RelicDefinition` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.RelicOwnership --> `component.hu.taliann.icesmp.relics.RelicOwnership` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.RelicRegistry --> `component.hu.taliann.icesmp.relics.RelicRegistry` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.RelicTrigger --> `component.hu.taliann.icesmp.relics.RelicTrigger` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.RelicTriggerConfig --> `component.hu.taliann.icesmp.relics.RelicTriggerConfig` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.SimpleRelicDefinition --> `component.hu.taliann.icesmp.relics.SimpleRelicDefinition` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.ability.RelicAbility --> `component.hu.taliann.icesmp.relics.ability.RelicAbility` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.ability.RelicAbilityContext --> `component.hu.taliann.icesmp.relics.ability.RelicAbilityContext` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.ability.RelicAbilityRegistry --> `component.hu.taliann.icesmp.relics.ability.RelicAbilityRegistry` | `feature.progression.relics_souls` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.session.PlayerStateCleanup --> `component.hu.taliann.icesmp.session.PlayerStateCleanup` | `feature.core.lifecycle` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.sit.SitGeometry --> `component.hu.taliann.icesmp.sit.SitGeometry` | `feature.world.sit_only` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.sit.SitPolicy --> `component.hu.taliann.icesmp.sit.SitPolicy` | `feature.world.sit_only` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.AngryChickenSpell --> `component.hu.taliann.icesmp.spells.AngryChickenSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.AntidoteSpell --> `component.hu.taliann.icesmp.spells.AntidoteSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ArmamentSpell --> `component.hu.taliann.icesmp.spells.ArmamentSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.BaseSpell --> `component.hu.taliann.icesmp.spells.BaseSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.BeeSwarmSpell --> `component.hu.taliann.icesmp.spells.BeeSwarmSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.BlinkSpell --> `component.hu.taliann.icesmp.spells.BlinkSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.BoneChillSpell --> `component.hu.taliann.icesmp.spells.BoneChillSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.BulwarkSpell --> `component.hu.taliann.icesmp.spells.BulwarkSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ChainsOfIceSpell --> `component.hu.taliann.icesmp.spells.ChainsOfIceSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ConfiguredSpell --> `component.hu.taliann.icesmp.spells.ConfiguredSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ConfusionSpell --> `component.hu.taliann.icesmp.spells.ConfusionSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.DeepBreathSpell --> `component.hu.taliann.icesmp.spells.DeepBreathSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.DemonicCircleSpell --> `component.hu.taliann.icesmp.spells.DemonicCircleSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.DevotionAuraSpell --> `component.hu.taliann.icesmp.spells.DevotionAuraSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.DoubleJumpSpell --> `component.hu.taliann.icesmp.spells.DoubleJumpSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.DruidFormSpell --> `component.hu.taliann.icesmp.spells.DruidFormSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.EagleEyeSpell --> `component.hu.taliann.icesmp.spells.EagleEyeSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ExpelHarmSpell --> `component.hu.taliann.icesmp.spells.ExpelHarmSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.FeastSpell --> `component.hu.taliann.icesmp.spells.FeastSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.FeatherfootSpell --> `component.hu.taliann.icesmp.spells.FeatherfootSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.FlyingSerpentKickSpell --> `component.hu.taliann.icesmp.spells.FlyingSerpentKickSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.FriendshipSpell --> `component.hu.taliann.icesmp.spells.FriendshipSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.FrostFeverSpell --> `component.hu.taliann.icesmp.spells.FrostFeverSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.GlaiveThrowSpell --> `component.hu.taliann.icesmp.spells.GlaiveThrowSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.GustSpell --> `component.hu.taliann.icesmp.spells.GustSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.HideSpell --> `component.hu.taliann.icesmp.spells.HideSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.HolyWrathSpell --> `component.hu.taliann.icesmp.spells.HolyWrathSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.InnerFocusSpell --> `component.hu.taliann.icesmp.spells.InnerFocusSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.LifeDrainSpell --> `component.hu.taliann.icesmp.spells.LifeDrainSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.LivingFlameSpell --> `component.hu.taliann.icesmp.spells.LivingFlameSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.LuckyStarSpell --> `component.hu.taliann.icesmp.spells.LuckyStarSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.MindBlastSpell --> `component.hu.taliann.icesmp.spells.MindBlastSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.MultishotSpell --> `component.hu.taliann.icesmp.spells.MultishotSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.PrimalBondSpell --> `component.hu.taliann.icesmp.spells.PrimalBondSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ProjectileBurstSpell --> `component.hu.taliann.icesmp.spells.ProjectileBurstSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.RainDanceSpell --> `component.hu.taliann.icesmp.spells.RainDanceSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.RootSpell --> `component.hu.taliann.icesmp.spells.RootSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.RuneStrikeSpell --> `component.hu.taliann.icesmp.spells.RuneStrikeSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ShadowburnSpell --> `component.hu.taliann.icesmp.spells.ShadowburnSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ShadowstepSpell --> `component.hu.taliann.icesmp.spells.ShadowstepSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ShamanTotemSpell --> `component.hu.taliann.icesmp.spells.ShamanTotemSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SmokeBombSpell --> `component.hu.taliann.icesmp.spells.SmokeBombSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SoulExchangeSpell --> `component.hu.taliann.icesmp.spells.SoulExchangeSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SpectralSightSpell --> `component.hu.taliann.icesmp.spells.SpectralSightSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.Spell --> `component.hu.taliann.icesmp.spells.Spell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SpellCatalog --> `component.hu.taliann.icesmp.spells.SpellCatalog` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SpellCostType --> `component.hu.taliann.icesmp.spells.SpellCostType` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SpellTargetingUtil --> `component.hu.taliann.icesmp.spells.SpellTargetingUtil` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SpinningCraneKickSpell --> `component.hu.taliann.icesmp.spells.SpinningCraneKickSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SummonMinionsSpell --> `component.hu.taliann.icesmp.spells.SummonMinionsSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SunDanceSpell --> `component.hu.taliann.icesmp.spells.SunDanceSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.VenomStrikeSpell --> `component.hu.taliann.icesmp.spells.VenomStrikeSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.WhirlwindSpell --> `component.hu.taliann.icesmp.spells.WhirlwindSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.WildMushroomSpell --> `component.hu.taliann.icesmp.spells.WildMushroomSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.WisplightSpell --> `component.hu.taliann.icesmp.spells.WisplightSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.WolfCallSpell --> `component.hu.taliann.icesmp.spells.WolfCallSpell` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.BlockRegenJournal --> `component.hu.taliann.icesmp.storage.BlockRegenJournal` | `feature.core.persistence` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.CorruptStateFileError --> `component.hu.taliann.icesmp.storage.CorruptStateFileError` | `feature.core.persistence` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.CriticalPersistenceWriteError --> `component.hu.taliann.icesmp.storage.CriticalPersistenceWriteError` | `feature.core.persistence` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.PersistentStore --> `component.hu.taliann.icesmp.storage.PersistentStore` | `feature.core.persistence` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.PersistentStoreCoordinator --> `component.hu.taliann.icesmp.storage.PersistentStoreCoordinator` | `feature.core.persistence` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.TransactionJournal --> `component.hu.taliann.icesmp.storage.TransactionJournal` | `feature.core.persistence` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.YamlStore --> `component.hu.taliann.icesmp.storage.YamlStore` | `feature.core.persistence` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.CcDiminish --> `component.hu.taliann.icesmp.utils.CcDiminish` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.DailyBudget --> `component.hu.taliann.icesmp.utils.DailyBudget` | `feature.economy.rewards_bounty` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.DisplayFxUtil --> `component.hu.taliann.icesmp.utils.DisplayFxUtil` | `feature.player.chat_feedback` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.ExperienceUtil --> `component.hu.taliann.icesmp.utils.ExperienceUtil` | `feature.progression.classes_specializations` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.GameModeCache --> `component.hu.taliann.icesmp.utils.GameModeCache` | `feature.core.shared_services` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.ItemProvenance --> `component.hu.taliann.icesmp.utils.ItemProvenance` | `feature.progression.items_rarity` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.LocalAnnounce --> `component.hu.taliann.icesmp.utils.LocalAnnounce` | `feature.player.chat_feedback` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.MessageManager --> `component.hu.taliann.icesmp.utils.MessageManager` | `feature.core.configuration` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.MobKillUtil --> `component.hu.taliann.icesmp.utils.MobKillUtil` | `feature.world.combat_rules` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.ParticleUtil --> `component.hu.taliann.icesmp.utils.ParticleUtil` | `feature.core.shared_services` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.PartyRewardResolver --> `component.hu.taliann.icesmp.utils.PartyRewardResolver` | `feature.progression.party` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.PeriodicChanceEvent --> `component.hu.taliann.icesmp.utils.PeriodicChanceEvent` | `feature.core.shared_services` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.PlainIngredients --> `component.hu.taliann.icesmp.utils.PlainIngredients` | `feature.progression.crafting_blueprints` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.PositionCache --> `component.hu.taliann.icesmp.utils.PositionCache` | `feature.core.shared_services` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.SpellDamageUtil --> `component.hu.taliann.icesmp.utils.SpellDamageUtil` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.SpellVfx --> `component.hu.taliann.icesmp.utils.SpellVfx` | `feature.progression.spells` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.TabCompleteUtil --> `component.hu.taliann.icesmp.utils.TabCompleteUtil` | `feature.core.shared_services` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.TextAnimator --> `component.hu.taliann.icesmp.utils.TextAnimator` | `feature.player.chat_feedback` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.TextUtil --> `component.hu.taliann.icesmp.utils.TextUtil` | `feature.player.chat_feedback` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.ToastUtil --> `component.hu.taliann.icesmp.utils.ToastUtil` | `feature.player.chat_feedback` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.TransientEntities --> `component.hu.taliann.icesmp.utils.TransientEntities` | `feature.core.shared_services` |
| <!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.UndeadUtil --> `component.hu.taliann.icesmp.utils.UndeadUtil` | `feature.world.mobs_loot` |
<!-- END GENERATED COMPONENT MANIFEST MARKERS -->
