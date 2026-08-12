# IceSMP class/spec/spell/cast audit

## Exact state
- Staging SHA: `1bf68ea2bddeae757042481c0fb7a00caa5f46b9`
- Artifact-producing feature source SHA: `5e2a01ab7933f5dd7287b47c50dc8c67ac8c3b57`
- Final artifact-bearing feature HEAD: `HEAD` (absolute SHA is recorded by the final GitHub readback ledger in PR #115)
- Merge-base SHA: `1bf68ea2bddeae757042481c0fb7a00caa5f46b9`
- Ahead / behind: **7 / 0**
- Changed file count: **41**

## Inventory
- Classes: **13**
- Specializations: **35**
- Source-defined spell IDs: **420**
- Runtime-registered spell IDs: **420**
- Normal progression-reachable spell IDs: **420**
- Balance entries: **420**
- Provenance: **131 BASE + 288 SPEC + 1 TALENT**
- Implementation: configured=348, dedicated=23, form=4, projectile=10, stateful=26, summon=9

## ConfiguredSpell verdict — OPTION B
- A: **320**
- B: **28**
- C: **0**
- D: **0**

### B/C/D reasons
| Spell | Category | Reason |
|---|---:|---|
| `ascendance_flame` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `avenging_wrath` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `berserk` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `breath_of_sindragosa` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `celestial_alignment` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `darkglare` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `deathcap` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `doom_winds` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `dragonrage` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `eternity_breath` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `evangelism` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `final_verdict` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `gravity_well` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `incarnation_bear` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `invoke_niuzao` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `king_of_beasts` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `last_stand` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `masterful_shot` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `metamorphosis_havoc` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `metamorphosis_veng` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `revival` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `rewind` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `serenity` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `spectre` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `spirit_tide` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `the_hunt` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `tranquility` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |
| `void_eruption` | B | generic gameplay retained; signature presentation/VFX readability is intentionally distinct |

## Class verdicts
| Class | Base spells | Specs | Verdict |
|---|---:|---|---|
| `warrior` | 10 | berserker, guardian | PASS |
| `evoker` | 10 | devastation, preservation | PASS |
| `archer` | 10 | sharpshooter, beast_master | PASS |
| `shaman` | 10 | elemental, enhancement, tidal | PASS |
| `monk` | 10 | windwalker, brewmaster, mistweaver | PASS |
| `paladin` | 10 | holy, retribution, protection | PASS |
| `demon_hunter` | 10 | havoc, vengeance | PASS |
| `druid` | 11 | feral, lunar, ironbark, restoration | PASS |
| `priest` | 10 | discipline, bone_priest, shadow | PASS |
| `death_knight` | 10 | blood, frost, unholy | PASS |
| `assassin` | 10 | poisoner, phantom, plaguebringer | PASS |
| `warlock` | 10 | affliction, destruction, demonologist | PASS |
| `wizard` | 10 | elementalist, necromancer | PASS |

## Specialization verdicts
| Spec | Class | Spell count | Active-kit refs | DARK | Verdict |
|---|---|---:|---:|---|---|
| `berserker` | `warrior` | 10 | 7 | no | PASS |
| `guardian` | `warrior` | 10 | 7 | no | PASS |
| `devastation` | `evoker` | 7 | 7 | no | PASS |
| `preservation` | `evoker` | 7 | 7 | no | PASS |
| `sharpshooter` | `archer` | 10 | 7 | no | PASS |
| `beast_master` | `archer` | 12 | 7 | no | PASS |
| `elemental` | `shaman` | 8 | 7 | no | PASS |
| `enhancement` | `shaman` | 9 | 6 | no | PASS |
| `tidal` | `shaman` | 8 | 6 | no | PASS |
| `windwalker` | `monk` | 7 | 7 | no | PASS |
| `brewmaster` | `monk` | 8 | 7 | no | PASS |
| `mistweaver` | `monk` | 8 | 7 | no | PASS |
| `holy` | `paladin` | 8 | 7 | no | PASS |
| `retribution` | `paladin` | 8 | 7 | no | PASS |
| `protection` | `paladin` | 8 | 7 | no | PASS |
| `havoc` | `demon_hunter` | 7 | 7 | no | PASS |
| `vengeance` | `demon_hunter` | 7 | 7 | no | PASS |
| `feral` | `druid` | 9 | 7 | no | PASS |
| `lunar` | `druid` | 8 | 7 | no | PASS |
| `ironbark` | `druid` | 8 | 7 | no | PASS |
| `restoration` | `druid` | 8 | 7 | no | PASS |
| `discipline` | `priest` | 7 | 7 | no | PASS |
| `bone_priest` | `priest` | 7 | 7 | yes | PASS |
| `shadow` | `priest` | 7 | 7 | no | PASS |
| `blood` | `death_knight` | 7 | 7 | no | PASS |
| `frost` | `death_knight` | 8 | 7 | no | PASS |
| `unholy` | `death_knight` | 7 | 7 | yes | PASS |
| `poisoner` | `assassin` | 10 | 7 | no | PASS |
| `phantom` | `assassin` | 10 | 7 | no | PASS |
| `plaguebringer` | `assassin` | 7 | 7 | yes | PASS |
| `affliction` | `warlock` | 7 | 7 | no | PASS |
| `destruction` | `warlock` | 7 | 7 | no | PASS |
| `demonologist` | `warlock` | 7 | 7 | yes | PASS |
| `elementalist` | `wizard` | 10 | 7 | no | PASS |
| `necromancer` | `wizard` | 12 | 7 | yes | PASS |

## Regression graph
| Suite | Wiring | Task |
|---|---|---|
| `hu.taliann.icesmp.archer.ArcherGameplayRegressionSuite` | TASK+CHECK | `archerGameplayRegressionTest` |
| `hu.taliann.icesmp.archer.ArcherProfileRegressionSuite` | TASK+CHECK | `archerProfileRegressionTest` |
| `hu.taliann.icesmp.assassin.AssassinGameplayRegressionSuite` | TASK+CHECK | `assassinGameplayRegressionTest` |
| `hu.taliann.icesmp.assassin.AssassinProfileRegressionSuite` | TASK+CHECK | `assassinProfileRegressionTest` |
| `hu.taliann.icesmp.classrelic.ClassRelicRegressionSuite` | TASK+CHECK | `classRelicRegressionTest` |
| `hu.taliann.icesmp.classspec.application.ClassSpecApplicationRegressionSuite` | TASK+CHECK | `classSpecApplicationRegressionTest` |
| `hu.taliann.icesmp.classspec.application.ClassSpecSectionLifecycleRegressionSuite` | TASK+CHECK | `classSpecLifecycleRegressionTest` |
| `hu.taliann.icesmp.classspec.compat.ClassSpecCompatibilityRegressionSuite` | TASK+CHECK | `classSpecCompatibilityRegressionTest` |
| `hu.taliann.icesmp.classspec.domain.SpellGrantLedgerRegressionSuite` | TASK+CHECK | `spellGrantLedgerRegressionTest` |
| `hu.taliann.icesmp.classspec.profile.ClassSpecSectionV2RegressionSuite` | TASK+CHECK | `classSpecSectionRegressionTest` |
| `hu.taliann.icesmp.classspec.transaction.RespecTransactionRegressionSuite` | TASK+CHECK | `respecTransactionRegressionTest` |
| `hu.taliann.icesmp.config.ConfigGuiCoverageRegressionSuite` | TASK+CHECK | `configGuiCoverageRegressionTest` |
| `hu.taliann.icesmp.config.ConfigGuiTransactionRegressionSuite` | TASK+CHECK | `configGuiTransactionRegressionTest` |
| `hu.taliann.icesmp.crates.CrateRegressionSuite` | TASK+CHECK | `crateRegressionTest` |
| `hu.taliann.icesmp.deathknight.DeathKnightGameplayRegressionSuite` | TASK+CHECK | `deathKnightGameplayRegressionTest` |
| `hu.taliann.icesmp.deathknight.DeathKnightProfileRegressionSuite` | TASK+CHECK | `deathKnightProfileRegressionTest` |
| `hu.taliann.icesmp.demonhunter.DemonHunterGameplayRegressionSuite` | TASK+CHECK | `demonHunterGameplayRegressionTest` |
| `hu.taliann.icesmp.demonhunter.DemonHunterProfileRegressionSuite` | TASK+CHECK | `demonHunterProfileRegressionTest` |
| `hu.taliann.icesmp.dialog.PauseMenuDialogRegressionSuite` | TASK+CHECK | `pauseMenuDialogRegressionTest` |
| `hu.taliann.icesmp.druid.DruidGameplayRegressionSuite` | TASK+CHECK | `druidGameplayRegressionTest` |
| `hu.taliann.icesmp.druid.DruidProfileRegressionSuite` | TASK+CHECK | `druidProfileRegressionTest` |
| `hu.taliann.icesmp.evoker.EvokerGameplayRegressionSuite` | TASK+CHECK | `evokerGameplayRegressionTest` |
| `hu.taliann.icesmp.evoker.EvokerProfileRegressionSuite` | TASK+CHECK | `evokerProfileRegressionTest` |
| `hu.taliann.icesmp.factions.FactionDisplayColorRegressionSuite` | TASK+CHECK | `factionDisplayColorRegressionTest` |
| `hu.taliann.icesmp.factions.FactionPassiveHardeningRegressionSuite` | TASK+CHECK | `factionPassiveHardeningRegressionTest` |
| `hu.taliann.icesmp.factions.FactionPassiveRegressionSuite` | TASK+CHECK | `factionPassiveRegressionTest` |
| `hu.taliann.icesmp.factions.FactionTaxDebtRegressionSuite` | TASK+CHECK | `factionTreasuryRegressionTest` |
| `hu.taliann.icesmp.hud.HudEditorRegressionSuite` | TASK+CHECK | `hudEditorRegressionTest` |
| `hu.taliann.icesmp.hud.IceSmpHudRegressionSuite` | TASK+CHECK | `iceSmpHudRegressionTest` |
| `hu.taliann.icesmp.integration.ProtectionBridgeRegressionSuite` | TASK+CHECK | `worldGuardBridgeRegressionTest` |
| `hu.taliann.icesmp.inventory.InventoryReadWriteRegressionSuite` | TASK+CHECK | `inventoryReadWriteRegressionTest` |
| `hu.taliann.icesmp.items.RelicRefreshRegressionSuite` | TASK+CHECK | `relicItemRefreshRegressionTest` |
| `hu.taliann.icesmp.lifecycle.LifecycleShutdownRegressionSuite` | TASK+CHECK | `lifecycleShutdownRegressionTest` |
| `hu.taliann.icesmp.managers.AdvancedConfigMenuRegressionSuite` | TASK+CHECK | `advancedConfigMenuRegressionTest` |
| `hu.taliann.icesmp.managers.AfkRegressionSuite` | TASK+CHECK | `afkRegressionTest` |
| `hu.taliann.icesmp.managers.BestiaryRegressionSuite` | TASK+CHECK | `bestiaryRegressionTest` |
| `hu.taliann.icesmp.managers.ConfigStartupRegressionSuite` | TASK+CHECK | `configStartupRegressionTest` |
| `hu.taliann.icesmp.managers.DevItemRewardRegressionSuite` | TASK+CHECK | `persistentStoreRegressionTest` |
| `hu.taliann.icesmp.managers.HudRegressionSuite` | TASK+CHECK | `hudRegressionTest` |
| `hu.taliann.icesmp.managers.OperationalConfigMenuRegressionSuite` | TASK+CHECK | `operationalConfigMenuRegressionTest` |
| `hu.taliann.icesmp.managers.RelicRefreshPipelineRegressionSuite` | TASK+CHECK | `relicRefreshPipelineRegressionTest` |
| `hu.taliann.icesmp.managers.SitRegressionSuite` | TASK+CHECK | `sitRegressionTest` |
| `hu.taliann.icesmp.managers.SpellMasteryTransactionRegressionSuite` | TASK+CHECK | `spellMasteryTransactionRegressionTest` |
| `hu.taliann.icesmp.moderation.ModerationRegressionSuite` | DELEGATED | `` |
| `hu.taliann.icesmp.moderation.ModerationReviewRegressionSuite` | TASK+CHECK | `moderationRegressionTest` |
| `hu.taliann.icesmp.monk.MonkGameplayRegressionSuite` | TASK+CHECK | `monkGameplayRegressionTest` |
| `hu.taliann.icesmp.monk.MonkProfileRegressionSuite` | TASK+CHECK | `monkProfileRegressionTest` |
| `hu.taliann.icesmp.monk.MonkStaggerLifecycleRegressionSuite` | DELEGATED | `` |
| `hu.taliann.icesmp.motd.MotdRegressionSuite` | TASK+CHECK | `motdRegressionTest` |
| `hu.taliann.icesmp.paladin.PaladinGameplayRegressionSuite` | TASK+CHECK | `paladinGameplayRegressionTest` |
| `hu.taliann.icesmp.paladin.PaladinProfileRegressionSuite` | TASK+CHECK | `paladinProfileRegressionTest` |
| `hu.taliann.icesmp.playerprofile.api.PlayerProfileApiRegressionSuite` | TASK+CHECK | `playerProfileApiRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStoreRegressionSuite` | TASK+CHECK | `playerProfileAchievementRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileCrateStoreRegressionSuite` | TASK+CHECK | `playerProfileCrateRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileDailyQuestStoreRegressionSuite` | TASK+CHECK | `playerProfileDailyQuestRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileDeathEscrowStoreRegressionSuite` | TASK+CHECK | `playerProfileDeathEscrowRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileEconomyStoreRegressionSuite` | TASK+CHECK | `playerProfileEconomyRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionAuthorityRegressionSuite` | TASK+CHECK | `playerProfileFactionAuthorityRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionFoodStoreRegressionSuite` | TASK+CHECK | `playerProfileFactionFoodRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileFullAuthorityRegressionSuite` | TASK+CHECK | `playerProfileFullAuthorityRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileIntroStoreRegressionSuite` | TASK+CHECK | `playerProfileIntroRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileModerationStoreRegressionSuite` | TASK+CHECK | `playerProfileModerationRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileQuestStoreRegressionSuite` | TASK+CHECK | `playerProfileQuestRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileStatisticsStoreRegressionSuite` | TASK+CHECK | `playerProfileStatisticsRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileTaxStoreRegressionSuite` | TASK+CHECK | `playerProfileTaxRegressionTest` |
| `hu.taliann.icesmp.playerprofile.application.PlayerProfileWeeklyGoalStoreRegressionSuite` | TASK+CHECK | `playerProfileWeeklyGoalRegressionTest` |
| `hu.taliann.icesmp.playerprofile.domain.PlayerProfileDomainRegressionSuite` | TASK+CHECK | `playerProfileDomainRegressionTest` |
| `hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensionsRegressionSuite` | TASK+CHECK | `playerProfileSectionExtensionsRegressionTest` |
| `hu.taliann.icesmp.playerprofile.domain.ProfessionProfileStateRegressionSuite` | TASK+CHECK | `professionProfileStateRegressionTest` |
| `hu.taliann.icesmp.playerprofile.http.PlayerProfileHttpContractRegressionSuite` | TASK+CHECK | `playerProfileHttpContractRegressionTest` |
| `hu.taliann.icesmp.playerprofile.persistence.PlayerProfileLifecycleTeardownRegressionSuite` | TASK+CHECK | `playerProfileLifecycleTeardownRegressionTest` |
| `hu.taliann.icesmp.playerprofile.persistence.PlayerProfileRepositoryEnumerationRegressionSuite` | TASK+CHECK | `playerProfileRepositoryEnumerationRegressionTest` |
| `hu.taliann.icesmp.playerprofile.persistence.PlayerProfileYamlRegressionSuite` | TASK+CHECK | `playerProfileYamlRegressionTest` |
| `hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionRegressionSuite` | TASK+CHECK | `playerProfileTransactionRegressionTest` |
| `hu.taliann.icesmp.priest.PriestGameplayRegressionSuite` | TASK+CHECK | `priestGameplayRegressionTest` |
| `hu.taliann.icesmp.priest.PriestProfileRegressionSuite` | TASK+CHECK | `priestProfileRegressionTest` |
| `hu.taliann.icesmp.professions.ProfessionRecipeAuditRegressionSuite` | TASK+CHECK | `professionRecipeAuditRegressionTest` |
| `hu.taliann.icesmp.quest.OnboardingDialogRegressionSuite` | TASK+CHECK | `onboardingDialogRegressionTest` |
| `hu.taliann.icesmp.quest.QuestFrameworkV2RegressionSuite` | TASK+CHECK | `questFrameworkV2RegressionTest` |
| `hu.taliann.icesmp.quests.QuestNpcValidationRegressionSuite` | TASK+CHECK | `questNpcValidationRegressionTest` |
| `hu.taliann.icesmp.relics.RelicTransferOwnershipRegressionSuite` | TASK+CHECK | `relicTransferOwnershipRegressionTest` |
| `hu.taliann.icesmp.resourcepack.ResourcePackRegressionSuite` | TASK+CHECK | `resourcePackRegressionTest` |
| `hu.taliann.icesmp.runtime.EventSpawnSafetyRegressionSuite` | TASK+CHECK | `eventSpawnSafetyRegressionTest` |
| `hu.taliann.icesmp.runtime.RuntimeBugfixRegressionSuite` | TASK+CHECK | `runtimeBugfixRegressionTest` |
| `hu.taliann.icesmp.runtime.RuntimeHardeningRegressionSuite` | TASK+CHECK | `runtimeHardeningRegressionTest` |
| `hu.taliann.icesmp.shaman.ShamanGameplayRegressionSuite` | TASK+CHECK | `shamanGameplayRegressionTest` |
| `hu.taliann.icesmp.shaman.ShamanProfileRegressionSuite` | TASK+CHECK | `shamanProfileRegressionTest` |
| `hu.taliann.icesmp.spells.ActiveKitLifecycleRegressionSuite` | DELEGATED | `` |
| `hu.taliann.icesmp.spells.ClassSpellAuditRegressionSuite` | DELEGATED | `` |
| `hu.taliann.icesmp.spells.DarkClassSpellLifecycleRegressionSuite` | DELEGATED | `` |
| `hu.taliann.icesmp.spells.SpellCastArchitectureRegressionSuite` | DELEGATED | `` |
| `hu.taliann.icesmp.spells.SpellRegistryRegressionSuite` | DELEGATED | `` |
| `hu.taliann.icesmp.territory.TerritoryCapitalRegressionSuite` | TASK+CHECK | `territoryCapitalRegressionTest` |
| `hu.taliann.icesmp.utils.PlatformCapabilitiesRegressionSuite` | TASK+CHECK | `platformCapabilitiesRegressionTest` |
| `hu.taliann.icesmp.warlock.WarlockGameplayRegressionSuite` | TASK+CHECK | `warlockGameplayRegressionTest` |
| `hu.taliann.icesmp.warlock.WarlockProfileRegressionSuite` | TASK+CHECK | `warlockProfileRegressionTest` |
| `hu.taliann.icesmp.warrior.WarriorGameplayRegressionSuite` | TASK+CHECK | `warriorGameplayRegressionTest` |
| `hu.taliann.icesmp.warrior.WarriorProfileRegressionSuite` | TASK+CHECK | `warriorProfileRegressionTest` |
| `hu.taliann.icesmp.wizard.WizardGameplayRegressionSuite` | TASK+CHECK | `wizardGameplayRegressionTest` |
| `hu.taliann.icesmp.wizard.WizardProfileRegressionSuite` | TASK+CHECK | `wizardProfileRegressionTest` |

## Combo audit
- Chains: **4**
- Pairs: **13**
- Every step must be registered and share a valid class/spec loadout.

## Fix summary
- Duplicate spell IDs fail fast while preserving the first registration.
- Typed `Spell.cast(Player, CastModifiers)` is the canonical execution path; the scalar bridge is one-way.
- Damage, healing and shielding scale independently from hard-CC duration.
- PREPARING, NO_TARGET and failed execution remain transaction-neutral.
- Delayed/projectile output carries immutable cast-time modifiers across scheduler hops.
- DARK seal/unseal reconciles grants, active kit, selection and transient projections.
- Shaman totem entity/pulse cleanup is lifecycle-safe.
- Druid `Természeti Erő` and `Harmónia` remain player-facing distinct.
- Invalid `soul-collapse` and `way-of-hundred-fists` combos remain absent.
- Unreachable cross-spec Shaman totem defaults are removed without changing runtime availability.

## Architecture decisions
- PlayerProfile/ClassSpec remains durable authority; combat, summon, pet and totem state are projections.
- Standard spell power scales magnitude only; hard CC duration requires an explicit modifier.
- Delayed/projectile damage carries immutable cast-time modifiers across scheduler hops.
- ConfiguredSpell remains for immediate generic primitives; state/lifecycle identity stays dedicated Java.

## Merge gate evidence
- Strict auditor: **PASS** — exact audit-source SHA `5e2a01ab7933f5dd7287b47c50dc8c67ac8c3b57`
- 420-row CSV: **PASS**
- Java 21 clean build, separate check, explicit JavaExec regressions, deterministic rerun, `git diff --check` and GitHub HEAD readback are final artifact-HEAD execution gates; their absolute-SHA ledger is maintained in PR #115 after the artifact commit.

## Strict result
**PASS**

## NEEDS PLAYTEST
Only live balance/readability tuning belongs here; source-auditable correctness is not intentionally deferred.

Complete per-spell matrix: `docs/audits/class-spell-inventory.csv`.
