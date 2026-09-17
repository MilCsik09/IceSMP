package hu.taliann.icesmp.ux;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Focused source/resource contracts for the first player-visible tooltip presentation pass. */
public final class TooltipPresentationRegressionSuite {
    private static int assertions;

    private TooltipPresentationRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        customGlyphFontDoesNotLeakIntoReadableText();
        resourcePackDefinesEverySemanticGlyph();
        canonicalRendererUsesSemanticPresentationSections();
        canonicalItemsRetainNativeRarityTooltipStyle();
        devReferencePreviewUsesReviewedPresentation();
        System.out.println("Tooltip presentation regression suite passed. assertions=" + assertions);
    }

    private static void customGlyphFontDoesNotLeakIntoReadableText() {
        final Component icon = TooltipPresentation.icon(TooltipPresentation.Glyph.STATS);
        final Component heading = TooltipPresentation.sectionHeading(
                TooltipPresentation.Glyph.STATS, "Harcértékek", NamedTextColor.GOLD);
        check(TooltipPresentation.FONT.equals(icon.style().font()),
                "semantic icon must use the IceSMP tooltip font");
        check(heading.style().font() == null,
                "readable heading root must keep the client default font");
        check(heading.children().stream()
                        .filter(child -> TooltipPresentation.FONT.equals(child.style().font()))
                        .count() == 1L,
                "only the decorative icon may carry the private-use glyph font");
    }

    private static void resourcePackDefinesEverySemanticGlyph() throws Exception {
        final String font = Files.readString(Path.of(
                "resource-pack/assets/icesmp/font/tooltip.json"));
        check(font.contains("\"type\": \"bitmap\"")
                        && font.contains("minecraft:item/iron_sword.png")
                        && font.contains("minecraft:item/experience_bottle.png")
                        && font.contains("minecraft:item/amethyst_shard.png"),
                "tooltip font must map semantic glyphs to deterministic bitmap providers");
        for (final TooltipPresentation.Glyph glyph : TooltipPresentation.Glyph.values()) {
            final String hex = Integer.toHexString(glyph.character()).toLowerCase(Locale.ROOT);
            check(font.toLowerCase(Locale.ROOT).contains(hex),
                    "resource-pack tooltip font is missing glyph U+" + hex.toUpperCase(Locale.ROOT));
        }
    }

    private static void canonicalRendererUsesSemanticPresentationSections() throws Exception {
        final String renderer = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/itemization/ItemTooltipRenderer.java"));
        check(renderer.contains("TooltipEngine.render(sections)"),
                "canonical item tooltip must flow through the semantic TooltipEngine");
        check(renderer.contains("TooltipEngine.SectionId.PRIMARY_STATS")
                        && renderer.contains("TooltipEngine.SectionId.REQUIREMENTS")
                        && renderer.contains("TooltipEngine.SectionId.EFFECTS")
                        && renderer.contains("TooltipEngine.SectionId.SOCKETS")
                        && renderer.contains("TooltipEngine.SectionId.PROVENANCE"),
                "canonical renderer lost one of the reviewed semantic presentation sections");
        check(renderer.contains("TooltipPresentation.sectionHeading")
                        && renderer.contains("TooltipPresentation.withIcon"),
                "canonical renderer bypassed the resource-pack-backed presentation tokens");
        check(!renderer.contains("private static final String DIVIDER"),
                "presentation pass regressed to the old divider-heavy tooltip layout");
    }

    private static void canonicalItemsRetainNativeRarityTooltipStyle() throws Exception {
        final String identity = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/itemization/ItemIdentityService.java"));
        check(identity.contains("ItemDataFactory.applyTooltipStyleForRarity(item, template.rarity().id())"),
                "canonical item presentation must retain rarity-backed native tooltip_style");
    }

    private static void devReferencePreviewUsesReviewedPresentation() throws Exception {
        final String dev = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/ux/UxDevCommand.java"));
        check(dev.contains("Fagyott Őrségpenge")
                        && dev.contains("TooltipPresentation.sectionHeading")
                        && dev.contains("TooltipPresentation.Glyph.STATS")
                        && dev.contains("TooltipPresentation.Glyph.REQUIREMENTS")
                        && dev.contains("TooltipPresentation.Glyph.EFFECT")
                        && dev.contains("TooltipPresentation.Glyph.SOCKETS")
                        && dev.contains("TooltipPresentation.Glyph.ARCHAEOLOGY")
                        && dev.contains("TooltipPresentation.Glyph.STORY"),
                "DEV reference preview must exercise the reviewed semantic presentation hierarchy");
        check(dev.contains("ItemDataFactory.hideAttributeTooltip(display)")
                        && dev.contains("ItemDataFactory.applyTooltipStyleForRarity(display, \"legendas\")")
                        && dev.contains("ItemDataFactory.applyRarity(display"),
                "DEV reference preview must exercise the legendary native tooltip frame");
        check(!dev.contains("Presentation-only DEV projection")
                        && !dev.contains("Minta sebzés:")
                        && !dev.contains("Tesztérték:"),
                "DEV reference preview regressed to the old neon/debug tooltip copy");
    }

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
