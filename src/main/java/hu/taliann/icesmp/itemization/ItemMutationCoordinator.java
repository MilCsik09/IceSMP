package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.storage.ItemMutationJournal;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Durable one-player inventory boundary for reroll, ascension and salvage. */
public final class ItemMutationCoordinator implements Listener {

    public enum Operation { REROLL, ASCEND, SALVAGE }

    public record Options(String lockedStat, boolean qualityAmplifier, boolean stabilitySeal) { }

    public record Preview(boolean allowed, String errorKey, ItemInstance instance,
                          ItemTemplate template, Map<String, Integer> costs,
                          String resultSummary) {
        public Preview { costs = Map.copyOf(costs == null ? Map.of() : costs); }
    }

    public record Outcome(boolean success, String messageKey, ItemInstance result) { }

    private record Plan(UUID operationId, Operation operation, ItemInstance before,
                        ItemInstance after, ItemStack[] beforeInventory,
                        ItemStack[] afterInventory) { }

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ItemIdentityService identity;
    private final UniqueMaterialFactory materials;
    private final MessageManager messages;
    private final ItemMutationService mutations = new ItemMutationService();
    private final ItemSalvageService salvage = new ItemSalvageService();
    private final ItemMutationJournal journal;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public ItemMutationCoordinator(final JavaPlugin plugin, final ConfigManager config,
                                   final ItemIdentityService identity,
                                   final UniqueMaterialFactory materials,
                                   final MessageManager messages) {
        this.plugin = plugin;
        this.config = config;
        this.identity = identity;
        this.materials = materials;
        this.messages = messages;
        this.journal = new ItemMutationJournal(plugin, new File(plugin.getDataFolder(),
                "item-mutation-journal.yml"), plugin.getLogger());
        this.journal.load();
    }

    public Preview preview(final Player player, final Operation operation, final Options options) {
        if (!journal.entriesFor(player.getUniqueId()).isEmpty()) {
            return new Preview(false, "itemization-operation-pending", null, null,
                    Map.of(), "A korábbi művelet tartós recoveryre vár");
        }
        final ItemStack held = player.getInventory().getItemInMainHand();
        final ItemIdentityService.Inspection inspection = identity.inspect(held);
        if (inspection.status() != ItemIdentityService.Status.VALID) {
            return new Preview(false, inspection.status() == ItemIdentityService.Status.NOT_MANAGED
                    ? "itemization-not-managed" : "itemization-invalid-identity",
                    inspection.instance(), inspection.template(), Map.of(), "");
        }
        if (!identity.inspectDuplicates(Arrays.asList(player.getInventory().getContents())).clean()) {
            return new Preview(false, "itemization-duplicate", inspection.instance(),
                    inspection.template(), Map.of(), "");
        }
        final ItemInstance item = inspection.instance();
        final ItemTemplate template = inspection.template();
        final Preview result = switch (operation) {
            case REROLL -> rerollPreview(item, template, options);
            case ASCEND -> ascendPreview(item, template);
            case SALVAGE -> salvagePreview(item, template);
        };
        if (result.allowed() && !hasCosts(player.getInventory().getContents(), result.costs())) {
            return new Preview(false, "itemization-insufficient-materials", item, template,
                    result.costs(), result.resultSummary());
        }
        return result;
    }

    public void execute(final Player player, final Operation operation, final Options options,
                        final Consumer<Outcome> callback) {
        if (!inFlight.add(player.getUniqueId())) {
            callback.accept(new Outcome(false, "itemization-operation-pending", null));
            return;
        }
        final Preview preview = preview(player, operation, options);
        if (!preview.allowed()) {
            inFlight.remove(player.getUniqueId());
            callback.accept(new Outcome(false, preview.errorKey(), preview.instance()));
            return;
        }
        final Plan plan;
        try {
            plan = buildPlan(player, operation, options, preview);
        } catch (final RuntimeException failure) {
            inFlight.remove(player.getUniqueId());
            plugin.getLogger().warning("Item mutation candidate rejected: " + failure.getMessage());
            callback.accept(new Outcome(false, "itemization-candidate-failed", preview.instance()));
            return;
        }
        final ItemMutationJournal.Entry entry = new ItemMutationJournal.Entry(plan.operationId(),
                player.getUniqueId(), operation.name(), plan.before().itemId(),
                ItemMutationJournal.encodeInventory(plan.beforeInventory()),
                ItemMutationJournal.encodeInventory(plan.afterInventory()), System.currentTimeMillis());
        journal.prepare(entry).whenComplete((prepared, failure) ->
                player.getScheduler().run(plugin, task -> {
                    if (failure != null || !Boolean.TRUE.equals(prepared) || !player.isOnline()) {
                        inFlight.remove(player.getUniqueId());
                        callback.accept(new Outcome(false, "itemization-journal-failed", plan.before()));
                        return;
                    }
                    final List<String> current = ItemMutationJournal.encodeInventory(
                            player.getInventory().getContents());
                    if (!current.equals(entry.beforeInventory())) {
                        journal.complete(entry.operationId());
                        inFlight.remove(player.getUniqueId());
                        callback.accept(new Outcome(false, "itemization-stale-operation", plan.before()));
                        return;
                    }
                    try {
                        player.getInventory().setContents(cloneContents(plan.afterInventory()));
                        player.saveData();
                    } catch (final RuntimeException persistenceFailure) {
                        player.getInventory().setContents(cloneContents(plan.beforeInventory()));
                        try { player.saveData(); } catch (final RuntimeException rollbackFailure) {
                            persistenceFailure.addSuppressed(rollbackFailure);
                        }
                        inFlight.remove(player.getUniqueId());
                        plugin.getLogger().severe("Item mutation playerdata commit failed for "
                                + player.getUniqueId() + ": " + persistenceFailure.getMessage());
                        callback.accept(new Outcome(false, "itemization-persistence-failed", plan.before()));
                        return;
                    }
                    journal.complete(entry.operationId()).whenComplete((completed, journalFailure) ->
                            player.getScheduler().run(plugin, done -> {
                                inFlight.remove(player.getUniqueId());
                                callback.accept(new Outcome(true,
                                        Boolean.TRUE.equals(completed) && journalFailure == null
                                                ? "itemization-operation-success"
                                                : "itemization-recovery-pending", plan.after()));
                            }, () -> inFlight.remove(player.getUniqueId())));
                }, () -> inFlight.remove(player.getUniqueId())));
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final List<ItemMutationJournal.Entry> entries = journal.entriesFor(player.getUniqueId());
        if (entries.isEmpty()) return;
        player.getScheduler().run(plugin, task -> recover(player, entries), null);
    }

    private void recover(final Player player, final List<ItemMutationJournal.Entry> entries) {
        final List<String> current = ItemMutationJournal.encodeInventory(player.getInventory().getContents());
        for (final ItemMutationJournal.Entry entry : entries) {
            switch (ItemMutationRecoveryPolicy.decide(current, entry.beforeInventory(),
                    entry.afterInventory())) {
                case ABORT_BEFORE, COMMIT_AFTER -> journal.complete(entry.operationId());
                case MANUAL_REVIEW -> {
                    plugin.getLogger().severe("Item mutation recovery requires review: "
                            + entry.operationId() + " / " + player.getUniqueId());
                    player.sendMessage(messages.get("itemization-recovery-review",
                            "&cEgy korábbi tárgyművelet állapota nem egyértelmű; az adminok értesítést kaptak."));
                }
            }
        }
    }

    private Preview rerollPreview(final ItemInstance item, final ItemTemplate template,
                                  final Options options) {
        final String lock = options == null || options.lockedStat() == null
                ? "" : options.lockedStat();
        if (template.rolledStatsAt(item.ascension().stageId()).isEmpty()) {
            return new Preview(false, "itemization-no-rolls", item, template, Map.of(), "");
        }
        if (!lock.isBlank() && !template.rolledStatsAt(item.ascension().stageId())
                .containsKey(ItemStatCatalog.normalizeId(lock))) {
            return new Preview(false, "itemization-invalid-lock", item, template, Map.of(), "");
        }
        final LinkedHashMap<String, Integer> costs = new LinkedHashMap<>();
        mergeCost(costs, config.getString("itemization.reroll.base-material", "runapor"),
                Math.toIntExact(ItemMutationService.boundedGeometricCost(
                        Math.max(1, config.getLong("itemization.reroll.base-amount", 2L)),
                        Math.max(1.0D, config.getDouble("itemization.reroll.growth", 1.55D)),
                        item.mutation().rerollCostStep(),
                        Math.min(Integer.MAX_VALUE, Math.max(1L,
                                config.getLong("itemization.reroll.maximum-amount", 32L))))));
        if (!lock.isBlank()) mergeCost(costs, config.getString(
                "itemization.reroll.stat-lock-extra-material", "folyositoszer"),
                Math.max(0, config.getInt("itemization.reroll.stat-lock-extra-amount", 1)));
        if (options != null && options.qualityAmplifier()) mergeCost(costs, config.getString(
                "itemization.reroll.quality-amplifier-material", "sarkfeny_cseppko"), 1);
        if (options != null && options.stabilitySeal()) mergeCost(costs, config.getString(
                "itemization.reroll.stability-material", "viaszpecset"), 1);
        return new Preview(true, "", item, template, costs,
                lock.isBlank() ? "Minden authored roll újragurul" : "Megmarad: " + lock);
    }

    private Preview ascendPreview(final ItemInstance item, final ItemTemplate template) {
        if (item.ascension().stageIndex() >= template.ascensionPath().size()) {
            return new Preview(false, template.ascensionPath().isEmpty()
                    ? "itemization-not-ascendable" : "itemization-terminal-stage",
                    item, template, Map.of(), "");
        }
        final String stage = template.ascensionPath().get(item.ascension().stageIndex());
        final ConfigurationSection section = config.getConfiguration().getConfigurationSection(
                "itemization.ascension." + template.templateId() + '.' + stage + ".materials");
        if (section == null) return new Preview(false, "itemization-ascension-cost-missing",
                item, template, Map.of(), "");
        final LinkedHashMap<String, Integer> costs = new LinkedHashMap<>();
        for (final String id : section.getKeys(false)) {
            final int amount = section.getInt(id);
            if (amount <= 0) return new Preview(false, "itemization-ascension-cost-missing",
                    item, template, Map.of(), "");
            mergeCost(costs, id, amount);
        }
        if (costs.isEmpty()) return new Preview(false, "itemization-ascension-cost-missing",
                item, template, Map.of(), "");
        return new Preview(true, "", item, template, costs,
                stage + " • ugyanaz az UUID és relatív roll-quality");
    }

    private Preview salvagePreview(final ItemInstance item, final ItemTemplate template) {
        final ItemSalvageService.Preview preview = salvage.preview(template, item,
                salvageTuning(), 64, false);
        return new Preview(preview.allowed(), "itemization-salvage-"
                + preview.status().name().toLowerCase(Locale.ROOT),
                item, template, Map.of(), preview.outputs().toString());
    }

    private Plan buildPlan(final Player player, final Operation operation, final Options options,
                           final Preview preview) {
        final UUID operationId = UUID.randomUUID();
        final ItemStack[] before = cloneContents(player.getInventory().getContents());
        final int heldSlot = player.getInventory().getHeldItemSlot();
        final ItemStack[] after = cloneContents(before);
        if (!consumeCosts(after, preview.costs())) throw new IllegalStateException("insufficient materials");
        ItemInstance candidate = preview.instance();
        switch (operation) {
            case REROLL -> {
                final double floor = options != null && options.qualityAmplifier()
                        ? Math.max(0.0D, Math.min(1.0D, config.getDouble(
                        "itemization.reroll.quality-amplifier-floor", 0.65D))) : 0.0D;
                final ItemMutationService.Result result = mutations.reroll(preview.template(), preview.instance(),
                        new ItemMutationService.RerollRequest(operationId,
                                options == null ? "" : options.lockedStat(), floor,
                                options != null && options.stabilitySeal(), System.currentTimeMillis()),
                        () -> ThreadLocalRandom.current().nextDouble());
                if (!result.applied()) throw new IllegalStateException(result.status().name());
                candidate = result.candidate();
                after[heldSlot] = identity.render(preview.template(), candidate);
            }
            case ASCEND -> {
                final ItemMutationService.Result result = mutations.ascend(preview.template(), preview.instance(),
                        new ItemMutationService.AscensionRequest(operationId, System.currentTimeMillis()));
                if (!result.applied()) throw new IllegalStateException(result.status().name());
                candidate = result.candidate();
                after[heldSlot] = identity.render(preview.template(), candidate);
            }
            case SALVAGE -> {
                after[heldSlot] = null;
                final ItemSalvageService.Preview salvagePreview = salvage.preview(preview.template(),
                        preview.instance(), salvageTuning(), 64, false);
                for (final Map.Entry<String, Integer> output : salvagePreview.outputs().entrySet()) {
                    final String materialId = config.getString(
                            "itemization.salvage.output-map." + output.getKey(),
                            "signature_dust".equals(output.getKey()) ? "folyositoszer" : "runapor");
                    final ItemStack stack = materials.create(materialId, output.getValue());
                    if (stack == null || !addToStorage(after, stack)) {
                        throw new IllegalStateException("salvage output does not fit");
                    }
                }
            }
        }
        return new Plan(operationId, operation, preview.instance(), candidate, before, after);
    }

    private boolean consumeCosts(final ItemStack[] contents, final Map<String, Integer> costs) {
        if (!hasCosts(contents, costs)) return false;
        for (final Map.Entry<String, Integer> cost : costs.entrySet()) {
            int remaining = cost.getValue();
            for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                final ItemStack item = contents[slot];
                if (item == null || !cost.getKey().equals(materials.idOf(item))) continue;
                final int take = Math.min(remaining, item.getAmount());
                remaining -= take;
                if (take == item.getAmount()) contents[slot] = null;
                else item.setAmount(item.getAmount() - take);
            }
        }
        return true;
    }

    private boolean hasCosts(final ItemStack[] contents, final Map<String, Integer> costs) {
        for (final Map.Entry<String, Integer> cost : costs.entrySet()) {
            int count = 0;
            for (final ItemStack item : contents) {
                if (item != null && cost.getKey().equals(materials.idOf(item))) count += item.getAmount();
            }
            if (count < cost.getValue()) return false;
        }
        return true;
    }

    private static boolean addToStorage(final ItemStack[] contents, final ItemStack added) {
        int remaining = added.getAmount();
        final int storageEnd = Math.min(36, contents.length);
        for (int slot = 0; slot < storageEnd && remaining > 0; slot++) {
            final ItemStack current = contents[slot];
            if (current != null && current.isSimilar(added)
                    && current.getAmount() < current.getMaxStackSize()) {
                final int move = Math.min(remaining, current.getMaxStackSize() - current.getAmount());
                current.setAmount(current.getAmount() + move);
                remaining -= move;
            }
        }
        for (int slot = 0; slot < storageEnd && remaining > 0; slot++) {
            if (contents[slot] != null) continue;
            final ItemStack stack = added.clone();
            final int move = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(move);
            contents[slot] = stack;
            remaining -= move;
        }
        return remaining == 0;
    }

    private static ItemStack[] cloneContents(final ItemStack[] source) {
        final ItemStack[] result = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = source[index] == null ? null : source[index].clone();
        }
        return result;
    }

    private ItemSalvageService.Tuning salvageTuning() {
        return new ItemSalvageService.Tuning(
                boundedInt(config.getInt("itemization.salvage.base-dust", 1), 64),
                boundedInt(config.getInt("itemization.salvage.per-rarity-dust", 1), 64),
                boundedInt(config.getInt("itemization.salvage.rune-dust-per-rune", 1), 64),
                boundedInt(config.getInt("itemization.salvage.signature-dust", 1), 64),
                boundedInt(config.getInt("itemization.salvage.maximum-dust", 8), 64));
    }

    private static int boundedInt(final int value, final int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }

    private static void mergeCost(final Map<String, Integer> costs, final String rawId,
                                  final int amount) {
        if (amount <= 0) return;
        final String id = rawId == null ? "" : rawId.trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) throw new IllegalArgumentException("blank item mutation material id");
        final long merged = (long) costs.getOrDefault(id, 0) + amount;
        costs.put(id, (int) Math.min(Integer.MAX_VALUE, merged));
    }
}
