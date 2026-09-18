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
        System.out.println("Special item tooltip regression suite passed. assertions=" + assertions);
    }

    private static void profileRendererCoversSpecialFamilies() throws Exception {
        final String source = read("src/main/java/hu/taliann/icesmp/ux/SpecialItemTooltipRenderer.java");
        for (final String profile : new String[]{
                "BLUEPRINT", "PROFESSION_MATERIAL", "PROFESSION_RESULT", "CURRENCY", "MONEY_POUCH",
                "RELIC", "DEVELOPER_ARTIFACT", "DEBUG_PROBE", "QUEST", "TOKEN"}) {
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
        check(read("src/main/java/hu/taliann/icesmp/items/UniqueMaterialFactory.java")
                        .contains("SpecialItemTooltipRenderer.professionMaterial(section)"),
                "profession materials bypass the shared tooltip profile");
        check(read("src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java")
                        .contains("SpecialItemTooltipRenderer.professionResult(recipe)"),
                "non-canonical profession results bypass the shared tooltip profile");
        check(read("src/main/java/hu/taliann/icesmp/items/CurrencyItemFactory.java")
                        .contains("SpecialItemTooltipRenderer.currency(currencyType)"),
                "physical currency bypasses the shared tooltip profile");
        check(read("src/main/java/hu/taliann/icesmp/items/MoneyPouchItemFactory.java")
                        .contains("SpecialItemTooltipRenderer.moneyPouch()"),
                "money pouches bypass the shared tooltip profile");
        check(read("src/main/java/hu/taliann/icesmp/items/RelicItemFactory.java")
                        .contains("SpecialItemTooltipRenderer.relic(definition)"),
                "relics bypass the shared tooltip profile");
        check(read("src/main/java/hu/taliann/icesmp/items/DevItemFactory.java")
                        .contains("SpecialItemTooltipRenderer.developerArtifact(definition, presentation)"),
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
        check(source.contains("a nálad lévő veretek befizethetők")
                        && source.contains("kézben hordozható fizikai pénz"),
                "currency tooltip lost physical/bank semantics");
        check(source.contains("ismeretlen, amíg ki nem bontod")
                        && source.contains("Jobb katt • bontsd ki az erszényt."),
                "money pouch tooltip leaked its hidden payload or lost the interaction hint");
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
