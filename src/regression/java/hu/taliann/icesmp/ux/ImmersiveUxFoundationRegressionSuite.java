package hu.taliann.icesmp.ux;

import java.util.List;

import static hu.taliann.icesmp.ux.MusicDirector.MusicContext;
import static hu.taliann.icesmp.ux.MusicDirector.Type;

/** Dependency-light contracts for deterministic UX arbitration and semantic tooltip rendering. */
public final class ImmersiveUxFoundationRegressionSuite {
    private static int assertions;

    public static void main(final String[] args) {
        musicPriorityAndTieBreak();
        tooltipSectionReplacementAndDeduplication();
        System.out.println("Immersive UX foundation regression suite passed. assertions=" + assertions);
    }

    private static void musicPriorityAndTieBreak() {
        final MusicContext ambient = new MusicContext("ambient", Type.AMBIENT, 10, "icesmp:ambient", 1, 1, true);
        final MusicContext boss = new MusicContext("boss", Type.BOSS, 80, "icesmp:boss", 1, 1, true);
        final MusicContext bossAlt = new MusicContext("boss-alt", Type.BOSS, 80, "icesmp:boss-alt", 1, 1, true);
        check(MusicDirector.select(List.of(ambient, boss)) == boss, "priority selection");
        check(MusicDirector.select(List.of(boss, bossAlt)) == bossAlt, "deterministic id tie break");
    }

    private static void tooltipSectionReplacementAndDeduplication() {
        final var header = net.kyori.adventure.text.Component.text("header");
        final var fact = net.kyori.adventure.text.Component.text("fact");
        final var oldFacts = TooltipEngine.generated(TooltipEngine.SectionId.ARCHAEOLOGY, 70, List.of(fact));
        final var newFacts = TooltipEngine.generated(TooltipEngine.SectionId.ARCHAEOLOGY, 70, List.of(fact, fact));
        final var rendered = TooltipEngine.replace(List.of(
                TooltipEngine.Section.of(TooltipEngine.SectionId.HEADER, 0, List.of(header)),
                oldFacts), newFacts);
        check(rendered.equals(List.of(header, fact)), "section replacement/deduplication");
    }

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
