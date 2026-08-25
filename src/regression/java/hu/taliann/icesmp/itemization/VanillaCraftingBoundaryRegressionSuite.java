package hu.taliann.icesmp.itemization;

import org.bukkit.Material;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class VanillaCraftingBoundaryRegressionSuite {

    private static int assertions;

    private VanillaCraftingBoundaryRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        vanillaAndBasicRemainSurvivalNative();
        canonicalTransformationsFailClosed();
        vanillaGearCannotBecomeAnEconomyFaucet();
        canonicalNonTransformingLifecycleRemainsUsable();
        legacyCannotBeLaundered();
        consumerSinksFailClosedForCanonicalBackingMaterials();
        cosmeticFlagsStillRequireCanonicalMutation();
        invalidIdentityAndConfigFailClosed();
        basicGearClassificationDoesNotInferArmorFamily();
        sourceAdaptersCoverEveryCommittedSurface();
        configurationPreservesSurvivalFreedom();
        System.out.println("Vanilla crafting boundary regression suite passed. assertions=" + assertions);
    }

    private static void vanillaAndBasicRemainSurvivalNative() {
        final ItemTransformationPolicy.Rules rules = ItemTransformationPolicy.Rules.safeDefaults();
        for (final ItemTransformationPolicy.Transformation operation
                : ItemTransformationPolicy.Transformation.values()) {
            if (operation == ItemTransformationPolicy.Transformation.CANONICAL_SALVAGE
                    || operation == ItemTransformationPolicy.Transformation.CANONICAL_PROFESSION_MUTATION) continue;
            check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.VANILLA_SURVIVAL,
                    false, operation, rules).directlyAllowed(), "vanilla survival remains native: " + operation);
            check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.BASIC_SURVIVAL_GEAR,
                    false, operation, rules).directlyAllowed(), "basic survival gear remains native: " + operation);
        }
    }

    private static void vanillaGearCannotBecomeAnEconomyFaucet() {
        final ItemTransformationPolicy.Rules rules = ItemTransformationPolicy.Rules.safeDefaults();
        for (final ItemTransformationPolicy.Domain domain : List.of(
                ItemTransformationPolicy.Domain.VANILLA_SURVIVAL,
                ItemTransformationPolicy.Domain.BASIC_SURVIVAL_GEAR)) {
            check(ItemTransformationPolicy.decide(domain, false,
                            ItemTransformationPolicy.Transformation.CANONICAL_SALVAGE, rules).action()
                            == ItemTransformationPolicy.Action.DENY,
                    "vanilla/basic salvage cannot mint canonical value: " + domain);
            check(ItemTransformationPolicy.decide(domain, false,
                            ItemTransformationPolicy.Transformation.CANONICAL_PROFESSION_MUTATION, rules).action()
                            == ItemTransformationPolicy.Action.DENY,
                    "vanilla/basic gear cannot bypass profession producers: " + domain);
        }
    }

    private static void canonicalTransformationsFailClosed() {
        final ItemTransformationPolicy.Rules rules = ItemTransformationPolicy.Rules.safeDefaults();
        final Set<ItemTransformationPolicy.Transformation> denied = Set.of(
                ItemTransformationPolicy.Transformation.VANILLA_CRAFT_INPUT,
                ItemTransformationPolicy.Transformation.VANILLA_RECIPE_OUTPUT,
                ItemTransformationPolicy.Transformation.FURNACE_OR_CAMPFIRE,
                ItemTransformationPolicy.Transformation.VANILLA_CONSUMER_SINK,
                ItemTransformationPolicy.Transformation.STONECUTTER,
                ItemTransformationPolicy.Transformation.ANVIL_RENAME,
                ItemTransformationPolicy.Transformation.ANVIL_MATERIAL_REPAIR,
                ItemTransformationPolicy.Transformation.ANVIL_ITEM_REPAIR,
                ItemTransformationPolicy.Transformation.ANVIL_ENCHANT_MERGE,
                ItemTransformationPolicy.Transformation.ENCHANTING_TABLE,
                ItemTransformationPolicy.Transformation.SMITHING_UPGRADE,
                ItemTransformationPolicy.Transformation.ARMOR_TRIM,
                ItemTransformationPolicy.Transformation.GRINDSTONE,
                ItemTransformationPolicy.Transformation.VILLAGER_TRADE);
        for (final ItemTransformationPolicy.Transformation operation : denied) {
            check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR,
                            true, operation, rules).action() == ItemTransformationPolicy.Action.DENY,
                    "canonical vanilla transformation is denied: " + operation);
        }
        check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR,
                        true, ItemTransformationPolicy.Transformation.CANONICAL_SALVAGE, rules).action()
                        == ItemTransformationPolicy.Action.CANONICAL_MUTATION_REQUIRED,
                "canonical salvage stays behind WAL");
        check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR,
                        true, ItemTransformationPolicy.Transformation.CANONICAL_PROFESSION_MUTATION, rules).action()
                        == ItemTransformationPolicy.Action.CANONICAL_MUTATION_REQUIRED,
                "profession mutation stays behind WAL");
    }

    private static void consumerSinksFailClosedForCanonicalBackingMaterials() {
        final ItemTransformationPolicy.Rules rules = ItemTransformationPolicy.Rules.safeDefaults();
        for (final Material backing : List.of(Material.BLAZE_ROD, Material.RABBIT_FOOT, Material.EMERALD)) {
            check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR,
                            true, ItemTransformationPolicy.Transformation.VANILLA_CONSUMER_SINK, rules).action()
                            == ItemTransformationPolicy.Action.DENY,
                    "canonical " + backing + " cannot be burned, brewed or spent as vanilla currency");
            check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.VANILLA_SURVIVAL,
                            false, ItemTransformationPolicy.Transformation.VANILLA_CONSUMER_SINK, rules)
                            .directlyAllowed(),
                    "ordinary vanilla " + backing + " remains consumable");
        }
        check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.LEGACY,
                        false, ItemTransformationPolicy.Transformation.VANILLA_CONSUMER_SINK, rules).action()
                        == ItemTransformationPolicy.Action.DENY,
                "legacy managed material cannot be destroyed by a vanilla sink");
    }

    private static void canonicalNonTransformingLifecycleRemainsUsable() {
        final ItemTransformationPolicy.Rules rules = ItemTransformationPolicy.Rules.safeDefaults();
        for (final ItemTransformationPolicy.Transformation operation : List.of(
                ItemTransformationPolicy.Transformation.DURABILITY_DAMAGE,
                ItemTransformationPolicy.Transformation.ITEM_BREAK,
                ItemTransformationPolicy.Transformation.CONTAINER_MOVE,
                ItemTransformationPolicy.Transformation.DROP_OR_DEATH,
                ItemTransformationPolicy.Transformation.MARKET_TRANSFER,
                ItemTransformationPolicy.Transformation.CANONICAL_AUTHORED_PRODUCER)) {
            check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR,
                    true, operation, rules).directlyAllowed(), "identity-preserving lifecycle remains allowed: " + operation);
        }
    }

    private static void legacyCannotBeLaundered() {
        final ItemTransformationPolicy.Rules rules = ItemTransformationPolicy.Rules.safeDefaults();
        check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.LEGACY, false,
                ItemTransformationPolicy.Transformation.CONTAINER_MOVE, rules).directlyAllowed(),
                "legacy can be moved for isolation");
        check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.LEGACY, false,
                ItemTransformationPolicy.Transformation.DROP_OR_DEATH, rules).directlyAllowed(),
                "legacy drop/death preserves metadata");
        check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.LEGACY, false,
                        ItemTransformationPolicy.Transformation.GRINDSTONE, rules).action()
                        == ItemTransformationPolicy.Action.DENY,
                "legacy grindstone laundering is denied");
        check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.LEGACY, false,
                        ItemTransformationPolicy.Transformation.VANILLA_CRAFT_INPUT, rules).action()
                        == ItemTransformationPolicy.Action.DENY,
                "legacy crafting laundering is denied");
    }

    private static void cosmeticFlagsStillRequireCanonicalMutation() {
        final ItemTransformationPolicy.Rules enabled = new ItemTransformationPolicy.Rules(
                5L, true, true, true, true, true, true, true,
                Set.of(), ItemTransformationPolicy.DurabilityMode.VANILLA, 2_000L, true, List.of());
        for (final ItemTransformationPolicy.Transformation operation : List.of(
                ItemTransformationPolicy.Transformation.ANVIL_RENAME,
                ItemTransformationPolicy.Transformation.ARMOR_TRIM,
                ItemTransformationPolicy.Transformation.ANVIL_ITEM_REPAIR,
                ItemTransformationPolicy.Transformation.SMITHING_UPGRADE,
                ItemTransformationPolicy.Transformation.GRINDSTONE,
                ItemTransformationPolicy.Transformation.ENCHANTING_TABLE)) {
            check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR,
                            true, operation, enabled).action()
                            == ItemTransformationPolicy.Action.CANONICAL_MUTATION_REQUIRED,
                    "enabled policy never authorizes direct Bukkit meta mutation: " + operation);
        }
    }

    private static void invalidIdentityAndConfigFailClosed() {
        final ItemTransformationPolicy.Rules rules = ItemTransformationPolicy.Rules.safeDefaults();
        check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR,
                        false, ItemTransformationPolicy.Transformation.ANVIL_RENAME, rules).action()
                        == ItemTransformationPolicy.Action.DENY,
                "malformed/checksum-stale canonical item cannot enter a station");
        final ItemTransformationPolicy.Rules invalid = new ItemTransformationPolicy.Rules(
                6L, true, false, false, false, false, false, false,
                Set.of(), ItemTransformationPolicy.DurabilityMode.VANILLA, 2_000L,
                false, List.of("unknown enchant"));
        check(ItemTransformationPolicy.decide(ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR,
                        true, ItemTransformationPolicy.Transformation.ANVIL_RENAME, invalid).action()
                        == ItemTransformationPolicy.Action.DENY,
                "invalid config fails closed for canonical items");
    }

    private static void basicGearClassificationDoesNotInferArmorFamily() {
        check(ItemTransformationPolicy.isBasicSurvivalGear(Material.LEATHER_CHESTPLATE),
                "leather chestplate is basic survival gear, not an MMO armor family inference");
        check(ItemTransformationPolicy.isBasicSurvivalGear(Material.IRON_CHESTPLATE),
                "iron chestplate is basic survival gear, not implicit PLATE");
        check(ItemTransformationPolicy.isBasicSurvivalGear(Material.NETHERITE_SWORD),
                "netherite sword remains basic survival combat gear");
        check(ItemTransformationPolicy.isBasicSurvivalGear(Material.BOW),
                "bow remains basic survival combat gear");
        check(!ItemTransformationPolicy.isBasicSurvivalGear(Material.NETHERITE_PICKAXE),
                "pickaxe remains vanilla survival tool");
        check(!ItemTransformationPolicy.isBasicSurvivalGear(Material.REDSTONE),
                "redstone remains vanilla survival infrastructure");
    }

    private static void sourceAdaptersCoverEveryCommittedSurface() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/VanillaCraftingBoundaryListener.java"));
        for (final String token : List.of("PrepareItemCraftEvent", "CraftItemEvent", "CrafterCraftEvent",
                "BlockCookEvent", "FurnaceBurnEvent", "BrewingStandFuelEvent", "BrewEvent",
                "PrepareSmithingEvent", "SmithItemEvent", "PrepareAnvilEvent",
                "PrepareGrindstoneEvent", "PrepareItemEnchantEvent", "EnchantItemEvent",
                "InventoryClickEvent", "InventoryDragEvent", "PlayerTradeEvent")) {
            check(source.contains(token), "station/sink adapter is present: " + token);
        }
        check(source.contains("SlotType.RESULT"), "committed result click covers shift/hotbar/repeated paths");
        check(source.contains("VANILLA_CONSUMER_SINK"),
                "fuel, brewing and merchant costs route through canonical sink policy");
        check(source.contains("event.setCancelled(true)"), "committed paths fail closed");
        check(source.contains("feedbackCooldownMillis"), "player feedback is bounded");
    }

    private static void configurationPreservesSurvivalFreedom() throws Exception {
        final String config = Files.readString(Path.of("src/main/resources/config/crafting.yml"));
        check(config.contains("basic-survival-gear: true"), "basic survival gear is enabled by default");
        check(config.contains("crafting-restrictions:\n  enabled: false"),
                "legacy profession gates no longer block survival recipes");
        check(config.contains("smithing-upgrade: false"), "canonical netherite upgrade is denied by default");
        final int allowlistStart = config.indexOf("allowed-enchantments:");
        final int allowlistEnd = config.indexOf("durability:", allowlistStart);
        check(allowlistStart >= 0 && allowlistEnd > allowlistStart,
                "canonical enchant whitelist is explicit");
        final String allowlist = config.substring(allowlistStart, allowlistEnd);
        final List<String> signatureEnchants = List.of(
                "icesmp:jegfog", "icesmp:vihartuz", "icesmp:verszomj", "icesmp:fagypancel",
                "icesmp:fonixtoll", "icesmp:erc_erzek", "icesmp:bokic_kegye");
        for (final String enchant : signatureEnchants) {
            check(allowlist.contains("\"" + enchant + "\""),
                    "bootstrap signature enchant missing from canonical allowlist: " + enchant);
        }
        check(allowlist.lines().filter(line -> line.trim().startsWith("- \"icesmp:")).count()
                        == signatureEnchants.size(),
                "canonical enchant whitelist contains an unauthorised entry");
        check(config.contains("durability: \"vanilla\""), "canonical durability contract is explicit");
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/itemization/ItemTransformationPolicy.java"));
        check(policy.contains("enchanting && allowedEnchantments.isEmpty()"),
                "contradictory canonical enchanting policy fails validation");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
