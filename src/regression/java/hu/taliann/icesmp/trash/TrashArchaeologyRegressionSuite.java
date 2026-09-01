package hu.taliann.icesmp.trash;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Pure progression simulations plus source/lifecycle gates for Phase F. */
public final class TrashArchaeologyRegressionSuite {

    private static final Path ROOT = Path.of("src/main/java/hu/taliann/icesmp");
    private static final Path PROFILE = ROOT.resolve("trash/TrashArchaeologyProfileStore.java");
    private static final Path FACTS = ROOT.resolve("trash/TrashArchaeologyFactEngine.java");
    private static final Path LISTENER = ROOT.resolve("trash/TrashArchaeologyListener.java");
    private static final Path BRIDGE = ROOT.resolve("trash/TooltipPacketBridge_1_21_11.java");
    private static final Path CORE = ROOT.resolve("core/IceSMPCore.java");

    private TrashArchaeologyRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        preservesHiddenKnowledgeOnlyIdentity();
        simulatesNoveltyBreadthUnlockAndDuplicateSuppression();
        preservesLevelCurveAndBoundedProfileAuthority();
        preservesThirtyTickCancelableBrushGesture();
        preservesFactSecrecyAndHistoryRevisionKeys();
        preservesCanonicalPlayerOnlyOverlay();
        preservesHiddenDevAndProfileVisibilityBoundaries();
        preservesFoliaLifecycleWiring();
        System.out.println("Trash archaeology regression suite passed.");
    }

    private static void preservesHiddenKnowledgeOnlyIdentity() throws Exception {
        check(HiddenDiscipline.values().length == 1
                        && HiddenDiscipline.values()[0] == HiddenDiscipline.ARCHAEOLOGY,
                "hidden discipline vocabulary drifted");
        final String service = Files.readString(ROOT.resolve("trash/TrashArchaeologyService.java"));
        check(!service.contains("ProfessionType") && !service.contains("lootMultiplier")
                        && !service.contains("vendor") && !service.contains("combat"),
                "Archaeology gained a profession/gameplay reward coupling");
    }

    private static void simulatesNoveltyBreadthUnlockAndDuplicateSuppression() {
        TrashArchaeologyProfileStore.Profile profile =
                TrashArchaeologyProfileStore.Profile.empty();
        for (int inspection = 0; inspection < 10; inspection++) {
            final TrashArchaeologyProfileStore.Evidence evidence = evidence(
                    "item" + inspection, "family" + inspection % 4,
                    "domain" + inspection % 3,
                    inspection < 3, "base@0:fact" + inspection, 3, false);
            final TrashArchaeologyProfileStore.Commit commit =
                    TrashArchaeologyProfileStore.advance(profile, evidence);
            profile = commit.profile();
            check(!profile.unlocked() && commit.awardedInsight() == 0L,
                    "locked familiarity awarded visible insight");
        }
        check(profile.familiarity() == 10 && profile.families().size() == 4
                        && profile.domains().size() == 3
                        && profile.historicalInspections() == 3,
                "meaningful breadth simulation drifted");
        final TrashArchaeologyProfileStore.Evidence unlockEvidence = evidence(
                "unlock_item", "family0", "domain0", true, "base@1:higher", 5, true);
        final TrashArchaeologyProfileStore.Commit unlocked =
                TrashArchaeologyProfileStore.advance(profile, unlockEvidence);
        check(unlocked.unlockedNow() && unlocked.profile().unlocked()
                        && unlocked.profile().level() >= 1 && unlocked.awardedInsight() == 5L,
                "next novel higher-order fact did not unlock after prior breadth");
        final TrashArchaeologyProfileStore.Commit duplicate =
                TrashArchaeologyProfileStore.advance(unlocked.profile(), unlockEvidence);
        check(duplicate.profile().equals(unlocked.profile())
                        && duplicate.novelSignatures().isEmpty()
                        && duplicate.awardedInsight() == 0L && !duplicate.unlockedNow(),
                "duplicate knowledge was farmable");

        final TrashArchaeologyProfileStore.Profile almost = new TrashArchaeologyProfileStore.Profile(
                false, 0, 0L, 9, 3, Set.of("a", "b", "c", "d"),
                Set.of("x", "y", "z"), Set.of("old"));
        final TrashArchaeologyProfileStore.Commit premature =
                TrashArchaeologyProfileStore.advance(almost, evidence(
                        "premature_item", "a", "x", true, "base@2:higher", 5, true));
        check(!premature.profile().unlocked() && !premature.unlockedNow(),
                "current inspection incorrectly satisfied its own unlock breadth");

        final TrashArchaeologyProfileStore.Commit firstHistory =
                TrashArchaeologyProfileStore.advance(
                        TrashArchaeologyProfileStore.Profile.empty(), evidence(
                                "same_item", "metal", "court", true,
                                "same_item@1:repaired", 3, true));
        final TrashArchaeologyProfileStore.Commit revisedSameHistory =
                TrashArchaeologyProfileStore.advance(firstHistory.profile(), evidence(
                        "same_item", "metal", "court", true,
                        "same_item@2:vendor_cycle", 3, true));
        check(revisedSameHistory.profile().historicalInspections() == 1,
                "one history-bearing identity was counted as multiple historical items");
        final TrashArchaeologyProfileStore.Commit repeatedSameFact =
                TrashArchaeologyProfileStore.advance(revisedSameHistory.profile(), evidence(
                        "same_item", "metal", "court", true,
                        "same_item@65:repaired", 3, true));
        check(repeatedSameFact.profile().equals(revisedSameHistory.profile())
                        && repeatedSameFact.awardedInsight() == 0L,
                "a later history revision made the same information farmable");
    }

    private static void preservesLevelCurveAndBoundedProfileAuthority() throws Exception {
        check(TrashArchaeologyProfileStore.threshold(50) == 1_600L,
                "level 50 cumulative threshold drifted");
        long previous = 0L;
        for (int level = 1; level <= 50; level++) {
            final long current = TrashArchaeologyProfileStore.threshold(level);
            check(current > previous, "threshold curve is not strictly increasing");
            previous = current;
        }
        final String profile = Files.readString(PROFILE);
        require(profile, "ProfileSectionId.ACHIEVEMENTS", "Profile v2 section authority");
        require(profile, "EXTENSION_KEY = \"trash_archaeology\"", "hidden extension key");
        require(profile, "MAX_KNOWLEDGE = 4_096", "bounded knowledge ledger");
        require(profile, "mutateSectionConditional", "CAS mutation boundary");
        check(!profile.contains("YamlConfiguration") && !profile.contains("new File("),
                "parallel per-player YAML authority entered Archaeology");
    }

    private static void preservesThirtyTickCancelableBrushGesture() throws Exception {
        final String listener = Files.readString(LISTENER);
        require(listener, "INSPECTION_TICKS = 30", "1.5 second inspection duration");
        require(listener, "Material.BRUSH", "vanilla Brush main-hand gate");
        require(listener, "getItemInOffHand()", "offhand inspected item gate");
        require(listener, "startUsingItem(EquipmentSlot.HAND)", "Paper item-use state");
        require(listener, "PlayerStopUsingItemEvent", "early release cancellation");
        for (final String cancellation : List.of("InventoryClickEvent", "InventoryDragEvent",
                "PlayerItemHeldEvent", "PlayerSwapHandItemsEvent", "PlayerDropItemEvent",
                "PlayerDeathEvent")) {
            require(listener, cancellation, "cancellation boundary " + cancellation);
        }
        require(listener, "current.isSimilar(session.snapshot)", "item-swap snapshot guard");
        require(listener, "PRESENTATION_CADENCE = 5", "bounded fallback presentation cadence");
        check(!listener.contains("Bukkit.getScheduler()"),
                "legacy scheduler entered Archaeology gesture runtime");
    }

    private static void preservesFactSecrecyAndHistoryRevisionKeys() throws Exception {
        final String facts = Files.readString(FACTS);
        require(facts, "trashId + \"@\" + fact.evidenceRevision() + \":\" + fact.id()",
                "first-evidence-revision knowledge signature");
        final TrashArchaeologyFactEngine.Fact material = new TrashArchaeologyFactEngine.Fact(
                "material", TrashArchaeologyFactEngine.Category.MATERIAL,
                0, 1, false, 0L, "material");
        final TrashArchaeologyFactEngine.Fact repaired = new TrashArchaeologyFactEngine.Fact(
                "repaired", TrashArchaeologyFactEngine.Category.HISTORY,
                8, 3, true, 4L, "repaired");
        final TrashArchaeologyFactEngine.Evaluation evaluation =
                new TrashArchaeologyFactEngine.Evaluation("sample", 99L,
                        "metal", "court", true, List.of(material, repaired));
        check(evaluation.signature(material).equals("sample@0:material")
                        && evaluation.signature(repaired).equals("sample@4:repaired"),
                "unrelated history revision made existing facts novel again");
        require(facts, "minLevel() <= archaeologyLevel", "level-gated reinspection");
        require(facts, ".limit(8)", "bounded visible fact set");
        check(!facts.contains("Anomália") && !facts.contains("\"Trash Relic\"")
                        && !facts.contains("activation condition"),
                "player fact copy became a hidden-mechanic scanner");
    }

    private static void preservesCanonicalPlayerOnlyOverlay() throws Exception {
        final String bridge = Files.readString(BRIDGE);
        require(bridge, "ClientboundContainerSetSlotPacket", "single packet bridge");
        require(bridge, "OFFHAND_MENU_SLOT = 45", "offhand display slot");
        require(bridge, "OVERLAY_TICKS = 1_200L", "60 second overlay expiry");
        require(bridge, "canonicalSnapshot.clone()", "display-only clone");
        require(bridge, "sendCanonical(player)", "canonical resync");
        require(bridge, "if (access == null", "runtime probe fail-closed boundary");
        require(bridge, "if (previous != null) previous.cancel()",
                "single overlay-expiry task per player");
        require(bridge, "overlay.cancel()", "overlay expiry teardown");
        check(bridge.indexOf("items.refreshPresentation(display)")
                        < bridge.indexOf("meta.lore(lore)"),
                "presentation refresh erased the temporary observation block");
        check(!bridge.contains("setItemInOffHand") && !bridge.contains("setItem(45"),
                "overlay mutated server-authoritative inventory state");
    }

    private static void preservesHiddenDevAndProfileVisibilityBoundaries() throws Exception {
        final String command = Files.readString(ROOT.resolve("trash/TrashDevCommand.java"));
        final String rootCommand = Files.readString(ROOT.resolve("commands/IceSMPCommand.java"));
        final String profile = Files.readString(ROOT.resolve("gui/ProfileGUI.java"));
        require(rootCommand, "HiddenDevAuthority.mayUseHiddenContent(sender)",
                "hardcoded hidden-content authority gate");
        require(command, "List.of(\"unlock\", \"setlevel\", \"addinsight\", \"reset\"",
                "hidden Archaeology mutation surface");
        require(command, "\"inspect\", \"force\"", "hidden inspect/force surface");
        check(!command.contains("hasPermission(") && !command.contains("isOp()"),
                "normal permission/OP authority entered hidden Archaeology DEV");
        require(profile, "if (archaeology.unlocked())", "locked profile invisibility");
        require(profile, "label(\"Régészet\"", "unlocked level visibility");
        check(!profile.contains("insight()"), "exact insight leaked into /profile");
    }

    private static void preservesFoliaLifecycleWiring() throws Exception {
        final String core = Files.readString(CORE);
        require(core, "registerEvents(trashArchaeologyListener", "listener registration");
        require(core, "trashArchaeologyListener::shutdown", "reload/disable teardown");
        require(ROOT.resolve("listeners/PlayerSessionCleanupListener.java"),
                "trashArchaeologyListener", "disconnect cleanup registration");
        final String listener = Files.readString(LISTENER);
        require(listener, "player.getScheduler().runAtFixedRate", "player-owned session tick");
        require(listener, "target.getScheduler().run(plugin", "DEV target ownership hop");
        require(listener, "tooltip.clearPlayerState(playerId)", "overlay session cleanup");
    }

    private static TrashArchaeologyProfileStore.Evidence evidence(
            final String historicalIdentity, final String family, final String domain,
            final boolean historical,
            final String signature, final int insight, final boolean higherOrder) {
        return new TrashArchaeologyProfileStore.Evidence(
                historicalIdentity, family, domain, historical,
                List.of(new TrashArchaeologyProfileStore.Discovery(
                        signature, insight, higherOrder)));
    }

    private static void require(final Path source, final String token, final String description)
            throws Exception {
        require(Files.readString(source), token, description);
    }

    private static void require(final String source, final String token, final String description) {
        check(source.contains(token), "missing " + description + ": " + token);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
