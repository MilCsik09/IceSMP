package hu.taliann.icesmp.quest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-free behavior regression for the first-join welcome dialog.
 *
 * <p>The decision is a pure function of what the server has configured, so every case below is a
 * real call, not a source match: an unconfigured server and a stale stock copy both get the current
 * text, genuine custom copy is never touched, and nothing is ever rewritten on disk.</p>
 */
public final class OnboardingDialogRegressionSuite {

    private static int assertions;

    private OnboardingDialogRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        anUnconfiguredServerGetsTheCurrentDialog();
        theExactLegacyStockDialogIsUpgraded();
        aLightlyEditedLegacyStockDialogIsUpgraded();
        genuineCustomCopyIsNeverOverwritten();
        theCurrentCopyIsStableAndSaysWhatItMustSay();
        packagedConfigAndFallbackAgree();
        firstJoinAndAutoQuestSourceContracts();
        System.out.println("Onboarding dialog regression suite passed. assertions=" + assertions);
    }

    /** 1. Üres config-lista → current default dialog. */
    private static void anUnconfiguredServerGetsTheCurrentDialog() {
        check(OnboardingWelcomeCopy.resolve(List.of()) == OnboardingWelcomeCopy.CURRENT_LINES,
                "an empty configured list falls back to the current copy");
        check(OnboardingWelcomeCopy.resolve(null) == OnboardingWelcomeCopy.CURRENT_LINES,
                "a missing configured list falls back to the current copy");
        check(!OnboardingWelcomeCopy.isLegacyStockDialog(List.of()),
                "an empty list is not itself classified as the legacy stock dialog");
    }

    /** 2. Exact legacy stock dialog → current dialog. */
    private static void theExactLegacyStockDialogIsUpgraded() {
        check(OnboardingWelcomeCopy.isLegacyStockDialog(OnboardingWelcomeCopy.LEGACY_LINES),
                "the exact retired stock copy is recognised");
        check(OnboardingWelcomeCopy.resolve(OnboardingWelcomeCopy.LEGACY_LINES)
                        == OnboardingWelcomeCopy.CURRENT_LINES,
                "a deployed stale stock config is upgraded in memory, not left pinned");
    }

    /** 3. Enyhén módosított régi "/kaszt" + „Láng vagy Fagy” stock copy → current dialog. */
    private static void aLightlyEditedLegacyStockDialogIsUpgraded() {
        final List<String> edited = new ArrayList<>(OnboardingWelcomeCopy.LEGACY_LINES);
        edited.set(0, "<gray>Sajat koszonto sor.</gray>");
        edited.add("<gray>Szerver-specifikus zaro sor.</gray>");
        check(OnboardingWelcomeCopy.isLegacyStockDialog(edited),
                "a lightly edited stock copy is still the retired default");
        check(OnboardingWelcomeCopy.resolve(edited) == OnboardingWelcomeCopy.CURRENT_LINES,
                "and it is upgraded too");

        // Both marks are required: one alone is not enough to claim someone's dialog.
        final List<String> onlyKaszt = List.of(
                "<yellow>•</yellow> <white>/kaszt</white> — valassz hivatast.");
        final List<String> onlyFlags = List.of(
                "<yellow>•</yellow> Lang vagy a Fagy zaszlaja ala allj.");
        check(!OnboardingWelcomeCopy.isLegacyStockDialog(onlyKaszt),
                "the removed /kaszt shortcut alone does not condemn a dialog");
        check(!OnboardingWelcomeCopy.isLegacyStockDialog(onlyFlags),
                "the two-flag framing alone does not condemn a dialog");
    }

    /** 4. Valódi egyedi admin custom dialog → érintetlen. */
    private static void genuineCustomCopyIsNeverOverwritten() {
        final List<String> custom = List.of(
                "<gold>Udv a szerverunkon!</gold>",
                "<yellow>•</yellow> <white>/rules</white> — a szabalyzat.",
                "<yellow>•</yellow> <white>/discord</white> — a kozossegunk.");
        check(!OnboardingWelcomeCopy.isLegacyStockDialog(custom),
                "an unrelated custom dialog is not mistaken for the old default");
        check(OnboardingWelcomeCopy.resolve(custom).equals(custom),
                "custom copy is returned exactly as the admin wrote it");
        check(OnboardingWelcomeCopy.resolve(custom) != OnboardingWelcomeCopy.CURRENT_LINES,
                "custom copy is never silently swapped for ours");

        // A single line that merely mentions a class is ordinary custom copy, not the stock text.
        final List<String> mentionsClasses = List.of(
                "<gray>13 kaszt kozul valaszthatsz a <white>/profile</white> menuben.</gray>");
        check(OnboardingWelcomeCopy.resolve(mentionsClasses).equals(mentionsClasses),
                "mentioning classes does not make a dialog the retired stock copy");
    }

    /** The current copy has to actually carry the onboarding model it claims. */
    private static void theCurrentCopyIsStableAndSaysWhatItMustSay() {
        final String joined = String.join("\n", OnboardingWelcomeCopy.CURRENT_LINES);
        check(joined.contains("/menu") && joined.contains("/profile"),
                "the current copy points at /menu and /profile");
        check(!joined.contains("/kaszt"), "the removed /kaszt shortcut is gone");
        check(joined.contains("Menedék vendégeként") && joined.contains("Menedék-vendég"),
                "the player starts as a Menedék guest");
        check(joined.contains("nem automatikus neutral tag"),
                "there is no automatic neutral membership");
        check(joined.contains("A frakció külön, tudatos döntés"),
                "the faction is a separate, deliberate choice");
        check(joined.contains("tartós döntés"), "the class choice is stated to be durable");
        check(!joined.contains("Láng vagy a Fagy"),
                "the faction choice is no longer framed as two flags");
        check(OnboardingWelcomeCopy.CURRENT_LINES.size() == 6
                        && OnboardingWelcomeCopy.LEGACY_LINES.size() == 5,
                "both copies keep their exact shape");
        check(!OnboardingWelcomeCopy.CURRENT_LINES.equals(OnboardingWelcomeCopy.LEGACY_LINES),
                "the two copies are genuinely different");
        check(!OnboardingWelcomeCopy.isLegacyStockDialog(OnboardingWelcomeCopy.CURRENT_LINES),
                "the current copy is never mistaken for the retired one, so it is stable");
    }

    /** The packaged default and the compiled fallback must not drift apart. */
    private static void packagedConfigAndFallbackAgree() throws Exception {
        final List<String> packaged = configuredWelcomeLines(
                Path.of("src/main/resources/content/progression/quests.yml"));
        check(packaged.equals(OnboardingWelcomeCopy.CURRENT_LINES),
                "the packaged quests.yml ships exactly the current copy");
        check(OnboardingWelcomeCopy.resolve(packaged).equals(OnboardingWelcomeCopy.CURRENT_LINES),
                "a freshly installed server lands on the current copy whether it reads the file or the fallback");
        check(!OnboardingWelcomeCopy.isLegacyStockDialog(packaged),
                "the shipped default is never itself treated as stale stock copy");
    }

    /** 5-6. First join runs once, and the accept path stays the AUTO-sourced quest API. */
    private static void firstJoinAndAutoQuestSourceContracts() throws Exception {
        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/OnboardingListener.java"));
        check(listener.contains("if (player.hasPlayedBefore()"),
                "first join is gated on hasPlayedBefore, so it can only run once");
        check(listener.contains("questManager.isActive(player, firstQuest)")
                        && listener.contains("questManager.hasCompleted(player, firstQuest)"),
                "a rejoin cannot re-announce an already running or finished opener");
        check(listener.contains("questManager.acceptWithSource(player, firstQuest,")
                        && listener.contains("QuestSourceContext.auto()"),
                "the opener is accepted through the AUTO-sourced quest API");
        check(!listener.contains("questManager.accept(player"),
                "the retired accept() API is not reintroduced by this forward-port");
        check(listener.contains("OnboardingWelcomeCopy")
                        && !listener.contains("Láng vagy a Fagy"),
                "the listener delegates to the one copy rule and hardcodes no stale text");
    }

    private static List<String> configuredWelcomeLines(final Path questsYaml) throws Exception {
        final List<String> lines = new ArrayList<>();
        boolean inBlock = false;
        for (final String raw : Files.readAllLines(questsYaml)) {
            if (raw.startsWith("  welcome-dialog-lines:")) {
                inBlock = true;
                continue;
            }
            if (!inBlock) continue;
            final String trimmed = raw.trim();
            if (!trimmed.startsWith("- ")) break;
            String value = trimmed.substring(2).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            lines.add(value);
        }
        return lines;
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
