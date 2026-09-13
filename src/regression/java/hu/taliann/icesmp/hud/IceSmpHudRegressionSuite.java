package hu.taliann.icesmp.hud;

import hu.taliann.icesmp.classspec.integration.ClassHudMetric;
import hu.taliann.icesmp.classspec.integration.ClassHudSlot;
import hu.taliann.icesmp.classspec.integration.ClassHudState;
import hu.taliann.icesmp.managers.HudManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** First-party HUD rendering, pack-readiness and authority-boundary regressions. */
public final class IceSmpHudRegressionSuite {
    private IceSmpHudRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        fixedLayoutIsIndependentOfDynamicValues();
        survivalVitalsAreCompleteAndFixedWidth();
        topLeftCarrierDrawsReturnToOrigin();
        classXpCurveRemainsExactButPersistentBarIsAbsent();
        targetVitalsAreEventDrivenAndBounded();
        factionThemeProjectionSelectsEveryFrame();
        layoutGeometryStaysInsideArtCompartments();
        specializationlessStateIsExplicit();
        walletAndClassContractIsGeneric();
        conditionalDetailRowAndDeathKnightLayoutStayCompact();
        packReadinessAndFallbackAreSafe();
        removedExternalHudDependencyIsAbsent();
        visualPackageIsComplete();
        System.out.println("First-party IceSMP HUD regression suite passed.");
    }

    private static void survivalVitalsAreCompleteAndFixedWidth() {
        final SurvivalHudState state = new SurvivalHudState(
                75.0D, 100.0D, 4.0D, 18.0D,
                14, 20, 150, 300);
        final String rendered = PlainTextComponentSerializer.plainText().serialize(
                new SurvivalHudRenderer().render(state, HudLayoutSnapshot.defaults()));
        check(rendered.contains("75 / 100 HP") && rendered.contains("+4 pajzs")
                        && rendered.contains("75%")
                        && rendered.contains("18") && !rendered.contains("18/")
                        && rendered.contains("14/20")
                        && rendered.contains("150/300"),
                "survival HUD must expose HP, flat armor, food and oxygen without an armor maximum");
        check(count(rendered, '\uEB00') == 1
                        && count(rendered, '\uEB10') == SurvivalHudRenderer.HEALTH_SEGMENTS
                        && count(rendered, '\uEB11') == 15
                        && count(rendered, '\uEB20') == SurvivalHudRenderer.MINI_SEGMENTS * 2,
                "survival bars must keep a fixed draw width independent of their values");
        final SurvivalHudState dry = new SurvivalHudState(
                20.0D, 20.0D, 0.0D, 0.0D,
                20, 20, 300, 300);
        final String dryRendered = PlainTextComponentSerializer.plainText().serialize(
                new SurvivalHudRenderer().render(dry, HudLayoutSnapshot.defaults()));
        check(!dryRendered.contains("300/300")
                        && count(dryRendered, '\uEB20') == SurvivalHudRenderer.MINI_SEGMENTS
                        && count(dryRendered, '\uEB00') == 1,
                "full oxygen must collapse to the balanced two-column surface layout");
        final SurvivalHudState clamped = new SurvivalHudState(
                Double.NaN, -1.0D, -4.0D, 48.0D,
                40, 20, -10, 300);
        check(clamped.health() == 0.0D && clamped.maximumHealth() == 20.0D
                        && clamped.absorption() == 0.0D && clamped.armor() == 48.0D
                        && clamped.food() == 20 && clamped.air() == 0,
                "invalid live values must clamp before reaching the survival compositor");
    }

    private static void topLeftCarrierDrawsReturnToOrigin() {
        final HudLayoutSnapshot layout = HudLayoutSnapshot.defaults();
        final Component player = new SurvivalHudRenderer().render(PlayerHudState.preview(), layout, null);
        final Component targetMob = new TargetHudRenderer().render(TargetHudState.previewMob(), layout, null);
        final Component targetPlayer = new TargetHudRenderer().render(
                TargetHudState.previewPlayer(), layout, null);
        final Component party = new PartyHudRenderer().render(PartyHudState.preview(), layout, null);
        check(carrierAdvance(player, null) == 0
                        && carrierAdvance(targetMob, null) == 0
                        && carrierAdvance(targetPlayer, null) == 0
                        && carrierAdvance(party, null) == 0,
                "every absolute top-left draw must restore the carrier cursor exactly");
        check(PartyHudRenderer.ROW_ADVANCE == 78,
                "scaled party rows must retain a visible gap instead of overlapping");
    }

    private static void classXpCurveRemainsExactButPersistentBarIsAbsent() {
        final ClassXpProgress levelOne = ClassXpProgress.calculate(30, 1, 60, 10, 50);
        check(levelOne.intoLevel() == 30 && levelOne.levelCost() == 60
                        && levelOne.remaining() == 30 && levelOne.percent() == 50,
                "class XP must project total experience into the first level interval");
        final ClassXpProgress levelThree = ClassXpProgress.calculate(150, 3, 60, 10, 50);
        check(levelThree.intoLevel() == 20 && levelThree.levelCost() == 80
                        && levelThree.remaining() == 60 && levelThree.percent() == 25,
                "class XP must subtract every completed progressive level cost");
        final ClassXpProgress maxed = ClassXpProgress.calculate(999_999, 50, 60, 10, 50);
        check(maxed.maxed() && maxed.percent() == 100 && maxed.remaining() == 0,
                "maximum class level must complete the XP bar without a phantom next cost");
        final String rendered = PlainTextComponentSerializer.plainText().serialize(
                new IceSmpHudRenderer().render(
                        HudPreviewCatalog.model(HudPreviewSelection.defaults())));
        check(!rendered.contains("Még ") && !rendered.contains(" XP")
                        && rendered.contains("Vérhold 04:12"),
                "class XP math must remain available without occupying the persistent class HUD");
    }

    private static void targetVitalsAreEventDrivenAndBounded() throws Exception {
        final String listener = read(
                "src/main/java/hu/taliann/icesmp/listeners/DamageIndicatorListener.java");
        check(listener.contains("recordLastTarget(attacker.getUniqueId(), victim, damage)")
                        && listener.contains("rayTrace(viewer.getEyeLocation()")
                        && listener.contains("target.getScheduler().run(plugin")
                        && listener.contains("TargetFrameMetadataPolicy")
                        && listener.contains("mob_template")
                        && listener.contains("mob_level")
                        && listener.contains("mob_rank")
                        && !listener.contains("showTargetVitals")
                        && !listener.contains("vitalDisplays")
                        && listener.contains("setVisibleByDefault(false)")
                        && listener.contains("attacker.showEntity(plugin, display)")
                        && !listener.contains("getNearbyEntities("),
                "target HUD must use an immutable owner-thread snapshot and leave only damage numbers in-world");
        final String config = read("src/main/resources/config/general.yml");
        final String manager = read("src/main/java/hu/taliann/icesmp/managers/HudManager.java");
        check(config.contains("target-frame:") && config.contains("enabled: true")
                        && config.contains("range: 24.0")
                        && config.contains("expire-seconds: 10")
                        && !config.contains("visibility: attacker-only"),
                "combat target vitals must ship as a screen-space target frame");
        check(manager.contains("indicators.sampleTarget(player)")
                        && manager.contains("survivalSnapshots.get(target.targetId())")
                        && manager.contains("snapshots.get(target.targetId())")
                        && manager.contains("targetSurvival == null ? target.health()"),
                "player targets must refresh from immutable live HUD caches without cross-region reads");
    }

    private static void fixedLayoutIsIndependentOfDynamicValues() throws Exception {
        final IceSmpHudRenderer renderer = new IceSmpHudRenderer();
        final Component empty = renderer.render(model(0, 120, 0));
        final Component full = renderer.render(model(120, 120, 100));
        check(!PlainTextComponentSerializer.plainText().serialize(empty).isBlank()
                        && !PlainTextComponentSerializer.plainText().serialize(full).isBlank(),
                "both empty and full resources must produce a HUD frame");
        check(ShadowColor.none().equals(empty.shadowColor())
                        && ShadowColor.none().equals(full.shadowColor()),
                "HUD roots must disable the vanilla text shadow pass to prevent duplicate shader glyphs");
        check(IceSmpHudRenderer.SPACE_FIRST >= 0xE000
                        && IceSmpHudRenderer.SPACE_FIRST + IceSmpHudRenderer.SPACE_MAX
                        - IceSmpHudRenderer.SPACE_MIN <= 0xF8FF,
                "spacing glyphs must stay in BMP PUA and avoid supplementary-plane sentinels");
        final String source = read("src/main/java/hu/taliann/icesmp/hud/IceSmpHudRenderer.java");
        check(source.contains("append(space(-anchoredX - width))")
                        && source.contains("List.of(\"ice\", \"ember\", \"frost\", \"guild\", \"lich\")")
                        && source.contains("final String levelText = Integer.toString(model.classLevel())")
                        && source.contains("LEVEL_CENTER_X = -36")
                        && source.contains("RESOURCE_TEXT_X = -186")
                        && source.contains("EVENT_TEXT_WIDTH = 186")
                        && source.contains("eventLine(model.event(), EVENT_TEXT_WIDTH)")
                        && source.contains("compactStateLine(model.classHud().state(), model.classHud().proc())")
                        && !source.contains("\"Lv. \"")
                        && source.contains("centeredText(HudComponent.EVENT_TEXT")
                        && !source.contains("glyph(HudComponent.LEVEL_ICON")
                        && !source.contains("glyph(HudComponent.EVENT_ICON")
                        && !source.contains("primaryMetric()") && !source.contains("secondaryMetric()"),
                "draws must return to origin, level must stay numeric-only and metrics generic");
        final String hud = read("src/main/java/hu/taliann/icesmp/managers/HudManager.java");
        check(!hud.contains("snapshot.classHud().classId().isBlank()"),
                "guest/profile/event HUD must remain visible before class selection");
        check(hud.contains("new ArrayList<>(CurrencyType.values().length)")
                        && !hud.contains("primary || amount > 0.0D"),
                "wallet snapshot must retain all four canonical currencies, including zero balances");
        check(hud.contains("faction == null ? \"ice\"")
                        && hud.contains("case RED -> \"ember\"")
                        && hud.contains("case BLUE -> \"frost\"")
                        && hud.contains("case NEUTRAL -> \"guild\"")
                        && hud.contains("case DARK -> \"lich\"")
                        && hud.contains("case DARK -> \"A955E8\""),
                "HUD snapshot and renderer theme ids must retain their five-frame ordering contract");
        final String preview = read("src/main/java/hu/taliann/icesmp/hud/HudPreviewCatalog.java");
        check(!preview.contains("\"wallet\".equals(state)")
                        && preview.contains("currency(\"red\"")
                        && preview.contains("currency(\"blue\"")
                        && preview.contains("currency(\"neutral\"")
                        && preview.contains("currency(\"dark\""),
                "every HUD editor preview must retain the canonical four-currency wallet");
    }

    private static void walletAndClassContractIsGeneric() {
        final IceSmpHudModel model = model(56, 100, 56);
        check(model.currencies().size() == 4
                        && model.currencies().stream().map(HudManager.HudCurrency::id)
                        .toList().equals(List.of("red", "blue", "neutral", "dark"))
                        && model.currencies().get(2).primary(),
                "all four currencies must retain canonical fixed wallet slots");
        final String rendered = PlainTextComponentSerializer.plainText().serialize(
                new IceSmpHudRenderer().render(model));
        check(rendered.contains("Parals 2.4k") && rendered.contains("Hópihér 8")
                        && rendered.contains("Creutzér 120") && rendered.contains("Csontveret 0"),
                "wallet must label and render every currency, including zero balances");
        check(model.classHud().metrics().size() == 2 && model.classHud().slots().size() == 2,
                "typed generic metrics and slots must survive into the display model");
        final IceSmpHudModel incompleteWallet = new IceSmpHudModel(
                model.faction(), model.factionTheme(), model.factionAccent(),
                model.className(), model.classLevel(), model.balance(), model.hasClass(),
                model.resource(), model.resourceMax(), model.resourcePercent(), model.resourceName(),
                model.event(), List.of(model.currencies().getFirst()), model.classHud());
        final String normalized = PlainTextComponentSerializer.plainText().serialize(
                new IceSmpHudRenderer().render(incompleteWallet));
        check(normalized.contains("Parals 2.4k") && normalized.contains("Hópihér 0")
                        && normalized.contains("Creutzér 0") && normalized.contains("Csontveret 0"),
                "renderer must fill missing wallet projections with fixed zero-balance slots");
        try {
            model.currencies().add(new HudManager.HudCurrency("dark", "Csontveret", "1", false));
            throw new AssertionError("wallet snapshot must be immutable");
        } catch (final UnsupportedOperationException expected) { }
    }

    private static void factionThemeProjectionSelectsEveryFrame() {
        final IceSmpHudModel baseline = model(56, 100, 56);
        final List<String> themes = List.of("ice", "ember", "frost", "guild", "lich");
        for (int index = 0; index < themes.size(); index++) {
            final IceSmpHudModel themed = new IceSmpHudModel(
                    baseline.faction(), themes.get(index), baseline.factionAccent(),
                    baseline.className(), baseline.classLevel(), baseline.balance(),
                    baseline.hasClass(), baseline.resource(), baseline.resourceMax(),
                    baseline.resourcePercent(), baseline.resourceName(), baseline.event(),
                    baseline.currencies(), baseline.classHud());
            final String rendered = PlainTextComponentSerializer.plainText().serialize(
                    new IceSmpHudRenderer().render(themed));
            check(rendered.indexOf(0xE100 + index) >= 0,
                    "HUD theme did not select frame index " + index + " for " + themes.get(index));
        }
    }

    private static void conditionalDetailRowAndDeathKnightLayoutStayCompact() throws Exception {
        final IceSmpHudRenderer renderer = new IceSmpHudRenderer();
        final IceSmpHudModel compact = model(56, 100, 56);
        final String compactRendered = PlainTextComponentSerializer.plainText().serialize(
                renderer.render(compact));
        check(compactRendered.indexOf('\uE106') < 0,
                "empty supplementary detail frame must not render");

        final ClassHudState detailedState = new ClassHudState("wizard", "elementalist", "Elementalista",
                "Rúnaszövés", "Hangolás", "harc", "Korona", 0, 0,
                List.of("Rúnaszövés", "Hangolás", "Tűz"),
                List.of(
                        ClassHudMetric.value("runewaving", "Rúnaszövés", "4/5", 4, 5, "active"),
                        ClassHudMetric.value("attunement", "Hangolás", "72", 72, 100, "ready"),
                        ClassHudMetric.value("attunement_fire", "Tűz", "72", 72, 100, "fire")),
                List.of());
        final IceSmpHudModel detailed = new IceSmpHudModel(
                compact.faction(), compact.factionTheme(), compact.factionAccent(), "Varázsló", 42,
                compact.balance(), true, compact.resource(), compact.resourceMax(),
                compact.resourcePercent(), "Mana", compact.event(), compact.currencies(), detailedState);
        final String detailedRendered = PlainTextComponentSerializer.plainText().serialize(
                renderer.render(detailed));
        check(detailedRendered.indexOf('\uE106') >= 0 && detailedRendered.contains("Tűz 72"),
                "supplementary detail frame must render only with a third metric");

        final String rendererSource = read("src/main/java/hu/taliann/icesmp/hud/IceSmpHudRenderer.java");
        check(rendererSource.contains("RUNE_PANEL_FONT")
                        && rendererSource.contains("glyph(HudComponent.DK_RUNES")
                        && rendererSource.contains("glyph(HudComponent.CHARGES")
                        && rendererSource.contains("WALLET_PANEL_COMPACT_FONT")
                        && rendererSource.contains("CURRENCY_COMPACT_FONT")
                        && !rendererSource.contains("compactWalletLayout")
                        && rendererSource.contains("if (index >= 6) break")
                        && rendererSource.contains("-244 + index * 18")
                        && !rendererSource.contains("model.classHud().mechanicPrimary(), accent, 132"),
                "large DK rune icons must replace counters and compact wallet fonts must collapse empty detail space");
        final String deathKnight = read(
                "src/main/java/hu/taliann/icesmp/deathknight/DeathKnightGameplayService.java");
        check(deathKnight.contains("\"rune_wheel\", \"Rúnakör\", \"Rúnák\"")
                        && deathKnight.contains("\"Fagyjel \" + marks")
                        && !deathKnight.contains("\"Fagyjel \" + marks + \"/\" + maximum")
                        && !deathKnight.contains("\"rune_wheel\", \"Rúnakör\", \"Rúnák V\" + blood"),
                "structured DK HUD must not duplicate per-rune counts as text");
    }

    private static void layoutGeometryStaysInsideArtCompartments() {
        check(IceSmpHudRenderer.RESOURCE_SEGMENT_ADVANCE * IceSmpHudRenderer.SEGMENTS <= 184,
                "resource bar must stay inside the full-width channel");
        check(IceSmpHudRenderer.METRIC_SEGMENT_ADVANCE * IceSmpHudRenderer.SEGMENTS == 96,
                "metric bars must exactly fill one modular half-panel");
        check(IceSmpHudRenderer.RESOURCE_BAR_X == -194
                        && IceSmpHudRenderer.RESOURCE_BAR_X
                        + IceSmpHudRenderer.RESOURCE_SEGMENT_ADVANCE * IceSmpHudRenderer.SEGMENTS <= -31,
                "resource bar must stay inside the reviewed full-width trough");
        check(IceSmpHudRenderer.PRIMARY_METRIC_BAR_X == -242
                        && IceSmpHudRenderer.PRIMARY_METRIC_BAR_X
                        + IceSmpHudRenderer.METRIC_SEGMENT_ADVANCE * IceSmpHudRenderer.SEGMENTS <= -146,
                "primary metric bar must stay inside the left panel");
        check(IceSmpHudRenderer.SECONDARY_METRIC_BAR_X == -129
                        && IceSmpHudRenderer.SECONDARY_METRIC_BAR_X
                        + IceSmpHudRenderer.METRIC_SEGMENT_ADVANCE * IceSmpHudRenderer.SEGMENTS <= -31,
                "secondary metric bar must stay inside the right panel");
        check(IceSmpHudRenderer.TEXT_ADVANCE == 6,
                "HUD text must retain the fixed six-pixel modular advance");
        check(IceSmpHudRenderer.LEVEL_CENTER_X == -36
                        && IceSmpHudRenderer.RESOURCE_TEXT_X == IceSmpHudRenderer.RESOURCE_BAR_X + 8
                        && IceSmpHudRenderer.EVENT_TEXT_WIDTH == 186,
                "level, resource label and full-width event text must stay inside their compartments");
        check(IceSmpHudRenderer.WALLET_LEFT_X + IceSmpHudRenderer.WALLET_TEXT_OFFSET
                        + IceSmpHudRenderer.WALLET_TEXT_WIDTH <= -139,
                "left wallet label must stop before the centre divider");
        check(IceSmpHudRenderer.WALLET_LEFT_X + IceSmpHudRenderer.WALLET_COLUMN_ADVANCE
                        + IceSmpHudRenderer.WALLET_TEXT_OFFSET
                        + IceSmpHudRenderer.WALLET_TEXT_WIDTH <= -19,
                "right wallet label must retain the frame-edge margin");
    }

    private static void packReadinessAndFallbackAreSafe() throws Exception {
        final String listener = read("src/main/java/hu/taliann/icesmp/listeners/ResourcePackListener.java");
        final String backend = read("src/main/java/hu/taliann/icesmp/hud/IceSmpHudBackend.java");
        final String hud = read("src/main/java/hu/taliann/icesmp/managers/HudManager.java");
        check(listener.contains("ConcurrentHashMap.newKeySet()")
                        && listener.contains("SUCCESSFULLY_LOADED")
                        && listener.contains("FAILED_RELOAD")
                        && listener.contains("isLoaded(final UUID"),
                "custom HUD readiness must come from a thread-safe pack status snapshot");
        check(backend.contains("resourcePackReady.test(player.getUniqueId())")
                        && backend.contains("survivalRenderer.fallback(playerState)")
                        && !backend.contains("PersistentDataContainer") && !backend.contains("PlayerProfile"),
                "backend must be display-only, pack-gated and preserve emergency vital text");
        check(hud.contains("!iceSmpHudActive(player)")
                        && hud.contains("renderIceSmpHud(player, snapshot);")
                        && hud.contains("applyFoliaCompactHud(player, snapshot);")
                        && hud.contains("tickSurvivalHud()")
                        && hud.contains("player.getRemainingAir()"),
                "first-party HUD must suppress duplicate native rendering while preserving its native fallback");
    }

    private static void specializationlessStateIsExplicit() {
        final IceSmpHudModel baseline = model(100, 100, 100);
        final ClassHudState noSpec = new ClassHudState("warrior", "", "",
                "Tempó Rendezett 0", "", "", "", 0, 0,
                List.of("Tempó Rendezett 0"),
                List.of(ClassHudMetric.value("battle_tempo", "Tempó", "Rendezett 0",
                        0, 100, "active")), List.of());
        final IceSmpHudModel noSpecModel = new IceSmpHudModel(
                baseline.faction(), baseline.factionTheme(), baseline.factionAccent(),
                "Harcos", 1, baseline.balance(), true,
                baseline.resource(), baseline.resourceMax(), baseline.resourcePercent(),
                "Düh", baseline.event(), baseline.currencies(), noSpec);
        final String rendered = PlainTextComponentSerializer.plainText().serialize(
                new IceSmpHudRenderer().render(noSpecModel));
        check(rendered.contains("Spec: nincs") && rendered.contains("Válassz profilt"),
                "a class selected without a specialization must be an explicit HUD state");
    }

    private static void removedExternalHudDependencyIsAbsent() throws Exception {
        final String removedPlugin = ("better" + "hud").toLowerCase(Locale.ROOT);
        final List<String> extensions = List.of(
                ".java", ".kt", ".kts", ".gradle", ".yml", ".yaml", ".json",
                ".md", ".py", ".properties", ".toml", ".txt");
        final List<Path> roots = List.of(
                Path.of(".github"), Path.of("gradle"), Path.of("src"), Path.of("docs"),
                Path.of("resource-pack"), Path.of("deploy"), Path.of("scripts"));
        for (final Path root : roots) {
            if (!Files.exists(root)) continue;
            try (var paths = Files.walk(root)) {
                for (final Path path : paths.filter(Files::isRegularFile).toList()) {
                    final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (extensions.stream().noneMatch(name::endsWith)) continue;
                    check(!Files.readString(path).toLowerCase(Locale.ROOT).contains(removedPlugin),
                            "removed external HUD dependency reference remains in " + path);
                }
            }
        }
        for (final Path path : List.of(Path.of("AGENTS.md"), Path.of("CLAUDE.md"),
                Path.of("build.gradle.kts"), Path.of("settings.gradle.kts"))) {
            check(!Files.readString(path).toLowerCase(Locale.ROOT).contains(removedPlugin),
                    "removed external HUD dependency reference remains in " + path);
        }
        check(!Files.exists(Path.of("deploy", removedPlugin))
                        && !Files.exists(Path.of("gradle", removedPlugin + "-dev.config.yml")),
                "removed external HUD package/config must not remain in the repository");
    }

    private static void visualPackageIsComplete() throws Exception {
        final String manifest = read("resource-pack/assets/icesmp_hud/hud-manifest.json");
        final String config = read("src/main/resources/config/general.yml");
        check(manifest.contains("\"fixed_segment_count\": 12")
                        && manifest.contains("\"text_advance\": 6")
                        && manifest.contains("\"text_font\": \"Inter SemiBold\"")
                        && manifest.contains("\"text_oversample\": 8")
                        && manifest.contains("\"text_source_resolution\": [")
                        && manifest.contains("\"resource_segment_advance\": 13")
                        && manifest.contains("\"metric_segment_advance\": 8")
                        && manifest.contains("\"layout_y\"")
                        && manifest.contains("\"layout_x\"")
                        && manifest.contains("\"wallet_slots\": 4")
                        && manifest.contains("\"wallet_columns\": 2")
                        && manifest.contains("\"wallet_rows\": 2")
                        && manifest.contains("\"detail_metrics_conditional\": true")
                        && manifest.contains("\"compact_wallet_anchor_y\": 178")
                        && manifest.contains("\"compact_wallet_anchor_delta\": -23")
                        && manifest.contains("\"rune_panel_size\": 18")
                        && manifest.contains("\"layout_color_payload_bits\": 14")
                        && manifest.contains("\"layout_scale_variants\"")
                        && manifest.contains("\"vanilla_health_hidden\": true")
                        && manifest.contains("\"vanilla_armor_hidden\": true")
                        && manifest.contains("\"vanilla_food_hidden\": true")
                        && manifest.contains("\"vanilla_oxygen_hidden\": true")
                        && manifest.contains("\"hardcore_hearts_overridden\": false"),
                "pack manifest must retain fixed bars, wallet capacity and complete survival coverage");
        check(config.contains("icesmp-hud:") && config.contains("hide-vanilla-health: true")
                        && config.contains("hide-vanilla-armor: true")
                        && config.contains("hide-vanilla-food: true")
                        && config.contains("hide-vanilla-oxygen: true")
                        && config.contains("refresh-ticks: 2")
                        && !config.contains("armor-display-cap")
                        && config.contains("player-group:")
                        && config.contains("target-group:")
                        && config.contains("party-group:"),
                "all hidden vanilla survival values must have an enabled custom replacement");
        final Path frames = Path.of("dev-assets/icesmp-hud/source/frames-v3.png");
        final var image = ImageIO.read(frames.toFile());
        check(image != null && image.getWidth() == 1200 && image.getHeight() == 160
                        && image.getColorModel().hasAlpha(),
                "normalized v3 frame atlas must retain all five fixed HUD themes");
        check(Files.isRegularFile(Path.of("resource-pack/assets/minecraft/shaders/core/rendertype_text.vsh"))
                        && Files.isRegularFile(Path.of("resource-pack/assets/icesmp_hud/font/space.json")),
                "standalone shader and BMP spacing font must be packaged by the first-party HUD");
        final String vertexShader = read(
                "resource-pack/assets/minecraft/shaders/core/rendertype_text.vsh");
        final String fragmentShader = read(
                "resource-pack/assets/minecraft/shaders/core/rendertype_text.fsh");
        check(vertexShader.startsWith("#version 330")
                        && vertexShader.contains("<minecraft:dynamictransforms.glsl>")
                        && vertexShader.contains("<minecraft:projection.glsl>")
                        && vertexShader.contains("<minecraft:globals.glsl>")
                        && vertexShader.contains("vec2 hudScale = vec2(responsiveScale) * ui / ScreenSize")
                        && vertexShader.contains("const float HUD_LAYOUT_SCALES[16]")
                        && vertexShader.contains("int layoutCode = (packedColor.r & 15)")
                        && vertexShader.contains("vec2 selectedHudScale = hudScale * layoutScale")
                        && vertexShader.contains("topLeft = id >= 11 && id <= 15")
                        && vertexShader.contains("ScreenSize.x / 2560.0")
                        && vertexShader.contains("clipPosition.x = -clipPosition.w")
                        && vertexShader.contains("layoutYOffset * responsiveScale * layoutScale")
                        && vertexShader.contains("fog_spherical_distance(pos)")
                        && !vertexShader.contains("uniform int FogShape"),
                "HUD vertex shader must implement the Minecraft 1.21.11 UBO contract");
        check(fragmentShader.startsWith("#version 330")
                        && fragmentShader.contains("<minecraft:dynamictransforms.glsl>")
                        && fragmentShader.contains("apply_fog(color")
                        && !fragmentShader.contains("uniform vec4 FogColor")
                        && !fragmentShader.contains("linear_fog("),
                "HUD fragment shader must not redeclare 1.21.11 Fog UBO members or call legacy fog");
        final String generator = read("scripts/generate_icesmp_hud_assets.py");
        final String survivalGenerator = read("scripts/generate_icesmp_survival_hud_assets.py");
        check(generator.contains("frames-v3.png")
                        && generator.contains("mechanics-core-v3.png")
                        && generator.contains("mechanics-spec-v3.png")
                        && generator.contains("HUD_FRAME_WIDTH = 240")
                        && generator.contains("TEXT_LOGICAL_WIDTH = 5")
                        && generator.contains("TEXT_OVERSAMPLE = 8")
                        && generator.contains("Inter-SemiBold.ttf")
                        && generator.contains("currency_lower")
                        && generator.contains("text_wallet_lower")
                        && generator.contains("runes_panel")
                        && generator.contains("wallet_panel_compact")
                        && generator.contains("currency_compact"),
                "v3 art, compact typography, conditional details and compact DK runes must remain generator-backed");
        check(survivalGenerator.contains("SURVIVAL_CANVAS_HEIGHT = 120")
                        && survivalGenerator.contains("HEART_SPRITES")
                        && survivalGenerator.contains("hardcore_hearts_overridden")
                        && !survivalGenerator.contains("hardcore_full.png"),
                "isolated survival generator must cover regular sprites without hardcore overrides");
        final String survivalManifest = read(
                "resource-pack/assets/icesmp_hud/survival-hud-manifest.json");
        check(survivalManifest.contains("\"anchor\": \"top_left\"")
                        && survivalManifest.contains("\"version\": 2")
                        && survivalManifest.contains("\"panel_size\": [")
                        && survivalManifest.contains("\"target_panel_size\": [")
                        && survivalManifest.contains("\"party_max_rows\": 4")
                        && survivalManifest.contains("\"health_segments\": 20")
                        && survivalManifest.contains("\"mini_segments\": 10")
                        && survivalManifest.contains("\"armor_display\": \"flat_value\"")
                        && survivalManifest.contains("\"air_display\": \"only_when_depleted\"")
                        && survivalManifest.contains("\"default_scale\": 1.0")
                        && survivalManifest.contains("\"text_font\": \"Inter SemiBold\"")
                        && survivalManifest.contains("\"text_oversample\": 8")
                        && survivalManifest.contains(
                                "\"text_atlas\": \"icesmp_hud:hud/survival/text-atlas.png\"")
                        && survivalManifest.contains("\"hardcore_hearts_overridden\": false"),
                "frame module manifest must retain its responsive top-left complete layout");
        final var survivalTextAtlas = ImageIO.read(Path.of(
                "resource-pack/assets/icesmp_hud/textures/hud/survival/text-atlas.png").toFile());
        check(survivalTextAtlas != null && survivalTextAtlas.getWidth() == 768
                        && survivalTextAtlas.getHeight() == 672,
                "frame text atlas must retain complete Hungarian target/party typography");
        for (final String font : List.of("panel", "health_segments", "mini_segments", "icons",
                "player_name", "text_header", "text_percent", "text_stats",
                "target_header", "target_status", "target_health_segments", "target_health",
                "target_resource_segments", "target_stats", "party_header",
                "party_health_segments", "party_health_text", "party_resource_segments",
                "party_status")) {
            check(Files.isRegularFile(Path.of(
                    "resource-pack/assets/icesmp_hud/font/survival", font + ".json")),
                    "missing isolated survival font: " + font);
        }
        for (final String sprite : List.of("heart/container.png", "heart/full.png",
                "heart/absorbing_full.png", "heart/poisoned_full.png", "heart/withered_full.png",
                "heart/frozen_full.png", "armor_full.png", "food_full.png", "air.png")) {
            final var replacement = ImageIO.read(Path.of(
                    "resource-pack/assets/minecraft/textures/gui/sprites/hud", sprite).toFile());
            check(replacement != null && replacement.getWidth() == 9 && replacement.getHeight() == 9
                            && replacement.getColorModel().hasAlpha()
                            && ((replacement.getRGB(0, 0) >>> 24) & 0xff) == 0,
                    "vanilla survival replacement must stay transparent 9x9: " + sprite);
        }
        try (var heartSprites = Files.list(Path.of(
                "resource-pack/assets/minecraft/textures/gui/sprites/hud/heart"))) {
            check(heartSprites.noneMatch(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).contains("hardcore")),
                    "hardcore heart sprites must remain untouched on the non-hardcore server");
        }
        final String classesConfig = read("src/main/resources/content/progression/classes.yml");
        final int healthSection = classesConfig.indexOf("\nhealth:\n");
        final int healthDisplay = classesConfig.indexOf("\n  display:\n", healthSection);
        check(healthSection >= 0 && healthDisplay > healthSection
                        && classesConfig.substring(healthSection, healthDisplay)
                        .contains("enabled: true")
                        && classesConfig.contains("normalize: false")
                        && classesConfig.contains("scale-heals: true"),
                "HP scaling must ship active while retaining real current/max HUD values");
        for (final String lichFrame : List.of(
                "player_lich.png", "target_player_lich.png", "party_lich.png")) {
            final var frame = ImageIO.read(Path.of(
                    "resource-pack/assets/icesmp_hud/textures/hud/survival", lichFrame).toFile());
            check(frame != null && (frame.getRGB(12, 1) & 0x00FFFFFF) == 0xA955E8,
                    "DARK survival frame must retain the canonical purple palette: " + lichFrame);
        }
        check(Files.isRegularFile(Path.of(
                        "dev-assets/icesmp-hud/source/LICENSE_INTER")),
                "Inter must retain its bundled OFL license");
        for (final String largeGlyph : List.of("frame-hud-guest.png", "frame-hud-red.png",
                "frame-hud-blue.png", "frame-hud-neutral.png", "frame-hud-dark.png",
                "wallet-strip.png", "detail-strip.png")) {
            final var glyphImage = ImageIO.read(Path.of(
                    "resource-pack/assets/icesmp_hud/textures/hud", largeGlyph).toFile());
            check(glyphImage != null && glyphImage.getWidth() <= 256 && glyphImage.getHeight() <= 256,
                    "bitmap glyph must fit Minecraft's 256x256 font stitcher: " + largeGlyph);
        }
        final var textAtlas = ImageIO.read(Path.of(
                "resource-pack/assets/icesmp_hud/textures/hud/text-atlas.png").toFile());
        check(textAtlas != null && textAtlas.getWidth() == 640 && textAtlas.getHeight() == 768,
                "HUD text atlas must retain the 8x antialiased Hungarian glyph source");
        for (final String icon : List.of("class-wizard.png", "class-none.png", "rune-blood-ready.png",
                "charge-ready.png", "currency-neutral.png",
                "mechanic-warrior-battle_tempo-active.png",
                "mechanic-demon_hunter-sigil-ready.png",
                "mechanic-priest-marrow-spent.png", "mechanic-wizard-court-alert.png")) {
            final var iconImage = ImageIO.read(Path.of(
                    "resource-pack/assets/icesmp_hud/textures/hud", icon).toFile());
            check(iconImage != null && iconImage.getWidth() == 64 && iconImage.getHeight() == 64
                            && ((iconImage.getRGB(63, 63) >>> 24) & 0xff) == 1,
                    "dynamic HUD icons must retain fixed logical width: " + icon);
        }
        check(manifest.contains("\"warrior:battle_tempo\"")
                        && manifest.contains("\"demon_hunter:sigil\"")
                        && manifest.contains("\"wizard:court\"")
                        && manifest.contains("\"alert\""),
                "all class-qualified mechanic icons and visual states must be manifest-backed");
    }

    private static IceSmpHudModel model(final int resource, final int maximum, final int percent) {
        final ClassHudState state = new ClassHudState("death_knight", "frost", "Fagyhozó",
                "Rúnák", "Fagyjel 2/5", "harc", "Dérrobbanás", 2, 6,
                List.of("Rúnák", "Fagyjel"),
                List.of(
                        ClassHudMetric.value("rune_wheel", "Rúnakör", "Rúnák", 2, 6, "active"),
                        ClassHudMetric.value("frost_marks", "Fagyjel", "2/5", 2, 5, "building")),
                List.of(
                        new ClassHudSlot("rune_1", "blood", "ready", 100, "Vér"),
                        new ClassHudSlot("rune_2", "frost", "regenerating", 40, "Fagy")));
        return new IceSmpHudModel("Menedék vendége", "ice", "66B5A3", "Halállovag", 12, "120", true,
                resource, maximum, percent, "Runikus Erő", "nyugalom",
                List.of(
                        new HudManager.HudCurrency("red", "Parázsló Parals", "2.4k", false),
                        new HudManager.HudCurrency("blue", "Hópihér-veret", "8", false),
                        new HudManager.HudCurrency("neutral", "Creutzér", "120", true),
                        new HudManager.HudCurrency("dark", "Csontveret", "0", false)), state);
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static int count(final String value, final char needle) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == needle) count++;
        }
        return count;
    }

    private static int carrierAdvance(final Component component, final Key inheritedFont) {
        final Key font = component.style().font() == null
                ? inheritedFont : component.style().font();
        int advance = 0;
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            final int[] codePoints = text.content().codePoints().toArray();
            for (final int codePoint : codePoints) advance += glyphAdvance(font, codePoint);
        }
        for (final Component child : component.children()) {
            advance += carrierAdvance(child, font);
        }
        return advance;
    }

    private static int glyphAdvance(final Key font, final int codePoint) {
        if (font == null) throw new AssertionError("HUD glyph has no explicit font");
        final String id = font.asString();
        if ("icesmp_hud:space".equals(id)) {
            return codePoint - IceSmpHudRenderer.SPACE_FIRST + IceSmpHudRenderer.SPACE_MIN;
        }
        if ("icesmp_hud:survival/panel".equals(id)) {
            return codePoint >= 0xEB05 && codePoint <= 0xEB0E
                    ? TargetHudRenderer.PANEL_ADVANCE : SurvivalHudRenderer.PANEL_ADVANCE;
        }
        if (id.equals("icesmp_hud:survival/health_segments")
                || id.equals("icesmp_hud:survival/target_health_segments")) {
            return SurvivalHudRenderer.HEALTH_SEGMENT_ADVANCE;
        }
        if (id.endsWith("mini_segments") || id.endsWith("resource_segments")
                || id.endsWith("party_health_segments")) {
            return SurvivalHudRenderer.MINI_SEGMENT_ADVANCE;
        }
        if ("icesmp_hud:survival/icons".equals(id)) return SurvivalHudRenderer.ICON_ADVANCE;
        if (id.startsWith("icesmp_hud:survival/")) return SurvivalHudRenderer.TEXT_ADVANCE;
        throw new AssertionError("Unknown top-left HUD font: " + id);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
