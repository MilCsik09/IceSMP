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
    compileOnly(libs.placeholderapi)
    compileOnly("LibsDisguises:LibsDisguises:${libs.versions.libsdisguises.get()}@jar") { isTransitive = false }
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
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

val persistentStoreRegressionTest by registerRegression(
    "persistentStoreRegressionTest", "Runs dependency-free persistent-store lifecycle regression tests.",
    "hu.taliann.icesmp.storage.PersistentStoreCoordinatorRegressionTest")
val devItemRewardRegressionTest by registerRegression(
    "devItemRewardRegressionTest", "Runs focused DEV-item state, retry and scheduler-gate regressions.",
    "hu.taliann.icesmp.managers.DevItemRewardRegressionSuite")
val moderationRegressionTest by registerRegression(
    "moderationRegressionTest", "Runs native moderation plus review concurrency and visibility regressions.",
    "hu.taliann.icesmp.moderation.ModerationReviewRegressionSuite")
val motdRegressionTest by registerRegression(
    "motdRegressionTest", "Runs deterministic MOTD rotation, event-priority and icon regressions.",
    "hu.taliann.icesmp.motd.MotdRegressionSuite")
val sitRegressionTest by registerRegression(
    "sitRegressionTest", "Runs native sit-only policy, reservation and lifecycle regressions.",
    "hu.taliann.icesmp.managers.SitRegressionSuite")
val crateRegressionTest by registerRegression(
    "crateRegressionTest", "Runs native crate validation, settlement, recovery and scheduler regressions.",
    "hu.taliann.icesmp.crates.CrateRegressionSuite")
val configStartupRegressionTest by registerRegression(
    "configStartupRegressionTest", "Runs packaged config, material compatibility and profession parser regressions.",
    "hu.taliann.icesmp.managers.ConfigStartupRegressionSuite")
val operationalConfigMenuRegressionTest by registerRegression(
    "operationalConfigMenuRegressionTest", "Runs operational config menu schema, help, reset and live-apply regressions.",
    "hu.taliann.icesmp.managers.OperationalConfigMenuRegressionSuite")
val advancedConfigMenuRegressionTest by registerRegression(
    "advancedConfigMenuRegressionTest", "Runs advanced text/list input, crate editor schema and live-apply regressions.",
    "hu.taliann.icesmp.managers.AdvancedConfigMenuRegressionSuite")
val afkRegressionTest by registerRegression(
    "afkRegressionTest", "Runs global AFK state, display ordering and product-boundary regressions.",
    "hu.taliann.icesmp.managers.AfkRegressionSuite")
val worldGuardBridgeRegressionTest by registerRegression(
    "worldGuardBridgeRegressionTest", "Runs WorldGuard/WorldEdit bridge and fail-direction regressions.",
    "hu.taliann.icesmp.integration.ProtectionBridgeRegressionSuite")
val territoryCapitalRegressionTest by registerRegression(
    "territoryCapitalRegressionTest", "Runs exact 3D-capital geometry, wiring and consumer regressions.",
    "hu.taliann.icesmp.territory.TerritoryCapitalRegressionSuite")
val hudRegressionTest by registerRegression(
    "hudRegressionTest", "Runs editable native HUD layout and Paper team-colour regressions.",
    "hu.taliann.icesmp.managers.HudRegressionSuite")
val pauseMenuDialogRegressionTest by registerRegression(
    "pauseMenuDialogRegressionTest", "Runs JAR datapack pause-menu website dialog regressions.",
    "hu.taliann.icesmp.dialog.PauseMenuDialogRegressionSuite")
val runtimeBugfixRegressionTest by registerRegression(
    "runtimeBugfixRegressionTest", "Runs pet nametag, lore output, corruption safety and spectator-menu regressions.",
    "hu.taliann.icesmp.runtime.RuntimeBugfixRegressionSuite")
val eventSpawnSafetyRegressionTest by registerRegression(
    "eventSpawnSafetyRegressionTest", "Runs deterministic event distance/search policy regressions.",
    "hu.taliann.icesmp.runtime.EventSpawnSafetyRegressionSuite")
val configGuiTransactionRegressionTest by registerRegression(
    "configGuiTransactionRegressionTest", "Runs staged save/cancel/reset and optimistic-concurrency config GUI regressions.",
    "hu.taliann.icesmp.config.ConfigGuiTransactionRegressionSuite")
val configGuiCoverageRegressionTest by registerRegression(
    "configGuiCoverageRegressionTest", "Validates config schema ↔ GUI allowlist coverage, types, defaults and ranges.",
    "hu.taliann.icesmp.config.ConfigGuiCoverageRegressionSuite")
val professionRecipeAuditRegressionTest by registerRegression(
    "professionRecipeAuditRegressionTest", "Validates deterministic profession recipes, semantic uniqueness and reload cleanup.",
    "hu.taliann.icesmp.professions.ProfessionRecipeAuditRegressionSuite")
val runtimeHardeningRegressionTest by registerRegression(
    "runtimeHardeningRegressionTest", "Runs 2D claim, vanish retracking and DARK mob lifecycle regressions.",
    "hu.taliann.icesmp.runtime.RuntimeHardeningRegressionSuite")
val factionPassiveRegressionTest by registerRegression(
    "factionPassiveRegressionTest", "Runs faction membership, damage, exhaustion, truce and lifecycle regressions.",
    "hu.taliann.icesmp.factions.FactionPassiveRegressionSuite")
val factionPassiveHardeningRegressionTest by registerRegression(
    "factionPassiveHardeningRegressionTest", "Runs pure adapter, retaliation, Blood Moon and signature-food hardening regressions.",
    "hu.taliann.icesmp.factions.FactionPassiveHardeningRegressionSuite")
val factionTreasuryRegressionTest by registerRegression(
    "factionTreasuryRegressionTest", "Runs faction tax origin, legacy migration and collection regressions.",
    "hu.taliann.icesmp.factions.FactionTaxDebtRegressionSuite")
val relicItemRefreshRegressionTest by registerRegression(
    "relicItemRefreshRegressionTest", "Runs Mélytépő modifier idempotency regressions.",
    "hu.taliann.icesmp.items.RelicRefreshRegressionSuite")
val relicRefreshPipelineRegressionTest by registerRegression(
    "relicRefreshPipelineRegressionTest", "Runs per-slot relic refresh isolation and diagnostic regressions.",
    "hu.taliann.icesmp.managers.RelicRefreshPipelineRegressionSuite")
val lifecycleShutdownRegressionTest by registerRegression(
    "lifecycleShutdownRegressionTest", "Runs Folia disable/shutdown scheduler regressions.",
    "hu.taliann.icesmp.lifecycle.LifecycleShutdownRegressionSuite")
val questNpcValidationRegressionTest by registerRegression(
    "questNpcValidationRegressionTest", "Runs quest-NPC exact-name and manual provisioning regressions.",
    "hu.taliann.icesmp.quests.QuestNpcValidationRegressionSuite")
val resourcePackRegressionTest by registerRegression(
    "resourcePackRegressionTest", "Runs additive resource-pack id, hash and immutable URL regressions.",
    "hu.taliann.icesmp.resourcepack.ResourcePackRegressionSuite")

tasks.check {
    dependsOn(
        persistentStoreRegressionTest, devItemRewardRegressionTest, moderationRegressionTest,
        motdRegressionTest, sitRegressionTest, crateRegressionTest,
        configStartupRegressionTest, operationalConfigMenuRegressionTest, advancedConfigMenuRegressionTest,
        eventSpawnSafetyRegressionTest, configGuiTransactionRegressionTest, configGuiCoverageRegressionTest,
        professionRecipeAuditRegressionTest, runtimeHardeningRegressionTest, afkRegressionTest,
        worldGuardBridgeRegressionTest, territoryCapitalRegressionTest, hudRegressionTest,
        pauseMenuDialogRegressionTest, runtimeBugfixRegressionTest, factionPassiveRegressionTest,
        factionPassiveHardeningRegressionTest, factionTreasuryRegressionTest, relicItemRefreshRegressionTest,
        relicRefreshPipelineRegressionTest, lifecycleShutdownRegressionTest, questNpcValidationRegressionTest,
        resourcePackRegressionTest
    )
}
