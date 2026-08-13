package hu.taliann.icesmp.hud;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Presentation-authority, isolation, mutation-boundary and layout shader regressions. */
public final class HudEditorRegressionSuite {
    private HudEditorRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        permissionAndProductionGateAreFailClosed();
        editorCopyLivesInTheMessageLayer();
        sessionsAndPreviewsArePlayerIsolated();
        resetUndoApplyAndCancelAreExact();
        invalidConfigFallsBackFieldByField();
        everyRenderedComponentHasIndependentLayout();
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
        final String menus = read("src/main/java/hu/taliann/icesmp/gui/CommandMenus.java");
        check(menus.contains("player.hasPermission(Permissions.HUD_EDITOR)")
                        && menus.contains("\"OPEN:hud edit\""),
                "the admin command menu exposes the HUD editor on its canonical permission");
        final String messageManager = read(
                "src/main/java/hu/taliann/icesmp/utils/MessageManager.java");
        check(messageManager.contains("\"hud\"")
                        && java.nio.file.Files.isRegularFile(java.nio.file.Path.of(
                                "src/main/resources/messages/hud.yml")),
                "HUD messages are a bundled message group");
    }

    private static void editorCopyLivesInTheMessageLayer() throws Exception {
        final YamlConfiguration messages = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/messages/hud.yml").toFile());
        for (final HudComponent component : HudComponent.editorTargets()) {
            check(!messages.getString("messages.hud-component-" + component.id(), "").isBlank(),
                    "HUD component label is missing from messages/hud.yml: " + component.id());
        }
        for (final String key : List.of(
                "hud-editor-player-only", "hud-editor-no-permission", "hud-editor-config-disabled",
                "hud-editor-cancelled", "hud-editor-no-session", "hud-editor-save-stale",
                "hud-editor-save-success", "hud-editor-error-unknown-action",
                "hud-editor-error-usage-move", "hud-editor-error-direction",
                "hud-editor-error-margin-global", "hud-editor-error-usage-margin",
                "hud-editor-error-usage-step", "hud-editor-error-step",
                "hud-editor-error-usage-scale", "hud-editor-error-scale-mode",
                "hud-editor-error-scale-direction", "hud-editor-error-missing-component",
                "hud-editor-error-unknown-component", "hud-editor-error-global-visibility",
                "hud-editor-error-missing-preset", "hud-editor-error-unknown-preset",
                "hud-editor-error-usage-preview", "hud-editor-error-preview-axis",
                "hud-editor-error-preview-value", "hud-editor-error-invalid-change",
                "hud-editor-values-global", "hud-editor-values-component", "hud-editor-panel",
                "hud-editor-preview", "hud-editor-pack-required")) {
            check(!messages.getString("messages." + key, "").isBlank(),
                    "HUD editor copy is missing from messages/hud.yml: " + key);
        }
        final String command = read("src/main/java/hu/taliann/icesmp/commands/HudCommand.java");
        check(!command.contains("Component.text(\"")
                        && !command.contains("new IllegalArgumentException(\""),
                "HUD command reintroduced player-visible inline copy");
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
        editor.select(first, HudComponent.EVENT_TEXT);
        editor.move(first, 1, 1);
        check(editor.session(first).orElseThrow().working().xOffsetPixels() == 10
                        && editor.session(first).orElseThrow().working().yOffsetPixels() == 6
                        && editor.session(first).orElseThrow().working()
                        .componentLayout(HudComponent.EVENT_TEXT).xOffsetPixels() == 10,
                "the selected player's global and component preview layouts must update live");
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
        editor.select(player, HudComponent.WALLET);
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
        final HudComponentLayout invalidComponent = HudComponentLayout.fromConfigValues(
                "bad", 999, Double.POSITIVE_INFINITY, "true");
        check(invalidComponent.equals(HudComponentLayout.defaults()),
                "malformed component fields must fail back independently to safe defaults");
    }

    private static void everyRenderedComponentHasIndependentLayout() throws Exception {
        HudLayoutSnapshot layout = HudLayoutSnapshot.defaults();
        for (final HudComponent component : HudComponent.editableValues()) {
            layout = layout.withComponent(component, new HudComponentLayout(
                    component.ordinal(), -component.ordinal(), 3, true));
            check(layout.componentLayout(component).xOffsetPixels() == component.ordinal(),
                    "component transform must remain independently addressable: " + component.id());
        }
        final HudLayoutSnapshot before = layout;
        final HudLayoutSnapshot moved = layout.move(HudComponent.EVENT_TEXT, 5, -10)
                .changeScale(HudComponent.EVENT_TEXT, 1)
                .toggleVisibility(HudComponent.EVENT_TEXT);
        check(moved.componentLayout(HudComponent.EVENT_TEXT).xOffsetPixels()
                        == before.componentLayout(HudComponent.EVENT_TEXT).xOffsetPixels() + 5
                        && moved.componentLayout(HudComponent.EVENT_TEXT).yOffsetPixels()
                        == before.componentLayout(HudComponent.EVENT_TEXT).yOffsetPixels() - 10
                        && !moved.componentLayout(HudComponent.EVENT_TEXT).visible()
                        && moved.componentLayout(HudComponent.WALLET)
                        .equals(before.componentLayout(HudComponent.WALLET)),
                "editing one component may not move, scale or hide another component");

        final String config = read("src/main/resources/config/general.yml");
        final String renderer = read("src/main/java/hu/taliann/icesmp/hud/IceSmpHudRenderer.java");
        final String manager = read("src/main/java/hu/taliann/icesmp/managers/HudManager.java");
        for (final HudComponent component : HudComponent.editableValues()) {
            check(config.contains("        " + component.id() + ":")
                            && renderer.contains("HudComponent." + component.name()),
                    "every editable component needs config defaults and a renderer consumer: "
                            + component.id());
        }
        check(manager.contains("layout.components.\" + component.id()")
                        && manager.contains("overrides.put(path + \".visible\"")
                        && manager.contains("IceSmpHudModel.from(snapshot), configuredHudLayout(), visible"),
                "component transforms and visibility must round-trip through validated config");
    }

    private static void layoutAndPreviewSnapshotsAreImmutable() {
        check(HudLayoutSnapshot.class.isRecord() && HudComponentLayout.class.isRecord()
                        && HudPreviewSelection.class.isRecord()
                        && HudEditorStateMachine.Session.class.isRecord(),
                "global/component layout, selection and session must remain immutable records");
        try {
            HudLayoutSnapshot.defaults().components().clear();
            throw new AssertionError("component layout map must be immutable");
        } catch (final UnsupportedOperationException expected) { }
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
        final HudLayoutSnapshot componentLayout = layout.withComponent(HudComponent.EVENT_TEXT,
                new HudComponentLayout(9, 12, 2, true));
        check(componentLayout.anchoredX(HudComponent.EVENT_TEXT, -214) == -197
                        && componentLayout.shaderCode(HudComponent.EVENT_TEXT) == (7 << 9) + 231,
                "component X/Y/scale must be composed into its own renderer payload");
        final TextColor componentEncoded = IceSmpHudRenderer.encodeLayoutColor(
                TextColor.color(0xF0D88D), componentLayout, HudComponent.EVENT_TEXT);
        check(IceSmpHudRenderer.decodeLayoutCode(componentEncoded)
                        == componentLayout.shaderCode(HudComponent.EVENT_TEXT),
                "each component must transport its own exact layout payload to the shader");
    }

    private static void generatedShaderVariantsMatchRuntimeContract() throws Exception {
        final String shader = read("resource-pack/assets/minecraft/shaders/core/rendertype_text.vsh");
        final String generator = read("scripts/generate_icesmp_hud_assets.py");
        final String manifest = read("resource-pack/assets/icesmp_hud/hud-manifest.json");
        final String scales = "0.75, 0.90, 1.00, 1.15, 1.25, 1.40, 1.60, 1.80";
        check(shader.contains("HUD_LAYOUT_SCALES[8]") && shader.contains(scales)
                        && generator.contains("HUD_LAYOUT_SCALES = (" + scales + ")")
                        && generator.contains("python3 -m pip install Pillow")
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
