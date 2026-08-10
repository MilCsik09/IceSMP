package hu.taliann.icesmp.resourcepack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Regressions for the additive resource-pack layer and custom wearable presentation contract. */
public final class ResourcePackRegressionSuite {

    private ResourcePackRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        listenerUsesAdditiveApiAndStableId();
        reloadOnlyResendsEffectiveChanges();
        developmentCompositeSurvivesReloads();
        packagedConfigMatchesTheStableId();
        bundledMetadataUsesMatchingImmutableHash();
        wearablePresentationSeparatesInventoryAndEquippedRendering();
        wearablePresentationPreservesVanillaEquippableState();
        wearableFallbackPolicyIsSharedAndVersioned();
        wearableCreationPathsUseTheCentralBoundary();
        relicWingEquipmentAssetsStayBundled();
        horseArmorEquipmentAssetsStayBundled();
        System.out.println("Resource pack regression suite passed.");
    }

    private static void reloadOnlyResendsEffectiveChanges() throws Exception {
        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ResourcePackListener.java"));
        final String entrypoint = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/IceSMP.java"));

        check(listener.contains("if (sameRequest(previous, current))"),
                "unchanged effective resource-pack requests must not be re-sent on reload");
        check(listener.contains("Arrays.equals(first.hash(), second.hash())")
                        && listener.contains("first.id().equals(second.id())")
                        && listener.contains("first.url().equals(second.url())")
                        && listener.contains("first.prompt().equals(second.prompt())")
                        && listener.contains("first.required() == second.required()"),
                "reload change detection must cover id, URL, SHA-1, prompt and required");
        check(listener.contains("player.removeResourcePack(previous.id())"),
                "disabling the pack or changing its UUID must remove the previous IceSMP layer");
        check(entrypoint.contains("resourcePackListener.resendCurrent();"),
                "plugin enable must still force-send the current pack to already-online players");
    }

    private static void developmentCompositeSurvivesReloads() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ResourcePackListener.java"));
        check(source.contains("return;\n        }\n        this.request = loadRequest();"),
                "a config reload must not clear an already prepared development composite pack");
        check(source.contains("new PackRequest(DEFAULT_PACK_ID")
                        && !source.contains("UUID.nameUUIDFromBytes(hash)"),
                "development pack updates must retain a stable additive pack UUID");
        check(source.contains("ensureDevelopmentServer(address)")
                        && source.contains("developmentPayload = new DevelopmentPayload"),
                "development updates must swap the payload without rebinding the HTTP server");
        check(source.contains("normalizedEntry.setTime(0L)"),
                "development composite ZIP output must be deterministic");
    }

    private static void listenerUsesAdditiveApiAndStableId() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ResourcePackListener.java"));
        check(source.contains("player.addResourcePack("),
                "IceSMP resource pack no longer uses Paper's additive API");
        check(!source.contains("player.setResourcePack("),
                "IceSMP must not overwrite the native/server or another plugin's pack layer");
        check(source.contains("icesmp.dev.mergedBetterHudPack"),
                "runFolia merged BetterHud pack must suppress the conflicting standalone R2 request");
        check(source.contains("UUID.fromString(\"7c847f1e-d942-3c8f-bd46-5c43bb1a3e67\")"),
                "stable IceSMP pack UUID changed unexpectedly");
    }

    private static void packagedConfigMatchesTheStableId() throws Exception {
        final String config = Files.readString(Path.of("src/main/resources/config.yml"));
        final UUID configured = UUID.fromString(extractQuotedValue(config, "  id:"));
        check(configured.equals(UUID.fromString("7c847f1e-d942-3c8f-bd46-5c43bb1a3e67")),
                "resource-pack.id no longer matches the stable IceSMP layer id");
    }

    private static void bundledMetadataUsesMatchingImmutableHash() throws Exception {
        final Properties metadata = new Properties();
        try (var reader = Files.newBufferedReader(
                Path.of("src/main/resources/resource-pack.properties"))) {
            metadata.load(reader);
        }
        final String url = metadata.getProperty("url", "");
        final String sha1 = metadata.getProperty("sha1", "");
        check(sha1.matches("[0-9a-f]{40}"), "bundled resource-pack SHA-1 is invalid");
        check(url.startsWith("https://assets.icesmp.taliann.dev/resource-packs/icesmp-")
                        && url.endsWith(sha1 + ".zip"),
                "resource-pack URL is not the immutable object matching the bundled SHA-1");
    }

    private static void wearablePresentationSeparatesInventoryAndEquippedRendering() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/items/WearablePresentation.java"));
        check(source.contains("ItemDataFactory.applyItemModel(item, normalizedModel)"),
                "wearable presentation no longer applies ITEM_MODEL through the canonical helper");
        check(source.contains("item.setData(DataComponentTypes.EQUIPPABLE"),
                "wearable presentation no longer writes the EQUIPPABLE component");
        check(source.contains("assetId(assetKey)"),
                "wearable presentation no longer binds the equipment asset id");
        check(source.contains("explicitEquipmentAsset") && source.contains("normalizedItemModel"),
                "wearable presentation lost the explicit-vs-same-id fallback distinction");
    }

    private static void wearablePresentationPreservesVanillaEquippableState() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/items/WearablePresentation.java"));
        final String compact = source.replaceAll("\\s+", "");
        check(compact.contains("current.toBuilder().assetId(assetKey).build()"),
                "equipment asset must be applied by rebuilding the existing EQUIPPABLE component");
        check(!source.contains("Equippable.equippable("),
                "wearable presentation must not synthesize a replacement EQUIPPABLE/slot");
        check(!source.contains(".slot("),
                "wearable presentation must not overwrite the vanilla equipment slot");
    }

    private static void wearableFallbackPolicyIsSharedAndVersioned() throws Exception {
        final Path policyPath = Path.of("src/main/resources/wearable-fallback-policy.properties");
        check(Files.isRegularFile(policyPath), "shared wearable fallback policy is missing");

        final Properties policy = new Properties();
        try (var reader = Files.newBufferedReader(policyPath)) {
            policy.load(reader);
        }
        check("1".equals(policy.getProperty("schema")), "unexpected wearable fallback policy schema");
        check("1.21.11".equals(policy.getProperty("minecraft-version")),
                "wearable fallback policy must stay pinned to the server Minecraft target");
        final String exact = policy.getProperty("exact", "");
        final String suffix = policy.getProperty("suffix", "");
        check(exact.contains("SADDLE"), "SADDLE is missing from the shared fallback policy");
        check(suffix.contains("_ARMOR"), "BODY armor families are missing from the fallback policy");
        check(suffix.contains("_HARNESS"), "harness BODY equipment is missing from the fallback policy");

        final String runtime = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/items/WearablePresentation.java"));
        final String validator = Files.readString(Path.of("scripts/resource_pack.py"));
        check(runtime.contains("wearable-fallback-policy.properties")
                        && runtime.contains("allowsImplicitSameIdFallback"),
                "runtime no longer consumes the shared wearable fallback policy");
        check(validator.contains("wearable-fallback-policy.properties")
                        && validator.contains("allows_implicit_same_id_fallback"),
                "resource-pack validator no longer consumes the shared wearable fallback policy");
    }

    private static void wearableCreationPathsUseTheCentralBoundary() throws Exception {
        final String profession = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java"));
        final String unique = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/items/UniqueMaterialFactory.java"));
        final String loot = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/MobLootListener.java"));
        final String relic = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/items/RelicItemFactory.java"));

        check(profession.contains("presentationBase + \"equipment-asset\"")
                        && profession.contains("WearablePresentation.applyWearablePresentation"),
                "profession recipe results no longer support the explicit equipment-asset field");
        check(profession.contains("uniqueMaterials.applyPresentation(result, recipe.uniqueResult())"),
                "unique profession results no longer reapply presentation after meta/affix round-trips");
        check(unique.contains("section.getString(\"equipment-asset\", null)")
                        && unique.contains("WearablePresentation.applyWearablePresentation"),
                "unique-material creation no longer uses the wearable presentation boundary");
        check(loot.contains("chosen.get(\"equipment-asset\")")
                        && loot.contains("WearablePresentation.applyWearablePresentation"),
                "named loot no longer supports equipment-asset through the wearable boundary");
        check(relic.contains("WearablePresentation.applyWearablePresentation")
                        && relic.contains("\"icesmp:relic_\" + relicId"),
                "relic create/refresh paths no longer route wearable wings through the central boundary");
    }

    private static void relicWingEquipmentAssetsStayBundled() {
        for (final String relicId : List.of("phoenix_wing", "frost_wing", "wander_wind", "bone_wing")) {
            final Path equipment = Path.of(
                    "resource-pack/assets/icesmp/equipment/relic_" + relicId + ".json");
            check(Files.isRegularFile(equipment),
                    "relic wing runtime binding has no matching equipment asset: " + equipment);
        }
    }

    private static void horseArmorEquipmentAssetsStayBundled() throws Exception {
        for (final String assetId : List.of("vas_lopancel", "arany_lopancel", "gyemant_lopancel")) {
            final Path equipment = Path.of("resource-pack/assets/icesmp/equipment/" + assetId + ".json");
            check(Files.isRegularFile(equipment),
                    "horse armor fallback has no matching equipment asset: " + equipment);
            check(Files.readString(equipment).contains("\"horse_body\""),
                    "horse armor equipment asset must render through horse_body: " + equipment);
        }
    }

    private static String extractQuotedValue(final String source, final String prefix) {
        for (final String line : source.split("\\R")) {
            if (!line.startsWith(prefix)) {
                continue;
            }
            final int first = line.indexOf('"');
            final int last = line.lastIndexOf('"');
            if (first >= 0 && last > first) {
                return line.substring(first + 1, last);
            }
        }
        throw new AssertionError("missing config line: " + prefix);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
