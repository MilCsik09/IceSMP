package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.items.ItemDataFactory;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opt-in Paper 1.21.11 runtime proof for source-integrity contracts which cannot be established
 * in a registry-less standalone JVM. It is inert in production unless the dedicated JVM property
 * is set by the CI run-paper workflow.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PaperSourceIntegrityRuntimeProbe {
    public static final String PROPERTY = "icesmp.source-integrity-runtime";
    public static final String PASS_MARKER = "ICESMP_SOURCE_INTEGRITY_RUNTIME_PROBE_PASS";

    private PaperSourceIntegrityRuntimeProbe() { }

    public static void maybeRun(final JavaPlugin plugin, final Object assembledCore) {
        if (!Boolean.getBoolean(PROPERTY)) return;
        final ItemIdentityService identity = readField(assembledCore,
                "itemIdentityService", ItemIdentityService.class);
        final ProfessionRecipeCatalog catalog = readField(assembledCore,
                "professionRecipeCatalog", ProfessionRecipeCatalog.class);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            try {
                verifyExplicitEmptyAttributes();
                verifyEquipmentRuntimeStates(identity);
                verifyActualInventoryAtomicity();
                verifyMutationPhysicalState(identity);
                verifyCatalogPositiveLoad(catalog);
                plugin.getLogger().info(PASS_MARKER);
            } catch (final Throwable failure) {
                plugin.getLogger().severe("ICESMP_SOURCE_INTEGRITY_RUNTIME_PROBE_FAIL: " + failure);
                failure.printStackTrace();
            } finally {
                Bukkit.shutdown();
            }
        }, 1L);
    }

    private static <T> T readField(final Object target, final String name, final Class<T> type) {
        try {
            final Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (final ReflectiveOperationException failure) {
            throw new IllegalStateException("runtime probe cannot read assembled core field: " + name, failure);
        }
    }

    private static void verifyExplicitEmptyAttributes() {
        final ItemStack vanilla = new ItemStack(Material.IRON_SWORD);
        check(!vanilla.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "fresh vanilla sword must use implicit material defaults");
        final var defaults = vanilla.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(defaults != null && !defaults.modifiers().isEmpty(),
                "fresh vanilla sword must expose implicit combat modifiers");

        ItemDataFactory.applyCanonicalAttributeModifiers(vanilla, List.of(), false);
        check(vanilla.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "zero-stat canonical item must explicitly own ATTRIBUTE_MODIFIERS");
        final var canonical = vanilla.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(canonical != null && canonical.modifiers().isEmpty(),
                "explicit empty canonical component must suppress backing defaults");

        final ItemStack suppressed = new ItemStack(Material.IRON_SWORD);
        final var meta = suppressed.getItemMeta();
        meta.setAttributeModifiers(com.google.common.collect.ArrayListMultimap.create());
        suppressed.setItemMeta(meta);
        check(suppressed.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "managed-invalid suppression must explicitly own ATTRIBUTE_MODIFIERS");
        final var suppressedData = suppressed.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(suppressedData != null && suppressedData.modifiers().isEmpty(),
                "managed-invalid suppression must leave zero combat modifiers");
    }

    /**
     * Final P1-009 matrix on real Paper ItemStacks. The pure admission state machine and the same
     * suppression/reactivation methods used by equipment reconciliation are exercised together so
     * backing Material defaults cannot masquerade as an active canonical contribution.
     */
    private static void verifyEquipmentRuntimeStates(final ItemIdentityService identity) {
        final ItemStack canonical = identity.create("glatziendorfi_jegvert",
                "runtime:equipment", "paper", null);
        final ItemIdentityService.Inspection inspection = identity.inspect(canonical);
        check(inspection.status() == ItemIdentityService.Status.VALID,
                "equipment probe canonical item must start VALID");
        check(canonical.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "canonical render must explicitly own ATTRIBUTE_MODIFIERS");
        final var activeData = canonical.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(activeData != null && !activeData.modifiers().isEmpty(),
                "authored armor probe must expose canonical modifiers before suppression");
        final var activeModifiers = List.copyOf(activeData.modifiers());

        check(EquipmentProficiencyService.decideActivity(ItemIdentityService.Status.VALID,
                        true, false, true, false, false)
                        == EquipmentProficiencyService.ActivityStatus.RESTRICTED,
                "wrong-family/class restriction must be runtime-inert");
        identity.setEquipmentSuppressed(canonical, inspection.template(), inspection.instance(), true);
        check(identity.isEquipmentSuppressed(canonical),
                "restricted canonical item must carry runtime suppression state");
        final var restricted = canonical.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(canonical.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                        && restricted != null && restricted.modifiers().isEmpty(),
                "wrong-family/restricted canonical item must have zero effective modifiers");
        check(EquipmentProficiencyService.decideActivity(ItemIdentityService.Status.VALID,
                        true, false, true, true, true)
                        == EquipmentProficiencyService.ActivityStatus.SUPPRESSED,
                "suppression marker must remain authoritative until reconciliation reactivates");

        identity.setEquipmentSuppressed(canonical, inspection.template(), inspection.instance(), false);
        check(!identity.isEquipmentSuppressed(canonical),
                "valid reconciliation must remove the transient suppression marker");
        final var reactivated = canonical.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(reactivated != null && activeModifiers.equals(List.copyOf(reactivated.modifiers())),
                "reactivation must restore exactly the canonical modifier projection");
        check(identity.inspect(canonical).status() == ItemIdentityService.Status.VALID,
                "suppression/reactivation must not rewrite canonical identity/checksum state");

        final ItemStack invalid = canonical.clone();
        invalid.setAmount(2);
        check(identity.inspect(invalid).status() == ItemIdentityService.Status.TEMPLATE_MISMATCH,
                "managed-invalid runtime fixture must be rejected by canonical identity");
        check(EquipmentProficiencyService.decideActivity(ItemIdentityService.Status.TEMPLATE_MISMATCH,
                        true, false, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.INVALID_IDENTITY,
                "managed-invalid canonical item must fail closed before proficiency contribution");
        identity.suppressManagedInvalid(invalid);
        final var invalidData = invalid.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(identity.isEquipmentSuppressed(invalid)
                        && invalid.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                        && invalidData != null && invalidData.modifiers().isEmpty(),
                "managed-invalid canonical item must be physically attribute-inert");

        final ItemStack basic = new ItemStack(Material.IRON_SWORD);
        check(EquipmentProficiencyService.decideActivity(ItemIdentityService.Status.NOT_MANAGED,
                        true, false, false, false, false)
                        == EquipmentProficiencyService.ActivityStatus.NOT_MANAGED,
                "BASIC/not-managed item must stay outside the MMO activity gate");
        check(!basic.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "BASIC vanilla control must retain implicit Material attribute ownership");
        final var basicDefaults = basic.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(basicDefaults != null && !basicDefaults.modifiers().isEmpty(),
                "BASIC vanilla control must still expose backing Material defaults");
    }

    private static void verifyActualInventoryAtomicity() {
        final ItemStack rune = new ItemStack(Material.AMETHYST_SHARD, 64);
        final Inventory trigger = fullStorageWithPartialRune();
        final Map<Integer, ItemStack> triggerLeftovers = trigger.addItem(rune.clone());
        check(trigger.getItem(0) != null && trigger.getItem(0).getAmount() == 64,
                "Paper Inventory.addItem must reproduce the partial merge trigger");
        check(triggerLeftovers.values().stream().mapToInt(ItemStack::getAmount).sum() == 63,
                "Paper Inventory.addItem trigger must leave 63 after merging one item");

        final Inventory protectedInventory = fullStorageWithPartialRune();
        final AtomicReference<ItemStack> cursor = new AtomicReference<>(rune.clone());
        final ItemStack[] before = cloneContents(protectedInventory.getStorageContents());
        final boolean failed = AtomicCursorRehome.rehome(adapter(protectedInventory, cursor,
                new AtomicInteger(), false), cursor.get());
        check(!failed, "atomic rehome must reject partial-stack plus otherwise-full inventory");
        check(Arrays.deepEquals(serialize(before), serialize(protectedInventory.getStorageContents())),
                "failed atomic rehome must preserve exact storage state");
        check(cursor.get() != null && cursor.get().getAmount() == 64,
                "failed atomic rehome must preserve exact cursor state");

        final Inventory enough = Bukkit.createInventory(null, 36);
        for (int slot = 0; slot < 35; slot++) enough.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        final AtomicReference<ItemStack> enoughCursor = new AtomicReference<>(rune.clone());
        final AtomicInteger successPersists = new AtomicInteger();
        check(AtomicCursorRehome.rehome(adapter(enough, enoughCursor, successPersists, false),
                        enoughCursor.get()),
                "exactly sufficient inventory must accept full cursor rehome");
        check(enoughCursor.get() == null && successPersists.get() == 1,
                "successful cursor rehome must clear cursor after one durable save");

        final Inventory rollback = Bukkit.createInventory(null, 36);
        for (int slot = 0; slot < 35; slot++) rollback.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        final ItemStack[] rollbackBefore = cloneContents(rollback.getStorageContents());
        final AtomicReference<ItemStack> rollbackCursor = new AtomicReference<>(rune.clone());
        final AtomicInteger rollbackPersists = new AtomicInteger();
        check(!AtomicCursorRehome.rehome(adapter(rollback, rollbackCursor, rollbackPersists, true),
                        rollbackCursor.get()),
                "persistence exception must fail cursor rehome");
        check(Arrays.deepEquals(serialize(rollbackBefore), serialize(rollback.getStorageContents())),
                "persistence exception must restore exact storage snapshot");
        check(rollbackCursor.get() != null && rollbackCursor.get().getAmount() == 64,
                "persistence exception must restore cursor snapshot");
        check(rollbackPersists.get() == 2,
                "persistence exception must attempt a second durable rollback save");
    }

    private static AtomicCursorRehome.Adapter adapter(final Inventory inventory,
                                                       final AtomicReference<ItemStack> cursor,
                                                       final AtomicInteger persists,
                                                       final boolean failFirstPersist) {
        return new AtomicCursorRehome.Adapter() {
            @Override public ItemStack[] storageContents() { return inventory.getStorageContents(); }
            @Override public Map<Integer, ItemStack> add(final ItemStack stack) { return inventory.addItem(stack); }
            @Override public void restoreStorage(final ItemStack[] snapshot) { inventory.setStorageContents(snapshot); }
            @Override public ItemStack cursor() { return cursor.get(); }
            @Override public void setCursor(final ItemStack stack) { cursor.set(stack); }
            @Override public void persist() {
                final int call = persists.incrementAndGet();
                if (failFirstPersist && call == 1) throw new IllegalStateException("simulated saveData failure");
            }
        };
    }

    private static Inventory fullStorageWithPartialRune() {
        final Inventory inventory = Bukkit.createInventory(null, 36);
        inventory.setItem(0, new ItemStack(Material.AMETHYST_SHARD, 63));
        for (int slot = 1; slot < 36; slot++) inventory.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        return inventory;
    }

    private static void verifyMutationPhysicalState(final ItemIdentityService identity) {
        final ItemStack previous = identity.create("glatziendorfi_jegvert",
                "runtime:probe", "paper", null);
        final ItemIdentityService.Inspection inspection = identity.inspect(previous);
        check(inspection.status() == ItemIdentityService.Status.VALID,
                "runtime probe canonical item must start VALID");
        final var oldMeta = previous.getItemMeta();
        check(oldMeta instanceof Damageable, "runtime probe template must use a damageable backing material");
        final Damageable damageable = (Damageable) oldMeta;
        damageable.setDamage(Math.min(17, Math.max(1, previous.getType().getMaxDurability() - 1)));
        final int expectedDamage = damageable.getDamage();
        oldMeta.setUnbreakable(true);
        oldMeta.lore(List.of(net.kyori.adventure.text.Component.text("STALE_RUNTIME_PROBE_LORE")));
        previous.setItemMeta(oldMeta);

        final ItemStack fresh = identity.render(inspection.template(), inspection.instance());
        final ItemStack preserved = CanonicalPhysicalState.preserve(previous, fresh);
        final var preservedMeta = preserved.getItemMeta();
        check(preservedMeta instanceof Damageable
                        && ((Damageable) preservedMeta).getDamage() == expectedDamage,
                "mutation render must preserve exact physical durability damage");
        check(!preservedMeta.isUnbreakable(),
                "mutation render must not launder non-authoritative unbreakable metadata");
        check(preservedMeta.lore() == null || preservedMeta.lore().stream()
                        .noneMatch(line -> line.toString().contains("STALE_RUNTIME_PROBE_LORE")),
                "mutation render must rebuild authored lore instead of restoring stale meta");
        check(identity.inspect(preserved).status() == ItemIdentityService.Status.VALID,
                "mutation physical-state preservation must leave canonical checksum VALID");
    }

    private static void verifyCatalogPositiveLoad(final ProfessionRecipeCatalog catalog) {
        final int count = catalog.allIds().size();
        final boolean professions2 = catalog.get("p2_fonixpihe_kopeny") != null;
        final int expected = professions2 ? 407 : 392;
        final int expectedCanonical = professions2 ? 18 : 15;
        check(count == expected, "production catalog effective recipe count mismatch: " + count);
        int canonical = 0;
        for (final String id : catalog.allIds()) {
            final ProfessionRecipeCatalog.Recipe recipe = catalog.get(id);
            check(recipe != null && recipe.result() != null && !recipe.result().isAir(),
                    "every production recipe must resolve a non-AIR backing material: " + id);
            if (recipe.templateId() != null) canonical++;
        }
        check(canonical == expectedCanonical,
                "production catalog canonical recipe count mismatch: " + canonical);
        if (professions2) {
            for (final String id : List.of("p2_fonixpihe_kopeny", "p2_vadorzo_csizma", "p2_csontenyv_pancel")) {
                final ProfessionRecipeCatalog.Recipe recipe = catalog.get(id);
                check(recipe != null && recipe.templateId() != null && !recipe.result().isAir(),
                        "Professions 2.0 canonical recipe failed production load: " + id);
            }
        }
    }

    private static ItemStack[] cloneContents(final ItemStack[] source) {
        final ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    private static byte[][] serialize(final ItemStack[] source) {
        final byte[][] encoded = new byte[source.length][];
        for (int i = 0; i < source.length; i++) {
            encoded[i] = source[i] == null ? new byte[0] : source[i].serializeAsBytes();
        }
        return encoded;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
