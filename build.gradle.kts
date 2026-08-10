plugins {
    id("java-library")
    alias(libs.plugins.run.paper)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.md-5.net/content/repositories/releases/") {
        content { includeGroup("LibsDisguises") }
        metadataSources { artifact() }
    }
}

dependencies {
    compileOnly(libs.folia.api)
    compileOnly(libs.log4j.core)
    compileOnly(libs.placeholderapi)
    compileOnly("LibsDisguises:LibsDisguises:${libs.versions.libsdisguises.get()}@jar") { isTransitive = false }
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G", "-Dpaper.disablePluginRemapping=true")
    }
    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("paper-plugin.yml") { expand(props) }
    }
}

val regressionTest by sourceSets.creating {
    java.srcDir("src/regression/java")
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}

fun registerRegression(name: String, descriptionText: String, mainClassName: String) =
    tasks.register<JavaExec>(name) {
        group = "verification"
        description = descriptionText
        dependsOn(tasks.named(regressionTest.classesTaskName))
        classpath = regressionTest.runtimeClasspath
        mainClass.set(mainClassName)
    }

val persistentStoreRegressionTest = registerRegression(
    "persistentStoreRegressionTest",
    "Runs dependency-free persistent-store lifecycle regression tests.",
    "hu.taliann.icesmp.storage.PersistentStoreCoordinatorRegressionTest")
val devItemRewardRegressionTest = registerRegression(
    "devItemRewardRegressionTest",
    "Runs focused DEV-item state, retry and scheduler-gate regressions.",
    "hu.taliann.icesmp.managers.DevItemRewardRegressionSuite")
val moderationRegressionTest = registerRegression(
    "moderationRegressionTest",
    "Runs native moderation plus review concurrency and visibility regressions.",
    "hu.taliann.icesmp.moderation.ModerationReviewRegressionSuite")
val motdRegressionTest = registerRegression(
    "motdRegressionTest",
    "Runs deterministic MOTD rotation, event-priority and icon regressions.",
    "hu.taliann.icesmp.motd.MotdRegressionSuite")
val sitRegressionTest = registerRegression(
    "sitRegressionTest",
    "Runs native sit-only policy, reservation and lifecycle regressions.",
    "hu.taliann.icesmp.managers.SitRegressionSuite")
val crateRegressionTest = registerRegression(
    "crateRegressionTest",
    "Runs native crate validation, settlement, recovery and scheduler regressions.",
    "hu.taliann.icesmp.crates.CrateRegressionSuite")
val configStartupRegressionTest = registerRegression(
    "configStartupRegressionTest",
    "Runs packaged config, material compatibility and profession parser regressions.",
    "hu.taliann.icesmp.managers.ConfigStartupRegressionSuite")
val afkRegressionTest = registerRegression(
    "afkRegressionTest",
    "Runs global AFK state, display ordering and product-boundary regressions.",
    "hu.taliann.icesmp.managers.AfkRegressionSuite")
val worldGuardBridgeRegressionTest = registerRegression(
    "worldGuardBridgeRegressionTest",
    "Runs WorldGuard/WorldEdit bridge and fail-direction regressions.",
    "hu.taliann.icesmp.integration.ProtectionBridgeRegressionSuite")
val territoryCapitalRegressionTest = registerRegression(
    "territoryCapitalRegressionTest",
    "Runs exact 3D-capital geometry, wiring and consumer regressions.",
    "hu.taliann.icesmp.territory.TerritoryCapitalRegressionSuite")
val hudRegressionTest = registerRegression(
    "hudRegressionTest",
    "Runs editable native HUD layout and Paper team-colour regressions.",
    "hu.taliann.icesmp.managers.HudRegressionSuite")
val classSpecCompatibilityRegressionTest = registerRegression(
    "classSpecCompatibilityRegressionTest",
    "Runs class/spec dependency-lock and portability regressions.",
    "hu.taliann.icesmp.classspec.compat.ClassSpecCompatibilityRegressionSuite")
val classSpecApplicationRegressionTest = registerRegression(
    "classSpecApplicationRegressionTest",
    "Runs Profile v2 mutation, DARK gate and fail-closed application regressions.",
    "hu.taliann.icesmp.classspec.application.ClassSpecApplicationRegressionSuite")
val classSpecSectionRegressionTest = registerRegression(
    "classSpecSectionRegressionTest",
    "Runs ClassSpec section invariants without opaque binary persistence.",
    "hu.taliann.icesmp.classspec.profile.ClassSpecSectionV2RegressionSuite")
val classSpecLifecycleRegressionTest = registerRegression(
    "classSpecLifecycleRegressionTest",
    "Runs PlayerProfile-backed class/spec join, logout and disable lifecycle regressions.",
    "hu.taliann.icesmp.classspec.application.ClassSpecSectionLifecycleRegressionSuite")
val playerProfileDomainRegressionTest = registerRegression(
    "playerProfileDomainRegressionTest",
    "Runs modular PlayerProfile root/section domain regressions.",
    "hu.taliann.icesmp.playerprofile.domain.PlayerProfileDomainRegressionSuite")
val playerProfileSectionExtensionsRegressionTest = registerRegression(
    "playerProfileSectionExtensionsRegressionTest",
    "Runs immutable extension-copy regressions across every PlayerProfile section.",
    "hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensionsRegressionSuite")
val spellMasteryTransactionRegressionTest = registerRegression(
    "spellMasteryTransactionRegressionTest",
    "Runs exact-once spell mastery wallet/receipt recovery regressions.",
    "hu.taliann.icesmp.managers.SpellMasteryTransactionRegressionSuite")
val professionProfileStateRegressionTest = registerRegression(
    "professionProfileStateRegressionTest",
    "Runs PlayerProfile profession slot, XP, level and recipe authority regressions.",
    "hu.taliann.icesmp.playerprofile.domain.ProfessionProfileStateRegressionSuite")
val playerProfileAchievementRegressionTest = registerRegression(
    "playerProfileAchievementRegressionTest",
    "Runs achievement, bestiary, hidden-spot and reward-receipt authority regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStoreRegressionSuite")
val playerProfileDailyQuestRegressionTest = registerRegression(
    "playerProfileDailyQuestRegressionTest",
    "Runs daily/weekly quest period, streak and reward-receipt regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileDailyQuestStoreRegressionSuite")
val playerProfileEconomyRegressionTest = registerRegression(
    "playerProfileEconomyRegressionTest",
    "Runs wallet CAS, durable operation and restart regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileEconomyStoreRegressionSuite")
val playerProfileFactionAuthorityRegressionTest = registerRegression(
    "playerProfileFactionAuthorityRegressionTest",
    "Runs faction, sinner, whisper and cross-section switch authority regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionAuthorityRegressionSuite")
val playerProfileFactionFoodRegressionTest = registerRegression(
    "playerProfileFactionFoodRegressionTest",
    "Runs faction-food timestamp and faction-binding authority regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionFoodStoreRegressionSuite")
val playerProfileFullAuthorityRegressionTest = registerRegression(
    "playerProfileFullAuthorityRegressionTest",
    "Runs final PlayerProfile operation, budget, wallet and bounty recovery regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileFullAuthorityRegressionSuite")
val playerProfileIntroRegressionTest = registerRegression(
    "playerProfileIntroRegressionTest",
    "Runs intro/onboarding and interrupted cinematic recovery regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileIntroStoreRegressionSuite")
val playerProfileModerationRegressionTest = registerRegression(
    "playerProfileModerationRegressionTest",
    "Runs moderation reference/summary CAS and restart durability regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileModerationStoreRegressionSuite")
val playerProfileCrateRegressionTest = registerRegression(
    "playerProfileCrateRegressionTest",
    "Runs crate settlement receipt idempotency and reset regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileCrateStoreRegressionSuite")
val playerProfileDeathEscrowRegressionTest = registerRegression(
    "playerProfileDeathEscrowRegressionTest",
    "Runs death escrow deposit, exact-once claim and durability regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileDeathEscrowStoreRegressionSuite")
val playerProfileWeeklyGoalRegressionTest = registerRegression(
    "playerProfileWeeklyGoalRegressionTest",
    "Runs weekly guild-goal contribution, award idempotency and atomic claim regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileWeeklyGoalStoreRegressionSuite")
val playerProfileQuestRegressionTest = registerRegression(
    "playerProfileQuestRegressionTest",
    "Runs main quest progress, cooldown, completion and reward settlement regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileQuestStoreRegressionSuite")
val playerProfileStatisticsRegressionTest = registerRegression(
    "playerProfileStatisticsRegressionTest",
    "Runs durable statistics and rebuildable leaderboard projection regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileStatisticsStoreRegressionSuite")
val playerProfileTaxRegressionTest = registerRegression(
    "playerProfileTaxRegressionTest",
    "Runs tax debt, wallet, treasury and sinner outbox recovery regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileTaxStoreRegressionSuite")
val playerProfileRepositoryEnumerationRegressionTest = registerRegression(
    "playerProfileRepositoryEnumerationRegressionTest",
    "Runs storage-independent durable profile owner enumeration regressions.",
    "hu.taliann.icesmp.playerprofile.persistence.PlayerProfileRepositoryEnumerationRegressionSuite")
val playerProfileYamlRegressionTest = registerRegression(
    "playerProfileYamlRegressionTest",
    "Runs structured section YAML, manifest, CAS, quarantine and shutdown regressions.",
    "hu.taliann.icesmp.playerprofile.persistence.PlayerProfileYamlRegressionSuite")
val playerProfileTransactionRegressionTest = registerRegression(
    "playerProfileTransactionRegressionTest",
    "Runs cross-section WAL, idempotency and restart-recovery regressions.",
    "hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionRegressionSuite")
val playerProfileApiRegressionTest = registerRegression(
    "playerProfileApiRegressionTest",
    "Runs internal/API DTO, authentication, ETag, rate-limit and shutdown regressions.",
    "hu.taliann.icesmp.playerprofile.api.PlayerProfileApiRegressionSuite")
val classRelicRegressionTest = registerRegression(
    "classRelicRegressionTest",
    "Runs class-relic registry, activation, resonance-routing, transfer and Evoker-migration regressions.",
    "hu.taliann.icesmp.classrelic.ClassRelicRegressionSuite")
val relicTransferOwnershipRegressionTest = registerRegression(
    "relicTransferOwnershipRegressionTest",
    "Runs stale-owner, durable rollback and concurrent PvP relic transfer regressions.",
    "hu.taliann.icesmp.relics.RelicTransferOwnershipRegressionSuite")
val bestiaryRegressionTest = registerRegression(
    "bestiaryRegressionTest",
    "Runs bestiary denominator, boss-archetype canon and kill-recording contract regressions.",
    "hu.taliann.icesmp.managers.BestiaryRegressionSuite")
val playerProfileHttpContractRegressionTest = registerRegression(
    "playerProfileHttpContractRegressionTest",
    "Runs real-routing HTTP by-name auth-order, SELF/ADMIN scope and stop-idempotency regressions.",
    "hu.taliann.icesmp.playerprofile.http.PlayerProfileHttpContractRegressionSuite")
val playerProfileLifecycleTeardownRegressionTest = registerRegression(
    "playerProfileLifecycleTeardownRegressionTest",
    "Runs partial-startup/shutdown-failure resource-teardown regressions.",
    "hu.taliann.icesmp.playerprofile.persistence.PlayerProfileLifecycleTeardownRegressionSuite")
val respecTransactionRegressionTest = registerRegression(
    "respecTransactionRegressionTest",
    "Runs Profile v2 respec WAL, restart recovery and crash-decision regressions.",
    "hu.taliann.icesmp.classspec.transaction.RespecTransactionRegressionSuite")
val spellGrantLedgerRegressionTest = registerRegression(
    "spellGrantLedgerRegressionTest",
    "Runs explicit BASE/SPEC/TALENT/QUEST/ADMIN spell provenance regressions.",
    "hu.taliann.icesmp.classspec.domain.SpellGrantLedgerRegressionSuite")
val pauseMenuDialogRegressionTest = registerRegression(
    "pauseMenuDialogRegressionTest",
    "Runs JAR datapack pause-menu website dialog regressions.",
    "hu.taliann.icesmp.dialog.PauseMenuDialogRegressionSuite")
val runtimeBugfixRegressionTest = registerRegression(
    "runtimeBugfixRegressionTest",
    "Runs pet nametag, lore output, corruption safety and spectator-menu regressions.",
    "hu.taliann.icesmp.runtime.RuntimeBugfixRegressionSuite")
val factionPassiveRegressionTest = registerRegression(
    "factionPassiveRegressionTest",
    "Runs faction membership, damage, exhaustion, truce and lifecycle regressions.",
    "hu.taliann.icesmp.factions.FactionPassiveRegressionSuite")
val factionPassiveHardeningRegressionTest = registerRegression(
    "factionPassiveHardeningRegressionTest",
    "Runs pure adapter, retaliation, Blood Moon and signature-food hardening regressions.",
    "hu.taliann.icesmp.factions.FactionPassiveHardeningRegressionSuite")
val factionTreasuryRegressionTest = registerRegression(
    "factionTreasuryRegressionTest",
    "Runs faction tax origin, collection and recovery regressions.",
    "hu.taliann.icesmp.factions.FactionTaxDebtRegressionSuite")
val relicItemRefreshRegressionTest = registerRegression(
    "relicItemRefreshRegressionTest",
    "Runs Mélytépő modifier idempotency regressions.",
    "hu.taliann.icesmp.items.RelicRefreshRegressionSuite")
val relicRefreshPipelineRegressionTest = registerRegression(
    "relicRefreshPipelineRegressionTest",
    "Runs per-slot relic refresh isolation and diagnostic regressions.",
    "hu.taliann.icesmp.managers.RelicRefreshPipelineRegressionSuite")
val lifecycleShutdownRegressionTest = registerRegression(
    "lifecycleShutdownRegressionTest",
    "Runs Folia disable/shutdown scheduler regressions.",
    "hu.taliann.icesmp.lifecycle.LifecycleShutdownRegressionSuite")
val questNpcValidationRegressionTest = registerRegression(
    "questNpcValidationRegressionTest",
    "Runs quest-NPC exact-name and manual provisioning regressions.",
    "hu.taliann.icesmp.quests.QuestNpcValidationRegressionSuite")
val questFrameworkV2RegressionTest = registerRegression(
    "questFrameworkV2RegressionTest",
    "Runs quest source-authority, graph-validator, choice-token and migration regressions.",
    "hu.taliann.icesmp.quest.QuestFrameworkV2RegressionSuite")
val onboardingDialogRegressionTest = registerRegression(
    "onboardingDialogRegressionTest",
    "Runs first-join welcome dialog copy, legacy-stock migration and custom-preservation regressions.",
    "hu.taliann.icesmp.quest.OnboardingDialogRegressionSuite")
val resourcePackRegressionTest = registerRegression(
    "resourcePackRegressionTest",
    "Runs additive resource-pack id, hash and immutable URL regressions.",
    "hu.taliann.icesmp.resourcepack.ResourcePackRegressionSuite")
val advancedConfigMenuRegressionTest = registerRegression(
    "advancedConfigMenuRegressionTest",
    "Runs advanced text/list input, crate editor schema and live-apply regressions.",
    "hu.taliann.icesmp.managers.AdvancedConfigMenuRegressionSuite")
val configGuiCoverageRegressionTest = registerRegression(
    "configGuiCoverageRegressionTest",
    "Validates config schema ↔ GUI allowlist coverage, types, defaults and ranges.",
    "hu.taliann.icesmp.config.ConfigGuiCoverageRegressionSuite")
val configGuiTransactionRegressionTest = registerRegression(
    "configGuiTransactionRegressionTest",
    "Runs staged save/cancel/reset and optimistic-concurrency config GUI regressions.",
    "hu.taliann.icesmp.config.ConfigGuiTransactionRegressionSuite")
val eventSpawnSafetyRegressionTest = registerRegression(
    "eventSpawnSafetyRegressionTest",
    "Runs deterministic event distance/search policy regressions.",
    "hu.taliann.icesmp.runtime.EventSpawnSafetyRegressionSuite")
val factionDisplayColorRegressionTest = registerRegression(
    "factionDisplayColorRegressionTest",
    "Runs central faction display palette and consumer-contract regressions.",
    "hu.taliann.icesmp.factions.FactionDisplayColorRegressionSuite")
val inventoryReadWriteRegressionTest = registerRegression(
    "inventoryReadWriteRegressionTest",
    "Runs invsee single-writer, donation gesture and rollback regressions.",
    "hu.taliann.icesmp.inventory.InventoryReadWriteRegressionSuite")
val operationalConfigMenuRegressionTest = registerRegression(
    "operationalConfigMenuRegressionTest",
    "Runs operational config menu schema, help, reset and live-apply regressions.",
    "hu.taliann.icesmp.managers.OperationalConfigMenuRegressionSuite")
val professionRecipeAuditRegressionTest = registerRegression(
    "professionRecipeAuditRegressionTest",
    "Validates deterministic profession recipes, semantic uniqueness and reload cleanup.",
    "hu.taliann.icesmp.professions.ProfessionRecipeAuditRegressionSuite")
val runtimeHardeningRegressionTest = registerRegression(
    "runtimeHardeningRegressionTest",
    "Runs 2D claim, vanish retracking and DARK mob lifecycle regressions.",
    "hu.taliann.icesmp.runtime.RuntimeHardeningRegressionSuite")

tasks.check {
    dependsOn(
        persistentStoreRegressionTest, devItemRewardRegressionTest, moderationRegressionTest,
        motdRegressionTest, sitRegressionTest, crateRegressionTest,
        configStartupRegressionTest, afkRegressionTest, worldGuardBridgeRegressionTest,
        territoryCapitalRegressionTest, hudRegressionTest, pauseMenuDialogRegressionTest,
        runtimeBugfixRegressionTest, factionPassiveRegressionTest, factionPassiveHardeningRegressionTest,
        factionTreasuryRegressionTest, relicItemRefreshRegressionTest, relicRefreshPipelineRegressionTest,
        lifecycleShutdownRegressionTest, questNpcValidationRegressionTest, questFrameworkV2RegressionTest,
        onboardingDialogRegressionTest, resourcePackRegressionTest,
        classSpecCompatibilityRegressionTest, classSpecSectionRegressionTest, classSpecApplicationRegressionTest,
        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest, playerProfileSectionExtensionsRegressionTest,
        spellMasteryTransactionRegressionTest, professionProfileStateRegressionTest, playerProfileAchievementRegressionTest,
        playerProfileDailyQuestRegressionTest, playerProfileEconomyRegressionTest, playerProfileFactionAuthorityRegressionTest,
        playerProfileFactionFoodRegressionTest, playerProfileFullAuthorityRegressionTest, playerProfileIntroRegressionTest,
        playerProfileModerationRegressionTest, playerProfileCrateRegressionTest, playerProfileDeathEscrowRegressionTest,
        playerProfileQuestRegressionTest, playerProfileWeeklyGoalRegressionTest, playerProfileStatisticsRegressionTest,
        playerProfileTaxRegressionTest, playerProfileRepositoryEnumerationRegressionTest, playerProfileYamlRegressionTest,
        playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
        playerProfileHttpContractRegressionTest, playerProfileLifecycleTeardownRegressionTest,
        bestiaryRegressionTest, classRelicRegressionTest, relicTransferOwnershipRegressionTest,
        respecTransactionRegressionTest, spellGrantLedgerRegressionTest, runtimeHardeningRegressionTest,
        eventSpawnSafetyRegressionTest, configGuiTransactionRegressionTest, configGuiCoverageRegressionTest,
        professionRecipeAuditRegressionTest, inventoryReadWriteRegressionTest,
        operationalConfigMenuRegressionTest, advancedConfigMenuRegressionTest, factionDisplayColorRegressionTest
    )
}
