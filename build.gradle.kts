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

data class LockedDevPlugin(
    val id: String,
    val runtimeRole: String,
    val devProvision: String,
    val devProject: String?,
    val devVersionId: String?,
    val devLocalFile: String?
)

data class ClassSpecDependencyLock(
    val minecraft: String,
    val plugins: List<LockedDevPlugin>
)

fun yamlScalar(raw: String): String = raw.substringBefore(" #").trim()
    .removeSurrounding("\"").removeSurrounding("'")

fun parseClassSpecDependencyLock(file: java.io.File): ClassSpecDependencyLock {
    require(file.isFile) { "Missing class/spec dependency lock: $file" }
    val text = file.readText()
    val schema = Regex("(?m)^schema-version:\\s*(\\d+)\\s*$")
        .find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?: error("class-spec-dependencies.lock.yml has no schema-version")
    require(schema == 2) { "Unsupported class/spec dependency lock schema $schema; expected 2" }
    val minecraft = Regex("(?m)^\\s{2}minecraft:\\s*[\\\"']?([^\\\"'#\\s]+)[\\\"']?\\s*$")
        .find(text)?.groupValues?.get(1)
        ?: error("class-spec-dependencies.lock.yml has no target.minecraft")

    val plugins = mutableListOf<LockedDevPlugin>()
    var inPlugins = false
    var currentId: String? = null
    var fields = linkedMapOf<String, String>()

    fun flush() {
        val id = currentId ?: return
        val role = fields["runtime-role"]
            ?: error("class-spec dependency '$id' has no runtime-role")
        require(role in setOf("required-runtime", "optional-integration", "dev-only", "validation-only")) {
            "class-spec dependency '$id' has invalid runtime-role '$role'"
        }
        val provision = fields["dev-provision"] ?: "disabled"
        require(provision in setOf("modrinth", "local", "disabled")) {
            "class-spec dependency '$id' has invalid dev-provision '$provision'"
        }
        val project = fields["dev-project"]
        val versionId = fields["dev-version-id"]
        val localFile = fields["dev-local-file"]
        if (provision == "modrinth") {
            require(!project.isNullOrBlank() && !versionId.isNullOrBlank()) {
                "class-spec dependency '$id' needs dev-project and dev-version-id for Modrinth provisioning"
            }
        }
        if (provision == "local") {
            require(!localFile.isNullOrBlank()) {
                "class-spec dependency '$id' needs dev-local-file for local provisioning"
            }
        }
        plugins += LockedDevPlugin(id, role, provision, project, versionId, localFile)
    }

    file.forEachLine { raw ->
        val trimmed = raw.trim()
        if (trimmed == "plugins:") {
            inPlugins = true
            return@forEachLine
        }
        if (!inPlugins || trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
        val first = raw.indexOfFirst { !it.isWhitespace() }
        val indent = if (first < 0) raw.length else first
        if (indent == 2 && trimmed.endsWith(":")) {
            flush()
            currentId = trimmed.dropLast(1)
            fields = linkedMapOf()
        } else if (indent == 4 && currentId != null) {
            val separator = trimmed.indexOf(':')
            if (separator > 0) {
                fields[trimmed.substring(0, separator)] = yamlScalar(trimmed.substring(separator + 1))
            }
        }
    }
    flush()
    require(plugins.isNotEmpty()) { "class-spec-dependencies.lock.yml contains no plugins" }
    return ClassSpecDependencyLock(minecraft, plugins.toList())
}

val classSpecDependencyLock = parseClassSpecDependencyLock(
    layout.projectDirectory.file("src/main/resources/class-spec-dependencies.lock.yml").asFile
)
val configuredMinecraftVersion = libs.versions.minecraft.get()
require(classSpecDependencyLock.minecraft == configuredMinecraftVersion) {
    "class-spec dependency lock targets ${classSpecDependencyLock.minecraft}, " +
        "but Gradle targets $configuredMinecraftVersion"
}

val localDevPluginDirectory = providers.gradleProperty("icesmpDevPluginDir")
    .orElse(providers.environmentVariable("ICESMP_DEV_PLUGIN_DIR"))
val localClassSpecPlugins = classSpecDependencyLock.plugins.filter { it.devProvision == "local" }
val prepareLocalClassSpecPlugins = tasks.register("prepareLocalClassSpecPlugins") {
    group = "icesmp development"
    description = "Copies licensed/local class-spec dev plugins into run/plugins when a local directory is configured."
    doLast {
        if (localClassSpecPlugins.isEmpty()) return@doLast
        val configured = localDevPluginDirectory.orNull
        if (configured.isNullOrBlank()) {
            logger.lifecycle(
                "IceSMP local dev plugins skipped. Set -PicesmpDevPluginDir=<dir> or " +
                    "ICESMP_DEV_PLUGIN_DIR to provision licensed/local artifacts."
            )
            return@doLast
        }
        val sourceDirectory = project.file(configured)
        require(sourceDirectory.isDirectory) { "IceSMP dev plugin directory does not exist: $sourceDirectory" }
        val targetDirectory = layout.projectDirectory.dir("run/plugins").asFile
        targetDirectory.mkdirs()
        localClassSpecPlugins.forEach { locked ->
            val fileName = requireNotNull(locked.devLocalFile)
            val source = sourceDirectory.resolve(fileName)
            require(source.isFile) { "Missing local dev plugin for ${locked.id}: $source" }
            source.copyTo(targetDirectory.resolve(fileName), overwrite = true)
            logger.lifecycle("Provisioned local IceSMP dev plugin ${locked.id}: $fileName")
        }
    }
}

tasks {
    runServer {
        dependsOn(prepareLocalClassSpecPlugins)
        minecraftVersion(classSpecDependencyLock.minecraft)
        jvmArgs("-Xms2G", "-Xmx2G", "-Dpaper.disablePluginRemapping=true")
        downloadPlugins {
            classSpecDependencyLock.plugins
                .filter { it.devProvision == "modrinth" }
                .forEach { locked ->
                    modrinth(requireNotNull(locked.devProject), requireNotNull(locked.devVersionId))
                }
        }
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
val warriorGameplayRegressionTest = registerRegression(
    "warriorGameplayRegressionTest",
    "Runs concrete Harcos Csatatempo, Berserker and Guardian state regressions.",
    "hu.taliann.icesmp.warrior.WarriorGameplayRegressionSuite")
val warriorProfileRegressionTest = registerRegression(
    "warriorProfileRegressionTest",
    "Runs Profile v2 Warrior second-spec, doctrine, mastery and capstone isolation regressions.",
    "hu.taliann.icesmp.warrior.WarriorProfileRegressionSuite")
val evokerGameplayRegressionTest = registerRegression(
    "evokerGameplayRegressionTest",
    "Runs concrete Sárkányidéző Felerősítés, Eszencia, Visszhang and Időlenyomat state regressions.",
    "hu.taliann.icesmp.evoker.EvokerGameplayRegressionSuite")
val evokerProfileRegressionTest = registerRegression(
    "evokerProfileRegressionTest",
    "Runs Profile v2 gameplay-v2 allowlist, Evoker second-spec and slot-isolation regressions.",
    "hu.taliann.icesmp.evoker.EvokerProfileRegressionSuite")
val archerGameplayRegressionTest = registerRegression(
    "archerGameplayRegressionTest",
    "Runs concrete Íjász Szélolvasás, Pontossági lánc and Kötelék state regressions.",
    "hu.taliann.icesmp.archer.ArcherGameplayRegressionSuite")
val archerProfileRegressionTest = registerRegression(
    "archerProfileRegressionTest",
    "Runs Profile v2 Archer allowlist, stable-roster and slot-isolation regressions.",
    "hu.taliann.icesmp.archer.ArcherProfileRegressionSuite")
val shamanGameplayRegressionTest = registerRegression(
    "shamanGameplayRegressionTest",
    "Runs concrete Sámán Totemkerék, Rezonancia, Maelstrom and Dagály/Apály state regressions.",
    "hu.taliann.icesmp.shaman.ShamanGameplayRegressionSuite")
val shamanProfileRegressionTest = registerRegression(
    "shamanProfileRegressionTest",
    "Runs Profile v2 Shaman allowlist, three-spec two-loadout and slot-isolation regressions.",
    "hu.taliann.icesmp.shaman.ShamanProfileRegressionSuite")
val monkGameplayRegressionTest = registerRegression(
    "monkGameplayRegressionTest",
    "Runs concrete Szerzetes Áramlás, Harcművészeti Lánc, Stagger and Ködszál state regressions.",
    "hu.taliann.icesmp.monk.MonkGameplayRegressionSuite")
val monkProfileRegressionTest = registerRegression(
    "monkProfileRegressionTest",
    "Runs Profile v2 Monk allowlist and slot-isolation regressions.",
    "hu.taliann.icesmp.monk.MonkProfileRegressionSuite")
val paladinGameplayRegressionTest = registerRegression(
    "paladinGameplayRegressionTest",
    "Runs concrete Paplovag Meggyőződés, Ítélet-jelek and Pajzstöltet state regressions.",
    "hu.taliann.icesmp.paladin.PaladinGameplayRegressionSuite")
val paladinProfileRegressionTest = registerRegression(
    "paladinProfileRegressionTest",
    "Runs Profile v2 Paladin allowlist and slot-isolation regressions.",
    "hu.taliann.icesmp.paladin.PaladinProfileRegressionSuite")
val demonHunterGameplayRegressionTest = registerRegression(
    "demonHunterGameplayRegressionTest",
    "Runs concrete Démonvadász Kárhozat-terhelés, Lélektöredék and Fájdalom/Sigil state regressions.",
    "hu.taliann.icesmp.demonhunter.DemonHunterGameplayRegressionSuite")
val demonHunterProfileRegressionTest = registerRegression(
    "demonHunterProfileRegressionTest",
    "Runs Profile v2 Demon Hunter allowlist and slot-isolation regressions.",
    "hu.taliann.icesmp.demonhunter.DemonHunterProfileRegressionSuite")
val druidGameplayRegressionTest = registerRegression(
    "druidGameplayRegressionTest",
    "Runs concrete Druida harmony/season, combo, eclipse, bark and seed-ripening state regressions.",
    "hu.taliann.icesmp.druid.DruidGameplayRegressionSuite")
val druidProfileRegressionTest = registerRegression(
    "druidProfileRegressionTest",
    "Runs Profile v2 Druid allowlist and four-spec slot-isolation regressions.",
    "hu.taliann.icesmp.druid.DruidProfileRegressionSuite")
val priestGameplayRegressionTest = registerRegression(
    "priestGameplayRegressionTest",
    "Runs concrete Pap Litánia, Engesztelés guard, Velő/Osszárium and Őrület threshold regressions.",
    "hu.taliann.icesmp.priest.PriestGameplayRegressionSuite")
val priestProfileRegressionTest = registerRegression(
    "priestProfileRegressionTest",
    "Runs Profile v2 Priest allowlist, DARK gate and slot-isolation regressions.",
    "hu.taliann.icesmp.priest.PriestProfileRegressionSuite")
val deathKnightGameplayRegressionTest = registerRegression(
    "deathKnightGameplayRegressionTest",
    "Runs concrete Halállovag Rúnakör, Vér Emlékezete, Fagyjel and Dögvész/ghoul-mutation regressions.",
    "hu.taliann.icesmp.deathknight.DeathKnightGameplayRegressionSuite")
val deathKnightProfileRegressionTest = registerRegression(
    "deathKnightProfileRegressionTest",
    "Runs Profile v2 Death Knight allowlist, DARK gate and slot-isolation regressions.",
    "hu.taliann.icesmp.deathknight.DeathKnightProfileRegressionSuite")
val assassinGameplayRegressionTest = registerRegression(
    "assassinGameplayRegressionTest",
    "Runs concrete Orgyilkos Lehetőség, Toxinkészlet, stealth-limit and capped plague regressions.",
    "hu.taliann.icesmp.assassin.AssassinGameplayRegressionSuite")
val assassinProfileRegressionTest = registerRegression(
    "assassinProfileRegressionTest",
    "Runs Profile v2 Assassin allowlist, DARK gate and slot-isolation regressions.",
    "hu.taliann.icesmp.assassin.AssassinProfileRegressionSuite")
val warlockGameplayRegressionTest = registerRegression(
    "warlockGameplayRegressionTest",
    "Runs concrete Boszorkánymester Paktum/Lélekadósság, Átokgrimoár, ember-burst and roster regressions.",
    "hu.taliann.icesmp.warlock.WarlockGameplayRegressionSuite")
val warlockProfileRegressionTest = registerRegression(
    "warlockProfileRegressionTest",
    "Runs Profile v2 Warlock allowlist, DARK gate and slot-isolation regressions.",
    "hu.taliann.icesmp.warlock.WarlockProfileRegressionSuite")
val wizardGameplayRegressionTest = registerRegression(
    "wizardGameplayRegressionTest",
    "Runs concrete Varázsló Rúnaszövés table, attunement convergence/crown and bounded court regressions.",
    "hu.taliann.icesmp.wizard.WizardGameplayRegressionSuite")
val wizardProfileRegressionTest = registerRegression(
    "wizardProfileRegressionTest",
    "Runs Profile v2 Wizard allowlist, DARK gate and slot-isolation regressions.",
    "hu.taliann.icesmp.wizard.WizardProfileRegressionSuite")

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
        operationalConfigMenuRegressionTest, advancedConfigMenuRegressionTest, factionDisplayColorRegressionTest,
        warriorGameplayRegressionTest, warriorProfileRegressionTest,
        evokerGameplayRegressionTest, evokerProfileRegressionTest,
        archerGameplayRegressionTest, archerProfileRegressionTest,
        shamanGameplayRegressionTest, shamanProfileRegressionTest,
        monkGameplayRegressionTest, monkProfileRegressionTest,
        paladinGameplayRegressionTest, paladinProfileRegressionTest,
        demonHunterGameplayRegressionTest, demonHunterProfileRegressionTest,
        druidGameplayRegressionTest, druidProfileRegressionTest,
        priestGameplayRegressionTest, priestProfileRegressionTest,
        deathKnightGameplayRegressionTest, deathKnightProfileRegressionTest,
        assassinGameplayRegressionTest, assassinProfileRegressionTest,
        warlockGameplayRegressionTest, warlockProfileRegressionTest,
        wizardGameplayRegressionTest, wizardProfileRegressionTest
    )
}
