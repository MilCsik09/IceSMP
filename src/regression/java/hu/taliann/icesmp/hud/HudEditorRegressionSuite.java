package hu.taliann.icesmp.hud;

import net.kyori.adventure.text.format.TextColor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Presentation-authority, isolation, mutation-boundary and layout shader regressions. */
public final class HudEditorRegressionSuite {
    private HudEditorRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        permissionAndProductionGateAreFailClosed();
        sessionsAndPreviewsArePlayerIsolated();
        resetUndoApplyAndCancelAreExact();
        invalidConfigFallsBackFieldByField();
        layoutAndPreviewSnapshotsAreImmutable();
        rendererAppliesOffsetAndScalePayload();
        generatedShaderVariantsMatchRuntimeContract();
        previewCannotReachGameplayAuthority();
        fallbackAndReadinessRemainIntact();
        System.out.println("First-party HUD editor regression suite passed.");
    }

    private static void permissionAndProductionGateAreFailClosed() throws Exception {
        check(HudEditorAccessPolicy.decide(false, true, true)
                        == HudEditorAccessPolicy.Decision.PLAYER_ONLY,
                "console may not create an editor preview");
        check(HudEditorAccessPolicy.decide(true, false, true)
                        == HudEditorAccessPolicy.Decision.NO_PERMISSION,
                "permission is mandatory");
        check(HudEditorAccessPolicy.decide(true, true, false)
                        == HudEditorAccessPolicy.Decision.CONFIG_DISABLED,
                "production config gate is mandatory");
        check(HudEditorAccessPolicy.decide(true, true, true)
                        == HudEditorAccessPolicy.Decision.ALLOWED,
                "player, permission and config gate should allow the editor");
        final String config = read("src/main/resources/config/general.yml");
        final String permissions = read("src/main/java/hu/taliann/icesmp/core/Permissions.java");
        check(config.contains("editor:\n      enabled: false"),
                "the production editor gate must default to false");
        check(permissions.contains("HUD_EDITOR = \"icesmp.admin.hud-editor\"")
                        && permissions.contains("canonical.put(HUD_EDITOR"),
                "the editor permission must use the registered canonical scheme");
    }

    private static void sessionsAndPreviewsArePlayerIsolated() {
        final HudEditorStateMachine editor = new HudEditorStateMachine();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        editor.start(first, HudLayoutSnapshot.defaults(), 1, "a");
        editor.start(second, HudLayoutSnapshot.defaults(), 1, "a");
        editor.step(first, 10);
        editor.move(first, 1, -1);
        editor.previewClass(first, "wizard");
        editor.previewState(first, "wizard-attunement");
        check(editor.session(first).orElseThrow().working().xOffsetPixels() == 10
                        && editor.session(first).orElseThrow().working().yOffsetPixels() == 6,
                "the selected player's preview layout must update live");
        check(editor.session(second).orElseThrow().working().equals(HudLayoutSnapshot.defaults())
                        && editor.session(second).orElseThrow().preview().equals(HudPreviewSelection.defaults()),
                "one player's editor operations must not leak into another session");
        for (final String faction : HudPreviewSelection.FACTIONS) {
            for (final String playerClass : HudPreviewSelection.CLASSES) {
                final var model = HudPreviewCatalog.model(
                        new HudPreviewSelection(faction, playerClass, "representative"));
                check(model.classHud() != null && !model.faction().isBlank(),
                        "every faction/class preview must be synthetic and complete");
            }
        }
        for (final String state : HudPreviewSelection.STATES) {
            check(HudPreviewCatalog.model(new HudPreviewSelection("guest", "warrior", state)) != null,
                    "every representative preview state must be available: " + state);
        }
    }

    private static void resetUndoApplyAndCancelAreExact() {
        final HudEditorStateMachine editor = new HudEditorStateMachine();
        final UUID player = UUID.randomUUID();
        final HudLayoutSnapshot original = new HudLayoutSnapshot(7, -3, 20, 3);
        editor.start(player, original, 8, "fingerprint");
        editor.step(player, 5);
        final HudLayoutSnapshot moved = editor.move(player, -1, 1).working();
        check(editor.reset(player).working().equals(HudLayoutSnapshot.defaults()),
                "reset must select the safe default layout");
        check(editor.undo(player).working().equals(moved),
                "undo must restore the exact previous immutable layout");
        check(editor.apply(player).orElseThrow().equals(moved) && editor.session(player).isEmpty(),
                "apply must return working values and close the isolated session");
        editor.start(player, original, 8, "fingerprint");
        editor.move(player, 1, 0);
        check(editor.cancel(player).orElseThrow().equals(original) && editor.session(player).isEmpty(),
                "cancel must discard edits and return the opening layout");
    }

    private static void invalidConfigFallsBackFieldByField() {
        final HudLayoutSnapshot invalid = HudLayoutSnapshot.fromConfigValues(
                9999, "bad", -1, Double.NaN);
        check(invalid.equals(HudLayoutSnapshot.defaults()),
                "malformed and out-of-range config must use safe defaults");
        final HudLayoutSnapshot partial = HudLayoutSnapshot.fromConfigValues(12, 999, 24, 1.39D);
        check(partial.xOffsetPixels() == 12
                        && partial.yOffsetPixels() == HudLayoutSnapshot.DEFAULT_Y_OFFSET
                        && partial.safeMarginPixels() == 24
                        && partial.scalePermille() == 1400,
                "config validation must preserve valid fields and normalize supported scale variants");
    }

    private static void layoutAndPreviewSnapshotsAreImmutable() {
        check(HudLayoutSnapshot.class.isRecord() && HudPreviewSelection.class.isRecord()
                        && HudEditorStateMachine.Session.class.isRecord(),
                "layout, selection and session must remain immutable records");
        final var model = HudPreviewCatalog.model(HudPreviewSelection.defaults());
        try {
            model.currencies().add(model.currencies().getFirst());
            throw new AssertionError("synthetic wallet preview must be immutable");
        } catch (final UnsupportedOperationException expected) { }
        try {
            model.classHud().slots().add(model.classHud().slots().getFirst());
            throw new AssertionError("synthetic class runtime projection must be immutable");
        } catch (final UnsupportedOperationException expected) { }
    }

    private static void rendererAppliesOffsetAndScalePayload() {
        final HudLayoutSnapshot layout = new HudLayoutSnapshot(21, -37, 13, 7);
        check(layout.anchoredX(-254) == -246,
                "X offset and right-edge safety margin must both reach renderer coordinates");
        final TextColor encoded = IceSmpHudRenderer.encodeLayoutColor(TextColor.color(0x77DDF2), layout);
        check(IceSmpHudRenderer.decodeLayoutCode(encoded) == layout.shaderCode()
                        && ((encoded.value() >> 20) & 0xF) == 0x7,
                "renderer color transport must preserve visual high nibbles and exact layout payload");
        check(layout.shaderCode() == (7 << 9) + 219,
                "shader payload must contain signed 9-bit Y and 3-bit scale index");
    }

    private static void generatedShaderVariantsMatchRuntimeContract() throws Exception {
        final String shader = read("resource-pack/assets/minecraft/shaders/core/rendertype_text.vsh");
        final String generator = read("scripts/generate_icesmp_hud_assets.py");
        final String manifest = read("resource-pack/assets/icesmp_hud/hud-manifest.json");
        final String scales = "0.75, 0.90, 1.00, 1.15, 1.25, 1.40, 1.60, 1.80";
        check(shader.contains("HUD_LAYOUT_SCALES[8]") && shader.contains(scales)
                        && generator.contains("HUD_LAYOUT_SCALES = (" + scales + ")")
                        && manifest.contains("\"layout_scale_variants\"")
                        && manifest.contains("\"layout_color_payload_bits\": 12"),
                "runtime, build generator and manifest must share the limited scale variants");
        check(shader.contains("layoutYOffset * 2.0 * clipPosition.w / ScreenSize.y")
                        && shader.contains("vec2 selectedHudScale = hudScale * layoutScale"),
                "shader must actually apply selected Y and scale values");
    }

    private static void previewCannotReachGameplayAuthority() throws Exception {
        final String catalog = read("src/main/java/hu/taliann/icesmp/hud/HudPreviewCatalog.java");
        final String state = read("src/main/java/hu/taliann/icesmp/hud/HudEditorStateMachine.java");
        final List<String> forbidden = List.of("PlayerProfile", "CurrencyManager", "ResourceManager",
                "JobManager", "ClassSpecRuntime", "PersistentDataContainer", "setBalance(", "addXp(");
        for (final String token : forbidden) {
            check(!catalog.contains(token) && !state.contains(token),
                    "preview/editor may not depend on gameplay mutation authority: " + token);
        }
        check(catalog.contains("Creates immutable display fixtures")
                        && state.contains("presentation-only"),
                "the synthetic preview and presentation-only boundary must remain explicit");
    }

    private static void fallbackAndReadinessRemainIntact() throws Exception {
        final String manager = read("src/main/java/hu/taliann/icesmp/managers/HudManager.java");
        final String backend = read("src/main/java/hu/taliann/icesmp/hud/IceSmpHudBackend.java");
        check(manager.contains("applyFoliaCompactHud(player, snapshot)")
                        && manager.contains("!iceSmpHudActive(player)")
                        && backend.contains("resourcePackReady.test(player.getUniqueId())")
                        && backend.contains("hide(player)"),
                "Folia/native fallback and resource-pack readiness failover must remain intact");
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
