package hu.taliann.icesmp.ux;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source contracts for non-equipment special item tooltip profiles. */
public final class SpecialItemTooltipRegressionSuite {
    private static int assertions;

    private SpecialItemTooltipRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        profileRendererCoversSpecialFamilies();
        factoriesUseSharedProfiles();
        developerArtifactsStayDistinctFromDebugProbes();
        playerFacingDescriptionsRemainConcrete();
        relicDescriptionsAreAuthoredContent();
        authoredUniqueProfilesStayExplicit();
        familyAccentsStayWired();
        System.out.println("Special item tooltip regression suite passed. assertions=" + assertions);
    }

    private static void profileRendererCoversSpecialFamilies() throws Exception {
        final String source = read("src/main/java/hu/taliann/icesmp/ux/SpecialItemTooltipRenderer.java");
        for (final String profile : new String[]{
                "BLUEPRINT", "PROFESSION_MATERIAL", "PROFESSION_RESULT", "CURRENCY", "MONEY_POUCH",
                "RELIC", "DEVELOPER_ARTIFACT", "DEBUG_PROBE", "QUEST", "TOKEN",
                "KEY", "UPGRADE", "UTILITY"}) {
            check(source.contains(profile), "missing special tooltip profile " + profile);
        }
        check(source.contains("TooltipEngine.render(sections)")
                        && source.contains("TooltipPresentation.sectionHeading"),
                "special-purpose tooltips must use the shared semantic presentation foundation");
    }

    private static void factoriesUseSharedProfiles() throws Exception {
        check(read("src/main/java/hu/taliann/icesmp/items/BlueprintItemFactory.java")
                        .contains("SpecialItemTooltipRenderer.blueprint(recipe)"),
                "blueprints bypass the shared tooltip profile");
        final String uniqueFactory = read(
                "src/main/java/hu/taliann/icesmp/items/UniqueMaterialFactory.java");
        check(uniqueFactory.contains("SpecialItemTooltipRenderer.uniqueItem(section)")
                        && uniqueFactory.contains("SpecialItemTooltipRenderer.profileOf(section)"),
                "unique items bypass authored special-item profile selection");
        check(read("src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java")
                        .contains("SpecialItemTooltipRenderer.professionResult(recipe, potionSpecs)"),
                "non-canonical profession results bypass the shared tooltip profile");
        check(read("src/main/java/hu/taliann/icesmp/items/CurrencyItemFactory.java")
                        .contains("SpecialItemTooltipRenderer.currency(currencyType)"),
                "physical currency bypasses the shared tooltip profile");
        check(read("src/main/java/hu/taliann/icesmp/items/MoneyPouchItemFactory.java")
                        .contains("SpecialItemTooltipRenderer.moneyPouch()"),
                "money pouches bypass the shared tooltip profile");
        check(read("src/main/java/hu/taliann/icesmp/items/CrateKeyFactory.java")
                        .contains("SpecialItemTooltipRenderer.crateKey(crateName, odds)"),
                "crate keys bypass the shared tooltip profile");
        check(read("src/main/java/hu/taliann/icesmp/items/RelicItemFactory.java")
                        .contains("SpecialItemTooltipRenderer.relic(definition)"),
                "relics bypass the shared tooltip profile");
        check(read("src/main/java/hu/taliann/icesmp/items/DevItemFactory.java")
                        .contains("SpecialItemTooltipRenderer.developerArtifact(definition, presentation, state)"),
                "developer artifacts bypass the shared tooltip profile");
    }

    private static void developerArtifactsStayDistinctFromDebugProbes() throws Exception {
        final String source = read("src/main/java/hu/taliann/icesmp/ux/SpecialItemTooltipRenderer.java");
        check(source.contains("FEJLESZTŐI EREKLYE")
                        && source.contains("Csodálatos Bingulus") == false,
                "developer artifact profile must describe role, not hard-code display identity");
        check(source.contains("Jutalomgenerátor")
                        && source.contains("Világformáló eszköz")
                        && source.contains("WorldWeaverArtifactBehavior.ID")
                        && source.contains("DevItemFactory.BINGULUS_ID"),
                "Bingulus and WorldWeaver need dedicated developer-artifact descriptions");
        check(source.contains("DEBUG_PROBE") && !source.contains("TEST ONLY"),
                "developer artifacts must not be presented as disposable debug probes");
    }

    private static void playerFacingDescriptionsRemainConcrete() throws Exception {
        final String source = read("src/main/java/hu/taliann/icesmp/ux/SpecialItemTooltipRenderer.java");
        check(source.contains("Jobb katt • megtanulod a receptet.")
                        && source.contains("Szakmaszint"),
                "blueprint tooltip lost usage or requirement copy");
        check(source.contains("Forrás")
                        && source.contains("Feldolgozza")
                        && source.contains("Felhasználás"),
                "profession material tooltip lost source/process/sink explanation");
        check(source.contains("SZAKMAI TÁRGY")
                        && source.contains("Kategória")
                        && source.contains("Típus"),
                "profession crafted output tooltip lost its role/category presentation");
        check(source.contains("professionPotionEffects")
                        && source.contains("Hatás")
                        && source.contains("formatDuration"),
                "profession potion output lost semantic potion effect presentation");
        final String recipeBuilder = read(
                "src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java");
        check(recipeBuilder.contains("HIDE_ADDITIONAL_TOOLTIP")
                        && recipeBuilder.contains("professionResult(recipe, potionSpecs)"),
                "custom profession potions must hide duplicate vanilla effects and render recipe effects");
        check(source.contains("a nálad lévő veretek befizethetők")
                        && source.contains("kézben hordozható fizikai pénz"),
                "currency tooltip lost physical/bank semantics");
        check(source.contains("ismeretlen, amíg ki nem bontod")
                        && source.contains("Jobb katt • bontsd ki az erszényt."),
                "money pouch tooltip leaked its hidden payload or lost the interaction hint");
        check(source.contains("KÜLDETÉSI TÁRGY")
                        && source.contains("HALADÁSI TÁRGY")
                        && source.contains("FEJLESZTÉS")
                        && source.contains("SEGÉDESZKÖZ")
                        && source.contains("LÁDAKULCS"),
                "quest/token/upgrade/utility/key tooltip profiles lost their category vocabulary");
    }

    private static void relicDescriptionsAreAuthoredContent() throws Exception {
        final String manager = read("src/main/java/hu/taliann/icesmp/managers/RelicManager.java");
        final String definition = read("src/main/java/hu/taliann/icesmp/relics/SimpleRelicDefinition.java");
        final String content = read("src/main/resources/content/equipment/relics.yml");
        check(manager.contains("getString(\"description\", \"\")")
                        && definition.contains("String description"),
                "relic tooltip summary must flow from authored relic definition data");
        final String[] relicIds = {
                "metelytepo", "phoenix_wing", "frost_wing", "wander_wind",
                "eleftheria_konnye", "sarkany_tojas", "bone_wing"};
        for (final String id : relicIds) {
            final int start = content.indexOf("    " + id + ":");
            int next = content.length();
            if (start >= 0) {
                for (final String other : relicIds) {
                    final int candidate = content.indexOf("    " + other + ":", start + 1);
                    if (candidate > start && candidate < next) next = candidate;
                }
            }
            final String block = start < 0 ? "" : content.substring(start, next);
            check(block.contains("description:"),
                    "relic content is missing concise tooltip description: " + id);
        }
    }

    private static void authoredUniqueProfilesStayExplicit() throws Exception {
        final String content = read("src/main/resources/content/professions/materials.yml");
        check(content.contains("suttogas_meghivo:")
                        && content.contains("tooltip-profile: QUEST")
                        && content.contains("tooltip-description: \"Titkos meghívó"),
                "Suttogás invitation must be an authored quest-item profile");
        check(content.contains("emlekszilank:")
                        && content.contains("tooltip-profile: TOKEN")
                        && content.contains("Beváltás: /emlek"),
                "memory fragment must be an authored progression-token profile");
        check(content.contains("ures_kupa:")
                        && content.contains("tooltip-profile: UTILITY"),
                "reusable tavern cup must be an authored utility profile");
        final int runeProfiles = content.split("tooltip-profile: UPGRADE", -1).length - 1;
        check(runeProfiles >= 10,
                "all authored rune items must use the upgrade tooltip profile");
    }

    private static void familyAccentsStayWired() throws Exception {
        final String blueprint = read("src/main/java/hu/taliann/icesmp/items/BlueprintItemFactory.java");
        final String currency = read("src/main/java/hu/taliann/icesmp/items/CurrencyItemFactory.java");
        final String pouch = read("src/main/java/hu/taliann/icesmp/items/MoneyPouchItemFactory.java");
        final String profession = read("src/main/java/hu/taliann/icesmp/items/UniqueMaterialFactory.java");
        final String dev = read("src/main/java/hu/taliann/icesmp/items/DevItemFactory.java");
        final String recipes = read("src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java");
        final String relic = read("src/main/java/hu/taliann/icesmp/items/RelicItemFactory.java");
        check(blueprint.contains("applyTooltipStyle(item, \"icesmp:blueprint\")"),
                "blueprint category accent is not applied after item presentation");
        check(currency.contains("\"icesmp:currency_\""),
                "currency category accent no longer follows the physical currency family");
        check(pouch.contains("applyTooltipStyle(stack, \"icesmp:money_pouch\")"),
                "money pouch category accent is missing");
        check(profession.contains("SpecialItemTooltipRenderer.styleId(")
                        && profession.contains("SpecialItemTooltipRenderer.profileOf(section)"),
                "unique item category accent must follow the authored tooltip profile");
        check(dev.contains("applyTooltipStyle(item, \"icesmp:developer\")"),
                "developer artifact category accent is missing");
        check(recipes.contains("rolledTooltipRarity")
                        && recipes.contains("recipe.uniqueResult() == null")
                        && recipes.contains("applyTooltipStyle(result, \"icesmp:profession\")"),
                "profession results must preserve rarity/category accent and avoid flattening authored unique profiles");
        check(relic.contains("applyTooltipStyleForRarity(itemStack, \"ereklye\")"),
                "relic refresh must restore the Ereklye tooltip accent");
        check(read("src/main/java/hu/taliann/icesmp/items/CrateKeyFactory.java")
                        .contains("applyTooltipStyle(itemStack, \"icesmp:key\")"),
                "crate key tooltip style is not restored after item-model presentation");
        for (final String style : new String[]{
                "blueprint", "profession", "currency_red", "currency_blue",
                "currency_neutral", "currency_dark", "money_pouch", "developer",
                "quest", "token", "key", "upgrade", "utility"}) {
            check(Files.isRegularFile(Path.of(
                            "resource-pack/assets/icesmp/textures/gui/sprites/tooltip/"
                                    + style + "_background.png"))
                            && Files.isRegularFile(Path.of(
                            "resource-pack/assets/icesmp/textures/gui/sprites/tooltip/"
                                    + style + "_frame.png")),
                    "missing special tooltip sprite pair: " + style);
        }
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
