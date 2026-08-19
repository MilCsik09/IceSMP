package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.classspec.transaction.RespecRecoveryProtocol;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.itemization.ArmorFamily;
import hu.taliann.icesmp.itemization.ItemIdentityService;
import hu.taliann.icesmp.itemization.ItemSalvageService;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real Paper 1.21.11 runtime proof for the final Professions 2.0 closure. */
public final class ProfessionsPaperRuntimeProbe {
    public static final String PASS_MARKER = "ICESMP_PROFESSIONS_RUNTIME_PROBE_PASS";

    private ProfessionsPaperRuntimeProbe() { }

    public static void maybeRun(final JavaPlugin plugin, final Object assembledCore) {
        if (!Boolean.getBoolean(hu.taliann.icesmp.itemization.PaperSourceIntegrityRuntimeProbe.PROPERTY)) return;
        final UniqueMaterialFactory uniqueMaterials = readField(assembledCore,
                "uniqueMaterialFactory", UniqueMaterialFactory.class);
        final ProfessionRecipeCatalog catalog = readField(assembledCore,
                "professionRecipeCatalog", ProfessionRecipeCatalog.class);
        final ItemIdentityService identity = readField(assembledCore,
                "itemIdentityService", ItemIdentityService.class);
        final ConfigManager config = readField(assembledCore, "configManager", ConfigManager.class);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            try {
                verifyCraftPlanRuntime(uniqueMaterials);
                verifyCanonicalCatalog(catalog, identity);
                verifyBlueprintRecovery();
                verifySalvageMappings(config);
                verifyRespecRecovery();
                plugin.getLogger().info(PASS_MARKER);
            } catch (final Throwable failure) {
                plugin.getLogger().severe("ICESMP_PROFESSIONS_RUNTIME_PROBE_FAIL: " + failure);
                failure.printStackTrace();
            }
        }, 1L);
    }

    private static void verifyCraftPlanRuntime(final UniqueMaterialFactory uniqueMaterials) {
        final ProfessionCraftTransaction tx = new ProfessionCraftTransaction(uniqueMaterials);
        final var efficiency = new ProfessionSpecializationEconomyPolicy.Effect(
                ProfessionSpecializationEconomyPolicy.Role.PROCESSING_EFFICIENCY,
                "runtime-efficiency", 0.90D, 1.0D);
        final ProfessionRecipeCatalog.Recipe processing = recipe(
                "runtime_processing", 16, Map.of(Material.IRON_INGOT, 16), Map.of());

        check(required(processing, efficiency, 1) == 15, "1x effective input must be 15");
        check(required(processing, efficiency, 2) == 30, "2x effective input must be 30");
        check(required(processing, efficiency, 5) == 75, "5x effective input must be 75");
        check(required(processing, efficiency, 64) == 960, "64x effective input must be 960");

        check(tx.preflightStorage(storageWith(Material.IRON_INGOT, 15),
                        ProfessionEffectiveCraftPlan.of(processing, efficiency, 1),
                        rawOutputs(Material.COPPER_INGOT, 16, 1)).applied(),
                "15 material must pass the exact 1x runtime transaction preflight");
        check(tx.preflightStorage(storageWith(Material.IRON_INGOT, 14),
                        ProfessionEffectiveCraftPlan.of(processing, efficiency, 1),
                        rawOutputs(Material.COPPER_INGOT, 16, 1)).status()
                        == ProfessionCraftTransaction.Status.MISSING_INGREDIENTS,
                "14 material must fail the same 1x runtime preflight");
        check(tx.preflightStorage(storageWith(Material.IRON_INGOT, 75),
                        ProfessionEffectiveCraftPlan.of(processing, efficiency, 5),
                        rawOutputs(Material.COPPER_INGOT, 16, 5)).applied(),
                "5x processing must pass with exactly 75 effective material");
        check(tx.preflightStorage(storageWith(Material.IRON_INGOT, 960),
                        ProfessionEffectiveCraftPlan.of(processing, efficiency, 64),
                        rawOutputs(Material.COPPER_INGOT, 16, 64)).applied(),
                "maximum 64x plan must use the same runtime arithmetic and capacity simulation");

        final ProfessionRecipeCatalog.Recipe mixed = recipe(
                "runtime_mixed", 1, Map.of(Material.IRON_INGOT, 16), Map.of("runapor", 16));
        final ProfessionEffectiveCraftPlan mixedPlan = ProfessionEffectiveCraftPlan.of(mixed, efficiency, 1);
        final ItemStack unique = uniqueMaterials.create("runapor", 15);
        check(unique != null, "runtime unique material fixture must resolve");
        final ItemStack[] mixedStorage = new ItemStack[36];
        mixedStorage[0] = new ItemStack(Material.IRON_INGOT, 15);
        mixedStorage[1] = unique;
        check(tx.preflightStorage(mixedStorage, mixedPlan,
                        rawOutputs(Material.COPPER_INGOT, 1, 1)).applied(),
                "plain + unique effective inputs must share one runtime plan");

        final var yield = new ProfessionSpecializationEconomyPolicy.Effect(
                ProfessionSpecializationEconomyPolicy.Role.PROCESSING_YIELD,
                "runtime-yield", 1.0D, 1.10D);
        final ProfessionRecipeCatalog.Recipe yielding = recipe(
                "runtime_yield", 16, Map.of(Material.IRON_INGOT, 1), Map.of());
        final ProfessionEffectiveCraftPlan yieldPlan = ProfessionEffectiveCraftPlan.of(yielding, yield, 5);
        check(yieldPlan.effectiveOutputAmount(16) == 85,
                "5x yield must equal five independently rounded 17-output crafts");
        final List<ItemStack> yielded = yieldPlan.effectiveOutputs(rawOutputs(Material.COPPER_INGOT, 16, 5));
        check(yielded.size() == 5 && yielded.stream().allMatch(item -> item.getAmount() == 17),
                "runtime yield projection must remain per-craft before batching");
        check(tx.preflightStorage(storageWith(Material.IRON_INGOT, 5), yieldPlan,
                        rawOutputs(Material.COPPER_INGOT, 16, 5)).applied(),
                "yield-adjusted outputs must use the execution capacity simulator");

        final ProfessionRecipeCatalog.Recipe oneForOne = recipe(
                "runtime_full", 1, Map.of(Material.IRON_INGOT, 1), Map.of());
        final ItemStack[] full = new ItemStack[36];
        full[0] = new ItemStack(Material.IRON_INGOT, 64);
        for (int slot = 1; slot < full.length; slot++) full[slot] = new ItemStack(Material.COBBLESTONE, 64);
        check(tx.preflightStorage(full,
                        ProfessionEffectiveCraftPlan.of(oneForOne,
                                ProfessionSpecializationEconomyPolicy.Effect.none(), 1),
                        rawOutputs(Material.COPPER_INGOT, 1, 1)).status()
                        == ProfessionCraftTransaction.Status.INVENTORY_FULL,
                "full inventory must reject output before consuming any live inventory");
    }

    private static void verifyCanonicalCatalog(final ProfessionRecipeCatalog catalog,
                                               final ItemIdentityService identity) {
        final boolean longTerm = catalog.get("lte_fonixszovet_sisak") != null;
        if (longTerm) {
            check(catalog.allIds().size() >= 471,
                    "long-term production catalog unexpectedly small: " + catalog.allIds().size());
        } else {
            check(catalog.allIds().size() == 407,
                    "production Professions 2.0 catalog must load 407 recipes");
        }
        int canonical = 0;
        for (final String id : catalog.allIds()) {
            final ProfessionRecipeCatalog.Recipe recipe = catalog.get(id);
            if (recipe.templateId() != null) canonical++;
        }
        // The 64 crafted armor target is total production armor, not 64 additions: six of those
        // pieces are preserved pre-existing canonical crafted anchors already counted in the
        // Professions 2.0 baseline. Therefore the cumulative recipe authority has 18 + 58 = 76.
        check(canonical == (longTerm ? 76 : 18),
                "production catalog canonical equipment recipe count mismatch: " + canonical);
        final ProfessionRecipeCatalog.Recipe equipment = catalog.get("p2_fonixpihe_kopeny");
        check(equipment != null && equipment.templateId() != null,
                "Professions 2.0 canonical runtime recipe must resolve a template");
        final ItemStack item = identity.create(equipment.templateId(),
                "runtime:profession-craft", "paper", null);
        check(identity.inspect(item).status() == ItemIdentityService.Status.VALID,
                "canonical profession result must produce a VALID ItemInstance on Paper");
        if (longTerm) {
            final ProfessionRecipeCatalog.Recipe expanded = catalog.get("lte_fonixszovet_sisak");
            check(expanded != null && "fonixszovet_sisak".equals(expanded.templateId()),
                    "long-term canonical profession output must resolve through the production catalog");
            final ItemStack expandedItem = identity.create(expanded.templateId(),
                    "runtime:long-term-profession-craft", "paper", null);
            check(identity.inspect(expandedItem).status() == ItemIdentityService.Status.VALID,
                    "long-term canonical profession output must produce a VALID ItemInstance on Paper");
        }
    }

    private static void verifyBlueprintRecovery() {
        check(BlueprintRecoveryPolicy.decide(false, false)
                        == BlueprintRecoveryPolicy.Decision.ROLLBACK_UNTOUCHED,
                "blueprint PREPARED-before-reservation recovery mismatch");
        check(BlueprintRecoveryPolicy.decide(false, true)
                        == BlueprintRecoveryPolicy.Decision.RELEASE_AND_ROLLBACK,
                "blueprint reservation rollback recovery mismatch");
        check(BlueprintRecoveryPolicy.decide(true, true)
                        == BlueprintRecoveryPolicy.Decision.CONSUME_AND_COMMIT,
                "blueprint learned+reserved recovery mismatch");
        check(BlueprintRecoveryPolicy.decide(true, false)
                        == BlueprintRecoveryPolicy.Decision.COMMIT_CONSUMED,
                "blueprint post-consumption recovery mismatch");
    }

    private static void verifySalvageMappings(final ConfigManager config) {
        final Map<ArmorFamily, String> expected = Map.of(
                ArmorFamily.CLOTH, "szovet_foszlany",
                ArmorFamily.LEATHER, "bor_hulladek",
                ArmorFamily.MAIL, "lanc_toredek",
                ArmorFamily.PLATE, "femhulladek");
        expected.forEach((family, material) -> {
            check(material.equals(ItemSalvageService.familyScrapId(family)),
                    "family salvage projection mismatch: " + family);
            check(material.equals(config.getString("itemization.salvage.output-map." + material, "")),
                    "live merged config salvage mapping mismatch: " + family);
        });
    }

    private static void verifyRespecRecovery() {
        check(RespecRecoveryProtocol.decide(true, 100.0D,
                        RespecRecoveryProtocol.WalletWitness.DEBITED).action()
                        == RespecRecoveryProtocol.Action.COMMIT_WALLET_AND_COMPLETE,
                "committed profession respec must finalize the durable wallet debit");
        check(RespecRecoveryProtocol.decide(false, 100.0D,
                        RespecRecoveryProtocol.WalletWitness.DEBITED).action()
                        == RespecRecoveryProtocol.Action.ROLLBACK_WALLET_AND_ABORT,
                "failed profession respec must roll back an outstanding debit");
    }

    private static int required(final ProfessionRecipeCatalog.Recipe recipe,
                                final ProfessionSpecializationEconomyPolicy.Effect effect,
                                final int batches) {
        return ProfessionEffectiveCraftPlan.of(recipe, effect, batches)
                .materialInputs().get(Material.IRON_INGOT);
    }

    private static ProfessionRecipeCatalog.Recipe recipe(
            final String id, final int outputAmount,
            final Map<Material, Integer> materials, final Map<String, Integer> unique) {
        return new ProfessionRecipeCatalog.Recipe(id, ProfessionType.ARMORER, 1, false,
                id, "processing", Material.COPPER_INGOT, outputAmount,
                null, null, materials, unique, List.of(), null, null,
                false, null, "processing", null, false);
    }

    private static List<ItemStack> rawOutputs(final Material material,
                                              final int amount, final int batches) {
        final ArrayList<ItemStack> result = new ArrayList<>(batches);
        for (int index = 0; index < batches; index++) result.add(new ItemStack(material, amount));
        return List.copyOf(result);
    }

    private static ItemStack[] storageWith(final Material material, final int amount) {
        final ItemStack[] storage = new ItemStack[36];
        int remaining = amount;
        int slot = 0;
        while (remaining > 0) {
            final int moved = Math.min(64, remaining);
            storage[slot++] = new ItemStack(material, moved);
            remaining -= moved;
        }
        return storage;
    }

    private static <T> T readField(final Object target, final String name, final Class<T> type) {
        try {
            final Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (final ReflectiveOperationException failure) {
            throw new IllegalStateException("profession runtime probe cannot read core field: " + name, failure);
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
