import java.security.MessageDigest
import groovy.json.JsonSlurper
import javax.imageio.ImageIO

plugins {
    id("java-library")
    alias(libs.plugins.run.paper)
}

val pythonCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "python" else "python3"
val generateIceSmpHudCoreAssets by tasks.registering(Exec::class) {
    group = "build"
    description = "Regenerates the first-party IceSMP class HUD fonts, sprites and positioning shader."
    commandLine(pythonCommand, "scripts/generate_icesmp_hud_assets.py")
}

val generateIceSmpSurvivalHudAssets by tasks.registering(Exec::class) {
    group = "build"
    description = "Regenerates the isolated HP, armor, food and oxygen HUD module."
    dependsOn(generateIceSmpHudCoreAssets)
    commandLine(pythonCommand, "scripts/generate_icesmp_survival_hud_assets.py")
}

val generateClassUiAssets by tasks.registering(Exec::class) {
    group = "build"
    description = "Regenerates the custom class UI family and 35 specialization badges."
    inputs.files(
        "scripts/generate_class_ui_assets.py",
        "dev-assets/icesmp-class-ui/source/faction-ornaments-v1.png",
        "dev-assets/icesmp-class-ui/source/specialization-sigils-v1.png",
    )
    outputs.dir("resource-pack/assets/icesmp_hud/textures/class_ui")
    outputs.file("resource-pack/assets/icesmp_hud/font/class_ui.json")
    outputs.file("resource-pack/assets/icesmp_hud/font/specialization_badge.json")
    outputs.file("resource-pack/assets/icesmp_hud/font/class_badge.json")
    commandLine(pythonCommand, "scripts/generate_class_ui_assets.py")
}

val generateIceSmpHudAssets by tasks.registering {
    group = "build"
    description = "Regenerates the complete first-party IceSMP HUD package."
    dependsOn(generateIceSmpSurvivalHudAssets, generateClassUiAssets)
}

val auditIceSmpHudAssets by tasks.registering(Exec::class) {
    group = "verification"
    description = "Audits every first-party IceSMP HUD PNG plus the 13-class mechanic matrix."
    dependsOn(generateIceSmpHudAssets)
    val report = layout.buildDirectory.file("reports/icesmp-hud/asset-audit.md")
    inputs.files("scripts/audit_icesmp_hud_assets.py")
    inputs.dir(layout.projectDirectory.dir("resource-pack/assets/icesmp_hud"))
    outputs.file(report)
    commandLine(
        pythonCommand,
        "scripts/audit_icesmp_hud_assets.py",
        "--report", report.get().asFile.relativeTo(layout.projectDirectory.asFile).path,
    )
}

val auditEquipmentAssets by tasks.registering(Exec::class) {
    group = "verification"
    description = "Audits slot UV isolation, equipment palettes, hand orientation and visual item states."
    inputs.files(
        "scripts/generate_equipment_assets.py",
        "scripts/audit_equipment_assets.py",
    )
    inputs.dir(layout.projectDirectory.dir("resource-pack/assets/icesmp/textures/entity/equipment"))
    inputs.dir(layout.projectDirectory.dir("resource-pack/assets/icesmp/textures/item"))
    inputs.dir(layout.projectDirectory.dir("resource-pack/assets/icesmp/models/item"))
    commandLine(pythonCommand, "scripts/audit_equipment_assets.py")
}

val trashSpriteAssetAudit by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates all 330 AI-authored Trash sprites and modern item models without writing."
    inputs.files(
        "scripts/process_trash_sprite_sheets.py",
        "dev-assets/trash/source/manifest.json",
        "src/main/resources/content/trash/catalog.yml",
    )
    inputs.dir(layout.projectDirectory.dir("dev-assets/trash/source"))
    inputs.dir(layout.projectDirectory.dir("resource-pack/assets/icesmp/items/trash"))
    inputs.dir(layout.projectDirectory.dir("resource-pack/assets/icesmp/models/item/trash"))
    inputs.dir(layout.projectDirectory.dir("resource-pack/assets/icesmp/textures/item/trash"))
    commandLine(pythonCommand, "scripts/process_trash_sprite_sheets.py", "--check", "--require-complete")
}

val progressionBalanceRegressionTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the Itemization/Mob 2.0 economy, Monte Carlo and bounded-load gate."
    inputs.files(
        "scripts/test_progression_balance.py",
        "src/main/resources/content/equipment/equipment.yml",
        "src/main/resources/config/crafting.yml",
        "src/main/resources/content/professions/recipes.yml",
        "src/main/resources/content/pve/enemies.yml",
        "src/main/resources/config/world.yml",
    )
    commandLine(pythonCommand, "scripts/test_progression_balance.py")
}

val equipment2ReportRegressionTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the deterministic Equipment 2.0 migration, budget and handoff report."
    inputs.files(
        "scripts/generate_equipment_2_report.py",
        "src/main/resources/content/equipment/equipment.yml",
        "src/main/resources/content/professions/recipes.yml",
        "docs/development/equipment-2-handoff.json",
    )
    commandLine(pythonCommand, "scripts/generate_equipment_2_report.py", "--check")
}

val combatEncounterFoundationAudit by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates normalized equipment, level gates, enemy techniques, TTK and wildlife evidence."
    inputs.files(
        "scripts/audit_combat_encounter_foundation.py",
        "scripts/audit_long_term_equipment_catalog.py",
        "src/main/resources/content/equipment/equipment.yml",
        "src/main/resources/content/pve/enemies.yml",
        "src/main/resources/config/world.yml",
        "docs/development/combat-balance-authority.json",
    )
    commandLine(pythonCommand, "scripts/audit_combat_encounter_foundation.py", "--check")
}

val unifiedCreatureCombatAudit by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the bounded creature species, technique, reaction, reward and social authority."
    inputs.files(
        "scripts/audit_unified_creature_combat.py",
        "src/main/resources/content/pve/enemies.yml",
        "src/main/java/hu/taliann/icesmp/pve/CreatureProfileService.java",
        "src/main/java/hu/taliann/icesmp/pve/MobAbilityRuntime.java",
        "src/main/java/hu/taliann/icesmp/managers/MobScalingManager.java",
    )
    commandLine(pythonCommand, "scripts/audit_unified_creature_combat.py", "--check")
}

val authoredPveConsolidationAudit by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates authored event spawn, template, ability, reward and lifecycle consolidation."
    inputs.files(
        "scripts/audit_authored_pve_consolidation.py",
        "src/main/resources/config.yml",
        "src/main/resources/content/pve/enemies.yml",
        "src/main/resources/config/world.yml",
        "src/main/java/hu/taliann/icesmp/pve/AuthoredCreatureSpawnService.java",
        "src/main/java/hu/taliann/icesmp/pve/MobAbilityRuntime.java",
        "src/main/java/hu/taliann/icesmp/utils/MobKillUtil.java",
        "src/main/java/hu/taliann/icesmp/managers/WorldBossManager.java",
        "src/main/java/hu/taliann/icesmp/managers/InvasionManager.java",
        "src/main/java/hu/taliann/icesmp/managers/DarkUndeadAmbienceManager.java",
        "src/main/java/hu/taliann/icesmp/prologue/PrologueEncounterEngine.java",
    )
    commandLine(pythonCommand, "scripts/audit_authored_pve_consolidation.py", "--check")
}

val enemyWorldBossReworkAudit by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates enemy inventory, variants, behavior, context, daylight, FX and boss diversity."
    inputs.files(
        "scripts/audit_enemy_worldboss_rework.py",
        "src/main/resources/content/pve/enemies.yml",
        "src/main/resources/config/world.yml",
        "src/main/java/hu/taliann/icesmp/pve/MobAbilityRuntime.java",
        "docs/development/enemy-worldboss-rework-2.json",
    )
    commandLine(pythonCommand, "scripts/audit_enemy_worldboss_rework.py", "--check")
}

val configContentCommandSurfaceAudit by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates canonical config/content ownership, semantic parity and command permissions."
    inputs.files(
        "scripts/audit_config_content_command_surface.py",
        "docs/development/config-content-command-surface-2.json",
        "src/main/java/hu/taliann/icesmp/managers/ConfigManager.java",
        "src/main/java/hu/taliann/icesmp/commands/IceSMPCommand.java",
        "src/main/java/hu/taliann/icesmp/commands/AbstractDispatchCommand.java",
        "src/main/java/hu/taliann/icesmp/commands/Subcommand.java",
    )
    inputs.dir("src/main/resources/config")
    inputs.dir("src/main/resources/content")
    commandLine(pythonCommand, "scripts/audit_config_content_command_surface.py", "--check")
}

val gameplayBootstrapIntegrityAudit by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the ten bounded gameplay/bootstrap finding closures and evidence matrices."
    inputs.files(
        "scripts/audit_gameplay_bootstrap_integrity.py",
        "docs/development/gameplay-bootstrap-integrity-hardening.json",
        "src/main/resources/paper-plugin.yml",
        "src/main/resources/class-spec-dependencies.lock.yml",
    )
    inputs.dir("src/main/resources/content")
    inputs.dir("src/main/resources/datapack")
    inputs.dir("src/main/java")
    commandLine(pythonCommand, "scripts/audit_gameplay_bootstrap_integrity.py", "--check")
}

val questItemContentIntegrityAudit by tasks.registering(Exec::class) {
    group = "verification"
    description = "Exhaustively validates quest, reward, item-lore and boss-reward finding closure."
    inputs.files(
        "scripts/audit_quest_item_content_integrity.py",
        "docs/development/quest-item-content-integrity-hardening.json",
    )
    inputs.dir("src/main/resources/content")
    inputs.dir("src/main/java")
    commandLine(pythonCommand, "scripts/audit_quest_item_content_integrity.py", "--check")
}

val validateIceSmpHudPackage by tasks.registering {
    group = "verification"
    description = "Validates the first-party HUD assets, fixed-width contract and HP-rework safety gates."
    dependsOn(generateIceSmpHudAssets)
    val pack = layout.projectDirectory.dir("resource-pack")
    val hud = pack.dir("assets/icesmp_hud")
    val rendererSource = layout.projectDirectory.file(
        "src/main/java/hu/taliann/icesmp/hud/IceSmpHudRenderer.java")
    inputs.dir(hud)
    inputs.files(
        "scripts/generate_icesmp_hud_assets.py",
        "scripts/generate_icesmp_survival_hud_assets.py",
        "scripts/generate_class_ui_assets.py",
        "src/main/java/hu/taliann/icesmp/hud/IceSmpHudRenderer.java"
    )
    doLast {
        val manifest = JsonSlurper().parse(hud.file("hud-manifest.json").asFile) as Map<*, *>
        require((manifest["fixed_segment_count"] as Number).toInt() == 12) {
            "First-party HUD bars must use 12 fixed cells"
        }
        require((manifest["text_advance"] as Number).toInt() == 6) {
            "First-party HUD must retain the compact six-pixel text grid"
        }
        require(manifest["text_font"] == "Inter SemiBold"
            && (manifest["text_oversample"] as Number).toInt() == 8
            && manifest["text_source_resolution"] == listOf(40, 96)) {
            "First-party HUD must retain the reviewed high-resolution UI typeface"
        }
        require((manifest["resource_segment_advance"] as Number).toInt() == 13
            && (manifest["metric_segment_advance"] as Number).toInt() == 8) {
            "HUD bars must retain their full-width and half-panel modular pitches"
        }
        val layoutY = manifest["layout_y"] as? Map<*, *>
            ?: error("First-party HUD layout anchors are missing")
        val expectedLayoutY = mapOf(
            "header" to 42, "subheader" to 55,
            "resource_text" to 67, "resource_bar" to 70,
            "mechanic_icon" to 86, "mechanic_text" to 94,
            "metric_bar" to 108, "runes" to 130,
            "charge" to 137, "state" to 143,
            "event_text" to 165, "wallet_text" to 217,
            "wallet_lower_text" to 237
        )
        expectedLayoutY.forEach { (name, value) ->
            require((layoutY[name] as? Number)?.toInt() == value) {
                "HUD layout anchor drifted from its reviewed panel: $name"
            }
        }
        val layoutX = manifest["layout_x"] as? Map<*, *>
            ?: error("First-party HUD horizontal anchors are missing")
        mapOf(
            "resource_text" to 68,
            "resource_bar" to 60,
            "primary_metric_bar" to 12,
            "secondary_metric_bar" to 125,
            "event_center" to 120,
            "level_center" to 218
        ).forEach { (name, value) ->
            require((layoutX[name] as? Number)?.toInt() == value) {
                "HUD horizontal anchor drifted outside its reviewed panel: $name"
            }
        }
        require((manifest["wallet_slots"] as Number).toInt() == 4) {
            "First-party HUD must expose four fixed wallet slots"
        }
        require((manifest["wallet_columns"] as Number).toInt() == 2
            && (manifest["wallet_rows"] as Number).toInt() == 2) {
            "First-party HUD wallet must retain its fixed 2x2 currency grid"
        }
        require(manifest["detail_metrics_conditional"] == true
            && (manifest["compact_wallet_anchor_y"] as Number).toInt() == 178
            && (manifest["compact_wallet_anchor_delta"] as Number).toInt() == -23) {
            "Empty supplementary detail rows must collapse before the wallet"
        }
        require(manifest["vanilla_health_hidden"] == true
            && manifest["vanilla_armor_hidden"] == true
            && manifest["vanilla_food_hidden"] == true
            && manifest["vanilla_oxygen_hidden"] == true
            && manifest["hardcore_hearts_overridden"] == false) {
            "The complete survival replacement must hide regular bars without touching hardcore hearts"
        }
        val fonts = hud.dir("font").asFile
        listOf("space", "panel", "wallet_panel", "wallet_panel_compact", "detail_panel", "class_icon",
            "currency", "currency_lower", "currency_compact", "currency_compact_lower",
            "runes", "runes_compact", "runes_panel", "charges",
            "mechanic_icons", "mechanic_slots", "resource_segments",
            "metric_segments", "text_header", "text_subheader", "text_resource",
            "text_mechanic", "text_state", "text_event", "text_detail", "text_wallet", "text_wallet_lower",
            "text_wallet_compact", "text_wallet_compact_lower").forEach { name ->
            require(fonts.resolve("$name.json").isFile) { "Missing IceSMP HUD font: $name" }
        }
        listOf("panel", "health_segments", "mini_segments", "icons", "player_name",
            "text_header", "text_percent", "text_stats",
            "target_header", "target_status", "target_health_segments", "target_health",
            "target_resource_segments", "target_stats",
            "party_header", "party_health_segments", "party_health_text",
            "party_resource_segments", "party_status").forEach { name ->
            require(fonts.resolve("survival/$name.json").isFile) {
                "Missing isolated survival HUD font: $name"
            }
        }
        val survivalManifest = JsonSlurper().parse(
            hud.file("survival-hud-manifest.json").asFile) as Map<*, *>
        require((survivalManifest["version"] as Number).toInt() == 2
            && survivalManifest["anchor"] == "top_left"
            && survivalManifest["panel_size"] == listOf(252, 72)
            && survivalManifest["target_panel_size"] == listOf(240, 88)
            && (survivalManifest["party_max_rows"] as Number).toInt() == 4
            && (survivalManifest["health_segments"] as Number).toInt() == 20
            && (survivalManifest["mini_segments"] as Number).toInt() == 10
            && survivalManifest["air_display"] == "only_when_depleted"
            && (survivalManifest["default_scale"] as Number).toDouble() == 1.0
            && survivalManifest["text_font"] == "Inter SemiBold"
            && (survivalManifest["text_oversample"] as Number).toInt() == 8
            && survivalManifest["text_atlas"] == "icesmp_hud:hud/survival/text-atlas.png"
            && survivalManifest["hardcore_hearts_overridden"] == false) {
            "Survival HUD manifest lost its reviewed coverage or geometry"
        }
        val survivalTextAtlas = ImageIO.read(
            hud.file("textures/hud/survival/text-atlas.png").asFile)
            ?: error("Unreadable isolated survival text atlas")
        require(survivalTextAtlas.width == 768 && survivalTextAtlas.height == 672) {
            "Survival HUD text atlas lost its isolated fixed-cell geometry"
        }
        listOf("panel.png", "panel_air.png").forEach { name ->
            val panel = ImageIO.read(hud.file("textures/hud/survival/$name").asFile)
                ?: error("Unreadable survival HUD panel: $name")
            require(panel.width == 252 && panel.height == 72) {
                "Survival HUD panel exceeds or lost its reviewed glyph geometry: $name"
            }
        }
        listOf("ice", "ember", "frost", "guild", "lich").forEach { theme ->
            listOf("player_$theme.png", "party_$theme.png").forEach { name ->
                val frame = ImageIO.read(hud.file("textures/hud/survival/$name").asFile)
                    ?: error("Unreadable HUD v2 frame: $name")
                require(frame.width == 252 && frame.height == 72) {
                    "Unexpected player/party frame size: $name"
                }
            }
            val target = ImageIO.read(
                hud.file("textures/hud/survival/target_player_$theme.png").asFile)
                ?: error("Unreadable player target frame: $theme")
            require(target.width == 240 && target.height == 88) {
                "Unexpected player target frame size: $theme"
            }
        }
        listOf("passive", "neutral", "hostile", "elite", "boss").forEach { style ->
            val target = ImageIO.read(
                hud.file("textures/hud/survival/target_mob_$style.png").asFile)
                ?: error("Unreadable mob target frame: $style")
            require(target.width == 240 && target.height == 88) {
                "Unexpected mob target frame size: $style"
            }
        }
        val textures = hud.dir("textures/hud").asFile
        listOf("guest", "red", "blue", "neutral", "dark").forEach { theme ->
            val frame = textures.resolve("frame-hud-$theme.png")
            val image = ImageIO.read(frame) ?: error("Unreadable HUD frame: $frame")
            require(image.width == 240 && image.height == 160) { "Unexpected HUD frame size: $frame" }
            require(image.width <= 256 && image.height <= 256) {
                "HUD frame exceeds Minecraft's 256x256 glyph stitcher: $frame"
            }
        }
        val guestHash = MessageDigest.getInstance("SHA-256")
            .digest(textures.resolve("frame-hud-guest.png").readBytes()).contentToString()
        val blueHash = MessageDigest.getInstance("SHA-256")
            .digest(textures.resolve("frame-hud-blue.png").readBytes()).contentToString()
        require(guestHash != blueHash) {
            "Guest and BLUE HUD frames must be visually distinct"
        }
        listOf("red", "blue", "neutral", "dark").forEach { currency ->
            val file = textures.resolve("currency-$currency.png")
            val image = ImageIO.read(file) ?: error("Unreadable HUD currency icon: $file")
            require(image.width == 64 && image.height == 64) { "Currency icon must stay 64x64: $file" }
        }
        val walletStrip = ImageIO.read(textures.resolve("wallet-strip.png"))
            ?: error("Unreadable HUD wallet strip")
        require(walletStrip.width == 240 && walletStrip.height == 42) {
            "HUD wallet must retain its fixed 2x2 panel geometry"
        }
        listOf("metric-track.png", "metric-fill.png", "metric-fill-warm.png",
            "metric-fill-gold.png").forEach { name ->
            val image = ImageIO.read(textures.resolve(name))
                ?: error("Unreadable HUD metric segment: $name")
            require(image.width == 7 && image.height == 5) {
                "HUD half-panel metric segments must stay 7x5: $name"
            }
        }
        listOf("segment-track.png", "segment-fill.png", "segment-fill-warm.png",
            "segment-fill-gold.png").forEach { name ->
            val image = ImageIO.read(textures.resolve(name))
                ?: error("Unreadable HUD resource segment: $name")
            require(image.width == 12 && image.height == 3) {
                "HUD resource segments must stay 12x3 to share the thin channel with text: $name"
            }
        }
        val mechanics = manifest["mechanics"] as? List<*>
            ?: error("First-party HUD mechanic manifest is missing")
        val mechanicVariants = manifest["mechanic_variants"] as? List<*>
            ?: error("First-party HUD mechanic variants are missing")
        require(mechanics.size == 49 && mechanicVariants == listOf("active", "ready", "alert", "spent")) {
            "First-party HUD must retain 49 class-qualified mechanics and four visual states"
        }
        mechanics.forEach { raw ->
            val (classId, mechanicId) = raw.toString().split(":", limit = 2)
            mechanicVariants.forEach { variant ->
                val file = textures.resolve("mechanic-$classId-$mechanicId-$variant.png")
                val image = ImageIO.read(file) ?: error("Unreadable HUD mechanic icon: $file")
                require(image.width == 64 && image.height == 64 && image.alphaRaster != null) {
                    "HUD mechanic icon must be a transparent 64x64 glyph: $file"
                }
            }
        }
        val shader = pack.file("assets/minecraft/shaders/core/rendertype_text.vsh").asFile
        require((manifest["layout_color_payload_bits"] as? Number)?.toInt() == 14
            && (manifest["layout_y_offset_range"] as? List<*>)
                ?.map { (it as Number).toInt() } == listOf(-512, 511)
            && (manifest["layout_scale_variants"] as? List<*>)
                ?.map { (it as Number).toDouble() } == listOf(
                    0.75, 0.9, 1.0, 1.15, 1.25, 1.4, 1.6, 1.8,
                    2.0, 2.2, 2.4, 2.6, 2.8, 3.0, 3.25, 3.5)) {
            "First-party HUD manifest lost the build-time layout/scale variant contract"
        }
        require(shader.isFile && shader.readText().contains("HEIGHT_BIT 13")
            && shader.readText().contains("<minecraft:globals.glsl>")
            && shader.readText().contains("vec2 hudScale = vec2(responsiveScale) * ui / ScreenSize")
            && shader.readText().contains("const float HUD_LAYOUT_SCALES[16]")
            && shader.readText().contains("int layoutCode = (packedColor.r & 15)")
            && shader.readText().contains("(packedColor.r & 16) << 9")
            && shader.readText().contains("layoutCode & 1023")
            && shader.readText().contains("layoutCode >> 10")
            && shader.readText().contains("vec2 selectedHudScale = hudScale * layoutScale")
            && shader.readText().contains("topLeft = id >= 11 && id <= 15")
            && shader.readText().contains("ScreenSize.x / 2560.0")
            && shader.readText().contains("ScreenSize.y / 1440.0")
            && shader.readText().contains("clipPosition.x = -clipPosition.w + clipPosition.x * selectedHudScale.x")
            && shader.readText().contains("layoutYOffset * responsiveScale * layoutScale")) {
            "Missing first-party 1.21.11 HUD positioning shader"
        }
        val renderer = rendererSource.asFile.readText()
        require(renderer.contains("append(space(-anchoredX - width))")) {
            "Every first-party HUD draw must return to its cursor origin"
        }
        require(!renderer.contains("primaryMetric()") && !renderer.contains("secondaryMetric()")) {
            "The renderer must consume generic metric slots, not class-specific accessors"
        }
        val vanillaHud = pack.dir("assets/minecraft/textures/gui/sprites/hud").asFile
        fun survivalSpriteNames(key: String): List<String> =
            (survivalManifest[key] as? List<*>)?.map { it.toString() }
                ?: error("Survival HUD manifest list is missing: $key")
        val transparentSprites = buildList {
            addAll(survivalSpriteNames("heart_sprites").map { "heart/$it" })
            addAll(survivalSpriteNames("armor_sprites"))
            addAll(survivalSpriteNames("food_sprites"))
            addAll(survivalSpriteNames("air_sprites"))
        }
        require(transparentSprites.size == 34 && transparentSprites.distinct().size == 34) {
            "Survival HUD must own exactly the reviewed 34 regular vanilla sprites"
        }
        transparentSprites.forEach { name ->
            val image = ImageIO.read(vanillaHud.resolve(name))
                ?: error("Unreadable vanilla HUD replacement: $name")
            val alpha = image.alphaRaster
            require(image.width == 9 && image.height == 9 && alpha != null
                && (0 until image.width).all { x ->
                    (0 until image.height).all { y -> alpha.getSample(x, y, 0) == 0 }
                }) {
                "Vanilla HUD replacement must stay transparent 9x9: $name"
            }
        }
        val forbiddenHardcoreSprites = vanillaHud.resolve("heart").walkTopDown()
            .filter { it.isFile && it.name.contains("hardcore", ignoreCase = true) }
            .toList()
        require(forbiddenHardcoreSprites.isEmpty()) {
            "Non-hardcore server pack must not override hardcore heart sprites: " +
                forbiddenHardcoreSprites.joinToString()
        }
        logger.lifecycle("First-party IceSMP HUD package valid: class HUD plus Player/Target/Party frames")
    }
}

val stageMergedResourcePackForR2 by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Deterministically merges the immutable external base with the first-party IceSMP pack for R2."
    dependsOn(generateIceSmpHudAssets, auditEquipmentAssets)
    // Resolve the Gradle property while configuring this Exec task. The former doFirst
    // action captured the Kotlin build-script object and could not be serialized by the
    // configuration cache. Gradle tracks the property read as a configuration input.
    val externalPackPath = providers.gradleProperty("icesmpExternalPack")
        .orElse("run/plugins/IceSMPExternalBase.zip").get()
    val externalPack = layout.projectDirectory.file(externalPackPath)
    val stagedPack = layout.buildDirectory.file("resource-pack/icesmp.zip")
    val metadata = layout.buildDirectory.file("resource-pack/merged.properties")
    inputs.file(externalPack)
    inputs.dir(layout.projectDirectory.dir("resource-pack"))
    inputs.file(layout.projectDirectory.file("scripts/resource_pack.py"))
    outputs.files(stagedPack, metadata)
    commandLine(
        pythonCommand,
        "scripts/resource_pack.py",
        "merge",
        "--base", externalPack.asFile.path,
        "--source", "resource-pack",
        "--output", stagedPack.get().asFile.path,
        "--metadata", metadata.get().asFile.path,
    )
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
val commandSurfaceRegressionTest = registerRegression(
    "commandSurfaceRegressionTest",
    "Runs permission, help and Paper trailing-space command surface source contracts.",
    "hu.taliann.icesmp.commands.CommandSurfaceRegressionSuite")
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
val iceSmpHudRegressionTest = registerRegression(
    "iceSmpHudRegressionTest",
    "Runs first-party HUD fixed-layout, wallet, readiness and authority regressions.",
    "hu.taliann.icesmp.hud.IceSmpHudRegressionSuite")
val targetFrameRegressionTest = registerRegression(
    "targetFrameRegressionTest",
    "Runs behavioral canonical Target Frame selection, metadata and lifecycle regressions.",
    "hu.taliann.icesmp.hud.TargetFrameRegressionSuite")
val runeLifecycleRegressionTest = registerRegression(
    "runeLifecycleRegressionTest",
    "Runs behavioral rune insert, remove, replace, identity and recovery regressions.",
    "hu.taliann.icesmp.itemization.RuneLifecycleRegressionSuite")
val hardeningClosureRegressionTest = registerRegression(
    "hardeningClosureRegressionTest",
    "Runs final CombatPower, set, boss, reward, contribution and ability closure regressions.",
    "hu.taliann.icesmp.pve.HardeningClosureRegressionSuite")
hardeningClosureRegressionTest.configure {
    dependsOn(targetFrameRegressionTest, runeLifecycleRegressionTest)
}
val hudEditorRegressionTest = registerRegression(
    "hudEditorRegressionTest",
    "Runs first-party HUD editor gate, isolation, layout, shader and authority regressions.",
    "hu.taliann.icesmp.hud.HudEditorRegressionSuite")
val playerProfileHudLayoutRegressionTest = registerRegression(
    "playerProfileHudLayoutRegressionTest",
    "Runs sparse Profile v2 HUD-layout inheritance, reset and malformed-data regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileHudLayoutRegressionSuite")
val classSpecApplicationRegressionTest = registerRegression(
    "classSpecApplicationRegressionTest",
    "Runs Profile v2 mutation, DARK gate and fail-closed application regressions.",
    "hu.taliann.icesmp.classspec.application.ClassSpecApplicationRegressionSuite")
val targetRegistryRegressionTest = registerRegression(
    "targetRegistryRegressionTest",
    "Runs UUID-only class target-link death, quit and concurrency regressions.",
    "hu.taliann.icesmp.classspec.application.TargetRegistryRegressionSuite")
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
    "Runs pet stable/spawn/XP, lore output, corruption safety and spectator-menu regressions.",
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
val clientProtocolRegressionTest = registerRegression(
    "clientProtocolRegressionTest",
    "Runs dependency-free IceSMP Client bridge protocol foundation regressions.",
    "hu.taliann.icesmp.client.ClientProtocolRegressionSuite")
val factionDisplayColorRegressionTest = registerRegression(
    "factionDisplayColorRegressionTest",
    "Runs central faction display palette and consumer-contract regressions.",
    "hu.taliann.icesmp.factions.FactionDisplayColorRegressionSuite")
val inventoryReadWriteRegressionTest = registerRegression(
    "inventoryReadWriteRegressionTest",
    "Runs invsee single-writer, donation gesture and rollback regressions.",
    "hu.taliann.icesmp.inventory.InventoryReadWriteRegressionSuite")
val donationChestDurabilityRegressionTest = registerRegression(
    "donationChestDurabilityRegressionTest",
    "Runs donation write-ahead, restart recovery and exactly-once regressions.",
    "hu.taliann.icesmp.managers.DonationChestDurabilityRegressionSuite")
val operationalConfigMenuRegressionTest = registerRegression(
    "operationalConfigMenuRegressionTest",
    "Runs operational config menu schema, help, reset and live-apply regressions.",
    "hu.taliann.icesmp.managers.OperationalConfigMenuRegressionSuite")
val professionRecipeAuditRegressionTest = registerRegression(
    "professionRecipeAuditRegressionTest",
    "Validates deterministic profession recipes, semantic uniqueness and reload cleanup.",
    "hu.taliann.icesmp.professions.ProfessionRecipeAuditRegressionSuite")
val professions2RegressionTest = registerRegression(
    "professions2RegressionTest",
    "Runs deterministic Masterwork and Professions 2.0 economy contracts.",
    "hu.taliann.icesmp.professions.Professions2RegressionSuite")
val professions2ReportRegressionTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates Professions 2.0 recipe migration and economy graph reports."
    inputs.files("scripts/check_professions_2_reports.py", "docs/development/professions-2-recipe-migration.json",
        "docs/development/professions-2-economy-graph.json", "docs/development/professions-2-rp-handoff.json")
    commandLine(pythonCommand, "scripts/check_professions_2_reports.py")
}
val professions2EconomyRegressionTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the seeded Professions 2.0 economy sanity harness."
    inputs.files("scripts/test_professions_2_economy.py", "src/main/resources/config/professions-2.yml",
        "src/main/resources/content/professions/recipes.yml", "src/main/resources/config/professions.yml")
    commandLine(pythonCommand, "scripts/test_professions_2_economy.py")
}
val runtimeHardeningRegressionTest = registerRegression(
    "runtimeHardeningRegressionTest",
    "Runs 2D claim, vanish retracking and DARK mob lifecycle regressions.",
    "hu.taliann.icesmp.runtime.RuntimeHardeningRegressionSuite")
val prologueRegressionTest = registerRegression(
    "prologueRegressionTest",
    "Runs Season 0 Prologue cleanup, pause, victory recovery and transition regressions.",
    "hu.taliann.icesmp.prologue.PrologueRegressionSuite")
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
    "Runs concrete Varázsló Rúnaszövés, attunement/convergence and durable court regressions.",
    "hu.taliann.icesmp.wizard.WizardGameplayRegressionSuite")
val wizardProfileRegressionTest = registerRegression(
    "wizardProfileRegressionTest",
    "Runs Profile v2 Wizard allowlist, DARK gate, court-authority and slot-isolation regressions.",
    "hu.taliann.icesmp.wizard.WizardProfileRegressionSuite")
val itemizationDomainRegressionTest = registerRegression(
    "itemizationDomainRegressionTest",
    "Runs canonical item template, identity, roll-quality, history, set and soft-diversity regressions.",
    "hu.taliann.icesmp.itemization.ItemizationDomainRegressionSuite")
val vanillaCraftingBoundaryRegressionTest = registerRegression(
    "vanillaCraftingBoundaryRegressionTest",
    "Runs vanilla/basic/canonical/legacy transformation and laundering regressions.",
    "hu.taliann.icesmp.itemization.VanillaCraftingBoundaryRegressionSuite")
val equipmentDomainRegressionTest = registerRegression(
    "equipmentDomainRegressionTest",
    "Runs Equipment 2.0 class-family, budget, BASIC boundary and bypass regressions.",
    "hu.taliann.icesmp.itemization.EquipmentDomainRegressionSuite")
val playerProfileLootDiversityRegressionTest = registerRegression(
    "playerProfileLootDiversityRegressionTest",
    "Runs durable, bounded and idempotent Itemization 2.0 loot diversity regressions.",
    "hu.taliann.icesmp.playerprofile.application.PlayerProfileLootDiversityStoreRegressionSuite")
val mobEncounterDomainRegressionTest = registerRegression(
    "mobEncounterDomainRegressionTest",
    "Runs level 1-70 scaling, authored mob, ability, affix, encounter and contribution regressions.",
    "hu.taliann.icesmp.pve.MobEncounterDomainRegressionSuite")
val mobRuntimeSourceRegressionTest = registerRegression(
    "mobRuntimeSourceRegressionTest",
    "Runs Folia, telegraph, bounded lifecycle and durable encounter reward source gates.",
    "hu.taliann.icesmp.pve.MobRuntimeSourceRegressionSuite")
val gameplayBootstrapIntegrityRegressionTest = registerRegression(
    "gameplayBootstrapIntegrityRegressionTest",
    "Runs behavioral acquisition, projectile, dependency, advancement and facade closure regressions.",
    "hu.taliann.icesmp.hardening.GameplayBootstrapIntegrityRegressionSuite")
val questItemContentIntegrityRegressionTest = registerRegression(
    "questItemContentIntegrityRegressionTest",
    "Runs guest reward, quest preview, capstone, profession, daily, item and boss identity regressions.",
    "hu.taliann.icesmp.quest.QuestItemContentIntegrityRegressionSuite")
val trashCatalogRegressionTest = registerRegression(
    "trashCatalogRegressionTest",
    "Runs the 330-identity Trash catalog, secrecy, factory and hidden DEV authority gates.",
    "hu.taliann.icesmp.trash.TrashCatalogRegressionSuite")
val trashLootDistributionRegressionTest = registerRegression(
    "trashLootDistributionRegressionTest",
    "Runs the 30M-event Trash source/category/context/recycle Monte Carlo gate.",
    "hu.taliann.icesmp.trash.TrashLootDistributionRegressionSuite")

tasks.check {
    dependsOn(auditIceSmpHudAssets)
    dependsOn(auditEquipmentAssets)
    dependsOn(trashSpriteAssetAudit)
    dependsOn(validateIceSmpHudPackage)
    dependsOn(progressionBalanceRegressionTest)
    dependsOn(equipment2ReportRegressionTest)
    dependsOn(combatEncounterFoundationAudit)
    dependsOn(unifiedCreatureCombatAudit)
    dependsOn(authoredPveConsolidationAudit)
    dependsOn(enemyWorldBossReworkAudit)
    dependsOn(configContentCommandSurfaceAudit)
    dependsOn(gameplayBootstrapIntegrityAudit)
    dependsOn(questItemContentIntegrityAudit)
    dependsOn(professions2ReportRegressionTest)
    dependsOn(professions2EconomyRegressionTest)
    dependsOn(
        persistentStoreRegressionTest, devItemRewardRegressionTest, moderationRegressionTest,
        motdRegressionTest, sitRegressionTest, crateRegressionTest,
        configStartupRegressionTest, commandSurfaceRegressionTest, afkRegressionTest, worldGuardBridgeRegressionTest,
        territoryCapitalRegressionTest, hudRegressionTest, platformCapabilitiesRegressionTest, pauseMenuDialogRegressionTest,
        runtimeBugfixRegressionTest, factionPassiveRegressionTest, factionPassiveHardeningRegressionTest,
        trashCatalogRegressionTest, trashLootDistributionRegressionTest,
        factionTreasuryRegressionTest, relicItemRefreshRegressionTest, relicRefreshPipelineRegressionTest,
        lifecycleShutdownRegressionTest, questNpcValidationRegressionTest, questFrameworkV2RegressionTest,
        onboardingDialogRegressionTest, resourcePackRegressionTest,
        classSpecCompatibilityRegressionTest, iceSmpHudRegressionTest, targetFrameRegressionTest,
        runeLifecycleRegressionTest, hardeningClosureRegressionTest,
        hudEditorRegressionTest,
        playerProfileHudLayoutRegressionTest,
        classSpecSectionRegressionTest, classSpecApplicationRegressionTest, targetRegistryRegressionTest,
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
        prologueRegressionTest,
        eventSpawnSafetyRegressionTest, configGuiTransactionRegressionTest, configGuiCoverageRegressionTest,
        clientProtocolRegressionTest,
        professionRecipeAuditRegressionTest, professions2RegressionTest, inventoryReadWriteRegressionTest,
        donationChestDurabilityRegressionTest,
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
        wizardGameplayRegressionTest, wizardProfileRegressionTest,
        itemizationDomainRegressionTest, vanillaCraftingBoundaryRegressionTest,
        equipmentDomainRegressionTest, playerProfileLootDiversityRegressionTest,
        mobEncounterDomainRegressionTest, mobRuntimeSourceRegressionTest,
        gameplayBootstrapIntegrityRegressionTest, questItemContentIntegrityRegressionTest
    )
}
