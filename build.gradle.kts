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

val classProfileV2RegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs Profile v2 domain invariants and deterministic ICS2 codec regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.profile.ClassProfileV2RegressionSuite")
}

val classSpecApplicationRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs Profile v2 mutation, DARK gate and fail-closed application regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.application.ClassSpecApplicationRegressionSuite")
}

val classProfileMigrationRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs idempotent legacy-to-Profile-v2 migration and preservation regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.migration.LegacyProfileMigrationRegressionSuite")
}

val classProfileRepositoryRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs Profile v2 YAML persistence, CAS, quarantine and flush regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.persistence.YamlClassProfileRepositoryRegressionSuite")
}

val classProfileLifecycleRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs Profile v2 join, migration, logout and disable lifecycle regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.classspec.application.ClassProfileLifecycleRegressionSuite")
}

val pauseMenuDialogRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs JAR datapack pause-menu website dialog regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.dialog.PauseMenuDialogRegressionSuite")
}

tasks.check {
    dependsOn(persistentStoreRegressionTest, devItemRewardRegressionTest, moderationRegressionTest,
        motdRegressionTest, sitRegressionTest, crateRegressionTest, configStartupRegressionTest,
        afkRegressionTest, worldGuardBridgeRegressionTest, territoryCapitalRegressionTest,
        hudRegressionTest, pauseMenuDialogRegressionTest, classSpecCompatibilityRegressionTest,
        classProfileV2RegressionTest, classSpecApplicationRegressionTest,
        classProfileMigrationRegressionTest, classProfileRepositoryRegressionTest,
        classProfileLifecycleRegressionTest)
}
