package hu.taliann.icesmp.quest;

import java.util.List;

/**
 * The first-join welcome dialog copy and the single rule that decides which copy a server gets.
 *
 * <p>Kept free of Bukkit so the decision can be proven directly. Servers keep their copied config
 * files across plugin updates, so an out-of-date {@code quests.yml} would otherwise pin the old
 * onboarding text forever. Recognising the previously bundled stock copy lets that text be
 * upgraded in memory, while any genuinely custom dialog is left exactly as the admin wrote it —
 * nothing is ever rewritten on disk.</p>
 */
public final class OnboardingWelcomeCopy {

    /** The stock copy bundled before the current /menu → /profile onboarding flow. */
    public static final List<String> LEGACY_LINES = List.of(
            "<gray>A <white>Fa árnyékában</white> írod a legendád. Első lépések:</gray>",
            "<yellow>•</yellow> <white>/kaszt</white> — válassz hivatást (13 kaszt).",
            "<yellow>•</yellow> <white>/faction</white> — a Menedékben kezdesz — állj a Láng vagy a Fagy zászlaja alá.",
            "<yellow>•</yellow> <white>/menu</white> — minden rendszer egy helyen.",
            "<gray>A haladásodat a <white>Haladás</white> képernyőn (L) is követheted.</gray>");

    /** Current first-join copy, aligned with the player guide and greenfield Profile v2. */
    public static final List<String> CURRENT_LINES = List.of(
            "<gray>Aetrinita visszahívott. A <white>Menedék vendégeként</white> kezded az utad.</gray>",
            "<yellow>•</yellow> <white>/menu</white> — innen eléred a fő rendszereket.",
            "<yellow>•</yellow> <white>/profile</white> — itt választasz kasztot, szakmát, később specializációt és talenteket.",
            "<red>!</red> <gray>A kasztválasztás tartós döntés; teljes kaszt-resethez adminisztrátor kell.</gray>",
            "<yellow>•</yellow> <white>Kövesd a kezdő küldetést</white> — játék közben végigvezet az alapokon.",
            "<gray>A frakció külön, tudatos döntés. Addig Menedék-vendég vagy, nem automatikus neutral tag.</gray>");

    private OnboardingWelcomeCopy() {
    }

    /** Unconfigured servers and stale stock copies both get the current text; custom copy wins. */
    public static List<String> resolve(final List<String> configured) {
        return configured == null || configured.isEmpty() || isLegacyStockDialog(configured)
                ? CURRENT_LINES : List.copyOf(configured);
    }

    /**
     * Recognises the retired stock dialog. Beyond the exact bundled text it also accepts a lightly
     * edited copy, identified by the two things only that stock text said: it pointed at the
     * removed {@code /kaszt} shortcut and it framed the faction choice as two flags. Both marks
     * must be present, so an unrelated custom dialog is never mistaken for the old default.
     */
    public static boolean isLegacyStockDialog(final List<String> configured) {
        if (configured == null || configured.isEmpty()) {
            return false;
        }
        if (configured.equals(LEGACY_LINES)) {
            return true;
        }
        final boolean pointsAtRemovedKasztShortcut = configured.stream()
                .anyMatch(line -> line != null && line.contains("<white>/kaszt</white>"));
        final boolean limitsFactionChoiceToTwoFlags = configured.stream()
                .anyMatch(line -> line != null && line.contains("Láng vagy a Fagy"));
        return pointsAtRemovedKasztShortcut && limitsFactionChoiceToTwoFlags;
    }
}
