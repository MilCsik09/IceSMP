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
        semanticIconsUseClientSafeFallback();
        resourcePackDefinesEverySemanticGlyph();
        canonicalRendererUsesSemanticPresentationSections();
        canonicalItemsUseSharedChromeWithRarityAccent();
        devReferencePreviewUsesReviewedPresentation();
        runeMutationRefreshesVisiblePresentation();
        SpecialItemTooltipRegressionSuite.main(args);
        System.out.println("Tooltip presentation regression suite passed. assertions=" + assertions);
    }

    private static void semanticIconsUseClientSafeFallback() {
        final Component icon = TooltipPresentation.icon(TooltipPresentation.Glyph.STATS);
        final Component heading = TooltipPresentation.sectionHeading(
                TooltipPresentation.Glyph.STATS, "Harcértékek", NamedTextColor.GOLD);
        check(icon.style().font() == null,
                "production semantic icons must render through the client-safe default font");
        check(heading.style().font() == null
                        && heading.children().stream()
                        .noneMatch(child -> TooltipPresentation.FONT.equals(child.style().font())),
                "production tooltip rows must not depend on private-use font glyphs");
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
            final String escaped = String.format(Locale.ROOT, "\\u%04x", (int) glyph.character());
            check(font.indexOf(glyph.character()) >= 0
                            || font.toLowerCase(Locale.ROOT).contains(escaped),
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
        check(renderer.contains("TooltipPresentation.classification")
                        && renderer.contains("final LinkedHashMap<String, Double> totalStats")
                        && renderer.contains("totalStats.merge(id, roll.value(), Double::sum)"),
                "canonical renderer must use compact classification and merged stat rows");
        check(renderer.contains("final NamedTextColor accent = color(template.rarity())")
                        && renderer.contains("template.rarity().displayName(), typeLine, accent")
                        && renderer.contains("statAmount(definition.id(), value)"),
                "compact canonical hierarchy no longer follows rarity/stat presentation");
        check(!renderer.contains("private static final String DIVIDER"),
                "presentation pass regressed to the old divider-heavy tooltip layout");
    }

    private static void canonicalItemsUseSharedChromeWithRarityAccent() throws Exception {
        final String identity = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/itemization/ItemIdentityService.java"));
        final String data = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/items/ItemDataFactory.java"));
        check(identity.contains("ItemDataFactory.applyTooltipStyleForRarity(item, template.rarity().id())"),
                "canonical item refresh must retain shared tooltip presentation");
        check(data.contains("applyTooltipStyle(item, \"icesmp:\" + normalized)")
                        && !data.contains("applyTooltipStyle(item, \"icesmp:tooltip/\""),
                "rarity tooltip style must use namespace:path without duplicated tooltip/");
        final String backgroundMeta = Files.readString(Path.of(
                "resource-pack/assets/minecraft/textures/gui/sprites/tooltip/background.png.mcmeta"));
        final String legendaryFrameMeta = Files.readString(Path.of(
                "resource-pack/assets/icesmp/textures/gui/sprites/tooltip/legendas_frame.png.mcmeta"));
        check(backgroundMeta.contains("\"border\": 9")
                        && legendaryFrameMeta.contains("\"border\": 10")
                        && legendaryFrameMeta.contains("\"stretch_inner\": true"),
                "shared/global and legendary rarity tooltip sprites lost nine-slice contract");
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
        check(dev.contains("final ItemStack display = new ItemStack(Material.NETHERITE_SWORD)")
                        && !dev.contains("final ItemStack display = source.clone()"),
                "DEV packet preview must use a fresh stack so stale tooltip styles cannot leak");
        check(dev.contains("ItemDataFactory.hideAttributeTooltip(display)")
                        && dev.contains("ItemDataFactory.applyTooltipStyleForRarity(display, \"legendas\")")
                        && dev.contains("ItemDataFactory.applyRarity(display"),
                "DEV reference preview must exercise the legendary rarity accent");
        check(!dev.contains("Presentation-only DEV projection")
                        && !dev.contains("Minta sebzés:")
                        && !dev.contains("Tesztérték:"),
                "DEV reference preview regressed to the old neon/debug tooltip copy");
    }

    private static void runeMutationRefreshesVisiblePresentation() throws Exception {
        final String identity = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/itemization/ItemIdentityService.java"));
        final int applyRune = identity.indexOf("public RuneMutation applyRune");
        final int duplicates = identity.indexOf("public Set<UUID> duplicateIds", applyRune);
        check(applyRune >= 0 && duplicates > applyRune,
                "applyRune presentation contract source range missing");
        final String mutation = identity.substring(applyRune, duplicates);
        check(mutation.contains("ItemTooltipRenderer.render(template, updated, templates)")
                        && mutation.contains("refreshPresentation(item, template, updated)"),
                "rune mutation must refresh semantic lore and native tooltip presentation");
    }

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
