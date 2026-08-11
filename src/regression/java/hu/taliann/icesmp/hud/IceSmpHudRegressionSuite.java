package hu.taliann.icesmp.hud;

import hu.taliann.icesmp.classspec.integration.ClassHudMetric;
import hu.taliann.icesmp.classspec.integration.ClassHudSlot;
import hu.taliann.icesmp.classspec.integration.ClassHudState;
import hu.taliann.icesmp.managers.HudManager;
import net.kyori.adventure.text.Component;
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
        walletAndClassContractIsGeneric();
        packReadinessAndFallbackAreSafe();
        removedExternalHudDependencyIsAbsent();
        visualPackageIsComplete();
        System.out.println("First-party IceSMP HUD regression suite passed.");
    }

    private static void fixedLayoutIsIndependentOfDynamicValues() throws Exception {
        final IceSmpHudRenderer renderer = new IceSmpHudRenderer();
        final Component empty = renderer.render(model(0, 120, 0));
        final Component full = renderer.render(model(120, 120, 100));
        check(!PlainTextComponentSerializer.plainText().serialize(empty).isBlank()
                        && !PlainTextComponentSerializer.plainText().serialize(full).isBlank(),
                "both empty and full resources must produce a HUD frame");
        check(IceSmpHudRenderer.SPACE_FIRST >= 0xE000
                        && IceSmpHudRenderer.SPACE_FIRST + IceSmpHudRenderer.SPACE_MAX
                        - IceSmpHudRenderer.SPACE_MIN <= 0xF8FF,
                "spacing glyphs must stay in BMP PUA and avoid supplementary-plane sentinels");
        final String source = read("src/main/java/hu/taliann/icesmp/hud/IceSmpHudRenderer.java");
        check(source.contains("append(space(-x - width))")
                        && !source.contains("primaryMetric()") && !source.contains("secondaryMetric()"),
                "every draw must return to origin and metrics must remain class-agnostic");
        final String hud = read("src/main/java/hu/taliann/icesmp/managers/HudManager.java");
        check(!hud.contains("snapshot.classHud().classId().isBlank()"),
                "guest/profile/event HUD must remain visible before class selection");
    }

    private static void walletAndClassContractIsGeneric() {
        final IceSmpHudModel model = model(56, 100, 56);
        check(model.currencies().size() == 3 && model.currencies().getFirst().primary(),
                "primary currency must remain first while positive foreign wallets follow");
        check(model.classHud().metrics().size() == 2 && model.classHud().slots().size() == 2,
                "typed generic metrics and slots must survive into the display model");
        try {
            model.currencies().add(new HudManager.HudCurrency("dark", "Csontveret", "1", false));
            throw new AssertionError("wallet snapshot must be immutable");
        } catch (final UnsupportedOperationException expected) { }
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
                        && !backend.contains("PersistentDataContainer") && !backend.contains("PlayerProfile"),
                "backend must be display-only and gated by the loaded pack");
        check(hud.contains("!iceSmpHudActive(player)")
                        && hud.contains("renderIceSmpHud(player, snapshot);")
                        && hud.contains("applyFoliaCompactHud(player, snapshot);"),
                "first-party HUD must suppress duplicate native rendering while preserving its native fallback");
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
                        && manifest.contains("\"wallet_slots\": 4")
                        && manifest.contains("\"vanilla_health_hidden\": false")
                        && manifest.contains("\"vanilla_armor_hidden\": false"),
                "pack manifest must retain fixed bars, wallet capacity and safe HP-rework gates");
        check(config.contains("icesmp-hud:") && config.contains("hide-vanilla-health: false")
                        && config.contains("hide-vanilla-armor: false"),
                "vanilla HUD removal must remain explicitly disabled until replacement coverage exists");
        final Path guest = Path.of("dev-assets/icesmp-hud/source/frame-guest-v2.png");
        final var image = ImageIO.read(guest.toFile());
        check(image != null && image.getWidth() >= 64 && image.getHeight() >= 64
                        && image.getColorModel().hasAlpha(),
                "Menedék frame donor must retain a transparent 64px-or-larger source");
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
                        && vertexShader.contains("clipPosition.x += clipPosition.w")
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
        check(generator.contains("guest_frame_with_canonical_layout")
                        && generator.contains("canonical_frames")
                        && generator.contains("Guest HUD changed the canonical content-grid geometry")
                        && generator.contains("HUD_FRAME_WIDTH = 240")
                        && generator.contains("TEXT_OVERSAMPLE = 4")
                        && generator.contains("DejaVuSans.ttf")
                        && generator.contains("dev-assets") && generator.contains("icesmp-hud"),
                "guest art must reuse canonical panel geometry from first-party source assets");
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
        check(textAtlas != null && textAtlas.getWidth() == 384 && textAtlas.getHeight() == 384,
                "HUD text atlas must retain the 4x antialiased Hungarian glyph source");
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
                "Rúnák V2 F2 H0", "Fagylánc", "harc", "Dérrobbanás", 2, 6,
                List.of("Rúnák", "Fagylánc"),
                List.of(
                        ClassHudMetric.value("frost_marks", "Fagyjel", "2/5", 2, 5, "building"),
                        ClassHudMetric.value("plague", "Pestis", "3/10", 3, 10, "active")),
                List.of(
                        new ClassHudSlot("rune_1", "blood", "ready", 100, "Vér"),
                        new ClassHudSlot("rune_2", "frost", "regenerating", 40, "Fagy")));
        return new IceSmpHudModel("Menedék vendége", "ice", "66B5A3", "Halállovag", 12, "120", true,
                resource, maximum, percent, "Runikus Erő", "nyugalom",
                List.of(
                        new HudManager.HudCurrency("neutral", "Creutzér", "120", true),
                        new HudManager.HudCurrency("red", "Parázsló Parals", "2.4k", false),
                        new HudManager.HudCurrency("blue", "Hópihér-veret", "8", false)), state);
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
