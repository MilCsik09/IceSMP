package hu.taliann.icesmp.ux;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Source + pure-presentation contracts for non-equipment item tooltip profiles. */
public final class SpecialItemTooltipRegressionSuite {
    private static int assertions;

    private SpecialItemTooltipRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        profileComposerKeepsReadableHierarchy();
        factoriesUseSemanticProfiles();
        professionPipelinePreservesRarityPrecedence();
        developerArtifactsStayDistinctFromDebugProbes();
        resourcePackOwnsProfileGlyphsAndStyles();
        System.out.println("Special item tooltip regression suite passed. assertions=" + assertions);
    }

    private static void profileComposerKeepsReadableHierarchy() {
        final List<Component> rendered = ItemTooltipProfiles.compose(
                ItemTooltipProfiles.Profile.CURRENCY,
                "TESZT",
                List.of(ItemTooltipProfiles.fact("Típus", "fizikai", net.kyori.adventure.text.format.NamedTextColor.GOLD)),
                List.of(TooltipPresentation.line("Használható.", net.kyori.adventure.text.format.NamedTextColor.GRAY)),
                List.of(TooltipPresentation.line("Leírás.", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        final String plain = rendered.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce((left, right) -> left + "\n" + right).orElse("");
        check(plain.contains("VALUTA  •  TESZT"), "profile type line missing");
        check(plain.contains("ADATOK"), "profile facts heading missing");
        check(plain.contains("HASZNÁLAT"), "profile usage heading missing");
        check(plain.contains("LEÍRÁS"), "profile story heading missing");
    }

    private static void factoriesUseSemanticProfiles() throws Exception {
        checkSource("src/main/java/hu/taliann/icesmp/items/BlueprintItemFactory.java",
                "ItemTooltipProfiles.blueprint(recipe)", "Profile.BLUEPRINT");
        checkSource("src/main/java/hu/taliann/icesmp/items/UniqueMaterialFactory.java",
                "ItemTooltipProfiles.professionMaterial", "Profile.PROFESSION");
        checkSource("src/main/java/hu/taliann/icesmp/items/CurrencyItemFactory.java",
                "ItemTooltipProfiles.currency(currencyType)", "Profile.CURRENCY");
        checkSource("src/main/java/hu/taliann/icesmp/items/MoneyPouchItemFactory.java",
                "ItemTooltipProfiles.moneyPouch()", "Profile.CURRENCY");
        checkSource("src/main/java/hu/taliann/icesmp/items/RelicItemFactory.java",
                "ItemTooltipProfiles.relic(definition, owner)", "Profile.RELIC");
        checkSource("src/main/java/hu/taliann/icesmp/items/DevItemFactory.java",
                "ItemTooltipProfiles.developerArtifact(definition)", "Profile.DEVELOPER_ARTIFACT");
        checkSource("src/main/java/hu/taliann/icesmp/items/CrateKeyFactory.java",
                "ItemTooltipProfiles.crateKey", "Profile.KEY");
    }

    private static void professionPipelinePreservesRarityPrecedence() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java"));
        check(source.contains("ItemTooltipProfiles.professionResult(recipe)"),
                "profession result did not adopt semantic profile lore");
        final int rarity = source.indexOf("if (finalRarity != null && !finalRarity.isBlank())");
        final int profile = source.indexOf("ItemTooltipProfiles.styleId(ItemTooltipProfiles.professionProfile(recipe))");
        check(rarity >= 0 && profile > rarity,
                "profession category style must be fallback-only behind gameplay rarity");
        check(source.contains("applyTooltipStyleForRarity(result, finalRarity)"),
                "final ItemMeta mutations must restore rarity tooltip style");
    }

    private static void developerArtifactsStayDistinctFromDebugProbes() throws Exception {
        final String profiles = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/ux/ItemTooltipProfiles.java"));
        final String worldWeaver = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/dev/artifact/WorldWeaverArtifactBehavior.java"));
        final String bingulus = Files.readString(Path.of(
                "src/main/resources/config/dev-items.yml"));
        check(profiles.contains("FEJLESZTŐI EREKLYE")
                        && profiles.contains("case \"csodalatos_bingulus\"")
                        && profiles.contains("case \"dev_world_weaver\""),
                "developer artifacts lost their dedicated presentation semantics");
        check(worldWeaver.contains("WorldWeaver futásidejű szövési felületének")
                        && !worldWeaver.contains("developer-only"),
                "WorldWeaver player-facing copy is not the reviewed Hungarian artifact description");
        check(bingulus.contains("10 aktív percenként jutalmat ad"),
                "Bingulus canonical reward description disappeared");
    }

    private static void resourcePackOwnsProfileGlyphsAndStyles() throws Exception {
        final String font = Files.readString(Path.of("resource-pack/assets/icesmp/font/tooltip.json"));
        for (final String codepoint : List.of("e10a", "e10b", "e10c", "e10d", "e10e", "e10f")) {
            check(font.toLowerCase(java.util.Locale.ROOT).contains(codepoint),
                    "tooltip font missing profile glyph U+" + codepoint.toUpperCase(java.util.Locale.ROOT));
        }
        final String generator = Files.readString(Path.of("scripts/generate_item_tooltip_assets.py"));
        for (final String style : List.of(
                "blueprint", "profession", "currency", "relic", "developer_artifact", "key")) {
            check(generator.contains("\"" + style + "\""),
                    "tooltip asset generator missing profile style " + style);
            check(Files.exists(Path.of(
                            "resource-pack/assets/icesmp/textures/gui/sprites/tooltip/"
                                    + style + "_frame.png")),
                    "checked-in tooltip frame missing for profile " + style);
        }
    }

    private static void checkSource(final String path, final String renderer, final String profile)
            throws Exception {
        final String source = Files.readString(Path.of(path));
        check(source.contains(renderer), path + " bypassed semantic tooltip renderer");
        check(source.contains(profile), path + " did not apply the expected tooltip profile style");
    }

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
