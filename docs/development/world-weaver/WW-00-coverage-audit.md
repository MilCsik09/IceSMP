# IceSMP — WorldWeaver WW-00 capability coverage audit

**INTERNAL DEVELOPER ONLY — do not mirror to IceSMPGuides or public guides.**

**Status: WW-00 source inventory; WorldWeaver runtime is not implemented.**

This is an implementation input, not a production acceptance report. `DEFERRED_BLOCKER` denotes an actual unresolved WorldWeaver capability, not a claim that the baseline gameplay subsystem is defective.

## Exact source authority

- Repository: `MilCsik09/IceSMP`.
- Base PR: #152, open/draft/unmerged; no newer open cumulative PR found on 2026-09-06.
- Base branch: `feature/trash-production-hardening`.
- Base commit: `a335b3b5acaea66772534e51527a1c9233a85d1a`.
- Base tree: `99cdf8036c9b4b0fa322e7c8f867e29a2e96e24f`.
- Work branch: `feature/world-weaver-ww00-coverage`.
- Normative v2: [design-v2.md](design-v2.md), read in full before this audit.
- Original attachment SHA-256: `fd636ab41e0defe6ae48ac41320b275fccc151fcee2d751df2f9eaa9eb98061b`.
- Repository design copy strips only trailing whitespace from six metadata-header Markdown hard breaks; normative wording is unchanged. Both digests are retained in coverage.json.
- No master/staging merge or unrelated sibling-branch merge was performed.

Open stack checked: #140 → #141 → #142 → #143 → #144 → #146 → #147 → #148 → #149 → #150 → #151 → #152. #145 is a messaging sibling, not the cumulative head. The separate faction rework branch has no newer open cumulative PR and was not silently merged into this task.

## Inventory boundary and result

- 1,009 main Java files at the base.
- 262 source-pinned authority/helper entries: the 246 Manager/Service/Registry/Runtime/Coordinator/Authority/Catalog/Policy/Store candidates plus 16 explicitly reviewed non-suffix seams.
- 414 distinct project components constructed/referenced across IceSMP, IceSMPBootstrap, IceSMPCore and PrologueRuntime have bootstrap role/domain assignments.
- 55 domain decisions; 51 current `DEFERRED_BLOCKER`; 4 `NO_RUNTIME_SURFACE`; 0 implemented providers.
- The retained procedural-daily history has an inspect-only target rationale, but remains a blocker until its WorldWeaver inspection adapter exists.
- Existing vanilla biome/PvE affinity/quest biome visits and existing Prologue/Doom gate runtime are classified separately from a future custom biome subsystem.

The reproducible machine inventory is [coverage.json](coverage.json). It records exact source hashes, public method names, domain routes, target provider, phase and rationale. The method list is a lexical navigation aid, not a Java call-graph proof or a list of methods safe to expose verbatim. A source hash change requires re-audit; a new suffix authority or changed bootstrap component set fails the checker. Static checks cannot prove that an arbitrarily named hidden authority outside these entry points does not exist; package and bootstrap review supplements the mechanical boundary.

```bash
python3 scripts/audit_world_weaver_coverage.py
# Must FAIL until all implementation blockers are actually closed:
python3 scripts/audit_world_weaver_coverage.py --release
```

The source checker is WW-00 tooling. It is not the required runtime `WorldWeaverCoverageRegressionSuite`, nor Dynamic Acceptance Test A/B.

## Domain coverage matrix

| Domain | Current status | Target adapter | Phase |
|---|---|---|---|
| `minecraft` | `DEFERRED_BLOCKER` | `MinecraftWeaverProvider` | WW-02 |
| `developer_self` | `DEFERRED_BLOCKER` | `DeveloperSelfWeaverProvider` | WW-01 |
| `pve` | `DEFERRED_BLOCKER` | `PvEWeaverProvider` | WW-04 |
| `faction` | `DEFERRED_BLOCKER` | `FactionWeaverProvider` | WW-04 |
| `trash` | `DEFERRED_BLOCKER` | `TrashWeaverProvider` | WW-05 |
| `archaeology_knowledge` | `DEFERRED_BLOCKER` | `KnowledgeWeaverProvider` | WW-06 |
| `territory` | `DEFERRED_BLOCKER` | `TerritoryWeaverProvider` | WW-05 |
| `claims` | `DEFERRED_BLOCKER` | `ClaimWeaverProvider` | WW-09 |
| `itemization` | `DEFERRED_BLOCKER` | `ItemizationWeaverProvider` | WW-06 |
| `class` | `DEFERRED_BLOCKER` | `ClassWeaverProvider` | WW-09 |
| `profession` | `DEFERRED_BLOCKER` | `ProfessionWeaverProvider` | WW-09 |
| `economy` | `DEFERRED_BLOCKER` | `EconomyWeaverProvider` | WW-09 |
| `market` | `DEFERRED_BLOCKER` | `EconomyWeaverProvider` | WW-09 |
| `crate` | `DEFERRED_BLOCKER` | `CrateWeaverProvider` | WW-09 |
| `pets` | `DEFERRED_BLOCKER` | `PetWeaverProvider` | WW-09 |
| `minions_totems` | `DEFERRED_BLOCKER` | `SummonWeaverProvider` | WW-09 |
| `relics` | `DEFERRED_BLOCKER` | `RelicWeaverProvider` | WW-09 |
| `soulforge_ritual` | `DEFERRED_BLOCKER` | `ProgressionWeaverProvider` | WW-09 |
| `knowledge` | `DEFERRED_BLOCKER` | `KnowledgeWeaverProvider` | WW-06 |
| `legacy_daily` | `DEFERRED_BLOCKER` | `KnowledgeWeaverProvider` | WW-06 |
| `goals` | `DEFERRED_BLOCKER` | `ProgressionWeaverProvider` | WW-09 |
| `guild_party` | `DEFERRED_BLOCKER` | `SocialWeaverProvider` | WW-09 |
| `politics` | `DEFERRED_BLOCKER` | `PoliticsWeaverProvider` | WW-09 |
| `crime_whisper` | `DEFERRED_BLOCKER` | `CrimeWeaverProvider` | WW-09 |
| `war_raid_duel` | `DEFERRED_BLOCKER` | `ConflictWeaverProvider` | WW-09 |
| `season` | `DEFERRED_BLOCKER` | `SeasonWeaverProvider` | WW-09 |
| `prologue_doom` | `DEFERRED_BLOCKER` | `PrologueWeaverProvider` | WW-09 |
| `parkour` | `DEFERRED_BLOCKER` | `ParkourWeaverProvider` | WW-09 |
| `moderation` | `DEFERRED_BLOCKER` | `ModerationWeaverProvider` | WW-09 |
| `player_runtime` | `DEFERRED_BLOCKER` | `PlayerRuntimeWeaverProvider` | WW-09 |
| `presentation` | `DEFERRED_BLOCKER` | `PresentationWeaverProvider` | WW-09 |
| `configuration` | `DEFERRED_BLOCKER` | `ConfigurationWeaverProvider` | WW-09 |
| `event_support` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.blood_moon` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.world_boss` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.invasion` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.caravan` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.ambient` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.gathering` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.treasure` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.wild_hunt` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.abundance` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.server_challenge` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.escort` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.meteor` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.stranger` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.corruption` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.archeology` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.cultists` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.economy` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `event.player_caravan` | `DEFERRED_BLOCKER` | `EventWeaverProvider` | WW-06 |
| `profile_infrastructure` | `NO_RUNTIME_SURFACE` | `owned infrastructure / absent subsystem` | WW-00 |
| `storage_infrastructure` | `NO_RUNTIME_SURFACE` | `owned infrastructure / absent subsystem` | WW-00 |
| `transport_infrastructure` | `NO_RUNTIME_SURFACE` | `owned infrastructure / absent subsystem` | WW-00 |
| `future_biome` | `NO_RUNTIME_SURFACE` | `owned infrastructure / absent subsystem` | WW-00 |

## Provider scope, existing routes and required seams

### minecraft

- Inspect: PLAYER/ENTITY/BLOCK/LOCATION/WORLD owner-thread snapshot; protected entity classification.
- Actions to implement: heal, nonlethal damage, kill, effects, freeze/fire, velocity, teleport, AI, invulnerability, gamemode/flight, target, remove, block data, effects, time/weather, sandbox stack.
- Domain route: Bukkit owner scheduler; teleportAsync; manager-owned entities excluded from generic remove.
- Coverage rationale / current gap: No WorldWeaver SubjectRef/snapshot/access router or vanilla adapter exists. World influence and block/spatial reward provenance must precede mutation.

### developer_self

- Inspect: authoritative instance, behavior state, sessions, receipts, diagnostics.
- Actions to implement: recover, clear session, arming, vanish, Thread/Imprint/Binding/Fork controls.
- Domain route: Generalize existing DevItemManager; preserve Bingulus pending/pity; HiddenDevAuthority; ModerationManager + VanishManager.
- Coverage rationale / current gap: Current DevItemManager is Bingulus-specific and owner-configurable. Its owner UUID differs from PRIMARY_DEVELOPER. It must not become the WorldWeaver authority. Issuance writes synchronously today.
- Source: [DevItemManager](../../../src/main/java/hu/taliann/icesmp/managers/DevItemManager.java), [HiddenDevAuthority](../../../src/main/java/hu/taliann/icesmp/security/HiddenDevAuthority.java), [DevItemFactory](../../../src/main/java/hu/taliann/icesmp/items/DevItemFactory.java).

### pve

- Inspect: canonical/effective template/rank/archetype/ability/level/affix/behavior/cast/source.
- Actions to implement: force_ability, add/remove ability, rank/archetype/template projection, refresh, export/import, fork.
- Domain route: MobAbilityRuntime.triggerTechnique/startCast; canonical registry enumeration; separate MobRuntimeProjectionSource consumer.
- Coverage rationale / current gap: No effective projection port. forceTemplate/forceRankedLevel write canonical combat identity and are unsuitable for projections; reward and Bestiary must retain canonical identity. triggerTechnique is constrained to the current kit/typed trigger.
- Source: [MobScalingManager](../../../src/main/java/hu/taliann/icesmp/managers/MobScalingManager.java), [MobAbilityRegistry](../../../src/main/java/hu/taliann/icesmp/pve/MobAbilityRegistry.java), [MobAbilityRuntime](../../../src/main/java/hu/taliann/icesmp/pve/MobAbilityRuntime.java), [MobTemplateRegistry](../../../src/main/java/hu/taliann/icesmp/pve/MobTemplateRegistry.java), [CreatureSpeciesRegistry](../../../src/main/java/hu/taliann/icesmp/pve/CreatureSpeciesRegistry.java), [CreatureProfileService](../../../src/main/java/hu/taliann/icesmp/pve/CreatureProfileService.java), [AuthoredCreatureSpawnService](../../../src/main/java/hu/taliann/icesmp/pve/AuthoredCreatureSpawnService.java), [EncounterRewardDeliveryService](../../../src/main/java/hu/taliann/icesmp/pve/EncounterRewardDeliveryService.java), [EquippedCombatPowerService](../../../src/main/java/hu/taliann/icesmp/pve/EquippedCombatPowerService.java).

### faction

- Inspect: membership, passive settings, semantic context, targeting trace.
- Actions to implement: project faction, contexts, scripted target/peace through existing policy, canonical membership.
- Domain route: FactionManager + PlayerProfileFactionStore conditional transaction; FactionPassiveListener/FactionMobContextResolver effective consumers.
- Coverage rationale / current gap: FactionManager.setFaction and switchFactionDurably block via join. Add asynchronous expected-revision entry at the same domain boundary, preserving guild, history and hooks. Never project CROWN_CURSE.
- Source: [FactionManager](../../../src/main/java/hu/taliann/icesmp/managers/FactionManager.java), [FactionRelationManager](../../../src/main/java/hu/taliann/icesmp/managers/FactionRelationManager.java), [FactionPassiveService](../../../src/main/java/hu/taliann/icesmp/factions/FactionPassiveService.java), [FactionMobContextResolver](../../../src/main/java/hu/taliann/icesmp/factions/FactionMobContextResolver.java), [FactionPassiveConfig](../../../src/main/java/hu/taliann/icesmp/factions/FactionPassiveConfig.java).

### trash

- Inspect: hidden identity/kind/behavior/phase/instance/revision/history/provenance/memory/rule fields.
- Actions to implement: individualize, activate, transition, repair, sandbox copy, four rule field kinds, export/import, fork.
- Domain route: TrashHistoryService individualize/transform/repair; extract TrashAnomalyActivationService/TrashRelicActivationService/TrashRuleFieldService from existing runtime.
- Coverage rationale / current gap: Anomaly and relic actions are currently event-handler/private-runtime routes. Provider must share extracted canonical activation APIs; fake events and natural-history spoofing prohibited.
- Source: [TrashCatalog](../../../src/main/java/hu/taliann/icesmp/trash/TrashCatalog.java), [TrashHistoryStore](../../../src/main/java/hu/taliann/icesmp/trash/TrashHistoryStore.java), [TrashHistoryService](../../../src/main/java/hu/taliann/icesmp/trash/TrashHistoryService.java), [TrashAnomalyRuntime](../../../src/main/java/hu/taliann/icesmp/trash/TrashAnomalyRuntime.java), [TrashRelicRuntime](../../../src/main/java/hu/taliann/icesmp/trash/TrashRelicRuntime.java), [TrashAnomalyStateStore](../../../src/main/java/hu/taliann/icesmp/trash/TrashAnomalyStateStore.java), [TrashSpatialFractureStore](../../../src/main/java/hu/taliann/icesmp/trash/TrashSpatialFractureStore.java), [TrashVendorService](../../../src/main/java/hu/taliann/icesmp/trash/TrashVendorService.java), [TrashLootService](../../../src/main/java/hu/taliann/icesmp/trash/TrashLootService.java), [TrashAmbientManager](../../../src/main/java/hu/taliann/icesmp/trash/TrashAmbientManager.java), [TossableObjectRuntime](../../../src/main/java/hu/taliann/icesmp/trash/TossableObjectRuntime.java), [TrashRecyclePool](../../../src/main/java/hu/taliann/icesmp/trash/TrashRecyclePool.java), [TrashItemFactory](../../../src/main/java/hu/taliann/icesmp/trash/TrashItemFactory.java), [TrashContextResolver](../../../src/main/java/hu/taliann/icesmp/trash/TrashContextResolver.java), [TrashLootSelector](../../../src/main/java/hu/taliann/icesmp/trash/TrashLootSelector.java), [TrashRuntimeTelemetry](../../../src/main/java/hu/taliann/icesmp/trash/TrashRuntimeTelemetry.java).

### archaeology_knowledge

- Inspect: familiarity/breadth/insight/revision-aware derived facts.
- Actions to implement: developer unlock/level/insight/reset using existing domain service with expected revision.
- Domain route: TrashArchaeologyService.inspect/unlock/setLevel/addInsight/reset -> Profile CAS.
- Coverage rationale / current gap: Existing hidden mutation route has no WorldWeaver receipt/revision fence. DEV_PROTOTYPED provenance must remain excluded from natural facts and progression.
- Source: [TrashArchaeologyService](../../../src/main/java/hu/taliann/icesmp/trash/TrashArchaeologyService.java), [TrashArchaeologyProfileStore](../../../src/main/java/hu/taliann/icesmp/trash/TrashArchaeologyProfileStore.java), [TrashArchaeologyFactEngine](../../../src/main/java/hu/taliann/icesmp/trash/TrashArchaeologyFactEngine.java).

### territory

- Inspect: territory, shape, protection decision trace, regeneration pending blocks.
- Actions to implement: DENY sandbox overlay; ALLOW LIVE_GM; canonical rename/type/owner/Y bounds; overlay fork.
- Domain route: TerritoryProtectionService common pure evaluator; TerritoryManager conditional copy-on-write transaction.
- Coverage rationale / current gap: Current rename/type/owner/Y setters publish map/index before save. Need durable candidate commit before publication and multi-territory fingerprint for capital demotion; no generic remove/reshape.
- Source: [TerritoryManager](../../../src/main/java/hu/taliann/icesmp/managers/TerritoryManager.java), [TerritoryProtectionService](../../../src/main/java/hu/taliann/icesmp/managers/TerritoryProtectionService.java), [BlockRegenService](../../../src/main/java/hu/taliann/icesmp/managers/BlockRegenService.java).

### claims

- Inspect: claims, trusted players, dimensions/footprint, build decision.
- Actions to implement: bounded trust/untrust/unclaim and claim operations via manager conditional API.
- Domain route: ClaimManager claimSelection/claimPolygon/extendClaim/trust/untrust/adminUnclaimAt.
- Coverage rationale / current gap: Currency and overlapping territory protection participate in claim changes. Existing player-oriented calls require expected footprint/revision and durable observed-state recovery, not raw map editing.
- Source: [ClaimManager](../../../src/main/java/hu/taliann/icesmp/managers/ClaimManager.java).

### itemization

- Inspect: UUID/template/roll/rune/ascension/signature/origin/revision/history/suppression.
- Actions to implement: prototype clone/reroll/ascend/runes; canonical reroll/ascend/runes/signature with explicit safe domain route; presentation refresh.
- Domain route: ItemMutationCoordinator and ItemMutationJournal; ItemMutationService; ItemIdentityService.
- Coverage rationale / current gap: Existing coordinator owns physical slot, cost, recovery and render. Need expected-slot/fingerprint/revision developer entry without reward receipt spoofing; prototype exclusion across all item consumers.
- Source: [ItemTemplateRegistry](../../../src/main/java/hu/taliann/icesmp/itemization/ItemTemplateRegistry.java), [ItemIdentityService](../../../src/main/java/hu/taliann/icesmp/itemization/ItemIdentityService.java), [ItemMutationCoordinator](../../../src/main/java/hu/taliann/icesmp/itemization/ItemMutationCoordinator.java), [ItemMutationService](../../../src/main/java/hu/taliann/icesmp/itemization/ItemMutationService.java), [ItemTransformationPolicy](../../../src/main/java/hu/taliann/icesmp/itemization/ItemTransformationPolicy.java), [EquipmentProficiencyService](../../../src/main/java/hu/taliann/icesmp/itemization/EquipmentProficiencyService.java), [ItemRarityService](../../../src/main/java/hu/taliann/icesmp/managers/ItemRarityService.java), [CursedGearService](../../../src/main/java/hu/taliann/icesmp/managers/CursedGearService.java).

### class

- Inspect: class/spec/doctrine/talent/resources/cooldowns/mastery/spell grants and gate trace.
- Actions to implement: conditional class/spec/doctrine/talent/xp/grants; bounded runtime resource/cooldown adjustments; favorites.
- Domain route: ClassSpecProfileGateway typed asynchronous requests and per-class GameplayService APIs; talent/cooldown stores remain domain-owned.
- Coverage rationale / current gap: Profile CAS alone does not provide WorldWeaver expected revision/observed recovery. Runtime resources and cooldown setters require bounded semantic domain ports and player quarantine; never reset private spell state maps.
- Source: [JobManager](../../../src/main/java/hu/taliann/icesmp/managers/JobManager.java), [SpecializationManager](../../../src/main/java/hu/taliann/icesmp/managers/SpecializationManager.java), [TalentManager](../../../src/main/java/hu/taliann/icesmp/managers/TalentManager.java), [ResourceManager](../../../src/main/java/hu/taliann/icesmp/managers/ResourceManager.java), [SpellRegistry](../../../src/main/java/hu/taliann/icesmp/managers/SpellRegistry.java), [SpellMasteryManager](../../../src/main/java/hu/taliann/icesmp/managers/SpellMasteryManager.java), [SpellFavoritesManager](../../../src/main/java/hu/taliann/icesmp/managers/SpellFavoritesManager.java), [ClassHealthService](../../../src/main/java/hu/taliann/icesmp/managers/ClassHealthService.java), [ResourceBonusService](../../../src/main/java/hu/taliann/icesmp/managers/ResourceBonusService.java), [RespecService](../../../src/main/java/hu/taliann/icesmp/managers/RespecService.java), [ClassSpecProfileGateway](../../../src/main/java/hu/taliann/icesmp/classspec/application/ClassSpecProfileGateway.java), [DefaultClassSpecProfileGateway](../../../src/main/java/hu/taliann/icesmp/classspec/application/DefaultClassSpecProfileGateway.java).

### profession

- Inspect: selected professions/XP/levels/recipes/blueprint availability/crafting quality.
- Actions to implement: conditional profession selection/clear/XP/learned recipe adjustments and blueprint prototype.
- Domain route: ProfessionManager select/set/clear/setXp/addXp/learnRecipe -> conditional PROFESSION section.
- Coverage rationale / current gap: Existing mutations are asynchronous but do not carry the WW snapshot expected revision. Gathering/crafting/fishing/weekly/class hooks must carry RewardContext before any progression.
- Source: [ProfessionManager](../../../src/main/java/hu/taliann/icesmp/managers/ProfessionManager.java), [ProfessionRecipeCatalog](../../../src/main/java/hu/taliann/icesmp/managers/ProfessionRecipeCatalog.java), [ProfessionRecipeManager](../../../src/main/java/hu/taliann/icesmp/managers/ProfessionRecipeManager.java), [CraftingRestrictionManager](../../../src/main/java/hu/taliann/icesmp/managers/CraftingRestrictionManager.java).

### economy

- Inspect: wallet/currency supply/exchange/shop/faucet receipt and pending state.
- Actions to implement: LIVE_GM conditional balance adjustment/compensation; sandbox currency visual prototype; safe board controls.
- Domain route: CurrencyManager durable wallet/creditOnce/apply/rollback; immutable receipt identities retained.
- Coverage rationale / current gap: Faucets and transfers share wallet API. Gate the originating faucet with context; denying every credit would strand previously legitimate settlement. Physical currency/prototype paths must be fenced.
- Source: [CurrencyManager](../../../src/main/java/hu/taliann/icesmp/managers/CurrencyManager.java), [ExchangeRateService](../../../src/main/java/hu/taliann/icesmp/managers/ExchangeRateService.java), [ShopManager](../../../src/main/java/hu/taliann/icesmp/managers/ShopManager.java), [BuyerService](../../../src/main/java/hu/taliann/icesmp/managers/BuyerService.java), [ExchangeBoardManager](../../../src/main/java/hu/taliann/icesmp/managers/ExchangeBoardManager.java), [MoneyPouchItemFactory](../../../src/main/java/hu/taliann/icesmp/items/MoneyPouchItemFactory.java).

### market

- Inspect: listings/auctions/settlement/delivery/donation escrow.
- Actions to implement: bounded cancellation/recovery and conditional listing/donation actions.
- Domain route: MarketManager + DonationChestManager canonical transaction/escrow routes.
- Coverage rationale / current gap: Other-player assets, bids and pending deliveries cannot be silently overwritten. Need conditional action contract, exact observed-state and compensation; no receipt/history erasure.
- Source: [MarketManager](../../../src/main/java/hu/taliann/icesmp/managers/MarketManager.java), [DonationChestManager](../../../src/main/java/hu/taliann/icesmp/managers/DonationChestManager.java).

### crate

- Inspect: crate definitions/locations/access/odds/cooldowns/keys/stats/pending recovery.
- Actions to implement: sandbox crate demonstration/prototype; armed canonical key/location/stats edits via manager.
- Domain route: CrateManager setCrateAsync/removeCrateAsync/buyKeyAsync/giveKeys/requestOpen/resetStatsAsync.
- Coverage rationale / current gap: SANDBOX must gate before consuming keys or reserving reward; legitimate pending rewards must not be deleted. Catalog must enumerate crateIds and definitions.
- Source: [CrateManager](../../../src/main/java/hu/taliann/icesmp/managers/CrateManager.java), [CrateKeyFactory](../../../src/main/java/hu/taliann/icesmp/items/CrateKeyFactory.java).

### pets

- Inspect: companion roster/selected id/XP/stance/armor/respawn/active entity/target.
- Actions to implement: conditional capture/summon/dismiss/select/release/rename/stance/armor/XP; runtime target.
- Domain route: PetManager V2 mutations via ClassSpecProfileGateway.mutateCompanion/mutateCompanionProgress.
- Coverage rationale / current gap: Pet owner and active entity have separate Folia owners. WW requests need expected profile and entity identity plus taint propagation into pet kills, spawned entities and owner XP.
- Source: [PetManager](../../../src/main/java/hu/taliann/icesmp/managers/PetManager.java).

### minions_totems

- Inspect: owned summons/stance/count/totem types.
- Actions to implement: bounded summon/stance/despawn/totem controls.
- Domain route: MinionManager canonical owner ledger; TotemManager placement and clearOwnerProjection; class spell APIs.
- Coverage rationale / current gap: No arbitrary entity clone. Parent influence must propagate to all minion/totem/projectile damage and secondary spawn sources.
- Source: [MinionManager](../../../src/main/java/hu/taliann/icesmp/managers/MinionManager.java), [TotemManager](../../../src/main/java/hu/taliann/icesmp/managers/TotemManager.java).

### relics

- Inspect: canonical owner/lost/cooldown/awakening/ability/world-state.
- Actions to implement: safe prototype; conditional issue/transfer/reclaim/awakening/cooldown through relic authority.
- Domain route: RelicManager lifecycle + ClassRelicService + RelicWorldStateStore.
- Coverage rationale / current gap: True relic limit/lost/ownership lifecycle must remain intact. DEV artifact is separate; no cloned owner/receipt/history. Explicit journaled domain commands needed for canonical mutation.
- Source: [RelicManager](../../../src/main/java/hu/taliann/icesmp/managers/RelicManager.java), [RelicCooldownService](../../../src/main/java/hu/taliann/icesmp/managers/RelicCooldownService.java), [MetelytepoManager](../../../src/main/java/hu/taliann/icesmp/managers/MetelytepoManager.java), [ClassRelicService](../../../src/main/java/hu/taliann/icesmp/classrelic/ClassRelicService.java), [RelicRegistry](../../../src/main/java/hu/taliann/icesmp/relics/RelicRegistry.java), [RelicWorldStateStore](../../../src/main/java/hu/taliann/icesmp/relics/RelicWorldStateStore.java), [RelicAbilityRegistry](../../../src/main/java/hu/taliann/icesmp/relics/ability/RelicAbilityRegistry.java).

### soulforge_ritual

- Inspect: shards/soulforge ranks/ritual/pact eligibility.
- Actions to implement: conditional shards/soulforge rank and explicit ritual/pact actions.
- Domain route: ClassSpecProfileGateway.mutateSoulShards/incrementSoulforge; RitualManager.tryRitual.
- Coverage rationale / current gap: Cost and progression/state transitions must not be simulated via inventory/PDC. Multi-owner summon consequences require influence before effects.
- Source: [SoulShardManager](../../../src/main/java/hu/taliann/icesmp/managers/SoulShardManager.java), [SoulforgeManager](../../../src/main/java/hu/taliann/icesmp/managers/SoulforgeManager.java), [RitualManager](../../../src/main/java/hu/taliann/icesmp/managers/RitualManager.java).

### knowledge

- Inspect: quest active/completed/tracked/progress/source gates; Bestiary/achievement/discovery/statistics.
- Actions to implement: track/abandon; conditional accept/reset/complete-without-reward/discover/achievement/statistics actions after safe domain ports.
- Domain route: QuestManager + PlayerProfileQuestStore; BestiaryManager + PlayerProfileAchievementStore; StatsManager; AdvancementService.
- Coverage rationale / current gap: Bestiary.record triggers milestone payout; Quest.turnIn reserves/delivers rewards. These are not reward-free developer mutation APIs. Need explicit expected-revision/no-reward paths, preserving pending legitimate receipts.
- Source: [QuestManager](../../../src/main/java/hu/taliann/icesmp/managers/QuestManager.java), [QuestPhysicalRewardDeliveryService](../../../src/main/java/hu/taliann/icesmp/managers/QuestPhysicalRewardDeliveryService.java), [BestiaryManager](../../../src/main/java/hu/taliann/icesmp/managers/BestiaryManager.java), [AchievementManager](../../../src/main/java/hu/taliann/icesmp/managers/AchievementManager.java), [AdvancementService](../../../src/main/java/hu/taliann/icesmp/managers/AdvancementService.java), [StatsManager](../../../src/main/java/hu/taliann/icesmp/managers/StatsManager.java), [HiddenSpotManager](../../../src/main/java/hu/taliann/icesmp/managers/HiddenSpotManager.java).

### legacy_daily

- Inspect: retained historical streak.
- Actions to implement: inspect only; no resurrected procedural-daily progression.
- Domain route: DailyQuestManager.getStreak.
- Coverage rationale / current gap: Retired procedural daily engine is a historical compatibility view only (source class documentation). No live daily mutation is valid; authored dailies belong to QuestManager. WorldWeaver inspect adapter is still absent.
- Source: [DailyQuestManager](../../../src/main/java/hu/taliann/icesmp/managers/DailyQuestManager.java), [PlayerProfileDailyQuestStore](../../../src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileDailyQuestStore.java).

### goals

- Inspect: community/weekly counters/goals/current season/pending completion.
- Actions to implement: conditional goal counter adjustment with explicit reward policy; inspect immutable receipts.
- Domain route: CommunityGoalManager.contributeOnce and durable completion outbox; ProfessionWeeklyGoalManager.add.
- Coverage rationale / current gap: Do not forge natural source-event receipts. Player/source context must survive contribution and payout queues; generic counter editing may inadvertently complete treasury/season rewards.
- Source: [CommunityGoalManager](../../../src/main/java/hu/taliann/icesmp/managers/CommunityGoalManager.java), [ProfessionWeeklyGoalManager](../../../src/main/java/hu/taliann/icesmp/managers/ProfessionWeeklyGoalManager.java).

### guild_party

- Inspect: members/leader/faction/activity/treasury and party sharing policy.
- Actions to implement: conditional lifecycle/membership/leadership controls and sandbox party-loot inspection.
- Domain route: GuildManager create/invite/accept/leave/kick/deposit; PartyManager invite/accept/leave/promote/disband.
- Coverage rationale / current gap: External membership drift and shared treasury/assets require transaction checks. Personal loot and guild activity XP need influence-aware source propagation.
- Source: [GuildManager](../../../src/main/java/hu/taliann/icesmp/managers/GuildManager.java), [PartyManager](../../../src/main/java/hu/taliann/icesmp/managers/PartyManager.java).

### politics

- Inspect: king/council/votes/tax/treasury/arrears.
- Actions to implement: conditional king/vote/tax/treasury changes and compensating actions.
- Domain route: KingManager.setKing/vote; CouncilManager.vote/onMembershipChange; FactionTreasuryManager.setTaxRate/depositOnce/withdraw.
- Coverage rationale / current gap: Crown/faction hooks, treasury balances and election eligibility cannot be reset via private maps. Expected revision and observable durable boundary required.
- Source: [KingManager](../../../src/main/java/hu/taliann/icesmp/managers/KingManager.java), [CouncilManager](../../../src/main/java/hu/taliann/icesmp/managers/CouncilManager.java), [FactionTreasuryManager](../../../src/main/java/hu/taliann/icesmp/managers/FactionTreasuryManager.java).

### crime_whisper

- Inspect: sin/pact/wanted/whisper/suspicion/evidence/exposure/crown curse/spy state.
- Actions to implement: conditional sin/pact/whisper/suspicion/exposure; runtime spy controls.
- Domain route: SinManager; PlayerProfileSinStore; WhisperManager; PlayerProfileWhisperStore; CrownCurseManager lifecycle.
- Coverage rationale / current gap: CROWN_CURSE has a dedicated lifecycle, so its context is inspectable but not freely projectable. Preserve immutable criminal history and pending punishment effects.
- Source: [SinManager](../../../src/main/java/hu/taliann/icesmp/managers/SinManager.java), [WhisperManager](../../../src/main/java/hu/taliann/icesmp/managers/WhisperManager.java), [CrownCurseManager](../../../src/main/java/hu/taliann/icesmp/managers/CrownCurseManager.java), [SpyManager](../../../src/main/java/hu/taliann/icesmp/managers/SpyManager.java).

### war_raid_duel

- Inspect: war window/raid instance/participants/points/duel/combat tags.
- Actions to implement: start/stop exact-instance conflict and bounded participants/target controls.
- Domain route: RaidManager.startRaid/endRaid/joinRaid; WarWindowManager.forceStart/forceEnd; HonorDuelManager.challenge/accept; CombatTagManager.
- Coverage rationale / current gap: SANDBOX conflict cannot grant kills, season points, treasury, loot or quest credit. Time-bound instance fences and combat-tag/protection invariants required.
- Source: [RaidManager](../../../src/main/java/hu/taliann/icesmp/managers/RaidManager.java), [WarWindowManager](../../../src/main/java/hu/taliann/icesmp/managers/WarWindowManager.java), [HonorDuelManager](../../../src/main/java/hu/taliann/icesmp/managers/HonorDuelManager.java), [CombatTagManager](../../../src/main/java/hu/taliann/icesmp/managers/CombatTagManager.java).

### season

- Inspect: season day/points/end/finale/modifier/chronicle/monument.
- Actions to implement: bounded modifier projection; conditional season points and lifecycle actions via canonical coordinator.
- Domain route: SeasonManager.setSeasonTransitionCoordinator/addExactPointsOnce; existing season/finale/monument/chronicle authorities.
- Coverage rationale / current gap: Global season completion fans out to treasury/community/history. Must preserve the real transition/outbox and use compensating events; never rewind immutable chronicle/monument history.
- Source: [SeasonManager](../../../src/main/java/hu/taliann/icesmp/managers/SeasonManager.java), [SeasonFinaleManager](../../../src/main/java/hu/taliann/icesmp/managers/SeasonFinaleManager.java), [SeasonMonumentManager](../../../src/main/java/hu/taliann/icesmp/managers/SeasonMonumentManager.java), [ChronicleManager](../../../src/main/java/hu/taliann/icesmp/managers/ChronicleManager.java), [HolidayService](../../../src/main/java/hu/taliann/icesmp/managers/HolidayService.java), [SeasonalModifierService](../../../src/main/java/hu/taliann/icesmp/managers/SeasonalModifierService.java), [SeasonStoryTeller](../../../src/main/java/hu/taliann/icesmp/managers/SeasonStoryTeller.java).

### prologue_doom

- Inspect: stage/stability/finale/participants/Doom gate/world access/commit chain.
- Actions to implement: bounded rehearsal/start/pause/resume/abort/checkpoint/gate controls through domain lifecycle.
- Domain route: PrologueManager durable state; PrologueFinaleManager; PrologueRuntime.worldAccess/encounters; reward commit chain.
- Coverage rationale / current gap: Current Doom runtime is the Prologue/gate/territory/portal boundary, not a separate Doom engine. Fork forbidden for this lifecycle. Rehearsal alone does not prove all reward quarantine.
- Source: [PrologueManager](../../../src/main/java/hu/taliann/icesmp/prologue/PrologueManager.java), [PrologueFinaleManager](../../../src/main/java/hu/taliann/icesmp/prologue/PrologueFinaleManager.java), [PrologueRuntime](../../../src/main/java/hu/taliann/icesmp/prologue/PrologueRuntime.java), [PrologueRewardService](../../../src/main/java/hu/taliann/icesmp/prologue/PrologueRewardService.java), [PrologueContentPolicy](../../../src/main/java/hu/taliann/icesmp/prologue/PrologueContentPolicy.java).

### parkour

- Inspect: courses/start/finish/current run/cancel conditions.
- Actions to implement: bounded start/cancel and conditional course definitions.
- Domain route: ParkourManager.start/cancel/cancelForMobility/setStart/setFinish/remove.
- Coverage rationale / current gap: Teleport/flight/velocity/SANDBOX influence must invalidate parkour credit and downstream quest completion, including post-projection quarantine.
- Source: [ParkourManager](../../../src/main/java/hu/taliann/icesmp/managers/ParkourManager.java).

### moderation

- Inspect: runtime vanished/muted/banned/history/reports/last location/inventory read view.
- Actions to implement: existing async moderation actions, revoke with new history, vanish refresh, report resolution.
- Domain route: ModerationManager.issueAsync/revokeAsync/setVanishedAsync; VanishManager refreshSubject; InvseeWriteCoordinator.
- Coverage rationale / current gap: Vanish has no independent setter ledger: durable state is ModerationManager. No arbitrary offline inventory writes or punishment/history erasure; WW primary-dev check precedes all adapters.
- Source: [ModerationManager](../../../src/main/java/hu/taliann/icesmp/managers/ModerationManager.java), [VanishManager](../../../src/main/java/hu/taliann/icesmp/managers/VanishManager.java), [InvseeManager](../../../src/main/java/hu/taliann/icesmp/managers/InvseeManager.java), [ReportManager](../../../src/main/java/hu/taliann/icesmp/managers/ReportManager.java).

### player_runtime

- Inspect: AFK/sit/intro/ferry availability.
- Actions to implement: bounded AFK/sit/stand/intro replay/ferry transition.
- Domain route: AfkManager.toggleAfk; SitManager.sit/standUp/resetPlayer; IntroManager.play; FerryManager.ride.
- Coverage rationale / current gap: Canonical mobility/cost paths and state cleanup must remain; SANDBOX teleport or cinematic cannot earn progression or parkour credit.
- Source: [AfkManager](../../../src/main/java/hu/taliann/icesmp/managers/AfkManager.java), [SitManager](../../../src/main/java/hu/taliann/icesmp/managers/SitManager.java), [IntroManager](../../../src/main/java/hu/taliann/icesmp/managers/IntroManager.java), [FerryManager](../../../src/main/java/hu/taliann/icesmp/managers/FerryManager.java).

### presentation

- Inspect: HUD snapshots/preferences, NPC binding/availability, bard runtime.
- Actions to implement: bounded preference/preview/refresh/binding/sound actions through owned APIs.
- Domain route: HudManager snapshot/preference/editor API; NpcBindingManager.bind/unbind; BardManager.sing.
- Coverage rationale / current gap: No permanent WorldWeaver HUD. Existing gameplay presentation surfaces can be inspected/manipulated without a second renderer or command wrapper; owner-thread GUI and NPC contracts apply.
- Source: [HudManager](../../../src/main/java/hu/taliann/icesmp/managers/HudManager.java), [TablistManager](../../../src/main/java/hu/taliann/icesmp/managers/TablistManager.java), [DialogService](../../../src/main/java/hu/taliann/icesmp/managers/DialogService.java), [BardManager](../../../src/main/java/hu/taliann/icesmp/managers/BardManager.java), [NpcBindingManager](../../../src/main/java/hu/taliann/icesmp/managers/NpcBindingManager.java).

### configuration

- Inspect: immutable merged snapshot/authority/reload policy/operator-editable paths.
- Actions to implement: bounded conditional operator override/revert through ConfigManager; content inspection only.
- Domain route: ConfigManager.applyOverridesIfUnchanged/applyOverride/resetOverride; ConfigSnapshot.
- Coverage rationale / current gap: No direct YAML editing. Restart-only canonical content is not runtime-mutated. A config-wide change with gameplay impact needs WORLD influence and explicit canonical arming.
- Source: [ConfigManager](../../../src/main/java/hu/taliann/icesmp/managers/ConfigManager.java).

### event_support

- Inspect: spawn anchors/eligibility/orchestration/protected event entities/dungeon chest/boss state.
- Actions to implement: conditional anchors/dungeon registrations and controlled ambient spawn/removal via authority.
- Domain route: EventSpawnPointManager; EventSpawnGuard; canonical CityGuard/DarkUndead/Dungeon managers.
- Coverage rationale / current gap: EventSpawner policies are shared infrastructure, not a replacement lifecycle; automatic spawns, dungeon payouts and natural DARK targeting need source identity/gates.
- Source: [EventSpawnPointManager](../../../src/main/java/hu/taliann/icesmp/managers/EventSpawnPointManager.java), [EventSpawnGuard](../../../src/main/java/hu/taliann/icesmp/managers/EventSpawnGuard.java), [EventSpawnSafetyPolicy](../../../src/main/java/hu/taliann/icesmp/managers/EventSpawnSafetyPolicy.java), [MajorEventGate](../../../src/main/java/hu/taliann/icesmp/managers/MajorEventGate.java), [CityGuardManager](../../../src/main/java/hu/taliann/icesmp/managers/CityGuardManager.java), [DarkUndeadAmbienceManager](../../../src/main/java/hu/taliann/icesmp/managers/DarkUndeadAmbienceManager.java), [DungeonLootService](../../../src/main/java/hu/taliann/icesmp/managers/DungeonLootService.java).

### event.blood_moon

- Inspect: isActive/getRemainingMillis.
- Actions to implement: forceStart/forceEnd.
- Domain route: BloodMoonManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [BloodMoonManager](../../../src/main/java/hu/taliann/icesmp/managers/BloodMoonManager.java).

### event.world_boss

- Inspect: isBossActive/encounterSnapshot/archetypeDisplayNames.
- Actions to implement: forceSpawn; add stopExpectedInstance.
- Domain route: WorldBossManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [WorldBossManager](../../../src/main/java/hu/taliann/icesmp/managers/WorldBossManager.java).

### event.invasion

- Inspect: isActive/isInvasionMob.
- Actions to implement: forceStart; add stopExpectedInstance.
- Domain route: InvasionManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [InvasionManager](../../../src/main/java/hu/taliann/icesmp/managers/InvasionManager.java).

### event.caravan

- Inspect: isActive/getStockSeed.
- Actions to implement: forceArrive/forceDepart.
- Domain route: CaravanManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [CaravanManager](../../../src/main/java/hu/taliann/icesmp/managers/CaravanManager.java).

### event.ambient

- Inspect: current activity requires immutable snapshot port.
- Actions to implement: forceRandom; add lifecycle instance/stop.
- Domain route: AmbientEventManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [AmbientEventManager](../../../src/main/java/hu/taliann/icesmp/managers/AmbientEventManager.java).

### event.gathering

- Inspect: getActive/getRemainingMillis.
- Actions to implement: forceRandom; add stopExpectedInstance.
- Domain route: GatheringBuffManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [GatheringBuffManager](../../../src/main/java/hu/taliann/icesmp/managers/GatheringBuffManager.java).

### event.treasure

- Inspect: isActive/isTreasureBlock.
- Actions to implement: forceSpawn; add stopExpectedInstance.
- Domain route: TreasureEventManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [TreasureEventManager](../../../src/main/java/hu/taliann/icesmp/managers/TreasureEventManager.java).

### event.wild_hunt

- Inspect: isActive/isWildHunt.
- Actions to implement: forceStart; add stopExpectedInstance.
- Domain route: WildHuntManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [WildHuntManager](../../../src/main/java/hu/taliann/icesmp/managers/WildHuntManager.java).

### event.abundance

- Inspect: isActive/getRemainingMillis.
- Actions to implement: forceStart; add stopExpectedInstance.
- Domain route: AbundanceManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [AbundanceManager](../../../src/main/java/hu/taliann/icesmp/managers/AbundanceManager.java).

### event.server_challenge

- Inspect: isActive/getProgress/getTarget.
- Actions to implement: forceStart; add stopExpectedInstance.
- Domain route: ServerChallengeManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [ServerChallengeManager](../../../src/main/java/hu/taliann/icesmp/managers/ServerChallengeManager.java).

### event.escort

- Inspect: isActive/isConvoy/isWaveMob.
- Actions to implement: forceStart; add stopExpectedInstance.
- Domain route: EscortManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [EscortManager](../../../src/main/java/hu/taliann/icesmp/managers/EscortManager.java).

### event.meteor

- Inspect: isActive/getRemainingMillis.
- Actions to implement: forceSpawn; add stopExpectedInstance.
- Domain route: MeteorEventManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [MeteorEventManager](../../../src/main/java/hu/taliann/icesmp/managers/MeteorEventManager.java).

### event.stranger

- Inspect: isStranger; snapshot port.
- Actions to implement: forceSpawn; add lifecycle stop.
- Domain route: StrangerNpcManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [StrangerNpcManager](../../../src/main/java/hu/taliann/icesmp/managers/StrangerNpcManager.java).

### event.corruption

- Inspect: isActive/getPurgeKills/isCoreBlock.
- Actions to implement: forceSpawn/forceSpawnAt/tryCleanse; add stopExpectedInstance.
- Domain route: CorruptionManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [CorruptionManager](../../../src/main/java/hu/taliann/icesmp/managers/CorruptionManager.java).

### event.archeology

- Inspect: isActive.
- Actions to implement: forceSpawn; add stopExpectedInstance.
- Domain route: ArcheologyManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [ArcheologyManager](../../../src/main/java/hu/taliann/icesmp/managers/ArcheologyManager.java).

### event.cultists

- Inspect: isActive/isCultist.
- Actions to implement: forceStart; add stopExpectedInstance.
- Domain route: CultistEventManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [CultistEventManager](../../../src/main/java/hu/taliann/icesmp/managers/CultistEventManager.java).

### event.economy

- Inspect: isActive/isBoomActive/getMultiplier.
- Actions to implement: startCouncilBoom; safe exact-instance stop.
- Domain route: EconomyEventManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [EconomyEventManager](../../../src/main/java/hu/taliann/icesmp/managers/EconomyEventManager.java).

### event.player_caravan

- Inspect: isActive/isConvoy.
- Actions to implement: send; exact-instance stop.
- Domain route: PlayerCaravanManager existing lifecycle, using new typed WeaverEventAdapter.
- Coverage rationale / current gap: Start needs durable origin/instance before first effect; stop must match expected instance; propagate source to spawned entities, spatial effects and reward/progress. Current API has no complete WW contract.
- Source: [PlayerCaravanManager](../../../src/main/java/hu/taliann/icesmp/managers/PlayerCaravanManager.java).

### profile_infrastructure

- Inspect: health/revision through self diagnostics; domain state through corresponding provider.
- Actions to implement: none directly.
- Domain route: canonical domain stores and profile transaction service.
- Coverage rationale / current gap: Generic storage/profile extension editing is not a gameplay capability and violates domain ownership. Each concrete domain store is assigned to its gameplay domain.
- Source: [PlayerProfileAuthority](../../../src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileAuthority.java), [PlayerProfileService](../../../src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileService.java), [PlayerProfileQueryService](../../../src/main/java/hu/taliann/icesmp/playerprofile/api/PlayerProfileQueryService.java), [PlayerProfileTransactionManager](../../../src/main/java/hu/taliann/icesmp/playerprofile/transaction/PlayerProfileTransactionManager.java), [YamlPlayerProfileTransactionManager](../../../src/main/java/hu/taliann/icesmp/playerprofile/transaction/YamlPlayerProfileTransactionManager.java).

### storage_infrastructure

- Inspect: health through self diagnostics.
- Actions to implement: none directly.
- Domain route: YamlStore and existing coordinators.
- Coverage rationale / current gap: Raw YAML/store mutation, queue control and authority installation are infrastructure operations; not independent gameplay developer surfaces.
- Source: [PersistentStore](../../../src/main/java/hu/taliann/icesmp/storage/PersistentStore.java), [PersistentStoreCoordinator](../../../src/main/java/hu/taliann/icesmp/storage/PersistentStoreCoordinator.java), [YamlStore](../../../src/main/java/hu/taliann/icesmp/storage/YamlStore.java).

### transport_infrastructure

- Inspect: bounded diagnostics through self/presentation.
- Actions to implement: none directly.
- Domain route: existing client bridge and message layer.
- Coverage rationale / current gap: Transport handshake/session nonce/localization internals are protocol infrastructure; direct editing would bypass client/session invariants. No new client protocol is required.
- Source: [ClientSessionRegistry](../../../src/main/java/hu/taliann/icesmp/client/ClientSessionRegistry.java), [MessageManager](../../../src/main/java/hu/taliann/icesmp/utils/MessageManager.java).

### future_biome

- Inspect: current vanilla biome read via minecraft, contextual PvE selection via pve.
- Actions to implement: none for a missing subsystem.
- Domain route: No current BiomeManager/provider-specific canonical biome registry found.
- Coverage rationale / current gap: Custom faction-biome subsystem is absent at this cumulative head. Existing vanilla biome observations, quest visits and spawn affinity are covered by minecraft/knowledge/pve; not deferred as missing current gameplay.

## Projection consumer matrix — required, not implemented

| Projection | Effective runtime consumer to integrate | Must stay canonical | Current status |
|---|---|---|---|
| PvE abilities / profile / archetype / rank | `MobAbilityRuntime`, combat attribute/profile layer | Mob loot, rank loot band, Bestiary identity, authored reward identity | No effective projection port |
| Player faction | `FactionPassiveListener` damage/environment/exhaustion and targeting | FactionManager membership/history, tax, treasury, quests, territory owner, HUD identity | No projection source |
| Entity semantic context / target peace | `FactionMobContextResolver`, `FactionPassivePolicy`, existing scripted targeting | `CROWN_CURSE` own lifecycle; canonical event identity | No projection source |
| Territory rules | Common pure evaluation core of `TerritoryProtectionService` | Territory storage/index and hard Doom/combat-tag/raid/bypass constraints | No shared trace/overlay evaluator |
| Trash rules | Shared `TrashRuleFieldService` extracted from actual relic/anomaly runtime | Identity, lifecycle history, archaeology natural facts | Shared service absent |
| Class resources / cooldowns / runtime mechanics | `ResourceManager`, per-class GameplayService, spell cooldown owner | Canonical profile, mastery, grants, real reward identity | Provider/domain ports absent |
| Vanilla world/spatial effects | Owner-routed Bukkit runtime | Canonical content registries | Provider and influence consumers absent |
| Prototype items | Presentation only; prototype-protection listener | Equipment stats/rune/set/signature and every economy/crafting/progression input | Quarantine absent |

A projection record in a store without a listed, exercised runtime consumer cannot close any row.

## Reward/progression ingress and settlement audit

All rows below require a neutral, context-carrying eligibility gate. No WorldWeaver gate exists at the base. These are source locations to integrate and test, not claims that a sandbox reward leak has been closed.

| Channel / surface | Existing producers / settlement authorities | Required source context / gate |
|---|---|---|
| Vanilla mob drops / XP | EntityDeathEvent; MobLootListener; MobMoneyDropListener; SoulstoneListener | Monotonic entity/source influence; clear drops and set XP=0 before reward producers; gate later additions too |
| Custom mob loot | MobLootListener; TrashMobDropListener; DungeonLootService | Snapshot victim/source before killer hop; canonical reward identity stays unchanged |
| CLASS_XP | ClassXpListener; JobManager.addXpToJobV2; SpecializationManager mastery paths | Victim/source lineage plus beneficiary quarantine before any class/mastery change |
| PET_XP | PetXpListener; PetManager.addXpV2 | Victim lineage, credited companion id and owner quarantine across both schedulers |
| BESTIARY | BestiaryListener; BestiaryManager.record/checkMilestone; PlayerProfileAchievementStore | Block discovery before section CAS and milestone reservation; preserve legitimate pending payout |
| TRACKING_PROGRESS | StatsCombatListener; StatsManager; spell cast / death / kill statistics | Prevent tainted counters from later unlocking metric-based achievements |
| QUEST_PROGRESS — kill | QuestProgressListener; QuestManager.handleKill/handleBossKill/handlePlayerKill | Victim/source lineage + actor quarantine before progress and community contribution |
| QUEST_PROGRESS — nonkill | QuestManager.handleBlockBreak/handleCraft/handleFish/handlePlaceBlock/handleCollect/handleBreed/handleEnchant/handleConsume/handleSmelt/handleTame/handleVillagerTrade | Immutable block/item/spatial/world or actor context carried to manager |
| QUEST_PROGRESS — authorized events | QuestManager.handleAuthorizedEvent/handleAuthorizedNpcInteract/handleBiomeVisit/handleTerritoryEnter/handleParkourFinish | Real authorized origin retained; WW action cannot forge a natural event receipt |
| QUEST_REWARD | QuestManager.turnIn/recoverPendingRewards; QuestPhysicalRewardDeliveryService | Gate before reservation/turn-in; persisted eligibility and exact payout identity; do not delete valid preexisting outbox |
| PROFESSION_XP | ProfessionXpListener; ProfessionManager.addXpFor/addXp; ProfessionRecipeBookListener | Gate before XP CAS and dependent weekly/recipe/level-up hooks |
| RARE_GATHERING | RareGatheringListener; FishingWindfallListener; GatheringBuffListener; AbundanceListener | Block, fish, weather/spatial and active event influence; no item/XP faucet |
| ITEM_ACQUISITION | ItemAcquisitionPolicy; QuestProgressListener pickup/inventory paths; ItemProvenanceListener | Prototype/source lineage survives transfers and stack transformations; accepted pickup amount remains authoritative |
| PERSONAL_LOOT | PartyManager.distributePersonalLoot; EncounterRewardDeliveryService; TreasureEventManager; WildHuntManager; CorruptionManager | Gate each beneficiary and originating encounter before reservation; carry outcome across owner hops |
| ACHIEVEMENT | AchievementManager.evaluate; PlayerProfileAchievementStore reserve/recovery | Gate state and reward separately; metric provenance cannot be laundered through delayed evaluation |
| DISCOVERY / advancement | HiddenSpotManager; AdvancementService.award; TrashArchaeologyService.inspect | Location/source and player quarantine; prototype/dev-history observations cannot earn natural knowledge |
| COMMUNITY_GOAL | CommunityGoalManager.contribute/contributeOnce; completion outbox | Source id/mode persisted before progress; treasury/season/buff fanout follows same eligibility |
| WEEKLY_GOAL | ProfessionWeeklyGoalManager.add/tick/onJoin | Gate counter and durable/reconnect reward, not just XP listener |
| SERVER_CHALLENGE | ServerChallengeListener; ServerChallengeManager.record/rewardAll | record currently lacks actor/source context; extend API so sandbox world/event progress cannot reward everyone |
| CURRENCY_FAUCET | CurrencyManager; MobMoneyDropListener; MoneyPouchListener; FishingWindfallListener; AmbientEventManager; SinListener bounty | Gate origin and physical token path; distinguish faucet from legitimate transfer/refund/escrow settlement |
| CRATE_REWARD | CrateManager.requestOpen / key / reward / restorePendingRecovery | Validate prototype/key influence before reservation; retain legitimate pending delivery |
| DEV_ITEM_REWARD | DevItemManager.tickOwner / pending reward | Bingulus must obey beneficiary quarantine without losing pending/pity/history |
| PARKOUR_CREDIT | ParkourManager.checkFinish/setFinishHook | Sandbox movement, flight and teleport cannot earn finish reward or quest credit |
| RAID_CREDIT | RaidManager.recordKill/endRaid; SinListener | Event instance/source/player lineage gates points, treasury, territory/season consequences |
| WAR_CREDIT | WarWindowManager.handleWarKill; HonorDuelManager.settleKill | Exact active instance/window token + source/beneficiary quarantine |
| EVENT_REWARD | 16 mandatory event managers plus economy and player caravan adapters | Origin durable before first effect; own spawn/item/field and participant effects; expected-instance stop |
| Guild activity | GuildManager.addActivityXp; QuestManager guild hooks | Preserve source eligibility beyond quest/class contribution |
| Season/finale reward | SeasonManager.addPointsOnce/addExactPointsOnce; PrologueRewardService; SeasonFinaleManager | No sandbox season/founder/chronicle/monument/treasury commit-chain credit |
| Soul shards and progression | SoulShardListener; SoulShardManager; RitualManager; SoulforgeManager | Tainted kills cannot mint shards or indirectly buy permanent upgrades |
| Trash loot/archaeology | TrashFishingListener; TrashMobDropListener; TrashAmbientManager; TrashHistoryService | Origin tracked through dropped items, significant history, recycler and knowledge consumption |
| Prototype escape protection | ItemTransformationPolicy; EquipmentProficiencyService; market/donation/buyer/crate; craft/anvil/smith/grindstone/furnace/brewing/consume/place/container/drop | No prototype economic/progression/stat source; authoritative cleanup on wrong holder/container |
| Influence propagation | MobAbilityRuntime projectiles/summons; minion/pet/totem; potion/DOT/TNT/environment; event/spatial/world child effects | Lineage captured before side effects; never remove SANDBOX taint using LIVE_GM; player/world minimum five-minute quarantine |

## Source-proven integration findings

| ID | Priority for WorldWeaver enable | Evidence | Required root-cause closure |
|---|---|---|---|
| WW-A01 | C0 authority | DevItemManager has configurable Bingulus owner; HiddenDevAuthority has a different fixed primary UUID | Generalize lifecycle, keep per-artifact owner policy, enforce kernel token and physical authoritative instance |
| WW-A02 | C0 reward | No DeveloperInfluence/RewardEligibilityPolicy integration at current producers | Durable monotonic lineage; gate producer/progress/reservation/settlement, not a kill-listener-only filter |
| WW-A03 | C1 Folia | FactionManager.setFaction/switchFactionDurably call `.join()` | Add asynchronous conditional domain route; continuation through profile authority and owner hooks |
| WW-A04 | C1 persistence | TerritoryManager setters publish map/index before save; capital edits may demote another territory | One durable conditional candidate transaction with complete observed-state fingerprint |
| WW-A05 | C1 domain ownership | Trash activation exposed mostly as event handlers | Extract actual activation/rule services shared by runtime/provider; no fake Bukkit event |
| WW-A06 | C1 lifecycle | Most event managers lack expected-instance public stop and durable origin | Domain-owned async start/stop adapter with pre-effect instance/origin and proper recovery |
| WW-A07 | C1 progression | Bestiary.record schedules milestone payout; quest turn-in owns reward reservation | Explicit no-reward canonical developer paths with expected revision and compensation |
| WW-A08 | C1 prototype | Generic item mutation assumes normal cost/physical-item lifecycle | Extend canonical coordinator and all prototype consumer boundaries; no fake receipt |
| WW-A09 | C1 observability | Existing manager booleans/maps cannot assess all prepared/applied operations | Provider observed-state assessment and persistent unloaded-entity pending state |

These are unresolved implementation seams. This audit does not mark them fixed, waive them or permit production enable.

## Validation performed

- Full normative design read: all 2,938 lines; original and whitespace-normalized copy hashes retained.
- Remote PR metadata and current stack inspected; fresh checkout exact base/tree verified.
- `python3 scripts/audit_world_weaver_coverage.py`: 262 authorities / 55 domains / 51 implementation blockers / 0 inventory errors.
- `python3 scripts/check_consistency.py`: 0 FAIL, 0 WARN.
- `python3 scripts/check_player_profile_transitions.py`: 0 transitional authorities.
- `python3 scripts/test_resource_pack.py`: 18 tests passed (tooling tests, not a WorldWeaver asset proof).
- No Java, bundled gameplay content, Gradle build definition or resource-pack file has changed in WW-00.
- Local wrapper build attempted: blocked downloading Gradle (`Network is unreachable`). Installed Gradle 9.4.1 offline build attempted: run-paper 3.0.2 plugin is not cached. Paper/optional dependency jars are not available locally.

### Existing baseline CI proof — exact scope

- [Trash production hardening run 33479204884](https://github.com/MilCsik09/IceSMP/actions/runs/33479204884): success.
- Java job 99764824018 log contains `:compileJava`, all seven stacked Trash suite pass markers and `BUILD SUCCESSFUL`.
- That compile job tested merge commit `9077f69e97e572a5c65b9bcbcbba2ac315772c82`; its tree is exactly `99cdf8036c9b4b0fa322e7c8f867e29a2e96e24f`, equal to the selected base.
- Paper job 99764824291 and Folia job 99764824313 completed their runtime and clean-shutdown proof steps successfully; their workflow checks out the exact PR head.
- [Resource-pack run 33479204874](https://github.com/MilCsik09/IceSMP/actions/runs/33479204874): success.
- [Documentation inventory run 33479204899](https://github.com/MilCsik09/IceSMP/actions/runs/33479204899): success.

Baseline evidence verifies unchanged code only. It does not establish WorldWeaver authority, reward integrity, Folia, persistence, dynamic discovery, or playtest acceptance.

## Remaining phase gates

| Phase | Status in this checkpoint | Required completion |
|---|---|---|
| WW-00 | Source audit and machine inventory prepared | Commit/PR evidence tracked in handoff report |
| WW-01 | NOT IMPLEMENTED | Shared DEV artifact lifecycle/schema 2/Bingulus extraction and four model states |
| WW-02 | NOT IMPLEMENTED | Kernel/typed subjects/snapshots/descriptors/discovery/GUI/execution and authority |
| WW-03 | NOT IMPLEMENTED | Journal/state/audit/projection/reconcile/conditional undo/AREA execution |
| WW-04 | NOT IMPLEMENTED | PvE/faction projection consumers and all reward ingress/egress gates |
| WW-05 | NOT IMPLEMENTED | Trash shared activation/rules; territory trace/overlay/transaction |
| WW-06 | NOT IMPLEMENTED | Itemization/event/self/knowledge domain adapters and prototype quarantine |
| WW-07 | NOT IMPLEMENTED | Thread/Imprint/Quick Apply/AREA selection UX |
| WW-08 | NOT IMPLEMENTED | Binding/Fork/limits/circuit breaker/failure/crash/Folia tests |
| WW-09 | NOT IMPLEMENTED | Close all 51 domain blockers and dynamic acceptance A/B |
| WW-10 | NOT AUTHORIZED FOR ENABLE | Separate production decision; flag remains disabled until evidence complete |

## Acceptance boundary

Architecture: NOT IMPLEMENTED. Gameplay capability: NOT IMPLEMENTED. Authority/security: NOT VERIFIED. Folia: NOT VERIFIED for WorldWeaver. Reward integrity: NOT IMPLEMENTED. Persistence: NOT IMPLEMENTED. Dynamic extensibility: NOT IMPLEMENTED. Full-stack merge readiness: NO. Production readiness: NO.

Required human/client evidence remains design sections 53 and 56: cross-region actor/target, unload/restart, forged/duplicate artifact, client model/interaction/GUI, prototype escape attempts, projection consumer separation, event reward zero, crash/recovery/undo drift. Static source checks are not substitutes.
