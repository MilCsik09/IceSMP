plugins {
    id("java-library")
    alias(libs.plugins.run.paper)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // Soft-dependenciák repói:
    maven("https://repo.extendedclip.com/releases/")        // PlaceholderAPI
    maven("https://repo.md-5.net/content/repositories/releases/") {
        content { includeGroup("LibsDisguises") }
        metadataSources { artifact() } // LibsDisguises 10.0.44 has a broken parent POM.
    }
}

dependencies {
    compileOnly(libs.folia.api)
    // Opcionális integrációk — futásidőben soft-depend (a kód ellenőrzi a jelenlétüket):
    compileOnly(libs.placeholderapi)   // %icesmp_...% placeholderek (pl. a TAB megjeleníti az Erő-csíkot)
    // A DruidDisguise reflexiós híd ehhez tartozik (Druida-formák vizuálja). isTransitive=false:
    // csak maga az API kell fordításhoz — a transitívjai (ProtocolLib/Spigot) nélkül is fordul,
    // így a build nem törik el, ha azok repói nem érhetők el.
    compileOnly("LibsDisguises:LibsDisguises:${libs.versions.libsdisguises.get()}@jar") { isTransitive = false }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G", "-Dpaper.disablePluginRemapping=true")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}

val regressionTest by sourceSets.creating {
    java.srcDir("src/regression/java")
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}

val persistentStoreRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs dependency-free persistent-store lifecycle regression tests."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.storage.PersistentStoreCoordinatorRegressionTest")
}

val devItemRewardRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs focused DEV-item state, retry and scheduler-gate regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.managers.DevItemRewardRegressionSuite")
}

val moderationRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs native moderation plus review concurrency and visibility regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.moderation.ModerationReviewRegressionSuite")
}

val motdRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs deterministic MOTD rotation, event-priority and icon regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.motd.MotdRegressionSuite")
}

val sitRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs native sit-only policy, reservation and lifecycle regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.managers.SitRegressionSuite")
}

val crateRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs native crate validation, settlement, recovery and scheduler regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.crates.CrateRegressionSuite")
}

val configStartupRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs packaged config, material compatibility and profession parser regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.managers.ConfigStartupRegressionSuite")
}

val afkRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs global AFK state, display ordering and product-boundary regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.managers.AfkRegressionSuite")
}

val worldGuardBridgeRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs WorldGuard/WorldEdit bridge and fail-direction regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.integration.ProtectionBridgeRegressionSuite")
}

val territoryCapitalRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs exact 3D-capital geometry, wiring and consumer regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.territory.TerritoryCapitalRegressionSuite")
}

val hudRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs editable native HUD layout and Paper team-colour regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.managers.HudRegressionSuite")
}


val classSpecCompatibilityRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs class/spec dependency-lock and portability regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.compat.ClassSpecCompatibilityRegressionSuite")
}

val classSpecApplicationRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs Profile v2 mutation, DARK gate and fail-closed application regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.application.ClassSpecApplicationRegressionSuite")
}

val classSpecSectionRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs ClassSpec section invariants without opaque binary persistence."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.profile.ClassSpecSectionV2RegressionSuite")
}

val classSpecLifecycleRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs PlayerProfile-backed class/spec join, logout and disable lifecycle regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.application.ClassSpecSectionLifecycleRegressionSuite")
}

val playerProfileDomainRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs modular PlayerProfile root/section domain regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.domain.PlayerProfileDomainRegressionSuite")
}

val playerProfileSectionExtensionsRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs immutable extension-copy regressions across every PlayerProfile section."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensionsRegressionSuite")
}

val spellMasteryTransactionRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs exact-once spell mastery wallet/receipt recovery regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.managers.SpellMasteryTransactionRegressionSuite")
}

val professionProfileStateRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs PlayerProfile profession slot, XP, level and recipe authority regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.domain.ProfessionProfileStateRegressionSuite")
}

val playerProfileYamlRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs structured section YAML, manifest, CAS, quarantine and shutdown regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.persistence.PlayerProfileYamlRegressionSuite")
}

val playerProfileTransactionRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs cross-section WAL, idempotency and restart-recovery regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionRegressionSuite")
}

val playerProfileApiRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs internal/API DTO, authentication, ETag, rate-limit and shutdown regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.api.PlayerProfileApiRegressionSuite")
}

val respecTransactionRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs Profile v2 respec WAL, restart recovery and crash-decision regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.transaction.RespecTransactionRegressionSuite")
}


val spellGrantLedgerRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs explicit BASE/SPEC/TALENT/QUEST/ADMIN spell provenance regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.domain.SpellGrantLedgerRegressionSuite")
}

val pauseMenuDialogRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs JAR datapack pause-menu website dialog regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.dialog.PauseMenuDialogRegressionSuite")
}

val runtimeBugfixRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs pet nametag, lore output, corruption safety and spectator-menu regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.runtime.RuntimeBugfixRegressionSuite")
}

val factionPassiveRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs faction membership, damage, exhaustion, truce and lifecycle regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.factions.FactionPassiveRegressionSuite")
}

val factionPassiveHardeningRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs pure adapter, retaliation, Blood Moon and signature-food hardening regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.factions.FactionPassiveHardeningRegressionSuite")
}

val factionTreasuryRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs faction tax origin, legacy migration and collection regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.factions.FactionTaxDebtRegressionSuite")
}

val relicItemRefreshRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs Mélytépő modifier idempotency regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.items.RelicRefreshRegressionSuite")
}

val relicRefreshPipelineRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs per-slot relic refresh isolation and diagnostic regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.managers.RelicRefreshPipelineRegressionSuite")
}

val lifecycleShutdownRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs Folia disable/shutdown scheduler regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.lifecycle.LifecycleShutdownRegressionSuite")
}

val questNpcValidationRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs quest-NPC exact-name and manual provisioning regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.quests.QuestNpcValidationRegressionSuite")
}

val resourcePackRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs additive resource-pack id, hash and immutable URL regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.resourcepack.ResourcePackRegressionSuite")
}

tasks.check {
    dependsOn(persistentStoreRegressionTest, devItemRewardRegressionTest, moderationRegressionTest,
        motdRegressionTest, sitRegressionTest, crateRegressionTest, configStartupRegressionTest,
        afkRegressionTest, worldGuardBridgeRegressionTest, territoryCapitalRegressionTest,
        hudRegressionTest, pauseMenuDialogRegressionTest, runtimeBugfixRegressionTest,
        factionPassiveRegressionTest, factionPassiveHardeningRegressionTest, factionTreasuryRegressionTest,
        relicItemRefreshRegressionTest, relicRefreshPipelineRegressionTest, lifecycleShutdownRegressionTest,
        questNpcValidationRegressionTest, resourcePackRegressionTest, classSpecCompatibilityRegressionTest,
        classSpecSectionRegressionTest, classSpecApplicationRegressionTest,
        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileSectionExtensionsRegressionTest, spellMasteryTransactionRegressionTest,
        professionProfileStateRegressionTest, playerProfileYamlRegressionTest,
        playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
        respecTransactionRegressionTest, spellGrantLedgerRegressionTest)
}
