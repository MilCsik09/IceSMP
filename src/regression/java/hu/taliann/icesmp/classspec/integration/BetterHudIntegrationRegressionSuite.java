package hu.taliann.icesmp.classspec.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Dependency-free contract audit for the optional BetterHud/PAPI projection. */
public final class BetterHudIntegrationRegressionSuite {
    private BetterHudIntegrationRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        immutableSnapshotContract();
        classMappingsAndAuthorityBoundary();
        classMechanicCoverageContract();
        placeholderAndFallbackContract();
        packagedLayoutContract();
        System.out.println("BetterHud integration regression suite passed.");
    }

    private static void immutableSnapshotContract() {
        final ClassHudMechanics empty = ClassHudMechanics.empty();
        check(empty.primary().text().isEmpty() && empty.secondary().text().isEmpty()
                        && empty.metrics().size() == 2,
                "missing class runtime data must produce a safe immutable empty projection");
        final ArrayList<String> mutable = new ArrayList<>(List.of("Tempo 42"));
        final ClassHudMetric metric = ClassHudMetric.value(
                "tempo", "Tempo", "Tempo 42", 42, 100, "heated");
        final ClassHudSlot slot = new ClassHudSlot(
                "rune_blood_1", "blood", "regenerating", 37, "Blood");
        final ClassHudState state = new ClassHudState("warrior", "berserker", "Berserker",
                "Tempo 42", "Blood 2/5", "combat", "overdrive", 2, 5, mutable,
                List.of(metric), List.of(slot));
        mutable.clear();
        check(state.mechanics().size() == 1 && state.charges() == 2 && state.chargesMax() == 5,
                "snapshot must defensively copy and retain generic fields");
        check(state.metrics().getFirst().percent() == 42
                        && state.slots().getFirst().progress() == 37,
                "typed metrics and discrete slots must survive the immutable snapshot");
        final ClassHudMechanics charged = ClassHudMechanics.of(
                ClassHudMetric.value("tempo", "Tempó", "Tempó 40", 40, 100, "active"),
                ClassHudMetric.value("combo", "Kombó", "Kombó 3/5", 3, 5, "building"),
                "", "", 3, 5);
        check(charged.slots().size() == 5
                        && charged.slots().stream().limit(3).allMatch(slotValue -> "ready".equals(slotValue.state()))
                        && charged.slots().stream().skip(3).allMatch(slotValue -> "spent".equals(slotValue.state())),
                "generic discrete charges must become a bounded visual slot row");
        try {
            state.mechanics().add("mutation");
            throw new AssertionError("mechanics list must be immutable");
        } catch (final UnsupportedOperationException expected) { }
        try {
            state.slots().add(slot);
            throw new AssertionError("slots list must be immutable");
        } catch (final UnsupportedOperationException expected) { }
    }

    private static void classMappingsAndAuthorityBoundary() throws Exception {
        final String runtime = read("src/main/java/hu/taliann/icesmp/classspec/integration/BukkitClassSpecRuntimeAdapter.java");
        check(count(runtime, "new ClassHudStateAdapter(JobType.") == 13
                        && count(runtime, "::hudState") == 13,
                "all 13 classes must map structured state through class-specific Java adapters");
        check(!runtime.contains("BetterHud") && !runtime.contains("PersistentDataContainer"),
                "class adapter must neither mutate BetterHud nor establish PDC authority");
        final String adapter = read("src/main/java/hu/taliann/icesmp/classspec/integration/ClassHudStateAdapter.java");
        check(!adapter.contains("split(") && !adapter.contains("Pattern.compile")
                        && adapter.contains("ClassHudMechanics"),
                "generic adapter must consume typed state rather than parse rendered text");
        final String deathKnight = read("src/main/java/hu/taliann/icesmp/deathknight/DeathKnightGameplayService.java");
        check(deathKnight.contains("addRuneSlots") && deathKnight.contains("rechargePercent"),
                "Death Knight must project individual ready/spent/regenerating rune slots");
        check(read("src/main/java/hu/taliann/icesmp/warrior/WarriorGameplayService.java")
                        .contains("aftermathActive(now)")
                        && read("src/main/java/hu/taliann/icesmp/evoker/EvokerGameplayService.java")
                        .contains("lastEssenceColor()")
                        && read("src/main/java/hu/taliann/icesmp/assassin/AssassinGameplayService.java")
                        .contains("isEchoArmed(now)")
                        && read("src/main/java/hu/taliann/icesmp/priest/PriestGameplayService.java")
                        .contains("isConverting()")
                        && read("src/main/java/hu/taliann/icesmp/warlock/WarlockGameplayService.java")
                        .contains("threadTarget(now)")
                        && read("src/main/java/hu/taliann/icesmp/wizard/WizardGameplayService.java")
                        .contains("lastSchool()")
                        && read("src/main/java/hu/taliann/icesmp/archer/ArcherGameplayService.java")
                        .contains("preyTargetId().isPresent()")
                        && read("src/main/java/hu/taliann/icesmp/monk/MonkGameplayService.java")
                        .contains("linkLabels()")
                        && read("src/main/java/hu/taliann/icesmp/assassin/AssassinGameplayService.java")
                        .contains("heldToxins()"),
                "class adapters must expose the audited transient state needed for combat decisions");
    }

    private static void placeholderAndFallbackContract() throws Exception {
        final String papi = read("src/main/java/hu/taliann/icesmp/integration/IceSMPPlaceholders.java");
        final String hud = read("src/main/java/hu/taliann/icesmp/managers/HudManager.java");
        final String bridge = read("src/main/java/hu/taliann/icesmp/classspec/integration/BetterHudSnapshotBridge.java");
        check(papi.contains("hudManager.snapshot(player.getUniqueId())")
                        && !papi.contains("getPlayer()") && !papi.contains("getPersistentDataContainer"),
                "async PAPI path must read only the immutable cache");
        for (final String key : List.of("class_spec", "class_mechanic_primary", "class_mechanic_secondary",
                "class_state", "class_proc", "class_charges", "class_charges_max")) {
            check(papi.contains("\"" + key + "\""), "missing generic placeholder: " + key);
        }
        check(papi.contains("CLASS_HUD_METRIC_CHANNELS")
                        && papi.contains("tertiary") && papi.contains("quinary")
                        && papi.contains("class_metric_count")
                        && papi.contains("class_slot_([1-9])_"),
                "PAPI must expose typed generic metric and slot channels");
        check(hud.contains("!betterHudActive(player) && job != null")
                        && hud.contains("betterHudReady.get()")
                        && hud.contains("betterHudPlayers.contains(player.getUniqueId())")
                        && bridge.contains("HUD_ID = \"icesmp_class_hud\"")
                        && bridge.contains("current.hud().invoke(hudManager, HUD_ID)")
                        && bridge.contains("if (hudPlayer == null) return false;"),
                "native class row must be suppressed only after the IceSMP BetterHud HUD exists");
    }

    private static void classMechanicCoverageContract() throws Exception {
        final Map<String, List<String>> expected = Map.ofEntries(
                Map.entry("warrior/WarriorGameplayService.java", List.of(
                        "battle_tempo", "blood_frenzy", "aftermathActive(now)", "guard", "oathTargetLabel")),
                Map.entry("evoker/EvokerGameplayService.java", List.of(
                        "empower", "resonance", "lastEssenceColor()", "imprint", "isEchoArmed(now)")),
                Map.entry("archer/ArcherGameplayService.java", List.of(
                        "wind_read", "precision_chain", "preyTargetId().isPresent()", "bond")),
                Map.entry("shaman/ShamanGameplayService.java", List.of(
                        "totem_wheel", "resonance", "maelstrom", "tide", "blessingSide")),
                Map.entry("monk/MonkGameplayService.java", List.of(
                        "flow", "combo_chain", "stagger", "mist_threads", "linkLabels()")),
                Map.entry("paladin/PaladinGameplayService.java", List.of(
                        "conviction", "beacon", "judgement_marks", "shield_charge")),
                Map.entry("demonhunter/DemonHunterGameplayService.java", List.of(
                        "load", "fragments", "isMomentumArmed", "pain", "armedSigils")),
                Map.entry("druid/DruidGameplayService.java", List.of(
                        "harmony", "combo", "balance", "bark", "seeds", "isAutumnWindowArmed")),
                Map.entry("priest/PriestGameplayService.java", List.of(
                        "litany", "shield_web", "isConverting()", "marrow", "madness")),
                Map.entry("deathknight/DeathKnightGameplayService.java", List.of(
                        "rune_wheel", "blood_memory", "frost_marks", "plague", "addRuneSlots")),
                Map.entry("assassin/AssassinGameplayService.java", List.of(
                        "opening", "toxin", "heldToxins()", "detection", "isEchoArmed(now)", "infection")),
                Map.entry("warlock/WarlockGameplayService.java", List.of(
                        "soul_debt", "curses", "threadTarget(now)", "embers", "demons")),
                Map.entry("wizard/WizardGameplayService.java", List.of(
                        "runewaving", "lastSchool()", "attunement_fire", "attunement_frost",
                        "attunement_arcane", "court")));
        for (final Map.Entry<String, List<String>> entry : expected.entrySet()) {
            final String source = read("src/main/java/hu/taliann/icesmp/" + entry.getKey());
            for (final String token : entry.getValue()) {
                check(source.contains(token), "missing class HUD mechanic mapping: "
                        + entry.getKey() + " -> " + token);
            }
        }
    }

    private static void packagedLayoutContract() throws Exception {
        final String layout = Files.list(Path.of("deploy/betterhud/layouts"))
                .filter(path -> path.getFileName().toString().endsWith(".yml"))
                .sorted().map(path -> {
                    try { return Files.readString(path); }
                    catch (final java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
                }).collect(java.util.stream.Collectors.joining("\n"));
        final String hud = read("deploy/betterhud/huds/icesmp-class-hud.yml");
        final String config = read("deploy/betterhud/config.yml");
        final String images = read("deploy/betterhud/images/icesmp-class-images.yml");
        final String popup = read("deploy/betterhud/popups/icesmp-class-proc-popup.yml");
        final String bridge = read("src/main/java/hu/taliann/icesmp/classspec/integration/BetterHudSnapshotBridge.java");
        final String launcher = read("gradle/run-folia.init.gradle");
        final String devConfig = read("gradle/betterhud-dev.config.yml");
        check(layout.contains("[string:icesmp_resource_current]")
                        && layout.contains("[string:icesmp_resource_max]")
                        && layout.contains("[string:icesmp_resource_current]<#75819A>//[string:icesmp_resource_max]")
                        && layout.contains("[string:icesmp_class_proc]"),
                "layout must expose resource, mechanics and proc text");
        check(!layout.contains("second: \"''\"")
                        && layout.contains("second: \"'RED'\"")
                        && layout.contains("second: \"'death_knight'\""),
                "BetterHud 1.14.1 string conditions must use quoted literals and avoid empty expressions");
        check(layout.contains("frame_red:") && layout.contains("frame_blue:")
                        && layout.contains("frame_neutral:") && layout.contains("frame_dark:")
                        && layout.contains("icesmp_resource_fill")
                        && layout.contains("use-legacy-format: false")
                        && layout.contains("rune_progress_8:")
                        && layout.contains("metric_primary_track:")
                        && layout.contains("metric_quinary_fill:")
                        && layout.contains("charge_9_ready:")
                        && layout.contains("string:icesmp_faction_id")
                        && images.contains("value: \"number:icesmp_resource_current\"")
                        && images.contains("number:icesmp_class_slot_8_progress")
                        && images.contains("number:icesmp_class_metric_quinary_value"),
                "layout must include faction skins, generic metric/charge visuals and DK slots");
        for (final String asset : List.of("frame-red.png", "frame-blue.png", "frame-neutral.png",
                "frame-dark.png", "class-death_knight.png", "rune-blood-ready.png",
                "rune-frost-ready.png", "rune-death-ready.png", "resource-track.png", "resource-fill.png",
                "metric-track.png", "metric-fill.png", "metric-mini-track.png", "metric-mini-fill.png",
                "charge-ready.png", "charge-spent.png")) {
            check(Files.isRegularFile(Path.of("deploy/betterhud/assets/icesmp", asset)),
                    "missing BetterHud visual asset: " + asset);
        }
        for (final String asset : List.of("class-death_knight.png", "rune-blood-ready.png",
                "rune-frost-ready.png", "rune-death-ready.png")) {
            final var image = javax.imageio.ImageIO.read(Path.of("deploy/betterhud/assets/icesmp", asset).toFile());
            check(image.getWidth() == 64 && image.getHeight() == 64,
                    "class and rune visual sources must remain 64x64: " + asset);
        }
        for (final String asset : List.of("frame-hud-guest.png", "frame-hud-red.png",
                "frame-hud-blue.png", "frame-hud-neutral.png", "frame-hud-dark.png")) {
            final var image = javax.imageio.ImageIO.read(Path.of("deploy/betterhud/assets/icesmp", asset).toFile());
            check(image.getWidth() == 204 && image.getHeight() == 126,
                    "render-safe BetterHud frame must remain 204x126: " + asset);
        }
        check(hud.contains("icesmp_main_layout")
                        && !hud.contains("icesmp_identity_layout")
                        && !hud.contains("icesmp_resource_layout")
                        && !hud.contains("icesmp_world_layout")
                        && !hud.contains("icesmp_mechanic_layout")
                        && hud.contains("icesmp_hud_visible")
                        && hud.contains("y: 0") && !hud.contains("y: 100")
                        && layout.contains("outline: true")
                        && layout.contains("icesmp_main_layout:")
                        && layout.contains("x: -218") && layout.contains("scale: 1.0"),
                "HUD package must use one shared readable upper-right canvas for every generic module");
        try (var layouts = Files.list(Path.of("deploy/betterhud/layouts"))) {
            final List<String> persistentFiles = layouts
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("icesmp-") && name.endsWith(".yml"))
                    .sorted().toList();
            check(persistentFiles.equals(List.of("icesmp-main-layout.yml", "icesmp-proc-layout.yml")),
                    "stale independent IceSMP layout canvases must not survive generation: " + persistentFiles);
        }
        check(!read("src/main/java/hu/taliann/icesmp/managers/HudManager.java").contains("🌕 VÉRHOLD"),
                "the Blood Moon bossbar must not use an unsupported astral emoji glyph");
        check(layout.contains("icesmp_class_proc_layout:")
                        && popup.contains("class: custom")
                        && popup.contains("name: icesmp_class_proc")
                        && popup.contains("name: icesmp_class_proc_layout")
                        && bridge.contains("showProcPopup(final Player player")
                        && bridge.contains("callEvent((Event) customEvent)"),
                "new immutable proc transitions must drive the optional BetterHud toast");
        check(config.contains("- icesmp_class_hud") && !config.contains("- test_hud"),
                "production BetterHud config must select only the IceSMP HUD");
        check(config.contains("enable-self-host: false") && config.contains("merge-with-external-resources: true"),
                "production pack must use the documented external merge pipeline");
        check(launcher.contains("prepareBetterHudForFolia")
                        && launcher.contains("betterhud-dev.config.yml")
                        && launcher.contains("icesmp.dev.mergedBetterHudPack=true")
                        && launcher.contains("targetLayoutFiles.findAll")
                        && launcher.contains("outputs.upToDateWhen { false }")
                        && launcher.contains("verifyBetterHudMirror")
                        && launcher.contains("Stale IceSMP BetterHud layout set")
                        && launcher.contains("IceSMP BetterHud layout mirror mismatch")
                        && launcher.contains("IceSMPResourcePack"),
                "runFolia must re-mirror and verify layouts without stale canvases before local pack delivery");
        check(devConfig.contains("- IceSMPResourcePack")
                        && devConfig.contains("- IceSMPExternalBase.zip")
                        && devConfig.indexOf("IceSMPResourcePack") < devConfig.indexOf("IceSMPExternalBase.zip")
                        && devConfig.contains("enable-self-host: false")
                        && devConfig.contains("force-update: false"),
                "development BetterHud pack must merge canonical IceSMP assets before delivery");
        check(launcher.contains("4900b0a9bed8db710143393916db3687e01def54")
                        && launcher.contains("External resource-pack SHA-1 mismatch"),
                "external development pack must be immutable and fail closed on hash mismatch");
        check(bridge.contains("variables.putAll(Map.copyOf(snapshot))")
                        && !bridge.contains("PersistentDataContainer")
                        && !bridge.contains("PlayerProfile"),
                "BetterHud bridge must only publish immutable display strings");
        for (final String key : List.of("icesmp_class_level", "icesmp_class_id", "icesmp_faction",
                "icesmp_faction_theme", "icesmp_balance", "icesmp_event", "icesmp_resource_current",
                "icesmp_resource_max", "icesmp_class_metric_count", "icesmp_class_metric_",
                "icesmp_class_slot_", "icesmp_hud_visible")) {
            check(read("src/main/java/hu/taliann/icesmp/managers/HudManager.java").contains("\"" + key),
                    "full BetterHud snapshot is missing: " + key);
        }
    }

    private static String read(final String path) throws Exception { return Files.readString(Path.of(path)); }
    private static int count(final String text, final String needle) {
        int count = 0, at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) { count++; at += needle.length(); }
        return count;
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
