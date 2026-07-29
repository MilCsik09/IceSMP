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
        jvmArgs("-Xms2G", "-Xmx2G")
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
    compileClasspath += sourceSets.main.get().output
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

tasks.check {
    dependsOn(persistentStoreRegressionTest, devItemRewardRegressionTest, moderationRegressionTest,
        motdRegressionTest)
}
