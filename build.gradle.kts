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

    testImplementation(libs.folia.api)
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    test {
        useJUnitPlatform()
    }

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
