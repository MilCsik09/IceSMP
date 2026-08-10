import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
    id("java-library")
    alias(libs.plugins.run.paper)
}

val betterHudPython = if (System.getProperty("os.name").lowercase().contains("windows")) "python" else "python3"
val generateBetterHudAssets by tasks.registering(Exec::class) {
    group = "build"
    description = "Regenerates deterministic IceSMP BetterHud pixel assets."
    commandLine(betterHudPython, "scripts/generate_betterhud_assets.py")
}
val generateBetterHudLayout by tasks.registering(Exec::class) {
    group = "build"
    description = "Regenerates the class-agnostic BetterHud layout."
    commandLine(betterHudPython, "scripts/generate_betterhud_layout.py")
}
val generateBetterHudPackage by tasks.registering {
    group = "build"
    description = "Regenerates deterministic IceSMP BetterHud pixel assets and layout."
    dependsOn(generateBetterHudAssets, generateBetterHudLayout)
}

val validateBetterHudPackage by tasks.registering {
    group = "verification"
    description = "Validates BetterHud layout coverage, asset dimensions, progress-mask alpha and budget."
    val deploy = layout.projectDirectory.dir("deploy/betterhud")
    inputs.dir(deploy)
    inputs.files("scripts/generate_betterhud_assets.py", "scripts/generate_betterhud_layout.py")
    doLast {
        val assets = deploy.dir("assets/icesmp").asFile
        val layoutText = deploy.dir("layouts").asFile.listFiles { file -> file.extension == "yml" }
            ?.sortedBy { it.name }?.joinToString("\n") { it.readText(Charsets.UTF_8) }.orEmpty()
        val imageText = deploy.file("images/icesmp-class-images.yml").asFile.readText(Charsets.UTF_8)
        val hudText = deploy.file("huds/icesmp-class-hud.yml").asFile.readText(Charsets.UTF_8)
        listOf("red", "blue", "neutral", "dark").forEach { faction ->
            require(layoutText.contains("frame_$faction:")) { "Missing graphical faction skin: $faction" }
            require(assets.resolve("frame-$faction.png").isFile) { "Missing faction frame asset: $faction" }
            require(assets.resolve("frame-hud-$faction.png").isFile) { "Missing render-safe faction frame: $faction" }
        }
        listOf("warrior", "evoker", "archer", "shaman", "monk", "paladin", "demon_hunter",
            "druid", "priest", "death_knight", "assassin", "warlock", "wizard").forEach { job ->
            require(layoutText.contains("class_$job:")) { "Missing class icon mapping: $job" }
            require(assets.resolve("class-$job.png").isFile) { "Missing class icon asset: $job" }
        }
        require(hudText.contains("icesmp_hud_visible")) { "BetterHud must honour the IceSMP /hud visibility snapshot" }
        require(hudText.contains("y: 0") && !hudText.contains("y: 100")) {
            "The persistent HUD must use the upper-right anchor and stay clear of the hand/hotbar"
        }
        require((1..8).all { layoutText.contains("rune_progress_$it:") }) {
            "Death Knight rune slots/progress are incomplete"
        }
        listOf("blood", "frost", "death").forEach { kind ->
            listOf("ready", "spent", "regenerating", "locked").forEach { state ->
                require(imageText.contains("icesmp_rune_${kind}_${state}:")) {
                    "Death Knight rune artwork is missing: $kind/$state"
                }
                require(layoutText.contains("rune_1_${kind}_${state}:")) {
                    "Death Knight rune state is not mapped into the generic slot layout: $kind/$state"
                }
            }
        }
        require(imageText.contains("number:icesmp_class_slot_8_progress")) {
            "Death Knight slot listener mapping is incomplete"
        }
        require(hudText.contains("icesmp_main_layout")
                && !hudText.contains("icesmp_identity_layout")
                && layoutText.contains("icesmp_main_layout:")
                && layoutText.contains("x: -218") && layoutText.contains("scale: 1.0")) {
            "The faction frame must remain inside the upper-right HUD safe area"
        }
        require(layoutText.contains("outline: true")) {
            "Persistent HUD text must retain its readability outline"
        }
        listOf("money", "event", "level").forEach { icon ->
            require(assets.resolve("icon-$icon.png").isFile) { "Missing HUD utility icon: $icon" }
        }
        listOf("guest", "red", "blue", "neutral", "dark").forEach { theme ->
            require(assets.resolve("popup-$theme.png").isFile) { "Missing proc popup skin: $theme" }
            require(layoutText.contains("popup_$theme:")) { "Missing proc popup layout skin: $theme" }
        }
        val pngs = assets.listFiles { file -> file.extension.equals("png", true) }?.toList().orEmpty()
        require(pngs.isNotEmpty()) { "No BetterHud PNG assets found" }
        require(pngs.sumOf { it.length() } <= 2_500_000L) { "BetterHud runtime asset budget exceeded 2.5 MB" }
        pngs.forEach { file ->
            val image: BufferedImage = ImageIO.read(file) ?: error("Unreadable PNG: $file")
            if (file.name.startsWith("class-") || file.name.startsWith("icon-")
                    || file.name.startsWith("rune-") && file.name != "rune-progress.png") {
                require(image.width == 64 && image.height == 64) {
                    "HUD cutout icons must retain their 64x64 source resolution: $file (${image.width}x${image.height})"
                }
                val alpha = image.alphaRaster ?: error("HUD sprite requires alpha: $file")
                val corners = listOf(0 to 0, 63 to 0, 0 to 63, 63 to 63)
                require(corners.all { (x, y) -> alpha.getSample(x, y, 0) == 0 }) {
                    "HUD sprite has an opaque square/backplate instead of a transparent cutout: $file"
                }
                var minX = image.width
                var minY = image.height
                var maxX = -1
                var maxY = -1
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    if (alpha.getSample(x, y, 0) > 0) {
                        minX = minOf(minX, x); minY = minOf(minY, y)
                        maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
                    }
                }
                require(maxX >= 0 && minX >= 3 && minY >= 3
                        && maxX <= image.width - 4 && maxY <= image.height - 4) {
                    "HUD sprite is empty or clipped against its 64x64 cell: $file"
                }
            }
            if (file.name.startsWith("frame-") && !file.name.startsWith("frame-hud-")) {
                require(image.width >= 640 && image.height >= 400) {
                    "HUD frames must retain high-resolution source detail: $file (${image.width}x${image.height})"
                }
            }
            if (file.name.startsWith("frame-hud-")) {
                require(image.width == 204 && image.height == 126) {
                    "BetterHud runtime frames must stay inside bitmap-provider safe dimensions: $file"
                }
            }
            if (file.name.startsWith("popup-")) {
                require(image.width == 300 && image.height == 72) {
                    "Proc popup skins must retain 300x72 resolution: $file"
                }
            }
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y).ushr(24) and 0xff
                if (file.name.startsWith("resource-") || file.name == "rune-progress.png") {
                    require(alpha == 0 || alpha == 255) {
                        "Progress masks require hard alpha: $file ($x,$y=$alpha)"
                    }
                }
            }
        }
        logger.lifecycle("BetterHud package valid: ${pngs.size} assets, ${pngs.sumOf { it.length() }} bytes")
    }
}

val stageMergedResourcePackForR2 by tasks.registering {
    group = "distribution"
    description = "Validates and stages the BetterHud-generated composite pack for immutable R2 publishing."
    val generatedPack = layout.projectDirectory.file("run/plugins/BetterHud/build.zip")
    val stagedPack = layout.buildDirectory.file("resource-pack/icesmp.zip")
    val metadata = layout.buildDirectory.file("resource-pack/merged.properties")
    inputs.file(generatedPack)
    outputs.files(stagedPack, metadata)
    doLast {
        val source = generatedPack.asFile
        if (!source.isFile) {
            throw GradleException("Missing BetterHud generated pack: $source. Run runFolia until BetterHud reports 'Zip packed' first.")
        }
        ZipFile(source).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            val required = listOf(
                "pack.mcmeta",
                "assets/minecraft/textures/logo/logo.png"
            )
            required.forEach { entry ->
                if (entry !in names) throw GradleException("Composite resource pack is missing $entry")
            }
            if (names.none { it.startsWith("assets/icesmp/") }) {
                throw GradleException("Composite resource pack is missing the IceSMP namespace")
            }
            if (names.none { it.startsWith("assets/betterhud/") || it.startsWith("betterhud_") }) {
                throw GradleException("Composite resource pack is missing the BetterHud layer")
            }
        }
        val target = stagedPack.get().asFile
        target.parentFile.mkdirs()
        ZipInputStream(source.inputStream().buffered()).use { input ->
            ZipOutputStream(target.outputStream().buffered()).use { output ->
                var entry = input.nextEntry
                while (entry != null) {
                    val content = input.readBytes()
                    output.putNextEntry(ZipEntry(entry.name).apply { time = 0L })
                    if (entry.name == "pack.mcmeta") {
                        @Suppress("UNCHECKED_CAST")
                        val root = JsonSlurper().parseText(content.toString(Charsets.UTF_8))
                                as MutableMap<String, Any?>
                        @Suppress("UNCHECKED_CAST")
                        val pack = root["pack"] as MutableMap<String, Any?>
                        pack.remove("supported_formats")
                        pack["pack_format"] = 75
                        pack["min_format"] = 75
                        pack["max_format"] = 75
                        output.write(JsonOutput.toJson(root).toByteArray(Charsets.UTF_8))
                    } else {
                        output.write(content)
                    }
                    output.closeEntry()
                    input.closeEntry()
                    entry = input.nextEntry
                }
            }
        }
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest(target.readBytes()).joinToString("") { "%02x".format(it) }
        metadata.get().asFile.writeText("sha1=$sha1\nsize=${target.length()}\n", Charsets.UTF_8)
        logger.lifecycle("Staged merged R2 pack: ${target.path} (${target.length()} bytes, SHA-1 $sha1)")
    }
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
val platformCapabilitiesRegressionTest = registerRegression(
    "platformCapabilitiesRegressionTest",
    "Runs Paper/Folia scoreboard capability and compact fallback regressions.",
    "hu.taliann.icesmp.utils.PlatformCapabilitiesRegressionSuite")
val classSpecCompatibilityRegressionTest = registerRegression(
    "classSpecCompatibilityRegressionTest",
    "Runs class/spec dependency-lock and portability regressions.",
    "hu.taliann.icesmp.classspec.compat.ClassSpecCompatibilityRegressionSuite")
val betterHudIntegrationRegressionTest = registerRegression(
    "betterHudIntegrationRegressionTest",
    "Runs generic immutable class HUD, PAPI safety and BetterHud fallback regressions.",
    "hu.taliann.icesmp.classspec.integration.BetterHudIntegrationRegressionSuite")
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

tasks.check {
    dependsOn(validateBetterHudPackage)
    dependsOn(
        persistentStoreRegressionTest, devItemRewardRegressionTest, moderationRegressionTest,
        motdRegressionTest, sitRegressionTest, crateRegressionTest,
        configStartupRegressionTest, afkRegressionTest, worldGuardBridgeRegressionTest,
        territoryCapitalRegressionTest, hudRegressionTest, platformCapabilitiesRegressionTest, pauseMenuDialogRegressionTest,
        runtimeBugfixRegressionTest, factionPassiveRegressionTest, factionPassiveHardeningRegressionTest,
        factionTreasuryRegressionTest, relicItemRefreshRegressionTest, relicRefreshPipelineRegressionTest,
        lifecycleShutdownRegressionTest, questNpcValidationRegressionTest, questFrameworkV2RegressionTest,
        onboardingDialogRegressionTest, resourcePackRegressionTest,
        classSpecCompatibilityRegressionTest, betterHudIntegrationRegressionTest,
        classSpecSectionRegressionTest, classSpecApplicationRegressionTest,
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
        warlockGameplayRegressionTest, warlockProfileRegressionTest
    )
}
