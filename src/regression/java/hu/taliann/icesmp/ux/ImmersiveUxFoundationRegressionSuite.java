package hu.taliann.icesmp.ux;

import net.kyori.adventure.text.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static hu.taliann.icesmp.ux.MusicDirector.MusicContext;
import static hu.taliann.icesmp.ux.MusicDirector.Type;

/** Dependency-light contracts for deterministic UX arbitration and semantic tooltip rendering. */
public final class ImmersiveUxFoundationRegressionSuite {
    private static int assertions;

    public static void main(final String[] args) throws Exception {
        musicPriorityTieBreakAndDisabledFallback();
        tooltipSectionReplacementPreservesAuthoredSpacing();
        questDialogueCadenceDoesNotDoubleDelay();
        questChoicesBelongToDialogueCompletion();
        guiSessionStateAndClosedLifecycle();
        hiddenDevHarnessStaysGuardedAndPresentationOnly();
        System.out.println("Immersive UX foundation regression suite passed. assertions=" + assertions);
    }

    private static void musicPriorityTieBreakAndDisabledFallback() {
        final MusicContext ambient = new MusicContext(
                "ambient", Type.AMBIENT, 10, "icesmp:ambient", 1, 1, true);
        final MusicContext boss = new MusicContext(
                "boss", Type.BOSS, 80, "icesmp:boss", 1, 1, true);
        final MusicContext bossAlt = new MusicContext(
                "boss-alt", Type.BOSS, 80, "icesmp:boss-alt", 1, 1, true);
        check(MusicDirector.select(List.of(ambient, boss)) == boss, "priority selection");
        check(MusicDirector.select(List.of(boss, bossAlt)) == bossAlt,
                "deterministic id tie break");
        check(MusicDirector.select(List.of(ambient, boss), context -> context != boss) == ambient,
                "disabled high-priority context must fall back");
    }

    private static void tooltipSectionReplacementPreservesAuthoredSpacing() {
        final Component header = Component.text("header");
        final Component fact = Component.text("fact");
        final Component blank = Component.empty();
        final var oldFacts = TooltipEngine.generated(
                TooltipEngine.SectionId.ARCHAEOLOGY, 70, List.of(fact));
        final var newFacts = TooltipEngine.generated(
                TooltipEngine.SectionId.ARCHAEOLOGY, 70, List.of(fact, fact));
        final var rendered = TooltipEngine.replace(List.of(
                TooltipEngine.Section.of(TooltipEngine.SectionId.HEADER, 0,
                        List.of(header, blank, blank)),
                oldFacts), newFacts);
        check(rendered.equals(List.of(header, blank, blank, fact, fact)),
                "section replacement must preserve repeated authored lines and spacing");
        check(TooltipEngine.replaceSection(List.of(oldFacts), newFacts).equals(List.of(newFacts)),
                "section replacement keeps semantic section identity");
    }

    private static void questDialogueCadenceDoesNotDoubleDelay() {
        final var first = new DialogueEngine.DialogueNode(
                "first", "", Component.text("one"), 0L, 0L, true,
                player -> true, null, null);
        final var second = new DialogueEngine.DialogueNode(
                "second", "", Component.text("two"), 30L, 0L, true,
                player -> true, null, null);
        final var finalNode = new DialogueEngine.DialogueNode(
                "final", "", Component.text("three"), 30L, 0L, true,
                player -> true, null, null);
        final var finalWithChoice = new DialogueEngine.DialogueNode(
                "final-choice", "", Component.text("choice"), 30L, 30L, true,
                player -> true, null, null);
        check(DialogueEngine.waitAfter(first, second) == 30L,
                "quest line cadence doubled from 30 to 60 ticks");
        check(DialogueEngine.waitAfter(second, finalNode) == 30L,
                "subsequent quest line cadence doubled from 30 to 60 ticks");
        check(DialogueEngine.waitAfter(finalNode, null) == 0L,
                "final zero-duration quest line must not invent trailing dialogue delay");
        check(DialogueEngine.waitAfter(finalWithChoice, null) == 30L,
                "choice completion tail must preserve the legacy 30-tick post-line delay");
    }

    private static void questChoicesBelongToDialogueCompletion() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/QuestManager.java"));
        check(source.contains("List<String> lines, Runnable onComplete)"),
                "QuestManager dialogue adapter does not expose sequence completion ownership");
        final int adapter = source.indexOf("if (adapter != null && adapter.play(");
        final int fallback = source.indexOf("for (int i = 0; i < lines.size(); i++)", adapter);
        check(adapter >= 0 && fallback > adapter, "quest dialogue adapter/fallback boundary missing");
        final String adapterPath = source.substring(adapter, fallback);
        check(adapterPath.contains("List.copyOf(lines), completion")
                        && !adapterPath.contains("scheduleDialogueChoices"),
                "adapter path scheduled quest choices outside the dialogue session lifecycle");
        check(source.contains("return () -> sendChoices(player, sourceQuestId, choices);"),
                "quest choices are not emitted by the dialogue sequence completion callback");
    }

    private static void guiSessionStateAndClosedLifecycle() {
        final GuiSession session = new GuiSession(UUID.randomUUID(), "regression");
        session.putState("page-name", "inventory");
        check("inventory".equals(session.state("page-name", String.class)),
                "typed GUI state lookup");
        session.currentPage(3);
        check(session.currentPage() == 3, "GUI page state");
        check("inventory".equals(session.removeState("page-name")), "GUI state removal");
        session.close();
        check(session.closed() && session.state().isEmpty(), "GUI close clears session state");
        boolean rejected = false;
        try {
            session.getInventory();
        } catch (final IllegalStateException expected) {
            rejected = true;
        }
        check(rejected, "closed GUI must not manufacture a replacement inventory");
    }

    private static void hiddenDevHarnessStaysGuardedAndPresentationOnly() throws Exception {
        final Path root = Path.of("src/main/java/hu/taliann/icesmp");
        final String listener = Files.readString(root.resolve("ux/ImmersiveUxListener.java"));
        final String command = Files.readString(root.resolve("ux/UxDevCommand.java"));
        final String projection = Files.readString(root.resolve("trash/TooltipDevProjection.java"));

        check(listener.contains("HiddenDevAuthority.mayUseHiddenContent(event.getPlayer())")
                        && listener.contains("onHiddenUxDevCommand")
                        && listener.contains("\"icesmp\".equalsIgnoreCase(tokens[0])")
                        && listener.contains("\"dev\".equalsIgnoreCase(tokens[1])")
                        && listener.contains("\"ux\".equalsIgnoreCase(tokens[2])"),
                "hidden UX DEV route is not strictly authority-gated and namespaced");
        check(command.contains("HiddenDevAuthority.mayUseHiddenContent(player)"),
                "UX DEV command lost its defense-in-depth hidden authority gate");
        check(command.contains("dialogue.play(player")
                        && command.contains("music.push(player")
                        && command.contains("gui.open(player")
                        && command.contains("TooltipEngine.render(context, renderers)")
                        && command.contains("TooltipDevProjection.projectMainHand(player, display)"),
                "UX DEV harness no longer exercises all four immersive foundation paths");
        check(!command.contains("player.getInventory().setItem")
                        && !command.contains("setItemInMainHand"),
                "UX DEV tooltip preview started mutating canonical inventory state");
        check(projection.contains("TooltipPacketBridge_1_21_11.projectHand")
                        && !projection.contains("setItemInMainHand")
                        && !projection.contains("getInventory().setItem"),
                "DEV tooltip adapter bypassed the presentation-only packet bridge");
    }

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
